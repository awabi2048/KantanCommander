package me.awabi2048.kantancommander.feedback

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import java.util.UUID

/**
 * 操作の拒否・失敗理由を利用者へ伝える共通通知です。
 *
 * バニラの実行不可操作に近い赤字ActionBarで簡潔に表示し、同一操作の連打で
 * 大量送信しないよう同一文言の短時間throttleを設けます。恒久ログ(chat)とは
 * 分離し、将来の操作失敗通知もこのサービスへ寄せます。プログラム再生の字幕
 * (TimedActionBarService)とは排他せず、最新の表示を優先します。
 */
class PlayerFeedbackService(
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val throttleMillis: Long = 3000L,
) {
    private data class Record(val message: String, val sentAtMillis: Long)

    private val lastSent = mutableMapOf<UUID, Record>()

    fun sendActionBar(player: Player, message: String) {
        if (!shouldSend(player.uniqueId, message)) return
        player.sendActionBar(Component.text(message, NamedTextColor.RED))
    }

    /** 同一文言の短時間再送を抑止します。Bukkit非依存のため単体テストで検証します。 */
    fun shouldSend(playerId: UUID, message: String): Boolean {
        val now = clockMillis()
        val last = lastSent[playerId]
        if (last != null && last.message == message && now - last.sentAtMillis < throttleMillis) return false
        lastSent[playerId] = Record(message, now)
        return true
    }

    fun forget(player: Player) {
        lastSent.remove(player.uniqueId)
    }
}
