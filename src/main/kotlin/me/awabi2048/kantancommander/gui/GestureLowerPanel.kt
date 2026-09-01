package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccess
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccessPolicy
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
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.util.KcI18n
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
 * - CONFIRM: 上部エディターが子画面（openChild・赤ガラス）として開く
 *
 * 親画面は左タブ列＋右詳細の分割型、子画面は子画面全体を使う集中型です。
 * 座標は画面中央原点・ブロック単位です。
 */
class GestureLowerPanel(
    private val plugin: KantanCommanderPlugin,
    private val onAction: (GestureGuiActionContext) -> Unit = {},
    private val screenAccess: GestureGuiAccess = GestureGuiAccess.OWNER_ONLY,
    private val screenAccessPolicy: GestureGuiAccessPolicy? = null,
) {
    val LOWER_SCREEN_ID = "gesture-editor-lower"
    /** 個別設定専用。親の下部画面へモーダルに重ねます。 */
    val SETTING_CHILD_SCREEN_ID = "gesture-editor-setting-child"
    val CONFIRM_SCREEN_ID = "gesture-editor-confirm"

    /** 子画面の面積を親の50%にするための縦横縮尺です。 */
    private val SETTING_CHILD_SCALE = sqrt(0.5)

    fun build(
        state: GestureEditorState,
        player: Player,
        attention: GestureAttentionState = GestureAttentionState.EMPTY,
    ): GestureGuiView {
        return when (state.lowerMode) {
            GestureLowerMode.SETTINGS -> buildSettings(state, player, attention)
            GestureLowerMode.PICKER -> buildPicker(state, player)
            GestureLowerMode.SETTING_CHOICES -> buildSettingChoices(state, player, attention)
            GestureLowerMode.CONFIRM -> buildConfirm(state, player)
        }
    }

    /** 親のタブ列を継承せず、子画面全体で詳細設定を生成します。 */
    fun buildSettingChild(
        state: GestureEditorState,
        player: Player,
        attention: GestureAttentionState = GestureAttentionState.EMPTY,
    ): GestureGuiView =
        buildSettingChoices(state, player, attention, child = true)

    /** SETTINGS: 左タブ列＋固定操作、右詳細＝値表示と編集導線です。 */
    private fun buildSettings(
        state: GestureEditorState,
        player: Player,
        attention: GestureAttentionState,
    ): GestureGuiView {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        val script = plugin.scripts.load(state.scriptId)
        val node = state.selectedNodeId?.let { id -> script?.graph?.nodes?.get(id) }
        if (node == null) {
            return buildScriptSettings(state, player, script, attention)
        }

        val fields = CommandSettingsModel.visibleFields(node)
        if (fields.isEmpty()) {
            addText(visuals, "lower-hint", 0.28, 0.20, 0.010, 160, Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_NO_FIELDS)))
            return view(GestureLowerMode.SETTINGS, elements, visuals)
        }
        val attentionFields = attention.fieldKeysByNode[node.id].orEmpty()
        val pageCount = addSettingsNavigation(state, player, node, visuals, elements, attentionFields)
        val page = state.settingsPage.coerceIn(0, pageCount - 1)
        val pageStart = page * SETTINGS_PAGE_SIZE
        val tabs = fields.drop(pageStart).take(SETTINGS_PAGE_SIZE)
        val selectedAbsolute = state.settingsTab.coerceIn(0, fields.lastIndex)
        val selected = if (selectedAbsolute in pageStart until pageStart + tabs.size) selectedAbsolute - pageStart else 0
        val field = tabs[selected]
        val descriptor = CommandSettingsModel.descriptor(node, field.key)
        val settingContext = state.settingContext
            ?: CommandSettingContext(state.scriptId, node.id, descriptor.role)
        val displayLabel = KcI18n.text(player, field.label)
        val value = field.value(node).render(player)
        val settingScreen = gestureSettingScreenFor(descriptor.editor)
        val settingChoices = settingScreen?.let { screen ->
            settingTreeNodes(
                node,
                settingContext,
                screen,
                field.key,
                player,
                attentionFields,
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
            field,
            fallback = displayLabel,
            detailHint = selectedDetail
                ?.let { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_OPEN_FILTERS) },
            warning = attentionWarning(player, field.key in attentionFields),
        )
        addValueRow(visuals, "lower-setting", SETTING_VALUE_Y, displayLabel, value)

        if (settingScreen != null) {
            // 設定木の直下は常に親画面へ表示します。子画面は、選択中の
            // ノードがさらに子要素を持つ場合だけ、再クリックで開きます。
            addSettingChoiceNodes(settingChoices, player, visuals, elements, field.key)
        }
        // 「移動先→ほかのエンティティ」は親のSETTINGS画面から選択します。
        // 子画面側だけへ対象三分類を追加すると、最初の選択直後に親画面へ残る
        // 実際の表示経路では候補が消え、保存済みの対象設定も再編集できません。
        // 親画面でもposition:TARGETの選択状態を共通モデルから読み取り、子画面と
        // 同じ右下領域へ描画します。
        val destinationTarget = if (
            settingScreen == GestureSettingScreen.POSITION &&
            settingContext.role == CommandSettingRole.DESTINATION
        ) {
            settingChoices.firstOrNull {
                it.id == "position:${PositionKind.TARGET.name}" && it.selected
            }
        } else null
        destinationTarget?.let {
            addLowerRightTargetChoiceNodes(it.children, player, visuals, elements, field.key)
        }

        // 設定木の直下はこの親画面に直接表示します。葉の入力や、木に含まれない
        // 文字列・数値だけを右ペインのダイアログ導線へ残し、専用子画面を増やしません。
        val heldItemSetting = field.key == "item" && (node.type == CommandType.GIVE_ITEM ||
            (node.type == CommandType.ENTITY_ACTION && node.string("action", "ride") == "equip") ||
            (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.PLAYER_STATE.name))
        val heldBlockSetting = field.key == "block" && (
            node.type == CommandType.BLOCK_OPERATION ||
                (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.BLOCK_STATE.name)
            )
        val heldDiskSetting = field.key == "diskId" && node.type == CommandType.DISK_CALL
        val heldMainHandSetting = heldItemSetting || heldBlockSetting || heldDiskSetting
        val heldMainHandAvailable = when {
            heldItemSetting -> GestureGuiClickPolicy.hasMainHandItem(player)
            heldBlockSetting -> GestureGuiClickPolicy.hasMainHandItem(player) &&
                player.inventory.itemInMainHand.type.isBlock
            heldDiskSetting -> KantanItemService.diskId(player.inventory.itemInMainHand) != null
            else -> true
        }
        val dialogInputSetting = !heldMainHandSetting &&
            descriptor.editor == CommandSettingEditor.TEXT && field.key in DIALOG_EDITABLE_KEYS
        if (heldMainHandSetting || dialogInputSetting) {
            // ダイアログ入力欄は、既存 lower-edit（例:「待機する秒数を設定する」）
            // の位置を唯一の設定入口として再利用します。同じ枠・寸法・装飾で入力方法を
            // 表示し、上のアクション説明行や新しい配置へ設定導線を増やしません。
            val editVisualText = if (heldMainHandSetting) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_EDIT_FROM_MAINHAND
            } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_HOVER
            addBlock(
                visuals,
                "lower-edit-bg",
                0.28,
                0.02,
                1.2,
                0.16,
                if (heldMainHandAvailable) Material.CYAN_TERRACOTTA else DisabledGuiVisualPolicy.material,
                4,
            )
            addText(
                visuals,
                "lower-edit",
                0.28,
                0.02,
                0.0055,
                180,
                Component.text(KcI18n.text(player, editVisualText)),
            )
            elements.add(GestureGuiElement(
                elementId = "lower-edit:${field.key}",
                // すべてのダイアログ入力を既存のlower-edit枠へ集約します。
                // 説明行は意味を伝える表示専用であり、同じ設定を別の位置から
                // 開ける二重導線にはしません。
                bounds = rect(0.28, 0.02, 1.2, 0.16),
                // メインハンドの中身はview生成後にも変わるため、acceptedGesturesへ
                // 空集合を焼き付けず、クリック時点のガードで判定します。空手時は
                // 既存仕様どおり効果音・Actionを発生させず、保持時だけハンドラへ届けます。
                acceptedGestures = if (heldMainHandSetting) {
                    GestureGuiClickPolicy.MAIN_HAND
                } else GestureGuiClickPolicy.CLICK,
                gestureGuard = if (heldMainHandSetting) {
                    { actor, _ ->
                        if (heldDiskSetting) {
                            KantanItemService.diskId(actor.inventory.itemInMainHand) != null
                        } else if (heldBlockSetting) {
                            GestureGuiClickPolicy.hasMainHandItem(actor) &&
                                actor.inventory.itemInMainHand.type.isBlock
                        } else {
                            GestureGuiClickPolicy.hasMainHandItem(actor)
                        }
                    }
                } else null,
                targetVisualId = "lower-edit-bg",
                hoverText = singleLineHover(
                    KcI18n.text(
                        player,
                        if (heldMainHandSetting) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MAINHAND_SAVE_HOVER
                        else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_HOVER,
                    ),
                    x = HOVER_SLOT_X,
                    y = HOVER_SLOT_Y,
                ),
            ))
        }
        if (heldItemSetting) {
            val itemConfigured = node.string("item").isNotBlank() || node.string("itemData").isNotBlank()
            addBlock(
                visuals,
                "lower-item-get-bg",
                0.28,
                -0.15,
                1.2,
                0.12,
                if (itemConfigured) Material.BROWN_CONCRETE else DisabledGuiVisualPolicy.material,
                4,
            )
            addText(visuals, "lower-item-get", 0.28, -0.15, 0.0048, 180, Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_ITEM)))
            elements.add(GestureGuiElement(
                elementId = "lower-item-get",
                bounds = rect(0.28, -0.15, 1.2, 0.12),
                // 設定有無は外部保存や別経路の更新でも変わるため、表示時のBooleanを
                // acceptedGesturesへ固定せず、クリック時点で最新ノードを確認します。
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                gestureGuard = { _, _ ->
                    plugin.scripts.load(state.scriptId)?.graph?.nodes?.get(node.id)?.let { current ->
                        current.string("item").isNotBlank() || current.string("itemData").isNotBlank()
                    } == true
                },
                targetVisualId = "lower-item-get-bg",
                hoverText = singleLineHover(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_GET_ITEM_HOVER),
                    x = HOVER_SLOT_X,
                    y = HOVER_SLOT_Y,
                ),
            ))
        }
        return view(GestureLowerMode.SETTINGS, elements, visuals)
    }

    /**
     * ノード未選択時の中央パネルです。
     *
     * ノード選択を促すだけではプログラム全体の設定へ到達できず、空のグラフでは
     * 「何をすれば追加できるか」も分かりません。ここではプログラム名とタイマーを
     * 常設の編集項目として同じ中央領域へ置き、グラフが空の場合だけ追加操作の
     * 明示的な案内を表示します。これらはノード設定のタブ木とは別ドメインなので、
     * ノードを選択していないときにだけ表示します。
     */
    private fun buildScriptSettings(
        state: GestureEditorState,
        player: Player,
        script: me.awabi2048.kantancommander.model.DiskScript?,
        attention: GestureAttentionState = GestureAttentionState.EMPTY,
    ): GestureGuiView {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        val hint = if (script?.graph?.nodes?.isEmpty() == true) {
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_EMPTY_GRAPH_HINT
        } else {
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_SELECT_NODE_HINT
        }
        addText(
            visuals,
            "lower-hint",
            0.0,
            0.27,
            0.008,
            230,
            Component.text(KcI18n.text(player, hint)),
        )
        if (script != null) {
            val programNameState = if (script.name.isBlank()) {
                GestureSettingValueState.INITIAL
            } else {
                GestureSettingValueState.CONFIGURED
            }
            addScriptSetting(
                player = player,
                visuals = visuals,
                elements = elements,
                id = "lower-script-name",
                backgroundId = "lower-script-name-bg",
                x = -0.38,
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_PROGRAM_NAME),
                value = script.name.ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) },
                valueState = programNameState,
                material = Material.NAME_TAG,
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
                        mapOf("value" to script.timer.intervalSeconds),
                    )
            } else {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED)
            }
            addScriptSetting(
                player = player,
                visuals = visuals,
                elements = elements,
                id = "lower-script-timer",
                backgroundId = "lower-script-timer-bg",
                x = 0.38,
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TIMER),
                value = timerValue,
                valueState = timerState,
                material = Material.CLOCK,
                attention = attention.timer,
            )
        }
        return view(GestureLowerMode.SETTINGS, elements, visuals)
    }

    /** プログラム全体設定のカードと入力面を共通の寸法で生成します。 */
    private fun addScriptSetting(
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        id: String,
        backgroundId: String,
        x: Double,
        label: String,
        value: String,
        valueState: GestureSettingValueState,
        material: Material,
        attention: Boolean = false,
    ) {
        addBlock(
            visuals,
            backgroundId,
            x,
            -0.02,
            0.68,
            0.17,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.MULTIPLE,
                valueState,
            ),
            4,
            glowColor = GestureSettingVisualPolicy.glowColor(selected = false, attention = attention),
        )
        visuals.add(GestureGuiVisual.Item(
            visualId = "$id-icon",
            x = x - 0.27,
            y = -0.02,
            item = ItemStack(material),
            scale = 0.08,
            layer = 5,
        ))
        addText(visuals, "$id-label", x, -0.045, 0.0052, 120, Component.text(label))
        addText(visuals, "$id-value", x, -0.005, 0.0044, 170, Component.text(value))
        // アイテム名は表示の識別用に使わず、設定値は常にLore／別TextDisplayへ出します。
        // このカードも同じ規則に従い、クリック対象のNameを持たせず入力面だけを公開します。
        elements.add(GestureGuiElement(
            elementId = id,
            bounds = rect(x, -0.02, 0.68, 0.17),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            targetVisualId = backgroundId,
            // 赤カードは状態名をホバーでも示します（色だけの通知を避ける規則）。
            hoverText = if (attention) {
                singleLineHover(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_CONTEXT_INCOMPLETE),
                    x = HOVER_SLOT_X,
                    y = HOVER_SLOT_Y,
                )
            } else null,
        ))
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
        fieldKey: String,
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
                settingChoiceMaterial(choice),
                4,
                glowColor = GestureSettingVisualPolicy.glowColor(choice.selected, choice.attention),
            )
            addText(visuals, "setting-choice-label-$index", cx, cy - 0.012, 0.0045, 115, Component.text(choice.label))
            val hoverDescription = choice.description.takeIf(String::isNotBlank)
                ?: choiceDescription(player, choice, fieldKey)
            elements.add(GestureGuiElement(
                elementId = "lower-setting-choice:${choice.id}",
                bounds = rect(cx, cy, width, SETTING_CHOICE_HEIGHT),
                acceptedGestures = if (choice.enabled) {
                    GestureGuiClickPolicy.CLICK
                } else emptySet(),
                gestureGuard = if (choice.enabled) null else { _, _ -> false },
                targetVisualId = bgId,
                // ホバーはカード直下へ追従させ、固定スロットとの重なりを解消します。
                hoverText = hoverDescription?.let {
                    val hoverX = cx
                    val hoverY = cy - SETTING_CHOICE_HEIGHT / 2.0 - 0.04
                    if (child) {
                        singleLineHover(
                            it,
                            x = hoverX,
                            y = hoverY,
                            replacesVisualId = SETTING_DESCRIPTION_HOVER_ID,
                        )
                    } else {
                        singleLineHover(it, x = hoverX, y = hoverY)
                    }
                },
            ))
        }
    }

    /** 操作不能な設定候補は、候補の種類にかかわらず薄灰色コンクリートで表します。 */
    private fun settingChoiceMaterial(choice: GestureSettingTreeNode): Material =
        if (choice.enabled) {
            GestureSettingVisualPolicy.material(
                choice.selectionMode,
                choice.valueState,
                choice.selected,
                choice.attention,
            )
        } else {
            DisabledGuiVisualPolicy.material
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
        pagerCenterX: Double = -0.30,
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
            val fieldState = if (CommandSettingsModel.isFieldConfigured(node, field.key)) {
                GestureSettingValueState.CONFIGURED
            } else {
                GestureSettingValueState.INITIAL
            }
            val attention = field.key in attentionFields
            // テクスチャはボタンの種類、Glow は「選択中」と警告・要確認で使い分けます。
            addBlock(
                visuals,
                "tab-bg-$index",
                -0.7975,
                cy,
                0.47,
                0.15,
                GestureSettingVisualPolicy.material(
                    GestureSettingSelectionMode.EXCLUSIVE,
                    fieldState,
                ),
                4,
                glowColor = GestureSettingVisualPolicy.glowColor(on, attention),
            )
            addText(visuals, "tab-$index", -0.7975, cy - 0.02, 0.0055, 90,
                Component.text(KcI18n.text(player, field.label)))
            elements.add(GestureGuiElement(
                elementId = "lower-tab:${pageStart + index}",
                bounds = rect(-0.7975, cy, 0.47, 0.15),
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                targetVisualId = "tab-bg-$index",
                // 要確認タブは色だけでなく状態名も示します。ホバー中は操作説明欄を
                // 置き換えて警告を表示し、どのタブが未完了かを文面で伝えます。
                hoverText = if (attention) {
                    attentionWarningHover(player)
                } else null,
            ))
        }


        val deleteY = -0.43
        addBlock(visuals, "delete-bg", -0.7975, deleteY, 0.47, 0.10, Material.RED_CONCRETE, 4)
        addText(visuals, "delete-label", -0.7975, deleteY, 0.0049, 90,
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE)))
        elements.add(GestureGuiElement(
            elementId = "lower-delete",
            bounds = rect(-0.7975, deleteY, 0.47, 0.10),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
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
     * 常設説明は灰色1本に統一し、項目の説明（field_description）を表示します。
     * 従来の白い説明行と操作動詞行（field_action）は、内容が説明と重複するため
     * ジェスチャーGUIから外しました。候補や操作面のホバー説明は、この灰色
     * スロットをreplacesVisualIdで置き換えて同じ位置・寸法へ表示します。
     * 2行目は要確認の状態名、無ければ「詳細を持つ候補の再クリック」案内です。
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
        detailY: Double = SETTING_DETAIL_HINT_Y,
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
    private fun fieldDescription(player: Player, field: EditorField): String =
        KcI18n.list(player, field.descriptionKey)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { KcI18n.text(player, field.label) }

    /** 要確認時に説明欄へ出す状態名です。赤テクスチャと同じ判定から生成します。 */
    private fun attentionWarning(player: Player, attention: Boolean): String? =
        if (attention) {
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_CONTEXT_INCOMPLETE)
        } else {
            null
        }

    /** 要確認タブへホバーしたときの状態名ホバーです。操作説明欄を置き換えて表示します。 */
    private fun attentionWarningHover(player: Player): GestureGuiHoverText =
        singleLineHover(
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_CONTEXT_INCOMPLETE),
            x = HOVER_SLOT_X,
            y = HOVER_SLOT_Y,
        )

    /** 選択肢ごとの意味を、選択肢IDと明示対応させたカタログキーから生成します。 */
    private fun choiceDescription(player: Player, choice: SettingChoice, fieldKey: String?): String? = when {
        // 対象の三分類は、ラベルと同一の文面ではなく項目の説明を表示します。
        choice.id == "target:${TargetCategory.INHERITED.name}" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_INHERITED_TARGET)
        choice.id == "target:${TargetCategory.PLAYER.name}" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PLAYER_TARGET)
        choice.id == "target:${TargetCategory.NON_PLAYER_ENTITY.name}" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_ENTITY_TARGET)
        // 条件種別は、種別ごとの説明を選択肢IDから解決して表示します。
        choice.id.startsWith("condition-kind:") -> conditionKindDescription(player, choice.id)
        // コンテキスト系は、「コンテキスト」コマンドのタブ（後続への設定）と
        // コマンド限りの上書き（fieldKey == "context"）で性質が異なるため文面を分けます。
        choice.id == "context:executor" -> contextOverrideOrFieldDescription(
            player,
            fieldKey,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONTEXT_OVERRIDE_EXECUTOR,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EXECUTOR,
        )
        choice.id == "context:target" -> contextOverrideOrFieldDescription(
            player,
            fieldKey,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONTEXT_OVERRIDE_TARGET,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_CONTEXT_TARGET,
        )
        choice.id == "context:position" -> contextOverrideOrFieldDescription(
            player,
            fieldKey,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONTEXT_OVERRIDE_POSITION,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
        )
        choice.id == "context:facing" -> contextOverrideOrFieldDescription(
            player,
            fieldKey,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONTEXT_OVERRIDE_FACING,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_FACING,
        )
        choice.id == "context:source" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONTEXT_SOURCE)
        choice.id == "context:inherit" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONTEXT_INHERIT)
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
                PositionKind.DISK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_DISK
                PositionKind.EXECUTOR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_EXECUTOR
                PositionKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_TARGET
                PositionKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_MYWORLD_SPAWN
                PositionKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_COORDINATES
                PositionKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_POSITION_CAPTURED
                null -> null
            }
        }
        choice.id.startsWith("facing:") -> suffixKeyDescription(player, choice.id, "facing:") { suffix ->
            when (runCatching { FacingKind.valueOf(suffix) }.getOrNull()) {
                FacingKind.INHERITED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_INHERITED
                FacingKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_CAPTURED
                FacingKind.EXECUTOR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_EXECUTOR
                FacingKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_TARGET
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
            else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_SCOPE_TEMPORARY,
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
                "iteration" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VALUE_ITERATION
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
        // 終端の包含はfor（inclusiveEnd）と条件の反転（inverted）で意味が異なるため、
        // 編集中のタブ（fieldKey）へ文面を分けます。
        choice.id.startsWith("inclusive:") -> suffixKeyDescription(player, choice.id, "inclusive:") { suffix ->
            when {
                suffix == "true" && fieldKey == "inverted" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_INVERT_ON
                suffix == "false" && fieldKey == "inverted" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_INVERT_OFF
                suffix == "true" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_INCLUSIVE_END_TRUE
                suffix == "false" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_INCLUSIVE_END_FALSE
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
            null -> null
        }

    /** コンテキスト系の説明です。上書き画面（fieldKey == "context"）では、コマンド限りの上書きとして文面を分けます。 */
    private fun contextOverrideOrFieldDescription(
        player: Player,
        fieldKey: String?,
        overrideKey: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>,
        fieldDescriptionKey: com.awabi2048.ccsystem.api.localization.LocalizationKey<List<String>>,
    ): String =
        if (fieldKey == "context") {
            KcI18n.text(player, overrideKey)
        } else {
            fieldDescriptionText(player, fieldDescriptionKey)
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
     * ホバーは1行1エンティティで表示します。1つのTextDisplayへ複数行を
     * 入れると中央列揃えになり下部パネルのレイアウトが崩れるためです。
     * 親画面では常設の灰色説明と対になる画面下段のスロットへ表示し、
     * 説明を置き換えません。子画面のみ、従来どおり説明スロットの置換を
     * 使います（replacesVisualId）。
     */
    private fun singleLineHover(
        text: String,
        x: Double,
        y: Double,
        replacesVisualId: String? = null,
    ): GestureGuiHoverText =
        GestureGuiHoverText(
            text = Component.text(text),
            x = x,
            y = y,
            size = DESCRIPTION_TEXT_SIZE,
            lineWidth = 280,
            replacesVisualId = replacesVisualId,
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
        attentionFields: Set<String> = emptySet(),
    ): List<GestureSettingTreeNode> = rawSettingTreeNodes(node, context, screen, fieldKey, player)
        .map { decorateSettingChoice(node, context, fieldKey, it, attentionFields) }

    private fun rawSettingTreeNodes(
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
                    children = facingChoices(node, context.copy(role = CommandSettingRole.CONTEXT_FACING), player),
                )
                else -> choice
            }
        }
        else -> settingChoices(node, context, screen, fieldKey, player)
    }

    /**
     * 生の選択肢へドメインの選択方式と値状態を付与します。
     * 子要素も同じ規則で再帰的に装飾し、対象・位置・コンテキストの役割が
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
            "context:executor" -> context.copy(role = CommandSettingRole.CONTEXT_EXECUTOR)
            "context:target" -> context.copy(role = CommandSettingRole.CONTEXT_TARGET)
            "context:position" -> context.copy(role = CommandSettingRole.CONTEXT_POSITION)
            "context:facing" -> context.copy(role = CommandSettingRole.CONTEXT_FACING)
            "condition-target" -> context.copy(role = CommandSettingRole.NODE_TARGET)
            "condition-position" -> context.copy(role = CommandSettingRole.CONDITION_POSITION)
            "position:${PositionKind.TARGET.name}" -> context.copy(role = CommandSettingRole.DESTINATION)
            else -> context
        }
        val effectiveContext = when {
            parentId == "context:executor" -> context.copy(role = CommandSettingRole.CONTEXT_EXECUTOR)
            parentId == "context:target" -> context.copy(role = CommandSettingRole.CONTEXT_TARGET)
            parentId == "context:position" -> context.copy(role = CommandSettingRole.CONTEXT_POSITION)
            parentId == "condition-target" -> context.copy(role = CommandSettingRole.NODE_TARGET)
            parentId == "condition-position" -> context.copy(role = CommandSettingRole.CONDITION_POSITION)
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
            // 要確認状態は、この選択肢が属する設定タブが実行前検証で指されていれば付与します。
            // タブと同じfieldKey基準のため、色の意味が画面間でずれません。
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
     * 実行前検証はタブのfieldKey単位で要確認を指すため、選択肢カードも同じ基準で
     * 赤表示へ投影します。settingChoiceConfiguredと同じ選択肢IDの分類に倣います。
     */
    private fun settingChoiceTabFieldKeys(
        choiceId: String,
        fieldKey: String,
        role: CommandSettingRole?,
    ): Set<String> = when {
        choiceId.startsWith("target:") || choiceId.startsWith("kind:") || choiceId.startsWith("filter:") ->
            setOfNotNull(role?.tabFieldKey ?: "target")
        choiceId.startsWith("position:") -> setOfNotNull(role?.tabFieldKey ?: "position")
        choiceId.startsWith("facing:") -> setOfNotNull(role?.tabFieldKey ?: "facing")
        choiceId == "condition-kind" -> setOf("kind")
        choiceId.startsWith("condition-") -> setOf("condition")
        choiceId == "context:executor" -> setOf("executor")
        choiceId == "context:target" -> setOf("target")
        choiceId == "context:position" -> setOf("position")
        choiceId == "context:facing" -> setOf("facing")
        choiceId == "context:source" || choiceId == "context:inherit" -> setOf("context")
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
        choiceId.startsWith("source:") || choiceId.startsWith("inclusive:") -> setOf(fieldKey)
        else -> setOf(fieldKey)
    }

    private fun settingSelectionMode(choiceId: String): GestureSettingSelectionMode = when {
        choiceId.startsWith("filter:") -> GestureSettingSelectionMode.MULTIPLE
        choiceId in setOf(
            "context:executor",
            "context:target",
            "context:position",
            "context:facing",
        ) -> GestureSettingSelectionMode.MULTIPLE
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
            id.startsWith("target:") -> choice.selected &&
                CommandSettingsModel.targetCategory(CommandSettingsModel.targetSpec(node, context.role)?.kind) ==
                targetCategoryFromChoice(id)
            id.startsWith("kind:") -> choice.selected &&
                CommandSettingsModel.targetCategory(CommandSettingsModel.targetSpec(node, context.role)?.kind) != TargetCategory.INHERITED
            id.startsWith("filter:") -> CommandSettingsModel.isTargetFilterConfigured(
                node,
                context.role,
                id.removePrefix("filter:"),
            )
            id.startsWith("block:") -> choice.selected &&
                CommandSettingsModel.isFieldConfigured(node, "operation", context.role)
            id.startsWith("position:") -> choice.selected &&
                CommandSettingsModel.positionKind(node, context.role)?.name == id.removePrefix("position:")
            id.startsWith("facing:") -> choice.selected &&
                CommandSettingsModel.facingSpec(node, context.role)?.kind?.name == id.removePrefix("facing:")
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
            id == "context:executor" -> CommandSettingsModel.isFieldConfigured(
                node,
                "executor",
                CommandSettingRole.CONTEXT_EXECUTOR,
            )
            id == "context:target" -> CommandSettingsModel.isFieldConfigured(
                node,
                "target",
                CommandSettingRole.CONTEXT_TARGET,
            )
            id == "context:position" -> CommandSettingsModel.isFieldConfigured(
                node,
                "position",
                CommandSettingRole.CONTEXT_POSITION,
            )
            id == "context:facing" -> CommandSettingsModel.isFieldConfigured(
                node,
                "facing",
                CommandSettingRole.CONTEXT_FACING,
            )
            id == "context:source" -> CommandSettingsModel.contextSource(node) != ContextSource.BASE
            id == "context:inherit" -> !CommandSettingsModel.isFieldConfigured(node, "context")
            id.startsWith("condition-state") -> CommandSettingsModel.isFieldConfigured(node, "sneaking")
            id.startsWith("condition-variable") -> CommandSettingsModel.isFieldConfigured(node, "variable")
            id.startsWith("condition-operator") -> CommandSettingsModel.isFieldConfigured(node, "operator")
            id.startsWith("condition-value") -> CommandSettingsModel.isFieldConfigured(node, "value")
            id.startsWith("condition-block") -> CommandSettingsModel.isFieldConfigured(node, "block")
            id.startsWith("condition-item-data") -> CommandSettingsModel.isFieldConfigured(node, "itemData")
            id.startsWith("condition-item") -> CommandSettingsModel.isFieldConfigured(node, "item")
            id.startsWith("changeMode:") -> choice.selected && node.string("changeMode", "ASSIGN") == id.removePrefix("changeMode:")
            id.startsWith("equipmentSlot:") -> choice.selected && node.string("slot", "HAND") == id.removePrefix("equipmentSlot:")
            id.startsWith("overwrite:") -> choice.selected && node.boolean("overwrite") == id.removePrefix("overwrite:").toBoolean()
            id.startsWith("tagOperation:") -> choice.selected && node.string("tagOperation", "add") == id.removePrefix("tagOperation:")
            id.startsWith("shake:") -> choice.selected && node.string("shakeType", "positional") == id.removePrefix("shake:")
            id.startsWith("soundScope:") -> choice.selected && node.string("soundScope", "CONTEXT") == id.removePrefix("soundScope:")
            else -> choice.selected && CommandSettingsModel.isFieldConfigured(node, fieldKey, context.role)
        }
    }

    private fun targetCategoryFromChoice(id: String): TargetCategory? = when (id.removePrefix("target:")) {
        TargetCategory.INHERITED.name -> TargetCategory.INHERITED
        TargetCategory.PLAYER.name -> TargetCategory.PLAYER
        TargetCategory.NON_PLAYER_ENTITY.name -> TargetCategory.NON_PLAYER_ENTITY
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
        attention: GestureAttentionState = GestureAttentionState.EMPTY,
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
        val attentionFields = attention.fieldKeysByNode[node.id].orEmpty()
        if (!child) {
            addSettingsNavigation(state, player, node, visuals, elements, attentionFields, pagerCenterX = -0.30)
        }
        val field = CommandSettingsModel.visibleFields(node).firstOrNull { it.key == fieldKey }
        val fieldLabel = field?.let { KcI18n.text(player, it.label) } ?: fieldKey
        val fieldValue = field?.value?.invoke(node)?.render(player)
            ?: settingCurrentValue(node, context, screen, fieldKey, player)
        val choices = settingTreeNodes(node, context, screen, fieldKey, player, attentionFields).map { choice ->
            if (state.settingTreePath?.nodeIds?.lastOrNull() == choice.id) {
                choice.copy(selected = true)
            } else choice
        }
        val selectedDetail = choices.firstOrNull { it.selected && it.hasChildren }
        addDescriptionRows(
            visuals,
            player,
            field,
            fallback = fieldLabel,
            detailHint = selectedDetail?.let {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_OPEN_FILTERS)
            },
            warning = attentionWarning(player, fieldKey in attentionFields),
            centerX = if (child) 0.0 else 0.28,
            hoverY = if (child) CHILD_HOVER_Y else ACTION_DESCRIPTION_Y,
            detailY = if (child) CHILD_DETAIL_HINT_Y else SETTING_DETAIL_HINT_Y,
        )
        addValueRow(
            visuals,
            "setting-header",
            if (child) CHILD_HEADER_Y else SETTING_VALUE_Y,
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
            fieldKey,
            child = child,
        )
        val destinationTarget = if (!child && screen == GestureSettingScreen.POSITION &&
            context.role == CommandSettingRole.DESTINATION
        ) {
            choices.firstOrNull { it.id == "position:${PositionKind.TARGET.name}" && it.selected }
        } else null
        destinationTarget?.let {
            addLowerRightTargetChoiceNodes(it.children, player, visuals, elements, fieldKey)
        }
        if (pageCount > 1) addPager(
            visuals,
            elements,
            "setting",
            page,
            pageCount,
            if (child) 0.0 else 0.25,
            if (child) CHILD_PAGER_Y else -0.43,
        )
        addBackSetting(
            player,
            elements,
            visuals,
            child = child,
            centerX = if (child) 0.0 else 0.78,
            width = if (child) CHILD_BACK_WIDTH else 0.42,
        )
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
            SettingChoice("soundScope:CONTEXT", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_POSITION), node.string("soundScope", "CONTEXT") == "CONTEXT"),
            SettingChoice("soundScope:WORLD", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_WORLD_WIDE), node.string("soundScope", "CONTEXT") == "WORLD"),
        )
        GestureSettingScreen.VARIABLE_TYPE -> listOf(
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
                add(SettingChoice("value:iteration", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_ITERATION), node.string("value") == "\$current_iteration_value"))
                add(SettingChoice("value:count", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT), node.string("value") == "\$current_loop_count"))
            }
        }
        GestureSettingScreen.FOR_SOURCE -> listOf(
            SettingChoice("source:FIXED", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_VALUE), node.string(fieldKey, "FIXED") == "FIXED"),
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
            SettingChoice(
                "context:executor",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EXECUTOR),
                CommandSettingsModel.isFieldConfigured(node, "executor", CommandSettingRole.CONTEXT_EXECUTOR),
            ),
            SettingChoice(
                "context:target",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.CONTEXT_TARGET),
            ),
            SettingChoice(
                "context:position",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION),
                CommandSettingsModel.isFieldConfigured(node, "position", CommandSettingRole.CONTEXT_POSITION),
            ),
            SettingChoice(
                "context:facing",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING),
                CommandSettingsModel.isFieldConfigured(node, "facing", CommandSettingRole.CONTEXT_FACING),
            ),
            SettingChoice(
                "context:source",
                KcI18n.text(
                    player,
                    if (CommandSettingsModel.contextSource(node) == ContextSource.PREVIOUS) {
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTEXT_PREVIOUS
                    } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTEXT_BASE,
                ),
            ),
            SettingChoice(
                "context:inherit",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERIT_ALL),
                !CommandSettingsModel.isFieldConfigured(node, "context"),
            ),
        )
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

    /** 対象種別を木の親ノードとして表示し、詳細条件を子ノードへぶら下げます。 */
    private fun targetChoices(node: CommandNode, context: CommandSettingContext, player: Player): List<SettingChoice> {
        val current = CommandSettingsModel.targetSpec(node, context.role)?.kind
        val graph = plugin.scripts.load(context.scriptId)?.graph
        val choices = listOf(
            TargetCategory.INHERITED to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERITED_TARGET,
            TargetCategory.PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER,
            TargetCategory.NON_PLAYER_ENTITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY,
        )
        return choices.map { (category, label) ->
            val selected = CommandSettingsModel.targetCategoryMatches(current, category) &&
                (category != TargetCategory.INHERITED || current == TargetKind.INHERITED_TARGET)
            val enabled = graph?.let { CommandSettingsModel.targetCategoryAvailable(it, node.id, category) }
                ?: category != TargetCategory.INHERITED
            val effectiveKind = if (selected) current else CommandSettingsModel.defaultTargetKind(category)
            SettingChoice(
                id = "target:${category.name}",
                label = KcI18n.text(player, label),
                selected = selected,
                enabled = enabled,
                // 大分類を選んだ後の詳細は、既存の細分類を保持したまま表示します。
                // 継承は実行時に参照元を必要とするため、詳細項目を持ちません。
                children = if (category != TargetCategory.INHERITED &&
                    CommandSettingsModel.targetSupportsDetailedFilters(effectiveKind)
                ) {
                    targetFilterChoices(node, context, player, kindOverride = effectiveKind)
                } else emptyList(),
            )
        }
    }

    /**
     * 移動先の「他のエンティティ」を選んだときだけ、対象三分類を右下へ並べます。
     * 座標方式の選択肢と同じ親画面へ置くことで、方式を選ぶ→対象種別を選ぶ→
     * 同じ種別を再クリックして詳細、という導線を保ちます。カードは右ペインの
     * 選択領域を3等分し、設定タブとおよそ同じ寸法で配置します。
     */
    private fun addLowerRightTargetChoiceNodes(
        choices: List<GestureSettingTreeNode>,
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        fieldKey: String,
    ) {
        val span = POSITION_TARGET_CHOICE_SPAN_END_X - POSITION_TARGET_CHOICE_SPAN_START_X
        val width = (span - POSITION_TARGET_CHOICE_GAP * 2) / 3.0
        val pitch = width + POSITION_TARGET_CHOICE_GAP
        choices.take(3).forEachIndexed { index, choice ->
            val cx = POSITION_TARGET_CHOICE_SPAN_START_X + width / 2.0 + index * pitch
            val cy = POSITION_TARGET_CHOICE_Y
            val bgId = "position-target-choice-bg-$index"
            addBlock(
                visuals,
                bgId,
                cx,
                cy,
                width,
                POSITION_TARGET_CHOICE_HEIGHT,
                settingChoiceMaterial(choice),
                4,
            )
            addText(
                visuals,
                "position-target-choice-label-$index",
                cx,
                cy - 0.02,
                0.0055,
                90,
                Component.text(choice.label),
            )
            val hoverDescription = choice.description.takeIf(String::isNotBlank)
                ?: choiceDescription(player, choice, fieldKey)
            elements += GestureGuiElement(
                // 共通ハンドラがtarget:<category>を解釈するため、elementIdの接頭辞は
                // 通常の設定カードと統一します。
                elementId = "lower-setting-choice:${choice.id}",
                bounds = rect(cx, cy, width, POSITION_TARGET_CHOICE_HEIGHT),
                acceptedGestures = if (choice.enabled) GestureGuiClickPolicy.CLICK else emptySet(),
                gestureGuard = if (choice.enabled) null else { _, _ -> false },
                targetVisualId = bgId,
                hoverText = hoverDescription?.let {
                    singleLineHover(
                        it,
                        x = cx,
                        y = cy - POSITION_TARGET_CHOICE_HEIGHT / 2.0 - 0.04,
                    )
                },
            )
        }
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

    private fun positionChoices(node: CommandNode, context: CommandSettingContext, player: Player): List<SettingChoice> {
        val destination = context.role == CommandSettingRole.DESTINATION
        val current = CommandSettingsModel.positionKind(node, context.role)
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
            )
        }
        return choices.map { (kind, label) -> SettingChoice("position:${kind.name}", KcI18n.text(player, label), current == kind) }
    }

    private fun facingChoices(node: CommandNode, context: CommandSettingContext, player: Player): List<SettingChoice> {
        val current = CommandSettingsModel.facingSpec(node, context.role)?.kind
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
                SettingChoice("condition-position", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION), node.conditionPositionSpec != null),
                SettingChoice("condition-block", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK, node.string("block", "minecraft:air"))),
            )
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
        GestureSettingScreen.INCLUSIVE_END -> if (node.type == CommandType.CONDITION && fieldKey == "inverted") {
            KcI18n.text(
                player,
                if (node.boolean(fieldKey, false)) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_ON
                } else {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_OFF
                },
            )
        } else if (fieldKey == "inclusiveEnd") {
            KcI18n.text(
                player,
                if (node.boolean(fieldKey, true)) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INCLUSIVE_ON
                } else {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INCLUSIVE_OFF
                },
            )
        } else {
            node.string(fieldKey).ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }
        }
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

    /** PICKER: 左タブ列＝カテゴリ（EXECUTION/CONTROL）、右詳細＝コマンド種別一覧 */
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
                acceptedGestures = GestureGuiClickPolicy.CLICK,
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
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            targetVisualId = "lower-close-bg",
        ))

        val category = categories[state.pickerCategory.coerceIn(0, categories.lastIndex)]
        val categoryDescription = KcI18n.list(player, category.descriptionKey)
            .filter(String::isNotBlank)
            .joinToString(" ")
        // カテゴリ名の白行は左タブ列のラベルと重複するため廃止し、灰色の説明1本に
        // 統一します。コマンド種別カードのホバーは、この説明スロットを置き換えます。
        addText(visuals, "picker-description-body", 0.28, ACTION_DESCRIPTION_Y, DESCRIPTION_TEXT_SIZE, 280,
            Component.text(categoryDescription, NamedTextColor.GRAY))
        val script = plugin.scripts.load(state.scriptId)
        val mergeConditionId = state.pendingInsertion?.mergeConditionId
        // 候補表示とGraphEditorの実データ検証を同じ条件にし、ネスト未合流の外側へ
        // MERGEを表示してクリック時例外になる不一致を防ぎます。
        val mergeAvailable = script?.let { GraphEditor.canAppendMerge(it.graph, mergeConditionId) } == true
        // MERGEは分岐合流用の挿入先だけで候補化し、FOR_END等は単独挿入不可のため除外します。
        val insertionTarget = state.pendingInsertion
        val insideForBody = script?.graph?.let {
            GraphEditor.isInsideFor(
                it,
                insertionTarget?.sourceId,
                insertionTarget?.edge ?: GraphEditor.Edge.ENTRY,
            )
        } == true
        val types = CommandType.entries.filter { type ->
            CommandPresentationPolicy.category(type) == category &&
                (type != CommandType.MERGE || mergeAvailable) &&
                type != CommandType.FOR_END &&
                (type != CommandType.BREAK && type != CommandType.CONTINUE || insideForBody)
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
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                targetVisualId = "type-bg-$index",
                // コマンド種別の説明は、説明と対になる画面下段のスロットへ
                // 表示します。カテゴリ説明は常設のため置き換えません。
                hoverText = singleLineHover(
                    KcI18n.list(player, type.descriptionKey)
                        .filter(String::isNotBlank)
                        .joinToString(" "),
                    x = HOVER_SLOT_X,
                    y = PICKER_HOVER_SLOT_Y,
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
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            targetVisualId = "confirm-yes-bg",
        ))
        addBlock(visuals, "confirm-no-bg", 0.27, -0.08, 0.48, 0.12, Material.CYAN_TERRACOTTA, 4)
        addText(visuals, "confirm-no", 0.27, -0.08, 0.004, 100,
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE_CONFIRM_CANCEL)))
        elements.add(GestureGuiElement(
            elementId = "confirm-cancel",
            bounds = rect(0.27, -0.08, 0.48, 0.12),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            targetVisualId = "confirm-no-bg",
        ))
        // 確認画面も詳細設定と同じ子画面契約（面積比50%）で表示します。
        // ここだけ0.5倍の縦横指定にすると、面積比が0.25へ縮み、子画面ごとに
        // 視線距離と操作領域が変わってしまいます。
        return view(GestureLowerMode.CONFIRM, elements, visuals, child = true)
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
            access = screenAccess,
            accessPolicy = screenAccessPolicy,
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
    ) {
        visuals.add(GestureGuiVisual.Block(
            visualId = id,
            x = x, y = y,
            width = w, height = h,
            blockData = Bukkit.createBlockData(material),
            layer = layer,
            glowColor = glowColor,
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
        const val SETTING_DESCRIPTION_HOVER_ID = "setting-description-hover"
        /** 常設説明とホバー説明に共通する文字寸法です。置き換え時のサイズ変化を防ぎます。 */
        const val DESCRIPTION_TEXT_SIZE = 0.0043
        // 値行と詳細案内を0.10ブロック以上離し、長いTextDisplayの折返しが
        // 互いの領域へ侵入しないようにします。
        const val SETTING_VALUE_Y = 0.27
        const val SETTING_DETAIL_HINT_Y = 0.17
        const val SETTINGS_PAGE_SIZE = 4
        // PICKERは説明と対になる下段ホバースロットを確保するため、2列×3行へ縮小します。
        const val PICKER_PAGE_SIZE = 6
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
        const val CHILD_HOVER_Y = 0.23
        const val CHILD_DETAIL_HINT_Y = 0.14
        const val CHILD_PAGER_Y = -0.34
        const val CHILD_BACK_WIDTH = 1.70
        const val ACTION_DESCRIPTION_Y = 0.36
        // 親画面のホバー説明は、常設の灰色説明（ACTION_DESCRIPTION_Y）と対になる
        // 画面下段のスロットへ表示します。操作行（-0.43）や対象カード（-0.25）と
        // 重ならない位置です。
        const val HOVER_SLOT_X = 0.28
        const val HOVER_SLOT_Y = -0.35
        // PICKERの下段はページャー（0.28, -0.48）があるため、その上へ置きます。
        const val PICKER_HOVER_SLOT_Y = -0.38
        // 「ほかのエンティティ」の対象三分類は、右ペインの選択カード領域
        // （SETTING_CHOICE 2列と同じスパン）を3等分し、設定タブ（0.47×0.15）と
        // およそ同じ寸法で配置します。
        const val POSITION_TARGET_CHOICE_SPAN_START_X = -0.43
        const val POSITION_TARGET_CHOICE_SPAN_END_X = 1.00
        const val POSITION_TARGET_CHOICE_GAP = 0.04
        const val POSITION_TARGET_CHOICE_HEIGHT = 0.15
        const val POSITION_TARGET_CHOICE_Y = -0.25
        /** 構造化モデルを壊さず、paramsへ文字列として保存できる項目だけを許可します。 */
        val DIALOG_EDITABLE_KEYS = setOf(
            "item", "itemData", "count", "text", "subtitle", "customName", "tags", "tag", "sound", "soundParameters", "volume", "pitch",
            "effect", "level", "seconds", "fadeInSeconds", "staySeconds", "fadeOutSeconds", "intensity", "slot", "entity", "diskId", "name", "startValue",
            "endValue", "stepValue", "condition", "variable", "value", "block", "sneaking",
        )
    }
}
