package me.awabi2048.kantancommander.item
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.placement.PlacedBlockMaterials
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class KantanInteractionListener(private val plugin: KantanCommanderPlugin) : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item
        val itemKind = KantanItemService.kind(item)
        val diskId = KantanItemService.diskId(item)
        val clickedPlacement = event.clickedBlock?.let { plugin.placements.find(it.location) }

        if (
            clickedPlacement != null &&
            event.action == Action.RIGHT_CLICK_BLOCK &&
            !player.isSneaking
        ) {
            // 拡張コマンドブロックを手に持った右クリックは通常のブロック配置を優先し、編集画面を開かない。
            if (itemKind == KantanItemKind.BLOCK) return
            event.isCancelled = true
            val script = plugin.scripts.load(clickedPlacement.scriptId) ?: return
            if (!plugin.placementAccess.canManage(player, clickedPlacement.world)) {
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PLACEMENT_ACCESS))
                return
            }
            plugin.editorMenu.open(player, clickedPlacement)
            return
        }

        when (KantanItemPolicy.itemAction(itemKind, event.action, player.isSneaking)) {
            KantanItemAction.NONE -> return
            KantanItemAction.OPEN -> {
                val script = diskId?.let(plugin.scripts::load) ?: return
                event.isCancelled = true
                plugin.editorMenu.open(player, script.id)
            }
            KantanItemAction.PLACE -> {
                event.isCancelled = true
                val script = diskId?.let(plugin.scripts::load) ?: return
                if (!plugin.placementAccess.canManage(player, player.world.name)) {
                    player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PLACEMENT_ACCESS))
                    return
                }
                val base = event.clickedBlock ?: return
                val target = if (base.isReplaceable) base else base.getRelative(event.blockFace)
                placeBlock(player, target.location, base, script, event.hand ?: EquipmentSlot.HAND)
            }
        }
    }

    /** 拡張コマンドブロックの設置。バニラのブロック配置イベントを捕捉して配置物へ変換する。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (KantanItemService.kind(event.itemInHand) != KantanItemKind.BLOCK) return
        event.isCancelled = true
        val player = event.player
        if (!plugin.placementAccess.canManage(player, player.world.name)) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PLACEMENT_ACCESS))
            plugin.logger.info("拡張コマンドブロックの設置を権限不足で拒否: player=${player.name}, world=${player.world.name}")
            return
        }
        plugin.logger.info("拡張コマンドブロックの設置を検出: player=${player.name}, location=${event.block.location}")
        placeBlock(player, event.block.location, event.blockAgainst, null, event.hand)
    }

    /** 拡張コマンドブロックの破壊。管理権限があれば内容をコマンドディスクとして出力して撤去する。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val placement = plugin.placements.find(block.location) ?: return
        if (!plugin.placementAccess.canManage(event.player, placement.world)) {
            event.isCancelled = true
            event.player.sendMessage(KcI18n.text(event.player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PLACEMENT_ACCESS))
            plugin.logger.info("拡張コマンドブロックの破壊を権限不足で拒否: player=${event.player.name}, location=${block.location}")
            return
        }
        event.isCancelled = true
        outputDiskAndRemove(event.player, block, placement)
    }

    private fun placeBlock(
        player: Player,
        location: Location,
        blockAgainst: Block,
        source: DiskScript?,
        hand: EquipmentSlot,
    ) {
        val block = location.block
        // 通常のブロックと同じ体験にするため、失敗時もメッセージを出さず何も起きない扱いにする。
        // 原因の追跡用に、失敗の理由を常時サーバーログへ記録する。
        if (!block.isReplaceable) {
            plugin.logger.info("配置先が置換不能のため設置せず: location=${block.location}")
            return
        }
        if (plugin.placements.find(block.location) != null) {
            plugin.logger.info("既存の配置物があるため設置せず: location=${block.location}")
            return
        }

        val targetBox = org.bukkit.util.BoundingBox.of(
            block.location.toVector(),
            block.location.clone().add(1.0, 1.0, 1.0).toVector(),
        )
        if (player.boundingBox.overlaps(targetBox)) {
            plugin.logger.info("プレイヤーと重なる位置のため設置せず: player=${player.name}, location=${block.location}")
            return
        }

        val placedScript = source?.let(plugin.scripts::copyForPlacement)
            ?: plugin.scripts.createPlacement(
                player.uniqueId,
                plugin.config.getString("default-disk-name", "Kantan Disk") ?: "Kantan Disk",
            )
        val material = PlacedBlockMaterials.forTimer(placedScript.timer.enabled)
        if (!block.canPlace(Bukkit.createBlockData(material))) {
            plugin.scripts.delete(placedScript.id)
            plugin.logger.info("ブロック状態が設置不可のため設置せず: location=${block.location}, material=$material")
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
            plugin.logger.info(
                "BlockPlaceEventがキャンセルされたため設置を中止: location=${block.location}, " +
                    "cancelled=${placeEvent.isCancelled()}, canBuild=${placeEvent.canBuild()}",
            )
            return
        }
        val placement = DiskPlacement(
            block.world.name, block.x, block.y, block.z, placedScript.id,
            player.facing.name, null
        )
        runCatching {
            plugin.placements.add(placement)
            plugin.placements.spawnDisplay(block.world, placement)
        }.onFailure { error ->
            plugin.placements.remove(block.world, block.x, block.y, block.z)
            plugin.scripts.delete(placedScript.id)
            replacedState.update(true, false)
            plugin.logger.log(
                java.util.logging.Level.INFO,
                "配置データの保存または表示体スポーンに失敗したため設置を中止: location=${block.location}",
                error,
            )
            return
        }
        val stack = if (hand == EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
        if (player.gameMode != org.bukkit.GameMode.CREATIVE) stack.amount = (stack.amount - 1).coerceAtLeast(0)
        // イベントキャンセル方式のため、バニラのブロック配置と同じ設置音・パーティクルを自前で再生する。
        val center = block.location.toCenterLocation()
        block.world.playSound(center, Sound.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 1.0f, 0.8f)
        block.world.spawnParticle(
            Particle.BLOCK, center, 32, 0.5, 0.5, 0.5,
            Bukkit.createBlockData(material),
        )
        plugin.logger.info("拡張コマンドブロックを設置: placement=${placement.key}, script=${placedScript.id}")
    }

    /** 破壊時の後始末。内容をコマンドディスクとして出力し、表示・配置・スクリプトを削除する。 */
    private fun outputDiskAndRemove(player: Player, block: Block, placement: DiskPlacement) {
        val world = block.world
        // イベントキャンセル方式のため、バニラのブロック破壊と同じ破壊音・パーティクルを自前で再生する。
        val center = block.location.toCenterLocation()
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f)
        world.spawnParticle(
            Particle.BLOCK, center, 64, 0.5, 0.5, 0.5,
            Bukkit.createBlockData(block.type),
        )
        val source = plugin.scripts.load(placement.scriptId)
        if (source != null) {
            val output = runCatching { plugin.scripts.copyForItem(source) }.getOrNull()
            if (output != null) {
                val item = runCatching { KantanItemService.createDisk(output, player) }.getOrElse { error ->
                    plugin.scripts.delete(output.id)
                    plugin.logger.log(
                        java.util.logging.Level.INFO,
                        "破壊時のコマンドディスク生成に失敗: location=${block.location}, script=${output.id}",
                        error,
                    )
                    return
                }
                player.inventory.addItem(item).values.forEach { world.dropItemNaturally(player.location, it) }
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_DISK_OUTPUT))
                plugin.logger.info("破壊時のディスク出力完了: location=${block.location}, script=${output.id}")
            } else {
                plugin.logger.info("破壊時のスクリプト複製に失敗したため、内容を出力せず破壊: location=${block.location}, script=${placement.scriptId}")
            }
        } else {
            plugin.logger.info("参照スクリプトが消失しているため、内容を出力せず破壊: location=${block.location}, script=${placement.scriptId}")
        }
        plugin.placements.removeDisplay(world, placement.displayId)
        plugin.placements.remove(world, placement.x, placement.y, placement.z)
        block.setType(Material.AIR, false)
        if (source != null) plugin.scripts.delete(source.id)
        plugin.logger.info("拡張コマンドブロックの破壊処理完了: placement=${placement.key}")
    }
}