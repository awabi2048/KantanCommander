package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.selectedControlBlockStates
import me.awabi2048.kantancommander.model.DisplayTextTimingPolicy
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.MAX_BLOCK_OPERATION_VOLUME
import me.awabi2048.kantancommander.model.MAX_COMMAND_TIME_SECONDS
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.FireworkSettings
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.ParticleDisplayMode
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.RegionAnchorKind
import me.awabi2048.kantancommander.model.RegionKind
import me.awabi2048.kantancommander.model.RegionSpec
import me.awabi2048.kantancommander.model.SpatialVector
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
import me.awabi2048.kantancommander.error.ErrorCode
import me.awabi2048.kantancommander.error.ErrorSeverity
import me.awabi2048.kantancommander.error.ExecutionError
import me.awabi2048.kantancommander.error.FailurePolicy
import me.awabi2048.kantancommander.error.ValidationPhase
import java.util.Collections
import java.util.IdentityHashMap
import java.util.ArrayDeque
import java.util.UUID

/**
 * 実行前検証の結果を、表示文字列ではなく対象ノードと設定項目で保持します。
 *
 * issue #6 の共通分類として code/severity/phase/policy を持ちます。
 * message はサーバーログ・デバッグ用の人間向け文で、プレイヤー表示の契約には
 * しません。表示は [ErrorCode.displayKey] 経由で生成済みキーへ解決します。
 * 既存呼出との互換のため message 付き構築も残しますが、新規検証は code 付きを
 * 使用し、message のみによる判定は行いません。
 */
data class ScriptValidationError(
    val path: String,
    val nodeId: UUID?,
    val fieldKeys: Set<String>,
    val message: String,
    val code: ErrorCode = ErrorCode.CONFIG_INVALID,
    val severity: ErrorSeverity = code.defaultSeverity,
    val phase: ValidationPhase = ValidationPhase.PREFLIGHT,
    val failurePolicy: FailurePolicy = code.defaultPolicy,
) {
    fun rendered(): String = "$path: $message"

    /** 共通エラー形式へ変換します。scriptId は呼出側で付与します。 */
    fun toExecutionError(scriptId: UUID? = null): ExecutionError = ExecutionError(
        code = code,
        severity = severity,
        phase = phase,
        scriptId = scriptId,
        nodeId = nodeId,
        fieldKeys = fieldKeys,
        path = path,
        message = message,
        failurePolicy = failurePolicy,
    )
}

/** 保存・実行・出力で共通利用するコマンド設定検証です。 */
object ExecutableScriptValidator {
    /**
     * variableDefinitionsは配置済みMyWorldでのみ渡します。
     * nullの場合は`${...}`の構文だけを検証し、未配置スクリプトの編集を妨げません。
     */
    fun validate(
        script: DiskScript,
        limits: GraphLimits = GraphLimits(),
        variableDefinitions: Map<String, WorldVariableValue>? = null,
    ): List<ScriptValidationError> {
        // variableDefinitions なしは未配置編集時の edit-time 検証、ありは配置済み
        // MyWorld での preflight 検証です。phase はこの切替えで確定します。
        val phase = if (variableDefinitions == null) ValidationPhase.EDIT_TIME else ValidationPhase.PREFLIGHT
        val errors = mutableListOf<ScriptValidationError>()
        errors += validateScriptRoot(script, phase)
        validateGraph(
            script.graph,
            "root",
            errors,
            Collections.newSetFromMap(IdentityHashMap()),
            limits,
            variableDefinitions,
            phase,
        )
        // 個別項目検証は当面 code 既定のままのため、phase だけここで正規化します。
        // 構造由来の PERSISTED_STRUCTURAL は維持し、それ以外の PREFLIGHT 既定値を
        // 未配置編集時は EDIT_TIME へ寄せます。code 細分化は後続作業で行います。
        if (phase == ValidationPhase.EDIT_TIME) {
            for (index in errors.indices) {
                val current = errors[index]
                if (current.phase == ValidationPhase.PREFLIGHT) {
                    errors[index] = current.copy(phase = ValidationPhase.EDIT_TIME)
                }
            }
        }
        return errors
    }

    /**
     * プログラム全体設定（タイマー等）の検証です。
     *
     * グラフ内容に依存しないため、全量検証と増分検証で共有します。
     * 増分側で再計算しても安価であり、staleの温床にしません。
     */
    internal fun validateScriptRoot(
        script: DiskScript,
        phase: ValidationPhase,
    ): List<ScriptValidationError> = buildList {
        if (!script.timer.enabled && script.activation == ActivationMode.ALWAYS_ACTIVE) {
            this += ScriptValidationError(
                "root", null, setOf("timer"), "タイマーオフでは常時実行を使用できません",
                code = ErrorCode.TIMER_CONFLICT, phase = phase,
            )
        }
        if (script.timer.enabled && !CommandValueRules.isTimerSeconds(script.timer.intervalSeconds)) {
            this += ScriptValidationError(
                "root", null, setOf("timer"),
                "タイマー間隔は0より大きく${MAX_TIMER_SECONDS}秒以下のtick境界（0.05秒単位）で指定してください",
                code = ErrorCode.TIMER_RANGE, phase = phase,
            )
        }
    }

    /**
     * 単一ノードの項目検証です。
     *
     * issue #15 の増分検証入口であり、全量走査なしに指定ノードの
     * エラーだけを求めます。呼び出し側と同じ展開・到達・一時変数条件で
     * 既存の [validateNode] を実行するため、全量検証の該当部分と一致します。
     * snapshot／foldedGraph内部の再帰は行いません。内部の差分は
     * [me.awabi2048.kantancommander.data.IncrementalValidator] 側で
     * 外側IDへ対応付けて個別に検証します。
     * 存在しないIDでは空を返し、欠落自体の判定は全量側に任せます。
     */
    internal fun validateNodeLocal(
        graph: CommandGraph,
        nodeId: UUID,
        limits: GraphLimits = GraphLimits(),
        variableDefinitions: Map<String, WorldVariableValue>? = null,
    ): List<ScriptValidationError> {
        val phase = if (variableDefinitions == null) ValidationPhase.EDIT_TIME else ValidationPhase.PREFLIGHT
        val errors = mutableListOf<ScriptValidationError>()
        // 全量側と同じく折り畳みは展開した一時グラフで検証します。
        // 内部IDは折り畳み時に元のIDのまま退避するため付け替えは不要です。
        val effective = if (graph.nodes.values.any(FoldedGraphs::isFolded)) {
            runCatching { FoldedGraphs.expand(graph, limits.maximumBranchDepth) }.getOrElse { return errors }
        } else {
            graph
        }
        val node = effective.nodes[nodeId] ?: return errors
        val temporaryAvailability = analyzeTemporaryAvailability(effective)
        validateNode(
            node,
            "root/${node.id}",
            errors,
            variableDefinitions,
            isInsideForBody(effective, node.id),
            temporaryAvailability[node.id] ?: emptyMap(),
            phase,
        )
        // 全量側と同じく未配置編集時は phase を寄せます。
        if (phase == ValidationPhase.EDIT_TIME) {
            for (index in errors.indices) {
                val current = errors[index]
                if (current.phase == ValidationPhase.PREFLIGHT) {
                    errors[index] = current.copy(phase = ValidationPhase.EDIT_TIME)
                }
            }
        }
        return errors
    }

    private fun validateGraph(
        graph: CommandGraph,
        path: String,
        errors: MutableList<ScriptValidationError>,
        visited: MutableSet<CommandGraph>,
        limits: GraphLimits,
        variableDefinitions: Map<String, WorldVariableValue>?,
        phase: ValidationPhase,
    ) {
        if (!visited.add(graph)) {
            errors += ScriptValidationError(
                path, null, emptySet(), "別ディスクのコピー内容が循環参照しています",
                code = ErrorCode.GRAPH_CYCLE, phase = ValidationPhase.PERSISTED_STRUCTURAL,
            )
            return
        }
        // 構造エラーは nodeId・code を保持したまま取込み、文面解析なしに
        // GUI の該当ノードへ紐付けます。phase は保存構造由来で固定します。
        GraphValidator.validateStructured(graph, limits).forEach { structural ->
            errors += ScriptValidationError(
                path, structural.nodeId, emptySet(), structural.message,
                code = structural.code, phase = ValidationPhase.PERSISTED_STRUCTURAL,
            )
        }
        // 折り畳みノードの中身は外側に通常形状で1個だけ残るため、項目検証と
        // データフロー解析はメモリ上で展開した一時グラフに対して行います。
        // 保存内容は折り畳んだまま維持されます。
        val effective = if (graph.nodes.values.any(FoldedGraphs::isFolded)) {
            FoldedGraphs.expand(graph, limits.maximumBranchDepth)
        } else {
            graph
        }
        // 一時変数はグラフ全体の宣言ではなく、実行時点で到達が保証される定義だけを
        // 参照可能とします。分岐合流では全経路の共通部分を使い、DISK_CALLの
        // スナップショットは別グラフとして解析することで、実行時セッションの寿命と
        // 静的検証の結果を一致させます。
        val temporaryAvailability = analyzeTemporaryAvailability(effective)
        effective.nodes.values.forEach { node ->
            validateNode(
                node,
                "$path/${node.id}",
                errors,
                variableDefinitions,
                isInsideForBody(effective, node.id),
                temporaryAvailability[node.id] ?: emptyMap(),
                phase,
            )
            node.snapshot?.let {
                validateGraph(it, "$path/${node.id}/snapshot", errors, visited, limits, variableDefinitions, phase)
            }
            node.foldedGraph?.let {
                validateGraph(it, "$path/${node.id}/folded", errors, visited, limits, variableDefinitions, phase)
            }
        }
        visited.remove(graph)
    }

