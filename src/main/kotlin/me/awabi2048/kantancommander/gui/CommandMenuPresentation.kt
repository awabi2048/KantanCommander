package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType

/** コマンド選択画面で、実処理と実行順序の制御を混在させないための表示分類です。 */
internal enum class CommandCategory(val routeValue: String, val labelKey: String) {
    PROCESS("process", "gui.editor.category_process"),
    CONTROL("control", "gui.editor.category_control"),
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
        CommandType.DISK_CALL,
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
    }

    fun supportsContextOverride(type: CommandType): Boolean = when (type) {
        CommandType.TELEPORT,
        CommandType.GIVE_ITEM,
        CommandType.ENTITY_ACTION,
        CommandType.DISPLAY_TEXT,
        CommandType.CONDITION,
        CommandType.DISK_CALL,
        CommandType.VARIABLE,
        -> true

        CommandType.WAIT,
        CommandType.CONTEXT,
        CommandType.MERGE,
        CommandType.FOR_START,
        CommandType.FOR_END,
        CommandType.BREAK,
        CommandType.CONTINUE,
        -> false
    }
}

/** 設定数が多いコマンドは、意味上の組を崩さない専用配置を使用します。 */
internal object CommandSettingsSlotPolicy {
    private val variableSlots = mapOf(
        "scope" to 11,
        "name" to 13,
        "type" to 15,
        "operation" to 20,
        "value" to 24,
    )
    private val forSlots = mapOf(
        "startSource" to 11,
        "endSource" to 13,
        "stepSource" to 15,
        "startValue" to 20,
        "endValue" to 22,
        "stepValue" to 24,
        "inclusiveEnd" to 31,
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
        return DistributedSettingSlots.slots(fieldKeys.size)
    }
}
