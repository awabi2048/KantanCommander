package me.awabi2048.kantancommander.gui.setting

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.gui.CommandSettingAvailabilityPolicy
import me.awabi2048.kantancommander.gui.CommandSettingContext
import me.awabi2048.kantancommander.gui.CommandSettingRole
import me.awabi2048.kantancommander.gui.CommandSettingsModel
import me.awabi2048.kantancommander.gui.GestureSettingTreeNode
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.entity.Player

/**
 * 位置設定の汎用プロバイダです。
 *
 * 移動先の「エンティティ位置を参照」は独立種別として扱わず、対象設定への委譲として
 * 表現します。表示・保存の分岐をコマンド固有に持たず、対象と同じ詳細条件へ進めます。
 */
object PositionSettingProvider {
    /**
     * 汎用位置候補を返します。移動先のみ座標／実体参照／一時変数の3択です。
     *
     * 実体参照の子は汎用対象分類（単一・複数を含む対象設定と同一規則）とし、
     * 二段階目の詳細画面へそのまま進めます。一時変数は通常の対象設定経路に残しません。
     */
    fun choices(
        node: CommandNode,
        context: CommandSettingContext,
        player: Player,
        text: (LocalizationKey<String>) -> String = { KcI18n.text(player, it) },
    ): List<GestureSettingTreeNode> {
        val destination = context.role == CommandSettingRole.DESTINATION
        val current = CommandSettingsModel.positionKind(node, context.role)
        val kinds = if (destination) {
            listOf(
                PositionKind.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES_SET,
                PositionKind.TARGET to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_OTHER_ENTITY,
                PositionKind.TEMPORARY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE,
            )
        } else {
            listOf(
                PositionKind.DISK to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION,
                PositionKind.MYWORLD_SPAWN to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN,
                PositionKind.COORDINATES to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES,
                PositionKind.TEMPORARY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE,
            )
        }
        return kinds.map { (kind, label) ->
            val selected = current == kind
            GestureSettingTreeNode(
                id = "position:${kind.name}",
                label = text(label),
                selected = selected,
                enabled = CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(node, context.role, kind),
                disabledHoverText = if (kind == PositionKind.DISK) {
                    CommandSettingAvailabilityPolicy.CONTROL_BLOCK_POSITION_DISABLED_HOVER
                } else null,
                children = if (kind == PositionKind.TARGET && destination) {
                    // 移動先実体参照は対象設定へ帰着します。一時変数は対象設定側に残します。
                    ReferenceSettingProviders.targetCategories(node, context, player, includeTemporary = false, text = text)
                } else emptyList(),
            )
        }
    }
}
