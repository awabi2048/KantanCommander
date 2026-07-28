package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.item.ItemStackCodec
import org.bukkit.Material
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.security.MessageDigest

class VanillaDatapackExporter(
    private val scripts: ScriptStore,
    private val outputRoot: File,
    private val maximumCommandCount: Int = 1024,
) {
    fun compileForStandalone(
        root: DiskScript,
        worldVariableTypes: Map<String, VariableType> = emptyMap(),
    ): StandaloneCompilation {
        val exportRoot = root.copy(graph = root.graph.deepCopy())
        val errors = mutableListOf<String>()
        errors += ExecutableScriptValidator.validate(exportRoot)
        annotateVariableTypes(exportRoot.graph, worldVariableTypes, errors)
        validate(exportRoot, errors, Collections.newSetFromMap(IdentityHashMap()))
        return if (errors.isEmpty()) {
            StandaloneCompilation.Success(compile(exportRoot))
        } else {
            StandaloneCompilation.Failure(errors.distinct())
        }
    }

    fun export(root: DiskScript): ExportResult {
        val compilation = compileForStandalone(root)
        if (compilation is StandaloneCompilation.Failure) return ExportResult.Failure(compilation.errors)

        val pack = outputRoot.resolve("kantan-${root.id}")
        val functions = pack.resolve("data/kantan/function").also(File::mkdirs)
        val loadTags = pack.resolve("data/minecraft/tags/function").also(File::mkdirs)
        pack.resolve("pack.mcmeta").writeText(
            """{"pack":{"pack_format":101,"description":"Kantan Commander export"}}""",
            Charsets.UTF_8,
        )
        functions.resolve("load.mcfunction").writeText(
            "scoreboard objectives add kc_result dummy\nscoreboard objectives add kc_vars dummy\nscoreboard objectives add kc_runtime dummy\n",
            Charsets.UTF_8,
        )
        loadTags.resolve("load.json").writeText("""{"values":["kantan:load"]}""", Charsets.UTF_8)
        (compilation as StandaloneCompilation.Success).functions.forEach { (name, content) ->
            functions.resolve("$name.mcfunction").writeText(content, Charsets.UTF_8)
        }
        return ExportResult.Success(pack)
    }

    private fun validate(
        script: DiskScript,
        errors: MutableList<String>,
        visited: MutableSet<CommandGraph>,
    ) {
        if (!visited.add(script.graph)) {
            errors += "${script.id}: 別ディスクのコピー内容が循環参照しています"
            return
        }
        me.awabi2048.kantancommander.data.GraphValidator.validate(script.graph).forEach {
            errors += "${script.id}: $it"
        }
        script.graph.nodes.values.forEach { node ->
            when (node.type) {
                CommandType.GIVE_ITEM -> {
                    val item = node.string("item")
                    if (!item.startsWith("minecraft:") || Material.matchMaterial(item) == null) {
                        errors += "${script.id}/${node.id}: バニラに存在しないアイテムです: $item"
                    }
                    val itemData = node.string("itemData")
                    if (itemData.isNotBlank() && ItemStackCodec.decode(itemData)?.hasItemMeta() != false) {
                        errors += "${script.id}/${node.id}: 保存されたItemStackメタデータは完全バニラ出力に未対応です"
                    }
                }
                CommandType.ENTITY_ACTION -> if (node.string("action") !in setOf("ride", "dismount")) {
                    errors += "${script.id}/${node.id}: プラグイン固有のエンティティ操作です"
                }
                CommandType.TELEPORT -> if (node.string("world").isNotBlank()) {
                    errors += "${script.id}/${node.id}: 出力先ワールドを検証できない固定ワールド参照です"
                } else {
                    validatePosition(script, node, node.destinationSpec, errors)
                }
                CommandType.DISK_CALL -> if (node.snapshot == null) {
                    errors += "${script.id}/${node.id}: コピー内容がありません"
                } else {
                    validate(script.copy(graph = node.snapshot!!), errors, visited)
                }
                CommandType.CONDITION -> validateCondition(script, node, errors)
                CommandType.CONTEXT -> validateContext(script, node, contextFrom(node), errors)
                CommandType.VARIABLE -> {
                    if (node.string("name").isBlank()) errors += "${script.id}/${node.id}: variable name is missing"
                    val type = runCatching { VariableType.valueOf(node.string("type")) }.getOrNull()
                    val operation = runCatching {
                        VariableOperation.valueOf(node.string("operation", VariableOperation.SET.name))
                    }.getOrNull()
                    val storageOperationSupported =
                        type in setOf(VariableType.DECIMAL, VariableType.TEXT) &&
                            operation in setOf(VariableOperation.SET, VariableOperation.CLEAR) ||
                            type in setOf(VariableType.POSITION, VariableType.ENTITY) &&
                            operation == VariableOperation.CLEAR
                    if (type !in setOf(VariableType.BOOLEAN, VariableType.INTEGER) && !storageOperationSupported) {
                        errors += "${script.id}/${node.id}: ${type ?: "不明"}型の変数は完全バニラ出力に未対応です"
                    }
                    if (type == VariableType.INTEGER &&
                        node.string("value") !in setOf("\$current_iteration_value", "\$current_loop_count") &&
                        operation in setOf(VariableOperation.SET, VariableOperation.ADD, VariableOperation.SUBTRACT) &&
                        node.string("value").toLongOrNull()?.let { it !in VANILLA_INTEGER_RANGE } != false
                    ) {
                        errors += "${script.id}/${node.id}: 整数値はバニラscoreboardの範囲外です"
                    }
                    if (operation in setOf(VariableOperation.STORE_POSITION, VariableOperation.STORE_TARGET)) {
                        errors += "${script.id}/${node.id}: $operation は完全バニラ出力に未対応です"
                    }
                    if (node.string("value") in setOf("\$current_iteration_value", "\$current_loop_count")) {
                        if (node.string("type") != VariableType.INTEGER.name) {
                            errors += "${script.id}/${node.id}: ループ値は整数変数だけへ保存できます"
                        }
                        if (enclosingFor(script.graph, node.id) == null) {
                            errors += "${script.id}/${node.id}: ループ値はfor本体内だけで参照できます"
                        }
                    }
                }
                CommandType.FOR_START -> {
                    listOf("start", "end", "step").forEach { field ->
                        if (node.string("${field}Source", "FIXED") == "FIXED") {
                            val value = node.string("${field}Value").toLongOrNull()
                            if (value == null) {
                                errors += "${script.id}/${node.id}: forの${field}値は64bit符号付き整数で指定してください"
                            } else if (value !in VANILLA_INTEGER_RANGE) {
                                errors += "${script.id}/${node.id}: forの${field}値はバニラscoreboardの範囲外です"
                            }
                        }
                    }
                    if (node.string("stepSource", "FIXED") == "FIXED" && node.string("stepValue").toLongOrNull() == 0L) {
                        errors += "${script.id}/${node.id}: forの増分に0は指定できません"
                    }
                }
                CommandType.WAIT -> {
                    errors += "${script.id}/${node.id}: 待機は実行者と実行位置を保持できないため完全バニラ出力できません"
                }
                else -> Unit
            }
            node.contextOverride?.let { validateContext(script, node, it, errors) }
            validatePosition(script, node, node.conditionPositionSpec, errors)
            if (node.secondaryTargetSpec?.kind == TargetKind.FIXED_ENTITY) {
                errors += "${script.id}/${node.id}: 固定エンティティ参照は完全バニラ出力できません"
            }
            listOfNotNull(node.targetSpec, node.secondaryTargetSpec, node.destinationTargetSpec).forEach { spec ->
                if (spec.excludeActivator || spec.excludeExecutor) {
                    errors += "${script.id}/${node.id}: 実行者・起動者の動的除外は完全バニラ出力できません"
                }
            }
        }
        visited.remove(script.graph)
    }

    private fun validateCondition(script: DiskScript, node: CommandNode, errors: MutableList<String>) {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrNull()
        if (kind == null) {
            errors += "${script.id}/${node.id}: 不明な条件種別です"
            return
        }
        if (kind == ConditionKind.VARIABLE_STATE && node.string("variable").isBlank()) {
            errors += "${script.id}/${node.id}: 一時変数名がありません"
        }
        if (kind == ConditionKind.VARIABLE_STATE && node.params[EXPORT_VARIABLE_TYPE] == null) {
            errors += "${script.id}/${node.id}: 変数の型を一意に解決できません"
        }
    }

    private fun annotateVariableTypes(
        graph: CommandGraph,
        worldVariableTypes: Map<String, VariableType>,
        errors: MutableList<String>,
    ) {
        val temporaryTypes = mutableMapOf<String, MutableSet<VariableType>>()
        fun collect(current: CommandGraph) {
            current.nodes.values.forEach { node ->
                if (node.type == CommandType.VARIABLE &&
                    node.string("scope", "TEMPORARY") != "WORLD"
                ) {
                    runCatching { VariableType.valueOf(node.string("type")) }.getOrNull()?.let {
                        temporaryTypes.getOrPut(node.string("name")) { linkedSetOf() } += it
                    }
                }
                node.snapshot?.let(::collect)
            }
        }
        collect(graph)

        fun annotate(current: CommandGraph) {
            current.nodes.values.forEach { node ->
                if (node.type == CommandType.CONDITION &&
                    node.string("kind") == ConditionKind.VARIABLE_STATE.name
                ) {
                    val name = node.string("variable")
                    val type = if (node.string("variableScope", "TEMPORARY") == "WORLD") {
                        worldVariableTypes[name]
                    } else {
                        temporaryTypes[name]?.singleOrNull()
                    }
                    val storageExistenceCheck = node.string("operator") in setOf("set", "unset")
                    if (type !in setOf(VariableType.BOOLEAN, VariableType.INTEGER) && !storageExistenceCheck) {
                        errors += "${node.id}: ${type ?: "不明"}型の変数条件は完全バニラ出力に未対応です"
                    } else if (type != null) {
                        node.params[EXPORT_VARIABLE_TYPE] = requireNotNull(type).name
                    }
                }
                node.snapshot?.let(::annotate)
            }
        }
        annotate(graph)
    }

    private fun validateContext(script: DiskScript, node: CommandNode, context: ExecutionContextSpec, errors: MutableList<String>) {
        if (context.target?.kind == TargetKind.FIXED_ENTITY || context.executor?.kind == TargetKind.FIXED_ENTITY) {
            errors += "${script.id}/${node.id}: 固定エンティティ参照は完全バニラ出力できません"
        }
        if (listOfNotNull(context.target, context.executor).any { it.excludeActivator || it.excludeExecutor }) {
            errors += "${script.id}/${node.id}: 実行者・起動者の動的除外は完全バニラ出力できません"
        }
        validatePosition(script, node, context.position, errors)
        if (context.facing?.kind == FacingKind.MYWORLD_SPAWN) {
            errors += "${script.id}/${node.id}: 出力先のMyWorldスポーンを検証できません"
        }
    }

    private fun validatePosition(
        script: DiskScript,
        node: CommandNode,
        position: me.awabi2048.kantancommander.model.PositionSpec?,
        errors: MutableList<String>,
    ) {
        val kind = position?.kind
        if (kind in setOf(
                PositionKind.MYWORLD_SPAWN,
                PositionKind.TEMPORARY_VARIABLE,
                PositionKind.WORLD_VARIABLE,
            )
        ) {
            errors += "${script.id}/${node.id}: ${kind}の位置は完全バニラ出力に未対応です"
        }
    }

    private fun compile(script: DiskScript): Map<String, String> =
        linkedMapOf<String, String>().also { compileGraph(script.graph, script.id.toString(), it, resetBudget = true) }

    private fun compileGraph(
        graph: CommandGraph,
        prefix: String,
        output: MutableMap<String, String>,
        resetBudget: Boolean = false,
    ) {
        val entryCall = graph.entryNodeId?.let { "return run function kantan:${prefix}_$it\n" } ?: "return 1\n"
        output[prefix] = if (resetBudget) {
            buildString {
                appendLine("scoreboard players set #executed kc_runtime 0")
                temporaryNames(graph).forEach {
                    appendLine("scoreboard players reset ${variableHolder(it, temporary = true)} kc_vars")
                    appendLine("data remove storage kantan:variables ${VanillaStorageNames.variablePath(it, temporary = true)}")
                }
                append(entryCall)
            }
        } else entryCall
        graph.nodes.values.forEach { node ->
            val lines = mutableListOf<String>()
            val emptyFor = node.type == CommandType.FOR_START && node.trueNext == node.pairedNodeId
            if (!emptyFor) {
                lines += "execute if score #executed kc_runtime matches ${maximumCommandCount.coerceAtLeast(1)}.. run return 0"
                lines += "scoreboard players add #executed kc_runtime 1"
            }
            when {
                emptyFor -> node.pairedNodeId?.let(graph.nodes::get)?.next?.let {
                    lines += "return run function kantan:${prefix}_$it"
                } ?: run { lines += "return 1" }
                node.type == CommandType.FOR_START -> {
                    val loop = loopName(node.id)
                    lines += assignLoopValue(loop, "value", node, "start")
                    lines += assignLoopValue(loop, "end", node, "end")
                    lines += assignLoopValue(loop, "step", node, "step")
                    lines += "scoreboard players set #${loop}_count kc_vars 1"
                    lines += "return run function kantan:${prefix}_${node.id}_check"
                    output["${prefix}_${node.id}_check"] = loopCheck(graph, prefix, node)
                }
                node.type == CommandType.FOR_END -> {
                    val start = node.pairedNodeId?.let(graph.nodes::get)
                    if (start != null) {
                        val loop = loopName(start.id)
                        lines += assignLoopValue(loop, "step", start, "step")
                        lines += "scoreboard players operation #${loop}_value kc_vars += #${loop}_step kc_vars"
                        lines += "scoreboard players add #${loop}_count kc_vars 1"
                        lines += "return run function kantan:${prefix}_${start.id}_check"
                    }
                }
                node.type == CommandType.BREAK -> {
                    enclosingFor(graph, node.id)?.pairedNodeId?.let(graph.nodes::get)?.next?.let {
                        lines += "return run function kantan:${prefix}_$it"
                    } ?: run { lines += "return 1" }
                }
                node.type == CommandType.CONTINUE -> {
                    enclosingFor(graph, node.id)?.pairedNodeId?.let {
                        lines += "return run function kantan:${prefix}_$it"
                    } ?: run { lines += "return 1" }
                }
                node.type == CommandType.DISK_CALL -> {
                    val snapshotPrefix = "${prefix}_snapshot_${node.id}"
                    node.snapshot?.let { compileGraph(it, snapshotPrefix, output, resetBudget = false) }
                    val call = "function kantan:$snapshotPrefix"
                    lines += storeFunctionResult(node, node.contextOverride?.let { wrapContext(it, call) } ?: call)
                }
                node.type == CommandType.CONTEXT -> {
                    node.next?.let {
                        lines += "return run ${wrapContext(contextFrom(node), "function kantan:${prefix}_$it")}"
                    } ?: run { lines += "return 1" }
                }
                else -> lower(node, graph)?.let { command ->
                    if (node.type == CommandType.DISPLAY_TEXT && node.string("mode", "tellraw") == "title") {
                        val times = "title ${effectiveTarget(node)} times ${node.int("fadeIn", 10)} " +
                            "${node.int("stay", 60)} ${node.int("fadeOut", 10)}"
                        lines += node.contextOverride?.let { wrapContext(it, times) } ?: times
                    }
                    val contextual = node.contextOverride?.let { wrapContext(it, command) } ?: command
                    lines += storeResult(node, contextual)
                }
            }

            when (node.type) {
                CommandType.CONDITION -> {
                    val conditionContext = conditionContext(node)
                    conditionPreparation(node)?.let { preparation ->
                        lines += conditionContext?.let { context -> wrapContext(context, preparation) } ?: preparation
                    }
                    val predicate = predicate(node)
                    val inequality = node.string("kind") == ConditionKind.VARIABLE_STATE.name &&
                        node.string("operator") in setOf("!=", "unset")
                    val inverted = node.boolean("inverted") xor inequality
                    val trueCheck = if (inverted) "unless" else "if"
                    val falseCheck = if (inverted) "if" else "unless"
                    val trueBranch = node.trueNext?.let {
                        "execute $trueCheck $predicate run return run function kantan:${prefix}_$it"
                    } ?: "execute $trueCheck $predicate run return 1"
                    val falseBranch = node.falseNext?.let {
                        "execute $falseCheck $predicate run return run function kantan:${prefix}_$it"
                    } ?: "execute $falseCheck $predicate run return 1"
                    lines += conditionContext?.let { context -> wrapContext(context, trueBranch) } ?: trueBranch
                    lines += conditionContext?.let { context -> wrapContext(context, falseBranch) } ?: falseBranch
                    lines += "return 0"
                }
                CommandType.WAIT ->
                    node.next?.let { lines += "schedule function kantan:${prefix}_$it ${node.int("ticks", 20).coerceAtLeast(1)}t replace" }
                CommandType.CONTEXT -> Unit
                CommandType.FOR_START, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> Unit
                CommandType.MERGE -> node.next?.let {
                    lines += "return run function kantan:${prefix}_$it"
                } ?: run { lines += "return 1" }
                else -> {
                    val result = scoreHolder(node.id)
                    node.next?.let {
                        lines += "execute if score $result kc_result matches 1 run return run function kantan:${prefix}_$it"
                    } ?: run {
                        lines += "execute if score $result kc_result matches 1 run return 1"
                    }
                    lines += "return 0"
                }
            }
            output["${prefix}_${node.id}"] = lines.joinToString("\n", postfix = "\n")
        }
    }

    private fun lower(node: CommandNode, graph: CommandGraph): String? = when (node.type) {
        CommandType.TELEPORT -> "tp ${effectiveTarget(node)} ${destination(node)}"
        CommandType.GIVE_ITEM -> "give ${effectiveTarget(node)} ${node.string("item")} ${node.int("count", 1)}"
        CommandType.ENTITY_ACTION ->
            if (node.string("action") == "dismount") "ride ${effectiveTarget(node)} dismount"
            else "ride ${effectiveTarget(node)} mount ${selector(requireNotNull(node.secondaryTargetSpec))}"
        CommandType.DISPLAY_TEXT -> when (node.string("mode", "tellraw")) {
            "title" -> "title ${effectiveTarget(node)} title {\"text\":\"${escape(node.string("text"))}\"}"
            "actionbar" -> "title ${effectiveTarget(node)} actionbar {\"text\":\"${escape(node.string("text"))}\"}"
            else -> "tellraw ${effectiveTarget(node)} {\"text\":\"${escape(node.string("text"))}\"}"
        }
        CommandType.DISK_CALL -> null
        CommandType.VARIABLE -> lowerVariable(node, graph)
        CommandType.WAIT, CommandType.CONTEXT, CommandType.CONDITION, CommandType.MERGE,
        CommandType.FOR_START, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> null
    }

    private fun predicate(node: CommandNode): String = when (
        ConditionKind.valueOf(node.string("kind", ConditionKind.TARGET_EXISTS.name))
    ) {
        ConditionKind.TARGET_EXISTS -> "entity ${conditionTarget(node)}"
        ConditionKind.ENTITY_STATE -> when (node.string("state", "sneaking")) {
            "sneaking" -> "entity ${appendSelectorArguments(conditionTarget(node), "nbt={Pose:\\\"CROUCHING\\\"}")}"
            "on_ground" -> "entity ${appendSelectorArguments(conditionTarget(node), "nbt={OnGround:1b}")}"
            else -> "entity ${conditionTarget(node)}"
        }
        ConditionKind.VARIABLE_STATE -> {
            val temporary = node.string("variableScope", "TEMPORARY") != "WORLD"
            val type = VariableType.valueOf(node.string(EXPORT_VARIABLE_TYPE))
            if (type !in setOf(VariableType.BOOLEAN, VariableType.INTEGER)) {
                "data storage kantan:variables ${VanillaStorageNames.variablePath(node.string("variable"), temporary)}"
            } else {
                val holder = variableHolder(node.string("variable"), temporary)
                val operator = node.string("operator")
                if (operator in setOf("set", "unset")) {
                    "score $holder kc_vars matches ${Int.MIN_VALUE}..${Int.MAX_VALUE}"
                } else {
                    val value = if (type == VariableType.BOOLEAN) {
                        if (node.string("value").toBooleanStrictOrNull() == true) 1L else 0L
                    } else node.string("value").toLong()
                    when {
                        operator == ">" && value == Int.MAX_VALUE.toLong() ||
                            operator == "<" && value == Int.MIN_VALUE.toLong() ->
                            "score #never_set kc_runtime matches 1"
                        operator == "<=" && value == Int.MAX_VALUE.toLong() ||
                            operator == ">=" && value == Int.MIN_VALUE.toLong() ->
                            "score $holder kc_vars matches ${Int.MIN_VALUE}..${Int.MAX_VALUE}"
                        else -> "score $holder kc_vars matches ${scoreRange(operator, value)}"
                    }
                }
            }
        }
        ConditionKind.BLOCK_STATE ->
            "block ${
                if (node.conditionPositionSpec == null) node.string("position", "~ ~ ~") else "~ ~ ~"
            } ${node.string("block", "minecraft:air")}"
        ConditionKind.ITEM_POSSESSION ->
            "score ${conditionCountHolder(node)} kc_result matches ${node.int("count", 1).coerceAtLeast(1)}.."
    }

    private fun conditionPreparation(node: CommandNode): String? =
        if (ConditionKind.valueOf(node.string("kind")) == ConditionKind.ITEM_POSSESSION) {
            "execute store result score ${conditionCountHolder(node)} kc_result run clear " +
                "${conditionTarget(node)} ${node.string("item", "minecraft:air")} 0"
        } else null

    private fun conditionCountHolder(node: CommandNode) =
        "#c_${node.id.toString().replace("-", "")}"

    private fun conditionTarget(node: CommandNode): String {
        val spec = node.targetSpec ?: node.contextOverride?.target ?: TargetSpec(TargetKind.EXECUTOR)
        return selector(if (spec.kind in setOf(TargetKind.EXECUTOR, TargetKind.ACTIVATOR, TargetKind.INHERITED_TARGET)) spec else spec.copy(limit = 1))
    }

    private fun lowerVariable(node: CommandNode, graph: CommandGraph): String? {
        val temporary = node.string("scope", "TEMPORARY") != "WORLD"
        val holder = variableHolder(node.string("name"), temporary)
        val storagePath = VanillaStorageNames.variablePath(node.string("name"), temporary)
        val type = VariableType.valueOf(node.string("type", VariableType.BOOLEAN.name))
        val operation = VariableOperation.valueOf(node.string("operation", VariableOperation.SET.name))
        val special = node.string("value").takeIf { it in setOf("\$current_iteration_value", "\$current_loop_count") }
        if (special != null) {
            val loop = enclosingFor(graph, node.id) ?: return null
            val source = "#${loopName(loop.id)}_${if (special == "\$current_loop_count") "count" else "value"}"
            val operator = when (operation) {
                VariableOperation.SET -> "="
                VariableOperation.ADD -> "+="
                VariableOperation.SUBTRACT -> "-="
                else -> return null
            }
            return "scoreboard players operation $holder kc_vars $operator $source kc_vars"
        }
        return when (operation) {
            VariableOperation.SET -> when (type) {
                VariableType.BOOLEAN -> "scoreboard players set $holder kc_vars ${if (node.boolean("value")) 1 else 0}"
                VariableType.INTEGER -> "scoreboard players set $holder kc_vars ${node.string("value").toLong()}"
                VariableType.DECIMAL ->
                    "data modify storage kantan:variables $storagePath set value ${node.string("value").toDouble()}d"
                VariableType.TEXT ->
                    "data modify storage kantan:variables $storagePath set value \"${escape(node.string("value"))}\""
                VariableType.POSITION, VariableType.ENTITY -> null
            }
            VariableOperation.ADD -> "scoreboard players add $holder kc_vars ${node.string("value").toLong()}"
            VariableOperation.SUBTRACT -> "scoreboard players remove $holder kc_vars ${node.string("value").toLong()}"
            VariableOperation.CLEAR ->
                if (type in setOf(VariableType.BOOLEAN, VariableType.INTEGER)) {
                    "scoreboard players reset $holder kc_vars"
                } else {
                    "data remove storage kantan:variables $storagePath"
                }
            VariableOperation.TOGGLE ->
                "execute store success score $holder kc_vars run execute unless score $holder kc_vars matches 1"
            VariableOperation.STORE_POSITION, VariableOperation.STORE_TARGET -> null
        }
    }

    private fun variableHolder(name: String, temporary: Boolean) =
        VanillaScoreNames.variableHolder(name, temporary)

    private fun loopName(id: UUID) = "for_${id.toString().replace("-", "").take(12)}"

    private fun assignLoopValue(loop: String, target: String, node: CommandNode, field: String): String {
        val destination = "#${loop}_$target"
        return if (node.string("${field}Source", "FIXED") == "TEMPORARY") {
            "scoreboard players operation $destination kc_vars = ${variableHolder(node.string("${field}Value"), temporary = true)} kc_vars"
        } else {
            "scoreboard players set $destination kc_vars ${node.string("${field}Value", if (field == "step") "1" else "0")}"
        }
    }

    private fun loopCheck(graph: CommandGraph, prefix: String, start: CommandNode): String {
        val loop = loopName(start.id)
        val body = start.trueNext
        val end = start.pairedNodeId
        val after = end?.let(graph.nodes::get)?.next
        val bodyFunction = body?.takeUnless { it == end }?.let { "function kantan:${prefix}_$it" }
        val lines = mutableListOf<String>()
        lines += assignLoopValue(loop, "end", start, "end")
        lines += "scoreboard players set #${loop}_run kc_vars 0"
        if (bodyFunction != null) {
            val positiveComparison = if (start.boolean("inclusiveEnd", true)) "<=" else "<"
            val negativeComparison = if (start.boolean("inclusiveEnd", true)) ">=" else ">"
            lines += "execute if score #${loop}_step kc_vars matches 1.. if score #${loop}_value kc_vars $positiveComparison #${loop}_end kc_vars run scoreboard players set #${loop}_run kc_vars 1"
            lines += "execute if score #${loop}_step kc_vars matches ..-1 if score #${loop}_value kc_vars $negativeComparison #${loop}_end kc_vars run scoreboard players set #${loop}_run kc_vars 1"
            lines += "execute if score #${loop}_run kc_vars matches 1 run return run $bodyFunction"
        }
        after?.let {
            lines += "execute if score #${loop}_run kc_vars matches 0 run return run function kantan:${prefix}_$it"
        } ?: run {
            lines += "execute if score #${loop}_run kc_vars matches 0 run return 1"
        }
        lines += "return 0"
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun enclosingFor(graph: CommandGraph, target: UUID): CommandNode? =
        graph.nodes.values
            .filter { start ->
                start.type == CommandType.FOR_START &&
                    reachableBefore(graph, start.trueNext, start.pairedNodeId, target)
            }
            .minByOrNull { candidate ->
                graph.nodes.values.count { nested ->
                    nested.type == CommandType.FOR_START &&
                        reachableBefore(graph, candidate.trueNext, candidate.pairedNodeId, nested.id)
                }
            }

    private fun reachableBefore(graph: CommandGraph, start: UUID?, stop: UUID?, target: UUID): Boolean {
        val visited = mutableSetOf<UUID>()
        fun visit(id: UUID?): Boolean {
            if (id == null || id == stop || !visited.add(id)) return false
            if (id == target) return true
            val node = graph.nodes[id] ?: return false
            return when (node.type) {
                CommandType.CONDITION -> visit(node.trueNext) || visit(node.falseNext)
                CommandType.FOR_START -> visit(node.trueNext) || visit(node.pairedNodeId)
                else -> visit(node.next)
            }
        }
        return visit(start)
    }

    private fun temporaryNames(graph: CommandGraph): Set<String> = buildSet {
        graph.nodes.values.forEach { node ->
            if (node.type == CommandType.VARIABLE && node.string("scope", "TEMPORARY") != "WORLD") {
                node.string("name").takeIf(String::isNotBlank)?.let(::add)
            }
            if (node.type == CommandType.CONDITION &&
                node.string("kind") == ConditionKind.VARIABLE_STATE.name &&
                node.string("variableScope", "TEMPORARY") != "WORLD"
            ) {
                node.string("variable").takeIf(String::isNotBlank)?.let(::add)
            }
            if (node.type == CommandType.FOR_START) {
                listOf("start", "end", "step").forEach { field ->
                    if (node.string("${field}Source", "FIXED") == "TEMPORARY") {
                        node.string("${field}Value").takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }
            node.snapshot?.let { addAll(temporaryNames(it)) }
        }
    }

    private fun effectiveTarget(node: CommandNode): String =
        selector(requireNotNull(node.targetSpec))

    private fun destination(node: CommandNode): String {
        node.destinationTargetSpec?.let { return selector(it) }
        return when (val spec = node.destinationSpec) {
            null -> error("structured teleport destination is missing")
            else -> when (spec.kind) {
                PositionKind.CAPTURED, PositionKind.COORDINATES ->
                    "${spec.x ?: "~"} ${spec.y ?: "~"} ${spec.z ?: "~"}"
                PositionKind.DISK -> "~ ~ ~"
                PositionKind.EXECUTOR -> "@s"
                PositionKind.TARGET ->
                    selector(node.contextOverride?.target ?: TargetSpec(TargetKind.INHERITED_TARGET))
                PositionKind.MYWORLD_SPAWN,
                PositionKind.TEMPORARY_VARIABLE,
                PositionKind.WORLD_VARIABLE -> error("unsupported structured teleport destination")
            }
        }
    }

    private fun contextFrom(node: CommandNode) = node.contextOverride ?: ExecutionContextSpec()

    private fun conditionContext(node: CommandNode): ExecutionContextSpec? {
        val inherited = node.contextOverride
        val position = node.conditionPositionSpec ?: return inherited
        return (inherited ?: ExecutionContextSpec()).copy(position = position)
    }

    private fun wrapContext(context: ExecutionContextSpec, command: String): String {
        val clauses = buildList {
            (context.target ?: context.executor)?.let { add("as ${selector(it)}") }
            context.position?.let {
                when (it.kind) {
                    PositionKind.COORDINATES, PositionKind.CAPTURED -> add("positioned ${it.x} ${it.y} ${it.z}")
                    PositionKind.EXECUTOR -> add("at @s")
                    PositionKind.TARGET -> context.target?.let { target -> add("at ${selector(target)}") }
                    PositionKind.TEMPORARY_VARIABLE, PositionKind.WORLD_VARIABLE -> Unit
                    else -> Unit
                }
            }
            context.facing?.let { facing ->
                when (facing.kind) {
                    FacingKind.TARGET -> context.target?.let { add("facing entity ${selector(it)} eyes") }
                    FacingKind.COORDINATES -> add("facing ${facing.x} ${facing.y} ${facing.z}")
                    FacingKind.ROTATION, FacingKind.CAPTURED -> add("rotated ${facing.yaw} ${facing.pitch}")
                    else -> Unit
                }
            }
        }
        return if (clauses.isEmpty()) command else "execute ${clauses.joinToString(" ")} run $command"
    }

    private fun storeResult(node: CommandNode, command: String): String =
        "execute store success score ${scoreHolder(node.id)} kc_result run $command"

    private fun storeFunctionResult(node: CommandNode, command: String): String =
        "execute store result score ${scoreHolder(node.id)} kc_result run $command"

    private fun scoreHolder(id: UUID) = "#n_${id.toString().replace("-", "")}"

    private fun selector(spec: TargetSpec): String {
        if (spec.kind in setOf(TargetKind.EXECUTOR, TargetKind.ACTIVATOR, TargetKind.INHERITED_TARGET)) return "@s"
        if (spec.kind == TargetKind.FIXED_ENTITY) return "@e[limit=0]"
        val base = when (spec.kind) {
            TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS, TargetKind.ALL_PLAYERS, TargetKind.RANDOM_PLAYER -> "@a"
            else -> "@e"
        }
        val arguments = buildList {
            spec.entityType?.let { add("type=$it") }
            if (spec.minimumDistance != null || spec.maximumDistance != null) {
                add("distance=${spec.minimumDistance ?: ""}..${spec.maximumDistance ?: ""}")
            } else {
                // A non-empty distance predicate keeps @a selectors in the current dimension.
                add("distance=0..")
            }
            spec.gameMode?.let { add("gamemode=${it.lowercase()}") }
            spec.tag?.let { add("tag=$it") }
            spec.name?.let { add("name=$it") }
            val limit = spec.limit ?: when (spec.kind) {
                TargetKind.NEAREST_PLAYER, TargetKind.RANDOM_PLAYER, TargetKind.NEAREST_ENTITY -> 1
                else -> null
            }
            limit?.let { add("limit=$it") }
            val sort = when {
                spec.kind == TargetKind.RANDOM_PLAYER -> "random"
                spec.sort.name == "FURTHEST" -> "furthest"
                spec.sort.name == "RANDOM" -> "random"
                spec.kind in setOf(TargetKind.NEAREST_PLAYER, TargetKind.NEAREST_ENTITY) -> "nearest"
                else -> null
            }
            sort?.let { add("sort=$it") }
        }
        return if (arguments.isEmpty()) base else "$base[${arguments.joinToString(",")}]"
    }

    private fun appendSelectorArguments(selector: String, argument: String): String =
        if (selector.endsWith("]")) selector.dropLast(1) + ",$argument]"
        else "$selector[$argument]"

    private fun scoreRange(operator: String, value: Long): String = when (operator) {
        "==" -> value.toString()
        "!=" -> value.toString()
        ">" -> "${Math.addExact(value, 1)}.."
        "<" -> "..${Math.subtractExact(value, 1)}"
        "<=" -> "..$value"
        else -> "$value.."
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private companion object {
        const val EXPORT_VARIABLE_TYPE = "_exportVariableType"
        val VANILLA_INTEGER_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    }
}

sealed interface ExportResult {
    data class Success(val directory: File) : ExportResult
    data class Failure(val errors: List<String>) : ExportResult
}

sealed interface StandaloneCompilation {
    data class Success(val functions: Map<String, String>) : StandaloneCompilation
    data class Failure(val errors: List<String>) : StandaloneCompilation
}

internal object VanillaScoreNames {
    fun variableHolder(name: String, temporary: Boolean): String {
        val normalized = name.lowercase().replace(Regex("[^a-z0-9_.-]"), "_")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(it) }
        return "#${if (temporary) "t" else "w"}_${normalized.take(20)}_$digest"
    }
}

internal object VanillaStorageNames {
    fun variablePath(name: String, temporary: Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(name.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "variables.${if (temporary) "temporary" else "world"}.v_$digest"
    }
}
