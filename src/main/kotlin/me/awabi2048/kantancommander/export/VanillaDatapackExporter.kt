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
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.WorldVariableValue
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.SystemVariableNames
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
        val variableDefinitions = worldVariableTypes.mapValues { (_, type) ->
            if (type == VariableType.NUMBER) WorldVariableValue(type, numberValue = 0.0)
            else WorldVariableValue(type, stringValue = "")
        }
        errors += ExecutableScriptValidator.validate(exportRoot, graphLimits, variableDefinitions).map { it.rendered() }
        collectWarnings(exportRoot.graph, "root", warnings)
        annotateVariableTypes(exportRoot.graph, worldVariableTypes, errors)
        validate(exportRoot, errors, Collections.newSetFromMap(IdentityHashMap()), 0, worldVariableTypes)
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
        worldVariableTypes: Map<String, VariableType>,
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
                CommandType.ENTITY_ACTION -> when (node.string("action")) {
                    "ride", "dismount" -> Unit
                    "equip" -> {
                        val item = node.string("item")
                        if (!item.startsWith("minecraft:") || CommandValueRules.material(item, allowAir = false) == null) {
                            errors += "${script.id}/${node.id}: バニラに存在しない装備アイテムです: $item"
                        }
                        val itemData = node.string("itemData")
                        if (itemData.isNotBlank() && ItemStackCodec.decode(itemData)?.hasItemMeta() != false) {
                            errors += "${script.id}/${node.id}: 保存された装備ItemStackメタデータは完全バニラ出力に未対応です"
                        }
                    }
                    "tag" -> Unit
                    else -> errors += "${script.id}/${node.id}: プラグイン固有のエンティティ操作です"
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
                        null -> errors += "${script.id}/${node.id}: 不明な配置方式です"
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
                        validate(script.copy(graph = node.snapshot!!), errors, visited, callDepth + 1, worldVariableTypes)
                }
                CommandType.CONDITION -> validateCondition(script, node, errors, worldVariableTypes)
                CommandType.CONTEXT -> validateContext(script, node, contextFrom(node), errors)
                CommandType.VARIABLE -> {
                    if (node.string("name").isBlank()) errors += "${script.id}/${node.id}: variable name is missing"
                    val operation = runCatching {
                        VariableOperation.valueOf(node.string("operation", VariableOperation.DEFINE.name))
                    }.getOrNull()
                    val type = runCatching {
                        val rawType = if (operation == VariableOperation.CHANGE) {
                            node.params[EXPORT_VARIABLE_TYPE] ?: node.string("type")
                        } else {
                            node.string("type")
                        }
                        VariableType.valueOf(rawType)
                    }.getOrNull()
                    if (type == null || operation == null) errors += "${script.id}/${node.id}: 変数型または操作が不正です"
                    if (operation == VariableOperation.CHANGE &&
                        node.string("changeMode", VariableChangeMode.ASSIGN.name) == VariableChangeMode.CALCULATE.name &&
                        type != VariableType.NUMBER
                    ) errors += "${script.id}/${node.id}: 文字列変数へ計算式を適用できません"
                }
                CommandType.FOR_START -> {
                    val raw = node.string("count", "1")
                    val value = raw.toDoubleOrNull()?.let(::exactLong)
                    val reference = VariableTemplate.references(raw).singleOrNull()
                    if (value == null && reference == null) {
                        errors += "${script.id}/${node.id}: forの回数は正の整数または数値変数参照で指定してください"
                    } else if (value != null && value !in 1L..Int.MAX_VALUE.toLong()) {
                        errors += "${script.id}/${node.id}: forの回数は1以上のInt範囲内で指定してください"
                    } else if (reference != null &&
                        !SystemVariableNames.isSystemName(reference) &&
                        worldVariableTypes[reference] != VariableType.NUMBER
                    ) {
                        errors += "${script.id}/${node.id}: forの回数参照変数は数値型である必要があります: $reference"
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
            validateFacing(script, node, node.destinationFacingSpec, errors)
            if (listOfNotNull(node.targetSpec, node.secondaryTargetSpec, node.destinationTargetSpec)
                    .any { it.kind == TargetKind.FIXED_ENTITY }
            ) {
                errors += "${script.id}/${node.id}: 固定エンティティ参照は完全バニラ出力できません"
            }
        }
        visited.remove(script.graph)
    }

    private fun validateCondition(
        script: DiskScript,
        node: CommandNode,
        errors: MutableList<String>,
        worldVariableTypes: Map<String, VariableType>,
    ) {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrNull()
        if (kind == null) {
            errors += "${script.id}/${node.id}: 不明な条件の種類です"
            return
        }
        when (kind) {
            ConditionKind.VARIABLE_STATE -> {
                val variable = node.string("variable")
                if (variable.isBlank()) {
                    errors += "${script.id}/${node.id}: ワールド内変数名がありません"
                }
                if (worldVariableTypes[variable] != VariableType.NUMBER) {
                    errors += "${script.id}/${node.id}: 数値型以外の変数条件は完全バニラ出力に未対応です"
                }
                if (node.string("operator") !in setOf(">", ">=", "<", "<=", "==", "!=")) {
                    errors += "${script.id}/${node.id}: 変数比較方法が不正です"
                }
                val raw = node.string("value")
                val parsed = raw.toDoubleOrNull()
                val reference = VariableTemplate.references(raw).singleOrNull()
                if (reference == null && parsed == null) {
                    errors += "${script.id}/${node.id}: バニラ出力の比較値は数値または単一の変数参照で指定してください: $raw"
                } else if (reference == null && scaledScore(parsed!!) == null) {
                    errors += "${script.id}/${node.id}: 比較値がバニラの固定小数点範囲外です: $raw"
                } else if (reference != null && SystemVariableNames.isSystemName(reference) &&
                    enclosingFor(script.graph, node.id) == null
                ) {
                    errors += "${script.id}/${node.id}: システム変数はfor本体内でのみ参照できます"
                } else if (reference != null && !SystemVariableNames.isSystemName(reference) &&
                    worldVariableTypes[reference] != VariableType.NUMBER
                ) {
                    errors += "${script.id}/${node.id}: 比較対象の変数は数値型である必要があります: $reference"
                }
            }
            ConditionKind.PLAYER_STATE -> {
                val itemData = node.string("itemData")
                if (itemData.isNotBlank() && ItemStackCodec.decode(itemData)?.hasItemMeta() != false) {
                    errors += "${script.id}/${node.id}: アイテムデータ付き所持判定は完全バニラ出力に未対応です"
                }
            }
            else -> Unit
        }
    }

    private fun annotateVariableTypes(
        graph: CommandGraph,
        worldVariableTypes: Map<String, VariableType>,
        errors: MutableList<String>,
    ) {
        fun annotate(current: CommandGraph) {
            current.nodes.values.forEach { node ->
                if (node.type == CommandType.CONDITION &&
                    node.string("kind") == ConditionKind.VARIABLE_STATE.name
                ) {
                    val name = node.string("variable")
                    val type = worldVariableTypes[name]
                    if (type == null) {
                        errors += "${node.id}: ワールド内変数の型を一意に解決できません: $name"
                    } else if (type != VariableType.NUMBER) {
                        errors += "${node.id}: 文字列変数の条件は完全バニラ出力に未対応です: $name"
                    } else {
                        node.params[EXPORT_VARIABLE_TYPE] = type.name
                    }
                }
                if (node.type == CommandType.VARIABLE &&
                    node.string("operation", VariableOperation.DEFINE.name) == VariableOperation.CHANGE.name
                ) {
                    val name = node.string("name")
                    val type = worldVariableTypes[name]
                    if (type == null) {
                        errors += "${node.id}: 変更対象のワールド内変数の型を一意に解決できません: $name"
                    } else {
                        // CHANGEでは型タブを表示しないため、出力時だけ実在する定義から補います。
                        node.params[EXPORT_VARIABLE_TYPE] = type.name
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
            errors += "${script.id}/${node.id}: 出力先のマイワールドのスポーン位置を検証できません"
        }
    }

    private fun validatePosition(
        script: DiskScript,
        node: CommandNode,
        position: me.awabi2048.kantancommander.model.PositionSpec?,
        errors: MutableList<String>,
    ) {
        val kind = position?.kind
        if (kind == PositionKind.MYWORLD_SPAWN) {
            errors += "${script.id}/${node.id}: ${kind}の位置は完全バニラ出力に未対応です"
        }
    }

    private fun validateFacing(
        script: DiskScript,
        node: CommandNode,
        facing: me.awabi2048.kantancommander.model.FacingSpec?,
        errors: MutableList<String>,
    ) {
        if (facing?.kind == FacingKind.MYWORLD_SPAWN) {
            errors += "${script.id}/${node.id}: 出力先のマイワールドのスポーン位置の向きを検証できません"
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
                    lines += assignLoopCount(loop, node, graph)
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
                        val after = node.next?.let { "return run function kantan:${nodeFunction(prefix, it)}" }
                            ?: "return 1"
                        // count == limit の反復後は加算せず終了します。Int最大値の
                        // ループでscoreboardをオーバーフローさせないための境界です。
                        lines += "execute if score #${loop}_count kc_vars >= #${loop}_limit kc_vars run $after"
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
                    node.string("operation") == VariableOperation.CHANGE.name &&
                    node.string("changeMode", VariableChangeMode.ASSIGN.name) == VariableChangeMode.CALCULATE.name -> {
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
                    val macro = prepareMacro(node, contextual, output, graph)
                    lines += macro.setup
                    lines += storeResult(node, macro.call)
                }
            }

            when (node.type) {
                CommandType.CONDITION -> {
                    val conditionContext = conditionContext(node)
                    conditionPreparation(node, graph)?.let { preparation ->
                        lines += conditionContext?.let { context -> wrapContext(context, preparation) } ?: preparation
                    }
                    val predicate = predicate(node)
                    val predicateMacro = prepareMacroTemplate(node, predicate, graph)
                    predicateMacro?.setup?.let(lines::addAll)
                    val predicateCommand = predicateMacro?.command ?: predicate
                    // Java版と同じく「に等しくない」は等号判定を反転して表現します。
                    // execute if score には != 演算子がないため、ノード自身の反転と
                    // 合成してからtrue/false枝を組み立てます。
                    val inverted = node.boolean("inverted") xor conditionNeedsInversion(node)
                    val trueCheck = if (inverted) "unless" else "if"
                    val falseCheck = if (inverted) "if" else "unless"
                    val trueBranch = node.trueNext?.let {
                        "execute $trueCheck $predicateCommand run return run function kantan:${nodeFunction(prefix, it)}"
                    } ?: "execute $trueCheck $predicateCommand run return 1"
                    val falseBranch = node.falseNext?.let {
                        "execute $falseCheck $predicateCommand run return run function kantan:${nodeFunction(prefix, it)}"
                    } ?: "execute $falseCheck $predicateCommand run return 1"
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

    private fun teleportCommand(node: CommandNode): String {
        val base = "tp ${effectiveTarget(node)} ${destination(node)}"
        val facing = node.destinationFacingSpec ?: return base
        return when (facing.kind) {
            FacingKind.INHERITED -> base
            FacingKind.ROTATION, FacingKind.CAPTURED ->
                "$base ${facing.yaw ?: 0f} ${facing.pitch ?: 0f}"
            FacingKind.EXECUTOR -> "$base facing entity @s eyes"
            FacingKind.TARGET -> {
                val target = node.contextOverride?.target ?: node.targetSpec ?: TargetSpec(TargetKind.INHERITED_TARGET)
                "$base facing entity ${singleSelector(target)} eyes"
            }
            FacingKind.TEMPORARY -> error("temporary facing is unsupported in vanilla output")
            FacingKind.COORDINATES ->
                "$base facing ${facing.x ?: error("destination facing x is missing")} " +
                    "${facing.y ?: error("destination facing y is missing")} " +
                    "${facing.z ?: error("destination facing z is missing")}"
            FacingKind.MYWORLD_SPAWN -> error("unsupported destination facing")
        }
    }

    private fun soundCommand(node: CommandNode): String {
        val sound = "playsound ${node.string("sound")} master @a ~ ~ ~ " +
            "${node.string("volume", "1.0")} ${node.string("pitch", "1.0")}"
        if (node.string("soundScope", "CONTEXT") == "WORLD") {
            // 全域指定は各プレイヤー位置を音源位置にするという計画書の定義に合わせ、
            // プレイヤーごとに一度ずつ実行します。
            return "execute as @a at @s run playsound ${node.string("sound")} master @s ~ ~ ~ " +
                "${node.string("volume", "1.0")} ${node.string("pitch", "1.0")}"
        }
        val position = node.soundPositionSpec ?: return sound
        return when (position.kind) {
            PositionKind.CAPTURED, PositionKind.COORDINATES ->
                "execute positioned ${position.x ?: error("sound x is missing")} " +
                    "${position.y ?: error("sound y is missing")} ${position.z ?: error("sound z is missing")} run $sound"
            PositionKind.DISK -> sound
            PositionKind.EXECUTOR -> "execute at @s run $sound"
            PositionKind.TARGET -> {
                val target = node.contextOverride?.target ?: node.targetSpec ?: TargetSpec(TargetKind.INHERITED_TARGET)
                "execute at ${singleSelector(target)} run $sound"
            }
            PositionKind.TEMPORARY -> error("temporary sound position is unsupported in vanilla output")
            PositionKind.MYWORLD_SPAWN -> error("unsupported sound position")
        }
    }

    private fun lower(node: CommandNode, graph: CommandGraph): String? = when (node.type) {
        CommandType.TELEPORT -> teleportCommand(node)
        CommandType.GIVE_ITEM -> "give ${effectiveTarget(node)} ${node.string("item")} ${node.string("count", "1")}"
        CommandType.ENTITY_ACTION -> when (node.string("action")) {
            "dismount" -> "ride ${effectiveTarget(node)} dismount"
            "equip" -> "item replace entity ${effectiveTarget(node)} ${equipmentSlot(node.string("slot"))} with ${node.string("item")}"
            "tag" -> "tag ${effectiveTarget(node)} ${node.string("tagOperation", "add")} ${node.string("tag")}"
            else -> "ride ${effectiveTarget(node)} mount ${singleSelector(requireNotNull(node.secondaryTargetSpec))}"
        }
        CommandType.DISPLAY_TEXT -> when (node.string("mode", "tellraw")) {
            "title" -> "title ${effectiveTarget(node)} title {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
            "subtitle" -> "title ${effectiveTarget(node)} subtitle {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
            "actionbar" -> "title ${effectiveTarget(node)} actionbar {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
            else -> "tellraw ${effectiveTarget(node)} {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
        }
        CommandType.SUMMON_ENTITY -> {
            // 召喚タグは一つの文字列としてNBTへ一要素だけを書き込みます。
            // 入力中のカンマを複数タグの区切りとして再解釈しません。
            val tag = node.string("tags").takeIf(String::isNotBlank)
            val tagNbt = tag?.let { "Tags:[\\\"${escape(it)}\\\"]" }.orEmpty()
            val customName = node.string("customName").takeIf(String::isNotBlank)
                ?.let { "CustomName:\"{\\\"text\\\":\\\"${escape(it.replace('&', '§'))}\\\"}\"" }
            val nbtFields = listOfNotNull(tagNbt.takeIf(String::isNotBlank), customName)
            val nbt = nbtFields.takeIf { it.isNotEmpty() }?.let { " {${it.joinToString(",")}}" }.orEmpty()
            "summon ${node.string("entity")} ~ ~ ~$nbt"
        }
        CommandType.PLAY_SOUND -> soundCommand(node)
        CommandType.APPLY_EFFECT ->
            "effect give ${effectiveTarget(node)} ${node.string("effect")} ${node.string("seconds", "30")} ${effectAmplifier(node.string("level", "1"))}"
        CommandType.CAMERA_SHAKE -> null
        CommandType.BLOCK_OPERATION -> blockOperationCommand(node)
        CommandType.ENTITY_DELETE -> "kill ${effectiveTarget(node)}"
        CommandType.DISK_CALL -> null
        CommandType.VARIABLE -> lowerVariable(node, graph)
        CommandType.TEMP_SET -> null
        CommandType.WAIT, CommandType.CONTEXT, CommandType.CONDITION, CommandType.MERGE,
        CommandType.FOR_START, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> null
    }

    /** 動的な文字列・数値入力はJava版のfunction macroへ一元的に変換します。 */
    private fun prepareMacro(
        node: CommandNode,
        command: String,
        output: MutableMap<String, String>,
        graph: CommandGraph,
    ): MacroCommand {
        val template = prepareMacroTemplate(node, command, graph)
        if (template == null) return MacroCommand(emptyList(), command)
        val macroName = nodeFunction("macro", node.id)
        defineFunction(output, macroName, "${'$'}${template.command}\n")
        return MacroCommand(template.setup, "function kantan:$macroName with storage kantan:variables ${template.storagePath}")
    }

    /** 条件分岐のpredicateなど、関数化せず置換文字列だけが必要な箇所にも使います。 */
    private fun prepareMacroTemplate(node: CommandNode, command: String, graph: CommandGraph): MacroTemplate? {
        val references = VariableTemplate.references(command)
        if (references.isEmpty()) return null
        val enclosingLoop = references
            .filter(SystemVariableNames::isSystemName)
            .map { enclosingFor(graph, node.id) }
            .firstOrNull()
        if (references.any(SystemVariableNames::isSystemName) && enclosingLoop == null) {
            // 実行前検証が通常この経路を遮断しますが、単体で呼ばれた場合にも
            // システム変数をワールド変数として誤出力しないよう防御します。
            return null
        }
        val token = node.id.toString().replace("-", "")
        val storagePath = "macro.$token"
        val replacements = references.associateWith { "v_${shortDigest(it, 10)}" }
        val macroCommand = Regex("\\$\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}").replace(command) { match ->
            "${'$'}(${replacements.getValue(match.groupValues[1])})"
        }
        val setup = buildList {
            add("data remove storage kantan:variables $storagePath")
            references.forEach { reference ->
                val target = "$storagePath.${replacements.getValue(reference)}"
                if (SystemVariableNames.isSystemName(reference)) {
                    val loop = requireNotNull(enclosingLoop)
                    val source = "#${loopName(loop.id)}_count"
                    add("execute store result storage kantan:variables $target double 1.0 run scoreboard players get $source kc_vars")
                } else {
                    add(
                        "data modify storage kantan:variables $target " +
                            "set from storage kantan:variables ${VanillaStorageNames.variablePath(reference, temporary = false)}",
                    )
                }
            }
        }
        return MacroTemplate(storagePath, macroCommand, setup)
    }

    private fun effectAmplifier(raw: String): String =
        raw.toDoubleOrNull()?.let { value ->
            if (value.isFinite() && value == value.toLong().toDouble()) {
                (value.toLong() - 1L).coerceAtLeast(0L).toString()
            } else {
                raw
            }
        } ?: raw

    private data class MacroCommand(
        val setup: List<String>,
        val call: String,
    )

    private data class MacroTemplate(
        val storagePath: String,
        val command: String,
        val setup: List<String>,
    )

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
                warnings += "$path/${node.id}: カメラシェイクはJava版データパックから省略されました"
            }
            node.snapshot?.let { collectWarnings(it, "$path/${node.id}/snapshot", warnings) }
        }
    }

    private fun predicate(node: CommandNode): String = when (
        ConditionKind.valueOf(node.string("kind", ConditionKind.TARGET_EXISTS.name))
    ) {
        ConditionKind.TARGET_EXISTS -> "entity ${conditionTarget(node)}"
        ConditionKind.PLAYER_STATE -> {
            val nbt = buildList {
                when (node.string("sneaking").toBooleanStrictOrNull()) {
                    true -> add("Pose:\\\"CROUCHING\\\"")
                    false -> add("Pose:\\\"STANDING\\\"")
                    null -> Unit
                }
                node.string("item").takeIf(String::isNotBlank)?.let {
                    add("Inventory:[{id:\\\"${escape(it)}\\\"}]")
                }
            }
            val target = conditionTarget(node)
            if (nbt.isEmpty()) "entity $target"
            else "entity ${appendSelectorArguments(target, "nbt={${nbt.joinToString(",")}}")}"
        }
        ConditionKind.VARIABLE_STATE -> {
            val holder = conditionCountHolder(node)
            val raw = node.string("value")
            val value = raw.toDoubleOrNull()?.let(::scaledScore)
            val operator = comparisonOperator(node)
            if (value != null) {
                "score $holder kc_runtime matches ${scoreRange(operator, value)}"
            } else {
                "score $holder kc_runtime $operator ${conditionValueHolder(node)} kc_runtime"
            }
        }
        ConditionKind.BLOCK_STATE ->
            "block ~ ~ ~ ${node.string("block", "minecraft:air")}"
    }

    private fun conditionPreparation(node: CommandNode, graph: CommandGraph): String? =
        if (ConditionKind.valueOf(node.string("kind")) == ConditionKind.VARIABLE_STATE) {
            buildList {
                add(
                    "execute store result score ${conditionCountHolder(node)} kc_runtime run data get storage " +
                        "kantan:variables ${VanillaStorageNames.variablePath(node.string("variable"), temporary = false)} $FIXED_POINT_SCALE",
                )
                val raw = node.string("value")
                val staticValue = raw.toDoubleOrNull()?.let(::scaledScore)
                if (staticValue != null) {
                    add("scoreboard players set ${conditionValueHolder(node)} kc_runtime $staticValue")
                } else {
                    VariableTemplate.references(raw).singleOrNull()?.let { reference ->
                        if (SystemVariableNames.isSystemName(reference)) {
                            val loop = enclosingFor(graph, node.id)
                            if (loop != null) {
                                val source = "#${loopName(loop.id)}_count"
                                add("scoreboard players operation ${conditionValueHolder(node)} kc_runtime = $source kc_vars")
                                add("scoreboard players set #kc_scale kc_runtime $FIXED_POINT_SCALE")
                                add("scoreboard players operation ${conditionValueHolder(node)} kc_runtime *= #kc_scale kc_runtime")
                            }
                        } else {
                            add(
                                "execute store result score ${conditionValueHolder(node)} kc_runtime run data get storage " +
                                    "kantan:variables ${VanillaStorageNames.variablePath(reference, temporary = false)} $FIXED_POINT_SCALE",
                            )
                        }
                    }
                }
            }.joinToString("\n")
        } else null

    private fun conditionCountHolder(node: CommandNode) =
        "#c_${node.id.toString().replace("-", "")}"

    private fun conditionValueHolder(node: CommandNode) =
        "#cv_${node.id.toString().replace("-", "")}"

    private fun conditionNeedsInversion(node: CommandNode): Boolean =
        node.string("kind") == ConditionKind.VARIABLE_STATE.name && node.string("operator") == "!="

    private fun comparisonOperator(node: CommandNode): String =
        node.string("operator").takeUnless { it == "!=" } ?: "=="

    private fun conditionTarget(node: CommandNode): String {
        val spec = node.targetSpec ?: node.contextOverride?.target ?: TargetSpec(TargetKind.INHERITED_TARGET)
        return selector(if (spec.kind == TargetKind.INHERITED_TARGET) spec else spec.copy(limit = 1))
    }

    private fun lowerVariable(node: CommandNode, graph: CommandGraph): String? {
        val name = node.string("name")
        val storagePath = VanillaStorageNames.variablePath(name, temporary = false)
        val operation = VariableOperation.valueOf(node.string("operation", VariableOperation.DEFINE.name))
        val type = VariableType.valueOf(
            if (operation == VariableOperation.CHANGE) {
                node.params[EXPORT_VARIABLE_TYPE] ?: node.string("type", VariableType.NUMBER.name)
            } else {
                node.string("type", VariableType.NUMBER.name)
            },
        )
        if (operation == VariableOperation.CHANGE &&
            node.string("changeMode", VariableChangeMode.ASSIGN.name) == VariableChangeMode.CALCULATE.name
        ) return null
        val raw = node.string("value")
        val special = raw.takeIf { it == "${'$'}{${SystemVariableNames.CURRENT_LOOP_COUNT}}" }
        if (special != null) {
            val loop = enclosingFor(graph, node.id) ?: return null
            if (type == VariableType.NUMBER) {
                val source = "#${loopName(loop.id)}_count"
                return "execute store result storage kantan:variables $storagePath double 1.0 run scoreboard players get $source kc_vars"
            }
            // 文字列代入は下のmacro経路でシステム変数を文字列化します。
        }
        val assignment = when (type) {
            VariableType.NUMBER ->
                "data modify storage kantan:variables $storagePath set value ${raw}d"
            VariableType.STRING ->
                "data modify storage kantan:variables $storagePath set value \"${escape(raw)}\""
        }
        return if (operation == VariableOperation.DEFINE) {
            "execute unless data storage kantan:variables $storagePath run $assignment"
        } else assignment
    }

    private fun lowerArithmeticVariable(node: CommandNode, graph: CommandGraph): List<String> {
        val parsed = NumericExpression.parse(node.string("value")).expression ?: return listOf("return 0")
        val enclosingLoop = enclosingFor(graph, node.id)
        val prefix = "#expr_${node.id.toString().replace("-", "").take(12)}"
        val stack = ArrayDeque<String>()
        val lines = mutableListOf<String>()
        lines += "scoreboard players set ${prefix}_scale kc_runtime $FIXED_POINT_SCALE"
        lines += "scoreboard players set ${prefix}_negative kc_runtime -1"

        fun holder(index: Int) = "${prefix}_$index"
        fun push(value: String) = stack.addLast(value)
        fun pop(): String? = stack.removeLastOrNull()

        parsed.postfix().forEachIndexed { index, token ->
            when (token) {
                is NumericExpression.PostfixToken.Literal -> {
                    val scaled = scaledScore(token.value) ?: return listOf("return 0")
                    val destination = holder(index)
                    lines += "scoreboard players set $destination kc_runtime $scaled"
                    push(destination)
                }
                is NumericExpression.PostfixToken.Reference -> {
                    val destination = holder(index)
                    when {
                        SystemVariableNames.isSystemName(token.name) -> {
                            val loop = enclosingLoop ?: return listOf("return 0")
                            if (token.name != SystemVariableNames.CURRENT_LOOP_COUNT) return listOf("return 0")
                            val source = "#${loopName(loop.id)}_count"
                            lines += "scoreboard players operation $destination kc_runtime = $source kc_vars"
                            lines += "scoreboard players operation $destination kc_runtime *= ${prefix}_scale kc_runtime"
                        }
                        else -> lines +=
                            "execute store result score $destination kc_runtime run data get storage " +
                                "kantan:variables ${VanillaStorageNames.variablePath(token.name, temporary = false)} $FIXED_POINT_SCALE"
                    }
                    push(destination)
                }
                is NumericExpression.PostfixToken.Operator -> {
                    if (token.value == '~') {
                        val operand = pop() ?: return listOf("return 0")
                        lines += "scoreboard players operation $operand kc_runtime *= ${prefix}_negative kc_runtime"
                        push(operand)
                    } else {
                        val rhs = pop() ?: return listOf("return 0")
                        val lhs = pop() ?: return listOf("return 0")
                        when (token.value) {
                            '+' -> lines += "scoreboard players operation $lhs kc_runtime += $rhs kc_runtime"
                            '-' -> lines += "scoreboard players operation $lhs kc_runtime -= $rhs kc_runtime"
                            '*' -> {
                                lines += "scoreboard players operation $lhs kc_runtime *= $rhs kc_runtime"
                                lines += "scoreboard players operation $lhs kc_runtime /= ${prefix}_scale kc_runtime"
                            }
                            '/' -> {
                                lines += "execute if score $rhs kc_runtime matches 0 run return 0"
                                lines += "scoreboard players operation $lhs kc_runtime *= ${prefix}_scale kc_runtime"
                                lines += "scoreboard players operation $lhs kc_runtime /= $rhs kc_runtime"
                            }
                            else -> return listOf("return 0")
                        }
                        push(lhs)
                    }
                }
            }
        }
        val result = stack.singleOrNull() ?: return listOf("return 0")
        val storagePath = VanillaStorageNames.variablePath(node.string("name"), temporary = false)
        lines += "execute store result storage kantan:variables $storagePath double 0.001 run scoreboard players get $result kc_runtime"
        lines += "return 1"
        return lines
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

    /** ループ上限を開始時に一度だけ確定します。可変参照でも本体中の変更の影響を受けません。 */
    private fun assignLoopCount(loop: String, node: CommandNode, graph: CommandGraph): String {
        val destination = "#${loop}_limit"
        val raw = node.string("count", "1")
        val reference = VariableTemplate.references(raw).singleOrNull()
        if (reference == SystemVariableNames.CURRENT_LOOP_COUNT) {
            val outer = enclosingFor(graph, node.id) ?: return "scoreboard players set $destination kc_vars 0"
            return "scoreboard players operation $destination kc_vars = #${loopName(outer.id)}_count kc_vars"
        }
        if (reference != null) {
            // ワールド変数の正本はstorageです。ループ開始時だけscoreboardへ読み込み、
            // 本体中に同じ変数が変更されても現在のループ上限を安定させます。
            return "execute store result score $destination kc_vars run data get storage " +
                "kantan:variables ${VanillaStorageNames.variablePath(reference, temporary = false)} 1"
        }
        return "scoreboard players set $destination kc_vars $raw"
    }

    private fun loopCheck(graph: CommandGraph, prefix: String, start: CommandNode): String {
        val loop = loopName(start.id)
        val body = start.trueNext
        val end = start.pairedNodeId
        val after = end?.let(graph.nodes::get)?.next
        val bodyFunction = body?.takeUnless { it == end }?.let { "function kantan:${nodeFunction(prefix, it)}" }
        val lines = mutableListOf<String>()
        lines += "scoreboard players set #${loop}_run kc_vars 0"
        if (bodyFunction != null) {
            lines += "execute if score #${loop}_count kc_vars <= #${loop}_limit kc_vars run scoreboard players set #${loop}_run kc_vars 1"
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

    /** 変数はすべてMyWorld単位へ統一したため、実行ローカルscoreboardの初期化は不要です。 */
    private fun temporaryNames(graph: CommandGraph): Set<String> = emptySet()

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
                PositionKind.TEMPORARY -> error("temporary teleport destination is unsupported in vanilla output")
                PositionKind.MYWORLD_SPAWN -> error("unsupported structured teleport destination")
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
        PositionKind.TEMPORARY -> error("temporary block position is unsupported in vanilla output")
        PositionKind.MYWORLD_SPAWN -> error("unsupported structured block position")
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
                .append(":destinationFacing=").append(node.destinationFacingSpec)
                .append(":conditionPosition=").append(node.conditionPositionSpec)
                .append(":soundPosition=").append(node.soundPositionSpec)
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
            spec.dx?.let { add("dx=${selectorExtent(it)}") }
            spec.dy?.let { add("dy=${selectorExtent(it)}") }
            spec.dz?.let { add("dz=${selectorExtent(it)}") }
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

    private fun selectorExtent(value: Double): String =
        value.toString().removeSuffix(".0")

    /**
     * バニラのscoreboardへdoubleを写すための固定小数点境界です。
     * 数値型の正本はstorageのdoubleのまま保持し、出力時だけ小数第3位へ
     * 射影します。範囲外は静かにラップさせず、事前検証で拒否します。
     */
    private fun scaledScore(value: Double): Long? {
        if (!value.isFinite()) return null
        val scaled = value * FIXED_POINT_SCALE.toDouble()
        if (!scaled.isFinite() || scaled < Int.MIN_VALUE || scaled > Int.MAX_VALUE) return null
        return kotlin.math.round(scaled).toLong()
    }

    private fun exactLong(value: Double): Long? {
        if (!value.isFinite() || value != kotlin.math.floor(value)) return null
        if (value < Long.MIN_VALUE.toDouble() || value > Long.MAX_VALUE.toDouble()) return null
        return value.toLong()
    }

    private fun scoreRange(operator: String, value: Long): String = when (operator) {
        "==" -> value.toString()
        "!=" -> value.toString()
        ">" -> if (value >= Int.MAX_VALUE) "${Int.MAX_VALUE + 1L}.." else "${value + 1}.."
        "<" -> if (value <= Int.MIN_VALUE) "..${Int.MIN_VALUE - 1L}" else "..${value - 1}"
        "<=" -> "..$value"
        else -> "$value.."
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private companion object {
        const val FIXED_POINT_SCALE = 1000L
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
