package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.MAX_BLOCK_OPERATION_VOLUME
import me.awabi2048.kantancommander.model.MIN_TIMER_UNITS
import me.awabi2048.kantancommander.model.MAX_TIMER_UNITS
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetKind
import org.bukkit.Material
import org.bukkit.NamespacedKey
import java.util.Collections
import java.util.IdentityHashMap

object ExecutableScriptValidator {
    fun validate(script: DiskScript, limits: GraphLimits = GraphLimits()): List<String> {
        val errors = mutableListOf<String>()
        if (!script.timer.enabled && script.activation == ActivationMode.ALWAYS_ACTIVE) {
            errors += "root: タイマーオフでは常時実行を使用できません"
        }
        if (script.timer.enabled && script.timer.intervalUnits !in MIN_TIMER_UNITS..MAX_TIMER_UNITS) {
            errors += "root: タイマー間隔は${MIN_TIMER_UNITS}から${MAX_TIMER_UNITS}単位で指定してください"
        }
        validateGraph(script.graph, "root", errors, Collections.newSetFromMap(IdentityHashMap()), limits)
        return errors
    }

    private fun validateGraph(
        graph: CommandGraph,
        path: String,
        errors: MutableList<String>,
        visited: MutableSet<CommandGraph>,
        limits: GraphLimits,
    ) {
        if (!visited.add(graph)) {
            errors += "$path: 別ディスクのコピー内容が循環参照しています"
            return
        }
        GraphValidator.validate(graph, limits).forEach { errors += "$path: $it" }
        graph.nodes.values.forEach { node ->
            validateNode(node, "$path/${node.id}", errors)
            node.snapshot?.let { validateGraph(it, "$path/${node.id}/snapshot", errors, visited, limits) }
        }
        visited.remove(graph)
    }

