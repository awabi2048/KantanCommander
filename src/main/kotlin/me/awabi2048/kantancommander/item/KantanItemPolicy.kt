package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action

enum class KantanItemAction {
    NONE,
    OPEN,
    PLACE,
}

/**
 * 拡張コマンドブロックはバニラのブロック配置（BlockPlaceEvent）で設置するため、手に持った操作は一切処理しない。
 * コマンドディスクはカスタムアイテムのため、通常右クリック=編集、Shift+右クリック=設置を割り当てる。
 */
object KantanItemPolicy {
    fun itemAction(kind: KantanItemKind, action: Action, sneaking: Boolean): KantanItemAction = when {
        kind == KantanItemKind.DISK &&
            action == Action.RIGHT_CLICK_BLOCK &&
            sneaking -> KantanItemAction.PLACE
        kind == KantanItemKind.DISK &&
            action in setOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK) &&
            !sneaking -> KantanItemAction.OPEN
        else -> KantanItemAction.NONE
    }
}