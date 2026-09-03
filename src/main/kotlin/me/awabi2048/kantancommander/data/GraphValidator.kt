package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

object GraphValidator {
    fun validate(graph: CommandGraph, limits: GraphLimits = GraphLimits()): List<String> {
        val errors = mutableListOf<String>()
        if (graph.nodes.size > limits.maximumNodeCount) {
            errors += "ノード数が上限 ${limits.maximumNodeCount} を超えています"
        }
        val entry = graph.entryNodeId
        if (entry == null) {
            if (graph.nodes.isNotEmpty()) errors += "開始ノードがありません"
            return errors
        }
        if (entry !in graph.nodes) errors += "開始ノードが存在しません"

        val duplicateIds = graph.nodes.values.groupingBy(CommandNode::id).eachCount().filterValues { it > 1 }.keys
        duplicateIds.forEach { errors += "ノードIDが重複しています: $it" }
        graph.nodes.forEach { (key, node) ->
            if (key != node.id) errors += "ノードの保存キーとIDが一致しません: key=$key id=${node.id}"
            node.outgoing().filterNot(graph.nodes::containsKey).forEach {
                errors += "${node.id} が存在しないノード $it を参照しています"
            }
            when (node.type) {
                CommandType.CONDITION -> validateCondition(graph, node, errors)
                CommandType.MERGE -> validatePair(graph, node, CommandType.CONDITION, errors)
                CommandType.FOR_START -> validateForStart(graph, node, errors)
                CommandType.FOR_END -> validatePair(graph, node, CommandType.FOR_START, errors)
                else -> Unit
            }
        }
        validateIncomingEdges(graph, errors)

        if (entry in graph.nodes) {
            val visited = mutableSetOf<UUID>()
            val active = mutableSetOf<UUID>()
            fun visit(id: UUID, depth: Int) {
                if (depth > limits.maximumBranchDepth) {
                    errors += "構造の深さが上限 ${limits.maximumBranchDepth} を超えています"
                    return
                }
                if (!active.add(id)) {
                    errors += "実行グラフに循環があります: $id"
                    return
                }
                if (!visited.add(id)) {
                    active.remove(id)
                    return
                }
                val node = graph.nodes[id]
                val nextDepth = when (node?.type) {
                    CommandType.CONDITION, CommandType.FOR_START -> depth + 1
                    CommandType.MERGE, CommandType.FOR_END -> (depth - 1).coerceAtLeast(0)
                    else -> depth
                }
                node?.outgoing()?.forEach { visit(it, nextDepth) }
                active.remove(id)
            }
            visit(entry, 0)
            validateStructuredControl(graph, entry, errors)
            (graph.nodes.keys - visited).forEach { errors += "到達不能ノードがあります: $it" }
        }
        return errors.distinct()
    }

    private fun validateStructuredControl(
        graph: CommandGraph,
        entry: UUID,
        errors: MutableList<String>,
    ) {
        val visited = mutableSetOf<Pair<UUID, List<UUID>>>()
        fun visit(id: UUID?, forStack: List<UUID>) {
            if (id == null || !visited.add(id to forStack)) return
            val node = graph.nodes[id] ?: return
            when (node.type) {
                CommandType.BREAK, CommandType.CONTINUE -> {
                    if (forStack.isEmpty()) {
                        errors += "${node.type} ${node.id} はfor本体の外では使用できません"
                    }
                }
                CommandType.FOR_START -> {
                    val endId = node.pairedNodeId ?: return
                    visit(node.trueNext, forStack + node.id)
                    visit(graph.nodes[endId]?.next, forStack)
                }
                CommandType.FOR_END -> {
                    val expectedStart = forStack.lastOrNull()
                    if (node.pairedNodeId != expectedStart) {
                        errors += "FOR_END ${node.id} の所属forが実行経路と一致しません"
                    }
                    visit(node.next, if (forStack.isEmpty()) forStack else forStack.dropLast(1))
                }
                CommandType.CONDITION -> {
                    visit(node.trueNext, forStack)
                    visit(node.falseNext, forStack)
                }
                else -> visit(node.next, forStack)
            }
        }
        visit(entry, emptyList())
    }

