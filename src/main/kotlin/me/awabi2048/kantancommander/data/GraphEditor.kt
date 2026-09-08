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
     * 単独ノードとして複製できない展開中の構造ノードです。
     *
     * CONDITION／FOR_STARTだけを除外すると、それらの対応ノードであるMERGE／FOR_END
     * が通常ノードとして複製され、分岐・ループの対応関係を壊します。構造の入口と
     * 境界を同じ集合で扱い、画面表示と保存処理の判定を一つに揃えます。
     * 折り畳みノード（FOLDED_FOR／FOLDED_IF）は通常形状のため含みません。
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
     * 同一の直列経路にある隣接2ノードの順序を入れ替えます。移動できるのは通常
     * ノードと折り畳みノード（FOLDED_FOR／FOLDED_IF）だけで、展開中の構造ノード
     * （条件分岐／合流・FOR開始／終了）は移動単位にしないためボタンを表示しません。
     * FOR境界を跨ぐ格納・取り出し・IF吸収は行いません。構造を変える場合は
     * 折り畳み／展開・削除・挿入で行います。枝の並べ替えは [swapBranches] を使います。
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
     * 同一直列経路にある隣接した2ノードを入れ替えます。
     *
     * 通常ノードと折り畳みノード（FOLDED_FOR／FOLDED_IF）だけが対象で、どちらも
     * 単一nextの通常形状のため、前任・後継との3者付け替えで順序を変えます。
     * 展開中の構造ノード（条件分岐／合流・FOR開始／終了）は対象外です。
     * FOR境界を跨ぐ移動（本体への格納・本体からの取り出し）は行いません。
     * 適用後に検証し、不正になる場合はグラフを変更せず拒否します。
     */
    fun swapAdjacent(
        graph: CommandGraph,
        nodeId: UUID,
        direction: ReorderDirection,
    ): Boolean {
        val node = graph.nodes[nodeId] ?: return false
        if (!isLinearReorderable(node)) return false

        val backup = graph.deepCopy()
        val applied = when (direction) {
            ReorderDirection.LEFT -> swapWithPrevious(graph, node)
            ReorderDirection.RIGHT -> swapWithNext(graph, node)
        }
        if (!applied) return false
        // 隣接交換は構造を保存しますが、break/continueの所属などを変える場合に
        // 備え、検証に通らない交換は元に戻して拒否します。
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
    ): Boolean {
        val node = graph.nodes[nodeId] ?: return false
        if (!isLinearReorderable(node)) return false
        return trialBlockMove(graph) { candidate ->
            val cNode = candidate.nodes[node.id] ?: return@trialBlockMove false
            when (direction) {
                ReorderDirection.LEFT -> swapWithPrevious(candidate, cNode)
                ReorderDirection.RIGHT -> swapWithNext(candidate, cNode)
            }
        }
    }

    /**
     * 指定ノードを直前の通常ノードと入れ替えます。
     *
     * 前任は単一の実行入力で結ばれた通常ノードに限ります。前任が展開中の構造
     * ノードの場合は拒否し、FOR境界の外への取り出しにはなりません。
     */
    private fun swapWithPrevious(graph: CommandGraph, node: CommandNode): Boolean {
        val incoming = incomingExecutionLink(graph, node.id) ?: return false
        val previousId = incoming.sourceId ?: return false
        val previous = graph.nodes[previousId] ?: return false
        if (!isLinearReorderable(previous)) return false
        if (previous.next != node.id) return false
        val previousIncoming = incomingExecutionLink(graph, previous.id) ?: return false
        val after = node.next
        // previousへ入っていた実行エッジをnodeへ向け直します。移動単位自身への
        // 入力ではなく、前任への入力を使う点に注意します（同一にすると循環します）。
        setExecutionLink(graph, previousIncoming, node.id)
        node.next = previous.id
        previous.next = after
        return true
    }

    /**
     * 指定ノードを直後の通常ノードと入れ替えます。
     *
     * 後継は単一nextで結ばれた通常ノードに限ります。後継が展開中の構造ノード
     * の場合は拒否し、FOR境界の中への格納にはなりません。
     */
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
        require(type != CommandType.FOLDED_FOR && type != CommandType.FOLDED_IF) {
            "$type は折り畳み操作だけで生成します"
        }
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
     * グラフ全体を独立コピーします。折り畳みノードの内部グラフも同じく独立コピーします。
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
        // 折り畳みノードの複製は内部IDを付け替えます。内部先頭は外側IDと同一に
        // 保つ不変条件があるため、そのまま複写すると先頭IDが食い違って検証に
        // 通りません。入れ子の折り畳みも含めて再帰的に付け替えます。
        clone.foldedGraph?.let { inner ->
            remapFoldedTree(clone.id, inner, graph.nodes.keys.toMutableSet())
        }
        graph.nodes[clone.id] = clone
        source.next = clone.id
        return clone
    }

    /**
     * 複製した折り畳み内部グラフのIDを付け替えます。
     *
     * 内部先頭は外側ノードと同一IDに保つため [wrapperId] へ、その他の内部ノードは
     * 外側と衝突しない新規IDへ付け替えます。実行・ペア参照も同じ対応で書き換え、
     * 単位外への参照（正規化済みのNULL終端など）はそのまま残します。
     * 入れ子の折り畳みは内部先頭の新IDを引き継いで再帰的に付け替えます。
     */
    private fun remapFoldedTree(wrapperId: UUID, inner: CommandGraph, taken: MutableSet<UUID>) {
        fun freshId(): UUID {
            var candidate = UUID.randomUUID()
            while (!taken.add(candidate)) candidate = UUID.randomUUID()
            return candidate
        }
        taken += wrapperId
        val mapping = mutableMapOf<UUID, UUID>()
        inner.nodes.keys.forEach { oldId ->
            mapping[oldId] = if (oldId == inner.entryNodeId) wrapperId else freshId()
        }
        val renamed = LinkedHashMap<UUID, CommandNode>()
        inner.nodes.forEach { (oldId, node) ->
            val newId = requireNotNull(mapping[oldId])
            val renamedNode = node.deepCopy().copy(id = newId)
            renamedNode.next = renamedNode.next?.let { mapping[it] ?: it }
            renamedNode.trueNext = renamedNode.trueNext?.let { mapping[it] ?: it }
            renamedNode.falseNext = renamedNode.falseNext?.let { mapping[it] ?: it }
            renamedNode.pairedNodeId = renamedNode.pairedNodeId?.let { mapping[it] ?: it }
            renamedNode.foldedGraph?.let { nested ->
                remapFoldedTree(newId, nested, taken)
            }
            renamed[newId] = renamedNode
        }
        inner.nodes.clear()
        inner.nodes.putAll(renamed)
        inner.entryNodeId = mapping[inner.entryNodeId] ?: inner.entryNodeId
    }

    fun delete(graph: CommandGraph, nodeId: UUID): Boolean {
        val node = graph.nodes[nodeId] ?: return false
        return when (node.type) {
            CommandType.CONDITION -> deleteCondition(graph, node)
            CommandType.MERGE -> deleteMerge(graph, node)
            CommandType.FOR_START, CommandType.FOR_END -> deleteFor(graph, node)
            // 折り畳みノードの削除は中身ごと削除します。内部グラフは外側と一体の
            // 内容であり、単独で残すことはありません。
            CommandType.FOLDED_FOR, CommandType.FOLDED_IF -> deleteSimple(graph, node)
            else -> deleteSimple(graph, node)
        }
    }

    private fun createBundle(graph: CommandGraph, type: CommandType): CommandNode {
        require(type !in setOf(CommandType.MERGE, CommandType.FOR_END)) {
            "$type は単独追加できません"
        }
        require(type != CommandType.FOLDED_FOR && type != CommandType.FOLDED_IF) {
            "$type は折り畳み操作だけで生成します"
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
     * FOR開始を折り畳めるかを判定します。
     *
     * 対応するFOR_ENDと正しく結ばれている場合だけ折り畳めます。既に折り畳み
     * ノードである場合やペアの壊れた残骸は対象外です。
     */
    fun canFoldForStart(graph: CommandGraph, nodeId: UUID): Boolean {
        val start = graph.nodes[nodeId]?.takeIf { it.type == CommandType.FOR_START } ?: return false
        return forEndOf(graph, start) != null
    }

    /**
     * FOR開始〜終了の全体を折り畳みノード（FOLDED_FOR）へ再構成します。
     *
     * 外側には開始と同じIDの折り畳みノード1個が通常形状（単一next）で残り、
     * 中身全体は内部グラフへ退避します。IDを引き継ぐため外側の入力エッジや
     * 入口の付け替えは不要です。適用後に検証し、不正になる場合は変更せず拒否します。
     */
    fun foldForStart(graph: CommandGraph, nodeId: UUID): Boolean {
        val start = graph.nodes[nodeId]?.takeIf { it.type == CommandType.FOR_START } ?: return false
        val end = forEndOf(graph, start) ?: return false
        // 本体は開始のTRUE枝から終了までです。入れ子の構造も含めて収集します。
        val unitIds = collectUnitIds(graph, listOfNotNull(start.trueNext), end.id) ?: return false
        if (!isDetachableUnit(graph, unitIds, headId = start.id, exitId = end.id)) return false
        val backup = graph.deepCopy()
        val continuation = end.next
        // 内部には先頭（開始ノード自体）も元のIDのまま含め、外側スロットだけを
        // 折り畳みノードで置換します。入口・入力エッジの付け替えは不要です。
        val fullUnit = unitIds + start.id
        val innerNodes = LinkedHashMap<UUID, CommandNode>()
        fullUnit.forEach { id ->
            innerNodes[id] = graph.nodes[id]?.deepCopy() ?: return false
        }
        val inner = CommandGraph(entryNodeId = start.id, nodes = innerNodes)
        // 内部出口の後続は外側継続先（単位外ID）を指すため、内部単独では
        // 終端として扱います。外側の後続は折り畳みノードのnextが正であり、
        // 展開時に現在の外側接続で上書きします。
        inner.nodes[end.id]?.next = null
        fullUnit.filterNot { it == start.id }.forEach(graph.nodes::remove)
        graph.nodes[start.id] = start.deepCopy().copy(
            type = CommandType.FOLDED_FOR,
            next = continuation,
            trueNext = null,
            falseNext = null,
            pairedNodeId = null,
            foldedGraph = inner,
        )
        if (GraphValidator.validate(graph).isNotEmpty()) {
            graph.entryNodeId = backup.entryNodeId
            graph.nodes.clear()
            graph.nodes.putAll(backup.nodes)
            return false
        }
        return true
    }

    /**
     * 条件分岐を折り畳めるかを判定します。
     *
     * 対応するMERGEと正しく結ばれた閉じた分岐だけが対象です。未合流の分岐は
     * 出口が定まらないため折り畳めません。
     */
    fun canFoldCondition(graph: CommandGraph, nodeId: UUID): Boolean {
        val condition = graph.nodes[nodeId]?.takeIf { it.type == CommandType.CONDITION } ?: return false
        return mergedPairOf(graph, condition) != null
    }

    /**
     * 条件分岐〜合流の全体を折り畳みノード（FOLDED_IF）へ再構成します。
     *
     * 外側には条件と同じIDの折り畳みノード1個が通常形状（単一next）で残り、
     * 両枝・合流は内部グラフへ退避します。両枝の NULL 終端（早期終了）は
     * そのまま内部に保持します。適用後に検証し、不正になる場合は変更せず拒否します。
     */
    fun foldCondition(graph: CommandGraph, nodeId: UUID): Boolean {
        val condition = graph.nodes[nodeId]?.takeIf { it.type == CommandType.CONDITION } ?: return false
        val merge = mergedPairOf(graph, condition) ?: return false
        // 両枝を合流まで辿り、入れ子の構造も含めて収集します。
        val unitIds = collectUnitIds(graph, listOfNotNull(condition.trueNext, condition.falseNext), merge.id)
            ?: return false
        if (!isDetachableUnit(graph, unitIds, headId = condition.id, exitId = merge.id)) return false
        val backup = graph.deepCopy()
        val continuation = merge.next
        // 内部には先頭（条件ノード自体）も元のIDのまま含め、外側スロットだけを
        // 折り畳みノードで置換します。入口・入力エッジの付け替えは不要です。
        val fullUnit = unitIds + condition.id
        val innerNodes = LinkedHashMap<UUID, CommandNode>()
        fullUnit.forEach { id ->
            innerNodes[id] = graph.nodes[id]?.deepCopy() ?: return false
        }
        val inner = CommandGraph(entryNodeId = condition.id, nodes = innerNodes)
        // 内部出口の後続は外側継続先（単位外ID）を指すため、内部単独では
        // 終端として扱います。外側の後続は折り畳みノードのnextが正であり、
        // 展開時に現在の外側接続で上書きします。
        inner.nodes[merge.id]?.next = null
        fullUnit.filterNot { it == condition.id }.forEach(graph.nodes::remove)
        graph.nodes[condition.id] = condition.deepCopy().copy(
            type = CommandType.FOLDED_IF,
            next = continuation,
            trueNext = null,
            falseNext = null,
            pairedNodeId = null,
            foldedGraph = inner,
        )
        if (GraphValidator.validate(graph).isNotEmpty()) {
            graph.entryNodeId = backup.entryNodeId
            graph.nodes.clear()
            graph.nodes.putAll(backup.nodes)
            return false
        }
        return true
    }

    /**
     * 折り畳みノードを展開できるかを判定します。
     *
     * 内部グラフを持ち、先頭が対応する種別（FOLDED_FORならFOR_START、
     * FOLDED_IFならCONDITION）である場合だけ展開できます。
     */
    fun canUnfold(graph: CommandGraph, nodeId: UUID): Boolean {
        val wrapper = graph.nodes[nodeId]?.takeIf { FoldedGraphs.isFolded(it) } ?: return false
        val inner = wrapper.foldedGraph ?: return false
        val head = inner.nodes[wrapper.id] ?: return false
        return when (wrapper.type) {
            CommandType.FOLDED_FOR -> head.type == CommandType.FOR_START &&
                head.pairedNodeId?.let(inner.nodes::get)
                    ?.takeIf { it.type == CommandType.FOR_END && it.pairedNodeId == head.id } != null
            CommandType.FOLDED_IF -> head.type == CommandType.CONDITION &&
                head.pairedNodeId?.let(inner.nodes::get)
                    ?.takeIf { it.type == CommandType.MERGE && it.pairedNodeId == head.id } != null
            else -> false
        }
    }

    /**
     * 折り畳みノードを展開し、内部構造を元の位置へ復元します。
     *
     * 内部IDは折り畳み時に元のIDのまま退避しているため、入力エッジや入口の
     * 付け替えは不要です。折り畳み中の外側設定（回数・条件内容）は正とし、
     * 内部先頭へ上書きしてから復元します。外側の後続が変わっていても、
     * 現在の外側接続を正として内部出口へ反映します。
     */
    fun unfold(graph: CommandGraph, nodeId: UUID): Boolean {
        val wrapper = graph.nodes[nodeId]?.takeIf { FoldedGraphs.isFolded(it) } ?: return false
        val inner = wrapper.foldedGraph?.deepCopy() ?: return false
        val head = inner.nodes[wrapper.id] ?: return false
        val exit = when (wrapper.type) {
            CommandType.FOLDED_FOR -> {
                if (head.type != CommandType.FOR_START) return false
                head.pairedNodeId?.let(inner.nodes::get)
                    ?.takeIf { it.type == CommandType.FOR_END && it.pairedNodeId == head.id }
                    ?: return false
            }
            CommandType.FOLDED_IF -> {
                if (head.type != CommandType.CONDITION) return false
                head.pairedNodeId?.let(inner.nodes::get)
                    ?.takeIf { it.type == CommandType.MERGE && it.pairedNodeId == head.id }
                    ?: return false
            }
            else -> return false
        }
        // 内部IDと外側IDの衝突（折り畳みノード自身を除く）は構造破損とします。
        if (inner.nodes.keys.any { it != wrapper.id && it in graph.nodes }) return false
        val backup = graph.deepCopy()
        // 折り畳み中の外側設定を正として内部先頭へ反映します。
        copyHeadSettings(wrapper, head)
        exit.next = wrapper.next
        graph.nodes.remove(wrapper.id)
        inner.nodes.forEach { (id, node) -> graph.nodes[id] = node }
        if (GraphValidator.validate(graph).isNotEmpty()) {
            graph.entryNodeId = backup.entryNodeId
            graph.nodes.clear()
            graph.nodes.putAll(backup.nodes)
            return false
        }
        return true
    }

    /**
     * 折り畳み単位に含めるノードID群を収集します。
     *
     * 開始鎖から実行エッジを辿り、出口IDへ到達する範囲を集めます。NULL 終端の
     * 枝はその枝で確定し、出口へ到達しない枝があっても収集自体は行います。
     * 辿れない参照・循環がある場合はnullを返します。
     */
    private fun collectUnitIds(graph: CommandGraph, starts: List<UUID>, exitId: UUID): LinkedHashSet<UUID>? {
        val collected = linkedSetOf<UUID>()
        val visited = mutableSetOf<UUID>()
        fun visit(id: UUID): Boolean {
            if (id == exitId) {
                collected += id
                return true
            }
            if (!visited.add(id)) return true
            val node = graph.nodes[id] ?: return false
            collected += id
            node.outgoingIds().forEach { next ->
                if (!visit(next)) return false
            }
            return true
        }
        starts.forEach { if (!visit(it)) return null }
        collected += exitId
        // 先頭自体は外側IDを引き継ぐため、呼び出し側で含めて扱います。
        return collected
    }

    /**
     * 単位を外側から切り離せるかを判定します。
     *
     * 単位内のノード（先頭を除く）へ単位外から実行入力がなく、単位内の実行参照が
     * 単位内・出口・NULL終端に収まる場合だけ切り離せます。出口の後続（外側継続先）
     * は切り離し対象外です。
     */
    private fun isDetachableUnit(
        graph: CommandGraph,
        unitIds: Set<UUID>,
        headId: UUID,
        exitId: UUID,
    ): Boolean {
        if (headId in unitIds) return false
        val fullUnit = unitIds + headId
        // 先頭への入力は一意でなければなりません（入口または単一実行エッジ）。
        if (incomingExecutionLink(graph, headId) == null) return false
        graph.nodes.values.forEach { source ->
            if (source.id in fullUnit) {
                // 単位内の参照は単位内・NULL終端に収まる必要があります。
                // 出口の後続だけが外側継続先として許されます。
                if (source.id == exitId) return@forEach
                source.outgoingIds().forEach { target ->
                    if (target !in fullUnit) return false
                }
                // ペア参照が単位外を指す構造は切り離しません。
                val pair = source.pairedNodeId
                if (pair != null && pair !in fullUnit) return false
            } else {
                // 単位外からの実行入力は、先頭への一意な入力だけが許されます。
                // 一意性は先の incomingExecutionLink で保証済みのため、ここでは
                // 先頭への参照を許し、単位内部（本体・枝・出口）への参照だけ拒否します。
                unitIds.forEach { innerId ->
                    if (source.next == innerId || source.trueNext == innerId || source.falseNext == innerId) {
                        return false
                    }
                }
                val pair = source.pairedNodeId
                if (pair != null && pair in fullUnit) return false
            }
        }
        // 出口（FOR_END／MERGE）は外側継続先を持つため、単位内の他ノードから
        // 参照されても切り離せます。先頭のペアが出口であることを確認します。
        val head = graph.nodes[headId] ?: return false
        if (head.pairedNodeId != exitId) return false
        return true
    }

    /**
     * 折り畳み中の外側設定を内部先頭へ反映します。
     *
     * 折り畳み中の設定編集は外側ノードに対して行うため、展開時は外側を正として
     * 内部先頭の設定（表示設定・実行設定の両方）を上書きします。接続情報と
     * 内部グラフ自体には触れません。
     */
    private fun copyHeadSettings(wrapper: CommandNode, head: CommandNode) {
        head.params.clear()
        head.params.putAll(wrapper.params)
        head.configuredFields = wrapper.configuredFields?.toMutableSet()
        head.targetSpec = wrapper.targetSpec?.copy()
        head.secondaryTargetSpec = wrapper.secondaryTargetSpec?.copy()
        head.destinationSpec = wrapper.destinationSpec?.copy()
        head.destinationTargetSpec = wrapper.destinationTargetSpec?.copy()
        head.destinationFacingSpec = wrapper.destinationFacingSpec?.copy()
        head.conditionPositionSpec = wrapper.conditionPositionSpec?.copy()
        head.controlBlockStates = wrapper.controlBlockStates?.toMutableSet()
        head.blockPositionSpec = wrapper.blockPositionSpec?.copy()
        head.blockFromSpec = wrapper.blockFromSpec?.copy()
        head.blockToSpec = wrapper.blockToSpec?.copy()
        head.soundPositionSpec = wrapper.soundPositionSpec?.copy()
        head.particlePositionSpec = wrapper.particlePositionSpec?.copy()
        head.particleEndPositionSpec = wrapper.particleEndPositionSpec?.copy()
        head.fireworkPositionSpec = wrapper.fireworkPositionSpec?.copy()
        head.summonPositionSpec = wrapper.summonPositionSpec?.copy()
        head.temporaryEntityTargetSpec = wrapper.temporaryEntityTargetSpec?.copy()
        head.temporaryLocationPositionSpec = wrapper.temporaryLocationPositionSpec?.copy()
        head.temporaryLocationFacingSpec = wrapper.temporaryLocationFacingSpec?.copy()
        head.itemTempRef = wrapper.itemTempRef
        head.blockTempRef = wrapper.blockTempRef
        head.soundTempRef = wrapper.soundTempRef
        head.effectTempRef = wrapper.effectTempRef
    }

    private fun pairedForEnd(graph: CommandGraph, start: CommandNode): CommandNode? =
        start.takeIf { it.type == CommandType.FOR_START }
            ?.pairedNodeId?.let(graph.nodes::get)
            ?.takeIf { it.type == CommandType.FOR_END && it.pairedNodeId == start.id }

    /** 移動候補を複製上で試し、検証に通る場合だけ有効とします。 */
    private fun trialBlockMove(graph: CommandGraph, move: (CommandGraph) -> Boolean): Boolean {
        val candidate = graph.deepCopy()
        if (!move(candidate)) return false
        return GraphValidator.validate(candidate).isEmpty()
    }

    /** 対応関係が正しいFOR終了を返します。ペアの壊れた残骸は折り畳み対象外です。 */
    private fun forEndOf(graph: CommandGraph, start: CommandNode): CommandNode? =
        pairedForEnd(graph, start)

    /** 条件に対応する合流が正しく結ばれている場合に返します。 */
    private fun mergedPairOf(graph: CommandGraph, condition: CommandNode): CommandNode? =
        condition.takeIf { it.type == CommandType.CONDITION }
            ?.pairedNodeId?.let(graph.nodes::get)
            ?.takeIf { it.type == CommandType.MERGE && it.pairedNodeId == condition.id }

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
