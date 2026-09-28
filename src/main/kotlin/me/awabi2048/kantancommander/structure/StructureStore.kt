package me.awabi2048.kantancommander.structure

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.structure.Structure
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import kotlin.concurrent.withLock

/**
 * issue #11 の建造物ストアです。
 *
 * 保存形式は Bukkit／Paper の [Structure]（バニラ構造物 `.nbt`）を単一正本とし、
 * プラグイン管理下のディレクトリだけを扱います。構造物名は検証層で
 * 英小文字・数字・アンダースコア・ハイフンに制限済みのため、パス連結による
 * ディレクトリ逸脱は起きません。
 *
 * 回転・反転は [Structure.place] がブロック状態・BlockEntity を一貫して
 * 変換するため、こちらで独自の座標変換を持ちません。設置はブロック更新を
 * 伴わない一括配置であり、BLOCK_OPERATION と同じく物理を発生させません。
 */
class StructureStore(private val logger: java.util.logging.Logger, private val directory: File) {
    /**
     * issue #16: ファイル置換を直列化するロックです。保存・除去の競合で
     * 一時ファイルが残留しないようにします。Bukkit の Structure 読書自体は
     * 呼び出し側スレッド（メイン）で行います。
     */
    private val lock = ReentrantLock()

    /**
     * 構造物の原点オフセットです。
     *
     * バニラ構造物形式（`.nbt`）は原点を保持しないため、保存時に指定された
     * 原点ブロックと範囲最小角の差分だけを別ファイル `origins.json` へ
     * 永続化します。設置時はこのオフセット分だけ平行移動し、指定ブロックが
     * 設置位置へ来るようにします。
     */
    private val origins = mutableMapOf<String, OriginOffset>()

    init {
        if (!directory.exists()) directory.mkdirs()
        loadOrigins()
    }

    /**
     * 保存済み建造物を読み込みます。存在しない・壊れている場合は null を返し、
     * 呼び出し側（実行・GUI）がそれぞれの文言で扱えるようにします。
     *
     * issue #16: 壊れたファイルは `corrupt/` へ退避し、次回以降の読み込み・
     * 一覧で繰り返し失敗しないようにします。
     */
    fun load(name: String): Structure? {
        val file = fileFor(name) ?: return null
        if (!file.isFile) return null
        return runCatching {
            Bukkit.getStructureManager().loadStructure(file)
        }.getOrElse { failure ->
            logger.log(Level.WARNING, "建造物の読み込みに失敗しました: name=$name", failure)
            quarantine(file)
            null
        }
    }

    fun exists(name: String): Boolean = fileFor(name)?.isFile == true

    /**
     * 保存済みの建造物名を列挙します。GUI候補用であり、存在しない・壊れた
     * ファイルはここでは検証しません（配置時に改めて検証します）。
     */
    fun list(): List<String> {
        val files = directory.listFiles { file -> file.isFile && file.extension == EXTENSION } ?: return emptyList()
        return files.mapNotNull { file ->
            file.nameWithoutExtension.takeIf(NAME_PATTERN::matches)
        }.sorted()
    }

