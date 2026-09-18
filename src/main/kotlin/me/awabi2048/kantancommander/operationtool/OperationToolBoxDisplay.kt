package me.awabi2048.kantancommander.operationtool

import me.awabi2048.kantancommander.model.SpatialVector
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * issue #14のBox選択の可視化管理です。
 *
 * Boxの12辺をBlock Displayで描画します。表示は操作中のプレイヤーにのみ見せ、
 * 他プレイヤーからは [org.bukkit.entity.Player.hideEntity] で隠します。
 * 毎tick論理状態を更新できることを前提とし、変化がないDisplayへの不要な更新は
 * 行いません。編集中は黄色、確定後はグレーを基本状態とします。
 *
 * 新しいBox選択を開始した場合、以前のBox表示は置き換えます。確定済みEntityの
 * 選択自体は [OperationToolSession] が保持し、ここでは表示だけを扱います。
 */
class OperationToolBoxDisplay(
    private val plugin: JavaPlugin,
) {
    /** Box表示の様式です。編集中は黄色・確定後はグレーです。 */
    enum class BoxStyle(val material: Material) {
        EDITING(Material.YELLOW_STAINED_GLASS),
        CONFIRMED(Material.GRAY_STAINED_GLASS),
    }

    private data class EdgeDisplays(
        val displays: List<BlockDisplay>,
        val minimum: SpatialVector,
        val maximum: SpatialVector,
    )

    private val editing = ConcurrentHashMap<UUID, EdgeDisplays>()
    private val confirmed = ConcurrentHashMap<UUID, EdgeDisplays>()
    private val trackedIds = ConcurrentHashMap.newKeySet<UUID>()

    /** 辺の太さです。Block単位へのsnapは行わないため細めに固定します。 */
    private val edgeThickness = 0.06

    /** 管理中のツール表示かどうかを返します。候補収集から除外するために使います。 */
    fun isToolDisplay(entityId: UUID): Boolean = trackedIds.contains(entityId)

    /**
     * 編集中Boxの表示を開始または更新します。
     *
     * 範囲が前回と同一の場合は何も送信しません。上限超過等の妥当性判定は
     * 呼び出し側（[OperationToolSession]）が行い、ここでは描画だけを扱います。
     */
    fun showEditing(playerId: UUID, worldName: String, minimum: SpatialVector, maximum: SpatialVector) {
        val player = plugin.server.getPlayer(playerId) ?: return
        val world = plugin.server.getWorld(worldName) ?: return
        if (player.world.uid != world.uid) return
        val current = editing[playerId]
        if (current != null && current.minimum == minimum && current.maximum == maximum) return
        removeEntries(editing, playerId)
        editing[playerId] = spawnEdges(playerId, world.name, minimum, maximum, BoxStyle.EDITING)
    }

    /** 編集中Boxの表示を破棄します。確定済み選択自体は保持します。 */
    fun dropEditing(playerId: UUID) {
        removeEntries(editing, playerId)
    }

    /**
     * 確定済みBoxの表示へ置き換えます。
     *
     * 編集中表示は破棄し、確定表示をグレーで残します。範囲がnullの場合は
     * 確定表示も破棄します。
     */
    fun replaceConfirmed(playerId: UUID, worldName: String?, minimum: SpatialVector?, maximum: SpatialVector?) {
        removeEntries(editing, playerId)
        removeEntries(confirmed, playerId)
        if (worldName == null || minimum == null || maximum == null) return
        if (plugin.server.getPlayer(playerId) == null) return
        confirmed[playerId] = spawnEdges(playerId, worldName, minimum, maximum, BoxStyle.CONFIRMED)
    }

    /** プレイヤーの全Box表示を破棄します。ログアウト・モード変更・全解除時に呼び出します。 */
    fun clear(playerId: UUID) {
        removeEntries(editing, playerId)
        removeEntries(confirmed, playerId)
    }

    /** 全Box表示を破棄します。プラグイン無効化時に呼び出します。 */
    fun removeAll() {
        (editing.keys + confirmed.keys).forEach(::clear)
    }

    /**
     * 参加したプレイヤーから他者のBox表示を隠します。
     *
     * 表示は操作者専用のため、新規参加者にも見せません。
     */
    fun hideFrom(joinerId: UUID) {
        val joiner = plugin.server.getPlayer(joinerId) ?: return
        val ids = editing.values + confirmed.values
        ids.flatMap { it.displays }.forEach { display ->
            runCatching { joiner.hideEntity(plugin, display) }
        }
    }

    private fun removeEntries(map: MutableMap<UUID, EdgeDisplays>, playerId: UUID) {
        val removed = map.remove(playerId) ?: return
        removed.displays.forEach { display ->
            trackedIds.remove(display.uniqueId)
            runCatching { display.remove() }
        }
    }

    private fun spawnEdges(
        playerId: UUID,
        worldName: String,
        minimum: SpatialVector,
        maximum: SpatialVector,
        style: BoxStyle,
    ): EdgeDisplays {
        val world = plugin.server.getWorld(worldName) ?: return EdgeDisplays(emptyList(), minimum, maximum)
        val owner = plugin.server.getPlayer(playerId)
        val blockData = style.material.createBlockData()
        val diagonal = kotlin.math.sqrt(
            (maximum.x - minimum.x).let { it * it } +
                (maximum.y - minimum.y).let { it * it } +
                (maximum.z - minimum.z).let { it * it },
        ).toFloat() + 2.0f
        val displays = boxEdges(minimum, maximum).mapNotNull { (start, end) ->
            runCatching {
                world.spawn(Location(world, start.x, start.y, start.z), BlockDisplay::class.java) { display ->
                    display.setBlock(blockData)
                    display.transformation = edgeTransformation(start, end)
                    display.brightness = Display.Brightness(15, 15)
                    display.viewRange = 1.0f
                    display.displayWidth = diagonal
                    display.displayHeight = diagonal
                    display.interpolationDuration = 0
                    display.teleportDuration = 0
                    display.isPersistent = false
                }
            }.getOrNull()
        }
        displays.forEach { display ->
            trackedIds.add(display.uniqueId)
            if (owner != null) {
                plugin.server.onlinePlayers
                    .filter { it.uniqueId != playerId }
                    .forEach { other -> runCatching { other.hideEntity(plugin, display) } }
            }
        }
        if (displays.size < 12) {
            plugin.logger.warning(
                "操作ツールBox表示の一部を生成できませんでした: player=$playerId edges=${displays.size}/12",
            )
        }
        return EdgeDisplays(displays, minimum, maximum)
    }

    /** AABBの12辺の始点・終点です。Block単位へのsnapは行いません。 */
    internal fun boxEdges(minimum: SpatialVector, maximum: SpatialVector): List<Pair<SpatialVector, SpatialVector>> {
        val x0 = minimum.x
        val y0 = minimum.y
        val z0 = minimum.z
        val x1 = maximum.x
        val y1 = maximum.y
        val z1 = maximum.z
        fun point(x: Double, y: Double, z: Double) = SpatialVector(x, y, z)
        return listOf(
            // X方向の4辺です。
            point(x0, y0, z0) to point(x1, y0, z0),
            point(x0, y1, z0) to point(x1, y1, z0),
            point(x0, y0, z1) to point(x1, y0, z1),
            point(x0, y1, z1) to point(x1, y1, z1),
            // Y方向の4辺です。
            point(x0, y0, z0) to point(x0, y1, z0),
            point(x1, y0, z0) to point(x1, y1, z0),
            point(x0, y0, z1) to point(x0, y1, z1),
            point(x1, y0, z1) to point(x1, y1, z1),
            // Z方向の4辺です。
            point(x0, y0, z0) to point(x0, y0, z1),
            point(x1, y0, z0) to point(x1, y0, z1),
            point(x0, y1, z0) to point(x0, y1, z1),
            point(x1, y1, z0) to point(x1, y1, z1),
        )
    }

    /** 辺の始点を原点とする軸平行の拡縮変換です。回転は行いません。 */
    internal fun edgeTransformation(start: SpatialVector, end: SpatialVector): Transformation {
        val thickness = edgeThickness.toFloat()
        val scale = Vector3f(
            (end.x - start.x).toFloat().coerceAtLeast(thickness),
            (end.y - start.y).toFloat().coerceAtLeast(thickness),
            (end.z - start.z).toFloat().coerceAtLeast(thickness),
        )
        return Transformation(Vector3f(0.0f, 0.0f, 0.0f), AxisAngle4f(), scale, AxisAngle4f())
    }
}
