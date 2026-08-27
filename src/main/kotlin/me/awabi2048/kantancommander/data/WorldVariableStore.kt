package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import me.awabi2048.kantancommander.model.WorldVariableValue
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

class WorldVariableStore(
    private val directory: File,
    private val logger: Logger = Logger.getLogger(WorldVariableStore::class.java.name),
) {
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
        val cached = cache[worldId]
        val target = file(worldId)
        val temporary = target.resolveSibling("${target.name}.tmp")
        val deleted = !target.exists() || target.delete()
        if (!deleted) {
            // ファイル削除に失敗した場合は、キャッシュだけを消して実体と
            // 不一致にしないよう、元の状態を保持します。
            if (cached != null) cache[worldId] = cached
            return false
        }
        cache.remove(worldId)
        // 一時ファイルは本体削除後の残骸だけを対象にします。削除できなくても
        // ワールド本体の削除結果は確定しているため、次回起動時に再試行します。
        if (temporary.exists()) temporary.delete()
        return deleted
    }

    private fun state(worldId: UUID): WorldVariables =
        cache.getOrPut(worldId) {
            val file = file(worldId)
            if (!file.isFile) WorldVariables()
            else runCatching {
                gson.fromJson(file.readText(Charsets.UTF_8), WorldVariables::class.java)
                    ?: error("JSON root is null")
            }.getOrElse { failure ->
                quarantine(file, failure)
                WorldVariables()
            }
        }

    private fun quarantine(file: File, error: Throwable) {
        val quarantineDir = directory.resolve("corrupt").also(File::mkdirs)
        val target = quarantineDir.resolve("${file.nameWithoutExtension}-${System.currentTimeMillis()}.json")
        runCatching {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { moveError ->
            logger.log(Level.WARNING, "ワールド変数データを隔離できませんでした: ${file.absolutePath}", moveError)
        }
        logger.log(Level.WARNING, "不正なワールド変数データを隔離しました: ${file.absolutePath}", error)
    }

    private fun persist(worldId: UUID, state: WorldVariables) {
        val target = file(worldId)
        val temporary = target.resolveSibling("${target.name}.tmp")
        temporary.writeText(gson.toJson(state), Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (failure: AtomicMoveNotSupportedException) {
            // 置換途中で正本を失う非原子的なフォールバックは行いません。
            // 既存のワールド変数ファイルを保ったまま保存失敗として扱います。
            runCatching { Files.deleteIfExists(temporary.toPath()) }
            throw IllegalStateException("ワールド変数を原子的に保存できないファイルシステムです", failure)
        }
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
