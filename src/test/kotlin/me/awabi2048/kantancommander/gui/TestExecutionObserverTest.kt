package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.execution.ExecutionNodeFinished
import me.awabi2048.kantancommander.execution.ExecutionNodeStarted
import me.awabi2048.kantancommander.execution.NodeExecutionOutcome
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TestExecutionObserverTest {
    private val ownerId = UUID.randomUUID()

    private fun loopScript(): Triple<DiskScript, UUID, UUID> {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val body = GraphEditor.appendToForBody(graph, start.id, CommandType.WAIT)
        val script = DiskScript(name = "loop", owner = ownerId, graph = graph)
        return Triple(script, start.id, body.id)
    }

    private fun testState(script: DiskScript): GestureTestState =
        GestureTestState(
            scopeKey = "test",
            snapshot = script,
            ownerId = ownerId,
            originalOrigin = MapPoint(0, 0),
            originalZoomLevel = 0,
            originalSelectedNodeId = null,
            originalLowerMode = GestureLowerMode.SETTINGS,
        ).apply { phase = GestureTestPhase.RUNNING }

    private fun finished(
        script: DiskScript,
        nodeId: UUID,
        type: CommandType,
        nextId: UUID?,
        count: Int,
        tick: Long,
        loopReturn: Boolean = false,
        nextWaitTicks: Long = 0L,
        outcome: NodeExecutionOutcome = NodeExecutionOutcome.SUCCESS,
    ) = ExecutionNodeFinished(
        scriptId = script.id,
        nodeId = nodeId,
        nodeType = type,
        depth = 0,
        attemptNumber = 0,
        outcome = outcome,
        successfulNodeCount = count,
        nextNodeId = nextId,
        tick = tick,
        loopReturn = loopReturn,
        nextWaitTicks = nextWaitTicks,
    )

    private fun started(
        script: DiskScript,
        nodeId: UUID,
        type: CommandType,
        attempt: Int,
        tick: Long,
    ) = ExecutionNodeStarted(script.id, nodeId, type, 0, attempt, tick)

    @Test
    fun `tracks active loop from start to normal exit`() {
        // START完了で実行中扱いになり、終了ノードの通常完了で閉じます。
        val (script, startId, bodyId) = loopScript()
        val endId = requireNotNull(script.graph.nodes[startId]?.pairedNodeId)
        val state = testState(script)
        val observer = GestureTestExecutionObserver(state, {}, {}, {})
        assertTrue(state.activeLoopStarts.isEmpty())
        observer.onNodeFinished(finished(script, startId, CommandType.FOR_START, bodyId, 1, 1L))
        assertEquals(listOf(startId), state.activeLoopStarts.toList())
        // 復帰では閉じません。
        observer.onNodeFinished(finished(script, bodyId, CommandType.WAIT, endId, 2, 2L))
        observer.onNodeFinished(finished(script, endId, CommandType.FOR_END, bodyId, 3, 3L, loopReturn = true))
        assertEquals(listOf(startId), state.activeLoopStarts.toList())
        // 回数完了の退出で閉じます。次はループ外（null終端）へ進みます。
        observer.onNodeFinished(finished(script, endId, CommandType.FOR_END, null, 4, 4L))
        assertTrue(state.activeLoopStarts.isEmpty())
    }

    @Test
    fun `pops innermost loop on successful break`() {
        val (script, startId, bodyId) = loopScript()
        val state = testState(script)
        val observer = GestureTestExecutionObserver(state, {}, {}, {})
        observer.onNodeFinished(finished(script, startId, CommandType.FOR_START, bodyId, 1, 1L))
        val breakId = UUID.randomUUID()
        observer.onNodeFinished(finished(script, breakId, CommandType.BREAK, null, 2, 2L))
        assertTrue(state.activeLoopStarts.isEmpty())
    }

    @Test
    fun `keeps active loop on failed break`() {
        // 枠がないBREAKの失敗では、無関係な実行中ループを閉じません。
        val (script, startId, bodyId) = loopScript()
        val state = testState(script)
        val observer = GestureTestExecutionObserver(state, {}, {}, {})
        observer.onNodeFinished(finished(script, startId, CommandType.FOR_START, bodyId, 1, 1L))
        val breakId = UUID.randomUUID()
        observer.onNodeFinished(
            finished(script, breakId, CommandType.BREAK, null, 1, 2L, outcome = NodeExecutionOutcome.FAILED),
        )
        assertEquals(listOf(startId), state.activeLoopStarts.toList())
    }

    @Test
    fun `excludes loop end from whitening set`() {
        // 終了ノードの緑表示を白化から守るため、集合に含めません。
        val (script, startId, _) = loopScript()
        val endId = requireNotNull(script.graph.nodes[startId]?.pairedNodeId)
        val state = testState(script)
        val observer = GestureTestExecutionObserver(state, {}, {}, {})
        val ids = observer.loopNodeIds(endId)
        assertFalse(ids.contains(endId))
        assertFalse(ids.contains(startId))
    }

    @Test
    fun `commits passed edges on next start for white blue green order`() {
        // 完了直後は白のままにし、次の開始で通過確定（黄緑）します。
        val (script, startId, bodyId) = loopScript()
        val state = testState(script)
        val observer = GestureTestExecutionObserver(state, {}, {}, {})
        observer.onNodeFinished(finished(script, startId, CommandType.FOR_START, bodyId, 1, 1L))
        assertFalse(state.passedEdges.contains(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY)))
        observer.onNodeStarted(started(script, bodyId, CommandType.WAIT, 1, 2L))
        assertTrue(state.passedEdges.contains(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY)))
    }

    @Test
    fun `defers loop reset until next start after looping back`() {
        // 復帰完了直後はまだ消えず、再開時に戻り経路とループ内が白に戻ります。
        // FOR復帰時に開始→本体の枝が消えると、二度と復元されません。
        val (script, startId, bodyId) = loopScript()
        val endId = requireNotNull(script.graph.nodes[startId]?.pairedNodeId)
        val state = testState(script)
        val observer = GestureTestExecutionObserver(state, {}, {}, {})
        observer.onNodeFinished(finished(script, startId, CommandType.FOR_START, bodyId, 1, 1L))
        observer.onNodeStarted(started(script, bodyId, CommandType.WAIT, 1, 2L))
        observer.onNodeFinished(finished(script, bodyId, CommandType.WAIT, endId, 2, 3L))
        observer.onNodeStarted(started(script, endId, CommandType.FOR_END, 2, 4L))
        assertTrue(state.passedEdges.contains(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY)))
        // 本体から開始位置へ戻ります。終了ノードの次は本体先頭です。
        observer.onNodeFinished(finished(script, endId, CommandType.FOR_END, bodyId, 3, 5L, loopReturn = true))
        // 完了直後はまだ残っています。
        assertTrue(state.passedEdges.contains(TestExecutionEdge(bodyId, GraphEditor.Edge.NEXT)))
        assertTrue(state.successfulNodeIds.contains(bodyId))
        // 再開でループ内だけが白に戻り、開始→本体の枝は残ります。
        observer.onNodeStarted(started(script, bodyId, CommandType.WAIT, 3, 6L))
        assertTrue(state.passedEdges.contains(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY)))
        assertFalse(state.passedEdges.contains(TestExecutionEdge(bodyId, GraphEditor.Edge.NEXT)))
        assertFalse(state.successfulNodeIds.contains(bodyId))
        assertTrue(state.successfulNodeIds.contains(startId))
    }

    @Test
    fun `shows loop return in light blue during long waits`() {
        // 復帰完了直後は無変化、+20で戻り青＋FOR内白化、+40で戻り白＋S相当表示、再開で復帰します。
        val (script, startId, bodyId) = loopScript()
        val endId = requireNotNull(script.graph.nodes[startId]?.pairedNodeId)
        val state = testState(script)
        val fake = FakeScheduler()
        val observer = GestureTestExecutionObserver(state, {}, {}, {}, dummyPlugin(), fake.scheduler)
        observer.onNodeFinished(finished(script, startId, CommandType.FOR_START, bodyId, 1, 1L))
        observer.onNodeStarted(started(script, bodyId, CommandType.WAIT, 1, 2L))
        observer.onNodeFinished(finished(script, bodyId, CommandType.WAIT, endId, 2, 3L))
        observer.onNodeStarted(started(script, endId, CommandType.FOR_END, 2, 4L))
        observer.onNodeFinished(
            finished(script, endId, CommandType.FOR_END, bodyId, 3, 5L, loopReturn = true, nextWaitTicks = 60L),
        )
        // 完了直後はEの黄緑化以外に変化がありません。
        assertNull(state.waitingLoopReturnId)
        assertTrue(state.passedEdges.contains(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY)))
        assertTrue(state.successfulNodeIds.contains(startId))
        assertTrue(state.passedEdges.contains(TestExecutionEdge(bodyId, GraphEditor.Edge.NEXT)))
        // +20で戻り青＋FOR内白化＋S白化が行われます。
        fake.runPending()
        assertEquals(endId, state.waitingLoopReturnId)
        assertFalse(state.passedEdges.contains(TestExecutionEdge(bodyId, GraphEditor.Edge.NEXT)))
        assertFalse(state.successfulNodeIds.contains(bodyId))
        assertFalse(state.passedEdges.contains(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY)))
        assertFalse(state.successfulNodeIds.contains(startId))
        // +40で戻り白＋S相当の表示へ移ります。
        fake.runPending()
        assertNull(state.waitingLoopReturnId)
        assertEquals(startId, state.currentNodeId)
        assertEquals(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY), state.waitingEdge)
        // 再開でS→body枝だけ黄緑に戻ります。
        observer.onNodeStarted(started(script, bodyId, CommandType.WAIT, 3, 65L))
        assertTrue(state.passedEdges.contains(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY)))
        assertEquals(bodyId, state.currentNodeId)
        assertFalse(state.successfulNodeIds.contains(startId))
    }

    /** runTaskLaterだけを記録する試験用スケジューラです。 */
    private class FakeScheduler {
        data class Scheduled(val task: Runnable, val delay: Long, var cancelled: Boolean = false)

        val scheduled = mutableListOf<Scheduled>()

        private fun dummyTask(entry: Scheduled): BukkitTask =
            Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(BukkitTask::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "cancel" -> {
                        entry.cancelled = true
                        null
                    }
                    "isCancelled" -> entry.cancelled
                    else -> defaultValue(method.returnType)
                }
            } as BukkitTask

        val scheduler: BukkitScheduler = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(BukkitScheduler::class.java),
        ) { _, method, args ->
            if (method.name == "runTaskLater" && args?.size == 3 && args[1] is Runnable) {
                val entry = Scheduled(args[1] as Runnable, args[2] as Long)
                scheduled += entry
                dummyTask(entry)
            } else {
                defaultValue(method.returnType)
            }
        } as BukkitScheduler

        fun runPending() {
            val targets = scheduled.filterNot { it.cancelled }
            scheduled.clear()
            targets.forEach { it.task.run() }
        }

        companion object {
            fun defaultValue(type: Class<*>): Any? = when (type) {
                Void.TYPE -> null
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                List::class.java -> emptyList<Any>()
                String::class.java -> ""
                else -> null
            }
        }
    }

    private fun dummyPlugin(): Plugin = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(Plugin::class.java),
    ) { _, method, _ -> FakeScheduler.defaultValue(method.returnType) } as Plugin

    @Test
    fun `shows waiting branch after twenty ticks of waiting`() {
        val graph = CommandGraph.empty()
        val wait = GraphEditor.append(graph, CommandType.WAIT)
        val next = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val script = DiskScript(name = "wait", owner = ownerId, graph = graph)
        val state = testState(script)
        val fake = FakeScheduler()
        val observer = GestureTestExecutionObserver(state, {}, {}, {}, dummyPlugin(), fake.scheduler)
        // WAIT開始直後はまだ待機表示になりません。
        observer.onNodeStarted(
            ExecutionNodeStarted(script.id, wait.id, CommandType.WAIT, 0, 0, 1L, waitTicks = 100L),
        )
        assertNull(state.waitingNodeId)
        assertEquals(1, fake.scheduled.size)
        assertEquals(TEST_WAIT_HIGHLIGHT_DELAY_TICKS, fake.scheduled.single().delay)
        // 20tick後に待機中のノードと次の枝が示されます。
        fake.runPending()
        assertEquals(wait.id, state.waitingNodeId)
        assertEquals(TestExecutionEdge(wait.id, GraphEditor.Edge.NEXT), state.waitingEdge)
        // 完了で待機表示は消えます。
        observer.onNodeFinished(finished(script, wait.id, CommandType.WAIT, next.id, 1, 101L))
        assertNull(state.waitingNodeId)
        assertNull(state.waitingEdge)
    }

    @Test
    fun `shows waiting branch during debug delay after finish`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val second = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val script = DiskScript(name = "debug", owner = ownerId, graph = graph)
        val state = testState(script)
        val fake = FakeScheduler()
        val observer = GestureTestExecutionObserver(state, {}, {}, {}, dummyPlugin(), fake.scheduler)
        observer.onNodeFinished(
            finished(script, first.id, CommandType.DISPLAY_TEXT, second.id, 1, 1L, nextWaitTicks = 40L),
        )
        fake.runPending()
        assertEquals(TestExecutionEdge(first.id, GraphEditor.Edge.NEXT), state.waitingEdge)
        // 次の開始で待機表示は消えます。
        observer.onNodeStarted(ExecutionNodeStarted(script.id, second.id, CommandType.DISPLAY_TEXT, 0, 1, 21L))
        assertNull(state.waitingEdge)
    }

    @Test
    fun `skips waiting display for short waits`() {
        val graph = CommandGraph.empty()
        val wait = GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val script = DiskScript(name = "short", owner = ownerId, graph = graph)
        val state = testState(script)
        val fake = FakeScheduler()
        val observer = GestureTestExecutionObserver(state, {}, {}, {}, dummyPlugin(), fake.scheduler)
        observer.onNodeStarted(
            ExecutionNodeStarted(script.id, wait.id, CommandType.WAIT, 0, 0, 1L, waitTicks = 20L),
        )
        assertTrue(fake.scheduled.isEmpty())
        assertNull(state.waitingNodeId)
    }
}
