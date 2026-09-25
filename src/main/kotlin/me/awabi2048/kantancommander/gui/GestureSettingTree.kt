package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.EntityPropertyDefinition
import me.awabi2048.kantancommander.model.EntityPropertyValueKind
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.conditionSubject
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material

/**
 * ジェスチャー設定を意味上の木として表す選択肢です。
 *
 * 画面IDや親画面IDを持たせず、選択肢が子要素を持つかどうかだけを
 * 「詳細設定が存在する」という事実として表現します。これにより、
 * 対象・位置・条件を同じ描画／クリック規則へ載せられます。
 */
data class GestureSettingTreeNode(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    /** 実行前提を満たさない選択肢は表示したまま操作だけを無効化します。 */
    val enabled: Boolean = true,
    val description: String = "",
    /** 選択不可の場合だけ通常説明を上書きする、操作不能理由のホバーです。 */
    val disabledHoverText: String? = null,
    val children: List<GestureSettingTreeNode> = emptyList(),
    /** 選択肢の意味。択一と複数選択可能な設定を同じ木で表現します。 */
    val selectionMode: GestureSettingSelectionMode = GestureSettingSelectionMode.EXCLUSIVE,
    /** 現在値が初期値のままか、明示設定済みかを表します。 */
    val valueState: GestureSettingValueState = GestureSettingValueState.INITIAL,
    /** 実行前検証で要確認（未設定・不正）と判定された設定に属するかを表します。 */
    val attention: Boolean = false,
    /**
     * 下揃えで配置する詳細項目かを表します。
     *
     * プリセット等の上位項目は上揃えのまま、詳細設定・一時変数参照等の
     * 下位項目だけを下端基準で配置します。上からの位置指定ではなく
     * 下端からの行番号で定まるため、項目数の増減で位置がずれません。
     */
    val bottomAligned: Boolean = false,
    /**
     * バニラ翻訳などのComponent表示です。指定時は描画で優先します。
     * 文字列のlabelはフォールバック・検証用に維持します。
     * 位置引数の既存呼び出しを壊さないため末尾に置きます。
     */
    val labelComponent: net.kyori.adventure.text.Component? = null,
    /**
     * クリックの意味です。未指定なら子を持つ項目は「選択後に詳細」、葉は「選択」として扱います。
     * 詳細だけを開く項目では DETAILS_ONLY を指定し、選択表示や選択値の変更を行いません。
     */
    val interactionMode: GestureSettingInteractionMode? = null,
) {
    val hasChildren: Boolean
        get() = children.isNotEmpty()

    val effectiveInteractionMode: GestureSettingInteractionMode
        get() = interactionMode ?: if (hasChildren) {
            GestureSettingInteractionMode.SELECT_AND_DETAILS
        } else {
            GestureSettingInteractionMode.SELECT
        }

    fun find(nodeId: String): GestureSettingTreeNode? {
        if (id == nodeId) return this
        return children.firstNotNullOfOrNull { it.find(nodeId) }
    }
}

/** 設定ドメインが許す選択方式。表示色だけでなく入力契約の基準にもします。 */
enum class GestureSettingSelectionMode {
    EXCLUSIVE,
    MULTIPLE,
}

/** クリック時に項目の選択状態と詳細画面をどう扱うかを表します。 */
enum class GestureSettingInteractionMode {
    /** 選択状態を更新し、同じ項目の再クリックで詳細へ進みます。 */
    SELECT_AND_DETAILS,

    /** 項目の選択だけを行い、子があっても詳細へは進みません。 */
    SELECT,

    /** 項目の選択状態を変えず、クリックで詳細へ進みます。 */
    DETAILS_ONLY,
}

/** 設定値の状態。初期値と明示設定済みを色・文言へ同時に投影します。 */
enum class GestureSettingValueState {
    INITIAL,
    CONFIGURED,
}

/**
 * エンティティ個別設定の操作対象と保存値を独立して投影する共通規則です。
 * selectedは「今どの項目を操作するか」、configuredは「その項目に有効な値が保存済みか」を表します。
 */
