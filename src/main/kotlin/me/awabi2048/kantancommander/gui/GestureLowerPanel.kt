package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccess
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenDefinition
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiView
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * ジェスチャーエディターの下部パネル（左右 1:3 分割型）のビュー生成を担います。
 *
 * - SETTINGS: 左タブ列＝設定フィールド、右詳細＝現在値＋候補
 * - PICKER: 左タブ列＝コマンドカテゴリ（PROCESS/CONTROL）、右詳細＝種別一覧
 * - CONFIRM: 上部エディターが子画面（openChild・赤ガラス）として開く
 *
 * 座標は画面中央原点・ブロック単位。左列: x=-0.7975 / 右ペイン: x∈[-0.50, 1.03]。
 */
class GestureLowerPanel(private val plugin: KantanCommanderPlugin) {
    val LOWER_SCREEN_ID = "gesture-editor-lower"

    fun build(state: GestureEditorState, player: Player): GestureGuiView {
        return when (state.lowerMode) {
            GestureLowerMode.SETTINGS -> buildSettings(state, player)
            GestureLowerMode.PICKER -> buildPicker(state, player)
            GestureLowerMode.CONFIRM -> buildConfirm(state, player)
        }
    }

    /** SETTINGS: 左タブ列＝フィールド（最大5件＋ページャー）、右詳細＝現在値＋候補 */
    private fun buildSettings(state: GestureEditorState, player: Player): GestureGuiView {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        val script = plugin.scripts.load(state.scriptId)
        val node = state.selectedNodeId?.let { id -> script?.graph?.nodes?.get(id) }
        if (node == null) {
            addText(visuals, "lower-hint", 0.28, 0.20, 0.010, 160, Component.text("ノードを選択してください"))
            return view(GestureLowerMode.SETTINGS, elements, visuals)
        }

        val fields = EditorMenuLayout.fields(node.type).let { filterFields(it, node) }
        if (fields.isEmpty()) {
            addText(visuals, "lower-hint", 0.28, 0.20, 0.010, 160, Component.text("設定項目はありません"))
            return view(GestureLowerMode.SETTINGS, elements, visuals)
        }
        val tabs = fields.take(5)
        val selected = state.settingsTab.coerceIn(0, tabs.lastIndex)

        tabs.forEachIndexed { index, field ->
            val cy = 0.38 - index * 0.17
            val on = index == selected
            addBlock(visuals, "tab-bg-$index", -0.7975, cy, 0.47, 0.15,
                if (on) Material.CYAN_CONCRETE else Material.GRAY_CONCRETE, 4)
            addText(visuals, "tab-$index", -0.7975, cy - 0.02, 0.0055, 90,
                Component.text(KcI18n.text(player, field.label)))
            elements.add(GestureGuiElement(
                elementId = "lower-tab:$index",
                bounds = rect(-0.7975, cy, 0.47, 0.15),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            ))
        }
        if (fields.size > 5) {
            addText(visuals, "lower-more", -0.7975, 0.38 - tabs.size * 0.17, 0.006, 90, Component.text("▼"))
        }

        val field = tabs[selected]
        val value = field.value(node).render(player)
        addText(visuals, "lower-header", 0.28, 0.43, 0.007, 200,
            Component.text(KcI18n.text(player, field.label) + "：" + value))
        addText(visuals, "lower-current", 0.28, 0.36, 0.005, 200, Component.text("[現在値]"))
        addText(visuals, "lower-hint", 0.28, 0.29, 0.005, 200, Component.text("左クリックで設定を変更"))
        // 値編集ボタン: チャット入力で値を確定する（ジェスチャーGUIは閉じない）
        addBlock(visuals, "lower-edit-bg", 0.28, 0.02, 1.2, 0.26, Material.STONE_BUTTON, 4)
        addText(visuals, "lower-edit", 0.28, -0.02, 0.006, 160, Component.text("編集（チャット入力）"))
        elements.add(GestureGuiElement(
            elementId = "lower-edit:${field.key}",
            bounds = rect(0.28, 0.02, 1.2, 0.26),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
        ))

        return view(GestureLowerMode.SETTINGS, elements, visuals)
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
                if (on) Material.CYAN_CONCRETE else Material.GRAY_CONCRETE, 4)
            addText(visuals, "cat-$index", -0.7975, cy - 0.02, 0.0055, 90,
                Component.text(KcI18n.text(player, category.labelKey)))
            elements.add(GestureGuiElement(
                elementId = "lower-cat:$index",
                bounds = rect(-0.7975, cy, 0.47, 0.15),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            ))
        }
        val closeCy = 0.38 - categories.size * 0.17
        addText(visuals, "lower-close", -0.7975, closeCy - 0.02, 0.006, 90, Component.text("＋ 閉じる"))
        elements.add(GestureGuiElement(
            elementId = "lower-close-picker",
            bounds = rect(-0.7975, closeCy, 0.47, 0.15),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
        ))

        val category = categories[state.pickerCategory.coerceIn(0, categories.lastIndex)]
        // MERGE/FOR_ENDは単独挿入不可、FOR_START以外の制御系はこの経路へ挿入できないため除外する
        val types = CommandType.entries.filter { type ->
            CommandPresentationPolicy.category(type) == category &&
                type != CommandType.MERGE &&
                type != CommandType.FOR_END &&
                type != CommandType.FOR_START &&
                type != CommandType.BREAK &&
                type != CommandType.CONTINUE
        }
        types.take(8).forEachIndexed { index, type ->
            val cx = if (index % 2 == 0) -0.11 else 0.65
            val cy = 0.20 - (index / 2) * 0.18
            addBlock(visuals, "type-bg-$index", cx, cy, 0.72, 0.155, Material.STONE, 4)
            addText(visuals, "type-$index", cx, cy - 0.02, 0.0055, 90,
                Component.text(KcI18n.text(player, type.key)))
            elements.add(GestureGuiElement(
                elementId = "lower-type:${type.name}",
                bounds = rect(cx, cy, 0.72, 0.155),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            ))
        }
        return view(GestureLowerMode.PICKER, elements, visuals)
    }

    /** CONFIRM: 子画面（上部エディターがopenChildで赤ガラスを重ねる） */
    private fun buildConfirm(state: GestureEditorState, player: Player): GestureGuiView {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        addText(visuals, "confirm-title", 0.0, 0.18, 0.007, 200, Component.text("このノードを削除しますか？"))
        addText(visuals, "confirm-warn", 0.0, 0.10, 0.005, 200, Component.text("元に戻せません"))

        addBlock(visuals, "confirm-yes-bg", -0.55, -0.10, 1.0, 0.22, Material.RED_CONCRETE, 4)
        addText(visuals, "confirm-yes", -0.55, -0.12, 0.006, 160, Component.text("削除する"))
        elements.add(GestureGuiElement(
            elementId = "confirm-delete",
            bounds = rect(-0.55, -0.10, 1.0, 0.22),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
        ))
        addBlock(visuals, "confirm-no-bg", 0.55, -0.10, 1.0, 0.22, Material.GRAY_CONCRETE, 4)
        addText(visuals, "confirm-no", 0.55, -0.12, 0.006, 160, Component.text("キャンセル"))
        elements.add(GestureGuiElement(
            elementId = "confirm-cancel",
            bounds = rect(0.55, -0.10, 1.0, 0.22),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
        ))
        return view(GestureLowerMode.CONFIRM, elements, visuals)
    }

    private fun filterFields(fields: List<EditorField>, node: CommandNode): List<EditorField> {
        if (node.type == CommandType.ENTITY_ACTION && node.string("action") != "ride") {
            return fields.filterNot { it.key == "other" }
        }
        if (node.type == CommandType.DISPLAY_TEXT && node.string("mode") != "title") {
            return fields.filterNot { it.key == "stay" }
        }
        if (node.type != CommandType.VARIABLE) return fields
        val operation = runCatching {
            VariableOperation.valueOf(node.string("operation"))
        }.getOrDefault(VariableOperation.SET)
        return fields.filterNot { field ->
            field.key == "value" &&
                operation !in setOf(VariableOperation.SET, VariableOperation.ADD, VariableOperation.SUBTRACT)
        }
    }

    private fun view(
        mode: GestureLowerMode,
        elements: List<GestureGuiElement>,
        visuals: List<GestureGuiVisual>,
    ): GestureGuiView = GestureGuiView(
        GestureGuiScreenDefinition(
            LOWER_SCREEN_ID,
            elements,
            access = GestureGuiAccess.OWNER_ONLY,
        ),
        visuals,
        onAction = { context -> onAction(context, mode) },
    )

    private fun onAction(context: GestureGuiActionContext, mode: GestureLowerMode) {
        // 操作ハンドリングはGestureSequenceEditorが担う。ここではビュー生成のみ。
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
}