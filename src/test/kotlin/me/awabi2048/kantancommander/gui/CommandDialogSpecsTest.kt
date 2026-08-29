package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Inventory GUIとGesture GUIの入力仕様が同じ定義から解決されることを検証します。
 * 画面を起動しなくても、maxLengthと検証境界の分岐を回帰テストできます。
 */
class CommandDialogSpecsTest {

    @Test
    fun `target filters share length and validation boundaries`() {
        val name = requireNotNull(CommandDialogSpecs.targetFilter("name"))
        val limit = requireNotNull(CommandDialogSpecs.targetFilter("limit"))
        val entityType = requireNotNull(CommandDialogSpecs.targetFilter("entityType"))

        assertEquals(256, name.maxLength)
        assertNull(name.validate(""))
        assertNull(limit.validate("1"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID, limit.validate("0"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_ENTITY_TYPE_FORMAT, entityType.validate("bad id"))
    }

    @Test
    fun `direct field specs cover loop values and field-specific validators`() {
        assertEquals(512, requireNotNull(CommandDialogSpecs.field("value")).maxLength)
        assertEquals(16, requireNotNull(CommandDialogSpecs.field("count")).maxLength)
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID,
            requireNotNull(CommandDialogSpecs.field("count")).validate("0"),
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTEGER_INVALID,
            requireNotNull(CommandDialogSpecs.field("startValue", "FIXED")).validate("not-a-number"),
        )
        assertNull(requireNotNull(CommandDialogSpecs.field("startValue", "TEMPORARY")).validate("variable_name"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STEP_ZERO,
            requireNotNull(CommandDialogSpecs.field("stepValue", "FIXED")).validate("0"),
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID,
            requireNotNull(CommandDialogSpecs.field("stay")).validate("-1"),
        )
        assertNull(requireNotNull(CommandDialogSpecs.field("stay")).validate("0"))
    }
}
