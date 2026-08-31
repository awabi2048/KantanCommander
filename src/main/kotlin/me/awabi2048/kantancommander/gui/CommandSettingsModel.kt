package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.DisplayTextTimingPolicy
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.MIN_TIMER_SECONDS
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.effectiveContextSource
import me.awabi2048.kantancommander.model.hasContextOverride
import me.awabi2048.kantancommander.model.supportsContextOverride
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

/** UIで統一表示する対象の大分類です。実行モデルのTargetKindとは分離します。 */
enum class TargetCategory {
    INHERITED,
    PLAYER,
    NON_PLAYER_ENTITY,
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
    private val PLAYER_KINDS = setOf(
        TargetKind.NEAREST_PLAYER,
        TargetKind.NEARBY_PLAYERS,
        TargetKind.ALL_PLAYERS,
        TargetKind.RANDOM_PLAYER,
    )
    private val ENTITY_KINDS = setOf(
        TargetKind.NEAREST_ENTITY,
        TargetKind.NEARBY_ENTITIES,
        TargetKind.FIXED_ENTITY,
    )

    /** 実行時の細分類を、GUIで表示する三つの対象大分類へ写像します。 */
    fun targetCategory(kind: TargetKind?): TargetCategory = when (kind) {
        TargetKind.INHERITED_TARGET, null -> TargetCategory.INHERITED
        in PLAYER_KINDS -> TargetCategory.PLAYER
        else -> TargetCategory.NON_PLAYER_ENTITY
    }

    /** 大分類を初めて選んだときに採用する実行モデル上の既定種別です。 */
    fun defaultTargetKind(category: TargetCategory): TargetKind = when (category) {
        TargetCategory.INHERITED -> TargetKind.INHERITED_TARGET
        TargetCategory.PLAYER -> TargetKind.NEAREST_PLAYER
        TargetCategory.NON_PLAYER_ENTITY -> TargetKind.NEAREST_ENTITY
    }

    /** 大分類の詳細画面で選択できる実行モデル上の細分類を返します。 */
    fun targetKinds(category: TargetCategory): List<TargetKind> = when (category) {
        TargetCategory.INHERITED -> emptyList()
        TargetCategory.PLAYER -> listOf(
            TargetKind.NEAREST_PLAYER,
            TargetKind.NEARBY_PLAYERS,
            TargetKind.ALL_PLAYERS,
            TargetKind.RANDOM_PLAYER,
        )
        TargetCategory.NON_PLAYER_ENTITY -> listOf(
            TargetKind.NEAREST_ENTITY,
            TargetKind.NEARBY_ENTITIES,
            TargetKind.FIXED_ENTITY,
        )
    }

    /** 既存の細分類を維持したまま大分類だけを表示するための所属判定です。 */
    fun targetCategoryMatches(kind: TargetKind?, category: TargetCategory): Boolean =
        targetCategory(kind) == category

    /**
     * 現在ノードより前の実行経路に、継承対象として利用できる対象設定があるかを返します。
     * LinkedHashMapの挿入順は実行順を保証しないため、entryからnext/trueNextを辿ります。
     * ループはvisitedで止め、現在ノード自身の設定を「前の設定」と誤認しません。
     */
    fun hasPriorTargetContext(graph: me.awabi2048.kantancommander.model.CommandGraph, nodeId: UUID): Boolean {
        val entry = graph.entryNodeId ?: return false
        val visited = mutableSetOf<UUID>()
        fun establishesTarget(node: CommandNode): Boolean =
            node.targetSpec?.kind?.let { it != TargetKind.INHERITED_TARGET } == true ||
                node.contextOverride?.target?.kind?.let { it != TargetKind.INHERITED_TARGET } == true
        fun visit(currentId: UUID): Boolean {
            if (currentId == nodeId) return false
            if (!visited.add(currentId)) return false
            val node = graph.nodes[currentId] ?: return false
            if (establishesTarget(node)) return true
            return listOfNotNull(node.next, node.trueNext, node.falseNext).any(::visit)
        }
        return visit(entry)
    }

