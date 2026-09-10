package me.awabi2048.kantancommander.util

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import me.awabi2048.kantancommander.KantanCommanderPlugin
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

object KcI18n {
    /**
     * 引数なしの静的文言の共有キャッシュです。
     *
     * issue #15 の再生成抑止として、上下画面の再構築ごとに繰り返される
     * 固定ラベルの解決を使い回します。引数付きの動的文言は毎回解決します。
     * 無効化条件は locale＋キーIDへの一元化（言語切替えへ追従）と、
     * プラグイン無効化時の破棄だけにします。
     */
    private val staticCache = StaticTextCache()

    fun init(plugin: KantanCommanderPlugin) {
        // 翻訳データはCC-Systemの埋め込みカタログを唯一の正として扱います。
        plugin.logger.fine("Kantan Commander uses CC-System language keys.")
    }

    fun shutdown() {
        staticCache.clear()
    }

    fun text(player: Player?, key: LocalizationKey<String>, vars: Map<String, Any> = emptyMap()): String =
        if (vars.isEmpty()) {
            staticCache.text(localeId(player), key.id) {
                CCSystem.getAPI().getLocalized(player, key, emptyMap())
            }
        } else {
            CCSystem.getAPI().getLocalized(player, key, vars)
        }

    fun list(player: Player?, key: LocalizationKey<List<String>>, vars: Map<String, Any> = emptyMap()): List<String> =
        if (vars.isEmpty()) {
            staticCache.textList(localeId(player), key.id) {
                CCSystem.getAPI().getLocalized(player, key, emptyMap())
            }
        } else {
            CCSystem.getAPI().getLocalized(player, key, vars)
        }

    fun component(player: Player?, key: LocalizationKey<String>, vars: Map<String, Any> = emptyMap()): Component =
        CCSystem.getAPI().getI18nComponent(player, key, vars)

    /**
     * キャッシュ分離のためのlocale識別子です。
     *
     * CC-Systemの解決と同じくプレイヤーlocaleを基準にし、取得不能時と
     * 非プレイヤー時は既定欄へ寄せます。言語切替え後は別欄として再解決します。
     */
    private fun localeId(player: Player?): String =
        runCatching { player?.locale()?.toString() }.getOrNull() ?: "default"
}
