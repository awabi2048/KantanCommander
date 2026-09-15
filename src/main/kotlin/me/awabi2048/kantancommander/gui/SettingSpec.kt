package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.TargetKind
import kotlin.math.roundToLong

/**
 * Gesture GUIの値仕様と表示操作の分離です。
 *
 * issue #18 の正本であり、`CommandDialogSpecs` の検証・既定値・nullable意味を
 * 複製せず、表示側の操作コントロールだけを振り分けます。保存・検証の正本は
 * `CommandDialogSpecs` と `CommandSettingsModel.setParameter` に残し、ここでは
 * 操作可否と刻み・範囲・表示形式だけを扱います。
 */
enum class GesturePresentation {
    /** 二値の直接切替えです。1クリックで確定します。 */
    CHECKBOX,

    /** 有界数値の段階操作です。[-][+]の離散操作で確定します。 */
    SLIDER,

    /** 有限候補の選択です。既存の選択木・選択カードを使います。 */
    CHOICE,

    /** メインハンド取得・試射等の作用操作です。 */
    ACTION,

    /** 名前・ID・自由文・任意精度等の明示入力です。Dialogを使います。 */
    INPUT_DIALOG,
}

/**
 * Sliderの範囲・刻み・表示形式です。
 *
 * 値はparams上の文字列表現と相互変換します。数値テンプレート参照（`${}`等）を
 * 含む値はSliderで編集せず、Dialog側へ委譲します。
 */
data class SliderSpec(
    val min: Double,
    val max: Double,
    val step: Double,
    val integer: Boolean,
    val defaultValue: String,
) {
    init {
        require(min <= max) { "Slider範囲が不正です: $min..$max" }
        require(step > 0.0 && step.isFinite()) { "Slider刻みが不正です: $step" }
    }
}

/**
 * Sliderの数値操作を一箇所へ集約します。
 *
 * 上下限・step丸め・浮動小数誤差・整数／小数の表示形式をここで固定し、
 * Gesture表示層とテストで共有します。Bukkitへ依存しません。
 */
object SliderValuePolicy {
    /** 現在値を範囲内へ寄せ、stepへ丸めます。 */
    fun clampAndRound(spec: SliderSpec, value: Double): Double {
        if (!value.isFinite()) return spec.defaultValue.toDoubleOrNull()?.coerceIn(spec.min, spec.max) ?: spec.min
        val clamped = value.coerceIn(spec.min, spec.max)
        if (spec.integer) return clamped.roundToLong().toDouble().coerceIn(spec.min, spec.max)
        // 浮動小数誤差の蓄積を防ぐため、step単位へ丸めます。
        val steps = ((clamped - spec.min) / spec.step).roundToLong()
        val rounded = spec.min + steps * spec.step
        // 表示上の -0.0 を避けます。
        val normalized = if (rounded == 0.0) 0.0 else rounded
        return normalized.coerceIn(spec.min, spec.max)
    }

    /** 保存文字列をSlider値へ変換します。失敗時は既定値へ落とします。 */
    fun parse(spec: SliderSpec, raw: String?): Double {
        val parsed = raw?.trim()?.toDoubleOrNull()?.takeIf(Double::isFinite)
        if (parsed == null) return spec.defaultValue.toDoubleOrNull() ?: spec.min
        return clampAndRound(spec, parsed)
    }

    /** Slider値を保存文字列へ変換します。 */
    fun format(spec: SliderSpec, value: Double): String {
        val rounded = clampAndRound(spec, value)
        return if (spec.integer) rounded.roundToLong().toString()
        else if (rounded == rounded.roundToLong().toDouble()) rounded.roundToLong().toString()
        else rounded.toString()
    }

    /** 現在値から1刻み進めた保存文字列を返します。 */
    fun stepBy(spec: SliderSpec, currentRaw: String?, steps: Int): String =
        format(spec, parse(spec, currentRaw) + steps * spec.step)

    /**
     * issue #18: バー上クリック位置の仮値を求めます。
     *
     * バーを `segments` 等分し、クリックされた区画の中央値を刻みへ丸めます。
     * DisplayEntity上で真のドラッグはできないため、区画クリックで連続操作を代替します。
     */
    fun valueForSegment(spec: SliderSpec, index: Int, segments: Int = SettingSpec.SLIDER_SEGMENTS): String {
        require(segments > 0) { "区画数が不正です: $segments" }
        val clamped = index.coerceIn(0, segments - 1)
        val span = spec.max - spec.min
        val raw = if (span <= 0.0) spec.min else spec.min + span * (clamped + 0.5) / segments
        return format(spec, raw)
    }

