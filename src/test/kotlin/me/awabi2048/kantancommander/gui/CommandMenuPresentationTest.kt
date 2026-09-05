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
        // Particle追加後は実行系が2ページになります。候補一覧全体ではなく、
        // ページャーが返すページ数で収まることを検証します。
        assertEquals(2, GestureCommandPickerLayoutPolicy.pageCount(executionTypes.size))
        assertTrue(controlTypes.size <= GestureCommandPickerLayoutPolicy.PAGE_SIZE)
    }

    @Test
    fun `gesture command picker fits ten candidates on one two-column page`() {
        assertEquals(10, GestureCommandPickerLayoutPolicy.PAGE_SIZE)
        assertEquals(1, GestureCommandPickerLayoutPolicy.pageCount(10))
        assertEquals(2, GestureCommandPickerLayoutPolicy.pageCount(11))
        assertEquals(5, GestureCommandPickerLayoutPolicy.rowCount(10))
        assertEquals(-0.10, GestureCommandPickerLayoutPolicy.columnX(0), 1.0e-9)
        assertEquals(0.67, GestureCommandPickerLayoutPolicy.columnX(1), 1.0e-9)
        assertEquals(0.20, GestureCommandPickerLayoutPolicy.rowY(0), 1.0e-9)
        assertEquals(-0.24, GestureCommandPickerLayoutPolicy.rowY(4), 1.0e-9)
        assertEquals(0.66, GestureCommandPickerLayoutPolicy.CARD_WIDTH, 1.0e-9)
        assertEquals(0.10, GestureCommandPickerLayoutPolicy.CARD_HEIGHT, 1.0e-9)
    }

    @Test
    fun `command picker and settings contain no explicit context controls`() {
        assertFalse(CommandType.entries.any { it.name == "CONTEXT" })
        CommandType.entries.forEach { type ->
            assertFalse(EditorMenuLayout.fields(type).any { it.key == "context" || it.key == "context" + "Source" })
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
