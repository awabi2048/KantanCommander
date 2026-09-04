package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.hasDiskContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

@Suppress("DEPRECATION")
class ScriptStoreTest {
    @TempDir
    lateinit var temp: File

    @Test
    fun `structured script round trips and placement copy is independent`() {
        val store = ScriptStore(temp.resolve("structured"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "test")
        val node = CommandType.DISPLAY_TEXT.newNode()
        script.graph = CommandGraph(node.id, linkedMapOf(node.id to node))
        store.save(script)

        val loaded = requireNotNull(store.load(script.id))
        val copy = store.copyForPlacement(loaded)
        copy.graph.nodes[node.id]?.params?.set("text", "changed")
        assertEquals("", loaded.graph.nodes[node.id]?.string("text"))
        assertNotEquals(script.id, copy.id)
    }

    @Test
    fun `revision rejects stale saves and compare and set updates`() {
        val store = ScriptStore(temp.resolve("revisions"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "revision")
        val first = requireNotNull(store.load(script.id))
        val stale = requireNotNull(store.load(script.id))

        first.name = "first"
        store.save(first)
        assertEquals(first.revision, requireNotNull(store.load(script.id)).revision)

        stale.name = "stale"
        assertThrows(java.util.ConcurrentModificationException::class.java) {
            store.save(stale)
        }
        assertEquals("first", requireNotNull(store.load(script.id)).name)

        val expected = requireNotNull(store.load(script.id)).revision
        assertTrue(
            store.update(script.id, expectedRevision = expected) {
                it.name = "compare-and-set"
                true
            } == true,
        )
        assertNull(
            store.update(script.id, expectedRevision = expected) {
                it.name = "must-not-win"
                true
            },
        )
        assertEquals("compare-and-set", requireNotNull(store.load(script.id)).name)
    }

    @Test
    fun `concurrent updates serialize read modify write and preserve independent node edits`() {
        val store = ScriptStore(temp.resolve("concurrent"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "concurrent")
        val first = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        val second = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        store.save(script)

        val start = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(
                executor.submit<Boolean?> {
                    start.await()
                    store.update(script.id) {
                        it.graph.nodes[first.id]?.params?.set("text", "left")
                        true
                    }
                },
                executor.submit<Boolean?> {
                    start.await()
                    store.update(script.id) {
                        it.graph.nodes[second.id]?.params?.set("text", "right")
                        true
                    }
                },
            )
            futures.forEach { future ->
                assertTrue(future.get(10, TimeUnit.SECONDS) == true)
            }
        } finally {
            executor.shutdownNow()
        }

        val updated = requireNotNull(store.load(script.id))
        assertEquals("left", updated.graph.nodes[first.id]?.string("text"))
        assertEquals("right", updated.graph.nodes[second.id]?.string("text"))
    }

    @Test
    fun `stale node deletion cannot remove a node after another editor saves`() {
        val store = ScriptStore(temp.resolve("stale-delete"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "stale-delete")
        val node = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        store.save(script)

        val deleteView = requireNotNull(store.load(script.id))
        val otherView = requireNotNull(store.load(script.id)).apply {
            graph.nodes[node.id]?.params?.set("text", "keep")
        }
        store.save(otherView)

        assertNull(
            store.update(script.id, expectedRevision = deleteView.revision) {
                if (GraphEditor.delete(it.graph, node.id)) true else null
            },
        )
        val current = requireNotNull(store.load(script.id))
        assertTrue(node.id in current.graph.nodes)
        assertEquals("keep", current.graph.nodes[node.id]?.string("text"))
    }

    @Test
    fun `legacy tick format migrates timer and command durations to seconds`() {
        val dir = temp.resolve("legacy-ticks").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        dir.resolve("$scriptId.json").writeText(
            """
            {
              "formatVersion": 6,
              "id": "$scriptId",
              "name": "legacy",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 1,
              "listed": true,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": true, "intervalUnits": 6},
              "graph": {
                "entryNodeId": "$nodeId",
                "nodes": {
                  "$nodeId": {
                    "id": "$nodeId",
                    "type": "WAIT",
                    "params": {"ticks": "1"}
                  }
                }
              }
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val migrated = requireNotNull(store.load(scriptId))

        assertEquals(9, migrated.formatVersion)
        assertEquals(3, migrated.timer.intervalSeconds)
        assertEquals("0.05", migrated.graph.nodes[nodeId]?.params?.get("seconds"))
        val rewritten = dir.resolve("$scriptId.json").readText(Charsets.UTF_8)
        assertTrue(rewritten.contains("\"formatVersion\": 9"))
        assertTrue(rewritten.contains("\"intervalSeconds\": 3"))
        assertTrue(!rewritten.contains("intervalUnits"))
        assertTrue(!rewritten.contains("\"ticks\""))
    }

    @Test
    fun `legacy format is ignored`() {
        val dir = temp.resolve("structured").also(File::mkdirs)
        dir.resolve("${UUID.randomUUID()}.json").writeText("""{"formatVersion":1}""")
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertTrue(store.listAll().isEmpty())
    }

    @Test
    fun `unset placement and written output use independent unlisted scripts`() {
        val store = ScriptStore(temp.resolve("structured"), Logger.getAnonymousLogger())
        val placement = store.createPlacement(UUID.randomUUID(), "unset")
        assertFalse(placement.listed)

        val node = CommandType.DISPLAY_TEXT.newNode()
        node.params["text"] = "before"
        placement.graph = CommandGraph(node.id, linkedMapOf(node.id to node))
        store.save(placement)

        val output = store.copyForItem(placement)
        placement.graph.nodes[node.id]?.params?.set("text", "after")
        store.save(placement)

        assertFalse(output.listed)
        assertNotEquals(placement.id, output.id)
        assertEquals("before", requireNotNull(store.load(output.id)).graph.nodes[node.id]?.string("text"))
        assertEquals("after", requireNotNull(store.load(placement.id)).graph.nodes[node.id]?.string("text"))
    }

    @Test
    fun `invalid copied disk graph is rejected recursively`() {
        val store = ScriptStore(temp.resolve("structured"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "nested")
        val call = CommandType.DISK_CALL.newNode()
        val missing = UUID.randomUUID()
        call.snapshot = CommandGraph(missing, linkedMapOf())
        script.graph = CommandGraph(call.id, linkedMapOf(call.id to call))

        assertThrows(IllegalArgumentException::class.java) { store.save(script) }
    }

    @Test
    fun `configured map width rejects save without replacing stored script`() {
        val store = ScriptStore(
            temp.resolve("width"),
            Logger.getAnonymousLogger(),
            GraphLimits(maximumMapWidth = 3),
        )
        val script = store.create(UUID.randomUUID(), "width")
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)

        assertThrows(IllegalArgumentException::class.java) { store.save(script) }
        assertTrue(requireNotNull(store.load(script.id)).graph.nodes.isEmpty())
    }

    @Test
    fun `configured map height rejects branched save without replacing stored script`() {
        val store = ScriptStore(
            temp.resolve("height"),
            Logger.getAnonymousLogger(),
            GraphLimits(maximumMapHeight = 3),
        )
        val script = store.create(UUID.randomUUID(), "height")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)

        assertThrows(IllegalArgumentException::class.java) { store.save(script) }
        assertTrue(requireNotNull(store.load(script.id)).graph.nodes.isEmpty())
    }

    @Test
    fun `lowering config limits does not quarantine previously saved scripts`() {
        val dir = temp.resolve("limits-shrink")
        val wideStore = ScriptStore(dir, Logger.getAnonymousLogger())
        val script = wideStore.create(UUID.randomUUID(), "wide")
        repeat(3) { GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT) }
        wideStore.save(script)

        // リロード相当: 同じ保存先をより小さい上限で開き直しても、既存データは隔離されず読み込める。
        val shrunk = ScriptStore(dir, Logger.getAnonymousLogger(), GraphLimits(maximumNodeCount = 2))
        val loaded = requireNotNull(shrunk.load(script.id))
        assertEquals(3, loaded.graph.nodes.size)
        assertFalse(dir.resolve("corrupt").exists() && dir.resolve("corrupt").list().orEmpty().isNotEmpty())
    }

    @Test
    fun `load hands out independent copies so unsaved edits stay invisible`() {
        val store = ScriptStore(temp.resolve("copy"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "copy")
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        store.save(script)

        val first = requireNotNull(store.load(script.id))
        first.graph.nodes.values.forEach { it.params["text"] = "unsaved" }

        val second = requireNotNull(store.load(script.id))
        assertTrue(second.graph.nodes.values.all { it.string("text").isBlank() })
    }

    @Test
    fun `metadata-only edits are retained as disk output content`() {
        val store = ScriptStore(temp.resolve("metadata-edits"), Logger.getAnonymousLogger())

        val renamed = store.createPlacement(UUID.randomUUID(), "default")
        val renamedEdit = requireNotNull(store.load(renamed.id)).apply { name = "renamed" }
        store.save(renamedEdit)
        assertTrue(requireNotNull(store.load(renamed.id)).hasDiskContent())
        assertTrue(requireNotNull(store.load(renamed.id)).graph.nodes.isEmpty())

        val timed = store.createPlacement(UUID.randomUUID(), "default")
        val timedEdit = requireNotNull(store.load(timed.id)).apply {
            timer.enabled = true
            timer.intervalSeconds = 5
        }
        store.save(timedEdit)
        assertTrue(requireNotNull(store.load(timed.id)).hasDiskContent())
        assertTrue(requireNotNull(store.load(timed.id)).graph.nodes.isEmpty())

        val untouched = store.createPlacement(UUID.randomUUID(), "default")
        assertTrue(!requireNotNull(store.load(untouched.id)).hasDiskContent())
    }

    @Test
    fun `library and history are independent relations and history survives library removal`() {
        val dir = temp.resolve("relations")
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val owner = UUID.randomUUID()
        val editor = UUID.randomUUID()
        val otherEditor = UUID.randomUUID()
        val script = store.createPlacement(owner, "placement")

        // 編集者ごとに同じ正本UUIDを履歴へ登録できます。
        store.save(requireNotNull(store.load(script.id)).apply { name = "edited" }, editor)
        store.recordHistory(otherEditor, script.id)
        assertEquals(listOf(script.id), store.listHistory(editor).map { it.id })
        assertEquals(listOf(script.id), store.listHistory(otherEditor).map { it.id })
        assertTrue(store.listLibrary(owner).isEmpty())

        store.addToLibrary(owner, script.id)
        assertEquals(listOf(script.id), store.listLibrary(owner).map { it.id })
        store.removeFromLibrary(owner, script.id)
        assertTrue(store.listLibrary(owner).isEmpty())
        assertEquals(listOf(script.id), store.listHistory(editor).map { it.id })

        // 配置撤去のdeleteは履歴の正本を残します。
        store.delete(script.id)
        assertEquals(listOf(script.id), store.listHistory(editor).map { it.id })
        assertEquals("edited", requireNotNull(store.load(script.id)).name)

        // 再生成したStoreでも関係と正本を復元できます。
        val reopened = ScriptStore(dir, Logger.getAnonymousLogger())
        assertEquals(listOf(script.id), reopened.listHistory(otherEditor).map { it.id })
    }
}
