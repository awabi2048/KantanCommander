package me.awabi2048.kantancommander.data

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.placement.PlacedBlockMaterials
import me.awabi2048.kantancommander.util.KcI18n
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.logging.Level
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f

class PlacementStore(private val plugin: KantanCommanderPlugin, private val file: File) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val type = object : TypeToken<List<PlacementDto>>() {}.type
    private val placements = linkedMapOf<String, DiskPlacement>()

    init {
        file.parentFile.mkdirs()
        load()
    }

    fun add(placement: DiskPlacement) {
        val previous = placements.put(placement.key, placement)
        try {
            save()
        } catch (failure: Throwable) {
            // ディスクへ書けなかった場合はメモリだけ先に進めない。次の操作が
            // 壊れた配置を参照しないよう、直前の値へ戻してから例外を伝播します。
            if (previous == null) placements.remove(placement.key) else placements[placement.key] = previous
            throw failure
        }
    }

    fun remove(world: World, x: Int, y: Int, z: Int): DiskPlacement? {
        val placementKey = key(world.name, x, y, z)
        val removed = placements.remove(placementKey) ?: return null
        try {
            save()
            plugin.forgetActivationState(removed.key, removed.scriptId)
        } catch (failure: Throwable) {
            placements[placementKey] = removed
            throw failure
        }
        return removed
    }

    fun find(location: Location): DiskPlacement? = find(location.world, location.blockX, location.blockY, location.blockZ)

    /**
     * 台帳に登録された配置かどうかだけを確認します。findと違ってスクリプト欠損時の
     * 台帳修復を行わないため、ブロック形状の再計算中にも副作用なく利用できます。
     */
    fun isRegistered(world: World, x: Int, y: Int, z: Int): Boolean =
        placements.containsKey(key(world.name, x, y, z))

    fun find(world: World?, x: Int, y: Int, z: Int): DiskPlacement? {
        world ?: return null
        val placementKey = key(world.name, x, y, z)
        val placement = placements[placementKey] ?: return null
        if (plugin.scripts.load(placement.scriptId) != null) return placement
        placements.remove(placementKey)
        runCatching { save() }.getOrElse { failure ->
            placements[placementKey] = placement
            plugin.logger.log(
                Level.WARNING,
                "参照切れ配置の台帳更新に失敗しました: placement=${placement.key}",
                failure,
            )
            return null
        }
        plugin.forgetActivationState(placement.key, placement.scriptId)
        removeDisplay(world, placement.displayId)
        return null
    }

    fun findByScript(id: UUID): List<DiskPlacement> = placements.values.filter { it.scriptId == id }

    fun refreshDisplaysForScript(id: UUID) {
        findByScript(id).forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            plugin.scripts.load(id)?.let { script ->
                world.getBlockAt(placement.x, placement.y, placement.z)
                    .setType(PlacedBlockMaterials.forTimer(script.timer.enabled), false)
            }
            // 新しい表示体を保存できてから古い表示体を消します。先に古い体を
            // 消すと、表示体スポーン／台帳保存の失敗時に配置だけが残ります。
            val oldDisplayId = placement.displayId
            runCatching { spawnDisplay(world, placement) }
                .onSuccess { removeDisplay(world, oldDisplayId) }
                .onFailure { failure ->
                    plugin.logger.log(
                        Level.WARNING,
                        "配置表示の更新に失敗しました: placement=${placement.key}",
                        failure,
                    )
                }
        }
    }

    fun all(): List<DiskPlacement> = placements.values.toList()

    fun removeWorld(worldName: String): List<DiskPlacement> {
        val removed = placements.values.filter { it.world == worldName }
        if (removed.isEmpty()) return emptyList()
        // ワールド削除イベントでは台帳保存を先に確定します。保存失敗時に
        // activation状態だけを先に捨てると、再試行時に残存配置が実行不能に
        // なるため、メモリとランタイム状態を原子的に更新します。
        removed.forEach { placements.remove(it.key) }
        try {
            save()
        } catch (failure: Throwable) {
            removed.forEach { placements[it.key] = it }
            throw failure
        }
        removed.forEach { plugin.forgetActivationState(it.key, it.scriptId) }
        return removed
    }

    fun restoreDisplays() {
        val stale = mutableListOf<String>()
        placements.values.toList().forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            if (plugin.scripts.load(placement.scriptId) == null) {
                removeDisplay(world, placement.displayId)
                stale += placement.key
                return@forEach
            }
            val block = world.getBlockAt(placement.x, placement.y, placement.z)
            if (!PlacedBlockMaterials.isPlacedBlock(block.type)) {
                removeDisplay(world, placement.displayId)
                stale += placement.key
                return@forEach
            }
            val oldDisplayId = placement.displayId
            runCatching { spawnDisplay(world, placement) }
                .onSuccess { removeDisplay(world, oldDisplayId) }
                .onFailure { failure ->
                    plugin.logger.log(
                        Level.WARNING,
                        "配置表示の復元に失敗しました: placement=${placement.key}",
                        failure,
                    )
                }
        }
        stale.forEach { key ->
            placements.remove(key)?.let { removed ->
                plugin.forgetActivationState(removed.key, removed.scriptId)
                // 台帳を消した後に再計算し、実体が残っている場合も拡張ブロック向けの
                // 特殊接続を通常のバニラ形状へ戻します。
                Bukkit.getWorld(removed.world)?.let { world ->
                    plugin.refreshRedstoneTopologyAround(
                        world.getBlockAt(removed.x, removed.y, removed.z),
                    )
                }
            }
        }
        save()
    }

    fun spawnDisplay(world: World, placement: DiskPlacement): UUID {
        val loc = Location(world, placement.x + 0.5, placement.y + 0.05, placement.z + 0.5)
        val display = world.spawn(loc, BlockDisplay::class.java) {
            val script = plugin.scripts.load(placement.scriptId)
            val displayMaterial = if (script?.timer?.enabled == true) Material.REPEATING_COMMAND_BLOCK else Material.COMMAND_BLOCK
            it.block = Bukkit.createBlockData(displayMaterial)
            it.isGlowing = false
            // 設置後実体の名称を拡張コマンドブロックへ統一する（浮遊表示はせず識別用に保持する）。
            it.customName(Component.text(
                KcI18n.text(null, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_NAME_BLOCK),
                NamedTextColor.AQUA,
            ))
            it.addScoreboardTag(DISPLAY_TAG)
            it.transformation = Transformation(
                Vector3f(-0.375f, 0.125f, -0.375f),
                AxisAngle4f(),
                Vector3f(0.75f, 0.75f, 0.75f),
                AxisAngle4f(),
            )
        }
        val previousDisplayId = placement.displayId
        placement.displayId = display.uniqueId
        try {
            save()
        } catch (failure: Throwable) {
            // 表示体だけが残ると、次回の復元で孤児Entityになります。
            placement.displayId = previousDisplayId
            display.remove()
            throw failure
        }
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
        val facing: String?,
        val displayId: String?
    ) {
        fun toModel(): DiskPlacement {
            val worldName = world?.takeIf { it.isNotBlank() } ?: error("world is missing")
            val script = parseUuid(scriptId, "scriptId")
            val display = displayId?.let { parseUuid(it, "displayId") }
            val direction = facing?.takeIf { it.isNotBlank() } ?: error("facing is missing")
            return DiskPlacement(worldName, x ?: error("x is missing"), y ?: error("y is missing"), z ?: error("z is missing"), script, direction, display)
        }

        private fun parseUuid(raw: String?, field: String): UUID =
            runCatching { UUID.fromString(raw ?: error("$field is missing")) }
                .getOrElse { error("invalid UUID in $field: $raw") }
    }

    private fun save() {
        val temporary = file.resolveSibling("${file.name}.tmp")
        temporary.writeText(gson.toJson(placements.values.toList()), Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (failure: AtomicMoveNotSupportedException) {
            // 正本を通常置換へ落とすと、プロセス停止／IO障害の瞬間に
            // placements.jsonが失われるため、非原子的なフォールバックは行いません。
            runCatching { Files.deleteIfExists(temporary.toPath()) }
            throw IllegalStateException("配置台帳を原子的に保存できないファイルシステムです", failure)
        }
    }

    private fun key(world: String, x: Int, y: Int, z: Int): String = "$world,$x,$y,$z"

    companion object {
        const val DISPLAY_TAG = "kantan_commander_display"
    }
}
