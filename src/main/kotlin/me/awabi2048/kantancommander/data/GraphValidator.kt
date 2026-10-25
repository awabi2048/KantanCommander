package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.error.ErrorCode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

/**
 * 構造検証の構造化エラーです。
 *
 * 旧来の List<String> では nodeId が文面へ埋没し、ExecutableScriptValidator 側で
 * emptySet() 化されて GUI へ届きませんでした。issue #6 の共通分類として
 * code と nodeId を保持し、表示文解析なしに該当ノードへ紐付けます。
 */
data class GraphStructuralError(
    val code: ErrorCode,
    val nodeId: UUID?,
    val message: String,
)

object GraphValidator {
    /** 旧来の文字列 API です。既存呼出との互換のため残します。 */
    fun validate(graph: CommandGraph, limits: GraphLimits = GraphLimits()): List<String> =
        validateStructured(graph, limits).map { it.message }.distinct()

    /** 共通分類付きの構造検証です。新規呼出はこちらを使用します。 */
    fun validateStructured(graph: CommandGraph, limits: GraphLimits = GraphLimits()): List<GraphStructuralError> {
        val errors = mutableListOf<GraphStructuralError>()
        // 上限判定は内部グラフも含めた総ノード数で行います。
        if (FoldedGraphs.totalNodeCount(graph) > limits.maximumNodeCount) {
            errors.add(ErrorCode.GRAPH_NODE_LIMIT, null, "ノード数が上限 ${limits.maximumNodeCount} を超えています")
        }
        // 折り畳みノードの中身は外側に通常形状で1個だけ残るため、構造検証は
        // メモリ上で展開した一時グラフに対して既存ロジックを適用します。
        // 保存内容は折り畳んだまま維持されます。
        val expanded = if (graph.nodes.values.any(FoldedGraphs::isFolded)) {
            graph.nodes.values.filter(FoldedGraphs::isFolded).forEach { validateFoldedWrapper(graph, it, errors) }
            FoldedGraphs.expand(graph, limits.maximumBranchDepth)
        } else {
            graph
        }
        validateExpanded(expanded, limits, errors)
        return errors.distinct()
    }

    private fun MutableList<GraphStructuralError>.add(code: ErrorCode, nodeId: UUID?, message: String) {
        this += GraphStructuralError(code, nodeId, message)
    }

