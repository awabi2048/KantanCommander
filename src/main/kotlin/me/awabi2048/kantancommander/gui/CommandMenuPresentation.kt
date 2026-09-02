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
    EXECUTION("execution", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_PROCESS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_PROCESS_DESCRIPTION),
    CONTROL("control", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_CONTROL, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_CONTROL_DESCRIPTION),
    ;

    companion object {
        fun fromRoute(value: String?): CommandCategory = entries.firstOrNull { it.routeValue == value } ?: EXECUTION
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
        CommandType.BLOCK_OPERATION,
        CommandType.ENTITY_DELETE,
        -> CommandCategory.EXECUTION

        CommandType.WAIT,
        CommandType.CONDITION,
        CommandType.CONTEXT,
        CommandType.DISK_CALL,
        CommandType.VARIABLE,
        CommandType.MERGE,
        CommandType.FOR_START,
        CommandType.FOR_END,
        CommandType.BREAK,
        CommandType.CONTINUE,
        -> CommandCategory.CONTROL

    }

    fun supportsContextOverride(type: CommandType): Boolean = type.supportsContextOverride()
}

/**
 * コマンド選択画面に表示できる種別を、実際の挿入状態から一度だけ決めます。
 *
 * MERGEは、枝を後続処理へ再合流させる場合だけ表示します。通常コマンドは、
 * 再合流する枝とその枝で正常終了する終端のどちらにも追加できます。
 */
internal object CommandPickerTypePolicy {
    fun types(
        category: CommandCategory,
        mergeAvailable: Boolean,
        insideForBody: Boolean,
    ): List<CommandType> = CommandType.entries.filter { type ->
        if (CommandPresentationPolicy.category(type) != category) return@filter false
        when (type) {
            CommandType.FOR_END -> false
            CommandType.MERGE -> mergeAvailable
            CommandType.BREAK, CommandType.CONTINUE -> insideForBody
            else -> true
        }
    }
}

/** 設定数が多いコマンドは、意味上の組を崩さない専用配置を使用します。 */
internal object CommandSettingsSlotPolicy {
    private val fiveFieldSlots = listOf(19, 20, 21, 28, 29)
    private val extendedFieldSlots = listOf(
        10, 11, 12, 19, 20, 21, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42,
    )
    private val variableSlots = mapOf(
        "operation" to 19,
        "name" to 20,
        "type" to 21,
        "changeMode" to 28,
        "value" to 29,
    )
    private val forSlots = mapOf(
        "count" to 20,
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
            in 6..extendedFieldSlots.size -> extendedFieldSlots.take(fieldKeys.size)
            else -> error("設定フィールドの専用配置が未定義です: type=$type count=${fieldKeys.size}")
        }
    }

    fun size(type: CommandType, fieldCount: Int? = null): Int = 54

    fun backSlot(type: CommandType, fieldCount: Int? = null): Int = 45
}
