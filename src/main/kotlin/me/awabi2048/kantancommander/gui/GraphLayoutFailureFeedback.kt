package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/**
 * グラフ変換失敗時の利用者向け表現を、ジェスチャーGUIとインベントリGUIで共有します。
 *
 * 描画変換の内部例外を各画面が個別に文章化すると、片方だけ通知が消えたり、ロケール
 * 切り替え後に片方だけ未翻訳になったりします。ここでは既存の型付きローカライズキー
 * だけを使い、描画不能と保存不能の通知を同じ判定規則で返します。
 */
internal object GraphLayoutFailureFeedback {
    fun renderMessage(player: Player): Component =
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_UPPER_RENDER)

    fun reopenHint(player: Player): Component =
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT)

    fun saveMessage(player: Player): Component =
        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED)

    /** 描画不能をチャットでも明示し、画面更新だけが失敗したように見せないための通知です。 */
    fun sendRenderFailure(player: Player) {
        player.sendMessage(renderMessage(player).color(NamedTextColor.RED))
        player.sendMessage(reopenHint(player).color(NamedTextColor.GRAY))
    }

    /** 例外のcause chainにレイアウト変換失敗が含まれるかを判定します。 */
    fun isLayoutFailure(failure: Throwable): Boolean {
        val visited = mutableSetOf<Throwable>()
        var current: Throwable? = failure
        while (current != null && visited.add(current)) {
            if (current is GraphLayoutException) return true
            current = current.cause
        }
        return false
    }

    /** 更新操作の失敗を、描画失敗なら専用文言、それ以外なら保存失敗として返します。 */
    fun operationMessage(player: Player, failure: Throwable): Component =
        if (isLayoutFailure(failure)) renderMessage(player) else saveMessage(player)
}
