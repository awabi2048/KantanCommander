package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Gestureエディターのlifecycle保証です。
 *
 * issue #15 の終了・破棄条件を一箇所へ集約します。CC-System側の終了通知に加え、
 * 退室時はFacade側のセッションを確実に閉じ、ワールド移動時は一時previewを
 * 破棄して保存値との不整合を残しません。表示Entity自体はCC-System側で管理され、
 * ここではKantan側のsession／cache／transient値の残留防止だけを担当します。
 */
class GestureSessionCleanupListener(
    private val plugin: KantanCommanderPlugin,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onQuit(event: PlayerQuitEvent) {
        runCatching { plugin.gestureEditor.close(event.player) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        // 追従表示は新ワールドでも継続しますが、操作中の一時previewだけは
        // 旧ワールドの文脈に結び付くため破棄し、保存値へ戻します。
        runCatching { plugin.gestureEditor.discardPreview(event.player) }
    }
}
