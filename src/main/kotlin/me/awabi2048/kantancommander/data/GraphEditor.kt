package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

/**
 * 構造だけを永続化し、描画座標を保持しないグラフ編集器です。
 */
object GraphEditor {
    fun canAppendMerge(graph: CommandGraph?, conditionId: UUID?): Boolean {
        val condition = conditionId?.let { graph?.nodes?.get(it) } ?: return false
        return condition.type == CommandType.CONDITION && condition.pairedNodeId == null
    }

    @Deprecated("レーン式GUIの置換完了後に削除します")
    fun canAppendMerge(graph: CommandGraph?, legacyLane: Int): Boolean {
        if (legacyLane <= 0) return false
        val conditionId = graph?.nodes?.values
            ?.filter { it.type == CommandType.CONDITION }
            ?.getOrNull(legacyLane - 1)
            ?.id
        return canAppendMerge(graph, conditionId)
    }

    /**
     * 既存GUIとの互換入口です。branchConditionId指定時はfalse枝、未指定時は主経路末尾へ追加します。
     * MERGEは必ずbranchConditionIdで対応条件を明示します。
     */
    fun append(graph: CommandGraph, type: CommandType, branchConditionId: UUID? = null): CommandNode {
        if (type == CommandType.MERGE) {
            requireNotNull(branchConditionId) { "合流には対応する条件分岐が必要です" }
            return appendMerge(graph, branchConditionId)
        }
        val inserted = createBundle(graph, type)
        if (graph.entryNodeId == null) {
            graph.entryNodeId = inserted.id
            return inserted
        }
        if (branchConditionId == null) appendToMainPath(graph, inserted)
        else appendToFalseBranch(graph, branchConditionId, inserted)
        return inserted
    }

    fun appendMerge(graph: CommandGraph, conditionId: UUID): CommandNode {
        val condition = graph.nodes[conditionId]
            ?.takeIf { it.type == CommandType.CONDITION }
            ?: error("対象の条件分岐が存在しません")
        require(condition.pairedNodeId == null) { "条件分岐には既に対応する合流があります" }

        val merge = CommandType.MERGE.newNode()
        graph.nodes[merge.id] = merge
        condition.pairedNodeId = merge.id
        merge.pairedNodeId = condition.id
        connectOpenTail(graph, condition.trueNext, condition, true, merge.id)
        connectOpenTail(graph, condition.falseNext, condition, false, merge.id)
        return merge
    }

    private fun createBundle(graph: CommandGraph, type: CommandType): CommandNode {
        require(type !in setOf(CommandType.MERGE, CommandType.FOR_END)) {
            "$type は単独追加できません"
        }
        val node = type.newNode()
        graph.nodes[node.id] = node
        if (type == CommandType.FOR_START) {
            val end = CommandType.FOR_END.newNode()
            node.pairedNodeId = end.id
            end.pairedNodeId = node.id
            node.next = end.id
            graph.nodes[end.id] = end
        }
        return node
    }

    private fun appendToMainPath(graph: CommandGraph, inserted: CommandNode) {
        val tail = findOpenTail(graph, graph.entryNodeId ?: error("開始ノードがありません"), preferTrue = true)
        connect(tail, inserted.id, preferTrue = true)
    }

    private fun appendToFalseBranch(graph: CommandGraph, conditionId: UUID, inserted: CommandNode) {
        val condition = graph.nodes[conditionId]
            ?.takeIf { it.type == CommandType.CONDITION }
            ?: error("対象の条件分岐が存在しません")
        val stop = condition.pairedNodeId
        val start = condition.falseNext
        if (start == null || start == stop) {
            condition.falseNext = inserted.id
            connectBundleTail(graph, inserted, stop)
            return
        }
        val tail = findOpenTail(graph, start, preferTrue = true, stop = stop)
        connect(tail, inserted.id, preferTrue = true)
        connectBundleTail(graph, inserted, stop)
    }

    private fun connectOpenTail(
        graph: CommandGraph,
        start: UUID?,
        condition: CommandNode,
        trueBranch: Boolean,
        target: UUID,
    ) {
        if (start == null) {
            if (trueBranch) condition.trueNext = target else condition.falseNext = target
            return
        }
        val tail = findOpenTail(graph, start, preferTrue = true)
        connect(tail, target, preferTrue = true)
    }

    private fun findOpenTail(
        graph: CommandGraph,
        start: UUID,
        preferTrue: Boolean,
        stop: UUID? = null,
    ): CommandNode {
        var currentId = start
        val visited = mutableSetOf<UUID>()
        while (visited.add(currentId)) {
            val current = graph.nodes[currentId] ?: error("存在しないノードです: $currentId")
            val next = when (current.type) {
                CommandType.CONDITION -> if (preferTrue) current.trueNext else current.falseNext
                else -> current.next
            }
            if (next == null || next == stop) return current
            currentId = next
        }
        error("循環したグラフには追加できません")
    }

    private fun connectBundleTail(graph: CommandGraph, inserted: CommandNode, target: UUID?) {
        val tail = if (inserted.type == CommandType.FOR_START) {
            inserted.pairedNodeId?.let(graph.nodes::get) ?: inserted
        } else inserted
        if (target != null) tail.next = target
    }

    private fun connect(source: CommandNode, target: UUID, preferTrue: Boolean) {
        if (source.type == CommandType.CONDITION) {
            if (preferTrue) source.trueNext = target else source.falseNext = target
        } else {
            source.next = target
        }
    }
}
