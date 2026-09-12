package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.FoldedGraphs
import me.awabi2048.kantancommander.data.GraphLimits
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.DisplayTextTiming
import me.awabi2048.kantancommander.model.DisplayTextTimingPolicy
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.variableScopeStrict
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.PlaceItemDestination
import me.awabi2048.kantancommander.model.FireworkSettings
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
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
        worldValueTypes: Map<String, ValueType> = emptyMap(),
        entryFunctionName: String = root.id.toString(),
    ): StandaloneCompilation {
        val exportRoot = root.copy(graph = FoldedGraphs.expand(root.graph.deepCopy()))
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val variableDefinitions = worldValueTypes.mapValues { (_, type) ->
            if (type == ValueType.NUMBER) TypedValue(type, numberValue = 0.0)
            else TypedValue(type, stringValue = "")
        }
        errors += ExecutableScriptValidator.validate(exportRoot, graphLimits, variableDefinitions).map { it.rendered() }
        collectWarnings(exportRoot.graph, "root", warnings)
        annotateValueTypes(exportRoot.graph, worldValueTypes, errors)
        validate(exportRoot, errors, Collections.newSetFromMap(IdentityHashMap()), 0, worldValueTypes)
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
        worldValueTypes: Map<String, ValueType>,
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
                    // 動き設定は単一バニラコマンドへ正確に変換できないため、
                    // 不正確な変換を行わず未対応として明示します。
                    "motion" -> errors += "${script.id}/${node.id}: 動き設定はバニラ出力に未対応です"
                    // 旧equipはPLACE_ITEM、旧tagはENTITY_OPERATIONへ移行済みのため、
                    // ここでは扱いません。
                    else -> errors += "${script.id}/${node.id}: プラグイン固有のエンティティ操作です"
                }
                CommandType.ENTITY_OPERATION -> when (node.string("operation", "tag")) {
                    "tag" -> Unit
                    "attribute" -> {
                        if (!EntityAttributeSupport.isAttributeId(node.string(EntityAttributeSupport.PARAM_ATTRIBUTE))) {
                            errors += "${script.id}/${node.id}: バニラに存在しないattributeです"
                        }
                    }
                    // 型付きデータ編集はバニラの等価表現へ正確に変換できないため、
                    // 不正確な変換を行わず未対応として明示します。
                    "data" -> errors += "${script.id}/${node.id}: データ編集はバニラ出力に未対応です"
                    else -> errors += "${script.id}/${node.id}: プラグイン固有のエンティティ操作です"
                }
                CommandType.PLACE_ITEM -> validatePlaceItemExport(script, node, errors)
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
                // #30: 図形表示はバニラに同等概念がないため、CommandType単位で明示エラーにします。
                CommandType.PARTICLE_GEOMETRY -> {
                    errors += "${script.id}/${node.id}: 図形表示は完全バニラ出力に未対応です"
                }
                CommandType.FIREWORK_LAUNCH -> {
                    val item = node.string(FireworkSettings.PARAM_ITEM)
                    val data = node.string(FireworkSettings.PARAM_DATA)
                    if (item.isBlank() && data.isBlank()) {
                        errors += "${script.id}/${node.id}: 花火の中身が未設定です"
                    } else if (item.isNotBlank() &&
                        (!item.startsWith("minecraft:") || !FireworkSettings.isFireworkMaterial(item))
                    ) {
                        errors += "${script.id}/${node.id}: 完全バニラ出力できない花火アイテムです: $item"
                    }
                    if (data.isNotBlank() && ItemStackCodec.decode(data) == null) {
                        errors += "${script.id}/${node.id}: 保存された花火ItemStackを復元できません"
                    }
                    val heightRaw = node.string(FireworkSettings.PARAM_HEIGHT, FireworkSettings.HEIGHT_DEFAULT.toString())
                    if (heightRaw.toDoubleOrNull()?.isFinite() != true) {
                        errors += "${script.id}/${node.id}: 打ち上げ高さは出力時に確定数値である必要があります"
                    }
                }
                CommandType.BLOCK_OPERATION -> {
                    val block = CommandValueRules.placementMaterial(node.string("block"))
                    if (node.blockTempRef.isNullOrBlank() &&
                        (!node.string("block").startsWith("minecraft:") || block == null)
                    ) {
                        errors += "${script.id}/${node.id}: 完全バニラ出力できない配置ブロックです"
                    }
                    // issue #26: コマンド固有位置が未設定のときは実行位置を既定値とします。
                    when (BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))) {
                        BlockOperationMode.SETBLOCK -> {
                            val position = effectiveBlockPosition(node)
                            if (position == null) {
                                errors += "${script.id}/${node.id}: ブロック配置位置が未設定です"
                            }
                            validatePosition(script, node, position, errors)
                        }
                        BlockOperationMode.FILL -> {
                            val from = effectiveFillFrom(node)
                            if (from == null) {
                                errors += "${script.id}/${node.id}: 範囲配置の始点が未設定です"
                            }
                            val to = effectiveFillTo(node)
                            if (to == null) {
                                errors += "${script.id}/${node.id}: 範囲配置の終点が未設定です"
                            }
                            validatePosition(script, node, from, errors)
                            validatePosition(script, node, to, errors)
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
                        validate(script.copy(graph = node.snapshot!!), errors, visited, callDepth + 1, worldValueTypes)
                }
                CommandType.CONDITION -> validateCondition(script, node, errors, worldValueTypes)
                CommandType.VARIABLE -> {
                    if (node.string("name").isBlank()) errors += "${script.id}/${node.id}: variable name is missing"
                    val scope = node.variableScopeStrict()
                    if (scope == null) errors += "${script.id}/${node.id}: 変数の範囲が不正です"
                    if (scope == VariableScope.ENTITY) {
                        errors += "${script.id}/${node.id}: エンティティ変数は完全バニラ出力に未対応です"
                    }
                    val operation = runCatching {
                        VariableOperation.valueOf(node.string("operation", VariableOperation.DEFINE.name))
                    }.getOrNull()
                    val type = runCatching {
                        val rawType = if (operation == VariableOperation.CHANGE) {
                            node.params[EXPORT_VARIABLE_TYPE] ?: node.string("type")
                        } else {
                            node.string("type")
                        }
                        ValueType.valueOf(rawType)
                    }.getOrNull()
                    if (type == null || operation == null) errors += "${script.id}/${node.id}: 変数型または操作が不正です"
                    if (operation == VariableOperation.CHANGE &&
                        node.string("changeMode", VariableChangeMode.ASSIGN.name) == VariableChangeMode.CALCULATE.name &&
                        type != ValueType.NUMBER
                    ) errors += "${script.id}/${node.id}: 文字列変数へ計算式を適用できません"
                    // #3: べき乗・三角関数はバニラscoreboardに同等概念がないため明示エラーにします。
                    if (scope == VariableScope.WORLD &&
                        operation == VariableOperation.CHANGE &&
                        node.string("changeMode", VariableChangeMode.ASSIGN.name) == VariableChangeMode.CALCULATE.name &&
                        type == ValueType.NUMBER
                    ) {
                        val parsed = NumericExpression.parse(node.string("value")).expression
                        if (parsed == null || !parsed.isVanillaLowerable()) {
                            errors += "${script.id}/${node.id}: べき乗・三角関数を含む計算式は完全バニラ出力に未対応です"
                        }
                    }
                    if (operation == VariableOperation.COPY && node.string("source").isBlank()) {
                        errors += "${script.id}/${node.id}: 複写元変数名が未設定です"
                    }
                    // #3: 詳細値取得・乱数は実行時の解決が必要なため、バニラには
                    // 静的な同等概念がありません。明示エラーにします。
                    if (node.string("changeMode") == VariableChangeMode.PROPERTY.name ||
                        node.string("changeMode") == VariableChangeMode.RANDOM.name
                    ) {
                        errors += "${script.id}/${node.id}: 詳細値取得・乱数は完全バニラ出力に未対応です"
                    }
                    // #2: 単体出力の初期値埋め込みとWORLD代入はスカラー3型だけに対応します。
                    // 複合型のWORLD変数はバニラstorageへ直結しないため明示エラーにします。
                    // EXECUTION scope の複合値は一時storage compoundで扱うため対象外です。
                    if (scope != VariableScope.EXECUTION &&
                        type != null &&
                        type != ValueType.NUMBER && type != ValueType.STRING && type != ValueType.BOOLEAN
                    ) {
                        errors += "${script.id}/${node.id}: 複合型の変数は完全バニラ出力に未対応です"
                    }
                    // #2: 実行内計算式のvanilla展開はWORLD格納だけに対応します。
                    if (scope == VariableScope.EXECUTION &&
                        operation == VariableOperation.CHANGE &&
                        node.string("changeMode", VariableChangeMode.ASSIGN.name) == VariableChangeMode.CALCULATE.name
                    ) {
                        errors += "${script.id}/${node.id}: 実行内変数の計算式は完全バニラ出力に未対応です"
                    }
                    if (scope == VariableScope.EXECUTION) {
                        validateExecutionValueCommands(node, script, errors)
                    }
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
                        worldValueTypes[reference] != ValueType.NUMBER
                    ) {
                        errors += "${script.id}/${node.id}: forの回数参照変数は数値型である必要があります: $reference"
                    }
                }
                CommandType.WAIT -> {
                    errors += "${script.id}/${node.id}: 待機は実行者と実行位置を保持できないため完全バニラ出力できません"
                }
                CommandType.TEMP_SET -> {
                    validateComplexValuePosition(
                        node,
                        script,
                        errors,
                        ValueType.parse(node.string("tempType")),
                    )
                }
                else -> Unit
            }
            validatePosition(script, node, node.conditionPositionSpec, errors)
            validatePosition(script, node, node.soundPositionSpec, errors)
            validatePosition(script, node, node.particlePositionSpec, errors)
            validatePosition(script, node, node.particleEndPositionSpec, errors)
            validatePosition(script, node, node.fireworkPositionSpec, errors)
            validatePosition(script, node, node.summonPositionSpec, errors)
            // issue #26: 実行位置も同じ制約で検証します。未設定時は従来どおり
            // 制御ブロック位置で動作するため、出力可否に影響しません。
            validatePosition(script, node, node.executionPositionSpec, errors)
            if (node.executionPositionSpec?.kind == PositionKind.TARGET) {
                errors += "${script.id}/${node.id}: 実行位置の対象位置指定は完全バニラ出力に未対応です"
            }
            // issue #29: 単一限定の役割は、実行時と同じく複数になり得る対象由来を拒否します。
            // 静的0..1の指定は先頭1件へ解決するため出力できます。
            // テレポート先は旧来の対象移動経路のため除外します。
            listOfNotNull(
                node.blockFromSpec,
                node.blockToSpec,
                node.particleEndPositionSpec,
                node.temporaryLocationPositionSpec,
            ).filter { it.kind == PositionKind.TARGET && (it.target == null || it.target.isMultiCapable()) }
                .forEach { errors += "${script.id}/${node.id}: この位置に対象由来の複数位置は指定できません" }
            // issue #29: fillの始点と終点が異なる対象由来の場合は1命令で表現できません。
            // 未設定の片端は実行位置を既定値とするため、既定値込みで比較します。
            val fillFromTarget = effectiveFillFrom(node)
                ?.takeIf { it.kind == PositionKind.TARGET }?.target
                ?.takeIf { it.kind != TargetKind.TEMPORARY }
            val fillToTarget = effectiveFillTo(node)
                ?.takeIf { it.kind == PositionKind.TARGET }?.target
                ?.takeIf { it.kind != TargetKind.TEMPORARY }
            if (node.type == CommandType.BLOCK_OPERATION &&
                BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value)) ==
                BlockOperationMode.FILL &&
                fillFromTarget != null && fillToTarget != null && fillFromTarget != fillToTarget
            ) {
                errors += "${script.id}/${node.id}: 始点と終点で異なる対象由来位置は完全バニラ出力に未対応です"
            }
            // issue #29: fillで片端だけがDISK（制御ブロック位置）の場合、
            // もう片端の対象由来へ execute at で包むとDISK側の相対座標まで
            // ずれるため、1命令では表現できません。未設定の片端は実行位置を
            // 既定値とするため、既定値込みで比較します。
            if (node.type == CommandType.BLOCK_OPERATION &&
                BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value)) ==
                BlockOperationMode.FILL
            ) {
                val fromDisk = effectiveFillFrom(node)?.kind == PositionKind.DISK
                val toDisk = effectiveFillTo(node)?.kind == PositionKind.DISK
                val fromEmbedded = effectiveFillFrom(node)
                    ?.takeIf { it.kind == PositionKind.TARGET }?.target
                    ?.takeIf { it.kind != TargetKind.TEMPORARY }
                val toEmbedded = effectiveFillTo(node)
                    ?.takeIf { it.kind == PositionKind.TARGET }?.target
                    ?.takeIf { it.kind != TargetKind.TEMPORARY }
                if ((fromDisk && toEmbedded != null) || (toDisk && fromEmbedded != null)) {
                    errors += "${script.id}/${node.id}: 始点と終点で基準位置（制御ブロック位置と対象由来）が異なるfillは完全バニラ出力に未対応です"
                }
            }
            // issue #29: 対象由来の扱いを表現可能性で分けます。
            // - MyWorld固有・実行位置の対象由来：表現不能のため明示エラーとします。
            // - 内包対象のない対象由来：解決不能な旧データのため明示エラーとします。
            // - 一時変数内包：実行側の一時実体とvanillaの実行者が一致しないため未対応とします。
            // - 条件分岐の複数位置：ANY評価の蓄積出力が未実装のため明示エラーとします。
            // - 上記以外（各コマンドの対象由来・静的単一・向き内包）は at 包み等で出力します。
            // 別の位置へ黙って読み替えず、出力時だけ異なる意味になりません。
            val bareTargets = listOfNotNull(
                node.conditionPositionSpec,
                node.blockPositionSpec,
                node.blockFromSpec,
                node.blockToSpec,
                node.soundPositionSpec,
                node.particlePositionSpec,
                node.particleEndPositionSpec,
                node.fireworkPositionSpec,
                node.summonPositionSpec,
                node.temporaryLocationPositionSpec,
            ).filter { it.kind == PositionKind.TARGET && it.target == null }
            if (bareTargets.isNotEmpty()) {
                errors += "${script.id}/${node.id}: 対象由来位置に対象の指定がありません"
            }
            val embeddedTargets = listOfNotNull(
                node.conditionPositionSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.blockPositionSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.blockFromSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.blockToSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.soundPositionSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.particlePositionSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.particleEndPositionSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.fireworkPositionSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.summonPositionSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.temporaryLocationPositionSpec?.takeIf { it.kind == PositionKind.TARGET }?.target,
                node.destinationFacingSpec?.takeIf { it.kind == FacingKind.TARGET }?.target,
                node.temporaryLocationFacingSpec?.takeIf { it.kind == FacingKind.TARGET }?.target,
            )
            if (embeddedTargets.any { it.kind == TargetKind.TEMPORARY }) {
                errors += "${script.id}/${node.id}: 一時変数参照の対象由来は完全バニラ出力に未対応です"
            }
            val conditionTarget = node.conditionPositionSpec
                ?.takeIf { it.kind == PositionKind.TARGET }?.target
            if (conditionTarget != null && (conditionTarget.kind == TargetKind.TEMPORARY || conditionTarget.isMultiCapable())) {
                errors += "${script.id}/${node.id}: 条件分岐の複数位置は完全バニラ出力に未対応です"
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
        worldValueTypes: Map<String, ValueType>,
    ) {
        // issue #27: 判定対象ベースで検証します。単一正本は `subject` 欄です。
        val subject = node.conditionSubject()
        if (subject == null) {
            errors += "${script.id}/${node.id}: 不明な判定対象です"
            return
        }
        when (subject) {
            ConditionSubject.VARIABLE -> {
                val variable = node.string("variable")
                if (variable.isBlank()) {
                    errors += "${script.id}/${node.id}: ワールド内変数名がありません"
                }
                if (worldValueTypes[variable] != ValueType.NUMBER) {
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
                    worldValueTypes[reference] != ValueType.NUMBER
                ) {
                    errors += "${script.id}/${node.id}: 比較対象の変数は数値型である必要があります: $reference"
                }
            }
            ConditionSubject.PLAYER -> {
                val itemData = node.string("itemData")
                if (itemData.isNotBlank() && ItemStackCodec.decode(itemData)?.hasItemMeta() != false) {
                    errors += "${script.id}/${node.id}: アイテムデータ付き所持判定は完全バニラ出力に未対応です"
                }
            }
            ConditionSubject.CONTROL_BLOCK ->
                errors += "${script.id}/${node.id}: 制御ブロックの状態条件は完全バニラ出力に未対応です"
            else -> Unit
        }
    }

    private fun annotateValueTypes(
        graph: CommandGraph,
        worldValueTypes: Map<String, ValueType>,
        errors: MutableList<String>,
    ) {
        fun annotate(current: CommandGraph) {
            current.nodes.values.forEach { node ->
                if (node.type == CommandType.CONDITION &&
                    node.conditionSubject() == ConditionSubject.VARIABLE
                ) {
                    val name = node.string("variable")
                    val type = worldValueTypes[name]
                    if (type == null) {
                        errors += "${node.id}: ワールド内変数の型を一意に解決できません: $name"
                    } else if (type != ValueType.NUMBER) {
                        errors += "${node.id}: 文字列変数の条件は完全バニラ出力に未対応です: $name"
                    } else {
                        node.params[EXPORT_VARIABLE_TYPE] = type.name
                    }
                }
                if (node.type == CommandType.VARIABLE &&
                    node.variableScopeStrict() == VariableScope.WORLD &&
                    node.string("operation", VariableOperation.DEFINE.name) == VariableOperation.CHANGE.name
                ) {
                    val name = node.string("name")
                    val type = worldValueTypes[name]
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
        if (position?.hasNonAbsoluteSemantics() == true) {
            errors += "${script.id}/${node.id}: 相対・ローカル指定の位置は完全バニラ出力に未対応です"
        }
    }

    private fun validateFacing(
        script: DiskScript,
        node: CommandNode,
        facing: me.awabi2048.kantancommander.model.FacingSpec?,
        errors: MutableList<String>,
    ) {
        if (facing?.hasNonAbsoluteSemantics() == true) {
            errors += "${script.id}/${node.id}: 相対・ローカル指定の向きは完全バニラ出力に未対応です"
        }
        // 向き複写はバニラのtp facing entity（注視）では表現できないため未対応とします。
        if (facing?.kind == FacingKind.COPY_ORIENTATION) {
            errors += "${script.id}/${node.id}: 向き複写は完全バニラ出力に未対応です"
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
                    node.variableScopeStrict() == VariableScope.WORLD &&
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
                node.type == CommandType.TEMP_SET || isExecutionAssignVariable(node) -> {
                    // LOCATIONの現位置捕捉やENTITYの対象選択は複数のvanilla命令へ
                    // 展開されます。通常コマンドの1命令前提へ押し込むと、先頭命令だけが
                    // scoreへ記録され、残りが実行されないため、ノード専用関数へまとめます。
                    // #2: EXECUTION scope の変数操作（代入系）も同じ一時storageへ出力します。
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
                    // issue #26: 実行位置が設定され、バニラで表現できる場合だけ
                    // positionedで包みます。
                    // issue #29: 対象由来位置は execute at で包むため positioned を
                    // 付けません。内側の positioned が外側の at を上書きし、実行時の
                    // 各位置実行と食い違うためです。DISK由来の相対座標も制御ブロック
                    // 位置を指すためずらしてはならず、作用位置が未設定のときだけ包みます。
                    val derivedTarget = entityDerivedTarget(node)
                    val positionedCommand = executionPositionContext(node)
                        ?.takeUnless { derivedTarget != null || PositionKind.DISK in actionPositionKinds(node) }
                        ?.let { context ->
                            wrapContext(context, command, node)
                        } ?: command
                    // issue #29: 対象由来位置は execute at で包みます。
                    // 複数該当時はエンティティごとに実行され、実行時の各位置実行と一致します。
                    val atCommand = derivedTarget?.let { target ->
                        "execute at ${selector(target)} run $positionedCommand"
                    } ?: positionedCommand
                    val temporaryReferences = temporaryEntityReferences(node, null)
                    temporaryReferences.forEach { reference ->
                        lines += temporaryEntityPreparation(reference)
                    }
                    val temporaryArgument = temporaryEntityArgumentReference(node)
                    val targetedCommand = temporaryArgument?.let { reference ->
                        temporaryEntitySelection(reference, atCommand)
                    } ?: atCommand
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
                    // issue #29: 静的単一の対象由来位置は execute at で包みます。
                    // 複数位置はANY蓄積が未実装のため検証で除外済みです。
                    val conditionAtTarget = node.conditionPositionSpec
                        ?.takeIf { it.kind == PositionKind.TARGET }?.target
                        ?.takeIf { it.kind != TargetKind.TEMPORARY }
                    fun atWrap(command: String): String = conditionAtTarget?.let { target ->
                        "execute at ${selector(target)} run $command"
                    } ?: command
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
                        val wrapped = conditionContext?.let { context -> wrapContext(context, preparation, node) } ?: preparation
                        lines += atWrap(wrapped)
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
                    // issue #29: 空集合フォールバックと共用するため、継続先へ名前を付けます。
                    fun continuation(next: UUID?): String = next?.let {
                        "return run function kantan:${nodeFunction(prefix, it)}"
                    } ?: "return 1"
                    val trueBranch =
                        "execute $trueCheck $predicateCommand run ${continuation(node.trueNext)}"
                    val falseBranch =
                        "execute $falseCheck $predicateCommand run ${continuation(node.falseNext)}"
                    val targetedTrueBranch = conditionTarget?.let { reference ->
                        temporaryEntitySelection(reference, trueBranch)
                    } ?: trueBranch
                    val targetedFalseBranch = conditionTarget?.let { reference ->
                        temporaryEntitySelection(reference, falseBranch)
                    } ?: falseBranch
                    lines += conditionContext?.let { context -> wrapContext(context, atWrap(targetedTrueBranch), node) } ?: atWrap(targetedTrueBranch)
                    lines += conditionContext?.let { context -> wrapContext(context, atWrap(targetedFalseBranch), node) } ?: atWrap(targetedFalseBranch)
                    conditionAtTarget?.let { target ->
                        // issue #29: 対象由来位置のセレクタが0件の場合、at包みの枝は
                        // 一度も実行されず分岐全体が途切れるため、false側の継続へ
                        // 明示的にフォールバックします。実行時も対象由来の空集合は
                        // FALSE扱いのため、ノード自身の反転だけを適用します。
                        val emptyFallback = if (node.boolean("inverted")) {
                            continuation(node.trueNext)
                        } else {
                            continuation(node.falseNext)
                        }
                        val emptyCheck = "execute unless entity ${selector(target)} run $emptyFallback"
                        lines += conditionContext?.let { context -> wrapContext(context, emptyCheck, node) } ?: emptyCheck
                    }
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
            FacingKind.ROTATION ->
                "$base ${facing.yaw ?: 0f} ${facing.pitch ?: 0f}"
            FacingKind.TARGET -> {
                // 注視は向き専用の単一体参照へ帰着します。
                val target = facing.target ?: error("destination facing target is missing")
                "$base facing entity ${singleSelector(target)} eyes"
            }
            FacingKind.COPY_ORIENTATION -> error("copy orientation is not supported by vanilla teleport")
            FacingKind.TEMPORARY -> {
                val name = facing.tempName ?: error("temporary facing name is missing")
                "$base facing ${temporaryPositionCoordinates(name)}"
            }
            FacingKind.COORDINATES ->
                "$base facing ${facing.x ?: error("destination facing x is missing")} " +
                    "${facing.y ?: error("destination facing y is missing")} " +
                    "${facing.z ?: error("destination facing z is missing")}"
        }
    }

    private fun soundCommand(node: CommandNode): String {
        if (SoundSettings.operation(node) == SoundOperation.STOP) return stopSoundCommand(node)
        val soundName = node.soundTempRef?.takeIf(String::isNotBlank)
            ?.let { temporaryMarker(it, "sound") }
            ?: node.string("sound")
        val volume = node.soundTempRef?.takeIf(String::isNotBlank)
            ?.let { temporaryMarker(it, "volume") }
            ?: node.string("volume", "1.0")
        val pitch = node.soundTempRef?.takeIf(String::isNotBlank)
            ?.let { temporaryMarker(it, "pitch") }
            ?: node.string("pitch", "1.0")
        val receivers = soundReceiverSelector(node)
        val sound = "playsound $soundName master $receivers ~ ~ ~ $volume $pitch"
        if (node.string("soundScope", "POSITION") == "WORLD") {
            // 全域指定は各プレイヤー位置を音源位置にするという計画書の定義に合わせ、
            // プレイヤーごとに一度ずつ実行します。
            return "execute as $receivers at @s run playsound $soundName master @s ~ ~ ~ $volume $pitch"
        }
        val position = node.soundPositionSpec ?: return sound
        return when (position.kind) {
            PositionKind.CAPTURED, PositionKind.COORDINATES ->
                "execute positioned ${position.x ?: error("sound x is missing")} " +
                    "${position.y ?: error("sound y is missing")} ${position.z ?: error("sound z is missing")} run $sound"
            PositionKind.DISK -> sound
            // issue #29: 対象由来位置は相対座標のまま出力し、呼び出し側で
            // execute atへ包みます。
            PositionKind.TARGET -> sound
            PositionKind.TEMPORARY -> {
                val name = position.tempName ?: error("temporary sound position name is missing")
                "execute positioned ${temporaryPositionCoordinates(name)} run $sound"
            }
            PositionKind.MYWORLD_SPAWN -> error("unsupported sound position")
        }
    }

    /**
     * 停止操作をvanillaの/stopsoundへ変換します。
     *
     * 構文は `stopsound <targets> [<source>] [<sound>]` であり、未指定のsource／
     * soundは省略します（全対象の意味）。BukkitのSoundCategory名はバニラの
     * source名へ写像します。
     */
    private fun stopSoundCommand(node: CommandNode): String {
        val soundName = node.soundTempRef?.takeIf(String::isNotBlank)
            ?.let { temporaryMarker(it, "sound") }
            ?: node.string("sound").takeIf(String::isNotBlank)
        val source = SoundSettings.category(node.string("soundCategory"))?.let(::vanillaSoundSource)
        val receivers = soundReceiverSelector(node)
        return listOfNotNull(
            "stopsound",
            receivers,
            source,
            soundName,
        ).joinToString(" ")
    }

    /** 対象プレイヤー指定があればselector、なければ従来の@aを返します。 */
    private fun soundReceiverSelector(node: CommandNode): String {
        val spec = node.targetSpec ?: return "@a"
        return if (spec.kind == TargetKind.TEMPORARY) "@s" else selector(spec)
    }

    private fun vanillaSoundSource(category: org.bukkit.SoundCategory): String = when (category) {
        org.bukkit.SoundCategory.MASTER -> "master"
        org.bukkit.SoundCategory.MUSIC -> "music"
        org.bukkit.SoundCategory.RECORDS -> "record"
        org.bukkit.SoundCategory.WEATHER -> "weather"
        org.bukkit.SoundCategory.BLOCKS -> "block"
        org.bukkit.SoundCategory.HOSTILE -> "hostile"
        org.bukkit.SoundCategory.NEUTRAL -> "neutral"
        org.bukkit.SoundCategory.PLAYERS -> "player"
        org.bukkit.SoundCategory.AMBIENT -> "ambient"
        org.bukkit.SoundCategory.VOICE -> "voice"
        org.bukkit.SoundCategory.UI -> "ui"
    }

    /**
     * 通常表示（PARTICLE）をvanillaの/particleへ変換します。対象selectorに@aを明示し、
     * execute positionedで表示中心だけを移動することで、同じワールドの全プレイヤーを
     * 表示対象にします。force指定による512ブロックのvanilla距離制限は仕様として許容します。
     *
     * 図形表示（PARTICLE_GEOMETRY）はvanillaに同等概念がないため出力できません。
     * 事前検証で拒否し、ここへは到達させません。
     */
    private fun particleCommand(node: CommandNode): String {
        val particle = ParticleSettings.particle(node) ?: error("particle type is missing")
        val data = ParticleSettings.parseData(particle, node.string(ParticleSettings.PARAM_DATA))
            .getOrElse { error("particle data is invalid") }
        // 追加データは1つのSNBT引数としてParticle IDへ直結します。旧版の
        // `/particle dust r g b scale ...`形式を出力すると、現行pack_formatの
        // データパックではコマンド解析に失敗するため、変換はParticleSettingsへ集約します。
        val dataArgument = data.vanillaArgument(particle)
        val receivers = node.targetSpec
            ?.let { if (it.kind == TargetKind.TEMPORARY) "@s" else selector(it) }
            ?: "@a"
        val command = "particle ${particle.key}$dataArgument " +
            "~ ~ ~ " +
            "${node.string(ParticleSettings.PARAM_DELTA_X, "0.0")} " +
            "${node.string(ParticleSettings.PARAM_DELTA_Y, "0.0")} " +
            "${node.string(ParticleSettings.PARAM_DELTA_Z, "0.0")} " +
            "${node.string(ParticleSettings.PARAM_SPEED, "0.0")} " +
            "${node.string(ParticleSettings.PARAM_COUNT, "1")} force $receivers"
        val position = node.particlePositionSpec ?: return command
        return when (position.kind) {
            PositionKind.CAPTURED, PositionKind.COORDINATES ->
                "execute positioned ${position.x ?: error("particle x is missing")} " +
                    "${position.y ?: error("particle y is missing")} ${position.z ?: error("particle z is missing")} run $command"
            PositionKind.DISK -> command
            PositionKind.TEMPORARY -> "execute positioned ${temporaryPositionCoordinates(
                position.tempName ?: error("temporary particle position name is missing"),
            )} run $command"
            // issue #29: 対象由来位置は相対座標のまま出力し、呼び出し側で
            // execute atへ包みます。
            PositionKind.TARGET -> command
            PositionKind.MYWORLD_SPAWN -> error("unsupported particle position")
        }
    }

    /** 一時値を保持するstorageパスです。型ごとの値は同じ名前空間へまとめます。 */
    private fun temporaryStoragePath(name: String): String =
        VanillaStorageNames.variablePath(TemporaryTemplate.normalized(name), temporary = true)

    /**
     * 複合値の位置・対象に関するvanilla制約を検証します。
     *
     * TEMP_SET と変数操作コマンドの EXECUTION scope で共有します。MyWorld固有
     * スポーンと固定エンティティ参照はバニラに同等概念がないため明示エラーにします。
     */
    private fun validateComplexValuePosition(
        node: CommandNode,
        script: DiskScript,
        errors: MutableList<String>,
        type: ValueType?,
    ) {
        when (type) {
            ValueType.LOCATION -> {
                if (node.temporaryLocationPositionSpec?.kind == PositionKind.MYWORLD_SPAWN) {
                    errors += "${script.id}/${node.id}: 一時LOCATIONのMyWorldスポーン位置は完全バニラ出力に未対応です"
                }
            }
            ValueType.ENTITY -> if (node.temporaryEntityTargetSpec?.kind == TargetKind.FIXED_ENTITY) {
                errors += "${script.id}/${node.id}: 一時ENTITYの固定エンティティ参照は完全バニラ出力できません"
            }
            else -> Unit
        }
    }

    /**
     * issue #2 の実行内変数値に関するvanilla制約を検証します。
     *
     * 一時storage compoundで扱うため、TEMP_SET と同じ位置・対象の制約を適用します。
     * 型は `type` 欄（TEMP_SET は `tempType` 欄）から解決します。
     */
    private fun validateExecutionValueCommands(
        node: CommandNode,
        script: DiskScript,
        errors: MutableList<String>,
    ) {
        val type = if (node.type == CommandType.TEMP_SET) {
            ValueType.parse(node.string("tempType"))
        } else {
            ValueType.parse(node.string("type"))
        }
        validateComplexValuePosition(node, script, errors, type)
    }

    /** LOCATION値をコマンド引数へ展開するための、exporter内部macro記法です。 */
    private fun temporaryPositionCoordinates(name: String): String = listOf("x", "y", "z")
        .joinToString(" ") { axis -> temporaryMarker(name, axis) }

    /**
     * 一時変数定義をstorage compoundへ変換します.
     *
     * Java実行側のTypedValueと同じく、LOCATION／SOUND／EFFECT等の複合値も
     * 1つの名前空間へ保存します。vanilla function macroへ渡す値は後段の
     * prepareMacroTemplateが必要なフィールドだけを抽出します.
     */
    private fun temporarySetCommand(node: CommandNode): String {
        val name = TemporaryTemplate.normalized(node.string("name"))
        val destination = temporaryStoragePath(name)
        val type = executionValueType(node) ?: error("unknown temporary variable type")
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
            ValueType.NUMBER ->
                directReference(node.string("value"))
                    ?: "data modify storage kantan:variables $destination set value ${number(node.string("value"))}"
            ValueType.STRING ->
                directReference(node.string("value"))
                    ?: "data modify storage kantan:variables $destination set value \"${escape(node.string("value"))}\""
            ValueType.BOOLEAN ->
                directReference(node.string("value"))
                    ?: "data modify storage kantan:variables $destination set value ${
                        when (node.string("value").trim().lowercase()) {
                            "true" -> "1b"
                            "false" -> "0b"
                            else -> error("boolean must be true or false")
                        }
                    }"
            ValueType.VECTOR ->
                "data modify storage kantan:variables $destination set value " +
                    "{x:${number(node.string("x"))},y:${number(node.string("y"))},z:${number(node.string("z"))}}"
            ValueType.LOCATION -> {
                val position = node.temporaryLocationPositionSpec
                val facing = node.temporaryLocationFacingSpec
                if (position != null && facing != null) {
                    require(position.kind in setOf(PositionKind.CAPTURED, PositionKind.COORDINATES)) {
                        "dynamic temporary LOCATION requires expanded commands"
                    }
                    require(facing.kind == FacingKind.ROTATION) {
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
            ValueType.ITEM ->
                "data modify storage kantan:variables $destination set value " +
                    "{item:\"${escape(node.string("item"))}\",itemData:\"${escape(node.string("itemData"))}\"}"
            ValueType.BLOCK ->
                "data modify storage kantan:variables $destination set value {block:\"${escape(node.string("block"))}\"}"
            ValueType.ENTITY -> {
                val uuid = runCatching { UUID.fromString(node.string("entityId")) }.getOrNull()
                // 不正なUUIDも「参照時に対象なし」として扱う契約です。空の
                // int配列を無理に生成するとSNBT自体が不正になるため、値を空の
                // compoundとして保存し、参照側の4要素リセットへ委ねます。
                val value = uuid?.let { "{uuid:${uuidIntArray(it)}}" } ?: "{}"
                "data modify storage kantan:variables $destination set value $value"
            }
            ValueType.SOUND ->
                "data modify storage kantan:variables $destination set value " +
                    "{sound:\"${escape(node.string("sound"))}\",volume:${number(node.string("volume", "1.0"))}," +
                    "pitch:${number(node.string("pitch", "1.0"))}}"
            ValueType.EFFECT ->
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
        val type = executionValueType(node) ?: error("unknown temporary variable type")
        val name = TemporaryTemplate.normalized(node.string("name"))
        val destination = temporaryStoragePath(name)
        val commands = when (type) {
            ValueType.LOCATION -> temporaryLocationSetCommands(node, destination)
            ValueType.ENTITY -> temporaryEntitySetCommands(node, destination)
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

    /**
     * issue #2 の実行内値の型を解決します。
     *
     * TEMP_SET は `tempType` 欄、変数操作コマンドの EXECUTION scope は `type` 欄を
     * 使います。両者は同じ一時storage compoundへ出力するため、 lowering を共有します。
     */
    private fun executionValueType(node: CommandNode): ValueType? = when (node.type) {
        CommandType.TEMP_SET -> ValueType.parse(node.string("tempType", ValueType.NUMBER.name))
        CommandType.VARIABLE -> ValueType.parse(node.string("type", ValueType.NUMBER.name))
        else -> null
    }

    /**
     * issue #2 の実行内代入ノードかを返します。
     *
     * EXECUTION scope の変数操作のうち、値を直接設定する操作（DEFINE／SET／
     * CHANGE＋ASSIGN）は、一時storageへの複数命令展開が必要な場合があるため、
     * TEMP_SET と同じ専用関数経路へ送ります。COPY／DELETE は単一命令で足りるため
     * 通常の lower 経路、CHANGE＋CALCULATE は検証で拒否するため対象外です。
     */
    private fun isExecutionAssignVariable(node: CommandNode): Boolean {
        if (node.type != CommandType.VARIABLE) return false
        if (node.variableScopeStrict() != VariableScope.EXECUTION) return false
        // #3: 詳細値取得・乱数は検証で拒否済みのため、専用関数展開の対象外です。
        val mode = node.string("changeMode", VariableChangeMode.ASSIGN.name)
        if (mode == VariableChangeMode.PROPERTY.name || mode == VariableChangeMode.RANDOM.name) return false
        return when (node.string("operation", VariableOperation.DEFINE.name)) {
            VariableOperation.DEFINE.name,
            VariableOperation.SET.name,
            -> true
            VariableOperation.CHANGE.name ->
                node.string("changeMode", VariableChangeMode.ASSIGN.name) == VariableChangeMode.ASSIGN.name
            else -> false
        }
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
            FacingKind.ROTATION -> {
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
            FacingKind.COPY_ORIENTATION -> error("temporary LOCATION copy orientation is not supported")
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
        }
        return commands
    }

    /** PositionSpecをvanillaのexecute位置引数へ変換します。 */
    private fun positionCoordinates(position: PositionSpec): String = when (position.kind) {
        PositionKind.CAPTURED, PositionKind.COORDINATES -> {
            // issue #4 の成分別指定は実行層のみで扱い、vanilla出力では
            // 黙って別意味へ変換しないため明示的に拒否します。
            if (position.hasNonAbsoluteSemantics()) {
                error("relative/local position is not supported by vanilla export")
            }
            "${position.x ?: error("location x is missing")} " +
                "${position.y ?: error("location y is missing")} " +
                "${position.z ?: error("location z is missing")}"
        }
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
            // 旧equipの出力はPLACE_ITEMへ移行済みのためここでは扱いません。
            // tagはENTITY_OPERATIONへ移行済みのため、ここでは扱いません。
            // motionは単一バニラコマンドへ正確に変換できないため出力しません（事前検査で未対応を明示します）。
            "ride" -> "ride ${effectiveTarget(node)} mount ${singleSelector(requireNotNull(node.secondaryTargetSpec))}"
            else -> null
        }
        CommandType.ENTITY_OPERATION -> when (node.string("operation", "tag")) {
            "tag" -> "tag ${effectiveTarget(node)} ${node.string("tagOperation", "add")} ${node.string("tag")}"
            "attribute" -> attributeCommand(node)
            // データ編集はバニラの等価表現へ正確に変換できないため出力しません（事前検査で未対応を明示します）。
            else -> null
        }
        // issue #21: PLACE_ITEMの出力を統合します。等価性の詳細はF1対応を参照します。
        CommandType.PLACE_ITEM -> placeItemCommand(node)
        CommandType.DISPLAY_TEXT -> when (node.string("mode", "tellraw")) {
            "title" -> "title ${effectiveTarget(node)} title {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
            "subtitle" -> "title ${effectiveTarget(node)} subtitle {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
            "actionbar" -> "title ${effectiveTarget(node)} actionbar {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
            else -> "tellraw ${effectiveTarget(node)} {\"text\":\"${escape(node.string("text").replace('&', '§'))}\"}"
        }
        CommandType.SUMMON_ENTITY -> summonCommand(node)
        CommandType.PLAY_SOUND -> soundCommand(node)
        CommandType.PARTICLE -> particleCommand(node)
        // #30: 図形表示はバニラ出力非対応です。検証層で拒否し、ここでは無出力とします。
        CommandType.PARTICLE_GEOMETRY -> null
        CommandType.FIREWORK_LAUNCH -> fireworkCommand(node)
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
        CommandType.FOR_START, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE,
        // 折り畳みノードは出力前に展開されるため、ここには到達しません。
        CommandType.FOLDED_FOR, CommandType.FOLDED_IF -> null
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
                // issue #29: 対象由来位置は相対座標で出力し、呼び出し側で
                // execute atへ包みます。単一限定の静的単一も同じ経路です。
                PositionKind.TARGET -> summon("~ ~ ~")
                PositionKind.TEMPORARY -> summon(
                    temporaryPositionCoordinates(
                        position.tempName ?: error("temporary summon position name is missing"),
                    ),
                )
                PositionKind.MYWORLD_SPAWN -> error("unsupported summon position")
            }
        }
    }

    /** FIREWORK_LAUNCHをsummon firework_rocketへ変換します。 */
    private fun fireworkCommand(node: CommandNode): String {
        val data = node.string(FireworkSettings.PARAM_DATA).takeIf(String::isNotBlank)
        val item = node.string(FireworkSettings.PARAM_ITEM).takeIf(String::isNotBlank)
        if (item == null && data == null) error("firework item is missing")
        if (item != null && !FireworkSettings.isFireworkMaterial(item)) error("firework item is not a rocket")
        // 中身なしの素ロケットは効果なしで打ち上げます。ItemStackの実体化は
        // 詳細効果の読み取りが必要な場合だけ行い、サーバー外の出力検証でも
        // 素ロケットを扱えるようにします。
        val effects = if (data != null) {
            val stack = ItemStackCodec.decode(data) ?: error("firework item data is invalid")
            if (stack.type != org.bukkit.Material.FIREWORK_ROCKET) error("firework item is not a rocket")
            (stack.itemMeta as? org.bukkit.inventory.meta.FireworkMeta)
                ?.effects?.map { effect ->
                    FireworkSettings.FireworkEffectData(
                        shape = FireworkSettings.vanillaShape(effect.type.name),
                        colors = effect.colors.map { it.asRGB() },
                        fadeColors = effect.fadeColors.map { it.asRGB() },
                        trail = effect.hasTrail(),
                        flicker = effect.hasFlicker(),
                    )
                }.orEmpty()
        } else {
            emptyList()
        }
        val heightRaw = node.string(FireworkSettings.PARAM_HEIGHT, FireworkSettings.HEIGHT_DEFAULT.toString())
        val height = heightRaw.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: error("firework height must be a static number for export")
        val power = FireworkSettings.heightToPower(height)
        val nbt = " ${FireworkSettings.fireworkSnbt(power, effects)}"
        val summon = { coordinates: String -> "summon minecraft:firework_rocket $coordinates$nbt" }
        return when (val position = node.fireworkPositionSpec) {
            null -> summon("~ ~ ~")
            else -> when (position.kind) {
                PositionKind.CAPTURED, PositionKind.COORDINATES -> summon(
                    "${position.x ?: error("firework x is missing")} " +
                        "${position.y ?: error("firework y is missing")} " +
                        "${position.z ?: error("firework z is missing")}",
                )
                PositionKind.DISK -> summon("~ ~ ~")
                // issue #29: 対象由来位置は相対座標で出力し、呼び出し側で
                // execute atへ包みます。
                PositionKind.TARGET -> summon("~ ~ ~")
                PositionKind.TEMPORARY -> summon(
                    temporaryPositionCoordinates(
                        position.tempName ?: error("temporary firework position name is missing"),
                    ),
                )
                PositionKind.MYWORLD_SPAWN -> error("unsupported firework position")
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

    /**
     * 「アイテムを配置する」(PLACE_ITEM) をバニラ `/item replace` へ変換します。
     *
     * 配置先のバニラ slot 記法は PlaceItemDestination が一元管理し、ここでは
     * 対象・位置・アイテム・個数の埋め込みだけを行います。個数は生の保存値を
     * そのまま出力します。スタック上限を超える個数は実行・出力ともに失敗する
     * ため等価であり、上限が解決できるリテラル超過は事前検査で拒否します。
     * 上書き禁止時は空スロット条件を前置した等価出力にします。実行側の成功は
     * 「1件以上適用」であり、条件付き出力と `store success` 包みの組合せで
     * 再現します。等価な空き検査を作れない配置先は事前検査で拒否するため、
     * ここへは到達させません。
     */
    private fun placeItemCommand(node: CommandNode): String {
        val destination = PlaceItemDestination.from(node.string("destination"))
            ?: error("unknown place item destination")
        val slot = PlaceItemDestination.vanillaSlot(destination, node.string("slot"))
            ?: error("unknown place item slot")
        val item = node.itemTempRef?.takeIf(String::isNotBlank)?.let { temporaryMarker(it, "item") }
            ?: node.string("item")
        val count = node.string("count", "1")
        if (node.boolean("overwrite")) {
            if (destination == PlaceItemDestination.BLOCK_CONTAINER) {
                val anchor = blockAnchor(node.blockPositionSpec ?: error("structured block position is missing"))
                return "${anchor.prefix}item replace block ${anchor.coordinates} $slot with $item $count"
            }
            return "item replace entity ${effectiveTarget(node)} $slot with $item $count"
        }
        // 上書き禁止時は空き検査を前置します。検査パスの不存在は空と等価です。
        // 対象が複数でも `as` で1件ずつ検査するため、実行側の適用件数と一致します。
        val guard = PlaceItemDestination.emptyCheckNbtPath(destination, node.string("slot"))
            ?: error("overwrite=false place item destination is not supported")
        if (destination == PlaceItemDestination.BLOCK_CONTAINER) {
            val anchor = blockAnchor(node.blockPositionSpec ?: error("structured block position is missing"))
            return "execute unless data block ${anchor.coordinates} $guard run " +
                "${anchor.prefix}item replace block ${anchor.coordinates} $slot with $item $count"
        }
        return "execute as ${effectiveTarget(node)} unless data entity @s $guard run " +
            "item replace entity @s $slot with $item $count"
    }

    /**
     * PLACE_ITEM の完全バニラ出力可否を検証します。
     *
     * GIVE_ITEM と同じく minecraft 名前空間・ItemStack メタデータを検査し、
     * 配置先とスロットの組合せ・ブロック位置の表現可否を加えます。
     * 個数は配置スタックの上限以下でなければ、実行・出力ともに失敗するため、
     * 上限が解決できる場合にリテラル超過を誤出力として拒否します。上限は
     * 保存済みitemDataの独自上限があればそれを、なければ素材の標準上限を
     * 用います。上限が解決できない場合（サーバーなし試験など）と変数・
     * 一時参照の個数は、実行時の失敗に委ねます。
     * 上書き禁止時は等価な空き検査を作れる配置先だけを通し、作れない配置先
     * （装備先など対象種別でNBT構造が変わる場合）は誤った等価出力を出さず
     * 明示拒否します。警告だけで別意味のコマンドは出力しません。
     */
    private fun validatePlaceItemExport(script: DiskScript, node: CommandNode, errors: MutableList<String>) {
        val item = node.string("item")
        if (node.itemTempRef.isNullOrBlank() &&
            (!item.startsWith("minecraft:") || CommandValueRules.material(item, allowAir = false) == null)
        ) {
            errors += "${script.id}/${node.id}: バニラに存在しない配置アイテムです: $item"
        }
        val itemData = node.string("itemData")
        // 保存済みスタックは1回だけ復元し、メタ検査と上限解決に使い回します。
        // 上限・メタの取得失敗は検査飛ばし・未対応として扱い、例外にしません。
        val decodedData = itemData.takeIf { it.isNotBlank() }?.let(ItemStackCodec::decode)
        if (node.itemTempRef.isNullOrBlank() && itemData.isNotBlank() &&
            decodedData?.let { runCatching { it.hasItemMeta() }.getOrNull() } != false
        ) {
            errors += "${script.id}/${node.id}: 保存された配置ItemStackメタデータは完全バニラ出力に未対応です"
        }
        val destination = PlaceItemDestination.from(node.string("destination"))
        if (destination == null) {
            errors += "${script.id}/${node.id}: 不明な配置先です"
            return
        }
        if (!PlaceItemDestination.isValidSlot(destination, node.string("slot"))) {
            errors += "${script.id}/${node.id}: 配置先に適用できないスロットです"
        }
        // 配置スタックの上限（保存済みitemDataの独自上限があればそれを、
        // なければ素材の標準上限）を超えるリテラル個数は、実行・出力ともに
        // 失敗する誤出力になるため、上限が解決できる場合に拒否します。
        // 一時変数アイテムは内容が確定しないため飛ばします。
        val itemDataMax = decodedData?.let { runCatching { it.maxStackSize }.getOrNull() }
        val material = if (!node.itemTempRef.isNullOrBlank()) null
        else CommandValueRules.material(node.string("item"), allowAir = false)
        val max = PlaceItemDestination.effectiveStackMax(
            itemDataMax,
            material?.let(PlaceItemDestination::materialMaxStackSize),
        )
        if (max != null && PlaceItemDestination.countExceedsMax(node.string("count", "1"), max)) {
            errors += "${script.id}/${node.id}: 配置個数がスタック上限を超えています"
        }
        if (!node.boolean("overwrite") &&
            PlaceItemDestination.emptyCheckNbtPath(destination, node.string("slot")) == null
        ) {
            errors += "${script.id}/${node.id}: 上書き禁止の配置先は完全バニラ出力に未対応です"
        }
        if (destination == PlaceItemDestination.BLOCK_CONTAINER) {
            validatePosition(script, node, node.blockPositionSpec, errors)
        }
    }

    /**
     * issue #32 のattribute操作をバニラのattributeコマンドへ変換します。
     *
     * 演算子の対応は[EntityAttributeSupport.vanillaModifierOperation]が正本であり、
     * 不正確な変換は行いません。対応付け不能な値は事前検査で未対応として扱い、
     * ここへは到達させません。
     * バニラのattributeは単一対象専用のため、対象ごとに `execute as … run` へ
     * 展開します。複数対象セレクターをそのまま渡すと無効になるためです。
     * 外側の `store success` 包みと組で、実行側の「1件以上適用」と一致します。
     */
    private fun attributeCommand(node: CommandNode): String? {
        val attribute = node.string(EntityAttributeSupport.PARAM_ATTRIBUTE).takeIf(String::isNotBlank)
            ?: return null
        val target = effectiveTarget(node)
        return when (node.string(EntityAttributeSupport.PARAM_OPERATION, EntityAttributeSupport.OPERATION_BASE_VALUE)) {
            EntityAttributeSupport.OPERATION_BASE_VALUE -> {
                val value = node.string(EntityAttributeSupport.PARAM_AMOUNT).takeIf(String::isNotBlank)
                    ?: return null
                "execute as $target run attribute @s $attribute base set $value"
            }
            EntityAttributeSupport.OPERATION_MODIFIER_ADD -> {
                val key = node.string(EntityAttributeSupport.PARAM_MODIFIER_KEY).takeIf(String::isNotBlank)
                    ?: return null
                val amount = node.string(EntityAttributeSupport.PARAM_AMOUNT).takeIf(String::isNotBlank)
                    ?: return null
                val operation = EntityAttributeSupport.vanillaModifierOperation(
                    node.string(EntityAttributeSupport.PARAM_MODIFIER_OPERATION),
                ) ?: return null
                "execute as $target run attribute @s $attribute modifier add $key $amount $operation"
            }
            EntityAttributeSupport.OPERATION_MODIFIER_REMOVE -> {
                val key = node.string(EntityAttributeSupport.PARAM_MODIFIER_KEY).takeIf(String::isNotBlank)
                    ?: return null
                "execute as $target run attribute @s $attribute modifier remove $key"
            }
            else -> null
        }
    }

    private fun collectWarnings(graph: CommandGraph, path: String, warnings: MutableList<String>) {
        graph.nodes.values.forEach { node ->
            if (node.type == CommandType.CAMERA_SHAKE) {
                warnings += "$path/${node.id}: カメラシェイクはJava版データパックから省略されました"
            }
            node.snapshot?.let { collectWarnings(it, "$path/${node.id}/snapshot", warnings) }
        }
    }

    // issue #27: 判定対象ベースで述語を生成します。未設定・不正な判定対象は
    // 検証で拒否済みのため、ここでは明示的に停止します。
    private fun predicate(node: CommandNode): String = when (
        node.conditionSubject()
    ) {
        ConditionSubject.OTHER_ENTITY -> "entity ${conditionTarget(node)}"
        ConditionSubject.PLAYER -> {
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
        ConditionSubject.VARIABLE -> {
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
        ConditionSubject.BLOCK ->
            "block ~ ~ ~ ${node.string("block", "minecraft:air")}"
        // 制御ブロック状態はプラグインの実行元ブロックを参照する条件であり、
        // 完全バニラ出力へ誤変換できないため、検証で拒否したうえでここでも明示的に止めます。
        ConditionSubject.CONTROL_BLOCK ->
            error("制御ブロックの状態条件は完全バニラ出力に未対応です")
        // 未設定・不正な判定対象は検証で拒否済みのため、述語生成へ進めません。
        null -> error("判定対象が未設定です")
    }

    private fun conditionPreparation(node: CommandNode, graph: CommandGraph): String? =
        if (node.conditionSubject() == ConditionSubject.VARIABLE) {
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
        node.conditionSubject() == ConditionSubject.VARIABLE && node.string("operator") == "!="

    private fun comparisonOperator(node: CommandNode): String =
        node.string("operator").takeUnless { it == "!=" } ?: "=="

    private fun conditionTarget(node: CommandNode): String {
        val spec = node.targetSpec ?: error("condition target is missing")
        return selector(spec.copy(limit = 1))
    }

    /**
     * issue #2 の変数操作コマンドをvanilla命令へ変換します。
     *
     * EXECUTION scope は一時storage compoundへ出力します。WORLD scope は永続
     * storageへ出力し、DEFINE だけは既存値を保つ unless  guard を付けます。
     * 計算式（CHANGE＋CALCULATE）は呼び出し側の算術展開へ委ね、ここでは扱いません。
     */
    private fun lowerVariable(node: CommandNode, graph: CommandGraph): String? {
        val scope = node.variableScopeStrict() ?: return null
        if (scope == VariableScope.ENTITY) return null
        val operation = VariableOperation.valueOf(node.string("operation", VariableOperation.DEFINE.name))
        if (scope == VariableScope.EXECUTION) return lowerExecutionVariable(node, operation)
        // #3: 詳細値取得・乱数は検証で拒否済みのため、ここへは到達しません。
        val mode = node.string("changeMode", VariableChangeMode.ASSIGN.name)
        if (mode == VariableChangeMode.PROPERTY.name || mode == VariableChangeMode.RANDOM.name) return null
        val name = node.string("name")
        val storagePath = VanillaStorageNames.variablePath(name, temporary = false)
        val type = ValueType.valueOf(
            if (operation == VariableOperation.CHANGE) {
                node.params[EXPORT_VARIABLE_TYPE] ?: node.string("type", ValueType.NUMBER.name)
            } else {
                node.string("type", ValueType.NUMBER.name)
            },
        )
        if (operation == VariableOperation.CHANGE &&
            node.string("changeMode", VariableChangeMode.ASSIGN.name) == VariableChangeMode.CALCULATE.name
        ) return null
        if (operation == VariableOperation.COPY) {
            val source = VanillaStorageNames.variablePath(node.string("source"), temporary = false)
            return "data modify storage kantan:variables $storagePath set from storage kantan:variables $source"
        }
        if (operation == VariableOperation.DELETE) {
            return "data remove storage kantan:variables $storagePath"
        }
        val raw = node.string("value")
        val special = raw.takeIf { it == "${'$'}{${SystemVariableNames.CURRENT_LOOP_COUNT}}" }
        if (special != null) {
            val loop = enclosingFor(graph, node.id) ?: return null
            if (type == ValueType.NUMBER) {
                val source = "#${loopName(loop.id)}_count"
                return "execute store result storage kantan:variables $storagePath double 1.0 run scoreboard players get $source kc_vars"
            }
            // 文字列代入は下のmacro経路でシステム変数を文字列化します。
        }
        val assignment = when (type) {
            ValueType.NUMBER ->
                "data modify storage kantan:variables $storagePath set value ${raw}d"
            ValueType.STRING ->
                "data modify storage kantan:variables $storagePath set value \"${escape(raw)}\""
            ValueType.BOOLEAN ->
                when (raw.trim().lowercase()) {
                    "true" -> "data modify storage kantan:variables $storagePath set value 1b"
                    "false" -> "data modify storage kantan:variables $storagePath set value 0b"
                    else -> return null
                }
            ValueType.LOCATION,
            ValueType.VECTOR,
            ValueType.ITEM,
            ValueType.BLOCK,
            ValueType.ENTITY,
            ValueType.SOUND,
            ValueType.EFFECT,
            -> return null
        }
        return if (operation == VariableOperation.DEFINE) {
            "execute unless data storage kantan:variables $storagePath run $assignment"
        } else assignment
    }

    /**
     * EXECUTION scope の単一路線をvanilla命令へ変換します。
     *
     * DEFINE は既存値を保つ unless guard を付けます。複合値は呼び出し側の
     * 専用関数展開へ委ね、ここでは扱いません。
     */
    private fun lowerExecutionVariable(node: CommandNode, operation: VariableOperation): String? {
        // CHANGE＋CALCULATE・詳細値取得・乱数は検証で拒否済みのため、ここへは到達しません。
        // 防御的に null を返し、算術展開（WORLD格納）へ流さないようにします。
        val mode = node.string("changeMode", VariableChangeMode.ASSIGN.name)
        if (mode != VariableChangeMode.ASSIGN.name) return null
        val destination = temporaryStoragePath(node.string("name"))
        return when (operation) {
            VariableOperation.DEFINE -> {
                val assignment = lowerExecutionAssignment(node) ?: return null
                "execute unless data storage kantan:variables $destination run $assignment"
            }
            VariableOperation.SET,
            VariableOperation.CHANGE,
            -> lowerExecutionAssignment(node)
            VariableOperation.COPY -> {
                val source = temporaryStoragePath(node.string("source"))
                "data modify storage kantan:variables $destination set from storage kantan:variables $source"
            }
            VariableOperation.DELETE ->
                "data remove storage kantan:variables $destination"
        }
    }

    /** EXECUTION scope の値代入を単一vanilla命令へ変換します。複合値は null です。 */
    private fun lowerExecutionAssignment(node: CommandNode): String? {
        val destination = temporaryStoragePath(node.string("name"))
        val raw = node.string("value")
        return when (ValueType.parse(node.string("type"))) {
            ValueType.NUMBER ->
                "data modify storage kantan:variables $destination set value ${raw}d"
            ValueType.STRING ->
                "data modify storage kantan:variables $destination set value \"${escape(raw)}\""
            ValueType.BOOLEAN ->
                when (raw.trim().lowercase()) {
                    "true" -> "data modify storage kantan:variables $destination set value 1b"
                    "false" -> "data modify storage kantan:variables $destination set value 0b"
                    else -> null
                }
            else -> null
        }
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
                    // #3: べき乗（^）は検証で拒否済みのため、ここへは到達しません。
                    // 防御的に失敗として扱い、不正確な変換を行いません。
                    if (token.value == '^') return listOf("return 0")
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
                // #3: 三角関数は検証で拒否済みのため、ここへは到達しません。
                is NumericExpression.PostfixToken.Function -> return listOf("return 0")
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
                    node.particleEndPositionSpec,
                    node.fireworkPositionSpec,
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
        // CONDITIONの条件対象として実際に効くプレイヤー／その他存在判定だけを
        // 対象にし、変数・ブロック条件の無関係な内部対象状態で分岐を変えません。
        val subject = node.conditionSubject()
        if (subject != ConditionSubject.OTHER_ENTITY && subject != ConditionSubject.PLAYER) return null
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
                val position = effectiveBlockPosition(node)
                    ?: error("structured block position is missing")
                val anchor = blockAnchor(position)
                "${anchor.prefix}setblock ${anchor.coordinates} $block"
            }
            BlockOperationMode.FILL -> {
                val from = blockAnchor(
                    effectiveFillFrom(node) ?: error("structured fill range is missing"),
                )
                val to = blockAnchor(
                    effectiveFillTo(node) ?: error("structured fill range is missing"),
                )
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
        // issue #29: 対象由来位置は相対座標で出力し、呼び出し側で
        // execute atへ包みます。単一限定の静的単一も同じ経路です。
        PositionKind.TARGET -> BlockAnchor("", "~ ~ ~")
        PositionKind.TEMPORARY -> BlockAnchor(
            "",
            temporaryPositionCoordinates(spec.tempName ?: error("temporary block position name is missing")),
        )
        PositionKind.MYWORLD_SPAWN -> error("unsupported structured block position")
    }

    private fun conditionContext(node: CommandNode): ExecutionContextSpec? {
        // issue #26: 条件判定位置が未設定のときだけ実行位置を既定値とします。
        val position = node.conditionPositionSpec ?: node.executionPositionSpec ?: return null
        return ExecutionContextSpec(position = position)
    }

    /**
     * issue #26: バニラで表現できる実行位置だけを文脈化します。
     *
     * MYWORLD_SPAWN・相対・対象位置は上記検証で除外済みのためここでは扱わず、
     * 未設定・DISK（制御ブロック位置と同義）は包みません。
     */
    private fun executionPositionContext(node: CommandNode): ExecutionContextSpec? {
        val position = node.executionPositionSpec ?: return null
        return when (position.kind) {
            PositionKind.COORDINATES, PositionKind.CAPTURED, PositionKind.TEMPORARY -> ExecutionContextSpec(position = position)
            else -> null
        }
    }

    /**
     * issue #29: コマンドの代表位置に内包された対象を返します。
     *
     * 検証済みの範囲では、対象由来位置の相対座標出力と組で execute at へ包み、
     * 実行時の各位置実行と一致させます。一時変数内包・実行位置・条件分岐の
     * 複数位置は検証で除外済みのため、ここでは扱いません。
     */
    private fun entityDerivedTarget(node: CommandNode): TargetSpec? {
        // fillは始点の対象へ包みます。終点だけが対象由来の場合は終点へ包み、
        // 両端で異なる対象の場合は検証で除外済みです。
        // 未設定の片端は実行位置を既定値とするため、既定値込みで判定します。
        if (node.type == CommandType.BLOCK_OPERATION &&
            BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value)) ==
            BlockOperationMode.FILL
        ) {
            val from = effectiveFillFrom(node)
                ?.takeIf { it.kind == PositionKind.TARGET }?.target
                ?.takeIf { it.kind != TargetKind.TEMPORARY }
            val to = effectiveFillTo(node)
                ?.takeIf { it.kind == PositionKind.TARGET }?.target
                ?.takeIf { it.kind != TargetKind.TEMPORARY }
            return from ?: to
        }
        val spec = when (node.type) {
            CommandType.SUMMON_ENTITY -> node.summonPositionSpec
            CommandType.PLAY_SOUND -> node.soundPositionSpec
            CommandType.PARTICLE -> node.particlePositionSpec
            CommandType.PARTICLE_GEOMETRY -> node.particlePositionSpec
            CommandType.FIREWORK_LAUNCH -> node.fireworkPositionSpec
            CommandType.BLOCK_OPERATION -> effectiveBlockPosition(node)
            CommandType.PLACE_ITEM -> node.blockPositionSpec
            else -> null
        }?.takeIf { it.kind == PositionKind.TARGET } ?: return null
        return spec.target?.takeIf { it.kind != TargetKind.TEMPORARY }
    }

    /**
     * issue #26: ブロック操作の実行時と同じ既定値を返します。
     *
     * コマンド固有位置があればそちらを優先し、未設定時は実行位置を既定値とします。
     * 実行位置の対象由来は上記検証で除外済みのため、ここへは到達しません。
     */
    private fun effectiveBlockPosition(node: CommandNode): me.awabi2048.kantancommander.model.PositionSpec? =
        node.blockPositionSpec ?: node.executionPositionSpec

    /** issue #26: fill始点の実行時と同じ既定値です。 */
    private fun effectiveFillFrom(node: CommandNode): me.awabi2048.kantancommander.model.PositionSpec? =
        node.blockFromSpec ?: node.executionPositionSpec

    /** issue #26: fill終点の実行時と同じ既定値です。 */
    private fun effectiveFillTo(node: CommandNode): me.awabi2048.kantancommander.model.PositionSpec? =
        node.blockToSpec ?: node.executionPositionSpec

    /**
     * issue #29: コマンドの代表位置の種別を返します。
     *
     * DISK由来の相対座標は制御ブロック位置を指すため、実行位置の
     * positioned包みでずらしてはなりません。テレポート先も同じ扱いです。
     * セレクタ方式の移動先（destinationTargetSpec）は位置に依存しないため
     * 含めません。
     */
    private fun actionPositionKinds(node: CommandNode): Set<PositionKind> {
        if (node.type == CommandType.BLOCK_OPERATION &&
            BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value)) ==
            BlockOperationMode.FILL
        ) {
            return listOfNotNull(effectiveFillFrom(node), effectiveFillTo(node)).map { it.kind }.toSet()
        }
        return listOfNotNull(
            when (node.type) {
                CommandType.SUMMON_ENTITY -> node.summonPositionSpec
                CommandType.PLAY_SOUND -> node.soundPositionSpec
                CommandType.PARTICLE -> node.particlePositionSpec
                CommandType.PARTICLE_GEOMETRY -> node.particlePositionSpec
                CommandType.FIREWORK_LAUNCH -> node.fireworkPositionSpec
                CommandType.BLOCK_OPERATION -> effectiveBlockPosition(node)
                CommandType.PLACE_ITEM -> node.blockPositionSpec
                CommandType.TELEPORT -> node.destinationSpec
                else -> null
            }?.kind,
        ).toSet()
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
                    FacingKind.TARGET -> facing.target?.let { add("facing entity ${selector(it)} eyes") }
                    FacingKind.COPY_ORIENTATION -> error("copy orientation context is not supported by vanilla export")
                    FacingKind.COORDINATES -> add("facing ${facing.x} ${facing.y} ${facing.z}")
                    FacingKind.TEMPORARY -> facing.tempName?.let { name -> add("facing ${temporaryPositionCoordinates(name)}") }
                    FacingKind.ROTATION -> add("rotated ${facing.yaw} ${facing.pitch}")
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
                .append(":particleEndPosition=").append(node.particleEndPositionSpec)
                .append(":fireworkPosition=").append(node.fireworkPositionSpec)
                .append(":summonPosition=").append(node.summonPositionSpec)
                .append(":blockPosition=").append(node.blockPositionSpec)
                .append(":blockFrom=").append(node.blockFromSpec)
                .append(":blockTo=").append(node.blockToSpec)
                .append(":executionPosition=").append(node.executionPositionSpec)
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
        const val EXPORT_VARIABLE_TYPE = "_exportValueType"
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
