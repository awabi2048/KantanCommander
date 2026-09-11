package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityPropertyValueKind
import me.awabi2048.kantancommander.model.MAX_COMMAND_TIME_SECONDS
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.ParticleDisplayMode
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.FireworkSettings
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Registry
import org.bukkit.entity.Player

/**
 * インベントリGUI／ジェスチャーGUIで同一のテキスト入力仕様を提供します。
 *
 * ラベル・最大文字数・検証ルールをパラメータ名ごとにここへ集約することで、
 * GUIごとに入力仕様が揺れて「片方だけ検証されない」「片方だけ長い値が
 * 入力できない」といった差分が再発しないようにします。
 */
internal object CommandDialogSpecs {

    /**
     * プレイヤー入力の意味を、単なるテキスト欄の見た目から分離して保持します。
     *
     * 同じString型のparamsでも、数量・時間・ID・任意文字列では保存前に確認すべき
     * 契約が異なります。フィールド仕様にこの形式をメタデータとして持たせ、
     * Inventory/Gesture双方が同じ形式・検証・案内を参照する境界にします。
     */
    enum class InputFormat {
        ANY_STRING,
        INTEGER,
        QUANTITY,
        NUMBER,
        TIME,
        RESOURCE_ID,
        MATERIAL_ID,
        VARIABLE_NAME,
        TAG,
        EQUIPMENT_SLOT,
        NUMERIC_EXPRESSION,
    }

    /**
     * issue #18 の数値範囲・刻みの共有正本です。
     *
     * 現在Slider操作は経路から外しているため、検証の正本として使います。
     * Slider復活時はここから段階操作の範囲・刻みを導出します。
     * `step` は段階操作の刻みであり、検証の合否には使いません
     * （tick整合等の検証固有規則は `validate` 側に残します）。
     */
    data class NumericRange(
        val min: Double,
        val max: Double,
        val step: Double,
        val integer: Boolean = false,
        /** trueの場合は下限値そのものを拒否します（0超過等）。 */
        val exclusiveMin: Boolean = false,
    ) {
        init {
            require(min <= max) { "数値範囲が不正です: $min..$max" }
            require(step > 0.0 && step.isFinite()) { "刻みが不正です: $step" }
        }
    }

    /** テキスト入力欄の共通仕様。 */
    data class Spec(
        /** 入力欄のラベル（入力画面の見出し・案内へ使用）。 */
        val labelKey: LocalizationKey<String>,
        val maxLength: Int,
        /** 空欄以外の入力へ適用する検証。null なら合格、キーはエラーメッセージ。 */
        val validate: (String) -> LocalizationKey<String>? = { null },
        /** trueの場合は空欄も検証関数へ渡し、必須値として扱います。 */
        val required: Boolean = false,
        /** 入力欄へ設定される意味上の形式です。既定値は任意文字列です。 */
        val format: InputFormat = InputFormat.ANY_STRING,
        /** 形式・範囲を入力本文へ表示する説明キーです。 */
        val formatHintKey: LocalizationKey<String>? = null,
        /** 数値欄の範囲・刻みです。検証の正本であり、検証側が正とします。 */
        val numericRange: NumericRange? = null,
    ) {
        /** GUI固有の空欄スキップを廃止し、必須／任意の契約をここで一元適用します。 */
        fun validateInput(raw: String): LocalizationKey<String>? =
            if (raw.isBlank() && !required) null else validate(raw)
    }

    /**
     * 単一テキスト入力の共通プロンプトを生成します。
     *
     * 入力欄の初期値はMenuDialogInput.Text.initialによって表示されるため、
     * 本文へ同じ値を「現在値」として重ねて表示しません。本文は入力方法と
     * 形式・制約の案内だけを担当し、値そのものは入力欄を唯一の表示場所にします。
     */
    fun prompt(player: Player, spec: Spec): String = KcI18n.text(
        player,
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_VALUE_INPUT_SUFFIX,
        mapOf("label" to KcI18n.text(player, spec.labelKey)),
    )

