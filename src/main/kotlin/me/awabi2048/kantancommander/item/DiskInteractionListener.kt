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
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import me.awabi2048.kantancommander.placement.PlacedDiskMaterials

class DiskInteractionListener(private val plugin: KantanCommanderPlugin) : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item
        val itemState = DiskItemService.state(item)
        val diskId = DiskItemService.diskId(item)
        val clickedPlacement = event.clickedBlock?.let { plugin.placements.find(it.location) }

        if (itemState == DiskItemState.NOT_DISK && clickedPlacement == null) return
        event.isCancelled = true

        if (clickedPlacement != null && !player.isSneaking) {
            val script = plugin.scripts.load(clickedPlacement.scriptId) ?: return
            if (!plugin.placementAccess.canManage(player, clickedPlacement.world, script.owner)) {
                player.sendMessage(KcI18n.text(player, "message.no_placement_access"))
                return
            }
            plugin.editorMenu.open(player, clickedPlacement)
            return
        }

        if (DiskInteractionPolicy.itemAction(itemState, event.action, player.isSneaking) == DiskItemAction.PLACE) {
            val script = when (itemState) {
                DiskItemState.UNSET -> null
                DiskItemState.WRITTEN -> diskId?.let(plugin.scripts::load) ?: return
                DiskItemState.NOT_DISK -> return
            }
            if (script != null && !plugin.placementAccess.canManage(player, player.world.name, script.owner)) {
                player.sendMessage(KcI18n.text(player, "message.no_placement_access"))
                return
            }
            val base = event.clickedBlock ?: return
            val target = base.getRelative(event.blockFace)
            placeDisk(player, target.location, base, script, event.hand ?: EquipmentSlot.HAND)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.isCancelled()) return
        val block = event.block
        val placement = plugin.placements.find(block.location) ?: return
        event.isCancelled = true
        val script = plugin.scripts.load(placement.scriptId) ?: return
        if (!plugin.placementAccess.canManage(event.player, placement.world, script.owner)) return
        event.player.sendMessage(KcI18n.text(event.player, "message.remove_from_menu"))
    }

    private fun placeDisk(
        player: Player,
        location: org.bukkit.Location,
        blockAgainst: org.bukkit.block.Block,
        source: me.awabi2048.kantancommander.model.DiskScript?,
        hand: EquipmentSlot,
    ) {
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
            blockAgainst,
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

        val targetBox = org.bukkit.util.BoundingBox.of(
            block.location.toVector(),
            block.location.clone().add(1.0, 1.0, 1.0).toVector(),
        )
        if (player.boundingBox.overlaps(targetBox)) {
            player.sendMessage(KcI18n.text(player, "message.place_blocked"))
            return
        }

        val placedScript = source?.let(plugin.scripts::copyForPlacement)
            ?: plugin.scripts.createPlacement(
                player.uniqueId,
                plugin.config.getString("default-disk-name", "Kantan Disk") ?: "Kantan Disk",
            )
        block.setType(PlacedDiskMaterials.forTimer(placedScript.timer.enabled), false)
        val placement = DiskPlacement(
            block.world.name, block.x, block.y, block.z, placedScript.id,
            player.facing.name, null
        )
        runCatching {
            plugin.placements.add(placement)
            plugin.placements.spawnDisplay(block.world, placement)
        }.onFailure {
            plugin.placements.remove(block.world, block.x, block.y, block.z)
            plugin.scripts.delete(placedScript.id)
            block.setType(Material.AIR, false)
            player.sendMessage(KcI18n.text(player, "message.place_blocked"))
            return
        }
        val stack = if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
        if (player.gameMode != org.bukkit.GameMode.CREATIVE) stack.amount = (stack.amount - 1).coerceAtLeast(0)
        player.sendMessage(KcI18n.text(player, "message.place_created"))
    }
}
