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

    @Test
    fun `boundary continuation renders a stub without rendering the outside icon`() {
        val visibleNode = node(0, 0)
        val visiblePath = path(1, 0)
        val cells = mapOf(visibleNode, visiblePath)
        val boundary = setOf(
            ViewportBoundaryConnection(
                visible = MapPoint(1, 0),
                outside = MapPoint(2, 0),
                outsideKind = MapCellKind.NODE,
            ),
        )

        val segments = GesturePathRenderer.buildSegments(
            cells,
            boundaryConnections = boundary,
            xCenter = { it.toDouble() },
            yCenter = { it.toDouble() },
            thickness = 0.1,
        )

        // node→画面外ノードという1接続を、3枚の帯へ分割します。
        assertEquals(3, segments.size)
        assertTrue(segments.all { it.w > it.h })
        // 画面外ノード自身のアイコンは入力集合へ追加されないため、
        // スタブの終端だけが x=2 まで到達します。
        assertTrue(segments.maxOf { it.x + it.w / 2.0 } <= 2.0 + 1.0e-9)
    }

    @Test
    fun `merge node receives a visible connector through its bottom port`() {
        val cells = mapOf(
            node(0, 0),
            path(1, 0),
            node(2, 0),
            path(2, 1),
            path(1, 1),
            path(1, 2),
        )

        val segments = GesturePathRenderer.buildSegments(
            cells,
            xCenter = { it.toDouble() },
            yCenter = { it.toDouble() },
            thickness = 0.1,
        )

        // 合流直下の経路からノード中心までは、独立した垂直接続として描画されます。
        val connector = segments.filter {
            it.x == 2.0 && it.h > it.w &&
                it.y + it.h / 2.0 > 0.0 &&
                it.y - it.h / 2.0 < 1.0
        }
        assertEquals(
            1.0,
            connector.sumOf {
                minOf(it.y + it.h / 2.0, 1.0) - maxOf(it.y - it.h / 2.0, 0.0)
            },
            1.0e-9,
        )
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
