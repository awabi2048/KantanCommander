package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class GraphValidatorTest {
    @Test
    fun `paired true straight false branch and merge is valid`() {
        val condition = CommandType.CONDITION.newNode()
        val trueNode = CommandType.DISPLAY_TEXT.newNode()
        val falseNode = CommandType.WAIT.newNode()
        val merge = CommandType.MERGE.newNode()
        condition.trueNext = trueNode.id
        condition.falseNext = falseNode.id
        condition.pairedNodeId = merge.id
        trueNode.next = merge.id
        falseNode.next = merge.id
        merge.pairedNodeId = condition.id
        val graph = CommandGraph(condition.id, linkedMapOf(
            condition.id to condition, trueNode.id to trueNode, falseNode.id to falseNode, merge.id to merge
        ))
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `break and continue outside for body are rejected`() {
        listOf(CommandType.BREAK, CommandType.CONTINUE).forEach { type ->
            val node = type.newNode()
            val graph = CommandGraph(node.id, linkedMapOf(node.id to node))
            assertTrue(GraphValidator.validate(graph).any { it.contains("for本体の外") })
        }
    }

    @Test
    fun `break inside for body is valid`() {
        val graph = CommandGraph()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        GraphEditor.appendToForBody(graph, start.id, CommandType.BREAK)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `configured node and branch depth limits are enforced`() {
        val graph = CommandGraph()
        GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.CONDITION)
        val errors = GraphValidator.validate(
            graph,
            GraphLimits(maximumNodeCount = 1, maximumBranchDepth = 0),
        )
        assertTrue(errors.any { it.contains("ノード数") })
        assertTrue(errors.any { it.contains("構造の深さ") })
    }

    @Test
    fun `node with multiple parents is rejected`() {
        val condition = CommandType.CONDITION.newNode()
        val shared = CommandType.DISPLAY_TEXT.newNode()
        condition.trueNext = shared.id
        condition.falseNext = shared.id
        val graph = CommandGraph(
            condition.id,
            linkedMapOf(condition.id to condition, shared.id to shared),
        )

        assertTrue(GraphValidator.validate(graph).any { it.contains("複数の親") })
    }

    @Test
    fun `storage key must match node id`() {
        val node = CommandType.DISPLAY_TEXT.newNode()
        val wrongKey = UUID.randomUUID()
        val graph = CommandGraph(wrongKey, linkedMapOf(wrongKey to node))

        assertTrue(GraphValidator.validate(graph).any { it.contains("保存キーとID") })
    }

    @Test
    fun `paired condition may end one branch before its merge`() {
        val condition = CommandType.CONDITION.newNode()
        val merge = CommandType.MERGE.newNode()
        condition.pairedNodeId = merge.id
        condition.trueNext = merge.id
        condition.falseNext = null
        merge.pairedNodeId = condition.id
        val graph = CommandGraph(
            condition.id,
            linkedMapOf(condition.id to condition, merge.id to merge),
        )

        val errors = GraphValidator.validate(graph)
        assertTrue(errors.isEmpty())
    }
}
