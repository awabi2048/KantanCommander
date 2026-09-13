package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.error.ErrorCode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.MAX_BLOCK_OPERATION_VOLUME
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.variableScope
import me.awabi2048.kantancommander.model.ControlBlockStateConditionPolicy
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.kantancommander.model.ExecutionVariables
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.declaredArguments
import me.awabi2048.kantancommander.model.normalizedTemporaryName
import me.awabi2048.kantancommander.model.selectedControlBlockStates
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.VariableRead
import me.awabi2048.kantancommander.model.VariableStore
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityPropertyValue
import me.awabi2048.kantancommander.model.clampMinecraftPitch
import me.awabi2048.kantancommander.model.wrapMinecraftYaw
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.RegionAnchorKind
import me.awabi2048.kantancommander.model.RegionKind
import me.awabi2048.kantancommander.model.RegionSpec
import me.awabi2048.kantancommander.model.SpatialVector
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.DisplayTextTiming
import me.awabi2048.kantancommander.model.FireworkSettings
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.PlaceItemDestination
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.error.PartialApplicability
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.SoundCategory
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.logging.Level
import kotlin.random.Random
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.kantancommander.item.ItemStackCodec
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** テスト実行の終了理由です。手動停止は失敗と区別して表示します。 */
enum class ExecutionFinishStatus {
    SUCCESS,
    FAILURE,
    CANCELLED,
}

/** ノード処理結果。参照先が無い場合だけ、失敗と区別して後続へ進めます。 */
enum class NodeExecutionOutcome {
    SUCCESS,
    SKIPPED,
    FAILED,
}

/** テスト実行へ通知する、最上位グラフのノード開始イベントです。 */
data class ExecutionNodeStarted(
    val scriptId: UUID,
    val nodeId: UUID,
    val nodeType: CommandType,
    val depth: Int,
    val attemptNumber: Int,
    val tick: Long,
)

/**
 * テスト実行へ通知する、最上位グラフのノード完了イベントです。
 * expandedParamsは実行値の展開結果、positionTextは実行位置です。
 * ホバー表示で構造的に出すため、detailの本文とは別に保持します。
 *
 * issue #6 の共通分類として errorCode を保持します。reason 文字列は
 * 旧呼出との互換のため残しますが、新規判定は code で行います。
 */
data class ExecutionNodeFinished(
    val scriptId: UUID,
    val nodeId: UUID,
    val nodeType: CommandType,
    val depth: Int,
    val attemptNumber: Int,
    val outcome: NodeExecutionOutcome,
    val successfulNodeCount: Int,
    val nextNodeId: UUID?,
    val tick: Long,
    val detail: String? = null,
    val reason: String? = null,
    val loopReturn: Boolean = false,
    val expandedParams: Map<String, String> = emptyMap(),
    val positionText: String? = null,
    /** 次ノードまでのデバッグ遅延です。待機表示の切り替えに使います。 */
    val nextWaitTicks: Long = 0L,
    /** 共通エラーコードです。旧 reason から読み替えた値が入ります。 */
    val errorCode: ErrorCode? = ErrorCode.fromLegacyReason(reason),
)

/**
 * テスト実行の最終結果です。successfulNodeCountは失敗ノードを含みません。
 * waitTicksはWAITコマンドによる待機の合計、debugWaitTicksはノード間の
 * デバッグ待機（復帰後の追加待機を含む）の合計です。どちらもelapsedTicksの内数です。
 *
 * issue #6 の共通分類として errorCode を保持します。reason 文字列は
 * 旧呼出との互換のため残しますが、新規判定は code で行います。
 */
data class ExecutionResult(
    val status: ExecutionFinishStatus,
    val elapsedTicks: Long,
    val successfulNodeCount: Int,
    val attemptCount: Int,
    val failedNodeId: UUID? = null,
    val reason: String? = null,
    val waitTicks: Long = 0L,
    val debugWaitTicks: Long = 0L,
    /** 共通エラーコードです。旧 reason から読み替えた値が入ります。 */
    val errorCode: ErrorCode? = ErrorCode.fromLegacyReason(reason),
)

/** 実行中のノードイベントと終了結果を受け取る観測者です。 */
interface ExecutionObserver {
    fun onNodeStarted(event: ExecutionNodeStarted) {}
    fun onNodeFinished(event: ExecutionNodeFinished) {}
    fun onFinished(result: ExecutionResult) {}
}

data class ExecutionOptions(
    val observer: ExecutionObserver? = null,
    val debugDelayTicks: Long = 0L,
    val suppressCreatorFailureNotification: Boolean = false,
    /**
     * ループ復帰後に本体開始まで追加で待機するtick数です。S→body待機の
     * 表示期間に使い、実処理時間からは差し引きます。通常実行では0のままです。
     */
    val loopRestartExtraWaitTicks: Long = 0L,
)

/** 非同期テスト実行を途中終了するためのハンドルです。 */
class ExecutionHandle internal constructor() {
    @Volatile
    var isActive: Boolean = true
        private set

    private var cancelAction: ((String) -> Unit)? = null

    fun cancel(reason: String = "manual_stop") {
        cancelAction?.invoke(reason)
    }

    internal fun bind(action: (String) -> Unit) {
        cancelAction = action
    }

    internal fun complete() {
        isActive = false
        cancelAction = null
    }
}

class SequenceExecutor(private val plugin: KantanCommanderPlugin) {
    private val timedActionBar = TimedActionBarService(plugin)
    private val systemEntityRegistry
        get() = CCSystem.getAPI().getSystemEntityRegistry()

    fun execute(scriptId: UUID, origin: Location, actor: Player? = null, callback: (Boolean) -> Unit = {}) {
        execute(scriptId, origin, actor, emptyMap(), callback)
    }

    /**
     * issue #7 のイベント引数付き実行です。
     *
     * `initialVariables` は実行開始時に定義済みとなるEXECUTION変数であり、
     * #8 のDISK_CALL引数と同じく呼び出し先の開始時点で定義済みとして渡します。
     * 検証層は同じ表をデータフロー解析の種子として扱うため、静的検証と
     * 実行時の定義状態が一致します。不正な値は fail-fast で拒否します。
     */
    fun execute(
        scriptId: UUID,
        origin: Location,
        actor: Player? = null,
        initialVariables: Map<String, TypedValue> = emptyMap(),
        callback: (Boolean) -> Unit = {},
    ) {
        val script = plugin.scripts.load(scriptId) ?: return callback(false)
        startExecution(
            script = script,
            origin = origin,
            actor = actor,
            options = ExecutionOptions(),
            initialVariables = initialVariables,
        ) { result -> callback(result.status == ExecutionFinishStatus.SUCCESS) }
    }

    /**
     * 保存済み正本を変更せずに、配置ブロック上のスナップショットを実行します。
     * テスト実行は実ワールドへ作用するため、通常実行と同じ実行器を通しつつ、
     * 観測者へ最上位グラフのノード境界だけを公開します。DISK_CALL内部は1ノード
     * として扱い、内部グラフのイベントやデバッグ待機は外へ漏らしません。
     */
    fun executeTest(
        snapshot: DiskScript,
        origin: Location,
        options: ExecutionOptions = ExecutionOptions(),
        callback: (ExecutionResult) -> Unit = {},
    ): ExecutionHandle = startExecution(
        script = snapshot.copy(graph = snapshot.graph.deepCopy()),
        origin = origin,
        actor = null,
        options = options,
        callback = callback,
    )

    private fun startExecution(
        script: DiskScript,
        origin: Location,
        actor: Player?,
        options: ExecutionOptions,
        initialVariables: Map<String, TypedValue> = emptyMap(),
        callback: (ExecutionResult) -> Unit,
    ): ExecutionHandle {
        val handle = ExecutionHandle()
        val startTick = plugin.server.currentTick.toLong()
        val worldData = if (plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) {
            MyWorldManagerApi.getWorldRepository()?.findByWorldName(origin.world.name)
        } else null
        if (worldData == null) {
            plugin.logger.warning("[KantanCommander] rejected disk=${script.id} reason=outside_myworld world=${origin.world.name}")
            return completeWithoutSession(
                handle,
                options,
                callback,
                ExecutionResult(ExecutionFinishStatus.FAILURE, 0L, 0, 0, reason = "outside_myworld"),
            )
        }
        val validationErrors = ExecutableScriptValidator.validate(
            script,
            plugin.graphLimits(),
            plugin.variables.definitions(worldData.uuid),
        )
        if (validationErrors.isNotEmpty()) {
            plugin.logger.warning(
                "[KantanCommander] rejected disk=${script.id} reason=invalid_script " +
                    "errors=${validationErrors.joinToString(" | ") { it.rendered() }}"
            )
            return completeWithoutSession(
                handle,
                options,
                callback,
                ExecutionResult(
                    ExecutionFinishStatus.FAILURE,
                    0L,
                    0,
                    0,
                    reason = "invalid_script: ${validationErrors.joinToString(" | ") { it.rendered() }}",
                ),
            )
        }
        // 通常実行とテスト実行は同じセッション実装を使います。テストだけが
        // observer/debugDelayTicksを持ち、actor=nullで暗黙の実行者を作りません。
        // 折り畳みノードは保存形のまま実行できるよう、実行用の一時グラフへ展開します。
        // 永続化内容は折り畳んだまま維持されます。
        val executionGraph = me.awabi2048.kantancommander.data.FoldedGraphs.expand(script.graph)
        val executionScript = script.copy(graph = executionGraph)
        val session = ExecutionSession(
            rootId = script.id,
            origin = origin.clone(),
            actor = actor,
            creatorId = script.owner,
            programName = script.name,
            budget = plugin.config.getInt("execution.max-nodes-per-activation"),
            maxDepth = plugin.config.getInt("execution.max-disk-call-depth"),
            worldId = worldData.uuid,
            options = options,
            startTick = startTick,
            handle = handle,
            callback = callback,
        )
        // issue #7: イベント引数を実行開始時の定義済みEXECUTION変数として設定します。
        // 不正な値は fail-fast で拒否し、未定義のまま実行しません。
        initialVariables.forEach { (name, value) -> session.executionVariables.set(name, value) }
        handle.bind { reason -> cancelSession(session, reason) }
        runGraph(executionScript, executionGraph, session, 0) { success ->
            finishSession(
                session,
                if (success) ExecutionFinishStatus.SUCCESS else ExecutionFinishStatus.FAILURE,
                session.failureReason,
            )
        }
        return handle
    }

    private fun completeWithoutSession(
        handle: ExecutionHandle,
        options: ExecutionOptions,
        callback: (ExecutionResult) -> Unit,
        result: ExecutionResult,
    ): ExecutionHandle {
        handle.complete()
        runCatching { options.observer?.onFinished(result) }
            .onFailure { failure -> plugin.logger.log(Level.WARNING, "[KantanCommander] execution observer failed", failure) }
        callback(result)
        return handle
    }

    private fun runGraph(
        script: DiskScript,
        graph: CommandGraph,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        if (!session.isActive) return
        runNode(script, graph, graph.entryNodeId, session, depth, done)
    }

    private fun runNode(
        script: DiskScript,
        graph: CommandGraph,
        nodeId: UUID?,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        if (!session.isActive) return
        if (nodeId == null) return done(true)
        val node = graph.nodes[nodeId] ?: return stop(session, script, nodeId, depth, "missing_node", done)
        if (node.type == CommandType.FOR_START && node.trueNext == node.pairedNodeId) {
            val after = node.pairedNodeId?.let(graph.nodes::get)?.next
            return runNode(script, graph, after, session, depth, done)
        }
        if (!ExecutionSemantics.withinBudget(session.executed, session.budget)) {
            // 上限超過も「そのノードを実行しようとして失敗した」結果です。
            // ここで開始／終了イベントを省くと、テスト画面では赤色表示も失敗ログも
            // 欠落し、実行コマンド数とログの通し番号の関係も崩れます。
            val attemptNumber = if (depth == 0) {
                session.attemptCount++
            } else {
                0
            }
            if (depth == 0) {
                session.currentAttemptNumber = attemptNumber
                session.currentRootNodeId = node.id
                notifyNodeStarted(
                    session,
                    ExecutionNodeStarted(
                        scriptId = script.id,
                        nodeId = node.id,
                        nodeType = node.type,
                        depth = depth,
                        attemptNumber = attemptNumber,
                        tick = plugin.server.currentTick.toLong(),
                    ),
                )
            }
            session.lastDetail = "実行上限に達したため実行できなかった"
            return advanceNode(
                script = script,
                graph = graph,
                node = node,
                session = session,
                depth = depth,
                attemptNumber = attemptNumber,
                target = null,
                outcome = NodeExecutionOutcome.FAILED,
                done = done,
                reason = "command_limit",
            )
        }
        session.executed++
        val attemptNumber = if (depth == 0) {
            session.attemptCount++
        } else {
            0
        }
        if (depth == 0) {
            session.currentAttemptNumber = attemptNumber
            session.currentRootNodeId = node.id
            notifyNodeStarted(
                session,
                ExecutionNodeStarted(
                    scriptId = script.id,
                    nodeId = node.id,
                    nodeType = node.type,
                    depth = depth,
                    attemptNumber = attemptNumber,
                    tick = plugin.server.currentTick.toLong(),
                ),
            )
        }
        session.lastDetail = null
        session.lastPartial = null
        session.lastExpandedParams = emptyMap()
        session.lastPositionText = null
        session.lastFailureReason = null
        session.lastFailureCode = null

        val next: (UUID?, NodeExecutionOutcome) -> Unit = { target, outcome ->
            advanceNode(
                script = script,
                graph = graph,
                node = node,
                session = session,
                depth = depth,
                attemptNumber = attemptNumber,
                target = target,
                outcome = outcome,
                done = done,
                reason = if (outcome == NodeExecutionOutcome.FAILED) {
                    // DISK_CALL は内部実行で設定した失敗理由を外側へ引き継ぎます。
                    // ここを直前ノード専用の値だけにすると、ホバーと結果画面が
                    // 「command_failed」に戻り、実際の失敗原因を失ってしまいます。
                    session.lastFailureReason ?: session.failureReason ?: "command_failed"
                } else if (outcome == NodeExecutionOutcome.SKIPPED) {
                    // SKIPPED は成功扱いで継続しますが、理由コードは保持して
                    // GUI の成功畳みを解消します。通常実行では表示しません。
                    session.lastFailureReason
                } else {
                    null
                },
                errorCode = if (outcome == NodeExecutionOutcome.FAILED) {
                    session.lastFailureCode ?: session.failureCode
                        ?: ErrorCode.fromLegacyReason(session.lastFailureReason ?: session.failureReason)
                } else if (outcome == NodeExecutionOutcome.SKIPPED) {
                    session.lastFailureCode
                } else {
                    null
                },
            )
        }
        when (node.type) {
            CommandType.WAIT -> {
                // 保存・入力値は秒を正本とし、Minecraftのスケジューラ境界でだけtickへ変換します。
                val seconds = resolveNumber(node.string("seconds"), session)
                    ?.takeIf(CommandValueRules::isWaitSeconds)
                    ?: return advanceNode(
                        script,
                        graph,
                        node,
                        session,
                        depth,
                        attemptNumber,
                        node.next,
                        NodeExecutionOutcome.FAILED,
                        done,
                        "invalid_wait_seconds",
                    )
                val waitTicks = CommandValueRules.secondsToTicks(seconds)
                    ?.takeIf { it >= 1L }
                    ?: return advanceNode(
                        script,
                        graph,
                        node,
                        session,
                        depth,
                        attemptNumber,
                        node.next,
                        NodeExecutionOutcome.FAILED,
                        done,
                        "invalid_wait_seconds",
                    )
                session.lastDetail = "WAIT: ${waitTicks} tick待機"
                // 待機時間は結果画面の内訳表示へ集計します。所要時間の内数です。
                if (depth == 0) session.waitTicks += waitTicks
                schedule(session, waitTicks) { next(node.next, NodeExecutionOutcome.SUCCESS) }
            }
            CommandType.CONDITION -> {
                val ambient = effectiveContext(session)
                // issue #26・#29: 実行起点集合を求め、対象は起点ごとの和集合で評価します。
                // 解決不能は従来経路へ委譲し、正常 false と解決失敗の分離を維持します。
                val origins = effectiveExecutionOrigins(node, session, ambient)
                val unionTargets = origins?.flatMap { origin ->
                    resolveTargets(originContext(origin, ambient), node.targetSpec, session)
                }?.distinctBy { it.uniqueId }
                val effectiveContext =
                    origins?.firstOrNull()?.let { originContext(it, ambient) } ?: ambient
                // 正常 false と解決失敗を分離します。UNRESOLVED/INVALID は
                // FALSE 分岐継続にせず、共通コード付きで失敗停止します。
                when (val evaluation = evaluateConditionDetailed(node, session, effectiveContext, unionTargets, origins)) {
                    ConditionEvaluation.TRUE, ConditionEvaluation.FALSE -> {
                        val rawResult = evaluation == ConditionEvaluation.TRUE
                        val result = ExecutionSemantics.conditionResult(rawResult, node.boolean("inverted"))
                        session.previousContext = effectiveContext
                        session.lastDetail = "分岐: ${if (result) "TRUE" else "FALSE"} に進んだ"
                        next(if (result) node.trueNext else node.falseNext, NodeExecutionOutcome.SUCCESS)
                    }
                    ConditionEvaluation.UNRESOLVED -> {
                        session.lastDetail = "条件値の解決に失敗した"
                        session.lastFailureReason = ErrorCode.CONDITION_UNRESOLVED.code
                        session.lastFailureCode = ErrorCode.CONDITION_UNRESOLVED
                        next(node.next, NodeExecutionOutcome.FAILED)
                    }
                    ConditionEvaluation.INVALID -> {
                        session.lastDetail = "条件値の形式が不正"
                        session.lastFailureReason = ErrorCode.CONDITION_INVALID_TYPE.code
                        session.lastFailureCode = ErrorCode.CONDITION_INVALID_TYPE
                        next(node.next, NodeExecutionOutcome.FAILED)
                    }
                }
            }
            CommandType.DISK_CALL -> {
                val callerContext = session.context
                val callerPrevious = session.previousContext
                runDiskCall(node, session, depth) { success ->
                    session.context = callerContext
                    session.previousContext = callerPrevious
                    session.lastDetail = if (success) "ディスク呼び出しに成功した" else "ディスク呼び出しに失敗した"
                    next(
                        node.next,
                        if (success) NodeExecutionOutcome.SUCCESS else NodeExecutionOutcome.FAILED,
                    )
                }
            }
            // issue #8: 残りのノードを打ち切って呼出元へ戻ります。値は
            // 呼び出し枠へ預け、DISK_CALL 境界で受け渡します。実行チェーンが
            // 線形のため、done への直接完了で残りを実行しません。
            CommandType.RETURN_VALUE -> {
                val value = resolveReturnValue(node, session) ?: run {
                    session.lastDetail = "戻り値の解決に失敗した"
                    session.lastFailureReason = ErrorCode.VARIABLE_UNSET.code
                    session.lastFailureCode = ErrorCode.VARIABLE_UNSET
                    next(node.next, NodeExecutionOutcome.FAILED)
                    return
                }
                val frame = session.callFrames.lastOrNull()
                if (frame != null) frame.pendingReturn = value
                session.returnValue = value
                session.returnRequested = true
                session.lastDetail = "値を返した"
                done(true)
            }
            CommandType.TEMP_SET -> {
                val success = executeTemporary(node, session)
                session.lastDetail = if (success) "一時変数を設定した" else "一時変数の設定に失敗した"
                next(
                    node.next,
                    if (success) NodeExecutionOutcome.SUCCESS else NodeExecutionOutcome.FAILED,
                )
            }
            CommandType.VARIABLE -> {
                val success = executeVariable(
                    node,
                    session,
            )
                if (success) session.previousContext = effectiveContext(session)
                session.lastDetail = if (success) "ワールド内変数を更新した" else "ワールド内変数の更新に失敗した"
                next(
                    node.next,
                    if (success) NodeExecutionOutcome.SUCCESS else NodeExecutionOutcome.FAILED,
                )
            }
            CommandType.MERGE -> {
                session.lastDetail = "分岐を合流した"
                next(node.next, NodeExecutionOutcome.SUCCESS)
            }
            CommandType.FOR_START -> beginFor(script, graph, node, session, depth, done)
            CommandType.FOR_END -> finishForIteration(script, graph, node, session, depth, done)
            CommandType.BREAK -> breakFor(script, graph, node, session, depth, done)
            CommandType.CONTINUE -> continueFor(script, graph, node, session, depth, done)
            else -> {
                // 一時参照の解決失敗は、入力値そのものの実行失敗とは異なります。
                // 仕様上は「対象が見つからなかった」と同じくそのノードだけを
                // スキップし、後続ノードを継続します。静的検証を通過した後でも、
                // ENTITYの死亡・別ワールド・アンロードは実行時に起こり得るため、
                // 実処理の前にこの判定を行います。
                val outcome = when {
                    hasUnavailableTemporaryReference(node, session) -> {
                        session.lastDetail = "参照先が見つからないためスキップした"
                        session.lastFailureReason = ErrorCode.TEMPORARY_UNRESOLVED.code
                        session.lastFailureCode = ErrorCode.TEMPORARY_UNRESOLVED
                        NodeExecutionOutcome.SKIPPED
                    }
                    else -> when (executeImmediate(node, session)) {
                        ImmediateExecutionResult.SUCCESS -> NodeExecutionOutcome.SUCCESS
                        ImmediateExecutionResult.SKIPPED -> NodeExecutionOutcome.SKIPPED
                        ImmediateExecutionResult.FAILED -> NodeExecutionOutcome.FAILED
                    }
                }
                next(node.next, outcome)
            }
        }
    }

