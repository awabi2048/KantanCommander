package me.awabi2048.kantancommander.model

import org.bukkit.Material
import java.util.UUID

const val STRUCTURED_FORMAT_VERSION = 2
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
    var contextOverride: ExecutionContextSpec? = null,
    var snapshot: CommandGraph? = null,
) {
    fun string(key: String, default: String = "") = params[key]?.takeIf(String::isNotBlank) ?: default
    fun int(key: String, default: Int = 0) = params[key]?.toIntOrNull() ?: default
    fun double(key: String, default: Double = 0.0) = params[key]?.toDoubleOrNull() ?: default
    fun summary(): String = type.summary(this)
}

data class ExecutionContextSpec(
    val executor: String? = null,
    val target: String? = null,
    val position: String? = null,
    val facing: String? = null,
)

enum class ConditionKind(val key: String) {
    TARGET_EXISTS("condition.target_exists"),
    ENTITY_STATE("condition.entity_state"),
    SCORE_COMPARE("condition.score_compare"),
    BLOCK_STATE("condition.block_state"),
    ITEM_POSSESSION("condition.item_possession"),
    COMMAND_RESULT("condition.command_result"),
}

enum class DiskCallMode {
    LIVE_REFERENCE,
    SNAPSHOT,
}

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
        "subject" to "@s",
        "state" to "alive",
        "objective" to "",
        "operator" to ">=",
        "value" to "0",
        "position" to "~ ~ ~",
        "block" to "minecraft:air",
        "item" to "minecraft:stone",
        "count" to "1",
        "nodeId" to "",
        "expected" to "success",
    )),
    CONTEXT("command.context", Material.RECOVERY_COMPASS, mapOf(
        "executor" to "", "target" to "", "position" to "", "facing" to ""
    )),
    DISK_CALL("command.disk_call", Material.MUSIC_DISC_13, mapOf("mode" to DiskCallMode.LIVE_REFERENCE.name, "diskId" to "")),
    MERGE("command.merge", Material.HOPPER, emptyMap());

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
        MERGE -> "merge"
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
