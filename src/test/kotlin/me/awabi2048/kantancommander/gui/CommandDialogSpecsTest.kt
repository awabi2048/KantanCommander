package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
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
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID, limit.validate("0"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID, limit.validate("+1"))
        val distance = requireNotNull(CommandDialogSpecs.targetFilter("distance"))
        assertNull(distance.validate("1.5"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID, distance.validate("-1"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_ENTITY_TYPE_FORMAT, entityType.validate("bad id"))
    }

    @Test
    fun `direct field specs cover loop values and field-specific validators`() {
        assertEquals(512, requireNotNull(CommandDialogSpecs.field("value")).maxLength)
        assertEquals(16, requireNotNull(CommandDialogSpecs.field("count")).maxLength)
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID,
            requireNotNull(CommandDialogSpecs.field("count")).validate("0"),
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID,
            requireNotNull(CommandDialogSpecs.field("count")).validate("+1"),
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
            requireNotNull(CommandDialogSpecs.field("staySeconds")).validate("-1"),
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID,
            requireNotNull(CommandDialogSpecs.field("staySeconds")).validate("+1"),
        )
        assertNull(requireNotNull(CommandDialogSpecs.field("staySeconds")).validate("0"))
    }

    @Test
    fun `command-aware specs follow runtime numeric contracts`() {
        val wait = CommandType.WAIT.newNode()
        val shake = CommandType.CAMERA_SHAKE.newNode()
        val effect = CommandType.APPLY_EFFECT.newNode()

        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID,
            requireNotNull(CommandDialogSpecs.field(wait, "seconds")).validate("+1"),
        )
        assertNull(requireNotNull(CommandDialogSpecs.field(shake, "seconds")).validate("1.5"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_LEVEL_INVALID,
            requireNotNull(CommandDialogSpecs.field(effect, "level")).validate("256"),
        )

        val variable = CommandType.VARIABLE.newNode().apply {
            params["type"] = VariableType.DECIMAL.name
            params["operation"] = VariableOperation.SET.name
        }
        assertNull(requireNotNull(CommandDialogSpecs.field(variable, "value")).validate("1.5"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT,
            requireNotNull(CommandDialogSpecs.field(variable, "value")).validate("not-a-number"),
        )
    }

    @Test
    fun `block field uses the same material validation in every gui`() {
        val block = requireNotNull(CommandDialogSpecs.field("block"))

        assertEquals(64, block.maxLength)
        assertNull(block.validate("minecraft:stone"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT,
            block.validate("not a block id"),
        )
    }
}
