package me.awabi2048.kantancommander.gui

import kotlin.math.roundToInt

/**
 * ジェスチャーエディターのビューポート・経路・ナビゲーション配置を純関数で計算します。
 * 既存インベントリGUIのグリッド配置前提を排し、ブロック単位の自由座標で表現します。
 *
 * 座標系: 画面中央原点、x右正/y上正、ブロック単位。
 * 配置はモックアップを参考にしつつ、パネル内寸・フレーム幅から全座標を再計算しています。
 *
 * 上部パネル: 2.90×1.0607, 内寸 2.81×0.9707, 半内 1.405×0.485
 * 下部パネル: 2.1213×1.0607, 内寸 2.0313×0.9707, 半内 1.016×0.485
 */
object GestureEditorLayout {
    /** ビューポートのマス数 */
    const val VIEWPORT_COLS: Int = 10
    const val VIEWPORT_ROWS: Int = 4

    /** 基準倍率。基準倍率では論理セルを10×4枚表示します。 */
    const val DEFAULT_ZOOM: Double = 0.75
    /** 表示領域の余白とGestureGuiPanelのフレーム幅を一致させます。 */
    const val FRAME_WIDTH: Double = 0.045

    /** ズーム段階の範囲。初期値は最大倍率（75%）です。 */
    const val MIN_ZOOM_LEVEL: Int = -2
    const val MAX_ZOOM_LEVEL: Int = 0
    const val INITIAL_ZOOM_LEVEL: Int = MAX_ZOOM_LEVEL

    /** マス間のピッチ（隣接マス中心間距離） */
    const val PITCH_X: Double = 0.22
    const val PITCH_Y: Double = 0.20

    /** アイコンはマスの90%サイズ（スロットに余白を生じる） */
    const val ICON_SCALE_REFERENCE: Double = 0.22
    const val ICON_SCALE: Double = 0.20
    const val ICON_W: Double = ICON_SCALE_REFERENCE * 0.9
    const val ICON_H: Double = ICON_SCALE_REFERENCE * 0.9
    /** コマンド／新規追加の共通表示レベル。背景の上にアイコン本体を置きます。 */
    const val ICON_BACKGROUND_LAYER: Int = 2
    const val ICON_LAYER: Int = 3

    /** 経路の断面（短辺）。進行方向に細長い2:3 */
    // 水平・垂直の双方で同じ正方形断面に見えるよう、短いピッチを基準にします。
    val PATH_THICKNESS: Double = minOf(PITCH_X, PITCH_Y) * 2.0 / 3.0

    /** グリッドの第1列/第1行の中心座標（パネル内に収まるよう計算） */
    const val FIRST_COL_X: Double = -0.99
    const val FIRST_ROW_Y: Double = 0.28

    /** 指定した列インデックスに対応する画面中心x（originによらず固定） */
    fun cellCenterX(col: Int): Double = cellCenterX(col.toDouble())
    fun cellCenterX(col: Double): Double = FIRST_COL_X + col * PITCH_X
    /** 指定した行インデックスに対応する画面中心y（originによらず固定） */
    fun cellCenterY(row: Int): Double = cellCenterY(row.toDouble())
    fun cellCenterY(row: Double): Double = FIRST_ROW_Y - row * PITCH_Y

    /** 2点を結ぶ軸整列な経路セグメントを生成します（水平または垂直） */
    data class PathSegment(val x: Double, val y: Double, val w: Double, val h: Double)

    fun horizontalPath(y: Double, xFrom: Double, xTo: Double): PathSegment {
        val left = minOf(xFrom, xTo)
        val right = maxOf(xFrom, xTo)
        return PathSegment((left + right) / 2.0, y, right - left, PATH_THICKNESS)
    }

    fun verticalPath(x: Double, yFrom: Double, yTo: Double): PathSegment {
        val top = maxOf(yFrom, yTo)
        val bottom = minOf(yFrom, yTo)
        return PathSegment(x, (top + bottom) / 2.0, PATH_THICKNESS, top - bottom)
    }

    /** L字経路: (cornerX, cornerY)で曲がり、上から入って右へ出る（┘型） */
    fun lPathDownRight(cornerX: Double, cornerY: Double, topY: Double, rightX: Double): List<PathSegment> =
        listOf(verticalPath(cornerX, cornerY, topY), horizontalPath(cornerY, cornerX, rightX))

