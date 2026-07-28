package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.awabi2048.kantancommander.model.WorldVariableValue
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class WorldVariableStore(private val directory: File) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val cache = mutableMapOf<UUID, MutableMap<String, WorldVariableValue>>()

    init {
        directory.mkdirs()
    }

    @Synchronized
    fun get(worldId: UUID, name: String): WorldVariableValue? =
        values(worldId)[normalizedName(name)]

    @Synchronized
    fun set(worldId: UUID, name: String, value: WorldVariableValue) {
        values(worldId)[normalizedName(name)] = value
        save(worldId)
    }

    @Synchronized
    fun remove(worldId: UUID, name: String): Boolean {
        val removed = values(worldId).remove(normalizedName(name)) != null
        if (removed) save(worldId)
        return removed
    }

    @Synchronized
    fun list(worldId: UUID): Map<String, WorldVariableValue> = values(worldId).toMap()

    @Synchronized
    fun deleteWorld(worldId: UUID): Boolean {
        cache.remove(worldId)
        val target = file(worldId)
        val temporary = target.resolveSibling("${target.name}.tmp")
        val deleted = !target.exists() || target.delete()
        if (temporary.exists()) temporary.delete()
        return deleted
    }

    private fun values(worldId: UUID): MutableMap<String, WorldVariableValue> =
        cache.getOrPut(worldId) {
            val file = file(worldId)
            if (!file.isFile) linkedMapOf()
            else runCatching {
                val type = object : TypeToken<LinkedHashMap<String, WorldVariableValue>>() {}.type
                gson.fromJson<LinkedHashMap<String, WorldVariableValue>>(file.readText(Charsets.UTF_8), type)
            }.getOrNull() ?: linkedMapOf()
        }

    private fun save(worldId: UUID) {
        val target = file(worldId)
        val temporary = target.resolveSibling("${target.name}.tmp")
        temporary.writeText(gson.toJson(values(worldId)), Charsets.UTF_8)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun file(worldId: UUID) = directory.resolve("$worldId.json")

    private fun normalizedName(raw: String): String {
        val value = raw.trim().lowercase()
        require(value.matches(Regex("[a-z0-9_.-]{1,64}"))) { "invalid variable name" }
        return value
    }
}
