package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

class ModelsTest {
    @Test
    fun `timer interval is normalized to supported range`() {
        assertEquals(20L, TimerSetting(true, 0.0).intervalTicks)
        assertEquals(1_728_000L, TimerSetting(true, 100_000.0).intervalTicks)
    }

    @Test
    fun `timer accepts fractional seconds without a lower bound`() {
        assertEquals(10L, TimerSetting(true, 0.5).intervalTicks)
        assertEquals(2L, TimerSetting(true, 0.1).intervalTicks)
    }

    @Test
    fun `graph copy is independent`() {
        val node = CommandType.ENTITY_ACTION.newNode()
        node.secondaryTargetSpec = TargetSpec(TargetKind.NEAREST_ENTITY, maximumDistance = 8.0)
        val graph = CommandGraph(node.id, linkedMapOf(node.id to node))
        val copy = graph.deepCopy()
        assertNotSame(graph.nodes[node.id]?.params, copy.nodes[node.id]?.params)
        assertNotSame(graph.nodes[node.id]?.secondaryTargetSpec, copy.nodes[node.id]?.secondaryTargetSpec)
        assertEquals(graph.nodes[node.id]?.secondaryTargetSpec, copy.nodes[node.id]?.secondaryTargetSpec)
    }

    @Test
    fun `entity action keeps legacy defaults and gains motion components`() {
        val node = CommandType.ENTITY_ACTION.newNode()
        assertEquals("ride", node.string("action", ""))
        assertEquals("0.0", node.string("motionX", ""))
        assertEquals("0.0", node.string("motionY", ""))
        assertEquals("0.0", node.string("motionZ", ""))
        // 旧equip／tagの既定値は読み込み互換のため維持します。
        assertEquals("HAND", node.string("slot", ""))
        assertEquals("add", node.string("tagOperation", ""))
    }

    @Test
    fun `entity operation starts with tag operation defaults`() {
        val node = CommandType.ENTITY_OPERATION.newNode()
        assertEquals("tag", node.string("operation", ""))
        assertEquals("add", node.string("tagOperation", ""))
        assertEquals(EntityAttributeSupport.OPERATION_BASE_VALUE, node.string(EntityAttributeSupport.PARAM_OPERATION, ""))
        assertEquals(EntityAttributeSupport.MODIFIER_ADD_NUMBER, node.string(EntityAttributeSupport.PARAM_MODIFIER_OPERATION, ""))
        assertEquals("0.0", node.string(EntityAttributeSupport.PARAM_AMOUNT, ""))
        // 未選択の設定項目は空保存値であり、表示側の既定値解決で未設定として扱います。
        assertEquals("", node.params[EntityDataSchema.PARAM_PROPERTY])
        assertEquals("unset", node.string(EntityDataSchema.PARAM_PROPERTY, "unset"))
    }
}
