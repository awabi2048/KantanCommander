package me.awabi2048.kantancommander.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.model.CommandType
import me.awabi2048.kantancommander.data.model.DiskScript
import me.awabi2048.kantancommander.data.model.ScriptCommand
import me.awabi2048.kantancommander.data.model.TriggerType
import java.io.File
import java.util.UUID

object DataManager {
    private lateinit var scriptsDir: File
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(UUID::class.java, UuidAdapter())
        .create()

    private data class ScriptData(
        val uuid: String,
        val name: String,
        val creator: String,
        val createdAt: Long,
        val commands: List<CommandData>,
        val triggerType: String
    )

    private data class CommandData(
        val type: String,
        val params: Map<String, String>
    )

    fun init(plugin: KantanCommanderPlugin) {
        scriptsDir = File(plugin.dataFolder, "scripts")
        scriptsDir.mkdirs()
    }

    fun load(uuid: UUID): DiskScript? {
        val file = scriptFile(uuid)
        if (!file.exists()) return null
        return try {
            val data = gson.fromJson(file.readText(), ScriptData::class.java)
            data.toDomain()
        } catch (e: Exception) {
            KantanCommanderPlugin.instance.logger.warning("Failed to load script $uuid: ${e.message}")
            file.renameTo(File(file.parentFile, "${uuid}.corrupted"))
            null
        }
    }

    fun save(script: DiskScript) {
        val data = ScriptData(
            uuid = script.uuid.toString(),
            name = script.name,
            creator = script.creator.toString(),
            createdAt = script.createdAt,
            commands = script.commands.map { cmd ->
                CommandData(type = cmd.type.id, params = cmd.params.toMap())
            },
            triggerType = script.triggerType.id
        )
        scriptFile(script.uuid).writeText(gson.toJson(data))
    }

    fun delete(uuid: UUID) {
        scriptFile(uuid).delete()
    }

    fun listAll(): List<DiskScript> {
        val files = scriptsDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
        return files.mapNotNull { file ->
            try {
                val uuid = UUID.fromString(file.nameWithoutExtension)
                load(uuid)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun listOwned(player: UUID): List<DiskScript> =
        listAll().filter { it.creator == player }

    private fun scriptFile(uuid: UUID): File =
        File(scriptsDir, "${uuid}.json")

    private fun ScriptData.toDomain(): DiskScript {
        val uuid = UUID.fromString(this.uuid)
        val creator = UUID.fromString(this.creator)
        val commands = this.commands.mapNotNull { cmd ->
            // 未知タイプは安全側で破棄し、削除済みの実行系命令が別名で復活しないようにする。
            val type = CommandType.entries.firstOrNull { it.id == cmd.type } ?: return@mapNotNull null
            ScriptCommand(type, cmd.params.toMutableMap())
        }.toMutableList()
        return DiskScript(
            uuid = uuid,
            name = name,
            creator = creator,
            createdAt = createdAt,
            commands = commands,
            triggerType = TriggerType.fromId(triggerType)
        )
    }
}

internal class UuidAdapter : com.google.gson.JsonSerializer<UUID>, com.google.gson.JsonDeserializer<UUID> {
    override fun serialize(src: UUID, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): com.google.gson.JsonElement =
        com.google.gson.JsonPrimitive(src.toString())

    override fun deserialize(json: com.google.gson.JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): UUID? {
        return try {
            UUID.fromString(json.asString)
        } catch (_: Exception) {
            null
        }
    }
}
