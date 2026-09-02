package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertEquals(CommandDialogSpecs.InputFormat.ANY_STRING, name.format)
        assertNull(name.validate(""))
        assertNull(limit.validate("1"))
        assertEquals(CommandDialogSpecs.InputFormat.QUANTITY, limit.format)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID, limit.validate("0"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID, limit.validate("+1"))
        val distance = requireNotNull(CommandDialogSpecs.targetFilter("distance"))
        assertEquals(CommandDialogSpecs.InputFormat.NUMBER, distance.format)
        assertNull(distance.validate("1.5"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID, distance.validate("-1"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID,
            distance.validate("\${distance}"),
        )
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_ENTITY_TYPE_FORMAT, entityType.validate("bad id"))

        val range = requireNotNull(CommandDialogSpecs.targetFilter("range"))
        assertEquals(64, range.maxLength)
        assertNull(range.validateInput(""))
        assertNull(range.validate("1.5"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID, range.validate("-1"))
    }

    @Test
    fun `direct field specs cover loop values and field-specific validators`() {
        val valueSpec = requireNotNull(CommandDialogSpecs.field("value"))
        val countSpec = requireNotNull(CommandDialogSpecs.field("count"))
        val timeSpec = requireNotNull(CommandDialogSpecs.field("staySeconds"))
        assertEquals(512, valueSpec.maxLength)
        assertEquals(CommandDialogSpecs.InputFormat.ANY_STRING, valueSpec.format)
        assertEquals(16, countSpec.maxLength)
        assertEquals(CommandDialogSpecs.InputFormat.QUANTITY, countSpec.format)
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID,
            countSpec.validate("0"),
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID,
            countSpec.validate("+1"),
        )
        assertNull(countSpec.validate("\${limit}"))
        assertNull(countSpec.validate("\${CURRENT_LOOP_COUNT}"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_POSITIVE_INVALID,
            countSpec.validate("not-a-number"),
        )
        assertEquals(CommandDialogSpecs.InputFormat.TIME, timeSpec.format)
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID,
            timeSpec.validate("-1"),
        )
        assertNull(timeSpec.validate("+1"))
        assertNull(timeSpec.validate("0"))
        assertNull(timeSpec.validate("0.05"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TICK_INVALID, timeSpec.validate("0.01"))
        assertNull(timeSpec.validate("86400"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID, timeSpec.validate("86400.05"))
    }

    @Test
    fun `command-aware specs follow runtime numeric contracts`() {
        val wait = CommandType.WAIT.newNode()
        val shake = CommandType.CAMERA_SHAKE.newNode()
        val effect = CommandType.APPLY_EFFECT.newNode()
        val repeat = CommandType.FOR_START.newNode()

        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_REPEAT_COUNT,
            requireNotNull(CommandDialogSpecs.field(repeat, "count")).labelKey,
        )

        val waitSpec = requireNotNull(CommandDialogSpecs.field(wait, "seconds"))
        assertEquals(CommandDialogSpecs.InputFormat.TIME, waitSpec.format)
        assertNull(waitSpec.validate("+1"))
        assertNull(waitSpec.validate("0.05"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TICK_INVALID, waitSpec.validate("0.01"))
        assertNull(waitSpec.validate("86400"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID, waitSpec.validate("86400.05"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID,
            waitSpec.validateInput(""),
        )
        assertEquals(CommandDialogSpecs.InputFormat.TIME, requireNotNull(CommandDialogSpecs.field(shake, "seconds")).format)
        assertNull(requireNotNull(CommandDialogSpecs.field(shake, "seconds")).validate("1.5"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_LEVEL_INVALID,
            requireNotNull(CommandDialogSpecs.field(effect, "level")).validate("256"),
        )

        val variable = CommandType.VARIABLE.newNode().apply {
            params["type"] = VariableType.NUMBER.name
            params["operation"] = VariableOperation.DEFINE.name
        }
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_VARIABLE_NAME,
            requireNotNull(CommandDialogSpecs.field(variable, "name")).validate("VariableName"),
        )
        assertNull(requireNotNull(CommandDialogSpecs.field(variable, "value")).validate("1.5"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT,
            requireNotNull(CommandDialogSpecs.field(variable, "value")).validate("not-a-number"),
        )

        variable.params["operation"] = VariableOperation.CHANGE.name
        variable.params["changeMode"] = "CALCULATE"
        val expressionSpec = requireNotNull(CommandDialogSpecs.field(variable, "value"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_OPERAND_REQUIRED,
            expressionSpec.validate("1 +"),
        )
        assertNull(expressionSpec.validate("1 + 2"))
        val expressionErrors = listOf(
            "" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_EMPTY,
            "1 2" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_TRAILING_CHARACTERS,
            "(1 + 2" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_UNCLOSED_PARENTHESIS,
            "1e999" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_INVALID_NUMBER,
            "1 @ 2" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_INVALID_CHARACTER,
            "\$unknown" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_INVALID_CHARACTER,
            "\${BadName}" to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_EXPRESSION_INVALID_VARIABLE_NAME,
        )
        expressionErrors.forEach { (raw, expected) ->
            assertEquals(expected, expressionSpec.validate(raw), raw)
        }
    }

    @Test
    fun `timer and multi-value dialogs share strict finite input boundaries`() {
        assertEquals(6, CommandDialogSpecs.timerSeconds.maxLength)
        assertNull(CommandDialogSpecs.timerSeconds.validate("1"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_INVALID,
            CommandDialogSpecs.timerSeconds.validate("+1"),
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_INVALID,
            CommandDialogSpecs.timerSeconds.validate("86401"),
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_INVALID,
            CommandDialogSpecs.timerSeconds.validateInput(""),
        )
        val displayTiming = requireNotNull(CommandDialogSpecs.field(CommandType.DISPLAY_TEXT.newNode(), "staySeconds"))
        assertEquals(CommandDialogSpecs.InputFormat.TIME, displayTiming.format)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIME_FORMAT_HINT, displayTiming.formatHintKey)
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID,
            displayTiming.validateInput(""),
        )
        assertNull(displayTiming.validate("0.05"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TICK_INVALID, displayTiming.validate("0.01"))
        assertNull(displayTiming.validate("86400"))
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID, displayTiming.validate("86400.05"))
        assertEquals(CommandDialogSpecs.InputFormat.TIME, CommandDialogSpecs.timerSeconds.format)
        assertEquals(1.5, CommandDialogSpecs.finiteDouble("1.5"))
        assertNull(CommandDialogSpecs.finiteDouble("NaN"))
        assertNull(CommandDialogSpecs.finiteFloat("Infinity"))
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

    @Test
    fun `block operation and condition use their runtime material contracts`() {
        val placement = requireNotNull(CommandDialogSpecs.field(CommandType.BLOCK_OPERATION.newNode(), "block"))
        val condition = requireNotNull(CommandDialogSpecs.field(CommandType.CONDITION.newNode(), "block"))

        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT,
            placement.validate("minecraft:air"),
        )
        assertNull(condition.validate("minecraft:air"))
    }

    @Test
    fun `namespaced input normalization is shared by both editors`() {
        assertEquals("minecraft:stone", CommandDialogSpecs.normalize("entityType", " MINECRAFT:STONE "))
        assertEquals("Text", CommandDialogSpecs.normalize("text", " Text "))
        assertTrue(CommandValueRules.isSoundId("resourcepack:custom.sound"))
    }

    @Test
    fun `unsigned integer parser is shared by input and execution contracts`() {
        assertEquals(1, CommandValueRules.parsePositiveInt("1"))
        assertNull(CommandValueRules.parsePositiveInt("+1"))
        assertNull(CommandValueRules.parsePositiveInt("0"))
        assertNull(CommandValueRules.parsePositiveInt("2147483648"))
        assertEquals(0, CommandValueRules.parseNonNegativeInt("0"))
        assertNull(CommandValueRules.parseNonNegativeInt("+1"))
    }

    @Test
    fun `tag inputs are validated as one string without comma splitting`() {
        val summonTag = requireNotNull(CommandDialogSpecs.field("tags"))
        val entityTag = requireNotNull(CommandDialogSpecs.field("tag"))

        assertNull(summonTag.validate("spawn_tag"))
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_TAG_FORMAT,
            summonTag.validate("spawn_tag,other"),
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_TAG_FORMAT,
            entityTag.validate("entity_tag,other"),
        )
    }
}
