package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

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
                    "params": {"ticks": "40"}
                  }
                }
              }
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val migrated = requireNotNull(store.load(scriptId))

        assertEquals(7, migrated.formatVersion)
        assertEquals(3, migrated.timer.intervalSeconds)
        assertEquals("2", migrated.graph.nodes[nodeId]?.params?.get("seconds"))
        val rewritten = dir.resolve("$scriptId.json").readText(Charsets.UTF_8)
        assertTrue(rewritten.contains("\"formatVersion\": 7"))
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
}
