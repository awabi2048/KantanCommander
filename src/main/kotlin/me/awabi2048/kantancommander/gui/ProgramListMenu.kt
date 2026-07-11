package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.MenuClickType
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.DiskItemService
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import java.util.UUID

class ProgramListMenu(private val plugin: KantanCommanderPlugin) : Listener {
    private val pages = mutableMapOf<UUID, Int>()

    fun open(player: Player, page: Int = pages[player.uniqueId] ?: 0) {
        val layout = KcGui.layouts.pagedList54()
        val holder = KcMenuHolder(player.uniqueId, "programs")
        val inv = KcGui.inventory(player, holder, layout.size, KcI18n.text(player, "gui.programs.title"))
        pages[player.uniqueId] = page
        render(player, inv)
        player.openInventory(inv)
        KcGui.sounds.onMenuOpen(player, "kantan_programs")
    }

    private fun render(player: Player, inv: org.bukkit.inventory.Inventory) {
        inv.clear()
        KcGui.frame(inv)
        val layout = KcGui.layouts.pagedList54()
        val scripts = plugin.scripts.listOwned(player.uniqueId)
        val total = ((scripts.size + layout.itemSlots.size - 1) / layout.itemSlots.size).coerceAtLeast(1)
        val page = (pages[player.uniqueId] ?: 0).coerceIn(0, total - 1)
        pages[player.uniqueId] = page

        scripts.drop(page * layout.itemSlots.size).take(layout.itemSlots.size).forEachIndexed { index, script ->
            inv.setItem(layout.itemSlots[index], KcGui.item(
                Material.MUSIC_DISC_13,
                script.name,
                GuiNameStyle.PRIMARY,
                listOf(
                    GuiLoreLine.Data(KcI18n.text(player, "item.commands"), script.commands.size, "§f"),
                    GuiLoreLine.Data(KcI18n.text(player, "item.trigger"), KcI18n.text(player, script.trigger.key), "§f"),
                    GuiLoreLine.Spacer,
                    KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.programs.action_get")),
                    KcGui.action(player, "lore.click.right", KcI18n.text(player, "gui.programs.action_edit"))
                )
            ))
        }

        inv.setItem(layout.previousPageSlot, if (page > 0) KcGui.item(Material.ARROW, "<", GuiNameStyle.DEFAULT) else KcGui.elements.decoration(Material.BARRIER))
        inv.setItem(layout.nextPageSlot, if (page < total - 1) KcGui.item(Material.ARROW, ">", GuiNameStyle.DEFAULT) else KcGui.elements.decoration(Material.BARRIER))
        inv.setItem(layout.backSlot, KcGui.elements.backItem(KcI18n.text(player, "gui.common.close")))
        inv.setItem(layout.infoSlot, KcGui.item(Material.BOOK, "${page + 1}/$total", GuiNameStyle.MUTED))
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? KcMenuHolder ?: return
        if (holder.id != "programs") return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (holder.owner != player.uniqueId) return
        val layout = KcGui.layouts.pagedList54()
        val page = pages[player.uniqueId] ?: 0
        when (event.rawSlot) {
            layout.previousPageSlot -> if (page > 0) open(player, page - 1)
            layout.nextPageSlot -> open(player, page + 1)
            layout.backSlot -> player.closeInventory()
            in layout.itemSlots -> {
                val index = page * layout.itemSlots.size + layout.itemSlots.indexOf(event.rawSlot)
                val script = plugin.scripts.listOwned(player.uniqueId).getOrNull(index) ?: return
                KcGui.sounds.onMenuClick(player, "kantan_programs", MenuClickType.CONFIRM)
                if (event.isRightClick) {
                    plugin.editorMenu.open(player, script.id)
                } else {
                    player.inventory.addItem(DiskItemService.create(script, player)).values.forEach { player.world.dropItem(player.location, it) }
                }
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is KcMenuHolder) event.isCancelled = true
    }
}
