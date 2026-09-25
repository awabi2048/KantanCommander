package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.EventTriggerConditions
import me.awabi2048.kantancommander.model.TriggerBlockIdentity
import me.awabi2048.kantancommander.model.TriggerClickSide
import me.awabi2048.kantancommander.model.TriggerLeverState
import org.bukkit.Material
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

/** 統合種別の詳細条件が、発火時の実対象に対して評価されることを検証します。 */
class EventTriggerMatcherTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, values: Map<String, Any?>): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> values[method.name] } as T

    private val world = proxy(World::class.java, mapOf("getName" to "test"))
    private val stone = proxy(Block::class.java, mapOf(
        "getWorld" to world, "getX" to 4, "getY" to 65, "getZ" to -3, "getType" to Material.STONE,
    ))

    @Test
    fun `AIR right click has AIR material and no clicked block identity`() {
        val air = EventTriggerConditions(blockType = "AIR", clickSide = TriggerClickSide.RIGHT)
        assertTrue(EventTriggerMatcher.matches(air, null, null, null, TriggerClickSide.RIGHT, null))
        assertFalse(EventTriggerMatcher.matches(air, null, null, null, TriggerClickSide.LEFT, null))
        assertFalse(EventTriggerMatcher.matches(
            air.copy(blockIdentity = TriggerBlockIdentity("test", 4, 65, -3)),
            null, null, null, TriggerClickSide.RIGHT, null,
        ))
    }

    @Test
    fun `block identity requires the clicked individual block`() {
        val condition = EventTriggerConditions(blockType = "STONE", blockIdentity = TriggerBlockIdentity("test", 4, 65, -3))
        assertTrue(EventTriggerMatcher.matches(condition, null, stone, null, null, null))
        assertFalse(EventTriggerMatcher.matches(
            condition.copy(blockIdentity = TriggerBlockIdentity("test", 5, 65, -3)),
            null, stone, null, null, null,
        ))
    }

    @Test
    fun `lever result and actor are separate optional conditions`() {
        val id = UUID.randomUUID()
        val actor = proxy(Player::class.java, mapOf("getUniqueId" to id))
        val condition = EventTriggerConditions(actorId = id, leverState = TriggerLeverState.ON)
        assertTrue(EventTriggerMatcher.matches(condition, actor, stone, null, null, TriggerLeverState.ON))
        assertFalse(EventTriggerMatcher.matches(condition, null, stone, null, null, TriggerLeverState.ON))
        assertFalse(EventTriggerMatcher.matches(condition, actor, stone, null, null, TriggerLeverState.OFF))
    }

    @Test
    fun `AIR position is the exact interaction range endpoint`() {
        val eye = Location(null, 0.5, 64.0, 0.5, 0f, 0f)
        val endpoint = airInteractionEndpoint(eye, 4.5)
        assertTrue(kotlin.math.abs(endpoint.x - 0.5) < 1e-9)
        assertTrue(kotlin.math.abs(endpoint.y - 64.0) < 1e-9)
        assertTrue(kotlin.math.abs(endpoint.z - 5.0) < 1e-9)
        // 入力Locationを変えないため、同じ視線から他のイベント位置も計算できます。
        assertTrue(kotlin.math.abs(eye.z - 0.5) < 1e-9)
    }
}
