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
import me.awabi2048.kantancommander.model.MIN_TIMER_UNITS
import me.awabi2048.kantancommander.model.MAX_TIMER_UNITS
import org.bukkit.Material
import java.util.Collections
import java.util.IdentityHashMap

object ExecutableScriptValidator {
    fun validate(script: DiskScript): List<String> {
        val errors = mutableListOf<String>()
        if (!script.timer.enabled && script.activation == ActivationMode.ALWAYS_ACTIVE) {
            errors += "root: タイマーオフでは常時実行を使用できません"
        }
        if (script.timer.enabled && script.timer.intervalUnits !in MIN_TIMER_UNITS..MAX_TIMER_UNITS) {
            errors += "root: タイマー間隔は${MIN_TIMER_UNITS}から${MAX_TIMER_UNITS}単位で指定してください"
        }
        validateGraph(script.graph, "root", errors, Collections.newSetFromMap(IdentityHashMap()))
        return errors
    }

    private fun validateGraph(
        graph: CommandGraph,
        path: String,
        errors: MutableList<String>,
        visited: MutableSet<CommandGraph>,
    ) {
        if (!visited.add(graph)) {
            errors += "$path: 別ディスクのコピー内容が循環参照しています"
            return
        }
        GraphValidator.validate(graph).forEach { errors += "$path: $it" }
        graph.nodes.values.forEach { node ->
            validateNode(node, "$path/${node.id}", errors)
            node.snapshot?.let { validateGraph(it, "$path/${node.id}/snapshot", errors, visited) }
        }
        visited.remove(graph)
    }

    private fun validateNode(node: CommandNode, path: String, errors: MutableList<String>) {
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
            CommandType.CONDITION -> validateCondition(node, path, errors)
            CommandType.CONTEXT -> if (node.contextOverride == null) errors += "$path: コンテキストが未設定です"
            CommandType.DISK_CALL -> if (node.snapshot == null) errors += "$path: 呼び出すディスク内容が未設定です"
            CommandType.VARIABLE -> validateVariable(node, path, errors)
            CommandType.FOR_START -> validateFor(node, path, errors)
            CommandType.MERGE, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> Unit
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
                else -> errors += "$path: forの${field}参照元が不正です"
            }
        }
        if (node.string("stepSource", "FIXED") == "FIXED" && node.string("stepValue").toLongOrNull() == 0L) {
            errors += "$path: forの増分に0は指定できません"
        }
    }
}
