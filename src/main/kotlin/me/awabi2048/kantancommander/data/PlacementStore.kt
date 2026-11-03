package me.awabi2048.kantancommander.data

import com.awabi2048.ccsystem.CCSystem
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.TriggerKind
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
    private var chunks: Map<Triple<String, Int, Int>, List<DiskPlacement>> = emptyMap()

    init {
        file.parentFile.mkdirs()
        load()
        rebuildChunkIndex()
    }

    fun add(placement: DiskPlacement) {
        val previous = placements.put(placement.key, placement)
        try {
            save()
            // issue #7: 配置の追加をイベント起動の登録へ反映します。
            plugin.refreshEventTriggerIndex()
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
            // issue #7: 制御ブロック撤去時にイベント起動の登録を解除します。
            plugin.refreshEventTriggerIndex()
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

    /**
     * 台帳上の配置をスクリプトの読込可否に関わらず返します。
     * 破壊・占有確認など「登録されている実体か」だけを扱う経路に使い、
     * スクリプトを利用する経路は [find] を使います。
     */
    fun registered(world: World?, x: Int, y: Int, z: Int): DiskPlacement? =
        world?.let { placements[key(it.name, x, y, z)] }

    fun find(world: World?, x: Int, y: Int, z: Int): DiskPlacement? {
        world ?: return null
        val placementKey = key(world.name, x, y, z)
        val placement = placements[placementKey] ?: return null
        if (plugin.scripts.load(placement.scriptId) != null) return placement
        // 参照先ファイルが残っている間は、破損・読込失敗を一時的なものとして保持します。
        // 台帳だけを消すと実体ブロックを残して管理を失うため、配置は休止として残し、
        // 浮遊表示だけを撤去します。
        if (plugin.scripts.isPersisted(placement.scriptId)) {
            removeDisplay(world, placement.displayId)
            return null
        }
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
        // issue #7: 参照切れ配置の除去をイベント起動の登録へ反映します。
        plugin.refreshEventTriggerIndex()
        removeDisplay(world, placement.displayId)
        return null
    }

    fun findByScript(id: UUID): List<DiskPlacement> = placements.values.filter { it.scriptId == id }

    fun refreshDisplaysForScript(id: UUID) {
        findByScript(id).forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            plugin.scripts.load(id)?.let { script ->
                world.getBlockAt(placement.x, placement.y, placement.z)
                    .setType(PlacedBlockMaterials.forTimer(script.trigger.normalized().kind == TriggerKind.TIMER), false)
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

    /** 台帳変更を確定した境界で更新し、チャンクイベントの全件走査を避けます。 */
    internal fun rebuildChunkIndex() {
        chunks = placements.values.groupBy { Triple(it.world, it.x shr 4, it.z shr 4) }
    }

    internal fun inChunk(world: String, x: Int, z: Int): List<DiskPlacement> =
        chunks[Triple(world, x, z)].orEmpty()

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
        // issue #7: ワールド削除に伴う配置除去をイベント起動の登録へ反映します。
        plugin.refreshEventTriggerIndex()
        return removed
    }

    fun restoreDisplays() {
        val stale = mutableListOf<String>()
        placements.values.toList().forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            // 未ロードチャンクはブロック読取りによる同期ロードを起こさず、
            // 復元を ChunkLoadEvent 側へ委ねます。起動直後にイベント登録済みの
            // リスナー経由でスクリプト読込が連鎖する経路をここで断ちます。
            if (!world.isChunkLoaded(placement.x shr 4, placement.z shr 4)) return@forEach
            restoreDisplayFor(world, placement, stale)
        }
        cullStale(stale)
        save()
    }

    /**
     * ChunkLoadEvent でロードが確定したチャンク内の配置を復元します。
     * 起動時に未ロードだった配置の遅延復元と、チャンク再ロード後の表示補充を兼ねます。
     */
    fun restoreChunkDisplays(world: World, chunkX: Int, chunkZ: Int) {
        val stale = mutableListOf<String>()
        inChunk(world.name, chunkX, chunkZ).forEach { placement ->
            restoreDisplayFor(world, placement, stale)
        }
        if (stale.isEmpty()) return
        cullStale(stale)
        save()
    }

    /**
     * ロード済みチャンク内の1配置を復元します。
     *
     * スクリプトが読めない配置は休止します。参照ファイルが残っている限り台帳は
     * 保持して浮遊表示だけを撤去し、ファイル自体が消えた参照切れと、実体
     * ブロックが消えた配置だけを孤立として [stale] へ積みます。
     */
    private fun restoreDisplayFor(world: World, placement: DiskPlacement, stale: MutableList<String>) {
        val scriptAvailable = plugin.scripts.load(placement.scriptId) != null
        if (!scriptAvailable && !plugin.scripts.isPersisted(placement.scriptId)) {
            removeDisplay(world, placement.displayId)
            stale += placement.key
            return
        }
        val block = world.getBlockAt(placement.x, placement.y, placement.z)
        if (!PlacedBlockMaterials.isPlacedBlock(block.type)) {
            removeDisplay(world, placement.displayId)
            stale += placement.key
            return
        }
        if (!scriptAvailable) return removeDisplay(world, placement.displayId)
        // 表示が実体として残っていれば付け直さず、欠けているときだけ補充します。
        if (placement.displayId?.let { world.getEntity(it) } != null) return
        runCatching { spawnDisplay(world, placement) }
            .onFailure { failure ->
                plugin.logger.log(
                    Level.WARNING,
                    "配置表示の復元に失敗しました: placement=${placement.key}",
                    failure,
                )
            }
    }

    /** 孤立配置を台帳から除去し、イベント起動の登録へまとめて反映します。 */
    private fun cullStale(stale: List<String>) {
        var removedAny = false
        stale.forEach { key ->
            placements.remove(key)?.let { removed ->
                removedAny = true
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
        // issue #7: 配置除去はイベント起動の登録へ反映します。件数ごとの再構築を
        // 避け、除去があったときだけ末尾で1回行います。
        if (removedAny) plugin.refreshEventTriggerIndex()
    }

    fun spawnDisplay(world: World, placement: DiskPlacement): UUID {
        val loc = Location(world, placement.x + 0.5, placement.y + 0.05, placement.z + 0.5)
        val display = world.spawn(loc, BlockDisplay::class.java) {
            val script = plugin.scripts.load(placement.scriptId)
            val displayMaterial = if (script?.trigger?.normalized()?.kind == TriggerKind.TIMER) Material.REPEATING_COMMAND_BLOCK else Material.COMMAND_BLOCK
            it.block = Bukkit.createBlockData(displayMaterial)
            it.isGlowing = false
            // 設置後実体の名称をかんたんコマンダー制御ブロックへ統一する（浮遊表示はせず識別用に保持する）。
            it.customName(Component.text(
                KcI18n.text(null, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_NAME_BLOCK),
                NamedTextColor.AQUA,
            ))
            CCSystem.getAPI().getSystemEntityRegistry().mark(it, plugin)
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
        world.getEntity(id)?.remove()
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

    /** issue #16: 原子書込は共通基盤へ委譲します。失敗意味は従来どおりです。 */
    private fun save() {
        AtomicFileWriter.write(file, gson.toJson(placements.values.toList()), "配置台帳")
    }

    private fun key(world: String, x: Int, y: Int, z: Int): String = "$world,$x,$y,$z"

    companion object {
        const val DISPLAY_TAG = "kantan_commander_display"
    }
}
