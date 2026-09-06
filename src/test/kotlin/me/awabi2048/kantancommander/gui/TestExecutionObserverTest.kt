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
    ) = ExecutionNodeFinished(
        scriptId = script.id,
        nodeId = nodeId,
        nodeType = type,
        depth = 0,
        attemptNumber = 0,
        outcome = NodeExecutionOutcome.SUCCESS,
        successfulNodeCount = count,
        nextNodeId = nextId,
        tick = tick,
        loopReturn = loopReturn,
        nextWaitTicks = nextWaitTicks,
    )

    @Test
    fun `keeps loop start edge after looping back`() {
        // FOR復帰時に開始→本体の枝が消えると、二度と復元されません。
        val (script, startId, bodyId) = loopScript()
        val endId = requireNotNull(script.graph.nodes[startId]?.pairedNodeId)
        val state = testState(script)
        val observer = GestureTestExecutionObserver(state, {}, {}, {})
        observer.onNodeFinished(finished(script, startId, CommandType.FOR_START, bodyId, 1, 1L))
        observer.onNodeFinished(finished(script, bodyId, CommandType.WAIT, endId, 2, 2L))
        assertTrue(state.passedEdges.contains(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY)))
        // 本体から開始位置へ戻ります。終了ノードの次は本体先頭です。
        observer.onNodeFinished(finished(script, endId, CommandType.FOR_END, bodyId, 3, 3L, loopReturn = true))
        // 開始→本体の枝は残り、本体内の枝は今回の反復表示から外れます。
        assertTrue(state.passedEdges.contains(TestExecutionEdge(startId, GraphEditor.Edge.FOR_BODY)))
        assertFalse(state.passedEdges.contains(TestExecutionEdge(bodyId, GraphEditor.Edge.NEXT)))
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
    fun `shows waiting branch after five ticks of waiting`() {
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
        // 5tick後に待機中のノードと次の枝が示されます。
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
            finished(script, first.id, CommandType.DISPLAY_TEXT, second.id, 1, 1L, nextWaitTicks = 20L),
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
            ExecutionNodeStarted(script.id, wait.id, CommandType.WAIT, 0, 0, 1L, waitTicks = 5L),
        )
        assertTrue(fake.scheduled.isEmpty())
        assertNull(state.waitingNodeId)
    }
}
