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
import me.awabi2048.kantancommander.data.FoldedGraphs
import me.awabi2048.kantancommander.data.GraphValidator
import me.awabi2048.kantancommander.data.IncrementalValidationPolicy
import me.awabi2048.kantancommander.execution.ExecutionFinishStatus
import me.awabi2048.kantancommander.execution.ExecutionNodeFinished
import me.awabi2048.kantancommander.execution.ExecutionResult
import me.awabi2048.kantancommander.execution.NodeExecutionOutcome
import me.awabi2048.kantancommander.error.displayKey
import me.awabi2048.kantancommander.data.WorldVariableUsageScanResult
import me.awabi2048.kantancommander.gui.declarative.DeclarativeUpper
import me.awabi2048.kantancommander.gui.declarative.KantanLayoutBridge
import me.awabi2048.kantancommander.gui.declarative.SettingsActionCard
import me.awabi2048.kantancommander.gui.declarative.UpperViewportRect
import me.awabi2048.kantancommander.item.ItemStackCodec
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.setConditionSubject
import me.awabi2048.kantancommander.model.ControlBlockStateKind
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.DiskArgument
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityGroupOperation
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityPropertySupport
import me.awabi2048.kantancommander.model.EntityPropertyValueKind
import me.awabi2048.kantancommander.model.EntityTagEntries
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.PlaceItemDestination
import me.awabi2048.kantancommander.model.FireworkSettings
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.StructureMirror
import me.awabi2048.kantancommander.model.StructureRotation
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.MyworldSpawnKind
import me.awabi2048.kantancommander.model.RegionAnchorKind
import me.awabi2048.kantancommander.model.RegionAnchorSpec
import me.awabi2048.kantancommander.model.RegionSpec
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.hasDiskContent
import me.awabi2048.kantancommander.model.toggleControlBlockState
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.declaredArguments
import me.awabi2048.kantancommander.model.applyCalledDisk
import me.awabi2048.kantancommander.security.PlacementDecision
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
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
import me.awabi2048.kantancommander.gui.setting.SettingSelectionEngine

/**
 * ジェスチャーエディターの状態。プレイヤー単位で保持され、操作のたびに更新されます。
 */
data class GestureEditorState(
    var scriptId: UUID,
    var placement: DiskPlacement?,
    var origin: MapPoint = MapPoint(0, 0),
    /**
     * 描画した配置寸法の軸ごとの最大値（セル単位）です。
     *
     * 折り畳み／展開で配置が縮んでも原点を引き戻さないよう、縮小では維持し
     * 拡大でのみ追従します。FOR・分岐のいずれの折り畳みでも同じ扱いです。
     * 原点の正規化基準に使い、セッション内保持で、永続化しません。
     */
    var lastExpandedLayoutWidth: Int = 0,
    var lastExpandedLayoutHeight: Int = 0,
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
    /** 花火上書き確認が保持する対象と完全なItemStackデータ */
    var pendingFireworkContext: CommandSettingContext? = null,
    var pendingFireworkKey: String? = null,
    var pendingFireworkData: String? = null,
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
    /**
     * 詳細子画面へ入室済みの設定タブ記録です。
     *
     * プリセットの形状一致だけでは詳細入室後も選択表示が残るため、
     * 入室記録と組み合わせて選択表示を詳細側へ移します。保存値は変更しません。
     * 永続化せず、タブ・ノード切替とプリセット再適用で破棄します。
     */
    var detailVisitedKeys: MutableSet<String> = mutableSetOf(),
    /** ワールド内変数一覧だけに使うページ番号です。 */
    var variablePage: Int = 0,
    /** タグ一覧子画面だけに使うページ番号です。 */
    var tagPage: Int = 0,
    /** ワールド内変数の新規作成で、Dialogへ進む前にGestureGUIで選択した型です。 */
    var pendingWorldVariableType: ValueType? = null,
    /** ワールド内変数の削除確認で、確認対象として表示している名前です。 */
    var pendingWorldVariableDeleteName: String? = null,
    /** issue #8: ディスク呼び出し設定の引数ページ番号です。 */
    var diskCallArgsPage: Int = 0,
    /** issue #7: イベント種別の選択子画面のページ番号です。 */
    var triggerEventPage: Int = 0,
    /** issue #8: ディスク設定の引数ページ番号です。 */
    var diskSettingsArgsPage: Int = 0,
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
    /**
     * issue #7: 起動方法の種別選択子画面です。
     *
     * タイマー／信号／イベントの3択を親から分離した子画面として扱います。
     */
    TRIGGER_KIND,
    /**
     * issue #7: イベント種別の選択子画面です。
     *
     * 初期10種から一つを選び、選択時に起動方法として確定します。
     */
    TRIGGER_EVENT,
    /**
     * issue #8: ディスク呼び出しの引数・戻り値受取先の設定子画面です。
     */
    DISK_CALL_SETTINGS,
    /**
     * issue #8: ディスクの設定（取り出しと引数定義）の子画面です。
     */
    DISK_SETTINGS,
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
    /** プログラムタイマー（スクリプト全体設定）が要確認か。旧保存の従来欄用に維持します。 */
    val timer: Boolean = false,
    /** issue #7 の起動方法（スクリプト全体設定）が要確認か。 */
    val trigger: Boolean = false,
) {
    companion object {
        val EMPTY = GestureAttentionState()
    }
}

enum class GestureConfirmKind {
    DELETE,
    ITEM_OVERWRITE,
    BLOCK_OVERWRITE,
    FIREWORK_OVERWRITE,
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
    ENTITY_DATA_ROOT,
    /** issue #10: 召喚時個別設定の一覧画面です。 */
    SUMMON_DATA_ROOT,
    ENTITY_ATTRIBUTE_SELECT,
    ENTITY_TAG_LIST,
    ENTITY_PROPERTY_VALUE,
    PLACE_DESTINATION,
    ENTITY_EQUIPMENT_SLOT,
    ENTITY_OVERWRITE,
    VARIABLE_SCOPE,
    VARIABLE_NAME,
    VARIABLE_PROPERTY,
    VARIABLE_TYPE,
    VARIABLE_OPERATION,
    VARIABLE_CHANGE_MODE,
    VARIABLE_VALUE,
    CONDITION_INVERSION,
    CAMERA_SHAKE_TYPE,
    SOUND_SCOPE,
    SOUND_OPERATION,
    SOUND_TARGET_POSITION,
    PARTICLE_GEOMETRY,
    BLOCK_OPERATION,
    /** issue #11: 建造物名・回転・反転の選択画面です。 */
    STRUCTURE_NAME,
    STRUCTURE_ROTATION,
    STRUCTURE_MIRROR,
    /** issue #24: 群操作・群名の選択画面です。 */
    ENTITY_GROUP_OPERATION,
    ENTITY_GROUP_NAME,
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
 * 下部の一覧・型選択・削除確認は設定経路に応じた子画面へ配置し、その他のCONFIRMも
 * 専用の子画面へ切り替えて、親画面の操作領域と混在させません。
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
    /** 下部画面の再描画失敗でチャットを連続送信しないための通知済みフラグです。 */
    private var lowerRefreshNoticeSent = false
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
    /** テスト実行の観測者です。待機タイマーの解除に使います。 */
    private var testObserver: GestureTestExecutionObserver? = null

    private fun canViewSharedActor(ownerId: UUID, actorId: UUID): Boolean {
        val placement = state.placement ?: return actorId == ownerId
        val owner = Bukkit.getPlayer(ownerId) ?: return false
        val actor = Bukkit.getPlayer(actorId) ?: return false
        // 共有GUIの表示も配置物を操作できるプレイヤーに限定します。
        // 理由付き判定の許可部分だけを再利用し、表示だけ判定が緩くなる入口を
        // 作らないようにします。拒否理由の通知は操作時の入口で行います。
        // 距離判定は操作時だけへ分離し、遠ざかったプレイヤーの画面全体を消しません。
        return actor.world.uid == owner.world.uid &&
            actor.world.name == placement.world &&
            plugin.placementAccess.decide(actor, placement.world) == PlacementDecision.ALLOW
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
            state.pendingFireworkContext != null ||
            state.pendingFireworkKey != null ||
            state.pendingFireworkData != null ||
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
        refreshFromStore(exceptRevisionAlreadyRendered = true)
    }

    /**
     * issue #13: presence 変更の反映です。
     *
     * 内容更新（refreshFromStore）とは別経路であり、revision を進めず
     * 上部表示だけを作り直します。開閉時の即時更新に使います。
     */
    internal fun refreshPresence() {
        val owner = sessionOwnerId?.let(Bukkit::getPlayer)?.takeIf(Player::isOnline) ?: return
        updateUpper(owner)
    }

    /**
     * 保存通知による再同期です。
     *
     * issue #15 の差分同期として、自身の保存直後に届くbroadcastでは
     * 直接描画済みの同一世代を作り直しません。選択欠落がある場合だけ
     * 作り直し、他editorの stale 世代は従来どおり全量更新します。
     * presence等のephemeral状態はここで触らず、内容更新だけを扱います。
     *
     * [changedNodeIds] には保存前後で変わったノードID集合を渡し、
     * 他editorの変更で古くなった一時previewを破棄します。Dialog系の
     * 外部変更時の入力無効化と対になる扱いです。nullの場合は差分不明
     * として破棄しません。
     */
    internal fun refreshFromStore(exceptRevisionAlreadyRendered: Boolean = true, changedNodeIds: Set<UUID>? = null) {
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
        // 他editorの変更で古くなった一時previewは保存値へ戻せず残留するため破棄します。
        // 自身の確定直後はconsume済みのため何も起きません。
        if (!changedNodeIds.isNullOrEmpty()) {
            changedNodeIds.forEach(previewStore::removeNode)
        }
        val selectedNodeMissing = state.selectedNodeId?.let { it !in current.graph.nodes } == true
        val confirmedNodeMissing = state.confirmNodeId?.let { it !in current.graph.nodes } == true
        val settingNodeMissing = state.settingContext?.nodeId?.let { it !in current.graph.nodes } == true
        val insertionMissing = state.pendingInsertion?.let { target ->
            (target.sourceId != null && target.sourceId !in current.graph.nodes) ||
                (target.mergeConditionId != null && target.mergeConditionId !in current.graph.nodes)
        } == true
        val hasMissingSelection = selectedNodeMissing || confirmedNodeMissing || settingNodeMissing || insertionMissing
        // 自身の直接描画とbroadcastの二重描画を抑止します。外部変更で世代が
        // 進んだ場合や選択欠落がある場合は抑止せず、整合性を優先します。
        if (exceptRevisionAlreadyRendered && !hasMissingSelection &&
            previousRevision != null && previousRevision == current.revision &&
            lastDirectRenderedRevision == current.revision
        ) {
            return
        }
        if (selectedNodeMissing || confirmedNodeMissing || settingNodeMissing) {
            invalidateInputs()
            // 削除済みノードの一時previewは保存値へ戻せないため破棄します。
            // 削除済みUUIDへ後続のpreview確定が届かないようにします。
            state.selectedNodeId?.let(previewStore::removeNode)
            state.confirmNodeId?.let(previewStore::removeNode)
            state.settingContext?.nodeId?.let(previewStore::removeNode)
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
        // issue #15 の上部抑止です。上部は種別・接続・未設定色・選択だけを表示し
        // 値そのものを表示しないため、要点が同じ間は下部だけを作り直します。
        // テスト表示・挿入preview・追加点選択の途中は抑止せず、整合性を優先します。
        val skipUpper = !hasMissingSelection &&
            state.testExecution == null &&
            state.pendingInsertion == null &&
            state.selectedInsertionCandidatePoint == null &&
            state.selectedAddPoint == null &&
            lastUpperStructureSignature != null &&
            GraphStructureFingerprint.fingerprint(current.graph) == lastUpperStructureSignature &&
            currentIncompleteForSkip(current) == lastUpperIncomplete &&
            state.selectedNodeId == lastUpperSelection
        if (skipUpper) {
            updateLower(owner)
        } else {
            updateUpper(owner)
            updateLower(owner)
        }
    }

    /**
     * 上部抑止の比較用に、現在の未設定集合を求めます。
     *
     * [resolveUpperGraph] と同じ対応付け規則を使い、表示上の差の有無だけを
     * 判定します。検証結果自体は世代cacheを使い回します。
     */
    private fun currentIncompleteForSkip(script: DiskScript): Set<java.util.UUID> = buildSet {
        validatedErrors(script).forEach { error ->
            error.nodeId?.let { nodeId ->
                FoldedGraphs.outermostWrapperId(script.graph, nodeId)?.let(::add)
            }
        }
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

    /**
     * 「閉じる」ボタンからの終了です。閉じるアニメーションを再生します。
     *
     * 終了ジェスチャー（Shift+Jump）や内部的な破棄は即時終了のままにし、
     * アニメーションは明示のボタン操作に限定します。
     */
    internal fun closeWithAnimation(ownerId: UUID) {
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
            api.close(ownerId, com.awabi2048.ccsystem.api.gesturegui.GestureGuiCloseMode.ANIMATED)
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
                // 画面の上下傾きを半分にします。
                tiltScale = EDITOR_TILT_SCALE,
                // 目位置から画面までの距離を標準の25%増しにし、視界の圧迫を抑えます。
                screenDistance = EDITOR_FOLLOW_DISTANCE,
            ),
        )
        gestureSessionId = snapshot.sessionId
    }

    fun updateUpper(player: Player): Boolean {
        val owner = ownerPlayerFor(player)
        val updated = updateScreen(owner, buildUpperViewport(owner), RenderTarget.UPPER)
        // 直接描画とbroadcastの二重描画を抑止するため、描画済み世代を記録します。
        // 他editorのstale判定には使わず、このeditorの同一世代再描画抑止にだけ使います。
        if (updated) lastDirectRenderedRevision = observedRevision
        return updated
    }

    /**
     * 保存成功後の上部更新です。表示内容が変わらない場合は作り直しません。
     *
     * issue #15 の部分再構築として、上部が種別・接続・未設定色・選択だけを
     * 表示することを利用し、前回描画時の要点と一致すれば抑止します。
     * 下部は値表示のため呼び出し側で必ず更新します。
     *
     * 適用条件は保存成功経路だけにします。以下を変える経路では使わず、
     * [updateUpper] で無条件に作り直します。
     * - 選択・原点・ズーム・固定（表示要点の追跡対象外）
     * - テスト状態・挿入preview・追加点選択（毎tick変化し得る一時表示）
     *
     * 戻り値は上部が最新であることを表します。抑止時も最新のためtrueを返し、
     * 描画失敗時は [updateUpper] の結果をそのまま返します。
     */
    private fun updateUpperIfNeeded(player: Player): Boolean {
        val current = plugin.scripts.load(state.scriptId) ?: return updateUpper(player)
        if (state.testExecution != null) return updateUpper(player)
        if (state.pendingInsertion != null ||
            state.selectedInsertionCandidatePoint != null ||
            state.selectedAddPoint != null
        ) {
            return updateUpper(player)
        }
        val signature = lastUpperStructureSignature ?: return updateUpper(player)
        if (GraphStructureFingerprint.fingerprint(current.graph) != signature) return updateUpper(player)
        if (currentIncompleteForSkip(current) != lastUpperIncomplete) return updateUpper(player)
        if (state.selectedNodeId != lastUpperSelection) return updateUpper(player)
        return true
    }

