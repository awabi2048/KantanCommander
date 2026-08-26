package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import java.util.UUID

/**
 * ジェスチャーエディターのビューポート・経路・ナビゲーション配置を純関数で計算します。
 * 既存インベントリGUIのグリッド配置前提を排し、ブロック単位の自由座標で表現します。
 *
 * 座標系: 画面中央原点、x右正・y上正、ブロック単位。
 */
object GestureEditorLayout {
    /** マス（便宜上のスロット領域）のサイズ */
    const val CELL_W: Double = 0.19
    const val CELL_H: Double = 0.19
    /** マス間のピッチ（隣接マス中心間距離） */
    const val PITCH_X: Double = 0.22
    const val PITCH_Y: Double = 0.24
    /** ビューポートのマス数 */
    const val VIEWPORT_COLS: Int = 10
    const val VIEWPORT_ROWS: Int = 3

    /** アイコンはマスの90%サイズ（スロットに余白を生じる） */
    const val ICON_SCALE: Double = 0.9
    const val ICON_W: Double = CELL_W * ICON_SCALE
    const val ICON_H: Double = CELL_H * ICON_SCALE

    /** 経路の断面（短辺） */
    const val PATH_THICKNESS: Double = CELL_W * 2.0 / 3.0

    /** 十字ナビ（75%サイズ・右下） */
    const val NAV_SIZE: Double = 0.16 * 0.75
    const val NAV_PITCH: Double = 0.20 * 0.75
    const val NAV_CENTER_X: Double = 1.17
    const val NAV_CENTER_Y: Double = -0.25

    /** back-to-start（十字の下・左に隣接） */
    const val BACK_X: Double = NAV_CENTER_X - NAV_PITCH
    const val BACK_Y: Double = NAV_CENTER_Y - NAV_PITCH

    /** 上部画面パネル幅（案A: ワイド） */
    const val UPPER_W: Double = 2.90
    const val UPPER_H: Double = 1.0606601717798212

    /** 列の左端xを求める（10列を中央配置せず左寄せ: 最初の列を-1.35に） */
    fun originColumnX(): Double = -(VIEWPORT_COLS - 1) * PITCH_X / 2.0

    /** 指定したグリッドgxに対応する画面中心x */
    fun cellCenterX(gx: Int): Double = originColumnX() + gx * PITCH_X
    /** 指定したグリッドgyに対応する画面中心y（gy増加＝画面下方向） */
    fun cellCenterY(gy: Int): Double = ((VIEWPORT_ROWS - 1) / 2) * PITCH_Y - gy * PITCH_Y

    /** グリッド座標→画面中央座標 */
    fun cellCenter(point: MapPoint): Pair<Double, Double> = cellCenterX(point.x) to cellCenterY(point.y)

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

    /** L字経路: (cornerX, cornerY)で曲がり、上から入って右へ出る（┌型反転＝┘型） */
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
}