    /**
     * 範囲を建造物として保存します。既存名は上書きします。
     *
     * [Structure.fill] は範囲の全ブロックを読み取るため、チェスト等の
     * BlockEntity 内容と看板テキストはそのまま保持されます。saveStructure は
     * 一時ファイルを作らないため、失敗時は書きかけのファイルが残り得ます。
     * それを避けるため、書き込み先を一時名に切り替えてから置換します。
     *
     * [origin] は設置時に基準位置へ来るブロックです。範囲内かつ同ワールドの
     * 場合だけ原点として記録し、未指定・範囲外の場合は最小角が原点になります。
     */
    fun save(name: String, from: Location, to: Location, origin: Location? = null): StructureSaveResult {
        val target = fileFor(name) ?: return StructureSaveResult.InvalidName
        val structure = Bukkit.getStructureManager().createStructure()
        val minX = minOf(from.x, to.x)
        val minY = minOf(from.y, to.y)
        val minZ = minOf(from.z, to.z)
        val sizeX = kotlin.math.abs(to.x - from.x).toInt() + 1
        val sizeY = kotlin.math.abs(to.y - from.y).toInt() + 1
        val sizeZ = kotlin.math.abs(to.z - from.z).toInt() + 1
        runCatching {
            // CraftStructure.fill は第2引数を排他的上端として扱うため、
            // 選択最大点そのものを渡すと同面選択（高さ0）で失敗します。
            // ブロック含みの最大点+1を上端として渡します。
            structure.fill(
                Location(from.world, minX, minY, minZ),
                Location(from.world, minX + sizeX, minY + sizeY, minZ + sizeZ),
                true,
            )
        }.onFailure { failure ->
            logger.log(Level.WARNING, "建造物の切り出しに失敗しました: name=$name", failure)
            return StructureSaveResult.Failed
        }

        // 原点は構造物ローカルのブロックオフセット（最小角からの差分）として記録します。
        val originOffset = origin
            ?.takeIf { it.world?.uid == from.world?.uid }
            ?.let {
                OriginOffset(
                    it.blockX - minX.toInt(),
                    it.blockY - minY.toInt(),
                    it.blockZ - minZ.toInt(),
                )
            }
            ?.takeIf { it.x in 0 until sizeX && it.y in 0 until sizeY && it.z in 0 until sizeZ }

        val temporary = File(target.parentFile, "${target.name}.tmp")
        return lock.withLock {
            runCatching {
                Bukkit.getStructureManager().saveStructure(temporary, structure)
                if (target.exists() && !target.delete()) error("failed to replace existing structure file")
                if (!temporary.renameTo(target)) {
                    // 一時ファイルを本体へ移せない環境（別ボリューム等）ではコピーで代替します。
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                if (originOffset != null) {
                    origins[name] = originOffset
                } else {
                    origins.remove(name)
                }
                saveOrigins()
            }.fold(
                onSuccess = { StructureSaveResult.Saved },
                onFailure = { failure ->
                    logger.log(Level.WARNING, "建造物の保存に失敗しました: name=$name", failure)
                    temporary.delete()
                    StructureSaveResult.Failed
                },
            )
        }
    }

    /**
     * 指定位置へ建造物を設置します。回転・反転は [Structure.place] に委ねます。
     *
     * Paper の [Structure.place] はブロック更新を伴わない一括配置であり、
     * 落下・伝播等の物理挙動を発生させません。これは BLOCK_OPERATION が
     * `setType(material, false)` で物理を抑制している方針と同じです。
     */
    fun place(
        name: String,
        location: Location,
        rotation: StructureRotation,
        mirror: Mirror,
    ): Boolean {
        val structure = load(name) ?: return false
        val size = structure.size
        // 保存時に指定された原点ブロックが設置位置へ来るよう平行移動します。
        val offset = transformedOffset(origins[name], size.blockX, size.blockZ, rotation, mirror)
        val base = location.clone().subtract(
            offset.x.toDouble(),
            offset.y.toDouble(),
            offset.z.toDouble(),
        )
        return runCatching {
            // palette=0（先頭パレット）／integrity=1.0（全ブロック設置）。
            // includeEntities=false とし、エンティティは建造物として複製しません。
            structure.place(base, false, rotation, mirror, 0, 1.0f, java.util.Random(0L))
        }.onFailure { failure ->
            logger.log(Level.WARNING, "建造物の設置に失敗しました: name=$name", failure)
        }.isSuccess
    }

    /**
     * 設置範囲の保護判定用に、建造物の大きさから対象範囲を求めます。
     * 実行層はこれを [me.awabi2048.kantancommander.execution.BlockOperationProtectionPolicy]
     * と組み合わせ、1座標でも保護対象なら全体を失敗させます。
     * 原点オフセット・回転・反転を [place] と同じ順で適用し、判定範囲を
     * 実際の設置範囲と一致させます。
     */
    fun boundsAt(
        name: String,
        location: Location,
        rotation: StructureRotation,
        mirror: Mirror = Mirror.NONE,
    ): StructureBounds? {
        val structure = load(name) ?: return null
        val size = structure.size
        val offset = transformedOffset(origins[name], size.blockX, size.blockZ, rotation, mirror)
        val (width, depth) = rotatedFootprint(size.blockX, size.blockZ, rotation)
        val baseX = location.blockX - offset.x
        val baseY = location.blockY - offset.y
        val baseZ = location.blockZ - offset.z
        return StructureBounds(
            world = location.world ?: return null,
            minX = baseX,
            minY = baseY,
            minZ = baseZ,
            maxX = baseX + width - 1,
            maxY = baseY + size.blockY - 1,
            maxZ = baseZ + depth - 1,
        )
    }

    /**
     * 原点オフセットを設置時の変形（反転→回転の順）へ適用します。
     *
     * 保存ローカル座標のオフセットは、バニラ構造物と同じ変換規則で
     * ワールド方向のオフセットへ変換します。未指定時は零です。
     */
    private fun transformedOffset(
        offset: OriginOffset?,
        sizeX: Int,
        sizeZ: Int,
        rotation: StructureRotation,
        mirror: Mirror,
    ): OriginOffset {
        if (offset == null) return OriginOffset(0, 0, 0)
        var x = offset.x
        var z = offset.z
        when (mirror) {
            Mirror.FRONT_BACK -> x = sizeX - 1 - x
            Mirror.LEFT_RIGHT -> z = sizeZ - 1 - z
            else -> Unit
        }
        val (rx, rz) = when (rotation) {
            StructureRotation.NONE -> x to z
            StructureRotation.CLOCKWISE_90 -> (sizeZ - 1 - z) to x
            StructureRotation.CLOCKWISE_180 -> (sizeX - 1 - x) to (sizeZ - 1 - z)
            StructureRotation.COUNTERCLOCKWISE_90 -> z to (sizeX - 1 - x)
        }
        return OriginOffset(rx, offset.y, rz)
    }

    /** 原点オフセットの永続化ファイルを読み込みます。壊れている場合は空として扱います。 */
    private fun loadOrigins() {
        val file = originsFile()
        if (!file.isFile) return
        runCatching {
            val root = JsonParser.parseString(file.readText()).asJsonObject
            root.entrySet().forEach { (name, element) ->
                val entry = element.asJsonObject
                if (NAME_PATTERN.matches(name)) {
                    origins[name] = OriginOffset(
                        entry.get("x").asInt,
                        entry.get("y").asInt,
                        entry.get("z").asInt,
                    )
                }
            }
        }.onFailure { failure ->
            logger.log(Level.WARNING, "建造物原点の読み込みに失敗しました: file=${file.path}", failure)
        }
    }

    /** 原点オフセットを永続化します。保存・除去と同じロック内で呼び出します。 */
    private fun saveOrigins() {
        val file = originsFile()
        val root = JsonObject()
        origins.toSortedMap().forEach { (name, offset) ->
            root.add(name, JsonObject().apply {
                addProperty("x", offset.x)
                addProperty("y", offset.y)
                addProperty("z", offset.z)
            })
        }
        runCatching {
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeText(GSON.toJson(root))
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }.onFailure { failure ->
            logger.log(Level.WARNING, "建造物原点の保存に失敗しました: file=${file.path}", failure)
        }
    }

    private fun originsFile(): File = File(directory, ORIGINS_FILE)

    /** 建造物名から保存ファイルを解決します。不正名は null を返します。 */
    private fun fileFor(name: String): File? {
        if (!NAME_PATTERN.matches(name)) return null
        return File(directory, "$name.$EXTENSION")
    }

    /**
     * issue #16: 壊れた保存ファイルを `corrupt/` へ退避します。
     * 他 store と同じく、繰り返し失敗と一覧汚染を防ぎます。
     */
    private fun quarantine(file: File) {
        logger.log(Level.WARNING, "建造物の保存形式が壊れているため退避します: file=${file.path}")
        runCatching {
            val corrupt = File(directory, "corrupt").also { it.mkdirs() }
            Files.move(
                file.toPath(),
                File(corrupt, "${file.nameWithoutExtension}-${System.currentTimeMillis()}.$EXTENSION").toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.onFailure { failure ->
            logger.log(Level.WARNING, "建造物の退避に失敗しました: file=${file.path}", failure)
        }
    }

    /**
     * issue #16: script から参照されない建造物を回収します。
     *
     * 実行中・編集中の asset を GC しないよう、自動実行はしません。
     * [referenced] に含まれる建造物名は残し、それ以外を削除して名前を返します。
     * 削除失敗分は残して次回に委ねます。
     */
    fun deleteUnreferenced(referenced: Set<String>): List<String> = lock.withLock {
        val removed = list().filter { it !in referenced }.filter { name ->
            val file = fileFor(name) ?: return@filter false
            if (file.delete()) true else {
                logger.log(Level.WARNING, "未参照建造物の削除に失敗しました: file=${file.path}")
                false
            }
        }
        if (removed.isNotEmpty()) {
            removed.forEach { origins.remove(it) }
            saveOrigins()
        }
        removed
    }

    internal companion object {
        /** 構造物の拡張子です。バニラの構造物形式（`.nbt`）に固定します。 */
        const val EXTENSION = "nbt"

        /** 原点オフセットの永続化ファイル名です。構造物と同じディレクトリへ置きます。 */
        const val ORIGINS_FILE = "origins.json"

        private val GSON = Gson()
        /** 検証層と同じ形式を二重に持たず、片方だけ通過する事故を防ぎます。 */
        val NAME_PATTERN = Regex("[a-z0-9_-]{1,64}")

        /** 90度単位の回転でXZ方向のフットプリントを入れ替えます。 */
        fun rotatedFootprint(width: Int, depth: Int, rotation: StructureRotation): Pair<Int, Int> =
            when (rotation) {
                StructureRotation.CLOCKWISE_90, StructureRotation.COUNTERCLOCKWISE_90 -> depth to width
                else -> width to depth
            }
    }
}

/** 構造物ローカルの原点オフセットです。保存範囲の最小角からのブロック差分です。 */
data class OriginOffset(val x: Int, val y: Int, val z: Int)

/** 保存結果です。呼び出し側が失敗理由ごとの文言を選べます。 */
enum class StructureSaveResult {
    Saved,
    InvalidName,
    Failed,
}

/** 建造物設置の保護判定・体積判定に使う範囲です。 */
data class StructureBounds(
    val world: org.bukkit.World,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
) {
    /** 建造物の体積です。BLOCK_OPERATION と同じ上限判定に使います。 */
    fun volume(): Long =
        (maxX.toLong() - minX + 1L) * (maxY.toLong() - minY + 1L) * (maxZ.toLong() - minZ + 1L)
}
