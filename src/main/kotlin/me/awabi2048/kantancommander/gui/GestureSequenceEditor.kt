package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuDialogResponse
import com.awabi2048.ccsystem.api.gui.MenuUpdate
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
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiSessionListener
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiView
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenLayout
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVerticalSlot
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.item.ItemStackCodec
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
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
    /** 確認子画面の用途（削除／アイテム上書き） */
    var confirmKind: GestureConfirmKind = GestureConfirmKind.DELETE,
    /** アイテム上書き確認が保持する対象と完全なItemStackデータ */
    var pendingItemContext: CommandSettingContext? = null,
    var pendingItemKey: String? = null,
    var pendingItemData: String? = null,
    /** PICKERで選択中の挿入先（addポイントクリック時に保持） */
    var pendingInsertion: InsertionTarget? = null,
    /** PICKERへ遷移した追加ポイントの選択状態。既存ノード選択とは独立して表示します。 */
    var selectedAddPoint: MapPoint? = null,
    /** PICKERへ遷移した経路上で実際にクリックされた判定セル。競合検証に使います。 */
    var selectedInsertionCandidatePoint: MapPoint? = null,
    /** PICKERで作成するノードの配置予定位置。ズーム／パン後も同じ位置を示します。 */
    var selectedInsertionPoint: MapPoint? = null,
    /** 個別設定画面が参照する共有コンテキスト（インベントリGUIと同じ識別情報）。 */
    var settingContext: CommandSettingContext? = null,
    /** 個別設定画面を開いたフィールドキー。設定保存後の表示更新に使います。 */
    var settingFieldKey: String? = null,
    /** 設定木で現在選択中の経路。画面名ではなくドメイン階層を保持します。 */
    var settingTreePath: GestureSettingTreePath? = null,
    /** 設定木の親子経路。子画面の戻る操作で一つ上のドメインへ戻します。 */
    var settingRoute: List<GestureSettingFrame> = emptyList(),
    /** 個別設定画面の現在の意味上の編集経路。 */
    var settingScreen: GestureSettingScreen? = null,
    var settingPage: Int = 0,
)

/** 下部パネルの表示モード。CONFIRMのみ子画面（赤ガラス）として開きます。 */
enum class GestureLowerMode {
    SETTINGS,
    PICKER,
    SETTING_CHOICES,
    CONFIRM,
}

enum class GestureConfirmKind {
    DELETE,
    ITEM_OVERWRITE,
}

/**
 * ジェスチャーGUI内で表示する個別設定の意味上の画面です。
 * インベントリGUIの画面IDではなく、CommandSettingsModelの編集経路を表します。
 */
