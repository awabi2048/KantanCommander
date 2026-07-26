package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiskInteractionPolicyTest {
    @Test
    fun `unset and written disks place only with shift right click on a block`() {
        listOf(DiskItemState.UNSET, DiskItemState.WRITTEN).forEach { state ->
            assertEquals(DiskItemAction.PLACE, DiskInteractionPolicy.itemAction(state, Action.RIGHT_CLICK_BLOCK, true))
            assertEquals(DiskItemAction.NONE, DiskInteractionPolicy.itemAction(state, Action.RIGHT_CLICK_BLOCK, false))
            assertEquals(DiskItemAction.NONE, DiskInteractionPolicy.itemAction(state, Action.RIGHT_CLICK_AIR, true))
        }
    }

    @Test
    fun `non disk item never enters disk placement`() {
        assertEquals(
            DiskItemAction.NONE,
            DiskInteractionPolicy.itemAction(DiskItemState.NOT_DISK, Action.RIGHT_CLICK_BLOCK, true),
        )
    }
}
