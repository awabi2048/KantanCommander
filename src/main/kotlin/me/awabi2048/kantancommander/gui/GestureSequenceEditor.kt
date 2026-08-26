package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccess
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOpenOptions
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenDefinition
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiView
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import me.awabi2048.kantancommander.KantanCommanderPlugin
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
)

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * ジェスチャーエディターの主要コントローラー。
 * プレイヤー単位に開かれたジェスチャーGUIセッションを管理し、上部ビューポートを生成します。
 * 下部パネルは既存のMenuRuntimeServiceによるインベントリGUIを継続利用します（v1）。
 */
class GestureSequenceEditor(
    private val plugin: KantanCommanderPlugin,
    private val viewportState: GestureEditorState,
) {
    private val api get() = CCSystem.getAPI().getGestureGuiService()

    private val UPPER_SCREEN_ID = "gesture-editor-upper"

    fun open(player: Player) {
        val upper = buildUpperViewport()
        api.open(player, listOf(upper), GestureGuiOpenOptions(anchor = viewportState.anchor))
    }

    fun updateViewport(player: Player) {
        val upper = buildUpperViewport()
        api.updateScreen(player.uniqueId, upper)
    }

    private fun buildUpperViewport(): GestureGuiView {
        val script = plugin.scripts.load(viewportState.scriptId) ?: return emptyView()
        val layout = GraphLayoutEngine.layout(script.graph)
        val cells = layout.viewport(viewportState.origin, GestureEditorLayout.VIEWPORT_COLS, GestureEditorLayout.VIEWPORT_ROWS)
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()

        cells.forEach { (localPoint, cell) ->
            val gx = viewportState.origin.x + localPoint.x
            val gy = viewportState.origin.y + localPoint.y
            val cx = GestureEditorLayout.cellCenterX(gx)
            val cy = GestureEditorLayout.cellCenterY(gy)
            when (cell.kind) {
                MapCellKind.NODE -> {
                    val node = cell.nodeId?.let { script.graph.nodes[it] }
                    if (node != null) {
                        val isSelected = viewportState.selectedNodeId == node.id
                        // 選択中ノードは色付きglow＋前面レイヤー。Geyser非対応時はglowが見えないため、
                        // 実機検証時に背景色変更フォールバックの併用を検討します。
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

        buildPathSegments(cells, viewportState.origin).forEach { seg ->
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
                if (context.gesture == GestureGuiGesture.PRIMARY) {
                    viewportState.selectedNodeId = nodeId
                    updateViewport(player)
                } else if (context.gesture == GestureGuiGesture.SECONDARY) {
                    // TODO: 削除確認子画面（CONFIRM）を下部画面上へ開く
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
                val script = plugin.scripts.load(viewportState.scriptId) ?: return
                viewportState.origin = GestureEditorLayout.clampOrigin(
                    MapPoint(viewportState.origin.x + delta.x, viewportState.origin.y + delta.y),
                    GraphLayoutEngine.layout(script.graph),
                )
                updateViewport(player)
            }
            context.elementId == "back-to-start" && context.gesture == GestureUiGestureSafe.PRIMARY -> {
                viewportState.selectedNodeId = null
                viewportState.origin = MapPoint(0, 0)
                updateViewport(player)
            }
            context.elementId.startsWith("add:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                // TODO: 追加ピッカー（既存typeRoute相当）を下部へ表示
            }
        }
    }

    private object GestureUiGestureSafe {
        val PRIMARY = GestureGuiGesture.PRIMARY
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
     * ノードの裏(z=1)に潜ることで、アイコンと厳密に繋がって見えます。
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
