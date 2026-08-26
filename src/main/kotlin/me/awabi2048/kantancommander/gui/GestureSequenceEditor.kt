package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccess
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiChildOptions
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOpenOptions
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenDefinition
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiView
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.util.KcI18n
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
    /** ビューポート表示倍率。0=75%、25%刻みで25%〜150%を許可します。 */
    var zoomLevel: Int = 0,
    var selectedNodeId: UUID? = null,
    var anchor: Location? = null,
    /** 下部パネルの表示モード */
    var lowerMode: GestureLowerMode = GestureLowerMode.SETTINGS,
    /** SETTINGSで選択中のフィールドインデックス */
    var settingsTab: Int = 0,
    var settingsPage: Int = 0,
    /** PICKERで選択中のカテゴリインデックス */
    var pickerCategory: Int = 0,
    var pickerPage: Int = 0,
    /** CONFIRM対象のノードID（削除確認） */
    var confirmNodeId: UUID? = null,
    /** PICKERで選択中の挿入先（addポイントクリック時に保持） */
    var pendingInsertion: InsertionTarget? = null,
    /** PICKERへ遷移した追加ポイントの選択状態。既存ノード選択とは独立して表示します。 */
    var selectedAddPoint: MapPoint? = null,
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

    internal fun isEditing(placement: DiskPlacement): Boolean = state.placement?.key == placement.key

    internal fun closeImmediately(ownerId: UUID) {
        api.close(ownerId, com.awabi2048.ccsystem.api.gesturegui.GestureGuiCloseMode.IMMEDIATE)
    }

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
                animated = false,
            ),
        )
    }

    private fun buildUpperViewport(player: Player): GestureGuiView {
        val script = plugin.scripts.load(state.scriptId) ?: return emptyView()
        val layout = GraphLayoutEngine.layout(script.graph)
        val cells = layout.viewport(state.origin, GestureEditorLayout.VIEWPORT_COLS, GestureEditorLayout.VIEWPORT_ROWS)
        val zoomScale = (0.75 + state.zoomLevel.coerceIn(-2, 3) * 0.25).coerceIn(0.25, 1.5)
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        // 画面内の余白クリックをActionへ届け、選択状態を解除できるようにします。
        elements.add(GestureGuiElement(
            elementId = "viewport-empty",
            bounds = GestureGuiBounds(
                -GestureEditorLayout.UPPER_W / 2.0 + 0.045,
                -GestureEditorLayout.UPPER_H / 2.0 + 0.045,
                GestureEditorLayout.UPPER_W / 2.0 - 0.045,
                GestureEditorLayout.UPPER_H / 2.0 - 0.045,
            ),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
        ))

        cells.forEach { (localPoint, cell) ->
            // セル検索はグリッド座標、配置は列/行インデックスで行う（origin移動してもグリッド位置は固定）
            val colIndex = localPoint.x
            val rowIndex = localPoint.y
            val gx = state.origin.x + colIndex
            val gy = state.origin.y + rowIndex
            val cx = GestureEditorLayout.cellCenterX(colIndex)
            val cy = GestureEditorLayout.cellCenterY(rowIndex)
            when (cell.kind) {
                MapCellKind.NODE -> {
                    val node = cell.nodeId?.let { script.graph.nodes[it] }
                    if (node != null) {
                        val isSelected = state.selectedNodeId == node.id
                        val glowColor = if (isSelected) Color.YELLOW.asARGB() else null
                        // アイコン単体を浮かせず、追加ポイントと同じマス背景で視認性と接続先を示します。
                        visuals.add(GestureGuiVisual.Block(
                            visualId = "node-bg-${node.id}",
                            x = cx, y = cy,
                            width = GestureEditorLayout.ICON_W,
                            height = GestureEditorLayout.ICON_H,
                            blockData = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                            // 背景は常にアイコンの背面。選択時もlayerを変えず素材色/glowだけを変えます。
                            layer = 2,
                            glowColor = glowColor,
                        ))
                        // マスの90% (ICON_W=0.171) に合わせる。Item.scale 0.22が標準のため 0.22*0.78≈0.17 とする
                        visuals.add(GestureGuiVisual.Item(
                            visualId = "node-icon-${node.id}",
                            x = cx, y = cy,
                            item = org.bukkit.inventory.ItemStack(node.type.icon),
                            scale = GestureEditorLayout.ICON_SCALE,
                            // 選択表現は背面セルだけに付け、アイコン自体は常に同じ前景レイヤーへ置きます。
                            layer = 3,
                            glowColor = null,
                        ))
                        elements.add(GestureGuiElement(
                            elementId = "node:${node.id}",
                            bounds = iconBounds(cx, cy),
                            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                            targetVisualId = "node-icon-${node.id}",
                            hoverText = GestureGuiHoverText(
                                text = net.kyori.adventure.text.Component.text(KcI18n.text(player, node.type.key)),
                                x = cx,
                                y = cy + GestureEditorLayout.ICON_H * 0.9,
                                size = 0.006,
                                lineWidth = 180,
                            ),
                        ))
                    }
                }
                MapCellKind.ADD -> {
                    val isSelected = state.selectedAddPoint == MapPoint(gx, gy)
                    visuals.add(GestureGuiVisual.Block(
                        visualId = "add-block-$gx-$gy",
                        x = cx, y = cy,
                        width = GestureEditorLayout.ICON_W,
                        height = GestureEditorLayout.ICON_H,
                        blockData = Bukkit.createBlockData(Material.YELLOW_CONCRETE),
                        layer = 2,
                        glowColor = if (isSelected) Color.YELLOW.asARGB() else null,
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
                        hoverText = GestureGuiHoverText(
                            text = net.kyori.adventure.text.Component.text("クリックで追加"),
                            x = cx,
                            y = cy + GestureEditorLayout.ICON_H * 0.9,
                            size = 0.006,
                            lineWidth = 120,
                        ),
                    ))
                }
                MapCellKind.PATH, MapCellKind.BRANCH_PATH, MapCellKind.LOOP_RETURN_PATH -> {
                    // 追加ポイント直前の経路は「クリックで挿入」を表示しません。
                    val hasAddNeighbor = listOf(
                        MapPoint(localPoint.x - 1, localPoint.y), MapPoint(localPoint.x + 1, localPoint.y),
                        MapPoint(localPoint.x, localPoint.y - 1), MapPoint(localPoint.x, localPoint.y + 1),
                    ).any { cells[it]?.kind == MapCellKind.ADD }
                    if (!hasAddNeighbor && cell.insertionTarget != null) {
                        elements.add(GestureGuiElement(
                            elementId = "path:${gx}:${gy}",
                            bounds = rect(cx, cy, GestureEditorLayout.PITCH_X, GestureEditorLayout.PITCH_Y),
                            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                            hoverText = GestureGuiHoverText(
                                text = net.kyori.adventure.text.Component.text("クリックで挿入"),
                                x = cx,
                                y = cy + GestureEditorLayout.PATH_THICKNESS,
                                size = 0.0055,
                                lineWidth = 120,
                            ),
                        ))
                    }
                }
            }
        }

        val expandedPathCells = layout.cells.mapNotNull { (global, cell) ->
            val local = MapPoint(global.x - state.origin.x, global.y - state.origin.y)
            if (local.x in -1..GestureEditorLayout.VIEWPORT_COLS && local.y in -1..GestureEditorLayout.VIEWPORT_ROWS) {
                local to cell.copy(point = local)
            } else null
        }.toMap()
        buildPathSegments(expandedPathCells, state.origin, layout.cells).forEach { seg ->
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

        addZoomControls(visuals, elements)

        // ズームはビューポート内容とその当たり判定だけを同じ倍率で変換します。
        val scaledVisuals = visuals.map { visual ->
            if (visual.visualId.startsWith("node-") || visual.visualId.startsWith("add-") || visual.visualId.startsWith("path-")) {
                when (visual) {
                    is GestureGuiVisual.Block -> visual.copy(x = visual.x * zoomScale, y = visual.y * zoomScale,
                        width = visual.width * zoomScale, height = visual.height * zoomScale)
                    is GestureGuiVisual.Item -> visual.copy(x = visual.x * zoomScale, y = visual.y * zoomScale,
                        scale = visual.scale * zoomScale)
                    is GestureGuiVisual.Text -> visual.copy(x = visual.x * zoomScale, y = visual.y * zoomScale,
                        size = visual.size * zoomScale)
                }
            } else visual
        }
        val scaledElements = elements.map { element ->
            if (element.elementId.startsWith("node:") || element.elementId.startsWith("add:") || element.elementId.startsWith("path:")) {
                val hover = element.hoverText
                element.copy(
                    bounds = scaleBounds(element.bounds, zoomScale),
                    hoverText = hover?.copy(
                        x = hover.x * zoomScale,
                        y = hover.y * zoomScale,
                        size = hover.size * zoomScale,
                    ),
                )
            } else element
        }

        return GestureGuiView(
            GestureGuiScreenDefinition(UPPER_SCREEN_ID, scaledElements, access = GestureGuiAccess.OWNER_ONLY),
            scaledVisuals,
            panel = GestureGuiPanel(
                width = GestureEditorLayout.UPPER_W,
                height = GestureEditorLayout.UPPER_H,
                backgroundMaterial = Material.GRAY_CONCRETE,
                frameMaterial = Material.LIGHT_GRAY_CONCRETE,
            ),
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
                        state.selectedAddPoint = null
                        state.pendingInsertion = null
                        state.lowerMode = GestureLowerMode.SETTINGS
                        state.settingsTab = 0
                        state.settingsPage = 0
                        updateUpper(player)
                        updateLower(player)
                    }
                    else -> Unit
                }
            }
            context.elementId == "viewport-empty" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.confirmNodeId = null
                state.pendingInsertion = null
                state.lowerMode = GestureLowerMode.SETTINGS
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "nav-zoom-in" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.zoomLevel = (state.zoomLevel + 1).coerceAtMost(3)
                updateUpper(player)
            }
            context.elementId == "nav-zoom-out" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.zoomLevel = (state.zoomLevel - 1).coerceAtLeast(-2)
                updateUpper(player)
            }
            context.elementId == "nav-zoom-reset" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.zoomLevel = 0
                updateUpper(player)
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
                val layout = GraphLayoutEngine.layout(script.graph)
                val nextOrigin = GestureEditorLayout.clampOrigin(
                    MapPoint(state.origin.x + delta.x, state.origin.y + delta.y), layout,
                )
                if (nextOrigin == state.origin) {
                    // 移動不能時も無反応にせず、操作対象へ短いフィードバックを返します。
                    player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.7f)
                    return
                }
                state.origin = nextOrigin
                updateUpper(player)
            }
            context.elementId == "back-to-start" && context.gesture == GestureGuiGesture.PRIMARY -> {
                // 最も先頭にある追加ポイントをビューに含めるよう原点を調整
                val script = plugin.scripts.load(state.scriptId)
                val layout = script?.let { GraphLayoutEngine.layout(it.graph) }
                val firstAdd = layout?.let { GestureEditorLayout.findFirstAddPoint(it.cells) }
                if (firstAdd != null) {
                    // 常に先頭追加ポイントをビューポート左上寄りの基準位置へ戻します。
                    val maxOx = (layout.width - GestureEditorLayout.VIEWPORT_COLS).coerceAtLeast(0)
                    val maxOy = (layout.height - GestureEditorLayout.VIEWPORT_ROWS).coerceAtLeast(0)
                    val ox = firstAdd.x.coerceIn(0, maxOx)
                    val oy = firstAdd.y.coerceIn(0, maxOy)
                    state.origin = MapPoint(ox, oy)
                } else {
                    state.origin = MapPoint(0, 0)
                }
                state.selectedNodeId = null
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
                state.selectedNodeId = null
                state.selectedAddPoint = MapPoint(gx, gy)
                state.lowerMode = GestureLowerMode.PICKER
                state.pickerCategory = 0
                state.pickerPage = 0
                updateUpper(player)
                updateLower(player)
            }
            context.elementId.startsWith("path:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                val point = context.elementId.removePrefix("path:").split(":").mapNotNull(String::toIntOrNull)
                if (point.size != 2) return
                val cell = GraphLayoutEngine.layout(script.graph).cells[MapPoint(point[0], point[1])] ?: return
                state.pendingInsertion = cell.insertionTarget ?: return
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.lowerMode = GestureLowerMode.PICKER
                state.pickerCategory = 0
                state.pickerPage = 0
                updateUpper(player)
                updateLower(player)
            }
            context.elementId.startsWith("lower-tab:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.settingsTab = context.elementId.removePrefix("lower-tab:").toIntOrNull() ?: return
                updateLower(player)
            }
            context.elementId.startsWith("lower-settings-page:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.settingsPage = context.elementId.removePrefix("lower-settings-page:").toIntOrNull() ?: return
                updateLower(player)
            }
            context.elementId.startsWith("lower-edit:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                val fieldKey = context.elementId.removePrefix("lower-edit:")
                if (fieldKey !in setOf(
                        "item", "count", "text", "stay", "ticks", "tags", "sound", "volume", "pitch",
                        "effect", "level", "seconds", "intensity", "diskId", "name", "startValue",
                        "endValue", "stepValue", "condition", "variable", "value",
                    )) return
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
                state.pickerPage = 0
                updateLower(player)
            }
            context.elementId.startsWith("lower-picker-page:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.pickerPage = context.elementId.removePrefix("lower-picker-page:").toIntOrNull() ?: return
                updateLower(player)
            }
            context.elementId.startsWith("lower-type:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                val typeName = context.elementId.removePrefix("lower-type:")
                val type = runCatching { CommandType.valueOf(typeName) }.getOrNull() ?: return
                val script = plugin.scripts.load(state.scriptId) ?: return
                val target = state.pendingInsertion
                    ?: InsertionTarget(null, GraphEditor.Edge.ENTRY)
                if (type == CommandType.FOR_END || (type == CommandType.MERGE && target.mergeConditionId == null)) {
                    // 合流は対応する分岐を持つ経路以外では選択できません。
                    state.lowerMode = GestureLowerMode.SETTINGS
                    updateLower(player)
                    return
                }
                val inserted = if (type == CommandType.MERGE) {
                    GraphEditor.appendMerge(script.graph, requireNotNull(target.mergeConditionId))
                } else {
                    GraphEditor.insert(script.graph, target.sourceId, target.edge, type)
                }
                plugin.scripts.save(script)
                state.pendingInsertion = null
                // 新規作成したコマンドを即座に選択し、下部設定パネルへ編集対象を引き継ぎます。
                state.selectedNodeId = inserted.id
                state.selectedAddPoint = null
                state.lowerMode = GestureLowerMode.SETTINGS
                state.settingsTab = 0
                state.settingsPage = 0
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "lower-close-picker" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.lowerMode = GestureLowerMode.SETTINGS
                updateLower(player)
            }
            context.elementId == "lower-delete" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.confirmNodeId = state.selectedNodeId ?: return
                openConfirmChild(player)
            }
            context.elementId == "confirm-delete" && context.gesture == GestureGuiGesture.PRIMARY -> {
                val nodeId = state.confirmNodeId ?: return
                val script = plugin.scripts.load(state.scriptId) ?: return
                if (GraphEditor.delete(script.graph, nodeId)) {
                    plugin.scripts.save(script)
                }
                state.confirmNodeId = null
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.lowerMode = GestureLowerMode.SETTINGS
                api.closeChild(player.uniqueId, lowerPanel.CONFIRM_SCREEN_ID)
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "confirm-cancel" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.confirmNodeId = null
                state.lowerMode = GestureLowerMode.SETTINGS
                api.closeChild(player.uniqueId, lowerPanel.CONFIRM_SCREEN_ID)
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

    private fun rect(cx: Double, cy: Double, width: Double, height: Double): GestureGuiBounds =
        GestureGuiBounds(cx - width / 2.0, cy - height / 2.0, cx + width / 2.0, cy + height / 2.0)

    private fun scaleBounds(bounds: GestureGuiBounds, scale: Double): GestureGuiBounds =
        GestureGuiBounds(bounds.minX * scale, bounds.minY * scale, bounds.maxX * scale, bounds.maxY * scale)

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

    /** ナビゲーション右側の縦積みズーム操作。ボタンはナビと同じ正方形寸法です。 */
    private fun addZoomControls(visuals: MutableList<GestureGuiVisual>, elements: MutableList<GestureGuiElement>) {
        listOf("nav-zoom-in" to "＋", "nav-zoom-out" to "−").forEachIndexed { index, (id, glyph) ->
            val x = GestureEditorLayout.ZOOM_X
            val y = GestureEditorLayout.ZOOM_TOP_Y + index * GestureEditorLayout.ZOOM_PITCH
            visuals.add(GestureGuiVisual.Block(
                visualId = "$id-block", x = x, y = y,
                width = GestureEditorLayout.ZOOM_SIZE, height = GestureEditorLayout.ZOOM_SIZE,
                blockData = Bukkit.createBlockData(Material.CYAN_CONCRETE), layer = 4,
            ))
            visuals.add(GestureGuiVisual.Text(
                visualId = "$id-glyph", x = x, y = y - 0.01,
                text = net.kyori.adventure.text.Component.text(glyph), size = 0.010, layer = 6,
            ))
            elements.add(GestureGuiElement(
                elementId = id,
                bounds = navBounds(x, y, GestureEditorLayout.ZOOM_SIZE),
                acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                targetVisualId = "$id-glyph",
            ))
        }
        val resetY = GestureEditorLayout.ZOOM_TOP_Y - GestureEditorLayout.ZOOM_PITCH
        visuals.add(GestureGuiVisual.Block(
            visualId = "nav-zoom-reset-block", x = GestureEditorLayout.ZOOM_X, y = resetY,
            width = GestureEditorLayout.ZOOM_SIZE, height = GestureEditorLayout.ZOOM_SIZE,
            blockData = Bukkit.createBlockData(Material.BROWN_CONCRETE), layer = 4,
        ))
        visuals.add(GestureGuiVisual.Text(
            visualId = "nav-zoom-reset-glyph", x = GestureEditorLayout.ZOOM_X, y = resetY - 0.01,
            text = net.kyori.adventure.text.Component.text("↺"), size = 0.009, layer = 6,
        ))
        elements.add(GestureGuiElement(
            elementId = "nav-zoom-reset",
            bounds = navBounds(GestureEditorLayout.ZOOM_X, resetY, GestureEditorLayout.ZOOM_SIZE),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            targetVisualId = "nav-zoom-reset-glyph",
        ))
    }

    /**
     * ビューポート内のセルから経路セグメントを生成します。
     * 経路セルは左右・上下隣の接続可能セル（経路/ノード/追加ポイント）との間に帯を張ります。
     * 配置は列/行インデックス基準です。
     */
    private fun buildPathSegments(
        viewportCells: Map<MapPoint, MapCell>,
        origin: MapPoint,
        allCells: Map<MapPoint, MapCell>,
    ): List<GestureEditorLayout.PathSegment> {
        val segments = linkedSetOf<GestureEditorLayout.PathSegment>()
        val pathKinds = setOf(MapCellKind.PATH, MapCellKind.BRANCH_PATH, MapCellKind.LOOP_RETURN_PATH)
        viewportCells.forEach { (localPoint, cell) ->
            if (cell.kind !in pathKinds) return@forEach
            val colIndex = localPoint.x
            val rowIndex = localPoint.y
            val cx = GestureEditorLayout.cellCenterX(colIndex)
            val cy = GestureEditorLayout.cellCenterY(rowIndex)
            val left = viewportCells[MapPoint(localPoint.x - 1, localPoint.y)]
            val right = viewportCells[MapPoint(localPoint.x + 1, localPoint.y)]
            val up = viewportCells[MapPoint(localPoint.x, localPoint.y - 1)]
            val down = viewportCells[MapPoint(localPoint.x, localPoint.y + 1)]
            if (left != null && left.kind in CONNECTABLE_KINDS) {
                segments.add(GestureEditorLayout.horizontalPath(cy, GestureEditorLayout.cellCenterX(colIndex - 1), cx))
            }
            val connectsRight = right != null && right.kind in CONNECTABLE_KINDS
            val connectsDown = down != null && down.kind in CONNECTABLE_KINDS
            if (connectsRight) segments.add(GestureEditorLayout.horizontalPath(cy, cx, GestureEditorLayout.cellCenterX(colIndex + 1)))
            if (up != null && up.kind in CONNECTABLE_KINDS) {
                segments.add(GestureEditorLayout.verticalPath(cx, GestureEditorLayout.cellCenterY(rowIndex - 1), cy))
            }
            if (connectsDown) segments.add(GestureEditorLayout.verticalPath(cx, cy, GestureEditorLayout.cellCenterY(rowIndex + 1)))
        }
        // 経路セルが省略された末端は、挿入元IDが一致する追加ポイントだけを接続します。
        viewportCells.forEach { (p, cell) ->
            if (cell.kind != MapCellKind.ADD) return@forEach
            listOf(MapPoint(p.x - 2, p.y), MapPoint(p.x + 2, p.y), MapPoint(p.x, p.y - 2), MapPoint(p.x, p.y + 2))
                .mapNotNull { candidate -> viewportCells[candidate]?.let { candidate to it } }
                .filter { (_, other) -> other.kind == MapCellKind.NODE && other.nodeId == cell.insertionTarget?.sourceId }
                .forEach { (n, _) ->
                    val x1 = GestureEditorLayout.cellCenterX(p.x)
                    val y1 = GestureEditorLayout.cellCenterY(p.y)
                    val x2 = GestureEditorLayout.cellCenterX(n.x)
                    val y2 = GestureEditorLayout.cellCenterY(n.y)
                    if (p.y == n.y) {
                        val mid = (x1 + x2) / 2.0
                        segments.add(GestureEditorLayout.horizontalPath(y1, x1, mid))
                        segments.add(GestureEditorLayout.horizontalPath(y1, mid, x2))
                    } else {
                        val mid = (y1 + y2) / 2.0
                        segments.add(GestureEditorLayout.verticalPath(x1, y1, mid))
                        segments.add(GestureEditorLayout.verticalPath(x1, mid, y2))
                    }
                }
        }
        // ビューポート端の先にも論理セルが続く場合は、画面端までのスタブを描画します。
        val maxX = GestureEditorLayout.VIEWPORT_COLS - 1
        val maxY = GestureEditorLayout.VIEWPORT_ROWS - 1
        viewportCells.forEach { (p, cell) ->
            if (cell.kind !in CONNECTABLE_KINDS) return@forEach
            val global = MapPoint(origin.x + p.x, origin.y + p.y)
            listOf(
                MapPoint(-1, 0) to (p.x == 0),
                MapPoint(1, 0) to (p.x == maxX),
                MapPoint(0, -1) to (p.y == 0),
                MapPoint(0, 1) to (p.y == maxY),
            ).forEach { (delta, atEdge) ->
                if (!atEdge || allCells[MapPoint(global.x + delta.x, global.y + delta.y)]?.kind !in CONNECTABLE_KINDS) return@forEach
                val cx = GestureEditorLayout.cellCenterX(p.x)
                val cy = GestureEditorLayout.cellCenterY(p.y)
                if (delta.x != 0) {
                    val outsideX = GestureEditorLayout.cellCenterX(if (delta.x < 0) -1 else GestureEditorLayout.VIEWPORT_COLS)
                    segments.add(GestureEditorLayout.horizontalPath(cy, cx, outsideX))
                } else {
                    val outsideY = GestureEditorLayout.cellCenterY(if (delta.y < 0) -1 else GestureEditorLayout.VIEWPORT_ROWS)
                    segments.add(GestureEditorLayout.verticalPath(cx, cy, outsideY))
                }
            }
        }
        return segments.toList()
    }

    private companion object {
        val CONNECTABLE_KINDS = MapCellKind.entries.toSet()
    }
}
