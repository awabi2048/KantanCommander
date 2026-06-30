package me.awabi2048.kantancommander.item

import java.util.UUID
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.DataManager
import me.awabi2048.kantancommander.data.PlacedDiskManager
import me.awabi2048.kantancommander.data.model.BlockPlacement
import me.awabi2048.kantancommander.util.I18nHelper
import me.awabi2048.kantancommander.util.MwmIntegration
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class DiskInteractionListener(private val plugin: KantanCommanderPlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        if (!DiskItemFactory.isDisk(item)) return
        event.isCancelled = true

        val diskUUID = DiskItemFactory.getDiskUUID(item) ?: return
        val script = DataManager.load(diskUUID) ?: return

        when (event.action) {
            Action.RIGHT_CLICK_BLOCK -> {
                if (player.isSneaking) {
                    val clickedBlock = event.clickedBlock ?: return
                    val targetLoc = clickedBlock.getRelative(event.blockFace).location
                    placeDisk(player, targetLoc, script, event.hand ?: EquipmentSlot.HAND)
                } else {
                    val clicked = event.clickedBlock ?: return
                    val placement = PlacedDiskManager.findByLocation(clicked.location)
                    val checkLoc = placement?.let { clicked.location } ?: player.location
                    if (!canEdit(player, script, checkLoc)) {
                        player.sendMessage(I18nHelper.string(player, "message.not_disk_owner"))
                        return
                    }
                    plugin.sequenceEditorMenu.open(player, script)
                }
            }
            Action.RIGHT_CLICK_AIR -> {
                if (!player.isSneaking) {
                    if (!canEdit(player, script, player.location)) {
                        player.sendMessage(I18nHelper.string(player, "message.not_disk_owner"))
                        return
                    }
                    plugin.sequenceEditorMenu.open(player, script)
                }
            }
            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onBlockPlace(event: BlockPlaceEvent) {
        // 通常のブロック設置経路に流れた場合も、ディスクアイテムは専用の設置処理だけを通す。
        if (DiskItemFactory.isDisk(event.itemInHand)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type != Material.NOTE_BLOCK) return

        val placement = PlacedDiskManager.findByLocation(block.location) ?: return
        val script = DataManager.load(placement.diskUUID) ?: return

        val player = event.player
        if (!canEdit(player, script, block.location)) {
            event.isCancelled = true
            player.sendMessage(I18nHelper.string(player, "message.not_disk_owner"))
            return
        }

        // 表示エンティティと配置記録を消してから、元のディスクを返却する。
        PlacedDiskManager.removeDisplay(block.world, placement.displayEntityUUID)
        PlacedDiskManager.remove(block.world, block.x, block.y, block.z)
        event.isDropItems = false
        val diskItem = DiskItemFactory.createDiskForPlayer(player, script)
        block.world.dropItemNaturally(block.location.add(0.5, 0.5, 0.5), diskItem)
    }

    private fun placeDisk(player: Player, location: Location?, script: me.awabi2048.kantancommander.data.model.DiskScript, hand: EquipmentSlot) {
        val loc = location ?: return
        val block = loc.block

        if (PlacedDiskManager.findByLocation(block.world, block.x, block.y, block.z) != null) {
            player.sendMessage(I18nHelper.string(player, "message.placement_exists"))
            return
        }
        if (!block.type.isAir) {
            player.sendMessage(I18nHelper.string(player, "message.placement_blocked"))
            return
        }

        if (!canEdit(player, script, loc)) {
            player.sendMessage(I18nHelper.string(player, "message.not_disk_owner"))
            return
        }

        // レッドストーン検知対象として NOTE_BLOCK を置く。
        block.type = Material.NOTE_BLOCK

        val placement = BlockPlacement(
            worldName = block.world.name,
            x = block.x, y = block.y, z = block.z,
            diskUUID = script.uuid,
            displayEntityUUID = UUID.randomUUID()
        )
        // BlockDisplay の実UUIDを保存するため、仮UUIDのまま永続化せずスポーン結果で登録する。
        PlacedDiskManager.spawnDisplay(block.world, placement)

        // 設置に成功したときだけ手元のディスクを1つ消費する。
        val handItem = if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
        handItem.amount = (handItem.amount - 1).coerceAtLeast(0)
        if (hand == EquipmentSlot.OFF_HAND) {
            player.inventory.setItemInOffHand(handItem)
        } else {
            player.inventory.setItemInMainHand(handItem)
        }

        player.sendMessage(I18nHelper.string(player, "message.placement_created"))
    }

    private fun canEdit(player: Player, script: me.awabi2048.kantancommander.data.model.DiskScript, location: Location): Boolean {
        if (player.uniqueId == script.creator) return true
        if (player.hasPermission("kankoma.admin")) return true
        val isMember = MwmIntegration.isWorldMember(player, location)
        if (isMember == true) return true
        return false
    }
}
