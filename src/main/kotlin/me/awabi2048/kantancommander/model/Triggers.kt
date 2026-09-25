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
 * イベントの種類です。クリックの左右とレバーの結果は詳細条件に保持します。
 * ブロックの設置と破壊は異なる出来事なので統合しません。
 */
enum class EventTriggerKind {
    BLOCK_BREAK,
    BLOCK_PLACE,
    BUTTON_ON,
    LEVER_CLICK,
    PRESSURE_PLATE_ON,
    ENTITY_CLICK,
    BLOCK_CLICK,
    ;

    companion object {
        fun parse(raw: String?): EventTriggerKind? =
            raw?.trim()?.uppercase()?.let { normalized ->
                entries.firstOrNull { it.name == normalized }
            }
    }
}

/** 未指定なら左右の両方、指定すればその操作だけに一致します。 */
enum class TriggerClickSide { LEFT, RIGHT }

/** 未指定ならオン・オフの両方、指定すれば変更後の状態だけに一致します。 */
enum class TriggerLeverState { ON, OFF }

/** ブロック個体は素材名だけでは特定できないため、ワールドと整数座標で識別します。 */
data class TriggerBlockIdentity(val world: String, val x: Int, val y: Int, val z: Int)

/** 詳細画面で指定する任意条件です。null は「絞り込まない」を表します。 */
data class EventTriggerConditions(
    /** 汎用対象指定を、実際に発生した操作者1人へ適用します。 */
    val actorTarget: TargetSpec? = null,
    val actorId: java.util.UUID? = null,
    val blockType: String? = null,
    val blockIdentity: TriggerBlockIdentity? = null,
    /** 汎用対象指定を、実際にクリックされたエンティティ1体へ適用します。 */
    val entityTarget: TargetSpec? = null,
    val entityId: java.util.UUID? = null,
    val clickSide: TriggerClickSide? = null,
    val leverState: TriggerLeverState? = null,
) {
    /** 他のイベント種別の条件を持ち越さないよう、種別に属さない欄は保存時に消します。 */
    fun forEvent(event: EventTriggerKind): EventTriggerConditions = copy(
        blockType = blockType.takeIf { event != EventTriggerKind.ENTITY_CLICK },
        blockIdentity = blockIdentity.takeIf { event != EventTriggerKind.ENTITY_CLICK },
        entityTarget = entityTarget.takeIf { event == EventTriggerKind.ENTITY_CLICK },
        entityId = entityId.takeIf { event == EventTriggerKind.ENTITY_CLICK },
        clickSide = clickSide.takeIf { event == EventTriggerKind.ENTITY_CLICK || event == EventTriggerKind.BLOCK_CLICK },
        leverState = leverState.takeIf { event == EventTriggerKind.LEVER_CLICK },
    )

    /** UI・保存・実行前検証が共有する、イベント候補1体へ適用できる条件契約です。 */
    fun validationProblem(event: EventTriggerKind): String? {
        if (this != forEvent(event)) return "イベント種別に適用できない詳細条件があります"
        if (blockType != null && CommandValueRules.material(blockType)?.isBlock != true) {
            return "対象ブロックの種類が不正です"
        }
        if (blockIdentity != null && blockIdentity.world.isBlank()) return "対象ブロックのワールドが未設定です"
        fun targetProblem(spec: TargetSpec?, playerOnly: Boolean): String? {
            if (spec == null) return null
            val validKinds = if (playerOnly) setOf(
                TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS, TargetKind.ALL_PLAYERS, TargetKind.RANDOM_PLAYER,
            ) else setOf(TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES)
            if (spec.kind !in validKinds) return "イベント条件の対象分類が不正です"
            // 起動前には一時変数・探索基準・領域を解決できません。詳細画面が受け付ける
            // 単体候補の属性条件だけに限定し、保存値と実行時判定を一致させます。
            if (spec.tempName != null || spec.searchOrigin != null || spec.region != null ||
                spec.minimumDistance != null || spec.maximumDistance != null ||
                spec.dx != null || spec.dy != null || spec.dz != null || spec.limit != null ||
                spec.sort != TargetSort.NEAREST
            ) return "イベント条件では探索・距離・領域の対象設定を使用できません"
            if (spec.entityType != null && !CommandValueRules.isEntityTypeId(spec.entityType)) {
                return "対象エンティティの種類が不正です"
            }
            if (spec.tag != null && !CommandValueRules.isTag(spec.tag)) return "対象タグが不正です"
            if (spec.name != null && spec.name.length > 256) return "対象名が長すぎます"
            if (spec.gameMode != null && spec.gameMode.uppercase() !in
                setOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")) return "対象ゲームモードが不正です"
            return null
        }
        return targetProblem(actorTarget, true) ?: targetProblem(entityTarget, false)
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
    val conditions: EventTriggerConditions = EventTriggerConditions(),
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
            conditions = EventTriggerConditions(),
            intervalSeconds = intervalSeconds
                .takeIf { CommandValueRules.isTimerSeconds(it) }
                ?: DEFAULT_TIMER_SECONDS,
        )
        TriggerKind.REDSTONE -> copy(event = null, intervalSeconds = DEFAULT_TIMER_SECONDS, conditions = EventTriggerConditions())
        TriggerKind.EVENT -> copy(intervalSeconds = DEFAULT_TIMER_SECONDS, conditions = event?.let(conditions::forEvent) ?: EventTriggerConditions())
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
 * 操作者を特定できない信号変化には actor を渡さず、条件との照合でも一致させません。
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
        EventTriggerKind.BUTTON_ON,
        EventTriggerKind.LEVER_CLICK,
        EventTriggerKind.PRESSURE_PLATE_ON,
        -> listOf(
            EventTriggerArgument(BLOCK, ValueType.BLOCK),
            EventTriggerArgument(POSITION, ValueType.LOCATION),
        )
        EventTriggerKind.ENTITY_CLICK -> listOf(
            EventTriggerArgument(ACTOR, ValueType.ENTITY),
            EventTriggerArgument(TARGET, ValueType.ENTITY),
        )
        EventTriggerKind.BLOCK_CLICK -> listOf(
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
    // 除去した値・変数指定に対応する明示設定も消し、表示上の設定済み残骸を残しません。
    configuredFields?.removeIf { key ->
        (key.startsWith("arg_") && key.removePrefix("arg_") !in names) ||
            (key.startsWith("argvar_") && key.removePrefix("argvar_") !in names)
    }
    if (configuredFields?.isEmpty() == true) configuredFields = null
    val encoded = encodeDeclaredArguments(disk.arguments)
    if (encoded.isBlank()) {
        params.remove("declaredArgs")
        clearConfigured("declaredArgs")
    } else {
        params["declaredArgs"] = encoded
        markConfigured("declaredArgs")
    }
}
