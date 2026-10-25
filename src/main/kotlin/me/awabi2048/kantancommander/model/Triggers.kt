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

enum class TriggerNormalizationIssue {
    BLOCK_CONDITION_REMOVED,
    ENTITY_CONDITION_REMOVED,
    CLICK_CONDITION_REMOVED,
    LEVER_CONDITION_REMOVED,
    DISCOVERY_FILTER_REMOVED,
    TARGET_KIND_NORMALIZED,
    INAPPLICABLE_CONDITION,
}


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
    /**
     * 保存前の正規化です。
     *
     * トリガーは発生した単一候補への属性絞りだけを意味します。種別に属さない欄は
     * 保存時に除去し、慣用的な対象設定の探索文脈（距離・件数・順序・領域・基準・
     * 一時参照・来歴）は除去して単体意味へ限定します。種別内の種類差
     * （NEARBY／ALL／RANDOM 等）は単体照合上同値のため NEAREST へ寄せます。
     * 除去される内容は [normalizationWarning] で事前に警告します。
     */
    fun forEvent(event: EventTriggerKind): EventTriggerConditions = copy(
        actorTarget = actorTarget?.let { normalizeTriggerTarget(it, playerOnly = true) },
        blockType = blockType.takeIf { event != EventTriggerKind.ENTITY_CLICK },
        blockIdentity = blockIdentity.takeIf { event != EventTriggerKind.ENTITY_CLICK },
        entityTarget = entityTarget?.let { normalizeTriggerTarget(it, playerOnly = false) }
            .takeIf { event == EventTriggerKind.ENTITY_CLICK },
        entityId = entityId.takeIf { event == EventTriggerKind.ENTITY_CLICK },
        clickSide = clickSide.takeIf { event == EventTriggerKind.ENTITY_CLICK || event == EventTriggerKind.BLOCK_CLICK },
        leverState = leverState.takeIf { event == EventTriggerKind.LEVER_CLICK },
    )

    fun hasActorFilter(): Boolean = actorTarget != null || actorId != null

    fun hasEntityFilter(): Boolean = entityTarget != null || entityId != null

    fun hasBlockFilter(): Boolean = blockType != null || blockIdentity != null

    /**
     * 保存時に除去される内容の警告です。正規化で変化がなければ null です。
     *
     * 種別切替・保存の確定前に呼び出し、適用外条件の持ち越しを利用者へ知らせます。
     * 検証エラーではなく保存時除去の事前警告であり、互換読み込みを妨げません。
     */
    fun normalizationWarning(event: EventTriggerKind): TriggerNormalizationIssue? {
        if (this == forEvent(event)) return null
        if (blockType != null && event == EventTriggerKind.ENTITY_CLICK) {
            return TriggerNormalizationIssue.BLOCK_CONDITION_REMOVED
        }
        if (blockIdentity != null && event == EventTriggerKind.ENTITY_CLICK) {
            return TriggerNormalizationIssue.BLOCK_CONDITION_REMOVED
        }
        if ((entityTarget != null || entityId != null) && event != EventTriggerKind.ENTITY_CLICK) {
            return TriggerNormalizationIssue.ENTITY_CONDITION_REMOVED
        }
        if (clickSide != null && event != EventTriggerKind.ENTITY_CLICK && event != EventTriggerKind.BLOCK_CLICK) {
            return TriggerNormalizationIssue.CLICK_CONDITION_REMOVED
        }
        if (leverState != null && event != EventTriggerKind.LEVER_CLICK) {
            return TriggerNormalizationIssue.LEVER_CONDITION_REMOVED
        }
        if (actorTarget?.hasTriggerIneffectiveSetting() == true || entityTarget?.hasTriggerIneffectiveSetting() == true) {
            return TriggerNormalizationIssue.DISCOVERY_FILTER_REMOVED
        }
        if (actorTarget?.needsTriggerKindNormalization() == true || entityTarget?.needsTriggerKindNormalization() == true) {
            return TriggerNormalizationIssue.TARGET_KIND_NORMALIZED
        }
        return TriggerNormalizationIssue.INAPPLICABLE_CONDITION
    }

    /**
     * UI・保存・実行前検証が共有する、イベント候補1体へ適用できる条件契約です。
     *
     * 正規化で除去される差（種別外・探索文脈・種類語彙）はエラーにしません。
     * 保存時に [forEvent] で除去されるため、互換読み込みを妨げないことが契約です。
     * 正規化後も残る値の不正（分類・素材・属性）だけをエラーにします。
     */
    fun validationProblem(event: EventTriggerKind): String? {
        val normalized = forEvent(event)
        if (normalized.blockType != null && CommandValueRules.material(normalized.blockType)?.isBlock != true) {
            return "対象ブロックの種類が不正です"
        }
        if (normalized.blockIdentity != null && normalized.blockIdentity.world.isBlank()) {
            return "対象ブロックのワールドが未設定です"
        }
        fun targetProblem(spec: TargetSpec?, playerOnly: Boolean): String? {
            if (spec == null) return null
            // 一時変数参照は起動前に解決できないため、互換読み込みでも拒否します。
            if (spec.kind == TargetKind.TEMPORARY) return "イベント条件に一時変数参照は使用できません"
            val validKinds = if (playerOnly) setOf(
                TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS, TargetKind.ALL_PLAYERS, TargetKind.RANDOM_PLAYER,
            ) else setOf(TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES)
            if (spec.kind !in validKinds) return "イベント条件の対象分類が不正です"
            // 探索文脈は正規化で除去されるため、ここでは属性値だけを検証します。
            // 種類差（NEARBY／ALL／RANDOM 等）は単体照合上同値として受け付けます。
            if (spec.entityType != null && !CommandValueRules.isEntityTypeId(spec.entityType)) {
                return "対象エンティティの種類が不正です"
            }
            if (spec.tag != null && !CommandValueRules.isTag(spec.tag)) return "対象タグが不正です"
            if (spec.name != null && spec.name.length > 256) return "対象名が長すぎます"
            if (spec.gameMode != null && spec.gameMode.uppercase() !in
                setOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")) return "対象ゲームモードが不正です"
            return null
        }
        return targetProblem(normalized.actorTarget, true) ?: targetProblem(normalized.entityTarget, false)
    }
}

