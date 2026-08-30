package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.DisplayTextTimingPolicy
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
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
    BLOCK_POSITION("block_position"),
    BLOCK_FROM("block_from"),
    BLOCK_TO("block_to"),
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
    BLOCK_OPERATION,
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
     * 例えば時間設定に対応しないDISPLAY_TEXTからstaySecondsを隠す処理を各画面へ複製しないことで、
     * 片方だけに存在する設定項目や、選択後に参照不能になる値を防ぎます。
     */
    fun visibleFields(node: CommandNode): List<EditorField> {
        // 表示方式ごとの説明は同じ「時間設定」項目でも意味が異なります。
        // フィールド集合を返す段階で文言も文脈化し、インベントリGUIとジェスチャーGUIの
        // どちらでもタイトル用の説明がアクションバーへ誤表示されないようにします。
        val fields = EditorMenuLayout.fields(node.type).map { field ->
            if (node.type == CommandType.DISPLAY_TEXT &&
                field.key == "staySeconds" &&
                node.string("mode", "tellraw") == "actionbar"
            ) {
                field.copy(
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISPLAY_ACTIONBAR_DURATION,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISPLAY_ACTIONBAR_DURATION,
                )
            } else field
        }
        if (node.type == CommandType.ENTITY_ACTION && node.string("action") != "ride") {
            return fields.filterNot { it.key == "other" }
        }
        if (node.type == CommandType.DISPLAY_TEXT && !DisplayTextTimingPolicy.supports(node)) {
            return fields.filterNot { it.key == "staySeconds" }
        }
        if (node.type == CommandType.BLOCK_OPERATION) {
            return if (node.string("operation", "setblock") == "fill") {
                fields.filterNot { it.key == "position" }
            } else {
                fields.filterNot { it.key == "from" || it.key == "to" }
            }
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
        CommandType.BLOCK_OPERATION -> when (fieldKey) {
            "operation" -> CommandSettingDescriptor(CommandSettingEditor.BLOCK_OPERATION)
            "position" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.BLOCK_POSITION)
            "from" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.BLOCK_FROM)
            "to" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.BLOCK_TO)
            else -> text()
        }
        CommandType.ENTITY_DELETE -> if (fieldKey == "target") {
            CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
        } else text()
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
        node.markConfigured(
            when (role) {
                CommandSettingRole.DESTINATION -> "destination"
                CommandSettingRole.SECONDARY_TARGET -> "other"
                CommandSettingRole.CONTEXT_EXECUTOR -> "executor"
                CommandSettingRole.CONTEXT_TARGET -> "target"
                else -> "target"
            },
        )
    }

    fun positionSpec(node: CommandNode, role: CommandSettingRole?): PositionSpec? = when (role) {
        CommandSettingRole.DESTINATION -> node.destinationSpec
        CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec
        CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec
        CommandSettingRole.BLOCK_FROM -> node.blockFromSpec
        CommandSettingRole.BLOCK_TO -> node.blockToSpec
        else -> node.contextOverride?.position
    }

    /**
     * 位置ドメインの現在種別を返します。
     *
     * 移動先だけは「座標」と「別エンティティ」が別の構造化値へ保存されます。
     * destinationTargetSpecをPositionSpecへ詰め替えて扱うと、対象設定後に親画面の
     * 選択色・現在値が未設定へ戻り、対象の詳細設定も別ドメインへ漏れます。
     * 表示・選択判定はこの共通関数を使い、実データはtargetSpec/positionSpecの
     * 各setterへ分けて保存します。
     */
    fun positionKind(node: CommandNode, role: CommandSettingRole?): PositionKind? = when (role) {
        CommandSettingRole.DESTINATION -> when {
            node.destinationTargetSpec != null -> PositionKind.TARGET
            else -> node.destinationSpec?.kind
        }
        CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec?.kind
        CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec?.kind
        CommandSettingRole.BLOCK_FROM -> node.blockFromSpec?.kind
        CommandSettingRole.BLOCK_TO -> node.blockToSpec?.kind
        else -> node.contextOverride?.position?.kind
    }

    fun setPositionSpec(node: CommandNode, role: CommandSettingRole?, spec: PositionSpec) {
        when (role) {
            CommandSettingRole.DESTINATION -> {
                node.destinationSpec = spec
                node.destinationTargetSpec = null
            }
            CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec = spec
            CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec = spec
            CommandSettingRole.BLOCK_FROM -> node.blockFromSpec = spec
            CommandSettingRole.BLOCK_TO -> node.blockToSpec = spec
            else -> node.contextOverride =
                (node.contextOverride ?: ExecutionContextSpec()).copy(position = spec)
        }
        node.markConfigured(
            when (role) {
                CommandSettingRole.DESTINATION -> "destination"
                CommandSettingRole.CONDITION_POSITION -> "condition"
                CommandSettingRole.BLOCK_POSITION -> "position"
                CommandSettingRole.BLOCK_FROM -> "from"
                CommandSettingRole.BLOCK_TO -> "to"
                else -> "position"
            },
        )
    }

    fun facingSpec(node: CommandNode): FacingSpec? = node.contextOverride?.facing

    fun setFacingSpec(node: CommandNode, spec: FacingSpec) {
        node.contextOverride = (node.contextOverride ?: ExecutionContextSpec()).copy(facing = spec)
        node.markConfigured("facing")
    }

    fun contextSource(node: CommandNode): ContextSource = node.effectiveContextSource

    fun toggleContextSource(node: CommandNode) {
        node.contextSource = if (node.effectiveContextSource == ContextSource.BASE) {
            ContextSource.PREVIOUS
        } else {
            ContextSource.BASE
        }
        node.markConfigured("context")
    }

    /**
     * 設定画面で表示する値の状態を、文字列値と構造化値の両方から一元判定します。
     *
     * 既存JSONにはconfiguredFieldsがないため、まず構造化値／既定値との差を推定し、
     * 新しい画面から明示的に既定値を選んだ場合だけCommandNodeの明示記録を優先します。
     * これにより、過去データを一律「未設定」と表示する退行を避けられます。
     */
    fun isFieldConfigured(
        node: CommandNode,
        fieldKey: String,
        role: CommandSettingRole? = null,
    ): Boolean {
        if (node.isExplicitlyConfigured(fieldKey)) return true
        return when (fieldKey) {
            "target" -> targetSpec(node, role) != null
            "destination" -> node.destinationSpec != null || node.destinationTargetSpec != null
            "other" -> node.secondaryTargetSpec != null
            "executor" -> node.contextOverride?.executor != null
            "position" -> when (role) {
                CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec != null
                CommandSettingRole.CONTEXT_POSITION -> node.contextOverride?.position != null
                CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec != null
                else -> node.contextOverride?.position != null
            }
            "from" -> node.blockFromSpec != null
            "to" -> node.blockToSpec != null
            "facing" -> node.contextOverride?.facing != null
            "context" -> node.contextOverride != null || node.effectiveContextSource != ContextSource.BASE
            "item" -> node.string("item").isNotBlank() || node.string("itemData").isNotBlank()
            "diskId" -> node.string("diskId").isNotBlank() || node.snapshot != null
            "condition" -> conditionDetailConfigured(node)
            else -> {
                val value = node.params[fieldKey] ?: return false
                value.isNotBlank() && value != node.type.defaults[fieldKey]
            }
        }
    }

    /** 対象の詳細条件は各項目が独立して設定されるため、複数選択色へ投影します。 */
    fun isTargetFilterConfigured(
        node: CommandNode,
        role: CommandSettingRole?,
        parameter: String,
    ): Boolean {
        val spec = targetSpec(node, role) ?: return false
        if (node.isExplicitlyConfigured("target.$parameter")) return true
        return when (parameter) {
            "entityType" -> !spec.entityType.isNullOrBlank()
            "minimumDistance" -> spec.minimumDistance != null
            "maximumDistance" -> spec.maximumDistance != null
            "limit" -> spec.limit != null
            "sort" -> spec.sort != TargetSort.NEAREST
            "gameMode" -> !spec.gameMode.isNullOrBlank()
            "tag" -> !spec.tag.isNullOrBlank()
            "name" -> !spec.name.isNullOrBlank()
            else -> false
        }
    }

    private fun conditionDetailConfigured(node: CommandNode): Boolean =
        node.targetSpec != null || node.conditionPositionSpec != null ||
            node.params.any { (key, value) ->
                key in setOf("state", "variable", "variableScope", "operator", "value", "block", "count", "item") &&
                    value.isNotBlank() && value != node.type.defaults[key]
            }

    fun allowedVariableOperations(type: VariableType): List<VariableOperation> = when (type) {
        VariableType.BOOLEAN -> listOf(VariableOperation.SET, VariableOperation.TOGGLE, VariableOperation.CLEAR)
        VariableType.INTEGER, VariableType.DECIMAL ->
            listOf(VariableOperation.SET, VariableOperation.ADD, VariableOperation.SUBTRACT, VariableOperation.CLEAR)
        VariableType.TEXT -> listOf(VariableOperation.SET, VariableOperation.CLEAR)
        VariableType.POSITION -> listOf(VariableOperation.STORE_POSITION, VariableOperation.CLEAR)
        VariableType.ENTITY -> listOf(VariableOperation.STORE_TARGET, VariableOperation.CLEAR)
    }

    private val PLAYER_KINDS = setOf(
        TargetKind.NEAREST_PLAYER,
        TargetKind.NEARBY_PLAYERS,
        TargetKind.ALL_PLAYERS,
        TargetKind.RANDOM_PLAYER,
    )
    private val ENTITY_KINDS = setOf(TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES)

    /**
     * 対象種別に対して詳細条件が意味を持つかを判定します。
     * 実行側の解決（matches）に合わせ、プレイヤー種別へentityType、
     * エンティティ種別へgameModeを指定しても解決しないため、GUIでは提示しません。
     */
    fun targetFilterApplies(kind: TargetKind?, parameter: String): Boolean = when (parameter) {
        "entityType" -> kind in ENTITY_KINDS
        "gameMode" -> kind in PLAYER_KINDS
        else -> true
    }

    /**
     * 対象種別と詳細条件の境界を共有します。
     * 親画面の種別選択後に詳細子画面へ進めるかを、描画側と入力状態遷移側で
     * 別々に持つと、表示された「詳細設定」が開けない対象が生まれるためです。
     */
    fun targetSupportsDetailedFilters(kind: TargetKind?): Boolean =
        kind?.let { it in FILTERABLE_TARGET_KINDS } == true

    /** インベントリ／ジェスチャー共通の保存処理です。配置済み表示も同時に更新します。 */
    fun updateNode(
        plugin: KantanCommanderPlugin,
        context: CommandSettingContext,
        configuredFields: Set<String> = emptySet(),
        change: (CommandNode) -> Unit,
    ): CommandNode? {
        val script = plugin.scripts.load(context.scriptId) ?: return null
        val node = script.graph.nodes[context.nodeId] ?: return null
        change(node)
        node.markConfigured(*configuredFields.toTypedArray())
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

    private val FILTERABLE_TARGET_KINDS = setOf(
        TargetKind.NEAREST_PLAYER,
        TargetKind.NEARBY_PLAYERS,
        TargetKind.ALL_PLAYERS,
        TargetKind.RANDOM_PLAYER,
        TargetKind.NEAREST_ENTITY,
        TargetKind.NEARBY_ENTITIES,
    )
}
