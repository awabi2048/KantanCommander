package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** 再起動・チャンクアンロードをまたいでKantan由来召喚数を数える永続台帳です。 */
class SummonedEntityTracker(
    private val plugin: KantanCommanderPlugin,
    private val file: File,
) : Listener {
    private val markerKey = NamespacedKey(plugin, "summoned_entity")
    private val scriptKey = NamespacedKey(plugin, "summoned_by_script")
    private val tracked = linkedMapOf<UUID, MutableSet<UUID>>()

    init {
        load()
        val recovered = plugin.server.worlds.flatMap(World::getEntities).count { recover(it) }
        if (recovered > 0) {
            runCatching { save() }.onFailure { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "再起動時に検出した召喚体台帳を保存できませんでした: count=$recovered",
                    failure,
                )
            }
        }
    }

    fun canSummon(worldId: UUID): Boolean =
        SummonLimitPolicy.canSummon(
            tracked[worldId].orEmpty().size,
            tracked.values.sumOf(Set<UUID>::size),
            plugin.config.getInt("execution.max-summoned-entities-per-world"),
            plugin.config.getInt("execution.max-summoned-entities-server"),
        )

    fun register(entity: Entity, scriptId: UUID) {
        check(canSummon(entity.world.uid)) { "Kantan召喚数が上限へ到達しています" }
        entity.persistentDataContainer.set(markerKey, PersistentDataType.BYTE, 1)
        entity.persistentDataContainer.set(scriptKey, PersistentDataType.STRING, scriptId.toString())
        val worldEntities = tracked.getOrPut(entity.world.uid, ::linkedSetOf)
        worldEntities.add(entity.uniqueId)
        try {
            save()
        } catch (failure: Throwable) {
            // 台帳保存に失敗した召喚体を「追跡済み」として残すと、再起動後に
            // 上限計算だけが壊れるため、登録を取り消して呼び出し側へ返します。
            worldEntities.remove(entity.uniqueId)
            if (worldEntities.isEmpty()) tracked.remove(entity.world.uid)
            entity.persistentDataContainer.remove(markerKey)
            entity.persistentDataContainer.remove(scriptKey)
            throw failure
        }
    }

    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        val added = mutableListOf<Pair<UUID, UUID>>()
        event.entities.forEach { entity ->
            if (isTracked(entity)) {
                val ids = tracked.getOrPut(entity.world.uid, ::linkedSetOf)
                if (ids.add(entity.uniqueId)) added += entity.world.uid to entity.uniqueId
            }
        }
        if (added.isEmpty()) return
        runCatching { save() }.onFailure { failure ->
            // チャンク読込時の台帳保存に失敗した場合も、メモリだけ新しい数を
            // 数え続けないよう追加分を巻き戻します。次の読込で再試行できます。
            added.forEach { (worldId, entityId) ->
                tracked[worldId]?.remove(entityId)
                if (tracked[worldId].isNullOrEmpty()) tracked.remove(worldId)
            }
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "召喚体台帳のチャンク読込更新を保存できませんでした",
                failure,
            )
        }
    }

    @EventHandler
    fun onEntityRemove(event: EntityRemoveEvent) {
        // UNLOADは実体が存続するため台帳へ残し、死亡・デスポーン・明示削除だけを解放します。
        if (event.cause == EntityRemoveEvent.Cause.UNLOAD || !isTracked(event.entity)) return
        val worldId = event.entity.world.uid
        val entityId = event.entity.uniqueId
        if (tracked[worldId]?.remove(entityId) != true) return
        if (tracked[worldId].isNullOrEmpty()) tracked.remove(worldId)
        runCatching { save() }.onFailure { failure ->
            // 保存失敗時は次回復帰や上限計算のために台帳上のIDを戻します。
            tracked.getOrPut(worldId, ::linkedSetOf).add(entityId)
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "召喚体台帳の削除を保存できませんでした: entity=$entityId",
                failure,
            )
        }
    }

    private fun recover(entity: Entity): Boolean {
        if (!isTracked(entity)) return false
        return tracked.getOrPut(entity.world.uid, ::linkedSetOf).add(entity.uniqueId)
    }

    private fun isTracked(entity: Entity) =
        entity.persistentDataContainer.get(markerKey, PersistentDataType.BYTE) == 1.toByte()

    private fun load() {
        if (!file.isFile) return
        file.readLines(Charsets.UTF_8).forEach { line ->
            val parts = line.split(',', limit = 2)
            val world = parts.getOrNull(0)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@forEach
            val entity = parts.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@forEach
            tracked.getOrPut(world, ::linkedSetOf).add(entity)
        }
    }

    private fun save() {
        file.parentFile.mkdirs()
        val temporary = file.resolveSibling("${file.name}.tmp")
        temporary.writeText(
            tracked.flatMap { (world, entities) -> entities.map { "$world,$it" } }.joinToString("\n"),
            Charsets.UTF_8,
        )
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

object SummonLimitPolicy {
    fun canSummon(worldCount: Int, serverCount: Int, perWorldLimit: Int = 256, serverLimit: Int = 2048) =
        worldCount < perWorldLimit && serverCount < serverLimit
}
