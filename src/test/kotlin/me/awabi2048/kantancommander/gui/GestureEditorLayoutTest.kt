package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class GestureEditorLayoutTest {
    @Test
    fun `cell centers follow grid pitch with 90 percent icons`() {
        // マス中心: x = -1.35 + gx*0.22、y = 0.34 - gy*0.24
        assertEquals(-1.35, GestureEditorLayout.cellCenterX(0), 1.0e-9)
        assertEquals(-1.13, GestureEditorLayout.cellCenterX(1), 1.0e-9)
        assertEquals(0.41, GestureEditorLayout.cellCenterX(8), 1.0e-9)
        assertEquals(0.34, GestureEditorLayout.cellCenterY(0), 1.0e-9)
        assertEquals(0.10, GestureEditorLayout.cellCenterY(1), 1.0e-9)
        assertEquals(-0.14, GestureEditorLayout.cellCenterY(2), 1.0e-9)
        // アイコンはマスの90%
        assertEquals(0.171, GestureEditorLayout.ICON_W, 1.0e-9)
        assertEquals(0.171, GestureEditorLayout.ICON_H, 1.0e-9)
    }

    @Test
    fun `horizontal path spans between node centers with thin thickness`() {
        val seg = GestureEditorLayout.horizontalPath(
            y = 0.10,
            xFrom = -1.13,
            xTo = -0.91,
        )
        assertEquals(-1.02, seg.x, 1.0e-9)
        assertEquals(0.10, seg.y, 1.0e-9)
        assertEquals(0.22, seg.w, 1.0e-9)
        // 断面はマスの2/3
        assertEquals(GestureEditorLayout.CELL_W * 2.0 / 3.0, seg.h, 1.0e-9)
    }

    @Test
    fun `vertical path spans between node centers`() {
        val seg = GestureEditorLayout.verticalPath(
            x = -0.03,
            yFrom = 0.34,
            yTo = -0.14,
        )
        assertEquals(-0.03, seg.x, 1.0e-9)
        assertEquals(0.10, seg.y, 1.0e-9)
        assertEquals(0.48, seg.h, 1.0e-9)
    }

    @Test
    fun `l path turns from top into right`() {
        val segments = GestureEditorLayout.lPathDownRight(
            cornerX = -0.03,
            cornerY = -0.14,
            topY = 0.34,
            rightX = 0.41,
        )
        assertEquals(2, segments.size)
        // 垂直部分: 上(IF)からcornerまで
        val vertical = segments.first { it.w < it.h }
        assertEquals(-0.03, vertical.x, 1.0e-9)
        assertTrue(abs(vertical.h - 0.48) < 1.0e-9)
        // 水平部分: cornerから右(分岐先)まで
        val horizontal = segments.first { it.w > it.h }
        assertEquals(-0.14, horizontal.y, 1.0e-9)
        assertEquals(0.44, horizontal.w, 1.0e-9)
    }

    @Test
    fun `first add point picks smallest coordinates`() {
        val cells = mapOf(
            MapPoint(4, 2) to MapCell(MapPoint(4, 2), MapCellKind.ADD),
            MapPoint(10, 1) to MapCell(MapPoint(10, 1), MapCellKind.ADD),
            MapPoint(-1, 1) to MapCell(MapPoint(-1, 1), MapCellKind.ADD),
        )
        assertEquals(MapPoint(-1, 1), GestureEditorLayout.findFirstAddPoint(cells))
    }

    @Test
    fun `first add point is null when absent`() {
        val cells = mapOf(
            MapPoint(1, 1) to MapCell(MapPoint(1, 1), MapCellKind.NODE),
        )
        assertNull(GestureEditorLayout.findFirstAddPoint(cells))
    }

    @Test
    fun `clamp origin keeps viewport inside layout`() {
        // 幅20・高さ5のマップでは、原点xは0..10、yは0..2に収まる
        val layout = GraphLayout(width = 20, height = 5, cells = emptyMap(), nodePoints = emptyMap())
        assertEquals(MapPoint(0, 0), GestureEditorLayout.clampOrigin(MapPoint(-5, -1), layout))
        assertEquals(MapPoint(10, 2), GestureEditorLayout.clampOrigin(MapPoint(99, 99), layout))
        assertEquals(MapPoint(3, 1), GestureEditorLayout.clampOrigin(MapPoint(3, 1), layout))
    }

    @Test
    fun `nav and back positions are adjacent bottom-left of the cross`() {
        // back-to-startは十字の下・左に隣接: x = 十字中心x - ピッチ、y = 十字中心y - ピッチ
        assertEquals(
            GestureEditorLayout.NAV_CENTER_X - GestureEditorLayout.NAV_PITCH,
            GestureEditorLayout.BACK_X,
            1.0e-9,
        )
        assertEquals(
            GestureEditorLayout.NAV_CENTER_Y - GestureEditorLayout.NAV_PITCH,
            GestureEditorLayout.BACK_Y,
            1.0e-9,
        )
    }
}