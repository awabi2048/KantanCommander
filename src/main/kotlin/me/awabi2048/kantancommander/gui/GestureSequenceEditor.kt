package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccess
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiChildOptions
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOpenOptions
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenDefinition
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiView
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskPlacement
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

/**
 * ジェスチャーエディターの状態。プレイヤー単位で保持され、操作のたびに更新されます。
 */
data class GestureEditorState(
    var scriptId: UUID,
    var placement: DiskPlacement?,
    var origin: MapPoint = MapPoint(0, 0),
    var selectedNodeId: UUID? = null,
    var anchor: Location? = null,
    /** 下部パネルの表示モード */
    var lowerMode: GestureLowerMode = GestureLowerMode.SETTINGS,
    /** SETTINGSで選択中のフィールドインデックス */
    var settingsTab: Int = 0,
    /** PICKERで選択中のカテゴリインデックス */
    var pickerCategory: Int = 0,
    /** CONFIRM対象のノードID（削除確認） */
    var confirmNodeId: UUID? = null,
    /** PICKERで選択中の挿入先（addポイントクリック時に保持） */
    var pendingInsertion: InsertionTarget? = null,
)

/** 下部パネルの表示モード。CONFIRMのみ子画面（赤ガラス）として開きます。 */
enum class GestureLowerMode {
    SETTINGS,
    PICKER,
    CONFIRM,
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * ジェスチャーエディターの主要コントローラー。
 * 上部ビューポートと下部パネルをジェスチャーGUIセッションとして管理します。
 * 下部のモード切替はupdateScreen、CONFIRMのみopenChild（赤ガラス）で実現します。
 */
class GestureSequenceEditor(
    private val plugin: KantanCommanderPlugin,
    private val state: GestureEditorState,
) {
    private val api get() = CCSystem.getAPI().getGestureGuiService()
    // 下部画面のクリックは上部と同じハンドラで処理します（タブ切替・PICKER・CONFIRMの共通ロジック）。
    private val lowerPanel = GestureLowerPanel(plugin, onAction = { ctx -> handleUpperAction(ctx) })

    private val UPPER_SCREEN_ID = "gesture-editor-upper"

    fun open(player: Player) {
        api.registerOwner(player.uniqueId)
        val upper = buildUpperViewport(player)
        val lower = lowerPanel.build(state, player)
        api.open(player, listOf(upper, lower), GestureGuiOpenOptions(anchor = state.anchor))
    }

    fun updateUpper(player: Player) {
        api.updateScreen(player.uniqueId, buildUpperViewport(player))
    }

    fun updateLower(player: Player) {
        api.updateScreen(player.uniqueId, lowerPanel.build(state, player))
    }

    fun openConfirmChild(player: Player) {
        state.lowerMode = GestureLowerMode.CONFIRM
        val view = lowerPanel.build(state, player)
        api.openChild(
            player.uniqueId,
            view,
            GestureGuiChildOptions(
                parentScreenId = lowerPanel.LOWER_SCREEN_ID,
                overlayMaterial = Material.RED_STAINED_GLASS,
            ),
        )
    }

    private fun buildUpperViewport(player: Player): GestureGuiView {
        val script = plugin.scripts.load(state.scriptId) ?: return emptyView()
        val layout = GraphLayoutEngine.layout(script.graph)
        val cells = layout.viewport(state.origin, GestureEditorLayout.VIEWPORT_COLS, GestureEditorLayout.VIEWPORT_ROWS)
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()

        cells.forEach { (localPoint, cell) ->
            val gx = state.origin.x + localPoint.x
            val gy = state.origin.y + localPoint.y
            val cx = GestureEditorLayout.cellCenterX(gx)
            val cy = GestureEditorLayout.cellCenterY(gy)
            when (cell.kind) {
                MapCellKind.NODE -> {
                    val node = cell.nodeId?.let { script.graph.nodes[it] }
                    if (node != null) {
                        val isSelected = state.selectedNodeId == node.id
                        val glowColor = if (isSelected) Color.YELLOW.asRGB() else null
                        visuals.add(GestureGuiVisual.Item(
                            visualId = "node-icon-${node.id}",
                            x = cx, y = cy,
                            item = org.bukkit.inventory.ItemStack(node.type.icon),
                            scale = 0.13,
                            layer = if (isSelected) 5 else 3,
                            glowColor = glowColor,
                        ))
                        elements.add(GestureGuiElement(
                            elementId = "node:${node.id}",
                            bounds = iconBounds(cx, cy),
                            acceptedGestures = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SECONDARY),
                            targetVisualId = "node-icon-${node.id}",
                        ))
                    }
                }
                MapCellKind.ADD -> {
                    visuals.add(GestureGuiVisual.Block(
                        visualId = "add-block-$gx-$gy",
                        x = cx, y = cy,
                        width = GestureEditorLayout.ICON_W,
                        height = GestureEditorLayout.ICON_H,
                        blockData = Bukkit.createBlockData(Material.YELLOW_CONCRETE),
                        layer = 2,
                    ))
                    visuals.add(GestureGuiVisual.Text(
                        visualId = "add-plus-$gx-$gy",
                        x = cx, y = cy - 0.02,
                        text = net.kyori.adventure.text.Component.text("+"),
                        size = 0.012,
                        layer = 4,
                    ))
                    elements.add(GestureGuiElement(
                        elementId = "add:$gx:$gy",
                        bounds = iconBounds(cx, cy),
                        acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                        targetVisualId = "add-plus-$gx-$gy",
                    ))
                }
                MapCellKind.PATH, MapCellKind.BRANCH_PATH, MapCellKind.LOOP_RETURN_PATH -> {
                    // 経路は後で一括生成
                }
            }
        }

        buildPathSegments(cells, state.origin).forEach { seg ->
            visuals.add(GestureGuiVisual.Block(
                visualId = "path-${seg.x}-${seg.y}-${seg.w}-${seg.h}",
                x = seg.x, y = seg.y,
                width = seg.w, height = seg.h,
                blockData = Bukkit.createBlockData(Material.WHITE_STAINED_GLASS),
                layer = 1,
            ))
        }

        addNavigation(visuals, elements)

        // back-to-start（十字の下・左に隣接）
        visuals.add(GestureGuiVisual.Block(
            visualId = "back-block",
            x = GestureEditorLayout.BACK_X,
            y = GestureEditorLayout.BACK_Y,
            width = GestureEditorLayout.NAV_SIZE,
            height = GestureEditorLayout.NAV_SIZE,
            blockData = Bukkit.createBlockData(Material.BROWN_CONCRETE),
            layer = 4,
        ))
        visuals.add(GestureGuiVisual.Text(
            visualId = "back-label",
            x = GestureEditorLayout.BACK_X,
            y = GestureEditorLayout.BACK_Y - 0.02,
            text = net.kyori.adventure.text.Component.text("⌂"),
            size = 0.010,
            layer = 6,
        ))
        elements.add(GestureGuiElement(
            elementId = "back-to-start",
            bounds = navBounds(GestureEditorLayout.BACK_X, GestureEditorLayout.BACK_Y, GestureEditorLayout.NAV_SIZE),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            targetVisualId = "back-label",
        ))

        return GestureGuiView(
            GestureGuiScreenDefinition(UPPER_SCREEN_ID, elements, access = GestureGuiAccess.OWNER_ONLY),
            visuals,
        ) { context -> handleUpperAction(context) }
    }

