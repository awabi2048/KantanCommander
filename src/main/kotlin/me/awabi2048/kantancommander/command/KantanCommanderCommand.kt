package me.awabi2048.kantancommander.command

import me.awabi2048.kantancommander.KantanCommanderPlugin
import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID
import me.awabi2048.kantancommander.export.ExportResult

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
            "export" -> export(sender, args.getOrNull(1))
            "reload" -> {
                if (!sender.hasPermission("kankoma.admin")) return true
                plugin.reloadConfig()
                sender.sendMessage(KcI18n.text(sender as? Player, "message.reloaded"))
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

    private fun export(sender: CommandSender, rawId: String?) {
        if (!sender.hasPermission("kankoma.admin")) return
        val id = rawId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val script = id?.let(plugin.scripts::load)
        if (script == null) {
            sender.sendMessage("§c${KcI18n.text(sender as? Player, "message.export_invalid_disk")}")
            return
        }
        when (val result = plugin.exporter.export(script)) {
            is ExportResult.Success -> sender.sendMessage(
                "§a${KcI18n.text(sender as? Player, "message.export_success", mapOf("path" to result.directory.absolutePath))}"
            )
            is ExportResult.Failure -> {
                sender.sendMessage("§c${KcI18n.text(sender as? Player, "message.export_failed")}")
                result.errors.forEach { sender.sendMessage("§c- $it") }
            }
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("programs", "placed", "export", "reload", "help").filter { it.startsWith(args[0], true) }
            else -> emptyList()
        }
    }
}
