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

/**
 * エンティティ操作で付与した飛行許可を、付与されたワールド内だけで有効にします。
 *
 * MyWorldの環境設定を最後に再適用することで、個別コマンドがワールド側の
 * 飛行禁止を上書きしないようにします。MyWorld管理外のワールドへ移動した場合も、
 * 次tickで一時許可を解除し、許可状態がワールドをまたいで残るのを防ぎます。
 */
class PlayerFlightPermissionService(
    private val plugin: KantanCommanderPlugin,
) : Listener {
    private val grantedWorldByPlayer = mutableMapOf<UUID, UUID>()

    fun apply(player: Player, allowed: Boolean): Boolean {
        player.allowFlight = allowed
        if (!allowed) {
            grantedWorldByPlayer.remove(player.uniqueId)
            if (player.isFlying) player.isFlying = false
            return true
        }

        grantedWorldByPlayer[player.uniqueId] = player.world.uid
        MyWorldManagerApi.getWorldRepository()
            ?.findByWorldKey(player.world.key.toString())
            ?.let { MyWorldManagerApi.syncWorldPlayerState(player, it) }
        return true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        val grantedWorld = grantedWorldByPlayer[player.uniqueId] ?: return
        if (grantedWorld != event.from.uid) return
        grantedWorldByPlayer.remove(player.uniqueId)

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
    }

    fun clear() {
        grantedWorldByPlayer.clear()
    }
}
