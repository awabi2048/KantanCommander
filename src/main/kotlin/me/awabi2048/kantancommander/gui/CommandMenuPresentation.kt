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

/**
 * コマンド選択画面の表示分類です。
 *
 * 基礎タブを廃止した5分類とし、対象・領域の意味で分けます。エンティティ関連・
 * ブロックの操作・演出は簡易GUIでも編集でき、制御系・変数の操作はフルGUIでのみ
 * 編集します。新CommandTypeの追加時は [CommandPresentationPolicy.categories] の
 * 一箇所へ分類を足すだけで、両GUIのタブ・候補・簡易編集可否へ反映します。
 */
internal enum class CommandCategory(
    val routeValue: String,
    val labelKey: LocalizationKey<String>,
    val descriptionKey: LocalizationKey<List<String>>,
) {
    /** 移動・召喚・付与を含むエンティティ関連コマンドです。簡易GUIでも編集できます。 */
    ENTITY("entity", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_ENTITY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_ENTITY_DESCRIPTION),
    /** ブロック・建造物の操作です。簡易GUIでも編集できます。 */
    BUILD("build", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_BUILD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_BUILD_DESCRIPTION),
    /** テキスト表示・効果付与を含む演出です。簡易GUIでも編集できます。 */
    EFFECT("effect", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_EFFECT, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_EFFECT_DESCRIPTION),
    /** 待機を含む実行の流れの制御です。フルGUIでのみ編集します。 */
    CONTROL("control", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_CONTROL, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_CONTROL_DESCRIPTION),
    /** 変数の操作です。フルGUIでのみ編集します。 */
    VARIABLE("variable", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_VARIABLE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_VARIABLE_DESCRIPTION),
    ;

    companion object {
        // 廃止した基礎タブを示す旧経路は、先頭の簡易タブへ読み替えます。
        fun fromRoute(value: String?): CommandCategory = entries.firstOrNull { it.routeValue == value } ?: ENTITY
    }

    /** 簡易GUI（統合版専用インベントリ）で編集できる分類です。 */
    fun isSimple(): Boolean = this == ENTITY || this == BUILD || this == EFFECT
}

/**
 * コマンド型が増えた際に、分類・説明が暗黙の既定値へ流れないよう、
 * 全型の表示契約を網羅的なwhenで管理します。1つのコマンドを複数分類に
 * 表示できるよう、各型は所属分類の集合を返します。
 */
internal object CommandPresentationPolicy {
    /**
     * コマンド型と分類の一元対応表です。複数分類への所属もここで明示します。
     *
     * 基礎タブ廃止後の5分類とし、旧基礎コマンドは意味の近い分類へ移しました。
     * 移動・召喚・付与はエンティティ関連、表示・効果付与は演出、待機は制御系です。
     * 新CommandTypeの追加時はこの一箇所へ分類を足すだけで、両GUIのタブ・
     * 候補・簡易編集可否へ反映します。分類ごとの候補は10件以内に収め、
     * ページングは将来の増加時の補助手段として残します。
     */
    fun categories(type: CommandType): Set<CommandCategory> = when (type) {
        // アイテム配置は対象（エンティティ）と操作領域（ブロック）の両方から選べます。
        CommandType.PLACE_ITEM -> setOf(CommandCategory.ENTITY, CommandCategory.BUILD)

        CommandType.TELEPORT,
        CommandType.GIVE_ITEM,
        CommandType.SUMMON_ENTITY,
        CommandType.ENTITY_ACTION,
        CommandType.ENTITY_OPERATION,
        CommandType.ENTITY_DELETE,
        // issue #24: 群操作はEntity分類です。簡易GUIでも編集できます。
        CommandType.ENTITY_GROUP,
        -> setOf(CommandCategory.ENTITY)

        CommandType.BLOCK_OPERATION,
        // issue #11: 建造物は建築分類です。簡易GUIでも編集できます。
        CommandType.STRUCTURE_PLACE,
        -> setOf(CommandCategory.BUILD)

        CommandType.DISPLAY_TEXT,
        CommandType.APPLY_EFFECT,
        CommandType.PLAY_SOUND,
        CommandType.PARTICLE,
        CommandType.PARTICLE_GEOMETRY,
        CommandType.FIREWORK_LAUNCH,
        CommandType.CAMERA_SHAKE,
        -> setOf(CommandCategory.EFFECT)

        CommandType.WAIT,
        CommandType.CONDITION,
        CommandType.DISK_CALL,
        // issue #8: 「値を返す」は呼出先の実行を打ち切る制御コマンドです。
        CommandType.RETURN_VALUE,
        CommandType.MERGE,
        CommandType.FOR_START,
        CommandType.FOR_END,
        CommandType.BREAK,
        CommandType.CONTINUE,
        // 折り畳みノードは折り畳み操作だけで生成するため、新規追加の候補に出しません。
        CommandType.FOLDED_FOR,
        CommandType.FOLDED_IF,
        -> setOf(CommandCategory.CONTROL)

        CommandType.VARIABLE,
        CommandType.TEMP_SET,
        -> setOf(CommandCategory.VARIABLE)
    }

    /**
     * 簡易GUI（統合版専用インベントリ）で扱うコマンド種別です。
     *
     * 簡易分類（エンティティ関連・ブロックの操作・演出）の固定集合とし、制御系・
     * 変数の操作はフルGUI（ジェスチャーGUI）でのみ編集します。内部の構造化データは
     * フル形式のまま保持し、ここでは表示・編集の入口だけを制限します。
     */
    val SIMPLE_TYPES: List<CommandType> =
        CommandType.entries.filter { type -> categories(type).any { it.isSimple() } }

    /** 簡易GUIで設定変更できるノードかを返します。制御・変数系は表示のみです。 */
    fun isSimpleEditable(type: CommandType): Boolean = type in SIMPLE_TYPES

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
        if (category !in CommandPresentationPolicy.categories(type)) return@filter false
        when (type) {
            CommandType.FOR_END -> false
            CommandType.MERGE -> mergeAvailable
            CommandType.BREAK, CommandType.CONTINUE -> insideForBody
            // 折り畳みノードは折り畳み操作だけで生成し、新規追加の候補に出しません。
            CommandType.FOLDED_FOR, CommandType.FOLDED_IF -> false
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
