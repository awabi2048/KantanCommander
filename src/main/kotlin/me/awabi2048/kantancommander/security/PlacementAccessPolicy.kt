package me.awabi2048.kantancommander.security

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.entity.Player

object PlacementAccessRules {
    fun canManage(admin: Boolean, canBuildInWorld: Boolean, extendedCommandBlockEnabled: Boolean = true): Boolean =
        extendedCommandBlockEnabled && (admin || canBuildInWorld)
}

class PlacementAccessPolicy(private val plugin: KantanCommanderPlugin) {
    fun canManage(player: Player, worldName: String): Boolean {
        // MWM-Chanponのツール権限が有効な環境では、ワールドの建築権限だけで
        // 拡張コマンドブロックを操作できないようにします。判定はLuckPermsの
        // ワールド単位の一時権限を読むため、設置・編集・破壊の全入口で同じ結果になります。
        val extendedCommandBlockEnabled =
            !plugin.server.pluginManager.isPluginEnabled("MWMChanpon") ||
                player.hasPermission(EXTENDED_COMMAND_BLOCK_PERMISSION)
        if (!extendedCommandBlockEnabled) return false

        val admin = player.hasPermission("kankoma.admin")
        if (admin) {
            return PlacementAccessRules.canManage(
                admin = true,
                canBuildInWorld = false,
                extendedCommandBlockEnabled = true,
            )
        }

        if (!plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) return false
        val worldData = MyWorldManagerApi.getWorldRepository()?.findByWorldName(worldName) ?: return false
        return PlacementAccessRules.canManage(
            admin = false,
            canBuildInWorld = MyWorldManagerApi.canBuildInWorld(player, worldData),
            extendedCommandBlockEnabled = true,
        )
    }

    companion object {
        /** MWM-Chanponのcommandblockツールが付与する明示権限です。 */
        const val EXTENDED_COMMAND_BLOCK_PERMISSION = "kankoma.extended_command_block"
    }
}
