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
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiAccessPolicy
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiChildOptions
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOpenOptions
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenDefinition
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiSessionState
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
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.hasContextOverride
import me.awabi2048.kantancommander.security.PlacementAccessPolicy
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
import org.bukkit.scheduler.BukkitTask
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

/**
 * 実行前検証を下部パネルの「要確認」表示へ投影するための情報です。
 *
 * 検証結果を構造化エラーから要約し、ノードごとの要確認タブ（fieldKey）集合と、
 * スクリプト全体のタイマー要確認だけを画面側へ渡します。表示側がエラー文言を
 * 解析して意味を推測しないよう、この集約が唯一の受け渡し経路になります。
 */
data class GestureAttentionState(
    /** ノードID → そのノードで要確認となっている設定タブ（fieldKey）の集合。 */
    val fieldKeysByNode: Map<UUID, Set<String>> = emptyMap(),
    /** プログラムタイマー（スクリプト全体設定）が要確認か。 */
    val timer: Boolean = false,
) {
    companion object {
        val EMPTY = GestureAttentionState()
    }
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
    ENTITY_EQUIPMENT_SLOT,
    ENTITY_OVERWRITE,
    ENTITY_TAG_OPERATION,
    VARIABLE_TYPE,
    VARIABLE_OPERATION,
    VARIABLE_CHANGE_MODE,
    VARIABLE_VALUE,
    FOR_SOURCE,
    INCLUSIVE_END,
    CAMERA_SHAKE_TYPE,
    SOUND_SCOPE,
    CONTEXT_OVERRIDE,
    BLOCK_OPERATION,
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
    /** Gesture GUIの所有者です。共有画面でも保存正本と子画面はこのUUIDへ紐付けます。 */
    private var sessionOwnerId: UUID? = null
    /** 保存処理が完了するまで有効な、最後に表示／確認した正本世代です。 */
    private var observedRevision: Long? = null
    /**
     * 外部Dialogは操作者ごとに独立させます。同じ共有画面でAが入力中にBが別の
     * Dialogを開いても、Aの入力を閉じてしまうと「同時編集」ではなく、入力開始順
     * だけで他人の操作を失わせます。各Dialogは保存時に自身の世代をCASします。
     */
    private data class ActiveInput(
        val token: UUID,
        val dialogId: String,
        val expectedRevision: Long?,
    )

    private val activeInputs = mutableMapOf<UUID, ActiveInput>()
    /** 配置編集だけを共有画面にし、ディスク編集は従来どおり所有者限定にします。 */
    private val screenAccess = if (state.placement != null) {
        GestureGuiAccess.PUBLIC
    } else {
        GestureGuiAccess.OWNER_ONLY
    }
    /** MWM-Chanponのワールド単位ツール権限を入力受付時点で再確認します。 */
    private val screenAccessPolicy = state.placement?.let {
        GestureGuiAccessPolicy(::canOperateSharedActor)
    }
    // 下部画面のクリックは上部と同じハンドラで処理します（タブ切替・PICKER・CONFIRMの共通ロジック）。
    private val lowerPanel = GestureLowerPanel(
        plugin,
        onAction = { ctx -> handleUpperAction(ctx) },
        screenAccess = screenAccess,
        screenAccessPolicy = screenAccessPolicy,
    )

    private val UPPER_SCREEN_ID = "gesture-editor-upper"
    /** このエディターが所有するGesture GUIセッション。再オープン後の古い応答を遮断します。 */
    private var gestureSessionId: UUID? = null
    /**
     * OPENING中に拒否された画面更新を、セッション単位でACTIVE後へ持ち越します。
     * updateScreenのBooleanを捨てると、ローカル状態だけが進んで表示側が古いままになるため、
     * 画面ごとに保留フラグを持ち、再試行時は保存済みviewではなく最新stateから再生成します。
     */
    private var pendingUpperRender = false
    private var pendingLowerRender = false
    private var renderRetryTask: BukkitTask? = null
    private var renderRetrySessionId: UUID? = null
    private var renderRetryAttempts = 0

    private fun canOperateSharedActor(ownerId: UUID, actorId: UUID): Boolean {
        if (actorId == ownerId) return true
        val placement = state.placement ?: return false
        val owner = Bukkit.getPlayer(ownerId) ?: return false
        val actor = Bukkit.getPlayer(actorId) ?: return false
        // ツール権限はMWM-Chanponが現在ワールドへ一時付与するノードです。
        // 建築権限は要求しません。今回の要件では、既存ブロックを編集する
        // 「共有GUI操作」と、配置物を設置・破壊する「管理操作」を分離します。
        return actor.world.uid == owner.world.uid &&
            actor.world.name == placement.world &&
            actor.hasPermission(PlacementAccessPolicy.EXTENDED_COMMAND_BLOCK_PERMISSION)
    }

    internal fun canOperateSharedActor(player: Player): Boolean =
        sessionOwnerId?.let { canOperateSharedActor(it, player.uniqueId) } == true

    private fun ownerIdFor(player: Player): UUID = sessionOwnerId ?: player.uniqueId

    private fun ownerPlayerFor(player: Player): Player =
        sessionOwnerId?.let(Bukkit::getPlayer)?.takeIf(Player::isOnline) ?: player

    private fun expectedMutationRevision(player: Player): Long? =
        activeInputs[player.uniqueId]?.expectedRevision ?: observedRevision

    internal fun isEditing(placement: DiskPlacement): Boolean = state.placement?.key == placement.key

    internal fun isEditingScript(scriptId: UUID): Boolean = state.scriptId == scriptId

    /**
     * 同じプログラムを開いている別エディターへ保存結果を配布します。
     * 選択中ノードが削除されていた場合は、古い設定経路・挿入候補・確認対象を
     * まとめて破棄し、削除済みUUIDへ後続入力が届かないようにします。
     */
    internal fun refreshFromStore() {
        val owner = sessionOwnerId?.let(Bukkit::getPlayer)?.takeIf(Player::isOnline) ?: return
        val current = plugin.scripts.load(state.scriptId) ?: return
        val previousRevision = observedRevision
        if (activeInputs.isNotEmpty() && previousRevision != null && previousRevision != current.revision) {
            // 別操作者の保存結果を受け取った時点で、表示中のDialogも古い入力です。
            // CASだけで拒否すると、利用者が何度も同じ古いDialogを送信できるため、
            // 物理Dialogを閉じて最新画面から再選択させます。
            invalidateInputs()
        }
        observedRevision = current.revision
        val selectedNodeMissing = state.selectedNodeId?.let { it !in current.graph.nodes } == true
        val confirmedNodeMissing = state.confirmNodeId?.let { it !in current.graph.nodes } == true
        val settingNodeMissing = state.settingContext?.nodeId?.let { it !in current.graph.nodes } == true
        if (selectedNodeMissing || confirmedNodeMissing || settingNodeMissing) {
            invalidateInputs()
            state.selectedNodeId = null
            state.confirmNodeId = null
            state.pendingInsertion = null
            state.selectedAddPoint = null
            state.selectedInsertionCandidatePoint = null
            clearSettingState()
            state.lowerMode = GestureLowerMode.SETTINGS
            api.closeChild(owner.uniqueId, lowerPanel.CONFIRM_SCREEN_ID)
            api.closeChild(owner.uniqueId, lowerPanel.SETTING_CHILD_SCREEN_ID)
        } else {
            val target = state.pendingInsertion
            if (target != null && (
                    (target.sourceId != null && target.sourceId !in current.graph.nodes) ||
                        (target.mergeConditionId != null && target.mergeConditionId !in current.graph.nodes)
                    )) {
                invalidateInputs()
                state.pendingInsertion = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                clearSettingState()
                state.lowerMode = GestureLowerMode.SETTINGS
            }
        }
        updateUpper(owner)
        updateLower(owner)
    }

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
        invalidateInputs()
        sessionOwnerId = player.uniqueId
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
                // KantanのGesture GUIでは右クリックを操作に使わず、Interactionの腕振りも抑制します。
                // Inventory GUIのクリック仕様には影響させません。
                secondaryInputEnabled = false,
                // 画面外を含む左右クリックを外部ブロック／エンティティへ漏らしません。
                suppressWorldClicks = true,
            ),
        )
        gestureSessionId = snapshot.sessionId
    }

    fun updateUpper(player: Player): Boolean {
        val owner = ownerPlayerFor(player)
        return updateScreen(owner, buildUpperViewport(owner), RenderTarget.UPPER)
    }

    fun updateLower(player: Player): Boolean {
        val owner = ownerPlayerFor(player)
        val childOpen = settingChildOpen(owner.uniqueId)
        val attention = attentionState()
        val view = if (state.lowerMode == GestureLowerMode.SETTING_CHOICES && childOpen) {
            lowerPanel.buildSettingChild(state, owner, attention)
        } else {
            lowerPanel.build(state, owner, attention)
        }
        return updateScreen(owner, view, RenderTarget.LOWER)
    }

    /**
     * 実行前検証を、下部パネルの「要確認」表示用の情報へ要約します。
     *
     * 構造化エラー（nodeId/fieldKeys）をそのまま集約するため、表示側でエラー文言を
     * 解析する必要はありません。snapshot内のエラーは主グラフのノードへ対応しないため、
     * 存在しないノードIDは除外します。
     */
    private fun attentionState(): GestureAttentionState {
        val script = plugin.scripts.load(state.scriptId) ?: return GestureAttentionState.EMPTY
        val errors = ExecutableScriptValidator.validate(script, plugin.graphLimits())
        val fieldKeysByNode = errors
            .filter { it.nodeId != null && it.nodeId in script.graph.nodes }
            .groupBy({ it.nodeId!! }) { it.fieldKeys }
            .mapValues { (_, keys) -> keys.flatten().toSet() }
        return GestureAttentionState(
            fieldKeysByNode = fieldKeysByNode,
            timer = errors.any { it.nodeId == null && "timer" in it.fieldKeys },
        )
    }

    /**
     * 画面差し替えの結果を必ず扱います。OPENING中だけはCC-Systemの仕様上失敗するため、
     * セッションIDを固定した再試行へ送り、ACTIVEなのに失敗した場合は異常として記録します。
     */
    private fun updateScreen(player: Player, view: GestureGuiView, target: RenderTarget): Boolean {
        val owner = ownerPlayerFor(player)
        val updated = api.updateScreen(owner.uniqueId, view)
        if (updated) {
            clearPendingRender(target)
            return true
        }
        markPendingRender(owner, target)
        return false
    }

    private fun markPendingRender(player: Player, target: RenderTarget) {
        when (target) {
            RenderTarget.UPPER -> pendingUpperRender = true
            RenderTarget.LOWER -> pendingLowerRender = true
        }
        val snapshot = api.snapshot(ownerIdFor(player))
        val sessionId = gestureSessionId
        if (sessionId == null || snapshot?.sessionId != sessionId) return
        if (snapshot.state != GestureGuiSessionState.OPENING) {
            plugin.logger.warning(
                "ジェスチャーGUIの画面更新が拒否されました: target=$target state=${snapshot.state} " +
                    "session=$sessionId",
            )
            return
        }
        if (renderRetryTask != null && renderRetrySessionId == sessionId) return
        cancelRenderRetry(clearPending = false)
        renderRetrySessionId = sessionId
        renderRetryAttempts = 0
        renderRetryTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            retryPendingRender(player, sessionId)
        }, 1L, 1L)
    }

    /** OPENING完了後に、保留した画面だけを最新ローカル状態から再生成します。 */
    private fun retryPendingRender(player: Player, sessionId: UUID) {
        val snapshot = api.snapshot(ownerIdFor(player))
        if (gestureSessionId != sessionId || snapshot?.sessionId != sessionId) {
            cancelRenderRetry()
            return
        }
        if (snapshot.state != GestureGuiSessionState.ACTIVE) {
            renderRetryAttempts++
            if (renderRetryAttempts >= MAX_RENDER_RETRY_TICKS) {
                plugin.logger.warning(
                    "ジェスチャーGUIの画面更新を再試行上限で打ち切りました: session=$sessionId state=${snapshot.state}",
                )
                cancelRenderRetry()
            }
            return
        }

        if (pendingUpperRender) {
            val owner = ownerPlayerFor(player)
            if (api.updateScreen(owner.uniqueId, buildUpperViewport(owner))) pendingUpperRender = false
        }
        if (pendingLowerRender) {
            val owner = ownerPlayerFor(player)
            val childOpen = settingChildOpen(owner.uniqueId)
            val view = if (state.lowerMode == GestureLowerMode.SETTING_CHOICES && childOpen) {
                lowerPanel.buildSettingChild(state, owner, attentionState())
            } else {
                lowerPanel.build(state, owner, attentionState())
            }
            if (api.updateScreen(owner.uniqueId, view)) pendingLowerRender = false
        }
        if (!pendingUpperRender && !pendingLowerRender) {
            cancelRenderRetry(clearPending = false)
        } else {
            // ACTIVE後も対象画面が見つからない場合は、毎tick無限再試行せず異常を残します。
            plugin.logger.warning("ACTIVE後もジェスチャーGUIの画面更新に失敗しました: session=$sessionId")
            cancelRenderRetry()
        }
    }

    private fun clearPendingRender(target: RenderTarget) {
        when (target) {
            RenderTarget.UPPER -> pendingUpperRender = false
            RenderTarget.LOWER -> pendingLowerRender = false
        }
        if (!pendingUpperRender && !pendingLowerRender) cancelRenderRetry(clearPending = false)
    }

    private fun cancelRenderRetry(clearPending: Boolean = true) {
        renderRetryTask?.cancel()
        renderRetryTask = null
        renderRetrySessionId = null
        renderRetryAttempts = 0
        if (clearPending) {
            pendingUpperRender = false
            pendingLowerRender = false
        }
    }

    private enum class RenderTarget {
        UPPER,
        LOWER,
    }

    fun openConfirmChild(player: Player) {
        val ownerId = ownerIdFor(player)
        if (api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.CONFIRM_SCREEN_ID) == true) return
        val settingChildWasOpen = settingChildOpen(ownerId)
        state.lowerMode = GestureLowerMode.CONFIRM
        val view = lowerPanel.build(state, ownerPlayerFor(player))
        val opened = api.openChild(
            ownerId,
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
        val ownerId = ownerIdFor(player)
        if (settingChildOpen(ownerId)) {
            updateLower(player)
            return
        }
        val opened = runCatching {
            api.openChild(
                ownerId,
                lowerPanel.buildSettingChild(state, ownerPlayerFor(player)),
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
        val ownerId = ownerIdFor(player)
        if (settingChildOpen(ownerId)) {
            api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
        }
        // 明示的な戻る操作はpopSettingFrameを通ります。ここはノード選択や
        // 画面切替による中断専用とし、途中の設定木を残して孤立させません。
        clearSettingState()
        state.lowerMode = GestureLowerMode.SETTINGS
        updateLower(player)
    }

    /** 指定操作者のDialog世代だけを破棄します。成功／キャンセル後の通常経路で使います。 */
    private fun clearInputState(playerId: UUID, token: UUID? = null) {
        val current = activeInputs[playerId] ?: return
        if (token != null && current.token != token) return
        activeInputs.remove(playerId)
    }

    /**
     * 現在のDialogを物理的にも閉じてから入力世代を破棄します。
     * 共有Gesture画面ではDialogの操作者とセッション所有者が異なるため、
     * 両者を混同して古いDialogを残さないようにします。
     */
    private fun invalidateInput(playerId: UUID) {
        val input = activeInputs.remove(playerId) ?: return
        val sessionId = gestureSessionId
        val ownerId = sessionOwnerId
        if (sessionId != null && ownerId != null) {
            Bukkit.getPlayer(playerId)?.let { player ->
                runCatching {
                    api.closeExternalDialogIfCurrent(
                        ownerId,
                        sessionId,
                        player,
                        DIALOG_OWNER,
                        input.dialogId,
                    )
                }.onFailure { failure ->
                    plugin.logger.warning("Kantan Commanderの入力Dialog終了に失敗しました: ${failure.message}")
                }
            }
        }
    }

    /** セッション終了や共有正本の再同期時に、全操作者の古いDialogを回収します。 */
    private fun invalidateInputs() {
        activeInputs.keys.toList().forEach(::invalidateInput)
    }

    /**
     * Gestureセッション終了時のローカル状態を一箇所で解放します。
     * DialogはIDと表示所有者をCC-System側で照合し、別機能のDialogを閉じないようにします。
     * Facade通知はgestureSessionIdをまだ保持した状態で行い、旧通知が新エディターを
     * 消さないようFacade側でインスタンスとセッションIDを照合できるようにします。
     */
    private fun detachLocalSession(ownerId: UUID, sessionId: UUID?) {
        if (sessionId != null && gestureSessionId != sessionId) return
        invalidateInputs()
        // 古いセッションの再試行が新セッションの画面を上書きしないよう、終了時に必ず破棄します。
        cancelRenderRetry()
        if (sessionId != null) {
            runCatching { onSessionClosed(this, ownerId, sessionId) }
                .onFailure { failure ->
                    // Facade側の通知失敗でエディター自身のセッションIDを残すと、
                    // 後続の入力を現行セッションと誤認するため、通知例外を隔離します。
                    plugin.logger.warning("Kantan Commanderのセッション終了通知に失敗しました: ${failure.message}")
                }
        }
        gestureSessionId = null
        sessionOwnerId = null
        observedRevision = null
    }

    private fun buildUpperViewport(player: Player): GestureGuiView {
        val script = plugin.scripts.load(state.scriptId) ?: return emptyView()
        observedRevision = script.revision
        val persistedLayout = runCatching { GraphLayoutEngine.layout(script.graph) }
            .getOrElse { return layoutErrorView(player) }
        // 挿入プレビューは「経路クリックによる挿入」のときだけ適用します。
        // 追加ポイントからの追加は、追加ボタン自体が候補位置であり既存ノードが
        // 動かないため、仮ノード入りレイアウトや候補マーカーは二重表示になります。
        val insertionPreview = insertionPreview(script)
        val renderGraph = insertionPreview?.graph ?: script.graph
        val layout = insertionPreview?.layout ?: persistedLayout
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
        // 検証結果は構造化エラー（nodeId/fieldKeys）として受けるため、表示文字列の
        // パス解析は行いません。snapshot内のエラーは主グラフのノードへ対応しないため、
        // 存在しないノードIDは自然に除外されます。
        val validationErrors = ExecutableScriptValidator.validate(script, plugin.graphLimits())
        val incompleteNodeIds = buildSet {
            validationErrors.forEach { error ->
                error.nodeId?.let { nodeId ->
                    if (nodeId in script.graph.nodes) add(nodeId)
                }
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
            acceptedGestures = GestureGuiClickPolicy.CLICK,
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
                    val node = cell.nodeId?.let { renderGraph.nodes[it] }
                    if (node != null && node.id != insertionPreview?.insertedNodeId) {
                        val isSelected = state.selectedNodeId == node.id
                        val glowColor = if (isSelected) Color.YELLOW.asARGB() else null
                        val incomplete = node.id in incompleteNodeIds
                        val hasContextOverride = node.hasContextOverride()
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
                        if (insertionPreview == null) {
                            elements.add(GestureGuiElement(
                                elementId = "node:${node.id}",
                                bounds = iconBounds(cx, cy, metrics.iconSize),
                                acceptedGestures = GestureGuiClickPolicy.CLICK,
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
                        if (insertionPreview == null && isSelected && node.type !in setOf(
                                CommandType.CONDITION,
                                CommandType.MERGE,
                                CommandType.FOR_START,
                                CommandType.FOR_END,
                            )) {
                            val reorderSize = minOf(metrics.pitchX, metrics.pitchY) * 0.20
                            val reorderY = cy - metrics.iconSize / 2.0 - reorderSize / 2.0 - 0.008
                            val reorderOffset = reorderSize * 0.78
                            listOf(
                                GraphEditor.ReorderDirection.LEFT to "←",
                                GraphEditor.ReorderDirection.RIGHT to "→",
                            ).forEach { (direction, glyph) ->
                                val enabled = GraphEditor.canSwapAdjacent(script.graph, node.id, direction)
                                val x = cx + if (direction == GraphEditor.ReorderDirection.LEFT) -reorderOffset else reorderOffset
                                val directionName = direction.name.lowercase()
                                visuals.add(GestureGuiVisual.Block(
                                    visualId = "node-reorder-$directionName-bg-${node.id}",
                                    x = x,
                                    y = reorderY,
                                    width = reorderSize,
                                    height = reorderSize,
                                    blockData = Bukkit.createBlockData(
                                        if (enabled) Material.CYAN_CONCRETE else DisabledGuiVisualPolicy.material,
                                    ),
                                    layer = GestureEditorLayout.ICON_BACKGROUND_LAYER,
                                ))
                                visuals.add(GestureGuiVisual.Text(
                                    visualId = "node-reorder-$directionName-glyph-${node.id}",
                                    x = x,
                                    y = reorderY - 0.006,
                                    text = Component.text(glyph).color(
                                        if (enabled) NamedTextColor.WHITE else NamedTextColor.GRAY,
                                    ),
                                    size = 0.007,
                                    layer = GestureEditorLayout.ICON_LAYER,
                                ))
                                elements.add(GestureGuiElement(
                                    elementId = "node-reorder:$directionName:${node.id}",
                                    bounds = iconBounds(x, reorderY, reorderSize),
                                    // 隣接ノードの状態は他の操作や外部保存で変わり得るため、
                                    // 描画時のenabledを入力可否へ固定しません。クリック時に
                                    // 最新グラフを再取得し、表示更新なしでも誤操作を防ぎます。
                                    acceptedGestures = GestureGuiClickPolicy.CLICK,
                                    gestureGuard = { _, _ ->
                                        plugin.scripts.load(state.scriptId)?.graph?.let { currentGraph ->
                                            GraphEditor.canSwapAdjacent(currentGraph, node.id, direction)
                                        } == true
                                    },
                                    targetVisualId = "node-reorder-$directionName-glyph-${node.id}",
                                ))
                            }
                        }
                    }
                }
                MapCellKind.ADD -> {
                    val isSelected = insertionPreview == null && state.selectedAddPoint == MapPoint(gx, gy)
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
                    if (insertionPreview == null) {
                        elements.add(GestureGuiElement(
                            elementId = "add:$gx:$gy",
                            bounds = iconBounds(cx, cy, metrics.iconSize),
                            acceptedGestures = GestureGuiClickPolicy.CLICK,
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
                }
                MapCellKind.PATH, MapCellKind.BRANCH_PATH, MapCellKind.LOOP_RETURN_PATH -> {
                    // 追加ポイント直前の経路は「クリックで挿入」を表示しません。
                    val hasAddNeighbor = projection.hasNeighborOfKind(localPoint, MapCellKind.ADD)
                    if (insertionPreview == null && !hasAddNeighbor && cell.insertionTarget != null) {
                        elements.add(GestureGuiElement(
                            elementId = "path:${gx}:${gy}",
                            bounds = rect(cx, cy, metrics.pitchX, metrics.pitchY),
                            acceptedGestures = GestureGuiClickPolicy.CLICK,
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
            val isLoopReturn = seg.kind == MapCellKind.LOOP_RETURN_PATH
            visuals.add(GestureGuiVisual.Block(
                visualId = "path-${seg.x}-${seg.y}-${seg.w}-${seg.h}",
                x = seg.x, y = seg.y,
                width = seg.w, height = seg.h,
                // ノード背景は変えず、経路色のみを InventoryGUI の様式に合わせて区別します。
                // 通常・分岐は白色、ループ戻り経路のみ空色（LIGHT_BLUE）とします。
                blockData = Bukkit.createBlockData(
                    if (isLoopReturn) Material.LIGHT_BLUE_CONCRETE else Material.WHITE_CONCRETE,
                ),
                layer = 1,
            ))
        }

        // 経路をクリックしてPICKERへ移った場合は、仮ノードを含むレイアウト上の
        // 実際の挿入位置へ候補アイコンを置きます。仮ノード自体の標準アイコンは
        // 上で描画せず、ここで「＋」と黄色ハイライトへ差し替えます。これにより、
        // クリック元と異なる位置へ移動する後続コマンドも同じプレビュー上で確認できます。
        val insertionCandidate = insertionPreview?.layout?.nodePoints?.get(insertionPreview.insertedNodeId)
            ?.takeIf { selectedGlobal ->
            projection.contains(MapPoint(selectedGlobal.x - state.origin.x, selectedGlobal.y - state.origin.y))
            }
        insertionCandidate?.let { selectedGlobal ->
            val local = MapPoint(
                selectedGlobal.x - state.origin.x,
                selectedGlobal.y - state.origin.y,
            )
            val cx = metrics.x(local.x)
            val cy = metrics.y(local.y)
            visuals.add(GestureGuiVisual.Block(
                visualId = "path-insertion-candidate-bg-${selectedGlobal.x}-${selectedGlobal.y}",
                x = cx,
                y = cy,
                width = metrics.iconSize,
                height = metrics.iconSize,
                blockData = Bukkit.createBlockData(Material.YELLOW_CONCRETE),
                layer = GestureEditorLayout.ICON_BACKGROUND_LAYER + 1,
                glowColor = Color.YELLOW.asARGB(),
            ))
            visuals.add(GestureGuiVisual.Text(
                visualId = "path-insertion-candidate-plus-${selectedGlobal.x}-${selectedGlobal.y}",
                x = cx,
                y = cy,
                text = Component.text("+"),
                size = 0.012,
                layer = GestureEditorLayout.ICON_LAYER + 1,
            ))
        }

        addNavigation(visuals, elements, layout, metrics.columns, metrics.rows)

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
            text = net.kyori.adventure.text.Component.text("先頭に移動する"),
            size = 0.0055,
            layer = 6,
        ))
        elements.add(GestureGuiElement(
            elementId = "back-to-start",
            bounds = navBounds(GestureEditorLayout.BACK_X, GestureEditorLayout.BACK_Y, GestureEditorLayout.NAV_SIZE),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
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
            if (element.elementId.startsWith("node:") || element.elementId.startsWith("node-reorder:") ||
                element.elementId.startsWith("add:") || element.elementId.startsWith("path:")) {
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
            GestureGuiScreenDefinition(
                UPPER_SCREEN_ID,
                finalElements,
                access = screenAccess,
                accessPolicy = screenAccessPolicy,
            ),
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
            val ownerId = ownerIdFor(player)
            if (settingChildOpen(ownerId)) {
                api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
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
        observedRevision = script.revision
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
        val fields = CommandSettingsModel.visibleFields(node)
        if (absoluteIndex !in fields.indices) return

        invalidateInput(player.uniqueId)
        state.settingsTab = absoluteIndex
        state.settingsPage = absoluteIndex / SETTINGS_PAGE_SIZE
        // タブ切替時は親画面を先に更新し、選択中の項目・現在値を常に残します。
        // 木の直下の選択肢も親画面へ表示し、二段階目が必要なときだけ子画面へ進みます。
        val ownerId = ownerIdFor(player)
        if (settingChildOpen(ownerId)) {
            api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
        }
        val field = fields[absoluteIndex]
        // アイテムタブの選択は表示だけを切り替えます。タブを開いただけで
        // 設定済みアイテムを上書きしないよう、保存操作は右ペインの
        // 「メインハンドから設定」ボタンへ限定します。
        if (field.key == "item" && (
                node.type == CommandType.GIVE_ITEM ||
                    (node.type == CommandType.ENTITY_ACTION && node.string("action", "ride") == "equip") ||
                    (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.PLAYER_STATE.name)
                )) {
            clearSettingState()
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
            return
        }
        val descriptor = CommandSettingsModel.descriptor(node, field.key)
        val screen = gestureSettingScreenFor(descriptor.editor)
        if (screen == null) {
            if (settingChildOpen(ownerId)) {
                api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
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
        observedRevision = script.revision
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
        val descriptor = CommandSettingsModel.descriptor(node, fieldKey)
        val context = CommandSettingContext(state.scriptId, node.id, descriptor.role)
        if (fieldKey == "item" && (
                node.type == CommandType.GIVE_ITEM ||
                    (node.type == CommandType.ENTITY_ACTION && node.string("action", "ride") == "equip") ||
                    (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.PLAYER_STATE.name)
                )) {
            applyHeldItem(player, context)
            return
        }
        if (fieldKey == "block" && (
                node.type == CommandType.BLOCK_OPERATION ||
                    (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.BLOCK_STATE.name)
                )) {
            applyHeldBlock(player, context)
            return
        }
        if (fieldKey == "diskId" && node.type == CommandType.DISK_CALL) {
            applyHeldDisk(player, context)
            return
        }
        if (fieldKey == "staySeconds" && node.type == CommandType.DISPLAY_TEXT) {
            // 表示時間はfadeInSeconds/staySeconds/fadeOutSecondsを一組として編集し、インベントリGUIと
            // 同じ入力欄・最大長・0以上検証を使います。
            showDisplayTimingSettingDialog(player, context, node)
            return
        }
        if (fieldKey == "soundParameters" && node.type == CommandType.PLAY_SOUND) {
            showSoundParametersSettingDialog(player, context, node)
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
            // 入力項目はCommandSettingsModelが返すフィールド集合から来るため、
            // 仕様未登録時に自由入力へ落とすとInventory/Gesture間の契約が壊れます。
            val spec = CommandDialogSpecs.field(node, fieldKey, valueSource) ?: return
            showTextInputDialog(
                player,
                spec,
                node.string(fieldKey),
                suggestionParameter = fieldKey,
            ) { raw ->
                // SETTINGS 経由の入力と同じく、前後空白を正規化してから検証・保存します。
                // 同一フィールドを上段・下段のどちらから編集しても結果が変わらないようにします。
                val value = CommandDialogSpecs.normalize(fieldKey, raw)
                val validationError = spec.validateInput(value)
                if (validationError != null) return@showTextInputDialog KcI18n.text(player, validationError)
                val updated = CommandSettingsModel.updateNode(
                    plugin,
                    context,
                    configuredFields = setOf(fieldKey),
                    change = { CommandSettingsModel.setParameter(it, fieldKey, value) },
                )
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

    /** ブロック設定はメインハンドの実アイテムからブロックIDだけを保存します。 */
    private fun applyHeldBlock(
        player: Player,
        context: CommandSettingContext,
    ): Boolean {
        val held = player.inventory.itemInMainHand
            .takeUnless { it.type.isAir || !it.type.isBlock }
            ?: run {
                // 空手や非ブロックアイテムでは、設定・通知・効果音を発生させません。
                updateLower(player)
                return false
            }
        val updated = updateSettingNode(player, context, configuredFields = setOf("block")) {
            CommandSettingsModel.setParameter(it, "block", held.type.key.toString())
        }
        if (updated) updateLower(player)
        return updated
    }

    /**
     * 外部ディスクもアイテム付与と同じメインハンド入力で設定します。
     * ディスクUUIDだけを保存すると元ディスクの変更で実行内容が変わるため、
     * 挿入時点の独立スナップショットを同時に保持し、後続実行を安定させます。
     */
    private fun applyHeldDisk(
        player: Player,
        context: CommandSettingContext,
    ): Boolean {
        val diskId = KantanItemService.diskId(player.inventory.itemInMainHand)
        if (diskId == null) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_DISK_NOT_HELD))
            updateLower(player)
            return false
        }
        val disk = plugin.scripts.load(diskId)
        if (disk == null) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_DISK_NOT_HELD))
            updateLower(player)
            return false
        }
        val updated = updateSettingNode(player, context, configuredFields = setOf("diskId")) { node ->
            CommandSettingsModel.setParameter(node, "diskId", diskId.toString())
            node.snapshot = disk.graph.deepCopy()
        }
        if (updated) {
            player.sendMessage(
                KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_DISK_SET,
                    mapOf("disk" to disk.name),
                ),
            )
        }
        return updated
    }

    private fun saveHeldItem(
        player: Player,
        context: CommandSettingContext,
        parameter: String,
        itemKey: String,
        itemData: String,
    ): Boolean {
        val updated = updateSettingNode(player, context, configuredFields = setOf(parameter)) { node ->
            CommandSettingsModel.setParameter(node, parameter, itemKey)
            // アイテム名だけでなく、数量・Name/Lore・エンチャント・データ
            // コンポーネントを含むシリアライズ結果を保存します。
            if (parameter == "item") CommandSettingsModel.setParameter(node, "itemData", itemData)
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
        val ownerId = ownerIdFor(player)
        if (api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.CONFIRM_SCREEN_ID) == true) return
        val parentId = if (settingChildOpen(ownerId)) lowerPanel.SETTING_CHILD_SCREEN_ID else lowerPanel.LOWER_SCREEN_ID
        state.confirmKind = GestureConfirmKind.ITEM_OVERWRITE
        state.confirmNodeId = null
        state.pendingItemContext = context
        state.pendingItemKey = itemKey
        state.pendingItemData = itemData
        state.lowerMode = GestureLowerMode.CONFIRM
        val opened = api.openChild(
            ownerId,
            lowerPanel.build(state, ownerPlayerFor(player)),
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
            CommandSettingsModel.updateNode(
                plugin,
                context,
                configuredFields = setOf("item"),
                editorId = player.uniqueId,
                expectedRevision = expectedMutationRevision(player),
                change = { node ->
                    CommandSettingsModel.setParameter(node, "item", itemKey)
                    CommandSettingsModel.setParameter(node, "itemData", itemData)
                },
            ) != null
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
        val ownerId = ownerIdFor(player)
        api.closeChild(ownerId, lowerPanel.CONFIRM_SCREEN_ID)
        player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_ITEM_OVERWRITTEN, mapOf("item" to itemKey)))
        state.lowerMode = if (settingChildOpen(ownerId) && state.settingContext != null) {
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
        configuredFields: Set<String> = emptySet(),
        change: (me.awabi2048.kantancommander.model.CommandNode) -> Unit,
    ): Boolean {
        val saved = runCatching {
            CommandSettingsModel.updateNode(
                plugin,
                context,
                editorId = player.uniqueId,
                // タブ選択直後は画面を常に親設定へ戻すためsettingFieldKeyが空に
                // なります。設定済み判定はUI状態に依存させず、実際に変更した
                // 項目を呼び出し元から明示します。
                configuredFields = configuredFields.ifEmpty { setOfNotNull(state.settingFieldKey) },
                expectedRevision = expectedMutationRevision(player),
                change = change,
            ) != null
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
    private fun beginSettingDistanceInput(
        player: Player,
        minimum: Double?,
        maximum: Double?,
        result: (Double?, Double?) -> String?,
    ) {
        val spec = requireNotNull(CommandDialogSpecs.targetFilter("distance"))
        fun format(value: Double?): String = value?.let {
            if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }.orEmpty()
        val current = "${format(minimum)}..${format(maximum)}"
        showInputDialog(
            player = player,
            body = CommandDialogSpecs.body(player, spec, current),
            inputs = listOf(
                MenuDialogInput.Text(
                    "minimum",
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE),
                    format(minimum),
                    maxLength = spec.maxLength,
                ),
                MenuDialogInput.Text(
                    "maximum",
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE),
                    format(maximum),
                    maxLength = spec.maxLength,
                ),
            ),
        ) { response ->
            val minimumRaw = response.textValue("minimum").trim().takeIf(String::isNotEmpty)
            val maximumRaw = response.textValue("maximum").trim().takeIf(String::isNotEmpty)
            val validationError = listOfNotNull(minimumRaw, maximumRaw)
                .mapNotNull(spec::validateInput)
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val minimumValue = minimumRaw?.toDoubleOrNull()?.takeIf(Double::isFinite)
            val maximumValue = maximumRaw?.toDoubleOrNull()?.takeIf(Double::isFinite)
            if ((minimumRaw != null && minimumValue == null) || (maximumRaw != null && maximumValue == null)) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID)
            }
            if (minimumValue != null && maximumValue != null && minimumValue > maximumValue) {
                return@showInputDialog KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_MINIMUM_ABOVE_MAXIMUM,
                )
            }
            val error = result(minimumValue, maximumValue)
            if (error != null) return@showInputDialog error
            updateUpper(player)
            updateLower(player)
            null
        }
    }

    /** 対象範囲の3軸を一つのDialogで入力し、空欄はその軸の指定解除とします。 */
    private fun beginSettingRangeInput(
        player: Player,
        dx: Double?,
        dy: Double?,
        dz: Double?,
        result: (Double?, Double?, Double?) -> String?,
    ) {
        val spec = requireNotNull(CommandDialogSpecs.targetFilter("range"))
        showInputDialog(
            player = player,
            body = CommandDialogSpecs.rangeBody(player, dx, dy, dz),
            inputs = CommandDialogSpecs.rangeInputs(player, dx, dy, dz),
        ) { response ->
            val raw = listOf("dx", "dy", "dz").associateWith { key ->
                response.textValue(key).trim().takeIf(String::isNotEmpty)
            }
            val validationError = raw.values
                .filterNotNull()
                .mapNotNull(spec::validateInput)
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val values = raw.mapValues { (_, value) -> value?.let(CommandDialogSpecs::finiteDouble) }
            if (values.any { (key, parsed) -> raw[key] != null && parsed == null }) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID)
            }
            result(values.getValue("dx"), values.getValue("dy"), values.getValue("dz"))
        }
    }

    private fun beginSettingInput(
        player: Player,
        spec: CommandDialogSpecs.Spec,
        initial: String = "",
        suggestionParameter: String? = null,
        result: (String) -> String?,
    ) {
        showTextInputDialog(player, spec, initial, suggestionParameter = suggestionParameter) { raw ->
            val error = result(raw)
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
        suggestionParameter: String? = null,
        candidateValues: List<String> = emptyList(),
        onSubmit: (String) -> String?,
    ) {
        val candidateButtons = candidateValues.take(12).map { candidate ->
                MenuDialogButton(
                    Component.text(candidate),
                    MenuDialogHandler { target, _ ->
                        if (target.uniqueId != player.uniqueId ||
                            !canOperateSharedActor(ownerIdFor(player), target.uniqueId)
                        ) {
                            if (target.uniqueId == player.uniqueId) invalidateInput(player.uniqueId)
                            return@MenuDialogHandler MenuActionResult.Ignored
                        }
                        val value = CommandDialogSpecs.normalize(suggestionParameter.orEmpty(), candidate)
                        val validationError = spec.validateInput(value)
                        if (validationError != null) {
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                Component.text(KcI18n.text(player, validationError), NamedTextColor.RED),
                            )
                        }
                        val error = runCatching { onSubmit(value) }
                            .getOrElse { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT) }
                        if (error != null) {
                            MenuActionResult.Rejected(Component.text(error, NamedTextColor.RED))
                        } else {
                            MenuActionResult.Success(MenuUpdate.Close)
                        }
                    },
                )
        }
        val candidateFooterActions = if (suggestionParameter != null && CommandDialogSpecs.supportsSuggestions(suggestionParameter)) {
            listOf(MenuDialogButton(
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SHOW_DETAILS),
                MenuDialogHandler { target, response ->
                    val ownerId = ownerIdFor(player)
                    if (target.uniqueId != player.uniqueId || !canOperateSharedActor(ownerId, target.uniqueId)) {
                        if (target.uniqueId == player.uniqueId) invalidateInput(player.uniqueId)
                        return@MenuDialogHandler MenuActionResult.Ignored
                    }
                    val query = response.textValue("value").trim()
                    showTextInputDialog(
                        player = player,
                        spec = spec,
                        initial = query,
                        suggestionParameter = suggestionParameter,
                        candidateValues = CommandDialogSpecs.suggestions(suggestionParameter, query),
                        onSubmit = onSubmit,
                    )
                    MenuActionResult.Success(MenuUpdate.None)
                },
            ))
        } else emptyList()
        showInputDialog(
            player = player,
            body = CommandDialogSpecs.body(player, spec, initial),
            inputs = listOf(CommandDialogSpecs.input(player, "value", initial, spec)),
            additionalActions = candidateButtons,
            footerActions = candidateFooterActions,
            multiActionWithoutExit = candidateFooterActions.isNotEmpty(),
            columns = if (candidateFooterActions.isNotEmpty()) 3 else 1,
        ) { response ->
            val value = CommandDialogSpecs.normalize(suggestionParameter.orEmpty(), response.textValue("value"))
            val validationError = spec.validateInput(value)
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            onSubmit(value)
        }
    }

    /** ノード未選択時に表示するプログラム名の設定ダイアログです。 */
    private fun showProgramNameDialog(player: Player) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        observedRevision = script.revision
        showTextInputDialog(player, CommandDialogSpecs.programName, script.name) { raw ->
            val value = raw.trim()
            val updated = runCatching {
                CommandSettingsModel.updateScriptName(
                    plugin,
                    state.scriptId,
                    value,
                    player.uniqueId,
                    expectedRevision = expectedMutationRevision(player),
                )
            }.getOrElse { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "プログラム名を保存できませんでした: script=${state.scriptId}",
                    failure,
                )
                return@showTextInputDialog KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                )
            }
            if (!updated) {
                return@showTextInputDialog KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                )
            }
            updateUpper(player)
            updateLower(player)
            null
        }
    }

    /** ノード未選択時のプログラムタイマーを秒単位で設定します。 */
    private fun showTimerSettingDialog(player: Player) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        observedRevision = script.revision
        val timerSpec = CommandDialogSpecs.timerSeconds
        showInputDialog(
            player = player,
            title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TIMER),
            body = CommandDialogSpecs.timerBody(player, script.timer.intervalSeconds),
            inputs = listOf(CommandDialogSpecs.timerInput(player, script.timer.intervalSeconds)),
        ) { response ->
            val rawSeconds = response.textValue("seconds").trim()
            val validationError = timerSpec.validateInput(rawSeconds)
            if (validationError != null) {
                return@showInputDialog KcI18n.text(player, validationError)
            }
            val seconds = requireNotNull(rawSeconds.toIntOrNull())
            val updated = runCatching {
                CommandSettingsModel.updateTimer(
                    plugin,
                    state.scriptId,
                    enabled = true,
                    intervalSeconds = seconds,
                    editorId = player.uniqueId,
                    expectedRevision = expectedMutationRevision(player),
                )
            }.getOrElse { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "タイマー設定を保存できませんでした: script=${state.scriptId}",
                    failure,
                )
                return@showInputDialog KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                )
            }
            if (!updated) {
                return@showInputDialog KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                )
            }
            updateUpper(player)
            updateLower(player)
            null
        }
    }

    /** DISPLAY_TEXTの3つの時間設定を、インベントリGUIと同じ仕様で編集します。 */
    private fun showDisplayTimingSettingDialog(
        player: Player,
        context: CommandSettingContext,
        node: me.awabi2048.kantancommander.model.CommandNode,
    ) {
        val fadeIn = node.string("fadeInSeconds", "1")
        val stay = node.string("staySeconds", "3")
        val fadeOut = node.string("fadeOutSeconds", "1")
        val durationSpec = requireNotNull(CommandDialogSpecs.field(node, "staySeconds"))
        showInputDialog(
            player = player,
            title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TITLE),
            body = CommandDialogSpecs.durationBody(
                player,
                fadeIn,
                stay,
                fadeOut,
                node.string("mode", "tellraw"),
            ),
            inputs = CommandDialogSpecs.durationInputs(player, fadeIn, stay, fadeOut),
        ) { response ->
            val rawValues = listOf("fadeInSeconds", "staySeconds", "fadeOutSeconds").associateWith { key ->
                response.textValue(key).trim()
            }
            val validationError = rawValues.values
                .mapNotNull(durationSpec::validateInput)
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            if (!updateSettingNode(player, context) { command ->
                CommandSettingsModel.setParameters(command, rawValues)
                }
            ) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            }
            null
        }
    }

    /** 効果音の音量・ピッチを一つの設定項目として編集します。 */
    private fun showSoundParametersSettingDialog(
        player: Player,
        context: CommandSettingContext,
        node: CommandNode,
    ) {
        val volumeSpec = requireNotNull(CommandDialogSpecs.field(node, "volume"))
        val pitchSpec = requireNotNull(CommandDialogSpecs.field(node, "pitch"))
        val volume = node.string("volume", "1.0")
        val pitch = node.string("pitch", "1.0")
        showInputDialog(
            player = player,
            body = CommandDialogSpecs.soundParametersBody(player, volume, pitch),
            inputs = CommandDialogSpecs.soundParametersInputs(player, volume, pitch),
        ) { response ->
            val volumeValue = CommandDialogSpecs.normalize("volume", response.textValue("volume"))
            val pitchValue = CommandDialogSpecs.normalize("pitch", response.textValue("pitch"))
            val validationError = volumeSpec.validateInput(volumeValue)
                ?: pitchSpec.validateInput(pitchValue)
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            if (!updateSettingNode(player, context, configuredFields = setOf("soundParameters")) { command ->
                    CommandSettingsModel.setParameters(
                        command,
                        mapOf("volume" to volumeValue, "pitch" to pitchValue),
                    )
                }) {
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
        additionalActions: List<MenuDialogButton> = emptyList(),
        footerActions: List<MenuDialogButton> = emptyList(),
        multiActionWithoutExit: Boolean = false,
        columns: Int = 1,
        onSubmit: (MenuDialogResponse) -> String?,
    ) {
        invalidateInput(player.uniqueId)
        val token = UUID.randomUUID()
        val dialogId = "gesture-input-$token"
        activeInputs[player.uniqueId] = ActiveInput(token, dialogId, observedRevision)
        try {
            CCSystem.getAPI().getMenuDialogService().show(
                player,
                MenuDialogRequest(
                    owner = DIALOG_OWNER,
                    id = dialogId,
                    title = title,
                    body = body,
                    inputs = inputs,
                    additionalActions = additionalActions,
                    footerActions = footerActions,
                    multiActionWithoutExit = multiActionWithoutExit,
                    columns = columns,
                    confirm = MenuDialogButton(
                        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                        MenuDialogHandler { target, response ->
                            val snapshot = api.snapshot(ownerIdFor(player))
                            val active = activeInputs[player.uniqueId]
                            if (
                                target.uniqueId == player.uniqueId &&
                                active?.token == token &&
                                active.dialogId == dialogId &&
                                !canOperateSharedActor(ownerIdFor(player), target.uniqueId)
                            ) {
                                invalidateInput(player.uniqueId)
                                return@MenuDialogHandler MenuActionResult.Ignored
                            }
                            if (
                                target.uniqueId != player.uniqueId ||
                                active?.token != token ||
                                active.dialogId != dialogId ||
                                !target.isOnline ||
                                gestureSessionId == null ||
                                snapshot?.sessionId != gestureSessionId ||
                                !canOperateSharedActor(ownerIdFor(player), target.uniqueId)
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
                            clearInputState(player.uniqueId, token)
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                    cancel = MenuDialogButton(
                        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL),
                        MenuDialogHandler { target, _ ->
                            if (
                                target.uniqueId != player.uniqueId ||
                                activeInputs[player.uniqueId]?.token != token ||
                                activeInputs[player.uniqueId]?.dialogId != dialogId
                            ) {
                                return@MenuDialogHandler MenuActionResult.Ignored
                            }
                            clearInputState(player.uniqueId, token)
                            MenuActionResult.Success(MenuUpdate.Close)
                        },
                    ),
                ),
            )
        } catch (failure: Throwable) {
            // show()はPaperのDialog生成失敗を例外で返すため、表示されていないDialogを
            // 後続のclose処理が所有中と誤認しないよう、同じ世代だけをロールバックします。
            if (activeInputs[player.uniqueId]?.token == token) invalidateInput(player.uniqueId)
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
            body = CommandDialogSpecs.coordinateBody(player, x, y, z),
            inputs = CommandDialogSpecs.coordinateInputs(player, x, y, z),
        ) { response ->
            val xValue = CommandDialogSpecs.finiteDouble(response.textValue("x").trim())
            val yValue = CommandDialogSpecs.finiteDouble(response.textValue("y").trim())
            val zValue = CommandDialogSpecs.finiteDouble(response.textValue("z").trim())
            if (xValue == null || yValue == null || zValue == null) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_COORDINATES_INVALID)
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
            body = CommandDialogSpecs.rotationBody(player, yaw, pitch),
            inputs = CommandDialogSpecs.rotationInputs(player, yaw, pitch),
        ) { response ->
            val yawValue = CommandDialogSpecs.finiteFloat(response.textValue("yaw").trim())
            val pitchValue = CommandDialogSpecs.finiteFloat(response.textValue("pitch").trim())
            if (yawValue == null || pitchValue == null) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_INVALID)
            }
            onSubmit(yawValue, pitchValue)
        }
    }

    /** 条件詳細の子設定IDを保存先のparamsキーへ変換します。 */
    private fun specSaveKey(encoded: String): String = when (encoded) {
        "condition-variable" -> "variable"
        "condition-value" -> "value"
        "condition-block" -> "block"
        "condition-item" -> "item"
        "condition-item-data" -> "itemData"
        else -> encoded
    }

    /** 専用選択画面のすべての選択を共有モデルへ適用します。 */
    private fun handleSettingAction(context: GestureGuiActionContext, player: Player) {
        if (!GestureGuiClickPolicy.isPrimaryClick(context.gesture)) return
        val ownerId = ownerIdFor(player)
        if (context.elementId == "lower-setting-back") {
            popSettingFrame(player)
            return
        }
        if (context.elementId.startsWith("lower-setting-page:")) {
            state.settingPage = context.elementId.removePrefix("lower-setting-page:").toIntOrNull() ?: return
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
        observedRevision = script.revision
        val node = script.graph.nodes[settingContext.nodeId] ?: return

        fun showSettingScreen(openChild: Boolean = false) {
            state.lowerMode = if (settingChildOpen(ownerId) || state.settingRoute.size > 1) {
                GestureLowerMode.SETTING_CHOICES
            } else {
                GestureLowerMode.SETTINGS
            }
            if (openChild && !settingChildOpen(ownerId)) ensureSettingChild(player) else updateLower(player)
        }

        /**
         * 対象の簡略三分類を保存し、必要なら距離・種類などの詳細へ進みます。
         * 移動先の「他のエンティティ」も同じ処理を使うため、インライン選択と
         * 通常の対象設定画面で細分類の保存規則が分岐しないようにします。
         */
        fun handleTargetCategory(categoryValue: String) {
            val category = runCatching { TargetCategory.valueOf(categoryValue) }.getOrNull() ?: return
            if (!CommandSettingsModel.targetCategoryAvailable(script.graph, node.id, category)) return
            val role = settingContext.role
            val current = CommandSettingsModel.targetSpec(node, role)
            val currentKind = current?.kind
            val kind = if (CommandSettingsModel.targetCategoryMatches(currentKind, category)) {
                currentKind ?: CommandSettingsModel.defaultTargetKind(category)
            } else {
                CommandSettingsModel.defaultTargetKind(category)
            }
            val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, "target:$categoryValue")
            val hasChildren = lowerPanel.hasSettingChoiceChildren(state, player, "target:$categoryValue")
            val fixedId = if (kind == TargetKind.FIXED_ENTITY) {
                current?.fixedEntityId ?: player.getTargetEntity(32)?.uniqueId ?: run {
                    player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_NO_ENTITY_IN_SIGHT))
                    return
                }
            } else null
            val entityKind = kind in setOf(TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES, TargetKind.FIXED_ENTITY)
            if (!updateSettingNode(player, settingContext.copy(role = role)) { target ->
                    CommandSettingsModel.setTargetSpec(
                        target,
                        role,
                        (current ?: TargetSpec(kind)).copy(
                            kind = kind,
                            fixedEntityId = fixedId,
                            entityType = if (entityKind) current?.entityType else null,
                        ),
                    )
                }) return
            val encodedCategory = "target:$categoryValue"
            rememberSettingNode(encodedCategory)
            when (settingSelectionAction(wasSelected, hasChildren)) {
                GestureSettingSelectionAction.ENTER_CHILD -> {
                    pushSettingFrame(
                        player,
                        GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET_FILTERS),
                        encodedCategory,
                    )
                }
                GestureSettingSelectionAction.STAY_ON_FRAME -> showSettingScreen()
            }
        }

        when (screen) {
            GestureSettingScreen.TARGET -> {
                if (group != "target") return
                handleTargetCategory(value)
            }
            GestureSettingScreen.TARGET_FILTERS -> {
                val role = settingContext.role
                val current = CommandSettingsModel.targetSpec(node, role) ?: TargetSpec(TargetKind.NEAREST_ENTITY)
                if (group == "kind") {
                    val category = CommandSettingsModel.targetCategory(current.kind)
                    val selectedKind = CommandSettingsModel.targetKinds(category)
                        .firstOrNull { it.name == value }
                        ?: return
                    val fixedId = if (selectedKind == TargetKind.FIXED_ENTITY) {
                        current.fixedEntityId ?: player.getTargetEntity(32)?.uniqueId ?: run {
                            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_NO_ENTITY_IN_SIGHT))
                            return
                        }
                    } else null
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setTargetSpec(
                                it,
                                role,
                                current.copy(
                                    kind = selectedKind,
                                    fixedEntityId = fixedId,
                                    entityType = if (selectedKind in setOf(TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES)) current.entityType else null,
                                ),
                            )
                        }) showSettingScreen()
                    return
                }
                if (group != "filter") return
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
                    "entityType", "distance", "range", "limit", "tag", "name" -> {
                        if (value == "distance") {
                            beginSettingDistanceInput(
                                player,
                                current.minimumDistance,
                                current.maximumDistance,
                            ) { minimum, maximum ->
                                if (!updateSettingNode(player, settingContext) {
                                        val latest = CommandSettingsModel.targetSpec(it, role)
                                            ?: TargetSpec(current.kind)
                                        CommandSettingsModel.setTargetSpec(
                                            it,
                                            role,
                                            latest.copy(
                                                minimumDistance = minimum,
                                                maximumDistance = maximum,
                                            ),
                                        )
                                    }) {
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                                } else null
                            }
                            return
                        }
                        if (value == "range") {
                            beginSettingRangeInput(
                                player,
                                current.dx,
                                current.dy,
                                current.dz,
                            ) { dx, dy, dz ->
                                if (!updateSettingNode(player, settingContext) {
                                        val latest = CommandSettingsModel.targetSpec(it, role)
                                            ?: TargetSpec(current.kind)
                                        CommandSettingsModel.setTargetSpec(
                                            it,
                                            role,
                                            latest.copy(dx = dx, dy = dy, dz = dz),
                                        )
                                    }) {
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                                } else null
                            }
                            return
                        }
                        // インベントリGUIと同一の入力仕様（ラベル・maxLength・検証）を使います。
                        val spec = CommandDialogSpecs.targetFilter(value)
                            ?: return
                        val initial = when (value) {
                            "limit" -> current.limit?.toString()
                            "entityType" -> current.entityType
                            "tag" -> current.tag
                            else -> current.name
                        }.orEmpty()
                        beginSettingInput(
                            player,
                            spec,
                            initial,
                            suggestionParameter = value.takeIf { it == "entityType" },
                        ) { raw ->
                            val parsedText = raw.trim().takeIf(String::isNotEmpty)
                            val parsedLimit = parsedText?.toIntOrNull()
                            if (!updateSettingNode(player, settingContext) {
                                val latest = CommandSettingsModel.targetSpec(it, role)
                                    ?: TargetSpec(current.kind)
                                val updated = when (value) {
                                    "entityType" -> latest.copy(entityType = parsedText)
                                    "limit" -> latest.copy(limit = parsedLimit)
                                    "tag" -> latest.copy(tag = parsedText)
                                    else -> latest.copy(name = parsedText)
                                }
                                CommandSettingsModel.setTargetSpec(it, role, updated)
                            }) return@beginSettingInput KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                            null
                        }
                    }
                }
            }
            GestureSettingScreen.POSITION -> {
                // 移動先の「他のエンティティ」はPOSITION画面の右下へインライン表示した
                // 対象三分類です。対象設定画面へ遷移せず、ここでも同じ保存・二段階選択を使います。
                if (settingContext.role == CommandSettingRole.DESTINATION && group == "target") {
                    handleTargetCategory(value)
                    return
                }
                if (group != "position") return
                val kind = runCatching { PositionKind.valueOf(value) }.getOrNull() ?: return
                val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                rememberSettingNode(encoded)
                if (kind == PositionKind.TARGET && settingContext.role == CommandSettingRole.DESTINATION) {
                    // 「移動先→別エンティティ」は位置ではなく対象ドメインです。
                    // PositionSpec(TARGET)へ保存すると、対象の種類・距離が失われるため、
                    // targetSpecの共通setterへ初期対象だけを渡して親子の境界を保ちます。
                    val currentTarget = CommandSettingsModel.targetSpec(node, settingContext.role)
                        ?: TargetSpec(TargetKind.INHERITED_TARGET)
                    if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setTargetSpec(it, settingContext.role, currentTarget)
                        }) return
                    // 対象三分類は同じPOSITION画面の右下へ表示します。ここで子画面を
                    // 開かないため、座標設定や戻る操作の位置も安定します。
                    showSettingScreen()
                    return
                }
                if (kind == PositionKind.COORDINATES) {
                    val current = CommandSettingsModel.positionSpec(node, settingContext.role)
                    if (!wasSelected) {
                        val location = player.location
                        if (!updateSettingNode(player, settingContext) {
                                CommandSettingsModel.setPositionSpec(
                                    it,
                                    settingContext.role,
                                    PositionSpec(PositionKind.COORDINATES, x = location.x, y = location.y, z = location.z),
                                )
                            }) return
                        // 一回目は方式だけを選択し、二回目に入力Dialogを開きます。
                        showSettingScreen()
                        return
                    }
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
                val facingRole = settingContext.role ?: CommandSettingRole.CONTEXT_FACING
                if (kind == FacingKind.COORDINATES) {
                    val current = CommandSettingsModel.facingSpec(node, facingRole)
                    showCoordinateSettingDialog(
                        player,
                        current?.x ?: player.location.x,
                        current?.y ?: player.location.y,
                        current?.z ?: player.location.z,
                    ) { x, y, z ->
                        if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setFacingSpec(it, FacingSpec(kind, x = x, y = y, z = z), facingRole)
                        }) return@showCoordinateSettingDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        showSettingScreen()
                        null
                    }
                    return
                }
                if (kind == FacingKind.ROTATION) {
                    val current = CommandSettingsModel.facingSpec(node, facingRole)
                    showRotationSettingDialog(
                        player,
                        current?.yaw ?: player.location.yaw,
                        current?.pitch ?: player.location.pitch,
                    ) { yaw, pitch ->
                        if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setFacingSpec(it, FacingSpec(kind, yaw = yaw, pitch = pitch), facingRole)
                        }) return@showRotationSettingDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        showSettingScreen()
                        null
                    }
                    return
                }
                val location = player.location
                val spec = if (kind == FacingKind.CAPTURED) FacingSpec(kind, yaw = location.yaw, pitch = location.pitch)
                else FacingSpec(kind)
                if (updateSettingNode(player, settingContext) { CommandSettingsModel.setFacingSpec(it, spec, facingRole) }) {
                    showSettingScreen()
                }
            }
            GestureSettingScreen.CONDITION_KIND -> {
                if (group != "condition-kind") return
                val kind = runCatching { ConditionKind.valueOf(value) }.getOrNull() ?: return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "kind", kind.name)
                    }) showSettingScreen()
            }
            GestureSettingScreen.CONDITION_DETAIL -> {
                when (encoded) {
                    "condition-target" -> {
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
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
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
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
                                val next = when (it.string("sneaking")) {
                                    "" -> "true"
                                    "true" -> "false"
                                    else -> ""
                                }
                                CommandSettingsModel.setParameter(it, "sneaking", next)
                            }) updateLower(player)
                    }
                    "condition-operator" -> {
                        val operators = listOf("==", "!=", ">", ">=", "<", "<=")
                        if (updateSettingNode(player, settingContext) {
                                val current = operators.indexOf(it.string("operator", "==")).coerceAtLeast(0)
                                CommandSettingsModel.setParameter(it, "operator", operators[(current + 1) % operators.size])
                            }) updateLower(player)
                    }
                    "condition-variable", "condition-value", "condition-block", "condition-item" -> {
                        if (encoded == "condition-item") {
                            applyHeldItem(player, settingContext)
                            updateLower(player)
                            return
                        }
                        if (encoded == "condition-block") {
                            applyHeldBlock(player, settingContext)
                            return
                        }
                        // インベントリGUIと同一の入力仕様（ラベル・maxLength・検証）を使います。
                        val spec = when (encoded) {
                            "condition-variable" -> CommandDialogSpecs.variableName
                            "condition-value" -> CommandDialogSpecs.conditionValue
                            else -> return
                        }
                        val saveKey = specSaveKey(encoded)
                        beginSettingInput(player, spec, node.string(saveKey)) { raw ->
                            if (!updateSettingNode(player, settingContext) {
                                    CommandSettingsModel.setParameter(it, saveKey, raw)
                                }) {
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                            } else null
                        }
                    }
                }
            }
            GestureSettingScreen.DISPLAY_MODE -> {
                if (group != "display" || value !in setOf("tellraw", "title", "subtitle", "actionbar")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "mode", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.BLOCK_OPERATION -> {
                if (group != "block" || value !in setOf("setblock", "fill")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "operation", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.ENTITY_ACTION -> {
                if (group != "action" || value !in setOf("ride", "dismount", "equip", "tag")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "action", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.ENTITY_EQUIPMENT_SLOT -> {
                if (group != "equipmentSlot" || value !in setOf("HAND", "OFF_HAND", "HEAD", "CHEST", "LEGS", "FEET")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "slot", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.ENTITY_OVERWRITE -> {
                if (group != "overwrite" || value !in setOf("true", "false")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "overwrite", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.ENTITY_TAG_OPERATION -> {
                if (group != "tagOperation" || value !in setOf("add", "remove")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "tagOperation", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.CAMERA_SHAKE_TYPE -> {
                if (group != "shake" || value !in setOf("positional", "rotational")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "shakeType", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.SOUND_SCOPE -> {
                if (group != "soundScope" || value !in setOf("CONTEXT", "WORLD")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "soundScope", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.VARIABLE_TYPE -> {
                if (group != "type") return
                val type = runCatching { VariableType.valueOf(value) }.getOrNull() ?: return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "type", type.name)
                    }) showSettingScreen()
            }
            GestureSettingScreen.VARIABLE_OPERATION -> {
                if (group != "operation") return
                val operation = runCatching { VariableOperation.valueOf(value) }.getOrNull() ?: return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "operation", operation.name)
                    }) showSettingScreen()
            }
            GestureSettingScreen.VARIABLE_CHANGE_MODE -> {
                if (group != "changeMode") return
                val mode = runCatching { VariableChangeMode.valueOf(value) }.getOrNull() ?: return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "changeMode", mode.name)
                    }) showSettingScreen()
            }
            GestureSettingScreen.VARIABLE_VALUE -> {
                if (group != "value") return
                when (value) {
                    "direct" -> beginSettingInput(
                        player,
                        CommandDialogSpecs.field(node, "value") ?: return,
                        node.string("value"),
                    ) { raw ->
                        if (!updateSettingNode(player, settingContext) {
                                CommandSettingsModel.setParameter(it, "value", raw)
                            }) {
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        } else null
                    }
                    "iteration" -> if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, "value", "\$current_iteration_value")
                        }) showSettingScreen()
                    "count" -> if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, "value", "\$current_loop_count")
                        }) showSettingScreen()
                }
            }
            GestureSettingScreen.FOR_SOURCE -> {
                if (group != "source" || value !in setOf("FIXED", "WORLD")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, fieldKey, value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.INCLUSIVE_END -> {
                if (group != "inclusive") return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, fieldKey, value.toBoolean().toString())
                    }) showSettingScreen()
            }
            GestureSettingScreen.CONTEXT_OVERRIDE -> {
                when (value) {
                    "executor", "target" -> {
                        val role = if (value == "executor") CommandSettingRole.CONTEXT_EXECUTOR else CommandSettingRole.CONTEXT_TARGET
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
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
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
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
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
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
                    "inherit" -> if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.clearContextOverride(it)
                    }) showSettingScreen()
                }
            }
        }
    }

    private fun handleUpperAction(context: GestureGuiActionContext) {
        val player = Bukkit.getPlayer(context.actorId) ?: return
        val ownerId = context.ownerId
        if (!canOperateSharedActor(ownerId, context.actorId)) return
        // 画面操作が発生した時点で、古いダイアログ入力を無効化します。
        // close/open以外の遷移でも遅延コールバックが設定を書き換えないようにします。
        invalidateInput(player.uniqueId)
        when {
            context.elementId.startsWith("node-reorder:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val encoded = context.elementId.removePrefix("node-reorder:")
                val directionName = encoded.substringBefore(":")
                val nodeId = runCatching { UUID.fromString(encoded.substringAfter(":")) }.getOrNull() ?: return
                val direction = runCatching {
                    GraphEditor.ReorderDirection.valueOf(directionName.uppercase())
                }.getOrNull() ?: return
                val reordered = runCatching {
                    CommandSettingsModel.updateGraph(
                        plugin,
                        state.scriptId,
                        player.uniqueId,
                        expectedRevision = expectedMutationRevision(player),
                    ) { candidateGraph ->
                        if (GraphEditor.swapAdjacent(candidateGraph, nodeId, direction)) true else null
                    }
                }.getOrElse { failure ->
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "ノード入れ替えを保存できませんでした: script=${state.scriptId} node=$nodeId direction=$direction",
                        failure,
                    )
                    return
                }
                if (reordered != true) return
                // 入れ替え後も同じノードを選択し続け、設定パネルの対象を失わせません。
                state.selectedNodeId = nodeId
                updateUpper(player)
                updateLower(player)
            }
            context.elementId.startsWith("node:") -> {
                val nodeId = runCatching { UUID.fromString(context.elementId.removePrefix("node:")) }.getOrNull() ?: return
                // 画面更新後に削除されたノードからの遅延入力は、選択も効果音も発生させません。
                val script = plugin.scripts.load(state.scriptId) ?: return
                if (script.graph.nodes[nodeId] == null) return
                if (GestureGuiClickPolicy.isPrimaryClick(context.gesture)) {
                    if (settingChildOpen(ownerId)) {
                        api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                    }
                    state.selectedNodeId = nodeId
                    state.selectedAddPoint = null
                    state.selectedInsertionCandidatePoint = null
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
            }
            context.elementId == "viewport-empty" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                if (settingChildOpen(ownerId)) {
                    api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                }
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                state.confirmNodeId = null
                state.pendingInsertion = null
                clearSettingState()
                state.lowerMode = GestureLowerMode.SETTINGS
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "nav-zoom-in" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val next = (state.zoomLevel + 1).coerceAtMost(GestureEditorLayout.MAX_ZOOM_LEVEL)
                if (next != state.zoomLevel) {
                    setZoomLevel(next)
                    updateUpper(player)
                }
            }
            context.elementId == "nav-zoom-out" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val next = (state.zoomLevel - 1).coerceAtLeast(GestureEditorLayout.MIN_ZOOM_LEVEL)
                if (next != state.zoomLevel) {
                    setZoomLevel(next)
                    updateUpper(player)
                }
            }
            context.elementId == "nav-zoom-reset" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                if (state.zoomLevel != GestureEditorLayout.INITIAL_ZOOM_LEVEL) {
                    setZoomLevel(GestureEditorLayout.INITIAL_ZOOM_LEVEL)
                    updateUpper(player)
                }
            }
            context.elementId.startsWith("nav-") && context.elementId != "nav-close" &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val delta = when (context.elementId) {
                    "nav-up" -> MapPoint(0, -1)
                    "nav-down" -> MapPoint(0, 1)
                    "nav-left" -> MapPoint(-1, 0)
                    "nav-right" -> MapPoint(1, 0)
                    else -> return
                }
                val layout = currentViewportLayout() ?: return
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
            context.elementId == "back-to-start" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                // 最も先頭にある追加ポイントをビューに含めるよう原点を調整
                // 挿入候補表示中は、後続ノードを右へ移動させた仮想レイアウトを使います。
                // 永続グラフだけで原点を決めると、候補アイコンと表示範囲の基準が分岐します。
                val layout = currentViewportLayout() ?: return
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
                updateUpper(player)
                updateLower(player)
            }
            context.elementId.startsWith("add:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                // addポイントの挿入先情報を保持し、下部をPICKERへ切り替える
                val script = plugin.scripts.load(state.scriptId) ?: return
                if (settingChildOpen(ownerId)) {
                    api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
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
                state.lowerMode = GestureLowerMode.PICKER
                state.pickerCategory = 0
                state.pickerPage = 0
                updateUpper(player)
                updateLower(player)
            }
            context.elementId.startsWith("path:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                if (settingChildOpen(ownerId)) {
                    api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
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
                state.lowerMode = GestureLowerMode.PICKER
                state.pickerCategory = 0
                state.pickerPage = 0
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "lower-script-name" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                showProgramNameDialog(player)
            }
            context.elementId == "lower-script-timer" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                showTimerSettingDialog(player)
            }
            context.elementId.startsWith("lower-tab:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val index = context.elementId.removePrefix("lower-tab:").toIntOrNull() ?: return
                openSettingsTab(player, index)
            }
            context.elementId.startsWith("lower-settings-page:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val page = context.elementId.removePrefix("lower-settings-page:").toIntOrNull() ?: return
                if (settingChildOpen(ownerId)) {
                    api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                }
                state.settingsPage = page
                // 専用選択画面から設定ページャーを押した場合も、古い専用画面を
                // 残さず、対応するタブ一覧へ戻します。これがページング重複を防ぎます。
                clearSettingState()
                state.settingsTab = page * SETTINGS_PAGE_SIZE
                state.lowerMode = GestureLowerMode.SETTINGS
                updateLower(player)
            }
            context.elementId.startsWith("lower-setting-") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                handleSettingAction(context, player)
            }
            context.elementId.startsWith("lower-edit:") &&
                context.gesture in GestureGuiClickPolicy.MAIN_HAND -> {
                val fieldKey = context.elementId.removePrefix("lower-edit:")
                beginSelectedFieldEdit(player, fieldKey)
            }
            context.elementId == "lower-item-get" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
                val item = configuredItem(node) ?: return
                player.inventory.addItem(item.clone()).values.forEach { overflow ->
                    player.world.dropItemNaturally(player.location, overflow)
                }
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_ITEM_TAKEN))
            }
            context.elementId.startsWith("lower-cat:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                state.pickerCategory = context.elementId.removePrefix("lower-cat:").toIntOrNull() ?: return
                state.pickerPage = 0
                updateLower(player)
            }
            context.elementId.startsWith("lower-picker-page:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                state.pickerPage = context.elementId.removePrefix("lower-picker-page:").toIntOrNull() ?: return
                updateLower(player)
            }
            context.elementId.startsWith("lower-type:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val typeName = context.elementId.removePrefix("lower-type:")
                val type = runCatching { CommandType.valueOf(typeName) }.getOrNull() ?: return
                val script = plugin.scripts.load(state.scriptId) ?: return
                observedRevision = script.revision
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
                // 変更前の取得結果を直接壊さず、共通グラフ更新境界でレイアウト・
                // 構造検証・保存・配置表示更新まで完了させます。描画不能なグラフが
                // 発生しても保存前に破棄され、サーバーイベントへ例外を漏らしません。
                val inserted = runCatching {
                    CommandSettingsModel.updateGraph(
                        plugin,
                        script.id,
                        player.uniqueId,
                        expectedRevision = expectedMutationRevision(player),
                    ) { candidateGraph ->
                        if (type == CommandType.MERGE) {
                            // 画面表示後に別操作でグラフが変わる競合にも例外を漏らしません。
                            if (!GraphEditor.canAppendMerge(candidateGraph, target.mergeConditionId)) {
                                null
                            } else {
                                GraphEditor.appendMerge(candidateGraph, requireNotNull(target.mergeConditionId))
                            }
                        } else {
                            GraphEditor.insert(candidateGraph, target.sourceId, target.edge, type)
                        }
                    }
                }.getOrElse { failure ->
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "コマンド挿入を保存できませんでした: script=${script.id} type=$type",
                        failure,
                    )
                    refreshFromStore()
                    return
                } ?: run {
                    // 直前に別プレイヤーが構造を変更した場合、古い挿入候補を
                    // そのまま再適用せず、最新グラフを表示して選び直させます。
                    refreshFromStore()
                    return
                }
                // コマンド追加の完了音は永続化が成功した後だけ再生します。
                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f)
                state.pendingInsertion = null
                clearSettingState()
                state.selectedInsertionCandidatePoint = null
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
            context.elementId == "lower-close-picker" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                if (settingChildOpen(ownerId)) {
                    api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                }
                state.pendingInsertion = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                clearSettingState()
                state.lowerMode = GestureLowerMode.SETTINGS
                updateLower(player)
            }
            context.elementId == "nav-close" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                // 右上の閉じる操作は、親・子画面と入力claimをまとめて即時解放します。
                if (context.actorId == ownerId) {
                    closeImmediately(ownerId)
                } else {
                    // 共有画面の第三者が押しても、所有者の編集セッション全体は
                    // 閉じず、その第三者の入力claimだけを解放します。
                    api.leave(context.actorId)
                }
            }
            context.elementId == "lower-delete" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                state.confirmNodeId = state.selectedNodeId ?: return
                state.confirmKind = GestureConfirmKind.DELETE
                openConfirmChild(player)
            }
            context.elementId == "confirm-delete" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                if (state.confirmKind == GestureConfirmKind.ITEM_OVERWRITE) {
                    confirmItemOverwrite(player)
                    return
                }
                val nodeId = state.confirmNodeId ?: return
                val script = plugin.scripts.load(state.scriptId) ?: return
                observedRevision = script.revision
                // 確認後の削除も共通グラフ更新境界へ適用し、分岐・合流のレイアウト
                // 検証を通過した内容だけを正本へ保存します。失敗時は選択状態を保持します。
                val deleted = runCatching {
                    CommandSettingsModel.updateGraph(
                        plugin,
                        script.id,
                        player.uniqueId,
                        expectedRevision = expectedMutationRevision(player),
                    ) { candidateGraph ->
                        if (GraphEditor.delete(candidateGraph, nodeId)) true else null
                    }
                }.getOrElse { failure ->
                        plugin.logger.log(
                            java.util.logging.Level.WARNING,
                            "ジェスチャーGUIからのコマンド削除を保存できませんでした: script=${script.id} node=$nodeId",
                            failure,
                        )
                        refreshFromStore()
                        return
                    }
                if (deleted != true) {
                    // 他プレイヤーが先に削除したノードの確認を二重適用しません。
                    // 確認子画面と古い設定対象を閉じ、最新状態へ戻します。
                    state.confirmNodeId = null
                    state.confirmKind = GestureConfirmKind.DELETE
                    state.selectedNodeId = null
                    clearSettingState()
                    state.lowerMode = GestureLowerMode.SETTINGS
                    api.closeChild(ownerId, lowerPanel.CONFIRM_SCREEN_ID)
                    refreshFromStore()
                    return
                }
                // 削除確認を開いただけでは鳴らさず、保存成功後に削除音を再生します。
                player.playSound(player.location, Sound.BLOCK_BAMBOO_HIT, 1.0f, 1.0f)
                val settingChildWasOpen = settingChildOpen(ownerId)
                state.confirmNodeId = null
                state.confirmKind = GestureConfirmKind.DELETE
                state.selectedNodeId = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                clearSettingState()
                state.lowerMode = GestureLowerMode.SETTINGS
                api.closeChild(ownerId, lowerPanel.CONFIRM_SCREEN_ID)
                if (settingChildWasOpen) api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                updateUpper(player)
                updateLower(player)
            }
            context.elementId == "confirm-cancel" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val settingChildWasOpen = settingChildOpen(ownerId)
                state.confirmNodeId = null
                state.confirmKind = GestureConfirmKind.DELETE
                state.pendingItemContext = null
                state.pendingItemKey = null
                state.pendingItemData = null
                api.closeChild(ownerId, lowerPanel.CONFIRM_SCREEN_ID)
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
            GestureGuiScreenDefinition(
                UPPER_SCREEN_ID,
                emptyList(),
                access = screenAccess,
                accessPolicy = screenAccessPolicy,
            ),
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
            GestureGuiScreenDefinition(
                UPPER_SCREEN_ID,
                emptyList(),
                access = screenAccess,
                accessPolicy = screenAccessPolicy,
            ),
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
    private fun addNavigation(
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        layout: GraphLayout,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
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
            val delta = MapPoint(quad.second, -quad.third)
            val enabled = layout.canMove(state.origin, delta.x, delta.y, viewportWidth, viewportHeight)
            visuals.add(GestureGuiVisual.Block(
                visualId = "${quad.first}-block",
                x = nx, y = ny,
                width = s, height = s,
                blockData = Bukkit.createBlockData(
                    if (enabled) Material.CYAN_CONCRETE else DisabledGuiVisualPolicy.material,
                ),
                layer = 4,
            ))
            visuals.add(GestureGuiVisual.Text(
                visualId = "${quad.first}-glyph",
                x = nx, y = ny - 0.01,
                text = net.kyori.adventure.text.Component.text(quad.fourth).color(
                    if (enabled) NamedTextColor.WHITE else NamedTextColor.GRAY,
                ),
                size = 0.011,
                layer = 6,
            ))
            elements.add(GestureGuiElement(
                elementId = quad.first,
                bounds = navBounds(nx, ny, s),
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                // 表示後のパンや挿入候補の変化も入力時点で再評価し、無効化された
                // ナビゲーションでは効果音・Actionを発生させません。
                gestureGuard = { _, _ -> canMoveCurrentViewport(delta) },
                targetVisualId = "${quad.first}-glyph",
            ))
        }
    }

    /** 表示中の仮想挿入状態を含め、現在のビューポート移動可否を再計算します。 */
    private fun canMoveCurrentViewport(delta: MapPoint): Boolean {
        val layout = currentViewportLayout() ?: return false
        val metrics = viewportMetrics(zoomScale())
        return layout.canMove(state.origin, delta.x, delta.y, metrics.columns, metrics.rows)
    }

    /**
     * 上部ビューポートへ適用する挿入プレビューです。
     *
     * 経路セルからの「挿入」では後続ノードが右へ移動するため、仮ノードを含む
     * レイアウトで表示・経路・幅を揃え、実際の挿入位置へ候補マーカーを置きます。
     * 追加ポイントからの「追加」は追加ボタン自体が候補位置であり、既存ノードも
     * 動かないためプレビューを適用しません。描画とナビゲーションの両方がこの
     * 共通判定を使うことで、表示と入力判定の基準が分岐しません。
     */
    private fun insertionPreview(script: DiskScript): InsertionPreview? {
        val target = state.pendingInsertion ?: return null
        // 追加起点（ADDセル選択中）ではADDセルの選択glowがそのまま候補位置を示します。
        if (state.selectedInsertionCandidatePoint == null) return null
        return GraphLayoutEngine.previewInsertion(script.graph, target)
    }

    /** 描画・ナビゲーション入力で共有する、現在の永続／仮想レイアウトです。 */
    private fun currentViewportLayout(): GraphLayout? {
        val script = plugin.scripts.load(state.scriptId) ?: return null
        val persistedLayout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrNull() ?: return null
        return insertionPreview(script)?.layout ?: persistedLayout
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
                blockData = Bukkit.createBlockData(
                    if (enabled) Material.CYAN_CONCRETE else DisabledGuiVisualPolicy.material,
                ), layer = 4,
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
                // 上限／下限はstateの変更後もクリック時点で再判定し、古いviewが
                // 残った場合にも誤操作と効果音を発生させません。
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                gestureGuard = { _, _ ->
                    if (id == "nav-zoom-in") {
                        state.zoomLevel < GestureEditorLayout.MAX_ZOOM_LEVEL
                    } else {
                        state.zoomLevel > GestureEditorLayout.MIN_ZOOM_LEVEL
                    }
                },
                targetVisualId = "$id-glyph",
            ))
        }
        val resetY = GestureEditorLayout.ZOOM_TOP_Y - GestureEditorLayout.ZOOM_PITCH
        val resetEnabled = state.zoomLevel != GestureEditorLayout.INITIAL_ZOOM_LEVEL
        visuals.add(GestureGuiVisual.Block(
            visualId = "nav-zoom-reset-block", x = GestureEditorLayout.ZOOM_X, y = resetY,
            width = GestureEditorLayout.ZOOM_SIZE, height = GestureEditorLayout.ZOOM_SIZE,
            blockData = Bukkit.createBlockData(
                if (resetEnabled) Material.BROWN_CONCRETE else DisabledGuiVisualPolicy.material,
            ), layer = 4,
        ))
        visuals.add(GestureGuiVisual.Text(
            visualId = "nav-zoom-reset-glyph", x = GestureEditorLayout.ZOOM_X, y = resetY - 0.01,
            text = net.kyori.adventure.text.Component.text("↺").color(
                if (resetEnabled) NamedTextColor.WHITE else NamedTextColor.GRAY,
            ), size = 0.009, layer = 6,
        ))
        elements.add(GestureGuiElement(
            elementId = "nav-zoom-reset",
            bounds = navBounds(GestureEditorLayout.ZOOM_X, resetY, GestureEditorLayout.ZOOM_SIZE),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            gestureGuard = { _, _ -> state.zoomLevel != GestureEditorLayout.INITIAL_ZOOM_LEVEL },
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
            acceptedGestures = GestureGuiClickPolicy.CLICK,
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
        val layout = currentViewportLayout() ?: return
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
        /** CC-SystemのOPENING完了待ちを吸収する上限（13tickのアニメーションより長くします）。 */
        const val MAX_RENDER_RETRY_TICKS = 20
    }

}
