package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GestureTargetChoiceLayoutTest {
    @Test
    fun `two choices merge the first and last two slots while preserving the span`() {
        val slots = GestureTargetChoiceLayoutPolicy.slots(2)

        assertEquals(2, slots.size)
        assertEquals(slots[0].width, slots[1].width, 1.0e-9)
        assertEquals(
            GestureTargetChoiceLayoutPolicy.SPAN_START_X,
            slots.first().centerX - slots.first().width / 2.0,
            1.0e-9,
        )
        assertEquals(
            GestureTargetChoiceLayoutPolicy.SPAN_END_X,
            slots.last().centerX + slots.last().width / 2.0,
            1.0e-9,
        )

        val centralGap = slots[1].centerX - slots[1].width / 2.0 -
            (slots[0].centerX + slots[0].width / 2.0)
        assertEquals(GestureTargetChoiceLayoutPolicy.GAP, centralGap, 1.0e-9)
    }

    @Test
    fun `layout rejects more choices than the two slot groups`() {
        assertThrows(IllegalArgumentException::class.java) {
            GestureTargetChoiceLayoutPolicy.slots(GestureTargetChoiceLayoutPolicy.CHOICE_COUNT + 1)
        }
    }
}
