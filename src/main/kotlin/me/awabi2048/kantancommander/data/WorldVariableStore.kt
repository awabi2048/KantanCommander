package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.VariableType
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
        state(worldId).values[normalizedName(name)]?.copy()

    @Synchronized
    fun set(worldId: UUID, name: String, value: WorldVariableValue) {
        require(valid(value)) { "invalid variable value" }
        val normalized = normalizedName(name)
        val current = state(worldId)
        val declared = current.definitions[normalized]
        require(declared == null || declared.type == value.type) { "variable type cannot change" }
        val candidate = current.deepCopy()
        candidate.definitions.putIfAbsent(normalized, value.copy())
        candidate.values[normalized] = value.copy()
        persist(worldId, candidate)
        cache[worldId] = candidate
    }

    /** 新しい変数を定義し、初期値を現在値として保存します。 */
    @Synchronized
    fun define(worldId: UUID, name: String, value: WorldVariableValue): Boolean {
        require(valid(value)) { "invalid variable value" }
        val normalized = normalizedName(name)
        val current = state(worldId)
        if (normalized in current.definitions) return false
        val candidate = current.deepCopy()
        candidate.definitions[normalized] = value.copy()
        candidate.values[normalized] = value.copy()
        persist(worldId, candidate)
        cache[worldId] = candidate
        return true
    }

    @Synchronized
    fun remove(worldId: UUID, name: String): Boolean {
        val normalized = normalizedName(name)
        val candidate = state(worldId).deepCopy()
        val removed = candidate.values.remove(normalized) != null || candidate.definitions.remove(normalized) != null
        candidate.definitions.remove(normalized)
        if (removed) {
            persist(worldId, candidate)
            cache[worldId] = candidate
        }
        return removed
    }

    @Synchronized
    fun list(worldId: UUID): Map<String, WorldVariableValue> =
        state(worldId).values.mapValues { (_, value) -> value.copy() }

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
            val target = file(worldId)
            if (!target.isFile) WorldVariables()
            else runCatching {
                val source = JsonParser.parseString(target.readText(Charsets.UTF_8)).asJsonObject
                val migrated = migrate(source, target)
                gson.fromJson(migrated, WorldVariables::class.java)
                    ?: error("JSON root is null")
            }.getOrElse { failure ->
                quarantine(target, failure)
                WorldVariables()
            }
        }

    /**
     * 旧型を値単位で移行します。新しい Gson モデルへ直接デシリアライズすると、
     * 廃止 enum が一つ混ざっただけでファイル全体が読めなくなるため、ここで型を
     * 意味変換してから新モデルへ渡します。
     */
    private fun migrate(source: JsonObject, file: File): JsonObject {
        val result = JsonObject()
        result.add("definitions", migrateMap(source.getAsJsonObject("definitions"), file, "定義"))
        result.add("values", migrateMap(source.getAsJsonObject("values"), file, "現在値"))
        return result
    }

    private fun migrateMap(source: JsonObject?, file: File, section: String): JsonObject {
        val result = JsonObject()
        if (source == null) return result
        source.entrySet().forEach { (name, element) ->
            val converted = migrateValue(element, file, section, name)
            if (converted != null) result.add(name, converted)
        }
        return result
    }

    private fun migrateValue(element: JsonElement, file: File, section: String, name: String): JsonObject? {
        if (!element.isJsonObject) {
            warnDropped(file, section, name, "値がオブジェクトではありません")
            return null
        }
        val source = element.asJsonObject
        val type = source.get("type")?.takeIf { it.isJsonPrimitive }?.asString?.uppercase()
            ?: run {
                warnDropped(file, section, name, "型がありません")
                return null
            }
        return when (type) {
            "NUMBER", "INTEGER", "DECIMAL" -> {
                val raw = source.get("numberValue")
                    ?: source.get("decimalValue")
                    ?: source.get("integerValue")
                val number = raw?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
                if (number == null || !number.isFinite()) {
                    warnDropped(file, section, name, "数値が不正です")
                    null
                } else JsonObject().apply {
                    addProperty("type", VariableType.NUMBER.name)
                    addProperty("numberValue", number)
                }
            }
            "STRING", "TEXT" -> {
                val raw = source.get("stringValue") ?: source.get("textValue")
                if (raw == null || !raw.isJsonPrimitive || !raw.asJsonPrimitive.isString) {
                    warnDropped(file, section, name, "文字列がありません")
                    null
                } else JsonObject().apply {
                    addProperty("type", VariableType.STRING.name)
                    addProperty("stringValue", raw.asString)
                }
            }
            "BOOLEAN", "POSITION", "ENTITY" -> {
                warnDropped(file, section, name, "廃止された型 $type です")
                null
            }
            else -> {
                warnDropped(file, section, name, "未知の型 $type です")
                null
            }
        }
    }

    private fun warnDropped(file: File, section: String, name: String, reason: String) {
        logger.warning("ワールド変数を移行時に破棄しました: file=${file.absolutePath}, section=$section, name=$name, reason=$reason")
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
        VariableType.NUMBER -> value.numberValue?.isFinite() == true && value.stringValue == null
        VariableType.STRING -> value.stringValue != null && value.numberValue == null
    }

    private fun file(worldId: UUID) = directory.resolve("$worldId.json")

    private fun normalizedName(raw: String): String {
        val value = raw.trim().lowercase()
        require(CommandValueRules.isVariableName(value)) { "invalid variable name" }
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