    private fun validateCondition(graph: CommandGraph, node: CommandNode, errors: MutableList<String>) {
        val mergeId = node.pairedNodeId ?: return
        validatePair(graph, node, CommandType.MERGE, errors)
        if (!allPathsReachMergeOrEnd(graph, node.trueNext, mergeId)) {
            errors += "CONDITION ${node.id} のtrue枝が対応合流または終端へ到達しません"
        }
        if (!allPathsReachMergeOrEnd(graph, node.falseNext, mergeId)) {
            errors += "CONDITION ${node.id} のfalse枝が対応合流または終端へ到達しません"
        }
    }

    private fun validateForStart(graph: CommandGraph, node: CommandNode, errors: MutableList<String>) {
        val endId = node.pairedNodeId ?: run {
            validatePair(graph, node, CommandType.FOR_END, errors)
            return
        }
        validatePair(graph, node, CommandType.FOR_END, errors)
        if (!allPathsReach(graph, node.trueNext, endId)) {
            errors += "FOR_START ${node.id} のbodyが対応for終了へ到達しません"
        }
    }

    private fun validatePair(
        graph: CommandGraph,
        node: CommandNode,
        expected: CommandType,
        errors: MutableList<String>,
    ) {
        val pair = node.pairedNodeId?.let(graph.nodes::get)
        if (pair?.type != expected || pair.pairedNodeId != node.id) {
            errors += "${node.type} ${node.id} の対応ノードが不正です"
        }
    }

    private fun validateIncomingEdges(graph: CommandGraph, errors: MutableList<String>) {
        val incoming = mutableMapOf<UUID, Int>()
        graph.nodes.values.forEach { node ->
            node.outgoing().forEach { target -> incoming[target] = (incoming[target] ?: 0) + 1 }
        }
        graph.nodes.values.forEach { node ->
            val count = incoming[node.id] ?: 0
            // MERGEだけでなくFOR_ENDも、ループ本体から到達する複数の条件枝を
            // 受ける暗黙の境界です。for本体内の未合流条件は、明示的なMERGEを
            // 追加しなくても、両枝を同じFOR_ENDへ到達させられます。
            val allowed = when {
                node.type == CommandType.MERGE || node.type == CommandType.FOR_END -> Int.MAX_VALUE
                node.id == graph.entryNodeId -> 0
                else -> 1
            }
            if (count > allowed) {
                errors += "${node.type} ${node.id} に複数の親があります: $count"
            }
        }
    }

    /** MERGEを持つ条件でも、枝ごとの正常終了（null終端）を許可します。 */
    private fun allPathsReachMergeOrEnd(graph: CommandGraph, start: UUID?, stop: UUID): Boolean {
        val active = mutableSetOf<UUID>()
        val memo = mutableMapOf<UUID, Boolean>()
        fun visit(id: UUID?): Boolean {
            if (id == stop || id == null) return true
            if (!graph.nodes.containsKey(id)) return false
            memo[id]?.let { return it }
            if (!active.add(id)) return false
            val node = graph.nodes.getValue(id)
            val outgoing = node.outgoing()
            // outgoingが空の実行ノードは、その枝で正常終了する終端です。
            // これを許可しないと、条件枝の末端へ到達した時点で不正扱いになり、
            // 二分木状の早期終了を構築できません。
            val result = outgoing.isEmpty() || outgoing.all(::visit)
            active.remove(id)
            memo[id] = result
            return result
        }
        return visit(start)
    }

    /** forのbodyは必ず対応FOR_ENDへ到達し、途中終了しない構造を維持します。 */
    private fun allPathsReach(graph: CommandGraph, start: UUID?, stop: UUID): Boolean {
        val active = mutableSetOf<UUID>()
        val memo = mutableMapOf<UUID, Boolean>()
        fun visit(id: UUID?): Boolean {
            if (id == stop) return true
            if (id == null || !graph.nodes.containsKey(id)) return false
            memo[id]?.let { return it }
            if (!active.add(id)) return false
            val node = graph.nodes.getValue(id)
            val result = node.outgoing().isNotEmpty() && node.outgoing().all(::visit)
            active.remove(id)
            memo[id] = result
            return result
        }
        return visit(start)
    }

    private fun CommandNode.outgoing(): List<UUID> =
        when (type) {
            CommandType.CONDITION -> listOfNotNull(trueNext, falseNext)
            CommandType.FOR_START -> listOfNotNull(trueNext)
            else -> listOfNotNull(next)
        }
}

data class GraphLimits(
    val maximumNodeCount: Int = 512,
    val maximumMapWidth: Int = 1024,
    val maximumMapHeight: Int = 256,
    val maximumBranchDepth: Int = 32,
)