    /** テンプレート参照を含む値はSliderで扱いません。 */
    fun isTemplateReference(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val trimmed = raw.trim()
        return trimmed.contains("\${") || trimmed.contains("%{") || trimmed.contains("%")
    }
}

/**
 * 設定項目ごとの値仕様と表示振り分けです。
 *
 * 新規設定にSliderを置く場合は `SLIDER_FIELDS` へ行を追加します。
 * 範囲・刻み・既定値は `CommandDialogSpecs` の共有正本から導出するため、
 * ここでの手作業の同期は不要です。
 */
object SettingSpec {
    /** Sliderバーの区画数です。表示と値解決で共有します。 */
    const val SLIDER_SEGMENTS = 12

    /** Checkboxで直接切り替える二値項目です。 */
    private val CHECKBOX_FIELDS: Set<Pair<CommandType, String>> = setOf(
        CommandType.ENTITY_ACTION to "overwrite",
        CommandType.PLACE_ITEM to "overwrite",
        CommandType.CONDITION to "inverted",
    )

    /**
     * Sliderで段階操作する有界数値項目です。
     *
     * 現在Slider操作は経路から外しているため空集合とし、全数値項目を
     * Dialog入力で編集します。Sliderを復活させる場合はここへ行を追加します。
     * ここでは「どの項目にSliderを置くか」（UX判断）だけを保持し、
     * 範囲・刻み・既定値は `CommandDialogSpecs` の共有正本から導出します。
     * 検証範囲の変更は自動でSliderへ反映され、手作業の同期は不要です。
     * 時間系のtick整合は保存時検証に残し、刻みは整合済みの代表刻みです。
     */
    private val SLIDER_FIELDS: Set<Pair<CommandType, String>> = emptySet()

    /**
     * Slider仕様を共有正本から導出します。
     *
     * 範囲・刻みは `CommandDialogSpecs` の `NumericRange`、既定値は
     * `CommandType.defaults` を正とします。範囲外の任意精度値は
     * Dialogへ委譲するため、ここでは扱いません。
     */
    fun sliderSpecFor(node: CommandNode, fieldKey: String): SliderSpec? {
        if ((node.type to fieldKey) !in SLIDER_FIELDS) return null
        val range = CommandDialogSpecs.field(node, fieldKey)?.numericRange ?: return null
        // 下限値そのものを拒否する範囲では、刻み1つ分を操作下限とします。
        val min = if (range.exclusiveMin) range.min + range.step else range.min
        val default = node.type.defaults[fieldKey]?.takeIf(String::isNotBlank)
            ?: if (range.integer) min.roundToLong().toString() else min.toString()
        return SliderSpec(
            min = if (range.integer) min.roundToLong().toDouble() else min,
            max = if (range.integer) range.max.roundToLong().toDouble() else range.max,
            step = range.step,
            integer = range.integer,
            defaultValue = default,
        )
    }

    /** 指定項目がCheckbox対象かを返します。 */
    fun isCheckbox(type: CommandType, fieldKey: String): Boolean =
        CHECKBOX_FIELDS.contains(type to fieldKey)

