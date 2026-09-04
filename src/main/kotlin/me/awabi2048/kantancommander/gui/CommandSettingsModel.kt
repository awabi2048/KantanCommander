package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.DisplayTextTimingPolicy
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.MIN_TIMER_SECONDS
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TemporaryTemplate
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import java.util.UUID

/**
 * 個別設定画面が対象とする構造化データの役割です。
 *
 * インベントリGUIは従来payloadへ任意文字列を詰めていましたが、ジェスチャーGUIまで
 * 同じ分岐を複製すると、対象／位置／向きの保存先がずれます。このenumを両GUIの
 * 中間表現として使い、画面固有のルート文字列をドメインの役割へ変換します。
 */
enum class CommandSettingRole(val routeValue: String, val tabFieldKey: String) {
    NODE_TARGET("node_target", "target"),
    DESTINATION("destination", "destination"),
    DESTINATION_FACING("destination_facing", "destinationFacing"),
    SECONDARY_TARGET("secondary_target", "other"),
    CONDITION_POSITION("condition_position", "condition"),
    BLOCK_POSITION("block_position", "position"),
    BLOCK_FROM("block_from", "from"),
    BLOCK_TO("block_to", "to"),
    SOUND_POSITION("sound_position", "soundPosition"),
    SUMMON_POSITION("summon_position", "summonPosition"),
    /** TEMP_SET ENTITYは通常コマンドと同じ対象選択木から1体を解決します。 */
    TEMPORARY_ENTITY("temporary_entity", "entity"),
    /** TEMP_SET LOCATIONは位置と向きをまとめた親画面から共通設定へ進みます。 */
    TEMPORARY_LOCATION("temporary_location", "location"),
    TEMPORARY_LOCATION_POSITION("temporary_location_position", "location"),
    TEMPORARY_LOCATION_FACING("temporary_location_facing", "location"),
    ;

    companion object {
        fun fromRoute(value: String?): CommandSettingRole? =
            entries.firstOrNull { it.routeValue == value }
    }
}

/** UIで統一表示する対象の大分類です。実行モデルのTargetKindとは分離します。 */
enum class TargetCategory {
    /** 対象設定がまだ存在しない状態です。暗黙の対象へ解決する意味は持ちません。 */
    UNSET,
    PLAYER,
    NON_PLAYER_ENTITY,
    TEMPORARY,
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
    LOCATION,
    CONDITION_KIND,
    CONDITION_DETAIL,
    DISPLAY_MODE,
    ENTITY_ACTION,
    ENTITY_EQUIPMENT_SLOT,
    ENTITY_OVERWRITE,
    ENTITY_TAG_OPERATION,
    VARIABLE_TYPE,
    VARIABLE_OPERATION,
    VARIABLE_CHANGE_MODE,
    VARIABLE_VALUE,
    CONDITION_INVERSION,
    CAMERA_SHAKE_TYPE,
    SOUND_SCOPE,
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

    /** 実行時の細分類を、GUIで表示する対象大分類へ写像します。 */
    fun targetCategory(kind: TargetKind?): TargetCategory = when (kind) {
        null -> TargetCategory.UNSET
        TargetKind.TEMPORARY -> TargetCategory.TEMPORARY
        in PLAYER_KINDS -> TargetCategory.PLAYER
        else -> TargetCategory.NON_PLAYER_ENTITY
    }

    /** 大分類を初めて選んだときに採用する実行モデル上の既定種別です。 */
    fun defaultTargetKind(category: TargetCategory): TargetKind = when (category) {
        TargetCategory.UNSET -> TargetKind.NEAREST_PLAYER
        TargetCategory.PLAYER -> TargetKind.NEAREST_PLAYER
        TargetCategory.NON_PLAYER_ENTITY -> TargetKind.NEAREST_ENTITY
        TargetCategory.TEMPORARY -> TargetKind.TEMPORARY
    }

    /** 大分類の詳細画面で選択できる実行モデル上の細分類を返します。 */
    fun targetKinds(category: TargetCategory): List<TargetKind> = when (category) {
        TargetCategory.UNSET -> emptyList()
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
        TargetCategory.TEMPORARY -> listOf(TargetKind.TEMPORARY)
    }

    /** 既存の細分類を維持したまま大分類だけを表示するための所属判定です。 */
    fun targetCategoryMatches(kind: TargetKind?, category: TargetCategory): Boolean =
        targetCategory(kind) == category

