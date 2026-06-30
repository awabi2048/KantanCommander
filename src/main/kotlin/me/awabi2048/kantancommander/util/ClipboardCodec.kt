package me.awabi2048.kantancommander.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.awabi2048.kantancommander.data.model.CommandType
import me.awabi2048.kantancommander.data.model.ScriptCommand

object ClipboardCodec {
    private const val PREFIX = "KC:v1:"
    private val gson: Gson = GsonBuilder().create()

    private data class ClipData(
        val version: Int = 1,
        val commands: List<ClipCommand>
    )

    private data class ClipCommand(
        val type: String,
        val params: Map<String, String>
    )

    fun encode(commands: List<ScriptCommand>): String {
        val clipCommands = commands.map { cmd ->
            ClipCommand(type = cmd.type.id, params = cmd.params.toMap())
        }
        val json = gson.toJson(ClipData(version = 1, commands = clipCommands))
        return PREFIX + json
    }

    fun decode(data: String): List<ScriptCommand>? {
        if (!isValid(data)) return null
        val json = data.removePrefix(PREFIX)
        return try {
            val clipData = gson.fromJson(json, ClipData::class.java)
            clipData.commands.map { cmd ->
                val type = CommandType.entries.firstOrNull { it.id == cmd.type } ?: return@map null
                ScriptCommand(type, cmd.params.toMutableMap())
            }.filterNotNull()
        } catch (_: Exception) {
            null
        }
    }

    fun isValid(data: String): Boolean =
        data.startsWith(PREFIX)
}
