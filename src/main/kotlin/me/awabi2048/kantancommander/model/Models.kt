package me.awabi2048.kantancommander.model

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import org.bukkit.Material
import java.util.UUID

const val STRUCTURED_FORMAT_VERSION = 6
const val TIMER_UNIT_TICKS = 10
const val MIN_TIMER_UNITS = 1
const val MAX_TIMER_UNITS = 86_400
const val MAX_BLOCK_OPERATION_VOLUME = 32_768L

data class DiskScript(
    val formatVersion: Int = STRUCTURED_FORMAT_VERSION,
    val id: UUID = UUID.randomUUID(),
    var name: String,
    val owner: UUID,
    val createdAt: Long = System.currentTimeMillis(),
    var listed: Boolean = true,
    var activation: ActivationMode = ActivationMode.NEEDS_REDSTONE,
    var timer: TimerSetting = TimerSetting(),
    var graph: CommandGraph = CommandGraph.empty(),
)

data class TimerSetting(
    var enabled: Boolean = false,
    var intervalUnits: Int = MIN_TIMER_UNITS,
) {
    fun normalized() = copy(intervalUnits = intervalUnits.coerceIn(MIN_TIMER_UNITS, MAX_TIMER_UNITS))
    val intervalTicks: Long get() = intervalUnits.coerceIn(MIN_TIMER_UNITS, MAX_TIMER_UNITS) * TIMER_UNIT_TICKS.toLong()
}

enum class ActivationMode(val key: LocalizationKey<String>) {
    NEEDS_REDSTONE(KcKeys.KANTAN_COMMANDER_CLEAN_ACTIVATION_NEEDS_REDSTONE),
    ALWAYS_ACTIVE(KcKeys.KANTAN_COMMANDER_CLEAN_ACTIVATION_ALWAYS_ACTIVE);

    fun toggled(timerEnabled: Boolean): ActivationMode =
        if (!timerEnabled) NEEDS_REDSTONE else if (this == NEEDS_REDSTONE) ALWAYS_ACTIVE else NEEDS_REDSTONE
}

data class CommandGraph(
    var entryNodeId: UUID? = null,
    val nodes: LinkedHashMap<UUID, CommandNode> = linkedMapOf(),
) {
    companion object {
        fun empty() = CommandGraph()
    }

    fun deepCopy(): CommandGraph {
        val copied = linkedMapOf<UUID, CommandNode>()
        nodes.forEach { (id, node) ->
            copied[id] = node.copy(
                params = node.params.toMutableMap(),
                configuredFields = node.configuredFields?.toMutableSet(),
                contextOverride = node.contextOverride?.copy(),
                targetSpec = node.targetSpec?.copy(),
                secondaryTargetSpec = node.secondaryTargetSpec?.copy(),
                destinationSpec = node.destinationSpec?.copy(),
                destinationTargetSpec = node.destinationTargetSpec?.copy(),
                conditionPositionSpec = node.conditionPositionSpec?.copy(),
                blockPositionSpec = node.blockPositionSpec?.copy(),
                blockFromSpec = node.blockFromSpec?.copy(),
                blockToSpec = node.blockToSpec?.copy(),
                snapshot = node.snapshot?.deepCopy(),
            )
        }
        return CommandGraph(entryNodeId, copied)
    }
}

data class CommandNode(
    val id: UUID = UUID.randomUUID(),
    val type: CommandType,
    val params: MutableMap<String, String> = linkedMapOf(),
    /**
     * ユーザーが明示的に操作した設定項目です。
     *
     * paramsの値がコマンド既定値と同じでも「操作して設定した」状態を失わない
     * よう、値そのものとは別に保持します。旧JSONにはこの項目が存在しないため
     * nullableにし、読み込み直後はCommandSettingsModelが値から安全に推定します。
     */
    var configuredFields: MutableSet<String>? = null,
    var next: UUID? = null,
    var trueNext: UUID? = null,
    var falseNext: UUID? = null,
    var pairedNodeId: UUID? = null,
    var targetSpec: TargetSpec? = null,
    var secondaryTargetSpec: TargetSpec? = null,
    var destinationSpec: PositionSpec? = null,
    var destinationTargetSpec: TargetSpec? = null,
    var conditionPositionSpec: PositionSpec? = null,
    /** ブロック操作の単一配置位置（setblock相当）。 */
    var blockPositionSpec: PositionSpec? = null,
    /** ブロック操作の範囲始点・終点（fill相当）。 */
    var blockFromSpec: PositionSpec? = null,
    var blockToSpec: PositionSpec? = null,
    var contextOverride: ExecutionContextSpec? = null,
    /** 欠損した旧JSONはBASEとし、既存のCONTEXT継承順序を変えません。 */
    var contextSource: ContextSource? = ContextSource.BASE,
    var snapshot: CommandGraph? = null,
) {
    fun string(key: String, default: String = "") = params[key]?.takeIf(String::isNotBlank) ?: default
    fun int(key: String, default: Int = 0) = params[key]?.toIntOrNull() ?: default
    fun double(key: String, default: Double = 0.0) = params[key]?.toDoubleOrNull() ?: default
    fun boolean(key: String, default: Boolean = false) = params[key]?.toBooleanStrictOrNull() ?: default

    fun isExplicitlyConfigured(key: String): Boolean = configuredFields?.contains(key) == true

    fun markConfigured(vararg keys: String) {
        if (keys.isEmpty()) return
        val fields = configuredFields ?: linkedSetOf<String>().also { configuredFields = it }
        fields += keys
    }
}

