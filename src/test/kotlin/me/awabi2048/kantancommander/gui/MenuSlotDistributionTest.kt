package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuSlotDistributionTest {
    @Test
    fun `command picker keeps one empty row above and below a fixed fourteen-slot area`() {
        assertEquals(54, CommandPickerLayoutPolicy.SIZE)
        assertEquals((19..25).toList() + (28..34).toList(), CommandPickerLayoutPolicy.itemSlots)
        assertEquals(listOf(47, 49, 51), CommandPickerLayoutPolicy.categorySlots)
        assertEquals(45, CommandPickerLayoutPolicy.BACK_SLOT)
    }

    @Test
    fun `choice menus center up to three choices and left-pack larger sets`() {
        assertEquals(ChoiceMenuLayout(45, listOf(22), 36), ChoiceMenuLayoutPolicy.layout(1))
        assertEquals(ChoiceMenuLayout(45, listOf(20, 24), 36), ChoiceMenuLayoutPolicy.layout(2))
        assertEquals(ChoiceMenuLayout(45, listOf(20, 22, 24), 36), ChoiceMenuLayoutPolicy.layout(3))
        assertEquals(ChoiceMenuLayout(45, listOf(19, 20, 21, 22), 36), ChoiceMenuLayoutPolicy.layout(4))
        assertEquals(
            ChoiceMenuLayout(54, listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30), 45),
            ChoiceMenuLayoutPolicy.layout(10),
        )
    }

    @Test
    fun `small setting screens keep one-slot margins and left alignment`() {
        assertEquals(listOf(19), DistributedSettingSlots.slots(1))
        assertEquals(listOf(19, 20), DistributedSettingSlots.slots(2))
        assertEquals(listOf(19, 20, 21), DistributedSettingSlots.slots(3))
        assertEquals(listOf(19, 20, 21, 22), DistributedSettingSlots.slots(4))
    }
}