    private fun handleUpperAction(context: GestureGuiActionContext) {
        val player = Bukkit.getPlayer(context.ownerId) ?: return
        when {
            context.elementId.startsWith("node:") -> {
                val nodeId = runCatching { UUID.fromString(context.elementId.removePrefix("node:")) }.getOrNull() ?: return
                when (context.gesture) {
                    GestureGuiGesture.PRIMARY -> {
                        state.selectedNodeId = nodeId
                        state.lowerMode = GestureLowerMode.SETTINGS
                        state.settingsTab = 0
                        updateUpper(player)
                        updateLower(player)
                    }
                    GestureGuiGesture.SECONDARY -> {
                        state.confirmNodeId = nodeId
                        openConfirmChild(player)
                    }
                    else -> Unit
                }
            }
            context.elementId.startsWith("nav-") && context.gesture == GestureGuiGesture.PRIMARY -> {
                val delta = when (context.elementId) {
                    "nav-up" -> MapPoint(0, -1)
                    "nav-down" -> MapPoint(0, 1)
                    "nav-left" -> MapPoint(-1, 0)
                    "nav-right" -> MapPoint(1, 0)
                    else -> return
                }
                val script = plugin.scripts.load(state.scriptId) ?: return
                state.origin = GestureEditorLayout.clampOrigin(
                    MapPoint(state.origin.x + delta.x, state.origin.y + delta.y),
                    GraphLayoutEngine.layout(script.graph),
                )
                updateUpper(player)
            }
            context.elementId == "back-to-start" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.selectedNodeId = null
                state.origin = MapPoint(0, 0)
                updateUpper(player)
                updateLower(player)
            }
            context.elementId.startsWith("add:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                // addポイントの挿入先情報を保持し、下部をPICKERへ切り替える
                val script = plugin.scripts.load(state.scriptId) ?: return
                val gx = context.elementId.removePrefix("add:").substringBefore(":").toIntOrNull() ?: return
                val gy = context.elementId.removePrefix("add:").substringAfter(":").toIntOrNull() ?: return
                val layout = GraphLayoutEngine.layout(script.graph)
                val cell = layout.cells[MapPoint(gx, gy)]
                val target = cell?.insertionTarget ?: run {
                    // セルが持たない場合は前後ノードから直接挿入先を導出する（末端追加）
                    InsertionTarget(
                        sourceId = null,
                        edge = GraphEditor.Edge.ENTRY,
                    )
                }
                state.pendingInsertion = target
                state.lowerMode = GestureLowerMode.PICKER
                state.pickerCategory = 0
                updateLower(player)
            }
            context.elementId.startsWith("lower-tab:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.settingsTab = context.elementId.removePrefix("lower-tab:").toIntOrNull() ?: return
                updateLower(player)
            }
            context.elementId.startsWith("lower-edit:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                val fieldKey = context.elementId.removePrefix("lower-edit:")
                val script = plugin.scripts.load(state.scriptId)
                val node = state.selectedNodeId?.let { id -> script?.graph?.nodes?.get(id) } ?: return
                // チャット入力でフィールド値を設定する（ジェスチャーGUIは閉じない）
                plugin.gestureChatInput.begin(player, "チャットで値を入力してください（「キャンセル」で中止）") { value ->
                    node.params[fieldKey] = value
                    if (script != null) plugin.scripts.save(script)
                    updateLower(player)
                }
            }
            context.elementId.startsWith("lower-cat:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.pickerCategory = context.elementId.removePrefix("lower-cat:").toIntOrNull() ?: return
                updateLower(player)
            }
            context.elementId.startsWith("lower-type:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                val typeName = context.elementId.removePrefix("lower-type:")
                val type = runCatching { CommandType.valueOf(typeName) }.getOrNull() ?: return
                val script = plugin.scripts.load(state.scriptId) ?: return
                val target = state.pendingInsertion
                    ?: InsertionTarget(null, GraphEditor.Edge.ENTRY)
                if (type in setOf(CommandType.MERGE, CommandType.FOR_END)) {
                    // 単独挿入不可の型はPICKER側で候補から除外済み。ここでは黙って戻る
                    state.lowerMode = GestureLowerMode.SETTINGS
                    updateLower(player)
                    return
                }
                val inserted = GraphEditor.insert(script.graph, target.sourceId, target.edge, type)
                plugin.scripts.save(script)
                state.pendingInsertion = null
                state.selectedNodeId = inserted.id
                state.lowerMode = GestureLowerMode.SETTINGS
                state.settingsTab = 0
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "lower-close-picker" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.lowerMode = GestureLowerMode.SETTINGS
                updateLower(player)
            }
            context.elementId == "confirm-delete" && context.gesture == GestureGuiGesture.PRIMARY -> {
                val nodeId = state.confirmNodeId ?: return
                val script = plugin.scripts.load(state.scriptId) ?: return
                if (GraphEditor.delete(script.graph, nodeId)) {
                    plugin.scripts.save(script)
                }
                state.confirmNodeId = null
                state.selectedNodeId = null
                state.lowerMode = GestureLowerMode.SETTINGS
                api.closeChild(player.uniqueId, lowerPanel.LOWER_SCREEN_ID)
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "confirm-cancel" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.confirmNodeId = null
                state.lowerMode = GestureLowerMode.SETTINGS
                api.closeChild(player.uniqueId, lowerPanel.LOWER_SCREEN_ID)
                updateLower(player)
            }
        }
    }

    private fun emptyView(): GestureGuiView {
        return GestureGuiView(
            GestureGuiScreenDefinition(UPPER_SCREEN_ID, emptyList(), access = GestureGuiAccess.OWNER_ONLY),
            emptyList(),
        ) {}
    }

    private fun iconBounds(cx: Double, cy: Double): GestureGuiBounds {
        val h = GestureEditorLayout.ICON_W / 2.0
        return GestureGuiBounds(cx - h, cy - h, cx + h, cy + h)
    }

    private fun navBounds(cx: Double, cy: Double, size: Double): GestureGuiBounds {
        val h = size / 2.0
        return GestureGuiBounds(cx - h, cy - h, cx + h, cy + h)
    }

    /** 十字ナビゲーション（75%サイズ・右下） */
    private fun addNavigation(visuals: MutableList<GestureGuiVisual>, elements: MutableList<GestureGuiElement>) {
        val s = GestureEditorLayout.NAV_SIZE
        val p = GestureEditorLayout.NAV_PITCH
        val cx = GestureEditorLayout.NAV_CENTER_X
        val cy = GestureEditorLayout.NAV_CENTER_Y
        listOf(
            Quad("nav-up", 0, 1, "▲"),
            Quad("nav-down", 0, -1, "▼"),
            Quad("nav-left", -1, 0, "◀"),
            Quad("nav-right", 1, 0, "▶"),
        ).forEach { quad ->
            val nx = cx + quad.second * p
            val ny = cy + quad.third * p
            visuals.add(GestureGuiVisual.Block(
                visualId = "${quad.first}-block",
                x = nx, y = ny,
                width = s, height = s,
                blockData = Bukkit.createBlockData(Material.CYAN_CONCRETE),
                layer = 4,
            ))
            visuals.add(GestureGuiVisual.Text(
                visualId = "${quad.first}-glyph",
                x = nx, y = ny - 0.01,
                text = net.kyori.adventure.text.Component.text(quad.fourth),
                size = 0.011,
                layer = 6,
            ))
            elements.add(GestureGuiElement(
                elementId = quad.first,
                bounds = navBounds(nx, ny, s),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "${quad.first}-glyph",
            ))
        }
    }

    /**
     * ビューポート内のセルから経路セグメントを生成します。
     * 経路セルは左右・上下隣の接続可能セル（経路/ノード/追加ポイント）との間に帯を張ります。
     */
    private fun buildPathSegments(viewportCells: Map<MapPoint, MapCell>, origin: MapPoint): List<GestureEditorLayout.PathSegment> {
        val segments = mutableListOf<GestureEditorLayout.PathSegment>()
        viewportCells.forEach { (localPoint, cell) ->
            if (cell.kind != MapCellKind.PATH && cell.kind != MapCellKind.BRANCH_PATH) return@forEach
            val gx = origin.x + localPoint.x
            val gy = origin.y + localPoint.y
            val cx = GestureEditorLayout.cellCenterX(gx)
            val cy = GestureEditorLayout.cellCenterY(gy)
            val right = viewportCells[MapPoint(localPoint.x + 1, localPoint.y)]
            val down = viewportCells[MapPoint(localPoint.x, localPoint.y + 1)]
            val connectsRight = right != null && right.kind in CONNECTABLE_KINDS
            val connectsDown = down != null && down.kind in CONNECTABLE_KINDS
            if (connectsRight) segments.add(GestureEditorLayout.horizontalPath(cy, cx, GestureEditorLayout.cellCenterX(gx + 1)))
            if (connectsDown) segments.add(GestureEditorLayout.verticalPath(cx, cy, GestureEditorLayout.cellCenterY(gy + 1)))
        }
        return segments
    }

    private companion object {
        val CONNECTABLE_KINDS = setOf(MapCellKind.PATH, MapCellKind.BRANCH_PATH, MapCellKind.NODE, MapCellKind.ADD)
    }
}