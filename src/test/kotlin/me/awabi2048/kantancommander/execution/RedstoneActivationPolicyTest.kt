package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.ActivationMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedstoneActivationPolicyTest {
    @Test
    fun `timer off runs only on redstone rising edge`() {
        assertTrue(decide(timer = false, previous = false, powered = true))
        assertFalse(decide(timer = false, previous = true, powered = true))
        assertFalse(decide(timer = false, previous = true, powered = false))
        assertFalse(decide(timer = false, previous = false, powered = false))
    }

    @Test
    fun `timer off never permits always active`() {
        assertFalse(
            decide(
                activation = ActivationMode.ALWAYS_ACTIVE,
                timer = false,
                previous = false,
                powered = true,
            )
        )
    }

    @Test
    fun `timer with redstone repeats only while powered and interval elapsed`() {
        assertTrue(decide(timer = true, powered = true, now = 100, last = null))
        assertFalse(decide(timer = true, powered = true, now = 109, last = 100))
        assertTrue(decide(timer = true, powered = true, now = 110, last = 100))
        assertFalse(decide(timer = true, powered = false, now = 110, last = 100))
    }

    @Test
    fun `always active timer ignores redstone but observes interval`() {
        assertTrue(
            decide(
                activation = ActivationMode.ALWAYS_ACTIVE,
                timer = true,
                powered = false,
                now = 100,
                last = null,
            )
        )
        assertFalse(
            decide(
                activation = ActivationMode.ALWAYS_ACTIVE,
                timer = true,
                powered = false,
                now = 109,
                last = 100,
            )
        )
        assertTrue(
            decide(
                activation = ActivationMode.ALWAYS_ACTIVE,
                timer = true,
                powered = false,
                now = 110,
                last = 100,
            )
        )
    }

    private fun decide(
        activation: ActivationMode = ActivationMode.NEEDS_REDSTONE,
        timer: Boolean,
        previous: Boolean = false,
        powered: Boolean,
        now: Long = 100,
        last: Long? = 90,
    ) = RedstoneActivationPolicy.shouldRun(
        activation = activation,
        timerEnabled = timer,
        intervalTicks = 10,
        wasPowered = previous,
        isPowered = powered,
        currentTick = now,
        lastRunTick = last,
    )
}
