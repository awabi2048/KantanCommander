package me.awabi2048.kantancommander.data

import me.awabi2048.myworldmanager.api.event.MwmWorldDeletedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class WorldVariableLifecycleListener(
    private val variables: WorldVariableStore,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldDeleted(event: MwmWorldDeletedEvent) {
        check(variables.deleteWorld(event.worldUuid)) {
            "MyWorld削除後のワールド内変数ファイルを削除できませんでした: ${event.worldUuid}"
        }
    }
}
