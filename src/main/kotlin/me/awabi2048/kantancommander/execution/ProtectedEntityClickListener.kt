package me.awabi2048.kantancommander.execution

import me.awabi2048.mwmchanpon.api.ProtectedEntityClickEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/** MWM-Chanpon が存在する場合のみ登録し、任意依存を維持します。 */
internal class ProtectedEntityClickListener(private val triggers: EventTriggerListener) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onClick(event: ProtectedEntityClickEvent) {
        triggers.fireEntityClick(event.player, event.target, event.hand)
    }
}
