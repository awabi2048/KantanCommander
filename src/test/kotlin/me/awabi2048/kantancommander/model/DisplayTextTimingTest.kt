package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DisplayTextTimingTest {
    @Test
    fun `title and actionbar share all three timing phases`() {
        val node = CommandType.DISPLAY_TEXT.newNode().apply {
            params["mode"] = "actionbar"
            params["fadeInSeconds"] = "2"
            params["staySeconds"] = "6"
            params["fadeOutSeconds"] = "3"
        }

        val timing = DisplayTextTiming.from(node)

        assertEquals(11L, timing.totalSeconds)
        assertEquals(40L, timing.fadeInTicks)
        assertEquals(120L, timing.stayTicks)
        assertEquals(60L, timing.fadeOutTicks)
        assertEquals(220L, timing.totalTicks)
        assertTrue(DisplayTextTimingPolicy.supports(node))
        assertTrue(DisplayTextTimingPolicy.supports("title"))
        assertFalse(DisplayTextTimingPolicy.supports("tellraw"))
    }

    @Test
    fun `runtime timing conversion clamps malformed negative values`() {
        val timing = DisplayTextTiming(-2, 3, -1)

        assertEquals(3L, timing.totalSeconds)
        assertEquals(60L, timing.totalTicks)
    }
}
