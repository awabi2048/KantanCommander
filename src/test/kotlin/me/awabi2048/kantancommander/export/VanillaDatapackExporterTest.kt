package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

class VanillaDatapackExporterTest {
    @TempDir
    lateinit var temp: File

    @Test
    fun `context is emitted as execute and not as comment`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "export")
        val context = GraphEditor.append(script.graph, CommandType.CONTEXT)
        context.contextOverride = ExecutionContextSpec(
            target = TargetSpec(TargetKind.NEAREST_PLAYER),
            position = PositionSpec(PositionKind.COORDINATES, 0.0, 1.0, 0.0),
        )
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).params["text"] = "hello"
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).export(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val files = success.directory.walkTopDown().filter(File::isFile).toList()
        val text = files.joinToString("\n") { it.readText() }
        assertTrue(text.contains("execute as @p positioned 0.0 1.0 0.0 run function"))
        assertFalse(text.contains("# context"))
    }

    @Test
    fun `plugin item fails preflight`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "invalid")
        GraphEditor.append(script.graph, CommandType.GIVE_ITEM).params["item"] = "custom:plugin_item"
        store.save(script)
        assertInstanceOf(
            ExportResult.Failure::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).export(script),
        )
    }
}
