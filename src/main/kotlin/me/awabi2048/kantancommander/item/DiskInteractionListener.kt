package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class DiskInteractionListener(private val plugin: KantanCommanderPlugin) : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item
        val diskId = DiskItemService.diskId(item)
        val clickedPlacement = event.clickedBlock?.let { plugin.placements.find(it.location) }

        if (diskId == null && clickedPlacement == null) return
        event.isCancelled = true

        if (clickedPlacement != null && !player.isSneaking) {
            val script = plugin.scripts.load(clickedPlacement.scriptId) ?: return
            if (!plugin.placementAccess.canManage(player, clickedPlacement.world, script.owner)) {
                player.sendMessage(KcI18n.text(player, "message.no_placement_access"))
                return
            }
            plugin.editorMenu.open(player, script.id)
            return
        }

        if (diskId == null) return
        val script = plugin.scripts.load(diskId) ?: return
        if (!plugin.placementAccess.canManage(player, player.world.name, script.owner)) {
            player.sendMessage(KcI18n.text(player, "message.no_placement_access"))
            return
        }

        when (event.action) {
            Action.RIGHT_CLICK_BLOCK -> {
                if (player.isSneaking) {
                    val base = event.clickedBlock ?: return
                    val target = base.getRelative(event.blockFace)
                    placeDisk(player, target.location, script.id, event.hand ?: EquipmentSlot.HAND)
                } else {
                    plugin.editorMenu.open(player, script.id)
                }
            }
            Action.RIGHT_CLICK_AIR -> {
                if (!player.isSneaking) plugin.editorMenu.open(player, script.id)
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.isCancelled()) return
        val block = event.block
        val placement = plugin.placements.find(block.location) ?: return
        val script = plugin.scripts.load(placement.scriptId) ?: return
        if (!plugin.placementAccess.canManage(event.player, placement.world, script.owner)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockDrop(event: BlockDropItemEvent) {
        val block = event.block
        val placement = plugin.placements.find(block.location) ?: return
        val script = plugin.scripts.load(placement.scriptId) ?: return

        event.items.forEach { it.remove() }
        plugin.placements.removeDisplay(block.world, placement.displayId)
        plugin.placements.remove(block.world, block.x, block.y, block.z)
        block.world.dropItemNaturally(
            block.location.add(0.5, 0.5, 0.5),
            DiskItemService.create(script, event.player)
        )
    }

    private fun placeDisk(player: Player, location: org.bukkit.Location, scriptId: java.util.UUID, hand: EquipmentSlot) {
        val block = location.block
        if (!block.type.isAir) {
            player.sendMessage(KcI18n.text(player, "message.place_blocked"))
            return
        }
        if (plugin.placements.find(block.location) != null) {
            player.sendMessage(KcI18n.text(player, "message.place_exists"))
            return
        }

        val placeEvent = BlockPlaceEvent(
            block,
            block.state,
            location.block.getRelative(org.bukkit.block.BlockFace.DOWN),
            player.inventory.getItem(hand),
            player,
            true,
            hand
        )
        Bukkit.getPluginManager().callEvent(placeEvent)
        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            player.sendMessage(KcI18n.text(player, "message.place_blocked"))
            return
        }

        block.setType(Material.NOTE_BLOCK, false)
        val placement = DiskPlacement(block.world.name, block.x, block.y, block.z, scriptId, null)
        plugin.placements.add(placement)
        plugin.placements.spawnDisplay(block.world, placement)

        val stack = if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
        stack.amount -= 1
        player.sendMessage(KcI18n.text(player, "message.place_created"))
    }
}
