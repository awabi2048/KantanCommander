package me.awabi2048.kantancommander.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanPlacementInteractionPolicyTest {
    @Test
    fun `sneaking always opens the player-following gesture editor`() {
        KantanItemKind.entries.forEach { itemKind ->
            assertEquals(
                KantanPlacementInteraction.FOLLOWING_GESTURE,
                KantanPlacementInteractionPolicy.resolve(itemKind, sneaking = true, useGestureEditor = false),
            )
            assertEquals(
                KantanPlacementInteraction.FOLLOWING_GESTURE,
                KantanPlacementInteractionPolicy.resolve(itemKind, sneaking = true, useGestureEditor = true),
            )
        }
    }

    @Test
    fun `non-sneaking interactions preserve placement and existing editor routes`() {
        assertEquals(
            KantanPlacementInteraction.VANILLA_PLACE,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.BLOCK, sneaking = false, useGestureEditor = true),
        )
        assertEquals(
            KantanPlacementInteraction.WRITE_CONFIRM,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.DISK, sneaking = false, useGestureEditor = true),
        )
        assertEquals(
            KantanPlacementInteraction.FOLLOWING_GESTURE,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.NONE, sneaking = false, useGestureEditor = true),
        )
        assertEquals(
            KantanPlacementInteraction.INVENTORY_EDITOR,
            KantanPlacementInteractionPolicy.resolve(KantanItemKind.NONE, sneaking = false, useGestureEditor = false),
        )
    }
}
