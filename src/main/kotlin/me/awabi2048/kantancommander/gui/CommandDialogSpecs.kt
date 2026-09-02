package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.NumericExpression
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

    /** テキスト入力欄の共通仕様。 */
    data class Spec(
        /** 入力欄のラベル（ダイアログ見出し・プロンプトへ使用）。 */
        val labelKey: LocalizationKey<String>,
        val maxLength: Int,
        /** 空欄以外の入力へ適用する検証。null なら合格、キーはエラーメッセージ。 */
        val validate: (String) -> LocalizationKey<String>? = { null },
        /** trueの場合は空欄も検証関数へ渡し、必須値として扱います。 */
        val required: Boolean = false,
    ) {
        /** GUI固有の空欄スキップを廃止し、必須／任意の契約をここで一元適用します。 */
        fun validateInput(raw: String): LocalizationKey<String>? =
            if (raw.isBlank() && !required) null else validate(raw)
    }

    /**
     * 単一テキスト入力の共通表示を生成します。
     *
     * Inventory GUIとGesture GUIが個別にbodyや入力欄を組み立てると、
     * プロンプト、現在値、入力欄ラベルが再び分岐します。表示形式もこの
     * 仕様オブジェクトから生成し、入力値の意味とUIの表現を同じ境界に置きます。
     */
    fun prompt(player: Player, spec: Spec): String = KcI18n.text(
        player,
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_VALUE_INPUT_SUFFIX,
        mapOf("label" to KcI18n.text(player, spec.labelKey)),
    )

    fun body(player: Player, spec: Spec, current: String): List<Component> = buildList {
        add(Component.text(prompt(player, spec)))
        add(
            Component.text(
                KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_CURRENT_VALUE,
                    mapOf(
                        "value" to current.ifBlank {
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
                        },
                    ),
                ),
                NamedTextColor.GRAY,
            ),
        )
        // テキスト入力を伴うDialogでは、変数参照と色指定の例を入力欄より下へ
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
    }

    fun input(player: Player, id: String, initial: String, spec: Spec): MenuDialogInput.Text =
        MenuDialogInput.Text(
            id = id,
            label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_LABEL)),
            initial = initial,
            maxLength = spec.maxLength,
        )

    /** プログラムタイマーの入力契約を両GUIで共有します。 */
    val timerSeconds = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTERVAL,
        6,
        validate = { raw ->
            val value = CommandValueRules.parsePositiveInt(raw)
            if (value == null || value !in 1..MAX_TIMER_SECONDS) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_INVALID
            } else null
        },
        required = true,
    )

    fun timerBody(player: Player, seconds: Int): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_BODY),
        Component.text(
            KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INTERVAL_SECONDS,
                mapOf("value" to seconds),
            ),
            NamedTextColor.GRAY,
        ),
    )

    fun timerInput(player: Player, seconds: Int): MenuDialogInput.Text =
        MenuDialogInput.Text(
            id = "seconds",
            label = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTERVAL),
            initial = seconds.toString(),
            maxLength = timerSeconds.maxLength,
        )

    /** 座標入力の表示・入力長・有限値判定を両GUIで共有します。 */
    fun coordinateBody(player: Player, x: Double, y: Double, z: Double): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_COORDINATE_PROMPT),
        Component.text(
            KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_COORDINATE_CURRENT,
                mapOf("x" to x.toString(), "y" to y.toString(), "z" to z.toString()),
            ),
            NamedTextColor.GRAY,
        ),
    )

    fun coordinateInputs(player: Player, x: Double, y: Double, z: Double): List<MenuDialogInput.Text> = listOf(
        MenuDialogInput.Text("x", Component.text("X"), x.toString(), maxLength = MULTI_VALUE_MAX_LENGTH),
        MenuDialogInput.Text("y", Component.text("Y"), y.toString(), maxLength = MULTI_VALUE_MAX_LENGTH),
        MenuDialogInput.Text("z", Component.text("Z"), z.toString(), maxLength = MULTI_VALUE_MAX_LENGTH),
    )

    /** 回転入力も座標入力と同じく、表示と有限値判定の境界を共有します。 */
    fun rotationBody(player: Player, yaw: Float, pitch: Float): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_ROTATION_PROMPT),
        Component.text(
            KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_ROTATION_CURRENT,
                mapOf("yaw" to yaw.toString(), "pitch" to pitch.toString()),
            ),
            NamedTextColor.GRAY,
        ),
    )

    fun rotationInputs(player: Player, yaw: Float, pitch: Float): List<MenuDialogInput.Text> = listOf(
        MenuDialogInput.Text("yaw", Component.text("Yaw"), yaw.toString(), maxLength = MULTI_VALUE_MAX_LENGTH),
        MenuDialogInput.Text("pitch", Component.text("Pitch"), pitch.toString(), maxLength = MULTI_VALUE_MAX_LENGTH),
    )

    /** 対象範囲は3軸を一つの設定項目として表示します。 */
    fun rangeBody(player: Player, dx: Double?, dy: Double?, dz: Double?): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_RANGE_BODY),
        Component.text(
            KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_CURRENT_VALUE,
                mapOf("value" to rangeSummary(player, dx, dy, dz)),
            ),
            NamedTextColor.GRAY,
        ),
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_RANGE_CONSTRAINT),
    )

    fun rangeInputs(player: Player, dx: Double?, dy: Double?, dz: Double?): List<MenuDialogInput.Text> {
        val maxLength = requireNotNull(targetFilter("range")).maxLength
        return listOf(
            MenuDialogInput.Text("dx", KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX), formatOptionalNumber(dx), maxLength = maxLength),
            MenuDialogInput.Text("dy", KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY), formatOptionalNumber(dy), maxLength = maxLength),
            MenuDialogInput.Text("dz", KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ), formatOptionalNumber(dz), maxLength = maxLength),
        )
    }

    /** 効果音の音量・ピッチを一つの設定項目として表示・入力します。 */
    fun soundParametersBody(player: Player, volume: String, pitch: String): List<Component> = listOf(
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SOUND_PARAMETERS_BODY),
        Component.text(
            KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_CURRENT_VALUE,
                mapOf(
                    "value" to listOf(
                        "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VOLUME)}=$volume",
                        "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PITCH)}=$pitch",
                    ).joinToString(" / "),
                ),
            ),
            NamedTextColor.GRAY,
        ),
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SOUND_PARAMETERS_CONSTRAINT),
    )

    fun soundParametersInputs(player: Player, volume: String, pitch: String): List<MenuDialogInput.Text> = listOf(
        MenuDialogInput.Text(
            "volume",
            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VOLUME),
            volume,
            maxLength = requireNotNull(field("volume")).maxLength,
        ),
        MenuDialogInput.Text(
            "pitch",
            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PITCH),
            pitch,
            maxLength = requireNotNull(field("pitch")).maxLength,
        ),
    )

    private fun formatOptionalNumber(value: Double?): String = value?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    }.orEmpty()

    private fun rangeSummary(player: Player, dx: Double?, dy: Double?, dz: Double?): String = listOf(
        "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX)}=${formatOptionalNumber(dx).ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }}",
        "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY)}=${formatOptionalNumber(dy).ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }}",
        "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ)}=${formatOptionalNumber(dz).ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }}",
    ).joinToString(" / ")

    fun finiteDouble(raw: String): Double? = raw.toDoubleOrNull()?.takeIf(Double::isFinite)

    fun finiteFloat(raw: String): Float? = raw.toFloatOrNull()?.takeIf(Float::isFinite)

    /** Inventory/Gestureの両方で、保存前に同じ入力正規化を行います。 */
    fun normalize(fieldKey: String, raw: String): String = raw.trim().let {
        if (fieldKey in NAMESPACED_ID_FIELDS) it.lowercase() else it
    }

    /** 表示方式ごとの説明を使い分けた表示時間ダイアログ本文を生成します。 */
    fun durationBody(
        player: Player,
        fadeIn: String,
        stay: String,
        fadeOut: String,
        mode: String,
    ): List<Component> {
        val current = listOf(
            "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_IN)}=$fadeIn",
            "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STAY)}=$stay",
            "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_OUT)}=$fadeOut",
        ).joinToString(", ")
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
                KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_CURRENT_VALUE,
                    mapOf("value" to current),
                ),
                NamedTextColor.GRAY,
            ),
        )
    }

    /** 表示時間3項目の入力欄を共通仕様から生成します。 */
    fun durationInputs(player: Player, fadeIn: String, stay: String, fadeOut: String): List<MenuDialogInput.Text> {
        val spec = requireNotNull(field("staySeconds"))
        return listOf(
            MenuDialogInput.Text(
                "fadeInSeconds",
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_IN),
                fadeIn,
                maxLength = spec.maxLength,
            ),
            MenuDialogInput.Text(
                "staySeconds",
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STAY),
                stay,
                maxLength = spec.maxLength,
            ),
            MenuDialogInput.Text(
                "fadeOutSeconds",
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_OUT),
                fadeOut,
                maxLength = spec.maxLength,
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
        )
        "distance" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE,
            64,
            { raw ->
                val value = raw.toDoubleOrNull()
                if (!isNumericTemplate(raw) && (value == null || !value.isFinite() || value < 0.0)) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID
                } else null
            },
        )
        "range" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_RANGE,
            64,
            { raw ->
                val value = raw.toDoubleOrNull()
                // TargetSpecのdx/dy/dzは実行時に必ず有限Doubleとして保持する型付き値です。
                // 文字列テンプレートをここで許すと、Gesture側のDouble保存時に参照式が
                // 消えるため、型付きセレクター欄では保存可能な数値だけを受け付けます。
                if (value == null || !value.isFinite() || value < 0.0) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID
                } else null
            },
        )
        "limit" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LIMIT,
            64,
            { raw ->
                if (!isPositiveInteger(raw)) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID else null
            },
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
    )

    /** 符号付き整数（条件の比較値など）。 */
    val signedInteger = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        64,
        { raw -> if (raw.toLongOrNull() == null) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID else null },
    )

    /** 条件値は参照先変数の型に従うため、入力欄では文字列を保持します。 */
    val conditionValue = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        512,
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
        valueSource: String? = null,
    ): Spec? {
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
        return when (node.type) {
            CommandType.BLOCK_OPERATION -> if (fieldKey == "block") placementBlock else field(fieldKey, valueSource)
            CommandType.WAIT -> if (fieldKey == "seconds") {
                decimalRange(
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
                    0.000001..86_400.0,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID,
                )
            } else field(fieldKey, valueSource)
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
                )
                else -> field(fieldKey, valueSource)
            }
            CommandType.CAMERA_SHAKE -> if (fieldKey == "seconds") {
                decimalRange(
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
                    1.0..10.0,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CAMERA_SHAKE_SECONDS_INVALID,
                )
            } else field(fieldKey, valueSource)
            else -> field(fieldKey, valueSource)
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

    private fun isNonNegativeInteger(raw: String): Boolean =
        CommandValueRules.parseNonNegativeInt(raw) != null

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
    )

    /** ブロック操作はAIRを配置できないため、条件ブロックとは別の規則を使います。 */
    private val placementBlock = block.copy(
        validate = { raw ->
            if (CommandValueRules.placementMaterial(raw) == null) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
    )

    /**
     * フィールド入力系（インベントリGUIとジェスチャーGUIで共有）。
     *
     * ここには両GUIが直接編集できる全フィールドを列挙します。未登録の
     * フィールドを呼び出し側の既定値へ落とすとmaxLengthや検証が分岐するため、
     * 新しい入力項目は必ずこの一覧へ追加します。
     */
    fun field(fieldKey: String, valueSource: String? = null): Spec? {
        // ブロック入力は条件設定とブロック操作で同じID検証を使います。
        // ここを通常の文字列入力へ流すと、ジェスチャーGUIだけが任意文字列を
        // 保存でき、インベントリGUIとの入力契約が分岐してしまいます。
        if (fieldKey == "block") return block
        val labelKey = when (fieldKey) {
            "text" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TEXT
            "subtitle" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TEXT
            "customName" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME
            "itemData" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM
            "value" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE
            "fadeInSeconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_IN
            "staySeconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STAY
            "fadeOutSeconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_OUT
            "startValue" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_START
            "endValue" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_END
            "stepValue" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_STEP
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
        val positiveInteger = fieldKey in setOf("count", "level", "seconds")
        val nonNegativeInteger = fieldKey in setOf("fadeInSeconds", "staySeconds", "fadeOutSeconds")
        val required = fieldKey in setOf(
            "block", "entity", "sound", "effect", "count", "level", "seconds",
            "volume", "pitch", "intensity", "shakeType", "slot", "tag",
            "startValue", "endValue", "stepValue",
            "fadeInSeconds", "staySeconds", "fadeOutSeconds",
        )
        return Spec(labelKey, maxLength, validate = { raw ->
            when {
                positiveInteger && !isNumericTemplate(raw) && !isPositiveInteger(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID
                nonNegativeInteger && !isNumericTemplate(raw) && !isNonNegativeInteger(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID
                fieldKey in setOf("entity", "sound", "effect") && !registeredFieldId(fieldKey, raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                fieldKey in setOf("tags", "tag") && VariableTemplate.hasMalformedReference(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_TAG_FORMAT
                fieldKey in setOf("tags", "tag") && VariableTemplate.references(raw).isEmpty() &&
                    !CommandValueRules.isTag(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_TAG_FORMAT
                fieldKey == "slot" && !CommandValueRules.isEquipmentSlot(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                fieldKey == "volume" && !isNumericTemplate(raw) && !CommandValueRules.isFiniteDoubleIn(raw, 0.0..34.0) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                fieldKey == "pitch" && !isNumericTemplate(raw) && !CommandValueRules.isFiniteDoubleIn(raw, 0.5..2.0) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                fieldKey == "intensity" && !isNumericTemplate(raw) && !CommandValueRules.isFiniteDoubleIn(raw, 0.1..4.0) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                fieldKey in setOf("startValue", "endValue", "stepValue") && valueSource == "FIXED" &&
                    !isNumericTemplate(raw) && raw.toLongOrNull() == null ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID
                fieldKey == "stepValue" && valueSource == "FIXED" && raw.toLongOrNull() == 0L ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STEP_ZERO
                fieldKey in setOf("text", "subtitle", "customName", "itemData", "value") && VariableTemplate.hasMalformedReference(raw) ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                else -> null
            }
        }, required = required)
    }

    /** 正の整数を受け付ける仕様を、個数・秒数・上限で共有します。 */
    private fun positiveInteger(labelKey: LocalizationKey<String>): Spec = Spec(
        labelKey,
        16,
        validate = { raw ->
            if (!isNumericTemplate(raw) && !isPositiveInteger(raw)) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID else null
        },
        required = true,
    )

    /** 実行側のInt範囲まで入力時に確認し、保存後にだけ失敗する値を作りません。 */
    private fun integerRange(
        labelKey: LocalizationKey<String>,
        range: IntRange,
        invalidKey: LocalizationKey<String>,
    ): Spec = Spec(labelKey, 16, validate = { raw ->
        val value = CommandValueRules.parsePositiveInt(raw)
        if (!isNumericTemplate(raw) && (value == null || value !in range)) invalidKey else null
    }, required = true)

    /** 小数を許可する実行値は、整数仕様へ誤って流さないよう専用化します。 */
    private fun decimalRange(
        labelKey: LocalizationKey<String>,
        range: ClosedFloatingPointRange<Double>,
        invalidKey: LocalizationKey<String>,
    ): Spec = Spec(labelKey, 16, validate = { raw ->
        val value = raw.toDoubleOrNull()
        if (!isNumericTemplate(raw) && (value == null || !value.isFinite() || value !in range)) invalidKey else null
    }, required = true)

    /** 変数操作の保存値を、実行時の式評価と同じ型境界で検証します。 */
    private fun variableValue(type: VariableType?, operation: VariableOperation, changeMode: VariableChangeMode): Spec {
        val base = requireNotNull(field("value"))
        return base.copy(required = true, validate = { raw ->
            when {
                type == null && changeMode == VariableChangeMode.CALCULATE ->
                    numericExpressionValidationError(raw)
                type == null ->
                    if (VariableTemplate.hasMalformedReference(raw)) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT else null
                type == VariableType.NUMBER -> if (changeMode == VariableChangeMode.CALCULATE) {
                    numericExpressionValidationError(raw)
                } else if (raw == CURRENT_ITERATION_VALUE || raw == CURRENT_LOOP_COUNT ||
                    isNumericTemplate(raw) || raw.toDoubleOrNull()?.isFinite() == true
                ) {
                    null
                } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
                type == VariableType.STRING ->
                    if (VariableTemplate.hasMalformedReference(raw)) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT else null
                else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            }
        })
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
                NumericExpression.ErrorCode.INVALID_READONLY_NAME -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_INVALID_READONLY_NAME
                NumericExpression.ErrorCode.INVALID_VARIABLE_NAME -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_INVALID_VARIABLE_NAME
            }
        }

    /** 一覧から選択できる入力項目だけを候補提示の対象にします。 */
    fun supportsSuggestions(fieldKey: String): Boolean = fieldKey in setOf("entity", "entityType", "sound", "effect")

    /**
     * 入力中の文字列に近い登録IDを最大12件返します。
     * Paper Dialogはキー入力ごとのコールバックを提供しないため、呼び出し側は
     * 「候補を表示」ボタン押下時の最新値を渡します。完全一致→前方一致→
     * 部分一致→編集距離の順で安定ソートし、空欄では候補を返しません。
     */
    fun suggestions(fieldKey: String, query: String, limit: Int = 12): List<String> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty() || !supportsSuggestions(fieldKey)) return emptyList()
        val values = registeredValues(fieldKey)
        return values.asSequence()
            .map { value ->
                val candidate = value.lowercase()
                val rank = when {
                    candidate == normalized -> 0
                    candidate.startsWith(normalized) -> 1
                    candidate.contains(normalized) -> 2
                    else -> 3
                }
                Triple(rank, levenshtein(candidate, normalized), value)
            }
            .sortedWith(compareBy<Triple<Int, Int, String>> { it.first }
                .thenBy { it.second }
                .thenBy { it.third })
            .take(limit.coerceAtLeast(0))
            .map { it.third }
            .toList()
    }

    private fun registeredFieldId(fieldKey: String, raw: String): Boolean = when (fieldKey) {
        "entity", "entityType" -> CommandValueRules.isEntityTypeId(raw)
        "sound" -> CommandValueRules.isSoundId(raw)
        "effect" -> CommandValueRules.isEffectId(raw)
        else -> false
    }

    private fun registeredValues(fieldKey: String): List<String> = runCatching {
        when (fieldKey) {
            // Registry.keyStream() は要素側の非推奨 Keyed.key プロパティを経由せず、
            // Paper 26.1.2 が提供する登録IDの公式ストリームをそのまま利用します。
            "entity", "entityType" -> Registry.ENTITY_TYPE.keyStream().map { it.toString() }.toList()
            "sound" -> Registry.SOUNDS.keyStream().map { it.toString() }.toList()
            "effect" -> Registry.EFFECT.keyStream().map { it.toString() }.toList()
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
    private const val CURRENT_ITERATION_VALUE = "\$current_iteration_value"
    private const val CURRENT_LOOP_COUNT = "\$current_loop_count"
    private val NAMESPACED_ID_FIELDS = setOf("entity", "entityType", "sound", "effect")

    /** 数値欄でも単一のワールド変数を実行時に数値化できるようにします。 */
    private fun isNumericTemplate(raw: String): Boolean =
        VariableTemplate.references(raw).size == 1 &&
            !VariableTemplate.hasMalformedReference(raw) &&
            raw.trim().matches(Regex("\\$\\{[a-z][a-z0-9_.-]{0,63}}"))
}
