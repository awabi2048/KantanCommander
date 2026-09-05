package me.awabi2048.kantancommander.command

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.KantanItemGrantService
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class KantanCommanderCommand(
    private val plugin: KantanCommanderPlugin,
    private val itemGrantService: KantanItemGrantService,
) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val subcommand = args.getOrNull(0)?.lowercase()
        if (!requireSubcommandPermission(sender, subcommand)) return true
        when (subcommand) {
            null -> grantControlBlock(sender)
            "library" -> openLibrary(sender)
            "history" -> openHistory(sender)
            "placed" -> listPlaced(sender, args.getOrNull(1)?.toIntOrNull() ?: 1)
            "reload" -> reload(sender)
            "help" -> help(sender)
            else -> help(sender)
        }
        return true
    }

    /** 一般ユーザーが自分へ制御ブロックを取得する唯一の直接コマンド入口です。 */
    private fun grantControlBlock(sender: CommandSender) {
        val player = sender as? Player ?: return
        val result = itemGrantService.grant(
            sender = sender,
            target = player,
            amount = 1,
            source = "/kankoma",
        )
        val messageKey = if (result.success) {
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_GRANT_SUCCESS
        } else {
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_GRANT_FAILED
        }
        player.sendMessage(KcI18n.text(player, messageKey))
    }

    private fun openLibrary(sender: CommandSender) {
        val player = sender as? Player ?: return
        CCSystem.getAPI().getMenuCommandService().open(
            player,
            player,
            "kantan:library",
            emptyMap(),
        )
    }

    private fun openHistory(sender: CommandSender) {
        val player = sender as? Player ?: return
        CCSystem.getAPI().getMenuCommandService().open(
            player,
            player,
            "kantan:history",
            emptyMap(),
        )
    }

    /** 配置一覧を10件ごとのページで表示する。ページ範囲外の指定は有効範囲へ丸める。 */
    private fun listPlaced(sender: CommandSender, requestedPage: Int) {
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
                "script" to it.scriptId,
            )))
        }
    }

    private fun reload(sender: CommandSender) {
        val key = if (plugin.reloadManagedSettings()) {
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_RELOADED
        } else {
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_RELOAD_FAILED
        }
        sender.sendMessage(KcI18n.text(sender as? Player, key))
    }

    private fun help(sender: CommandSender) {
        if (!requirePermission(sender, KantanCommandPermissions.HELP)) return
        val player = sender as? Player
        buildList {
            if (sender.hasPermission(KantanCommandPermissions.ROOT)) {
                add(KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_HELP_GRANT)
            }
            if (
                sender.hasPermission(KantanCommandPermissions.LIBRARY) ||
                sender.hasPermission(KantanCommandPermissions.HISTORY)
            ) {
                add(KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_HELP_PROGRAMS)
            }
            if (sender.hasPermission(KantanCommandPermissions.PLACED)) {
                add(KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_HELP_PLACED)
            }
            if (sender.hasPermission(KantanCommandPermissions.RELOAD)) {
                add(KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_HELP_RELOAD)
            }
        }.forEach { key ->
            sender.sendMessage(KcI18n.text(player, key))
        }
    }

    private fun requireSubcommandPermission(sender: CommandSender, subcommand: String?): Boolean {
        val permission = KantanCommandPermissions.forSubcommand(subcommand) ?: return true
        return requirePermission(sender, permission)
    }

    private fun requirePermission(sender: CommandSender, permission: String): Boolean {
        if (sender.hasPermission(permission)) return true
        sender.sendMessage(KcI18n.text(sender as? Player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PERMISSION))
        return false
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> buildList {
                if (sender.hasPermission(KantanCommandPermissions.HISTORY)) add("history")
                if (sender.hasPermission(KantanCommandPermissions.LIBRARY)) add("library")
                if (sender.hasPermission(KantanCommandPermissions.PLACED)) add("placed")
                if (sender.hasPermission(KantanCommandPermissions.RELOAD)) add("reload")
                if (sender.hasPermission(KantanCommandPermissions.HELP)) add("help")
            }.filter { it.startsWith(args[0], true) }
            2 -> emptyList()
            else -> emptyList()
        }
    }
}
