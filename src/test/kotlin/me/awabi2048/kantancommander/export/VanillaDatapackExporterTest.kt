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
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
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
        assertTrue(text.contains("execute as @a[distance=0..,limit=1,sort=nearest] positioned 0.0 1.0 0.0 run function"))
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
        val loop = "for_${start.id.toString().replace("-", "").take(12)}"
        assertTrue(Regex("scoreboard players set #${loop}_end kc_vars 3").findAll(text).count() == 1)
        assertTrue(Regex("scoreboard players set #${loop}_step kc_vars 1").findAll(text).count() == 1)
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
        assertTrue(root.contains("reset ${VanillaScoreNames.variableHolder("local", true)} kc_vars"))
        assertFalse(root.contains("reset ${VanillaScoreNames.variableHolder("shared", false)} kc_vars"))
        assertTrue(all.contains("${VanillaScoreNames.variableHolder("shared", false)} kc_vars"))
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
        assertTrue(text.contains("${VanillaScoreNames.variableHolder("iteration", true)} kc_vars = #for_"))
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
    fun `not equal condition reverses the equality predicate`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "not-equal")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params.putAll(
            mapOf("kind" to "VARIABLE_STATE", "variable" to "value", "operator" to "!=", "value" to "4")
        )
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).export(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        val holder = VanillaScoreNames.variableHolder("value", true)
        assertTrue(text.contains("execute unless score $holder kc_vars matches 4 run return run function"))
        assertTrue(text.contains("execute if score $holder kc_vars matches 4 run return run function"))
    }

    @Test
    fun `structured teleport destination and boolean variables are lowered`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "structured")
        GraphEditor.append(script.graph, CommandType.TELEPORT).destinationSpec =
            PositionSpec(PositionKind.COORDINATES, 12.5, 64.0, -3.0)
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "flag",
                "type" to VariableType.BOOLEAN.name,
                "operation" to VariableOperation.SET.name,
                "value" to "true",
            )
        )
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "flag",
                "type" to VariableType.BOOLEAN.name,
                "operation" to VariableOperation.TOGGLE.name,
            )
        )
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).export(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("tp @s 12.5 64.0 -3.0"))
        val holder = VanillaScoreNames.variableHolder("flag", true)
        assertTrue(text.contains("scoreboard players set $holder kc_vars 1"))
        assertTrue(text.contains("execute store success score $holder kc_vars"))
    }

    @Test
    fun `exclusive for end uses strict scoreboard comparisons`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "exclusive-for")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["inclusiveEnd"] = "false"
        GraphEditor.appendToForBody(script.graph, start.id, CommandType.DISPLAY_TEXT)
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).export(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("kc_vars < #for_"))
        assertTrue(text.contains("kc_vars > #for_"))
        assertFalse(text.contains("kc_vars <= #for_"))
    }

    @Test
    fun `item possession condition checks the configured count`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "item-count")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params.putAll(
            mapOf("kind" to "ITEM_POSSESSION", "item" to "minecraft:stone", "count" to "5")
        )
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).export(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("run clear @s minecraft:stone 0"))
        assertTrue(text.contains("kc_result matches 5.."))
    }

    @Test
    fun `disk call context wraps only the copied function call`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "call-context")
        val call = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        val nested = CommandType.DISPLAY_TEXT.newNode()
        call.snapshot = CommandGraph(nested.id, linkedMapOf(nested.id to nested))
        call.contextOverride = ExecutionContextSpec(
            position = PositionSpec(PositionKind.COORDINATES, 2.0, 70.0, 3.0)
        )
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).export(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("positioned 2.0 70.0 3.0 run function kantan:${script.id}_snapshot_${call.id}"))
    }

    @Test
    fun `failed vanilla command does not execute its successor`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "failure")
        val give = GraphEditor.append(script.graph, CommandType.GIVE_ITEM)
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).export(script),
        )
        val function = success.directory
            .resolve("data/kantan/function/${script.id}_${give.id}.mcfunction")
            .readText()
        assertTrue(function.contains("execute store success score"))
        assertTrue(function.contains("matches 1 run return run function"))
        assertTrue(function.endsWith("return 0\n"))
        assertTrue(success.directory.resolve("pack.mcmeta").readText().contains("\"pack_format\":101"))
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

    @Test
    fun `title export preserves configured durations`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "title")
        val title = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        title.params.putAll(
            mapOf("mode" to "title", "text" to "hello", "fadeIn" to "3", "stay" to "17", "fadeOut" to "4")
        )

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).export(script),
        )
        val function = success.directory
            .resolve("data/kantan/function/${script.id}_${title.id}.mcfunction")
            .readText()

        assertTrue(function.contains("title @s times 3 17 4"))
        assertTrue(function.contains("title @s title"))
    }
}
