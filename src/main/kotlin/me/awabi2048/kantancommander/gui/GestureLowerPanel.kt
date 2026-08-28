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
import kotlin.math.sqrt

/**
 * ジェスチャーエディターの下部パネル（左右 1:3 分割型）のビュー生成を担います。
 *
 * - SETTINGS: 左タブ列＝設定フィールド、右詳細＝現在値＋候補
 * - PICKER: 左タブ列＝コマンドカテゴリ（PROCESS/CONTROL）、右詳細＝種別一覧
 * - CONFIRM: 上部エディターが子画面（openChild・赤ガラス）として開く
 *
 * 座標は画面中央原点・ブロック単位。左列: x=-0.7975 / 右ペイン: x∈[-0.50, 1.03]。
 */
class GestureLowerPanel(
    private val plugin: KantanCommanderPlugin,
    private val onAction: (GestureGuiActionContext) -> Unit = {},
) {
    val LOWER_SCREEN_ID = "gesture-editor-lower"
    /** 個別設定専用。親の下部画面を残したまま前面へ重ねます。 */
    val SETTING_CHILD_SCREEN_ID = "gesture-editor-setting-child"
    val CONFIRM_SCREEN_ID = "gesture-editor-confirm"

    /** 子画面の面積を親の約90%にする一様縮尺です（sqrt(0.9)）。 */
    private val SETTING_CHILD_SCALE = sqrt(0.9)

    fun build(state: GestureEditorState, player: Player): GestureGuiView {
        return when (state.lowerMode) {
            GestureLowerMode.SETTINGS -> buildSettings(state, player)
            GestureLowerMode.PICKER -> buildPicker(state, player)
            GestureLowerMode.SETTING_CHOICES -> buildSettingChoices(state, player)
            GestureLowerMode.CONFIRM -> buildConfirm(state, player)
        }
    }

    /** 親画面を残して重ねる個別設定子画面を生成します。 */
    fun buildSettingChild(state: GestureEditorState, player: Player): GestureGuiView =
        buildSettingChoices(state, player, child = true)

    /** SETTINGS: 左タブ列＋固定操作、右詳細＝値表示と編集導線です。 */
    private fun buildSettings(state: GestureEditorState, player: Player): GestureGuiView {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        val script = plugin.scripts.load(state.scriptId)
        val node = state.selectedNodeId?.let { id -> script?.graph?.nodes?.get(id) }
        if (node == null) {
            addText(visuals, "lower-hint", 0.0, 0.20, 0.010, 160, Component.text("ノードを選択してください"))
            return view(GestureLowerMode.SETTINGS, elements, visuals)
        }

        val fields = CommandSettingsModel.visibleFields(node)
        if (fields.isEmpty()) {
            addText(visuals, "lower-hint", 0.28, 0.20, 0.010, 160, Component.text("設定項目はありません"))
            return view(GestureLowerMode.SETTINGS, elements, visuals)
        }
        val pageCount = addSettingsNavigation(state, player, node, visuals, elements)
        val page = state.settingsPage.coerceIn(0, pageCount - 1)
        val pageStart = page * SETTINGS_PAGE_SIZE
        val tabs = fields.drop(pageStart).take(SETTINGS_PAGE_SIZE)
        val selectedAbsolute = state.settingsTab.coerceIn(0, fields.lastIndex)
        val selected = if (selectedAbsolute in pageStart until pageStart + tabs.size) selectedAbsolute - pageStart else 0
        val field = tabs[selected]
        val value = field.value(node).render(player)
        addDescriptionRows(visuals, player, field, null)
        addValueRow(visuals, "lower-setting", 0.26, KcI18n.text(player, field.label), value)

        val descriptor = CommandSettingsModel.descriptor(node, field.key)
        // 専用選択項目はタブ選択時に直接開くため、ここには余分なボタンを置きません。
        // 文字列・数値など、専用モデルを持たない項目だけダイアログ入力を表示します。
        val heldItemSetting = field.key == "item" && node.type in setOf(CommandType.GIVE_ITEM, CommandType.EQUIP_ITEM)
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
                Component.text(if (heldItemSetting) "メインハンドから設定" else "値を入力"),
            )
            elements.add(GestureGuiElement(
                elementId = "lower-edit:${field.key}",
                bounds = rect(0.28, 0.02, 1.2, 0.16),
                // 空のメインハンドでは入力面自体を無効化し、CC-Systemの
                // クリック音も発生させません。
                acceptedGestures = if (heldItemSetting && !mainHandAvailable) emptySet() else setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "lower-edit-bg",
                hoverText = twoLineHover(
                    top = fieldDescription(player, field),
                    bottom = if (heldItemSetting) "メインハンドのアイテム情報を保存します" else "ダイアログで値を入力します",
                    x = 0.28,
                    y = 0.39,
                ),
            ))
        }
        if (heldItemSetting) {
            addBlock(visuals, "lower-item-get-bg", 0.28, -0.15, 1.2, 0.12, Material.BROWN_CONCRETE, 4)
            addText(visuals, "lower-item-get", 0.28, -0.15, 0.0048, 180, Component.text("設定中のアイテムを取得"))
            elements.add(GestureGuiElement(
                elementId = "lower-item-get",
                bounds = rect(0.28, -0.15, 1.2, 0.12),
                // 設定値がない取得ボタンは入力面を作らず、効果音を含めて無操作にします。
                acceptedGestures = if (!configuredItem) {
                    emptySet()
                } else setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "lower-item-get-bg",
                hoverText = twoLineHover(
                    top = fieldDescription(player, field),
                    bottom = "保存済みのアイテムをインベントリへ取得します",
                    x = 0.28,
                    y = 0.39,
                ),
            ))
        }
        return view(GestureLowerMode.SETTINGS, elements, visuals)
    }

    /**
     * 設定タブと固定操作を全モードで同じように描画します。
     * 専用選択モードへ入っても左側を消さず、タブクリックだけで別項目へ
     * 移れるようにすることが、無駄なページ移動をなくすための重要な共通部です。
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
                hoverText = twoLineHover(
                    top = fieldDescription(player, field),
                    bottom = fieldActionDescription(player, field),
                    x = 0.28,
                    y = 0.39,
                ),
            ))
        }

        val contextY = -0.29
        if (CommandPresentationPolicy.supportsContextOverride(node.type)) {
            val activeContext = state.settingFieldKey == "context" && state.settingScreen == GestureSettingScreen.CONTEXT_OVERRIDE
            addBlock(visuals, "context-bg", -0.7975, contextY, 0.47, 0.14,
                if (activeContext) Material.YELLOW_CONCRETE else Material.YELLOW_TERRACOTTA, 4)
            addText(visuals, "context-label", -0.7975, contextY - 0.018, 0.0045, 110, Component.text("実行コンテキスト上書き"))
            elements.add(GestureGuiElement(
                elementId = "lower-context",
                bounds = rect(-0.7975, contextY, 0.47, 0.14),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "context-bg",
                hoverText = twoLineHover(
                    top = "実行コンテキスト上書き",
                    bottom = "このノードだけ実行者・対象・位置・向きを上書きします",
                    x = 0.28,
                    y = 0.39,
                ),
            ))
        }

        val deleteY = -0.43
        addBlock(visuals, "delete-bg", -0.7975, deleteY, 0.47, 0.10, Material.RED_CONCRETE, 4)
        addText(visuals, "delete-label", -0.7975, deleteY, 0.0049, 90, Component.text("削除"))
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
    ) {
        addText(visuals, "$id-label", -0.08, y, 0.0060, 120, Component.text(label))
        addText(visuals, "$id-value", 0.52, y, 0.0060, 170, Component.text(value))
    }

    /** タブ説明と、現在ホバーしている候補の説明を2行に分けて表示します。 */
    private fun addDescriptionRows(
        visuals: MutableList<GestureGuiVisual>,
        player: Player,
        field: EditorField?,
        hoveredDescription: String?,
        fallback: String = "設定項目",
    ) {
        addText(
            visuals,
            "setting-description-tab",
            0.28,
            0.43,
            0.0047,
            280,
            Component.text(field?.let { fieldDescription(player, it) } ?: fallback),
        )
        addText(
            visuals,
            "setting-description-hover",
            0.28,
            0.36,
            0.0043,
            280,
            Component.text(
                hoveredDescription
                    ?: field?.let { fieldActionDescription(player, it) }
                    ?: "",
                NamedTextColor.GRAY,
            ),
        )
    }

    private fun fieldDescription(player: Player, field: EditorField): String =
        KcI18n.list(player, field.descriptionKey).firstOrNull()?.takeIf(String::isNotBlank)
            ?: KcI18n.text(player, field.label)

    private fun fieldActionDescription(player: Player, field: EditorField): String =
        if (field.key == "item") "メインハンドのアイテム情報を保存・取得します"
        else KcI18n.text(player, field.actionKey)

    private fun choiceDescription(choice: SettingChoice): String = when {
        choice.id == "open-filters" -> "距離・種類・除外条件などの対象オプションを設定します"
        choice.id.startsWith("target:") -> "このコマンドの対象種別を設定します"
        choice.id == "filter:entityType" -> "対象に含めるエンティティ種別を minecraft:zombie の形式で指定します"
        choice.id == "filter:minimumDistance" -> "実行位置からこの距離以上にいる対象だけを選びます。空欄で制限しません"
        choice.id == "filter:maximumDistance" -> "実行位置からこの距離以下にいる対象だけを選びます。空欄で制限しません"
        choice.id == "filter:limit" -> "候補から選ぶ最大件数を指定します。1以上の整数を入力します"
        choice.id == "filter:sort" -> "複数の対象があるとき、距離順またはランダムで選ぶ方法を切り替えます"
        choice.id == "filter:gameMode" -> "プレイヤー対象をゲームモードで絞り込みます。未設定なら全モードです"
        choice.id == "filter:tag" -> "指定したスコアボードタグを持つ対象だけを選びます。空欄で解除します"
        choice.id == "filter:name" -> "対象名で絞り込みます。空欄で解除します"
        choice.id.startsWith("filter:") -> "対象の種類・距離・件数などを組み合わせて実行対象を絞り込みます"
        choice.id.startsWith("position:") -> "実行位置をこの方式へ変更します"
        choice.id.startsWith("facing:") -> "実行時の向きをこの方式へ変更します"
        choice.id.startsWith("context:") -> "実行コンテキストの上書き内容を変更します"
        choice.id.startsWith("condition-") -> "条件分岐で判定する内容を設定します"
        choice.id.startsWith("display:") -> "文字列を表示する場所を設定します"
        choice.id.startsWith("action:") -> "対象エンティティへの操作を設定します"
        choice.id.startsWith("scope:") -> "変数を保存する範囲を設定します"
        choice.id.startsWith("type:") -> "変数へ保存するデータ型を設定します"
        choice.id.startsWith("operation:") -> "変数へ適用する操作を設定します"
        choice.id.startsWith("value:") -> "変数へ設定する値の入力方法を選びます"
        choice.id.startsWith("source:") -> "ループ値の参照元を設定します"
        choice.id.startsWith("inclusive:") -> "終端値を含めるか設定します"
        else -> "クリックで設定"
    }

    private fun twoLineHover(top: String, bottom: String, x: Double, y: Double): GestureGuiHoverText =
        GestureGuiHoverText(
            text = Component.text(top)
                .append(Component.newline())
                .append(Component.text(bottom, NamedTextColor.GRAY)),
            x = x,
            y = y,
            size = 0.0048,
            lineWidth = 280,
        )

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
        if (child) {
            // 子画面の余白自体も入力面にします。ボタン以外をクリックしたときに
            // 子画面を閉じ、背面の親画面へ誤って入力が透過しないようにします。
            elements += GestureGuiElement(
                elementId = "setting-child-empty",
                bounds = rect(0.0, 0.0, GestureEditorLayout.LOWER_W, GestureEditorLayout.LOWER_H),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            )
        }
        if (context == null || node == null || fieldKey == null || screen == null) {
            addText(visuals, "setting-hint", 0.28, 0.20, 0.008, 220, Component.text("設定対象がありません"))
            addBackSetting(elements, visuals)
            return view(GestureLowerMode.SETTING_CHOICES, elements, visuals, child = child)
        }

        // 専用選択中も設定タブを同じ左領域へ描画し、下部画面全体が
        // 「右ペインだけに置き換わる」状態にならないようにします。
        // 左ナビ・コンテキスト上書き・削除を専用選択画面でも残します。
        // 設定タブのページャーは候補ページャーと横方向に分離します。
        addSettingsNavigation(state, player, node, visuals, elements, pagerCenterX = -0.30)
        val field = CommandSettingsModel.visibleFields(node).firstOrNull { it.key == fieldKey }
        val fieldLabel = field?.let { KcI18n.text(player, it.label) }
            ?: if (fieldKey == "context") "実行コンテキスト上書き" else fieldKey
        val fieldValue = field?.value?.invoke(node)?.render(player)
            ?: if (screen == GestureSettingScreen.CONTEXT_OVERRIDE) {
                if (node.contextOverride == null) "すべて継承" else "一部を上書き"
            } else settingCurrentValue(node, context, screen, fieldKey, player)
        addDescriptionRows(visuals, player, field, null, fallback = fieldLabel)
        addValueRow(visuals, "setting-header", 0.26, fieldLabel, fieldValue)

        val choices = settingChoices(node, context, screen, fieldKey, player)
        val pageSize = SETTING_CHOICE_PAGE_SIZE
        val pageCount = (choices.size + pageSize - 1) / pageSize
        val page = state.settingPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        choices.drop(page * pageSize).take(pageSize).forEachIndexed { index, choice ->
            val column = index % 2
            val row = index / 2
            val cx = if (column == 0) -0.10 else 0.67
            val cy = 0.23 - row * SETTING_CHOICE_PITCH
            val bgId = "setting-choice-bg-$index"
            addBlock(
                visuals,
                bgId,
                cx,
                cy,
                SETTING_CHOICE_WIDTH,
                SETTING_CHOICE_HEIGHT,
                if (choice.selected) Material.CYAN_CONCRETE else Material.CYAN_TERRACOTTA,
                4,
            )
            addText(visuals, "setting-choice-label-$index", cx, cy - 0.012, 0.0045, 115, Component.text(choice.label))
            elements.add(GestureGuiElement(
                elementId = "lower-setting-choice:${choice.id}",
                bounds = rect(cx, cy, SETTING_CHOICE_WIDTH, SETTING_CHOICE_HEIGHT),
                // 条件のアイテム設定もメインハンド実体がない場合は無効化し、
                // 「何もしない」操作でクリック音だけが鳴る状態を防ぎます。
                acceptedGestures = if (choice.id == "condition-item" && player.inventory.itemInMainHand.type == Material.AIR) {
                    emptySet()
                } else setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = bgId,
                hoverText = twoLineHover(
                    top = field?.let { fieldDescription(player, it) } ?: fieldLabel,
                    bottom = choice.description.ifBlank { choiceDescription(choice) },
                    x = 0.28,
                    y = 0.39,
                ),
            ))
        }
        if (pageCount > 1) addPager(visuals, elements, "setting", page, pageCount, 0.25, -0.43)
        if (screen != GestureSettingScreen.CONTEXT_OVERRIDE) {
            addBackSetting(elements, visuals, centerX = 0.78, width = 0.42)
        }
        return view(GestureLowerMode.SETTING_CHOICES, elements, visuals, child = child)
    }

    private fun addBackSetting(
        elements: MutableList<GestureGuiElement>,
        visuals: MutableList<GestureGuiVisual>,
        centerX: Double = 0.67,
        width: Double = 0.66,
    ) {
        addBlock(visuals, "setting-back-bg", centerX, -0.43, width, 0.10, Material.BROWN_CONCRETE, 4)
        addText(visuals, "setting-back-label", centerX, -0.43, 0.0048, 100, Component.text("戻る"))
        elements.add(GestureGuiElement(
            elementId = "lower-setting-back",
            bounds = rect(centerX, -0.43, width, 0.10),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            targetVisualId = "setting-back-bg",
        ))
    }

    private data class SettingChoice(
        val id: String,
        val label: String,
        val selected: Boolean = false,
        val description: String = "",
    )

    private fun settingChoices(
        node: CommandNode,
        context: CommandSettingContext,
        screen: GestureSettingScreen,
        fieldKey: String,
        player: Player,
    ): List<SettingChoice> = when (screen) {
        GestureSettingScreen.TARGET -> targetChoices(node, context, player)
        GestureSettingScreen.TARGET_FILTERS -> targetFilterChoices(node, context, player)
        GestureSettingScreen.POSITION -> positionChoices(node, context, player)
        GestureSettingScreen.FACING -> facingChoices(node, player)
        GestureSettingScreen.CONDITION_KIND -> conditionKindChoices(node, player)
        GestureSettingScreen.CONDITION_DETAIL -> conditionDetailChoices(node, player)
        GestureSettingScreen.DISPLAY_MODE -> listOf(
            SettingChoice("display:tellraw", "チャット", node.string("mode", "tellraw") == "tellraw"),
            SettingChoice("display:title", "タイトル", node.string("mode", "tellraw") == "title"),
            SettingChoice("display:actionbar", "アクションバー", node.string("mode", "tellraw") == "actionbar"),
        )
        GestureSettingScreen.ENTITY_ACTION -> listOf(
            SettingChoice("action:ride", "乗る", node.string("action", "ride") == "ride"),
            SettingChoice("action:dismount", "降りる", node.string("action", "ride") == "dismount"),
        )
        GestureSettingScreen.VARIABLE_SCOPE -> listOf(
            SettingChoice("scope:TEMPORARY", "一時変数", node.string("scope", VariableScope.TEMPORARY.name) == VariableScope.TEMPORARY.name),
            SettingChoice("scope:WORLD", "ワールド内変数", node.string("scope", VariableScope.TEMPORARY.name) == VariableScope.WORLD.name),
        )
        GestureSettingScreen.VARIABLE_TYPE -> listOf(
            SettingChoice("type:BOOLEAN", "真偽値", node.string("type", VariableType.BOOLEAN.name) == VariableType.BOOLEAN.name),
            SettingChoice("type:INTEGER", "整数", node.string("type", VariableType.BOOLEAN.name) == VariableType.INTEGER.name),
            SettingChoice("type:DECIMAL", "小数", node.string("type", VariableType.BOOLEAN.name) == VariableType.DECIMAL.name),
            SettingChoice("type:TEXT", "文字列", node.string("type", VariableType.BOOLEAN.name) == VariableType.TEXT.name),
            SettingChoice("type:POSITION", "位置", node.string("type", VariableType.BOOLEAN.name) == VariableType.POSITION.name),
            SettingChoice("type:ENTITY", "エンティティ参照", node.string("type", VariableType.BOOLEAN.name) == VariableType.ENTITY.name),
        )
        GestureSettingScreen.VARIABLE_OPERATION -> {
            val type = runCatching { VariableType.valueOf(node.string("type", VariableType.BOOLEAN.name)) }
                .getOrDefault(VariableType.BOOLEAN)
            CommandSettingsModel.allowedVariableOperations(type).map { operation ->
                SettingChoice("operation:${operation.name}", operationLabel(operation), node.string("operation", operation.name) == operation.name)
            }
        }
        GestureSettingScreen.VARIABLE_VALUE -> buildList {
            add(SettingChoice("value:direct", "直接入力", !node.string("value").startsWith("$")))
            val script = plugin.scripts.load(context.scriptId)
            val insideFor = script != null && node.string("type", VariableType.BOOLEAN.name) == VariableType.INTEGER.name &&
                node.string("scope", VariableScope.TEMPORARY.name) != VariableScope.WORLD.name &&
                GraphEditor.isInsideFor(script.graph, node.id, GraphEditor.Edge.NEXT)
            if (insideFor) {
                add(SettingChoice("value:iteration", "現在の反復値", node.string("value") == "\$current_iteration_value"))
                add(SettingChoice("value:count", "現在のループ回数", node.string("value") == "\$current_loop_count"))
            }
        }
        GestureSettingScreen.FOR_SOURCE -> listOf(
            SettingChoice("source:FIXED", "固定値", node.string(fieldKey, "FIXED") == "FIXED"),
            SettingChoice("source:TEMPORARY", "一時変数", node.string(fieldKey, "FIXED") == "TEMPORARY"),
            SettingChoice("source:WORLD", "ワールド内変数", node.string(fieldKey, "FIXED") == "WORLD"),
        )
        GestureSettingScreen.INCLUSIVE_END -> if (node.type == CommandType.CONDITION && fieldKey == "inverted") {
            listOf(
                SettingChoice("inclusive:true", "反転する", node.boolean(fieldKey, false)),
                SettingChoice("inclusive:false", "反転しない", !node.boolean(fieldKey, false)),
            )
        } else {
            listOf(
                SettingChoice("inclusive:true", "終端を含む", node.boolean(fieldKey, true)),
                SettingChoice("inclusive:false", "終端を含まない", !node.boolean(fieldKey, true)),
            )
        }
        GestureSettingScreen.CONTEXT_OVERRIDE -> listOf(
            SettingChoice("context:executor", "実行者", node.contextOverride?.executor != null),
            SettingChoice("context:target", "対象", node.contextOverride?.target != null),
            SettingChoice("context:position", "位置", node.contextOverride?.position != null),
            SettingChoice("context:facing", "向き", node.contextOverride?.facing != null),
            SettingChoice("context:source", if (CommandSettingsModel.contextSource(node) == ContextSource.PREVIOUS) "直前コンテキスト" else "基底コンテキスト"),
            SettingChoice("context:inherit", "すべて継承", node.contextOverride == null),
        )
    }

    private fun targetChoices(node: CommandNode, context: CommandSettingContext, player: Player): List<SettingChoice> {
        val current = CommandSettingsModel.targetSpec(node, context.role)?.kind
        val choices = listOf(
            TargetKind.INHERITED_TARGET to "継承対象",
            TargetKind.NEAREST_PLAYER to "最寄りのプレイヤー",
            TargetKind.NEARBY_PLAYERS to "周囲のプレイヤー",
            TargetKind.ALL_PLAYERS to "全プレイヤー",
            TargetKind.RANDOM_PLAYER to "ランダムなプレイヤー",
            TargetKind.NEAREST_ENTITY to "最寄りのエンティティ",
            TargetKind.NEARBY_ENTITIES to "周囲のエンティティ",
            TargetKind.FIXED_ENTITY to "固定エンティティ",
        ).map { (kind, label) -> SettingChoice("target:${kind.name}", label, current == kind) }
        // 対象種別にかかわらずオプション入口を常設します。対象を変更した後に
        // 詳細条件の導線だけ消えると、同じ設定を再び開けなくなるためです。
        return choices + SettingChoice("open-filters", "詳細条件を編集")
    }

    private fun targetFilterChoices(node: CommandNode, context: CommandSettingContext, player: Player): List<SettingChoice> {
        val spec = CommandSettingsModel.targetSpec(node, context.role)
            ?: me.awabi2048.kantancommander.model.TargetSpec(TargetKind.NEAREST_ENTITY)
        fun value(parameter: String): String = when (parameter) {
            "sort" -> when (spec.sort) {
                TargetSort.NEAREST -> "最寄り"
                TargetSort.FURTHEST -> "最遠"
                TargetSort.RANDOM -> "ランダム"
            }
            "gameMode" -> spec.gameMode ?: "未設定"
            "entityType" -> spec.entityType ?: "未設定"
            "minimumDistance" -> spec.minimumDistance?.toString() ?: "未設定"
            "maximumDistance" -> spec.maximumDistance?.toString() ?: "未設定"
            "limit" -> spec.limit?.toString() ?: "未設定"
            "tag" -> spec.tag ?: "未設定"
            else -> spec.name ?: "未設定"
        }
        return listOf(
            "entityType" to "エンティティ種別",
            "minimumDistance" to "最小距離",
            "maximumDistance" to "最大距離",
            "limit" to "上限数",
            "sort" to "並び順",
            "gameMode" to "ゲームモード",
            "tag" to "タグ",
            "name" to "名前",
        ).map { (id, label) -> SettingChoice("filter:$id", "$label ${value(id)}") }
    }

    private fun positionChoices(node: CommandNode, context: CommandSettingContext, player: Player): List<SettingChoice> {
        val destination = context.role == CommandSettingRole.DESTINATION
        val current = CommandSettingsModel.positionSpec(node, context.role)?.kind
        val choices = if (destination) {
            listOf(
                PositionKind.COORDINATES to "座標を設定",
                PositionKind.TARGET to "他のエンティティ",
            )
        } else {
            listOf(
                PositionKind.DISK to "ディスク位置",
                PositionKind.EXECUTOR to "実行者の位置",
                PositionKind.TARGET to "対象の位置",
                PositionKind.MYWORLD_SPAWN to "MyWorldスポーン",
                PositionKind.COORDINATES to "座標",
                PositionKind.TEMPORARY_VARIABLE to "一時変数",
                PositionKind.WORLD_VARIABLE to "ワールド内変数",
            )
        }
        return choices.map { (kind, label) -> SettingChoice("position:${kind.name}", label, current == kind) }
    }

    private fun facingChoices(node: CommandNode, player: Player): List<SettingChoice> {
        val current = CommandSettingsModel.facingSpec(node)?.kind
        return listOf(
            FacingKind.INHERITED to "変更しない",
            FacingKind.CAPTURED to "現在の向き",
            FacingKind.EXECUTOR to "実行者の向き",
            FacingKind.TARGET to "対象の向き",
            FacingKind.COORDINATES to "座標を向く",
            FacingKind.MYWORLD_SPAWN to "MyWorldスポーンを向く",
            FacingKind.ROTATION to "数値指定",
        ).map { (kind, label) -> SettingChoice("facing:${kind.name}", label, current == kind) }
    }

    private fun conditionKindChoices(node: CommandNode, player: Player): List<SettingChoice> =
        listOf(
            ConditionKind.TARGET_EXISTS to "対象の存在",
            ConditionKind.ENTITY_STATE to "エンティティ状態",
            ConditionKind.VARIABLE_STATE to "変数状態",
            ConditionKind.BLOCK_STATE to "ブロック状態",
            ConditionKind.ITEM_POSSESSION to "アイテム所持",
        ).map { (kind, label) -> SettingChoice("condition-kind:${kind.name}", label, node.string("kind") == kind.name) }

    private fun conditionDetailChoices(node: CommandNode, player: Player): List<SettingChoice> {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrDefault(ConditionKind.TARGET_EXISTS)
        return when (kind) {
            ConditionKind.TARGET_EXISTS -> listOf(SettingChoice("condition-target", "対象", false))
            ConditionKind.ENTITY_STATE -> listOf(
                SettingChoice("condition-target", "対象", false),
                SettingChoice("condition-state", "状態 ${if (node.string("state", "sneaking") == "sneaking") "スニーク中" else "地上"}"),
            )
            ConditionKind.VARIABLE_STATE -> listOf(
                SettingChoice("condition-variable", "変数 ${node.string("variable").ifBlank { "未設定" }}"),
                SettingChoice("condition-scope", "範囲 ${if (node.string("variableScope", VariableScope.TEMPORARY.name) == VariableScope.WORLD.name) "ワールド" else "一時"}"),
                SettingChoice("condition-operator", "演算子 ${node.string("operator", "==")}"),
                SettingChoice("condition-value", "値 ${node.string("value", "0")}"),
            )
            ConditionKind.BLOCK_STATE -> listOf(
                SettingChoice("condition-position", "位置", false),
                SettingChoice("condition-block", "ブロック ${node.string("block", "minecraft:air")}"),
            )
            ConditionKind.ITEM_POSSESSION -> listOf(
                SettingChoice("condition-target", "対象", false),
                SettingChoice("condition-item", "アイテム ${node.string("item").ifBlank { "未設定" }}"),
                SettingChoice("condition-count", "個数 ${node.string("count", "1")}"),
            )
        }
    }

    private fun operationLabel(operation: VariableOperation): String = when (operation) {
        VariableOperation.SET -> "設定"
        VariableOperation.ADD -> "加算"
        VariableOperation.SUBTRACT -> "減算"
        VariableOperation.TOGGLE -> "反転"
        VariableOperation.STORE_POSITION -> "位置を保存"
        VariableOperation.STORE_TARGET -> "対象を保存"
        VariableOperation.CLEAR -> "消去"
    }

    private fun settingCurrentValue(
        node: CommandNode,
        context: CommandSettingContext,
        screen: GestureSettingScreen,
        fieldKey: String,
        player: Player,
    ): String = when (screen) {
        GestureSettingScreen.TARGET -> CommandSettingsModel.targetSpec(node, context.role)?.kind?.name ?: "未設定"
        GestureSettingScreen.POSITION -> CommandSettingsModel.positionSpec(node, context.role)?.kind?.name ?: "未設定"
        GestureSettingScreen.FACING -> CommandSettingsModel.facingSpec(node)?.kind?.name ?: "未設定"
        else -> node.string(fieldKey).ifBlank { "未設定" }
    }

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
        addText(visuals, "lower-close", -0.7975, closeCy, 0.006, 90, Component.text("閉じる"))
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
                hoverText = twoLineHover(
                    top = KcI18n.text(player, type.key),
                    bottom = KcI18n.list(player, type.descriptionKey)
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
            Component.text(if (overwrite) "設定中のアイテムを上書きしますか？" else "このノードを削除しますか？"),
        )
        addText(
            visuals,
            "confirm-warn",
            0.0,
            0.05,
            0.004,
            180,
            Component.text(if (overwrite) "現在のアイテム情報は置き換えられます" else "元に戻せません"),
        )

        addBlock(visuals, "confirm-yes-bg", -0.27, -0.08, 0.48, 0.12, Material.RED_CONCRETE, 4)
        addText(visuals, "confirm-yes", -0.27, -0.08, 0.004, 100, Component.text(if (overwrite) "上書きする" else "削除する"))
        elements.add(GestureGuiElement(
            elementId = "confirm-delete",
            bounds = rect(-0.27, -0.08, 0.48, 0.12),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            targetVisualId = "confirm-yes-bg",
        ))
        addBlock(visuals, "confirm-no-bg", 0.27, -0.08, 0.48, 0.12, Material.CYAN_TERRACOTTA, 4)
        addText(visuals, "confirm-no", 0.27, -0.08, 0.004, 100, Component.text("キャンセル"))
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
        listOf(page - 1 to "◀", page + 1 to "▶").forEachIndexed { index, (targetPage, glyph) ->
            if (targetPage !in 0 until pageCount) return@forEachIndexed
            val x = centerX + if (index == 0) -0.12 else 0.12
            val visualId = "$id-page-$targetPage-bg"
            addBlock(visuals, visualId, x, centerY, 0.18, 0.10, Material.CYAN_CONCRETE, 4)
            addText(visuals, "$id-page-$targetPage-label", x, centerY, 0.005, 60, Component.text(glyph))
            elements.add(GestureGuiElement(
                elementId = "lower-$id-page:$targetPage",
                bounds = rect(x, centerY, 0.18, 0.10),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = visualId,
            ))
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
        /** 構造化モデルを壊さず、paramsへ文字列として保存できる項目だけを許可します。 */
        val DIALOG_EDITABLE_KEYS = setOf(
            "item", "count", "text", "stay", "ticks", "tags", "sound", "volume", "pitch",
            "effect", "level", "seconds", "intensity", "shakeType", "slot", "entity", "diskId", "name", "startValue",
            "endValue", "stepValue", "condition", "variable", "value",
        )
    }
}
