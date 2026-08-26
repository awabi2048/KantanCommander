package me.awabi2048.kantancommander.export

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.PlacementStore
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
import me.awabi2048.kantancommander.placement.PlacedBlockMaterials
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.mwmchanpon.api.PreparedStandaloneExport
import me.awabi2048.mwmchanpon.api.StandaloneExportContext
import me.awabi2048.mwmchanpon.api.StandaloneExportContributor

class KantanStandaloneExportContributor(
    private val plugin: KantanCommanderPlugin,
) : StandaloneExportContributor {
    override fun prepare(context: StandaloneExportContext): PreparedStandaloneExport {
        val worldsByName = context.worlds.associateBy { it.sourceWorldName }
        val selected = plugin.placements.all().filter { it.world in worldsByName }
        val errors = mutableListOf<String>()
        context.worlds.forEach { world ->
            plugin.variables.definitions(world.uuid).forEach { (name, value) ->
                validateVariable(world.sourceWorldName, name, value)?.let(errors::add)
                if (value.type == VariableType.INTEGER &&
                    value.integerValue?.let { it !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() } != false
                ) {
                    errors += "${world.sourceWorldName}/$name: 整数初期値はバニラscoreboardの範囲外です"
                }
            }
        }
        val programs = selected.mapNotNull { placement ->
            val liveWorld = plugin.server.getWorld(placement.world)
            val liveBlock = liveWorld?.getBlockAt(placement.x, placement.y, placement.z)
            if (liveBlock == null || !PlacedBlockMaterials.isPlacedBlock(liveBlock.type)) {
                errors += "${placement.key}: 配置ブロックの実体が存在しないか、別のブロックへ変更されています"
                return@mapNotNull null
            }
            val sourceScript = plugin.scripts.load(placement.scriptId)
            if (sourceScript == null) {
                errors += "${placement.key}: script ${placement.scriptId} is missing"
                return@mapNotNull null
            }
            val world = worldsByName.getValue(placement.world)
            val namespace = world.uuid.toString().replace("-", "")
            val script = namespaceWorldVariables(sourceScript, namespace)
            val worldVariableTypes = plugin.variables.definitions(world.uuid)
                .mapKeys { (name, _) -> "${namespace}_$name" }
                .mapValues { (_, value) -> value.type }
            // 同じスクリプトを複数ワールドへ配置しても、ワールド変数を含む関数本文が衝突しない名前にする。
            val entryFunctionName = standaloneEntryFunctionName(placement, script, world.uuid)
            when (
                val compilation = plugin.exporter.compileForStandalone(
                    script,
                    worldVariableTypes,
                    entryFunctionName,
                )
            ) {
                is StandaloneCompilation.Failure -> {
                    compilation.errors.forEach { errors += "${placement.key}: $it" }
                    null
                }
                is StandaloneCompilation.Success -> PreparedProgram(
                    placement = placement.copy(displayId = null),
                    script = script,
                    dimensionKey = world.dimensionKey,
                    variableNamespace = namespace,
                    entryFunctionName = compilation.entryFunctionName,
                    functions = compilation.functions.toMap(),
                ).also {
                    compilation.warnings.forEach(plugin.logger::warning)
                }
            }
        }
        if (errors.isNotEmpty()) {
            throw IllegalStateException(
                "Kantan Commander export validation failed:\n${errors.distinct().joinToString("\n")}"
            )
        }
        val variableDefinitions = context.worlds.associate { world ->
            world.dimensionKey to PreparedVariables(
                world.uuid.toString().replace("-", ""),
                plugin.variables.definitions(world.uuid),
            )
        }
        return PreparedKantanExport(programs, variableDefinitions)
    }

    private fun validateVariable(world: String, name: String, value: WorldVariableValue): String? {
        val valid = when (value.type) {
            VariableType.BOOLEAN -> value.booleanValue != null
            VariableType.INTEGER -> value.integerValue != null
            VariableType.DECIMAL -> value.decimalValue?.isFinite() == true
            VariableType.TEXT -> value.textValue != null
            VariableType.POSITION -> value.position?.let {
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
                    it.yaw.isFinite() && it.pitch.isFinite()
            } == true
            VariableType.ENTITY -> value.entityId != null
        }
        return if (valid) null else "$world/$name: 変数初期値が不完全または有限値ではありません"
    }

    private fun standaloneEntryFunctionName(
        placement: DiskPlacement,
        script: DiskScript,
        worldId: UUID,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${script.id}/$worldId/${placement.key}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "p_${digest.take(24)}"
    }

    private fun namespaceWorldVariables(script: DiskScript, namespace: String): DiskScript {
        val graph = script.graph.deepCopy()
        fun rewrite(current: me.awabi2048.kantancommander.model.CommandGraph) {
            current.nodes.values.forEach { node ->
                if (node.type == CommandType.VARIABLE &&
                    node.string("scope", VariableScope.TEMPORARY.name) == VariableScope.WORLD.name
                ) {
                    node.params["name"] = "${namespace}_${node.string("name")}"
                }
                if (node.type == CommandType.CONDITION &&
                    node.string("variableScope", VariableScope.TEMPORARY.name) == VariableScope.WORLD.name
                ) {
                    node.params["variable"] = "${namespace}_${node.string("variable")}"
                }
                node.snapshot?.let(::rewrite)
            }
        }
        rewrite(graph)
        return script.copy(graph = graph)
    }
}

