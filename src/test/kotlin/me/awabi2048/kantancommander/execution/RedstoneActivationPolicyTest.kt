package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.intervalTicks
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class RedstoneActivationPolicyTest {
    @Test
    fun `redstone trigger runs only on rising edge`() {
        assertTrue(decide(trigger = ScriptTrigger(TriggerKind.REDSTONE), previous = false, powered = true))
        assertFalse(decide(trigger = ScriptTrigger(TriggerKind.REDSTONE), previous = true, powered = true))
        assertFalse(decide(trigger = ScriptTrigger(TriggerKind.REDSTONE), previous = true, powered = false))
        assertFalse(decide(trigger = ScriptTrigger(TriggerKind.REDSTONE), previous = false, powered = false))
    }

    @Test
    fun `timer trigger repeats by interval and ignores signal edges`() {
        val timer = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = 0.5)
        // 信号の立ち上がりは実行条件に含めず、基準時刻からの間隔経過だけで判定する。
        assertFalse(decide(trigger = timer, previous = false, powered = true, now = 104, last = 95))
        assertFalse(decide(trigger = timer, previous = true, powered = true, now = 109, last = 100))
        assertTrue(decide(trigger = timer, previous = true, powered = true, now = 110, last = 100))
        // 通電が切れている間も経過時間は進むため、間隔が満ちていれば直ちに実行される。
        assertTrue(decide(trigger = timer, previous = true, powered = false, now = 110, last = 100))
        assertFalse(decide(trigger = timer, previous = true, powered = false, now = 109, last = 100))
    }

    @Test
    fun `timer trigger waits for the first anchor`() {
        val timer = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = 0.5)
        assertFalse(decide(trigger = timer, powered = false, now = 100, last = null))
        assertFalse(decide(trigger = timer, powered = false, now = 109, last = 100))
        assertTrue(decide(trigger = timer, powered = false, now = 110, last = 100))
    }

    @Test
    fun `event trigger never runs on the tick watcher`() {
        val event = ScriptTrigger(TriggerKind.EVENT)
        assertFalse(decide(trigger = event, previous = false, powered = true, now = 1000, last = 0))
    }

    @Test
    fun `first observation while powered does not count as rising edge`() {
        val state = RedstoneRuntimeState()
        // 初回観測は通電状態を記録するだけで、前回値として現在値そのものを返す。
        assertEquals(true, state.observePower("world,1,2,3", true))
        // そのためサーバー起動直後に通電中でも、立ち上がり実行は起きない。
        assertFalse(
            decide(
                trigger = ScriptTrigger(TriggerKind.REDSTONE),
                previous = state.observePower("world,1,2,3", true),
                powered = true,
            )
        )
    }

    @Test
    fun `power mask changes direction without creating a second rising edge`() {
        val state = RedstoneRuntimeState()
        val key = "world,1,2,3"

        // 6面の入力はORした通電状態で立ち上がりを判定します。
        assertFalse(state.observePower(key, 0))
        assertFalse(state.observePower(key, 1 shl 2))
        // 北面から東面へ切り替わっても、通電中のままなので新しい立ち上がりではありません。
        assertTrue(state.observePower(key, 1 shl 4))
        assertTrue(state.observePower(key, 0))
        assertFalse(state.observePower(key, 1 shl 5))
    }

    @Test
    fun `timer anchor and power edge reset when configuration or placement is removed`() {
        val state = RedstoneRuntimeState()
        val id = UUID.randomUUID()

        assertEquals(true, state.observePower("world,1,2,3", true))
        assertEquals(100L, state.timerAnchor(id, 100L))
        state.markRun(id, 120L)
        assertEquals(120L, state.timerAnchor(id, 130L))

        state.resetTiming(id)
        assertEquals(140L, state.timerAnchor(id, 140L))
        state.forget("world,1,2,3", id)
        assertEquals(true, state.observePower("world,1,2,3", true))
    }

    private fun decide(
        trigger: ScriptTrigger,
        previous: Boolean = false,
        powered: Boolean,
        now: Long = 100,
        last: Long? = 90,
    ): Boolean = when (trigger.normalized().kind) {
        TriggerKind.EVENT -> false
        TriggerKind.TIMER -> RedstoneActivationPolicy.isIntervalElapsed(
            intervalTicks = trigger.normalized().intervalTicks(),
            currentTick = now,
            lastRunTick = last,
        )
        TriggerKind.REDSTONE -> RedstoneActivationPolicy.isRisingEdge(previous, powered)
    }
}