/**
 * トリガー単体意味へ対象指定を正規化します。
 *
 * 慣用的な対象設定との互換のため読み込み時は探索語彙・探索条件を受け付けますが、
 * 保存・照合では属性絞り（種類・名前・タグ・ゲームモード）だけを残します。
 * 種類は単体代表（NEAREST）へ寄せ、距離・件数・順序・範囲・基準・一時参照・来歴は除去します。
 * 分類違い（TEMPORARY・他カテゴリ）は正規化せず検証エラーに委ねます。
 */
private fun normalizeTriggerTarget(spec: TargetSpec, playerOnly: Boolean): TargetSpec {
    // 一時変数参照・分類違いは除去対象ではなく検証エラーとします。正規化で隠しません。
    if (spec.kind == TargetKind.TEMPORARY) return spec
    val playerKinds = setOf(
        TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS,
        TargetKind.ALL_PLAYERS, TargetKind.RANDOM_PLAYER,
    )
    val entityKinds = setOf(TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES)
    if (playerOnly && spec.kind !in playerKinds) return spec
    if (!playerOnly && spec.kind !in entityKinds) return spec
    return spec.copy(
    kind = if (playerOnly) TargetKind.NEAREST_PLAYER else TargetKind.NEAREST_ENTITY,
    entityType = spec.entityType.takeIf { !playerOnly },
    minimumDistance = null,
    maximumDistance = null,
    limit = null,
    sort = null,
    gameMode = spec.gameMode,
    tag = spec.tag,
    name = spec.name,
    dx = null,
    dy = null,
    dz = null,
    tempName = null,
)
}

/** 正規化で除去される探索文脈を持つかを返します。警告文の特定に使います。 */
private fun TargetSpec.hasTriggerIneffectiveSetting(): Boolean =
    tempName != null ||
        minimumDistance != null || maximumDistance != null ||
        dx != null || dy != null || dz != null || limit != null ||
        sort != null

/** 単体代表への寄せが必要な探索語彙かを返します。警告文の特定に使います。 */
private fun TargetSpec.needsTriggerKindNormalization(): Boolean = kind != TargetKind.NEAREST_PLAYER &&
    kind != TargetKind.NEAREST_ENTITY

/**
 * issue #7 の起動方法の保存形式です。
 *
 * `.kcp` の `trigger` 宣言が正本であり、埋め込みJSONでは `trigger` オブジェクトに
 * 保持します。旧形式の `activation`＋`timer` 欄は読込時の移行で
 * この形式へ置き換えます。`intervalSeconds` は TIMER 以外では使用しません。
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
 * 保持値を保存時と同じ規則で正規化して返します（TIMER 以外に残った間隔・
 * イベント指定は実行意味を持たないため落とします）。
 */
fun DiskScript.effectiveTrigger(): ScriptTrigger = trigger.normalized()

/**
 * TIMER の間隔をtick値へ換算します。
 * Minecraftの内部スケジューラへ渡す値はこの境界でだけ算出します。
 * 0以下・非有限は既定秒へ、上限超過は上限秒へ寄せます（いずれも検証層で
 * エラーになる値であり、ここでは実行不能にしないためのクランプです）。
 */
fun ScriptTrigger.intervalTicks(): Long {
    val seconds = intervalSeconds
        .takeIf { it.isFinite() && it > 0.0 }
        ?.coerceAtMost(MAX_TIMER_SECONDS.toDouble())
        ?: DEFAULT_TIMER_SECONDS
    return CommandValueRules.secondsToTicks(seconds) ?: 1L
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
 * DISK_CALL は `diskId` 参照として動作するため、識別子と引数宣言だけを転記
 * します。宣言から消えた引数の古い値・変数指定は、実行時に残存キーを誤って
 * 使わないよう除去します。選択操作の副作用を一箇所へ集約し、GUI とテストで
 * 同じ規則を使います。
 */
fun CommandNode.applyCalledDisk(diskId: java.util.UUID, disk: DiskScript) {
    params["diskId"] = diskId.toString()
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
