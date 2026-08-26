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

data class GraphLayout(
    val cells: Map<MapPoint, MapCell>,
    val nodePoints: Map<UUID, MapPoint>,
    val width: Int,
    val height: Int,
) {
    fun viewport(origin: MapPoint, width: Int, height: Int): Map<MapPoint, MapCell> =
        cells.mapNotNull { (point, cell) ->
            val local = MapPoint(point.x - origin.x, point.y - origin.y)
            if (local.x in 0 until width && local.y in 0 until height) local to cell.copy(point = local) else null
        }.toMap()

    fun canMove(origin: MapPoint, dx: Int, dy: Int, viewportWidth: Int, viewportHeight: Int): Boolean {
        val next = MapPoint(origin.x + dx, origin.y + dy)
        if (next.x < 0 || next.y < 0) return false
        if (next.x > (width - viewportWidth).coerceAtLeast(0)) return false
        if (next.y > (height - viewportHeight).coerceAtLeast(0)) return false
        return next != origin
    }
}

/**
 * 永続化されたグラフだけから、余白を含む論理マップを毎回再構築します。
 * GUI都合の圧縮、端での例外配置、レーンへの再投影は行いません。
 */
object GraphLayoutEngine {
    fun layout(graph: CommandGraph): GraphLayout {
        val builder = Builder(graph)
        builder.renderRoot()
        val maxX = builder.cells.keys.maxOfOrNull(MapPoint::x) ?: 0
        val maxY = builder.cells.keys.maxOfOrNull(MapPoint::y) ?: 0
        return GraphLayout(
            cells = builder.cells.toMap(),
            nodePoints = builder.nodePoints.toMap(),
            width = maxX + 2,
            height = maxY + 2,
        )
    }

    private class Builder(private val graph: CommandGraph) {
        val cells = linkedMapOf<MapPoint, MapCell>()
        val nodePoints = linkedMapOf<UUID, MapPoint>()

        fun renderRoot() {
            val entry = graph.entryNodeId
            if (entry == null) {
                putAdd(1, 1, null, GraphEditor.Edge.ENTRY)
                return
            }
            val segment = renderSequence(entry, null, 1, 1)
            segment.tail?.let { tail ->
                putAdd(segment.nextX, nodePoints[tail]?.y ?: 1, tail, GraphEditor.Edge.NEXT)
            }
        }

