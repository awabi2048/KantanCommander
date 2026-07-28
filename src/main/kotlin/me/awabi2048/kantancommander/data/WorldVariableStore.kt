package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import me.awabi2048.kantancommander.model.WorldVariableValue
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class WorldVariableStore(private val directory: File) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val cache = mutableMapOf<UUID, WorldVariables>()

    init {
        directory.mkdirs()
    }

    @Synchronized
    fun get(worldId: UUID, name: String): WorldVariableValue? =
        state(worldId).values[normalizedName(name)]

    @Synchronized
    fun set(worldId: UUID, name: String, value: WorldVariableValue) {
        val normalized = normalizedName(name)
        val state = state(worldId)
        state.definitions.putIfAbsent(normalized, value)
        state.values[normalized] = value
        save(worldId)
    }

    @Synchronized
    fun remove(worldId: UUID, name: String): Boolean {
        val normalized = normalizedName(name)
        val state = state(worldId)
        val removed = state.values.remove(normalized) != null
        state.definitions.remove(normalized)
        if (removed) save(worldId)
        return removed
    }

    @Synchronized
    fun list(worldId: UUID): Map<String, WorldVariableValue> = state(worldId).values.toMap()

    @Synchronized
    fun definitions(worldId: UUID): Map<String, WorldVariableValue> =
        state(worldId).definitions.mapValues { (_, value) -> value.copy() }

    @Synchronized
    fun copyDefinitions(sourceWorldId: UUID, targetWorldId: UUID) {
        require(sourceWorldId != targetWorldId) { "source and target MyWorld must differ" }
        val initialValues = state(sourceWorldId).definitions.mapValues { (_, value) -> value.copy() }
        cache[targetWorldId] = WorldVariables(
            definitions = initialValues.toMutableMap(),
            values = initialValues.toMutableMap(),
        )
        save(targetWorldId)
    }

    @Synchronized
    fun deleteWorld(worldId: UUID): Boolean {
        cache.remove(worldId)
        val target = file(worldId)
        val temporary = target.resolveSibling("${target.name}.tmp")
        val deleted = !target.exists() || target.delete()
        if (temporary.exists()) temporary.delete()
        return deleted
    }

    private fun state(worldId: UUID): WorldVariables =
        cache.getOrPut(worldId) {
            val file = file(worldId)
            if (!file.isFile) WorldVariables()
            else runCatching {
                gson.fromJson(file.readText(Charsets.UTF_8), WorldVariables::class.java)
            }.getOrNull() ?: WorldVariables()
        }

    private fun save(worldId: UUID) {
        val target = file(worldId)
        val temporary = target.resolveSibling("${target.name}.tmp")
        temporary.writeText(gson.toJson(state(worldId)), Charsets.UTF_8)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun file(worldId: UUID) = directory.resolve("$worldId.json")

    private fun normalizedName(raw: String): String {
        val value = raw.trim().lowercase()
        require(value.matches(Regex("[a-z0-9_.-]{1,64}"))) { "invalid variable name" }
        return value
    }

    private data class WorldVariables(
        val definitions: MutableMap<String, WorldVariableValue> = linkedMapOf(),
        val values: MutableMap<String, WorldVariableValue> = linkedMapOf(),
    )
}
