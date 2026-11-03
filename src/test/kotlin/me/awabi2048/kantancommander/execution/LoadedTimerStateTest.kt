package me.awabi2048.kantancommander.execution

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadedTimerStateTest {
    @Test
    fun `未ロードの面があっても観測可能な面の立ち上がりを検知する`() {
        val state = RedstoneRuntimeState()
        assertFalse(state.observePartialPower("配置", 0, 1))
        assertTrue(state.observePartialPower("配置", 1, 1))
        assertFalse(state.observePartialPower("配置", 0, 0))
        // 未観測の面が再び読めた時点の通電は立ち上がりに数えません。
        assertFalse(state.observePartialPower("配置", 1, 1))
        assertFalse(state.observePartialPower("配置", 0, 1))
        assertTrue(state.observePartialPower("配置", 1, 1))
    }
    @Test
    fun `アンロード中は残り時間を保持し再ロード後に再開する`() {
        val timers = LoadedTimerState()
        assertFalse(timers.advance("配置", 3))
        assertFalse(timers.advance("配置", 3))
        // アンロード中はadvanceを呼ばず、時間を経過させません。
        assertTrue(timers.advance("配置", 3))
        timers.markRun("配置")
        assertFalse(timers.advance("配置", 3))
    }

    @Test
    fun `配置ごとに時間を分離し撤去時は破棄する`() {
        val timers = LoadedTimerState()
        assertFalse(timers.advance("一", 2))
        assertFalse(timers.advance("二", 2))
        timers.forget("一")
        assertFalse(timers.advance("一", 2))
        assertTrue(timers.advance("二", 2))
    }
}