    /**
     * 折り畳みノード外側の接続形状を検証します。
     *
     * 外側は通常形状（単一next・ペアなし・枝なし）でなければならず、中身の
     * 先頭は対応する種別で同じIDを保つ必要があります。中身の構造自体は
     * 展開後グラフの検証で確認します。
     */
    private fun validateFoldedWrapper(graph: CommandGraph, node: CommandNode, errors: MutableList<GraphStructuralError>) {
        val inner = node.foldedGraph
        if (inner == null) {
            errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} の内部グラフがありません")
            return
        }
        if (node.trueNext != null || node.falseNext != null || node.pairedNodeId != null) {
            errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} の外側接続が通常形状ではありません")
        }
        val head = inner.nodes[node.id]
        if (head == null) {
            errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} の内部先頭が存在しません")
            return
        }
        when (node.type) {
            CommandType.FOLDED_FOR -> {
                if (head.type != CommandType.FOR_START) {
                    errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} の内部先頭がFOR開始ではありません")
                } else if (head.pairedNodeId?.let(inner.nodes::get)
                        ?.takeIf { it.type == CommandType.FOR_END && it.pairedNodeId == head.id } == null
                ) {
                    errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} の内部FOR対応が不正です")
                }
            }
            CommandType.FOLDED_IF -> {
                if (head.type != CommandType.CONDITION) {
                    errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} の内部先頭が条件分岐ではありません")
                } else if (head.pairedNodeId?.let(inner.nodes::get)
                        ?.takeIf { it.type == CommandType.MERGE && it.pairedNodeId == head.id } == null
                ) {
                    errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} の内部合流対応が不正です")
                }
            }
            else -> errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} は折り畳みノードではありません")
        }
        if (inner.entryNodeId != node.id) {
            errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} の内部入口が外側IDと一致しません")
        }
    }

    private fun validateExpanded(graph: CommandGraph, limits: GraphLimits, errors: MutableList<GraphStructuralError>) {
        val entry = graph.entryNodeId
        if (entry == null) {
            if (graph.nodes.isNotEmpty()) errors.add(ErrorCode.MISSING_NODE, null, "開始ノードがありません")
            return
        }
        if (entry !in graph.nodes) errors.add(ErrorCode.DANGLING_REFERENCE, entry, "開始ノードが存在しません")

        val duplicateIds = graph.nodes.values.groupingBy(CommandNode::id).eachCount().filterValues { it > 1 }.keys
        duplicateIds.forEach { errors.add(ErrorCode.GRAPH_DUPLICATE_ID, it, "ノードIDが重複しています: $it") }
        graph.nodes.forEach { (key, node) ->
            if (key != node.id) errors.add(ErrorCode.GRAPH_KEY_MISMATCH, node.id, "ノードの保存キーとIDが一致しません: key=$key id=${node.id}")
            node.outgoing().filterNot(graph.nodes::containsKey).forEach {
                errors.add(ErrorCode.GRAPH_UNKNOWN_REFERENCE, node.id, "${node.id} が存在しないノード $it を参照しています")
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
            // ノード連鎖の長さだけ再帰が深くなる経路を、ENTER／EXIT の明示
            // スタックへ置き換えています。active は探索中の経路集合で、
            // 循環検出と枝深度の判定は入口側の処理順と同じです。
            val stack = ArrayDeque<Triple<UUID, Int, Boolean>>()
            stack.addLast(Triple(entry, 0, false))
            while (stack.isNotEmpty()) {
                val (id, depth, exiting) = stack.removeLast()
                if (exiting) {
                    active.remove(id)
                    continue
                }
                if (depth > limits.maximumBranchDepth) {
                    errors.add(ErrorCode.GRAPH_DEPTH_EXCEEDED, id, "構造の深さが上限 ${limits.maximumBranchDepth} を超えています")
                    continue
                }
                if (!active.add(id)) {
                    errors.add(ErrorCode.GRAPH_CYCLE, id, "実行グラフに循環があります: $id")
                    continue
                }
                if (!visited.add(id)) {
                    active.remove(id)
                    continue
                }
                val node = graph.nodes[id]
                val nextDepth = when (node?.type) {
                    CommandType.CONDITION, CommandType.FOR_START -> depth + 1
                    CommandType.MERGE, CommandType.FOR_END -> (depth - 1).coerceAtLeast(0)
                    else -> depth
                }
                stack.addLast(Triple(id, depth, true))
                node?.outgoing()?.asReversed()?.forEach { stack.addLast(Triple(it, nextDepth, false)) }
            }
            validateStructuredControl(graph, entry, errors)
            (graph.nodes.keys - visited).forEach { errors.add(ErrorCode.GRAPH_UNREACHABLE, it, "到達不能ノードがあります: $it") }
        }
    }

    private fun validateStructuredControl(
        graph: CommandGraph,
        entry: UUID,
        errors: MutableList<GraphStructuralError>,
    ) {
        val visited = mutableSetOf<Pair<UUID, List<UUID>>>()
        // for所属スタックを持ち回る走査です。ノード連鎖の長さだけ深くなる
        // 再帰を明示スタックへ置き換えています。（id, forStack）単位の
        // 訪問管理は元の再帰と同じで、訪問順は結果へ影響しません。
        val stack = ArrayDeque<Pair<UUID?, List<UUID>>>()
        stack.addLast(entry to emptyList())
        while (stack.isNotEmpty()) {
            val (id, forStack) = stack.removeLast()
            if (id == null || !visited.add(id to forStack)) continue
            val node = graph.nodes[id] ?: continue
            when (node.type) {
                CommandType.BREAK, CommandType.CONTINUE -> {
                    if (forStack.isEmpty()) {
                        errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} はfor本体の外では使用できません")
                    }
                }
                CommandType.FOR_START -> {
                    val endId = node.pairedNodeId ?: continue
                    stack.addLast(node.trueNext to (forStack + node.id))
                    stack.addLast(graph.nodes[endId]?.next to forStack)
                }
                CommandType.FOR_END -> {
                    val expectedStart = forStack.lastOrNull()
                    if (node.pairedNodeId != expectedStart) {
                        errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "FOR_END ${node.id} の所属forが実行経路と一致しません")
                    }
                    stack.addLast(node.next to (if (forStack.isEmpty()) forStack else forStack.dropLast(1)))
                }
                CommandType.CONDITION -> {
                    stack.addLast(node.trueNext to forStack)
                    stack.addLast(node.falseNext to forStack)
                }
                else -> stack.addLast(node.next to forStack)
            }
        }
    }

    private fun validateCondition(graph: CommandGraph, node: CommandNode, errors: MutableList<GraphStructuralError>) {
        val mergeId = node.pairedNodeId ?: return
        validatePair(graph, node, CommandType.MERGE, errors)
        if (!allPathsReachMergeOrEnd(graph, node.trueNext, mergeId)) {
            errors.add(ErrorCode.GRAPH_UNREACHABLE, node.id, "CONDITION ${node.id} のtrue枝が対応合流または終端へ到達しません")
        }
        if (!allPathsReachMergeOrEnd(graph, node.falseNext, mergeId)) {
            errors.add(ErrorCode.GRAPH_UNREACHABLE, node.id, "CONDITION ${node.id} のfalse枝が対応合流または終端へ到達しません")
        }
    }

    private fun validateForStart(graph: CommandGraph, node: CommandNode, errors: MutableList<GraphStructuralError>) {
        val endId = node.pairedNodeId ?: run {
            validatePair(graph, node, CommandType.FOR_END, errors)
            return
        }
        validatePair(graph, node, CommandType.FOR_END, errors)
        if (!allPathsReach(graph, node.trueNext, endId)) {
            errors.add(ErrorCode.GRAPH_UNREACHABLE, node.id, "FOR_START ${node.id} のbodyが対応for終了へ到達しません")
        }
    }

    private fun validatePair(
        graph: CommandGraph,
        node: CommandNode,
        expected: CommandType,
        errors: MutableList<GraphStructuralError>,
    ) {
        val pair = node.pairedNodeId?.let(graph.nodes::get)
        if (pair?.type != expected || pair.pairedNodeId != node.id) {
            errors.add(ErrorCode.GRAPH_INVALID_PAIR, node.id, "${node.type} ${node.id} の対応ノードが不正です")
        }
    }

    private fun validateIncomingEdges(graph: CommandGraph, errors: MutableList<GraphStructuralError>) {
        val incoming = mutableMapOf<UUID, Int>()
        graph.nodes.values.forEach { node ->
            node.outgoing().forEach { target -> incoming[target] = (incoming[target] ?: 0) + 1 }
        }
        graph.nodes.values.forEach { node ->
            val count = incoming[node.id] ?: 0
            // 複数の実行エッジを受け取れるのは明示的なMERGEだけです。FOR_ENDを
            // 暗黙の合流点として扱うと、未合流条件の終端枝までループへ接続され、
            // 構造データと利用者が選択した枝の意味が変わってしまいます。
            val allowed = when {
                node.type == CommandType.MERGE -> Int.MAX_VALUE
                node.id == graph.entryNodeId -> 0
                else -> 1
            }
            if (count > allowed) {
                errors.add(ErrorCode.GRAPH_MULTIPLE_PARENTS, node.id, "${node.type} ${node.id} に複数の親があります: $count")
            }
        }
    }

    /** MERGEを持つ条件でも、枝ごとの正常終了（null終端）を許可します。 */
    private fun allPathsReachMergeOrEnd(graph: CommandGraph, start: UUID?, stop: UUID): Boolean =
        allPathsReach(graph, start, stop)

    /**
     * startからの全経路が stop または終端へ到達するかを判定します。
     *
     * MERGEの条件枝・FOR_STARTのbodyのどちらにも使い、枝ごとの正常終了
     * （null終端）と行き止まりの参照切れを区別します。ノード連鎖の長さだけ
     * 深くなる再帰を明示スタックへ置き換えています。戻り値の意味は、
     * 子の評価結果を lastResult で親へ返す点を除いて元の再帰と同じです。
     */
    private fun allPathsReach(graph: CommandGraph, start: UUID?, stop: UUID): Boolean {
        if (start == null || start == stop) return true
        if (start !in graph.nodes) return false
        class Frame(val id: UUID, val children: List<UUID>, var nextChild: Int)
        val memo = mutableMapOf<UUID, Boolean>()
        val active = mutableSetOf<UUID>()
        val stack = ArrayDeque<Frame>()
        active += start
        stack.addLast(Frame(start, graph.nodes.getValue(start).outgoing(), 0))
        var lastResult = true
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            if (!lastResult) {
                // 子の失敗は全経路判定の失敗として確定します。
                active.remove(frame.id)
                stack.removeLast()
                memo[frame.id] = false
                continue
            }
            if (frame.nextChild < frame.children.size) {
                val child = frame.children[frame.nextChild++]
                when {
                    // 合流点への到達はその枝の成功です。
                    child == stop -> lastResult = true
                    child !in graph.nodes -> lastResult = false
                    memo.containsKey(child) -> lastResult = memo.getValue(child)
                    // 探索中の経路への再接続（循環）は合流にも終端にも届きません。
                    !active.add(child) -> lastResult = false
                    else -> {
                        stack.addLast(Frame(child, graph.nodes.getValue(child).outgoing(), 0))
                        lastResult = true
                    }
                }
            } else {
                // 全子成功、または子のない終端は成功です。
                active.remove(frame.id)
                stack.removeLast()
                memo[frame.id] = true
                lastResult = true
            }
        }
        return memo.getValue(start)
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
