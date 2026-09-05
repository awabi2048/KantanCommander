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
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiCloseReason
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOpenOptions
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenDefinition
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiSessionState
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiSessionListener
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiView
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisibilityPolicy
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiScreenLayout
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVerticalSlot
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.execution.ExecutionFinishStatus
import me.awabi2048.kantancommander.execution.ExecutionNodeFinished
import me.awabi2048.kantancommander.execution.ExecutionResult
import me.awabi2048.kantancommander.execution.NodeExecutionOutcome
import me.awabi2048.kantancommander.data.WorldVariableUsageScanResult
import me.awabi2048.kantancommander.item.ItemStackCodec
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.WorldVariableValue
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ControlBlockStateKind
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.toggleControlBlockState
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
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
    /** PICKERで選択中のカテゴリインデックス */
    var pickerCategory: Int = 0,
    var pickerPage: Int = 0,
    /** CONFIRM対象のノードID（削除確認） */
    var confirmNodeId: UUID? = null,
    /** 確認子画面の用途（削除／アイテム・ブロック上書き） */
    var confirmKind: GestureConfirmKind = GestureConfirmKind.DELETE,
    /** アイテム上書き確認が保持する対象と完全なItemStackデータ */
    var pendingItemContext: CommandSettingContext? = null,
    var pendingItemKey: String? = null,
    var pendingItemData: String? = null,
    /** ブロック上書き確認が保持する対象と、クリック時点のブロックID */
    var pendingBlockContext: CommandSettingContext? = null,
    var pendingBlockId: String? = null,
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
    /** 設定候補だけに使うページ番号です。左側の設定タブはページ分割しません。 */
    var settingChoicePage: Int = 0,
    /** ワールド内変数一覧だけに使うページ番号です。 */
    var variablePage: Int = 0,
    /** ワールド内変数の新規作成で、Dialogへ進む前にGestureGUIで選択した型です。 */
    var pendingWorldVariableType: VariableType? = null,
    /** ワールド内変数の削除確認で、確認対象として表示している名前です。 */
    var pendingWorldVariableDeleteName: String? = null,
    /** テスト実行中だけ共有する表示・復元状態です。 */
    var testExecution: GestureTestState? = null,
)

/** 下部パネルの表示モード。各確認画面は親から分離した子画面として開きます。 */
enum class GestureLowerMode {
    SETTINGS,
    PICKER,
    SETTING_CHOICES,
    WORLD_VARIABLES,
    WORLD_VARIABLE_TYPE,
    WORLD_VARIABLE_DELETE_CONFIRM,
    CONFIRM,
    TEST_CONFIRM,
    TEST_STATUS,
    TEST_RESULT,
}

/**
 * 共有エディターを第三者が操作できる状態を、画面描画や個別イベントから独立して定義します。
 *
 * 配置物の共有画面は、所有者が編集中のノード・設定木・確認画面を持つ間に別のプレイヤーが
 * 保存操作を行うと、表示世代と入力経路の組み合わせを壊します。そのため第三者へ許可するのは、
 * ノード未選択かつ設定ルート深さ0で、子画面・保留中の遷移も存在しないルート画面だけにします。
 */
internal object GestureSharedOperationPolicy {
    fun allowsOtherPlayer(
        lowerMode: GestureLowerMode,
        settingRouteDepth: Int,
        hasSelection: Boolean,
        hasChildScreen: Boolean,
        hasPendingState: Boolean,
    ): Boolean = lowerMode == GestureLowerMode.SETTINGS &&
        settingRouteDepth == 0 &&
        !hasSelection &&
        !hasChildScreen &&
        !hasPendingState
}

/**
 * 実行前検証を下部パネルの「要確認」表示へ投影するための情報です。
 *
 * 検証結果を構造化エラーから要約し、ノードごとの要確認タブ（fieldKey）集合と、
 * スクリプト全体のタイマー要確認だけを画面側へ渡します。表示側がエラー文言を
 * 解析して意味を推測しないよう、この集約が唯一の受け渡し経路になります。
 */
