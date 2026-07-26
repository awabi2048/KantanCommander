package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuRoute
import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import java.util.UUID

class ItemSelectionListener(private val plugin: KantanCommanderPlugin) : Listener {
    private val selections = mutableMapOf<UUID, Selection>()

    fun begin(player: Player, scriptId: UUID, nodeId: UUID, returnRoute: MenuRoute) {
        selections[player.uniqueId] = Selection(scriptId, nodeId, returnRoute)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val selection = selections[player.uniqueId] ?: return
        if (event.clickedInventory != player.inventory) return
        event.isCancelled = true
        val selected = event.currentItem?.takeUnless { it.type == Material.AIR } ?: return
        val script = plugin.scripts.load(selection.scriptId) ?: return cancel(player)
        val node = script.graph.nodes[selection.nodeId] ?: return cancel(player)
        node.params["item"] = selected.type.key.toString()
        node.params["itemData"] = ItemStackCodec.encode(selected)
        plugin.scripts.save(script)
        selections.remove(player.uniqueId)
        CCSystem.getAPI().getMenuRuntimeService().open(player, selection.returnRoute)
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        // Closing the menu is the explicit cancellation path; no item is changed.
        selections.remove(event.player.uniqueId)
    }

    private fun cancel(player: Player) {
        selections.remove(player.uniqueId)
    }

    private data class Selection(val scriptId: UUID, val nodeId: UUID, val returnRoute: MenuRoute)
}
