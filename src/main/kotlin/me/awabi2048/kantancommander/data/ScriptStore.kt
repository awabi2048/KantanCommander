package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.MAX_TIMER_SECONDS
import me.awabi2048.kantancommander.model.STRUCTURED_FORMAT_VERSION
import me.awabi2048.kantancommander.gui.GraphLayoutEngine
import java.io.File
import java.math.BigDecimal
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.withLock

class ScriptStore(
    private val dir: File,
    private val logger: Logger,
    private val limits: GraphLimits = GraphLimits(),
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val relationFile = dir.resolve("relations.json")
    private val libraryPrograms = linkedMapOf<UUID, LinkedHashSet<UUID>>()
    private val historyPrograms = linkedMapOf<UUID, LinkedHashSet<UUID>>()

    /**
     * 保存済み内容の正本キャッシュ。レッドストーン監視のようにtick単位で参照される
     * [load]が、参照ごとにJSON解析と構造検証を繰り返さないようにするためのもの。
     * 正本は検証に通った時点の内容のみを採用し、[load]経由では独立コピーを渡す。
     */
    private val cache = ConcurrentHashMap<UUID, DiskScript>()
    /**
     * 読み込み・変更・検証・保存を同じロックへ束ねます。ファイル置換だけを
     * atomicにしても、2つの画面が古いコピーを保存すれば後勝ちで変更を失うため、
     * プログラム単位のread-modify-write全体を直列化します。
     */
    private val scriptLocks = ConcurrentHashMap<UUID, ReentrantLock>()
    /** 関係JSONを複数の保存処理から同時に更新しても、片方の履歴を失わないためのロックです。 */
    private val relationLock = ReentrantLock()

    init {
        dir.mkdirs()
        loadRelations()
        migrateLegacyListedPrograms()
    }

    fun create(owner: UUID, name: String): DiskScript =
        DiskScript(name = name, owner = owner).also {
            save(it)
            addToLibrary(owner, it.id)
        }

    fun createPlacement(
        owner: UUID,
        name: String,
    ): DiskScript = DiskScript(name = name, owner = owner, listed = false).also(::save)

    fun copyForPlacement(source: DiskScript): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            createdAt = System.currentTimeMillis(),
            listed = false,
            revision = 0L,
            graph = source.graph.deepCopy(),
        ).also(::save)

    fun copyForItem(source: DiskScript): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            createdAt = System.currentTimeMillis(),
            listed = false,
            revision = 0L,
            graph = source.graph.deepCopy(),
        ).also(::save)

    fun copyToLibrary(source: DiskScript, owner: UUID, name: String = source.name): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            name = name,
            owner = owner,
            createdAt = System.currentTimeMillis(),
            listed = false,
            revision = 0L,
            graph = source.graph.deepCopy(),
        ).also {
            save(it)
            addToLibrary(owner, it.id)
        }

    /** 保存済み内容の独立コピーを返す。呼び出し側が変更しても保存済み正本へ波及しない。 */
    fun load(id: UUID): DiskScript? = withScriptLock(id) { cached(id)?.deepCopy() }

    /**
     * 最新の正本をロック内で取得し、変更・検証・保存まで一度だけ行います。
     * expectedRevisionを指定した場合、画面表示後に別の編集が成功していれば
     * 保存せずnullを返します。削除確認や入力画面の古い応答を安全に無効化する
     * 共通境界として、Gesture／Inventory双方から利用します。
     */
    fun <T : Any> update(
        id: UUID,
        editorId: UUID? = null,
        expectedRevision: Long? = null,
        change: (DiskScript) -> T?,
    ): T? = withScriptLock(id) {
        val script = cached(id)?.deepCopy() ?: return@withScriptLock null
        if (expectedRevision != null && script.revision != expectedRevision) {
            return@withScriptLock null
        }
        val result = change(script) ?: return@withScriptLock null
        saveLocked(script, editorId)
        result
    }

    @Suppress("DEPRECATION")
    fun save(script: DiskScript, editorId: UUID? = null) {
        withScriptLock(script.id) {
            saveLocked(script, editorId)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLocked(script: DiskScript, editorId: UUID?) {
        require(script.formatVersion == STRUCTURED_FORMAT_VERSION) { "unsupported script format" }
        require(script.revision >= 0L) { "unsupported negative script revision" }
        val validation = validateRecursively(script.graph)
        require(validation.isEmpty()) { validation.joinToString("; ") }
        // 旧listedフラグを一覧の正本として再出力しないよう、保存境界で必ず
        // 関係ファイル方式へ正規化します。
        script.listed = false
        val previous = cache[script.id]
        if (previous != null && script.revision != previous.revision) {
            throw java.util.ConcurrentModificationException(
                "プログラムディスクが別の編集で更新されています: id=${script.id} " +
                    "expected=${script.revision} actual=${previous.revision}",
            )
        }
        if (previous != null && !sameEditableContent(previous, script)) {
            // 名前・タイマーだけの編集も、ノード編集と同じくディスク内容です。
            // 呼び出し元ごとにフラグを立てるとインベントリGUIとジェスチャーGUIで
            // 漏れが生じるため、保存正本を確定するこの一点で差分を記録します。
            script.contentModified = true
        }
        // 保存成功ごとに単調増加させ、入力画面開始時の正本世代と比較できるようにします。
        script.revision = (previous?.revision ?: script.revision).inc()
        atomicWrite(file(script.id), gson.toJson(script))
        // 検証に通った時点の内容だけを正本として採用する。以後の呼び出し側変更は反映されない。
        cache[script.id] = script.deepCopy()
        editorId?.let { recordHistory(it, script.id) }
    }

    fun delete(id: UUID) = withScriptLock(id) {
        // 履歴は「編集したプログラム」を無条件に追跡するため、配置撤去や
        // ライブラリからの除外だけで正本を消してはなりません。履歴に残る間は
        // ライブラリ関係だけを外し、JSONと履歴関係を保持します。
        // ノード保存と削除も同じプログラムロックへ束ね、削除途中に古い編集結果が
        // 復活したり、削除直後の保存が正本を再生成したりする競合を防ぎます。
        val isInHistory = relationLock.withLock { historyPrograms.values.any { id in it } }
        relationLock.withLock {
            libraryPrograms.values.forEach { it.remove(id) }
            libraryPrograms.entries.removeIf { it.value.isEmpty() }
            writeRelationsLocked()
        }
        if (isInHistory) return@withScriptLock
        val target = file(id)
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("プログラムディスクを削除できません: ${target.absolutePath}")
        }
        cache.remove(id)
    }

    fun listAll(): List<DiskScript> =
        dir.listFiles { file -> file.isFile && file.extension.equals("json", true) }
            ?.mapNotNull { file ->
                val id = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()
                    ?: return@mapNotNull null
                cached(id)?.deepCopy()
            }
            ?.sortedBy(DiskScript::createdAt)
            ?: emptyList()

    /** 旧API名。実体は新しいライブラリ関係を参照します。 */
    fun listOwned(owner: UUID): List<DiskScript> = listLibrary(owner)

    /** プレイヤーが明示的にライブラリへ保存したプログラムを返します。 */
    fun listLibrary(playerId: UUID): List<DiskScript> =
        relationLock.withLock { resolveRelations(libraryPrograms[playerId]) }

    /** プレイヤーが編集したプログラムを、直近に編集した順で返します。 */
    fun listHistory(playerId: UUID): List<DiskScript> =
        relationLock.withLock { resolveRelations(historyPrograms[playerId]).asReversed() }

    /** 編集成功時にプレイヤーとプログラムの関係を一件だけ記録します。 */
    fun recordHistory(playerId: UUID, scriptId: UUID): Boolean {
        if (cached(scriptId) == null) return false
        return relationLock.withLock {
            val entries = historyPrograms.getOrPut(playerId) { linkedSetOf() }
            // LinkedHashSetの末尾を最新編集位置として利用します。版管理は行いません。
            entries.remove(scriptId)
            entries += scriptId
            writeRelationsLocked()
            true
        }
    }

    /** 既存プログラムをプレイヤーのライブラリへ明示登録します。 */
    fun addToLibrary(playerId: UUID, scriptId: UUID): Boolean {
        if (cached(scriptId) == null) return false
        return relationLock.withLock {
            libraryPrograms.getOrPut(playerId) { linkedSetOf() } += scriptId
            writeRelationsLocked()
            true
        }
    }

    /** ライブラリから外してもプログラム正本や履歴は削除しません。 */
    fun removeFromLibrary(playerId: UUID, scriptId: UUID): Boolean {
        return relationLock.withLock {
            val entries = libraryPrograms[playerId] ?: return@withLock false
            val removed = entries.remove(scriptId)
            if (entries.isEmpty()) libraryPrograms.remove(playerId)
            if (removed) writeRelationsLocked()
            removed
        }
    }

    /** 一覧から取得するディスクは正本UUIDを維持し、履歴の同一プログラム関係を壊しません。 */
    fun referenceForItem(source: DiskScript): DiskScript = source.deepCopy()

    private fun resolveRelations(ids: Set<UUID>?): List<DiskScript> =
        ids.orEmpty().mapNotNull { cached(it)?.deepCopy() }

    private fun loadRelations() {
        if (!relationFile.isFile) return
        runCatching {
            val root = JsonParser.parseString(relationFile.readText(Charsets.UTF_8)).asJsonObject
            readRelation(root["library"], libraryPrograms)
            readRelation(root["history"], historyPrograms)
        }.onFailure { error ->
            logger.warning("プログラムのライブラリ／履歴関係を読み込めないため空として扱います: ${relationFile.absolutePath}: ${error.message}")
            libraryPrograms.clear()
            historyPrograms.clear()
        }
    }

    private fun readRelation(value: com.google.gson.JsonElement?, target: MutableMap<UUID, LinkedHashSet<UUID>>) {
        val root = value?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        root.entrySet().forEach { (playerRaw, idsRaw) ->
            val playerId = runCatching { UUID.fromString(playerRaw) }.getOrNull() ?: return@forEach
            val ids = idsRaw.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { entry -> runCatching { UUID.fromString(entry.asString) }.getOrNull() }
                ?.toCollection(linkedSetOf())
                ?: return@forEach
            if (ids.isNotEmpty()) target[playerId] = ids
        }
    }

    private fun writeRelations() {
        relationLock.withLock { writeRelationsLocked() }
    }

    private fun writeRelationsLocked() {
        val root = JsonObject()
        root.add("library", writeRelation(libraryPrograms))
        root.add("history", writeRelation(historyPrograms))
        atomicWrite(relationFile, gson.toJson(root))
    }

    private fun writeRelation(source: Map<UUID, LinkedHashSet<UUID>>): JsonObject {
        val root = JsonObject()
        source.filterValues { it.isNotEmpty() }.forEach { (playerId, ids) ->
            val array = JsonArray()
            ids.forEach { array.add(it.toString()) }
            root.add(playerId.toString(), array)
        }
        return root
    }

    /** listed=trueの旧形式を初回だけライブラリ関係へ移行します。 */
    @Suppress("DEPRECATION")
    private fun migrateLegacyListedPrograms() {
        var changed = false
        dir.listFiles { file -> file.isFile && file.extension.equals("json", true) }
            ?.forEach { file ->
                val id = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull() ?: return@forEach
                val script = cached(id) ?: return@forEach
                if (script.listed) {
                    val entries = libraryPrograms.getOrPut(script.owner) { linkedSetOf() }
                    changed = entries.add(script.id) || changed
                    // 移行済みマーカーを消すことで、利用者が後からライブラリから
                    // 外したプログラムを次回起動時に再登録しないようにします。
                    script.listed = false
                    atomicWrite(file(id), gson.toJson(script))
                }
            }
        if (changed) writeRelations()
    }

    private fun <T> withScriptLock(id: UUID, action: () -> T): T =
        scriptLocks.computeIfAbsent(id) { ReentrantLock() }.withLock(action)

    private fun cached(id: UUID): DiskScript? {
        cache[id]?.let { return it }
        if (!file(id).isFile) return null
        val loaded = read(file(id)) ?: return null
        cache[id] = loaded
        return loaded
    }

    private fun DiskScript.deepCopy(): DiskScript = copy(
        // TimerSettingは可変データなので、グラフだけをコピーするとload()後の
        // タイマー編集が保存済みキャッシュへ直接波及し、差分追跡をすり抜けます。
        timer = timer.copy(),
        graph = graph.deepCopy(),
    )

    /** 識別子・所有者・一覧公開状態・作成時刻を除いた利用者編集部分を比較します。 */
    private fun sameEditableContent(left: DiskScript, right: DiskScript): Boolean =
        left.name == right.name &&
            left.activation == right.activation &&
            left.timer == right.timer &&
            left.graph == right.graph

    private fun file(id: UUID) = dir.resolve("$id.json")

    private fun atomicWrite(target: File, content: String) {
        val temporary = target.resolveSibling("${target.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (failure: AtomicMoveNotSupportedException) {
            // 正本を通常のREPLACE_EXISTINGで上書きすると、途中停止時に正本が
            // 消失／部分書込みになるため、非原子的な代替は行いません。
            // 一時ファイルだけを破棄して既存正本を保ち、呼び出し側へ失敗を返します。
            runCatching { Files.deleteIfExists(temporary.toPath()) }
            throw IllegalStateException("プログラムディスクを原子的に保存できないファイルシステムです", failure)
        }
    }

    private fun read(file: File): DiskScript? = try {
        val source = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
        val sourceVersion = source["formatVersion"]?.asInt ?: return null
        val migrated = when (sourceVersion) {
            STRUCTURED_FORMAT_VERSION -> source
            LEGACY_COMMAND_FORMAT_VERSION -> migrateCommandFormat(source)
            LEGACY_TICK_FORMAT_VERSION -> migrateCommandFormat(migrateLegacyTickFormat(source))
            else -> {
                logger.warning("未対応の構造化プログラムディスク形式を読み込みません: ${file.absolutePath} version=$sourceVersion")
                return null
            }
        }
        gson.fromJson(migrated, DiskScript::class.java)
            ?.takeIf { it.formatVersion == STRUCTURED_FORMAT_VERSION }
            ?.also { script ->
                // 構造そのものの違反は破損データとして隔離する。
                val structuralViolations = validateRecursively(script.graph, UNBOUNDED_LIMITS)
                require(structuralViolations.isEmpty()) { structuralViolations.joinToString("; ") }
                // config上限だけを超えるデータは、リロードでの上限引き下げ時に
                // 既存データを失わせないため、警告のみで読み込みを続行する（仕様18 既存グラフを変更しない）。
                // 実行可否は実行前検証が別途判定する。
                val limitViolations = validateRecursively(script.graph, limits)
                if (limitViolations.isNotEmpty()) {
                    logger.warning(
                        "設定上限を超える保存済みプログラムディスクを読み込みました（隔離していません）: " +
                            "${file.absolutePath} (${limitViolations.joinToString("; ")})"
                    )
                }
                if (sourceVersion != STRUCTURED_FORMAT_VERSION) {
                    // 旧形式を読み込んだ時点でv9の正本へ書き戻し、次回以降に
                    // 旧tick値や廃止コマンドを二重解釈しないようにします。
                    atomicWrite(file, gson.toJson(migrated))
                    logger.info("構造化プログラムディスクをv9形式へ移行しました: ${file.absolutePath}")
                }
            }
    } catch (error: Exception) {
        quarantine(file, error)
        null
    }

    /**
     * v6の内部単位（10tick=0.5秒）を、v7の秒単位へ一度だけ変換します。
     * 保存済みJSONを直接新モデルへ流し込むと旧ticksキーが黙って無視されるため、
     * フォーマットバージョンを分岐させた明示移行にしています。
     */
    private fun migrateLegacyTickFormat(source: JsonObject): JsonObject {
        val migrated = source.deepCopy()
        migrated.addProperty("formatVersion", LEGACY_COMMAND_FORMAT_VERSION)
        migrated["timer"]?.asJsonObject?.let { timer ->
            val units = timer["intervalUnits"]?.asInt ?: 1
            val seconds = ((units.toLong() + 1L) / 2L)
                .coerceIn(1L, MAX_TIMER_SECONDS.toLong())
            timer.addProperty("intervalSeconds", seconds.toInt())
            timer.remove("intervalUnits")
        }
        migrateGraphTimes(migrated["graph"]?.asJsonObject)
        return migrated
    }

    /** v7のコマンド・型モデルをv9へ意味変換します。 */
    private fun migrateCommandFormat(source: JsonObject): JsonObject {
        val migrated = source.deepCopy()
        migrated.addProperty("formatVersion", STRUCTURED_FORMAT_VERSION)
        migrateCommandGraph(migrated["graph"]?.asJsonObject)
        return migrated
    }

    private fun migrateCommandGraph(graph: JsonObject?) {
        val nodes = graph?.get("nodes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val nodeIds = nodes.entrySet().map { it.key }.toList()
        nodeIds.forEach { nodeId ->
            val node = nodes[nodeId]?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val params = node["params"]?.takeIf { it.isJsonObject }?.asJsonObject
            when (node["type"]?.asString) {
                "EQUIP_ITEM" -> {
                    node.addProperty("type", "ENTITY_ACTION")
                    params?.addProperty("action", "equip")
                    params?.addProperty("slot", params["slot"]?.asString ?: "HAND")
                    params?.addProperty("item", params["item"]?.asString ?: "")
                    params?.addProperty("itemData", params["itemData"]?.asString ?: "")
                    params?.addProperty("overwrite", "false")
                }
                "ENTITY_STATE" -> migrateLegacyEntityState(node, params, graph)
                "ITEM_POSSESSION" -> {
                    node.addProperty("type", "CONDITION")
                    params?.addProperty("kind", "PLAYER_STATE")
                    params?.addProperty("sneaking", "")
                    params?.addProperty("item", params["item"]?.asString ?: "")
                    params?.addProperty("itemData", params["itemData"]?.asString ?: "")
                    params?.remove("variableScope")
                    params?.remove("count")
                }
                "VARIABLE" -> migrateLegacyVariable(node, params, graph)
                else -> Unit
            }
            migrateLegacyPositionFields(node, params)
            if (node["type"]?.asString == "FOR_START") migrateLegacyForCount(params)
            migrateCommandGraph(node["snapshot"]?.asJsonObject)
        }
    }

    private fun migrateLegacyEntityState(node: JsonObject, params: JsonObject?, graph: JsonObject?) {
        if (params == null) {
            logger.warning("v7の状態条件に設定値がないため操作を破棄します: node=${node["id"]?.asString}")
            dropLinearNode(graph, node["id"]?.asString)
            return
        }
        val state = params["state"]?.asString
        if (state == "sneaking") {
            node.addProperty("type", "CONDITION")
            params.addProperty("kind", "PLAYER_STATE")
            params.addProperty("sneaking", "true")
            params.remove("state")
            return
        }
        logger.warning("v7の未対応エンティティ状態のノードを破棄します: node=${node["id"]?.asString} state=$state")
        dropLinearNode(graph, node["id"]?.asString)
    }

    private fun migrateLegacyVariable(node: JsonObject, params: JsonObject?, graph: JsonObject?) {
        if (params == null) {
            logger.warning("v7の変数操作に設定値がないため操作を破棄します: node=${node["id"]?.asString}")
            dropLinearNode(graph, node["id"]?.asString)
            return
        }
        val type = params["type"]?.asString?.uppercase()
        val newType = when (type) {
            "INTEGER", "DECIMAL" -> "NUMBER"
            "TEXT" -> "STRING"
            else -> null
        }
        if (newType == null) {
            logger.warning("v7の未対応変数型の操作を破棄します: node=${node["id"]?.asString} type=$type")
            dropLinearNode(graph, node["id"]?.asString)
            return
        }
        val name = params["name"]?.asString.orEmpty()
        val operation = params["operation"]?.asString?.uppercase()
        val rawValue = params["value"]?.asString ?: ""
        val mappedOperation: String
        val mappedValue: String
        val mappedMode: String
        when (operation) {
            "SET" -> {
                mappedOperation = "DEFINE"
                mappedValue = rawValue
                mappedMode = "ASSIGN"
            }
            "ADD" -> {
                mappedOperation = "CHANGE"
                mappedValue = "${'$'}{$name} + ($rawValue)"
                mappedMode = "CALCULATE"
            }
            "SUBTRACT" -> {
                mappedOperation = "CHANGE"
                mappedValue = "${'$'}{$name} - ($rawValue)"
                mappedMode = "CALCULATE"
            }
            else -> {
                logger.warning("v7の未対応変数操作を破棄します: node=${node["id"]?.asString} operation=$operation")
                dropLinearNode(graph, node["id"]?.asString)
                return
            }
        }
        if (mappedOperation.isBlank()) {
            logger.warning("v7の未対応変数操作を破棄します: node=${node["id"]?.asString} operation=$operation")
            dropLinearNode(graph, node["id"]?.asString)
            return
        }
        params.remove("scope")
        params.remove("variableScope")
        params.addProperty("type", newType)
        params.addProperty("operation", mappedOperation)
        params.addProperty("changeMode", mappedMode)
        params.addProperty("value", mappedValue)
    }

    private fun migrateLegacyPositionFields(node: JsonObject, params: JsonObject?) {
        val fields = listOf("destinationSpec", "conditionPositionSpec", "blockPositionSpec", "blockFromSpec", "blockToSpec")
        fields.forEach { field ->
            node[field]?.takeIf { it.isJsonObject }?.asJsonObject?.let { position ->
                val kind = position["kind"]?.asString
                if (kind == "TEMPORARY_VARIABLE" || kind == "WORLD_VARIABLE") {
                    logger.warning("v7の変数位置参照を移行時に破棄します: node=${node["id"]?.asString} field=$field")
                    node.remove(field)
                }
            }
        }
    }

    /** v7の範囲指定を、現行の回数指定へ残さず変換します。 */
    private fun migrateLegacyForCount(params: JsonObject?) {
        if (params == null) return
        val legacyFields = listOf(
            "startSource", "endSource", "stepSource",
            "startValue", "endValue", "stepValue", "inclusiveEnd",
        )
        if (legacyFields.none(params::has)) return
        legacyFields.forEach(params::remove)
        if (!params.has("count")) params.addProperty("count", "1")
        logger.warning("v7の範囲指定forを現行の回数指定へ移行しました。旧範囲値は保持しません")
    }

    /** 変換不能な線形ノードを経路から外し、保存グラフの構造を壊さないようにします。 */
    private fun dropLinearNode(graph: JsonObject?, rawId: String?) {
        if (graph == null || rawId.isNullOrBlank()) return
        val nodes = graph["nodes"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val node = nodes[rawId]?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val replacement = node["next"]?.asString
        nodes.entrySet().forEach { (_, element) ->
            val parent = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            listOf("next", "trueNext", "falseNext").forEach { edge ->
                if (parent[edge]?.asString == rawId) {
                    if (replacement == null) parent.remove(edge) else parent.addProperty(edge, replacement)
                }
            }
        }
        if (graph["entryNodeId"]?.asString == rawId) {
            if (replacement == null) graph.remove("entryNodeId") else graph.addProperty("entryNodeId", replacement)
        }
        nodes.remove(rawId)
    }

    private fun migrateGraphTimes(graph: JsonObject?) {
        val nodes = graph?.get("nodes")?.asJsonObject ?: return
        nodes.entrySet().forEach { (_, element) ->
            val node = element.asJsonObject
            val params = node["params"]?.asJsonObject
            when (node["type"]?.asString) {
                "WAIT" -> migrateTicksParam(params, "ticks", "seconds")
                "DISPLAY_TEXT" -> {
                    migrateTicksParam(params, "fadeIn", "fadeInSeconds")
                    migrateTicksParam(params, "stay", "staySeconds")
                    migrateTicksParam(params, "fadeOut", "fadeOutSeconds")
                }
            }
            migrateGraphTimes(node["snapshot"]?.asJsonObject)
        }
    }

    private fun migrateTicksParam(params: JsonObject?, oldKey: String, newKey: String) {
        val raw = params?.get(oldKey)?.asString ?: return
        val ticks = raw.toLongOrNull() ?: return
        // 旧形式のtick値は、整数秒へ切り上げると1tick=0.05秒の設定を失います。
        // 現行形式は小数秒を正本にできるため、丸めずに1tick単位の値へ移行します。
        val seconds = if (ticks <= 0L) {
            "0"
        } else {
            BigDecimal.valueOf(ticks)
                .divide(BigDecimal.valueOf(20L))
                .stripTrailingZeros()
                .toPlainString()
        }
        params.addProperty(newKey, seconds)
        params.remove(oldKey)
    }

    private fun quarantine(file: File, error: Exception) {
        val quarantine = dir.resolve("corrupt").also(File::mkdirs)
        val target = quarantine.resolve("${file.nameWithoutExtension}-${System.currentTimeMillis()}.json")
        runCatching { Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        logger.log(Level.WARNING, "構造化プログラムディスクを隔離しました: ${file.absolutePath}", error)
    }

    private fun validateRecursively(
        root: me.awabi2048.kantancommander.model.CommandGraph,
        limits: GraphLimits = this.limits,
    ): List<String> {
        val errors = mutableListOf<String>()
        val visited = Collections.newSetFromMap(
            IdentityHashMap<me.awabi2048.kantancommander.model.CommandGraph, Boolean>()
        )
        fun validate(graph: me.awabi2048.kantancommander.model.CommandGraph, path: String) {
            if (!visited.add(graph)) {
                errors += "$path: 別ディスクのコピー内容が循環参照しています"
                return
            }
            val graphErrors = GraphValidator.validate(graph, limits)
            graphErrors.forEach { errors += "$path: $it" }
            // 構造違反を含むグラフをレイアウトへ渡すと、巨大な座標や不正な参照を
            // 描画セルへ展開して保存処理自体が例外になる可能性があります。
            // 先に構造検証を通し、さらにセル数上限付きでレイアウトを検証します。
            if (graphErrors.isEmpty()) {
                runCatching {
                    GraphLayoutEngine.layout(graph, maxCells = layoutCellLimit(limits))
                }.onSuccess { layout ->
                    if (layout.width > limits.maximumMapWidth) {
                        errors += "$path: 描画幅が上限 ${limits.maximumMapWidth} を超えています"
                    }
                    if (layout.height > limits.maximumMapHeight) {
                        errors += "$path: 描画高さが上限 ${limits.maximumMapHeight} を超えています"
                    }
                }.onFailure { failure ->
                    errors += "$path: 描画レイアウトを生成できません: ${failure.message ?: failure::class.simpleName}"
                }
            }
            graph.nodes.values.forEach { node ->
                node.snapshot?.let { validate(it, "$path/${node.id}") }
            }
            visited.remove(graph)
        }
        validate(root, "root")
        return errors
    }

    private companion object {
        private const val LEGACY_TICK_FORMAT_VERSION = 6
        private const val LEGACY_COMMAND_FORMAT_VERSION = 7
        /** 保存データ検証で確保してよい描画セル数。入力JSONによるメモリ消費を制限します。 */
        private const val MAX_LAYOUT_VALIDATION_CELLS = 1_000_000L

        private fun layoutCellLimit(limits: GraphLimits): Long {
            val width = limits.maximumMapWidth.toLong().coerceAtLeast(1L)
            val height = limits.maximumMapHeight.toLong().coerceAtLeast(1L)
            val product = if (width > MAX_LAYOUT_VALIDATION_CELLS / height) {
                MAX_LAYOUT_VALIDATION_CELLS
            } else {
                width * height
            }
            return product.coerceAtMost(MAX_LAYOUT_VALIDATION_CELLS)
        }

        /** 構造違反と上限超過を区別するための、上限を実質無効化した検証用設定。 */
        private val UNBOUNDED_LIMITS = GraphLimits(
            maximumNodeCount = Int.MAX_VALUE,
            maximumMapWidth = Int.MAX_VALUE,
            maximumMapHeight = Int.MAX_VALUE,
            maximumBranchDepth = Int.MAX_VALUE,
        )
    }
}
