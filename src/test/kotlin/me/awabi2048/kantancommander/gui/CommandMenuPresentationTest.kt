package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandMenuPresentationTest {
    @Test
    fun `every command type has an explicit category`() {
        assertEquals(CommandType.entries.toSet(), CommandType.entries.groupBy(CommandPresentationPolicy::category).values.flatten().toSet())
        assertTrue(CommandType.TELEPORT.let(CommandPresentationPolicy::category) == CommandCategory.PROCESS)
        assertTrue(CommandType.CONDITION.let(CommandPresentationPolicy::category) == CommandCategory.CONTROL)
    }

    @Test
    fun `context footer excludes context and control-only nodes`() {
        assertTrue(CommandPresentationPolicy.supportsContextOverride(CommandType.GIVE_ITEM))
        assertTrue(CommandPresentationPolicy.supportsContextOverride(CommandType.CONDITION))
        assertFalse(CommandPresentationPolicy.supportsContextOverride(CommandType.CONTEXT))
        assertFalse(CommandPresentationPolicy.supportsContextOverride(CommandType.FOR_START))
    }

    @Test
    fun `variable and for settings use semantic two-dimensional layouts`() {
        assertEquals(
            listOf(11, 13, 15, 20, 24),
            CommandSettingsSlotPolicy.slots(CommandType.VARIABLE, listOf("scope", "name", "type", "operation", "value")),
        )
        assertEquals(
            listOf(11, 13, 15, 20, 22, 24, 31),
            CommandSettingsSlotPolicy.slots(
                CommandType.FOR_START,
                listOf("startSource", "endSource", "stepSource", "startValue", "endValue", "stepValue", "inclusiveEnd"),
            ),
        )
    }
}
