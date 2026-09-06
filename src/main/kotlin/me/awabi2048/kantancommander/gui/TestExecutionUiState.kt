package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.execution.ExecutionNodeFinished
import me.awabi2048.kantancommander.execution.ExecutionNodeStarted
import me.awabi2048.kantancommander.execution.ExecutionResult
import me.awabi2048.kantancommander.execution.ExecutionObserver
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import java.util.ArrayDeque
import java.util.UUID

/** テスト実行の画面状態です。処理本体から独立しているため、オーナー退出後も実行を継続できます。 */
enum class GestureTestPhase {
    CONFIRM,
    RUNNING,
    RESULT,
}

/** 実行済み経路の論理エッジです。ノードIDだけでは条件のTRUE/FALSEを区別できません。 */
data class TestExecutionEdge(
    val sourceId: UUID,
    val edge: GraphEditor.Edge,
)

data class GestureTestState(
    val scopeKey: String,
    /** 確定クリック時に複製した、テスト専用の実行スナップショットです。 */
    var snapshot: DiskScript,
    val ownerId: UUID,
    val originalOrigin: MapPoint,
    val originalZoomLevel: Int,
    val originalSelectedNodeId: UUID?,
    val originalLowerMode: GestureLowerMode,
    var phase: GestureTestPhase = GestureTestPhase.CONFIRM,
    var debugMode: Boolean = false,
    var logOutput: Boolean = false,
    var currentNodeId: UUID? = null,
    var successfulNodeIds: MutableSet<UUID> = linkedSetOf(),
    var successfulNodeCount: Int = 0,
    var passedEdges: MutableSet<TestExecutionEdge> = linkedSetOf(),
    /** 次の開始で白に戻すループ内ノード集合です。復帰完了時に保持します。 */
    var pendingLoopRestartNodes: Set<UUID>? = null,
    /** 再開時に黄緑へ戻すS→body再入場枝です。復帰完了時に保持します。 */
    var pendingRestartEdge: TestExecutionEdge? = null,
    /** 待機表示中のループ戻り経路のFOR_ENDです。水色で示します。 */
    var waitingLoopReturnId: UUID? = null,
    /** 待機表示中の次の枝です。待機が20tickを超えるときだけ設定されます。 */
    var waitingEdge: TestExecutionEdge? = null,
    /** 待機表示中のWAITノードです。青緑で示します。 */
    var waitingNodeId: UUID? = null,
    var failedNodeId: UUID? = null,
    var startedAtTick: Long = 0L,
    var elapsedTicks: Long = 0L,
    var result: ExecutionResult? = null,
)

/**
 * 白から水色への切り替わりまでの遅延です。待機自体がこのtick数以下の場合は
 * 待機表示を出さず、次の実行へ直接進みます。水色から緑は次実行時に切り替わるため、
 * 40tick待機では白20・水色20の均等表示になります。
 */
internal const val TEST_WAIT_HIGHLIGHT_DELAY_TICKS: Long = 20L

