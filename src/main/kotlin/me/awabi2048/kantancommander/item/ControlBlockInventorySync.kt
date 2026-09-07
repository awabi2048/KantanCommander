package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.security.PlacementDecision
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack

/**
 * 設置権限の状態を手持ち表示へ反映します。
 *
 * 権限を持たない利用者の制御ブロックはlocked表示（毒じゃがいも）へ切り替え、
 * クライアント側から通常のブロック設置操作が成立しない状態にします。変換は
 * 論理情報を維持した正規化だけを行い、payloadの作り直しはしません。
 * コンテナ内のアイテムまで世界中から書き換えず、利用者が取得して同期される
 * 境界（所持品・防具・オフハンド）で正規化します。
 */
class ControlBlockInventorySync(private val plugin: KantanCommanderPlugin) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onJoin(event: PlayerJoinEvent) = normalizeSafely(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRespawn(event: PlayerRespawnEvent) = normalizeSafely(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldChange(event: PlayerChangedWorldEvent) = normalizeSafely(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        (event.player as? Player)?.let(::normalizeSafely)
    }

    fun normalize(player: Player) {
        val locked =
            plugin.placementAccess.decide(player, player.world.name) != PlacementDecision.ALLOW
        var changed = false
        val inventory = player.inventory
        // 所持品・防具は実体の参照を直接正規化します。
        (inventory.storageContents + inventory.armorContents).filterNotNull().forEach { stack ->
            if (normalizeStack(player, stack, locked)) changed = true
        }
        val offHand = inventory.itemInOffHand
        if (normalizeStack(player, offHand, locked)) {
            inventory.setItemInOffHand(offHand)
            changed = true
        }
        if (changed) player.updateInventory()
    }

    /**
     * 1スロットを正規化します。複数stackは単体へ分割し、溢れは足元へ落とします。
     * いずれも消失・複製を起こさない経路だけを使います。
     */
    private fun normalizeStack(player: Player, stack: ItemStack, locked: Boolean): Boolean {
        if (KantanItemService.kind(stack) != KantanItemKind.BLOCK) return false
        var changed = false
        if (KantanItemService.isLockedView(stack) != locked) {
            KantanItemService.normalizeControlBlockView(stack, locked)
            changed = true
        }
        if (stack.amount > 1) {
            val extra = stack.amount - 1
            stack.amount = 1
            repeat(extra) {
                val single = stack.clone().apply { amount = 1 }
                player.inventory.addItem(single).values.forEach { overflow ->
                    player.world.dropItemNaturally(player.location, overflow)
                }
            }
            changed = true
        }
        return changed
    }

    private fun normalizeSafely(player: Player) {
        runCatching { normalize(player) }.onFailure { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "制御ブロックの所持品同期に失敗しました: player=${player.name}",
                failure,
            )
        }
    }
}