    /**
     * 枝が最も進んだ位置にあるadd-pointのグリッド座標を返します。
     *
     * 「先頭へ戻る」はエントリー側の最小座標ではなく、分岐を含むグラフで
     * 最も先まで進んだ枝末端を基準にします。同じ列なら画面下側の枝を優先し、
     * ネストした分岐でも最後に到達する追加位置を安定して選びます。
     */
    fun findFirstAddPoint(cells: Map<MapPoint, MapCell>): MapPoint? =
        cells.filter { it.value.kind == MapCellKind.ADD }
            .maxWithOrNull(compareBy({ it.key.x }, { it.key.y }))?.key

    /** targetセルが表示範囲へ入るよう、現在原点を必要な場合だけ移動します。 */
    fun revealOrigin(
        current: MapPoint,
        target: MapPoint,
        layout: GraphLayout,
        viewportCols: Int,
        viewportRows: Int,
    ): MapPoint {
        val maxX = (layout.width - viewportCols).coerceAtLeast(0)
        val maxY = (layout.height - viewportRows).coerceAtLeast(0)
        fun reveal(currentOrigin: Int, targetPoint: Int, visible: Int, maximum: Int): Int =
            when {
                targetPoint < currentOrigin -> targetPoint
                targetPoint > currentOrigin + visible - 1 -> targetPoint - visible + 1
                else -> currentOrigin
            }.coerceIn(0, maximum)
        return MapPoint(
            reveal(current.x, target.x, viewportCols, maxX),
            reveal(current.y, target.y, viewportRows, maxY),
        )
    }

    /** ビューポート原点の範囲をclampします（canMoveと同等） */
    fun clampOrigin(origin: MapPoint, layout: GraphLayout): MapPoint {
        return clampOrigin(origin, layout, VIEWPORT_COLS, VIEWPORT_ROWS)
    }

    fun clampOrigin(origin: MapPoint, layout: GraphLayout, viewportCols: Int, viewportRows: Int): MapPoint {
        val maxX = (layout.width - viewportCols).coerceAtLeast(0)
        val maxY = (layout.height - viewportRows).coerceAtLeast(0)
        return MapPoint(origin.x.coerceIn(0, maxX), origin.y.coerceIn(0, maxY))
    }

    // ---- 十字ナビゲーション（右下、グリッド外） ----
    const val NAV_CENTER_X: Double = 1.05
    const val NAV_CENTER_Y: Double = -0.32
    const val NAV_SIZE: Double = 0.09
    const val NAV_PITCH: Double = 0.11

    /** 先頭に戻る（⌂）: 十字の下・左に隣接 */
    val BACK_X: Double = NAV_CENTER_X - NAV_PITCH
    val BACK_Y: Double = NAV_CENTER_Y - NAV_PITCH

    /** ナビゲーション右側に縦積みするズーム操作領域 */
    const val ZOOM_X: Double = 1.30
    const val ZOOM_TOP_Y: Double = NAV_CENTER_Y + 0.055
    const val ZOOM_SIZE: Double = NAV_SIZE
    const val ZOOM_PITCH: Double = NAV_SIZE + 0.015

    /** 画面右上の閉じるボタン（ズーム／ナビゲーションと同じ正方形寸法）。 */
    const val CLOSE_X: Double = ZOOM_X
    const val CLOSE_Y: Double = 0.40
    const val CLOSE_SIZE: Double = NAV_SIZE

    /** ズーム倍率に応じた論理表示セル数です。座標を拡大縮小するだけにせず、表示範囲も再計算します。 */
    fun viewportColumns(zoomScale: Double): Int =
        (VIEWPORT_COLS * DEFAULT_ZOOM / zoomScale).roundToInt().coerceAtLeast(1)

    fun viewportRows(zoomScale: Double): Int =
        (VIEWPORT_ROWS * DEFAULT_ZOOM / zoomScale).roundToInt().coerceAtLeast(1)

    /** 基準グリッドを画面中央へ保つための小数セルオフセットです。 */
    fun viewportOffset(base: Int, visible: Int): Double = (base - visible) / 2.0

    /** 上部画面パネル寸法（案A: ワイド） */
    const val UPPER_W: Double = 2.90
    const val UPPER_H: Double = 1.0606601717798212

    /** 下部画面パネル寸法（標準） */
    const val LOWER_W: Double = 2.1213203435596424
    const val LOWER_H: Double = 1.0606601717798212

    // ---- 下部1:3分割 ----
    const val TAB_CENTER_X: Double = -0.75
    const val TAB_WIDTH: Double = 0.46
    const val TAB_HEIGHT: Double = 0.14
    const val TAB_PITCH: Double = 0.16
    const val DETAIL_CENTER_X: Double = 0.45
}
