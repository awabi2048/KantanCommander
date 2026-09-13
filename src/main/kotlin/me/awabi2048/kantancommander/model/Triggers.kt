package me.awabi2048.kantancommander.model

/**
 * issue #7 の起動方法です。
 *
 * 「タイマー設定」を「起動方法」へ整理し、タイマー／信号／イベントから一つを選びます。
 * 1スクリプトに複数の起動方法は持たせません。単一選択にすることで、検証・実行・
 * 出力のすべてが同じ起動契約を共有し、起動ごとの意味の食い違いを防ぎます。
 * タイマーの維持範囲は従来どおり配置単位の実行であり、間隔の基準も変えません。
 */
enum class TriggerKind {
    TIMER,
    REDSTONE,
    EVENT,
    ;

    companion object {
        fun parse(raw: String?): TriggerKind? =
            raw?.trim()?.uppercase()?.let { normalized ->
                entries.firstOrNull { it.name == normalized }
            }
    }
}

/**
 * issue #7 の初期イベント種別です。
 *
 * 手順書の指定どおり初期種別に限定します。起動設定そのもので操作を選び分けるため、
 * ボタンとレバーを分け、レバーはオン／オフ、クリックは左右を別種別とします。
 * 感圧板は「踏まれた時」だけを提供します（離した時に起動させません）。
 * 追加のイベント種別は、引数契約と登録解除の検証と合わせて拡張します。
 */
enum class EventTriggerKind {
    BLOCK_BREAK,
    BLOCK_PLACE,
    BUTTON_ON,
    BUTTON_OFF,
    LEVER_ON,
    LEVER_OFF,
    PRESSURE_PLATE_ON,
    ENTITY_LEFT_CLICK,
    ENTITY_RIGHT_CLICK,
    BLOCK_LEFT_CLICK,
    BLOCK_RIGHT_CLICK,
    ;

    companion object {
        fun parse(raw: String?): EventTriggerKind? =
            raw?.trim()?.uppercase()?.let { normalized ->
                entries.firstOrNull { it.name == normalized }
            }
    }
}

/**
 * issue #7 の起動方法の保存形式です。
 *
 * 旧JSONには存在しないため、欠落時は `DiskScript.trigger` が null となり、
 * [DiskScript.effectiveTrigger] が従来の `activation`＋`timer` から読み替えます。
 * 新規保存は常に明示値を書き、従来欄（`timer`／`activation`）も整合させます。
 * `intervalSeconds` は TIMER 以外では使用しません。
 */
data class ScriptTrigger(
    val kind: TriggerKind = TriggerKind.REDSTONE,
    val event: EventTriggerKind? = null,
    val intervalSeconds: Double = DEFAULT_TIMER_SECONDS,
) {
    /**
     * 保存前の正規化です。
     *
     * TIMER 以外の種別に残ったイベント指定・間隔指定は実行意味を持たないため、
     * 保存時点で除去・既定化し、stale な設定の読み替えを防ぎます。
     * EVENT で種別が空の場合は検証層で拒否するため、ここでは補完しません。
     */
    fun normalized(): ScriptTrigger = when (kind) {
        TriggerKind.TIMER -> copy(
            event = null,
            intervalSeconds = intervalSeconds
                .takeIf { CommandValueRules.isTimerSeconds(it) }
                ?: DEFAULT_TIMER_SECONDS,
        )
        TriggerKind.REDSTONE -> copy(event = null, intervalSeconds = DEFAULT_TIMER_SECONDS)
        TriggerKind.EVENT -> copy(intervalSeconds = DEFAULT_TIMER_SECONDS)
    }
}

/**
 * 実行時に採用する起動方法を返します。
 *
 * 明示指定があれば正規化して採用し、旧保存（欠落）は従来欄から読み替えます。
 * タイマー有効→TIMER、有効でない→REDSTONE であり、`ALWAYS_ACTIVE` 単独の
 * 旧値は検証層で拒否されるため到達しません。到達した場合はタイマー扱いとします。
 */
fun DiskScript.effectiveTrigger(): ScriptTrigger {
    val explicit = trigger
    if (explicit != null) return explicit.normalized()
    if (timer.enabled) return ScriptTrigger(TriggerKind.TIMER, intervalSeconds = timer.intervalSeconds)
    return ScriptTrigger(TriggerKind.REDSTONE)
}

