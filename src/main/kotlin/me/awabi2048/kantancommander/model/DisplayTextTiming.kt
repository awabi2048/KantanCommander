package me.awabi2048.kantancommander.model

/**
 * title・subtitle・actionbarで共有する表示時間です。
 *
 * 値は保存形式どおり秒を正本とし、Minecraftのtickへの変換は実行・出力側が
 * 必要とする境界でこのモデルから行います。アクションバーにはTitle.Times相当の
 * Bukkit APIがないため、実行時は3区分の合計を表示ライフサイクルとして扱います。
 */
data class DisplayTextTiming(
    val fadeInSeconds: Double,
    val staySeconds: Double,
    val fadeOutSeconds: Double,
) {
    val totalSeconds: Double
        get() = listOf(fadeInSeconds, staySeconds, fadeOutSeconds)
            .sumOf { it.coerceAtLeast(0.0) }

    val fadeInTicks: Long
        get() = safeTicks(fadeInSeconds)

    val stayTicks: Long
        get() = safeTicks(staySeconds)

    val fadeOutTicks: Long
        get() = safeTicks(fadeOutSeconds)

    val totalTicks: Long
        get() = fadeInTicks + stayTicks + fadeOutTicks

    /** Bukkit Title APIへ渡す時間も、tickから変換して秒の切り捨てを防ぎます。 */
    val fadeInDuration: java.time.Duration
        get() = java.time.Duration.ofMillis(fadeInTicks * MILLIS_PER_TICK)

    val stayDuration: java.time.Duration
        get() = java.time.Duration.ofMillis(stayTicks * MILLIS_PER_TICK)

    val fadeOutDuration: java.time.Duration
        get() = java.time.Duration.ofMillis(fadeOutTicks * MILLIS_PER_TICK)

    companion object {
        fun from(node: CommandNode): DisplayTextTiming = DisplayTextTiming(
            fadeInSeconds = value(node, "fadeInSeconds", 1.0),
            staySeconds = value(node, "staySeconds", 3.0),
            fadeOutSeconds = value(node, "fadeOutSeconds", 1.0),
        )

        private fun value(node: CommandNode, key: String, fallback: Double): Double =
            CommandValueRules.parseFiniteDouble(node.string(key))
                ?.takeIf(CommandValueRules::isDisplayTimeSeconds)
                ?: fallback

        private fun safeTicks(seconds: Double): Long =
            CommandValueRules.secondsToTicks(seconds.coerceAtLeast(0.0)) ?: 0L

        private const val MILLIS_PER_TICK = 50L
    }
}

/** 時間設定を表示・検証・実行・データパック出力する表示方式を一元管理します。 */
object DisplayTextTimingPolicy {
    private val TIMED_MODES = setOf("title", "subtitle", "actionbar")

    fun supports(mode: String): Boolean = mode in TIMED_MODES

    fun supports(node: CommandNode): Boolean = supports(node.string("mode", "tellraw"))
}
