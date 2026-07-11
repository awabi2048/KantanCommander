package me.awabi2048.kantancommander.command

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.DiskItemService
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class KantanCommanderCommand(private val plugin: KantanCommanderPlugin) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.getOrNull(0)?.lowercase()) {
            "disk" -> createDisk(sender, args)
            "programs" -> {
                val player = sender as? Player ?: return true
                plugin.programListMenu.open(player)
            }
            "placed" -> listPlaced(sender)
            "reload" -> {
                if (!sender.hasPermission("kankoma.admin")) return true
                plugin.reloadConfig()
                sender.sendMessage(KcI18n.text(sender as? Player, "message.reloaded"))
            }
            else -> help(sender)
        }
        return true
    }

    private fun createDisk(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("kankoma.admin")) return
        if (args.getOrNull(1)?.lowercase() != "give") {
            sender.sendMessage(KcI18n.text(sender as? Player, "message.usage_disk_give"))
            return
        }
        val target = Bukkit.getPlayer(args.getOrNull(2) ?: "") ?: run {
            sender.sendMessage(KcI18n.text(sender as? Player, "message.player_not_found"))
            return
        }
        val name = args.drop(3).joinToString(" ").ifBlank { plugin.config.getString("default-disk-name", "Kantan Disk") ?: "Kantan Disk" }
        val script = plugin.scripts.create(target.uniqueId, name)
        target.inventory.addItem(DiskItemService.create(script, target)).values.forEach { target.world.dropItem(target.location, it) }
        sender.sendMessage(KcI18n.text(sender as? Player, "message.disk_created", mapOf("player" to target.name, "name" to name)))
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
        listOf("help_disk_give", "help_programs", "help_placed", "help_reload").forEach {
            sender.sendMessage(KcI18n.text(player, "message.$it"))
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("disk", "programs", "placed", "reload", "help").filter { it.startsWith(args[0], true) }
            2 -> if (args[0].equals("disk", true)) listOf("give") else emptyList()
            3 -> if (args[0].equals("disk", true) && args[1].equals("give", true)) Bukkit.getOnlinePlayers().map { it.name } else emptyList()
            else -> emptyList()
        }
    }
}
