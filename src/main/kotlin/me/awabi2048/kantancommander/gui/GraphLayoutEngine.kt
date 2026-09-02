package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

data class MapPoint(val x: Int, val y: Int)

data class InsertionTarget(
    val sourceId: UUID?,
    val edge: GraphEditor.Edge,
    val mergeConditionId: UUID? = null,
    /**
     * 弱い候補は、同じセルへ後から明示的な調整経路が来た場合に譲る補助的な挿入先。
     * 合流後・for終了後の水平経路のように、外側構造の経路とセルを共有し得る場所で使う。
    */
    val weak: Boolean = false,
    /**
     * 未合流の入れ子条件を閉じた後に戻る、外側構造の継続先です。
     * 明示的にMERGEを追加する場合だけ、内側MERGEの次へこの継続先を接続します。
     */
    val continuationId: UUID? = null,
) {
    init {
        require(!weak || mergeConditionId == null) { "弱い挿入候補は分岐対応を持ちません" }
    }
}

enum class MapCellKind {
    NODE,
    PATH,
    BRANCH_PATH,
    LOOP_RETURN_PATH,
    ADD,
}

data class MapCell(
    val point: MapPoint,
    val kind: MapCellKind,
    val nodeId: UUID? = null,
    val insertionTarget: InsertionTarget? = null,
)

/**
 * 経路の接続判定で使うセル種別を一箇所に集約します。
 *
 * 画面描画側が個別に「経路らしいセル」を定義すると、レイアウトと描画の境界で
 * ノード／追加ポイント／分岐経路の接続がずれます。論理マップ上で隣接接続を
 * 許可する種別は、この集合だけを共有して判定します。
 */
internal val PATH_CELL_KINDS: Set<MapCellKind> = setOf(
    MapCellKind.PATH,
    MapCellKind.BRANCH_PATH,
    MapCellKind.LOOP_RETURN_PATH,
)

/**
 * 戻り経路矢印の起点にできる、body内の横向き経路セルです。
 * LOOP_RETURN_PATHを除外することで、入れ子forの戻り経路そのものを
 * 外側forの矢印起点として二重投影しないようにします。
 */
private val LOOP_RETURN_ARROW_SOURCE_KINDS: Set<MapCellKind> = setOf(
    MapCellKind.PATH,
    MapCellKind.BRANCH_PATH,
)

internal val CONNECTABLE_CELL_KINDS: Set<MapCellKind> = PATH_CELL_KINDS + setOf(
    MapCellKind.NODE,
    MapCellKind.ADD,
)

/** 論理セルの隣接判定で使う4方向。描画・入力・境界投影で同じ定義を共有します。 */
internal val ORTHOGONAL_DIRECTIONS: List<MapPoint> = listOf(
    MapPoint(-1, 0),
    MapPoint(1, 0),
    MapPoint(0, -1),
    MapPoint(0, 1),
)

/** ビューポートの境界を越えて続く、表示専用の接続情報です。 */
data class ViewportBoundaryConnection(
    /** ビューポート内に存在する接続端点（ローカル座標）。 */
    val visible: MapPoint,
    /** 可視範囲の外側にある論理上の隣接セル（ローカル座標）。 */
    val outside: MapPoint,
    /** 外側セルの種別。外側アイコンそのものは描画しません。 */
    val outsideKind: MapCellKind,
)

/**
 * アイコン・経路・入力判定が共有するビューポート投影です。
 *
 * `cells` は純粋に表示範囲内の論理セルだけを含みます。画面端で経路を途切れさせ
 * ないための `boundaryConnections` は、外側のセルを「描画対象」として持ち込まず、
 * 可視端点から外へ続く接続だけを表します。これにより、画面外のアイコンが表示
 * されることなく、パンしても同じ論理接続が端で連続します。
 */
data class ViewportProjection(
    val origin: MapPoint,
    val width: Int,
    val height: Int,
    val cells: Map<MapPoint, MapCell>,
    val boundaryConnections: Set<ViewportBoundaryConnection>,
    /** body内の中間経路から垂直投影した、戻り経路上の矢印表示用論理スロットです。 */
    val loopReturnArrowPoints: Set<MapPoint> = emptySet(),
) {
    fun contains(local: MapPoint): Boolean =
        local.x in 0 until width && local.y in 0 until height

    /** 可視セルまたは境界継続先が指定種別かを返します。 */
    fun hasNeighborOfKind(local: MapPoint, kind: MapCellKind): Boolean {
        return ORTHOGONAL_DIRECTIONS.any { direction ->
            val neighbor = MapPoint(local.x + direction.x, local.y + direction.y)
            cells[neighbor]?.kind == kind ||
                boundaryConnections.any { it.visible == local && it.outside == neighbor && it.outsideKind == kind }
        }
    }
}

