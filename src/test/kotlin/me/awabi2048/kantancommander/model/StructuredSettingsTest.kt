package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.data.GraphEditor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

class StructuredSettingsTest {
    @Test
    fun `structured target and destination survive graph copy independently`() {
        val graph = CommandGraph.empty()
        val node = GraphEditor.append(graph, CommandType.TELEPORT)
        node.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER, maximumDistance = 16.0)
        node.destinationSpec = PositionSpec(PositionKind.CAPTURED, 1.0, 64.0, 2.0, 90f, 0f)
        node.conditionPositionSpec = PositionSpec(PositionKind.DISK)

        val copied = graph.deepCopy()
        val copiedNode = copied.nodes.getValue(node.id)
        assertEquals(node.targetSpec, copiedNode.targetSpec)
        assertEquals(node.destinationSpec, copiedNode.destinationSpec)
        assertEquals(node.conditionPositionSpec, copiedNode.conditionPositionSpec)
        assertNotSame(node.targetSpec, copiedNode.targetSpec)
        assertNotSame(node.conditionPositionSpec, copiedNode.conditionPositionSpec)
    }
}
