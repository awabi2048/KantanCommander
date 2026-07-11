package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandParam
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import java.util.UUID

class CommandEditMenu(private val plugin: KantanCommanderPlugin) : Listener {
    private data class Session(val scriptId: UUID, val commandIndex: Int? = null)
    private val sessions = mutableMapOf<UUID, Session>()

    fun openTypePicker(player: Player, scriptId: UUID) {
        sessions[player.uniqueId] = Session(scriptId)
        val layout = KcGui.layouts.free45()
        val holder = KcMenuHolder(player.uniqueId, "type")
        val inv = KcGui.inventory(player, holder, layout.size, KcI18n.text(player, "gui.type.title"))
        KcGui.frame(inv)
        val slots = listOf(10, 11, 12, 13, 14, 15, 16)
        CommandType.entries.forEachIndexed { index, type ->
            inv.setItem(slots[index], KcGui.item(
                type.icon,
                KcI18n.text(player, type.key),
                GuiNameStyle.PRIMARY,
                type.params.map { GuiLoreLine.Text(KcI18n.text(player, it.key)) }
            ))
        }
        inv.setItem(layout.backSlot, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")))
        player.openInventory(inv)
    }

    fun openParamEditor(player: Player, scriptId: UUID, commandIndex: Int) {
        val script = plugin.scripts.load(scriptId) ?: return
        val command = script.commands.getOrNull(commandIndex) ?: return
        sessions[player.uniqueId] = Session(scriptId, commandIndex)
        val layout = KcGui.layouts.settings54()
        val holder = KcMenuHolder(player.uniqueId, "params")
        val inv = KcGui.inventory(player, holder, layout.size, KcI18n.text(player, "gui.params.title", mapOf("type" to KcI18n.text(player, command.type.key))))
        KcGui.frame(inv)
        val slots = (10..16) + (19..25)
        command.type.params.forEachIndexed { index, param ->
            val value = command.params[param.id] ?: param.defaultValue
            inv.setItem(slots[index], KcGui.item(
                icon(param),
                "${KcI18n.text(player, param.key)}: $value",
                GuiNameStyle.PRIMARY,
                listOf(KcGui.singleAction(player, KcI18n.text(player, "gui.params.action_cycle")))
            ))
        }
        inv.setItem(layout.backSlot, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")))
        inv.setItem(layout.infoSlot, KcGui.item(Material.BOOK, "#${commandIndex + 1}", GuiNameStyle.MUTED))
        player.openInventory(inv)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? KcMenuHolder ?: return
        if (holder.id != "type" && holder.id != "params") return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val session = sessions[player.uniqueId] ?: return

        if (holder.id == "type") {
            val layout = KcGui.layouts.free45()
            if (event.rawSlot == layout.backSlot) return plugin.editorMenu.open(player, session.scriptId)
            val slots = listOf(10, 11, 12, 13, 14, 15, 16)
            val index = slots.indexOf(event.rawSlot)
            val type = CommandType.entries.getOrNull(index) ?: return
            val script = plugin.scripts.load(session.scriptId) ?: return
            val max = plugin.config.getInt("max-commands-per-disk", 32)
            if (script.commands.size >= max) {
                player.sendMessage(KcI18n.text(player, "message.max_commands", mapOf("max" to max)))
                return
            }
            script.commands.add(type.newCommand())
            plugin.scripts.save(script)
            openParamEditor(player, script.id, script.commands.lastIndex)
            return
        }

        val commandIndex = session.commandIndex ?: return
        val script = plugin.scripts.load(session.scriptId) ?: return
        val command = script.commands.getOrNull(commandIndex) ?: return
        val layout = KcGui.layouts.settings54()
        if (event.rawSlot == layout.backSlot) return plugin.editorMenu.open(player, script.id)

        val slots = (10..16) + (19..25)
        val paramIndex = slots.indexOf(event.rawSlot)
        val param = command.type.params.getOrNull(paramIndex) ?: return
        command.params[param.id] = nextValue(param, command.params[param.id] ?: param.defaultValue)
        plugin.scripts.save(script)
        openParamEditor(player, script.id, commandIndex)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is KcMenuHolder) event.isCancelled = true
    }

    private fun nextValue(param: CommandParam, current: String): String = when (param) {
        is CommandParam.Choice -> param.options[(param.options.indexOf(current).takeIf { it >= 0 } ?: 0).let { (it + 1) % param.options.size }]
        is CommandParam.Number -> (current.toDoubleOrNull()?.plus(1.0) ?: param.defaultValue.toDouble()).toString().removeSuffix(".0")
        is CommandParam.Text -> current
    }

    private fun icon(param: CommandParam): Material = when (param) {
        is CommandParam.Choice -> Material.COMPARATOR
        is CommandParam.Number -> Material.REPEATER
        is CommandParam.Text -> Material.PAPER
    }
}