    /** ノード完了通知、失敗停止、デバッグ待機を一つの遷移境界へ束ねます。 */
    private fun advanceNode(
        script: DiskScript,
        graph: CommandGraph,
        node: CommandNode,
        session: ExecutionSession,
        depth: Int,
        attemptNumber: Int,
        target: UUID?,
        outcome: NodeExecutionOutcome,
        done: (Boolean) -> Unit,
        reason: String?,
        loopReturn: Boolean = false,
        followUpDelayTicks: Long = 0L,
        errorCode: ErrorCode? = ErrorCode.fromLegacyReason(reason),
    ) {
        if (!session.isActive) return
        val success = outcome == NodeExecutionOutcome.SUCCESS || outcome == NodeExecutionOutcome.SKIPPED
        if (depth == 0 && success) session.successfulRootNodeCount++
        // 次までの待機です。表示駆動だけに使い、通常実行ではどちらも0のままです。
        val debugDelay = if (depth == 0 && target != null) session.options.debugDelayTicks else 0L
        val followUpDelay = if (depth == 0 && target != null) followUpDelayTicks else 0L
        if (depth == 0) {
            notifyNodeFinished(
                session,
                ExecutionNodeFinished(
                    scriptId = script.id,
                    nodeId = node.id,
                    nodeType = node.type,
                    depth = depth,
                    attemptNumber = attemptNumber,
                    outcome = outcome,
                    successfulNodeCount = session.successfulRootNodeCount,
                    nextNodeId = target,
                    tick = plugin.server.currentTick.toLong(),
                    detail = session.lastDetail,
                    reason = reason,
                    loopReturn = loopReturn,
                    expandedParams = session.lastExpandedParams,
                    positionText = session.lastPositionText,
                    // 次までの待機合計です。待機表示の切り替えに使います。
                    nextWaitTicks = debugDelay + followUpDelay,
                    errorCode = errorCode,
                ),
            )
        }
        if (!success) {
            if (depth == 0) session.failedNodeId = node.id
            session.failureReason = session.failureReason ?: reason ?: "node_failed"
            session.failureCode = session.failureCode ?: errorCode ?: ErrorCode.fromLegacyReason(session.failureReason)
            return stop(session, script, node.id, depth, session.failureReason!!, done)
        }
        if (target == null) return runNode(script, graph, null, session, depth, done)
        if (debugDelay > 0L || followUpDelay > 0L) {
            // 実処理時間の算出用にデバッグ待機の合計を数えます。
            session.debugWaitTicks += debugDelay + followUpDelay
            schedule(session, debugDelay + followUpDelay) { runNode(script, graph, target, session, depth, done) }
        } else {
            runNode(script, graph, target, session, depth, done)
        }
    }

    private fun notifyNodeStarted(session: ExecutionSession, event: ExecutionNodeStarted) {
        runCatching { session.options.observer?.onNodeStarted(event) }
            .onFailure { failure -> plugin.logger.log(Level.WARNING, "[KantanCommander] node-start observer failed", failure) }
    }

    private fun notifyNodeFinished(session: ExecutionSession, event: ExecutionNodeFinished) {
        runCatching { session.options.observer?.onNodeFinished(event) }
            .onFailure { failure -> plugin.logger.log(Level.WARNING, "[KantanCommander] node-finish observer failed", failure) }
    }

    /** ログのホバーへ出す人間向けのコマンド種別説明です。 */
    private fun immediateDetail(type: CommandType): String = when (type) {
        CommandType.TELEPORT -> "対象を指定位置へテレポートした"
        CommandType.GIVE_ITEM -> "対象へアイテムを付与した"
        CommandType.PLACE_ITEM -> "対象へアイテムを配置した"
        CommandType.ENTITY_ACTION -> "対象へエンティティ操作を行った"
        CommandType.ENTITY_OPERATION -> "対象のエンティティデータを変更した"
        CommandType.DISPLAY_TEXT -> "対象へテキストを表示した"
        CommandType.SUMMON_ENTITY -> "エンティティを召喚した"
        CommandType.PLAY_SOUND -> "サウンドを再生した"
        CommandType.APPLY_EFFECT -> "対象へ効果を適用した"
        CommandType.CAMERA_SHAKE -> "対象のカメラを揺らした"
        CommandType.BLOCK_OPERATION -> "ブロックを変更した"
        CommandType.ENTITY_DELETE -> "対象を削除した"
        else -> "コマンドを実行した"
    }

