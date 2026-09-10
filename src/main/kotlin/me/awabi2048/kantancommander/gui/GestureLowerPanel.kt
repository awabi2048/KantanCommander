package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccess
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccessPolicy
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOutline
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import me.awabi2048.kantancommander.gui.declarative.ContentFrame
import me.awabi2048.kantancommander.gui.declarative.DeclarativeSettings
import me.awabi2048.kantancommander.gui.declarative.DeclarativeVariables
import me.awabi2048.kantancommander.gui.declarative.DescriptionDetail
import me.awabi2048.kantancommander.gui.declarative.SettingsActionCard
import me.awabi2048.kantancommander.gui.declarative.SettingsChoiceCard
import me.awabi2048.kantancommander.gui.declarative.SettingsTabEntry
import me.awabi2048.kantancommander.gui.declarative.SettingsValueRow
import me.awabi2048.kantancommander.gui.declarative.contentFrame
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenDefinition
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiView
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisibilityPolicy
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.error.displayKey
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.WorldVariableUsageScanResult
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ControlBlockStateKind
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.ParticleDisplayMode
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.hasDiskContent
import me.awabi2048.kantancommander.model.selectedControlBlockStates
import me.awabi2048.kantancommander.util.KcI18n
import me.awabi2048.kantancommander.gui.declarative.CenteredLine
import me.awabi2048.kantancommander.gui.declarative.ConfirmTexts
import me.awabi2048.kantancommander.gui.declarative.DeclarativeTest
import me.awabi2048.kantancommander.gui.declarative.TestConfirmData
import me.awabi2048.kantancommander.gui.declarative.TestToggleData
import me.awabi2048.kantancommander.gui.declarative.DeclarativeConfirm
import me.awabi2048.kantancommander.gui.declarative.DeclarativePicker
import me.awabi2048.kantancommander.gui.declarative.KantanLayoutBridge
import me.awabi2048.kantancommander.gui.declarative.PickerArrow
import me.awabi2048.kantancommander.gui.declarative.PickerCard
import me.awabi2048.kantancommander.gui.declarative.PickerCategory
import me.awabi2048.kantancommander.gui.declarative.PickerData
import me.awabi2048.kantancommander.gui.declarative.PickerPager
import me.awabi2048.kantancommander.gui.declarative.ScriptSettingCard
import me.awabi2048.kantancommander.gui.declarative.WorldVariableDeleteData
import me.awabi2048.kantancommander.gui.declarative.WorldVariableEntry
import me.awabi2048.kantancommander.gui.declarative.WorldVariableTypeData
import me.awabi2048.kantancommander.gui.declarative.WorldVariableTypeOption
import me.awabi2048.kantancommander.gui.declarative.WorldVariablesData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.sqrt

/** 既存の選択肢生成を木のノードとして扱うための局所的な別名です。 */
private typealias SettingChoice = GestureSettingTreeNode

/**
 * ジェスチャーエディターの下部パネルのビュー生成を担います。
 *
 * - SETTINGS: 左タブ列＝設定フィールド、右詳細＝現在値＋説明。設定木の直下は親画面で編集
 * - PICKER: 左タブ列＝コマンドカテゴリ（EXECUTION/CONTROL）、右詳細＝種別一覧
 * - CONFIRM／ワールド内変数削除確認: 上部エディターが子画面（openChild）として開く
 *
 * 親画面は左タブ列＋右詳細の分割型、子画面は子画面全体を使う集中型です。
 * 座標は画面中央原点・ブロック単位です。
 */
