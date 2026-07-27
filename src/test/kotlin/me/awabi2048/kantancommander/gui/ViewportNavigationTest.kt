package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ViewportNavigationTest {
    @Test
    fun `single navigation icon maps all four click gestures`() {
        assertEquals(MapPoint(-1, 0), ViewportNavigation.delta(left = true, right = false, shift = false))
        assertEquals(MapPoint(1, 0), ViewportNavigation.delta(left = false, right = true, shift = false))
        assertEquals(MapPoint(0, -1), ViewportNavigation.delta(left = true, right = false, shift = true))
        assertEquals(MapPoint(0, 1), ViewportNavigation.delta(left = false, right = true, shift = true))
    }

    @Test
    fun `unsupported gestures do not navigate`() {
        assertNull(ViewportNavigation.delta(left = false, right = false, shift = false))
        assertNull(ViewportNavigation.delta(left = true, right = true, shift = false))
    }
}
