package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
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
import java.util.logging.Level
import java.util.logging.Logger

class ScriptStore(
    private val dir: File,
    private val logger: Logger,
    private val limits: GraphLimits = GraphLimits(),
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 保存済み内容の正本キャッシュ。レッドストーン監視のようにtick単位で参照される
     * [load]が、参照ごとにJSON解析と構造検証を繰り返さないようにするためのもの。
     * 正本は検証に通った時点の内容のみを採用し、[load]経由では独立コピーを渡す。
     */
    private val cache = mutableMapOf<UUID, DiskScript>()

    init {
        dir.mkdirs()
    }

    fun create(owner: UUID, name: String): DiskScript =
        DiskScript(name = name, owner = owner).also(::save)

    fun createPlacement(
        owner: UUID,
        name: String,
    ): DiskScript = DiskScript(name = name, owner = owner, listed = false).also(::save)

    fun copyForPlacement(source: DiskScript): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            createdAt = System.currentTimeMillis(),
            listed = false,
            graph = source.graph.deepCopy(),
        ).also(::save)

    fun copyForItem(source: DiskScript): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            createdAt = System.currentTimeMillis(),
            listed = false,
            graph = source.graph.deepCopy(),
        ).also(::save)

    fun copyToLibrary(source: DiskScript, owner: UUID, name: String = source.name): DiskScript =
        source.copy(
            id = UUID.randomUUID(),
            name = name,
            owner = owner,
            createdAt = System.currentTimeMillis(),
            listed = true,
            graph = source.graph.deepCopy(),
        ).also(::save)

    /** 保存済み内容の独立コピーを返す。呼び出し側が変更しても保存済み正本へ波及しない。 */
    fun load(id: UUID): DiskScript? = cached(id)?.deepCopy()

    fun save(script: DiskScript) {
        require(script.formatVersion == STRUCTURED_FORMAT_VERSION) { "unsupported script format" }
        val validation = validateRecursively(script.graph)
        require(validation.isEmpty()) { validation.joinToString("; ") }
        atomicWrite(file(script.id), gson.toJson(script))
        // 検証に通った時点の内容だけを正本として採用する。以後の呼び出し側変更は反映されない。
        cache[script.id] = script.deepCopy()
    }

    fun delete(id: UUID) {
        val target = file(id)
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("コマンドディスクを削除できません: ${target.absolutePath}")
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

    fun listOwned(owner: UUID): List<DiskScript> = listAll().filter { it.owner == owner && it.listed }

    private fun cached(id: UUID): DiskScript? {
        cache[id]?.let { return it }
        if (!file(id).isFile) return null
        val loaded = read(file(id)) ?: return null
        cache[id] = loaded
        return loaded
    }

    private fun DiskScript.deepCopy(): DiskScript = copy(graph = graph.deepCopy())

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
            throw IllegalStateException("コマンドディスクを原子的に保存できないファイルシステムです", failure)
        }
    }

    private fun read(file: File): DiskScript? = try {
        gson.fromJson(file.readText(Charsets.UTF_8), DiskScript::class.java)
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
                        "設定上限を超える保存済みコマンドディスクを読み込みました（隔離していません）: " +
                            "${file.absolutePath} (${limitViolations.joinToString("; ")})"
                    )
                }
            }
    } catch (error: Exception) {
        quarantine(file, error)
        null
    }

    private fun quarantine(file: File, error: Exception) {
        val quarantine = dir.resolve("corrupt").also(File::mkdirs)
        val target = quarantine.resolve("${file.nameWithoutExtension}-${System.currentTimeMillis()}.json")
        runCatching { Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        logger.log(Level.WARNING, "構造化コマンドディスクを隔離しました: ${file.absolutePath}", error)
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