internal object EntityPropertyChoicePolicy {
    fun state(
        activePropertyId: String?,
        definition: EntityPropertyDefinition,
        rawValue: String,
    ): EntityPropertyChoiceState = EntityPropertyChoiceState(
        selected = activePropertyId == definition.id,
        // エンティティ操作は現在の項目の値をPARAM_VALUEへ一つだけ保存します。
        // 対応する項目だけ設定済みにし、共用欄の値で別項目まで色付けしないようにします。
        configured = activePropertyId == definition.id && isValueConfigured(definition, rawValue),
    )

    /** falseも設定値です。空欄や壊れたbooleanをfalseへ読み替えません。 */
    fun isValueConfigured(definition: EntityPropertyDefinition, rawValue: String): Boolean = when (definition.valueKind) {
        EntityPropertyValueKind.BOOLEAN -> rawValue.toBooleanStrictOrNull() != null
        EntityPropertyValueKind.ENUM -> definition.enumChoices.any { it.id == rawValue }
        EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> rawValue.isNotBlank()
    }
}

internal data class EntityPropertyChoiceState(
    val selected: Boolean,
    val configured: Boolean,
)

/**
 * 設定カードの表示規則を一箇所へ集約します。
 *
 * 設定項目の通常テクスチャは薄灰色へ統一し、ホバー中もテクスチャを変えません。
 * タブの選択状態は縁取りではなく右へ幅15%伸ばし、選択中タブの背景はシアンの
 * テラコッタで示します。テキスト位置は維持します。
 * 要確認状態はタブテキストの§c（Adventureの赤色）で表します。
 * 画面ごとの色分岐を増やさず、視認性を確保します。
 */
internal object GestureSettingVisualPolicy {
    /** 選択中タブを右へ伸ばす比率です。左端を固定し、幅の15%だけ右へ拡張します。 */
    const val SELECTED_TAB_EXTENSION_RATIO = 0.15

    /** 選択中なら拡張後の幅、非選択なら基準幅を返します。 */
    fun selectedTabWidth(baseWidth: Double, selected: Boolean): Double =
        if (selected) baseWidth * (1.0 + SELECTED_TAB_EXTENSION_RATIO) else baseWidth

    /**
     * 選択中タブの中心Xを返します。
     * 左端を固定するため、拡張分の半分だけ右へずらします。テキスト位置は動かしません。
     */
    fun selectedTabCenterX(baseCx: Double, baseWidth: Double, selected: Boolean): Double =
        if (selected) baseCx + baseWidth * SELECTED_TAB_EXTENSION_RATIO / 2.0 else baseCx
    // 設定項目は値状態や択一／複数選択の違いで背景色を変えず、通常時は薄灰色へ
    // 統一します。値状態は枠・テキスト・警告などの意味表現から独立させます。
    fun material(
        selectionMode: GestureSettingSelectionMode,
        valueState: GestureSettingValueState,
    ): Material = Material.LIGHT_GRAY_CONCRETE

    // 既存の呼び出し形を維持します。内部ではテクスチャとハイライトを分離します。
    fun material(
        selectionMode: GestureSettingSelectionMode,
        valueState: GestureSettingValueState,
        selected: Boolean,
        attention: Boolean = false,
    ): Material = material(selectionMode, valueState)

    /**
     * タブ列の背景素材です。
     * 非選択は薄灰色、選択中はシアンのテラコッタへ変えます。右側カード等の設定項目は
     * この関数を使わず、選択状態にかかわらず薄灰色のままにします。
     */
    fun tabMaterial(selected: Boolean): Material =
        if (selected) Material.CYAN_TERRACOTTA else Material.LIGHT_GRAY_CONCRETE

    /** 警告タブの文字色です。NamedTextColor.REDはレガシー表記の§cに相当します。 */
    fun tabTextColor(attention: Boolean): NamedTextColor? =
        if (attention) NamedTextColor.RED else null

    /** タブ内部・子画面の選択項目の外周枠に使う素材です。 */
    fun nonTabOutlineMaterial(selected: Boolean): Material? =
        if (selected) Material.WHITE_CONCRETE else null

