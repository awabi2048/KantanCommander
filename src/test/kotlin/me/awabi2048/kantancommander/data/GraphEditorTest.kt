package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphEditorTest {
    @Test
    fun `false branch keeps insertion order`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val first = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val second = GraphEditor.append(graph, CommandType.WAIT, condition.id)

        assertEquals(first.id, condition.falseNext)
        assertEquals(second.id, first.next)
        assertEquals(condition.pairedNodeId, second.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `nested condition is appended to selected false branch`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val inner = GraphEditor.append(graph, CommandType.CONDITION, outer.id)
        val outerCommand = GraphEditor.append(graph, CommandType.GIVE_ITEM, outer.id)
        val innerCommand = GraphEditor.append(graph, CommandType.TELEPORT, inner.id)

        assertEquals(inner.id, outer.falseNext)
        assertEquals(outerCommand.id, inner.pairedNodeId?.let(graph.nodes::get)?.next)
        assertEquals(innerCommand.id, inner.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }
}
