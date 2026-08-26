package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanItemPolicyTest {
    @Test
    fun `block item never handles hand interactions and relies on vanilla placement`() {
        listOf(Action.RIGHT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR, Action.LEFT_CLICK_BLOCK, Action.LEFT_CLICK_AIR)
            .forEach { action ->
                assertEquals(
                    KantanItemAction.NONE,
                    KantanItemPolicy.itemAction(KantanItemKind.BLOCK, action, sneaking = false),
                )
                assertEquals(
                    KantanItemAction.NONE,
                    KantanItemPolicy.itemAction(KantanItemKind.BLOCK, action, sneaking = true),
                )
            }
    }

    @Test
    fun `disk opens with right click and places only with shift right click on a block`() {
        listOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK).forEach { action ->
            assertEquals(
                KantanItemAction.OPEN,
                KantanItemPolicy.itemAction(KantanItemKind.DISK, action, sneaking = false),
            )
        }
        assertEquals(
            KantanItemAction.PLACE,
            KantanItemPolicy.itemAction(KantanItemKind.DISK, Action.RIGHT_CLICK_BLOCK, sneaking = true),
        )
        listOf(Action.RIGHT_CLICK_AIR, Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK).forEach { action ->
            assertEquals(
                KantanItemAction.NONE,
                KantanItemPolicy.itemAction(KantanItemKind.DISK, action, sneaking = true),
            )
        }
    }

    @Test
    fun `unrelated item never enters kantan interaction`() {
        listOf(Action.RIGHT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR).forEach { action ->
            assertEquals(
                KantanItemAction.NONE,
                KantanItemPolicy.itemAction(KantanItemKind.NONE, action, sneaking = true),
            )
            assertEquals(
                KantanItemAction.NONE,
                KantanItemPolicy.itemAction(KantanItemKind.NONE, action, sneaking = false),
            )
        }
    }
}