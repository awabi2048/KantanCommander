package me.awabi2048.kantancommander.feedback

import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/** 通知throttleの記録を退室時に破棄し、UUID台帳の残留を防ぎます。 */
class FeedbackCleanupListener(private val plugin: KantanCommanderPlugin) : Listener {
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        plugin.feedback.forget(event.player)
    }
}
