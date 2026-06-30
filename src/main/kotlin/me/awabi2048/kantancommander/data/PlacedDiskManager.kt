package me.awabi2048.kantancommander.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.model.BlockPlacement
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f

object PlacedDiskManager {
    private const val DEFAULT_DISPLAY_GLOWING = true

    private lateinit var placementsFile: File
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(UUID::class.java, UuidAdapter())
        .create()

    private val placements: MutableMap<String, BlockPlacement> = mutableMapOf()

    fun init(plugin: KantanCommanderPlugin) {
        placementsFile = File(plugin.dataFolder, "placements.json")
        loadFromFile()
    }

    fun place(placement: BlockPlacement) {
        placements[placement.locationKey()] = placement
        saveToFile()
    }

    fun remove(world: World, x: Int, y: Int, z: Int) {
        val key = locationKey(world.name, x, y, z)
        placements.remove(key)
        saveToFile()
    }

    fun findByLocation(world: World, x: Int, y: Int, z: Int): BlockPlacement? =
        placements[locationKey(world.name, x, y, z)]

    fun findByLocation(location: Location): BlockPlacement? =
        findByLocation(location.world, location.blockX, location.blockY, location.blockZ)

    fun findByDisk(uuid: UUID): List<BlockPlacement> =
        placements.values.filter { it.diskUUID == uuid }

    fun listAll(): List<BlockPlacement> = placements.values.toList()

    fun rebuildAllDisplays(plugin: KantanCommanderPlugin) {
        val iterator = placements.values.iterator()
        while (iterator.hasNext()) {
            val placement = iterator.next()
            val world = Bukkit.getWorld(placement.worldName) ?: continue
            val block = world.getBlockAt(placement.x, placement.y, placement.z)

            // NOTE_BLOCK が消えている配置は、保存データだけ残っている孤立状態なので削除する。
            if (block.type != org.bukkit.Material.NOTE_BLOCK) {
                iterator.remove()
                saveToFile()
                continue
            }

            val existing = world.getEntitiesByClass(BlockDisplay::class.java).find { entity ->
                entity.uniqueId == placement.displayEntityUUID
            }
            if (existing != null) continue

            // サーバー再起動や手動削除で表示だけ失われた場合は、配置データから再生成する。
            spawnDisplay(world, placement)
        }
    }

    fun spawnDisplay(world: World, placement: BlockPlacement): BlockDisplay {
        val loc = Location(world, placement.x + 0.5, placement.y + 0.005, placement.z + 0.5)
        val display = world.spawn(loc, BlockDisplay::class.java) { display ->
            display.block = Bukkit.createBlockData(org.bukkit.Material.COMMAND_BLOCK)
            display.setGlowing(KantanCommanderPlugin.instance.config.getBoolean("display.glowing", DEFAULT_DISPLAY_GLOWING))
            display.setBrightness(Display.Brightness(15, 15))
            display.transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                AxisAngle4f(0f, 0f, 0f, 1f),
                Vector3f(1f, 1f, 1f),
                AxisAngle4f(0f, 0f, 0f, 1f)
            )
            display.setGravity(false)
            display.setInvulnerable(true)
        }

        // 実際にスポーンした BlockDisplay の UUID で配置記録を永続化する。
        val updated = placement.copy(displayEntityUUID = display.uniqueId)
        placements[placement.locationKey()] = updated
        saveToFile()

        return display
    }

    fun removeDisplay(world: World, displayUUID: UUID) {
        val entity = world.getEntitiesByClass(BlockDisplay::class.java).find { it.uniqueId == displayUUID }
        entity?.remove()
    }

    fun getPlacementsFile(): File = placementsFile

    private fun loadFromFile() {
        if (!placementsFile.exists()) {
            placementsFile.parentFile.mkdirs()
            placementsFile.writeText("[]")
            return
        }
        try {
            val listType = object : TypeToken<List<BlockPlacement>>() {}.type
            val loaded: List<BlockPlacement> = gson.fromJson(placementsFile.readText(), listType) ?: emptyList()
            placements.clear()
            loaded.forEach { placements[it.locationKey()] = it }
        } catch (e: Exception) {
            KantanCommanderPlugin.instance.logger.warning("Failed to load placements: ${e.message}")
        }
    }

    private fun saveToFile() {
        placementsFile.writeText(gson.toJson(placements.values.toList()))
    }

    private fun locationKey(world: String, x: Int, y: Int, z: Int): String =
        "$world,$x,$y,$z"
}