    /**
     * 選択状態と保存状態から設定カードの枠素材を決めます。
     * 現在選択中は白枠を優先し、それ以外の設定済み項目は選択方式にかかわらず
     * 空色枠で示します。択一／複数選択は入力契約を決め、保存状態の可視性は隠しません。
     */
    fun choiceOutlineMaterial(choice: GestureSettingTreeNode): Material? =
        entityChoiceOutlineMaterial(
            selected = choice.selected,
            configured = choice.valueState == GestureSettingValueState.CONFIGURED,
        )

    /** クリック先が子画面なら§b、値入力・値変更なら§6、操作不能なら灰色です。 */
    fun settingChoiceTextColor(choice: GestureSettingTreeNode): NamedTextColor = when {
        !choice.enabled -> DisabledChoiceVisualPolicy.textColor
        choice.hasChildren -> NamedTextColor.AQUA
        else -> NamedTextColor.GOLD
    }

    /**
     * エンティティ操作の設定済み縁色です。
     *
     * 「選択している状態」（排他的）と「値を設定済みである状態」（非排他的）を
     * 別チャネルで表します。選択中は白縁、未選択の設定済みは空色縁とします。
     * 真偽値の偽値も入力として扱い、設定済みに含めます。
     */
    fun entityChoiceOutlineMaterial(selected: Boolean, configured: Boolean): Material? = when {
        selected -> nonTabOutlineMaterial(true)
        configured -> Material.LIGHT_BLUE_CONCRETE
        else -> null
    }
}

/** 木構造上の現在位置。表示状態と戻る経路を同じ値から復元します。 */
data class GestureSettingTreePath(
    val fieldKey: String,
    val role: CommandSettingRole?,
    val nodeIds: List<String> = emptyList(),
) {
    /** 現在のフレームの選択だけを置き換え、別枝の履歴を混ぜません。 */
    fun selectAtDepth(depth: Int, nodeId: String): GestureSettingTreePath {
        val safeDepth = depth.coerceAtLeast(0)
        val ids = nodeIds.take(safeDepth).toMutableList()
        ids += nodeId
        return copy(nodeIds = ids)
    }

    /** 子フレームへ進む経路を追加します。既に選択済みなら重複させません。 */
    fun enterChild(nodeId: String): GestureSettingTreePath =
        if (nodeIds.lastOrNull() == nodeId) this else copy(nodeIds = nodeIds + nodeId)

    /** 親フレームへ戻るため、末尾の選択を一つ取り除きます。 */
    fun leaveChild(): GestureSettingTreePath = copy(nodeIds = nodeIds.dropLast(1))
}

/**
 * 設定木の直下項目を押した後の共通遷移です。
 *
 * 一回目のクリックは選択状態の更新だけを行い、子を持つ同じ項目を再クリック
 * した場合だけ子フレームへ進みます。葉を選んだ後も現在フレームに留めるため、
 * 兄弟項目の選択可能性を画面種類ごとに変えません。
 */
internal enum class GestureSettingSelectionAction {
    STAY_ON_FRAME,
    ENTER_CHILD,
}

internal fun settingSelectionAction(
    wasSelected: Boolean,
    interactionMode: GestureSettingInteractionMode,
): GestureSettingSelectionAction = when (interactionMode) {
    GestureSettingInteractionMode.SELECT_AND_DETAILS -> if (wasSelected) {
        GestureSettingSelectionAction.ENTER_CHILD
    } else {
        GestureSettingSelectionAction.STAY_ON_FRAME
    }
    GestureSettingInteractionMode.SELECT -> GestureSettingSelectionAction.STAY_ON_FRAME
    GestureSettingInteractionMode.DETAILS_ONLY -> GestureSettingSelectionAction.ENTER_CHILD
}

/**
 * 召喚時個別設定で選択中の項目IDを経路末尾から復元します。
 *
 * 召喚には項目パス欄がないため、有限候補子画面の表示対象は保存値から
 * 決められません。親画面の行選択だけを根拠にし、接頭辞のない経路では
 * nullを返して空表示にします。
 */
internal fun summonPropertyIdFromPath(pathLastNodeId: String?): String? {
    if (pathLastNodeId == null || !pathLastNodeId.startsWith("summonData:")) return null
    return pathLastNodeId.removePrefix("summonData:").takeIf(String::isNotBlank)
}

