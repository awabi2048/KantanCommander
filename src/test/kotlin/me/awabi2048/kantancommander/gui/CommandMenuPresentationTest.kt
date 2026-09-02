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
        assertTrue(CommandType.TELEPORT.let(CommandPresentationPolicy::category) == CommandCategory.EXECUTION)
        assertTrue(CommandType.CONDITION.let(CommandPresentationPolicy::category) == CommandCategory.CONTROL)
        assertTrue(CommandType.DISK_CALL.let(CommandPresentationPolicy::category) == CommandCategory.CONTROL)
    }

    @Test
    fun `nested open branch picker retains normal commands and optional merge`() {
        val executionTypes = CommandPickerTypePolicy.types(
            category = CommandCategory.EXECUTION,
            mergeAvailable = true,
            insideForBody = false,
        )
        val controlTypes = CommandPickerTypePolicy.types(
            category = CommandCategory.CONTROL,
            mergeAvailable = true,
            insideForBody = false,
        )

        assertTrue(CommandType.TELEPORT in executionTypes)
        assertTrue(CommandType.CONDITION in controlTypes)
        assertTrue(CommandType.MERGE in controlTypes)
    }

    @Test
    fun `context footer excludes context and control-only nodes`() {
        assertTrue(CommandPresentationPolicy.supportsContextOverride(CommandType.GIVE_ITEM))
        assertTrue(CommandPresentationPolicy.supportsContextOverride(CommandType.CONDITION))
        assertFalse(CommandPresentationPolicy.supportsContextOverride(CommandType.VARIABLE))
        assertFalse(CommandPresentationPolicy.supportsContextOverride(CommandType.CONTEXT))
        assertFalse(CommandPresentationPolicy.supportsContextOverride(CommandType.FOR_START))
        CommandType.entries.forEach { type ->
            assertFalse(EditorMenuLayout.fields(type).any { it.key == "context" || it.key == "contextSource" })
        }
    }

    @Test
    fun `every editable field declares semantic lore metadata`() {
        CommandType.entries.flatMap(EditorMenuLayout::fields).forEach { field ->
            assertTrue(field.descriptionKey.id.startsWith("kantan_commander_clean.gui.field_description."), field.key)
            assertTrue(field.actionKey.id.startsWith("kantan_commander_clean.gui.field_action."), field.key)
        }
    }

    @Test
    fun `variable and for settings use semantic two-dimensional layouts`() {
        assertEquals(
            listOf(19, 20, 21, 28, 29),
            CommandSettingsSlotPolicy.slots(CommandType.VARIABLE, listOf("operation", "name", "type", "changeMode", "value")),
        )
        assertEquals(54, CommandSettingsSlotPolicy.size(CommandType.VARIABLE))
        assertEquals(45, CommandSettingsSlotPolicy.backSlot(CommandType.VARIABLE))
        assertEquals(54, CommandSettingsSlotPolicy.size(CommandType.APPLY_EFFECT, 5))
        assertEquals(45, CommandSettingsSlotPolicy.backSlot(CommandType.APPLY_EFFECT, 5))
        assertEquals(
            listOf(20),
            CommandSettingsSlotPolicy.slots(
                CommandType.FOR_START,
                listOf("count"),
            ),
        )
    }
}
