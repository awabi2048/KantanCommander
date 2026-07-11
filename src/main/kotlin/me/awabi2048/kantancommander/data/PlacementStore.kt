package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.logging.Level

class PlacementStore(private val plugin: KantanCommanderPlugin, private val file: File) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val type = object : TypeToken<List<PlacementDto>>() {}.type
    private val placements = linkedMapOf<String, DiskPlacement>()

    init {
        file.parentFile.mkdirs()
        load()
    }

    fun add(placement: DiskPlacement) {
        placements[placement.key] = placement
        save()
    }

    fun remove(world: World, x: Int, y: Int, z: Int): DiskPlacement? {
        val removed = placements.remove(key(world.name, x, y, z))
        save()
        return removed
    }

    fun find(location: Location): DiskPlacement? = find(location.world, location.blockX, location.blockY, location.blockZ)

    fun find(world: World?, x: Int, y: Int, z: Int): DiskPlacement? =
        world?.let { placements[key(it.name, x, y, z)] }

    fun findByScript(id: UUID): List<DiskPlacement> = placements.values.filter { it.scriptId == id }

    fun all(): List<DiskPlacement> = placements.values.toList()

    fun restoreDisplays() {
        placements.values.forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            val block = world.getBlockAt(placement.x, placement.y, placement.z)
            if (block.type != Material.NOTE_BLOCK) return@forEach
            placement.displayId = spawnDisplay(world, placement)
        }
        save()
    }

    fun spawnDisplay(world: World, placement: DiskPlacement): UUID {
        val loc = Location(world, placement.x + 0.5, placement.y + 0.05, placement.z + 0.5)
        val display = world.spawn(loc, BlockDisplay::class.java) {
            it.block = Bukkit.createBlockData(Material.COMMAND_BLOCK)
            it.isGlowing = plugin.config.getBoolean("display.glowing", true)
        }
        placement.displayId = display.uniqueId
        save()
        return display.uniqueId
    }

    fun removeDisplay(world: World, id: UUID?) {
        if (id == null) return
        world.entities.firstOrNull { it.uniqueId == id }?.remove()
    }

    fun removeAllDisplays() {
        placements.values.forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            removeDisplay(world, placement.displayId)
        }
    }

    private fun load() {
        if (!file.isFile) return
        try {
            val loaded = gson.fromJson<List<PlacementDto>>(file.readText(Charsets.UTF_8), type)
                ?: error("JSON root is null")
            val parsed = loaded.map { it.toModel() }
            if (parsed.map { it.key }.toSet().size != parsed.size) {
                error("duplicate placement coordinates")
            }
            parsed.forEach { placements[it.key] = it }
        } catch (error: Exception) {
            quarantine(error)
        }
    }

    private fun quarantine(error: Exception) {
        val quarantineDir = file.parentFile.resolve("corrupt")
        quarantineDir.mkdirs()
        val target = quarantineDir.resolve("${file.nameWithoutExtension}-${System.currentTimeMillis()}.json")
        runCatching { Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            .onFailure { moveError ->
                plugin.logger.log(Level.WARNING, "不正な設置データを隔離できませんでした: ${file.absolutePath}", moveError)
            }
        plugin.logger.log(Level.WARNING, "不正な設置データを隔離しました: ${file.absolutePath} (${error.message})", error)
    }

    private data class PlacementDto(
        val world: String?,
        val x: Int?,
        val y: Int?,
        val z: Int?,
        val scriptId: String?,
        val displayId: String?
    ) {
        fun toModel(): DiskPlacement {
            val worldName = world?.takeIf { it.isNotBlank() } ?: error("world is missing")
            val script = parseUuid(scriptId, "scriptId")
            val display = displayId?.let { parseUuid(it, "displayId") }
            return DiskPlacement(worldName, x ?: error("x is missing"), y ?: error("y is missing"), z ?: error("z is missing"), script, display)
        }

        private fun parseUuid(raw: String?, field: String): UUID =
            runCatching { UUID.fromString(raw ?: error("$field is missing")) }
                .getOrElse { error("invalid UUID in $field: $raw") }
    }

    private fun save() {
        file.writeText(gson.toJson(placements.values.toList()), Charsets.UTF_8)
    }

    private fun key(world: String, x: Int, y: Int, z: Int): String = "$world,$x,$y,$z"
}
