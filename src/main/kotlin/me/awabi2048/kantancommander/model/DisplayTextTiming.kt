package me.awabi2048.kantancommander.model

/**
 * タイトルとアクションバーで共有する表示時間です。
 *
 * 値は保存形式どおり秒を正本とし、Minecraftのtickへの変換は実行・出力側が
 * 必要とする境界でこのモデルから行います。アクションバーにはTitle.Times相当の
 * Bukkit APIがないため、実行時は3区分の合計を表示ライフサイクルとして扱います。
 */
data class DisplayTextTiming(
    val fadeInSeconds: Int,
    val staySeconds: Int,
    val fadeOutSeconds: Int,
) {
    val totalSeconds: Long
        get() = listOf(fadeInSeconds, staySeconds, fadeOutSeconds)
            .sumOf { it.coerceAtLeast(0).toLong() }

    val fadeInTicks: Long
        get() = fadeInSeconds.coerceAtLeast(0).toLong() * TICKS_PER_SECOND

    val stayTicks: Long
        get() = staySeconds.coerceAtLeast(0).toLong() * TICKS_PER_SECOND

    val fadeOutTicks: Long
        get() = fadeOutSeconds.coerceAtLeast(0).toLong() * TICKS_PER_SECOND

    val totalTicks: Long
        get() = totalSeconds * TICKS_PER_SECOND

    companion object {
        fun from(node: CommandNode): DisplayTextTiming = DisplayTextTiming(
            fadeInSeconds = node.int("fadeInSeconds", 1),
            staySeconds = node.int("staySeconds", 3),
            fadeOutSeconds = node.int("fadeOutSeconds", 1),
        )
    }
}

/** 時間設定を表示・検証・実行・データパック出力する表示方式を一元管理します。 */
object DisplayTextTimingPolicy {
    private val TIMED_MODES = setOf("title", "actionbar")

    fun supports(mode: String): Boolean = mode in TIMED_MODES

    fun supports(node: CommandNode): Boolean = supports(node.string("mode", "tellraw"))
}