    /**
     * 設定項目の表示操作を振り分けます。
     *
     * - Checkbox／SliderはGesture親画面で直接操作します。
     * - 有限候補・構造化設定は既存の選択木（CHOICE）を使います。
     * - メインハンド取得・試射等はACTIONです。
     * - 名前・ID・自由文・任意精度・座標記法はINPUT_DIALOGに残します。
     */
    fun presentationFor(node: CommandNode, fieldKey: String, descriptor: CommandSettingDescriptor): GesturePresentation {
        // テンプレート参照を含む数値はSliderへ載せず、Dialogで編集します。
        val slider = sliderSpecFor(node, fieldKey)
        if (slider != null && !SliderValuePolicy.isTemplateReference(node.params[fieldKey])) {
            return GesturePresentation.SLIDER
        }
        if (isCheckbox(node.type, fieldKey)) return GesturePresentation.CHECKBOX
        // データ設定値の操作は項目の型で振り分けます。
        // 有限候補は選択木、文字列・整数はDialog入力です。真偽値は
        // 統合選択画面のクリックで直接切り替えるため子画面を持ちません。
        if (descriptor.editor == CommandSettingEditor.ENTITY_PROPERTY_VALUE) {
            return GesturePresentation.CHOICE
        }
        return when (descriptor.editor) {
            CommandSettingEditor.TEXT -> GesturePresentation.INPUT_DIALOG
            CommandSettingEditor.ENTITY_OVERWRITE -> GesturePresentation.CHECKBOX
            CommandSettingEditor.CONDITION_INVERSION -> GesturePresentation.CHECKBOX
            CommandSettingEditor.TARGET,
            CommandSettingEditor.POSITION,
            CommandSettingEditor.FACING,
            CommandSettingEditor.LOCATION,
            CommandSettingEditor.CONDITION_KIND,
            CommandSettingEditor.CONDITION_DETAIL,
            CommandSettingEditor.DISPLAY_MODE,
            CommandSettingEditor.ENTITY_ACTION,
            CommandSettingEditor.ENTITY_DATA_ROOT,
            CommandSettingEditor.SUMMON_DATA_ROOT,
            CommandSettingEditor.ENTITY_ATTRIBUTE_SELECT,
            CommandSettingEditor.ENTITY_TAG_LIST,
            CommandSettingEditor.PLACE_DESTINATION,
            CommandSettingEditor.ENTITY_EQUIPMENT_SLOT,
            CommandSettingEditor.VARIABLE_SCOPE,
            CommandSettingEditor.VARIABLE_NAME,
            CommandSettingEditor.VARIABLE_PROPERTY,
            CommandSettingEditor.VARIABLE_TYPE,
            CommandSettingEditor.VARIABLE_OPERATION,
            CommandSettingEditor.VARIABLE_CHANGE_MODE,
            CommandSettingEditor.VARIABLE_VALUE,
            CommandSettingEditor.CAMERA_SHAKE_TYPE,
            CommandSettingEditor.SOUND_OPERATION,
            CommandSettingEditor.SOUND_SCOPE,
            CommandSettingEditor.SOUND_TARGET_POSITION,
            CommandSettingEditor.PARTICLE_DISPLAY,
            CommandSettingEditor.PARTICLE_GEOMETRY,
            CommandSettingEditor.BLOCK_OPERATION,
            // issue #11: 建造物名・回転・反転はいずれも選択木（CHOICE）です。
            CommandSettingEditor.STRUCTURE_NAME,
            CommandSettingEditor.STRUCTURE_ROTATION,
            CommandSettingEditor.STRUCTURE_MIRROR,
            // issue #24: 群操作・群名はいずれも選択木（CHOICE）です。
            CommandSettingEditor.ENTITY_GROUP_OPERATION,
            CommandSettingEditor.ENTITY_GROUP_NAME,
            -> GesturePresentation.CHOICE
        }
    }

    /**
     * 分類表の正本です。新規設定はこの規則に従います。
     *
     * - bounded numeric → SLIDER（範囲外・任意精度はDIALOGへ委譲）
     * - boolean flags → CHECKBOX（三値は別表現）
     * - enum／有限候補 → CHOICE
     * - name／tag／text／ID／座標記法／数式 → INPUT_DIALOG
     */
    fun classificationTable(): List<Triple<String, GesturePresentation, String>> = listOf(
        Triple("volume/pitch/intensity/level/count/seconds/duration/particleDensity/burstHeight/geometry数値", GesturePresentation.SLIDER, "有界数値は段階操作へ"),
        Triple("overwrite/inverted等の二値", GesturePresentation.CHECKBOX, "1クリック確定へ"),
        Triple("entity/sound/effect/particle/slot等の有限候補", GesturePresentation.CHOICE, "選択木へ"),
        Triple("名前/タグ/自由文/ID/座標記法/数式", GesturePresentation.INPUT_DIALOG, "明示入力のみDialogへ"),
    )
}

/**
 * 動的Targetに対する表示方針です。
 *
 * runtimeまで型が確定しない対象では、現在の推測だけを根拠に設定項目を
 * 隠しません。静的に「確実に不可能」と判定できる場合だけ非表示にし、
 * それ以外は表示＋適用条件の注記に留めます。
 */
object RuntimeDependentPolicy {
    /** runtimeまで型が確定しない対象かを返します。 */
    fun isRuntimeDependentTarget(kind: TargetKind?): Boolean = when (kind) {
        null -> false
        TargetKind.TEMPORARY,
        TargetKind.NEAREST_ENTITY,
        TargetKind.NEARBY_ENTITIES,
        -> true
        else -> false
    }

    /**
     * 設定項目を隠してよいか（確実に不可能か）を返します。
     *
     * runtime-dependentな対象に対してはfalseを返し、表示を維持します。
     * 検証側では「確実に不可能」と「runtime-dependent」を区別します。
     */
    fun canHide(node: CommandNode, targetKind: TargetKind?): Boolean =
        !isRuntimeDependentTarget(targetKind)
}
