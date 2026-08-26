package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action

enum class KantanItemAction {
    NONE,
    OPEN,
}

/**
 * 拡張コマンドブロックはバニラのブロック配置（BlockPlaceEvent）で設置するため、手に持った操作は一切処理しない。
 * コマンドディスクはカスタムアイテムのため、通常右クリック=編集を割り当てる。
 * 空の拡張コマンドブロックへの書き込みは、右クリック対象が配置物の場合にonInteract側で分岐する。
 * ディスクによる設置（Shift+右クリック）は仕様として廃止している。
 */
object KantanItemPolicy {
    fun itemAction(kind: KantanItemKind, action: Action, sneaking: Boolean): KantanItemAction = when {
        kind == KantanItemKind.DISK &&
            action in setOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK) &&
            !sneaking -> KantanItemAction.OPEN
        else -> KantanItemAction.NONE
    }
}