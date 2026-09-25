package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.FireworkSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FireworkFlightMonitorTest {
    private fun check(
        valid: Boolean = true,
        detonated: Boolean = false,
        chunkLoaded: Boolean = true,
        currentY: Double = 60.0,
        targetY: Double = 80.0,
        elapsed: Long = 10L,
        timeout: Long = 150L,
    ) = FireworkFlightService.FlightMonitor.check(
        valid = valid,
        detonated = detonated,
        targetChunkLoaded = chunkLoaded,
        currentY = currentY,
        targetY = targetY,
        elapsedTicks = elapsed,
        timeoutTicks = timeout,
    )

    @Test
    fun `waits while climbing below the target`() {
        assertEquals(FireworkFlightService.FlightCheck.WAIT, check(currentY = 79.9, targetY = 80.0))
    }

    @Test
    fun `detonates on arrival at or above the target`() {
        assertEquals(FireworkFlightService.FlightCheck.DETONATE, check(currentY = 80.0, targetY = 80.0))
        assertEquals(FireworkFlightService.FlightCheck.DETONATE, check(currentY = 80.5, targetY = 80.0))
    }

    @Test
    fun `waits while the target chunk is unloaded`() {
        assertEquals(
            FireworkFlightService.FlightCheck.WAIT,
            check(currentY = 90.0, targetY = 80.0, chunkLoaded = false),
        )
    }

    @Test
    fun `aborts when the rocket is gone or already detonated`() {
        assertEquals(FireworkFlightService.FlightCheck.ABORT, check(valid = false))
        assertEquals(FireworkFlightService.FlightCheck.ABORT, check(detonated = true))
    }

    @Test
    fun `aborts on timeout instead of flying forever`() {
        assertEquals(
            FireworkFlightService.FlightCheck.ABORT,
            check(currentY = 10.0, elapsed = 150L, timeout = 150L),
        )
    }

    @Test
    fun `timeout stays beyond the natural lifetime backstop`() {
        val height = 20.0
        assertEquals(
            FireworkSettings.detonationTicks(height) + FireworkSettings.LIFETIME_MARGIN +
                FireworkFlightService.FlightMonitor.TIMEOUT_MARGIN_TICKS,
            FireworkFlightService.FlightMonitor.timeoutTicks(height),
        )
    }
}
