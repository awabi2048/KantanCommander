package me.awabi2048.kantancommander.util

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Axiom併用時の注意喚起です。
 *
 * Axiom有効プレイヤーがエディターを開いた際と `/kankoma` を実行した際に、
 * チャットへ警告を1件表示します。AxiomPaperの有無・有効状態は
 * `canUseAxiom(player)` を正本とし、未導入環境では何も表示しません。
 */
object AxiomUsageWarning {
    private const val AXIOM_PLUGIN_NAME = "AxiomPaper"

    fun isAxiomActive(plugin: Plugin, player: Player): Boolean {
        if (!plugin.server.pluginManager.isPluginEnabled(AXIOM_PLUGIN_NAME)) return false
        // AxiomPaper JARが存在しない環境ではクラス解決自体が失敗するため、
        // 例外・エラーは警告なしへ劣化させ、コマンドやエディターの本処理を止めません。
        return runCatching {
            com.moulberry.axiom.AxiomPaper.PLUGIN.canUseAxiom(player)
        }.getOrDefault(false)
    }

    fun warnIfActive(plugin: Plugin, player: Player) {
        if (isAxiomActive(plugin, player)) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_AXIOM_WARNING))
        }
    }
}
