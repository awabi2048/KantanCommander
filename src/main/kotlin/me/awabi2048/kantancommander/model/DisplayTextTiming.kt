package me.awabi2048.kantancommander.model

/**
 * title・subtitle・actionbarで共有する表示時間です。
 *
 * 値は保存形式どおり秒を正本とし、Minecraftのtickへの変換は実行・出力側が
 * 必要とする境界でこのモデルから行います。
 * issue #35-2: アクションバーは単独の表示時間（stay）で動作し、
 * フェード値は加算しません。タイトル系は従来どおり3区分を使います。
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

    /**
     * issue #35-2: アクションバーの表示時間です。
     *
     * 単独の表示時間（stay）だけを使い、フェード値は加算しません。
     * AdventureのsendActionBarにフェード設定がなく、表示維持は再送で行うため、
     * 3区分の合計をライフサイクルにすると利用者の設定と表示がずれます。
     */
    val actionBarTicks: Long
        get() = stayTicks

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

    /**
     * issue #review P2-7: 有効な時間項目の定義です。
     *
     * アクションバーは単独の表示時間だけを使い、フェード値は加算しません。
     * 検証・参照チェック・実行でこの定義を共有し、非表示項目の残存値で
     * 動作が変わらないようにします。
     */
    fun effectiveTimeFields(mode: String): Set<String> =
        if (mode == "actionbar") setOf("staySeconds")
        else setOf("fadeInSeconds", "staySeconds", "fadeOutSeconds")

    fun effectiveTimeFields(node: CommandNode): Set<String> =
        effectiveTimeFields(node.string("mode", "tellraw"))
}