internal data class PreparedProgram(
    val placement: DiskPlacement,
    val script: DiskScript,
    val dimensionKey: String,
    val variableNamespace: String,
    val entryFunctionName: String = script.id.toString(),
    val functions: Map<String, String>,
)

internal data class PreparedVariables(
    val namespace: String,
    val values: Map<String, WorldVariableValue>,
)

internal class PreparedKantanExport(
    private val programs: List<PreparedProgram>,
    private val variableDefinitions: Map<String, PreparedVariables>,
) : PreparedStandaloneExport {
    override fun writeTo(stagingWorld: Path) {
        if (programs.isEmpty() && variableDefinitions.values.all { it.values.isEmpty() }) return
        val pack = stagingWorld.resolve("datapacks/kantan-commander")
        val functions = pack.resolve("data/kantan/function")
        val tags = pack.resolve("data/minecraft/tags/function")
        Files.createDirectories(functions)
        Files.createDirectories(tags)
        Files.writeString(
            pack.resolve("pack.mcmeta"),
            """{"pack":{"pack_format":101,"description":"Kantan Commander standalone runtime"}}""",
        )

        val load = mutableListOf(
            "scoreboard objectives add kc_result dummy",
            "scoreboard objectives add kc_vars dummy",
            "scoreboard objectives add kc_runtime dummy",
            "scoreboard objectives add kc_timer dummy",
        )
        val tick = mutableListOf<String>()
        // 関数名の衝突を無言の上書きにせず、同一内容だけを共有する。
        val writtenFunctions = mutableMapOf<String, String>()
        variableDefinitions.forEach { (dimension, variables) ->
            variables.values.forEach { (name, value) ->
                load += initializeVariable(dimension, "${variables.namespace}_$name", value)
            }
        }
        programs.forEach { program ->
            program.functions.forEach { (name, body) ->
                val previous = writtenFunctions.putIfAbsent(name, body)
                require(previous == null || previous == body) {
                    "異なる配置のKantan関数が同じ名前へ解決されました: $name"
                }
                if (previous == null) {
                    val target = functions.resolve("$name.mcfunction")
                    Files.createDirectories(target.parent)
                    Files.writeString(target, body)
                }
            }
            load += "execute in ${program.dimensionKey} run kill @e[type=minecraft:block_display,tag=${PlacementStore.DISPLAY_TAG}]"
            val entry = program.entryFunctionName
            val command = if (program.script.timer.enabled) {
                val wrapper = "placed/${entry}_timer"
                val holder = timerHolder(entry)
                Files.createDirectories(functions.resolve("placed"))
                Files.writeString(
                    functions.resolve("$wrapper.mcfunction"),
                    timerFunction(entry, program.script.timer.intervalTicks),
                )
                load += "scoreboard players set $holder kc_timer 0"
                tick += "execute unless score $holder kc_timer matches ${program.script.timer.intervalTicks}.. " +
                    "run scoreboard players add $holder kc_timer 1"
                "function kantan:$wrapper"
            } else {
                "function kantan:$entry"
            }
            load += setBlockCommand(program, command)
        }
        Files.writeString(functions.resolve("load.mcfunction"), load.distinct().joinToString("\n", postfix = "\n"))
        Files.writeString(tags.resolve("load.json"), """{"values":["kantan:load"]}""")
        if (tick.isNotEmpty()) {
            Files.writeString(functions.resolve("tick.mcfunction"), tick.distinct().joinToString("\n", postfix = "\n"))
            Files.writeString(tags.resolve("tick.json"), """{"values":["kantan:tick"]}""")
        }
    }

    private fun setBlockCommand(program: PreparedProgram, command: String): String {
        val placement = program.placement
        val facing = placement.facing.lowercase().takeIf {
            it in setOf("north", "south", "east", "west", "up", "down")
        } ?: "north"
        val repeating = program.script.timer.enabled
        val block = if (repeating) "minecraft:repeating_command_block" else "minecraft:command_block"
        val automatic = repeating && program.script.activation == ActivationMode.ALWAYS_ACTIVE
        return "execute in ${program.dimensionKey} run setblock ${placement.x} ${placement.y} ${placement.z} " +
            "$block[facing=$facing]{Command:\"${escapeNbt(command)}\",auto:${if (automatic) 1 else 0}b}"
    }

    private fun timerFunction(scriptId: String, intervalTicks: Long): String {
        val holder = timerHolder(scriptId)
        return buildString {
            appendLine("execute unless score $holder kc_timer matches $intervalTicks.. run return 1")
            appendLine("scoreboard players set $holder kc_timer 0")
            appendLine("return run function kantan:$scriptId")
        }
    }

    private fun timerHolder(scriptId: String) =
        "#timer_${scriptId.replace("-", "")}"

    private fun initializeVariable(dimension: String, name: String, value: WorldVariableValue): String {
        val holder = VanillaScoreNames.variableHolder(name, temporary = false)
        return when (value.type) {
            VariableType.BOOLEAN ->
                "scoreboard players set $holder kc_vars ${if (value.booleanValue == true) 1 else 0}"
            VariableType.INTEGER -> {
                val number = value.integerValue ?: 0L
                require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    "world variable $name exceeds the vanilla scoreboard integer range"
                }
                "scoreboard players set $holder kc_vars $number"
            }
            VariableType.DECIMAL ->
                storageSet(name, "${value.decimalValue ?: 0.0}d")
            VariableType.TEXT ->
                storageSet(name, "\"${escapeNbt(value.textValue.orEmpty())}\"")
            VariableType.POSITION -> {
                val position = requireNotNull(value.position) { "world variable $name has no position" }
                storageSet(
                    name,
                    "{position:[${position.x}d,${position.y}d,${position.z}d]," +
                        "rotation:[${position.yaw}f,${position.pitch}f]}",
                )
            }
            VariableType.ENTITY ->
                storageSet(name, uuidSnbt(value.entityId ?: UUID(0L, 0L)))
        }
    }

    private fun storageSet(name: String, snbt: String): String =
        "data modify storage kantan:variables ${VanillaStorageNames.variablePath(name, temporary = false)} set value $snbt"

    private fun escapeNbt(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun uuidSnbt(uuid: UUID): String {
        val most = uuid.mostSignificantBits
        val least = uuid.leastSignificantBits
        return "[I;${(most shr 32).toInt()},${most.toInt()},${(least shr 32).toInt()},${least.toInt()}]"
    }
}
