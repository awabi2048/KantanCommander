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
        // 所持品は表示変換で実体が替わるため、位置指定で書き戻します。
        val storage = inventory.storageContents
        storage.forEachIndexed { index, stack ->
            if (stack != null && normalizeSlot(player, { inventory.setItem(index, it) }, stack, locked)) {
                changed = true
            }
        }
        // 防具は配列をまとめて書き戻します。
        val armor = inventory.armorContents
        var armorChanged = false
        armor.forEachIndexed { index, stack ->
            if (stack != null && normalizeSlot(player, { armor[index] = it }, stack, locked)) {
                armorChanged = true
            }
        }
        if (armorChanged) {
            inventory.armorContents = armor
            changed = true
        }
        val offHand = inventory.itemInOffHand
        if (normalizeSlot(player, inventory::setItemInOffHand, offHand, locked)) {
            changed = true
        }
        if (changed) player.updateInventory()
    }

    /**
     * 1スロットを正規化します。複数stackは単体へ分割し、溢れは足元へ落とします。
     * いずれも消失・複製を起こさない経路だけを使います。
     */
    private fun normalizeSlot(
        player: Player,
        writeBack: (ItemStack) -> Unit,
        stack: ItemStack,
        locked: Boolean,
    ): Boolean {
        if (KantanItemService.kind(stack) != KantanItemKind.BLOCK) return false
        var changed = false
        val converted = KantanItemService.normalizeControlBlockView(stack, locked) ?: return false
        if (!converted.isSimilar(stack)) {
            // withTypeの複製は新規実体のため、元のスロットへ書き戻します。
            // PDC・lore・同梱内容は正規化で維持されます。
            writeBack(converted)
            changed = true
        }
        val target = if (changed) converted else stack
        if (target.amount > 1) {
            val extra = target.amount - 1
            target.amount = 1
            repeat(extra) {
                val single = target.clone().apply { amount = 1 }
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
