package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NumericExpressionTest {
    @Test
    fun `parser applies arithmetic precedence and parentheses`() {
        val expression = requireNotNull(NumericExpression.parse("2 + 3 * (4 - 1)").expression)

        assertEquals(11.0, expression.evaluate { null })
    }

    @Test
    fun `parser exposes ordinary and system references in unified syntax`() {
        val expression = requireNotNull(
            NumericExpression.parse("\${base} * 2 + \${CURRENT_LOOP_COUNT}").expression,
        )

        assertEquals(setOf("base", "CURRENT_LOOP_COUNT"), expression.references)
        assertEquals(17.0, expression.evaluate { name -> if (name == "base") 5.0 else 7.0 })
    }

    @Test
    fun `evaluation rejects missing values and division by zero`() {
        assertNull(NumericExpression.parse("known / 0").expression?.evaluate { 1.0 })
        assertNull(NumericExpression.parse("known + missing").expression?.evaluate { if (it == "known") 1.0 else null })
    }

    @Test
    fun `parser rejects legacy references and malformed expressions`() {
        assertFalse(NumericExpression.isValid("\$unknown"))
        assertTrue(NumericExpression.isValid("\${unknown}"))
        assertTrue(NumericExpression.isValid("\${CURRENT_LOOP_COUNT}"))
        assertTrue(NumericExpression.isValid("%{temporary}% + 1"))
        assertFalse(NumericExpression.isValid("%temporary% + 1"))
        assertFalse(NumericExpression.isValid("1 +"))
        assertFalse(NumericExpression.isValid("(1 + 2"))
        assertTrue(NumericExpression.isValid("\${my-var} - 1"))
    }

    @Test
    fun `parser separates temporary references from world references`() {
        val expression = requireNotNull(
            NumericExpression.parse("%{temporary}% + \${world}").expression,
        )

        assertEquals(setOf("world"), expression.references)
        assertEquals(setOf("temporary"), expression.temporaryReferences)
        assertEquals(
            7.0,
            expression.evaluate(
                worldResolve = { if (it == "world") 2.0 else null },
                tempResolve = { if (it == "temporary") 5.0 else null },
            ),
        )
    }

    @Test
    fun `parser exposes stable error codes for every user-facing syntax failure`() {
        val cases = mapOf(
            "" to NumericExpression.ErrorCode.EMPTY,
            "1 2" to NumericExpression.ErrorCode.TRAILING_CHARACTERS,
            "(1 + 2" to NumericExpression.ErrorCode.UNCLOSED_PARENTHESIS,
            "1 +" to NumericExpression.ErrorCode.OPERAND_REQUIRED,
            "1e999" to NumericExpression.ErrorCode.INVALID_NUMBER,
            "1 @ 2" to NumericExpression.ErrorCode.INVALID_CHARACTER,
            "\$unknown" to NumericExpression.ErrorCode.INVALID_CHARACTER,
            "\${unknown" to NumericExpression.ErrorCode.INVALID_VARIABLE_NAME,
            "\${BadName}" to NumericExpression.ErrorCode.INVALID_VARIABLE_NAME,
        )

        cases.forEach { (raw, expected) ->
            assertEquals(expected, NumericExpression.parse(raw).error?.code, raw)
        }
    }
}
