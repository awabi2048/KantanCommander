package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import me.awabi2048.kantancommander.data.model.DiskScript
import me.awabi2048.kantancommander.util.I18nHelper
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.UUID

object DiskItemFactory {
    private val DISK_UUID_KEY = NamespacedKey("kantancommander", "disk_uuid")
    private val DISK_MODEL_KEY = NamespacedKey("minecraft", "music_disc_13")

    fun createDisk(script: DiskScript): ItemStack {
        val item = ItemStack(Material.POISONOUS_POTATO, 1)

        item.editMeta { meta ->
            meta.displayName(Component.text(script.name, NamedTextColor.YELLOW))
            meta.setItemModel(DISK_MODEL_KEY)
            meta.persistentDataContainer.set(DISK_UUID_KEY, PersistentDataType.STRING, script.uuid.toString())
        }

        return item
    }

    fun createDiskForPlayer(player: Player, script: DiskScript): ItemStack {
        val item = createDisk(script)
        updateLore(item, script, player)
        return item
    }

    fun getDiskUUID(item: ItemStack): UUID? {
        val meta = item.itemMeta ?: return null
        val raw = meta.persistentDataContainer.get(DISK_UUID_KEY, PersistentDataType.STRING) ?: return null
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun isDisk(item: ItemStack): Boolean {
        if (item.type != Material.POISONOUS_POTATO) return false
        return getDiskUUID(item) != null
    }

    fun updateLore(item: ItemStack, script: DiskScript, player: Player?) {
        item.editMeta { meta ->
            val creatorName = Bukkit.getOfflinePlayer(script.creator).name ?: script.creator.toString().take(8)

            // ディスク表示もメニューLoreと同じルールに通し、区切り線の揺れを避ける。
            val lore = CCSystem.getAPI().getLoreService().render(
                GuiLoreSpec.Rich(
                    listOf(
                        GuiLoreLine.Data(I18nHelper.string(player, "item.disk_lore.commands"), script.commands.size, "§f"),
                        GuiLoreLine.Data(I18nHelper.string(player, "item.disk_lore.creator"), creatorName, "§f"),
                        GuiLoreLine.Data(I18nHelper.string(player, "item.disk_lore.trigger"), I18nHelper.string(player, script.triggerType.displayNameKey), "§f"),
                        GuiLoreLine.Spacer,
                        GuiLoreLine.SingleAction(I18nHelper.string(player, "item.disk_lore.action_edit")),
                        GuiLoreLine.SingleAction(I18nHelper.string(player, "item.disk_lore.action_place")),
                    ),
                    GuiLoreFrame.BOTH
                )
            )

            meta.lore(lore)
        }
    }
}
