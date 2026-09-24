package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.variableScopeStrict
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
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.PlaceItemDestination
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityGroupMath
import me.awabi2048.kantancommander.model.EntityGroupOperation
import me.awabi2048.kantancommander.model.EntityTagEntries
import me.awabi2048.kantancommander.model.EntityPropertySupport
import me.awabi2048.kantancommander.model.EntityPropertyValueKind
import me.awabi2048.kantancommander.model.RegionAnchorKind
import me.awabi2048.kantancommander.model.RegionKind
import me.awabi2048.kantancommander.model.RegionSpec
import me.awabi2048.kantancommander.model.SpatialVector
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.StructureMirror
import me.awabi2048.kantancommander.model.StructureRotation
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.effectiveTrigger
import me.awabi2048.kantancommander.model.EventTriggerArguments
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.declaredArguments
import me.awabi2048.kantancommander.item.ItemStackCodec
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
     * issue #11: 建造物名として許可する形式です。ストアのファイル名と照合キーへ
     * そのまま使うため、英小文字・数字・アンダースコア・ハイフンだけを許可します。
     */
    private val STRUCTURE_NAME_PATTERN = Regex("[a-z0-9_-]{1,64}")

    /**
     * issue #24: 群名として許可する形式です。ストアの参照キーへそのまま使うため、
     * 建造物名と同じ文字種・長さに制限します。
     */
    private val ENTITY_GROUP_NAME_PATTERN = Regex("[a-z0-9_-]{1,64}")

    /**
     * issue #7/#8: 実行開始時に定義済みとなるEXECUTION変数の型表です。
     *
     * イベント引数と自ディスクの引数定義を合わせたもので、全量検証と増分検証で
     * 同じ表を使います。配置実行と呼び出し実行のどちらの役割でも参照できるよう
     * 合わせて与え、役割違いでの参照は実行時の解決失敗として扱い検証では拒否しません。
     * 呼び出し先スナップショットの種子は呼び出し側の宣言だけであり、ここでは扱いません。
     */
    internal fun seedTemporaries(script: DiskScript): Map<String, ValueType> {
        val triggerSeeds = script.effectiveTrigger().let { effective ->
            if (effective.kind == TriggerKind.EVENT && effective.event != null) {
                EventTriggerArguments.seedTypesFor(effective.event)
            } else {
                emptyMap()
            }
        }
        val argumentSeeds = script.arguments.associate { argument ->
            TemporaryTemplate.normalized(argument.name) to argument.type
        }
        return triggerSeeds + argumentSeeds
    }

    /**
     * variableDefinitionsは配置済みMyWorldでのみ渡します。
     * nullの場合は`${...}`の構文だけを検証し、未配置スクリプトの編集を妨げません。
     */
    fun validate(
        script: DiskScript,
        limits: GraphLimits = GraphLimits(),
        variableDefinitions: Map<String, TypedValue>? = null,
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
            triggerSeeds = seedTemporaries(script),
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
        // issue #7: 明示の起動方法がある場合はそちらを検証し、従来欄の検査は行いません。
        // 保存時に従来欄も整合させるため、両方の検査が同時に通る状態は作りません。
        val explicit = script.trigger
        if (explicit != null) {
            // 生値を検査します。正規化後の検査では不正な保存値を検出できません。
            if (explicit.kind == TriggerKind.TIMER &&
                !CommandValueRules.isTimerSeconds(explicit.intervalSeconds)
            ) {
                this += ScriptValidationError(
                    "root", null, setOf("trigger"),
                    "タイマー間隔は0より大きく${MAX_TIMER_SECONDS}秒以下のtick境界（0.05秒単位）で指定してください",
                    code = ErrorCode.TIMER_RANGE, phase = phase,
                )
            }
            if (explicit.kind == TriggerKind.EVENT && explicit.event == null) {
                this += ScriptValidationError(
                    "root", null, setOf("trigger"), "イベント起動には種別の指定が必要です",
                    code = ErrorCode.CONFIG_INVALID, phase = phase,
                )
            }
        } else {
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
        // issue #8: 関数引数の重複・不正名を拒否します。型は enum のため保存時に確定します。
        val argumentNames = script.arguments.map { it.name }
        if (argumentNames.size != argumentNames.toSet().size) {
            this += ScriptValidationError(
                "root", null, setOf("disk"), "引数名が重複しています",
                code = ErrorCode.CONFIG_INVALID, phase = phase,
            )
        }
        argumentNames.forEach { name ->
            if (!CommandValueRules.isVariableName(name)) {
                this += ScriptValidationError(
                    "root", null, setOf("disk"), "引数名が不正です: $name",
                    code = ErrorCode.CONFIG_INVALID, phase = phase,
                )
            }
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
        variableDefinitions: Map<String, TypedValue>? = null,
        // issue #7: 実行開始時に定義済みとなる変数（イベント引数）の型表です。
        seedTemporaries: Map<String, ValueType> = emptyMap(),
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
        val temporaryAvailability = analyzeTemporaryAvailability(effective, seedTemporaries)
        validateNode(
            node,
            "root/${node.id}",
            errors,
            variableDefinitions,
            isInsideForBody(effective, node.id),
            temporaryAvailability[node.id] ?: emptyMap(),
            phase,
            limits,
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
        variableDefinitions: Map<String, TypedValue>?,
        phase: ValidationPhase,
        // issue #8: 呼び出し連鎖の diskId 経路です。同一ディスクの再入で循環を検出します。
        callPath: List<UUID> = emptyList(),
        // issue #7: 実行開始時に定義済みとなるEXECUTION変数の型表です。
        // イベント引数を入口ノードの初期定義として与えます。
        triggerSeeds: Map<String, ValueType> = emptyMap(),
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
        val temporaryAvailability = analyzeTemporaryAvailability(effective, triggerSeeds)
        validateReturnConsistency(effective, path, errors, variableDefinitions, temporaryAvailability)
        effective.nodes.values.forEach { node ->
            validateNode(
                node,
                "$path/${node.id}",
                errors,
                variableDefinitions,
                isInsideForBody(effective, node.id),
                temporaryAvailability[node.id] ?: emptyMap(),
                phase,
                limits,
            )
            // #8: snapshot 内部は被呼出側のグラフとして、呼び出し連鎖に積んで検証します。
            node.snapshot?.let {
                val diskId = node.string("diskId").takeIf(String::isNotBlank)
                    ?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
                if (diskId != null && diskId in callPath) {
                    errors += nodeError(node, "$path/${node.id}", setOf("diskId"), "循環呼出しのため実行できません")
                } else {
                    // issue #8: 呼び出し先のグラフは、呼び出し側の引数宣言を実行開始時の
                    // 定義として検証します。実行時は変数表を空にして引数だけを渡すため、
                    // 外側の種子は引き継がず、宣言だけを与えます。
                    val callSeeds = node.declaredArguments().associate { argument ->
                        TemporaryTemplate.normalized(argument.name) to argument.type
                    }
                    validateGraph(
                        it,
                        "$path/${node.id}/snapshot",
                        errors,
                        visited,
                        limits,
                        variableDefinitions,
                        phase,
                        if (diskId != null) callPath + diskId else callPath,
                        triggerSeeds = callSeeds,
                    )
                }
            }
            node.foldedGraph?.let {
                validateGraph(it, "$path/${node.id}/folded", errors, visited, limits, variableDefinitions, phase)
            }
        }
        visited.remove(graph)
    }

    /**
     * issue #8: RETURNノードの型解決の共通処理です。
     *
     * 戻り元の変数は参照元scopeの定義表で解決します。EXECUTION は入口時点の
     * 定義表、WORLD は永続定義表を見ます。scope を無視して WORLD 定義を引くと、
     * 同名変数の型で誤判定するため、確定できない場合は null とし、
     * 呼び出し側は実行時の型安定で担保します。
     * 一貫性検証と呼出側受取検査の両方がこの解決を使い、経路間の差を作りません。
     */
    internal fun resolveReturnNodeType(
        node: CommandNode,
        temporaries: Map<String, ValueType>,
        variableDefinitions: Map<String, TypedValue>?,
    ): ValueType? {
        val source = node.string("source").takeIf(String::isNotBlank)
        if (source != null) {
            return when (VariableScope.parse(node.string("sourceScope")) ?: VariableScope.EXECUTION) {
                VariableScope.EXECUTION -> temporaries[TemporaryTemplate.normalized(source)]
                VariableScope.WORLD -> variableDefinitions?.get(source)?.type
                VariableScope.ENTITY -> null
            }
        }
        return me.awabi2048.kantancommander.model.inferLiteralType(node.string("value"))
    }

    /**
     * issue #8: 呼び出し先スナップショットの返り値型を推論します。
     *
     * 折り畳み展開とデータフロー解析は既存処理を再利用し、RETURNノードの走査順と
     * 確定規則は一貫性検証と同一です。種子は呼び出し側の引数宣言だけを使い、
     * callerのEXECUTION変数は混ぜません（実行時は変数表を空にして引数だけを渡すため）。
     * 最初に確定できる型を返し、確定できなければ null とします。
     */
    internal fun inferSnapshotReturnType(
        snapshot: CommandGraph,
        callSeeds: Map<String, ValueType>,
        variableDefinitions: Map<String, TypedValue>?,
        limits: GraphLimits,
    ): ValueType? {
        val effective = if (snapshot.nodes.values.any(FoldedGraphs::isFolded)) {
            FoldedGraphs.expand(snapshot, limits.maximumBranchDepth)
        } else {
            snapshot
        }
        val availability = analyzeTemporaryAvailability(effective, callSeeds)
        for (node in effective.nodes.values) {
            if (node.type != CommandType.RETURN_VALUE) continue
            resolveReturnNodeType(node, availability[node.id] ?: emptyMap(), variableDefinitions)
                ?.let { return it }
        }
        return null
    }

    /**
     * issue #8 の返り値型の一貫性を検証します.
     *
     * グラフ内の「値を返す」ノードのうち型を確定できるものが2種以上あれば
     * 拒否します。最初に確定した型が固定であり、最後の削除で解除されることは
     * グラフ走査の結果として自然に表れます。型解決は共通処理を使います。
     */
    private fun validateReturnConsistency(
        graph: CommandGraph,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaries: Map<UUID, Map<String, ValueType>>,
    ) {
        val known = linkedMapOf<UUID, ValueType>()
        graph.nodes.values.forEach { node ->
            if (node.type != CommandType.RETURN_VALUE) return@forEach
            val type = resolveReturnNodeType(node, temporaries[node.id] ?: emptyMap(), variableDefinitions)
                ?: return@forEach
            known[node.id] = type
        }
        val fixed = known.values.firstOrNull() ?: return
        known.forEach { (nodeId, type) ->
            if (type != fixed) {
                val node = graph.nodes[nodeId] ?: return@forEach
                errors += nodeError(node, "$path/$nodeId", setOf("source"), "戻り値の型が一致しません")
            }
        }
    }

    /**
     * 各ノードの入口で確実に定義済みとなる一時変数の型をデータフロー解析します。
     *
     * TEMP_SETは直後の経路へ型を追加・上書きします。条件分岐の合流では、全ての
     * 到達経路に存在し、かつ型が一致する名前だけを残します。異なる分岐で異なる型を
     * 設定した名前は、合流後の型を静的に決められないため未定義として扱います。
     * 実行時のセッション表は別途必ず確認し、動的な参照失敗を防御します。
     * issue #7: `seed` は実行開始時に定義済みとなる変数（イベント引数）であり、
     * 入口ノードの初期定義として与えます。
     */
    private fun analyzeTemporaryAvailability(
        graph: CommandGraph,
        seed: Map<String, ValueType> = emptyMap(),
    ): Map<UUID, Map<String, ValueType>> {
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

        fun join(states: List<Map<String, ValueType>>): Map<String, ValueType> {
            if (states.isEmpty()) return emptyMap()
            return states.first().keys.mapNotNull { name ->
                val type = states.first().getValue(name)
                if (states.drop(1).all { it[name] == type }) name to type else null
            }.toMap()
        }

        fun transfer(node: CommandNode, input: Map<String, ValueType>): Map<String, ValueType> {
            if (node.type == CommandType.TEMP_SET) {
                val name = TemporaryTemplate.normalized(node.string("name"))
                val type = ValueType.parse(
                    node.string("tempType", ValueType.NUMBER.name),
                )
                if (!CommandValueRules.isVariableName(name) || type == null) return input
                return input + (name to type)
            }
            // #2: EXECUTION scope の変数操作も定義フローへ反映します。DEFINE／SET は
            // 定義を追加・上書きし、DELETE は除去します。CHANGE／COPY は定義の
            // 有無だけを validateVariable 側で検証し、フローは変えません。
            if (node.type == CommandType.VARIABLE &&
                VariableScope.parse(node.string("scope")) == VariableScope.EXECUTION
            ) {
                val name = TemporaryTemplate.normalized(node.string("name"))
                if (!CommandValueRules.isVariableName(name)) return input
                return when (runCatching { VariableOperation.valueOf(node.string("operation")) }.getOrNull()) {
                    VariableOperation.DEFINE, VariableOperation.SET -> {
                        val type = ValueType.parse(node.string("type")) ?: return input
                        input + (name to type)
                    }
                    VariableOperation.DELETE -> input - name
                    else -> input
                }
            }
            return input
        }

        val inputs = mutableMapOf<UUID, Map<String, ValueType>>()
        val outputs = mutableMapOf<UUID, Map<String, ValueType>>()
        val queue = ArrayDeque<UUID>()
        inputs[entry] = seed
        queue.add(entry)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val node = graph.nodes[id] ?: continue
            val input = if (id == entry) {
                seed
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
        variableDefinitions: Map<String, TypedValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, ValueType>? = null,
        phase: ValidationPhase = ValidationPhase.PREFLIGHT,
        limits: GraphLimits = GraphLimits(),
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
        // 向き注視・複写の参照先は汎用対象検証へ通し、対象設定と同じ規則で検証します。
        listOfNotNull(
            node.destinationFacingSpec?.target,
            node.temporaryLocationFacingSpec?.target,
        ).forEach {
            validateTarget(it, path, node, errors, variableDefinitions, insideForBody, temporaryDefinitions)
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
            CommandType.ENTITY_OPERATION -> validateEntityOperation(node, path, errors, temporaryDefinitions)
            CommandType.PLACE_ITEM -> validatePlaceItem(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.DISPLAY_TEXT -> validateDisplayText(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.WAIT -> validateWait(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.SUMMON_ENTITY -> {
                if (!CommandValueRules.isEntityTypeId(node.string("summonEntity"))) {
                    errors += nodeError(node, path, setOf("summonEntity"), "エンティティの種類が不正です")
                }
                // 召喚タグも単一文字列です。カンマ区切りの複数タグへ展開せず、
                // 入力された値全体を一つのタグとして検証します。
                val tag = node.string("tags")
                if (tag.isNotBlank() && !isTemplateOrTag(tag, variableDefinitions, temporaryDefinitions)) {
                    errors += nodeError(node, path, setOf("tags"), "タグが不正です")
                }
                validateSummonData(node, path, errors, temporaryDefinitions)
            }
            CommandType.PLAY_SOUND -> validateSound(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.PARTICLE -> validateParticle(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.PARTICLE_GEOMETRY -> validateParticleGeometry(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.FIREWORK_LAUNCH -> validateFirework(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.APPLY_EFFECT -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
                if (!CommandValueRules.isEffectId(node.string("effect"))) {
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
            CommandType.STRUCTURE_PLACE -> validateStructurePlace(node, path, errors, temporaryDefinitions)
            CommandType.ENTITY_GROUP -> validateEntityGroup(node, path, errors, temporaryDefinitions)
            CommandType.ENTITY_DELETE -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "削除対象が未設定です")
            }
            CommandType.CONDITION -> validateCondition(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.DISK_CALL -> validateDiskCall(node, path, errors, variableDefinitions, temporaryDefinitions, limits)
            CommandType.RETURN_VALUE -> validateReturnValue(node, path, errors, variableDefinitions, temporaryDefinitions)
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
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        val operationRaw = node.params[SoundSettings.PARAM_OPERATION]
        if (!operationRaw.isNullOrBlank() && SoundOperation.parse(operationRaw).name != operationRaw.trim().uppercase()) {
            errors += nodeError(node, path, setOf("soundOperation"), "効果音の操作が不正です")
            return
        }
        when (SoundSettings.operation(node)) {
            SoundOperation.PLAY -> {
                if (!CommandValueRules.isSoundId(node.string("sound"))) {
                    errors += nodeError(node, path, setOf("soundSetting"), "サウンドIDが不正です")
                }
                validateSettingRange(node, path, "volume", 0.0..34.0, "音量は0.0〜34.0の範囲です", "soundSetting", errors, variableDefinitions, temporaryDefinitions)
                validateSettingRange(node, path, "pitch", 0.5..2.0, "ピッチは0.5〜2.0の範囲です", "soundSetting", errors, variableDefinitions, temporaryDefinitions)
                if (node.string("soundScope", "POSITION") !in setOf("POSITION", "WORLD")) {
                    errors += nodeError(node, path, setOf("soundScope"), "サウンドの再生範囲が不正です")
                }
            }
            SoundOperation.STOP -> {
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

    /** 文字列パラメータの範囲検証を、表示タブキーへ投影して報告します。 */
    private fun validateSettingRange(
        node: CommandNode,
        path: String,
        field: String,
        range: ClosedFloatingPointRange<Double>,
        message: String,
        reportField: String,
        errors: MutableList<ScriptValidationError>,
        definitions: Map<String, TypedValue>?,
        temporaries: Map<String, ValueType>? = null,
    ) {
        validateNumberInput(node.string(field), definitions, node, path, setOf(reportField), errors, temporaries)
        val value = resolvedDouble(node.string(field), definitions, temporaries)
        if (value != null && value !in range) errors += nodeError(node, path, setOf(reportField), message)
    }

    /**
     * 通常表示（PARTICLE）を検証します。
     *
     * #19の回帰対象として従来の散布検証を維持します。粒子種類と詳細データの
     * 検証は図形表示と共通です。表示方式による分岐は#30で廃止し、
     * CommandType単位で検証します。
     */
    private fun validateParticle(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        validateParticleKind(node, path, errors)
        validatePointScatter(node, path, errors, variableDefinitions, temporaryDefinitions)
    }

    /**
     * 図形表示（PARTICLE_GEOMETRY）を検証します。
     *
     * 粒子種類と詳細データの検証は通常表示と共通です。形状・密度・形状数値を
     * 検証し、幾何量が確定できる場合は見積点数の上限も確認します。
     */
    private fun validateParticleGeometry(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        val shapeRaw = node.params[ParticleGeometry.PARAM_SHAPE]
        if (!shapeRaw.isNullOrBlank() &&
            ParticleShape.parse(shapeRaw).name != shapeRaw.trim().uppercase()
        ) {
            errors += nodeError(node, path, setOf("geometrySettings"), "パーティクルの形状が不正です")
            return
        }
        validateParticleKind(node, path, errors)
        validateGeometry(node, path, errors, variableDefinitions, temporaryDefinitions)
        // #30・#29: 図形を構成する位置は原則として単一位置です。通常表示の複数許可と異なり、
        // 図形の中心・始点に対象由来の複数位置は指定できません。静的0..1は先頭1件へ解決するため許可します。
        val start = node.particlePositionSpec
        if (start != null && start.kind == PositionKind.TARGET && (start.target == null || start.target.isMultiCapable())) {
            errors += nodeError(node, path, setOf("position"), "この位置に対象由来の複数位置は指定できません")
        }
    }

    /**
     * 粒子種類と詳細データの共通検証です。通常表示と図形表示で共有します。
     *
     * 詳細データを必要とする種類だけ検証します。不要な種類の残存値は表示・
     * 実行へ漏らさないため、旧保存データの救済として対象外にします。
     */
    private fun validateParticleKind(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
    ) {
        val particle = ParticleSettings.particle(node)
        if (particle == null) {
            errors += nodeError(node, path, setOf(ParticleSettings.PARAM_PARTICLE), "パーティクルの種類が不正です")
        } else if (ParticleSettings.requiresData(particle)) {
            val data = ParticleSettings.parseData(particle, node.string(ParticleSettings.PARAM_DATA))
            if (data.isFailure) {
                errors += nodeError(node, path, setOf(ParticleSettings.PARAM_DATA), "パーティクルの詳細データが不正です")
            }
        }
    }

    /** 従来の単点散布（delta／speed／count）を検証します。 */
    private fun validatePointScatter(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
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
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
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
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
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
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
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
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
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
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
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
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
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
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
        when (node.string("action", "ride")) {
            "ride" -> if (node.secondaryTargetSpec == null) {
                errors += nodeError(node, path, setOf("other"), "乗り物となる対象が未設定です")
            }
            "dismount" -> Unit
            "motion" -> {
                // 動き設定は現在速度を指定値へ設定する意味であり、加算等の
                // modeを混在させません。成分ごとに有限数値を要求します。
                if (CommandValueRules.parseFiniteDouble(node.string("motionX")) == null) {
                    errors += nodeError(node, path, setOf("motionX"), "動きのX成分は有限な数値で指定してください")
                }
                if (CommandValueRules.parseFiniteDouble(node.string("motionY")) == null) {
                    errors += nodeError(node, path, setOf("motionY"), "動きのY成分は有限な数値で指定してください")
                }
                if (CommandValueRules.parseFiniteDouble(node.string("motionZ")) == null) {
                    errors += nodeError(node, path, setOf("motionZ"), "動きのZ成分は有限な数値で指定してください")
                }
            }
            // 旧equip／旧tagは読込時に正本化するため、ここに残る値は
            // 手組み等の不正値として不明扱いにします（#21・#32）。
            else -> errors += nodeError(node, path, setOf("action"), "不明なエンティティ操作です")
        }
    }

    /**
     * 「エンティティを操作」を検証します。
     *
     * Entity自身の状態・データ変更（タグ一覧／attribute／型付きデータ）を扱います。
     * 即時的な移動・関係変化はENTITY_ACTION側の責務であり、ここでは扱いません。
     * capabilityの判定は「確実に不可能」だけを編集時エラーとし、
     * runtimeまで不明な対象は実行時の適用可否へ委ねます（#6・#18）。
     */
    private fun validateEntityOperation(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
        when (val parsed = EntityDataPath.parse(node.string(EntityDataPath.PARAM_PATH))) {
            is EntityDataPath.Parsed.Tag -> validateEntityTagEntries(node, path, errors, temporaryDefinitions)
            is EntityDataPath.Parsed.Attribute -> validateEntityAttribute(node, path, parsed.attributeId, errors)
            is EntityDataPath.Parsed.Property -> validateEntityData(node, path, parsed.propertyId, errors, temporaryDefinitions)
            null -> errors += nodeError(node, path, setOf(EntityDataPath.PARAM_PATH), "設定する項目が未選択です")
        }
    }

    /**
     * タグ一覧の検証です。
     *
     * 空一覧・不正形式・重複・不正タグを編集時エラーとします。
     * 一時変数 `%{name}%` の参照も文字列欄として許可します。
     */
    private fun validateEntityTagEntries(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        val entries = EntityTagEntries.parse(node.string(EntityTagEntries.PARAM_ENTRIES))
        if (entries == null || entries.isEmpty()) {
            errors += nodeError(node, path, setOf(EntityTagEntries.PARAM_ENTRIES), "タグが未設定です")
            return
        }
        if (!EntityTagEntries.isValid(entries)) {
            errors += nodeError(node, path, setOf(EntityTagEntries.PARAM_ENTRIES), "タグが不正です")
            return
        }
        for (entry in entries) {
            validateTemplate(entry.tag, node, path, EntityTagEntries.PARAM_ENTRIES, errors, null, temporaries = temporaryDefinitions)
        }
    }

    /**
     * attribute基本値設定の検証です。
     *
     * modifier操作は廃止したため、IDの実在とbase値の有限性だけを検証します。
     * 対象の種類が静的に確定し、LivingEntityでないことが明らかな場合だけ
     * 非対応とします。種類不明の対象はruntime判定へ委ねます。
     */
    private fun validateEntityAttribute(
        node: CommandNode,
        path: String,
        attributeId: String,
        errors: MutableList<ScriptValidationError>,
    ) {
        if (!EntityAttributeSupport.isAttributeId(attributeId)) {
            errors += nodeError(node, path, setOf(EntityDataPath.PARAM_PATH), "attributeの指定が不正です")
            return
        }
        // 対象の種類が静的に確定し、LivingEntityでないことが明らかな場合だけ
        // 非対応とします。種類不明の対象はruntime判定へ委ねます。
        val knownType = EntityDataSchema.knownEntityType(node.targetSpec)
        if (knownType != null && !knownType.isAlive) {
            errors += nodeError(node, path, setOf(EntityDataPath.PARAM_PATH), "この対象の種類にはattributeを適用できません")
        }
        if (CommandValueRules.parseFiniteDouble(node.string(EntityAttributeSupport.PARAM_VALUE)) == null) {
            errors += nodeError(node, path, setOf(EntityAttributeSupport.PARAM_VALUE), "attribute値は有限な数値で指定してください")
        }
    }

    private fun validateEntityData(
        node: CommandNode,
        path: String,
        propertyId: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        val definition = EntityDataSchema.definition(propertyId)
        if (definition == null) {
            errors += nodeError(node, path, setOf(EntityDataPath.PARAM_PATH), "不明な設定項目です")
            return
        }
        // 確実に対象外の種類だけを編集時エラーとし、種類不明の対象は
        // 過剰に制限せずruntime判定へ委ねます（#10・#18）。
        val knownType = EntityDataSchema.knownEntityType(node.targetSpec)
        if (EntityDataSchema.staticSupport(knownType, propertyId) == EntityPropertySupport.STATICALLY_UNSUPPORTED) {
            errors += nodeError(node, path, setOf(EntityDataPath.PARAM_PATH), "この対象の種類には適用できない設定項目です")
        }
        val raw = node.string(EntityDataSchema.PARAM_VALUE)
        validatePropertyValue(node, path, EntityDataSchema.PARAM_VALUE, propertyId, raw, errors, temporaryDefinitions)
    }

    /**
     * issue #10 の型付き設定値の検証です。
     *
     * 既存Entity編集と召喚時個別設定で同じ型・候補・範囲契約を使い、
     * 保存キーの違い（単一値／召喚時複数）だけを呼び出し側で吸収します。
     * 空値は未設定と同等に扱い検査しません。
     */
    private fun validatePropertyValue(
        node: CommandNode,
        path: String,
        fieldKey: String,
        propertyId: String,
        raw: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        val definition = EntityDataSchema.definition(propertyId) ?: return
        when (definition.valueKind) {
            EntityPropertyValueKind.BOOLEAN -> {
                if (raw.toBooleanStrictOrNull() == null) {
                    errors += nodeError(node, path, setOf(fieldKey), "設定値はtrueまたはfalseで指定してください")
                }
            }
            EntityPropertyValueKind.ENUM -> {
                if (definition.enumChoices.none { it.id == raw }) {
                    errors += nodeError(node, path, setOf(fieldKey), "設定値が不正です")
                }
            }
            EntityPropertyValueKind.INTEGER -> {
                val range = definition.intRange
                val value = raw.trim().toIntOrNull()
                if (range == null || value == null || value !in range) {
                    errors += nodeError(node, path, setOf(fieldKey), "設定値が範囲外です")
                }
            }
            // 表示名は召喚時の表示名と同じくテンプレート参照を許可します。
            // Mannequinのスキン名はプレイヤー名のみを受け付け、参照なしのリテラルは構文検証します。
            EntityPropertyValueKind.TEXT -> {
                if (propertyId == EntityDataSchema.PROPERTY_MANNEQUIN_PROFILE_NAME) {
                    validateMannequinProfileName(node, path, fieldKey, raw, errors, temporaryDefinitions)
                } else {
                    validateTemplate(raw, node, path, fieldKey, errors, null, temporaries = temporaryDefinitions)
                }
            }
        }
    }

    /**
     * issue #10 の召喚時個別設定を検証します。
     *
     * 既存Entity編集と同じ項目ID・型・検証を使い、保存キーの違いだけを吸収します。
     * 旧 `customName` 欄が残る手組み値は個別設定への移行漏れとして拒否します
     * （読込時は正規化済みのため到達しません）。
     * 確実に対象外の種類だけを編集時エラーとし、種類不明の対象は
     * 過剰に制限せずruntime判定へ委ねます（#10・#18）。
     */
    private fun validateSummonData(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        if (node.string("customName").isNotBlank()) {
            errors += nodeError(node, path, setOf("customName"), "表示名は「個別データの設定」へ移行しました")
        }
        val knownType = EntityDataSchema.knownSummonType(node.string("summonEntity"))
        EntityDataSchema.summonDataEntries(node.params).forEach { (propertyId, raw) ->
            val key = EntityDataSchema.summonDataKey(propertyId)
            if (EntityDataSchema.definition(propertyId) == null) {
                errors += nodeError(node, path, setOf(key), "不明な設定項目です: $propertyId")
                return@forEach
            }
            // 種類ID文字列で静的に判定します。Registryなしで確定できる項目は
            // 編集時に検出し、それ以外は実行時のcapability判定へ委ねます。
            if (EntityDataSchema.staticSupportByName(node.string("summonEntity"), propertyId) == EntityPropertySupport.STATICALLY_UNSUPPORTED) {
                errors += nodeError(node, path, setOf(key), "この種類には適用できない設定項目です: $propertyId")
                return@forEach
            }
            validatePropertyValue(node, path, key, propertyId, raw, errors, temporaryDefinitions)
        }
    }

    /**
     * Mannequinのスキン名（プレイヤー名のみ）を検証します。
     *
     * 変数参照を含む場合は解決後の値を実行時に検証するため、ここでは
     * 記法の正しさのみを確認します。参照なしのリテラルはプレイヤー名構文を要求します。
     * descriptionは設定不可のためここでは扱いません。
     */
    private fun validateMannequinProfileName(
        node: CommandNode,
        path: String,
        fieldKey: String,
        raw: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        validateTemplate(raw, node, path, fieldKey, errors, null, temporaries = temporaryDefinitions)
        if (VariableTemplate.references(raw).isNotEmpty() || TemporaryTemplate.references(raw).isNotEmpty()) return
        if (!EntityDataSchema.isMannequinProfileName(raw)) {
            errors += nodeError(node, path, setOf(fieldKey), "スキンのプレイヤー名が不正です")
        }
    }

    /**
     * 「アイテムを配置する」(PLACE_ITEM) を検証します。
     *
     * 配置先ごとに要求する対象が異なります。エンティティ系は TargetSpec、
     * ブロックコンテナ系は blockPositionSpec を必須にし、もう一方は要求しません。
     * スロットは配置先ごとの保存形式で検証し、個数は正整数として変数参照ごと
     * 受け付けます。配置スタックの上限（保存済みitemDataの独自上限があれば
     * それを、なければ素材の標準上限）を超えるリテラル個数は、上限が解決できる
     * 場合に早期エラーにします（上限超過は実行・出力ともに失敗するため）。
     * 変数・一時参照の個数と上限が解決できない場合（サーバーなし試験など）は
     * 実行時の失敗に委ねます。
     */
    private fun validatePlaceItem(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        val destination = PlaceItemDestination.from(node.string("placeDestination"))
        if (destination == null) {
            errors += nodeError(node, path, setOf("placeDestination"), "配置先が不正です")
            return
        }
        if (destination.requiresEntityTarget && node.targetSpec == null) {
            errors += nodeError(node, path, setOf("target"), "対象が未設定です")
        }
        if (destination.requiresBlockPosition && node.blockPositionSpec == null) {
            errors += nodeError(node, path, setOf("position"), "配置位置が未設定です")
        }
        if (!PlaceItemDestination.isValidSlot(destination, node.string("slot"))) {
            errors += nodeError(node, path, setOf("slot"), "配置スロットが不正です")
        }
        if (node.itemTempRef.isNullOrBlank() && CommandValueRules.material(node.string("item"), allowAir = false) == null) {
            errors += nodeError(node, path, setOf("item"), "配置アイテムが未設定です")
        }
        validatePositiveIntegerInput(node.string("count"), variableDefinitions, node, path, setOf("count"), errors, temporaryDefinitions)
        validatePlaceItemCountLimit(node, variableDefinitions, temporaryDefinitions, path, errors)
        if (node.params["overwrite"]?.toBooleanStrictOrNull() == null) {
            errors += nodeError(node, path, setOf("overwrite"), "上書き設定が不正です")
        }
    }

    /**
     * 配置個数のスタック上限超過を、解決できる範囲で早期エラーにします。
     *
     * 実行側と同じ優先順位（保存済みitemDataの上限があればそれを、なければ
     * 素材の標準上限）で上限を解決します。リテラル個数かつ上限が確定した
     * ときだけ判定し、それ以外（変数・一時参照、上限が解決できない
     * サーバーなし試験）は実行時の失敗に委ねます。実行も出力も上限超過で
     * 失敗するため等価です。
     */
    private fun validatePlaceItemCountLimit(
        node: CommandNode,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>? = null,
        path: String,
        errors: MutableList<ScriptValidationError>,
    ) {
        val raw = node.string("count", "1")
        if (deferNumericValidation(raw, variableDefinitions, temporaryDefinitions)) return
        // 一時変数アイテムは内容が確定しないため、上限検査を飛ばします。
        if (!node.itemTempRef.isNullOrBlank()) return
        val itemDataMax = node.string("itemData").takeIf { it.isNotBlank() }?.let { encoded ->
            runCatching { ItemStackCodec.decode(encoded)?.maxStackSize }.getOrNull()
        }
        val material = CommandValueRules.material(node.string("item"), allowAir = false)
        val max = PlaceItemDestination.effectiveStackMax(
            itemDataMax,
            material?.let(PlaceItemDestination::materialMaxStackSize),
        ) ?: return
        if (PlaceItemDestination.countExceedsMax(raw, max)) {
            errors += nodeError(node, path, setOf("count"), "配置個数がスタック上限を超えています")
        }
    }

    private fun validateDisplayText(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
        if (node.string("mode") !in setOf("tellraw", "title", "subtitle", "actionbar")) {
            errors += nodeError(node, path, setOf("mode"), "不明な表示形式です")
        }
        validateTemplate(node.string("text"), node, path, "text", errors, variableDefinitions, temporaries = temporaryDefinitions)
        validateTemplate(node.string("subtitle"), node, path, "subtitle", errors, variableDefinitions, temporaries = temporaryDefinitions)
        if (DisplayTextTimingPolicy.supports(node)) {
            // issue #35-2: アクションバーは単独の表示時間だけを検証します。
            // フェード値は表示時間に加算しないため、検証対象にしません。
            // 有効項目の定義は DisplayTextTimingPolicy で共有します。
            DisplayTextTimingPolicy.effectiveTimeFields(node).forEach { field ->
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
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>? = null,
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
        temporaryDefinitions: Map<String, ValueType>? = null,
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

    /**
     * issue #11: 保存済み建造物の読み込みの検証です。建造物名の存在確認は実行時のストア照合で
     * 行い、ここでは名前形式・回転・反転・設置位置の指定だけを検証します。
     */
    private fun validateStructurePlace(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        validateStructureName(node, path, errors)
        if (StructureRotation.from(node.string("rotation", StructureRotation.NONE.value)) == null) {
            errors += nodeError(node, path, setOf("rotation"), "回転の指定が不正です")
        }
        if (StructureMirror.from(node.string("mirror", StructureMirror.NONE.value)) == null) {
            errors += nodeError(node, path, setOf("mirror"), "反転の指定が不正です")
        }
    }

    /**
     * issue #24: Entity Group の検証です。操作ごと に必要な項目だけを検証し、
     * 無関係な操作の値は無視します（ENTITY_ACTION と同一方針）。
     * 群名はストアの参照キーへそのまま使うため、英小文字・数字・
     * アンダースコア・ハイフンだけを許可します。
     */
    private fun validateEntityGroup(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        val operation = EntityGroupOperation.from(node.string("operation"))
        if (operation == null) {
            errors += nodeError(node, path, setOf("operation"), "群操作が未設定または不正です")
        }
        val name = node.string("group")
        if (name.isBlank()) {
            errors += nodeError(node, path, setOf("group"), "群が未設定です")
        } else if (!ENTITY_GROUP_NAME_PATTERN.matches(name)) {
            errors += nodeError(node, path, setOf("group"), "群名は英数字・アンダースコア・ハイフンで指定してください")
        }
        when (operation) {
            EntityGroupOperation.CREATE,
            EntityGroupOperation.ADD,
            EntityGroupOperation.REMOVE,
            -> if (node.targetSpec == null) {
                errors += nodeError(node, path, setOf("target"), "群の対象が未設定です")
            }
            EntityGroupOperation.SET_PIVOT -> {
                // CREATE では未設定時に所属中心を pivot とするため必須にしません。
                if (node.groupPivotPositionSpec == null) {
                    errors += nodeError(node, path, setOf("pivot"), "pivotが未設定です")
                }
            }
            EntityGroupOperation.MOVE -> validateGroupAxes(
                node,
                path,
                errors,
                listOf("moveX", "moveY", "moveZ"),
                EntityGroupMath::parseMoveAxis,
                "移動",
            )
            EntityGroupOperation.ROTATE -> validateGroupAxes(
                node,
                path,
                errors,
                listOf("rotateX", "rotateY", "rotateZ"),
                EntityGroupMath::parseRotateAxis,
                "回転",
            )
            EntityGroupOperation.CREATE,
            EntityGroupOperation.DELETE,
            EntityGroupOperation.CLEANUP,
            null,
            -> Unit
        }
        // pivot は CREATE／SET_PIVOT でのみ使い、指定がある場合は共通の位置検証へ通します。
        if (operation == EntityGroupOperation.CREATE || operation == EntityGroupOperation.SET_PIVOT) {
            node.groupPivotPositionSpec?.let { validatePosition(it, path, node, errors, temporaryDefinitions) }
        }
    }

    /**
     * issue #24: 移動・回転の軸入力を検証します。3軸すべて必須であり、
     * 無印は絶対、`~` は相対です。移動にローカル（`^`）は基準が定義できないため
     * 拒否し、回転は parse 側で同じく拒否します。
     */
    private fun validateGroupAxes(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        keys: List<String>,
        parse: (String) -> Pair<CoordinateSemantics, Double>?,
        label: String,
    ) {
        keys.forEach { key ->
            val parsed = parse(node.string(key))
            if (parsed == null) {
                errors += nodeError(node, path, setOf(key), "${label}の指定が不正です")
            } else if (parsed.first == CoordinateSemantics.LOCAL) {
                errors += nodeError(node, path, setOf(key), "${label}にローカルは指定できません")
            }
        }
    }

    /**
     * issue #11: 建造物名の共通検証です。変数・一時変数参照は保存時に解決できないため
     * 建造物名はリテラル固定とし、ファイル名へ展開する安全な文字だけを許可します。
     */
    private fun validateStructureName(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
    ) {
        val name = node.string("structure")
        if (name.isBlank()) {
            errors += nodeError(node, path, setOf("structure"), "建造物が未設定です")
        } else if (!STRUCTURE_NAME_PATTERN.matches(name)) {
            errors += nodeError(node, path, setOf("structure"), "建造物名は英数字・アンダースコア・ハイフンで指定してください")
        }
    }

    private fun validateTarget(
        spec: TargetSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        // issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
        // 旧保存データは読込時の型検証で隔離するため、ここでの分岐は不要です。
        if (spec.kind == TargetKind.TEMPORARY) {
            checkTemporaryDefinition(spec.tempName, ValueType.ENTITY, temporaryDefinitions, node, path, emptySet(), errors)
            // issue #review P2-9: 一時変数参照は詳細条件を使わない契約のため、
            // 残存条件の形式検査を行いません。保存時の除去・GUIの非提示・
            // 実行の非適用と合わせ、同じ禁止規則を検証にも適用します。
            // 探索基準位置だけは構造検査の対象に残します。
            spec.searchOrigin?.let { validateSearchOrigin(it, path, node, errors, temporaryDefinitions) }
            return
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
        // issue #4: 明示領域があれば共通の領域検証へ通します。未設定時は
        // 従来の dx/dy/dz と距離指定だけを使い、保存互換を維持します。
        spec.region?.takeIf { it.hasAnySetting() }?.let { region ->
            validateRegionSpec(region, "$path/region", node, errors, temporaryDefinitions)
        }
    }

    private fun validatePosition(
        spec: PositionSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        if (spec.kind == PositionKind.TEMPORARY) {
            checkTemporaryDefinition(spec.tempName, ValueType.LOCATION, temporaryDefinitions, node, path, emptySet(), errors)
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
        variableDefinitions: Map<String, TypedValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, ValueType>? = null,
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
                    // issue #24: 群 pivot の内包対象も通常の対象検証へ通します。
                    // #11 の設置位置と同じ扱いであり、旧データには存在しません。
                    node.groupPivotPositionSpec?.target,
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
            // issue #24: pivot は単一位置であり、対象由来の複数位置は指定できません。
            node.groupPivotPositionSpec,
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
            // issue #24: pivot の対象由来も内包対象なしでは解決不能です。
            node.groupPivotPositionSpec,
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
     * issue #4 の明示領域の検証です。設定方法は直方体＋距離を継続し、
     * SPHERE／CYLINDERは予約のため現行実行では拒否します。
     */
    fun validateRegionSpec(
        spec: RegionSpec?,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
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
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        if (spec.kind == FacingKind.TEMPORARY) {
            checkTemporaryDefinition(spec.tempName, ValueType.LOCATION, temporaryDefinitions, node, path, emptySet(), errors)
            if (spec.hasNonAbsoluteSemantics()) {
                errors += nodeError(node, path, setOf("semantics"), "一時変数の向きに相対・ローカルは指定できません")
            }
            return
        }
        // 意味付けはROTATIONの向き成分だけの指定です。
        if (spec.hasNonAbsoluteSemantics() &&
            spec.kind != FacingKind.ROTATION
        ) {
            errors += nodeError(node, path, setOf("semantics"), "相対・ローカルは向き指定のときだけ有効です")
            return
        }
        // 角度にLOCALは基準が定義できないため拒否します。
        if (spec.semanticsYaw == CoordinateSemantics.LOCAL || spec.semanticsPitch == CoordinateSemantics.LOCAL) {
            errors += nodeError(node, path, setOf("semantics"), "向きにローカルは指定できません")
        }
        // 注視・向き複写は単一体参照へ帰着します。複数解決は汎用単一参照として拒否します。
        // 詳細条件の妥当性は呼び出し側で汎用対象検証へ通すため、ここでは存在と単一性のみを見ます。
        if (spec.kind == FacingKind.TARGET || spec.kind == FacingKind.COPY_ORIENTATION) {
            val target = spec.target
            if (target == null) {
                errors += nodeError(node, path, emptySet(), "向き参照先の実体が未設定です")
                return
            }
            if ((target.limit ?: 1) != 1) {
                errors += nodeError(node, path, emptySet(), "向き参照は単一体のみ指定できます")
            }
            return
        }
        // 向きは各コマンドのFacingSpecだけで解決し、前段の向きを
        // GUI設定として指定する経路は検証しません。
        if (spec.kind == FacingKind.COORDINATES && listOf(spec.x, spec.y, spec.z).any { it?.isFinite() != true }) {
            errors += nodeError(node, path, emptySet(), "向く座標が未設定または有限値ではありません")
        }
        if (spec.kind == FacingKind.ROTATION &&
            (spec.yaw?.isFinite() != true || spec.pitch?.isFinite() != true)
        ) {
            errors += nodeError(node, path, emptySet(), "向きが未設定または有限値ではありません")
        }
    }

    private fun validateCondition(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        // issue #27: 判定対象ベースで検証します。条件の単一正本は `subject` 欄であり、
        // 旧 `kind` へのフォールバックは行いません。旧保存の読込は保存境界の正規化が担います。
        // 未設定・不正な判定対象は「判定対象」タブ（kind）の未設定として拒否します。
        val subject = node.conditionSubject()
        if (subject == null) {
            errors += nodeError(node, path, setOf("kind"), "判定対象が未設定です")
            return
        }
        when (subject) {
            ConditionSubject.OTHER_ENTITY -> if (node.targetSpec == null) {
                errors += nodeError(node, path, setOf("condition"), "条件の対象が未設定です")
            }
            ConditionSubject.PLAYER -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("condition"), "対象プレイヤーが未設定です")
                if (node.params["sneaking"]?.isNotBlank() == true && node.params["sneaking"]?.toBooleanStrictOrNull() == null) {
                    errors += nodeError(node, path, setOf("condition"), "スニーク状態の設定が不正です")
                }
                val item = node.string("item")
                if (item.isNotBlank() && CommandValueRules.material(item, allowAir = false) == null) {
                    errors += nodeError(node, path, setOf("condition"), "所持アイテムが不正です")
                }
            }
            ConditionSubject.VARIABLE -> {
                if (!CommandValueRules.isVariableName(node.string("variable"))) {
                    errors += nodeError(node, path, setOf("condition"), "評価する変数名が不正です")
                } else checkDefinition(node.string("variable"), ValueType.NUMBER, variableDefinitions, node, path, setOf("condition"), errors)
                if (node.string("operator") !in setOf(">", ">=", "<", "<=", "==", "!=")) {
                    errors += nodeError(node, path, setOf("condition"), "評価の方法が不正です")
                }
                validateNumberInput(node.string("value"), variableDefinitions, node, path, setOf("condition"), errors, temporaryDefinitions)
            }
            // issue #5: 型付き条件比較です。左辺の変数・演算子・右辺のリテラルを検証し、
            // 正常な不一致と解決失敗・型不一致を区別できる形にします。
            ConditionSubject.VALUE -> {
                validateValueComparison(node, path, errors, variableDefinitions, temporaryDefinitions)
            }
            ConditionSubject.BLOCK -> {
                if (CommandValueRules.material(node.string("block")) == null) {
                    errors += nodeError(node, path, setOf("condition"), "判定するブロックが未設定です")
                }
                // 判定位置が未設定の場合は、実行元の制御ブロック位置を使います。
            }
            ConditionSubject.CONTROL_BLOCK -> {
                if (node.selectedControlBlockStates().isEmpty()) {
                    errors += nodeError(node, path, setOf("condition"), "制御ブロックの状態が未設定です")
                }
            }
        }
    }

    /**
     * issue #5 の型付き条件比較を検証します。
     *
     * 左辺は EXECUTION／WORLD／ENTITY scope の変数、右辺は型に応じたリテラルです。
     * 演算子なしは Boolean 直接条件、領域演算子はノードの領域指定を使います。
     * ENTITY scope は定義表を持たないため型検査は実行時に行います。
     * 定義型が確定している場合は演算子の適用可否まで確認し、未確定の場合は
     * 形状検証だけを行って実行時へ委ねます。
     */
    private fun validateValueComparison(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        val scopeRaw = node.string("leftScope").ifBlank { VariableScope.WORLD.name }
        val scope = VariableScope.parse(scopeRaw)
        if (scope == null) {
            errors += nodeError(node, path, setOf("condition"), "比較左辺の範囲が不正です")
            return
        }
        val leftName = node.string("leftName")
        if (!CommandValueRules.isVariableName(leftName)) {
            errors += nodeError(node, path, setOf("condition"), "比較する変数名が不正です")
            return
        }
        // 定義型が確定している場合だけ、型依存の検査を行います。
        // ENTITY scope は定義表を持たないため、型検査は実行時に行います。
        val definedType: ValueType? = when (scope) {
            VariableScope.WORLD -> variableDefinitions?.get(leftName)?.type
            VariableScope.EXECUTION -> temporaryDefinitions?.get(TemporaryTemplate.normalized(leftName))
            VariableScope.ENTITY -> null
        }
        val hasTables = variableDefinitions != null || temporaryDefinitions != null
        val operator = node.string("operator")
        if (operator.isBlank()) {
            errors += nodeError(node, path, setOf("condition"), "条件の比較方法を選択してください")
            return
        }
        if (operator !in me.awabi2048.kantancommander.model.ConditionComparison.ALL) {
            errors += nodeError(node, path, setOf("condition"), "比較の方法が不正です")
            return
        }
        // issue #5: 領域述語は右辺リテランの代わりにノードの領域指定を使います。
        // 左辺はLOCATION／ENTITYに限定し、型が確定している場合に検査します。
        if (me.awabi2048.kantancommander.model.ConditionComparison.isRegionPredicate(operator)) {
            if (definedType != null && definedType != ValueType.LOCATION && definedType != ValueType.ENTITY) {
                errors += nodeError(node, path, setOf("condition"), "変数の型に使えない比較方法です: $operator")
                return
            }
            validateRegionSpec(node.regionSpec, "$path/region", node, errors, temporaryDefinitions)
            return
        }
        if (node.string("right").isBlank()) {
            errors += nodeError(node, path, setOf("condition"), "比較値が未設定です")
            return
        }
        // 定義型が確定している場合だけ、演算子の適用可否を確認します。
        if (scope != VariableScope.ENTITY && hasTables && definedType == null) {
            errors += nodeError(node, path, setOf("condition"), "比較する変数が未定義です: $leftName")
            return
        }
        if (definedType != null &&
            operator !in me.awabi2048.kantancommander.model.ConditionComparison.operatorsFor(definedType)
        ) {
            errors += nodeError(node, path, setOf("condition"), "変数の型に使えない比較方法です: $operator")
        }
    }

    /**
     * issue #2 の共通変数操作コマンドを検証します。
     *
     * scope（EXECUTION／WORLD）で定義表を切り替え、operation で操作内容を検証します。
     * 旧ノードに scope がない場合は WORLD として読み替えます。ENTITY scope は
     * #22 で接続するため、ここでは未対応として拒否します。
     *
     * - DEFINE: 新規定義のみ。型不正・重複定義・値不正を拒否します。
     * - SET: 未定義には暗黙定義、定義済みには型一致を要求します。
     * - CHANGE: 定義済みのみ。NUMBERでは changeMode（ASSIGN／CALCULATE）を検証します。
     * - COPY: 複写元は同一scope内で定義済みであること、複写先の定義型との一致を検証します。
     * - DELETE: 定義済みのみ。存在しない名前は拒否します。
     */
    /**
     * issue #22 のEntity変数操作を検証します。
     *
     * 通常の対象形式と文字列の変数名で操作し、専用の固定選択や事前DEFINEを
     * 要求しません。CHANGE／COPY／DELETE の存在確認は解決対象が実行時に決まる
     * ため静的には行わず、値の形状と生成方法だけを検証します。生成方法の検証は
     * scope 非依存の共通検証へ委ねます。
     */
    private fun validateEntityVariable(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        operation: VariableOperation?,
        mode: VariableChangeMode?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        if (node.targetSpec == null) {
            errors += nodeError(node, path, setOf("target"), "対象が未設定です")
        }
        val name = node.string("name")
        if (!CommandValueRules.isVariableName(name)) {
            errors += nodeError(node, path, setOf("name"), "変数名が不正です")
        }
        if (operation == null) {
            errors += nodeError(node, path, setOf("operation"), "変数操作が不正です")
            return
        }
        if (operation == VariableOperation.DEFINE) {
            errors += nodeError(node, path, setOf("operation"), "エンティティ変数に定義操作は不要です")
            return
        }
        if (mode == null) {
            errors += nodeError(node, path, setOf("changeMode"), "値の生成方法が不正です")
            return
        }
        // COPY の複写元は同一Entity内の変数のため、存在確認は実行時に行います。
        if (operation == VariableOperation.COPY) {
            val source = node.string("source")
            if (!CommandValueRules.isVariableName(source)) {
                errors += nodeError(node, path, setOf("source"), "複写元変数名が不正です")
            }
            return
        }
        if (operation == VariableOperation.DELETE) return
        // ASSIGN ではGUI上のtypeを使い、それ以外は生成結果の型を採用します。
        val targetType = if (mode == VariableChangeMode.ASSIGN) {
            val parsed = ValueType.parse(node.string("type"))
            if (parsed == null) {
                errors += nodeError(node, path, setOf("type"), "変数型が不正です")
                return
            }
            parsed
        } else null
        validateVariableGeneration(
            node,
            targetType,
            mode,
            VariableScope.ENTITY,
            name,
            path,
            errors,
            null,
            insideForBody,
            temporaryDefinitions,
        )
    }

    /**
     * issue #3 の値生成方法を検証します。
     *
     * targetType は ASSIGN／CALCULATE モードの対象型です。PROPERTY／RANDOM モードでは
     * 生成結果の型を採用するため null で呼び出します。scope は複写元・参照元の
     * 定義表を選択するために使います。
     */
    private fun validateVariableGeneration(
        node: CommandNode,
        targetType: ValueType?,
        mode: VariableChangeMode,
        scope: VariableScope,
        name: String,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        when (mode) {
            VariableChangeMode.ASSIGN -> {
                val type = targetType ?: return
                validateTypedNodeValue(node, type, path, errors, variableDefinitions, temporaryDefinitions)
            }
            VariableChangeMode.CALCULATE -> {
                if (targetType != null && targetType != ValueType.NUMBER) {
                    errors += nodeError(node, path, setOf("changeMode"), "数値変数以外へ計算式を適用できません")
                    return
                }
                validateNumericExpression(node.string("value"), name, variableDefinitions, node, path, errors, insideForBody, temporaryDefinitions)
            }
            VariableChangeMode.PROPERTY -> {
                val source = node.string("propertySource")
                if (!CommandValueRules.isVariableName(source)) {
                    errors += nodeError(node, path, setOf("propertySource"), "参照元変数名が不正です")
                    return
                }
                val sourceType = when (scope) {
                    VariableScope.WORLD -> variableDefinitions?.get(source)?.type
                    VariableScope.EXECUTION -> temporaryDefinitions?.get(TemporaryTemplate.normalized(source))
                    VariableScope.ENTITY -> null
                }
                val hasTables = when (scope) {
                    // #22: ENTITY scope は定義表を持たないため、存在確認は実行時に行います。
                    VariableScope.ENTITY -> false
                    else -> variableDefinitions != null || temporaryDefinitions != null
                }
                if (hasTables && sourceType == null) {
                    errors += nodeError(node, path, setOf("propertySource"), "参照元変数が未定義です: $source")
                    return
                }
                val propertyId = node.string("property")
                val definition = me.awabi2048.kantancommander.model.ValueProperties.definition(propertyId)
                if (definition == null) {
                    errors += nodeError(node, path, setOf("property"), "詳細値が不正です")
                    return
                }
                if (sourceType != null && sourceType !in definition.sources) {
                    errors += nodeError(node, path, setOf("property"), "参照元の型から取得できない詳細値です: $propertyId")
                    return
                }
                if (targetType != null && sourceType != null && definition.result != targetType) {
                    errors += nodeError(node, path, setOf("property"), "取得値の型が変数の型と一致しません: $propertyId")
                }
            }
            VariableChangeMode.RANDOM -> {
                if (targetType != null && targetType != ValueType.NUMBER) {
                    errors += nodeError(node, path, setOf("changeMode"), "数値変数以外へ乱数を適用できません")
                    return
                }
                if (CommandValueRules.parseNumericRange(node.string("range")) == null) {
                    errors += nodeError(node, path, setOf("range"), "乱数範囲を min..max 形式で指定してください")
                }
            }
        }
    }

    /**
     * issue #8 のディスク呼び出しを検証します。
     *
     * 取り出し時点の独立複写（snapshot＋引数宣言）を正本とし、live定義との
     * 差分検出は取り出し操作側で行います。ここでは自己完結性を検証します。
     * 循環呼出しはグラフ再帰の diskId 経路で検出します。
     */
    private fun validateDiskCall(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
        limits: GraphLimits,
    ) {
        if (node.snapshot == null) {
            errors += nodeError(node, path, setOf("diskId"), "呼び出すディスク内容が未設定です")
            return
        }
        // 引数宣言の厳密な解釈です。不正な項目があれば拒否します。
        val rawDeclared = node.string("declaredArgs")
        val declared = node.declaredArguments()
        if (rawDeclared.isNotBlank()) {
            val entries = rawDeclared.split(',').map(String::trim).filter(String::isNotBlank)
            if (declared.size != entries.size) {
                errors += nodeError(node, path, setOf("diskId"), "引数宣言が不正です")
                return
            }
        }
        // 宣言された全引数に対応する値を要求します。リテラルと変数の両方指定は拒否します。
        declared.forEach { argument ->
            val literal = node.string("arg_${argument.name}").takeIf(String::isNotBlank)
            val variable = node.string("argvar_${argument.name}").takeIf(String::isNotBlank)
            if (literal == null && variable == null) {
                errors += nodeError(node, path, setOf("diskId"), "引数 ${argument.name} が未設定です")
                return@forEach
            }
            if (literal != null && variable != null) {
                errors += nodeError(node, path, setOf("diskId"), "引数 ${argument.name} は値と変数の両方を指定できません")
                return@forEach
            }
            if (variable != null) {
                if (!CommandValueRules.isVariableName(variable)) {
                    errors += nodeError(node, path, setOf("diskId"), "引数 ${argument.name} の変数名が不正です")
                    return@forEach
                }
                // 呼出元EXECUTION変数の定義を確認します。未確定の場合は実行時へ委ねます。
                if (temporaryDefinitions != null) {
                    val actual = temporaryDefinitions[TemporaryTemplate.normalized(variable)]
                    if (actual == null) {
                        errors += nodeError(node, path, setOf("diskId"), "引数 ${argument.name} の変数が未定義です: $variable")
                    } else if (actual != argument.type) {
                        errors += nodeError(node, path, setOf("diskId"), "引数 ${argument.name} の型が一致しません")
                    }
                }
            } else {
                validateCallArgumentLiteral(node, path, errors, argument, literal.orEmpty())
            }
        }
        // 戻り値の受取検査は確定できる場合だけ行い、実行時の型安定で担保します。
        val returnName = node.string("returnName")
        if (returnName.isNotBlank()) {
            if (!CommandValueRules.isVariableName(returnName)) {
                errors += nodeError(node, path, setOf("diskId"), "戻り先の変数名が不正です")
                return
            }
            val returnScopeRaw = node.string("returnScope").ifBlank { VariableScope.EXECUTION.name }
            val returnScope = VariableScope.parse(returnScopeRaw)
            if (returnScope == null || returnScope == VariableScope.ENTITY) {
                errors += nodeError(node, path, setOf("diskId"), "戻り先の範囲が不正です")
                return
            }
            val receiverType: ValueType? = when (returnScope) {
                VariableScope.WORLD -> variableDefinitions?.get(returnName)?.type
                VariableScope.EXECUTION -> temporaryDefinitions?.get(TemporaryTemplate.normalized(returnName))
                VariableScope.ENTITY -> null
            }
            val hasTables = variableDefinitions != null || temporaryDefinitions != null
            if (hasTables && receiverType == null) {
                errors += nodeError(node, path, setOf("diskId"), "戻り先の変数が未定義です: $returnName")
                return
            }
            val calleeReturn = node.snapshot?.let { snapshot ->
                // issue #8: 呼び出し先の返り値は共通解決で求めます。
                // 種子は呼び出し側の引数宣言だけを使い、callerのEXECUTION変数は
                // 混ぜません。引数・ローカル定義・WORLD変数を別々に探索する
                // 処理は残さず、一貫性検証と同じ解析結果を使います。
                val callSeeds = declared.associate { argument ->
                    TemporaryTemplate.normalized(argument.name) to argument.type
                }
                inferSnapshotReturnType(snapshot, callSeeds, variableDefinitions, limits)
            }
            if (receiverType != null && calleeReturn != null && receiverType != calleeReturn) {
                errors += nodeError(node, path, setOf("diskId"), "戻り値の型が一致しません")
            }
        }
    }

    /** 数値欄でも単一のワールド変数・一時変数を実行時に数値化できるようにします。 */
    private fun isNumericTemplate(raw: String): Boolean =
        VariableTemplate.isSingleReference(raw) || TemporaryTemplate.isSingleReference(raw)

    /** 呼出引数のリテラルを宣言型で検証します。複合型は変数指定を要求します。 */
    private fun validateCallArgumentLiteral(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        argument: me.awabi2048.kantancommander.model.DiskArgument,
        raw: String,
    ) {
        when (argument.type) {
            ValueType.NUMBER -> {
                if (!isNumericTemplate(raw) && CommandValueRules.parseFiniteDouble(raw) == null) {
                    errors += nodeError(node, path, setOf("diskId"), "引数 ${argument.name} の数値が不正です")
                }
            }            ValueType.STRING -> {
                if (VariableTemplate.hasMalformedReference(raw) || TemporaryTemplate.hasMalformedReference(raw)) {
                    errors += nodeError(node, path, setOf("diskId"), "引数 ${argument.name} の文字列が不正です")
                }
            }
            ValueType.LOCATION,
            ValueType.ITEM,
            ValueType.BLOCK,
            ValueType.ENTITY,
            -> errors += nodeError(node, path, setOf("diskId"), "複合型の引数 ${argument.name} は変数で指定します")
        }
    }

    /**
     * issue #8 の「値を返す」を検証します。
     *
     * 変数由来は定義の存在を確認し、リテラルは常に受理します。返り値型の
     * 一貫性（全RETURNの確定型の一致）はグラフ単位で別途検証します。
     */
    private fun validateReturnValue(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        val source = node.string("source").takeIf(String::isNotBlank) ?: return
        if (!CommandValueRules.isVariableName(source)) {
            errors += nodeError(node, path, setOf("source"), "戻り元の変数名が不正です")
            return
        }
        val scopeRaw = node.string("sourceScope").ifBlank { VariableScope.EXECUTION.name }
        val scope = VariableScope.parse(scopeRaw)
        if (scope == null || scope == VariableScope.ENTITY) {
            errors += nodeError(node, path, setOf("source"), "戻り元の範囲が不正です")
            return
        }
        val defined: ValueType? = when (scope) {
            VariableScope.WORLD -> variableDefinitions?.get(source)?.type
            VariableScope.EXECUTION -> temporaryDefinitions?.get(TemporaryTemplate.normalized(source))
            VariableScope.ENTITY -> null
        }
        val hasTables = variableDefinitions != null || temporaryDefinitions != null
        if (hasTables && defined == null) {
            errors += nodeError(node, path, setOf("source"), "戻り元の変数が未定義です: $source")
        }
    }

    private fun validateVariable(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, TypedValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, ValueType>? = null,
    ) {
        val scope = node.variableScopeStrict()
        if (scope == null) {
            errors += nodeError(node, path, setOf("scope"), "変数の範囲が不正です")
            return
        }
        val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }.getOrNull()
        val mode = runCatching { VariableChangeMode.valueOf(node.string("changeMode")) }.getOrNull()
        // #22: ENTITY scope は事前DEFINEを要求しません。対象指定と名前・値の
        // 妥当性だけを検証し、存在確認は実行時に行います。
        if (scope == VariableScope.ENTITY) {
            validateEntityVariable(node, path, errors, operation, mode, insideForBody, temporaryDefinitions)
            return
        }
        val name = node.string("name")
        if (!CommandValueRules.isVariableName(name)) errors += nodeError(node, path, setOf("name"), "変数名が不正です")
        val type = ValueType.parse(node.string("type"))
        // PROPERTY／RANDOM モードでは生成結果の型を採用するため、GUI上のtypeは参照しません。
        if (type == null && (mode == null || mode == VariableChangeMode.ASSIGN || mode == VariableChangeMode.CALCULATE)) {
            errors += nodeError(node, path, setOf("type"), "変数型が不正です")
        }
        if (operation == null) errors += nodeError(node, path, setOf("operation"), "変数操作が不正です")
        if (operation == null) return
        // scope ごとの定義型を解決します。WORLD は永続定義表、EXECUTION は
        // フロー解析の定義表を使います。表自体がない場合は検証を素通しします。
        fun definedType(target: String): ValueType? = when (scope) {
            VariableScope.WORLD -> variableDefinitions?.get(target)?.type
            VariableScope.EXECUTION -> temporaryDefinitions?.get(TemporaryTemplate.normalized(target))
            VariableScope.ENTITY -> null
        }
        fun hasDefinitions(): Boolean = when (scope) {
            VariableScope.WORLD -> variableDefinitions != null
            VariableScope.EXECUTION -> temporaryDefinitions != null
            VariableScope.ENTITY -> false
        }
        when (operation) {
            VariableOperation.DEFINE -> {
                if (definedType(name) != null) {
                    errors += nodeError(node, path, setOf("name"), "同名の変数は既に定義されています")
                }
                if (mode == null) {
                    errors += nodeError(node, path, setOf("changeMode"), "値の生成方法が不正です")
                    return
                }
                // PROPERTY／RANDOM モードでは生成結果の型を採用するため、GUI上の
                // type は参照しません。ASSIGN／CALCULATE では型が必須です。
                val effective = if (mode == VariableChangeMode.ASSIGN || mode == VariableChangeMode.CALCULATE) {
                    type ?: run {
                        errors += nodeError(node, path, setOf("type"), "変数型が不正です")
                        return
                    }
                } else null
                validateVariableGeneration(node, effective, mode, scope, name, path, errors, variableDefinitions, insideForBody, temporaryDefinitions)
            }
            VariableOperation.SET -> {
                if (mode == null) {
                    errors += nodeError(node, path, setOf("changeMode"), "値の生成方法が不正です")
                    return
                }
                // issue #2: EXECUTION／WORLD は明示定義を前提とします。定義表が
                // あるのに未定義の場合は、DEFINEの省略や変数名の誤入力を拒否します。
                // 定義表がない編集時だけは型欄での確定を許し、配置時の事前検証で
                // 改めて未定義として検出します。
                val defined = definedType(name)
                if (hasDefinitions() && defined == null) {
                    if (name.isNotBlank()) {
                        errors += nodeError(node, path, setOf("name"), "変数が未定義です: $name")
                    }
                    return
                }
                // 定義済みの型を正とします。PROPERTY／RANDOM モードでは生成結果の型を採用します。
                val effective = if (mode == VariableChangeMode.ASSIGN || mode == VariableChangeMode.CALCULATE) {
                    val candidate = defined ?: type
                    if (candidate == null) {
                        errors += nodeError(node, path, setOf("type"), "変数型が不正です")
                        return
                    }
                    if (type != null && candidate != type && defined != null) {
                        errors += nodeError(node, path, setOf("type"), "変数の型が一致しません: $name")
                        return
                    }
                    candidate
                } else null
                validateVariableGeneration(node, effective, mode, scope, name, path, errors, variableDefinitions, insideForBody, temporaryDefinitions)
            }
            VariableOperation.CHANGE -> {
                val defined = definedType(name)
                if (hasDefinitions() && defined == null) {
                    if (name.isNotBlank()) {
                        errors += nodeError(node, path, setOf("name"), "変数が未定義です: $name")
                    }
                    return
                }
                if (mode == null) {
                    errors += nodeError(node, path, setOf("changeMode"), "変数の変更内容が不正です")
                    return
                }
                // 定義型が確定している場合は生成結果との整合を確認します。配置前など
                // 定義表がない場合は構文検証だけを行い、型の確定は実行時へ委ねます。
                val effective = defined
                if (defined != null && mode == VariableChangeMode.CALCULATE && defined != ValueType.NUMBER) {
                    errors += nodeError(node, path, setOf("changeMode"), "数値変数以外へ計算式を適用できません")
                    return
                }
                if (defined != null && mode == VariableChangeMode.RANDOM && defined != ValueType.NUMBER) {
                    errors += nodeError(node, path, setOf("changeMode"), "数値変数以外へ乱数を適用できません")
                    return
                }
                if (defined == null && variableDefinitions == null && temporaryDefinitions == null) {
                    if (mode == VariableChangeMode.CALCULATE) {
                        validateNumericExpression(node.string("value"), name, variableDefinitions, node, path, errors, insideForBody, temporaryDefinitions)
                    } else if (mode == VariableChangeMode.ASSIGN) {
                        validateTypedNodeValue(node, ValueType.STRING, path, errors, variableDefinitions, temporaryDefinitions)
                    } else {
                        validateVariableGeneration(node, null, mode, scope, name, path, errors, variableDefinitions, insideForBody, temporaryDefinitions)
                    }
                    return
                }
                if (effective != null || mode == VariableChangeMode.PROPERTY || mode == VariableChangeMode.RANDOM) {
                    validateVariableGeneration(node, effective, mode, scope, name, path, errors, variableDefinitions, insideForBody, temporaryDefinitions)
                }
            }
            VariableOperation.COPY -> {
                val source = node.string("source")
                if (!CommandValueRules.isVariableName(source)) {
                    errors += nodeError(node, path, setOf("source"), "複写元変数名が不正です")
                    return
                }
                val sourceType = definedType(source)
                if (hasDefinitions() && sourceType == null) {
                    errors += nodeError(node, path, setOf("source"), "複写元変数が未定義です: $source")
                    return
                }
                val targetType = definedType(name)
                // issue #2: 複写先も明示定義を前提とします。未定義名へのCOPYは
                // 変数名の誤入力を隠すため拒否します。
                if (hasDefinitions() && targetType == null) {
                    if (name.isNotBlank()) {
                        errors += nodeError(node, path, setOf("name"), "変数が未定義です: $name")
                    }
                    return
                }
                if (sourceType != null && targetType != null && sourceType != targetType) {
                    errors += nodeError(node, path, setOf("name"), "複写先の変数の型が一致しません: $name")
                }
            }
            VariableOperation.DELETE -> {
                if (hasDefinitions() && definedType(name) == null) {
                    if (name.isNotBlank()) {
                        errors += nodeError(node, path, setOf("name"), "変数が未定義です: $name")
                    }
                }
            }
        }
    }

    private fun validateNumericExpression(
        raw: String,
        selfName: String,
        definitions: Map<String, TypedValue>?,
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        insideForBody: Boolean,
        temporaries: Map<String, ValueType>? = null,
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
        // issue #3: 計算に使用できる値は変数同士に限定します。数値リテラルを
        // 含む式は構文としては正しくても CALCULATE では拒否し、単純な数値代入は
        // ASSIGN、乱数は RANDOM を使わせます。
        if (expression.hasLiteralOperand()) {
            errors += nodeError(node, path, setOf("value"), "計算に使える値は変数同士に限ります")
            return
        }
        expression.references.forEach { reference ->
            if (SystemVariableNames.isSystemName(reference)) {
                if (!insideForBody) {
                    errors += nodeError(node, path, setOf("value"), "システム変数はfor本体内でのみ参照できます")
                }
            } else if (definitions != null && reference != selfName && definitions[reference]?.type != ValueType.NUMBER) {
                errors += nodeError(node, path, setOf("value"), "計算式の変数が未定義または数値型ではありません: $reference")
            }
        }
        expression.temporaryReferences.forEach { reference ->
            val normalized = TemporaryTemplate.normalized(reference)
            if (temporaries != null && temporaries[normalized] != ValueType.NUMBER) {
                errors += nodeError(node, path, setOf("value"), "計算式の一時変数が未定義または数値型ではありません: $reference")
            }
        }
    }

    private fun validateFor(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        definitions: Map<String, TypedValue>?,
        temporaries: Map<String, ValueType>? = null,
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
        expected: ValueType?,
        definitions: Map<String, TypedValue>?,
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
        expected: ValueType,
        definitions: Map<String, ValueType>?,
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
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        search.positionTemp?.takeIf(String::isNotBlank)?.let {
            checkTemporaryDefinition(it, ValueType.LOCATION, temporaryDefinitions, node, path, emptySet(), errors)
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
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        fun checkRef(rawRef: String?, expected: ValueType, literalBlank: Boolean, fields: Set<String>) {
            val ref = rawRef?.takeIf(String::isNotBlank) ?: return
            if (!literalBlank) {
                errors += nodeError(node, path, fields, "一時変数参照と直接指定は併用できません")
            }
            checkTemporaryDefinition(ref, expected, temporaryDefinitions, node, path, fields, errors)
        }
        checkRef(
            node.itemTempRef, ValueType.ITEM,
            node.string("item").isBlank() && node.string("itemData").isBlank(), setOf("item"),
        )
        checkRef(
            node.blockTempRef, ValueType.BLOCK,
            node.string("block").isBlank(), setOf("block"),
        )
    }

    /** 「一時変数を設定」ノードを検証します。再設定は上書きのため重複検査しません。 */
    private fun validateTemporary(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        val name = TemporaryTemplate.normalized(node.string("name"))
        if (!CommandValueRules.isVariableName(name)) {
            errors += nodeError(node, path, setOf("name"), "一時変数名が不正です")
        }
        val type = ValueType.parse(
            node.string("tempType", ValueType.NUMBER.name),
        )
        if (type == null) {
            errors += nodeError(node, path, setOf("tempType"), "一時変数の型が不正です")
            return
        }
        val mode = runCatching { VariableChangeMode.valueOf(node.string("changeMode")) }
            .getOrDefault(VariableChangeMode.ASSIGN)
        if (mode == VariableChangeMode.RANDOM) {
            if (type != ValueType.NUMBER) {
                errors += nodeError(node, path, setOf("changeMode"), "乱数は数値型の一時変数にだけ指定できます")
            } else if (CommandValueRules.parseNumericRange(node.string("range")) == null) {
                errors += nodeError(node, path, setOf("range"), "乱数範囲を min..max 形式で指定してください")
            }
            return
        }
        if (mode != VariableChangeMode.ASSIGN) {
            errors += nodeError(node, path, setOf("changeMode"), "一時変数の値指定方法が不正です")
            return
        }
        validateTypedNodeValue(node, type, path, errors, null, temporaryDefinitions)
    }

    /**
     * issue #2 の型別値検証です。
     *
     * 一時変数の設定と変数操作コマンド（DEFINE／SET／CHANGE値）で共有します。
     * NUMBER・STRING は値欄から、複合型は型ごとの共通設定から
     * 検証します。実行時の [SequenceExecutor.resolveNodeValue] と同じ入力を
     * 受け付けることが契約です。
     */
    private fun validateTypedNodeValue(
        node: CommandNode,
        type: ValueType,
        path: String,
        errors: MutableList<ScriptValidationError>,
        worldDefinitions: Map<String, TypedValue>?,
        temporaryDefinitions: Map<String, ValueType>?,
    ) {
        when (type) {
            ValueType.NUMBER -> validateNumberInput(
                node.string("value"), worldDefinitions, node, path, setOf("value"), errors, temporaryDefinitions,
            )
            ValueType.STRING -> validateTemplate(
                node.string("value"), node, path, "value", errors, worldDefinitions, temporaries = temporaryDefinitions,
            )
            ValueType.LOCATION -> {
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
            ValueType.ITEM -> if (CommandValueRules.material(node.string("item"), allowAir = false) == null) {
                errors += nodeError(node, path, setOf("item"), "アイテムが未設定です")
            }
            ValueType.BLOCK -> if (CommandValueRules.placementMaterial(node.string("block")) == null) {
                errors += nodeError(node, path, setOf("block"), "ブロックが未設定です")
            }
            ValueType.ENTITY -> {
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
        temporaryDefinitions: Map<String, ValueType>?,
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
        temporaryDefinitions: Map<String, ValueType>?,
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
        definitions: Map<String, TypedValue>?,
        node: CommandNode,
        path: String,
        fields: Set<String>,
        errors: MutableList<ScriptValidationError>,
        temporaries: Map<String, ValueType>? = null,
    ) {
        validateTemplate(raw, node, path, fields.firstOrNull() ?: "value", errors, definitions, temporaries = temporaries)
        if (VariableTemplate.hasMalformedReference(raw) || TemporaryTemplate.hasMalformedReference(raw)) return
        val nonNumericReferences = if (definitions == null) {
            emptyList()
        } else {
            VariableTemplate.references(raw)
                .filterNot(SystemVariableNames::isSystemName)
                .filter { definitions[it]?.type == ValueType.STRING }
        }
        if (nonNumericReferences.isNotEmpty()) {
            nonNumericReferences.forEach { reference ->
                errors += nodeError(node, path, fields, "数値欄では数値型変数だけを参照できます: $reference")
            }
            return
        }
        if (temporaries != null) {
            TemporaryTemplate.references(raw).map(TemporaryTemplate::normalized).forEach { reference ->
                if (temporaries[reference] != ValueType.NUMBER) {
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
        variableDefinitions: Map<String, TypedValue>?,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, ValueType>? = null,
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
        definitions: Map<String, TypedValue>?,
        node: CommandNode,
        path: String,
        fields: Set<String>,
        errors: MutableList<ScriptValidationError>,
        temporaries: Map<String, ValueType>? = null,
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
        definitions: Map<String, TypedValue>?,
        temporaries: Map<String, ValueType>? = null,
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
        definitions: Map<String, TypedValue>?,
        temporaries: Map<String, ValueType>? = null,
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
        definitions: Map<String, TypedValue>?,
        insideForBody: Boolean = true,
        temporaries: Map<String, ValueType>? = null,
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
                    ValueType.NUMBER, ValueType.STRING -> Unit
                    else -> errors += nodeError(node, path, setOf(field), "複合型の一時変数はテキストへ埋め込めません: $reference ($type)")
                }
            }
        }
    }

    private fun isTemplateOrTag(
        raw: String,
        definitions: Map<String, TypedValue>?,
        temporaries: Map<String, ValueType>? = null,
    ): Boolean {
        if (VariableTemplate.hasMalformedReference(raw) || TemporaryTemplate.hasMalformedReference(raw)) return false
        if (VariableTemplate.references(raw).any {
                !SystemVariableNames.isSystemName(it) && definitions != null && it !in definitions
            }) return false
        if (temporaries != null && TemporaryTemplate.references(raw).map(TemporaryTemplate::normalized).any { reference ->
                temporaries[reference] == null || temporaries[reference] !in setOf(
                    ValueType.NUMBER,
                    ValueType.STRING,
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
        definitions: Map<String, TypedValue>?,
        temporaries: Map<String, ValueType>? = null,
    ): Boolean {
        TemporaryTemplate.references(raw).singleOrNull()?.let { temp ->
            if (!TemporaryTemplate.isSingleReference(raw) && VariableTemplate.references(raw).isNotEmpty()) return false
            if (TemporaryTemplate.isSingleReference(raw)) {
                val normalized = TemporaryTemplate.normalized(temp)
                return temporaries == null || temporaries[normalized] == ValueType.NUMBER
            }
        }
        val reference = VariableTemplate.references(raw).singleOrNull()
            ?: return false
        if (!VariableTemplate.isSingleReference(raw)) return false
        return SystemVariableNames.isSystemName(reference) ||
            definitions == null || definitions[reference]?.type == ValueType.NUMBER
    }

    private fun resolvedDouble(
        raw: String,
        definitions: Map<String, TypedValue>?,
        temporaries: Map<String, ValueType>? = null,
    ): Double? {
        if (VariableTemplate.hasMalformedReference(raw) || TemporaryTemplate.hasMalformedReference(raw)) return null
        val tempExpanded = if (TemporaryTemplate.references(raw).isEmpty()) raw else {
            // 検証時は型表だけを見るため、一時数値は 1.0 へ置換して数値性を判定します。
            TemporaryTemplate.interpolateText(raw) { name ->
                val normalized = TemporaryTemplate.normalized(name)
                if (temporaries == null || temporaries[normalized] == ValueType.NUMBER) "1.0" else null
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
