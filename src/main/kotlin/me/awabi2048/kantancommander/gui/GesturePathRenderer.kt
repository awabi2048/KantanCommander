package me.awabi2048.kantancommander.gui

/**
 * ビューポート用の経路を、論理セルの接続グラフから再構成します。
 *
 * 旧実装は同一行の隣接セルを一括結合していたため、ノードをまたぐ複数接続が
 * 1本の長い帯へ融合し、ビューポート原点を1セル移動しただけで長さや端点が変化
 * していました。ここでは NODE/ADD と分岐・曲がり角を経路端点としてトレースし、
 * 接続ごとに独立した3枚の帯へ分割します。呼び出し側から渡される境界継続は
 * 仮想端点としてだけ扱い、画面外のアイコンや未確定の経路を描画しません。
 */
internal object GesturePathRenderer {
    private val pathKinds = PATH_CELL_KINDS
    private val connectableKinds = CONNECTABLE_CELL_KINDS

    private enum class Axis { HORIZONTAL, VERTICAL }

    /** 経路を投影する論理ビューポートの切り取り矩形（ズーム前の座標）。 */
    data class ClipBounds(
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double,
    ) {
        init {
            require(minX < maxX) { "path clip width must be positive" }
            require(minY < maxY) { "path clip height must be positive" }
        }
    }

    private data class GridEdge(val first: MapPoint, val second: MapPoint)

    private fun edge(first: MapPoint, second: MapPoint): GridEdge =
        if (first.x < second.x || (first.x == second.x && first.y <= second.y)) {
            GridEdge(first, second)
        } else {
            GridEdge(second, first)
        }

