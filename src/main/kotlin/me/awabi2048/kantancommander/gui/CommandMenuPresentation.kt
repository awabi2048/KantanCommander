package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import me.awabi2048.kantancommander.model.CommandType
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material

/** 一般の操作不能なUI部品の見た目です。選択肢専用の表示はDisabledChoiceVisualPolicyを使います。 */
internal object DisabledGuiVisualPolicy {
    val material: Material = Material.LIGHT_GRAY_CONCRETE
}

/**
 * 「選択肢だが現在の状態では選べない」項目の共通表示です。
 * 背景や単なる実行不能ボタンはDisabledGuiVisualPolicyのまま扱い、選択肢だけを
 * 赤コンクリートと灰色文字で明示します。両GUIで同じ意味を同じ外観へ投影します。
 */
internal object DisabledChoiceVisualPolicy {
    val material: Material = Material.RED_CONCRETE
    val textColor: NamedTextColor = NamedTextColor.GRAY
    val nameStyle: GuiNameStyle = GuiNameStyle.MUTED

    /** 無効項目だけ、指定された理由ホバーを通常説明へ優先して表示します。 */
    fun hoverText(enabled: Boolean, normal: String?, disabled: String?): String? =
        if (enabled) normal else disabled?.takeIf(String::isNotBlank) ?: normal

    /** インベントリGUIのLoreも同じ優先規則で、無効理由を通常説明へ上書きします。 */
    fun hoverLines(enabled: Boolean, normal: List<String>, disabled: List<String>?): List<String> =
        if (enabled) normal else disabled?.takeIf { it.isNotEmpty() } ?: normal
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
 * コマンド型が増えた際に、分類・説明が暗黙の既定値へ流れないよう、
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
        CommandType.PARTICLE,
        CommandType.APPLY_EFFECT,
        CommandType.CAMERA_SHAKE,
        CommandType.BLOCK_OPERATION,
        CommandType.ENTITY_DELETE,
        -> CommandCategory.EXECUTION

        CommandType.WAIT,
        CommandType.CONDITION,
        CommandType.DISK_CALL,
        CommandType.VARIABLE,
        CommandType.TEMP_SET,
        CommandType.MERGE,
        CommandType.FOR_START,
        CommandType.FOR_END,
        CommandType.BREAK,
        CommandType.CONTINUE,
        -> CommandCategory.CONTROL

    }

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

/**
 * ジェスチャー下部PICKERの候補配置を一つの契約へ集約します。
 *
 * 設定候補と同じ2列グリッドを使い、1ページ10件を2列×5行へ収めます。
 * コマンド種別が増えて10件を超えた場合だけ、同じ配置のまま次ページへ送ります。
 */
internal object GestureCommandPickerLayoutPolicy {
    const val PAGE_SIZE = 10
    const val COLUMNS = 2
    const val CARD_WIDTH = 0.66
    const val CARD_HEIGHT = 0.10
    const val CARD_TOP_Y = 0.20
    const val CARD_ROW_PITCH = 0.11
    const val LEFT_COLUMN_X = -0.10
    const val RIGHT_COLUMN_X = 0.67

    fun pageCount(candidateCount: Int): Int {
        require(candidateCount >= 0) { "候補数は0以上で指定してください: $candidateCount" }
        return ((candidateCount + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }

    fun rowCount(candidateCount: Int): Int {
        require(candidateCount >= 0) { "候補数は0以上で指定してください: $candidateCount" }
        return (candidateCount + COLUMNS - 1) / COLUMNS
    }

    fun columnX(column: Int): Double = when (column) {
        0 -> LEFT_COLUMN_X
        1 -> RIGHT_COLUMN_X
        else -> error("PICKER列は0または1で指定してください: $column")
    }

    fun rowY(row: Int): Double {
        require(row >= 0) { "PICKER行は0以上で指定してください: $row" }
        return CARD_TOP_Y - row * CARD_ROW_PITCH
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
