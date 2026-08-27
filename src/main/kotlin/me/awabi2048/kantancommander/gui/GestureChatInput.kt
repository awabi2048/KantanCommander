package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import com.destroystokyo.paper.event.player.PlayerJumpEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ジェスチャーエディターのテキスト入力リスナー。
 *
 * ジェスチャーGUIを閉じず、チャット入力で値を確定します（仕様§4.6）。
 * - 入力待機中のプレイヤーの発言をAsyncPlayerChatEventで捕捉して消費します。
 * - 「キャンセル」と入力すると入力を中止します。
 * - 捕捉した値は同期スレッドでコールバックへ渡します。
 */
class GestureChatInput(private val plugin: KantanCommanderPlugin) : Listener {
    /**
     * AsyncPlayerChatEventは非同期で届くため、GUI操作（メインスレッド）と同じ
     * HashMapを共有すると、キャンセル直後の発言が古い入力へ配送されます。
     * ConcurrentHashMapとトークン照合で、画面を閉じた後の遅延コールバックも遮断します。
     */
    private val pending = ConcurrentHashMap<UUID, PendingInput>()
    private val activeTokens = ConcurrentHashMap<UUID, UUID>()
    private val cancelKeyword = "キャンセル"

    data class PendingInput(
        val token: UUID,
        val prompt: String,
        val onResult: (String) -> Unit,
    )

    /** チャット入力待機を開始します。ジェスチャーGUIセッションは閉じません。 */
    fun begin(player: Player, prompt: String, onResult: (String) -> Unit) {
        // 同一プレイヤーの再入力開始は前の入力を置き換えます。
        cancel(player.uniqueId)
        val token = UUID.randomUUID()
        activeTokens[player.uniqueId] = token
        pending[player.uniqueId] = PendingInput(token, prompt, onResult)
        player.sendMessage(prompt)
    }

    fun isPending(playerId: UUID): Boolean = pending.containsKey(playerId)

    fun cancel(playerId: UUID) {
        pending.remove(playerId)
        activeTokens.remove(playerId)
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val input = pending[event.player.uniqueId] ?: return
        // begin()/cancel() と同時に実行された場合、現在の入力を横取りしません。
        if (!pending.remove(event.player.uniqueId, input)) return
        event.isCancelled = true
        val message = event.message
        val player = event.player
        if (message.trim().equals(cancelKeyword, ignoreCase = true)) {
            activeTokens.remove(player.uniqueId, input.token)
            player.sendMessage("入力を中止しました。")
            return
        }
        // チャットは非同期。キャンセルしたイベントから値だけを同期スレッドへ渡します。
        Bukkit.getScheduler().runTask(plugin, Runnable {
            // GUIを閉じる／別画面へ移る／再入力を始めるとトークンが無効になります。
            // この確認を同期コールバック直前にも行い、イベント捕捉後の競合を防ぎます。
            if (player.isOnline && activeTokens[player.uniqueId] == input.token) {
                activeTokens.remove(player.uniqueId, input.token)
                input.onResult(message)
            } else {
                activeTokens.remove(player.uniqueId, input.token)
            }
        })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        // 切断したプレイヤーの入力コールバックを保持し続けないようにします。
        cancel(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        // Fキーは通常操作・GUI操作のどちらでも、保留中のチャット入力を
        // その場で無効化します。GUI側でcloseされる場合の通知がAPIにないため、
        // イベント境界でもキャンセルしてすり抜けを防ぎます。
        cancel(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onTeleport(event: PlayerTeleportEvent) = cancel(event.player.uniqueId)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onWorldChanged(event: PlayerChangedWorldEvent) = cancel(event.player.uniqueId)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onDeath(event: PlayerDeathEvent) = cancel(event.entity.uniqueId)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onJump(event: PlayerJumpEvent) {
        // GestureGuiの終了操作はShift＋ジャンプだけです。通常のジャンプで
        // 入力を中断しないよう、同じ条件をここでも共有します。
        if (event.player.isSneaking) cancel(event.player.uniqueId)
    }
}
