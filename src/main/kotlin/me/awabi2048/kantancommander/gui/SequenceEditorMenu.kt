package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.MenuClickType
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import java.util.UUID

class SequenceEditorMenu(private val plugin: KantanCommanderPlugin) : Listener {
    private val sessions = mutableMapOf<UUID, UUID>()

    fun open(player: Player, scriptId: UUID) {
        val script = plugin.scripts.load(scriptId) ?: return
        sessions[player.uniqueId] = scriptId
        val layout = KcGui.layouts.settings54()
        val holder = KcMenuHolder(player.uniqueId, "editor")
        val inv = KcGui.inventory(player, holder, layout.size, KcI18n.text(player, "gui.editor.title", mapOf("name" to script.name)))
        KcGui.frame(inv)

        // 本文領域だけに編集対象と操作を置き、共有フッターの戻る/情報スロットを侵食しない。
        val commandSlots = (10..16) + (19..25) + (28..34)
        script.commands.take(commandSlots.size).forEachIndexed { index, command ->
            inv.setItem(commandSlots[index], KcGui.item(
                command.type.icon,
                "#${index + 1} ${KcI18n.text(player, command.type.key)}",
                GuiNameStyle.DEFAULT,
                listOf(
                    GuiLoreLine.Data(KcI18n.text(player, "gui.editor.summary"), command.summary(), "§f"),
                    GuiLoreLine.Spacer,
                    KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.action_edit")),
                    KcGui.action(player, "lore.click.right", KcI18n.text(player, "gui.editor.action_delete"))
                )
            ))
        }

        val actionSlots = listOf(37, 38, 39, 40, 41, 42, 43)
        val actions = listOf(
            Material.LIME_WOOL to KcI18n.text(player, "gui.editor.add"),
            Material.FIREWORK_ROCKET to KcI18n.text(player, "gui.editor.test"),
            Material.REDSTONE to KcI18n.text(player, "gui.editor.trigger", mapOf("trigger" to KcI18n.text(player, script.trigger.key))),
            Material.DIAMOND to KcI18n.text(player, "gui.editor.save")
        )
        actions.forEachIndexed { index, (material, name) -> inv.setItem(actionSlots[index], KcGui.item(material, name, GuiNameStyle.PRIMARY)) }

        inv.setItem(layout.backSlot, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")))
        inv.setItem(layout.infoSlot, KcGui.item(Material.BOOK, "${script.commands.size}", GuiNameStyle.MUTED))
        player.openInventory(inv)
        KcGui.sounds.onMenuOpen(player, "kantan_editor")
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? KcMenuHolder ?: return
        if (holder.id != "editor") return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val scriptId = sessions[player.uniqueId] ?: return
        val script = plugin.scripts.load(scriptId) ?: return
        val layout = KcGui.layouts.settings54()
        val commandSlots = (10..16) + (19..25) + (28..34)

        when (event.rawSlot) {
            layout.backSlot -> plugin.programListMenu.open(player)
            37 -> plugin.commandEditMenu.openTypePicker(player, script.id)
            38 -> {
                plugin.executor.execute(script.id, player.location, player)
                KcGui.sounds.onMenuClick(player, "kantan_editor", MenuClickType.CONFIRM)
            }
            39 -> {
                script.trigger = script.trigger.next()
                plugin.scripts.save(script)
                open(player, script.id)
            }
            40 -> {
                plugin.scripts.save(script)
                player.sendMessage(KcI18n.text(player, "message.saved"))
                KcGui.sounds.onMenuClick(player, "kantan_editor", MenuClickType.CONFIRM)
            }
            in commandSlots -> {
                val index = commandSlots.indexOf(event.rawSlot)
                if (index !in script.commands.indices) return
                if (event.isRightClick) {
                    script.commands.removeAt(index)
                    plugin.scripts.save(script)
                    open(player, script.id)
                } else {
                    plugin.commandEditMenu.openParamEditor(player, script.id, index)
                }
            }
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is KcMenuHolder) event.isCancelled = true
    }
}
