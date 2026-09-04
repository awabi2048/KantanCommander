package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.PositionKind
import org.bukkit.Material

/**
 * 構造化設定の選択可否を、表示側と入力側で共有するポリシーです。
 *
 * setblockの配置位置だけは、制御ブロックを上書きできないという実行時制約が
 * あるため、選択肢を表示したまま無効化します。無効化条件をGUIごとに複製せず、
 * インベントリGUIとジェスチャーGUIの表示・クリック受付へ同じ判定を渡します。
 */
internal object CommandSettingAvailabilityPolicy {
    /** 制御ブロック位置を選べない理由として、無効項目のホバーへ表示します。 */
    const val CONTROL_BLOCK_POSITION_DISABLED_HOVER = "制御ブロックのある位置は操作できませんん"

    fun isPositionChoiceEnabled(
        node: CommandNode,
        role: CommandSettingRole?,
        kind: PositionKind,
    ): Boolean =
        kind != PositionKind.DISK ||
            node.type != CommandType.BLOCK_OPERATION ||
            role != CommandSettingRole.BLOCK_POSITION ||
            BlockOperationMode.from(node.string("operation", BlockOperationMode.SETBLOCK.value)) != BlockOperationMode.SETBLOCK
}

/** メインハンドのブロック設定値を、空手を含む同じ変換規則で保存します。 */
internal object HeldBlockSettingPolicy {
    /** 空手は明示的なminecraft:air、非ブロックアイテムは未選択として返します。 */
    fun materialId(material: Material): String? {
        // 空のメインハンドは必ずMaterial.AIRです。isAir()はサーバーのレジストリへ
        // 依存するため、単純な空手判定では使わず、AIRを先に確定します。
        if (material == Material.AIR) return material.key.toString()
        return materialId(material.key.toString(), isAir = false, isBlock = material.isBlock)
    }

    /** プラットフォーム判定と保存値変換を分離し、空手を含む保存契約を検証可能にします。 */
    internal fun materialId(materialKey: String, isAir: Boolean, isBlock: Boolean): String? =
        materialKey.takeIf { isAir || isBlock }
}
