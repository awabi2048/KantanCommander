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
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.effectiveTrigger
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.placement.PlacedBlockMaterials
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.VariableScope
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
                if (value.type == ValueType.NUMBER && value.numberValue?.isFinite() != true) {
                    errors += "${world.sourceWorldName}/$name: 数値初期値は有限値で指定してください"
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
            // issue #7: イベント起動にバニラの等価表現はありません。
            // 黙って別意味へ変換せず、単体出力の検証で明示エラーにします。
            if (sourceScript.effectiveTrigger().kind == TriggerKind.EVENT) {
                errors += "${placement.key}: イベント起動は単体出力に変換できません"
                return@mapNotNull null
            }
            val world = worldsByName.getValue(placement.world)
            val namespace = world.uuid.toString().replace("-", "")
            val script = namespaceWorldVariables(sourceScript, namespace)
            val worldValueTypes = plugin.variables.definitions(world.uuid)
                .mapKeys { (name, _) -> "${namespace}_$name" }
                .mapValues { (_, value) -> value.type }
            // 同じスクリプトを複数ワールドへ配置しても、ワールド変数を含む関数本文が衝突しない名前にする。
            val entryFunctionName = standaloneEntryFunctionName(placement, script, world.uuid)
            when (
                val compilation = plugin.exporter.compileForStandalone(
                    script,
                    worldValueTypes,
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

    private fun validateVariable(world: String, name: String, value: TypedValue): String? {
        // #2: 単体出力の初期値埋め込みはスカラー3型だけに対応します。複合型は
        // 構造がバニラstorageへ直結しないため、明示エラーにします。
        val valid = when (value.type) {
            ValueType.NUMBER -> value.numberValue?.isFinite() == true && value.stringValue == null
            ValueType.STRING -> value.stringValue != null && value.numberValue == null
            ValueType.BOOLEAN -> value.booleanValue != null
            ValueType.LOCATION,
            ValueType.VECTOR,
            ValueType.ITEM,
            ValueType.BLOCK,
            ValueType.ENTITY,
            ValueType.SOUND,
            ValueType.EFFECT,
            -> return "$world/$name: 複合型の変数は単体出力に未対応です"
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
                if (node.type == CommandType.VARIABLE) {
                    node.params["name"] = "${namespace}_${node.string("name")}"
                }
                node.targetSpec = namespaceTarget(node.targetSpec, namespace)
                node.secondaryTargetSpec = namespaceTarget(node.secondaryTargetSpec, namespace)
                node.destinationTargetSpec = namespaceTarget(node.destinationTargetSpec, namespace)
                val operation = node.string("operation", VariableOperation.DEFINE.name)
                node.params.keys.toList().forEach { key ->
                    val raw = node.params[key] ?: return@forEach
                    node.params[key] = namespaceExportParam(node, key, raw, namespace, operation)
                }
                node.snapshot?.let(::rewrite)
            }
        }
        rewrite(graph)
        return script.copy(graph = graph)
    }

    private fun namespaceTarget(
        target: me.awabi2048.kantancommander.model.TargetSpec?,
        namespace: String,
    ): me.awabi2048.kantancommander.model.TargetSpec? = target?.copy(
        tag = namespaceTemplate(target.tag, namespace),
        name = namespaceTemplate(target.name, namespace),
    )

    private fun namespaceExpression(raw: String, namespace: String): String =
        namespaceStandaloneExpression(raw, namespace)
}

/**
 * issue #5: 単体出力の変数名前空間付与は、1キー分の純粋な書換え規則です。
 * クラスの外に出して単体検証できる形にします。
 */
internal fun namespaceExportParam(
    node: me.awabi2048.kantancommander.model.CommandNode,
    key: String,
    raw: String,
    namespace: String,
    operation: String = node.string("operation", VariableOperation.DEFINE.name),
): String = when {
    node.type == CommandType.VARIABLE && key == "value" &&
        operation == VariableOperation.CHANGE.name &&
        node.string("changeMode", "ASSIGN") == "CALCULATE" -> namespaceStandaloneExpression(raw, namespace)
    key in DIRECT_VARIABLE_FIELDS &&
        node.type == CommandType.CONDITION &&
        node.conditionSubject() == ConditionSubject.VARIABLE -> "${namespace}_$raw"
    // 型付き条件比較の左辺は、WORLD参照の場合だけ素の変数名を持つため
    // 名前空間を付けます。EXECUTION参照は一時変数のまま扱い、
    // ENTITY参照は検証層で拒否済みです。
    key == "leftName" &&
        node.type == CommandType.CONDITION &&
        node.conditionSubject() == ConditionSubject.VALUE &&
        node.string("leftScope").ifBlank { VariableScope.WORLD.name } == VariableScope.WORLD.name &&
        raw.isNotBlank() -> "${namespace}_$raw"
    key in REGISTRY_FIELDS || key in NON_TEXT_FIELDS -> raw
    else -> namespaceTemplate(raw, namespace) ?: raw
}

internal fun namespaceTemplate(raw: String?, namespace: String): String? = raw?.let {
    TEMPLATE_REFERENCE.replace(it) { match ->
        "${'$'}{${namespace}_${match.groupValues[1]}}"
    }
}

internal fun namespaceStandaloneExpression(raw: String, namespace: String): String {
    val parsed = NumericExpression.parse(raw).expression ?: return raw
    return parsed.references
        .filterNot(SystemVariableNames::isSystemName)
        .fold(raw) { expression, reference ->
            expression.replace(
                "${'$'}{$reference}",
                "${'$'}{${namespace}_$reference}",
            )
        }
}

private val TEMPLATE_REFERENCE = Regex("\\$\\{([a-z][a-z0-9_.-]{0,63})}")
private val DIRECT_VARIABLE_FIELDS = setOf("variable")
private val REGISTRY_FIELDS = setOf("summonEntity", "sound", "effect", "block", "item", "itemData", "diskId", "fireworkItem", "fireworkData")
private val NON_TEXT_FIELDS = setOf(
    "name", "type", "operation", "changeMode", "action", "mode", "kind", "operator", "slot", "placeDestination",
    "tagOperation", "soundScope", "soundOperation", "soundCategory",
    "particleShape", "geometryWireframe", "shakeType", "overwrite", "inverted",
    "attributeOperation", "modifierOperation", "property",
)

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
    val values: Map<String, TypedValue>,
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
            "scoreboard objectives add kc_tu0 dummy",
            "scoreboard objectives add kc_tu1 dummy",
            "scoreboard objectives add kc_tu2 dummy",
            "scoreboard objectives add kc_tu3 dummy",
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

    private fun initializeVariable(dimension: String, name: String, value: TypedValue): String {
        return when (value.type) {
            ValueType.NUMBER -> storageSet(name, "${value.numberValue ?: 0.0}d")
            ValueType.STRING -> storageSet(name, "\"${escapeNbt(value.stringValue.orEmpty())}\"")
            ValueType.BOOLEAN -> storageSet(name, if (value.booleanValue == true) "1b" else "0b")
            ValueType.LOCATION,
            ValueType.VECTOR,
            ValueType.ITEM,
            ValueType.BLOCK,
            ValueType.ENTITY,
            ValueType.SOUND,
            ValueType.EFFECT,
            -> error("unsupported variable type for standalone export: ${value.type}")
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
