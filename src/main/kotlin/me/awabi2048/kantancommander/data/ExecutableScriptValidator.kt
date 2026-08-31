package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.DisplayTextTimingPolicy
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.supportsContextOverride
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.MAX_BLOCK_OPERATION_VOLUME
import me.awabi2048.kantancommander.model.MIN_TIMER_SECONDS
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.effectiveContextSource
import me.awabi2048.kantancommander.model.hasContextOverride
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

/**
 * 実行前検証の結果を、表示文字列ではなく意味（対象ノードと設定項目）として保持します。
 *
 * GUIはnodeIdとfieldKeysから「どのノードの、どの設定タブが要確認か」を直接導出します。
 * 以前は表示文字列のパス解析からノードIDを復元していましたが、これは表示形式と
 * 意味の結合であり、文言変更がGUI判定を壊す原因になります。表示はrendered()だけが
 * 担当し、意味の推測に文字列解析を使いません。
 */
data class ScriptValidationError(
    /** 人間向け表示用のグラフ上の経路（例: "root/{nodeId}"）。 */
    val path: String,
    /** エラーの直接の原因ノード。スクリプト全体（タイマー・構造）の問題はnull。 */
    val nodeId: UUID?,
    /** このエラーが指すGUI設定項目（タブのfieldKey）。構造問題は空集合。 */
    val fieldKeys: Set<String>,
    val message: String,
) {
    /** 従来の表示形式（"path: message"）へ整形します。ログ・エクスポート表示専用です。 */
    fun rendered(): String = "$path: $message"
}

object ExecutableScriptValidator {
    fun validate(script: DiskScript, limits: GraphLimits = GraphLimits()): List<ScriptValidationError> {
        val errors = mutableListOf<ScriptValidationError>()
        if (!script.timer.enabled && script.activation == ActivationMode.ALWAYS_ACTIVE) {
            errors += ScriptValidationError(
                path = "root",
                nodeId = null,
                // タイマーと起動モードの組み合わせ問題のため、プログラムタイマーのカードへ投影します。
                fieldKeys = setOf("timer"),
                message = "タイマーオフでは常時実行を使用できません",
            )
        }
        if (script.timer.enabled && script.timer.intervalSeconds !in MIN_TIMER_SECONDS..MAX_TIMER_SECONDS) {
            errors += ScriptValidationError(
                path = "root",
                nodeId = null,
                fieldKeys = setOf("timer"),
                message = "タイマー間隔は${MIN_TIMER_SECONDS}から${MAX_TIMER_SECONDS}秒で指定してください",
            )
        }
        validateGraph(script.graph, "root", errors, Collections.newSetFromMap(IdentityHashMap()), limits)
        return errors
    }

    private fun validateGraph(
        graph: CommandGraph,
        path: String,
        errors: MutableList<ScriptValidationError>,
        visited: MutableSet<CommandGraph>,
        limits: GraphLimits,
    ) {
        if (!visited.add(graph)) {
            errors += ScriptValidationError(path, null, emptySet(), "別ディスクのコピー内容が循環参照しています")
            return
        }
        GraphValidator.validate(graph, limits).forEach {
            errors += ScriptValidationError(path, null, emptySet(), it)
        }
        graph.nodes.values.forEach { node ->
            validateNode(node, "$path/${node.id}", errors)
            node.snapshot?.let { validateGraph(it, "$path/${node.id}/snapshot", errors, visited, limits) }
        }
        visited.remove(graph)
    }