    /** 対象の大分類を選択可能にする条件（継承だけは前置き設定を要求）です。 */
    fun targetCategoryAvailable(
        graph: me.awabi2048.kantancommander.model.CommandGraph,
        nodeId: UUID,
        category: TargetCategory,
    ): Boolean = category != TargetCategory.INHERITED || hasPriorTargetContext(graph, nodeId)

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
        val conditionalFields = when {
            node.type == CommandType.ENTITY_ACTION && node.string("action") != "ride" ->
                fields.filterNot { it.key == "other" }
            node.type == CommandType.DISPLAY_TEXT && !DisplayTextTimingPolicy.supports(node) ->
                fields.filterNot { it.key == "staySeconds" }
            node.type == CommandType.BLOCK_OPERATION ->
                if (node.string("operation", "setblock") == "fill") {
                    fields.filterNot { it.key == "position" }
                } else {
                    fields.filterNot { it.key == "from" || it.key == "to" }
                }
            node.type == CommandType.VARIABLE -> {
                val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }
                    .getOrDefault(VariableOperation.SET)
                fields.filterNot { field ->
                    field.key == "value" && operation !in setOf(
                        VariableOperation.SET,
                        VariableOperation.ADD,
                        VariableOperation.SUBTRACT,
                    )
                }
            }
            else -> fields
        }
        // コンテキスト設定も通常フィールドへ投影し、Inventory/Gestureのどちらから開いても
        // 同じ表示・選択・保存契約を通ります。VARIABLEはドメイン契約で対象外です。
        return if (node.type.supportsContextOverride()) {
            conditionalFields + contextField()
        } else {
            conditionalFields
        }
    }

    fun descriptor(node: CommandNode, fieldKey: String): CommandSettingDescriptor {
        if (fieldKey == "context" && node.type.supportsContextOverride()) {
            return CommandSettingDescriptor(CommandSettingEditor.CONTEXT)
        }
        return when (node.type) {
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
    }

    /**
     * 文字列パラメータの書き込みも、両GUIが同じドメイン入口を通ります。
     *
     * 構造化値だけを共通setterへ寄せても、条件・表示・変数などのparamsだけが
     * 画面ごとの直接代入に残ると、明示設定の記録や将来の検証を再び分岐させます。
     * 文字列値の保存と「ユーザーが設定した」状態をここで同時に扱います。
     */
    fun setParameter(node: CommandNode, key: String, value: String) {
        require(key.isNotBlank()) { "設定キーが空です" }
        node.params[key] = value
        node.markConfigured(key)
    }

    /** 複数の文字列パラメータを同じ明示設定契約で保存します。 */
    fun setParameters(node: CommandNode, values: Map<String, String>) {
        values.forEach { (key, value) -> setParameter(node, key, value) }
    }

    fun targetSpec(node: CommandNode, role: CommandSettingRole?): TargetSpec? = when (role) {
        CommandSettingRole.DESTINATION -> node.destinationTargetSpec
        CommandSettingRole.CONTEXT_EXECUTOR -> node.contextOverride?.executor
        CommandSettingRole.CONTEXT_TARGET -> node.contextOverride?.target
        CommandSettingRole.SECONDARY_TARGET -> node.secondaryTargetSpec
        else -> node.targetSpec
    }

    fun setTargetSpec(node: CommandNode, role: CommandSettingRole?, spec: TargetSpec) {
        check(
            role !in setOf(CommandSettingRole.CONTEXT_EXECUTOR, CommandSettingRole.CONTEXT_TARGET) ||
                node.type == CommandType.CONTEXT || node.type.supportsContextOverride()
        ) {
            "${node.type} は実行コンテキスト上書きを持てません"
        }
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
        node.markConfigured(configuredFieldKey("target", role))
    }

    fun positionSpec(node: CommandNode, role: CommandSettingRole?): PositionSpec? = when (role) {
        CommandSettingRole.DESTINATION -> node.destinationSpec
        CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec
        CommandSettingRole.CONTEXT_POSITION -> node.contextOverride?.position
        CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec
        CommandSettingRole.BLOCK_FROM -> node.blockFromSpec
        CommandSettingRole.BLOCK_TO -> node.blockToSpec
        else -> null
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
        CommandSettingRole.CONTEXT_POSITION -> node.contextOverride?.position?.kind
        CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec?.kind
        CommandSettingRole.BLOCK_FROM -> node.blockFromSpec?.kind
        CommandSettingRole.BLOCK_TO -> node.blockToSpec?.kind
        else -> null
    }

    fun setPositionSpec(node: CommandNode, role: CommandSettingRole?, spec: PositionSpec) {
        check(role != CommandSettingRole.CONTEXT_POSITION || node.type == CommandType.CONTEXT || node.type.supportsContextOverride()) {
            "${node.type} は実行コンテキスト上書きを持てません"
        }
        when (role) {
            CommandSettingRole.DESTINATION -> {
                node.destinationSpec = spec
                node.destinationTargetSpec = null
            }
            CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec = spec
            CommandSettingRole.CONTEXT_POSITION -> node.contextOverride =
                (node.contextOverride ?: ExecutionContextSpec()).copy(position = spec)
            CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec = spec
            CommandSettingRole.BLOCK_FROM -> node.blockFromSpec = spec
            CommandSettingRole.BLOCK_TO -> node.blockToSpec = spec
            else -> error("${node.type} の位置設定役割が不正です: $role")
        }
        node.markConfigured(configuredFieldKey("position", role))
    }

    fun facingSpec(node: CommandNode): FacingSpec? = node.contextOverride?.facing

    fun setFacingSpec(node: CommandNode, spec: FacingSpec) {
        check(node.type == CommandType.CONTEXT || node.type.supportsContextOverride()) {
            "${node.type} は実行コンテキスト上書きを持てません"
        }
        node.contextOverride = (node.contextOverride ?: ExecutionContextSpec()).copy(facing = spec)
        node.markConfigured(configuredFieldKey("facing", CommandSettingRole.CONTEXT_FACING))
    }

    fun contextSource(node: CommandNode): ContextSource = node.effectiveContextSource

    fun toggleContextSource(node: CommandNode) {
        check(node.type == CommandType.CONTEXT || node.type.supportsContextOverride()) {
            "${node.type} は実行コンテキスト上書きを持てません"
        }
        val source = if (node.effectiveContextSource == ContextSource.BASE) {
            ContextSource.PREVIOUS
        } else {
            ContextSource.BASE
        }
        node.contextSource = source
        // BASEは「すべて継承」の実効状態です。明示フラグだけを残すと、値がBASEへ
        // 戻ったあとも設定済みと表示されるため、選択状態を実効値から一貫して導出します。
        if (source == ContextSource.BASE) {
            node.clearConfigured(configuredFieldKey("context", null))
        } else {
            node.markConfigured(configuredFieldKey("context", null))
        }
    }

    /** コンテキスト設定を「すべて継承」へ戻す共通操作です。 */
    fun clearContextOverride(node: CommandNode) {
        check(node.type == CommandType.CONTEXT || node.type.supportsContextOverride()) {
            "${node.type} は実行コンテキスト上書きを持てません"
        }
        node.contextOverride = null
        // 上書き本体だけ消してPREVIOUSを残すと、表示は「全継承」でも実行時には
        // 直前文脈を選び続けます。入力の意味と実行結果を一致させるため、継承元もBASEへ戻します。
        node.contextSource = ContextSource.BASE
        node.clearConfigured("context")
        node.clearConfiguredPrefix("context.")
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
        // roleを省略したInventoryの一覧描画でも、コマンド型から保存先を復元します。
        // これをしないとBLOCK_OPERATIONのpositionがコンテキスト位置として判定され、
        // InventoryとGestureで同じ値を表示していても選択状態だけがずれます。
        val effectiveRole = role ?: descriptor(node, fieldKey).role
        // contextはcontextSource/contextOverrideの実効値からだけ判定します。履歴的な
        // 明示フラグを先に見ると、BASEへ戻した選択が「設定済み」として残ります。
        if (fieldKey == "context") {
            return node.hasContextOverride() || node.effectiveContextSource != ContextSource.BASE
        }
        if (node.isExplicitlyConfigured(configuredFieldKey(fieldKey, effectiveRole))) return true
        return when (fieldKey) {
            "target" -> targetSpec(node, effectiveRole) != null
            "destination" -> node.destinationSpec != null || node.destinationTargetSpec != null
            "other" -> node.secondaryTargetSpec != null
            "executor" -> node.contextOverride?.executor != null
            "position" -> when (effectiveRole) {
                CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec != null
                CommandSettingRole.CONTEXT_POSITION -> node.contextOverride?.position != null
                CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec != null
                else -> node.contextOverride?.position != null
            }
            "from" -> node.blockFromSpec != null
            "to" -> node.blockToSpec != null
            "facing" -> node.contextOverride?.facing != null
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
        if (node.isExplicitlyConfigured(configuredFieldKey("target.$parameter", role))) return true
        return when (parameter) {
            "kind" -> spec.kind != TargetKind.INHERITED_TARGET
            "entityType" -> !spec.entityType.isNullOrBlank()
            "distance" -> spec.minimumDistance != null || spec.maximumDistance != null
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

    /**
     * 対象種別に対して詳細条件が意味を持つかを判定します。
     * 実行側の解決（matches）に合わせ、プレイヤー種別へentityType、
     * エンティティ種別へgameModeを指定しても解決しないため、GUIでは提示しません。
     */
    fun targetFilterApplies(kind: TargetKind?, parameter: String): Boolean = when (parameter) {
        "kind" -> kind != null && kind != TargetKind.INHERITED_TARGET
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

    /**
     * プログラム全体の編集を共通保存境界へ通します。
     *
     * 名前・タイマー・有効化方式を画面ごとに直接保存すると、変更履歴・配置表示・
     * 実行時状態の更新順序が分岐します。ここで正本の独立コピーを読み込み、保存後
     * の補助処理も共通化します。
     */
    fun updateScript(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
        afterSave: (DiskScript) -> Unit = {},
        change: (DiskScript) -> Unit,
    ): Boolean {
        val script = plugin.scripts.update(scriptId, editorId, expectedRevision) { current ->
            change(current)
            current
        } ?: return false
        afterSave(script)
        refreshDisplays(plugin, script.id)
        plugin.gestureEditor.refreshForScript(script.id)
        return true
    }

    /** インベントリ／ジェスチャー共通のグラフ更新処理です。 */
    fun <T : Any> updateGraph(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
        change: (CommandGraph) -> T?,
    ): T? {
        val updated = plugin.scripts.update(scriptId, editorId, expectedRevision) { script ->
            val candidateGraph = script.graph.deepCopy()
            val result = change(candidateGraph) ?: return@update null
            // 描画セルの衝突・幅・高さ検証を保存前に必ず通し、両GUIの経路編集で
            // 「保存後にだけ描画不能になる」差を作りません。
            GraphLayoutEngine.layout(candidateGraph)
            script.graph = candidateGraph
            result
        } ?: return null
        refreshDisplays(plugin, scriptId)
        plugin.gestureEditor.refreshForScript(scriptId)
        return updated
    }

    /** インベントリ／ジェスチャー共通のノード保存処理です。配置済み表示も更新します。 */
    fun updateNode(
        plugin: KantanCommanderPlugin,
        context: CommandSettingContext,
        configuredFields: Set<String> = emptySet(),
        editorId: UUID? = null,
        expectedRevision: Long? = null,
        change: (CommandNode) -> Unit,
    ): CommandNode? = updateGraph(plugin, context.scriptId, editorId, expectedRevision) { graph ->
        graph.nodes[context.nodeId]?.also { node ->
            change(node)
            node.markConfigured(
                *configuredFields
                    .map { configuredFieldKey(it, context.role) }
                    .toTypedArray(),
            )
        }
    }

    /** プログラム名を両GUI共通の更新入口から変更します。 */
    fun updateScriptName(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        name: String,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
    ): Boolean {
        require(name.isNotBlank()) { "プログラム名が空です" }
        return updateScript(plugin, scriptId, editorId, expectedRevision = expectedRevision) { it.name = name }
    }

    /**
     * プログラム全体設定であるタイマーも、両GUIが同じ保存境界を通ります。
     *
     * タイマー値だけを各画面で更新すると、間隔変更時の再登録や無効化時の
     * ALWAYS_ACTIVE解除が片方から抜けます。ノード設定と同じく、読み込み・検証・
     * 保存・実行時登録更新・表示更新を一つの操作として扱います。
     */
    fun updateTimer(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        enabled: Boolean,
        intervalSeconds: Int? = null,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
    ): Boolean {
        return updateScript(
            plugin,
            scriptId,
            editorId,
            expectedRevision = expectedRevision,
            afterSave = { plugin.resetActivationTiming(it.id) },
        ) { script ->
            if (enabled) {
                val seconds = requireNotNull(intervalSeconds) { "タイマー有効化には間隔が必要です" }
                require(seconds in MIN_TIMER_SECONDS..MAX_TIMER_SECONDS) {
                    "タイマー間隔は${MIN_TIMER_SECONDS}から${MAX_TIMER_SECONDS}秒で指定してください"
                }
                script.timer.intervalSeconds = seconds
            }
            script.timer.enabled = enabled
            if (!enabled) script.activation = ActivationMode.NEEDS_REDSTONE
        }
    }

    /** タイマー有効時の実行方式切替も、プログラム共通の保存境界へ通します。 */
    fun toggleActivation(
        plugin: KantanCommanderPlugin,
        scriptId: UUID,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
    ): Boolean {
        return updateScript(plugin, scriptId, editorId, expectedRevision = expectedRevision) { script ->
            check(script.timer.enabled) { "タイマーが無効なため実行方式を変更できません" }
            script.activation = script.activation.toggled(timerEnabled = true)
        }
    }

    /** 保存成功後だけ配置表示を更新し、補助表示の失敗を設定保存へ波及させません。 */
    private fun refreshDisplays(plugin: KantanCommanderPlugin, scriptId: UUID) {
        runCatching { plugin.placements.refreshDisplaysForScript(scriptId) }
            .onFailure { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "設定保存後の配置表示更新に失敗しました: script=$scriptId",
                    failure,
                )
            }
    }

    private fun text() = CommandSettingDescriptor(CommandSettingEditor.TEXT)

    /** 構造化フィールドの明示設定キーを、保存先の役割ごとに名前空間化します。 */
    private fun configuredFieldKey(fieldKey: String, role: CommandSettingRole?): String = when (role) {
        CommandSettingRole.CONTEXT_EXECUTOR -> if (fieldKey.startsWith("target.")) {
            "context.executor.${fieldKey.removePrefix("target.")}"
        } else "context.executor"
        CommandSettingRole.CONTEXT_TARGET -> if (fieldKey.startsWith("target.")) {
            "context.$fieldKey"
        } else "context.target"
        CommandSettingRole.CONTEXT_POSITION -> "context.position"
        CommandSettingRole.CONTEXT_FACING -> "context.facing"
        CommandSettingRole.CONDITION_POSITION -> "condition.position"
        CommandSettingRole.DESTINATION -> if (fieldKey.startsWith("target.")) {
            "destination.$fieldKey"
        } else "destination"
        CommandSettingRole.SECONDARY_TARGET -> if (fieldKey.startsWith("target.")) {
            "other.$fieldKey"
        } else "other"
        else -> fieldKey
    }

    private fun contextField() = EditorField(
        key = "context",
        label = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONTEXT,
        material = CommandType.CONTEXT.icon,
        descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_CONTEXT,
        actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_CONTEXT,
        value = { node ->
            DisplayValue.Localized(
                if (isFieldConfigured(node, "context")) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONFIGURED
                } else {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERITED
                },
            )
        },
    )

    private val FILTERABLE_TARGET_KINDS = PLAYER_KINDS + ENTITY_KINDS
}