    /**
     * 経路セル集合を画面座標上の帯へ変換します。
     * xCenter/yCenter はズーム前の論理グリッド中心を返す関数です。
     */
    fun buildSegments(
        cells: Map<MapPoint, MapCell>,
        boundaryConnections: Set<ViewportBoundaryConnection> = emptySet(),
        xCenter: (Int) -> Double,
        yCenter: (Int) -> Double,
        thickness: Double,
        clipBounds: ClipBounds? = null,
        glowColorFor: ((MapPoint, MapCell) -> Int?)? = null,
    ): List<GestureEditorLayout.PathSegment> {
        if (cells.isEmpty()) return emptyList()

        /*
         * 境界継続は外側のアイコンを表示するためではなく、可視端点から経路を
         * 途切れさせないための仮想接続です。仮想セル同士は接続しないことで、
         * 画面外の構造を推測して別の帯を勝手に生成することを防ぎます。
         */
        val boundaryCells = boundaryConnections.associate { connection ->
            connection.outside to MapCell(connection.outside, connection.outsideKind)
        }
        val allCells = cells + boundaryCells
        val boundaryEdges = boundaryConnections.mapTo(hashSetOf()) { connection ->
            edge(connection.visible, connection.outside)
        }
        val points = allCells.keys.sortedWith(compareBy<MapPoint>({ it.y }, { it.x }))
        val adjacency = points.associateWith { point ->
            ORTHOGONAL_DIRECTIONS.map { direction -> MapPoint(point.x + direction.x, point.y + direction.y) }
                .filter { neighbor ->
                    val neighborCell = allCells[neighbor] ?: return@filter false
                    if (point in cells && neighbor in cells) {
                        neighborCell.kind in connectableKinds
                    } else {
                        // 仮想セルは、明示された境界接続の相手とだけ接続します。
                        edge(point, neighbor) in boundaryEdges && neighborCell.kind in connectableKinds
                    }
                }
        }
        val degree = adjacency.mapValues { (_, neighbors) -> neighbors.size }
        fun isTerminal(point: MapPoint): Boolean =
            allCells[point]?.kind in setOf(MapCellKind.NODE, MapCellKind.ADD) || degree[point] != 2

        val visitedEdges = mutableSetOf<GridEdge>()
        val polylines = mutableListOf<List<MapPoint>>()

        fun trace(start: MapPoint, first: MapPoint) {
            val line = mutableListOf(start)
            var previous = start
            var current = first
            while (true) {
                line += current
                visitedEdges += edge(previous, current)
                if (isTerminal(current) || current == start) break
                val next = adjacency[current].orEmpty().firstOrNull { candidate ->
                    candidate != previous && edge(current, candidate) !in visitedEdges
                } ?: break
                previous = current
                current = next
            }
            if (line.size > 1 && line.last() == start) line.removeAt(line.lastIndex)
            if (line.size > 1) polylines += line
        }

        // 端点・分岐点から開始し、各無向辺を一度だけトレースします。
        points.filter(::isTerminal).forEach { start ->
            adjacency[start].orEmpty().forEach { next ->
                if (edge(start, next) !in visitedEdges) trace(start, next)
            }
        }
        // 理論上は存在しない完全な輪にも対応し、未訪問辺を取りこぼしません。
        points.forEach { start ->
            adjacency[start].orEmpty().forEach { next ->
                if (edge(start, next) !in visitedEdges) trace(start, next)
            }
        }

        fun axis(first: MapPoint, second: MapPoint): Axis? = when {
            first.y == second.y && first.x != second.x -> Axis.HORIZONTAL
            first.x == second.x && first.y != second.y -> Axis.VERTICAL
            else -> null
        }

        fun isJunction(point: MapPoint, segmentAxis: Axis): Boolean {
            if (allCells[point]?.kind !in pathKinds) return false
            return when (segmentAxis) {
                Axis.HORIZONTAL -> adjacency[point].orEmpty().any { it.y != point.y }
                Axis.VERTICAL -> adjacency[point].orEmpty().any { it.x != point.x }
            }
        }

        /**
         * ノード／追加ポイントへ入るポートは、角側の経路をトリミングしません。
         * 角とノードの中心間をそのまま3分割することで、アイコンの背面へ潜る
         * 接続実体を必ず残します。経路同士の角だけは従来どおり半幅を削り、
         * 角同士の重複を防ぎます。ノード／追加ポイント側の重なりは、アイコンの
         * 背面へ経路を潜り込ませるために意図的に許可します。
         */
        fun shouldTrimAt(point: MapPoint, other: MapPoint, segmentAxis: Axis): Boolean =
            isJunction(point, segmentAxis) &&
                allCells[other]?.kind !in setOf(MapCellKind.NODE, MapCellKind.ADD)

        val segments = mutableListOf<GestureEditorLayout.PathSegment>()

        // 経路セグメントの種別を判定します。戻り経路（LOOP_RETURN_PATH）は空色で描画するため、
        // 通常経路（PATH/BRANCH_PATH）と区別します。InventoryGUIの MapCellMaterialPolicy
        //（LOOP_RETURN_PATH → LIGHT_BLUE_STAINED_GLASS_PANE）に準拠します。
        fun segmentKind(first: MapPoint, second: MapPoint): MapCellKind {
            // 直線区間を構成する両端と、その間に含まれる中間点のいずれかが
            // LOOP_RETURN_PATH であれば、区間全体を戻り経路として扱います。
            val pointsInRun = mutableListOf<MapPoint>()
            if (first.x == second.x) {
                val minY = minOf(first.y, second.y)
                val maxY = maxOf(first.y, second.y)
                for (y in minY..maxY) pointsInRun += MapPoint(first.x, y)
            } else {
                val minX = minOf(first.x, second.x)
                val maxX = maxOf(first.x, second.x)
                for (x in minX..maxX) pointsInRun += MapPoint(x, first.y)
            }
            return if (pointsInRun.any { allCells[it]?.kind == MapCellKind.LOOP_RETURN_PATH }) {
                MapCellKind.LOOP_RETURN_PATH
            } else {
                MapCellKind.PATH
            }
        }

        fun emitStraight(first: MapPoint, second: MapPoint, segmentAxis: Axis) {
            val firstCoordinate = if (segmentAxis == Axis.HORIZONTAL) xCenter(first.x) else yCenter(first.y)
            val secondCoordinate = if (segmentAxis == Axis.HORIZONTAL) xCenter(second.x) else yCenter(second.y)
            val increasing = firstCoordinate <= secondCoordinate
            var low = minOf(firstCoordinate, secondCoordinate)
            var high = maxOf(firstCoordinate, secondCoordinate)
            val trim = thickness / 2.0
            if (shouldTrimAt(first, second, segmentAxis)) {
                if (increasing) low += trim else high -= trim
            }
            if (shouldTrimAt(second, first, segmentAxis)) {
                if (increasing) high -= trim else low += trim
            }
            val length = (high - low).coerceAtLeast(0.0)
            if (length <= 1.0e-6) return
            val third = length / 3.0
            val glowColors = if (glowColorFor == null) {
                emptyList()
            } else {
                val minCoordinate = if (segmentAxis == Axis.HORIZONTAL) minOf(first.x, second.x) else minOf(first.y, second.y)
                val maxCoordinate = if (segmentAxis == Axis.HORIZONTAL) maxOf(first.x, second.x) else maxOf(first.y, second.y)
                (minCoordinate..maxCoordinate).mapNotNull { coordinate ->
                    val point = if (segmentAxis == Axis.HORIZONTAL) {
                        MapPoint(coordinate, first.y)
                    } else {
                        MapPoint(first.x, coordinate)
                    }
                    allCells[point]?.let { cell -> glowColorFor(point, cell) }
                }
            }
            val glowColor = when {
                glowColors.any { it == GLOW_CYAN } -> GLOW_CYAN
                glowColors.any { it == GLOW_GREEN } -> GLOW_GREEN
                else -> glowColors.firstOrNull()
            }
            repeat(3) { index ->
                val center = low + third * (index + 0.5)
                val kind = segmentKind(first, second)
                segments += if (segmentAxis == Axis.HORIZONTAL) {
                    GestureEditorLayout.PathSegment(center, yCenter(first.y), third, thickness, kind, glowColor)
                } else {
                    GestureEditorLayout.PathSegment(xCenter(first.x), center, thickness, third, kind, glowColor)
                }
            }
        }

        polylines.forEach { line ->
            if (line.size < 2) return@forEach
            var runStart = line.first()
            var previous = line[1]
            var runAxis = axis(runStart, previous) ?: return@forEach
            for (index in 2 until line.size) {
                val next = line[index]
                val nextAxis = axis(previous, next) ?: return@forEach
                if (nextAxis != runAxis) {
                    emitStraight(runStart, previous, runAxis)
                    runStart = previous
                    runAxis = nextAxis
                }
                previous = next
            }
            emitStraight(runStart, previous, runAxis)
        }

        // 曲がり角・T字接続は正方形1枚で埋め、水平帯と垂直帯の重複を防ぎます。
        points.filter { point ->
            if (allCells[point]?.kind !in pathKinds) return@filter false
            val neighbors = adjacency[point].orEmpty()
            neighbors.any { it.y == point.y } && neighbors.any { it.x == point.x }
        }.forEach { point ->
            val kind = allCells[point]?.kind ?: MapCellKind.PATH
            segments += GestureEditorLayout.PathSegment(
                xCenter(point.x),
                yCenter(point.y),
                thickness,
                thickness,
                kind,
                glowColor = glowColorFor?.invoke(point, allCells.getValue(point)),
            )
        }
        val uniqueSegments = segments.distinct()
        if (clipBounds == null) return uniqueSegments

        // 境界継続の仮想端点は1セル外側に置かれますが、実際に表示するのは
        // 可視論理範囲までです。これで経路だけがナビゲーション領域へ侵入しません。
        return uniqueSegments.mapNotNull { segment ->
            val left = maxOf(segment.x - segment.w / 2.0, clipBounds.minX)
            val right = minOf(segment.x + segment.w / 2.0, clipBounds.maxX)
            val bottom = maxOf(segment.y - segment.h / 2.0, clipBounds.minY)
            val top = minOf(segment.y + segment.h / 2.0, clipBounds.maxY)
            if (right - left <= 1.0e-6 || top - bottom <= 1.0e-6) return@mapNotNull null
            GestureEditorLayout.PathSegment(
                x = (left + right) / 2.0,
                y = (bottom + top) / 2.0,
                w = right - left,
                h = top - bottom,
                kind = segment.kind,
                glowColor = segment.glowColor,
            )
        }.distinct()
    }

    // テスト経路の色はエディターと共有する固定ARGB値です。
    private const val GLOW_CYAN: Int = 0xFF55FFFF.toInt()
    private const val GLOW_GREEN: Int = 0xFF55FF55.toInt()
}
