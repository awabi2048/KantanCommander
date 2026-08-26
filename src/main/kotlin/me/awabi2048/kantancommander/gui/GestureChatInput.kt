package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.UUID

/**
 * ジェスチャーエディターのテキスト入力リスナー。
 *
 * ジェスチャーGUIを閉じず、チャット入力で値を確定します（仕様§4.6）。
 * - 入力待機中のプレイヤーの発言をAsyncPlayerChatEventで捕捉して消費します。
 * - 「キャンセル」と入力すると入力を中止します。
 * - 捕捉した値は同期スレッドでコールバックへ渡します。
 */
class GestureChatInput(private val plugin: KantanCommanderPlugin) : Listener {
    private val pending = mutableMapOf<UUID, PendingInput>()
    private val cancelKeyword = "キャンセル"

    data class PendingInput(val prompt: String, val onResult: (String) -> Unit)

    /** チャット入力待機を開始します。ジェスチャーGUIセッションは閉じません。 */
    fun begin(player: Player, prompt: String, onResult: (String) -> Unit) {
        pending[player.uniqueId] = PendingInput(prompt, onResult)
        player.sendMessage(prompt)
    }

    fun isPending(playerId: UUID): Boolean = pending.containsKey(playerId)

    fun cancel(playerId: UUID) {
        pending.remove(playerId)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val input = pending[event.player.uniqueId] ?: return
        event.isCancelled = true
        pending.remove(event.player.uniqueId)
        val message = event.message
        val player = event.player
        if (message.trim().equals(cancelKeyword, ignoreCase = true)) {
            player.sendMessage("入力を中止しました。")
            return
        }
        // チャットは非同期。キャンセルしたイベントから値だけを同期スレッドへ渡します。
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.isOnline) input.onResult(message)
        })
    }
}