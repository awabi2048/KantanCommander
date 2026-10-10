package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.awabi2048.kantancommander.dsl.DslCompiler
import me.awabi2048.kantancommander.dsl.DslDefarg
import me.awabi2048.kantancommander.dsl.DslDiskLink
import me.awabi2048.kantancommander.dsl.DslNames
import me.awabi2048.kantancommander.dsl.DslParser
import me.awabi2048.kantancommander.dsl.DslPrinter
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskArgument
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.effectiveTrigger
import me.awabi2048.kantancommander.model.STRUCTURED_FORMAT_VERSION
import me.awabi2048.kantancommander.gui.GraphLayoutEngine
import java.io.File
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

enum class ScriptMutationOperation {
    UPDATE,
    SAVE,
    DELETE,
}

class ScriptMutationLockedException(
    val scriptId: UUID,
    val operation: ScriptMutationOperation,
) : IllegalStateException("プログラムはテスト実行中のため変更できません: script=$scriptId operation=$operation")

class ScriptStore(
    private val dir: File,
    private val logger: Logger,
    private val limits: GraphLimits = GraphLimits(),
    /**
     * DSL コンパイル時のワールド変数名→型の供給です。
     * プラグイン側は [WorldVariableStore.allDefinitionTypes] を渡します。
     */
    private val worldTypes: () -> Map<String, ValueType> = { emptyMap() },
    /** テスト実行中の通常の編集経路を遮断する共有ガードです。 */
    private val mutationGuard: (UUID, UUID?, ScriptMutationOperation) -> Unit = { _, _, _ -> },
    /** ロックを意図的に越えた管理経路を、実行中テストへ通知する共有境界です。 */
    private val bypassedMutationNotifier: (UUID, ScriptMutationOperation) -> Unit = { _, _ -> },
    /**
     * issue #7 の正本の削除を、イベント起動の登録解除へ伝える共有境界です。
     *
     * 履歴に残る間の関係解除では正本が残るため呼び出しません。
     * JSONとキャッシュを消した削除確定時だけ呼び出します。
     */
    private val onScriptDeleted: (UUID) -> Unit = {},
    /**
     * issue #16: 配置で使われている script の判定です。orphan 解放・回収で
     * 配置参照の正本を残すために使います。既定は参照なしであり、
     * プラグインが配置台帳の判定を注入します。
     */
    private val isPlacedScript: (UUID) -> Boolean = { false },
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val relationFile = dir.resolve("relations.json")
    private val libraryPrograms = linkedMapOf<UUID, LinkedHashSet<UUID>>()
    private val historyPrograms = linkedMapOf<UUID, LinkedHashSet<UUID>>()

    /**
     * 保存済み内容の正本キャッシュ。レッドストーン監視のようにtick単位で参照される
     * [load]が、参照ごとにJSON解析と構造検証を繰り返さないようにするためのもの。
     * 正本は検証に通った時点の内容のみを採用し、[load]経由では独立コピーを渡す。
     *
     * issue #16: LRU で [StorageLifecycle.MAX_CACHED_PROGRAMS] 件まで保持します。
     * 追い出しはメモリ複写を捨てるだけで disk が正本のため、保存内容が古い世代へ
     * 戻ることはありません。楽観排他の検出は cache 命中時に行うため、追い出し後は
     * 再起動直後と同じ扱い（disk 世代を基準にする）になります。
     */
    private val cache: MutableMap<UUID, DiskScript> =
        Collections.synchronizedMap(object : LinkedHashMap<UUID, DiskScript>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, DiskScript>): Boolean =
                size > StorageLifecycle.MAX_CACHED_PROGRAMS
        })
    /**
     * 隔離先へ移動できなかったファイルの負のキャッシュです。監視処理や一覧更新が
     * 同じ不正ファイルを毎回解析してログを繰り返さないようにします。ファイルの
     * 更新時刻またはサイズが変わった場合だけ、修復された可能性を確認して再読込します。
     */
    private val rejectedFiles = ConcurrentHashMap<UUID, RejectedFileFingerprint>()
    /**
     * 読み込み・変更・検証・保存を同じロックへ束ねます。ファイル置換だけを
     * atomicにしても、2つの画面が古いコピーを保存すれば後勝ちで変更を失うため、
     * プログラム単位のread-modify-write全体を直列化します。
     */
    private val scriptLocks = ConcurrentHashMap<UUID, ReentrantLock>()
    /** 関係JSONを複数の保存処理から同時に更新しても、片方の履歴を失わないためのロックです。 */
    private val relationLock = ReentrantLock()

    /**
     * コンパイル中の script の宣言部スタックです。
     * `call` が自分自身・相互参照するプログラムへ向く場合、対象のコンパイルが
     * まだ終わっていないため、宣言部（名前・引数）だけの暫定リンクで受理します。
     */
    private data class PendingDecl(val id: UUID, val name: String, val arguments: List<DiskArgument>)
    private val compiling = ThreadLocal.withInitial { mutableListOf<PendingDecl>() }

    init {
        dir.mkdirs()
        loadRelations()
        migrateLegacyListedPrograms()
        migrateLegacyJsonPrograms()
    }

    fun create(playerId: UUID, name: String): DiskScript =
        DiskScript(name = name).also {
            save(it)
            addToLibrary(playerId, it.id)
        }

    fun createPlacement(name: String): DiskScript = DiskScript(name = name).also(::save)

    /**
     * 埋め込みプログラム（アイテム同梱のスナップショット）を配置用に複製します。
     *
     * 同梱データは旧形式の場合があります。ファイル読込と同じく現行と直前2世代を
     * 受け付けて移行し、それ以外はnullを返します。保存・検証・入出力の失敗も
     * 含めて例外を逃がさず、呼び出し側は設置を正常に拒否します。
     * イベント処理から例外を逃がすと、バニラ配置物だけが残る壊れた設置になります。
     */
    fun copyForPlacement(source: DiskScript): DiskScript? = runCatching {
        val current = migrateEmbedded(source) ?: return null
        current.copy(
            id = UUID.randomUUID(),
            revision = 0L,
            graph = current.graph.deepCopy(),
        ).also(::save)
    }.getOrNull()

    /**
     * 埋め込みスナップショットを現行形式へ移行します。
     *
     * ファイル読込（[read]）と同じ受付方針です。現行18と直前3世代（17・16・15）を
     * 受け付け、それ以外は移行せずnullを返します。
     * 旧equipは [LegacyEquipMigration] で PLACE_ITEM へ、
     * 旧kind条件は [LegacyConditionMigration] で判定対象へ正本化するため、
     * 移行後の検証に通らない内容もnullとします。
     */
    private fun migrateEmbedded(source: DiskScript): DiskScript? = runCatching {
        val json = gson.toJsonTree(source).asJsonObject
        val version = json["formatVersion"]
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
            ?: return null
        val migrated = when (version) {
            STRUCTURED_FORMAT_VERSION -> json
            STRUCTURED_FORMAT_VERSION - 1 -> migrateStructuredV17ToV18(json)
            STRUCTURED_FORMAT_VERSION - 2 -> migrateStructuredV16ToV18(json)
            STRUCTURED_FORMAT_VERSION - 3 -> migrateStructuredV15ToV18(json)
            else -> return null
        }
        // issue #21: 旧装備操作を PLACE_ITEM へ正本化します。
        LegacyEquipMigration.normalizeGraph(migrated["graph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        // issue #27: 旧条件分類を判定対象へ正本化します。
        LegacyConditionMigration.normalizeGraph(migrated["graph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        // issue #10: 旧召喚時表示名を個別設定へ正本化します。
        LegacySummonMigration.normalizeGraph(migrated["graph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        // issue #32: 旧タグ操作を ENTITY_OPERATION へ正本化します。
        LegacyTagMigration.normalizeGraph(migrated["graph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        if (PersistedScriptValidator.validate(migrated).isNotEmpty()) return null
        val script = gson.fromJson(migrated, DiskScript::class.java) ?: return null
        // 旧JSONには arguments 欄が存在しません。Gsonの復元ではKotlinの
        // 非null境界が保証されないため、欠落時は空へ正規化します。
        @Suppress("SENSELESS_COMPARISON")
        if (script.arguments == null) {
            script.arguments = emptyList()
        }
        @Suppress("SENSELESS_COMPARISON")
        if (script.trigger == null) {
            script.trigger = ScriptTrigger()
        }
        if (script.formatVersion != STRUCTURED_FORMAT_VERSION) return null
        if (validateRecursively(script.graph, UNBOUNDED_LIMITS).isNotEmpty()) return null
        script
    }.getOrNull()

    fun copyForItem(source: DiskScript): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            revision = 0L,
            graph = source.graph.deepCopy(),
        ).also(::save)

    fun copyToLibrary(source: DiskScript, playerId: UUID, name: String = source.name): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            name = name,
            revision = 0L,
            graph = source.graph.deepCopy(),
        ).also {
            save(it)
            addToLibrary(playerId, it.id)
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
        mutationGuard(id, editorId, ScriptMutationOperation.UPDATE)
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
            mutationGuard(script.id, editorId, ScriptMutationOperation.SAVE)
            saveLocked(script, editorId)
        }
    }

    /** 管理コマンド等の明示的なバイパス経路です。保存成功後に共有通知を発行します。 */
    @Suppress("DEPRECATION")
    fun saveBypassingExecutionLock(script: DiskScript, editorId: UUID? = null) {
        withScriptLock(script.id) {
            saveLocked(script, editorId)
        }
        bypassedMutationNotifier(script.id, ScriptMutationOperation.SAVE)
    }

    @Suppress("DEPRECATION")
    private fun saveLocked(script: DiskScript, editorId: UUID?) {
        require(script.formatVersion == STRUCTURED_FORMAT_VERSION) { "unsupported script format" }
        require(script.revision >= 0L) { "unsupported negative script revision" }
        val validation = validateRecursively(script.graph)
        require(validation.isEmpty()) { validation.joinToString("; ") }
        val previous = cache[script.id]
        if (previous != null && script.revision != previous.revision) {
            throw java.util.ConcurrentModificationException(
                "プログラムディスクが別の編集で更新されています: id=${script.id} " +
                    "expected=${script.revision} actual=${previous.revision}",
            )
        }
        // 保存成功ごとに単調増加させ、入力画面開始時の正本世代と比較できるようにします。
        script.revision = (previous?.revision ?: script.revision).inc()
        // `.kcp`（純粋DSLテキスト）が正本です。折り畳みはGUIの表示状態なので
        // 展開してから正規形へ印刷し、コメントは文へ所属する形で保持します。
        val canonical = canonicalized(script)
        val text = canonicalText(canonical)
        val target = file(script.id)
        // 内容が変わらない保存ではファイルを書き換えません。外部エディタ由来の
        // 改行・時刻を保ち、mtime を基準とするツールとの無用な差分を防ぎます。
        if (!target.isFile || target.readText(Charsets.UTF_8) != text) {
            atomicWrite(target, text)
        }
        // 同名の旧JSONが残っていれば、移行済み入力として整理します。
        legacyJsonFile(script.id).takeIf { it.isFile }?.let(::archiveLegacyJson)
        // 検証に通った時点の内容だけを正本として採用する。以後の呼び出し側変更は反映されない。
        rejectedFiles.remove(script.id)
        cache[script.id] = canonical.deepCopy().also { it.revision = script.revision }
        editorId?.let { recordHistory(it, script.id) }
        if (previous != null && previous.name != script.name) {
            propagateDiskRename(script.id)
        }
    }

    fun delete(id: UUID) = withScriptLock(id) {
        // 保存と同じプログラムロック内で判定します。ガードの直後にテストが
        // 開始されても、削除だけが実行中テストをすり抜けないようにします。
        mutationGuard(id, null, ScriptMutationOperation.DELETE)
        deleteContentsLocked(id)
    }

    /** 管理コマンド等の明示的な削除バイパス経路です。削除成功後に共有通知を発行します。 */
    fun deleteBypassingExecutionLock(id: UUID) {
        withScriptLock(id) {
            deleteContentsLocked(id)
        }
        bypassedMutationNotifier(id, ScriptMutationOperation.DELETE)
    }

    private fun deleteContentsLocked(id: UUID) {
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
        if (isInHistory) return
        // `.kcp` 正本と残存する旧JSONの両方を消します。
        listOf(file(id), legacyJsonFile(id)).filter { it.exists() }.forEach { target ->
            if (!target.delete()) {
                throw IllegalStateException("プログラムディスクを削除できません: ${target.absolutePath}")
            }
        }
        cache.remove(id)
        rejectedFiles.remove(id)
        runCatching { onScriptDeleted(id) }.onFailure { failure ->
            logger.log(Level.WARNING, "スクリプト削除の通知に失敗しました: script=$id", failure)
        }
    }

    fun listAll(): List<DiskScript> {
        // `.kcp` を正本として先に処理し、同一IDの旧JSONは重複として扱いません。
        val files = dir.listFiles { file ->
            file.isFile && (file.extension.equals("kcp", true) || file.extension.equals("json", true))
        }.orEmpty().sortedBy { it.extension.equals("json", true) }
        val seen = mutableSetOf<UUID>()
        return files.mapNotNull { file ->
            val id = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()
                ?: return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            cached(id)?.deepCopy()?.to(sourceFile(id)?.lastModified() ?: file.lastModified())
        }
            .sortedBy(Pair<DiskScript, Long>::second)
            .map(Pair<DiskScript, Long>::first)
    }

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
            // issue #16: 履歴は上限まで保持し、超過分は古い方から外します。
            // 外れた記録の正本は、他関係・配置の参照がなければ解放します。
            while (entries.size > StorageLifecycle.MAX_HISTORY_PER_PLAYER) {
                val evicted = entries.first()
                entries.remove(evicted)
                releaseIfOrphan(evicted)
            }
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

    /**
     * issue #16: どの関係・配置からも参照されない正本ファイルを回収します。
     *
     * 実行中・編集中の script を GC しないよう、自動実行はしません。
     * 履歴上限による解放・起動時の整理・管理操作から明示的に呼び出します。
     * 回収した件数を返します。
     */
    fun pruneOrphans(): Int = relationLock.withLock {
        val referenced = libraryPrograms.values.flatten().toSet() +
            historyPrograms.values.flatten().toSet()
        var removed = 0
        dir.listFiles { file ->
            file.isFile && (file.extension.equals("kcp", true) || file.extension.equals("json", true))
        }
            ?.mapNotNull { file ->
                runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()?.to(file)
            }
            ?.groupBy(Pair<UUID, File>::first)
            ?.filterKeys { id -> id !in referenced && !isPlacedScript(id) }
            ?.forEach { (id, files) ->
                if (files.map(Pair<UUID, File>::second).all { it.delete() }) {
                    cache.remove(id)
                    rejectedFiles.remove(id)
                    removed++
                } else {
                    logger.log(Level.WARNING, "orphanプログラムの削除に失敗しました: script=$id")
                }
            }
        removed
    }

    /**
     * issue #16: 関係から外れた正本を、他参照がなければ解放します。
     *
     * 配置で使われている script・他 Player の関係に残る script は残します。
     * 呼び出し側は relationLock 内で呼ぶため、ここでは排他しません。
     */
    private fun releaseIfOrphan(id: UUID) {
        val referenced = libraryPrograms.values.any { id in it } ||
            historyPrograms.values.any { id in it } ||
            isPlacedScript(id)
        if (referenced) return
        listOf(file(id), legacyJsonFile(id)).filter { it.isFile }.forEach { target ->
            if (!target.delete()) {
                logger.log(Level.WARNING, "orphanプログラムの削除に失敗しました: file=${target.path}")
                return
            }
        }
        cache.remove(id)
        rejectedFiles.remove(id)
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
        // issue #16: 正本ファイルのない関係 ID は起動時に除外して自己修復します。
        // 履歴 tombstone は正本ファイルを残すため、ここで消えるのは外部改変分だけです。
        pruneDanglingRelations()
    }

    /**
     * issue #16: 関係 ID のうち正本ファイルを持たないものを除外します。
     * 呼び出し側は relationLock 内または起動直後の一時停止中に呼ぶため、
     * ここでは排他しません。
     */
    private fun pruneDanglingRelations() {
        var repaired = false
        (libraryPrograms.values + historyPrograms.values).forEach { ids ->
            if (ids.removeIf { sourceFile(it) == null }) repaired = true
        }
        libraryPrograms.entries.removeIf { it.value.isEmpty() }
        historyPrograms.entries.removeIf { it.value.isEmpty() }
        if (repaired) {
            writeRelationsLocked()
            logger.warning("正本のないライブラリ／履歴関係を除外しました")
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

    /**
     * listed=trueの旧形式を初回だけライブラリ関係へ移行します。
     *
     * モデルからは廃止した項目のため、保存JSONの欄を直接読み取ります。
     * 移行済みマーカーはJSONから除去して書き戻し、次回起動時に再登録しない
     * ようにします。
     */
    private fun migrateLegacyListedPrograms() {
        var changed = false
        dir.listFiles { file -> file.isFile && file.extension.equals("json", true) }
            ?.forEach { file ->
                val id = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull() ?: return@forEach
                // 起動時に全ファイルを検証するため、通常の読込・隔離経路を通します。
                // 読み込めない（隔離対象）ファイルはここで警告済みになります。
                cached(id) ?: return@forEach
                val root = runCatching {
                    JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
                }.getOrNull() ?: return@forEach
                val listed = root["listed"]?.takeIf { it.isJsonPrimitive }?.asBoolean == true
                if (!listed) return@forEach
                val owner = root["owner"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.let { runCatching { UUID.fromString(it.asString) }.getOrNull() }
                    ?: return@forEach
                val entries = libraryPrograms.getOrPut(owner) { linkedSetOf() }
                changed = entries.add(id) || changed
                root.remove("listed")
                atomicWrite(file, gson.toJson(root))
            }
        if (changed) writeRelations()
    }

    private fun <T> withScriptLock(id: UUID, action: () -> T): T =
        scriptLocks.computeIfAbsent(id) { ReentrantLock() }.withLock(action)

    private fun cached(id: UUID): DiskScript? {
        cache[id]?.let { return it }
        val target = sourceFile(id) ?: run {
            rejectedFiles.remove(id)
            return null
        }
        val fingerprint = RejectedFileFingerprint.from(target)
        rejectedFiles[id]?.let { rejected ->
            if (rejected == fingerprint) return null
            rejectedFiles.remove(id, rejected)
        }
        val loaded = read(target, id) ?: run {
            // 隔離・残置されたままのファイルはここに残ります。次回以降は同じ
            // ファイルの更新を検知するまで、読み込み・警告を繰り返しません。
            if (target.isFile) {
                rejectedFiles[id] = RejectedFileFingerprint.from(target)
            } else {
                rejectedFiles.remove(id)
            }
            return null
        }
        rejectedFiles.remove(id)
        cache[id] = loaded
        return loaded
    }

    private fun DiskScript.deepCopy(): DiskScript = copy(
        // グラフの独立コピーは CommandGraph.deepCopy が内部グラフも含めて行います。
        graph = graph.deepCopy(),
    )

    /** `.kcp`（純粋DSLテキスト）の正本ファイルです。 */
    private fun file(id: UUID) = dir.resolve("$id.kcp")

    /** 旧形式（`.json`）の移行専用入力ファイルです。 */
    private fun legacyJsonFile(id: UUID) = dir.resolve("$id.json")

    /** 読み込み元です。同一IDに双方があれば `.kcp` を優先します。 */
    private fun sourceFile(id: UUID): File? =
        file(id).takeIf { it.isFile } ?: legacyJsonFile(id).takeIf { it.isFile }

    /**
     * 保存・移行共通の正規化です。折り畳みノードはDSLで表現できないため
     * 展開し、起動方法は保存時と同じ規則で正規化します。引数の
     * 呼出し側（copyForItem 等）のモデルは変更しません。
     */
    private fun canonicalized(script: DiskScript): DiskScript =
        script.deepCopy().also { canonical ->
            unfoldAll(canonical.graph)
            canonical.trigger = canonical.trigger.normalized()
        }

    /** 正規形 `.kcp` テキストを生成します。 */
    private fun canonicalText(canonical: DiskScript): String =
        DslPrinter(::diskName).print(
            canonical.graph, canonical.name, canonical.effectiveTrigger(), canonical.arguments,
        )

    /**
     * グラフ内の折り畳みノードを展開します。折り畳みはGUIの表示状態であり、
     * 展開は意味を変えません。展開不能なノードが残る場合は例外とし、
     * 不完全な DSL を保存しません。
     */
    private fun unfoldAll(graph: CommandGraph) {
        repeat(FOLD_UNROLL_LIMIT) {
            val folded = graph.nodes.values.firstOrNull(FoldedGraphs::isFolded) ?: return
            require(GraphEditor.unfold(graph, folded.id)) {
                "折り畳みノードを展開できません: ${folded.id}"
            }
        }
        require(graph.nodes.values.none(FoldedGraphs::isFolded)) {
            "折り畳みノードの展開が収束しません"
        }
    }

    /**
     * 起動時の移行です。旧 `.json` を `.kcp` へ一度だけ変換します。
     * 変換成功した旧JSONは `converted-json/` へ退避し、変換できないものは
     * 次回起動まで残します（`call` 参照解決のため既存読込・隔離経路を通します）。
     */
    private fun migrateLegacyJsonPrograms() {
        dir.listFiles { file -> file.isFile && file.extension.equals("json", true) }
            ?.forEach { file ->
                val id = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()
                    ?: return@forEach
                if (this.file(id).isFile) {
                    // `.kcp` 正本が既にある旧JSONは残置物として整理します。
                    archiveLegacyJson(file)
                    return@forEach
                }
                val script = cached(id) ?: return@forEach
                val canonical = runCatching { canonicalized(script) }.getOrNull()
                val text = canonical?.let { runCatching { canonicalText(it) }.getOrNull() }
                if (text == null) {
                    logger.warning(
                        ".kcp への変換に失敗したため旧JSONを残します: ${file.absolutePath}"
                    )
                    return@forEach
                }
                runCatching { atomicWrite(this.file(id), text) }
                    .onSuccess {
                        archiveLegacyJson(file)
                        // 読込済みキャッシュをDSL正本の内容（展開・正規化済み）へ揃えます。
                        cache[id] = canonical
                    }
                    .onFailure { failure ->
                        logger.log(
                            Level.WARNING,
                            ".kcp への書き込みに失敗したため旧JSONを残します: ${file.absolutePath}",
                            failure,
                        )
                    }
            }
    }

    /** 変換済みの旧JSONを `converted-json/` へ退避します。 */
    private fun archiveLegacyJson(file: File) {
        val archive = dir.resolve(CONVERTED_DIR_NAME)
        if (!archive.isDirectory && !archive.mkdirs() && !archive.isDirectory) {
            logger.warning("旧JSONの退避先を準備できないため残します: ${file.absolutePath}")
            return
        }
        val target = uniqueQuarantineTarget(archive, file, fileTag = "json")
        runCatching { Files.move(file.toPath(), target.toPath()) }
            .onFailure { failure ->
                logger.log(
                    Level.WARNING,
                    "旧JSONを退避できないため残します: ${file.absolutePath}",
                    failure,
                )
            }
    }

    /**
     * ディスク名の変更を、`call "name"` で参照する全プログラムの `.kcp` 正本へ
     * 伝播します。DSL テキストはディスク名を正本として参照するため、保存済み
     * テキストだけを最新名へ書き換えます（グラフ・revision は変わりません）。
     */
    private fun propagateDiskRename(renamedId: UUID) {
        listAll().filter { candidate ->
            candidate.id != renamedId && candidate.graph.nodes.values.any {
                it.type == CommandType.DISK_CALL &&
                    it.string("diskId") == renamedId.toString()
            }
        }.forEach { caller ->
            withScriptLock(caller.id) {
                val text = runCatching { canonicalText(canonicalized(caller)) }
                    .getOrNull() ?: return@withScriptLock
                val target = file(caller.id)
                if (target.isFile && target.readText(Charsets.UTF_8) == text) return@withScriptLock
                atomicWrite(target, text)
            }
        }
    }

    /**
     * `call "<name>"` の対象ディスクを名前または UUID で解決します。
     * スナップショットには解決済み正本のグラフをそのまま使います。
     */
    private fun resolveDiskLink(name: String): DslDiskLink? {
        val byUuid = runCatching { UUID.fromString(name) }.getOrNull()
        // コンパイル中の自己・相互参照は宣言部だけの暫定リンクで受理します。
        // 実行は diskId 参照で行われるため、空のスナップショットで意味は保てます。
        compiling.get().firstOrNull { it.name == name || it.id == byUuid }?.let {
            return DslDiskLink(it.id, CommandGraph(), it.arguments)
        }
        // 名前参照は一意の場合だけ受理します。重複名の `call` は UUID 表記が
        // 書かれている前提であり、ここで任意解決すると誤った対象へ結び付きます。
        val target = if (byUuid != null) {
            cached(byUuid)
        } else {
            listAll().filter { it.name == name }.singleOrNull()
        } ?: return null
        compiling.get().firstOrNull { it.id == target.id }?.let {
            return DslDiskLink(it.id, CommandGraph(), it.arguments)
        }
        return DslDiskLink(target.id, target.graph, target.arguments)
    }

    /**
     * DISK_CALL の diskId を現在のプログラム名へ逆引きします（DSL 印刷用）。
     * 名前が一意でない対象は UUID 表記を返し、復元時の identity を保ちます。
     */
    private fun diskName(id: UUID): String? {
        val name = cached(id)?.name
            ?: compiling.get().firstOrNull { it.id == id }?.name
            ?: return id.toString()
        val unique = listAll().count { it.name == name } <= 1
        return if (unique) name else id.toString()
    }

    /** `program` の `defarg` 宣言をモデルの引数定義へ写像します。 */
    private fun declaredArguments(program: me.awabi2048.kantancommander.dsl.DslProgram): List<DiskArgument> =
        program.decls.filterIsInstance<DslDefarg>()
            .map { DiskArgument(name = DslNames.modelName(it.name), type = it.type) }

    /** issue #16: 原子書込は共通基盤へ委譲します。失敗意味は従来どおりです。 */
    private fun atomicWrite(target: File, content: String) {
        AtomicFileWriter.write(target, content, "プログラムディスク")
    }

    /**
     * ファイルを拡張子で読み分けます。`.kcp`（純粋DSLテキスト）が正本、
     * `.json` は v15-v18 の移行専用入力です。
     */
    private fun read(file: File, id: UUID): DiskScript? =
        if (file.extension.equals("kcp", true)) readDsl(file, id) else readJson(file)

    /**
     * `.kcp` テキストを解析・検証・低級化してモデルへ復元します。
     *
     * `.kcp` はユーザーが外部エディタで直接編集する正本であるため、読み込め
     * ないファイルは隔離せず警告を残してその場に置きます（修正→再読込で
     * 復旧します）。
     */
    private fun readDsl(file: File, id: UUID): DiskScript? = try {
        val program = DslParser.parse(file.readText(Charsets.UTF_8))
        // コンパイル中の自己・相互 call が宣言部を参照できるよう登録します。
        val pending = PendingDecl(id, program.name, declaredArguments(program))
        compiling.get() += pending
        val script = try {
            val compiled = DslCompiler(worldTypes(), ::resolveDiskLink).compile(program)
            DiskScript(
                id = id,
                name = program.name,
                graph = compiled.graph,
                trigger = compiled.trigger,
                arguments = compiled.arguments,
            )
        } finally {
            compiling.get().remove(pending)
        }
        // 構造そのものの違反は破損データとして未ロードとします。
        val structuralViolations = validateRecursively(script.graph, UNBOUNDED_LIMITS)
        require(structuralViolations.isEmpty()) { structuralViolations.joinToString("; ") }
        // config上限だけを超えるデータは、リロードでの上限引き下げ時に
        // 既存データを失わせないため、警告のみで読み込みを続行する。
        val limitViolations = validateRecursively(script.graph, limits)
        if (limitViolations.isNotEmpty()) {
            logger.warning(
                "設定上限を超える保存済みプログラムを読み込みました（隔離していません）: " +
                    "${file.absolutePath} (${limitViolations.joinToString("; ")})"
            )
        }
        script
    } catch (error: Exception) {
        logger.log(
            Level.WARNING,
            "プログラムを読み込めません（修正して再読込してください）: ${file.absolutePath}",
            error,
        )
        null
    }

    /** `.json`（v15-v18）を移行込みで読み込みます。受付対象外・破損は隔離します。 */
    private fun readJson(file: File): DiskScript? = try {
        val source = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
        val sourceVersion = source["formatVersion"]
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asString
            ?.toIntOrNull()
            ?: error("formatVersionがありません、または整数ではありません")
        // issue #4 の方針により、現行18と直前3世代（17・16・15）だけを受け付け、
        // それ以外は非対応形式として隔離します。旧形式の移行連鎖は保持せず、
        // 黙って別意味へ変換しません。
        // v17→v18はメタ欄の廃止と起動方法の trigger 一本化、v16→v18は保存キーの
        // 改名（召喚の種類 entity→summonEntity、配置先 destination→placeDestination）を
        // 加え、v15→v18は#30の粒子移行も併せて適用します。
        // v14以前は今回の受付対象外として隔離します。
        // 旧kind条件は [LegacyConditionMigration] で判定対象へ正本化します（#27）。
        // 旧equipは [LegacyEquipMigration] で PLACE_ITEM へ正本化します（#21）。
        // 同じ番号を異なる保存形式に再利用しません。
        val migrated = when (sourceVersion) {
            STRUCTURED_FORMAT_VERSION -> source
            STRUCTURED_FORMAT_VERSION - 1 -> migrateStructuredV17ToV18(source)
            STRUCTURED_FORMAT_VERSION - 2 -> migrateStructuredV16ToV18(source)
            STRUCTURED_FORMAT_VERSION - 3 -> migrateStructuredV15ToV18(source)
            else -> {
                quarantine(
                    file,
                    QuarantineKind.INCOMPATIBLE,
                    "formatVersion=$sourceVersion",
                    fileTag = "v$sourceVersion",
                )
                return null
            }
        }
        // issue #21: 旧装備操作を PLACE_ITEM へ正本化します。
        // バージョン移行とは独立した読込時正規化であり、現行v17を含む
        // 受付全世代へ適用します。新規保存は常に PLACE_ITEM だけを書きます。
        LegacyEquipMigration.normalizeGraph(migrated["graph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        // issue #27: 旧条件分類を判定対象へ正本化します。適用範囲は同じです。
        LegacyConditionMigration.normalizeGraph(migrated["graph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        // issue #10: 旧召喚時表示名を個別設定へ正本化します。
        LegacySummonMigration.normalizeGraph(migrated["graph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        // issue #32: 旧タグ操作を ENTITY_OPERATION へ正本化します。
        LegacyTagMigration.normalizeGraph(migrated["graph"]?.takeIf { it.isJsonObject }?.asJsonObject)
        val schemaViolations = PersistedScriptValidator.validate(migrated)
        require(schemaViolations.isEmpty()) {
            "保存データの型・enum検証に失敗しました: ${schemaViolations.joinToString("; ")}"
        }
        val script = gson.fromJson(migrated, DiskScript::class.java)
            ?: error("保存データをプログラムディスクへ復元できません")
        // issue #8: 旧JSONには arguments 欄が存在しません。Kotlinの非null境界は
        // Gsonのリフレクション復元では保証されないため、欠落時は空へ正規化します。
        // 要素の型・enum不正は PersistedScriptValidator で隔離済みのため、
        // ここでは欠落だけを扱い、意味の再解釈は行いません。
        @Suppress("SENSELESS_COMPARISON")
        if (script.arguments == null) {
            script.arguments = emptyList()
        }
        require(script.formatVersion == STRUCTURED_FORMAT_VERSION) {
            "移行後のプログラムディスク形式が不正です: ${script.formatVersion}"
        }
        // issue #27: 条件の判定対象は `subject` 欄が単一正本です。旧 `kind` 欄の
        // 変換は行わず、旧値は未設定として検証層で拒否します。
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
        // 旧JSONには trigger 欄が存在しない可能性があります（Gsonのリフレクション
        // 復元では非null境界が保証されないため、欠落時はレッドストーン既定へ
        // 正規化します）。意味の再解釈は行いません。
        @Suppress("SENSELESS_COMPARISON")
        if (script.trigger == null) {
            script.trigger = ScriptTrigger(kind = TriggerKind.REDSTONE)
        }
        script
    } catch (error: Exception) {
        quarantine(
            file,
            QuarantineKind.CORRUPT,
            "JSON解析または保存データ検証に失敗しました",
            error = error,
        )
        null
    }

    /**
     * v17をv18へ移行します。メタ欄の廃止と起動方法の trigger 一本化を行います。
     *
     * - 廃止欄 `owner` / `createdAt` / `listed` / `contentModified` / `dsl` /
     *   `activation` / `timer` / `collapsedForStartIds` / `collapsedConditionIds` を除去。
     * - 起動方法は旧 `activation` / `timer` から `trigger` 欄へ合成します。
     */
    private fun migrateStructuredV17ToV18(source: JsonObject): JsonObject {
        val migrated = source.deepCopy()
        migrated.addProperty("formatVersion", STRUCTURED_FORMAT_VERSION)
        migrateTriggerFields(migrated)
        return migrated
    }

    /**
     * v16をv18へ移行します。v17相当の保存キー改名（値保持）を加えます。
     *
     * - 召喚ノードの種類 `entity` → `summonEntity`（一時対象指定の同名キーと区別）。
     * - 配置ノードの配置先 `destination` → `placeDestination`（テレポート移動先と区別）。
     * - 明示設定の記録（configuredFields）も同じ置き換えを行います。
     *
     * グラフ内のsnapshot／foldedGraphへ退避した副グラフも同じ規則で移行します。
     */
    private fun migrateStructuredV16ToV18(source: JsonObject): JsonObject {
        val migrated = source.deepCopy()
        migrated.addProperty("formatVersion", STRUCTURED_FORMAT_VERSION)
        // issue #8: 旧JSONにない関数引数定義は空として移行し、欠落のまま復元しません。
        if (!migrated.has("arguments")) migrated.add("arguments", JsonArray())
        migrateRenamedFieldKeys(migrated["graph"]?.asJsonObject)
        migrateTriggerFields(migrated)
        return migrated
    }

    /**
     * v15をv18へ直接移行します。連鎖は残しません。
     *
     * #30の粒子移行に加えて、保存キー改名と trigger 一本化を適用します。
     * 現行の受付窓から外れているため呼び出されませんが、世代の境で再拡張
     * した場合に備えて移行手順だけは保持します。
     */
    private fun migrateStructuredV15ToV18(source: JsonObject): JsonObject {
        val migrated = source.deepCopy()
        migrated.addProperty("formatVersion", STRUCTURED_FORMAT_VERSION)
        if (!migrated.has("arguments")) migrated.add("arguments", JsonArray())
        migrateParticleGraph(migrated["graph"]?.asJsonObject)
        migrateRenamedFieldKeys(migrated["graph"]?.asJsonObject)
        migrateTriggerFields(migrated)
        return migrated
    }

    /**
     * 廃止した旧起動欄 `activation` / `timer` とメタ欄を除去し、
     * `trigger` 欄を合成します。合成規則は旧 [TriggersKt.effectiveTrigger] と
     * 同一です（timer が有効なら TIMER 優先、activation に応じて種別を選択）。
     * `trigger` 欄が既にある場合は合成せず既存値を維持します。
     */
    private fun migrateTriggerFields(script: JsonObject) {
        if (!script.has("trigger")) {
            // 旧欄からの読み替えは廃止前の effectiveTrigger と同一です。
            // `activation`（NEEDS_REDSTONE/ALWAYS_ACTIVE のenum文字列）は
            // 起動意味を持たず、タイマー有効なら TIMER、そうでなければ REDSTONE です。
            val timer = script["timer"]?.takeIf { it.isJsonObject }?.asJsonObject
            val timerEnabled = timer?.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
            val trigger = JsonObject()
            if (timerEnabled) {
                trigger.addProperty("kind", "TIMER")
                timer.get("intervalSeconds")?.takeIf { it.isJsonPrimitive }
                    ?.let { trigger.addProperty("intervalSeconds", it.asDouble) }
            } else {
                trigger.addProperty("kind", "REDSTONE")
            }
            script.add("trigger", trigger)
        }
        listOf(
            "owner", "createdAt", "listed", "contentModified", "dsl",
            "activation", "timer", "collapsedForStartIds", "collapsedConditionIds",
        ).forEach { script.remove(it) }
    }

    /**
     * v17の保存キー改名をグラフ全体へ適用します。
     *
     * 同名キーの意味衝突をなくすための純粋な改名であり、値の意味は変えません。
     * 新キーが既にある場合は旧値を破棄し、旧キーだけを除去します。
     */
    private fun migrateRenamedFieldKeys(graph: JsonObject?) {
        val nodes = graph?.get("nodes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val nodeIds = nodes.entrySet().map { it.key }.toList()
        nodeIds.forEach { nodeId ->
            val node = nodes[nodeId]?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val params = node["params"]?.takeIf { it.isJsonObject }?.asJsonObject
            if (params != null) {
                when (node["type"]?.takeIf { it.isJsonPrimitive }?.asString) {
                    "SUMMON_ENTITY" -> moveParamKey(params, "entity", "summonEntity")
                    "PLACE_ITEM" -> moveParamKey(params, "destination", "placeDestination")
                }
            }
            val nodeType = node["type"]?.takeIf { it.isJsonPrimitive }?.asString
            node["configuredFields"]?.takeIf { it.isJsonArray }?.asJsonArray?.let { marks ->
                val renamed = marks.mapNotNull { element ->
                    if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return@mapNotNull element.asString
                    when (Pair(nodeType, element.asString)) {
                        Pair("SUMMON_ENTITY", "entity") -> "summonEntity"
                        Pair("PLACE_ITEM", "destination") -> "placeDestination"
                        else -> element.asString
                    }
                }
                // configuredFieldsは集合であり順序を保持しないため、重複除去して書き戻します。
                val rewritten = com.google.gson.JsonArray()
                renamed.toSet().forEach { rewritten.add(it) }
                node.add("configuredFields", rewritten)
            }
            migrateRenamedFieldKeys(node["snapshot"]?.asJsonObject)
            migrateRenamedFieldKeys(node["foldedGraph"]?.asJsonObject)
        }
    }

    // 改名元の値を改名先へ引き継ぎます。新キーが既にある場合は旧値を破棄します。
    private fun moveParamKey(params: JsonObject, oldKey: String, newKey: String) {
        val old = params.remove(oldKey) ?: return
        if (!params.has(newKey)) params.add(newKey, old)
    }

    /**
     * #30の粒子移行をグラフ全体へ適用します。
     *
     * 旧 `PARTICLE` ノードの `particleDisplayMode`（大文字・前後空白を無視、欠落時は
     * POINT扱い）を読み分け、GEOMETRYだけを `PARTICLE_GEOMETRY` へ変換します。
     * POINT側は型を変えず、両者とも旧modeキーを除去します。設定値は保持が原則で、
     * 型固有でない側のキーだけを除去します（POINT→図形系除去、GEOMETRY→散布系除去）。
     */
    private fun migrateParticleGraph(graph: JsonObject?) {
        val nodes = graph?.get("nodes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val nodeIds = nodes.entrySet().map { it.key }.toList()
        nodeIds.forEach { nodeId ->
            val node = nodes[nodeId]?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val params = node["params"]?.takeIf { it.isJsonObject }?.asJsonObject
            if (node["type"]?.asString == "PARTICLE" && params != null) {
                val mode = params["particleDisplayMode"]
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    ?.uppercase()
                if (mode == "GEOMETRY") {
                    node.addProperty("type", "PARTICLE_GEOMETRY")
                    PARTICLE_SCATTER_KEYS.forEach(params::remove)
                } else {
                    PARTICLE_GEOMETRY_KEYS.forEach(params::remove)
                }
                params.remove("particleDisplayMode")
            }
            migrateParticleGraph(node["snapshot"]?.asJsonObject)
            migrateParticleGraph(node["foldedGraph"]?.asJsonObject)
        }
    }

    // v14以前の移行処理は削除しました。現行18と直前3世代（17・16・15）以外は
    // 読み込み時に隔離するため、到達不能な移行連鎖を残しません。

    private fun quarantine(
        file: File,
        kind: QuarantineKind,
        reason: String,
        fileTag: String = "invalid",
        error: Throwable? = null,
    ): Boolean {
        val quarantine = dir.resolve(kind.directoryName)
        if (!quarantine.isDirectory && !quarantine.mkdirs() && !quarantine.isDirectory) {
            logger.log(
                Level.WARNING,
                "構造化プログラムディスクを隔離できませんでした（隔離先を準備できません）: " +
                    "${file.absolutePath} reason=$reason",
                error,
            )
            return false
        }
        val target = uniqueQuarantineTarget(quarantine, file, fileTag)
        val moveFailure = runCatching {
            // 同名の隔離済みファイルを上書きしないことで、過去のデータを失わないようにします。
            Files.move(file.toPath(), target.toPath())
        }.exceptionOrNull()
        if (moveFailure == null) {
            val message = "構造化プログラムディスクを${kind.label}へ隔離しました: " +
                "${file.absolutePath} -> ${target.absolutePath} reason=$reason"
            if (error == null) logger.warning(message) else logger.log(Level.WARNING, message, error)
            return true
        }
        logger.log(
            Level.WARNING,
            "構造化プログラムディスクを${kind.label}へ隔離できませんでした: " +
                "${file.absolutePath} reason=$reason",
            moveFailure,
        )
        return false
    }

    private fun uniqueQuarantineTarget(directory: File, source: File, fileTag: String): File {
        val safeTag = fileTag.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
        val base = "${source.nameWithoutExtension}-$safeTag-${System.currentTimeMillis()}"
        var target = directory.resolve("$base.json")
        var suffix = 1
        while (target.exists()) {
            target = directory.resolve("$base-$suffix.json")
            suffix++
        }
        return target
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
                node.foldedGraph?.let { validate(it, "$path/${node.id}/folded") }
            }
            visited.remove(graph)
        }
        validate(root, "root")
        return errors
    }

    private companion object {
        /** 保存データ検証で確保してよい描画セル数。入力JSONによるメモリ消費を制限します。 */
        private const val MAX_LAYOUT_VALIDATION_CELLS = 1_000_000L

        /** 変換済みの旧JSONを退避するディレクトリ名です。 */
        private const val CONVERTED_DIR_NAME = "converted-json"

        /** 折り畳み展開の反復上限です。入れ子の折り畳みを逐段展開するための安全弁です。 */
        private const val FOLD_UNROLL_LIMIT = 10_000

        /**
         * #30移行で除去する散布系キーです。旧GEOMETRY→新PARTICLE_GEOMETRY時に除去します。
         *
         * 種類系（particle／particleData）は両型で共有するため除去しません。
         */
        private val PARTICLE_SCATTER_KEYS = setOf(
            "particleDeltaX",
            "particleDeltaY",
            "particleDeltaZ",
            "particleSpeed",
            "particleCount",
        )

        /**
         * #30移行で除去する図形系キーです。旧POINT→新PARTICLE時に除去します。
         *
         * 種類系（particle／particleData）は両型で共有するため除去しません。
         */
        private val PARTICLE_GEOMETRY_KEYS = setOf(
            "particleShape",
            "particleDensity",
            "geometryRadius",
            "geometryHeight",
            "geometryTurns",
            "geometryStartAngle",
            "geometryEndAngle",
            "geometryRotation",
            "geometryAxisYaw",
            "geometryAxisPitch",
            "geometryStep",
            "geometryWireframe",
        )

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

    private enum class QuarantineKind(val directoryName: String, val label: String) {
        CORRUPT("corrupt", "破損データ"),
        INCOMPATIBLE("incompatible", "非対応形式"),
    }

    private data class RejectedFileFingerprint(
        val lastModified: Long,
        val size: Long,
    ) {
        companion object {
            fun from(file: File) = RejectedFileFingerprint(file.lastModified(), file.length())
        }
    }
}
