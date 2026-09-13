package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.awabi2048.kantancommander.model.DiskScript
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
            // 旧形式の折り畳み表示状態は新形式で不使用のため引き継ぎません。
            // 折り畳み自体はノード種別としてグラフ複製に含まれます。
            collapsedForStartIds = null,
            collapsedConditionIds = null,
        ).also(::save)

    fun copyForItem(source: DiskScript): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            createdAt = System.currentTimeMillis(),
            listed = false,
            revision = 0L,
            graph = source.graph.deepCopy(),
            collapsedForStartIds = null,
            collapsedConditionIds = null,
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
            collapsedForStartIds = null,
            collapsedConditionIds = null,
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
        rejectedFiles.remove(script.id)
        cache[script.id] = script.deepCopy()
        editorId?.let { recordHistory(it, script.id) }
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
        val target = file(id)
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("プログラムディスクを削除できません: ${target.absolutePath}")
        }
        cache.remove(id)
        rejectedFiles.remove(id)
        runCatching { onScriptDeleted(id) }.onFailure { failure ->
            logger.log(Level.WARNING, "スクリプト削除の通知に失敗しました: script=$id", failure)
        }
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
        val target = file(id)
        if (!target.isFile) {
            rejectedFiles.remove(id)
            return null
        }
        val fingerprint = RejectedFileFingerprint.from(target)
        rejectedFiles[id]?.let { rejected ->
            if (rejected == fingerprint) return null
            rejectedFiles.remove(id, rejected)
        }
        val loaded = read(target) ?: run {
            // 隔離に失敗した場合だけ元ファイルが残ります。次回以降は同じファイルの
            // 更新を検知するまで、読み込み・隔離・警告を繰り返しません。
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
        // TimerSettingは可変データなので、グラフだけをコピーするとload()後の
        // タイマー編集が保存済みキャッシュへ直接波及し、差分追跡をすり抜けます。
        // グラフの独立コピーは CommandGraph.deepCopy が内部グラフも含めて行います。
        timer = timer.copy(),
        graph = graph.deepCopy(),
        // 旧形式の折り畳み表示状態は新形式で不使用のため引き継ぎません。
        collapsedForStartIds = null,
        collapsedConditionIds = null,
    )

    /**
     * 識別子・所有者・一覧公開状態・作成時刻を除いた利用者編集部分を比較します。
     * 折りたたみ表示は実行内容ではないため比較対象外とし、トグルだけで
     * ディスク内容ありとは判定しません。
     */
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
        val sourceVersion = source["formatVersion"]
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asString
            ?.toIntOrNull()
            ?: error("formatVersionがありません、または整数ではありません")
        // issue #4 の方針により、現行17と直前2世代（16・15）だけを受け付け、
        // それ以外は非対応形式として隔離します。旧形式の移行連鎖は保持せず、
        // 黙って別意味へ変換しません。
        // v16→v17は保存キーの改名（召喚の種類 entity→summonEntity、
        // 配置先 destination→placeDestination、値保持）を行います。
        // v15→v17は#30の粒子移行に加えて同じ改名を適用します（v15→v16相当）。
        // v14以前は今回の受付対象外として隔離します。
        // 旧kind条件・旧equipは変換せず、検証層で未設定・移行漏れとして拒否します（#27・#21方針）。
        // 同じ番号を異なる保存形式に再利用しません。
        val migrated = when (sourceVersion) {
            STRUCTURED_FORMAT_VERSION -> source
            STRUCTURED_FORMAT_VERSION - 1 -> migrateStructuredV16ToV17(source)
            STRUCTURED_FORMAT_VERSION - 2 -> migrateStructuredV15ToV17(source)
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
        // 旧形式は読み込み不可のため書き戻し移行は行いません。
        // 現行形式のみを正本として扱います。
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
     * v16をv17へ移行します。保存キーの改名（値保持）を行います。
     *
     * - 召喚ノードの種類 `entity` → `summonEntity`（一時対象指定の同名キーと区別）。
     * - 配置ノードの配置先 `destination` → `placeDestination`（テレポート移動先と区別）。
     * - 明示設定の記録（configuredFields）も同じ置き換えを行います。
     *
     * グラフ内のsnapshot／foldedGraphへ退避した副グラフも同じ規則で移行します。
     */
    private fun migrateStructuredV16ToV17(source: JsonObject): JsonObject {
        val migrated = source.deepCopy()
        migrated.addProperty("formatVersion", STRUCTURED_FORMAT_VERSION)
        // issue #8: 旧JSONにない関数引数定義は空として移行し、欠落のまま復元しません。
        if (!migrated.has("arguments")) migrated.add("arguments", JsonArray())
        migrateRenamedFieldKeys(migrated["graph"]?.asJsonObject)
        return migrated
    }

    /**
     * v15をv17へ直接移行します。v16を経由する連鎖は残しません。
     *
     * #30の粒子移行に加えて、v16→v17相当の保存キー改名を適用します。
     */
    private fun migrateStructuredV15ToV17(source: JsonObject): JsonObject {
        val migrated = source.deepCopy()
        migrated.addProperty("formatVersion", STRUCTURED_FORMAT_VERSION)
        // issue #8: 旧JSONにない関数引数定義は空として移行し、欠落のまま復元しません。
        if (!migrated.has("arguments")) migrated.add("arguments", JsonArray())
        migrateParticleGraph(migrated["graph"]?.asJsonObject)
        migrateRenamedFieldKeys(migrated["graph"]?.asJsonObject)
        return migrated
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

    // v14以前の移行処理は削除しました。現行17と直前2世代（16・15）以外は
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