/**
 * issue #7 のイベント引数の定義です。
 *
 * イベント引数は #2・#8 と共通の型付き値として、実行開始時に定義済みの
 * EXECUTION 変数になります。呼び出し側は全引数に対応する値を設定し、
 * 検証層は同じ表を実行開始時点の定義として扱います。
 */
data class EventTriggerArgument(
    val name: String,
    val type: ValueType,
)

/**
 * issue #7 のイベント引数契約です。
 *
 * 引数の正式名称・型はこの表が単一正本です。実行層の引数生成と検証層の
 * 定義表はどちらもここから導出し、名称・型の食い違いを作りません。
 * ボタン／レバー／感圧板は信号変化の観測であり操作者を特定できないため、
 * 操作者（actor）を持ちません。オン／オフや左右はイベント種別そのもので
 * 選び分けるため、状態や操作方向の引数は持ちません。
 */
object EventTriggerArguments {
    const val ACTOR = "event_actor"
    const val BLOCK = "event_block"
    const val POSITION = "event_position"
    const val TARGET = "event_target"

    /** イベント種別ごとの正式な引数表です。順序は GUI の表示順と一致させます。 */
    fun argumentsFor(event: EventTriggerKind): List<EventTriggerArgument> = when (event) {
        EventTriggerKind.BLOCK_BREAK, EventTriggerKind.BLOCK_PLACE -> listOf(
            EventTriggerArgument(ACTOR, ValueType.ENTITY),
            EventTriggerArgument(BLOCK, ValueType.BLOCK),
            EventTriggerArgument(POSITION, ValueType.LOCATION),
        )
        EventTriggerKind.BUTTON_ON, EventTriggerKind.BUTTON_OFF,
        EventTriggerKind.LEVER_ON, EventTriggerKind.LEVER_OFF,
        EventTriggerKind.PRESSURE_PLATE_ON,
        -> listOf(
            EventTriggerArgument(BLOCK, ValueType.BLOCK),
            EventTriggerArgument(POSITION, ValueType.LOCATION),
        )
        EventTriggerKind.ENTITY_LEFT_CLICK, EventTriggerKind.ENTITY_RIGHT_CLICK -> listOf(
            EventTriggerArgument(ACTOR, ValueType.ENTITY),
            EventTriggerArgument(TARGET, ValueType.ENTITY),
        )
        EventTriggerKind.BLOCK_LEFT_CLICK, EventTriggerKind.BLOCK_RIGHT_CLICK -> listOf(
            EventTriggerArgument(ACTOR, ValueType.ENTITY),
            EventTriggerArgument(BLOCK, ValueType.BLOCK),
            EventTriggerArgument(POSITION, ValueType.LOCATION),
        )
    }

    /** 検証層が実行開始時点の定義として扱う型表です。 */
    fun seedTypesFor(event: EventTriggerKind): Map<String, ValueType> =
        argumentsFor(event).associate { it.name to it.type }
}

/**
 * issue #8 の呼び出し先ディスクの選択をノードへ反映します。
 *
 * 呼び出すディスクの内容スナップショットを保持し、併せて引数宣言を転記します。
 * 宣言から消えた引数の古い値・変数指定は、実行時に残存キーを誤って使わないよう
 * 除去します。選択操作の副作用を一箇所へ集約し、GUI とテストで同じ規則を使います。
 */
fun CommandNode.applyCalledDisk(diskId: java.util.UUID, disk: DiskScript) {
    params["diskId"] = diskId.toString()
    snapshot = disk.graph.deepCopy()
    markConfigured("diskId")
    val names = disk.arguments.map { it.name }.toSet()
    params.keys.removeIf { key ->
        (key.startsWith("arg_") && key.removePrefix("arg_") !in names) ||
            (key.startsWith("argvar_") && key.removePrefix("argvar_") !in names)
    }
    val encoded = encodeDeclaredArguments(disk.arguments)
    if (encoded.isBlank()) {
        params.remove("declaredArgs")
        clearConfigured("declaredArgs")
    } else {
        params["declaredArgs"] = encoded
        markConfigured("declaredArgs")
    }
}
