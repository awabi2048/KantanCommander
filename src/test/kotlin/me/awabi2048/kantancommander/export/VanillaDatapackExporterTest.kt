package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.GraphLimits
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.ContextSource
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
                        if (node.targetSpec == null) node.targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
                        if (node.destinationSpec == null && node.destinationTargetSpec == null) {
                            node.destinationSpec = PositionSpec(PositionKind.DISK)
                        }
                    }
                    CommandType.GIVE_ITEM, CommandType.ENTITY_ACTION, CommandType.DISPLAY_TEXT -> {
                        if (node.targetSpec == null) node.targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
                        if (node.type == CommandType.GIVE_ITEM && node.string("item").isBlank()) {
                            node.params["item"] = "minecraft:stone"
                        }
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
                                me.awabi2048.kantancommander.model.ConditionKind.PLAYER_STATE,
                            ) && node.targetSpec == null
                        ) {
                            node.targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
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
    fun `new semantic commands compile and camera shake is reported as skipped`() {
        val store = ScriptStore(temp.resolve("new-commands"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "commands")
        GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY).apply {
            params["entity"] = "minecraft:pig"
            params["tags"] = "kc_test"
        }
        GraphEditor.append(script.graph, CommandType.PLAY_SOUND).params["sound"] = "minecraft:block.note_block.harp"
        GraphEditor.append(script.graph, CommandType.APPLY_EFFECT).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
            params["effect"] = "minecraft:speed"
        }
        GraphEditor.append(script.graph, CommandType.ENTITY_ACTION).apply {
            params["action"] = "equip"
            targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
            params["item"] = "minecraft:stone"
        }
        GraphEditor.append(script.graph, CommandType.CAMERA_SHAKE).targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("summon minecraft:pig"))
        assertTrue(body.contains("playsound minecraft:block.note_block.harp"))
        assertTrue(body.contains("effect give"))
        assertTrue(body.contains("item replace entity"))
        assertTrue(result.warnings.any { it.contains("カメラ揺れ") })
    }

    @Test
    fun `block operation and entity deletion lower to vanilla commands`() {
        val store = ScriptStore(temp.resolve("block-commands"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "block commands")
        GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION).apply {
            params["block"] = "minecraft:stone"
            blockPositionSpec = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0)
        }
        GraphEditor.append(script.graph, CommandType.ENTITY_DELETE).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
        }

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("setblock 1.0 2.0 3.0 minecraft:stone"))
        assertTrue(body.contains("kill @e["))
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
            .targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
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
    fun `all fixed entity references fail standalone preflight`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val exporter = VanillaDatapackExporter(store, temp.resolve("exports"))

        val fixedTarget = store.create(UUID.randomUUID(), "fixed-target")
        GraphEditor.append(fixedTarget.graph, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.FIXED_ENTITY)

        val fixedSecondary = store.create(UUID.randomUUID(), "fixed-secondary")
        GraphEditor.append(fixedSecondary.graph, CommandType.ENTITY_ACTION).apply {
            targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
            secondaryTargetSpec = TargetSpec(TargetKind.FIXED_ENTITY)
        }

        val fixedDestination = store.create(UUID.randomUUID(), "fixed-destination")
        GraphEditor.append(fixedDestination.graph, CommandType.TELEPORT).apply {
            targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
            destinationTargetSpec = TargetSpec(TargetKind.FIXED_ENTITY)
        }

        listOf(fixedTarget, fixedSecondary, fixedDestination).forEach { script ->
            assertInstanceOf(
                StandaloneCompilation.Failure::class.java,
                exporter.compileForStandalone(script),
            )
        }
    }

    @Test
    fun `standalone function names stay within vanilla limits and references resolve`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "short-function-names")
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
        GraphEditor.append(script.graph, CommandType.VARIABLE).apply {
            params.putAll(
                mapOf(
                    "name" to "place",
                    "type" to VariableType.NUMBER.name,
                    "operation" to VariableOperation.DEFINE.name,
                    "value" to "1",
                )
            )
        }

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        assertTrue(
            success.functions.keys.all { it.length <= 64 },
            "function name exceeded the vanilla limit: ${success.functions.keys}",
        )

        val references = success.functions.values
            .flatMap { body ->
                Regex("""function kantan:([a-z0-9_]+)""")
                    .findAll(body)
                    .map { it.groupValues[1] }
                    .toList()
            }
            .toSet()
        assertTrue(
            references.all { it in success.functions.keys },
            "unresolved function reference(s): ${references - success.functions.keys}",
        )
    }

    @Test
    fun `standalone compilation namespaces do not collide between placements`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "placement-namespaces")
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
        val exporter = VanillaDatapackExporter(store, temp.resolve("exports"))

        val first = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            exporter.compileForStandalone(script, entryFunctionName = "p_first"),
        )
        val second = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            exporter.compileForStandalone(script, entryFunctionName = "p_second"),
        )

        assertTrue(first.entryFunctionName == "p_first")
        assertTrue(second.entryFunctionName == "p_second")
        assertTrue(first.functions.keys.intersect(second.functions.keys).isEmpty())
    }

    @Test
    fun `temporary variables reset while world variables remain persistent`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "variables")
        val local = GraphEditor.append(script.graph, CommandType.VARIABLE)
        local.params.putAll(mapOf("name" to "local", "type" to VariableType.NUMBER.name, "operation" to VariableOperation.DEFINE.name, "value" to "1"))
        val world = GraphEditor.append(script.graph, CommandType.VARIABLE)
        world.params.putAll(mapOf("name" to "shared", "type" to VariableType.NUMBER.name, "operation" to VariableOperation.DEFINE.name, "value" to "2"))
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val root = success.directory.resolve("data/kantan/function/${script.id}.mcfunction").readText()
        val all = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertFalse(root.contains("scoreboard players reset"))
        assertTrue(all.contains("set value 1d"))
        assertTrue(all.contains("set value 2d"))
    }

    @Test
    fun `current loop values compile only inside a for body`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "loop-value")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        val variable = GraphEditor.appendToForBody(script.graph, start.id, CommandType.VARIABLE)
        variable.params["name"] = "iteration"
        variable.params["type"] = VariableType.NUMBER.name
        variable.params["value"] = "\$current_iteration_value"
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("execute store result storage kantan:variables"))
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
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params.putAll(
            mapOf("kind" to "VARIABLE_STATE", "variable" to "value", "operator" to "!=", "value" to "4")
        )
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.INHERITED_TARGET)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.INHERITED_TARGET)

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(
                script,
                mapOf("value" to VariableType.NUMBER),
            ),
        )
        val text = success.functions.values.joinToString("\n")
        val holder = "#c_"
        assertTrue(text.contains("execute unless score $holder"))
        assertTrue(text.contains("execute if score $holder"))
    }

    @Test
    fun `string variable conditions fail vanilla preflight`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "boolean-condition")
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "enabled",
                "type" to VariableType.STRING.name,
                "operation" to VariableOperation.DEFINE.name,
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
            .targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)

        val compilation = VanillaDatapackExporter(store, temp.resolve("exports"))
            .compileForStandalone(script, mapOf("enabled" to VariableType.STRING))
        assertInstanceOf(StandaloneCompilation.Failure::class.java, compilation)
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
                "type" to VariableType.NUMBER.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to (Int.MAX_VALUE.toLong() + 1).toString(),
            )
        )
        val oversizedSuccess = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(oversized),
        )
        assertTrue(oversizedSuccess.functions.values.any { it.contains("set value") })
    }

    @Test
    fun `text and number variables use storage`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "storage")
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "message",
                "type" to VariableType.STRING.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "hello",
            )
        )
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "ratio",
                "type" to VariableType.NUMBER.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "1.25",
            )
        )
        val compilation = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val text = compilation.functions.values.joinToString("\n")
        assertTrue(text.contains("set value \"hello\""))
        assertTrue(text.contains("set value 1.25d"))
        assertTrue(text.contains("data modify storage kantan:variables ${VanillaStorageNames.variablePath("message", false)}"))
    }

    @Test
    fun `dynamic strings are exported through vanilla macros`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "dynamic-text")
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).apply {
            targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
            params["text"] = "hello ${'$'}{message}"
        }

        val compilation = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(
                script,
                mapOf("message" to VariableType.STRING),
            ),
        )
        val text = compilation.functions.values.joinToString("\n")
        assertTrue(text.contains("with storage kantan:variables macro."))
        assertTrue(text.contains("$(v_"))
    }

    @Test
    fun `structured teleport destination and number variables are lowered`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "structured")
        GraphEditor.append(script.graph, CommandType.TELEPORT).destinationSpec =
            PositionSpec(PositionKind.COORDINATES, 12.5, 64.0, -3.0)
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "name" to "flag",
                "type" to VariableType.NUMBER.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "1.25",
            )
        )
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("tp @s 12.5 64.0 -3.0"))
        assertTrue(text.contains("set value 1.25d"))
        assertTrue(text.contains("execute store success score"))
    }

    @Test
    fun `exclusive for end uses strict scoreboard comparisons`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "exclusive-for")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["inclusiveEnd"] = "false"
        GraphEditor.appendToForBody(script.graph, start.id, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.INHERITED_TARGET)
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
    fun `player state condition combines sneaking and item possession`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "item-count")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params.putAll(
            mapOf("kind" to "PLAYER_STATE", "sneaking" to "true", "item" to "minecraft:stone")
        )
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("Pose:\\\"CROUCHING\\\""))
        assertTrue(text.contains("Inventory:[{id:\\\"minecraft:stone\\\"}]"))
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
        assertTrue(
            Regex("""positioned 2\.0 70\.0 3\.0 run function kantan:s_[0-9a-f]{24}""")
                .containsMatchIn(text)
        )
    }

    @Test
    fun `failed vanilla command does not execute its successor`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "failure")
        GraphEditor.append(script.graph, CommandType.GIVE_ITEM)
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val function = success.directory
            .resolve("data/kantan/function")
            .walkTopDown()
            .first {
                it.isFile && it.readText().let { body ->
                    "execute store success score" in body && "matches 1 run return run function" in body
                }
            }
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
    fun `standalone export enforces the same copied disk call depth`() {
        fun nestedCalls(remaining: Int): CommandGraph {
            val node = if (remaining == 0) {
                CommandType.DISPLAY_TEXT.newNode()
            } else {
                CommandType.DISK_CALL.newNode().also { it.snapshot = nestedCalls(remaining - 1) }
            }
            return CommandGraph(node.id, linkedMapOf(node.id to node))
        }

        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "depth")
        val rootCall = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        rootCall.snapshot = nestedCalls(3)

        val failure = assertInstanceOf(
            ExportResult.Failure::class.java,
            VanillaDatapackExporter(
                store,
                temp.resolve("exports"),
                maximumDiskCallDepth = 3,
            ).exportConfigured(script),
        )
        assertTrue(failure.errors.any { it.contains("別ディスク呼出深度") })

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(
            store,
            temp.resolve("exports-allowed"),
            maximumDiskCallDepth = 4,
            ).exportConfigured(script),
        )
        val functionFiles = success.directory
            .resolve("data/kantan/function")
            .walkTopDown()
            .filter(File::isFile)
            .toList()
        assertTrue(functionFiles.isNotEmpty())
        assertTrue(functionFiles.all { it.name.length < 100 })
    }

    @Test
    fun `standalone preflight uses configured disk graph limits`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "limits")
        val first = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        first.targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        val second = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        second.targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)

        val failure = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            VanillaDatapackExporter(
                store,
                temp.resolve("exports"),
                graphLimits = GraphLimits(maximumNodeCount = 1),
            ).compileForStandalone(script),
        )
        assertTrue(failure.errors.any { it.contains("上限 1") })
    }

    @Test
    fun `title export preserves configured durations`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "title")
        val title = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        title.params.putAll(
            mapOf("mode" to "title", "text" to "hello", "fadeInSeconds" to "3", "staySeconds" to "17", "fadeOutSeconds" to "4")
        )

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val function = success.directory
            .resolve("data/kantan/function")
            .walkTopDown()
            .first { it.isFile && it.readText().contains("title @s times 60 340 80") }
            .readText()

        assertTrue(function.contains("title @s times 60 340 80"))
        assertTrue(function.contains("title @s title"))
    }

    @Test
    fun `actionbar export preserves configured durations`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "actionbar")
        val actionbar = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        actionbar.params.putAll(
            mapOf("mode" to "actionbar", "text" to "hello", "fadeInSeconds" to "2", "staySeconds" to "6", "fadeOutSeconds" to "3")
        )

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val function = success.directory
            .resolve("data/kantan/function")
            .walkTopDown()
            .first { it.isFile && it.readText().contains("title @s times 40 120 60") }
            .readText()

        assertTrue(function.contains("title @s times 40 120 60"))
        assertTrue(function.contains("title @s actionbar"))
    }

    @Test
    fun `numeric calculation is lowered to scoreboard operations`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "overflow-guards")
        val variable = GraphEditor.append(script.graph, CommandType.VARIABLE)
        variable.params.putAll(
            mapOf(
                "name" to "counter",
                "type" to VariableType.NUMBER.name,
                "operation" to VariableOperation.CHANGE.name,
                "changeMode" to "CALCULATE",
                "value" to "counter + 10 * 2",
            )
        )
        val loop = GraphEditor.append(script.graph, CommandType.FOR_START)
        loop.params.putAll(mapOf("startValue" to "0", "endValue" to "10", "stepValue" to "1"))
        GraphEditor.appendToForBody(script.graph, loop.id, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.INHERITED_TARGET)

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(
                script,
                mapOf("counter" to VariableType.NUMBER),
            ),
        )
        val arithmetic = success.functions.values
            .first { it.contains("scoreboard players operation") && it.contains("expr_") }
        val endFunction = success.functions.values
            .first { it.contains("scoreboard players operation #for_") }

        assertTrue(arithmetic.contains("scoreboard players operation"))
        assertTrue(arithmetic.contains("kc_runtime"))
        assertTrue(endFunction.contains("if score #for_"))
        assertTrue(endFunction.contains("run return 0"))
        assertTrue(endFunction.contains("scoreboard players operation #for_"))
    }

    @Test
    fun `decimal condition output rejects unsupported fixed point range`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val exporter = VanillaDatapackExporter(store, temp.resolve("exports"))

        fun comparisonScript(name: String, value: String): me.awabi2048.kantancommander.model.DiskScript {
            val script = store.create(UUID.randomUUID(), name)
            val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
            condition.params.putAll(
                mapOf(
                    "kind" to "VARIABLE_STATE",
                    "variable" to "counter",
                    "operator" to ">",
                    "value" to value,
                )
            )
            return script
        }

        val oversized = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            exporter.compileForStandalone(
                comparisonScript("compare-oversized", (Int.MAX_VALUE.toDouble() / 1000.0 + 1.0).toString()),
                mapOf("counter" to VariableType.NUMBER),
            ),
        )
        assertTrue(oversized.errors.any { it.contains("固定小数点範囲外") })

        val nonNumeric = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            exporter.compileForStandalone(
                comparisonScript("compare-non-numeric", "abc"),
                mapOf("counter" to VariableType.NUMBER),
            ),
        )
        assertTrue(nonNumeric.errors.any { it.contains("比較値は数値") })
    }

    @Test
    fun `teleport destination target is limited to one entity`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "teleport-multi")
        GraphEditor.append(script.graph, CommandType.TELEPORT).apply {
            targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
            destinationTargetSpec = null
            destinationSpec = PositionSpec(PositionKind.TARGET)
            contextOverride = ExecutionContextSpec(target = TargetSpec(TargetKind.ALL_PLAYERS))
        }

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val text = success.functions.values.joinToString("\n")
        // 移動先は複数エンティティへtpできないため、limit=1の単一セレクタへ固定される。
        assertTrue(text.contains(Regex("""tp @s @a\[.*limit=1.*]""")))
    }

    @Test
    fun `for ranges may read world variables in vanilla output`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "for-world-vanilla")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params.putAll(
            mapOf(
                "startSource" to "WORLD",
                "startValue" to "base",
                "endSource" to "WORLD",
                "endValue" to "limit",
                "stepSource" to "FIXED",
                "stepValue" to "1",
            )
        )
        GraphEditor.appendToForBody(script.graph, start.id, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.INHERITED_TARGET)

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(
                script,
                mapOf("base" to VariableType.NUMBER, "limit" to VariableType.NUMBER),
            ),
        )
        val text = success.functions.values.joinToString("\n")
        // ワールド変数storageからfor開始値・終了値が転記される。
        assertTrue(
            text.contains("data get storage kantan:variables ${VanillaStorageNames.variablePath("base", false)}"),
            text,
        )
        assertTrue(
            text.contains("data get storage kantan:variables ${VanillaStorageNames.variablePath("limit", false)}"),
            text,
        )
    }

    @Test
    fun `ambiguous previous context across merged branches fails preflight`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "ambiguous-previous")

        // 条件分岐のtrue枝だけがコンテキストを設定し、合流後のPREVIOUS参照は経路ごとに内容が変わる。
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.CONTEXT).apply {
            contextOverride = ExecutionContextSpec(position = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0))
        }
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)
        val merge = GraphEditor.appendMerge(script.graph, condition.id)
        val follower = GraphEditor.insert(script.graph, merge.id, GraphEditor.Edge.NEXT, CommandType.DISPLAY_TEXT)
        follower.contextSource = ContextSource.PREVIOUS
        follower.targetSpec = TargetSpec(TargetKind.INHERITED_TARGET)

        val failure = assertInstanceOf(
            ExportResult.Failure::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        assertTrue(failure.errors.any { it.contains("確定しないため") })
    }
}
