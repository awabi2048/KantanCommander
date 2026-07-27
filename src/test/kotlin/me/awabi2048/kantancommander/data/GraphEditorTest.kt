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
        val trueCommand = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val first = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val second = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertEquals(first.id, condition.falseNext)
        assertEquals(second.id, first.next)
        assertEquals(merge.id, second.next)
        assertEquals(merge.id, trueCommand.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `nested condition is appended to selected false branch`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val inner = GraphEditor.append(graph, CommandType.CONDITION, outer.id)
        GraphEditor.append(graph, CommandType.WAIT, inner.id)
        GraphEditor.appendMerge(graph, inner.id)
        val outerCommand = GraphEditor.append(graph, CommandType.GIVE_ITEM, outer.id)
        GraphEditor.appendMerge(graph, outer.id)

        assertEquals(inner.id, outer.falseNext)
        assertEquals(outerCommand.id, inner.pairedNodeId?.let(graph.nodes::get)?.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `for start creates a paired end node`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val end = start.pairedNodeId?.let(graph.nodes::get)

        assertEquals(CommandType.FOR_END, end?.type)
        assertEquals(end?.id, start.trueNext)
        assertEquals(start.id, end?.pairedNodeId)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `condition deletion is refused while false branch has an execution node`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        GraphEditor.appendMerge(graph, condition.id)

        assertEquals(false, GraphEditor.delete(graph, condition.id))
        assertTrue(condition.id in graph.nodes)
    }

    @Test
    fun `empty condition deletion also removes matching merge and promotes true path`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.WAIT)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)

        assertTrue(GraphEditor.delete(graph, condition.id))
        assertEquals(trueNode.id, graph.entryNodeId)
        assertEquals(after.id, trueNode.next)
        assertTrue(merge.id !in graph.nodes)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `for pair deletion is allowed only while body is empty`() {
        val empty = CommandGraph.empty()
        val emptyStart = GraphEditor.append(empty, CommandType.FOR_START)
        assertTrue(GraphEditor.delete(empty, emptyStart.id))

        val occupied = CommandGraph.empty()
        val occupiedStart = GraphEditor.append(occupied, CommandType.FOR_START)
        GraphEditor.appendToForBody(occupied, occupiedStart.id, CommandType.WAIT)
        assertEquals(false, GraphEditor.delete(occupied, occupiedStart.id))
    }
}
