package me.awabi2048.kantancommander.execution

import me.awabi2048.chanponutilities.api.TourEntityClickEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/** Chanpon-Utilities が存在する場合のみ登録し、任意依存を維持します。 */
internal class ProtectedEntityClickListener(private val triggers: EventTriggerListener) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onClick(event: TourEntityClickEvent) {
        triggers.fireEntityClick(event.player, event.target, event.hand)
    }
}
