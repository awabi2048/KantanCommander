package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class GraphValidatorTest {
    @Test
    @org.junit.jupiter.api.Timeout(2)
    fun `循環FORは所属スタックを増殖させず拒否する`() {
        val start = CommandType.FOR_START.newNode()
        val end = CommandType.FOR_END.newNode()
        start.pairedNodeId = end.id
        end.pairedNodeId = start.id
        start.trueNext = start.id
        val graph = CommandGraph(start.id, linkedMapOf(start.id to start, end.id to end))
        assertTrue(GraphValidator.validateStructured(graph).any {
            it.code == me.awabi2048.kantancommander.error.ErrorCode.GRAPH_CYCLE
        })
    }

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

    @Test
    fun `long node chains validate iteratively without stack overflow`() {
        // ノード連鎖の長さだけ深くなる再帰走査は、長い直線グラフで
        // StackOverflowError に達しえました。反復走査は連鎖長に依存しません。
        val length = 20_000
        val nodes = (1..length).map { CommandType.DISPLAY_TEXT.newNode() }
        nodes.zipWithNext().forEach { (current, next) -> current.next = next.id }
        val graph = CommandGraph(
            nodes.first().id,
            nodes.associateByTo(linkedMapOf()) { it.id },
        )

        val errors = GraphValidator.validate(
            graph,
            GraphLimits(maximumNodeCount = Int.MAX_VALUE),
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `for body allows a terminal nested condition branch without treating for end as a merge`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val outer = GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val inner = GraphEditor.insert(
            graph,
            outer.id,
            GraphEditor.Edge.FALSE,
            CommandType.CONDITION,
            continuationId = end.id,
        )
        GraphEditor.insert(
            graph,
            inner.id,
            GraphEditor.Edge.FALSE,
            CommandType.WAIT,
            continuationId = end.id,
        )

        assertTrue(GraphValidator.validate(graph).isEmpty())
    }
}
