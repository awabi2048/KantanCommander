package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

/**
 * 折り畳みノード（FOLDED_FOR／FOLDED_IF）の内部グラフを扱う共有処理です。
 *
 * 折り畳みノードは外側グラフに通常形状（単一next）で1個だけ残り、中身の構造
 * 全体（FOR_START〜FOR_END／CONDITION〜MERGE）をfoldedGraphへ内包します。
 * 実行・検証・出力は永続化形を変えず、メモリ上で展開した一時グラフに対して
 * 既存ロジックを適用します。利用者から見れば折り畳んだまま実行・検証でき、
 * 保存内容は折り畳んだまま維持されます。
 */
object FoldedGraphs {
    /** 折り畳みノード種別の集合です。外側では通常ノードと同じ接続形状です。 */
    val FOLDED_TYPES = setOf(CommandType.FOLDED_FOR, CommandType.FOLDED_IF)

    /** 展開中の構造ノード種別の集合です。入れ替え対象外として表示しません。 */
    val EXPANDED_STRUCTURAL_TYPES = setOf(
        CommandType.CONDITION,
        CommandType.MERGE,
        CommandType.FOR_START,
        CommandType.FOR_END,
    )

    /** 折り畳みノードかを判定します。 */
    fun isFolded(node: CommandNode): Boolean = node.type in FOLDED_TYPES

    /** 展開中の構造ノード（入れ替え不可）かを判定します。 */
    fun isExpandedStructural(node: CommandNode): Boolean = node.type in EXPANDED_STRUCTURAL_TYPES

    /** 内部グラフも含めた総ノード数を数えます。上限判定は外側だけでなく全体で行います。 */
    fun totalNodeCount(graph: CommandGraph): Int {
        var count = 0
        val visitedGraphs = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<CommandGraph, Boolean>(),
        )
        fun visit(current: CommandGraph) {
            if (!visitedGraphs.add(current)) return
            count += current.nodes.size
            current.nodes.values.forEach { node ->
                node.foldedGraph?.let(::visit)
            }
            visitedGraphs.remove(current)
        }
        visit(graph)
        return count
    }

    /** 指定IDが外側またはいずれかの内部グラフに含まれるかを判定します。 */
    fun containsNodeId(graph: CommandGraph, targetId: UUID): Boolean {
        val visitedGraphs = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<CommandGraph, Boolean>(),
        )
        fun visit(current: CommandGraph): Boolean {
            if (!visitedGraphs.add(current)) return false
            try {
                if (targetId in current.nodes) return true
                return current.nodes.values.any { node -> node.foldedGraph?.let(::visit) == true }
            } finally {
                visitedGraphs.remove(current)
            }
        }
        return visit(graph)
    }

    /**
     * 外側IDを含む折り畳みノードのIDを返します。
     *
     * 内包エラーを外側の代理ノードへ対応付ける表示用途です。外側に直接あれば
     * 自身、内部にあれば最も外側の折り畳みノード、なければnullです。
     */
    fun outermostWrapperId(graph: CommandGraph, targetId: UUID): UUID? {
        if (targetId in graph.nodes) return targetId
        graph.nodes.values.forEach { node ->
            val inner = node.foldedGraph ?: return@forEach
            if (containsNodeId(inner, targetId)) return node.id
        }
        return null
    }

    /**
     * 折り畳みノードをすべて展開した一時グラフを返します。
     *
     * 元グラフは変更しません。折り畳みノードは内部の構造全体へ置換され、
     * 内部の出口（FOR_END／MERGEのnext）へ外側の後続を接続します。
     * 内部IDは折り畳み時に元のIDのまま退避しているため、入口の付け替えは不要です。
     * 壊れた折り畳み（内部なし・先頭不一致）は展開せず線形ノードとして残し、
     * 検証側で別途エラーとして報告します。再帰の深さは構造深さ上限で打ち切ります。
     */
    fun expand(graph: CommandGraph, maximumDepth: Int = 32): CommandGraph {
        val expanded = CommandGraph(
            entryNodeId = graph.entryNodeId,
            nodes = LinkedHashMap(graph.nodes.mapValues { (_, node) -> node.deepCopy() }),
        )
        var depth = 0
        while (expanded.nodes.values.any(::isFolded) && depth < maximumDepth) {
            var replaced = false
            // 変更中の走査を避けるため、対象IDだけを先に確定します。
            val targets = expanded.nodes.values.filter(::isFolded).map { it.id }
            targets.forEach { wrapperId ->
                if (expandSingle(expanded, wrapperId)) replaced = true
            }
            if (!replaced) break
            depth++
        }
        return expanded
    }

    /**
     * 折り畳みノード1個を内部構造へ置換します。
     *
     * 内部の先頭IDは折り畳みノードと同一のため、入口・入力エッジの付け替えは
     * 不要です。出口（FOR_END／MERGEのnext）だけを外側の後続で上書きします。
     * 外側で後続が変わっていても、展開結果は現在の外側接続を正とします。
     */
    private fun expandSingle(graph: CommandGraph, wrapperId: UUID): Boolean {
        val wrapper = graph.nodes[wrapperId] ?: return false
        val inner = wrapper.foldedGraph?.deepCopy() ?: return false
        // 入れ子の折り畳みは先に展開し、単一走査で全体を平坦化します。
        val flattened = if (inner.nodes.values.any(::isFolded)) expand(inner) else inner
        val head = flattened.nodes[wrapperId] ?: return false
        val exit = when (wrapper.type) {
            CommandType.FOLDED_FOR -> {
                if (head.type != CommandType.FOR_START) return false
                val endId = head.pairedNodeId ?: return false
                val end = flattened.nodes[endId]?.takeIf {
                    it.type == CommandType.FOR_END && it.pairedNodeId == head.id
                } ?: return false
                end
            }
            CommandType.FOLDED_IF -> {
                if (head.type != CommandType.CONDITION) return false
                val mergeId = head.pairedNodeId ?: return false
                val merge = flattened.nodes[mergeId]?.takeIf {
                    it.type == CommandType.MERGE && it.pairedNodeId == head.id
                } ?: return false
                merge
            }
            else -> return false
        }
        // 内部IDと外側IDの衝突（折り畳みノード自身を除く）は構造破損とします。
        val collision = flattened.nodes.keys.any { it != wrapperId && it in graph.nodes }
        if (collision) return false
        exit.next = wrapper.next
        graph.nodes.remove(wrapperId)
        flattened.nodes.forEach { (id, node) -> graph.nodes[id] = node }
        return true
    }
}
