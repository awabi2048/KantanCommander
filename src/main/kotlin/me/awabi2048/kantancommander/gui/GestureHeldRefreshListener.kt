package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent

/**
 * 手持ち変化をGesture下部表示へ反映します。
 *
 * メインハンド由来の設定入口（アイテム・ブロック・花火・ディスク）は、描画時の
 * 所持状態で有効色を決めます。所持変化で描画を作り直さないと、設定可能なのに
 * 灰色のまま残り、クリック時点判定と表示がずれます。インベントリ操作は適用前の
 * タイミングで通知されるため、1tick遅延で最新状態を描画します。
 */
class GestureHeldRefreshListener(private val plugin: KantanCommanderPlugin) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHeld(event: PlayerItemHeldEvent) = refreshLater(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        (event.whoClicked as? Player)?.let(::refreshLater)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        (event.whoClicked as? Player)?.let(::refreshLater)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClose(event: InventoryCloseEvent) {
        (event.player as? Player)?.let(::refreshLater)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) = refreshLater(event.player)

    private fun refreshLater(player: Player) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val online = plugin.server.getPlayer(player.uniqueId) ?: return@Runnable
            plugin.gestureEditor.refreshLowerIfOpen(online)
        })
    }
}
