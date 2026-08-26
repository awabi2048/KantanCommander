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
        require(valid(value)) { "invalid variable value" }
        val normalized = normalizedName(name)
        val candidate = state(worldId).deepCopy()
        candidate.definitions.putIfAbsent(normalized, value.copy())
        candidate.values[normalized] = value.copy()
        persist(worldId, candidate)
        cache[worldId] = candidate
    }

    @Synchronized
    fun remove(worldId: UUID, name: String): Boolean {
        val normalized = normalizedName(name)
        val candidate = state(worldId).deepCopy()
        val removed = candidate.values.remove(normalized) != null
        candidate.definitions.remove(normalized)
        if (removed) {
            persist(worldId, candidate)
            cache[worldId] = candidate
        }
        return removed
    }

    @Synchronized
    fun list(worldId: UUID): Map<String, WorldVariableValue> = state(worldId).values.toMap()

    @Synchronized
    fun definitions(worldId: UUID): Map<String, WorldVariableValue> =
        state(worldId).definitions.mapValues { (_, value) -> value.copy() }

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

    private fun persist(worldId: UUID, state: WorldVariables) {
        val target = file(worldId)
        val temporary = target.resolveSibling("${target.name}.tmp")
        temporary.writeText(gson.toJson(state), Charsets.UTF_8)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun valid(value: WorldVariableValue): Boolean = when (value.type) {
        me.awabi2048.kantancommander.model.VariableType.BOOLEAN -> value.booleanValue != null
        me.awabi2048.kantancommander.model.VariableType.INTEGER -> value.integerValue != null
        me.awabi2048.kantancommander.model.VariableType.DECIMAL -> value.decimalValue?.isFinite() == true
        me.awabi2048.kantancommander.model.VariableType.TEXT -> value.textValue != null
        me.awabi2048.kantancommander.model.VariableType.POSITION -> value.position?.let {
            it.x.isFinite() && it.y.isFinite() && it.z.isFinite() && it.yaw.isFinite() && it.pitch.isFinite()
        } == true
        me.awabi2048.kantancommander.model.VariableType.ENTITY -> value.entityId != null
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
    ) {
        fun deepCopy() = WorldVariables(
            definitions.mapValuesTo(linkedMapOf()) { (_, value) -> value.copy() },
            values.mapValuesTo(linkedMapOf()) { (_, value) -> value.copy() },
        )
    }
}
