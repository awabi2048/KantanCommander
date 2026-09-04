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

    /**
     * 単独ノードとして複製できない構造ノードです。
     *
     * CONDITION／FOR_STARTだけを除外すると、それらの対応ノードであるMERGE／FOR_END
     * が通常ノードとして複製され、分岐・ループの対応関係を壊します。構造の入口と
     * 境界を同じ集合で扱い、画面表示と保存処理の判定を一つに揃えます。
     */
    private val STRUCTURAL_TYPES = setOf(
        CommandType.CONDITION,
        CommandType.MERGE,
        CommandType.FOR_START,
        CommandType.FOR_END,
    )

    /**
     * ビューポート上の左右入れ替え操作で使う方向です。
     *
     * グラフは表示座標を保存しないため、左右のボタンは座標だけを動かすのではなく、
     * 同一の直列経路にある実行単位の `next` 関係を入れ替えます。条件分岐やforの
     * ペアを途中で分解しないよう、構造ノードはこの操作の対象外にします。
     */
    enum class ReorderDirection { LEFT, RIGHT }

    /**
     * 同一直列経路にある隣接ノードを入れ替えます。
     *
     * 条件分岐／合流／for開始・終了は、分岐やペアの構造を一組で扱う別操作が必要です。
     * それらを単純な `next` 入れ替えへ通すと、TRUE/FALSEの意味やforの対応関係を壊す
     * ため、ここでは安全側に拒否します。通常実行ノードとbreak/continueは、親が
     * 分岐の枝であっても同じ枝内の隣接ノードとして入れ替えられます。
     */
    fun swapAdjacent(graph: CommandGraph, nodeId: UUID, direction: ReorderDirection): Boolean {
        val node = graph.nodes[nodeId] ?: return false
        if (!isLinearReorderable(node)) return false

        return when (direction) {
            ReorderDirection.LEFT -> swapWithPrevious(graph, node)
            ReorderDirection.RIGHT -> swapWithNext(graph, node)
        }
    }

    /** 表示前に左右ボタンを有効化できるかを、グラフを変更せず判定します。 */
    fun canSwapAdjacent(graph: CommandGraph, nodeId: UUID, direction: ReorderDirection): Boolean {
        val node = graph.nodes[nodeId] ?: return false
        if (!isLinearReorderable(node)) return false
        return when (direction) {
            ReorderDirection.LEFT -> {
                val incoming = incomingExecutionLink(graph, node.id) ?: return false
                incoming.edge == Edge.NEXT &&
                    incoming.sourceId?.let(graph.nodes::get)?.let(::isLinearReorderable) == true
            }
            ReorderDirection.RIGHT -> {
                val next = node.next?.let(graph.nodes::get) ?: return false
                isLinearReorderable(next) && incomingExecutionLink(graph, node.id) != null
            }
        }
    }

    /** 構造を分解せず、設定ノード単位で複製できる種類かを判定します。 */
    fun isDuplicable(node: CommandNode): Boolean = node.type !in STRUCTURAL_TYPES

    /** 複製ボタンの表示・クリック受理で共有する上限を含む可否判定です。 */
    fun canDuplicate(
        graph: CommandGraph,
        nodeId: UUID,
        maximumNodeCount: Int = Int.MAX_VALUE,
    ): Boolean = graph.nodes.size < maximumNodeCount && graph.nodes[nodeId]?.let(::isDuplicable) == true

    fun canAppendMerge(graph: CommandGraph?, conditionId: UUID?, continuationId: UUID? = null): Boolean {
        val currentGraph = graph ?: return false
        val condition = conditionId?.let { currentGraph.nodes[it] } ?: return false
        if (continuationId != null && (continuationId !in currentGraph.nodes || continuationId == condition.id)) return false
        return condition.type == CommandType.CONDITION &&
            condition.pairedNodeId == null &&
            !containsUnmergedCondition(currentGraph, condition.trueNext, condition.id, continuationId) &&
            !containsUnmergedCondition(currentGraph, condition.falseNext, condition.id, continuationId)
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

    /**
     * 未合流条件へMERGEを追加します。
     *
     * 入れ子の未合流条件を親の枝内で閉じる場合、両枝の終端が親の合流へ直結して
     * いることがあります。その親合流を `continuationId` として受け取り、既存の
     * 直結を新しい内側MERGEへ付け替えてから、内側MERGEのNEXTへ親合流を接続します。
     * これにより、親MERGEの入力が3本になることや、親の実行経路を書き換えることを
     * 防ぎます。
     */
    fun appendMerge(graph: CommandGraph, conditionId: UUID, continuationId: UUID? = null): CommandNode {
        val condition = graph.nodes[conditionId]
            ?.takeIf { it.type == CommandType.CONDITION }
            ?: error("対象の条件分岐が存在しません")
        require(condition.pairedNodeId == null) { "条件分岐には既に対応する合流があります" }
        // FOR本体の未合流条件は、生成時点でTRUE枝またはFALSE枝が対応FOR_ENDを
        // 直接指します。古いUIやインベントリ経路が境界IDを渡し損ねても、ここで
        // 現在の所属FORを復元してから末尾探索を止めます。これを行わないと、
        // connectOpenTailがFOR_END.nextまで進み、MERGEをFORの外側へ配置します。
        // 推測対象は「条件自身を含むFORの対応FOR_ENDへの直接参照」に限定し、
        // 深い枝のNULL終端や親MERGEを暗黙に継続先へ変換しません。
        val resolvedContinuationId = continuationId ?: directEnclosingForEnd(graph, condition)
        require(canAppendMerge(graph, conditionId, resolvedContinuationId)) {
            "内側の条件分岐を先に合流してください"
        }
        resolvedContinuationId?.let { continuation ->
            require(continuation in graph.nodes) { "合流後の継続先が存在しません: $continuation" }
            require(continuation != conditionId) { "条件分岐自身へは継続できません" }
        }

        val merge = CommandType.MERGE.newNode()
        graph.nodes[merge.id] = merge
        condition.pairedNodeId = merge.id
        merge.pairedNodeId = condition.id
        connectOpenTail(graph, condition.trueNext, condition, true, merge.id, resolvedContinuationId)
        connectOpenTail(graph, condition.falseNext, condition, false, merge.id, resolvedContinuationId)
        merge.next = resolvedContinuationId
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

    /**
     * 既存エッジの直後へ通常ノードを挿入します。
     *
     * 条件枝の終端は後続へ再合流する場合と、その枝でプログラムを正常終了する場合を
     * 区別します。通常は既存エッジの終端をそのまま引き継ぐため、`null` 終端の枝へ
     * 追加しても、ループ外では早期終了の意味を変えません。
     *
     * GUIの挿入候補に含まれるcontinuationIdは、条件ノードを既存の主経路へ挿入する
     * 場合のTRUE枝の継続先としてだけ使用します。通常ノードのNULL終端へ同じ値を
     * 適用すると、未合流枝をMERGEやFOR_ENDへ暗黙接続してしまい、枝の終端意味を
     * 破壊するためです。
     */
    fun insert(
        graph: CommandGraph,
        sourceId: UUID?,
        edge: Edge,
        type: CommandType,
        continuationId: UUID? = null,
    ): CommandNode {
        require(type !in setOf(CommandType.MERGE, CommandType.FOR_END)) { "$type はこの経路へ挿入できません" }
        val edgeTarget = edgeTarget(graph, sourceId, edge)
        // NULL終端の枝へ通常ノードを追加した場合は、その終端をそのまま引き継ぎます。
        // continuationIdは、明示的なMERGEを外側の継続先とする条件追加にだけ使います。
        // FOR_ENDはループ境界であって合流ノードではないため、内側の枝へ持ち込むと
        // 複数の枝が同じFOR_ENDへ入り、枝の深さと終端意味を同時に壊してしまいます。
        val target = edgeTarget ?: continuationId
            ?.takeIf { type == CommandType.CONDITION && graph.nodes[it]?.type == CommandType.MERGE }
        val inserted = createBundle(graph, type)
        setEdge(graph, sourceId, edge, inserted.id)
        if (inserted.type == CommandType.CONDITION) {
            connectBundleTail(graph, inserted, target)
            inserted.next = null
            inserted.trueNext = target
            inserted.falseNext = null
        } else {
            connectBundleTail(graph, inserted, target)
        }
        return inserted
    }

    /**
     * 選択中ノードの直後へ、設定内容だけを引き継いだ新しいノードを挿入します。
     *
     * ノードIDと実行リンクは設定ではないため、IDは必ず新規発行し、分岐・ループの
     * リンクは複製しません。通常ノードのNEXTだけを「元ノード -> 複製 -> 元の後続」へ
     * 付け替えることで、主経路・条件枝・for本体のどの直列経路でも同じ意味で動作します。
     * snapshotはDISK_CALLの実行内容なので、参照共有による後編集の巻き込みを防ぐため
     * グラフ全体を独立コピーします。
     *
     * 構造ノードおよびノード上限到達時はnullを返し、表示側と同じ境界で拒否します。
     */
    fun duplicate(
        graph: CommandGraph,
        nodeId: UUID,
        maximumNodeCount: Int = Int.MAX_VALUE,
    ): CommandNode? {
        if (!canDuplicate(graph, nodeId, maximumNodeCount)) return null
        val source = graph.nodes.getValue(nodeId)
        val successor = source.next
        var cloneId = UUID.randomUUID()
        while (cloneId in graph.nodes) cloneId = UUID.randomUUID()

        val clone = source.deepCopy().copy(
            id = cloneId,
            next = successor,
            trueNext = null,
            falseNext = null,
            pairedNodeId = null,
        )
        graph.nodes[clone.id] = clone
        source.next = clone.id
        return clone
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
        // 対応MERGEがある条件だけを停止境界にします。未合流条件の枝には境界を
        // 補わず、NULL終端までを同じ枝として扱います。FOR_ENDを停止点にすると、
        // 条件より外側の深さへ枝を延長し、終端枝をループへ誤接続してしまいます。
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
        stop: UUID?,
    ) {
        if (start == null || start == stop) {
            if (trueBranch) condition.trueNext = target else condition.falseNext = target
            return
        }
        val tail = findOpenTail(graph, start, preferTrue = true, stop = stop)
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
        if (target == null) return
        when (inserted.type) {
            CommandType.FOR_START -> {
                // FOR_STARTの次経路は対応FOR_ENDから続くため、ペア末尾へ接続します。
                val end = inserted.pairedNodeId?.let(graph.nodes::get) ?: inserted
                end.next = target
            }
            CommandType.CONDITION -> {
                // 条件ノードには通常のNEXT実行経路がありません。ここでnextへ書くと
                // FOR_BODYの終了先が実行グラフから消え、FOR_ENDが到達不能になります。
                // 条件を既存経路へ置いた場合の主経路はTRUE枝として表現します。
                inserted.trueNext = target
                inserted.next = null
            }
            else -> inserted.next = target
        }
    }

    private fun connect(source: CommandNode, target: UUID, preferTrue: Boolean) {
        if (source.type == CommandType.CONDITION) {
            if (preferTrue) source.trueNext = target else source.falseNext = target
        } else {
            source.next = target
        }
    }

    private fun swapWithPrevious(graph: CommandGraph, node: CommandNode): Boolean {
        val incoming = incomingExecutionLink(graph, node.id) ?: return false
        // 最初の枝要素を条件分岐そのものと入れ替えることはできません。
        if (incoming.edge != Edge.NEXT) return false
        val previous = incoming.sourceId?.let(graph.nodes::get) ?: return false
        if (!isLinearReorderable(previous)) return false
        val after = node.next

        val previousIncoming = incomingExecutionLink(graph, previous.id)
        // previousがグラフ入口なら、入口IDだけを差し替えます。そうでなければ
        // previousへ入っていた同じ実行エッジをnodeへ向け直します。
        setExecutionLink(graph, previousIncoming, node.id)
        node.next = previous.id
        previous.next = after
        return true
    }

    private fun swapWithNext(graph: CommandGraph, node: CommandNode): Boolean {
        val nextId = node.next ?: return false
        val next = graph.nodes[nextId] ?: return false
        if (!isLinearReorderable(next)) return false
        val incoming = incomingExecutionLink(graph, node.id) ?: return false

        setExecutionLink(graph, incoming, next.id)
        node.next = next.next
        next.next = node.id
        return true
    }

    private fun isLinearReorderable(node: CommandNode): Boolean = node.type !in STRUCTURAL_TYPES

    private data class ExecutionLink(val sourceId: UUID?, val edge: Edge)

    /** ペア参照は実行順ではないため除外し、実行エッジだけを収集します。 */
    private fun incomingExecutionLink(graph: CommandGraph, target: UUID): ExecutionLink? {
        val links = buildList {
            if (graph.entryNodeId == target) add(ExecutionLink(null, Edge.ENTRY))
            graph.nodes.values.forEach { source ->
                if (source.next == target) add(ExecutionLink(source.id, Edge.NEXT))
                if (source.trueNext == target) add(ExecutionLink(source.id, Edge.TRUE))
                if (source.falseNext == target) add(ExecutionLink(source.id, Edge.FALSE))
            }
        }
        return links.singleOrNull()
    }

    private fun setExecutionLink(graph: CommandGraph, link: ExecutionLink?, target: UUID?) {
        val resolved = link ?: error("実行エッジが一意に解決できません")
        setEdge(graph, resolved.sourceId, resolved.edge, target)
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

    private fun containsUnmergedCondition(
        graph: CommandGraph,
        start: UUID?,
        rootConditionId: UUID,
        stop: UUID? = null,
    ): Boolean {
        val visited = mutableSetOf<UUID>()
        fun visit(id: UUID?): Boolean {
            if (id == null || id == stop || !visited.add(id)) return false
            val node = graph.nodes[id] ?: return false
            if (node.id != rootConditionId && node.type == CommandType.CONDITION && node.pairedNodeId == null) {
                return true
            }
            return node.outgoingIds().any(::visit)
        }
        return visit(start)
    }

    /**
     * FOR本体の未合流条件が直接参照しているFOR_ENDを、明示的な合流境界として返します。
     *
     * この補正は、境界メタデータをまだ保持していない旧呼び出し元への防御です。
     * 条件がFOR本体内に存在することも同時に確認するため、トップレベルの不正な
     * FOR_END参照や、別の深さにある境界を拾って枝へ接続することはありません。
     */
    private fun directEnclosingForEnd(graph: CommandGraph, condition: CommandNode): UUID? {
        val directBoundaryIds = listOfNotNull(condition.trueNext, condition.falseNext)
            .filter { graph.nodes[it]?.type == CommandType.FOR_END }
        return directBoundaryIds.firstOrNull { endId ->
            graph.nodes.values.any { start ->
                start.type == CommandType.FOR_START &&
                    start.pairedNodeId == endId &&
                    containsNodeBefore(graph, start.trueNext, endId, condition.id)
            }
        }
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
