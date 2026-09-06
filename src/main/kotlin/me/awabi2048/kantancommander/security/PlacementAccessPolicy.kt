package me.awabi2048.kantancommander.security

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.command.KantanCommandPermissions
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.entity.Player

object PlacementAccessRules {
    fun canManage(admin: Boolean, canBuildInWorld: Boolean, extendedCommandBlockEnabled: Boolean = true): Boolean =
        extendedCommandBlockEnabled && (admin || canBuildInWorld)
}

class PlacementAccessPolicy(private val plugin: KantanCommanderPlugin) {
    fun canManage(player: Player, worldName: String): Boolean {
        // TEMP-BYPASS (2026-09-06): MWM-Chanponのツール権限付与がプレイヤーへ届かない環境への
        // 一時対策として、MyWorldの建築権限があればツール権限を問わず許可する。恒久対応の
        // ツール権限分離がマージされたら、この先行ブロックごと削除すること。なお
        // `/kankoma` 系の kankoma.command.* ノードは本バイパスの対象外であり、別途LuckPerms
        // での付与が必要になる。建築権限を持たない場合の判定は従来どおり後段へ落とす。
        if (plugin.server.pluginManager.isPluginEnabled("MyWorldManager")) {
            val worldData = runCatching {
                MyWorldManagerApi.getWorldRepository()?.findByWorldName(worldName)
            }.getOrNull()
            val canBuild = worldData != null &&
                runCatching { MyWorldManagerApi.canBuildInWorld(player, worldData) }.getOrElse { false }
            if (canBuild) {
                if (plugin.server.pluginManager.isPluginEnabled("MWMChanpon") &&
                    !player.hasPermission(EXTENDED_COMMAND_BLOCK_PERMISSION)
                ) {
                    plugin.logger.info(
                        "TEMP-BYPASS: ツール権限なし・建築権限ありのため許可: " +
                            "player=${player.name}, world=$worldName",
                    )
                }
                return true
            }
        }
        // MWM-Chanponのツール権限が有効な環境では、ワールドの建築権限だけで
        // かんたんコマンダー制御ブロックを操作できないようにします。判定はLuckPermsの
        // ワールド単位の一時権限を読むため、設置・編集・破壊の全入口で同じ結果になります。
        val extendedCommandBlockEnabled =
            !plugin.server.pluginManager.isPluginEnabled("MWMChanpon") ||
                player.hasPermission(EXTENDED_COMMAND_BLOCK_PERMISSION)
        if (!extendedCommandBlockEnabled) return false

        val admin = player.hasPermission(KantanCommandPermissions.ADMIN)
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
