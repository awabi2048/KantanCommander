package me.awabi2048.kantancommander.operationtool

import me.awabi2048.kantancommander.model.SpatialVector
import org.bukkit.Color
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
     * 発光色は表示ブロックの色に合わせます。チーム色は16色の近似しか持てない
     * ため、[Display.setGlowColorOverride]でコンクリートの色へ合わせた
     * RGBを直接指定します（染料色の公式パレット値を使用）。
     * 追従の補間tickは様式ごとに持ちます。Box追従は高応答を保ちつつ微振動を
     * 抑えるため1tickとし、エリア選択は補間を使用しません。
     */
    enum class BoxStyle(
        val material: Material,
        val interpolationTicks: Int,
        val glowColor: Color,
    ) {
        EDITING(Material.YELLOW_CONCRETE, 1, Color.fromRGB(0xFED83D)),
        CONFIRMED(Material.GRAY_CONCRETE, 1, Color.fromRGB(0x474F52)),
        AREA(Material.LIGHT_BLUE_CONCRETE, 0, Color.fromRGB(0x3AB3DA)),
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

    init {
        // 発光色はDisplayのglowColorOverrideへ移行したため、旧実装の
        // 発光チーム（緑固定＋様式別）をすべて掃除します。非永続Displayの
        // UUIDが残り続けないよう、起動ごとに解除します。
        val board = plugin.server.scoreboardManager?.mainScoreboard
        if (board != null) {
            LEGACY_TEAM_NAMES.forEach { name ->
                runCatching { board.getTeam(name)?.unregister() }
            }
        }
    }

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
            // 発光色もブロック色へ追従させます（編集中→確定の色替え）。
            runCatching { display.glowColorOverride = style.glowColor }
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
        val displays = boxEdges(minimum, maximum).mapNotNull { (start, end) ->
            val (origin, transformation) = edgePlacement(start, end)
            runCatching {
                world.spawn(Location(world, origin.x, origin.y, origin.z), BlockDisplay::class.java) { display ->
                    display.setBlock(blockData)
                    display.transformation = transformation
                    display.brightness = Display.Brightness(15, 15)
                    display.viewRange = 1.0f
                    // 辺の原点が視界外でも表示が崩れないよう、カリング判定の
                    // width/heightへ辺長より十分大きな値を固定で与えます。
                    display.displayWidth = EDGE_CULL_SIZE
                    display.displayHeight = EDGE_CULL_SIZE
                    display.interpolationDuration = style.interpolationTicks
                    display.teleportDuration = style.interpolationTicks
                    display.isPersistent = false
                    // 選択範囲は操作者だけに見えるため、実体フラグの発光でも
                    // 他プレイヤーへは露出しません。発光色はブロック色へ合わせた
                    // RGBをglowColorOverrideで直接指定します。
                    display.isGlowing = true
                    display.glowColorOverride = style.glowColor
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

        /**
         * 辺Displayのカリング判定サイズです。
         *
         * displayWidth/displayHeightは原点中心の描画判定箱であり、辺の実寸が
         * これを超えるとカメラ位置によって一部が欠けます。Box最大一辺（128）より
         * 十分大きい固定値へ揃え、視点位置で表示が崩れないようにします。
         */
        const val EDGE_CULL_SIZE = 512.0f

        /**
         * 旧実装の発光チーム名です。
         *
         * 緑固定の単一チーム（26.928.6以前）と様式別チーム（26.928.7）の双方を
         * 起動時に解除します。
         */
        val LEGACY_TEAM_NAMES = listOf(
            "kc_wand_edges",
            "kc_wand_glow_editing",
            "kc_wand_glow_confirmed",
            "kc_wand_glow_area",
        )

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
