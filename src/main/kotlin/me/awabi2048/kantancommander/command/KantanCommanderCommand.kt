package me.awabi2048.kantancommander.command
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

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
            "library", "programs" -> {
                val player = sender as? Player ?: return true
                CCSystem.getAPI().getMenuCommandService().open(
                    player,
                    player,
                    "kantan:library",
                    emptyMap()
                )
            }
            "history" -> {
                val player = sender as? Player ?: return true
                CCSystem.getAPI().getMenuCommandService().open(
                    player,
                    player,
                    "kantan:history",
                    emptyMap(),
                )
            }
            "placed" -> listPlaced(sender, args.getOrNull(1)?.toIntOrNull() ?: 1)
            "reload" -> {
                if (!sender.hasPermission("kankoma.admin")) return true
                val key = if (plugin.reloadManagedSettings()) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_RELOADED
                } else {
                    KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_RELOAD_FAILED
                }
                sender.sendMessage(KcI18n.text(sender as? Player, key))
            }
            "gesture" -> {
                if (!sender.hasPermission("kankoma.admin")) return true
                when (args.getOrNull(1)?.lowercase()) {
                    "on" -> {
                        plugin.config.set("use-gesture-editor", true)
                        plugin.saveConfig()
                        sender.sendMessage("ジェスチャーエディターを使用します。")
                    }
                    "off" -> {
                        plugin.config.set("use-gesture-editor", false)
                        plugin.saveConfig()
                        sender.sendMessage("従来のエディターを使用します。")
                    }
                    else -> sender.sendMessage("/kankoma gesture <on|off>")
                }
            }
            else -> help(sender)
        }
        return true
    }

    /** 配置一覧を10件ごとのページで表示する。ページ範囲外の指定は有効範囲へ丸める。 */
    private fun listPlaced(sender: CommandSender, requestedPage: Int) {
        if (!sender.hasPermission("kankoma.admin")) return
        val player = sender as? Player
        val all = plugin.placements.all()
        val pageSize = 10
        val totalPages = ((all.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = requestedPage.coerceIn(1, totalPages)
        sender.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACEMENTS_HEADER, mapOf("count" to all.size)))
        sender.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACEMENTS_PAGE, mapOf("page" to page, "pages" to totalPages)))
        all.drop((page - 1) * pageSize).take(pageSize).forEach {
            sender.sendMessage(KcI18n.text(sender as? Player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACEMENTS_ENTRY, mapOf(
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
        listOf(
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_HELP_PROGRAMS,
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_HELP_PLACED,
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_HELP_RELOAD,
        ).forEach {
            sender.sendMessage(KcI18n.text(player, it))
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("library", "history", "placed", "reload", "gesture", "help").filter { it.startsWith(args[0], true) }
            2 -> if (args[0].equals("gesture", true)) listOf("on", "off").filter { it.startsWith(args[1], true) } else emptyList()
            else -> emptyList()
        }
    }
}