internal fun settingSelectionAction(
    wasSelected: Boolean,
    hasChildren: Boolean,
): GestureSettingSelectionAction = settingSelectionAction(
    wasSelected,
    if (hasChildren) GestureSettingInteractionMode.SELECT_AND_DETAILS else GestureSettingInteractionMode.SELECT,
)

/** 数値変更方式の選択後に必要な操作です。乱数だけは同じ選択肢の再クリックで範囲入力へ進みます。 */
internal enum class VariableModeSelectionAction {
    SAVE_MODE,
    EDIT_RANDOM_RANGE,
}

internal fun variableModeSelectionAction(
    mode: VariableChangeMode,
    wasSelectedInCurrentRoute: Boolean,
): VariableModeSelectionAction = if (
    mode == VariableChangeMode.RANDOM && wasSelectedInCurrentRoute
) {
    VariableModeSelectionAction.EDIT_RANDOM_RANGE
} else {
    VariableModeSelectionAction.SAVE_MODE
}

/**
 * 経路末尾の選択を同階層へ排他的に適用します。
 *
 * 経路が現在の候補一覧を指す場合、その1件だけを選択表示にし、
 * 保存値由来の他候補の選択表示を抑止します。経路が一覧外（子画面の
 * 詳細ID等）を指す場合や空の場合は何も変えず、保存値表示を維持します。
 * これにより、詳細カードの初回クリック直後にプリセットと詳細が
 * 二重選択表示になることを防ぎます。
 */
internal fun applyPathSelectionExclusive(
    choices: List<GestureSettingTreeNode>,
    pathLastNodeId: String?,
): List<GestureSettingTreeNode> {
    if (pathLastNodeId == null || choices.none { it.id == pathLastNodeId }) return choices
    return choices.map { choice ->
        choice.copy(selected = choice.id == pathLastNodeId)
    }
}

/** 詳細子画面をまたぐための設定木上の1フレームです。 */
data class GestureSettingFrame(
    val context: CommandSettingContext,
    val fieldKey: String,
    val screen: GestureSettingScreen,
)

