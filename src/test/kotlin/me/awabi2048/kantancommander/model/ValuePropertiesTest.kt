package me.awabi2048.kantancommander.model

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * issue #3 の詳細値取得テストです。
 *
 * 型安全なadapterの一覧・結果型・取得不能条件を固定します。Bukkit実体を
 * 必要とするENTITY系は動的プロキシの偽装実体で検証し、サーバーを要求しません。
 */
class ValuePropertiesTest {
    @Test
    fun `registry exposes the initial property list`() {
        val entityProperties = ValueProperties.forSource(ValueType.ENTITY).map { it.id }.toSet()
        assertEquals(setOf("name", "type", "health", "location"), entityProperties)
        assertEquals(
            setOf("x", "y", "z", "yaw", "pitch"),
            ValueProperties.forSource(ValueType.LOCATION).map { it.id }.toSet(),
        )
        assertEquals(
            setOf("x", "y", "z"),
            ValueProperties.forSource(ValueType.VECTOR).map { it.id }.toSet(),
        )
        assertEquals(
            setOf("type", "amount"),
            ValueProperties.forSource(ValueType.ITEM).map { it.id }.toSet(),
        )
        assertEquals(listOf("type"), ValueProperties.forSource(ValueType.BLOCK).map { it.id })
        assertTrue(ValueProperties.forSource(ValueType.NUMBER).isEmpty())
        assertEquals(ValueType.NUMBER, ValueProperties.definition("health")?.result)
        assertEquals(ValueType.LOCATION, ValueProperties.definition("location")?.result)
        assertNull(ValueProperties.definition("unknown"))
    }

    @Test
    fun `location vector item and block properties resolve without a server`() {
        val location = TypedValue(ValueType.LOCATION, location = SavedLocation(1.0, 2.0, 3.0, 90f, 45f))
        assertEquals(1.0, ValueProperties.resolve(location, "x") { null }?.numberValue)
        assertEquals(2.0, ValueProperties.resolve(location, "y") { null }?.numberValue)
        assertEquals(3.0, ValueProperties.resolve(location, "z") { null }?.numberValue)
        assertEquals(90.0, ValueProperties.resolve(location, "yaw") { null }?.numberValue)
        assertEquals(45.0, ValueProperties.resolve(location, "pitch") { null }?.numberValue)

        val vector = TypedValue(ValueType.VECTOR, vector = SavedVector(4.0, 5.0, 6.0))
        assertEquals(4.0, ValueProperties.resolve(vector, "x") { null }?.numberValue)
        // 型外の詳細値は取得しません。
        assertNull(ValueProperties.resolve(vector, "yaw") { null })
        assertNull(ValueProperties.resolve(location, "amount") { null })
        assertNull(ValueProperties.resolve(location, "unknown") { null })

        val item = TypedValue(ValueType.ITEM, item = "STONE", amount = 3)
        assertEquals("STONE", ValueProperties.resolve(item, "type") { null }?.stringValue)
        assertEquals(3.0, ValueProperties.resolve(item, "amount") { null }?.numberValue)
        val single = TypedValue(ValueType.ITEM, item = "STONE")
        assertEquals(1.0, ValueProperties.resolve(single, "amount") { null }?.numberValue)

        val block = TypedValue(ValueType.BLOCK, block = "STONE")
        assertEquals("STONE", ValueProperties.resolve(block, "type") { null }?.stringValue)
    }

    @Test
    fun `entity properties resolve through the lookup`() {
        val id = UUID.randomUUID()
        val entity = fakeEntity("boss", EntityType.ZOMBIE, 15.5, id)
        val value = TypedValue(ValueType.ENTITY, entityId = id)
        val lookup: (UUID) -> Entity? = { if (it == id) entity else null }

        assertEquals("boss", ValueProperties.resolve(value, "name", lookup)?.stringValue)
        assertEquals("minecraft:zombie", ValueProperties.resolve(value, "type", lookup)?.stringValue)
        assertEquals(15.5, ValueProperties.resolve(value, "health", lookup)?.numberValue)
        assertEquals(10.0, ValueProperties.resolve(value, "location", lookup)?.location?.x)

        // 実体消失・未保持は解決不能として扱います。
        assertNull(ValueProperties.resolve(value, "name") { null })
        assertNull(ValueProperties.resolve(TypedValue(ValueType.ENTITY), "health", lookup))
        // 非生物に体力はありません。
        val markerId = UUID.randomUUID()
        val marker = fakeEntity("mark", EntityType.ARMOR_STAND, null, markerId)
        val markerValue = TypedValue(ValueType.ENTITY, entityId = markerId)
        assertNull(ValueProperties.resolve(markerValue, "health") { marker })
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeEntity(name: String, type: EntityType, health: Double?, id: UUID): Entity {
        val handler = InvocationHandler { proxy, method, _ ->
            when (method.name) {
                "toString" -> "fakeEntity($name)"
                "hashCode" -> System.identityHashCode(proxy)
                "getName" -> name
                "getType" -> type
                "getUniqueId" -> id
                "getHealth" -> health
                "getLocation" -> Location(null, 10.0, 64.0, 0.0)
                else -> null
            }
        }
        // 非生物は LivingEntity を実装しないため、体力解決は型で除外されます。
        val interfaces = if (health == null) {
            arrayOf(Entity::class.java)
        } else {
            arrayOf(Entity::class.java, LivingEntity::class.java)
        }
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            interfaces,
            handler,
        ) as Entity
    }
}