class GestureLowerPanel(
    private val plugin: KantanCommanderPlugin,
    private val onAction: (GestureGuiActionContext) -> Unit = {},
    private val screenAccess: GestureGuiAccess = GestureGuiAccess.OWNER_ONLY,
    private val screenAccessPolicy: GestureGuiAccessPolicy? = null,
    private val screenVisibilityPolicy: GestureGuiVisibilityPolicy? = null,
) {
    val LOWER_SCREEN_ID = "gesture-editor-lower"
    /** 個別設定専用。親の下部画面へモーダルに重ねます。 */
    val SETTING_CHILD_SCREEN_ID = "gesture-editor-setting-child"
    val CONFIRM_SCREEN_ID = "gesture-editor-confirm"
    val TEST_CONFIRM_SCREEN_ID = "gesture-editor-test-confirm"

    /** 子画面の面積を親の50%にするための縦横縮尺です。宣言実装と共有します。 */
    internal val SETTING_CHILD_SCALE = sqrt(0.5)

    fun build(
        state: GestureEditorState,
        player: Player,
        attention: GestureAttentionState = GestureAttentionState.EMPTY,
        suppressHighlight: Boolean = false,
        // issue #18: Slider操作中の仮値です。保存値ではなく表示だけに使います。
        fieldPreviews: Map<FieldPreviewKey, String?> = emptyMap(),
    ): GestureGuiView {
        return when (state.lowerMode) {
            GestureLowerMode.SETTINGS -> buildSettings(state, player, attention, suppressHighlight, fieldPreviews)
            GestureLowerMode.PICKER -> buildPickerNew(state, player)
            GestureLowerMode.SETTING_CHOICES -> buildSettingChoicesParentNew(
                state,
                player,
                attention,
                suppressHighlight = suppressHighlight,
            )
            GestureLowerMode.WORLD_VARIABLES -> buildSettings(state, player, attention, suppressHighlight)
            // 型選択はワールド変数一覧の子画面から遷移するため、子画面が何らかの理由で
            // 閉じられた場合でも、親側へ型選択の内部座標を誤描画しないようにします。
            GestureLowerMode.WORLD_VARIABLE_TYPE -> buildSettings(state, player, attention, suppressHighlight)
            // 削除確認も一覧と同じ子画面契約で表示し、親画面へ確認ボタンを混在させません。
            GestureLowerMode.WORLD_VARIABLE_DELETE_CONFIRM -> buildSettings(state, player, attention, suppressHighlight)
            GestureLowerMode.CONFIRM -> buildConfirmNew(state, player)
            GestureLowerMode.TEST_CONFIRM -> buildTestConfirmationNew(state, player, child = true)
            GestureLowerMode.TEST_STATUS -> buildTestStatusNew(state, player)
            GestureLowerMode.TEST_RESULT -> buildTestResultNew(state, player)
            // issue #28: ID候補一覧専用の子画面です。設定木の選択経路とは分離します。
            GestureLowerMode.RESOURCE_PICKER -> buildResourcePicker(state, player)
        }
    }

    /** 親のタブ列を継承せず、子画面全体で詳細設定を生成します（宣言実装）。 */
    fun buildSettingChild(
        state: GestureEditorState,
        player: Player,
        attention: GestureAttentionState = GestureAttentionState.EMPTY,
        suppressHighlight: Boolean = false,
    ): GestureGuiView = buildSettingChoicesChildNew(state, player, attention, suppressHighlight)

    /**
     * issue #28 のID候補一覧子画面です。
     *
     * 親の設定画面へ大量候補を置かず、正方形の小型アイコンを縦横に並べて
     * 選択します。一覧選択とDialog入力は同じ保存値への別経路であり、
     * 既存のテキスト入力によるID直接指定は残します。
     */
    private fun buildResourcePicker(state: GestureEditorState, player: Player): GestureGuiView {
        val frame = contentFrame(GestureGuiPanel())
        val nodes = mutableListOf<GestureGuiNode>()
        val context = state.settingContext
        val fieldKey = state.resourcePickerFieldKey
        val node = context?.let { plugin.scripts.load(it.scriptId)?.graph?.nodes?.get(it.nodeId) }
        val kind = fieldKey?.let(ResourcePicker::kindForFieldKey)
        if (context == null || node == null || fieldKey == null || kind == null) {
            DeclarativeSettings.hintSection(nodes, frame, Component.text("候補を表示できません"), x = 0.2, y = 0.12, id = "resource-hint")
            DeclarativeSettings.backSection(nodes, frame, Component.text("戻る"), Bukkit.createBlockData(Material.BROWN_CONCRETE), 0.2, CHILD_BACK_WIDTH)
            return finishResourcePicker(nodes)
        }
        val ids = CommandDialogSpecs.registeredIds(fieldKey)
        val pageCount = maxOf(ResourcePicker.pageCount(ids.size), 1)
        val page = state.resourcePickerPage.coerceIn(0, pageCount - 1)
        val currentRaw = CommandDialogSpecs.normalize(fieldKey, node.string(fieldKey))
        val fieldLabel = state.settingFieldKey
            ?.let { key -> CommandSettingsModel.gestureVisibleFields(node).firstOrNull { it.key == key }?.label }
            ?.let { KcI18n.text(player, it) } ?: fieldKey
        DeclarativeSettings.valueRowsSection(
            nodes,
            frame,
            rows = listOf(SettingsValueRow("resource-title", fieldLabel, "${page + 1} / $pageCount", RESOURCE_TITLE_Y)),
            labelX = -0.08,
            valueX = 0.52,
        )
        val cards = ResourcePicker.pageItems(ids, page).mapIndexed { index, id ->
            val selected = CommandDialogSpecs.normalize(fieldKey, id) == currentRaw ||
                id == node.string(fieldKey)
            val col = index % ResourcePicker.COLUMNS
            val row = index / ResourcePicker.COLUMNS
            me.awabi2048.kantancommander.gui.declarative.ResourcePickerCard(
                elementId = "lower-resource-pick:$id",
                icon = ItemStack(ResourcePickerIcons.resolve(kind, id)),
                background = Bukkit.createBlockData(
                    if (selected) Material.CYAN_TERRACOTTA else Material.GRAY_CONCRETE,
                ),
                outline = if (selected) {
                    GestureSettingVisualPolicy.nonTabOutlineMaterial(true)?.let { Bukkit.createBlockData(it) }
                } else null,
                cx = RESOURCE_GRID_LEFT_X + col * RESOURCE_GRID_PITCH_X,
                cy = RESOURCE_GRID_TOP_Y - row * RESOURCE_GRID_PITCH_Y,
                w = RESOURCE_CELL_W,
                h = RESOURCE_CELL_H,
                hover = multiLineHover(
                    listOf(ResourcePicker.displayName(kind, id), id),
                    x = HOVER_SLOT_X,
                    y = HOVER_SLOT_Y,
                ),
                visualIndex = index,
            )
        }
        DeclarativeSettings.resourcePickerSection(nodes, frame, cards)
        if (pageCount > 1) {
            DeclarativeSettings.pagerSection(
                nodes,
                frame,
                idPrefix = "resource-picker",
                elementPrefix = "lower-resource-page",
                page = page,
                pageCount = pageCount,
                centerX = RESOURCE_CENTER_X,
                centerY = RESOURCE_PAGER_Y,
                arrowBackgrounds = listOf(page - 1, page + 1).map { targetPage ->
                    Bukkit.createBlockData(
                        if (targetPage in 0 until pageCount) Material.CYAN_CONCRETE
                        else DisabledGuiVisualPolicy.material,
                    )
                },
                arrowGlyphs = listOf("◀", "▶").mapIndexed { arrowIndex, glyph ->
                    val targetPage = if (arrowIndex == 0) page - 1 else page + 1
                    Component.text(glyph).color(
                        if (targetPage in 0 until pageCount) NamedTextColor.WHITE else NamedTextColor.GRAY,
                    )
                },
                status = Component.text("${page + 1} / $pageCount"),
            )
        }
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-resource-input",
                label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_HOVER)),
                background = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                cx = RESOURCE_BUTTON_LEFT_X,
                cy = RESOURCE_BUTTON_Y,
                w = RESOURCE_BUTTON_W,
                h = SETTING_INPUT_HEIGHT,
                labelSize = 0.0048,
                labelWidth = 120,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = singleLineHover(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_HOVER),
                    x = HOVER_SLOT_X,
                    y = HOVER_SLOT_Y,
                ),
                visualTag = "lower-resource-input",
            ),
        )
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-resource-back",
                label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                background = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                cx = RESOURCE_BUTTON_RIGHT_X,
                cy = RESOURCE_BUTTON_Y,
                w = RESOURCE_BUTTON_W,
                h = SETTING_INPUT_HEIGHT,
                labelSize = 0.0048,
                labelWidth = 120,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "lower-resource-back",
            ),
        )
        return finishResourcePicker(nodes)
    }

    /** 候補子画面の宣言区画を解決・変換し、既存の画面定義へ載せます。 */
    private fun finishResourcePicker(nodes: List<GestureGuiNode>): GestureGuiView {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeSettings.document(nodes),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][ResourcePicker] $diagnostics")
            },
        )
        return view(GestureLowerMode.RESOURCE_PICKER, compiled.elements, compiled.visuals)
    }

    /**
     * 設定候補子画面の宣言実装です。旧 `buildSettingChoices(child = true)` と同じ
     * 算出・文言・座標・要素 ID・ホバーを用い、既存の画面定義・子画面縮尺・
     * 操作振分け（`view()`）を利用します。
     */
    private fun buildSettingChoicesChildNew(
        state: GestureEditorState,
        player: Player,
        attention: GestureAttentionState = GestureAttentionState.EMPTY,
        suppressHighlight: Boolean = false,
    ): GestureGuiView {
        val context = state.settingContext
        val script = context?.let { plugin.scripts.load(it.scriptId) }
        val node = context?.let { script?.graph?.nodes?.get(it.nodeId) }
        val fieldKey = state.settingFieldKey
        val screen = state.settingScreen
        val frame = contentFrame(GestureGuiPanel())
        val nodes = mutableListOf<GestureGuiNode>()
        val noFields = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_NO_FIELDS))
        val backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK))
        val backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE)
        if (context == null || node == null || fieldKey == null || screen == null) {
            DeclarativeSettings.hintSection(nodes, frame, noFields, x = 0.0, y = 0.12, id = "setting-hint")
            DeclarativeSettings.backSection(nodes, frame, backLabel, backBackground, 0.0, CHILD_BACK_WIDTH)
            return finishSettingChoicesChild(nodes)
        }
        val attentionFields = attention.fieldKeysByNode[node.id].orEmpty()
        val field = CommandSettingsModel.gestureVisibleFields(node).firstOrNull { it.key == fieldKey }
        if (field == null) {
            DeclarativeSettings.hintSection(nodes, frame, noFields, x = 0.0, y = 0.12, id = "setting-hidden")
            DeclarativeSettings.backSection(nodes, frame, backLabel, backBackground, 0.0, CHILD_BACK_WIDTH)
            return finishSettingChoicesChild(nodes)
        }
        val fieldLabel = KcI18n.text(player, field.label)
        val fieldValue = field.value(node).render(player)
        // issue #29: 子画面ではエンティティ導線の再入場を隠し、往復ループを作りません。
        val choices = settingTreeNodes(node, context, screen, fieldKey, player, attentionFields, inChild = true).map { choice ->
            if (state.settingTreePath?.nodeIds?.lastOrNull() == choice.id) {
                choice.copy(selected = true)
            } else choice
        }
        val selectedDetail = choices.firstOrNull { it.selected && it.hasChildren }
        DeclarativeSettings.descriptionSection(
            nodes,
            frame,
            body = Component.text(fieldDescription(player, field), NamedTextColor.GRAY),
            detail = run {
                val warning = attentionWarning(player, node, fieldKey, attentionFields)
                val detailHint = selectedDetail
                    ?.let { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_OPEN_FILTERS) }
                when {
                    !warning.isNullOrBlank() -> DescriptionDetail(
                        Component.text(warning, NamedTextColor.RED),
                        CHILD_DETAIL_HINT_Y,
                    )
                    !detailHint.isNullOrBlank() -> DescriptionDetail(
                        Component.text(detailHint, NamedTextColor.GRAY),
                        CHILD_DETAIL_HINT_Y,
                    )
                    else -> null
                }
            },
            centerX = 0.0,
            hoverY = CHILD_HOVER_Y,
        )
        DeclarativeSettings.valueRowsSection(
            nodes,
            frame,
            rows = listOf(SettingsValueRow("setting-header", fieldLabel, fieldValue, CHILD_HEADER_Y)),
            labelX = -0.53,
            valueX = 0.53,
        )
        val pageSize = SETTING_CHOICE_PAGE_SIZE
        val pageCount = (choices.size + pageSize - 1) / pageSize
        val page = state.settingChoicePage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        appendSettingChoiceCards(
            nodes,
            frame,
            choices.drop(page * pageSize).take(pageSize),
            fieldKey,
            player,
            child = true,
            suppressHighlight = suppressHighlight,
        )
        if (pageCount > 1) {
            DeclarativeSettings.pagerSection(
                nodes,
                frame,
                idPrefix = "setting-choice",
                elementPrefix = "lower-setting-choice-page",
                page = page,
                pageCount = pageCount,
                centerX = 0.0,
                centerY = CHILD_PAGER_Y,
                arrowBackgrounds = listOf(page - 1, page + 1).map { targetPage ->
                    Bukkit.createBlockData(
                        if (targetPage in 0 until pageCount) Material.CYAN_CONCRETE
                        else DisabledGuiVisualPolicy.material,
                    )
                },
                arrowGlyphs = listOf("◀", "▶").mapIndexed { arrowIndex, glyph ->
                    val targetPage = if (arrowIndex == 0) page - 1 else page + 1
                    Component.text(glyph).color(
                        if (targetPage in 0 until pageCount) NamedTextColor.WHITE else NamedTextColor.GRAY,
                    )
                },
                status = Component.text("${page + 1}/$pageCount"),
            )
        }
        DeclarativeSettings.backSection(nodes, frame, backLabel, backBackground, 0.0, CHILD_BACK_WIDTH)
        return finishSettingChoicesChild(nodes)
    }

    /** 宣言の候補子画面を解決・変換し、既存の子画面定義へ載せます。 */
    private fun finishSettingChoicesChild(nodes: List<GestureGuiNode>): GestureGuiView {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeSettings.document(nodes),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][DeclarativeSettingChoices] $diagnostics")
            },
        )
        return view(GestureLowerMode.SETTING_CHOICES, compiled.elements, compiled.visuals, child = true)
    }

    /**
     * 親の設定候補画面の宣言実装です。旧 `buildSettingChoices(child = false)` と同じ
     * 算出・文言・座標・要素 ID・ホバーを用い、既存の画面定義・操作振分け（`view()`）を利用します。
     * 子画面とは異なり設定タブ列を保持し、入力カードは置きません。
     */
    private fun buildSettingChoicesParentNew(
        state: GestureEditorState,
        player: Player,
        attention: GestureAttentionState = GestureAttentionState.EMPTY,
        suppressHighlight: Boolean = false,
    ): GestureGuiView {
        val context = state.settingContext
        val script = context?.let { plugin.scripts.load(it.scriptId) }
        val node = context?.let { script?.graph?.nodes?.get(it.nodeId) }
        val fieldKey = state.settingFieldKey
        val screen = state.settingScreen
        val frame = contentFrame(GestureGuiPanel())
        val nodes = mutableListOf<GestureGuiNode>()
        val noFields = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_NO_FIELDS))
        val backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK))
        val backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE)
        if (context == null || node == null || fieldKey == null || screen == null) {
            DeclarativeSettings.hintSection(nodes, frame, noFields, x = 0.28, y = 0.20, id = "setting-hint")
            DeclarativeSettings.backSection(nodes, frame, backLabel, backBackground, 0.78, 0.42)
            return finishSettingChoicesParent(nodes)
        }
        val attentionFields = attention.fieldKeysByNode[node.id].orEmpty()
        val canDuplicate = script?.let {
            GraphEditor.canDuplicate(it.graph, node.id, plugin.graphLimits().maximumNodeCount)
        } == true
        val duplicateBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE)
        val deleteBackground = Bukkit.createBlockData(Material.RED_CONCRETE)
        val duplicateLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DUPLICATE))
        val deleteLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE))
        val fields = CommandSettingsModel.gestureVisibleFields(node)
        DeclarativeSettings.tabsSection(
            nodes,
            frame,
            tabs = fields.mapIndexed { index, field ->
                val on = index == (state.settingFieldKey?.let { key -> fields.indexOfFirst { it.key == key } }
                    ?.takeIf { it >= 0 } ?: state.settingsTab.coerceIn(0, fields.lastIndex))
                val fieldAttention = field.key in attentionFields
                val tabSelected = on && !suppressHighlight
                val tabTextColor = GestureSettingVisualPolicy.tabTextColor(fieldAttention)
                SettingsTabEntry(
                    label = Component.text(fieldTabLabel(player, node, field)).let {
                        if (tabTextColor == null) it else it.color(tabTextColor)
                    },
                    selected = tabSelected,
                    attentionHover = if (fieldAttention) attentionWarningHover(player, node, field.key) else null,
                    background = Bukkit.createBlockData(GestureSettingVisualPolicy.tabMaterial(tabSelected)),
                )
            },
            duplicateLabel = if (canDuplicate) duplicateLabel else null,
            duplicateBackground = if (canDuplicate) duplicateBackground else null,
            deleteLabel = deleteLabel,
            deleteBackground = deleteBackground,
        )
        val field = fields.firstOrNull { it.key == fieldKey }
        if (field == null) {
            // 依存値の変更直後に古い設定経路が一瞬残っても、非表示フィールドの
            // 選択肢を描画しません。親タブは共通可視性判定で既に再構成されています。
            DeclarativeSettings.hintSection(nodes, frame, noFields, x = 0.28, y = 0.20, id = "setting-hidden")
            DeclarativeSettings.backSection(nodes, frame, backLabel, backBackground, 0.78, 0.42)
            return finishSettingChoicesParent(nodes)
        }
        val fieldLabel = KcI18n.text(player, field.label)
        val fieldValue = field.value(node).render(player)
        val choices = settingTreeNodes(node, context, screen, fieldKey, player, attentionFields).map { choice ->
            if (state.settingTreePath?.nodeIds?.lastOrNull() == choice.id) {
                choice.copy(selected = true)
            } else choice
        }
        val selectedDetail = choices.firstOrNull { it.selected && it.hasChildren }
        val warning = attentionWarning(player, node, fieldKey, attentionFields)
        val detailHint = selectedDetail
            ?.let { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_OPEN_FILTERS) }
        DeclarativeSettings.descriptionSection(
            nodes,
            frame,
            body = Component.text(fieldDescription(player, field), NamedTextColor.GRAY),
            detail = when {
                !warning.isNullOrBlank() -> DescriptionDetail(
                    Component.text(warning, NamedTextColor.RED),
                    SETTING_DETAIL_ANCHOR_Y,
                )
                !detailHint.isNullOrBlank() -> DescriptionDetail(
                    Component.text(detailHint, NamedTextColor.GRAY),
                    SETTING_DETAIL_ANCHOR_Y,
                )
                else -> null
            },
            centerX = 0.28,
            hoverY = ACTION_DESCRIPTION_Y,
        )
        DeclarativeSettings.valueRowsSection(
            nodes,
            frame,
            rows = listOf(SettingsValueRow("setting-header", fieldLabel, fieldValue, SETTING_VALUE_ANCHOR_Y)),
        )
        val pageSize = SETTING_CHOICE_PAGE_SIZE
        val pageCount = (choices.size + pageSize - 1) / pageSize
        val page = state.settingChoicePage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        appendSettingChoiceCards(
            nodes,
            frame,
            choices.drop(page * pageSize).take(pageSize),
            fieldKey,
            player,
            child = false,
            suppressHighlight = suppressHighlight,
        )
        if (screen == GestureSettingScreen.POSITION && context.role == CommandSettingRole.DESTINATION) {
            choices.firstOrNull { it.id == "position:${PositionKind.TARGET.name}" && it.selected }?.let {
                appendDestinationChoiceCards(nodes, frame, it.children, fieldKey, player, suppressHighlight)
            }
        }
        if (pageCount > 1) {
            DeclarativeSettings.pagerSection(
                nodes,
                frame,
                idPrefix = "setting-choice",
                elementPrefix = "lower-setting-choice-page",
                page = page,
                pageCount = pageCount,
                centerX = 0.25,
                centerY = -0.43,
                arrowBackgrounds = listOf(page - 1, page + 1).map { targetPage ->
                    Bukkit.createBlockData(
                        if (targetPage in 0 until pageCount) Material.CYAN_CONCRETE
                        else DisabledGuiVisualPolicy.material,
                    )
                },
                arrowGlyphs = listOf("◀", "▶").mapIndexed { arrowIndex, glyph ->
                    val targetPage = if (arrowIndex == 0) page - 1 else page + 1
                    Component.text(glyph).color(
                        if (targetPage in 0 until pageCount) NamedTextColor.WHITE else NamedTextColor.GRAY,
                    )
                },
                status = Component.text("${page + 1}/$pageCount"),
            )
        }
        DeclarativeSettings.backSection(nodes, frame, backLabel, backBackground, 0.78, 0.42)
        return finishSettingChoicesParent(nodes)
    }

    /** 宣言の候補親画面を解決・変換し、既存の画面定義へ載せます。 */
    private fun finishSettingChoicesParent(nodes: List<GestureGuiNode>): GestureGuiView {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeSettings.document(nodes),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][DeclarativeSettingChoicesParent] $diagnostics")
            },
        )
        return view(GestureLowerMode.SETTING_CHOICES, compiled.elements, compiled.visuals, child = false)
    }

    /** ワールド変数一覧の子画面を生成します（宣言実装）。 */
    fun buildWorldVariablesChild(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView = buildWorldVariablesNew(state, player)

    /**
     * 変数一覧子画面の宣言実装です。旧 `buildWorldVariables` と同じ算出・文言・
     * 座標・要素 ID・ホバーを用い、既存の画面定義・子画面縮尺・操作振分けを利用します。
     */
    private fun buildWorldVariablesNew(state: GestureEditorState, player: Player): GestureGuiView {
        val worldId = resolveVariableWorldId(state)
        val entries = worldId?.let { plugin.variables.list(it).toSortedMap() }.orEmpty()
        val definitions = worldId?.let { plugin.variables.definitions(it) }.orEmpty()
        val descriptionBody = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_DESCRIPTION_SINGLE)
            .ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_SETTINGS_FIELD_FALLBACK) }
        if (entries.isEmpty()) {
            return finishVariablesChild(
                DeclarativeVariables.variablesDocument(
                    WorldVariablesData(
                        title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_TITLE)),
                        description = Component.text(descriptionBody, NamedTextColor.GRAY),
                        emptyLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_EMPTY)),
                        entries = emptyList(),
                        entryBackground = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                        deleteBackgroundInUse = Bukkit.createBlockData(DisabledGuiVisualPolicy.material),
                        deleteBackgroundFree = Bukkit.createBlockData(Material.RED_CONCRETE),
                        page = 0,
                        pageCount = 1,
                        pagerAvailableBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                        pagerDisabledBackground = Bukkit.createBlockData(DisabledGuiVisualPolicy.material),
                        createLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_CREATE)),
                        createBackground = Bukkit.createBlockData(Material.GREEN_CONCRETE),
                        backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                        backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                    ),
                ),
                GestureLowerMode.WORLD_VARIABLES,
                "[DeclarativeVariables]",
            )
        }
        val pageSize = WORLD_VARIABLE_PAGE_SIZE
        val pageCount = (entries.size + pageSize - 1) / pageSize
        val page = state.variablePage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val usageScan = state.placement?.world
            ?.let { worldName -> plugin.findWorldVariableUsagesSafely(worldName, entries.keys) }
            ?: WorldVariableUsageScanResult(emptyMap(), complete = true)
        val usagesByName = usageScan.usages
        return finishVariablesChild(
            DeclarativeVariables.variablesDocument(
                WorldVariablesData(
                    title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_TITLE)),
                    description = Component.text(descriptionBody, NamedTextColor.GRAY),
                    emptyLabel = null,
                    entries = entries.keys.toList().drop(page * pageSize).take(pageSize).map { name ->
                        val value = entries.getValue(name)
                        val type = definitions[name]?.type ?: value.type
                        val inUse = !usageScan.complete || usagesByName[name].orEmpty().isNotEmpty()
                        WorldVariableEntry(
                            name = name,
                            label = Component.text(name),
                            hover = multiLineHover(
                                listOf(
                                    KcI18n.text(
                                        player,
                                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_VALUE,
                                        mapOf("value" to VariableTemplate.stringify(value)),
                                    ),
                                    KcI18n.text(
                                        player,
                                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_TYPE,
                                        mapOf("type" to variableTypeName(player, type)),
                                    ),
                                ),
                                x = 0.0,
                                y = CHILD_HOVER_Y,
                                replacesVisualId = SETTING_DESCRIPTION_HOVER_ID,
                            ),
                            inUse = inUse,
                            deleteHover = singleLineHover(
                                KcI18n.text(
                                    player,
                                    if (inUse) {
                                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_DELETE_IN_USE
                                    } else {
                                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_DELETE
                                    },
                                ),
                                x = 0.0,
                                y = CHILD_HOVER_Y,
                                replacesVisualId = SETTING_DESCRIPTION_HOVER_ID,
                            ),
                        )
                    },
                    entryBackground = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                    deleteBackgroundInUse = Bukkit.createBlockData(DisabledGuiVisualPolicy.material),
                    deleteBackgroundFree = Bukkit.createBlockData(Material.RED_CONCRETE),
                    page = page,
                    pageCount = pageCount,
                    pagerAvailableBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                    pagerDisabledBackground = Bukkit.createBlockData(DisabledGuiVisualPolicy.material),
                    createLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_CREATE)),
                    createBackground = Bukkit.createBlockData(Material.GREEN_CONCRETE),
                    backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                    backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                ),
            ),
            GestureLowerMode.WORLD_VARIABLES,
            "[DeclarativeVariables]",
        )
    }

    /** 変数系子画面の宣言区画を解決・変換し、既存の子画面定義へ載せます。 */
    private fun finishVariablesChild(document: GestureGuiDocument, mode: GestureLowerMode, tag: String): GestureGuiView {
        val compiled = KantanLayoutBridge.compile(
            document,
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander]$tag $diagnostics")
            },
        )
        return view(mode, compiled.elements, compiled.visuals, child = true)
    }

    /** ワールド変数の型選択子画面を生成します（宣言実装）。 */
    fun buildWorldVariableTypeChild(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView = buildWorldVariableTypeNew(state, player)

    /**
     * 型選択子画面の宣言実装です。旧 `buildWorldVariableType` と同じ算出・文言・
     * 座標・要素 ID を用い、既存の画面定義・子画面縮尺・操作振分けを利用します。
     */
    private fun buildWorldVariableTypeNew(state: GestureEditorState, player: Player): GestureGuiView {
        val selectedType = state.pendingWorldVariableType
        return finishVariablesChild(
            DeclarativeVariables.typeDocument(
                WorldVariableTypeData(
                    title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_WORLD_VARIABLE_CREATE_TITLE)),
                    description = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_TYPE),
                    ),
                    options = listOf(VariableType.NUMBER, VariableType.STRING).map { type ->
                        val selected = selectedType == type
                        val label = variableTypeName(player, type)
                        WorldVariableTypeOption(
                            key = type.name,
                            label = Component.text(
                                label,
                                GestureSettingVisualPolicy.settingChoiceTextColor(
                                    GestureSettingTreeNode(id = "world-variable-type:${type.name}", label = label, selected = selected),
                                ),
                            ),
                            selected = selected,
                            background = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                            outline = GestureSettingVisualPolicy.nonTabOutlineMaterial(selected)?.let {
                                Bukkit.createBlockData(it)
                            },
                        )
                    },
                    nextLabel = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_NEXT),
                        if (selectedType != null) NamedTextColor.WHITE else NamedTextColor.GRAY,
                    ),
                    nextBackground = Bukkit.createBlockData(
                        if (selectedType != null) Material.GREEN_CONCRETE else DisabledGuiVisualPolicy.material,
                    ),
                    nextEnabled = selectedType != null,
                    backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                    backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                ),
            ),
            GestureLowerMode.WORLD_VARIABLE_TYPE,
            "[DeclarativeVariableType]",
        )
    }

    /** ワールド内変数の削除確認子画面を生成します（宣言実装）。 */
    fun buildWorldVariableDeleteConfirmationChild(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView = buildWorldVariableDeleteConfirmationNew(state, player)

    /**
     * 削除確認子画面の宣言実装です。旧実装と同じ文言・座標・要素 ID を用い、
     * 取消には戻る操作と同一 ID を用います（旧実装どおり）。
     */
    private fun buildWorldVariableDeleteConfirmationNew(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView {
        val name = state.pendingWorldVariableDeleteName.orEmpty()
        return finishVariablesChild(
            DeclarativeVariables.deleteConfirmDocument(
                WorldVariableDeleteData(
                    title = Component.text(
                        KcI18n.text(
                            player,
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_DELETE_TITLE,
                        ),
                    ),
                    body = Component.text(
                        KcI18n.text(
                            player,
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_DELETE_BODY,
                            mapOf("name" to name),
                        ),
                        NamedTextColor.GRAY,
                    ),
                    confirmLabel = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                        NamedTextColor.WHITE,
                    ),
                    confirmBackground = Bukkit.createBlockData(Material.RED_CONCRETE),
                    cancelLabel = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL),
                        NamedTextColor.WHITE,
                    ),
                    cancelBackground = Bukkit.createBlockData(Material.CYAN_TERRACOTTA),
                ),
            ),
            GestureLowerMode.WORLD_VARIABLE_DELETE_CONFIRM,
            "[DeclarativeVariableDelete]",
        )
    }

    /** テスト開始確認は下部中央画面へ重ねる専用子画面です（宣言実装）。 */
    fun buildTestConfirmationChild(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView = buildTestConfirmationNew(state, player, child = true)

    /**
     * テスト開始確認の宣言実装です。旧実装と同じ文言・座標・要素 ID を用い、
     * 既存の画面定義・パネル・操作振分けを利用します。
     */
    private fun buildTestConfirmationNew(
        state: GestureEditorState,
        player: Player,
        child: Boolean,
    ): GestureGuiView {
        val test = state.testExecution ?: return buildSettings(state, player, GestureAttentionState.EMPTY)
        fun toggleLabel(labelKey: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>, enabled: Boolean): Component {
            val value = KcI18n.text(
                player,
                if (enabled) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_ON
                else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_OFF,
            )
            return Component.text(KcI18n.text(player, labelKey), NamedTextColor.WHITE)
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text(value, if (enabled) NamedTextColor.GREEN else NamedTextColor.GRAY))
        }
        val compiled = KantanLayoutBridge.compile(
            DeclarativeTest.confirmDocument(
                TestConfirmData(
                    title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_CONFIRM_TITLE)),
                    description = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_DEBUG_MODE_DESCRIPTION),
                        NamedTextColor.GRAY,
                    ),
                    toggles = listOf(
                        TestToggleData(
                            id = "test-confirm-debug",
                            label = toggleLabel(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_DEBUG_MODE, test.debugMode),
                            background = Bukkit.createBlockData(
                                if (test.debugMode) Material.LIME_CONCRETE else Material.LIGHT_GRAY_CONCRETE,
                            ),
                            centerY = 0.13,
                        ),
                        TestToggleData(
                            id = "test-confirm-log",
                            label = toggleLabel(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_LOG_OUTPUT, test.logOutput),
                            background = Bukkit.createBlockData(
                                if (test.logOutput) Material.LIME_CONCRETE else Material.LIGHT_GRAY_CONCRETE,
                            ),
                            centerY = -0.02,
                        ),
                    ),
                    startLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_CONFIRM)),
                    startBackground = Bukkit.createBlockData(Material.CYAN_TERRACOTTA),
                    cancelLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_CANCEL)),
                    cancelBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                ),
            ),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][DeclarativeTestConfirm] $diagnostics")
            },
        )
        return view(GestureLowerMode.TEST_CONFIRM, compiled.elements, compiled.visuals, child = child)
    }

    /**
     * テスト状態・結果の宣言実装です。旧実装と同じ行構成・要素 ID を用います。
     * 実行中は毎tick再生成されるため、解決・変換の軽さを前提にします。
     */
    private fun buildTestStatusNew(state: GestureEditorState, player: Player): GestureGuiView {
        val test = state.testExecution ?: return buildSettings(state, player, GestureAttentionState.EMPTY)
        fun row(id: String, label: String, value: String, y: Double): CenteredLine =
            CenteredLine(id, Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(Component.text(value, NamedTextColor.WHITE)), y)
        val compiled = KantanLayoutBridge.compile(
            DeclarativeTest.statusDocument(
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_STATUS_TITLE)),
                rows = listOf(
                    row(
                        "test-status-elapsed",
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_ELAPSED_TIME),
                        formatTestElapsedSeconds(test.elapsedTicks),
                        0.21,
                    ),
                    row(
                        "test-status-count",
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_COMMAND_COUNT),
                        test.successfulNodeCount.toString(),
                        0.00,
                    ),
                    row(
                        "test-status-log",
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_LOG_OUTPUT),
                        if (test.logOutput) KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_ON)
                        else KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_OFF),
                        -0.21,
                    ),
                ),
                hint = Component.text(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_RUNNING_HINT),
                    NamedTextColor.GRAY,
                ),
            ),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][DeclarativeTestStatus] $diagnostics")
            },
        )
        return view(GestureLowerMode.TEST_STATUS, compiled.elements, compiled.visuals)
    }

    private fun buildTestResultNew(state: GestureEditorState, player: Player): GestureGuiView {
        val test = state.testExecution ?: return buildSettings(state, player, GestureAttentionState.EMPTY)
        val result = test.result
        val elapsed = result?.elapsedTicks ?: test.elapsedTicks
        val waitTicks = result?.waitTicks ?: 0L
        val showReal = test.debugMode
        val durationY = if (showReal) 0.24 else 0.22
        val waitY = if (showReal) 0.11 else 0.09
        val countY = if (showReal) -0.15 else -0.04
        val statusY = if (showReal) -0.28 else -0.17
        fun row(id: String, label: String, value: String, color: NamedTextColor, y: Double): CenteredLine =
            CenteredLine(id, Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(Component.text(value, color)), y)
        val rows = mutableListOf(
            row(
                "test-result-duration",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_DURATION),
                formatTestElapsed(elapsed),
                NamedTextColor.WHITE,
                durationY,
            ),
            row(
                "test-result-wait",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_WAIT_DURATION),
                formatTestElapsed(waitTicks),
                NamedTextColor.WHITE,
                waitY,
            ),
        )
        if (showReal) {
            rows += row(
                "test-result-real",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REAL_DURATION),
                formatTestElapsed(
                    TestExecutionTimeFormatter.realProcessingTicks(elapsed, result?.debugWaitTicks ?: 0L),
                ),
                NamedTextColor.WHITE,
                -0.02,
            )
        }
        rows += row(
            "test-result-count",
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_COMMAND_COUNT),
            (result?.successfulNodeCount ?: test.successfulNodeCount).toString(),
            NamedTextColor.WHITE,
            countY,
        )
        val status = when (result?.status) {
            me.awabi2048.kantancommander.execution.ExecutionFinishStatus.SUCCESS ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_SUCCESS)
            me.awabi2048.kantancommander.execution.ExecutionFinishStatus.CANCELLED ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_INTERRUPTED)
            else -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_FAILURE)
        }
        val statusColor = when (result?.status) {
            me.awabi2048.kantancommander.execution.ExecutionFinishStatus.SUCCESS -> NamedTextColor.GREEN
            me.awabi2048.kantancommander.execution.ExecutionFinishStatus.FAILURE -> NamedTextColor.RED
            else -> NamedTextColor.GOLD
        }
        rows += row(
            "test-result-status",
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_FINAL_RESULT),
            status,
            statusColor,
            statusY,
        )
        if (result?.status == me.awabi2048.kantancommander.execution.ExecutionFinishStatus.FAILURE) {
            rows += row(
                "test-result-cause",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_CAUSE),
                // issue #6 の共通分類として code で解決し、原文表示へ戻しません。
                // errorCode 優先で表示キーを引き、旧 reason は読み替えます。
                result.errorCode?.let { KcI18n.text(player, it.displayKey()) }
                    ?: result.reason?.let { me.awabi2048.kantancommander.error.ErrorCode.fromLegacyReason(it) }
                        ?.let { KcI18n.text(player, it.displayKey()) }
                    ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET),
                NamedTextColor.WHITE,
                -0.40,
            )
        }
        val compiled = KantanLayoutBridge.compile(
            DeclarativeTest.resultDocument(
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_RESULT_TITLE)),
                rows = rows,
                completeLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_COMPLETE)),
                completeBackground = Bukkit.createBlockData(Material.CYAN_TERRACOTTA),
            ),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][DeclarativeTestResult] $diagnostics")
            },
        )
        return view(GestureLowerMode.TEST_RESULT, compiled.elements, compiled.visuals)
    }

    /** SETTINGS: 左タブ列＋固定操作、右詳細＝値表示と編集導線です（宣言実装）。 */
    private fun buildSettings(
        state: GestureEditorState,
        player: Player,
        attention: GestureAttentionState,
        suppressHighlight: Boolean = false,
        fieldPreviews: Map<FieldPreviewKey, String?> = emptyMap(),
    ): GestureGuiView = buildSettingsNew(state, player, attention, suppressHighlight, fieldPreviews)

    /**
     * SETTINGS の宣言実装です。旧実装と同じ算出・文言・座標・要素 ID・ガード・ホバーを用い、
     * 既存の画面定義・パネル・操作振分け（`view()`）を利用します。ノード未選択時は
     * プログラム全体設定の宣言実装へ委譲します。
     */
    private fun buildSettingsNew(
        state: GestureEditorState,
        player: Player,
        attention: GestureAttentionState,
        suppressHighlight: Boolean = false,
        fieldPreviews: Map<FieldPreviewKey, String?> = emptyMap(),
    ): GestureGuiView {
        val script = plugin.scripts.load(state.scriptId)
        val node = state.selectedNodeId?.let { id -> script?.graph?.nodes?.get(id) }
        if (node == null) return buildScriptSettingsNew(state, player, script, attention)
        val frame = contentFrame(GestureGuiPanel())
        val nodes = mutableListOf<GestureGuiNode>()
        val canDuplicate = script?.let {
            GraphEditor.canDuplicate(it.graph, node.id, plugin.graphLimits().maximumNodeCount)
        } == true
        val duplicateBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE)
        val deleteBackground = Bukkit.createBlockData(Material.RED_CONCRETE)
        val duplicateLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DUPLICATE))
        val deleteLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE))
        val fields = CommandSettingsModel.gestureVisibleFields(node)
        if (fields.isEmpty()) {
            DeclarativeSettings.hintSection(
                nodes,
                frame,
                Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_NO_FIELDS)),
            )
            DeclarativeSettings.tabsSection(
                nodes,
                frame,
                tabs = emptyList(),
                duplicateLabel = if (canDuplicate) duplicateLabel else null,
                duplicateBackground = if (canDuplicate) duplicateBackground else null,
                deleteLabel = deleteLabel,
                deleteBackground = deleteBackground,
            )
            return finishSettings(nodes)
        }
        val attentionFields = attention.fieldKeysByNode[node.id].orEmpty()
        val activeField = state.settingFieldKey?.let { key -> fields.indexOfFirst { it.key == key } }
            ?.takeIf { it >= 0 } ?: state.settingsTab.coerceIn(0, fields.lastIndex)
        check(fields.size <= SETTINGS_TAB_MAX) {
            "設定タブは${SETTINGS_TAB_MAX}個以内で定義してください: ${node.type}=${fields.size}"
        }
        DeclarativeSettings.tabsSection(
            nodes,
            frame,
            tabs = fields.mapIndexed { index, field ->
                val on = index == activeField
                val fieldAttention = field.key in attentionFields
                val tabSelected = on && !suppressHighlight
                val tabTextColor = GestureSettingVisualPolicy.tabTextColor(fieldAttention)
                SettingsTabEntry(
                    label = Component.text(fieldTabLabel(player, node, field)).let {
                        if (tabTextColor == null) it else it.color(tabTextColor)
                    },
                    selected = tabSelected,
                    attentionHover = if (fieldAttention) attentionWarningHover(player, node, field.key) else null,
                    background = Bukkit.createBlockData(GestureSettingVisualPolicy.tabMaterial(tabSelected)),
                )
            },
            duplicateLabel = if (canDuplicate) duplicateLabel else null,
            duplicateBackground = if (canDuplicate) duplicateBackground else null,
            deleteLabel = deleteLabel,
            deleteBackground = deleteBackground,
        )
        val field = fields[state.settingsTab.coerceIn(0, fields.lastIndex)]
        val descriptor = CommandSettingsModel.descriptor(node, field.key)
        val settingContext = state.settingContext
            ?: CommandSettingContext(state.scriptId, node.id, descriptor.role)
        val displayLabel = KcI18n.text(player, field.label)
        val displayValue = field.value(node)
        val compositeRows = when (displayValue) {
            is DisplayValue.Timing -> displayValue.rows(player)
            is DisplayValue.ParticleParameters -> displayValue.rows(player)
            is DisplayValue.SoundTargetPosition -> displayValue.rows(player)
            is DisplayValue.SoundSetting -> displayValue.rows(player)
            is DisplayValue.ParticleDisplay -> displayValue.rows(player)
            else -> null
        }
        val compositeLayout = compositeRows?.let {
            GestureSettingValueLayout.calculate(
                rowCount = it.size,
                valueAnchorY = SETTING_VALUE_ANCHOR_Y - SETTING_COMPOSITE_SHIFT,
                detailAnchorY = SETTING_DETAIL_ANCHOR_Y - SETTING_COMPOSITE_SHIFT,
                rowGapRatio = TIMING_ROW_GAP_RATIO,
                minimumDetailY = SETTING_INPUT_CENTER_Y - SETTING_COMPOSITE_SHIFT +
                    SETTING_INPUT_HEIGHT / 2.0 + SETTING_DETAIL_INPUT_GAP,
            )
        }
        val inputCenterY = if (compositeRows != null) {
            SETTING_INPUT_CENTER_Y - SETTING_COMPOSITE_SHIFT
        } else {
            SETTING_INPUT_CENTER_Y
        }
        val value = displayValue.render(player)
        val settingScreen = gestureSettingScreenFor(descriptor.editor)
        // issue #18: Checkbox対象は親画面のトグルで直接操作するため、
        // 旧来の二択カードを重ねて表示しません。
        val checkboxPresentation = SettingSpec.presentationFor(node, field.key, descriptor) ==
            GesturePresentation.CHECKBOX
        val settingChoices = if (checkboxPresentation) emptyList() else settingScreen?.let { screen ->
            settingTreeNodes(node, settingContext, screen, field.key, player, attentionFields).map { choice ->
                if (state.settingTreePath?.nodeIds?.lastOrNull() == choice.id) {
                    choice.copy(selected = true)
                } else choice
            }
        }.orEmpty()
        val selectedDetail = settingChoices.firstOrNull { it.selected && it.hasChildren }
        val warning = attentionWarning(player, node, field.key, attentionFields)
        val detailHint = selectedDetail
            ?.let { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_OPEN_FILTERS) }
        DeclarativeSettings.descriptionSection(
            nodes,
            frame,
            body = Component.text(fieldDescription(player, field), NamedTextColor.GRAY),
            detail = when {
                !warning.isNullOrBlank() -> DescriptionDetail(
                    Component.text(warning, NamedTextColor.RED),
                    compositeLayout?.detailCenterY ?: SETTING_DETAIL_ANCHOR_Y,
                )
                !detailHint.isNullOrBlank() -> DescriptionDetail(
                    Component.text(detailHint, NamedTextColor.GRAY),
                    compositeLayout?.detailCenterY ?: SETTING_DETAIL_ANCHOR_Y,
                )
                else -> null
            },
        )
        if (compositeRows != null && compositeLayout != null) {
            check(compositeRows.size == compositeLayout.rowCentersY.size) {
                "複合表示の値行とレイアウトの行数が一致しません: ${compositeRows.size} != ${compositeLayout.rowCentersY.size}"
            }
            DeclarativeSettings.valueRowsSection(
                nodes,
                frame,
                rows = compositeRows.mapIndexed { index, row ->
                    SettingsValueRow("lower-setting-composite-$index", row.label, row.value, compositeLayout.rowCentersY[index])
                },
            )
        } else {
            DeclarativeSettings.valueRowsSection(
                nodes,
                frame,
                rows = listOf(SettingsValueRow("lower-setting", displayLabel, value, SETTING_VALUE_ANCHOR_Y)),
            )
        }
        if (settingScreen != null) {
            appendSettingChoiceCards(
                nodes,
                frame,
                settingChoices,
                fieldKey = field.key,
                player = player,
                child = false,
                suppressHighlight = suppressHighlight,
            )
        }
        val destinationTarget = if (
            settingScreen == GestureSettingScreen.POSITION &&
            settingContext.role == CommandSettingRole.DESTINATION
        ) {
            settingChoices.firstOrNull {
                it.id == "position:${PositionKind.TARGET.name}" && it.selected
            }
        } else null
        destinationTarget?.let {
            appendDestinationChoiceCards(nodes, frame, it.children, field.key, player, suppressHighlight)
        }
        appendSettingsInputCards(nodes, frame, state, player, node, field, descriptor, inputCenterY, suppressHighlight, fieldPreviews)
        return finishSettings(nodes)
    }

    /** 宣言区画を解決・変換し、既存の画面定義へ載せます。 */
    private fun finishSettings(nodes: List<GestureGuiNode>): GestureGuiView {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeSettings.document(nodes),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][DeclarativeSettings] $diagnostics")
            },
        )
        return view(GestureLowerMode.SETTINGS, compiled.elements, compiled.visuals)
    }

    private fun appendSettingChoiceCards(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        choices: List<GestureSettingTreeNode>,
        fieldKey: String,
        player: Player,
        child: Boolean,
        suppressHighlight: Boolean,
    ) {
        val rowCount = (choices.size + 1) / 2
        choices.forEachIndexed { index, choice ->
            val column = index % 2
            val row = index / 2
            val cx = if (child) {
                if (column == 0) -0.53 else 0.53
            } else if (column == 0) -0.10 else 0.67
            val cy = if (child) {
                CHILD_CHOICE_TOP_Y - row * CHILD_CHOICE_PITCH
            } else {
                TARGET_PARENT_CHOICE_TOP_Y - row * TARGET_PARENT_CHOICE_PITCH
            }
            val width = if (child) CHILD_CHOICE_WIDTH else SETTING_CHOICE_WIDTH
            DeclarativeSettings.choiceCardsSection(
                nodes,
                frame,
                cards = listOf(
                    SettingsChoiceCard(
                        elementId = "lower-setting-choice:${choice.id}",
                        label = Component.text(choice.label, GestureSettingVisualPolicy.settingChoiceTextColor(choice)),
                        background = Bukkit.createBlockData(settingChoiceMaterial(choice)),
                        outline = if (suppressHighlight) {
                            null
                        } else {
                            GestureSettingVisualPolicy.nonTabOutlineMaterial(choice.selected)?.let {
                                Bukkit.createBlockData(it)
                            }
                        },
                        enabled = choice.enabled,
                        cx = cx,
                        cy = cy,
                        w = width,
                        h = SETTING_CHOICE_HEIGHT,
                        labelDy = -0.012,
                        labelSize = 0.0045,
                        labelWidth = 115,
                        hover = DisabledChoiceVisualPolicy.hoverText(
                            enabled = choice.enabled,
                            normal = choice.description.takeIf(String::isNotBlank)
                                ?: choiceDescription(player, choice, fieldKey),
                            disabled = choice.disabledHoverText,
                        )?.let {
                            parallelButtonHover(
                                text = it,
                                x = cx,
                                y = cy,
                                row = row,
                                rowCount = rowCount,
                                height = SETTING_CHOICE_HEIGHT,
                                descriptionX = if (child) 0.0 else HOVER_SLOT_X,
                                descriptionY = if (child) CHILD_HOVER_Y else HOVER_SLOT_Y,
                                replacesDescription = child,
                                parallel = choices.size > 1,
                            )
                        },
                        replacesDescription = child,
                        visualTag = "setting-choice",
                        visualIndex = index,
                    ),
                ),
            )
        }
    }

    private fun appendDestinationChoiceCards(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        choices: List<GestureSettingTreeNode>,
        fieldKey: String,
        player: Player,
        suppressHighlight: Boolean,
    ) {
        val visibleChoices = choices.take(GestureTargetChoiceLayoutPolicy.CHOICE_COUNT)
        GestureTargetChoiceLayoutPolicy.slots(visibleChoices.size)
            .zip(visibleChoices)
            .forEachIndexed { index, (slot, choice) ->
                val cx = slot.centerX
                val cy = POSITION_TARGET_CHOICE_Y
                DeclarativeSettings.choiceCardsSection(
                    nodes,
                    frame,
                    cards = listOf(
                        SettingsChoiceCard(
                            elementId = "lower-setting-choice:${choice.id}",
                            label = Component.text(choice.label, GestureSettingVisualPolicy.settingChoiceTextColor(choice)),
                            background = Bukkit.createBlockData(settingChoiceMaterial(choice)),
                            outline = if (suppressHighlight) {
                                null
                            } else {
                                GestureSettingVisualPolicy.nonTabOutlineMaterial(choice.selected)?.let {
                                    Bukkit.createBlockData(it)
                                }
                            },
                            enabled = choice.enabled,
                            cx = cx,
                            cy = cy,
                            w = slot.width,
                            h = POSITION_TARGET_CHOICE_HEIGHT,
                            labelDy = -0.02,
                            labelSize = 0.0055,
                            labelWidth = 90,
                            hover = DisabledChoiceVisualPolicy.hoverText(
                                enabled = choice.enabled,
                                normal = choice.description.takeIf(String::isNotBlank)
                                    ?: choiceDescription(player, choice, fieldKey),
                                disabled = choice.disabledHoverText,
                            )?.let {
                                parallelButtonHover(
                                    text = it,
                                    x = cx,
                                    y = cy,
                                    row = 0,
                                    rowCount = 1,
                                    height = POSITION_TARGET_CHOICE_HEIGHT,
                                    descriptionX = HOVER_SLOT_X,
                                    descriptionY = HOVER_SLOT_Y,
                                    replacesDescription = false,
                                    parallel = visibleChoices.size > 1,
                                )
                            },
                            replacesDescription = false,
                            visualTag = "position-target-choice",
                            visualIndex = index,
                        ),
                    ),
                )
            }
    }

    private fun appendSettingsInputCards(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        state: GestureEditorState,
        player: Player,
        node: CommandNode,
        field: EditorField,
        descriptor: CommandSettingDescriptor,
        inputCenterY: Double,
        suppressHighlight: Boolean = false,
        fieldPreviews: Map<FieldPreviewKey, String?> = emptyMap(),
    ) {
        val typedValueSourceSetting =
            CommandSettingsModel.supportsTemporaryValueReference(node, field.key)
        val heldItemSetting = field.key == "item" && (node.type == CommandType.GIVE_ITEM ||
            (node.type == CommandType.ENTITY_ACTION && node.string("action", "ride") == "equip") ||
            (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.PLAYER_STATE.name))
        val heldBlockSetting = field.key == "block" && (
            node.type == CommandType.BLOCK_OPERATION ||
                (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.BLOCK_STATE.name) ||
                (node.type == CommandType.TEMP_SET &&
                    TemporaryVariableType.parse(node.string("tempType")) == TemporaryVariableType.BLOCK)
            )
        val heldDiskSetting = field.key == "diskId" && node.type == CommandType.DISK_CALL
        val heldFireworkSetting = field.key == "fireworkContents" && node.type == CommandType.FIREWORK_LAUNCH
        val heldMainHandSetting = heldItemSetting || heldBlockSetting || heldDiskSetting || heldFireworkSetting
        val heldMainHandAvailable = when {
            heldItemSetting -> GestureGuiClickPolicy.hasMainHandItem(player)
            heldBlockSetting -> HeldBlockSettingPolicy.canSet(player.inventory.itemInMainHand.type)
            heldDiskSetting -> KantanItemService.diskId(player.inventory.itemInMainHand) != null
            heldFireworkSetting -> player.inventory.itemInMainHand.type == Material.FIREWORK_ROCKET
            else -> true
        }
        // issue #18: 値仕様と表示操作を分離します。Slider／Checkbox対象は
        // Dialog入力カードを出さず、親画面で直接操作できる操作面を置きます。
        // 検証・既定値の正本は CommandDialogSpecs 側に残します。
        val presentation = SettingSpec.presentationFor(node, field.key, descriptor)
        val sliderSpec = SettingSpec.sliderSpecFor(node, field.key)
            ?.takeIf { presentation == GesturePresentation.SLIDER }
        val isCheckboxPresentation = presentation == GesturePresentation.CHECKBOX &&
            (SettingSpec.isCheckbox(node.type, field.key) ||
                descriptor.editor == CommandSettingEditor.ENTITY_OVERWRITE ||
                descriptor.editor == CommandSettingEditor.CONDITION_INVERSION)
        if (sliderSpec != null) {
            appendSliderCards(
                nodes,
                frame,
                node,
                field.key,
                sliderSpec,
                inputCenterY,
                suppressHighlight,
                fieldPreviews[FieldPreviewKey(node.id, field.key)],
                player,
            )
        }
        if (isCheckboxPresentation) {
            appendCheckboxCard(nodes, frame, node, field.key, inputCenterY, suppressHighlight)
        }
        val dialogInputSetting = !heldMainHandSetting &&
            descriptor.editor == CommandSettingEditor.TEXT && field.key in TEXT_EDITABLE_KEYS &&
            sliderSpec == null && !isCheckboxPresentation
        if (typedValueSourceSetting) {
            val selectedSource = CommandSettingsModel.temporaryValueSource(node, field.key)
                ?: CommandValueSource.LITERAL
            val sources = listOf(CommandValueSource.LITERAL, CommandValueSource.TEMPORARY)
            sources.forEachIndexed { index, source ->
                val selected = source == selectedSource
                // 方式指定は手持ちの有無にかかわらず行えます。手持ち要件は
                // 二段階目の適用時判定に残し、クリック時点のガードで再評価します。
                val lines = typedValueSourceLines(player, field.key, source)
                val x = if (index == 0) TYPED_VALUE_SOURCE_LEFT_X else TYPED_VALUE_SOURCE_RIGHT_X
                DeclarativeSettings.actionCardSection(
                    nodes,
                    frame,
                    SettingsActionCard(
                        elementId = "lower-value-source:${field.key}:${source.name}",
                        label = typedValueSourceComponent(lines).color(NamedTextColor.GOLD),
                        background = Bukkit.createBlockData(
                            if (selected) Material.CYAN_TERRACOTTA else Material.LIGHT_GRAY_CONCRETE,
                        ),
                        outline = if (suppressHighlight) {
                            null
                        } else {
                            GestureSettingVisualPolicy.nonTabOutlineMaterial(selected)?.let {
                                Bukkit.createBlockData(it)
                            }
                        },
                        cx = x,
                        cy = SETTING_INPUT_CENTER_Y,
                        w = TYPED_VALUE_SOURCE_WIDTH,
                        h = SETTING_INPUT_HEIGHT,
                        labelSize = 0.0043,
                        labelWidth = 92,
                        gestures = typedValueSourceGestures(field.key, source),
                        // 方式指定自体は常時許可します。手持ち要件は二段階目の
                        // 適用時（heldValueAvailable）に判定するため、ここでは拒否しません。
                        guard = null,
                    hover = multiLineHover(lines, x = x, y = HOVER_SLOT_Y),
                    visualTag = "lower-value-source-${source.name.lowercase()}",
                ),
            )
        }
            appendTypedValuePreview(nodes, frame, node, field.key, selectedSource, state.scriptId, player)
        } else if (heldMainHandSetting || dialogInputSetting) {
            val editVisualText = if (heldMainHandSetting) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_EDIT_FROM_MAINHAND
            } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_HOVER
            val editTextColor = if (heldMainHandAvailable) NamedTextColor.GOLD else NamedTextColor.GRAY
            // issue #28: ID候補を持つ項目は、一覧選択とDialog入力の2経路を並べます。
            // 一覧選択の文言はDialogの候補表示と共有し、新規キーを増やしません。
            val pickerKind = ResourcePicker.kindForFieldKey(field.key)
            if (!heldMainHandSetting && pickerKind != null) {
                DeclarativeSettings.actionCardSection(
                    nodes,
                    frame,
                    SettingsActionCard(
                        elementId = "lower-resource-open:${field.key}",
                        label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SHOW_DETAILS), NamedTextColor.GOLD),
                        background = Bukkit.createBlockData(Material.CYAN_TERRACOTTA),
                        cx = -0.02,
                        cy = inputCenterY,
                        w = 0.56,
                        h = SETTING_INPUT_HEIGHT,
                        labelSize = 0.0048,
                        labelWidth = 92,
                        gestures = GestureGuiClickPolicy.CLICK,
                        guard = null,
                        hover = singleLineHover(
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SHOW_DETAILS),
                            x = HOVER_SLOT_X,
                            y = HOVER_SLOT_Y,
                        ),
                        visualTag = "lower-resource-open",
                    ),
                )
            }
            DeclarativeSettings.actionCardSection(
                nodes,
                frame,
                SettingsActionCard(
                    elementId = "lower-edit:${field.key}",
                    label = Component.text(KcI18n.text(player, editVisualText), editTextColor),
                    background = Bukkit.createBlockData(
                        if (heldMainHandAvailable) Material.CYAN_TERRACOTTA else DisabledGuiVisualPolicy.material,
                    ),
                    cx = if (!heldMainHandSetting && pickerKind != null) 0.58 else 0.28,
                    cy = inputCenterY,
                    w = if (!heldMainHandSetting && pickerKind != null) 0.56 else 1.2,
                    h = SETTING_INPUT_HEIGHT,
                    labelSize = 0.0055,
                    labelWidth = 180,
                    gestures = if (heldMainHandSetting) {
                        GestureGuiClickPolicy.MAIN_HAND
                    } else GestureGuiClickPolicy.CLICK,
                    guard = if (heldMainHandSetting) {
                        { actor, _ ->
                            if (heldDiskSetting) {
                                KantanItemService.diskId(actor.inventory.itemInMainHand) != null
                            } else if (heldBlockSetting) {
                                HeldBlockSettingPolicy.canSet(actor.inventory.itemInMainHand.type)
                            } else if (heldFireworkSetting) {
                                actor.inventory.itemInMainHand.type == Material.FIREWORK_ROCKET
                            } else {
                                GestureGuiClickPolicy.hasMainHandItem(actor)
                            }
                        }
                    } else null,
                    hover = singleLineHover(
                        KcI18n.text(
                            player,
                            if (heldMainHandSetting) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MAINHAND_SAVE_HOVER
                            else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_HOVER,
                        ),
                        x = HOVER_SLOT_X,
                        y = HOVER_SLOT_Y,
                    ),
                    visualTag = "lower-edit",
                ),
            )
        }
        if (heldItemSetting && !typedValueSourceSetting) {
            val itemConfigured = node.string("item").isNotBlank() || node.string("itemData").isNotBlank()
            DeclarativeSettings.actionCardSection(
                nodes,
                frame,
                SettingsActionCard(
                    elementId = "lower-item-get",
                    label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_ITEM)),
                    background = Bukkit.createBlockData(
                        if (itemConfigured) Material.BROWN_CONCRETE else DisabledGuiVisualPolicy.material,
                    ),
                    cx = 0.28,
                    cy = -0.15,
                    w = 1.2,
                    h = 0.12,
                    labelSize = 0.0048,
                    labelWidth = 180,
                    gestures = GestureGuiClickPolicy.CLICK,
                    guard = { _, _ ->
                        plugin.scripts.load(state.scriptId)?.graph?.nodes?.get(node.id)?.let { current ->
                            current.string("item").isNotBlank() || current.string("itemData").isNotBlank()
                        } == true
                    },
                    hover = singleLineHover(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_ITEM_HOVER),
                        x = HOVER_SLOT_X,
                        y = HOVER_SLOT_Y,
                    ),
                    visualTag = "lower-item-get",
                ),
            )
        }
        if (field.key == "fireworkContents" && node.type == CommandType.FIREWORK_LAUNCH) {
            val fireworkConfigured = node.string("fireworkItem").isNotBlank() ||
                node.string("fireworkData").isNotBlank()
            DeclarativeSettings.actionCardSection(
                nodes,
                frame,
                SettingsActionCard(
                    elementId = "lower-firework-preview",
                    label = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_FIREWORK_PREVIEW),
                        if (fireworkConfigured) NamedTextColor.GOLD else NamedTextColor.GRAY,
                    ),
                    background = Bukkit.createBlockData(
                        if (fireworkConfigured) Material.ORANGE_CONCRETE else DisabledGuiVisualPolicy.material,
                    ),
                    cx = 0.28,
                    cy = -0.15,
                    w = 1.2,
                    h = 0.12,
                    labelSize = 0.0048,
                    labelWidth = 180,
                    gestures = GestureGuiClickPolicy.CLICK,
                    guard = { _, _ ->
                        plugin.scripts.load(state.scriptId)?.graph?.nodes?.get(node.id)?.let { current ->
                            current.string("fireworkItem").isNotBlank() || current.string("fireworkData").isNotBlank()
                        } == true
                    },
                    hover = singleLineHover(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIREWORK_PREVIEW_HOVER),
                        x = HOVER_SLOT_X,
                        y = HOVER_SLOT_Y,
                    ),
                    visualTag = "lower-firework-preview",
                ),
            )
        }
    }

    /**
     * issue #18 のSlider操作面です。
     *
     * バー上クリック形式で連続操作します。真のドラッグはDisplayEntity上で
     * できないため、区画クリックで代替します。クリックは仮値（プレビュー）の
     * 更新に留め、正本保存・revision進行は［確定］だけが行います。［取消］は
     * 保存値へ戻します。他editorへの共有は間引きます。範囲外・任意精度値は
     * ［入力］のDialogへ委譲し、ここでは範囲内刻みだけを扱います。
     */
    private fun appendSliderCards(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        node: CommandNode,
        fieldKey: String,
        spec: SliderSpec,
        inputCenterY: Double,
        suppressHighlight: Boolean = false,
        previewRaw: String? = null,
        player: Player,
    ) {
        val saved = SliderValuePolicy.format(spec, SliderValuePolicy.parse(spec, node.params[fieldKey]))
        val preview = previewRaw?.takeIf(String::isNotBlank)?.let {
            SliderValuePolicy.format(spec, SliderValuePolicy.parse(spec, it))
        }
        val effective = preview ?: saved
        val effectiveValue = SliderValuePolicy.parse(spec, effective)
        val savedValue = SliderValuePolicy.parse(spec, saved)
        val hoverText = if (preview != null && preview != saved) {
            "範囲 ${SliderValuePolicy.format(spec, spec.min)}〜${SliderValuePolicy.format(spec, spec.max)}／現在値 $saved → 仮値 $preview"
        } else {
            "範囲 ${SliderValuePolicy.format(spec, spec.min)}〜${SliderValuePolicy.format(spec, spec.max)}／刻み ${if (spec.integer) spec.step.toLong().toString() else spec.step.toString()}／現在値 $saved"
        }
        val barY = inputCenterY - 0.14
        val segments = SettingSpec.SLIDER_SEGMENTS
        val segW = 0.095
        val segH = 0.10
        repeat(segments) { index ->
            val centerValue = spec.min + (spec.max - spec.min) * (index + 0.5) / segments
            val filled = centerValue <= effectiveValue + 1e-9
            val holdsSaved = preview != null && preview != saved &&
                centerValue <= savedValue + 1e-9 &&
                spec.min + (spec.max - spec.min) * (index + 1.0) / segments > savedValue + 1e-9
            DeclarativeSettings.actionCardSection(
                nodes,
                frame,
                SettingsActionCard(
                    elementId = "lower-slider-bar:$fieldKey:$index",
                    label = null,
                    background = Bukkit.createBlockData(
                        if (filled) Material.CYAN_CONCRETE else Material.GRAY_CONCRETE,
                    ),
                    cx = -0.32 + segW / 2.0 + index * 0.10,
                    cy = barY,
                    w = segW,
                    h = segH,
                    labelSize = 0.005,
                    labelWidth = 20,
                    gestures = GestureGuiClickPolicy.CLICK,
                    guard = null,
                    hover = singleLineHover(hoverText, x = HOVER_SLOT_X, y = HOVER_SLOT_Y),
                    visualTag = "lower-slider-bar",
                    outline = if (suppressHighlight || !holdsSaved) null
                    else GestureSettingVisualPolicy.nonTabOutlineMaterial(true)?.let { Bukkit.createBlockData(it) },
                ),
            )
        }
        sliderControlCard(
            nodes, frame, "lower-slider-dec:$fieldKey", "−", Material.GRAY_CONCRETE,
            cx = -0.23, cy = inputCenterY, w = 0.18, hoverText, suppressHighlight,
        )
        sliderControlCard(
            nodes, frame, "lower-slider-inc:$fieldKey", "＋", Material.GRAY_CONCRETE,
            cx = -0.05, cy = inputCenterY, w = 0.18, hoverText, suppressHighlight,
        )
        sliderControlCard(
            nodes, frame, "lower-slider-commit:$fieldKey",
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
            if (preview != null && preview != saved) Material.LIME_CONCRETE else DisabledGuiVisualPolicy.material,
            cx = 0.19, cy = inputCenterY, w = 0.26, hoverText, suppressHighlight,
        )
        sliderControlCard(
            nodes, frame, "lower-slider-cancel:$fieldKey",
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL),
            if (preview != null && preview != saved) Material.ORANGE_CONCRETE else DisabledGuiVisualPolicy.material,
            cx = 0.47, cy = inputCenterY, w = 0.26, hoverText, suppressHighlight,
        )
        sliderControlCard(
            nodes, frame, "lower-slider-input:$fieldKey",
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SHOW_DETAILS),
            Material.CYAN_CONCRETE,
            cx = 0.75, cy = inputCenterY, w = 0.26,
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_HOVER),
            suppressHighlight,
        )
    }

    /** Slider操作列の小操作面です。背景・文言・ホバーの定型だけを扱います。 */
    private fun sliderControlCard(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        elementId: String,
        label: String,
        background: Material,
        cx: Double,
        cy: Double,
        w: Double,
        hoverText: String,
        suppressHighlight: Boolean,
    ) {
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = elementId,
                label = Component.text(label),
                background = Bukkit.createBlockData(background),
                cx = cx,
                cy = cy,
                w = w,
                h = 0.12,
                labelSize = 0.005,
                labelWidth = 60,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = singleLineHover(hoverText, x = HOVER_SLOT_X, y = HOVER_SLOT_Y),
                visualTag = elementId.substringBefore(":"),
                outline = null,
            ),
        )
    }

    /**
     * issue #18 のCheckbox操作面です。
     *
     * 二値設定を1クリックで切り替え、checked／uncheckedを色で区別します。
     * nullable三値はここへ載せず、別表現に委譲します。
     */
    private fun appendCheckboxCard(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        node: CommandNode,
        fieldKey: String,
        inputCenterY: Double,
        suppressHighlight: Boolean = false,
    ) {
        val checked = node.boolean(fieldKey)
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-toggle:$fieldKey",
                label = Component.text(if (checked) "ON" else "OFF"),
                background = Bukkit.createBlockData(
                    if (checked) Material.LIME_CONCRETE else Material.GRAY_CONCRETE,
                ),
                cx = 0.28,
                cy = inputCenterY,
                w = 1.2,
                h = SETTING_INPUT_HEIGHT,
                labelSize = 0.006,
                labelWidth = 120,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = singleLineHover(
                    if (checked) "ON（クリックでOFF）" else "OFF（クリックでON）",
                    x = HOVER_SLOT_X,
                    y = HOVER_SLOT_Y,
                ),
                visualTag = "lower-toggle",
                outline = if (suppressHighlight) null
                else GestureSettingVisualPolicy.nonTabOutlineMaterial(checked)?.let { Bukkit.createBlockData(it) },
            ),
        )
    }

    private fun appendTypedValuePreview(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        node: CommandNode,
        fieldKey: String,
        source: CommandValueSource,
        scriptId: java.util.UUID,
        player: Player,
    ) {
        if (source != CommandValueSource.LITERAL) return
        val mapping = when (fieldKey) {
            "item" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_CONFIGURED_ITEM,
                "lower-item-get",
                Material.BROWN_CONCRETE,
            )
            "block" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_CONFIGURED_BLOCK,
                "lower-block-get",
                Material.GRAY_CONCRETE,
            )
            "sound" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_PLAY_CONFIGURED_SOUND,
                "lower-sound-preview",
                Material.PURPLE_CONCRETE,
            )
            "soundSetting" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_PLAY_CONFIGURED_SOUND,
                "lower-sound-preview",
                Material.PURPLE_CONCRETE,
            )
            "effect" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_APPLY_CONFIGURED_EFFECT,
                "lower-effect-preview",
                Material.MAGENTA_CONCRETE,
            )
            else -> return
        }
        val (labelKey, elementId, material) = mapping
        val lines = KcI18n.list(player, labelKey).filter(String::isNotBlank)
        val configured = typedValuePreviewConfigured(node, fieldKey)
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = elementId,
                label = typedValueSourceComponent(lines).color(
                    if (configured) NamedTextColor.GOLD else NamedTextColor.GRAY,
                ),
                background = Bukkit.createBlockData(
                    if (configured) material else DisabledGuiVisualPolicy.material,
                ),
                cx = TYPED_VALUE_PREVIEW_CENTER_X,
                cy = TYPED_VALUE_PREVIEW_CENTER_Y,
                w = TYPED_VALUE_PREVIEW_WIDTH,
                h = TYPED_VALUE_PREVIEW_HEIGHT,
                labelSize = 0.0043,
                labelWidth = 180,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = { _, _ ->
                    plugin.scripts.load(scriptId)?.graph?.nodes?.get(node.id)?.let { current ->
                        CommandSettingsModel.temporaryValueSource(current, fieldKey) == CommandValueSource.LITERAL &&
                            typedValuePreviewConfigured(current, fieldKey)
                    } == true
                },
                hover = multiLineHover(lines, x = HOVER_SLOT_X, y = HOVER_SLOT_Y),
                visualTag = elementId,
            ),
        )
    }

    /**
     * ITEM／BLOCK／SOUND／EFFECTの設定元を、既存の入力欄と同じ右下領域へ2分割で表示します。
     *
     * 直接値側はITEM／BLOCKならメインハンド、SOUND／EFFECTならDialogへ進みます。
     * 一時変数側は他の二段階設定と同じく、1回目を方式選択、2回目を名前入力とします。
     */
    private fun addTypedValueSourceControls(
        node: CommandNode,
        fieldKey: String,
        scriptId: java.util.UUID,
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        suppressHighlight: Boolean,
    ) {
        val selectedSource = CommandSettingsModel.temporaryValueSource(node, fieldKey)
            ?: CommandValueSource.LITERAL
        val sources = listOf(CommandValueSource.LITERAL, CommandValueSource.TEMPORARY)
        val width = TYPED_VALUE_SOURCE_WIDTH
        val centers = listOf(TYPED_VALUE_SOURCE_LEFT_X, TYPED_VALUE_SOURCE_RIGHT_X)
        sources.forEachIndexed { index, source ->
            val selected = source == selectedSource
            val enabled = source == CommandValueSource.TEMPORARY || typedValueSourceAvailable(player, fieldKey)
            val choice = GestureSettingTreeNode(
                id = "value-source:${fieldKey}:${source.name}",
                label = typedValueSourceLines(player, fieldKey, source).joinToString(" "),
                selected = selected,
                enabled = enabled,
            )
            val x = centers[index]
            val lines = typedValueSourceLines(player, fieldKey, source)
            val background = if (enabled) {
                if (selected) Material.CYAN_TERRACOTTA else Material.LIGHT_GRAY_CONCRETE
            } else DisabledGuiVisualPolicy.material
            addBlock(
                visuals,
                "lower-value-source-bg-${source.name.lowercase()}",
                x,
                SETTING_INPUT_CENTER_Y,
                width,
                SETTING_INPUT_HEIGHT,
                background,
                4,
                outlineMaterial = if (suppressHighlight) {
                    null
                } else {
                    GestureSettingVisualPolicy.nonTabOutlineMaterial(selected)
                },
            )
            addText(
                visuals,
                "lower-value-source-label-${source.name.lowercase()}",
                x,
                SETTING_INPUT_CENTER_Y,
                0.0043,
                92,
                typedValueSourceComponent(lines).color(
                    if (choice.enabled) NamedTextColor.GOLD else DisabledChoiceVisualPolicy.textColor,
                ),
            )
            elements += GestureGuiElement(
                elementId = "lower-value-source:$fieldKey:${source.name}",
                bounds = rect(x, SETTING_INPUT_CENTER_Y, width, SETTING_INPUT_HEIGHT),
                acceptedGestures = if (enabled) {
                    typedValueSourceGestures(fieldKey, source)
                } else emptySet(),
                // メインハンドの所持状態は画面生成後にも変わるため、クリック時点で
                // 同じ有効性を再確認します。一時変数側は空の名前でも方式選択を許可します。
                gestureGuard = if (enabled) {
                    { actor, _ -> source == CommandValueSource.TEMPORARY || typedValueSourceAvailable(actor, fieldKey) }
                } else {
                    { _, _ -> false }
                },
                targetVisualId = "lower-value-source-bg-${source.name.lowercase()}",
                hoverText = multiLineHover(lines, x = x, y = HOVER_SLOT_Y),
            )
        }
        addTypedValuePreview(
            node = node,
            fieldKey = fieldKey,
            source = selectedSource,
            scriptId = scriptId,
            player = player,
            visuals = visuals,
            elements = elements,
        )
    }

    private fun typedValueSourceAvailable(player: Player, fieldKey: String): Boolean = when (fieldKey) {
        "item" -> GestureGuiClickPolicy.hasMainHandItem(player)
        "block" -> HeldBlockSettingPolicy.canSet(player.inventory.itemInMainHand.type)
        // SOUND／EFFECTはDialog入力なので、メインハンドの状態に依存しません。
        "sound", "soundSetting", "effect" -> true
        else -> false
    }

    internal fun typedValueSourceGestures(
        fieldKey: String,
        source: CommandValueSource,
    ): Set<GestureGuiGesture> = if (
        source == CommandValueSource.TEMPORARY || fieldKey !in setOf("item", "block")
    ) {
        GestureGuiClickPolicy.CLICK
    } else {
        GestureGuiClickPolicy.MAIN_HAND
    }

    /**
     * ノード未選択時の中央パネルです。
     *
     * ノード選択を促すだけではプログラム全体の設定へ到達できず、空のグラフでは
     * 「何をすれば追加できるか」も分かりません。ここではプログラム名・タイマーを上段、
     * ワールド変数・ディスク取得を下段とする2x2配置で同じ中央領域へ置き、
     * グラフが空の場合だけ追加操作の明示的な案内を表示します。これらはノード設定の
     * タブ木とは別ドメインなので、ノードを選択していないときにだけ表示します。
     *
     * ホバーは上段カードでは図柄の上側、下段カードでは図柄の下側へ出します。
     */
    /**
     * プログラム全体設定の宣言実装です。旧実装と同じ算出・文言・座標・要素 ID・ホバーを用い、
     * 既存の画面定義・操作振分け（`view()`）を利用します。
     */
    private fun buildScriptSettingsNew(
        state: GestureEditorState,
        player: Player,
        script: me.awabi2048.kantancommander.model.DiskScript?,
        attention: GestureAttentionState = GestureAttentionState.EMPTY,
    ): GestureGuiView {
        val hint = if (script?.graph?.nodes?.isEmpty() == true) {
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_EMPTY_GRAPH_HINT
        } else {
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_SELECT_NODE_HINT
        }
        val cards = mutableListOf<ScriptSettingCard>()
        if (script != null) {
            val programNameState = if (script.name.isBlank()) {
                GestureSettingValueState.INITIAL
            } else {
                GestureSettingValueState.CONFIGURED
            }
            cards += ScriptSettingCard(
                elementId = "lower-script-name",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_PROGRAM_NAME),
                value = Component.text(script.name.ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }),
                background = Bukkit.createBlockData(
                    GestureSettingVisualPolicy.material(
                        GestureSettingSelectionMode.MULTIPLE,
                        programNameState,
                    ),
                ),
                icon = ItemStack(Material.NAME_TAG),
                attentionHover = null,
                cx = SCRIPT_SETTING_COL_LEFT_X,
                cy = SCRIPT_SETTING_ROW_TOP_Y,
                w = SCRIPT_SETTING_WIDTH,
            )
            val timerState = if (script.timer.enabled) {
                GestureSettingValueState.CONFIGURED
            } else {
                GestureSettingValueState.INITIAL
            }
            val timerValue = if (script.timer.enabled) {
                "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED)} " +
                    KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INTERVAL_SECONDS,
                        mapOf("value" to CommandDialogSpecs.formatSeconds(script.timer.intervalSeconds)),
                    )
            } else {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED)
            }
            cards += ScriptSettingCard(
                elementId = "lower-script-timer",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TIMER),
                value = Component.text(timerValue),
                background = Bukkit.createBlockData(
                    GestureSettingVisualPolicy.material(
                        GestureSettingSelectionMode.MULTIPLE,
                        timerState,
                    ),
                ),
                icon = ItemStack(Material.CLOCK),
                attentionHover = if (attention.timer) {
                    // 注意ホバーは上段カードの図柄の上側へ出します（下段は下側の規則）。
                    // 固定ホバースロット（HOVER_SLOT_Y）は使わず、対象カードの直上へ置きます。
                    singleLineHover(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET),
                        x = SCRIPT_SETTING_COL_RIGHT_X,
                        y = SCRIPT_SETTING_ROW_TOP_Y +
                            DeclarativeSettings.SCRIPT_CARD_HEIGHT / 2.0 +
                            HOVER_BUTTON_GAP,
                    )
                } else {
                    null
                },
                cx = SCRIPT_SETTING_COL_RIGHT_X,
                cy = SCRIPT_SETTING_ROW_TOP_Y,
                w = SCRIPT_SETTING_WIDTH,
            )
            val worldId = resolveVariableWorldId(state)
            val variableCount = worldId?.let { plugin.variables.list(it).size }
            val variablesState = if ((variableCount ?: 0) > 0) {
                GestureSettingValueState.CONFIGURED
            } else {
                GestureSettingValueState.INITIAL
            }
            val variablesValue = if (worldId == null) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
            } else {
                KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_COUNT,
                    mapOf("value" to (variableCount ?: 0)),
                )
            }
            cards += ScriptSettingCard(
                elementId = "lower-script-variables",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES),
                value = Component.text(variablesValue),
                background = Bukkit.createBlockData(
                    GestureSettingVisualPolicy.material(
                        GestureSettingSelectionMode.MULTIPLE,
                        variablesState,
                    ),
                ),
                icon = ItemStack(Material.WRITABLE_BOOK),
                attentionHover = null,
                cx = SCRIPT_SETTING_COL_LEFT_X,
                cy = SCRIPT_SETTING_ROW_BOTTOM_Y,
                w = SCRIPT_SETTING_WIDTH,
            )
            // 未選択時下部の4枚目はプログラムディスクの取得操作です。設定値の編集ではなく
            // 内容の複製であり、簡易GUIの出力操作と同じく内容があるときだけ有効にします。
            // 空のプログラムでは未設定表示とし、クリック時点のガードでも受け付けません。
            val diskAvailable = script.hasDiskContent()
            val diskState = if (diskAvailable) {
                GestureSettingValueState.CONFIGURED
            } else {
                GestureSettingValueState.INITIAL
            }
            val diskValue = if (diskAvailable) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_OUTPUT_COPY)
            } else {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
            }
            cards += ScriptSettingCard(
                elementId = "lower-script-disk",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_DISK),
                value = Component.text(diskValue),
                background = Bukkit.createBlockData(
                    GestureSettingVisualPolicy.material(
                        GestureSettingSelectionMode.MULTIPLE,
                        diskState,
                    ),
                ),
                icon = ItemStack(Material.MUSIC_DISC_OTHERSIDE),
                attentionHover = null,
                // 所持状態や保存内容は表示生成後に変わるため、表示時の真偽値を焼き付けず
                // クリック時点で最新スクリプトの内容有無を再確認します。
                guard = { _, _ ->
                    plugin.scripts.load(state.scriptId)?.hasDiskContent() == true
                },
                cx = SCRIPT_SETTING_COL_RIGHT_X,
                cy = SCRIPT_SETTING_ROW_BOTTOM_Y,
                w = SCRIPT_SETTING_WIDTH,
            )
        }
        val compiled = KantanLayoutBridge.compile(
            DeclarativeSettings.scriptSettingsDocument(
                Component.text(KcI18n.text(player, hint)),
                cards,
            ),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][DeclarativeScriptSettings] $diagnostics")
            },
        )
        return view(GestureLowerMode.SETTINGS, compiled.elements, compiled.visuals)
    }

    /**
     * 編集対象ワールドのワールド変数IDを解決します。
     *
     * 実行時と同じくMyWorldManagerの所有情報を正とし、未配置・所有外では
     * nullを返して一覧を開けない状態を表示側へ伝えます。
     */
    internal fun resolveVariableWorldId(state: GestureEditorState): java.util.UUID? {
        val worldName = state.placement?.world ?: return null
        if (!plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) return null
        return runCatching {
            me.awabi2048.myworldmanager.api.MyWorldManagerApi.getWorldRepository()
                ?.findByWorldName(worldName)?.uuid
        }.getOrNull()
    }



    /** ワールド変数の型名を、ノード設定と同じ表示キーで解決します。 */
    private fun variableTypeName(player: Player, type: VariableType): String = KcI18n.text(
        player,
        when (type) {
            VariableType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMBER
            VariableType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT
        },
    )

    /**
     * 直接値を確認するための実行ボタンです。
     *
     * 一時変数は実行時にその時点の値へ解決されるため、編集画面で固定値を
     * 「現在設定値」として取り出したり再生したりできません。したがって、
     * このボタン自体を直接値モードのときだけ描画します。
     */
    private fun addTypedValuePreview(
        node: CommandNode,
        fieldKey: String,
        source: CommandValueSource,
        scriptId: java.util.UUID,
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
    ) {
        if (source != CommandValueSource.LITERAL) return
        val configured = typedValuePreviewConfigured(node, fieldKey)
        val (labelKey, elementId, material) = when (fieldKey) {
            "item" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_CONFIGURED_ITEM,
                "lower-item-get",
                Material.BROWN_CONCRETE,
            )
            "block" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_CONFIGURED_BLOCK,
                "lower-block-get",
                Material.GRAY_CONCRETE,
            )
            "sound" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_PLAY_CONFIGURED_SOUND,
                "lower-sound-preview",
                Material.PURPLE_CONCRETE,
            )
            "soundSetting" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_PLAY_CONFIGURED_SOUND,
                "lower-sound-preview",
                Material.PURPLE_CONCRETE,
            )
            "effect" -> Triple(
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_APPLY_CONFIGURED_EFFECT,
                "lower-effect-preview",
                Material.MAGENTA_CONCRETE,
            )
            else -> return
        }
        val lines = KcI18n.list(player, labelKey).filter(String::isNotBlank)
        val centerY = TYPED_VALUE_PREVIEW_CENTER_Y
        val height = TYPED_VALUE_PREVIEW_HEIGHT
        val background = if (configured) material else DisabledGuiVisualPolicy.material
        addBlock(
            visuals,
            "$elementId-bg",
            TYPED_VALUE_PREVIEW_CENTER_X,
            centerY,
            TYPED_VALUE_PREVIEW_WIDTH,
            height,
            background,
            4,
        )
        addText(
            visuals,
            elementId,
            TYPED_VALUE_PREVIEW_CENTER_X,
            centerY,
            0.0043,
            180,
            typedValueSourceComponent(lines).color(if (configured) NamedTextColor.GOLD else NamedTextColor.GRAY),
        )
        elements += GestureGuiElement(
            elementId = elementId,
            bounds = rect(TYPED_VALUE_PREVIEW_CENTER_X, centerY, TYPED_VALUE_PREVIEW_WIDTH, height),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            gestureGuard = { _, _ ->
                plugin.scripts.load(scriptId)?.graph?.nodes?.get(node.id)?.let { current ->
                    CommandSettingsModel.temporaryValueSource(current, fieldKey) == CommandValueSource.LITERAL &&
                        typedValuePreviewConfigured(current, fieldKey)
                } == true
            },
            targetVisualId = "$elementId-bg",
            hoverText = multiLineHover(lines, x = HOVER_SLOT_X, y = HOVER_SLOT_Y),
        )
    }

    internal fun typedValuePreviewConfigured(node: CommandNode, fieldKey: String): Boolean = when (fieldKey) {
        "item" -> node.string("item").isNotBlank() || node.string("itemData").isNotBlank()
        "block" -> Material.matchMaterial(node.string("block"))
            ?.takeUnless { it == Material.AIR } != null
        "sound", "effect" -> node.string(fieldKey).isNotBlank()
        "soundSetting" -> node.string("sound").isNotBlank()
        else -> false
    }

    /**
     * 操作不能な設定候補は、候補専用の共通ポリシーで赤コンクリートへ変更します。
     * 通常の設定項目や空欄の無効表示とは意味が異なるため、無効な選択肢だけを
     * DisabledChoiceVisualPolicyへ通します。
     */
    internal fun settingChoiceMaterial(choice: GestureSettingTreeNode): Material =
        if (choice.enabled) {
            // 択一／複数選択、初期／設定済みの区別は背景色へ反映しません。
            Material.LIGHT_GRAY_CONCRETE
        } else {
            DisabledChoiceVisualPolicy.material
        }

    /**
     * 設定タブと固定操作を親の設定画面で描画します。
     * 詳細子画面へ進んでも親の表示は背面に残るため、子画面側へ同じナビゲーションを
     * 複製して操作領域を混在させません。
     */
    private fun addSettingsNavigation(
        state: GestureEditorState,
        player: Player,
        node: CommandNode,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        attentionFields: Set<String> = emptySet(),
        suppressHighlight: Boolean = false,
        canDuplicate: Boolean,
    ) {
        val fields = CommandSettingsModel.gestureVisibleFields(node)
        if (fields.isEmpty()) {
            // 設定選択子画面から親のナビゲーションを再構成する場合も、制御ノードの
            // 削除ボタンだけは欠落させません。通常の設定タブがないことと、ノードを
            // 削除できないことは別の状態です。
            addNodeActions(player, visuals, elements, canDuplicate)
            return
        }
        val activeField = state.settingFieldKey?.let { key -> fields.indexOfFirst { it.key == key } }
            ?.takeIf { it >= 0 } ?: state.settingsTab.coerceIn(0, fields.lastIndex)
        check(fields.size <= SETTINGS_TAB_MAX) {
            "設定タブは${SETTINGS_TAB_MAX}個以内で定義してください: ${node.type}=${fields.size}"
        }
        fields.forEachIndexed { index, field ->
            val cy = SETTINGS_TAB_TOP_Y - index * SETTINGS_TAB_PITCH
            val on = index == activeField
            val attention = field.key in attentionFields
            // タブの選択状態は縁取りではなく右へ幅15%伸ばし、選択中タブの背景はシアンの
            // テラコッタで示します。テキスト位置は維持し、背景とヒットボックスだけ拡張します。
            // 未完了警告はタブテキストの§cです。
            val tabSelected = on && !suppressHighlight
            val tabWidth = GestureSettingVisualPolicy.selectedTabWidth(SETTINGS_TAB_WIDTH, tabSelected)
            val tabCx = GestureSettingVisualPolicy.selectedTabCenterX(SETTINGS_TAB_CENTER_X, SETTINGS_TAB_WIDTH, tabSelected)
            addBlock(
                visuals,
                "tab-bg-$index",
                tabCx,
                cy,
                tabWidth,
                SETTINGS_TAB_HEIGHT,
                GestureSettingVisualPolicy.tabMaterial(tabSelected),
                4,
            )
            val tabLabel = fieldTabLabel(player, node, field)
            val tabTextColor = GestureSettingVisualPolicy.tabTextColor(attention)
            addText(visuals, "tab-$index", SETTINGS_TAB_CENTER_X, cy - 0.02, 0.0055, 90,
                if (tabTextColor == null) Component.text(tabLabel) else Component.text(tabLabel, tabTextColor))
            elements.add(GestureGuiElement(
                elementId = "lower-tab:$index",
                bounds = rect(tabCx, cy, tabWidth, SETTINGS_TAB_HEIGHT),
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                targetVisualId = "tab-bg-$index",
                // 要確認タブは色だけでなく状態名も示します。ホバー中は操作説明欄を
                // 置き換えて警告を表示し、どのタブが未完了かを文面で伝えます。
                hoverText = if (attention) {
                    attentionWarningHover(player, node, field.key)
                } else null,
            ))
        }


        addNodeActions(player, visuals, elements, canDuplicate)
    }

    /** ノード選択中の固定操作を、表示可否と配置の契約ごとまとめて描画します。 */
    private fun addNodeActions(
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        canDuplicate: Boolean,
    ) {
        if (canDuplicate) addDuplicateAction(player, visuals, elements)
        addDeleteAction(player, visuals, elements)
    }

    /** 選択中ノードの直後へ同じ設定のコマンドを挿入する操作です。 */
    private fun addDuplicateAction(
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
    ) {
        val duplicateY = SETTINGS_TAB_DUPLICATE_Y
        addBlock(
            visuals,
            "duplicate-bg",
            SETTINGS_TAB_CENTER_X,
            duplicateY,
            SETTINGS_TAB_WIDTH,
            SETTINGS_TAB_HEIGHT,
            Material.CYAN_CONCRETE,
            4,
        )
        addText(
            visuals,
            "duplicate-label",
            SETTINGS_TAB_CENTER_X,
            duplicateY,
            0.0049,
            90,
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DUPLICATE)),
        )
        elements.add(GestureGuiElement(
            elementId = "lower-duplicate",
            bounds = rect(SETTINGS_TAB_CENTER_X, duplicateY, SETTINGS_TAB_WIDTH, SETTINGS_TAB_HEIGHT),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            targetVisualId = "duplicate-bg",
        ))
    }

    /**
     * 設定値の有無に依存しない、ノード削除の固定操作です。
     *
     * 表示位置・要素ID・クリック契約を通常の設定画面と共有し、入力側の
     * `lower-delete`から既存の削除確認／保存失敗フィードバックへ接続します。
     */
    private fun addDeleteAction(
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
    ) {
        val deleteY = SETTINGS_TAB_ACTION_Y
        addBlock(
            visuals,
            "delete-bg",
            SETTINGS_TAB_CENTER_X,
            deleteY,
            SETTINGS_TAB_WIDTH,
            SETTINGS_TAB_HEIGHT,
            Material.RED_CONCRETE,
            4,
        )
        addText(
            visuals,
            "delete-label",
            SETTINGS_TAB_CENTER_X,
            deleteY,
            0.0049,
            90,
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE)),
        )
        elements.add(GestureGuiElement(
            elementId = "lower-delete",
            bounds = rect(SETTINGS_TAB_CENTER_X, deleteY, SETTINGS_TAB_WIDTH, SETTINGS_TAB_HEIGHT),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            targetVisualId = "delete-bg",
        ))
    }

    /** 表示時間はグループ名をタブへ、表示継続時間は内部の現在値ラベルへ分離します。 */
    internal fun fieldTabLabel(player: Player, node: CommandNode, field: EditorField): String =
        if (node.type == CommandType.DISPLAY_TEXT && field.key == "staySeconds") {
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TITLE)
        } else {
            KcI18n.text(player, field.label)
        }

    /** 項目名と値を別々のTextDisplayへ配置し、表示領域の分離を明示します。 */
    private fun addValueRow(
        visuals: MutableList<GestureGuiVisual>,
        id: String,
        y: Double,
        label: String,
        value: String,
        labelX: Double = -0.08,
        valueX: Double = 0.52,
    ) {
        addText(visuals, "$id-label", labelX, y, 0.0060, 120, Component.text(label))
        addText(visuals, "$id-value", valueX, y, 0.0060, 170, Component.text(value))
    }

    /** 複数の意味値を1行ずつ分離し、共通の値行レイアウトで配置します。 */
    private fun addCompositeValueRows(
        visuals: MutableList<GestureGuiVisual>,
        rows: List<DisplayValue.TimingRow>,
        layout: GestureSettingValueRowsLayout,
    ) {
        check(rows.size == layout.rowCentersY.size) {
            "複合表示の値行とレイアウトの行数が一致しません: ${rows.size} != ${layout.rowCentersY.size}"
        }
        rows.forEachIndexed { index, row ->
            addValueRow(
                visuals,
                id = "lower-setting-composite-$index",
                y = layout.rowCentersY[index],
                label = row.label,
                value = row.value,
            )
        }
    }

    /**
     * 説明ブロックを意味別のスロットへ配置します。
     *
     * 常設説明は灰色1本に統一し、項目の説明（field_description）を表示します。
     * 従来の白い説明行と操作動詞行（field_action）は、内容が説明と重複するため
     * ジェスチャーGUIから外しました。候補や操作面のホバー説明は、この灰色
     * スロットをreplacesVisualIdで置き換えて同じ位置・寸法へ表示します。
     * 2行目は要確認の状態名、無ければ「詳細を持つ候補の再クリック」案内です。
     * 複数の現在値行を表示する画面では、呼び出し側が値行数に応じたdetailYを渡します。
     */
    private fun addDescriptionRows(
        visuals: MutableList<GestureGuiVisual>,
        player: Player,
        field: EditorField?,
        fallback: String = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_SETTINGS_FIELD_FALLBACK),
        detailHint: String? = null,
        warning: String? = null,
        centerX: Double = 0.28,
        hoverY: Double = ACTION_DESCRIPTION_Y,
        detailY: Double = SETTING_DETAIL_ANCHOR_Y,
    ) {
        addText(
            visuals,
            SETTING_DESCRIPTION_HOVER_ID,
            centerX,
            hoverY,
            DESCRIPTION_TEXT_SIZE,
            280,
            Component.text(
                field?.let { fieldDescription(player, it) } ?: fallback,
                NamedTextColor.GRAY,
            ),
        )
        if (!warning.isNullOrBlank()) {
            // 要確認の状態名は詳細案内より優先して表示します（色だけの通知を避ける規則）。
            addText(
                visuals,
                "setting-description-detail",
                centerX,
                detailY,
                0.0041,
                280,
                Component.text(warning, NamedTextColor.RED),
            )
        } else if (!detailHint.isNullOrBlank()) {
            addText(
                visuals,
                "setting-description-detail",
                centerX,
                detailY,
                0.0041,
                280,
                Component.text(detailHint, NamedTextColor.GRAY),
            )
        }
    }

    /** 項目の説明文です。複数行の説明は1文へ連結し、折り返しは描画側へ任せます。 */
    internal fun fieldDescription(player: Player, field: EditorField): String =
        KcI18n.list(player, field.descriptionKey)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { KcI18n.text(player, field.label) }

    /**
     * 現在のタブに対応する固定キーから、設定不足の警告文を解決します。
     *
     * 検証エラーの文言をそのまま表示せず、タブごとの必要設定を示す文面へ
     * 統一します。キーの選択はCommandSettingsModelへ集約し、表示側でコマンド型の
     * 分岐や入力値の解析を行わないようにします。
     */
    internal fun attentionWarning(
        player: Player,
        node: CommandNode,
        fieldKey: String,
        attentionFields: Set<String>,
    ): String? = if (fieldKey in attentionFields) {
        KcI18n.text(player, CommandSettingsModel.incompleteWarningKey(node, fieldKey))
    } else {
        null
    }

    /** 要確認タブへホバーしたときも、そのタブ固有の固定警告文を表示します。 */
    internal fun attentionWarningHover(
        player: Player,
        node: CommandNode,
        fieldKey: String,
    ): GestureGuiHoverText =
        singleLineHover(
            KcI18n.text(player, CommandSettingsModel.incompleteWarningKey(node, fieldKey)),
            x = HOVER_SLOT_X,
            y = HOVER_SLOT_Y,
        )

    /** 選択肢ごとの意味を、選択肢IDと明示対応させたカタログキーから生成します。 */
    internal fun choiceDescription(player: Player, choice: SettingChoice, fieldKey: String?): String? = when {
        // 対象分類は、ラベルと同一の文面ではなく項目の説明を表示します。
        choice.id == "target:${TargetCategory.PLAYER.name}" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PLAYER_TARGET)
        choice.id == "target:${TargetCategory.NON_PLAYER_ENTITY.name}" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_ENTITY_TARGET)
        choice.id == "target:${TargetCategory.TEMPORARY.name}" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_SOURCE_TEMPORARY)
        // 条件種別は、種別ごとの説明を選択肢IDから解決して表示します。
        choice.id.startsWith("condition-kind:") -> conditionKindDescription(player, choice.id)
        choice.id == "filter:entityType" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_ENTITY_TYPE)
        choice.id == "filter:distance" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_MINIMUM_DISTANCE_BODY)
        choice.id == "filter:range" ->
            KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_RANGE).joinToString(" ")
        choice.id == "filter:limit" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_LIMIT)
        choice.id == "filter:sort" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_SORT)
        choice.id == "filter:gameMode" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_GAME_MODE)
        choice.id == "filter:tag" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_TAG)
        choice.id == "filter:name" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_NAME)
        choice.id.startsWith("filter:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_DEFAULT)
        choice.id.startsWith("position:") -> suffixKeyDescription(player, choice.id, "position:") { suffix ->
            when (runCatching { PositionKind.valueOf(suffix) }.getOrNull()) {
                PositionKind.DISK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_CONTROL_BLOCK
                PositionKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_TARGET
                PositionKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_TEMPORARY_VARIABLE
                PositionKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_MYWORLD_SPAWN
                PositionKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_COORDINATES
                PositionKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_CAPTURED
                null -> null
            }
        }
        choice.id.startsWith("facing:") -> suffixKeyDescription(player, choice.id, "facing:") { suffix ->
            when (runCatching { FacingKind.valueOf(suffix) }.getOrNull()) {
                FacingKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_CAPTURED
                FacingKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_TARGET
                FacingKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_SOURCE_TEMPORARY
                FacingKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_COORDINATES
                FacingKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_MYWORLD_SPAWN
                FacingKind.ROTATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_ROTATION
                null -> null
            }
        }
        choice.id.startsWith("condition-") -> conditionDetailDescription(player, choice.id)
        choice.id.startsWith("display:") -> suffixKeyDescription(player, choice.id, "display:") { suffix ->
            when (suffix) {
                "tellraw" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_DISPLAY_TELLRAW
                "title" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_DISPLAY_TITLE
                "subtitle" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_DISPLAY_SUBTITLE
                "actionbar" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_DISPLAY_ACTIONBAR
                else -> null
            }
        }
        choice.id.startsWith("action:") -> suffixKeyDescription(player, choice.id, "action:") { suffix ->
            when (suffix) {
                "ride" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_ACTION_RIDE
                "dismount" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_ACTION_DISMOUNT
                else -> null
            }
        }
        choice.id.startsWith("equipmentSlot:") -> suffixKeyDescription(player, choice.id, "equipmentSlot:") { suffix ->
            when (suffix) {
                "HAND" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HAND
                "OFF_HAND" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_OFF_HAND
                "HEAD" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HEAD
                "CHEST" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_CHEST
                "LEGS" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_LEGS
                "FEET" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_FEET
                else -> null
            }
        }
        choice.id.startsWith("overwrite:") -> KcI18n.text(
            player,
            if (choice.id.endsWith(":true")) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED
            else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED,
        )
        choice.id.startsWith("tagOperation:") -> suffixKeyDescription(player, choice.id, "tagOperation:") { suffix ->
            when (suffix) {
                "add" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ADD
                "remove" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REMOVE
                else -> null
            }
        }
        choice.id.startsWith("shake:") -> suffixKeyDescription(player, choice.id, "shake:") { suffix ->
            when (suffix) {
                "positional" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SHAKE_POSITIONAL
                "rotational" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SHAKE_ROTATIONAL
                else -> null
            }
        }
        choice.id.startsWith("soundScope:") -> KcI18n.text(
            player,
            if (choice.id.endsWith("WORLD")) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_SCOPE_WORLD
            else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_SCOPE_POSITION,
        )
        choice.id.startsWith("type:") -> suffixKeyDescription(player, choice.id, "type:") { suffix ->
            when (runCatching { VariableType.valueOf(suffix) }.getOrNull()) {
                VariableType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_TYPE_DECIMAL
                VariableType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_TYPE_TEXT
                null -> null
            }
        }
        choice.id.startsWith("operation:") -> suffixKeyDescription(player, choice.id, "operation:") { suffix ->
            when (runCatching { VariableOperation.valueOf(suffix) }.getOrNull()) {
                VariableOperation.DEFINE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_OPERATION_SET
                VariableOperation.CHANGE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_OPERATION_ADD
                null -> null
            }
        }
        choice.id.startsWith("value:") -> suffixKeyDescription(player, choice.id, "value:") { suffix ->
            when (suffix) {
                "direct" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VALUE_DIRECT
                "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VALUE_COUNT
                else -> null
            }
        }
        choice.id.startsWith("source:") -> suffixKeyDescription(player, choice.id, "source:") { suffix ->
            when (suffix) {
                "FIXED" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_SOURCE_FIXED
                "TEMPORARY" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_SOURCE_TEMPORARY
                "WORLD" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_SOURCE_WORLD
                else -> null
            }
        }
        // 条件の反転設定は、選択肢の意味を編集中のタブ（fieldKey）へ投影します。
        choice.id.startsWith("invert:") -> suffixKeyDescription(player, choice.id, "invert:") { suffix ->
            when {
                suffix == "true" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_INVERT_ON
                suffix == "false" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_INVERT_OFF
                else -> null
            }
        }
        choice.id.startsWith("block:") -> suffixKeyDescription(player, choice.id, "block:") { suffix ->
            when (suffix) {
                "setblock" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_BLOCK_SETBLOCK
                "fill" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_BLOCK_FILL
                else -> null
            }
        }
        // issue #29: プリセットの説明は既存の種別説明を再利用し、新規キーを増やしません。
        choice.id.startsWith("target-preset:") || choice.id.startsWith("position-entity-preset:") ||
            choice.id.startsWith("facing-entity-preset:") -> when (choice.id.substringAfterLast(":")) {
            "NEARBY_PLAYERS", "MYWORLD_PLAYERS", "NEAREST_PLAYER" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PLAYER_TARGET)
            else ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_ENTITY_TARGET)
        }
        choice.id.startsWith("position-preset:") -> when (choice.id.substringAfterLast(":")) {
            "CONTROL_BLOCK" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_CONTROL_BLOCK)
            "NEAREST_PLAYER", "NEARBY_PLAYERS" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_TARGET)
            "MYWORLD_SPAWN" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_MYWORLD_SPAWN)
            else ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_COORDINATES)
        }
        choice.id == "position-entity" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_TARGET)
        choice.id.startsWith("facing-preset:") -> when (choice.id.substringAfterLast(":")) {
            "NEAREST_PLAYER" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_TARGET)
            "MYWORLD_SPAWN" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_MYWORLD_SPAWN)
            "COORDINATES" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_COORDINATES)
            else ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_ROTATION)
        }
        choice.id == "facing-entity" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_TARGET)
        choice.id.startsWith("myworld-spawn:") ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_MYWORLD_SPAWN)
        else -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_DEFAULT)
    }

    /** 選択肢IDのサフィックスから説明キーを解決する共通処理です。解決できないIDは総称説明へフォールバックします。 */
    private fun suffixKeyDescription(
        player: Player,
        choiceId: String,
        prefix: String,
        keyFor: (String) -> com.awabi2048.ccsystem.api.localization.LocalizationKey<String>?,
    ): String = keyFor(choiceId.removePrefix(prefix))
        ?.let { KcI18n.text(player, it) }
        ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_DEFAULT)

    /** 条件種別の選択肢ID（condition-kind:<KIND>）から、種別ごとの説明へ解決します。 */
    private fun conditionKindDescription(player: Player, choiceId: String): String? =
        when (runCatching { ConditionKind.valueOf(choiceId.removePrefix("condition-kind:")) }.getOrNull()) {
            ConditionKind.TARGET_EXISTS ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_TARGET_EXISTS)
            ConditionKind.PLAYER_STATE ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_ENTITY_STATE)
            ConditionKind.VARIABLE_STATE ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_VARIABLE_STATE)
            ConditionKind.BLOCK_STATE ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_BLOCK_STATE)
            ConditionKind.CONTROL_BLOCK_STATE ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_CONTROL_BLOCK_STATE)
            null -> null
        }

    /** 条件詳細の選択肢IDから、項目ごとの説明へ解決します。 */
    private fun conditionDetailDescription(player: Player, choiceId: String): String? =
        when (choiceId) {
            "condition-target" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_TARGET)
            "condition-state" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_STATE)
            "condition-variable" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_VARIABLE)
            "condition-scope" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_SCOPE)
            "condition-operator" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_OPERATOR)
            "condition-value" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_VALUE)
            "condition-block" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_BLOCK)
            "condition-item" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_ITEM)
            "condition-count" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_COUNT)
            "condition-position" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_POSITION)
            "condition-control-block-state:${ControlBlockStateKind.REDSTONE_INPUT.name}" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_REDSTONE_INPUT)
            else -> null
        }

    /** 項目の説明（テキストリスト）を、ホバー用の1文へ連結します。 */
    private fun fieldDescriptionText(
        player: Player,
        key: com.awabi2048.ccsystem.api.localization.LocalizationKey<List<String>>,
    ): String = KcI18n.list(player, key)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_DEFAULT) }


    /**
     * ホバー表示を共通のTextDisplay仕様へ変換します。通常の操作案内は1行、
     * ワールド変数の値・型のように意味行を分ける情報だけは複数行を渡します。
     * 親画面では常設の灰色説明と対になる画面下段のスロットへ表示し、
     * 子画面のみ、従来どおり説明スロットの置換を使います。
     */
    private fun hoverText(
        text: Component,
        x: Double,
        y: Double,
        replacesVisualId: String? = null,
    ): GestureGuiHoverText =
        GestureGuiHoverText(
            text = text,
            x = x,
            y = y,
            size = DESCRIPTION_TEXT_SIZE,
            lineWidth = 280,
            replacesVisualId = replacesVisualId,
        )

    internal fun singleLineHover(
        text: String,
        x: Double,
        y: Double,
        replacesVisualId: String? = null,
    ): GestureGuiHoverText = hoverText(Component.text(text), x, y, replacesVisualId)

    /** 複数の意味行を、空行を挿入せずに一つのホバーTextDisplayへ配置します。 */
    internal fun multiLineHover(
        lines: List<String>,
        x: Double,
        y: Double,
        replacesVisualId: String? = null,
    ): GestureGuiHoverText {
        val nonBlankLines = lines.filter(String::isNotBlank)
        val text = nonBlankLines.foldIndexed(Component.empty()) { index, result, line ->
            val component = Component.text(line)
            if (index == 0) component else result.append(Component.newline()).append(component)
        }
        return hoverText(text, x, y, replacesVisualId)
    }

    /**
     * 2列以上の選択肢で、行位置に応じたホバー説明の配置を返します。
     *
     * 上段・下段はカードに近い上下へ置き、中段だけは説明が左右のカード間で
     * 交差しないよう共通説明領域へ集約します。子画面は常設説明を置き換え、
     * 親画面は右下の専用ホバースロットを使うという既存の画面契約もここで
     * 吸収し、各ボタン生成側に配置条件を複製しません。
     * 宣言実装と共有するためモジュール内へ公開します。
     */
    internal fun parallelButtonHover(
        text: String,
        x: Double,
        y: Double,
        row: Int,
        rowCount: Int,
        height: Double,
        descriptionX: Double,
        descriptionY: Double,
        replacesDescription: Boolean,
        parallel: Boolean,
    ): GestureGuiHoverText = when {
        parallel && row == 0 -> singleLineHover(
            text,
            x = x,
            y = y + height / 2.0 + HOVER_BUTTON_GAP,
        )
        parallel && rowCount > 1 && row == rowCount - 1 -> singleLineHover(
            text,
            x = x,
            y = y - height / 2.0 - HOVER_BUTTON_GAP,
        )
        else -> singleLineHover(
            text,
            x = descriptionX,
            y = descriptionY,
            replacesVisualId = if (replacesDescription) SETTING_DESCRIPTION_HOVER_ID else null,
        )
    }

    /**
     * 現在の設定経路における直下の木ノードを返します。
     *
     * `TARGET` の対象種別、`POSITION` の移動先種別、条件の
     * 下位ドメインだけが必要に応じて子を持ちます。単純な列挙選択は葉として
     * 扱い、共通の親画面へ表示して不要な子画面を作りません。
     */
    internal fun settingTreeNodes(
        node: CommandNode,
        context: CommandSettingContext,
        screen: GestureSettingScreen,
        fieldKey: String,
        player: Player,
        attentionFields: Set<String> = emptySet(),
        // issue #29: 子画面ではエンティティ導線の再入場を隠し、往復ループを作りません。
        inChild: Boolean = false,
    ): List<GestureSettingTreeNode> = rawSettingTreeNodes(node, context, screen, fieldKey, player, inChild)
        .map { decorateSettingChoice(node, context, fieldKey, it, attentionFields) }

    private fun rawSettingTreeNodes(
        node: CommandNode,
        context: CommandSettingContext,
        screen: GestureSettingScreen,
        fieldKey: String,
        player: Player,
        inChild: Boolean = false,
    ): List<GestureSettingTreeNode> = when (screen) {
        GestureSettingScreen.TARGET -> targetChoices(node, context, player)
        GestureSettingScreen.POSITION -> positionChoices(node, context, player, inChild).map { choice ->
            if (choice.id == "position:${PositionKind.TARGET.name}" && context.role == CommandSettingRole.DESTINATION) {
                // 移動先のインライン選択は、実体の分類（プレイヤー／プレイヤー以外）だけを
                // 選ぶ画面契約です。一時変数は通常の対象設定経路に残し、この選択肢へ混在させません。
                choice.copy(
                    children = targetChoices(
                        node,
                        context.copy(role = CommandSettingRole.DESTINATION),
                        player,
                        includeTemporary = false,
                    ),
                )
            } else choice
        }
        GestureSettingScreen.LOCATION -> listOf(
            GestureSettingTreeNode(
                id = "location:position",
                label = labeled(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                    CommandSettingsModel.positionKind(node, CommandSettingRole.TEMPORARY_LOCATION_POSITION)
                        ?.let { positionKindLabel(player, it) },
                ),
                selected = node.temporaryLocationPositionSpec != null,
                children = positionChoices(
                    node,
                    context.copy(role = CommandSettingRole.TEMPORARY_LOCATION_POSITION),
                    player,
                ),
            ),
            GestureSettingTreeNode(
                id = "location:facing",
                label = labeled(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING,
                    CommandSettingsModel.facingSpec(node, CommandSettingRole.TEMPORARY_LOCATION_FACING)
                        ?.kind
                        ?.let { facingKindLabel(player, it) },
                ),
                selected = node.temporaryLocationFacingSpec != null,
                children = facingChoices(
                    node,
                    context.copy(role = CommandSettingRole.TEMPORARY_LOCATION_FACING),
                    player,
                ),
            ),
        )
        GestureSettingScreen.CONDITION_DETAIL -> conditionDetailChoices(node, player).map { choice ->
            when (choice.id) {
                "condition-target" -> choice.copy(
                    selected = CommandSettingsModel.isFieldConfigured(
                        node,
                        "target",
                        CommandSettingRole.NODE_TARGET,
                    ),
                    children = targetChoices(
                        node,
                        context.copy(role = CommandSettingRole.NODE_TARGET),
                        player,
                    ),
                )
                "condition-position" -> choice.copy(
                    selected = CommandSettingsModel.isFieldConfigured(
                        node,
                        "position",
                        CommandSettingRole.CONDITION_POSITION,
                    ),
                    children = positionChoices(
                        node,
                        context.copy(role = CommandSettingRole.CONDITION_POSITION),
                        player,
                    ),
                )
                else -> choice
            }
        }
        else -> settingChoices(node, context, screen, fieldKey, player)
    }

    /**
     * 生の選択肢へドメインの選択方式と値状態を付与します。
     * 子要素も同じ規則で再帰的に装飾し、対象・位置の役割が
     * 入れ替わっても表示側が個別の画面分岐を持たないようにします。
     */
    private fun decorateSettingChoice(
        node: CommandNode,
        context: CommandSettingContext,
        fieldKey: String,
        choice: GestureSettingTreeNode,
        attentionFields: Set<String> = emptySet(),
        parentId: String? = null,
    ): GestureSettingTreeNode {
        val childContext = when (choice.id) {
            "condition-target" -> context.copy(role = CommandSettingRole.NODE_TARGET)
            "condition-position" -> context.copy(role = CommandSettingRole.CONDITION_POSITION)
            "location:position" -> context.copy(role = CommandSettingRole.TEMPORARY_LOCATION_POSITION)
            "location:facing" -> context.copy(role = CommandSettingRole.TEMPORARY_LOCATION_FACING)
            "position:${PositionKind.TARGET.name}" -> context.copy(role = CommandSettingRole.DESTINATION)
            else -> context
        }
        val effectiveContext = when {
            parentId == "condition-target" -> context.copy(role = CommandSettingRole.NODE_TARGET)
            parentId == "condition-position" -> context.copy(role = CommandSettingRole.CONDITION_POSITION)
            parentId == "location:position" -> context.copy(role = CommandSettingRole.TEMPORARY_LOCATION_POSITION)
            parentId == "location:facing" -> context.copy(role = CommandSettingRole.TEMPORARY_LOCATION_FACING)
            parentId == "position:${PositionKind.TARGET.name}" -> context.copy(role = CommandSettingRole.DESTINATION)
            else -> childContext
        }
        val configured = settingChoiceConfigured(node, effectiveContext, fieldKey, choice)
        return choice.copy(
            selectionMode = settingSelectionMode(choice.id),
            valueState = if (configured) {
                GestureSettingValueState.CONFIGURED
            } else {
                GestureSettingValueState.INITIAL
            },
            // 要確認状態は、この選択肢が属する設定タブが実行前検証で指されていれば
            // 付与します。右側カード自身は警告表示を使いませんが、タブ単位の警告文や
            // 子要素への状態投影には同じfieldKey情報を引き続き使用します。
            attention = settingChoiceTabFieldKeys(choice.id, fieldKey, effectiveContext.role)
                .any { it in attentionFields },
            children = choice.children.map {
                decorateSettingChoice(node, effectiveContext, fieldKey, it, attentionFields, choice.id)
            },
        )
    }

    /**
     * 選択肢が属する設定タブ（fieldKey）の集合を返します。
     *
     * 実行前検証はタブのfieldKey単位で要確認を指すため、警告文の対象タブへ
     * 投影します。右側カードの警告表示はこの値を直接使わず、settingChoiceConfiguredと
     * 同じ選択肢IDの分類に倣います。
     */
    private fun settingChoiceTabFieldKeys(
        choiceId: String,
        fieldKey: String,
        role: CommandSettingRole?,
    ): Set<String> = when {
        choiceId.startsWith("target:") || choiceId.startsWith("target-preset:") ||
            choiceId.startsWith("kind:") || choiceId.startsWith("filter:") ->
            setOfNotNull(role?.tabFieldKey ?: "target")
        choiceId.startsWith("position:") || choiceId.startsWith("position-preset:") ||
            choiceId == "position-entity" || choiceId.startsWith("position-entity-preset:") ||
            choiceId.startsWith("myworld-spawn:") ->
            setOfNotNull(role?.tabFieldKey ?: "position")
        choiceId.startsWith("facing:") || choiceId.startsWith("facing-preset:") ||
            choiceId == "facing-entity" || choiceId.startsWith("facing-entity-preset:") ->
            setOfNotNull(role?.tabFieldKey ?: "facing")
        choiceId == "condition-kind" -> setOf("kind")
        choiceId.startsWith("condition-") -> setOf("condition")
        choiceId.startsWith("block:") -> setOf("operation")
        choiceId.startsWith("display:") -> setOf("mode")
        choiceId.startsWith("action:") -> setOf("action")
        choiceId.startsWith("equipmentSlot:") -> setOf("slot")
        choiceId.startsWith("overwrite:") -> setOf("overwrite")
        choiceId.startsWith("tagOperation:") -> setOf("tagOperation")
        choiceId.startsWith("type:") -> setOf("type")
        choiceId.startsWith("operation:") -> setOf("operation")
        choiceId.startsWith("changeMode:") -> setOf("changeMode")
        choiceId.startsWith("shake:") -> setOf("shakeType")
        choiceId.startsWith("soundScope:") -> setOf("soundScope")
        choiceId.startsWith("value:") -> setOf("value")
        // forの参照元・包含判定は、その画面を開いたタブ自身（fieldKey）へ投影します。
        choiceId.startsWith("source:") || choiceId.startsWith("invert:") -> setOf(fieldKey)
        else -> setOf(fieldKey)
    }

    private fun settingSelectionMode(choiceId: String): GestureSettingSelectionMode = when {
        choiceId.startsWith("filter:") -> GestureSettingSelectionMode.MULTIPLE
        choiceId.startsWith("condition-") && choiceId != "condition-kind" -> GestureSettingSelectionMode.MULTIPLE
        else -> GestureSettingSelectionMode.EXCLUSIVE
    }

    private fun settingChoiceConfigured(
        node: CommandNode,
        context: CommandSettingContext,
        fieldKey: String,
        choice: GestureSettingTreeNode,
    ): Boolean {
        val id = choice.id
        return when {
            id.startsWith("target-preset:") -> choice.selected &&
                SettingPresets.matchTargetPreset(CommandSettingsModel.targetSpec(node, context.role))?.name ==
                id.removePrefix("target-preset:") &&
                CommandSettingsModel.isFieldConfigured(node, "target", context.role)
            id.startsWith("position-preset:") -> choice.selected &&
                SettingPresets.matchPositionPreset(CommandSettingsModel.positionSpec(node, context.role))?.name ==
                id.removePrefix("position-preset:") &&
                CommandSettingsModel.positionSpec(node, context.role) != null
            id.startsWith("facing-preset:") -> choice.selected &&
                SettingPresets.matchFacingPreset(CommandSettingsModel.facingSpec(node, context.role))?.name ==
                id.removePrefix("facing-preset:") &&
                CommandSettingsModel.facingSpec(node, context.role) != null
            id.startsWith("position-entity-preset:") -> choice.selected &&
                SettingPresets.matchTargetPreset(CommandSettingsModel.positionSpec(node, context.role)?.target)?.name ==
                id.removePrefix("position-entity-preset:") &&
                CommandSettingsModel.positionSpec(node, context.role) != null
            id.startsWith("facing-entity-preset:") -> choice.selected &&
                SettingPresets.matchTargetPreset(CommandSettingsModel.facingSpec(node, context.role)?.target)?.name ==
                id.removePrefix("facing-entity-preset:") &&
                CommandSettingsModel.facingSpec(node, context.role) != null
            id == "position-entity" -> choice.selected &&
                CommandSettingsModel.positionSpec(node, context.role)?.kind == PositionKind.TARGET
            id == "facing-entity" -> choice.selected &&
                CommandSettingsModel.facingSpec(node, context.role)?.kind == FacingKind.TARGET
            id.startsWith("myworld-spawn:") -> choice.selected
            id.startsWith("target:") -> choice.selected &&
                CommandSettingsModel.targetCategory(CommandSettingsModel.targetSpec(node, context.role)?.kind) ==
                targetCategoryFromChoice(id) &&
                CommandSettingsModel.isFieldConfigured(node, "target", context.role)
            id.startsWith("kind:") -> choice.selected &&
                CommandSettingsModel.isFieldConfigured(node, "target", context.role)
            id.startsWith("filter:") -> CommandSettingsModel.isTargetFilterConfigured(
                node,
                context.role,
                id.removePrefix("filter:"),
            )
            id.startsWith("block:") -> choice.selected &&
                CommandSettingsModel.isFieldConfigured(node, "operation", context.role)
            id.startsWith("position:") -> choice.selected &&
                CommandSettingsModel.positionKind(node, context.role)?.name == id.removePrefix("position:") &&
                CommandSettingsModel.isFieldConfigured(node, "position", context.role)
            id.startsWith("facing:") -> choice.selected &&
                CommandSettingsModel.facingSpec(node, context.role)?.kind?.name == id.removePrefix("facing:") &&
                CommandSettingsModel.isFieldConfigured(node, "facing", context.role)
            id == "location:position" -> node.temporaryLocationPositionSpec != null
            id == "location:facing" -> node.temporaryLocationFacingSpec != null
            id == "condition-target" -> CommandSettingsModel.isFieldConfigured(
                node,
                "target",
                CommandSettingRole.NODE_TARGET,
            )
            id == "condition-position" -> CommandSettingsModel.isFieldConfigured(
                node,
                "position",
                CommandSettingRole.CONDITION_POSITION,
            )
            id.startsWith("condition-control-block-state:") -> {
                val controlState = runCatching {
                    ControlBlockStateKind.valueOf(id.removePrefix("condition-control-block-state:"))
                }.getOrNull()
                controlState != null && controlState in node.selectedControlBlockStates()
            }
            id.startsWith("condition-state") -> CommandSettingsModel.isFieldConfigured(node, "sneaking")
            id.startsWith("condition-variable") -> CommandSettingsModel.isFieldConfigured(node, "variable")
            id.startsWith("condition-operator") -> CommandSettingsModel.isFieldConfigured(node, "operator")
            id.startsWith("condition-value") -> CommandSettingsModel.isFieldConfigured(node, "value")
            id.startsWith("condition-block") -> CommandSettingsModel.isFieldConfigured(node, "block")
            id.startsWith("condition-item-data") -> CommandSettingsModel.isFieldConfigured(node, "itemData")
            id.startsWith("condition-item") -> CommandSettingsModel.isFieldConfigured(node, "item")
            id.startsWith("changeMode:") -> choice.selected &&
                node.string("changeMode", "ASSIGN") == id.removePrefix("changeMode:") &&
                CommandSettingsModel.isFieldConfigured(node, "changeMode")
            id.startsWith("equipmentSlot:") -> choice.selected &&
                node.string("slot", "HAND") == id.removePrefix("equipmentSlot:") &&
                CommandSettingsModel.isFieldConfigured(node, "slot")
            id.startsWith("overwrite:") -> choice.selected &&
                node.boolean("overwrite") == id.removePrefix("overwrite:").toBoolean() &&
                CommandSettingsModel.isFieldConfigured(node, "overwrite")
            id.startsWith("tagOperation:") -> choice.selected &&
                node.string("tagOperation", "add") == id.removePrefix("tagOperation:") &&
                CommandSettingsModel.isFieldConfigured(node, "tagOperation")
            id.startsWith("shake:") -> choice.selected &&
                node.string("shakeType", "positional") == id.removePrefix("shake:") &&
                CommandSettingsModel.isFieldConfigured(node, "shakeType")
            id.startsWith("soundScope:") -> choice.selected &&
                node.string("soundScope", "POSITION") == id.removePrefix("soundScope:") &&
                CommandSettingsModel.isFieldConfigured(node, "soundScope")
            else -> choice.selected && CommandSettingsModel.isFieldConfigured(node, fieldKey, context.role)
        }
    }

    private fun targetCategoryFromChoice(id: String): TargetCategory? = when (id.removePrefix("target:")) {
        TargetCategory.PLAYER.name -> TargetCategory.PLAYER
        TargetCategory.NON_PLAYER_ENTITY.name -> TargetCategory.NON_PLAYER_ENTITY
        TargetCategory.TEMPORARY.name -> TargetCategory.TEMPORARY
        else -> null
    }

    /** 現在の表示経路から、クリック対象が詳細子画面を持つかを判定します。 */
    internal fun hasSettingChoiceChildren(
        state: GestureEditorState,
        player: Player,
        choiceId: String,
    ): Boolean {
        val context = state.settingContext ?: return false
        val screen = state.settingScreen ?: return false
        val fieldKey = state.settingFieldKey ?: return false
        val node = plugin.scripts.load(context.scriptId)?.graph?.nodes?.get(context.nodeId) ?: return false
        return settingTreeNodes(node, context, screen, fieldKey, player)
            .firstNotNullOfOrNull { it.find(choiceId) }
            ?.hasChildren == true
    }

    /**
     * 木の選択状態を再利用し、初回選択と詳細再クリックを区別します。
     *
     * 画面を開いた直後はsettingTreePathが空でも、モデルには既に選択済みの値が
     * 保存されている場合があります。この状態を一回目のクリックとして扱うと、
     * 詳細設定へ二回クリックが必要になるため、現在画面の木を再構築して永続値も
     * 選択状態へ投影します。対象の下位候補（移動先の対象種別など）もfindで同じ
     * 規則に載せます。
     */
    internal fun isSettingChoiceSelected(
        state: GestureEditorState,
        player: Player,
        choiceId: String,
    ): Boolean {
        if (state.settingTreePath?.nodeIds?.lastOrNull() == choiceId) return true
        val context = state.settingContext ?: return false
        val screen = state.settingScreen ?: return false
        val fieldKey = state.settingFieldKey ?: return false
        val node = plugin.scripts.load(context.scriptId)?.graph?.nodes?.get(context.nodeId) ?: return false
        return settingTreeNodes(node, context, screen, fieldKey, player)
            .firstNotNullOfOrNull { it.find(choiceId) }
            ?.selected == true
    }

    private fun addBackSetting(
        player: Player,
        elements: MutableList<GestureGuiElement>,
        visuals: MutableList<GestureGuiVisual>,
        child: Boolean = false,
        centerX: Double = if (child) 0.0 else 0.67,
        width: Double = if (child) CHILD_BACK_WIDTH else 0.66,
    ) {
        // childフラグを引数の既定値にも反映し、戻る操作の位置を親と混同しません。
        addBlock(visuals, "setting-back-bg", centerX, -0.43, width, 0.10, Material.BROWN_CONCRETE, 4)
        addText(visuals, "setting-back-label", centerX, -0.43, 0.0048, 100,
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)))
        elements.add(GestureGuiElement(
            elementId = "lower-setting-back",
            bounds = rect(centerX, -0.43, width, 0.10),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            targetVisualId = "setting-back-bg",
        ))
    }

    private fun settingChoices(
        node: CommandNode,
        context: CommandSettingContext,
        screen: GestureSettingScreen,
        fieldKey: String,
        player: Player,
    ): List<GestureSettingTreeNode> = when (screen) {
        GestureSettingScreen.TARGET -> targetChoices(node, context, player)
        GestureSettingScreen.TARGET_FILTERS -> targetFilterChoices(node, context, player)
        GestureSettingScreen.POSITION -> positionChoices(node, context, player)
        GestureSettingScreen.FACING -> facingChoices(node, context, player)
        // LOCATIONはrawSettingTreeNodesで位置・向きの親木を生成します。
        // ここは網羅性を保ちつつ、親木を単純な択一候補へ誤変換しないため空にします。
        GestureSettingScreen.LOCATION -> emptyList()
        GestureSettingScreen.CONDITION_KIND -> conditionKindChoices(node, player)
        GestureSettingScreen.CONDITION_DETAIL -> conditionDetailChoices(node, player)
        GestureSettingScreen.DISPLAY_MODE -> listOf(
            SettingChoice("display:tellraw", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHAT), node.string("mode", "tellraw") == "tellraw"),
            SettingChoice("display:title", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TITLE), node.string("mode", "tellraw") == "title"),
            SettingChoice("display:subtitle", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SUBTITLE), node.string("mode", "tellraw") == "subtitle"),
            SettingChoice("display:actionbar", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ACTIONBAR), node.string("mode", "tellraw") == "actionbar"),
        )
        GestureSettingScreen.BLOCK_OPERATION -> listOf(
            SettingChoice(
                "block:setblock",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BLOCK_SETBLOCK),
                node.string("operation", "setblock") == "setblock",
            ),
            SettingChoice(
                "block:fill",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BLOCK_FILL),
                node.string("operation", "setblock") == "fill",
            ),
        )
        GestureSettingScreen.ENTITY_ACTION -> listOf(
            SettingChoice("action:ride", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RIDE), node.string("action", "ride") == "ride"),
            SettingChoice("action:dismount", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISMOUNT), node.string("action", "ride") == "dismount"),
            SettingChoice("action:equip", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIP), node.string("action", "ride") == "equip"),
            SettingChoice("action:tag", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SET_TAG), node.string("action", "ride") == "tag"),
        )
        GestureSettingScreen.ENTITY_EQUIPMENT_SLOT -> listOf(
            "HAND" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HAND,
            "OFF_HAND" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_OFF_HAND,
            "HEAD" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HEAD,
            "CHEST" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_CHEST,
            "LEGS" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_LEGS,
            "FEET" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_FEET,
        ).map { (slot, label) ->
            SettingChoice("equipmentSlot:$slot", KcI18n.text(player, label), node.string("slot", "HAND") == slot)
        }
        GestureSettingScreen.ENTITY_OVERWRITE -> listOf(
            SettingChoice("overwrite:true", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED), node.boolean("overwrite")),
            SettingChoice("overwrite:false", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED), !node.boolean("overwrite")),
        )
        GestureSettingScreen.ENTITY_TAG_OPERATION -> listOf(
            SettingChoice("tagOperation:add", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ADD), node.string("tagOperation", "add") == "add"),
            SettingChoice("tagOperation:remove", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REMOVE), node.string("tagOperation", "add") == "remove"),
        )
        GestureSettingScreen.CAMERA_SHAKE_TYPE -> listOf(
            SettingChoice("shake:positional", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SHAKE_POSITIONAL), node.string("shakeType", "positional") == "positional"),
            SettingChoice("shake:rotational", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SHAKE_ROTATIONAL), node.string("shakeType", "positional") == "rotational"),
        )
        GestureSettingScreen.SOUND_SCOPE -> listOf(
            SettingChoice("soundScope:POSITION", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_POSITION), node.string("soundScope", "POSITION") == "POSITION"),
            SettingChoice("soundScope:WORLD", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_WORLD_WIDE), node.string("soundScope", "POSITION") == "WORLD"),
        )
        GestureSettingScreen.SOUND_OPERATION -> listOf(
            SettingChoice("soundOperation:PLAY", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SOUND_PLAY), SoundSettings.operation(node) == SoundOperation.PLAY),
            SettingChoice("soundOperation:STOP", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SOUND_STOP), SoundSettings.operation(node) == SoundOperation.STOP),
        )
        // 再生位置と対象は、対象選択と再生位置選択の2つの掘り下げで構成します。
        // 停止操作では再生位置を使用しないため、対象だけを提示します。
        GestureSettingScreen.SOUND_TARGET_POSITION -> buildList {
            add(
                GestureSettingTreeNode(
                    id = "sound-target",
                    label = labeled(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                        node.targetSpec?.kind?.let { targetKindLabel(player, it) },
                    ),
                    selected = CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET),
                    children = targetChoices(node, context.copy(role = CommandSettingRole.NODE_TARGET), player),
                ),
            )
            if (SoundSettings.operation(node) == SoundOperation.PLAY) {
                add(
                    GestureSettingTreeNode(
                        id = "sound-position",
                        label = labeled(
                            player,
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_POSITION,
                            CommandSettingsModel.positionKind(node, CommandSettingRole.SOUND_POSITION)
                                ?.let { positionKindLabel(player, it) },
                        ),
                        selected = CommandSettingsModel.isFieldConfigured(node, "position", CommandSettingRole.SOUND_POSITION),
                        children = positionChoices(node, context.copy(role = CommandSettingRole.SOUND_POSITION), player),
                    ),
                )
            }
        }
        // 表示設定は、表示方式の択一と対象選択の掘り下げで構成します。
        GestureSettingScreen.PARTICLE_DISPLAY -> listOf(
            SettingChoice("display:POINT", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARTICLE_POINT), ParticleGeometry.displayMode(node) == ParticleDisplayMode.POINT),
            SettingChoice("display:GEOMETRY", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARTICLE_GEOMETRY), ParticleGeometry.displayMode(node) == ParticleDisplayMode.GEOMETRY),
            GestureSettingTreeNode(
                id = "particle-target",
                label = labeled(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                    node.targetSpec?.kind?.let { targetKindLabel(player, it) },
                ),
                selected = CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET),
                children = targetChoices(node, context.copy(role = CommandSettingRole.NODE_TARGET), player),
            ),
        )
        // 図形設定は、形状の択一・数値入力・終端位置の掘り下げで構成します。
        // 数値入力はダイアログで開く葉であり、終端位置はLINE／BOXのときだけ提示します。
        GestureSettingScreen.PARTICLE_GEOMETRY -> buildList {
            addAll(
                listOf(
                    Triple(ParticleShape.LINE, "shape:LINE", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_LINE),
                    Triple(ParticleShape.CIRCLE, "shape:CIRCLE", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_CIRCLE),
                    Triple(ParticleShape.ARC, "shape:ARC", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_ARC),
                    Triple(ParticleShape.BOX, "shape:BOX", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_BOX),
                    Triple(ParticleShape.SPHERE, "shape:SPHERE", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_SPHERE),
                    Triple(ParticleShape.HELIX, "shape:HELIX", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_HELIX),
                ).map { (shape, id, label) ->
                    SettingChoice(id, KcI18n.text(player, label), ParticleGeometry.shape(node) == shape)
                },
            )
            if (ParticleGeometry.shape(node) == ParticleShape.BOX) {
                // 枠線表示は条件の演算子と同じく、押すたびに切り替わる単一選択肢です。
                val wireframe = node.params[ParticleGeometry.PARAM_WIREFRAME]?.toBooleanStrictOrNull() ?: true
                add(
                    SettingChoice(
                        "wireframe:toggle",
                        labeled(
                            player,
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_SETTINGS,
                            KcI18n.text(
                                player,
                                if (wireframe) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED
                                else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED,
                            ),
                        ),
                        wireframe,
                    ),
                )
            }
            add(
                GestureSettingTreeNode(
                    id = "geometry-numbers",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_PARAMETERS),
                    selected = false,
                ),
            )
            val shape = ParticleGeometry.shape(node)
            if (shape == ParticleShape.LINE || shape == ParticleShape.BOX) {
                add(
                    GestureSettingTreeNode(
                        id = "geometry-end",
                        label = labeled(
                            player,
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                            CommandSettingsModel.positionKind(node, CommandSettingRole.PARTICLE_END)
                                ?.let { positionKindLabel(player, it) },
                        ),
                        selected = CommandSettingsModel.isFieldConfigured(node, "position", CommandSettingRole.PARTICLE_END),
                        children = positionChoices(node, context.copy(role = CommandSettingRole.PARTICLE_END), player),
                    ),
                )
            }
        }
        GestureSettingScreen.VARIABLE_TYPE -> if (node.type == CommandType.TEMP_SET) {
            TemporaryVariableType.entries.map { type ->
                val label = when (type) {
                    TemporaryVariableType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMBER
                    TemporaryVariableType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT
                    TemporaryVariableType.LOCATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION
                    TemporaryVariableType.ITEM -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM
                    TemporaryVariableType.BLOCK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK
                    TemporaryVariableType.ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
                    TemporaryVariableType.SOUND -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND
                    TemporaryVariableType.EFFECT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EFFECT
                }
                SettingChoice(
                    "type:${type.name}",
                    KcI18n.text(player, label),
                    TemporaryVariableType.parse(node.string("tempType", TemporaryVariableType.NUMBER.name)) == type,
                )
            }
        } else listOf(
            SettingChoice("type:NUMBER", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMBER), node.string("type", VariableType.NUMBER.name) == VariableType.NUMBER.name),
            SettingChoice("type:STRING", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT), node.string("type", VariableType.NUMBER.name) == VariableType.STRING.name),
        )
        GestureSettingScreen.VARIABLE_OPERATION -> {
            val type = runCatching { VariableType.valueOf(node.string("type", VariableType.NUMBER.name)) }
                .getOrDefault(VariableType.NUMBER)
            CommandSettingsModel.allowedVariableOperations(type).map { operation ->
                SettingChoice(
                    "operation:${operation.name}",
                    KcI18n.text(player, operationLabel(operation)),
                    node.string("operation", operation.name) == operation.name,
                )
            }
        }
        GestureSettingScreen.VARIABLE_CHANGE_MODE -> {
            // 文字列型では計算式を適用できないため、代入のみを提供します。
            val isString = node.string("type", VariableType.NUMBER.name) == VariableType.STRING.name
            if (isString) {
                listOf(SettingChoice("changeMode:ASSIGN", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ASSIGN), true))
            } else {
                listOf(
                    SettingChoice("changeMode:ASSIGN", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ASSIGN), node.string("changeMode", "ASSIGN") == "ASSIGN"),
                    SettingChoice("changeMode:CALCULATE", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CALCULATE), node.string("changeMode", "ASSIGN") == "CALCULATE"),
                )
            }
        }
        GestureSettingScreen.VARIABLE_VALUE -> buildList {
            add(SettingChoice("value:direct", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DIRECT_VALUE), !node.string("value").startsWith("$")))
            val script = plugin.scripts.load(context.scriptId)
            val insideFor = script != null && node.string("type", VariableType.NUMBER.name) == VariableType.NUMBER.name &&
                GraphEditor.isInsideFor(script.graph, node.id, GraphEditor.Edge.NEXT)
            if (insideFor) {
                add(
                    SettingChoice(
                        "value:count",
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT),
                        node.string("value") == "\${CURRENT_LOOP_COUNT}",
                    ),
                )
            }
        }
        GestureSettingScreen.CONDITION_INVERSION -> {
            listOf(
                SettingChoice("invert:true", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_ON), node.boolean(fieldKey, false)),
                SettingChoice("invert:false", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_OFF), !node.boolean(fieldKey, false)),
            )
        }
    }

    /** 選択肢ラベルを「ラベル 現在値」形式へ結合します。未設定はGUI共通の未設定文言を使います。 */
    private fun labeled(player: Player, key: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>, value: String?): String =
        "${KcI18n.text(player, key)} ${value ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)}"

    /** 距離範囲は上下限を同じカードにまとめ、片側だけの既存データも欠落させません。 */
    private fun displayDistance(minimum: Double?, maximum: Double?): String? {
        if (minimum == null && maximum == null) return null
        fun format(value: Double?): String = value?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }.orEmpty()
        return "${format(minimum)}..${format(maximum)}"
    }

    /** 対象範囲は3軸を一つの設定項目として現在値を表示します。 */
    private fun displayTargetRange(player: Player, dx: Double?, dy: Double?, dz: Double?): String? {
        if (dx == null && dy == null && dz == null) return null
        fun format(value: Double?): String = value?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }.orEmpty()
        return listOf(
            "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX)}=${format(dx).ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }}",
            "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY)}=${format(dy).ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }}",
            "${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ)}=${format(dz).ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }}",
        ).joinToString(" / ")
    }

    /**
     * issue #29 の対象親画面です。
     *
     * 頻用する4プリセットを先頭に直接提示し、従来の汎用設定（一時変数・
     * カテゴリ分類と詳細フィルター）は下部導線へ残します。プリセット選択時は
     * 以前の詳細フィルター等を引き継がず正規化し、詳細で外れた場合は
     * 選択済み表示にしません。
     */
    private fun targetChoices(
        node: CommandNode,
        context: CommandSettingContext,
        player: Player,
        includeTemporary: Boolean = true,
    ): List<SettingChoice> {
        val current = CommandSettingsModel.targetSpec(node, context.role)
        val presets = listOf(
            SettingPresets.TargetPreset.NEARBY_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS,
            SettingPresets.TargetPreset.MYWORLD_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS,
            SettingPresets.TargetPreset.NEAREST_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER,
            SettingPresets.TargetPreset.NEARBY_ENTITIES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES,
        ).map { (preset, label) ->
            SettingChoice(
                id = "target-preset:${preset.name}",
                label = KcI18n.text(player, label),
                selected = SettingPresets.matchTargetPreset(current) == preset &&
                    CommandSettingsModel.isFieldConfigured(node, "target", context.role),
            )
        }
        val categories = buildList {
            add(TargetCategory.PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER)
            add(TargetCategory.NON_PLAYER_ENTITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY)
            if (includeTemporary) {
                add(TargetCategory.TEMPORARY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE)
            }
        }
        val currentKind = current?.kind
        val detailed = categories.map { (category, label) ->
            val selected = CommandSettingsModel.targetCategoryMatches(currentKind, category) &&
                CommandSettingsModel.isFieldConfigured(node, "target", context.role) &&
                SettingPresets.matchTargetPreset(current) == null
            val effectiveKind = if (selected && currentKind != null) currentKind
            else CommandSettingsModel.defaultTargetKind(category)
            SettingChoice(
                id = "target:${category.name}",
                label = KcI18n.text(player, label),
                selected = selected,
                children = if (CommandSettingsModel.targetSupportsDetailedFilters(effectiveKind)) {
                    targetFilterChoices(node, context, player, kindOverride = effectiveKind)
                } else emptyList(),
            )
        }
        return presets + detailed
    }

    /**
     * 移動先の「他のエンティティ」を選んだときだけ、対象分類を右下へ並べます。
     * 座標方式の選択肢と同じ親画面へ置くことで、方式を選ぶ→対象種別を選ぶ→
     * 同じ種別を再クリックして詳細、という導線を保ちます。既存の4スロットを
     * 前半2スロット・後半2スロットへまとめ、2枚のカードを同じ横幅で配置します。
     */
    private fun addLowerRightTargetChoiceNodes(
        choices: List<GestureSettingTreeNode>,
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        fieldKey: String,
        suppressHighlight: Boolean = false,
    ) {
        val visibleChoices = choices.take(GestureTargetChoiceLayoutPolicy.CHOICE_COUNT)
        GestureTargetChoiceLayoutPolicy.slots(visibleChoices.size)
            .zip(visibleChoices)
            .forEachIndexed { index, (slot, choice) ->
                val cx = slot.centerX
                val cy = POSITION_TARGET_CHOICE_Y
                val bgId = "position-target-choice-bg-$index"
                addBlock(
                    visuals,
                    bgId,
                    cx,
                    cy,
                    slot.width,
                    POSITION_TARGET_CHOICE_HEIGHT,
                    settingChoiceMaterial(choice),
                    4,
                    // 右側へ表示する対象分類ボタンもタブ以外の設定ボタンです。
                    // 選択中は完了状態にかかわらず白い外周枠、未完了警告はタブ文字へ残します。
                    outlineMaterial = if (suppressHighlight) {
                        null
                    } else {
                        GestureSettingVisualPolicy.nonTabOutlineMaterial(choice.selected)
                    },
                )
                addText(
                    visuals,
                    "position-target-choice-label-$index",
                    cx,
                    cy - 0.02,
                    0.0055,
                    90,
                    Component.text(choice.label, GestureSettingVisualPolicy.settingChoiceTextColor(choice)),
                )
                val hoverDescription = DisabledChoiceVisualPolicy.hoverText(
                    enabled = choice.enabled,
                    normal = choice.description.takeIf(String::isNotBlank)
                        ?: choiceDescription(player, choice, fieldKey),
                    disabled = choice.disabledHoverText,
                )
                elements += GestureGuiElement(
                    // 共通ハンドラがtarget:<category>を解釈するため、elementIdの接頭辞は
                    // 通常の設定カードと統一します。
                    elementId = "lower-setting-choice:${choice.id}",
                    bounds = rect(cx, cy, slot.width, POSITION_TARGET_CHOICE_HEIGHT),
                    acceptedGestures = if (choice.enabled) GestureGuiClickPolicy.CLICK else emptySet(),
                    gestureGuard = if (choice.enabled) null else { _, _ -> false },
                    targetVisualId = bgId,
                    // ホバー中はテクスチャを変えず説明文だけを示します。色付けは実際に
                    // 操作が行われた場合（選択・警告等）に限ります。
                    hoverText = hoverDescription?.let {
                        parallelButtonHover(
                            text = it,
                            x = cx,
                            y = cy,
                            row = 0,
                            rowCount = 1,
                            height = POSITION_TARGET_CHOICE_HEIGHT,
                            descriptionX = HOVER_SLOT_X,
                            descriptionY = HOVER_SLOT_Y,
                            replacesDescription = false,
                            parallel = visibleChoices.size > 1,
                        )
                    },
                )
            }
    }

    internal fun targetFilterChoices(
        node: CommandNode,
        context: CommandSettingContext,
        player: Player,
        kindOverride: TargetKind? = null,
    ): List<SettingChoice> {
        val currentSpec = CommandSettingsModel.targetSpec(node, context.role)
            ?: me.awabi2048.kantancommander.model.TargetSpec(kindOverride ?: TargetKind.NEAREST_ENTITY)
        val spec = kindOverride?.let { currentSpec.copy(kind = it) } ?: currentSpec
        fun value(parameter: String): String? = when (parameter) {
            "sort" -> KcI18n.text(player, when (spec.sort) {
                TargetSort.NEAREST -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SORT_NEAREST
                TargetSort.FURTHEST -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SORT_FURTHEST
                TargetSort.RANDOM -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SORT_RANDOM
            })
            else -> spec.let {
                when (parameter) {
                    "gameMode" -> it.gameMode
                    "entityType" -> it.entityType
                    "distance" -> displayDistance(it.minimumDistance, it.maximumDistance)
                    "range" -> displayTargetRange(player, it.dx, it.dy, it.dz)
                    "limit" -> it.limit?.toString()
                    "tag" -> it.tag
                    else -> it.name
                }
            }
        }
        // Targetの上位画面で旧細分類（最も近い／全員／固定エンティティ等）は
        // 3カテゴリへ統一して削除しました。ここでは詳細条件だけを残し、削除済み
        // の選択肢を詳細画面へ再掲しないようにします。距離・上限数・名前等の既存
        // の詳細設定は変更しません。
        val filterChoices = listOf(
            "entityType" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_TYPE,
            "distance" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE,
            "range" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_RANGE,
            "limit" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LIMIT,
            "sort" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SORT,
            "gameMode" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GAME_MODE,
            "tag" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG,
            "name" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME,
        )
            .filter { (id, _) -> CommandSettingsModel.targetFilterApplies(spec.kind, id) }
            .map { (id, label) -> SettingChoice("filter:$id", labeled(player, label, value(id))) }
        return filterChoices
    }

    /**
     * issue #29 の位置親画面です。
     *
     * 頻用する5プリセットを先頭に直接提示し、汎用設定（一時変数・
     * エンティティ由来位置）は下部導線へ残します。単一限定の役割では
     * 対象由来位置を無効化し、理由を表示します。
     */
    private fun positionChoices(node: CommandNode, context: CommandSettingContext, player: Player, inChild: Boolean = false): List<SettingChoice> {
        val destination = context.role == CommandSettingRole.DESTINATION
        val current = CommandSettingsModel.positionSpec(node, context.role)
        if (destination) {
            val choices = listOf(
                PositionKind.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES_SET,
                PositionKind.TARGET to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_OTHER_ENTITY,
                PositionKind.TEMPORARY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE,
            )
            return choices.map { (kind, label) ->
                SettingChoice(
                    id = "position:${kind.name}",
                    label = KcI18n.text(player, label),
                    selected = current?.kind == kind,
                    enabled = CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(node, context.role, kind),
                    disabledHoverText = if (kind == PositionKind.DISK) {
                        CommandSettingAvailabilityPolicy.CONTROL_BLOCK_POSITION_DISABLED_HOVER
                    } else null,
                )
            }
        }
        val matchedPreset = SettingPresets.matchPositionPreset(current)
        val presets = listOf(
            SettingPresets.PositionPreset.CONTROL_BLOCK to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION,
            SettingPresets.PositionPreset.NEAREST_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER,
            SettingPresets.PositionPreset.NEARBY_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS,
            SettingPresets.PositionPreset.MYWORLD_SPAWN to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN,
            SettingPresets.PositionPreset.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES,
        ).map { (preset, label) ->
            val isMultiPreset = preset == SettingPresets.PositionPreset.NEAREST_PLAYER ||
                preset == SettingPresets.PositionPreset.NEARBY_PLAYERS
            val multiEnabled = !isMultiPreset ||
                CommandSettingAvailabilityPolicy.isEmbeddedTargetPositionEnabled(context.role)
            SettingChoice(
                id = "position-preset:${preset.name}",
                label = KcI18n.text(player, label),
                selected = matchedPreset == preset,
                enabled = multiEnabled &&
                    CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(
                        node,
                        context.role,
                        presetKind(preset),
                    ),
                disabledHoverText = when {
                    !multiEnabled -> CommandSettingAvailabilityPolicy.MULTI_POSITION_DISABLED_HOVER
                    preset == SettingPresets.PositionPreset.CONTROL_BLOCK -> CommandSettingAvailabilityPolicy.CONTROL_BLOCK_POSITION_DISABLED_HOVER
                    else -> null
                },
                children = if (preset == SettingPresets.PositionPreset.MYWORLD_SPAWN) {
                    myworldSpawnChoices(current?.myworldSpawn, player)
                } else emptyList(),
            )
        }
        val entityEnabled = CommandSettingAvailabilityPolicy.isEmbeddedTargetPositionEnabled(context.role)
        val entityEntry = SettingChoice(
            id = "position-entity",
            label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION),
            selected = current?.kind == PositionKind.TARGET && matchedPreset == null,
            enabled = entityEnabled,
            disabledHoverText = if (entityEnabled) null
            else CommandSettingAvailabilityPolicy.MULTI_POSITION_DISABLED_HOVER,
            children = embeddedTargetPresetChoices(current?.target, player, "position-entity-preset"),
        )
        // 子画面では導線自体を隠し、内包プリセットの選択に集中させます。
        val bottomEntries = (if (inChild) emptyList() else listOf(entityEntry)) + listOf(
            SettingChoice(
                id = "position:${PositionKind.TEMPORARY.name}",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE),
                selected = current?.kind == PositionKind.TEMPORARY,
            ),
        )
        return presets + bottomEntries
    }

    /** プリセットが表す位置種別です。選択可否の既存判定へ渡します。 */
    private fun presetKind(preset: SettingPresets.PositionPreset): PositionKind = when (preset) {
        SettingPresets.PositionPreset.CONTROL_BLOCK -> PositionKind.DISK
        SettingPresets.PositionPreset.NEAREST_PLAYER,
        SettingPresets.PositionPreset.NEARBY_PLAYERS,
        -> PositionKind.TARGET
        SettingPresets.PositionPreset.MYWORLD_SPAWN -> PositionKind.MYWORLD_SPAWN
        SettingPresets.PositionPreset.COORDINATES -> PositionKind.COORDINATES
    }

    /** issue #29 のスポーン種別2択です。旧単一種別はメンバー用として表示します。 */
    private fun myworldSpawnChoices(
        current: me.awabi2048.kantancommander.model.MyworldSpawnKind?,
        player: Player,
    ): List<SettingChoice> {
        val resolved = current ?: me.awabi2048.kantancommander.model.MyworldSpawnKind.MEMBER
        return listOf(
            me.awabi2048.kantancommander.model.MyworldSpawnKind.MEMBER to
                com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_MEMBER,
            me.awabi2048.kantancommander.model.MyworldSpawnKind.GUEST to
                com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_GUEST,
        ).map { (kind, label) ->
            SettingChoice(
                id = "myworld-spawn:${kind.name}",
                label = KcI18n.text(player, label),
                selected = resolved == kind,
            )
        }
    }

    private fun embeddedTargetPresetChoices(
        current: TargetSpec?,
        player: Player,
        prefix: String,
    ): List<SettingChoice> {
        val matched = SettingPresets.matchTargetPreset(current)
        return listOf(
            SettingPresets.TargetPreset.NEARBY_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS,
            SettingPresets.TargetPreset.MYWORLD_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS,
            SettingPresets.TargetPreset.NEAREST_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER,
            SettingPresets.TargetPreset.NEARBY_ENTITIES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES,
        ).map { (preset, label) ->
            SettingChoice(
                id = "$prefix:${preset.name}",
                label = KcI18n.text(player, label),
                selected = matched == preset,
            )
        }
    }

    /**
     * issue #29 の向き親画面です。
     *
     * 頻用する4プリセットを先頭に直接提示し、汎用設定（一時変数・
     * エンティティ指定）は下部導線へ残します。既存の「現在の向き」は
     * 内部表現として残しますが、新規プリセットには表示しません。
     * 旧データで設定済みの場合だけ従来選択肢として表示します。
     */
    private fun facingChoices(node: CommandNode, context: CommandSettingContext, player: Player, inChild: Boolean = false): List<SettingChoice> {
        val current = CommandSettingsModel.facingSpec(node, context.role)
        val matchedPreset = SettingPresets.matchFacingPreset(current)
        val presets = listOf(
            SettingPresets.FacingPreset.NEAREST_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER,
            SettingPresets.FacingPreset.MYWORLD_SPAWN to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN,
            SettingPresets.FacingPreset.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES,
            SettingPresets.FacingPreset.NUMERIC to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMERIC,
        ).map { (preset, label) ->
            SettingChoice(
                id = "facing-preset:${preset.name}",
                label = KcI18n.text(player, label),
                selected = matchedPreset == preset,
                children = if (preset == SettingPresets.FacingPreset.MYWORLD_SPAWN) {
                    myworldSpawnChoices(current?.myworldSpawn, player)
                } else emptyList(),
            )
        }
        val legacy = if (current?.kind == FacingKind.CAPTURED) {
            listOf(
                SettingChoice(
                    id = "facing:${FacingKind.CAPTURED.name}",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_FACING),
                    selected = true,
                ),
            )
        } else emptyList()
        return presets + legacy + (if (inChild) emptyList() else listOf(
            SettingChoice(
                id = "facing-entity",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET),
                selected = current?.kind == FacingKind.TARGET && matchedPreset == null,
                children = embeddedTargetPresetChoices(current?.target, player, "facing-entity-preset"),
            ),
        )) + listOf(
            SettingChoice(
                id = "facing:${FacingKind.TEMPORARY.name}",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE),
                selected = current?.kind == FacingKind.TEMPORARY,
            ),
        )
    }

    private fun conditionKindChoices(node: CommandNode, player: Player): List<SettingChoice> =
        ConditionKind.entries.map { kind ->
            SettingChoice("condition-kind:${kind.name}", KcI18n.text(player, kind.key), node.string("kind") == kind.name)
        }

    private fun conditionDetailChoices(node: CommandNode, player: Player): List<SettingChoice> {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrDefault(ConditionKind.TARGET_EXISTS)
        fun label(key: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>, value: String?): String =
            labeled(player, key, value)
        return when (kind) {
            ConditionKind.TARGET_EXISTS -> listOf(
                SettingChoice(
                    "condition-target",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                    node.targetSpec != null,
                ),
            )
            ConditionKind.PLAYER_STATE -> listOf(
                SettingChoice("condition-target", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET), node.targetSpec != null),
                SettingChoice(
                    "condition-state",
                    label(
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_STATE,
                        when (node.string("sneaking")) {
                            "true" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SNEAKING)
                            "false" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ON_GROUND)
                            else -> null
                        },
                    ),
                ),
                SettingChoice("condition-item", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM_CONDITION, node.string("item"))),
            )
            ConditionKind.VARIABLE_STATE -> listOf(
                SettingChoice("condition-variable", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, node.string("variable"))),
                SettingChoice("condition-operator", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATOR, node.string("operator", "=="))),
                SettingChoice("condition-value", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, node.string("value", "0"))),
            )
            ConditionKind.BLOCK_STATE -> listOf(
                SettingChoice("condition-position", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONDITION_POSITION), node.conditionPositionSpec != null),
                SettingChoice("condition-block", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK, node.string("block", "minecraft:air"))),
            )
            // 制御ブロック自身の状態は、今後の項目追加を前提に複数選択の木として
            // 表示します。選択済み集合の評価は実行モデル側でANDへ集約します。
            ConditionKind.CONTROL_BLOCK_STATE -> ControlBlockStateKind.entries.map { state ->
                SettingChoice(
                    "condition-control-block-state:${state.name}",
                    KcI18n.text(player, state.key),
                    state in node.selectedControlBlockStates(),
                )
            }
        }
    }

    private fun operationLabel(operation: VariableOperation): com.awabi2048.ccsystem.api.localization.LocalizationKey<String> = when (operation) {
        VariableOperation.DEFINE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DEFINE
        VariableOperation.CHANGE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHANGE
    }

    private fun settingCurrentValue(
        node: CommandNode,
        context: CommandSettingContext,
        screen: GestureSettingScreen,
        fieldKey: String,
        player: Player,
    ): String = when (screen) {
        // enum名を直接見せず、インベントリGUIと同じ日本語表示へ統一します。
        GestureSettingScreen.TARGET -> CommandSettingsModel.targetSpec(node, context.role)?.kind
            ?.let { targetKindLabel(player, it) } ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        GestureSettingScreen.POSITION -> CommandSettingsModel.positionKind(node, context.role)
            ?.let { positionKindLabel(player, it) } ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        GestureSettingScreen.FACING -> CommandSettingsModel.facingSpec(node, context.role)?.kind
            ?.let { facingKindLabel(player, it) } ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        GestureSettingScreen.CONDITION_INVERSION -> {
            KcI18n.text(
                player,
                if (node.boolean(fieldKey, false)) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_ON
                } else {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_OFF
                },
            )
        }
        else -> node.string(fieldKey).ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }
    }

    private fun targetKindLabel(player: Player, kind: TargetKind): String = KcI18n.text(player, when (kind) {
        TargetKind.NEAREST_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER
        TargetKind.NEARBY_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS
        TargetKind.ALL_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS
        TargetKind.RANDOM_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RANDOM_PLAYER
        TargetKind.NEAREST_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY
        TargetKind.NEARBY_ENTITIES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES
        TargetKind.FIXED_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
        TargetKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
    })

    private fun positionKindLabel(player: Player, kind: PositionKind): String = KcI18n.text(player, when (kind) {
        PositionKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAPTURED_POSITION
        PositionKind.DISK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION
        PositionKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION
        PositionKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
        PositionKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN
        PositionKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES
    })

    private fun facingKindLabel(player: Player, kind: FacingKind): String = KcI18n.text(player, when (kind) {
        FacingKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_FACING
        FacingKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET
        FacingKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
        FacingKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES
        FacingKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN
        FacingKind.ROTATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMERIC
    })


    private fun addTestToggle(
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        player: Player,
        id: String,
        centerY: Double,
        label: String,
        enabled: Boolean,
    ) {
        val width = 1.55
        addBlock(
            visuals,
            "$id-bg",
            0.0,
            centerY,
            width,
            0.12,
            // OFF時は画面背景と同化しないよう、薄灰色のコンクリートで明示します。
            if (enabled) Material.LIME_CONCRETE else Material.LIGHT_GRAY_CONCRETE,
            4,
        )
        val value = KcI18n.text(
            player,
            if (enabled) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_ON
            else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_OFF,
        )
        val text = Component.text(label, NamedTextColor.WHITE)
            .append(Component.text("  ", NamedTextColor.GRAY))
            .append(Component.text(value, if (enabled) NamedTextColor.GREEN else NamedTextColor.GRAY))
        addText(visuals, "$id-label", 0.0, centerY, 0.0055, 230, text)
        elements.add(GestureGuiElement(
            elementId = id,
            bounds = rect(0.0, centerY, width, 0.12),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            targetVisualId = "$id-bg",
        ))
    }



    /**
     * 実行結果・実行中ステータスの項目行です。項目と値を半角スペース区切りで1行にまとめ、
     * 中央揃えのTextDisplayへ出します。ラベルは灰色、値は指定色で分けます。
     */
    private fun addTestResultRow(
        visuals: MutableList<GestureGuiVisual>,
        id: String,
        centerY: Double,
        label: String,
        value: String,
        valueColor: NamedTextColor,
    ) {
        addText(
            visuals,
            id,
            0.0,
            centerY,
            0.005,
            230,
            Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(Component.text(value, valueColor)),
            alignment = GestureGuiTextAlignment.CENTER,
        )
    }

    private fun formatTestElapsed(ticks: Long): String {
        return TestExecutionTimeFormatter.formatTicks(ticks)
    }

    /** 実行中の経過表示用の秒単位の書式です。最終結果には使いません。 */
    private fun formatTestElapsedSeconds(ticks: Long): String {
        return TestExecutionTimeFormatter.formatTicksSeconds(ticks)
    }


    /**
     * PICKER の宣言実装です。旧 `buildPicker` と同じ分類・候補算出・座標・要素 ID・
     * ホバー行位置規則を用い、既存の画面定義・パネル・操作振分け（`view()`）を利用します。
     */
    private fun buildPickerNew(state: GestureEditorState, player: Player): GestureGuiView {
        val categories = CommandCategory.entries
        val insertionTarget = state.pendingInsertion
        val selectedCategory = categories[state.pickerCategory.coerceIn(0, categories.lastIndex)]
        val script = plugin.scripts.load(state.scriptId)
        val mergeConditionId = state.pendingInsertion?.mergeConditionId
        val mergeAvailable = script?.let {
            GraphEditor.canAppendMerge(it.graph, mergeConditionId, insertionTarget?.continuationId)
        } == true
        val insideForBody = script?.graph?.let {
            GraphEditor.isInsideFor(
                it,
                insertionTarget?.sourceId,
                insertionTarget?.edge ?: GraphEditor.Edge.ENTRY,
            )
        } == true
        val types = CommandPickerTypePolicy.types(
            category = selectedCategory,
            mergeAvailable = mergeAvailable,
            insideForBody = insideForBody,
        )
        val pageCount = GestureCommandPickerLayoutPolicy.pageCount(types.size)
        val page = state.pickerPage.coerceIn(0, pageCount - 1)
        val pageTypes = types
            .drop(page * GestureCommandPickerLayoutPolicy.PAGE_SIZE)
            .take(GestureCommandPickerLayoutPolicy.PAGE_SIZE)
        val rowCount = GestureCommandPickerLayoutPolicy.rowCount(pageTypes.size)
        val parallel = pageTypes.size > 1
        val data = PickerData(
            categories = categories.map { PickerCategory(KcI18n.text(player, it.labelKey), it == selectedCategory) },
            tabBackgrounds = categories.map {
                Bukkit.createBlockData(GestureSettingVisualPolicy.tabMaterial(it == selectedCategory))
            },
            description = Component.text(
                KcI18n.list(player, selectedCategory.descriptionKey)
                    .filter(String::isNotBlank)
                    .joinToString(" "),
                NamedTextColor.GRAY,
            ),
            cards = pageTypes.mapIndexed { index, type ->
                val column = index % GestureCommandPickerLayoutPolicy.COLUMNS
                val row = index / GestureCommandPickerLayoutPolicy.COLUMNS
                val cx = GestureCommandPickerLayoutPolicy.columnX(column)
                val cy = GestureCommandPickerLayoutPolicy.rowY(row)
                val hoverText = KcI18n.list(player, type.descriptionKey)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                val hover = parallelButtonHover(
                    hoverText,
                    cx,
                    cy,
                    row,
                    rowCount,
                    GestureCommandPickerLayoutPolicy.CARD_HEIGHT,
                    HOVER_SLOT_X,
                    PICKER_HOVER_SLOT_Y,
                    replacesDescription = false,
                    parallel = parallel,
                )
                PickerCard(type.name, KcI18n.text(player, type.key), hoverText, hover.x, hover.y)
            },
            cardBackgrounds = pageTypes.map { Bukkit.createBlockData(Material.CYAN_TERRACOTTA) },
            pager = if (pageCount > 1) PickerPager(
                arrows = listOf(page - 1 to "◀", page + 1 to "▶").map { (targetPage, glyph) ->
                    val available = targetPage in 0 until pageCount
                    PickerArrow(
                        targetPage = targetPage,
                        glyph = Component.text(glyph).color(
                            if (available) NamedTextColor.WHITE else NamedTextColor.GRAY,
                        ),
                        background = Bukkit.createBlockData(
                            if (available) Material.CYAN_CONCRETE else DisabledGuiVisualPolicy.material,
                        ),
                        available = available,
                    )
                },
                status = Component.text("${page + 1}/$pageCount"),
            ) else null,
            closeLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_CLOSE)),
            closeBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
        )
        val compiled = KantanLayoutBridge.compile(
            DeclarativePicker.document(data),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][DeclarativePicker] $diagnostics")
            },
        )
        return view(GestureLowerMode.PICKER, compiled.elements, compiled.visuals)
    }


    /**
     * CONFIRM の宣言実装です。旧 `buildConfirm` と同じ文言・座標・要素 ID を用い、
     * 既存の画面定義・パネル・子画面縮尺・操作振分け（`view()`）をそのまま利用します。
     */
    private fun buildConfirmNew(state: GestureEditorState, player: Player): GestureGuiView {
        val overwrite = state.confirmKind != GestureConfirmKind.DELETE
        val texts = ConfirmTexts(
            title = Component.text(KcI18n.text(player, when (state.confirmKind) {
                GestureConfirmKind.ITEM_OVERWRITE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_ITEM_OVERWRITE_TITLE
                GestureConfirmKind.BLOCK_OVERWRITE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_BLOCK_OVERWRITE_TITLE
                GestureConfirmKind.FIREWORK_OVERWRITE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_ITEM_OVERWRITE_TITLE
                GestureConfirmKind.DELETE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_DELETE_TITLE
            })),
            warn = Component.text(
                KcI18n.text(player, when (state.confirmKind) {
                    GestureConfirmKind.ITEM_OVERWRITE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_ITEM_OVERWRITE_WARN
                    GestureConfirmKind.BLOCK_OVERWRITE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_BLOCK_OVERWRITE_WARN
                    GestureConfirmKind.FIREWORK_OVERWRITE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_ITEM_OVERWRITE_WARN
                    GestureConfirmKind.DELETE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_DELETE_WARN
                }),
                NamedTextColor.GRAY,
            ),
            yes = Component.text(KcI18n.text(
                player,
                if (overwrite) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_OVERWRITE_YES
                else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE_CONFIRM_EXECUTE,
            )),
            no = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE_CONFIRM_CANCEL)),
        )
        val compiled = KantanLayoutBridge.compile(
            DeclarativeConfirm.document(
                texts,
                Bukkit.createBlockData(DeclarativeConfirm.YES_BACKGROUND_MATERIAL),
                Bukkit.createBlockData(DeclarativeConfirm.NO_BACKGROUND_MATERIAL),
            ),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.warning("[KantanCommander][DeclarativeConfirm] $diagnostics")
            },
        )
        return view(GestureLowerMode.CONFIRM, compiled.elements, compiled.visuals, child = true)
    }

    private fun view(
        mode: GestureLowerMode,
        elements: List<GestureGuiElement>,
        visuals: List<GestureGuiVisual>,
        child: Boolean = false,
    ): GestureGuiView = GestureGuiView(
        GestureGuiScreenDefinition(
            when {
                mode == GestureLowerMode.CONFIRM -> CONFIRM_SCREEN_ID
                mode == GestureLowerMode.TEST_CONFIRM -> TEST_CONFIRM_SCREEN_ID
                child -> SETTING_CHILD_SCREEN_ID
                else -> LOWER_SCREEN_ID
            },
            elements,
            access = screenAccess,
            accessPolicy = screenAccessPolicy,
            visibilityPolicy = screenVisibilityPolicy,
        ),
        visuals,
        panel = GestureGuiPanel(
            width = if (mode == GestureLowerMode.CONFIRM || child) {
                GestureEditorLayout.LOWER_W * SETTING_CHILD_SCALE
            } else {
                GestureEditorLayout.LOWER_W
            },
            height = if (mode == GestureLowerMode.CONFIRM || child) {
                GestureEditorLayout.LOWER_H * SETTING_CHILD_SCALE
            } else {
                GestureEditorLayout.LOWER_H
            },
            backgroundMaterial = Material.GRAY_CONCRETE,
            frameMaterial = Material.LIGHT_GRAY_CONCRETE,
        ),
        onAction = onAction,
    ).let { view -> if (child) scaleChildView(view) else view }

    /** 子画面のパネル寸法と内容座標を同じ縮尺で変換し、端の要素を欠落させません。宣言実装と共有します。 */
    internal fun scaleChildView(view: GestureGuiView): GestureGuiView {
        val scaledVisuals = view.visuals.map { visual ->
            when (visual) {
                is GestureGuiVisual.Block -> visual.copy(
                    x = visual.x * SETTING_CHILD_SCALE,
                    y = visual.y * SETTING_CHILD_SCALE,
                    width = visual.width * SETTING_CHILD_SCALE,
                    height = visual.height * SETTING_CHILD_SCALE,
                )
                is GestureGuiVisual.Text -> visual.copy(
                    x = visual.x * SETTING_CHILD_SCALE,
                    y = visual.y * SETTING_CHILD_SCALE,
                    size = visual.size * SETTING_CHILD_SCALE,
                )
                is GestureGuiVisual.Item -> visual.copy(
                    x = visual.x * SETTING_CHILD_SCALE,
                    y = visual.y * SETTING_CHILD_SCALE,
                    scale = visual.scale * SETTING_CHILD_SCALE,
                )
            }
        }
        val scaledElements = view.definition.elements.map { element ->
            val hover = element.hoverText
            element.copy(
                bounds = scaleBounds(element.bounds, SETTING_CHILD_SCALE),
                hoverText = hover?.copy(
                    x = hover.x * SETTING_CHILD_SCALE,
                    y = hover.y * SETTING_CHILD_SCALE,
                    size = hover.size * SETTING_CHILD_SCALE,
                ),
            )
        }
        return view.copy(
            definition = view.definition.copy(elements = scaledElements),
            visuals = scaledVisuals,
        )
    }

    private fun addPager(
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        id: String,
        page: Int,
        pageCount: Int,
        centerX: Double,
        centerY: Double,
    ) {
        // 利用できない方向の矢印も灰色で常時表示し、ページングの存在を視覚化します。
        listOf(page - 1 to "◀", page + 1 to "▶").forEachIndexed { index, (targetPage, glyph) ->
            val available = targetPage in 0 until pageCount
            val x = centerX + if (index == 0) -0.12 else 0.12
            val visualId = "$id-page-$targetPage-bg"
            addBlock(
                visuals,
                visualId,
                x,
                centerY,
                0.18,
                0.10,
                if (available) Material.CYAN_CONCRETE else DisabledGuiVisualPolicy.material,
                4,
            )
            addText(
                visuals,
                "$id-page-$targetPage-label",
                x,
                centerY,
                0.005,
                60,
                Component.text(glyph).color(
                    if (available) NamedTextColor.WHITE else NamedTextColor.GRAY,
                ),
            )
            if (available) {
                elements.add(GestureGuiElement(
                    elementId = "lower-$id-page:$targetPage",
                    bounds = rect(x, centerY, 0.18, 0.10),
                    acceptedGestures = GestureGuiClickPolicy.CLICK,
                    targetVisualId = visualId,
                ))
            }
        }
        addText(visuals, "$id-page-status", centerX, centerY, 0.004, 80, Component.text("${page + 1}/$pageCount"))
    }

    private fun addBlock(
        visuals: MutableList<GestureGuiVisual>,
        id: String,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        material: Material,
        layer: Int,
        glowColor: Int? = null,
        outlineMaterial: Material? = null,
    ) {
        visuals.add(GestureGuiVisual.Block(
            visualId = id,
            x = x, y = y,
            width = w, height = h,
            blockData = Bukkit.createBlockData(material),
            layer = layer,
            glowColor = glowColor,
            outline = outlineMaterial?.let { GestureGuiOutline(Bukkit.createBlockData(it)) },
        ))
    }

    private fun addText(
        visuals: MutableList<GestureGuiVisual>,
        id: String,
        x: Double,
        y: Double,
        size: Double,
        lineWidth: Int,
        text: Component,
        alignment: GestureGuiTextAlignment = GestureGuiTextAlignment.CENTER,
    ) {
        visuals.add(GestureGuiVisual.Text(
            visualId = id,
            x = x, y = y,
            text = text,
            size = size,
            lineWidth = lineWidth,
            alignment = alignment,
        ))
    }

    private fun rect(cx: Double, cy: Double, w: Double, h: Double): GestureGuiBounds {
        val hw = w / 2.0
        val hh = h / 2.0
        return GestureGuiBounds(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    private fun scaleBounds(bounds: GestureGuiBounds, scale: Double): GestureGuiBounds =
        GestureGuiBounds(
            bounds.minX * scale,
            bounds.minY * scale,
            bounds.maxX * scale,
            bounds.maxY * scale,
        )

    /** 配置定数は宣言実装と共有します。同一モジュール内のみに公開します。 */
    internal companion object {
        // プログラム全体設定カードを2列x2行で配置するための定数です。各項目を同じ大きさで
        // 画面領域を等分し、上段にプログラム名・タイマー、下段にワールド変数・ディスク取得を置きます。
        // カード間0.08・両端0.035の余白を保ちます。
        const val SCRIPT_SETTING_COL_LEFT_X = -0.48
        const val SCRIPT_SETTING_COL_RIGHT_X = 0.48
        const val SCRIPT_SETTING_ROW_TOP_Y = 0.13
        const val SCRIPT_SETTING_ROW_BOTTOM_Y = -0.16
        const val SCRIPT_SETTING_WIDTH = 0.88
        const val SETTING_DESCRIPTION_HOVER_ID = "setting-description-hover"
        /** 常設説明とホバー説明に共通する文字寸法です。置き換え時のサイズ変化を防ぎます。 */
        const val DESCRIPTION_TEXT_SIZE = 0.0043
        // 既存の1行表示時の位置をアンカーとして保持し、複数の現在値行は
        // GestureSettingValueLayoutでこの2点の間隔から行ピッチを算出します。
        const val SETTING_VALUE_ANCHOR_Y = 0.27
        const val SETTING_DETAIL_ANCHOR_Y = 0.17
        // 既存タブと同じ比率指定で、行高の30%を行間として確保します。
        const val TIMING_ROW_GAP_RATIO = 0.30
        // 複数値行の画面では、先頭行が常設説明文へ接するため値行ブロックと
        // 編集枠をまとめて下へ移動させます。単行表示の画面配置は変えず、
        // 移動後も詳細行と編集枠上端の間隔規則は維持します。
        const val SETTING_COMPOSITE_SHIFT = 0.03
        const val SETTING_INPUT_CENTER_Y = 0.02
        const val SETTING_INPUT_HEIGHT = 0.16
        // 既存の入力欄（幅1.20）を、外周余白と中央間隔を保ったまま2分割します。
        const val TYPED_VALUE_SOURCE_WIDTH = 0.56
        const val TYPED_VALUE_SOURCE_LEFT_X = -0.04
        const val TYPED_VALUE_SOURCE_RIGHT_X = 0.60
        const val TYPED_VALUE_PREVIEW_CENTER_X = 0.28
        const val TYPED_VALUE_PREVIEW_CENTER_Y = -0.17
        const val TYPED_VALUE_PREVIEW_WIDTH = 1.20
        const val TYPED_VALUE_PREVIEW_HEIGHT = 0.16
        // 詳細・警告行は入力欄の上端から一定距離を空け、値行の計算結果が
        // 入力欄へ入り込む場合は値行ブロック全体を上へ移動します。
        const val SETTING_DETAIL_INPUT_GAP = 0.02
        // 6項目を同一画面へ収めるため、旧ページャーの4項目制限を廃止します。
        // タブの高さ0.10に対してピッチ0.11を確保し、意図された0.01の余白を維持します。
        const val SETTINGS_TAB_MAX = 6
        const val SETTINGS_TAB_CENTER_X = -0.7975
        const val SETTINGS_TAB_WIDTH = 0.47
        const val SETTINGS_TAB_TOP_Y = 0.38
        const val SETTINGS_TAB_PITCH = 0.11
        const val SETTINGS_TAB_HEIGHT = 0.10
        // 左列下端の固定操作（ノード選択中の削除／新規追加画面の閉じる）は同じ位置へ置き、
        // 画面種別が変わっても操作導線を一定に保ちます。
        const val SETTINGS_TAB_ACTION_Y = -0.43
        // 複製は削除の直上へ置き、設定タブ列と下端の固定操作を同じ左列に揃えます。
        const val SETTINGS_TAB_DUPLICATE_Y = SETTINGS_TAB_ACTION_Y + SETTINGS_TAB_PITCH
        // 設定候補とコマンド追加候補は、同じ2列×5行のページ契約を共有します。
        // 下端の操作列とは0.08ブロック以上離し、ページャーの重なりも防ぎます。
        const val SETTING_CHOICE_PAGE_SIZE = 10
        // ワールド変数一覧は下段に新規作成／戻るを左右配置するため、2列×3行に制限します。
        const val WORLD_VARIABLE_PAGE_SIZE = 6
        const val SETTING_CHOICE_WIDTH = 0.66
        const val SETTING_CHOICE_HEIGHT = 0.10
        const val SETTING_CHOICE_PITCH = 0.12
        // 親画面では上部の現在値と説明を避け、直接の設定項目を2列で表示します。
        const val TARGET_PARENT_CHOICE_TOP_Y = 0.08
        const val TARGET_PARENT_CHOICE_PITCH = 0.11
        // 子画面は親の右ペイン座標を再利用せず、子画面全体を論理領域として使います。
        const val CHILD_CHOICE_WIDTH = 0.92
        const val CHILD_CHOICE_TOP_Y = 0.08
        const val CHILD_CHOICE_PITCH = 0.12
        const val CHILD_HEADER_Y = 0.36
        const val CHILD_HOVER_Y = 0.23
        const val CHILD_DETAIL_HINT_Y = 0.14
        const val CHILD_PAGER_Y = -0.34
        const val CHILD_BACK_WIDTH = 1.70
        // ワールド内変数の各列は「値編集カード＋削除ボタン」で構成し、列全体の幅を
        // 既存の0.92へ固定します。ボタンの位置は一覧と確認画面で共通にします。
        const val WORLD_VARIABLE_CARD_WIDTH = 0.72
        const val WORLD_VARIABLE_DELETE_GAP = 0.05
        const val WORLD_VARIABLE_DELETE_WIDTH = 0.15
        const val ACTION_DESCRIPTION_Y = 0.36
        // 親画面のホバー説明は、常設の灰色説明（ACTION_DESCRIPTION_Y）と対になる
        // 画面下段のスロットへ表示します。操作行（-0.43）や対象カード（-0.25）と
        // 重ならない位置です。
        const val HOVER_SLOT_X = 0.28
        const val HOVER_SLOT_Y = -0.35
        const val HOVER_BUTTON_GAP = 0.04
        // PICKERの下段はページャー（0.28, -0.48）があるため、その上へ置きます。
        const val PICKER_HOVER_SLOT_Y = -0.38
        // 「ほかのエンティティ」のカードは、既存の4スロット区分を
        // GestureTargetChoiceLayoutPolicyで前後2スロットずつへまとめます。
        const val POSITION_TARGET_CHOICE_HEIGHT = 0.15
        const val POSITION_TARGET_CHOICE_Y = -0.25
        // issue #28: 候補一覧子画面の配置です。主要領域を一覧に使い、
        // 下部にページャーと「IDを入力」「戻る」を常設します。
        const val RESOURCE_TITLE_Y = 0.36
        const val RESOURCE_GRID_LEFT_X = -0.45
        const val RESOURCE_GRID_PITCH_X = 0.185
        const val RESOURCE_GRID_TOP_Y = 0.24
        const val RESOURCE_GRID_PITCH_Y = 0.14
        const val RESOURCE_CELL_W = 0.17
        const val RESOURCE_CELL_H = 0.12
        const val RESOURCE_CENTER_X = 0.20
        const val RESOURCE_PAGER_Y = -0.20
        const val RESOURCE_BUTTON_Y = -0.34
        const val RESOURCE_BUTTON_W = 0.55
        const val RESOURCE_BUTTON_LEFT_X = -0.075
        const val RESOURCE_BUTTON_RIGHT_X = 0.475
        /** 構造化モデルを壊さず、paramsへ文字列として保存できる項目だけを許可します。 */
        val TEXT_EDITABLE_KEYS = setOf(
            "item", "itemData", "count", "text", "subtitle", "customName", "tags", "tag", "sound", "soundParameters", "volume", "pitch", "particle", "particleParameters", "particleData",
            "particleDensity", "burstHeight",
            "effect", "level", "seconds", "fadeInSeconds", "staySeconds", "fadeOutSeconds", "intensity", "slot", "entity", "diskId", "name",
            "condition", "variable", "value", "block", "sneaking",
        )
    }
}
