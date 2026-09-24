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
import com.awabi2048.ccsystem.api.gesturegui.html.GestureGuiHtmlEnvironment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHover
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHoverTarget
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
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.ControlBlockStateKind
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityGroupOperation
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityPropertyValueKind
import me.awabi2048.kantancommander.model.EntityTagEntries
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.variableScope
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.PlaceItemDestination
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.StructureMirror
import me.awabi2048.kantancommander.model.StructureRotation
import me.awabi2048.kantancommander.model.hasDiskContent
import me.awabi2048.kantancommander.model.effectiveTrigger
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.declaredArguments
import me.awabi2048.kantancommander.model.displayKey
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
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.sqrt

/** 既存の選択肢生成を木のノードとして扱うための局所的な別名です。 */
private typealias SettingChoice = GestureSettingTreeNode

/**
 * 二値トグルの表示文言を組み立てます。
 *
 * 固定ラベルがある場合は「固定ラベル 状態」、ない場合は状態のみにします。
 * 状態リテラルは現行トグル表示と同一のON／OFFを使い、`< >` 等の装飾記号は
 * 言語キーへ含めず表示側の結合に留めます。
 */
internal fun checkboxToggleLabel(fixedLabel: String?, checked: Boolean): String {
    val state = if (checked) "ON" else "OFF"
    return if (fixedLabel.isNullOrBlank()) state else "$fixedLabel $state"
}

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
    val SETTING_CHILD_SCREEN_ID = GestureSettingChildScreenIds.ROOT
    val CONFIRM_SCREEN_ID = "gesture-editor-confirm"
    val TEST_CONFIRM_SCREEN_ID = "gesture-editor-test-confirm"

    /** 子画面の面積を親の50%にするための縦横縮尺です。宣言実装と共有します。 */
    internal val SETTING_CHILD_SCALE = sqrt(0.5)

    /** 設定木の経路長に対応する物理子画面IDです。ルート画面のIDは既存値を維持します。 */
    fun settingChildScreenIdForRoute(routeSize: Int): String =
        GestureSettingChildScreenIds.forRouteSize(routeSize)

    fun settingChildScreenIdForDepth(depth: Int): String =
        GestureSettingChildScreenIds.forDepth(depth)

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
            // issue #7: 起動方法の選択子画面です。子画面が閉じられた場合でも、
            // 親側へ選択肢の内部座標を誤描画しないようにします。
            GestureLowerMode.TRIGGER_KIND -> buildSettings(state, player, attention, suppressHighlight)
            GestureLowerMode.TRIGGER_EVENT -> buildSettings(state, player, attention, suppressHighlight)
            GestureLowerMode.TRIGGER_DETAIL -> buildSettings(state, player, attention, suppressHighlight)
            // issue #8: 呼び出し設定の子画面です。子画面が閉じられた場合でも、
            // 親側へ設定用の内部座標を誤描画しないようにします。
            GestureLowerMode.DISK_CALL_SETTINGS -> buildSettings(state, player, attention, suppressHighlight)
            // issue #8: ディスク設定の子画面です。子画面が閉じられた場合でも、
            // 親側へ設定用の内部座標を誤描画しないようにします。
            GestureLowerMode.DISK_SETTINGS -> buildSettings(state, player, attention, suppressHighlight)
            GestureLowerMode.CONFIRM -> buildConfirmNew(state, player)
            GestureLowerMode.TEST_CONFIRM -> buildTestConfirmationNew(state, player, child = true)
            GestureLowerMode.TEST_STATUS -> buildTestStatusNew(state, player)
            GestureLowerMode.TEST_RESULT -> buildTestResultNew(state, player)
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
     * 資源ID項目のプリセット一覧を親タブ直下へ直接配置します。
     *
     * 子画面遷移は行いません。表示は使用頻度の高い [ResourcePresets] 24件に限定し、
     * プリセット外の値は入力カードからのDialog入力と候補検索でのみ指定します。
     * 保存・検証の正本は全件のまま変えません。一覧を上、入力カードを下に置き、
     * 現在値がプリセット外のときは入力カードを選択済みとして扱います。
     * Nameへ生値を出しません。
     * グリッド位置は呼び出し側が項目行に合わせて指定します。
     */
    private fun appendInlineResourcePicker(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        player: Player,
        node: CommandNode,
        fieldKey: String,
        kind: GestureResourcePickerKind,
        gridTopY: Double,
        inputCx: Double,
        inputCy: Double,
        inputW: Double,
        gridLeftX: Double = INLINE_RESOURCE_GRID_LEFT_X,
        columns: Int = INLINE_RESOURCE_COLUMNS,
        cellW: Double = INLINE_RESOURCE_CELL_W,
        cellH: Double = INLINE_RESOURCE_CELL_H,
        pitchX: Double = INLINE_RESOURCE_GRID_PITCH_X,
        pitchY: Double = INLINE_RESOURCE_GRID_PITCH_Y,
    ) {
        val ids = ResourcePresets.ids(kind)
        val valueFieldKey = ResourcePicker.valueFieldKey(fieldKey)
        val currentRaw = CommandDialogSpecs.normalize(valueFieldKey, node.string(valueFieldKey))
        // プリセット外の現在値は入力カード側を選択済みにします。
        val isCurrentPreset = ResourcePresets.isPreset(kind, currentRaw)
        // グリッド全体を1マス高さの半分だけ上げ、最底辺を詰めます。
        // 入力カード位置は変えず、間の余白を広げる方向です。
        val topY = gridTopY + cellH / 2
        // ホバー文言は一覧の上側へ、1マス高さの1/3＋1/2だけ上げた位置へ表示します。
        val hoverY = topY + cellH * 2 / 3 + INLINE_RESOURCE_HOVER_ABOVE_GAP
        val cards = ids.mapIndexed { index, id ->
            val selected = CommandDialogSpecs.normalize(valueFieldKey, id) == currentRaw ||
                id == node.string(valueFieldKey)
            val col = index % columns
            val row = index / columns
            me.awabi2048.kantancommander.gui.declarative.ResourcePickerCard(
                elementId = InlineResourcePicker.pickElementId(fieldKey, id),
                icon = ItemStack(ResourcePickerIcons.resolve(kind, id)),
                background = Bukkit.createBlockData(
                    if (selected) Material.CYAN_TERRACOTTA else Material.LIGHT_GRAY_CONCRETE,
                ),
                outline = if (selected) {
                    GestureSettingVisualPolicy.nonTabOutlineMaterial(true)?.let { Bukkit.createBlockData(it) }
                } else null,
                cx = gridLeftX + col * pitchX,
                cy = topY - row * pitchY,
                w = cellW,
                h = cellH,
                // 翻訳名は表示せず、実際に指定するコマンド用IDだけを1行で示します。
                hover = singleLineHover(
                    ResourcePresetDisplay.commandId(kind, id),
                    x = HOVER_SLOT_X,
                    y = hoverY,
                ),
                visualIndex = index,
            )
        }
        DeclarativeSettings.resourcePickerSection(nodes, frame, cards)
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = InlineResourcePicker.inputElementId(fieldKey),
                label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_RESOURCE_ID_INPUT)),
                background = Bukkit.createBlockData(
                    if (!isCurrentPreset && currentRaw.isNotBlank()) Material.CYAN_TERRACOTTA else Material.CYAN_CONCRETE,
                ),
                cx = inputCx,
                cy = inputCy,
                w = inputW,
                h = SETTING_INPUT_HEIGHT,
                labelSize = 0.0048,
                labelWidth = 120,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = singleLineHover(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_RESOURCE_ID_INPUT_HOVER),
                    x = HOVER_SLOT_X,
                    y = hoverY,
                ),
                visualTag = "lower-inline-input",
                outline = if (!isCurrentPreset && currentRaw.isNotBlank()) {
                    GestureSettingVisualPolicy.nonTabOutlineMaterial(true)?.let { Bukkit.createBlockData(it) }
                } else null,
            ),
        )
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
        val childScreenId = settingChildScreenIdForRoute(state.settingRoute.size)
        val noFields = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_NO_FIELDS))
        val backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK))
        if (context == null || node == null || fieldKey == null || screen == null) {
            return finishSettingChoicesChildHtml(
                noFieldsHint = noFields,
                backLabel = backLabel,
                childScreenId = childScreenId,
            )
        }
        val attentionFields = attention.fieldKeysByNode[node.id].orEmpty()
        val field = CommandSettingsModel.gestureVisibleFields(node).firstOrNull { it.key == fieldKey }
        if (field == null) {
            return finishSettingChoicesChildHtml(
                noFieldsHint = noFields,
                backLabel = backLabel,
                childScreenId = childScreenId,
            )
        }
        val fieldLabel = KcI18n.text(player, field.label)
        val fieldValue = if (CommandSettingsModel.isFieldConfigured(node, fieldKey, context.role)) {
            displayValueComponent(player, field.value(node))
        } else {
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET))
        }
        val pathLast = state.settingTreePath?.nodeIds?.lastOrNull()
        // タグ一覧はワールド変数子画面と同型の専用描画を使います。
        if (screen == GestureSettingScreen.ENTITY_TAG_LIST) {
            return buildEntityTagListChild(state, player, childScreenId)
        }
        // スポーン種別の子画面では移動先選択の再掲ではなく、メンバー／ゲストの2択を示します。
        val choices = if (screen == GestureSettingScreen.POSITION &&
            pathLast == "position-preset:${SettingPresets.PositionPreset.MYWORLD_SPAWN.name}"
        ) {
            applyPathSelectionExclusive(
                myworldSpawnChoices(
                    CommandSettingsModel.positionSpec(node, context.role)?.myworldSpawn,
                    player,
                ),
                pathLast,
            )
        } else {
            // issue #29: 子画面ではエンティティ導線の再入場を隠し、往復ループを作りません。
            applyPathSelectionExclusive(
                settingTreeNodes(
                    node,
                    context,
                    screen,
                    fieldKey,
                    player,
                    attentionFields,
                    inChild = true,
                    detailVisited = state.detailVisitedKeys.contains(
                        PresetSelectionDisplay.detailVisitKey(fieldKey, context.role),
                    ),
                ),
                pathLast,
            )
        }
        val selectedDetail = choices.firstOrNull { it.selected && it.hasChildren }
        val warning = attentionWarning(player, node, fieldKey, attentionFields)
        val detailHint = selectedDetail
            ?.let { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_OPEN_FILTERS) }
        val detail = when {
            !warning.isNullOrBlank() -> Component.text(warning, NamedTextColor.RED)
            !detailHint.isNullOrBlank() -> Component.text(detailHint, NamedTextColor.GRAY)
            else -> null
        }
        // HTML側でも候補の意味的な上下揃えを保ち、戻る操作の領域を先に予約します。
        // 下揃え候補はページを移動しても常に見えるため、詳細への経路を失いません。
        val topChoices = choices.filterNot { it.bottomAligned }
        val bottomChoices = choices.filter { it.bottomAligned }
        val page = GestureSettingChildLayoutPolicy.plan(
            topChoiceCount = topChoices.size,
            bottomChoiceCount = bottomChoices.size,
            requestedPage = state.settingChoicePage,
        )
        val pageChoices = topChoices.subList(page.topStart, page.topEndExclusive)
        val topCards = pageChoices.mapIndexed { index, choice ->
            val row = index / 2
            settingChoiceCard(
                choice = choice,
                fieldKey = fieldKey,
                player = player,
                child = true,
                suppressHighlight = suppressHighlight,
                cx = if (index % 2 == 0) -0.53 else 0.53,
                cy = GestureSettingChildLayoutPolicy.topY(row),
                row = row,
                rowCount = page.topRowCount,
                choicesInRowGroup = pageChoices.size,
                visualIndex = index,
            )
        }
        val bottomCards = bottomChoices.mapIndexed { index, choice ->
            val row = index / 2
            settingChoiceCard(
                choice = choice,
                fieldKey = fieldKey,
                player = player,
                child = true,
                suppressHighlight = suppressHighlight,
                cx = if (index % 2 == 0) -0.53 else 0.53,
                cy = GestureSettingChildLayoutPolicy.bottomY(row, page.bottomRowCount),
                row = row,
                rowCount = page.bottomRowCount,
                choicesInRowGroup = bottomChoices.size,
                visualIndex = topCards.size + index,
            )
        }
        return finishSettingChoicesChildHtml(
            fieldLabel = fieldLabel,
            fieldValue = fieldValue,
            backLabel = backLabel,
            description = Component.text(fieldDescription(player, field), NamedTextColor.GRAY),
            detail = detail,
            cards = topCards + bottomCards,
            page = page.index,
            pageCount = page.count,
            childScreenId = childScreenId,
        )
    }

    /**
     * 選択子画面をHTMLテンプレートで組み立てます。
     * 文言Component、素材、ホバー、操作可否は環境解決へ渡し、配置と予約寸法はHTMLへ置きます。
     */
    private fun finishSettingChoicesChildHtml(
        noFieldsHint: Component? = null,
        fieldLabel: String? = null,
        fieldValue: Component? = null,
        backLabel: Component,
        description: Component? = null,
        detail: Component? = null,
        cards: List<SettingsChoiceCard> = emptyList(),
        page: Int = 0,
        pageCount: Int = 1,
        childScreenId: String = SETTING_CHILD_SCREEN_ID,
    ): GestureGuiView {
        val panel = GestureGuiPanel()
        val frame = contentFrame(panel)
        val components = mutableMapOf<String, Component>()
        val backgrounds = mutableMapOf<String, BlockData>()
        val outlines = mutableMapOf<String, BlockData>()
        val hovers = mutableMapOf<String, GestureGuiHover>()
        val guards = mutableMapOf<String, (Player, GestureGuiGesture) -> Boolean>()
        val html = buildString {
            append("<section id=\"setting-child-root\" style=\"width: 100%; height: 100%;\">")
            fun text(
                id: String,
                component: Component,
                cx: Double,
                cy: Double,
                width: Double,
                height: Double,
                size: Double,
                lineWidth: Int,
            ) {
                components[id] = component
                append("<p id=\"${htmlEscape(id)}\" style=\"${absoluteStyle(frame, cx, cy, width, height)}; font-size: $size; line-width: $lineWidth; text-align: center;\">_</p>")
            }
            fun button(
                id: String,
                label: Component,
                background: BlockData,
                cx: Double,
                cy: Double,
                width: Double,
                height: Double,
                textSize: Double,
                lineWidth: Int,
                labelDy: Double = 0.0,
                outline: BlockData? = null,
                hover: GestureGuiHover? = null,
                enabled: Boolean = true,
            ) {
                components[id] = label
                backgrounds[id] = background
                outline?.let { outlines[id] = it }
                hover?.let { hovers[id] = it }
                if (!enabled) guards[id] = { _, _ -> false }
                val border = if (outline == null) "" else " border: 0.06;"
                val offset = if (labelDy == 0.0) "" else " text-offset-y: $labelDy;"
                append("<button id=\"${htmlEscape(id)}\" action=\"${htmlEscape(id)}\" gestures=\"click\" style=\"${absoluteStyle(frame, cx, cy, width, height)}; background: ${materialName(background)}; font-size: $textSize; line-width: $lineWidth;$border$offset\">_</button>")
            }

            if (noFieldsHint != null) {
                text("setting-hint", noFieldsHint, 0.0, 0.12, 1.0, 0.04, 0.010, 160)
            } else {
                val label = requireNotNull(fieldLabel)
                val value = requireNotNull(fieldValue)
                text("setting-header-label", Component.text(label), -0.53, CHILD_HEADER_Y, 0.86, 0.03, 0.0060, 120)
                text("setting-header-value", value, 0.53, CHILD_HEADER_Y, 0.86, 0.03, 0.0060, 170)
                text(
                    SETTING_DESCRIPTION_HOVER_ID,
                    requireNotNull(description),
                    0.0,
                    CHILD_HOVER_Y,
                    1.80,
                    0.03,
                    DESCRIPTION_TEXT_SIZE,
                    280,
                )
                detail?.let {
                    text("setting-description-detail", it, 0.0, CHILD_DETAIL_HINT_Y, 1.80, 0.025, 0.0041, 280)
                }
                cards.forEach { card ->
                    val hover = card.hover?.let { source ->
                        DeclarativeSettings.hoverSpecOf(source).copy(
                            replacement = if (card.replacesDescription) {
                                GestureGuiHoverTarget.Node(SETTING_DESCRIPTION_HOVER_ID)
                            } else {
                                GestureGuiHoverTarget.None
                            },
                        )
                    }
                    button(
                        id = card.elementId,
                        label = card.label,
                        background = card.background,
                        cx = card.cx,
                        cy = card.cy,
                        width = card.w,
                        height = card.h,
                        textSize = card.labelSize,
                        lineWidth = card.labelWidth,
                        labelDy = card.labelDy,
                        outline = card.outline,
                        hover = hover,
                        enabled = card.enabled,
                    )
                }
            }

            if (pageCount > 1) {
                val pagerY = CHILD_FOOTER_Y
                val arrowData = listOf(page - 1, page + 1).map { targetPage ->
                    Bukkit.createBlockData(if (targetPage in 0 until pageCount) Material.CYAN_CONCRETE else DisabledGuiVisualPolicy.material)
                }
                val glyphs = listOf(page - 1, page + 1).map { targetPage ->
                    Component.text(if (targetPage < page) "◀" else "▶").color(
                        if (targetPage in 0 until pageCount) NamedTextColor.WHITE else NamedTextColor.GRAY,
                    )
                }
                val arrowCenters = listOf(-0.68, -0.12)
                listOf(page - 1, page + 1).forEachIndexed { index, targetPage ->
                    val actionId = if (targetPage in 0 until pageCount) {
                        "lower-setting-choice-page:$targetPage"
                    } else {
                        "lower-setting-choice-page:disabled-$index"
                    }
                    button(
                        id = actionId,
                        label = glyphs[index],
                        background = arrowData[index],
                        cx = arrowCenters[index],
                        cy = pagerY,
                        width = 0.16,
                        height = 0.10,
                        textSize = 0.005,
                        lineWidth = 60,
                        enabled = targetPage in 0 until pageCount,
                    )
                }
                text("setting-choice-page-status", Component.text("${page + 1}/$pageCount"), -0.40, pagerY, 0.18, 0.03, 0.004, 80)
            }

            val backX = if (pageCount > 1) 0.56 else 0.0
            val backWidth = if (pageCount > 1) 0.78 else CHILD_BACK_WIDTH
            button(
                id = "lower-setting-back",
                label = backLabel,
                background = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                cx = backX,
                cy = CHILD_BACK_Y,
                width = backWidth,
                height = 0.10,
                textSize = 0.0048,
                lineWidth = 100,
            )
            append("</section>")
        }
        val environment = object : GestureGuiHtmlEnvironment {
            override fun textComponent(text: String, tag: String, classes: Set<String>, id: String?): Component =
                id?.let(components::get) ?: Component.text(text)

            override fun buttonBackground(background: String?, classes: Set<String>, id: String?): BlockData =
                id?.let(backgrounds::get) ?: background?.let { Material.matchMaterial(it.uppercase()) }
                    ?.let { Bukkit.createBlockData(it) }
                ?: error("HTML setting button background is not registered: $id / $background")

            override fun containerBackground(background: String, classes: Set<String>, id: String?): BlockData? = null

            override fun outlineBlock(classes: Set<String>, id: String?): BlockData? = id?.let(outlines::get)

            override fun blockData(material: String, classes: Set<String>, id: String?): BlockData =
                id?.let(backgrounds::get) ?: Material.matchMaterial(material.uppercase())
                    ?.let { Bukkit.createBlockData(it) } ?: error("unknown HTML block material: $material")

            override fun itemStack(reference: String, classes: Set<String>, id: String?): ItemStack =
                error("setting-choice HTML does not contain item references: $reference")

            override fun textSize(tag: String, classes: Set<String>, id: String?): Double = 0.005

            override fun hover(tag: String, classes: Set<String>, id: String?): GestureGuiHover? = id?.let(hovers::get)

            override fun gestureGuard(
                tag: String,
                classes: Set<String>,
                id: String?,
            ): ((Player, GestureGuiGesture) -> Boolean)? = id?.let(guards::get)
        }
        val compiled = KantanLayoutBridge.compileHtml(
            html = html,
            environment = environment,
            onAction = onAction,
            documentName = "kantan/setting-choices-child.html",
            panel = panel,
            onDiagnostics = { diagnostics ->
                plugin.logger.fine("[KantanCommander][SettingChoicesHtml] $diagnostics")
            },
        )
        return view(
            GestureLowerMode.SETTING_CHOICES,
            compiled.elements,
            compiled.visuals,
            child = true,
            childScreenId = childScreenId,
        )
    }

    private fun absoluteStyle(frame: ContentFrame, cx: Double, cy: Double, width: Double, height: Double): String =
        "position: absolute; left: ${cx - width / 2.0 - frame.minX}; top: ${frame.maxY - cy - height / 2.0}; width: $width; height: $height"

    private fun materialName(blockData: BlockData): String = blockData.material.name.lowercase()

    private fun htmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

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
            // 実行位置タブはタブ列から抽出し、下端の操作行の直上へ固定配置します。
            executionTabIndex = fields.indexOfFirst { it.key == "executionPosition" }.takeIf { it >= 0 },
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
        // 初期時点では全項目を未設定とするため、明示設定のない現在値欄は未設定表示にします。
        // 実行時の既定フォールバックは変えず、表示と完了状態だけを未完了扱いにします。
        val fieldValue = if (CommandSettingsModel.isFieldConfigured(node, fieldKey, context.role)) {
            displayValueComponent(player, field.value(node))
        } else {
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET))
        }
        // 経路末尾の選択は同階層へ排他的に適用し、保存値由来との二重選択を防ぎます。
        val choices = applyPathSelectionExclusive(
            settingTreeNodes(
                node,
                context,
                screen,
                fieldKey,
                player,
                attentionFields,
                detailVisited = state.detailVisitedKeys.contains(
                    PresetSelectionDisplay.detailVisitKey(fieldKey, context.role),
                ),
            ),
            state.settingTreePath?.nodeIds?.lastOrNull(),
        )
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
                plugin.logger.fine("[KantanCommander][DeclarativeSettingChoicesParent] $diagnostics")
            },
        )
        return view(GestureLowerMode.SETTING_CHOICES, compiled.elements, compiled.visuals, child = false)
    }

    /** ワールド変数一覧の子画面を生成します（宣言実装）。 */
    fun buildWorldVariablesChild(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView = buildWorldVariablesNew(state, player)

    /** タグ一覧の子画面を生成します（宣言実装）。 */
    fun buildEntityTagListChild(
        state: GestureEditorState,
        player: Player,
        childScreenId: String = settingChildScreenIdForRoute(state.settingRoute.size),
    ): GestureGuiView {
        val context = state.settingContext
        val node = context?.let { plugin.scripts.load(it.scriptId)?.graph?.nodes?.get(it.nodeId) }
        val entries = node?.let { EntityTagEntries.parse(it.string(EntityTagEntries.PARAM_ENTRIES)) }.orEmpty()
        val pageSize = TAG_PAGE_SIZE
        val pageCount = ((entries.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = state.tagPage.coerceIn(0, pageCount - 1)
        return finishTagListChild(
            me.awabi2048.kantancommander.gui.declarative.DeclarativeTagEntries.tagDocument(
                me.awabi2048.kantancommander.gui.declarative.TagListData(
                    title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG_ENTRIES)),
                    description = tagListDescription(player),
                    emptyLabel = if (entries.isEmpty()) {
                        Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET))
                    } else null,
                    entries = entries.drop(page * pageSize).take(pageSize).map { entry ->
                        val adding = entry.mode == EntityTagEntries.MODE_ADD
                        val modeText = KcI18n.text(
                            player,
                            if (adding) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ADD
                            else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REMOVE,
                        )
                        me.awabi2048.kantancommander.gui.declarative.TagListEntry(
                            tag = entry.tag,
                            modeGlyph = Component.text(if (adding) "＋" else "－", NamedTextColor.WHITE),
                            modeBackground = Bukkit.createBlockData(
                                if (adding) Material.GREEN_CONCRETE else Material.ORANGE_CONCRETE,
                            ),
                            hover = singleLineHover(
                                labeled(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG_OPERATION, modeText),
                                0.0,
                                CHILD_HOVER_Y,
                                SETTING_DESCRIPTION_HOVER_ID,
                            ),
                            modeHover = singleLineHover(
                                labeled(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG_OPERATION, modeText),
                                0.0,
                                CHILD_HOVER_Y,
                                SETTING_DESCRIPTION_HOVER_ID,
                            ),
                            deleteHover = singleLineHover(
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TAG_DELETE),
                                0.0,
                                CHILD_HOVER_Y,
                                SETTING_DESCRIPTION_HOVER_ID,
                            ),
                        )
                    },
                    // 行自体が入力であるため縁は一律空色とし、選択ハイライトは付けません。
                    entryBackground = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                    edgeBackground = Bukkit.createBlockData(Material.LIGHT_BLUE_CONCRETE),
                    deleteBackground = Bukkit.createBlockData(Material.RED_CONCRETE),
                    page = page,
                    pageCount = pageCount,
                    pagerAvailableBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                    pagerDisabledBackground = Bukkit.createBlockData(DisabledGuiVisualPolicy.material),
                    createLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TAG_ADD)),
                    createBackground = Bukkit.createBlockData(Material.GREEN_CONCRETE),
                    backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                    backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                ),
            ),
            childScreenId = childScreenId,
        )
    }

    /** タグ一覧子画面の説明文です。言語キーの複数行をそのまま重ねます。 */
    private fun tagListDescription(player: Player): Component =
        KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TAGS)
            .foldIndexed(Component.empty()) { index, result, line ->
                if (index == 0) Component.text(line) else result.append(Component.newline()).append(Component.text(line))
            }

    /** タグ一覧子画面の宣言区画を解決・変換し、既存の子画面定義へ載せます。 */
    private fun finishTagListChild(
        document: GestureGuiDocument,
        tag: String = "[DeclarativeTagList]",
        childScreenId: String = SETTING_CHILD_SCREEN_ID,
    ): GestureGuiView {
        val compiled = KantanLayoutBridge.compile(
            document,
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.fine("[KantanCommander]$tag $diagnostics")
            },
        )
        return view(
            GestureLowerMode.SETTING_CHOICES,
            compiled.elements,
            compiled.visuals,
            child = true,
            childScreenId = childScreenId,
        )
    }

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
                plugin.logger.fine("[KantanCommander]$tag $diagnostics")
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
                    options = ValueType.entries.map { type ->
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
            "[DeclarativeValueType]",
        )
    }

    /**
     * issue #7 の起動方法の種別選択子画面を生成します。
     *
     * 変更前の選択カード配置を維持します。種別クリックで次の操作へ進みます。
     */
    fun buildTriggerKindChild(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView {
        val script = plugin.scripts.load(state.scriptId)
        val currentTrigger = script?.effectiveTrigger()
        val current = currentTrigger?.kind
        return finishVariablesChild(
            me.awabi2048.kantancommander.gui.declarative.DeclarativeTriggers.kindDocument(
                me.awabi2048.kantancommander.gui.declarative.TriggerChoiceData(
                    title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TRIGGER)),
                    description = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TRIGGER_BODY)),
                    options = TriggerKind.entries.map { kind ->
                        me.awabi2048.kantancommander.gui.declarative.TriggerChoiceOption(
                            key = kind.name,
                            label = Component.text(TriggerDisplay.kindName(player, kind)),
                            selected = current == kind,
                            background = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                            outline = GestureSettingVisualPolicy.nonTabOutlineMaterial(current == kind)?.let {
                                Bukkit.createBlockData(it)
                            },
                        )
                    },
                    backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                    backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                ),
            ),
            GestureLowerMode.TRIGGER_KIND,
            "[DeclarativeTriggerKind]",
        )
    }

    /**
     * issue #8 の呼び出し設定子画面を生成します。
     *
     * 引数の値（リテラルまたは変数参照）と戻り値の受取先を一画面で編集します。
     * 引数は3件ずつページングし、カード選択でDialogへ進みます。
     */
    fun buildDiskCallSettingsChild(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView {
        val script = plugin.scripts.load(state.scriptId)
        val node = state.selectedNodeId?.let { id -> script?.graph?.nodes?.get(id) }
        val arguments = node?.declaredArguments().orEmpty()
        val pageSize = 3
        val pageCount = ((arguments.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = state.diskCallArgsPage.coerceIn(0, pageCount - 1)
        val returnName = node?.string("returnName").orEmpty()
        val returnScope = node?.string("returnScope").orEmpty().ifBlank { VariableScope.EXECUTION.name }
        return finishVariablesChild(
            me.awabi2048.kantancommander.gui.declarative.DeclarativeDiskCall.settingsDocument(
                me.awabi2048.kantancommander.gui.declarative.DiskCallSettingsData(
                    title = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DISK_CALL_SETTINGS),
                    ),
                    body = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_BODY),
                    ),
                    emptyArgs = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_NO_ARGS),
                    ),
                    arguments = arguments
                        .drop(page * pageSize)
                        .take(pageSize)
                        .map { argument ->
                            val variable = node?.string("argvar_${argument.name}")?.takeIf(String::isNotBlank)
                            val literal = node?.string("arg_${argument.name}")?.takeIf(String::isNotBlank)
                            val valueText = when {
                                variable != null -> "%{$variable}%"
                                literal != null -> literal
                                else -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
                            }
                            val configured = variable != null || literal != null
                            me.awabi2048.kantancommander.gui.declarative.DiskCallArgumentCard(
                                name = argument.name,
                                label = Component.text("${argument.name}: $valueText"),
                                background = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                                outline = GestureSettingVisualPolicy.nonTabOutlineMaterial(configured)?.let {
                                    Bukkit.createBlockData(it)
                                },
                            )
                        },
                    page = page,
                    pageCount = pageCount,
                    pagerAvailableBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                    pagerDisabledBackground = Bukkit.createBlockData(DisabledGuiVisualPolicy.material),
                    returnLabel = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_RETURN),
                    ),
                    returnScopeLabel = Component.text(
                        if (returnScope == VariableScope.WORLD.name) {
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_WORLD)
                        } else {
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_EXECUTION)
                        },
                    ),
                    returnScopeBackground = Bukkit.createBlockData(Material.LIGHT_BLUE_CONCRETE),
                    returnNameLabel = Component.text(
                        returnName.ifBlank {
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
                        },
                    ),
                    returnNameBackground = Bukkit.createBlockData(
                        if (returnName.isNotBlank()) Material.CYAN_CONCRETE else Material.LIGHT_GRAY_CONCRETE,
                    ),
                    backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                    backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                ),
            ),
            GestureLowerMode.DISK_CALL_SETTINGS,
            "[DeclarativeDiskCall]",
        )
    }

    /**
     * issue #7 のイベント種別の選択子画面を生成します。
     *
     * 初期10種を変更前のカード配置でページ表示し、同じ候補の再クリックで詳細へ進みます。
     */
    fun buildTriggerEventChild(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView {
        val configuredEvent = plugin.scripts.load(state.scriptId)?.effectiveTrigger()
            ?.takeIf { it.kind == TriggerKind.EVENT }?.event
        val current = state.pendingTriggerEvent
        val pageSize = me.awabi2048.kantancommander.gui.declarative.DeclarativeTriggers.EVENT_PAGE_SIZE
        val total = EventTriggerKind.entries.size
        val pageCount = ((total + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = state.triggerEventPage.coerceIn(0, pageCount - 1)
        return finishVariablesChild(
            me.awabi2048.kantancommander.gui.declarative.DeclarativeTriggers.eventDocument(
                me.awabi2048.kantancommander.gui.declarative.TriggerChoiceData(
                    title = Component.text(TriggerDisplay.kindName(player, TriggerKind.EVENT)),
                    description = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TRIGGER_BODY),
                    ),
                    options = EventTriggerKind.entries.map { event ->
                        me.awabi2048.kantancommander.gui.declarative.TriggerChoiceOption(
                            key = event.name,
                            label = Component.text(TriggerDisplay.eventName(player, event)),
                            selected = current == event,
                            background = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                            outline = GestureSettingVisualPolicy.entityChoiceOutlineMaterial(
                                selected = current == event,
                                configured = configuredEvent == event,
                            )?.let {
                                Bukkit.createBlockData(it)
                            },
                        )
                    },
                    backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                    backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                    page = page,
                    pageCount = pageCount,
                    pagerAvailableBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                    pagerDisabledBackground = Bukkit.createBlockData(DisabledGuiVisualPolicy.material),
                ),
            ),
            GestureLowerMode.TRIGGER_EVENT,
            "[DeclarativeTriggerEvent]",
        )
    }

    /** 再クリック後のイベント詳細だけを、一般コマンド設定と同じ左右構成で表示します。 */
    fun buildTriggerDetailChild(state: GestureEditorState, player: Player): GestureGuiView {
        val event = requireNotNull(state.pendingTriggerEvent) { "詳細画面のイベントが選択されていません" }
        return finishVariablesChild(
            me.awabi2048.kantancommander.gui.declarative.DeclarativeTriggers.detailDocument(
                me.awabi2048.kantancommander.gui.declarative.TriggerDetailData(
                    title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DETAIL_SETTINGS)),
                    eventLabel = TriggerDisplay.kindName(player, TriggerKind.EVENT),
                    eventName = Component.text(TriggerDisplay.eventName(player, event)),
                    description = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TRIGGER_BODY)),
                    confirmLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM)),
                    backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                    tabBackground = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                    tabOutline = Bukkit.createBlockData(Material.WHITE_CONCRETE),
                    confirmBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                    backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                ),
            ),
            GestureLowerMode.TRIGGER_DETAIL,
            "[DeclarativeTriggerDetail]",
        )
    }

    /**
     * issue #8 のディスク設定子画面を生成します。
     *
     * 取り出し操作と引数定義の一覧を示します。引数は名前・型・削除の3カード1行で
     * 2件ずつページングし、追加カードと戻る操作が重ならないようにします。
     */
    fun buildDiskSettingsChild(
        state: GestureEditorState,
        player: Player,
    ): GestureGuiView {
        val script = plugin.scripts.load(state.scriptId)
        val arguments = script?.arguments.orEmpty()
        val pageSize = 2
        val pageCount = ((arguments.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = state.diskSettingsArgsPage.coerceIn(0, pageCount - 1)
        return finishVariablesChild(
            me.awabi2048.kantancommander.gui.declarative.DeclarativeDiskSettings.settingsDocument(
                me.awabi2048.kantancommander.gui.declarative.DiskSettingsData(
                    title = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISK_SETTINGS),
                    ),
                    body = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_SETTINGS_BODY),
                    ),
                    takeOutLabel = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_TAKE_OUT),
                    ),
                    takeOutBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                    emptyArgs = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_NO_ARGS),
                    ),
                    arguments = arguments
                        .drop(page * pageSize)
                        .take(pageSize)
                        .map { argument ->
                            me.awabi2048.kantancommander.gui.declarative.DiskArgumentRow(
                                name = argument.name,
                                nameLabel = Component.text(argument.name),
                                typeLabel = Component.text(variableTypeName(player, argument.type)),
                                background = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                                outline = null,
                            )
                        },
                    page = page,
                    pageCount = pageCount,
                    pagerAvailableBackground = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                    pagerDisabledBackground = Bukkit.createBlockData(DisabledGuiVisualPolicy.material),
                    addLabel = Component.text(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_ARG_ADD),
                    ),
                    addBackground = Bukkit.createBlockData(Material.GREEN_CONCRETE),
                    backLabel = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
                    backBackground = Bukkit.createBlockData(Material.BROWN_CONCRETE),
                ),
            ),
            GestureLowerMode.DISK_SETTINGS,
            "[DeclarativeDiskSettings]",
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
                plugin.logger.fine("[KantanCommander][DeclarativeTestConfirm] $diagnostics")
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
                plugin.logger.fine("[KantanCommander][DeclarativeTestStatus] $diagnostics")
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
                plugin.logger.fine("[KantanCommander][DeclarativeTestResult] $diagnostics")
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
        // 実行位置タブはタブ列から抽出して固定行へ置くため、上限検査の対象外にします。
        // 通常タブ列だけが6枠の物理配置を共有します。
        val columnTabCount = fields.count { it.key != EXECUTION_POSITION_FIELD_KEY }
        check(columnTabCount <= SETTINGS_TAB_MAX) {
            "設定タブは${SETTINGS_TAB_MAX}個以内で定義してください: ${node.type}=$columnTabCount"
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
            // 実行位置タブはタブ列から抽出し、下端の操作行の直上へ固定配置します。
            executionTabIndex = fields.indexOfFirst { it.key == "executionPosition" }.takeIf { it >= 0 },
        )
        val field = fields[state.settingsTab.coerceIn(0, fields.lastIndex)]
        val descriptor = CommandSettingsModel.descriptor(node, field.key)
        val settingContext = state.settingContext
            ?: CommandSettingContext(state.scriptId, node.id, descriptor.role)
        val displayLabel = KcI18n.text(player, field.label)
        val displayValue = field.value(node)
        val compositeRows = displayValue.rows(player)
        val compositeLayout = compositeRows?.let {
            GestureSettingValueLayout.calculate(
                rowCount = it.size,
                valueAnchorY = SETTING_VALUE_ANCHOR_Y - SETTING_COMPOSITE_SHIFT,
                detailAnchorY = SETTING_DETAIL_ANCHOR_Y - SETTING_COMPOSITE_SHIFT,
                // 2行の効果音値は説明文と近いため、行間を追加せずに配置域を使います。
                // 3行以上は既存の行間率を保ち、画面内で値と詳細を下方へ寄せます。
                rowGapRatio = if (it.size == 2) 0.0 else TIMING_ROW_GAP_RATIO,
                minimumDetailY = SETTING_INPUT_CENTER_Y - SETTING_COMPOSITE_SHIFT +
                    SETTING_INPUT_HEIGHT / 2.0 + SETTING_DETAIL_INPUT_GAP,
            )
        }
        val inputCenterY = if (compositeRows != null) {
            SETTING_INPUT_CENTER_Y - SETTING_COMPOSITE_SHIFT
        } else {
            SETTING_INPUT_CENTER_Y
        }
        // 種別名はバニラ翻訳のComponentで渡し、各クライアントの言語で描画させます。
        val valueComponent = displayValueComponent(player, displayValue)
        // 初期時点では全項目を未設定とするため、単行表示では明示設定のない現在値欄を
        // 未設定表示にします。複合表示は各項目の表示規則に従います。
        val singleValue = if (CommandSettingsModel.isFieldConfigured(node, field.key, descriptor.role)) {
            valueComponent
        } else {
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET))
        }
        val settingScreen = gestureSettingScreenFor(descriptor.editor)
        // issue #18: Checkbox対象は親画面のトグルで直接操作するため、
        // 旧来の二択カードを重ねて表示しません。
        val checkboxPresentation = SettingSpec.presentationFor(node, field.key, descriptor) ==
            GesturePresentation.CHECKBOX
        val settingChoices = if (checkboxPresentation) emptyList() else settingScreen?.let { screen ->
            // 経路末尾の選択は同階層へ排他的に適用し、保存値由来との二重選択を防ぎます。
            applyPathSelectionExclusive(
                settingTreeNodes(
                    node,
                    settingContext,
                    screen,
                    field.key,
                    player,
                    attentionFields,
                    detailVisited = state.detailVisitedKeys.contains(
                        PresetSelectionDisplay.detailVisitKey(field.key, settingContext.role),
                    ),
                ),
                state.settingTreePath?.nodeIds?.lastOrNull(),
            )
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
                    SettingsValueRow("lower-setting-composite-$index", row.label, Component.text(row.value), compositeLayout.rowCentersY[index])
                },
            )
        } else {
            DeclarativeSettings.valueRowsSection(
                nodes,
                frame,
                rows = listOf(SettingsValueRow("lower-setting", displayLabel, singleValue, SETTING_VALUE_ANCHOR_Y)),
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
        appendSettingsInputCards(nodes, frame, state, player, node, field, descriptor, inputCenterY, suppressHighlight, fieldPreviews)
        // 「アイテムを配置」の上書き設定は独立タブを持たず、配置先タブの
        // 中央下揃えトグルで編集します。配置先5択（上揃え）とは行を共有せず、
        // 下端基準の中央配置に置くため項目数の増減で位置がずれません。
        if (node.type == CommandType.PLACE_ITEM && field.key == "placeDestination") {
            appendCheckboxCard(
                nodes, frame, node, "overwrite", inputCenterY, suppressHighlight,
                centerX = SETTING_TOGGLE_CENTER_X,
                centerY = SETTING_CHOICE_BOTTOM_Y,
                fixedLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OVERWRITE_PLACE),
                // 下段共通スロットはボタンの内側と重なるため、ボタン上端の外側へ移します。
                // 上段の配置先5択（最下行の下端-0.19）とも重なりません。
                hoverX = SETTING_TOGGLE_CENTER_X,
                hoverY = SETTING_CHOICE_BOTTOM_Y + SETTING_INPUT_HEIGHT / 2.0 + HOVER_BUTTON_GAP,
            )
        }
        return finishSettings(nodes)
    }

    /** 宣言区画を解決・変換し、既存の画面定義へ載せます。 */
    private fun finishSettings(nodes: List<GestureGuiNode>): GestureGuiView {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeSettings.document(nodes),
            onAction,
            onDiagnostics = { diagnostics ->
                plugin.logger.fine("[KantanCommander][DeclarativeSettings] $diagnostics")
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
        // 上揃え項目は上端基準、下揃え項目（詳細設定・一時変数参照等）は
        // 下端基準で配置します。下揃え行は下からの行番号で定まるため、
        // 上位項目数の増減で位置がずれません。
        val topChoices = choices.filterNot { it.bottomAligned }
        val bottomChoices = choices.filter { it.bottomAligned }
        appendChoiceRows(
            nodes, frame, topChoices, fieldKey, player, child, suppressHighlight,
            rowBase = 0,
            rowCount = (topChoices.size + 1) / 2,
            cyForRow = { row ->
                if (child) CHILD_CHOICE_TOP_Y - row * CHILD_CHOICE_PITCH
                else TARGET_PARENT_CHOICE_TOP_Y - row * TARGET_PARENT_CHOICE_PITCH
            },
        )
        val bottomRowCount = (bottomChoices.size + 1) / 2
        appendChoiceRows(
            nodes, frame, bottomChoices, fieldKey, player, child, suppressHighlight,
            rowBase = 0,
            rowCount = bottomRowCount,
            cyForRow = { row -> SETTING_CHOICE_BOTTOM_Y + (bottomRowCount - 1 - row) * SETTING_CHOICE_PITCH },
        )
    }

    private fun appendChoiceRows(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        choices: List<GestureSettingTreeNode>,
        fieldKey: String,
        player: Player,
        child: Boolean,
        suppressHighlight: Boolean,
        rowBase: Int,
        rowCount: Int,
        cyForRow: (Int) -> Double,
    ) {
        choices.forEachIndexed { index, choice ->
            val column = index % 2
            val row = rowBase + index / 2
            val cx = if (child) {
                if (column == 0) -0.53 else 0.53
            } else if (column == 0) -0.10 else 0.67
            val cy = cyForRow(index / 2)
            DeclarativeSettings.choiceCardsSection(
                nodes,
                frame,
                cards = listOf(settingChoiceCard(
                    choice = choice,
                    fieldKey = fieldKey,
                    player = player,
                    child = child,
                    suppressHighlight = suppressHighlight,
                    cx = cx,
                    cy = cy,
                    row = row,
                    rowCount = rowCount,
                    choicesInRowGroup = choices.size,
                    visualIndex = index,
                )),
            )
        }
    }

    /** 同じ意味・色・ホバー規則をKotlin宣言とHTMLテンプレートで共有する選択カードです。 */
    private fun settingChoiceCard(
        choice: GestureSettingTreeNode,
        fieldKey: String,
        player: Player,
        child: Boolean,
        suppressHighlight: Boolean,
        cx: Double,
        cy: Double,
        row: Int,
        rowCount: Int,
        choicesInRowGroup: Int,
        visualIndex: Int,
    ): SettingsChoiceCard = SettingsChoiceCard(
        elementId = "lower-setting-choice:${choice.id}",
        label = (choice.labelComponent ?: Component.text(choice.label))
            .color(GestureSettingVisualPolicy.settingChoiceTextColor(choice)),
        background = Bukkit.createBlockData(settingChoiceMaterial(choice)),
        outline = if (suppressHighlight) {
            null
        } else {
            // 選択中は白枠、設定済み値は空色枠とする共通状態規則を使います。
            val outline = GestureSettingVisualPolicy.choiceOutlineMaterial(choice)
            outline?.let(Bukkit::createBlockData)
        },
        enabled = choice.enabled,
        cx = cx,
        cy = cy,
        w = if (child) CHILD_CHOICE_WIDTH else SETTING_CHOICE_WIDTH,
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
                parallel = choicesInRowGroup > 1,
            )
        },
        replacesDescription = child,
        visualTag = "setting-choice",
        visualIndex = visualIndex,
    )

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
            // 旧ENTITY_ACTION-equipの実物選択はPLACE_ITEMへ移行済みのため外します。
            node.type == CommandType.PLACE_ITEM ||
            (node.type == CommandType.CONDITION && node.conditionSubject() == ConditionSubject.PLAYER))
        val heldBlockSetting = field.key == "block" && (
            node.type == CommandType.BLOCK_OPERATION ||
                (node.type == CommandType.CONDITION && node.conditionSubject() == ConditionSubject.BLOCK) ||
                (node.type == CommandType.TEMP_SET &&
                    ValueType.parse(node.string("tempType")) == ValueType.BLOCK)
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
            // 一時変数参照に対応した項目（ITEM／BLOCK）は、直接値のとき
            // 試聴・取得カードを配置します。音・効果の一時変数参照は廃止したため、
            // ここへ資源ピッカー項目は到達しません。
            appendTypedValuePreview(
                nodes, frame, node, field.key, selectedSource, state.scriptId, player,
                compact = false,
            )
        } else if (heldMainHandSetting || dialogInputSetting) {
            val editVisualText = if (heldMainHandSetting) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_EDIT_FROM_MAINHAND
            } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_HOVER
            val editTextColor = if (heldMainHandAvailable) NamedTextColor.GOLD else NamedTextColor.GRAY
            // ID候補を持つ項目は、プリセット一覧と入力カードを親タブ直下へ
            // 直接配置します。子画面遷移は行いません。
            val pickerKind = ResourcePicker.kindForFieldKey(field.key)
            if (!heldMainHandSetting && pickerKind != null) {
                // 一覧を上、入力カードを下に置きます。
                val inlineGridTopY = inputCenterY - INLINE_RESOURCE_GRID_TOP_DY
                appendInlineResourcePicker(
                    nodes, frame, player, node, field.key, pickerKind,
                    gridTopY = inlineGridTopY,
                    inputCx = 0.28,
                    inputCy = inlineGridTopY - 2 * INLINE_RESOURCE_GRID_PITCH_Y - INLINE_RESOURCE_INPUT_BELOW_DY,
                    inputW = 1.2,
                )
            } else {
                DeclarativeSettings.actionCardSection(
                    nodes,
                    frame,
                    SettingsActionCard(
                        elementId = "lower-edit:${field.key}",
                        label = Component.text(KcI18n.text(player, editVisualText), editTextColor),
                        background = Bukkit.createBlockData(
                            if (heldMainHandAvailable) Material.CYAN_TERRACOTTA else DisabledGuiVisualPolicy.material,
                        ),
                        cx = 0.28,
                        cy = inputCenterY,
                        w = 1.2,
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
     * トグルは選択とは別概念のため、選択表示の縁取りは付けません。
     * nullable三値はここへ載せず、別表現に委譲します。
     */
    private fun appendCheckboxCard(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        node: CommandNode,
        fieldKey: String,
        inputCenterY: Double,
        suppressHighlight: Boolean = false,
        // 既定は従来どおり右ペイン中央の入力行です。配置先タブの上書き
        // トグルのように下端中央へ置く場合は呼び出し側で上書きします。
        centerX: Double = SETTING_TOGGLE_CENTER_X,
        centerY: Double? = null,
        // 固定ラベル部（状態を含まない文言）です。配置先タブの上書きトグルでは
        // 「上書きして配置する ON」の形式にするため呼び出し側で渡します。
        // 既定のままでは従来どおりON／OFFのみを表示します。
        fixedLabel: String? = null,
        // ホバー文の表示位置です。既定は画面下段の共通スロットですが、
        // ボタンと重なる配置では呼び出し側でボタン外（上など）へ移します。
        hoverX: Double = HOVER_SLOT_X,
        hoverY: Double = HOVER_SLOT_Y,
    ) {
        val checked = node.boolean(fieldKey)
        val labelText = checkboxToggleLabel(fixedLabel, checked)
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-toggle:$fieldKey",
                label = Component.text(labelText),
                background = Bukkit.createBlockData(
                    if (checked) Material.LIME_CONCRETE else Material.GRAY_CONCRETE,
                ),
                cx = centerX,
                cy = centerY ?: inputCenterY,
                w = 1.2,
                h = SETTING_INPUT_HEIGHT,
                labelSize = 0.006,
                labelWidth = 120,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = singleLineHover(
                    "$labelText（クリックで${if (checked) "OFF" else "ON"}）",
                    x = hoverX,
                    y = hoverY,
                ),
                visualTag = "lower-toggle",
                // トグルのON／OFFは選択状態ではないため、選択表示の縁取りは付けません。
                // 状態の区別は背景色（LIME／GRAY）に限定します。
                outline = null,
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
        // issue #29: 一覧選択カードと並べるときは試聴カードを左半分へ寄せます。
        compact: Boolean = false,
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
                cx = if (compact) -0.02 else TYPED_VALUE_PREVIEW_CENTER_X,
                cy = TYPED_VALUE_PREVIEW_CENTER_Y,
                w = if (compact) 0.56 else TYPED_VALUE_PREVIEW_WIDTH,
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
            // issue #7: 「タイマー設定」を「起動方法」へ整理します。
            // 実行時に採用する起動方法（明示指定または従来欄の読み替え）の要約を示し、
            // 名称の固定ラベルと現在値の表示欄を分離します。
            val triggerState = GestureSettingValueState.CONFIGURED
            val triggerValue = TriggerDisplay.summary(player, script.effectiveTrigger())
            cards += ScriptSettingCard(
                elementId = "lower-script-trigger",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TRIGGER),
                value = Component.text(triggerValue),
                background = Bukkit.createBlockData(
                    GestureSettingVisualPolicy.material(
                        GestureSettingSelectionMode.MULTIPLE,
                        triggerState,
                    ),
                ),
                icon = ItemStack(Material.CLOCK),
                attentionHover = if (attention.trigger) {
                    // 注意ホバーは上段カードの図柄の上側へ出します（下段は下側の規則）。
                    // 固定ホバースロット（HOVER_SLOT_Y）は使わず、対象カードの直上へ置きます。
                    singleLineHover(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TRIGGER),
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
            // issue #8: 未選択時下部の4枚目はディスクの設定（取り出しと引数定義）です。
            // 取り出し自体は内容の複製であり、簡易GUIの出力操作と同じく内容があるときだけ
            // 有効にします。空のプログラムでは未設定表示とし、クリック時点のガードでも受け付けません。
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
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISK_SETTINGS),
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
                plugin.logger.fine("[KantanCommander][DeclarativeScriptSettings] $diagnostics")
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

    /**
     * issue #2 の変数名候補で使うワールドIDです。
     *
     * 設定画面の文脈は state を持たないため、スクリプトの配置先から所有ワールドを
     * 解決します。未配置・解決不能の場合は候補なし（直接入力のみ）として扱います。
     */
    private fun variableWorldIdFor(scriptId: java.util.UUID): java.util.UUID? {
        val worldName = plugin.placements.findByScript(scriptId).firstOrNull()?.world ?: return null
        if (!plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) return null
        return runCatching {
            me.awabi2048.myworldmanager.api.MyWorldManagerApi.getWorldRepository()
                ?.findByWorldName(worldName)?.uuid
        }.getOrNull()
    }



    /**
     * issue #3 の参照元変数の定義型を解決します。
     *
     * WORLD scope は永続定義表から確定します。EXECUTION scope はフロー解析の
     * 定義表を画面側で持たないため未確定（null）とし、全候補を提示します。
     * 不適合の選択は検証層で拒否します。
     */
    private fun resolvePropertySourceType(node: CommandNode, context: CommandSettingContext): ValueType? {
        val source = node.string("propertySource").takeIf(String::isNotBlank) ?: return null
        if (node.variableScope() != VariableScope.WORLD) return null
        val worldId = variableWorldIdFor(context.scriptId) ?: return null
        return plugin.variables.definitions(worldId)[source]?.type
    }

    /** 変数の型名を、ノード設定と同じ表示キーで解決します。 */
    private fun variableTypeName(player: Player, type: ValueType): String = KcI18n.text(
        player,
        displayVariableTypeNameKey(type),
    )

    /** issue #2 の共通値型の表示キーを返します。両GUIで同じ文言を使います。 */
    fun displayVariableTypeNameKey(type: ValueType): com.awabi2048.ccsystem.api.localization.LocalizationKey<String> =
        type.displayKey()

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
                FacingKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_TARGET
                FacingKind.COPY_ORIENTATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_COPY_ORIENTATION
                FacingKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_SOURCE_TEMPORARY
                FacingKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_FACING_COORDINATES
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
                "motion" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_ACTION_MOTION
                else -> null
            }
        }
        choice.id.startsWith("placeDestination:") -> suffixKeyDescription(player, choice.id, "placeDestination:") { suffix ->
            PlaceItemDestination.from(suffix)?.labelKey
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
            when (runCatching { ValueType.valueOf(suffix) }.getOrNull()) {
                ValueType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_TYPE_DECIMAL
                ValueType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_TYPE_TEXT
                ValueType.LOCATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_TYPE_POSITION
                ValueType.ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_TYPE_ENTITY
                ValueType.ITEM,
                ValueType.BLOCK,
                null -> null
            }
        }
        choice.id.startsWith("operation:") -> suffixKeyDescription(player, choice.id, "operation:") { suffix ->
            when (runCatching { VariableOperation.valueOf(suffix) }.getOrNull()) {
                VariableOperation.DEFINE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_OPERATION_SET
                VariableOperation.SET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_OPERATION_SET
                VariableOperation.CHANGE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_OPERATION_ADD
                VariableOperation.COPY -> null
                VariableOperation.DELETE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_VARIABLE_OPERATION_CLEAR
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
        // issue #29: プリセットの説明は専用キーで示します。
        choice.id.startsWith("target-preset:") || choice.id.startsWith("position-entity-preset:") ||
            choice.id.startsWith("facing-entity-preset:") || choice.id.startsWith("facing-copy-preset:") -> when (choice.id.substringAfterLast(":")) {
            "NEARBY_PLAYERS" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_TARGET_NEARBY_PLAYERS)
            "MYWORLD_PLAYERS" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_TARGET_MYWORLD_PLAYERS)
            "NEAREST_PLAYER" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_TARGET_NEAREST_PLAYER)
            else ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_TARGET_NEARBY_ENTITIES)
        }
        choice.id == "target-detail" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_TARGET_DETAIL)
        choice.id.startsWith("position-preset:") -> when (choice.id.substringAfterLast(":")) {
            "CONTROL_BLOCK" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_POSITION_CONTROL_BLOCK)
            "NEAREST_PLAYER", "NEARBY_PLAYERS" -> when (choice.id.substringAfterLast(":")) {
                "NEAREST_PLAYER" ->
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_POSITION_NEAREST)
                else ->
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_POSITION_NEARBY)
            }
            "MYWORLD_SPAWN" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_POSITION_MYWORLD_SPAWN)
            else ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_POSITION_COORDINATES)
        }
        choice.id == "position-entity" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_POSITION_ENTITY)
        choice.id.startsWith("facing-preset:") -> when (choice.id.substringAfterLast(":")) {
            "NEAREST_PLAYER" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_FACING_NEAREST)
            "COORDINATES" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_FACING_COORDINATES)
            else ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_FACING_NUMERIC)
        }
        choice.id == "facing-entity" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_FACING_ENTITY)
        choice.id == "facing-copy" ->
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_FACING_COPY)
        choice.id.startsWith("myworld-spawn:") -> when (choice.id.substringAfterLast(":")) {
            "GUEST" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_MYWORLD_SPAWN_GUEST)
            else ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_PRESET_MYWORLD_SPAWN_MEMBER)
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

    /**
     * issue #27: 判定対象の選択肢ID（condition-kind:<SUBJECT>）から、対象ごとの説明へ解決します。
     * 単一正本の `subject` 欄だけを扱い、旧 `kind` 形式のIDは受け付けません。
     */
    private fun conditionKindDescription(player: Player, choiceId: String): String? {
        val subject = runCatching {
            ConditionSubject.valueOf(choiceId.removePrefix("condition-kind:"))
        }.getOrNull() ?: return null
        return KcI18n.text(
            player,
            when (subject) {
                ConditionSubject.PLAYER ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_ENTITY_STATE
                ConditionSubject.OTHER_ENTITY ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_TARGET_EXISTS
                ConditionSubject.VARIABLE ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_VARIABLE_STATE
                // #5: 型付き比較は変数条件の説明を共用します。左辺・演算子・右辺の
                // 意味は詳細項目の説明で補います。
                ConditionSubject.VALUE ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_VARIABLE_STATE
                ConditionSubject.BLOCK ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_BLOCK_STATE
                ConditionSubject.CONTROL_BLOCK ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_CONTROL_BLOCK_STATE
            },
        )
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
            // #5: 左辺・右辺は変数名・比較値の説明を共用し、範囲は対象範囲の説明を共用します。
            "condition-left" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_VARIABLE)
            "condition-right" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_VALUE)
            "condition-left-scope" ->
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESCRIPTION_CONDITION_DETAIL_SCOPE)
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

    /** 複数の意味行（Component）を、空行を挿入せずに一つのホバーTextDisplayへ配置します。 */
    internal fun multiLineHoverComponents(
        lines: List<Component>,
        x: Double,
        y: Double,
        replacesVisualId: String? = null,
    ): GestureGuiHoverText {
        val text = lines.foldIndexed(Component.empty() as Component) { index, result, line ->
            if (index == 0) line else result.append(Component.newline()).append(line)
        }
        return hoverText(text, x, y, replacesVisualId)
    }

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
     * issue #review P2-10: 行構造を保つ複数行版です。
     *
     * 配置規則は [parallelButtonHover] と同一で、本文だけ複数行結合にします。
     * PICKERのコマンド説明2行構成用であり、既存の単行呼び出し側は変えません。
     */
    internal fun parallelButtonHoverLines(
        lines: List<String>,
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
        parallel && row == 0 -> multiLineHover(
            lines,
            x = x,
            y = y + height / 2.0 + HOVER_BUTTON_GAP,
        )
        parallel && rowCount > 1 && row == rowCount - 1 -> multiLineHover(
            lines,
            x = x,
            y = y - height / 2.0 - HOVER_BUTTON_GAP,
        )
        else -> multiLineHover(
            lines,
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
        // 詳細入室後はプリセットの選択表示を詳細側へ移します（保存値は不変）。
        detailVisited: Boolean = false,
    ): List<GestureSettingTreeNode> = rawSettingTreeNodes(node, context, screen, fieldKey, player, inChild, detailVisited)
        .map { decorateSettingChoice(node, context, fieldKey, it, attentionFields) }

    private fun rawSettingTreeNodes(
        node: CommandNode,
        context: CommandSettingContext,
        screen: GestureSettingScreen,
        fieldKey: String,
        player: Player,
        inChild: Boolean = false,
        detailVisited: Boolean = false,
    ): List<GestureSettingTreeNode> = when (screen) {
        GestureSettingScreen.TARGET -> targetChoices(node, context, player, detailVisited = detailVisited)
        GestureSettingScreen.POSITION -> positionChoices(node, context, player, inChild, detailVisited)
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
                    detailVisited = detailVisited,
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
                    detailVisited = detailVisited,
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
        else -> settingChoices(node, context, screen, fieldKey, player, detailVisited)
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
        // エンティティ操作の選択肢は変数操作と接頭辞を分け、
        // 変数操作の説明解決へ混ざらないようにします。
        // 詳細タブを持たないため、注意表示は設定対象タブへ投影します。
        choiceId.startsWith("entityData:") -> setOf(EntityDataPath.PARAM_PATH)
        choiceId.startsWith("attribute:") -> setOf(EntityDataPath.PARAM_PATH)
        choiceId.startsWith("tagAdd") -> setOf(EntityDataPath.PARAM_PATH)
        choiceId.startsWith("tagEntry:") -> setOf(EntityDataPath.PARAM_PATH)
        choiceId.startsWith("tagMode:") -> setOf(EntityDataPath.PARAM_PATH)
        choiceId.startsWith("tagDelete:") -> setOf(EntityDataPath.PARAM_PATH)
        choiceId.startsWith("propertyValue:") -> setOf(EntityDataPath.PARAM_PATH)
        choiceId.startsWith("property:") -> setOf(EntityDataSchema.PARAM_PROPERTY)
        // issue #21: 配置先の選択肢は配置先欄へ投影します。
        choiceId.startsWith("placeDestination:") -> setOf("placeDestination")
        // issue #10: 召喚時個別設定の選択肢は個別設定欄へ投影します。
        choiceId.startsWith("summonDataValue:") -> setOf("summonData")
        choiceId.startsWith("summonData:") -> setOf("summonData")
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
                SettingPresets.matchTargetPreset(
                    if (CommandSettingsModel.isEmbeddedTargetRole(context.role)) {
                        CommandSettingsModel.embeddedTargetSpec(node, context.role)
                    } else {
                        CommandSettingsModel.targetSpec(node, context.role)
                    },
                )?.name == id.removePrefix("target-preset:") &&
                if (CommandSettingsModel.isEmbeddedTargetRole(context.role)) {
                    CommandSettingsModel.embeddedTargetSpec(node, context.role) != null
                } else {
                    CommandSettingsModel.isFieldConfigured(node, "target", context.role)
                }
            id == "target-detail" -> choice.selected &&
                if (CommandSettingsModel.isEmbeddedTargetRole(context.role)) {
                    CommandSettingsModel.embeddedTargetSpec(node, context.role)
                        ?.let { SettingPresets.matchTargetPreset(it) == null } == true
                } else {
                    CommandSettingsModel.targetSpec(node, context.role)
                        ?.let { SettingPresets.matchTargetPreset(it) == null } == true &&
                        CommandSettingsModel.isFieldConfigured(node, "target", context.role)
                }
            id.startsWith("position-preset:") -> choice.selected &&
                (
                    if (context.role == CommandSettingRole.DESTINATION) {
                        SettingPresets.matchDestinationPreset(
                            CommandSettingsModel.targetSpec(node, context.role),
                            CommandSettingsModel.positionSpec(node, context.role),
                        )?.name
                    } else {
                        SettingPresets.matchPositionPreset(CommandSettingsModel.positionSpec(node, context.role))?.name
                    } == id.removePrefix("position-preset:")
                    ) &&
                positionChoiceComplete(node, context.role)
            id.startsWith("facing-preset:") -> choice.selected &&
                SettingPresets.matchFacingPreset(CommandSettingsModel.facingSpec(node, context.role))?.name ==
                id.removePrefix("facing-preset:") &&
                CommandSettingsModel.isFacingSpecComplete(CommandSettingsModel.facingSpec(node, context.role))
            id.startsWith("position-entity-preset:") -> choice.selected &&
                SettingPresets.matchTargetPreset(CommandSettingsModel.positionSpec(node, context.role)?.target)?.name ==
                id.removePrefix("position-entity-preset:") &&
                CommandSettingsModel.positionSpec(node, context.role) != null
            id.startsWith("facing-entity-preset:") -> choice.selected &&
                SettingPresets.matchTargetPreset(CommandSettingsModel.facingSpec(node, context.role)?.target)?.name ==
                id.removePrefix("facing-entity-preset:") &&
                CommandSettingsModel.facingSpec(node, context.role) != null
            id == "position-entity" -> choice.selected &&
                CommandSettingsModel.positionSpec(node, context.role)?.kind == PositionKind.TARGET &&
                positionChoiceComplete(node, context.role)
            id == "facing-entity" -> choice.selected &&
                CommandSettingsModel.facingSpec(node, context.role)?.kind == FacingKind.TARGET &&
                CommandSettingsModel.isFacingSpecComplete(CommandSettingsModel.facingSpec(node, context.role))
            id == "facing-copy" -> choice.selected &&
                CommandSettingsModel.facingSpec(node, context.role)?.kind == FacingKind.COPY_ORIENTATION &&
                CommandSettingsModel.isFacingSpecComplete(CommandSettingsModel.facingSpec(node, context.role))
            id.startsWith("myworld-spawn:") -> choice.selected
            // issue #34-1: 内包役割では位置・向き仕様の内部対象を参照します。
            // targetSpec へ流すと別フィールドを参照するため、ここで分岐します。
            id.startsWith("target:") -> choice.selected &&
                CommandSettingsModel.targetCategory(
                    (if (CommandSettingsModel.isEmbeddedTargetRole(context.role)) {
                        CommandSettingsModel.embeddedTargetSpec(node, context.role)
                    } else {
                        CommandSettingsModel.targetSpec(node, context.role)
                    })?.kind,
                ) ==
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
            // issue #5: Boolean直接条件は演算子なしで成立します。
            id == "condition-direct" -> node.string("operator").isBlank()
            // issue #5: 条件の領域とその子選択肢はノードの領域指定から導出します。
            // 対象絞り込み側と同じ共有判定を使い、分岐を増やしません。
            id == "condition-region" -> CommandSettingsModel.isRegionConfigured(node.regionSpec, "region")
            id.startsWith("condition-region-") -> CommandSettingsModel.isRegionConfigured(
                node.regionSpec,
                id.removePrefix("condition-"),
            )
            id.startsWith("condition-variable") -> CommandSettingsModel.isFieldConfigured(node, "variable")
            id.startsWith("condition-operator") -> CommandSettingsModel.isFieldConfigured(node, "operator")
            id.startsWith("condition-value") -> CommandSettingsModel.isFieldConfigured(node, "value")
            id.startsWith("condition-block") -> CommandSettingsModel.isFieldConfigured(node, "block")
            id.startsWith("condition-item-data") -> CommandSettingsModel.isFieldConfigured(node, "itemData")
            id.startsWith("condition-item") -> CommandSettingsModel.isFieldConfigured(node, "item")
            id.startsWith("changeMode:") -> choice.selected &&
                node.string("changeMode", "ASSIGN") == id.removePrefix("changeMode:") &&
                CommandSettingsModel.isFieldConfigured(node, "changeMode")
            // issue #10: 召喚時個別設定の選択状態です。値の有無で判定し、
            // 有限候補の選択肢は現在の保存値との一致で判定します。
            id.startsWith("summonDataValue:") -> {
                val remainder = id.removePrefix("summonDataValue:")
                val separator = remainder.indexOf(':')
                if (separator <= 0) false
                else {
                    val propertyId = remainder.substring(0, separator)
                    val choice = remainder.substring(separator + 1)
                    node.string(EntityDataSchema.summonDataKey(propertyId)) == choice
                }
            }
            id.startsWith("summonData:") -> {
                // selectedは切替状態（真偽値ならON）を表すため、設定済み判定には使いません。
                // 明示的なfalseも有効な保存値として扱います。
                val propertyId = id.removePrefix("summonData:")
                val definition = EntityDataSchema.definition(propertyId)
                val raw = node.string(EntityDataSchema.summonDataKey(propertyId))
                definition?.let { EntityPropertyChoicePolicy.isValueConfigured(it, raw) } == true
            }
            id.startsWith("equipmentSlot:") -> choice.selected &&
                node.string("slot", "HAND") == id.removePrefix("equipmentSlot:") &&
                CommandSettingsModel.isFieldConfigured(node, "slot")
            id.startsWith("placeDestination:") -> choice.selected &&
                node.string("placeDestination", PlaceItemDestination.EQUIPMENT.paramValue) == id.removePrefix("placeDestination:") &&
                CommandSettingsModel.isFieldConfigured(node, "placeDestination")
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
            // エンティティ操作の設定済みは入力存在で判定し、選択表示と分離します。
            // 選択表示は排他的に1件ですが、設定済み表示は複数件に付き得ます。
            // 真偽値は偽値も入力として扱います。
            id == "entityData:tag" ->
                EntityTagEntries.parse(node.string(EntityTagEntries.PARAM_ENTRIES)).orEmpty().isNotEmpty()
            id == "entityData:attribute" ->
                EntityDataPath.parse(node.string(EntityDataPath.PARAM_PATH)) is EntityDataPath.Parsed.Attribute
            id.startsWith("entityData:property:") -> {
                val propertyId = id.removePrefix("entityData:property:")
                val raw = node.string(EntityDataSchema.PARAM_VALUE)
                val definition = EntityDataSchema.definition(propertyId) ?: return false
                EntityPropertyChoicePolicy.state(
                    activePropertyId = EntityDataPath.propertyIdOf(node.string(EntityDataPath.PARAM_PATH)),
                    definition = definition,
                    rawValue = raw,
                ).configured
            }
            id.startsWith("attribute:") ->
                EntityDataPath.attributeIdOf(node.string(EntityDataPath.PARAM_PATH)) == id.removePrefix("attribute:")
            id.startsWith("propertyValue:") ->
                node.string(EntityDataSchema.PARAM_VALUE) == id.removePrefix("propertyValue:")
            id == "tagAdd" -> false
            id.startsWith("tagEntry:") || id.startsWith("tagMode:") || id.startsWith("tagDelete:") -> true
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
    internal fun settingChoiceInteractionMode(
        state: GestureEditorState,
        player: Player,
        choiceId: String,
    ): GestureSettingInteractionMode {
        val context = state.settingContext ?: return GestureSettingInteractionMode.SELECT
        val screen = state.settingScreen ?: return GestureSettingInteractionMode.SELECT
        val fieldKey = state.settingFieldKey ?: return GestureSettingInteractionMode.SELECT
        val node = plugin.scripts.load(context.scriptId)?.graph?.nodes?.get(context.nodeId)
            ?: return GestureSettingInteractionMode.SELECT
        return settingTreeNodes(node, context, screen, fieldKey, player)
            .firstNotNullOfOrNull { it.find(choiceId) }
            ?.effectiveInteractionMode
            ?: GestureSettingInteractionMode.SELECT
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
        detailVisited: Boolean = false,
    ): List<GestureSettingTreeNode> = when (screen) {
        GestureSettingScreen.TARGET -> targetChoices(node, context, player, detailVisited = detailVisited)
        GestureSettingScreen.TARGET_FILTERS -> targetFilterChoices(node, context, player)
        GestureSettingScreen.POSITION -> positionChoices(node, context, player, detailVisited = detailVisited)
        GestureSettingScreen.FACING -> facingChoices(node, context, player, detailVisited = detailVisited)
        // LOCATIONはrawSettingTreeNodesで位置・向きの親木を生成します。
        // ここは網羅性を保ちつつ、親木を単純な択一候補へ誤変換しないため空にします。
        GestureSettingScreen.LOCATION -> emptyList()
        GestureSettingScreen.CONDITION_KIND -> conditionKindChoices(node, player)
        GestureSettingScreen.CONDITION_DETAIL -> conditionDetailChoices(node, player)
        GestureSettingScreen.DISPLAY_MODE -> listOf(
            SettingChoice("display:tellraw", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHAT), explicitSelected(node, fieldKey, context.role, node.string("mode", "tellraw") == "tellraw")),
            SettingChoice("display:title", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TITLE), explicitSelected(node, fieldKey, context.role, node.string("mode", "tellraw") == "title")),
            SettingChoice("display:subtitle", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SUBTITLE), explicitSelected(node, fieldKey, context.role, node.string("mode", "tellraw") == "subtitle")),
            SettingChoice("display:actionbar", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ACTIONBAR), explicitSelected(node, fieldKey, context.role, node.string("mode", "tellraw") == "actionbar")),
        )
        GestureSettingScreen.BLOCK_OPERATION -> listOf(
            SettingChoice(
                "block:setblock",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BLOCK_SETBLOCK),
                explicitSelected(node, fieldKey, context.role, node.string("operation", "setblock") == "setblock"),
            ),
            SettingChoice(
                "block:fill",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BLOCK_FILL),
                explicitSelected(node, fieldKey, context.role, node.string("operation", "setblock") == "fill"),
            ),
        )
        GestureSettingScreen.ENTITY_ACTION -> listOf(
            // ENTITY_ACTIONはride/dismount/motionに限定します。旧equipはPLACE_ITEM（#21）へ、
            // 旧tagはENTITY_OPERATIONへ移行済みのため新規選択肢に出しません。
            SettingChoice("action:ride", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RIDE), node.string("action", "ride") == "ride"),
            SettingChoice("action:dismount", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISMOUNT), node.string("action", "ride") == "dismount"),
            // 旧equip／tagは新規選択肢に出さず、保存済み値の編集入口だけを残します（#21・#32）。
            SettingChoice("action:motion", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MOTION), node.string("action", "ride") == "motion"),
        )
        // 統合選択画面です。データ種類の事前指定は行わず、全項目を
        // NBTレイヤーの階層で選択します。対象種別が確定できる場合のみ
        // 適用可能な項目を表示し、和集合表示は行いません。
        GestureSettingScreen.ENTITY_DATA_ROOT -> entityDataRootChoices(node, player)
        // issue #10: 召喚時個別設定の一覧画面です。召喚種類が確定できる場合のみ
        // 適用可能な項目を表示し、和集合表示は行いません。
        GestureSettingScreen.SUMMON_DATA_ROOT -> summonDataRootChoices(node, player)
        // attribute子画面です。選択→変更後の値入力フローであり、
        // ID自体の自由入力欄は持ちません。
        GestureSettingScreen.ENTITY_ATTRIBUTE_SELECT -> {
            val current = EntityDataPath.attributeIdOf(node.string(EntityDataPath.PARAM_PATH))
            EntityAttributeSupport.allAttributeIds().map { id ->
                SettingChoice("attribute:$id", id, current == id)
            }
        }
        // タグ一覧はワールド変数子画面と同型の専用描画を使うため、
        // 汎用選択肢は出しません。操作は専用要素IDで振り分けます。
        GestureSettingScreen.ENTITY_TAG_LIST -> emptyList()
        // 有限候補の選択画面です。真偽値・文字列・整数は別経路で編集します。
        GestureSettingScreen.ENTITY_PROPERTY_VALUE -> {
            val propertyId = EntityDataPath.propertyIdOf(node.string(EntityDataPath.PARAM_PATH))
            val definition = propertyId?.let { EntityDataSchema.definition(it) }
            if (definition?.valueKind == EntityPropertyValueKind.ENUM) {
                definition.enumChoices.map { choice ->
                    SettingChoice(
                        "propertyValue:${choice.id}",
                        KcI18n.text(player, choice.optionKey),
                        node.string(EntityDataSchema.PARAM_VALUE) == choice.id,
                    )
                }
            } else emptyList()
        }
        GestureSettingScreen.PLACE_DESTINATION -> PlaceItemDestination.entries.map { destination ->
            SettingChoice(
                "placeDestination:${destination.paramValue}",
                KcI18n.text(player, destination.labelKey),
                node.string("placeDestination", PlaceItemDestination.EQUIPMENT.paramValue) == destination.paramValue,
            )
        }
        GestureSettingScreen.ENTITY_EQUIPMENT_SLOT -> listOf(
            "HAND" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HAND,
            "OFF_HAND" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_OFF_HAND,
            "HEAD" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HEAD,
            "CHEST" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_CHEST,
            "LEGS" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_LEGS,
            "FEET" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_FEET,
        ).map { (slot, label) ->
            SettingChoice("equipmentSlot:$slot", KcI18n.text(player, label), explicitSelected(node, fieldKey, context.role, node.string("slot", "HAND") == slot))
        }
        GestureSettingScreen.ENTITY_OVERWRITE -> listOf(
            SettingChoice("overwrite:true", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED), explicitSelected(node, fieldKey, context.role, node.boolean("overwrite"))),
            SettingChoice("overwrite:false", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED), explicitSelected(node, fieldKey, context.role, !node.boolean("overwrite"))),
        )
        GestureSettingScreen.CAMERA_SHAKE_TYPE -> listOf(
            SettingChoice("shake:positional", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SHAKE_POSITIONAL), explicitSelected(node, fieldKey, context.role, node.string("shakeType", "positional") == "positional")),
            SettingChoice("shake:rotational", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SHAKE_ROTATIONAL), explicitSelected(node, fieldKey, context.role, node.string("shakeType", "positional") == "rotational")),
        )
        GestureSettingScreen.SOUND_SCOPE -> listOf(
            SettingChoice("soundScope:POSITION", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_POSITION), explicitSelected(node, fieldKey, context.role, node.string("soundScope", "POSITION") == "POSITION")),
            SettingChoice("soundScope:WORLD", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_WORLD_WIDE), explicitSelected(node, fieldKey, context.role, node.string("soundScope", "POSITION") == "WORLD")),
        )
        GestureSettingScreen.SOUND_OPERATION -> listOf(
            SettingChoice("soundOperation:PLAY", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SOUND_PLAY), explicitSelected(node, fieldKey, context.role, SoundSettings.operation(node) == SoundOperation.PLAY)),
            SettingChoice("soundOperation:STOP", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SOUND_STOP), explicitSelected(node, fieldKey, context.role, SoundSettings.operation(node) == SoundOperation.STOP)),
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
        // 図形設定は、形状の択一と数値入力で構成します。
        // 数値入力はダイアログで開く葉であり、選択形状の数値だけを扱います。
        // 終点・対角点は表示位置（geometryEnd）へ分離したため、ここでは扱いません。
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
                // 初期時点では未設定とし、明示選択だけを反映します。
                val wireframe = node.params[ParticleGeometry.PARAM_WIREFRAME]?.toBooleanStrictOrNull() ?: true
                val wireframeExplicit = node.isExplicitlyConfigured(ParticleGeometry.PARAM_WIREFRAME)
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
                        wireframe && wireframeExplicit,
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
        }
        // #2: WORLD scope の変数名は定義済み候補から選択します。候補にない名前は
        // 直接入力から定義フロー（DEFINE）へ進めます。EXECUTION scope は文字列入力の
        // ためこの画面を使いません（TEXT表示）。
        GestureSettingScreen.VARIABLE_NAME -> buildList {
            val worldId = variableWorldIdFor(context.scriptId)
            val defined = worldId?.let { plugin.variables.definitions(it).keys.sorted() }.orEmpty()
            val current = node.string("name")
            defined.forEach { name ->
                add(
                    SettingChoice(
                        "variableName:$name",
                        name,
                        explicitSelected(node, fieldKey, context.role, current == name),
                    ),
                )
            }
            add(
                GestureSettingTreeNode(
                    id = "variable-name-direct",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DIRECT_VALUE),
                    selected = current.isNotBlank() && current !in defined,
                ),
            )
        }
        GestureSettingScreen.VARIABLE_TYPE -> ValueType.entries.map { type ->
            val key = if (node.type == CommandType.TEMP_SET) "tempType" else "type"
            SettingChoice(
                "type:${type.name}",
                KcI18n.text(player, displayVariableTypeNameKey(type)),
                explicitSelected(node, fieldKey, context.role, ValueType.parse(node.string(key, ValueType.NUMBER.name)) == type),
            )
        }
        GestureSettingScreen.VARIABLE_SCOPE -> listOf(
            SettingChoice(
                "variableScope:${VariableScope.EXECUTION.name}",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_EXECUTION),
                explicitSelected(node, fieldKey, context.role, node.string("scope", VariableScope.WORLD.name) == VariableScope.EXECUTION.name),
            ),
            SettingChoice(
                "variableScope:${VariableScope.WORLD.name}",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_WORLD),
                explicitSelected(node, fieldKey, context.role, node.string("scope", VariableScope.WORLD.name) == VariableScope.WORLD.name),
            ),
            // #22: ENTITY scope は事前DEFINEなしで対象直結のため、選択肢に出します。
            // 通常の対象形式と文字列の変数名で操作します。
            SettingChoice(
                "variableScope:${VariableScope.ENTITY.name}",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_ENTITY),
                explicitSelected(node, fieldKey, context.role, node.string("scope", VariableScope.WORLD.name) == VariableScope.ENTITY.name),
            ),
        )
        GestureSettingScreen.VARIABLE_OPERATION -> {
            val type = runCatching { ValueType.valueOf(node.string("type", ValueType.NUMBER.name)) }
                .getOrDefault(ValueType.NUMBER)
            // #22: ENTITY scope は事前DEFINEを要求しないため、定義操作を提示しません。
            val operations = CommandSettingsModel.allowedVariableOperations(type)
                .filter { node.variableScope() != VariableScope.ENTITY || it != VariableOperation.DEFINE }
            operations.map { operation ->
                SettingChoice(
                    "operation:${operation.name}",
                    KcI18n.text(player, operationLabel(operation)),
                    explicitSelected(node, fieldKey, context.role, node.string("operation", operation.name) == operation.name),
                )
            }
        }
        GestureSettingScreen.VARIABLE_CHANGE_MODE -> {
            // 一時変数とワールド変数で値指定／乱数の選択を共通化します。
            val type = ValueType.parse(
                node.string(if (node.type == CommandType.TEMP_SET) "tempType" else "type"),
            ) ?: ValueType.NUMBER
            val current = node.string("changeMode", "ASSIGN")
            fun choice(mode: VariableChangeMode, label: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>) =
                SettingChoice(
                    "changeMode:${mode.name}",
                    KcI18n.text(player, label),
                    explicitSelected(node, fieldKey, context.role, current == mode.name),
                )
            buildList {
                add(choice(VariableChangeMode.ASSIGN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ASSIGN))
                if (node.type == CommandType.TEMP_SET && type == ValueType.NUMBER) {
                    if (CommandSettingsModel.supportsRandomNumberMode(node)) {
                        add(choice(VariableChangeMode.RANDOM, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_CHANGE_RANDOM))
                    }
                } else if (node.type == CommandType.VARIABLE && type == ValueType.NUMBER) {
                    add(choice(VariableChangeMode.CALCULATE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CALCULATE))
                    add(choice(VariableChangeMode.PROPERTY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_CHANGE_PROPERTY))
                    if (CommandSettingsModel.supportsRandomNumberMode(node)) {
                        add(choice(VariableChangeMode.RANDOM, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_CHANGE_RANDOM))
                    }
                } else if (node.type == CommandType.VARIABLE) {
                    add(choice(VariableChangeMode.PROPERTY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_CHANGE_PROPERTY))
                }
            }
        }
        // #3: 詳細値は参照元の型で取得できる候補だけを提示します。参照元が
        // 未確定の場合は全候補を提示し、検証層で不適合を拒否します。
        GestureSettingScreen.VARIABLE_PROPERTY -> {
            val candidates = resolvePropertySourceType(node, context)
                ?.let { me.awabi2048.kantancommander.model.ValueProperties.forSource(it) }
                ?: me.awabi2048.kantancommander.model.ValueProperties.ALL
            candidates.map { definition ->
                SettingChoice(
                    "property:${definition.id}",
                    definition.id,
                    explicitSelected(node, fieldKey, context.role, node.string("property") == definition.id),
                )
            }
        }
        GestureSettingScreen.VARIABLE_VALUE -> buildList {
            add(SettingChoice("value:direct", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DIRECT_VALUE), explicitSelected(node, fieldKey, context.role, !node.string("value").startsWith("$"))))
            val script = plugin.scripts.load(context.scriptId)
            val insideFor = script != null && node.string("type", ValueType.NUMBER.name) == ValueType.NUMBER.name &&
                GraphEditor.isInsideFor(script.graph, node.id, GraphEditor.Edge.NEXT)
            if (insideFor) {
                add(
                    SettingChoice(
                        "value:count",
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT),
                        explicitSelected(node, fieldKey, context.role, node.string("value") == "\${CURRENT_LOOP_COUNT}"),
                    ),
                )
            }
        }
        GestureSettingScreen.CONDITION_INVERSION -> {
            listOf(
                SettingChoice("invert:true", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_ON), explicitSelected(node, fieldKey, context.role, node.boolean(fieldKey, false))),
                SettingChoice("invert:false", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_OFF), explicitSelected(node, fieldKey, context.role, !node.boolean(fieldKey, false))),
            )
        }
        // issue #11: 建造物の回転・反転は明示選択肢です。保存値は小文字文字列のため、
        // ここで各enumへ解決し直して選択表示と照合します。
        GestureSettingScreen.STRUCTURE_ROTATION -> StructureRotation.entries.map { rotation ->
            SettingChoice(
                "rotation:${rotation.value}",
                "${rotation.degrees}°",
                explicitSelected(node, fieldKey, context.role, node.string("rotation", StructureRotation.NONE.value) == rotation.value),
            )
        }
        GestureSettingScreen.STRUCTURE_MIRROR -> StructureMirror.entries.map { mirror ->
            val labelKey = when (mirror) {
                StructureMirror.NONE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STRUCTURE_MIRROR_NONE
                StructureMirror.LEFT_RIGHT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STRUCTURE_MIRROR_LEFT_RIGHT
                StructureMirror.FRONT_BACK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STRUCTURE_MIRROR_FRONT_BACK
            }
            SettingChoice(
                "mirror:${mirror.value}",
                KcI18n.text(player, labelKey),
                explicitSelected(node, fieldKey, context.role, node.string("mirror", StructureMirror.NONE.value) == mirror.value),
            )
        }
        GestureSettingScreen.STRUCTURE_NAME -> buildList {
            // 保存済みの建造物を候補として列挙します。存在しない場合は追加保存の
            // 手順を示すため、空候補には新規作成用の固定項目だけを出します。
            val existing = plugin.structures.list()
            existing.forEach { name ->
                add(
                    SettingChoice(
                        "structureName:$name",
                        name,
                        explicitSelected(node, fieldKey, context.role, node.string("structure") == name),
                    ),
                )
            }
            add(
                GestureSettingTreeNode(
                    id = "structure-name-direct",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DIRECT_VALUE),
                    selected = node.string("structure").isNotBlank() && node.string("structure") !in existing,
                ),
            )
        }
        // issue #24: 群操作は8操作の明示選択肢です。保存値は小文字文字列のため、
        // ここで各操作へ解決し直して選択表示と照合します。
        GestureSettingScreen.ENTITY_GROUP_OPERATION -> EntityGroupOperation.entries.map { operation ->
            val labelKey = when (operation) {
                EntityGroupOperation.CREATE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_CREATE
                EntityGroupOperation.ADD -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_ADD
                EntityGroupOperation.REMOVE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_REMOVE
                EntityGroupOperation.DELETE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_DELETE
                EntityGroupOperation.SET_PIVOT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_SET_PIVOT
                EntityGroupOperation.MOVE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_MOVE
                EntityGroupOperation.ROTATE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_ROTATE
                EntityGroupOperation.CLEANUP -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_CLEANUP
            }
            SettingChoice(
                "groupOperation:${operation.value}",
                KcI18n.text(player, labelKey),
                explicitSelected(node, fieldKey, context.role, node.string("operation") == operation.value),
            )
        }
        GestureSettingScreen.ENTITY_GROUP_NAME -> buildList {
            // 保存済みの群を候補として列挙します。存在しない場合は新規作成用の
            // 固定項目だけを出し、直接入力へ進めます。
            val existing = plugin.entityGroups.names()
            existing.forEach { name ->
                add(
                    SettingChoice(
                        "groupName:$name",
                        name,
                        explicitSelected(node, fieldKey, context.role, node.string("group") == name),
                    ),
                )
            }
            add(
                GestureSettingTreeNode(
                    id = "group-name-direct",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DIRECT_VALUE),
                    selected = node.string("group").isNotBlank() && node.string("group") !in existing,
                ),
            )
        }
    }

    /** 選択肢ラベルを「ラベル 現在値」形式へ結合します。未設定はGUI共通の未設定文言を使います。 */
    private fun labeled(player: Player, key: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>, value: String?): String =
        "${KcI18n.text(player, key)} ${value ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)}"

    /**
     * 単一選択カードの選択表示です。
     *
     * 初期時点では全項目を未設定とするため、既定値との一致だけでは選択表示にせず、
     * 明示設定記録がある場合だけ反映します。明示的に既定値を選んだ場合は記録が
     * 残るため選択表示になります。
     */
    private fun explicitSelected(
        node: CommandNode,
        fieldKey: String,
        role: CommandSettingRole?,
        valueMatches: Boolean,
    ): Boolean = valueMatches && CommandSettingsModel.isFieldConfigured(node, fieldKey, role)

    /**
     * 統合選択画面の選択肢です。
     *
     * タグ・attributeグループ・各データ項目を同じ階層に並べます。
     * attribute全種のフラット展開は行わず、attributeは子画面への親項目とします。
     * タグ・attribute・有限候補は子画面を持つ親項目であり、選択済みの
     * 再クリックで入室します（コードベースの共通契約）。選択表示は操作対象の項目、
     * 設定値はvalueStateと値欄へ別々に投影します。真偽値がfalseでも対象項目は選択中です。
     * 対象種別が確定できる場合のみ適用可能な項目を表示し、和集合表示は行いません。
     */
    private fun entityDataRootChoices(node: CommandNode, player: Player): List<SettingChoice> {
        val parsed = EntityDataPath.parse(node.string(EntityDataPath.PARAM_PATH))
        val currentProperty = (parsed as? EntityDataPath.Parsed.Property)?.propertyId
        val currentValue = node.string(EntityDataSchema.PARAM_VALUE)
        val entries = EntityTagEntries.parse(node.string(EntityTagEntries.PARAM_ENTRIES)).orEmpty()
        return buildList {
            add(
                GestureSettingTreeNode(
                    id = "entityData:tag",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_DATA_TAG),
                    selected = parsed is EntityDataPath.Parsed.Tag,
                    children = tagChildNodes(node, player, entries),
                ),
            )
            val knownType = EntityDataSchema.knownEntityType(node.targetSpec)
            // 非Living確定時はattribute群を提示しません。種類不明時は全件提示し、
            // 検証・実行時のcapability判定へ委ねます。
            if (knownType == null || knownType.isAlive) {
                add(
                    GestureSettingTreeNode(
                        id = "entityData:attribute",
                        label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_DATA_ATTRIBUTE),
                        selected = parsed is EntityDataPath.Parsed.Attribute,
                        children = EntityAttributeSupport.allAttributeIds().map { id ->
                            SettingChoice("attribute:$id", id, false)
                        },
                    ),
                )
            }
            EntityDataSchema.propertiesFor(knownType).forEach { definition ->
                val propertyState = EntityPropertyChoicePolicy.state(
                    activePropertyId = currentProperty,
                    definition = definition,
                    rawValue = currentValue,
                )
                when (definition.valueKind) {
                    // 操作対象の選択と真偽値を分けます。falseでも現在の対象項目として選択表示し、
                    // 実際のON/OFFは値欄、明示設定の有無はvalueStateで示します。
                    EntityPropertyValueKind.BOOLEAN -> add(
                        SettingChoice(
                            "entityData:property:${definition.id}",
                            KcI18n.text(player, definition.optionKey),
                            propertyState.selected,
                        ),
                    )
                    EntityPropertyValueKind.ENUM -> add(
                        GestureSettingTreeNode(
                            id = "entityData:property:${definition.id}",
                            label = KcI18n.text(player, definition.optionKey),
                            selected = propertyState.selected,
                            children = definition.enumChoices.map { choice ->
                                SettingChoice(
                                    "propertyValue:${choice.id}",
                                    KcI18n.text(player, choice.optionKey),
                                    currentValue == choice.id,
                                )
                            },
                        ),
                    )
                    EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> add(
                        SettingChoice(
                            "entityData:property:${definition.id}",
                            KcI18n.text(player, definition.optionKey),
                            propertyState.selected,
                        ),
                    )
                }
            }
        }
    }

    /**
     * issue #10: 召喚時個別設定の選択肢です。
     *
     * 既存Entity編集の統合選択画面と同じ階層・操作契約で、保存先だけを
     * 召喚時個別設定（`summonData_<propertyId>`）へ振り替えます。
     * 各行の選択表示は現在の操作経路、設定済み状態は共通valueStateへ渡し、
     * 真偽値や文字列の保存値を操作選択状態へ流用しません。
     * 召喚種類が確定できる場合のみ適用可能な項目を表示します。
     */
    private fun summonDataRootChoices(node: CommandNode, player: Player): List<SettingChoice> {
        val summonType = me.awabi2048.kantancommander.model.EntityDataSchema
            .knownSummonType(node.string("summonEntity"))
        val entries = me.awabi2048.kantancommander.model.EntityDataSchema.summonDataEntries(node.params)
        return buildList {
            me.awabi2048.kantancommander.model.EntityDataSchema.propertiesFor(summonType).forEach { definition ->
                val key = me.awabi2048.kantancommander.model.EntityDataSchema.summonDataKey(definition.id)
                val current = entries[definition.id]
                when (definition.valueKind) {
                    // 真偽値はON状態を選択表示し、トグル操作と一致させます。子画面を持ちません。
                    me.awabi2048.kantancommander.model.EntityPropertyValueKind.BOOLEAN -> add(
                        SettingChoice(
                            "summonData:${definition.id}",
                            KcI18n.text(player, definition.optionKey),
                            // ON/OFFは保存値表示に使い、操作選択として白枠へ流用しません。
                            selected = false,
                            description = current?.toBooleanStrictOrNull()?.let { enabled ->
                                KcI18n.text(
                                    player,
                                    if (enabled) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED
                                    else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED,
                                )
                            }.orEmpty(),
                        ),
                    )
                    me.awabi2048.kantancommander.model.EntityPropertyValueKind.ENUM -> add(
                        GestureSettingTreeNode(
                            id = "summonData:${definition.id}",
                            label = KcI18n.text(player, definition.optionKey),
                            selected = false,
                            description = definition.enumChoices.firstOrNull { it.id == current }
                                ?.let { KcI18n.text(player, it.optionKey) }.orEmpty(),
                            children = definition.enumChoices.map { choice ->
                                SettingChoice(
                                    "summonDataValue:${definition.id}:${choice.id}",
                                    KcI18n.text(player, choice.optionKey),
                                    current == choice.id,
                                )
                            },
                        ),
                    )
                    me.awabi2048.kantancommander.model.EntityPropertyValueKind.TEXT,
                    me.awabi2048.kantancommander.model.EntityPropertyValueKind.INTEGER,
                    -> add(
                        SettingChoice(
                            "summonData:${definition.id}",
                            KcI18n.text(player, definition.optionKey),
                            selected = false,
                            description = current.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * タグ親項目の子要素です。タグ行が空の場合は追加行マーカーで非空を保ち、
     * 入室判定が常に成立するようにします。
     */
    private fun tagChildNodes(
        node: CommandNode,
        player: Player,
        entries: List<EntityTagEntries.Entry>,
    ): List<SettingChoice> {
        if (entries.isEmpty()) {
            return listOf(
                SettingChoice(
                    "tagAdd",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TAG_ADD),
                    false,
                ),
            )
        }
        return entries.mapIndexed { index, entry ->
            SettingChoice("tagEntry:$index", entry.tag, false)
        }
    }

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
        detailVisited: Boolean = false,
    ): List<SettingChoice> {
        // 内包役割では位置・向き仕様の対象部を編集します。「一時変数」は
        // 内包TEMPORARY対象の実行経路が未整備のため出しません。
        val embedded = CommandSettingsModel.isEmbeddedTargetRole(context.role)
        val current = if (embedded) {
            CommandSettingsModel.embeddedTargetSpec(node, context.role)
        } else {
            CommandSettingsModel.targetSpec(node, context.role)
        }
        val configured = if (embedded) {
            current != null
        } else {
            CommandSettingsModel.isFieldConfigured(node, "target", context.role)
        }
        val presets = listOf(
            SettingPresets.TargetPreset.NEARBY_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS,
            SettingPresets.TargetPreset.MYWORLD_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS,
            SettingPresets.TargetPreset.NEAREST_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER,
            SettingPresets.TargetPreset.NEARBY_ENTITIES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES,
        ).map { (preset, label) ->
            SettingChoice(
                id = "target-preset:${preset.name}",
                label = KcI18n.text(player, label),
                selected = PresetSelectionDisplay.isPresetSelected(
                    SettingPresets.matchTargetPreset(current) == preset,
                    configured,
                    detailVisited,
                ),
            )
        }
        val detailed = buildList {
            add(
                SettingChoice(
                    id = "target-detail",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DETAIL_SETTINGS),
                    selected = PresetSelectionDisplay.isDetailSelected(
                        configured,
                        current == null || SettingPresets.matchTargetPreset(current) == null,
                        detailVisited,
                    ),
                    children = targetFilterChoices(node, context, player),
                    bottomAligned = true,
                ),
            )
            // 移動先では従来どおり一時変数対象を出さず、内包役割でも出しません。
            // 内包TEMPORARY対象の実行経路が未整備のため出しません。
            if (includeTemporary && !embedded && context.role != CommandSettingRole.DESTINATION) {
                add(
                    SettingChoice(
                        id = "target:${TargetCategory.TEMPORARY.name}",
                        label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REFERENCE_TEMPORARY),
                        selected = current?.kind == TargetKind.TEMPORARY && configured,
                        bottomAligned = true,
                    ),
                )
            }
        }
        return presets + detailed
    }

    internal fun targetFilterChoices(
        node: CommandNode,
        context: CommandSettingContext,
        player: Player,
        kindOverride: TargetKind? = null,
    ): List<SettingChoice> {
        // issue #29: 位置・向きの役割で開いた場合は内包対象を表示します。
        val currentSpec = (if (CommandSettingsModel.isEmbeddedTargetRole(context.role)) {
            CommandSettingsModel.embeddedTargetSpec(node, context.role)
        } else {
            CommandSettingsModel.targetSpec(node, context.role)
        }) ?: me.awabi2048.kantancommander.model.TargetSpec(kindOverride ?: TargetKind.NEAREST_ENTITY)
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
                    "region" -> CommandSettingsModel.formatRegionSummary(it.region) { key -> KcI18n.text(player, key) }
                    "limit" -> it.limit?.toString()
                    "tag" -> it.tag
                    else -> it.name
                }
            }
        }
        // Targetの上位画面で旧細分類（最も近い／全員／固定エンティティ等）は
        // 3カテゴリへ統一して削除しました。ここでは詳細条件だけを残し、削除済み
        // の選択肢を詳細画面へ再掲しないようにします。周囲系は最大距離だけを
        // 設定項目とし、他の絞り込みは提示しません。
        val filterChoices = listOf(
            "entityType" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_TYPE,
            "distance" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE,
            "range" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_RANGE,
            "region" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_REGION,
            "limit" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LIMIT,
            "sort" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SORT,
            "gameMode" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GAME_MODE,
            "tag" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG,
            "name" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME,
        )
            .filter { (id, _) -> id in CommandSettingsModel.visibleTargetFilterIds(spec.kind) }
            .map { (id, label) ->
                SettingChoice(
                    "filter:$id",
                    labeled(player, label, value(id)),
                    children = if (id == "region") regionChildren(spec.region, player, "filter") else emptyList(),
                )
            }
        return filterChoices
    }

    /**
     * issue #4・#5 の領域編集の子選択肢です。
     *
     * 基準選択・範囲・距離・基準別参数・解除を一つの行へ集約します。
     * `idPrefix` に対象絞り込み（`filter`）と条件（`condition`）の
     * 派生ID接頭辞を渡し、受付側の画面と共有します。基準別参数は現在の
     * 基準のときだけ出し、無関係な入力欄を提示しません。
     */
    private fun regionChildren(
        region: me.awabi2048.kantancommander.model.RegionSpec?,
        player: Player,
        idPrefix: String,
    ): List<SettingChoice> {
        val active = region?.takeIf { it.hasAnySetting() }
        val anchor = active?.anchor?.kind
        fun extents(): String? = active?.let {
            displayTargetRange(player, it.dx, it.dy, it.dz)
        }
        fun distances(): String? = active?.let {
            if (it.minimumDistance == null && it.maximumDistance == null) null
            else displayDistance(it.minimumDistance, it.maximumDistance)
        }
        val children = mutableListOf(
            SettingChoice(
                "$idPrefix:region-clear",
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CLEAR),
                selected = false,
            ),
        )
        // 基準選択は4択を常時提示します。選択時に範囲等は引き継ぎます。
        me.awabi2048.kantancommander.model.RegionAnchorKind.entries.forEach { kind ->
            children += SettingChoice(
                "$idPrefix:region-anchor:${kind.name}",
                KcI18n.text(player, CommandSettingsModel.regionAnchorNameKey(kind)),
                selected = anchor == kind,
            )
        }
        children += SettingChoice(
            "$idPrefix:region-range",
            labeled(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_RANGE, extents()),
        )
        children += SettingChoice(
            "$idPrefix:region-distance",
            labeled(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE, distances()),
        )
        if (anchor == me.awabi2048.kantancommander.model.RegionAnchorKind.FIXED) {
            children += SettingChoice(
                "$idPrefix:region-position",
                labeled(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION_COORDINATES,
                    active.anchor.position?.let { position ->
                        listOf(position.x, position.y, position.z).joinToString(" / ") { value ->
                            value?.let {
                                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                            }.orEmpty()
                        }
                    },
                ),
            )
        }
        if (anchor == me.awabi2048.kantancommander.model.RegionAnchorKind.TEMPORARY) {
            children += SettingChoice(
                "$idPrefix:region-temp",
                labeled(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REFERENCE_TEMPORARY,
                    active.anchor.tempName,
                ),
            )
        }
        if (anchor == me.awabi2048.kantancommander.model.RegionAnchorKind.TARGET_ENTITY) {
            children += embeddedTargetPresetChoices(
                active.anchor.target,
                player,
                "$idPrefix:region-target-preset",
                null,
            )
        }
        return children
    }

    /**
     * issue #29 の位置親画面です。
     *
     * 頻用する5プリセットを先頭に直接提示し、汎用設定（一時変数・
     * エンティティ由来位置）は下部導線へ残します。単一限定の役割では
     * 対象由来位置を無効化し、理由を表示します。
     */
    private fun positionChoices(node: CommandNode, context: CommandSettingContext, player: Player, inChild: Boolean = false, detailVisited: Boolean = false): List<SettingChoice> {
        // 移動先も位置画面と表示・文言・導線が完全同一です。保存先だけが
        // 移動先用に振り分けられます（受付側で対応します）。
        val current = CommandSettingsModel.positionSpec(node, context.role)
        val matchedPreset = SettingPresets.matchPositionPreset(current)
        val presets = listOf(
            SettingPresets.PositionPreset.CONTROL_BLOCK to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION,
            SettingPresets.PositionPreset.NEAREST_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION_NEAREST,
            SettingPresets.PositionPreset.NEARBY_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION_NEARBY,
            SettingPresets.PositionPreset.MYWORLD_SPAWN to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN,
            SettingPresets.PositionPreset.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION_COORDINATES,
        ).map { (preset, label) ->
            // issue #29: 無効化は取得元ではなく件数で判定し、静的0..1の
            // プリセットは単一限定でも許可します。
            val multiCapable = preset == SettingPresets.PositionPreset.NEARBY_PLAYERS
            val multiEnabled = !multiCapable ||
                CommandSettingAvailabilityPolicy.isEmbeddedTargetPositionEnabled(context.role)
            SettingChoice(
                id = "position-preset:${preset.name}",
                label = KcI18n.text(player, label),
                selected = PresetSelectionDisplay.isPresetSelected(
                    matchedPreset == preset,
                    positionChoiceComplete(node, context.role),
                    detailVisited,
                ),
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
        // 移動先では利用不可の候補を赤背景で残さず、一覧から隠します。
        .filter { choice -> choice.enabled || context.role != CommandSettingRole.DESTINATION }
        val entityEntry = SettingChoice(
            id = "position-entity",
            label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REFERENCE_ENTITY_POSITION),
            selected = current?.kind == PositionKind.TARGET && (matchedPreset == null || detailVisited) &&
                positionChoiceComplete(node, context.role),
            // 再クリックで共用の対象子画面へ進むため、子選択肢は持たせない。
            // 単一限定でも導線自体は開く。
            enabled = true,
            disabledHoverText = null,
            bottomAligned = true,
        )
        // 子画面では導線自体を隠し、内包プリセットの選択に集中させます。
        val bottomEntries = (if (inChild) emptyList() else listOf(entityEntry)) + listOf(
            SettingChoice(
                id = "position:${PositionKind.TEMPORARY.name}",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REFERENCE_TEMPORARY),
                selected = current?.kind == PositionKind.TEMPORARY &&
                    CommandSettingsModel.isPositionSpecComplete(current),
                bottomAligned = true,
            ),
        )
        return presets + bottomEntries
    }

    /**
     * 位置選択の表示用完全性です。
     *
     * 座標・一時変数は必須値の有無を要求します。移動先など対象指定に保存される
     * 場合は対象側の設定済みで判定します。選択表示されていても未完了の項目は
     * 未完了扱いにします。
     */
    private fun positionChoiceComplete(node: CommandNode, role: CommandSettingRole?): Boolean {
        val spec = CommandSettingsModel.positionSpec(node, role)
        if (spec != null) return CommandSettingsModel.isPositionSpecComplete(spec)
        return if (role == CommandSettingRole.DESTINATION) {
            CommandSettingsModel.isTargetSpecConfigured(CommandSettingsModel.targetSpec(node, role))
        } else false
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

    /** issue #29 のスポーン種別2択です。未設定時はどちらも選択表示にしません。 */
    private fun myworldSpawnChoices(
        current: me.awabi2048.kantancommander.model.MyworldSpawnKind?,
        player: Player,
    ): List<SettingChoice> {
        return listOf(
            me.awabi2048.kantancommander.model.MyworldSpawnKind.MEMBER to
                com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_MEMBER,
            me.awabi2048.kantancommander.model.MyworldSpawnKind.GUEST to
                com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_GUEST,
        ).map { (kind, label) ->
            SettingChoice(
                id = "myworld-spawn:${kind.name}",
                label = KcI18n.text(player, label),
                selected = current == kind,
            )
        }
    }

    private fun embeddedTargetPresetChoices(
        current: TargetSpec?,
        player: Player,
        prefix: String,
        role: CommandSettingRole?,
    ): List<SettingChoice> {
        val matched = SettingPresets.matchTargetPreset(current)
        // issue #29: 単一限定では複数になり得る内包候補だけを無効化し、
        // 静的0..1の選択は許可します。
        val singleRole = MultiPositionPolicy.multiplicityFor(role) == PositionMultiplicity.SINGLE
        return listOf(
            SettingPresets.TargetPreset.NEARBY_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS,
            SettingPresets.TargetPreset.MYWORLD_PLAYERS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS,
            SettingPresets.TargetPreset.NEAREST_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER,
            SettingPresets.TargetPreset.NEARBY_ENTITIES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES,
        ).map { (preset, label) ->
            val multiCapable = preset != SettingPresets.TargetPreset.NEAREST_PLAYER
            val enabled = !singleRole || !multiCapable
            SettingChoice(
                id = "$prefix:${preset.name}",
                label = KcI18n.text(player, label),
                selected = matched == preset,
                enabled = enabled,
                disabledHoverText = if (enabled) null
                else CommandSettingAvailabilityPolicy.MULTI_POSITION_DISABLED_HOVER,
            )
        }
    }

    /**
     * issue #29 の向き親画面です。
     *
     * 頻用する3プリセットを先頭に直接提示し、汎用設定（一時変数・
     * エンティティ指定）は下揃えの詳細導線へ残します。
     * 注視・複写は単一体参照の汎用導線へ載せます。一時変数はLOCATIONのみです。
     */
    private fun facingChoices(node: CommandNode, context: CommandSettingContext, player: Player, inChild: Boolean = false, detailVisited: Boolean = false): List<SettingChoice> {
        val current = CommandSettingsModel.facingSpec(node, context.role)
        val matchedPreset = SettingPresets.matchFacingPreset(current)
        // 追加入力が必要な選択は、選択表示でも必須値が揃うまで未完了とします。
        val complete = CommandSettingsModel.isFacingSpecComplete(current)
        val presets = listOf(
            SettingPresets.FacingPreset.NEAREST_PLAYER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER,
            SettingPresets.FacingPreset.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES,
            SettingPresets.FacingPreset.NUMERIC to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMERIC,
        ).map { (preset, label) ->
            SettingChoice(
                id = "facing-preset:${preset.name}",
                label = KcI18n.text(player, label),
                selected = PresetSelectionDisplay.isPresetSelected(
                    matchedPreset == preset,
                    complete,
                    detailVisited,
                ),
            )
        }
        // 注視は上段末尾に置き、上下4:2の配置にします。
        val upperEntity = if (inChild) emptyList() else listOf(
            SettingChoice(
                id = "facing-entity",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET),
                selected = current?.kind == FacingKind.TARGET && (matchedPreset == null || detailVisited) && complete,
                children = embeddedTargetPresetChoices(current?.target, player, "facing-entity-preset", context.role),
            ),
        )
        return presets + upperEntity + listOf(
            SettingChoice(
                id = "facing-copy",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COPY_ORIENTATION),
                selected = current?.kind == FacingKind.COPY_ORIENTATION && complete,
                children = embeddedTargetPresetChoices(current?.target, player, "facing-copy-preset", context.role),
                bottomAligned = true,
            ),
        ) + listOf(
            SettingChoice(
                id = "facing:${FacingKind.TEMPORARY.name}",
                label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE),
                selected = current?.kind == FacingKind.TEMPORARY && complete,
                bottomAligned = true,
            ),
        )
    }

    /**
     * issue #27: 「条件の種類」を「判定対象」の5区分で列挙します。
     * 旧 `TARGET_EXISTS` は独立カテゴリとして再露出させず、その他存在判定へ統合します。
     * 選択肢IDは `condition-kind:<判定対象名>` で、旧 `condition-kind:<種類名>` とは別体系です。
     * #29の向き親画面（facingChoices）は維持します。
     */
    private fun conditionKindChoices(node: CommandNode, player: Player): List<SettingChoice> =
        ConditionSubject.entries.map { subject ->
            SettingChoice("condition-kind:${subject.name}", KcI18n.text(player, subject.key), node.conditionSubject() == subject)
        }

    private fun conditionDetailChoices(node: CommandNode, player: Player): List<SettingChoice> {
        // issue #27: 選択した判定対象に応じて詳細欄を切り替えます。
        // 未設定・不正な判定対象は詳細欄を出さず、判定対象タブでの選択を促します。
        val subject = node.conditionSubject() ?: return emptyList()
        fun label(key: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>, value: String?): String =
            labeled(player, key, value)
        return when (subject) {
            // その他エンティティは対象設定そのものを存在判定として扱います。
            // 追加条件なしでも対象設定だけで成立します。
            ConditionSubject.OTHER_ENTITY -> listOf(
                SettingChoice(
                    "condition-target",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                    node.targetSpec != null,
                ),
            )
            ConditionSubject.PLAYER -> listOf(
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
            ConditionSubject.VARIABLE -> listOf(
                SettingChoice("condition-variable", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, node.string("variable"))),
                SettingChoice("condition-operator", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATOR, node.string("operator", "=="))),
                SettingChoice("condition-value", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, node.string("value", "0"))),
            )
            // issue #5: 型付き条件比較は左辺（範囲＋変数）・演算子・右辺で構成します。
            // 簡単な条件プリセット（既存5区分）はそのまま残し、詳細比較はこの
            // 判定対象を選んだ場合の高度設定として展開します。
            // Boolean直接条件と領域述語は同じ行構成へ載せ、演算子・右辺・領域の
            // 表示を現在の指定に合わせます。
            ConditionSubject.VALUE -> buildList {
                add(
                    SettingChoice(
                        "condition-left-scope",
                        label(
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE_SCOPE,
                            when (node.string("leftScope").ifBlank { VariableScope.WORLD.name }) {
                                VariableScope.EXECUTION.name -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_EXECUTION)
                                VariableScope.ENTITY.name -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_ENTITY)
                                else -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_WORLD)
                            },
                        ),
                    ),
                )
                add(SettingChoice("condition-left", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, node.string("leftName"))))
                val operator = node.string("operator")
                if (operator.isBlank()) {
                    add(
                        SettingChoice(
                            "condition-direct",
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONDITION_DIRECT),
                            selected = true,
                        ),
                    )
                } else {
                    add(SettingChoice("condition-operator", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATOR, operator)))
                    if (me.awabi2048.kantancommander.model.ConditionComparison.isRegionPredicate(operator)) {
                        add(
                            SettingChoice(
                                "condition-region",
                                label(
                                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_REGION,
                                    CommandSettingsModel.formatRegionSummary(node.regionSpec) { key ->
                                        KcI18n.text(player, key)
                                    },
                                ),
                                children = regionChildren(node.regionSpec, player, "condition"),
                            ),
                        )
                    } else {
                        add(SettingChoice("condition-right", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, node.string("right"))))
                    }
                    add(
                        SettingChoice(
                            "condition-direct",
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONDITION_DIRECT),
                            selected = false,
                        ),
                    )
                }
            }
            ConditionSubject.BLOCK -> listOf(
                SettingChoice("condition-position", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONDITION_POSITION), node.conditionPositionSpec != null),
                SettingChoice("condition-block", label(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK, node.string("block", "minecraft:air"))),
            )
            // 制御ブロック自身の状態は、今後の項目追加を前提に複数選択の木として
            // 表示します。選択済み集合の評価は実行モデル側でANDへ集約します。
            ConditionSubject.CONTROL_BLOCK -> ControlBlockStateKind.entries.map { state ->
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
        VariableOperation.SET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SET
        VariableOperation.CHANGE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHANGE
        VariableOperation.COPY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_OPERATION_COPY
        VariableOperation.DELETE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REMOVE
    }

    /**
     * 表示値をComponentへ変換します。
     *
     * 種別名はバニラ翻訳のtranslatableで渡し、各クライアントの言語で
     * 描画させます。独自翻訳は持ちません。
     */
    private fun displayValueComponent(player: Player, display: DisplayValue): Component = when (display) {
        is DisplayValue.EntityTypeName -> display.entityComponent()
        else -> Component.text(display.render(player))
    }

    private fun settingCurrentValue(
        node: CommandNode,
        context: CommandSettingContext,
        screen: GestureSettingScreen,
        fieldKey: String,
        player: Player,
    ): String = when (screen) {
        // enum名を直接見せず、インベントリGUIと同じ日本語表示へ統一します。
        // issue #34-1: 内包役割では位置・向き仕様の内部対象を表示します。
        GestureSettingScreen.TARGET -> (if (CommandSettingsModel.isEmbeddedTargetRole(context.role)) {
            CommandSettingsModel.embeddedTargetSpec(node, context.role)
        } else {
            CommandSettingsModel.targetSpec(node, context.role)
        })?.kind
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
        FacingKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET
        FacingKind.COPY_ORIENTATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COPY_ORIENTATION
        FacingKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
        FacingKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES
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
                // issue #review P2-10: 説明の2行構成（機能説明・バニラ対応）を
                // 空白結合で潰さず、行構造のままカードへ渡します。
                val hoverLines = KcI18n.list(player, type.descriptionKey)
                    .filter(String::isNotBlank)
                val hover = parallelButtonHoverLines(
                    hoverLines,
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
                PickerCard(type.name, KcI18n.text(player, type.key), hoverLines, hover.x, hover.y)
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
                plugin.logger.fine("[KantanCommander][DeclarativePicker] $diagnostics")
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
                plugin.logger.fine("[KantanCommander][DeclarativeConfirm] $diagnostics")
            },
        )
        return view(GestureLowerMode.CONFIRM, compiled.elements, compiled.visuals, child = true)
    }

    private fun view(
        mode: GestureLowerMode,
        elements: List<GestureGuiElement>,
        visuals: List<GestureGuiVisual>,
        child: Boolean = false,
        childScreenId: String? = null,
    ): GestureGuiView = GestureGuiView(
        GestureGuiScreenDefinition(
            when {
                mode == GestureLowerMode.CONFIRM -> CONFIRM_SCREEN_ID
                mode == GestureLowerMode.TEST_CONFIRM -> TEST_CONFIRM_SCREEN_ID
                child -> childScreenId ?: SETTING_CHILD_SCREEN_ID
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
        // 上限は通常タブ列だけに適用し、固定行へ抽出する実行位置タブは数えません。
        const val SETTINGS_TAB_MAX = 6
        /** 固定行へ抽出する実行位置タブのフィールドキーです。上限検査から除外します。 */
        const val EXECUTION_POSITION_FIELD_KEY = "executionPosition"
        const val SETTINGS_TAB_CENTER_X = -0.7975
        const val SETTINGS_TAB_WIDTH = 0.47
        const val SETTINGS_TAB_TOP_Y = 0.38
        const val SETTINGS_TAB_PITCH = 0.11
        const val SETTINGS_TAB_HEIGHT = 0.10
        // 左列下端の固定操作（ノード選択中の削除／新規追加画面の閉じる）は同じ位置へ置き、
        // 画面種別が変わっても操作導線を一定に保ちます。
        const val SETTINGS_TAB_ACTION_Y = -0.43
        // 実行位置タブを固定配置する、下端の操作行の直上の行です。
        const val SETTINGS_TAB_EXECUTION_Y = SETTINGS_TAB_ACTION_Y + SETTINGS_TAB_PITCH
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
        // 下揃え項目（詳細設定・一時変数参照等）の最下行です。上からの5行目に相当し、
        // 下端基準のため上位項目数の増減で位置がずれません。
        const val SETTING_CHOICE_BOTTOM_Y = -0.36
        // 二値トグル（Checkbox直接切替え）の水平中央です。右ペインの
        // 2列中央（-0.10と0.67の中点）に相当し、単独配置で中央下揃えになります。
        const val SETTING_TOGGLE_CENTER_X = 0.28
        // 子画面は親の右ペイン座標を再利用せず、子画面全体を論理領域として使います。
        const val CHILD_CHOICE_WIDTH = 0.92
        const val CHILD_CHOICE_TOP_Y = 0.08
        const val CHILD_CHOICE_PITCH = 0.12
        const val CHILD_HEADER_Y = 0.36
        const val CHILD_HOVER_Y = 0.23
        const val CHILD_DETAIL_HINT_Y = 0.16
        const val CHILD_FOOTER_Y = -0.43
        const val CHILD_BACK_Y = -0.43
        const val CHILD_PAGER_Y = -0.34
        const val CHILD_BACK_WIDTH = 1.70
        // ワールド内変数の各列は「値編集カード＋削除ボタン」で構成し、列全体の幅を
        // 既存の0.92へ固定します。ボタンの位置は一覧と確認画面で共通にします。
        const val WORLD_VARIABLE_CARD_WIDTH = 0.72
        const val WORLD_VARIABLE_DELETE_GAP = 0.05
        const val WORLD_VARIABLE_DELETE_WIDTH = 0.15
        // タグ一覧の各列は「名札＋モード＋削除」で構成します。モード枠は削除枠と
        // 同一寸法とし、3枠総幅（1.00）を列ピッチ（1.06）以内に収めます。
        const val TAG_CARD_WIDTH = 0.60
        const val TAG_ICON_GAP = 0.05
        const val TAG_MODE_WIDTH = WORLD_VARIABLE_DELETE_WIDTH
        // タグ一覧は変数一覧と同じく2列×3行に制限します。
        const val TAG_PAGE_SIZE = 6
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
        // 親タブ直下の資源プリセット配置です。子画面遷移は行いません。
        // 右詳細域へ収まるよう、8列×3行で24件を収めます。図柄のはみ出しを
        // 防ぐため、セル寸法に対して図柄係数と行間を一体で調整します。
        const val INLINE_RESOURCE_COLUMNS = 8
        const val INLINE_RESOURCE_CELL_W = 0.13
        const val INLINE_RESOURCE_CELL_H = 0.085
        const val INLINE_RESOURCE_GRID_PITCH_X = 0.14
        const val INLINE_RESOURCE_GRID_PITCH_Y = 0.10
        // グリッド重心を値入力ボタンの中心（0.28）へ合わせます。
        // 外縁-0.275〜0.835に対し余白が左右0.045ずつ均等となり、
        // グリッド＋余白の横幅はボタンの1.2と一致します。
        const val INLINE_RESOURCE_GRID_LEFT_X = -0.21
        // 通常入力行の直下へ3行を置くための、入力行からの下げ幅です。
        // 入力カードとの余白確保のため、グリッド全体はこの分だけ上に寄ります。
        const val INLINE_RESOURCE_GRID_TOP_DY = -0.01
        // ホバー文言をグリッド上側へ置くための、グリッド上端からの上げ幅です。
        const val INLINE_RESOURCE_HOVER_ABOVE_GAP = 0.03
        // 一覧の下へ入力カードを置くための、グリッド最終行を超える下げ幅です。
        const val INLINE_RESOURCE_INPUT_BELOW_DY = 0.13
        /** 構造化モデルを壊さず、paramsへ文字列として保存できる項目だけを許可します。 */
        val TEXT_EDITABLE_KEYS = setOf(
            "item", "itemData", "count", "text", "subtitle", "customName", "tags", "sound", "soundSetting", "soundParameters", "volume", "pitch", "particle", "particleParameters", "particleData",
            "particleDensity", "burstHeight",
            "effect", "level", "seconds", "fadeInSeconds", "staySeconds", "fadeOutSeconds", "intensity", "slot", "summonEntity", "diskId", "name",
            "condition", "variable", "value", "block", "sneaking",
            "motionX", "motionY", "motionZ",
            EntityTagEntries.PARAM_ENTRIES,
            EntityAttributeSupport.PARAM_VALUE,
            EntityDataSchema.PARAM_VALUE,
        )
    }
}