data class GraphLayout(
    val cells: Map<MapPoint, MapCell>,
    val nodePoints: Map<UUID, MapPoint>,
    val width: Int,
    val height: Int,
    /** body内の中間経路を戻り経路へ投影して配置する矢印の論理座標です。 */
    val loopReturnArrowPoints: Set<MapPoint> = emptySet(),
) {
    fun viewport(origin: MapPoint, width: Int, height: Int): Map<MapPoint, MapCell> =
        cells.mapNotNull { (point, cell) ->
            val local = MapPoint(point.x - origin.x, point.y - origin.y)
            if (local.x in 0 until width && local.y in 0 until height) local to cell.copy(point = local) else null
        }.toMap()

    /**
     * 表示・入力で共有する投影を一度だけ生成します。
     *
     * `viewport` と同じ可視範囲を使い、境界の外側に接続セルがある場合だけ
     * `boundaryConnections` を付与します。外側セル自体は投影へ追加しません。
     */
    fun projection(origin: MapPoint, width: Int, height: Int): ViewportProjection {
        require(width > 0) { "viewport width must be positive" }
        require(height > 0) { "viewport height must be positive" }

        val visible = viewport(origin, width, height)
        val boundaries = buildSet {
            visible.forEach visibleCell@{ (local, cell) ->
                if (cell.kind !in CONNECTABLE_CELL_KINDS) return@visibleCell
                ORTHOGONAL_DIRECTIONS.forEach direction@{ direction ->
                    val outside = MapPoint(local.x + direction.x, local.y + direction.y)
                    if (outside.x in 0 until width && outside.y in 0 until height) return@direction

                    val global = MapPoint(
                        origin.x + outside.x,
                        origin.y + outside.y,
                    )
                    val outsideCell = cells[global] ?: return@direction
                    if (outsideCell.kind !in CONNECTABLE_CELL_KINDS) return@direction
                    add(ViewportBoundaryConnection(local, outside, outsideCell.kind))
                }
            }
        }
        val arrows = loopReturnArrowPoints.mapNotNull { global ->
            val local = MapPoint(global.x - origin.x, global.y - origin.y)
            local.takeIf { it.x in 0 until width && it.y in 0 until height }
        }.toSet()
        return ViewportProjection(origin, width, height, visible, boundaries, arrows)
    }

    fun canMove(origin: MapPoint, dx: Int, dy: Int, viewportWidth: Int, viewportHeight: Int): Boolean {
        val next = MapPoint(origin.x + dx, origin.y + dy)
        if (next.x < 0 || next.y < 0) return false
        if (next.x > (width - viewportWidth).coerceAtLeast(0)) return false
        if (next.y > (height - viewportHeight).coerceAtLeast(0)) return false
        return next != origin
    }
}

/** 実グラフへ未保存の仮ノードを挿入した状態と、その対応レイアウトです。 */
data class InsertionPreview(
    val graph: CommandGraph,
    val layout: GraphLayout,
    val insertedNodeId: UUID,
)

/**
 * 永続化されたグラフだけから、余白を含む論理マップを毎回再構築します。
 * GUI都合の圧縮、端での例外配置、レーンへの再投影は行いません。
 */
object GraphLayoutEngine {
    /**
     * グラフを論理セルへ展開します。
     *
     * [maxCells] は外部JSONや設定変更で異常に大きくなったグラフを、描画前に
     * 有限時間・有限メモリで拒否するための安全弁です。通常のGUI呼び出しは
     * 無制限の既定値を使い、ScriptStoreだけが設定上限に応じた値を渡します。
     */
    fun layout(graph: CommandGraph, maxCells: Long = Long.MAX_VALUE): GraphLayout {
        require(maxCells > 0L) { "layout cell limit must be positive" }
        val builder = Builder(graph, maxCells)
        builder.renderRoot()
        val maxX = builder.cells.keys.maxOfOrNull(MapPoint::x) ?: 0
        val maxY = builder.cells.keys.maxOfOrNull(MapPoint::y) ?: 0
        return GraphLayout(
            cells = builder.cells.toMap(),
            nodePoints = builder.nodePoints.toMap(),
            width = maxX + 2,
            height = maxY + 2,
            loopReturnArrowPoints = builder.loopReturnArrowPoints.toSet(),
        )
    }

    /**
     * 挿入候補を、実際に仮ノードを含むグラフとしてレイアウトします。
     *
     * クリック元セルから座標を推測すると、挿入後に右へ移動する後続ノードと
     * ハイライトがずれます。保存前のコピーへGraphEditorの実処理を適用し、
     * 本番描画と同じレイアウトエンジンを通すことで、後続のノード・経路・幅を
     * すべて同じ論理状態から生成します。仮ノード自体は呼び出し側で記号表示へ
     * 差し替えるため、WAITを実行したり保存したりすることはありません。
     */
    fun previewInsertion(
        graph: CommandGraph,
        target: InsertionTarget,
        placeholderType: CommandType = CommandType.WAIT,
    ): InsertionPreview? {
        if (placeholderType == CommandType.MERGE || placeholderType == CommandType.FOR_END) return null
        val candidate = graph.deepCopy()
        val inserted = runCatching {
            GraphEditor.insert(
                candidate,
                target.sourceId,
                target.edge,
                placeholderType,
            )
        }.getOrNull() ?: return null
        val previewLayout = runCatching { layout(candidate) }.getOrNull() ?: return null
        return InsertionPreview(candidate, previewLayout, inserted.id)
    }

