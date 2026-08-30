package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.supportsContextOverride
import org.bukkit.Material

/** 操作不能な選択肢の見た目を両GUIで統一します。効果音の有無は入力契約側で制御します。 */
internal object DisabledGuiVisualPolicy {
    val material: Material = Material.LIGHT_GRAY_CONCRETE
}

/** コマンド選択画面で、実処理と実行順序の制御を混在させないための表示分類です。 */
internal enum class CommandCategory(
    val routeValue: String,
    val labelKey: LocalizationKey<String>,
    val descriptionKey: LocalizationKey<List<String>>,
) {
    PROCESS("process", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_PROCESS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_PROCESS_DESCRIPTION),
    CONTROL("control", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_CONTROL, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_CONTROL_DESCRIPTION),
    EXTERNAL_DISK("external_disk", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_EXTERNAL_DISK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_EXTERNAL_DISK_DESCRIPTION),
    ;

    companion object {
        fun fromRoute(value: String?): CommandCategory = entries.firstOrNull { it.routeValue == value } ?: PROCESS
    }
}

/**
 * コマンド型が増えた際に、分類・個別コンテキスト対応・説明が暗黙の既定値へ流れないよう、
 * 全型の表示契約を網羅的なwhenで管理します。
 */
internal object CommandPresentationPolicy {
    fun category(type: CommandType): CommandCategory = when (type) {
        CommandType.TELEPORT,
        CommandType.GIVE_ITEM,
        CommandType.ENTITY_ACTION,
        CommandType.DISPLAY_TEXT,
        CommandType.SUMMON_ENTITY,
        CommandType.PLAY_SOUND,
        CommandType.APPLY_EFFECT,
        CommandType.CAMERA_SHAKE,
        CommandType.EQUIP_ITEM,
        CommandType.BLOCK_OPERATION,
        CommandType.ENTITY_DELETE,
        CommandType.VARIABLE,
        -> CommandCategory.PROCESS

        CommandType.WAIT,
        CommandType.CONDITION,
        CommandType.CONTEXT,
        CommandType.MERGE,
        CommandType.FOR_START,
        CommandType.FOR_END,
        CommandType.BREAK,
        CommandType.CONTINUE,
        -> CommandCategory.CONTROL

        CommandType.DISK_CALL -> CommandCategory.EXTERNAL_DISK
    }

    fun supportsContextOverride(type: CommandType): Boolean = type.supportsContextOverride()
}

/** 設定数が多いコマンドは、意味上の組を崩さない専用配置を使用します。 */
internal object CommandSettingsSlotPolicy {
    private val fiveFieldSlots = listOf(19, 20, 21, 28, 29)
    private val variableSlots = mapOf(
        "scope" to 19,
        "name" to 20,
        "type" to 21,
        "operation" to 28,
        "value" to 29,
    )
    private val forSlots = mapOf(
        "startSource" to 10,
        "endSource" to 11,
        "stepSource" to 12,
        "startValue" to 19,
        "endValue" to 20,
        "stepValue" to 21,
        "inclusiveEnd" to 28,
    )

    fun slots(type: CommandType, fieldKeys: List<String>): List<Int> {
        val dedicated = when (type) {
            CommandType.VARIABLE -> variableSlots
            CommandType.FOR_START -> forSlots
            else -> null
        }
        if (dedicated != null) {
            return fieldKeys.map { key -> requireNotNull(dedicated[key]) { "専用配置が未定義です: type=$type field=$key" } }
        }
        return when (fieldKeys.size) {
            in 1..4 -> DistributedSettingSlots.slots(fieldKeys.size)
            5 -> fiveFieldSlots
            else -> error("設定フィールドの専用配置が未定義です: type=$type count=${fieldKeys.size}")
        }
    }

    fun size(type: CommandType, fieldCount: Int? = null): Int =
        if (type == CommandType.VARIABLE || fieldCount?.let { it > 4 } == true) 54 else 45

    fun backSlot(type: CommandType, fieldCount: Int? = null): Int = size(type, fieldCount) - 9
}
