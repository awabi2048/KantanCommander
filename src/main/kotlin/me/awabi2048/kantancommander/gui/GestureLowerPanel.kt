package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccess
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenDefinition
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiView
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player

/** 既存の選択肢生成を木のノードとして扱うための局所的な別名です。 */
private typealias SettingChoice = GestureSettingTreeNode

/**
 * ジェスチャーエディターの下部パネルのビュー生成を担います。
 *
 * - SETTINGS: 左タブ列＝設定フィールド、右詳細＝現在値＋説明。設定木の直下は親画面で編集
 * - PICKER: 左タブ列＝コマンドカテゴリ（PROCESS/CONTROL）、右詳細＝種別一覧
 * - CONFIRM: 上部エディターが子画面（openChild・赤ガラス）として開く
 *
 * 親画面は左タブ列＋右詳細の分割型、子画面は子画面全体を使う集中型です。
 * 座標は画面中央原点・ブロック単位です。
 */
class GestureLowerPanel(
    private val plugin: KantanCommanderPlugin,
    private val onAction: (GestureGuiActionContext) -> Unit = {},
) {
    val LOWER_SCREEN_ID = "gesture-editor-lower"
    /** 個別設定専用。親の下部画面へモーダルに重ねます。 */
    val SETTING_CHILD_SCREEN_ID = "gesture-editor-setting-child"
    val CONFIRM_SCREEN_ID = "gesture-editor-confirm"

    /** 子画面の縦横を親の50%にする一様縮尺です。 */
    private val SETTING_CHILD_SCALE = 0.5

    fun build(state: GestureEditorState, player: Player): GestureGuiView {
        return when (state.lowerMode) {
            GestureLowerMode.SETTINGS -> buildSettings(state, player)
            GestureLowerMode.PICKER -> buildPicker(state, player)
            GestureLowerMode.SETTING_CHOICES -> buildSettingChoices(state, player)
            GestureLowerMode.CONFIRM -> buildConfirm(state, player)
        }
    }

    /** 親のタブ列を継承せず、子画面全体で詳細設定を生成します。 */
    fun buildSettingChild(state: GestureEditorState, player: Player): GestureGuiView =
        buildSettingChoices(state, player, child = true)

    /** SETTINGS: 左タブ列＋固定操作、右詳細＝値表示と編集導線です。 */
    private fun buildSettings(state: GestureEditorState, player: Player): GestureGuiView {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        val script = plugin.scripts.load(state.scriptId)
        val node = state.selectedNodeId?.let { id -> script?.graph?.nodes?.get(id) }
        if (node == null) {
            addText(visuals, "lower-hint", 0.0, 0.20, 0.010, 160, Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_SELECT_NODE_HINT)))
            return view(GestureLowerMode.SETTINGS, elements, visuals)
        }

        val fields = CommandSettingsModel.visibleFields(node)
        if (fields.isEmpty()) {
            addText(visuals, "lower-hint", 0.28, 0.20, 0.010, 160, Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_NO_FIELDS)))
            return view(GestureLowerMode.SETTINGS, elements, visuals)
        }
        val pageCount = addSettingsNavigation(state, player, node, visuals, elements)
        val page = state.settingsPage.coerceIn(0, pageCount - 1)
        val pageStart = page * SETTINGS_PAGE_SIZE
        val tabs = fields.drop(pageStart).take(SETTINGS_PAGE_SIZE)
        val selectedAbsolute = state.settingsTab.coerceIn(0, fields.lastIndex)
        val selected = if (selectedAbsolute in pageStart until pageStart + tabs.size) selectedAbsolute - pageStart else 0
        val field = tabs[selected]
        val contextOverrideActive = state.settingScreen == GestureSettingScreen.CONTEXT_OVERRIDE &&
            state.settingFieldKey == "context" && state.settingContext?.nodeId == node.id
        val descriptor = if (contextOverrideActive) {
            CommandSettingDescriptor(CommandSettingEditor.CONTEXT)
        } else CommandSettingsModel.descriptor(node, field.key)
        val settingContext = state.settingContext
            ?: CommandSettingContext(state.scriptId, node.id, descriptor.role)
        val settingField = if (contextOverrideActive) null else field
        val displayLabel = if (contextOverrideActive) {
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONTEXT_OVERRIDE)
        } else KcI18n.text(player, field.label)
        val value = if (contextOverrideActive) {
            if (node.contextOverride == null) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERIT_ALL)
            } else KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_PARTIAL_OVERRIDE)
        } else field.value(node).render(player)
        val settingScreen = if (contextOverrideActive) {
            GestureSettingScreen.CONTEXT_OVERRIDE
        } else gestureSettingScreenFor(descriptor.editor)
        val settingChoices = settingScreen?.let { screen ->
            settingTreeNodes(
                node,
                settingContext,
                screen,
                if (contextOverrideActive) "context" else field.key,
                player,
            ).map { choice ->
                if (state.settingTreePath?.nodeIds?.lastOrNull() == choice.id) {
                    choice.copy(selected = true)
                } else choice
            }
        }.orEmpty()
        val selectedDetail = settingChoices.firstOrNull { it.selected && it.hasChildren }
        addDescriptionRows(
            visuals,
            player,
            settingField,
            null,
            fallback = displayLabel,
            actionFallback = if (contextOverrideActive) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONTEXT_OVERRIDE_HOVER)
            } else null,
            detailHint = selectedDetail
                ?.let { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_OPEN_FILTERS) },
        )
        addValueRow(visuals, "lower-setting", 0.26, displayLabel, value)

        if (settingScreen != null) {
            // 設定木の直下は常に親画面へ表示します。子画面は、選択中の
            // ノードがさらに子要素を持つ場合だけ、再クリックで開きます。
            addSettingChoiceNodes(settingChoices, player, visuals, elements)
        }

        // 設定木の直下はこの親画面に直接表示します。葉の入力や、木に含まれない
        // 文字列・数値だけを右ペインのダイアログ導線へ残し、専用子画面を増やしません。
        val heldItemSetting = !contextOverrideActive &&
            field.key == "item" && node.type in setOf(CommandType.GIVE_ITEM, CommandType.EQUIP_ITEM)
        val mainHandAvailable = player.inventory.itemInMainHand.type != Material.AIR
        val configuredItem = node.string("item").isNotBlank() || node.string("itemData").isNotBlank()
        if (heldItemSetting || (descriptor.editor == CommandSettingEditor.TEXT && field.key in DIALOG_EDITABLE_KEYS)) {
            addBlock(visuals, "lower-edit-bg", 0.28, 0.02, 1.2, 0.16, Material.CYAN_TERRACOTTA, 4)
            addText(
                visuals,
                "lower-edit",
                0.28,
                0.02,
                0.0055,
                180,
                Component.text(KcI18n.text(player, if (heldItemSetting) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_EDIT_FROM_MAINHAND
                } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_EDIT_VALUE)),
            )
            elements.add(GestureGuiElement(
                elementId = "lower-edit:${field.key}",
                bounds = rect(0.28, 0.02, 1.2, 0.16),
                // 空のメインハンドでは入力面自体を無効化し、CC-Systemの
                // クリック音も発生させません。
                acceptedGestures = if (heldItemSetting && !mainHandAvailable) {
                    emptySet()
                } else setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY),
                targetVisualId = "lower-edit-bg",
                hoverText = singleLineHover(
                    KcI18n.text(
                        player,
                        if (heldItemSetting) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MAINHAND_SAVE_HOVER
                        else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_HOVER,
                    ),
                    x = 0.28,
                    y = 0.39,
                ),
            ))
        }
        if (heldItemSetting) {
            addBlock(visuals, "lower-item-get-bg", 0.28, -0.15, 1.2, 0.12, Material.BROWN_CONCRETE, 4)
            addText(visuals, "lower-item-get", 0.28, -0.15, 0.0048, 180, Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_ITEM)))
            elements.add(GestureGuiElement(
                elementId = "lower-item-get",
                bounds = rect(0.28, -0.15, 1.2, 0.12),
                // 設定値がない取得ボタンは入力面を作らず、効果音を含めて無操作にします。
                acceptedGestures = if (!configuredItem) {
                    emptySet()
                } else setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "lower-item-get-bg",
                hoverText = singleLineHover(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_ITEM_HOVER),
                    x = 0.28,
                    y = 0.39,
                ),
            ))
        }
        return view(GestureLowerMode.SETTINGS, elements, visuals)
    }

    /**
     * 設定木の現在画面に属する直下ノードを配置します。
     *
     * 親画面ではタブの直下を、子画面では現在選択ノードの直下を描画します。
     * 描画側は子要素の意味を解釈せず、同じノードを選択・ホバー可能にするだけです。
     */
    private fun addSettingChoiceNodes(
        choices: List<GestureSettingTreeNode>,
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        child: Boolean = false,
    ) {
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
            val bgId = "setting-choice-bg-$index"
            addBlock(
                visuals,
                bgId,
                cx,
                cy,
                width,
                SETTING_CHOICE_HEIGHT,
                if (choice.selected) Material.CYAN_CONCRETE else Material.CYAN_TERRACOTTA,
                4,
            )
            addText(visuals, "setting-choice-label-$index", cx, cy - 0.012, 0.0045, 115, Component.text(choice.label))
            elements.add(GestureGuiElement(
                elementId = "lower-setting-choice:${choice.id}",
                bounds = rect(cx, cy, width, SETTING_CHOICE_HEIGHT),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = bgId,
                hoverText = singleLineHover(
                    choice.description.ifBlank { choiceDescription(player, choice) },
                    x = if (child) 0.0 else 0.28,
                    y = if (child) CHILD_HOVER_Y else ACTION_DESCRIPTION_Y,
                ),
            ))
        }
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
        pagerCenterX: Double = -0.10,
    ): Int {
        val fields = CommandSettingsModel.visibleFields(node)
        if (fields.isEmpty()) return 1
        val pageCount = (fields.size + SETTINGS_PAGE_SIZE - 1) / SETTINGS_PAGE_SIZE
        val page = state.settingsPage.coerceIn(0, pageCount - 1)
        val pageStart = page * SETTINGS_PAGE_SIZE
        val tabs = fields.drop(pageStart).take(SETTINGS_PAGE_SIZE)
        val activeField = state.settingFieldKey?.let { key -> fields.indexOfFirst { it.key == key } }
            ?.takeIf { it >= 0 } ?: state.settingsTab.coerceIn(0, fields.lastIndex)
        val selected = if (activeField in pageStart until pageStart + tabs.size) activeField - pageStart else 0
        tabs.forEachIndexed { index, field ->
            val cy = 0.38 - index * 0.17
            val on = index == selected
            addBlock(visuals, "tab-bg-$index", -0.7975, cy, 0.47, 0.15,
                if (on) Material.CYAN_CONCRETE else Material.CYAN_TERRACOTTA, 4)
            addText(visuals, "tab-$index", -0.7975, cy - 0.02, 0.0055, 90,
                Component.text(KcI18n.text(player, field.label)))
            elements.add(GestureGuiElement(
                elementId = "lower-tab:${pageStart + index}",
                bounds = rect(-0.7975, cy, 0.47, 0.15),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "tab-bg-$index",
                hoverText = singleLineHover(
                    fieldActionDescription(player, field),
                    x = 0.28,
                    y = descriptionY(node, field),
                ),
            ))
        }

        val contextY = -0.29
        if (CommandPresentationPolicy.supportsContextOverride(node.type)) {
            val activeContext = state.settingFieldKey == "context" && state.settingScreen == GestureSettingScreen.CONTEXT_OVERRIDE
            addBlock(visuals, "context-bg", -0.7975, contextY, 0.47, 0.14,
                if (activeContext) Material.YELLOW_CONCRETE else Material.YELLOW_TERRACOTTA, 4)
            addText(visuals, "context-label", -0.7975, contextY - 0.018, 0.0045, 110,
                Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONTEXT_OVERRIDE)))
            elements.add(GestureGuiElement(
                elementId = "lower-context",
                bounds = rect(-0.7975, contextY, 0.47, 0.14),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "context-bg",
                hoverText = singleLineHover(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONTEXT_OVERRIDE_HOVER),
                    x = 0.28,
                    y = 0.39,
                ),
            ))
        }

        val deleteY = -0.43
        addBlock(visuals, "delete-bg", -0.7975, deleteY, 0.47, 0.10, Material.RED_CONCRETE, 4)
        addText(visuals, "delete-label", -0.7975, deleteY, 0.0049, 90,
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE)))
        elements.add(GestureGuiElement(
            elementId = "lower-delete",
            bounds = rect(-0.7975, deleteY, 0.47, 0.10),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            targetVisualId = "delete-bg",
        ))
        // ページャーは左ナビと右ペインの境界に置きます。専用選択画面では
        // 候補用ページャーと重ならないよう、呼び出し側から位置を分けます。
        if (pageCount > 1) addPager(visuals, elements, "settings", page, pageCount, pagerCenterX, -0.43)
        return pageCount
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

    /**
     * 説明ブロックを意味別の固定スロットへ配置します。
     *
     * 1行目は選択中タブ、2行目はそのタブでの操作、3行目は現在選択中の
     * ノードが詳細を持つ場合だけ表示する案内です。対象だけ別レイアウトに
     * 分岐させず、候補の説明は各要素のホバーへ同じ規則で渡します。
     */
    private fun addDescriptionRows(
        visuals: MutableList<GestureGuiVisual>,
        player: Player,
        field: EditorField?,
        hoveredDescription: String?,
        fallback: String = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_SETTINGS_FIELD_FALLBACK),
        actionFallback: String? = null,
        detailHint: String? = null,
        centerX: Double = 0.28,
        tabY: Double = 0.43,
        hoverY: Double = ACTION_DESCRIPTION_Y,
        detailY: Double = 0.23,
    ) {
        addText(
            visuals,
            "setting-description-tab",
            centerX,
            tabY,
            0.0047,
            280,
            Component.text(field?.let { fieldDescription(player, it) } ?: fallback),
        )
        addText(
            visuals,
            "setting-description-hover",
            centerX,
            hoverY,
            0.0043,
            280,
            Component.text(
                hoveredDescription
                    ?: if (field?.key == "target") {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_TARGET_ACTION_FALLBACK)
                    } else field?.let { fieldActionDescription(player, it) }
                        ?: actionFallback
                        ?: "",
                NamedTextColor.GRAY,
            ),
        )
        if (!detailHint.isNullOrBlank()) {
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

    private fun fieldDescription(player: Player, field: EditorField): String =
        KcI18n.list(player, field.descriptionKey).firstOrNull()?.takeIf(String::isNotBlank)
            ?: KcI18n.text(player, field.label)

    private fun fieldActionDescription(player: Player, field: EditorField): String =
        if (field.key == "item") KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MAINHAND_SAVE_GET_HOVER)
        else KcI18n.text(player, field.actionKey)

    private fun descriptionY(node: CommandNode, field: EditorField): Double =
        if (CommandSettingsModel.descriptor(node, field.key).editor == CommandSettingEditor.TARGET) {
            ACTION_DESCRIPTION_Y
        } else DEFAULT_HOVER_Y

    /** 選択肢ごとの意味を、選択肢IDと明示対応させたカタログキーから生成します。 */
    private fun choiceDescription(player: Player, choice: SettingChoice): String = when {
        choice.id.startsWith("target:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_TARGET)
        choice.id == "filter:entityType" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_ENTITY_TYPE)
        choice.id == "filter:minimumDistance" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_MINIMUM_DISTANCE_BODY)
        choice.id == "filter:maximumDistance" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_MAXIMUM_DISTANCE_BODY)
        choice.id == "filter:limit" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_LIMIT)
        choice.id == "filter:sort" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_SORT)
        choice.id == "filter:gameMode" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_GAME_MODE)
        choice.id == "filter:tag" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_TAG)
        choice.id == "filter:name" -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_NAME)
        choice.id.startsWith("filter:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_DEFAULT)
        choice.id.startsWith("position:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_POSITION)
        choice.id.startsWith("facing:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FACING)
        choice.id.startsWith("context:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_CONTEXT)
        choice.id.startsWith("condition-") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_CONDITION)
        choice.id.startsWith("display:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_DISPLAY)
        choice.id.startsWith("action:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_ACTION)
        choice.id.startsWith("scope:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_SCOPE)
        choice.id.startsWith("type:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_TYPE)
        choice.id.startsWith("operation:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_OPERATION)
        choice.id.startsWith("value:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_VALUE)
        choice.id.startsWith("source:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_SOURCE)
        choice.id.startsWith("inclusive:") -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_INCLUSIVE)
        else -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_DEFAULT)
    }

    /**
     * ホバーは1行1エンティティで表示します。1つのTextDisplayへ複数行を
     * 入れると中央列揃えになり下部パネルのレイアウトが崩れるためです。
     * 項目の説明は常設の説明ディスプレイへ集約し、ホバーは操作案内のみを
     * 担うことで表示の重複も解消します。
     */
    private fun singleLineHover(text: String, x: Double, y: Double): GestureGuiHoverText =
        GestureGuiHoverText(
            text = Component.text(text),
            x = x,
            y = y,
            size = 0.0048,
            lineWidth = 280,
        )

    /**
     * 現在の設定経路における直下の木ノードを返します。
     *
     * `TARGET` の対象種別、`POSITION` の移動先種別、条件・コンテキストの
     * 下位ドメインだけが必要に応じて子を持ちます。単純な列挙選択は葉として
     * 扱い、共通の親画面へ表示して不要な子画面を作りません。
     */
    internal fun settingTreeNodes(
        node: CommandNode,
        context: CommandSettingContext,
        screen: GestureSettingScreen,
        fieldKey: String,
        player: Player,
    ): List<GestureSettingTreeNode> = when (screen) {
        GestureSettingScreen.TARGET -> targetChoices(node, context, player)
        GestureSettingScreen.POSITION -> positionChoices(node, context, player).map { choice ->
            if (choice.id == "position:${PositionKind.TARGET.name}" && context.role == CommandSettingRole.DESTINATION) {
                choice.copy(children = targetChoices(node, context.copy(role = CommandSettingRole.DESTINATION), player))
            } else choice
        }
        GestureSettingScreen.CONDITION_DETAIL -> conditionDetailChoices(node, player).map { choice ->
            when (choice.id) {
                "condition-target" -> choice.copy(
                    selected = CommandSettingsModel.targetSpec(node, CommandSettingRole.NODE_TARGET) != null,
                    children = targetChoices(
                        node,
                        context.copy(role = CommandSettingRole.NODE_TARGET),
                        player,
                    ),
                )
                "condition-position" -> choice.copy(
                    selected = CommandSettingsModel.positionSpec(node, CommandSettingRole.CONDITION_POSITION) != null,
                    children = positionChoices(
                        node,
                        context.copy(role = CommandSettingRole.CONDITION_POSITION),
                        player,
                    ),
                )
                else -> choice
            }
        }
        GestureSettingScreen.CONTEXT_OVERRIDE -> settingChoices(node, context, screen, fieldKey, player).map { choice ->
            when (choice.id) {
                "context:executor" -> choice.copy(
                    children = targetChoices(node, context.copy(role = CommandSettingRole.CONTEXT_EXECUTOR), player),
                )
                "context:target" -> choice.copy(
                    children = targetChoices(node, context.copy(role = CommandSettingRole.CONTEXT_TARGET), player),
                )
                "context:position" -> choice.copy(
                    children = positionChoices(node, context.copy(role = CommandSettingRole.CONTEXT_POSITION), player),
                )
                "context:facing" -> choice.copy(
                    children = facingChoices(node, player),
                )
                else -> choice
            }
        }
        else -> settingChoices(node, context, screen, fieldKey, player)
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
            .firstOrNull { it.id == choiceId }
            ?.hasChildren == true
    }

    /** 木の選択状態を再利用し、初回選択と詳細再クリックを区別します。 */
    internal fun isSettingChoiceSelected(
        state: GestureEditorState,
        choiceId: String,
    ): Boolean {
        // 永続値が選択済みでも、画面を開いた直後のクリックは「選択」と数えます。
        // 再クリックだけを詳細子画面の入口にするため、判定は一時的な木の経路に
        // 限定し、モデル上のselectedとは分離します。
        return state.settingTreePath?.nodeIds?.lastOrNull() == choiceId
    }

    /**
     * 専用選択で編集する設定画面です。
     *
     * ここではInventoryMenuの画面IDを直接再利用せず、同じ
     * CommandSettingEditor／CommandSettingRoleを選択肢へ投影します。これにより、
     * どのGUIから変更しても同じCommandNodeの構造化フィールドへ保存されます。
     * 画面上の値は設定画面と同じく「項目名 設定値」の1行を常に上部へ表示します。
     */
    private fun buildSettingChoices(
        state: GestureEditorState,
        player: Player,
        child: Boolean = false,
    ): GestureGuiView {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        val context = state.settingContext
        val script = context?.let { plugin.scripts.load(it.scriptId) }
        val node = context?.let { script?.graph?.nodes?.get(it.nodeId) }
        val fieldKey = state.settingFieldKey
        val screen = state.settingScreen
        if (context == null || node == null || fieldKey == null || screen == null) {
            addText(visuals, "setting-hint", if (child) 0.0 else 0.28, if (child) 0.12 else 0.20, 0.008, 220,
                Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_NO_FIELDS)))
            addBackSetting(player, elements, visuals, child = child)
            return view(GestureLowerMode.SETTING_CHOICES, elements, visuals, child = child)
        }

        // 親画面では設定タブを保持します。子画面は詳細設定に集中させ、
        // 親のタブ・コンテキスト・削除操作を重ねて表示しません。
        if (!child) addSettingsNavigation(state, player, node, visuals, elements, pagerCenterX = -0.30)
        val field = CommandSettingsModel.visibleFields(node).firstOrNull { it.key == fieldKey }
        val fieldLabel = field?.let { KcI18n.text(player, it.label) }
            ?: if (fieldKey == "context") {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONTEXT_OVERRIDE)
            } else fieldKey
        val fieldValue = field?.value?.invoke(node)?.render(player)
            ?: if (screen == GestureSettingScreen.CONTEXT_OVERRIDE) {
                if (node.contextOverride == null) KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERIT_ALL)
                else KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_PARTIAL_OVERRIDE)
            } else settingCurrentValue(node, context, screen, fieldKey, player)
        val choices = settingTreeNodes(node, context, screen, fieldKey, player).map { choice ->
            if (state.settingTreePath?.nodeIds?.lastOrNull() == choice.id) {
                choice.copy(selected = true)
            } else choice
        }
        val selectedDetail = choices.firstOrNull { it.selected && it.hasChildren }
        addDescriptionRows(
            visuals,
            player,
            field,
            null,
            fallback = fieldLabel,
            detailHint = selectedDetail?.let {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_OPEN_FILTERS)
            },
            centerX = if (child) 0.0 else 0.28,
            tabY = if (child) CHILD_TAB_DESCRIPTION_Y else 0.43,
            hoverY = if (child) CHILD_HOVER_Y else ACTION_DESCRIPTION_Y,
            detailY = if (child) CHILD_DETAIL_HINT_Y else 0.23,
        )
        addValueRow(
            visuals,
            "setting-header",
            if (child) CHILD_HEADER_Y else 0.26,
            fieldLabel,
            fieldValue,
            labelX = if (child) -0.53 else -0.08,
            valueX = if (child) 0.53 else 0.52,
        )

        val pageSize = SETTING_CHOICE_PAGE_SIZE
        val pageCount = (choices.size + pageSize - 1) / pageSize
        val page = state.settingPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        addSettingChoiceNodes(
            choices.drop(page * pageSize).take(pageSize),
            player,
            visuals,
            elements,
            child = child,
        )
        if (pageCount > 1) addPager(
            visuals,
            elements,
            "setting",
            page,
            pageCount,
            if (child) 0.0 else 0.25,
            if (child) CHILD_PAGER_Y else -0.43,
        )
        if (child || screen != GestureSettingScreen.CONTEXT_OVERRIDE) {
            addBackSetting(
                player,
                elements,
                visuals,
                child = child,
                centerX = if (child) 0.0 else 0.78,
                width = if (child) CHILD_BACK_WIDTH else 0.42,
            )
        }
        return view(GestureLowerMode.SETTING_CHOICES, elements, visuals, child = child)
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
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
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
        GestureSettingScreen.FACING -> facingChoices(node, player)
        GestureSettingScreen.CONDITION_KIND -> conditionKindChoices(node, player)
        GestureSettingScreen.CONDITION_DETAIL -> conditionDetailChoices(node, player)
        GestureSettingScreen.DISPLAY_MODE -> listOf(
            SettingChoice("display:tellraw", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHAT), node.string("mode", "tellraw") == "tellraw"),
            SettingChoice("display:title", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TITLE), node.string("mode", "tellraw") == "title"),
            SettingChoice("display:actionbar", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ACTIONBAR), node.string("mode", "tellraw") == "actionbar"),
        )
        GestureSettingScreen.ENTITY_ACTION -> listOf(
            SettingChoice("action:ride", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RIDE), node.string("action", "ride") == "ride"),
            SettingChoice("action:dismount", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISMOUNT), node.string("action", "ride") == "dismount"),
        )
        GestureSettingScreen.VARIABLE_SCOPE -> listOf(
            SettingChoice(
                "scope:TEMPORARY",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE),
                node.string("scope", VariableScope.TEMPORARY.name) == VariableScope.TEMPORARY.name,
            ),
            SettingChoice(
                "scope:WORLD",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE),
                node.string("scope", VariableScope.TEMPORARY.name) == VariableScope.WORLD.name,
            ),
        )
        GestureSettingScreen.VARIABLE_TYPE -> listOf(
            SettingChoice("type:BOOLEAN", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TRUE_FALSE), node.string("type", VariableType.BOOLEAN.name) == VariableType.BOOLEAN.name),
            SettingChoice("type:INTEGER", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INTEGER), node.string("type", VariableType.BOOLEAN.name) == VariableType.INTEGER.name),
            SettingChoice("type:DECIMAL", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DECIMAL), node.string("type", VariableType.BOOLEAN.name) == VariableType.DECIMAL.name),
            SettingChoice("type:TEXT", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT), node.string("type", VariableType.BOOLEAN.name) == VariableType.TEXT.name),
            SettingChoice("type:POSITION", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION), node.string("type", VariableType.BOOLEAN.name) == VariableType.POSITION.name),
            SettingChoice("type:ENTITY", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_REFERENCE), node.string("type", VariableType.BOOLEAN.name) == VariableType.ENTITY.name),
        )
        GestureSettingScreen.VARIABLE_OPERATION -> {
            val type = runCatching { VariableType.valueOf(node.string("type", VariableType.BOOLEAN.name)) }
                .getOrDefault(VariableType.BOOLEAN)
            CommandSettingsModel.allowedVariableOperations(type).map { operation ->
                SettingChoice(
                    "operation:${operation.name}",
                    KcI18n.text(player, operationLabel(operation)),
                    node.string("operation", operation.name) == operation.name,
                )
            }
        }
        GestureSettingScreen.VARIABLE_VALUE -> buildList {
            add(SettingChoice("value:direct", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DIRECT_VALUE), !node.string("value").startsWith("$")))
            val script = plugin.scripts.load(context.scriptId)
            val insideFor = script != null && node.string("type", VariableType.BOOLEAN.name) == VariableType.INTEGER.name &&
                node.string("scope", VariableScope.TEMPORARY.name) != VariableScope.WORLD.name &&
                GraphEditor.isInsideFor(script.graph, node.id, GraphEditor.Edge.NEXT)
            if (insideFor) {
                add(SettingChoice("value:iteration", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_ITERATION), node.string("value") == "\$current_iteration_value"))
                add(SettingChoice("value:count", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT), node.string("value") == "\$current_loop_count"))
            }
        }
        GestureSettingScreen.FOR_SOURCE -> listOf(
            SettingChoice("source:FIXED", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_VALUE), node.string(fieldKey, "FIXED") == "FIXED"),
            SettingChoice("source:TEMPORARY", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE), node.string(fieldKey, "FIXED") == "TEMPORARY"),
            SettingChoice("source:WORLD", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE), node.string(fieldKey, "FIXED") == "WORLD"),
        )
        GestureSettingScreen.INCLUSIVE_END -> if (node.type == CommandType.CONDITION && fieldKey == "inverted") {
            listOf(
                SettingChoice("inclusive:true", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_ON), node.boolean(fieldKey, false)),
                SettingChoice("inclusive:false", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_OFF), !node.boolean(fieldKey, false)),
            )
        } else {
            listOf(
                SettingChoice("inclusive:true", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INCLUSIVE_ON), node.boolean(fieldKey, true)),
                SettingChoice("inclusive:false", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INCLUSIVE_OFF), !node.boolean(fieldKey, true)),
            )
        }
        GestureSettingScreen.CONTEXT_OVERRIDE -> listOf(
            SettingChoice("context:executor", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EXECUTOR), node.contextOverride?.executor != null),
            SettingChoice("context:target", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET), node.contextOverride?.target != null),
            SettingChoice("context:position", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION), node.contextOverride?.position != null),
            SettingChoice("context:facing", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING), node.contextOverride?.facing != null),
            SettingChoice(
                "context:source",
                KcI18n.text(
                    player,
                    if (CommandSettingsModel.contextSource(node) == ContextSource.PREVIOUS) {
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTEXT_PREVIOUS
                    } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTEXT_BASE,
                ),
            ),
            SettingChoice("context:inherit", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERIT_ALL), node.contextOverride == null),
        )
    }

    /** 選択肢ラベルを「ラベル 現在値」形式へ結合します。未設定はGUI共通の未設定文言を使います。 */
    private fun labeled(player: Player, key: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>, value: String?): String =
        "${KcI18n.text(player, key)} ${value ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)}"

    /** 対象種別を木の親ノードとして表示し、詳細条件を子ノードへぶら下げます。 */
    private fun targetChoices(node: CommandNode, context: CommandSettingContext, player: Player): List<SettingChoice> {
        val current = CommandSettingsModel.targetSpec(node, context.role)?.kind
        val kindChoices = listOf(
            TargetKind.INHERITED_TARGET to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERITED_TARGET,
            TargetKind.NEAREST_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER,
            TargetKind.NEARBY_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS,
            TargetKind.ALL_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS,
            TargetKind.RANDOM_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RANDOM_PLAYER,
            TargetKind.NEAREST_ENTITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY,
            TargetKind.NEARBY_ENTITIES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES,
            TargetKind.FIXED_ENTITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY,
        ).map { (kind, label) ->
            SettingChoice(
                id = "target:${kind.name}",
                label = KcI18n.text(player, label),
                selected = current == kind,
                children = if (CommandSettingsModel.targetSupportsDetailedFilters(kind)) {
                    targetFilterChoices(node, context, player, kindOverride = kind)
                } else emptyList(),
            )
        }
        return kindChoices
    }

    private fun targetFilterChoices(
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
                    "minimumDistance" -> it.minimumDistance?.toString()
                    "maximumDistance" -> it.maximumDistance?.toString()
                    "limit" -> it.limit?.toString()
                    "tag" -> it.tag
                    else -> it.name
                }
            }
        }
        return listOf(
            "entityType" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_TYPE,
            "minimumDistance" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE,
            "maximumDistance" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE,
            "limit" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LIMIT,
            "sort" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SORT,
            "gameMode" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GAME_MODE,
            "tag" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG,
            "name" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME,
        )
            .filter { (id, _) -> CommandSettingsModel.targetFilterApplies(spec.kind, id) }
            .map { (id, label) -> SettingChoice("filter:$id", labeled(player, label, value(id))) }
    }

    private fun positionChoices(node: CommandNode, context: CommandSettingContext, player: Player): List<SettingChoice> {
        val destination = context.role == CommandSettingRole.DESTINATION
        val current = CommandSettingsModel.positionSpec(node, context.role)?.kind
        val choices = if (destination) {
            listOf(
                PositionKind.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES_SET,
                PositionKind.TARGET to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_OTHER_ENTITY,
            )
        } else {
            listOf(
                PositionKind.DISK to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISK_POSITION,
                PositionKind.EXECUTOR to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EXECUTOR_POSITION,
                PositionKind.TARGET to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION,
                PositionKind.MYWORLD_SPAWN to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN,
                PositionKind.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES,
                PositionKind.TEMPORARY_VARIABLE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE,
                PositionKind.WORLD_VARIABLE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE,
            )
        }
        return choices.map { (kind, label) -> SettingChoice("position:${kind.name}", KcI18n.text(player, label), current == kind) }
    }

    private fun facingChoices(node: CommandNode, player: Player): List<SettingChoice> {
        val current = CommandSettingsModel.facingSpec(node)?.kind
        return listOf(
            FacingKind.INHERITED to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_UNCHANGED,
            FacingKind.CAPTURED to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_FACING,
            FacingKind.EXECUTOR to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EXECUTOR_FACING,
            FacingKind.TARGET to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET,
            FacingKind.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES,
            FacingKind.MYWORLD_SPAWN to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN,
            FacingKind.ROTATION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMERIC,
        ).map { (kind, label) -> SettingChoice("facing:${kind.name}", KcI18n.text(player, label), current == kind) }
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
            ConditionKind.ENTITY_STATE -> listOf(
                SettingChoice("condition-target", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET), node.targetSpec != null),
                SettingChoice(
                    "condition-state",
                    label(
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_STATE,
                        KcI18n.text(
                            player,
                            if (node.string("state", "sneaking") == "sneaking") KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SNEAKING
                            else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ON_GROUND,
                        ),
                    ),
                ),
            )
            ConditionKind.VARIABLE_STATE -> listOf(
                SettingChoice("condition-variable", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, node.string("variable"))),
                SettingChoice(
                    "condition-scope",
                    label(
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE_SCOPE,
                        KcI18n.text(
                            player,
                            if (node.string("variableScope", VariableScope.TEMPORARY.name) == VariableScope.WORLD.name) {
                                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE
                            } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE,
                        ),
                    ),
                ),
                SettingChoice("condition-operator", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATOR, node.string("operator", "=="))),
                SettingChoice("condition-value", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, node.string("value", "0"))),
            )
            ConditionKind.BLOCK_STATE -> listOf(
                SettingChoice("condition-position", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION), node.conditionPositionSpec != null),
                SettingChoice("condition-block", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK, node.string("block", "minecraft:air"))),
            )
            ConditionKind.ITEM_POSSESSION -> listOf(
                SettingChoice("condition-target", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET), node.targetSpec != null),
                SettingChoice("condition-item", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM_CONDITION, node.string("item"))),
                SettingChoice("condition-count", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT, node.string("count", "1"))),
            )
        }
    }

    private fun operationLabel(operation: VariableOperation): com.awabi2048.ccsystem.api.localization.LocalizationKey<String> = when (operation) {
        VariableOperation.SET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SET
        VariableOperation.ADD -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ADD
        VariableOperation.SUBTRACT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SUBTRACT
        VariableOperation.TOGGLE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TOGGLE
        VariableOperation.STORE_POSITION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STORE_POSITION
        VariableOperation.STORE_TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STORE_TARGET
        VariableOperation.CLEAR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CLEAR
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
        GestureSettingScreen.POSITION -> CommandSettingsModel.positionSpec(node, context.role)?.kind
            ?.let { positionKindLabel(player, it) } ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        GestureSettingScreen.FACING -> CommandSettingsModel.facingSpec(node)?.kind
            ?.let { facingKindLabel(player, it) } ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        else -> node.string(fieldKey).ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }
    }

    private fun targetKindLabel(player: Player, kind: TargetKind): String = KcI18n.text(player, when (kind) {
        TargetKind.INHERITED_TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERITED_TARGET
        TargetKind.NEAREST_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER
        TargetKind.NEARBY_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS
        TargetKind.ALL_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS
        TargetKind.RANDOM_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RANDOM_PLAYER
        TargetKind.NEAREST_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY
        TargetKind.NEARBY_ENTITIES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES
        TargetKind.FIXED_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
    })

    private fun positionKindLabel(player: Player, kind: PositionKind): String = KcI18n.text(player, when (kind) {
        PositionKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_POSITION
        PositionKind.DISK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISK_POSITION
        PositionKind.EXECUTOR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EXECUTOR_POSITION
        PositionKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION
        PositionKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN
        PositionKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES
        PositionKind.TEMPORARY_VARIABLE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
        PositionKind.WORLD_VARIABLE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE
    })

    private fun facingKindLabel(player: Player, kind: FacingKind): String = KcI18n.text(player, when (kind) {
        FacingKind.INHERITED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_UNCHANGED
        FacingKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_FACING
        FacingKind.EXECUTOR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EXECUTOR_FACING
        FacingKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET
        FacingKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES
        FacingKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN
        FacingKind.ROTATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMERIC
    })

    /** PICKER: 左タブ列＝カテゴリ（PROCESS/CONTROL）、右詳細＝コマンド種別一覧 */
    private fun buildPicker(state: GestureEditorState, player: Player): GestureGuiView {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        val categories = CommandCategory.entries
        categories.forEachIndexed { index, category ->
            val cy = 0.38 - index * 0.17
            val on = index == state.pickerCategory
            addBlock(visuals, "cat-bg-$index", -0.7975, cy, 0.47, 0.15,
                if (on) Material.CYAN_CONCRETE else Material.CYAN_TERRACOTTA, 4)
            addText(visuals, "cat-$index", -0.7975, cy - 0.02, 0.0055, 90,
                Component.text(KcI18n.text(player, category.labelKey)))
            elements.add(GestureGuiElement(
                elementId = "lower-cat:$index",
                bounds = rect(-0.7975, cy, 0.47, 0.15),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "cat-bg-$index",
            ))
        }
        val closeCy = 0.38 - categories.size * 0.17
        addBlock(visuals, "lower-close-bg", -0.7975, closeCy, 0.47, 0.15, Material.BROWN_CONCRETE, 4)
        addText(visuals, "lower-close", -0.7975, closeCy, 0.006, 90,
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_CLOSE)))
        elements.add(GestureGuiElement(
            elementId = "lower-close-picker",
            bounds = rect(-0.7975, closeCy, 0.47, 0.15),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            targetVisualId = "lower-close-bg",
        ))

        val category = categories[state.pickerCategory.coerceIn(0, categories.lastIndex)]
        val categoryDescription = KcI18n.list(player, category.descriptionKey)
            .filter(String::isNotBlank)
            .joinToString(" ")
        addText(visuals, "picker-description-title", 0.28, 0.43, 0.0047, 280,
            Component.text(KcI18n.text(player, category.labelKey)))
        addText(visuals, "picker-description-body", 0.28, 0.36, 0.0043, 280,
            Component.text(categoryDescription, NamedTextColor.GRAY))
        val script = plugin.scripts.load(state.scriptId)
        val mergeConditionId = state.pendingInsertion?.mergeConditionId
        // 候補表示とGraphEditorの実データ検証を同じ条件にし、ネスト未合流の外側へ
        // MERGEを表示してクリック時例外になる不一致を防ぎます。
        val mergeAvailable = script?.let { GraphEditor.canAppendMerge(it.graph, mergeConditionId) } == true
        // MERGEは分岐合流用の挿入先だけで候補化し、FOR_END等は単独挿入不可のため除外します。
        val types = CommandType.entries.filter { type ->
            CommandPresentationPolicy.category(type) == category &&
                (type != CommandType.MERGE || mergeAvailable) &&
                type != CommandType.FOR_END &&
                type != CommandType.FOR_START &&
                type != CommandType.BREAK &&
                type != CommandType.CONTINUE
        }
        val pageCount = ((types.size + PICKER_PAGE_SIZE - 1) / PICKER_PAGE_SIZE).coerceAtLeast(1)
        val page = state.pickerPage.coerceIn(0, pageCount - 1)
        types.drop(page * PICKER_PAGE_SIZE).take(PICKER_PAGE_SIZE).forEachIndexed { index, type ->
            val cx = if (index % 2 == 0) -0.11 else 0.65
            val cy = 0.20 - (index / 2) * 0.18
            addBlock(visuals, "type-bg-$index", cx, cy, 0.72, 0.155, Material.CYAN_TERRACOTTA, 4)
            addText(visuals, "type-$index", cx, cy - 0.02, 0.0055, 90,
                Component.text(KcI18n.text(player, type.key)))
            elements.add(GestureGuiElement(
                elementId = "lower-type:${type.name}",
                bounds = rect(cx, cy, 0.72, 0.155),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "type-bg-$index",
                hoverText = singleLineHover(
                    KcI18n.list(player, type.descriptionKey)
                        .filter(String::isNotBlank)
                        .joinToString(" "),
                    x = 0.28,
                    y = 0.39,
                ),
            ))
        }
        if (pageCount > 1) addPager(visuals, elements, "picker", page, pageCount, 0.28, -0.48)
        return view(GestureLowerMode.PICKER, elements, visuals)
    }

    /** CONFIRM: 子画面（上部エディターがopenChildで赤ガラスを重ねる） */
    private fun buildConfirm(state: GestureEditorState, player: Player): GestureGuiView {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        val overwrite = state.confirmKind == GestureConfirmKind.ITEM_OVERWRITE
        addText(
            visuals,
            "confirm-title",
            0.0,
            0.11,
            0.005,
            180,
            Component.text(KcI18n.text(
                player,
                if (overwrite) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_ITEM_OVERWRITE_TITLE
                else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_DELETE_TITLE,
            )),
        )
        addText(
            visuals,
            "confirm-warn",
            0.0,
            0.05,
            0.004,
            180,
            Component.text(KcI18n.text(
                player,
                if (overwrite) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_ITEM_OVERWRITE_WARN
                else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_DELETE_WARN,
            ), NamedTextColor.GRAY),
        )

        addBlock(visuals, "confirm-yes-bg", -0.27, -0.08, 0.48, 0.12, Material.RED_CONCRETE, 4)
        addText(
            visuals,
            "confirm-yes",
            -0.27,
            -0.08,
            0.004,
            100,
            Component.text(KcI18n.text(
                player,
                if (overwrite) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_OVERWRITE_YES
                else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE_CONFIRM_EXECUTE,
            )),
        )
        elements.add(GestureGuiElement(
            elementId = "confirm-delete",
            bounds = rect(-0.27, -0.08, 0.48, 0.12),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            targetVisualId = "confirm-yes-bg",
        ))
        addBlock(visuals, "confirm-no-bg", 0.27, -0.08, 0.48, 0.12, Material.CYAN_TERRACOTTA, 4)
        addText(visuals, "confirm-no", 0.27, -0.08, 0.004, 100,
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE_CONFIRM_CANCEL)))
        elements.add(GestureGuiElement(
            elementId = "confirm-cancel",
            bounds = rect(0.27, -0.08, 0.48, 0.12),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            targetVisualId = "confirm-no-bg",
        ))
        return view(GestureLowerMode.CONFIRM, elements, visuals)
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
                child -> SETTING_CHILD_SCREEN_ID
                else -> LOWER_SCREEN_ID
            },
            elements,
            access = GestureGuiAccess.OWNER_ONLY,
        ),
        visuals,
        panel = GestureGuiPanel(
            width = when {
                mode == GestureLowerMode.CONFIRM -> GestureEditorLayout.LOWER_W * 0.5
                child -> GestureEditorLayout.LOWER_W * SETTING_CHILD_SCALE
                else -> GestureEditorLayout.LOWER_W
            },
            height = when {
                mode == GestureLowerMode.CONFIRM -> GestureEditorLayout.LOWER_H * 0.5
                child -> GestureEditorLayout.LOWER_H * SETTING_CHILD_SCALE
                else -> GestureEditorLayout.LOWER_H
            },
            backgroundMaterial = Material.GRAY_CONCRETE,
            frameMaterial = Material.LIGHT_GRAY_CONCRETE,
        ),
        onAction = onAction,
    ).let { view -> if (child) scaleChildView(view) else view }

    /** 子画面のパネル寸法と内容座標を同じ縮尺で変換し、端の要素を欠落させません。 */
    private fun scaleChildView(view: GestureGuiView): GestureGuiView {
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
                if (available) Material.CYAN_CONCRETE else Material.GRAY_CONCRETE,
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
                    acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
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
    ) {
        visuals.add(GestureGuiVisual.Block(
            visualId = id,
            x = x, y = y,
            width = w, height = h,
            blockData = Bukkit.createBlockData(material),
            layer = layer,
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
    ) {
        visuals.add(GestureGuiVisual.Text(
            visualId = id,
            x = x, y = y,
            text = text,
            size = size,
            lineWidth = lineWidth,
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

    private companion object {
        const val SETTINGS_PAGE_SIZE = 4
        const val PICKER_PAGE_SIZE = 8
        // 2列×5行に収め、対象フィルター（10項目）を1画面で編集できます。
        // 下端の操作列とは0.08ブロック以上離し、ページャーの重なりも防ぎます。
        const val SETTING_CHOICE_PAGE_SIZE = 10
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
        const val CHILD_TAB_DESCRIPTION_Y = 0.29
        const val CHILD_HOVER_Y = 0.23
        const val CHILD_DETAIL_HINT_Y = 0.17
        const val CHILD_PAGER_Y = -0.34
        const val CHILD_BACK_WIDTH = 1.70
        const val ACTION_DESCRIPTION_Y = 0.36
        const val DEFAULT_HOVER_Y = 0.39
        /** 構造化モデルを壊さず、paramsへ文字列として保存できる項目だけを許可します。 */
        val DIALOG_EDITABLE_KEYS = setOf(
            "item", "count", "text", "stay", "ticks", "tags", "sound", "volume", "pitch",
            "effect", "level", "seconds", "intensity", "shakeType", "slot", "entity", "diskId", "name", "startValue",
            "endValue", "stepValue", "condition", "variable", "value",
        )
    }
}
