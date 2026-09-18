package me.awabi2048.kantancommander.operationtool

import me.awabi2048.kantancommander.model.SpatialVector
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * issue #14の範囲選択の可視化管理です。
 *
 * Boxおよびエリアの12辺をBlock Displayで描画します。不透明なコンクリートを用い、
 * 辺方向へ厚みの半分ずつ延長して角立方体で接合するため、角欠けなく表示します。
 * 表示は操作中のプレイヤーにのみ見せ、他プレイヤーからは
 * [org.bukkit.entity.Player.hideEntity] で隠します。
 *
 * ちらつき防止のため、表示個体は作り直さず再利用します。初回のみ12体を生成し、
 * 2回目以降は既存個体へ `teleport` + `transformation` 更新だけを行います。
 * 追従の補間は2tickとし、高応答を保ちつつ微振動を抑えます。
 * 編集中は黄色、確定後はグレー、エリア選択は水色を基本状態とします。
 *
 * 新しいBox選択を開始した場合、以前のBox表示は置き換えます。確定済みEntityの
 * 選択自体は [OperationToolSession] が保持し、ここでは表示だけを扱います。
 */
class OperationToolBoxDisplay(
    private val plugin: JavaPlugin,
) {
    /**
     * Box表示の様式です。編集中は黄色・確定後はグレー・エリアは水色です。
     *
     * 追従の補間tickは様式ごとに持ちます。Box追従は高応答を保ちつつ微振動を
     * 抑えるため1tickとし、エリア選択は補間を使用しません。
     */
    enum class BoxStyle(val material: Material, val interpolationTicks: Int) {
        EDITING(Material.YELLOW_CONCRETE, 1),
        CONFIRMED(Material.GRAY_CONCRETE, 1),
        AREA(Material.LIGHT_BLUE_CONCRETE, 0),
    }

    private data class EdgeDisplays(
        val displays: List<BlockDisplay>,
        val minimum: SpatialVector,
        val maximum: SpatialVector,
        val style: BoxStyle,
    )

    private val editing = ConcurrentHashMap<UUID, EdgeDisplays>()
    private val confirmed = ConcurrentHashMap<UUID, EdgeDisplays>()
    private val area = ConcurrentHashMap<UUID, EdgeDisplays>()
    private val trackedIds = ConcurrentHashMap.newKeySet<UUID>()

    /** 管理中のツール表示かどうかを返します。候補収集から除外するために使います。 */
    fun isToolDisplay(entityId: UUID): Boolean = trackedIds.contains(entityId)

    /**
     * 編集中Boxの表示を開始または更新します。
     *
     * 初回のみ生成し、2回目以降は既存個体を移動・拡縮して再利用するため、
     * 毎tick作り直しによるちらつきは発生しません。範囲が前回と同一の場合は
     * 何も送信しません。上限超過等の妥当性判定は呼び出し側が行います。
     */
    fun showEditing(playerId: UUID, worldName: String, minimum: SpatialVector, maximum: SpatialVector) {
        upsert(editing, playerId, worldName, minimum, maximum, BoxStyle.EDITING)
    }

    /** 編集中Boxの表示を破棄します。確定済み選択自体は保持します。 */
    fun dropEditing(playerId: UUID) {
        removeEntries(editing, playerId)
    }

    /**
     * 確定済みBoxの表示へ置き換えます。
     *
     * 編集中表示は破棄せず灰色へ変えて転用するため、確定時の瞬きは発生しません。
     * 範囲がnullの場合は確定表示も破棄します。
     */
    fun replaceConfirmed(playerId: UUID, worldName: String?, minimum: SpatialVector?, maximum: SpatialVector?) {
        removeEntries(confirmed, playerId)
        if (worldName == null || minimum == null || maximum == null) {
            removeEntries(editing, playerId)
            return
        }
        val player = plugin.server.getPlayer(playerId)
        val world = plugin.server.getWorld(worldName)
        val current = editing.remove(playerId)
        if (player != null && world != null && player.world.uid == world.uid &&
            current != null && isReusable(current)
        ) {
            restyle(current.displays, BoxStyle.CONFIRMED)
            updateEdges(world, current.displays, minimum, maximum)
            confirmed[playerId] = EdgeDisplays(current.displays, minimum, maximum, BoxStyle.CONFIRMED)
        } else {
            if (current != null) removeDisplays(current.displays)
            upsert(confirmed, playerId, worldName, minimum, maximum, BoxStyle.CONFIRMED)
        }
    }

    /**
     * エリア選択の表示を開始または更新します。
     *
     * pos1・pos2の両指定がある場合に呼び出し、編集中Box・確定Boxとは別枠で
     * 管理します。モード変更・全解除で消去されます。
     */
    fun showArea(playerId: UUID, worldName: String, minimum: SpatialVector, maximum: SpatialVector) {
        upsert(area, playerId, worldName, minimum, maximum, BoxStyle.AREA)
    }

    /** エリア選択の表示を破棄します。 */
    fun dropArea(playerId: UUID) {
        removeEntries(area, playerId)
    }

    /** プレイヤーの全範囲表示を破棄します。ログアウト・モード変更・全解除時に呼び出します。 */
    fun clear(playerId: UUID) {
        removeEntries(editing, playerId)
        removeEntries(confirmed, playerId)
        removeEntries(area, playerId)
    }

    /** 全範囲表示を破棄します。プラグイン無効化時に呼び出します。 */
    fun removeAll() {
        (editing.keys + confirmed.keys + area.keys).forEach(::clear)
    }

    /**
     * 参加したプレイヤーから他者の範囲表示を隠します。
     *
     * 表示は操作者専用のため、新規参加者にも見せません。
     */
    fun hideFrom(joinerId: UUID) {
        val joiner = plugin.server.getPlayer(joinerId) ?: return
        val ids = editing.values + confirmed.values + area.values
        ids.flatMap { it.displays }.forEach { display ->
            runCatching { joiner.hideEntity(plugin, display) }
        }
    }

    private fun upsert(
        map: MutableMap<UUID, EdgeDisplays>,
        playerId: UUID,
        worldName: String,
        minimum: SpatialVector,
        maximum: SpatialVector,
        style: BoxStyle,
    ) {
        val player = plugin.server.getPlayer(playerId)
        val world = plugin.server.getWorld(worldName)
        if (player == null || world == null || player.world.uid != world.uid) {
            removeEntries(map, playerId)
            return
        }
        val current = map[playerId]
        if (current != null && current.style == style && current.minimum == minimum &&
            current.maximum == maximum && isReusable(current)
        ) {
            return
        }
        if (current != null && current.style == style && isReusable(current)) {
            updateEdges(world, current.displays, minimum, maximum)
            map[playerId] = current.copy(minimum = minimum, maximum = maximum)
            return
        }
        removeEntries(map, playerId)
        map[playerId] = spawnEdges(playerId, world.name, minimum, maximum, style)
    }

    private fun isReusable(entry: EdgeDisplays): Boolean =
        entry.displays.size == EDGE_COUNT && entry.displays.all { it.isValid }

    private fun removeEntries(map: MutableMap<UUID, EdgeDisplays>, playerId: UUID) {
        val removed = map.remove(playerId) ?: return
        removeDisplays(removed.displays)
    }

    private fun removeDisplays(displays: List<BlockDisplay>) {
        displays.forEach { display ->
            trackedIds.remove(display.uniqueId)
            runCatching { display.remove() }
        }
    }

    private fun restyle(displays: List<BlockDisplay>, style: BoxStyle) {
        val blockData = style.material.createBlockData()
        displays.forEach { display ->
            runCatching { display.setBlock(blockData) }
        }
    }

    private fun updateEdges(world: World, displays: List<BlockDisplay>, minimum: SpatialVector, maximum: SpatialVector) {
        boxEdges(minimum, maximum).forEachIndexed { index, (start, end) ->
            val display = displays.getOrNull(index) ?: return@forEachIndexed
            if (!display.isValid) return@forEachIndexed
            val (origin, transformation) = edgePlacement(start, end)
            runCatching {
                display.teleport(Location(world, origin.x, origin.y, origin.z))
                display.transformation = transformation
            }
        }
    }

    private fun spawnEdges(
        playerId: UUID,
        worldName: String,
        minimum: SpatialVector,
        maximum: SpatialVector,
        style: BoxStyle,
    ): EdgeDisplays {
        val world = plugin.server.getWorld(worldName) ?: return EdgeDisplays(emptyList(), minimum, maximum, style)
        val owner = plugin.server.getPlayer(playerId)
        val blockData = style.material.createBlockData()
        val diagonal = kotlin.math.sqrt(
            (maximum.x - minimum.x).let { it * it } +
                (maximum.y - minimum.y).let { it * it } +
                (maximum.z - minimum.z).let { it * it },
        ).toFloat() + 2.0f
        val displays = boxEdges(minimum, maximum).mapNotNull { (start, end) ->
            val (origin, transformation) = edgePlacement(start, end)
            runCatching {
                world.spawn(Location(world, origin.x, origin.y, origin.z), BlockDisplay::class.java) { display ->
                    display.setBlock(blockData)
                    display.transformation = transformation
                    display.brightness = Display.Brightness(15, 15)
                    display.viewRange = 1.0f
                    display.displayWidth = diagonal
                    display.displayHeight = diagonal
                    display.interpolationDuration = style.interpolationTicks
                    display.teleportDuration = style.interpolationTicks
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
        if (displays.size < EDGE_COUNT) {
            plugin.logger.warning(
                "操作ツール範囲表示の一部を生成できませんでした: player=$playerId edges=${displays.size}/$EDGE_COUNT",
            )
        }
        return EdgeDisplays(displays, minimum, maximum, style)
    }

    companion object {
        /** Box表示の辺数です。生成・再利用の完全性をこの数で判定します。 */
        const val EDGE_COUNT = 12

        /** 辺の太さです。Block単位へのsnapは行わないため細めに固定します。 */
        const val EDGE_THICKNESS = 0.06

        /** AABBの12辺の始点・終点です。Block単位へのsnapは行いません。 */
        fun boxEdges(minimum: SpatialVector, maximum: SpatialVector): List<Pair<SpatialVector, SpatialVector>> {
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

    /**
     * 辺の配置（原点と拡縮変換）を返します。
     *
     * 辺方向へ厚みの半分ずつ延長し、角立方体で接合するため角欠けしません。
     * 回転は行わず、軸平行の拡縮だけを用います。
     */
    fun edgePlacement(start: SpatialVector, end: SpatialVector): Pair<SpatialVector, Transformation> {
        val thickness = EDGE_THICKNESS
        fun place(from: Double, to: Double): Pair<Double, Float> =
            if (to > from) (from - thickness / 2.0) to ((to - from) + thickness).toFloat()
            else (from - thickness / 2.0) to thickness.toFloat()
        val (originX, scaleX) = place(start.x, end.x)
        val (originY, scaleY) = place(start.y, end.y)
        val (originZ, scaleZ) = place(start.z, end.z)
        val origin = SpatialVector(originX, originY, originZ)
        val transformation = Transformation(
            Vector3f(0.0f, 0.0f, 0.0f),
            AxisAngle4f(),
            Vector3f(scaleX, scaleY, scaleZ),
            AxisAngle4f(),
        )
        return origin to transformation
    }
    }
}
