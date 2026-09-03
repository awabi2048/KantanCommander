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
import me.awabi2048.kantancommander.model.MAX_COMMAND_TIME_SECONDS
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.MIN_TIMER_SECONDS
import me.awabi2048.kantancommander.model.NumericExpression
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
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
        // 一時変数は実行内寿命のため、グラフ内の一時定義を収集して参照検証に使います。
        // DISK_CALLのスナップショットは隔離されるため、再帰先では収集し直します。
        val temporaryDefinitions = collectTemporaryDefinitions(graph)
        graph.nodes.values.forEach { node ->
            validateNode(node, "$path/${node.id}", errors, variableDefinitions, isInsideForBody(graph, node.id), temporaryDefinitions)
            node.snapshot?.let {
                validateGraph(it, "$path/${node.id}/snapshot", errors, visited, limits, variableDefinitions)
            }
        }
        visited.remove(graph)
    }

    /** グラフ内の「一時変数を設定」ノードから名前→型の定義表を収集します。 */
    private fun collectTemporaryDefinitions(graph: CommandGraph): Map<String, TemporaryVariableType> = buildMap {
        graph.nodes.values.forEach { node ->
            if (node.type != CommandType.TEMP_SET) return@forEach
            val name = TemporaryTemplate.normalized(node.string("name"))
            if (!CommandValueRules.isVariableName(name)) return@forEach
            val type = runCatching {
                TemporaryVariableType.valueOf(node.string("tempType", TemporaryVariableType.NUMBER.name))
            }.getOrNull() ?: return@forEach
            put(name, type)
        }
    }

    private fun validateNode(
        node: CommandNode,
        path: String,
        errors: MutableList<ScriptValidationError>,
        variableDefinitions: Map<String, WorldVariableValue>?,
        insideForBody: Boolean,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        validateSystemReferences(node, path, insideForBody, errors)
        validateRemovedSystemReferences(node, path, errors)
        validateTemporaryReferences(node, path, errors, temporaryDefinitions)
        val hasContextState = node.hasContextOverride() || node.effectiveContextSource != ContextSource.BASE
        if (hasContextState && node.type != CommandType.CONTEXT && !node.type.supportsContextOverride()) {
            errors += nodeError(node, path, emptySet(), "${node.type} では実行コンテキストを設定できません")
        }
        listOfNotNull(node.targetSpec, node.secondaryTargetSpec, node.destinationTargetSpec).forEach {
            validateTarget(it, path, node, errors, variableDefinitions, insideForBody, temporaryDefinitions)
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
        ).forEach { validatePosition(it, path, node, errors, temporaryDefinitions) }
        node.destinationFacingSpec?.let { validateFacing(it, path, node, errors, temporaryDefinitions) }
        node.contextOverride?.let { context ->
            listOfNotNull(context.executor, context.target).forEach {
                validateTarget(it, path, node, errors, variableDefinitions, insideForBody, temporaryDefinitions)
            }
            context.facing?.let { validateFacing(it, path, node, errors, temporaryDefinitions) }
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
            CommandType.PLAY_SOUND -> {
                if (!CommandValueRules.isSoundId(node.string("sound"))) {
                    errors += nodeError(node, path, setOf("sound"), "サウンドIDが不正です")
                }
                validateRange(node, path, "volume", 0.0..34.0, "音量は0.0〜34.0の範囲です", errors, variableDefinitions, temporaryDefinitions)
                validateRange(node, path, "pitch", 0.5..2.0, "ピッチは0.5〜2.0の範囲です", errors, variableDefinitions, temporaryDefinitions)
                if (node.string("soundScope", "CONTEXT") !in setOf("CONTEXT", "WORLD")) {
                    errors += nodeError(node, path, setOf("soundPosition"), "サウンドの再生位置が不正です")
                }
            }
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
            CommandType.ENTITY_DELETE -> {
                if (node.targetSpec == null) errors += nodeError(node, path, setOf("target"), "削除対象が未設定です")
            }
            CommandType.CONDITION -> validateCondition(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.CONTEXT -> if (!node.hasContextOverride()) {
                errors += nodeError(node, path, setOf("context"), "コンテキストが未設定です")
            }
            CommandType.DISK_CALL -> if (node.snapshot == null) {
                errors += nodeError(node, path, setOf("diskId"), "呼び出すディスク内容が未設定です")
            }
            CommandType.VARIABLE -> validateVariable(node, path, errors, variableDefinitions, insideForBody, temporaryDefinitions)
            CommandType.TEMP_SET -> validateTemporary(node, path, errors, temporaryDefinitions)
            CommandType.FOR_START -> validateFor(node, path, errors, variableDefinitions, temporaryDefinitions)
            CommandType.MERGE, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> Unit
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
                // 一時変数 `%name%` の参照も文字列欄として許可します。
                if (TemporaryTemplate.hasMalformedReference(tag) ||
                    VariableTemplate.hasMalformedReference(tag) ||
                    (VariableTemplate.references(tag).isEmpty() && TemporaryTemplate.references(tag).isEmpty() &&
                        !CommandValueRules.isTag(tag))
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
        if (CommandValueRules.placementMaterial(node.string("block")) == null) {
            errors += nodeError(node, path, setOf("block"), "配置ブロックが未設定または不正です")
        }
        when (operation) {
            BlockOperationMode.SETBLOCK -> if (node.blockPositionSpec == null) {
                errors += nodeError(node, path, setOf("position"), "ブロック配置位置が未設定です")
            }
            BlockOperationMode.FILL -> {
                val from = node.blockFromSpec
                val to = node.blockToSpec
                if (from == null) errors += nodeError(node, path, setOf("from"), "範囲配置の始点が未設定です")
                if (to == null) errors += nodeError(node, path, setOf("to"), "範囲配置の終点が未設定です")
                if (from != null && to != null && blockVolume(from, to)?.let { it > MAX_BLOCK_OPERATION_VOLUME } == true) {
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
        // 暗示的継承は廃止予定です。新規設定のGUIでは選択肢を出さず、
        // 既存データの読み・実行・出力は第2段階の読替まで維持します。
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

    private fun validatePosition(
        spec: PositionSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
        temporaryDefinitions: Map<String, TemporaryVariableType>? = null,
    ) {
        if (spec.kind == PositionKind.TEMPORARY) {
            checkTemporaryDefinition(spec.tempName, TemporaryVariableType.POSITION, temporaryDefinitions, node, path, emptySet(), errors)
            return
        }
        // コンテキスト経由解決は廃止予定です。新規設定のGUIでは選択肢を出さず、
        // 既存データの読み・実行・出力は第2段階の読替まで維持します。
        if (spec.kind in setOf(PositionKind.CAPTURED, PositionKind.COORDINATES) &&
            listOf(spec.x, spec.y, spec.z).any { it?.isFinite() != true }
        ) {
            errors += nodeError(node, path, emptySet(), "座標が未設定または有限値ではありません")
        }
        if (spec.kind == PositionKind.CAPTURED && (spec.yaw?.isFinite() != true || spec.pitch?.isFinite() != true)) {
            errors += nodeError(node, path, emptySet(), "捕捉した向きが未設定または有限値ではありません")
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
            checkTemporaryDefinition(spec.tempName, TemporaryVariableType.POSITION, temporaryDefinitions, node, path, emptySet(), errors)
            return
        }
        // 継承向きは廃止予定です。新規設定のGUIでは選択肢を出さず、
        // 既存データの読み・実行・出力は第2段階の読替まで維持します。
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
                // 暗示的コンテキスト廃止後は判定位置を必須化します。
                // 第2段階の読替までは既存データのため未設定を許容します。
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
        parsed.expression!!.references.forEach { reference ->
            if (SystemVariableNames.isSystemName(reference)) {
                if (!insideForBody) {
                    errors += nodeError(node, path, setOf("value"), "システム変数はfor本体内でのみ参照できます")
                }
            } else if (definitions != null && reference != selfName && definitions[reference]?.type != VariableType.NUMBER) {
                errors += nodeError(node, path, setOf("value"), "計算式の変数が未定義または数値型ではありません: $reference")
            }
        }
        parsed.expression!!.temporaryReferences.forEach { reference ->
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
            checkTemporaryDefinition(it, TemporaryVariableType.POSITION, temporaryDefinitions, node, path, emptySet(), errors)
        }
        search.position?.let { validatePosition(it, path, node, errors, temporaryDefinitions) }
    }

    /**
     * 非リテラル一時変数参照欄の一貫性を検証します。
     *
     * 参照指定時は対応するリテラル値の併記を拒否し、参照先の存在と型を確認します。
     * リテラル欄へ `%name%` 以外の一時記法が混入した場合もここで検出します。
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
        val type = runCatching {
            TemporaryVariableType.valueOf(node.string("tempType", TemporaryVariableType.NUMBER.name))
        }.getOrNull()
        if (type == null) {
            errors += nodeError(node, path, setOf("type"), "一時変数の型が不正です")
            return
        }
        when (type) {
            TemporaryVariableType.NUMBER -> validateNumberInput(
                node.string("value"), null, node, path, setOf("value"), errors, temporaryDefinitions,
            )
            TemporaryVariableType.STRING -> validateTemplate(
                node.string("value"), node, path, "value", errors, null, temporaries = temporaryDefinitions,
            )
            TemporaryVariableType.POSITION -> {
                listOf("x" to node.string("x"), "y" to node.string("y"), "z" to node.string("z")).forEach { (field, raw) ->
                    if (raw.isBlank() || resolvedDouble(raw, null, temporaryDefinitions) == null &&
                        !deferNumericValidation(raw, null, temporaryDefinitions)
                    ) {
                        errors += nodeError(node, path, setOf("value"), "位置の$field が不正です")
                    }
                }
            }
            TemporaryVariableType.ITEM -> if (CommandValueRules.material(node.string("item"), allowAir = false) == null) {
                errors += nodeError(node, path, setOf("value"), "アイテムが未設定です")
            }
            TemporaryVariableType.BLOCK -> if (CommandValueRules.placementMaterial(node.string("block")) == null) {
                errors += nodeError(node, path, setOf("value"), "ブロックが未設定です")
            }
            TemporaryVariableType.ENTITY -> { /* 実行時に解決するため、設定時は検証しません。 */ }
            TemporaryVariableType.SOUND -> {
                if (!CommandValueRules.isSoundId(node.string("sound"))) {
                    errors += nodeError(node, path, setOf("value"), "サウンドIDが不正です")
                }
            }
            TemporaryVariableType.EFFECT -> {
                if (!CommandValueRules.isEffectId(node.string("effect"))) {
                    errors += nodeError(node, path, setOf("value"), "エフェクトが不正です")
                }
            }
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
            TemporaryTemplate.references(raw)
                .map(TemporaryTemplate::normalized)
                .filter { it !in temporaries }
                .forEach {
                    errors += nodeError(node, path, setOf(field), "一時変数が未定義です: $it")
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
        if (temporaries != null && TemporaryTemplate.references(raw).map(TemporaryTemplate::normalized).any { it !in temporaries }) {
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

    private fun nodeError(node: CommandNode, path: String, fields: Set<String>, message: String) =
        ScriptValidationError(path, node.id, fields, message)
}
