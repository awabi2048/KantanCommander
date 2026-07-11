package me.awabi2048.kantancommander.util

import me.awabi2048.kantancommander.KantanCommanderPlugin
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

object KcI18n {
    private const val PREFIX = "kantan_commander_clean"

    fun init(plugin: KantanCommanderPlugin) {
        // 言語ファイルはワークスペース方針に合わせてCC-System側へ集約する。
        plugin.logger.fine("Kantan Commander uses CC-System language keys.")
    }

    fun shutdown() {
    }

    fun text(player: Player?, key: String, vars: Map<String, Any> = emptyMap()): String =
        com.awabi2048.ccsystem.CCSystem.getAPI().getI18nString(player, "$PREFIX.$key", vars)

    fun component(player: Player?, key: String, vars: Map<String, Any> = emptyMap()): Component =
        com.awabi2048.ccsystem.CCSystem.getAPI().getI18nComponent(player, "$PREFIX.$key", vars)
}
