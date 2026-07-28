package me.awabi2048.kantancommander.security

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.entity.Player

object PlacementAccessRules {
    fun canManage(admin: Boolean, canBuildInWorld: Boolean): Boolean =
        admin || canBuildInWorld
}

class PlacementAccessPolicy(private val plugin: KantanCommanderPlugin) {
    fun canManage(player: Player, worldName: String): Boolean {
        val admin = player.hasPermission("kankoma.admin")
        if (admin) return PlacementAccessRules.canManage(admin = true, canBuildInWorld = false)

        if (!plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) return false
        val worldData = MyWorldManagerApi.getWorldRepository()?.findByWorldName(worldName) ?: return false
        return PlacementAccessRules.canManage(
            admin = false,
            canBuildInWorld = MyWorldManagerApi.canBuildInWorld(player, worldData),
        )
    }
}
