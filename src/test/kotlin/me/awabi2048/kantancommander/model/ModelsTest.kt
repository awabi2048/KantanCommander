package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `entity action keeps only ride motion defaults`() {
        // ENTITY_ACTIONはride/dismount/motionに限定します。旧equip欄は
        // PLACE_ITEM、旧tag欄はENTITY_OPERATIONへ移行済みのため既定値に含めません。
        val node = CommandType.ENTITY_ACTION.newNode()
        assertEquals("ride", node.string("action", ""))
        assertEquals("0.0", node.string("motionX", ""))
        assertEquals("0.0", node.string("motionY", ""))
        assertEquals("0.0", node.string("motionZ", ""))
        assertFalse(node.params.containsKey("slot"))
        assertFalse(node.params.containsKey("tagOperation"))
        assertFalse(node.params.containsKey("tag"))
    }

    @Test
    fun `entity operation starts with unified selection defaults`() {
        val node = CommandType.ENTITY_OPERATION.newNode()
        // 統合選択形式では設定対象IDが未選択であり、旧3択キーは保持しません。
        assertEquals("", node.params[EntityDataPath.PARAM_PATH])
        assertEquals("", node.params[EntityTagEntries.PARAM_ENTRIES])
        assertEquals("0.0", node.string(EntityAttributeSupport.PARAM_VALUE, ""))
        assertEquals("", node.params[EntityDataSchema.PARAM_VALUE])
        assertFalse(node.params.containsKey("operation"))
        assertFalse(node.params.containsKey("tagOperation"))
        assertFalse(node.params.containsKey("tag"))
    }
}
