package me.awabi2048.kantancommander.display

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.gui.ProgramMetadata
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * issue #13 の制御ブロック注視表示です。
 *
 * 設定で有効化すると、制御ブロックを見ている Player へ program 名・所有者を
 * 短時間だけ表示します。常設 Entity を大量生成せず、注視中だけ TextDisplay を
 * 1体維持し、視線が外れたら即時除去します。設置権限不足等の拒否 feedback は
 * #20 の ActionBar service と責務を分け、ここでは扱いません。
 *
 * Player ごとの表示可否は持ちません。有効化・距離・更新周期は全体設定であり、
 * 個別指定が必要になった場合は設定の拡張として扱います。
 */
class ControlBlockGazeService(private val plugin: KantanCommanderPlugin) : Listener {
    /** Player ごとの表示中 Entity です。注視対象が変わるたびに入れ替えます。 */
    private val shown = linkedMapOf<UUID, ShownDisplay>()
    private var task: BukkitTask? = null

    private data class ShownDisplay(val entityId: UUID, val placementKey: String)

    /** 設定を読み直してタスクを組み替えます。無効化時は表示を全除去します。 */
    fun reload() {
        stop()
        if (!plugin.config.getBoolean("gaze.enabled", false)) return
        val interval = plugin.config.getLong("gaze.interval-ticks", DEFAULT_INTERVAL_TICKS).coerceAtLeast(1L)
        task = Bukkit.getScheduler().runTaskTimer(plugin, ::tick, interval, interval)
    }

    /** 表示を全除去してタスクを止めます。無効化時・終了時に使います。 */
    fun stop() {
        task?.cancel()
        task = null
        shown.keys.toList().forEach(::hide)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onQuit(event: PlayerQuitEvent) {
        hide(event.player.uniqueId)
    }

    private fun tick() {
        if (!plugin.config.getBoolean("gaze.enabled", false)) {
            stop()
            return
        }
        val distance = plugin.config.getDouble("gaze.distance", DEFAULT_DISTANCE).let {
            if (it.isFinite()) it.coerceIn(1.0, MAX_DISTANCE) else DEFAULT_DISTANCE
        }
        Bukkit.getOnlinePlayers().forEach { player -> update(player, distance) }
        // 注視をやめた Player の残留表示を除去します。
        shown.keys.toList()
            .filter { playerId -> Bukkit.getPlayer(playerId)?.let { it.isOnline } != true }
            .forEach(::hide)
    }

    private fun update(player: Player, distance: Double) {
        val hit = runCatching { player.rayTraceBlocks(distance) }.getOrNull()?.hitBlock
        val placement = hit?.let { block ->
            plugin.placements.find(block.world, block.x, block.y, block.z)
        }
        if (placement == null) {
            hide(player.uniqueId)
            return
        }
        val current = shown[player.uniqueId]
        if (current?.placementKey == placement.key) {
            // 同一対象の注視継続中は Entity を維持します。無効化されていたら作り直します。
            Bukkit.getEntity(current.entityId)?.takeIf { it.isValid } ?: show(player, placement.key)
            return
        }
        show(player, placement.key)
    }

    private fun show(player: Player, placementKey: String) {
        hide(player.uniqueId)
        val located = locatePlacement(placementKey) ?: return
        val script = plugin.scripts.load(located.scriptId) ?: return
        val display = located.world.spawn(located.location, TextDisplay::class.java) { display ->
            display.billboard = Display.Billboard.CENTER
            display.isSeeThrough = false
            display.text(gazeText(player, script.name, ownerName(script.owner)))
        }
        shown[player.uniqueId] = ShownDisplay(display.uniqueId, placementKey)
        // 文言変化・chunk unload 時の残留を防ぐため、表示は短時間で除去します。
        // 注視継続中は次 tick で作り直すため、ちらつきは更新周期に一致します。
        Bukkit.getScheduler().runTaskLater(plugin, { _ ->
            shown[player.uniqueId]?.takeIf { it.entityId == display.uniqueId }?.let {
                hide(player.uniqueId)
            }
        }, EXPIRE_TICKS)
    }

    private fun hide(playerId: UUID) {
        val shownDisplay = shown.remove(playerId) ?: return
        Bukkit.getEntity(shownDisplay.entityId)?.takeIf { it.isValid }?.remove()
    }

    private fun locatePlacement(placementKey: String): LocatedPlacement? {
        val parts = placementKey.split(",")
        if (parts.size != 4) return null
        val world = Bukkit.getWorld(parts[0]) ?: return null
        val x = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        val z = parts[3].toIntOrNull() ?: return null
        val placement = plugin.placements.find(world, x, y, z) ?: return null
        return LocatedPlacement(placement.scriptId, world, Location(world, x + 0.5, y + 1.2, z + 0.5))
    }

    private data class LocatedPlacement(
        val scriptId: UUID,
        val world: org.bukkit.World,
        val location: Location,
    )

    private fun ownerName(owner: UUID): String =
        ProgramMetadata.ownerName(owner) { Bukkit.getOfflinePlayer(it).name }

    companion object {
        const val DEFAULT_INTERVAL_TICKS = 10L
        const val DEFAULT_DISTANCE = 8.0
        const val MAX_DISTANCE = 32.0
        /** 表示の有効期限です。注視継続中は tick ごとに作り直します。 */
        const val EXPIRE_TICKS = 40L

        /**
         * issue #13: 注視表示の文言です。program 名・所有者だけを示し、
         * 権限・状態の feedback は含めません（#20 と責務を分けます）。
         */
        fun gazeText(player: Player, programName: String, ownerName: String): net.kyori.adventure.text.Component {
            val ownerLabel = me.awabi2048.kantancommander.util.KcI18n.text(
                player,
                com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys.KANTAN_COMMANDER_CLEAN_ITEM_OWNER,
            )
            return net.kyori.adventure.text.Component.text(programName)
                .append(net.kyori.adventure.text.Component.newline())
                .append(net.kyori.adventure.text.Component.text("$ownerLabel: $ownerName"))
        }
    }
}
