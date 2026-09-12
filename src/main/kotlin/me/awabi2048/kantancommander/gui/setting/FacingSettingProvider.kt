package me.awabi2048.kantancommander.gui.setting

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.gui.CommandSettingContext
import me.awabi2048.kantancommander.gui.CommandSettingsModel
import me.awabi2048.kantancommander.gui.GestureSettingTreeNode
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.entity.Player

/**
 * 向き設定の汎用プロバイダです。
 *
 * 全向き設定で同一の5項目を返し、コマンド固有の出し分けを持ちません：
 * - 上段：特定の座標を向く／角度を指定する／特定のエンティティの位置を向く（注視）
 * - 下段：特定のエンティティと同じ向きを向く（複写、単一体のみ）／一時変数を参照（LOCATIONのみ）
 *
 * 注視・複写の子は汎用単一実体参照へ帰着し、対象設定と同じ詳細条件へ進めます。
 */
object FacingSettingProvider {
    /**
     * 汎用向き5項目を返します。役割による出し分けは行いません。
     */
    fun choices(
        node: CommandNode,
        context: CommandSettingContext,
        player: Player,
        text: (LocalizationKey<String>) -> String = { KcI18n.text(player, it) },
    ): List<GestureSettingTreeNode> {
        val current = CommandSettingsModel.facingSpec(node, context.role)?.kind
        // 上段3＋下段2の順序を固定し、画面側で段分けできるようID順序を保証します。
        return listOf(
            GestureSettingTreeNode(
                id = "facing:${FacingKind.COORDINATES.name}",
                label = text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES),
                selected = current == FacingKind.COORDINATES,
            ),
            GestureSettingTreeNode(
                id = "facing:${FacingKind.ROTATION.name}",
                label = text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMERIC),
                selected = current == FacingKind.ROTATION,
            ),
            GestureSettingTreeNode(
                id = "facing:${FacingKind.TARGET.name}",
                label = text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET),
                selected = current == FacingKind.TARGET,
                children = ReferenceSettingProviders.singleEntityCategories(node, context, player, text = text),
            ),
            GestureSettingTreeNode(
                id = "facing:${FacingKind.COPY_ORIENTATION.name}",
                label = text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COPY_ORIENTATION),
                selected = current == FacingKind.COPY_ORIENTATION,
                children = ReferenceSettingProviders.singleEntityCategories(node, context, player, text = text),
            ),
            GestureSettingTreeNode(
                id = "facing:${FacingKind.TEMPORARY.name}",
                label = text(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE),
                selected = current == FacingKind.TEMPORARY,
            ),
        )
    }
}
