package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.MenuRoute
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.effectiveContextSource
import java.util.UUID

/**
 * 個別設定画面が対象とする構造化データの役割です。
 *
 * インベントリGUIは従来payloadへ任意文字列を詰めていましたが、ジェスチャーGUIまで
 * 同じ分岐を複製すると、対象／位置／向きの保存先がずれます。このenumを両GUIの
 * 中間表現として使い、画面固有のルート文字列をドメインの役割へ変換します。
 */
enum class CommandSettingRole(val routeValue: String) {
    NODE_TARGET("node_target"),
    DESTINATION("destination"),
    SECONDARY_TARGET("secondary_target"),
    CONTEXT_EXECUTOR("context_executor"),
    CONTEXT_TARGET("context_target"),
    CONTEXT_POSITION("context_position"),
    CONDITION_POSITION("condition_position"),
    CONTEXT_FACING("context_facing"),
    ;

    companion object {
        fun fromRoute(value: String?): CommandSettingRole? =
            entries.firstOrNull { it.routeValue == value }
    }
}

/** インベントリ／ジェスチャー双方で共有する設定対象の識別子です。 */
data class CommandSettingContext(
    val scriptId: UUID,
    val nodeId: UUID,
    val role: CommandSettingRole? = null,
) {
    companion object {
        /** 既存InventoryMenuのMenuRouteを共通コンテキストへ変換します。 */
        fun from(route: MenuRoute): CommandSettingContext? {
            val session = EditorSession.from(route) ?: return null
            val nodeId = route.payload["nodeId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return null
            val role = CommandSettingRole.fromRoute(route.payload["role"])
                ?: if (route.id == "facing_settings") CommandSettingRole.CONTEXT_FACING else null
            return CommandSettingContext(session.scriptId, nodeId, role)
        }
    }
}

/** フィールドを押した後に遷移すべき意味上の編集経路です。 */
enum class CommandSettingEditor {
    TEXT,
    TARGET,
    POSITION,
    FACING,
    CONDITION_KIND,
    CONDITION_DETAIL,
    DISPLAY_MODE,
    ENTITY_ACTION,
    VARIABLE_SCOPE,
    VARIABLE_TYPE,
    VARIABLE_OPERATION,
    VARIABLE_VALUE,
    FOR_SOURCE,
    INCLUSIVE_END,
    CONTEXT,
}

data class CommandSettingDescriptor(
    val editor: CommandSettingEditor,
    val role: CommandSettingRole? = null,
)

/**
 * コマンド型とフィールド名から、両GUIが共有する編集経路を解決します。
 * ここへ新しい設定項目を追加すれば、表示側が「専用選択で編集」を描画でき、
 * インベントリGUIのルートとジェスチャーGUIの選択画面が同じ役割を受け取ります。
 */
object CommandSettingsModel {
    /**
     * 両GUIで同じ条件付きフィールド集合を表示します。
     * 例えばタイトル以外のDISPLAY_TEXTからstayを隠す処理を各画面へ複製しないことで、
     * 片方だけに存在する設定項目や、選択後に参照不能になる値を防ぎます。
     */
    fun visibleFields(node: CommandNode): List<EditorField> {
        val fields = EditorMenuLayout.fields(node.type)
        if (node.type == CommandType.ENTITY_ACTION && node.string("action") != "ride") {
            return fields.filterNot { it.key == "other" }
        }
        if (node.type == CommandType.DISPLAY_TEXT && node.string("mode") != "title") {
            return fields.filterNot { it.key == "stay" }
        }
        if (node.type != CommandType.VARIABLE) return fields
        val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }
            .getOrDefault(VariableOperation.SET)
        return fields.filterNot { field ->
            field.key == "value" && operation !in setOf(
                VariableOperation.SET,
                VariableOperation.ADD,
                VariableOperation.SUBTRACT,
            )
        }
    }

    fun descriptor(node: CommandNode, fieldKey: String): CommandSettingDescriptor = when (node.type) {
        CommandType.TELEPORT -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "destination" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.DESTINATION)
            else -> text()
        }
        CommandType.GIVE_ITEM -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            else -> text()
        }
        CommandType.ENTITY_ACTION -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "action" -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_ACTION)
            "other" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.SECONDARY_TARGET)
            else -> text()
        }
        CommandType.DISPLAY_TEXT -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "mode" -> CommandSettingDescriptor(CommandSettingEditor.DISPLAY_MODE)
            else -> text()
        }
        CommandType.WAIT,
        CommandType.SUMMON_ENTITY,
        CommandType.PLAY_SOUND,
        -> text()
        CommandType.APPLY_EFFECT,
        CommandType.CAMERA_SHAKE,
        CommandType.EQUIP_ITEM,
        -> if (fieldKey == "target") {
            CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
        } else {
            text()
        }
        CommandType.CONDITION -> when (fieldKey) {
            "inverted" -> CommandSettingDescriptor(CommandSettingEditor.INCLUSIVE_END)
            "kind" -> CommandSettingDescriptor(CommandSettingEditor.CONDITION_KIND)
            "condition" -> CommandSettingDescriptor(CommandSettingEditor.CONDITION_DETAIL)
            else -> text()
        }
        CommandType.CONTEXT -> when (fieldKey) {
            "executor" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.CONTEXT_EXECUTOR)
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.CONTEXT_TARGET)
            "position" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.CONTEXT_POSITION)
            "facing" -> CommandSettingDescriptor(CommandSettingEditor.FACING, CommandSettingRole.CONTEXT_FACING)
            else -> text()
        }
        CommandType.DISK_CALL -> text()
        CommandType.VARIABLE -> when (fieldKey) {
            "scope" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_SCOPE)
            "type" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_TYPE)
            "operation" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_OPERATION)
            "value" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_VALUE)
            else -> text()
        }
        CommandType.FOR_START -> when {
            fieldKey.endsWith("Source") -> CommandSettingDescriptor(CommandSettingEditor.FOR_SOURCE)
            fieldKey == "inclusiveEnd" -> CommandSettingDescriptor(CommandSettingEditor.INCLUSIVE_END)
            else -> text()
        }
        CommandType.MERGE,
        CommandType.FOR_END,
        CommandType.BREAK,
        CommandType.CONTINUE,
        -> text()
    }

    fun targetSpec(node: CommandNode, role: CommandSettingRole?): TargetSpec? = when (role) {
        CommandSettingRole.DESTINATION -> node.destinationTargetSpec
        CommandSettingRole.CONTEXT_EXECUTOR -> node.contextOverride?.executor
        CommandSettingRole.CONTEXT_TARGET -> node.contextOverride?.target
        CommandSettingRole.SECONDARY_TARGET -> node.secondaryTargetSpec
        else -> node.targetSpec
    }

    fun setTargetSpec(node: CommandNode, role: CommandSettingRole?, spec: TargetSpec) {
        when (role) {
            CommandSettingRole.DESTINATION -> {
                node.destinationTargetSpec = spec
                node.destinationSpec = null
            }
            CommandSettingRole.CONTEXT_EXECUTOR -> node.contextOverride =
                (node.contextOverride ?: ExecutionContextSpec()).copy(executor = spec)
            CommandSettingRole.CONTEXT_TARGET -> node.contextOverride =
                (node.contextOverride ?: ExecutionContextSpec()).copy(target = spec)
            CommandSettingRole.SECONDARY_TARGET -> node.secondaryTargetSpec = spec
            else -> node.targetSpec = spec
        }
    }

    fun positionSpec(node: CommandNode, role: CommandSettingRole?): PositionSpec? = when (role) {
        CommandSettingRole.DESTINATION -> node.destinationSpec
        CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec
        else -> node.contextOverride?.position
    }

    fun setPositionSpec(node: CommandNode, role: CommandSettingRole?, spec: PositionSpec) {
        when (role) {
            CommandSettingRole.DESTINATION -> {
                node.destinationSpec = spec
                node.destinationTargetSpec = null
            }
            CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec = spec
            else -> node.contextOverride =
                (node.contextOverride ?: ExecutionContextSpec()).copy(position = spec)
        }
    }

    fun facingSpec(node: CommandNode): FacingSpec? = node.contextOverride?.facing

    fun setFacingSpec(node: CommandNode, spec: FacingSpec) {
        node.contextOverride = (node.contextOverride ?: ExecutionContextSpec()).copy(facing = spec)
    }

    fun contextSource(node: CommandNode): ContextSource = node.effectiveContextSource

    fun toggleContextSource(node: CommandNode) {
        node.contextSource = if (node.effectiveContextSource == ContextSource.BASE) {
            ContextSource.PREVIOUS
        } else {
            ContextSource.BASE
        }
    }

    fun allowedVariableOperations(type: VariableType): List<VariableOperation> = when (type) {
        VariableType.BOOLEAN -> listOf(VariableOperation.SET, VariableOperation.TOGGLE, VariableOperation.CLEAR)
        VariableType.INTEGER, VariableType.DECIMAL ->
            listOf(VariableOperation.SET, VariableOperation.ADD, VariableOperation.SUBTRACT, VariableOperation.CLEAR)
        VariableType.TEXT -> listOf(VariableOperation.SET, VariableOperation.CLEAR)
        VariableType.POSITION -> listOf(VariableOperation.STORE_POSITION, VariableOperation.CLEAR)
        VariableType.ENTITY -> listOf(VariableOperation.STORE_TARGET, VariableOperation.CLEAR)
    }

    /** インベントリ／ジェスチャー共通の保存処理です。配置済み表示も同時に更新します。 */
    fun updateNode(
        plugin: KantanCommanderPlugin,
        context: CommandSettingContext,
        change: (CommandNode) -> Unit,
    ): CommandNode? {
        val script = plugin.scripts.load(context.scriptId) ?: return null
        val node = script.graph.nodes[context.nodeId] ?: return null
        change(node)
        plugin.scripts.save(script)
        // 表示体の再生成は永続化成功後の補助処理です。ここで失敗しても
        // 設定値そのものは保存済みなので、入力イベントへ例外を戻さず次回復元へ委ねます。
        runCatching { plugin.placements.refreshDisplaysForScript(script.id) }
            .onFailure { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "設定保存後の配置表示更新に失敗しました: script=${script.id}",
                    failure,
                )
            }
        return node
    }

    private fun text() = CommandSettingDescriptor(CommandSettingEditor.TEXT)
}
