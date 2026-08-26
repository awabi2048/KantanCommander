package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.ActivationMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

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
    fun `timer with redstone runs on rising edge then repeats by interval`() {
        // タイマー有効時は立ち上がりエッジで即時実行され、基準時刻が張り直される。
        assertTrue(decide(timer = true, previous = false, powered = true, now = 100, last = 95))
        // 立ち上がり後は常時実行へ切り替えた場合と同じく、間隔経過だけで判定する。
        assertFalse(decide(timer = true, previous = true, powered = true, now = 109, last = 100))
        assertTrue(decide(timer = true, previous = true, powered = true, now = 110, last = 100))
        // 通電が切れている間も経過時間は進むため、間隔が満ちていれば直ちに実行される。
        assertTrue(decide(timer = true, previous = true, powered = false, now = 110, last = 100))
        assertFalse(decide(timer = true, previous = true, powered = false, now = 109, last = 100))
    }

    @Test
    fun `always active timer ignores redstone but observes interval`() {
        assertFalse(
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

    @Test
    fun `first observation while powered does not count as rising edge`() {
        val state = RedstoneRuntimeState()
        // 初回観測は通電状態を記録するだけで、前回値として現在値そのものを返す。
        assertEquals(true, state.observePower("world,1,2,3", true))
        // そのためサーバー起動直後に通電中でも、立ち上がり実行は起きない。
        assertFalse(
            decide(
                timer = false,
                previous = state.observePower("world,1,2,3", true),
                powered = true,
            )
        )
    }

    @Test
    fun `timer anchor and power edge reset when configuration or placement is removed`() {
        val state = RedstoneRuntimeState()
        val id = UUID.randomUUID()

        assertEquals(true, state.observePower("world,1,2,3", true))
        assertEquals(100L, state.timerAnchor(id, true, 100L))
        state.markRun(id, 120L)
        assertEquals(120L, state.timerAnchor(id, true, 130L))

        state.resetTiming(id)
        assertEquals(140L, state.timerAnchor(id, true, 140L))
        state.forget("world,1,2,3", id)
        assertEquals(true, state.observePower("world,1,2,3", true))
        assertEquals(null, state.timerAnchor(id, false, 150L))
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
