package me.awabi2048.kantancommander.command

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.DataManager
import me.awabi2048.kantancommander.data.PlacedDiskManager
import me.awabi2048.kantancommander.data.model.DiskScript
import me.awabi2048.kantancommander.item.DiskItemFactory
import me.awabi2048.kantancommander.util.I18nHelper
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

class KantanCommanderCommand(private val plugin: KantanCommanderPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "disk" -> handleDisk(sender, args.drop(1).toTypedArray())
            "programs" -> handlePrograms(sender)
            "placed" -> handlePlaced(sender, args.drop(1).toTypedArray())
            "reload" -> handleReload(sender)
            "help" -> sendHelp(sender)
            else -> sendHelp(sender)
        }
        return true
    }

    private fun handleDisk(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            sender.sendMessage(tr(sender, "command.usage.disk_give"))
            return
        }
        when (args[0].lowercase()) {
            "give" -> {
                if (!sender.hasPermission("kankoma.admin")) {
                    sender.sendMessage(tr(sender, "message.no_permission"))
                    return
                }
                if (args.size < 2) {
                    sender.sendMessage(tr(sender, "command.usage.disk_give"))
                    return
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null) {
                    sender.sendMessage(tr(sender, "command.error.player_not_found"))
                    return
                }
                val name = args.getOrElse(2) { plugin.config.getString("default-disk-name") ?: "&e新しいディスク" }
                val script = DiskScript(
                    uuid = UUID.randomUUID(),
                    name = name,
                    creator = target.uniqueId
                )
                DataManager.save(script)
                val item = DiskItemFactory.createDiskForPlayer(target, script)
                val leftover = target.inventory.addItem(item)
                if (leftover.isNotEmpty()) {
                    target.world.dropItem(target.location, leftover.values.first())
                }
                sender.sendMessage(I18nHelper.string(target, "message.disk_given", mapOf("player" to target.name)))
            }
            else -> sender.sendMessage(tr(sender, "command.usage.disk_give"))
        }
    }

    private fun handlePrograms(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(tr(sender, "command.error.player_only"))
            return
        }
        plugin.programListMenu.open(sender)
    }

    private fun handlePlaced(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("kankoma.admin")) {
            sender.sendMessage(tr(sender, "message.no_permission"))
            return
        }

        val placements = PlacedDiskManager.listAll()
        if (placements.isEmpty()) {
            sender.sendMessage(tr(sender, "command.placed.empty"))
            return
        }

        sender.sendMessage(tr(sender, "command.placed.header"))
        placements.forEach { p ->
            val script = DataManager.load(p.diskUUID)
            val name = script?.name ?: "?"
            sender.sendMessage(
                tr(
                    sender,
                    "command.placed.line",
                    mapOf(
                        "world" to p.worldName,
                        "x" to p.x.toString(),
                        "y" to p.y.toString(),
                        "z" to p.z.toString(),
                        "name" to name,
                        "uuid" to "${p.diskUUID.toString().take(8)}..."
                    )
                )
            )
        }
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("kankoma.admin")) {
            sender.sendMessage(tr(sender, "message.no_permission"))
            return
        }
        plugin.reloadConfig()
        PlacedDiskManager.rebuildAllDisplays(plugin)
        sender.sendMessage(tr(sender, "message.reloaded"))
    }

    private fun sendHelp(sender: CommandSender) {
        listOf(
            "command.help.header",
            "command.help.disk_give",
            "command.help.programs",
            "command.help.placed",
            "command.help.reload",
            "command.help.help"
        ).forEach { key -> sender.sendMessage(tr(sender, key)) }
    }

    private fun tr(sender: CommandSender, key: String, placeholders: Map<String, Any> = emptyMap()): String =
        I18nHelper.string(sender as? Player, key, placeholders)

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<out String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("disk", "programs", "placed", "reload", "help").filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "disk" -> listOf("give").filter { it.startsWith(args[1].lowercase()) }
                "placed" -> listOf("list").filter { it.startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "disk" -> if (args[1].lowercase() == "give") {
                    Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
                } else emptyList()
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