    private class Builder(
        private val graph: CommandGraph,
        private val maxCells: Long,
    ) {
        val cells = linkedMapOf<MapPoint, MapCell>()
        val nodePoints = linkedMapOf<UUID, MapPoint>()
        val loopReturnArrowPoints = linkedSetOf<MapPoint>()

        fun renderRoot() {
            val entry = graph.entryNodeId
            if (entry == null) {
                putAdd(1, 1, null, GraphEditor.Edge.ENTRY)
                return
            }
            val segment = renderSequence(entry, null, 1, 1)
            segment.tail?.let { tail ->
                putTailAdd(segment.nextX, nodePoints[tail]?.y ?: 1, tail)
            }
        }

        fun renderSequence(
            start: UUID,
            stop: UUID?,
            x: Int,
            y: Int,
            continuationId: UUID? = null,
        ): Segment {
            var currentId: UUID? = start
            var cursorX = x
            var maximumY = y
            var hasOpenEnd = false
            val visited = mutableSetOf<UUID>()
            while (currentId != null && currentId != stop && visited.add(currentId)) {
                val node = graph.nodes[currentId] ?: break
                if (node.type == CommandType.CONDITION && node.pairedNodeId != null) {
                    val branch = renderCondition(node, cursorX, y)
                    cursorX = branch.nextX
                    maximumY = maxOf(maximumY, branch.maxY)
                    // 合流後の水平経路は「合流と次コマンドの間」への弱い挿入候補（仕様13.5）。
                    // 外側分岐の調整経路とセルを共有する場合はそちらへ譲る。
                    val mergeNode = graph.nodes[node.pairedNodeId]
                    currentId = mergeNode?.next
                    if (currentId != null) {
                        val continuationTarget = mergeNode?.let {
                            InsertionTarget(it.id, GraphEditor.Edge.NEXT, weak = true)
                        }
                        putTargetedPath(cursorX - 1, y, target = continuationTarget)
                        if (currentId == stop) {
                            // ここで親の合流へ到達した場合、合流後の補償経路を
                            // 上位のrenderConditionへ返します。targetを失うと、
                            // 上位側が横幅を合わせるために延ばしたセルだけが
                            // 見た目の経路になり、クリックしても挿入できません。
                            return Segment(
                                cursorX,
                                maximumY,
                                null,
                                hasOpenEnd,
                                mainInsertionTarget = continuationTarget,
                            )
                        }
                    } else return Segment(
                        cursorX,
                        maximumY,
                        branch.tail,
                        branch.hasOpenEnd,
                        mainNextX = branch.mainNextX,
                    )
                    continue
                }
                if (node.type == CommandType.CONDITION) {
                    // 親の分岐内では、開いた条件のTRUE/FALSE枝も親の合流境界で
                    // 停止させます。これをnullで描画すると、既存のMERGEを
                    // 子条件の通常ノードとして二重描画してセルが衝突します。
                    val branch = renderOpenCondition(node, cursorX, y, stop)
                    cursorX = branch.nextX
                    maximumY = maxOf(maximumY, branch.maxY)
                    hasOpenEnd = hasOpenEnd || branch.hasOpenEnd
                    // nextXは下側のFALSE枝を含む全体幅です。一方、親の合流へ
                    // 上段を延長するときは、条件のTRUE枝が実際に終わった列だけを
                    // 使う必要があります。ここでmainNextXを保持しないと、内側の
                    // 条件分岐の下枝幅ぶん上段に空白が生じます。
                    return Segment(
                        cursorX,
                        maximumY,
                        null,
                        hasOpenEnd,
                        mainNextX = branch.mainNextX,
                        mainInsertionTarget = branch.mainInsertionTarget,
                    )
                }
                if (node.type == CommandType.FOR_START && node.pairedNodeId != null) {
                    val loop = renderFor(node, cursorX, y)
                    cursorX = loop.nextX
                    maximumY = maxOf(maximumY, loop.maxY)
                    // for終了後の水平経路は「for終了と次コマンドの間」への弱い挿入候補（仕様13.5）。
                    val endNode = graph.nodes[node.pairedNodeId]
                    currentId = endNode?.next
                    if (currentId != null) {
                        val continuationTarget = endNode?.let {
                            InsertionTarget(it.id, GraphEditor.Edge.NEXT, weak = true)
                        }
                        putTargetedPath(cursorX - 1, y, target = continuationTarget)
                        if (currentId == stop) {
                            // FOR_ENDの直前へ到達した経路も、親分岐の合流経路と
                            // 同じく上位の幅調整へ引き継ぎます。
                            return Segment(
                                cursorX,
                                maximumY,
                                null,
                                mainInsertionTarget = continuationTarget,
                            )
                        }
                    } else return Segment(cursorX, maximumY, loop.tail, mainNextX = loop.mainNextX)
                    continue
                }
                putNode(cursorX, y, node)
                maximumY = maxOf(maximumY, y)
                val next = node.next
                if (next == null || next == stop) return Segment(cursorX + 2, maximumY, node.id)
                putPath(
                    cursorX + 1,
                    y,
                    sourceId = node.id,
                    edge = GraphEditor.Edge.NEXT,
                    continuationId = continuationId,
                )
                cursorX += 2
                currentId = next
            }
            return Segment(cursorX, maximumY, null, hasOpenEnd)
        }

        private fun renderCondition(condition: CommandNode, x: Int, y: Int): Segment {
            putNode(x, y, condition)
            val mergeId = condition.pairedNodeId ?: return Segment(x + 2, y, condition.id)

            val trueStart = condition.trueNext
            val trueSegment = if (trueStart != null && trueStart != mergeId) {
                putPath(x + 1, y, MapCellKind.BRANCH_PATH, condition.id, GraphEditor.Edge.TRUE, condition.id)
                renderSequence(trueStart, mergeId, x + 2, y)
            } else {
                putPath(x + 1, y, MapCellKind.BRANCH_PATH, condition.id, GraphEditor.Edge.TRUE, condition.id)
                if (trueStart == null) {
                    // 対応MERGEを持つ条件でも、枝をここで正常終了させられます。
                    // 追加ポイントは「合流へ進むための経路」と区別せず、枝へ次の
                    // コマンドを追加できる通常の終端として表示します。
                    putAdd(x + 2, y, condition.id, GraphEditor.Edge.TRUE, condition.id)
                    Segment(x + 4, y, null, hasOpenEnd = true)
                } else {
                    // trueNext == mergeId は既に合流へ接続済みの空枝です。
                    Segment(x + 2, y, null)
                }
            }
            val trueReachesMerge = trueStart != null && hasPathTo(trueStart, mergeId)
            if (!trueReachesMerge && trueSegment.tail != null) {
                // 通常ノード列がNULLで終わる場合は、列の末尾にも枝追加点を置きます。
                putTailAdd(trueSegment.nextX, y, trueSegment.tail, condition.id)
            }

            val falseY = trueSegment.maxY + 2
            // FALSE枝は条件分岐アイコンの中心列から真下へ降ろします。これにより、
            // 「分岐ノードの下側からfalse鎖が出る」という接続方向を論理マップでも
            // 保証します。縦線と角は接続専用で、挿入候補は下段の水平経路へ付与します。
            putVerticalBranchPath(x, y + 1, falseY)
            putPath(
                x + 1,
                falseY,
                MapCellKind.BRANCH_PATH,
                condition.id,
                GraphEditor.Edge.FALSE,
                condition.id,
            )
            val falseStart = condition.falseNext
            val falseSegment = if (falseStart != null && falseStart != mergeId) {
                renderSequence(falseStart, mergeId, x + 2, falseY)
            } else {
                if (falseStart == null) {
                    putAdd(x + 2, falseY, condition.id, GraphEditor.Edge.FALSE, condition.id)
                    Segment(x + 4, falseY, null, hasOpenEnd = true)
                } else {
                    putPath(x + 1, falseY, MapCellKind.BRANCH_PATH, condition.id, GraphEditor.Edge.FALSE, condition.id)
                    Segment(x + 2, falseY, null)
                }
            }
            val falseReachesMerge = falseStart != null && hasPathTo(falseStart, mergeId)
            if (!falseReachesMerge && falseSegment.tail != null) {
                putTailAdd(falseSegment.nextX, falseY, falseSegment.tail, condition.id)
            }

            // 合流ノードは最長枝の nextX に置き、通常ノード列と同じ2ピッチを保ちます。
            // TRUE枝は従来どおり左側から直進させますが、折り返すFALSE枝は mergeX 列まで
            // 水平に延ばしてから真上へ戻します。これにより、合流ノードの直下セルが最後の
            // 接続端点になり、画面上でもMERGEアイコンの下側ポートへ経路が潜り込みます。
            //
            // ただし、枝の中に未合流の条件分岐がある場合、nextXがその枝の追加ポイント
            // 自体を指すことがあります（通常ノードを追加した直後のFALSE枝が該当）。
            // その列へ合流側の縦線を置くと、追加ポイントを経路で上書きして描画例外になり、
            // UI側では候補選択が無反応に見えます。開いた枝を含むときはノード1個分を
            // 予約してから合流列を決め、追加ポイントと戻り経路を必ず別列へ分離します。
            val openBranchClearance =
                if (trueSegment.hasOpenEnd || falseSegment.hasOpenEnd) 2 else 0
            val mergeX = maxOf(trueSegment.nextX, falseSegment.nextX) + openBranchClearance
            val mergeNodeX = mergeX
            // 枝の長さ調整で連続する水平経路が生じた場合は、その全セルを同じ
            // 挿入判定領域にします。どのセルをクリックしてもエッジ直後へ挿入され、
            // UI側では実際の挿入先ノード位置だけをハイライトします。
            val trueTarget = branchInsertionTarget(
                condition.id,
                GraphEditor.Edge.TRUE,
                trueStart,
                mergeId,
                trueSegment,
            )
            val falseTarget = branchInsertionTarget(
                condition.id,
                GraphEditor.Edge.FALSE,
                falseStart,
                mergeId,
                falseSegment,
            )
            // 条件直下の縦幹は、FALSE枝の先頭への挿入を受け付けます（開いた枝・
            // 閉じた枝で共通）。空枝では追加ボタンが枝への追加を担うため、
            // 縦幹は接続専用になります。
            val falseStemTarget = if (falseStart == null || falseStart == mergeId) null
                else InsertionTarget(condition.id, GraphEditor.Edge.FALSE, condition.id)
            markVerticalInsertionTarget(x, y + 1, falseY, falseStemTarget)
            if (trueReachesMerge) {
                fillHorizontal(
                    trueSegment.mainNextX - 1,
                    mergeX - 1,
                    y,
                    MapCellKind.BRANCH_PATH,
                    target = trueTarget,
                )
            }
            if (falseReachesMerge) {
                fillHorizontal(
                    falseSegment.mainNextX - 1,
                    mergeX,
                    falseY,
                    MapCellKind.BRANCH_PATH,
                    target = falseTarget,
                )
            }
            // 合流側の縦線・角もfalse末尾からMERGEへ接続するFALSE経路の一部です。
            // 水平部分と同じ挿入判定領域を全セルへ付与し、縦横で操作仕様を揃えます。
            if (falseReachesMerge) {
                putVerticalBranchPath(mergeX, y + 1, falseY)
                markVerticalInsertionTarget(mergeX, y + 1, falseY, falseTarget)
            }

            val merge = graph.nodes[mergeId] ?: return Segment(mergeNodeX, falseSegment.maxY, condition.id)
            // 枝が空／短い場合でも、角セルを合流直前の経路として明示的に
            // 再設定し、「水平枝→L字の角→合流アイコン」の接続を保証します。
            // 合流直前の角も水平経路の一部として同じ判定領域を維持します。縦線側へ
            // 候補を複製しないことで、L字の接続方向と挿入方向を混同しません。
            if (trueReachesMerge) putPath(mergeX - 1, y, MapCellKind.BRANCH_PATH)
            putNode(mergeNodeX, y, merge)
            return Segment(mergeNodeX + 2, maxOf(falseSegment.maxY, falseY), merge.id)
        }

        /** 枝内に対応MERGEへ到達する経路が一つでもあるかを調べます。 */
        private fun hasPathTo(start: UUID, stop: UUID): Boolean {
            val visited = mutableSetOf<UUID>()
            fun visit(id: UUID): Boolean {
                if (id == stop) return true
                if (!visited.add(id)) return false
                val node = graph.nodes[id] ?: return false
                val next = when (node.type) {
                    CommandType.CONDITION -> listOfNotNull(node.trueNext, node.falseNext)
                    CommandType.FOR_START -> listOfNotNull(node.trueNext)
                    else -> listOfNotNull(node.next)
                }
                return next.any(::visit)
            }
            return visit(start)
        }

        /**
         * 枝の主行を、指定された停止ノードへ延長するときの挿入先を解決します。
         *
         * 末尾ノードが返る通常枝はそのNEXT、空枝は条件自身のTRUE/FALSEです。
         * 入れ子の条件・forが既に停止ノードまでの経路を描いている場合は、
         * 子Segmentが保持する終端ターゲットをそのまま引き継ぎます。
         */
        private fun branchInsertionTarget(
            conditionId: UUID,
            edge: GraphEditor.Edge,
            start: UUID?,
            stop: UUID?,
            segment: Segment,
            continuationId: UUID? = null,
        ): InsertionTarget? {
            if (start == null || stop == null || !hasPathTo(start, stop)) return null
            return when {
                start == stop -> InsertionTarget(conditionId, edge, conditionId, continuationId = continuationId)
                segment.tail != null -> InsertionTarget(
                    segment.tail,
                    GraphEditor.Edge.NEXT,
                    conditionId,
                    continuationId = continuationId,
                )
                else -> segment.mainInsertionTarget
            }
        }

        private fun renderOpenCondition(condition: CommandNode, x: Int, y: Int, stop: UUID?): Segment {
            putNode(x, y, condition)
            putPath(
                x + 1,
                y,
                MapCellKind.BRANCH_PATH,
                condition.id,
                GraphEditor.Edge.TRUE,
                condition.id,
                continuationId = stop,
            )
            val trueStart = condition.trueNext
            val trueSegment = when {
                trueStart != null && trueStart == stop -> Segment(x + 2, y, null)
                trueStart != null -> renderSequence(trueStart, stop, x + 2, y, continuationId = stop)
                else -> {
                    putAdd(
                        x + 2,
                        y,
                        condition.id,
                        GraphEditor.Edge.TRUE,
                        condition.id,
                        continuationId = stop,
                    )
                    Segment(x + 4, y, null, hasOpenEnd = true)
                }
            }
            if (trueSegment.tail != null && (stop == null || graph.nodes[trueSegment.tail]?.next != stop)) {
                putTailAdd(trueSegment.nextX, y, trueSegment.tail, condition.id, continuationId = stop)
            }
            val falseY = trueSegment.maxY + 2
            // FALSE枝は条件分岐アイコンの中心列から真下へ降ろします。縦線も
            // 水平枝と同じFALSE経路の一部なので、下段へ合流しない条件分岐でも
            // 同じ挿入先を全高さへ付与します。
            putVerticalBranchPath(x, y + 1, falseY)
            putPath(
                x + 1,
                falseY,
                MapCellKind.BRANCH_PATH,
                condition.id,
                GraphEditor.Edge.FALSE,
                condition.id,
                continuationId = stop,
            )
            val falseStart = condition.falseNext
            val falseSegment = when {
                falseStart != null && falseStart == stop -> Segment(x + 2, falseY, null)
                falseStart != null -> renderSequence(falseStart, stop, x + 2, falseY, continuationId = stop)
                else -> {
                    putAdd(
                        x + 2,
                        falseY,
                        condition.id,
                        GraphEditor.Edge.FALSE,
                        condition.id,
                        continuationId = stop,
                    )
                    Segment(x + 4, falseY, null, hasOpenEnd = true)
                }
            }
            if (falseSegment.tail != null && (stop == null || graph.nodes[falseSegment.tail]?.next != stop)) {
                putTailAdd(falseSegment.nextX, falseY, falseSegment.tail, condition.id, continuationId = stop)
            }
            // 条件直下の縦幹は、FALSE枝の先頭への挿入を受け付けます（開いた枝・
            // 閉じた枝で共通）。空枝（枝の先頭にノードがなく、追加ボタンが枝への
            // 追加を担う場合）では、縦幹は接続専用になります。
            val falseStemTarget = if (falseStart == null || falseStart == stop) null
                else InsertionTarget(condition.id, GraphEditor.Edge.FALSE, condition.id, continuationId = stop)
            markVerticalInsertionTarget(x, y + 1, falseY, falseStemTarget)
            val mainInsertionTarget = branchInsertionTarget(
                condition.id,
                GraphEditor.Edge.TRUE,
                trueStart,
                stop,
                trueSegment,
                continuationId = stop,
            )
            val trueOpen = trueStart == null || trueSegment.hasOpenEnd ||
                (trueSegment.tail != null && (stop == null || graph.nodes[trueSegment.tail]?.next != stop))
            val falseOpen = falseStart == null || falseSegment.hasOpenEnd ||
                (falseSegment.tail != null && (stop == null || graph.nodes[falseSegment.tail]?.next != stop))
            return Segment(
                maxOf(trueSegment.nextX, falseSegment.nextX),
                falseSegment.maxY,
                null,
                hasOpenEnd = trueOpen || falseOpen,
                // 親へ返す上段終端はTRUE枝の実際の幅です。nextX（下側枝を含む
                // 全体幅）をそのまま返すと、親の合流補完で経路が空きます。
                mainNextX = trueSegment.mainNextX,
                mainInsertionTarget = mainInsertionTarget,
            )
        }

        private fun renderFor(start: CommandNode, x: Int, y: Int): Segment {
            putNode(x, y, start)
            val endId = start.pairedNodeId ?: return Segment(x + 2, y, start.id)
            // bodyの再帰描画が追加するノード座標だけをスナップショットします。
            // これにより、単純な直列ノードだけでなく、body内の条件分岐・入れ子forの
            // ノードからも同じ戻り経路へ矢印を投影できます。開始・終了ノード自身は
            // 戻り先を示す内部ノードではないため、スナップショットの外側に置きます。
            val bodyNodeIdsBefore = nodePoints.keys.toSet()
            val bodyStart = start.trueNext
            val body = if (bodyStart != null && bodyStart != endId) {
                putPath(x + 1, y, sourceId = start.id, edge = GraphEditor.Edge.FOR_BODY)
                renderSequence(bodyStart, endId, x + 2, y)
            } else {
                Segment(x + 2, y, null)
            }
            val endX = body.nextX
            when {
                bodyStart == null || bodyStart == endId ->
                    // 空bodyの開始・終了間の経路は、body先頭への挿入（FOR_BODY）を受け付ける（仕様10.1）。
                    putPath(endX - 1, y, sourceId = start.id, edge = GraphEditor.Edge.FOR_BODY)
                body.tail != null ->
                    putPath(endX - 1, y, sourceId = body.tail, edge = GraphEditor.Edge.NEXT)
                body.mainInsertionTarget != null ->
                    // body内の条件・forがFOR_ENDへ到達した場合、末尾ノードを
                    // 返せなくても主行の経路には一意な挿入先があります。
                    // そのtargetをFOR_END直前の補償経路全体へ引き継ぎます。
                    // 下側枝の幅でbody.nextXが広がる場合も、主行の空白を残さず
                    // 連続経路として描画します。
                    fillHorizontal(
                        body.mainNextX - 1,
                        endX - 1,
                        y,
                        if (body.mainInsertionTarget.mergeConditionId != null) {
                            MapCellKind.BRANCH_PATH
                        } else {
                            MapCellKind.PATH
                        },
                        target = body.mainInsertionTarget,
                    )
                else ->
                    // 未合流分岐など挿入先が曖昧なbody末尾では誤挿入より無反応が安全なため装飾扱いにする。
                    // 枝末端には既に黄色の追加アイコンがあるため追加導線は失われない。
                    putPath(endX - 1, y)
            }
            val end = graph.nodes[endId] ?: return Segment(endX, body.maxY, start.id)
            putNode(endX, y, end)

            val returnY = body.maxY + 2
            val bodyHasNodes = nodePoints.keys.any { it !in bodyNodeIdsBefore }
            if (bodyHasNodes) {
                // ノードの中心列ではなく、body内で実際にノード同士をつなぐ横経路セルを
                // 矢印の起点にします。経路セルの左右が接続可能なら「内部ノードの中間」
                // とみなし、その論理X列を戻り経路へ垂直投影します。これにより、分岐の
                // 下枝や入れ子forを含めても、ノードそのものへ矢印が重ならず、経路上の
                // 複数スロットへ等間隔に「«」を配置できます。
                cells.asSequence()
                    .filter { (point, cell) ->
                        point.x in (x + 1) until endX &&
                            point.y in y..body.maxY &&
                            cell.kind in LOOP_RETURN_ARROW_SOURCE_KINDS &&
                            isHorizontalBodyPathSlot(point)
                    }
                    .map { (point, _) -> point.x }
                    .distinct()
                    .forEach { arrowX ->
                        loopReturnArrowPoints += MapPoint(arrowX, returnY)
                    }
            }
            for (verticalY in y + 1..returnY) {
                putPath(endX, verticalY, MapCellKind.LOOP_RETURN_PATH)
                putPath(x, verticalY, MapCellKind.LOOP_RETURN_PATH)
            }
            fillHorizontal(x, endX, returnY, MapCellKind.LOOP_RETURN_PATH)
            return Segment(endX + 2, returnY, end.id)
        }

        /**
         * 経路セルがノード間の水平経路に属するかを判定します。
         * 縦枝の途中は上下だけで接続されるため、左右の接続可能セルを要求すると、
         * 縦線や曲がり角を矢印の起点へ誤って採用しません。
         */
        private fun isHorizontalBodyPathSlot(point: MapPoint): Boolean {
            val leftKind = cells[MapPoint(point.x - 1, point.y)]?.kind
            val rightKind = cells[MapPoint(point.x + 1, point.y)]?.kind
            return leftKind in CONNECTABLE_CELL_KINDS && rightKind in CONNECTABLE_CELL_KINDS
        }

        private fun fillHorizontal(
            from: Int,
            to: Int,
            y: Int,
            kind: MapCellKind,
            target: InsertionTarget? = null,
        ) {
            if (from > to) return
            // 長さ調整で同じ論理エッジを複数セルへ描く場合は、from..to の全セルを
            // 同じ挿入判定領域として扱います。子構造から返されたcontinuationIdも
            // ここで保持し、親の合流へ補償経路を延ばしても、挿入後の再合流情報を
            // 失わないようにします。
            for (pathX in from..to) {
                putTargetedPath(pathX, y, kind, target)
            }
        }

        /** 分岐の縦経路を水平経路と同じセル種別で生成します。 */
        private fun putVerticalBranchPath(x: Int, fromY: Int, toY: Int) {
            if (fromY > toY) return
            for (verticalY in fromY..toY) {
                putPath(x, verticalY, MapCellKind.BRANCH_PATH)
            }
        }

        /**
         * 条件直下の縦経路へ、FALSE枝の先頭への挿入先を付与します。
         * 空枝（枝の先頭にノードがなく、追加ボタンが枝への追加を担う場合）は
         * 接続専用にします。合流側の縦幹は、枝末端への挿入を担うため呼び出し側が
         * 横経路と同じターゲットを渡します（renderCondition参照）。
         */
        private fun markVerticalInsertionTarget(
            x: Int,
            fromY: Int,
            toY: Int,
            target: InsertionTarget?,
        ) {
            if (target == null || fromY > toY) return
            for (verticalY in fromY..toY) {
                addInsertionTarget(MapPoint(x, verticalY), target)
            }
        }

        private fun putNode(x: Int, y: Int, node: CommandNode) {
            val point = MapPoint(x, y)
            val existing = cells[point]
            check(existing == null || (existing.kind == MapCellKind.NODE && existing.nodeId == node.id)) {
                "ノードが既存の経路または別ノードと衝突しています: point=$point existing=$existing node=${node.id}"
            }
            // 同じ構造ノードが分岐の描画経路上へ再登場する場合があります。
            // そのときは最新位置をクリック対象として採用しますが、経路／追加ポイント
            // の上書きだけは上の衝突検査で必ず拒否します。
            ensureCapacity(point)
            cells[point] = MapCell(point, MapCellKind.NODE, node.id)
            nodePoints[node.id] = point
        }

        private fun putPath(
            x: Int,
            y: Int,
            kind: MapCellKind = MapCellKind.PATH,
            sourceId: UUID? = null,
            edge: GraphEditor.Edge? = null,
            mergeConditionId: UUID? = null,
            weakInsertionTarget: Boolean = false,
            continuationId: UUID? = null,
        ) {
            val point = MapPoint(x, y)
            val existing = cells[point]
            check(existing?.kind != MapCellKind.NODE && existing?.kind != MapCellKind.ADD) {
                "生成経路が実行要素と衝突しています: point=$point existing=$existing"
            }
            val incomingTarget = edge?.let {
                InsertionTarget(
                    sourceId,
                    it,
                    mergeConditionId,
                    weak = weakInsertionTarget,
                    continuationId = continuationId,
                )
            }
            val resolvedKind = mergePathKind(existing?.kind, kind)
            val resolvedTarget = mergeInsertionTarget(point, existing?.insertionTarget, incomingTarget)
            ensureCapacity(point)
            cells[point] = MapCell(point, resolvedKind, insertionTarget = resolvedTarget)
        }

        /** 挿入先の全属性を保ったまま、表示経路へ同じ判定を付与します。 */
        private fun putTargetedPath(
            x: Int,
            y: Int,
            kind: MapCellKind = MapCellKind.PATH,
            target: InsertionTarget?,
        ) {
            putPath(
                x,
                y,
                kind,
                sourceId = target?.sourceId,
                edge = target?.edge,
                mergeConditionId = target?.mergeConditionId,
                weakInsertionTarget = target?.weak == true,
                continuationId = target?.continuationId,
            )
        }

        /** 既に描画済みの縦経路へ、同じ実行エッジの挿入候補を後付けします。 */
        private fun addInsertionTarget(point: MapPoint, target: InsertionTarget) {
            val existing = cells[point] ?: return
            check(existing.kind in PATH_CELL_KINDS) {
                "縦経路の挿入判定対象が経路セルではありません: point=$point existing=$existing"
            }
            val resolved = mergeInsertionTarget(point, existing.insertionTarget, target)
            cells[point] = existing.copy(insertionTarget = resolved)
        }

        private fun putAdd(
            x: Int,
            y: Int,
            sourceId: UUID?,
            edge: GraphEditor.Edge,
            mergeConditionId: UUID? = null,
            continuationId: UUID? = null,
        ) {
            val point = MapPoint(x, y)
            check(cells[point] == null) {
                "追加位置が既存要素と衝突しています: point=$point existing=${cells[point]}"
            }
            ensureCapacity(point)
            cells[point] = MapCell(
                point,
                MapCellKind.ADD,
                insertionTarget = InsertionTarget(
                    sourceId,
                    edge,
                    mergeConditionId,
                    continuationId = continuationId,
                ),
            )
        }

        private fun ensureCapacity(point: MapPoint) {
            if (point !in cells && cells.size.toLong() >= maxCells) {
                throw IllegalArgumentException("描画セル数が上限 $maxCells を超えています: point=$point")
            }
        }

        /**
         * 末尾ノードの直後へ追加ポイントを置く共通処理です。
         *
         * `renderSequence` は末尾ノードの次セルを予約せずに Segment を返すため、
         * 追加ポイントだけを置くと「ノード→空セル→追加」の未接続状態になります。
         * 必ず直前セルへ NEXT 経路を生成してから追加ポイントを配置します。
         */
        private fun putTailAdd(
            x: Int,
            y: Int,
            sourceId: UUID,
            mergeConditionId: UUID? = null,
            continuationId: UUID? = null,
        ) {
            putPath(
                x - 1,
                y,
                sourceId = sourceId,
                edge = GraphEditor.Edge.NEXT,
                mergeConditionId = mergeConditionId,
                continuationId = continuationId,
            )
            putAdd(
                x,
                y,
                sourceId,
                GraphEditor.Edge.NEXT,
                mergeConditionId,
                continuationId,
            )
        }

        private fun mergePathKind(existing: MapCellKind?, incoming: MapCellKind): MapCellKind = when {
            existing == null || existing == incoming -> incoming
            existing == MapCellKind.PATH -> incoming
            incoming == MapCellKind.PATH -> existing
            else -> error("異なる種類の生成経路が交差しています: existing=$existing incoming=$incoming")
        }

        private fun mergeInsertionTarget(
            point: MapPoint,
            existing: InsertionTarget?,
            incoming: InsertionTarget?,
        ): InsertionTarget? = when {
            existing == null -> incoming
            incoming == null || existing == incoming -> existing
            // 弱い候補は、同じセルを共有する明示的な調整経路へ譲る。
            existing.weak && !incoming.weak -> incoming
            incoming.weak && !existing.weak -> existing
            else -> error("異なる挿入位置が同じセルを共有しています: point=$point existing=$existing incoming=$incoming")
        }
    }

    private data class Segment(
        val nextX: Int,
        val maxY: Int,
        val tail: UUID?,
        /** 親構造が末尾の挿入先を補完してはいけない開いた枝を含むか。 */
        val hasOpenEnd: Boolean = false,
        /** 現在の主行（TRUE／通常行）で実際に経路が終わる次列。 */
        val mainNextX: Int = nextX,
        /** 主行の終端経路を親構造が延長するときに引き継ぐ挿入先。 */
        val mainInsertionTarget: InsertionTarget? = null,
    )
}
