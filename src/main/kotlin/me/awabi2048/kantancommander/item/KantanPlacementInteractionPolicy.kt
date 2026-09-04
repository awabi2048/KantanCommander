package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.gui.EditorGuiMode

/**
 * 既存のかんたんコマンダー制御ブロックを右クリックした後の入口を決めます。
 *
 * 通常時に制御ブロックアイテムを持っている場合はバニラの配置を優先しますが、
 * スニークは操作プレイヤーの環境に応じたエディターを開く操作として扱います。
 * イベント処理や権限判定から表示方式の選択を分離し、スニーク時の動作が別の
 * 手持ちアイテムや固定設定に左右されないことをテスト可能にします。
 */
internal enum class KantanPlacementInteraction {
    VANILLA_PLACE,
    FOLLOWING_GESTURE,
    FIXED_GESTURE,
    WRITE_CONFIRM,
    INVENTORY_EDITOR,
}

internal object KantanPlacementInteractionPolicy {
    fun resolve(
        itemKind: KantanItemKind,
        sneaking: Boolean,
        editorGuiMode: EditorGuiMode,
    ): KantanPlacementInteraction {
        if (!sneaking && itemKind == KantanItemKind.BLOCK) {
            return KantanPlacementInteraction.VANILLA_PLACE
        }
        if (sneaking) {
            return if (editorGuiMode == EditorGuiMode.GESTURE) {
                KantanPlacementInteraction.FOLLOWING_GESTURE
            } else {
                KantanPlacementInteraction.INVENTORY_EDITOR
            }
        }
        if (itemKind == KantanItemKind.DISK) return KantanPlacementInteraction.WRITE_CONFIRM
        return if (editorGuiMode == EditorGuiMode.GESTURE) {
            KantanPlacementInteraction.FIXED_GESTURE
        } else {
            KantanPlacementInteraction.INVENTORY_EDITOR
        }
    }
}