        fun renderSequence(start: UUID, stop: UUID?, x: Int, y: Int): Segment {
            var currentId: UUID? = start
            var cursorX = x
            var maximumY = y
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
                        putPath(
                            cursorX - 1, y,
                            sourceId = mergeNode?.id,
                            edge = GraphEditor.Edge.NEXT,
                            weakInsertionTarget = true,
                        )
                    } else return Segment(cursorX, maximumY, branch.tail)
                    continue
                }
                if (node.type == CommandType.CONDITION) {
                    val branch = renderOpenCondition(node, cursorX, y)
                    cursorX = branch.nextX
                    maximumY = maxOf(maximumY, branch.maxY)
                    currentId = null
                    continue
                }
                if (node.type == CommandType.FOR_START && node.pairedNodeId != null) {
                    val loop = renderFor(node, cursorX, y)
                    cursorX = loop.nextX
                    maximumY = maxOf(maximumY, loop.maxY)
                    // for終了後の水平経路は「for終了と次コマンドの間」への弱い挿入候補（仕様13.5）。
                    val endNode = graph.nodes[node.pairedNodeId]
                    currentId = endNode?.next
                    if (currentId != null) {
                        putPath(
                            cursorX - 1, y,
                            sourceId = endNode?.id,
                            edge = GraphEditor.Edge.NEXT,
                            weakInsertionTarget = true,
                        )
                    } else return Segment(cursorX, maximumY, loop.tail)
                    continue
                }
                putNode(cursorX, y, node)
                maximumY = maxOf(maximumY, y)
                val next = node.next
                if (next == null || next == stop) return Segment(cursorX + 2, maximumY, node.id)
                putPath(cursorX + 1, y, sourceId = node.id, edge = GraphEditor.Edge.NEXT)
                cursorX += 2
                currentId = next
            }
            return Segment(cursorX, maximumY, null)
        }

        private fun renderCondition(condition: CommandNode, x: Int, y: Int): Segment {
            putNode(x, y, condition)
            val mergeId = condition.pairedNodeId ?: return Segment(x + 2, y, condition.id)

            val trueStart = condition.trueNext
            val trueSegment = if (trueStart != null && trueStart != mergeId) {
                putPath(x + 1, y, MapCellKind.BRANCH_PATH, condition.id, GraphEditor.Edge.TRUE, condition.id)
                renderSequence(trueStart, mergeId, x + 2, y)
            } else Segment(x + 2, y, null)

            val falseY = trueSegment.maxY + 2
            putPath(x + 1, y, MapCellKind.BRANCH_PATH, condition.id, GraphEditor.Edge.TRUE, condition.id)
            for (verticalY in y + 1..falseY) {
                putPath(x + 1, verticalY, MapCellKind.BRANCH_PATH, condition.id, GraphEditor.Edge.FALSE, condition.id)
            }
            val falseStart = condition.falseNext
            val falseSegment = if (falseStart != null && falseStart != mergeId) {
                renderSequence(falseStart, mergeId, x + 2, falseY)
            } else Segment(x + 2, falseY, null)

            val mergeX = maxOf(trueSegment.nextX, falseSegment.nextX)
            val trueSource = trueSegment.tail ?: condition.id
            val trueEdge = if (trueSegment.tail == null) GraphEditor.Edge.TRUE else GraphEditor.Edge.NEXT
            val falseSource = falseSegment.tail ?: condition.id
            val falseEdge = if (falseSegment.tail == null) GraphEditor.Edge.FALSE else GraphEditor.Edge.NEXT
            fillHorizontal(
                trueSegment.nextX - 1,
                mergeX - 1,
                y,
                MapCellKind.BRANCH_PATH,
                trueSource,
                trueEdge,
                condition.id,
            )
            fillHorizontal(
                falseSegment.nextX - 1,
                mergeX - 1,
                falseY,
                MapCellKind.BRANCH_PATH,
                falseSource,
                falseEdge,
                condition.id,
            )
            for (verticalY in y + 1..falseY) {
                putPath(
                    mergeX - 1,
                    verticalY,
                    MapCellKind.BRANCH_PATH,
                    falseSource,
                    falseEdge,
                    condition.id,
                )
            }

            val merge = graph.nodes[mergeId] ?: return Segment(mergeX, falseSegment.maxY, condition.id)
            putNode(mergeX, y, merge)
            return Segment(mergeX + 2, maxOf(falseSegment.maxY, falseY), merge.id)
        }

        private fun renderOpenCondition(condition: CommandNode, x: Int, y: Int): Segment {
            putNode(x, y, condition)
            putPath(x + 1, y, MapCellKind.BRANCH_PATH, condition.id, GraphEditor.Edge.TRUE, condition.id)
            val trueStart = condition.trueNext
            val trueSegment = if (trueStart != null) renderSequence(trueStart, null, x + 2, y)
            else {
                putAdd(x + 2, y, condition.id, GraphEditor.Edge.TRUE, condition.id)
                Segment(x + 4, y, null)
            }
            if (trueSegment.tail != null) {
                putAdd(trueSegment.nextX, y, trueSegment.tail, GraphEditor.Edge.NEXT, condition.id)
            }
            val falseY = trueSegment.maxY + 2
            for (verticalY in y + 1..falseY) {
                putPath(x + 1, verticalY, MapCellKind.BRANCH_PATH, condition.id, GraphEditor.Edge.FALSE, condition.id)
            }
            val falseStart = condition.falseNext
            val falseSegment = if (falseStart != null) renderSequence(falseStart, null, x + 2, falseY)
            else {
                putAdd(x + 2, falseY, condition.id, GraphEditor.Edge.FALSE, condition.id)
                Segment(x + 4, falseY, null)
            }
            if (falseSegment.tail != null) {
                putAdd(falseSegment.nextX, falseY, falseSegment.tail, GraphEditor.Edge.NEXT, condition.id)
            }
            return Segment(maxOf(trueSegment.nextX, falseSegment.nextX), falseSegment.maxY, null)
        }

        private fun renderFor(start: CommandNode, x: Int, y: Int): Segment {
            putNode(x, y, start)
            val endId = start.pairedNodeId ?: return Segment(x + 2, y, start.id)
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
                else ->
                    // 未合流分岐など挿入先が曖昧なbody末尾では誤挿入より無反応が安全なため装飾扱いにする。
                    // 枝末端には既に黄色の追加アイコンがあるため追加導線は失われない。
                    putPath(endX - 1, y)
            }
            val end = graph.nodes[endId] ?: return Segment(endX, body.maxY, start.id)
            putNode(endX, y, end)

            val returnY = body.maxY + 2
            for (verticalY in y + 1..returnY) {
                putPath(endX, verticalY, MapCellKind.LOOP_RETURN_PATH)
                putPath(x, verticalY, MapCellKind.LOOP_RETURN_PATH)
            }
            fillHorizontal(x, endX, returnY, MapCellKind.LOOP_RETURN_PATH)
            return Segment(endX + 2, returnY, end.id)
        }

        private fun fillHorizontal(
            from: Int,
            to: Int,
            y: Int,
            kind: MapCellKind,
            sourceId: UUID? = null,
            edge: GraphEditor.Edge? = null,
            mergeConditionId: UUID? = null,
        ) {
            if (from > to) return
            for (x in from..to) putPath(x, y, kind, sourceId, edge, mergeConditionId)
        }

        private fun putNode(x: Int, y: Int, node: CommandNode) {
            val point = MapPoint(x, y)
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
        ) {
            val point = MapPoint(x, y)
            val existing = cells[point]
            check(existing?.kind != MapCellKind.NODE && existing?.kind != MapCellKind.ADD) {
                "生成経路が実行要素と衝突しています: point=$point existing=$existing"
            }
            val incomingTarget = edge?.let {
                InsertionTarget(sourceId, it, mergeConditionId, weak = weakInsertionTarget)
            }
            val resolvedKind = mergePathKind(existing?.kind, kind)
            val resolvedTarget = mergeInsertionTarget(point, existing?.insertionTarget, incomingTarget)
            cells[point] = MapCell(point, resolvedKind, insertionTarget = resolvedTarget)
        }

        private fun putAdd(
            x: Int,
            y: Int,
            sourceId: UUID?,
            edge: GraphEditor.Edge,
            mergeConditionId: UUID? = null,
        ) {
            val point = MapPoint(x, y)
            check(cells[point] == null) {
                "追加位置が既存要素と衝突しています: point=$point existing=${cells[point]}"
            }
            cells[point] = MapCell(
                point,
                MapCellKind.ADD,
                insertionTarget = InsertionTarget(sourceId, edge, mergeConditionId),
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

    private data class Segment(val nextX: Int, val maxY: Int, val tail: UUID?)
}
