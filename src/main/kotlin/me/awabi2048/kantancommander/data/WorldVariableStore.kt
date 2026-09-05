package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
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

    /**
     * 型だけを指定して新しい変数を定義します。
     *
     * 保存形式は定義と現在値を常に同じ型付き値として持つため、型だけの定義でも
     * 現在値の入れ物が必要です。ここで型ごとの空値を内部生成し、GUIで利用者へ
     * 初期値を入力させない定義経路を共通化します。
     */
    @Synchronized
    fun define(worldId: UUID, name: String, type: VariableType): Boolean =
        define(worldId, name, emptyValue(type))

    /** 新しい変数を定義し、渡された値を現在値として保存します。 */
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
                val loaded = gson.fromJson(migrated, WorldVariables::class.java)
                    ?: error("JSON root is null")
                val normalized = normalize(loaded, target)
                if (migrated.toString() != source.toString() || normalized != loaded) {
                    // 読み込み時点で定義と現在値を同じ正規形へ戻します。次回起動時に
                    // 同じ不整合を再解釈しないよう、修復結果も原子的に保存します。
                    runCatching { persist(worldId, normalized) }
                        .onFailure { failure ->
                            logger.log(
                                Level.WARNING,
                                "ワールド変数データの正規化結果を保存できませんでした: ${target.absolutePath}",
                                failure,
                            )
                        }
                }
                normalized
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
        result.add("definitions", migrateMap(section(source, "definitions", file), file, "定義"))
        result.add("values", migrateMap(section(source, "values", file), file, "現在値"))
        return result
    }

    /** JSONのセクション形状を先に確認し、片側の破損でファイル全体を落としません。 */
    private fun section(source: JsonObject, name: String, file: File): JsonObject? {
        val element = source.get(name) ?: return null
        if (!element.isJsonObject) {
            warnDropped(file, "構造", name, "セクションがオブジェクトではありません")
            return null
        }
        return element.asJsonObject
    }

    private fun migrateMap(source: JsonObject?, file: File, section: String): JsonObject {
        val result = JsonObject()
        if (source == null) return result
        source.entrySet().forEach { (name, element) ->
            val normalizedName = name.trim().lowercase(Locale.ROOT)
            if (SystemVariableNames.isReservedName(name)) {
                warnDropped(file, section, name, "システム予約名です")
                return@forEach
            }
            if (!CommandValueRules.isVariableName(normalizedName)) {
                warnDropped(file, section, name, "変数名が不正です")
                return@forEach
            }
            val converted = migrateValue(element, file, section, name)
            if (converted != null) result.add(normalizedName, converted)
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

    /**
     * Gsonは保存JSONの欠落・null・型不一致をKotlinの非nullモデルへ無検証で詰めるため、
     * 読み込み直後に定義と現在値を一対一の組へ再構成します。片側だけ存在する値や
     * 型が異なる組を残すと、一覧の表示・編集・使用箇所スキャンで別々の例外になります。
     */
    private fun normalize(loaded: WorldVariables, file: File): WorldVariables {
        val normalized = WorldVariables()
        val names = (loaded.definitions.keys + loaded.values.keys).distinct().sorted()
        names.forEach { name ->
            val definition = loaded.definitions[name]
            val value = loaded.values[name]
            if (definition == null || value == null) {
                warnDropped(file, "整合性", name, "定義と現在値が対になっていません")
                return@forEach
            }
            val definitionType = runCatching { definition.type }.getOrNull()
            val valueType = runCatching { value.type }.getOrNull()
            if (definitionType == null || valueType == null) {
                warnDropped(file, "整合性", name, "型がありません")
                return@forEach
            }
            if (definitionType != valueType) {
                warnDropped(file, "整合性", name, "定義と現在値の型が異なります")
                return@forEach
            }
            if (!valid(definition) || !valid(value)) {
                warnDropped(file, "整合性", name, "値の構造が型と一致しません")
                return@forEach
            }
            normalized.definitions[name] = definition.copy()
            normalized.values[name] = value.copy()
        }
        return normalized
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

    private fun valid(value: WorldVariableValue?): Boolean {
        if (value == null) return false
        val type = runCatching { value.type }.getOrNull() ?: return false
        return when (type) {
            VariableType.NUMBER -> value.numberValue?.isFinite() == true && value.stringValue == null
            // STRINGの長さ上限はCommandValueRulesの共通上限と一致させます。超過値は
            // 保存(set/define)で拒否され、読み込み時の正規化でも無効ペアとして破棄される
            // ため、編集Dialogの入力上限を超える保存値が存在しない不変条件を維持します。
            VariableType.STRING ->
                value.stringValue != null && value.numberValue == null &&
                    value.stringValue.length <= CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH
        }
    }

    /** 型だけの定義で使う保存層の空値です。利用者が設定した初期値ではありません。 */
    private fun emptyValue(type: VariableType): WorldVariableValue = when (type) {
        VariableType.NUMBER -> WorldVariableValue(type, numberValue = 0.0)
        VariableType.STRING -> WorldVariableValue(type, stringValue = "")
    }

    private fun file(worldId: UUID) = directory.resolve("$worldId.json")

    private fun normalizedName(raw: String): String {
        val value = raw.trim().lowercase(Locale.ROOT)
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