/** 実行イベントを共有エディター状態へ投影し、同じ配置を見ている全員へ表示します。 */
class GestureTestExecutionObserver(
    private val state: GestureTestState,
    private val onChanged: () -> Unit,
    private val onLog: (ExecutionNodeFinished) -> Unit,
    private val onResult: (ExecutionResult) -> Unit,
    /** 待機表示タイマー用です。nullのときは待機表示を行いません（試験用）。 */
    private val plugin: org.bukkit.plugin.Plugin? = null,
    private val scheduler: org.bukkit.scheduler.BukkitScheduler? = null,
) : ExecutionObserver {
    private var pendingWaitTask: org.bukkit.scheduler.BukkitTask? = null
    /**
     * 移動中の枝です。完了時点では白のままにし、次のノードの開始で
     * 通過確定（黄緑）します。白→水色→緑の順に切り替えるためです。
     */
    private var pendingEdge: TestExecutionEdge? = null

    /** 終了・中断・破棄時に残っている待機タイマーを解除します。 */
    fun cancelPendingWait() {
        pendingWaitTask?.cancel()
        pendingWaitTask = null
        state.waitingEdge = null
        state.waitingNodeId = null
        state.waitingLoopReturnId = null
    }

    override fun onNodeStarted(event: ExecutionNodeStarted) {
        if (event.depth != 0) return
        cancelPendingWait()
        // ループ復帰後の再開では、戻り経路とループ内の通過表示を白に戻します。
        // 開始ノード自体は集合外のため、開始→本体の枝は維持されます。
        state.pendingLoopRestartNodes?.let { loopNodes ->
            state.passedEdges.removeAll { it.sourceId in loopNodes }
            state.successfulNodeIds.removeAll(loopNodes)
            state.pendingLoopRestartNodes = null
        }
        // S相当の再入場枝を黄緑に戻します。待機中は白のままでした。
        state.pendingRestartEdge?.let(state.passedEdges::add)
        state.pendingRestartEdge = null
        // 移動中の枝を通過確定します。開始前は白のままです。
        pendingEdge?.let(state.passedEdges::add)
        pendingEdge = null
        state.currentNodeId = event.nodeId
        // WAITコマンドの待機が長いときは、20tick後に待機表示へ切り替えます。
        if (event.waitTicks > TEST_WAIT_HIGHLIGHT_DELAY_TICKS) {
            scheduleWaitHighlight {
                if (state.phase != GestureTestPhase.RUNNING || state.currentNodeId != event.nodeId) return@scheduleWaitHighlight
                state.waitingNodeId = event.nodeId
                state.snapshot.graph.nodes[event.nodeId]?.next?.let { nextId ->
                    state.waitingEdge = edgeFor(state.snapshot, event.nodeId, nextId)
                }
                onChanged()
            }
        }
        onChanged()
    }

    override fun onNodeFinished(event: ExecutionNodeFinished) {
        if (event.depth != 0) return
        cancelPendingWait()
        if (event.outcome != me.awabi2048.kantancommander.execution.NodeExecutionOutcome.FAILED) {
            // ノード処理が成功した時点で、そのノードは実行中ではありません。
            // デバッグ待機中も水色を残すと「再度実行している」ように見えるため、
            // 次のNodeStartedまで実行中ノードを空にして成功色へ移します。
            state.currentNodeId = null
            state.successfulNodeCount = event.successfulNodeCount
            state.successfulNodeIds += event.nodeId
            // 直前までの移動を通過確定し、今回の移動を移動中として保持します。
            // 完了直後に黄緑へ変えると、待機表示の前に緑→水色→緑とちらつきます。
            pendingEdge?.let(state.passedEdges::add)
            pendingEdge = edgeFor(state.snapshot, event.nodeId, event.nextNodeId)
            if (event.loopReturn) {
                // 復帰完了時点では何も消さず、次の開始で一括して白に戻します。
                // 待機が長いとき（デバッグON）は20tick後に戻り経路とS相当を水色・実行中表示にし、
                // 再開と同時に白へ戻します。回数表示・ログ・所要時間は変えません。
                state.pendingLoopRestartNodes = loopNodeIds(event.nodeId)
                if (event.nextWaitTicks > TEST_WAIT_HIGHLIGHT_DELAY_TICKS) {
                    // S相当の表示用に、SとS→body枝を白に戻して再入場枝として保持します。
                    // Sの実行は完了していない扱いのため、成功集合から外します。
                    val startId = state.snapshot.graph.nodes[event.nodeId]?.pairedNodeId
                    if (startId != null) {
                        edgeFor(state.snapshot, startId, state.snapshot.graph.nodes[startId]?.trueNext)?.let { reentry ->
                            state.passedEdges.remove(reentry)
                            state.successfulNodeIds.remove(startId)
                            state.pendingRestartEdge = reentry
                        }
                    }
                    scheduleWaitHighlight {
                        if (state.phase != GestureTestPhase.RUNNING) return@scheduleWaitHighlight
                        state.waitingLoopReturnId = event.nodeId
                        // S相当の表示です。Sを実行中表示し、枝を水色にします。
                        state.pendingRestartEdge?.let { edge ->
                            state.currentNodeId = edge.sourceId
                            state.waitingEdge = edge
                        }
                        onChanged()
                    }
                }
            }
            // 次までの待機が長いときは、20tick後に次の枝を待機表示へ切り替えます。
            if (event.nextWaitTicks > TEST_WAIT_HIGHLIGHT_DELAY_TICKS) {
                val edge = edgeFor(state.snapshot, event.nodeId, event.nextNodeId)
                if (edge != null) {
                    scheduleWaitHighlight {
                        if (state.phase != GestureTestPhase.RUNNING) return@scheduleWaitHighlight
                        state.waitingEdge = edge
                        onChanged()
                    }
                }
            }
        } else {
            state.failedNodeId = event.nodeId
        }
        state.elapsedTicks = (event.tick - state.startedAtTick).coerceAtLeast(0L)
        onLog(event)
        onChanged()
    }

    override fun onFinished(result: ExecutionResult) {
        cancelPendingWait()
        state.pendingLoopRestartNodes = null
        state.pendingRestartEdge = null
        onResult(result)
    }

    private fun scheduleWaitHighlight(action: () -> Unit) {
        val owner = plugin ?: return
        val tasks = scheduler ?: return
        pendingWaitTask?.cancel()
        pendingWaitTask = tasks.runTaskLater(owner, Runnable(action), TEST_WAIT_HIGHLIGHT_DELAY_TICKS)
    }

    /**
     * 対応FOR_STARTからFOR_ENDまでの、現在ループ内部の最上位ノード集合です。
     * 開始ノード自身は含みません。以後の反復は開始ノードを経由しないため、
     * 含めると開始→本体の枝の通過表示が復元されなくなります。
     */
    internal fun loopNodeIds(endNodeId: UUID): Set<UUID> {
        val end = state.snapshot.graph.nodes[endNodeId] ?: return setOf(endNodeId)
        val startId = end.pairedNodeId ?: return setOf(endNodeId)
        val graph = state.snapshot.graph
        val visited = linkedSetOf<UUID>()
        val queue = ArrayDeque<UUID>()
        queue.addLast(startId)
        while (queue.isNotEmpty()) {
            val nodeId = queue.removeFirst()
            if (!visited.add(nodeId) || nodeId == endNodeId) continue
            val node = graph.nodes[nodeId] ?: continue
            when (node.type) {
                CommandType.CONDITION -> {
                    node.trueNext?.let(queue::addLast)
                    node.falseNext?.let(queue::addLast)
                }
                CommandType.FOR_START -> {
                    // 入れ子ループは本体側へ入り、対応FOR_END後の外側経路へ
                    // 直接抜けないようにします。空ループだけは対応終端を含めます。
                    if (node.trueNext == node.pairedNodeId) {
                        node.pairedNodeId?.let(queue::addLast)
                    } else {
                        node.trueNext?.let(queue::addLast)
                    }
                }
                else -> node.next?.let(queue::addLast)
            }
        }
        return visited - startId
    }

    private fun edgeFor(script: DiskScript, nodeId: UUID, nextNodeId: UUID?): TestExecutionEdge? {
        val next = nextNodeId ?: return null
        val node = script.graph.nodes[nodeId] ?: return null
        val edge = when {
            node.type == CommandType.CONDITION && node.trueNext == next -> GraphEditor.Edge.TRUE
            node.type == CommandType.CONDITION && node.falseNext == next -> GraphEditor.Edge.FALSE
            node.type == CommandType.FOR_START && node.trueNext == next -> GraphEditor.Edge.FOR_BODY
            node.next == next -> GraphEditor.Edge.NEXT
            else -> null
        } ?: return null
        return TestExecutionEdge(nodeId, edge)
    }
}