enum class ContextSource { BASE, PREVIOUS }

/** ブロック操作が実行時に採用する配置方式です。保存値は明示的な小文字文字列にします。 */
enum class BlockOperationMode(val value: String) {
    SETBLOCK("setblock"),
    FILL("fill"),
    ;

    companion object {
        fun from(value: String?): BlockOperationMode? = entries.firstOrNull { it.value == value }
    }
}

val CommandNode.effectiveContextSource: ContextSource
    get() = contextSource ?: ContextSource.BASE

enum class TargetKind {
    INHERITED_TARGET, NEAREST_PLAYER, NEARBY_PLAYERS,
    ALL_PLAYERS, RANDOM_PLAYER, NEAREST_ENTITY, NEARBY_ENTITIES, FIXED_ENTITY,
}

enum class TargetSort { NEAREST, FURTHEST, RANDOM }

data class TargetSpec(
    val kind: TargetKind,
    val entityType: String? = null,
    val minimumDistance: Double? = null,
    val maximumDistance: Double? = null,
    val limit: Int? = null,
    val sort: TargetSort = TargetSort.NEAREST,
    val gameMode: String? = null,
    val tag: String? = null,
    val name: String? = null,
    val fixedEntityId: UUID? = null,
)

enum class PositionKind {
    CAPTURED, DISK, EXECUTOR, TARGET, MYWORLD_SPAWN, COORDINATES,
    TEMPORARY_VARIABLE, WORLD_VARIABLE,
}
data class PositionSpec(
    val kind: PositionKind,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val yaw: Float? = null,
    val pitch: Float? = null,
    val variable: String? = null,
)

enum class FacingKind { INHERITED, CAPTURED, EXECUTOR, TARGET, COORDINATES, MYWORLD_SPAWN, ROTATION }
data class FacingSpec(
    val kind: FacingKind,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val yaw: Float? = null,
    val pitch: Float? = null,
)

data class ExecutionContextSpec(
    val executor: TargetSpec? = null,
    val target: TargetSpec? = null,
    val position: PositionSpec? = null,
    val facing: FacingSpec? = null,
)

enum class ConditionKind(val key: LocalizationKey<String>) {
    TARGET_EXISTS(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_TARGET_EXISTS),
    ENTITY_STATE(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_ENTITY_STATE),
    VARIABLE_STATE(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_VARIABLE_STATE),
    BLOCK_STATE(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_BLOCK_STATE),
    ITEM_POSSESSION(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_ITEM_POSSESSION),
}

enum class VariableType { BOOLEAN, INTEGER, DECIMAL, TEXT, POSITION, ENTITY }
enum class VariableScope { TEMPORARY, WORLD }
enum class VariableOperation { SET, ADD, SUBTRACT, TOGGLE, STORE_POSITION, STORE_TARGET, CLEAR }

data class WorldVariableValue(
    val type: VariableType,
    val booleanValue: Boolean? = null,
    val integerValue: Long? = null,
    val decimalValue: Double? = null,
    val textValue: String? = null,
    val position: SavedPosition? = null,
    val entityId: UUID? = null,
)

data class SavedPosition(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
)