    private fun validateNode(node: CommandNode, path: String, errors: MutableList<ScriptValidationError>) {
        val hasContextState = node.hasContextOverride() || node.effectiveContextSource != ContextSource.BASE
        if (hasContextState && node.type != CommandType.CONTEXT && !node.type.supportsContextOverride()) {
            errors += ScriptValidationError(path, node.id, emptySet(), "${node.type} では実行コンテキストを設定できません")
        }
        listOfNotNull(node.targetSpec, node.secondaryTargetSpec, node.destinationTargetSpec).forEach {
            validateTarget(it, path, node, errors)
        }
        listOfNotNull(
            node.destinationSpec,
            node.conditionPositionSpec,
            node.blockPositionSpec,
            node.blockFromSpec,
            node.blockToSpec,
            node.contextOverride?.position,
        ).forEach {
            validatePosition(it, path, node, errors)
        }
        node.contextOverride?.let { context ->
            listOfNotNull(context.executor, context.target).forEach { validateTarget(it, path, node, errors) }
            context.facing?.let { validateFacing(it, path, node, errors) }
        }
        when (node.type) {
            CommandType.TELEPORT -> {
                if (node.targetSpec == null) {
                    errors += nodeError(node, path, setOf("target"), "対象が未設定です")
                }
                if (node.destinationSpec == null && node.destinationTargetSpec == null) {
                    errors += nodeError(node, path, setOf("destination"), "移動先が未設定です")
                }
            }
            CommandType.GIVE_ITEM -> {
                if (node.targetSpec == null) {
                    errors += nodeError(node, path, setOf("target"), "対象が未設定です")
                }
                if (CommandValueRules.material(node.string("item"), allowAir = false) == null) {
                    errors += nodeError(node, path, setOf("item"), "アイテムが未設定です")
                }
                if (CommandValueRules.parsePositiveInt(node.string("count")) == null) {
                    errors += nodeError(node, path, setOf("count"), "個数は1以上である必要があります")
                }
            }
            CommandType.ENTITY_ACTION -> {
                if (node.targetSpec == null) {
                    errors += nodeError(node, path, setOf("target"), "対象が未設定です")
                }
                val action = node.string("action")
                if (action !in setOf("ride", "dismount")) {
                    errors += nodeError(node, path, setOf("action"), "不明なエンティティ操作です")
                }
                if (action == "ride" && node.secondaryTargetSpec == null) {
                    errors += nodeError(node, path, setOf("other"), "乗り物となる対象が未設定です")
                }
            }
            CommandType.DISPLAY_TEXT -> {
                if (node.targetSpec == null) {
                    errors += nodeError(node, path, setOf("target"), "対象が未設定です")
                }
                if (node.string("mode") !in setOf("tellraw", "title", "actionbar")) {
                    errors += nodeError(node, path, setOf("mode"), "不明な文字列表示方式です")
                }
                if (DisplayTextTimingPolicy.supports(node) &&
                    listOf("fadeInSeconds", "staySeconds", "fadeOutSeconds")
                        .any { CommandValueRules.parseNonNegativeInt(node.string(it)) == null }
                ) {
                    // 表示時間はGUI上も「時間設定」（staySecondsタブ）へ一括編集のため、そこへ投影します。
                    errors += nodeError(
                        node,
                        path,
                        setOf("staySeconds"),
                        "タイトル／アクションバーの表示時間は0秒以上である必要があります",
                    )
                }
            }
            CommandType.WAIT ->
                if (CommandValueRules.parsePositiveInt(node.string("seconds")) == null) {
                    errors += nodeError(node, path, setOf("seconds"), "待機時間は1秒以上である必要があります")
                }
            CommandType.SUMMON_ENTITY -> {
                if (!CommandValueRules.isEntityTypeId(node.string("entity"))) {
                    errors += nodeError(node, path, setOf("entity"), "エンティティ種類が不正です")
                }
                val tags = node.string("tags").split(',').map(String::trim).filter(String::isNotEmpty)
                if (tags.any { !CommandValueRules.isTag(it) }) {
                    errors += nodeError(node, path, setOf("tags"), "召喚タグが不正です")
                }
            }
            CommandType.PLAY_SOUND -> {
                if (!CommandValueRules.isSoundId(node.string("sound"))) {
                    errors += nodeError(node, path, setOf("sound"), "サウンドIDが不正です")
                }
                if (node.double("volume", -1.0) !in 0.0..2.0) {
                    errors += nodeError(node, path, setOf("volume"), "音量は0.0〜2.0の範囲です")
                }
                if (node.double("pitch", -1.0) !in 0.5..2.0) {
                    errors += nodeError(node, path, setOf("pitch"), "ピッチは0.5〜2.0の範囲です")
                }
            }
            CommandType.APPLY_EFFECT -> {
                if (!CommandValueRules.isEffectId(node.string("effect"))) {
                    errors += nodeError(node, path, setOf("effect"), "エフェクト種類が不正です")
                }
                if (CommandValueRules.parsePositiveInt(node.string("level")) !in 1..255) {
                    errors += nodeError(node, path, setOf("level"), "エフェクトレベルは1〜255の範囲です")
                }
                if (CommandValueRules.parsePositiveInt(node.string("seconds")) !in 1..86_400) {
                    errors += nodeError(node, path, setOf("seconds"), "効果時間は1〜86400秒の範囲です")
                }
            }
            CommandType.CAMERA_SHAKE -> {
                if (node.double("intensity", -1.0) !in 0.1..4.0) {
                    errors += nodeError(node, path, setOf("intensity"), "揺れの強さは0.1〜4.0の範囲です")
                }
                if (node.double("seconds", -1.0) !in 1.0..10.0) {
                    errors += nodeError(node, path, setOf("seconds"), "揺れ時間は1.0〜10.0秒の範囲です")
                }
                if (node.string("shakeType") !in setOf("positional", "rotational")) {
                    errors += nodeError(node, path, setOf("shakeType"), "揺れ種類が不正です")
                }
                if (node.targetSpec == null) {
                    errors += nodeError(node, path, setOf("target"), "カメラ揺れの対象が未設定です")
                }
            }
            CommandType.EQUIP_ITEM -> {
                if (node.targetSpec == null) {
                    errors += nodeError(node, path, setOf("target"), "装備変更の対象が未設定です")
                }
                if (node.string("slot") !in setOf("HAND", "OFF_HAND", "HEAD", "CHEST", "LEGS", "FEET")) {
                    errors += nodeError(node, path, setOf("slot"), "装備スロットが不正です")
                }
                if (CommandValueRules.material(node.string("item"), allowAir = false) == null) {
                    errors += nodeError(node, path, setOf("item"), "装備アイテムが未設定です")
                }
            }
            CommandType.BLOCK_OPERATION -> {
                val operation = BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))
                if (operation == null) {
                    errors += nodeError(node, path, setOf("operation"), "ブロック操作方式が不正です")
                }
                if (CommandValueRules.placementMaterial(node.string("block")) == null) {
                    errors += nodeError(node, path, setOf("block"), "配置ブロックが未設定または不正です")
                }
                when (operation) {
                    BlockOperationMode.SETBLOCK -> {
                        if (node.blockPositionSpec == null) {
                            errors += nodeError(node, path, setOf("position"), "ブロック配置位置が未設定です")
                        }
                    }
                    BlockOperationMode.FILL -> {
                        val from = node.blockFromSpec
                        val to = node.blockToSpec
                        if (from == null) {
                            errors += nodeError(node, path, setOf("from"), "範囲配置の始点が未設定です")
                        }
                        if (to == null) {
                            errors += nodeError(node, path, setOf("to"), "範囲配置の終点が未設定です")
                        }
                        if (from != null && to != null) {
                            blockVolume(from, to)?.let { volume ->
                                if (volume > MAX_BLOCK_OPERATION_VOLUME) {
                                    errors += nodeError(
                                        node,
                                        path,
                                        setOf("from", "to"),
                                        "範囲配置は${MAX_BLOCK_OPERATION_VOLUME}ブロック以内で指定してください",
                                    )
                                }
                            }
                        }
                    }
                    null -> Unit
                }
            }
            CommandType.ENTITY_DELETE -> {
                if (node.targetSpec == null) {
                    errors += nodeError(node, path, setOf("target"), "削除対象が未設定です")
                }
            }
            CommandType.CONDITION -> validateCondition(node, path, errors)
            CommandType.CONTEXT ->
                if (!node.hasContextOverride()) {
                    errors += nodeError(node, path, setOf("context"), "コンテキストが未設定です")
                }
            CommandType.DISK_CALL ->
                if (node.snapshot == null) {
                    errors += nodeError(node, path, setOf("diskId"), "呼び出すディスク内容が未設定です")
                }
            CommandType.VARIABLE -> validateVariable(node, path, errors)
            CommandType.FOR_START -> validateFor(node, path, errors)
            CommandType.MERGE, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> Unit
        }
    }

    private fun nodeError(node: CommandNode, path: String, fieldKeys: Set<String>, message: String): ScriptValidationError =
        ScriptValidationError(path, node.id, fieldKeys, message)

    private fun validateTarget(
        spec: TargetSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
    ) {
        if (spec.kind == TargetKind.FIXED_ENTITY && spec.fixedEntityId == null) {
            errors += nodeError(node, path, emptySet(), "固定エンティティが未設定です")
        }
        spec.entityType?.takeIf(String::isNotBlank)?.let { raw ->
            if (!CommandValueRules.isEntityTypeId(raw)) {
                errors += nodeError(node, path, emptySet(), "エンティティ種別が不正です")
            }
        }
        spec.gameMode?.takeIf(String::isNotBlank)?.let { mode ->
            if (mode.uppercase() !in setOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")) {
                errors += nodeError(node, path, emptySet(), "ゲームモードが不正です")
            }
        }
        spec.tag?.takeIf(String::isNotBlank)?.let { tag ->
            if (!CommandValueRules.isTag(tag)) {
                errors += nodeError(node, path, emptySet(), "タグが不正です")
            }
        }
        spec.name?.let { name ->
            if (name.length > 256) {
                errors += nodeError(node, path, emptySet(), "エンティティ名が長すぎます")
            }
        }
        if (spec.minimumDistance?.isFinite() == false || spec.maximumDistance?.isFinite() == false) {
            errors += nodeError(node, path, emptySet(), "対象距離は有限値で指定してください")
        }
        if (spec.minimumDistance?.let { it < 0.0 } == true ||
            spec.maximumDistance?.let { it < 0.0 } == true
        ) {
            errors += nodeError(node, path, emptySet(), "対象距離は0以上で指定してください")
        }
        if (spec.minimumDistance != null && spec.maximumDistance != null &&
            spec.minimumDistance > spec.maximumDistance
        ) {
            errors += nodeError(node, path, emptySet(), "最小距離が最大距離を超えています")
        }
        if (spec.limit?.let { it < 1 } == true) {
            errors += nodeError(node, path, emptySet(), "対象数は1以上で指定してください")
        }
    }

    private fun validatePosition(
        spec: PositionSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
    ) {
        if (spec.kind in setOf(PositionKind.CAPTURED, PositionKind.COORDINATES) &&
            listOf(spec.x, spec.y, spec.z).any { it?.isFinite() != true }
        ) {
            errors += nodeError(node, path, emptySet(), "座標が未設定または有限値ではありません")
        }
        if (spec.kind == PositionKind.CAPTURED &&
            (spec.yaw?.isFinite() != true || spec.pitch?.isFinite() != true)
        ) {
            errors += nodeError(node, path, emptySet(), "捕捉した向きが未設定または有限値ではありません")
        }
        if (spec.kind in setOf(PositionKind.TEMPORARY_VARIABLE, PositionKind.WORLD_VARIABLE) &&
            !CommandValueRules.isVariableName(spec.variable.orEmpty())
        ) {
            errors += nodeError(node, path, emptySet(), "位置変数名が不正です")
        }
    }

    private fun validateFacing(
        spec: FacingSpec,
        path: String,
        node: CommandNode,
        errors: MutableList<ScriptValidationError>,
    ) {
        if (spec.kind == FacingKind.COORDINATES &&
            listOf(spec.x, spec.y, spec.z).any { it?.isFinite() != true }
        ) {
            errors += nodeError(node, path, emptySet(), "向く座標が未設定または有限値ではありません")
        }
        if (spec.kind in setOf(FacingKind.CAPTURED, FacingKind.ROTATION) &&
            (spec.yaw?.isFinite() != true || spec.pitch?.isFinite() != true)
        ) {
            errors += nodeError(node, path, emptySet(), "向きが未設定または有限値ではありません")
        }
    }

    /** 座標値が静的な場合だけ、実行前にfillの上限を判定します。 */
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
            } else {
                total * size.toLong()
            }
        }
    }

    private fun validateCondition(node: CommandNode, path: String, errors: MutableList<ScriptValidationError>) {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrNull()
        if (kind == null) {
            errors += nodeError(node, path, setOf("kind"), "条件種別が未設定です")
            return
        }
        when (kind) {
            ConditionKind.TARGET_EXISTS ->
                if (node.targetSpec == null) {
                    // 条件の詳細項目はGUI上「条件値」（conditionタブ）配下のため、そこへ投影します。
                    errors += nodeError(node, path, setOf("condition"), "条件の対象が未設定です")
                }
            ConditionKind.ENTITY_STATE -> {
                if (node.targetSpec == null) {
                    errors += nodeError(node, path, setOf("condition"), "条件の対象が未設定です")
                }
                if (node.string("state") !in setOf("sneaking", "on_ground")) {
                    errors += nodeError(node, path, setOf("condition"), "状態が未設定です")
                }
            }
            ConditionKind.VARIABLE_STATE -> {
                if (node.string("variable").isBlank()) {
                    errors += nodeError(node, path, setOf("condition"), "変数名が未設定です")
                }
                if (runCatching { VariableScope.valueOf(node.string("variableScope")) }.isFailure) {
                    errors += nodeError(node, path, setOf("condition"), "変数の範囲が不正です")
                }
                if (node.string("operator") !in setOf("set", "unset", "==", "!=", ">", "<", ">=", "<=")) {
                    errors += nodeError(node, path, setOf("condition"), "比較方法が不正です")
                }
            }
            ConditionKind.BLOCK_STATE ->
                if (CommandValueRules.material(node.string("block")) == null) {
                    errors += nodeError(node, path, setOf("condition"), "ブロックが未設定です")
                }
            ConditionKind.ITEM_POSSESSION -> {
                if (node.targetSpec == null) {
                    errors += nodeError(node, path, setOf("condition"), "条件の対象が未設定です")
                }
                if (CommandValueRules.material(node.string("item"), allowAir = false) == null) {
                    errors += nodeError(node, path, setOf("condition"), "アイテムが未設定です")
                }
                if (node.int("count", 0) < 1) {
                    errors += nodeError(node, path, setOf("condition"), "必要個数は1以上である必要があります")
                }
            }
        }
    }

    private fun validateVariable(node: CommandNode, path: String, errors: MutableList<ScriptValidationError>) {
        if (!CommandValueRules.isVariableName(node.string("name"))) {
            errors += nodeError(node, path, setOf("name"), "変数名が不正です")
        }
        val type = runCatching { VariableType.valueOf(node.string("type")) }.getOrNull()
        val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }.getOrNull()
        if (type == null) {
            errors += nodeError(node, path, setOf("type"), "変数型が不正です")
        }
        if (operation == null) {
            errors += nodeError(node, path, setOf("operation"), "変数操作が不正です")
        }
        // 型と操作の組み合わせ問題は、どちらのタブを直しても解消できるため両方へ投影します。
        if (operation in setOf(VariableOperation.ADD, VariableOperation.SUBTRACT) &&
            type !in setOf(VariableType.INTEGER, VariableType.DECIMAL)
        ) {
            errors += nodeError(node, path, setOf("type", "operation"), "加減算できない変数型です")
        }
        if (operation == VariableOperation.TOGGLE && type != VariableType.BOOLEAN) {
            errors += nodeError(node, path, setOf("type", "operation"), "切替は真偽値だけに使用できます")
        }
        if (operation == VariableOperation.STORE_POSITION && type != VariableType.POSITION) {
            errors += nodeError(node, path, setOf("type", "operation"), "位置保存には位置型が必要です")
        }
        if (operation == VariableOperation.STORE_TARGET && type != VariableType.ENTITY) {
            errors += nodeError(node, path, setOf("type", "operation"), "対象保存にはエンティティ型が必要です")
        }
        // 反復値・ループ回数は起動ローカルの読み取り専用値のため、ワールド内変数（MyWorld共有）への
        // 保存を拒否する（仕様12.2 forの反復値・ループ回数の出力先には使用できない）。
        if (operation == VariableOperation.SET &&
            node.string("scope", VariableScope.TEMPORARY.name) == VariableScope.WORLD.name &&
            node.string("value") in setOf("\$current_iteration_value", "\$current_loop_count")
        ) {
            errors += nodeError(node, path, setOf("scope", "value"), "ループ値はワールド内変数へ保存できません")
        }
    }

    private fun validateFor(node: CommandNode, path: String, errors: MutableList<ScriptValidationError>) {
        listOf("start", "end", "step").forEach { field ->
            when (node.string("${field}Source", "FIXED")) {
                "FIXED" -> if (node.string("${field}Value").toLongOrNull() == null) {
                    errors += nodeError(node, path, setOf("${field}Value"), "forの${field}値が不正です")
                }
                "TEMPORARY" -> if (node.string("${field}Value").isBlank()) {
                    errors += nodeError(node, path, setOf("${field}Value"), "forの${field}参照変数が未設定です")
                }
                "WORLD" -> if (node.string("${field}Value").isBlank()) {
                    errors += nodeError(node, path, setOf("${field}Value"), "forの${field}参照変数が未設定です")
                }
                else -> errors += nodeError(node, path, setOf("${field}Source"), "forの${field}参照元が不正です")
            }
        }
        if (node.string("stepSource", "FIXED") == "FIXED" && node.string("stepValue").toLongOrNull() == 0L) {
            errors += nodeError(node, path, setOf("stepValue"), "forの増分に0は指定できません")
        }
    }
}
