package me.awabi2048.kantancommander.command

import me.awabi2048.kantancommander.KantanCommanderPlugin
import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class KantanCommanderCommand(private val plugin: KantanCommanderPlugin) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.getOrNull(0)?.lowercase()) {
            "programs" -> {
                val player = sender as? Player ?: return true
                CCSystem.getAPI().getMenuCommandService().open(
                    player,
                    player,
                    "kantan:programs",
                    emptyMap()
                )
            }
            "placed" -> listPlaced(sender)
            "reload" -> {
                if (!sender.hasPermission("kankoma.admin")) return true
                val key = if (plugin.reloadManagedSettings()) "message.reloaded" else "message.reload_failed"
                sender.sendMessage(KcI18n.text(sender as? Player, key))
            }
            else -> help(sender)
        }
        return true
    }

    private fun listPlaced(sender: CommandSender) {
        if (!sender.hasPermission("kankoma.admin")) return
        sender.sendMessage(KcI18n.text(sender as? Player, "message.placements_header", mapOf("count" to plugin.placements.all().size)))
        plugin.placements.all().take(10).forEach {
            sender.sendMessage(KcI18n.text(sender as? Player, "message.placements_entry", mapOf(
                "world" to it.world,
                "x" to it.x,
                "y" to it.y,
                "z" to it.z,
                "script" to it.scriptId
            )))
        }
    }

    private fun help(sender: CommandSender) {
        val player = sender as? Player
        listOf("help_programs", "help_placed", "help_reload").forEach {
            sender.sendMessage(KcI18n.text(player, "message.$it"))
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("programs", "placed", "reload", "help").filter { it.startsWith(args[0], true) }
            else -> emptyList()
        }
    }
}
