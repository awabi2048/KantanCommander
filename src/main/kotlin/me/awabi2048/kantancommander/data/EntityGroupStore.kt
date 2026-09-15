package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.awabi2048.kantancommander.model.SpatialVector
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.withLock

/**
 * issue #24 の Entity Group 永続ストアです。
 *
 * 群は名前を一意な参照キーとし、所属実体は UUID で保持します。
 * ワールド再起動後も所属・pivot・姿勢を復元できるよう、単一 JSON へ
 * 原子書込みします。保存形式が壊れている場合は `corrupt/` へ退避し、
 * 空の状態から再開します（PlacementStore と同一方針）。
 *
 * 異なるワールドの実体を同一群へ入れません。unload 中の所属は即座に
 * 削除せず、明示の CLEANUP でのみ orphan を除去します。
 */
class EntityGroupStore(
    private val file: File,
    private val logger: Logger = Logger.getLogger(EntityGroupStore::class.java.name),
) {
    data class MemberRecord(
        val entityId: UUID,
        /** 最終既知位置です。未解決所属の unload 判定に使います。 */
        val lastKnown: SpatialVector? = null,
    )

    data class GroupRecord(
        val id: UUID,
        val name: String,
        val worldName: String,
        val members: List<MemberRecord>,
        val pivot: SpatialVector,
        /** 群姿勢の度数 (x, y, z) です。[0, 360) へ正規化して保持します。 */
        val orientation: Triple<Double, Double, Double>,
        val createdAt: Long,
        val updatedAt: Long,
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val lock = ReentrantLock()
    private val groups = linkedMapOf<String, GroupRecord>()

    init {
        file.parentFile?.mkdirs()
        load()
    }

    fun get(name: String): GroupRecord? = lock.withLock { groups[name]?.copy(members = groups[name]!!.members.toList()) }

    fun exists(name: String): Boolean = lock.withLock { groups.containsKey(name) }

    fun names(): List<String> = lock.withLock { groups.keys.sorted() }

    fun all(): List<GroupRecord> = lock.withLock { groups.values.map { it.copy(members = it.members.toList()) } }

    /**
     * 群を新規作成します。同名群がある場合は上書きせず false を返します。
     * 保存失敗時はメモリへ反映しません。
     */
    fun create(record: GroupRecord): Boolean = lock.withLock {
        if (groups.containsKey(record.name)) return@withLock false
        groups[record.name] = record
        try {
            saveLocked()
            true
        } catch (failure: Throwable) {
            groups.remove(record.name)
            throw failure
        }
    }

    /**
     * 群を置換します。存在しない群は false を返します。
     * 保存失敗時は直前の記録へ戻します。
     */
    fun update(record: GroupRecord): Boolean = lock.withLock {
        val previous = groups[record.name] ?: return@withLock false
        groups[record.name] = record
        try {
            saveLocked()
            true
        } catch (failure: Throwable) {
            groups[record.name] = previous
            throw failure
        }
    }

    /**
     * 群を削除します。所属実体自体は削除しません。
     * 保存失敗時は直前の記録へ戻します。
     */
    fun delete(name: String): Boolean = lock.withLock {
        val previous = groups.remove(name) ?: return@withLock false
        try {
            saveLocked()
            true
        } catch (failure: Throwable) {
            groups[name] = previous
            throw failure
        }
    }

    private fun load() {
        lock.withLock {
            if (!file.isFile) return
            val parsed = try {
                JsonParser.parseString(file.readText())
            } catch (failure: Throwable) {
                quarantine(failure)
                return
            }
            val root = parsed as? JsonObject ?: run {
                quarantine(IllegalStateException("entity group store root is not an object"))
                return
            }
            val loaded = linkedMapOf<String, GroupRecord>()
            root.getAsJsonArray("groups")?.forEach { element ->
                val entry = element as? JsonObject ?: return@forEach
                val record = readRecord(entry) ?: return@forEach
                loaded[record.name] = record
            }
            groups.clear()
            groups.putAll(loaded)
        }
    }

    private fun readRecord(entry: JsonObject): GroupRecord? {
        val name = entry.get("name")?.asString ?: return null
        if (!NAME_PATTERN.matches(name)) return null
        val worldName = entry.get("world")?.asString?.takeIf(String::isNotBlank) ?: return null
        val pivot = entry.getAsJsonObject("pivot")?.let(::readVector) ?: return null
        val orientation = entry.getAsJsonObject("orientation")?.let {
            val x = it.get("x")?.asDouble?.takeIf(Double::isFinite) ?: return null
            val y = it.get("y")?.asDouble?.takeIf(Double::isFinite) ?: return null
            val z = it.get("z")?.asDouble?.takeIf(Double::isFinite) ?: return null
            Triple(x, y, z)
        } ?: return null
        val members = entry.getAsJsonArray("members")?.mapNotNull { element ->
            val member = element as? JsonObject ?: return@mapNotNull null
            val entityId = runCatching { UUID.fromString(member.get("entityId")?.asString) }.getOrNull()
                ?: return@mapNotNull null
            MemberRecord(entityId, member.getAsJsonObject("lastKnown")?.let(::readVector))
        } ?: return null
        val id = runCatching { UUID.fromString(entry.get("id")?.asString) }.getOrNull() ?: return null
        return GroupRecord(
            id = id,
            name = name,
            worldName = worldName,
            members = members,
            pivot = pivot,
            orientation = orientation,
            createdAt = entry.get("createdAt")?.asLong ?: 0L,
            updatedAt = entry.get("updatedAt")?.asLong ?: 0L,
        )
    }

    private fun readVector(entry: JsonObject): SpatialVector? {
        val x = entry.get("x")?.asDouble?.takeIf(Double::isFinite) ?: return null
        val y = entry.get("y")?.asDouble?.takeIf(Double::isFinite) ?: return null
        val z = entry.get("z")?.asDouble?.takeIf(Double::isFinite) ?: return null
        return SpatialVector(x, y, z)
    }

    private fun saveLocked() {
        val root = JsonObject()
        val array = JsonArray()
        groups.values.forEach { record ->
            val entry = JsonObject()
            entry.addProperty("id", record.id.toString())
            entry.addProperty("name", record.name)
            entry.addProperty("world", record.worldName)
            entry.add("pivot", writeVector(record.pivot))
            val orientation = JsonObject()
            orientation.addProperty("x", record.orientation.first)
            orientation.addProperty("y", record.orientation.second)
            orientation.addProperty("z", record.orientation.third)
            entry.add("orientation", orientation)
            val members = JsonArray()
            record.members.forEach { member ->
                val memberEntry = JsonObject()
                memberEntry.addProperty("entityId", member.entityId.toString())
                member.lastKnown?.let { memberEntry.add("lastKnown", writeVector(it)) }
                members.add(memberEntry)
            }
            entry.add("members", members)
            entry.addProperty("createdAt", record.createdAt)
            entry.addProperty("updatedAt", record.updatedAt)
            array.add(entry)
        }
        root.add("groups", array)
        atomicWrite(gson.toJson(root))
    }

    private fun writeVector(vector: SpatialVector): JsonObject = JsonObject().also {
        it.addProperty("x", vector.x)
        it.addProperty("y", vector.y)
        it.addProperty("z", vector.z)
    }

    /** issue #16: 原子書込は共通基盤へ委譲します。失敗意味は従来どおりです。 */
    private fun atomicWrite(content: String) {
        AtomicFileWriter.write(file, content, "Entity Group")
    }

    /**
     * issue #16: script から参照されない群を回収します。
     *
     * 実行中・編集中の群を GC しないよう、自動実行はしません。
     * [referenced] に含まれる群名は残し、それ以外を削除して名前を返します。
     */
    fun deleteUnreferenced(referenced: Set<String>): List<String> = lock.withLock {
        val removed = groups.keys.filter { it !in referenced }
        if (removed.isEmpty()) return@withLock emptyList()
        val previous = removed.associateWith { groups[it]!! }
        removed.forEach { groups.remove(it) }
        return@withLock try {
            saveLocked()
            removed
        } catch (failure: Throwable) {
            groups.putAll(previous)
            throw failure
        }
    }

    private fun quarantine(failure: Throwable) {
        logger.log(Level.WARNING, "Entity Groupの保存形式が壊れているため退避します: file=${file.path}", failure)
        runCatching {
            val directory = File(file.parentFile, "corrupt").also { it.mkdirs() }
            Files.move(
                file.toPath(),
                File(directory, "${file.nameWithoutExtension}-${System.currentTimeMillis()}.json").toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.onFailure { moveFailure ->
            logger.log(Level.WARNING, "Entity Groupの退避に失敗しました: file=${file.path}", moveFailure)
        }
        groups.clear()
    }

    companion object {
        /** 検証層と同じ形式を二重に持たず、片方だけ通過する事故を防ぎます。 */
        val NAME_PATTERN = Regex("[a-z0-9_-]{1,64}")
    }
}
