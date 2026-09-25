package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.gui.EditorGuiMode

/**
 * 既存のかんたんコマンダー制御ブロックを右クリックした後の入口を決めます。
 *
 * バニラの対話ブロックに則り、スニーク中の右クリックは使用をバイパスして
 * 手持ちのブロック配置へ素通しします。非スニークの右クリックは編集入口であり、
 * かんたんコマンダー制御ブロック所持中も配置ではなくエディターを開きます。
 * Gesture GUIは追従を初期値とし、固定化は画面上のクリップ操作へ集約します。
 * イベント処理や権限判定から表示方式の選択を分離し、スニーク時の動作が別の
 * 手持ちアイテムや固定設定に左右されないことをテスト可能にします。
 */
internal enum class KantanPlacementInteraction {
    VANILLA_PLACE,
    FOLLOWING_GESTURE,
    WRITE_CONFIRM,
    INVENTORY_EDITOR,
}

internal object KantanPlacementInteractionPolicy {
    fun resolve(
        itemKind: KantanItemKind,
        sneaking: Boolean,
        editorGuiMode: EditorGuiMode,
    ): KantanPlacementInteraction {
        // スニーク中は手持ちの種別を問わずバニラの使用・配置へ素通しします。
        if (sneaking) {
            return KantanPlacementInteraction.VANILLA_PLACE
        }
        if (itemKind == KantanItemKind.DISK) return KantanPlacementInteraction.WRITE_CONFIRM
        return if (editorGuiMode == EditorGuiMode.GESTURE) {
            // Gesture GUIの起動経路は常に追従です。ワールド固定は、ユーザーが
            // 画面上のクリップボタンを押した現在poseへ明示的に委譲します。
            KantanPlacementInteraction.FOLLOWING_GESTURE
        } else {
            KantanPlacementInteraction.INVENTORY_EDITOR
        }
    }
}
