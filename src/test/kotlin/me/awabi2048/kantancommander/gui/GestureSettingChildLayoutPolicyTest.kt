package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GestureSettingChildLayoutPolicyTest {
    @Test
    fun `bottom detail reserves return footer and leaves three rows for usual choices`() {
        val page = GestureSettingChildLayoutPolicy.plan(
            topChoiceCount = 9,
            bottomChoiceCount = 1,
            requestedPage = 0,
        )

        assertEquals(2, page.count)
        assertEquals(6, page.topPageSize)
        assertEquals(3, page.topRowCount)
        assertEquals(-0.29, GestureSettingChildLayoutPolicy.bottomY(0, 1), 1.0e-9)

        val lastTopBottom = GestureSettingChildLayoutPolicy.topY(2) -
            GestureSettingChildLayoutPolicy.CHOICE_HEIGHT / 2.0
        val detailTop = GestureSettingChildLayoutPolicy.bottomY(0, 1) +
            GestureSettingChildLayoutPolicy.CHOICE_HEIGHT / 2.0
        assertTrue(lastTopBottom > detailTop)
        assertTrue(lastTopBottom - detailTop >= GestureSettingChildLayoutPolicy.GROUP_GAP - 1.0e-9)

        val detailBottom = GestureSettingChildLayoutPolicy.bottomY(0, 1) -
            GestureSettingChildLayoutPolicy.CHOICE_HEIGHT / 2.0
        val returnTop = GestureSettingChildLayoutPolicy.BACK_Y +
            GestureSettingChildLayoutPolicy.BACK_HEIGHT / 2.0
        assertTrue(detailBottom > returnTop)
        assertTrue(detailBottom - returnTop >= GestureSettingChildLayoutPolicy.FOOTER_GAP - 1.0e-9)
    }

    @Test
    fun `bottom aligned group stays visible while top choices page`() {
        val second = GestureSettingChildLayoutPolicy.plan(9, 1, requestedPage = 1)

        assertEquals(1, second.index)
        assertEquals(2, second.count)
        assertEquals(6, second.topStart)
        assertEquals(9, second.topEndExclusive)
        assertEquals(1, second.bottomRowCount)
    }

    @Test
    fun `without bottom choices the reserved footer permits four top rows`() {
        val page = GestureSettingChildLayoutPolicy.plan(8, 0, requestedPage = 0)

        assertEquals(8, page.topPageSize)
        assertEquals(4, page.topRowCount)
        assertEquals(1, page.count)
    }

    @Test
    fun `setting route depth maps to nested child ids and preserves the root id`() {
        assertEquals("gesture-editor-setting-child", GestureSettingChildScreenIds.forRouteSize(1))
        assertEquals("gesture-editor-setting-child", GestureSettingChildScreenIds.forRouteSize(2))
        assertEquals("gesture-editor-setting-child:2", GestureSettingChildScreenIds.forRouteSize(3))
        assertEquals(2, GestureSettingChildScreenIds.depthForRouteSize(3))
    }
}
