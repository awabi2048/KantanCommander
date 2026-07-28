package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class EditorMenuLayoutTest {
    @Test
    fun `options are centered in the third row`() {
        assertEquals(listOf(22), EditorMenuLayout.centeredSlots(1))
        assertEquals(listOf(21, 22, 23), EditorMenuLayout.centeredSlots(3))
        assertEquals(listOf(20, 21, 22, 23), EditorMenuLayout.centeredSlots(4))
        assertEquals((18..26).toList(), EditorMenuLayout.centeredSlots(9))
    }

    @Test
    fun `every configurable command has centered setting icons`() {
        CommandType.entries
            .filter { EditorMenuLayout.fields(it).isNotEmpty() }
            .forEach { type ->
                val slots = EditorMenuLayout.centeredSlots(EditorMenuLayout.fields(type).size)
                assertFalse(slots.isEmpty(), type.name)
                assertEquals(slots.sorted(), slots, type.name)
                assertEquals(slots.size, slots.distinct().size, type.name)
            }
    }

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
