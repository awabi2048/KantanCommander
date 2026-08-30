package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphLimits
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.DisplayTextTiming
import me.awabi2048.kantancommander.model.DisplayTextTimingPolicy
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.effectiveContextSource
import me.awabi2048.kantancommander.model.hasContextOverride
import me.awabi2048.kantancommander.model.supportsContextOverride
import me.awabi2048.kantancommander.model.TICKS_PER_SECOND
import me.awabi2048.kantancommander.execution.ExecutionSemantics
import me.awabi2048.kantancommander.item.ItemStackCodec
import java.io.File
import java.math.BigInteger
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.security.MessageDigest

class VanillaDatapackExporter(
    private val scripts: ScriptStore,
    private val outputRoot: File,
    private val maximumCommandCount: Int = 1024,
    private val maximumDiskCallDepth: Int = 3,
    private val graphLimits: GraphLimits = GraphLimits(),
) {
    fun compileForStandalone(
        root: DiskScript,
        worldVariableTypes: Map<String, VariableType> = emptyMap(),
        entryFunctionName: String = root.id.toString(),
    ): StandaloneCompilation {
        val exportRoot = root.copy(graph = root.graph.deepCopy())
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        errors += ExecutableScriptValidator.validate(exportRoot, graphLimits)
        collectWarnings(exportRoot.graph, "root", warnings)
        annotateVariableTypes(exportRoot.graph, worldVariableTypes, errors)
        validate(exportRoot, errors, Collections.newSetFromMap(IdentityHashMap()), 0)
        return if (errors.isEmpty()) {
            StandaloneCompilation.Success(compile(exportRoot, entryFunctionName), entryFunctionName, warnings.distinct())
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
        return ExportResult.Success(pack, compilation.warnings)
    }

    private fun validate(
        script: DiskScript,
        errors: MutableList<String>,
        visited: MutableSet<CommandGraph>,
        callDepth: Int,
    ) {
        if (!visited.add(script.graph)) {
            errors += "${script.id}: 別ディスクのコピー内容が循環参照しています"
            return
        }
        me.awabi2048.kantancommander.data.GraphValidator.validate(script.graph, graphLimits).forEach {
            errors += "${script.id}: $it"
        }
        script.graph.nodes.values.forEach { node ->
            when (node.type) {
                CommandType.GIVE_ITEM -> {
                    val item = node.string("item")
                    if (!item.startsWith("minecraft:") || CommandValueRules.material(item, allowAir = false) == null) {
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
                CommandType.BLOCK_OPERATION -> {
                    val block = CommandValueRules.placementMaterial(node.string("block"))
                    if (!node.string("block").startsWith("minecraft:") || block == null) {
                        errors += "${script.id}/${node.id}: 完全バニラ出力できない配置ブロックです"
                    }
                    when (BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))) {
                        BlockOperationMode.SETBLOCK -> validatePosition(script, node, node.blockPositionSpec, errors)
                        BlockOperationMode.FILL -> {
                            validatePosition(script, node, node.blockFromSpec, errors)
                            validatePosition(script, node, node.blockToSpec, errors)
                        }
                        null -> errors += "${script.id}/${node.id}: 不明なブロック操作方式です"
                    }
                }
                CommandType.ENTITY_DELETE -> Unit
                CommandType.TELEPORT -> if (node.string("world").isNotBlank()) {
                    errors += "${script.id}/${node.id}: 出力先ワールドを検証できない固定ワールド参照です"
                } else {
                    validatePosition(script, node, node.destinationSpec, errors)
                }
                CommandType.DISK_CALL -> when {
                    node.snapshot == null ->
                        errors += "${script.id}/${node.id}: コピー内容がありません"
                    callDepth >= maximumDiskCallDepth ->
                        errors += "${script.id}/${node.id}: 別ディスク呼出深度が上限 $maximumDiskCallDepth を超えます"
                    else ->
                        validate(script.copy(graph = node.snapshot!!), errors, visited, callDepth + 1)
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
                            type == VariableType.POSITION &&
                            operation in setOf(VariableOperation.STORE_POSITION, VariableOperation.CLEAR) ||
                            type == VariableType.ENTITY &&
                            operation in setOf(VariableOperation.STORE_TARGET, VariableOperation.CLEAR)
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
                    if (type == VariableType.INTEGER &&
                        operation in setOf(VariableOperation.ADD, VariableOperation.SUBTRACT) &&
                        node.string("value").toLongOrNull() == Int.MIN_VALUE.toLong()
                    ) {
                        // Int最小値は32bit定数へ反転できないため、加減算どちらでもバニラ命令が範囲外になる。
                        errors += "${script.id}/${node.id}: Int最小値の加減算はバニラscoreboard命令へ安全に変換できません"
                    }
                    if (operation == VariableOperation.STORE_TARGET && node.targetSpec == null) {
                        errors += "${script.id}/${node.id}: 対象を保存するための対象指定が未設定です"
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
                        when (node.string("${field}Source", "FIXED")) {
                            "FIXED" -> {
                                val value = node.string("${field}Value").toLongOrNull()
                                if (value == null) {
                                    errors += "${script.id}/${node.id}: forの${field}値は64bit符号付き整数で指定してください"
                                } else if (value !in VANILLA_INTEGER_RANGE) {
                                    errors += "${script.id}/${node.id}: forの${field}値はバニラscoreboardの範囲外です"
                                }
                            }
                            "TEMPORARY", "WORLD" -> Unit
                            else -> errors += "${script.id}/${node.id}: forの${field}参照元が不正です"
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
            if (resolveExportContext(script.graph, node).second) {
                errors += "${script.id}/${node.id}: 直前コンテキスト(PREVIOUS)の継承内容が経路ごとに確定しないため、完全バニラ出力できません"
            }
            val hasContextState = node.hasContextOverride() || node.effectiveContextSource != ContextSource.BASE
            if (hasContextState && node.type != CommandType.CONTEXT && !node.type.supportsContextOverride()) {
                errors += "${script.id}/${node.id}: ${node.type} では実行コンテキストを設定できません"
            } else {
                node.contextOverride?.let { validateContext(script, node, it, errors) }
            }
            validatePosition(script, node, node.conditionPositionSpec, errors)
            if (listOfNotNull(node.targetSpec, node.secondaryTargetSpec, node.destinationTargetSpec)
                    .any { it.kind == TargetKind.FIXED_ENTITY }
            ) {
                errors += "${script.id}/${node.id}: 固定エンティティ参照は完全バニラ出力できません"
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
        // 整数変数の大小比較はバニラscoreboardのmatches範囲へ展開されるため、
        // 比較値が32bit範囲外・非数値のまま出力すると常にfalseまたはコマンドエラーになる。
        val comparisonType = node.params[EXPORT_VARIABLE_TYPE]?.let {
            runCatching { VariableType.valueOf(it) }.getOrNull()
        }
        if (kind == ConditionKind.VARIABLE_STATE &&
            comparisonType == VariableType.INTEGER &&
            node.string("operator") !in setOf("set", "unset")
        ) {
            val parsed = node.string("value").toLongOrNull()
            if (parsed == null || parsed !in VANILLA_INTEGER_RANGE) {
                errors += "${script.id}/${node.id}: 整数比較値はバニラscoreboardの32bit整数範囲で指定してください: ${node.string("value")}"
            }
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

    private fun compile(script: DiskScript, entryFunctionName: String): Map<String, String> =
        linkedMapOf<String, String>().also {
            compileGraph(
                script.graph,
                scriptPrefix(entryFunctionName, script.graph),
                it,
                resetBudget = true,
                rootFunctionName = entryFunctionName,
            )
        }

    private fun compileGraph(
        graph: CommandGraph,
        prefix: String,
        output: MutableMap<String, String>,
        resetBudget: Boolean = false,
        rootFunctionName: String = prefix,
    ) {
        val entryCall = graph.entryNodeId?.let { "return run function kantan:${nodeFunction(prefix, it)}\n" } ?: "return 1\n"
        defineFunction(output, rootFunctionName, if (resetBudget) {
            buildString {
                appendLine("scoreboard players set #executed kc_runtime 0")
                temporaryNames(graph).forEach {
                    appendLine("scoreboard players reset ${variableHolder(it, temporary = true)} kc_vars")
                    appendLine("data remove storage kantan:variables ${VanillaStorageNames.variablePath(it, temporary = true)}")
                }
                append(entryCall)
            }
        } else entryCall)
        graph.nodes.values.forEach { node ->
            val lines = mutableListOf<String>()
            val nodeExportContext = exportContext(graph, node)
            if (node.type == CommandType.VARIABLE &&
                node.string("operation") == VariableOperation.STORE_POSITION.name
            ) {
                val helper = nodeFunction(prefix, node.id, "capture_position")
                node.params[EXPORT_CAPTURE_FUNCTION] = helper
                val temporary = node.string("scope", "TEMPORARY") != "WORLD"
                val path = VanillaStorageNames.variablePath(node.string("name"), temporary)
                defineFunction(output, helper, buildString {
                    appendLine("data modify storage kantan:variables $path set value {}")
                    appendLine("data modify storage kantan:variables $path.position set from entity @s Pos")
                    appendLine("data modify storage kantan:variables $path.rotation set from entity @s Rotation")
                    appendLine("kill @s")
                })
            }
            val emptyFor = node.type == CommandType.FOR_START && node.trueNext == node.pairedNodeId
            if (!emptyFor) {
                lines += "execute if score #executed kc_runtime matches ${maximumCommandCount.coerceAtLeast(1)}.. run return 0"
                lines += "scoreboard players add #executed kc_runtime 1"
            }
            when {
                emptyFor -> node.pairedNodeId?.let(graph.nodes::get)?.next?.let {
                    lines += "return run function kantan:${nodeFunction(prefix, it)}"
                } ?: run { lines += "return 1" }
                node.type == CommandType.FOR_START -> {
                    val loop = loopName(node.id)
                    lines += assignLoopValue(loop, "value", node, "start")
                    lines += assignLoopValue(loop, "end", node, "end")
                    lines += assignLoopValue(loop, "step", node, "step")
                    lines += "scoreboard players set #${loop}_count kc_vars 1"
                    lines += "return run function kantan:${nodeFunction(prefix, node.id, "check")}"
                    defineFunction(
                        output,
                        nodeFunction(prefix, node.id, "check"),
                        loopCheck(graph, prefix, node),
                    )
                }
                node.type == CommandType.FOR_END -> {
                    val start = node.pairedNodeId?.let(graph.nodes::get)
                    if (start != null) {
                        val loop = loopName(start.id)
                        lines += assignLoopValue(loop, "step", start, "step")
                        lines += guardedScoreOperation(
                            target = "#${loop}_value",
                            source = "#${loop}_step",
                        )
                        lines += "scoreboard players add #${loop}_count kc_vars 1"
                        lines += "return run function kantan:${nodeFunction(prefix, start.id, "check")}"
                    }
                }
                node.type == CommandType.BREAK -> {
                    enclosingFor(graph, node.id)?.pairedNodeId?.let(graph.nodes::get)?.next?.let {
                        lines += "return run function kantan:${nodeFunction(prefix, it)}"
                    } ?: run { lines += "return 1" }
                }
                node.type == CommandType.CONTINUE -> {
                    enclosingFor(graph, node.id)?.pairedNodeId?.let {
                        lines += "return run function kantan:${nodeFunction(prefix, it)}"
                    } ?: run { lines += "return 1" }
                }
                node.type == CommandType.CAMERA_SHAKE -> {
                    // Java版には命令本体を出せないため、成功だけ記録して後続関数を維持します。
                    lines += "scoreboard players set ${scoreHolder(node.id)} kc_result 1"
                }
                node.type == CommandType.DISK_CALL -> {
                    val snapshotPrefix = snapshotPrefix(prefix, node.id)
                    node.snapshot?.let { compileGraph(it, snapshotPrefix, output, resetBudget = false) }
                    val call = "function kantan:$snapshotPrefix"
                    lines += storeFunctionResult(node, nodeExportContext?.let { wrapContext(it, call) } ?: call)
                }
                node.type == CommandType.CONTEXT -> {
                    node.next?.let {
                        lines += "return run ${wrapContext(contextFrom(node), "function kantan:${nodeFunction(prefix, it)}")}"
                    } ?: run { lines += "return 1" }
                }
                node.type == CommandType.VARIABLE &&
                    node.string("operation") in setOf(
                        VariableOperation.ADD.name,
                        VariableOperation.SUBTRACT.name,
                    ) -> {
                    val helper = nodeFunction(prefix, node.id, "arithmetic")
                    defineFunction(
                        output,
                        helper,
                        lowerArithmeticVariable(node, graph).joinToString("\n", postfix = "\n"),
                    )
                    val command = "function kantan:$helper"
                    lines += storeFunctionResult(
                        node,
                        nodeExportContext?.let { wrapContext(it, command) } ?: command,
                    )
                }
                else -> lower(node, graph)?.let { command ->
                    if (node.type == CommandType.DISPLAY_TEXT && DisplayTextTimingPolicy.supports(node)) {
                        val timing = DisplayTextTiming.from(node)
                        val times = "title ${effectiveTarget(node)} times " +
                            "${timing.fadeInTicks} " +
                            "${timing.stayTicks} " +
                            "${timing.fadeOutTicks}"
                        lines += nodeExportContext?.let { wrapContext(it, times) } ?: times
                    }
                    val contextual = nodeExportContext?.let { wrapContext(it, command) } ?: command
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
                        "execute $trueCheck $predicate run return run function kantan:${nodeFunction(prefix, it)}"
                    } ?: "execute $trueCheck $predicate run return 1"
                    val falseBranch = node.falseNext?.let {
                        "execute $falseCheck $predicate run return run function kantan:${nodeFunction(prefix, it)}"
                    } ?: "execute $falseCheck $predicate run return 1"
                    lines += conditionContext?.let { context -> wrapContext(context, trueBranch) } ?: trueBranch
                    lines += conditionContext?.let { context -> wrapContext(context, falseBranch) } ?: falseBranch
                    lines += "return 0"
                }
                CommandType.WAIT ->
                    node.next?.let {
                        lines += "schedule function kantan:${nodeFunction(prefix, it)} ${node.int("seconds", 1).coerceAtLeast(1).toLong() * TICKS_PER_SECOND}t replace"
                    }
                CommandType.CONTEXT -> Unit
                CommandType.FOR_START, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> Unit
                CommandType.MERGE -> node.next?.let {
                    lines += "return run function kantan:${nodeFunction(prefix, it)}"
                } ?: run { lines += "return 1" }
                else -> {
                    val result = scoreHolder(node.id)
                    node.next?.let {
                        lines += "execute if score $result kc_result matches 1 run return run function kantan:${nodeFunction(prefix, it)}"
                    } ?: run {
                        lines += "execute if score $result kc_result matches 1 run return 1"
                    }
                    lines += "return 0"
                }
            }
            defineFunction(output, nodeFunction(prefix, node.id), lines.joinToString("\n", postfix = "\n"))
        }
    }

    private fun lower(node: CommandNode, graph: CommandGraph): String? = when (node.type) {
        CommandType.TELEPORT -> "tp ${effectiveTarget(node)} ${destination(node)}"
        CommandType.GIVE_ITEM -> "give ${effectiveTarget(node)} ${node.string("item")} ${node.int("count", 1)}"
        CommandType.ENTITY_ACTION ->
            if (node.string("action") == "dismount") "ride ${effectiveTarget(node)} dismount"
            else "ride ${effectiveTarget(node)} mount ${singleSelector(requireNotNull(node.secondaryTargetSpec))}"
        CommandType.DISPLAY_TEXT -> when (node.string("mode", "tellraw")) {
            "title" -> "title ${effectiveTarget(node)} title {\"text\":\"${escape(node.string("text"))}\"}"
            "actionbar" -> "title ${effectiveTarget(node)} actionbar {\"text\":\"${escape(node.string("text"))}\"}"
            else -> "tellraw ${effectiveTarget(node)} {\"text\":\"${escape(node.string("text"))}\"}"
        }
        CommandType.SUMMON_ENTITY -> {
            val tags = node.string("tags").split(',').map(String::trim).filter(String::isNotEmpty)
            val nbt = if (tags.isEmpty()) "" else " {Tags:[${tags.joinToString(",") { "\\\"${escape(it)}\\\"" }}]}"
            "summon ${node.string("entity")} ~ ~ ~$nbt"
        }
        CommandType.PLAY_SOUND ->
            "execute as @a at @s run playsound ${node.string("sound")} master @s ~ ~ ~ " +
                "${node.double("volume", 1.0)} ${node.double("pitch", 1.0)}"
        CommandType.APPLY_EFFECT ->
            "effect give ${effectiveTarget(node)} ${node.string("effect")} ${node.int("seconds", 30)} ${node.int("level", 1) - 1}"
        CommandType.CAMERA_SHAKE -> null
        CommandType.EQUIP_ITEM ->
            "item replace entity ${effectiveTarget(node)} ${equipmentSlot(node.string("slot"))} with ${node.string("item")}"
        CommandType.BLOCK_OPERATION -> blockOperationCommand(node)
        CommandType.ENTITY_DELETE -> "kill ${effectiveTarget(node)}"
        CommandType.DISK_CALL -> null
        CommandType.VARIABLE -> lowerVariable(node, graph)
        CommandType.WAIT, CommandType.CONTEXT, CommandType.CONDITION, CommandType.MERGE,
        CommandType.FOR_START, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> null
    }

    private fun equipmentSlot(slot: String) = when (slot) {
        "OFF_HAND" -> "weapon.offhand"
        "HEAD" -> "armor.head"
        "CHEST" -> "armor.chest"
        "LEGS" -> "armor.legs"
        "FEET" -> "armor.feet"
        else -> "weapon.mainhand"
    }

    /**
     * PREVIOUSは、直前に実行したノードの有効コンテキストへ静的に展開できる場合だけ出力できます。
     * 2つ目の戻り値は「先行経路ごとに継承内容が確定せず静的展開できない」ことを示します。
     * MERGEやfor終了など直前実行情報を更新しない経由ノードは、さらに手前の実行ノードへ遡って解決します。
     */
    private fun resolveExportContext(
        graph: CommandGraph,
        node: CommandNode,
    ): Pair<ExecutionContextSpec?, Boolean> {
        // VARIABLEはCONTEXTコマンド／現在の実行文脈をそのまま受け取り、ノード自身の
        // contextOverrideやPREVIOUS指定を解釈しません。検証で旧状態は拒否しますが、
        // 防御的にもエクスポート経路へ混入させないようここで遮断します。
        if (node.type != CommandType.CONTEXT && !node.type.supportsContextOverride()) return null to false
        if (node.effectiveContextSource != ContextSource.PREVIOUS) {
            return ExecutionSemantics.mergeContexts(null, node.contextOverride) to false
        }
        val directPredecessors = graphPredecessors(graph, node)
        val candidates = if (directPredecessors.isEmpty()) setOf<ExecutionContextSpec?>(null)
        else previousContextCandidates(graph, directPredecessors)
        if (candidates.size >= 2) return null to true
        return ExecutionSemantics.mergeContexts(candidates.singleOrNull(), node.contextOverride) to false
    }

    /** 直前実行情報（PREVIOUS継承元）を更新しないため、解決をさらに手前へ透過させるノード種別。 */
    private fun passesThroughPreviousContext(node: CommandNode): Boolean = when (node.type) {
        CommandType.WAIT, CommandType.MERGE, CommandType.FOR_START,
        CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE,
        CommandType.DISK_CALL -> true
        else -> false
    }

    private fun graphPredecessors(graph: CommandGraph, node: CommandNode): List<CommandNode> =
        graph.nodes.values.filter { node.id in listOfNotNull(it.next, it.trueNext, it.falseNext) }

    /** ノード自身の有効コンテキスト。PREVIOUS指定時は先行ノードの直前実行値を単一候補として解決します。 */
    private fun ownExportContext(graph: CommandGraph, current: CommandNode): ExecutionContextSpec? {
        if (current.type != CommandType.CONTEXT && !current.type.supportsContextOverride()) return null
        if (current.effectiveContextSource != ContextSource.PREVIOUS) {
            return ExecutionSemantics.mergeContexts(null, current.contextOverride)
        }
        val predecessors = graphPredecessors(graph, current)
        val inherited = previousContextCandidates(graph, predecessors).singleOrNull()
        return ExecutionSemantics.mergeContexts(inherited, current.contextOverride)
    }

    /** 先行ノード群それぞれを「直前に実行した」ときのpreviousContext候補をすべて収集します。 */
    private fun previousContextCandidates(
        graph: CommandGraph,
        starts: List<CommandNode>,
    ): Set<ExecutionContextSpec?> {
        val candidates = linkedSetOf<ExecutionContextSpec?>()
        fun visit(current: CommandNode, visited: MutableSet<UUID>) {
            if (!visited.add(current.id)) return
            if (passesThroughPreviousContext(current)) {
                val predecessors = graphPredecessors(graph, current)
                if (predecessors.isEmpty()) {
                    candidates += null
                    return
                }
                predecessors.forEach { visit(it, mutableSetOf()) }
                return
            }
            candidates += ownExportContext(graph, current)
        }
        starts.forEach { visit(it, mutableSetOf()) }
        return candidates
    }

    private fun exportContext(graph: CommandGraph, node: CommandNode): ExecutionContextSpec? =
        resolveExportContext(graph, node).first

    private fun collectWarnings(graph: CommandGraph, path: String, warnings: MutableList<String>) {
        graph.nodes.values.forEach { node ->
            if (node.type == CommandType.CAMERA_SHAKE) {
                warnings += "$path/${node.id}: カメラ揺れはJava版データパックから省略されました"
            }
            node.snapshot?.let { collectWarnings(it, "$path/${node.id}/snapshot", warnings) }
        }
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
            "block ~ ~ ~ ${node.string("block", "minecraft:air")}"
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
        val spec = node.targetSpec ?: node.contextOverride?.target ?: TargetSpec(TargetKind.INHERITED_TARGET)
        return selector(if (spec.kind == TargetKind.INHERITED_TARGET) spec else spec.copy(limit = 1))
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
            val operator = if (operation == VariableOperation.SET) "=" else return null
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
            VariableOperation.ADD, VariableOperation.SUBTRACT -> null
            VariableOperation.CLEAR ->
                if (type in setOf(VariableType.BOOLEAN, VariableType.INTEGER)) {
                    "scoreboard players reset $holder kc_vars"
                } else {
                    "data remove storage kantan:variables $storagePath"
                }
            VariableOperation.TOGGLE ->
                "execute store success score $holder kc_vars run execute unless score $holder kc_vars matches 1"
            VariableOperation.STORE_POSITION ->
                "execute summon minecraft:marker run function kantan:${node.string(EXPORT_CAPTURE_FUNCTION)}"
            VariableOperation.STORE_TARGET ->
                "execute as ${selector(requireNotNull(node.targetSpec))} run data modify storage kantan:variables " +
                    "$storagePath set from entity @s UUID"
        }
    }

    private fun lowerArithmeticVariable(node: CommandNode, graph: CommandGraph): List<String> {
        val holder = variableHolder(
            node.string("name"),
            temporary = node.string("scope", "TEMPORARY") != "WORLD",
        )
        val subtract = node.string("operation") == VariableOperation.SUBTRACT.name
        val special = node.string("value").takeIf {
            it in setOf("\$current_iteration_value", "\$current_loop_count")
        }
        if (special != null) {
            val loop = requireNotNull(enclosingFor(graph, node.id))
            val source = "#${loopName(loop.id)}_${if (special == "\$current_loop_count") "count" else "value"}"
            return guardedScoreOperation(holder, source, subtract)
        }
        val raw = node.string("value").toLong()
        val delta = if (subtract) Math.negateExact(raw) else raw
        return guardedScoreConstant(holder, delta)
    }

    private fun guardedScoreConstant(target: String, delta: Long): List<String> {
        if (delta == 0L) return listOf("return 1")
        val guard = if (delta > 0) {
            val firstOverflowing = Int.MAX_VALUE.toLong() - delta + 1
            "execute if score $target kc_vars matches $firstOverflowing.. run return 0"
        } else {
            val lastOverflowing = Int.MIN_VALUE.toLong() - delta - 1
            "execute if score $target kc_vars matches ..$lastOverflowing run return 0"
        }
        val operation = if (delta > 0) {
            "scoreboard players add $target kc_vars $delta"
        } else {
            "scoreboard players remove $target kc_vars ${-delta}"
        }
        return listOf(guard, operation, "return 1")
    }

    private fun guardedScoreOperation(
        target: String,
        source: String,
        subtract: Boolean = false,
    ): List<String> =
        buildList {
            if (subtract) {
                add("scoreboard players set #kc_limit kc_runtime ${Int.MIN_VALUE}")
                add("scoreboard players operation #kc_limit kc_runtime += $source kc_vars")
                add("execute if score $source kc_vars matches 1.. if score $target kc_vars < #kc_limit kc_runtime run return 0")
                add("scoreboard players set #kc_limit kc_runtime ${Int.MAX_VALUE}")
                add("scoreboard players operation #kc_limit kc_runtime += $source kc_vars")
                add("execute if score $source kc_vars matches ..-1 if score $target kc_vars > #kc_limit kc_runtime run return 0")
                add("scoreboard players operation $target kc_vars -= $source kc_vars")
            } else {
                add("scoreboard players set #kc_limit kc_runtime ${Int.MAX_VALUE}")
                add("scoreboard players operation #kc_limit kc_runtime -= $source kc_vars")
                add("execute if score $source kc_vars matches 1.. if score $target kc_vars > #kc_limit kc_runtime run return 0")
                add("scoreboard players set #kc_limit kc_runtime ${Int.MIN_VALUE}")
                add("scoreboard players operation #kc_limit kc_runtime -= $source kc_vars")
                add("execute if score $source kc_vars matches ..-1 if score $target kc_vars < #kc_limit kc_runtime run return 0")
                add("scoreboard players operation $target kc_vars += $source kc_vars")
            }
            add("return 1")
        }

    private fun variableHolder(name: String, temporary: Boolean) =
        VanillaScoreNames.variableHolder(name, temporary)

    private fun loopName(id: UUID) = "for_${id.toString().replace("-", "").take(12)}"

    private fun assignLoopValue(loop: String, target: String, node: CommandNode, field: String): String {
        val destination = "#${loop}_$target"
        return when (node.string("${field}Source", "FIXED")) {
            "TEMPORARY" ->
                "scoreboard players operation $destination kc_vars = ${variableHolder(node.string("${field}Value"), temporary = true)} kc_vars"
            // ワールド内変数は永続scoreboardへ初期化済みのため、一時変数と同じoperation転記で読める。
            "WORLD" ->
                "scoreboard players operation $destination kc_vars = ${variableHolder(node.string("${field}Value"), temporary = false)} kc_vars"
            else ->
                "scoreboard players set $destination kc_vars ${node.string("${field}Value", if (field == "step") "1" else "0")}"
        }
    }

    private fun loopCheck(graph: CommandGraph, prefix: String, start: CommandNode): String {
        val loop = loopName(start.id)
        val body = start.trueNext
        val end = start.pairedNodeId
        val after = end?.let(graph.nodes::get)?.next
        val bodyFunction = body?.takeUnless { it == end }?.let { "function kantan:${nodeFunction(prefix, it)}" }
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
            lines += "execute if score #${loop}_run kc_vars matches 0 run return run function kantan:${nodeFunction(prefix, it)}"
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
        node.destinationTargetSpec?.let { return singleSelector(it) }
        return when (val spec = node.destinationSpec) {
            null -> error("structured teleport destination is missing")
            else -> when (spec.kind) {
                PositionKind.CAPTURED, PositionKind.COORDINATES ->
                    "${spec.x ?: "~"} ${spec.y ?: "~"} ${spec.z ?: "~"}"
                PositionKind.DISK -> "~ ~ ~"
                PositionKind.EXECUTOR -> "@s"
                PositionKind.TARGET ->
                    // tpの移動先は単一エンティティでなければならないため、limit=1へ固定する。
                    singleSelector(node.contextOverride?.target ?: TargetSpec(TargetKind.INHERITED_TARGET))
                PositionKind.MYWORLD_SPAWN,
                PositionKind.TEMPORARY_VARIABLE,
                PositionKind.WORLD_VARIABLE -> error("unsupported structured teleport destination")
            }
        }
    }

    /** ブロック操作固有の位置指定を、座標または実行位置へ静的に展開します。 */
    private fun blockOperationCommand(node: CommandNode): String {
        val block = node.string("block")
        return when (BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))) {
            BlockOperationMode.SETBLOCK -> {
                val position = requireNotNull(node.blockPositionSpec)
                val anchor = blockAnchor(node, position)
                "${anchor.prefix}setblock ${anchor.coordinates} $block"
            }
            BlockOperationMode.FILL -> {
                val from = blockAnchor(node, requireNotNull(node.blockFromSpec))
                val to = blockAnchor(node, requireNotNull(node.blockToSpec))
                require(from.prefix == to.prefix) {
                    "fillの始点と終点は同じ基準位置で指定してください"
                }
                "${from.prefix}fill ${from.coordinates} ${to.coordinates} $block"
            }
            null -> error("unknown block operation")
        }
    }

    private data class BlockAnchor(val prefix: String, val coordinates: String)

    private fun blockAnchor(node: CommandNode, spec: me.awabi2048.kantancommander.model.PositionSpec): BlockAnchor = when (spec.kind) {
        PositionKind.CAPTURED, PositionKind.COORDINATES -> BlockAnchor(
            "",
            "${spec.x ?: error("block x is missing")} ${spec.y ?: error("block y is missing")} ${spec.z ?: error("block z is missing")}",
        )
        PositionKind.DISK -> BlockAnchor("", "~ ~ ~")
        PositionKind.EXECUTOR -> BlockAnchor("execute at @s run ", "~ ~ ~")
        PositionKind.TARGET -> BlockAnchor(
            "execute at ${singleSelector(node.contextOverride?.target ?: node.targetSpec ?: error("block target is missing"))} run ",
            "~ ~ ~",
        )
        PositionKind.MYWORLD_SPAWN,
        PositionKind.TEMPORARY_VARIABLE,
        PositionKind.WORLD_VARIABLE -> error("unsupported structured block position")
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

    /**
     * Minecraftの関数名はパス要素ごとに64文字までなので、UUIDをそのまま連結しない。
     * ノードUUIDはbase36化して情報を削らず、入口名とグラフ内容から作る96bit接頭辞で
     * 配置単位・ネストしたグラフ単位の名前空間を分離する。
     */
    private fun scriptPrefix(entryFunctionName: String, graph: CommandGraph): String =
        "s_${shortDigest("root/$entryFunctionName/${graphFingerprint(graph)}", 24)}"

    private fun nodeFunction(prefix: String, nodeId: UUID, suffix: String? = null): String = buildString {
        append(prefix)
        append("_n_")
        append(uuidToken(nodeId))
        suffix?.takeIf(String::isNotEmpty)?.let {
            append('_')
            append(
                when (it) {
                    "capture_position" -> "capture"
                    "arithmetic" -> "arith"
                    else -> it
                }
            )
        }
    }

    private fun uuidToken(id: UUID): String =
        BigInteger(id.toString().replace("-", ""), 16).toString(36)

    private fun snapshotPrefix(parentPrefix: String, nodeId: UUID): String =
        "s_${shortDigest("snapshot/$parentPrefix/$nodeId", 24)}"

    private fun graphFingerprint(graph: CommandGraph): String = buildString {
        append("entry=").append(graph.entryNodeId)
        graph.nodes.entries.sortedBy { it.key.toString() }.forEach { (id, node) ->
            append("|node=").append(id)
                .append(':').append(node.type.name)
                .append(":params=").append(node.params.toSortedMap())
                .append(":next=").append(node.next)
                .append(":true=").append(node.trueNext)
                .append(":false=").append(node.falseNext)
                .append(":pair=").append(node.pairedNodeId)
                .append(":target=").append(node.targetSpec)
                .append(":secondary=").append(node.secondaryTargetSpec)
                .append(":destination=").append(node.destinationSpec)
                .append(":destinationTarget=").append(node.destinationTargetSpec)
                .append(":conditionPosition=").append(node.conditionPositionSpec)
                .append(":context=").append(node.contextOverride)
            node.snapshot?.let {
                append(":snapshot={").append(graphFingerprint(it)).append('}')
            }
        }
    }

    private fun shortDigest(value: String, length: Int): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(length)

    private fun defineFunction(output: MutableMap<String, String>, name: String, content: String) {
        val previous = output.putIfAbsent(name, content)
        require(previous == null || previous == content) {
            "同じバニラ関数名へ異なる内容を割り当てました: $name"
        }
    }

    private fun selector(spec: TargetSpec): String {
        if (spec.kind == TargetKind.INHERITED_TARGET) return "@s"
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

    private fun singleSelector(spec: TargetSpec): String =
        if (spec.kind == TargetKind.INHERITED_TARGET) {
            selector(spec)
        } else {
            selector(spec.copy(limit = 1))
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
        const val EXPORT_CAPTURE_FUNCTION = "_exportCaptureFunction"
        val VANILLA_INTEGER_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    }
}

sealed interface ExportResult {
    data class Success(val directory: File, val warnings: List<String> = emptyList()) : ExportResult
    data class Failure(val errors: List<String>) : ExportResult
}

sealed interface StandaloneCompilation {
    data class Success(
        val functions: Map<String, String>,
        val entryFunctionName: String = functions.keys.first(),
        val warnings: List<String> = emptyList(),
    ) : StandaloneCompilation
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
