package me.awabi2048.kantancommander.gui

/**
 * Custom描画向けのグラフ座標写像です。
 *
 * 旧 `ViewportMetrics` と同一式で、基準矩形だけを引数で受けます。
 * 宣言レイアウト下では内容域を渡し、旧来経路では上部グラフ領域を渡すことで
 * 両経路の座標を一致させます。Bukkitへ依存しない純粋計算です。
 */
data class GraphViewportFrame(
    val zoomScale: Double,
    val columns: Int,
    val rows: Int,
    val firstX: Double,
    val firstY: Double,
    val pitchX: Double,
    val pitchY: Double,
    val iconSize: Double,
    val iconScale: Double,
    val pathThickness: Double,
    /** 旧 graphMin/Maxに相当する基準矩形です。 */
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
) {
    fun x(local: Int): Double = firstX + local * pitchX
    fun x(local: Double): Double = firstX + local * pitchX
    fun y(local: Int): Double = firstY - local * pitchY
    fun y(local: Double): Double = firstY - local * pitchY
}

object GraphViewportMapper {
    /**
     * 表示面の実寸を先に決め、そこへ可視論理セルを等分します。
     * 式は旧来の矩形基準配置と同一です。
     */
    fun frame(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        columns: Int,
        rows: Int,
        scale: Double,
    ): GraphViewportFrame {
        require(scale.isFinite() && scale > 0.0) { "viewport zoom scale must be positive and finite" }
        require(columns >= 1 && rows >= 1) { "viewport grid must have at least one column and row" }
        require(listOf(minX, maxX, minY, maxY).all(Double::isFinite)) {
            "viewport bounds must be finite"
        }
        require(minX < maxX && minY < maxY) { "viewport bounds must be positive" }
        val screenPitchX = (maxX - minX) / columns
        val screenPitchY = (maxY - minY) / rows
        val screenIconSize = minOf(screenPitchX, screenPitchY) * 0.9
        val screenPathThickness = minOf(screenPitchX, screenPitchY) * 2.0 / 3.0
        val iconScaleRatio = GestureEditorLayout.ICON_SCALE / GestureEditorLayout.ICON_W
        return GraphViewportFrame(
            zoomScale = scale,
            columns = columns,
            rows = rows,
            firstX = (minX + screenPitchX / 2.0) / scale,
            firstY = (maxY - screenPitchY / 2.0) / scale,
            pitchX = screenPitchX / scale,
            pitchY = screenPitchY / scale,
            iconSize = screenIconSize / scale,
            iconScale = screenIconSize * iconScaleRatio / scale,
            pathThickness = screenPathThickness / scale,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
        )
    }

    /** 旧来の上部グラフ領域に対する写像です。旧経路の委譲に用います。 */
    fun legacyFrame(scale: Double): GraphViewportFrame = frame(
        minX = GestureEditorLayout.UPPER_GRAPH_MIN_X,
        maxX = GestureEditorLayout.UPPER_GRAPH_MAX_X,
        minY = GestureEditorLayout.UPPER_GRAPH_MIN_Y,
        maxY = GestureEditorLayout.UPPER_GRAPH_MAX_Y,
        columns = GestureEditorLayout.viewportColumns(scale),
        rows = GestureEditorLayout.viewportRows(scale),
        scale = scale,
    )
}
