package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuSlotDistributionTest {
    @Test
    fun `command picker balances every supported item count`() {
        for (count in 1..21) {
            val slots = CommandPickerSlotDistribution.slots(count)
            val rowSizes = slots.groupingBy { it / 9 }.eachCount().values

            assertEquals(count, slots.distinct().size)
            assertTrue(rowSizes.max() - rowSizes.min() <= 1, "count=$count rows=$rowSizes")
            assertTrue(slots.all { it in 9..35 }, "count=$count slots=$slots")
        }
    }

    @Test
    fun `future command counts use the approved balanced rows`() {
        assertEquals(listOf(11, 12, 13, 14, 15, 20, 21, 22, 23, 24), CommandPickerSlotDistribution.slots(10))
        assertEquals(listOf(10, 11, 12, 13, 14, 15, 20, 21, 22, 23, 24), CommandPickerSlotDistribution.slots(11))
        assertEquals(listOf(10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24), CommandPickerSlotDistribution.slots(12))
        assertEquals(listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24), CommandPickerSlotDistribution.slots(13))
    }

    @Test
    fun `small setting screens keep explicit equal spacing`() {
        assertEquals(listOf(22), DistributedSettingSlots.slots(1))
        assertEquals(listOf(20, 24), DistributedSettingSlots.slots(2))
        assertEquals(listOf(19, 22, 25), DistributedSettingSlots.slots(3))
        assertEquals(listOf(19, 21, 23, 25), DistributedSettingSlots.slots(4))
    }
}
