package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    fun `legacy format is ignored`() {
        val dir = temp.resolve("structured").also(File::mkdirs)
        dir.resolve("${UUID.randomUUID()}.json").writeText("""{"formatVersion":1}""")
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertTrue(store.listAll().isEmpty())
    }
}
