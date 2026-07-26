package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

object GraphEditor {
    fun canAppendMerge(graph: CommandGraph?, lane: Int): Boolean {
        if (graph == null || lane <= 0) return false
        val condition = graph.nodes.values
            .filter { it.type == CommandType.CONDITION }
            .getOrNull(lane - 1)
            ?: return false
        return condition.pairedNodeId == null
    }

    fun append(graph: CommandGraph, type: CommandType, branchConditionId: UUID? = null): CommandNode {
        val inserted = createBundle(graph, type)
        if (graph.entryNodeId == null) {
            graph.entryNodeId = inserted.id
            return inserted
        }

        if (branchConditionId != null) {
            appendToFalseBranch(graph, branchConditionId, inserted)
        } else {
            appendToMainPath(graph, inserted)
        }
        return inserted
    }

    private fun createBundle(graph: CommandGraph, type: CommandType): CommandNode {
        val node = type.newNode()
        graph.nodes[node.id] = node
        if (type == CommandType.CONDITION) {
            val merge = CommandType.MERGE.newNode()
            node.trueNext = merge.id
            node.falseNext = merge.id
            node.pairedNodeId = merge.id
            merge.pairedNodeId = node.id
            graph.nodes[merge.id] = merge
        }
        return node
    }

    private fun appendToMainPath(graph: CommandGraph, inserted: CommandNode) {
        var currentId = graph.entryNodeId ?: error("開始ノードがありません")
        val visited = mutableSetOf<UUID>()
        while (visited.add(currentId)) {
            val current = graph.nodes[currentId] ?: error("存在しないノードです: $currentId")
            val next = if (current.type == CommandType.CONDITION) current.trueNext else current.next
            if (next == null) {
                connect(current, inserted.id)
                return
            }
            currentId = next
        }
        error("循環したグラフには追加できません")
    }

    private fun appendToFalseBranch(graph: CommandGraph, conditionId: UUID, inserted: CommandNode) {
        val condition = graph.nodes[conditionId]
            ?.takeIf { it.type == CommandType.CONDITION }
            ?: error("対象の条件分岐が存在しません")
        val mergeId = condition.pairedNodeId ?: error("対応合流がありません")
        var currentId = condition.falseNext ?: error("false枝がありません")

        if (currentId == mergeId) {
            condition.falseNext = inserted.id
            connectBundleTail(graph, inserted, mergeId)
            return
        }

        val visited = mutableSetOf<UUID>()
        while (visited.add(currentId)) {
            val current = graph.nodes[currentId] ?: error("存在しないノードです: $currentId")
            val next = (if (current.type == CommandType.CONDITION) current.trueNext else current.next)
                ?: error("false枝が対応合流へ到達しません")
            if (next == mergeId) {
                connect(current, inserted.id)
                connectBundleTail(graph, inserted, mergeId)
                return
            }
            currentId = next
        }
        error("循環したfalse枝には追加できません")
    }

    private fun connectBundleTail(graph: CommandGraph, inserted: CommandNode, target: UUID) {
        if (inserted.type == CommandType.CONDITION) {
            val merge = inserted.pairedNodeId?.let(graph.nodes::get) ?: error("追加した条件の合流がありません")
            merge.next = target
        } else {
            inserted.next = target
        }
    }

    private fun connect(source: CommandNode, target: UUID) {
        if (source.type == CommandType.CONDITION) source.trueNext = target else source.next = target
    }
}
