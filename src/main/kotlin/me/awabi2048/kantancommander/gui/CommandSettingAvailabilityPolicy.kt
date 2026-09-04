package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.PositionKind

/**
 * 構造化設定の選択可否を、表示側と入力側で共有するポリシーです。
 *
 * setblockの配置位置だけは、制御ブロックを上書きできないという実行時制約が
 * あるため、選択肢を表示したまま無効化します。無効化条件をGUIごとに複製せず、
 * インベントリGUIとジェスチャーGUIの表示・クリック受付へ同じ判定を渡します。
 */
internal object CommandSettingAvailabilityPolicy {
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
