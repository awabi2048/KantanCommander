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
     * 同一の直列経路にある実行単位の順序を入れ替えます。移動単位は通常ノードと
     * 折畳み単位（折りたたんだFORブロック・折りたたんだ合流済みIF）に限り、
     * 展開中の構造は移動単位にしません。枝の並べ替えは [swapBranches] を使います。
     */
    enum class ReorderDirection { LEFT, RIGHT }

    /**
     * 条件分岐のTRUE鎖とFALSE鎖を入れ替えます。
     *
     * ペアの合流・後続・反転フラグには触れず、両枝の接続先だけを交換するため、
     * 妥当なグラフは交換後も妥当です。分岐の実行意味そのものが変わる保存操作として
     * 扱い、表示だけの反転は行いません。条件分岐以外には何もしません。
     */
    fun swapBranches(graph: CommandGraph, nodeId: UUID): Boolean {
        val node = graph.nodes[nodeId] ?: return false
        if (node.type != CommandType.CONDITION) return false
        val trueNext = node.trueNext
        node.trueNext = node.falseNext
        node.falseNext = trueNext
        return true
    }

    /**
     * 同一直列経路にある隣接した実行単位を入れ替えます。
     *
     * 展開中のFOR境界を跨ぐ移動は、外側ノードの本体への格納（前任は先頭へ・
     * 後継は末尾へ）と、本体端ノードの外への取り出しとして扱います。隣接する
     * 合流済みIF全体（`COND〜MERGE`）は一体として本体の近い側へ格納します。
     * 同一ペアの `START⇄END` 直接入替と、展開中のFOR同士の組み合わせ移動は
     * 構造を壊すため拒否します。FOR同士の入替・入れ子化は折りたたみ状態で行います。
     *
     * 折りたたんだFORブロック・合流済みIF単位は原子ブロックとして前後の単位と
     * 順序を入れ替えます。通常実行ノードとbreak/continueは、分岐の枝であっても
     * 同じ枝内の隣接ノードとして入れ替えられます。展開中の構造（条件分岐／合流・
     * 展開FORの開始／終了）は移動単位にしないため、単体交換の対象外です。
     * 適用後に検証し、不正になる場合はグラフを変更せず拒否します。
     *
     * @param collapsedForStarts ブロック全体として扱うFOR開始ID集合（UIの折りたたみ状態）
     * @param collapsedConditionIds 原子ブロックとして扱う合流済み条件ID集合（UIの折りたたみ状態）
     */
    fun swapAdjacent(
        graph: CommandGraph,
        nodeId: UUID,
        direction: ReorderDirection,
        collapsedForStarts: Set<UUID> = emptySet(),
        collapsedConditionIds: Set<UUID> = emptySet(),
    ): Boolean {
        val node = graph.nodes[nodeId] ?: return false
        if (node.type == CommandType.MERGE) return false
        if (node.type == CommandType.CONDITION &&
            !isFoldedMergedIf(graph, node, collapsedConditionIds)
        ) return false

        val backup = graph.deepCopy()
        val applied = when (direction) {
            ReorderDirection.LEFT -> moveLeft(graph, node, collapsedForStarts, collapsedConditionIds)
            ReorderDirection.RIGHT -> moveRight(graph, node, collapsedForStarts, collapsedConditionIds)
        }
        if (!applied) return false
        // 線形交換は構造を保存しますが、ブロック移動はbreak/continueの所属などを
        // 変える場合があるため、検証に通らない交換は元に戻して拒否します。
        if (GraphValidator.validate(graph).isNotEmpty()) {
            graph.entryNodeId = backup.entryNodeId
            graph.nodes.clear()
            graph.nodes.putAll(backup.nodes)
            return false
        }
        return true
    }

    /** 表示前に左右ボタンを有効化できるかを、グラフを変更せず判定します。 */
    fun canSwapAdjacent(
        graph: CommandGraph,
        nodeId: UUID,
        direction: ReorderDirection,
        collapsedForStarts: Set<UUID> = emptySet(),
        collapsedConditionIds: Set<UUID> = emptySet(),
    ): Boolean {
        val node = graph.nodes[nodeId] ?: return false
        if (node.type == CommandType.MERGE) return false
        if (node.type == CommandType.CONDITION &&
            !isFoldedMergedIf(graph, node, collapsedConditionIds)
        ) return false
        return trialBlockMove(graph) { candidate ->
            val cNode = candidate.nodes[node.id] ?: return@trialBlockMove false
            when (direction) {
                ReorderDirection.LEFT -> moveLeft(candidate, cNode, collapsedForStarts, collapsedConditionIds)
                ReorderDirection.RIGHT -> moveRight(candidate, cNode, collapsedForStarts, collapsedConditionIds)
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
        // 条件分岐にNEXT実行経路は存在しないため、NEXT指定は無効です。書き込むと
        // 到達不能ノードを生成し保存時検証で失敗するため、ここで即時失敗させます。
        val source = sourceId?.let(graph.nodes::get)
        require(edge != Edge.NEXT || source == null || source.type != CommandType.CONDITION) {
            "条件分岐の直後にNEXT経路で挿入できません"
        }
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

    /**
     * 指定ノードを直列経路上で一つ前へ移動します。
     *
     * 移動主体と前任者の種類で操作を切り替えます。展開FORの開始・通常ノードが
     * FOR境界を跨ぐ場合は格納・取り出し・IF全体吸収を行い、原子ブロック
     * （折りたたみFOR・折りたたみ合流済みIF・通常ノード）同士は順序を入れ替えます。
     */
    private fun moveLeft(
        graph: CommandGraph,
        node: CommandNode,
        collapsedForStarts: Set<UUID>,
        collapsedConditionIds: Set<UUID>,
    ): Boolean {
        // 折りたたんだ合流済みIF・折りたたんだFOR開始は原子ブロックとして移動します。
        if (node.type == CommandType.CONDITION || isCollapsedForStart(graph, node, collapsedForStarts)) {
            return moveUnitLeft(graph, node, collapsedForStarts, collapsedConditionIds)
        }
        // 折りたたんだFOR終了の左移動は表示しないため拒否します（左移動は開始側が担います）。
        if (node.type == CommandType.FOR_END && isCollapsedForEnd(graph, node, collapsedForStarts)) return false
        if (node.type == CommandType.FOR_START) {
            // 展開FOR開始の左移動：前任が通常ノードなら本体先頭へ格納し、
            // 直前の合流済みIF全体なら一体として本体先頭へ格納します。
            val incoming = incomingExecutionLink(graph, node.id) ?: return false
            if (incoming.edge != Edge.NEXT) return false
            val previous = incoming.sourceId?.let(graph.nodes::get) ?: return false
            if (isLinearReorderable(previous)) return moveOuterIntoHead(graph, previous, node)
            val merge = previous.takeIf { it.type == CommandType.MERGE } ?: return false
            val condition = mergedConditionOf(graph, merge) ?: return false
            if (merge.next != node.id) return false
            return absorbIfIntoHead(graph, condition, merge, node)
        }
        if (node.type == CommandType.FOR_END) {
            // 展開FOR終了の左移動：本体末尾の取り出しです。本体が空の場合は
            // STARTとの直接入替になるため拒否します。
            val incoming = incomingExecutionLink(graph, node.id) ?: return false
            if (incoming.edge != Edge.NEXT) return false
            val tail = incoming.sourceId?.let(graph.nodes::get) ?: return false
            if (!isLinearReorderable(tail)) return false
            return takeBodyTailOut(graph, tail)
        }
        if (!isLinearReorderable(node)) return false
        val incoming = incomingExecutionLink(graph, node.id) ?: return false
        when (incoming.edge) {
            Edge.NEXT -> Unit
            // 本体先頭はSTARTからTRUE枝で入るため、左移動は外への取り出しです。
            Edge.TRUE -> return takeBodyHeadOut(graph, node, collapsedForStarts)
            else -> return false
        }
        val previous = incoming.sourceId?.let(graph.nodes::get) ?: return false
        if (isLinearReorderable(previous)) {
            val after = node.next
            val previousIncoming = incomingExecutionLink(graph, previous.id)
            // previousがグラフ入口なら、入口IDだけを差し替えます。そうでなければ
            // previousへ入っていた同じ実行エッジをnodeへ向け直します。
            setExecutionLink(graph, previousIncoming, node.id)
            node.next = previous.id
            previous.next = after
            return true
        }
        // 前任が原子ブロックの末尾なら、その前へ回ります。展開FORの終了なら
        // 本体末尾への格納、折りたたみ単位なら順序入替です。
        if (previous.type == CommandType.FOR_END) {
            val start = forStartOf(graph, previous) ?: return false
            return if (start.id in collapsedForStarts) {
                jumpBeforeUnit(graph, node, MoveUnit(start.id, previous.id))
            } else {
                moveOuterIntoTail(graph, node, previous)
            }
        }
        if (previous.type == CommandType.MERGE) {
            val condition = mergedConditionOf(graph, previous) ?: return false
            if (!isFoldedMergedIf(graph, condition, collapsedConditionIds)) return false
            return jumpBeforeUnit(graph, node, MoveUnit(condition.id, previous.id))
        }
        return false
    }

    /**
     * 指定ノードを直列経路上で一つ後ろへ移動します。
     *
     * 左移動と対称に、展開FOR境界の跨ぎは格納・取り出し・IF全体吸収、
     * 原子ブロック同士は順序入替です。展開FOR開始の右移動は本体先頭の
     * 取り出しであり、本体が空の場合は終了との直接入替になるため拒否します。
     */
    private fun moveRight(
        graph: CommandGraph,
        node: CommandNode,
        collapsedForStarts: Set<UUID>,
        collapsedConditionIds: Set<UUID>,
    ): Boolean {
        if (node.type == CommandType.CONDITION || isCollapsedForStart(graph, node, collapsedForStarts)) {
            return moveUnitRight(graph, node, collapsedForStarts, collapsedConditionIds)
        }
        // 折りたたんだFOR終了の右移動は、開始側と同じブロック移動です。
        if (node.type == CommandType.FOR_END && isCollapsedForEnd(graph, node, collapsedForStarts)) {
            return moveUnitRight(graph, node, collapsedForStarts, collapsedConditionIds)
        }
        if (node.type == CommandType.FOR_START) {
            val end = forEndOf(graph, node) ?: return false
            val head = node.trueNext?.let(graph.nodes::get) ?: return false
            // 本体が空の場合は終了との直接入替になるため拒否します。
            if (head.id == end.id) return false
            if (!isLinearReorderable(head)) return false
            return takeBodyHeadOut(graph, head, collapsedForStarts)
        }
        if (node.type == CommandType.FOR_END) {
            // 展開FOR終了の右移動：後継が通常ノードなら本体末尾へ格納し、
            // 直後の合流済みIF全体なら一体として本体末尾へ格納します。
            val successor = node.next?.let(graph.nodes::get) ?: return false
            if (isLinearReorderable(successor)) return moveOuterIntoTail(graph, successor, node)
            if (successor.type != CommandType.CONDITION || node.next != successor.id) return false
            val merge = mergedPairOf(graph, successor) ?: return false
            return absorbIfIntoTail(graph, successor, merge, node)
        }
        if (!isLinearReorderable(node)) return false
        val nextId = node.next ?: return false
        val next = graph.nodes[nextId] ?: return false
        if (isLinearReorderable(next)) {
            val incoming = incomingExecutionLink(graph, node.id) ?: return false
            setExecutionLink(graph, incoming, next.id)
            node.next = next.next
            next.next = node.id
            return true
        }
        // 後継が原子ブロックの先頭なら、その後ろへ回ります。展開FORの開始なら
        // 本体先頭への格納、折りたたみ単位なら順序入替です。
        if (next.type == CommandType.FOR_START) {
            val end = forEndOf(graph, next) ?: return false
            return if (next.id in collapsedForStarts) {
                jumpAfterUnit(graph, node, MoveUnit(next.id, end.id))
            } else {
                moveOuterIntoHead(graph, node, next)
            }
        }
        // 後継が本体の終了なら、自身が本体末尾のため外への取り出しです。
        if (next.type == CommandType.FOR_END) {
            val start = forStartOf(graph, next) ?: return false
            if (start.id in collapsedForStarts) return false
            return takeBodyTailOut(graph, node)
        }
        if (next.type == CommandType.MERGE) {
            val condition = mergedConditionOf(graph, next) ?: return false
            if (!isFoldedMergedIf(graph, condition, collapsedConditionIds)) return false
            return jumpAfterUnit(graph, node, MoveUnit(condition.id, next.id))
        }
        return false
    }

    /**
     * 順序入替の対象となる原子単位です。
     *
     * 先頭ノードへ入る実行エッジと、末尾ノードのNEXTだけを付け替えることで
     * 単位全体を移動します。通常ノードは先頭＝末尾、折りたたみFORは開始〜終了、
     * 折りたたみ合流済みIFは条件〜合流です。未合流の折りたたみIFは出口が
     * 定まらないため単位にしません。
     */
    private data class MoveUnit(val headId: UUID, val tailId: UUID)

    /**
     * 原子ブロック（折りたたみFOR開始・折りたたみ合流済み条件）を一つ前へ移動します。
     *
     * 前任が通常ノードまたは別の原子単位（折りたたみ・展開を問わないFORブロック、
     * 折りたたみ合流済みIF）なら順序を入れ替えます。未合流の条件・合流・展開FORの
     * 境界ノードが前任の場合は拒否します。
     */
    private fun moveUnitLeft(
        graph: CommandGraph,
        node: CommandNode,
        collapsedForStarts: Set<UUID>,
        collapsedConditionIds: Set<UUID>,
    ): Boolean {
        val unit = leadingUnitOf(graph, node, collapsedForStarts, collapsedConditionIds) ?: return false
        val incoming = incomingExecutionLink(graph, unit.headId) ?: return false
        if (incoming.edge != Edge.NEXT && incoming.edge != Edge.ENTRY) return false
        val previousId = incoming.sourceId ?: return false
        val previous = graph.nodes[previousId] ?: return false
        val previousUnit = trailingUnitEndingAt(graph, previous, collapsedForStarts, collapsedConditionIds)
            ?: return false
        // 単位の入口へ入っていた実行エッジを後続単位へ向け直します。移動単位自身への
        // 入力ではなく、前任単位への入力を使う点に注意します（同一にすると循環します）。
        val previousIncoming = incomingExecutionLink(graph, previousUnit.headId) ?: return false
        return swapUnits(graph, previousIncoming, previousUnit, unit)
    }

    /**
     * 原子ブロックを一つ後ろへ移動します。
     *
     * 後継が通常ノードまたは別の原子単位なら順序を入れ替えます。後継が未合流の
     * 条件・合流・展開FORの境界ノードの場合は拒否します。
     */
    private fun moveUnitRight(
        graph: CommandGraph,
        node: CommandNode,
        collapsedForStarts: Set<UUID>,
        collapsedConditionIds: Set<UUID>,
    ): Boolean {
        val unit = leadingUnitOf(graph, node, collapsedForStarts, collapsedConditionIds) ?: return false
        val exitTarget = graph.nodes[unit.tailId]?.next ?: return false
        val next = graph.nodes[exitTarget] ?: return false
        val nextUnit = leadingUnitStartingAt(graph, next, collapsedForStarts, collapsedConditionIds)
            ?: return false
        return swapUnits(graph, incomingExecutionLink(graph, unit.headId), unit, nextUnit)
    }

    /**
     * 移動主体ノードが属する原子単位を返します。
     *
     * 折りたたみFORの開始・終了、折りたたみ合流済みIFの条件・合流は単位全体を
     * 返します。通常ノードは単独単位です。それ以外は移動対象外のためnullです。
     */
    private fun leadingUnitOf(
        graph: CommandGraph,
        node: CommandNode,
        collapsedForStarts: Set<UUID>,
        collapsedConditionIds: Set<UUID>,
    ): MoveUnit? {
        if (isLinearReorderable(node)) return MoveUnit(node.id, node.id)
        if (node.type == CommandType.FOR_START) {
            val end = forEndOf(graph, node) ?: return null
            if (node.id !in collapsedForStarts) return null
            return MoveUnit(node.id, end.id)
        }
        if (node.type == CommandType.FOR_END) {
            val start = forStartOf(graph, node) ?: return null
            if (start.id !in collapsedForStarts) return null
            return MoveUnit(start.id, node.id)
        }
        if (node.type == CommandType.CONDITION) {
            if (!isFoldedMergedIf(graph, node, collapsedConditionIds)) return null
            val merge = mergedPairOf(graph, node) ?: return null
            return MoveUnit(node.id, merge.id)
        }
        if (node.type == CommandType.MERGE) {
            val condition = mergedConditionOf(graph, node) ?: return null
            if (!isFoldedMergedIf(graph, condition, collapsedConditionIds)) return null
            return MoveUnit(condition.id, node.id)
        }
        return null
    }

    /**
     * 指定ノードを末尾とする原子単位を返します。
     *
     * 前任者側の解決に使い、通常ノード・FOR終了（折りたたみ状態を問わない）・
     * 折りたたみ合流済みIFの合流を受け付けます。未合流の折りたたみIFは出口が
     * 定まらないため単位にしません。
     */
    private fun trailingUnitEndingAt(
        graph: CommandGraph,
        node: CommandNode,
        collapsedForStarts: Set<UUID>,
        collapsedConditionIds: Set<UUID>,
    ): MoveUnit? {
        if (isLinearReorderable(node)) return MoveUnit(node.id, node.id)
        if (node.type == CommandType.FOR_END) {
            val start = forStartOf(graph, node) ?: return null
            return MoveUnit(start.id, node.id)
        }
        if (node.type == CommandType.MERGE) {
            val condition = mergedConditionOf(graph, node) ?: return null
            if (!isFoldedMergedIf(graph, condition, collapsedConditionIds)) return null
            return MoveUnit(condition.id, node.id)
        }
        return null
    }

    /**
     * 指定ノードを先頭とする原子単位を返します。
     *
     * 後継者側の解決に使い、通常ノード・FOR開始（折りたたみ状態を問わない）・
     * 折りたたみ合流済みIFの条件を受け付けます。
     */
    private fun leadingUnitStartingAt(
        graph: CommandGraph,
        node: CommandNode,
        collapsedForStarts: Set<UUID>,
        collapsedConditionIds: Set<UUID>,
    ): MoveUnit? {
        if (isLinearReorderable(node)) return MoveUnit(node.id, node.id)
        if (node.type == CommandType.FOR_START) {
            val end = forEndOf(graph, node) ?: return null
            return MoveUnit(node.id, end.id)
        }
        if (node.type == CommandType.CONDITION) {
            if (!isFoldedMergedIf(graph, node, collapsedConditionIds)) return null
            val merge = mergedPairOf(graph, node) ?: return null
            return MoveUnit(node.id, merge.id)
        }
        return null
    }

    /**
     * 隣接した2単位 `[X, first, second, Z]` を `[X, second, first, Z]` へ組み替えます。
     *
     * 単位内部の接続には触れず、先頭への入力・末尾のNEXTだけを付け替えるため、
     * 単位の種類（通常・折りたたみFOR・折りたたみIF・展開FOR障害物）を問いません。
     */
    private fun swapUnits(
        graph: CommandGraph,
        firstIncoming: ExecutionLink?,
        first: MoveUnit,
        second: MoveUnit,
    ): Boolean {
        if (firstIncoming == null) return false
        val firstTail = graph.nodes[first.tailId] ?: return false
        val secondTail = graph.nodes[second.tailId] ?: return false
        if (firstTail.next != second.headId) return false
        val after = secondTail.next
        setExecutionLink(graph, firstIncoming, second.headId)
        secondTail.next = first.headId
        firstTail.next = after
        return true
    }

    /**
     * `[X, node, unit, Z]` を `[X, unit, node, Z]` へ組み替えます。
     * 通常ノードが原子ブロック全体の前へ回る左移動です。
     */
    private fun jumpBeforeUnit(graph: CommandGraph, node: CommandNode, unit: MoveUnit): Boolean {
        val unitHead = graph.nodes[unit.headId] ?: return false
        val unitTail = graph.nodes[unit.tailId] ?: return false
        if (unitTail.next != node.id) return false
        val unitIncoming = incomingExecutionLink(graph, unit.headId) ?: return false
        if (unitIncoming.edge != Edge.NEXT && unitIncoming.edge != Edge.ENTRY) return false
        val after = node.next
        setExecutionLink(graph, unitIncoming, node.id)
        node.next = unit.headId
        unitTail.next = after
        return unitHead.id != node.id
    }

    /**
     * `[X, node, unit, Z]` を `[X, unit, node, Z]` へ組み替えます。
     * 通常ノードが原子ブロック全体の後ろへ回る右移動です。
     */
    private fun jumpAfterUnit(graph: CommandGraph, node: CommandNode, unit: MoveUnit): Boolean {
        val unitTail = graph.nodes[unit.tailId] ?: return false
        if (node.next != unit.headId) return false
        val nodeIncoming = incomingExecutionLink(graph, node.id) ?: return false
        if (nodeIncoming.edge != Edge.NEXT && nodeIncoming.edge != Edge.ENTRY) return false
        val after = unitTail.next
        setExecutionLink(graph, nodeIncoming, unit.headId)
        unitTail.next = node.id
        node.next = after
        return true
    }

    /**
     * `[X, outer, START..END, S]` を `[X, START, outer, ..END, S]` へ組み替えます。
     *
     * FOR外の通常ノードを本体先頭へ格納します。本体が空の場合も同じ配線で
     * `[X, START, outer, END, S]` になります。
     */
    private fun moveOuterIntoHead(graph: CommandGraph, outer: CommandNode, start: CommandNode): Boolean {
        val end = forEndOf(graph, start) ?: return false
        if (outer.next != start.id) return false
        val head = start.trueNext?.let(graph.nodes::get) ?: return false
        if (head.id != end.id && head.id !in graph.nodes) return false
        val outerIncoming = incomingExecutionLink(graph, outer.id) ?: return false
        if (outerIncoming.edge != Edge.NEXT && outerIncoming.edge != Edge.ENTRY) return false
        setExecutionLink(graph, outerIncoming, start.id)
        outer.next = head.id
        start.trueNext = outer.id
        return true
    }

    /**
     * `[X, START..tail, END, outer, S]` を `[X, START..tail, outer, END, S]` へ組み替えます。
     *
     * FOR外の通常ノードを本体末尾へ格納します。本体が空の場合は開始のTRUE枝を
     * 付け替えて `[X, START, outer, END, S]` になります。
     */
    private fun moveOuterIntoTail(graph: CommandGraph, outer: CommandNode, end: CommandNode): Boolean {
        val start = forStartOf(graph, end) ?: return false
        if (outer.id != end.next) return false
        val after = outer.next
        val tailIncoming = incomingExecutionLink(graph, end.id) ?: return false
        if (tailIncoming.edge == Edge.NEXT) {
            val tail = tailIncoming.sourceId?.let(graph.nodes::get) ?: return false
            tail.next = outer.id
        } else if (tailIncoming.edge == Edge.TRUE && tailIncoming.sourceId == start.id) {
            start.trueNext = outer.id
        } else {
            return false
        }
        outer.next = end.id
        end.next = after
        return true
    }

    /**
     * `[X, START, head, ..END, S]` を `[X, head, START, ..END, S]` へ組み替えます。
     *
     * 本体先頭を外へ取り出します。開始自体の右移動と本体先頭の左移動は
     * 同じ組み替えです。折りたたみ中の開始には適用しません。
     */
    private fun takeBodyHeadOut(
        graph: CommandGraph,
        head: CommandNode,
        collapsedForStarts: Set<UUID>,
    ): Boolean {
        val headIncoming = incomingExecutionLink(graph, head.id) ?: return false
        if (headIncoming.edge != Edge.TRUE) return false
        val start = headIncoming.sourceId?.let(graph.nodes::get) ?: return false
        if (start.type != CommandType.FOR_START || start.id in collapsedForStarts) return false
        if (forEndOf(graph, start) == null) return false
        val rest = head.next
        val startIncoming = incomingExecutionLink(graph, start.id) ?: return false
        if (startIncoming.edge != Edge.NEXT && startIncoming.edge != Edge.ENTRY) return false
        setExecutionLink(graph, startIncoming, head.id)
        head.next = start.id
        start.trueNext = rest
        return true
    }

    /**
     * `[X, START..prev, tail, END, S]` を `[X, START..prev, END, tail, S]` へ組み替えます。
     *
     * 本体末尾を外へ取り出します。本体末尾の右移動と終了の左移動は同じ組み替えです。
     * 本体長1の場合は開始のTRUE枝を終了へ戻します。
     */
    private fun takeBodyTailOut(graph: CommandGraph, tail: CommandNode): Boolean {
        if (!isLinearReorderable(tail)) return false
        val end = tail.next?.let(graph.nodes::get) ?: return false
        if (end.type != CommandType.FOR_END) return false
        val start = forStartOf(graph, end) ?: return false
        val after = end.next
        val tailIncoming = incomingExecutionLink(graph, tail.id) ?: return false
        when {
            tailIncoming.edge == Edge.NEXT -> {
                val previous = tailIncoming.sourceId?.let(graph.nodes::get) ?: return false
                previous.next = end.id
            }
            tailIncoming.edge == Edge.TRUE && tailIncoming.sourceId == start.id ->
                start.trueNext = end.id
            else -> return false
        }
        end.next = tail.id
        tail.next = after
        return true
    }

    /**
     * `[X, COND..MERGE, START..END, S]` を `[X, START, COND..MERGE, ..END, S]` へ組み替えます。
     *
     * 開始直前の合流済みIF全体を一体として本体先頭へ格納します。分岐・合流の
     * 対応関係には触れず、IF単位の入口と出口だけを付け替えます。
     */
    private fun absorbIfIntoHead(
        graph: CommandGraph,
        condition: CommandNode,
        merge: CommandNode,
        start: CommandNode,
    ): Boolean {
        if (merge.next != start.id) return false
        val head = start.trueNext ?: return false
        val conditionIncoming = incomingExecutionLink(graph, condition.id) ?: return false
        if (conditionIncoming.edge != Edge.NEXT && conditionIncoming.edge != Edge.ENTRY) return false
        setExecutionLink(graph, conditionIncoming, start.id)
        start.trueNext = condition.id
        merge.next = head
        return true
    }

    /**
     * `[X, START..tail, END, COND..MERGE, S]` を `[X, START..tail, COND..MERGE, END, S]` へ組み替えます。
     *
     * 終了直後の合流済みIF全体を一体として本体末尾へ格納します。本体が空の
     * 場合も開始のTRUE枝を付け替えて同じ形状になります。
     */
    private fun absorbIfIntoTail(
        graph: CommandGraph,
        condition: CommandNode,
        merge: CommandNode,
        end: CommandNode,
    ): Boolean {
        val start = forStartOf(graph, end) ?: return false
        if (end.next != condition.id) return false
        val after = merge.next
        val tailIncoming = incomingExecutionLink(graph, end.id) ?: return false
        if (tailIncoming.edge == Edge.NEXT) {
            val tail = tailIncoming.sourceId?.let(graph.nodes::get) ?: return false
            tail.next = condition.id
        } else if (tailIncoming.edge == Edge.TRUE && tailIncoming.sourceId == start.id) {
            start.trueNext = condition.id
        } else {
            return false
        }
        merge.next = end.id
        end.next = after
        return true
    }

    private fun pairedForEnd(graph: CommandGraph, start: CommandNode): CommandNode? =
        start.takeIf { it.type == CommandType.FOR_START }
            ?.pairedNodeId?.let(graph.nodes::get)
            ?.takeIf { it.type == CommandType.FOR_END && it.pairedNodeId == start.id }

    private fun pairedForStart(graph: CommandGraph, end: CommandNode): CommandNode? =
        end.takeIf { it.type == CommandType.FOR_END }
            ?.pairedNodeId?.let(graph.nodes::get)
            ?.takeIf { it.type == CommandType.FOR_START && it.pairedNodeId == end.id }

    /** 移動候補を複製上で試し、検証に通る場合だけ有効とします。 */
    private fun trialBlockMove(graph: CommandGraph, move: (CommandGraph) -> Boolean): Boolean {
        val candidate = graph.deepCopy()
        if (!move(candidate)) return false
        return GraphValidator.validate(candidate).isEmpty()
    }

    /** 対応関係が正しいFOR終了を返します。ペアの壊れた残骸は移動対象外です。 */
    private fun forEndOf(graph: CommandGraph, start: CommandNode): CommandNode? =
        pairedForEnd(graph, start)

    /** 対応関係が正しいFOR開始を返します。ペアの壊れた残骸は移動対象外です。 */
    private fun forStartOf(graph: CommandGraph, end: CommandNode): CommandNode? =
        pairedForStart(graph, end)

    /** 折りたたみ対象として有効なFOR開始かを判定します。 */
    private fun isCollapsedForStart(
        graph: CommandGraph,
        node: CommandNode,
        collapsedForStarts: Set<UUID>,
    ): Boolean = node.type == CommandType.FOR_START &&
        node.id in collapsedForStarts &&
        forEndOf(graph, node) != null

    /** 折りたたみ対象のFORに対応する終了かを判定します。 */
    private fun isCollapsedForEnd(
        graph: CommandGraph,
        node: CommandNode,
        collapsedForStarts: Set<UUID>,
    ): Boolean {
        val start = forStartOf(graph, node) ?: return false
        return start.id in collapsedForStarts
    }

    /** 条件に対応する合流が正しく結ばれている場合に返します。 */
    private fun mergedPairOf(graph: CommandGraph, condition: CommandNode): CommandNode? =
        condition.takeIf { it.type == CommandType.CONDITION }
            ?.pairedNodeId?.let(graph.nodes::get)
            ?.takeIf { it.type == CommandType.MERGE && it.pairedNodeId == condition.id }

    /** 合流に対応する条件が正しく結ばれている場合に返します。 */
    private fun mergedConditionOf(graph: CommandGraph, merge: CommandNode): CommandNode? =
        merge.takeIf { it.type == CommandType.MERGE }
            ?.pairedNodeId?.let(graph.nodes::get)
            ?.takeIf { it.type == CommandType.CONDITION && it.pairedNodeId == merge.id }

    /**
     * 折りたたみ表示する合流済み条件かを判定します。
     *
     * 未合流の条件は出口が定まらないため、折りたたみ集合に含まれていても
     * 移動単位にはしません（表示の折りたたみ自体は行います）。
     */
    private fun isFoldedMergedIf(
        graph: CommandGraph,
        node: CommandNode,
        collapsedConditionIds: Set<UUID>,
    ): Boolean = node.type == CommandType.CONDITION &&
        node.id in collapsedConditionIds &&
        mergedPairOf(graph, node) != null

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
