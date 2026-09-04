package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.GraphLimits
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TemporaryVariableType
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
                        if (node.targetSpec == null) node.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
                        if (node.destinationSpec == null && node.destinationTargetSpec == null) {
                            node.destinationSpec = PositionSpec(PositionKind.DISK)
                        }
                    }
                    CommandType.GIVE_ITEM, CommandType.ENTITY_ACTION, CommandType.DISPLAY_TEXT -> {
                        if (node.targetSpec == null) node.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
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
                            node.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
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
    fun `condition position is emitted as execute positioned and not as a comment`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "export")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            conditionPositionSpec = PositionSpec(PositionKind.COORDINATES, 0.0, 1.0, 0.0)
        }
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).params["text"] = "hello"
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val files = success.directory.walkTopDown().filter(File::isFile).toList()
        val text = files.joinToString("\n") { it.readText() }
        assertTrue(text.contains("execute positioned 0.0 1.0 0.0"))
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
        assertTrue(body.contains("Tags:[\\\"kc_test\\\"]"))
        assertTrue(body.contains("playsound minecraft:block.note_block.harp"))
        assertTrue(body.contains("effect give"))
        assertTrue(body.contains("item replace entity"))
        assertTrue(result.warnings.any { it.contains("カメラシェイク") })
    }

    @Test
    fun `sound command-specific position remains an execute positioned wrapper`() {
        val store = ScriptStore(temp.resolve("sound-position"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "sound position")
        GraphEditor.append(script.graph, CommandType.PLAY_SOUND).apply {
            params["sound"] = "minecraft:block.note_block.harp"
            soundPositionSpec = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0)
        }

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("execute positioned 1.0 2.0 3.0 run playsound minecraft:block.note_block.harp"))
    }

    @Test
    fun `particle command uses current SNBT options and all-world viewers`() {
        val store = ScriptStore(temp.resolve("particle-position"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "particle position")
        GraphEditor.append(script.graph, CommandType.PARTICLE).apply {
            params.putAll(
                mapOf(
                    "particle" to "DUST",
                    "particleData" to "#ff0000 2",
                    "particleDeltaX" to "0.25",
                    "particleDeltaY" to "-0.5",
                    "particleDeltaZ" to "0",
                    "particleSpeed" to "0.5",
                    "particleCount" to "3",
                )
            )
            particlePositionSpec = PositionSpec(PositionKind.COORDINATES, 10.0, 64.0, -2.0)
        }

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(
            body.contains(
                "execute positioned 10.0 64.0 -2.0 run particle " +
                    "minecraft:dust{color:[1,0,0],scale:2} ~ ~ ~ 0.25 -0.5 0 0.5 3 force @a"
            ),
            body,
        )
    }

    @Test
    fun `summon command-specific position remains in vanilla output`() {
        val store = ScriptStore(temp.resolve("summon-position"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "summon position")
        GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY).apply {
            params["entity"] = "minecraft:pig"
            summonPositionSpec = PositionSpec(PositionKind.COORDINATES, 4.0, 5.0, 6.0)
        }

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("summon minecraft:pig 4.0 5.0 6.0"))
    }

    @Test
    fun `removed shared target position is rejected for sound and summon`() {
        val store = ScriptStore(temp.resolve("removed-target-position"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "removed target position")
        GraphEditor.append(script.graph, CommandType.PLAY_SOUND).apply {
            params["sound"] = "minecraft:block.note_block.harp"
            soundPositionSpec = PositionSpec(PositionKind.TARGET)
        }
        GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY).apply {
            params["entity"] = "minecraft:pig"
            summonPositionSpec = PositionSpec(PositionKind.TARGET)
        }

        val failure = assertInstanceOf(
            StandaloneCompilation.Failure::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        assertTrue(failure.errors.any { it.contains("効果音の対象位置指定") })
        assertTrue(failure.errors.any { it.contains("召喚の対象位置指定") })
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
    fun `repeat count and its control commands are compiled to scoreboard functions`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "for")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["count"] = "3"
        GraphEditor.appendToForBody(script.graph, start.id, CommandType.CONTINUE)
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val text = success.directory.walkTopDown()
            .filter(File::isFile)
            .joinToString("\n") { it.readText() }
        assertTrue(text.contains("_check"))
        assertTrue(text.contains("scoreboard players set #for_"))
        assertTrue(text.contains("_limit kc_vars 3"))
        assertTrue(text.contains("_count kc_vars 1"))
        assertTrue(text.contains("_count kc_vars <= #for_"))
    }

    @Test
    fun `inverted condition swaps vanilla predicates`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "condition")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params["inverted"] = "true"
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.TRUE, CommandType.DISPLAY_TEXT)
            .targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
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
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            secondaryTargetSpec = TargetSpec(TargetKind.FIXED_ENTITY)
        }

        val fixedDestination = store.create(UUID.randomUUID(), "fixed-destination")
        GraphEditor.append(fixedDestination.graph, CommandType.TELEPORT).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
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
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
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
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
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
    fun `temporary values and typed references are emitted to vanilla storage`() {
        val store = ScriptStore(temp.resolve("temporary-export"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "temporary-export")

        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "message",
                    "tempType" to TemporaryVariableType.STRING.name,
                    "value" to "hello",
                ),
            )
        }
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            params["text"] = "%{message}%"
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "point",
                    "tempType" to TemporaryVariableType.LOCATION.name,
                ),
            )
            temporaryLocationPositionSpec = PositionSpec(
                PositionKind.COORDINATES,
                x = 1.0,
                y = 2.0,
                z = 3.0,
            )
            temporaryLocationFacingSpec = FacingSpec(
                FacingKind.ROTATION,
                yaw = 0f,
                pitch = 0f,
            )
        }
        GraphEditor.append(script.graph, CommandType.TELEPORT).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            destinationSpec = PositionSpec(PositionKind.TEMPORARY, tempName = "point")
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "stack",
                    "tempType" to TemporaryVariableType.ITEM.name,
                    "item" to "minecraft:stone",
                ),
            )
        }
        GraphEditor.append(script.graph, CommandType.GIVE_ITEM).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            itemTempRef = "stack"
            params["item"] = ""
        }

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = success.functions.values.joinToString("\n")
        assertTrue(body.contains("data modify storage kantan:variables variables.temporary"))
        assertTrue(body.contains("$(m_"), "typed item/position references should use function macros")
        assertTrue(body.contains("set from storage kantan:variables variables.temporary"))
        assertFalse(body.contains("temporary teleport destination is unsupported"))
    }

    @Test
    fun `temporary scalar and compound values resolve temporary references in their definition`() {
        val store = ScriptStore(temp.resolve("temporary-definition-references"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "temporary-definition-references")

        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(mapOf("name" to "amount", "tempType" to TemporaryVariableType.NUMBER.name, "value" to "2.5"))
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(mapOf("name" to "copied", "tempType" to TemporaryVariableType.NUMBER.name, "value" to "%{amount}%"))
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(mapOf("name" to "label", "tempType" to TemporaryVariableType.STRING.name, "value" to "prefix-%{copied}%"))
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "sound",
                    "tempType" to TemporaryVariableType.SOUND.name,
                    "sound" to "minecraft:block.note_block.harp",
                    "volume" to "%{amount}%",
                    "pitch" to "1.0",
                ),
            )
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "effect",
                    "tempType" to TemporaryVariableType.EFFECT.name,
                    "effect" to "minecraft:speed",
                    "level" to "%{copied}%",
                    "seconds" to "30",
                ),
            )
        }

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = success.functions.values.joinToString("\n")

        assertTrue(body.contains("set from storage kantan:variables variables.temporary"))
        assertTrue(body.contains("function kantan:") && body.contains("temporary_macro"))
        assertFalse(body.contains("%{amount}%"), "temporary references must be lowered before output")
    }

    @Test
    fun `temporary LOCATION uses shared position and facing specs for dynamic sources`() {
        val store = ScriptStore(temp.resolve("temporary-location-dynamic"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "temporary-location-dynamic")

        fun location(name: String, x: Double, y: Double, z: Double) {
            GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
                params.putAll(mapOf("name" to name, "tempType" to TemporaryVariableType.LOCATION.name))
                temporaryLocationPositionSpec = PositionSpec(PositionKind.COORDINATES, x = x, y = y, z = z)
                temporaryLocationFacingSpec = FacingSpec(FacingKind.ROTATION, yaw = 0f, pitch = 0f)
            }
        }

        location("origin", 1.0, 2.0, 3.0)
        location("target", 5.0, 2.0, 3.0)
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "aimed"
            params["tempType"] = TemporaryVariableType.LOCATION.name
            temporaryLocationPositionSpec = PositionSpec(PositionKind.TEMPORARY, tempName = "origin")
            temporaryLocationFacingSpec = FacingSpec(FacingKind.TEMPORARY, tempName = "target")
        }

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = success.functions.values.joinToString("\n")

        assertTrue(body.contains("execute positioned ${'$'}(m_"), "dynamic LOCATION must use a macro function")
        assertTrue(body.contains("facing ${'$'}(m_"), "temporary facing must be resolved from LOCATION coordinates")
        assertFalse(body.contains("@{temp."), "internal marker syntax must not leak into the datapack")
    }

    @Test
    fun `temporary effect integers are emitted as command-compatible integer tags`() {
        val store = ScriptStore(temp.resolve("temporary-effect-export"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "temporary-effect-export")
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "effect",
                    "tempType" to TemporaryVariableType.EFFECT.name,
                    "effect" to "minecraft:speed",
                    "level" to "2.0",
                    "seconds" to "30.0",
                ),
            )
        }

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = success.functions.values.joinToString("\n")

        assertTrue(body.contains("level:2,seconds:30"))
        assertFalse(body.contains("level:2.0d,seconds:30.0d"))
    }

    @Test
    fun `temporary entity target is lowered through uuid component scores`() {
        val store = ScriptStore(temp.resolve("temporary-entity-export"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "temporary-entity-export")
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "entity",
                    "tempType" to TemporaryVariableType.ENTITY.name,
                    "entityId" to UUID.randomUUID().toString(),
                ),
            )
        }
        GraphEditor.append(script.graph, CommandType.ENTITY_DELETE).targetSpec =
            TargetSpec(TargetKind.TEMPORARY, tempName = "entity")

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = success.functions.values.joinToString("\n")
        assertTrue(body.contains("kc_tu0"))
        assertTrue(body.contains("data get entity @s UUID[0]"))
        assertTrue(body.contains("execute as @e if score @s kc_tu0 = #kc_temp_uuid0 kc_tu0"))
    }

    @Test
    fun `temporary entity with an invalid uuid remains a valid export and selects nothing`() {
        val store = ScriptStore(temp.resolve("temporary-invalid-entity-export"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "temporary-invalid-entity-export")
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "entity",
                    "tempType" to TemporaryVariableType.ENTITY.name,
                    "entityId" to "not-a-uuid",
                ),
            )
        }
        GraphEditor.append(script.graph, CommandType.ENTITY_DELETE).targetSpec =
            TargetSpec(TargetKind.TEMPORARY, tempName = "entity")

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = success.functions.values.joinToString("\n")
        assertTrue(body.contains("set value {}"), "invalid UUID must not produce invalid SNBT")
        assertTrue(body.contains("scoreboard players set #kc_temp_uuid0 kc_tu0 0"))
    }

    @Test
    fun `temporary entity is usable as a secondary target`() {
        val store = ScriptStore(temp.resolve("temporary-secondary-export"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "temporary-secondary-export")
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "vehicle",
                    "tempType" to TemporaryVariableType.ENTITY.name,
                    "entityId" to UUID.randomUUID().toString(),
                ),
            )
        }
        GraphEditor.append(script.graph, CommandType.ENTITY_ACTION).apply {
            params["action"] = "ride"
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
            secondaryTargetSpec = TargetSpec(TargetKind.TEMPORARY, tempName = "vehicle")
        }
        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = success.functions.values.joinToString("\n")
        assertTrue(body.contains("ride @a"))
    }

    @Test
    fun `current loop count compiles only inside a for body`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "loop-value")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        val variable = GraphEditor.appendToForBody(script.graph, start.id, CommandType.VARIABLE)
        variable.params["name"] = "iteration"
        variable.params["type"] = VariableType.NUMBER.name
        variable.params["value"] = "\${CURRENT_LOOP_COUNT}"
        store.save(script)

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script)
        val success = assertInstanceOf(ExportResult.Success::class.java, result)
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("execute store result storage kantan:variables"))
        assertTrue(text.contains("_count kc_vars"))
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
            TargetSpec(TargetKind.NEAREST_PLAYER)
        GraphEditor.insert(script.graph, condition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.NEAREST_PLAYER)

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
            .targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)

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
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
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
        assertTrue(text.contains("tp @a[distance=0..,limit=1,sort=nearest] 12.5 64.0 -3.0"))
        assertTrue(text.contains("set value 1.25d"))
        assertTrue(text.contains("execute store success score"))
    }

    @Test
    fun `repeat loop stops at the configured count without an extra increment`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "exclusive-for")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["count"] = "2"
        GraphEditor.appendToForBody(script.graph, start.id, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.NEAREST_PLAYER)
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("kc_vars <= #for_"))
        assertTrue(text.contains("kc_vars >= #for_"))
        assertTrue(text.contains("scoreboard players add #for_"))
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
    fun `disk call invokes only the copied function`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "call-context")
        val call = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        val nested = CommandType.DISPLAY_TEXT.newNode()
        call.snapshot = CommandGraph(nested.id, linkedMapOf(nested.id to nested))
        store.save(script)

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val text = success.directory.walkTopDown().filter(File::isFile).joinToString("\n") { it.readText() }
        assertTrue(text.contains("function kantan:s_"))
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
            .first { it.isFile && it.readText().contains("title @a[distance=0..,limit=1,sort=nearest] times 60 340 80") }
            .readText()

        assertTrue(function.contains("title @a[distance=0..,limit=1,sort=nearest] times 60 340 80"))
        assertTrue(function.contains("title @a[distance=0..,limit=1,sort=nearest] title"))
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
            .first { it.isFile && it.readText().contains("title @a[distance=0..,limit=1,sort=nearest] times 40 120 60") }
            .readText()

        assertTrue(function.contains("title @a[distance=0..,limit=1,sort=nearest] times 40 120 60"))
        assertTrue(function.contains("title @a[distance=0..,limit=1,sort=nearest] actionbar"))
    }

    @Test
    fun `title export preserves fractional tick durations`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "fractional-title")
        val title = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        title.params.putAll(
            mapOf(
                "mode" to "title",
                "text" to "hello",
                "fadeInSeconds" to "0.05",
                "staySeconds" to "0.15",
                "fadeOutSeconds" to "0.25",
            )
        )

        val success = assertInstanceOf(
            ExportResult.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).exportConfigured(script),
        )
        val function = success.directory
            .resolve("data/kantan/function")
            .walkTopDown()
            .first { it.isFile && it.readText().contains("title @a[distance=0..,limit=1,sort=nearest] times 1 3 5") }
            .readText()

        assertTrue(function.contains("title @a[distance=0..,limit=1,sort=nearest] times 1 3 5"))
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
                "value" to "\${counter} + 10 * 2",
            )
        )
        val loop = GraphEditor.append(script.graph, CommandType.FOR_START)
        loop.params["count"] = "10"
        GraphEditor.appendToForBody(script.graph, loop.id, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.NEAREST_PLAYER)

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
            .first { it.contains("scoreboard players add #for_") }

        assertTrue(arithmetic.contains("scoreboard players operation"))
        assertTrue(arithmetic.contains("kc_runtime"))
        assertTrue(endFunction.contains("if score #for_"))
        assertTrue(endFunction.contains("run return 0"))
        assertTrue(endFunction.contains("scoreboard players add #for_"))
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
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            destinationTargetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
            destinationSpec = PositionSpec(PositionKind.TARGET)
        }

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val text = success.functions.values.joinToString("\n")
        // 移動先は複数エンティティへtpできないため、limit=1の単一セレクタへ固定される。
        assertTrue(text.contains(Regex("""tp @a\[distance=0\.\.,limit=1,sort=nearest] @a\[.*limit=1.*]""")))
    }

    @Test
    fun `repeat count may read a world variable in vanilla output`() {
        val store = ScriptStore(temp.resolve("scripts"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "for-world-vanilla")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["count"] = "\${limit}"
        GraphEditor.appendToForBody(script.graph, start.id, CommandType.DISPLAY_TEXT).targetSpec =
            TargetSpec(TargetKind.NEAREST_PLAYER)

        val success = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(
                script,
                mapOf("limit" to VariableType.NUMBER),
            ),
        )
        val text = success.functions.values.joinToString("\n")
        // ワールド変数storageからfor上限を開始時に転記する。
        assertTrue(
            text.contains("data get storage kantan:variables ${VanillaStorageNames.variablePath("limit", false)}"),
            text,
        )
    }

}
