package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.FireworkSettings
import me.awabi2048.kantancommander.model.ParticleGeometry
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
 * 新規設定を追加する場合はこの表へ行を追加し、`CommandDialogSpecs` の
 * 検証範囲と矛盾しない範囲・刻みを設定します。全項目の一括移行ではなく、
 * この表が新規設定の従うべき規則の正本になります。
 */
object SettingSpec {
    /** Checkboxで直接切り替える二値項目です。 */
    private val CHECKBOX_FIELDS: Set<Pair<CommandType, String>> = setOf(
        CommandType.ENTITY_ACTION to "overwrite",
        CommandType.CONDITION to "inverted",
    )

    /**
     * Sliderで段階操作する有界数値項目です。
     *
     * 範囲は `CommandDialogSpecs` の検証範囲内へ収め、範囲外の任意精度値は
     * Dialogへ委譲します。時間系のtick整合は保存時検証に残し、Sliderは
     * 整合済みの代表刻みだけを扱います。
     */
    fun sliderSpecFor(type: CommandType, fieldKey: String): SliderSpec? = when (type to fieldKey) {
        CommandType.PLAY_SOUND to "volume" ->
            SliderSpec(min = 0.0, max = 34.0, step = 1.0, integer = false, defaultValue = "1.0")
        CommandType.PLAY_SOUND to "pitch" ->
            SliderSpec(min = 0.5, max = 2.0, step = 0.1, integer = false, defaultValue = "1.0")
        CommandType.CAMERA_SHAKE to "intensity" ->
            SliderSpec(min = 0.1, max = 4.0, step = 0.1, integer = false, defaultValue = "1.0")
        CommandType.APPLY_EFFECT to "level" ->
            SliderSpec(min = 1.0, max = 10.0, step = 1.0, integer = true, defaultValue = "1")
        CommandType.GIVE_ITEM to "count" ->
            SliderSpec(min = 1.0, max = 64.0, step = 1.0, integer = true, defaultValue = "1")
        CommandType.FOR_START to "count" ->
            SliderSpec(min = 1.0, max = 32.0, step = 1.0, integer = true, defaultValue = "1")
        CommandType.WAIT to "seconds" ->
            SliderSpec(min = 1.0, max = 60.0, step = 1.0, integer = false, defaultValue = "1")
        CommandType.DISPLAY_TEXT to "staySeconds" ->
            SliderSpec(min = 1.0, max = 30.0, step = 1.0, integer = false, defaultValue = "3")
        CommandType.DISPLAY_TEXT to "fadeInSeconds" ->
            SliderSpec(min = 0.0, max = 10.0, step = 1.0, integer = false, defaultValue = "1")
        CommandType.DISPLAY_TEXT to "fadeOutSeconds" ->
            SliderSpec(min = 0.0, max = 10.0, step = 1.0, integer = false, defaultValue = "1")
        CommandType.APPLY_EFFECT to "seconds" ->
            SliderSpec(min = 1.0, max = 60.0, step = 1.0, integer = true, defaultValue = "30")
        CommandType.CAMERA_SHAKE to "seconds" ->
            SliderSpec(min = 1.0, max = 10.0, step = 1.0, integer = false, defaultValue = "5")
        CommandType.FIREWORK_LAUNCH to "burstHeight" ->
            SliderSpec(
                min = FireworkSettings.HEIGHT_MIN,
                max = FireworkSettings.HEIGHT_MAX,
                step = 5.0,
                integer = false,
                defaultValue = FireworkSettings.HEIGHT_DEFAULT.toString(),
            )
        CommandType.PARTICLE to "particleDensity" ->
            SliderSpec(min = 0.1, max = 2.0, step = 0.1, integer = false, defaultValue = "0.5")
        else -> null
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
        val slider = sliderSpecFor(node.type, fieldKey)
        if (slider != null && !SliderValuePolicy.isTemplateReference(node.params[fieldKey])) {
            return GesturePresentation.SLIDER
        }
        if (isCheckbox(node.type, fieldKey)) return GesturePresentation.CHECKBOX
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
            CommandSettingEditor.ENTITY_EQUIPMENT_SLOT,
            CommandSettingEditor.ENTITY_TAG_OPERATION,
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
        TargetKind.FIXED_ENTITY,
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

/**
 * 図形数値のSlider範囲です。形状別の検証範囲と共有します。
 *
 * params上の図形数値キー（radius／height／turns／step／axis等）は形状に
 * よって意味が変わるため、個別表ではなく範囲解決関数として扱います。
 */
object GeometrySliderPolicy {
    /** 図形数値キーのSlider範囲を返します。未知キーはnullです。 */
    fun sliderSpecFor(key: String): SliderSpec? = when (key) {
        ParticleGeometry.PARAM_RADIUS -> SliderSpec(0.5, ParticleGeometry.MAX_RADIUS, 0.5, false, "3.0")
        ParticleGeometry.PARAM_HEIGHT -> SliderSpec(1.0, ParticleGeometry.MAX_HEIGHT, 1.0, false, "5.0")
        ParticleGeometry.PARAM_TURNS -> SliderSpec(ParticleGeometry.MIN_TURNS, ParticleGeometry.MAX_TURNS, 0.25, false, "3.0")
        ParticleGeometry.PARAM_STEP -> SliderSpec(ParticleGeometry.MIN_STEP, ParticleGeometry.MAX_STEP, 0.1, false, "0.5")
        ParticleGeometry.PARAM_AXIS_YAW -> SliderSpec(-180.0, 180.0, 5.0, false, "0.0")
        ParticleGeometry.PARAM_AXIS_PITCH -> SliderSpec(-90.0, 90.0, 5.0, false, "0.0")
        ParticleGeometry.PARAM_ROTATION -> SliderSpec(ParticleGeometry.MIN_ANGLE, ParticleGeometry.MAX_ANGLE, 5.0, false, "0.0")
        else -> null
    }
}
