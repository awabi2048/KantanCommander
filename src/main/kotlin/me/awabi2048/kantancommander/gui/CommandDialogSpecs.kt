package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import org.bukkit.NamespacedKey

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
    )

    /** 対象フィルタ系。空欄は「指定解除」として常に合格です。 */
    fun targetFilter(parameter: String): Spec? = when (parameter) {
        "entityType" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_TYPE,
            64,
            { raw ->
                if (NamespacedKey.fromString(raw) == null) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_ENTITY_TYPE_FORMAT
                } else null
            },
        )
        "minimumDistance", "maximumDistance" -> Spec(
            if (parameter == "minimumDistance") KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE
            else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE,
            64,
            { raw ->
                val value = raw.toDoubleOrNull()
                if (value == null || !value.isFinite() || value < 0.0) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID
                } else null
            },
        )
        "limit" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LIMIT,
            64,
            { raw ->
                val value = raw.toIntOrNull()
                if (value == null || value < 1) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID else null
            },
        )
        "tag" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG,
            64,
            { raw ->
                if (!raw.matches(Regex("[A-Za-z0-9_.:+-]{1,64}"))) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_TAG_FORMAT
                } else null
            },
        )
        "name" -> Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME,
            256,
            { raw -> if (raw.length > 256) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_NAME_LENGTH else null },
        )
        else -> null
    }

    /** 変数名（保存名）の共通仕様。 */
    val variableName = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE,
        64,
        { raw ->
            if (!raw.matches(Regex("[a-z0-9_.-]{1,64}"))) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_VARIABLE_NAME
            } else null
        },
    )

    /** 符号付き整数（条件の比較値など）。 */
    val signedInteger = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
        64,
        { raw -> if (raw.toLongOrNull() == null) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID else null },
    )

    /** ブロックID（条件のブロック状態など）。マテリアルとして解決できることを検証します。 */
    val block = Spec(
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK,
        64,
        { raw ->
            if (org.bukkit.Material.matchMaterial(raw) == null) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else null
        },
    )

    /** フィールド入力系（インベントリGUIの欄別maxLength・正整数検証と同一仕様）。 */
    fun field(fieldKey: String): Spec? {
        val labelKey = when (fieldKey) {
            "text" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TEXT
            "value" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE
            "entity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY
            "sound" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND
            "effect" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EFFECT
            "tags" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAGS
            "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT
            "ticks" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WAIT
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
            "text", "value" -> 512
            "entity", "sound", "effect", "tags" -> 64
            else -> 16
        }
        val positiveInteger = fieldKey in setOf("count", "ticks", "level", "seconds")
        return Spec(labelKey, maxLength) { raw ->
            if (positiveInteger && (raw.toIntOrNull() ?: 0) < 1) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID
            } else null
        }
    }
}
