package me.awabi2048.kantancommander.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import me.awabi2048.kantancommander.KantanCommanderPlugin
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

object KcI18n {
    fun init(plugin: KantanCommanderPlugin) {
        // 翻訳データはCC-Systemの埋め込みカタログを唯一の正として扱います。
        plugin.logger.fine("Kantan Commander uses CC-System language keys.")
    }

    fun shutdown() {
    }

    fun text(player: Player?, key: LocalizationKey<String>, vars: Map<String, Any> = emptyMap()): String =
        CCSystem.getAPI().getLocalized(player, key, vars)

    fun list(player: Player?, key: LocalizationKey<List<String>>, vars: Map<String, Any> = emptyMap()): List<String> =
        CCSystem.getAPI().getLocalized(player, key, vars)

    fun component(player: Player?, key: LocalizationKey<String>, vars: Map<String, Any> = emptyMap()): Component =
        CCSystem.getAPI().getI18nComponent(player, key, vars)
}
