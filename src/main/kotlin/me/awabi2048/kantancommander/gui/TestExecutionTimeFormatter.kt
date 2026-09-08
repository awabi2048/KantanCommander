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

    /**
     * 実行中の経過表示用の秒単位の書式です。
     *
     * テスト中の定周期更新を1Hzへ間引くため、百分秒・tick数の毎tick変化を
     * なくします。最終結果の表示は [formatTicks] のままにし、小数点以下と
     * tick表記を残します。
     */
    fun formatTicksSeconds(ticks: Long): String {
        val safeTicks = ticks.coerceAtLeast(0L)
        val totalSeconds = safeTicks / TICKS_PER_SECOND
        val seconds = totalSeconds % 60L
        val totalMinutes = totalSeconds / 60L
        return if (totalMinutes < 60L) {
            String.format(
                Locale.ROOT,
                "%02d:%02d",
                totalMinutes,
                seconds,
            )
        } else {
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            String.format(
                Locale.ROOT,
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds,
            )
        }
    }
    /**
     * 所要時間からデバッグ待機の合計を差し引いた実処理時間を求めます。
     * 待機コマンドの所要時間は差し引かず、実処理に含めたままにします。
     */
    fun realProcessingTicks(elapsedTicks: Long, debugWaitTicks: Long): Long =
        (elapsedTicks - debugWaitTicks).coerceAtLeast(0L)

    private const val TICKS_PER_SECOND = 20L
}
