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
import kotlin.math.roundToInt

/**
 * ジェスチャーエディターの状態。プレイヤー単位で保持され、操作のたびに更新されます。
 */
data class GestureEditorState(
    var scriptId: UUID,
    var placement: DiskPlacement?,
    var origin: MapPoint = MapPoint(0, 0),
    /** ビューポート表示倍率。初期値は最大倍率、縮小25%まで25%刻みで許可します。 */
    var zoomLevel: Int = GestureEditorLayout.INITIAL_ZOOM_LEVEL,
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
    /** PICKERへ遷移した経路上で実際にクリックされた判定セル。競合検証に使います。 */
    var selectedInsertionCandidatePoint: MapPoint? = null,
    /** PICKERで作成するノードの配置予定位置。ズーム／パン後も同じ位置を示します。 */
    var selectedInsertionPoint: MapPoint? = null,
)

/** 下部パネルの表示モード。CONFIRMのみ子画面（赤ガラス）として開きます。 */
enum class GestureLowerMode {
    SETTINGS,
    PICKER,
    CONFIRM,
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * ズーム後も論理セル範囲と画面上の座標系を一致させる値です。
 *
 * x/y と各寸法はズーム前の値で保持します。buildUpperViewportの最後で同じ倍率を
 * 全マップ要素へ適用するため、アイコン・経路・当たり判定の投影が常に一致します。
 */
private data class ViewportMetrics(
    val zoomScale: Double,
    val columns: Int,
    val rows: Int,
    val firstX: Double,
    val firstY: Double,
    val pitchX: Double,
    val pitchY: Double,
    val iconSize: Double,
    val iconScale: Double,
    val pathThickness: Double,
    /** ズーム後のグラフ領域。最終クリップと同じ矩形を共有します。 */
    val graphMinX: Double,
    val graphMaxX: Double,
    val graphMinY: Double,
    val graphMaxY: Double,
) {
    fun x(local: Int): Double = firstX + local * pitchX
    fun x(local: Double): Double = firstX + local * pitchX
    fun y(local: Int): Double = firstY - local * pitchY
    fun y(local: Double): Double = firstY - local * pitchY
}

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
        val zoomScale = zoomScale()
        val metrics = viewportMetrics(zoomScale)
        // ズーム変更後に前回の原点が新しい表示可能範囲を越えないよう、
        // 描画・入力判定で共有する原点を毎回正規化します。
        state.origin = GestureEditorLayout.clampOrigin(
            state.origin,
            layout,
            metrics.columns,
            metrics.rows,
        )
        // アイコン・経路・経路の入力判定は必ず同じ投影を共有します。
        // 経路だけを画面外へ拡張すると、アイコンが消えた後も帯だけ残るため、
        // 画面外へ続く接続は projection の境界情報だけで表現します。
        val projection = layout.projection(state.origin, metrics.columns, metrics.rows)
        val cells = projection.cells
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        // 画面内の余白クリックをActionへ届け、選択状態を解除できるようにします。
        elements.add(GestureGuiElement(
            elementId = "viewport-empty",
            bounds = GestureGuiBounds(
                -GestureEditorLayout.UPPER_W / 2.0 + GestureEditorLayout.FRAME_WIDTH,
                -GestureEditorLayout.UPPER_H / 2.0 + GestureEditorLayout.FRAME_WIDTH,
                GestureEditorLayout.UPPER_W / 2.0 - GestureEditorLayout.FRAME_WIDTH,
                GestureEditorLayout.UPPER_H / 2.0 - GestureEditorLayout.FRAME_WIDTH,
            ),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
        ))

