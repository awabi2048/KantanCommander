package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandMenuPresentationTest {
    @Test
    fun `every command type has one or more explicit categories`() {
        val categoriesByType = CommandType.entries.associateWith(CommandPresentationPolicy::categories)
        assertEquals(CommandType.entries.toSet(), categoriesByType.keys)
        assertTrue(categoriesByType.values.all { it.isNotEmpty() })
        // 基礎タブ廃止後の5分類です。旧基礎コマンドは意味の近い分類へ移しました。
        assertTrue(CommandCategory.ENTITY in CommandPresentationPolicy.categories(CommandType.TELEPORT))
        assertTrue(CommandCategory.ENTITY in CommandPresentationPolicy.categories(CommandType.GIVE_ITEM))
        assertTrue(CommandCategory.ENTITY in CommandPresentationPolicy.categories(CommandType.SUMMON_ENTITY))
        assertTrue(CommandCategory.EFFECT in CommandPresentationPolicy.categories(CommandType.DISPLAY_TEXT))
        assertTrue(CommandCategory.EFFECT in CommandPresentationPolicy.categories(CommandType.APPLY_EFFECT))
        assertTrue(CommandCategory.CONTROL in CommandPresentationPolicy.categories(CommandType.WAIT))
        assertTrue(CommandCategory.ENTITY in CommandPresentationPolicy.categories(CommandType.ENTITY_OPERATION))
        assertTrue(CommandCategory.BUILD in CommandPresentationPolicy.categories(CommandType.BLOCK_OPERATION))
        assertTrue(CommandCategory.EFFECT in CommandPresentationPolicy.categories(CommandType.PARTICLE))
        assertTrue(CommandCategory.CONTROL in CommandPresentationPolicy.categories(CommandType.CONDITION))
        assertTrue(CommandCategory.CONTROL in CommandPresentationPolicy.categories(CommandType.DISK_CALL))
        assertTrue(CommandCategory.VARIABLE in CommandPresentationPolicy.categories(CommandType.VARIABLE))
        assertEquals(
            setOf(CommandCategory.ENTITY, CommandCategory.BUILD),
            CommandPresentationPolicy.categories(CommandType.PLACE_ITEM),
        )
    }

    @Test
    fun `nested open branch picker retains normal commands and optional merge`() {
        val entityTypes = CommandPickerTypePolicy.types(
            category = CommandCategory.ENTITY,
            mergeAvailable = true,
            insideForBody = false,
        )
        val controlTypes = CommandPickerTypePolicy.types(
            category = CommandCategory.CONTROL,
            mergeAvailable = true,
            insideForBody = false,
        )

        assertTrue(CommandType.TELEPORT in entityTypes)
        assertTrue(CommandType.PLACE_ITEM in entityTypes)
        assertTrue(CommandType.CONDITION in controlTypes)
        assertTrue(CommandType.MERGE in controlTypes)
        assertTrue(CommandType.WAIT in controlTypes)
        // 分類ごとの候補は1ページに収め、ページングは将来の
        // 増加時の補助手段として残します。
        CommandCategory.entries.forEach { category ->
            val types = CommandPickerTypePolicy.types(
                category = category,
                mergeAvailable = true,
                insideForBody = false,
            )
            assertTrue(types.size <= GestureCommandPickerLayoutPolicy.PAGE_SIZE, "$category")
        }
        assertTrue(
            CommandType.PLACE_ITEM in CommandPickerTypePolicy.types(
                CommandCategory.BUILD,
                mergeAvailable = true,
                insideForBody = false,
            ),
        )
        assertEquals(1, GestureCommandPickerLayoutPolicy.pageCount(entityTypes.size))
        assertTrue(controlTypes.size <= GestureCommandPickerLayoutPolicy.PAGE_SIZE)
    }

    @Test
    fun `simple inventory picker fits every simple category`() {
        // 簡易選択画面は簡易分類のタブで候補を分けます。
        // 基礎タブ廃止後の簡易3タブとし、待機は制御系へ移しました。
        val simpleCategories = CommandCategory.entries.filter { it.isSimple() }
        assertEquals(
            listOf(CommandCategory.ENTITY, CommandCategory.BUILD, CommandCategory.EFFECT),
            simpleCategories,
        )
        simpleCategories.forEach { category ->
            val types = CommandPresentationPolicy.SIMPLE_TYPES.filter {
                category in CommandPresentationPolicy.categories(it)
            }
            assertTrue(types.isNotEmpty(), "$category")
            assertTrue(types.size <= CommandPickerLayoutPolicy.itemSlots.size, "$category")
        }
        assertEquals(CommandPickerLayoutPolicy.categorySlots.size, simpleCategories.size)
        // 簡易集合は待機と建造物保存を除く17型です。重複所属は型数を増やしません。
        assertEquals(17, CommandPresentationPolicy.SIMPLE_TYPES.size)
        assertTrue(CommandType.TELEPORT in CommandPresentationPolicy.SIMPLE_TYPES)
        assertEquals(1, CommandPresentationPolicy.SIMPLE_TYPES.count { it == CommandType.PLACE_ITEM })
        assertFalse(CommandType.WAIT in CommandPresentationPolicy.SIMPLE_TYPES)
        assertFalse(CommandType.CONDITION in CommandPresentationPolicy.SIMPLE_TYPES)
        assertFalse(CommandType.VARIABLE in CommandPresentationPolicy.SIMPLE_TYPES)
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