/** 構造化設定エディターとジェスチャー画面の意味画面を結ぶ共通変換です。 */
internal fun gestureSettingScreenFor(editor: CommandSettingEditor): GestureSettingScreen? = when (editor) {
    CommandSettingEditor.TARGET -> GestureSettingScreen.TARGET
    CommandSettingEditor.POSITION -> GestureSettingScreen.POSITION
    CommandSettingEditor.FACING -> GestureSettingScreen.FACING
    CommandSettingEditor.LOCATION -> GestureSettingScreen.LOCATION
    CommandSettingEditor.CONDITION_KIND -> GestureSettingScreen.CONDITION_KIND
    CommandSettingEditor.CONDITION_DETAIL -> GestureSettingScreen.CONDITION_DETAIL
    CommandSettingEditor.DISPLAY_MODE -> GestureSettingScreen.DISPLAY_MODE
    CommandSettingEditor.ENTITY_ACTION -> GestureSettingScreen.ENTITY_ACTION
    CommandSettingEditor.ENTITY_DATA_ROOT -> GestureSettingScreen.ENTITY_DATA_ROOT
    CommandSettingEditor.SUMMON_DATA_ROOT -> GestureSettingScreen.SUMMON_DATA_ROOT
    CommandSettingEditor.ENTITY_ATTRIBUTE_SELECT -> GestureSettingScreen.ENTITY_ATTRIBUTE_SELECT
    CommandSettingEditor.ENTITY_TAG_LIST -> GestureSettingScreen.ENTITY_TAG_LIST
    CommandSettingEditor.ENTITY_PROPERTY_VALUE -> GestureSettingScreen.ENTITY_PROPERTY_VALUE
    CommandSettingEditor.PLACE_DESTINATION -> GestureSettingScreen.PLACE_DESTINATION
    CommandSettingEditor.ENTITY_EQUIPMENT_SLOT -> GestureSettingScreen.ENTITY_EQUIPMENT_SLOT
    CommandSettingEditor.ENTITY_OVERWRITE -> GestureSettingScreen.ENTITY_OVERWRITE
    CommandSettingEditor.VARIABLE_SCOPE -> GestureSettingScreen.VARIABLE_SCOPE
    CommandSettingEditor.VARIABLE_NAME -> GestureSettingScreen.VARIABLE_NAME
    CommandSettingEditor.VARIABLE_PROPERTY -> GestureSettingScreen.VARIABLE_PROPERTY
    CommandSettingEditor.VARIABLE_TYPE -> GestureSettingScreen.VARIABLE_TYPE
    CommandSettingEditor.VARIABLE_OPERATION -> GestureSettingScreen.VARIABLE_OPERATION
    CommandSettingEditor.VARIABLE_CHANGE_MODE -> GestureSettingScreen.VARIABLE_CHANGE_MODE
    CommandSettingEditor.VARIABLE_VALUE -> GestureSettingScreen.VARIABLE_VALUE
    CommandSettingEditor.CONDITION_INVERSION -> GestureSettingScreen.CONDITION_INVERSION
    CommandSettingEditor.CAMERA_SHAKE_TYPE -> GestureSettingScreen.CAMERA_SHAKE_TYPE
    CommandSettingEditor.SOUND_SCOPE -> GestureSettingScreen.SOUND_SCOPE
    CommandSettingEditor.SOUND_OPERATION -> GestureSettingScreen.SOUND_OPERATION
    CommandSettingEditor.SOUND_TARGET_POSITION -> GestureSettingScreen.SOUND_TARGET_POSITION
    // 表示対象タブは通常の対象選択を親画面へ直接表示します。単独の中間画面を挟みません。
    CommandSettingEditor.PARTICLE_DISPLAY -> GestureSettingScreen.TARGET
    CommandSettingEditor.PARTICLE_GEOMETRY -> GestureSettingScreen.PARTICLE_GEOMETRY
    CommandSettingEditor.BLOCK_OPERATION -> GestureSettingScreen.BLOCK_OPERATION
    // issue #11: 建造物名・回転・反転の各編集経路を意味画面へ結びます。
    CommandSettingEditor.STRUCTURE_NAME -> GestureSettingScreen.STRUCTURE_NAME
    CommandSettingEditor.STRUCTURE_ROTATION -> GestureSettingScreen.STRUCTURE_ROTATION
    CommandSettingEditor.STRUCTURE_MIRROR -> GestureSettingScreen.STRUCTURE_MIRROR
    // issue #24: 群操作・群名の各編集経路を意味画面へ結びます。
    CommandSettingEditor.ENTITY_GROUP_OPERATION -> GestureSettingScreen.ENTITY_GROUP_OPERATION
    CommandSettingEditor.ENTITY_GROUP_NAME -> GestureSettingScreen.ENTITY_GROUP_NAME
    CommandSettingEditor.TEXT -> null
}

/**
 * 単一項目経由を避ける直行先を返します。
 *
 * 次の画面は対象選択が唯一の掘り下げのため、経由画面を挟まず対象画面へ
 * 直行します。SOUND_TARGET_POSITION は停止操作時、CONDITION_DETAIL は
 * 他エンティティ対象時に該当します。それ以外は null を返し、
 * 通常の画面遷移を行います。操作種別・対象種別の切替はタブの開き直しで追従し、
 * 経路開始時に判定します。文脈の役割は対象選択用へ正規化します。
 */
internal fun shortCircuitTargetFrame(node: CommandNode, frame: GestureSettingFrame): GestureSettingFrame? {
    val direct = when (frame.screen) {
        GestureSettingScreen.SOUND_TARGET_POSITION ->
            node.type == CommandType.PLAY_SOUND && SoundSettings.operation(node) == SoundOperation.STOP
        GestureSettingScreen.CONDITION_DETAIL ->
            node.type == CommandType.CONDITION && node.conditionSubject() == ConditionSubject.OTHER_ENTITY
        else -> false
    }
    if (!direct) return null
    return frame.copy(
        context = frame.context.copy(role = CommandSettingRole.NODE_TARGET),
        screen = GestureSettingScreen.TARGET,
    )
}
