package me.awabi2048.kantancommander.item

import org.bukkit.event.block.Action

enum class DiskItemAction {
    NONE,
    OPEN,
    PLACE,
}

object DiskInteractionPolicy {
    fun itemAction(state: DiskItemState, action: Action, sneaking: Boolean): DiskItemAction = when {
        state != DiskItemState.NOT_DISK &&
            action == Action.RIGHT_CLICK_BLOCK &&
            sneaking -> DiskItemAction.PLACE
        state == DiskItemState.WRITTEN &&
            action in setOf(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK) &&
            !sneaking -> DiskItemAction.OPEN
        else -> DiskItemAction.NONE
    }
}
