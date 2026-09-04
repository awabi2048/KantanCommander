package me.awabi2048.kantancommander.gui

import java.util.Locale

/**
 * テスト実行のサーバーtickを、ステータス／結果画面の共通表記へ変換します。
 *
 * 画面の更新周期と実行結果の確定時で別々に計算すると、境界tickや60分境界の
 * 表記がずれるため、時間の書式はこの純粋な関数へ集約します。
 */
internal object TestExecutionTimeFormatter {
    fun formatTicks(ticks: Long): String {
        val safeTicks = ticks.coerceAtLeast(0L)
        val totalSeconds = safeTicks / TICKS_PER_SECOND
        val hundredths = (safeTicks % TICKS_PER_SECOND) * 100L / TICKS_PER_SECOND
        val seconds = totalSeconds % 60L
        val totalMinutes = totalSeconds / 60L
        return if (totalMinutes < 60L) {
            String.format(
                Locale.ROOT,
                "%02d:%02d.%02d (%d tick)",
                totalMinutes,
                seconds,
                hundredths,
                safeTicks,
            )
        } else {
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            String.format(
                Locale.ROOT,
                "%02d:%02d:%02d.%02d (%d tick)",
                hours,
                minutes,
                seconds,
                hundredths,
                safeTicks,
            )
        }
    }

    private const val TICKS_PER_SECOND = 20L
}
