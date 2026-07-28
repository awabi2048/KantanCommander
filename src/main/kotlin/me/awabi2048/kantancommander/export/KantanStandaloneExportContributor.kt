package me.awabi2048.kantancommander.export

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.PlacementStore
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
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
        val programs = selected.mapNotNull { placement ->
            val sourceScript = plugin.scripts.load(placement.scriptId)
            if (sourceScript == null) {
                errors += "${placement.key}: script ${placement.scriptId} is missing"
                return@mapNotNull null
            }
            val world = worldsByName.getValue(placement.world)
            val namespace = world.uuid.toString().replace("-", "")
            val script = namespaceWorldVariables(sourceScript, namespace)
            when (val compilation = plugin.exporter.compileForStandalone(script)) {
                is StandaloneCompilation.Failure -> {
                    compilation.errors.forEach { errors += "${placement.key}: $it" }
                    null
                }
                is StandaloneCompilation.Success -> PreparedProgram(
                    placement = placement.copy(displayId = null),
                    script = script,
                    dimensionKey = world.dimensionKey,
                    variableNamespace = namespace,
                    functions = compilation.functions.toMap(),
                )
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

private data class PreparedProgram(
    val placement: DiskPlacement,
    val script: DiskScript,
    val dimensionKey: String,
    val variableNamespace: String,
    val functions: Map<String, String>,
)

private data class PreparedVariables(
    val namespace: String,
    val values: Map<String, WorldVariableValue>,
)

private class PreparedKantanExport(
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
        variableDefinitions.forEach { (dimension, variables) ->
            variables.values.forEach { (name, value) ->
                load += initializeVariable(dimension, "${variables.namespace}_$name", value)
            }
        }
        programs.forEach { program ->
            program.functions.forEach { (name, body) ->
                val target = functions.resolve("$name.mcfunction")
                Files.createDirectories(target.parent)
                Files.writeString(target, body)
            }
            load += "execute in ${program.dimensionKey} run kill @e[type=minecraft:block_display,tag=${PlacementStore.DISPLAY_TAG}]"
            val entry = program.script.id.toString()
            val command = if (program.script.timer.enabled) {
                val wrapper = "placed/${entry}_timer"
                Files.createDirectories(functions.resolve("placed"))
                Files.writeString(
                    functions.resolve("$wrapper.mcfunction"),
                    timerFunction(entry, program.script.timer.intervalTicks),
                )
                "function kantan:$wrapper"
            } else {
                "function kantan:$entry"
            }
            load += setBlockCommand(program, command)
        }
        Files.writeString(functions.resolve("load.mcfunction"), load.distinct().joinToString("\n", postfix = "\n"))
        Files.writeString(tags.resolve("load.json"), """{"values":["kantan:load"]}""")
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
        val holder = "#timer_${scriptId.replace("-", "")}"
        return buildString {
            appendLine("scoreboard players add $holder kc_timer 1")
            appendLine("execute unless score $holder kc_timer matches $intervalTicks.. run return 1")
            appendLine("scoreboard players set $holder kc_timer 0")
            appendLine("return run function kantan:$scriptId")
        }
    }

    private fun initializeVariable(dimension: String, name: String, value: WorldVariableValue): String {
        val holder = "#w_${name.replace(Regex("[^a-z0-9_.-]"), "_")}"
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
                storageSet(dimension, name, "${value.decimalValue ?: 0.0}d")
            VariableType.TEXT ->
                storageSet(dimension, name, "\"${escapeNbt(value.textValue.orEmpty())}\"")
            VariableType.POSITION -> {
                val position = requireNotNull(value.position) { "world variable $name has no position" }
                storageSet(
                    dimension,
                    name,
                    "{x:${position.x}d,y:${position.y}d,z:${position.z}d,yaw:${position.yaw}f,pitch:${position.pitch}f}",
                )
            }
            VariableType.ENTITY ->
                storageSet(dimension, name, "\"${value.entityId ?: UUID(0L, 0L)}\"")
        }
    }

    private fun storageSet(dimension: String, name: String, snbt: String): String =
        "data modify storage kantan:variables ${storagePath(dimension, name)} set value $snbt"

    private fun storagePath(dimension: String, name: String): String =
        "${dimension.replace(Regex("[^a-zA-Z0-9_]"), "_")}.${name.replace(Regex("[^a-zA-Z0-9_]"), "_")}"

    private fun escapeNbt(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
