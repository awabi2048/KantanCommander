package me.awabi2048.kantancommander.security

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.entity.Player
import java.util.UUID

object PlacementAccessRules {
    fun canManage(playerId: UUID, scriptOwner: UUID, worldOwner: UUID?, members: Set<UUID>, admin: Boolean): Boolean =
        admin || playerId == scriptOwner || (worldOwner != null && (playerId == worldOwner || playerId in members))
}

class PlacementAccessPolicy(private val plugin: KantanCommanderPlugin) {
    fun canManage(player: Player, worldName: String, scriptOwner: UUID): Boolean {
        val admin = player.hasPermission("kankoma.admin")
        if (admin) return true

        if (!plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) return false
        val worldData = MyWorldManagerApi.getWorldRepository()?.findByWorldName(worldName) ?: return false
        return MyWorldManagerApi.canBuildInWorld(player, worldData)
    }
}