    /**
     * 両GUIで同じ条件付きフィールド集合を表示します。
     * 例えば時間設定に対応しないDISPLAY_TEXTからstaySecondsを隠す処理を各画面へ複製しないことで、
     * 片方だけに存在する設定項目や、選択後に参照不能になる値を防ぎます。
     */
    fun visibleFields(node: CommandNode): List<EditorField> {
        // 表示方式ごとの説明は同じ「時間設定」項目でも意味が異なります。
        // フィールド集合を返す段階で文言も文脈化し、インベントリGUIとジェスチャーGUIの
        // どちらでもタイトル用の説明がアクションバーへ誤表示されないようにします。
        val fields = EditorMenuLayout.fields(node.type, node).map { field ->
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
            node.type == CommandType.ENTITY_ACTION -> when (node.string("action", "ride")) {
                // 操作ごとの許可リストを明示します。除外リスト方式では、別操作へ
                // 追加した詳細項目が無関係な操作へ漏れ続けるためです。
                "ride" -> fields.filter { it.key in setOf("target", "action", "other") }
                "dismount" -> fields.filter { it.key in setOf("target", "action") }
                // itemDataはメインハンドの実物から内部保存する値であり、利用者が
                // 別途編集する設定ではありません。表示すると実物選択と手入力の
                // 二重入口が生まれ、装備内容の正本が分岐します。
                "equip" -> fields.filter { it.key in setOf("target", "action", "slot", "item", "overwrite") }
                "tag" -> fields.filter { it.key in setOf("target", "action", "tagOperation", "tag") }
                else -> fields.filter { it.key in setOf("target", "action") }
            }
            node.type == CommandType.DISPLAY_TEXT -> {
                val withoutUnusedSubtitle = if (node.string("mode", "tellraw") == "title") {
                    fields
                } else {
                    fields.filterNot { it.key == "subtitle" }
                }
                if (DisplayTextTimingPolicy.supports(node)) withoutUnusedSubtitle
                else withoutUnusedSubtitle.filterNot { it.key == "staySeconds" }
            }
            node.type == CommandType.BLOCK_OPERATION ->
                if (node.string("operation", "setblock") == "fill") {
                    fields.filterNot { it.key == "position" }
                } else {
                    fields.filterNot { it.key == "from" || it.key == "to" }
                }
            node.type == CommandType.VARIABLE -> {
                val operation = runCatching { VariableOperation.valueOf(node.string("operation")) }
                    .getOrDefault(VariableOperation.DEFINE)
                fields.filterNot { field ->
                    when (operation) {
                        VariableOperation.DEFINE -> field.key == "changeMode"
                        VariableOperation.CHANGE -> field.key == "type"
                    }
                }
            }
            else -> fields
        }
        return conditionalFields
    }

    /**
     * Gesture GUIで表示するフィールドを返します。
     *
     * Inventory GUIとGesture GUIは同じ「表示時間」設定を編集しますが、Gesture GUIでは
     * タブを増やさず、選択中のタブの現在値欄へ3項目をまとめて表示します。値の結合は
     * 表示専用の意味型へ閉じ込め、各画面が独自の文字列フォーマットを持たないようにします。
     */
    fun gestureVisibleFields(node: CommandNode): List<EditorField> =
        visibleFields(node).map { field ->
            if (node.type == CommandType.DISPLAY_TEXT &&
                field.key == "staySeconds" &&
                DisplayTextTimingPolicy.supports(node)
            ) {
                field.copy(
                    value = { currentNode ->
                        DisplayValue.Timing(
                            fadeInSeconds = currentNode.string(
                                "fadeInSeconds",
                                currentNode.type.defaults["fadeInSeconds"].orEmpty(),
                            ),
                            staySeconds = currentNode.string(
                                "staySeconds",
                                currentNode.type.defaults["staySeconds"].orEmpty(),
                            ),
                            fadeOutSeconds = currentNode.string(
                                "fadeOutSeconds",
                                currentNode.type.defaults["fadeOutSeconds"].orEmpty(),
                            ),
                        )
                    },
                )
            } else {
                field
            }
        }

