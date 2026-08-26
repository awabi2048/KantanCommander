package me.awabi2048.kantancommander.gui

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
    const val VIEWPORT_ROWS: Int = 3

    /** マス間のピッチ（隣接マス中心間距離） */
    const val PITCH_X: Double = 0.22
    const val PITCH_Y: Double = 0.20

    /** アイコンはマスの90%サイズ（スロットに余白を生じる） */
    const val ICON_SCALE_REFERENCE: Double = 0.22
    const val ICON_SCALE: Double = 0.20
    const val ICON_W: Double = ICON_SCALE_REFERENCE * 0.9
    const val ICON_H: Double = ICON_SCALE_REFERENCE * 0.9

    /** 経路の断面（短辺）。進行方向に細長い2:3 */
    const val PATH_THICKNESS: Double = PITCH_X * 2.0 / 3.0

    /** グリッドの第1列/第1行の中心座標（パネル内に収まるよう計算） */
    const val FIRST_COL_X: Double = -0.99
    const val FIRST_ROW_Y: Double = 0.28

    /** 指定した列インデックスに対応する画面中心x（originによらず固定） */
    fun cellCenterX(col: Int): Double = FIRST_COL_X + col * PITCH_X
    /** 指定した行インデックスに対応する画面中心y（originによらず固定） */
    fun cellCenterY(row: Int): Double = FIRST_ROW_Y - row * PITCH_Y

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

    /** 最も先頭にあるadd-pointのグリッド座標を返します（エントリー用） */
    fun findFirstAddPoint(cells: Map<MapPoint, MapCell>): MapPoint? =
        cells.filter { it.value.kind == MapCellKind.ADD }
            .minWithOrNull(compareBy({ it.key.x }, { it.key.y }))?.key

    /** ビューポート原点の範囲をclampします（canMoveと同等） */
    fun clampOrigin(origin: MapPoint, layout: GraphLayout): MapPoint {
        val maxX = (layout.width - VIEWPORT_COLS).coerceAtLeast(0)
        val maxY = (layout.height - VIEWPORT_ROWS).coerceAtLeast(0)
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
