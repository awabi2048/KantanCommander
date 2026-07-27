package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

/**
 * 構造だけを永続化し、描画座標を保持しないグラフ編集器です。
 */
object GraphEditor {
    enum class Edge { ENTRY, NEXT, TRUE, FALSE, FOR_BODY }

    fun canAppendMerge(graph: CommandGraph?, conditionId: UUID?): Boolean {
        val condition = conditionId?.let { graph?.nodes?.get(it) } ?: return false
        return condition.type == CommandType.CONDITION && condition.pairedNodeId == null
    }

    fun isInsideFor(graph: CommandGraph, sourceId: UUID?, edge: Edge): Boolean {
        if (sourceId == null) return false
        if (edge == Edge.FOR_BODY && graph.nodes[sourceId]?.type == CommandType.FOR_START) return true
        return graph.nodes.values
            .asSequence()
            .filter { it.type == CommandType.FOR_START && it.pairedNodeId != null }
            .any { start -> containsNodeBefore(graph, start.trueNext, start.pairedNodeId, sourceId) }
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

    fun appendToForBody(graph: CommandGraph, forStartId: UUID, type: CommandType): CommandNode {
        val start = graph.nodes[forStartId]
            ?.takeIf { it.type == CommandType.FOR_START }
            ?: error("対象のfor開始が存在しません")
        val endId = start.pairedNodeId ?: error("対応するfor終了がありません")
        val inserted = createBundle(graph, type)
        val body = start.trueNext
        if (body == null || body == endId) {
            start.trueNext = inserted.id
            connectBundleTail(graph, inserted, endId)
        } else {
            val tail = findOpenTail(graph, body, preferTrue = true, stop = endId)
            connect(tail, inserted.id, preferTrue = true)
            connectBundleTail(graph, inserted, endId)
        }
        return inserted
    }

    fun insert(graph: CommandGraph, sourceId: UUID?, edge: Edge, type: CommandType): CommandNode {
        require(type !in setOf(CommandType.MERGE, CommandType.FOR_END)) { "$type はこの経路へ挿入できません" }
        val target = edgeTarget(graph, sourceId, edge)
        val inserted = createBundle(graph, type)
        setEdge(graph, sourceId, edge, inserted.id)
        connectBundleTail(graph, inserted, target)
        if (inserted.type == CommandType.CONDITION) {
            inserted.next = null
            inserted.trueNext = target
            inserted.falseNext = null
        }
        return inserted
    }

    fun delete(graph: CommandGraph, nodeId: UUID): Boolean {
        val node = graph.nodes[nodeId] ?: return false
        return when (node.type) {
            CommandType.CONDITION -> deleteCondition(graph, node)
            CommandType.MERGE -> deleteMerge(graph, node)
            CommandType.FOR_START, CommandType.FOR_END -> deleteFor(graph, node)
            else -> deleteSimple(graph, node)
        }
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
            node.trueNext = end.id
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
                CommandType.FOR_START -> current.pairedNodeId
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

    private fun deleteSimple(graph: CommandGraph, node: CommandNode): Boolean {
        replaceIncoming(graph, node.id, node.next)
        graph.nodes.remove(node.id)
        return true
    }

    private fun deleteCondition(graph: CommandGraph, condition: CommandNode): Boolean {
        val merge = condition.pairedNodeId?.let(graph.nodes::get)
        val stop = merge?.id
        if (containsExecutionBefore(graph, condition.falseNext, stop)) return false
        val promoted = if (condition.trueNext == stop) merge?.next else condition.trueNext
        replaceIncoming(graph, condition.id, promoted)
        if (merge != null) {
            replaceIncoming(graph, merge.id, merge.next, excluded = setOf(condition.id))
            graph.nodes.remove(merge.id)
        }
        graph.nodes.remove(condition.id)
        return true
    }

    private fun deleteMerge(graph: CommandGraph, merge: CommandNode): Boolean {
        val condition = merge.pairedNodeId?.let(graph.nodes::get) ?: return false
        val after = merge.next
        val trueTail = branchTail(graph, condition.trueNext, merge.id)
        disconnectIncoming(graph, merge.id)
        condition.pairedNodeId = null
        condition.trueNext = condition.trueNext.takeUnless { it == merge.id } ?: after
        condition.falseNext = condition.falseNext.takeUnless { it == merge.id }
        if (trueTail != null && trueTail.id != condition.id) trueTail.next = after
        graph.nodes.remove(merge.id)
        return true
    }

    private fun deleteFor(graph: CommandGraph, node: CommandNode): Boolean {
        val start = if (node.type == CommandType.FOR_START) node else node.pairedNodeId?.let(graph.nodes::get)
            ?: return false
        val end = start.pairedNodeId?.let(graph.nodes::get) ?: return false
        if (start.trueNext != end.id) return false
        replaceIncoming(graph, start.id, end.next)
        graph.nodes.remove(start.id)
        graph.nodes.remove(end.id)
        return true
    }

    private fun containsExecutionBefore(graph: CommandGraph, start: UUID?, stop: UUID?): Boolean {
        if (start == null || start == stop) return false
        val visited = mutableSetOf<UUID>()
        fun visit(id: UUID?): Boolean {
            if (id == null || id == stop || !visited.add(id)) return false
            val node = graph.nodes[id] ?: return false
            return node.type != CommandType.MERGE || node.outgoingIds().any(::visit)
        }
        return visit(start)
    }

    private fun containsNodeBefore(graph: CommandGraph, start: UUID?, stop: UUID?, target: UUID): Boolean {
        val visited = mutableSetOf<UUID>()
        fun visit(id: UUID?): Boolean {
            if (id == null || id == stop || !visited.add(id)) return false
            if (id == target) return true
            return graph.nodes[id]?.outgoingIds()?.any(::visit) == true
        }
        return visit(start)
    }

    private fun branchTail(graph: CommandGraph, start: UUID?, stop: UUID): CommandNode? {
        var current = start ?: return null
        var previous: CommandNode? = null
        val visited = mutableSetOf<UUID>()
        while (current != stop && visited.add(current)) {
            previous = graph.nodes[current] ?: return previous
            current = if (previous.type == CommandType.CONDITION) previous.trueNext ?: return previous
            else previous.next ?: return previous
        }
        return previous
    }

    private fun replaceIncoming(
        graph: CommandGraph,
        target: UUID,
        replacement: UUID?,
        excluded: Set<UUID> = emptySet(),
    ) {
        if (graph.entryNodeId == target) graph.entryNodeId = replacement
        graph.nodes.values.filterNot { it.id in excluded }.forEach { node ->
            if (node.next == target) node.next = replacement
            if (node.trueNext == target) node.trueNext = replacement
            if (node.falseNext == target) node.falseNext = replacement
        }
    }

    private fun disconnectIncoming(graph: CommandGraph, target: UUID) {
        graph.nodes.values.forEach { node ->
            if (node.next == target) node.next = null
            if (node.trueNext == target) node.trueNext = null
            if (node.falseNext == target) node.falseNext = null
        }
    }

    private fun edgeTarget(graph: CommandGraph, sourceId: UUID?, edge: Edge): UUID? {
        if (edge == Edge.ENTRY) return graph.entryNodeId
        val source = sourceId?.let(graph.nodes::get) ?: error("挿入元ノードが存在しません")
        return when (edge) {
            Edge.NEXT -> source.next
            Edge.TRUE -> source.trueNext
            Edge.FALSE -> source.falseNext
            Edge.FOR_BODY -> source.trueNext
            Edge.ENTRY -> graph.entryNodeId
        }
    }

    private fun setEdge(graph: CommandGraph, sourceId: UUID?, edge: Edge, target: UUID?) {
        if (edge == Edge.ENTRY) {
            graph.entryNodeId = target
            return
        }
        val source = sourceId?.let(graph.nodes::get) ?: error("挿入元ノードが存在しません")
        when (edge) {
            Edge.NEXT -> source.next = target
            Edge.TRUE -> source.trueNext = target
            Edge.FALSE -> source.falseNext = target
            Edge.FOR_BODY -> source.trueNext = target
            Edge.ENTRY -> Unit
        }
    }

    private fun CommandNode.outgoingIds() =
        when (type) {
            CommandType.CONDITION -> listOfNotNull(trueNext, falseNext)
            CommandType.FOR_START -> listOfNotNull(trueNext, pairedNodeId)
            else -> listOfNotNull(next)
        }
}