    /**
     * 下部画面の設定タブへ表示する、未完了警告の固定キーを返します。
     *
     * 検証エラーの文言や保存値から表示文字列を組み立てると、同じタブでも
     * エラーの種類によって表示が揺れ、ローカライズ契約も画面側へ漏れます。
     * そのため、画面で編集できる設定項目ごとに、コマンド型とfieldKeyの
     * 組み合わせをこの表で固定します。実際の文言はCC-Systemの型付きキーから
     * 解決し、タブ単位で常に同じ警告を表示します。
     */
    fun incompleteWarningKey(node: CommandNode, fieldKey: String): LocalizationKey<String> {
        return when (node.type) {
            CommandType.TELEPORT -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "destination" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DESTINATION
                "destinationFacing" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DESTINATION_FACING
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.GIVE_ITEM -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_GIVE_TARGET
                "item" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_GIVE_ITEM
                "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_COUNT
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.ENTITY_ACTION -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "action" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_ACTION
                "other" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_OTHER
                "slot" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_EQUIPMENT_SLOT
                "item" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_EQUIPMENT_ITEM
                "overwrite" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_OVERWRITE
                "tagOperation" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TAG_OPERATION
                "tag" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TAG
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.DISPLAY_TEXT -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DISPLAY_TARGET
                "mode" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_MODE
                "text", "subtitle" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TEXT
                "fadeInSeconds", "staySeconds", "fadeOutSeconds" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DURATION
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.WAIT -> when (fieldKey) {
                "seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_WAIT
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.SUMMON_ENTITY -> when (fieldKey) {
                "entity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_ENTITY
                "customName" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_NAME
                "tags" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TAGS
                "summonPosition" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SUMMON_POSITION
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.PLAY_SOUND -> when (fieldKey) {
                "sound" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SOUND
                "soundParameters" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SOUND_PARAMETERS
                "soundScope" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SOUND_SCOPE
                "soundPosition" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SOUND_POSITION
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.APPLY_EFFECT -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "effect" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_EFFECT
                "level" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_LEVEL
                "seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SECONDS
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.CAMERA_SHAKE -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                "intensity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_INTENSITY
                "seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SECONDS
                "shakeType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_SHAKE_TYPE
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.BLOCK_OPERATION -> when (fieldKey) {
                "operation" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_OPERATION
                "block" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK
                "position" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_POSITION
                "from" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_FROM
                "to" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_BLOCK_TO
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.ENTITY_DELETE -> when (fieldKey) {
                "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TARGET
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.CONDITION -> when (fieldKey) {
                "inverted" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_INVERTED
                "kind" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_CONDITION_KIND
                "condition" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_CONDITION
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.DISK_CALL -> when (fieldKey) {
                "diskId" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_DISK
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.VARIABLE -> when (fieldKey) {
                "operation", "changeMode" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_OPERATION
                "name" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VARIABLE
                "type" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TYPE
                "value" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VALUE
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.TEMP_SET -> when (fieldKey) {
                "name" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VARIABLE
                "tempType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_TYPE
                "value", "location", "x", "y", "z", "yaw", "pitch", "item", "block", "entity",
                "entityId", "sound", "soundParameters", "volume", "effect", "level", "seconds" ->
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_VALUE
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.FOR_START -> when (fieldKey) {
                "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_WARNING_REPEAT_COUNT
                else -> undefinedWarningKey(node, fieldKey)
            }
            CommandType.MERGE,
            CommandType.FOR_END,
            CommandType.BREAK,
            CommandType.CONTINUE,
            -> undefinedWarningKey(node, fieldKey)
        }
    }

    /**
     * 検証側の内部フィールド名を、実際に表示するタブへ正規化します。
     *
     * 表示時間はInventory GUIとGesture GUIのどちらでも1項目です。Gesture GUIでは
     * そのタブの現在値欄だけを3項目表示へ拡張します。
     * また非表示のsubtitleはtextタブで編集するため、検証結果だけが存在しない
     * タブを指さないようにします。
     */
    fun visibleAttentionFieldKey(node: CommandNode, validationFieldKey: String): String? {
        val visibleKey = when (node.type) {
            CommandType.DISPLAY_TEXT -> when (validationFieldKey) {
                "fadeInSeconds", "staySeconds", "fadeOutSeconds" -> "staySeconds"
                "subtitle" -> "text"
                else -> validationFieldKey
            }
            CommandType.PLAY_SOUND -> when (validationFieldKey) {
                "volume", "pitch" -> "soundParameters"
                // 現行バリデーターはscopeの不正をsoundPositionとして報告します。
                "soundPosition" -> "soundScope"
                else -> validationFieldKey
            }
            CommandType.CONDITION -> when (validationFieldKey) {
                "sneaking", "variable", "operator", "value", "block", "item", "itemData" -> "condition"
                else -> validationFieldKey
            }
            CommandType.TEMP_SET -> when (validationFieldKey) {
                // 旧形式のx/y/z/yaw/pitchをLOCATIONの親タブへ投影し、
                // 新形式のENTITYも対象選択欄へ投影します。
                "x", "y", "z", "yaw", "pitch", "location" -> "location"
                "entityId", "entity" -> "entity"
                else -> validationFieldKey
            }
            else -> validationFieldKey
        }
        return gestureVisibleFields(node).firstOrNull { it.key == visibleKey }?.key
    }

    private fun undefinedWarningKey(node: CommandNode, fieldKey: String): Nothing =
        error("未定義の設定警告キーです: type=${node.type}, field=$fieldKey")

    fun descriptor(node: CommandNode, fieldKey: String): CommandSettingDescriptor {
        return when (node.type) {
        CommandType.TELEPORT -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "destination" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.DESTINATION)
            "destinationFacing" -> CommandSettingDescriptor(CommandSettingEditor.FACING, CommandSettingRole.DESTINATION_FACING)
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
            "slot" -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_EQUIPMENT_SLOT)
            "overwrite" -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_OVERWRITE)
            "tagOperation" -> CommandSettingDescriptor(CommandSettingEditor.ENTITY_TAG_OPERATION)
            else -> text()
        }
        CommandType.DISPLAY_TEXT -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "mode" -> CommandSettingDescriptor(CommandSettingEditor.DISPLAY_MODE)
            else -> text()
        }
        CommandType.WAIT -> text()
        CommandType.SUMMON_ENTITY -> when (fieldKey) {
            "summonPosition" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.SUMMON_POSITION)
            else -> text()
        }
        CommandType.PLAY_SOUND -> when (fieldKey) {
            "soundPosition" -> CommandSettingDescriptor(CommandSettingEditor.POSITION, CommandSettingRole.SOUND_POSITION)
            "soundScope" -> CommandSettingDescriptor(CommandSettingEditor.SOUND_SCOPE)
            else -> text()
        }
        CommandType.APPLY_EFFECT -> if (fieldKey == "target") {
            CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
        } else text()
        CommandType.CAMERA_SHAKE -> when (fieldKey) {
            "target" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.NODE_TARGET)
            "shakeType" -> CommandSettingDescriptor(CommandSettingEditor.CAMERA_SHAKE_TYPE)
            else -> text()
        }
        CommandType.CONDITION -> when (fieldKey) {
            "inverted" -> CommandSettingDescriptor(CommandSettingEditor.CONDITION_INVERSION)
            "kind" -> CommandSettingDescriptor(CommandSettingEditor.CONDITION_KIND)
            "condition" -> CommandSettingDescriptor(CommandSettingEditor.CONDITION_DETAIL)
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
            "type" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_TYPE)
            "operation" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_OPERATION)
            "changeMode" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_CHANGE_MODE)
            "value" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_VALUE)
            else -> text()
        }
        CommandType.TEMP_SET -> when (fieldKey) {
            "tempType" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_TYPE)
            "value" -> CommandSettingDescriptor(CommandSettingEditor.VARIABLE_VALUE)
            "entity" -> CommandSettingDescriptor(CommandSettingEditor.TARGET, CommandSettingRole.TEMPORARY_ENTITY)
            "location" -> CommandSettingDescriptor(CommandSettingEditor.LOCATION, CommandSettingRole.TEMPORARY_LOCATION)
            else -> text()
        }
        CommandType.FOR_START -> text()
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
     * 文字列値の保存と「ユーザーが設定した」状態をここで同時に扱います。空文字は
     * 入力値の解除を意味するため、明示設定フラグも解除して未設定へ戻します。
     */
    fun setParameter(node: CommandNode, key: String, value: String) {
        require(key.isNotBlank()) { "設定キーが空です" }
        if (node.type == CommandType.VARIABLE && key == "name") {
            require(CommandValueRules.isVariableName(value)) { "予約済みまたは不正な変数名です" }
        }
        if (node.type == CommandType.TEMP_SET && key == "name") {
            require(CommandValueRules.isVariableName(TemporaryTemplate.normalized(value))) { "予約済みまたは不正な変数名です" }
        }
        if (node.type == CommandType.TEMP_SET && key == "tempType") {
            val type = requireNotNull(TemporaryVariableType.parse(value)) { "不正な一時変数型です" }
            changeTemporaryType(node, type)
            return
        }
        node.params[key] = value
        if (value.isBlank()) {
            node.clearConfigured(key)
        } else {
            node.markConfigured(key)
        }
    }

    /** 複数の文字列パラメータを同じ明示設定契約で保存します。 */
    fun setParameters(node: CommandNode, values: Map<String, String>) {
        values.forEach { (key, value) -> setParameter(node, key, value) }
    }

    fun targetSpec(node: CommandNode, role: CommandSettingRole?): TargetSpec? = when (role) {
        CommandSettingRole.DESTINATION -> node.destinationTargetSpec
        CommandSettingRole.SECONDARY_TARGET -> node.secondaryTargetSpec
        CommandSettingRole.TEMPORARY_ENTITY -> node.temporaryEntityTargetSpec
        else -> node.targetSpec
    }

    /**
     * 一時変数の型変更を、古い型の入力値が新しい型へ幽霊表示されない単一操作にします。
     *
     * 一時値は型によって入力経路も実行時の保存形式も変わります。型だけを書き換えて
     * 旧payloadを残すと、いったんLOCATIONへ変えた後にNUMBERへ戻した際などに、見えない
     * 値が実行・出力側へ流れます。型変更時は共通の可変部分を全消去し、次の型の入力を
     * 必ずその型の設定画面から開始させます。
     */
    fun changeTemporaryType(node: CommandNode, type: TemporaryVariableType) {
        check(node.type == CommandType.TEMP_SET) { "一時変数型を持たないノードです: ${node.type}" }
        val current = TemporaryVariableType.parse(node.string("tempType", TemporaryVariableType.NUMBER.name))
        if (current != type) {
            setOf(
                "value", "x", "y", "z", "yaw", "pitch", "item", "itemData", "entityId",
                "sound", "volume", "effect", "level", "seconds",
            ).forEach(node.params::remove)
            node.temporaryEntityTargetSpec = null
            node.temporaryLocationPositionSpec = null
            node.temporaryLocationFacingSpec = null
            node.clearConfigured(
                "value", "x", "y", "z", "yaw", "pitch", "item", "itemData", "entityId",
                "sound", "volume", "effect", "level", "seconds", "entity", "location",
                "locationPosition", "locationFacing",
            )
        }
        node.params["tempType"] = type.name
        node.markConfigured("tempType")
    }

    fun setTargetSpec(node: CommandNode, role: CommandSettingRole?, spec: TargetSpec) {
        // TEMP_SET ENTITYは対象を複数保持できないTemporaryValueへ変換するため、
        // 共通TargetSpecの詳細条件はそのまま利用しつつ、解決数だけ1体へ正規化します。
        val normalizedSpec = if (role == CommandSettingRole.TEMPORARY_ENTITY) {
            spec.copy(limit = 1)
        } else spec
        when (role) {
            CommandSettingRole.DESTINATION -> {
                node.destinationTargetSpec = normalizedSpec
                node.destinationSpec = null
            }
            CommandSettingRole.SECONDARY_TARGET -> node.secondaryTargetSpec = normalizedSpec
            CommandSettingRole.TEMPORARY_ENTITY -> node.temporaryEntityTargetSpec = normalizedSpec
            else -> node.targetSpec = normalizedSpec
        }
        val configuredKey = configuredFieldKey("target", role)
        if (isTargetSpecConfigured(normalizedSpec)) {
            node.markConfigured(configuredKey)
        } else {
            // 方式だけ選択して必須の実値が未入力の場合は、選択履歴を
            // 「設定完了」として残しません。次の入力で完全なSpecになった時点で
            // 初めて明示設定へ昇格させます。
            node.clearConfigured(configuredKey)
        }
    }

    fun positionSpec(node: CommandNode, role: CommandSettingRole?): PositionSpec? = when (role) {
        CommandSettingRole.DESTINATION -> node.destinationSpec
        CommandSettingRole.SOUND_POSITION -> node.soundPositionSpec
        CommandSettingRole.SUMMON_POSITION -> node.summonPositionSpec
        CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec
        CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec
        CommandSettingRole.BLOCK_FROM -> node.blockFromSpec
        CommandSettingRole.BLOCK_TO -> node.blockToSpec
        CommandSettingRole.TEMPORARY_LOCATION_POSITION -> node.temporaryLocationPositionSpec
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
        CommandSettingRole.SOUND_POSITION -> node.soundPositionSpec?.kind
        CommandSettingRole.SUMMON_POSITION -> node.summonPositionSpec?.kind
        CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec?.kind
        CommandSettingRole.BLOCK_FROM -> node.blockFromSpec?.kind
        CommandSettingRole.BLOCK_TO -> node.blockToSpec?.kind
        CommandSettingRole.TEMPORARY_LOCATION_POSITION -> node.temporaryLocationPositionSpec?.kind
        else -> null
    }

    fun setPositionSpec(node: CommandNode, role: CommandSettingRole?, spec: PositionSpec) {
        when (role) {
            CommandSettingRole.DESTINATION -> {
                node.destinationSpec = spec
                node.destinationTargetSpec = null
            }
            CommandSettingRole.SOUND_POSITION -> node.soundPositionSpec = spec
            CommandSettingRole.SUMMON_POSITION -> node.summonPositionSpec = spec
            CommandSettingRole.CONDITION_POSITION -> node.conditionPositionSpec = spec
            CommandSettingRole.BLOCK_POSITION -> node.blockPositionSpec = spec
            CommandSettingRole.BLOCK_FROM -> node.blockFromSpec = spec
            CommandSettingRole.BLOCK_TO -> node.blockToSpec = spec
            CommandSettingRole.TEMPORARY_LOCATION_POSITION -> node.temporaryLocationPositionSpec = spec
            else -> error("${node.type} の位置設定役割が不正です: $role")
        }
        val configuredKey = configuredFieldKey(
            when (role) {
                CommandSettingRole.SOUND_POSITION -> "soundPosition"
                CommandSettingRole.SUMMON_POSITION -> "summonPosition"
                CommandSettingRole.TEMPORARY_LOCATION_POSITION -> "locationPosition"
                else -> "position"
            },
            role,
        )
        if (isPositionSpecConfigured(spec)) {
            node.markConfigured(configuredKey)
        } else {
            // COORDINATES/CAPTUREDの方式だけを選んだ段階では、座標や向きが
            // 未入力なので設定済みにしません。入力ダイアログの確定後だけ記録します。
            node.clearConfigured(configuredKey)
        }
    }

    fun facingSpec(node: CommandNode, role: CommandSettingRole? = null): FacingSpec? = when (role) {
        CommandSettingRole.DESTINATION_FACING -> node.destinationFacingSpec
        CommandSettingRole.TEMPORARY_LOCATION_FACING -> node.temporaryLocationFacingSpec
        else -> null
    }

    fun setFacingSpec(
        node: CommandNode,
        spec: FacingSpec,
        role: CommandSettingRole? = null,
    ) {
        check(
            role == CommandSettingRole.DESTINATION_FACING ||
                role == CommandSettingRole.TEMPORARY_LOCATION_FACING
        ) {
            "${node.type} の向き設定役割が不正です: $role"
        }
        if (role == CommandSettingRole.DESTINATION_FACING) {
            node.destinationFacingSpec = spec
            if (isFacingSpecConfigured(spec)) {
                node.markConfigured("destinationFacing")
            } else {
                node.clearConfigured("destinationFacing")
            }
        } else if (role == CommandSettingRole.TEMPORARY_LOCATION_FACING) {
            node.temporaryLocationFacingSpec = spec
            val configuredKey = configuredFieldKey("facing", role)
            if (isFacingSpecConfigured(spec)) {
                node.markConfigured(configuredKey)
            } else {
                node.clearConfigured(configuredKey)
            }
        }
    }

    /**
     * 設定画面で表示する値の状態を、文字列値と構造化値の両方から一元判定します。
     *
     * 既存JSONにはconfiguredFieldsがないため、まず構造化値／既定値との差を推定し、
     * 新しい画面から明示的に既定値を選んだ場合だけCommandNodeの明示記録を優先します。
     * ただし、必須の実値を持つ構造化値は明示フラグだけで完了扱いにしません。
     * 方式選択後に座標などが未入力の中間Specを保存できるため、実値の完全性を
     * 最初に確認してから、明示設定フラグを補助的に参照します。
     */
    fun isFieldConfigured(
        node: CommandNode,
        fieldKey: String,
        role: CommandSettingRole? = null,
    ): Boolean {
        // roleを省略したInventoryの一覧描画でも、コマンド型から保存先を復元します。
        // これをしないとBLOCK_OPERATIONのpositionが別の保存先として判定され、
        // InventoryとGestureで同じ値を表示していても選択状態だけがずれます。
        val effectiveRole = role ?: descriptor(node, fieldKey).role
        return when (fieldKey) {
            "target" -> isTargetSpecConfigured(targetSpec(node, effectiveRole))
            "entity" -> isTargetSpecConfigured(node.temporaryEntityTargetSpec)
            "location" -> isPositionSpecConfigured(node.temporaryLocationPositionSpec) &&
                isFacingSpecConfigured(node.temporaryLocationFacingSpec)
            "destination" -> isPositionSpecConfigured(node.destinationSpec) ||
                isTargetSpecConfigured(node.destinationTargetSpec)
            "other" -> isTargetSpecConfigured(node.secondaryTargetSpec)
            "position" -> when (effectiveRole) {
                CommandSettingRole.DESTINATION -> isPositionSpecConfigured(node.destinationSpec) ||
                    isTargetSpecConfigured(node.destinationTargetSpec)
                CommandSettingRole.CONDITION_POSITION -> isPositionSpecConfigured(node.conditionPositionSpec)
                CommandSettingRole.BLOCK_POSITION -> isPositionSpecConfigured(node.blockPositionSpec)
                CommandSettingRole.SOUND_POSITION -> isPositionSpecConfigured(node.soundPositionSpec)
                CommandSettingRole.SUMMON_POSITION -> isPositionSpecConfigured(node.summonPositionSpec)
                CommandSettingRole.TEMPORARY_LOCATION_POSITION -> isPositionSpecConfigured(node.temporaryLocationPositionSpec)
                else -> false
            }
            "from" -> isPositionSpecConfigured(node.blockFromSpec)
            "to" -> isPositionSpecConfigured(node.blockToSpec)
            "destinationFacing" -> isFacingSpecConfigured(node.destinationFacingSpec)
            "facing" -> if (effectiveRole == CommandSettingRole.DESTINATION_FACING) {
                isFacingSpecConfigured(node.destinationFacingSpec)
            } else if (effectiveRole == CommandSettingRole.TEMPORARY_LOCATION_FACING) {
                isFacingSpecConfigured(node.temporaryLocationFacingSpec)
            } else {
                false
            }
            "soundPosition" -> isPositionSpecConfigured(node.soundPositionSpec)
            "summonPosition" -> isPositionSpecConfigured(node.summonPositionSpec)
            // 音量とピッチは保存上は別パラメータですが、編集入口は一つです。
            // 旧データも正しく「設定済み」と表示できるよう、両方の実値を確認します。
            "soundParameters" -> listOf("volume", "pitch").any { key ->
                val value = node.params[key]
                value != null && value.isNotBlank() &&
                    (value != node.type.defaults[key] || node.isExplicitlyConfigured(key))
            }
            "item" -> node.string("item").isNotBlank() || node.string("itemData").isNotBlank()
            "diskId" -> node.string("diskId").isNotBlank() || node.snapshot != null
            "condition" -> conditionDetailConfigured(node)
            else -> {
                val value = node.params[fieldKey] ?: return false
                value.isNotBlank() &&
                    (value != node.type.defaults[fieldKey] ||
                        node.isExplicitlyConfigured(configuredFieldKey(fieldKey, effectiveRole)))
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
        val explicitlyConfigured = node.isExplicitlyConfigured(configuredFieldKey("target.$parameter", role))
        return when (parameter) {
            "kind" -> true
            "entityType" -> !spec.entityType.isNullOrBlank()
            "distance" -> spec.minimumDistance != null || spec.maximumDistance != null
            "range", "dx", "dy", "dz" -> when (parameter) {
                "dx" -> spec.dx != null
                "dy" -> spec.dy != null
                "dz" -> spec.dz != null
                else -> spec.dx != null || spec.dy != null || spec.dz != null
            }
            "limit" -> spec.limit != null
            // sortだけは入力欄ではなく択一操作です。既定値を明示選択した履歴も
            // 保持しますが、文字列・数値入力項目では空値を完了扱いにしません。
            "sort" -> spec.sort != TargetSort.NEAREST || explicitlyConfigured
            "gameMode" -> !spec.gameMode.isNullOrBlank()
            "tag" -> !spec.tag.isNullOrBlank()
            "name" -> !spec.name.isNullOrBlank()
            else -> false
        }
    }

    private fun conditionDetailConfigured(node: CommandNode): Boolean =
        isTargetSpecConfigured(node.targetSpec) || isPositionSpecConfigured(node.conditionPositionSpec) ||
            node.params.any { (key, value) ->
                key in setOf("sneaking", "variable", "operator", "value", "block", "item", "itemData") &&
                    value.isNotBlank() &&
                    (value != node.type.defaults[key] || node.isExplicitlyConfigured(key))
            }

    /** 固定エンティティだけは、種別を選んだだけでは実行対象が確定しません。 */
    private fun isTargetSpecConfigured(spec: TargetSpec?): Boolean = spec != null && when (spec.kind) {
        TargetKind.FIXED_ENTITY -> spec.fixedEntityId != null
        TargetKind.TEMPORARY -> !spec.tempName.isNullOrBlank() &&
            CommandValueRules.isVariableName(TemporaryTemplate.normalized(spec.tempName))
        else -> true
    }

    /** 座標・捕捉方式は必須値が揃った場合だけ設定完了とします。 */
    private fun isPositionSpecConfigured(spec: PositionSpec?): Boolean = spec != null && when (spec.kind) {
        PositionKind.COORDINATES -> listOf(spec.x, spec.y, spec.z).all { it?.isFinite() == true }
        PositionKind.CAPTURED -> listOf(spec.x, spec.y, spec.z).all { it?.isFinite() == true } &&
            listOf(spec.yaw, spec.pitch).all { it?.isFinite() == true }
        PositionKind.TEMPORARY -> !spec.tempName.isNullOrBlank() &&
            CommandValueRules.isVariableName(TemporaryTemplate.normalized(spec.tempName))
        else -> true
    }

    /** 座標・数値回転方式は入力値が揃った場合だけ設定完了とします。 */
    private fun isFacingSpecConfigured(spec: FacingSpec?): Boolean = spec != null && when (spec.kind) {
        FacingKind.COORDINATES -> listOf(spec.x, spec.y, spec.z).all { it?.isFinite() == true }
        FacingKind.CAPTURED, FacingKind.ROTATION -> listOf(spec.yaw, spec.pitch).all { it?.isFinite() == true }
        FacingKind.TEMPORARY -> !spec.tempName.isNullOrBlank() &&
            CommandValueRules.isVariableName(TemporaryTemplate.normalized(spec.tempName))
        else -> true
    }

    fun allowedVariableOperations(type: VariableType): List<VariableOperation> =
        VariableOperation.entries

    /**
     * 対象種別に対して詳細条件が意味を持つかを判定します。
     * 実行側の解決（matches）に合わせ、プレイヤー種別へentityType、
     * エンティティの種類へgameModeを指定しても解決しないため、GUIでは提示しません。
     */
    fun targetFilterApplies(kind: TargetKind?, parameter: String): Boolean = when (parameter) {
        "kind" -> kind != null
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
        CommandSettingRole.CONDITION_POSITION -> "condition.position"
        CommandSettingRole.SOUND_POSITION -> "soundPosition"
        CommandSettingRole.SUMMON_POSITION -> "summonPosition"
        CommandSettingRole.TEMPORARY_ENTITY -> "entity"
        CommandSettingRole.TEMPORARY_LOCATION -> "location"
        CommandSettingRole.TEMPORARY_LOCATION_POSITION -> "locationPosition"
        CommandSettingRole.TEMPORARY_LOCATION_FACING -> "locationFacing"
        CommandSettingRole.DESTINATION -> if (fieldKey.startsWith("target.")) {
            "destination.$fieldKey"
        } else "destination"
        CommandSettingRole.SECONDARY_TARGET -> if (fieldKey.startsWith("target.")) {
            "other.$fieldKey"
        } else "other"
        else -> fieldKey
    }

    private val FILTERABLE_TARGET_KINDS = PLAYER_KINDS + ENTITY_KINDS
}
