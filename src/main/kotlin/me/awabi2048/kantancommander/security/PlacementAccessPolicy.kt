package me.awabi2048.kantancommander.security

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.command.KantanCommandPermissions
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.entity.Player

/**
 * 制御ブロックの設置・破壊・編集・メニュー開放に共通する許可判定の結果です。
 *
 * 拒否理由ごとに利用者への通知を分けるため、真偽値ではなく列挙で返します。
 * 各コマンド・ツール固有の権限分離は将来拡張とし、ここでは制御ブロック操作の
 * 共通フロー（admin → 世界内外 → ツール権限）だけを扱います。
 */
enum class PlacementDecision {
    ALLOW,
    /** マイワールドの外では使用できません。 */
    OUTSIDE_MY_WORLD,
    /** このマイワールドで使用する権限がありません。 */
    NO_TOOL_PERMISSION,
}

object PlacementAccessRules {
    fun decide(admin: Boolean, inMyWorld: Boolean, toolEnabled: Boolean): PlacementDecision = when {
        admin -> PlacementDecision.ALLOW
        !inMyWorld -> PlacementDecision.OUTSIDE_MY_WORLD
        !toolEnabled -> PlacementDecision.NO_TOOL_PERMISSION
        else -> PlacementDecision.ALLOW
    }
}

class PlacementAccessPolicy(private val plugin: KantanCommanderPlugin) {
    fun decide(player: Player, worldName: String): PlacementDecision {
        // kankoma.adminは全ての制限をバイパスします。
        if (player.hasPermission(KantanCommandPermissions.ADMIN)) {
            return PlacementDecision.ALLOW
        }
        // 世界台帳に存在しない場所はマイワールドの外として扱います。
        // 取得失敗時も安全側（外扱い）へ倒します。
        val inMyWorld = plugin.server.pluginManager.isPluginEnabled("MyWorldManager") &&
            runCatching {
                MyWorldManagerApi.getWorldRepository()?.findByWorldName(worldName) != null
            }.getOrDefault(false)
        if (!inMyWorld) return PlacementDecision.OUTSIDE_MY_WORLD
        // MWM-Chanponが「かんたんコマンダー」ツールを有効化した滞在者へは、
        // ワールド滞在に追従する一時権限が付与されます。Chanpon無効環境では検査を素通しします。
        val toolEnabled = !plugin.server.pluginManager.isPluginEnabled("MWMChanpon") ||
            player.hasPermission(EXTENDED_COMMAND_BLOCK_PERMISSION)
        if (!toolEnabled) return PlacementDecision.NO_TOOL_PERMISSION
        return PlacementDecision.ALLOW
    }

    /** 段階移行中の橋渡しです。呼出側がdecideへ移行したら削除します。 */
    fun canManage(player: Player, worldName: String): Boolean =
        decide(player, worldName) == PlacementDecision.ALLOW

    companion object {
        /** MWM-Chanponのcommandblockツールが付与する明示権限です。 */
        const val EXTENDED_COMMAND_BLOCK_PERMISSION = "kankoma.extended_command_block"
    }
}
