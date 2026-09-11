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
import me.awabi2048.kantancommander.model.ControlBlockStateConditionPolicy
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.TemporaryValue
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.normalizedTemporaryName
import me.awabi2048.kantancommander.model.selectedControlBlockStates
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.WorldVariableValue
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.CoordinateSemantics
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
import me.awabi2048.kantancommander.model.ParticleDisplayMode
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import com.awabi2048.ccsystem.CCSystem
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.SoundCategory
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.logging.Level
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import me.awabi2048.kantancommander.item.ItemStackCodec
import org.bukkit.inventory.EquipmentSlot
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
        val script = plugin.scripts.load(scriptId) ?: return callback(false)
        startExecution(
            script = script,
            origin = origin,
            actor = actor,
            options = ExecutionOptions(),
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
                // 一時変数は呼出先へ引き継がず、復帰時に復元します（隔離）。
                val callerTemporaries = session.temporaries.toMap()
                session.temporaries.clear()
                // 実行状態はDISK_CALLの境界を越えて共有しません。現在はGUIから
                // コンテキストを生成しませんが、内部呼出し元が保持している実行状態
                // を呼出先の処理へ渡す境界として、この保存・復元を残します。
                session.context = effectiveContext(session)
                runDiskCall(node, session, depth) { success ->
                    session.context = callerContext
                    session.previousContext = callerPrevious
                    session.temporaries.clear()
                    session.temporaries.putAll(callerTemporaries)
                    session.lastDetail = if (success) "ディスク呼び出しに成功した" else "ディスク呼び出しに失敗した"
                    next(
                        node.next,
                        if (success) NodeExecutionOutcome.SUCCESS else NodeExecutionOutcome.FAILED,
                    )
                }
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
        CommandType.ENTITY_ACTION -> "対象へエンティティ操作を行った"
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

    private fun runDiskCall(node: CommandNode, session: ExecutionSession, depth: Int, done: (Boolean) -> Unit) {
        if (!ExecutionSemantics.withinCallDepth(depth, session.maxDepth)) {
            plugin.logger.warning("[KantanCommander] disk-call-depth root=${session.rootId} node=${node.id} depth=$depth max=${session.maxDepth}")
            return done(false)
        }
        val snapshot = node.snapshot ?: return done(false)
        val synthetic = DiskScript(name = "snapshot", owner = session.actor?.uniqueId ?: UUID(0, 0), graph = snapshot)
        runGraph(synthetic, snapshot, session, depth + 1, done)
    }

    private fun executeImmediate(node: CommandNode, session: ExecutionSession): ImmediateExecutionResult = try {
        if (executeImmediateBoolean(node, session)) {
            if (session.lastDetail.isNullOrBlank()) session.lastDetail = immediateDetail(node.type)
            ImmediateExecutionResult.SUCCESS
        } else {
            if (session.lastDetail.isNullOrBlank()) {
                session.lastDetail = "${immediateDetail(node.type)}（実行に失敗）"
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
        session.lastDetail = "${immediateDetail(node.type)}（実行に失敗: $reason）"
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
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.ITEM }
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
                    "equip" -> {
                        val refTemp = node.itemTempRef?.takeIf(String::isNotBlank)
                            ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                            ?.takeIf { it.type == TemporaryVariableType.ITEM }
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
                        val slot = runCatching { EquipmentSlot.valueOf(node.string("slot")) }.getOrNull() ?: return false
                        val overwrite = node.boolean("overwrite")
                        val applicable = targets.filterIsInstance<LivingEntity>().mapNotNull { it.equipment }
                        if (applicable.isEmpty()) return false
                        applicable.forEach { equipment ->
                            if (overwrite || equipment.getItem(slot).type.isAir) {
                                equipment.setItem(slot, template.clone())
                            }
                        }
                        true
                    }
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
                    else -> false
                }
            }
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
            CommandType.FIREWORK_LAUNCH -> executeFirework(node, session, effectiveContext, effectiveOrigin, origins, attempts)
            CommandType.APPLY_EFFECT -> {
                val refTemp = node.effectTempRef?.takeIf(String::isNotBlank)
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.EFFECT }
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
            session.lastDetail = content.head
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
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.SOUND }
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
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.SOUND }
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
     * Particleの送信は、対象プレイヤー指定がなければ表示中心と同じワールドの
     * 全プレイヤーへ1回のパケット送信で届けます。
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
        val particle = ParticleSettings.particle(node) ?: return false
        // issue #29: コマンド固有位置があればそちらを優先し、なければ実行起点集合を使います。
        val centers = node.particlePositionSpec?.let {
            actionOrigins(it, session, context) ?: return false
        } ?: origins
        val receivers = if (node.targetSpec == null) effectiveOrigin.world.players else targets.filterIsInstance<Player>()
        val data = if (ParticleSettings.requiresData(particle)) {
            ParticleSettings.parseData(particle, node.string(ParticleSettings.PARAM_DATA))
                .getOrNull() ?: return false
        } else {
            // 詳細データを必要としない種類は保存値を無視し、旧保存の残存値で
            // 実行を止めません。検証側も同じく対象外にしています。
            ParticleSettings.ParticleDataSpec.None
        }
        val bukkitData = data.toBukkitData(effectiveOrigin)
        if (ParticleSettings.requiresData(particle) && bukkitData == null) return false

        return when (ParticleGeometry.displayMode(node)) {
            ParticleDisplayMode.POINT -> {
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
                            particle,
                            receivers,
                            null,
                            origin.x,
                            origin.y,
                            origin.z,
                            count,
                            deltaX,
                            deltaY,
                            deltaZ,
                            speed,
                            bukkitData,
                            true,
                        )
                    }.isSuccess)
                }
                attempts.overall
            }
            ParticleDisplayMode.GEOMETRY -> {
                centers.forEach { origin ->
                    attempts.record(
                        runCatching {
                            executeGeometryParticle(node, session, context, origin, receivers, particle, bukkitData)
                        }.getOrElse { failure ->
                            // 上限超過はスキップ扱いを維持し、他は失敗として記録します。
                            if (failure is ParticleQuotaRejected) throw failure
                            false
                        },
                    )
                }
                attempts.overall
            }
        }
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

    private fun executeBlockOperation(
        node: CommandNode,
        session: ExecutionSession,
        context: ExecutionContextSpec?,
        origins: List<Location>,
        attempts: MultiAttemptRecorder,
    ): Boolean {
        val refTemp = node.blockTempRef?.takeIf(String::isNotBlank)
            ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
            ?.takeIf { it.type == TemporaryVariableType.BLOCK }
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
                if (value.type != VariableType.NUMBER) return ConditionEvaluation.INVALID
                val comparisonValue = resolveNumber(node.string("value"), session)
                    ?: return ConditionEvaluation.UNRESOLVED
                val operator = node.string("operator", "==")
                if (operator !in setOf("==", "!=", ">", "<", ">=", "<=")) {
                    return ConditionEvaluation.INVALID
                }
                val applicable = compareVariable(value, comparisonValue, operator)
                if (applicable) ConditionEvaluation.TRUE else ConditionEvaluation.FALSE
            }
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
            ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
            ?.takeIf { it.type == TemporaryVariableType.ITEM }
        val item = refTemp?.item ?: node.string("item")
        val itemData = refTemp?.itemData ?: node.string("itemData")
        val itemMatches = item.isBlank() || playerHasItem(player, item, itemData)
        return sneakingMatches && itemMatches
    }

    private fun executeVariable(
        node: CommandNode,
        session: ExecutionSession,
    ): Boolean = runCatching {
        val name = node.string("name")
        val operation = VariableOperation.valueOf(node.string("operation", VariableOperation.DEFINE.name))
        val current = plugin.variables.get(session.worldId, name)
        // CHANGEではGUI上のtypeを編集させず、MyWorldに定義済みの型を唯一の正とします。
        // 古いノードに残ったtypeを参照すると、STRING変数の変更がNUMBERとして拒否されます。
        val type = when (operation) {
            VariableOperation.DEFINE -> VariableType.valueOf(node.string("type", VariableType.NUMBER.name))
            VariableOperation.CHANGE -> current?.type ?: return false
        }
        val value = when (operation) {
            VariableOperation.DEFINE -> {
                if (current != null) return false
                parseAssignedValue(type, node.string("value"), session)
            }
            VariableOperation.CHANGE -> {
                when (type) {
                    VariableType.NUMBER -> when (VariableChangeMode.valueOf(node.string("changeMode", VariableChangeMode.ASSIGN.name))) {
                        VariableChangeMode.ASSIGN -> parseAssignedValue(type, node.string("value"), session)
                        VariableChangeMode.CALCULATE -> {
                            val expression = NumericExpression.parse(node.string("value")).expression ?: return false
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
                                    session.temporaries[reference.normalizedTemporaryName()]
                                        ?.takeIf { it.type == TemporaryVariableType.NUMBER }
                                        ?.numberValue?.takeIf(Double::isFinite)
                                },
                            ) ?: return false
                            WorldVariableValue(VariableType.NUMBER, numberValue = result)
                        }
                    }
                    VariableType.STRING -> parseAssignedValue(type, node.string("value"), session)
                }
            }
        }
        when (operation) {
            VariableOperation.DEFINE -> plugin.variables.define(session.worldId, name, value)
            VariableOperation.CHANGE -> {
                plugin.variables.set(session.worldId, name, value)
                true
            }
        }
    }.getOrDefault(false)

    /**
     * 一時変数を設定します。再設定は上書きとして扱います。
     *
     * NUMBER・STRING はリテラル値を受け付け、`%{name}%` 参照の展開に対応します。
     * 複合6型は型ごとの共通設定で解決済み値を保持する想定であり、
     * ここでは型ごとの最小検証のうえ保存します。
     */
    private fun executeTemporary(node: CommandNode, session: ExecutionSession): Boolean = runCatching {
        val name = me.awabi2048.kantancommander.model.TemporaryTemplate.normalized(node.string("name"))
        require(CommandValueRules.isVariableName(name)) { "invalid temporary name" }
        val type = TemporaryVariableType.parse(
            node.string("tempType", TemporaryVariableType.NUMBER.name),
        ) ?: return false
        val value = when (type) {
            TemporaryVariableType.NUMBER -> TemporaryValue(
                type,
                numberValue = resolveNumber(node.string("value"), session) ?: error("number must be finite"),
            )
            TemporaryVariableType.STRING -> TemporaryValue(
                type,
                stringValue = resolveText(node.string("value"), session) ?: error("string is unavailable"),
            )
            TemporaryVariableType.LOCATION -> {
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
                TemporaryValue(
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
            TemporaryVariableType.ITEM -> {
                val material = CommandValueRules.material(node.string("item"), allowAir = false)
                    ?: error("invalid item")
                TemporaryValue(type, item = material.name, itemData = node.string("itemData"))
            }
            TemporaryVariableType.BLOCK -> {
                val material = CommandValueRules.placementMaterial(node.string("block"))
                    ?: error("invalid block")
                TemporaryValue(type, block = material.name)
            }
            TemporaryVariableType.ENTITY -> {
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
                TemporaryValue(type, entityId = entityId)
            }
            TemporaryVariableType.SOUND -> {
                if (!CommandValueRules.isSoundId(node.string("sound"))) error("invalid sound")
                val volume = resolveNumber(node.string("volume", "1.0"), session)
                    ?.takeIf { it.isFinite() && it in 0.0..34.0 } ?: error("invalid volume")
                val pitch = resolveNumber(node.string("pitch", "1.0"), session)
                    ?.takeIf { it.isFinite() && it in 0.5..2.0 } ?: error("invalid pitch")
                TemporaryValue(type, sound = node.string("sound"), volume = volume, pitch = pitch)
            }
            TemporaryVariableType.EFFECT -> {
                if (!CommandValueRules.isEffectId(node.string("effect"))) error("invalid effect")
                val level = resolveNumber(node.string("level", "1"), session)
                    ?.takeIf { it == kotlin.math.floor(it) && it in 1.0..255.0 }
                    ?.toInt() ?: error("invalid level")
                val seconds = resolveNumber(node.string("seconds", "30"), session)
                    ?.takeIf { it == kotlin.math.floor(it) && it in 1.0..86_400.0 }
                    ?.toInt() ?: error("invalid seconds")
                TemporaryValue(type, effect = node.string("effect"), level = level, seconds = seconds)
            }
        }
        session.temporaries[name] = value
        true
    }.getOrDefault(false)

    private fun parseAssignedValue(type: VariableType, raw: String, session: ExecutionSession): WorldVariableValue {
        return when (type) {
            VariableType.NUMBER -> WorldVariableValue(
                VariableType.NUMBER,
                numberValue = resolveNumber(raw, session) ?: error("number must be finite"),
            )
            // STRING代入もGUI入力と同じ共通上限へ揃えます。超過代入はGUIで入力できない
            // 値と同じ扱いとして失敗(原因表示)にするため、保存層の不変条件を崩しません。
            VariableType.STRING -> {
                val text = resolveText(raw, session) ?: error("string variable is unavailable")
                require(text.length <= CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH) {
                    "world variable string exceeds ${CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH} characters"
                }
                WorldVariableValue(VariableType.STRING, stringValue = text)
            }
        }
    }

    private fun compareVariable(value: WorldVariableValue?, comparisonValue: Double, operator: String): Boolean {
        val actual = value?.takeIf { it.type == VariableType.NUMBER }?.numberValue ?: return false
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
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.ENTITY }
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
                val temp = session.temporaries[TemporaryTemplate.normalized(name)]
                    ?.takeIf { it.type == TemporaryVariableType.LOCATION }
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
                ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.LOCATION }
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
                ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                ?.takeIf { it.type == TemporaryVariableType.LOCATION }
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
                    ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                    ?.takeIf { it.type == TemporaryVariableType.LOCATION }
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
                    else -> session.temporaries[TemporaryTemplate.normalized(name)]?.let(::stringifyTemporary)
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
                    else -> session.temporaries[TemporaryTemplate.normalized(name)]
                        ?.takeIf { it.type == TemporaryVariableType.NUMBER }
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
                        ?.takeIf { it.type == VariableType.NUMBER }
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
                val value = session.temporaries[TemporaryTemplate.normalized(name)]
                value != null && value.type in setOf(TemporaryVariableType.NUMBER, TemporaryVariableType.STRING)
            }

        // テキスト／数値欄に現れる `%{name}%` はスカラーだけを許可します。
        // POSITION等を空文字へ変換して実行を続けると、型エラーが成功に見えます。
        if (node.params.values.any { !scalarReferencesAvailable(it) }) return true

        fun entityAvailable(name: String?): Boolean {
            val value = name?.takeIf(String::isNotBlank)
                ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                ?: return false
            if (value.type != TemporaryVariableType.ENTITY) return false
            val id = value.entityId ?: return false
            val entity = session.origin.world.getEntity(id) ?: return false
            return entity.isValid &&
                !entity.isDead &&
                entity.world == session.origin.world &&
                !systemEntityRegistry.isSystemEntity(entity)
        }

        fun positionAvailable(name: String?): Boolean {
            val value = name?.takeIf(String::isNotBlank)
                ?.let { session.temporaries[TemporaryTemplate.normalized(it)] }
                ?: return false
            return value.type == TemporaryVariableType.LOCATION && value.location != null
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
            node.itemTempRef to TemporaryVariableType.ITEM,
            node.blockTempRef to TemporaryVariableType.BLOCK,
            node.soundTempRef to TemporaryVariableType.SOUND,
            node.effectTempRef to TemporaryVariableType.EFFECT,
        ).any { (name, expected) ->
            name?.takeIf(String::isNotBlank)?.let {
                session.temporaries[TemporaryTemplate.normalized(it)]?.type != expected
            } == true
        }
    }

    /** 一時変数値を文字列へ変換します。複合型は暗黙に空文字へ変換しません。 */
    private fun stringifyTemporary(value: TemporaryValue): String? = when (value.type) {
        TemporaryVariableType.NUMBER -> value.numberValue?.let {
            if (it.isFinite() && it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }
        TemporaryVariableType.STRING -> value.stringValue
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
        /** 一時変数の実行内保持域です。実行終了時に破棄し、DISK_CALL越境では隔離します。 */
        val temporaries: MutableMap<String, TemporaryValue> = linkedMapOf(),
    ) {
        val isActive: Boolean get() = !cancelled && !finished
        val scheduledTasks: MutableSet<BukkitTask> = linkedSetOf()
    }

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
