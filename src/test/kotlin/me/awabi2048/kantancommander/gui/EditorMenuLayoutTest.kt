package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EditorMenuLayoutTest {
    @Test
    fun `structured target and destination values are shown in settings`() {
        val node = CommandType.TELEPORT.newNode().apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            destinationSpec = PositionSpec(PositionKind.MYWORLD_SPAWN)
        }
        val values = EditorMenuLayout.fields(CommandType.TELEPORT).associate { it.key to it.value(node) }

        assertEquals(TargetKind.NEAREST_PLAYER.name, values["target"])
        assertEquals(PositionKind.MYWORLD_SPAWN.name, values["destination"])
    }
}
