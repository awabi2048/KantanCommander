package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

data class MapPoint(val x: Int, val y: Int)

enum class MapCellKind {
    NODE,
    PATH,
    BRANCH_PATH,
    LOOP_RETURN_PATH,
}

data class MapCell(
    val point: MapPoint,
    val kind: MapCellKind,
    val nodeId: UUID? = null,
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
        graph.entryNodeId?.let { builder.renderSequence(it, null, 1, 1) }
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
                    currentId = graph.nodes[node.pairedNodeId]?.next
                    if (currentId != null) putPath(cursorX - 1, y)
                    continue
                }
                if (node.type == CommandType.FOR_START && node.pairedNodeId != null) {
                    val loop = renderFor(node, cursorX, y)
                    cursorX = loop.nextX
                    maximumY = maxOf(maximumY, loop.maxY)
                    currentId = graph.nodes[node.pairedNodeId]?.next
                    if (currentId != null) putPath(cursorX - 1, y)
                    continue
                }
                putNode(cursorX, y, node)
                maximumY = maxOf(maximumY, y)
                val next = node.next
                if (next == null || next == stop) return Segment(cursorX + 2, maximumY, node.id)
                putPath(cursorX + 1, y)
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
                putPath(x + 1, y, MapCellKind.BRANCH_PATH)
                renderSequence(trueStart, mergeId, x + 2, y)
            } else Segment(x + 2, y, null)

            val falseY = trueSegment.maxY + 2
            putPath(x + 1, y, MapCellKind.BRANCH_PATH)
            for (verticalY in y + 1..falseY) putPath(x + 1, verticalY, MapCellKind.BRANCH_PATH)
            val falseStart = condition.falseNext
            val falseSegment = if (falseStart != null && falseStart != mergeId) {
                renderSequence(falseStart, mergeId, x + 2, falseY)
            } else Segment(x + 2, falseY, null)

            val mergeX = maxOf(trueSegment.nextX, falseSegment.nextX)
            fillHorizontal(trueSegment.nextX - 1, mergeX - 1, y, MapCellKind.BRANCH_PATH)
            fillHorizontal(falseSegment.nextX - 1, mergeX - 1, falseY, MapCellKind.BRANCH_PATH)
            for (verticalY in y..falseY) putPath(mergeX - 1, verticalY, MapCellKind.BRANCH_PATH)

            val merge = graph.nodes[mergeId] ?: return Segment(mergeX, falseSegment.maxY, condition.id)
            putNode(mergeX, y, merge)
            return Segment(mergeX + 2, maxOf(falseSegment.maxY, falseY), merge.id)
        }

        private fun renderFor(start: CommandNode, x: Int, y: Int): Segment {
            putNode(x, y, start)
            val endId = start.pairedNodeId ?: return Segment(x + 2, y, start.id)
            putPath(x + 1, y)
            val bodyStart = start.trueNext
            val body = if (bodyStart != null && bodyStart != endId) {
                renderSequence(bodyStart, endId, x + 2, y)
            } else {
                Segment(x + 2, y, null)
            }
            val endX = body.nextX
            fillHorizontal(body.nextX - 1, endX - 1, y, MapCellKind.PATH)
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

        private fun fillHorizontal(from: Int, to: Int, y: Int, kind: MapCellKind) {
            if (from > to) return
            for (x in from..to) putPath(x, y, kind)
        }

        private fun putNode(x: Int, y: Int, node: CommandNode) {
            val point = MapPoint(x, y)
            cells[point] = MapCell(point, MapCellKind.NODE, node.id)
            nodePoints[node.id] = point
        }

        private fun putPath(x: Int, y: Int, kind: MapCellKind = MapCellKind.PATH) {
            val point = MapPoint(x, y)
            if (cells[point]?.kind != MapCellKind.NODE) cells[point] = MapCell(point, kind)
        }
    }

    private data class Segment(val nextX: Int, val maxY: Int, val tail: UUID?)
}