    private fun schedule(session: ExecutionSession, delayTicks: Long, action: () -> Unit) {
        if (!session.isActive) return
        lateinit var task: BukkitTask
        task = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            session.scheduledTasks.remove(task)
            if (session.isActive) action()
        }, delayTicks.coerceAtLeast(1L))
        session.scheduledTasks += task
    }

    private fun cancelSession(session: ExecutionSession, reason: String) {
        if (!session.isActive) return
        session.cancelled = true
        session.failureReason = reason
        session.scheduledTasks.toList().forEach(BukkitTask::cancel)
        session.scheduledTasks.clear()
        finishSession(session, ExecutionFinishStatus.CANCELLED, reason)
    }

    private fun finishSession(session: ExecutionSession, status: ExecutionFinishStatus, reason: String?) {
        if (session.finished) return
        session.finished = true
        session.scheduledTasks.toList().forEach(BukkitTask::cancel)
        session.scheduledTasks.clear()
        val elapsed = (plugin.server.currentTick.toLong() - session.startTick).coerceAtLeast(0L)
        val result = ExecutionResult(
            status = status,
            elapsedTicks = elapsed,
            successfulNodeCount = session.successfulRootNodeCount,
            attemptCount = session.attemptCount,
            failedNodeId = session.failedNodeId,
            reason = reason,
            waitTicks = session.waitTicks,
            debugWaitTicks = session.debugWaitTicks,
        )
        session.handle.complete()
        runCatching { session.options.observer?.onFinished(result) }
            .onFailure { failure -> plugin.logger.log(Level.WARNING, "[KantanCommander] finish observer failed", failure) }
        runCatching { session.callback(result) }
            .onFailure { failure -> plugin.logger.log(Level.WARNING, "[KantanCommander] execution callback failed", failure) }
    }

    private fun beginFor(
        script: DiskScript,
        graph: CommandGraph,
        node: CommandNode,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        val endId = node.pairedNodeId ?: return advanceNode(
            script, graph, node, session, depth, session.currentAttemptNumber, null,
            NodeExecutionOutcome.FAILED, done, "missing_for_end",
        )
        val count = resolvePositiveInt(node.string("count", "1"), session)
            ?: return advanceNode(
                script, graph, node, session, depth, session.currentAttemptNumber, null,
                NodeExecutionOutcome.FAILED, done, "invalid_for_count",
            )
        session.loops += LoopFrame(node.id, endId, count.toLong(), 1, session.context)
        session.currentLoopCount = 1
        session.lastDetail = "ループを開始した"
        advanceNode(
            script, graph, node, session, depth, session.currentAttemptNumber, node.trueNext,
            NodeExecutionOutcome.SUCCESS, done, reason = null,
        )
    }

    private fun finishForIteration(
        script: DiskScript,
        graph: CommandGraph,
        node: CommandNode,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        val frame = session.loops.lastOrNull { it.endId == node.id }
            ?: return advanceNode(
                script, graph, node, session, depth, session.currentAttemptNumber, null,
                NodeExecutionOutcome.FAILED, done, "for_frame_missing",
            )
        val startNode = graph.nodes[frame.startId]
            ?: return advanceNode(
                script, graph, node, session, depth, session.currentAttemptNumber, null,
                NodeExecutionOutcome.FAILED, done, "for_start_missing",
            )
        session.context = frame.startContext
        if (ExecutionSemantics.shouldRunNextLoopIteration(frame.count, frame.limit)) {
            frame.count = requireNotNull(ExecutionSemantics.nextLoopCount(frame.count))
            session.currentLoopCount = frame.count
            session.lastDetail = "ループを継続した"
            // 復帰後のS→body待機の表示期間にするため、デバッグ表示がある場合だけ
            // 追加待機します。通常実行とデバッグOFFでは現行動作のままです。
            val restartWait = if (depth == 0 && session.options.debugDelayTicks > 0L) {
                session.options.loopRestartExtraWaitTicks
            } else {
                0L
            }
            advanceNode(
                script, graph, node, session, depth, session.currentAttemptNumber, startNode.trueNext,
                NodeExecutionOutcome.SUCCESS, done, reason = null, loopReturn = true,
                followUpDelayTicks = restartWait,
            )
        } else {
            session.loops.remove(frame)
            session.currentLoopCount = session.loops.lastOrNull()?.count
            session.lastDetail = "ループを終了した"
            advanceNode(
                script, graph, node, session, depth, session.currentAttemptNumber, node.next,
                NodeExecutionOutcome.SUCCESS, done, reason = null,
            )
        }
    }

    private fun breakFor(
        script: DiskScript,
        graph: CommandGraph,
        node: CommandNode,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        val frame = session.loops.removeLastOrNull()
            ?: return advanceNode(
                script, graph, node, session, depth, session.currentAttemptNumber, null,
                NodeExecutionOutcome.FAILED, done, "break_outside_for",
            )
        session.context = frame.startContext
        session.currentLoopCount = session.loops.lastOrNull()?.count
        session.lastDetail = "ループを中断した"
        advanceNode(
            script, graph, node, session, depth, session.currentAttemptNumber, graph.nodes[frame.endId]?.next,
            NodeExecutionOutcome.SUCCESS, done, reason = null,
        )
    }

    private fun continueFor(
        script: DiskScript,
        graph: CommandGraph,
        node: CommandNode,
        session: ExecutionSession,
        depth: Int,
        done: (Boolean) -> Unit,
    ) {
        val frame = session.loops.lastOrNull()
            ?: return advanceNode(
                script, graph, node, session, depth, session.currentAttemptNumber, null,
                NodeExecutionOutcome.FAILED, done, "continue_outside_for",
            )
        session.context = frame.startContext
        session.lastDetail = "ループの次の反復へ進んだ"
        advanceNode(
            script, graph, node, session, depth, session.currentAttemptNumber, frame.endId,
            NodeExecutionOutcome.SUCCESS, done, reason = null,
        )
    }

    /**
     * issue #8 のディスク呼び出しを実行します。
     *
     * 呼び出しごとに呼び出し枠を作り、呼出元のEXECUTION変数表を退避します。
     * 引数は被呼出側の開始時点で定義済みのEXECUTION変数として渡します。
     * WORLD／ENTITY のstoreは複製しません。復帰時は枠を戻し、戻り値があれば
     * 受取変数へ設定します。循環呼出しは深さ上限の前に検出して失敗します。
     */
    private fun runDiskCall(node: CommandNode, session: ExecutionSession, depth: Int, done: (Boolean) -> Unit) {
        if (!ExecutionSemantics.withinCallDepth(depth, session.maxDepth)) {
            plugin.logger.warning("[KantanCommander] disk-call-depth root=${session.rootId} node=${node.id} depth=$depth max=${session.maxDepth}")
            return done(false)
        }
        val snapshot = node.snapshot ?: return done(false)
        val diskId = node.string("diskId").takeIf(String::isNotBlank)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        // 循環呼出しは深さ上限の前に検出します。diskId のない旧ノードは深さ制限に委ねます。
        if (diskId != null && session.callFrames.any { it.diskId == diskId }) {
            plugin.logger.warning("[KantanCommander] disk-call-circular root=${session.rootId} node=${node.id} disk=$diskId")
            session.lastDetail = "循環呼出しのため実行できなかった"
            session.lastFailureReason = ErrorCode.DISK_CALL_SIGNATURE_MISMATCH.code
            session.lastFailureCode = ErrorCode.DISK_CALL_SIGNATURE_MISMATCH
            return done(false)
        }
        val declared = node.declaredArguments()
        // 引数を解決します。変数指定があれば呼出元の値を使い、なければリテラルを型で解釈します。
        val arguments = linkedMapOf<String, TypedValue>()
        for (argument in declared) {
            val value = resolveCallArgument(node, argument, session) ?: run {
                session.lastDetail = "引数 ${argument.name} の解決に失敗した"
                session.lastFailureReason = ErrorCode.VARIABLE_UNSET.code
                session.lastFailureCode = ErrorCode.VARIABLE_UNSET
                return done(false)
            }
            arguments[argument.name] = value
        }
        val frame = ExecutionCallFrame(
            scriptId = session.rootId,
            nodeId = node.id,
            diskId = diskId,
            callerVariables = session.executionVariables.snapshot(),
            callerLoops = session.loops.map { it.copy() },
            callerLoopCount = session.currentLoopCount,
            returnScope = node.string("returnScope").takeIf(String::isNotBlank)
                ?.let(VariableScope::parse),
            returnName = node.string("returnName"),
        )
        session.callFrames.add(frame)
        session.executionVariables.clear()
        session.loops.clear()
        session.currentLoopCount = null
        // 引数は被呼出側の開始時点で定義済みのEXECUTION変数として渡します。
        // 呼出元の一時変数は暗黙共有しません。
        arguments.forEach { (name, value) -> session.executionVariables.set(name, value) }
        // 実行状態はDISK_CALLの境界を越えて共有しません。現在はGUIから
        // コンテキストを生成しませんが、内部呼出し元が保持している実行状態
        // を呼出先の処理へ渡す境界として、この保存・復元を残します。
        session.context = effectiveContext(session)
        val synthetic = DiskScript(name = "snapshot", owner = session.actor?.uniqueId ?: UUID(0, 0), graph = snapshot)
        runGraph(synthetic, snapshot, session, depth + 1) { success ->
            val popped = if (session.callFrames.isNotEmpty()) {
                session.callFrames.removeLast()
            } else null
            // 復帰時は呼出元の変数表・ループ状態へ戻します。
            if (popped != null) {
                session.executionVariables.restore(popped.callerVariables)
                session.loops.clear()
                session.loops.addAll(popped.callerLoops)
                session.currentLoopCount = popped.callerLoopCount
            }
            val pending = popped?.pendingReturn
            if (pending != null) {
                // 「値を返す」で中断された場合、残りを実行せず受取変数へ設定します。
                // 受取指定がなければ値を破棄します。型不一致は保存層の型安定で失敗します。
                val delivered = runCatching {
                    val targetScope = popped.returnScope
                    val targetName = popped.returnName
                    if (targetScope != null && targetName.isNotBlank()) {
                        when (targetScope) {
                            VariableScope.EXECUTION -> session.executionVariables.set(targetName, pending)
                            VariableScope.WORLD -> WorldVariableAdapter(plugin.variables, session.worldId).set(targetName, pending)
                            VariableScope.ENTITY -> return@runCatching false
                        }
                    }
                    true
                }.getOrDefault(false)
                session.returnRequested = false
                session.returnValue = null
                session.lastDetail = if (delivered) "値を返した" else session.lastDetail
                done(delivered && success)
            } else {
                done(success)
            }
        }
    }

    /**
     * issue #8 の呼出引数を解決します。
     *
     * 変数指定（`argvar_<name>`）があれば呼出元のEXECUTION変数を使い、なければ
     * リテラル（`arg_<name>`）を宣言型で解釈します。両方指定は検証で拒否済み
     * ですが、防御的に変数指定を優先します。失敗は null です。
     */
    private fun resolveCallArgument(
        node: CommandNode,
        argument: me.awabi2048.kantancommander.model.DiskArgument,
        session: ExecutionSession,
    ): TypedValue? = runCatching {
        val variable = node.string("argvar_${argument.name}").takeIf(String::isNotBlank)
        if (variable != null) {
            val value = session.executionVariables.get(
                me.awabi2048.kantancommander.model.TemporaryTemplate.normalized(variable),
            ) ?: error("argument variable is missing")
            if (value.type != argument.type) error("argument type mismatch")
            return value
        }
        val raw = node.string("arg_${argument.name}").takeIf(String::isNotBlank) ?: error("argument is missing")
        when (argument.type) {
            ValueType.NUMBER -> TypedValue(argument.type, numberValue = resolveNumber(raw, session) ?: error("number is invalid"))
            ValueType.STRING -> TypedValue(argument.type, stringValue = resolveText(raw, session) ?: error("string is invalid"))
            ValueType.BOOLEAN -> TypedValue(
                argument.type,
                booleanValue = resolveText(raw, session)?.trim()?.lowercase()?.let {
                    if (it == "true") true else if (it == "false") false else null
                } ?: error("boolean is invalid"),
            )
            ValueType.LOCATION,
            ValueType.VECTOR,
            ValueType.ITEM,
            ValueType.BLOCK,
            ValueType.ENTITY,
            ValueType.SOUND,
            ValueType.EFFECT,
            -> error("complex arguments require a variable")
        }
    }.getOrNull()

    /**
     * issue #8 の戻り値を解決します。
     *
     * source 指定があれば sourceScope の変数、なければ value 欄のリテラルを
     * 値型推論で解決します。失敗は null です。
     */
    private fun resolveReturnValue(node: CommandNode, session: ExecutionSession): TypedValue? = runCatching {
        val source = node.string("source").takeIf(String::isNotBlank)
        if (source != null) {
            val scope = VariableScope.parse(node.string("sourceScope")) ?: VariableScope.EXECUTION
            when (scope) {
                VariableScope.EXECUTION -> session.executionVariables.get(
                    me.awabi2048.kantancommander.model.TemporaryTemplate.normalized(source),
                ) ?: error("return variable is missing")
                VariableScope.WORLD -> plugin.variables.get(session.worldId, source) ?: error("return variable is missing")
                VariableScope.ENTITY -> error("entity return is unsupported")
            }
        } else {
            val raw = node.string("value")
            when (me.awabi2048.kantancommander.model.inferLiteralType(raw)) {
                ValueType.NUMBER -> TypedValue(ValueType.NUMBER, numberValue = resolveNumber(raw, session) ?: error("number is invalid"))
                ValueType.STRING -> TypedValue(ValueType.STRING, stringValue = resolveText(raw, session) ?: error("string is invalid"))
                ValueType.BOOLEAN -> TypedValue(
                    ValueType.BOOLEAN,
                    booleanValue = resolveText(raw, session)?.trim()?.lowercase()?.let {
                        if (it == "true") true else if (it == "false") false else null
                    } ?: error("boolean is invalid"),
                )
                else -> error("unsupported literal type")
            }
        }
    }.getOrNull()

    private fun executeImmediate(node: CommandNode, session: ExecutionSession): ImmediateExecutionResult = try {
        if (executeImmediateBoolean(node, session)) {
            if (session.lastDetail.isNullOrBlank()) session.lastDetail = immediateDetail(node.type)
            ImmediateExecutionResult.SUCCESS
        } else {
            // 全件非適用・全件失敗時も件数と理由を残します。件数接尾辞は
            // 部分適用があるときだけ付き、全件適用時と同様の表示にはなりません。
            // lastPartialを設定する操作以外には影響しません。
            if (session.lastDetail.isNullOrBlank()) {
                session.lastDetail = "${immediateDetail(node.type)}（実行に失敗）" +
                    partialDetailSuffix(session.actor, session.lastPartial)
            }
            session.lastFailureReason = session.lastFailureReason ?: ErrorCode.COMMAND_FAILED.code
            session.lastFailureCode = session.lastFailureCode ?: ErrorCode.COMMAND_FAILED
            ImmediateExecutionResult.FAILED
        }
    } catch (_: ParticleQuotaRejected) {
        // 上限超過は入力・実行失敗ではなく、このノードだけを送信せず後続へ進める
        // 制御結果です。通常の例外ログへ混ぜると、運用上の想定内の抑止が障害に見えます。
        session.lastDetail = "Particle上限超過のためスキップした"
        session.lastFailureReason = ErrorCode.PARTICLE_QUOTA_EXCEEDED.code
        session.lastFailureCode = ErrorCode.PARTICLE_QUOTA_EXCEEDED
        ImmediateExecutionResult.SKIPPED
    } catch (failure: Throwable) {
        val reason = failure.message?.takeIf(String::isNotBlank) ?: failure::class.simpleName ?: "command_failed"
        session.lastFailureReason = reason
        session.lastFailureCode = ErrorCode.fromLegacyReason(reason)
        session.lastDetail = "${immediateDetail(node.type)}（実行に失敗: $reason）" +
            partialDetailSuffix(session.actor, session.lastPartial)
        plugin.logger.log(Level.WARNING, "[KantanCommander] node failed root=${session.rootId} node=${node.id}", failure)
        ImmediateExecutionResult.FAILED
    }

    /**
     * issue #29: 複数位置の試行記録です。
     *
     * 各位置を独立に試行し、1件でも成功すれば全体成功とします。
     * 失敗は件数で記録し、試行なし（空集合）は成功扱いとします。
     */
    private class MultiAttemptRecorder {
        var succeeded: Int = 0
            private set
        var failed: Int = 0
            private set

        fun record(success: Boolean) {
            if (success) succeeded++ else failed++
        }

        /** 1件でも成功すれば成功です。全件失敗のときだけ失敗とします。 */
        val overall: Boolean get() = failed == 0 || succeeded > 0
    }

    private fun executeImmediateBoolean(node: CommandNode, session: ExecutionSession): Boolean = run {
        val ambient = effectiveContext(session)
        // issue #26・#29: 実行起点集合を求めます。解決不能は従来どおり失敗にします。
        val origins = effectiveExecutionOrigins(node, session, ambient) ?: return false
        // issue #29: 対象探索は各起点を基準に行い、結果を重複排除の和集合とします。
        val targets = origins.flatMap { origin ->
            resolveTargets(originContext(origin, ambient), node.targetSpec, session)
        }.distinctBy { it.uniqueId }
        val effectiveContext = origins.firstOrNull()?.let { originContext(it, ambient) } ?: ambient
        val effectiveOrigin = origins.firstOrNull() ?: session.origin
        // issue #29: 複数位置の試行記録です。位置依存の実行だけが使います。
        val attempts = MultiAttemptRecorder()
        val success = when (node.type) {
            CommandType.TELEPORT -> {
                if (targets.isEmpty()) return false
                val destination = node.destinationTargetSpec
                    ?.let { resolveTargetSpec(it, session, effectiveContext)?.location }
                    ?: node.destinationSpec?.let { resolvePosition(it, session, effectiveContext) }
                    ?: return false
                (node.destinationFacingSpec ?: effectiveContext?.facing)?.let {
                    applyFacing(destination, it, effectiveContext, session)
                }
                targets.all { it.teleport(destination.clone()) }
            }
            CommandType.GIVE_ITEM -> {
                val players = targets.filterIsInstance<Player>()
                if (players.isEmpty()) return false
                // 一時変数参照があれば最優先します。リテラルとの併記は検証で拒否します。
                val refTemp = node.itemTempRef?.takeIf(String::isNotBlank)
                    ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                    ?.takeIf { it.type == ValueType.ITEM }
                val material = if (refTemp != null) {
                    CommandValueRules.material(refTemp.item.orEmpty(), allowAir = false)
                } else {
                    CommandValueRules.material(node.string("item"), allowAir = false)
                } ?: return false
                val template = if (refTemp != null) {
                    refTemp.itemData?.takeIf(String::isNotBlank)?.let(ItemStackCodec::decode)
                        ?: ItemStack(material)
                } else {
                    ItemStackCodec.decode(node.string("itemData")) ?: ItemStack(material)
                }
                val count = resolvePositiveInt(node.string("count"), session) ?: return false
                players.all { player -> giveItem(player, template, count) }
            }
            CommandType.ENTITY_ACTION -> {
                if (targets.isEmpty()) return false
                when (node.string("action", "ride")) {
                    "ride" -> {
                        val vehicle = resolveTarget(effectiveContext, node.secondaryTargetSpec, session)
                            ?: return false
                        targets.all(vehicle::addPassenger)
                    }
                    "dismount" -> targets.all(Entity::leaveVehicle)
                    // 動き設定は現在速度を指定値へ設定する意味です。Bukkit／Paper APIの
                    // velocityとして扱い、生NBTは経由しません。将来の加算modeとは混在させません。
                    "motion" -> {
                        val x = CommandValueRules.parseFiniteDouble(node.string("motionX")) ?: return false
                        val y = CommandValueRules.parseFiniteDouble(node.string("motionY")) ?: return false
                        val z = CommandValueRules.parseFiniteDouble(node.string("motionZ")) ?: return false
                        val velocity = Vector(x, y, z)
                        targets.forEach { it.velocity = velocity.clone() }
                        true
                    }
                    // 旧equipは#21のPLACE_ITEMへ移行済みのため実行しません。
                    // 旧tagは#32のENTITY_OPERATIONへ移行済みのためここでは扱いません。
                    // いずれも検証層で移行漏れとして拒否されるため、到達時は失敗とします。
                    else -> false
                }
            }
            // issue #32 の「エンティティを操作」です。Entity自身の状態・データを変更します。
            //
            // 混合対象では対応する対象にだけ適用し、件数は構造化結果へ残します
            // （#6のPARTIAL_APPLY方針、レビューF4対応）。表示への変換は共通の
            // 成功・失敗処理が行い、1件も適用できなかった場合は失敗とします。
            // modifierの追加・削除はバニラの同名操作と等価です。
            CommandType.ENTITY_OPERATION -> {
                if (targets.isEmpty()) return false
                when (node.string("operation", "tag")) {
                    // tag実行本体は#32の分岐を維持します（旧ENTITY_ACTIONの重複は置きません）。
                    "tag" -> {
                        val tag = resolveText(node.string("tag"), session) ?: return false
                        // タグは単一値としてそのまま扱います。カンマを分割したり、
                        // 複数タグとして順番に適用したりしません。
                        if (!CommandValueRules.isTag(tag)) return false
                        when (node.string("tagOperation", "add")) {
                            // タグ追加は冪等な設定操作として扱います。既にタグを持つ対象が
                            // 混在していても、対象全体の処理を失敗扱いにしません。
                            "add" -> {
                                targets.forEach { it.addScoreboardTag(tag) }
                                true
                            }
                            "remove" -> targets.forEach { it.removeScoreboardTag(tag) }.let { true }
                            else -> false
                        }
                    }
                    "attribute" -> executeEntityAttribute(node, session, targets)
                    "data" -> executeEntityData(node, session, targets)
                    else -> false
                }
            }
            CommandType.PLACE_ITEM -> executePlaceItem(node, session, effectiveContext, targets, origins, attempts)
            CommandType.DISPLAY_TEXT -> {
                val players = targets.filterIsInstance<Player>()
                if (players.isEmpty()) return false
                val text = legacyText(resolveText(node.string("text"), session) ?: return false)
                val subtitle = legacyText(resolveText(node.string("subtitle"), session) ?: return false)
                when (node.string("mode", "tellraw")) {
                    "title" -> players.forEach { player ->
                        timedActionBar.cancel(player, clear = true)
                        val timing = displayTiming(node, session)
                        player.showTitle(Title.title(
                                text,
                                subtitle,
                                Title.Times.times(
                                    timing.fadeInDuration,
                                    timing.stayDuration,
                                    timing.fadeOutDuration,
                                ),
                            ))
                    }
                    "subtitle" -> players.forEach { player ->
                        timedActionBar.cancel(player, clear = true)
                        val timing = displayTiming(node, session)
                        player.showTitle(Title.title(
                            Component.empty(),
                            text,
                            Title.Times.times(
                                timing.fadeInDuration,
                                timing.stayDuration,
                                timing.fadeOutDuration,
                            ),
                        ))
                    }
                    "actionbar" -> players.forEach { player ->
                        timedActionBar.show(player, text, displayTiming(node, session))
                    }
                    else -> players.forEach { player ->
                        timedActionBar.cancel(player, clear = true)
                        player.sendMessage(text)
                    }
                }
                true
            }
            CommandType.SUMMON_ENTITY -> {
                if (!plugin.summonedEntities.canSummon(effectiveOrigin.world.uid)) return false
                val key = NamespacedKey.fromString(node.string("entity")) ?: return false
                val type = Registry.ENTITY_TYPE.get(key) ?: return false
                // 召喚タグは単一値です。空欄はタグなしとして扱い、カンマを
                // 複数タグの区切りに変換しないことでGUI・保存・実行の契約を揃えます。
                val tag = resolveText(node.string("tags"), session) ?: return false
                if (tag.isNotBlank() && !CommandValueRules.isTag(tag)) return false
                // 召喚位置が指定されていればそちらを優先し、未設定時は実行起点集合を使用します。
                // issue #29: 対象内包位置は各位置へ実行し、1件でも成功すれば全体成功とします。
                // 空集合は実行対象なしの成功、対象外種別の未解決は従来どおり失敗にします。
                val summonOrigins = node.summonPositionSpec?.let {
                    actionOrigins(it, session, effectiveContext) ?: return false
                } ?: origins
                summonOrigins.forEach { summonOrigin ->
                    attempts.record(runCatching {
                        val spawn = summonOrigin.clone()
                        effectiveContext?.facing?.let { applyFacing(spawn, it, effectiveContext, session) }
                        val entity = summonOrigin.world.spawnEntity(spawn, type)
                        try {
                            if (tag.isNotBlank()) entity.addScoreboardTag(tag)
                            val rawCustomName = node.string("customName")
                            val customName = resolveText(rawCustomName, session)
                                ?: error("custom name is unavailable")
                            if (customName.isNotEmpty()) {
                                entity.customName(legacyText(customName))
                                entity.isCustomNameVisible = true
                            }
                            plugin.summonedEntities.register(entity, session.rootId)
                        } catch (failure: Throwable) {
                            // 台帳へ登録できない召喚体をワールドへ残すと、制限をすり抜けた
                            // 孤児Entityになります。登録と実体生成を一組として扱います。
                            runCatching { entity.remove() }
                            throw failure
                        }
                    }.isSuccess)
                }
                attempts.overall
            }
            CommandType.PLAY_SOUND -> executeSound(node, session, effectiveContext, effectiveOrigin, targets, origins, attempts)
            CommandType.PARTICLE -> executeParticle(node, session, effectiveContext, effectiveOrigin, targets, origins, attempts)
            CommandType.PARTICLE_GEOMETRY -> executeParticleGeometry(node, session, effectiveContext, effectiveOrigin, targets, origins, attempts)
            CommandType.FIREWORK_LAUNCH -> executeFirework(node, session, effectiveContext, effectiveOrigin, origins, attempts)
            CommandType.APPLY_EFFECT -> {
                val refTemp = node.effectTempRef?.takeIf(String::isNotBlank)
                    ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                    ?.takeIf { it.type == ValueType.EFFECT }
                val key = NamespacedKey.fromString(refTemp?.effect ?: node.string("effect")) ?: return false
                val effect = Registry.EFFECT.get(key) ?: return false
                val applicable = targets.filterIsInstance<LivingEntity>()
                if (applicable.isEmpty()) return false
                val seconds = refTemp?.seconds
                    ?: resolvePositiveInt(node.string("seconds"), session) ?: return false
                val level = refTemp?.level
                    ?: resolvePositiveInt(node.string("level"), session) ?: return false
                applicable.forEach {
                    it.addPotionEffect(org.bukkit.potion.PotionEffect(
                        effect,
                        seconds * 20,
                        level - 1,
                    ))
                }
                true
            }
            CommandType.CAMERA_SHAKE -> {
                val intensity = resolveNumber(node.string("intensity", "1.0"), session)?.toFloat() ?: return false
                val seconds = resolveNumber(node.string("seconds", "5.0"), session)?.toFloat() ?: return false
                targets.filterIsInstance<Player>().forEach {
                    CameraShakeService.apply(
                        plugin,
                        it,
                        intensity,
                        seconds,
                        node.string("shakeType", "positional"),
                    )
                }
                true
            }
            CommandType.BLOCK_OPERATION -> executeBlockOperation(node, session, effectiveContext, origins, attempts)
            CommandType.ENTITY_DELETE -> {
                if (targets.isEmpty()) return false
                targets.forEach(Entity::remove)
                true
            }
            else -> true
        }
        if (success) {
            session.previousContext = effectiveContext
            val content = executionContent(node, targets, effectiveOrigin, session)
            // issue #32 のレビューF4対応：部分適用の件数は構造化結果から
            // 中央で表示へ変換します。操作側で詳細文字列を直接書かないことで、
            // 成功時の共通表示生成で件数が失われないようにします。
            session.lastDetail = content.head + partialDetailSuffix(session.actor, session.lastPartial)
            session.lastExpandedParams = content.expandedParams
            session.lastPositionText = content.positionText
            // issue #29: 複数位置の一部失敗は件数で記録します。
            if (attempts.failed > 0) {
                session.lastDetail = (session.lastDetail ?: "") + "（${attempts.failed}件の位置で失敗）"
            }
        }
        success
    }

    /**
     * issue #32 のレビューF4対応：部分適用件数の表示変換です。
     *
     * 件数の有無判定は [PartialApplicability.displayVars] が担い、ここでは
     * 型付き言語キーへの解決だけを行います。実行者のlocaleで解決し、
     * テスト実行表示の詳細行へ残します。全件適用時は空文字を返します。
     */
    private fun partialDetailSuffix(actor: Player?, partial: PartialApplicability?): String {
        val vars = partial?.displayVars() ?: return ""
        return KcI18n.text(
            actor,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_TEST_DETAIL_PARTIAL_APPLY,
            vars,
        )
    }

    /**
     * issue #32 のattribute操作を実行します。
     *
     * 対象がattributeを持つかはcapabilityとして判定し、対応しない対象は
     * 非適用として数えます。基本値の設定・modifierの追加・削除を型付きで扱い、
     * 曖昧な「デフォルトへ戻す」は用意しません。
     * 適用数が0件の場合は失敗とし、設定値自体の不正とも区別します。
     */
    private fun executeEntityAttribute(
        node: CommandNode,
        session: ExecutionSession,
        targets: List<Entity>,
    ): Boolean {
        val attribute = EntityAttributeSupport.resolve(node.string(EntityAttributeSupport.PARAM_ATTRIBUTE))
            ?: return false
        val operation = node.string(EntityAttributeSupport.PARAM_OPERATION, EntityAttributeSupport.OPERATION_BASE_VALUE)
        if (!EntityAttributeSupport.isOperation(operation)) return false
        var applied = 0
        var nonApplicable = 0
        var failed = 0
        targets.forEach { target ->
            val instance = EntityAttributeSupport.instanceOf(target, attribute)
            if (instance == null) {
                nonApplicable++
                return@forEach
            }
            val success = when (operation) {
                EntityAttributeSupport.OPERATION_BASE_VALUE -> {
                    val value = CommandValueRules.parseFiniteDouble(node.string(EntityAttributeSupport.PARAM_AMOUNT))
                        ?: return@forEach run { failed++ }
                    EntityAttributeSupport.setBaseValue(instance, value)
                }
                EntityAttributeSupport.OPERATION_MODIFIER_ADD -> {
                    val key = EntityAttributeSupport.modifierKey(node.string(EntityAttributeSupport.PARAM_MODIFIER_KEY))
                        ?: return@forEach run { failed++ }
                    val amount = CommandValueRules.parseFiniteDouble(node.string(EntityAttributeSupport.PARAM_AMOUNT))
                        ?: return@forEach run { failed++ }
                    val modifierOperation = EntityAttributeSupport.modifierOperation(
                        node.string(EntityAttributeSupport.PARAM_MODIFIER_OPERATION),
                    ) ?: return@forEach run { failed++ }
                    EntityAttributeSupport.addModifier(instance, key, amount, modifierOperation)
                }
                EntityAttributeSupport.OPERATION_MODIFIER_REMOVE -> {
                    val key = EntityAttributeSupport.modifierKey(node.string(EntityAttributeSupport.PARAM_MODIFIER_KEY))
                        ?: return@forEach run { failed++ }
                    // 削除対象の不在は失敗とします（バニラの `modifier remove` と等価）。
                    // 対象自体がattributeを持たない場合だけが非適用です。
                    EntityAttributeSupport.removeModifier(instance, key)
                }
                else -> false
            }
            if (success) applied++ else failed++
        }
        // issue #32 のレビューF4対応：件数は構造化結果として保持し、
        // 詳細文字列への変換は共通成功処理へ委ねます。
        session.lastPartial = PartialApplicability(
            appliedCount = applied,
            nonApplicableCount = nonApplicable,
            failedCount = failed,
        )
        return applied > 0
    }

    /**
     * issue #32 の型付きデータ編集（#10スキーマ）を実行します。
     *
     * 任意NBTではなくスキーマ定義の項目だけを適用し、対応しない対象は
     * 非適用として数えます。表示名は召喚時と同じ描画規則で適用します。
     */
    private fun executeEntityData(
        node: CommandNode,
        session: ExecutionSession,
        targets: List<Entity>,
    ): Boolean {
        val propertyId = node.string(EntityDataSchema.PARAM_PROPERTY)
        val definition = EntityDataSchema.definition(propertyId) ?: return false
        val raw = node.string(EntityDataSchema.PARAM_VALUE)
        val parsed = EntityDataSchema.parse(propertyId, raw) ?: return false
        // 表示名はテンプレート参照を解決します。解決不能は失敗とします。
        val resolvedText = if (parsed is EntityPropertyValue.TextValue) {
            resolveText(parsed.text, session) ?: return false
        } else null
        var applied = 0
        var nonApplicable = 0
        var failed = 0
        targets.forEach { target ->
            if (!EntityDataSchema.supports(target, propertyId)) {
                nonApplicable++
                return@forEach
            }
            val success = when (parsed) {
                is EntityPropertyValue.BooleanValue ->
                    EntityDataSchema.applyBoolean(target, propertyId, parsed.value)
                is EntityPropertyValue.EnumValue ->
                    EntityDataSchema.applyEnum(target, propertyId, parsed.choiceId)
                is EntityPropertyValue.TextValue -> applyCustomName(target, propertyId, resolvedText.orEmpty())
            }
            if (success) applied++ else failed++
        }
        // issue #32 のレビューF4対応：件数は構造化結果として保持し、
        // 詳細文字列への変換は共通成功処理へ委ねます。
        session.lastPartial = PartialApplicability(
            appliedCount = applied,
            nonApplicableCount = nonApplicable,
            failedCount = failed,
        )
        return applied > 0
    }

    /**
     * 表示名を適用します。召喚時の表示名と同じ規則であり、空欄は表示名の
     * 消去として扱います。TEXT種別の項目は現行では表示名だけです。
     */
    private fun applyCustomName(target: Entity, propertyId: String, text: String): Boolean {
        if (propertyId != EntityDataSchema.PROPERTY_CUSTOM_NAME) return false
        return runCatching {
            if (text.isBlank()) {
                target.customName(null)
                target.isCustomNameVisible = false
            } else {
                target.customName(legacyText(text))
                target.isCustomNameVisible = true
            }
            true
        }.getOrDefault(false)
    }

    /**
     * 効果音の再生・停止を実行します。
     *
     * 受信者は対象プレイヤー（targetSpec）があれば解決結果、なければ従来どおり
     * 起動ワールドの全プレイヤーです。エンティティ対象は再生できないため除外
     * します。停止対象の音が存在しない場合は正常no-opとし、失敗にしません。
     */
    private fun executeSound(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        effectiveOrigin: Location,
        targets: List<Entity>,
        origins: List<Location>,
        attempts: MultiAttemptRecorder,
    ): Boolean {
        val receivers = soundReceivers(node, effectiveOrigin, targets)
        when (SoundSettings.operation(node)) {
            SoundOperation.PLAY -> {
                val refTemp = node.soundTempRef?.takeIf(String::isNotBlank)
                    ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                    ?.takeIf { it.type == ValueType.SOUND }
                val sound = refTemp?.sound ?: node.string("sound")
                if (NamespacedKey.fromString(sound) == null) return false
                val volume = (refTemp?.volume ?: resolveNumber(node.string("volume", "1.0"), session))
                    ?.toFloat() ?: return false
                val pitch = (refTemp?.pitch ?: resolveNumber(node.string("pitch", "1.0"), session))
                    ?.toFloat() ?: return false
                if (node.string("soundScope", "POSITION") == "WORLD") {
                    // 全域指定は各プレイヤー位置を音源位置にするため、現行の
                    // 「起動ワールドの全プレイヤーへ確実に届ける」挙動を維持します。
                    receivers.forEach { it.playSound(it.location, sound, SoundCategory.MASTER, volume, pitch) }
                } else {
                    // issue #29: 対象内包位置は各位置へ実行し、1件でも成功すれば全体成功とします。
                    val sources = node.soundPositionSpec?.let {
                        actionOrigins(it, session, context) ?: return false
                    } ?: origins
                    sources.forEach { source ->
                        attempts.record(runCatching {
                            receivers.forEach { it.playSound(source, sound, SoundCategory.MASTER, volume, pitch) }
                        }.isSuccess)
                    }
                    return attempts.overall
                }
                return true
            }
            SoundOperation.STOP -> {
                val refTemp = node.soundTempRef?.takeIf(String::isNotBlank)
                    ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                    ?.takeIf { it.type == ValueType.SOUND }
                val sound = (refTemp?.sound ?: node.string("sound")).takeIf(String::isNotBlank)
                if (sound != null && NamespacedKey.fromString(sound) == null) return false
                val category = SoundSettings.category(node.string("soundCategory"))
                receivers.forEach { player ->
                    when {
                        sound != null && category != null -> player.stopSound(sound, category)
                        sound != null -> stopSoundAllCategories(player, sound)
                        category != null -> player.stopSound(category)
                        else -> stopAllSounds(player)
                    }
                }
                return true
            }
        }
    }

    /** 対象プレイヤー指定があれば解決結果、なければ起動ワールド全員を返します。 */
    private fun soundReceivers(
        node: CommandNode,
        effectiveOrigin: Location,
        targets: List<Entity>,
    ): List<Player> {
        if (node.targetSpec == null) return effectiveOrigin.world.players
        return targets.filterIsInstance<Player>()
    }

    /** Paper APIに単音全停止のoverloadはないため、全カテゴリへ順に停止要求を送ります。 */
    private fun stopSoundAllCategories(player: Player, sound: String) {
        SoundCategory.entries.forEach { category ->
            runCatching { player.stopSound(sound, category) }
        }
    }

    private fun stopAllSounds(player: Player) {
        SoundCategory.entries.forEach { category ->
            runCatching { player.stopSound(category) }
        }
    }

    /**
     * 通常表示（PARTICLE）を実行します。
     *
     * #19の回帰対象として散布（delta／speed／count）送信を維持します。
     * 粒子種類・詳細データ・表示対象の解決は図形表示と共通です。
     * issue #29: 各位置を独立に試行し、1件でも成功すれば全体成功とします。
     */
    private fun executeParticle(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        effectiveOrigin: Location,
        targets: List<Entity>,
        origins: List<Location>,
        attempts: MultiAttemptRecorder,
    ): Boolean {
        val resolved = resolveParticleKind(node, effectiveOrigin, targets) ?: return false
        // issue #29: コマンド固有位置があればそちらを優先し、なければ実行起点集合を使います。
        // 通常表示は複数位置を許可し、各位置で同じパーティクルを表示します。
        val centers = node.particlePositionSpec?.let {
            actionOrigins(it, session, context) ?: return false
        } ?: origins
        val deltaX = resolveNumber(node.string(ParticleSettings.PARAM_DELTA_X, "0.0"), session)
            ?.takeIf(Double::isFinite) ?: return false
        val deltaY = resolveNumber(node.string(ParticleSettings.PARAM_DELTA_Y, "0.0"), session)
            ?.takeIf(Double::isFinite) ?: return false
        val deltaZ = resolveNumber(node.string(ParticleSettings.PARAM_DELTA_Z, "0.0"), session)
            ?.takeIf(Double::isFinite) ?: return false
        val speed = resolveNumber(node.string(ParticleSettings.PARAM_SPEED, "0.0"), session)
            ?.takeIf { it.isFinite() && it >= 0.0 } ?: return false
        val count = resolvePositiveInt(node.string(ParticleSettings.PARAM_COUNT, "1"), session) ?: return false
        // 上限超過は失敗ではなくスキップ扱いのため、全位置分を事前予約し、
        // 不足時は送信せずスキップします。送信時の個別失敗は記録します。
        if (centers.isNotEmpty() &&
            !plugin.particleQuota.tryAcquire(effectiveOrigin.world.uid, count * centers.size)
        ) {
            throw ParticleQuotaRejected()
        }
        // receiverへ明示対象を渡すことで、「実行位置周囲」ではなく
        // 「表示中心と同じワールドの全員または指定対象」という仕様を残します。
        centers.forEach { origin ->
            attempts.record(runCatching {
                origin.world.spawnParticle(
                    resolved.particle,
                    resolved.receivers,
                    null,
                    origin.x,
                    origin.y,
                    origin.z,
                    count,
                    deltaX,
                    deltaY,
                    deltaZ,
                    speed,
                    resolved.bukkitData,
                    true,
                )
            }.isSuccess)
        }
        return attempts.overall
    }

    /**
     * 図形表示（PARTICLE_GEOMETRY）を実行します。
     *
     * ParticleGeometryで点列を生成し、各生成点を共通送信処理で表示します。
     * 図形を構成する位置は原則として単一位置として扱います（#29）。
     */
    private fun executeParticleGeometry(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        effectiveOrigin: Location,
        targets: List<Entity>,
        origins: List<Location>,
        attempts: MultiAttemptRecorder,
    ): Boolean {
        val resolved = resolveParticleKind(node, effectiveOrigin, targets) ?: return false
        val centers = node.particlePositionSpec?.let {
            actionOrigins(it, session, context) ?: return false
        } ?: origins
        centers.forEach { origin ->
            attempts.record(
                runCatching {
                    executeGeometryParticle(node, session, context, origin, resolved.receivers, resolved.particle, resolved.bukkitData)
                }.getOrElse { failure ->
                    // 上限超過はスキップ扱いを維持し、他は失敗として記録します。
                    if (failure is ParticleQuotaRejected) throw failure
                    false
                },
            )
        }
        return attempts.overall
    }

    /**
     * 粒子種類・詳細データ・表示対象の共通解決です。通常表示と図形表示で共有します。
     *
     * Particleの送信は、対象プレイヤー指定がなければ表示中心と同じワールドの
     * 全プレイヤーへ1回のパケット送信で届けます。
     */
    private data class ResolvedParticle(
        val particle: org.bukkit.Particle,
        val receivers: List<Player>,
        val bukkitData: Any?,
    )

    private fun resolveParticleKind(
        node: CommandNode,
        effectiveOrigin: Location,
        targets: List<Entity>,
    ): ResolvedParticle? {
        val particle = ParticleSettings.particle(node) ?: return null
        val receivers = if (node.targetSpec == null) effectiveOrigin.world.players else targets.filterIsInstance<Player>()
        val data = if (ParticleSettings.requiresData(particle)) {
            ParticleSettings.parseData(particle, node.string(ParticleSettings.PARAM_DATA))
                .getOrNull() ?: return null
        } else {
            // 詳細データを必要としない種類は保存値を無視し、旧保存の残存値で
            // 実行を止めません。検証側も同じく対象外にしています。
            ParticleSettings.ParticleDataSpec.None
        }
        val bukkitData = data.toBukkitData(effectiveOrigin)
        if (ParticleSettings.requiresData(particle) && bukkitData == null) return null
        return ResolvedParticle(particle, receivers, bukkitData)
    }

    /**
     * 図形表示を実行します。
     *
     * 生成点列を事前に見積もり、合計点でquotaを1回予約してから送信します。
     * 拒否時は部分表示を残さずスキップ扱い（ParticleQuotaRejected）とします。
     */
    private fun executeGeometryParticle(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        origin: Location,
        receivers: List<Player>,
        particle: org.bukkit.Particle,
        bukkitData: Any?,
    ): Boolean {
        val shape = ParticleGeometry.shape(node)
        if (shape == ParticleShape.POINT_SCATTER) return false
        resolveNumber(node.string(ParticleGeometry.PARAM_DENSITY, ParticleGeometry.DENSITY_DEFAULT.toString()), session)
            ?.takeIf(Double::isFinite) ?: return false
        val resolved = resolveGeometry(node, session, context, origin) ?: return false
        val points = ParticleGeometry.generate(shape, resolved).take(ParticleGeometry.MAX_POINTS)
        if (points.isEmpty()) return false
        if (!plugin.particleQuota.tryAcquire(origin.world.uid, points.size)) {
            throw ParticleQuotaRejected()
        }
        points.forEach { (ox, oy, oz) ->
            origin.world.spawnParticle(
                particle,
                receivers,
                null,
                origin.x + ox,
                origin.y + oy,
                origin.z + oz,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                bukkitData,
                true,
            )
        }
        return true
    }

    /** 図形パラメータを実行時値へ解決します。テンプレート値は数値解決へ委ねます。 */
    private fun resolveGeometry(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        origin: Location,
    ): ParticleGeometry.ResolvedGeometry? {
        fun number(key: String, default: Double): Double? =
            resolveNumber(node.string(key, default.toString()), session)?.takeIf(Double::isFinite)
        // issue #29: 終点も始点と同じく単一位置へ解決します。対象内包の静的単一は
        // 先頭1件へ解決し、複数・対象なしは検証と同じく未解決として図形全体を
        // 失敗させます。resolvePosition はTARGET種別を一律未解決にするため、
        // ここでは使わず resolveSinglePosition を使います。
        val endOffset = node.particleEndPositionSpec
            ?.let { resolveSinglePosition(it, session, context) ?: return null }
            ?.let { Triple(it.x - origin.x, it.y - origin.y, it.z - origin.z) }
        return ParticleGeometry.ResolvedGeometry(
            radius = number(ParticleGeometry.PARAM_RADIUS, 3.0) ?: return null,
            height = number(ParticleGeometry.PARAM_HEIGHT, 3.0) ?: return null,
            turns = number(ParticleGeometry.PARAM_TURNS, 2.0) ?: return null,
            startAngle = number(ParticleGeometry.PARAM_START_ANGLE, 0.0) ?: return null,
            endAngle = number(ParticleGeometry.PARAM_END_ANGLE, 360.0) ?: return null,
            rotation = number(ParticleGeometry.PARAM_ROTATION, 0.0) ?: return null,
            axisYaw = number(ParticleGeometry.PARAM_AXIS_YAW, 0.0) ?: return null,
            axisPitch = number(ParticleGeometry.PARAM_AXIS_PITCH, -90.0) ?: return null,
            step = number(ParticleGeometry.PARAM_STEP, 0.5) ?: return null,
            wireframe = node.params[ParticleGeometry.PARAM_WIREFRAME]?.toBooleanStrictOrNull() ?: true,
            endOffset = endOffset,
        )
    }

    /**
     * 設定済み花火を打ち上げます。
     *
     * バニラの上昇運動（初速0.05・毎tick＋0.04・XZ恒久0）をそのまま使い、
     * 検証済み逆変換式で求めたtickにワンショットタスクで起爆判定します。
     * 寿命バックストップで無限飛行を防ぎ、召喚体台帳へ登録します
     * （SUMMON_ENTITYと同一契約）。発射音・軌跡・起爆演出は全自動です。
     */
    private fun executeFirework(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        effectiveOrigin: Location,
        origins: List<Location>,
        attempts: MultiAttemptRecorder,
    ): Boolean {
        if (!plugin.summonedEntities.canSummon(effectiveOrigin.world.uid)) return false
        val height = resolveNumber(
            node.string(FireworkSettings.PARAM_HEIGHT, FireworkSettings.HEIGHT_DEFAULT.toString()),
            session,
        )?.takeIf(Double::isFinite) ?: return false
        val stack = resolveFireworkStack(node) ?: return false
        // issue #29: コマンド固有位置があればそちらを優先し、なければ実行起点集合を使います。
        // 各位置を独立に試行し、1件でも成功すれば全体成功とします。
        val launchBases = node.fireworkPositionSpec?.let {
            actionOrigins(it, session, context) ?: return false
        } ?: origins
        // 制御ブロック位置（未設定を含む）は固体内部のため、そのまま召喚すると
        // 初tickで接触起爆します。装置上端を発射口として1ブロック上げます。
        // 明示座標は利用者指定のままバニラ通りに扱います。
        val explicitKind = node.fireworkPositionSpec?.kind
        launchBases.forEach { launchBase ->
            attempts.record(runCatching {
                val launchAt = launchBase.clone().apply {
                    if (explicitKind == null || explicitKind == PositionKind.DISK) add(0.0, 1.0, 0.0)
                }
                launchFireworkRocket(session, node, stack, height, launchAt)
            }.isSuccess)
        }
        return attempts.overall
    }

    /**
     * issue #29 の花火1発分の打上げです。位置集合の各位置から呼び出します。
     *
     * 高さ0は演出なしの即時破裂です。
     */
    private fun launchFireworkRocket(
        session: ExecutionSession,
        node: CommandNode,
        stack: org.bukkit.inventory.ItemStack,
        height: Double,
        launchAt: org.bukkit.Location,
    ) {
        // 高さ0は演出なしの即時破裂です。
        if (height <= 0.0) {
            val instant = launchAt.world.spawn(launchAt.clone(), org.bukkit.entity.Firework::class.java)
            instant.setItem(stack)
            instant.setShotAtAngle(false)
            instant.detonate()
            return
        }
        val rocket = launchAt.world.spawn(launchAt.clone(), org.bukkit.entity.Firework::class.java)
        rocket.setItem(stack)
        rocket.setShotAtAngle(false)
        // 初速を厳密値で上書きし、XZドリフト（0×1.15の恒久0）を確定します。
        rocket.velocity = org.bukkit.util.Vector(
            0.0,
            FireworkSettings.INITIAL_UPWARD_VELOCITY,
            0.0,
        )
        rocket.ticksToDetonate = FireworkSettings.backstopLifetime(height)
        try {
            plugin.summonedEntities.register(rocket, session.rootId)
        } catch (failure: Throwable) {
            rocket.remove()
            throw failure
        }
        val target = launchAt.clone().add(0.0, height, 0.0)
        schedule(session, FireworkSettings.detonationTicks(height).toLong()) {
            detonateAtTarget(rocket, target)
        }
    }

    /**
     * ワンショット起爆判定です。
     *
     * 発火時点で正確な目標座標へテレポート補正して即時起爆するため、高度は
     * 厳密です。チャンク無効・実体無効・起爆済みの場合は20tick後に再試行し、
     * バックストップ到達まで繰り返します。
     */
    private fun detonateAtTarget(rocket: org.bukkit.entity.Firework, target: Location, retriesLeft: Int = 5) {
        val world = target.world
        if (!rocket.isValid || rocket.isDetonated || world == null ||
            !world.isChunkLoaded(target.blockX shr 4, target.blockZ shr 4)
        ) {
            if (retriesLeft > 0) {
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    detonateAtTarget(rocket, target, retriesLeft - 1)
                }, 20L)
            } else {
                runCatching { rocket.remove() }
            }
            return
        }
        // XZの残留ブレもここで消し、完全垂直の到達点で破裂させます。
        rocket.teleport(target)
        rocket.detonate()
    }

    /** 花火アイテムを保存値から復元します。 */
    private fun resolveFireworkStack(node: CommandNode): ItemStack? {
        val data = node.string(FireworkSettings.PARAM_DATA)
        val decoded = data.takeIf(String::isNotBlank)?.let { ItemStackCodec.decode(it) }
        val stack = decoded ?: node.string(FireworkSettings.PARAM_ITEM).takeIf(String::isNotBlank)?.let { raw ->
            CommandValueRules.material(raw, allowAir = false)?.let { ItemStack(it) }
        } ?: return null
        if (stack.type != org.bukkit.Material.FIREWORK_ROCKET) return null
        return stack.clone()
    }

    /** 実行後のホバーへ、解決済み対象と展開後の設定値をまとめて表示します。 */
    /** 実行内容の人向け要約です。実効値と位置はホバーの構造表示用に分けて保持します。 */
    private data class ExecutionContent(
        val head: String,
        val expandedParams: Map<String, String> = emptyMap(),
        val positionText: String? = null,
    )

    private fun executionContent(
        node: CommandNode,
        targets: List<Entity>,
        origin: Location,
        session: ExecutionSession,
    ): ExecutionContent {
        val head = buildString {
            append(immediateDetail(node.type))
            if (targets.isNotEmpty()) {
                append("（対象: ")
                append(targets.joinToString("、") { entity -> entity.name })
                append('）')
            }
        }
        val expandedParams = node.params.entries
            .filter { (_, value) -> value.isNotBlank() }
            .associate { (key, value) -> key to (resolveText(value, session) ?: value) }
        return ExecutionContent(head, expandedParams, formatLocation(origin))
    }

    // テスト実行は常に操作者のいるMyWorldで行うため、ワールド名は表示しません。
    private fun formatLocation(location: Location): String =
        "(${location.blockX}, ${location.blockY}, ${location.blockZ})"

    private fun giveItem(player: Player, template: ItemStack, count: Int): Boolean {
        var remaining = count
        while (remaining > 0) {
            val stack = template.clone()
            val batch = minOf(remaining, stack.maxStackSize)
            stack.amount = batch
            val overflow = player.inventory.addItem(stack)
            remaining -= batch
            if (overflow.isNotEmpty()) {
                // 付与量が容量を超えた場合は、Minecraftの通常の付与と同じく
                // 空きスロットへ入った分だけを残し、超過分は破棄します。
                // 既に入った分まで巻き戻すと、複数対象時に結果が不自然になります。
                return true
            }
        }
        return true
    }

    /**
     * ブロック操作をBukkitのブロック更新へ一元化します。
     *
     * setblock/fill相当の入力は、座標解決・ワールド一致・件数上限を同じ境界で
     * 検証してから更新します。fillを無制限に受け付けると、1ノードだけでサーバー
     * メインスレッドを長時間占有できるため、実行時にも上限を再確認します。
     */
    /**
     * issue #26・#29: 単一位置の既定値として実行起点を使います。
     *
     * コマンド固有位置があればそちらを優先し、未設定時は実行起点が
     * 1件の場合だけ既定値とします。起点が0件・複数件の場合は単一へ
     * 定まらないためnullであり、呼び出し側で失敗にします。
     */
    private fun commandPositionOrExecution(
        spec: PositionSpec?,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        origins: List<Location>,
    ): Location? {
        spec?.let { return resolveSinglePosition(it, session, context) }
        return origins.singleOrNull()
    }

    /**
     * 「アイテムを配置する」(PLACE_ITEM) を実行します。
     *
     * バニラ `/item replace entity|block` に相当し、配置先の capability に応じて
     * adapter を解決します。EntityType の分岐を増やさず、対象ごとの適用可否で
     * 判定します。`from` によるスロット間コピーは持たず、配置アイテムと個数の
     * 変数指定で代替します。
     *
     * 個数は素材のスタック上限以下でなければ失敗とします。黙って寄せると
     * バニラ出力の失敗と食い違うためです。
     * 成功定義は全配置先で「1件以上適用なら成功」に統一します。
     */
    private fun executePlaceItem(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        targets: List<Entity>,
        origins: List<Location>,
        attempts: MultiAttemptRecorder,
    ): Boolean {
        // 一時変数参照があれば最優先します。リテラルとの併記は検証で拒否します。
        val refTemp = node.itemTempRef?.takeIf(String::isNotBlank)
            ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
            ?.takeIf { it.type == ValueType.ITEM }
        val material = if (refTemp != null) {
            CommandValueRules.material(refTemp.item.orEmpty(), allowAir = false)
        } else {
            CommandValueRules.material(node.string("item"), allowAir = false)
        } ?: return false
        val template = if (refTemp != null) {
            refTemp.itemData?.takeIf(String::isNotBlank)?.let(ItemStackCodec::decode)
                ?: ItemStack(material)
        } else {
            ItemStackCodec.decode(node.string("itemData")) ?: ItemStack(material)
        }
        val count = resolvePositiveInt(node.string("count"), session) ?: return false
        // 個数は素材のスタック上限以下でなければ失敗とします。黙って寄せると
        // バニラ出力の失敗と食い違うためです（出力も上限超過で失敗します）。
        // リテラル超過は検証層でも早期エラーにします。変数・一時参照の超過は
        // 実行時にここで失敗し、出力側も実行時に失敗するため等価です。
        val stack = template.clone()
        if (count > stack.maxStackSize) return false
        stack.amount = count
        val destination = PlaceItemDestination.from(node.string("destination")) ?: return false
        val slot = node.string("slot")
        if (!PlaceItemDestination.isValidSlot(destination, slot)) return false
        val overwrite = node.boolean("overwrite")
        // F5: 成功定義を全配置先で「1件以上適用なら成功」へ統一します。
        // ブロック系も書込みなしの無操作は非適用とし、例外なしでも成功にしません。
        // 空集合・全件非適用・全件失敗はいずれも失敗とします。
        // 件数は構造化結果へ残し、共通の成功・失敗処理が型付き文言で表示します（F4）。
        if (destination == PlaceItemDestination.BLOCK_CONTAINER) {
            val index = slot.toIntOrNull() ?: return false
            // コマンド固有位置がなければ起点集合を使い、各位置を独立に試行します。
            val locations = node.blockPositionSpec?.let {
                actionOrigins(it, session, context) ?: return false
            } ?: origins
            var applied = 0
            var nonApplicable = 0
            var failed = 0
            locations.forEach { location ->
                val outcome = runCatching {
                    val world = location.world ?: error("world is unavailable")
                    if (plugin.placements.isRegistered(world, location.blockX, location.blockY, location.blockZ)) {
                        // 拡張コマンドブロックの位置は、配置先としても常に変更を拒否します。
                        error("placement is protected")
                    }
                    val inventory = (location.block.state as? InventoryHolder)?.inventory
                        ?: error("block is not a container")
                    if (index !in 0 until inventory.size) error("container slot is out of range")
                    if (overwrite || isEmptyStack(inventory.getItem(index))) {
                        inventory.setItem(index, stack.clone())
                        true
                    } else {
                        false
                    }
                }
                outcome.onSuccess { placed ->
                    if (placed) {
                        applied++
                        attempts.record(true)
                    } else {
                        // 上書き拒否による無操作は失敗でなく非適用とし、
                        // 位置失敗件数へは数えません。
                        nonApplicable++
                    }
                }.onFailure {
                    failed++
                    attempts.record(false)
                }
            }
            session.lastPartial = PartialApplicability(applied, nonApplicable, failed)
            return applied > 0
        }
        if (targets.isEmpty()) return false
        var applied = 0
        var nonApplicable = 0
        var failed = 0
        targets.forEach { target ->
            // 対象種別の不一致・上書き拒否は非適用、設定値の不正は失敗とします。
            val placed = when (destination) {
                PlaceItemDestination.EQUIPMENT -> {
                    val bukkitSlot = runCatching { EquipmentSlot.valueOf(slot) }.getOrNull()
                    val equipment = (target as? LivingEntity)?.equipment
                    if (bukkitSlot == null) {
                        failed++
                        false
                    } else if (equipment == null) {
                        nonApplicable++
                        false
                    } else if (overwrite || isEmptyStack(equipment.getItem(bukkitSlot))) {
                        equipment.setItem(bukkitSlot, stack.clone())
                        true
                    } else {
                        nonApplicable++
                        false
                    }
                }
                PlaceItemDestination.PLAYER_HOTBAR, PlaceItemDestination.PLAYER_INVENTORY -> {
                    val player = target as? Player
                    val bukkitSlot = PlaceItemDestination.bukkitPlayerSlot(destination, slot)
                    if (bukkitSlot == null) {
                        failed++
                        false
                    } else if (player == null) {
                        nonApplicable++
                        false
                    } else if (overwrite || isEmptyStack(player.inventory.getItem(bukkitSlot))) {
                        player.inventory.setItem(bukkitSlot, stack.clone())
                        true
                    } else {
                        nonApplicable++
                        false
                    }
                }
                PlaceItemDestination.ITEM_FRAME -> {
                    // GlowItemFrame は ItemFrame を継承するため、同じ経路で扱います。
                    // 回転等の表示設定はこの操作の対象外です。
                    val frame = target as? ItemFrame
                    if (frame == null) {
                        nonApplicable++
                        false
                    } else if (overwrite || isEmptyStack(frame.item)) {
                        frame.setItem(stack.clone())
                        true
                    } else {
                        nonApplicable++
                        false
                    }
                }
                PlaceItemDestination.BLOCK_CONTAINER -> false
            }
            if (placed) applied++
        }
        session.lastPartial = PartialApplicability(applied, nonApplicable, failed)
        return applied > 0
    }

    private fun executeBlockOperation(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        origins: List<Location>,
        attempts: MultiAttemptRecorder,
    ): Boolean {
        val refTemp = node.blockTempRef?.takeIf(String::isNotBlank)
            ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
            ?.takeIf { it.type == ValueType.BLOCK }
        val material = if (refTemp != null) {
            CommandValueRules.placementMaterial(refTemp.block.orEmpty())
        } else {
            CommandValueRules.placementMaterial(node.string("block"))
        } ?: return false
        return when (BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))) {
            BlockOperationMode.SETBLOCK -> {
                // issue #26 の既定値（単一の実行起点）＋issue #29 の各位置へ実行します。
                // コマンド固有位置がなければ起点集合を使い、各位置を独立に試行します。
                val locations = node.blockPositionSpec?.let {
                    actionOrigins(it, session, context) ?: return false
                } ?: origins
                locations.forEach { location ->
                    attempts.record(runCatching {
                        val world = location.world ?: error("world is unavailable")
                        if (plugin.placements.isRegistered(world, location.blockX, location.blockY, location.blockZ)) {
                            // 拡張コマンドブロックの位置は、setblockでも常に変更を拒否します。
                            error("placement is protected")
                        }
                        location.block.setType(material, false)
                    }.isSuccess)
                }
                attempts.overall
            }
            BlockOperationMode.FILL -> {
                // issue #26: 未設定時は単一の実行起点を既定値とします。
                val from = commandPositionOrExecution(node.blockFromSpec, session, context, origins) ?: return false
                val to = commandPositionOrExecution(node.blockToSpec, session, context, origins) ?: return false
                val world = from.world ?: return false
                if (world != to.world) return false
                val minX = minOf(from.blockX, to.blockX)
                val maxX = maxOf(from.blockX, to.blockX)
                val minY = minOf(from.blockY, to.blockY)
                val maxY = maxOf(from.blockY, to.blockY)
                val minZ = minOf(from.blockZ, to.blockZ)
                val maxZ = maxOf(from.blockZ, to.blockZ)
                val volume = (maxX.toLong() - minX.toLong() + 1L) *
                    (maxY.toLong() - minY.toLong() + 1L) *
                    (maxZ.toLong() - minZ.toLong() + 1L)
                if (volume !in 1L..MAX_BLOCK_OPERATION_VOLUME) return false
                // 1座標でも保護対象なら、通常のfillのように対象外だけを飛ばさず、
                // 変更前にコマンド全体を失敗させます。これにより部分変更を残しません。
                if (BlockOperationProtectionPolicy.hasProtectedBlock(
                        minX,
                        maxX,
                        minY,
                        maxY,
                        minZ,
                        maxZ,
                    ) { coordinate ->
                        plugin.placements.isRegistered(world, coordinate.x, coordinate.y, coordinate.z)
                    }
                ) return false
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            world.getBlockAt(x, y, z).setType(material, false)
                        }
                    }
                }
                true
            }
            null -> false
        }
    }

    private fun evaluateCondition(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
    ): Boolean = when (evaluateConditionDetailed(node, session, context)) {
        ConditionEvaluation.TRUE -> true
        ConditionEvaluation.FALSE,
        ConditionEvaluation.UNRESOLVED,
        ConditionEvaluation.INVALID,
        -> false
    }

    /**
     * 条件評価の詳細版です。正常な比較結果と解決失敗を分離します。
     *
     * - TRUE/FALSE: 正常な比較結果。分岐継続します。
     * - UNRESOLVED: 変数・位置などの解決失敗。FALSE 扱いにせず失敗停止します。
     * - INVALID: 未知 kind・型不正。FALSE 扱いにせず失敗停止します。
     */
    private fun evaluateConditionDetailed(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        // issue #26・#29: 実行起点ごとの対象の和集合です。null の場合は従来どおり
        // 文脈から解決します。
        unionTargets: List<Entity>? = null,
        // issue #26・#29: 有効な実行起点集合です。null の場合は従来の単一起点解決を使います。
        executionOrigins: List<Location>? = null,
    ): ConditionEvaluation {
        // issue #27: 判定対象ベースで分岐します。条件の単一正本は `subject` 欄であり、
        // 旧 `kind` へのフォールバックは行いません。旧保存の読込は保存境界の正規化が担います。
        // 未設定・不正な判定対象は評価失敗として停止し、FALSE 分岐へ畳みません。
        val subject = node.conditionSubject() ?: return ConditionEvaluation.INVALID
        // issue #26・#29: 和集合が与えられた場合は起点ごとの解決結果を使い回し、重複解決しません。
        val targets = unionTargets ?: resolveTargets(context, node.targetSpec, session)
        return when (subject) {
            // Entity 系は設定条件に一致する対象が1件以上存在すれば成立します。
            // 任意の1件だけを選んで判定せず、絞り込み条件は原則ANDで評価します。
            ConditionSubject.OTHER_ENTITY -> {
                if (targets.isNotEmpty()) {
                    ConditionEvaluation.TRUE
                } else {
                    ConditionEvaluation.FALSE
                }
            }
            ConditionSubject.PLAYER -> {
                // issue #27×#26・#29: 先頭1件でなく対象和集合からPlayerを絞りANY評価します。
                val players = targets.filterIsInstance<Player>()
                if (players.isEmpty()) {
                    ConditionEvaluation.FALSE
                } else if (players.any { matchesPlayerCondition(node, session, it) }) {
                    ConditionEvaluation.TRUE
                } else {
                    ConditionEvaluation.FALSE
                }
            }
            ConditionSubject.VARIABLE -> {
                val value = plugin.variables.get(session.worldId, node.string("variable"))
                    ?: return ConditionEvaluation.UNRESOLVED
                // 非 NUMBER 変数との数値比較は正常 false ではなく型不一致として分離します。
                if (value.type != ValueType.NUMBER) return ConditionEvaluation.INVALID
                val comparisonValue = resolveNumber(node.string("value"), session)
                    ?: return ConditionEvaluation.UNRESOLVED
                val operator = node.string("operator", "==")
                if (operator !in setOf("==", "!=", ">", "<", ">=", "<=")) {
                    return ConditionEvaluation.INVALID
                }
                val applicable = compareVariable(value, comparisonValue, operator)
                if (applicable) ConditionEvaluation.TRUE else ConditionEvaluation.FALSE
            }
            // issue #5: 型付き条件比較です。左辺の変数・演算子・右辺のリテラルで
            // 比較します。正常な不一致と解決失敗・型不一致を区別します。
            ConditionSubject.VALUE -> evaluateValueComparison(node, session)
            // issue #27×#26・#29: 判定対象名はBLOCKへ置換し、複数位置ANY評価と実行起点集合を維持します。
            // #27単独の単一位置解決へは戻しません。
            ConditionSubject.BLOCK -> {
                val expected = CommandValueRules.material(node.string("block"))
                    ?: return ConditionEvaluation.INVALID
                // issue #29: 複数位置はANY評価です。どこか1か所でTRUEなら全体をTRUE、
                // 全位置FALSE・空集合はFALSEとします。対象外種別の未解決は従来どおり
                // UNRESOLVEDにします。実行起点集合がある場合は既定値に使います。
                val candidates = node.conditionPositionSpec?.let { spec ->
                    val resolved = resolvePositions(spec, session, context)
                    if (resolved.isEmpty() && spec.kind != PositionKind.TARGET) {
                        return ConditionEvaluation.UNRESOLVED
                    }
                    resolved
                } ?: executionOrigins ?: context?.position?.let { spec ->
                    val resolved = resolvePositions(spec, session, context)
                    if (resolved.isEmpty() && spec.kind != PositionKind.TARGET) {
                        return ConditionEvaluation.UNRESOLVED
                    }
                    resolved
                } ?: listOf(session.origin)
                val matched = candidates.any { location ->
                    location.world == session.origin.world && location.block.type == expected
                }
                if (matched) ConditionEvaluation.TRUE else ConditionEvaluation.FALSE
            }
            ConditionSubject.CONTROL_BLOCK -> {
                // 制御ブロック自身の現在状態を判定します。複数項目はポリシー側でAND評価し、
                // 外側の反転指定は既存の条件実行共通処理へ任せます。
                if (
                    ControlBlockStateConditionPolicy.matches(
                        node.selectedControlBlockStates(),
                        RedstoneInputReader.isPowered(session.origin.block),
                    )
                ) {
                    ConditionEvaluation.TRUE
                } else {
                    ConditionEvaluation.FALSE
                }
            }
            // when は網羅列挙のため else を置きません。将来の判定対象追加時は
            // コンパイルエラーで検出します（冗長else除去と同一方針）。
        }
    }

    /**
     * issue #5 の型付き条件比較を評価します。
     *
     * 左辺は EXECUTION／WORLD scope の変数、右辺は型に応じたリテラルです。
     * ENTITY scope の左辺は対象文脈を必要とするため未対応（INVALID）とし、
     * 後続で対象付き評価へ拡張します。解決失敗は UNRESOLVED、型外の演算子・
     * 未対応型の組み合わせは INVALID とし、正常な不一致（FALSE）へ畳みません。
     */
    private fun evaluateValueComparison(
        node: CommandNode,
        session: ExecutionSession,
    ): ConditionEvaluation {
        val scopeRaw = node.string("leftScope").ifBlank { VariableScope.WORLD.name }
        val scope = VariableScope.parse(scopeRaw) ?: return ConditionEvaluation.INVALID
        if (scope == VariableScope.ENTITY) return ConditionEvaluation.INVALID
        val leftName = node.string("leftName")
        if (!CommandValueRules.isVariableName(leftName)) return ConditionEvaluation.INVALID
        val left: TypedValue = when (scope) {
            VariableScope.WORLD -> plugin.variables.get(session.worldId, leftName)
                ?: return ConditionEvaluation.UNRESOLVED
            VariableScope.EXECUTION -> session.executionVariables.get(
                me.awabi2048.kantancommander.model.TemporaryTemplate.normalized(leftName),
            ) ?: return ConditionEvaluation.UNRESOLVED
            VariableScope.ENTITY -> return ConditionEvaluation.INVALID
        }
        val operator = node.string("operator", "==")
        if (operator !in me.awabi2048.kantancommander.model.ConditionComparison.ALL) {
            return ConditionEvaluation.INVALID
        }
        val right = resolveComparisonRight(node.string("right"), left.type, session)
            ?: return ConditionEvaluation.UNRESOLVED
        val applicable = me.awabi2048.kantancommander.model.ConditionComparison.compare(left, operator, right)
            ?: return ConditionEvaluation.INVALID
        return if (applicable) ConditionEvaluation.TRUE else ConditionEvaluation.FALSE
    }

    /**
     * 比較右辺のリテラルを左辺の型で解決します。
     *
     * スカラーはテンプレート参照を含められます。ENTITY は UUID 文字列を受け付けます。
     * 未対応型・解決失敗は null です。
     */
    private fun resolveComparisonRight(
        raw: String,
        type: ValueType,
        session: ExecutionSession,
    ): TypedValue? = when (type) {
        ValueType.NUMBER -> resolveNumber(raw, session)?.let { TypedValue(type, numberValue = it) }
        ValueType.STRING -> resolveText(raw, session)?.let { TypedValue(type, stringValue = it) }
        ValueType.BOOLEAN -> resolveText(raw, session)
            ?.trim()?.lowercase()
            ?.let { if (it == "true") true else if (it == "false") false else null }
            ?.let { TypedValue(type, booleanValue = it) }
        ValueType.ENTITY -> raw.trim().takeIf(String::isNotBlank)?.let {
            runCatching { TypedValue(type, entityId = java.util.UUID.fromString(it)) }.getOrNull()
        }
        ValueType.LOCATION,
        ValueType.VECTOR,
        ValueType.ITEM,
        ValueType.BLOCK,
        ValueType.SOUND,
        ValueType.EFFECT,
        -> null
    }

    /**
     * issue #27: 単一プレイヤーに対する絞り込み条件（スニーク状態・所持アイテム）をAND評価します。
     *
     * 未設定欄は不問として扱い、設定済み欄をすべて満たす場合だけ true です。
     * 呼び出し側が複数候補へ `any` 適用し、存在判定 semantics を実現します。
     */
    private fun matchesPlayerCondition(
        node: CommandNode,
        session: ExecutionSession,
        player: Player,
    ): Boolean {
        val sneaking = node.params["sneaking"]?.takeIf(String::isNotBlank)?.toBooleanStrictOrNull()
        val sneakingMatches = sneaking == null || player.isSneaking == sneaking
        val refTemp = node.itemTempRef?.takeIf(String::isNotBlank)
            ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
            ?.takeIf { it.type == ValueType.ITEM }
        val item = refTemp?.item ?: node.string("item")
        val itemData = refTemp?.itemData ?: node.string("itemData")
        val itemMatches = item.isBlank() || playerHasItem(player, item, itemData)
        return sneakingMatches && itemMatches
    }

    /**
     * issue #2 の共通変数操作コマンドを実行します。
     *
     * scope（EXECUTION／WORLD）で対象storeを切り替え、operation で操作内容を
     * 切り替えます。旧ノードに scope がない場合は WORLD として読み替えます。
     * ENTITY scope は #22 で接続するため、ここでは失敗として扱います。
     *
     * - DEFINE: 新規定義のみ。既存名・不明な型・値の解決失敗は失敗です。
     * - SET: 値の設定。未定義名には暗黙定義として記録し、定義済みと異なる型は
     *   拒否します（定義型を正とし、GUI上のtypeを優先しません）。
     * - CHANGE: 定義済みの変更のみ。値生成方法は changeMode で切り替えます。
     * - COPY: 同一scope内の別変数へ値を複写します。複写元の型を引き継ぎ、
     *   複写先の定義型と異なる場合は失敗します。
     * - DELETE: 定義と値を削除します。存在しない場合は失敗します。
     *
     * #3 の値生成方法（changeMode）は値を書き込む3操作で共有します。
     * - ASSIGN: 値欄・型別設定のリテラルを代入します。
     * - CALCULATE: 数値計算式を評価します（NUMBERのみ）。
     * - PROPERTY: 別変数の詳細値を型安全に取得します。
     * - RANDOM: 乱数を生成します（NUMBERのみ）。
     */
    private fun executeVariable(
        node: CommandNode,
        session: ExecutionSession,
    ): Boolean = runCatching {
        val scope = node.variableScope()
        val operation = VariableOperation.valueOf(node.string("operation", VariableOperation.DEFINE.name))
        // #22: ENTITY scope は対象指定が必須であり、定義表を持ちません。
        // 通常の対象形式と文字列の変数名で操作します。
        if (scope == VariableScope.ENTITY) {
            return executeEntityVariable(node, session, operation)
        }
        val store: VariableStore = when (scope) {            VariableScope.EXECUTION -> session.executionVariables
            VariableScope.WORLD -> WorldVariableAdapter(plugin.variables, session.worldId)
            VariableScope.ENTITY -> return false
        }
        val name = node.string("name")
        when (operation) {
            VariableOperation.DEFINE -> {
                val mode = variableChangeMode(node)
                val type = if (mode == VariableChangeMode.ASSIGN || mode == VariableChangeMode.CALCULATE) {
                    ValueType.parse(node.string("type")) ?: return false
                } else null
                if (mode == VariableChangeMode.CALCULATE && type != ValueType.NUMBER) return false
                if (store.definition(name) != null) return false
                val value = resolveVariableGeneration(node, type, mode, store::get, session) ?: return false
                store.define(name, value)
            }
            VariableOperation.SET -> {
                // 定義済みの型を正とします。未定義の場合はGUI上のtypeで暗黙定義します。
                // PROPERTY／RANDOM モードでは生成結果の型を採用します。
                val mode = variableChangeMode(node)
                val defined = store.definition(name)
                val type = if (mode == VariableChangeMode.ASSIGN || mode == VariableChangeMode.CALCULATE) {
                    defined ?: (ValueType.parse(node.string("type")) ?: return false)
                } else null
                if (mode == VariableChangeMode.CALCULATE && type != ValueType.NUMBER) return false
                val value = resolveVariableGeneration(node, type, mode, store::get, session) ?: return false
                store.set(name, value)
                true
            }
            VariableOperation.CHANGE -> {
                val defined = store.definition(name) ?: return false
                val mode = variableChangeMode(node)
                if (mode == VariableChangeMode.CALCULATE && defined != ValueType.NUMBER) return false
                val value = resolveVariableGeneration(node, defined, mode, store::get, session) ?: return false
                store.set(name, value)
                true
            }
            VariableOperation.COPY -> {
                val source = store.get(node.string("source")) ?: return false
                store.set(name, source)
                true
            }
            VariableOperation.DELETE -> store.remove(name)
        }
    }.getOrDefault(false)

    /** 値生成方法を解決します。不正値は ASSIGN として扱います。 */
    private fun variableChangeMode(node: CommandNode): VariableChangeMode =
        runCatching { VariableChangeMode.valueOf(node.string("changeMode", VariableChangeMode.ASSIGN.name)) }
            .getOrDefault(VariableChangeMode.ASSIGN)

    /**
     * issue #22 のEntity変数操作を実行します。
     *
     * 通常の対象形式で解決したEntity群へ、文字列の変数名で操作します。事前DEFINEを
     * 要求せず、定義表を持ちません。同名変数の型変更は後勝ちで上書きします。
     *
     * - SET: 全対象へ書き込みます。未定義名は暗黙に作成します。
     * - CHANGE: 全対象に変数が存在する場合だけ書き込みます。
     * - COPY: 対象ごとに同一Entity内の複写元から複写します。
     * - DELETE: 全対象から削除し、1件でも削除できれば成功です。
     * - DEFINE: 不要なため失敗します。
     *
     * 値生成は同一Entity内で閉じます。PROPERTY モードの参照元は対象Entity自身の
     * 変数から読み出すため、複数Entityでも各個体が独立に解決します。
     */
    private fun executeEntityVariable(
        node: CommandNode,
        session: ExecutionSession,
        operation: VariableOperation,
    ): Boolean = runCatching {
        val spec = node.targetSpec ?: return false
        val targets = resolveTargetSpecs(spec, session, session.context)
        if (targets.isEmpty()) return false
        val name = node.string("name")
        val store = plugin.entityVariables
        when (operation) {
            VariableOperation.DEFINE -> false
            VariableOperation.SET -> {
                val mode = variableChangeMode(node)
                val type = if (mode == VariableChangeMode.ASSIGN || mode == VariableChangeMode.CALCULATE) {
                    ValueType.parse(node.string("type")) ?: return false
                } else null
                if (mode == VariableChangeMode.CALCULATE && type != ValueType.NUMBER) return false
                if (mode == VariableChangeMode.PROPERTY) {
                    // 対象ごとに自身の参照元から取得して書き込みます。
                    targets.forEach { target ->
                        val value = resolveVariableGeneration(node, null, mode, { store.readSingle(listOf(target), it).asValue() }, session)
                            ?: return false
                        store.write(listOf(target), name, value)
                    }
                    true
                } else {
                    val value = resolveVariableGeneration(node, type, mode, { null }, session) ?: return false
                    store.write(targets, name, value)
                    true
                }
            }
            VariableOperation.CHANGE -> {
                // 全対象に変数が存在する場合だけ書き込みます。
                if (targets.any { store.readSingle(listOf(it), name) !is me.awabi2048.kantancommander.model.VariableRead.Value }) {
                    return false
                }
                val mode = variableChangeMode(node)
                if (mode == VariableChangeMode.PROPERTY) {
                    targets.forEach { target ->
                        val value = resolveVariableGeneration(node, null, mode, { store.readSingle(listOf(target), it).asValue() }, session)
                            ?: return false
                        store.write(listOf(target), name, value)
                    }
                    true
                } else {
                    // ASSIGN はGUI上のtype、CALCULATE／RANDOM は生成結果の型を採用します。
                    val type = if (mode == VariableChangeMode.ASSIGN) {
                        ValueType.parse(node.string("type")) ?: return false
                    } else null
                    val value = resolveVariableGeneration(node, type, mode, { null }, session) ?: return false
                    store.write(targets, name, value)
                    true
                }
            }
            VariableOperation.COPY -> {
                val source = node.string("source")
                targets.forEach { target ->
                    val origin = store.readSingle(listOf(target), source)
                    if (origin !is VariableRead.Value) return false
                    store.write(listOf(target), name, origin.value)
                }
                true
            }
            VariableOperation.DELETE -> store.delete(targets, name) > 0
        }
    }.getOrDefault(false)

    /**
     * issue #3 の値生成を解決します。
     *
     * type は ASSIGN／CALCULATE モードの対象型です。PROPERTY／RANDOM モードでは
     * 生成結果の型を採用するため null で呼び出します。sourceRead は同一scopeの
     * 参照元読み出しであり、ENTITY scope では対象Entityごとの読み出しを渡します。
     * 失敗は null で返します。
     */
    private fun resolveVariableGeneration(
        node: CommandNode,
        type: ValueType?,
        mode: VariableChangeMode,
        sourceRead: (String) -> TypedValue?,
        session: ExecutionSession,
    ): TypedValue? = runCatching {
        when (mode) {
            VariableChangeMode.ASSIGN -> {
                val resolved = type ?: return null
                resolveNodeValue(resolved, node, session) ?: error("value is unavailable")
            }
            VariableChangeMode.CALCULATE -> {
                if (type != ValueType.NUMBER) error("calculation requires NUMBER")
                evaluateVariableExpression(node.string("value"), session) ?: error("expression failed")
            }
            VariableChangeMode.PROPERTY -> {
                val source = sourceRead(node.string("propertySource")) ?: error("property source is missing")
                me.awabi2048.kantancommander.model.ValueProperties.resolve(
                    source,
                    node.string("property"),
                ) { uuid -> plugin.server.getEntity(uuid) } ?: error("property is unavailable")
            }
            VariableChangeMode.RANDOM -> generateRandomValue(
                node.string("randomMin"),
                node.string("randomMax"),
            ) ?: error("random range is invalid")
        }
    }.getOrNull()

    /** 数値計算式を評価します。変数同士の参照だけを使います。 */
    private fun evaluateVariableExpression(raw: String, session: ExecutionSession): TypedValue? {
        val expression = NumericExpression.parse(raw).expression ?: return null
        val result = expression.evaluate(
            { reference ->
                if (SystemVariableNames.isSystemName(reference)) {
                    when (reference) {
                        SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toDouble()
                        else -> null
                    }
                } else plugin.variables.get(session.worldId, reference)?.numberValue
            },
            { reference ->
                session.executionVariables.get(reference.normalizedTemporaryName())
                    ?.takeIf { it.type == ValueType.NUMBER }
                    ?.numberValue?.takeIf(Double::isFinite)
            },
        ) ?: return null
        return TypedValue(ValueType.NUMBER, numberValue = result)
    }

    /**
     * issue #3 の乱数を生成します。
     *
     * 範囲は両端を含む [min, max] です。min・max ともに整数値の場合は整数の
     * 一様乱数を、それ以外は実数の一様乱数を返します。範囲不正・非有限値は
     * 失敗として扱います。
     */
    private fun generateRandomValue(rawMin: String, rawMax: String): TypedValue? {
        val min = rawMin.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        val max = rawMax.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        if (min > max) return null
        val integer = min == kotlin.math.floor(min) && max == kotlin.math.floor(max)
        val value = if (integer) {
            val range = (max - min + 1.0).takeIf { it.isFinite() && it <= Long.MAX_VALUE.toDouble() }
                ?: return null
            min + Random.nextLong(range.toLong())
        } else {
            min + Random.nextDouble() * (max - min)
        }.takeIf(Double::isFinite) ?: return null
        return TypedValue(ValueType.NUMBER, numberValue = value)
    }

    /**
     * WORLD scope を共通store契約へ接続します。
     *
     * WorldVariableStore は worldId を引数に取るため、実行セッションの worldId で
     * 束縛して VariableStore として扱います。名前正規化は保存層へ委ねます。
     */
    private class WorldVariableAdapter(
        private val store: me.awabi2048.kantancommander.data.WorldVariableStore,
        private val worldId: UUID,
    ) : VariableStore {
        override fun define(name: String, value: TypedValue): Boolean =
            store.define(worldId, name, value)

        override fun set(name: String, value: TypedValue) =
            store.set(worldId, name, value)

        override fun get(name: String): TypedValue? =
            store.get(worldId, name)

        override fun definition(name: String): ValueType? =
            store.definitions(worldId)[name.trim().lowercase(java.util.Locale.ROOT)]?.type

        override fun definitions(): Map<String, ValueType> =
            store.definitions(worldId).mapValues { (_, value) -> value.type }

        override fun remove(name: String): Boolean =
            store.remove(worldId, name)
    }

    /**
     * 一時変数を設定します。再設定は上書きとして扱います。
     *
     * #2 の共通store（EXECUTION scope）へ保存し、定義も同時に記録します。
     * 値の組み立ては [resolveNodeValue] へ集約し、変数操作コマンドと共有します。
     */
    private fun executeTemporary(node: CommandNode, session: ExecutionSession): Boolean = runCatching {
        val name = me.awabi2048.kantancommander.model.TemporaryTemplate.normalized(node.string("name"))
        require(CommandValueRules.isVariableName(name)) { "invalid temporary name" }
        val type = ValueType.parse(
            node.string("tempType", ValueType.NUMBER.name),
        ) ?: return false
        val value = resolveNodeValue(type, node, session) ?: return false
        session.executionVariables.set(name, value)
        true
    }.getOrDefault(false)

    /**
     * issue #2 の型別値組み立てです。
     *
     * NUMBER・STRING・BOOLEAN・VECTOR は値欄から、複合6型は型ごとの共通設定から
     * 解決済み値を保持します。Bukkit／Paper の実体は保持せず、再解決可能な
     * 表現（UUID・座標・素材名等）だけを使います。失敗は null で返します。
     */
    private fun resolveNodeValue(type: ValueType, node: CommandNode, session: ExecutionSession): TypedValue? =
        runCatching {
            when (type) {
                ValueType.NUMBER -> TypedValue(
                    type,
                    numberValue = resolveNumber(node.string("value"), session) ?: error("number must be finite"),
                )
                ValueType.STRING -> {
                    val text = resolveText(node.string("value"), session) ?: error("string is unavailable")
                    require(text.length <= CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH) {
                        "variable string exceeds ${CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH} characters"
                    }
                    TypedValue(type, stringValue = text)
                }
                ValueType.BOOLEAN -> TypedValue(
                    type,
                    booleanValue = parseBooleanInput(node.string("value")) ?: error("boolean must be true or false"),
                )
                ValueType.VECTOR -> TypedValue(
                    type,
                    vector = me.awabi2048.kantancommander.model.SavedVector(
                        x = resolveNumber(node.string("x"), session)?.takeIf(Double::isFinite) ?: error("invalid x"),
                        y = resolveNumber(node.string("y"), session)?.takeIf(Double::isFinite) ?: error("invalid y"),
                        z = resolveNumber(node.string("z"), session)?.takeIf(Double::isFinite) ?: error("invalid z"),
                    ),
                )
                ValueType.LOCATION -> {
                // 新形式は通常コマンドと同じPositionSpec/FacingSpecから解決します。
                // 旧POSITIONのx/y/z形式は読み込み済みデータを壊さないため、この実行境界
                // だけでLOCATIONへ組み立て直します。以後の参照は常に一つのSavedLocationです。
                val positionSpec = node.temporaryLocationPositionSpec ?: PositionSpec(
                    PositionKind.COORDINATES,
                    x = resolveNumber(node.string("x"), session) ?: error("invalid x"),
                    y = resolveNumber(node.string("y"), session) ?: error("invalid y"),
                    z = resolveNumber(node.string("z"), session) ?: error("invalid z"),
                )
                val facingSpec = node.temporaryLocationFacingSpec ?: FacingSpec(
                    FacingKind.ROTATION,
                    yaw = node.string("yaw").takeIf(String::isNotBlank)
                        ?.let { resolveNumber(it, session)?.toFloat() } ?: 0f,
                    pitch = node.string("pitch").takeIf(String::isNotBlank)
                        ?.let { resolveNumber(it, session)?.toFloat() } ?: 0f,
                )
                val location = resolveSinglePosition(positionSpec, session, session.context)
                    ?: error("location is unavailable")
                applyFacing(location, facingSpec, session.context, session)
                TypedValue(
                    type,
                    location = me.awabi2048.kantancommander.model.SavedLocation(
                        location.x,
                        location.y,
                        location.z,
                        location.yaw,
                        location.pitch,
                    ),
                )
            }
            ValueType.ITEM -> {
                val material = CommandValueRules.material(node.string("item"), allowAir = false)
                    ?: error("invalid item")
                TypedValue(type, item = material.name, itemData = node.string("itemData"))
            }
            ValueType.BLOCK -> {
                val material = CommandValueRules.placementMaterial(node.string("block"))
                    ?: error("invalid block")
                TypedValue(type, block = material.name)
            }
            ValueType.ENTITY -> {
                val targetSpec = node.temporaryEntityTargetSpec
                val entityId = if (targetSpec != null) {
                    resolveTargetSpec(targetSpec, session, session.context)?.uniqueId
                } else {
                    node.string("entityId").takeIf(String::isNotBlank)
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                }
                // 定義時点で実体が見つからなくても一時値そのものは保存します。
                // 死亡・別ワールド・未ロードは参照時の「対象なし」と同じ契約で扱い、
                // 後続ノードだけをスキップできるようにします。
                TypedValue(type, entityId = entityId)
            }
            ValueType.SOUND -> {
                if (!CommandValueRules.isSoundId(node.string("sound"))) error("invalid sound")
                val volume = resolveNumber(node.string("volume", "1.0"), session)
                    ?.takeIf { it.isFinite() && it in 0.0..34.0 } ?: error("invalid volume")
                val pitch = resolveNumber(node.string("pitch", "1.0"), session)
                    ?.takeIf { it.isFinite() && it in 0.5..2.0 } ?: error("invalid pitch")
                TypedValue(type, sound = node.string("sound"), volume = volume, pitch = pitch)
            }
            ValueType.EFFECT -> {
                if (!CommandValueRules.isEffectId(node.string("effect"))) error("invalid effect")
                val level = resolveNumber(node.string("level", "1"), session)
                    ?.takeIf { it == kotlin.math.floor(it) && it in 1.0..255.0 }
                    ?.toInt() ?: error("invalid level")
                val seconds = resolveNumber(node.string("seconds", "30"), session)
                    ?.takeIf { it == kotlin.math.floor(it) && it in 1.0..86_400.0 }
                    ?.toInt() ?: error("invalid seconds")
                TypedValue(type, effect = node.string("effect"), level = level, seconds = seconds)
            }
        }
    }.getOrNull()

    /** 真偽値入力を解釈します。受付は大小無視の true／false のみです。 */
    private fun parseBooleanInput(raw: String): Boolean? = when (raw.trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun compareVariable(value: TypedValue?, comparisonValue: Double, operator: String): Boolean {
        val actual = value?.takeIf { it.type == ValueType.NUMBER }?.numberValue ?: return false
        val comparison = actual.compareTo(comparisonValue)
        return when (operator) {
            "==" -> comparison == 0
            "!=" -> comparison != 0
            ">" -> comparison > 0
            "<" -> comparison < 0
            ">=" -> comparison >= 0
            "<=" -> comparison <= 0
            else -> false
        }
    }

    private fun resolveTarget(context: ExecutionContextSpec?, nodeTarget: TargetSpec?, session: ExecutionSession): Entity? =
        resolveTargets(context, nodeTarget, session).firstOrNull()

    private fun resolveTargets(
        context: ExecutionContextSpec?,
        nodeTarget: TargetSpec?,
        session: ExecutionSession,
    ): List<Entity> = nodeTarget?.let { resolveTargetSpecs(it, session, context) }
        ?: context?.target?.let { resolveTargetSpecs(it, session, context) }
        ?: context?.executor?.let { resolveTargetSpecs(it, session, context) }
        ?: emptyList()

    private fun resolveTargetSpec(
        spec: TargetSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec? = session.context,
    ): Entity? = resolveTargetSpecs(spec, session, context).firstOrNull()

    private fun resolveTargetSpecs(
        spec: TargetSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec? = session.context,
    ): List<Entity> {
        val resolvedSpec = resolveTargetFilter(spec, session) ?: return emptyList()
        val selectionOrigin = selectionOrigin(context, session) ?: return emptyList()
        val candidates: List<Entity> = when (resolvedSpec.kind) {
            TargetKind.TEMPORARY -> listOfNotNull(
                resolvedSpec.tempName
                    ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                    ?.takeIf { it.type == ValueType.ENTITY }
                    ?.entityId?.let(session.origin.world::getEntity),
            )
            TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS, TargetKind.ALL_PLAYERS, TargetKind.RANDOM_PLAYER ->
                session.origin.world.players.filter { matches(it, resolvedSpec, session, context) }
            TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES ->
                session.origin.world.entities.filter { matches(it, resolvedSpec, session, context) }
            TargetKind.FIXED_ENTITY -> listOfNotNull(resolvedSpec.fixedEntityId?.let(session.origin.world::getEntity))
        }
        // 対象種別・固定UUID・一時変数の全経路をここで同じように検閲します。
        // 個別の操作実装で除外すると、新しいコマンド種別の追加時に抜け道になります。
        val inMyWorld = candidates
            .filter { it.world == session.origin.world }
            .filterNot(systemEntityRegistry::isSystemEntity)
        val sorted = when {
            resolvedSpec.kind == TargetKind.RANDOM_PLAYER || resolvedSpec.sort == me.awabi2048.kantancommander.model.TargetSort.RANDOM ->
                inMyWorld.shuffled()
            resolvedSpec.sort == me.awabi2048.kantancommander.model.TargetSort.FURTHEST ->
                inMyWorld.sortedByDescending { it.location.distanceSquared(selectionOrigin) }
            else -> inMyWorld.sortedBy { it.location.distanceSquared(selectionOrigin) }
        }
        val defaultLimit = when (resolvedSpec.kind) {
            TargetKind.NEAREST_PLAYER, TargetKind.RANDOM_PLAYER, TargetKind.NEAREST_ENTITY,
            TargetKind.FIXED_ENTITY, TargetKind.TEMPORARY -> 1
            else -> Int.MAX_VALUE
        }
        return sorted.take((resolvedSpec.limit ?: defaultLimit).coerceAtLeast(1))
    }

    /** 対象名・タグだけを実行直前に補間し、Registry IDや構造化値は型を保ちます。 */
    private fun resolveTargetFilter(spec: TargetSpec, session: ExecutionSession): TargetSpec? {
        fun resolve(value: String?): String? {
            if (value == null || value.isBlank()) return null
            return resolveText(value, session)
        }
        val tag = resolve(spec.tag)
        val name = resolve(spec.name)
        if ((spec.tag?.isNotBlank() == true && tag == null) ||
            (spec.name?.isNotBlank() == true && name == null)
        ) return null
        return spec.copy(tag = tag, name = name)
    }

    private fun matches(
        entity: Entity,
        spec: TargetSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
    ): Boolean {
        if (spec.entityType != null && entity.type.key.toString() != spec.entityType) return false
        if (spec.name != null && entity.name != spec.name) return false
        if (spec.tag != null && spec.tag !in entity.scoreboardTags) return false
        val origin = searchOriginFor(spec, context, session) ?: return false
        val distance = entity.location.distance(origin)
        if (spec.minimumDistance != null && distance < spec.minimumDistance) return false
        if (spec.maximumDistance != null && distance > spec.maximumDistance) return false
        if (!withinSelectorAxis(entity.location.x, origin.x, spec.dx)) return false
        if (!withinSelectorAxis(entity.location.y, origin.y, spec.dy)) return false
        if (!withinSelectorAxis(entity.location.z, origin.z, spec.dz)) return false
        return spec.gameMode == null || entity is Player && entity.gameMode.name.equals(spec.gameMode, true)
    }

    /** dx/dy/dzは基準ブロックを含むバニラセレクターの直方体として判定します。 */
    private fun withinSelectorAxis(value: Double, origin: Double, extent: Double?): Boolean {
        if (extent == null) return true
        val minimum = kotlin.math.floor(origin)
        return value >= minimum && value < minimum + extent + 1.0
    }

    private fun selectionOrigin(context: ExecutionContextSpec?, session: ExecutionSession): Location? =
        when (val position = context?.position) {
            null -> session.origin
            else -> when (position.kind) {
                PositionKind.CAPTURED, PositionKind.COORDINATES,
                PositionKind.DISK, PositionKind.MYWORLD_SPAWN, PositionKind.TEMPORARY ->
                    resolvePosition(position, session, context)
                // TARGETはテレポート先などのコマンド固有設定でのみ使用します。
                PositionKind.TARGET -> null
            }
        }

    /**
     * 対象探索の基準位置です。「探索の基準」設定があれば最優先し、
     * なければ従来の選択原点（コンテキスト位置または制御ブロック位置）を用います。
     */
    private fun searchOriginFor(spec: TargetSpec, context: ExecutionContextSpec?, session: ExecutionSession): Location? {
        val search = spec.searchOrigin?.takeIf { it.hasAnySetting() }
        if (search != null) {
            search.positionTemp?.takeIf(String::isNotBlank)?.let { name ->
                val temp = session.executionVariables.get(TemporaryTemplate.normalized(name))
                    ?.takeIf { it.type == ValueType.LOCATION }
                    ?.location ?: return null
                return Location(session.origin.world, temp.x, temp.y, temp.z, temp.yaw, temp.pitch)
            }
            search.position?.let { return resolvePosition(it, session, context) }
        }
        return selectionOrigin(context, session)
    }

    /** GUI設定ではなく、実行エンジンが現在保持している内部状態だけを返します。 */
    private fun effectiveContext(session: ExecutionSession): ExecutionContextSpec? = session.context

    /**
     * issue #26・#29: 起点位置から単一の実行状態を組み立てます。
     *
     * 座標解決の基準は `session.origin` に固定し、起点の値を絶対指定へ
     * 写像します。ambientの実行者・対象・向きは引き継ぎます。
     */
    private fun originContext(origin: Location, ambient: ExecutionContextSpec?): ExecutionContextSpec {
        val position = PositionSpec(
            PositionKind.COORDINATES,
            origin.x,
            origin.y,
            origin.z,
            origin.yaw,
            origin.pitch,
        )
        return ambient?.copy(position = position) ?: ExecutionContextSpec(position = position)
    }

    /**
     * issue #26・#29: 有効な実行起点集合です。
     *
     * 未設定時はambient位置または制御ブロック位置の1件、対象内包位置は
     * 0..N件です。対象外種別の未解決はnullであり、呼び出し側で従来どおり
     * 失敗にします。空集合は対象なしとして扱います。
     */
    private fun effectiveExecutionOrigins(
        node: CommandNode,
        session: ExecutionSession,
        ambient: ExecutionContextSpec?,
    ): List<Location>? {
        val spec = node.executionPositionSpec
        if (spec == null) {
            val base = ambient?.position?.let { resolvePosition(it, session, ambient) ?: return null }
                ?: session.origin
            return listOf(base)
        }
        val resolved = resolvePositions(spec, session, ambient)
        if (resolved.isEmpty() && spec.kind != PositionKind.TARGET) return null
        return resolved
    }

    /**
     * issue #29: 単一限定の役割を実行位置として解決します。
     *
     * 対象内包のうち静的0..1の指定だけを先頭1件へ解決し、それ以外は
     * 未解決とします。複数になり得る指定は検証で拒否済みです。
     */
    private fun resolveSinglePosition(
        spec: PositionSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
    ): Location? {
        if (spec.kind == PositionKind.TARGET) {
            val target = spec.target?.takeIf { !it.isMultiCapable() } ?: return null
            return resolveTargetSpecs(target, session, context).firstOrNull()?.location?.clone()
        }
        return resolvePosition(spec, session, context)
    }

    private fun resolvePosition(
        spec: PositionSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec? = session.context,
    ): Location? = when (spec.kind) {
        PositionKind.CAPTURED, PositionKind.COORDINATES -> resolveCoordinatePosition(spec, session)
        PositionKind.DISK -> {
            // 成分別指定は座標種別だけの指定です。制御ブロック自体への付与は拒否します。
            if (spec.hasNonAbsoluteSemantics()) return null
            session.origin.clone()
        }
        PositionKind.TEMPORARY -> {
            if (spec.hasNonAbsoluteSemantics()) return null
            val temp = spec.tempName
                ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                    ?.takeIf { it.type == ValueType.LOCATION }
                    ?.location ?: return null
            Location(session.origin.world, temp.x, temp.y, temp.z, temp.yaw, temp.pitch)
        }
        // TARGETはテレポート先などのコマンド固有設定で解決するため、通常の
        // 実行位置へ渡された場合は静的検証と同じく未解決として扱います。
        // issue #29 の対象内包位置は resolvePositions 側で集合解決します。
        PositionKind.TARGET -> null
        PositionKind.MYWORLD_SPAWN -> {
            if (spec.hasNonAbsoluteSemantics()) return null
            myworldSpawnLocation(session, spec.myworldSpawn)
        }
    }

    /**
     * issue #29 のMyWorldスポーン解決です。
     *
     * Bukkitの通常world spawnではなく、MyWorldManagerが保持する該当スポーン位置を
     * 参照します。旧来の単一種別はメンバー用として扱います。
     * MWM無効・スポーン未設定・取得例外・別ワールドの場合は解決不能とし、
     * 通常のワールドスポーンへ無断で読み替えません。呼び出し側で失敗として扱います。
     */
    private fun myworldSpawnLocation(
        session: ExecutionSession,
        kind: me.awabi2048.kantancommander.model.MyworldSpawnKind?,
    ): Location? {
        val spawnKind = kind ?: me.awabi2048.kantancommander.model.MyworldSpawnKind.MEMBER
        if (!plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) return null
        val stored = runCatching {
            val data = MyWorldManagerApi.getWorldRepository()?.findByWorldName(session.origin.world.name)
            when (spawnKind) {
                me.awabi2048.kantancommander.model.MyworldSpawnKind.MEMBER -> data?.spawnPosMember
                me.awabi2048.kantancommander.model.MyworldSpawnKind.GUEST -> data?.spawnPosGuest
            }
        }.getOrNull()
        if (stored == null || stored.world?.uid != session.origin.world.uid) return null
        return stored.clone()
    }

    /**
     * issue #29 のコマンド固有位置の集合解決です。
     *
     * 対象内包位置は0..N件（空集合あり）、対象外種別の未解決はnullであり、
     * 呼び出し側で従来どおり失敗にします。
     */
    private fun actionOrigins(
        spec: PositionSpec?,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
    ): List<Location>? {
        if (spec == null) return null
        val resolved = resolvePositions(spec, session, context)
        if (resolved.isEmpty() && spec.kind != PositionKind.TARGET) return null
        return resolved
    }

    /**
     * issue #29 の位置集合解決です。
     *
     * 対象内包位置（TARGET）は選択エンティティそれぞれの位置へ0..N件解決し、
     * それ以外は単一解決の0..1件です。各コマンド側で重複実装しません。
     */
    private fun resolvePositions(
        spec: PositionSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
    ): List<Location> {
        if (spec.kind == PositionKind.TARGET) {
            val target = spec.target ?: return emptyList()
            return resolveTargetSpecs(target, session, context).map { it.location.clone() }
        }
        return resolvePosition(spec, session, context)?.let { listOf(it) } ?: emptyList()
    }

    /**
     * issue #4 の座標解決です。基準は `session.origin` に固定し、
     * 実行コンテキスト位置は基準にしません。成分ごとに解釈が異なります。
     *
     * - ABSOLUTE軸：値そのまま（欠落時は起点値を継承します）。
     * - RELATIVE軸：起点からの差分として加算します。欠落・非有限は未解決です。
     * - LOCAL軸：起点の向きを基準にした左・上・前の合成ベクトルから対応成分を
     *   採用します。向きの関係で他軸へ寄与する場合があります（例：yaw=90°時の
     *   LOCAL-Xはworld-Zへ寄与します）。欠落・非有限は未解決です。
     * - yaw／pitch：ABSOLUTEは上書き、RELATIVEは起点へ加算して正規化します。
     *   LOCALは角度に基準が定義できないため未解決です。
     */
    private fun resolveCoordinatePosition(spec: PositionSpec, session: ExecutionSession): Location? {
        val origin = session.origin
        val localOffset = if (
            spec.semanticsX == CoordinateSemantics.LOCAL ||
            spec.semanticsY == CoordinateSemantics.LOCAL ||
            spec.semanticsZ == CoordinateSemantics.LOCAL
        ) {
            // LOCAL軸だけを基底へ載せます。ABSOLUTE／RELATIVE軸は0として寄与させません。
            localOffset(
                origin.yaw,
                origin.pitch,
                if (spec.semanticsX == CoordinateSemantics.LOCAL) spec.x?.takeIf(Double::isFinite) ?: return null else 0.0,
                if (spec.semanticsY == CoordinateSemantics.LOCAL) spec.y?.takeIf(Double::isFinite) ?: return null else 0.0,
                if (spec.semanticsZ == CoordinateSemantics.LOCAL) spec.z?.takeIf(Double::isFinite) ?: return null else 0.0,
            ) ?: return null
        } else {
            SpatialVector(0.0, 0.0, 0.0)
        }
        return Location(
            origin.world,
            resolveAxis(spec.semanticsX, spec.x, origin.x, origin.x + localOffset.x) ?: return null,
            resolveAxis(spec.semanticsY, spec.y, origin.y, origin.y + localOffset.y) ?: return null,
            resolveAxis(spec.semanticsZ, spec.z, origin.z, origin.z + localOffset.z) ?: return null,
            resolveAngle(spec.semanticsYaw, spec.yaw, origin.yaw, ::wrapMinecraftYaw) ?: return null,
            resolveAngle(spec.semanticsPitch, spec.pitch, origin.pitch, ::clampMinecraftPitch) ?: return null,
        )
    }

    /**
     * 1軸分の座標を解決します。ABSOLUTEは欠落時に起点値を継承し、
     * RELATIVE／LOCALは有限値の明示を要求します。
     */
    private fun resolveAxis(
        semantics: CoordinateSemantics,
        value: Double?,
        origin: Double,
        localResolved: Double,
    ): Double? = when (semantics) {
        CoordinateSemantics.ABSOLUTE -> value ?: origin
        CoordinateSemantics.RELATIVE -> value?.takeIf(Double::isFinite)?.let { origin + it }
        CoordinateSemantics.LOCAL -> value?.takeIf(Double::isFinite)?.let { localResolved }
    }

    /**
     * 1成分分の角度を解決します。ABSOLUTEは欠落時に起点値を継承し、
     * RELATIVEは起点へ加算して正規化します。LOCALは未解決です。
     */
    private fun resolveAngle(
        semantics: CoordinateSemantics,
        value: Float?,
        origin: Float,
        normalize: (Float) -> Float,
    ): Float? = when (semantics) {
        CoordinateSemantics.ABSOLUTE -> value ?: origin
        CoordinateSemantics.RELATIVE ->
            value?.takeIf(Float::isFinite)?.let { normalize(origin + it) }
        CoordinateSemantics.LOCAL -> null
    }

    /**
     * ローカル座標の基底を `session.origin` の向きから求めます。
     * Bukkitのyaw（0=南・90=西・-90=東・180=北）とpitch（-90=上・90=下）に合わせ、
     * 左・上・前の正規直交基底で差分を合成します。向きが非有限なら未解決です。
     */
    internal fun localOffset(yaw: Float, pitch: Float, left: Double, up: Double, forward: Double): SpatialVector? {
        if (!yaw.isFinite() || !pitch.isFinite()) return null
        if (!left.isFinite() || !up.isFinite() || !forward.isFinite()) return null
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val sinYaw = kotlin.math.sin(yawRad)
        val cosYaw = kotlin.math.cos(yawRad)
        val sinPitch = kotlin.math.sin(pitchRad)
        val cosPitch = kotlin.math.cos(pitchRad)
        // 前は視線方向、左は水平面上の直交方向、上は両者に直交する方向です。
        val forwardVector = SpatialVector(-sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch)
        val leftVector = SpatialVector(cosYaw, 0.0, sinYaw)
        val upVector = SpatialVector(-sinYaw * sinPitch, cosPitch, cosYaw * sinPitch)
        return SpatialVector(
            leftVector.x * left + upVector.x * up + forwardVector.x * forward,
            leftVector.y * left + upVector.y * up + forwardVector.y * forward,
            leftVector.z * left + upVector.z * up + forwardVector.z * forward,
        )
    }

    /**
     * issue #4 の領域解決の骨組みです。現行コマンドへの本接続は行わず、
     * 将来Target絞り込み・Condition判定へ移植できる形に留めます。
     * worldを跨ぐ比較は呼び出し側で明示的に失敗させます。
     */
    internal data class ResolvedRegion(
        val origin: Location,
        val dx: Double?,
        val dy: Double?,
        val dz: Double?,
        val minimumDistance: Double?,
        val maximumDistance: Double?,
    )

    /**
     * 領域基準を解決します。FIXEDは明示位置、CONTROL_BLOCKは制御ブロック位置、
     * TEMPORARYはLOCATION型一時変数、TARGET_ENTITYは対象実体位置です。
     * 未解決・world不一致はnullで返し、呼び出し側で区別できるようにします。
     */
    private fun resolveRegionAnchor(
        spec: RegionSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec? = session.context,
    ): Location? = when (spec.anchor.kind) {
        RegionAnchorKind.CONTROL_BLOCK -> session.origin.clone()
        RegionAnchorKind.FIXED -> {
            val position = spec.anchor.position ?: return null
            resolvePosition(position, session, context)
        }
        RegionAnchorKind.TEMPORARY -> {
            val temp = spec.anchor.tempName
                ?.takeIf(String::isNotBlank)
                ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                ?.takeIf { it.type == ValueType.LOCATION }
                ?.location ?: return null
            Location(session.origin.world, temp.x, temp.y, temp.z, temp.yaw, temp.pitch)
        }
        RegionAnchorKind.TARGET_ENTITY -> {
            val target = spec.anchor.target ?: context?.target ?: return null
            resolveTargetSpec(target, session, context)?.location
        }
    }

    /**
     * 直方体＋距離の骨組みを解決します。SPHERE／CYLINDERは予約のため、
     * 現行実行では未対応としてnullを返します。呼び出し側でemptyと区別します。
     */
    private fun resolveRegion(
        spec: RegionSpec,
        session: ExecutionSession,
        context: ExecutionContextSpec? = session.context,
    ): ResolvedRegion? {
        if (spec.kind != RegionKind.CUBOID) return null
        val origin = resolveRegionAnchor(spec, session, context) ?: return null
        if (origin.world != session.origin.world) return null
        return ResolvedRegion(
            origin = origin,
            dx = spec.dx?.takeIf { it.isFinite() && it >= 0.0 } ?: spec.dx?.let { return null },
            dy = spec.dy?.takeIf { it.isFinite() && it >= 0.0 } ?: spec.dy?.let { return null },
            dz = spec.dz?.takeIf { it.isFinite() && it >= 0.0 } ?: spec.dz?.let { return null },
            minimumDistance = spec.minimumDistance?.takeIf { it.isFinite() && it >= 0.0 }
                ?: spec.minimumDistance?.let { return null },
            maximumDistance = spec.maximumDistance?.takeIf { it.isFinite() && it >= 0.0 }
                ?: spec.maximumDistance?.let { return null },
        ).takeIf { it.minimumDistance == null || it.maximumDistance == null || it.minimumDistance <= it.maximumDistance }
    }

    private fun applyFacing(
        destination: Location,
        facing: me.awabi2048.kantancommander.model.FacingSpec,
        context: ExecutionContextSpec?,
        session: ExecutionSession,
    ) {
        // 意味付けはROTATIONの向き成分だけの指定です。他種別への付与は拒否します。
        if (facing.hasNonAbsoluteSemantics() && facing.kind != FacingKind.ROTATION) return
        when (facing.kind) {
            FacingKind.ROTATION -> {
                // 向きも成分ごとに解釈します。LOCALは角度に基準がないため未解決です。
                destination.yaw = resolveAngle(
                    facing.semanticsYaw, facing.yaw, destination.yaw, ::wrapMinecraftYaw,
                ) ?: return
                destination.pitch = resolveAngle(
                    facing.semanticsPitch, facing.pitch, destination.pitch, ::clampMinecraftPitch,
                ) ?: return
            }
            FacingKind.TARGET -> {
                // 注視は向き専用の単一体参照へ帰着します。旧来のcontext.target暗黙参照は使いません。
                val target = facing.target?.let { resolveTargetSpec(it, session, context)?.location }
                    ?: return
                faceLocation(destination, target)
            }
            FacingKind.COPY_ORIENTATION -> {
                // 向き複写は参照先実体と同じyaw/pitchを適用します。単一体のみに制限します。
                val source = facing.target?.let { resolveTargetSpec(it, session, context)?.location }
                    ?: return
                destination.yaw = source.yaw
                destination.pitch = source.pitch
            }
            FacingKind.COORDINATES -> faceLocation(
                destination,
                Location(
                    destination.world,
                    facing.x ?: return,
                    facing.y ?: return,
                    facing.z ?: return,
                ),
            )
            FacingKind.TEMPORARY -> {
                val temp = facing.tempName
                    ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                    ?.takeIf { it.type == ValueType.LOCATION }
                    ?.location ?: return
                faceLocation(destination, Location(destination.world, temp.x, temp.y, temp.z))
            }
        }
    }

    private fun resolveText(raw: String, session: ExecutionSession): String? {
        // 一時変数 `%{name}%` を先に展開し、残った `${name}` をワールド内変数として展開します。
        val tempExpanded = if (TemporaryTemplate.references(raw).isEmpty() && !raw.contains('%')) {
            raw
        } else {
            TemporaryTemplate.interpolateText(raw) { name ->
                when (name) {
                    SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toString()
                    else -> session.executionVariables.get(TemporaryTemplate.normalized(name))?.let(::stringifyTemporary)
                }
            } ?: return null
        }
        return VariableTemplate.interpolateText(tempExpanded) { name ->
            when (name) {
                SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toString()
                else -> plugin.variables.get(session.worldId, name)?.let(VariableTemplate::stringify)
            }
        }
            ?: tempExpanded.takeIf {
                VariableTemplate.references(it).isEmpty() && !VariableTemplate.hasMalformedReference(it) &&
                    TemporaryTemplate.references(it).isEmpty() && !TemporaryTemplate.hasMalformedReference(it)
            }
    }

    private fun resolveNumber(raw: String, session: ExecutionSession): Double? {
        val tempExpanded = if (TemporaryTemplate.references(raw).isEmpty() && !raw.contains('%')) {
            raw
        } else {
            TemporaryTemplate.interpolateText(raw) { name ->
                when (name) {
                    SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toString()
                    else -> session.executionVariables.get(TemporaryTemplate.normalized(name))
                        ?.takeIf { it.type == ValueType.NUMBER }
                        ?.numberValue?.takeIf(Double::isFinite)?.toString()
                }
            } ?: return null
        }
        val expanded = if (VariableTemplate.references(tempExpanded).isEmpty()) tempExpanded else {
            VariableTemplate.interpolateText(tempExpanded) { name ->
                when (name) {
                    SystemVariableNames.CURRENT_LOOP_COUNT -> session.currentLoopCount?.toString()
                    // 数値欄は文字列変数を暗黙にDoubleへ変換しません。保存値がたまたま
                    // 数字だけでも、定義型を越境するとGUI・実行・出力で意味が分岐するためです。
                    else -> plugin.variables.get(session.worldId, name)
                        ?.takeIf { it.type == ValueType.NUMBER }
                        ?.numberValue
                        ?.takeIf(Double::isFinite)
                        ?.toString()
                }
            } ?: return null
        }
        return expanded.toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    /**
     * 実行時にだけ確定する一時参照の欠損を、通常のノード失敗と分けて判定します。
     *
     * 静的検証は「その経路で定義が存在すること」までしか保証できません。特に
     * ENTITYは定義後に死亡・別ワールド移動・アンロードが起こるため、
     * executeImmediateへ渡してfalseを返すだけではスクリプト全体を停止してしまいます。
     * ここで参照先を確認し、呼び出し側がノード単位のSKIPPEDとして後続へ進めます。
     */
    private fun hasUnavailableTemporaryReference(node: CommandNode, session: ExecutionSession): Boolean {
        fun scalarReferencesAvailable(raw: String): Boolean =
            TemporaryTemplate.references(raw).all { name ->
                val value = session.executionVariables.get(TemporaryTemplate.normalized(name))
                value != null && value.type in setOf(ValueType.NUMBER, ValueType.STRING, ValueType.BOOLEAN)
            }

        // テキスト／数値欄に現れる `%{name}%` はスカラーだけを許可します。
        // POSITION等を空文字へ変換して実行を続けると、型エラーが成功に見えます。
        if (node.params.values.any { !scalarReferencesAvailable(it) }) return true

        fun entityAvailable(name: String?): Boolean {
            val value = name?.takeIf(String::isNotBlank)
                ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                ?: return false
            if (value.type != ValueType.ENTITY) return false
            val id = value.entityId ?: return false
            val entity = session.origin.world.getEntity(id) ?: return false
            return entity.isValid &&
                !entity.isDead &&
                entity.world == session.origin.world &&
                !systemEntityRegistry.isSystemEntity(entity)
        }

        fun positionAvailable(name: String?): Boolean {
            val value = name?.takeIf(String::isNotBlank)
                ?.let { session.executionVariables.get(TemporaryTemplate.normalized(it)) }
                ?: return false
            return value.type == ValueType.LOCATION && value.location != null
        }

        fun targetUnavailable(spec: TargetSpec?): Boolean =
            spec?.kind == TargetKind.TEMPORARY && !entityAvailable(spec.tempName)

        fun positionUnavailable(spec: PositionSpec?): Boolean =
            spec?.kind == PositionKind.TEMPORARY && !positionAvailable(spec.tempName)

        fun facingUnavailable(spec: me.awabi2048.kantancommander.model.FacingSpec?): Boolean =
            spec?.kind == FacingKind.TEMPORARY && !positionAvailable(spec.tempName)

        val effective = effectiveContext(session)
        return listOf(
            node.targetSpec,
            node.secondaryTargetSpec,
            node.destinationTargetSpec,
            effective?.target,
            effective?.executor,
        ).any(::targetUnavailable) || listOf(
            node.destinationSpec,
            node.conditionPositionSpec,
            node.blockPositionSpec,
            node.blockFromSpec,
            node.blockToSpec,
            node.soundPositionSpec,
            node.particlePositionSpec,
            node.summonPositionSpec,
            effective?.position,
        ).any(::positionUnavailable) || listOf(
            node.destinationFacingSpec,
            effective?.facing,
        ).any(::facingUnavailable) || listOfNotNull(
            node.targetSpec?.searchOrigin,
            node.secondaryTargetSpec?.searchOrigin,
            node.destinationTargetSpec?.searchOrigin,
            effective?.target?.searchOrigin,
            effective?.executor?.searchOrigin,
        ).any { search ->
            search.positionTemp?.let { !positionAvailable(it) } == true ||
                positionUnavailable(search.position)
        } || listOfNotNull(
            node.itemTempRef to ValueType.ITEM,
            node.blockTempRef to ValueType.BLOCK,
            node.soundTempRef to ValueType.SOUND,
            node.effectTempRef to ValueType.EFFECT,
        ).any { (name, expected) ->
            name?.takeIf(String::isNotBlank)?.let {
                session.executionVariables.get(TemporaryTemplate.normalized(it))?.type != expected
            } == true
        }
    }

    /** 一時変数値を文字列へ変換します。複合型は暗黙に空文字へ変換しません。 */
    private fun stringifyTemporary(value: TypedValue): String? = when (value.type) {
        ValueType.NUMBER -> value.numberValue?.let {
            if (it.isFinite() && it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }
        ValueType.STRING -> value.stringValue
        ValueType.BOOLEAN -> value.booleanValue?.toString()
        else -> null
    }

    private fun displayTiming(node: CommandNode, session: ExecutionSession): DisplayTextTiming {
        fun value(key: String, fallback: Double): Double =
            resolveNumber(node.string(key), session)
                ?.takeIf(CommandValueRules::isDisplayTimeSeconds)
                ?: fallback
        return DisplayTextTiming(
            value("fadeInSeconds", 1.0),
            value("staySeconds", 3.0),
            value("fadeOutSeconds", 1.0),
        )
    }

    private fun legacyText(raw: String): Component =
        LegacyComponentSerializer.legacySection().deserialize(raw.replace('&', '§'))

    /** 数値テンプレートを実行値の正のIntへ変換する共通境界です。 */
    private fun resolvePositiveInt(raw: String, session: ExecutionSession): Int? {
        val value = resolveNumber(raw, session) ?: return null
        if (value != kotlin.math.floor(value) || value <= 0.0 || value > Int.MAX_VALUE.toDouble()) return null
        return value.toInt()
    }

    /** overwrite=false の配置可否を、空スロット判定で統一します。 */
    private fun isEmptyStack(stack: ItemStack?): Boolean = stack == null || stack.type.isAir

    /** プレイヤー状態の所持判定は、メインハンド由来のItemStack情報も尊重します。 */
    private fun playerHasItem(player: Player, itemId: String, itemData: String): Boolean {
        val material = CommandValueRules.material(itemId, allowAir = false) ?: return false
        val required = itemData.takeIf(String::isNotBlank)?.let(ItemStackCodec::decode)
        return player.inventory.contents.any { stack ->
            stack != null && !stack.type.isAir && stack.type == material &&
                (required == null || stack.isSimilar(required))
        }
    }

    private fun faceLocation(destination: Location, target: Location) {
        if (destination.world != target.world) return
        val direction = target.toVector().subtract(destination.toVector())
        if (direction.lengthSquared() > 0.0) destination.direction = direction
    }

    private fun stop(
        session: ExecutionSession,
        script: DiskScript,
        nodeId: UUID,
        depth: Int,
        reason: String,
        done: (Boolean) -> Unit,
    ) {
        if (session.failureReason == null) session.failureReason = reason
        if (depth == 0 && session.failedNodeId == null) session.failedNodeId = nodeId
        plugin.logger.warning(
            "[KantanCommander] forced-stop root=${session.rootId} disk=${script.id} node=$nodeId " +
                "reason=$reason executed=${session.executed} limit=${session.budget} depth=$depth " +
                "world=${session.origin.world.name} " +
                "location=${session.origin.blockX},${session.origin.blockY},${session.origin.blockZ}"
        )
        if (!session.options.suppressCreatorFailureNotification) notifyCreatorOfFailure(session)
        done(false)
    }

    /**
     * 実行途中の失敗を最上位プログラムの作成者へ一度だけ通知します。
     * DISK_CALLの入れ子や非同期WAITから同じ失敗経路へ戻っても、作成者への通知が
     * 重複しないようセッション単位で抑制します。オフラインの場合は送信先がないため、
     * 実行ログだけを正本として通知を捨てます。
     */
    private fun notifyCreatorOfFailure(session: ExecutionSession) {
        if (session.failureNotified) return
        session.failureNotified = true
        val creator = plugin.server.getPlayer(session.creatorId) ?: return
        creator.sendMessage("§cかんたんコマンダープログラム【${session.programName}】の実行に失敗しました。")
        creator.playSound(
            creator.location,
            "minecraft:block.note_block.bit",
            SoundCategory.MASTER,
            1.0f,
            0.5f,
        )
    }

    /** 即時ノード処理結果。Particle上限超過は失敗ではなく、そのノードだけを省略します。 */
    private enum class ImmediateExecutionResult { SUCCESS, SKIPPED, FAILED }

    /**
     * 条件評価の3状態です。
     *
     * issue #6 で「値解決失敗=false」を無条件に採用しないため、正常な比較結果と
     * 解決失敗を分離します。TRUE/FALSE は分岐継続、UNRESOLVED/INVALID は
     * RETURN_ERROR 相当の失敗として停止します。
     */
    private enum class ConditionEvaluation {
        TRUE,
        FALSE,
        UNRESOLVED,
        INVALID,
    }

    /** 上限超過を通常の実行例外と区別する内部制御例外です。 */
    private class ParticleQuotaRejected : RuntimeException()

    private data class ExecutionSession(
        val rootId: UUID,
        val origin: Location,
        val actor: Player?,
        /** 入れ子呼び出しでも通知先を変えないため、最上位スクリプトの作成者を保持します。 */
        val creatorId: UUID,
        val programName: String,
        val budget: Int,
        val maxDepth: Int,
        val worldId: UUID,
        val options: ExecutionOptions,
        val startTick: Long,
        val handle: ExecutionHandle,
        val callback: (ExecutionResult) -> Unit,
        var executed: Int = 0,
        var failureNotified: Boolean = false,
        var attemptCount: Int = 0,
        var successfulRootNodeCount: Int = 0,
        /** WAITコマンドの待機tick合計と、デバッグ待機のtick合計です。 */
        var waitTicks: Long = 0L,
        var debugWaitTicks: Long = 0L,
        var currentAttemptNumber: Int = 0,
        var currentRootNodeId: UUID? = null,
        var failedNodeId: UUID? = null,
        var failureReason: String? = null,
        var lastDetail: String? = null,
        /**
         * issue #32 のレビューF4対応：部分適用の構造化結果です。
         *
         * 件数表示は共通成功処理が型付き言語キーへ変換します。各操作が
         * 詳細文字列を直接書くと成功時に上書きで失われるため、計数だけを
         * 保持し、表示への変換責務を持ちません。
         */
        var lastPartial: PartialApplicability? = null,
        var lastExpandedParams: Map<String, String> = emptyMap(),
        var lastPositionText: String? = null,
        var lastFailureReason: String? = null,
        /** issue #6 の共通コード保持です。文字列 reason と対で更新します。 */
        var failureCode: ErrorCode? = null,
        var lastFailureCode: ErrorCode? = null,
        var cancelled: Boolean = false,
        var finished: Boolean = false,
        var context: ExecutionContextSpec? = null,
        var previousContext: ExecutionContextSpec? = null,
        val loops: MutableList<LoopFrame> = mutableListOf(),
        var currentLoopCount: Long? = null,
        /** EXECUTION scope の実行内保持域です。実行終了時に破棄し、DISK_CALL越境では隔離します。 */
        val executionVariables: ExecutionVariables = ExecutionVariables(),
        /**
         * issue #8 の呼び出し枠の積み上げです。
         *
         * DISK_CALL のたびに呼出元の変数表・戻り先情報を積み、復帰時に戻します。
         * WORLD／ENTITY のstoreは複製しません。WAITによる中断をまたいでも
         * 維持されるため、復帰後の復元が崩れません。
         */
        val callFrames: MutableList<ExecutionCallFrame> = mutableListOf(),
        /** 「値を返す」で中断が要求された場合に真になります。 */
        var returnRequested: Boolean = false,
        /** 中断時に呼出元へ渡す戻り値です。 */
        var returnValue: TypedValue? = null,
    ) {
        val isActive: Boolean get() = !cancelled && !finished
        val scheduledTasks: MutableSet<BukkitTask> = linkedSetOf()
    }

    /**
     * issue #8 の呼び出し枠です。
     *
     * callerVariables は呼出元のEXECUTION変数表の複写であり、復帰時に戻します。
     * pendingReturn に値がある枠へ戻った場合、その呼出しの戻り値として
     * 受け渡します。returnScope／returnName が空の場合は値を破棄します。
     */
    private data class ExecutionCallFrame(
        val scriptId: UUID,
        val nodeId: UUID,
        val diskId: UUID?,
        val callerVariables: Map<String, TypedValue>,
        val callerLoops: List<LoopFrame>,
        val callerLoopCount: Long?,
        val returnScope: VariableScope?,
        val returnName: String,
        var pendingReturn: TypedValue? = null,
    )

    private data class LoopFrame(
        val startId: UUID,
        val endId: UUID,
        val limit: Long,
        var count: Long,
        val startContext: ExecutionContextSpec?,
    )
}

internal object ExecutionSemantics {
    fun conditionResult(rawResult: Boolean, inverted: Boolean): Boolean =
        rawResult xor inverted

    fun withinBudget(executed: Int, budget: Int): Boolean =
        executed < budget

    fun withinCallDepth(currentDepth: Int, maximumDepth: Int): Boolean =
        currentDepth < maximumDepth

    /**
     * 回数指定ループの継続判定を一箇所へ集約します。
     *
     * 実行器とテストがそれぞれ境界条件を持つと、1回多く実行する差異が
     * 保存データや出力先によって生じます。現在回数が上限未満のときだけ
     * 次の反復へ進む、という新仕様の境界を共通化します。
     */
    fun shouldRunNextLoopIteration(currentCount: Long, limit: Long): Boolean =
        currentCount > 0 && limit > 0 && currentCount < limit

    /** 次の回数を安全に計算し、符号付き64bitの上限では停止できるようにします。 */
    fun nextLoopCount(currentCount: Long): Long? =
        currentCount.takeIf { it < Long.MAX_VALUE }?.plus(1)

    fun mergeContexts(
        inherited: ExecutionContextSpec?,
        override: ExecutionContextSpec?,
    ): ExecutionContextSpec? {
        if (override == null || !override.hasAnySetting()) return inherited
        if (inherited == null) return override
        return ExecutionContextSpec(
            executor = override.executor ?: inherited.executor,
            target = override.target ?: inherited.target,
            position = override.position ?: inherited.position,
            facing = override.facing ?: inherited.facing,
        )
    }

}
