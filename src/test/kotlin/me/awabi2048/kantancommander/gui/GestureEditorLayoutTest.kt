package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `most advanced add point picks the furthest branch endpoint`() {
        val cells = mapOf(
            MapPoint(4, 2) to MapCell(MapPoint(4, 2), MapCellKind.ADD),
            MapPoint(10, 1) to MapCell(MapPoint(10, 1), MapCellKind.ADD),
            MapPoint(-1, 1) to MapCell(MapPoint(-1, 1), MapCellKind.ADD),
        )
        assertEquals(MapPoint(10, 1), GestureEditorLayout.findMostAdvancedAddPoint(cells))
    }

    @Test
    fun `most advanced add point is null when absent`() {
        val cells = mapOf(
            MapPoint(1, 1) to MapCell(MapPoint(1, 1), MapCellKind.NODE),
        )
        assertNull(GestureEditorLayout.findMostAdvancedAddPoint(cells))
    }

    @Test
    fun `clamp origin keeps viewport inside layout`() {
        // 幅20・高さ5のマップでは、原点xは0..10、yは0..0に収まる（ROWS 6 のため高さ方向は収まらない）
        val layout = GraphLayout(width = 20, height = 5, cells = emptyMap(), nodePoints = emptyMap())
        assertEquals(MapPoint(0, 0), GestureEditorLayout.clampOrigin(MapPoint(-5, -1), layout))
        assertEquals(MapPoint(10, 0), GestureEditorLayout.clampOrigin(MapPoint(99, 99), layout))
        assertEquals(MapPoint(3, 0), GestureEditorLayout.clampOrigin(MapPoint(3, 1), layout))
    }

    @Test
    fun `clamp origin with remembered size keeps origin after collapse`() {
        // 折りたたみで幅20→14へ縮んでも、展開時寸法を基準にすれば
        // 端付近の原点は引き戻されず、STARTが同じ位置に留まる。
        assertEquals(
            MapPoint(10, 0),
            GestureEditorLayout.clampOrigin(MapPoint(10, 0), 20, 5, 10, 6),
        )
        assertEquals(
            MapPoint(4, 0),
            GestureEditorLayout.clampOrigin(MapPoint(10, 0), 14, 5, 10, 6),
        )
        assertEquals(
            MapPoint(0, 0),
            GestureEditorLayout.clampOrigin(MapPoint(-5, -1), 20, 5, 10, 6),
        )
    }

    @Test
    fun `remembered layout size keeps the maximum per axis`() {
        // 折り畳み（FOR・分岐のいずれも）で縮んでも記憶を維持し、原点を引き戻さない。
        // 幅20・高さ8の展開配置から幅14・高さ5へ縮んだ場合。
        assertEquals(
            MapPoint(20, 8),
            GestureEditorLayout.rememberLayoutSize(20, 8, 14, 5),
        )
        // 展開・追加で広がった場合は軸ごとに追従する。
        assertEquals(
            MapPoint(22, 8),
            GestureEditorLayout.rememberLayoutSize(20, 8, 22, 6),
        )
        // 初回（記憶なし）は現寸法をそのまま覚える。
        assertEquals(
            MapPoint(14, 5),
            GestureEditorLayout.rememberLayoutSize(0, 0, 14, 5),
        )
    }

    @Test
    fun `remembered range move matches normal rules inside the map`() {
        // 折りたたみなし（記憶＝実配置）では通常規則と等価です。幅20・10列で原点xは0..10。
        assertTrue(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(3, 0), MapPoint(1, 0), 10, 6, 20, 8, 20, 8,
            ),
        )
        assertFalse(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(10, 0), MapPoint(1, 0), 10, 6, 20, 8, 20, 8,
            ),
        )
        assertFalse(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(3, 0), MapPoint(0, 0), 10, 6, 20, 8, 20, 8,
            ),
        )
    }

    @Test
    fun `outside remembered range only allows returning moves`() {
        // 実配置幅14（上限4）・記憶幅20（上限10）・10列で原点(10, 0)に残った場合。
        // 復帰方向だけを許可し、空きへ遠ざかる逆行は拒否します。
        assertTrue(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(10, 0), MapPoint(-1, 0), 10, 6, 14, 8, 20, 8,
            ),
        )
        assertFalse(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(10, 0), MapPoint(1, 0), 10, 6, 14, 8, 20, 8,
            ),
        )
        // 範囲内（上限ちょうど）からは外へ出られません。
        assertFalse(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(4, 0), MapPoint(1, 0), 10, 6, 14, 8, 20, 8,
            ),
        )
        assertTrue(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(4, 0), MapPoint(-1, 0), 10, 6, 14, 8, 20, 8,
            ),
        )
        // xが範囲外でもy方向の範囲内移動は可能です（軸独立）。
        assertTrue(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(10, 0), MapPoint(0, 1), 10, 6, 14, 8, 20, 8,
            ),
        )
        assertFalse(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(10, 0), MapPoint(0, -1), 10, 6, 14, 8, 20, 8,
            ),
        )
        // 記憶を越えた一時的逸脱（ズーム変化直後など）からも内向きには動けます。
        assertTrue(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(12, 0), MapPoint(-1, 0), 10, 6, 14, 8, 20, 8,
            ),
        )
        assertFalse(
            GestureEditorLayout.canMoveWithRememberedRange(
                MapPoint(12, 0), MapPoint(1, 0), 10, 6, 14, 8, 20, 8,
            ),
        )
    }

    @Test
    fun `remembered range move matches layout move inside the map`() {
        // 折りたたみなし（記憶＝実配置）では従来のcanMoveと等価になることを総当たりで確認します。
        val layout = GraphLayout(width = 20, height = 8, cells = emptyMap(), nodePoints = emptyMap())
        val deltas = listOf(MapPoint(1, 0), MapPoint(-1, 0), MapPoint(0, 1), MapPoint(0, -1))
        for (x in 0..10) for (y in 0..4) for (delta in deltas) {
            val origin = MapPoint(x, y)
            assertEquals(
                layout.canMove(origin, delta.x, delta.y, 10, 4),
                GestureEditorLayout.canMoveWithRememberedRange(origin, delta, 10, 4, 20, 8, 20, 8),
                "origin=$origin delta=$delta",
            )
        }
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
        assertEquals(6, GestureEditorLayout.viewportRows(0.75))
        assertEquals(20, GestureEditorLayout.viewportColumns(0.375))
        assertEquals(12, GestureEditorLayout.viewportRows(0.375))
        assertEquals(30, GestureEditorLayout.viewportColumns(0.25))
        assertEquals(18, GestureEditorLayout.viewportRows(0.25))
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
    fun `upper graph viewport uses the inner panel and leaves the control rail`() {
        assertEquals(
            -GestureEditorLayout.UPPER_W / 2.0 + GestureEditorLayout.FRAME_WIDTH,
            GestureEditorLayout.UPPER_GRAPH_MIN_X,
            1.0e-9,
        )
        assertEquals(
            GestureEditorLayout.UPPER_W / 2.0 - GestureEditorLayout.FRAME_WIDTH,
            GestureEditorLayout.UPPER_INNER_MAX_X,
            1.0e-9,
        )
        assertEquals(
            GestureEditorLayout.UPPER_H / 2.0 - GestureEditorLayout.FRAME_WIDTH,
            GestureEditorLayout.UPPER_GRAPH_MAX_Y,
            1.0e-9,
        )
        assertTrue(GestureEditorLayout.UPPER_GRAPH_MAX_X < GestureEditorLayout.UPPER_INNER_MAX_X)
        assertTrue(GestureEditorLayout.UPPER_GRAPH_MAX_X > GestureEditorLayout.UPPER_GRAPH_MIN_X)
        assertTrue(GestureEditorLayout.UPPER_GRAPH_MAX_Y > GestureEditorLayout.UPPER_GRAPH_MIN_Y)
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

    @Test
    fun `nav cross stays above and left of the zoom rail without overlap`() {
        assertTrue(GestureEditorLayout.NAV_CENTER_X < 1.05)
        assertTrue(GestureEditorLayout.NAV_CENTER_Y > -0.32)

        val crossRight = GestureEditorLayout.NAV_CENTER_X +
            GestureEditorLayout.NAV_PITCH + GestureEditorLayout.NAV_SIZE / 2.0
        val zoomLeft = GestureEditorLayout.ZOOM_X - GestureEditorLayout.ZOOM_SIZE / 2.0
        assertTrue(crossRight < zoomLeft)
    }

    @Test
    fun `clip button is the same size and directly left of close`() {
        assertEquals(GestureEditorLayout.CLOSE_X - GestureEditorLayout.NAV_PITCH, GestureEditorLayout.CLIP_X, 1.0e-9)
        assertEquals(GestureEditorLayout.CLOSE_Y, GestureEditorLayout.CLIP_Y, 1.0e-9)
        assertEquals(GestureEditorLayout.NAV_SIZE, GestureEditorLayout.CLIP_SIZE, 1.0e-9)
    }

    @Test
    fun `fit zoom selects the closest level containing the whole graph`() {
        // 全体が収まる範囲で最も寄った倍率を選びます。
        assertEquals(0, GestureEditorLayout.fitZoomLevel(10, 6))
        assertEquals(0, GestureEditorLayout.fitZoomLevel(1, 1))
        assertEquals(-1, GestureEditorLayout.fitZoomLevel(11, 6))
        assertEquals(-1, GestureEditorLayout.fitZoomLevel(15, 9))
        assertEquals(-2, GestureEditorLayout.fitZoomLevel(16, 9))
        // 最小ズームでも収まらない場合は最小倍率へ退避します。
        assertEquals(-2, GestureEditorLayout.fitZoomLevel(31, 1))
        assertEquals(-2, GestureEditorLayout.fitZoomLevel(30, 19))
    }

    @Test
    fun `zoom rail packs zoom in, zoom out, and test execution downward without reset`() {
        // 下から拡大・縮小・テスト実行の順に1ピッチずつ積みます。
        assertEquals(
            GestureEditorLayout.ZOOM_TOP_Y - GestureEditorLayout.ZOOM_PITCH,
            GestureEditorLayout.ZOOM_IN_Y,
            1.0e-9,
        )
        assertEquals(GestureEditorLayout.ZOOM_TOP_Y, GestureEditorLayout.ZOOM_OUT_Y, 1.0e-9)
        assertEquals(
            GestureEditorLayout.ZOOM_TOP_Y + GestureEditorLayout.ZOOM_PITCH,
            GestureEditorLayout.TEST_BUTTON_Y,
            1.0e-9,
        )
        // 実行中はズーム列を隠すため、最下段をテストボタンが使っても重なりません。
        assertEquals(GestureEditorLayout.ZOOM_IN_Y, GestureEditorLayout.TEST_RUNNING_Y, 1.0e-9)
    }

    @Test
    fun `navigation hover region spans from the cross left edge to the zoom right edge`() {
        val crossLeft = GestureEditorLayout.NAV_CENTER_X -
            GestureEditorLayout.NAV_PITCH - GestureEditorLayout.NAV_SIZE / 2.0
        val zoomRight = GestureEditorLayout.ZOOM_X + GestureEditorLayout.ZOOM_SIZE / 2.0
        assertEquals(crossLeft, GestureEditorLayout.NAV_HOVER_MIN_X, 1.0e-9)
        assertEquals(zoomRight, GestureEditorLayout.NAV_HOVER_MAX_X, 1.0e-9)
        assertEquals((crossLeft + zoomRight) / 2.0, GestureEditorLayout.NAV_HOVER_X, 1.0e-9)
        assertTrue(GestureEditorLayout.NAV_HOVER_LINE_WIDTH >= 200)
    }
}
