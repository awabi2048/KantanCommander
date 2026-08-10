package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuSlotDistributionTest {
    @Test
    fun `command picker keeps one empty row above and below a fixed fourteen-slot area`() {
        assertEquals(54, CommandPickerLayoutPolicy.SIZE)
        assertEquals((19..25).toList() + (28..34).toList(), CommandPickerLayoutPolicy.itemSlots)
        assertEquals(listOf(49, 51), CommandPickerLayoutPolicy.categorySlots)
        assertEquals(45, CommandPickerLayoutPolicy.BACK_SLOT)
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
