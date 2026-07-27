package me.awabi2048.kantancommander.model

import org.bukkit.Material
import java.util.UUID

const val STRUCTURED_FORMAT_VERSION = 5
const val TIMER_UNIT_TICKS = 10
const val MIN_TIMER_UNITS = 1
const val MAX_TIMER_UNITS = 86_400

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

enum class ActivationMode(val key: String) {
    NEEDS_REDSTONE("activation.needs_redstone"),
    ALWAYS_ACTIVE("activation.always_active");

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
                contextOverride = node.contextOverride?.copy(),
                targetSpec = node.targetSpec?.copy(),
                destinationSpec = node.destinationSpec?.copy(),
                destinationTargetSpec = node.destinationTargetSpec?.copy(),
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
    var next: UUID? = null,
    var trueNext: UUID? = null,
    var falseNext: UUID? = null,
    var pairedNodeId: UUID? = null,
    var targetSpec: TargetSpec? = null,
    var destinationSpec: PositionSpec? = null,
    var destinationTargetSpec: TargetSpec? = null,
    var contextOverride: ExecutionContextSpec? = null,
    var snapshot: CommandGraph? = null,
) {
    fun string(key: String, default: String = "") = params[key]?.takeIf(String::isNotBlank) ?: default
    fun int(key: String, default: Int = 0) = params[key]?.toIntOrNull() ?: default
    fun double(key: String, default: Double = 0.0) = params[key]?.toDoubleOrNull() ?: default
    fun boolean(key: String, default: Boolean = false) = params[key]?.toBooleanStrictOrNull() ?: default
    fun summary(): String = type.summary(this)
}

enum class TargetKind {
    EXECUTOR, ACTIVATOR, INHERITED_TARGET, NEAREST_PLAYER, NEARBY_PLAYERS,
    RANDOM_PLAYER, NEAREST_ENTITY, NEARBY_ENTITIES, FIXED_ENTITY,
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
    val excludeExecutor: Boolean = false,
    val excludeActivator: Boolean = false,
    val fixedEntityId: UUID? = null,
)

enum class PositionKind { CAPTURED, DISK, EXECUTOR, TARGET, MYWORLD_SPAWN, COORDINATES, VARIABLE }
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

enum class ConditionKind(val key: String) {
    TARGET_EXISTS("condition.target_exists"),
    ENTITY_STATE("condition.entity_state"),
    VARIABLE_STATE("condition.variable_state"),
    BLOCK_STATE("condition.block_state"),
    ITEM_POSSESSION("condition.item_possession"),
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
    val key: String,
    val icon: Material,
    val defaults: Map<String, String>,
) {
    TELEPORT("command.teleport", Material.ENDER_PEARL, mapOf("target" to "@s", "destination" to "~ ~ ~")),
    GIVE_ITEM("command.give_item", Material.CHEST, mapOf("target" to "@s", "item" to "minecraft:stone", "count" to "1")),
    ENTITY_ACTION("command.entity_action", Material.SADDLE, mapOf("target" to "@s", "action" to "ride", "other" to "")),
    DISPLAY_TEXT("command.display_text", Material.WRITABLE_BOOK, mapOf(
        "target" to "@s", "mode" to "tellraw", "text" to "", "fadeIn" to "10", "stay" to "60", "fadeOut" to "10"
    )),
    WAIT("command.wait", Material.CLOCK, mapOf("ticks" to "20")),
    CONDITION("command.condition", Material.COMPARATOR, mapOf(
        "kind" to ConditionKind.TARGET_EXISTS.name,
        "inverted" to "false",
        "subject" to "@s",
        "state" to "sneaking",
        "variable" to "",
        "variableScope" to VariableScope.TEMPORARY.name,
        "operator" to ">=",
        "value" to "0",
        "position" to "~ ~ ~",
        "block" to "minecraft:air",
        "item" to "minecraft:stone",
        "count" to "1",
    )),
    CONTEXT("command.context", Material.RECOVERY_COMPASS, mapOf(
        "executor" to "", "target" to "", "position" to "", "facing" to ""
    )),
    DISK_CALL("command.disk_call", Material.MUSIC_DISC_13, mapOf("diskId" to "")),
    VARIABLE("command.variable", Material.REDSTONE, mapOf(
        "name" to "",
        "scope" to VariableScope.TEMPORARY.name,
        "type" to VariableType.BOOLEAN.name,
        "operation" to VariableOperation.SET.name,
        "value" to "false",
    )),
    MERGE("command.merge", Material.HOPPER, emptyMap()),
    FOR_START("command.for_start", Material.REPEATER, mapOf(
        "startSource" to "FIXED",
        "startValue" to "0",
        "endSource" to "FIXED",
        "endValue" to "0",
        "stepSource" to "FIXED",
        "stepValue" to "1",
    )),
    FOR_END("command.for_end", Material.COMPARATOR, emptyMap()),
    BREAK("command.break", Material.BARRIER, emptyMap()),
    CONTINUE("command.continue", Material.ARROW, emptyMap());

    fun newNode() = CommandNode(type = this, params = defaults.toMutableMap())

    fun summary(node: CommandNode): String = when (this) {
        TELEPORT -> "${node.string("target", "@s")} → ${node.string("destination", "~ ~ ~")}"
        GIVE_ITEM -> "${node.string("item", "minecraft:stone")} ×${node.int("count", 1)}"
        ENTITY_ACTION -> node.string("action", "ride")
        DISPLAY_TEXT -> node.string("text").ifBlank { "-" }
        WAIT -> "${node.int("ticks", 20)} ticks"
        CONDITION -> node.string("kind", ConditionKind.TARGET_EXISTS.name)
        CONTEXT -> "execute context"
        DISK_CALL -> node.string("diskId").ifBlank { "-" }
        VARIABLE -> node.string("name").ifBlank { "-" }
        MERGE -> "merge"
        FOR_START -> "${node.string("startValue", "0")}..${node.string("endValue", "0")} step ${node.string("stepValue", "1")}"
        FOR_END -> "for end"
        BREAK -> "break"
        CONTINUE -> "continue"
    }
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