enum class CommandType(
    val key: LocalizationKey<String>,
    val descriptionKey: LocalizationKey<List<String>>,
    val icon: Material,
    val defaults: Map<String, String>,
) {
    TELEPORT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_TELEPORT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_TELEPORT_DESCRIPTION, Material.ENDER_PEARL, emptyMap()),
    GIVE_ITEM(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_GIVE_ITEM, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_GIVE_ITEM_DESCRIPTION, Material.CHEST, mapOf("count" to "1")),
    ENTITY_ACTION(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_ACTION, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_ACTION_DESCRIPTION, Material.SADDLE, mapOf("action" to "ride")),
    DISPLAY_TEXT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISPLAY_TEXT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISPLAY_TEXT_DESCRIPTION, Material.WRITABLE_BOOK, mapOf(
        "mode" to "tellraw", "text" to "", "fadeIn" to "10", "stay" to "60", "fadeOut" to "10"
    )),
    WAIT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_WAIT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_WAIT_DESCRIPTION, Material.CLOCK, mapOf("ticks" to "20")),
    SUMMON_ENTITY(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_SUMMON_ENTITY, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_SUMMON_ENTITY_DESCRIPTION, Material.ZOMBIE_SPAWN_EGG, mapOf(
        "entity" to "", "tags" to ""
    )),
    PLAY_SOUND(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PLAY_SOUND, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PLAY_SOUND_DESCRIPTION, Material.NOTE_BLOCK, mapOf(
        "sound" to "", "volume" to "1.0", "pitch" to "1.0"
    )),
    APPLY_EFFECT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_APPLY_EFFECT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_APPLY_EFFECT_DESCRIPTION, Material.POTION, mapOf(
        "effect" to "", "level" to "1", "seconds" to "30"
    )),
    CAMERA_SHAKE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CAMERA_SHAKE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CAMERA_SHAKE_DESCRIPTION, Material.SPYGLASS, mapOf(
        "intensity" to "1.0", "seconds" to "5.0", "shakeType" to "positional"
    )),
    EQUIP_ITEM(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_EQUIP_ITEM, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_EQUIP_ITEM_DESCRIPTION, Material.IRON_CHESTPLATE, mapOf(
        "slot" to "HAND", "item" to ""
    )),
    BLOCK_OPERATION(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BLOCK_OPERATION, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BLOCK_OPERATION_DESCRIPTION, Material.BRICKS, mapOf(
        "operation" to "setblock", "block" to ""
    )),
    ENTITY_DELETE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_DELETE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_DELETE_DESCRIPTION, Material.BARRIER, emptyMap()),
    CONDITION(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONDITION, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONDITION_DESCRIPTION, Material.COMPARATOR, mapOf(
        "kind" to ConditionKind.TARGET_EXISTS.name,
        "inverted" to "false",
        "state" to "sneaking",
        "variable" to "",
        "variableScope" to VariableScope.TEMPORARY.name,
        "operator" to ">=",
        "value" to "0",
        "block" to "minecraft:air",
        "count" to "1",
    )),
    CONTEXT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTEXT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTEXT_DESCRIPTION, Material.RECOVERY_COMPASS, mapOf(
        "executor" to "", "target" to "", "position" to "", "facing" to ""
    )),
    DISK_CALL(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISK_CALL, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISK_CALL_DESCRIPTION, Material.MUSIC_DISC_13, mapOf("diskId" to "")),
    VARIABLE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_VARIABLE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_VARIABLE_DESCRIPTION, Material.REDSTONE, mapOf(
        "name" to "",
        "scope" to VariableScope.TEMPORARY.name,
        "type" to VariableType.BOOLEAN.name,
        "operation" to VariableOperation.SET.name,
        "value" to "false",
    )),
    MERGE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_MERGE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_MERGE_DESCRIPTION, Material.HOPPER, emptyMap()),
    FOR_START(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_START, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_START_DESCRIPTION, Material.REPEATER, mapOf(
        "startSource" to "FIXED",
        "startValue" to "0",
        "endSource" to "FIXED",
        "endValue" to "0",
        "stepSource" to "FIXED",
        "stepValue" to "1",
        "inclusiveEnd" to "true",
    )),
    FOR_END(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_END, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_END_DESCRIPTION, Material.COMPARATOR, emptyMap()),
    BREAK(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BREAK, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BREAK_DESCRIPTION, Material.BARRIER, emptyMap()),
    CONTINUE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTINUE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTINUE_DESCRIPTION, Material.ARROW, emptyMap());

    fun newNode() = CommandNode(type = this, params = defaults.toMutableMap())
}

data class DiskPlacement(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val scriptId: UUID,
    val facing: String,
    var displayId: UUID?,
) {
    val key: String get() = "$world,$x,$y,$z"
}
