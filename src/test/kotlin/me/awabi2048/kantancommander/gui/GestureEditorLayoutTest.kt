package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.math.abs

class GestureEditorLayoutTest {
    @Test
    fun `cell centers follow grid pitch with 90 percent icons`() {
        // マス中心: x = -0.99 + col*0.22、y = 0.28 - row*0.20
        assertEquals(-0.99, GestureEditorLayout.cellCenterX(0), 1.0e-9)
        assertEquals(-0.77, GestureEditorLayout.cellCenterX(1), 1.0e-9)
        assertEquals(0.99, GestureEditorLayout.cellCenterX(9), 1.0e-9)
        assertEquals(0.28, GestureEditorLayout.cellCenterY(0), 1.0e-9)
        assertEquals(0.08, GestureEditorLayout.cellCenterY(1), 1.0e-9)
        assertEquals(-0.12, GestureEditorLayout.cellCenterY(2), 1.0e-9)
        // アイコンはマスの90%
        assertEquals(0.22 * 0.9, GestureEditorLayout.ICON_W, 1.0e-9)
        assertEquals(0.22 * 0.9, GestureEditorLayout.ICON_H, 1.0e-9)
    }

    @Test
    fun `horizontal path spans between node centers with thin thickness`() {
        val seg = GestureEditorLayout.horizontalPath(
            y = 0.08,
            xFrom = -0.77,
            xTo = -0.55,
        )
        assertEquals(-0.66, seg.x, 1.0e-9)
        assertEquals(0.08, seg.y, 1.0e-9)
        assertEquals(0.22, seg.w, 1.0e-9)
        // 断面はピッチの2:3
        assertEquals(GestureEditorLayout.PATH_THICKNESS, seg.h, 1.0e-9)
    }

    @Test
    fun `vertical path spans between node centers`() {
        val seg = GestureEditorLayout.verticalPath(
            x = -0.33,
            yFrom = 0.28,
            yTo = -0.12,
        )
        assertEquals(-0.33, seg.x, 1.0e-9)
        assertEquals(0.08, seg.y, 1.0e-9)
        assertEquals(0.40, seg.h, 1.0e-9)
    }

    @Test
    fun `l path turns from top into right`() {
        val segments = GestureEditorLayout.lPathDownRight(
            cornerX = -0.33,
            cornerY = -0.12,
            topY = 0.28,
            rightX = 0.33,
        )
        assertEquals(2, segments.size)
        // 垂直部分: 上(IF)からcornerまで
        val vertical = segments.first { it.w < it.h }
        assertEquals(-0.33, vertical.x, 1.0e-9)
        assertTrue(abs(vertical.h - 0.40) < 1.0e-9)
        // 水平部分: cornerから右(分岐先)まで
        val horizontal = segments.first { it.w > it.h }
        assertEquals(-0.12, horizontal.y, 1.0e-9)
        assertEquals(0.66, horizontal.w, 1.0e-9)
    }

    @Test
    fun `first add point picks the most advanced branch endpoint`() {
        val cells = mapOf(
            MapPoint(4, 2) to MapCell(MapPoint(4, 2), MapCellKind.ADD),
            MapPoint(10, 1) to MapCell(MapPoint(10, 1), MapCellKind.ADD),
            MapPoint(-1, 1) to MapCell(MapPoint(-1, 1), MapCellKind.ADD),
        )
        assertEquals(MapPoint(10, 1), GestureEditorLayout.findFirstAddPoint(cells))
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
        assertEquals(MapPoint(10, 1), GestureEditorLayout.clampOrigin(MapPoint(99, 99), layout))
        assertEquals(MapPoint(3, 1), GestureEditorLayout.clampOrigin(MapPoint(3, 1), layout))
    }

    @Test
    fun `reveal origin includes the most advanced add point`() {
        val layout = GraphLayout(width = 20, height = 8, cells = emptyMap(), nodePoints = emptyMap())
        assertEquals(
            MapPoint(10, 4),
            GestureEditorLayout.revealOrigin(MapPoint(0, 0), MapPoint(19, 7), layout, 10, 4),
        )
        assertEquals(
            MapPoint(3, 1),
            GestureEditorLayout.revealOrigin(MapPoint(3, 1), MapPoint(7, 3), layout, 10, 4),
        )
    }

    @Test
    fun `zoom recalculates logical viewport dimensions around the default range`() {
        assertEquals(10, GestureEditorLayout.viewportColumns(0.75))
        assertEquals(4, GestureEditorLayout.viewportRows(0.75))
        assertEquals(20, GestureEditorLayout.viewportColumns(0.375))
        assertEquals(8, GestureEditorLayout.viewportRows(0.375))
        assertEquals(30, GestureEditorLayout.viewportColumns(0.25))
        assertEquals(12, GestureEditorLayout.viewportRows(0.25))
        assertEquals(-5.0, GestureEditorLayout.viewportOffset(10, 20), 1.0e-9)
    }

    @Test
    fun `gesture editor starts and resets at maximum zoom`() {
        assertEquals(GestureEditorLayout.MAX_ZOOM_LEVEL, GestureEditorLayout.INITIAL_ZOOM_LEVEL)
        assertEquals(
            GestureEditorLayout.INITIAL_ZOOM_LEVEL,
            GestureEditorState(UUID.randomUUID(), null).zoomLevel,
        )
        assertEquals(0, GestureEditorLayout.MAX_ZOOM_LEVEL)
        assertEquals(0.045, GestureEditorLayout.FRAME_WIDTH, 1.0e-9)
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
