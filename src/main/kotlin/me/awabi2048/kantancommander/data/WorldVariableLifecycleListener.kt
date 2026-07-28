package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.myworldmanager.api.event.MwmWorldDeletedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class WorldVariableLifecycleListener(
    private val plugin: KantanCommanderPlugin,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldDeleted(event: MwmWorldDeletedEvent) {
        plugin.placements.removeWorld(event.worldName).forEach { placement ->
            plugin.scripts.delete(placement.scriptId)
        }
        if (!plugin.variables.deleteWorld(event.worldUuid)) {
            plugin.logger.severe(
                "[KantanCommander] MyWorld削除後のワールド内変数ファイルを削除できませんでした: ${event.worldUuid}"
            )
        }
    }
}
