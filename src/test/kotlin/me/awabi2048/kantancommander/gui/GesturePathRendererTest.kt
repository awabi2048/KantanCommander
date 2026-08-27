package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GesturePathRendererTest {
    @Test
    fun `each node connection is split independently`() {
        val cells = mapOf(
            node(0, 0),
            path(1, 0),
            node(2, 0),
            path(3, 0),
            node(4, 0),
        )

        val segments = GesturePathRenderer.buildSegments(
            cells,
            xCenter = { it.toDouble() },
            yCenter = { it.toDouble() },
            thickness = 0.1,
        )

        // node-path-node が2接続あるため、各接続3枚で合計6枚になります。
        assertEquals(6, segments.size)
        assertTrue(segments.all { it.w > it.h })
        assertTrue(segments.all {
            it.x + it.w / 2.0 <= 2.0 + 1.0e-9 || it.x - it.w / 2.0 >= 2.0 - 1.0e-9
        })
    }

    @Test
    fun `turns receive independent square corners without overlapping bands`() {
        val cells = mapOf(
            node(0, 0),
            path(1, 0),
            path(1, 1),
            add(2, 1),
        )

        val segments = GesturePathRenderer.buildSegments(
            cells,
            xCenter = { it.toDouble() },
            yCenter = { it.toDouble() },
            thickness = 0.1,
        )

        // 水平・垂直・水平の各3枚と、2つの曲がり角正方形です。
        assertEquals(11, segments.size)
        assertEquals(2, segments.count { it.w == 0.1 && it.h == 0.1 })
    }

    private fun node(x: Int, y: Int): Pair<MapPoint, MapCell> {
        val point = MapPoint(x, y)
        return point to MapCell(point, MapCellKind.NODE)
    }

    private fun path(x: Int, y: Int): Pair<MapPoint, MapCell> {
        val point = MapPoint(x, y)
        return point to MapCell(point, MapCellKind.PATH)
    }

    private fun add(x: Int, y: Int): Pair<MapPoint, MapCell> {
        val point = MapPoint(x, y)
        return point to MapCell(point, MapCellKind.ADD)
    }
}
