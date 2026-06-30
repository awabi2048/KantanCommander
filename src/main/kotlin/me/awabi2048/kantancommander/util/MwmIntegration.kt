package me.awabi2048.kantancommander.util

import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.Location
import org.bukkit.entity.Player

object MwmIntegration {

    fun isAvailable(): Boolean {
        return try {
            Class.forName("me.awabi2048.myworldmanager.api.MyWorldManagerApi")
            MyWorldManagerApi.getWorldRepository() != null
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    fun isWorldMember(player: Player, location: Location): Boolean? {
        if (!isAvailable()) return null
        val repo = MyWorldManagerApi.getWorldRepository() ?: return null
        val worldData = repo.findByWorldName(location.world.name) ?: return null
        return worldData.owner == player.uniqueId ||
            worldData.members.contains(player.uniqueId) ||
            worldData.moderators.contains(player.uniqueId)
    }
}
