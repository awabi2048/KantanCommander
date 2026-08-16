package me.awabi2048.kantancommander.item
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import me.awabi2048.kantancommander.placement.PlacedDiskMaterials
import me.awabi2048.kantancommander.model.DiskProfile
import me.awabi2048.kantancommander.model.effectiveProfile

class DiskInteractionListener(private val plugin: KantanCommanderPlugin) : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item
        val itemState = DiskItemService.state(item)
        val diskId = DiskItemService.diskId(item)
        val clickedPlacement = event.clickedBlock?.let { plugin.placements.find(it.location) }

        if (
            clickedPlacement != null &&
            event.action == Action.RIGHT_CLICK_BLOCK &&
            !player.isSneaking
        ) {
            event.isCancelled = true
            val script = plugin.scripts.load(clickedPlacement.scriptId) ?: return
            if (!plugin.placementAccess.canManage(player, clickedPlacement.world)) {
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PLACEMENT_ACCESS))
                return
            }
            plugin.editorMenu.open(player, clickedPlacement)
            return
        }

        when (DiskInteractionPolicy.itemAction(itemState, event.action, player.isSneaking)) {
            DiskItemAction.NONE -> return
            DiskItemAction.OPEN -> {
                val script = diskId?.let(plugin.scripts::load) ?: return
                event.isCancelled = true
                plugin.editorMenu.open(player, script.id)
            }
            DiskItemAction.PLACE -> {
                event.isCancelled = true
                val script = when (itemState) {
                    DiskItemState.UNSET -> null
                    DiskItemState.WRITTEN -> diskId?.let(plugin.scripts::load) ?: return
                    DiskItemState.NOT_DISK -> return
                }
                val unsetProfile = DiskItemService.unsetProfile(item) ?: DiskProfile.STANDARD
                if (!plugin.placementAccess.canManage(player, player.world.name)) {
                    player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PLACEMENT_ACCESS))
                    return
                }
                val base = event.clickedBlock ?: return
                val target = if (base.isReplaceable) base else base.getRelative(event.blockFace)
                placeDisk(player, target.location, base, script, unsetProfile, event.hand ?: EquipmentSlot.HAND)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.isCancelled()) return
        val block = event.block
        val placement = plugin.placements.find(block.location) ?: return
        event.isCancelled = true
        val script = plugin.scripts.load(placement.scriptId) ?: return
        if (!plugin.placementAccess.canManage(event.player, placement.world)) return
        event.player.sendMessage(KcI18n.text(event.player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_REMOVE_FROM_MENU))
    }

    private fun placeDisk(
        player: Player,
        location: org.bukkit.Location,
        blockAgainst: org.bukkit.block.Block,
        source: me.awabi2048.kantancommander.model.DiskScript?,
        unsetProfile: DiskProfile,
        hand: EquipmentSlot,
    ) {
        val block = location.block
        if (!block.isReplaceable) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACE_BLOCKED))
            return
        }
        if (plugin.placements.find(block.location) != null) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACE_EXISTS))
            return
        }

        val targetBox = org.bukkit.util.BoundingBox.of(
            block.location.toVector(),
            block.location.clone().add(1.0, 1.0, 1.0).toVector(),
        )
        if (player.boundingBox.overlaps(targetBox)) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACE_BLOCKED))
            return
        }

        val placedScript = source?.let(plugin.scripts::copyForPlacement)
            ?: plugin.scripts.createPlacement(
                player.uniqueId,
                plugin.config.getString("default-disk-name", "Kantan Disk") ?: "Kantan Disk",
                unsetProfile,
            )
        val material = PlacedDiskMaterials.forTimer(placedScript.timer.enabled, placedScript.effectiveProfile)
        if (!block.canPlace(Bukkit.createBlockData(material))) {
            plugin.scripts.delete(placedScript.id)
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACE_BLOCKED))
            return
        }
        val replacedState = block.state
        block.setType(material, false)
        val placeEvent = BlockPlaceEvent(
            block,
            replacedState,
            blockAgainst,
            player.inventory.getItem(hand),
            player,
            true,
            hand
        )
        Bukkit.getPluginManager().callEvent(placeEvent)
        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            replacedState.update(true, false)
            plugin.scripts.delete(placedScript.id)
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACE_BLOCKED))
            return
        }
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
            replacedState.update(true, false)
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACE_BLOCKED))
            return
        }
        val stack = if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
        if (player.gameMode != org.bukkit.GameMode.CREATIVE) stack.amount = (stack.amount - 1).coerceAtLeast(0)
        player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACE_CREATED))
    }
}
