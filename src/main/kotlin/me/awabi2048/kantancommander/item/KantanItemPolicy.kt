package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action

enum class KantanItemAction {
    NONE,
    OPEN,
}

/**
 * 手に持った単体操作だけでは何も処理しません。
 * かんたんコマンダー制御ブロックの設置はバニラのブロック配置（BlockPlaceEvent）で行い、
 * 配置物への右クリックはonInteract側でスニーク有無により振り分けます。
 * スニーク中はバニラの使用・配置へ素通しし、非スニークは編集入口としてGUIを開きます。
 * プログラムディスクは表示専用の成果物とし、単体右クリックから編集画面を開きません。
 * ディスク自体の設置は仕様として扱いません。
 */
object KantanItemPolicy {
    fun itemAction(kind: KantanItemKind, action: Action, sneaking: Boolean): KantanItemAction = KantanItemAction.NONE
}
