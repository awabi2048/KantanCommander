package me.awabi2048.kantancommander.model

import org.bukkit.Material
import java.util.UUID

data class DiskScript(
    val id: UUID = UUID.randomUUID(),
    var name: String,
    val owner: UUID,
    val createdAt: Long = System.currentTimeMillis(),
    var trigger: TriggerMode = TriggerMode.REDSTONE_RISING,
    val commands: MutableList<ScriptCommand> = mutableListOf()
)

data class ScriptCommand(
    val type: CommandType,
    val params: MutableMap<String, String> = linkedMapOf()
) {
    fun string(key: String, default: String = ""): String = params[key]?.takeIf { it.isNotBlank() } ?: default
    fun int(key: String, default: Int = 0): Int = params[key]?.toIntOrNull() ?: default
    fun double(key: String, default: Double = 0.0): Double = params[key]?.toDoubleOrNull() ?: default

    fun summary(): String = when (type) {
        CommandType.SOUND -> string("sound", "minecraft:block.note_block.pling")
        CommandType.MESSAGE -> string("text", "")
        CommandType.PARTICLE -> string("particle", "minecraft:happy_villager")
        CommandType.WAIT -> "${int("ticks", 20)} ticks"
        CommandType.TITLE -> string("title", "")
        CommandType.ACTIONBAR -> string("text", "")
        CommandType.EFFECT -> string("effect", "SPEED")
    }.ifBlank { "-" }
}

enum class TriggerMode(val key: String) {
    REDSTONE_RISING("trigger.redstone_rising"),
    REDSTONE_EDGE("trigger.redstone_edge");

    fun next(): TriggerMode = if (this == REDSTONE_RISING) REDSTONE_EDGE else REDSTONE_RISING
}

enum class CommandType(
    val key: String,
    val icon: Material,
    val params: List<CommandParam>
) {
    SOUND("command.sound", Material.NOTE_BLOCK, listOf(
        CommandParam.Text("sound", "param.sound", "minecraft:block.note_block.pling"),
        CommandParam.Number("volume", "param.volume", "1.0"),
        CommandParam.Number("pitch", "param.pitch", "1.0"),
        CommandParam.Choice("category", "param.category", "MASTER", listOf("MASTER", "PLAYERS", "BLOCKS", "AMBIENT"))
    )),
    MESSAGE("command.message", Material.PAPER, listOf(
        CommandParam.Text("text", "param.text", "Hello"),
        CommandParam.Choice("target", "param.target", "nearby", listOf("nearby", "self"))
    )),
    PARTICLE("command.particle", Material.FIREWORK_STAR, listOf(
        CommandParam.Text("particle", "param.particle", "minecraft:happy_villager"),
        CommandParam.Number("count", "param.count", "8"),
        CommandParam.Number("speed", "param.speed", "0.0"),
        CommandParam.Number("offsetX", "param.offset_x", "0.5"),
        CommandParam.Number("offsetY", "param.offset_y", "0.5"),
        CommandParam.Number("offsetZ", "param.offset_z", "0.5")
    )),
    WAIT("command.wait", Material.CLOCK, listOf(
        CommandParam.Number("ticks", "param.ticks", "20")
    )),
    TITLE("command.title", Material.NAME_TAG, listOf(
        CommandParam.Text("title", "param.title", "Title"),
        CommandParam.Text("subtitle", "param.subtitle", ""),
        CommandParam.Number("fadeIn", "param.fade_in", "10"),
        CommandParam.Number("stay", "param.stay", "60"),
        CommandParam.Number("fadeOut", "param.fade_out", "10")
    )),
    ACTIONBAR("command.actionbar", Material.WRITABLE_BOOK, listOf(
        CommandParam.Text("text", "param.text", "Hello")
    )),
    EFFECT("command.effect", Material.POTION, listOf(
        CommandParam.Choice("effect", "param.effect", "SPEED", listOf("SPEED", "HASTE", "JUMP_BOOST", "REGENERATION", "RESISTANCE", "GLOWING")),
        CommandParam.Number("duration", "param.duration", "100"),
        CommandParam.Number("amplifier", "param.amplifier", "0")
    ));

    fun newCommand(): ScriptCommand = ScriptCommand(this, params.associate { it.id to it.defaultValue }.toMutableMap())
}

sealed class CommandParam(val id: String, val key: String, val defaultValue: String) {
    class Text(id: String, key: String, defaultValue: String) : CommandParam(id, key, defaultValue)
    class Number(id: String, key: String, defaultValue: String) : CommandParam(id, key, defaultValue)
    class Choice(id: String, key: String, defaultValue: String, val options: List<String>) : CommandParam(id, key, defaultValue)
}

data class DiskPlacement(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val scriptId: UUID,
    var displayId: UUID?
) {
    val key: String get() = "$world,$x,$y,$z"
}
