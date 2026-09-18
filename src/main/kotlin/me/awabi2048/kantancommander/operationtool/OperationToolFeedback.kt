package me.awabi2048.kantancommander.operationtool

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/**
 * issue #14の操作ツールwandの操作ログ通知です。
 *
 * 操作ログはチャットではなくActionBarへ出し、結果の種別で色付けして
 * 分かりやすくします。現在のモード・選択数は常に先頭へ併記し、
 * 状態確認と操作結果を1行で把握できるようにします。
 */
object OperationToolFeedback {
    /**
     * 現在のモード・選択数の状態行を返します。
     *
     * 操作結果がない場合の定常表示です。地味な色に抑え、結果色を邪魔しません。
     */
    fun statusLine(player: Player, session: OperationToolSession): Component {
        val modeName = KcI18n.text(player, session.mode.displayNameKey())
        return Component.text(
            KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STATUS,
                mapOf("mode" to modeName, "count" to session.selected().size),
            ),
            NamedTextColor.GRAY,
        )
    }

    /** 状態行だけをActionBarへ出します。 */
    fun showStatus(player: Player, session: OperationToolSession) {
        player.sendActionBar(statusLine(player, session))
    }

    /** 状態行に続けて操作結果を色付きでActionBarへ出します。 */
    fun notify(player: Player, session: OperationToolSession, text: String, color: NamedTextColor) {
        player.sendActionBar(
            statusLine(player, session)
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(text, color)),
        )
    }

    /** 言語キーで操作結果を色付きでActionBarへ出します。 */
    fun notifyKey(
        player: Player,
        session: OperationToolSession,
        key: LocalizationKey<String>,
        vars: Map<String, Any> = emptyMap(),
        color: NamedTextColor,
    ) {
        notify(player, session, KcI18n.text(player, key, vars), color)
    }
}
