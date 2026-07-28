package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

class ModelsTest {
    @Test
    fun `timer interval is normalized to supported range`() {
        assertEquals(10L, TimerSetting(true, 0).intervalTicks)
        assertEquals(864_000L, TimerSetting(true, 100_000).intervalTicks)
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
}
