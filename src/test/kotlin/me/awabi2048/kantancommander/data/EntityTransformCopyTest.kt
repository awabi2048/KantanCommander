package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.EntityVariableStore
import me.awabi2048.kantancommander.model.MapEntityVariableBackend
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableRead
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.entity.EntityTransformEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * issue #22 の変化時複製テストです。
 *
 * Paper API では `EntityTransformEvent.entity` が変化元、`transformedEntities` が
 * 変化先です。変化元の変数が全変化先へ引き継がれることを固定します。
 * `transformedEntity`（変化先の先頭）を複製元にすると、変化先間でしか値が
 * 伝わらず変化元の値を見失うため、その取り違えを回帰として防ぎます。
 * サーバーなしで実行できます。
 */
class EntityTransformCopyTest {
    @Test
    fun `transform copies from the source entity to every destination`() {
        val store = EntityVariableStore(MapEntityVariableBackend())
        val source = fakeEntity()
        val first = fakeEntity()
        val second = fakeEntity()
        store.write(listOf(source), "score", TypedValue(ValueType.NUMBER, numberValue = 42.0))

        val event = EntityTransformEvent(
            source,
            listOf(first, second),
            EntityTransformEvent.TransformReason.DROWNED,
        )
        // 変更の前提: transformedEntity は変化元ではなく変化先の先頭です。
        assertEquals(first.uniqueId, event.transformedEntity.uniqueId)
        assertEquals(source.uniqueId, event.entity.uniqueId)

        applyEntityTransformCopy(store, event)

        // 変化先の全実体が、変化元の値を受け取ります。
        listOf(first, second).forEach { destination ->
            val read = store.readSingle(listOf(destination), "score")
            assertInstanceOf(VariableRead.Value::class.java, read)
            assertEquals(42.0, (read as VariableRead.Value).value.numberValue)
        }
    }

    private fun fakeEntity(): Entity {
        val id = UUID.randomUUID()
        val handler = InvocationHandler { proxy, method, _ ->
            when (method.name) {
                "toString" -> "fakeEntity($id)"
                "hashCode" -> System.identityHashCode(proxy)
                "getUniqueId" -> id
                "getType" -> EntityType.ZOMBIE
                "getLocation" -> Location(null, 0.0, 64.0, 0.0)
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Entity::class.java),
            handler,
        ) as Entity
    }
}
