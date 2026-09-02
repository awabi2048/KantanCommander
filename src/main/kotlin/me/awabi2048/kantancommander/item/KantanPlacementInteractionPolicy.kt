package me.awabi2048.kantancommander.item

/**
 * 既存のかんたんコマンダー制御ブロックを右クリックした後の入口を決めます。
 *
 * 通常時に制御ブロックアイテムを持っている場合はバニラの配置を優先しますが、
 * スニークは明示的な「追従GestureGUIを開く」操作として扱います。イベント処理や
 * 権限判定から表示モードの選択を分離し、スニーク時の動作が別の手持ちアイテムや
 * use-gesture-editor設定に左右されないことをテスト可能にします。
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
        useGestureEditor: Boolean,
    ): KantanPlacementInteraction {
        if (!sneaking && itemKind == KantanItemKind.BLOCK) {
            return KantanPlacementInteraction.VANILLA_PLACE
        }
        if (sneaking) return KantanPlacementInteraction.FOLLOWING_GESTURE
        if (itemKind == KantanItemKind.DISK) return KantanPlacementInteraction.WRITE_CONFIRM
        return if (useGestureEditor) {
            KantanPlacementInteraction.FIXED_GESTURE
        } else {
            KantanPlacementInteraction.INVENTORY_EDITOR
        }
    }
}
