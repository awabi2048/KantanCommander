package me.awabi2048.kantancommander.security

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.command.KantanCommandPermissions
import me.awabi2048.kantancommander.util.KcI18n
import me.awabi2048.myworldmanager.api.MyWorldManagerApi
import org.bukkit.GameMode
import org.bukkit.entity.Player

/**
 * 制御ブロックの設置・破壊・編集・メニュー開放に共通する許可判定の結果です。
 *
 * 拒否理由ごとに利用者への通知を分けるため、真偽値ではなく列挙で返します。
 * 各コマンド・ツール固有の権限分離は将来拡張とし、ここでは制御ブロック操作の
 * 共通フロー（admin → 世界内外 → ツール権限 → クリエイティブ）だけを扱います。
 */
enum class PlacementDecision {
    ALLOW,
    /** マイワールドの外では使用できません。 */
    OUTSIDE_MY_WORLD,
    /** このマイワールドで使用する権限がありません。 */
    NO_TOOL_PERMISSION,
    /** クリエイティブモード以外では使用できません。 */
    NOT_CREATIVE,
}

object PlacementAccessRules {
    fun decide(admin: Boolean, inMyWorld: Boolean, toolEnabled: Boolean, creative: Boolean): PlacementDecision = when {
        admin -> PlacementDecision.ALLOW
        !inMyWorld -> PlacementDecision.OUTSIDE_MY_WORLD
        !toolEnabled -> PlacementDecision.NO_TOOL_PERMISSION
        !creative -> PlacementDecision.NOT_CREATIVE
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
        if (player.gameMode != GameMode.CREATIVE) return PlacementDecision.NOT_CREATIVE
        return PlacementDecision.ALLOW
    }

    /**
     * 判定して拒否理由を赤字ActionBarで通知します。設置・破壊・編集・開放の
     * 全入口がこの1点を通ることで、理由文言とthrottleが分岐しません。
     */
    fun requireManage(player: Player, worldName: String): Boolean {
        val decision = decide(player, worldName)
        notifyDenied(player, decision)
        return decision == PlacementDecision.ALLOW
    }

    /** 拒否理由を赤字ActionBarで通知します。許可時は送信しません。 */
    fun notifyDenied(player: Player, decision: PlacementDecision) {
        val key = when (decision) {
            PlacementDecision.OUTSIDE_MY_WORLD ->
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACEMENT_OUTSIDE_MYWORLD
            PlacementDecision.NO_TOOL_PERMISSION ->
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACEMENT_NO_TOOL_PERMISSION
            PlacementDecision.NOT_CREATIVE ->
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_PLACEMENT_NOT_CREATIVE
            PlacementDecision.ALLOW -> return
        }
        plugin.feedback.sendActionBar(player, KcI18n.text(player, key))
    }

    companion object {
        /** MWM-Chanponのcommandblockツールが付与する明示権限です。 */
        const val EXTENDED_COMMAND_BLOCK_PERMISSION = "kankoma.extended_command_block"
    }
}
