package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandGraph
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
        assertTrue(text.contains("execute as @a[limit=1,sort=nearest] positioned 0.0 1.0 0.0 run function"))
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

    @Test
    fun `disk call exports only its stored snapshot`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "snapshot")
        val call = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        val nested = CommandType.DISPLAY_TEXT.newNode().also { it.params["text"] = "copied" }
        call.snapshot = CommandGraph(nested.id, linkedMapOf(nested.id to nested))
        call.params["diskId"] = UUID.randomUUID().toString()
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).export(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val text = success.directory.walkTopDown()
            .filter(File::isFile)
            .joinToString("\n") { it.readText() }
        assertTrue(text.contains("copied"))
        assertFalse(text.contains("function kantan:${call.params["diskId"]}"))
    }

    @Test
    fun `for loop and its control commands are compiled to scoreboard functions`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "for")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["startValue"] = "1"
        start.params["endValue"] = "3"
        start.params["stepValue"] = "1"
        GraphEditor.appendToForBody(script.graph, start.id, CommandType.CONTINUE)
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).export(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val text = success.directory.walkTopDown()
            .filter(File::isFile)
            .joinToString("\n") { it.readText() }
        assertTrue(text.contains("_check"))
        assertTrue(text.contains("scoreboard players operation"))
        assertTrue(text.contains("matches 1.."))
    }

    @Test
    fun `inverted condition swaps vanilla predicates`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "condition")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params["inverted"] = "true"
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).export(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val text = success.directory.walkTopDown()
            .filter(File::isFile)
            .joinToString("\n") { it.readText() }
        assertTrue(text.contains("execute unless entity"))
        assertTrue(text.contains("execute if entity"))
    }

    @Test
    fun `exported functions share one execution budget`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "budget")
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports"), maximumCommandCount = 7).export(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("scoreboard players set #executed kc_runtime 0"))
        assertTrue(text.contains("matches 7.. run return 0"))
        assertTrue(text.contains("scoreboard players add #executed kc_runtime 1"))
    }

    @Test
    fun `fixed secondary entity fails preflight`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "fixed-secondary")
        val node = GraphEditor.append(script.graph, CommandType.ENTITY_ACTION)
        node.secondaryTargetSpec = TargetSpec(TargetKind.FIXED_ENTITY)
        store.save(script)
        assertInstanceOf(
            ExportResult.Failure::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).export(script),
        )
    }

    @Test
    fun `temporary variables reset while world variables remain persistent`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "variables")
        val temporary = GraphEditor.append(script.graph, CommandType.VARIABLE)
        temporary.params["name"] = "local"
        val world = GraphEditor.append(script.graph, CommandType.VARIABLE)
        world.params["name"] = "shared"
        world.params["scope"] = "WORLD"
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).export(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val root = success.directory.resolve("data/kantan/function/${script.id}.mcfunction").readText()
        val all = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(root.contains("reset #t_local kc_vars"))
        assertFalse(root.contains("reset #w_shared kc_vars"))
        assertTrue(all.contains("#w_shared kc_vars"))
    }

    @Test
    fun `current loop values compile only inside a for body`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "loop-value")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        val variable = GraphEditor.appendToForBody(script.graph, start.id, CommandType.VARIABLE)
        variable.params["name"] = "iteration"
        variable.params["type"] = "INTEGER"
        variable.params["value"] = "\$current_iteration_value"
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).export(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("#t_iteration kc_vars = #for_"))
        assertTrue(text.contains("_value kc_vars"))
    }

    @Test
    fun `wait fails preflight instead of losing execution context`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "wait")
        GraphEditor.append(script.graph, CommandType.WAIT)
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).export(script)
        val failure = assertInstanceOf(ExportResult.Failure::class.java, result)
        assertTrue(failure.errors.any { it.contains("実行者と実行位置") })
        assertFalse(temp.resolve("exports/kantan-${script.id}").exists())
    }

    @Test
    fun `nested copied disk is included in preflight validation`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "nested")
        val call = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        val nested = CommandType.GIVE_ITEM.newNode()
        nested.params["item"] = "plugin:custom_item"
        call.snapshot = CommandGraph(nested.id, linkedMapOf(nested.id to nested))

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).export(script)
        val failure = assertInstanceOf(ExportResult.Failure::class.java, result)
        assertTrue(failure.errors.any { it.contains("バニラに存在しないアイテム") })
    }
}
