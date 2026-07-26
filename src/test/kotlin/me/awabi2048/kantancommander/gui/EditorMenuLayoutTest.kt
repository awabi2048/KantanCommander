package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
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
    fun `every editable command has at most five setting icons`() {
        CommandType.entries
            .filterNot { it == CommandType.MERGE }
            .forEach { type ->
                val slots = EditorMenuLayout.centeredSlots(EditorMenuLayout.fields(type).size)
                assertFalse(slots.isEmpty(), type.name)
                assertEquals(slots.sorted(), slots, type.name)
                assertEquals(slots.size, slots.distinct().size, type.name)
            }
    }
}
