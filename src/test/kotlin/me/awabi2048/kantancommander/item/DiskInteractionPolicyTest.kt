package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiskInteractionPolicyTest {
    @Test
    fun `unset and written disks place only with shift right click on a block`() {
        listOf(DiskItemState.UNSET, DiskItemState.WRITTEN).forEach { state ->
            assertEquals(DiskItemAction.PLACE, DiskInteractionPolicy.itemAction(state, Action.RIGHT_CLICK_BLOCK, true))
            assertEquals(DiskItemAction.NONE, DiskInteractionPolicy.itemAction(state, Action.RIGHT_CLICK_AIR, true))
        }
        assertEquals(
            DiskItemAction.NONE,
            DiskInteractionPolicy.itemAction(DiskItemState.UNSET, Action.RIGHT_CLICK_BLOCK, false),
        )
    }

    @Test
    fun `written disk opens with right click and left click remains untouched`() {
        listOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK).forEach { action ->
            assertEquals(DiskItemAction.OPEN, DiskInteractionPolicy.itemAction(DiskItemState.WRITTEN, action, false))
        }
        listOf(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK).forEach { action ->
            assertEquals(DiskItemAction.NONE, DiskInteractionPolicy.itemAction(DiskItemState.WRITTEN, action, false))
        }
        assertEquals(
            DiskItemAction.NONE,
            DiskInteractionPolicy.itemAction(DiskItemState.UNSET, Action.RIGHT_CLICK_AIR, false),
        )
    }

    @Test
    fun `non disk item never enters disk placement`() {
        assertEquals(
            DiskItemAction.NONE,
            DiskInteractionPolicy.itemAction(DiskItemState.NOT_DISK, Action.RIGHT_CLICK_BLOCK, true),
        )
    }
}