        cells.forEach { (localPoint, cell) ->
            // セル検索はグリッド座標、配置は列/行インデックスで行う（origin移動してもグリッド位置は固定）
            val colIndex = localPoint.x
            val rowIndex = localPoint.y
            val gx = state.origin.x + colIndex
            val gy = state.origin.y + rowIndex
            val cx = metrics.x(colIndex)
            val cy = metrics.y(rowIndex)
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
                            width = metrics.iconSize,
                            height = metrics.iconSize,
                            blockData = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                            // 背景は常にアイコンの背面。選択時もlayerを変えず素材色/glowだけを変えます。
                            layer = GestureEditorLayout.ICON_BACKGROUND_LAYER,
                            glowColor = glowColor,
                        ))
                        // マスの90% (ICON_W=0.198) に合わせます。追加ポイントの背景・記号も同じ寸法です。
                        visuals.add(GestureGuiVisual.Item(
                            visualId = "node-icon-${node.id}",
                            x = cx, y = cy,
                            item = org.bukkit.inventory.ItemStack(node.type.icon),
                            scale = metrics.iconScale,
                            // 選択表現は背面セルだけに付け、アイコン自体は常に同じ前景レイヤーへ置きます。
                            layer = GestureEditorLayout.ICON_LAYER,
                            glowColor = null,
                        ))
                        elements.add(GestureGuiElement(
                            elementId = "node:${node.id}",
                            bounds = iconBounds(cx, cy, metrics.iconSize),
                            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                            targetVisualId = "node-icon-${node.id}",
                            hoverText = GestureGuiHoverText(
                                text = net.kyori.adventure.text.Component.text(KcI18n.text(player, node.type.key)),
                                x = cx,
                                y = cy + metrics.iconSize * 0.9,
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
                        width = metrics.iconSize,
                        height = metrics.iconSize,
                        blockData = Bukkit.createBlockData(Material.YELLOW_CONCRETE),
                        layer = GestureEditorLayout.ICON_BACKGROUND_LAYER,
                        glowColor = if (isSelected) Color.YELLOW.asARGB() else null,
                    ))
                    visuals.add(GestureGuiVisual.Text(
                        visualId = "add-plus-$gx-$gy",
                        // 新規追加もコマンドアイコンと同じ中心・前景レベルで表示します。
                        x = cx, y = cy,
                        text = net.kyori.adventure.text.Component.text("+"),
                        size = 0.012,
                        layer = GestureEditorLayout.ICON_LAYER,
                    ))
                    elements.add(GestureGuiElement(
                        elementId = "add:$gx:$gy",
                        bounds = iconBounds(cx, cy, metrics.iconSize),
                        acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                        targetVisualId = "add-plus-$gx-$gy",
                        hoverText = GestureGuiHoverText(
                            text = net.kyori.adventure.text.Component.text("クリックで追加"),
                            x = cx,
                            y = cy + metrics.iconSize * 0.9,
                            size = 0.006,
                            lineWidth = 120,
                        ),
                    ))
                }
                MapCellKind.PATH, MapCellKind.BRANCH_PATH, MapCellKind.LOOP_RETURN_PATH -> {
                    // 追加ポイント直前の経路は「クリックで挿入」を表示しません。
                    val hasAddNeighbor = projection.hasNeighborOfKind(localPoint, MapCellKind.ADD)
                    val verticalBranchOnly = cell.kind == MapCellKind.BRANCH_PATH &&
                        (cells[MapPoint(localPoint.x, localPoint.y - 1)]?.kind in CONNECTABLE_CELL_KINDS ||
                            cells[MapPoint(localPoint.x, localPoint.y + 1)]?.kind in CONNECTABLE_CELL_KINDS) &&
                        cells[MapPoint(localPoint.x - 1, localPoint.y)]?.kind !in CONNECTABLE_CELL_KINDS &&
                        cells[MapPoint(localPoint.x + 1, localPoint.y)]?.kind !in CONNECTABLE_CELL_KINDS
                    if (!hasAddNeighbor && !verticalBranchOnly && cell.insertionTarget != null) {
                        elements.add(GestureGuiElement(
                            elementId = "path:${gx}:${gy}",
                            bounds = rect(cx, cy, metrics.pitchX, metrics.pitchY),
                            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
                            hoverText = GestureGuiHoverText(
                                text = net.kyori.adventure.text.Component.text("クリックで挿入"),
                                x = cx,
                                y = cy + metrics.pathThickness,
                                size = 0.0055,
                                lineWidth = 120,
                            ),
                        ))
                    }
                }
            }
        }

        GesturePathRenderer.buildSegments(
            cells,
            boundaryConnections = projection.boundaryConnections,
            xCenter = metrics::x,
            yCenter = metrics::y,
            thickness = metrics.pathThickness,
            clipBounds = GesturePathRenderer.ClipBounds(
                // 仮想境界セルをグラフ領域の端までだけ延長します。これより外側へ
                // 経路を出さないため、ナビゲーション列との重なりが発生しません。
                minX = GestureEditorLayout.UPPER_GRAPH_MIN_X / zoomScale,
                maxX = GestureEditorLayout.UPPER_GRAPH_MAX_X / zoomScale,
                minY = GestureEditorLayout.UPPER_GRAPH_MIN_Y / zoomScale,
                maxY = GestureEditorLayout.UPPER_GRAPH_MAX_Y / zoomScale,
            ),
        ).forEach { seg ->
            visuals.add(GestureGuiVisual.Block(
                visualId = "path-${seg.x}-${seg.y}-${seg.w}-${seg.h}",
                x = seg.x, y = seg.y,
                width = seg.w, height = seg.h,
                blockData = Bukkit.createBlockData(Material.WHITE_CONCRETE),
                layer = 1,
            ))
        }

        // 経路をクリックしてPICKERへ移った場合は、クリック元の経路ではなく、
        // 作成後に新ノードが配置される位置を背景側だけ発光させます。経路素材や
        // 既存アイコン自体の色は変更しません。連続経路上のどのセルをクリックしても
        // 同じ挿入先エッジへ入るため、ハイライト位置はクリックセルから切り離します。
        state.selectedInsertionPoint?.let { selectedGlobal ->
            val local = MapPoint(
                selectedGlobal.x - state.origin.x,
                selectedGlobal.y - state.origin.y,
            )
            if (projection.contains(local)) {
                val cx = metrics.x(local.x)
                val cy = metrics.y(local.y)
                visuals.add(GestureGuiVisual.Block(
                    visualId = "path-highlight-preview-${selectedGlobal.x}-${selectedGlobal.y}",
                    x = cx,
                    y = cy,
                    width = metrics.iconSize,
                    height = metrics.iconSize,
                    blockData = Bukkit.createBlockData(Material.LIGHT_GRAY_CONCRETE),
                    layer = GestureEditorLayout.ICON_BACKGROUND_LAYER,
                    glowColor = Color.YELLOW.asARGB(),
                ))
            }
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
        addCloseButton(visuals, elements)

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
        val clippedVisuals = scaledVisuals.filter { visual ->
            if (!(visual.visualId.startsWith("node-") || visual.visualId.startsWith("add-") || visual.visualId.startsWith("path-"))) return@filter true
            // マップ要素は、入力要素・経路と同じグラフ領域へクリップします。
            // パネル全体へ別の矩形で切り取ると、ノードだけがナビ列へ流出するため、
            // 端の継続経路もここで同じ境界に揃えます。
            val minX = metrics.graphMinX
            val maxX = metrics.graphMaxX
            val minY = metrics.graphMinY
            val maxY = metrics.graphMaxY
            val halfVisualW = when (visual) {
                is GestureGuiVisual.Block -> visual.width / 2.0
                is GestureGuiVisual.Item -> metrics.iconSize * zoomScale / 2.0
                is GestureGuiVisual.Text -> 0.06 * zoomScale
            }
            val halfVisualH = when (visual) {
                is GestureGuiVisual.Block -> visual.height / 2.0
                is GestureGuiVisual.Item -> metrics.iconSize * zoomScale / 2.0
                is GestureGuiVisual.Text -> 0.04 * zoomScale
            }
            visual.x + halfVisualW >= minX && visual.x - halfVisualW <= maxX &&
                visual.y + halfVisualH >= minY && visual.y - halfVisualH <= maxY
        }

        return GestureGuiView(
            GestureGuiScreenDefinition(UPPER_SCREEN_ID, scaledElements, access = GestureGuiAccess.OWNER_ONLY),
            clippedVisuals,
            panel = GestureGuiPanel(
                width = GestureEditorLayout.UPPER_W,
                height = GestureEditorLayout.UPPER_H,
                backgroundMaterial = Material.GRAY_CONCRETE,
                frameMaterial = Material.LIGHT_GRAY_CONCRETE,
                frameWidth = GestureEditorLayout.FRAME_WIDTH,
            ),
        ) { context -> handleUpperAction(context) }
    }

    private fun handleUpperAction(context: GestureGuiActionContext) {
        val player = Bukkit.getPlayer(context.ownerId) ?: return
        when {
            context.elementId.startsWith("node:") -> {
                val nodeId = runCatching { UUID.fromString(context.elementId.removePrefix("node:")) }.getOrNull() ?: return
                // 画面更新後に削除されたノードからの遅延入力は、選択も効果音も発生させません。
                val script = plugin.scripts.load(state.scriptId) ?: return
                if (script.graph.nodes[nodeId] == null) return
                when (context.gesture) {
                    GestureGuiGesture.PRIMARY -> {
                        state.selectedNodeId = nodeId
                        state.selectedAddPoint = null
                        state.selectedInsertionCandidatePoint = null
                        state.selectedInsertionPoint = null
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
                state.selectedInsertionCandidatePoint = null
                state.selectedInsertionPoint = null
                state.confirmNodeId = null
                state.pendingInsertion = null
                state.lowerMode = GestureLowerMode.SETTINGS
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "nav-zoom-in" && context.gesture == GestureGuiGesture.PRIMARY -> {
                val next = (state.zoomLevel + 1).coerceAtMost(GestureEditorLayout.MAX_ZOOM_LEVEL)
                if (next != state.zoomLevel) {
                    setZoomLevel(next)
                    updateUpper(player)
                }
            }
            context.elementId == "nav-zoom-out" && context.gesture == GestureGuiGesture.PRIMARY -> {
                val next = (state.zoomLevel - 1).coerceAtLeast(GestureEditorLayout.MIN_ZOOM_LEVEL)
                if (next != state.zoomLevel) {
                    setZoomLevel(next)
                    updateUpper(player)
                }
            }
            context.elementId == "nav-zoom-reset" && context.gesture == GestureGuiGesture.PRIMARY -> {
                if (state.zoomLevel != GestureEditorLayout.INITIAL_ZOOM_LEVEL) {
                    setZoomLevel(GestureEditorLayout.INITIAL_ZOOM_LEVEL)
                    updateUpper(player)
                }
            }
            context.elementId.startsWith("nav-") && context.elementId != "nav-close" &&
                context.gesture == GestureGuiGesture.PRIMARY -> {
                val delta = when (context.elementId) {
                    "nav-up" -> MapPoint(0, -1)
                    "nav-down" -> MapPoint(0, 1)
                    "nav-left" -> MapPoint(-1, 0)
                    "nav-right" -> MapPoint(1, 0)
                    else -> return
                }
                val script = plugin.scripts.load(state.scriptId) ?: return
                val layout = GraphLayoutEngine.layout(script.graph)
                val metrics = viewportMetrics(zoomScale())
                val nextOrigin = GestureEditorLayout.clampOrigin(
                    MapPoint(state.origin.x + delta.x, state.origin.y + delta.y),
                    layout,
                    metrics.columns,
                    metrics.rows,
                )
                if (nextOrigin == state.origin) {
                    // 移動不能時は状態・更新・効果音のいずれも発生させません。
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
                    // 枝が最も進んだ追加ポイントが範囲外なら、右端／下端に
                    // 入る位置まで原点を移動します。単純にポイント座標を原点へ
                    // 代入すると、マップ末端では表示範囲を越えてしまいます。
                    val metrics = viewportMetrics(zoomScale())
                    state.origin = GestureEditorLayout.revealOrigin(
                        state.origin,
                        firstAdd,
                        layout,
                        metrics.columns,
                        metrics.rows,
                    )
                } else {
                    state.origin = MapPoint(0, 0)
                }
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                state.selectedInsertionPoint = null
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
                state.selectedInsertionCandidatePoint = null
                state.selectedInsertionPoint = null
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
                val layout = GraphLayoutEngine.layout(script.graph)
                val clickedPoint = MapPoint(point[0], point[1])
                val cell = layout.cells[clickedPoint] ?: return
                val target = cell.insertionTarget ?: return
                state.pendingInsertion = target
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = clickedPoint
                state.selectedInsertionPoint = layout.insertionPreviewPoint(clickedPoint, target) ?: clickedPoint
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
                // PICKERを開いている間に別の編集が発生した場合、古い座標の候補を
                // そのまま適用しません。現在のレイアウト上でも同じセルが同じ
                // 挿入先を示すことを確認し、連続経路の装飾セルへの誤挿入を防ぎます。
                state.selectedInsertionCandidatePoint?.let { point ->
                    val currentTarget = GraphLayoutEngine.layout(script.graph)
                        .cells[point]
                        ?.insertionTarget
                    if (currentTarget != target) return
                }
                if (type == CommandType.FOR_END || (type == CommandType.MERGE &&
                        (target.mergeConditionId == null || !GraphEditor.canAppendMerge(script.graph, target.mergeConditionId)))) {
                    // 合流は対応する分岐を持つ経路以外では選択できません。
                    state.lowerMode = GestureLowerMode.SETTINGS
                    updateLower(player)
                    return
                }
                // 変更前の取得結果を直接壊さず、レイアウト・構造検証・保存まで
                // 一つの候補グラフで完了させます。描画不能なグラフが発生しても
                // 保存前に破棄され、サーバーイベントへ例外を漏らしません。
                val candidateGraph = script.graph.deepCopy()
                val inserted = runCatching {
                    if (type == CommandType.MERGE) {
                        // 画面表示後に別操作でグラフが変わる競合にも例外を漏らしません。
                        GraphEditor.appendMerge(candidateGraph, requireNotNull(target.mergeConditionId))
                    } else {
                        GraphEditor.insert(candidateGraph, target.sourceId, target.edge, type)
                    }
                }.mapCatching { insertedNode ->
                    // 保存処理も同じ候補グラフで行い、GraphLayoutEngineの衝突検査を
                    // 永続化前に通します。
                    GraphLayoutEngine.layout(candidateGraph)
                    plugin.scripts.save(script.copy(graph = candidateGraph))
                    insertedNode
                }.getOrNull() ?: return
                state.pendingInsertion = null
                state.selectedInsertionCandidatePoint = null
                state.selectedInsertionPoint = null
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
                state.pendingInsertion = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                state.selectedInsertionPoint = null
                state.lowerMode = GestureLowerMode.SETTINGS
                updateLower(player)
            }
            context.elementId == "nav-close" && context.gesture == GestureGuiGesture.PRIMARY -> {
                // 右上の閉じる操作は、親・子画面と入力claimをまとめて即時解放します。
                closeImmediately(player.uniqueId)
            }
            context.elementId == "lower-delete" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.confirmNodeId = state.selectedNodeId ?: return
                openConfirmChild(player)
            }
            context.elementId == "confirm-delete" && context.gesture == GestureGuiGesture.PRIMARY -> {
                val nodeId = state.confirmNodeId ?: return
                val script = plugin.scripts.load(state.scriptId) ?: return
                if (!GraphEditor.delete(script.graph, nodeId)) return
                plugin.scripts.save(script)
                state.confirmNodeId = null
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                state.selectedInsertionPoint = null
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

    private fun iconBounds(cx: Double, cy: Double, size: Double): GestureGuiBounds {
        val h = size / 2.0
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
            val enabled = if (id == "nav-zoom-in") {
                state.zoomLevel < GestureEditorLayout.MAX_ZOOM_LEVEL
            } else {
                state.zoomLevel > GestureEditorLayout.MIN_ZOOM_LEVEL
            }
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
                // 上限／下限では要素を無効化し、クリック音を含めて何もしません。
                acceptedGestures = if (enabled) setOf(GestureGuiGesture.PRIMARY) else emptySet(),
                targetVisualId = "$id-glyph",
            ))
        }
        val resetY = GestureEditorLayout.ZOOM_TOP_Y - GestureEditorLayout.ZOOM_PITCH
        val resetEnabled = state.zoomLevel != GestureEditorLayout.INITIAL_ZOOM_LEVEL
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
            acceptedGestures = if (resetEnabled) setOf(GestureGuiGesture.PRIMARY) else emptySet(),
            targetVisualId = "nav-zoom-reset-glyph",
        ))
    }

    /** 画面右上へ配置する明示的な終了導線です。 */
    private fun addCloseButton(visuals: MutableList<GestureGuiVisual>, elements: MutableList<GestureGuiElement>) {
        val x = GestureEditorLayout.CLOSE_X
        val y = GestureEditorLayout.CLOSE_Y
        val size = GestureEditorLayout.CLOSE_SIZE
        visuals.add(GestureGuiVisual.Block(
            visualId = "nav-close-block",
            x = x,
            y = y,
            width = size,
            height = size,
            blockData = Bukkit.createBlockData(Material.RED_CONCRETE),
            layer = 4,
        ))
        visuals.add(GestureGuiVisual.Text(
            visualId = "nav-close-glyph",
            x = x,
            y = y - 0.01,
            text = net.kyori.adventure.text.Component.text("×"),
            size = 0.010,
            layer = 6,
        ))
        elements.add(GestureGuiElement(
            elementId = "nav-close",
            bounds = navBounds(x, y, size),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            targetVisualId = "nav-close-glyph",
            hoverText = GestureGuiHoverText(
                text = net.kyori.adventure.text.Component.text("閉じる"),
                x = x,
                y = y - size,
                size = 0.0055,
                lineWidth = 80,
            ),
        ))
    }

    private fun zoomScale(): Double =
        (GestureEditorLayout.DEFAULT_ZOOM +
            state.zoomLevel.coerceIn(GestureEditorLayout.MIN_ZOOM_LEVEL, GestureEditorLayout.MAX_ZOOM_LEVEL) * 0.25)
            .coerceIn(0.25, GestureEditorLayout.DEFAULT_ZOOM)

    /**
     * 倍率変更時も現在見ている論理セルの中心を保ちます。
     * 可視列数だけを変更して原点を固定すると、ズームアウト時にマップが片側へ
     * 飛び、端の経路とアイコンの対応が崩れます。グラフの範囲を考慮してから
     * 新しい表示可能範囲へ原点をclampすることで、中央基準の投影を維持します。
     */
    private fun setZoomLevel(level: Int) {
        val script = plugin.scripts.load(state.scriptId) ?: run {
            state.zoomLevel = level
            return
        }
        val oldMetrics = viewportMetrics(zoomScale())
        val centerX = state.origin.x + (oldMetrics.columns - 1) / 2.0
        val centerY = state.origin.y + (oldMetrics.rows - 1) / 2.0
        state.zoomLevel = level
        val newMetrics = viewportMetrics(zoomScale())
        state.origin = GestureEditorLayout.clampOrigin(
            MapPoint(
                (centerX - (newMetrics.columns - 1) / 2.0).roundToInt(),
                (centerY - (newMetrics.rows - 1) / 2.0).roundToInt(),
            ),
            GraphLayoutEngine.layout(script.graph),
            newMetrics.columns,
            newMetrics.rows,
        )
    }

    private fun viewportMetrics(scale: Double): ViewportMetrics {
        val columns = GestureEditorLayout.viewportColumns(scale)
        val rows = GestureEditorLayout.viewportRows(scale)
        require(scale.isFinite() && scale > 0.0) { "viewport zoom scale must be positive and finite" }

        /*
         * 表示面の実寸を先に決め、そこへ可視論理セルを等分します。
         * 以前は固定のFIRST_COL_X/FIRST_ROW_Yを拡大縮小していたため、
         * 10×4の論理範囲が画面中央の狭い帯に留まり、ズーム時には
         * アイコンと経路の端点も別々に切り取られていました。
         * ここでは「画面上の寸法」を基準にし、最後の一括scaleで戻せる
         * ズーム前座標へ変換します。
         */
        val screenPitchX =
            (GestureEditorLayout.UPPER_GRAPH_MAX_X - GestureEditorLayout.UPPER_GRAPH_MIN_X) / columns
        val screenPitchY =
            (GestureEditorLayout.UPPER_GRAPH_MAX_Y - GestureEditorLayout.UPPER_GRAPH_MIN_Y) / rows
        val screenIconSize = minOf(screenPitchX, screenPitchY) * 0.9
        val screenPathThickness = minOf(screenPitchX, screenPitchY) * 2.0 / 3.0
        val iconScaleRatio = GestureEditorLayout.ICON_SCALE / GestureEditorLayout.ICON_W
        return ViewportMetrics(
            zoomScale = scale,
            columns = columns,
            rows = rows,
            firstX = (GestureEditorLayout.UPPER_GRAPH_MIN_X + screenPitchX / 2.0) / scale,
            firstY = (GestureEditorLayout.UPPER_GRAPH_MAX_Y - screenPitchY / 2.0) / scale,
            pitchX = screenPitchX / scale,
            pitchY = screenPitchY / scale,
            iconSize = screenIconSize / scale,
            iconScale = screenIconSize * iconScaleRatio / scale,
            pathThickness = screenPathThickness / scale,
            graphMinX = GestureEditorLayout.UPPER_GRAPH_MIN_X,
            graphMaxX = GestureEditorLayout.UPPER_GRAPH_MAX_X,
            graphMinY = GestureEditorLayout.UPPER_GRAPH_MIN_Y,
            graphMaxY = GestureEditorLayout.UPPER_GRAPH_MAX_Y,
        )
    }

}
