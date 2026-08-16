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
        plugin.server.worlds.flatMap(World::getEntities).forEach(::recover)
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
        tracked.getOrPut(entity.world.uid, ::linkedSetOf).add(entity.uniqueId)
        save()
    }

    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        var changed = false
        event.entities.forEach { entity ->
            if (isTracked(entity)) changed = tracked.getOrPut(entity.world.uid, ::linkedSetOf).add(entity.uniqueId) || changed
        }
        if (changed) save()
    }

    @EventHandler
    fun onEntityRemove(event: EntityRemoveEvent) {
        // UNLOADは実体が存続するため台帳へ残し、死亡・デスポーン・明示削除だけを解放します。
        if (event.cause == EntityRemoveEvent.Cause.UNLOAD || !isTracked(event.entity)) return
        if (tracked[event.entity.world.uid]?.remove(event.entity.uniqueId) == true) save()
    }

    private fun recover(entity: Entity) {
        if (isTracked(entity)) tracked.getOrPut(entity.world.uid, ::linkedSetOf).add(entity.uniqueId)
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
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

object SummonLimitPolicy {
    fun canSummon(worldCount: Int, serverCount: Int, perWorldLimit: Int = 256, serverLimit: Int = 2048) =
        worldCount < perWorldLimit && serverCount < serverLimit
}
