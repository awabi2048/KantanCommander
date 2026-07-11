package me.awabi2048.kantancommander.util

import com.awabi2048.ccsystem.CCSystem
import me.awabi2048.kantancommander.KantanCommanderPlugin
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

object I18nHelper {
    private const val KEY_PREFIX = "kantan_commander"

    fun init(plugin: KantanCommanderPlugin) {
        // 言語ファイルは cc-system/src/main/resources/lang/kantan/ 側に集約する。
        // Kantan Commander 側では独自 source を登録せず、CC-System の検証済み統合辞書を参照する。
    }

    fun shutdown() {
    }

    fun string(player: Player?, key: String, placeholders: Map<String, Any> = emptyMap()): String =
        CCSystem.getAPI().getI18nString(player, "$KEY_PREFIX.$key", placeholders)

    fun component(player: Player?, key: String, placeholders: Map<String, Any> = emptyMap()): Component =
        CCSystem.getAPI().getI18nComponent(player, "$KEY_PREFIX.$key", placeholders)
}
