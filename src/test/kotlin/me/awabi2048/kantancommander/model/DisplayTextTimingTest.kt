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

        assertEquals(11.0, timing.totalSeconds)
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
        val timing = DisplayTextTiming(-2.0, 3.0, -1.0)

        assertEquals(3.0, timing.totalSeconds)
        assertEquals(60L, timing.totalTicks)
    }

    @Test
    fun `fractional seconds preserve exact tick boundaries`() {
        val node = CommandType.DISPLAY_TEXT.newNode().apply {
            params["fadeInSeconds"] = "0.05"
            params["staySeconds"] = "0.15"
            params["fadeOutSeconds"] = "0.25"
        }

        val timing = DisplayTextTiming.from(node)

        assertEquals(0.45, timing.totalSeconds)
        assertEquals(1L, timing.fadeInTicks)
        assertEquals(3L, timing.stayTicks)
        assertEquals(5L, timing.fadeOutTicks)
        assertEquals(9L, timing.totalTicks)
        assertEquals(java.time.Duration.ofMillis(50), timing.fadeInDuration)
        assertEquals(java.time.Duration.ofMillis(150), timing.stayDuration)
    }

    @Test
    fun `invalid timing values use safe defaults`() {
        val node = CommandType.DISPLAY_TEXT.newNode().apply {
            params["fadeInSeconds"] = "0.01"
            params["staySeconds"] = "86400.05"
            params["fadeOutSeconds"] = "NaN"
        }

        val timing = DisplayTextTiming.from(node)

        assertEquals(DisplayTextTiming(1.0, 3.0, 1.0), timing)
    }
}
