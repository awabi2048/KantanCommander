package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.error.ErrorCode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.persistence.PersistentDataContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * issue #22 のEntity変数テストです。
 *
 * codecの往復・複数書き込み・単一読み出し規則・型変更・死亡時破棄・複製を
 * 固定します。永続領域はメモリbackendで代替し、サーバーを要求しません。
 * PDC実体との結合は起動試験で確認します。
 */
class EntityVariablesTest {
    @Test
    fun `codec round trips every value type`() {
        val values = listOf(
            TypedValue(ValueType.NUMBER, numberValue = 1.5),
            TypedValue(ValueType.STRING, stringValue = "a:b,c|d"),
            TypedValue(ValueType.BOOLEAN, booleanValue = true),
            TypedValue(ValueType.LOCATION, location = SavedLocation(1.0, 2.0, 3.0, 90f, 45f)),
            TypedValue(ValueType.VECTOR, vector = SavedVector(1.0, 2.0, 3.0)),
            TypedValue(ValueType.ITEM, item = "STONE", itemData = "e30=", amount = 3),
            TypedValue(ValueType.ITEM, item = "STONE", amount = 1),
            TypedValue(ValueType.BLOCK, block = "STONE"),
            TypedValue(ValueType.ENTITY, entityId = UUID.randomUUID()),
            TypedValue(ValueType.ENTITY),
            TypedValue(ValueType.SOUND, sound = "minecraft:block.note_block.harp", volume = 1.0, pitch = 1.0),
            TypedValue(ValueType.EFFECT, effect = "SPEED", level = 2, seconds = 30),
        )
        values.forEach { value ->
            val encoded = EntityVariableCodec.encode(value)
            assertTrue(encoded != null, "encodes $value")
            assertEquals(value, EntityVariableCodec.decode(encoded), "round-trips $value")
        }
    }

    @Test
    fun `codec rejects malformed payloads`() {
        assertNull(EntityVariableCodec.decode(null))
        assertNull(EntityVariableCodec.decode(""))
        assertNull(EntityVariableCodec.decode("NUMBER"))
        assertNull(EntityVariableCodec.decode("UNKNOWN:1"))
        assertNull(EntityVariableCodec.decode("NUMBER:oops"))
        assertNull(EntityVariableCodec.decode("BOOLEAN:yes"))
        assertNull(EntityVariableCodec.decode("LOCATION:1,2"))
        assertNull(EntityVariableCodec.decode("ITEM:,e30=,0"))
        assertNull(EntityVariableCodec.decode("ENTITY:not-a-uuid"))
        assertNull(EntityVariableCodec.decode("EFFECT:SPEED,x,30"))
    }

    @Test
    fun `store writes to many and reads from exactly one`() {
        val store = EntityVariableStore(MapEntityVariableBackend())
        val first = fakeEntity()
        val second = fakeEntity()
        val value = TypedValue(ValueType.NUMBER, numberValue = 7.0)

        assertEquals(2, store.write(listOf(first, second), "score", value))
        assertEquals(0, store.write(emptyList(), "score", value))

        // 単一解決だけが値を返します。0件・複数件の暗黙採用はしません。
        val single = store.readSingle(listOf(first), "score")
        assertInstanceOf(VariableRead.Value::class.java, single)
        assertEquals(7.0, (single as VariableRead.Value).value.numberValue)
        assertInstanceOf(
            VariableRead.Failed::class.java,
            store.readSingle(emptyList(), "score"),
        )
        assertInstanceOf(
            VariableRead.Failed::class.java,
            store.readSingle(listOf(first, second), "score"),
        )
        assertEquals(
            ErrorCode.VARIABLE_UNSET,
            (store.readSingle(emptyList(), "score") as VariableRead.Failed).errorCode(),
        )
        val missing = store.readSingle(listOf(first), "ghost")
        assertInstanceOf(VariableRead.Failed::class.java, missing)
        assertEquals(
            ErrorCode.VARIABLE_UNDEFINED,
            (missing as VariableRead.Failed).errorCode(),
        )
    }