    fun updateLower(player: Player): Boolean {
        val owner = ownerPlayerFor(player)
        return runCatching {
            updateScreen(owner, buildCurrentLowerView(owner), RenderTarget.LOWER).also { updated ->
                // 再描画の成功で通知抑制を解除します。
                if (updated) lowerRefreshNoticeSent = false
            }
        }.getOrElse {
            // 下部画面の再描画は保存処理の成功条件ではありません。保存済みデータの
            // 破損でここが失敗しても、入力を「形式不正」と誤判定しないよう、GUI境界で
            // 失敗をBooleanへ閉じ込めます。再描画の失敗自体は操作者への通知へ
            // 一本化します。連続操作で重複通知にならないよう成功まで抑制し、
            // コンソールへ出しません。
            if (!lowerRefreshNoticeSent) {
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
                lowerRefreshNoticeSent = true
            }
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
                // issue #7: 起動方法の選択子画面です。子画面の内容だけを差し替えます。
                GestureLowerMode.TRIGGER_KIND -> lowerPanel.buildTriggerKindChild(state, owner)
                GestureLowerMode.TRIGGER_EVENT -> lowerPanel.buildTriggerEventChild(state, owner)
                // issue #8: 呼び出し設定の子画面です。
                GestureLowerMode.DISK_CALL_SETTINGS -> lowerPanel.buildDiskCallSettingsChild(state, owner)
                // issue #8: ディスク設定の子画面です。
                GestureLowerMode.DISK_SETTINGS -> lowerPanel.buildDiskSettingsChild(state, owner)
                else -> lowerPanel.build(state, owner, attention, fieldPreviews = previewStore.snapshot())
            }
            else -> lowerPanel.build(state, owner, attention, fieldPreviews = previewStore.snapshot())
        }
    }

    /**
     * 世代単位の検証結果です。上下画面の重複検証を1回にします。
     *
     * 同一世代の再検証は同一結果になるため、スクリプトIDと世代で保持します。
     * 保存成功で世代が進むと自動的に再検証します。
     */
    private var validationCacheKey: Pair<java.util.UUID, Long>? = null
    private var validationCacheErrors: List<me.awabi2048.kantancommander.data.ScriptValidationError> = emptyList()

    /**
     * issue #15 の差分更新基盤です。値変更だけの保存で全量レイアウトを
     * 繰り返さないよう、構造署名単位で配置結果を使い回します。
     * 無効化条件は構造署名の変化に一元化し、設定上限の変更時だけ破棄します。
     */
    private val layoutCache = GraphLayoutCache()

    /**
     * 構造検証エラーの署名単位キャッシュです。値変更だけでは構造由来の
     * エラーは変わらないため、世代が進んでも構造署名が同じ間は再利用します。
     * 項目検証の全量走査は [validatedErrors] 側に残し、ここでは構造部分の
     * 再計算抑止だけを担当します。
     */
    private var structuralValidationCacheKey: String? = null
    private var structuralValidationErrors: List<me.awabi2048.kantancommander.data.GraphStructuralError> = emptyList()

    /**
     * 変数定義付きのテスト可否検証のTTLキャッシュです。
     *
     * 世代管理の対象外（ワールド変数の変更ではscript revisionが進まない）のため
     * 世代cacheにはせず、短い鮮度で再計算を抑止します。上部再構築ごとの
     * 全量検証＋MyWorld参照を間引き、ボタン表示のちらつきなく保ちます。
     * 無効化条件はキー（script＋世代）の変化・TTL超過・セッション終了に一元化します。
     */
    private val testValidationCache =
        TtlCache<Pair<java.util.UUID, Long>, List<me.awabi2048.kantancommander.data.ScriptValidationError>>(
            ttlMillis = 1500L,
        )

    /**
     * Slider等の連続previewを永続保存へ直結させない一時保管場所です。
     * session-localに保持し、commit時にだけrevisionを進めます。
     * 他editorへの共有は低頻度・明示channelに限定します。
     */
    private val previewStore = TransientPreviewStore()
    private var lastPreviewBroadcastAtMillis: Long = 0L

    /**
     * 自身の保存直後に届くbroadcast再描画を抑止する世代記録です。
     * 保存成功経路は直接描画済みのため、同一世代のbroadcastでは
     * 選択欠落がない限り再構築しません。他editorの stale 世代には影響しません。
     */
    private var lastDirectRenderedRevision: Long? = null

    /**
     * issue #15 の上部抑止判定に使う、前回描画時の表示内容の要点です。
     *
     * 上部のノード表示は種別・接続・未設定色・選択だけに依存し、値そのものを
     * 表示しないため、署名・未設定集合・選択が同じ間は作り直しません。
     * テスト表示・挿入preview中は記録せず、抑止判定から外します。
     */
    private var lastUpperStructureSignature: String? = null
    private var lastUpperIncomplete: Set<java.util.UUID> = emptySet()
    private var lastUpperSelection: java.util.UUID? = null

    /** 世代キャッシュ付きの実行前検証です。変数定義付きの検証には使いません。 */
    private fun validatedErrors(
        script: me.awabi2048.kantancommander.model.DiskScript,
        baseline: Boolean = true,
    ): List<me.awabi2048.kantancommander.data.ScriptValidationError> {
        val key = script.id to script.revision
        if (validationCacheKey != key) {
            // issue #15 の増分検証として、値変更だけの世代進行では前回結果から
            // 変更ノード分だけを再検証します。全量が必要な場合は従来どおりです。
            val incremental = if (baseline) tryIncrementalValidation(script) else null
            if (incremental != null) {
                validationCacheErrors = incremental
                // 構造不変のため署名cacheは有効なままです。構造署名の一致は
                // 増分側の分類で保証されています。
            } else {
                validationCacheErrors = ExecutableScriptValidator.validate(script, plugin.graphLimits())
                // 世代が進んでも構造署名が同じ間は構造エラーを再利用できるよう、
                // 構造部分だけを署名単位で保持します。無効化条件は署名変化に一元化します。
                val signature = GraphStructureFingerprint.fingerprint(script.graph)
                if (structuralValidationCacheKey != signature) {
                    structuralValidationCacheKey = signature
                    structuralValidationErrors = GraphValidator.validateStructured(script.graph, plugin.graphLimits())
                }
            }
            validationCacheKey = key
            // テストsnapshotは正本の基準にせず、永続世代の検証基準だけを保持します。
            if (baseline) {
                lastValidatedScriptId = script.id
                lastValidatedGraph = script.graph
                lastValidationSeeds =
                    me.awabi2048.kantancommander.data.ExecutableScriptValidator.seedTemporaries(script)
            }
        }
        return validationCacheErrors
    }

    /**
     * 増分検証の前回基準です。テストsnapshotの混入を防ぐため、
     * 永続世代の検証時にだけ保持します。終了時に破棄します。
     */
    private var lastValidatedScriptId: java.util.UUID? = null
    private var lastValidatedGraph: me.awabi2048.kantancommander.model.CommandGraph? = null
    /**
     * issue #7/#8: 増分検証の前回実行開始時定義表です。
     *
     * 引数定義・イベント種別の変更は全ノードの定義可視性に波及するため、
     * 不一致時は増分検証を使わず全量へ委譲します。
     */
    private var lastValidationSeeds: Map<String, me.awabi2048.kantancommander.model.ValueType> = emptyMap()

    /**
     * 前回検証結果からの増分更新を試みます。
     *
     * 全量が必要な場合は null を返し、呼び出し側は従来の全量検証を使います。
     * 同一世代の再検証は呼び出し側の世代cacheで弾かれるため、ここでは
     * 世代進行後の差分だけを扱います。
     */
    private fun tryIncrementalValidation(
        script: me.awabi2048.kantancommander.model.DiskScript,
    ): List<me.awabi2048.kantancommander.data.ScriptValidationError>? {
        val previousGraph = lastValidatedGraph ?: return null
        if (lastValidatedScriptId != script.id) return null
        return me.awabi2048.kantancommander.data.IncrementalValidator.refresh(
            previousGraph = previousGraph,
            previousErrors = validationCacheErrors,
            script = script,
            limits = plugin.graphLimits(),
            variableDefinitions = null,
            previousSeeds = lastValidationSeeds,
        )
    }

    /**
     * 構造署名単位で再利用できる構造エラーを返します。
     *
     * 値変更だけの保存では世代が進んでも署名が変わらないため再計算しません。
     * 構造署名が変わった場合だけ再検証し、無効化条件を一箇所へ集約します。
     * 現段階では [validatedErrors] 側の世代cacheを維持しつつ構造cacheを暖め、
     * 将来の項目検証の増分化への接続点を残します。
     */
    internal fun structuralErrors(graph: me.awabi2048.kantancommander.model.CommandGraph): List<me.awabi2048.kantancommander.data.GraphStructuralError> {
        val signature = GraphStructureFingerprint.fingerprint(graph)
        if (structuralValidationCacheKey != signature) {
            structuralValidationCacheKey = signature
            structuralValidationErrors = GraphValidator.validateStructured(graph, plugin.graphLimits())
        }
        return structuralValidationErrors
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
        val errors = validatedErrors(script)
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
            // issue #7: 明示の起動方法と旧来のタイマー欄のどちらも起動方法カードへ集約します。
            trigger = errors.any { it.nodeId == null && ("trigger" in it.fieldKeys || "timer" in it.fieldKeys) },
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
        // セッション競合の更新拒否は定常のためコンソールへ出しません。
        if (snapshot.state != GestureGuiSessionState.OPENING) {
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
                // 再試行の打切りは定常のためコンソールへ出しません。
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
            // ACTIVE後の更新失敗は定常のためコンソールへ出しません。
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
        // 配置・ワールドの存在確認は安価なため毎回行い、高価な全量検証だけを
        // 短い鮮度で使い回します。ワールド変数の変更は最大TTLだけ遅れて
        // ボタン表示へ反映されますが、実行開始時の成否表示には影響しません。
        return testValidationCache.getOrCompute(script.id to script.revision) {
            ExecutableScriptValidator.validate(
                script,
                plugin.graphLimits(),
                plugin.variables.definitions(worldData.uuid),
            )
        }
    }

    /**
     * 実行可能なコマンド数です。制御ノードも件数に含めます。
     * 0件のときはテスト実行ボタンを出さず、バリア表示で理由を案内します。
     */
    private fun executableCommandCount(): Int =
        plugin.scripts.load(state.scriptId)?.graph?.nodes?.size ?: 0

    private fun canStartTest(): Boolean {
        val placement = state.placement ?: return false
        return !plugin.testExecution.isActive(placement.key) &&
            plugin.scripts.load(state.scriptId) != null &&
            executableCommandCount() > 0 &&
            testValidationErrors().isEmpty()
    }

    /**
     * テスト実行を開始できない短縮理由です。開始できるときはnullを返します。
     * バリア表示のホバーへ出し、押下時は何もしないため確定判定と対にします。
     */
    private fun testUnavailableReason(player: Player): String? {
        val script = plugin.scripts.load(state.scriptId)
        if (script == null || script.graph.nodes.isEmpty()) {
            return KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_SHORT_EMPTY)
        }
        val placement = state.placement
        if (placement != null && plugin.testExecution.isActive(placement.key)) {
            return KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_SHORT_BUSY)
        }
        val errors = testValidationErrors()
        if (errors.isEmpty()) return null
        return KcI18n.text(
            player,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_SHORT_ERRORS,
            mapOf("count" to errors.size),
        )
    }

    /** テスト確認子画面を開きます。グラフの固定は「確定」クリック時に行います。 */
    private fun openTestConfirmation(player: Player) {
        if (!canStartTest() || state.testExecution != null) return
        // テストsnapshotは保存済み正本から作るため、未確定の一時previewは
        // 持ち込まず破棄します。確定値だけがテスト対象になります。
        previewStore.cancel()
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
            snapshot = current.copy(graph = FoldedGraphs.expand(current.graph.deepCopy())),
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
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
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

    /**
     * 全体を映すのに必要な最小ズームで表示し、指定ノードが端から切れない原点を求めます。
     * 設定中のズームが全体収納より広域の場合は何もせず、狭域の場合だけ
     * 全体収納まで引きます。追従が発生するのは最小ズームでも収まらない場合に限られます。
     */
    private fun fitTestViewport(targetNodeId: UUID? = null) {
        val test = state.testExecution ?: return
        val layout = runCatching { GraphLayoutEngine.layout(test.snapshot.graph) }.getOrNull()
            ?.withoutEditArtifacts(test.passedEdges) ?: return
        val fitLevel = GestureEditorLayout.fitZoomLevel(layout.width, layout.height)
        // 現在の倍率が必要倍率より小さい（広域）場合は、利用者の表示を維持します。
        // 原点の中心合わせは広域のまま行うため、ここでは倍率だけを据え置きます。
        if (GestureEditorLayout.zoomScaleFor(state.zoomLevel) >= GestureEditorLayout.zoomScaleFor(fitLevel)) {
            state.zoomLevel = fitLevel
        }
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
        test.snapshot = current.copy(graph = FoldedGraphs.expand(current.graph.deepCopy()))
        test.startedAtTick = plugin.server.currentTick.toLong()
        test.phase = GestureTestPhase.RUNNING
        state.lowerMode = GestureLowerMode.TEST_STATUS
        if (testConfirmationChildOpen(test.ownerId)) {
            api.closeChild(test.ownerId, lowerPanel.TEST_CONFIRM_SCREEN_ID)
        }
        fitTestViewport()
        playTestSound(player, TEST_SOUND_START_PITCH)
        // 開始を操作者へ通知します。
        player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_STARTED))
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
            plugin = plugin,
            scheduler = plugin.server.scheduler,
        )
        testObserver = observer
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
        // 経過表示は秒単位・1Hzで十分なため、毎tickの全量再構築は行いません。
        // 上部の色・通過表示は状態変化イベントで即時反映します。
        testRefreshTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val test = state.testExecution
            if (test?.phase != GestureTestPhase.RUNNING) {
                testRefreshTask?.cancel()
                testRefreshTask = null
                return@Runnable
            }
            test.elapsedTicks = (plugin.server.currentTick.toLong() - test.startedAtTick).coerceAtLeast(0L)
            onTestTimerTick()
        }, 1L, TEST_STATUS_REFRESH_PERIOD_TICKS)
    }

    /** 1Hzの経過表示更新です。下部の秒表示だけを更新し、上部は再構築しません。 */
    private fun onTestTimerTick() {
        val owner = sessionOwnerId?.let(Bukkit::getPlayer)?.takeIf(Player::isOnline) ?: return
        updateLower(owner)
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
        testObserver?.cancelPendingWait()
        testObserver = null
        // 終了音と終了通知は操作者だけへ出します。
        // 中断時は中断操作時の通知が既にあるため、ここでは何もしません。
        Bukkit.getPlayer(test.ownerId)?.takeIf(Player::isOnline)?.let { owner ->
            when (result.status) {
                ExecutionFinishStatus.SUCCESS -> {
                    playTestSound(owner, TEST_SOUND_SUCCESS_PITCH)
                    owner.sendMessage(KcI18n.text(owner, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_FINISHED_SUCCESS))
                }
                ExecutionFinishStatus.FAILURE -> {
                    playTestSound(owner, TEST_SOUND_FAILURE_PITCH)
                    owner.sendMessage(KcI18n.text(owner, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_FINISHED_FAILURE))
                }
                ExecutionFinishStatus.CANCELLED -> Unit
            }
        }
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
        testObserver?.cancelPendingWait()
        testObserver = null
        // テスト前後で一時previewを持ち越さず、保存値との不整合を残しません。
        previewStore.cancel()
        state.testExecution = null
        state.lowerMode = test.originalLowerMode
        onTestStateChanged()
    }

    private fun interruptTestWithoutResult(reason: String) {
        val test = state.testExecution ?: return
        Bukkit.getPlayer(test.ownerId)?.takeIf(Player::isOnline)?.let { playTestSound(it, TEST_SOUND_INTERRUPT_PITCH) }
        notifyTestInterrupted(test)
        plugin.testExecution.cancel(test.scopeKey, showResult = false, reason = reason)
        // Coordinatorがすでに完了させた競合状態でも、必ずローカル表示を
        // 通常画面へ戻します。通常はcancel()内の観測者が先に同じ処理を行います。
        if (state.testExecution === test) discardTestWithoutResult()
    }

    private fun interruptTestWithResult() {
        val test = state.testExecution ?: return
        Bukkit.getPlayer(test.ownerId)?.takeIf(Player::isOnline)?.let { playTestSound(it, TEST_SOUND_INTERRUPT_PITCH) }
        notifyTestInterrupted(test)
        plugin.testExecution.cancel(test.scopeKey, showResult = true, reason = "manual_stop")
    }

    /** テスト実行の中断を、操作者と共有中のプレイヤーへ通知します。 */
    private fun notifyTestInterrupted(test: GestureTestState) {
        val ownerId = test.ownerId
        val recipients = linkedSetOf<Player>()
        Bukkit.getPlayer(ownerId)?.takeIf(Player::isOnline)?.let(recipients::add)
        if (state.placement != null) {
            plugin.server.onlinePlayers
                .filter { it.uniqueId != ownerId && canViewSharedActor(ownerId, it.uniqueId) }
                .forEach(recipients::add)
        }
        recipients.forEach { recipient ->
            recipient.sendMessage(KcI18n.text(recipient, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_INTERRUPTED))
        }
    }

    /** テスト実行の節目に操作者だけへ鳴らす効果音です。 */
    private fun playTestSound(player: Player, pitch: Float) {
        player.playSound(
            player.location,
            "minecraft:block.note_block.bit",
            SoundCategory.MASTER,
            1.0f,
            pitch,
        )
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
        testObserver?.cancelPendingWait()
        testObserver = null
        // テスト前後で一時previewを持ち越さず、保存値との不整合を残しません。
        previewStore.cancel()
        updateUpper(player)
        updateLower(player)
    }

    /**
     * テスト実行のログを操作者へ送ります。実行番号は#1から数え始め、
     * ホバーは項目ごとに見出しを付けて構造的に表示します。
     * 実行位置は常に操作者のいるMyWorldのため、ワールド名は出しません。
     */
    private fun sendTestLog(event: ExecutionNodeFinished) {
        val ownerId = state.testExecution?.ownerId ?: return
        val player = Bukkit.getPlayer(ownerId)?.takeIf(Player::isOnline) ?: return
        if (!player.isOnline) return
        val command = KcI18n.text(player, event.nodeType.key)
        // 内部の通し番号は0始まりのため、表示時に#1始まりへ補正します。
        val attempt = event.attemptNumber + 1
        // issue #6 の SKIPPED 分離です。失敗でも成功でもなく、成功ログへ畳みません。
        // 新規キー追加なしで区別するため、文面は成功系を流用し、結果色と原因で示します。
        val key = if (event.outcome == NodeExecutionOutcome.FAILED) {
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_LOG_FAILURE
        } else {
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_TEST_LOG_SUCCESS
        }
        val text = KcI18n.text(
            player,
            key,
            mapOf("attempt" to attempt, "command" to command),
        )
        val resultWord = if (event.outcome == NodeExecutionOutcome.FAILED) {
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_FAILURE)
        } else {
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_SUCCESS)
        }
        val resultColor = when (event.outcome) {
            NodeExecutionOutcome.FAILED -> NamedTextColor.RED
            NodeExecutionOutcome.SKIPPED -> NamedTextColor.YELLOW
            else -> NamedTextColor.GREEN
        }
        val hover = Component.text()
            .append(Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_LOG_COMMAND), NamedTextColor.GRAY))
            .append(Component.text(": ", NamedTextColor.GRAY))
            .append(Component.text(command, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_LOG_RESULT), NamedTextColor.GRAY))
            .append(Component.text(": ", NamedTextColor.GRAY))
            .append(Component.text(resultWord, resultColor))
        event.detail?.takeIf(String::isNotBlank)?.let { detail ->
            hover.append(Component.newline())
                .append(Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_LOG_DETAIL), NamedTextColor.GRAY))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(detail, NamedTextColor.WHITE))
        }
        // 実効値は項目ごとに1行ずつ出し、実行位置は専用の見出しへ出します。
        // 項目名はエディタ内の設定項目名へ合わせ、対応がない内部キーはそのまま出します。
        val loggedNode = state.testExecution?.snapshot?.graph?.nodes?.get(event.nodeId)
        event.expandedParams.forEach { (key, value) ->
            val label = TestLogParamPresentation.labelKey(event.nodeType, loggedNode, key)
                ?.let { KcI18n.text(player, it) } ?: key
            hover.append(Component.newline())
                .append(Component.text("  $label: ", NamedTextColor.GRAY))
                .append(Component.text(value, NamedTextColor.WHITE))
        }
        event.positionText?.takeIf(String::isNotBlank)?.let { position ->
            hover.append(Component.newline())
                .append(Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_LOG_POSITION), NamedTextColor.GRAY))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(position, NamedTextColor.WHITE))
        }
        event.reason?.takeIf(String::isNotBlank)?.let { reason ->
            hover.append(Component.newline())
                .append(Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_CAUSE), NamedTextColor.GRAY))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(testReasonText(player, reason), NamedTextColor.WHITE))
        }
        val component = Component.text(text).hoverEvent(
            net.kyori.adventure.text.event.HoverEvent.showText(hover.build()),
        )
        player.sendMessage(component)
    }

    /**
     * 失敗・スキップ理由コードに対応する表示文です。
     *
     * issue #6 の共通分類として code で解決し、原文フォールバックは行いません。
     * 未知コードは汎用失敗キーへ寄せ、code 自体の区別はログ・テストで保持します。
     */
    private fun testReasonText(player: Player, reason: String): String {
        val key = when (reason) {
            "command_failed" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_COMMAND_FAILED
            "node_failed" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_NODE_FAILED
            "invalid_wait_seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_INVALID_WAIT_SECONDS
            "command_limit" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_COMMAND_LIMIT
            "missing_node" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_MISSING_NODE
            "manual_stop" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_MANUAL_STOP
            "test_start_rejected" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_TEST_START_REJECTED
            "outside_myworld" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_OUTSIDE_MYWORLD
            else -> null
        }
        if (key != null) return KcI18n.text(player, key)
        // 新規コードは共通分類の表示キーへ解決し、未知は汎用キーへ寄せます。
        val code = me.awabi2048.kantancommander.error.ErrorCode.fromLegacyReason(reason)
        if (code != null) return KcI18n.text(player, code.displayKey())
        return KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_REASON_NODE_FAILED)
    }

    fun openConfirmChild(player: Player) {
        val ownerId = ownerIdFor(player)
        if (api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.CONFIRM_SCREEN_ID) == true) return
        val settingChildWasOpen = settingChildOpen(ownerId)
        val parentId = if (settingChildWasOpen) currentSettingChildScreenId() else lowerPanel.LOWER_SCREEN_ID
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
                    parentScreenId = parentId,
                    overlayMaterial = Material.RED_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
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

    /** 現在の設定フレームに対応する、物理子画面のIDです。 */
    private fun currentSettingChildScreenId(): String =
        lowerPanel.settingChildScreenIdForRoute(state.settingRoute.size)

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
        // 親ハイライトの解除失敗は装飾残留のみのためコンソールへ出しません。
        api.updateScreen(ownerId, parentView)
        return true
    }

    /** 個別設定子画面を開き、既に開いている場合は差分更新だけを行います。 */
    private fun ensureSettingChild(player: Player, parentView: GestureGuiView? = null) {
        state.lowerMode = GestureLowerMode.SETTING_CHOICES
        val ownerId = ownerIdFor(player)
        val childDepth = GestureSettingChildScreenIds.depthForRouteSize(state.settingRoute.size)
        val childScreenId = lowerPanel.settingChildScreenIdForDepth(childDepth)
        val existingChildIds = api.snapshot(ownerId)?.childScreenIds.orEmpty()
        if (childScreenId in existingChildIds) {
            updateLower(player)
            return
        }
        val parentScreenId = if (childDepth == 1) {
            lowerPanel.LOWER_SCREEN_ID
        } else {
            lowerPanel.settingChildScreenIdForDepth(childDepth - 1)
        }
        if (childDepth > 1 && parentScreenId !in existingChildIds) {
            // 祖先子画面が先に閉じられた場合、その先だけは積めません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            resetSettingChildFailure(player, ownerId)
            return
        }
        val owner = ownerPlayerFor(player)
        val attention = attentionState()
        val effectiveParentView = parentView ?: if (childDepth == 1) {
            lowerPanel.build(
                state,
                owner,
                attention,
                suppressHighlight = true,
            )
        } else {
            // 深い画面の親ビューはpushSettingFrameで遷移前に構築して渡します。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            resetSettingChildFailure(player, ownerId)
            return
        }
        val opened = runCatching {
            openChildAndSuppressParentHighlight(
                ownerId,
                effectiveParentView,
                lowerPanel.buildSettingChild(state, owner, attention),
                GestureGuiChildOptions(
                    parentScreenId = parentScreenId,
                    overlayMaterial = Material.GRAY_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            false
        }
        if (!opened) {
            // セッションが終了している／子深度上限に達している等の場合は、
            // 孤立した設定状態を残さず通常の設定画面へ戻します。
            resetSettingChildFailure(player, ownerId)
        }
    }

    /** 入れ子の子画面を作れなかった場合は、古い子画面も閉じて設定のルートへ戻します。 */
    private fun resetSettingChildFailure(player: Player, ownerId: UUID) {
        if (settingChildOpen(ownerId)) {
            api.closeChild(ownerId, lowerPanel.SETTING_CHILD_SCREEN_ID)
        }
        clearSettingState()
        state.lowerMode = GestureLowerMode.SETTINGS
        updateLower(player)
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
                // 終了時の後片付けの失敗は定常のためコンソールへ出しません。
                runCatching {
                    api.closeExternalDialogIfCurrent(
                        ownerId,
                        sessionId,
                        player,
                        DIALOG_OWNER,
                        input.dialogId,
                    )
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
        // issue #15 のlifecycle保証です。表示・一時値・署名cacheを残留させず、
        // open／close・disconnectの繰返しでも leak しないようにします。
        // 実行本体の継続有無とは独立に、画面専用の観測参照だけを破棄します。
        testObserver?.cancelPendingWait()
        testObserver = null
        state.testExecution = null
        previewStore.cancel()
        lastPreviewBroadcastAtMillis = 0L
        pendingSoundDialogValues.clear()
        validationCacheKey = null
        validationCacheErrors = emptyList()
        structuralValidationCacheKey = null
        structuralValidationErrors = emptyList()
        lastValidatedScriptId = null
        lastValidatedGraph = null
        testValidationCache.clear()
        iconTemplateCache.clear()
        litRedstoneLampData = null
        litCopperBulbData = null
        lastUpperStructureSignature = null
        lastUpperIncomplete = emptySet()
        lastUpperSelection = null
        lastDirectRenderedRevision = null
        lastGraphShadowSignature = ""
        layoutFailureNoticeSent = false
        layoutFailureDuringCurrentRender = false
        if (sessionId != null) {
            // Facade側の通知失敗でエディター自身のセッションIDを残すと、
            // 後続の入力を現行セッションと誤認するため、通知例外を隔離します。
            // 終了時の後片付けの失敗は定常のためコンソールへ出しません。
            runCatching { onSessionClosed(this, ownerId, sessionId) }
        }
        gestureSessionId = null
        sessionOwnerId = null
        observedRevision = null
    }

    /**
     * issue #15 と #18 の共有契約である preview／commit の編集側入口です。
     *
     * preview値は保存・検証・revisionへ触れず、下部表示の更新にだけ使います。
     * commit時に [updateSettingNode] と同じ保存境界を通し、cancel時は保存値へ戻します。
     * 他editorへの共有が必要な場合は [maybeBroadcastPreview] で間引いて送ります。
     */
    internal fun beginFieldPreview(player: Player, revisionBase: Long? = null) {
        previewStore.begin(revisionBase ?: observedRevision)
    }

    internal fun updateFieldPreview(player: Player, nodeId: java.util.UUID, fieldKey: String, previewValue: String?) {
        if (previewStore.revisionBase == null) previewStore.begin(observedRevision)
        previewStore.update(nodeId, fieldKey, previewValue)
        updateLower(player)
        maybeBroadcastPreview(player)
    }

    internal fun previewValue(nodeId: java.util.UUID, fieldKey: String): String? =
        previewStore.current(nodeId, fieldKey)

    internal fun hasFieldPreview(): Boolean = previewStore.hasPreview

    /**
     * issue #15 と #18 の共有契約であるpreview確定の編集側入口です。
     *
     * [apply] には (nodeId, fieldKey) から保存境界への適用だけを実装します。
     * 取出し・失敗時復元・結果集約は [commitPreviews] に任せ、確定成功分は
     * [apply] 側の保存境界（[updateSettingNode] 等）でrevisionを進めます。
     * 描画は [apply] 側で行い、ここでは行いません。
     */
    internal fun commitFieldPreviews(apply: (FieldPreviewKey, String?) -> Boolean): PreviewCommitResult =
        commitPreviews(previewStore, apply)

    internal fun cancelFieldPreview(player: Player) {
        if (!previewStore.hasPreview) return
        previewStore.cancel()
        updateLower(player)
    }

    /**
     * previewを破棄して保存値へ戻します。ワールド移動・中断時の残留防止に使います。
     *
     * 保存は行わず、下部表示だけを作り直します。
     */
    internal fun discardFieldPreview(player: Player) {
        cancelFieldPreview(player)
    }

    /**
     * 低頻度の共有previewを必要に応じて送ります。
     *
     * 常時全送信は行わず、最終値のcommitだけを正本へ反映します。
     * 共有表示自体は将来の#13 presence拡張で利用し、ここでは要点の配布に留めます。
     */
    internal fun maybeBroadcastPreview(player: Player) {
        if (!previewStore.hasPreview) return
        val now = System.currentTimeMillis()
        if (!previewStore.shouldSharePreview(now, lastPreviewBroadcastAtMillis)) return
        lastPreviewBroadcastAtMillis = now
        val summary = SharedPreviewSummary(
            scriptId = state.scriptId,
            revisionBase = previewStore.revisionBase,
            previews = previewStore.snapshot(),
            updatedAtMillis = now,
        )
        plugin.gestureEditor.broadcastPreview(summary, this)
    }

    /**
     * 他editorからの共有previewを受け取ります。
     *
     * 正本・revision・検証へは触れず、表示上の要点としてのみ扱います。
     * 現段階では受信を破棄せず保持し、将来の共有表示へ接続できるようにします。
     */
    internal fun receiveSharedPreview(summary: SharedPreviewSummary) {
        if (summary.scriptId != state.scriptId) return
        if (summary.previews.isEmpty()) return
        // 現段階では保存値への適用も再描画の強制も行いません。
        // 低頻度channelの疎通確認と将来の共有表示への接続点を残すことが目的です。
    }

    /**
     * issue #13: 共同編集者の表示専用カードです。
     *
     * 自分以外の編集者がいる場合だけ文言付きカードを返し、いない場合は
     * null です。操作面を持たず、ホバーで編集者名を示します。文言は
     * 先頭2名までとし、全員はホバーで確認します。
     */
    private fun presenceCard(player: Player): SettingsActionCard? {
        val others = plugin.gestureEditor.presenceFor(state.scriptId)
            .filter { it != player.uniqueId }
            .mapNotNull { Bukkit.getPlayer(it)?.name ?: Bukkit.getOfflinePlayer(it).name }
        if (others.isEmpty()) return null
        val label = others.take(2).joinToString(", ")
        var hover = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_EDITING))
        others.forEach { name ->
            hover = hover.append(Component.newline()).append(Component.text(name))
        }
        return SettingsActionCard(
            elementId = null,
            label = Component.text(label).color(NamedTextColor.YELLOW),
            background = blockDataOf(Material.PLAYER_HEAD),
            cx = GestureEditorLayout.PRESENCE_X,
            cy = GestureEditorLayout.PRESENCE_Y,
            w = GestureEditorLayout.PRESENCE_SIZE,
            h = GestureEditorLayout.PRESENCE_SIZE,
            labelSize = 0.008,
            labelWidth = 90,
            labelDy = -0.01,
            gestures = emptySet(),
            guard = null,
            hover = navigationHoverText(hover),
            visualTag = "presence",
        )
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
            buildUpperViewportContentNew(player)
        } catch (failure: RuntimeException) {
            reportLayoutFailure(
                player,
                "ジェスチャーGUIの画面定義生成に失敗しました: script=${state.scriptId}",
                failure,
            )
            layoutErrorView(player)
        }

    /**
     * 素材単位の表示素材です。毎回の生成を避けるため保持します。
     *
     * 保持物は生成後に変更しません。描画側も読取りだけに用います。
     * 生成後に変更する素材（点灯切替え等）はここへ入れず個別生成します。
     * GUI更新はメインスレッドからのみ行います。
     */
    private val blockDataCache = mutableMapOf<Material, BlockData>()

    /** 共有の表示素材を返します。変更せずに表示専用として用います。 */
    private fun blockDataOf(material: Material): BlockData =
        blockDataCache.getOrPut(material) { Bukkit.createBlockData(material) }

    /**
     * 点灯状態が固定の表示素材です。無条件の共有欄へは入れず用途別に保持します。
     *
     * 生成後に点灯切替えを行わない固定表示だけを対象にし、描画側も読取りだけに
     * 用います。テストボタンの状態表示はこの3通りに固定されています。
     * GUI更新はメインスレッドからのみ行います。
     */
    private var litRedstoneLampData: BlockData? = null
    private var litCopperBulbData: BlockData? = null

    /** 点灯済みレッドストーンランプの共有表示素材を返します。 */
    private fun litRedstoneLampData(): BlockData =
        litRedstoneLampData ?: Bukkit.createBlockData(Material.REDSTONE_LAMP).also {
            if (it is org.bukkit.block.data.Lightable) it.isLit = true
            litRedstoneLampData = it
        }

    /** 点灯済み銅電球の共有表示素材を返します。 */
    private fun litCopperBulbData(): BlockData =
        litCopperBulbData ?: Bukkit.createBlockData(Material.COPPER_BULB).also {
            if (it is org.bukkit.block.data.Lightable) it.isLit = true
            litCopperBulbData = it
        }

    /**
     * ノード種別ごとのアイコン雛形です。毎回の生成を避けるため保持します。
     *
     * 雛形自体は変更せず、描画へ渡す際に複写します。種別は有限であり、
     * 折り畳み装飾の有無を合わせても数十件に収まります。
     * GUI更新はメインスレッドからのみ行います。
     */
    private val iconTemplateCache = mutableMapOf<Pair<CommandType, Boolean>, ItemStack>()

    /** 表示用のノードアイコンを返します。雛形の複写であり呼び出し側で変更できます。 */
    private fun cachedIconStack(node: CommandNode, folded: Boolean): ItemStack =
        iconTemplateCache.getOrPut(node.type to folded) { foldedIconStack(node, folded) }.clone()

    /** shadow検証の直近の差分署名です。同一差分の連投を抑えます。 */
    private var lastGraphShadowSignature: String = ""


    /**
     * グラフセル・経路・矢印・挿入候補を描画します。
     *
     * 座標基準だけを [GraphViewportFrame] で受け、旧来の矩形基準配置と同一式です。
     * ナビ等の外枠・ズーム後段の拡縮切断・画面組立は呼び出し側で行います。
     */
    private fun renderGraphCells(
        player: Player,
        script: DiskScript,
        renderGraph: CommandGraph,
        projection: ViewportProjection,
        cells: Map<MapPoint, MapCell>,
        frame: GraphViewportFrame,
        incompleteNodeIds: Set<java.util.UUID>,
        test: GestureTestState?,
        testActive: Boolean,
        insertionPreview: InsertionPreview?,
        zoomScale: Double,
    ): GraphCellOutput {
        val visuals = mutableListOf<GestureGuiVisual>()
        val elements = mutableListOf<GestureGuiElement>()
        cells.forEach { (localPoint, cell) ->
            // セル検索はグリッド座標、配置は列/行インデックスで行う（origin移動してもグリッド位置は固定）
            val colIndex = localPoint.x
            val rowIndex = localPoint.y
            val gx = state.origin.x + colIndex
            val gy = state.origin.y + rowIndex
            val cx = frame.x(colIndex)
            val cy = frame.y(rowIndex)
            when (cell.kind) {
                MapCellKind.NODE -> {
                    val node = cell.nodeId?.let { renderGraph.nodes[it] }
                    if (node != null && node.id != insertionPreview?.insertedNodeId) {
                        val isSelected = !testActive && state.selectedNodeId == node.id
                        val activeTest = test?.takeIf { testActive }
                        val isFailedNode = activeTest?.failedNodeId == node.id
                        val isWaitingNode = activeTest?.waitingNodeId == node.id
                        val isRunningNode = activeTest?.phase == GestureTestPhase.RUNNING &&
                            activeTest.currentNodeId == node.id
                        // STARTはFOR全体を表現するため、全体が完了するまで実行中色で示します。
                        val isLoopActiveStart = activeTest?.activeLoopStarts?.contains(node.id) == true
                        val isSuccessfulNode = activeTest?.successfulNodeIds?.contains(node.id) == true
                        // issue #6 の SKIPPED 分離です。成功の黄緑へ畳まず灰色で示します。
                        val isSkippedNode = activeTest?.skippedNodeIds?.contains(node.id) == true
                        val incomplete = node.id in incompleteNodeIds
                        // 折り畳みノードは構造全体の代理として縁取りで示します。
                        // 背景色は変えないため、未設定警告やテスト色と両立します。
                        // テスト実行中は展開表示のため、折り畳みノードは現れません。
                        val isFoldedFor = node.type == CommandType.FOLDED_FOR
                        val isFoldedIf = node.type == CommandType.FOLDED_IF
                        // テスト中はGlowではなく、ノード背景の張り替えで状態を示します。
                        // 背景はシュルカーボックスのtop系とし、同じ色分けで表現します。
                        // 失敗=赤、待機中=黄、実行中・ループ実行中=空色、成功済み=黄緑、スキップ=灰です。
                        val backgroundMaterial = when {
                            isFailedNode -> Material.RED_SHULKER_BOX
                            isWaitingNode -> Material.YELLOW_SHULKER_BOX
                            isRunningNode -> Material.LIGHT_BLUE_SHULKER_BOX
                            isLoopActiveStart -> Material.LIGHT_BLUE_SHULKER_BOX
                            isSuccessfulNode -> Material.LIME_SHULKER_BOX
                            isSkippedNode -> Material.GRAY_SHULKER_BOX
                            incomplete -> Material.ORANGE_SHULKER_BOX
                            else -> Material.LIGHT_GRAY_SHULKER_BOX
                        }
                        val glowColor = if (testActive) null else if (isSelected) Color.YELLOW.asARGB() else null
                        val statusLine = when {
                            incomplete -> Component.text(
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_NODE_INCOMPLETE),
                                NamedTextColor.RED,
                            )
                            else -> null
                        }
                        val hoverText = foldGuidanceText(player, node, renderGraph)
                            .let { guidance ->
                                // 折り畳み操作案内は灰色（§7）で示します。§形式へ直列化すると
                                // 先行色が後続行へ漏れるため、ノード名には白色を明示します。
                                val firstLine = if (guidance != null) {
                                    Component.text(guidance, NamedTextColor.GRAY).append(Component.newline())
                                        .append(Component.text(KcI18n.text(player, node.type.key), NamedTextColor.WHITE))
                                } else {
                                    Component.text(KcI18n.text(player, node.type.key), NamedTextColor.WHITE)
                                }
                                statusLine?.let { firstLine.append(Component.newline()).append(it) } ?: firstLine
                            }
                        // アイコン単体を浮かせず、追加ポイントと同じマス背景で視認性と接続先を示します。
                        visuals.add(GestureGuiVisual.Block(
                            visualId = "node-bg-${node.id}",
                            x = cx, y = cy,
                            width = frame.iconSize,
                            height = frame.iconSize,
                            blockData = blockDataOf(backgroundMaterial),
                            // 背景は常にアイコンの背面。選択時もlayerを変えず素材色/glowだけを変えます。
                            layer = GestureEditorLayout.ICON_BACKGROUND_LAYER,
                            glowColor = glowColor,
                        ))
                        // マスの90% (ICON_W=0.198) に合わせます。追加ポイントの背景・記号も同じ寸法です。
                        // 折りたたみ中の構造はアイコンにエンチャントグリントを付け、折畳み状態を
                        // 補助表現します（システム横断UI設計の状態表現規則）。背景・縁取りは変えません。
                        visuals.add(GestureGuiVisual.Item(
                            visualId = "node-icon-${node.id}",
                            x = cx, y = cy,
                            item = cachedIconStack(node, isFoldedFor || isFoldedIf),
                            scale = frame.iconScale,
                            // 選択表現は背面セルだけに付け、アイコン自体は常に同じ前景レイヤーへ置きます。
                            layer = GestureEditorLayout.ICON_LAYER,
                            glowColor = null,
                        ))
                        // 折り畳みノードは、背景の右の外縁へ縁取りを付けます。
                        // 太さはアイコン幅の30%、高さはアイコンと同じで、背景の外側に
                        // 配置します。隣接ノードは2セル周期のため縁取りは届きません。
                        // FORは空色、IFは黄色で種別を区別します。
                        val foldedFrameMaterial = when {
                            isFoldedFor -> Material.LIGHT_BLUE_CONCRETE
                            isFoldedIf -> Material.YELLOW_CONCRETE
                            else -> null
                        }
                        // 縁にもノードと同じクリック・ホバー判定を設け、縁経由でも
                        // 選択・再クリック変化が行えるようにします。単一ヒット確定の
                        // ため二重発火しません。
                        val frameThickness = frame.iconSize * 0.30
                        val frameOffset = frame.iconSize / 2.0 + frameThickness / 2.0
                        if (foldedFrameMaterial != null) {
                            visuals.add(GestureGuiVisual.Block(
                                visualId = "node-folded-frame-right-${node.id}",
                                x = cx + frameOffset, y = cy,
                                width = frameThickness,
                                height = frame.iconSize,
                                blockData = blockDataOf(foldedFrameMaterial),
                                layer = GestureEditorLayout.ICON_LAYER,
                            ))
                        }
                        // 挿入プレビュー中も既存ノードの入力要素を残します。仮ノードを
                        // 含む再レイアウト後の座標へ同じnodeIdを結び付けることで、
                        // 旧選択の解除と新ノードの選択を1クリックで完了させます。
                        // 入れ替えボタン表示中はホバー文言をボタンのさらに上へ退避させ、
                        // ボタン・ホバーをノード上側に積み上げます。通常ノードと
                        // 折り畳みノードだけが対象で、展開中の構造ノードは表示しません。
                        val showReorder = !testActive && insertionPreview == null && isSelected &&
                            !FoldedGraphs.isExpandedStructural(node)
                        // ボタン寸法はノードアイコン一辺の50%とし、配置計算は
                        // NodeReorderLayoutへ集約します（寸法導出の二重持ち防止）。
                        val reorderSize = NodeReorderLayout.buttonSize(frame.iconSize)
                        val reorderY = NodeReorderLayout.buttonY(cy, frame.iconSize, reorderSize)
                        val reorderGlyphSize = NodeReorderLayout.glyphSize(reorderSize, minOf(frame.pitchX, frame.pitchY))
                        val reorderGlyphDy = NodeReorderLayout.glyphDy(reorderGlyphSize)
                        if (!testActive) elements.add(GestureGuiElement(
                            elementId = "node:${node.id}",
                            // 折畳み縁にもノードと同じクリック・ホバー判定を設けます。
                            // 要素IDの重複は構造エラーになるため別要素にせず、
                            // ノード要素の領域を縁まで拡張します。
                            bounds = if (foldedFrameMaterial != null) {
                                GestureGuiBounds(
                                    cx - frame.iconSize / 2.0,
                                    cy - frame.iconSize / 2.0,
                                    cx + frame.iconSize / 2.0 + frameThickness,
                                    cy + frame.iconSize / 2.0,
                                )
                            } else {
                                iconBounds(cx, cy, frame.iconSize)
                            },
                            acceptedGestures = GestureGuiClickPolicy.CLICK,
                            targetVisualId = "node-icon-${node.id}",
                            hoverText = GestureGuiHoverText(
                                text = hoverText,
                                x = cx,
                                y = if (showReorder) NodeReorderLayout.hoverY(reorderY, reorderSize)
                                else cy + frame.iconSize * 0.9,
                                size = 0.006,
                                lineWidth = 180,
                            ),
                        ))
                        if (showReorder) {
                            // 左右の入れ替えボタンは隙間だけ空けて隣接させ、
                            // 対全体を中央揃え（左右対称）にします。
                            val reorderOffset = NodeReorderLayout.sideOffset(reorderSize)
                            listOf(
                                GraphEditor.ReorderDirection.LEFT to "←",
                                GraphEditor.ReorderDirection.RIGHT to "→",
                            ).forEach { (direction, glyph) ->
                                // 入替可否は描画時と同じ折りたたみ集合で判定し、表示と実可否を一致させます。
                                val enabled = GraphEditor.canSwapAdjacent(script.graph, node.id, direction)
                                val x = cx + if (direction == GraphEditor.ReorderDirection.LEFT) -reorderOffset else reorderOffset
                                val directionName = direction.name.lowercase()
                                visuals.add(GestureGuiVisual.Block(
                                    visualId = "node-reorder-$directionName-bg-${node.id}",
                                    x = x,
                                    y = reorderY,
                                    width = reorderSize,
                                    height = reorderSize,
                                    blockData = blockDataOf(
                                        if (enabled) Material.CYAN_CONCRETE else DisabledGuiVisualPolicy.material,
                                    ),
                                    layer = GestureEditorLayout.ICON_BACKGROUND_LAYER,
                                ))
                                visuals.add(GestureGuiVisual.Text(
                                    visualId = "node-reorder-$directionName-glyph-${node.id}",
                                    x = x,
                                    y = reorderY - reorderGlyphDy,
                                    text = Component.text(glyph).color(
                                        if (enabled) NamedTextColor.WHITE else NamedTextColor.GRAY,
                                    ),
                                    size = reorderGlyphSize,
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
                                        val guardGraph = plugin.scripts.load(state.scriptId)?.graph
                                        guardGraph?.let { currentGraph ->
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
                        width = frame.iconSize,
                        height = frame.iconSize,
                        blockData = blockDataOf(Material.YELLOW_CONCRETE),
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
                            bounds = iconBounds(cx, cy, frame.iconSize),
                            acceptedGestures = GestureGuiClickPolicy.CLICK,
                            targetVisualId = "add-plus-$gx-$gy",
                            hoverText = GestureGuiHoverText(
                                text = net.kyori.adventure.text.Component.text(
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ACTION_CLICK_ADD),
                                ),
                                x = cx,
                                y = cy + frame.iconSize * 0.9,
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
                        // 当たり判定に使うため、パン・ズーム後も戻り経路上の説明がずれません。
                        elements.add(GestureGuiElement(
                            // 経路要素の名前空間へ統一します。戻り経路だけ別の接頭辞にすると、
                            // 表示側のpath-判定とは一致しても、当たり判定側の縮尺・クリップ判定から
                            // 漏れてしまいます。通常経路と同じpath:配下に置くことで、表示と入力の
                            // 座標変換を同じ規則で適用します。
                            elementId = "path:return:$gx:$gy",
                            bounds = rect(cx, cy, frame.pitchX, frame.pitchY),
                            acceptedGestures = emptySet(),
                            hoverText = GestureGuiHoverText(
                                text = Component.text(
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_LOOP_RETURN_HOVER),
                                ),
                                x = cx,
                                y = cy + frame.pathThickness,
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
                                bounds = rect(cx, cy, frame.pitchX, frame.pitchY),
                                acceptedGestures = GestureGuiClickPolicy.CLICK,
                                hoverText = GestureGuiHoverText(
                                    text = net.kyori.adventure.text.Component.text(
                                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ACTION_CLICK_INSERT),
                                    ),
                                    x = cx,
                                    y = cy + frame.pathThickness,
                                    size = 0.0055,
                                    lineWidth = 120,
                                ),
                            ))
                        }
                    }
                }
            }
        }

        // テスト中の経路通過マーカーです。Glow描画には使わず、コンクリートへの
        // 張り替え判定にだけ使います。ループ戻りも通常通路と同じ仕様で扱います。
        // 待機中の次の枝は空色で示し、通過表示より優先します。
        val waitingPathMark = Color.fromRGB(85, 255, 255).asARGB()
        val passedPathMark = Color.fromRGB(85, 255, 85).asARGB()
        val testPathMark: ((MapPoint, MapCell) -> Int?)? = test?.takeIf { testActive }?.let { activeTest ->
            val waitingEdge = activeTest.waitingEdge
            val waitingLoopReturnId = activeTest.waitingLoopReturnId
            val mark: (MapPoint, MapCell) -> Int? = { _, cell ->
                when {
                    waitingEdge != null && cell.insertionTarget?.let { target ->
                        target.sourceId == waitingEdge.sourceId && target.edge == waitingEdge.edge
                    } == true -> waitingPathMark
                    waitingLoopReturnId != null && cell.loopReturnId == waitingLoopReturnId -> waitingPathMark
                    cell.insertionTarget?.let { target ->
                        target.sourceId?.let { sourceId ->
                            activeTest.passedEdges.contains(TestExecutionEdge(sourceId, target.edge))
                        }
                    } == true -> passedPathMark
                    else -> null
                }
            }
            mark
        }
        GesturePathRenderer.buildSegments(
            cells,
            boundaryConnections = projection.boundaryConnections,
            xCenter = frame::x,
            yCenter = frame::y,
            thickness = frame.pathThickness,
            glowColorFor = testPathMark,
            clipBounds = GesturePathRenderer.ClipBounds(
                // 仮想境界セルをグラフ領域の端までだけ延長します。これより外側へ
                // 経路を出さないため、ナビゲーション列との重なりが発生しません。
                minX = frame.minX / zoomScale,
                maxX = frame.maxX / zoomScale,
                minY = frame.minY / zoomScale,
                maxY = frame.maxY / zoomScale,
            ),
        ).forEach { seg ->
            // テスト中はGlowではなくテクスチャ変更で示します。通過経路=黄緑、
            // 待機中の次の枝=空色、それ以外は白色です。ループ戻りも通常通路と同じ仕様です。
            val pathMaterial = if (testActive && seg.glowColor != null) {
                if (seg.glowColor == waitingPathMark) Material.LIGHT_BLUE_CONCRETE else Material.LIME_CONCRETE
            } else {
                Material.WHITE_CONCRETE
            }
            visuals.add(GestureGuiVisual.Block(
                visualId = "path-${seg.x}-${seg.y}-${seg.w}-${seg.h}",
                x = seg.x, y = seg.y,
                width = seg.w, height = seg.h,
                blockData = blockDataOf(pathMaterial),
                layer = 1,
                glowColor = null,
            ))
        }

        // 横向き経路の方向矢印は、角・縦経路を除いた直線ランへ1つおきに配置します。
        // 順方向は»、ループ戻りは«で示します。経路自体は白いため、矢印だけを空色で示します。
        // 論理座標から投影するため、ズーム・パン後も矢印とホバー用の
        // 当たり判定が同じ経路セルを参照します。矢印自体は表示専用です。
        projection.horizontalArrowPoints.forEach { arrow ->
            val localPoint = arrow.point
            val gx = state.origin.x + localPoint.x
            val gy = state.origin.y + localPoint.y
            val glyph = if (arrow.direction == HorizontalArrowDirection.RETURN) "«" else "»"
            val kind = if (arrow.direction == HorizontalArrowDirection.RETURN) "return" else "forward"
            visuals.add(GestureGuiVisual.Text(
                visualId = "path-$kind-arrow-$gx-$gy",
                x = frame.x(localPoint.x),
                y = frame.y(localPoint.y) - 0.006,
                text = Component.text(glyph).color(NamedTextColor.AQUA),
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
            val cx = frame.x(local.x)
            val cy = frame.y(local.y)
            visuals.add(GestureGuiVisual.Block(
                visualId = "path-insertion-candidate-bg-${selectedGlobal.x}-${selectedGlobal.y}",
                x = cx,
                y = cy,
                width = frame.iconSize,
                height = frame.iconSize,
                blockData = blockDataOf(Material.YELLOW_CONCRETE),
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
        return GraphCellOutput(visuals, elements)
    }

    /**
     * 上部描画の共通前段です。旧来経路と宣言経路で共有します。
     *
     * 配置・投影・検証までを行い、表示・入力の組立は呼び出し側で行います。
     * 空表示と配置失敗の画面もここで確定し、呼び出し側の分岐を減らします。
     */
    private data class UpperGraphData(
        val script: DiskScript,
        val renderGraph: CommandGraph,
        val layout: GraphLayout,
        val projection: ViewportProjection,
        val cells: Map<MapPoint, MapCell>,
        val metrics: ViewportMetrics,
        val zoomScale: Double,
        val incompleteNodeIds: Set<java.util.UUID>,
        val test: GestureTestState?,
        val testActive: Boolean,
        val insertionPreview: InsertionPreview?,
    )

    private sealed interface UpperGraphInput {
        data class Ready(val data: UpperGraphData) : UpperGraphInput
        data object Empty : UpperGraphInput
        data class LayoutFailed(val view: GestureGuiView) : UpperGraphInput
    }

    private fun resolveUpperGraph(player: Player): UpperGraphInput {
        val test = state.testExecution
        val testActive = test?.phase == GestureTestPhase.RUNNING || test?.phase == GestureTestPhase.RESULT
        val script = test?.snapshot?.takeIf { testActive } ?: plugin.scripts.load(state.scriptId)
        if (script == null) return UpperGraphInput.Empty
        if (!testActive) observedRevision = script.revision
        // 値変更だけの保存では構造署名が変わらないため、前回の配置を使い回します。
        // 挿入previewやテスト表示は別グラフのため cache 対象外にします。
        // 無効化条件は構造署名の変化へ一元化し、設定上限の変更時だけ破棄します。
        val persistedLayout = runCatching {
            if (testActive) {
                GraphLayoutEngine.layout(script.graph)
            } else {
                val signature = GraphStructureFingerprint.fingerprint(script.graph)
                layoutCache.getOrCompute(signature) { GraphLayoutEngine.layout(script.graph) }
            }
        }
            .getOrElse { failure ->
                reportLayoutFailure(
                    player,
                    "ジェスチャーGUIの経路描画でレイアウトを生成できません: script=${script.id}",
                    failure,
                )
                return UpperGraphInput.LayoutFailed(layoutErrorView(player))
            }
        // 挿入プレビューは「経路クリックによる挿入」のときだけ適用します。
        // 追加ポイントからの追加は、追加ボタン自体が候補位置であり既存ノードが
        // 動かないため、仮ノード入りレイアウトや候補マーカーは二重表示になります。
        val insertionPreview = if (testActive) null else insertionPreview(script, player)
        val renderGraph = insertionPreview?.graph ?: script.graph
        // テスト中はADDとその延長経路を除いた真の配置で描画します。
        // testActiveが真のときtestは非nullであることが確定しています。
        val layout = (insertionPreview?.layout ?: persistedLayout).let { base ->
            if (testActive) base.withoutEditArtifacts(test.passedEdges) else base
        }
        val zoomScale = zoomScale()
        val metrics = viewportMetrics(zoomScale)
        // 折り畳み／展開で配置寸法が変わっても、軸ごとに最大寸法を覚えて原点を正規化します。
        // 縮小（折り畳み）では記憶を維持して原点を引き戻さず、拡大（展開・追加）では追従します。
        // FOR・分岐のいずれの折り畳みでも同じ扱いです。
        if (!testActive) {
            val remembered = GestureEditorLayout.rememberLayoutSize(
                state.lastExpandedLayoutWidth,
                state.lastExpandedLayoutHeight,
                persistedLayout.width,
                persistedLayout.height,
            )
            state.lastExpandedLayoutWidth = remembered.x
            state.lastExpandedLayoutHeight = remembered.y
        }
        // ズーム変更後に前回の原点が新しい表示可能範囲を越えないよう、
        // 描画・入力判定で共有する原点を毎回正規化します。
        state.origin = clampOriginWithRemembered(state.origin, metrics.columns, metrics.rows, layout)
        // アイコン・経路・経路の入力判定は必ず同じ投影を共有します。
        // 経路だけを画面外へ拡張すると、アイコンが消えた後も帯だけ残るため、
        // 画面外へ続く接続は projection の境界情報だけで表現します。
        val projection = layout.projection(state.origin, metrics.columns, metrics.rows)
        val cells = projection.cells
        // 実行前検証を一度だけ行い、ノード単位の未設定状態を背景色とホバーへ
        // 投影します。グラフ全体の検証を各セルで繰り返さないことで、ノード数が
        // 増えたときにも描画コストを線形に保ちます。
        // 検証結果は構造化エラー（nodeId/fieldKeys）として受けるため、表示文字列の
        // パス解析は行いません。snapshot・内部グラフ内のエラーは外側の対応ノードへ
        // 対応付けるため、存在しないノードIDは自然に除外されます。
        // テストsnapshotは正本の検証基準に混ぜず、永続世代のときだけ
        // 増分検証の基準にします。
        val validationErrors = validatedErrors(script, baseline = !testActive)
        val incompleteNodeIds = buildSet {
            validationErrors.forEach { error ->
                error.nodeId?.let { nodeId ->
                    // 内部グラフの未設定は外側の折り畳みノードへ対応付けて示します。
                    FoldedGraphs.outermostWrapperId(script.graph, nodeId)?.let(::add)
                }
            }
        }
        // 上部抑止の判定要点を記録します。テスト表示・挿入preview中は
        // 毎tick変化し得るため記録せず、抑止判定から外します。
        if (!testActive && insertionPreview == null) {
            lastUpperStructureSignature = GraphStructureFingerprint.fingerprint(script.graph)
            lastUpperIncomplete = incompleteNodeIds
            lastUpperSelection = state.selectedNodeId
        }
        return UpperGraphInput.Ready(
            UpperGraphData(
                script = script,
                renderGraph = renderGraph,
                layout = layout,
                projection = projection,
                cells = cells,
                metrics = metrics,
                zoomScale = zoomScale,
                incompleteNodeIds = incompleteNodeIds,
                test = test,
                testActive = testActive,
                insertionPreview = insertionPreview,
            ),
        )
    }


    /**
     * 上部画面の宣言実装です。グラフ本体は Custom 描画口へ接続し、外枠は
     * 旧来座標のまま転記します。独自の切断処理は compiler へ任せます。
     */
    private fun buildUpperViewportContentNew(player: Player): GestureGuiView {
        layoutFailureDuringCurrentRender = false
        val data = when (val input = resolveUpperGraph(player)) {
            UpperGraphInput.Empty -> return emptyView()
            is UpperGraphInput.LayoutFailed -> return input.view
            is UpperGraphInput.Ready -> input.data
        }
        val script = data.script
        val layout = data.layout
        val metrics = data.metrics
        val zoomScale = data.zoomScale
        val test = data.test
        val testActive = data.testActive
        val chrome = mutableListOf<SettingsActionCard>()
        // issue #13: 同一 script の共同編集者を表示専用カードで示します。
        // 自分以外の編集者がいる場合だけ出し、内容・revision へは触れません。
        presenceCard(player)?.let { chrome += it }
        if (!testActive) {
            listOf(
                Quad("nav-up", 0, 1, "▲"),
                Quad("nav-down", 0, -1, "▼"),
                Quad("nav-left", -1, 0, "◀"),
                Quad("nav-right", 1, 0, "▶"),
            ).forEach { quad ->
                val nx = GestureEditorLayout.NAV_CENTER_X + quad.second * GestureEditorLayout.NAV_PITCH
                val ny = GestureEditorLayout.NAV_CENTER_Y + quad.third * GestureEditorLayout.NAV_PITCH
                val delta = MapPoint(quad.second, -quad.third)
                val enabled = canNavigate(layout, metrics.columns, metrics.rows, delta)
                val hoverKey = when (quad.first) {
                    "nav-up" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_UP
                    "nav-down" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_DOWN
                    "nav-left" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_LEFT
                    "nav-right" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_RIGHT
                    else -> error("unknown gesture navigation button: ${quad.first}")
                }
                chrome += SettingsActionCard(
                    elementId = quad.first,
                    label = Component.text(quad.fourth).color(
                        if (enabled) NamedTextColor.WHITE else NamedTextColor.GRAY,
                    ),
                    background = blockDataOf(
                        if (enabled) Material.CYAN_CONCRETE else DisabledGuiVisualPolicy.material,
                    ),
                    cx = nx,
                    cy = ny,
                    w = GestureEditorLayout.NAV_SIZE,
                    h = GestureEditorLayout.NAV_SIZE,
                    labelSize = 0.011,
                    labelWidth = 90,
                    labelDy = -0.01,
                    gestures = GestureGuiClickPolicy.CLICK,
                    guard = { _, _ -> canMoveCurrentViewport(delta) },
                    hover = if (enabled) navigationHover(player, hoverKey) else null,
                    visualTag = quad.first,
                )
            }
            // back-to-start（十字の下・左に隣接）
            chrome += SettingsActionCard(
                elementId = "back-to-start",
                label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CENTER_GLYPH)),
                background = blockDataOf(Material.BROWN_CONCRETE),
                cx = GestureEditorLayout.BACK_X,
                cy = GestureEditorLayout.BACK_Y,
                w = GestureEditorLayout.NAV_SIZE,
                h = GestureEditorLayout.NAV_SIZE,
                labelSize = 0.0055,
                labelWidth = 90,
                labelDy = -0.02,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = navigationHover(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CENTER_ACTION),
                visualTag = "back",
            )
            listOf(
                "nav-zoom-in" to "＋",
                "nav-zoom-out" to "－",
            ).forEachIndexed { index, (id, glyph) ->
                val enabled = if (id == "nav-zoom-in") {
                    state.zoomLevel < GestureEditorLayout.MAX_ZOOM_LEVEL
                } else {
                    state.zoomLevel > GestureEditorLayout.MIN_ZOOM_LEVEL
                }
                chrome += SettingsActionCard(
                    elementId = id,
                    label = Component.text(glyph).color(
                        if (enabled) NamedTextColor.WHITE else NamedTextColor.GRAY,
                    ),
                    background = blockDataOf(
                        if (enabled) Material.CYAN_CONCRETE else DisabledGuiVisualPolicy.material,
                    ),
                    cx = GestureEditorLayout.ZOOM_X,
                    cy = GestureEditorLayout.ZOOM_IN_Y + index * GestureEditorLayout.ZOOM_PITCH,
                    w = GestureEditorLayout.ZOOM_SIZE,
                    h = GestureEditorLayout.ZOOM_SIZE,
                    labelSize = 0.010,
                    labelWidth = 90,
                    labelDy = -0.01,
                    gestures = GestureGuiClickPolicy.CLICK,
                    guard = { _, _ ->
                        if (id == "nav-zoom-in") {
                            state.zoomLevel < GestureEditorLayout.MAX_ZOOM_LEVEL
                        } else {
                            state.zoomLevel > GestureEditorLayout.MIN_ZOOM_LEVEL
                        }
                    },
                    hover = if (enabled) {
                        navigationHover(
                            player,
                            if (id == "nav-zoom-in") {
                                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ZOOM_IN
                            } else {
                                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ZOOM_OUT
                            },
                        )
                    } else null,
                    visualTag = id,
                )
            }
        }
        // クリップ（位置固定）と閉じるボタンはテスト中も通常通り表示します。
        run {
            val following = state.anchor == null
            chrome += SettingsActionCard(
                elementId = "nav-clip",
                label = Component.text("@"),
                background = blockDataOf(
                    if (following) Material.CYAN_CONCRETE else Material.LIGHT_BLUE_CONCRETE,
                ),
                cx = GestureEditorLayout.CLIP_X,
                cy = GestureEditorLayout.CLIP_Y,
                w = GestureEditorLayout.CLIP_SIZE,
                h = GestureEditorLayout.CLIP_SIZE,
                labelSize = 0.010,
                labelWidth = 90,
                labelDy = -0.01,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = { actor, _ -> actor.uniqueId == sessionOwnerId },
                hover = if (player.uniqueId == sessionOwnerId) {
                    navigationHover(
                        player,
                        if (following) {
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CLIP
                        } else {
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CLIP_FIXED
                        },
                    )
                } else null,
                visualTag = "nav-clip",
            )
        }
        chrome += SettingsActionCard(
            elementId = "nav-close",
            label = Component.text("×"),
            background = blockDataOf(Material.RED_CONCRETE),
            cx = GestureEditorLayout.CLOSE_X,
            cy = GestureEditorLayout.CLOSE_Y,
            w = GestureEditorLayout.CLOSE_SIZE,
            h = GestureEditorLayout.CLOSE_SIZE,
            labelSize = 0.010,
            labelWidth = 90,
            labelDy = -0.01,
            gestures = GestureGuiClickPolicy.CLICK,
            guard = null,
            hover = navigationHover(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_CLOSE),
            visualTag = "nav-close",
        )
        when {
            test?.phase == GestureTestPhase.RUNNING -> {
                val lamp = litRedstoneLampData()
                chrome += SettingsActionCard(
                    elementId = "test-execution",
                    label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TEST_EXECUTION_GLYPH)),
                    background = lamp,
                    cx = GestureEditorLayout.ZOOM_X,
                    cy = GestureEditorLayout.TEST_RUNNING_Y,
                    w = GestureEditorLayout.ZOOM_SIZE,
                    h = GestureEditorLayout.ZOOM_SIZE,
                    labelSize = 0.010,
                    labelWidth = 90,
                    labelDy = -0.01,
                    gestures = GestureGuiClickPolicy.CLICK,
                    guard = null,
                    hover = navigationHoverText(
                        Component.text(
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_RUNNING_HINT),
                        ),
                    ),
                    visualTag = "test-execution",
                )
            }
            state.testExecution == null && !testActive -> {
                val reason = testUnavailableReason(player)
                if (reason == null) {
                    val lamp = blockDataOf(Material.REDSTONE_LAMP)
                    chrome += SettingsActionCard(
                        elementId = "test-execution",
                        label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TEST_EXECUTION_GLYPH)),
                        background = lamp,
                        cx = GestureEditorLayout.ZOOM_X,
                        cy = GestureEditorLayout.TEST_BUTTON_Y,
                        w = GestureEditorLayout.ZOOM_SIZE,
                        h = GestureEditorLayout.ZOOM_SIZE,
                        labelSize = 0.010,
                        labelWidth = 90,
                        labelDy = -0.01,
                        gestures = GestureGuiClickPolicy.CLICK,
                        guard = null,
                        hover = navigationHoverText(
                            Component.text(
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TEST_EXECUTION),
                            ),
                        ),
                        visualTag = "test-execution",
                    )
                } else {
                    val bulb = litCopperBulbData()
                    chrome += SettingsActionCard(
                        elementId = "test-execution",
                        // 無言操作面のため文言表示は作りません（旧実装どおり）。
                        label = null,
                        background = bulb,
                        cx = GestureEditorLayout.ZOOM_X,
                        cy = GestureEditorLayout.TEST_BUTTON_Y,
                        w = GestureEditorLayout.ZOOM_SIZE,
                        h = GestureEditorLayout.ZOOM_SIZE,
                        labelSize = 0.010,
                        labelWidth = 90,
                        labelDy = -0.01,
                        gestures = GestureGuiClickPolicy.CLICK,
                        guard = { _, _ -> false },
                        hover = navigationHoverText(Component.text(reason)),
                        visualTag = "test-execution",
                    )
                }
            }
        }
        // グラフ本体の描画は共有関数へ委譲し、座標基準は旧来と同一にします。
        val frame = GraphViewportMapper.legacyFrame(zoomScale)
        val graphOutput = renderGraphCells(
            player,
            script,
            data.renderGraph,
            data.projection,
            data.cells,
            frame,
            data.incompleteNodeIds,
            test,
            testActive,
            data.insertionPreview,
            zoomScale,
        )
        val panel = GestureGuiPanel(
            width = GestureEditorLayout.UPPER_W,
            height = GestureEditorLayout.UPPER_H,
            backgroundMaterial = Material.GRAY_CONCRETE,
            frameMaterial = Material.LIGHT_GRAY_CONCRETE,
            frameWidth = GestureEditorLayout.FRAME_WIDTH,
        )
        val compiled = KantanLayoutBridge.compile(
            DeclarativeUpper.document(
                viewport = UpperViewportRect(
                    minX = GestureEditorLayout.UPPER_GRAPH_MIN_X,
                    minY = GestureEditorLayout.UPPER_GRAPH_MIN_Y,
                    maxX = GestureEditorLayout.UPPER_GRAPH_MAX_X,
                    maxY = GestureEditorLayout.UPPER_GRAPH_MAX_Y,
                ),
                emptyRect = UpperViewportRect(
                    minX = -GestureEditorLayout.UPPER_W / 2.0 + GestureEditorLayout.FRAME_WIDTH,
                    minY = -GestureEditorLayout.UPPER_H / 2.0 + GestureEditorLayout.FRAME_WIDTH,
                    maxX = GestureEditorLayout.UPPER_W / 2.0 - GestureEditorLayout.FRAME_WIDTH,
                    maxY = GestureEditorLayout.UPPER_H / 2.0 - GestureEditorLayout.FRAME_WIDTH,
                ),
                chrome = chrome,
                includeViewportEmpty = !testActive,
                panel = panel,
            ),
            onAction = { context ->
                handleUpperAction(context.copy(elementId = DeclarativeUpper.stripGraphPrefix(context.elementId)))
            },
            onDiagnostics = { diagnostics ->
                plugin.logger.fine("[KantanCommander][DeclarativeUpper] $diagnostics")
            },
            extraActionIds = graphOutput.elements.map { it.elementId }.toSet(),
            customRenderers = mapOf(
                DeclarativeUpper.GRAPH_RENDERER_ID to KantanGraphCustomRenderer(graphOutput, zoomScale, frame),
            ),
        )
        ensureUniqueScreenElementIds(compiled.elements, script.id)
        if (!layoutFailureDuringCurrentRender) layoutFailureNoticeSent = false
        return GestureGuiView(
            GestureGuiScreenDefinition(
                UPPER_SCREEN_ID,
                compiled.elements,
                access = screenAccess,
                accessPolicy = screenAccessPolicy,
                visibilityPolicy = screenVisibilityPolicy,
            ),
            compiled.visuals,
            panel = panel,
        ) { context ->
            handleUpperAction(context.copy(elementId = DeclarativeUpper.stripGraphPrefix(context.elementId)))
        }
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
        state.detailVisitedKeys.clear()
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
        state.pendingFireworkContext = null
        state.pendingFireworkKey = null
        state.pendingFireworkData = null
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
        // 単一項目経由の画面は対象画面へ直行し、経由画面を経路へ積みません。
        // 判定に使う操作種別・対象種別はタブを開く時点で確定します。
        val script = plugin.scripts.load(frame.context.scriptId)
        val node = frame.context.nodeId.let { script?.graph?.nodes?.get(it) }
        val effective = node?.let { shortCircuitTargetFrame(it, frame) } ?: frame
        state.settingRoute = listOf(effective)
        state.detailVisitedKeys.remove(PresetSelectionDisplay.detailVisitKey(frame.fieldKey, frame.context.role))
        activateSettingFrame(effective, emptyList())
    }

    /**
     * 詳細子画面への入室を記録します。プリセットの選択表示を詳細側へ移すための
     * 表示専用記録であり、保存値・検証・実行へ影響しません。
     */
    private fun markDetailVisited(fieldKey: String, role: CommandSettingRole?) {
        state.detailVisitedKeys += PresetSelectionDisplay.detailVisitKey(fieldKey, role)
    }

    /**
     * プリセット再適用時に入室記録を破棄し、プリセット側の選択表示に戻します。
     */
    private fun clearDetailVisited(fieldKey: String, role: CommandSettingRole?) {
        state.detailVisitedKeys.remove(PresetSelectionDisplay.detailVisitKey(fieldKey, role))
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
        if (frame.screen == GestureSettingScreen.TARGET_FILTERS) {
            markDetailVisited(frame.fieldKey, frame.context.role)
        }
        activateSettingFrame(frame, nextPath)
        state.lowerMode = GestureLowerMode.SETTING_CHOICES
        ensureSettingChild(player, parentView)
    }

    /** 直下ノードの選択を経路へ記録し、同じ項目の再クリックを判定可能にします。 */
    private fun rememberSettingNode(
        nodeId: String,
        interactionMode: GestureSettingInteractionMode? = null,
    ) {
        // 詳細だけを開く項目は選択状態へ投影しません。
        if (interactionMode == GestureSettingInteractionMode.DETAILS_ONLY) return
        val path = state.settingTreePath ?: return
        state.settingTreePath = path.selectAtDepth(state.settingRoute.size - 1, nodeId)
    }

    /** 設定木を一段戻し、対応する物理子画面も一段だけ閉じます。 */
    private fun popSettingFrame(player: Player): Boolean {
        if (state.settingRoute.size <= 1) {
            closeSettingChild(player)
            return false
        }
        val poppedChildScreenId = currentSettingChildScreenId()
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
            api.closeChild(ownerIdFor(player), poppedChildScreenId)
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
                    // 旧ENTITY_ACTION-equipの実物選択はPLACE_ITEMへ移行済みのため外します。
                    node.type == CommandType.PLACE_ITEM ||
                    (node.type == CommandType.CONDITION && node.conditionSubject() == ConditionSubject.PLAYER) ||
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
            // TEXT項目は専用画面を持たないためルートは開始しないが、候補一覧など
            // 設定文脈を要する操作のため対象情報は残す。残った子画面の経路は親へ戻す。
            clearSettingState()
            state.settingContext = CommandSettingContext(state.scriptId, node.id, descriptor.role)
            state.settingFieldKey = field.key
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
        if (applyHeldField(player, context, node, fieldKey)) return
        // issue #8: 呼び出し設定は専用子画面で編集します。対話入力へは進めません。
        if (fieldKey == "diskCallSettings" && node.type == CommandType.DISK_CALL) {
            openDiskCallSettings(player)
            return
        }
        if (fieldKey == "staySeconds" && node.type == CommandType.DISPLAY_TEXT) {
            // 表示時間は1つのタブにまとめ、現在値欄では3項目を表示します。編集時は
            // インベントリGUIと同じ一組の入力欄・最大長・検証を使います。
            showDisplayTimingSettingDialog(player, context, node)
            return
        }
        if (fieldKey == "soundParameters" && node.type == CommandType.PLAY_SOUND) {
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
        if (fieldKey == "soundSetting" && node.type == CommandType.PLAY_SOUND) {
            showSoundSettingDialog(player, context, node)
            return
        }
        if (fieldKey == "particle" && node.type == CommandType.PARTICLE) {
            showParticleDialog(player, context, node)
            return
        }
        if (fieldKey == "particleDensity" && node.type == CommandType.PARTICLE) {
            beginSettingInput(
                player,
                requireNotNull(CommandDialogSpecs.field(node, "particleDensity")),
                node.string("particleDensity", "0.5"),
            ) { raw ->
                val value = CommandDialogSpecs.normalize("particleDensity", raw)
                val spec = requireNotNull(CommandDialogSpecs.field(node, "particleDensity"))
                val validationError = spec.validateInput(value)
                if (validationError != null) {
                    KcI18n.text(player, validationError)
                } else if (!updateSettingNode(player, context, configuredFields = setOf("particleDensity")) {
                        CommandSettingsModel.setParameter(it, "particleDensity", value)
                    }) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else null
            }
            return
        }
        if (fieldKey == "burstHeight" && node.type == CommandType.FIREWORK_LAUNCH) {
            beginSettingInput(
                player,
                requireNotNull(CommandDialogSpecs.field(node, "burstHeight")),
                node.string("burstHeight", "20.0"),
            ) { raw ->
                val value = CommandDialogSpecs.normalize("burstHeight", raw)
                val spec = requireNotNull(CommandDialogSpecs.field(node, "burstHeight"))
                val validationError = spec.validateInput(value)
                if (validationError != null) {
                    KcI18n.text(player, validationError)
                } else if (!updateSettingNode(player, context, configuredFields = setOf("burstHeight")) {
                        CommandSettingsModel.setParameter(it, "burstHeight", value)
                    }) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else null
            }
            return
        }
        if (fieldKey == "slot" && node.type == CommandType.PLACE_ITEM) {
            // 数値スロットは GUI 表示 (1始まり) で受け付け、保存形式 (0始まり) へ
            // 変換します。装備は選択画面、額縁は固定のためここへは到達しません。
            // 仕様と検証は CommandDialogSpecs.placeSlot へ一元化します。
            val destination = PlaceItemDestination.from(node.string("placeDestination")) ?: return
            val spec = CommandDialogSpecs.placeSlot(node) ?: return
            beginSettingInput(
                player,
                spec,
                PlaceItemDestination.toDisplay(destination, node.string("slot")),
            ) { raw ->
                val value = CommandDialogSpecs.normalize("slot", raw)
                val validationError = spec.validateInput(value)
                if (validationError != null) {
                    KcI18n.text(player, validationError)
                } else {
                    val saved = PlaceItemDestination.fromDisplay(destination, value)
                    if (saved == null) {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT)
                    } else if (!updateSettingNode(player, context, configuredFields = setOf("slot")) {
                            CommandSettingsModel.setParameter(it, "slot", saved)
                        }) {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                    } else null
                }
            }
            return
        }
        if (fieldKey == "fireworkContents" && node.type == CommandType.FIREWORK_LAUNCH) {
            applyHeldFirework(player, context)
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
                    change = {
                        // issue #10: 召喚種類の変更時は非対応の個別設定を残しません。
                        if (fieldKey == "summonEntity" && it.type == me.awabi2048.kantancommander.model.CommandType.SUMMON_ENTITY) {
                            CommandSettingsModel.setSummonEntity(it, value)
                        } else {
                            CommandSettingsModel.setParameter(it, fieldKey, value)
                        }
                    },
                )
                if (updated == null) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else {
                    updateUpperIfNeeded(player)
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
     * 1回目の選択操作は設定元の切替だけを行い、値の入力へ引き継ぎません。
     * 選択済みの直接値をもう一度押す2回目の操作で、項目別の子設定
     * （手持ち適用・設定対話）へ進みます。選択だけでDialogや手持ち保存へ
     * 進むと、設定元の確認と値入力が分離できず、誤った設定元で値を確定する
     * 操作を防げないためです。
     */

    /**
     * 手持ち対応項目へ手持ち内容を適用します。編集枠と同等の導線です。
     *
     * 編集枠と直接値2回目の両方から共用します。引き受けた場合（上書き確認を
     * 開いた場合を含む）はtrueを返します。
     */
    private fun applyHeldField(
        player: Player,
        context: CommandSettingContext,
        node: CommandNode,
        fieldKey: String,
    ): Boolean {
        if (fieldKey == "item" && (
                node.type == CommandType.GIVE_ITEM ||
                    // 旧ENTITY_ACTION-equipの実物適用はPLACE_ITEMへ移行済みのため外します。
                    node.type == CommandType.PLACE_ITEM ||
                    (node.type == CommandType.CONDITION && node.conditionSubject() == ConditionSubject.PLAYER) ||
                    node.type == CommandType.TEMP_SET
                )) {
            applyHeldItem(player, context)
            return true
        }
        if (fieldKey == "block" && (
                node.type == CommandType.BLOCK_OPERATION ||
                    (node.type == CommandType.CONDITION && node.conditionSubject() == ConditionSubject.BLOCK) ||
                    (node.type == CommandType.TEMP_SET &&
                        ValueType.parse(node.string("tempType")) == ValueType.BLOCK)
                )) {
            applyHeldBlock(player, context)
            return true
        }
        if (fieldKey == "diskId" && node.type == CommandType.DISK_CALL) {
            applyHeldDisk(player, context)
            return true
        }
        return false
    }

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
                updateLower(player)
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
        // 直接値の1回目は方式指定のみ行います（慣習どおり）。
        if (currentSource != CommandValueSource.LITERAL) {
            updateLower(player)
            return
        }
        // 直接値の2回目は子設定へ進みます。束縛付き項目の記述子は対話系か
        // 手持ち系に限られ、構造化画面への遷移は起きません。
        // 空手等の案内表示は各導線の既存動作に従います。
        beginSelectedFieldEdit(player, fieldKey)
        return
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
            // issue #8: 呼び出し先の内容スナップショットと引数宣言を転記します。
            node.applyCalledDisk(diskId, disk)
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

    /**
     * 設定中の花火をその場で打ち上げます。
     *
     * 本実行と同じ検証済み方式（初速固定・ワンショット起爆判定）を使い、
     * 保存は行わず使い捨てで発射します。召喚体台帳へは登録しません。
     */
    private fun launchFireworkPreview(player: Player, node: CommandNode): Boolean {
        val data = node.string(FireworkSettings.PARAM_DATA)
        val stack = data.takeIf(String::isNotBlank)?.let { ItemStackCodec.decode(it) }
            ?: node.string(FireworkSettings.PARAM_ITEM).takeIf(String::isNotBlank)?.let { raw ->
                Material.matchMaterial(raw)?.let { ItemStack(it) }
            } ?: return false
        if (stack.type != Material.FIREWORK_ROCKET) return false
        val height = node.string(FireworkSettings.PARAM_HEIGHT, FireworkSettings.HEIGHT_DEFAULT.toString())
            .toDoubleOrNull()?.takeIf(Double::isFinite) ?: FireworkSettings.HEIGHT_DEFAULT
        // 試射はGesture GUIの存在する座標（クリップ固定のanchor）から打ち上げ、
        // 発射者を自機に固定して接触起爆を避けます。anchorがない場合は
        // 制御ブロックの1ブロック上、配置もなければ操作者位置へ落とします。
        val placement = state.placement
        val launchAt = FireworkSettings.previewSite(
            state.anchor,
            placement?.let(FireworkSettings::placementAbove),
            player.location,
        )
        if (height <= 0.0) {
            val instant = player.world.spawn(launchAt, org.bukkit.entity.Firework::class.java)
            instant.setItem(stack)
            instant.setShotAtAngle(false)
            instant.detonate()
            return true
        }
        val rocket = player.world.spawn(launchAt, org.bukkit.entity.Firework::class.java)
        rocket.setItem(stack)
        rocket.setShotAtAngle(false)
        rocket.shooter = player
        rocket.velocity = org.bukkit.util.Vector(0.0, FireworkSettings.INITIAL_UPWARD_VELOCITY, 0.0)
        rocket.ticksToDetonate = FireworkSettings.backstopLifetime(height)
        val target = launchAt.clone().add(0.0, height, 0.0)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            detonatePreviewAtTarget(rocket, target)
        }, FireworkSettings.detonationTicks(height).toLong().coerceAtLeast(1L))
        return true
    }

    /** 試射のワンショット起爆判定です。本実行と同一条件で目標高度へ補正起爆します。 */
    private fun detonatePreviewAtTarget(rocket: org.bukkit.entity.Firework, target: Location, retriesLeft: Int = 5) {
        val world = target.world
        if (!rocket.isValid || rocket.isDetonated || world == null ||
            !world.isChunkLoaded(target.blockX shr 4, target.blockZ shr 4)
        ) {
            if (retriesLeft > 0) {
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    detonatePreviewAtTarget(rocket, target, retriesLeft - 1)
                }, 20L)
            } else {
                runCatching { rocket.remove() }
            }
            return
        }
        rocket.teleport(target)
        rocket.detonate()
    }

    /** メインハンドの花火ロケットを設定値へ保存します。 */
    private fun applyHeldFirework(player: Player, context: CommandSettingContext): Boolean {
        val held = player.inventory.itemInMainHand
        if (held.type != Material.FIREWORK_ROCKET) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_HELD_FIREWORK_REQUIRED))
            updateLower(player)
            return false
        }
        val itemKey = held.type.key.toString()
        val itemData = ItemStackCodec.encode(held)
        val node = plugin.scripts.load(context.scriptId)?.graph?.nodes?.get(context.nodeId) ?: return false
        if (node.string(FireworkSettings.PARAM_ITEM).isNotBlank() ||
            node.string(FireworkSettings.PARAM_DATA).isNotBlank()
        ) {
            openFireworkOverwriteConfirm(player, context, itemKey, itemData)
            return false
        }
        return saveHeldFirework(player, context, itemKey, itemData)
    }

    private fun saveHeldFirework(
        player: Player,
        context: CommandSettingContext,
        itemKey: String,
        itemData: String,
    ): Boolean {
        val updated = updateSettingNode(player, context, configuredFields = setOf("fireworkContents")) { node ->
            CommandSettingsModel.setParameter(node, FireworkSettings.PARAM_ITEM, itemKey)
            CommandSettingsModel.setParameter(node, FireworkSettings.PARAM_DATA, itemData)
        }
        if (updated) {
            player.sendMessage(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_ITEM_SET, mapOf("item" to itemKey)),
            )
        }
        return updated
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
        val parentId = if (settingChildWasOpen) currentSettingChildScreenId() else lowerPanel.LOWER_SCREEN_ID
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
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            false
        }
        if (!opened) {
            state.confirmKind = GestureConfirmKind.DELETE
            clearPendingOverwriteState()
            state.lowerMode = if (settingChildWasOpen) GestureLowerMode.SETTING_CHOICES else GestureLowerMode.SETTINGS
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
        val parentId = if (settingChildWasOpen) currentSettingChildScreenId() else lowerPanel.LOWER_SCREEN_ID
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
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            false
        }
        if (!opened) {
            state.confirmKind = GestureConfirmKind.DELETE
            clearPendingOverwriteState()
            state.lowerMode = if (settingChildWasOpen) GestureLowerMode.SETTING_CHOICES else GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    private fun openFireworkOverwriteConfirm(
        player: Player,
        context: CommandSettingContext,
        itemKey: String,
        itemData: String,
    ) {
        val ownerId = ownerIdFor(player)
        if (api.snapshot(ownerId)?.childScreenIds?.contains(lowerPanel.CONFIRM_SCREEN_ID) == true) return
        val settingChildWasOpen = settingChildOpen(ownerId)
        val parentId = if (settingChildWasOpen) currentSettingChildScreenId() else lowerPanel.LOWER_SCREEN_ID
        val owner = ownerPlayerFor(player)
        val attention = attentionState()
        val parentView = if (settingChildWasOpen) {
            lowerPanel.buildSettingChild(state, owner, attention, suppressHighlight = true)
        } else {
            lowerPanel.build(state, owner, attention, suppressHighlight = true)
        }
        // 文言はアイテム上書き確認と共有し、花火もアイテムの一種として扱います。
        state.confirmKind = GestureConfirmKind.FIREWORK_OVERWRITE
        state.confirmNodeId = null
        clearPendingOverwriteState()
        state.pendingFireworkContext = context
        state.pendingFireworkKey = itemKey
        state.pendingFireworkData = itemData
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
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            false
        }
        if (!opened) {
            state.confirmKind = GestureConfirmKind.DELETE
            clearPendingOverwriteState()
            state.lowerMode = if (settingChildWasOpen) GestureLowerMode.SETTING_CHOICES else GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    private fun confirmFireworkOverwrite(player: Player) {
        val context = state.pendingFireworkContext ?: return
        val itemKey = state.pendingFireworkKey ?: return
        val itemData = state.pendingFireworkData ?: return
        val saved = runCatching {
            CommandSettingsModel.updateNode(
                plugin,
                context,
                configuredFields = setOf("fireworkContents"),
                editorId = player.uniqueId,
                expectedRevision = expectedMutationRevision(player),
                change = { node ->
                    CommandSettingsModel.setParameter(node, FireworkSettings.PARAM_ITEM, itemKey)
                    CommandSettingsModel.setParameter(node, FireworkSettings.PARAM_DATA, itemData)
                },
            ) != null
        }.onFailure { failure ->
            reportGraphOperationFailure(
                player,
                "花火上書きの保存に失敗しました: script=${context.scriptId} node=${context.nodeId}",
                failure,
            )
        }.getOrDefault(false)
        if (!saved) {
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
        updateUpperIfNeeded(player)
        updateLower(player)
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
        updateUpperIfNeeded(player)
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
        updateUpperIfNeeded(player)
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
        // 約50箇所のfield編集が通る共通 funnel です。値変更だけでは
        // 上部の表示内容が変わらないため、要点一致時は下部だけ更新します。
        updateUpperIfNeeded(player)
        updateLower(player)
        return true
    }

    /**
     * issue #18 のSlider段階操作です。
     *
     * [-][+]の離散操作で1刻みずつ仮値を進めます。保存・revision進行は行わず、
     * [updateFieldPreview] へ送って表示と間引き共有だけを行います。
     * 確定は［確定］、破棄は［取消］が担います。
     */
    private fun handleSliderStep(context: GestureGuiActionContext, player: Player) {
        val increment = when {
            context.elementId.startsWith("lower-slider-inc:") -> 1
            context.elementId.startsWith("lower-slider-dec:") -> -1
            else -> return
        }
        val fieldKey = context.elementId.substringAfter(":")
        if (fieldKey.isBlank()) return
        val settingContext = state.settingContext ?: return
        val script = plugin.scripts.load(settingContext.scriptId) ?: return
        observedRevision = script.revision
        val node = script.graph.nodes[settingContext.nodeId] ?: return
        if (settingContext.nodeId != state.selectedNodeId) return
        val spec = SettingSpec.sliderSpecFor(node, fieldKey) ?: return
        // テンプレート参照値はSliderで編集しません。Dialog側へ委譲します。
        if (SliderValuePolicy.isTemplateReference(node.params[fieldKey])) return
        val base = previewValue(node.id, fieldKey) ?: node.params[fieldKey]
        val next = SliderValuePolicy.stepBy(spec, base, increment)
        updateFieldPreview(player, node.id, fieldKey, next)
    }

    /**
     * issue #18 のSliderバー上クリックです。
     *
     * クリックされた区画の中央値を仮値とします。保存・revision進行は行いません。
     */
    private fun handleSliderBar(context: GestureGuiActionContext, player: Player) {
        // lower-slider-bar:<fieldKey>:<index> の形式です。fieldKey自体に
        // コロンは含まれないため、末尾区画をindexとして分離します。
        val rest = context.elementId.removePrefix("lower-slider-bar:")
        val separator = rest.lastIndexOf(':')
        if (separator < 0) return
        val fieldKey = rest.substring(0, separator)
        val index = rest.substring(separator + 1).toIntOrNull() ?: return
        if (fieldKey.isBlank()) return
        val settingContext = state.settingContext ?: return
        val script = plugin.scripts.load(settingContext.scriptId) ?: return
        observedRevision = script.revision
        val node = script.graph.nodes[settingContext.nodeId] ?: return
        if (settingContext.nodeId != state.selectedNodeId) return
        val spec = SettingSpec.sliderSpecFor(node, fieldKey) ?: return
        if (SliderValuePolicy.isTemplateReference(node.params[fieldKey])) return
        updateFieldPreview(player, node.id, fieldKey, SliderValuePolicy.valueForSegment(spec, index))
    }

    /**
     * issue #18 のSlider確定です。
     *
     * 仮値を共通検証へ通し、[updateSettingNode] と同じ保存境界で確定します。
     * 失敗分は保存されず、仮値へ戻ります。
     */
    private fun handleSliderCommit(context: GestureGuiActionContext, player: Player) {
        val fieldKey = context.elementId.removePrefix("lower-slider-commit:")
        if (fieldKey.isBlank()) return
        val settingContext = state.settingContext ?: return
        commitFieldPreviews { key, value ->
            if (key.fieldKey != fieldKey || value == null) return@commitFieldPreviews false
            if (settingContext.nodeId != key.nodeId) return@commitFieldPreviews false
            val script = plugin.scripts.load(settingContext.scriptId) ?: return@commitFieldPreviews false
            val node = script.graph.nodes[key.nodeId] ?: return@commitFieldPreviews false
            val normalized = CommandDialogSpecs.normalize(key.fieldKey, value)
            val dialogSpec = CommandDialogSpecs.field(node, key.fieldKey)
            if (dialogSpec != null && dialogSpec.validateInput(normalized) != null) {
                return@commitFieldPreviews false
            }
            updateSettingNode(player, settingContext, configuredFields = setOf(key.fieldKey)) {
                CommandSettingsModel.setParameter(it, key.fieldKey, normalized)
            }
        }
    }

    /**
     * 親タブ直下のプリセット選択でIDを保存します。
     *
     * 子画面遷移は行わず、親設定画面のまま保存・再描画します。
     * 一覧選択とDialog入力は同じ保存値への別経路であり、保存前には
     * 共通の検証を通します。一時変数参照方式の項目は、直接値選択時の
     * 操作面だけが表示されるため、ここでも直接値以外は受け付けません。
     */
    private fun applyInlineResourcePick(player: Player, elementId: String) {
        val (fieldKey, id) = InlineResourcePicker.parsePick(elementId) ?: return
        if (ResourcePicker.kindForFieldKey(fieldKey) == null) return
        if (id.isBlank()) return
        val valueFieldKey = ResourcePicker.valueFieldKey(fieldKey)
        val settingContext = state.settingContext ?: return
        val script = plugin.scripts.load(settingContext.scriptId) ?: return
        observedRevision = script.revision
        val node = script.graph.nodes[settingContext.nodeId] ?: return
        if (CommandSettingsModel.supportsTemporaryValueReference(node, valueFieldKey) &&
            CommandSettingsModel.temporaryValueSource(node, valueFieldKey) != CommandValueSource.LITERAL
        ) return
        val normalized = CommandDialogSpecs.normalize(valueFieldKey, id)
        val spec = CommandDialogSpecs.field(node, valueFieldKey)
        if (spec != null && spec.validateInput(normalized) != null) return
        if (!updateSettingNode(player, settingContext, configuredFields = setOf(fieldKey)) {
                CommandSettingsModel.setParameter(it, valueFieldKey, normalized)
            }) return
    }

    /**
     * issue #18 のCheckbox直接切替えです。
     *
     * 1クリックで値を確定し、保存・表示更新まで反映します。nullable三値は
     * ここへ載せず、別表現に委譲します。
     */
    private fun handleToggleAction(context: GestureGuiActionContext, player: Player) {
        val fieldKey = context.elementId.removePrefix("lower-toggle:")
        if (fieldKey.isBlank()) return
        val settingContext = state.settingContext ?: return
        val script = plugin.scripts.load(settingContext.scriptId) ?: return
        observedRevision = script.revision
        val node = script.graph.nodes[settingContext.nodeId] ?: return
        val descriptor = CommandSettingsModel.descriptor(node, fieldKey)
        if (SettingSpec.presentationFor(node, fieldKey, descriptor) != GesturePresentation.CHECKBOX) return
        val next = (!node.boolean(fieldKey)).toString()
        updateSettingNode(player, settingContext) {
            CommandSettingsModel.setParameter(it, fieldKey, next)
        }
    }

    /**
     * 共通入力仕様（CommandDialogSpecs）に沿った単一テキスト入力です。
     * プロンプト・maxLength・検証をインベントリGUIと同一に保ちます。
     */
    /**
     * 対象距離をDialogで入力します。空欄はその側の指定解除とします。
     *
     * 周囲系では最大距離だけを設定項目とするため、[maximumOnly] のときは
     * 最小値欄を出さず、入室時の最小値をそのまま引き継ぎます。
     */
    private fun beginSettingDistanceInput(
        player: Player,
        minimum: Double?,
        maximum: Double?,
        maximumOnly: Boolean = false,
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
            inputs = buildList {
                if (!maximumOnly) {
                    add(
                        CommandDialogSpecs.input(
                            player = player,
                            id = "minimum",
                            initial = format(minimum),
                            spec = spec,
                            label = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE),
                        ),
                    )
                }
                add(
                    CommandDialogSpecs.input(
                        player = player,
                        id = "maximum",
                        initial = format(maximum),
                        spec = spec,
                        label = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE),
                    ),
                )
            },
        ) { response ->
            val minimumRaw = if (maximumOnly) null else response.textValue("minimum").trim().takeIf(String::isNotEmpty)
            val maximumRaw = response.textValue("maximum").trim().takeIf(String::isNotEmpty)
            val validationError = listOfNotNull(minimumRaw, maximumRaw)
                .mapNotNull(spec::validateInput)
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val minimumValue = minimumRaw?.let(CommandDialogSpecs::finiteDouble) ?: minimum.takeIf { maximumOnly }
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
            updateUpperIfNeeded(player)
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
            updateUpperIfNeeded(player)
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
        // issue #31: 候補選択は即時保存せず、入力欄全体を選択IDで置き換えて
        // 確定待ちに戻します。複数クエリ入力からの選択も単一IDへの置換とし、
        // validationと保存は「確定」押下時の既存処理に一任します。
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
                        showTextInputDialog(
                            player = player,
                            spec = spec,
                            initial = candidate,
                            suggestionParameter = suggestionParameter,
                            candidateValues = emptyList(),
                            onSubmit = onSubmit,
                        )
                        MenuActionResult.Success(MenuUpdate.None)
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
                        candidateValues = CommandDialogSpecs.suggestionsFromInput(suggestionParameter, query),
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
            }.getOrElse {
                // Dialog内に保存失敗を表示するため、コンソールへ出しません。
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
            updateUpperIfNeeded(player)
            updateLower(player)
            null
        }
    }

    /**
     * issue #7 の起動方法の種別選択子画面へ遷移します。
     *
     * 種別選択はDialogの追加操作ではなく、GestureGUI上の独立した設定画面として扱います。
     * タイマーは確定後に間隔入力へ進み、信号・イベントは選択時に即時確定します。
     */
    private fun openTriggerKindSelection(player: Player) {
        state.lowerMode = GestureLowerMode.TRIGGER_KIND
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
                lowerPanel.buildTriggerKindChild(state, owner),
                GestureGuiChildOptions(
                    parentScreenId = lowerPanel.LOWER_SCREEN_ID,
                    overlayMaterial = Material.GRAY_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            false
        }
        if (!opened) {
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    /**
     * issue #8 のディスク設定子画面へ遷移します。
     *
     * 取り出し操作と引数定義の一覧を示します。引数の追加・改名・型変更・削除は
     * すべてこの画面から行い、子画面の内容差し替えで遷移します。
     */
    private fun openDiskSettings(player: Player) {
        state.diskSettingsArgsPage = 0
        state.lowerMode = GestureLowerMode.DISK_SETTINGS
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
                lowerPanel.buildDiskSettingsChild(state, owner),
                GestureGuiChildOptions(
                    parentScreenId = lowerPanel.LOWER_SCREEN_ID,
                    overlayMaterial = Material.GRAY_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            false
        }
        if (!opened) {
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    /** ディスク設定から設定一覧へ戻ります。子画面自体は閉じず、内容だけを差し替えます。 */
    private fun closeDiskSettings(player: Player) {
        state.lowerMode = GestureLowerMode.SETTINGS
        updateLower(player)
    }

    /**
     * 引数定義の変更を保存します。
     *
     * 成功時は子画面を再描画します。名前の重複・不正は保存前に拒否し、
     * 保存失敗時はログへ原因を残して画面に示します。
     */
    private fun saveDiskArguments(
        player: Player,
        change: (MutableList<DiskArgument>) -> Unit,
    ): Boolean {
        val updated = runCatching {
            CommandSettingsModel.updateDiskArguments(
                plugin,
                state.scriptId,
                editorId = player.uniqueId,
                expectedRevision = expectedMutationRevision(player),
                change = change,
            )
        }.getOrElse {
            // チャットへ保存失敗を通知するため、コンソールへ出しません。
            player.sendMessage(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
            )
            return false
        }
        if (!updated) {
            player.sendMessage(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
            )
            return false
        }
        updateLower(player)
        return true
    }

    /** 引数定義の追加・改名を名前入力で受け付けます。空欄・重複・不正名は拒否します。 */
    private fun editDiskArgumentName(player: Player, current: String?) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        observedRevision = script.revision
        val spec = CommandDialogSpecs.variableName
        val inputId = "value"
        showInputDialog(
            player = player,
            body = listOf(
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_ARG_NAME),
            ),
            inputs = listOf(
                CommandDialogSpecs.input(player, inputId, current.orEmpty(), spec),
            ),
        ) { response ->
            val raw = response.textValue(inputId).trim()
            val errorKey = when {
                raw.isEmpty() -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_VARIABLE_INVALID
                else -> spec.validateInput(raw)
            } ?: if (script.arguments.any { it.name == raw && it.name != current }) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED
            } else {
                null
            }
            if (errorKey != null) {
                KcI18n.text(player, errorKey)
            } else if (!saveDiskArguments(player) { arguments ->
                    if (current == null) {
                        arguments += DiskArgument(raw, ValueType.NUMBER)
                    } else {
                        val index = arguments.indexOfFirst { it.name == current }
                        if (index >= 0) arguments[index] = arguments[index].copy(name = raw)
                    }
                }) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            } else null
        }
    }

    /** 引数定義の型を全型で循環します。 */
    private fun cycleDiskArgumentType(player: Player, name: String) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        val current = script.arguments.firstOrNull { it.name == name } ?: return
        observedRevision = script.revision
        val entries = ValueType.entries
        val next = entries[(entries.indexOf(current.type) + 1) % entries.size]
        saveDiskArguments(player) { arguments ->
            val index = arguments.indexOfFirst { it.name == name }
            if (index >= 0) arguments[index] = arguments[index].copy(type = next)
        }
    }

    /** 引数定義を削除します。即時確定し、結果を画面に示します。 */
    private fun deleteDiskArgument(player: Player, name: String) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        if (script.arguments.none { it.name == name }) return
        observedRevision = script.revision
        if (!saveDiskArguments(player) { arguments ->
                arguments.removeIf { it.name == name }
            }
        ) {
            return
        }
        player.sendMessage(
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_ARG_DELETED),
        )
    }

    /** 起動方法の種別選択から設定一覧へ戻ります。子画面自体は閉じず、内容だけを差し替えます。 */
    private fun closeTriggerKindSelection(player: Player) {
        state.lowerMode = GestureLowerMode.SETTINGS
        updateLower(player)
    }

    /** 起動方法の種別選択からイベント種別の選択へ進みます。子画面の内容だけを差し替えます。 */
    private fun openTriggerEventSelection(player: Player) {
        state.triggerEventPage = 0
        state.lowerMode = GestureLowerMode.TRIGGER_EVENT
        updateLower(player)
    }

    /**
     * 起動方法を保存します。
     *
     * 保存失敗時はログへ原因を残し、画面には保存失敗を示します。成功時は
     * 設定一覧へ戻して上下画面を更新します。
     */
    private fun saveTrigger(player: Player, trigger: ScriptTrigger): Boolean {
        val updated = runCatching {
            CommandSettingsModel.updateTrigger(
                plugin,
                state.scriptId,
                trigger,
                editorId = player.uniqueId,
                expectedRevision = expectedMutationRevision(player),
            )
        }.getOrElse {
            // チャットへ保存失敗を通知するため、コンソールへ出しません。
            player.sendMessage(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
            )
            return false
        }
        if (!updated) {
            player.sendMessage(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
            )
            return false
        }
        state.lowerMode = GestureLowerMode.SETTINGS
        updateUpperIfNeeded(player)
        updateLower(player)
        return true
    }

    /**
     * issue #8 の呼び出し設定子画面へ遷移します。
     */
    private fun openDiskCallSettings(player: Player) {
        state.diskCallArgsPage = 0
        state.lowerMode = GestureLowerMode.DISK_CALL_SETTINGS
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
                lowerPanel.buildDiskCallSettingsChild(state, owner),
                GestureGuiChildOptions(
                    parentScreenId = lowerPanel.LOWER_SCREEN_ID,
                    overlayMaterial = Material.GRAY_STAINED_GLASS,
                    animated = false,
                ),
            )
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            false
        }
        if (!opened) {
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    /** 呼び出し設定から設定一覧へ戻ります。子画面自体は閉じず、内容だけを差し替えます。 */
    private fun closeDiskCallSettings(player: Player) {
        state.lowerMode = GestureLowerMode.SETTINGS
        updateLower(player)
    }

    /** 呼び出し設定の変更を保存し、成功時は子画面を再描画します。 */
    private fun saveDiskCallNode(
        player: Player,
        change: (me.awabi2048.kantancommander.model.CommandNode) -> Unit,
    ): Boolean {
        val script = plugin.scripts.load(state.scriptId) ?: return false
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return false
        val context = CommandSettingContext(
            state.scriptId,
            node.id,
            CommandSettingsModel.descriptor(node, "diskCallSettings").role,
        )
        val saved = updateSettingNode(player, context, configuredFields = emptySet(), change = change)
        if (saved) updateLower(player)
        return saved
    }

    /**
     * 引数の値を編集します。
     *
     * 複合型は変数名のみ受け付け、スカラー型はリテラル（テンプレート参照を含む）を
     * 受け付けます。リテラルと変数指定は排他で、保存時に他方を除去します。
     */
    private fun editDiskCallArgument(player: Player, name: String) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
        val argument = node.declaredArguments().firstOrNull { it.name == name } ?: return
        observedRevision = script.revision
        val complex = argument.type !in setOf(ValueType.NUMBER, ValueType.STRING, ValueType.BOOLEAN)
        val inputId = "value"
        if (complex) {
            val spec = CommandDialogSpecs.variableName
            showInputDialog(
                player = player,
                body = listOf(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_ARG_VARIABLE),
                ),
                inputs = listOf(
                    CommandDialogSpecs.input(
                        player,
                        inputId,
                        node.string("argvar_$name"),
                        spec,
                        Component.text(name),
                    ),
                ),
            ) { response ->
                val raw = response.textValue(inputId).trim()
                val errorKey = if (raw.isEmpty()) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_VARIABLE_INVALID
                } else {
                    spec.validateInput(raw)
                }
                if (errorKey != null) {
                    KcI18n.text(player, errorKey)
                } else if (!saveDiskCallNode(player) { target ->
                        target.params["argvar_$name"] = raw
                        target.params.remove("arg_$name")
                        target.clearConfigured("arg_$name")
                        target.markConfigured("argvar_$name")
                    }) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else null
            }
            return
        }
        val spec = CommandDialogSpecs.Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
            256,
            validate = { raw ->
                if (isValidArgumentLiteral(argument.type, raw)) null
                else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            },
        )
        showInputDialog(
            player = player,
            body = listOf(
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_ARG_VALUE),
            ),
            inputs = listOf(
                CommandDialogSpecs.input(player, inputId, node.string("arg_$name"), spec, Component.text(name)),
            ),
        ) { response ->
            val raw = response.textValue(inputId).trim()
            val errorKey = if (raw.isEmpty()) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            } else {
                spec.validateInput(raw)
            }
            if (errorKey != null) {
                KcI18n.text(player, errorKey)
            } else if (!saveDiskCallNode(player) { target ->
                    target.params["arg_$name"] = raw
                    target.params.remove("argvar_$name")
                    target.clearConfigured("argvar_$name")
                    target.markConfigured("arg_$name")
                }) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            } else null
        }
    }

    /** 引数のリテラルが型に適合するかを判定します。規則は共通モデルで共有します。 */
    private fun isValidArgumentLiteral(type: ValueType, raw: String): Boolean =
        CommandSettingsModel.isCallArgumentLiteralValid(type, raw)

    /** 戻り値の受取先scopeをEXECUTION／WORLDで切り替えます。受取先未設定時は変更しません。 */
    private fun cycleDiskCallReturnScope(player: Player) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
        if (node.string("returnName").isBlank()) return
        observedRevision = script.revision
        val current = node.string("returnScope").ifBlank { VariableScope.EXECUTION.name }
        val next = if (current == VariableScope.WORLD.name) {
            VariableScope.EXECUTION.name
        } else {
            VariableScope.WORLD.name
        }
        saveDiskCallNode(player) { target ->
            target.params["returnScope"] = next
            target.markConfigured("returnScope")
        }
    }

    /**
     * 戻り値の受取先変数名を編集します。空欄で受取を解除します。
     */
    private fun editDiskCallReturnName(player: Player) {
        val script = plugin.scripts.load(state.scriptId) ?: return
        val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
        observedRevision = script.revision
        val spec = CommandDialogSpecs.variableName
        val inputId = "value"
        showInputDialog(
            player = player,
            body = listOf(
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_RETURN_NAME),
            ),
            inputs = listOf(
                CommandDialogSpecs.input(player, inputId, node.string("returnName"), spec),
            ),
        ) { response ->
            val raw = response.textValue(inputId).trim()
            when {
                raw.isEmpty() -> {
                    if (!saveDiskCallNode(player) { target ->
                            target.params.remove("returnName")
                            target.params.remove("returnScope")
                            target.clearConfigured("returnName")
                            target.clearConfigured("returnScope")
                        }
                    ) {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                    } else null
                }
                spec.validateInput(raw) != null -> KcI18n.text(player, spec.validateInput(raw)!!)
                !saveDiskCallNode(player) { target ->
                    target.params["returnName"] = raw
                    if (target.string("returnScope").isBlank()) {
                        target.params["returnScope"] = VariableScope.EXECUTION.name
                    }
                    target.markConfigured("returnName")
                    target.markConfigured("returnScope")
                } -> KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                else -> null
            }
        }
    }

    /** ノード未選択時のプログラムのタイマー間隔を秒単位で設定します。 */
    private fun showTriggerIntervalDialog(player: Player) {
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
            val seconds = requireNotNull(CommandDialogSpecs.parseTimerSeconds(rawSeconds))
            state.lowerMode = GestureLowerMode.SETTINGS
            if (!saveTrigger(player, ScriptTrigger(TriggerKind.TIMER, intervalSeconds = seconds))) {
                return@showInputDialog KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                )
            }
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
                // 失敗時の通知はupdateLower側へ一本化しています。
                updateLower(player)
            })
        }.onFailure {
            // 保存は成功しているため、一覧更新の予約失敗は別事象の通知へ
            // 一本化し、コンソールへ出しません。
            player.sendMessage(
                KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_LIST_REFRESH_FAILED,
                ),
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
    /**
     * タグ一覧子画面の操作対象ノードです。
     *
     * 一覧表示と同一世代のノードを取り直し、ページ送り後の古い行番号が
     * 別世代の内容へ作用しないようにします。
     */
    private fun entityTagNode(): Pair<CommandSettingContext, CommandNode>? {
        val settingContext = state.settingContext ?: return null
        val node = plugin.scripts.load(settingContext.scriptId)?.graph?.nodes?.get(settingContext.nodeId)
            ?: return null
        return settingContext to node
    }

    /** タグ一覧を保存します。成功時は下部画面を更新します。 */
    private fun saveEntityTagEntries(
        player: Player,
        settingContext: CommandSettingContext,
        entries: List<EntityTagEntries.Entry>,
    ): Boolean = updateSettingNode(
        player,
        settingContext,
        configuredFields = setOf(EntityTagEntries.PARAM_ENTRIES),
    ) {
        CommandSettingsModel.setParameter(it, EntityTagEntries.PARAM_ENTRIES, EntityTagEntries.encode(entries))
    }

    /** タグ一覧子画面の追加入力です。入力値をaddモードで追記します。 */
    private fun showTagEntryDialog(player: Player) {
        val (_, node) = entityTagNode() ?: return
        beginSettingInput(
            player,
            requireNotNull(CommandDialogSpecs.field(node, EntityTagEntries.PARAM_ENTRIES)),
            "",
        ) { raw ->
            val normalized = raw.trim()
            val (_, current) = entityTagNode()
                ?: return@beginSettingInput KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                )
            val spec = CommandDialogSpecs.field(current, EntityTagEntries.PARAM_ENTRIES)
                ?: return@beginSettingInput KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                )
            val validationError = spec.validateInput(normalized)
            if (validationError != null) {
                KcI18n.text(player, validationError)
            } else {
                val entries = EntityTagEntries.parse(current.string(EntityTagEntries.PARAM_ENTRIES))
                    ?: return@beginSettingInput KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                    )
                val updated = entries + EntityTagEntries.Entry(EntityTagEntries.MODE_ADD, normalized)
                if (!EntityTagEntries.isValid(updated)) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT)
                } else {
                    val (settingContext, _) = entityTagNode()
                        ?: return@beginSettingInput KcI18n.text(
                            player,
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                        )
                    if (!saveEntityTagEntries(player, settingContext, updated)) {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                    } else null
                }
            }
        }
    }

    /** タグ一覧子画面のモード切替です。add⇔removeを切り替えます。 */
    private fun toggleEntityTagMode(player: Player, index: Int) {
        val (settingContext, node) = entityTagNode() ?: return
        val current = EntityTagEntries.parse(node.string(EntityTagEntries.PARAM_ENTRIES)) ?: return
        val target = current.getOrNull(index) ?: return
        val toggled = if (target.mode == EntityTagEntries.MODE_ADD) EntityTagEntries.MODE_REMOVE else EntityTagEntries.MODE_ADD
        val updated = current.toMutableList().also { it[index] = target.copy(mode = toggled) }
        if (!EntityTagEntries.isValid(updated)) return
        saveEntityTagEntries(player, settingContext, updated)
    }

    /** タグ一覧子画面の削除です。該当エントリのみ削除します。 */
    private fun deleteEntityTagEntry(player: Player, index: Int) {
        val (settingContext, node) = entityTagNode() ?: return
        val current = EntityTagEntries.parse(node.string(EntityTagEntries.PARAM_ENTRIES)) ?: return
        if (index !in current.indices) return
        saveEntityTagEntries(player, settingContext, current.toMutableList().also { it.removeAt(index) })
    }

    private fun openWorldVariables(player: Player) {        if (resolveVariableWorldId() == null) return
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
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
            false
        }
        if (!opened) {
            state.lowerMode = GestureLowerMode.SETTINGS
            updateLower(player)
        }
    }

    /**
     * 未選択時下部のディスク取得操作です。簡易GUIの出力操作と同じく、内容のある
     * プログラムだけを独立したプログラムディスクへ複製して配布します。
     * 元スクリプトは変更しないため、画面の再描画は行いません。
     */
    private fun outputScriptDisk(player: Player) {
        val source = plugin.scripts.load(state.scriptId) ?: return
        // 空プログラムの取得は表示側のガードでも拒否しますが、保存内容は操作後に
        // 変わり得るため、実行時点でも同じ条件を再確認します。
        if (!source.hasDiskContent()) return
        val placement = state.placement ?: return
        if (!plugin.placementAccess.requireManage(player, placement.world)) return
        val world = plugin.server.getWorld(placement.world) ?: return
        val block = world.getBlockAt(placement.x, placement.y, placement.z)
        if (plugin.placements.find(block.location)?.scriptId != source.id) return
        val output = runCatching { plugin.scripts.copyForItem(source) }.getOrNull() ?: return
        val item = runCatching { KantanItemService.createDisk(output, player) }.getOrElse {
            runCatching { plugin.scripts.delete(output.id) }
            return
        }
        player.inventory.addItem(item).values.forEach { world.dropItemNaturally(player.location, it) }
        player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_DISK_OUTPUT))
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

    /**
     * 一覧から選んだ変数の値を編集します。型は定義側を正とし、変更しません。
     *
     * スカラー3型はDialogで編集します。複合6型の値は変数操作コマンドの型別設定で
     * 編集するため、一覧からは案内のみ表示します。
     */
    private fun showWorldVariableValueDialog(player: Player, name: String) {
        val worldId = resolveVariableWorldId() ?: return
        val current = plugin.variables.get(worldId, name) ?: return
        val type = plugin.variables.definitions(worldId)[name]?.type ?: current.type
        // 型ごとの長さ・有限値・参照記法の検証は、Inventory/Gestureで分岐させず
        // 共通Specへ集約します。画面側はDialogの表示と保存だけを担当します。
        val spec = CommandDialogSpecs.worldVariableValue(type)
        if (spec == null) {
            player.sendMessage(KcI18n.text(player, CommandDialogSpecs.worldVariableValueBody(type)))
            return
        }
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
                ValueType.NUMBER -> TypedValue(
                    ValueType.NUMBER,
                    numberValue = CommandValueRules.parseFiniteDouble(raw),
                ).takeIf { it.numberValue != null } ?: return@showInputDialog KcI18n.text(
                    player,
                    CommandDialogSpecs.worldVariableValueInvalid(ValueType.NUMBER),
                )
                ValueType.STRING -> TypedValue(ValueType.STRING, stringValue = raw)
                ValueType.BOOLEAN -> TypedValue(
                    ValueType.BOOLEAN,
                    booleanValue = raw.trim().lowercase().toBooleanStrictOrNull(),
                ).takeIf { it.booleanValue != null } ?: return@showInputDialog KcI18n.text(
                    player,
                    CommandDialogSpecs.worldVariableValueInvalid(ValueType.BOOLEAN),
                )
                ValueType.LOCATION,
                ValueType.VECTOR,
                ValueType.ITEM,
                ValueType.BLOCK,
                ValueType.ENTITY,
                -> return@showInputDialog KcI18n.text(
                    player,
                    CommandDialogSpecs.worldVariableValueBody(type),
                )
            }
            val saved = runCatching { plugin.variables.set(worldId, name, value); true }
                .getOrElse {
                    // Dialog内に保存失敗を表示するため、コンソールへ出しません。
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
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
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
    private fun showWorldVariableNameDialog(player: Player, type: ValueType) {
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
                .getOrElse {
                    // Dialog内に保存失敗を表示するため、コンソールへ出しません。
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
                .getOrElse {
                    // Dialog内に保存失敗を表示するため、コンソールへ出しません。
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
            .getOrElse {
                // 確認できない場合は一覧を再描画し、コンソールへ出しません。
                updateLower(player)
                return
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
        }.getOrElse {
            // 子画面のオープン失敗は操作者への通知へ一本化し、コンソールへ出しません。
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT))
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
            .getOrElse {
                // 確認画面に保存失敗を表示するため、コンソールへ出しません。
                // 下のnull分岐と重複通知にならないよう、ここで終えます。
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                return
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

    /**
     * DISPLAY_TEXTの時間設定を、インベントリGUIと同じ仕様で編集します。
     *
     * issue #35-2: タイトル・サブタイトルは3項目、アクションバーは単独の
     * 表示時間1項目です。
     */
    private fun showDisplayTimingSettingDialog(
        player: Player,
        context: CommandSettingContext,
        node: me.awabi2048.kantancommander.model.CommandNode,
    ) {
        val isActionbar = node.string("mode", "tellraw") == "actionbar"
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
            inputs = if (isActionbar) {
                CommandDialogSpecs.actionbarDurationInputs(player, stay)
            } else {
                CommandDialogSpecs.durationInputs(player, fadeIn, stay, fadeOut)
            },
        ) { response ->
            val keys = if (isActionbar) listOf("staySeconds") else listOf("fadeInSeconds", "staySeconds", "fadeOutSeconds")
            val rawValues = keys.associateWith { key ->
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
        // Inventory GUIと同じく既定値で補完します。未設定時に空欄で開くと必須検証に
        // 必ず失敗し、両GUIで同じ値を表示しているのに編集結果が変わります。
        val values = parameterKeys.associateWith { key -> node.string(key, node.type.defaults[key].orEmpty()) }
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
     * 効果音設定の束ね値（ID・音量・ピッチ／ID・カテゴリ）を一括検証して保存します。
     *
     * Inventory GUIのshowSoundSettingFormと同じ入力欄・検証を使い、操作ごとの
     * 入力欄集合もCommandDialogSpecsへ一元化します。定型プリセットはGesture GUIへ集約し、
     * Dialogには入力文字列で絞り込んだ候補だけを提示します。
     */
    private fun showSoundSettingDialog(
        player: Player,
        context: CommandSettingContext,
        node: CommandNode,
        soundOverride: String? = null,
        soundCandidates: List<String> = emptyList(),
    ) {
        val inputs = CommandDialogSpecs.soundSettingInputs(node)
        // 候補選択の開き直しで引き継いだ入力途中値は、ここで消費します。
        // 確定せずに閉じた場合も残さないため、次回の新規表示は保存値から始まります。
        val pending = pendingSoundDialogValues.remove(player.uniqueId).orEmpty()
        // 候補の確定はサウンド欄だけを置き換え、他の欄は入力途中の
        // 値を維持して同じダイアログへ戻します。即時保存はしません。
        fun candidateButton(candidate: String) = MenuDialogButton(
            Component.text(candidate),
            MenuDialogHandler { target, response ->
                if (target.uniqueId != player.uniqueId ||
                    !canOperateSharedActor(ownerIdFor(player), target.uniqueId)
                ) {
                    if (target.uniqueId == player.uniqueId) invalidateInput(player.uniqueId)
                    return@MenuDialogHandler MenuActionResult.Ignored
                }
                val current = nodeStringMap(response, inputs.map { it.id })
                pendingSoundDialogValues[player.uniqueId] = current
                showSoundSettingDialog(player, context, node, soundOverride = candidate, soundCandidates = emptyList())
                MenuActionResult.Success(MenuUpdate.None)
            },
        )
        val candidateButtons = soundCandidates.take(12).map(::candidateButton)
        val candidateFooterActions = listOf(MenuDialogButton(
            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SHOW_DETAILS),
            MenuDialogHandler { target, response ->
                val ownerId = ownerIdFor(player)
                if (target.uniqueId != player.uniqueId || !canOperateSharedActor(ownerId, target.uniqueId)) {
                    if (target.uniqueId == player.uniqueId) invalidateInput(player.uniqueId)
                    return@MenuDialogHandler MenuActionResult.Ignored
                }
                // 候補判定はサウンド欄の内容だけを見ます。音量・カテゴリ欄は
                // クエリに含めず、入力途中の値も破棄しません。
                val query = response.textValue(SoundSettings.PARAM_SOUND).trim()
                pendingSoundDialogValues[player.uniqueId] =
                    nodeStringMap(response, inputs.map { it.id })
                showSoundSettingDialog(
                    player,
                    context,
                    node,
                    soundCandidates = CommandDialogSpecs.suggestionsFromInput("sound", query),
                )
                MenuActionResult.Success(MenuUpdate.None)
            },
        ))
        showInputDialog(
            player = player,
            body = CommandDialogSpecs.soundSettingBody(node, player),
            inputs = inputs.map {
                if (it.id == SoundSettings.PARAM_SOUND && soundOverride != null) {
                    it.copy(initial = soundOverride).toMenuInput(player)
                } else {
                    it.copy(initial = pending[it.id] ?: it.initial).toMenuInput(player)
                }
            },
            additionalActions = candidateButtons,
            footerActions = candidateFooterActions,
            multiActionWithoutExit = true,
            columns = 3,
        ) { response ->
            pendingSoundDialogValues.remove(player.uniqueId)
            val rawValues = inputs.associate { it.id to CommandDialogSpecs.normalize(it.id, response.textValue(it.id)) }
            val validationError = inputs
                .mapNotNull { it.spec.validateInput(rawValues.getValue(it.id)) }
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            if (!updateSettingNode(player, context, configuredFields = setOf("soundSetting")) { command ->
                    CommandSettingsModel.setParameters(command, rawValues)
                }) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            }
            null
        }
    }

    /** 候補選択で開き直す間に、入力途中の値を保持する一時領域です。 */
    private val pendingSoundDialogValues = mutableMapOf<UUID, Map<String, String>>()

    private fun nodeStringMap(response: MenuDialogResponse, ids: List<String>): Map<String, String> =
        ids.associateWith { response.textValue(it) }

    /**
     * パーティクル種類と詳細データを一括検証して保存します。
     *
     * VIBRATION／TRAILは終点XYZを1群として束ねた入力欄から単一詳細文字列へ
     * 合成します。保存形式は従来の1文字列のまま変えません。
     * 種類欄は「検索」による補完に対応し、候補確定時は種類欄だけを
     * 置き換えて同じダイアログへ戻します。即時保存はしません。
     */
    private fun showParticleDialog(
        player: Player,
        context: CommandSettingContext,
        node: CommandNode,
        particleOverride: String? = null,
        particleCandidates: List<String> = emptyList(),
    ) {
        // 候補確定時は種類欄だけを置き換え、詳細欄の入力途中値は維持します。
        // 確定せずに閉じた場合も残さないため、次回の新規表示は保存値から始まります。
        val pending = pendingParticleDialogValues.remove(player.uniqueId).orEmpty()
        val effectiveNode = if (particleOverride != null) {
            node.copy(params = (node.params + mapOf(ParticleSettings.PARAM_PARTICLE to particleOverride)).toMutableMap())
        } else node
        val inputs = CommandDialogSpecs.particleInputs(effectiveNode)
        val particle = ParticleSettings.particle(effectiveNode)
        val candidateButtons = particleCandidates.take(12).map { candidate ->
            MenuDialogButton(
                Component.text(candidate),
                MenuDialogHandler { target, response ->
                    if (target.uniqueId != player.uniqueId ||
                        !canOperateSharedActor(ownerIdFor(player), target.uniqueId)
                    ) {
                        if (target.uniqueId == player.uniqueId) invalidateInput(player.uniqueId)
                        return@MenuDialogHandler MenuActionResult.Ignored
                    }
                    val current = nodeStringMap(response, inputs.map { it.id })
                    pendingParticleDialogValues[player.uniqueId] = current
                    showParticleDialog(player, context, node, particleOverride = candidate, particleCandidates = emptyList())
                    MenuActionResult.Success(MenuUpdate.None)
                },
            )
        }
        val candidateFooterActions = listOf(MenuDialogButton(
            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SHOW_DETAILS),
            MenuDialogHandler { target, response ->
                val ownerId = ownerIdFor(player)
                if (target.uniqueId != player.uniqueId || !canOperateSharedActor(ownerId, target.uniqueId)) {
                    if (target.uniqueId == player.uniqueId) invalidateInput(player.uniqueId)
                    return@MenuDialogHandler MenuActionResult.Ignored
                }
                // 候補判定は種類欄の内容だけを見ます。詳細欄はクエリに含めず、
                // 入力途中の値も破棄しません。
                val query = response.textValue(ParticleSettings.PARAM_PARTICLE).trim()
                pendingParticleDialogValues[player.uniqueId] =
                    nodeStringMap(response, inputs.map { it.id })
                showParticleDialog(
                    player,
                    context,
                    node,
                    particleOverride = particleOverride,
                    particleCandidates = CommandDialogSpecs.suggestionsFromInput(ParticleSettings.PARAM_PARTICLE, query),
                )
                MenuActionResult.Success(MenuUpdate.None)
            },
        ))
        showInputDialog(
            player = player,
            body = if (particle != null && ParticleSettings.requiresData(particle)) {
                listOf(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_PARTICLE_DATA_BODY))
            } else {
                emptyList()
            },
            inputs = inputs.map {
                if (it.id == ParticleSettings.PARAM_PARTICLE && particleOverride != null) {
                    it.copy(initial = particleOverride).toMenuInput(player)
                } else {
                    it.copy(initial = pending[it.id] ?: it.initial).toMenuInput(player)
                }
            },
            additionalActions = candidateButtons,
            footerActions = candidateFooterActions,
            multiActionWithoutExit = true,
            columns = 3,
        ) { response ->
            pendingParticleDialogValues.remove(player.uniqueId)
            val rawValues = inputs.associate { it.id to CommandDialogSpecs.normalize(it.id, response.textValue(it.id)) }
            val validationError = inputs
                .mapNotNull { it.spec.validateInput(rawValues.getValue(it.id)) }
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val composed = mutableMapOf<String, String>()
            composed[ParticleSettings.PARAM_PARTICLE] = rawValues.getValue(ParticleSettings.PARAM_PARTICLE)
            when {
                rawValues.containsKey("vibX") -> composed[ParticleSettings.PARAM_DATA] =
                    CommandDialogSpecs.composeVibrationData(rawValues)
                rawValues.containsKey("trailX") -> composed[ParticleSettings.PARAM_DATA] =
                    CommandDialogSpecs.composeTrailData(rawValues)
                rawValues.containsKey(ParticleSettings.PARAM_DATA) -> composed[ParticleSettings.PARAM_DATA] =
                    rawValues.getValue(ParticleSettings.PARAM_DATA)
            }
            if (!updateSettingNode(player, context, configuredFields = setOf("particle", "particleData")) { command ->
                    CommandSettingsModel.setParameters(command, composed)
                }) {
                return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            }
            null
        }
    }

    /** 候補選択で開き直す間に、入力途中の値を保持する一時領域です。 */
    private val pendingParticleDialogValues = mutableMapOf<UUID, Map<String, String>>()

    /** 図形数値を現形状ごとに一括検証して保存します。 */
    private fun showGeometryNumbersDialog(
        player: Player,
        context: CommandSettingContext,
        node: CommandNode,
    ) {
        val inputs = CommandDialogSpecs.geometryInputs(node)
        showInputDialog(
            player = player,
            body = CommandDialogSpecs.geometryBody(player),
            inputs = inputs.map { it.toMenuInput(player) },
        ) { response ->
            val rawValues = inputs.associate { it.id to CommandDialogSpecs.normalize(it.id, response.textValue(it.id)) }
            val validationError = inputs
                .mapNotNull { it.spec.validateInput(rawValues.getValue(it.id)) }
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            if (!updateSettingNode(player, context, configuredFields = setOf("geometrySettings")) { command ->
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
                                .getOrElse {
                                    // 入力検証の戻り値と、保存・データ処理の例外を分離します。
                                    // 後者を入力形式エラーへ変換すると、保存済みの変数だけが
                                    // 残ったままDialogをキャンセルできる今回の不整合を再発させます。
                                    // Dialog内に保存失敗を表示するため、コンソールへ出しません。
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
                            // 確定成功後の副作用の失敗は定常のためコンソールへ出しません。
                            runCatching { afterSubmit?.invoke() }
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
        semanticsX: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
        semanticsY: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
        semanticsZ: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
        allowNotation: Boolean = true,
        onSubmit: (CoordinateSemantics, Double, CoordinateSemantics, Double, CoordinateSemantics, Double) -> String?,
    ) {
        showInputDialog(
            player = player,
            body = CommandDialogSpecs.coordinateBody(player),
            inputs = CommandDialogSpecs.coordinateInputs(
                player,
                CommandDialogSpecs.formatCoordinateComponent(semanticsX, x),
                CommandDialogSpecs.formatCoordinateComponent(semanticsY, y),
                CommandDialogSpecs.formatCoordinateComponent(semanticsZ, z),
            ),
        ) { response ->
            val rawValues = listOf("x", "y", "z").associateWith { key -> response.textValue(key).trim() }
            val validationError = rawValues.entries
                .mapNotNull { (key, raw) ->
                    CommandDialogSpecs.coordinateSpec(key, allowNotation).validateInput(raw)
                }
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val parsed = rawValues.mapValues { (_, raw) ->
                // 注視点など記法対象外の欄では、検証で接頭辞を弾いているため素の数値として扱います。
                if (allowNotation) CommandDialogSpecs.parseCoordinateComponent(raw)
                else CommandDialogSpecs.finiteDouble(raw)?.let { CoordinateSemantics.ABSOLUTE to it }
            }
            val (sx, xValue) = parsed.getValue("x") ?: return@showInputDialog invalidCoordinates(player)
            val (sy, yValue) = parsed.getValue("y") ?: return@showInputDialog invalidCoordinates(player)
            val (sz, zValue) = parsed.getValue("z") ?: return@showInputDialog invalidCoordinates(player)
            onSubmit(sx, xValue, sy, yValue, sz, zValue)
        }
    }

    private fun invalidCoordinates(player: Player): String =
        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_COORDINATES_INVALID)

    /**
     * 設定画面の表示を現在の編集状態へ戻します。
     *
     * 設定選択の分岐内では同名のローカル関数へ委譲し、表示遷移の正本は
     * ここに一本化します。子画面の開閉状態で戻り先を切り替えます。
     */
    private fun refreshSettingScreen(player: Player, openChild: Boolean = false) {
        val ownerId = ownerIdFor(player)
        state.lowerMode = if (settingChildOpen(ownerId) || state.settingRoute.size > 1) {
            GestureLowerMode.SETTING_CHOICES
        } else {
            GestureLowerMode.SETTINGS
        }
        if (openChild && !settingChildOpen(ownerId)) ensureSettingChild(player) else updateLower(player)
    }

    /**
     * issue #4・#5: 領域基準の一時変数名入力です。
     *
     * 対象絞り込みと条件の両系統で同じ入力仕様（ラベル・検証）を使い、
     * 保存先だけを呼び出し側の書き込みに委ねます。基準種別は呼び出し前に
     * 確定しているため、ここでは変数名の入力だけを扱います。
     */
    private fun openRegionTempNameDialog(
        player: Player,
        settingContext: CommandSettingContext,
        currentName: String?,
        writeTempName: (me.awabi2048.kantancommander.model.CommandNode, String) -> Unit,
    ) {
        beginSettingInput(
            player,
            CommandDialogSpecs.variableName,
            currentName.orEmpty(),
        ) { raw ->
            val name = raw.trim()
            if (!updateSettingNode(player, settingContext) {
                    writeTempName(it, name)
                }) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            } else {
                refreshSettingScreen(player)
                null
            }
        }
    }

    /**
     * issue #5: 条件の領域編集です。
     *
     * 対象絞り込み側（TARGET_FILTERS）と同じ対話部品（範囲・距離・座標・
     * 変数名・対象プリセット）を使い、保存先だけをノードの領域指定へ
     * 振り替えます。空になった領域は保持せず null へ戻します。
     */
    private fun handleConditionRegion(
        player: Player,
        settingContext: CommandSettingContext,
        node: me.awabi2048.kantancommander.model.CommandNode,
        encoded: String,
    ) {
        fun currentRegion(): RegionSpec =
            node.regionSpec?.takeIf { it.hasAnySetting() } ?: RegionSpec()
        fun writeRegion(target: me.awabi2048.kantancommander.model.CommandNode, region: RegionSpec?) {
            target.regionSpec = region?.takeIf { it.hasAnySetting() }
        }
        // 行自体のクリックは子選択肢へ委ね、保存しません。
        if (encoded == "condition-region") {
            refreshSettingScreen(player)
            return
        }
        if (encoded.startsWith("condition-region-anchor:")) {
            val kind = runCatching {
                RegionAnchorKind.valueOf(encoded.removePrefix("condition-region-anchor:"))
            }.getOrNull() ?: return
            if (updateSettingNode(player, settingContext) {
                    val base = it.regionSpec?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                    // 基準切替えでは範囲・距離を引き継ぎ、基準固有参数だけを初期化します。
                    writeRegion(it, base.copy(anchor = RegionAnchorSpec(kind)))
                }) refreshSettingScreen(player)
            return
        }
        if (encoded == "condition-region-range") {
            val base = currentRegion()
            beginSettingRangeInput(player, base.dx, base.dy, base.dz) { dx, dy, dz ->
                if (!updateSettingNode(player, settingContext) {
                        val latest = it.regionSpec?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                        writeRegion(it, latest.copy(dx = dx, dy = dy, dz = dz))
                    }) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else null
            }
            return
        }
        if (encoded == "condition-region-distance") {
            val base = currentRegion()
            beginSettingDistanceInput(
                player,
                base.minimumDistance,
                base.maximumDistance,
                maximumOnly = false,
            ) { minimum, maximum ->
                if (!updateSettingNode(player, settingContext) {
                        val latest = it.regionSpec?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                        writeRegion(
                            it,
                            latest.copy(minimumDistance = minimum, maximumDistance = maximum),
                        )
                    }) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else null
            }
            return
        }
        if (encoded == "condition-region-position") {
            val position = currentRegion().anchor.position
            showCoordinateSettingDialog(
                player,
                position?.x ?: player.location.x,
                position?.y ?: player.location.y,
                position?.z ?: player.location.z,
                semanticsX = position?.semanticsX ?: CoordinateSemantics.ABSOLUTE,
                semanticsY = position?.semanticsY ?: CoordinateSemantics.ABSOLUTE,
                semanticsZ = position?.semanticsZ ?: CoordinateSemantics.ABSOLUTE,
            ) { sx, x, sy, y, sz, z ->
                if (!updateSettingNode(player, settingContext) {
                        val latest = it.regionSpec?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                        writeRegion(
                            it,
                            latest.copy(
                                anchor = latest.anchor.copy(
                                    kind = RegionAnchorKind.FIXED,
                                    position = PositionSpec(
                                        PositionKind.COORDINATES,
                                        x, y, z,
                                        semanticsX = sx, semanticsY = sy, semanticsZ = sz,
                                    ),
                                ),
                            ),
                        )
                    }) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else null
            }
            return
        }
        if (encoded == "condition-region-temp") {
            openRegionTempNameDialog(player, settingContext, currentRegion().anchor.tempName) { target, name ->
                val latest = target.regionSpec?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                writeRegion(
                    target,
                    latest.copy(
                        anchor = latest.anchor.copy(
                            kind = RegionAnchorKind.TEMPORARY,
                            tempName = name,
                        ),
                    ),
                )
            }
            return
        }
        if (encoded.startsWith("condition-region-target-preset:")) {
            val preset = runCatching {
                SettingPresets.TargetPreset.valueOf(encoded.removePrefix("condition-region-target-preset:"))
            }.getOrNull() ?: return
            if (updateSettingNode(player, settingContext) {
                    val latest = it.regionSpec?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                    writeRegion(
                        it,
                        latest.copy(
                            anchor = latest.anchor.copy(
                                kind = RegionAnchorKind.TARGET_ENTITY,
                                target = SettingPresets.targetSpecFor(preset),
                            ),
                        ),
                    )
                }) refreshSettingScreen(player)
            return
        }
        if (encoded == "condition-region-clear") {
            if (updateSettingNode(player, settingContext) {
                    writeRegion(it, null)
                }) refreshSettingScreen(player)
            return
        }
    }

    /**
     * issue #29: プリセットが表す位置種別です。
     *
     * 選択可否の既存判定（制御ブロック上書き等）へ渡します。
     */
    private fun presetPositionKind(preset: SettingPresets.PositionPreset): PositionKind = when (preset) {
        SettingPresets.PositionPreset.CONTROL_BLOCK -> PositionKind.DISK
        SettingPresets.PositionPreset.NEAREST_PLAYER,
        SettingPresets.PositionPreset.NEARBY_PLAYERS,
        -> PositionKind.TARGET
        SettingPresets.PositionPreset.MYWORLD_SPAWN -> PositionKind.MYWORLD_SPAWN
        SettingPresets.PositionPreset.COORDINATES -> PositionKind.COORDINATES
    }

    /**
     * issue #29: 座標プリセットの現在値初期表示つき入力です。
     *
     * 既存の座標方式フローと同じ保存・検証を使い、向き成分は保持します。
     */
    private fun openPositionCoordinateDialog(
        player: Player,
        settingContext: CommandSettingContext,
        fieldKey: String,
    ) {
        val script = plugin.scripts.load(settingContext.scriptId) ?: return
        val node = script.graph.nodes[settingContext.nodeId] ?: return
        val current = CommandSettingsModel.positionSpec(node, settingContext.role)
        showCoordinateSettingDialog(
            player,
            current?.x ?: player.location.x,
            current?.y ?: player.location.y,
            current?.z ?: player.location.z,
            semanticsX = current?.semanticsX ?: CoordinateSemantics.ABSOLUTE,
            semanticsY = current?.semanticsY ?: CoordinateSemantics.ABSOLUTE,
            semanticsZ = current?.semanticsZ ?: CoordinateSemantics.ABSOLUTE,
        ) { sx, x, sy, y, sz, z ->
            if (!updateSettingNode(player, settingContext) {
                    val kept = CommandSettingsModel.positionSpec(it, settingContext.role)
                        ?.takeIf { kept -> kept.kind == PositionKind.COORDINATES }
                        ?: PositionSpec(PositionKind.COORDINATES)
                    CommandSettingsModel.setPositionSpec(
                        it,
                        settingContext.role,
                        kept.copy(x = x, y = y, z = z, semanticsX = sx, semanticsY = sy, semanticsZ = sz),
                    )
                }) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            } else {
                val ownerId = ownerIdFor(player)
                state.lowerMode = if (settingChildOpen(ownerId) || state.settingRoute.size > 1) {
                    GestureLowerMode.SETTING_CHOICES
                } else {
                    GestureLowerMode.SETTINGS
                }
                if (!settingChildOpen(ownerId)) updateLower(player)
                null
            }
        }
    }

    /**
     * issue #29: 内包対象の付近距離を変更します。
     *
     * 位置・向きに内包する対象指定と、役割直下の対象指定を同じ入力仕様で扱い、
     * 保存先だけを isFacing で切り替えます。ダイアログには現在の距離を初期表示します。
     * 周囲系では最大距離だけを入力欄とします。
     */
    private fun openEmbeddedTargetDistanceDialog(
        player: Player,
        settingContext: CommandSettingContext,
        fieldKey: String,
        currentTarget: TargetSpec?,
        isFacing: Boolean,
        maximumOnly: Boolean = false,
    ) {
        beginSettingDistanceInput(
            player,
            currentTarget?.minimumDistance,
            currentTarget?.maximumDistance,
            maximumOnly = maximumOnly,
        ) { minimum, maximum ->
            val facingRole = settingContext.role
            if (isFacing && facingRole == null) {
                return@beginSettingDistanceInput KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                )
            }
            if (!updateSettingNode(player, settingContext) {
                    if (isFacing) {
                        val latest = CommandSettingsModel.facingSpec(it, requireNotNull(facingRole))
                            ?.takeIf { base -> base.kind == FacingKind.TARGET }
                            ?: FacingSpec(FacingKind.TARGET)
                        val target = latest.target ?: TargetSpec(TargetKind.NEAREST_PLAYER)
                        CommandSettingsModel.setFacingSpec(
                            it,
                            latest.copy(
                                kind = FacingKind.TARGET,
                                // 距離の手入力で来歴を消し、等価値のプリセット誤判定を防ぎます。
                                target = SettingPresets.withoutPresetConversion(
                                    target.copy(minimumDistance = minimum, maximumDistance = maximum),
                                ),
                            ),
                            requireNotNull(facingRole),
                        )
                    } else {
                        val latest = CommandSettingsModel.positionSpec(it, settingContext.role)
                            ?.takeIf { base -> base.kind == PositionKind.TARGET }
                            ?: PositionSpec(PositionKind.TARGET)
                        val target = latest.target ?: TargetSpec(TargetKind.NEAREST_PLAYER)
                        CommandSettingsModel.setPositionSpec(
                            it,
                            settingContext.role,
                            latest.copy(
                                kind = PositionKind.TARGET,
                                // 距離の手入力で来歴を消し、等価値のプリセット誤判定を防ぎます。
                                target = SettingPresets.withoutPresetConversion(
                                    target.copy(minimumDistance = minimum, maximumDistance = maximum),
                                ),
                            ),
                        )
                    }
                }) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
            } else null
        }
    }

    /** 回転設定用の入力欄をyaw/pitchへ分割します。 */
    private fun showRotationSettingDialog(
        player: Player,
        yaw: Float,
        pitch: Float,
        semanticsYaw: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
        semanticsPitch: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
        onSubmit: (CoordinateSemantics, Float, CoordinateSemantics, Float) -> String?,
    ) {
        showInputDialog(
            player = player,
            body = CommandDialogSpecs.rotationBody(player),
            inputs = CommandDialogSpecs.rotationInputs(
                player,
                CommandDialogSpecs.formatAngleComponent(semanticsYaw, yaw),
                CommandDialogSpecs.formatAngleComponent(semanticsPitch, pitch),
            ),
        ) { response ->
            val rawValues = listOf("yaw", "pitch").associateWith { key -> response.textValue(key).trim() }
            val validationError = rawValues.entries
                .mapNotNull { (key, raw) -> CommandDialogSpecs.rotationSpec(key).validateInput(raw) }
                .firstOrNull()
            if (validationError != null) return@showInputDialog KcI18n.text(player, validationError)
            val (syaw, yawValue) = CommandDialogSpecs.parseAngleComponent(rawValues.getValue("yaw"))
                ?: return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_INVALID)
            val (spitch, pitchValue) = CommandDialogSpecs.parseAngleComponent(rawValues.getValue("pitch"))
                ?: return@showInputDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_INVALID)
            onSubmit(syaw, yawValue, spitch, pitchValue)
        }
    }

    /** 条件詳細の子設定IDを保存先のparamsキーへ変換します。 */
    private fun specSaveKey(encoded: String): String = when (encoded) {
        "condition-variable" -> "variable"
        "condition-value" -> "value"
        "condition-left" -> "leftName"
        "condition-right" -> "right"
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
            refreshSettingScreen(player, openChild)
        }

        /**
         * 一時変数名の二段階入力を集約します。
         *
         * 対象・位置・向きの一時変数入力は「初回＝方式選択、再クリック＝変数名入力」の
         * 同一契約です。分散実装すると選択記憶・検証・再表示の扱いがずれます。
         * 読取は現在の変数名、保存は方式選択と変数名確定の2動作だけを受けます。
         */
        fun editTemporaryVariableName(
            encodedChoice: String,
            currentName: String?,
            isTemporaryMode: Boolean,
            saveMode: () -> Boolean,
            saveName: (String) -> Boolean,
        ) {
            val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encodedChoice)
            rememberSettingNode(encodedChoice)
            if (!wasSelected || !isTemporaryMode) {
                if (!saveMode()) return
                showSettingScreen()
                return
            }
            beginSettingInput(
                player,
                CommandDialogSpecs.variableName,
                currentName.orEmpty(),
            ) { raw ->
                if (!saveName(raw.trim())) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                } else {
                    showSettingScreen()
                    null
                }
            }
        }

        /**
         * 対象の簡略分類を保存し、必要なら距離・種類などの詳細へ進みます。
         * 移動先の「他のエンティティ」も同じ処理を使うため、インライン選択と
         * 通常の対象設定画面で細分類の保存規則が分岐しないようにします。
         */
        fun handleTargetCategory(categoryValue: String) {
            // 対象画面の分類は「一時変数を参照」だけです。プレイヤー系・
            // エンティティ系はプリセットと詳細設定へ集約しました。
            if (runCatching { TargetCategory.valueOf(categoryValue) }.getOrNull() != TargetCategory.TEMPORARY) return
            val role = settingContext.role
            // issue #34-1: 内包役割では位置・向き仕様の内部対象を読み書きします。
            // 通常の targetSpec へ流すと別フィールド（ノード本体の対象）を
            // 書き換えるため、ここで分岐します。
            val embedded = CommandSettingsModel.isEmbeddedTargetRole(role)
            val current = if (embedded) {
                CommandSettingsModel.embeddedTargetSpec(node, role)
            } else {
                CommandSettingsModel.targetSpec(node, role)
            }
            // 一時変数は通常の選択肢と同じく、1回目は方式の選択だけにします。
            // 値未入力のTargetSpecを設定済み扱いにしないため、明示的に
            // configuredFieldsを空集合へ指定します。
            editTemporaryVariableName(
                encodedChoice = "target:$categoryValue",
                currentName = current?.takeIf { it.kind == TargetKind.TEMPORARY }?.tempName,
                isTemporaryMode = current?.kind == TargetKind.TEMPORARY,
                saveMode = {
                    updateSettingNode(
                        player,
                        settingContext.copy(role = role),
                        configuredFields = emptySet(),
                    ) {
                        if (embedded) {
                            CommandSettingsModel.setEmbeddedTargetSpec(it, role, TargetSpec(TargetKind.TEMPORARY))
                        } else {
                            CommandSettingsModel.setTargetSpec(it, role, TargetSpec(TargetKind.TEMPORARY))
                        }
                    }
                },
                saveName = { name ->
                    updateSettingNode(player, settingContext.copy(role = role)) {
                        if (embedded) {
                            CommandSettingsModel.setEmbeddedTargetSpec(
                                it,
                                role,
                                TargetSpec(TargetKind.TEMPORARY, tempName = name),
                            )
                        } else {
                            CommandSettingsModel.setTargetSpec(
                                it,
                                role,
                                TargetSpec(TargetKind.TEMPORARY, tempName = name),
                            )
                        }
                    }
                },
            )
            return
        }

        when (screen) {
            GestureSettingScreen.TARGET -> {
                // issue #29: 対象プリセットは1クリックで正規仕様へ設定します。
                // 付近系は選択済みの再クリックで距離を変更します。
                // 内包役割では位置・向き仕様の対象部だけを書き換えます。
                val embeddedTarget = settingContext.role?.let {
                    CommandSettingsModel.isEmbeddedTargetRole(it)
                } == true
                fun readBranchTarget(target: CommandNode): TargetSpec? = if (embeddedTarget) {
                    CommandSettingsModel.embeddedTargetSpec(target, settingContext.role)
                } else {
                    CommandSettingsModel.targetSpec(target, settingContext.role)
                }
                fun writeBranchTarget(target: CommandNode, spec: TargetSpec) {
                    val cleared = SettingPresets.withoutPresetConversion(spec)
                    if (embeddedTarget) {
                        CommandSettingsModel.setEmbeddedTargetSpec(target, settingContext.role, cleared)
                    } else {
                        CommandSettingsModel.setTargetSpec(target, settingContext.role, cleared)
                    }
                }
                if (group == "target-preset") {
                    val preset = runCatching { SettingPresets.TargetPreset.valueOf(value) }.getOrNull() ?: return
                    val current = readBranchTarget(node)
                    val matched = SettingPresets.matchTargetPreset(current) == preset
                    rememberSettingNode(encoded)
                    // 内包役割では選択済みの再クリックで絞込子画面へ進みます。
                    if (embeddedTarget && matched) {
                        pushSettingFrame(
                            player,
                            GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET_FILTERS),
                            encoded,
                        )
                        return
                    }
                    if (matched && (preset == SettingPresets.TargetPreset.NEARBY_PLAYERS ||
                            preset == SettingPresets.TargetPreset.NEARBY_ENTITIES)
                    ) {
                        beginSettingDistanceInput(
                            player,
                            current?.minimumDistance,
                            current?.maximumDistance,
                            maximumOnly = true,
                        ) { minimum, maximum ->
                            if (!updateSettingNode(player, settingContext) {
                                    val latest = readBranchTarget(it)
                                        ?: SettingPresets.targetSpecFor(preset)
                                    writeBranchTarget(
                                        it,
                                        latest.copy(minimumDistance = minimum, maximumDistance = maximum),
                                    )
                                }) {
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                            } else null
                        }
                        return
                    }
                    if (updateSettingNode(player, settingContext) {
                            writeBranchTarget(it, SettingPresets.targetSpecFor(preset))
                        }) {
                        clearDetailVisited(fieldKey, settingContext.role)
                        showSettingScreen()
                    }
                    return
                }
                // 詳細設定は初回クリックで選択のみ行い、選択済みの再クリックで
                // 現行の絞込子画面へ進みます。未設定時は保存せず留まります。
                // プリセットからの引き継ぎは行わず、現在の保存値をそのまま
                // 初期表示します。絞込画面の保存で来歴は消えます。
                if (group == "target-detail") {
                    val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                    val current = readBranchTarget(node)
                    rememberSettingNode(encoded)
                    if (!wasSelected) {
                        showSettingScreen()
                        return
                    }
                    if (current == null) {
                        showSettingScreen()
                        return
                    }
                    pushSettingFrame(
                        player,
                        GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET_FILTERS),
                        encoded,
                    )
                    return
                }
                if (group != "target") return
                handleTargetCategory(value)
            }
            GestureSettingScreen.TARGET_FILTERS -> {
                val role = settingContext.role
                // issue #29: 位置・向きの役割で開いた場合は内包対象を編集します。
                // これらの役割で通常対象画面へ進む経路はないため、役割で判別します。
                // 保存形式は変えず、表示・保存・検証・実行・出力は既存経路のままです。
                val embedded = CommandSettingsModel.isEmbeddedTargetRole(role)
                fun readFilterTarget(target: CommandNode): TargetSpec? = if (embedded) {
                    CommandSettingsModel.embeddedTargetSpec(target, role)
                } else {
                    CommandSettingsModel.targetSpec(target, role)
                }
                fun writeFilterTarget(target: CommandNode, spec: TargetSpec) {
                    // 詳細画面の保存で来歴を消し、手入力の等価値をプリセット扱いにしません。
                    // 変換保存だけが来歴を立てる入口です。
                    val cleared = SettingPresets.withoutPresetConversion(spec)
                    if (embedded) {
                        CommandSettingsModel.setEmbeddedTargetSpec(target, role, cleared)
                    } else {
                        CommandSettingsModel.setTargetSpec(target, role, cleared)
                    }
                }
                val current = readFilterTarget(node) ?: TargetSpec(TargetKind.NEAREST_ENTITY)
                if (group == "kind") {
                    val category = CommandSettingsModel.targetCategory(current.kind)
                    val selectedKind = CommandSettingsModel.targetKinds(category)
                        .firstOrNull { it.name == value }
                        ?: return
                    // issue #35-3: 固定実体（旧FIXED_ENTITY）は撤去しました。
                    // 視線取得の保存導線も持ちません。特定個体の参照は
                    // 一時変数（TEMPORARY）で行います。
                    if (updateSettingNode(player, settingContext) {
                            writeFilterTarget(
                                it,
                                current.copy(
                                    kind = selectedKind,
                                    entityType = if (selectedKind in setOf(TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES)) current.entityType else null,
                                ),
                            )
                        }) showSettingScreen()
                    return
                }
                if (group != "filter") return
                // issue #4: 明示領域の編集です。基準・範囲・距離・基準別参数・解除を
                // `filter:region-` 派生IDで受け、既存の絞り込み保存経路へ載せます。
                // 空になった領域は保持せず null へ戻します。
                if (value == "region" || value.startsWith("region-")) {
                    // 行自体のクリックは子選択肢へ委ね、保存しません。
                    if (value == "region") {
                        showSettingScreen()
                        return
                    }
                    fun currentRegion(): RegionSpec =
                        readFilterTarget(node)?.region?.takeIf { it.hasAnySetting() } ?: RegionSpec()
                    fun writeRegion(target: CommandNode, region: RegionSpec?) {
                        val latest = readFilterTarget(target) ?: TargetSpec(current.kind)
                        writeFilterTarget(target, latest.copy(region = region?.takeIf { it.hasAnySetting() }))
                    }
                    if (value.startsWith("region-anchor:")) {
                        val kind = runCatching {
                            RegionAnchorKind.valueOf(value.removePrefix("region-anchor:"))
                        }.getOrNull() ?: return
                        if (updateSettingNode(player, settingContext) {
                                val base = readFilterTarget(it)
                                    ?.region?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                                // 基準切替えでは範囲・距離を引き継ぎ、基準固有参数だけを初期化します。
                                // 空になった領域は保持しません。
                                writeRegion(it, base.copy(anchor = RegionAnchorSpec(kind)))
                            }) showSettingScreen()
                        return
                    }
                    if (value == "region-range") {
                        val base = currentRegion()
                        beginSettingRangeInput(player, base.dx, base.dy, base.dz) { dx, dy, dz ->
                            if (!updateSettingNode(player, settingContext) {
                                    val latestRegion = readFilterTarget(it)
                                        ?.region?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                                    val latest = readFilterTarget(it) ?: TargetSpec(current.kind)
                                    writeFilterTarget(it, latest.copy(region = latestRegion.copy(dx = dx, dy = dy, dz = dz)))
                                }) {
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                            } else null
                        }
                        return
                    }
                    if (value == "region-distance") {
                        val base = currentRegion()
                        beginSettingDistanceInput(
                            player,
                            base.minimumDistance,
                            base.maximumDistance,
                            maximumOnly = false,
                        ) { minimum, maximum ->
                            if (!updateSettingNode(player, settingContext) {
                                    val latestRegion = readFilterTarget(it)
                                        ?.region?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                                    val latest = readFilterTarget(it) ?: TargetSpec(current.kind)
                                    writeFilterTarget(
                                        it,
                                        latest.copy(
                                            region = latestRegion.copy(
                                                minimumDistance = minimum,
                                                maximumDistance = maximum,
                                            ),
                                        ),
                                    )
                                }) {
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                            } else null
                        }
                        return
                    }
                    if (value == "region-position") {
                        val position = currentRegion().anchor.position
                        showCoordinateSettingDialog(
                            player,
                            position?.x ?: player.location.x,
                            position?.y ?: player.location.y,
                            position?.z ?: player.location.z,
                            semanticsX = position?.semanticsX ?: CoordinateSemantics.ABSOLUTE,
                            semanticsY = position?.semanticsY ?: CoordinateSemantics.ABSOLUTE,
                            semanticsZ = position?.semanticsZ ?: CoordinateSemantics.ABSOLUTE,
                        ) { sx, x, sy, y, sz, z ->
                            if (!updateSettingNode(player, settingContext) {
                                    val latestRegion = readFilterTarget(it)
                                        ?.region?.takeIf { region -> region.hasAnySetting() } ?: RegionSpec()
                                    val latest = readFilterTarget(it) ?: TargetSpec(current.kind)
                                    writeFilterTarget(
                                        it,
                                        latest.copy(
                                            region = latestRegion.copy(
                                                anchor = latestRegion.anchor.copy(
                                                    kind = RegionAnchorKind.FIXED,
                                                    position = PositionSpec(
                                                        PositionKind.COORDINATES,
                                                        x, y, z,
                                                        semanticsX = sx, semanticsY = sy, semanticsZ = sz,
                                                    ),
                                                ),
                                            ),
                                        ),
                                    )
                                }) {
                                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                            } else null
                        }
                        return
                    }
                    if (value == "region-temp") {
                        openRegionTempNameDialog(player, settingContext, currentRegion().anchor.tempName) { target, name ->
                            val latest = readFilterTarget(target) ?: TargetSpec(current.kind)
                            val base = latest.region?.takeIf { region -> region.hasAnySetting() }
                                ?: RegionSpec()
                            writeFilterTarget(
                                target,
                                latest.copy(
                                    region = base.copy(
                                        anchor = base.anchor.copy(
                                            kind = RegionAnchorKind.TEMPORARY,
                                            tempName = name,
                                        ),
                                    ),
                                ),
                            )
                        }
                        return
                    }
                    if (value.startsWith("region-target-preset:")) {
                        val preset = runCatching {
                            SettingPresets.TargetPreset.valueOf(value.removePrefix("region-target-preset:"))
                        }.getOrNull() ?: return
                        if (updateSettingNode(player, settingContext) {
                                val latest = readFilterTarget(it) ?: TargetSpec(current.kind)
                                val base = latest.region?.takeIf { region -> region.hasAnySetting() }
                                    ?: RegionSpec()
                                writeFilterTarget(
                                    it,
                                    latest.copy(
                                        region = base.copy(
                                            anchor = base.anchor.copy(
                                                kind = RegionAnchorKind.TARGET_ENTITY,
                                                target = SettingPresets.targetSpecFor(preset),
                                            ),
                                        ),
                                    ),
                                )
                            }) showSettingScreen()
                        return
                    }
                    if (value == "region-clear") {
                        if (updateSettingNode(player, settingContext) {
                                writeRegion(it, null)
                            }) showSettingScreen()
                        return
                    }
                    return
                }
                when (value) {
                    "sort" -> updateSettingNode(player, settingContext) {
                        val next = TargetSort.entries[(current.sort.ordinal + 1) % TargetSort.entries.size]
                        writeFilterTarget(it, current.copy(sort = next))
                    }
                    "gameMode" -> updateSettingNode(player, settingContext) {
                        val modes = listOf(null, "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")
                        val next = modes[(modes.indexOf(current.gameMode) + 1).coerceAtLeast(0) % modes.size]
                        writeFilterTarget(it, current.copy(gameMode = next))
                    }
                    "entityType", "distance", "range", "limit", "tag", "name" -> {
                        if (value == "distance") {
                            beginSettingDistanceInput(
                                player,
                                current.minimumDistance,
                                current.maximumDistance,
                                // 周囲系の詳細設定項目は最大距離だけとします。
                                maximumOnly = CommandSettingsModel.isNearbyKind(current.kind),
                            ) { minimum, maximum ->
                                if (!updateSettingNode(player, settingContext) {
                                        val latest = readFilterTarget(it)
                                            ?: TargetSpec(current.kind)
                                        writeFilterTarget(
                                            it,
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
                                        val latest = readFilterTarget(it)
                                            ?: TargetSpec(current.kind)
                                        writeFilterTarget(it, latest.copy(dx = dx, dy = dy, dz = dz))
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
                                val latest = readFilterTarget(it)
                                    ?: TargetSpec(current.kind)
                                val updated = when (value) {
                                    "entityType" -> latest.copy(entityType = parsedText)
                                    "limit" -> latest.copy(limit = parsedLimit)
                                    "tag" -> latest.copy(tag = parsedText)
                                    else -> latest.copy(name = parsedText)
                                }
                                writeFilterTarget(it, updated)
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
                // issue #29: 位置プリセットは1クリックで正規仕様へ設定します。
                // 付近系・座標系は選択済みの再クリックで詳細を開きます。
                // 移動先の最寄り・付近は対象指定へ保存します（二重保持の正規化は
                // 共通setterが担います）。表示・導線は位置画面と完全同一です。
                // 付近系プリセットは複数になり得るため単一限定では拒否します。
                // 最寄り等の静的0..1は許可し、表示側・Inventory側と同じ判定にします。
                if (group == "position-preset") {
                    val preset = runCatching { SettingPresets.PositionPreset.valueOf(value) }.getOrNull() ?: return
                    val isDestinationEntityPreset = settingContext.role == CommandSettingRole.DESTINATION &&
                        (preset == SettingPresets.PositionPreset.NEAREST_PLAYER ||
                            preset == SettingPresets.PositionPreset.NEARBY_PLAYERS)
                    if (!isDestinationEntityPreset &&
                        preset == SettingPresets.PositionPreset.NEARBY_PLAYERS &&
                        !CommandSettingAvailabilityPolicy.isEmbeddedTargetPositionEnabled(settingContext.role)
                    ) {
                        return
                    }
                    if (!isDestinationEntityPreset && !CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(
                            node,
                            settingContext.role,
                            presetPositionKind(preset),
                        )
                    ) {
                        return
                    }
                    val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                    val interactionMode = lowerPanel.settingChoiceInteractionMode(state, player, encoded)
                    rememberSettingNode(encoded, interactionMode)
                    if (isDestinationEntityPreset) {
                        val targetPreset = if (preset == SettingPresets.PositionPreset.NEARBY_PLAYERS) {
                            SettingPresets.TargetPreset.NEARBY_PLAYERS
                        } else {
                            SettingPresets.TargetPreset.NEAREST_PLAYER
                        }
                        val currentTarget = CommandSettingsModel.targetSpec(node, settingContext.role)
                        val matchedTarget = SettingPresets.matchTargetPreset(currentTarget) == targetPreset
                        if ((matchedTarget || wasSelected) && targetPreset == SettingPresets.TargetPreset.NEARBY_PLAYERS) {
                            beginSettingDistanceInput(
                                player,
                                currentTarget?.minimumDistance,
                                currentTarget?.maximumDistance,
                                maximumOnly = true,
                            ) { minimum, maximum ->
                                if (!updateSettingNode(player, settingContext) {
                                        val latest = CommandSettingsModel.targetSpec(it, settingContext.role)
                                            ?: SettingPresets.targetSpecFor(targetPreset)
                                        CommandSettingsModel.setTargetSpec(
                                            it,
                                            settingContext.role,
                                            SettingPresets.withoutPresetConversion(
                                                latest.copy(minimumDistance = minimum, maximumDistance = maximum),
                                            ),
                                        )
                                    }) {
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                                } else null
                            }
                            return
                        }
                        if (updateSettingNode(player, settingContext) {
                                CommandSettingsModel.setTargetSpec(
                                    it,
                                    settingContext.role,
                                    SettingPresets.targetSpecFor(targetPreset),
                                )
                            }) showSettingScreen()
                        return
                    }
                    val current = CommandSettingsModel.positionSpec(node, settingContext.role)
                    val matched = SettingPresets.matchPositionPreset(current) == preset
                    if ((matched || wasSelected) && preset == SettingPresets.PositionPreset.NEARBY_PLAYERS) {
                        openEmbeddedTargetDistanceDialog(
                            player,
                            settingContext,
                            fieldKey,
                            current?.target,
                            isFacing = false,
                            maximumOnly = true,
                        )
                        return
                    }
                    if ((matched || wasSelected) && preset == SettingPresets.PositionPreset.COORDINATES) {
                        openPositionCoordinateDialog(player, settingContext, fieldKey)
                        return
                    }
                    if (settingSelectionAction(wasSelected, interactionMode) ==
                        GestureSettingSelectionAction.ENTER_CHILD
                    ) {
                        pushSettingFrame(
                            player,
                            GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.POSITION),
                            encoded,
                        )
                        return
                    }
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setPositionSpec(
                                it,
                                settingContext.role,
                                SettingPresets.positionSpecFor(preset),
                            )
                        }) {
                        clearDetailVisited(fieldKey, settingContext.role)
                        showSettingScreen()
                    }
                    return
                }
                // エンティティ由来位置の導線です。遷移条件は回数ではなく選択状態での
                // 再クリックです。未選択時の初回は実体参照を選択して留まり、
                // 選択状態でのクリックで共用の対象子画面へ進みます。
                // 単一限定でも導線自体は開き、複数になり得る候補だけを子画面で無効化します。
                // 移動先は対象指定（destinationTargetSpec）へ保存します。
                if (group == "position-entity") {
                    val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                    rememberSettingNode(encoded)
                    if (settingContext.role == CommandSettingRole.DESTINATION) {
                        val currentTarget = CommandSettingsModel.targetSpec(node, CommandSettingRole.DESTINATION)
                        if (SettingSelectionEngine.decidePositionEntity(wasSelected, currentTarget != null) is SettingSelectionEngine.Next.EnterChild) {
                            pushSettingFrame(
                                player,
                                GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET),
                                encoded,
                            )
                            return
                        }
                        if (currentTarget == null) {
                            if (!updateSettingNode(
                                    player,
                                    settingContext,
                                    configuredFields = emptySet(),
                                ) {
                                    CommandSettingsModel.setTargetSpec(
                                        it,
                                        CommandSettingRole.DESTINATION,
                                        TargetSpec(TargetKind.NEAREST_PLAYER),
                                    )
                                }) return
                        }
                        showSettingScreen()
                        return
                    }
                    val current = CommandSettingsModel.positionSpec(node, settingContext.role)
                    if (SettingSelectionEngine.decidePositionEntity(wasSelected, current?.kind == PositionKind.TARGET) is SettingSelectionEngine.Next.EnterChild) {
                        pushSettingFrame(
                            player,
                            GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET),
                            encoded,
                        )
                        return
                    }
                    if (current?.kind != PositionKind.TARGET) {
                        if (!updateSettingNode(
                                player,
                                settingContext,
                                configuredFields = emptySet(),
                            ) {
                                CommandSettingsModel.setPositionSpec(
                                    it,
                                    settingContext.role,
                                    PositionSpec(
                                        PositionKind.TARGET,
                                        target = TargetSpec(TargetKind.NEAREST_PLAYER),
                                    ),
                                )
                            }) return
                    }
                    showSettingScreen()
                    return
                }
                // issue #29: 内包対象プリセットは位置仕様の対象部だけを書き換えます。
                // 単一限定では複数になり得る候補を拒否し、静的0..1だけを許可します。
                // 選択済みの再クリックで詳細条件（TARGET_FILTERS）へ進みます。
                // 距離も詳細画面の項目から変更し、保存形式は変えません。
                if (group == "position-entity-preset") {
                    val preset = runCatching { SettingPresets.TargetPreset.valueOf(value) }.getOrNull() ?: return
                    if (MultiPositionPolicy.multiplicityFor(settingContext.role) == PositionMultiplicity.SINGLE &&
                        preset != SettingPresets.TargetPreset.NEAREST_PLAYER
                    ) {
                        return
                    }
                    val current = CommandSettingsModel.positionSpec(node, settingContext.role)
                    val matched = SettingPresets.matchTargetPreset(current?.target) == preset
                    rememberSettingNode(encoded)
                    if (matched) {
                        pushSettingFrame(
                            player,
                            GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET_FILTERS),
                            encoded,
                        )
                        return
                    }
                    if (!updateSettingNode(player, settingContext) {
                            val base = CommandSettingsModel.positionSpec(it, settingContext.role)
                                ?.takeIf { base -> base.kind == PositionKind.TARGET }
                                ?: PositionSpec(PositionKind.TARGET)
                            CommandSettingsModel.setPositionSpec(
                                it,
                                settingContext.role,
                                base.copy(kind = PositionKind.TARGET, target = SettingPresets.targetSpecFor(preset)),
                            )
                        }) return
                    clearDetailVisited(fieldKey, settingContext.role)
                    if (state.settingRoute.size > 1) popSettingFrame(player) else showSettingScreen()
                    return
                }
                // issue #29: スポーン種別の2択です。選択後は子画面内で表示だけを
                // 切り替え、画面を閉じません。子画面を閉じる導線は戻る操作です。
                if (group == "myworld-spawn") {
                    val spawn = runCatching { MyworldSpawnKind.valueOf(value) }.getOrNull() ?: return
                    rememberSettingNode(encoded)
                    if (!updateSettingNode(player, settingContext) {
                            val current = CommandSettingsModel.positionSpec(it, settingContext.role)
                                ?.takeIf { base -> base.kind == PositionKind.MYWORLD_SPAWN }
                                ?: PositionSpec(PositionKind.MYWORLD_SPAWN)
                            CommandSettingsModel.setPositionSpec(
                                it,
                                settingContext.role,
                                current.copy(kind = PositionKind.MYWORLD_SPAWN, myworldSpawn = spawn),
                            )
                        }) return
                    showSettingScreen()
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
                    // 一時変数は1回目を方式選択、2回目を変数名入力とします。
                    // 保存するSpecは入力前の未完成状態なので、設定済み項目として
                    // マークしないことを明示します。
                    editTemporaryVariableName(
                        encodedChoice = encoded,
                        currentName = current?.takeIf { it.kind == PositionKind.TEMPORARY }?.tempName,
                        isTemporaryMode = current?.kind == PositionKind.TEMPORARY,
                        saveMode = {
                            updateSettingNode(
                                player,
                                settingContext,
                                configuredFields = emptySet(),
                            ) {
                                CommandSettingsModel.setPositionSpec(
                                    it,
                                    settingContext.role,
                                    PositionSpec(PositionKind.TEMPORARY),
                                )
                            }
                        },
                        saveName = { name ->
                            updateSettingNode(player, settingContext) {
                                CommandSettingsModel.setPositionSpec(
                                    it,
                                    settingContext.role,
                                    PositionSpec(PositionKind.TEMPORARY, tempName = name),
                                )
                            }
                        },
                    )
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
                        semanticsX = current?.semanticsX ?: CoordinateSemantics.ABSOLUTE,
                        semanticsY = current?.semanticsY ?: CoordinateSemantics.ABSOLUTE,
                        semanticsZ = current?.semanticsZ ?: CoordinateSemantics.ABSOLUTE,
                    ) { sx, x, sy, y, sz, z ->
                        if (!updateSettingNode(player, settingContext) {
                            // Dialog記法の意味付けを保存します。向き成分は本欄で扱わないため保持します。
                            val kept = CommandSettingsModel.positionSpec(it, settingContext.role)
                                ?.takeIf { kept -> kept.kind == kind }
                                ?: PositionSpec(kind)
                            CommandSettingsModel.setPositionSpec(
                                it,
                                settingContext.role,
                                kept.copy(x = x, y = y, z = z, semanticsX = sx, semanticsY = sy, semanticsZ = sz),
                            )
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
                // issue #29: 向きプリセットは1クリックで正規仕様へ設定します。
                if (group == "facing-preset") {
                    val preset = runCatching { SettingPresets.FacingPreset.valueOf(value) }.getOrNull() ?: return
                    val facingRole = settingContext.role ?: return
                    val current = CommandSettingsModel.facingSpec(node, facingRole)
                    val matched = SettingPresets.matchFacingPreset(current) == preset
                    val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                    val interactionMode = lowerPanel.settingChoiceInteractionMode(state, player, encoded)
                    rememberSettingNode(encoded, interactionMode)
                    if ((matched || wasSelected) && preset == SettingPresets.FacingPreset.COORDINATES) {
                        showCoordinateSettingDialog(
                            player,
                            current?.x ?: player.location.x,
                            current?.y ?: player.location.y,
                            current?.z ?: player.location.z,
                            // 注視点は絶対固定のため、記法を受け付けません。
                            allowNotation = false,
                        ) { _, x, _, y, _, z ->
                            if (!updateSettingNode(player, settingContext) {
                                    CommandSettingsModel.setFacingSpec(
                                        it,
                                        FacingSpec(FacingKind.COORDINATES, x = x, y = y, z = z),
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
                    if ((matched || wasSelected) && preset == SettingPresets.FacingPreset.NUMERIC) {
                        showRotationSettingDialog(
                            player,
                            current?.yaw ?: player.location.yaw,
                            current?.pitch ?: player.location.pitch,
                        ) { syaw, yaw, spitch, pitch ->
                            if (!updateSettingNode(player, settingContext) {
                                    CommandSettingsModel.setFacingSpec(
                                        it,
                                        FacingSpec(
                                            FacingKind.ROTATION,
                                            yaw = yaw,
                                            pitch = pitch,
                                            semanticsYaw = syaw,
                                            semanticsPitch = spitch,
                                        ),
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
                    if (settingSelectionAction(wasSelected, interactionMode) ==
                        GestureSettingSelectionAction.ENTER_CHILD
                    ) {
                        pushSettingFrame(
                            player,
                            GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.FACING),
                            encoded,
                        )
                        return
                    }
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setFacingSpec(
                                it,
                                SettingPresets.facingSpecFor(preset),
                                facingRole,
                            )
                        }) {
                        clearDetailVisited(fieldKey, settingContext.role)
                        showSettingScreen()
                    }
                    return
                }
                // エンティティ向きの導線です。遷移条件は回数ではなく選択状態での
                // 再クリックです。未選択時の初回は注視を選択して留まり、
                // 選択状態でのクリックで共用の対象子画面へ進みます。
                if (group == "facing-entity") {
                    val facingRole = settingContext.role ?: return
                    val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                    rememberSettingNode(encoded)
                    val current = CommandSettingsModel.facingSpec(node, facingRole)
                    if (SettingSelectionEngine.decideFacingEntity(wasSelected, current?.kind) is SettingSelectionEngine.Next.EnterChild) {
                        pushSettingFrame(
                            player,
                            GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET),
                            encoded,
                        )
                        return
                    }
                    if (current?.kind != FacingKind.TARGET) {
                        if (!updateSettingNode(
                                player,
                                settingContext,
                                configuredFields = emptySet(),
                            ) {
                                CommandSettingsModel.setFacingSpec(
                                    it,
                                    FacingSpec(
                                        FacingKind.TARGET,
                                        target = TargetSpec(TargetKind.NEAREST_PLAYER),
                                    ),
                                    facingRole,
                                )
                            }) return
                    }
                    showSettingScreen()
                    return
                }
                // 向き複写の導線です。注視と同じく単一体参照へ帰着し、共用の対象子画面へ進みます。
                // 遷移条件は回数ではなく選択状態での再クリックです。未選択時の初回は
                // 複写を選択して留まり、選択状態でのクリックで対象子画面へ進みます。
                if (group == "facing-copy") {
                    val facingRole = settingContext.role ?: return
                    val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                    rememberSettingNode(encoded)
                    val current = CommandSettingsModel.facingSpec(node, facingRole)
                    if (SettingSelectionEngine.decideFacingCopy(wasSelected, current?.kind) is SettingSelectionEngine.Next.EnterChild) {
                        pushSettingFrame(
                            player,
                            GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET),
                            encoded,
                        )
                        return
                    }
                    if (current?.kind != FacingKind.COPY_ORIENTATION) {
                        if (!updateSettingNode(
                                player,
                                settingContext,
                                configuredFields = emptySet(),
                            ) {
                                CommandSettingsModel.setFacingSpec(
                                    it,
                                    FacingSpec(
                                        FacingKind.COPY_ORIENTATION,
                                        target = TargetSpec(TargetKind.NEAREST_PLAYER, limit = 1),
                                    ),
                                    facingRole,
                                )
                            }) return
                    }
                    showSettingScreen()
                    return
                }
                // issue #29: 内包対象プリセットは向き仕様の対象部だけを書き換えます。
                // 選択済みの再クリックで詳細条件（TARGET_FILTERS）へ進みます。
                // 注視・複写の種別を保持し、保存形式は変えません。
                if (group == "facing-entity-preset" || group == "facing-copy-preset") {
                    val facingRole = settingContext.role ?: return
                    val preset = runCatching { SettingPresets.TargetPreset.valueOf(value) }.getOrNull() ?: return
                    val facingKind = if (group == "facing-copy-preset") FacingKind.COPY_ORIENTATION else FacingKind.TARGET
                    val current = CommandSettingsModel.facingSpec(node, facingRole)
                        ?.takeIf { it.kind == facingKind }
                    val matched = SettingPresets.matchTargetPreset(current?.target) == preset
                    rememberSettingNode(encoded)
                    if (matched) {
                        pushSettingFrame(
                            player,
                            GestureSettingFrame(settingContext, fieldKey, GestureSettingScreen.TARGET_FILTERS),
                            encoded,
                        )
                        return
                    }
                    if (!updateSettingNode(player, settingContext) {
                            val base = CommandSettingsModel.facingSpec(it, facingRole)
                                ?.takeIf { base -> base.kind == facingKind }
                                ?: FacingSpec(facingKind)
                            CommandSettingsModel.setFacingSpec(
                                it,
                                base.copy(kind = facingKind, target = SettingPresets.targetSpecFor(preset).copy(limit = 1)),
                                facingRole,
                            )
                        }) return
                    clearDetailVisited(fieldKey, settingContext.role)
                    if (state.settingRoute.size > 1) popSettingFrame(player) else showSettingScreen()
                    return
                }
                if (group != "facing") return
                val kind = runCatching { FacingKind.valueOf(value) }.getOrNull() ?: return
                val facingRole = settingContext.role ?: return
                if (kind == FacingKind.TEMPORARY) {
                    val current = CommandSettingsModel.facingSpec(node, facingRole)
                    val encodedFacing = "facing:${kind.name}"
                    // 他の設定値と同じく、最初のクリックでは方式だけを選びます。
                    // 変数名がまだないため、configuredFieldsは空集合のままにします。
                    editTemporaryVariableName(
                        encodedChoice = encodedFacing,
                        currentName = current?.takeIf { it.kind == FacingKind.TEMPORARY }?.tempName,
                        isTemporaryMode = current?.kind == FacingKind.TEMPORARY,
                        saveMode = {
                            updateSettingNode(
                                player,
                                settingContext,
                                configuredFields = emptySet(),
                            ) {
                                CommandSettingsModel.setFacingSpec(
                                    it,
                                    FacingSpec(FacingKind.TEMPORARY),
                                    facingRole,
                                )
                            }
                        },
                        saveName = { name ->
                            updateSettingNode(player, settingContext) {
                                CommandSettingsModel.setFacingSpec(
                                    it,
                                    FacingSpec(FacingKind.TEMPORARY, tempName = name),
                                    facingRole,
                                )
                            }
                        },
                    )
                    return
                }
                if (kind == FacingKind.COORDINATES) {
                    val current = CommandSettingsModel.facingSpec(node, facingRole)
                    showCoordinateSettingDialog(
                        player,
                        current?.x ?: player.location.x,
                        current?.y ?: player.location.y,
                        current?.z ?: player.location.z,
                        // 注視点は絶対固定のため、記法を受け付けません。
                        allowNotation = false,
                    ) { _, x, _, y, _, z ->
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
                        semanticsYaw = current?.semanticsYaw ?: CoordinateSemantics.ABSOLUTE,
                        semanticsPitch = current?.semanticsPitch ?: CoordinateSemantics.ABSOLUTE,
                    ) { syaw, yaw, spitch, pitch ->
                        if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setFacingSpec(
                                it,
                                FacingSpec(kind, yaw = yaw, pitch = pitch, semanticsYaw = syaw, semanticsPitch = spitch),
                                facingRole,
                            )
                        }) return@showRotationSettingDialog KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        showSettingScreen()
                        null
                    }
                    return
                }
                if (updateSettingNode(player, settingContext) { CommandSettingsModel.setFacingSpec(it, FacingSpec(kind), facingRole) }) {
                    showSettingScreen()
                }
            }
            GestureSettingScreen.CONDITION_KIND -> {
                if (group != "condition-kind") return
                // issue #27: 判定対象ベースで保存します。単一正本の `subject` 欄だけを扱い、
                // 旧 `kind` 形式のIDは受け付けません。
                val subject = runCatching { ConditionSubject.valueOf(value) }.getOrNull() ?: return
                if (updateSettingNode(player, settingContext) { node ->
                        node.setConditionSubject(subject)
                    }) showSettingScreen()
            }
            GestureSettingScreen.CONDITION_DETAIL -> {
                // issue #5: 条件の領域編集です。対象絞り込み側と同じ対話部品
                // （範囲・距離・座標・変数名・対象プリセット）を使い、
                // 保存先だけをノードの領域指定へ振り替えます。
                if (encoded == "condition-region" || encoded.startsWith("condition-region-")) {
                    handleConditionRegion(player, settingContext, node, encoded)
                    return
                }
                when (encoded) {
                    "condition-target" -> {
                        val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                        val interactionMode = lowerPanel.settingChoiceInteractionMode(state, player, encoded)
                        rememberSettingNode(encoded, interactionMode)
                        when (settingSelectionAction(wasSelected, interactionMode)) {
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
                        val interactionMode = lowerPanel.settingChoiceInteractionMode(state, player, encoded)
                        rememberSettingNode(encoded, interactionMode)
                        when (settingSelectionAction(wasSelected, interactionMode)) {
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
                        val operators = if (node.conditionSubject() == ConditionSubject.VALUE) {
                            // #5: 型付き比較では contains・領域述語を含む9演算子から循環します。
                            // 型への適用可否は検証層で確認します。
                            listOf("==", "!=", ">", ">=", "<", "<=", "contains", "in_region", "not_in_region")
                        } else {
                            listOf("==", "!=", ">", ">=", "<", "<=")
                        }
                        if (updateSettingNode(player, settingContext) {
                                val current = operators.indexOf(it.string("operator", "==")).coerceAtLeast(0)
                                CommandSettingsModel.setParameter(it, "operator", operators[(current + 1) % operators.size])
                            }) updateLower(player)
                    }
                    // issue #5: Boolean直接条件の切替えです。有効化で演算子・右辺を
                    // 消去し、無効化で `==` へ戻します。保存形式は変えません。
                    "condition-direct" -> {
                        if (updateSettingNode(player, settingContext) {
                                if (it.string("operator").isBlank()) {
                                    CommandSettingsModel.setParameter(it, "operator", "==")
                                } else {
                                    CommandSettingsModel.setParameter(it, "operator", "")
                                    CommandSettingsModel.setParameter(it, "right", "")
                                }
                            }) updateLower(player)
                    }
                    "condition-left-scope" -> {
                        if (updateSettingNode(player, settingContext) {
                                val next = if (it.string("leftScope", VariableScope.WORLD.name) == VariableScope.WORLD.name) {
                                    VariableScope.EXECUTION.name
                                } else {
                                    VariableScope.WORLD.name
                                }
                                CommandSettingsModel.setParameter(it, "leftScope", next)
                            }) updateLower(player)
                    }
                    "condition-variable", "condition-value", "condition-block", "condition-item",
                    "condition-left", "condition-right",
                    -> {
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
                            "condition-left" -> CommandDialogSpecs.variableName
                            "condition-value" -> CommandDialogSpecs.conditionValueSpec(node)
                            "condition-right" -> CommandDialogSpecs.conditionRightSpec(node)
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
            // issue #24: 群操作は定数IDだけを受け付けます。未知値は検証層が
            // 拒否するため、ここでは何もせず画面維持にします。
            GestureSettingScreen.ENTITY_GROUP_OPERATION -> {
                if (group != "groupOperation" || EntityGroupOperation.from(value) == null) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "operation", EntityGroupOperation.from(value)!!.value)
                    }) showSettingScreen()
            }
            // issue #24: 群名は保存済み一覧からの選択と、直接入力の両方を
            // 受け付けます。直接入力は検証層の形式チェックへ委ねます。
            GestureSettingScreen.ENTITY_GROUP_NAME -> {
                if (group == "groupName" && value.isNotBlank()) {
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, "group", value)
                        }) showSettingScreen()
                    return
                }
                if (encoded != "group-name-direct") return
                val spec = CommandDialogSpecs.entityGroupNameSpec()
                beginSettingInput(player, spec, node.string("group")) { raw ->
                    if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, "group", raw)
                        }) {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                    } else null
                }
            }
            // issue #11: 建造物の回転・反転は定数IDだけを受け付けます。未知値は
            // 検証層が拒否するため、ここでは何もせず画面維持にします。
            GestureSettingScreen.STRUCTURE_ROTATION -> {
                if (group != "rotation" || StructureRotation.from(value) == null) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "rotation", StructureRotation.from(value)!!.value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.STRUCTURE_MIRROR -> {
                if (group != "mirror" || StructureMirror.from(value) == null) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "mirror", StructureMirror.from(value)!!.value)
                    }) showSettingScreen()
            }
            // issue #11: 建造物名は保存済み一覧からの選択と、直接入力の両方を
            // 受け付けます。直接入力は検証層の形式チェックへ委ねます。
            GestureSettingScreen.STRUCTURE_NAME -> {
                if (group == "structureName" && value.isNotBlank()) {
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, "structure", value)
                        }) showSettingScreen()
                    return
                }
                if (encoded != "structure-name-direct") return
                val spec = CommandDialogSpecs.structureNameSpec()
                beginSettingInput(player, spec, node.string("structure")) { raw ->
                    if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, "structure", raw)
                        }) {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                    } else null
                }
            }
            GestureSettingScreen.ENTITY_ACTION -> {
                // 旧equipはPLACE_ITEM、旧tagはENTITY_OPERATIONへ移行済みのため受け付けません。
                if (group != "action" || value !in setOf("ride", "dismount", "motion")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "action", value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.ENTITY_DATA_ROOT -> {
                if (group != "entityData") return
                // タグ・attribute・有限候補は子画面を持つ親項目であり、
                // 同一項目への連続クリックで入室します。永続選択とのOR判定は
                // 使わず、切替操作（別項目の選択）が誤入室しないようにします。
                // 真偽値・文字列・整数は葉としてその場で確定します。
                // 直前のクリック記憶は遷移専用であり、選択表示とは分離します。
                val consecutive = state.settingTreePath?.nodeIds?.lastOrNull() == encoded
                if (value == "attribute") {
                    val interactionMode = lowerPanel.settingChoiceInteractionMode(state, player, encoded)
                    rememberSettingNode(encoded, interactionMode)
                    if (settingSelectionAction(consecutive, interactionMode) !=
                        GestureSettingSelectionAction.ENTER_CHILD
                    ) {
                        showSettingScreen()
                        return
                    }
                    pushSettingFrame(
                        player,
                        GestureSettingFrame(
                            settingContext,
                            EntityDataPath.PARAM_PATH,
                            GestureSettingScreen.ENTITY_ATTRIBUTE_SELECT,
                        ),
                        encoded,
                    )
                    return
                }
                if (value == "tag") {
                    val interactionMode = lowerPanel.settingChoiceInteractionMode(state, player, encoded)
                    rememberSettingNode(encoded, interactionMode)
                    if (!updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, EntityDataPath.PARAM_PATH, EntityDataPath.TAG)
                        }) {
                        showSettingScreen()
                        return
                    }
                    if (settingSelectionAction(consecutive, interactionMode) !=
                        GestureSettingSelectionAction.ENTER_CHILD
                    ) {
                        showSettingScreen()
                        return
                    }
                    pushSettingFrame(
                        player,
                        GestureSettingFrame(
                            settingContext,
                            EntityDataPath.PARAM_PATH,
                            GestureSettingScreen.ENTITY_TAG_LIST,
                        ),
                        encoded,
                    )
                    state.tagPage = 0
                    return
                }
                if (!value.startsWith("property:")) return
                val propertyId = value.removePrefix("property:")
                val definition = EntityDataSchema.definition(propertyId) ?: return
                val previous = EntityDataPath.propertyIdOf(node.string(EntityDataPath.PARAM_PATH))
                val interactionMode = lowerPanel.settingChoiceInteractionMode(state, player, encoded)
                rememberSettingNode(encoded, interactionMode)
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, EntityDataPath.PARAM_PATH, EntityDataPath.propertyPath(propertyId))
                        // 項目切替時は値をクリアし、古い項目の値残存を防ぎます。
                        if (previous != propertyId) {
                            CommandSettingsModel.setParameter(it, EntityDataSchema.PARAM_VALUE, "")
                        }
                    }) {
                    when (definition.valueKind) {
                        // 真偽値は選択→クリックで直接切り替えます。子画面を持ちません。
                        EntityPropertyValueKind.BOOLEAN -> {
                            val current = node.string(EntityDataSchema.PARAM_VALUE).toBooleanStrictOrNull()
                            val toggled = if (previous == propertyId && current != null) (!current).toString() else "true"
                            if (updateSettingNode(player, settingContext) {
                                    CommandSettingsModel.setParameter(it, EntityDataSchema.PARAM_VALUE, toggled)
                                }) showSettingScreen()
                        }
                        EntityPropertyValueKind.ENUM -> {
                            if (settingSelectionAction(consecutive, interactionMode) !=
                                GestureSettingSelectionAction.ENTER_CHILD
                            ) {
                                showSettingScreen()
                                return
                            }
                            pushSettingFrame(
                                player,
                                GestureSettingFrame(
                                    settingContext,
                                    EntityDataPath.PARAM_PATH,
                                    GestureSettingScreen.ENTITY_PROPERTY_VALUE,
                                ),
                                encoded,
                            )
                        }
                        EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> {
                            // 保存前のnode参照では項目が古いままになり、入力仕様が
                            // 解決できず例外になるため、保存後のノードを再取得します。
                            val fresh = plugin.scripts.load(settingContext.scriptId)
                                ?.graph?.nodes?.get(settingContext.nodeId)
                                ?: return
                            beginSettingInput(
                                player,
                                requireNotNull(CommandDialogSpecs.field(fresh, EntityDataSchema.PARAM_VALUE)),
                                fresh.string(EntityDataSchema.PARAM_VALUE, ""),
                            ) { raw ->
                                val normalized = CommandDialogSpecs.normalize(EntityDataSchema.PARAM_VALUE, raw)
                                // 入力確定時にも取り直します。Dialog表示中に項目が
                                // 変わっていた場合は例外にせず保存失敗として扱います。
                                val current = plugin.scripts.load(settingContext.scriptId)
                                    ?.graph?.nodes?.get(settingContext.nodeId)
                                val spec = current?.let { CommandDialogSpecs.field(it, EntityDataSchema.PARAM_VALUE) }
                                    ?: return@beginSettingInput KcI18n.text(
                                        player,
                                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                                    )
                                val validationError = spec.validateInput(normalized)
                                if (validationError != null) {
                                    KcI18n.text(player, validationError)
                                } else if (!updateSettingNode(player, settingContext, configuredFields = setOf(EntityDataSchema.PARAM_VALUE)) {
                                        CommandSettingsModel.setParameter(it, EntityDataSchema.PARAM_VALUE, normalized)
                                    }) {
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                                } else null
                            }
                        }
                    }
                } else showSettingScreen()
            }
            GestureSettingScreen.ENTITY_ATTRIBUTE_SELECT -> {
                // 選択→変更後の値入力フローです。attribute IDを確定して値Dialogを開きます。
                if (group != "attribute" || !EntityAttributeSupport.isAttributeId(value)) return
                if (!updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, EntityDataPath.PARAM_PATH, EntityDataPath.attributePath(value))
                    }) {
                    showSettingScreen()
                    return
                }
                val fresh = plugin.scripts.load(settingContext.scriptId)
                    ?.graph?.nodes?.get(settingContext.nodeId)
                    ?: return
                beginSettingInput(
                    player,
                    requireNotNull(CommandDialogSpecs.field(fresh, EntityAttributeSupport.PARAM_VALUE)),
                    fresh.string(EntityAttributeSupport.PARAM_VALUE, "0.0"),
                ) { raw ->
                    val normalized = CommandDialogSpecs.normalize(EntityAttributeSupport.PARAM_VALUE, raw)
                    val current = plugin.scripts.load(settingContext.scriptId)
                        ?.graph?.nodes?.get(settingContext.nodeId)
                    val spec = current?.let { CommandDialogSpecs.field(it, EntityAttributeSupport.PARAM_VALUE) }
                        ?: return@beginSettingInput KcI18n.text(
                            player,
                            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                        )
                    val validationError = spec.validateInput(normalized)
                    if (validationError != null) {
                        KcI18n.text(player, validationError)
                    } else if (!updateSettingNode(player, settingContext, configuredFields = setOf(EntityAttributeSupport.PARAM_VALUE)) {
                            CommandSettingsModel.setParameter(it, EntityAttributeSupport.PARAM_VALUE, normalized)
                        }) {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                    } else null
                }
            }
            // タグ一覧は専用ドキュメントで描画し、専用要素IDで振り分けるため、
            // 汎用選択の分岐は持ちません。網羅性のために空分岐を置きます。
            GestureSettingScreen.ENTITY_TAG_LIST -> Unit
            GestureSettingScreen.ENTITY_PROPERTY_VALUE -> {
                if (group != "propertyValue") return
                val propertyId = EntityDataPath.propertyIdOf(node.string(EntityDataPath.PARAM_PATH))
                    ?: return
                val definition = EntityDataSchema.definition(propertyId)
                    ?: return
                // 有限候補のみ選択適用します。真偽値・文字列・整数は別経路で編集します。
                if (definition.valueKind != EntityPropertyValueKind.ENUM) return
                if (definition.enumChoices.none { it.id == value }) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, EntityDataSchema.PARAM_VALUE, value)
                    }) showSettingScreen()
            }
            // issue #10: 召喚時個別設定の一覧画面です。既存Entity編集と同じ
            // 操作契約で、保存先だけを召喚時個別設定へ振り替えます。
            // 真偽値は選択→クリックで直接切り替え、有限候補は行内の選択肢で
            // 確定し、文字列・整数はDialog入力へ進みます。
            GestureSettingScreen.SUMMON_DATA_ROOT -> {
                if (group == "summonData") {
                    val definition = EntityDataSchema.definition(value) ?: return
                    // 召喚種類の変更後に残った非対応項目は選択できません。
                    val summonType = EntityDataSchema.knownSummonType(node.string("summonEntity"))
                    if (EntityDataSchema.staticSupport(summonType, value) == EntityPropertySupport.STATICALLY_UNSUPPORTED) return
                    when (definition.valueKind) {
                        EntityPropertyValueKind.BOOLEAN -> {
                            val key = EntityDataSchema.summonDataKey(value)
                            val toggled = (!(node.string(key).toBooleanStrictOrNull() ?: false)).toString()
                            if (updateSettingNode(player, settingContext) {
                                    CommandSettingsModel.setParameter(it, key, toggled)
                                }) showSettingScreen()
                        }
                        EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> {
                            val key = EntityDataSchema.summonDataKey(value)
                            val fresh = plugin.scripts.load(settingContext.scriptId)
                                ?.graph?.nodes?.get(settingContext.nodeId)
                                ?: return
                            beginSettingInput(
                                player,
                                requireNotNull(CommandDialogSpecs.field(fresh, key)),
                                fresh.string(key, ""),
                            ) { raw ->
                                val normalized = CommandDialogSpecs.normalize(key, raw)
                                val current = plugin.scripts.load(settingContext.scriptId)
                                    ?.graph?.nodes?.get(settingContext.nodeId)
                                val spec = current?.let { CommandDialogSpecs.field(it, key) }
                                    ?: return@beginSettingInput KcI18n.text(
                                        player,
                                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED,
                                    )
                                val validationError = spec.validateInput(normalized)
                                if (validationError != null) {
                                    KcI18n.text(player, validationError)
                                } else if (!updateSettingNode(player, settingContext, configuredFields = setOf(key)) {
                                        CommandSettingsModel.setParameter(it, key, normalized)
                                    }) {
                                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                                } else null
                            }
                        }
                        // 有限候補は行内の選択肢で確定し、行自体のクリックでは保存しません。
                        EntityPropertyValueKind.ENUM -> showSettingScreen()
                    }
                    return
                }
                if (group == "summonDataValue") {
                    val separator = value.indexOf(':')
                    if (separator <= 0) return
                    val propertyId = value.substring(0, separator)
                    val choice = value.substring(separator + 1)
                    val definition = EntityDataSchema.definition(propertyId) ?: return
                    if (definition.valueKind != EntityPropertyValueKind.ENUM) return
                    if (definition.enumChoices.none { it.id == choice }) return
                    val summonType = EntityDataSchema.knownSummonType(node.string("summonEntity"))
                    if (EntityDataSchema.staticSupport(summonType, propertyId) == EntityPropertySupport.STATICALLY_UNSUPPORTED) return
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, EntityDataSchema.summonDataKey(propertyId), choice)
                        }) showSettingScreen()
                    return
                }
            }
            // 配置先の切替えで要求対象が変わるため、スロットは既定値へ戻して
            // 不正な組合せを残しません。額縁は内容固定のためスロット欄を隠します。
            GestureSettingScreen.PLACE_DESTINATION -> {
                val destination = PlaceItemDestination.from(value)?.takeIf { group == "placeDestination" } ?: return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "placeDestination", destination.paramValue)
                        CommandSettingsModel.setParameter(it, "slot", PlaceItemDestination.defaultSlot(destination))
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
            GestureSettingScreen.SOUND_OPERATION -> {
                if (group != "soundOperation" || value !in setOf("PLAY", "STOP")) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, SoundSettings.PARAM_OPERATION, value)
                    }) showSettingScreen()
            }
            GestureSettingScreen.SOUND_TARGET_POSITION -> when (encoded) {
                "sound-target" -> {
                    val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                    val interactionMode = lowerPanel.settingChoiceInteractionMode(state, player, encoded)
                    rememberSettingNode(encoded, interactionMode)
                    when (settingSelectionAction(wasSelected, interactionMode)) {
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
                "sound-position" -> {
                    val wasSelected = lowerPanel.isSettingChoiceSelected(state, player, encoded)
                    val interactionMode = lowerPanel.settingChoiceInteractionMode(state, player, encoded)
                    rememberSettingNode(encoded, interactionMode)
                    when (settingSelectionAction(wasSelected, interactionMode)) {
                        GestureSettingSelectionAction.ENTER_CHILD -> {
                            pushSettingFrame(
                                player,
                                GestureSettingFrame(
                                    settingContext.copy(role = CommandSettingRole.SOUND_POSITION),
                                    fieldKey,
                                    GestureSettingScreen.POSITION,
                                ),
                                encoded,
                            )
                        }
                        GestureSettingSelectionAction.STAY_ON_FRAME -> showSettingScreen()
                    }
                }
            }
            GestureSettingScreen.PARTICLE_GEOMETRY -> when {
                group == "shape" -> {
                    val shape = runCatching { ParticleShape.valueOf(value) }.getOrNull()
                        ?.takeIf { it != ParticleShape.POINT_SCATTER } ?: return
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, ParticleGeometry.PARAM_SHAPE, shape.name)
                        }) showSettingScreen()
                }
                encoded == "wireframe:toggle" -> {
                    if (updateSettingNode(player, settingContext) {
                            val current = it.params[ParticleGeometry.PARAM_WIREFRAME]?.toBooleanStrictOrNull() ?: true
                            CommandSettingsModel.setParameter(it, ParticleGeometry.PARAM_WIREFRAME, (!current).toString())
                        }) showSettingScreen()
                }
                encoded == "geometry-numbers" -> {
                    showGeometryNumbersDialog(player, settingContext, node)
                }
            }
            GestureSettingScreen.VARIABLE_TYPE -> {
                if (group != "type") return
                if (node.type == CommandType.TEMP_SET) {
                    val type = ValueType.parse(value) ?: return
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.changeTemporaryType(it, type)
                        }) showSettingScreen()
                } else {
                    val type = runCatching { ValueType.valueOf(value) }.getOrNull() ?: return
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
            // #22: ENTITY scope は事前DEFINEなしの別契約ですが、選択肢に出します。
            // 通常の対象形式と文字列の変数名で操作できるようにします。
            GestureSettingScreen.VARIABLE_SCOPE -> {
                if (group != "variableScope") return
                val scope = runCatching { VariableScope.valueOf(value) }.getOrNull() ?: return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "scope", scope.name)
                    }) showSettingScreen()
            }
            // #2: WORLD scope の変数名は定義済み候補から選択します。候補外の名前は
            // 直接入力へ進み、DEFINE 操作で新規定義します。
            GestureSettingScreen.VARIABLE_NAME -> when {
                group == "variableName" -> {
                    if (updateSettingNode(player, settingContext) {
                            CommandSettingsModel.setParameter(it, "name", value)
                        }) showSettingScreen()
                }
                encoded == "variable-name-direct" -> {
                    beginSettingInput(
                        player,
                        CommandDialogSpecs.variableName,
                        node.string("name"),
                    ) { raw ->
                        if (!updateSettingNode(player, settingContext) {
                                CommandSettingsModel.setParameter(it, "name", raw)
                            }) {
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)
                        } else null
                    }
                }
            }
            // #3: 詳細値は参照元の型で取得できる候補から選択します。
            GestureSettingScreen.VARIABLE_PROPERTY -> {
                if (group != "property") return
                if (me.awabi2048.kantancommander.model.ValueProperties.definition(value) == null) return
                if (updateSettingNode(player, settingContext) {
                        CommandSettingsModel.setParameter(it, "property", value)
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
            // クリップ（位置固定）は確認・実行・結果の全段階で通常通り操作できます。
            if (context.elementId == "nav-clip" && GestureGuiClickPolicy.isPrimaryClick(context.gesture)) {
                if (state.anchor != null) {
                    if (!api.unpinToFollow(ownerId)) return
                    state.anchor = null
                } else {
                    if (!api.pinToCurrentPosition(ownerId)) return
                    state.anchor = player.eyeLocation.clone()
                }
                updateUpper(player)
                return
            }
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
                        closeWithAnimation(ownerId)
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
                        closeWithAnimation(ownerId)
                    }
                }
                GestureTestPhase.RESULT -> when {
                    context.elementId == "test-result-complete" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                        completeTest(player)
                    }
                    // 結果画面の閉じるボタンは、編集表示へ戻したうえで画面を閉じます。
                    context.elementId == "nav-close" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                        completeTest(player)
                        closeWithAnimation(ownerId)
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
                updateUpperIfNeeded(player)
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
                    // 選択済みノードへの再クリックは、種類別の変形動作です。
                    // 折り畳みノードの再クリックは展開、FOR開始・条件分岐の再クリックは
                    // 折り畳み（条件分岐は合流済みのみ）を行い、条件分岐のShift+再クリックは
                    // TRUE/FALSE鎖の入替です。折り畳み操作は再クリックを唯一の経路とし、
                    // 下部の設定表示は維持します。
                    // 新規選択は従来どおり設定表示へ遷移します。
                    val clicked = script.graph.nodes.getValue(nodeId)
                    if (state.selectedNodeId == nodeId && !settingChildOpen(ownerId)) {
                        // Shift+再クリックの枝入替だけを先に分岐し、通常クリックの
                        // 折り畳み変形は applyFoldToggle へ任せます。
                        if (clicked.type == CommandType.CONDITION &&
                            context.gesture == GestureGuiGesture.SHIFT_PRIMARY
                        ) {
                            swapConditionBranches(player, nodeId)
                            return
                        }
                        if (applyFoldToggle(player, clicked)) return
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
                // 1歩移動の可否だけで決め、実寸法クランプで丸めないことで、
                // 範囲外にいる間の不連続な端への飛びを防ぎます。
                if (!canNavigate(layout, metrics.columns, metrics.rows, delta)) {
                    // 移動不能時は状態・更新・効果音のいずれも発生させません。
                    return
                }
                state.origin = MapPoint(state.origin.x + delta.x, state.origin.y + delta.y)
                updateUpper(player)
            }
            context.elementId == "back-to-start" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                // 最も進んだ枝の追加ポイントをビューに含めるよう原点を調整
                // 挿入候補表示中は、後続ノードを右へ移動させた仮想レイアウトを使います。
                // 永続グラフだけで原点を決めると、候補アイコンと表示範囲の基準が分岐します。
                val layout = currentViewportLayout(player) ?: return
                val firstAdd = GestureEditorLayout.findMostAdvancedAddPoint(layout.cells)
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
                // 追加ボタンは現在の配置座標で表示されるため、確認も同じ配置で行います。
                val layout = runCatching {
                    GraphLayoutEngine.layout(script.graph)
                }.getOrElse { failure ->
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
                // 経路セルは現在の配置座標で表示されるため、確認も同じ配置で行います。
                val layout = runCatching {
                    GraphLayoutEngine.layout(script.graph)
                }.getOrElse { failure ->
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
            context.elementId == "lower-script-trigger" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                openTriggerKindSelection(player)
            }
            context.elementId == "lower-script-variables" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                openWorldVariables(player)
            }
            context.elementId == "lower-script-disk" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                // issue #8: 取り出し操作はディスク設定の子画面へ移し、引数定義と同列に扱います。
                openDiskSettings(player)
            }
            // issue #8: ディスク設定の取り出し・引数定義の編集です。
            context.elementId == "lower-disk-take-out" &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                outputScriptDisk(player)
            }
            context.elementId == "lower-diskdef-add" &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                editDiskArgumentName(player, null)
            }
            context.elementId.startsWith("lower-diskdef-name:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val name = context.elementId.removePrefix("lower-diskdef-name:")
                if (name.isNotBlank()) editDiskArgumentName(player, name)
            }
            context.elementId.startsWith("lower-diskdef-type:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val name = context.elementId.removePrefix("lower-diskdef-type:")
                if (name.isNotBlank()) cycleDiskArgumentType(player, name)
            }
            context.elementId.startsWith("lower-diskdef-delete:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val name = context.elementId.removePrefix("lower-diskdef-delete:")
                if (name.isNotBlank()) deleteDiskArgument(player, name)
            }
            context.elementId.startsWith("lower-diskdef-page:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                state.diskSettingsArgsPage =
                    context.elementId.removePrefix("lower-diskdef-page:").toIntOrNull() ?: return
                updateLower(player)
            }
            context.elementId == "lower-setting-back" && state.lowerMode == GestureLowerMode.DISK_SETTINGS &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                closeDiskSettings(player)
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
            // タグ一覧子画面の操作です。ワールド変数子画面と同一の接頭辞方式を使い、
            // 一覧カード・モード・削除の取り違えが起きない順序で判定します。
            context.elementId.startsWith("lower-tag-page:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                state.tagPage = context.elementId.removePrefix("lower-tag-page:").toIntOrNull() ?: return
                updateLower(player)
            }
            context.elementId.startsWith("lower-tag-delete:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val index = context.elementId.removePrefix("lower-tag-delete:").toIntOrNull() ?: return
                deleteEntityTagEntry(player, index)
            }
            context.elementId.startsWith("lower-tag-mode:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val index = context.elementId.removePrefix("lower-tag-mode:").toIntOrNull() ?: return
                toggleEntityTagMode(player, index)
            }
            context.elementId == "lower-tag-create" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                showTagEntryDialog(player)
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
                    ValueType.valueOf(context.elementId.removePrefix("lower-world-variable-type:"))
                }.getOrNull() ?: return
                state.pendingWorldVariableType = type
                updateLower(player)
            }
            context.elementId == "lower-world-variable-type-next" &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val type = state.pendingWorldVariableType ?: return
                showWorldVariableNameDialog(player, type)
            }
            // issue #7: 起動方法の種別選択です。タイマーは間隔入力へ進み、
            // 信号は即時確定し、イベントは種別選択へ進みます。
            context.elementId.startsWith("lower-trigger-kind:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val kind = runCatching {
                    TriggerKind.valueOf(context.elementId.removePrefix("lower-trigger-kind:"))
                }.getOrNull() ?: return
                when (kind) {
                    TriggerKind.TIMER -> showTriggerIntervalDialog(player)
                    TriggerKind.REDSTONE -> saveTrigger(player, ScriptTrigger(TriggerKind.REDSTONE))
                    TriggerKind.EVENT -> openTriggerEventSelection(player)
                }
            }
            // issue #7: イベント種別の選択です。選択時に起動方法として確定します。
            context.elementId.startsWith("lower-trigger-event:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val event = runCatching {
                    EventTriggerKind.valueOf(context.elementId.removePrefix("lower-trigger-event:"))
                }.getOrNull() ?: return
                saveTrigger(player, ScriptTrigger(TriggerKind.EVENT, event))
            }
            context.elementId == "lower-setting-back" && state.lowerMode == GestureLowerMode.TRIGGER_EVENT &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                openTriggerKindSelection(player)
            }
            context.elementId == "lower-setting-back" && state.lowerMode == GestureLowerMode.TRIGGER_KIND &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                closeTriggerKindSelection(player)
            }
            // issue #8: 呼び出し設定の引数・戻り値受取先の編集です。
            context.elementId.startsWith("lower-disk-arg:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val name = context.elementId.removePrefix("lower-disk-arg:")
                if (name.isNotBlank()) editDiskCallArgument(player, name)
            }
            context.elementId == "lower-disk-return-scope" &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                cycleDiskCallReturnScope(player)
            }
            context.elementId == "lower-disk-return-name" &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                editDiskCallReturnName(player)
            }
            context.elementId.startsWith("lower-disk-arg-page:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                state.diskCallArgsPage =
                    context.elementId.removePrefix("lower-disk-arg-page:").toIntOrNull() ?: return
                updateLower(player)
            }
            // issue #7: イベント種別の選択子画面のページ移動です。
            context.elementId.startsWith("lower-trigger-event-page:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                state.triggerEventPage =
                    context.elementId.removePrefix("lower-trigger-event-page:").toIntOrNull() ?: return
                updateLower(player)
            }
            context.elementId == "lower-setting-back" && state.lowerMode == GestureLowerMode.DISK_CALL_SETTINGS &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                closeDiskCallSettings(player)
            }
            context.elementId == "lower-variables-create" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                openWorldVariableTypeSelection(player)
            }
            context.elementId.startsWith("lower-tab:") && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                // 実行位置タブは固定行へ抽出しているため、全フィールド基準の番号へ戻します。
                val suffix = context.elementId.removePrefix("lower-tab:")
                val index = suffix.toIntOrNull() ?: run {
                    if (suffix != "exec") return
                    val script = plugin.scripts.load(state.scriptId) ?: return
                    val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
                    CommandSettingsModel.gestureVisibleFields(node)
                        .indexOfFirst { it.key == "executionPosition" }.takeIf { it >= 0 } ?: return
                }
                openSettingsTab(player, index)
            }
            // issue #18: Sliderのバー上クリック・段階操作です。検証の正本は
            // CommandDialogSpecs側に残し、ここでは仮値更新だけを扱います。
            // 保存・revision進行は［確定］だけが行います。
            (context.elementId.startsWith("lower-slider-dec:") || context.elementId.startsWith("lower-slider-inc:")) &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                handleSliderStep(context, player)
            }
            context.elementId.startsWith("lower-slider-bar:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                handleSliderBar(context, player)
            }
            context.elementId.startsWith("lower-slider-commit:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                handleSliderCommit(context, player)
            }
            context.elementId.startsWith("lower-slider-cancel:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                cancelFieldPreview(player)
            }
            context.elementId.startsWith("lower-slider-input:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val fieldKey = context.elementId.removePrefix("lower-slider-input:")
                if (fieldKey.isBlank()) return
                // Dialog入力へ移る前に仮値を破棄し、保存値との二重表示を防ぎます。
                discardFieldPreview(player)
                beginSelectedFieldEdit(player, fieldKey)
            }
            context.elementId.startsWith("lower-toggle:") &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                handleToggleAction(context, player)
            }
            // 親タブ直下のプリセット選択操作です。一覧選択とDialog入力は
            // 同じ保存値への別経路であり、子画面遷移は行いません。
            context.elementId.startsWith(InlineResourcePicker.PICK_PREFIX) &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                applyInlineResourcePick(player, context.elementId)
            }
            context.elementId.startsWith(InlineResourcePicker.INPUT_PREFIX) &&
                GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val fieldKey = InlineResourcePicker.parseInput(context.elementId) ?: return
                beginSelectedFieldEdit(player, fieldKey)
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
            context.elementId == "lower-firework-preview" && GestureGuiClickPolicy.isPrimaryClick(context.gesture) -> {
                val script = plugin.scripts.load(state.scriptId) ?: return
                val node = state.selectedNodeId?.let { script.graph.nodes[it] } ?: return
                launchFireworkPreview(player, node)
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
                    // 候補セルは現在の配置座標で選ばれるため、再確認も同じ配置で行います。
                    val currentTarget = runCatching {
                        GraphLayoutEngine.layout(script.graph)
                    }
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
                updateUpperIfNeeded(player)
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
                // 右上の閉じる操作は、閉じるアニメーションを再生して終了します。
                // Shift+Jump 等の終了ジェスチャーは即時終了のままです。
                if (context.actorId == ownerId) {
                    closeWithAnimation(ownerId)
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
                updateUpperIfNeeded(player)
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
                if (state.confirmKind == GestureConfirmKind.FIREWORK_OVERWRITE) {
                    confirmFireworkOverwrite(player)
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
                // 削除済みノードの一時previewは保存値へ戻せず残留するため破棄します。
                previewStore.removeNode(nodeId)
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
                updateUpperIfNeeded(player)
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





    /**
     * ノードホバーの1行目に表示する折り畳み操作案内を返します。
     *
     * 1行目は案内、2行目がノード名となるよう、呼び出し側で名前へ改行接続します。
     * 対象外ノードではnullを返し、従来どおり名前だけを表示します。
     * 未合流の条件分岐は折り畳めないため案内を出しません（素の再クリックは枝入替）。
     * 可否条件は再クリック受付と共有し、案内と操作のずれを防ぎます。
     */
    private fun foldGuidanceText(player: Player, node: CommandNode, graph: CommandGraph): String? {
        if (!NodeReorderLayout.isFoldToggleAvailable(node, graph)) return null
        val key = when (node.type) {
            CommandType.FOLDED_FOR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ACTION_UNFOLD_FOR
            CommandType.FOLDED_IF -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ACTION_UNFOLD_IF
            CommandType.FOR_START -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ACTION_FOLD_FOR
            CommandType.CONDITION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ACTION_FOLD_IF
            else -> return null
        }
        return KcI18n.text(player, key)
    }

    /**
     * 選択済みノードへの再クリックによる折り畳み変形動作です。
     *
     * 折り畳み操作は再クリックを唯一の経路とします。
     * 折り畳みノードは展開、FOR開始・条件分岐は折り畳みを行い、処理した場合は
     * trueを返します。条件分岐のShift+再クリック（枝入替）は呼び出し側で先に
     * 分岐するため、ここでは扱いません。対象外ノードでは何もせずfalseを返し、
     * 呼び出し側は従来どおり選択処理へ進みます。
     */
    private fun applyFoldToggle(player: Player, node: CommandNode): Boolean = when (node.type) {
        CommandType.FOLDED_FOR, CommandType.FOLDED_IF -> {
            unfoldNode(player, node.id)
            true
        }
        CommandType.FOR_START -> if (node.pairedNodeId != null) {
            foldFor(player, node.id)
            true
        } else false
        CommandType.CONDITION -> {
            foldIf(player, node.id)
            true
        }
        else -> false
    }

    /**
     * ノードアイコンの表示用アイテムを返します。
     *
     * 折りたたみ中の構造はエンチャントグリントを付け、折畳み状態を補助表現します
     * （システム横断UI設計の状態表現規則）。Block表示ではグリントを使えないため、
     * アイコン（Item表示）にだけ付け、背景・縁取りは変えません。
     */
    private fun foldedIconStack(node: CommandNode, folded: Boolean): org.bukkit.inventory.ItemStack {
        val stack = org.bukkit.inventory.ItemStack(node.type.icon)
        if (!folded) return stack
        val meta = stack.itemMeta ?: return stack
        // グリント上書きで発光だけを付け、偽のエンチャントや説明文は付与しません。
        runCatching { meta.setEnchantmentGlintOverride(true) }
        stack.itemMeta = meta
        return stack
    }

    /**
     * 選択済み条件分岐のShift+再クリックでTRUE/FALSE鎖を入れ替えます。
     *
     * 表示だけの反転ではなく構造化データの操作として保存します。合流・後続・
     * 反転フラグは不変で、両枝の接続先だけが交換されます。
     */
    private fun swapConditionBranches(player: Player, nodeId: UUID) {
        val swapped = runCatching {
            CommandSettingsModel.updateGraph(
                plugin,
                state.scriptId,
                player.uniqueId,
                expectedRevision = expectedMutationRevision(player),
            ) { candidateGraph ->
                if (GraphEditor.swapBranches(candidateGraph, nodeId)) true else null
            }
        }.getOrElse { failure ->
            reportGraphOperationFailure(
                player,
                "条件分岐の枝入れ替えを保存できませんでした: script=${state.scriptId} node=$nodeId",
                failure,
            )
            return
        }
        if (swapped != true) return
        state.selectedNodeId = nodeId
        updateUpperIfNeeded(player)
        updateLower(player)
    }
    /**
     * 選択済みFOR開始の再クリックでFOR全体を折り畳みノードへ再構成します。
     *
     * 外側には開始と同じIDの折り畳みノードが残り、中身は内部グラフへ退避します。
     * 下部の設定表示は維持し、上下ビューポートを更新します。
     */
    private fun foldFor(player: Player, nodeId: UUID) {
        val folded = runCatching {
            CommandSettingsModel.updateGraph(
                plugin,
                state.scriptId,
                player.uniqueId,
                expectedRevision = expectedMutationRevision(player),
            ) { candidateGraph ->
                if (GraphEditor.foldForStart(candidateGraph, nodeId)) true else null
            }
        }.getOrElse { failure ->
            reportGraphOperationFailure(
                player,
                "FOR折り畳みを保存できませんでした: script=${state.scriptId} node=$nodeId",
            failure,
            )
            return
        }
        if (folded != true) return
        state.selectedNodeId = nodeId
        player.playSound(player.location, Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f)
        updateUpperIfNeeded(player)
        updateLower(player)
    }

    /**
     * 選択済み条件分岐の再クリックで閉じた分岐全体を折り畳みノードへ再構成します。
     *
     * 合流のある分岐だけが対象です。未合流の分岐は折り畳めないため何もしません。
     * 下部の設定表示は維持し、上下ビューポートを更新します。
     */
    private fun foldIf(player: Player, nodeId: UUID) {
        val folded = runCatching {
            CommandSettingsModel.updateGraph(
                plugin,
                state.scriptId,
                player.uniqueId,
                expectedRevision = expectedMutationRevision(player),
            ) { candidateGraph ->
                if (GraphEditor.foldCondition(candidateGraph, nodeId)) true else null
            }
        }.getOrElse { failure ->
            reportGraphOperationFailure(
                player,
                "IF折り畳みを保存できませんでした: script=${state.scriptId} node=$nodeId",
            failure,
            )
            return
        }
        if (folded != true) return
        state.selectedNodeId = nodeId
        player.playSound(player.location, Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f)
        updateUpperIfNeeded(player)
        updateLower(player)
    }

    /**
     * 選択済み折り畳みノードの再クリックで内部構造を元の位置へ展開します。
     *
     * 折り畳み中の外側設定を正として内部先頭へ反映します。下部の設定表示は
     * 維持し、上下ビューポートを更新します。
     */
    private fun unfoldNode(player: Player, nodeId: UUID) {
        val unfolded = runCatching {
            CommandSettingsModel.updateGraph(
                plugin,
                state.scriptId,
                player.uniqueId,
                expectedRevision = expectedMutationRevision(player),
            ) { candidateGraph ->
                if (GraphEditor.unfold(candidateGraph, nodeId)) true else null
            }
        }.getOrElse { failure ->
            reportGraphOperationFailure(
                player,
                "折り畳み展開を保存できませんでした: script=${state.scriptId} node=$nodeId",
            failure,
            )
            return
        }
        if (unfolded != true) return
        state.selectedNodeId = nodeId
        player.playSound(player.location, Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f)
        updateUpperIfNeeded(player)
        updateLower(player)
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
        // 描画失敗はチャット通知とエラー画面へ一本化し、コンソールへ出しません。
        // 引数は呼び出し側との funnel 契約のため維持します。
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
            // 保存失敗はチャット通知へ一本化し、コンソールへ出しません。
            player.sendMessage(GraphLayoutFailureFeedback.saveMessage(player))
        }
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
        navigationHoverText(Component.text(KcI18n.text(player, key)))

    /** ナビゲーション列と共通の説明欄へ、任意の文言を表示します。 */
    private fun navigationHoverText(text: Component): GestureGuiHoverText =
        GestureGuiHoverText(
            text = text,
            x = GestureEditorLayout.NAV_HOVER_X,
            y = GestureEditorLayout.NAV_HOVER_Y,
            size = GestureEditorLayout.NAV_HOVER_SIZE,
            lineWidth = GestureEditorLayout.NAV_HOVER_LINE_WIDTH,
        )

    private fun rect(cx: Double, cy: Double, width: Double, height: Double): GestureGuiBounds =
        GestureGuiBounds(cx - width / 2.0, cy - height / 2.0, cx + width / 2.0, cy + height / 2.0)


    /** 表示中の仮想挿入状態を含め、現在のビューポート移動可否を再計算します。 */
    private fun canMoveCurrentViewport(delta: MapPoint): Boolean {
        val layout = currentViewportLayout() ?: return false
        val metrics = viewportMetrics(zoomScale())
        return canNavigate(layout, metrics.columns, metrics.rows, delta)
    }

    /**
     * 折りたたみ後の範囲外に原点がある間も、実配置へ戻る方向の移動を許可します。
     *
     * 記憶寸法（展開時）と現配置の広い方を上限の基準にし、方向限定は共通規則へ
     * 委ねます。範囲内へ戻った後は実配置の通常規則と等価になります。
     */
    private fun canNavigate(layout: GraphLayout, viewportWidth: Int, viewportHeight: Int, delta: MapPoint): Boolean =
        GestureEditorLayout.canMoveWithRememberedRange(
            state.origin,
            delta,
            viewportWidth,
            viewportHeight,
            layout.width,
            layout.height,
            maxOf(layout.width, state.lastExpandedLayoutWidth),
            maxOf(layout.height, state.lastExpandedLayoutHeight),
        )

    /**
     * 描画・ナビ・ズームで共有する原点正規化です。
     *
     * 折りたたみで配置が縮んでも展開時の記憶寸法を基準にするため、範囲外へ
     * 残った原点が端へ不連続に飛びません。
     */
    private fun clampOriginWithRemembered(
        origin: MapPoint,
        viewportWidth: Int,
        viewportHeight: Int,
        layout: GraphLayout,
    ): MapPoint = GestureEditorLayout.clampOrigin(
        origin,
        maxOf(layout.width, state.lastExpandedLayoutWidth),
        maxOf(layout.height, state.lastExpandedLayoutHeight),
        viewportWidth,
        viewportHeight,
    )

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
        // 入力判定も描画と同じ折りたたみ配置で確認します。展開配置で確認すると、
        // 表示上の無効（グレーアウト）と実可否がずれて空きへ戻れてしまいます。
        val persistedLayout = runCatching {
            GraphLayoutEngine.layout(
                script.graph,
            )
        }.getOrElse { failure ->
            player?.let {
                reportLayoutFailure(
                    it,
                    "ジェスチャーGUIの現在経路確認でレイアウトを生成できません: script=${script.id}",
                    failure,
                )
            }
            return null
        }
        // テスト中はADDとその延長経路を除いた真の配置で確認します。
        // testActiveが真のときtestは非nullであることが確定しています。
        if (testActive) return persistedLayout.withoutEditArtifacts(test.passedEdges)
        return insertionPreview(script, player)?.layout ?: persistedLayout
    }






    private fun zoomScale(): Double = GestureEditorLayout.zoomScaleFor(state.zoomLevel)

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
        state.origin = clampOriginWithRemembered(
            MapPoint(
                (centerX - (newMetrics.columns - 1) / 2.0).roundToInt(),
                (centerY - (newMetrics.rows - 1) / 2.0).roundToInt(),
            ),
            newMetrics.columns,
            newMetrics.rows,
            layout,
        )
    }

    private fun viewportMetrics(scale: Double): ViewportMetrics {
        // 座標式の正本は GraphViewportMapper です。宣言経路と共有し、 divergences を防ぎます。
        val frame = GraphViewportMapper.legacyFrame(scale)
        return ViewportMetrics(
            zoomScale = frame.zoomScale,
            columns = frame.columns,
            rows = frame.rows,
            firstX = frame.firstX,
            firstY = frame.firstY,
            pitchX = frame.pitchX,
            pitchY = frame.pitchY,
            iconSize = frame.iconSize,
            iconScale = frame.iconScale,
            pathThickness = frame.pathThickness,
            graphMinX = frame.minX,
            graphMaxX = frame.maxX,
            graphMinY = frame.minY,
            graphMaxY = frame.maxY,
        )
    }

    private companion object {
        const val DIALOG_OWNER = "kantan-commander"
        /** テスト実行の効果音（minecraft:block.note_block.bit）の高さです。 */
        const val TEST_SOUND_START_PITCH = 1.0f
        const val TEST_SOUND_SUCCESS_PITCH = 1.5f
        const val TEST_SOUND_FAILURE_PITCH = 0.5f
        const val TEST_SOUND_INTERRUPT_PITCH = 1.25f
        /**
         * エディター画面全体へ適用するY方向の補正です(ブロック単位)。
         * 追従中の再計算にだけ適用され、クリップ固定位置には影響しません。
         */
        const val EDITOR_VERTICAL_OFFSET: Double = -0.25
        /**
         * エディター画面の上下傾き倍率です。1.0で従来配置、0.5で半減します。
         * 追従中の再計算にだけ適用され、クリップ固定位置には影響しません。
         */
        const val EDITOR_TILT_SCALE: Double = 0.5
        /**
         * エディター画面までの追従距離です(ブロック単位)。
         * 標準の画面距離 1.5 の25%増しとし、追従中の再計算にだけ適用されます。
         * クリップ固定位置には影響しません。
         */
        const val EDITOR_FOLLOW_DISTANCE: Double = 1.875
        /** CC-SystemのOPENING完了待ちを吸収する上限（13tickのアニメーションより長くします）。 */
        const val MAX_RENDER_RETRY_TICKS = 20
        /** テスト中の経過表示の更新周期です。秒表示のため1Hzで十分です。 */
        const val TEST_STATUS_REFRESH_PERIOD_TICKS = 20L
    }

}
