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

    private fun VanillaDatapackExporter.exportConfigured(script: me.awabi2048.kantancommander.model.DiskScript): ExportResult {
        fun configure(graph: CommandGraph) {
            graph.nodes.values.forEach { node ->
                when (node.type) {
                    CommandType.TELEPORT -> {
                        if (node.targetSpec == null) node.targetSpec = TargetSpec(TargetKind.EXECUTOR)
                        if (node.destinationSpec == null && node.destinationTargetSpec == null) {
                            node.destinationSpec = PositionSpec(PositionKind.DISK)
                        }
                    }
                    CommandType.GIVE_ITEM, CommandType.ENTITY_ACTION, CommandType.DISPLAY_TEXT -> {
                        if (node.targetSpec == null) node.targetSpec = TargetSpec(TargetKind.EXECUTOR)
                        if (node.type == CommandType.ENTITY_ACTION &&
                            node.string("action", "ride") == "ride" &&
                            node.secondaryTargetSpec == null
                        ) {
                            node.secondaryTargetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
                        }
                    }
                    CommandType.CONDITION -> {
                        val kind = runCatching {
                            me.awabi2048.kantancommander.model.ConditionKind.valueOf(node.string("kind"))
                        }.getOrNull()
                        if (kind in setOf(
                                me.awabi2048.kantancommander.model.ConditionKind.TARGET_EXISTS,
                                me.awabi2048.kantancommander.model.ConditionKind.ENTITY_STATE,
                                me.awabi2048.kantancommander.model.ConditionKind.ITEM_POSSESSION,
                            ) && node.targetSpec == null
                        ) {
                            node.targetSpec = TargetSpec(TargetKind.EXECUTOR)
                        }
                    }
                    else -> Unit
                }
                node.snapshot?.let(::configure)
            }
        }
        configure(script.graph)
        return export(script)
    }

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

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
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
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
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

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
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

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
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
            .targetSpec = TargetSpec(TargetKind.EXECUTOR)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
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

        val result = VanillaDatapackExporter(store, temp.resolve("exports"), maximumCommandCount = 7).exportConfigured(script)
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
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
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

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
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

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
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

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
        val failure = assertInstanceOf(ExportResult.Failure::class.java, result)
        assertTrue(failure.errors.any { it.contains("実行者と実行位置") })
        assertFalse(temp.resolve("exports/kantan-${script.id}").exists())
    }

    @Test
    fun `not equal condition reverses the equality predicate`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "not-equal")
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "value",
                "scope" to "TEMPORARY",
                "type" to "INTEGER",
                "operation" to "SET",
                "value" to "4",
            )
        )
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params.putAll(
            mapOf("kind" to "VARIABLE_STATE", "variable" to "value", "operator" to "!=", "value" to "4")
        )
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        val holder = VanillaScoreNames.variableHolder("value", true)
        assertTrue(text.contains("execute unless score $holder kc_vars matches 4 run return run function"))
        assertTrue(text.contains("execute if score $holder kc_vars matches 4 run return run function"))
    }

    @Test
    fun `boolean variable condition uses scoreboard boolean values`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "boolean-condition")
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "enabled",
                "scope" to "TEMPORARY",
                "type" to "BOOLEAN",
                "operation" to "SET",
                "value" to "true",
            )
        )
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params.putAll(
            mapOf(
                "kind" to "VARIABLE_STATE",
                "variable" to "enabled",
                "operator" to "==",
                "value" to "true",
            )
        )
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT)
            .targetSpec = TargetSpec(TargetKind.EXECUTOR)

        val compilation = VanillaDatapackExporter(store, temp.resolve("exports"))
            .compileForStandalone(script)
        val success = assertInstanceOf(StandaloneCompilation.Success::class.java, compilation)
        assertTrue(success.functions.values.any { it.contains("kc_vars matches 1") })
    }

    @Test
    fun `unresolved variable condition and out of range integers fail preflight`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val unresolved = store.create(UUID.randomUUID(), "unresolved")
        GraphEditor.append(unresolved.graph, CommandType.CONDITION).params.putAll(
            mapOf(
                "kind" to "VARIABLE_STATE",
                "variable" to "missing",
                "operator" to "==",
                "value" to "1",
            )
        )
        val unresolvedFailure = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(unresolved),
        )
        assertTrue(unresolvedFailure.errors.any { it.contains("型を一意に解決できません") })

        val oversized = store.create(UUID.randomUUID(), "oversized")
        GraphEditor.append(oversized.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "large",
                "scope" to "TEMPORARY",
                "type" to "INTEGER",
                "operation" to "SET",
                "value" to (Int.MAX_VALUE.toLong() + 1).toString(),
            )
        )
        val oversizedFailure = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(oversized),
        )
        assertTrue(oversizedFailure.errors.any { it.contains("scoreboardの範囲外") })
    }

    @Test
    fun `text and decimal variables use storage and support existence checks`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "storage")
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "message",
                "scope" to "TEMPORARY",
                "type" to "TEXT",
                "operation" to "SET",
                "value" to "hello",
            )
        )
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "ratio",
                "scope" to "TEMPORARY",
                "type" to "DECIMAL",
                "operation" to "SET",
                "value" to "1.25",
            )
        )
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params.putAll(
            mapOf(
                "kind" to "VARIABLE_STATE",
                "variable" to "message",
                "variableScope" to "TEMPORARY",
                "operator" to "set",
            )
        )

        val compilation = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val text = compilation.functions.values.joinToString("\n")
        assertTrue(text.contains("set value \"hello\""))
        assertTrue(text.contains("set value 1.25d"))
        assertTrue(text.contains("data storage kantan:variables ${VanillaStorageNames.variablePath("message", true)}"))
    }

    @Test
    fun `position and entity references are captured into storage`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "captured-storage")
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "place",
                "scope" to "TEMPORARY",
                "type" to "POSITION",
                "operation" to "STORE_POSITION",
            )
        )
        GraphEditor.append(script.graph, CommandType.VARIABLE).apply {
            params.putAll(
                mapOf(
                    "name" to "entity",
                    "scope" to "TEMPORARY",
                    "type" to "ENTITY",
                    "operation" to "STORE_TARGET",
                )
            )
            targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
        }

        val compilation = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val text = compilation.functions.values.joinToString("\n")
        val positionPath = VanillaStorageNames.variablePath("place", true)
        val entityPath = VanillaStorageNames.variablePath("entity", true)
        assertTrue(text.contains("$positionPath.position set from entity @s Pos"))
        assertTrue(text.contains("$positionPath.rotation set from entity @s Rotation"))
        assertTrue(text.contains("execute summon minecraft:marker run function kantan:"))
        assertTrue(text.contains("$entityPath set from entity @s UUID"))
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
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
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
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
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
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
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
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
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
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
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

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
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
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val function = success.directory
            .resolve("data/kantan/function/${script.id}_${title.id}.mcfunction")
            .readText()

        assertTrue(function.contains("title @s times 3 17 4"))
        assertTrue(function.contains("title @s title"))
    }

    @Test
    fun `integer arithmetic and for increments stop before scoreboard overflow`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "overflow-guards")
        val variable = GraphEditor.append(script.graph, CommandType.VARIABLE)
        variable.params.putAll(
            mapOf(
                "name" to "counter",
                "type" to VariableType.INTEGER.name,
                "operation" to VariableOperation.ADD.name,
                "value" to "10",
            )
        )
        val loop = GraphEditor.append(script.graph, CommandType.FOR_START)
        loop.params.putAll(mapOf("startValue" to "0", "endValue" to "10", "stepValue" to "1"))
        GraphEditor.appendToForBody(script.graph, loop.id, CommandType.DISPLAY_TEXT)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val arithmetic = success.directory
            .resolve("data/kantan/function/${script.id}_${variable.id}_arithmetic.mcfunction")
            .readText()
        val loopEnd = requireNotNull(script.graph.nodes[loop.pairedNodeId])
        val endFunction = success.directory
            .resolve("data/kantan/function/${script.id}_${loopEnd.id}.mcfunction")
            .readText()

        assertTrue(arithmetic.contains("matches 2147483638.. run return 0"))
        assertTrue(arithmetic.contains("scoreboard players add "))
        assertTrue(arithmetic.contains(" kc_vars 10"))
        assertTrue(endFunction.contains("if score #for_"))
        assertTrue(endFunction.contains("run return 0"))
        assertTrue(endFunction.contains("scoreboard players operation #for_"))
    }

    @Test
    fun `unrepresentable minimum integer subtraction fails preflight`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "minimum-subtraction")
        val variable = GraphEditor.append(script.graph, CommandType.VARIABLE)
        variable.params.putAll(
            mapOf(
                "name" to "counter",
                "type" to VariableType.INTEGER.name,
                "operation" to VariableOperation.SUBTRACT.name,
                "value" to Int.MIN_VALUE.toString(),
            )
        )

        val failure = assertInstanceOf(
            ExportResult.Failure::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        assertTrue(failure.errors.any { it.contains("安全に変換できません") })
    }
}
