package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action

enum class KantanItemAction {
    NONE,
    OPEN,
}

/**
 * かんたんコマンダー制御ブロックはバニラのブロック配置（BlockPlaceEvent）で設置するため、手に持った操作は一切処理しない。
 * プログラムディスクは表示専用の成果物とし、単体右クリックから編集画面を開かない。
 * 空のかんたんコマンダー制御ブロックへの書き込みは、右クリック対象が配置物の場合にonInteract側で分岐する。
 * ディスクによる設置（Shift+右クリック）は仕様として廃止している。
 */
object KantanItemPolicy {
    fun itemAction(kind: KantanItemKind, action: Action, sneaking: Boolean): KantanItemAction = KantanItemAction.NONE
}
