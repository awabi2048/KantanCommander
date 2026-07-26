package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action

enum class DiskItemAction {
    NONE,
    PLACE,
}

object DiskInteractionPolicy {
    fun itemAction(state: DiskItemState, action: Action, sneaking: Boolean): DiskItemAction =
        if (
            state != DiskItemState.NOT_DISK &&
            action == Action.RIGHT_CLICK_BLOCK &&
            sneaking
        ) {
            DiskItemAction.PLACE
        } else {
            DiskItemAction.NONE
        }
}