data class GestureAttentionState(
    /** ノードID → そのノードで要確認となっている表示上の設定タブ（fieldKey）の集合。 */
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
    BLOCK_OVERWRITE,
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
    LOCATION,
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
    CONDITION_INVERSION,
    CAMERA_SHAKE_TYPE,
    SOUND_SCOPE,
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
 * 下部の一覧・型選択・削除確認はSETTING_CHILD_SCREEN_ID、その他のCONFIRMは
 * 専用の子画面へ切り替え、親画面の操作領域と混在させません。
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
    /** 同じ描画失敗でチャットを連続送信しないための通知済みフラグです。 */
    private var layoutFailureNoticeSent = false
    /** 現在の画面生成中に描画失敗が発生したかを、成功時の通知解除判定へ渡します。 */
    private var layoutFailureDuringCurrentRender = false
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
    /** Kantan Commander自身の配置操作権限を入力受付時点で再確認します。 */
    private val screenAccessPolicy = state.placement?.let {
        GestureGuiAccessPolicy(::canOperateSharedActor)
    }
    /** 遠距離でも共有画面の存在は確認できるよう、表示認可から距離判定を外します。 */
    private val screenVisibilityPolicy = state.placement?.let {
        GestureGuiVisibilityPolicy(::canViewSharedActor)
    }
    // 下部画面のクリックは上部と同じハンドラで処理します（タブ切替・PICKER・CONFIRMの共通ロジック）。
    private val lowerPanel = GestureLowerPanel(
        plugin,
        onAction = { ctx -> handleUpperAction(ctx) },
        screenAccess = screenAccess,
        screenAccessPolicy = screenAccessPolicy,
        screenVisibilityPolicy = screenVisibilityPolicy,
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
    /** 実行中の経過時間・現在ノードを画面へ反映する周期タスクです。 */
    private var testRefreshTask: BukkitTask? = null

    private fun canViewSharedActor(ownerId: UUID, actorId: UUID): Boolean {
        val placement = state.placement ?: return actorId == ownerId
        val owner = Bukkit.getPlayer(ownerId) ?: return false
        val actor = Bukkit.getPlayer(actorId) ?: return false
        // 共有GUIの表示も配置物を操作できるプレイヤーに限定します。
        // PlacementAccessPolicy側の追加権限・管理者例外・MyWorld建築権限の分岐を
        // ここでも再利用し、表示だけ権限判定が緩くなる入口を作らないようにします。
        // 距離判定は操作時だけへ分離し、遠ざかったプレイヤーの画面全体を消しません。
        return actor.world.uid == owner.world.uid &&
            actor.world.name == placement.world &&
            plugin.placementAccess.canManage(actor, placement.world)
    }

    /**
     * 所有者以外へ操作を渡せる、完全な深さ0の状態かを判定します。
     * CC-Systemの子画面一覧だけでは、子画面へ遷移する直前の設定木やPICKERの保留状態を
     * 見落とすため、描画状態と遷移状態をまとめて同じポリシーへ渡します。
     */
    private fun isSharedOperationRoot(ownerId: UUID): Boolean {
        val hasSelection = state.selectedNodeId != null ||
            state.selectedAddPoint != null ||
            state.selectedInsertionCandidatePoint != null ||
            state.confirmNodeId != null
        val hasPendingState = state.pendingInsertion != null ||
            state.pendingItemContext != null ||
            state.pendingItemKey != null ||
            state.pendingItemData != null ||
            state.pendingBlockContext != null ||
            state.pendingBlockId != null ||
            state.settingContext != null ||
            state.settingFieldKey != null ||
            state.settingTreePath != null ||
            state.settingScreen != null ||
            state.pendingWorldVariableType != null ||
            state.pendingWorldVariableDeleteName != null
        val hasChildScreen = api.snapshot(ownerId)?.childScreenIds?.isNotEmpty() == true
        return GestureSharedOperationPolicy.allowsOtherPlayer(
            lowerMode = state.lowerMode,
            settingRouteDepth = state.settingRoute.size,
            hasSelection = hasSelection,
            hasChildScreen = hasChildScreen,
            hasPendingState = hasPendingState,
        )
    }

    private fun canOperateSharedActor(ownerId: UUID, actorId: UUID): Boolean {
        // テスト中の中断導線は追従アンカーの状態に依存させません。オーナーが
        // 離れても「テスト実行」「閉じる」だけは受け付け、ほかの操作者は下の
        // Actionハンドラで明示的に拒否します。
        if (state.testExecution?.ownerId == actorId) return true
        val placement = state.placement ?: return actorId == ownerId
        if (!canViewSharedActor(ownerId, actorId)) return false
        if (actorId != ownerId && !isSharedOperationRoot(ownerId)) return false
        val actor = Bukkit.getPlayer(actorId) ?: return false
        // 距離判定はCC-System側の到達検査へ一任します。CC-Systemはクリックを
        // 試みた画面上の点までの距離(eye→hit)をinteraction_rangeで検査するため、
        // ここで配置ブロック起点の重複検査を行うと、追従画面がブロックから
        // 離れた位置にある場合に正当な操作まで拒否してしまいます。
        // 当たり点がない経路(Dialog確定等)では距離を検査しません。
        return actor.world.name == placement.world
    }

    internal fun canOperateSharedActor(player: Player): Boolean =
        sessionOwnerId?.let { ownerId -> canOperateSharedActor(ownerId, player.uniqueId) } == true

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
            clearPendingOverwriteState()
            state.confirmKind = GestureConfirmKind.DELETE
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
                sessionListener = object : GestureGuiSessionListener {
                    override fun onClosed(ownerId: UUID, sessionId: UUID) {
                        onGestureSessionClosed(ownerId, sessionId)
                    }

                    override fun onCloseRequested(
                        ownerId: UUID,
                        sessionId: UUID,
                        actorId: UUID,
                        reason: GestureGuiCloseReason,
                    ) {
                        if (reason == GestureGuiCloseReason.SHIFT_JUMP && actorId == ownerId) {
                            // CC-Systemが画面を閉じる直前に停止だけを確定します。
                            // SHIFT_JUMPは「閉じる」と同じく結果画面を経由しません。
                            interruptTestWithoutResult("shift_jump")
                        }
                    }
                },
                layout = layout,
                verticalSlots = listOf(GestureGuiVerticalSlot.TOP, GestureGuiVerticalSlot.MIDDLE),
                // KantanのGesture GUIでは右クリックを操作に使わず、Interactionの腕振りも抑制します。
                // Inventory GUIのクリック仕様には影響させません。
                secondaryInputEnabled = false,
                // 画面外を含む左右クリックを外部ブロック／エンティティへ漏らしません。
                suppressWorldClicks = true,
                // エディター全体の高さを0.5ブロック下げます。
                verticalOffset = EDITOR_VERTICAL_OFFSET,
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
        return runCatching {
            updateScreen(owner, buildCurrentLowerView(owner), RenderTarget.LOWER)
        }.getOrElse { failure ->
            // 下部画面の再描画は保存処理の成功条件ではありません。保存済みデータの
            // 破損でここが失敗しても、入力を「形式不正」と誤判定しないよう、GUI境界で
            // 失敗をBooleanへ閉じ込め、原因だけをログへ残します。
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "ジェスチャーGUI下部画面の生成または更新に失敗しました: script=${state.scriptId} mode=${state.lowerMode}",
                failure,
            )
            false
        }
    }

    /** 通常更新とOPENING後の再試行で、子画面の優先順位を同じにします。 */
    private fun buildCurrentLowerView(owner: Player): GestureGuiView {
        val childOpen = settingChildOpen(owner.uniqueId)
        val testChildOpen = testConfirmationChildOpen(owner.uniqueId)
        val attention = attentionState()
        return when {
            testChildOpen -> lowerPanel.buildTestConfirmationChild(state, owner)
            childOpen -> when (state.lowerMode) {
                GestureLowerMode.SETTING_CHOICES -> lowerPanel.buildSettingChild(state, owner, attention)
                GestureLowerMode.WORLD_VARIABLES -> lowerPanel.buildWorldVariablesChild(state, owner)
                GestureLowerMode.WORLD_VARIABLE_TYPE -> lowerPanel.buildWorldVariableTypeChild(state, owner)
                GestureLowerMode.WORLD_VARIABLE_DELETE_CONFIRM -> lowerPanel.buildWorldVariableDeleteConfirmationChild(state, owner)
                else -> lowerPanel.build(state, owner, attention)
            }
            else -> lowerPanel.build(state, owner, attention)
        }
    }

    /**
     * 実行前検証を、下部パネルの「要確認」表示用の情報へ要約します。
     *
     * 構造化エラー（nodeId/fieldKeys）を表示上のタブへ正規化して集約するため、表示側で
     * エラー文言を解析する必要はありません。表示時間の3項目は1つの時間タブへ投影します。
     * snapshot内のエラーは主グラフのノードへ対応しないため、存在しないノードIDは除外します。
     */
    private fun attentionState(): GestureAttentionState {
        val script = plugin.scripts.load(state.scriptId) ?: return GestureAttentionState.EMPTY
        val errors = ExecutableScriptValidator.validate(script, plugin.graphLimits())
        val fieldKeysByNode = errors
            .filter { it.nodeId != null && it.nodeId in script.graph.nodes }
            .groupBy { it.nodeId!! }
            .mapValues { (nodeId, nodeErrors) ->
                val node = script.graph.nodes.getValue(nodeId)
                nodeErrors
                    .flatMap { error ->
                        error.fieldKeys.mapNotNull { fieldKey ->
                            CommandSettingsModel.visibleAttentionFieldKey(node, fieldKey)
                        }
                    }
                    .toSet()
            }
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
            val view = buildCurrentLowerView(owner)
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

    /** 現在の正本がテスト可能かを、ボタン表示と開始時の両方で共有します。 */
    private fun testValidationErrors(): List<me.awabi2048.kantancommander.data.ScriptValidationError> {
        val script = plugin.scripts.load(state.scriptId) ?: return emptyList()
        val placement = state.placement ?: return listOf(
            me.awabi2048.kantancommander.data.ScriptValidationError(
                "root",
                null,
                emptySet(),
                "テスト対象の配置がありません",
            ),
        )
        // 実行器と同じMyWorld／ワールド変数の正本を使います。ここを構文検証だけに
        // すると、ボタン表示時は有効でも実行開始時の変数定義検証で失敗します。
        if (!plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) {
            return listOf(
                me.awabi2048.kantancommander.data.ScriptValidationError(
                    "root",
                    null,
                    emptySet(),
                    "MyWorldManagerを利用できません",
                ),
            )
        }
        val worldData = runCatching {
            MyWorldManagerApi.getWorldRepository()?.findByWorldName(placement.world)
        }.getOrNull() ?: return listOf(
            me.awabi2048.kantancommander.data.ScriptValidationError(
                "root",
                null,
                emptySet(),
                "テスト対象のMyWorldが見つかりません",
            ),
        )
        if (Bukkit.getWorld(placement.world) == null) {
            return listOf(
                me.awabi2048.kantancommander.data.ScriptValidationError(
                    "root",
                    null,
                    emptySet(),
                    "テスト対象のワールドが読み込まれていません",
                ),
            )
        }
        return ExecutableScriptValidator.validate(
            script,
            plugin.graphLimits(),
            plugin.variables.definitions(worldData.uuid),
        )
    }

    private fun canStartTest(): Boolean {
        val placement = state.placement ?: return false
        return !plugin.testExecution.isActive(placement.key) &&
            plugin.scripts.load(state.scriptId) != null &&
            testValidationErrors().isEmpty()
    }

    /** テスト確認子画面を開きます。グラフの固定は「確定」クリック時に行います。 */
    private fun openTestConfirmation(player: Player) {
        if (!canStartTest() || state.testExecution != null) return
        val current = plugin.scripts.load(state.scriptId) ?: return
        val ownerId = ownerIdFor(player)
        // テスト開始前に子画面を閉じるため、完了後は必ず通常のエディター親画面へ
        // 戻します。SETTING_CHOICES等をそのまま保存すると、子画面を閉じた後に
        // 親画面へ子画面専用の内部状態を描画してしまいます。
        val originalMode = state.lowerMode.takeIf {
            it == GestureLowerMode.SETTINGS || it == GestureLowerMode.PICKER
        } ?: GestureLowerMode.SETTINGS
        val parentView = lowerPanel.build(state, ownerPlayerFor(player), attentionState(), suppressHighlight = true)
        if (settingChildOpen(ownerId)) api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
        val placement = state.placement ?: return
        state.testExecution = GestureTestState(
            scopeKey = placement.key,
            snapshot = current.copy(graph = current.graph.deepCopy()),
            ownerId = ownerId,
            originalOrigin = state.origin,
            originalZoomLevel = state.zoomLevel,
            originalSelectedNodeId = state.selectedNodeId,
            originalLowerMode = originalMode,
            debugMode = plugin.testExecutionPreferences.debugMode(player),
            logOutput = plugin.testExecutionPreferences.logOutput(player),
        )
        state.lowerMode = GestureLowerMode.TEST_CONFIRM
        val opened = runCatching {
            openChildAndSuppressParentHighlight(
                ownerId,
                parentView,
                lowerPanel.buildTestConfirmationChild(state, ownerPlayerFor(player)),
                GestureGuiChildOptions(
                    parentScreenId = lowerPanel.LOWER_SCREEN_ID,
                    overlayMaterial = Material.RED_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "テスト実行確認子画面のオープンに失敗しました: script=${state.scriptId}",
                failure,
            )
            false
        }
        if (!opened) {
            state.testExecution = null
            state.lowerMode = originalMode
            updateLower(player)
        }
    }

    private fun testConfirmationChildOpen(ownerId: UUID): Boolean =
        api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.TEST_CONFIRM_SCREEN_ID) == true

    /** 最小倍率でマップ全体を表示し、指定ノードが端から切れない原点を求めます。 */
    private fun fitTestViewport(targetNodeId: UUID? = null) {
        val test = state.testExecution ?: return
        val layout = runCatching { GraphLayoutEngine.layout(test.snapshot.graph) }.getOrNull() ?: return
        state.zoomLevel = GestureEditorLayout.MIN_ZOOM_LEVEL
        val metrics = viewportMetrics(zoomScale())
        val target = targetNodeId?.let(layout.nodePoints::get)
        state.origin = if (target != null) {
            // 実行中ノードを中心にした「最小ズームと同じ大きさ」の近傍を
            // 毎回作り、マップ外へ出る場合だけ端へ最小限clampします。
            GestureEditorLayout.clampOrigin(
                MapPoint(
                    target.x - ((metrics.columns - 1) / 2.0).roundToInt(),
                    target.y - ((metrics.rows - 1) / 2.0).roundToInt(),
                ),
                layout,
                metrics.columns,
                metrics.rows,
            )
        } else {
            GestureEditorLayout.clampOrigin(state.origin, layout, metrics.columns, metrics.rows)
        }
    }

    private fun startTestExecution(player: Player) {
        val test = state.testExecution ?: return
        if (test.ownerId != player.uniqueId) return
        if (!canStartTest()) {
            // 確認画面を開いた後に管理経路で正本が不正化・撤去された場合は、
            // 不正な内容を実行せず、通常編集画面へ戻してテストボタンを非表示にします。
            cancelTestConfirmation(player)
            return
        }
        val current = plugin.scripts.load(state.scriptId) ?: run {
            cancelTestConfirmation(player)
            return
        }
        val origin = state.placement?.let { placement ->
            Bukkit.getWorld(placement.world)?.let { world ->
                Location(world, placement.x + 0.5, placement.y + 0.5, placement.z + 0.5)
            }
        } ?: run {
            cancelTestConfirmation(player)
            return
        }
        // 確定クリック時点で最新グラフを複製して固定します。以後のGUI／外部保存は
        // このスナップショットを変更せず、同じ配置を見ている全員へ状態を配布します。
        test.snapshot = current.copy(graph = current.graph.deepCopy())
        test.startedAtTick = plugin.server.currentTick.toLong()
        test.phase = GestureTestPhase.RUNNING
        state.lowerMode = GestureLowerMode.TEST_STATUS
        if (testConfirmationChildOpen(test.ownerId)) {
            api.closeChild(test.ownerId, lowerPanel.TEST_CONFIRM_SCREEN_ID)
        }
        fitTestViewport()
        startTestRefreshTask()
        val observer = GestureTestExecutionObserver(
            test,
            onChanged = { onTestStateChanged() },
            onLog = { event -> if (test.logOutput) sendTestLog(event) },
            onResult = { result ->
                if (result.status == ExecutionFinishStatus.CANCELLED && result.reason != "manual_stop") {
                    discardTestWithoutResult()
                } else {
                    finishTest(result)
                }
            },
        )
        val started = plugin.testExecution.startTest(
            scopeKey = test.scopeKey,
            script = test.snapshot,
            ownerId = test.ownerId,
            origin = origin,
            debugMode = test.debugMode,
            observer = observer,
            // 結果画面への反映は observer.onFinished が担当する。ここでは実行器側の
            // 協調状態だけを解放し、UI状態を二重に終了させない。
            onFinished = {},
        )
        if (!started) {
            finishTest(
                ExecutionResult(
                    status = ExecutionFinishStatus.FAILURE,
                    elapsedTicks = 0L,
                    successfulNodeCount = 0,
                    attemptCount = 0,
                    reason = "test_start_rejected",
                ),
            )
        }
    }

    private fun startTestRefreshTask() {
        testRefreshTask?.cancel()
        testRefreshTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val test = state.testExecution
            if (test?.phase != GestureTestPhase.RUNNING) {
                testRefreshTask?.cancel()
                testRefreshTask = null
                return@Runnable
            }
            test.elapsedTicks = (plugin.server.currentTick.toLong() - test.startedAtTick).coerceAtLeast(0L)
            onTestStateChanged()
        }, 1L, 1L)
    }

    private fun onTestStateChanged() {
        val owner = sessionOwnerId?.let(Bukkit::getPlayer)?.takeIf(Player::isOnline) ?: return
        state.testExecution?.currentNodeId?.let(::fitTestViewport)
        updateUpper(owner)
        updateLower(owner)
    }

    private fun finishTest(result: ExecutionResult) {
        val test = state.testExecution ?: return
        test.result = result
        test.elapsedTicks = result.elapsedTicks
        test.failedNodeId = result.failedNodeId ?: test.failedNodeId
        test.phase = GestureTestPhase.RESULT
        test.currentNodeId = test.failedNodeId
        state.lowerMode = GestureLowerMode.TEST_RESULT
        testRefreshTask?.cancel()
        testRefreshTask = null
        onTestStateChanged()
    }

    /** 確認子画面を閉じ、テストを開始していない通常編集状態へ戻します。 */
    private fun cancelTestConfirmation(player: Player) {
        val test = state.testExecution ?: return
        if (test.phase != GestureTestPhase.CONFIRM) return
        if (testConfirmationChildOpen(test.ownerId)) {
            api.closeChild(test.ownerId, lowerPanel.TEST_CONFIRM_SCREEN_ID)
        }
        state.testExecution = null
        state.lowerMode = test.originalLowerMode
        updateUpper(player)
        updateLower(player)
    }

    /** 終了要求で結果画面を出さないテストを、表示状態ごと破棄します。 */
    private fun discardTestWithoutResult() {
        val test = state.testExecution ?: return
        testRefreshTask?.cancel()
        testRefreshTask = null
        state.testExecution = null
        state.lowerMode = test.originalLowerMode
        onTestStateChanged()
    }

    private fun interruptTestWithoutResult(reason: String) {
        val test = state.testExecution ?: return
        plugin.testExecution.cancel(test.scopeKey, showResult = false, reason = reason)
        // Coordinatorがすでに完了させた競合状態でも、必ずローカル表示を
        // 通常画面へ戻します。通常はcancel()内の観測者が先に同じ処理を行います。
        if (state.testExecution === test) discardTestWithoutResult()
    }

    private fun interruptTestWithResult() {
        val test = state.testExecution ?: return
        plugin.testExecution.cancel(test.scopeKey, showResult = true, reason = "manual_stop")
    }

    /** 結果画面の完了で、テスト開始前の編集表示へ戻します。 */
    private fun completeTest(player: Player) {
        val test = state.testExecution ?: return
        if (test.ownerId != player.uniqueId || test.phase != GestureTestPhase.RESULT) return
        if (testConfirmationChildOpen(test.ownerId)) {
            api.closeChild(test.ownerId, lowerPanel.TEST_CONFIRM_SCREEN_ID)
        }
        state.origin = test.originalOrigin
        state.zoomLevel = test.originalZoomLevel
        state.selectedNodeId = test.originalSelectedNodeId
        state.lowerMode = test.originalLowerMode
        state.testExecution = null
        testRefreshTask?.cancel()
        testRefreshTask = null
        updateUpper(player)
        updateLower(player)
    }

    private fun sendTestLog(event: ExecutionNodeFinished) {
        val ownerId = state.testExecution?.ownerId ?: return
        val player = Bukkit.getPlayer(ownerId)?.takeIf(Player::isOnline) ?: return
        if (!player.isOnline) return
        val command = KcI18n.text(player, event.nodeType.key)
        val key = if (event.outcome == NodeExecutionOutcome.FAILED) {
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_LOG_FAILURE
        } else {
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_LOG_SUCCESS
        }
        val text = KcI18n.text(
            player,
            key,
            mapOf("attempt" to event.attemptNumber, "command" to command),
        )
        val detail = buildString {
            event.detail?.takeIf(String::isNotBlank)?.let(::append)
            event.reason?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append('\n')
                append(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_CAUSE))
                    .append(": ")
                    .append(it)
            }
        }
        val component = Component.text(text).takeIf { detail.isBlank() } ?:
            Component.text(text).hoverEvent(
                net.kyori.adventure.text.event.HoverEvent.showText(Component.text(detail)),
            )
        player.sendMessage(component)
    }

    fun openConfirmChild(player: Player) {
        val ownerId = ownerIdFor(player)
        if (api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.CONFIRM_SCREEN_ID) == true) return
        val settingChildWasOpen = settingChildOpen(ownerId)
        val owner = ownerPlayerFor(player)
        val attention = attentionState()
        // 確認画面も下部画面の子画面です。openChild前の親ビューからハイライトを
        // 外しておくことで、親がメイン画面でも設定子画面でも同じ抑制経路を通します。
        val parentView = if (settingChildWasOpen) {
            lowerPanel.buildSettingChild(state, owner, attention, suppressHighlight = true)
        } else {
            lowerPanel.build(state, owner, attention, suppressHighlight = true)
        }
        state.lowerMode = GestureLowerMode.CONFIRM
        val view = lowerPanel.build(state, owner, attention)
        val opened = runCatching {
            openChildAndSuppressParentHighlight(
                ownerId,
                parentView,
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
        }.getOrElse { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "確認子画面のオープンに失敗しました: script=${state.scriptId}",
                failure,
            )
            false
        }
        if (!opened) {
            state.confirmKind = GestureConfirmKind.DELETE
            state.confirmNodeId = null
            clearPendingOverwriteState()
            state.lowerMode = if (settingChildWasOpen) GestureLowerMode.SETTING_CHOICES else GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    private fun settingChildOpen(ownerId: UUID): Boolean =
        api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.SETTING_CHILD_SCREEN_ID) == true

    /**
     * 下部画面へ子画面を積み、子画面生成後に親ビューのハイライトを一括解除します。
     *
     * CC-SystemのopenChildは親を背面へ残すため、子ビューからハイライトを除くだけでは
     * 親の描画済み装飾が残ります。設定詳細・削除確認・上書き確認を個別対応せず、
     * 親ビューをハイライトなしで差分更新する責務をここへ集約します。
     */
    private fun openChildAndSuppressParentHighlight(
        ownerId: UUID,
        parentView: GestureGuiView,
        childView: GestureGuiView,
        options: GestureGuiChildOptions,
    ): Boolean {
        val opened = api.openChild(ownerId, childView, options)
        if (!opened) return false
        if (!api.updateScreen(ownerId, parentView)) {
            plugin.logger.warning(
                "子画面の親ビューからハイライトを解除できませんでした: " +
                    "parent=${parentView.definition.screenId} child=${childView.definition.screenId}",
            )
        }
        return true
    }

    /** 個別設定子画面を開き、既に開いている場合は差分更新だけを行います。 */
    private fun ensureSettingChild(player: Player, parentView: GestureGuiView? = null) {
        state.lowerMode = GestureLowerMode.SETTING_CHOICES
        val ownerId = ownerIdFor(player)
        if (settingChildOpen(ownerId)) {
            updateLower(player)
            return
        }
        val owner = ownerPlayerFor(player)
        val attention = attentionState()
        val effectiveParentView = parentView ?: lowerPanel.build(
            state,
            owner,
            attention,
            suppressHighlight = true,
        )
        val opened = runCatching {
            openChildAndSuppressParentHighlight(
                ownerId,
                effectiveParentView,
                lowerPanel.buildSettingChild(state, owner, attention),
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

    /** 指定操作者の入力画面世代だけを破棄します。成功／キャンセル後の通常経路で使います。 */
    private fun clearInputState(playerId: UUID, token: UUID? = null) {
        val current = activeInputs[playerId] ?: return
        if (token != null && current.token != token) return
        activeInputs.remove(playerId)
    }

    /**
     * 現在の入力画面を物理的にも閉じてから入力世代を破棄します。
     * 共有Gesture画面では入力画面の操作者とセッション所有者が異なるため、
     * 両者を混同して古い入力画面を残さないようにします。
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
                    plugin.logger.warning("Kantan Commanderの入力画面終了に失敗しました: ${failure.message}")
                }
            }
        }
    }

    /** セッション終了や共有正本の再同期時に、全操作者の古い入力画面を回収します。 */
    private fun invalidateInputs() {
        activeInputs.keys.toList().forEach(::invalidateInput)
    }

    /**
     * Gestureセッション終了時のローカル状態を一箇所で解放します。
     * 入力画面はIDと表示所有者をCC-System側で照合し、別機能の入力画面を閉じないようにします。
     * Facade通知はgestureSessionIdをまだ保持した状態で行い、旧通知が新エディターを
     * 消さないようFacade側でインスタンスとセッションIDを照合できるようにします。
     */
    private fun detachLocalSession(ownerId: UUID, sessionId: UUID?) {
        if (sessionId != null && gestureSessionId != sessionId) return
        invalidateInputs()
        // オーナー退出時は実行本体を止めませんが、この画面専用の再描画タスクは
        // セッションが無いため解放します。テスト結果は、オーナーがいない間は
        // 画面へ投影せず、処理自体だけをCoordinator側で継続します。
        testRefreshTask?.cancel()
        testRefreshTask = null
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

    /**
     * 上部画面の構築全体を保護します。
     *
     * レイアウト変換が成功しても、画面要素IDの重複やCC-Systemの画面定義検証で
     * 例外になる可能性があります。画面定義の生成を呼び出し側へ漏らすと、保存後の
     * refreshだけが失敗して操作者には無反応に見えるため、描画不能画面へ退避し、
     * レイアウト変換失敗と同じ通知経路へ集約します。
     */
    private fun buildUpperViewport(player: Player): GestureGuiView =
        try {
            buildUpperViewportContent(player)
        } catch (failure: RuntimeException) {
            reportLayoutFailure(
                player,
                "ジェスチャーGUIの画面定義生成に失敗しました: script=${state.scriptId}",
                failure,
            )
            layoutErrorView(player)
        }

    private fun buildUpperViewportContent(player: Player): GestureGuiView {
        layoutFailureDuringCurrentRender = false
        val test = state.testExecution
        val testActive = test?.phase == GestureTestPhase.RUNNING || test?.phase == GestureTestPhase.RESULT
        val script = test?.snapshot?.takeIf { testActive } ?: plugin.scripts.load(state.scriptId) ?: return emptyView()
        if (!testActive) observedRevision = script.revision
        val persistedLayout = runCatching { GraphLayoutEngine.layout(script.graph) }
            .getOrElse { failure ->
                reportLayoutFailure(
                    player,
                    "ジェスチャーGUIの経路描画でレイアウトを生成できません: script=${script.id}",
                    failure,
                )
                return layoutErrorView(player)
            }
        // 挿入プレビューは「経路クリックによる挿入」のときだけ適用します。
        // 追加ポイントからの追加は、追加ボタン自体が候補位置であり既存ノードが
        // 動かないため、仮ノード入りレイアウトや候補マーカーは二重表示になります。
        val insertionPreview = if (testActive) null else insertionPreview(script, player)
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
        if (!testActive) {
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
        }

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
                        val isSelected = !testActive && state.selectedNodeId == node.id
                        val testGlowColor = when {
                            test?.failedNodeId == node.id -> Color.fromRGB(255, 85, 85).asARGB()
                            test?.phase == GestureTestPhase.RUNNING && test.currentNodeId == node.id ->
                                Color.fromRGB(85, 255, 255).asARGB()
                            test?.successfulNodeIds?.contains(node.id) == true -> Color.fromRGB(85, 255, 85).asARGB()
                            else -> null
                        }
                        val glowColor = testGlowColor ?: if (isSelected) Color.YELLOW.asARGB() else null
                        val incomplete = node.id in incompleteNodeIds
                        val backgroundMaterial = when {
                            incomplete -> Material.ORANGE_CONCRETE
                            else -> Material.LIGHT_GRAY_CONCRETE
                        }
                        val statusLine = when {
                            incomplete -> Component.text(
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET),
                                NamedTextColor.RED,
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
                        // 挿入プレビュー中も既存ノードの入力要素を残します。仮ノードを
                        // 含む再レイアウト後の座標へ同じnodeIdを結び付けることで、
                        // 旧選択の解除と新ノードの選択を1クリックで完了させます。
                        if (!testActive) elements.add(GestureGuiElement(
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
                        if (!testActive && insertionPreview == null && isSelected && node.type !in setOf(
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
                    if (!testActive && insertionPreview == null) {
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
                    if (!testActive && insertionPreview == null && cell.kind == MapCellKind.LOOP_RETURN_PATH) {
                        // 戻り経路は処理の流れを示す表示専用要素です。クリック操作は受け付けず、
                        // ホバー時だけ「戻って処理を繰り返します」と説明します。矢印と同じ論理セルを
                        // 当たり判定に使うため、パン・ズーム後も水色経路上の説明がずれません。
                        elements.add(GestureGuiElement(
                            // 経路要素の名前空間へ統一します。戻り経路だけ別の接頭辞にすると、
                            // 表示側のpath-判定とは一致しても、当たり判定側の縮尺・クリップ判定から
                            // 漏れてしまいます。通常経路と同じpath:配下に置くことで、表示と入力の
                            // 座標変換を同じ規則で適用します。
                            elementId = "path:return:$gx:$gy",
                            bounds = rect(cx, cy, metrics.pitchX, metrics.pitchY),
                            acceptedGestures = emptySet(),
                            hoverText = GestureGuiHoverText(
                                text = Component.text(
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_LOOP_RETURN_HOVER),
                                ),
                                x = cx,
                                y = cy + metrics.pathThickness,
                                size = 0.0055,
                                lineWidth = 120,
                            ),
                        ))
                    } else {
                        // 追加ポイント直前の経路は「クリックで挿入」を表示しません。
                        val hasAddNeighbor = projection.hasNeighborOfKind(localPoint, MapCellKind.ADD)
                        if (!testActive && insertionPreview == null && !hasAddNeighbor && cell.insertionTarget != null) {
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
        }

        val testPathGlow: ((MapPoint, MapCell) -> Int?)? = test?.takeIf { testActive }?.let { activeTest ->
            val cyanGlow = Color.fromRGB(85, 255, 255).asARGB()
            val greenGlow = Color.fromRGB(85, 255, 85).asARGB()
            val glow: (MapPoint, MapCell) -> Int? = { _, cell ->
                when {
                    activeTest.loopReturnActive && cell.kind == MapCellKind.LOOP_RETURN_PATH -> cyanGlow
                    cell.insertionTarget?.let { target ->
                        target.sourceId?.let { sourceId ->
                            activeTest.passedEdges.contains(TestExecutionEdge(sourceId, target.edge))
                        }
                    } == true -> greenGlow
                    else -> null
                }
            }
            glow
        }
        GesturePathRenderer.buildSegments(
            cells,
            boundaryConnections = projection.boundaryConnections,
            xCenter = metrics::x,
            yCenter = metrics::y,
            thickness = metrics.pathThickness,
            glowColorFor = testPathGlow,
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
                glowColor = seg.glowColor,
            ))
        }

        // 戻り経路の矢印は、ループ開始・終了セルを空けた1つおきの水色経路上へ
        // 配置します。論理座標から投影するため、ズーム・パン後も矢印とホバー用の
        // 当たり判定が同じ戻り経路セルを参照します。
        projection.loopReturnArrowPoints.forEach { localPoint ->
            val gx = state.origin.x + localPoint.x
            val gy = state.origin.y + localPoint.y
            visuals.add(GestureGuiVisual.Text(
                visualId = "path-return-arrow-$gx-$gy",
                x = metrics.x(localPoint.x),
                y = metrics.y(localPoint.y) - 0.006,
                text = Component.text("«").color(NamedTextColor.WHITE),
                size = 0.010,
                layer = 2,
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

        if (!testActive) {
            addNavigation(player, visuals, elements, layout, metrics.columns, metrics.rows)

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
                text = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CENTER_GLYPH)),
                size = 0.0055,
                layer = 6,
            ))
            elements.add(GestureGuiElement(
                elementId = "back-to-start",
                bounds = navBounds(GestureEditorLayout.BACK_X, GestureEditorLayout.BACK_Y, GestureEditorLayout.NAV_SIZE),
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                targetVisualId = "back-label",
                hoverText = navigationHover(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CENTER_ACTION),
            ))

            addZoomControls(player, visuals, elements)
            addClipButton(player, visuals, elements)
        }
        if (test?.phase != GestureTestPhase.RESULT) addCloseButton(player, visuals, elements)
        when {
            test?.phase == GestureTestPhase.RUNNING -> addTestExecutionButton(player, visuals, elements, running = true)
            state.testExecution == null && !testActive && canStartTest() ->
                addTestExecutionButton(player, visuals, elements, running = false)
        }

        // ズームはビューポート内容とその当たり判定だけを同じ倍率で変換します。
        // IDの接頭辞判定はisMapVisual/isMapElementへ集約し、表示だけ・入力だけが
        // 変換対象から外れる状態を防ぎます。特に戻り経路のホバー要素はpath:名前空間へ
        // 統一しているため、通常経路と同じ縮尺・クリップ処理を通ります。
        val scaledVisuals = visuals.map { visual ->
            if (isMapVisual(visual)) {
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
            if (isMapElement(element)) {
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
        ensureUniqueScreenElementIds(finalElements, script.id)

        // 今回の描画が最後まで完了した場合だけ、次回の別障害を通知できるように
        // 通知抑制を解除します。プレビューだけ失敗した場合は、上の処理で失敗状態を
        // 保持したまま旧レイアウトを表示するため、同じ障害を画面更新ごとに連投しません。
        if (!layoutFailureDuringCurrentRender) layoutFailureNoticeSent = false

        return GestureGuiView(
            GestureGuiScreenDefinition(
                UPPER_SCREEN_ID,
                finalElements,
                access = screenAccess,
                accessPolicy = screenAccessPolicy,
                visibilityPolicy = screenVisibilityPolicy,
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

    /**
     * CC-Systemへ渡す直前に、画面要素IDの重複を構造エラーとして明示します。
     *
     * 重複要素をdistinctByで捨てると、どのノード／操作が消えたか分からないまま
     * 画面だけが部分的に表示され、構造化グラフと編集操作の対応が壊れます。ここでは
     * 一つも隠さず例外化し、buildUpperViewportの保護処理から操作者へ通知します。
     */
    private fun ensureUniqueScreenElementIds(elements: List<GestureGuiElement>, scriptId: UUID) {
        val duplicateIds = elements
            .groupingBy(GestureGuiElement::elementId)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            throw GraphLayoutException(
                "ジェスチャーGUIの要素IDが重複しています: script=$scriptId " +
                    "elementIds=${duplicateIds.joinToString(", ")}",
            )
        }
    }

    private fun clearSettingState() {
        state.settingContext = null
        state.settingFieldKey = null
        state.settingTreePath = null
        state.settingRoute = emptyList()
        state.settingScreen = null
        state.settingChoicePage = 0
        state.pendingWorldVariableType = null
        state.pendingWorldVariableDeleteName = null
    }

    /** アイテム／ブロック上書き確認の保留値を、確認画面の全終了経路で一括破棄します。 */
    private fun clearPendingOverwriteState() {
        state.pendingItemContext = null
        state.pendingItemKey = null
        state.pendingItemData = null
        state.pendingBlockContext = null
        state.pendingBlockId = null
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
        state.settingChoicePage = 0
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
        val ownerId = ownerIdFor(player)
        val owner = ownerPlayerFor(player)
        val attention = attentionState()
        // stateを子フレームへ進める前に、現在背面にある親ビューを取得します。
        // 進行後のstateで再生成すると、親ビューの構造まで子フレームへ変わるため、
        // 子画面を積む対象とハイライトを解除する対象を正しく特定できません。
        val parentView = if (settingChildOpen(ownerId)) {
            lowerPanel.buildSettingChild(state, owner, attention, suppressHighlight = true)
        } else {
            lowerPanel.build(state, owner, attention, suppressHighlight = true)
        }
        val nextPath = state.settingTreePath?.enterChild(selectedNodeId)?.nodeIds.orEmpty()
        state.settingRoute = state.settingRoute + frame
        activateSettingFrame(frame, nextPath)
        state.lowerMode = GestureLowerMode.SETTING_CHOICES
        ensureSettingChild(player, parentView)
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
        val fields = CommandSettingsModel.gestureVisibleFields(node)
        if (absoluteIndex !in fields.indices) return

        invalidateInput(player.uniqueId)
        state.settingsTab = absoluteIndex
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
                    (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.PLAYER_STATE.name) ||
                    node.type == CommandType.TEMP_SET
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

    /** SETTINGSの編集ボタンから、入力画面または専用選択へ遷移します。 */
    private fun beginSelectedFieldEdit(player: Player, fieldKey: String) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        observedRevision = script.revision
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
        if (CommandSettingsModel.gestureVisibleFields(node).none { it.key == fieldKey }) {
            // 状態更新の直後に古いタブ操作が届いても、実行時に不要な設定を
            // 隠した共通モデルの可視性を操作入口でも再確認します。
            clearSettingState()
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
            return
        }
        val descriptor = CommandSettingsModel.descriptor(node, fieldKey)
        val context = CommandSettingContext(state.scriptId, node.id, descriptor.role)
        if (fieldKey == "item" && (
                node.type == CommandType.GIVE_ITEM ||
                    (node.type == CommandType.ENTITY_ACTION && node.string("action", "ride") == "equip") ||
                    (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.PLAYER_STATE.name) ||
                    node.type == CommandType.TEMP_SET
                )) {
            applyHeldItem(player, context)
            return
        }
        if (fieldKey == "block" && (
                node.type == CommandType.BLOCK_OPERATION ||
                    (node.type == CommandType.CONDITION && node.string("kind") == ConditionKind.BLOCK_STATE.name) ||
                    (node.type == CommandType.TEMP_SET &&
                        TemporaryVariableType.parse(node.string("tempType")) == TemporaryVariableType.BLOCK)
                )) {
            applyHeldBlock(player, context)
            return
        }
        if (fieldKey == "diskId" && node.type == CommandType.DISK_CALL) {
            applyHeldDisk(player, context)
            return
        }
        if (fieldKey == "staySeconds" && node.type == CommandType.DISPLAY_TEXT) {
            // 表示時間は1つのタブにまとめ、現在値欄では3項目を表示します。編集時は
            // インベントリGUIと同じ一組の入力欄・最大長・検証を使います。
            showDisplayTimingSettingDialog(player, context, node)
            return
        }
        if (fieldKey == "soundParameters" && (
                node.type == CommandType.PLAY_SOUND ||
                    (node.type == CommandType.TEMP_SET &&
                        TemporaryVariableType.parse(node.string("tempType")) == TemporaryVariableType.SOUND)
                )) {
            showSoundParametersSettingDialog(player, context, node)
            return
        }
        if (fieldKey == "particleParameters" && node.type == CommandType.PARTICLE) {
            // パーティクルの散布範囲・速度・個数は、Inventoryと同じく
            // 1つの「表示設定」ダイアログで縦に編集します。個別入力へ分解すると
            // 3軸のうち一部だけ保存されるため、必ず一括検証・一括保存します。
            showParticleParametersSettingDialog(player, context, node)
            return
        }
        val screen = gestureSettingScreenFor(descriptor.editor)
        if (screen == null) {
            // 構造化モデルで専用画面を持たない項目は、チャットを横取りせず
            // CC-System共通の入力画面で入力します。
            // インベントリGUIのshowFieldDialogと同一の maxLength・検証を使います。
            // 入力項目はCommandSettingsModelが返すフィールド集合から来るため、
            // 仕様未登録時に自由入力へ落とすとInventory/Gesture間の契約が壊れます。
            val spec = CommandDialogSpecs.field(node, fieldKey) ?: return
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

    /**
     * ITEM／BLOCK／SOUND／EFFECTの設定元を選択します。
     *
     * 一時変数は、他の構造化設定と同じく方式選択と参照名入力を別操作にします。
     * 直接値を選んだ場合は、選択直後に既存のメインハンド設定またはDialog入力へ
     * 引き継ぎ、設定元の選択だけで未完成の値を完了扱いにしないようにします。
     */
    private fun selectTypedValueSource(
        player: Player,
        fieldKey: String,
        source: CommandValueSource,
    ) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        observedRevision = script.revision
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
        if (!CommandSettingsModel.supportsTemporaryValueReference(node, fieldKey)) return
        val context = CommandSettingContext(
            state.scriptId,
            node.id,
            CommandSettingsModel.descriptor(node, fieldKey).role,
        )
        val currentSource = CommandSettingsModel.temporaryValueSource(node, fieldKey)
        if (source == CommandValueSource.TEMPORARY) {
            if (currentSource != CommandValueSource.TEMPORARY) {
                if (!updateSettingNode(player, context, configuredFields = emptySet()) {
                        CommandSettingsModel.selectTemporaryValueSource(it, fieldKey)
                    }) return
                return
            }
            beginSettingInput(
                player,
                CommandDialogSpecs.variableName,
                CommandSettingsModel.temporaryValueReference(node, fieldKey).orEmpty(),
            ) { raw ->
                if (!updateSettingNode(player, context, configuredFields = emptySet()) {
                        CommandSettingsModel.setTemporaryValueReference(it, fieldKey, raw)
                    }) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else null
            }
            return
        }

        if (!updateSettingNode(player, context, configuredFields = emptySet()) {
                CommandSettingsModel.selectLiteralValueSource(it, fieldKey)
            }) return
        beginSelectedFieldEdit(player, fieldKey)
    }

    /** メインハンドの実アイテムを設定値とスナップショットへ保存します。 */
    private fun applyHeldItem(
        player: Player,
        context: CommandSettingContext,
        parameter: String = "item",
    ): Boolean {
        val held = player.inventory.itemInMainHand.takeUnless { it.type == Material.AIR }
        if (held == null) {
            player.sendMessage("§cメインハンドにアイテムを持ってください。")
            // 呼び出し元が専用選択画面から設定タブへ戻した直後でも、
            // 画面上の表示を状態と同期させ、古い候補画面を残しません。
            // 未所持時は要求どおり効果音・チャット通知を含めて何もしません。
            updateLower(player)
            return false
        }
        val itemKey = held.type.key.toString()
        val itemData = ItemStackCodec.encode(held)
        val node = plugin.scripts.load(context.scriptId)?.graph?.nodes?.get(context.nodeId) ?: return false
        val hasExistingItem = HeldSettingOverwritePolicy.requiresConfirmation(
            node.string(parameter),
            node.string("itemData"),
        )
        if (parameter == "item" && hasExistingItem) {
            openItemOverwriteConfirm(player, context, itemKey, itemData)
            return false
        }
        return saveHeldItem(player, context, parameter, itemKey, itemData)
    }

    /** ブロック設定はメインハンドからブロックIDを保存し、空手はminecraft:airとして扱います。 */
    private fun applyHeldBlock(
        player: Player,
        context: CommandSettingContext,
    ): Boolean {
        val blockId = HeldBlockSettingPolicy.materialId(player.inventory.itemInMainHand.type)
            ?: run {
                player.sendMessage("§cメインハンドにブロックを持ってください。")
                updateLower(player)
                return false
            }
        val node = plugin.scripts.load(context.scriptId)?.graph?.nodes?.get(context.nodeId) ?: return false
        if (HeldSettingOverwritePolicy.requiresConfirmation(node.string("block"))) {
            openBlockOverwriteConfirm(player, context, blockId)
            return false
        }
        return saveHeldBlock(player, context, blockId)
    }

    /** 確認不要なブロック設定の保存を、アイテム設定と同じ共通更新境界へ通します。 */
    private fun saveHeldBlock(
        player: Player,
        context: CommandSettingContext,
        blockId: String,
    ): Boolean = updateSettingNode(player, context, configuredFields = setOf("block")) {
        CommandSettingsModel.setParameter(it, "block", blockId)
    }

    /**
     * プログラムディスクもアイテム付与と同じメインハンド入力で設定します。
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

    private fun configuredBlock(node: CommandNode): ItemStack? =
        Material.matchMaterial(node.string("block"))
            ?.takeUnless { it == Material.AIR || !it.isBlock }
            ?.let(::ItemStack)

    private data class ConfiguredSound(
        val name: String,
        val volume: Float,
        val pitch: Float,
    )

    private fun configuredSound(node: CommandNode): ConfiguredSound? {
        val name = node.string("sound").takeIf(String::isNotBlank) ?: return null
        if (NamespacedKey.fromString(name) == null) return null
        val volume = node.string("volume", "1.0").toFloatOrNull()?.takeIf(Float::isFinite) ?: return null
        val pitch = node.string("pitch", "1.0").toFloatOrNull()?.takeIf(Float::isFinite) ?: return null
        return ConfiguredSound(name, volume, pitch)
    }

    private data class ConfiguredEffect(
        val type: org.bukkit.potion.PotionEffectType,
        val durationTicks: Int,
        val amplifier: Int,
    )

    private fun configuredEffect(node: CommandNode): ConfiguredEffect? {
        val key = NamespacedKey.fromString(node.string("effect")) ?: return null
        val type = Registry.EFFECT.get(key) ?: return null
        val seconds = node.string("seconds").toIntOrNull()?.takeIf { it > 0 } ?: return null
        val level = node.string("level").toIntOrNull()?.takeIf { it > 0 } ?: return null
        val durationTicks = runCatching { Math.multiplyExact(seconds, 20) }.getOrNull() ?: return null
        return ConfiguredEffect(type, durationTicks, level - 1)
    }

    private fun playConfiguredSound(player: Player, node: CommandNode): Boolean {
        val sound = configuredSound(node) ?: return false
        player.playSound(player.location, sound.name, SoundCategory.MASTER, sound.volume, sound.pitch)
        return true
    }

    private fun applyConfiguredEffect(player: Player, node: CommandNode): Boolean {
        val effect = configuredEffect(node) ?: return false
        player.addPotionEffect(PotionEffect(effect.type, effect.durationTicks, effect.amplifier))
        return true
    }

    /** 条件等、設定元切替を持たない従来の確認ボタンも引き続き有効にします。 */
    private fun isDirectTypedValuePreview(node: CommandNode, fieldKey: String): Boolean =
        !CommandSettingsModel.supportsTemporaryValueReference(node, fieldKey) ||
            CommandSettingsModel.temporaryValueSource(node, fieldKey) == CommandValueSource.LITERAL

    private fun openItemOverwriteConfirm(
        player: Player,
        context: CommandSettingContext,
        itemKey: String,
        itemData: String,
    ) {
        val ownerId = ownerIdFor(player)
        if (api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.CONFIRM_SCREEN_ID) == true) return
        val settingChildWasOpen = settingChildOpen(ownerId)
        val parentId = if (settingChildWasOpen) lowerPanel.SETTING_CHILD_SCREEN_ID else lowerPanel.LOWER_SCREEN_ID
        val owner = ownerPlayerFor(player)
        val attention = attentionState()
        val parentView = if (settingChildWasOpen) {
            lowerPanel.buildSettingChild(state, owner, attention, suppressHighlight = true)
        } else {
            lowerPanel.build(state, owner, attention, suppressHighlight = true)
        }
        state.confirmKind = GestureConfirmKind.ITEM_OVERWRITE
        state.confirmNodeId = null
        clearPendingOverwriteState()
        state.pendingItemContext = context
        state.pendingItemKey = itemKey
        state.pendingItemData = itemData
        state.lowerMode = GestureLowerMode.CONFIRM
        val opened = runCatching {
            openChildAndSuppressParentHighlight(
                ownerId,
                parentView,
                lowerPanel.build(state, owner, attention),
                GestureGuiChildOptions(
                    parentScreenId = parentId,
                    overlayMaterial = Material.RED_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "アイテム上書き確認子画面のオープンに失敗しました: script=${state.scriptId}",
                failure,
            )
            false
        }
        if (!opened) {
            state.confirmKind = GestureConfirmKind.DELETE
            clearPendingOverwriteState()
            state.lowerMode = if (parentId == lowerPanel.SETTING_CHILD_SCREEN_ID) GestureLowerMode.SETTING_CHOICES else GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    private fun openBlockOverwriteConfirm(
        player: Player,
        context: CommandSettingContext,
        blockId: String,
    ) {
        val ownerId = ownerIdFor(player)
        if (api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.CONFIRM_SCREEN_ID) == true) return
        val settingChildWasOpen = settingChildOpen(ownerId)
        val parentId = if (settingChildWasOpen) lowerPanel.SETTING_CHILD_SCREEN_ID else lowerPanel.LOWER_SCREEN_ID
        val owner = ownerPlayerFor(player)
        val attention = attentionState()
        val parentView = if (settingChildWasOpen) {
            lowerPanel.buildSettingChild(state, owner, attention, suppressHighlight = true)
        } else {
            lowerPanel.build(state, owner, attention, suppressHighlight = true)
        }
        state.confirmKind = GestureConfirmKind.BLOCK_OVERWRITE
        state.confirmNodeId = null
        clearPendingOverwriteState()
        state.pendingBlockContext = context
        state.pendingBlockId = blockId
        state.lowerMode = GestureLowerMode.CONFIRM
        val opened = runCatching {
            openChildAndSuppressParentHighlight(
                ownerId,
                parentView,
                lowerPanel.build(state, owner, attention),
                GestureGuiChildOptions(
                    parentScreenId = parentId,
                    overlayMaterial = Material.RED_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "ブロック上書き確認子画面のオープンに失敗しました: script=${state.scriptId}",
                failure,
            )
            false
        }
        if (!opened) {
            state.confirmKind = GestureConfirmKind.DELETE
            clearPendingOverwriteState()
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
            reportGraphOperationFailure(
                player,
                "アイテム上書きの保存に失敗しました: script=${context.scriptId} node=${context.nodeId}",
                failure,
            )
        }.getOrDefault(false)
        if (!saved) {
            // 確認子画面を閉じず、再試行できるよう保留中のItemStackを維持します。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_ITEM_SAVE_RETRY))
            return
        }
        clearPendingOverwriteState()
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

    private fun confirmBlockOverwrite(player: Player) {
        val context = state.pendingBlockContext ?: return
        val blockId = state.pendingBlockId ?: return
        val saved = runCatching {
            CommandSettingsModel.updateNode(
                plugin,
                context,
                configuredFields = setOf("block"),
                editorId = player.uniqueId,
                expectedRevision = expectedMutationRevision(player),
                change = { node ->
                    CommandSettingsModel.setParameter(node, "block", blockId)
                },
            ) != null
        }.onFailure { failure ->
            reportGraphOperationFailure(
                player,
                "ブロック上書きの保存に失敗しました: script=${context.scriptId} node=${context.nodeId}",
                failure,
            )
        }.getOrDefault(false)
        if (!saved) {
            // 確認子画面を閉じず、保存失敗時も同じ対象で再試行できるようにします。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
            return
        }
        clearPendingOverwriteState()
        state.confirmKind = GestureConfirmKind.DELETE
        val ownerId = ownerIdFor(player)
        api.closeChild(ownerId, lowerPanel.CONFIRM_SCREEN_ID)
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
        configuredFields: Set<String>? = null,
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
                // nullは従来どおり現在の設定項目を完了扱いにします。
                // 空集合は方式だけを選択した未完成状態を明示し、TEMPORARYや
                // COORDINATESの二段階入力で値未入力のまま確定しないようにします。
                configuredFields = configuredFields ?: setOfNotNull(state.settingFieldKey),
                expectedRevision = expectedMutationRevision(player),
                change = change,
            ) != null
        }.onFailure { failure ->
            reportGraphOperationFailure(
                player,
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
            body = CommandDialogSpecs.body(player, spec),
            inputs = listOf(
                CommandDialogSpecs.input(
                    player = player,
                    id = "minimum",
                    initial = format(minimum),
                    spec = spec,
                    label = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE),
                ),
                CommandDialogSpecs.input(
                    player = player,
                    id = "maximum",
                    initial = format(maximum),
                    spec = spec,
                    label = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE),
                ),
            ),
        ) { response ->
            val minimumRaw = response.textValue("minimum").trim().takeIf(String::isNotEmpty)
            val maximumRaw = response.textValue("maximum").trim().takeIf(String::isNotEmpty)
            val validationError = listOfNotNull(minimumRaw, maximumRaw)
                .mapNotNull(spec::validateInput)
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val minimumValue = minimumRaw?.let(CommandDialogSpecs::finiteDouble)
            val maximumValue = maximumRaw?.let(CommandDialogSpecs::finiteDouble)
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
            body = CommandDialogSpecs.rangeBody(player),
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

    /** 単一文字列の入力をCC-Systemの入力画面へ委譲します。 */
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
                                validationComponent(KcI18n.text(player, validationError)),
                            )
                        }
                        val error = runCatching { onSubmit(value) }
                            .getOrElse { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT) }
                        if (error != null) {
                            MenuActionResult.Rejected(validationComponent(error))
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
            body = CommandDialogSpecs.body(player, spec),
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

    /** Locale内の§カラーコードを失わず、通常の検証エラーも赤で表示します。 */
    private fun validationComponent(error: String): Component =
        if ('§' in error || '&' in error) {
            LegacyComponentSerializer.legacySection().deserialize(error.replace('&', '§'))
        } else {
            Component.text(error, NamedTextColor.RED)
        }

    /** ノード未選択時に表示するプログラム名の設定入力画面です。 */
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
            body = CommandDialogSpecs.timerBody(player),
            inputs = listOf(CommandDialogSpecs.timerInput(player, script.timer.intervalSeconds)),
        ) { response ->
            val rawSeconds = response.textValue("seconds").trim()
            val validationError = timerSpec.validateInput(rawSeconds)
            if (validationError != null) {
                return@showInputDialog KcI18n.text(player, validationError)
            }
            val seconds = requireNotNull(CommandValueRules.parsePositiveInt(rawSeconds))
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

    /**
     * ワールド内変数の編集対象ワールドを解決します。
     *
     * 実行時と同じくMyWorldManagerの所蔵情報を正とし、配置なし・所蔵外では
     * nullを返して一覧を開けないことを呼び出し側へ伝えます。
     */
    private fun resolveVariableWorldId(): UUID? {
        val worldName = state.placement?.world ?: return null
        if (!plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) return null
        return runCatching {
            MyWorldManagerApi.getWorldRepository()?.findByWorldName(worldName)?.uuid
        }.getOrNull()
    }

    /** 現在のMyWorldに配置されたプログラムから、変数の使用一覧を安全に再計算します。 */
    private fun findWorldVariableUsages(name: String): WorldVariableUsageScanResult =
        state.placement?.world?.let { worldName ->
            plugin.findWorldVariableUsagesSafely(worldName, listOf(name))
        } ?: WorldVariableUsageScanResult(emptyMap(), complete = true)

    /**
     * 保存成功後の一覧更新を入力確定処理から切り離します。
     * 画面更新は保存の成否ではなく、次tickで行う表示副作用として扱います。
     */
    private fun scheduleWorldVariableListRefresh(player: Player, operation: String) {
        runCatching {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                val updated = runCatching { updateLower(player) }
                    .getOrElse { failure ->
                        plugin.logger.log(
                            java.util.logging.Level.WARNING,
                            "ワールド内変数の${operation}後の一覧更新に失敗しました: script=${state.scriptId}",
                            failure,
                        )
                        // 保存は成功しているため入力エラー・保存失敗ではなく、
                        // 一覧更新だけが失敗した別事象として利用者へ通知します。
                        player.sendMessage(
                            KcI18n.text(
                                player,
                                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_LIST_REFRESH_FAILED,
                            ),
                        )
                        false
                    }
                if (!updated) {
                    plugin.logger.warning(
                        "ワールド内変数の${operation}後の一覧更新が拒否されました: script=${state.scriptId}",
                    )
                }
            })
        }.onFailure { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "ワールド内変数の${operation}後の一覧更新を予約できませんでした: script=${state.scriptId}",
                failure,
            )
        }
    }

    /** 使用中の変数を削除できない理由と、対象プログラム名をチャットへ通知します。 */
    private fun sendWorldVariableUsageList(
        player: Player,
        variableName: String,
        usages: List<me.awabi2048.kantancommander.data.WorldVariableUsage>,
    ) {
        // プログラム名は利用者データなのでComponent.textへ渡し、チャット装飾コードを
        // 意図せず解釈しないようにします。固定文とplaceholderは言語カタログから解決します。
        player.sendMessage(
            Component.text(
                KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_DELETE_USAGE_HEADER,
                    mapOf("name" to variableName),
                ),
            ),
        )
        usages.forEach { usage ->
            player.sendMessage(
                Component.text(
                    KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_DELETE_USAGE_ENTRY,
                        mapOf("name" to usage.programName),
                    ),
                ),
            )
        }
    }

    /** ワールド内変数一覧の子画面を開きます。 */
    private fun openWorldVariables(player: Player) {
        if (resolveVariableWorldId() == null) return
        state.variablePage = 0
        state.pendingWorldVariableType = null
        state.pendingWorldVariableDeleteName = null
        state.lowerMode = GestureLowerMode.WORLD_VARIABLES
        val ownerId = ownerIdFor(player)
        if (settingChildOpen(ownerId)) {
            updateLower(player)
            return
        }
        val owner = ownerPlayerFor(player)
        val opened = runCatching {
            openChildAndSuppressParentHighlight(
                ownerId,
                lowerPanel.build(state, owner, attentionState(), suppressHighlight = true),
                lowerPanel.buildWorldVariablesChild(state, owner),
                GestureGuiChildOptions(
                    parentScreenId = lowerPanel.LOWER_SCREEN_ID,
                    overlayMaterial = Material.GRAY_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "ワールド内変数一覧のオープンに失敗しました: script=${state.scriptId}",
                failure,
            )
            false
        }
        if (!opened) {
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    /** ワールド内変数一覧の子画面を閉じて親へ戻します。 */
    private fun closeWorldVariables(player: Player) {
        val ownerId = ownerIdFor(player)
        if (settingChildOpen(ownerId)) {
            api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
        }
        state.pendingWorldVariableType = null
        state.pendingWorldVariableDeleteName = null
        state.lowerMode = GestureLowerMode.SETTINGS
        updateLower(player)
    }

    /** 一覧から選んだ変数の値を編集します。型は定義側を正とし、変更しません。 */
    private fun showWorldVariableValueDialog(player: Player, name: String) {
        val worldId = resolveVariableWorldId() ?: return
        val current = plugin.variables.get(worldId, name) ?: return
        val type = plugin.variables.definitions(worldId)[name]?.type ?: current.type
        // 型ごとの長さ・有限値・参照記法の検証は、Inventory/Gestureで分岐させず
        // 共通Specへ集約します。画面側はDialogの表示と保存だけを担当します。
        val spec = CommandDialogSpecs.worldVariableValue(type)
        showInputDialog(
            player = player,
            title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_WORLD_VARIABLE_VALUE_TITLE),
            body = listOf(KcI18n.component(player, CommandDialogSpecs.worldVariableValueBody(type))),
            inputs = listOf(CommandDialogSpecs.input(player, "value", VariableTemplate.stringify(current), spec)),
            afterSubmit = { scheduleWorldVariableListRefresh(player, "値保存") },
        ) { response ->
            val raw = response.textValue("value")
            val validationError = spec.validateInput(raw)
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val value = when (type) {
                VariableType.NUMBER -> WorldVariableValue(
                    VariableType.NUMBER,
                    numberValue = CommandValueRules.parseFiniteDouble(raw),
                ).takeIf { it.numberValue != null } ?: return@showInputDialog KcI18n.text(
                    player,
                    CommandDialogSpecs.worldVariableValueInvalid(VariableType.NUMBER),
                )
                VariableType.STRING -> WorldVariableValue(VariableType.STRING, stringValue = raw)
            }
            val saved = runCatching { plugin.variables.set(worldId, name, value); true }
                .getOrElse { failure ->
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "ワールド内変数を保存できませんでした: world=$worldId name=$name",
                        failure,
                    )
                    false
                }
            if (!saved) {
                return@showInputDialog KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                )
            }
            null
        }
    }

    /**
     * 一覧から型選択子画面へ遷移します。型選択はDialogの追加操作ではなく、
     * GestureGUI上の独立した設定画面として扱い、選択状態をstateへ保持します。
     */
    private fun openWorldVariableTypeSelection(player: Player) {
        if (resolveVariableWorldId() == null) return
        state.pendingWorldVariableType = null
        state.lowerMode = GestureLowerMode.WORLD_VARIABLE_TYPE
        val ownerId = ownerIdFor(player)
        if (settingChildOpen(ownerId)) {
            updateLower(player)
            return
        }
        val owner = ownerPlayerFor(player)
        val opened = runCatching {
            openChildAndSuppressParentHighlight(
                ownerId,
                lowerPanel.build(state, owner, attentionState(), suppressHighlight = true),
                lowerPanel.buildWorldVariableTypeChild(state, owner),
                GestureGuiChildOptions(
                    parentScreenId = lowerPanel.LOWER_SCREEN_ID,
                    overlayMaterial = Material.GRAY_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "ワールド内変数の型選択画面のオープンに失敗しました: script=${state.scriptId}",
                failure,
            )
            false
        }
        if (!opened) {
            state.pendingWorldVariableType = null
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    /** 型選択画面から一覧へ戻ります。子画面自体は閉じず、内容だけを差し替えます。 */
    private fun closeWorldVariableTypeSelection(player: Player) {
        state.pendingWorldVariableType = null
        state.lowerMode = GestureLowerMode.WORLD_VARIABLES
        updateLower(player)
    }

    /**
     * 型選択後に表示する変数名Dialogです。
     *
     * 初期値入力はここでは行いません。保存層の型専用defineが必要な空値を用意し、
     * Dialogは名前の検証・定義の確定だけを担当します。
     */
    private fun showWorldVariableNameDialog(player: Player, type: VariableType) {
        if (resolveVariableWorldId() == null) return
        // Dialogのキャンセル後も一覧へ戻れるよう、背面の状態を先に一覧へ戻します。
        state.pendingWorldVariableType = null
        state.lowerMode = GestureLowerMode.WORLD_VARIABLES
        updateLower(player)
        val spec = CommandDialogSpecs.variableName
        showInputDialog(
            player = player,
            title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_WORLD_VARIABLE_CREATE_TITLE),
            body = listOf(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_VARIABLE_BODY)),
            inputs = listOf(
                CommandDialogSpecs.input(
                    player,
                    id = "name",
                    initial = "",
                    spec = spec,
                    label = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_VARIABLE_NAME),
                ),
            ),
            afterSubmit = { scheduleWorldVariableListRefresh(player, "定義保存") },
        ) { response ->
            val name = response.textValue("name").trim()
            // Dialog側でも検証しますが、将来別経路から呼ばれても保存前の境界を
            // 失わないよう、このコールバック自身でも同じ名前Specを確認します。
            val validationError = spec.validateInput(name)
            if (validationError != null) {
                return@showInputDialog KcI18n.text(
                    player,
                    validationError,
                )
            }
            val worldId = resolveVariableWorldId() ?: return@showInputDialog KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
            )
            val alreadyDefined = runCatching { name in plugin.variables.definitions(worldId) }
                .getOrElse { failure ->
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "ワールド内変数の定義を確認できませんでした: world=$worldId name=$name",
                        failure,
                    )
                    return@showInputDialog KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                    )
                }
            if (alreadyDefined) {
                return@showInputDialog KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_DUPLICATE,
                )
            }
            val defined = runCatching { plugin.variables.define(worldId, name, type) }
                .getOrElse { failure ->
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "ワールド内変数を定義できませんでした: world=$worldId name=$name type=$type",
                        failure,
                    )
                    return@showInputDialog KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                    )
                }
            if (!defined) {
                // 同時操作で先に定義された場合も、保存失敗ではなく利用者が修正できる
                // 重複名として扱います。
                return@showInputDialog KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WORLD_VARIABLES_DUPLICATE,
                )
            }
            null
        }
    }

    /**
     * 一覧行の削除ボタンから確認子画面へ遷移します。
     *
     * 変数名をstateへ保持してから子画面を開くことで、値編集Dialogとは独立した
     * 「削除対象の確認→確定」境界を作ります。対象が同時操作で消えていた場合は、
     * staleな確認画面を表示せず一覧を再描画します。
     */
    private fun openWorldVariableDeleteConfirmation(player: Player, name: String) {
        val worldId = resolveVariableWorldId() ?: return
        val exists = runCatching { plugin.variables.get(worldId, name) != null }
            .getOrElse { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "ワールド内変数の削除対象を確認できませんでした: world=$worldId name=$name",
                    failure,
                )
                false
            }
        if (!exists) {
            updateLower(player)
            return
        }
        state.pendingWorldVariableDeleteName = name
        state.pendingWorldVariableType = null
        state.lowerMode = GestureLowerMode.WORLD_VARIABLE_DELETE_CONFIRM
        val ownerId = ownerIdFor(player)
        if (settingChildOpen(ownerId)) {
            updateLower(player)
            return
        }
        val owner = ownerPlayerFor(player)
        val opened = runCatching {
            openChildAndSuppressParentHighlight(
                ownerId,
                lowerPanel.build(state, owner, attentionState(), suppressHighlight = true),
                lowerPanel.buildWorldVariableDeleteConfirmationChild(state, owner),
                GestureGuiChildOptions(
                    parentScreenId = lowerPanel.LOWER_SCREEN_ID,
                    // 破壊的操作の確認であることを既存のCONFIRM子画面と同じ赤系素材で示します。
                    overlayMaterial = Material.RED_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "ワールド内変数の削除確認画面のオープンに失敗しました: script=${state.scriptId} name=$name",
                failure,
            )
            false
        }
        if (!opened) {
            state.pendingWorldVariableDeleteName = null
            state.lowerMode = GestureLowerMode.WORLD_VARIABLES
            updateLower(player)
        }
    }

    /** 削除確認子画面を閉じ、削除せずに一覧へ戻ります。 */
    private fun closeWorldVariableDeleteConfirmation(player: Player) {
        state.pendingWorldVariableDeleteName = null
        state.lowerMode = GestureLowerMode.WORLD_VARIABLES
        updateLower(player)
    }

    /** 確認子画面の「確定」だけでワールド内変数を削除します。 */
    private fun confirmWorldVariableDelete(player: Player) {
        val name = state.pendingWorldVariableDeleteName ?: run {
            closeWorldVariableDeleteConfirmation(player)
            return
        }
        val worldId = resolveVariableWorldId() ?: run {
            closeWorldVariableDeleteConfirmation(player)
            return
        }
        val worldName = state.placement?.world ?: run {
            closeWorldVariableDeleteConfirmation(player)
            return
        }
        val result: me.awabi2048.kantancommander.data.WorldVariableRemovalResult? =
            runCatching { plugin.removeWorldVariable(worldId, worldName, name) }
            .getOrElse { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "ワールド内変数を削除できませんでした: world=$worldId name=$name",
                    failure,
                )
                null
            }
        if (result == null) {
            // 確認画面を残して再試行・キャンセルを可能にし、保存失敗を黙って
            // 成功扱いにしません。画面外へ移動した場合も同じメッセージで通知します。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
            return
        }
        if (!result.scanComplete) {
            // 使用箇所を確認できない状態で削除すると、破損していた配置から
            // 参照切れを作るため、確認画面を残して再スキャンを待ちます。
            // これは保存失敗ではなく「検査未完了」の別事象なので、専用の文言を
            // 使って、利用者が再スキャンを待つべき操作であることを示します。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SCAN_INCOMPLETE))
            return
        }
        if (result.usages.isNotEmpty()) {
            // 確認画面を開いた後に使用箇所が増えた場合も、一覧を通知して
            // 削除確認を閉じます。保存境界で再判定するため、古い画面から削除できません。
            sendWorldVariableUsageList(player, name, result.usages)
            closeWorldVariableDeleteConfirmation(player)
            return
        }
        if (!result.removed) {
            // 確認画面を残して再試行・キャンセルを可能にし、保存失敗を黙って
            // 成功扱いにしません。画面外へ移動した場合も同じメッセージで通知します。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
            return
        }
        player.playSound(player.location, Sound.BLOCK_BAMBOO_HIT, 1.0f, 1.0f)
        state.pendingWorldVariableDeleteName = null
        state.lowerMode = GestureLowerMode.WORLD_VARIABLES
        updateLower(player)
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
            body = CommandDialogSpecs.soundParametersBody(player),
            inputs = CommandDialogSpecs.soundParametersInputs(player, volume, pitch),
        ) { response ->
            val volumeValue = CommandDialogSpecs.normalize("volume", response.textValue("volume"))
            val pitchValue = CommandDialogSpecs.normalize("pitch", response.textValue("pitch"))
            val volumeError = volumeSpec.validateInput(volumeValue)
            val pitchError = pitchSpec.validateInput(pitchValue)
            if (volumeError != null || pitchError != null) {
                val messages = buildList {
                    if (volumeError != null) {
                        add(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_VOLUME_BODY))
                    }
                    if (pitchError != null) {
                        add(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_PITCH_BODY))
                    }
                }
                return@showInputDialog messages.joinToString("\n")
            }
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

    /** パーティクルの範囲・速度・個数を一括検証して保存します。 */
    private fun showParticleParametersSettingDialog(
        player: Player,
        context: CommandSettingContext,
        node: CommandNode,
    ) {
        val parameterKeys = listOf(
            ParticleSettings.PARAM_DELTA_X,
            ParticleSettings.PARAM_DELTA_Y,
            ParticleSettings.PARAM_DELTA_Z,
            ParticleSettings.PARAM_SPEED,
            ParticleSettings.PARAM_COUNT,
        )
        val specs = parameterKeys.associateWith { key -> requireNotNull(CommandDialogSpecs.field(node, key)) }
        val values = parameterKeys.associateWith { key -> node.string(key) }
        showInputDialog(
            player = player,
            body = CommandDialogSpecs.particleParametersBody(player),
            inputs = CommandDialogSpecs.particleParametersInputs(
                player,
                values.getValue(ParticleSettings.PARAM_DELTA_X),
                values.getValue(ParticleSettings.PARAM_DELTA_Y),
                values.getValue(ParticleSettings.PARAM_DELTA_Z),
                values.getValue(ParticleSettings.PARAM_SPEED),
                values.getValue(ParticleSettings.PARAM_COUNT),
            ),
        ) { response ->
            val rawValues = parameterKeys.associateWith { key ->
                CommandDialogSpecs.normalize(key, response.textValue(key))
            }
            val validationError = rawValues.entries
                .mapNotNull { (key, value) -> specs.getValue(key).validateInput(value) }
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            if (!updateSettingNode(player, context, configuredFields = setOf("particleParameters")) { command ->
                    CommandSettingsModel.setParameters(command, rawValues)
                }) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            }
            null
        }
    }

    /**
     * 複数値の設定は入力欄を分割します（座標X/Y/Z、yaw/pitchなど）。
     * 連結文字列を1欄で受けると、どの値が不正かをユーザーが特定できず、
     * 入力画面の再表示時にも入力値の対応が崩れるため、入力IDと値を一対一にします。
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
        afterSubmit: (() -> Unit)? = null,
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
                                .getOrElse { failure ->
                                    // 入力検証の戻り値と、保存・データ処理の例外を分離します。
                                    // 後者を入力形式エラーへ変換すると、保存済みの変数だけが
                                    // 残ったままDialogをキャンセルできる今回の不整合を再発させます。
                                    plugin.logger.log(
                                        java.util.logging.Level.WARNING,
                                        "入力ダイアログの確定処理に失敗しました: dialog=$dialogId",
                                        failure,
                                    )
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                                }
                            if (error != null) {
                                // RejectedはCC-System側で同じ入力画面を入力値付きで
                                // 再表示するため、入力セッションを維持したまま修正できます。
                                return@MenuDialogHandler MenuActionResult.Rejected(
                                    validationComponent(error),
                                )
                            }
                            clearInputState(player.uniqueId, token)
                            // 保存成功後の表示更新は、確定成功を覆さない別処理です。
                            runCatching { afterSubmit?.invoke() }
                                .onFailure { failure ->
                                    plugin.logger.log(
                                        java.util.logging.Level.WARNING,
                                        "入力ダイアログ確定後の後処理に失敗しました: dialog=$dialogId",
                                        failure,
                                    )
                                }
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
            // show()はPaperの入力画面生成失敗を例外で返すため、表示されていない入力画面を
            // 後続のclose処理が所有中と誤認しないよう、同じ世代だけをロールバックします。
            if (activeInputs[player.uniqueId]?.token == token) invalidateInput(player.uniqueId)
            throw failure
        }
    }

    /**
     * 座標設定用の入力欄をX/Y/Zへ分割します。
     *
     * 座標を1つの文字列として受け取る方式では、区切り文字の誤りや一部の
     * 値だけの入力を画面上で特定できません。入力画面の各入力値をそのまま
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
            body = CommandDialogSpecs.coordinateBody(player),
            inputs = CommandDialogSpecs.coordinateInputs(player, x, y, z),
        ) { response ->
            val rawValues = listOf("x", "y", "z").associateWith { key -> response.textValue(key).trim() }
            val validationError = rawValues.entries
                .mapNotNull { (key, raw) -> CommandDialogSpecs.coordinateSpec(key).validateInput(raw) }
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val xValue = CommandDialogSpecs.finiteDouble(rawValues.getValue("x"))
            val yValue = CommandDialogSpecs.finiteDouble(rawValues.getValue("y"))
            val zValue = CommandDialogSpecs.finiteDouble(rawValues.getValue("z"))
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
            body = CommandDialogSpecs.rotationBody(player),
            inputs = CommandDialogSpecs.rotationInputs(player, yaw, pitch),
        ) { response ->
            val rawValues = listOf("yaw", "pitch").associateWith { key -> response.textValue(key).trim() }
            val validationError = rawValues.entries
                .mapNotNull { (key, raw) -> CommandDialogSpecs.rotationSpec(key).validateInput(raw) }
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val yawValue = CommandDialogSpecs.finiteFloat(rawValues.getValue("yaw"))
            val pitchValue = CommandDialogSpecs.finiteFloat(rawValues.getValue("pitch"))
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
        if (context.elementId.startsWith("lower-setting-choice-page:")) {
            state.settingChoicePage = context.elementId.removePrefix("lower-setting-choice-page:").toIntOrNull() ?: return
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
        if (CommandSettingsModel.gestureVisibleFields(node).none { it.key == fieldKey }) {
            // 依存値の変更後に残った古い子画面からの操作を受け付けません。
            if (settingChildOpen(ownerId)) api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
            clearSettingState()
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
            return
        }

        fun showSettingScreen(openChild: Boolean = false) {
            state.lowerMode = if (settingChildOpen(ownerId) || state.settingRoute.size > 1) {
                GestureLowerMode.SETTING_CHOICES
            } else {
                GestureLowerMode.SETTINGS
            }
            if (openChild && !settingChildOpen(ownerId)) ensureSettingChild(player) else updateLower(player)
        }

        /**
         * 対象の簡略分類を保存し、必要なら距離・種類などの詳細へ進みます。
         * 移動先の「他のエンティティ」も同じ処理を使うため、インライン選択と
         * 通常の対象設定画面で細分類の保存規則が分岐しないようにします。
         */
        fun handleTargetCategory(categoryValue: String) {
            val category = runCatching { TargetCategory.valueOf(categoryValue) }.getOrNull() ?: return
            val role = settingContext.role
            val current = CommandSettingsModel.targetSpec(node, role)
            val currentKind = current?.kind
            val kind = if (CommandSettingsModel.targetCategoryMatches(currentKind, category)) {
                currentKind ?: CommandSettingsModel.defaultTargetKind(category)
            } else {
                CommandSettingsModel.defaultTargetKind(category)
            }
            val encodedCategory = "target:$categoryValue"
            val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encodedCategory)
            if (category == TargetCategory.TEMPORARY) {
                if (!wasSelected || currentKind != TargetKind.TEMPORARY) {
                    // 一時変数は通常の選択肢と同じく、1回目は方式の選択だけにします。
                    // 値未入力のTargetSpecを設定済み扱いにしないため、明示的に
                    // configuredFieldsを空集合へ指定します。
                    if (!updateSettingNode(
                            player,
                            settingContext.copy(role = role),
                            configuredFields = emptySet(),
                        ) {
                            CommandSettingsModel.setTargetSpec(it, role, TargetSpec(TargetKind.TEMPORARY))
                        }) return
                    rememberSettingNode(encodedCategory)
                    showSettingScreen()
                    return
                }
                // 二回目のクリックで初めて一時変数名を入力します。入力完了まで
                // 未設定のまま保持し、途中状態を「設定済み」と表示しません。
                beginSettingInput(
                    player,
                    CommandDialogSpecs.variableName,
                    current.takeIf { it.kind == TargetKind.TEMPORARY }?.tempName.orEmpty(),
                ) { raw ->
                    if (!updateSettingNode(player, settingContext.copy(role = role)) {
                            CommandSettingsModel.setTargetSpec(
                                it,
                                role,
                                TargetSpec(TargetKind.TEMPORARY, tempName = raw.trim()),
                            )
                        }) {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                    } else {
                        showSettingScreen()
                        null
                    }
                }
                return
            }
            val hasChildren = lowerPanel.hasSettingChoiceChildren(state, player, encodedCategory)
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
                            val parsedLimit = parsedText?.let(CommandValueRules::parsePositiveInt)
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
            GestureSettingScreen.LOCATION -> {
                if (group != "location") return
                val child = when (value) {
                    "position" -> GestureSettingScreen.POSITION
                    "facing" -> GestureSettingScreen.FACING
                    else -> return
                }
                val role = when (value) {
                    "position" -> CommandSettingRole.TEMPORARY_LOCATION_POSITION
                    "facing" -> CommandSettingRole.TEMPORARY_LOCATION_FACING
                    else -> return
                }
                // LOCATIONの各項目は既存の位置／向き編集木をそのまま子画面として
                // 開きます。x/y/zを新しい専用入力へ再実装せず、通常コマンドと同じ
                // 参照元・座標・回転の選択契約を共有するのがこの画面の責務です。
                rememberSettingNode(encoded)
                pushSettingFrame(
                    player,
                    GestureSettingFrame(
                        settingContext.copy(role = role),
                        fieldKey,
                        child,
                    ),
                    encoded,
                )
            }
            GestureSettingScreen.POSITION -> {
                // 移動先の「他のエンティティ」はPOSITION画面の右下へインライン表示した
                // 対象分類です。対象設定画面へ遷移せず、ここでも同じ保存・二段階選択を使います。
                if (settingContext.role == CommandSettingRole.DESTINATION && group == "target") {
                    handleTargetCategory(value)
                    return
                }
                if (group != "position") return
                val kind = runCatching { PositionKind.valueOf(value) }.getOrNull() ?: return
                if (!CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(node, settingContext.role, kind)) {
                    // 表示時点から状態が変わっている場合も、保存入口で同じ制約を再確認します。
                    return
                }
                val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                rememberSettingNode(encoded)
                if (kind == PositionKind.TARGET && settingContext.role == CommandSettingRole.DESTINATION) {
                    // 「移動先→別エンティティ」は位置ではなく対象ドメインです。
                    // PositionSpec(TARGET)へ保存すると、対象の種類・距離が失われるため、
                    // targetSpecの共通setterへ初期対象だけを渡して親子の境界を保ちます。
                    val currentTarget = CommandSettingsModel.targetSpec(node, settingContext.role)
                        ?: TargetSpec(TargetKind.NEAREST_PLAYER)
                    if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setTargetSpec(it, settingContext.role, currentTarget)
                        }) return
                    // 対象分類は同じPOSITION画面の右下へ表示します。ここで子画面を
                    // 開かないため、座標設定や戻る操作の位置も安定します。
                    showSettingScreen()
                    return
                }
                if (kind == PositionKind.TEMPORARY) {
                    val current = CommandSettingsModel.positionSpec(node, settingContext.role)
                    if (!wasSelected || current?.kind != PositionKind.TEMPORARY) {
                        // 一時変数は1回目を方式選択、2回目を変数名入力とします。
                        // 保存するSpecは入力前の未完成状態なので、設定済み項目として
                        // マークしないことを明示します。
                        if (!updateSettingNode(
                                player,
                                settingContext,
                                configuredFields = emptySet(),
                            ) {
                                CommandSettingsModel.setPositionSpec(
                                    it,
                                    settingContext.role,
                                    PositionSpec(PositionKind.TEMPORARY),
                                )
                            }) return
                        showSettingScreen()
                        return
                    }
                    beginSettingInput(
                        player,
                        CommandDialogSpecs.variableName,
                        current.takeIf { it.kind == PositionKind.TEMPORARY }?.tempName.orEmpty(),
                    ) { raw ->
                        if (!updateSettingNode(player, settingContext) {
                                CommandSettingsModel.setPositionSpec(
                                    it,
                                    settingContext.role,
                                    PositionSpec(PositionKind.TEMPORARY, tempName = raw.trim()),
                                )
                            }) {
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        } else {
                            showSettingScreen()
                            null
                        }
                    }
                    return
                }
                if (kind == PositionKind.COORDINATES) {
                    val current = CommandSettingsModel.positionSpec(node, settingContext.role)
                    if (!wasSelected) {
                        // 方式選択と実値入力を分離します。ここでプレイヤー位置を仮値として
                        // 保存すると、まだ座標を入力していないのに設定完了へ遷移してしまう
                        // ため、未完成のCOORDINATES Specだけを保持し、完了判定はモデル側へ
                        // 任せます。次回クリックの入力欄では現在位置を初期候補として使います。
                        if (!updateSettingNode(
                                player,
                                settingContext,
                                configuredFields = emptySet(),
                            ) {
                                CommandSettingsModel.setPositionSpec(
                                    it,
                                    settingContext.role,
                                    PositionSpec(PositionKind.COORDINATES),
                                )
                            }) return
                        // 一回目は方式だけを選択し、二回目に入力画面を開きます。
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
                val facingRole = settingContext.role ?: return
                if (kind == FacingKind.TEMPORARY) {
                    val current = CommandSettingsModel.facingSpec(node, facingRole)
                    val encodedFacing = "facing:${kind.name}"
                    val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encodedFacing)
                    if (!wasSelected || current?.kind != FacingKind.TEMPORARY) {
                        // 他の設定値と同じく、最初のクリックでは方式だけを選びます。
                        // 変数名がまだないため、configuredFieldsは空集合のままにします。
                        if (!updateSettingNode(
                                player,
                                settingContext,
                                configuredFields = emptySet(),
                            ) {
                                CommandSettingsModel.setFacingSpec(
                                    it,
                                    FacingSpec(FacingKind.TEMPORARY),
                                    facingRole,
                                )
                            }) return
                        rememberSettingNode(encodedFacing)
                        showSettingScreen()
                        return
                    }
                    beginSettingInput(
                        player,
                        CommandDialogSpecs.variableName,
                        current.takeIf { it.kind == FacingKind.TEMPORARY }?.tempName.orEmpty(),
                    ) { raw ->
                        if (!updateSettingNode(player, settingContext) {
                                CommandSettingsModel.setFacingSpec(
                                    it,
                                    FacingSpec(FacingKind.TEMPORARY, tempName = raw.trim()),
                                    facingRole,
                                )
                            }) {
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        } else {
                            showSettingScreen()
                            null
                        }
                    }
                    return
                }
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
                    "condition-control-block-state" -> {
                        val controlState = runCatching { ControlBlockStateKind.valueOf(value) }.getOrNull() ?: return
                        if (updateSettingNode(player, settingContext) {
                                it.toggleControlBlockState(controlState)
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
                            "condition-value" -> CommandDialogSpecs.conditionValueSpec(node)
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
                if (group != "soundScope" || value !in setOf("POSITION", "WORLD")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "soundScope", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.VARIABLE_TYPE -> {
                if (group != "type") return
                if (node.type == CommandType.TEMP_SET) {
                    val type = TemporaryVariableType.parse(value) ?: return
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.changeTemporaryType(it, type)
                        }) showSettingScreen()
                } else {
                    val type = runCatching { VariableType.valueOf(value) }.getOrNull() ?: return
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, "type", type.name)
                        }) showSettingScreen()
                }
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
                    "count" -> if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, "value", "\${CURRENT_LOOP_COUNT}")
                        }) showSettingScreen()
                }
            }
            GestureSettingScreen.CONDITION_INVERSION -> {
                if (group != "invert") return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, fieldKey, value.toBoolean().toString())
                    }) showSettingScreen()
            }
        }
    }

    private fun handleUpperAction(context: GestureGuiActionContext) {
        val player = Bukkit.getPlayer(context.actorId) ?: return
        val ownerId = context.ownerId
        state.testExecution?.let { test ->
            // テスト中のエディター操作は全員分を遮断します。指定された操作も
            // 確定したオーナーだけが実行でき、共有画面の第三者は離脱も含めて
            // テスト状態へ触れません。
            if (context.actorId != test.ownerId) return
            when (test.phase) {
                GestureTestPhase.CONFIRM -> when {
                    context.elementId == "test-confirm-debug" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                        test.debugMode = !test.debugMode
                        plugin.testExecutionPreferences.save(player, test.debugMode, test.logOutput)
                        updateLower(player)
                    }
                    context.elementId == "test-confirm-log" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                        test.logOutput = !test.logOutput
                        plugin.testExecutionPreferences.save(player, test.debugMode, test.logOutput)
                        updateLower(player)
                    }
                    context.elementId == "nav-close" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                        cancelTestConfirmation(player)
                        closeImmediately(ownerId)
                    }
                    context.elementId == "test-confirm-start" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                        plugin.testExecutionPreferences.save(player, test.debugMode, test.logOutput)
                        startTestExecution(player)
                    }
                    context.elementId == "test-confirm-cancel" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                        cancelTestConfirmation(player)
                    }
                }
                GestureTestPhase.RUNNING -> when {
                    context.elementId == "test-execution" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                        interruptTestWithResult()
                    }
                    context.elementId == "nav-close" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                        interruptTestWithoutResult("close")
                        closeImmediately(ownerId)
                    }
                }
                GestureTestPhase.RESULT -> {
                    if (context.elementId == "test-result-complete" && GestureGuiClickPolicy.isPrimaryClick(context.gesture)) {
                        completeTest(player)
                    }
                }
            }
            return
        }
        if (!canOperateSharedActor(ownerId, context.actorId)) return
        // 画面操作が発生した時点で、古い入力画面の入力を無効化します。
        // close/open以外の遷移でも遅延コールバックが設定を書き換えないようにします。
        invalidateInput(player.uniqueId)
        when {
            context.elementId == "test-execution" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                openTestConfirmation(player)
            }
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
                    reportGraphOperationFailure(
                        player,
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
                    setZoomLevel(player, next)
                    updateUpper(player)
                }
            }
            context.elementId == "nav-zoom-out" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val next = (state.zoomLevel - 1).coerceAtLeast(GestureEditorLayout.MIN_ZOOM_LEVEL)
                if (next != state.zoomLevel) {
                    setZoomLevel(player, next)
                    updateUpper(player)
                }
            }
            context.elementId == "nav-zoom-reset" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                if (state.zoomLevel != GestureEditorLayout.INITIAL_ZOOM_LEVEL) {
                    setZoomLevel(player, GestureEditorLayout.INITIAL_ZOOM_LEVEL)
                    updateUpper(player)
                }
            }
            context.elementId == "nav-clip" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                // 共有画面の第三者には、所有者の表示モードを変更する権限を渡しません。
                // クリップはトグル操作です。追従中はpinToCurrentPositionでその場に固定し、
                // 固定中はunpinToFollowでプレイヤー追従へ戻します。どちらも実行時poseを
                // 保持したまま追従の停止/再開だけを切り替えるため、クリック直後の表示と
                // 当たり判定の位置がずれません。
                if (context.actorId != ownerId) return
                if (state.anchor != null) {
                    if (!api.unpinToFollow(ownerId)) return
                    state.anchor = null
                } else {
                    if (!api.pinToCurrentPosition(ownerId)) return
                    state.anchor = player.eyeLocation.clone()
                }
                updateUpper(player)
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
                val layout = currentViewportLayout(player) ?: return
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
                val layout = currentViewportLayout(player) ?: return
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
                val layout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrElse { failure ->
                    reportLayoutFailure(
                        player,
                        "ジェスチャーGUIの追加位置確認でレイアウトを生成できません: script=${script.id}",
                        failure,
                    )
                    return
                }
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
                val layout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrElse { failure ->
                    reportLayoutFailure(
                        player,
                        "ジェスチャーGUIの挿入位置確認でレイアウトを生成できません: script=${script.id}",
                        failure,
                    )
                    return
                }
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
            context.elementId == "lower-script-variables" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                openWorldVariables(player)
            }
            context.elementId == "lower-setting-back" && state.lowerMode == GestureLowerMode.WORLD_VARIABLE_DELETE_CONFIRM &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                closeWorldVariableDeleteConfirmation(player)
            }
            context.elementId == "lower-setting-back" && state.lowerMode == GestureLowerMode.WORLD_VARIABLE_TYPE &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                closeWorldVariableTypeSelection(player)
            }
            context.elementId == "lower-setting-back" && state.lowerMode == GestureLowerMode.WORLD_VARIABLES &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                closeWorldVariables(player)
            }
            context.elementId.startsWith("lower-variables-page:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                state.variablePage = context.elementId.removePrefix("lower-variables-page:").toIntOrNull() ?: return
                updateLower(player)
            }
            // 「削除」要素は lower-variable: より先に判定します。共通接頭辞の後段を
            // 値編集の変数名として誤解釈すると、削除クリックでDialogが開いてしまいます。
            context.elementId.startsWith("lower-variable-delete:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val name = context.elementId.removePrefix("lower-variable-delete:").trim()
                if (name.isEmpty()) return
                val usageScan = findWorldVariableUsages(name)
                if (!usageScan.complete) {
                    // 一覧カードも削除不可表示にしているため、クリックされた場合も
                    // 確認画面へ進めず、完全スキャンが可能になるまで保存を保護します。
                    player.sendMessage(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                    )
                } else if (usageScan.usages[name].orEmpty().isNotEmpty()) {
                    val usages = usageScan.usages[name].orEmpty()
                    // 使用中の削除ボタンは灰色表示ですが、クリックを無反応にせず、
                    // 削除できない理由と使用プログラムをチャットへ示します。
                    sendWorldVariableUsageList(player, name, usages)
                } else {
                    openWorldVariableDeleteConfirmation(player, name)
                }
            }
            context.elementId.startsWith("lower-variable:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val name = context.elementId.removePrefix("lower-variable:").trim()
                if (name.isEmpty()) return
                showWorldVariableValueDialog(player, name)
            }
            context.elementId == "lower-world-variable-delete-confirm" &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                confirmWorldVariableDelete(player)
            }
            context.elementId.startsWith("lower-world-variable-type:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val type = runCatching {
                    VariableType.valueOf(context.elementId.removePrefix("lower-world-variable-type:"))
                }.getOrNull() ?: return
                state.pendingWorldVariableType = type
                updateLower(player)
            }
            context.elementId == "lower-world-variable-type-next" &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val type = state.pendingWorldVariableType ?: return
                showWorldVariableNameDialog(player, type)
            }
            context.elementId == "lower-variables-create" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                openWorldVariableTypeSelection(player)
            }
            context.elementId.startsWith("lower-tab:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val index = context.elementId.removePrefix("lower-tab:").toIntOrNull() ?: return
                openSettingsTab(player, index)
            }
            context.elementId.startsWith("lower-setting-") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                handleSettingAction(context, player)
            }
            context.elementId.startsWith("lower-value-source:") &&
                (context.gesture in GestureGuiClickPolicy.MAIN_HAND ||
                    GestureGuiClickPolicy.isPrimaryClick(context.gesture)) -> {
                val encoded = context.elementId.removePrefix("lower-value-source:").split(":")
                if (encoded.size != 2) return
                val source = runCatching { CommandValueSource.valueOf(encoded[1]) }.getOrNull() ?: return
                selectTypedValueSource(player, encoded[0], source)
            }
            context.elementId.startsWith("lower-edit:") &&
                context.gesture in GestureGuiClickPolicy.MAIN_HAND -> {
                val fieldKey = context.elementId.removePrefix("lower-edit:")
                beginSelectedFieldEdit(player, fieldKey)
            }
            context.elementId == "lower-item-get" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
                if (!isDirectTypedValuePreview(node, "item")) return
                val item = configuredItem(node) ?: return
                player.inventory.addItem(item.clone()).values.forEach { overflow ->
                    player.world.dropItemNaturally(player.location, overflow)
                }
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_ITEM_TAKEN))
            }
            context.elementId == "lower-block-get" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
                if (!isDirectTypedValuePreview(node, "block")) return
                val block = configuredBlock(node) ?: return
                player.inventory.addItem(block.clone()).values.forEach { overflow ->
                    player.world.dropItemNaturally(player.location, overflow)
                }
            }
            context.elementId == "lower-sound-preview" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
                if (!isDirectTypedValuePreview(node, "sound")) return
                playConfiguredSound(player, node)
            }
            context.elementId == "lower-effect-preview" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
                if (!isDirectTypedValuePreview(node, "effect")) return
                applyConfiguredEffect(player, node)
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
                        .getOrElse { failure ->
                            reportLayoutFailure(
                                player,
                                "ジェスチャーGUIの挿入候補確認でレイアウトを生成できません: script=${script.id}",
                                failure,
                            )
                            return
                        }
                        .cells[point]
                        ?.insertionTarget
                    if (currentTarget != target) return
                }
                if (type == CommandType.FOR_END || (type == CommandType.MERGE &&
                        (target.mergeConditionId == null || !GraphEditor.canAppendMerge(
                            script.graph,
                            target.mergeConditionId,
                            target.continuationId,
                        )))) {
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
                            if (!GraphEditor.canAppendMerge(
                                    candidateGraph,
                                    target.mergeConditionId,
                                    target.continuationId,
                                )) {
                                null
                            } else {
                                GraphEditor.appendMerge(
                                    candidateGraph,
                                    requireNotNull(target.mergeConditionId),
                                    continuationId = target.continuationId,
                                )
                            }
                        } else {
                            GraphEditor.insert(
                                candidateGraph,
                                target.sourceId,
                                target.edge,
                                type,
                                continuationId = target.continuationId,
                            )
                        }
                    }
                }.getOrElse { failure ->
                    reportGraphOperationFailure(
                        player,
                        "コマンド挿入を保存できませんでした: script=${script.id} type=$type",
                        failure,
                    )
                    state.pendingInsertion = null
                    state.selectedInsertionCandidatePoint = null
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
            context.elementId == "lower-duplicate" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                val nodeId = state.selectedNodeId ?: return
                observedRevision = script.revision
                // 表示時と同じ構造・上限判定をクリック時にも再確認します。画面表示後に
                // 別操作者が構造を変更しても、古いボタンから複製を強行しません。
                if (!GraphEditor.canDuplicate(
                        script.graph,
                        nodeId,
                        plugin.graphLimits().maximumNodeCount,
                    )) {
                    refreshFromStore()
                    return
                }
                val duplicated = runCatching {
                    CommandSettingsModel.updateGraph(
                        plugin,
                        script.id,
                        player.uniqueId,
                        expectedRevision = expectedMutationRevision(player),
                    ) { candidateGraph ->
                        GraphEditor.duplicate(
                            candidateGraph,
                            nodeId,
                            plugin.graphLimits().maximumNodeCount,
                        )
                    }
                }.getOrElse { failure ->
                    reportGraphOperationFailure(
                        player,
                        "ジェスチャーGUIからのコマンド複製を保存できませんでした: script=${script.id} node=$nodeId",
                        failure,
                    )
                    refreshFromStore()
                    return
                } ?: run {
                    // CAS競合や保存直前の可否変化では、古い選択状態を再利用せず、
                    // 最新グラフを表示してから利用者にもう一度選択させます。
                    refreshFromStore()
                    return
                }
                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f)
                if (settingChildOpen(ownerId)) {
                    api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
                }
                state.pendingInsertion = null
                state.selectedAddPoint = null
                state.selectedInsertionCandidatePoint = null
                clearSettingState()
                // 複製したノードをそのまま選択し、複製結果の設定内容を即座に確認・編集
                // できるよう、通常のノード選択と同じ初期表示経路へ戻します。
                state.selectedNodeId = duplicated.id
                state.lowerMode = GestureLowerMode.SETTINGS
                state.settingsTab = 0
                updateUpper(player)
                updateLower(player)
                openSettingsTab(player, 0)
            }
            context.elementId == "lower-delete" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                state.confirmNodeId = state.selectedNodeId ?: return
                state.confirmKind = GestureConfirmKind.DELETE
                clearPendingOverwriteState()
                openConfirmChild(player)
            }
            context.elementId == "confirm-delete" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                if (state.confirmKind == GestureConfirmKind.ITEM_OVERWRITE) {
                    confirmItemOverwrite(player)
                    return
                }
                if (state.confirmKind == GestureConfirmKind.BLOCK_OVERWRITE) {
                    confirmBlockOverwrite(player)
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
                        reportGraphOperationFailure(
                            player,
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
                    clearPendingOverwriteState()
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
                clearPendingOverwriteState()
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
                clearPendingOverwriteState()
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
                visibilityPolicy = screenVisibilityPolicy,
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
                text = GraphLayoutFailureFeedback.renderMessage(player).color(NamedTextColor.RED),
                size = 0.008,
                lineWidth = 260,
            ),
            GestureGuiVisual.Text(
                visualId = "viewport-error-detail",
                x = 0.0,
                y = -0.02,
                text = GraphLayoutFailureFeedback.reopenHint(player).color(NamedTextColor.GRAY),
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
                visibilityPolicy = screenVisibilityPolicy,
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

    /**
     * 描画変換の失敗をログと操作者の両方へ返します。
     *
     * 画面更新は保存成功・他プレイヤー操作・入力ガードなど複数の経路から連続して
     * 呼ばれるため、同じ障害をチャットへ毎回送ると本来の操作結果を隠してしまいます。
     * 画面生成が一度成功するまで通知を抑制し、エラー画面自体は毎回返します。
     */
    private fun reportLayoutFailure(player: Player, operation: String, failure: Throwable) {
        plugin.logger.log(java.util.logging.Level.WARNING, operation, failure)
        layoutFailureDuringCurrentRender = true
        if (!layoutFailureNoticeSent) {
            GraphLayoutFailureFeedback.sendRenderFailure(player)
            layoutFailureNoticeSent = true
        }
    }

    /** グラフ更新失敗を、描画変換失敗と通常の保存失敗に分けて操作者へ返します。 */
    private fun reportGraphOperationFailure(player: Player, operation: String, failure: Throwable) {
        if (GraphLayoutFailureFeedback.isLayoutFailure(failure)) {
            reportLayoutFailure(player, operation, failure)
        } else {
            plugin.logger.log(java.util.logging.Level.WARNING, operation, failure)
            player.sendMessage(GraphLayoutFailureFeedback.saveMessage(player))
        }
    }

    /**
     * グラフの表示要素を一括変換する対象か判定します。
     * 表示IDはハイフン区切りの名前空間として扱い、経路上の矢印も含めます。
     */
    private fun isMapVisual(visual: GestureGuiVisual): Boolean =
        visual.visualId.startsWith("node-") ||
            visual.visualId.startsWith("add-") ||
            visual.visualId.startsWith("path-")

    /**
     * グラフの入力要素を表示と同じ変換対象へ揃えます。
     * node-reorderもグラフ上の操作要素であるため、通常ノードと同じ縮尺・クリップを
     * 適用します。IDの名前空間を追加するときは、ここだけでなく生成側も同じ規則に従います。
     */
    private fun isMapElement(element: GestureGuiElement): Boolean =
        element.elementId.startsWith("node:") ||
            element.elementId.startsWith("node-reorder:") ||
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

    /** ナビゲーション列の各ボタンが共有する、参照画像準拠の説明欄を生成します。 */
    private fun navigationHover(player: Player, key: LocalizationKey<String>): GestureGuiHoverText =
        GestureGuiHoverText(
            text = Component.text(KcI18n.text(player, key)),
            x = GestureEditorLayout.NAV_HOVER_X,
            y = GestureEditorLayout.NAV_HOVER_Y,
            size = GestureEditorLayout.NAV_HOVER_SIZE,
            lineWidth = GestureEditorLayout.NAV_HOVER_LINE_WIDTH,
        )

    private fun rect(cx: Double, cy: Double, width: Double, height: Double): GestureGuiBounds =
        GestureGuiBounds(cx - width / 2.0, cy - height / 2.0, cx + width / 2.0, cy + height / 2.0)

    private fun scaleBounds(bounds: GestureGuiBounds, scale: Double): GestureGuiBounds =
        GestureGuiBounds(bounds.minX * scale, bounds.minY * scale, bounds.maxX * scale, bounds.maxY * scale)

    /** 十字ナビゲーション（75%サイズ・右下） */
    private fun addNavigation(
        player: Player,
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
            val hoverKey = when (quad.first) {
                "nav-up" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_UP
                "nav-down" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_DOWN
                "nav-left" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_LEFT
                "nav-right" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_RIGHT
                else -> error("unknown gesture navigation button: ${quad.first}")
            }
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
                hoverText = navigationHover(player, hoverKey),
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
    private fun insertionPreview(script: DiskScript, player: Player? = null): InsertionPreview? {
        val target = state.pendingInsertion ?: return null
        // 追加起点（ADDセル選択中）ではADDセルの選択glowがそのまま候補位置を示します。
        if (state.selectedInsertionCandidatePoint == null) return null
        return GraphLayoutEngine.previewInsertion(
            script.graph,
            target,
            onLayoutFailure = { failure ->
                player?.let {
                    reportLayoutFailure(
                        it,
                        "ジェスチャーGUIの挿入プレビューでレイアウトを生成できません: script=${script.id}",
                        failure,
                    )
                }
            },
        )
    }

    /** 描画・ナビゲーション入力で共有する、現在の永続／仮想レイアウトです。 */
    private fun currentViewportLayout(player: Player? = null): GraphLayout? {
        val test = state.testExecution
        val testActive = test?.phase == GestureTestPhase.RUNNING || test?.phase == GestureTestPhase.RESULT
        val script = test?.snapshot?.takeIf { testActive } ?: plugin.scripts.load(state.scriptId) ?: return null
        val persistedLayout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrElse { failure ->
            player?.let {
                reportLayoutFailure(
                    it,
                    "ジェスチャーGUIの現在経路確認でレイアウトを生成できません: script=${script.id}",
                    failure,
                )
            }
            return null
        }
        return if (testActive) persistedLayout else insertionPreview(script, player)?.layout ?: persistedLayout
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
            val hoverKey = if (id == "nav-zoom-in") {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ZOOM_IN
            } else {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ZOOM_OUT
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
                hoverText = navigationHover(player, hoverKey),
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
            hoverText = navigationHover(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ZOOM_RESET),
        ))
    }

    /**
     * 追従画面と現在の表示位置をトグルで切り替えるクリップ操作です。
     * 追従中は共通サービスが保持する実行時poseを凍結して固定し、固定中は
     * 解除してプレイヤー追従へ戻します。固定位置を再計算せず、クリックした
     * 瞬間の画面と入力判定を一致させます。
     */
    private fun addClipButton(
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
    ) {
        val x = GestureEditorLayout.CLIP_X
        val y = GestureEditorLayout.CLIP_Y
        val size = GestureEditorLayout.CLIP_SIZE
        val following = state.anchor == null
        visuals.add(GestureGuiVisual.Block(
            visualId = "nav-clip-block",
            x = x,
            y = y,
            width = size,
            height = size,
            blockData = Bukkit.createBlockData(
                // クリップはトグル操作です。追従中(クリップで固定できる状態)と
                // 固定中(クリップで解除できる状態)の両方が押せるため、無効表現は
                // 使わず、状態を素材の色で示します。
                if (following) Material.CYAN_CONCRETE else Material.LIGHT_BLUE_CONCRETE,
            ),
            layer = 4,
        ))
        visuals.add(GestureGuiVisual.Text(
            visualId = "nav-clip-glyph",
            x = x,
            y = y - 0.01,
            text = Component.text("@"),
            size = 0.010,
            layer = 6,
        ))
        elements.add(GestureGuiElement(
            elementId = "nav-clip",
            bounds = navBounds(x, y, size),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            // トグル自体は所有者だけが行えます。固定中も所有者は押せるため、
            // 追従状態に依存せず所有者判定だけで受け付けます。
            gestureGuard = { actor, _ -> actor.uniqueId == sessionOwnerId },
            targetVisualId = "nav-clip-glyph",
            hoverText = navigationHover(
                player,
                if (following) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CLIP
                } else {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CLIP_FIXED
                },
            ),
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
            hoverText = navigationHover(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_CLOSE),
        ))
    }

    /** 通常時はズームアウトの上、実行中はズームリセットの位置へ置くテストボタンです。 */
    private fun addTestExecutionButton(
        player: Player,
        visuals: MutableList<GestureGuiVisual>,
        elements: MutableList<GestureGuiElement>,
        running: Boolean,
    ) {
        val x = GestureEditorLayout.ZOOM_X
        val y = if (running) {
            GestureEditorLayout.ZOOM_TOP_Y - GestureEditorLayout.ZOOM_PITCH
        } else {
            GestureEditorLayout.ZOOM_TOP_Y + GestureEditorLayout.ZOOM_PITCH * 2.0
        }
        val size = GestureEditorLayout.ZOOM_SIZE
        val lamp = Bukkit.createBlockData(Material.REDSTONE_LAMP)
        if (lamp is org.bukkit.block.data.Lightable) lamp.isLit = running
        visuals.add(GestureGuiVisual.Block(
            visualId = "test-execution-block",
            x = x,
            y = y,
            width = size,
            height = size,
            blockData = lamp,
            layer = 4,
        ))
        visuals.add(GestureGuiVisual.Text(
            visualId = "test-execution-glyph",
            x = x,
            y = y - 0.01,
            text = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TEST_EXECUTION_GLYPH)),
            size = 0.010,
            layer = 6,
        ))
        elements.add(GestureGuiElement(
            elementId = "test-execution",
            bounds = navBounds(x, y, size),
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            targetVisualId = "test-execution-glyph",
            hoverText = GestureGuiHoverText(
                text = Component.text(
                    if (running) {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_RUNNING_HINT)
                    } else {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TEST_EXECUTION)
                    },
                ),
                x = x,
                y = y - size,
                size = 0.0055,
                lineWidth = 120,
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
    private fun setZoomLevel(player: Player, level: Int) {
        val script = plugin.scripts.load(state.scriptId) ?: run {
            state.zoomLevel = level
            return
        }
        val oldMetrics = viewportMetrics(zoomScale())
        val centerX = state.origin.x + (oldMetrics.columns - 1) / 2.0
        val centerY = state.origin.y + (oldMetrics.rows - 1) / 2.0
        val oldZoomLevel = state.zoomLevel
        state.zoomLevel = level
        val newMetrics = viewportMetrics(zoomScale())
        val layout = currentViewportLayout(player) ?: run {
            // レイアウトを得られない場合は倍率だけを先に確定させず、次回の再描画で
            // 画面状態と入力状態が食い違わないよう変更前へ戻します。
            state.zoomLevel = oldZoomLevel
            return
        }
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
        const val DIALOG_OWNER = "kantan-commander"
        /**
         * エディター画面全体へ適用するY方向の補正です(ブロック単位)。
         * 追従中の再計算にだけ適用され、クリップ固定位置には影響しません。
         */
        const val EDITOR_VERTICAL_OFFSET: Double = -0.5
        /** CC-SystemのOPENING完了待ちを吸収する上限（13tickのアニメーションより長くします）。 */
        const val MAX_RENDER_RETRY_TICKS = 20
    }

}