    @Test
    fun `store overwrites types and deletes per entity`() {
        val store = EntityVariableStore(MapEntityVariableBackend())
        val entity = fakeEntity()
        // 定義表を持たないため、同名変数の型変更は後勝ちで上書きします。
        store.write(listOf(entity), "flex", TypedValue(ValueType.NUMBER, numberValue = 1.0))
        store.write(listOf(entity), "flex", TypedValue(ValueType.STRING, stringValue = "s"))
        assertEquals("s", (store.readSingle(listOf(entity), "flex") as VariableRead.Value).value.stringValue)

        assertEquals(1, store.delete(listOf(entity), "flex"))
        assertEquals(0, store.delete(listOf(entity), "flex"))
        assertTrue(store.readSingle(listOf(entity), "flex") is VariableRead.Failed)
    }

    @Test
    fun `clear and copy support the lifecycle contract`() {
        val store = EntityVariableStore(MapEntityVariableBackend())
        val mother = fakeEntity()
        val child = fakeEntity()
        store.write(listOf(mother), "a", TypedValue(ValueType.NUMBER, numberValue = 1.0))
        store.write(listOf(mother), "b", TypedValue(ValueType.STRING, stringValue = "x"))

        // 複製時は変数一式をそのまま複製します。
        assertEquals(2, store.copyTo(mother, child))
        assertEquals(1.0, (store.readSingle(listOf(child), "a") as VariableRead.Value).value.numberValue)

        // 死亡時は変数をリセットします。
        store.clear(mother)
        assertTrue(store.readSingle(listOf(mother), "a") is VariableRead.Failed)
        // 複製先は影響を受けません。
        assertTrue(store.readSingle(listOf(child), "a") is VariableRead.Value)
    }

    @Test
    fun `pdc backend round trips through a fake container`() {
        val store = EntityVariableStore(PdcEntityVariableBackend())
        val entity = fakeEntity()
        val value = TypedValue(ValueType.STRING, stringValue = "hello:world|test")
        assertEquals(1, store.write(listOf(entity), "greeting", value))
        val read = store.readSingle(listOf(entity), "greeting")
        assertInstanceOf(VariableRead.Value::class.java, read)
        assertEquals("hello:world|test", (read as VariableRead.Value).value.stringValue)
        store.clear(entity)
        assertTrue(store.readSingle(listOf(entity), "greeting") is VariableRead.Failed)
    }

    @Test
    fun `backend keys stay inside the dedicated namespace`() {
        val key = EntityVariableCodec.keyFor("Score")
        assertEquals("kantancommander", key.namespace)
        assertTrue(EntityVariableCodec.isVariableKey(key))
        assertEquals("score", EntityVariableCodec.nameOf(key))
    }

    private fun fakeEntity(): Entity {
        val id = UUID.randomUUID()
        // PDCは実体に付随する同一インスタンスである必要があります。
        val container = fakeContainer()
        val handler = InvocationHandler { proxy, method, _ ->
            when (method.name) {
                "toString" -> "fakeEntity($id)"
                "hashCode" -> System.identityHashCode(proxy)
                "getUniqueId" -> id
                "getType" -> EntityType.ZOMBIE
                "getLocation" -> Location(null, 0.0, 64.0, 0.0)
                "getPersistentDataContainer" -> container
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Entity::class.java, LivingEntity::class.java),
            handler,
        ) as Entity
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeContainer(): PersistentDataContainer {
        val data = linkedMapOf<String, String>()
        fun keyOf(arg: Any?): String = (arg as NamespacedKey).key
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "toString" -> "fakeContainer"
                "hashCode" -> System.identityHashCode(proxy)
                "get" -> data[keyOf(args[0])]
                "set" -> {
                    data[keyOf(args[0])] = args[2] as String
                    null
                }
                "has" -> data.containsKey(keyOf(args[0]))
                "remove" -> data.remove(keyOf(args[0]))
                "getKeys" -> data.keys.map { NamespacedKey("kantancommander", it) }.toSet()
                "isEmpty" -> data.isEmpty()
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(PersistentDataContainer::class.java),
            handler,
        ) as PersistentDataContainer
    }
}
