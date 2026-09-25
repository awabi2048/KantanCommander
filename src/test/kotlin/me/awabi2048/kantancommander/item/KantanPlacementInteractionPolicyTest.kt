package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.gui.EditorGuiMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanPlacementInteractionPolicyTest {
    @Test
    fun `sneaking always passes through to vanilla use and placement`() {
        // バニラの対話ブロックに則り、スニーク中は手持ちの種別・表示方式を問わず
        // 使用バイパス（ブロック設置）へ素通しし、GUIは開きません。
        KantanItemKind.entries.forEach { itemKind ->
            assertEquals(
                KantanPlacementInteraction.VANILLA_PLACE,
                KantanPlacementInteractionPolicy.resolve(itemKind, sneaking = true, editorGuiMode = EditorGuiMode.GESTURE),
            )
            assertEquals(
                KantanPlacementInteraction.VANILLA_PLACE,
                KantanPlacementInteractionPolicy.resolve(itemKind, sneaking = true, editorGuiMode = EditorGuiMode.INVENTORY),
            )
        }
    }

    @Test
    fun `non-sneaking interactions preserve placement and existing editor routes`() {
        // 非スニークの右クリックは編集入口であり、制御ブロック所持中も配置ではなく
        // 環境に対応したエディターを開きます。
        assertEquals(
            KantanPlacementInteraction.FOLLOWING_GESTURE,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.BLOCK, sneaking = false, editorGuiMode = EditorGuiMode.GESTURE),
        )
        assertEquals(
            KantanPlacementInteraction.INVENTORY_EDITOR,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.BLOCK, sneaking = false, editorGuiMode = EditorGuiMode.INVENTORY),
        )
        assertEquals(
            KantanPlacementInteraction.WRITE_CONFIRM,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.DISK, sneaking = false, editorGuiMode = EditorGuiMode.GESTURE),
        )
        assertEquals(
            KantanPlacementInteraction.FOLLOWING_GESTURE,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.NONE, sneaking = false, editorGuiMode = EditorGuiMode.GESTURE),
        )
        assertEquals(
            KantanPlacementInteraction.INVENTORY_EDITOR,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.NONE, sneaking = false, editorGuiMode = EditorGuiMode.INVENTORY),
        )
        assertEquals(
            KantanPlacementInteraction.FOLLOWING_GESTURE,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.WAND, sneaking = false, editorGuiMode = EditorGuiMode.GESTURE),
        )
        assertEquals(
            KantanPlacementInteraction.INVENTORY_EDITOR,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.WAND, sneaking = false, editorGuiMode = EditorGuiMode.INVENTORY),
        )
    }
}