    fun body(player: Player, spec: Spec): List<Component> = buildList {
        add(Component.text(prompt(player, spec)))
        // テキスト入力を伴う入力画面では、変数参照と色指定の例を入力欄より下へ
        // 表示します。完成済みの日本語文をここへ持たせず、localeごとのカタログ
        // から取得することで、Inventory/Gesture双方の案内を同じ契約にします。
        if (spec.maxLength >= 256) {
            add(
                Component.text(
                    KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_TEMPLATE_HINT,
                        // プレースホルダーを一段経由して、表示上の「${score}」を
                        // ローカライズ文字列内で別の置換対象と誤認させません。
                        mapOf("variable_name" to "{score}"),
                    ),
                    NamedTextColor.GRAY,
                ),
            )
        }
        spec.formatHintKey?.let { key ->
            add(Component.text(KcI18n.text(player, key), NamedTextColor.GRAY))
        }
    }

    fun input(
        player: Player,
        id: String,
        initial: String,
        spec: Spec,
        label: Component? = null,
    ): MenuDialogInput.Text =
        MenuDialogInput.Text(
            id = id,
            label = label ?: Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_LABEL)),
            initial = initial,
            maxLength = spec.maxLength,
        )

    /**
     * プログラムタイマーの入力契約を両GUIで共有します。
     * 小数秒（tick境界）を受け付け、下限は設けません。
     */
    val timerSeconds = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTERVAL,
        16,
        validate = { raw ->
            val value = CommandValueRules.parseFiniteDouble(raw)
            when {
                value == null || value <= 0.0 || value > MAX_TIMER_SECONDS.toDouble() -> {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_INVALID
                }
                !CommandValueRules.isTickAlignedSeconds(raw) -> {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TICK_INVALID
                }
                else -> null
            }
        },
        required = true,
        format = InputFormat.TIME,
        formatHintKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIME_FORMAT_HINT,
        // issue #18: 範囲の共有正本です。検証クロージャと一致させます。
        numericRange = NumericRange(0.0, MAX_TIMER_SECONDS.toDouble(), step = 1.0, exclusiveMin = true),
    )

    /** 検証済みのタイマー入力文字列を、保存用の秒数へ変換します。 */
    fun parseTimerSeconds(raw: String): Double? =
        raw.trim().toDoubleOrNull()?.takeIf(CommandValueRules::isTimerSeconds)

    /**
     * タイマー秒数を表示・初期値用に整形します。整数値は小数点を落とし、
     * 小数は末尾の0を落としてtick境界のまま表記します。
     */
    fun formatSeconds(seconds: Double): String = runCatching {
        java.math.BigDecimal.valueOf(seconds).stripTrailingZeros().toPlainString()
    }.getOrDefault(seconds.toString())

    /** タイマー入力の方法だけを表示します。現在の秒数は入力欄に表示します。 */
    fun timerBody(player: Player): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_BODY),
    )

    fun timerInput(player: Player, seconds: Double): MenuDialogInput.Text =
        input(
            player,
            id = "seconds",
            initial = formatSeconds(seconds),
            spec = timerSeconds,
            label = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTERVAL),
        )

    /** 座標入力の表示・入力長・有限値判定を両GUIで共有します。 */
    fun coordinateBody(player: Player): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_COORDINATE_PROMPT),
    )

    fun coordinateSpec(axis: String, allowNotation: Boolean = true): Spec =
        finiteCoordinate(axis, allowNotation)

    fun coordinateInputs(
        player: Player,
        x: String,
        y: String,
        z: String,
    ): List<MenuDialogInput.Text> = listOf(
        input(player, "x", x, coordinateSpec("x"), Component.text("X")),
        input(player, "y", y, coordinateSpec("y"), Component.text("Y")),
        input(player, "z", z, coordinateSpec("z"), Component.text("Z")),
    )

    /** 回転入力も座標入力と同じく、表示と有限値判定の境界を共有します。 */
    fun rotationBody(player: Player): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_ROTATION_PROMPT),
    )

    fun rotationSpec(axis: String): Spec = finiteRotation(axis)

    fun rotationInputs(player: Player, yaw: String, pitch: String): List<MenuDialogInput.Text> = listOf(
        input(player, "yaw", yaw, rotationSpec("yaw"), Component.text("ヨー")),
        input(player, "pitch", pitch, rotationSpec("pitch"), Component.text("ピッチ")),
    )

    /** 対象範囲の入力方法と制約だけを表示します。各軸の値は入力欄に表示します。 */
    fun rangeBody(player: Player): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_RANGE_BODY),
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_RANGE_CONSTRAINT),
    )

    fun rangeInputs(player: Player, dx: Double?, dy: Double?, dz: Double?): List<MenuDialogInput.Text> {
        val spec = requireNotNull(targetFilter("range"))
        return listOf(
            input(player, "dx", formatOptionalNumber(dx), spec, KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX)),
            input(player, "dy", formatOptionalNumber(dy), spec, KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY)),
            input(player, "dz", formatOptionalNumber(dz), spec, KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ)),
        )
    }

    /** 効果音の音量・ピッチの入力方法と制約だけを表示します。値は入力欄に表示します。 */
    fun soundParametersBody(player: Player): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SOUND_PARAMETERS_BODY),
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SOUND_PARAMETERS_CONSTRAINT),
    )

    fun soundParametersInputs(player: Player, volume: String, pitch: String): List<MenuDialogInput.Text> = listOf(
        input(
            player,
            "volume",
            volume,
            requireNotNull(field("volume")),
            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VOLUME),
        ),
        input(
            player,
            "pitch",
            pitch,
            requireNotNull(field("pitch")),
            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PITCH),
        ),
    )

    /** パーティクルの散布範囲・速度・個数を1つの設定項目で編集します。 */
    fun particleParametersBody(player: Player): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_PARTICLE_PARAMETERS_BODY),
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_PARTICLE_PARAMETERS_CONSTRAINT),
    )

    fun particleParametersInputs(
        player: Player,
        deltaX: String,
        deltaY: String,
        deltaZ: String,
        speed: String,
        count: String,
    ): List<MenuDialogInput.Text> = listOf(
        input(player, ParticleSettings.PARAM_DELTA_X, deltaX, particleSpread, KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX)),
        input(player, ParticleSettings.PARAM_DELTA_Y, deltaY, particleSpread, KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY)),
        input(player, ParticleSettings.PARAM_DELTA_Z, deltaZ, particleSpread, KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ)),
        input(player, ParticleSettings.PARAM_SPEED, speed, particleSpeed, KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SPEED)),
        input(player, ParticleSettings.PARAM_COUNT, count, positiveInteger(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT), KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT)),
    )

    /**
     * 両GUIで共有するダイアログ入力欄の中立表現です。
     *
     * Gesture Dialog（MenuDialogInput.Text）とInventory Form（FormTextField）は
     * 欄ごとの表示型が異なりますが、id・ラベル・初期値・検証仕様は同一です。
     * 入力欄の定義はここへ集約し、各画面は表示型への変換だけを担当します。
     */
    data class FieldInput(
        val id: String,
        val labelKey: LocalizationKey<String>,
        val initial: String,
        val spec: Spec,
    ) {
        fun toMenuInput(player: Player): MenuDialogInput.Text =
            MenuDialogInput.Text(
                id = id,
                label = Component.text(KcI18n.text(player, labelKey)),
                initial = initial,
                maxLength = spec.maxLength,
            )
    }

    /** 停止対象のカテゴリは空欄（全カテゴリ）またはSoundCategory名を受け付けます。 */
    val stopSoundCategory = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_SCOPE,
        32,
        validate = { raw ->
            if (raw.isBlank() || SoundSettings.isCategoryValue(raw)) {
                null
            } else {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STOP_SOUND_CATEGORY_INVALID
            }
        },
        required = false,
        format = InputFormat.ANY_STRING,
        formatHintKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STOP_SOUND_BODY,
    )

    /** 停止対象の効果音IDは空欄（全音）またはサウンドIDを受け付けます。 */
    val stopSoundId = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND,
        64,
        validate = { raw ->
            if (raw.isBlank() || CommandValueRules.isSoundId(raw)) {
                null
            } else {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            }
        },
        required = false,
        format = InputFormat.RESOURCE_ID,
    )

    /**
     * 効果音設定の束ね入力を操作ごとに生成します。
     *
     * 再生はID・音量・ピッチ、停止はID（任意）・カテゴリ（任意）です。入力欄の
     * idはparamsキーと一致させ、保存側でそのままsetParametersへ渡せるようにします。
     */
    fun soundSettingInputs(node: CommandNode): List<FieldInput> =
        when (SoundSettings.operation(node)) {
            SoundOperation.PLAY -> listOf(
                FieldInput(
                    SoundSettings.PARAM_SOUND,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND,
                    node.string(SoundSettings.PARAM_SOUND, "minecraft:block.note_block.harp"),
                    requireNotNull(field("sound")),
                ),
                FieldInput(
                    SoundSettings.PARAM_VOLUME,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VOLUME,
                    node.string(SoundSettings.PARAM_VOLUME, "1.0"),
                    requireNotNull(field("volume")),
                ),
                FieldInput(
                    SoundSettings.PARAM_PITCH,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PITCH,
                    node.string(SoundSettings.PARAM_PITCH, "1.0"),
                    requireNotNull(field("pitch")),
                ),
            )
            SoundOperation.STOP -> listOf(
                FieldInput(
                    SoundSettings.PARAM_SOUND,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND,
                    node.string(SoundSettings.PARAM_SOUND),
                    stopSoundId,
                ),
                FieldInput(
                    SoundSettings.PARAM_CATEGORY,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_SCOPE,
                    node.string(SoundSettings.PARAM_CATEGORY),
                    stopSoundCategory,
                ),
            )
        }

    /** 効果音設定の入力方法と制約だけを表示します。値は入力欄に表示します。 */
    fun soundSettingBody(node: CommandNode, player: Player): List<Component> =
        when (SoundSettings.operation(node)) {
            SoundOperation.PLAY -> soundParametersBody(player)
            SoundOperation.STOP -> listOf(
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STOP_SOUND_BODY),
            )
        }

    /**
     * パーティクル種類と詳細データの束ね入力を生成します。
     *
     * VIBRATION／TRAILは終点XYZを1群として束ね、4項目以内に収めます。それ以外の
     * 種類は従来の単一詳細文字列のまま扱います。入力欄idは保存キー（particle／
     * particleData）または合成キー（vib*／trail*）であり、保存側で単一詳細文字列
     * へ合成してからsetParameterへ渡します。
     */
    fun particleInputs(node: CommandNode): List<FieldInput> {
        val particle = ParticleSettings.particle(node)
        val idInput = FieldInput(
            ParticleSettings.PARAM_PARTICLE,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE,
            particle?.name ?: "FLAME",
            requireNotNull(field(node, ParticleSettings.PARAM_PARTICLE)),
        )
        if (particle == null || !ParticleSettings.requiresData(particle)) return listOf(idInput)
        return listOf(idInput) + particleDetailInputs(node, particle)
    }

    private fun particleDetailInputs(
        node: CommandNode,
        particle: org.bukkit.Particle,
    ): List<FieldInput> {
        val raw = node.string(ParticleSettings.PARAM_DATA)
        return when (particle.dataType) {
            org.bukkit.Vibration::class.java -> {
                val parts = vibrationParts(raw)
                listOf(
                    FieldInput("vibX", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX, parts[0], coordinateSpec("x", allowNotation = false)),
                    FieldInput("vibY", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY, parts[1], coordinateSpec("y", allowNotation = false)),
                    FieldInput("vibZ", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ, parts[2], coordinateSpec("z", allowNotation = false)),
                    FieldInput(
                        "vibT",
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_TICKS,
                        parts[3],
                        integerRange(
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_TICKS,
                            0..Int.MAX_VALUE,
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID,
                        ),
                    ),
                )
            }
            org.bukkit.Particle.Trail::class.java -> {
                val parts = trailParts(raw)
                listOf(
                    FieldInput("trailX", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX, parts[0], coordinateSpec("x", allowNotation = false)),
                    FieldInput("trailY", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY, parts[1], coordinateSpec("y", allowNotation = false)),
                    FieldInput("trailZ", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ, parts[2], coordinateSpec("z", allowNotation = false)),
                    FieldInput(
                        "trailColor",
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_COLOR,
                        parts[3],
                        Spec(
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_COLOR,
                            32,
                            validate = { value ->
                                if (runCatching {
                                        ParticleSettings.parseData(
                                            particle,
                                            "${parts[0]} ${parts[1]} ${parts[2]} $value ${parts[4]}",
                                        ).getOrThrow()
                                    }.isFailure
                                ) {
                                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_PARTICLE_DATA_INVALID
                                } else null
                            },
                            required = true,
                            format = InputFormat.ANY_STRING,
                        ),
                    ),
                    FieldInput(
                        "trailT",
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_TICKS,
                        parts[4],
                        integerRange(
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_TICKS,
                            0..Int.MAX_VALUE,
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID,
                        ),
                    ),
                )
            }
            else -> listOf(
                FieldInput(
                    ParticleSettings.PARAM_DATA,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DATA,
                    raw,
                    particleData(node),
                ),
            )
        }
    }

    /** 詳細文字列をVIBRATIONの4群（終点XYZ＋到達tick）へ分解します。 */
    fun vibrationParts(raw: String): List<String> {
        val tokens = raw.trim().split(Regex("[,\\s]+")).filter(String::isNotBlank)
        return listOf(
            tokens.getOrElse(0) { "" },
            tokens.getOrElse(1) { "" },
            tokens.getOrElse(2) { "" },
            tokens.getOrElse(3) { "" },
        )
    }

    /** 詳細文字列をTRAILの5群（終点XYZ＋色＋表示tick）へ分解します。 */
    fun trailParts(raw: String): List<String> {
        val tokens = raw.trim().split(Regex("[,\\s]+")).filter(String::isNotBlank)
        if (tokens.size < 5) return listOf("", "", "", "", "")
        // 色は「#RRGGBB」1トークンまたは「R G B [A]」の可変長です。先頭3・末尾1を
        // 固定し、残りを色として復元します。
        val duration = tokens.last()
        val head = tokens.dropLast(1)
        if (head.size < 4) return listOf("", "", "", "", duration)
        val color = head.drop(3).joinToString(" ")
        return listOf(head[0], head[1], head[2], color, duration)
    }

    /** 分解したVIBRATION入力を単一詳細文字列へ合成します。 */
    fun composeVibrationData(values: Map<String, String>): String =
        listOf(values["vibX"], values["vibY"], values["vibZ"], values["vibT"])
            .joinToString(" ") { it?.trim().orEmpty() }.trim()

    /** 分解したTRAIL入力を単一詳細文字列へ合成します。 */
    fun composeTrailData(values: Map<String, String>): String =
        listOf(values["trailX"], values["trailY"], values["trailZ"], values["trailColor"], values["trailT"])
            .joinToString(" ") { it?.trim().orEmpty() }.trim()

    /**
     * 図形数値の入力欄定義です。GUI表示型に依存しないため、両GUIで共有します。
     *
     * 終端位置（LINE／BOX）は位置選択画面で編集するため、ここには含めません。
     * `step` は段階操作の刻みであり、検証には使いません。
     */
    data class GeometryNumberField(
        val key: String,
        val labelKey: LocalizationKey<String>,
        val range: ClosedFloatingPointRange<Double>,
        val step: Double = 1.0,
    )

    /** 現形状が必要とする数値欄の一覧を返します。 */
    fun geometryNumberFields(shape: ParticleShape): List<GeometryNumberField> {
        fun number(
            key: String,
            labelKey: LocalizationKey<String>,
            range: ClosedFloatingPointRange<Double>,
            step: Double = 1.0,
        ) = GeometryNumberField(key, labelKey, range, step)
        val radius = {
            number(
                ParticleGeometry.PARAM_RADIUS,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_RADIUS,
                0.0..ParticleGeometry.MAX_RADIUS,
                step = 0.5,
            )
        }
        val axis = listOf(
            number(
                ParticleGeometry.PARAM_AXIS_YAW,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_AXIS_YAW,
                -180.0..180.0,
                step = 5.0,
            ),
            number(
                ParticleGeometry.PARAM_AXIS_PITCH,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_AXIS_PITCH,
                -90.0..90.0,
                step = 5.0,
            ),
            number(
                ParticleGeometry.PARAM_ROTATION,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_ROTATION,
                ParticleGeometry.MIN_ANGLE..ParticleGeometry.MAX_ANGLE,
                step = 5.0,
            ),
        )
        val step = {
            number(
                ParticleGeometry.PARAM_STEP,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_STEP,
                ParticleGeometry.MIN_STEP..ParticleGeometry.MAX_STEP,
                step = 0.1,
            )
        }
        val shapeFields = when (shape) {
            ParticleShape.LINE -> emptyList()
            ParticleShape.CIRCLE -> listOf(radius()) + axis
            ParticleShape.ARC -> listOf(
                radius(),
                number(
                    ParticleGeometry.PARAM_START_ANGLE,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_START_ANGLE,
                    ParticleGeometry.MIN_ANGLE..ParticleGeometry.MAX_ANGLE,
                    step = 5.0,
                ),
                number(
                    ParticleGeometry.PARAM_END_ANGLE,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_END_ANGLE,
                    ParticleGeometry.MIN_ANGLE..ParticleGeometry.MAX_ANGLE,
                    step = 5.0,
                ),
            ) + axis
            ParticleShape.BOX -> emptyList()
            ParticleShape.SPHERE -> listOf(radius()) + axis
            ParticleShape.HELIX -> listOf(
                radius(),
                number(
                    ParticleGeometry.PARAM_HEIGHT,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_HEIGHT,
                    0.0..ParticleGeometry.MAX_HEIGHT,
                    step = 1.0,
                ),
                number(
                    ParticleGeometry.PARAM_TURNS,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_TURNS,
                    ParticleGeometry.MIN_TURNS..ParticleGeometry.MAX_TURNS,
                    step = 0.25,
                ),
            ) + axis
            ParticleShape.POINT_SCATTER -> emptyList()
        }
        return shapeFields + step()
    }

    /**
     * 図形数値の束ね入力を現形状ごとに生成します。
     *
     * 入力欄idはparamsキーと一致させ、保存側でそのままsetParametersへ渡せる
     * ようにします。
     */
    fun geometryInputs(node: CommandNode): List<FieldInput> {
        val shape = ParticleGeometry.shape(node)
        return geometryNumberFields(shape).map { field ->
            FieldInput(
                field.key,
                field.labelKey,
                node.string(field.key, node.type.defaults[field.key].orEmpty()),
                decimalRange(
                    field.labelKey,
                    field.range,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_GEOMETRY_NUMBER_INVALID,
                    step = field.step,
                ),
            )
        }
    }

    /** 図形数値の入力方法と制約だけを表示します。値は入力欄に表示します。 */
    fun geometryBody(player: Player): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_GEOMETRY_NUMBERS_BODY),
    )

    /** 密度の入力仕様です。合意範囲（0超過・2.0以下）を受け付けます。 */
    val particleDensity = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DENSITY,
        16,
        validate = { raw ->
            if (!isNumericTemplate(raw) && !ParticleGeometry.isDensityValue(raw)) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_PARTICLE_DENSITY_INVALID
            } else null
        },
        required = true,
        format = InputFormat.NUMBER,
        formatHintKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_PARTICLE_DENSITY_BODY,
        // issue #18: 範囲の共有正本です。検証クロージャ（0超過・2.0以下）と一致させます。
        numericRange = NumericRange(0.0, ParticleGeometry.DENSITY_MAX, step = 0.1, exclusiveMin = true),
    )

    /** 打ち上げ高さの入力仕様です。0〜320ブロックを受け付けます。 */
    val burstHeight = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BURST_HEIGHT,
        16,
        validate = { raw ->
            if (!isNumericTemplate(raw) && !FireworkSettings.isBurstHeight(raw)) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_BURST_HEIGHT_INVALID
            } else null
        },
        required = true,
        format = InputFormat.NUMBER,
        formatHintKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_BURST_HEIGHT_BODY,
        // issue #18: 範囲の共有正本です。検証委譲先と一致させます。
        numericRange = NumericRange(
            FireworkSettings.HEIGHT_MIN,
            FireworkSettings.HEIGHT_MAX,
            step = 5.0,
        ),
    )
    fun particleData(node: CommandNode): Spec = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DATA,
        512,
        validate = { raw ->
            val particle = ParticleSettings.particle(node)
            if (particle == null || ParticleSettings.parseData(particle, raw).isFailure) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_PARTICLE_DATA_INVALID
            } else null
        },
        required = ParticleSettings.particle(node)?.let(ParticleSettings::requiresData) ?: true,
        format = InputFormat.ANY_STRING,
        formatHintKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_PARTICLE_DATA_BODY,
    )

    private fun formatOptionalNumber(value: Double?): String = value?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    }.orEmpty()

    fun finiteDouble(raw: String): Double? = CommandValueRules.parseFiniteDouble(raw)

    fun finiteFloat(raw: String): Float? = raw.trim().toFloatOrNull()?.takeIf(Float::isFinite)

    /**
     * issue #4 のDialog記法を解析します。先頭の `~` は相対、`^` はローカル、
     * 無印は絶対として、残りを有限Doubleへ変換します。`~`・`^` 単体は
     * 差分0として受け付けます。不正な入力はnullを返します。
     */
    fun parseCoordinateComponent(raw: String): Pair<CoordinateSemantics, Double>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val semantics = when {
            trimmed.startsWith("~") -> CoordinateSemantics.RELATIVE
            trimmed.startsWith("^") -> CoordinateSemantics.LOCAL
            else -> CoordinateSemantics.ABSOLUTE
        }
        val number = trimmed.removePrefix("~").removePrefix("^").trim()
        val value = number.takeIf(String::isNotEmpty)?.toDoubleOrNull() ?: 0.0.takeIf { number.isEmpty() }
        if (value == null || !value.isFinite()) return null
        return semantics to value
    }

    /**
     * issue #4 の角度欄のDialog記法を解析します。`~` は相対、無印は絶対です。
     * 角度にローカルは基準が定義できないため `^` は拒否します。
     */
    fun parseAngleComponent(raw: String): Pair<CoordinateSemantics, Float>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("^")) return null
        val semantics = if (trimmed.startsWith("~")) CoordinateSemantics.RELATIVE else CoordinateSemantics.ABSOLUTE
        val number = trimmed.removePrefix("~").trim()
        val value = number.takeIf(String::isNotEmpty)?.toFloatOrNull() ?: 0f.takeIf { number.isEmpty() }
        if (value == null || !value.isFinite()) return null
        return semantics to value
    }

    /** 保存値の意味付けをDialogの初期表示へ戻します（`~5`・`^5`・`5`）。 */
    fun formatCoordinateComponent(semantics: CoordinateSemantics, value: Double): String = when (semantics) {
        CoordinateSemantics.ABSOLUTE -> value.toString()
        CoordinateSemantics.RELATIVE -> "~$value"
        CoordinateSemantics.LOCAL -> "^$value"
    }

    /** 保存値の意味付けをDialogの初期表示へ戻します（`~90`・`90`）。 */
    fun formatAngleComponent(semantics: CoordinateSemantics, value: Float): String = when (semantics) {
        CoordinateSemantics.RELATIVE -> "~$value"
        else -> value.toString()
    }

    private val particleSpread = decimalRange(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX,
        -Double.MAX_VALUE..Double.MAX_VALUE,
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT,
    )

    private val particleSpeed = decimalRange(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SPEED,
        0.0..Double.MAX_VALUE,
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT,
    )

    /** Inventory/Gestureの両方で、保存前に同じ入力正規化を行います。 */
    fun normalize(fieldKey: String, raw: String): String = raw.trim().let {
        if (fieldKey in NAMESPACED_ID_FIELDS) it.lowercase() else it
    }

    /** 表示方式ごとの説明と入力形式だけを表示する時間入力画面の本文を生成します。 */
    fun durationBody(
        player: Player,
        mode: String,
    ): List<Component> {
        return listOf(
            KcI18n.component(
                player,
                if (mode == "actionbar") {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ACTIONBAR_DURATION_BODY
                } else {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_BODY
                },
            ),
            Component.text(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIME_FORMAT_HINT),
                NamedTextColor.GRAY,
            ),
        )
    }

    /** 表示時間3項目の入力欄を共通仕様から生成します。 */
    fun durationInputs(player: Player, fadeIn: String, stay: String, fadeOut: String): List<MenuDialogInput.Text> {
        return listOf(
            input(
                player,
                "fadeInSeconds",
                fadeIn,
                requireNotNull(field("fadeInSeconds")),
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_IN),
            ),
            input(
                player,
                "staySeconds",
                stay,
                requireNotNull(field("staySeconds")),
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STAY),
            ),
            input(
                player,
                "fadeOutSeconds",
                fadeOut,
                requireNotNull(field("fadeOutSeconds")),
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_OUT),
            ),
        )
    }

    /** 対象フィルタ系。空欄は「指定解除」として常に合格です。 */
    fun targetFilter(parameter: String): Spec? = when (parameter) {
        "entityType" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_TYPE,
            64,
            { raw ->
                if (!registeredFieldId("entityType", raw)) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_ENTITY_TYPE_FORMAT
                } else null
            },
            format = InputFormat.RESOURCE_ID,
        )
        "distance" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE,
            64,
            { raw ->
                val value = CommandValueRules.parseFiniteDouble(raw)
                // TargetSpecへ保存する距離はDouble型の構造化値です。生の参照式を
                // 受け付けると入力画面では成功しても、保存時にDoubleへ変換できず
                // 消えるため、参照可能な数値欄（params保存）とは契約を分けます。
                if (value == null || !value.isFinite() || value < 0.0) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID
                } else null
            },
            format = InputFormat.NUMBER,
        )
        "range" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_RANGE,
            64,
            { raw ->
                val value = CommandValueRules.parseFiniteDouble(raw)
                // TargetSpecのdx/dy/dzは実行時に必ず有限Doubleとして保持する型付き値です。
                // 文字列テンプレートをここで許すと、Gesture側のDouble保存時に参照式が
                // 消えるため、型付きセレクター欄では保存可能な数値だけを受け付けます。
                if (value == null || !value.isFinite() || value < 0.0) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID
                } else null
            },
            format = InputFormat.NUMBER,
        )
        "limit" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LIMIT,
            64,
            { raw ->
                if (!isPositiveInteger(raw)) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID else null
            },
            format = InputFormat.QUANTITY,
        )
        "tag" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG,
            64,
            validate = { raw ->
                // タグは単一の入力値として検証し、カンマを区切り文字として解釈しません。
                if (VariableTemplate.references(raw).isNotEmpty() && !VariableTemplate.hasMalformedReference(raw)) {
                    null
                } else if (!CommandValueRules.isTag(raw)) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_TAG_FORMAT
                } else null
            },
            format = InputFormat.TAG,
        )
        "name" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME,
            256,
            { raw ->
                when {
                    raw.length > 256 -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_NAME_LENGTH
                    VariableTemplate.hasMalformedReference(raw) -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                    else -> null
                }
            },
            format = InputFormat.ANY_STRING,
        )
        else -> null
    }

    /** プログラム名の共通仕様。 */
    val programName = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME,
        256,
        validate = { raw ->
            if (raw.isBlank() || raw.length > 256) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_NAME_LENGTH else null
        },
        required = true,
        format = InputFormat.ANY_STRING,
    )

    /** 変数名（保存名）の共通仕様。 */
    val variableName = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE,
        64,
        validate = { raw ->
            if (!CommandValueRules.isVariableName(raw)) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_VARIABLE_NAME
            } else null
        },
        required = true,
        format = InputFormat.VARIABLE_NAME,
    )

    /**
     * 一覧から編集するワールド内変数の値入力仕様です。
     *
     * ワールド内変数は保存層でNUMBER／STRINGの型を固定するため、画面側でも
     * 型ごとの入力境界をここへ集約します。入力欄のmaxLengthだけに依存せず、
     * 応答を直接組み立てた場合も同じ検証を通るよう、文字列長も保存前に確認します。
     */
    fun worldVariableValue(type: VariableType): Spec = when (type) {
        VariableType.NUMBER -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
            32,
            validate = { raw ->
                if (CommandValueRules.parseFiniteDouble(raw) == null) {
                    worldVariableValueInvalid(VariableType.NUMBER)
                } else null
            },
            required = true,
            format = InputFormat.NUMBER,
        )
        VariableType.STRING -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
            CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH,
            validate = { raw ->
                if (raw.length > CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH ||
                    VariableTemplate.hasMalformedReference(raw)
                ) {
                    worldVariableValueInvalid(VariableType.STRING)
                } else null
            },
            required = false,
            format = InputFormat.ANY_STRING,
        )
    }

    /** ワールド内変数の値編集Dialogで表示する案内を、保存型ごとに解決します。 */
    fun worldVariableValueBody(type: VariableType): LocalizationKey<String> = when (type) {
        VariableType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_WORLD_VARIABLE_VALUE_NUMBER_BODY
        VariableType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_WORLD_VARIABLE_VALUE_STRING_BODY
    }

    /** 値編集の入力エラーも型別に解決し、利用者が修正方法を判断できるようにします。 */
    fun worldVariableValueInvalid(type: VariableType): LocalizationKey<String> = when (type) {
        VariableType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_WORLD_VARIABLE_VALUE_NUMBER_INVALID
        VariableType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_WORLD_VARIABLE_VALUE_STRING_INVALID
    }

    /** 符号付き整数（条件の比較値など）。 */
    val signedInteger = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        64,
        { raw -> if (raw.toLongOrNull() == null) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID else null },
        format = InputFormat.INTEGER,
    )

    /** 条件値は参照先変数の型に従うため、入力欄では文字列を保持します。 */
    val conditionValue = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        512,
        format = InputFormat.ANY_STRING,
    )

    /** 条件種別に応じた比較値の形式を返し、数値条件だけを数値欄へ切り替えます。 */
    fun conditionValueSpec(node: CommandNode): Spec =
        field(node, "value") ?: conditionValue

    /**
     * 一時変数 NUMBER の値入力です。有限doubleまたは単一 `%{name}%` 参照を受け付けます。
     * ワールド変数 `${name}` も数値型として解決できるため同様に許可します。
     */
    val tempNumberValue = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        64,
        validate = { raw ->
            if (!isNumericTemplate(raw) && CommandValueRules.parseFiniteDouble(raw) == null) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
        required = true,
        format = InputFormat.NUMBER,
    )

    /** 一時変数 STRING の値入力です。不正参照記法だけを拒否します。 */
    val tempStringValue = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        512,
        validate = { raw ->
            if (hasMalformedAnyReference(raw)) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
        required = false,
        format = InputFormat.ANY_STRING,
    )

    /** 一時位置の座標入力です。数値リテラルまたは単一変数参照を受け付けます。 */
    val tempCoordinate = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        64,
        validate = { raw ->
            if (!isNumericTemplate(raw) && CommandValueRules.parseFiniteDouble(raw) == null) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_COORDINATES_INVALID
            } else null
        },
        required = true,
        format = InputFormat.NUMBER,
    )

    /** 一時エンティティの実体ID入力です。実行時にワールド内解決できるUUIDを要求します。 */
    val tempEntityId = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        64,
        validate = { raw ->
            if (runCatching { java.util.UUID.fromString(raw.trim()) }.isFailure) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
        required = true,
        format = InputFormat.ANY_STRING,
    )

    /**
     * コマンド種別を含めてフィールド入力仕様を解決します。
     *
     * 同じ `seconds` でもWAIT／効果／カメラシェイクで保存型と許容範囲が異なるため、
     * コマンド編集時は必ずCommandNodeを渡します。フィールド名だけのAPIは、コマンド種別に
     * 依存しない表示時間の共通入力など、型が確定している呼び出しに限定します。
     * これによりInventory GUIとGesture GUIが別々の分岐で数値型を解釈する状態を防ぎます。
     */
    fun field(
        node: CommandNode,
        fieldKey: String,
    ): Spec? {
        // issue #32: 動き設定の成分は有限数値のDialog入力です。
        // Sliderは使わず（#10の方針）、範囲制限も設けません。
        if (node.type == CommandType.ENTITY_ACTION && fieldKey in setOf("motionX", "motionY", "motionZ")) {
            return motionComponent(fieldKey)
        }
        // issue #32: エンティティ操作の入力仕様です。
        // 真偽値・有限候補はDialogを使わずCheckbox／選択木で編集するため、
        // ここではID・数値・文字列の明示入力だけを定義します（#18）。
        if (node.type == CommandType.ENTITY_OPERATION) {
            return when (fieldKey) {
                "tag" -> field("tag")
                EntityAttributeSupport.PARAM_ATTRIBUTE -> entityAttributeId
                EntityAttributeSupport.PARAM_MODIFIER_KEY -> attributeModifierKey
                EntityAttributeSupport.PARAM_AMOUNT -> attributeModifierAmount
                EntityDataSchema.PARAM_VALUE -> entityPropertyValue(node)
                else -> field(fieldKey)
            }
        }
        if (node.type == CommandType.PARTICLE) {
            return when (fieldKey) {
                ParticleSettings.PARAM_PARTICLE -> Spec(
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE,
                    64,
                    validate = { raw ->
                        if (runCatching { org.bukkit.Particle.valueOf(raw.trim()) }.isFailure) {
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                        } else null
                    },
                    required = true,
                    format = InputFormat.ANY_STRING,
                )
                ParticleSettings.PARAM_DATA -> particleData(node)
                ParticleSettings.PARAM_DELTA_X,
                ParticleSettings.PARAM_DELTA_Y,
                ParticleSettings.PARAM_DELTA_Z,
                -> particleSpread
                ParticleSettings.PARAM_SPEED -> particleSpeed
                ParticleSettings.PARAM_COUNT -> positiveInteger(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT)
                "particleDensity" -> particleDensity
                else -> field(fieldKey)
            }
        }
        if (node.type == CommandType.FOR_START && fieldKey == "count") {
            return positiveInteger(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_REPEAT_COUNT)
        }
        if (node.type == CommandType.FIREWORK_LAUNCH && fieldKey == "burstHeight") {
            return burstHeight
        }
        if (fieldKey == "name" && node.type == CommandType.VARIABLE) return variableName
        if (fieldKey == "value" && node.type == CommandType.VARIABLE) {
            val type = runCatching { VariableType.valueOf(node.string("type")) }
                .getOrDefault(VariableType.NUMBER)
            val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }
                .getOrDefault(VariableOperation.DEFINE)
            val changeMode = runCatching { VariableChangeMode.valueOf(node.string("changeMode")) }
                .getOrDefault(VariableChangeMode.ASSIGN)
            return variableValue(
                type = if (operation == VariableOperation.CHANGE) null else type,
                operation = operation,
                changeMode = changeMode,
            )
        }
        if (node.type == CommandType.TEMP_SET) {
            if (fieldKey == "name") return variableName
            val tempType = TemporaryVariableType.parse(
                node.string("tempType", TemporaryVariableType.NUMBER.name),
            ) ?: TemporaryVariableType.NUMBER
            when (fieldKey) {
                "value" -> return when (tempType) {
                    TemporaryVariableType.NUMBER -> tempNumberValue
                    TemporaryVariableType.STRING -> tempStringValue
                    else -> null
                }
                "x", "y", "z" -> return tempCoordinate
                "entityId" -> return tempEntityId
                "volume" -> return field("volume")
                "pitch" -> return field("pitch")
                "level" -> return field("level")
                "seconds" -> return field("seconds")
                "sound" -> return field("sound")
                "effect" -> return field("effect")
                "item" -> return field("item")
                "block" -> return block
            }
        }
        if (node.type == CommandType.CONDITION && fieldKey == "value" &&
            node.conditionSubject() == me.awabi2048.kantancommander.model.ConditionSubject.VARIABLE
        ) {
            return conditionNumberValue
        }
        return when (node.type) {
            CommandType.BLOCK_OPERATION -> if (fieldKey == "block") placementBlock else field(fieldKey)
            CommandType.WAIT -> if (fieldKey == "seconds") {
                timeRange(
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
                    minimum = 0.0,
                    exclusiveMinimum = true,
                    invalidKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID,
                )
            } else field(fieldKey)
            CommandType.APPLY_EFFECT -> when (fieldKey) {
                "level" -> integerRange(
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LEVEL,
                    1..255,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_LEVEL_INVALID,
                )
                "seconds" -> integerRange(
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
                    1..MAX_EFFECT_SECONDS,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_EFFECT_SECONDS_INVALID,
                    format = InputFormat.TIME,
                )
                else -> field(fieldKey)
            }
            CommandType.CAMERA_SHAKE -> if (fieldKey == "seconds") {
                decimalRange(
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
                    1.0..10.0,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CAMERA_SHAKE_SECONDS_INVALID,
                    format = InputFormat.TIME,
                )
            } else field(fieldKey)
            else -> field(fieldKey)
        }
    }

    /**
     * 正の整数として扱う入力の共通判定です。
     *
     * `toIntOrNull() > 0` だけでは `+1` を受け入れてしまい、入力形式が
     * 「符号付き整数」になります。個数・上限・秒数は符号を持たないため、
     * ASCII数字だけを許可し、保存先のInt範囲も同時に検証します。
     */
    internal fun isPositiveInteger(raw: String): Boolean =
        CommandValueRules.parsePositiveInt(raw) != null

    /** ブロックID（条件のブロック状態など）。マテリアルとして解決できることを検証します。 */
    val block = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK,
        64,
        validate = { raw ->
            if (CommandValueRules.material(raw) == null) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
        required = true,
        format = InputFormat.MATERIAL_ID,
    )

    /** ブロック操作は配置可能なブロック値（AIRを含む）として専用の規則を使います。 */
    private val placementBlock = block.copy(
        validate = { raw ->
            if (CommandValueRules.placementMaterial(raw) == null) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
    )

    /**
     * issue #32: 動き設定の成分入力です。
     *
     * 有限数値だけを要求し、範囲制限は設けません。Sliderは使わず（#10の方針）、
     * 検証の正本は保存前検証とこの仕様で共有します。
     */
    private fun motionComponent(fieldKey: String): Spec {
        val labelKey = when (fieldKey) {
            "motionY" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_Y
            "motionZ" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_Z
            else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_X
        }
        return Spec(
            labelKey,
            16,
            validate = { raw ->
                if (CommandValueRules.parseFiniteDouble(raw) == null) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_GEOMETRY_NUMBER_INVALID
                } else null
            },
            required = true,
            format = InputFormat.NUMBER,
        )
    }

    /** issue #32: 操作するattributeのID入力です。実在確認は検証側と共有します。 */
    private val entityAttributeId = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ATTRIBUTE,
        64,
        validate = { raw ->
            if (!EntityAttributeSupport.isAttributeId(raw)) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
        required = true,
        format = InputFormat.RESOURCE_ID,
    )

    /** issue #32: modifier識別キーの入力です。NamespacedKey構文を要求します。 */
    private val attributeModifierKey = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MODIFIER_KEY,
        64,
        validate = { raw ->
            if (!EntityAttributeSupport.isModifierKey(raw)) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
        required = true,
        format = InputFormat.RESOURCE_ID,
    )

    /** issue #32: 基本値・modifier量の数値入力です。有限数値を要求します。 */
    private val attributeModifierAmount = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MODIFIER_AMOUNT,
        16,
        validate = { raw ->
            if (CommandValueRules.parseFiniteDouble(raw) == null) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_GEOMETRY_NUMBER_INVALID
            } else null
        },
        required = true,
        format = InputFormat.NUMBER,
    )

    /**
     * issue #32: データ設定値の入力です。
     *
     * 真偽値・有限候補はDialogを使わずCheckbox／選択木で編集するため、
     * ここでは文字列項目（表示名）だけを定義します。空欄は表示名の消去であり、
     * 必須にはしません。テンプレート参照は召喚時の表示名と同じ規則で許可します。
     */
    private fun entityPropertyValue(node: CommandNode): Spec? {
        val definition = EntityDataSchema.definition(node.string(EntityDataSchema.PARAM_PROPERTY))
            ?: return null
        if (definition.valueKind != EntityPropertyValueKind.TEXT) return null
        return Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PROPERTY_VALUE,
            512,
            format = InputFormat.ANY_STRING,
        )
    }

    /**
     * フィールド入力系（インベントリGUIとジェスチャーGUIで共有）。
     *
     * ここには両GUIが直接編集できる全フィールドを列挙します。未登録の
     * フィールドを呼び出し側の既定値へ落とすとmaxLengthや検証が分岐するため、
     * 新しい入力項目は必ずこの一覧へ追加します。
     */
    fun field(fieldKey: String): Spec? {
        // ブロック入力は条件設定とブロック操作で同じID検証を使います。
        // ここを通常の文字列入力へ流すと、ジェスチャーGUIだけが任意文字列を
        // 保存でき、インベントリGUIとの入力契約が分岐してしまいます。
        if (fieldKey == "block") return block
        if (fieldKey in setOf("fadeInSeconds", "staySeconds", "fadeOutSeconds")) {
            return timeRange(
                labelKey = when (fieldKey) {
                    "fadeInSeconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_IN
                    "staySeconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STAY
                    else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_OUT
                },
                minimum = 0.0,
                exclusiveMinimum = false,
                invalidKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID,
            )
        }
        val labelKey = when (fieldKey) {
            "text" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TEXT
            "subtitle" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TEXT
            "customName" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_DISPLAY_NAME
            "itemData" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM
            "value" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE
            "fadeInSeconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_IN
            "staySeconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STAY
            "fadeOutSeconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_OUT
            "entity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY
            "sound" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND
            "soundParameters" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_PARAMETERS
            "effect" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EFFECT
            "tags" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAGS
            "tag" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG
            "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT
            "level" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LEVEL
            "seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS
            "volume" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VOLUME
            "pitch" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PITCH
            "intensity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_INTENSITY
            "slot" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EQUIPMENT_SLOT
            "shakeType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SHAKE_TYPE
            else -> return null
        }
        val maxLength = when (fieldKey) {
            "text", "subtitle", "customName", "itemData", "value" -> 512
            "entity", "sound", "effect", "tags", "tag" -> 64
            else -> 16
        }
        val format = when (fieldKey) {
            "text", "subtitle", "customName", "itemData", "value", "shakeType" -> InputFormat.ANY_STRING
            "entity", "sound", "effect" -> InputFormat.RESOURCE_ID
            "tags", "tag" -> InputFormat.TAG
            "count" -> InputFormat.QUANTITY
            "level" -> InputFormat.INTEGER
            "seconds", "fadeInSeconds", "staySeconds", "fadeOutSeconds" -> InputFormat.TIME
            "volume", "pitch", "intensity" -> InputFormat.NUMBER
            "slot" -> InputFormat.EQUIPMENT_SLOT
            else -> InputFormat.ANY_STRING
        }
        val formatHintKey = when (fieldKey) {
            "text", "subtitle" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_TEXT_BODY
            "value" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_VALUE_BODY
            "entity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_ENTITY_BODY
            "tags", "tag" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_TAGS_BODY
            "sound" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_SOUND_BODY
            "volume" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_VOLUME_BODY
            "pitch" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_PITCH_BODY
            "effect" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_EFFECT_BODY
            "level" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_LEVEL_BODY
            "seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_SECONDS_BODY
            "intensity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_INTENSITY_BODY
            "slot" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_EQUIPMENT_SLOT_BODY
            "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_COUNT_BODY
            else -> null
        }
        val positiveInteger = fieldKey in setOf("count", "level", "seconds")
        // issue #18: 有界数値の範囲・刻みは共有正本です。検証の正本として使います。
        // 上限の定まらない数量系（個数・レベル・秒数）は範囲を持たず、
        // テキスト入力のまま正の整数検証だけを適用します。
        val numericRange = when (fieldKey) {
            "volume" -> NumericRange(0.0, 34.0, step = 1.0)
            "pitch" -> NumericRange(0.5, 2.0, step = 0.1)
            "intensity" -> NumericRange(0.1, 4.0, step = 0.1)
            else -> null
        }
        val required = fieldKey in setOf(
            "block", "entity", "sound", "effect", "count", "level", "seconds",
            "volume", "pitch", "intensity", "shakeType", "slot", "tag",
            "fadeInSeconds", "staySeconds", "fadeOutSeconds",
        )
        return Spec(labelKey, maxLength, validate = { raw ->
            when {
                positiveInteger && !isNumericTemplate(raw) && !isPositiveInteger(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID
                fieldKey in setOf("entity", "sound", "effect") && !registeredFieldId(fieldKey, raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                fieldKey in setOf("tags", "tag") && VariableTemplate.hasMalformedReference(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_TAG_FORMAT
                fieldKey in setOf("tags", "tag") && VariableTemplate.references(raw).isEmpty() &&
                    !CommandValueRules.isTag(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_TAG_FORMAT
                fieldKey == "slot" && !CommandValueRules.isEquipmentSlot(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                numericRange != null && fieldKey in setOf("volume", "pitch", "intensity") &&
                    !isNumericTemplate(raw) &&
                    !CommandValueRules.isFiniteDoubleIn(raw, numericRange.min..numericRange.max) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                fieldKey in setOf("text", "subtitle", "customName", "itemData", "value") && hasMalformedAnyReference(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                else -> null
            }
        }, required = required, format = format, formatHintKey = formatHintKey, numericRange = numericRange)
    }

    /** 有限の座標値を受け付ける仕様です。座標・回転の各入力欄で共有します。 */
    private fun finiteCoordinate(axis: String, allowNotation: Boolean = true): Spec = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        MULTI_VALUE_MAX_LENGTH,
        validate = { raw ->
            // Particle形状値など記法対象外の欄では、接頭辞付き入力を拒否します。
            val parsed = parseCoordinateComponent(raw)
            if (parsed == null || (!allowNotation && parsed.first != CoordinateSemantics.ABSOLUTE)) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_COORDINATES_INVALID
            } else null
        },
        required = true,
        format = InputFormat.NUMBER,
    )

    private fun finiteRotation(axis: String): Spec = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        MULTI_VALUE_MAX_LENGTH,
        validate = { raw ->
            if (parseAngleComponent(raw) == null) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_INVALID
            } else null
        },
        required = true,
        format = InputFormat.NUMBER,
    )

    /** Minecraftのtickで表現できる秒数を検証する仕様です。 */
    private fun timeRange(
        labelKey: LocalizationKey<String>,
        minimum: Double,
        exclusiveMinimum: Boolean,
        invalidKey: LocalizationKey<String>,
        // issue #18: 段階操作の刻みです。tick整合（0.05）の倍数に限り、
        // 生成値が検証を必ず通過します。
        step: Double = 1.0,
    ): Spec {
        val range = NumericRange(minimum, MAX_COMMAND_TIME_SECONDS, step, exclusiveMin = exclusiveMinimum)
        return Spec(
            labelKey,
            16,
            validate = { raw ->
                if (isNumericTemplate(raw)) {
                    null
                } else {
                    val value = CommandValueRules.parseFiniteDouble(raw)
                    when {
                        value == null ||
                            (range.exclusiveMin && value <= range.min) ||
                            (!range.exclusiveMin && value < range.min) ||
                            value > range.max -> invalidKey
                        !CommandValueRules.isTickAlignedSeconds(raw) ->
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TICK_INVALID
                        else -> null
                    }
                }
            },
            required = true,
            format = InputFormat.TIME,
            formatHintKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIME_FORMAT_HINT,
            numericRange = range,
        )
    }

    /** 条件の数値比較値は、数値または単一の数値変数参照だけを許可します。 */
    private val conditionNumberValue = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        512,
        validate = { raw ->
            if (VariableTemplate.hasMalformedReference(raw) ||
                (!isNumericTemplate(raw) && CommandValueRules.parseFiniteDouble(raw) == null)
            ) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
        required = true,
        format = InputFormat.NUMBER,
        formatHintKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONDITION_VALUE_BODY,
    )

    /**
     * 正の整数を受け付ける仕様を、数量・整数秒数・上限で共有します。
     *
     * 上限の定まらない数量はテキスト入力のまま検証だけを適用します。
     * 有界の整数は [integerRange] を使います。
     */
    private fun positiveInteger(
        labelKey: LocalizationKey<String>,
        max: Int = Int.MAX_VALUE,
    ): Spec {
        val range = NumericRange(1.0, max.toDouble(), step = 1.0, integer = true)
        return Spec(
            labelKey,
            16,
            validate = { raw ->
                // issue #18: 範囲判定は共有正本を参照します。
                val value = CommandValueRules.parsePositiveInt(raw)?.toDouble()
                if (!isNumericTemplate(raw) && (value == null || value < range.min || value > range.max)) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID
                } else null
            },
            required = true,
            format = InputFormat.QUANTITY,
            // Slider導出には使わず、テキスト入力にします。
            numericRange = null,
        )
    }

    /** 実行側のInt範囲まで入力時に確認し、保存後にだけ失敗する値を作りません。 */
    private fun integerRange(
        labelKey: LocalizationKey<String>,
        range: IntRange,
        invalidKey: LocalizationKey<String>,
        format: InputFormat = InputFormat.INTEGER,
    ): Spec {
        val numericRange = NumericRange(range.first.toDouble(), range.last.toDouble(), step = 1.0, integer = true)
        return Spec(labelKey, 16, validate = { raw ->
            val value = CommandValueRules.parsePositiveInt(raw)
            if (!isNumericTemplate(raw) && (value == null || value < numericRange.min || value > numericRange.max)) invalidKey else null
        }, required = true, format = format, numericRange = numericRange)
    }

    /** 小数を許可する実行値は、整数仕様へ誤って流さないよう専用化します。 */
    private fun decimalRange(
        labelKey: LocalizationKey<String>,
        range: ClosedFloatingPointRange<Double>,
        invalidKey: LocalizationKey<String>,
        format: InputFormat = InputFormat.NUMBER,
        // issue #18: 段階操作の刻みです。検証には使いません。
        step: Double = 1.0,
    ): Spec {
        val numericRange = NumericRange(range.start, range.endInclusive, step)
        return Spec(labelKey, 16, validate = { raw ->
            val value = CommandValueRules.parseFiniteDouble(raw)
            if (!isNumericTemplate(raw) && (value == null || !value.isFinite() || value < numericRange.min || value > numericRange.max)) invalidKey else null
        }, required = true, format = format, numericRange = numericRange)
    }

    /** 変数操作の保存値を、実行時の式評価と同じ型境界で検証します。 */
    private fun variableValue(type: VariableType?, operation: VariableOperation, changeMode: VariableChangeMode): Spec {
        val base = requireNotNull(field("value"))
        val format = when {
            type == VariableType.NUMBER && changeMode == VariableChangeMode.CALCULATE -> InputFormat.NUMERIC_EXPRESSION
            type == VariableType.NUMBER -> InputFormat.NUMBER
            else -> InputFormat.ANY_STRING
        }
        return base.copy(
            required = true,
            format = format,
            // 通常の条件値と同じ `value` フィールドでも、変数コマンドの保存先は
            // ワールド内変数です。共通の入力本文をそのまま流用すると、条件値の
            // 説明まで「変数」になってしまうため、ここで意味を分離します。
            formatHintKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_WORLD_VARIABLE_VALUE_BODY,
            validate = { raw ->
                when {
                    type == null && changeMode == VariableChangeMode.CALCULATE ->
                        numericExpressionValidationError(raw)
                    type == null ->
                        if (VariableTemplate.hasMalformedReference(raw)) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT else null
                    type == VariableType.NUMBER -> if (changeMode == VariableChangeMode.CALCULATE) {
                        numericExpressionValidationError(raw)
                    } else if (isNumericTemplate(raw) || CommandValueRules.parseFiniteDouble(raw) != null
                    ) {
                        null
                    } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                    type == VariableType.STRING ->
                        if (VariableTemplate.hasMalformedReference(raw)) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT else null
                    else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                }
            },
        )
    }

    /** NumericExpressionの構文エラーを、入力画面で表示する専用キーへ変換します。 */
    private fun numericExpressionValidationError(raw: String): LocalizationKey<String>? =
        NumericExpression.parse(raw).error?.let { error ->
            when (error.code) {
                NumericExpression.ErrorCode.EMPTY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_EMPTY
                NumericExpression.ErrorCode.TRAILING_CHARACTERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_TRAILING_CHARACTERS
                NumericExpression.ErrorCode.UNCLOSED_PARENTHESIS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_UNCLOSED_PARENTHESIS
                NumericExpression.ErrorCode.OPERAND_REQUIRED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_OPERAND_REQUIRED
                NumericExpression.ErrorCode.INVALID_NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_INVALID_NUMBER
                NumericExpression.ErrorCode.INVALID_CHARACTER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_INVALID_CHARACTER
                NumericExpression.ErrorCode.INVALID_VARIABLE_NAME -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_INVALID_VARIABLE_NAME
            }
        }

    /** 一覧から選択できる入力項目だけを候補提示の対象にします。 */
    fun supportsSuggestions(fieldKey: String): Boolean = fieldKey in setOf("entity", "entityType", "sound", "effect", ParticleSettings.PARAM_PARTICLE)

    /**
     * issue #28: Registry由来の全候補IDです。
     *
     * 検索ランキングを持たず、Dialogの候補検索とGesture Resource Pickerの
     * 双方から利用する共通境界です。サーバーなしでは空列を返します。
     */
    fun registeredIds(fieldKey: String): List<String> = registeredValues(fieldKey)

    /** 複数クエリ検索で一度に扱うクエリ数の上限です。多すぎる再検索を抑えます。 */
    const val MAX_SUGGESTION_QUERIES = 8

    /** 候補表示の総数上限です。単一・複数クエリいずれもこの値を維持します。 */
    const val MAX_SUGGESTIONS = 12

    /** 複数クエリ入力の区切り文字です。空白類・`,`・`、`を区切りとして扱います。 */
    private val SUGGESTION_QUERY_DELIMITERS = Regex("[,、\\s\\u3000]+")

    /**
     * issue #31: 「候補を表示」押下時の入力文字列を複数クエリへ分解します。
     *
     * 空要素は除外し、大小無視で重複を除去して [MAX_SUGGESTION_QUERIES] 件で
     * 打ち切ります。保存値の仕様には触れず、検索専用の入力構文として扱います。
     */
    fun parseSuggestionQueries(input: String, maxQueries: Int = MAX_SUGGESTION_QUERIES): List<String> {
        if (maxQueries <= 0) return emptyList()
        val seenKeys = LinkedHashSet<String>()
        val queries = ArrayList<String>()
        for (raw in input.split(SUGGESTION_QUERY_DELIMITERS)) {
            val query = raw.trim()
            if (query.isEmpty()) continue
            if (!seenKeys.add(query.lowercase())) continue
            queries.add(query)
            if (queries.size >= maxQueries) break
        }
        return queries
    }

    /**
     * 入力中の文字列に近い登録IDを最大12件返します。
     * Paperの入力画面はキー入力ごとのコールバックを提供しないため、呼び出し側は
     * 「候補を表示」ボタン押下時の最新値を渡します。完全一致→前方一致→
     * 部分一致→編集距離の順で安定ソートし、空欄では候補を返しません。
     * ランキングは [registeredIds] の取得と分離しています。
     */
    fun suggestions(fieldKey: String, query: String, limit: Int = MAX_SUGGESTIONS): List<String> =
        suggestionsFromInput(fieldKey, query, limit)

    /**
     * issue #31: 「候補を表示」押下時の入力文字列を複数クエリとしてOR検索します。
     *
     * 各クエリ内部では単一クエリと同一の一致度順ランキングを維持し、クエリ間の
     * 結果はラウンドロビンで統合します。先頭クエリだけで候補枠を使い切らず、
     * 同一IDの重複表示も除去します。候補一覧の取得は1回にまとめます。
     */
    fun suggestionsFromInput(fieldKey: String, input: String, limit: Int = MAX_SUGGESTIONS): List<String> =
        suggestions(fieldKey, parseSuggestionQueries(input), limit)

    /**
     * issue #31: 複数クエリをOR条件で検索し、最大[limit]件へ統合します。
     *
     * 候補一覧の取得は1回だけ行い、各クエリのランキングへ使い回します。
     */
    fun suggestions(fieldKey: String, queries: List<String>, limit: Int = MAX_SUGGESTIONS): List<String> {
        if (!supportsSuggestions(fieldKey)) return emptyList()
        val normalized = queries.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_SUGGESTION_QUERIES)
        if (normalized.isEmpty()) return emptyList()
        val values = registeredIds(fieldKey)
        if (values.isEmpty()) return emptyList()
        val perQuery = normalized.map { rankCandidates(values, it) }
        return mergeSuggestionRounds(perQuery, limit.coerceAtLeast(0))
    }

    /**
     * 単一クエリに対する一致度順ランキングです。
     *
     * 完全一致→前方一致→部分一致→編集距離の順で安定ソートします。
     * Registry取得を含まない純粋処理のため、サーバーなしの単体テストから直接検証できます。
     */
    internal fun rankCandidates(values: List<String>, normalizedQuery: String): List<String> =
        values.asSequence()
            .map { value ->
                val candidate = value.lowercase()
                val rank = when {
                    candidate == normalizedQuery -> 0
                    candidate.startsWith(normalizedQuery) -> 1
                    candidate.contains(normalizedQuery) -> 2
                    else -> 3
                }
                Triple(rank, levenshtein(candidate, normalizedQuery), value)
            }
            .sortedWith(compareBy<Triple<Int, Int, String>> { it.first }
                .thenBy { it.second }
                .thenBy { it.third })
            .map { it.third }
            .toList()

    /**
     * 複数クエリのランキング結果をラウンドロビンで統合します。
     *
     * `A1 A2 ...` `B1 B2 ...` を `A1 B1 A2 B2 ...` の順に束ね、同一IDの
     * 重複を除去して [limit] 件で打ち切ります。先頭クエリへの偏りを防ぎます。
     */
    internal fun mergeSuggestionRounds(ranked: List<List<String>>, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val merged = LinkedHashSet<String>()
        val indices = IntArray(ranked.size)
        var progressed = true
        while (merged.size < limit && progressed) {
            progressed = false
            for (i in ranked.indices) {
                if (merged.size >= limit) break
                val candidates = ranked[i]
                var index = indices[i]
                while (index < candidates.size && candidates[index] in merged) index++
                if (index < candidates.size) {
                    merged.add(candidates[index])
                    index++
                    progressed = true
                }
                indices[i] = index
            }
        }
        return merged.take(limit)
    }

    private fun registeredFieldId(fieldKey: String, raw: String): Boolean = when (fieldKey) {
        "entity", "entityType" -> CommandValueRules.isEntityTypeId(raw)
        "sound" -> CommandValueRules.isSoundId(raw)
        "effect" -> CommandValueRules.isEffectId(raw)
        ParticleSettings.PARAM_PARTICLE -> runCatching { org.bukkit.Particle.valueOf(raw.trim()) }.isSuccess
        else -> false
    }

    private fun registeredValues(fieldKey: String): List<String> = runCatching {
        when (fieldKey) {
            // Registry.keyStream() は要素側の非推奨 Keyed.key プロパティを経由せず、
            // Paper 26.1.2 が提供する登録IDの公式ストリームをそのまま利用します。
            "entity", "entityType" -> Registry.ENTITY_TYPE.keyStream().map { it.toString() }.toList()
            "sound" -> Registry.SOUNDS.keyStream().map { it.toString() }.toList()
            "effect" -> Registry.EFFECT.keyStream().map { it.toString() }.toList()
            ParticleSettings.PARAM_PARTICLE -> org.bukkit.Particle.entries.map { it.name }
            else -> emptyList()
        }.distinct().sorted()
    }.getOrElse { emptyList() }

    private fun levenshtein(first: String, second: String): Int {
        if (first == second) return 0
        if (first.isEmpty()) return second.length
        if (second.isEmpty()) return first.length
        var previous = IntArray(second.length + 1) { it }
        for (i in first.indices) {
            val current = IntArray(second.length + 1)
            current[0] = i + 1
            for (j in second.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (first[i] == second[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[second.length]
    }

    private const val MAX_EFFECT_SECONDS = 86_400
    private const val MULTI_VALUE_MAX_LENGTH = 64
    private val NAMESPACED_ID_FIELDS = setOf("entity", "entityType", "sound", "effect")

    /** 数値欄でも単一のワールド変数・一時変数を実行時に数値化できるようにします。 */
    private fun isNumericTemplate(raw: String): Boolean =
        VariableTemplate.isSingleReference(raw) || TemporaryTemplate.isSingleReference(raw)

    /** 文字列欄で両記法の不正参照を検出します。 */
    private fun hasMalformedAnyReference(raw: String): Boolean =
        VariableTemplate.hasMalformedReference(raw) || TemporaryTemplate.hasMalformedReference(raw)
}
