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
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.TICKS_PER_SECOND
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
            "scoreboard objectives add kc_result dummy\n" +
                "scoreboard objectives add kc_vars dummy\n" +
                "scoreboard objectives add kc_runtime dummy\n" +
                "scoreboard objectives add kc_tu0 dummy\n" +
                "scoreboard objectives add kc_tu1 dummy\n" +
                "scoreboard objectives add kc_tu2 dummy\n" +
                "scoreboard objectives add kc_tu3 dummy\n",
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
                    if (node.itemTempRef.isNullOrBlank() &&
                        (!item.startsWith("minecraft:") || CommandValueRules.material(item, allowAir = false) == null)
                    ) {
                        errors += "${script.id}/${node.id}: バニラに存在しないアイテムです: $item"
                    }
                    val itemData = node.string("itemData")
                    if (node.itemTempRef.isNullOrBlank() && itemData.isNotBlank() && ItemStackCodec.decode(itemData)?.hasItemMeta() != false) {
                        errors += "${script.id}/${node.id}: 保存されたItemStackメタデータは完全バニラ出力に未対応です"
                    }
                }
                CommandType.ENTITY_ACTION -> when (node.string("action")) {
                    "ride", "dismount" -> Unit
                    "equip" -> {
                        val item = node.string("item")
                        if (node.itemTempRef.isNullOrBlank() &&
                            (!item.startsWith("minecraft:") || CommandValueRules.material(item, allowAir = false) == null)
                        ) {
                            errors += "${script.id}/${node.id}: バニラに存在しない装備アイテムです: $item"
                        }
                        val itemData = node.string("itemData")
                        if (node.itemTempRef.isNullOrBlank() && itemData.isNotBlank() && ItemStackCodec.decode(itemData)?.hasItemMeta() != false) {
                            errors += "${script.id}/${node.id}: 保存された装備ItemStackメタデータは完全バニラ出力に未対応です"
                        }
                    }
                    "tag" -> Unit
                    else -> errors += "${script.id}/${node.id}: プラグイン固有のエンティティ操作です"
                }
                CommandType.PARTICLE -> {
                    val particle = ParticleSettings.particle(node)
                    if (particle == null) {
                        errors += "${script.id}/${node.id}: パーティクルの種類が不正です"
                    } else {
                        val data = ParticleSettings.parseData(particle, node.string(ParticleSettings.PARAM_DATA)).getOrNull()
                        if (data == null) {
                            errors += "${script.id}/${node.id}: パーティクルの詳細データが不正です"
                        } else if (data is ParticleSettings.ParticleDataSpec.Item &&
                            (!data.raw.startsWith("minecraft:") || CommandValueRules.material(data.raw, allowAir = false) == null)
                        ) {
                            errors += "${script.id}/${node.id}: ItemStackの詳細データは完全バニラ出力に未対応です"
                        }
                    }
                }
                CommandType.BLOCK_OPERATION -> {
                    val block = CommandValueRules.placementMaterial(node.string("block"))
                    if (node.blockTempRef.isNullOrBlank() &&
                        (!node.string("block").startsWith("minecraft:") || block == null)
                    ) {
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
                CommandType.TEMP_SET -> {
                    when (TemporaryVariableType.parse(node.string("tempType"))) {
                        TemporaryVariableType.LOCATION -> {
                            if (node.temporaryLocationPositionSpec?.kind == PositionKind.MYWORLD_SPAWN) {
                                errors += "${script.id}/${node.id}: 一時LOCATIONのMyWorldスポーン位置は完全バニラ出力に未対応です"
                            }
                            if (node.temporaryLocationFacingSpec?.kind == FacingKind.MYWORLD_SPAWN) {
                                errors += "${script.id}/${node.id}: 一時LOCATIONのMyWorldスポーン向きは完全バニラ出力に未対応です"
                            }
                        }
                        TemporaryVariableType.ENTITY -> if (node.temporaryEntityTargetSpec?.kind == TargetKind.FIXED_ENTITY) {
                            errors += "${script.id}/${node.id}: 一時ENTITYの固定エンティティ参照は完全バニラ出力できません"
                        }
                        else -> Unit
                    }
                }
                else -> Unit
            }
            validatePosition(script, node, node.conditionPositionSpec, errors)
            validatePosition(script, node, node.soundPositionSpec, errors)
            validatePosition(script, node, node.particlePositionSpec, errors)
            validatePosition(script, node, node.summonPositionSpec, errors)
            if (node.soundPositionSpec?.kind == PositionKind.TARGET) {
                errors += "${script.id}/${node.id}: 効果音の対象位置指定は完全バニラ出力に未対応です"
            }
            if (node.particlePositionSpec?.kind == PositionKind.TARGET) {
                errors += "${script.id}/${node.id}: パーティクルの対象位置指定は完全バニラ出力に未対応です"
            }
            if (node.summonPositionSpec?.kind == PositionKind.TARGET) {
                errors += "${script.id}/${node.id}: 召喚の対象位置指定は完全バニラ出力に未対応です"
            }
            validateFacing(script, node, node.destinationFacingSpec, errors)
            if (listOfNotNull(
                    node.targetSpec,
                    node.secondaryTargetSpec,
                    node.destinationTargetSpec,
                    node.temporaryEntityTargetSpec,
                )
                    .any { it.kind == TargetKind.FIXED_ENTITY }
            ) {
                errors += "${script.id}/${node.id}: 固定エンティティ参照は完全バニラ出力できません"
            }
            val temporaryArgumentNames = listOfNotNull(
                node.targetSpec,
                node.secondaryTargetSpec,
                node.destinationTargetSpec,
            ).filter { it.kind == TargetKind.TEMPORARY }
                .mapNotNull { it.tempName?.takeIf(String::isNotBlank)?.let(TemporaryTemplate::normalized) }
                .distinct()
            if (temporaryArgumentNames.size > 1) {
                // vanillaのselectorには別の一時ENTITYを同時に埋め込めないため、
                // 片方がもう片方へ化ける出力を許可せず、実行前に明示的に止めます。
                errors += "${script.id}/${node.id}: 複数の異なる一時エンティティを同じコマンド引数へ指定したため完全バニラ出力できません"
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
                // 一時変数の正本はこのstorage配下だけです。実行開始時に
                // ルートを空にすることで、前回実行や別の入口の値を引き継ぎません。
                appendLine("data modify storage kantan:variables variables.temporary set value {}")
                temporaryNames(graph).forEach {
                    appendLine("scoreboard players reset ${variableHolder(it, temporary = true)} kc_vars")
                    appendLine("data remove storage kantan:variables ${VanillaStorageNames.variablePath(it, temporary = true)}")
                }
                append(entryCall)
            }
        } else entryCall)
        graph.nodes.values.forEach { node ->
            val lines = mutableListOf<String>()
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
                    temporaryEntityReferences(node, null).forEach { reference ->
                        lines += temporaryEntityPreparation(reference)
                    }
                    val backupPath = VanillaStorageNames.callBackupPath(node.id)
                    // Java版のExecutionSessionと同じく、呼出先の一時変数を
                    // 呼出元へ漏らしません。storageの一時領域全体を退避し、
                    // 呼出先終了後に正確に復元します。
                    lines += "data remove storage kantan:variables $backupPath"
                    lines += "data modify storage kantan:variables $backupPath set from storage kantan:variables variables.temporary"
                    lines += "data modify storage kantan:variables variables.temporary set value {}"
                    lines += storeFunctionResult(node, call)
                    lines += "data modify storage kantan:variables variables.temporary set value {}"
                    lines += "data modify storage kantan:variables variables.temporary set from storage kantan:variables $backupPath"
                    lines += "data remove storage kantan:variables $backupPath"
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
                    lines += storeFunctionResult(node, command)
                }
                node.type == CommandType.TEMP_SET -> {
                    // LOCATIONの現位置捕捉やENTITYの対象選択は複数のvanilla命令へ
                    // 展開されます。通常コマンドの1命令前提へ押し込むと、先頭命令だけが
                    // scoreへ記録され、残りが実行されないため、ノード専用関数へまとめます。
                    val helper = nodeFunction(prefix, node.id, "temporary")
                    val expansion = temporarySetCommands(node, output, graph)
                    defineFunction(
                        output,
                        helper,
                        expansion.commands.joinToString("\n", postfix = "\n") + "return 1\n",
                    )
                    // 値欄や複合値の数値欄に含まれるテンプレートは、専用関数を
                    // 呼び出す前にmacro用storageへ展開します。LOCATIONの複数命令も
                    // 同じ関数境界で実行するため、参照値を途中で取り違えません。
                    lines += expansion.setup
                    lines += storeFunctionResult(node, "function kantan:$helper")
                }
                else -> lower(node, graph)?.let { command ->
                    val temporaryReferences = temporaryEntityReferences(node, null)
                    temporaryReferences.forEach { reference ->
                        lines += temporaryEntityPreparation(reference)
                    }
                    val temporaryArgument = temporaryEntityArgumentReference(node)
                    val targetedCommand = temporaryArgument?.let { reference ->
                        temporaryEntitySelection(reference, command)
                    } ?: command
                    if (node.type == CommandType.DISPLAY_TEXT && DisplayTextTimingPolicy.supports(node)) {
                        val timing = DisplayTextTiming.from(node)
                        val times = "title ${effectiveTarget(node)} times " +
                            "${timing.fadeInTicks} " +
                            "${timing.stayTicks} " +
                            "${timing.fadeOutTicks}"
                        val primaryTarget = temporaryReferences.firstOrNull { it.role == "primary" }
                        val targetedTimes = primaryTarget?.let { reference ->
                            temporaryEntitySelection(reference, times)
                        } ?: times
                        lines += targetedTimes
                    }
                    val macro = prepareMacro(node, targetedCommand, output, graph)
                    lines += macro.setup
                    lines += storeResult(node, macro.call)
                    if (temporaryReferences.isNotEmpty()) {
                        // 対象が見つからないことは「対象なし」のスキップです。
                        // 実行ノードの成功スコアを補正し、後続関数へ進めます。
                        lines += "scoreboard players set ${scoreHolder(node.id)} kc_result 1"
                    }
                }
            }

            when (node.type) {
                CommandType.CONDITION -> {
                    val conditionContext = conditionContext(node)
                    val conditionTarget = temporaryConditionTargetReference(node, conditionContext)
                    val temporaryConditionFound = conditionTarget?.let { conditionTargetFoundHolder(node) }
                    temporaryEntityReferences(node, conditionContext).forEach { reference ->
                        lines += temporaryEntityPreparation(reference)
                    }
                    if (conditionTarget != null && temporaryConditionFound != null) {
                        lines += "scoreboard players set $temporaryConditionFound kc_runtime 0"
                        lines += temporaryEntityPresence(conditionTarget, temporaryConditionFound)
                    }
                    conditionPreparation(node, graph)?.let { preparation ->
                        lines += conditionContext?.let { context -> wrapContext(context, preparation, node) } ?: preparation
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
                    val targetedTrueBranch = conditionTarget?.let { reference ->
                        temporaryEntitySelection(reference, trueBranch)
                    } ?: trueBranch
                    val targetedFalseBranch = conditionTarget?.let { reference ->
                        temporaryEntitySelection(reference, falseBranch)
                    } ?: falseBranch
                    lines += conditionContext?.let { context -> wrapContext(context, targetedTrueBranch, node) } ?: targetedTrueBranch
                    lines += conditionContext?.let { context -> wrapContext(context, targetedFalseBranch, node) } ?: targetedFalseBranch
                    if (conditionTarget != null && temporaryConditionFound != null) {
                        // 一時ENTITYが消えている場合、execute as @e の枝は一度も
                        // 実行されないため、Java版と同じ「対象なし＝条件false」へ
                        // 明示的にフォールバックします。ノード自身の反転時だけ
                        // true枝へ反転します。
                        // 欠損時の条件値は常に「対象なし＝raw false」です。
                        // VARIABLE_STATEの `!=` を表現するためのpredicate反転まで
                        // ここへ混ぜると、対象欠損時だけ分岐が逆転してしまいます。
                        val fallback = if (node.boolean("inverted")) {
                            node.trueNext?.let { "return run function kantan:${nodeFunction(prefix, it)}" } ?: "return 1"
                        } else {
                            node.falseNext?.let { "return run function kantan:${nodeFunction(prefix, it)}" } ?: "return 1"
                        }
                        lines += "execute unless score $temporaryConditionFound kc_runtime matches 1 run $fallback"
                    }
                    lines += "return 0"
                }
                CommandType.WAIT ->
                    node.next?.let {
                        lines += "schedule function kantan:${nodeFunction(prefix, it)} ${node.int("seconds", 1).coerceAtLeast(1).toLong() * TICKS_PER_SECOND}t replace"
                    }
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
            FacingKind.ROTATION, FacingKind.CAPTURED ->
                "$base ${facing.yaw ?: 0f} ${facing.pitch ?: 0f}"
            FacingKind.TARGET -> {
                val target = node.targetSpec ?: error("destination target is missing")
                "$base facing entity ${singleSelector(target)} eyes"
            }
            FacingKind.TEMPORARY -> {
                val name = facing.tempName ?: error("temporary facing name is missing")
                "$base facing ${temporaryPositionCoordinates(name)}"
            }
            FacingKind.COORDINATES ->
                "$base facing ${facing.x ?: error("destination facing x is missing")} " +
                    "${facing.y ?: error("destination facing y is missing")} " +
                    "${facing.z ?: error("destination facing z is missing")}"
            FacingKind.MYWORLD_SPAWN -> error("unsupported destination facing")
        }
    }

    private fun soundCommand(node: CommandNode): String {
        val soundName = node.soundTempRef?.takeIf(String::isNotBlank)
            ?.let { temporaryMarker(it, "sound") }
            ?: node.string("sound")
        val volume = node.soundTempRef?.takeIf(String::isNotBlank)
            ?.let { temporaryMarker(it, "volume") }
            ?: node.string("volume", "1.0")
        val pitch = node.soundTempRef?.takeIf(String::isNotBlank)
            ?.let { temporaryMarker(it, "pitch") }
            ?: node.string("pitch", "1.0")
        val sound = "playsound $soundName master @a ~ ~ ~ $volume $pitch"
        if (node.string("soundScope", "POSITION") == "WORLD") {
            // 全域指定は各プレイヤー位置を音源位置にするという計画書の定義に合わせ、
            // プレイヤーごとに一度ずつ実行します。
            return "execute as @a at @s run playsound $soundName master @s ~ ~ ~ $volume $pitch"
        }
        val position = node.soundPositionSpec ?: return sound
        return when (position.kind) {
            PositionKind.CAPTURED, PositionKind.COORDINATES ->
                "execute positioned ${position.x ?: error("sound x is missing")} " +
                    "${position.y ?: error("sound y is missing")} ${position.z ?: error("sound z is missing")} run $sound"
            PositionKind.DISK -> sound
            // TARGETはテレポート先だけのコマンド固有値です。音の位置へ
            // 対象欄を暗黙に読み替えると、廃止した共通コンテキストへ戻るため、
            // 事前検証で拒否し、実行時にも別の意味へ変換しません。
            PositionKind.TARGET -> error("sound target position is not supported")
            PositionKind.TEMPORARY -> {
                val name = position.tempName ?: error("temporary sound position name is missing")
                "execute positioned ${temporaryPositionCoordinates(name)} run $sound"
            }
            PositionKind.MYWORLD_SPAWN -> error("unsupported sound position")
        }
    }

    /**
     * PARTICLEをvanillaの/particleへ変換します。対象selectorに@aを明示し、
     * execute positionedで表示中心だけを移動することで、同じワールドの全プレイヤーを
     * 表示対象にします。force指定による512ブロックのvanilla距離制限は仕様として許容します。
     */
    private fun particleCommand(node: CommandNode): String {
        val particle = ParticleSettings.particle(node) ?: error("particle type is missing")
        val data = ParticleSettings.parseData(particle, node.string(ParticleSettings.PARAM_DATA))
            .getOrElse { error("particle data is invalid") }
        // 追加データは1つのSNBT引数としてParticle IDへ直結します。旧版の
        // `/particle dust r g b scale ...`形式を出力すると、現行pack_formatの
        // データパックではコマンド解析に失敗するため、変換はParticleSettingsへ集約します。
        val dataArgument = data.vanillaArgument(particle)
        val command = "particle ${particle.key}$dataArgument " +
            "~ ~ ~ " +
            "${node.string(ParticleSettings.PARAM_DELTA_X, "0.0")} " +
            "${node.string(ParticleSettings.PARAM_DELTA_Y, "0.0")} " +
            "${node.string(ParticleSettings.PARAM_DELTA_Z, "0.0")} " +
            "${node.string(ParticleSettings.PARAM_SPEED, "0.0")} " +
            "${node.string(ParticleSettings.PARAM_COUNT, "1")} force @a"
        val position = node.particlePositionSpec ?: return command
        return when (position.kind) {
            PositionKind.CAPTURED, PositionKind.COORDINATES ->
                "execute positioned ${position.x ?: error("particle x is missing")} " +
                    "${position.y ?: error("particle y is missing")} ${position.z ?: error("particle z is missing")} run $command"
            PositionKind.DISK -> command
            PositionKind.TEMPORARY -> "execute positioned ${temporaryPositionCoordinates(
                position.tempName ?: error("temporary particle position name is missing"),
            )} run $command"
            PositionKind.TARGET -> error("particle target position is not supported")
            PositionKind.MYWORLD_SPAWN -> error("unsupported particle position")
        }
    }

    /** 一時値を保持するstorageパスです。型ごとの値は同じ名前空間へまとめます。 */
    private fun temporaryStoragePath(name: String): String =
        VanillaStorageNames.variablePath(TemporaryTemplate.normalized(name), temporary = true)

    /** LOCATION値をコマンド引数へ展開するための、exporter内部macro記法です。 */
    private fun temporaryPositionCoordinates(name: String): String = listOf("x", "y", "z")
        .joinToString(" ") { axis -> temporaryMarker(name, axis) }

    /**
     * 一時変数定義をstorage compoundへ変換します。
     *
     * Java実行側のTemporaryValueと同じく、LOCATION／SOUND／EFFECT等の複合値も
     * 1つの名前空間へ保存します。vanilla function macroへ渡す値は後段の
     * prepareMacroTemplateが必要なフィールドだけを抽出します。
     */
    private fun temporarySetCommand(node: CommandNode): String {
        val name = TemporaryTemplate.normalized(node.string("name"))
        val destination = temporaryStoragePath(name)
        val type = TemporaryVariableType.parse(
            node.string("tempType", TemporaryVariableType.NUMBER.name),
        ) ?: error("unknown temporary variable type")
        fun number(raw: String, fallback: String = "0.0"): String {
            val value = raw.trim().ifBlank { fallback }
            return if (value.endsWith("d", ignoreCase = true)) value else "${value}d"
        }
        fun integer(raw: String, fallback: String): String {
            val value = raw.trim().ifBlank { fallback }
            val parsed = value.toDoubleOrNull()
            return if (parsed != null && parsed.isFinite() && parsed == kotlin.math.floor(parsed)) {
                parsed.toLong().toString()
            } else {
                // テンプレートは後段のmacroへ渡すため、ここで型接尾辞を付けません。
                value
            }
        }
        fun directReference(raw: String): String? {
            TemporaryTemplate.references(raw).singleOrNull()?.let { reference ->
                if (TemporaryTemplate.isSingleReference(raw)) {
                    return "data modify storage kantan:variables $destination set from storage " +
                        "kantan:variables ${temporaryStoragePath(reference)}"
                }
            }
            VariableTemplate.references(raw).singleOrNull()?.let { reference ->
                if (VariableTemplate.isSingleReference(raw) && !SystemVariableNames.isSystemName(reference)) {
                    return "data modify storage kantan:variables $destination set from storage " +
                        "kantan:variables ${VanillaStorageNames.variablePath(reference, temporary = false)}"
                }
            }
            return null
        }
        return when (type) {
            TemporaryVariableType.NUMBER ->
                directReference(node.string("value"))
                    ?: "data modify storage kantan:variables $destination set value ${number(node.string("value"))}"
            TemporaryVariableType.STRING ->
                directReference(node.string("value"))
                    ?: "data modify storage kantan:variables $destination set value \"${escape(node.string("value"))}\""
            TemporaryVariableType.LOCATION -> {
                val position = node.temporaryLocationPositionSpec
                val facing = node.temporaryLocationFacingSpec
                if (position != null && facing != null) {
                    require(position.kind in setOf(PositionKind.CAPTURED, PositionKind.COORDINATES)) {
                        "dynamic temporary LOCATION requires expanded commands"
                    }
                    require(facing.kind in setOf(FacingKind.CAPTURED, FacingKind.ROTATION)) {
                        "dynamic temporary LOCATION facing requires expanded commands"
                    }
                    "data modify storage kantan:variables $destination set value " +
                        "{x:${number(position.x?.toString() ?: error("location x is missing"))}," +
                        "y:${number(position.y?.toString() ?: error("location y is missing"))}," +
                        "z:${number(position.z?.toString() ?: error("location z is missing"))}," +
                        "yaw:${number((facing.yaw ?: position.yaw ?: 0f).toString())}," +
                        "pitch:${number((facing.pitch ?: position.pitch ?: 0f).toString())}}"
                } else {
                    // 旧POSITIONのx/y/z/yaw/pitch形式は読み込み境界でだけ残り得ます。
                    "data modify storage kantan:variables $destination set value " +
                        "{x:${number(node.string("x"))},y:${number(node.string("y"))}," +
                        "z:${number(node.string("z"))},yaw:${number(node.string("yaw"))},pitch:${number(node.string("pitch"))}}"
                }
            }
            TemporaryVariableType.ITEM ->
                "data modify storage kantan:variables $destination set value " +
                    "{item:\"${escape(node.string("item"))}\",itemData:\"${escape(node.string("itemData"))}\"}"
            TemporaryVariableType.BLOCK ->
                "data modify storage kantan:variables $destination set value {block:\"${escape(node.string("block"))}\"}"
            TemporaryVariableType.ENTITY -> {
                val uuid = runCatching { UUID.fromString(node.string("entityId")) }.getOrNull()
                // 不正なUUIDも「参照時に対象なし」として扱う契約です。空の
                // int配列を無理に生成するとSNBT自体が不正になるため、値を空の
                // compoundとして保存し、参照側の4要素リセットへ委ねます。
                val value = uuid?.let { "{uuid:${uuidIntArray(it)}}" } ?: "{}"
                "data modify storage kantan:variables $destination set value $value"
            }
            TemporaryVariableType.SOUND ->
                "data modify storage kantan:variables $destination set value " +
                    "{sound:\"${escape(node.string("sound"))}\",volume:${number(node.string("volume", "1.0"))}," +
                    "pitch:${number(node.string("pitch", "1.0"))}}"
            TemporaryVariableType.EFFECT ->
                "data modify storage kantan:variables $destination set value " +
                    "{effect:\"${escape(node.string("effect"))}\",level:${integer(node.string("level", "1"), "1")}," +
                    "seconds:${integer(node.string("seconds", "30"), "30")}}"
        }
    }

    /**
     * 一時変数定義を、1命令または複数命令のvanilla関数へ展開します。
     *
     * LOCATIONのPositionSpecは、制御ブロック位置・座標・別LOCATION一時変数の
     * ような位置指定をstorageへ展開します。ENTITYのTargetSpecも同様にセレクター
     * 解決が必要です。それらを「数値3個の直接代入」として扱うと、GUIで選べる設定と
     * Datapack出力の意味がずれるため、実行時にstorageへ値を組み立てます。
     */
    private fun temporarySetCommands(
        node: CommandNode,
        output: MutableMap<String, String>,
        graph: CommandGraph,
    ): TemporarySetExpansion {
        val type = TemporaryVariableType.parse(
            node.string("tempType", TemporaryVariableType.NUMBER.name),
        ) ?: error("unknown temporary variable type")
        val name = TemporaryTemplate.normalized(node.string("name"))
        val destination = temporaryStoragePath(name)
        val commands = when (type) {
            TemporaryVariableType.LOCATION -> temporaryLocationSetCommands(node, destination)
            TemporaryVariableType.ENTITY -> temporaryEntitySetCommands(node, destination)
            else -> listOf(temporarySetCommand(node))
        }
        // NUMBER/STRINGの値だけでなく、SOUNDの音量・ピッチ、EFFECTのレベル・
        // 持続時間、LOCATIONの動的座標も同じmacro入口へ通します。テンプレートが
        // なければprepareMacroCommandsが元の複数命令をそのまま返すため、静的値の
        // 出力形式は変わりません。
        val macro = prepareMacroCommands(
            node,
            commands,
            output,
            graph,
            functionPrefix = "temporary_macro",
        )
        return macro?.let { TemporarySetExpansion(it.setup, listOf(it.call)) }
            ?: TemporarySetExpansion(emptyList(), commands)
    }

    private data class TemporarySetExpansion(
        val setup: List<String>,
        val commands: List<String>,
    )

    private fun temporaryEntitySetCommands(node: CommandNode, destination: String): List<String> {
        val spec = node.temporaryEntityTargetSpec
            ?: return listOf(temporarySetCommand(node))
        val destinationStorage = "kantan:variables $destination"
        return when (spec.kind) {
            TargetKind.TEMPORARY -> {
                val sourceName = spec.tempName?.takeIf(String::isNotBlank)
                    ?: error("temporary ENTITY source name is missing")
                listOf(
                    "data remove storage $destinationStorage",
                    "execute unless data storage kantan:variables ${temporaryStoragePath(sourceName)}.uuid run return 0",
                    "data modify storage $destinationStorage set from storage kantan:variables ${temporaryStoragePath(sourceName)}",
                )
            }
            TargetKind.FIXED_ENTITY -> error("fixed entity is not supported by vanilla temporary ENTITY")
            else -> listOf(
                // 対象が0体でも定義ノード自体は成功し、空のENTITYとして保存します。
                // 後続の対象解決はUUID未設定を「対象なし」として扱います。
                "data modify storage $destinationStorage set value {}",
                "execute as ${singleSelector(spec)} run data modify storage $destinationStorage.uuid set from entity @s UUID",
            )
        }
    }

    private fun temporaryLocationSetCommands(node: CommandNode, destination: String): List<String> {
        val position = node.temporaryLocationPositionSpec
        val facing = node.temporaryLocationFacingSpec
        if (position == null && facing == null) {
            // 旧POSITION保存値を読み込んだ場合の境界です。新規GUIからはこの経路へ入りません。
            return listOf(temporarySetCommand(node))
        }
        require(position != null) { "temporary LOCATION position is missing" }
        require(facing != null) { "temporary LOCATION facing is missing" }

        fun number(value: Number): String = value.toString().let { raw ->
            if (raw.endsWith("d", ignoreCase = true)) raw else "${raw}d"
        }
        val storage = "kantan:variables $destination"
        val commands = mutableListOf(
            "data modify storage $storage set value {x:0d,y:0d,z:0d,yaw:0d,pitch:0d}",
        )
        when (position.kind) {
            PositionKind.CAPTURED, PositionKind.COORDINATES -> {
                commands += "data modify storage $storage.x set value ${number(position.x ?: error("location x is missing"))}"
                commands += "data modify storage $storage.y set value ${number(position.y ?: error("location y is missing"))}"
                commands += "data modify storage $storage.z set value ${number(position.z ?: error("location z is missing"))}"
            }
            PositionKind.TEMPORARY -> {
                val sourceName = position.tempName?.takeIf(String::isNotBlank)
                    ?: error("temporary LOCATION source name is missing")
                val source = temporaryStoragePath(sourceName)
                commands += "execute unless data storage kantan:variables $source run return 0"
                commands += "data modify storage $storage set from storage kantan:variables $source"
            }
            PositionKind.DISK -> {
                // DISKは実行元の制御ブロック位置を意味します。実行者・対象を
                // GUIから暗黙に読み替える経路は持たせません。
                commands += captureCurrentLocation(storage, node)
            }
            PositionKind.TARGET -> error("temporary LOCATION target position is not supported")
            PositionKind.MYWORLD_SPAWN -> error("temporary LOCATION spawn position is not supported by vanilla")
        }

        when (facing.kind) {
            FacingKind.CAPTURED, FacingKind.ROTATION -> {
                commands += "data modify storage $storage.yaw set value ${number(facing.yaw ?: error("location yaw is missing"))}"
                commands += "data modify storage $storage.pitch set value ${number(facing.pitch ?: error("location pitch is missing"))}"
            }
            FacingKind.TEMPORARY -> {
                val sourceName = facing.tempName?.takeIf(String::isNotBlank)
                    ?: error("temporary LOCATION facing source name is missing")
                val source = temporaryStoragePath(sourceName)
                // FacingSpec.TEMPORARYは参照先の向きをコピーするのではなく、
                // 位置から参照先LOCATIONを見る向きを計算します。実行時と同じ
                // semanticsを保つため、vanillaのexecute facingで一時markerの
                // Rotationを得ます。
                commands += "execute unless data storage kantan:variables $source.x run return 0"
                commands += captureDynamicRotation(
                    storage,
                    node,
                    positionCoordinates(position),
                    temporaryPositionCoordinates(sourceName),
                )
            }
            FacingKind.TARGET -> error("temporary LOCATION target facing is not supported")
            FacingKind.COORDINATES -> {
                val rotation = staticFacingRotation(position, facing)
                if (rotation != null) {
                    commands += "data modify storage $storage.yaw set value ${number(rotation.first)}"
                    commands += "data modify storage $storage.pitch set value ${number(rotation.second)}"
                } else {
                    // 位置が実行時に決まる場合は、現在位置または一時LOCATIONを
                    // execute positionedへ渡し、座標指定先を向くmarkerを作ります。
                    commands += captureDynamicRotation(
                        storage,
                        node,
                        positionCoordinates(position),
                        "${facing.x ?: error("location facing x is missing")} " +
                            "${facing.y ?: error("location facing y is missing")} " +
                            "${facing.z ?: error("location facing z is missing")}",
                    )
                }
            }
            FacingKind.MYWORLD_SPAWN -> error("temporary LOCATION spawn facing is not supported by vanilla")
        }
        return commands
    }

    /** PositionSpecをvanillaのexecute位置引数へ変換します。 */
    private fun positionCoordinates(position: PositionSpec): String = when (position.kind) {
        PositionKind.CAPTURED, PositionKind.COORDINATES ->
            "${position.x ?: error("location x is missing")} " +
                "${position.y ?: error("location y is missing")} " +
                "${position.z ?: error("location z is missing")}"
        PositionKind.TEMPORARY -> temporaryPositionCoordinates(
            position.tempName?.takeIf(String::isNotBlank)
                ?: error("temporary LOCATION source name is missing"),
        )
        PositionKind.DISK -> "~ ~ ~"
        PositionKind.TARGET -> error("temporary LOCATION target position is not supported")
        PositionKind.MYWORLD_SPAWN -> error("temporary LOCATION spawn position is not supported by vanilla")
    }

    private fun captureCurrentLocation(storage: String, node: CommandNode): List<String> {
        val tag = "kc_loc_${shortDigest(node.id.toString(), 12)}"
        val marker = "@e[type=marker,tag=$tag,limit=1]"
        return buildList {
            add("kill @e[type=marker,tag=$tag]")
            add("summon marker ~ ~ ~ {Tags:[\"$tag\"]}")
            add("execute unless entity $marker run return 0")
            add("execute store result storage $storage.x double 1 run data get entity $marker Pos[0] 1")
            add("execute store result storage $storage.y double 1 run data get entity $marker Pos[1] 1")
            add("execute store result storage $storage.z double 1 run data get entity $marker Pos[2] 1")
            add("kill $marker")
        }
    }

    private fun captureCurrentRotation(storage: String): List<String> = listOf(
        "execute store result storage $storage.yaw double 1 run data get entity @s Rotation[0] 1",
        "execute store result storage $storage.pitch double 1 run data get entity @s Rotation[1] 1",
    )

    /** 動的な原点・向き先からvanillaの実行回転を作り、LOCATIONへ保存します。 */
    private fun captureDynamicRotation(
        storage: String,
        node: CommandNode,
        origin: String,
        target: String,
    ): List<String> {
        val tag = "kc_rot_${shortDigest("${node.id}:rotation", 12)}"
        val marker = "@e[type=marker,tag=$tag,limit=1]"
        return listOf(
            "kill @e[type=marker,tag=$tag]",
            "execute positioned $origin facing $target run summon marker ~ ~ ~ {Tags:[\"$tag\"]}",
            // summon時の既定回転に依存せず、execute facingで決まった回転を明示的に
            // markerへ適用します。relativeな~ ~は現在の実行回転を保持します。
            "execute positioned $origin facing $target run tp $marker ~ ~ ~ ~ ~",
            "execute unless entity $marker run return 0",
            "execute store result storage $storage.yaw double 1 run data get entity $marker Rotation[0] 1",
            "execute store result storage $storage.pitch double 1 run data get entity $marker Rotation[1] 1",
            "kill $marker",
        )
    }

    private fun staticFacingRotation(
        position: PositionSpec,
        facing: me.awabi2048.kantancommander.model.FacingSpec,
    ): Pair<Float, Float>? {
        val px = position.x ?: return null
        val py = position.y ?: return null
        val pz = position.z ?: return null
        val tx = facing.x ?: return null
        val ty = facing.y ?: return null
        val tz = facing.z ?: return null
        val dx = tx - px
        val dy = ty - py
        val dz = tz - pz
        val horizontal = kotlin.math.sqrt(dx * dx + dz * dz)
        if (horizontal == 0.0 && dy == 0.0) return 0f to 0f
        return Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat() to
            Math.toDegrees(kotlin.math.atan2(-dy, horizontal)).toFloat()
    }

    private fun lower(node: CommandNode, graph: CommandGraph): String? = when (node.type) {
        CommandType.TELEPORT -> teleportCommand(node)
        CommandType.GIVE_ITEM -> "give ${effectiveTarget(node)} " +
            "${node.itemTempRef?.takeIf(String::isNotBlank)?.let { temporaryMarker(it, "item") } ?: node.string("item")} " +
            node.string("count", "1")
        CommandType.ENTITY_ACTION -> when (node.string("action")) {
            "dismount" -> "ride ${effectiveTarget(node)} dismount"
            "equip" -> "item replace entity ${effectiveTarget(node)} ${equipmentSlot(node.string("slot"))} with " +
                (node.itemTempRef?.takeIf(String::isNotBlank)?.let { temporaryMarker(it, "item") } ?: node.string("item"))
            "tag" -> "tag ${effectiveTarget(node)} ${node.string("tagOperation", "add")} ${node.string("tag")}"
            else -> "ride ${effectiveTarget(node)} mount ${singleSelector(requireNotNull(node.secondaryTargetSpec))}"
        }
        CommandType.DISPLAY_TEXT -> when (node.string("mode", "tellraw")) {
            "title" -> "title ${effectiveTarget(node)} title {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
            "subtitle" -> "title ${effectiveTarget(node)} subtitle {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
            "actionbar" -> "title ${effectiveTarget(node)} actionbar {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
            else -> "tellraw ${effectiveTarget(node)} {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
        }
        CommandType.SUMMON_ENTITY -> summonCommand(node)
        CommandType.PLAY_SOUND -> soundCommand(node)
        CommandType.PARTICLE -> particleCommand(node)
        CommandType.APPLY_EFFECT ->
            "effect give ${effectiveTarget(node)} " +
                (node.effectTempRef?.takeIf(String::isNotBlank)?.let { temporaryMarker(it, "effect") } ?: node.string("effect")) +
                " ${node.effectTempRef?.takeIf(String::isNotBlank)?.let { temporaryMarker(it, "seconds") } ?: node.string("seconds", "30")} " +
                effectAmplifier(node.effectTempRef?.takeIf(String::isNotBlank)?.let { temporaryMarker(it, "level") } ?: node.string("level", "1"))
        CommandType.CAMERA_SHAKE -> null
        CommandType.BLOCK_OPERATION -> blockOperationCommand(node)
        CommandType.ENTITY_DELETE -> "kill ${effectiveTarget(node)}"
        CommandType.DISK_CALL -> null
        CommandType.VARIABLE -> lowerVariable(node, graph)
        // TEMP_SETはcompileGraph側で専用関数へ展開します。ここへ到達させると
        // 複数命令のLOCATION/ENTITY定義が1命令として扱われるため、防御的に無出力とします。
        CommandType.TEMP_SET -> null
        CommandType.WAIT, CommandType.CONDITION, CommandType.MERGE,
        CommandType.FOR_START, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> null
    }

    /** SUMMON_ENTITYのコマンド固有位置を、summonの座標またはexecute atへ変換します。 */
    private fun summonCommand(node: CommandNode): String {
        // 召喚タグは一つの文字列としてNBTへ一要素だけを書き込みます。
        // 入力中のカンマを複数タグの区切りとして再解釈しません。
        val tag = node.string("tags").takeIf(String::isNotBlank)
        val tagNbt = tag?.let { "Tags:[\\\"${escape(it)}\\\"]" }.orEmpty()
        val customName = node.string("customName").takeIf(String::isNotBlank)
            ?.let { "CustomName:\"{\\\"text\\\":\\\"${escape(it.replace('&', '§'))}\\\"}\"" }
        val nbtFields = listOfNotNull(tagNbt.takeIf(String::isNotBlank), customName)
        val nbt = nbtFields.takeIf { it.isNotEmpty() }?.let { " {${it.joinToString(",")}}" }.orEmpty()
        val entity = node.string("entity")
        val summon = { coordinates: String -> "summon $entity $coordinates$nbt" }
        return when (val position = node.summonPositionSpec) {
            null -> summon("~ ~ ~")
            else -> when (position.kind) {
                PositionKind.CAPTURED, PositionKind.COORDINATES -> summon(
                    "${position.x ?: error("summon x is missing")} " +
                        "${position.y ?: error("summon y is missing")} " +
                        "${position.z ?: error("summon z is missing")}",
                )
                PositionKind.DISK -> summon("~ ~ ~")
                // TARGETはテレポート先だけのコマンド固有値です。召喚位置で
                // 対象欄を暗黙利用する旧コンテキスト解釈は受け付けません。
                PositionKind.TARGET -> error("summon target position is not supported")
                PositionKind.TEMPORARY -> summon(
                    temporaryPositionCoordinates(
                        position.tempName ?: error("temporary summon position name is missing"),
                    ),
                )
                PositionKind.MYWORLD_SPAWN -> error("unsupported summon position")
            }
        }
    }

    /** 動的な文字列・数値入力はJava版のfunction macroへ一元的に変換します。 */
    private fun prepareMacro(
        node: CommandNode,
        command: String,
        output: MutableMap<String, String>,
        graph: CommandGraph,
    ): MacroCommand = prepareMacroCommands(node, listOf(command), output, graph)
        ?: MacroCommand(emptyList(), command)

    /** 複数命令を一つのfunction macroへまとめ、同じ実行時値を共有します。 */
    private fun prepareMacroCommands(
        node: CommandNode,
        commands: List<String>,
        output: MutableMap<String, String>,
        graph: CommandGraph,
        functionPrefix: String = "macro",
    ): MacroCommand? {
        val command = commands.joinToString("\n")
        val template = prepareMacroTemplate(node, command, graph) ?: return null
        val macroName = nodeFunction(functionPrefix, node.id)
        // function macroは命令行ごとに`$`を付けて初めて値展開されます。
        // LOCATIONの現位置捕捉のように複数行を使う場合も、全行を同じmacro
        // storageから評価して、原子的な一時値定義として実行します。
        val macroBody = template.command
            .split('\n')
            .joinToString("\n") { line -> "${'$'}$line" }
        defineFunction(output, macroName, macroBody + "\n")
        return MacroCommand(template.setup, "function kantan:$macroName with storage kantan:variables ${template.storagePath}")
    }

    /** 条件分岐のpredicateなど、関数化せず置換文字列だけが必要な箇所にも使います。 */
    private fun prepareMacroTemplate(node: CommandNode, command: String, graph: CommandGraph): MacroTemplate? {
        val references = VariableTemplate.references(command)
        val temporaryReferences = TemporaryTemplate.references(command)
        val temporaryMarkers = TEMPORARY_MACRO_MARKER.findAll(command).map { it.value }.toSet()
        if (references.isEmpty() && temporaryReferences.isEmpty() && temporaryMarkers.isEmpty()) return null
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
        val worldReplacements = references.associateWith { "v_${shortDigest(it, 10)}" }
        val temporaryReplacements = temporaryReferences.associateWith { "t_${shortDigest(it, 10)}" }
        val markerReplacements = temporaryMarkers.associateWith { "m_${shortDigest(it, 10)}" }
        val macroCommand = TEMPORARY_MACRO_MARKER.replace(command) { match ->
            "${'$'}(${markerReplacements.getValue(match.value)})"
        }.let { withoutMarkers ->
            TEMPORARY_REFERENCE.replace(withoutMarkers) { match ->
                "${'$'}(${temporaryReplacements.getValue(match.groupValues[1])})"
            }
        }.let { withoutTemporaryReferences ->
            Regex("\\$\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}").replace(withoutTemporaryReferences) { match ->
                "${'$'}(${worldReplacements.getValue(match.groupValues[1])})"
            }
        }
        val setup = buildList {
            add("data remove storage kantan:variables $storagePath")
            references.forEach { reference ->
                val target = "$storagePath.${worldReplacements.getValue(reference)}"
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
            temporaryReferences.forEach { reference ->
                add(
                    "data modify storage kantan:variables $storagePath.${temporaryReplacements.getValue(reference)} " +
                        "set from storage kantan:variables ${temporaryStoragePath(reference)}",
                )
            }
            temporaryMarkers.forEach { marker ->
                val match = TEMPORARY_MACRO_MARKER.matchEntire(marker)
                    ?: error("invalid temporary macro marker: $marker")
                val name = match.groupValues[1]
                val field = match.groupValues[2]
                add(
                    "data modify storage kantan:variables $storagePath.${markerReplacements.getValue(marker)} " +
                        "set from storage kantan:variables ${temporaryStoragePath(name)}.$field",
                )
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
                    TemporaryTemplate.references(raw).singleOrNull()?.let { reference ->
                        add(
                            "execute store result score ${conditionValueHolder(node)} kc_runtime run data get storage " +
                                "kantan:variables ${temporaryStoragePath(reference)} $FIXED_POINT_SCALE",
                        )
                    }
                }
            }.joinToString("\n")
        } else null

    private fun conditionCountHolder(node: CommandNode) =
        "#c_${node.id.toString().replace("-", "")}"

    private fun conditionValueHolder(node: CommandNode) =
        "#cv_${node.id.toString().replace("-", "")}"

    private fun conditionTargetFoundHolder(node: CommandNode) =
        "#ct_${node.id.toString().replace("-", "")}"

    private fun conditionNeedsInversion(node: CommandNode): Boolean =
        node.string("kind") == ConditionKind.VARIABLE_STATE.name && node.string("operator") == "!="

    private fun comparisonOperator(node: CommandNode): String =
        node.string("operator").takeUnless { it == "!=" } ?: "=="

    private fun conditionTarget(node: CommandNode): String {
        val spec = node.targetSpec ?: error("condition target is missing")
        return selector(spec.copy(limit = 1))
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
                        token.temporary -> lines +=
                            "execute store result score $destination kc_runtime run data get storage " +
                                "kantan:variables ${temporaryStoragePath(token.name)} $FIXED_POINT_SCALE"
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
        val temporaryReference = TemporaryTemplate.references(raw).singleOrNull()
        if (temporaryReference != null && TemporaryTemplate.isSingleReference(raw)) {
            return "execute store result score $destination kc_vars run data get storage " +
                "kantan:variables ${temporaryStoragePath(temporaryReference)} 1"
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

    /**
     * グラフ内で使う一時変数名を収集します。
     *
     * exporterのstorageは実行開始時に空にします。名前を事前に把握しておくと、
     * 旧形式の値や個別フィールドが残った場合も、ルート関数の初期化で確実に
     * 消去できます。snapshotの値も同じ実行の一部なので再帰的に走査します。
     */
    private fun temporaryNames(graph: CommandGraph): Set<String> = linkedSetOf<String>().also { names ->
        fun add(raw: String?) {
            raw?.takeIf(String::isNotBlank)?.let { names += TemporaryTemplate.normalized(it) }
        }
        fun scan(current: CommandGraph) {
            current.nodes.values.forEach { node ->
                if (node.type == CommandType.TEMP_SET) add(node.string("name"))
                node.params.values.forEach { raw ->
                    TemporaryTemplate.references(raw).forEach(::add)
                }
                add(node.itemTempRef)
                add(node.blockTempRef)
                add(node.soundTempRef)
                add(node.effectTempRef)
                listOfNotNull(
                    node.targetSpec,
                    node.secondaryTargetSpec,
                    node.destinationTargetSpec,
                ).forEach { target ->
                    if (target.kind == TargetKind.TEMPORARY) add(target.tempName)
                    target.searchOrigin?.let { search ->
                        add(search.positionTemp)
                        if (search.position?.kind == PositionKind.TEMPORARY) add(search.position.tempName)
                    }
                }
                listOfNotNull(
                    node.destinationSpec,
                    node.conditionPositionSpec,
                    node.blockPositionSpec,
                    node.blockFromSpec,
                    node.blockToSpec,
                    node.soundPositionSpec,
                    node.particlePositionSpec,
                    node.summonPositionSpec,
                ).forEach { position -> if (position.kind == PositionKind.TEMPORARY) add(position.tempName) }
                listOfNotNull(
                    node.temporaryLocationPositionSpec,
                ).forEach { position -> if (position.kind == PositionKind.TEMPORARY) add(position.tempName) }
                listOfNotNull(
                    node.destinationFacingSpec,
                ).forEach { facing -> if (facing.kind == FacingKind.TEMPORARY) add(facing.tempName) }
                node.temporaryLocationFacingSpec
                    ?.takeIf { it.kind == FacingKind.TEMPORARY }
                    ?.let { add(it.tempName) }
                node.temporaryEntityTargetSpec?.let { target ->
                    if (target.kind == TargetKind.TEMPORARY) add(target.tempName)
                    target.searchOrigin?.let { search ->
                        add(search.positionTemp)
                        if (search.position?.kind == PositionKind.TEMPORARY) add(search.position.tempName)
                    }
                }
                node.snapshot?.let(::scan)
            }
        }
        scan(graph)
    }

    private fun effectiveTarget(node: CommandNode): String {
        val spec = requireNotNull(node.targetSpec)
        return if (spec.kind == TargetKind.TEMPORARY) "@s" else selector(spec)
    }

    private data class TemporaryEntityReference(
        val name: String,
        val role: String,
        val holderPrefix: String,
    ) {
        fun holder(index: Int): String = "$holderPrefix$index"
    }

    /**
     * ノードから実行時ENTITY参照を収集します。
     *
     * 一時ENTITYはセレクターへUUIDを直接埋め込めないため、UUIDの4要素を
     * scoreboardへ写してから `execute as @e if score ...` で選択します。通常の対象欄と
     * 実行エンジン内部の対象状態は同じノード内で共存できるため、名前ごとにscore
     * holderを分け、片方の準備がもう片方の比較値を上書きしないようにします。
     */
    private fun temporaryEntityReferences(
        node: CommandNode,
        context: ExecutionContextSpec?,
    ): List<TemporaryEntityReference> = buildList {
        fun add(role: String, spec: TargetSpec?) {
            if (spec?.kind != TargetKind.TEMPORARY) return
            val name = spec.tempName?.takeIf(String::isNotBlank) ?: return
            val normalized = TemporaryTemplate.normalized(name)
            val holderPrefix = if (role == "primary") {
                "#kc_temp_uuid"
            } else {
                "#kc_temp_${shortDigest("${node.id}:$role:$normalized", 12)}_"
            }
            add(TemporaryEntityReference(normalized, role, holderPrefix))
        }
        add("primary", node.targetSpec)
        add("secondary", node.secondaryTargetSpec)
        add("destination", node.destinationTargetSpec)
        add("runtime", context?.target ?: context?.executor)
    }.distinctBy { it.role to it.name }

    /** コマンド引数として同時に使われる一時ENTITY参照です。 */
    private fun temporaryEntityArgumentReference(node: CommandNode): TemporaryEntityReference? =
        temporaryEntityReferences(node, null)
            .firstOrNull { it.role == "primary" || it.role == "secondary" || it.role == "destination" }

    /** 条件対象の `%{...}%` 相当となる一時ENTITY参照です。 */
    private fun temporaryConditionTargetReference(
        node: CommandNode,
        context: ExecutionContextSpec?,
    ): TemporaryEntityReference? {
        // CONDITIONの条件対象として実際に効くTARGET_EXISTS／PLAYER_STATEだけを
        // 対象にし、変数・ブロック条件の無関係な内部対象状態で分岐を変えません。
        val conditionKind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrNull()
        if (conditionKind !in setOf(ConditionKind.TARGET_EXISTS, ConditionKind.PLAYER_STATE)) return null
        val explicitTarget = node.targetSpec
        val spec = explicitTarget ?: context?.target ?: context?.executor ?: return null
        if (spec.kind != TargetKind.TEMPORARY) return null
        val role = if (explicitTarget != null) "primary" else "runtime"
        return temporaryEntityReferences(node, context).firstOrNull { it.role == role }
    }

    /** 一時ENTITYのUUIDをscoreboardへ写し、現在ディメンションの実体を選択します。 */
    private fun temporaryEntityPreparation(reference: TemporaryEntityReference): List<String> {
        val path = "kantan:variables ${temporaryStoragePath(reference.name)}.uuid"
        // data getが対象なしで失敗した場合にも、前回のUUIDを比較値として残さない
        // よう、読み取り前に4要素をすべて0へ戻します。
        return (0..3).flatMap { index ->
            listOf(
                "scoreboard players set ${reference.holder(index)} kc_tu$index 0",
                "execute store result score ${reference.holder(index)} kc_tu$index run data get storage $path[$index] 1",
            )
        } + listOf(
            "execute as @e store result score @s kc_tu0 run data get entity @s UUID[0] 1",
            "execute as @e store result score @s kc_tu1 run data get entity @s UUID[1] 1",
            "execute as @e store result score @s kc_tu2 run data get entity @s UUID[2] 1",
            "execute as @e store result score @s kc_tu3 run data get entity @s UUID[3] 1",
        )
    }

    private fun temporaryEntitySelection(reference: TemporaryEntityReference, command: String): String {
        val checks = (0..3).joinToString(" ") { index ->
            "if score @s kc_tu$index = ${reference.holder(index)} kc_tu$index"
        }
        return "execute as @e $checks run $command"
    }

    /** 一時ENTITYが現在ディメンションに存在するかをフラグへ反映します。 */
    private fun temporaryEntityPresence(
        reference: TemporaryEntityReference,
        foundHolder: String,
    ): String {
        val checks = (0..3).joinToString(" ") { index ->
            "if score @s kc_tu$index = ${reference.holder(index)} kc_tu$index"
        }
        return "execute as @e $checks run scoreboard players set $foundHolder kc_runtime 1"
    }

    private fun destination(node: CommandNode): String {
        node.destinationTargetSpec?.let { return singleSelector(it) }
        return when (val spec = node.destinationSpec) {
            null -> error("structured teleport destination is missing")
            else -> when (spec.kind) {
                PositionKind.CAPTURED, PositionKind.COORDINATES ->
                    "${spec.x ?: "~"} ${spec.y ?: "~"} ${spec.z ?: "~"}"
                PositionKind.DISK -> "~ ~ ~"
                PositionKind.TARGET -> error("teleport target position must use destinationTargetSpec")
                PositionKind.TEMPORARY -> temporaryPositionCoordinates(
                    spec.tempName ?: error("temporary teleport destination name is missing"),
                )
                PositionKind.MYWORLD_SPAWN -> error("unsupported structured teleport destination")
            }
        }
    }

    /** ブロック操作固有の位置指定を、座標または実行位置へ静的に展開します。 */
    private fun blockOperationCommand(node: CommandNode): String {
        val block = node.blockTempRef?.takeIf(String::isNotBlank)
            ?.let { temporaryMarker(it, "block") }
            ?: node.string("block")
        return when (BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))) {
            BlockOperationMode.SETBLOCK -> {
                val position = requireNotNull(node.blockPositionSpec)
                val anchor = blockAnchor(position)
                "${anchor.prefix}setblock ${anchor.coordinates} $block"
            }
            BlockOperationMode.FILL -> {
                val from = blockAnchor(requireNotNull(node.blockFromSpec))
                val to = blockAnchor(requireNotNull(node.blockToSpec))
                require(from.prefix == to.prefix) {
                    "fillの始点と終点は同じ基準位置で指定してください"
                }
                "${from.prefix}fill ${from.coordinates} ${to.coordinates} $block"
            }
            null -> error("unknown block operation")
        }
    }

    private data class BlockAnchor(val prefix: String, val coordinates: String)

    private fun blockAnchor(spec: PositionSpec): BlockAnchor = when (spec.kind) {
        PositionKind.CAPTURED, PositionKind.COORDINATES -> BlockAnchor(
            "",
            "${spec.x ?: error("block x is missing")} ${spec.y ?: error("block y is missing")} ${spec.z ?: error("block z is missing")}",
        )
        PositionKind.DISK -> BlockAnchor("", "~ ~ ~")
        PositionKind.TARGET -> error("block target position is not supported")
        PositionKind.TEMPORARY -> BlockAnchor(
            "",
            temporaryPositionCoordinates(spec.tempName ?: error("temporary block position name is missing")),
        )
        PositionKind.MYWORLD_SPAWN -> error("unsupported structured block position")
    }

    private fun conditionContext(node: CommandNode): ExecutionContextSpec? {
        val position = node.conditionPositionSpec ?: return null
        return ExecutionContextSpec(position = position)
    }

    private fun wrapContext(
        context: ExecutionContextSpec,
        command: String,
        node: CommandNode? = null,
    ): String {
        val contextTarget = context.target ?: context.executor
        val temporaryContextTarget = if (contextTarget?.kind == TargetKind.TEMPORARY && node != null) {
            temporaryEntityReferences(node, context).firstOrNull { it.role == "runtime" }
        } else null
        val clauses = buildList {
            // 一時ENTITYはselectorへUUIDを埋め込めないため、最後に専用の
            // execute asラッパーを付けます。ここで `as @s` を先に追加すると、
            // 元の実行者を参照してしまい、未ロード時のスキップも検出できません。
            if (temporaryContextTarget == null) {
                contextTarget?.let { add("as ${selector(it)}") }
            }
            context.position?.let {
                when (it.kind) {
                    PositionKind.COORDINATES, PositionKind.CAPTURED -> add("positioned ${it.x} ${it.y} ${it.z}")
                    PositionKind.TARGET -> context.target?.let { target -> add("at ${selector(target)}") }
                    PositionKind.TEMPORARY -> it.tempName?.let { name -> add("positioned ${temporaryPositionCoordinates(name)}") }
                    else -> Unit
                }
            }
            context.facing?.let { facing ->
                when (facing.kind) {
                    FacingKind.TARGET -> context.target?.let { add("facing entity ${selector(it)} eyes") }
                    FacingKind.COORDINATES -> add("facing ${facing.x} ${facing.y} ${facing.z}")
                    FacingKind.TEMPORARY -> facing.tempName?.let { name -> add("facing ${temporaryPositionCoordinates(name)}") }
                    FacingKind.ROTATION, FacingKind.CAPTURED -> add("rotated ${facing.yaw} ${facing.pitch}")
                    else -> Unit
                }
            }
        }
        val wrapped = if (clauses.isEmpty()) command else "execute ${clauses.joinToString(" ")} run $command"
        return temporaryContextTarget?.let { temporaryEntitySelection(it, wrapped) } ?: wrapped
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
                .append(":temporaryEntity=").append(node.temporaryEntityTargetSpec)
                .append(":temporaryLocationPosition=").append(node.temporaryLocationPositionSpec)
                .append(":temporaryLocationFacing=").append(node.temporaryLocationFacingSpec)
                .append(":conditionPosition=").append(node.conditionPositionSpec)
                .append(":soundPosition=").append(node.soundPositionSpec)
                .append(":particlePosition=").append(node.particlePositionSpec)
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
        if (spec.kind == TargetKind.TEMPORARY) return "@s"
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

    private fun singleSelector(spec: TargetSpec): String = selector(spec.copy(limit = 1))

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

    private fun temporaryMarker(name: String, field: String): String =
        "@{temp.${TemporaryTemplate.normalized(name)}.$field}"

    private fun uuidIntArray(uuid: UUID): String {
        val most = uuid.mostSignificantBits
        val least = uuid.leastSignificantBits
        return "[I;${(most shr 32).toInt()},${most.toInt()},${(least shr 32).toInt()},${least.toInt()}]"
    }

    private companion object {
        const val FIXED_POINT_SCALE = 1000L
        const val EXPORT_VARIABLE_TYPE = "_exportVariableType"
        const val EXPORT_CAPTURE_FUNCTION = "_exportCaptureFunction"
        val VANILLA_INTEGER_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        val TEMPORARY_REFERENCE = Regex("%\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}%")
        val TEMPORARY_MACRO_MARKER = Regex("@\\{temp\\.([A-Za-z][A-Za-z0-9_.-]{0,63})\\.([A-Za-z][A-Za-z0-9_.-]{0,32})}")
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

    fun callBackupPath(nodeId: UUID): String =
        "execution.temporary_backup_${nodeId.toString().replace("-", "").take(24)}"
}
