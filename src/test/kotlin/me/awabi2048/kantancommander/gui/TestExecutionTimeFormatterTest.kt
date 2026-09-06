package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestExecutionTimeFormatterTest {
    @Test
    fun `formats server ticks with two decimal places`() {
        assertEquals("00:00.00 (0 tick)", TestExecutionTimeFormatter.formatTicks(0L))
        assertEquals("00:00.05 (1 tick)", TestExecutionTimeFormatter.formatTicks(1L))
        assertEquals("00:00.95 (19 tick)", TestExecutionTimeFormatter.formatTicks(19L))
        assertEquals("00:01.00 (20 tick)", TestExecutionTimeFormatter.formatTicks(20L))
    }

    @Test
    fun `switches to cumulative hours after one hour`() {
        assertEquals(
            "01:00:00.00 (72000 tick)",
            TestExecutionTimeFormatter.formatTicks(72_000L),
        )
        assertEquals(
            "02:03:04.50 (147690 tick)",
            TestExecutionTimeFormatter.formatTicks(147_690L),
        )
    }

    @Test
    fun `does not render negative elapsed ticks`() {
        assertEquals("00:00.00 (0 tick)", TestExecutionTimeFormatter.formatTicks(-1L))
    }

    @Test
    fun `real processing time subtracts only debug waits`() {
        // デバッグ待機の合計だけを差し引き、待機コマンドの時間は含めたままにします。
        assertEquals(80L, TestExecutionTimeFormatter.realProcessingTicks(100L, 20L))
        assertEquals(0L, TestExecutionTimeFormatter.realProcessingTicks(30L, 40L))
        assertEquals(50L, TestExecutionTimeFormatter.realProcessingTicks(50L, 0L))
    }
}
