package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiLoreFrame
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import io.papermc.paper.datacomponent.item.Tool
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

object DiskItemService {
    private const val CUSTOM_ITEM_ID = "kantan.disk"
    private val customItemIdKey = NamespacedKey("kantancommander", "custom_item_id")
    private val diskIdKey = NamespacedKey("kantancommander", "disk_id")
    private val modelKey = NamespacedKey("minecraft", "music_disc_13")
    private val baseMaterial = Material.POISONOUS_POTATO

    fun create(script: DiskScript, player: Player): ItemStack {
        val item = ItemStack(baseMaterial, 1)
        item.editMeta { meta ->
            meta.displayName(Component.text(script.name, NamedTextColor.YELLOW))
            meta.setItemModel(modelKey)
            meta.setMaxStackSize(1)
            meta.persistentDataContainer.set(customItemIdKey, PersistentDataType.STRING, CUSTOM_ITEM_ID)
            meta.persistentDataContainer.set(diskIdKey, PersistentDataType.STRING, script.id.toString())
        }
        applyCustomItemComponents(item)
        updateLore(item, script, player)
        return item
    }

    fun diskId(item: ItemStack?): UUID? {
        if (item?.type != baseMaterial) {
            return null
        }
        val meta = item.itemMeta ?: return null
        val customItemId = meta.persistentDataContainer.get(customItemIdKey, PersistentDataType.STRING)
        if (customItemId != CUSTOM_ITEM_ID) {
            return null
        }
        val raw = meta.persistentDataContainer.get(diskIdKey, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    fun isDisk(item: ItemStack?): Boolean = diskId(item) != null

    private fun applyCustomItemComponents(item: ItemStack) {
        item.setData(DataComponentTypes.TOOL, Tool.tool().build())
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build())

        // システム全体のカスタムアイテム慣習に合わせ、見た目用の毒じゃがいもを食べ物として扱わせない。
        item.unsetData(DataComponentTypes.FOOD)
        item.unsetData(DataComponentTypes.CONSUMABLE)
    }

    private fun updateLore(item: ItemStack, script: DiskScript, player: Player) {
        item.editMeta { meta ->
            val ownerName = Bukkit.getOfflinePlayer(script.owner).name ?: script.owner.toString().take(8)
            val lore = CCSystem.getAPI().getLoreService().render(
                GuiLoreSpec.Rich(
                    listOf(
                        GuiLoreLine.Data(KcI18n.text(player, "item.commands"), script.commands.size, "§f"),
                        GuiLoreLine.Data(KcI18n.text(player, "item.owner"), ownerName, "§f"),
                        GuiLoreLine.Data(KcI18n.text(player, "item.trigger"), KcI18n.text(player, script.trigger.key), "§f"),
                        GuiLoreLine.Spacer,
                        me.awabi2048.kantancommander.gui.KcGui.action(
                            player,
                            "lore.click.right",
                            KcI18n.text(player, "item.action_edit")
                        ),
                        me.awabi2048.kantancommander.gui.KcGui.action(
                            player,
                            "lore.click.shift_right",
                            KcI18n.text(player, "item.action_place")
                        )
                    ),
                    GuiLoreFrame.BOTH
                )
            )
            meta.lore(lore)
        }
    }
}
