package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.gui.EditorGuiMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanPlacementInteractionPolicyTest {
    @Test
    fun `sneaking follows the selected editor GUI mode`() {
        KantanItemKind.entries.forEach { itemKind ->
            assertEquals(
                KantanPlacementInteraction.FOLLOWING_GESTURE,
                KantanPlacementInteractionPolicy.resolve(itemKind, sneaking = true, editorGuiMode = EditorGuiMode.GESTURE),
            )
            assertEquals(
                KantanPlacementInteraction.INVENTORY_EDITOR,
                KantanPlacementInteractionPolicy.resolve(itemKind, sneaking = true, editorGuiMode = EditorGuiMode.INVENTORY),
            )
        }
    }

    @Test
    fun `non-sneaking interactions preserve placement and existing editor routes`() {
        assertEquals(
            KantanPlacementInteraction.VANILLA_PLACE,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.BLOCK, sneaking = false, editorGuiMode = EditorGuiMode.GESTURE),
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
    }
}
