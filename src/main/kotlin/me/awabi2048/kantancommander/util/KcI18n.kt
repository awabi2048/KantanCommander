package me.awabi2048.kantancommander.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import me.awabi2048.kantancommander.KantanCommanderPlugin
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

object KcI18n {
    private const val PREFIX = "kantan_commander_clean"

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
        CCSystem.getAPI().getI18nComponent(player, key.id, vars)

    @Deprecated("固定キーには生成済みLocalizationKeyを使用してください。有限な動的キーファミリー専用です。")
    fun text(player: Player?, relativeKey: String, vars: Map<String, Any> = emptyMap()): String =
        dynamicText(player, relativeKey, vars)

    @Deprecated("固定キーには生成済みLocalizationKeyを使用してください。有限な動的キーファミリー専用です。")
    fun list(player: Player?, relativeKey: String, vars: Map<String, Any> = emptyMap()): List<String> =
        dynamicList(player, relativeKey, vars)

    @Deprecated("固定キーには生成済みLocalizationKeyを使用してください。有限な動的キーファミリー専用です。")
    fun component(player: Player?, relativeKey: String, vars: Map<String, Any> = emptyMap()): Component =
        dynamicComponent(player, relativeKey, vars)

    /** enumや定義テーブルから組み立てる有限な動的キーファミリーだけに使用します。 */
    fun dynamicText(player: Player?, relativeKey: String, vars: Map<String, Any> = emptyMap()): String =
        CCSystem.getAPI().getI18nString(player, "$PREFIX.$relativeKey", vars)

    /** TextListを返す有限な動的キーファミリーだけに使用します。 */
    fun dynamicList(player: Player?, relativeKey: String, vars: Map<String, Any> = emptyMap()): List<String> =
        CCSystem.getAPI().getI18nStringList(player, "$PREFIX.$relativeKey", vars)

    /** Dialog定義などの有限な動的キーファミリーをComponentとして解決します。 */
    fun dynamicComponent(player: Player?, relativeKey: String, vars: Map<String, Any> = emptyMap()): Component =
        CCSystem.getAPI().getI18nComponent(player, "$PREFIX.$relativeKey", vars)
}
