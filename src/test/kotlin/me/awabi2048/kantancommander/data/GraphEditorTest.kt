package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphEditorTest {
    @Test
    fun `adjacent reorder swaps execution order without changing node identity`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val second = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val third = GraphEditor.append(graph, CommandType.GIVE_ITEM)

        assertTrue(GraphEditor.swapAdjacent(graph, second.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(second.id, graph.entryNodeId)
        assertEquals(first.id, second.next)
        assertEquals(third.id, first.next)

        assertTrue(GraphEditor.swapAdjacent(graph, second.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(first.id, graph.entryNodeId)
        assertEquals(second.id, first.next)
        assertEquals(third.id, second.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `adjacent reorder works within a branch but does not exchange branch meaning`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.WAIT)
        val falseFirst = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val falseSecond = GraphEditor.append(graph, CommandType.GIVE_ITEM, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertTrue(GraphEditor.swapAdjacent(graph, falseSecond.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(falseSecond.id, condition.falseNext)
        assertEquals(falseFirst.id, falseSecond.next)
        assertEquals(merge.id, falseFirst.next)
        assertEquals(trueNode.id, condition.trueNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `structural nodes and branch boundary cannot be reordered as simple nodes`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val branchNode = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        GraphEditor.appendMerge(graph, condition.id)

        assertEquals(false, GraphEditor.swapAdjacent(graph, condition.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(false, GraphEditor.swapAdjacent(graph, branchNode.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

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

    @Test
    fun `outer condition cannot merge before nested condition`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.insert(graph, outer.id, GraphEditor.Edge.TRUE, CommandType.CONDITION)

        assertEquals(false, GraphEditor.canAppendMerge(graph, outer.id))
        assertThrows(IllegalArgumentException::class.java) {
            GraphEditor.appendMerge(graph, outer.id)
        }
    }

    @Test
    fun `last false command may be deleted while branches remain merged`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val falseCommand = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertTrue(GraphEditor.delete(graph, falseCommand.id))
        assertEquals(merge.id, condition.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `deleting merge reopens branches and keeps successor on true path`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueCommand = GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)

        assertTrue(GraphEditor.delete(graph, merge.id))
        assertEquals(null, condition.pairedNodeId)
        assertEquals(after.id, trueCommand.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }
}