    /**
     * 各ノードの入口で確実に定義済みとなる一時変数の型をデータフロー解析します。
     *
     * TEMP_SETは直後の経路へ型を追加・上書きします。条件分岐の合流では、全ての
     * 到達経路に存在し、かつ型が一致する名前だけを残します。異なる分岐で異なる型を
     * 設定した名前は、合流後の型を静的に決められないため未定義として扱います。
     * 実行時のセッション表は別途必ず確認し、動的な参照失敗を防御します。
     */
    private fun analyzeTemporaryAvailability(
        graph: CommandGraph,
    ): Map<UUID, Map<String, TemporaryVariableType>> {
        val entry = graph.entryNodeId?.takeIf(graph.nodes::containsKey) ?: return emptyMap()
        fun successors(node: CommandNode): List<UUID> = when (node.type) {
            CommandType.CONDITION -> listOfNotNull(node.trueNext, node.falseNext)
            CommandType.FOR_START -> listOfNotNull(node.trueNext)
            else -> listOfNotNull(node.next)
        }

        val reachable = linkedSetOf<UUID>()
        val visitQueue = ArrayDeque<UUID>()
        visitQueue.add(entry)
        while (visitQueue.isNotEmpty()) {
            val id = visitQueue.removeFirst()
            if (!reachable.add(id)) continue
            graph.nodes[id]?.let { node -> successors(node).forEach(visitQueue::addLast) }
        }

        val predecessors = reachable.associateWith { mutableListOf<UUID>() }.toMutableMap()
        reachable.forEach { sourceId ->
            val source = graph.nodes[sourceId] ?: return@forEach
            successors(source).filter(reachable::contains).forEach { targetId ->
                predecessors.getValue(targetId) += sourceId
            }
        }

        fun join(states: List<Map<String, TemporaryVariableType>>): Map<String, TemporaryVariableType> {
            if (states.isEmpty()) return emptyMap()
            return states.first().keys.mapNotNull { name ->
                val type = states.first().getValue(name)
                if (states.drop(1).all { it[name] == type }) name to type else null
            }.toMap()
        }

        fun transfer(node: CommandNode, input: Map<String, TemporaryVariableType>): Map<String, TemporaryVariableType> {
            if (node.type != CommandType.TEMP_SET) return input
            val name = TemporaryTemplate.normalized(node.string("name"))
            val type = TemporaryVariableType.parse(
                node.string("tempType", TemporaryVariableType.NUMBER.name),
            )
            if (!CommandValueRules.isVariableName(name) || type == null) return input
            return input + (name to type)
        }

        val inputs = mutableMapOf<UUID, Map<String, TemporaryVariableType>>()
        val outputs = mutableMapOf<UUID, Map<String, TemporaryVariableType>>()
        val queue = ArrayDeque<UUID>()
        inputs[entry] = emptyMap()
        queue.add(entry)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val node = graph.nodes[id] ?: continue
            val input = if (id == entry) {
                emptyMap()
            } else {
                val incoming = predecessors.getValue(id)
                if (incoming.any { it !in outputs }) continue
                join(incoming.map(outputs::getValue))
            }
            if (inputs[id] == input && id in outputs) continue
            inputs[id] = input
            val output = transfer(node, input)
            val changed = outputs[id] != output
            outputs[id] = output
            if (changed) successors(node).filter(reachable::contains).forEach(queue::addLast)
        }
        return inputs
    }

    private fun validateNode(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
        phase: ValidationPhase = ValidationPhase.PREFLIGHT,
    ) {
        validateSystemReferences(node, path, insideForBody, errors)
        validateRemovedSystemReferences(node, path, errors)
        validateTemporaryReferences(node, path, errors, temporaryDefinitions)
        listOfNotNull(node.targetSpec, node.secondaryTargetSpec, node.destinationTargetSpec).forEach {
            validateTarget(it, path, node, errors, variableDefinitions, insideForBody, temporaryDefinitions)
        }
        // TEMP_SETのENTITYも通常コマンドと同じTargetSpecを使います。定義側だけが
        // 独自のentityId文字列へ戻ると、対象種別・絞り込み・一時参照の検証が分岐するため、
        // 構造化された設定を通常の対象検証へ必ず通します。
        node.temporaryEntityTargetSpec?.let {
            validateTarget(it, path, node, errors, variableDefinitions, insideForBody, temporaryDefinitions)
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
            node.executionPositionSpec,
        ).forEach { validatePosition(it, path, node, errors, temporaryDefinitions) }
        node.temporaryLocationPositionSpec?.let {
            validatePosition(it, path, node, errors, temporaryDefinitions)
        }
        validateEmbeddedPositionTargets(node, path, errors, variableDefinitions, insideForBody, temporaryDefinitions)
        validateSinglePositionTargets(node, path, errors)
        validateBareTargetPositions(node, path, errors)
        node.destinationFacingSpec?.let { validateFacing(it, path, node, errors, temporaryDefinitions) }
        node.temporaryLocationFacingSpec?.let {
            validateFacing(it, path, node, errors, temporaryDefinitions)
        }
        when (node.type) {
            CommandType.TELEPORT -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
                if (node.destinationSpec == null && node.destinationTargetSpec == null) {
                    errors += nodeError(node, path, setOf("destination"), "移動先座標が未設定です")
                }
            }
            CommandType.GIVE_ITEM -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
                if (node.itemTempRef.isNullOrBlank() && CommandValueRules.material(node.string("item"), allowAir = false) == null) {
                    errors += nodeError(node, path, setOf("item"), "アイテムが未設定です")
                }
                validatePositiveIntegerInput(node.string("count"), variableDefinitions, node, path, setOf("count"), errors, temporaryDefinitions)
            }
            CommandType.ENTITY_ACTION -> validateEntityAction(node, path, errors, temporaryDefinitions)
            CommandType.DISPLAY_TEXT -> validateDisplayText(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.WAIT -> validateWait(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.SUMMON_ENTITY -> {
                if (!CommandValueRules.isEntityTypeId(node.string("entity"))) {
                    errors += nodeError(node, path, setOf("entity"), "エンティティの種類が不正です")
                }
                // 召喚タグも単一文字列です。カンマ区切りの複数タグへ展開せず、
                // 入力された値全体を一つのタグとして検証します。
                val tag = node.string("tags")
                if (tag.isNotBlank() && !isTemplateOrTag(tag, variableDefinitions, temporaryDefinitions)) {
                    errors += nodeError(node, path, setOf("tags"), "タグが不正です")
                }
                validateTemplate(node.string("customName"), node, path, "customName", errors, variableDefinitions, temporaries = temporaryDefinitions)
            }
            CommandType.PLAY_SOUND -> validateSound(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.PARTICLE -> validateParticle(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.FIREWORK_LAUNCH -> validateFirework(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.APPLY_EFFECT -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
                if (node.effectTempRef.isNullOrBlank() && !CommandValueRules.isEffectId(node.string("effect"))) {
                    errors += nodeError(node, path, setOf("effect"), "エフェクトが不正です")
                }
                validateIntegerRange(node, path, "level", 1..255, "エフェクトレベルは1〜255の範囲です", errors, variableDefinitions, temporaryDefinitions)
                validateIntegerRange(node, path, "seconds", 1..86_400, "エフェクトの持続時間は1〜86400秒の範囲です", errors, variableDefinitions, temporaryDefinitions)
            }
            CommandType.CAMERA_SHAKE -> {
                validateRange(node, path, "intensity", 0.1..4.0, "カメラシェイクの強さは0.1〜4.0の範囲です", errors, variableDefinitions, temporaryDefinitions)
                validateRange(node, path, "seconds", 1.0..10.0, "カメラシェイクの時間は1.0〜10.0秒の範囲です", errors, variableDefinitions, temporaryDefinitions)
                if (node.string("shakeType") !in setOf("positional", "rotational")) {
                    errors += nodeError(node, path, setOf("shakeType"), "カメラシェイクの種類が不正です")
                }
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "カメラシェイクの対象が未設定です")
            }
            CommandType.BLOCK_OPERATION -> validateBlockOperation(node, path, errors, temporaryDefinitions)
            CommandType.ENTITY_DELETE -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "削除対象が未設定です")
            }
            CommandType.CONDITION -> validateCondition(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.DISK_CALL -> if (node.snapshot == null) {
                errors += nodeError(node, path, setOf("diskId"), "呼び出すディスク内容が未設定です")
            }
            CommandType.VARIABLE -> validateVariable(node, path, errors, variableDefinitions, insideForBody, temporaryDefinitions)
            CommandType.TEMP_SET -> validateTemporary(node, path, errors, temporaryDefinitions)
            CommandType.FOR_START -> validateFor(node, path, errors, variableDefinitions, temporaryDefinitions)
            // 折り畳みノードの外側は先頭設定を写しているため、展開前の検査でも
            // 同じ項目検証を適用します。中身の構造検証は展開後グラフで行います。
            CommandType.FOLDED_FOR -> validateFor(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.FOLDED_IF -> validateCondition(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.MERGE, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> Unit
        }
    }

    /**
     * 効果音コマンドを操作種別ごとに検証します。
     *
     * PLAYは従来の sound／volume／pitch／soundScope 検証を維持し、STOPは
     * sound（空＝全音）／soundCategory（空＝全カテゴリ）の任意指定だけを検証
     * します。対象プレイヤー（targetSpec）は共通の対象検証へ委ね、未設定時は
     * 実行起点ワールドの全員へ届けます。エラーは統合後のタブキーで報告します。
     */
    private fun validateSound(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        val operationRaw = node.params[SoundSettings.PARAM_OPERATION]
        if (!operationRaw.isNullOrBlank() && SoundOperation.parse(operationRaw).name != operationRaw.trim().uppercase()) {
            errors += nodeError(node, path, setOf("soundOperation"), "効果音の操作が不正です")
            return
        }
        when (SoundSettings.operation(node)) {
            SoundOperation.PLAY -> {
                if (node.soundTempRef.isNullOrBlank() && !CommandValueRules.isSoundId(node.string("sound"))) {
                    errors += nodeError(node, path, setOf("soundSetting"), "サウンドIDが不正です")
                }
                validateSettingRange(node, path, "volume", 0.0..34.0, "音量は0.0〜34.0の範囲です", "soundSetting", errors, variableDefinitions, temporaryDefinitions)
                validateSettingRange(node, path, "pitch", 0.5..2.0, "ピッチは0.5〜2.0の範囲です", "soundSetting", errors, variableDefinitions, temporaryDefinitions)
                if (node.string("soundScope", "POSITION") !in setOf("POSITION", "WORLD")) {
                    errors += nodeError(node, path, setOf("soundScope"), "サウンドの再生範囲が不正です")
                }
            }
            SoundOperation.STOP -> {
                // 一時変数参照時は実行時に参照先の効果音IDへ解決するため、
                // リテラル欄の検証を抑止します。
                if (node.soundTempRef.isNullOrBlank()) {
                    val sound = node.string("sound")
                    if (sound.isNotBlank() && !CommandValueRules.isSoundId(sound)) {
                        errors += nodeError(node, path, setOf("soundSetting"), "停止対象のサウンドIDが不正です")
                    }
                    if (!SoundSettings.isCategoryValue(node.string("soundCategory"))) {
                        errors += nodeError(node, path, setOf("soundSetting"), "停止対象のカテゴリが不正です")
                    }
                }
            }
        }
    }

    /** 文字列パラメータの範囲検証を、表示タブキーへ投影して報告します。 */
    private fun validateSettingRange(
        node: CommandNode,
        path: String,
        field: String,
        range: ClosedFloatingPointRange<Double>,
        message: String,
        reportField: String,
        errors: MutableList<ScriptValidationError>,
        definitions: Map<String, WorldVariableValue>?,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ) {
        validateNumberInput(node.string(field), definitions, node, path, setOf(reportField), errors, temporaries)
        val value = resolvedDouble(node.string(field), definitions, temporaries)
        if (value != null && value !in range) errors += nodeError(node, path, setOf(reportField), message)
    }

    /**
     * PARTICLEを表示方式ごとに検証します。
     *
     * 点表示（POINT）は従来の散布検証を維持し、図形表示（GEOMETRY）は形状・
     * 密度・形状数値を検証します。粒子種類と詳細データの検証は両方式で共通です。
     * 幾何量が確定できる場合は見積点数の上限も確認し、quota超過の常時拒否を
     * 保存時に抑止します。
     */
    private fun validateParticle(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        val displayRaw = node.params[ParticleGeometry.PARAM_DISPLAY_MODE]
        if (!displayRaw.isNullOrBlank() &&
            ParticleDisplayMode.parse(displayRaw).name != displayRaw.trim().uppercase()
        ) {
            errors += nodeError(node, path, setOf("particleDisplay"), "パーティクルの表示方式が不正です")
            return
        }
        val shapeRaw = node.params[ParticleGeometry.PARAM_SHAPE]
        if (!shapeRaw.isNullOrBlank() &&
            ParticleShape.parse(shapeRaw).name != shapeRaw.trim().uppercase()
        ) {
            errors += nodeError(node, path, setOf("geometrySettings"), "パーティクルの形状が不正です")
            return
        }
        val particle = ParticleSettings.particle(node)
        if (particle == null) {
            errors += nodeError(node, path, setOf(ParticleSettings.PARAM_PARTICLE), "パーティクルの種類が不正です")
        } else if (ParticleSettings.requiresData(particle)) {
            // 詳細データを必要とする種類だけ検証します。不要な種類の残存値は
            // 表示・実行へ漏らさないため、旧保存データの救済として対象外にします。
            val data = ParticleSettings.parseData(particle, node.string(ParticleSettings.PARAM_DATA))
            if (data.isFailure) {
                errors += nodeError(node, path, setOf(ParticleSettings.PARAM_DATA), "パーティクルの詳細データが不正です")
            }
        }
        when (ParticleGeometry.displayMode(node)) {
            ParticleDisplayMode.POINT -> validatePointScatter(node, path, errors, variableDefinitions, temporaryDefinitions)
            ParticleDisplayMode.GEOMETRY -> validateGeometry(node, path, errors, variableDefinitions, temporaryDefinitions)
        }
    }

    /** 従来の単点散布（delta／speed／count）を検証します。 */
    private fun validatePointScatter(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        val parameterField = setOf("particleParameters")
        validateParticleRange(node, path, ParticleSettings.PARAM_DELTA_X, "X方向の散布範囲", errors, variableDefinitions, temporaryDefinitions, parameterField)
        validateParticleRange(node, path, ParticleSettings.PARAM_DELTA_Y, "Y方向の散布範囲", errors, variableDefinitions, temporaryDefinitions, parameterField)
        validateParticleRange(node, path, ParticleSettings.PARAM_DELTA_Z, "Z方向の散布範囲", errors, variableDefinitions, temporaryDefinitions, parameterField)
        validateParticleRange(
            node,
            path,
            ParticleSettings.PARAM_SPEED,
            "速度",
            errors,
            variableDefinitions,
            temporaryDefinitions,
            parameterField,
            minimum = 0.0,
        )
        validatePositiveIntegerInput(
            node.string(ParticleSettings.PARAM_COUNT, "1"),
            variableDefinitions,
            node,
            path,
            parameterField,
            errors,
            temporaryDefinitions,
        )
    }

    /** 図形表示の形状・密度・形状数値を検証します。 */
    private fun validateGeometry(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        val shape = ParticleGeometry.shape(node)
        if (shape == ParticleShape.POINT_SCATTER) {
            errors += nodeError(node, path, setOf("geometrySettings"), "図形表示では形状を選択してください")
            return
        }
        validateGeometryNumber(node, path, ParticleGeometry.PARAM_DENSITY, "密度", errors, variableDefinitions, temporaryDefinitions, setOf("particleDensity")) {
            ParticleGeometry.isDensityValue(it)
        }
        val report = setOf("geometrySettings")
        when (shape) {
            ParticleShape.LINE -> {
                if (node.particleEndPositionSpec == null) {
                    errors += nodeError(node, path, report, "線の終点が未設定です")
                }
            }
            ParticleShape.CIRCLE -> {
                validateGeometryNumber(node, path, ParticleGeometry.PARAM_RADIUS, "半径", errors, variableDefinitions, temporaryDefinitions, report) {
                    finiteIn(it, 0.0, ParticleGeometry.MAX_RADIUS, exclusiveMin = true)
                }
                validateAxis(node, path, errors, variableDefinitions, temporaryDefinitions, report)
            }
            ParticleShape.ARC -> {
                validateGeometryNumber(node, path, ParticleGeometry.PARAM_RADIUS, "半径", errors, variableDefinitions, temporaryDefinitions, report) {
                    finiteIn(it, 0.0, ParticleGeometry.MAX_RADIUS, exclusiveMin = true)
                }
                validateGeometryNumber(node, path, ParticleGeometry.PARAM_START_ANGLE, "開始角", errors, variableDefinitions, temporaryDefinitions, report) {
                    finiteIn(it, ParticleGeometry.MIN_ANGLE, ParticleGeometry.MAX_ANGLE)
                }
                validateGeometryNumber(node, path, ParticleGeometry.PARAM_END_ANGLE, "終了角", errors, variableDefinitions, temporaryDefinitions, report) {
                    finiteIn(it, ParticleGeometry.MIN_ANGLE, ParticleGeometry.MAX_ANGLE)
                }
                validateAxis(node, path, errors, variableDefinitions, temporaryDefinitions, report)
            }
            ParticleShape.BOX -> {
                if (node.particleEndPositionSpec == null) {
                    errors += nodeError(node, path, report, "箱の対角点が未設定です")
                }
                validateGeometryBoolean(node, path, ParticleGeometry.PARAM_WIREFRAME, "ワイヤーフレーム", errors, report)
            }
            ParticleShape.SPHERE -> {
                validateGeometryNumber(node, path, ParticleGeometry.PARAM_RADIUS, "半径", errors, variableDefinitions, temporaryDefinitions, report) {
                    finiteIn(it, 0.0, ParticleGeometry.MAX_RADIUS, exclusiveMin = true)
                }
                validateAxis(node, path, errors, variableDefinitions, temporaryDefinitions, report)
            }
            ParticleShape.HELIX -> {
                validateGeometryNumber(node, path, ParticleGeometry.PARAM_RADIUS, "半径", errors, variableDefinitions, temporaryDefinitions, report) {
                    finiteIn(it, 0.0, ParticleGeometry.MAX_RADIUS, exclusiveMin = true)
                }
                validateGeometryNumber(node, path, ParticleGeometry.PARAM_HEIGHT, "高さ", errors, variableDefinitions, temporaryDefinitions, report) {
                    finiteIn(it, 0.0, ParticleGeometry.MAX_HEIGHT, exclusiveMin = true)
                }
                validateGeometryNumber(node, path, ParticleGeometry.PARAM_TURNS, "巻き数", errors, variableDefinitions, temporaryDefinitions, report) {
                    finiteIn(it, ParticleGeometry.MIN_TURNS, ParticleGeometry.MAX_TURNS)
                }
                validateAxis(node, path, errors, variableDefinitions, temporaryDefinitions, report)
            }
            ParticleShape.POINT_SCATTER -> Unit
        }
        validateGeometryNumber(node, path, ParticleGeometry.PARAM_STEP, "間隔", errors, variableDefinitions, temporaryDefinitions, report) {
            finiteIn(it, ParticleGeometry.MIN_STEP, ParticleGeometry.MAX_STEP)
        }
        validatePointEstimate(node, path, shape, errors, variableDefinitions, temporaryDefinitions, report)
    }

    /** 法線・軸（yaw／pitch）を検証します。 */
    private fun validateAxis(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
        report: Set<String>,
    ) {
        validateGeometryNumber(node, path, ParticleGeometry.PARAM_AXIS_YAW, "軸の向き", errors, variableDefinitions, temporaryDefinitions, report) {
            finiteIn(it, -180.0, 180.0)
        }
        validateGeometryNumber(node, path, ParticleGeometry.PARAM_AXIS_PITCH, "軸の傾き", errors, variableDefinitions, temporaryDefinitions, report) {
            finiteIn(it, -90.0, 90.0)
        }
        validateGeometryNumber(node, path, ParticleGeometry.PARAM_ROTATION, "回転", errors, variableDefinitions, temporaryDefinitions, report) {
            finiteIn(it, ParticleGeometry.MIN_ANGLE, ParticleGeometry.MAX_ANGLE)
        }
    }

    /** 図形数値をテンプレート委譲つきで検証します。 */
    private fun validateGeometryNumber(
        node: CommandNode,
        path: String,
        field: String,
        label: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
        report: Set<String>,
        accepts: (String) -> Boolean,
    ) {
        val raw = node.string(field)
        validateNumberInput(raw, variableDefinitions, node, path, report, errors, temporaryDefinitions)
        if (!deferNumericValidation(raw, variableDefinitions, temporaryDefinitions) && !accepts(raw)) {
            errors += nodeError(node, path, report, "${label}の値が不正です")
        }
    }

    private fun validateGeometryBoolean(
        node: CommandNode,
        path: String,
        field: String,
        label: String,
        errors: MutableList<ScriptValidationError>,
        report: Set<String>,
    ) {
        if (node.params[field]?.toBooleanStrictOrNull() == null) {
            errors += nodeError(node, path, report, "${label}の設定が不正です")
        }
    }

    private fun finiteIn(raw: String, min: Double, max: Double, exclusiveMin: Boolean = false): Boolean {
        val value = raw.toDoubleOrNull() ?: return false
        if (!value.isFinite()) return false
        return if (exclusiveMin) value > min && value <= max else value in min..max
    }

    /**
     * 確定値から見積点数を求め、上限超過を保存時に抑止します。
     *
     * 位置・テンプレートが未確定の場合は実行時のquota判定へ委ねます。
     */
    private fun validatePointEstimate(
        node: CommandNode,
        path: String,
        shape: ParticleShape,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
        report: Set<String>,
    ) {
        val densityRaw = node.string(ParticleGeometry.PARAM_DENSITY, "0.5")
        if (deferNumericValidation(densityRaw, variableDefinitions, temporaryDefinitions)) return
        val density = densityRaw.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return
        // 終端距離が必要な形状だけ両端の確定座標を求め、それ以外は距離不要です。
        val endDistance = when (shape) {
            ParticleShape.LINE, ParticleShape.BOX -> literalEndDistance(node) ?: return
            else -> 0.0
        }
        val estimate = ParticleGeometry.estimatePoints(shape, node.params, density, endDistance) ?: return
        if (estimate > ParticleGeometry.MAX_POINTS) {
            errors += nodeError(node, path, report, "図形が大きすぎます（見積${estimate}点・上限${ParticleGeometry.MAX_POINTS}点）")
        }
    }

    /** 両端が確定座標の場合だけ終端距離を返します。未確定時はnullです。 */
    private fun literalEndDistance(node: CommandNode): Double? {
        val start = node.particlePositionSpec ?: return null
        val end = node.particleEndPositionSpec ?: return null
        val sx = start.x ?: return null
        val sy = start.y ?: return null
        val sz = start.z ?: return null
        val ex = end.x ?: return null
        val ey = end.y ?: return null
        val ez = end.z ?: return null
        if (!listOf(sx, sy, sz, ex, ey, ez).all(Double::isFinite)) return null
        val dx = ex - sx
        val dy = ey - sy
        val dz = ez - sz
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * 花火打ち上げを検証します。
     *
     * 中身（item＋itemData）は素材の正当性だけを確認し、Base64の復元可否は
     * 実行時に判定します。GIVE_ITEMの装備検証と同じく、保存時のRegistry解決に
     * 依存しない境界とします。打ち上げ位置は共通の位置検証へ委ねます。
     */
    private fun validateFirework(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        val item = node.string(FireworkSettings.PARAM_ITEM)
        val data = node.string(FireworkSettings.PARAM_DATA)
        if (item.isBlank() && data.isBlank()) {
            errors += nodeError(node, path, setOf("fireworkContents"), "花火の中身が未設定です")
        } else if (item.isNotBlank() && !FireworkSettings.isFireworkMaterial(item)) {
            errors += nodeError(node, path, setOf("fireworkContents"), "花火のロケットではありません")
        }
        val heightRaw = node.string(FireworkSettings.PARAM_HEIGHT, FireworkSettings.HEIGHT_DEFAULT.toString())
        validateNumberInput(heightRaw, variableDefinitions, node, path, setOf("burstHeight"), errors, temporaryDefinitions)
        if (!deferNumericValidation(heightRaw, variableDefinitions, temporaryDefinitions) &&
            !FireworkSettings.isBurstHeight(heightRaw)
        ) {
            errors += nodeError(node, path, setOf("burstHeight"), "打ち上げ高さは0〜320ブロックの範囲です")
        }
    }

    private fun validateParticleRange(
        node: CommandNode,
        path: String,
        field: String,
        message: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
        fields: Set<String>,
        minimum: Double? = null,
    ) {
        val raw = node.string(field, "0.0")
        validateNumberInput(raw, variableDefinitions, node, path, fields, errors, temporaryDefinitions)
        val value = resolvedDouble(raw, variableDefinitions, temporaryDefinitions)
        if (!deferNumericValidation(raw, variableDefinitions, temporaryDefinitions) &&
            (value == null || (minimum != null && value < minimum))
        ) {
            val constraint = if (minimum != null) "0以上の有限な数値" else "有限な数値"
            errors += nodeError(node, path, fields, "${message}は${constraint}で指定してください")
        }
    }

    private fun validateEntityAction(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
        when (node.string("action", "ride")) {
            "ride" -> if (node.secondaryTargetSpec == null) {
                errors += nodeError(node, path, setOf("other"), "乗り物となる対象が未設定です")
            }
            "dismount" -> Unit
            "equip" -> {
                if (!CommandValueRules.isEquipmentSlot(node.string("slot"))) {
                    errors += nodeError(node, path, setOf("slot"), "装備するスロットが不正です")
                }
                if (node.itemTempRef.isNullOrBlank() && CommandValueRules.material(node.string("item"), allowAir = false) == null) {
                    errors += nodeError(node, path, setOf("item"), "装備アイテムが未設定です")
                }
                if (node.params["overwrite"]?.toBooleanStrictOrNull() == null) {
                    errors += nodeError(node, path, setOf("overwrite"), "上書き設定が不正です")
                }
            }
            "tag" -> {
                if (node.string("tagOperation", "add") !in setOf("add", "remove")) {
                    errors += nodeError(node, path, setOf("tagOperation"), "タグ操作が不正です")
                }
                val tag = node.string("tag")
                // カンマを特別扱いせず、単一タグの通常の形式検証へ委ねます。
                // 一時変数 `%{name}%` の参照も文字列欄として許可します。
                validateTemplate(tag, node, path, "tag", errors, null, temporaries = temporaryDefinitions)
                if (VariableTemplate.references(tag).isEmpty() && TemporaryTemplate.references(tag).isEmpty() &&
                    !CommandValueRules.isTag(tag)
                ) {
                    errors += nodeError(node, path, setOf("tag"), "タグが不正です")
                }
            }
            else -> errors += nodeError(node, path, setOf("action"), "不明なエンティティ操作です")
        }
    }

    private fun validateDisplayText(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
        if (node.string("mode") !in setOf("tellraw", "title", "subtitle", "actionbar")) {
            errors += nodeError(node, path, setOf("mode"), "不明な表示形式です")
        }
        validateTemplate(node.string("text"), node, path, "text", errors, variableDefinitions, temporaries = temporaryDefinitions)
        validateTemplate(node.string("subtitle"), node, path, "subtitle", errors, variableDefinitions, temporaries = temporaryDefinitions)
        if (DisplayTextTimingPolicy.supports(node)) {
            listOf("fadeInSeconds", "staySeconds", "fadeOutSeconds").forEach { field ->
                validateTickAlignedTime(
                    node = node,
                    path = path,
                    field = field,
                    raw = node.string(field),
                    minimum = 0.0,
                    exclusiveMinimum = false,
                    rangeMessage = "表示時間は0秒以上86400秒以下の数値で指定してください",
                    variableDefinitions = variableDefinitions,
                    errors = errors,
                    temporaryDefinitions = temporaryDefinitions,
                )
            }
        }
    }

    private fun validateWait(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        validateTickAlignedTime(
            node = node,
            path = path,
            field = "seconds",
            raw = node.string("seconds"),
            minimum = 0.0,
            exclusiveMinimum = true,
            rangeMessage = "待機時間は0秒より大きく86400秒以下の数値で指定してください",
            variableDefinitions = variableDefinitions,
            errors = errors,
            temporaryDefinitions = temporaryDefinitions,
        )
    }

    private fun validateBlockOperation(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        val operation = BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))
        if (operation == null) errors += nodeError(node, path, setOf("operation"), "配置方式が不正です")
        if (node.blockTempRef.isNullOrBlank() && CommandValueRules.placementMaterial(node.string("block")) == null) {
            errors += nodeError(node, path, setOf("block"), "配置ブロックが未設定または不正です")
        }
        when (operation) {
            // issue #26: コマンド固有位置が未設定のときは実行位置を既定値とします。
            // 実行位置も未設定の場合だけ未設定エラーにし、実行時のフォールバックと一致させます。
            BlockOperationMode.SETBLOCK -> if (node.blockPositionSpec == null && node.executionPositionSpec == null) {
                errors += nodeError(node, path, setOf("position"), "ブロック配置位置が未設定です")
            }
            BlockOperationMode.FILL -> {
                val from = node.blockFromSpec
                val to = node.blockToSpec
                if (from == null && node.executionPositionSpec == null) {
                    errors += nodeError(node, path, setOf("from"), "範囲配置の始点が未設定です")
                }
                if (to == null && node.executionPositionSpec == null) {
                    errors += nodeError(node, path, setOf("to"), "範囲配置の終点が未設定です")
                }
                // 体積上限は実行位置の既定値も含めて判定します。座標に定まらない
                // 指定（対象由来等）は blockVolume がnullを返すため対象外です。
                val effectiveFrom = from ?: node.executionPositionSpec
                val effectiveTo = to ?: node.executionPositionSpec
                if (effectiveFrom != null && effectiveTo != null &&
                    blockVolume(effectiveFrom, effectiveTo)?.let { it > MAX_BLOCK_OPERATION_VOLUME } == true
                ) {
                    errors += nodeError(node, path, setOf("from", "to"), "範囲配置は${MAX_BLOCK_OPERATION_VOLUME}ブロック以内で指定してください")
                }
            }
            null -> Unit
        }
    }

    private fun validateTarget(
        spec: TargetSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        if (spec.kind == TargetKind.FIXED_ENTITY && spec.fixedEntityId == null) {
            errors += nodeError(node, path, emptySet(), "固定エンティティが未設定です")
        }
        if (spec.kind == TargetKind.TEMPORARY) {
            checkTemporaryDefinition(spec.tempName, TemporaryVariableType.ENTITY, temporaryDefinitions, node, path, emptySet(), errors)
        }
        // 対象は各コマンドのTargetSpecだけで解決し、未設定の対象を
        // 前段ノードから暗黙に継承する経路は検証しません。
        spec.searchOrigin?.let { validateSearchOrigin(it, path, node, errors, temporaryDefinitions) }
        spec.entityType?.takeIf(String::isNotBlank)?.let {
            if (!CommandValueRules.isEntityTypeId(it)) errors += nodeError(node, path, emptySet(), "エンティティの種類が不正です")
        }
        spec.gameMode?.takeIf(String::isNotBlank)?.let {
            if (it.uppercase() !in setOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")) {
                errors += nodeError(node, path, emptySet(), "ゲームモードが不正です")
            }
        }
        spec.tag?.takeIf(String::isNotBlank)?.let {
            validateTemplate(it, node, path, "target", errors, variableDefinitions, insideForBody, temporaryDefinitions)
            if (VariableTemplate.references(it).isEmpty() && TemporaryTemplate.references(it).isEmpty() && !CommandValueRules.isTag(it)) {
                errors += nodeError(node, path, emptySet(), "タグが不正です")
            }
        }
        spec.name?.takeIf(String::isNotBlank)?.let {
            validateTemplate(it, node, path, "target", errors, variableDefinitions, insideForBody, temporaryDefinitions)
            if (it.length > 256) errors += nodeError(node, path, emptySet(), "エンティティ名が長すぎます")
        }
        val distances = listOf(spec.minimumDistance, spec.maximumDistance)
        if (distances.any { it?.isFinite() == false }) errors += nodeError(node, path, emptySet(), "対象距離は有限値で指定してください")
        if (distances.any { it != null && it < 0.0 }) errors += nodeError(node, path, emptySet(), "対象距離は0以上で指定してください")
        if (spec.minimumDistance != null && spec.maximumDistance != null && spec.minimumDistance > spec.maximumDistance) {
            errors += nodeError(node, path, emptySet(), "最小距離が最大距離を超えています")
        }
        listOf("dx" to spec.dx, "dy" to spec.dy, "dz" to spec.dz).forEach { (field, value) ->
            if (value != null && (!value.isFinite() || value < 0.0)) {
                errors += nodeError(node, path, setOf(field), "$field は有限な0以上の数値で指定してください")
            }
        }
        if (spec.limit?.let { it < 1 } == true) errors += nodeError(node, path, emptySet(), "対象数は1以上で指定してください")
    }

    private fun validatePosition(
        spec: PositionSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        if (spec.kind == PositionKind.TEMPORARY) {
            checkTemporaryDefinition(spec.tempName, TemporaryVariableType.LOCATION, temporaryDefinitions, node, path, emptySet(), errors)
            if (spec.hasNonAbsoluteSemantics()) {
                errors += nodeError(node, path, setOf("semantics"), "一時変数位置に相対・ローカルは指定できません")
            }
            return
        }
        // 成分別指定は座標種別だけの指定です。他種別への付与は拒否します。
        if (spec.hasNonAbsoluteSemantics() &&
            spec.kind !in setOf(PositionKind.CAPTURED, PositionKind.COORDINATES)
        ) {
            errors += nodeError(node, path, setOf("semantics"), "相対・ローカルは座標指定のときだけ有効です")
            return
        }
        // 角度にLOCALは基準が定義できないため拒否します。
        if (spec.semanticsYaw == CoordinateSemantics.LOCAL || spec.semanticsPitch == CoordinateSemantics.LOCAL) {
            errors += nodeError(node, path, setOf("semantics"), "向きにローカルは指定できません")
        }
        // 位置は各コマンドのPositionSpecだけで解決し、実行者・前段対象を
        // 位置設定へ暗黙に読み替える経路は検証しません。
        if (spec.kind in setOf(PositionKind.CAPTURED, PositionKind.COORDINATES) &&
            listOf(spec.x, spec.y, spec.z).any { it?.isFinite() != true }
        ) {
            errors += nodeError(node, path, emptySet(), "座標が未設定または有限値ではありません")
        }
        if (spec.kind == PositionKind.CAPTURED && (spec.yaw?.isFinite() != true || spec.pitch?.isFinite() != true)) {
            errors += nodeError(node, path, emptySet(), "捕捉した向きが未設定または有限値ではありません")
        }
    }

    /**
     * issue #29: 位置・向きに内包された対象指定を、通常の対象検証へ通します。
     *
     * 対象内包なし（旧データ）は検証対象外であり、互換性を維持します。
     */
    private fun validateEmbeddedPositionTargets(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        listOfNotNull(
            node.destinationSpec?.target,
            node.conditionPositionSpec?.target,
            node.blockPositionSpec?.target,
            node.blockFromSpec?.target,
            node.blockToSpec?.target,
            node.soundPositionSpec?.target,
            node.particlePositionSpec?.target,
            node.particleEndPositionSpec?.target,
            node.fireworkPositionSpec?.target,
            node.summonPositionSpec?.target,
            node.executionPositionSpec?.target,
            node.temporaryLocationPositionSpec?.target,
        ).forEach {
            validateTarget(it, path, node, errors, variableDefinitions, insideForBody, temporaryDefinitions)
        }
        listOfNotNull(
            node.destinationFacingSpec?.target,
            node.temporaryLocationFacingSpec?.target,
        ).forEach {
            validateTarget(it, path, node, errors, variableDefinitions, insideForBody, temporaryDefinitions)
        }
    }

    /**
     * issue #29: 単一限定の役割に対象由来位置が選ばれていないか検証します。
     *
     * 複数位置の対応関係が別途必要になる位置（fill始点終点・図形の対応位置・
     * LOCATION一時変数そのもの）は、GUIでも無効化します。静的0..1の指定は
     * 先頭1件へ解決するため許可します。テレポート先のdestinationSpecは
     * 旧来の対象移動経路（destinationTargetSpec併用）のため除外します。
     */
    private fun validateSinglePositionTargets(        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
    ) {
        listOfNotNull(
            node.blockFromSpec,
            node.blockToSpec,
            node.particleEndPositionSpec,
            node.temporaryLocationPositionSpec,
        ).filter { it.kind == PositionKind.TARGET && (it.target == null || it.target.isMultiCapable()) }
            .forEach { errors += nodeError(node, path, setOf("position"), "この位置に対象由来の複数位置は指定できません") }
        // issue #26・#29: fillの始点終点は単一へ定まる実行起点だけを既定値とします。
        // 複数になり得る実行位置との組合せは実行時に定まらないため検証で拒否します。
        // 片端だけの欠落も既定値を使うため、両端欠落に限定しません。
        val executionTarget = node.executionPositionSpec?.takeIf { it.kind == PositionKind.TARGET }?.target
        if (node.type == CommandType.BLOCK_OPERATION &&
            BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value)) ==
            BlockOperationMode.FILL &&
            (node.blockFromSpec == null || node.blockToSpec == null) &&
            executionTarget != null && executionTarget.isMultiCapable()
        ) {
            errors += nodeError(node, path, setOf("executionPosition"), "範囲指定の既定値に複数位置は指定できません")
        }
    }

    /**
     * issue #29: 対象指定のない対象由来位置を検証します。
     *
     * 内包対象のないTARGET種別は解決不能な旧データであり、黙って
     * 空集合扱いにせず明示エラーとします。テレポート先のdestinationSpecと
     * 向きの旧来形式（実行状態対象）は従来経路のため除外します。
     */
    private fun validateBareTargetPositions(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
    ) {
        listOfNotNull(
            node.conditionPositionSpec,
            node.blockPositionSpec,
            node.blockFromSpec,
            node.blockToSpec,
            node.soundPositionSpec,
            node.particlePositionSpec,
            node.particleEndPositionSpec,
            node.fireworkPositionSpec,
            node.summonPositionSpec,
            node.executionPositionSpec,
            node.temporaryLocationPositionSpec,
        ).filter { it.kind == PositionKind.TARGET && it.target == null }
            .forEach { errors += nodeError(node, path, setOf("position"), "対象由来位置に対象の指定がありません") }
    }

    /**
     * issue #4 の移植用ベクトル検証骨組みです。現行コマンドへの本接続は行わず、
     * 将来Typed Value接続へ移植できる判定に留めます。
     */
    fun validateSpatialVector(
        value: SpatialVector?,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
    ) {
        if (value == null) {
            errors += nodeError(node, path, emptySet(), "ベクトルが未設定です")
            return
        }
        if (!value.isFinite()) {
            errors += nodeError(node, path, emptySet(), "ベクトルは有限値で指定してください")
        }
    }

    /**
     * issue #4 の明示領域の検証骨組みです。設定方法は直方体＋距離を継続し、
     * SPHERE／CYLINDERは予約のため現行実行では拒否します。
     */
    fun validateRegionSpec(
        spec: RegionSpec?,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        if (spec == null) {
            errors += nodeError(node, path, emptySet(), "領域が未設定です")
            return
        }
        if (spec.kind != RegionKind.CUBOID) {
            errors += nodeError(node, path, setOf("kind"), "未対応の領域形状です")
            return
        }
        listOf("dx" to spec.dx, "dy" to spec.dy, "dz" to spec.dz).forEach { (field, value) ->
            if (value != null && (!value.isFinite() || value < 0.0)) {
                errors += nodeError(node, path, setOf(field), "$field は有限な0以上の数値で指定してください")
            }
        }
        if (spec.minimumDistance != null && (!spec.minimumDistance.isFinite() || spec.minimumDistance < 0.0)) {
            errors += nodeError(node, path, setOf("minimumDistance"), "最小距離は0以上の有限値で指定してください")
        }
        if (spec.maximumDistance != null && (!spec.maximumDistance.isFinite() || spec.maximumDistance < 0.0)) {
            errors += nodeError(node, path, setOf("maximumDistance"), "最大距離は0以上の有限値で指定してください")
        }
        if (spec.minimumDistance != null && spec.maximumDistance != null &&
            spec.minimumDistance > spec.maximumDistance
        ) {
            errors += nodeError(node, path, setOf("minimumDistance"), "最小距離が最大距離を超えています")
        }
        when (spec.anchor.kind) {
            RegionAnchorKind.CONTROL_BLOCK -> Unit
            RegionAnchorKind.FIXED -> {
                val position = spec.anchor.position
                if (position == null) {
                    errors += nodeError(node, path, setOf("anchor"), "固定基準の位置が未設定です")
                } else {
                    validatePosition(position, "$path/anchor", node, errors, temporaryDefinitions)
                }
            }
            RegionAnchorKind.TEMPORARY -> {
                if (spec.anchor.tempName.isNullOrBlank()) {
                    errors += nodeError(node, path, setOf("anchor"), "一時変数基準の変数名が未設定です")
                }
            }
            RegionAnchorKind.TARGET_ENTITY -> {
                if (spec.anchor.target == null) {
                    errors += nodeError(node, path, setOf("anchor"), "対象基準の対象が未設定です")
                }
            }
        }
    }

    private fun validateFacing(
        spec: FacingSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        if (spec.kind == FacingKind.TEMPORARY) {
            checkTemporaryDefinition(spec.tempName, TemporaryVariableType.LOCATION, temporaryDefinitions, node, path, emptySet(), errors)
            if (spec.hasNonAbsoluteSemantics()) {
                errors += nodeError(node, path, setOf("semantics"), "一時変数の向きに相対・ローカルは指定できません")
            }
            return
        }
        // 意味付けはROTATION／CAPTUREDの向き成分だけの指定です。
        if (spec.hasNonAbsoluteSemantics() &&
            spec.kind != FacingKind.ROTATION && spec.kind != FacingKind.CAPTURED
        ) {
            errors += nodeError(node, path, setOf("semantics"), "相対・ローカルは向き指定のときだけ有効です")
            return
        }
        // 角度にLOCALは基準が定義できないため拒否します。
        if (spec.semanticsYaw == CoordinateSemantics.LOCAL || spec.semanticsPitch == CoordinateSemantics.LOCAL) {
            errors += nodeError(node, path, setOf("semantics"), "向きにローカルは指定できません")
        }
        // 向きは各コマンドのFacingSpecだけで解決し、前段の向きを
        // GUI設定として指定する経路は検証しません。
        if (spec.kind == FacingKind.COORDINATES && listOf(spec.x, spec.y, spec.z).any { it?.isFinite() != true }) {
            errors += nodeError(node, path, emptySet(), "向く座標が未設定または有限値ではありません")
        }
        if (spec.kind in setOf(FacingKind.CAPTURED, FacingKind.ROTATION) &&
            (spec.yaw?.isFinite() != true || spec.pitch?.isFinite() != true)
        ) {
            errors += nodeError(node, path, emptySet(), "向きが未設定または有限値ではありません")
        }
    }

    private fun validateCondition(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrNull()
        if (kind == null) {
            errors += nodeError(node, path, setOf("kind"), "条件の種類が未設定です")
            return
        }
        when (kind) {
            ConditionKind.TARGET_EXISTS -> if (node.targetSpec == null) {
                errors += nodeError(node, path, setOf("condition"), "条件の対象が未設定です")
            }
            ConditionKind.PLAYER_STATE -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("condition"), "対象プレイヤーが未設定です")
                if (node.params["sneaking"]?.isNotBlank() == true && node.params["sneaking"]?.toBooleanStrictOrNull() == null) {
                    errors += nodeError(node, path, setOf("condition"), "スニーク状態の設定が不正です")
                }
                val item = node.string("item")
                if (item.isNotBlank() && CommandValueRules.material(item, allowAir = false) == null) {
                    errors += nodeError(node, path, setOf("condition"), "所持アイテムが不正です")
                }
            }
            ConditionKind.VARIABLE_STATE -> {
                if (!CommandValueRules.isVariableName(node.string("variable"))) {
                    errors += nodeError(node, path, setOf("condition"), "評価する変数名が不正です")
                } else checkDefinition(node.string("variable"), VariableType.NUMBER, variableDefinitions, node, path, setOf("condition"), errors)
                if (node.string("operator") !in setOf(">", ">=", "<", "<=", "==", "!=")) {
                    errors += nodeError(node, path, setOf("condition"), "評価の方法が不正です")
                }
                validateNumberInput(node.string("value"), variableDefinitions, node, path, setOf("condition"), errors, temporaryDefinitions)
            }
            ConditionKind.BLOCK_STATE -> {
                if (CommandValueRules.material(node.string("block")) == null) {
                    errors += nodeError(node, path, setOf("condition"), "判定するブロックが未設定です")
                }
                // 判定位置が未設定の場合は、実行元の制御ブロック位置を使います。
            }
            ConditionKind.CONTROL_BLOCK_STATE -> {
                if (node.selectedControlBlockStates().isEmpty()) {
                    errors += nodeError(node, path, setOf("condition"), "制御ブロックの状態が未設定です")
                }
            }
        }
    }

    private fun validateVariable(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        val name = node.string("name")
        if (!CommandValueRules.isVariableName(name)) errors += nodeError(node, path, setOf("name"), "変数名が不正です")
        val type = runCatching { VariableType.valueOf(node.string("type")) }.getOrNull()
        val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }.getOrNull()
        if (type == null) errors += nodeError(node, path, setOf("type"), "変数型が不正です")
        if (operation == null) errors += nodeError(node, path, setOf("operation"), "変数操作が不正です")
        when (operation) {
            VariableOperation.DEFINE -> {
                if (type == null) return
                if (variableDefinitions?.containsKey(name) == true) {
                    errors += nodeError(node, path, setOf("name"), "同名のワールド内変数は既に定義されています")
                }
                validateAssignedValue(node.string("value"), type, variableDefinitions, node, path, errors, insideForBody)
            }
            VariableOperation.CHANGE -> {
                if (name.isNotBlank()) checkDefinition(name, null, variableDefinitions, node, path, setOf("name"), errors)
                val mode = runCatching { VariableChangeMode.valueOf(node.string("changeMode")) }.getOrNull()
                if (mode == null) {
                    errors += nodeError(node, path, setOf("changeMode"), "変数の変更内容が不正です")
                } else when (variableDefinitions?.get(name)?.type) {
                    VariableType.STRING -> if (mode == VariableChangeMode.CALCULATE) {
                        errors += nodeError(node, path, setOf("changeMode"), "文字列変数へ計算式を適用できません")
                    } else {
                        validateAssignedValue(node.string("value"), VariableType.STRING, variableDefinitions, node, path, errors, insideForBody)
                    }
                    VariableType.NUMBER -> when (mode) {
                        VariableChangeMode.CALCULATE -> validateNumericExpression(node.string("value"), name, variableDefinitions, node, path, errors, insideForBody, temporaryDefinitions)
                        VariableChangeMode.ASSIGN -> validateAssignedValue(node.string("value"), VariableType.NUMBER, variableDefinitions, node, path, errors, insideForBody, temporaryDefinitions)
                    }
                    null -> if (variableDefinitions == null) {
                        // 配置前は対象変数の型を照会できないため、代入は文字列化して
                        // 実行時へ渡し、計算式だけ構文検証します。
                        if (mode == VariableChangeMode.CALCULATE) {
                            validateNumericExpression(node.string("value"), name, variableDefinitions, node, path, errors, insideForBody, temporaryDefinitions)
                        } else {
                            validateAssignedValue(node.string("value"), VariableType.STRING, variableDefinitions, node, path, errors, insideForBody, temporaryDefinitions)
                        }
                    }
                }
            }
            null -> Unit
        }
    }

    private fun validateAssignedValue(
        raw: String,
        type: VariableType,
        definitions: Map<String, WorldVariableValue>?,
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        insideForBody: Boolean,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ) {
        if (type == VariableType.NUMBER) validateNumberInput(raw, definitions, node, path, setOf("value"), errors, temporaries)
        else validateTemplate(raw, node, path, "value", errors, definitions, temporaries = temporaries)
    }

    private fun validateNumericExpression(
        raw: String,
        selfName: String,
        definitions: Map<String, WorldVariableValue>?,
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        insideForBody: Boolean,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ) {
        val parsed = NumericExpression.parse(raw)
        if (!parsed.isSuccess) {
            // 詳細な入力エラーは入力画面側でlocaleへ解決します。実行前検証は
            // プレイヤー文脈を持たないため、モデルのErrorCodeをそのまま表示文へ
            // 埋め込まず、実行ログ・出力検証で共通の要約だけを返します。
            errors += nodeError(node, path, setOf("value"), "計算式が不正です")
            return
        }
        val expression = requireNotNull(parsed.expression)
        expression.references.forEach { reference ->
            if (SystemVariableNames.isSystemName(reference)) {
                if (!insideForBody) {
                    errors += nodeError(node, path, setOf("value"), "システム変数はfor本体内でのみ参照できます")
                }
            } else if (definitions != null && reference != selfName && definitions[reference]?.type != VariableType.NUMBER) {
                errors += nodeError(node, path, setOf("value"), "計算式の変数が未定義または数値型ではありません: $reference")
            }
        }
        expression.temporaryReferences.forEach { reference ->
            val normalized = TemporaryTemplate.normalized(reference)
            if (temporaries != null && temporaries[normalized] != TemporaryVariableType.NUMBER) {
                errors += nodeError(node, path, setOf("value"), "計算式の一時変数が未定義または数値型ではありません: $reference")
            }
        }
    }

    private fun validateFor(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        definitions: Map<String, WorldVariableValue>?,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ) {
        validatePositiveIntegerInput(
            node.string("count", "1"),
            definitions,
            node,
            path,
            setOf("count"),
            errors,
            temporaries,
        )
    }

    private fun checkDefinition(
        name: String,
        expected: VariableType?,
        definitions: Map<String, WorldVariableValue>?,
        node: CommandNode,
        path: String,
        fields: Set<String>,
        errors: MutableList<ScriptValidationError>,
    ) {
        val actual = definitions?.get(name)
        if (definitions != null && actual == null) errors += nodeError(node, path, fields, "ワールド内変数が未定義です: $name")
        if (expected != null && actual != null && actual.type != expected) {
            errors += nodeError(node, path, fields, "ワールド内変数の型が一致しません: $name")
        }
    }

    /**
     * 一時変数参照の存在と型を検証します。
     *
     * 一時変数は同一グラフ内の「一時変数を設定」ノードで定義されるため、
     * ワールド内変数とは別の定義表で照合します。定義表自体が無い場合
     * （単体テスト等）は検証を素通しします。
     */
    private fun checkTemporaryDefinition(
        rawName: String?,
        expected: TemporaryVariableType,
        definitions: Map<String, TemporaryVariableType>?,
        node: CommandNode,
        path: String,
        fields: Set<String>,
        errors: MutableList<ScriptValidationError>,
    ) {
        val name = rawName?.let(TemporaryTemplate::normalized).orEmpty()
        if (name.isBlank() || !CommandValueRules.isVariableName(name)) {
            errors += nodeError(node, path, fields, "一時変数名が不正です")
            return
        }
        if (definitions == null) return
        val actual = definitions[name]
        if (actual == null) {
            errors += nodeError(node, path, fields, "一時変数が未定義です: $name")
        } else if (actual != expected) {
            errors += nodeError(node, path, fields, "一時変数の型が一致しません: $name")
        }
    }

    /** 「探索の基準」設定を検証します。 */
    private fun validateSearchOrigin(
        search: me.awabi2048.kantancommander.model.SearchOriginSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        search.positionTemp?.takeIf(String::isNotBlank)?.let {
            checkTemporaryDefinition(it, TemporaryVariableType.LOCATION, temporaryDefinitions, node, path, emptySet(), errors)
        }
        search.position?.let { validatePosition(it, path, node, errors, temporaryDefinitions) }
    }

    /**
     * 非リテラル一時変数参照欄の一貫性を検証します。
     *
     * 参照指定時は対応するリテラル値の併記を拒否し、参照先の存在と型を確認します。
     * リテラル欄へ `%{name}%` 以外の一時記法が混入した場合もここで検出します。
     */
    private fun validateTemporaryReferences(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        fun checkRef(rawRef: String?, expected: TemporaryVariableType, literalBlank: Boolean, fields: Set<String>) {
            val ref = rawRef?.takeIf(String::isNotBlank) ?: return
            if (!literalBlank) {
                errors += nodeError(node, path, fields, "一時変数参照と直接指定は併用できません")
            }
            checkTemporaryDefinition(ref, expected, temporaryDefinitions, node, path, fields, errors)
        }
        checkRef(
            node.itemTempRef, TemporaryVariableType.ITEM,
            node.string("item").isBlank() && node.string("itemData").isBlank(), setOf("item"),
        )
        checkRef(
            node.blockTempRef, TemporaryVariableType.BLOCK,
            node.string("block").isBlank(), setOf("block"),
        )
        checkRef(
            node.soundTempRef, TemporaryVariableType.SOUND,
            node.string("sound").isBlank(), setOf("sound"),
        )
        checkRef(
            node.effectTempRef, TemporaryVariableType.EFFECT,
            node.string("effect").isBlank(), setOf("effect"),
        )
    }

    /** 「一時変数を設定」ノードを検証します。再設定は上書きのため重複検査しません。 */
    private fun validateTemporary(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        val name = TemporaryTemplate.normalized(node.string("name"))
        if (!CommandValueRules.isVariableName(name)) {
            errors += nodeError(node, path, setOf("name"), "一時変数名が不正です")
        }
        val type = TemporaryVariableType.parse(
            node.string("tempType", TemporaryVariableType.NUMBER.name),
        )
        if (type == null) {
            errors += nodeError(node, path, setOf("tempType"), "一時変数の型が不正です")
            return
        }
        when (type) {
            TemporaryVariableType.NUMBER -> validateNumberInput(
                node.string("value"), null, node, path, setOf("value"), errors, temporaryDefinitions,
            )
            TemporaryVariableType.STRING -> validateTemplate(
                node.string("value"), node, path, "value", errors, null, temporaries = temporaryDefinitions,
            )
            TemporaryVariableType.LOCATION -> {
                // 新形式では位置と向きをそれぞれ既存の共通Specで保持します。
                // 片方だけ設定されたLOCATIONは実行時に暗黙補完せず、親欄を未完了として
                // 扱います。旧x/y/z形式だけは読み込み済みデータを検証できるよう、
                // この境界で同じ数値規則へ読替えます。
                val position = node.temporaryLocationPositionSpec
                if (position != null) {
                    validatePosition(position, path, node, errors, temporaryDefinitions)
                } else if (listOf("x", "y", "z").any { node.string(it).isNotBlank() }) {
                    listOf("x" to node.string("x"), "y" to node.string("y"), "z" to node.string("z"))
                        .forEach { (field, raw) ->
                            if (raw.isBlank() || resolvedDouble(raw, null, temporaryDefinitions) == null &&
                                !deferNumericValidation(raw, null, temporaryDefinitions)
                            ) {
                                errors += nodeError(node, path, setOf("location"), "位置の$field が不正です")
                            }
                        }
                } else {
                    errors += nodeError(node, path, setOf("location"), "位置が未設定です")
                }

                val facing = node.temporaryLocationFacingSpec
                if (facing != null) {
                    validateFacing(facing, path, node, errors, temporaryDefinitions)
                } else if (listOf("yaw", "pitch").any { node.string(it).isNotBlank() }) {
                    // 旧形式では向きの省略値を0として扱っていたため、入力済みの値だけを
                    // 検証し、未入力は互換境界で0へ委ねます。
                    listOf("yaw", "pitch").forEach { field ->
                        val raw = node.string(field)
                        if (raw.isNotBlank()) {
                            validateNumberInput(raw, null, node, path, setOf("location"), errors, temporaryDefinitions)
                        }
                    }
                } else if (position == null) {
                    errors += nodeError(node, path, setOf("location"), "向きが未設定です")
                } else {
                    // 旧形式のx/y/zのみを保持する値は、従来どおり向きを0度として扱います。
                }
            }
            TemporaryVariableType.ITEM -> if (CommandValueRules.material(node.string("item"), allowAir = false) == null) {
                errors += nodeError(node, path, setOf("item"), "アイテムが未設定です")
            }
            TemporaryVariableType.BLOCK -> if (CommandValueRules.placementMaterial(node.string("block")) == null) {
                errors += nodeError(node, path, setOf("block"), "ブロックが未設定です")
            }
            TemporaryVariableType.ENTITY -> {
                if (node.temporaryEntityTargetSpec == null) {
                    val rawId = node.string("entityId")
                    if (rawId.isBlank()) {
                        errors += nodeError(node, path, setOf("entity"), "対象エンティティが未設定です")
                    } else {
                        // UUIDの形式不正は定義入力の失敗ではなく、実行時に対象が
                        // 見つからない状態です。死亡・別ワールド・未ロードと同じく、
                        // 後続の対象指定を空集合へ解決してコマンドをスキップします。
                        // ここで拒否すると、共有TargetSpecへ移行する前の保存データだけ
                        // が異なるライフサイクルになるため、参照失敗の契約へ統一します。
                    }
                }
            }
            TemporaryVariableType.SOUND -> {
                if (node.soundTempRef.isNullOrBlank() && !CommandValueRules.isSoundId(node.string("sound"))) {
                    errors += nodeError(node, path, setOf("sound"), "サウンドIDが不正です")
                }
                validateTemporaryRange(
                    node,
                    path,
                    field = "volume",
                    raw = node.string("volume", "1.0"),
                    range = 0.0..34.0,
                    message = "音量は0.0〜34.0の範囲です",
                    errors = errors,
                    temporaryDefinitions = temporaryDefinitions,
                )
                validateTemporaryRange(
                    node,
                    path,
                    field = "pitch",
                    raw = node.string("pitch", "1.0"),
                    range = 0.5..2.0,
                    message = "ピッチは0.5〜2.0の範囲です",
                    errors = errors,
                    temporaryDefinitions = temporaryDefinitions,
                )
            }
            TemporaryVariableType.EFFECT -> {
                if (node.effectTempRef.isNullOrBlank() && !CommandValueRules.isEffectId(node.string("effect"))) {
                    errors += nodeError(node, path, setOf("effect"), "エフェクトが不正です")
                }
                validateTemporaryIntegerRange(
                    node,
                    path,
                    field = "level",
                    raw = node.string("level", "1"),
                    range = 1..255,
                    message = "エフェクトレベルは1〜255の範囲です",
                    errors = errors,
                    temporaryDefinitions = temporaryDefinitions,
                )
                validateTemporaryIntegerRange(
                    node,
                    path,
                    field = "seconds",
                    raw = node.string("seconds", "30"),
                    range = 1..86_400,
                    message = "エフェクトの持続時間は1〜86400秒の範囲です",
                    errors = errors,
                    temporaryDefinitions = temporaryDefinitions,
                )
            }
        }
    }

    /** TEMP_SETの既定値を展開したうえで、複合値内の小数範囲を検証します。 */
    private fun validateTemporaryRange(
        node: CommandNode,
        path: String,
        field: String,
        raw: String,
        range: ClosedFloatingPointRange<Double>,
        message: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        validateNumberInput(raw, null, node, path, setOf(field), errors, temporaryDefinitions)
        val value = resolvedDouble(raw, null, temporaryDefinitions)
        if (!deferNumericValidation(raw, null, temporaryDefinitions) &&
            (value == null || value !in range)
        ) {
            errors += nodeError(node, path, setOf(field), message)
        }
    }

    /** TEMP_SETの既定値を展開したうえで、複合値内の整数範囲を検証します。 */
    private fun validateTemporaryIntegerRange(
        node: CommandNode,
        path: String,
        field: String,
        raw: String,
        range: IntRange,
        message: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>?,
    ) {
        validateNumberInput(raw, null, node, path, setOf(field), errors, temporaryDefinitions)
        val value = resolvedDouble(raw, null, temporaryDefinitions)
        if (!deferNumericValidation(raw, null, temporaryDefinitions) &&
            (value == null || value != kotlin.math.floor(value) || value.toInt() !in range)
        ) {
            errors += nodeError(node, path, setOf(field), message)
        }
    }

    private fun validateNumberInput(
        raw: String,
        definitions: Map<String, WorldVariableValue>?,
        node: CommandNode,
        path: String,
        fields: Set<String>,
        errors: MutableList<ScriptValidationError>,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ) {
        validateTemplate(raw, node, path, fields.firstOrNull() ?: "value", errors, definitions, temporaries = temporaries)
        if (VariableTemplate.hasMalformedReference(raw) || TemporaryTemplate.hasMalformedReference(raw)) return
        val nonNumericReferences = if (definitions == null) {
            emptyList()
        } else {
            VariableTemplate.references(raw)
                .filterNot(SystemVariableNames::isSystemName)
                .filter { definitions[it]?.type == VariableType.STRING }
        }
        if (nonNumericReferences.isNotEmpty()) {
            nonNumericReferences.forEach { reference ->
                errors += nodeError(node, path, fields, "数値欄では数値型変数だけを参照できます: $reference")
            }
            return
        }
        if (temporaries != null) {
            TemporaryTemplate.references(raw).map(TemporaryTemplate::normalized).forEach { reference ->
                if (temporaries[reference] != TemporaryVariableType.NUMBER) {
                    errors += nodeError(node, path, fields, "数値欄の一時変数が未定義または数値型ではありません: $reference")
                }
            }
        }
        val deferred = deferNumericValidation(raw, definitions, temporaries)
        if (VariableTemplate.references(raw).isEmpty() && TemporaryTemplate.references(raw).isEmpty() &&
            resolvedDouble(raw, definitions, temporaries) == null
        ) {
            errors += nodeError(node, path, fields, "数値で入力してください")
        } else if (!deferred && definitions != null && resolvedDouble(raw, definitions, temporaries) == null) {
            errors += nodeError(node, path, fields, "変数展開後の値が数値ではありません")
        }
    }

    /** 表示時間とWAITに共通する、秒数・上限・ティック単位の検証です。 */
    private fun validateTickAlignedTime(
        node: CommandNode,
        path: String,
        field: String,
        raw: String,
        minimum: Double,
        exclusiveMinimum: Boolean,
        rangeMessage: String,
        variableDefinitions: Map<String, WorldVariableValue>?,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        validateNumberInput(raw, variableDefinitions, node, path, setOf(field), errors, temporaryDefinitions)
        val value = resolvedDouble(raw, variableDefinitions, temporaryDefinitions)
        val deferred = deferNumericValidation(raw, variableDefinitions, temporaryDefinitions)
        when {
            value == null && !deferred ->
                errors += nodeError(node, path, setOf(field), rangeMessage)
            value != null && (
                !value.isFinite() ||
                    (exclusiveMinimum && value <= minimum) ||
                    (!exclusiveMinimum && value < minimum) ||
                    value > MAX_COMMAND_TIME_SECONDS
                ) ->
                errors += nodeError(node, path, setOf(field), rangeMessage)
            value != null && !CommandValueRules.isTickAlignedSeconds(value) ->
                errors += nodeError(node, path, setOf(field), "時間の設定は、1ティック = 0.05秒 の単位で行ってください")
        }
    }

    /** 数値欄へ展開結果を入れる正の整数項目の共通検証です。 */
    private fun validatePositiveIntegerInput(
        raw: String,
        definitions: Map<String, WorldVariableValue>?,
        node: CommandNode,
        path: String,
        fields: Set<String>,
        errors: MutableList<ScriptValidationError>,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ) {
        validateNumberInput(raw, definitions, node, path, fields, errors, temporaries)
        val value = resolvedDouble(raw, definitions, temporaries)
        if (!deferNumericValidation(raw, definitions, temporaries) &&
            (value == null || value != kotlin.math.floor(value) || value < 1.0 || value > Int.MAX_VALUE)
        ) {
            errors += nodeError(node, path, fields, "個数は1以上の整数である必要があります")
        }
    }

    /** エフェクトのような整数範囲を、テンプレート展開後の値へ適用します。 */
    private fun validateIntegerRange(
        node: CommandNode,
        path: String,
        field: String,
        range: IntRange,
        message: String,
        errors: MutableList<ScriptValidationError>,
        definitions: Map<String, WorldVariableValue>?,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ) {
        val raw = node.string(field)
        validateNumberInput(raw, definitions, node, path, setOf(field), errors, temporaries)
        val value = resolvedDouble(raw, definitions, temporaries)
        if (!deferNumericValidation(raw, definitions, temporaries) &&
            (value == null || value != kotlin.math.floor(value) || value.toInt() !in range)
        ) {
            errors += nodeError(node, path, setOf(field), message)
        }
    }

    private fun validateRange(
        node: CommandNode,
        path: String,
        field: String,
        range: ClosedFloatingPointRange<Double>,
        message: String,
        errors: MutableList<ScriptValidationError>,
        definitions: Map<String, WorldVariableValue>?,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ) {
        validateNumberInput(node.string(field), definitions, node, path, setOf(field), errors, temporaries)
        val value = resolvedDouble(node.string(field), definitions, temporaries)
        if (value != null && value !in range) errors += nodeError(node, path, setOf(field), message)
    }

    private fun validateTemplate(
        raw: String,
        node: CommandNode,
        path: String,
        field: String,
        errors: MutableList<ScriptValidationError>,
        definitions: Map<String, WorldVariableValue>?,
        insideForBody: Boolean = true,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ) {
        if (VariableTemplate.hasMalformedReference(raw)) {
            errors += nodeError(node, path, setOf(field), "ワールド内変数の記法が不正です")
        }
        if (TemporaryTemplate.hasMalformedReference(raw)) {
            errors += nodeError(node, path, setOf(field), "一時変数の記法が不正です")
        }
        if (!insideForBody && VariableTemplate.references(raw).any(SystemVariableNames::isSystemName)) {
            errors += nodeError(node, path, setOf(field), "システム変数はfor本体内でのみ参照できます")
        }
        if (definitions != null) {
            VariableTemplate.references(raw)
                .filterNot(SystemVariableNames::isSystemName)
                .filter { it !in definitions }
                .forEach {
                errors += nodeError(node, path, setOf(field), "ワールド内変数が未定義です: $it")
            }
        }
        if (temporaries != null) {
            TemporaryTemplate.references(raw).map(TemporaryTemplate::normalized).forEach { reference ->
                when (val type = temporaries[reference]) {
                    null -> errors += nodeError(node, path, setOf(field), "一時変数が未定義です: $reference")
                    TemporaryVariableType.NUMBER, TemporaryVariableType.STRING -> Unit
                    else -> errors += nodeError(node, path, setOf(field), "複合型の一時変数はテキストへ埋め込めません: $reference ($type)")
                }
            }
        }
    }

    private fun isTemplateOrTag(
        raw: String,
        definitions: Map<String, WorldVariableValue>?,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ): Boolean {
        if (VariableTemplate.hasMalformedReference(raw) || TemporaryTemplate.hasMalformedReference(raw)) return false
        if (VariableTemplate.references(raw).any {
                !SystemVariableNames.isSystemName(it) && definitions != null && it !in definitions
            }) return false
        if (temporaries != null && TemporaryTemplate.references(raw).map(TemporaryTemplate::normalized).any { reference ->
                temporaries[reference] == null || temporaries[reference] !in setOf(
                    TemporaryVariableType.NUMBER,
                    TemporaryVariableType.STRING,
                )
            }) {
            return false
        }
        return VariableTemplate.references(raw).isNotEmpty() || TemporaryTemplate.references(raw).isNotEmpty() ||
            CommandValueRules.isTag(raw)
    }

    /** 配置未配置のスクリプトでは、変数の存在だけを将来の実行境界へ委ねます。 */
    private fun deferNumericValidation(
        raw: String,
        definitions: Map<String, WorldVariableValue>?,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ): Boolean {
        TemporaryTemplate.references(raw).singleOrNull()?.let { temp ->
            if (!TemporaryTemplate.isSingleReference(raw) && VariableTemplate.references(raw).isNotEmpty()) return false
            if (TemporaryTemplate.isSingleReference(raw)) {
                val normalized = TemporaryTemplate.normalized(temp)
                return temporaries == null || temporaries[normalized] == TemporaryVariableType.NUMBER
            }
        }
        val reference = VariableTemplate.references(raw).singleOrNull()
            ?: return false
        if (!VariableTemplate.isSingleReference(raw)) return false
        return SystemVariableNames.isSystemName(reference) ||
            definitions == null || definitions[reference]?.type == VariableType.NUMBER
    }

    private fun resolvedDouble(
        raw: String,
        definitions: Map<String, WorldVariableValue>?,
        temporaries: Map<String, TemporaryVariableType>? = null,
    ): Double? {
        if (VariableTemplate.hasMalformedReference(raw) || TemporaryTemplate.hasMalformedReference(raw)) return null
        val tempExpanded = if (TemporaryTemplate.references(raw).isEmpty()) raw else {
            // 検証時は型表だけを見るため、一時数値は 1.0 へ置換して数値性を判定します。
            TemporaryTemplate.interpolateText(raw) { name ->
                val normalized = TemporaryTemplate.normalized(name)
                if (temporaries == null || temporaries[normalized] == TemporaryVariableType.NUMBER) "1.0" else null
            } ?: return null
        }
        val expanded = if (VariableTemplate.references(tempExpanded).isEmpty()) tempExpanded else {
            if (definitions == null) return null
            VariableTemplate.interpolate(tempExpanded) { definitions[it] } ?: return null
        }
        return expanded.toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    private fun blockVolume(from: PositionSpec, to: PositionSpec): Long? {
        val coordinates = listOf(from.x, from.y, from.z, to.x, to.y, to.z)
        if (coordinates.any { it?.isFinite() != true }) return null
        val sizes = listOf(
            kotlin.math.abs(kotlin.math.floor(to.x!!) - kotlin.math.floor(from.x!!)) + 1.0,
            kotlin.math.abs(kotlin.math.floor(to.y!!) - kotlin.math.floor(from.y!!)) + 1.0,
            kotlin.math.abs(kotlin.math.floor(to.z!!) - kotlin.math.floor(from.z!!)) + 1.0,
        )
        if (sizes.any { it > Long.MAX_VALUE.toDouble() }) return Long.MAX_VALUE
        return sizes.fold(1L) { total, size ->
            if (total > MAX_BLOCK_OPERATION_VOLUME / size.toLong().coerceAtLeast(1L)) {
                MAX_BLOCK_OPERATION_VOLUME + 1
            } else total * size.toLong()
        }
    }

    /** システム変数のスコープを全パラメータで検証し、項目ごとの検証漏れを防ぎます。 */
    private fun validateSystemReferences(
        node: CommandNode,
        path: String,
        insideForBody: Boolean,
        errors: MutableList<ScriptValidationError>,
    ) {
        if (insideForBody) return
        node.params.forEach { (field, raw) ->
            if (VariableTemplate.references(raw).any(SystemVariableNames::isSystemName)) {
                errors += nodeError(node, path, setOf(field), "システム変数はfor本体内でのみ参照できます")
            }
        }
    }

    /** 破棄した旧ループ値を文字列として保存済みのノードも実行前に拒否します。 */
    private fun validateRemovedSystemReferences(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
    ) {
        node.params.forEach { (field, raw) ->
            if (raw.contains("\$current_iteration_value") || raw.contains("\$current_loop_count")) {
                errors += nodeError(node, path, setOf(field), "旧形式のシステム変数は使用できません")
            }
        }
    }

    /** for本体の境界を越えず、読み取り専用ループ値の参照位置を検証します。 */
    private fun isInsideForBody(graph: CommandGraph, target: UUID): Boolean =
        graph.nodes.values.any { start ->
            if (start.type != CommandType.FOR_START) return@any false
            val stop = start.pairedNodeId
            val visited = mutableSetOf<UUID>()
            fun visit(id: UUID?): Boolean {
                if (id == null || id == stop || !visited.add(id)) return false
                if (id == target) return true
                val node = graph.nodes[id] ?: return false
                return listOfNotNull(node.next, node.trueNext, node.falseNext, node.pairedNodeId).any(::visit)
            }
            visit(start.trueNext)
        }

    private fun nodeError(
        node: CommandNode,
        path: String,
        fields: Set<String>,
        message: String,
        code: ErrorCode = ErrorCode.CONFIG_INVALID,
        phase: ValidationPhase = ValidationPhase.PREFLIGHT,
    ) = ScriptValidationError(path, node.id, fields, message, code = code, phase = phase)
}