enum class GestureSettingScreen {
    TARGET,
    TARGET_FILTERS,
    POSITION,
    FACING,
    CONDITION_KIND,
    CONDITION_DETAIL,
    DISPLAY_MODE,
    ENTITY_ACTION,
    VARIABLE_SCOPE,
    VARIABLE_TYPE,
    VARIABLE_OPERATION,
    VARIABLE_VALUE,
    FOR_SOURCE,
    INCLUSIVE_END,
    CONTEXT_OVERRIDE,
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
    private val onSessionClosed: (GestureSequenceEditor, UUID, UUID) -> Unit = { _, _, _ -> },
) {
    private val api get() = CCSystem.getAPI().getGestureGuiService()
    // 下部画面のクリックは上部と同じハンドラで処理します（タブ切替・PICKER・CONFIRMの共通ロジック）。
    private val lowerPanel = GestureLowerPanel(plugin, onAction = { ctx -> handleUpperAction(ctx) })

    private val UPPER_SCREEN_ID = "gesture-editor-upper"
    /** ダイアログの遅延コールバックを、GUI終了・別編集後に無効化する世代トークン。 */
    private var activeInputToken: UUID? = null
    /** 所有権を照合して閉じるための、現在の入力Dialog ID。 */
    private var activeInputDialogId: String? = null
    /** このエディターが所有するGesture GUIセッション。再オープン後の古い応答を遮断します。 */
    private var gestureSessionId: UUID? = null

    internal fun isEditing(placement: DiskPlacement): Boolean = state.placement?.key == placement.key

    /** Facadeが終了通知を新しいエディターへ誤適用しないよう、現在のIDを照合します。 */
    internal fun isCurrentSession(sessionId: UUID): Boolean = gestureSessionId == sessionId

    /**
     * CC-System側から届く終了通知を、エディターのローカル状態へ反映します。
     * 通知経路ではCC-Systemのcloseを再度呼ばず、終了要求との再入を防ぎます。
     */
    internal fun onGestureSessionClosed(ownerId: UUID, sessionId: UUID) {
        if (gestureSessionId != sessionId) return
        detachLocalSession(ownerId, sessionId)
    }

    internal fun closeImmediately(ownerId: UUID) {
        // GUIを閉じた後に遅延した入力コールバックが設定を書き換えないよう、
        // 画面の終了を入力セッションの終了として扱います。
        val sessionId = gestureSessionId
        val ownsCurrentServiceSession = sessionId != null && runCatching {
            api.snapshot(ownerId)?.sessionId == sessionId
        }.getOrDefault(false)
        detachLocalSession(ownerId, sessionId)
        // 既にCC-System側で終了済み、または別プラグインのセッションへ置き換わった
        // 場合は、ownerIdだけを根拠に別セッションを閉じてはいけません。
        if (ownsCurrentServiceSession) {
            api.close(ownerId, com.awabi2048.ccsystem.api.gesturegui.GestureGuiCloseMode.IMMEDIATE)
        }
    }

    fun open(player: Player) {
        // 同一プレイヤーの再オープンは以前の入力セッションを必ず置き換えます。
        invalidateInput()
        api.registerOwner(player.uniqueId)
        val upper = buildUpperViewport(player)
        val lower = lowerPanel.build(state, player)
        // 主要画面は3画面縦配置の上・中スロットへ置きます。下スロットを
        // ダミーviewで埋めないことで、不要な背景だけが下へ表示されることを防ぎます。
        val layout = GestureGuiScreenLayout.VERTICAL
        val snapshot = api.open(
            player,
            listOf(upper, lower),
            GestureGuiOpenOptions(
                anchor = state.anchor,
                sessionListener = GestureGuiSessionListener { ownerId, sessionId ->
                    onGestureSessionClosed(ownerId, sessionId)
                },
                layout = layout,
                verticalSlots = listOf(GestureGuiVerticalSlot.TOP, GestureGuiVerticalSlot.MIDDLE),
            ),
        )
        gestureSessionId = snapshot.sessionId
    }

    fun updateUpper(player: Player) {
        api.updateScreen(player.uniqueId, buildUpperViewport(player))
    }

    fun updateLower(player: Player) {
        val childOpen = settingChildOpen(player.uniqueId)
        val view = if (state.lowerMode == GestureLowerMode.SETTING_CHOICES && childOpen) {
            lowerPanel.buildSettingChild(state, player)
        } else {
            lowerPanel.build(state, player)
        }
        api.updateScreen(player.uniqueId, view)
    }

    fun openConfirmChild(player: Player) {
        if (api.snapshot(player.uniqueId)?.childScreenIds?.contains(lowerPanel.CONFIRM_SCREEN_ID) == true) return
        val settingChildWasOpen = settingChildOpen(player.uniqueId)
        state.lowerMode = GestureLowerMode.CONFIRM
        val view = lowerPanel.build(state, player)
        val opened = api.openChild(
            player.uniqueId,
            view,
            GestureGuiChildOptions(
                // 個別設定中の確認は、その子画面のさらに前面に置きます。
                // 親IDを固定すると子画面を飛び越えて重なり、キャンセル後に
                // どの表示を復元すべきか失われるためです。
                parentScreenId = if (settingChildWasOpen) lowerPanel.SETTING_CHILD_SCREEN_ID else lowerPanel.LOWER_SCREEN_ID,
                overlayMaterial = Material.RED_STAINED_GLASS,
                animated = false,
            ),
        )
        if (!opened) {
            state.confirmKind = GestureConfirmKind.DELETE
            state.confirmNodeId = null
            state.pendingItemContext = null
            state.pendingItemKey = null
            state.pendingItemData = null
            state.lowerMode = if (settingChildWasOpen) GestureLowerMode.SETTING_CHOICES else GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    private fun settingChildOpen(ownerId: UUID): Boolean =
        api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.SETTING_CHILD_SCREEN_ID) == true

    /** 個別設定子画面を開き、既に開いている場合は差分更新だけを行います。 */
    private fun ensureSettingChild(player: Player) {
        state.lowerMode = GestureLowerMode.SETTING_CHOICES
        if (settingChildOpen(player.uniqueId)) {
            updateLower(player)
            return
        }
        val opened = runCatching {
            api.openChild(
                player.uniqueId,
                lowerPanel.buildSettingChild(state, player),
                GestureGuiChildOptions(
                    parentScreenId = lowerPanel.LOWER_SCREEN_ID,
                    overlayMaterial = Material.GRAY_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse { failure ->
            // 子画面が開かない原因（screenId重複・深度上限など）を現場で可視化します。
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "個別設定子画面のオープンに失敗しました: script=${state.scriptId} screenId=${lowerPanel.SETTING_CHILD_SCREEN_ID}",
                failure,
            )
            false
        }
        if (!opened) {
            // セッションが終了している／子深度上限に達している等の場合は、
            // 孤立した設定状態を残さず通常の設定画面へ戻します。
            clearSettingState()
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    /** 個別設定子画面を閉じて親の設定一覧へ戻します。 */
    private fun closeSettingChild(player: Player) {
        if (settingChildOpen(player.uniqueId)) {
            api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
        }
        // 明示的な戻る操作はpopSettingFrameを通ります。ここはノード選択や
        // 画面切替による中断専用とし、途中の設定木を残して孤立させません。
        clearSettingState()
        state.lowerMode = GestureLowerMode.SETTINGS
        updateLower(player)
    }

    private fun invalidateInput() {
        activeInputToken = null
        activeInputDialogId = null
    }

    /**
     * Gestureセッション終了時のローカル状態を一箇所で解放します。
     * DialogはIDと表示所有者をCC-System側で照合し、別機能のDialogを閉じないようにします。
     * Facade通知はgestureSessionIdをまだ保持した状態で行い、旧通知が新エディターを
     * 消さないようFacade側でインスタンスとセッションIDを照合できるようにします。
     */
    private fun detachLocalSession(ownerId: UUID, sessionId: UUID?) {
        if (sessionId != null && gestureSessionId != sessionId) return
        val player = Bukkit.getPlayer(ownerId)
        val dialogId = activeInputDialogId
        invalidateInput()
        if (dialogId != null && player != null && sessionId != null) {
            runCatching {
                api.closeExternalDialogIfCurrent(
                    ownerId,
                    sessionId,
                    player,
                    DIALOG_OWNER,
                    dialogId,
                )
            }.onFailure { failure ->
                // Dialog終了の失敗でGestureセッションのローカル解放を止めないよう、
                // 失敗は記録して下のセッション終了処理を継続します。
                plugin.logger.warning("Kantan Commanderの入力Dialog終了に失敗しました: ${failure.message}")
            }
        }
        if (sessionId != null) {
            runCatching { onSessionClosed(this, ownerId, sessionId) }
                .onFailure { failure ->
                    // Facade側の通知失敗でエディター自身のセッションIDを残すと、
                    // 後続の入力を現行セッションと誤認するため、通知例外を隔離します。
                    plugin.logger.warning("Kantan Commanderのセッション終了通知に失敗しました: ${failure.message}")
                }
        }
        gestureSessionId = null
    }

    private fun buildUpperViewport(player: Player): GestureGuiView {
        val script = plugin.scripts.load(state.scriptId) ?: return emptyView()
        val layout = runCatching { GraphLayoutEngine.layout(script.graph) }
            .getOrElse { return layoutErrorView(player) }
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
        // 実行前検証を一度だけ行い、ノード単位の未設定状態を背景色とホバーへ
        // 投影します。グラフ全体の検証を各セルで繰り返さないことで、ノード数が
        // 増えたときにも描画コストを線形に保ちます。
        val validationErrors = ExecutableScriptValidator.validate(script, plugin.graphLimits())
        val incompleteNodeIds = script.graph.nodes.keys.filterTo(mutableSetOf()) { nodeId ->
            validationErrors.any { error ->
                error.substringBefore(':').split('/').lastOrNull() == nodeId.toString()
            }
        }
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
                        val incomplete = node.id in incompleteNodeIds
                        val hasContextOverride = node.contextOverride?.let { context ->
                            context.executor != null || context.target != null ||
                                context.position != null || context.facing != null
                        } == true
                        val backgroundMaterial = when {
                            incomplete -> Material.ORANGE_CONCRETE
                            hasContextOverride -> Material.CYAN_CONCRETE
                            else -> Material.LIGHT_GRAY_CONCRETE
                        }
                        val statusLine = when {
                            incomplete -> Component.text(
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_CONTEXT_INCOMPLETE),
                                NamedTextColor.RED,
                            )
                            hasContextOverride -> Component.text(
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_CONTEXT_STATUS),
                                NamedTextColor.AQUA,
                            )
                            else -> null
                        }
                        val hoverText = Component.text(KcI18n.text(player, node.type.key)).let { firstLine ->
                            statusLine?.let { firstLine.append(Component.newline()).append(it) } ?: firstLine
                        }
                        // アイコン単体を浮かせず、追加ポイントと同じマス背景で視認性と接続先を示します。
                        visuals.add(GestureGuiVisual.Block(
                            visualId = "node-bg-${node.id}",
                            x = cx, y = cy,
                            width = metrics.iconSize,
                            height = metrics.iconSize,
                            blockData = Bukkit.createBlockData(backgroundMaterial),
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
                                text = hoverText,
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
                            text = net.kyori.adventure.text.Component.text(
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ACTION_CLICK_ADD),
                            ),
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
                                text = net.kyori.adventure.text.Component.text(
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ACTION_CLICK_INSERT),
                                ),
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

        addZoomControls(player, visuals, elements)
        addCloseButton(player, visuals, elements)

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
        // 表示と入力の境界は同じグラフ矩形から生成します。従来は表示だけを
        // filterしていたため、画面外へ消えたアイコンのInteractionがナビ列を
        // 横取りしていました。Blockは矩形を実際に切り詰め、Item/Textは
        // 完全に収まる場合だけ残すことで、見えていない要素を操作不能にします。
        val clippedVisuals = scaledVisuals.mapNotNull { visual ->
            if (!isMapVisual(visual)) visual else clipMapVisual(visual, metrics)
        }
        val clippedElements = scaledElements.mapNotNull { element ->
            if (!isMapElement(element)) element else clipMapElement(element, metrics)
        }
        val visibleVisualIds = clippedVisuals.mapTo(hashSetOf(), GestureGuiVisual::visualId)
        val finalElements = clippedElements.filter {
            it.targetVisualId == null || it.targetVisualId in visibleVisualIds
        }

        return GestureGuiView(
            GestureGuiScreenDefinition(UPPER_SCREEN_ID, finalElements, access = GestureGuiAccess.OWNER_ONLY),
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

    private fun clearSettingState() {
        state.settingContext = null
        state.settingFieldKey = null
        state.settingTreePath = null
        state.settingRoute = emptyList()
        state.settingScreen = null
        state.settingPage = 0
    }

    /** 設定木の現在フレームを既存表示状態へ投影します。 */
    private fun activateSettingFrame(
        frame: GestureSettingFrame,
        nodeIds: List<String> = state.settingTreePath?.nodeIds.orEmpty(),
    ) {
        state.settingContext = frame.context
        state.settingFieldKey = frame.fieldKey
        state.settingTreePath = GestureSettingTreePath(frame.fieldKey, frame.context.role, nodeIds)
        state.settingScreen = frame.screen
        state.settingPage = 0
    }

    /** タブから設定木のルートを開始します。 */
    private fun startSettingRoute(frame: GestureSettingFrame) {
        state.settingRoute = listOf(frame)
        activateSettingFrame(frame, emptyList())
    }

    /** 現在ノードの子フレームへ進みます。物理子画面は必要なときだけ開きます。 */
    private fun pushSettingFrame(
        player: Player,
        frame: GestureSettingFrame,
        selectedNodeId: String,
    ) {
        val nextPath = state.settingTreePath?.enterChild(selectedNodeId)?.nodeIds.orEmpty()
        state.settingRoute = state.settingRoute + frame
        activateSettingFrame(frame, nextPath)
        state.lowerMode = GestureLowerMode.SETTING_CHOICES
        ensureSettingChild(player)
    }

    /** 直下ノードの選択を経路へ記録し、同じ項目の再クリックを判定可能にします。 */
    private fun rememberSettingNode(nodeId: String) {
        val path = state.settingTreePath ?: return
        state.settingTreePath = path.selectAtDepth(state.settingRoute.size - 1, nodeId)
    }

    /** 設定木を一段戻します。ルートへ戻る時だけ物理子画面を閉じます。 */
    private fun popSettingFrame(player: Player): Boolean {
        if (state.settingRoute.size <= 1) {
            closeSettingChild(player)
            return false
        }
        state.settingRoute = state.settingRoute.dropLast(1)
        val frame = state.settingRoute.last()
        val path = state.settingTreePath?.leaveChild()?.nodeIds.orEmpty()
        activateSettingFrame(frame, path)
        if (state.settingRoute.size == 1) {
            if (settingChildOpen(player.uniqueId)) {
                api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
            }
            state.lowerMode = GestureLowerMode.SETTINGS
        } else {
            state.lowerMode = GestureLowerMode.SETTING_CHOICES
        }
        updateLower(player)
        return true
    }

    /**
     * 下部の設定タブから直接編集経路へ入ります。
     *
     * 以前はタブを選ぶだけで一覧を更新し、さらに「選択して編集」を押す必要が
     * ありました。タブ自体を編集入口にすることで、インベントリGUIと同じ意味上の
     * 設定画面へ一度の操作で遷移させます。
     */
    private fun openSettingsTab(player: Player, absoluteIndex: Int) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
        val fields = CommandSettingsModel.visibleFields(node)
        if (absoluteIndex !in fields.indices) return

        invalidateInput()
        state.settingsTab = absoluteIndex
        state.settingsPage = absoluteIndex / SETTINGS_PAGE_SIZE
        // タブ切替時は親画面を先に更新し、選択中の項目・現在値を常に残します。
        // 木の直下の選択肢も親画面へ表示し、二段階目が必要なときだけ子画面へ進みます。
        if (settingChildOpen(player.uniqueId)) {
            api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
        }
        val field = fields[absoluteIndex]
        // アイテムタブの選択は表示だけを切り替えます。タブを開いただけで
        // 設定済みアイテムを上書きしないよう、保存操作は右ペインの
        // 「メインハンドから設定」ボタンへ限定します。
        if (field.key == "item" && node.type in setOf(CommandType.GIVE_ITEM, CommandType.EQUIP_ITEM)) {
            clearSettingState()
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
            return
        }
        val descriptor = CommandSettingsModel.descriptor(node, field.key)
        val screen = gestureSettingScreenFor(descriptor.editor)
        if (screen == null) {
            if (settingChildOpen(player.uniqueId)) {
                api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
            }
            clearSettingState()
            state.lowerMode = GestureLowerMode.SETTINGS
        } else {
            startSettingRoute(
                GestureSettingFrame(
                    CommandSettingContext(state.scriptId, node.id, descriptor.role),
                    field.key,
                    screen,
                ),
            )
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
            return
        }
        updateLower(player)
    }

    /** SETTINGSの編集ボタンから、ダイアログ入力または専用選択へ遷移します。 */
    private fun beginSelectedFieldEdit(player: Player, fieldKey: String) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
        val descriptor = CommandSettingsModel.descriptor(node, fieldKey)
        val context = CommandSettingContext(state.scriptId, node.id, descriptor.role)
        if (fieldKey == "item" && node.type in setOf(CommandType.GIVE_ITEM, CommandType.EQUIP_ITEM)) {
            applyHeldItem(player, context)
            return
        }
        if (fieldKey == "stay" && node.type == CommandType.DISPLAY_TEXT) {
            // 表示時間はfadeIn/stay/fadeOutを一組として編集し、インベントリGUIと
            // 同じ入力欄・最大長・0以上検証を使います。
            showDisplayTimingSettingDialog(player, context, node)
            return
        }
        val screen = gestureSettingScreenFor(descriptor.editor)
        if (screen == null) {
            // 構造化モデルで専用画面を持たない項目は、チャットを横取りせず
            // CC-System共通のダイアログで入力します。
            // インベントリGUIのshowFieldDialogと同一の maxLength・検証を使います。
            val valueSource = if (fieldKey in setOf("startValue", "endValue", "stepValue")) {
                node.string(fieldKey.removeSuffix("Value") + "Source", "FIXED")
            } else null
            val spec = CommandDialogSpecs.field(fieldKey, valueSource)
                ?: CommandDialogSpecs.Spec(
                    com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
                    512,
                )
            showTextInputDialog(player, spec, node.string(fieldKey)) { raw ->
                // SETTINGS 経由の入力と同じく、前後空白を正規化してから検証・保存します。
                // 同一フィールドを上段・下段のどちらから編集しても結果が変わらないようにします。
                val value = raw.trim()
                val validationError = value.takeIf(String::isNotEmpty)?.let(spec.validate)
                if (validationError != null) return@showTextInputDialog KcI18n.text(player, validationError)
                val updated = CommandSettingsModel.updateNode(plugin, context) { it.params[fieldKey] = value }
                if (updated == null) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else {
                    updateUpper(player)
                    updateLower(player)
                    null
                }
            }
            return
        }
        startSettingRoute(GestureSettingFrame(context, fieldKey, screen))
        state.lowerMode = GestureLowerMode.SETTINGS
        updateLower(player)
    }

    /** メインハンドの実アイテムを設定値とスナップショットへ保存します。 */
    private fun applyHeldItem(
        player: Player,
        context: CommandSettingContext,
        parameter: String = "item",
    ): Boolean {
        val held = player.inventory.itemInMainHand.takeUnless { it.type == Material.AIR }
        if (held == null) {
            // 呼び出し元が専用選択画面から設定タブへ戻した直後でも、
            // 画面上の表示を状態と同期させ、古い候補画面を残しません。
            // 未所持時は要求どおり効果音・チャット通知を含めて何もしません。
            updateLower(player)
            return false
        }
        val itemKey = held.type.key.toString()
        val itemData = ItemStackCodec.encode(held)
        val node = plugin.scripts.load(context.scriptId)?.graph?.nodes?.get(context.nodeId) ?: return false
        val hasExistingItem = node.string(parameter).isNotBlank() || node.string("itemData").isNotBlank()
        if (parameter == "item" && hasExistingItem) {
            openItemOverwriteConfirm(player, context, itemKey, itemData)
            return false
        }
        return saveHeldItem(player, context, parameter, itemKey, itemData)
    }

    private fun saveHeldItem(
        player: Player,
        context: CommandSettingContext,
        parameter: String,
        itemKey: String,
        itemData: String,
    ): Boolean {
        val updated = updateSettingNode(player, context) { node ->
            node.params[parameter] = itemKey
            // アイテム名だけでなく、数量・Name/Lore・エンチャント・データ
            // コンポーネントを含むシリアライズ結果を保存します。
            if (parameter == "item") node.params["itemData"] = itemData
        }
        if (updated) player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_ITEM_SET, mapOf("item" to itemKey)))
        return updated
    }

    private fun configuredItem(node: me.awabi2048.kantancommander.model.CommandNode): ItemStack? =
        (ItemStackCodec.decode(node.string("itemData"))
            ?: Material.matchMaterial(node.string("item"))?.let(::ItemStack))
            ?.takeUnless { it.type == Material.AIR }

    private fun openItemOverwriteConfirm(
        player: Player,
        context: CommandSettingContext,
        itemKey: String,
        itemData: String,
    ) {
        if (api.snapshot(player.uniqueId)?.childScreenIds?.contains(lowerPanel.CONFIRM_SCREEN_ID) == true) return
        val parentId = if (settingChildOpen(player.uniqueId)) lowerPanel.SETTING_CHILD_SCREEN_ID else lowerPanel.LOWER_SCREEN_ID
        state.confirmKind = GestureConfirmKind.ITEM_OVERWRITE
        state.confirmNodeId = null
        state.pendingItemContext = context
        state.pendingItemKey = itemKey
        state.pendingItemData = itemData
        state.lowerMode = GestureLowerMode.CONFIRM
        val opened = api.openChild(
            player.uniqueId,
            lowerPanel.build(state, player),
            GestureGuiChildOptions(
                parentScreenId = parentId,
                overlayMaterial = Material.RED_STAINED_GLASS,
                animated = false,
            ),
        )
        if (!opened) {
            state.confirmKind = GestureConfirmKind.DELETE
            state.pendingItemContext = null
            state.pendingItemKey = null
            state.pendingItemData = null
            state.lowerMode = if (parentId == lowerPanel.SETTING_CHILD_SCREEN_ID) GestureLowerMode.SETTING_CHOICES else GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    private fun confirmItemOverwrite(player: Player) {
        val context = state.pendingItemContext ?: return
        val itemKey = state.pendingItemKey ?: return
        val itemData = state.pendingItemData ?: return
        val saved = runCatching {
            CommandSettingsModel.updateNode(plugin, context) { node ->
                node.params["item"] = itemKey
                node.params["itemData"] = itemData
            } != null
        }.onFailure { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "アイテム上書きの保存に失敗しました: script=${context.scriptId} node=${context.nodeId}",
                failure,
            )
        }.getOrDefault(false)
        if (!saved) {
            // 確認子画面を閉じず、再試行できるよう保留中のItemStackを維持します。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_ITEM_SAVE_RETRY))
            return
        }
        state.pendingItemContext = null
        state.pendingItemKey = null
        state.pendingItemData = null
        state.confirmKind = GestureConfirmKind.DELETE
        api.closeChild(player.uniqueId, lowerPanel.CONFIRM_SCREEN_ID)
        player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_ITEM_OVERWRITTEN, mapOf("item" to itemKey)))
        state.lowerMode = if (settingChildOpen(player.uniqueId) && state.settingContext != null) {
            GestureLowerMode.SETTING_CHOICES
        } else {
            clearSettingState()
            GestureLowerMode.SETTINGS
        }
        updateUpper(player)
        updateLower(player)
    }

    private fun updateSettingNode(
        player: Player,
        context: CommandSettingContext,
        change: (me.awabi2048.kantancommander.model.CommandNode) -> Unit,
    ): Boolean {
        val saved = runCatching {
            CommandSettingsModel.updateNode(plugin, context, change) != null
        }.onFailure { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "ジェスチャー設定の保存に失敗しました: script=${context.scriptId} node=${context.nodeId}",
                failure,
            )
        }.getOrDefault(false)
        if (!saved) return false
        updateUpper(player)
        updateLower(player)
        return true
    }

    /**
     * 共通入力仕様（CommandDialogSpecs）に沿った単一テキスト入力です。
     * プロンプト・maxLength・検証をインベントリGUIと同一に保ちます。
     */
    private fun beginSettingInput(
        player: Player,
        spec: CommandDialogSpecs.Spec,
        initial: String = "",
        result: (String) -> String?,
    ) {
        showTextInputDialog(player, spec, initial) { raw ->
            val value = raw.trim()
            val validationError = value.takeIf(String::isNotEmpty)?.let(spec.validate)
            if (validationError != null) return@showTextInputDialog KcI18n.text(player, validationError)
            val error = result(value)
            if (error != null) return@showTextInputDialog error
            updateUpper(player)
            updateLower(player)
            null
        }
    }

    /** 単一文字列の入力を共通ダイアログへ委譲します。 */
    private fun showTextInputDialog(
        player: Player,
        spec: CommandDialogSpecs.Spec,
        initial: String = "",
        onSubmit: (String) -> String?,
    ) = showInputDialog(
        player = player,
        body = CommandDialogSpecs.body(player, spec, initial),
        inputs = listOf(CommandDialogSpecs.input(player, "value", initial, spec)),
    ) { response -> onSubmit(response.textValue("value")) }

    /** DISPLAY_TEXTの3つの時間設定を、インベントリGUIと同じ仕様で編集します。 */
    private fun showDisplayTimingSettingDialog(
        player: Player,
        context: CommandSettingContext,
        node: me.awabi2048.kantancommander.model.CommandNode,
    ) {
        val fadeIn = node.string("fadeIn", "10")
        val stay = node.string("stay", "60")
        val fadeOut = node.string("fadeOut", "10")
        val durationSpec = requireNotNull(CommandDialogSpecs.field("stay"))
        showInputDialog(
            player = player,
            title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TITLE),
            body = CommandDialogSpecs.durationBody(player, fadeIn, stay, fadeOut),
            inputs = CommandDialogSpecs.durationInputs(player, fadeIn, stay, fadeOut),
        ) { response ->
            val rawValues = listOf("fadeIn", "stay", "fadeOut").associateWith { key ->
                response.textValue(key).trim()
            }
            val validationError = rawValues.values
                .mapNotNull { durationSpec.validate(it) }
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val values = rawValues.mapValues { (_, value) -> requireNotNull(value.toIntOrNull()) }
            if (!updateSettingNode(player, context) { command ->
                    values.forEach { (key, value) -> command.params[key] = value.toString() }
                }
            ) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            }
            null
        }
    }

    /**
     * 複数値の設定は入力欄を分割します（座標X/Y/Z、yaw/pitchなど）。
     * 連結文字列を1欄で受けると、どの値が不正かをユーザーが特定できず、
     * Dialog再表示時にも入力値の対応が崩れるため、入力IDと値を一対一にします。
     */
    private fun showInputDialog(
        player: Player,
        body: List<Component>,
        inputs: List<MenuDialogInput>,
        title: Component = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE)),
        onSubmit: (MenuDialogResponse) -> String?,
    ) {
        invalidateInput()
        val token = UUID.randomUUID()
        val dialogId = "gesture-input-$token"
        activeInputToken = token
        activeInputDialogId = dialogId
        try {
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = DIALOG_OWNER,
                    id = dialogId,
                    title = title,
                    body = body,
                    inputs = inputs,
                    confirm = MenuDialogButton(
                        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                        MenuDialogHandler { target, response ->
                            val snapshot = api.snapshot(target.uniqueId)
                            if (
                                target.uniqueId != player.uniqueId ||
                                activeInputToken != token ||
                                activeInputDialogId != dialogId ||
                                !target.isOnline ||
                                gestureSessionId == null ||
                                snapshot?.sessionId != gestureSessionId
                            ) {
                                return@MenuDialogHandler MenuActionResult.Ignored
                            }
                            val error = runCatching { onSubmit(response) }
                                .getOrElse { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT) }
                            if (error != null) {
                                // RejectedはCC-System側で同じダイアログを入力値付きで
                                // 再表示するため、入力セッションを維持したまま修正できます。
                                return@MenuDialogHandler MenuActionResult.Rejected(
                                    Component.text(error, NamedTextColor.RED),
                                )
                            }
                            invalidateInput()
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                    cancel = MenuDialogButton(
                        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DELETE_CONFIRM_CANCEL),
                        MenuDialogHandler { target, _ ->
                            if (
                                target.uniqueId != player.uniqueId ||
                                activeInputToken != token ||
                                activeInputDialogId != dialogId
                            ) {
                                return@MenuDialogHandler MenuActionResult.Ignored
                            }
                            invalidateInput()
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                ),
            )
        } catch (failure: Throwable) {
            // show()はPaperのDialog生成失敗を例外で返すため、表示されていないDialogを
            // 後続のclose処理が所有中と誤認しないよう、同じ世代だけをロールバックします。
            if (activeInputToken == token && activeInputDialogId == dialogId) invalidateInput()
            throw failure
        }
    }

    /**
     * 座標設定用の入力欄をX/Y/Zへ分割します。
     *
     * 座標を1つの文字列として受け取る方式では、区切り文字の誤りや一部の
     * 値だけの入力を画面上で特定できません。Dialogの各入力値をそのまま
     * 検証することで、エラー時も入力済みの値を保持したまま再表示できます。
     */
    private fun showCoordinateSettingDialog(
        player: Player,
        x: Double,
        y: Double,
        z: Double,
        onSubmit: (Double, Double, Double) -> String?,
    ) {
        showInputDialog(
            player = player,
            body = listOf(
                Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_COORDINATE_PROMPT)),
                Component.text(
                    KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_COORDINATE_CURRENT,
                        mapOf("x" to x.toString(), "y" to y.toString(), "z" to z.toString()),
                    ),
                    NamedTextColor.GRAY,
                ),
            ),
            inputs = listOf(
                MenuDialogInput.Text("x", Component.text("X"), x.toString(), maxLength = 64),
                MenuDialogInput.Text("y", Component.text("Y"), y.toString(), maxLength = 64),
                MenuDialogInput.Text("z", Component.text("Z"), z.toString(), maxLength = 64),
            ),
        ) { response ->
            val xValue = response.textValue("x").trim().toDoubleOrNull()?.takeIf(Double::isFinite)
            val yValue = response.textValue("y").trim().toDoubleOrNull()?.takeIf(Double::isFinite)
            val zValue = response.textValue("z").trim().toDoubleOrNull()?.takeIf(Double::isFinite)
            if (xValue == null || yValue == null || zValue == null) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_COORDINATE_INVALID)
            }
            onSubmit(xValue, yValue, zValue)
        }
    }

    /** 回転設定用の入力欄をyaw/pitchへ分割します。 */
    private fun showRotationSettingDialog(
        player: Player,
        yaw: Float,
        pitch: Float,
        onSubmit: (Float, Float) -> String?,
    ) {
        showInputDialog(
            player = player,
            body = listOf(
                Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_ROTATION_PROMPT)),
                Component.text(
                    KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_ROTATION_CURRENT,
                        mapOf("yaw" to yaw.toString(), "pitch" to pitch.toString()),
                    ),
                    NamedTextColor.GRAY,
                ),
            ),
            inputs = listOf(
                MenuDialogInput.Text("yaw", Component.text("Yaw"), yaw.toString(), maxLength = 64),
                MenuDialogInput.Text("pitch", Component.text("Pitch"), pitch.toString(), maxLength = 64),
            ),
        ) { response ->
            val yawValue = response.textValue("yaw").trim().toFloatOrNull()?.takeIf(Float::isFinite)
            val pitchValue = response.textValue("pitch").trim().toFloatOrNull()?.takeIf(Float::isFinite)
            if (yawValue == null || pitchValue == null) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_ROTATION_INVALID)
            }
            onSubmit(yawValue, pitchValue)
        }
    }

    private fun parseCoordinates(raw: String): Triple<Double, Double, Double>? {
        val tokens = raw.trim().split(Regex("[ ,]+"))
            .filter(String::isNotEmpty)
        if (tokens.size != 3) return null
        val values = tokens.map { it.toDoubleOrNull()?.takeIf(Double::isFinite) }
        if (values.any { it == null }) return null
        return Triple(values[0]!!, values[1]!!, values[2]!!)
    }

    /** 条件詳細の子設定IDを保存先のparamsキーへ変換します。 */
    private fun specSaveKey(encoded: String): String = when (encoded) {
        "condition-variable" -> "variable"
        "condition-value" -> "value"
        "condition-block" -> "block"
        "condition-count" -> "count"
        else -> encoded
    }

    /** 専用選択画面のすべての選択を共有モデルへ適用します。 */
    private fun handleSettingAction(context: GestureGuiActionContext, player: Player) {
        if (context.gesture != GestureGuiGesture.PRIMARY) return
        if (context.elementId == "lower-setting-back") {
            popSettingFrame(player)
            return
        }
        if (context.elementId.startsWith("lower-setting-page:")) {
            state.settingPage = context.elementId.removePrefix("lower-setting-page:").toIntOrNull() ?: return
            updateLower(player)
            return
        }
        if (context.elementId == "lower-context") {
            val nodeId = state.selectedNodeId ?: return
            if (state.settingScreen == GestureSettingScreen.CONTEXT_OVERRIDE) {
                clearSettingState()
                state.lowerMode = GestureLowerMode.SETTINGS
                updateLower(player)
                return
            }
            startSettingRoute(
                GestureSettingFrame(
                    CommandSettingContext(state.scriptId, nodeId, null),
                    "context",
                    GestureSettingScreen.CONTEXT_OVERRIDE,
                ),
            )
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
            return
        }
        val encoded = context.elementId.removePrefix("lower-setting-choice:")
        if (encoded == context.elementId) return
        val separator = encoded.indexOf(':')
        val group = if (separator < 0) encoded else encoded.substring(0, separator)
        val value = if (separator < 0) "" else encoded.substring(separator + 1)
        val settingContext = state.settingContext ?: return
        val screen = state.settingScreen ?: return
        val fieldKey = state.settingFieldKey ?: return
        val script = plugin.scripts.load(settingContext.scriptId) ?: return
        val node = script.graph.nodes[settingContext.nodeId] ?: return

        fun showSettingScreen(openChild: Boolean = false) {
            state.lowerMode = if (settingChildOpen(player.uniqueId) || state.settingRoute.size > 1) {
                GestureLowerMode.SETTING_CHOICES
            } else {
                GestureLowerMode.SETTINGS
            }
            if (openChild && !settingChildOpen(player.uniqueId)) ensureSettingChild(player) else updateLower(player)
        }

        when (screen) {
            GestureSettingScreen.TARGET -> {
                if (group != "target") return
                val kind = runCatching { TargetKind.valueOf(value) }.getOrNull() ?: return
                val wasSelected = lowerPanel.isSettingChoiceSelected(state, encoded)
                val hasChildren = lowerPanel.hasSettingChoiceChildren(state, player, encoded)
                val fixedId = if (kind == TargetKind.FIXED_ENTITY) {
                    player.getTargetEntity(32)?.uniqueId ?: run {
                        player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_NO_ENTITY_IN_SIGHT))
                        return
                    }
                } else null
                val role = settingContext.role
                val current = CommandSettingsModel.targetSpec(node, role)
                    ?: TargetSpec(kind)
                val entityKind = kind in setOf(TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES)
                if (!updateSettingNode(player, settingContext.copy(role = role)) { target ->
                        CommandSettingsModel.setTargetSpec(
                            target,
                            role,
                            current.copy(
                                kind = kind,
                                fixedEntityId = fixedId,
                                entityType = if (entityKind) current.entityType else null,
                            ),
                        )
                }) return
                rememberSettingNode(encoded)
                when (settingSelectionAction(wasSelected, hasChildren)) {
                    GestureSettingSelectionAction.ENTER_CHILD -> {
                        pushSettingFrame(
                            player,
                            GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET_FILTERS),
                            encoded,
                        )
                    }
                    GestureSettingSelectionAction.STAY_ON_FRAME -> {
                        // 一回目の選択および葉の選択では現在の設定木を維持します。
                        // 兄弟項目を続けて選べる状態にし、戻る操作だけで親へ戻します。
                        showSettingScreen()
                    }
                }
            }
            GestureSettingScreen.TARGET_FILTERS -> {
                if (group != "filter") return
                val role = settingContext.role
                val current = CommandSettingsModel.targetSpec(node, role) ?: TargetSpec(TargetKind.NEAREST_ENTITY)
                when (value) {
                    "sort" -> updateSettingNode(player, settingContext) {
                        val next = TargetSort.entries[(current.sort.ordinal + 1) % TargetSort.entries.size]
                        CommandSettingsModel.setTargetSpec(it, role, current.copy(sort = next))
                    }
                    "gameMode" -> updateSettingNode(player, settingContext) {
                        val modes = listOf(null, "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")
                        val next = modes[(modes.indexOf(current.gameMode) + 1).coerceAtLeast(0) % modes.size]
                        CommandSettingsModel.setTargetSpec(it, role, current.copy(gameMode = next))
                    }
                    "entityType", "minimumDistance", "maximumDistance", "limit", "tag", "name" -> {
                        // インベントリGUIと同一の入力仕様（ラベル・maxLength・検証）を使います。
                        val spec = CommandDialogSpecs.targetFilter(value)
                            ?: return
                        val initial = when (value) {
                            "minimumDistance" -> current.minimumDistance?.toString()
                            "maximumDistance" -> current.maximumDistance?.toString()
                            "limit" -> current.limit?.toString()
                            "entityType" -> current.entityType
                            "tag" -> current.tag
                            else -> current.name
                        }.orEmpty()
                        beginSettingInput(player, spec, initial) { raw ->
                            val parsed = when (value) {
                                "minimumDistance", "maximumDistance" -> raw.takeIf(String::isNotEmpty)
                                    ?.toDoubleOrNull()?.takeIf(Double::isFinite)
                                "limit" -> raw.takeIf(String::isNotEmpty)?.toIntOrNull()
                                else -> raw.takeIf(String::isNotEmpty)
                            }
                            val updated = when (value) {
                                "entityType" -> current.copy(entityType = parsed as String?)
                                "minimumDistance" -> current.copy(minimumDistance = parsed as Double?)
                                "maximumDistance" -> current.copy(maximumDistance = parsed as Double?)
                                "limit" -> current.copy(limit = parsed as Int?)
                                "tag" -> current.copy(tag = parsed as String?)
                                else -> current.copy(name = parsed as String?)
                            }
                            if (updated.minimumDistance != null && updated.maximumDistance != null &&
                                updated.minimumDistance > updated.maximumDistance
                            ) {
                                return@beginSettingInput KcI18n.text(
                                    player,
                                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_MINIMUM_ABOVE_MAXIMUM,
                                )
                            }
                            if (!updateSettingNode(player, settingContext) {
                                CommandSettingsModel.setTargetSpec(it, role, updated)
                            }) return@beginSettingInput KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                            null
                        }
                    }
                }
            }
            GestureSettingScreen.POSITION -> {
                if (group != "position") return
                val kind = runCatching { PositionKind.valueOf(value) }.getOrNull() ?: return
                val wasSelected = lowerPanel.isSettingChoiceSelected(state, encoded)
                val hasChildren = lowerPanel.hasSettingChoiceChildren(state, player, encoded)
                rememberSettingNode(encoded)
                if (kind == PositionKind.TARGET && settingContext.role == CommandSettingRole.DESTINATION) {
                    if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setPositionSpec(it, settingContext.role, PositionSpec(kind))
                        }) return
                    when (settingSelectionAction(wasSelected, hasChildren)) {
                        GestureSettingSelectionAction.ENTER_CHILD -> {
                            pushSettingFrame(
                                player,
                                GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET),
                                encoded,
                            )
                        }
                        GestureSettingSelectionAction.STAY_ON_FRAME -> showSettingScreen()
                    }
                    return
                }
                if (kind == PositionKind.COORDINATES) {
                    val current = CommandSettingsModel.positionSpec(node, settingContext.role)
                    showCoordinateSettingDialog(
                        player,
                        current?.x ?: player.location.x,
                        current?.y ?: player.location.y,
                        current?.z ?: player.location.z,
                    ) { x, y, z ->
                        if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setPositionSpec(it, settingContext.role, PositionSpec(kind, x = x, y = y, z = z))
                        }) return@showCoordinateSettingDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        showSettingScreen()
                        null
                    }
                    return
                }
                if (kind in setOf(PositionKind.TEMPORARY_VARIABLE, PositionKind.WORLD_VARIABLE)) {
                    beginSettingInput(
                        player,
                        CommandDialogSpecs.variableName,
                        CommandSettingsModel.positionSpec(node, settingContext.role)?.variable.orEmpty(),
                    ) { raw ->
                        if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setPositionSpec(it, settingContext.role, PositionSpec(kind, variable = raw))
                        }) return@beginSettingInput KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        showSettingScreen()
                        null
                    }
                    return
                }
                val location = player.location
                val spec = if (kind == PositionKind.CAPTURED) {
                    PositionSpec(kind, location.x, location.y, location.z, location.yaw, location.pitch)
                } else PositionSpec(kind)
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setPositionSpec(it, settingContext.role, spec)
                    }) showSettingScreen()
            }
            GestureSettingScreen.FACING -> {
                if (group != "facing") return
                val kind = runCatching { FacingKind.valueOf(value) }.getOrNull() ?: return
                if (kind == FacingKind.COORDINATES) {
                    val current = CommandSettingsModel.facingSpec(node)
                    showCoordinateSettingDialog(
                        player,
                        current?.x ?: player.location.x,
                        current?.y ?: player.location.y,
                        current?.z ?: player.location.z,
                    ) { x, y, z ->
                        if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setFacingSpec(it, FacingSpec(kind, x = x, y = y, z = z))
                        }) return@showCoordinateSettingDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        showSettingScreen()
                        null
                    }
                    return
                }
                if (kind == FacingKind.ROTATION) {
                    val current = CommandSettingsModel.facingSpec(node)
                    showRotationSettingDialog(
                        player,
                        current?.yaw ?: player.location.yaw,
                        current?.pitch ?: player.location.pitch,
                    ) { yaw, pitch ->
                        if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setFacingSpec(it, FacingSpec(kind, yaw = yaw, pitch = pitch))
                        }) return@showRotationSettingDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        showSettingScreen()
                        null
                    }
                    return
                }
                val location = player.location
                val spec = if (kind == FacingKind.CAPTURED) FacingSpec(kind, yaw = location.yaw, pitch = location.pitch)
                else FacingSpec(kind)
                if (updateSettingNode(player, settingContext) { CommandSettingsModel.setFacingSpec(it, spec) }) {
                    showSettingScreen()
                }
            }
            GestureSettingScreen.CONDITION_KIND -> {
                if (group != "condition-kind") return
                val kind = runCatching { ConditionKind.valueOf(value) }.getOrNull() ?: return
                if (updateSettingNode(player, settingContext) { it.params["kind"] = kind.name }) showSettingScreen()
            }
            GestureSettingScreen.CONDITION_DETAIL -> {
                when (encoded) {
                    "condition-target" -> {
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, encoded)
                        val hasChildren = lowerPanel.hasSettingChoiceChildren(state, player, encoded)
                        rememberSettingNode(encoded)
                        when (settingSelectionAction(wasSelected, hasChildren)) {
                            GestureSettingSelectionAction.ENTER_CHILD -> {
                                pushSettingFrame(
                                    player,
                                    GestureSettingFrame(
                                        settingContext.copy(role = CommandSettingRole.NODE_TARGET),
                                        fieldKey,
                                        GestureSettingScreen.TARGET,
                                    ),
                                    encoded,
                                )
                            }
                            GestureSettingSelectionAction.STAY_ON_FRAME -> showSettingScreen()
                        }
                    }
                    "condition-position" -> {
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, encoded)
                        val hasChildren = lowerPanel.hasSettingChoiceChildren(state, player, encoded)
                        rememberSettingNode(encoded)
                        when (settingSelectionAction(wasSelected, hasChildren)) {
                            GestureSettingSelectionAction.ENTER_CHILD -> {
                                pushSettingFrame(
                                    player,
                                    GestureSettingFrame(
                                        settingContext.copy(role = CommandSettingRole.CONDITION_POSITION),
                                        fieldKey,
                                        GestureSettingScreen.POSITION,
                                    ),
                                    encoded,
                                )
                            }
                            GestureSettingSelectionAction.STAY_ON_FRAME -> showSettingScreen()
                        }
                    }
                    "condition-state" -> {
                        if (updateSettingNode(player, settingContext) {
                                it.params["state"] = if (it.string("state", "sneaking") == "sneaking") "on_ground" else "sneaking"
                            }) updateLower(player)
                    }
                    "condition-scope" -> {
                        if (updateSettingNode(player, settingContext) {
                                it.params["variableScope"] = if (it.string("variableScope", VariableScope.TEMPORARY.name) == VariableScope.WORLD.name) VariableScope.TEMPORARY.name else VariableScope.WORLD.name
                            }) updateLower(player)
                    }
                    "condition-operator" -> {
                        val operators = listOf("set", "unset", "==", "!=", ">", ">=", "<", "<=")
                        if (updateSettingNode(player, settingContext) {
                                val current = operators.indexOf(it.string("operator", "==")).coerceAtLeast(0)
                                it.params["operator"] = operators[(current + 1) % operators.size]
                            }) updateLower(player)
                    }
                    "condition-variable", "condition-value", "condition-block", "condition-item", "condition-count" -> {
                        if (encoded == "condition-item") {
                            applyHeldItem(player, settingContext)
                            updateLower(player)
                            return
                        }
                        // インベントリGUIと同一の入力仕様（ラベル・maxLength・検証）を使います。
                        val spec = when (encoded) {
                            "condition-variable" -> CommandDialogSpecs.variableName
                            "condition-value" -> CommandDialogSpecs.signedInteger
                            "condition-block" -> CommandDialogSpecs.block
                            "condition-count" -> CommandDialogSpecs.field("count")
                                ?: CommandDialogSpecs.Spec(
                                    com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT,
                                    10,
                                    { raw -> if ((raw.toIntOrNull() ?: 0) < 1) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID else null },
                                )
                            else -> return
                        }
                        val saveKey = specSaveKey(encoded)
                        beginSettingInput(player, spec, node.string(saveKey)) { raw ->
                            if (!updateSettingNode(player, settingContext) { it.params[saveKey] = raw }) {
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                            } else null
                        }
                    }
                }
            }
            GestureSettingScreen.DISPLAY_MODE -> {
                if (group != "display" || value !in setOf("tellraw", "title", "actionbar")) return
                if (updateSettingNode(player, settingContext) { it.params["mode"] = value }) showSettingScreen()
            }
            GestureSettingScreen.ENTITY_ACTION -> {
                if (group != "action" || value !in setOf("ride", "dismount")) return
                if (updateSettingNode(player, settingContext) { it.params["action"] = value }) showSettingScreen()
            }
            GestureSettingScreen.VARIABLE_SCOPE -> {
                if (group != "scope") return
                val scope = runCatching { VariableScope.valueOf(value) }.getOrNull() ?: return
                if (updateSettingNode(player, settingContext) { it.params["scope"] = scope.name }) showSettingScreen()
            }
            GestureSettingScreen.VARIABLE_TYPE -> {
                if (group != "type") return
                val type = runCatching { VariableType.valueOf(value) }.getOrNull() ?: return
                if (updateSettingNode(player, settingContext) {
                        it.params["type"] = type.name
                        val operation = runCatching { VariableOperation.valueOf(it.string("operation")) }.getOrNull()
                        if (operation !in CommandSettingsModel.allowedVariableOperations(type)) {
                            it.params["operation"] = CommandSettingsModel.allowedVariableOperations(type).first().name
                        }
                    }) showSettingScreen()
            }
            GestureSettingScreen.VARIABLE_OPERATION -> {
                if (group != "operation") return
                val operation = runCatching { VariableOperation.valueOf(value) }.getOrNull() ?: return
                val type = runCatching { VariableType.valueOf(node.string("type", VariableType.BOOLEAN.name)) }.getOrDefault(VariableType.BOOLEAN)
                if (operation !in CommandSettingsModel.allowedVariableOperations(type)) return
                if (updateSettingNode(player, settingContext) { it.params["operation"] = operation.name }) showSettingScreen()
            }
            GestureSettingScreen.VARIABLE_VALUE -> {
                if (group != "value") return
                when (value) {
                    "direct" -> beginSettingInput(
                        player,
                        CommandDialogSpecs.field("value") ?: return,
                        node.string("value"),
                    ) { raw ->
                        if (!updateSettingNode(player, settingContext) { it.params["value"] = raw }) {
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        } else null
                    }
                    "iteration" -> if (updateSettingNode(player, settingContext) { it.params["value"] = "\$current_iteration_value" }) showSettingScreen()
                    "count" -> if (updateSettingNode(player, settingContext) { it.params["value"] = "\$current_loop_count" }) showSettingScreen()
                }
            }
            GestureSettingScreen.FOR_SOURCE -> {
                if (group != "source" || value !in setOf("FIXED", "TEMPORARY", "WORLD")) return
                if (updateSettingNode(player, settingContext) { it.params[fieldKey] = value }) showSettingScreen()
            }
            GestureSettingScreen.INCLUSIVE_END -> {
                if (group != "inclusive") return
                if (updateSettingNode(player, settingContext) { it.params[fieldKey] = value.toBoolean().toString() }) showSettingScreen()
            }
            GestureSettingScreen.CONTEXT_OVERRIDE -> {
                when (value) {
                    "executor", "target" -> {
                        val role = if (value == "executor") CommandSettingRole.CONTEXT_EXECUTOR else CommandSettingRole.CONTEXT_TARGET
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, encoded)
                        val hasChildren = lowerPanel.hasSettingChoiceChildren(state, player, encoded)
                        rememberSettingNode(encoded)
                        when (settingSelectionAction(wasSelected, hasChildren)) {
                            GestureSettingSelectionAction.ENTER_CHILD -> {
                                pushSettingFrame(
                                    player,
                                    GestureSettingFrame(settingContext.copy(role = role), fieldKey, GestureSettingScreen.TARGET),
                                    encoded,
                                )
                            }
                            GestureSettingSelectionAction.STAY_ON_FRAME -> showSettingScreen()
                        }
                    }
                    "position" -> {
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, encoded)
                        val hasChildren = lowerPanel.hasSettingChoiceChildren(state, player, encoded)
                        rememberSettingNode(encoded)
                        when (settingSelectionAction(wasSelected, hasChildren)) {
                            GestureSettingSelectionAction.ENTER_CHILD -> {
                                pushSettingFrame(
                                    player,
                                    GestureSettingFrame(
                                        settingContext.copy(role = CommandSettingRole.CONTEXT_POSITION),
                                        fieldKey,
                                        GestureSettingScreen.POSITION,
                                    ),
                                    encoded,
                                )
                            }
                            GestureSettingSelectionAction.STAY_ON_FRAME -> showSettingScreen()
                        }
                    }
                    "facing" -> {
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, encoded)
                        val hasChildren = lowerPanel.hasSettingChoiceChildren(state, player, encoded)
                        rememberSettingNode(encoded)
                        when (settingSelectionAction(wasSelected, hasChildren)) {
                            GestureSettingSelectionAction.ENTER_CHILD -> {
                                pushSettingFrame(
                                    player,
                                    GestureSettingFrame(settingContext.copy(role = CommandSettingRole.CONTEXT_FACING), fieldKey, GestureSettingScreen.FACING),
                                    encoded,
                                )
                            }
                            GestureSettingSelectionAction.STAY_ON_FRAME -> showSettingScreen()
                        }
                    }
                    "source" -> {
                        if (updateSettingNode(player, settingContext) { CommandSettingsModel.toggleContextSource(it) }) updateLower(player)
                    }
                    "inherit" -> if (updateSettingNode(player, settingContext) { it.contextOverride = null }) showSettingScreen()
                }
            }
        }
    }

    private fun handleUpperAction(context: GestureGuiActionContext) {
        val player = Bukkit.getPlayer(context.ownerId) ?: return
        // 画面操作が発生した時点で、古いダイアログ入力を無効化します。
        // close/open以外の遷移でも遅延コールバックが設定を書き換えないようにします。
        invalidateInput()
        when {
            context.elementId.startsWith("node:") -> {
                val nodeId = runCatching { UUID.fromString(context.elementId.removePrefix("node:")) }.getOrNull() ?: return
                // 画面更新後に削除されたノードからの遅延入力は、選択も効果音も発生させません。
                val script = plugin.scripts.load(state.scriptId) ?: return
                if (script.graph.nodes[nodeId] == null) return
                when (context.gesture) {
                    GestureGuiGesture.PRIMARY -> {
                        if (settingChildOpen(player.uniqueId)) {
                            api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                        }
                        state.selectedNodeId = nodeId
                        state.selectedAddPoint = null
                        state.selectedInsertionCandidatePoint = null
                        state.selectedInsertionPoint = null
                        state.pendingInsertion = null
                        clearSettingState()
                        state.lowerMode = GestureLowerMode.SETTINGS
                        state.settingsTab = 0
                        state.settingsPage = 0
                        updateUpper(player)
                        updateLower(player)
                        // ノード選択直後は先頭フィールドを親画面へ表示します。
                        // 詳細子画面は、木の項目を選択して再クリックしたときだけ開きます。
                        openSettingsTab(player, 0)
                    }
                    else -> Unit
                }
            }
            context.elementId == "viewport-empty" && context.gesture == GestureGuiGesture.PRIMARY -> {
                if (settingChildOpen(player.uniqueId)) {
                    api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                }
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                state.selectedInsertionPoint = null
                state.confirmNodeId = null
                state.pendingInsertion = null
                clearSettingState()
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
                val layout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrNull() ?: return
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
                val layout = script?.let { runCatching { GraphLayoutEngine.layout(it.graph) }.getOrNull() }
                    ?: return
                val firstAdd = GestureEditorLayout.findFirstAddPoint(layout.cells)
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
                if (settingChildOpen(player.uniqueId)) {
                    api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                }
                val gx = context.elementId.removePrefix("add:").substringBefore(":").toIntOrNull() ?: return
                val gy = context.elementId.removePrefix("add:").substringAfter(":").toIntOrNull() ?: return
                val layout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrNull() ?: return
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
                clearSettingState()
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
                if (settingChildOpen(player.uniqueId)) {
                    api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                }
                val point = context.elementId.removePrefix("path:").split(":").mapNotNull(String::toIntOrNull)
                if (point.size != 2) return
                val layout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrNull() ?: return
                val clickedPoint = MapPoint(point[0], point[1])
                val cell = layout.cells[clickedPoint] ?: return
                val target = cell.insertionTarget ?: return
                state.pendingInsertion = target
                state.selectedNodeId = null
                clearSettingState()
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
                val index = context.elementId.removePrefix("lower-tab:").toIntOrNull() ?: return
                openSettingsTab(player, index)
            }
            context.elementId.startsWith("lower-settings-page:") && context.gesture == GestureGuiGesture.PRIMARY -> {
                val page = context.elementId.removePrefix("lower-settings-page:").toIntOrNull() ?: return
                if (settingChildOpen(player.uniqueId)) {
                    api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                }
                state.settingsPage = page
                // 専用選択画面から設定ページャーを押した場合も、古い専用画面を
                // 残さず、対応するタブ一覧へ戻します。これがページング重複を防ぎます。
                clearSettingState()
                state.settingsTab = page * SETTINGS_PAGE_SIZE
                state.lowerMode = GestureLowerMode.SETTINGS
                updateLower(player)
            }
            (context.elementId == "lower-context" || context.elementId.startsWith("lower-setting-")) &&
                context.gesture == GestureGuiGesture.PRIMARY -> {
                handleSettingAction(context, player)
            }
            context.elementId.startsWith("lower-edit:") &&
                context.gesture in setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY) -> {
                val fieldKey = context.elementId.removePrefix("lower-edit:")
                beginSelectedFieldEdit(player, fieldKey)
            }
            context.elementId == "lower-item-get" && context.gesture == GestureGuiGesture.PRIMARY -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
                val item = configuredItem(node) ?: return
                player.inventory.addItem(item.clone()).values.forEach { overflow ->
                    player.world.dropItemNaturally(player.location, overflow)
                }
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_ITEM_TAKEN))
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
                    val currentTarget = runCatching { GraphLayoutEngine.layout(script.graph) }
                        .getOrNull()
                        ?.cells?.get(point)
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
                // コマンド追加の完了音は永続化が成功した後だけ再生します。
                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f)
                state.pendingInsertion = null
                clearSettingState()
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
                // 新規追加時も既存ノード選択時と同じ初期表示経路を通し、
                // 「タブだけ選択されて詳細が空」の状態を作りません。
                openSettingsTab(player, 0)
            }
            context.elementId == "lower-close-picker" && context.gesture == GestureGuiGesture.PRIMARY -> {
                if (settingChildOpen(player.uniqueId)) {
                    api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                }
                state.pendingInsertion = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                state.selectedInsertionPoint = null
                clearSettingState()
                state.lowerMode = GestureLowerMode.SETTINGS
                updateLower(player)
            }
            context.elementId == "nav-close" && context.gesture == GestureGuiGesture.PRIMARY -> {
                // 右上の閉じる操作は、親・子画面と入力claimをまとめて即時解放します。
                closeImmediately(player.uniqueId)
            }
            context.elementId == "lower-delete" && context.gesture == GestureGuiGesture.PRIMARY -> {
                state.confirmNodeId = state.selectedNodeId ?: return
                state.confirmKind = GestureConfirmKind.DELETE
                openConfirmChild(player)
            }
            context.elementId == "confirm-delete" && context.gesture == GestureGuiGesture.PRIMARY -> {
                if (state.confirmKind == GestureConfirmKind.ITEM_OVERWRITE) {
                    confirmItemOverwrite(player)
                    return
                }
                val nodeId = state.confirmNodeId ?: return
                val script = plugin.scripts.load(state.scriptId) ?: return
                // 確認後の削除も候補グラフへ適用し、分岐・合流のレイアウト検証を
                // 通過した内容だけを正本へ保存します。失敗時は選択状態を保持します。
                val candidateGraph = script.graph.deepCopy()
                if (!GraphEditor.delete(candidateGraph, nodeId)) return
                runCatching { plugin.scripts.save(script.copy(graph = candidateGraph)) }
                    .onFailure { failure ->
                        plugin.logger.log(
                            java.util.logging.Level.WARNING,
                            "ジェスチャーGUIからのコマンド削除を保存できませんでした: script=${script.id} node=$nodeId",
                            failure,
                        )
                    }
                    .getOrElse { return }
                // 削除確認を開いただけでは鳴らさず、保存成功後に削除音を再生します。
                player.playSound(player.location, Sound.BLOCK_BAMBOO_HIT, 1.0f, 1.0f)
                val settingChildWasOpen = settingChildOpen(player.uniqueId)
                state.confirmNodeId = null
                state.confirmKind = GestureConfirmKind.DELETE
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                state.selectedInsertionPoint = null
                clearSettingState()
                state.lowerMode = GestureLowerMode.SETTINGS
                api.closeChild(player.uniqueId, lowerPanel.CONFIRM_SCREEN_ID)
                if (settingChildWasOpen) api.closeChild(player.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "confirm-cancel" && context.gesture == GestureGuiGesture.PRIMARY -> {
                val settingChildWasOpen = settingChildOpen(player.uniqueId)
                state.confirmNodeId = null
                state.confirmKind = GestureConfirmKind.DELETE
                state.pendingItemContext = null
                state.pendingItemKey = null
                state.pendingItemData = null
                api.closeChild(player.uniqueId, lowerPanel.CONFIRM_SCREEN_ID)
                state.lowerMode = if (settingChildWasOpen && state.settingContext != null) {
                    GestureLowerMode.SETTING_CHOICES
                } else {
                    clearSettingState()
                    GestureLowerMode.SETTINGS
                }
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

    /** 不正な保存グラフでメニュー全体をクラッシュさせず、操作不能な警告面を返します。 */
    private fun layoutErrorView(player: Player): GestureGuiView {
        val visuals = listOf(
            GestureGuiVisual.Text(
                visualId = "viewport-error-title",
                x = 0.0,
                y = 0.10,
                text = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_UPPER_RENDER), NamedTextColor.RED),
                size = 0.008,
                lineWidth = 260,
            ),
            GestureGuiVisual.Text(
                visualId = "viewport-error-detail",
                x = 0.0,
                y = -0.02,
                text = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT), NamedTextColor.GRAY),
                size = 0.005,
                lineWidth = 260,
            ),
        )
        return GestureGuiView(
            GestureGuiScreenDefinition(UPPER_SCREEN_ID, emptyList(), access = GestureGuiAccess.OWNER_ONLY),
            visuals,
            panel = GestureGuiPanel(
                width = GestureEditorLayout.UPPER_W,
                height = GestureEditorLayout.UPPER_H,
                backgroundMaterial = Material.GRAY_CONCRETE,
                frameMaterial = Material.LIGHT_GRAY_CONCRETE,
                frameWidth = GestureEditorLayout.FRAME_WIDTH,
            ),
        ) {}
    }

    private fun isMapVisual(visual: GestureGuiVisual): Boolean =
        visual.visualId.startsWith("node-") ||
            visual.visualId.startsWith("add-") ||
            visual.visualId.startsWith("path-")

    private fun isMapElement(element: GestureGuiElement): Boolean =
        element.elementId.startsWith("node:") ||
            element.elementId.startsWith("add:") ||
            element.elementId.startsWith("path:")

    private fun clipMapVisual(visual: GestureGuiVisual, metrics: ViewportMetrics): GestureGuiVisual? {
        val halfWidth = when (visual) {
            is GestureGuiVisual.Block -> visual.width / 2.0
            is GestureGuiVisual.Item -> metrics.iconSize * metrics.zoomScale / 2.0
            is GestureGuiVisual.Text -> 0.06 * metrics.zoomScale
        }
        val halfHeight = when (visual) {
            is GestureGuiVisual.Block -> visual.height / 2.0
            is GestureGuiVisual.Item -> metrics.iconSize * metrics.zoomScale / 2.0
            is GestureGuiVisual.Text -> 0.04 * metrics.zoomScale
        }
        val left = maxOf(visual.x - halfWidth, metrics.graphMinX)
        val right = minOf(visual.x + halfWidth, metrics.graphMaxX)
        val bottom = maxOf(visual.y - halfHeight, metrics.graphMinY)
        val top = minOf(visual.y + halfHeight, metrics.graphMaxY)
        if (right - left <= 1.0e-6 || top - bottom <= 1.0e-6) return null
        return when (visual) {
            is GestureGuiVisual.Block -> visual.copy(
                x = (left + right) / 2.0,
                y = (bottom + top) / 2.0,
                width = right - left,
                height = top - bottom,
            )
            // ItemDisplay/TextDisplayは矩形クリップを持たないため、完全に
            // 内側へ収まる要素だけを残します。対応するInteractionも同時に削除します。
            is GestureGuiVisual.Item,
            is GestureGuiVisual.Text -> visual.takeIf {
                visual.x - halfWidth >= metrics.graphMinX &&
                    visual.x + halfWidth <= metrics.graphMaxX &&
                    visual.y - halfHeight >= metrics.graphMinY &&
                    visual.y + halfHeight <= metrics.graphMaxY
            }
        }
    }

    private fun clipMapElement(element: GestureGuiElement, metrics: ViewportMetrics): GestureGuiElement? {
        val minX = maxOf(element.bounds.minX, metrics.graphMinX)
        val maxX = minOf(element.bounds.maxX, metrics.graphMaxX)
        val minY = maxOf(element.bounds.minY, metrics.graphMinY)
        val maxY = minOf(element.bounds.maxY, metrics.graphMaxY)
        if (minX >= maxX || minY >= maxY) return null
        val originalHover = element.hoverText
        val hover = originalHover?.copy(
            x = originalHover.x.coerceIn(metrics.graphMinX, metrics.graphMaxX),
            y = originalHover.y.coerceIn(metrics.graphMinY, metrics.graphMaxY),
        )
        return element.copy(
            bounds = GestureGuiBounds(minX, minY, maxX, maxY),
            hoverText = hover,
        )
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
    private fun addZoomControls(player: Player, visuals: MutableList<GestureGuiVisual>, elements: MutableList<GestureGuiElement>) {
        listOf("nav-zoom-in" to "＋", "nav-zoom-out" to "−").forEachIndexed { index, (id, glyph) ->
            val x = GestureEditorLayout.ZOOM_X
            val y = GestureEditorLayout.ZOOM_TOP_Y + index * GestureEditorLayout.ZOOM_PITCH
            val enabled = if (id == "nav-zoom-in") {
                state.zoomLevel < GestureEditorLayout.MAX_ZOOM_LEVEL
            } else {
                state.zoomLevel > GestureEditorLayout.MIN_ZOOM_LEVEL
            }
            // 利用できない操作は灰色で常時表示し、操作可能かを視線で判別できるようにします。
            visuals.add(GestureGuiVisual.Block(
                visualId = "$id-block", x = x, y = y,
                width = GestureEditorLayout.ZOOM_SIZE, height = GestureEditorLayout.ZOOM_SIZE,
                blockData = Bukkit.createBlockData(if (enabled) Material.CYAN_CONCRETE else Material.GRAY_CONCRETE), layer = 4,
            ))
            visuals.add(GestureGuiVisual.Text(
                visualId = "$id-glyph", x = x, y = y - 0.01,
                text = net.kyori.adventure.text.Component.text(glyph).color(
                    if (enabled) net.kyori.adventure.text.format.NamedTextColor.WHITE else net.kyori.adventure.text.format.NamedTextColor.GRAY,
                ), size = 0.010, layer = 6,
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
    private fun addCloseButton(player: Player, visuals: MutableList<GestureGuiVisual>, elements: MutableList<GestureGuiElement>) {
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
                text = net.kyori.adventure.text.Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_CLOSE)),
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
        val layout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrNull() ?: return
        state.origin = GestureEditorLayout.clampOrigin(
            MapPoint(
                (centerX - (newMetrics.columns - 1) / 2.0).roundToInt(),
                (centerY - (newMetrics.rows - 1) / 2.0).roundToInt(),
            ),
            layout,
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

    private companion object {
        // GestureLowerPanelと同じ4項目単位で設定タブをページ分割します。
        const val SETTINGS_PAGE_SIZE = 4
        const val DIALOG_OWNER = "kantan-commander"
    }

}
