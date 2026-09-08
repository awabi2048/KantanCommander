package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * グラフ座標写像のテストです。旧矩形基準配置との同一式を検証します。
 * サーバーなしで実行できます。
 */
class GraphViewportMapperTest {
    @Test
    fun `frame divides the given rect evenly`() {
        val frame = GraphViewportMapper.frame(
            minX = 0.0, maxX = 2.0, minY = 0.0, maxY = 1.0,
            columns = 4, rows = 2, scale = 1.0,
        )
        assertEquals(0.5, frame.pitchX, 1.0e-9)
        assertEquals(0.5, frame.pitchY, 1.0e-9)
        assertEquals(0.25, frame.firstX, 1.0e-9)
        assertEquals(0.75, frame.firstY, 1.0e-9)
        assertEquals(0.45, frame.iconSize, 1.0e-9)
        assertEquals(0.25, frame.x(0), 1.0e-9)
        assertEquals(1.75, frame.x(3), 1.0e-9)
        assertEquals(0.75, frame.y(0), 1.0e-9)
        assertEquals(0.25, frame.y(1), 1.0e-9)
        assertEquals(0.0, frame.minX, 1.0e-9)
        assertEquals(2.0, frame.maxX, 1.0e-9)
    }

    @Test
    fun `scale halves pitches and sizes`() {
        val base = GraphViewportMapper.frame(0.0, 2.0, 0.0, 1.0, 4, 2, 1.0)
        val zoomed = GraphViewportMapper.frame(0.0, 2.0, 0.0, 1.0, 4, 2, 2.0)
        assertEquals(base.pitchX / 2.0, zoomed.pitchX, 1.0e-9)
        assertEquals(base.pitchY / 2.0, zoomed.pitchY, 1.0e-9)
        assertEquals(base.iconSize / 2.0, zoomed.iconSize, 1.0e-9)
    }

    @Test
    fun `legacy frame matches upper graph bounds and zoom grid`() {
        listOf(0.5, 0.75, 1.0).forEach { scale ->
            val frame = GraphViewportMapper.legacyFrame(scale)
            assertEquals(GestureEditorLayout.UPPER_GRAPH_MIN_X, frame.minX, 1.0e-9)
            assertEquals(GestureEditorLayout.UPPER_GRAPH_MAX_X, frame.maxX, 1.0e-9)
            assertEquals(GestureEditorLayout.UPPER_GRAPH_MIN_Y, frame.minY, 1.0e-9)
            assertEquals(GestureEditorLayout.UPPER_GRAPH_MAX_Y, frame.maxY, 1.0e-9)
            assertEquals(GestureEditorLayout.viewportColumns(scale), frame.columns)
            assertEquals(GestureEditorLayout.viewportRows(scale), frame.rows)
        }
    }

    @Test
    fun `invalid inputs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GraphViewportMapper.frame(0.0, 1.0, 0.0, 1.0, 2, 2, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraphViewportMapper.frame(1.0, 0.0, 0.0, 1.0, 2, 2, 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraphViewportMapper.frame(0.0, 1.0, 0.0, 1.0, 0, 2, 1.0)
        }
    }
}