    private fun validateNode(node: CommandNode, path: String, errors: MutableList<String>) {
        listOfNotNull(node.targetSpec, node.secondaryTargetSpec, node.destinationTargetSpec).forEach {
            validateTarget(it, path, errors)
        }
        listOfNotNull(
            node.destinationSpec,
            node.conditionPositionSpec,
            node.blockPositionSpec,
            node.blockFromSpec,
            node.blockToSpec,
            node.contextOverride?.position,
        ).forEach {
            validatePosition(it, path, errors)
        }
        node.contextOverride?.let { context ->
            listOfNotNull(context.executor, context.target).forEach { validateTarget(it, path, errors) }
            context.facing?.let { validateFacing(it, path, errors) }
        }
        when (node.type) {
            CommandType.TELEPORT -> {
                if (node.targetSpec == null) errors += "$path: 対象が未設定です"
                if (node.destinationSpec == null && node.destinationTargetSpec == null) {
                    errors += "$path: 移動先が未設定です"
                }
            }
            CommandType.GIVE_ITEM -> {
                if (node.targetSpec == null) errors += "$path: 対象が未設定です"
                if (Material.matchMaterial(node.string("item")) == null) errors += "$path: アイテムが未設定です"
                if (node.int("count", 0) < 1) errors += "$path: 個数は1以上である必要があります"
            }
            CommandType.ENTITY_ACTION -> {
                if (node.targetSpec == null) errors += "$path: 対象が未設定です"
                val action = node.string("action")
                if (action !in setOf("ride", "dismount")) errors += "$path: 不明なエンティティ操作です"
                if (action == "ride" && node.secondaryTargetSpec == null) {
                    errors += "$path: 乗り物となる対象が未設定です"
                }
            }
            CommandType.DISPLAY_TEXT -> {
                if (node.targetSpec == null) errors += "$path: 対象が未設定です"
                if (node.string("mode") !in setOf("tellraw", "title", "actionbar")) {
                    errors += "$path: 不明な文字列表示方式です"
                }
                if (node.string("mode") == "title" &&
                    listOf("fadeIn", "stay", "fadeOut").any { node.int(it, -1) < 0 }
                ) {
                    errors += "$path: タイトルの表示時間は0tick以上である必要があります"
                }
            }
            CommandType.WAIT ->
                if (node.int("ticks", 0) < 1) errors += "$path: 待機時間は1tick以上である必要があります"
            CommandType.SUMMON_ENTITY -> {
                val key = NamespacedKey.fromString(node.string("entity"))
                if (key == null) errors += "$path: エンティティ種類が不正です"
                val tags = node.string("tags").split(',').map(String::trim).filter(String::isNotEmpty)
                if (tags.any { !it.matches(Regex("[A-Za-z0-9_.:+-]{1,64}")) }) errors += "$path: 召喚タグが不正です"
            }
            CommandType.PLAY_SOUND -> {
                if (NamespacedKey.fromString(node.string("sound")) == null) errors += "$path: サウンドIDが不正です"
                if (node.double("volume", -1.0) !in 0.0..2.0) errors += "$path: 音量は0.0〜2.0の範囲です"
                if (node.double("pitch", -1.0) !in 0.5..2.0) errors += "$path: ピッチは0.5〜2.0の範囲です"
            }
            CommandType.APPLY_EFFECT -> {
                val key = NamespacedKey.fromString(node.string("effect"))
                if (key == null) errors += "$path: エフェクト種類が不正です"
                if (node.int("level", 0) !in 1..255) errors += "$path: エフェクトレベルは1〜255の範囲です"
                if (node.int("seconds", 0) !in 1..86_400) errors += "$path: 効果時間は1〜86400秒の範囲です"
            }
            CommandType.CAMERA_SHAKE -> {
                if (node.double("intensity", -1.0) !in 0.1..4.0) errors += "$path: 揺れの強さは0.1〜4.0の範囲です"
                if (node.double("seconds", -1.0) !in 1.0..10.0) errors += "$path: 揺れ時間は1.0〜10.0秒の範囲です"
                if (node.string("shakeType") !in setOf("positional", "rotational")) errors += "$path: 揺れ種類が不正です"
                if (node.targetSpec == null) errors += "$path: カメラ揺れの対象が未設定です"
            }
            CommandType.EQUIP_ITEM -> {
                if (node.targetSpec == null) errors += "$path: 装備変更の対象が未設定です"
                if (node.string("slot") !in setOf("HAND", "OFF_HAND", "HEAD", "CHEST", "LEGS", "FEET")) {
                    errors += "$path: 装備スロットが不正です"
                }
                if (Material.matchMaterial(node.string("item")) == null) errors += "$path: 装備アイテムが未設定です"
            }
            CommandType.BLOCK_OPERATION -> {
                val operation = BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value))
                if (operation == null) {
                    errors += "$path: ブロック操作方式が不正です"
                }
                val block = Material.matchMaterial(node.string("block"))
                if (block == null || block == Material.AIR) {
                    errors += "$path: 配置ブロックが未設定または不正です"
                }
                when (operation) {
                    BlockOperationMode.SETBLOCK -> {
                        if (node.blockPositionSpec == null) errors += "$path: ブロック配置位置が未設定です"
                    }
                    BlockOperationMode.FILL -> {
                        val from = node.blockFromSpec
                        val to = node.blockToSpec
                        if (from == null) errors += "$path: 範囲配置の始点が未設定です"
                        if (to == null) errors += "$path: 範囲配置の終点が未設定です"
                        if (from != null && to != null) {
                            blockVolume(from, to)?.let { volume ->
                                if (volume > MAX_BLOCK_OPERATION_VOLUME) {
                                    errors += "$path: 範囲配置は${MAX_BLOCK_OPERATION_VOLUME}ブロック以内で指定してください"
                                }
                            }
                        }
                    }
                    null -> Unit
                }
            }
            CommandType.ENTITY_DELETE -> {
                if (node.targetSpec == null) errors += "$path: 削除対象が未設定です"
            }
            CommandType.CONDITION -> validateCondition(node, path, errors)
            CommandType.CONTEXT -> if (node.contextOverride == null) errors += "$path: コンテキストが未設定です"
            CommandType.DISK_CALL -> if (node.snapshot == null) errors += "$path: 呼び出すディスク内容が未設定です"
            CommandType.VARIABLE -> validateVariable(node, path, errors)
            CommandType.FOR_START -> validateFor(node, path, errors)
            CommandType.MERGE, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> Unit
        }
    }

    private fun validateTarget(spec: TargetSpec, path: String, errors: MutableList<String>) {
        if (spec.kind == TargetKind.FIXED_ENTITY && spec.fixedEntityId == null) {
            errors += "$path: 固定エンティティが未設定です"
        }
        spec.entityType?.takeIf(String::isNotBlank)?.let { raw ->
            if (NamespacedKey.fromString(raw) == null) errors += "$path: エンティティ種別が不正です"
        }
        spec.gameMode?.takeIf(String::isNotBlank)?.let { mode ->
            if (mode.uppercase() !in setOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")) {
                errors += "$path: ゲームモードが不正です"
            }
        }
        spec.tag?.takeIf(String::isNotBlank)?.let { tag ->
            if (!tag.matches(Regex("[A-Za-z0-9_.:+-]{1,64}"))) errors += "$path: タグが不正です"
        }
        spec.name?.let { name ->
            if (name.length > 256) errors += "$path: エンティティ名が長すぎます"
        }
        if (spec.minimumDistance?.isFinite() == false || spec.maximumDistance?.isFinite() == false) {
            errors += "$path: 対象距離は有限値で指定してください"
        }
        if (spec.minimumDistance?.let { it < 0.0 } == true ||
            spec.maximumDistance?.let { it < 0.0 } == true
        ) errors += "$path: 対象距離は0以上で指定してください"
        if (spec.minimumDistance != null && spec.maximumDistance != null &&
            spec.minimumDistance > spec.maximumDistance
        ) errors += "$path: 最小距離が最大距離を超えています"
        if (spec.limit?.let { it < 1 } == true) errors += "$path: 対象数は1以上で指定してください"
    }

    private fun validatePosition(spec: PositionSpec, path: String, errors: MutableList<String>) {
        if (spec.kind in setOf(PositionKind.CAPTURED, PositionKind.COORDINATES) &&
            listOf(spec.x, spec.y, spec.z).any { it?.isFinite() != true }
        ) errors += "$path: 座標が未設定または有限値ではありません"
        if (spec.kind == PositionKind.CAPTURED &&
            (spec.yaw?.isFinite() != true || spec.pitch?.isFinite() != true)
        ) errors += "$path: 捕捉した向きが未設定または有限値ではありません"
        if (spec.kind in setOf(PositionKind.TEMPORARY_VARIABLE, PositionKind.WORLD_VARIABLE) &&
            !spec.variable.orEmpty().matches(Regex("[a-z0-9_.-]{1,64}"))
        ) errors += "$path: 位置変数名が不正です"
    }

    private fun validateFacing(spec: FacingSpec, path: String, errors: MutableList<String>) {
        if (spec.kind == FacingKind.COORDINATES &&
            listOf(spec.x, spec.y, spec.z).any { it?.isFinite() != true }
        ) errors += "$path: 向く座標が未設定または有限値ではありません"
        if (spec.kind in setOf(FacingKind.CAPTURED, FacingKind.ROTATION) &&
            (spec.yaw?.isFinite() != true || spec.pitch?.isFinite() != true)
        ) errors += "$path: 向きが未設定または有限値ではありません"
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

    private fun validateCondition(node: CommandNode, path: String, errors: MutableList<String>) {
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrNull()
        if (kind == null) {
            errors += "$path: 条件種別が未設定です"
            return
        }
        when (kind) {
            ConditionKind.TARGET_EXISTS ->
                if (node.targetSpec == null) errors += "$path: 条件の対象が未設定です"
            ConditionKind.ENTITY_STATE -> {
                if (node.targetSpec == null) errors += "$path: 条件の対象が未設定です"
                if (node.string("state") !in setOf("sneaking", "on_ground")) errors += "$path: 状態が未設定です"
            }
            ConditionKind.VARIABLE_STATE -> {
                if (node.string("variable").isBlank()) errors += "$path: 変数名が未設定です"
                if (runCatching { VariableScope.valueOf(node.string("variableScope")) }.isFailure) {
                    errors += "$path: 変数の範囲が不正です"
                }
                if (node.string("operator") !in setOf("set", "unset", "==", "!=", ">", "<", ">=", "<=")) {
                    errors += "$path: 比較方法が不正です"
                }
            }
            ConditionKind.BLOCK_STATE ->
                if (Material.matchMaterial(node.string("block")) == null) errors += "$path: ブロックが未設定です"
            ConditionKind.ITEM_POSSESSION -> {
                if (node.targetSpec == null) errors += "$path: 条件の対象が未設定です"
                if (Material.matchMaterial(node.string("item")) == null) errors += "$path: アイテムが未設定です"
                if (node.int("count", 0) < 1) errors += "$path: 必要個数は1以上である必要があります"
            }
        }
    }

    private fun validateVariable(node: CommandNode, path: String, errors: MutableList<String>) {
        if (!node.string("name").matches(Regex("[a-z0-9_.-]{1,64}"))) errors += "$path: 変数名が不正です"
        val type = runCatching { VariableType.valueOf(node.string("type")) }.getOrNull()
        val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }.getOrNull()
        if (type == null) errors += "$path: 変数型が不正です"
        if (operation == null) errors += "$path: 変数操作が不正です"
        if (operation in setOf(VariableOperation.ADD, VariableOperation.SUBTRACT) &&
            type !in setOf(VariableType.INTEGER, VariableType.DECIMAL)
        ) errors += "$path: 加減算できない変数型です"
        if (operation == VariableOperation.TOGGLE && type != VariableType.BOOLEAN) {
            errors += "$path: 切替は真偽値だけに使用できます"
        }
        if (operation == VariableOperation.STORE_POSITION && type != VariableType.POSITION) {
            errors += "$path: 位置保存には位置型が必要です"
        }
        if (operation == VariableOperation.STORE_TARGET && type != VariableType.ENTITY) {
            errors += "$path: 対象保存にはエンティティ型が必要です"
        }
        // 反復値・ループ回数は起動ローカルの読み取り専用値のため、ワールド内変数（MyWorld共有）への
        // 保存を拒否する（仕様12.2 forの反復値・ループ回数の出力先には使用できない）。
        if (operation == VariableOperation.SET &&
            node.string("scope", VariableScope.TEMPORARY.name) == VariableScope.WORLD.name &&
            node.string("value") in setOf("\$current_iteration_value", "\$current_loop_count")
        ) {
            errors += "$path: ループ値はワールド内変数へ保存できません"
        }
    }

    private fun validateFor(node: CommandNode, path: String, errors: MutableList<String>) {
        listOf("start", "end", "step").forEach { field ->
            when (node.string("${field}Source", "FIXED")) {
                "FIXED" -> if (node.string("${field}Value").toLongOrNull() == null) {
                    errors += "$path: forの${field}値が不正です"
                }
                "TEMPORARY" -> if (node.string("${field}Value").isBlank()) {
                    errors += "$path: forの${field}参照変数が未設定です"
                }
                "WORLD" -> if (node.string("${field}Value").isBlank()) {
                    errors += "$path: forの${field}参照変数が未設定です"
                }
                else -> errors += "$path: forの${field}参照元が不正です"
            }
        }
        if (node.string("stepSource", "FIXED") == "FIXED" && node.string("stepValue").toLongOrNull() == 0L) {
            errors += "$path: forの増分に0は指定できません"
        }
    }
}
