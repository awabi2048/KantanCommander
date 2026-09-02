package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.DisplayTextTimingPolicy
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.MAX_BLOCK_OPERATION_VOLUME
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.MIN_TIMER_SECONDS
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableTemplate
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
import me.awabi2048.kantancommander.model.effectiveContextSource
import me.awabi2048.kantancommander.model.hasContextOverride
import me.awabi2048.kantancommander.model.supportsContextOverride
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

/** 実行前検証の結果を、表示文字列ではなく対象ノードと設定項目で保持します。 */
data class ScriptValidationError(
    val path: String,
    val nodeId: UUID?,
    val fieldKeys: Set<String>,
    val message: String,
) {
    fun rendered(): String = "$path: $message"
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
        val errors = mutableListOf<ScriptValidationError>()
        if (!script.timer.enabled && script.activation == ActivationMode.ALWAYS_ACTIVE) {
            errors += ScriptValidationError("root", null, setOf("timer"), "タイマーオフでは常時実行を使用できません")
        }
        if (script.timer.enabled && script.timer.intervalSeconds !in MIN_TIMER_SECONDS..MAX_TIMER_SECONDS) {
            errors += ScriptValidationError(
                "root", null, setOf("timer"),
                "タイマー間隔は${MIN_TIMER_SECONDS}から${MAX_TIMER_SECONDS}秒で指定してください",
            )
        }
        validateGraph(
            script.graph,
            "root",
            errors,
            Collections.newSetFromMap(IdentityHashMap()),
            limits,
            variableDefinitions,
        )
        return errors
    }

    private fun validateGraph(
        graph: CommandGraph,
        path: String,
        errors: MutableList<ScriptValidationError>,
        visited: MutableSet<CommandGraph>,
        limits: GraphLimits,
        variableDefinitions: Map<String, WorldVariableValue>?,
    ) {
        if (!visited.add(graph)) {
            errors += ScriptValidationError(path, null, emptySet(), "別ディスクのコピー内容が循環参照しています")
            return
        }
        GraphValidator.validate(graph, limits).forEach { errors += ScriptValidationError(path, null, emptySet(), it) }
        graph.nodes.values.forEach { node ->
            validateNode(node, "$path/${node.id}", errors, variableDefinitions, isInsideForBody(graph, node.id))
            node.snapshot?.let {
                validateGraph(it, "$path/${node.id}/snapshot", errors, visited, limits, variableDefinitions)
            }
        }
        visited.remove(graph)
    }

    private fun validateNode(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        insideForBody: Boolean,
    ) {
        validateSystemReferences(node, path, insideForBody, errors)
        validateRemovedSystemReferences(node, path, errors)
        val hasContextState = node.hasContextOverride() || node.effectiveContextSource != ContextSource.BASE
        if (hasContextState && node.type != CommandType.CONTEXT && !node.type.supportsContextOverride()) {
            errors += nodeError(node, path, emptySet(), "${node.type} では実行コンテキストを設定できません")
        }
        listOfNotNull(node.targetSpec, node.secondaryTargetSpec, node.destinationTargetSpec).forEach {
            validateTarget(it, path, node, errors, variableDefinitions, insideForBody)
        }
        listOfNotNull(
            node.destinationSpec,
            node.conditionPositionSpec,
            node.blockPositionSpec,
            node.blockFromSpec,
            node.blockToSpec,
            node.soundPositionSpec,
            node.summonPositionSpec,
            node.contextOverride?.position,
        ).forEach { validatePosition(it, path, node, errors) }
        node.destinationFacingSpec?.let { validateFacing(it, path, node, errors) }
        node.contextOverride?.let { context ->
            listOfNotNull(context.executor, context.target).forEach {
                validateTarget(it, path, node, errors, variableDefinitions, insideForBody)
            }
            context.facing?.let { validateFacing(it, path, node, errors) }
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
                if (CommandValueRules.material(node.string("item"), allowAir = false) == null) {
                    errors += nodeError(node, path, setOf("item"), "アイテムが未設定です")
                }
                validatePositiveIntegerInput(node.string("count"), variableDefinitions, node, path, setOf("count"), errors)
            }
            CommandType.ENTITY_ACTION -> validateEntityAction(node, path, errors)
            CommandType.DISPLAY_TEXT -> validateDisplayText(node, path, errors, variableDefinitions)
            CommandType.WAIT -> validateWait(node, path, errors, variableDefinitions)
            CommandType.SUMMON_ENTITY -> {
                if (!CommandValueRules.isEntityTypeId(node.string("entity"))) {
                    errors += nodeError(node, path, setOf("entity"), "エンティティ種類が不正です")
                }
                // 召喚タグも単一文字列です。カンマ区切りの複数タグへ展開せず、
                // 入力された値全体を一つのタグとして検証します。
                val tag = node.string("tags")
                if (tag.isNotBlank() && !isTemplateOrTag(tag, variableDefinitions)) {
                    errors += nodeError(node, path, setOf("tags"), "タグが不正です")
                }
                validateTemplate(node.string("customName"), node, path, "customName", errors, variableDefinitions)
            }
            CommandType.PLAY_SOUND -> {
                if (!CommandValueRules.isSoundId(node.string("sound"))) {
                    errors += nodeError(node, path, setOf("sound"), "サウンドIDが不正です")
                }
                validateRange(node, path, "volume", 0.0..34.0, "音量は0.0〜34.0の範囲です", errors, variableDefinitions)
                validateRange(node, path, "pitch", 0.5..2.0, "ピッチは0.5〜2.0の範囲です", errors, variableDefinitions)
                if (node.string("soundScope", "CONTEXT") !in setOf("CONTEXT", "WORLD")) {
                    errors += nodeError(node, path, setOf("soundPosition"), "サウンドの再生位置が不正です")
                }
            }
            CommandType.APPLY_EFFECT -> {
                if (!CommandValueRules.isEffectId(node.string("effect"))) {
                    errors += nodeError(node, path, setOf("effect"), "エフェクト種類が不正です")
                }
                validateIntegerRange(node, path, "level", 1..255, "エフェクトレベルは1〜255の範囲です", errors, variableDefinitions)
                validateIntegerRange(node, path, "seconds", 1..86_400, "効果時間は1〜86400秒の範囲です", errors, variableDefinitions)
            }
            CommandType.CAMERA_SHAKE -> {
                validateRange(node, path, "intensity", 0.1..4.0, "揺れの強さは0.1〜4.0の範囲です", errors, variableDefinitions)
                validateRange(node, path, "seconds", 1.0..10.0, "揺れ時間は1.0〜10.0秒の範囲です", errors, variableDefinitions)
                if (node.string("shakeType") !in setOf("positional", "rotational")) {
                    errors += nodeError(node, path, setOf("shakeType"), "揺れ種類が不正です")
                }
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "カメラ揺れの対象が未設定です")
            }
            CommandType.BLOCK_OPERATION -> validateBlockOperation(node, path, errors)
            CommandType.ENTITY_DELETE -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "削除対象が未設定です")
            }
            CommandType.CONDITION -> validateCondition(node, path, errors, variableDefinitions)
            CommandType.CONTEXT -> if (!node.hasContextOverride()) {
                errors += nodeError(node, path, setOf("context"), "コンテキストが未設定です")
            }
            CommandType.DISK_CALL -> if (node.snapshot == null) {
                errors += nodeError(node, path, setOf("diskId"), "呼び出すディスク内容が未設定です")
            }
            CommandType.VARIABLE -> validateVariable(node, path, errors, variableDefinitions, insideForBody)
            CommandType.FOR_START -> validateFor(node, path, errors, variableDefinitions)
            CommandType.MERGE, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> Unit
        }
    }

    private fun validateEntityAction(node: CommandNode, path: String, errors: MutableList<ScriptValidationError>) {
        if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
        when (node.string("action", "ride")) {
            "ride" -> if (node.secondaryTargetSpec == null) {
                errors += nodeError(node, path, setOf("other"), "乗り物となる対象が未設定です")
            }
            "dismount" -> Unit
            "equip" -> {
                if (!CommandValueRules.isEquipmentSlot(node.string("slot"))) {
                    errors += nodeError(node, path, setOf("slot"), "装備スロットが不正です")
                }
                if (CommandValueRules.material(node.string("item"), allowAir = false) == null) {
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
                if (VariableTemplate.hasMalformedReference(tag) ||
                    (VariableTemplate.references(tag).isEmpty() && !CommandValueRules.isTag(tag))
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
    ) {
        if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "対象が未設定です")
        if (node.string("mode") !in setOf("tellraw", "title", "subtitle", "actionbar")) {
            errors += nodeError(node, path, setOf("mode"), "不明なテキスト表示位置です")
        }
        validateTemplate(node.string("text"), node, path, "text", errors, variableDefinitions)
        validateTemplate(node.string("subtitle"), node, path, "subtitle", errors, variableDefinitions)
        if (DisplayTextTimingPolicy.supports(node)) {
            listOf("fadeInSeconds", "staySeconds", "fadeOutSeconds").forEach { field ->
                validateNumberInput(node.string(field), variableDefinitions, node, path, setOf(field), errors)
                val value = resolvedDouble(node.string(field), variableDefinitions)
                if (!deferNumericValidation(node.string(field), variableDefinitions) &&
                    (value == null || value < 0.0 || value > Int.MAX_VALUE)
                ) {
                    errors += nodeError(node, path, setOf(field), "表示時間は0秒以上の数値で指定してください")
                }
            }
        }
    }

    private fun validateWait(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
    ) {
        validateNumberInput(node.string("seconds"), variableDefinitions, node, path, setOf("seconds"), errors)
        val seconds = resolvedDouble(node.string("seconds"), variableDefinitions)
        if (!deferNumericValidation(node.string("seconds"), variableDefinitions) &&
            (seconds == null || !seconds.isFinite() || seconds <= 0.0 || seconds > 86_400.0)
        ) {
            errors += nodeError(node, path, setOf("seconds"), "待機時間は0秒より大きく86400秒以下の数値で指定してください")
        }
    }

    private fun validateBlockOperation(node: CommandNode, path: String, errors: MutableList<ScriptValidationError>) {
        val operation = BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))
        if (operation == null) errors += nodeError(node, path, setOf("operation"), "ブロック操作方式が不正です")
        if (CommandValueRules.placementMaterial(node.string("block")) == null) {
            errors += nodeError(node, path, setOf("block"), "配置ブロックが未設定または不正です")
        }
        when (operation) {
            BlockOperationMode.SETBLOCK -> if (node.blockPositionSpec == null) {
                errors += nodeError(node, path, setOf("position"), "ブロック設置位置が未設定です")
            }
            BlockOperationMode.FILL -> {
                val from = node.blockFromSpec
                val to = node.blockToSpec
                if (from == null) errors += nodeError(node, path, setOf("from"), "範囲設置の始点が未設定です")
                if (to == null) errors += nodeError(node, path, setOf("to"), "範囲設置の終点が未設定です")
                if (from != null && to != null && blockVolume(from, to)?.let { it > MAX_BLOCK_OPERATION_VOLUME } == true) {
                    errors += nodeError(node, path, setOf("from", "to"), "範囲設置は${MAX_BLOCK_OPERATION_VOLUME}ブロック以内で指定してください")
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
    ) {
        if (spec.kind == TargetKind.FIXED_ENTITY && spec.fixedEntityId == null) {
            errors += nodeError(node, path, emptySet(), "固定エンティティが未設定です")
        }
        spec.entityType?.takeIf(String::isNotBlank)?.let {
            if (!CommandValueRules.isEntityTypeId(it)) errors += nodeError(node, path, emptySet(), "エンティティ種別が不正です")
        }
        spec.gameMode?.takeIf(String::isNotBlank)?.let {
            if (it.uppercase() !in setOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")) {
                errors += nodeError(node, path, emptySet(), "ゲームモードが不正です")
            }
        }
        spec.tag?.takeIf(String::isNotBlank)?.let {
            validateTemplate(it, node, path, "target", errors, variableDefinitions, insideForBody)
            if (VariableTemplate.references(it).isEmpty() && !CommandValueRules.isTag(it)) {
                errors += nodeError(node, path, emptySet(), "タグが不正です")
            }
        }
        spec.name?.takeIf(String::isNotBlank)?.let {
            validateTemplate(it, node, path, "target", errors, variableDefinitions, insideForBody)
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

    private fun validatePosition(spec: PositionSpec, path: String, node: CommandNode, errors: MutableList<ScriptValidationError>) {
        if (spec.kind in setOf(PositionKind.CAPTURED, PositionKind.COORDINATES) &&
            listOf(spec.x, spec.y, spec.z).any { it?.isFinite() != true }
        ) {
            errors += nodeError(node, path, emptySet(), "座標が未設定または有限値ではありません")
        }
        if (spec.kind == PositionKind.CAPTURED && (spec.yaw?.isFinite() != true || spec.pitch?.isFinite() != true)) {
            errors += nodeError(node, path, emptySet(), "捕捉した向きが未設定または有限値ではありません")
        }
    }

    private fun validateFacing(spec: FacingSpec, path: String, node: CommandNode, errors: MutableList<ScriptValidationError>) {
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
                validateNumberInput(node.string("value"), variableDefinitions, node, path, setOf("condition"), errors)
            }
            ConditionKind.BLOCK_STATE -> {
                if (CommandValueRules.material(node.string("block")) == null) {
                    errors += nodeError(node, path, setOf("condition"), "判定するブロックが未設定です")
                }
                if (node.conditionPositionSpec == null && node.contextOverride?.position == null) {
                    // コンテキスト位置を既定値として使うため、未設定は有効です。
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
                        VariableChangeMode.CALCULATE -> validateNumericExpression(node.string("value"), name, variableDefinitions, node, path, errors, insideForBody)
                        VariableChangeMode.ASSIGN -> validateAssignedValue(node.string("value"), VariableType.NUMBER, variableDefinitions, node, path, errors, insideForBody)
                    }
                    null -> if (variableDefinitions == null) {
                        // 配置前は対象変数の型を照会できないため、代入は文字列化して
                        // 実行時へ渡し、計算式だけ構文検証します。
                        if (mode == VariableChangeMode.CALCULATE) {
                            validateNumericExpression(node.string("value"), name, variableDefinitions, node, path, errors, insideForBody)
                        } else {
                            validateAssignedValue(node.string("value"), VariableType.STRING, variableDefinitions, node, path, errors, insideForBody)
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
    ) {
        if (type == VariableType.NUMBER) validateNumberInput(raw, definitions, node, path, setOf("value"), errors)
        else validateTemplate(raw, node, path, "value", errors, definitions)
    }

    private fun validateNumericExpression(
        raw: String,
        selfName: String,
        definitions: Map<String, WorldVariableValue>?,
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        insideForBody: Boolean,
    ) {
        val parsed = NumericExpression.parse(raw)
        if (!parsed.isSuccess) {
            // 詳細な入力エラーは入力画面側でlocaleへ解決します。実行前検証は
            // プレイヤー文脈を持たないため、モデルのErrorCodeをそのまま表示文へ
            // 埋め込まず、実行ログ・出力検証で共通の要約だけを返します。
            errors += nodeError(node, path, setOf("value"), "計算式が不正です")
            return
        }
        parsed.expression!!.references.forEach { reference ->
            if (SystemVariableNames.isSystemName(reference)) {
                if (!insideForBody) {
                    errors += nodeError(node, path, setOf("value"), "システム変数はfor本体内でのみ参照できます")
                }
            } else if (definitions != null && reference != selfName && definitions[reference]?.type != VariableType.NUMBER) {
                errors += nodeError(node, path, setOf("value"), "計算式の変数が未定義または数値型ではありません: $reference")
            }
        }
    }

    private fun validateFor(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        definitions: Map<String, WorldVariableValue>?,
    ) {
        validatePositiveIntegerInput(
            node.string("count", "1"),
            definitions,
            node,
            path,
            setOf("count"),
            errors,
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

    private fun validateNumberInput(
        raw: String,
        definitions: Map<String, WorldVariableValue>?,
        node: CommandNode,
        path: String,
        fields: Set<String>,
        errors: MutableList<ScriptValidationError>,
    ) {
        validateTemplate(raw, node, path, fields.firstOrNull() ?: "value", errors, definitions)
        if (VariableTemplate.hasMalformedReference(raw)) return
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
        val deferred = deferNumericValidation(raw, definitions)
        if (VariableTemplate.references(raw).isEmpty() && resolvedDouble(raw, definitions) == null) {
            errors += nodeError(node, path, fields, "数値で入力してください")
        } else if (!deferred && definitions != null && resolvedDouble(raw, definitions) == null) {
            errors += nodeError(node, path, fields, "変数展開後の値が数値ではありません")
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
    ) {
        validateNumberInput(raw, definitions, node, path, fields, errors)
        val value = resolvedDouble(raw, definitions)
        if (!deferNumericValidation(raw, definitions) &&
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
    ) {
        val raw = node.string(field)
        validateNumberInput(raw, definitions, node, path, setOf(field), errors)
        val value = resolvedDouble(raw, definitions)
        if (!deferNumericValidation(raw, definitions) &&
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
    ) {
        validateNumberInput(node.string(field), definitions, node, path, setOf(field), errors)
        val value = resolvedDouble(node.string(field), definitions)
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
    ) {
        if (VariableTemplate.hasMalformedReference(raw)) {
            errors += nodeError(node, path, setOf(field), "ワールド内変数の記法が不正です")
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
    }

    private fun isTemplateOrTag(raw: String, definitions: Map<String, WorldVariableValue>?): Boolean {
        if (VariableTemplate.hasMalformedReference(raw)) return false
        if (VariableTemplate.references(raw).any {
                !SystemVariableNames.isSystemName(it) && definitions != null && it !in definitions
            }) return false
        return VariableTemplate.references(raw).isNotEmpty() || CommandValueRules.isTag(raw)
    }

    /** 配置未配置のスクリプトでは、変数の存在だけを将来の実行境界へ委ねます。 */
    private fun deferNumericValidation(raw: String, definitions: Map<String, WorldVariableValue>?): Boolean {
        val reference = VariableTemplate.references(raw).singleOrNull()
            ?: return false
        if (!VariableTemplate.isSingleReference(raw)) return false
        return SystemVariableNames.isSystemName(reference) ||
            definitions == null || definitions[reference]?.type == VariableType.NUMBER
    }

    private fun resolvedDouble(raw: String, definitions: Map<String, WorldVariableValue>?): Double? {
        if (VariableTemplate.hasMalformedReference(raw)) return null
        val expanded = if (VariableTemplate.references(raw).isEmpty()) raw else {
            if (definitions == null) return null
            VariableTemplate.interpolate(raw) { definitions[it] } ?: return null
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

    private fun nodeError(node: CommandNode, path: String, fields: Set<String>, message: String) =
        ScriptValidationError(path, node.id, fields, message)
}
