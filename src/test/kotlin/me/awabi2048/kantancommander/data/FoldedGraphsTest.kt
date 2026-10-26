package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * 深い入れ子の折り畳みグラフを、反復走査と深度予算で安全に扱えることを固定します。
 *
 * ノード連鎖・入れ子の深さに比例するJVM再帰は、保存データだけで
 * StackOverflowError に達しえます。走査・展開は連鎖長へ依存しません。
 */
class FoldedGraphsTest {

    /**
     * [depth] 段の FOLDED_FOR 入れ子を持つ最外側の折り畳みノードを返します。
     *
     * 各段は 外側FOLDED_FOR → 内部 FOR_START〜FOR_END の組で、内部本体へ
     * 一段内側の折り畳みノードを収めます。折り畳み時の規約どおり、外側IDと
     * 内部先頭（FOR_START）のIDは一致させます。
     */
    private fun nestedFoldedFor(depth: Int): CommandNode {
        var child: CommandNode? = null
        var root: CommandNode? = null
        repeat(depth) {
            val head = CommandType.FOR_START.newNode()
            val end = CommandType.FOR_END.newNode()
            head.pairedNodeId = end.id
            end.pairedNodeId = head.id
            head.trueNext = child?.id ?: end.id
            val nodes = linkedMapOf<UUID, CommandNode>(head.id to head, end.id to end)
            child?.let { inner ->
                inner.next = end.id
                nodes[inner.id] = inner
            }
            val wrapper = CommandType.FOLDED_FOR.newNode().copy(id = head.id)
            wrapper.foldedGraph = CommandGraph(head.id, nodes)
            child = wrapper
            root = wrapper
        }
        return requireNotNull(root)
    }

    @Test
    fun `deep folded graphs traverse iteratively`() {
        val wrapper = nestedFoldedFor(200)
        val graph = CommandGraph(wrapper.id, linkedMapOf(wrapper.id to wrapper))

        // 総ノード数は全入れ子を数えます（外側1＋各段のFOR組2個×200）。
        assertTrue(FoldedGraphs.totalNodeCount(graph) >= 2 * 200)
        assertTrue(FoldedGraphs.containsNodeId(graph, wrapper.id))
        assertFalse(FoldedGraphs.containsNodeId(graph, UUID.randomUUID()))
    }

    @Test
    fun `deep folded expansion is bounded by the depth budget`() {
        // 深度予算はJVM再帰の深さを抑えるためのもので、展開そのものは
        // 反復で続行されます。予算を使い切って残った入れ子は折り畳みの
        // まま残り、どこまででも深い保存データでも暴走しません。
        val wrapper = nestedFoldedFor(600)
        val graph = CommandGraph(wrapper.id, linkedMapOf(wrapper.id to wrapper))

        val expanded = FoldedGraphs.expand(graph, maximumDepth = 8)
        assertTrue(expanded.nodes.values.any(FoldedGraphs::isFolded))
        // 元グラフは変更されません。
        assertTrue(graph.nodes.values.single().foldedGraph != null)
    }

    @Test
    fun `self containing folded graphs stop traversal`() {
        // 自身を内包する壊れた構造でも、訪問集合で走査が止まります。
        val wrapper = nestedFoldedFor(4)
        wrapper.foldedGraph?.nodes?.values
            ?.firstOrNull { FoldedGraphs.isFolded(it) }
            ?.foldedGraph = wrapper.foldedGraph
        val graph = CommandGraph(wrapper.id, linkedMapOf(wrapper.id to wrapper))

        val count = FoldedGraphs.totalNodeCount(graph)
        assertTrue(count > 0)
        assertTrue(FoldedGraphs.containsNodeId(graph, wrapper.id))
    }
}
