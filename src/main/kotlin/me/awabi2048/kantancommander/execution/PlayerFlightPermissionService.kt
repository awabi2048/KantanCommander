package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

/** 明示された飛行可否を現在のワールドに限定して最優先で適用します。 */
class PlayerFlightPermissionService(
    private val plugin: KantanCommanderPlugin,
) : Listener {
    private val grantedWorldByPlayer = mutableMapOf<UUID, UUID>()

    fun apply(player: Player, allowed: Boolean): Boolean {
        grantedWorldByPlayer[player.uniqueId] = player.world.uid
        MyWorldManagerApi.setLocalFlightRequest(player, REQUEST_OWNER, allowed)
        return true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        val grantedWorld = grantedWorldByPlayer[player.uniqueId] ?: return
        if (grantedWorld != event.from.uid) return
        grantedWorldByPlayer.remove(player.uniqueId)
        MyWorldManagerApi.clearLocalFlightRequest(player.uniqueId, REQUEST_OWNER)

        // MyWorldの同イベント処理後に方針を再適用します。未管理ワールドには
        // 環境設定がないため、一時許可を明示的に解除します。
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!player.isOnline || player.world.uid == grantedWorld) return@Runnable
            val worldData = MyWorldManagerApi.getWorldRepository()
                ?.findByWorldKey(player.world.key.toString())
            if (worldData != null) {
                MyWorldManagerApi.syncWorldPlayerState(player, worldData)
            } else if (player.gameMode != GameMode.CREATIVE && player.gameMode != GameMode.SPECTATOR) {
                if (player.isFlying) player.isFlying = false
                player.allowFlight = false
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        grantedWorldByPlayer.remove(event.player.uniqueId)
        MyWorldManagerApi.clearLocalFlightRequest(event.player.uniqueId, REQUEST_OWNER)
    }

    fun clear() {
        grantedWorldByPlayer.keys.toList().forEach { playerId ->
            MyWorldManagerApi.clearLocalFlightRequest(playerId, REQUEST_OWNER)
            plugin.server.getPlayer(playerId)?.let { player ->
                MyWorldManagerApi.getWorldRepository()?.findByWorldKey(player.world.key.toString())
                    ?.let { MyWorldManagerApi.syncWorldPlayerState(player, it) }
            }
        }
        grantedWorldByPlayer.clear()
    }

    private companion object {
        const val REQUEST_OWNER = "kantan-commander"
    }
}
