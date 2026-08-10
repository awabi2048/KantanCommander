package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuSlotDistributionTest {
    @Test
    fun `command picker fills the seven-column body from the upper left`() {
        for (count in 1..21) {
            val slots = CommandPickerSlotDistribution.slots(count)

            assertEquals(count, slots.distinct().size)
            assertTrue(slots.all { it % 9 in 1..7 }, "count=$count slots=$slots")
            assertEquals((0 until count).map { 10 + (it / 7) * 9 + (it % 7) }, slots)
        }
    }

    @Test
    fun `choice menus center up to three choices and left-pack larger sets`() {
        assertEquals(listOf(22), ChoiceMenuSlotDistribution.slots(1))
        assertEquals(listOf(20, 24), ChoiceMenuSlotDistribution.slots(2))
        assertEquals(listOf(20, 22, 24), ChoiceMenuSlotDistribution.slots(3))
        assertEquals(listOf(19, 20, 21, 22), ChoiceMenuSlotDistribution.slots(4))
        assertEquals(listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30), ChoiceMenuSlotDistribution.slots(10))
    }

    @Test
    fun `small setting screens keep one-slot margins and left alignment`() {
        assertEquals(listOf(19), DistributedSettingSlots.slots(1))
        assertEquals(listOf(19, 20), DistributedSettingSlots.slots(2))
        assertEquals(listOf(19, 20, 21), DistributedSettingSlots.slots(3))
        assertEquals(listOf(19, 20, 21, 22), DistributedSettingSlots.slots(4))
    }
}
