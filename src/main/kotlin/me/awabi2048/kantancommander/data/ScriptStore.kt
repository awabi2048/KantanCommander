package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ScriptCommand
import me.awabi2048.kantancommander.model.TriggerMode
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Level
import java.util.logging.Logger
import java.util.UUID

class ScriptStore(private val dir: File, private val logger: Logger) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    init {
        dir.mkdirs()
    }

    fun create(owner: UUID, name: String): DiskScript {
        val script = DiskScript(name = name, owner = owner)
        save(script)
        return script
    }

    fun load(id: UUID): DiskScript? {
        val file = file(id)
        if (!file.isFile) return null
        return read(file)
    }

    fun save(script: DiskScript) {
        file(script.id).writeText(gson.toJson(ScriptDto.from(script)), Charsets.UTF_8)
    }

    fun delete(id: UUID) {
        file(id).delete()
    }

    fun listAll(): List<DiskScript> =
        dir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull { read(it) }
            ?.sortedBy { it.createdAt }
            ?: emptyList()

    fun listOwned(owner: UUID): List<DiskScript> = listAll().filter { it.owner == owner }

    private fun file(id: UUID): File = dir.resolve("$id.json")

    private fun read(file: File): DiskScript? {
        return try {
            val dto = gson.fromJson(file.readText(Charsets.UTF_8), ScriptDto::class.java)
                ?: error("JSON root is null")
            dto.toModel()
        } catch (error: Exception) {
            quarantine(file, error)
            null
        }
    }

    private fun quarantine(file: File, error: Exception) {
        val quarantineDir = dir.resolve("corrupt")
        quarantineDir.mkdirs()
        val target = quarantineDir.resolve("${file.nameWithoutExtension}-${System.currentTimeMillis()}.json")
        runCatching { Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            .onFailure { moveError ->
                logger.log(Level.WARNING, "不正なスクリプトを隔離できませんでした: ${file.absolutePath}", moveError)
            }
        logger.log(Level.WARNING, "不正なスクリプトを隔離しました: ${file.absolutePath} (${error.message})", error)
    }

    private data class ScriptDto(
        val id: String?,
        val name: String?,
        val owner: String?,
        val createdAt: Long?,
        val trigger: String?,
        val commands: List<CommandDto>?
    ) {
        fun toModel(): DiskScript {
            val uuid = parseUuid(id, "id")
            val ownerId = parseUuid(owner, "owner")
            val parsedCommands = commands?.map { it.toModel() }?.toMutableList()
                ?: error("commands is missing")
            return DiskScript(
                id = uuid,
                name = name?.takeIf { it.isNotBlank() } ?: error("name is missing"),
                owner = ownerId,
                createdAt = createdAt ?: error("createdAt is missing"),
                trigger = parseEnum<TriggerMode>(trigger, "trigger"),
                commands = parsedCommands
            )
        }

        private fun parseUuid(raw: String?, field: String): UUID =
            runCatching { UUID.fromString(raw ?: error("$field is missing")) }
                .getOrElse { error("invalid UUID in $field: $raw") }

        private inline fun <reified T : Enum<T>> parseEnum(raw: String?, field: String): T =
            runCatching { enumValueOf<T>(raw ?: error("$field is missing")) }
                .getOrElse { error("invalid enum in $field: $raw") }

        companion object {
            fun from(script: DiskScript): ScriptDto = ScriptDto(
                id = script.id.toString(),
                name = script.name,
                owner = script.owner.toString(),
                createdAt = script.createdAt,
                trigger = script.trigger.name,
                commands = script.commands.map { CommandDto.from(it) }
            )
        }
    }

    private data class CommandDto(val type: String?, val params: Map<String, String>?) {
        fun toModel(): ScriptCommand {
            val commandType = runCatching { CommandType.valueOf(type ?: error("command type is missing")) }
                .getOrElse { error("invalid command type: $type") }
            val commandParams = params ?: error("params is missing for command $type")
            if (commandParams.keys.any { it.isBlank() || commandParams[it] == null }) {
                error("invalid command params for $type")
            }
            return ScriptCommand(commandType, commandParams.toMutableMap())
        }

        companion object {
            fun from(command: ScriptCommand): CommandDto = CommandDto(command.type.name, command.params.toMap())
        }
    }
}
