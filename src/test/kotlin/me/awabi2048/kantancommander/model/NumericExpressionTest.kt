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
    fun `parser exposes ordinary and loop readonly references`() {
        val expression = requireNotNull(
            NumericExpression.parse("base * 2 + \$current_loop_count").expression,
        )

        assertEquals(setOf("base", "\$current_loop_count"), expression.references)
        assertEquals(17.0, expression.evaluate { name -> if (name == "base") 5.0 else 7.0 })
    }

    @Test
    fun `evaluation rejects missing values and division by zero`() {
        assertNull(NumericExpression.parse("known / 0").expression?.evaluate { 1.0 })
        assertNull(NumericExpression.parse("known + missing").expression?.evaluate { if (it == "known") 1.0 else null })
    }

    @Test
    fun `parser rejects unknown readonly names and malformed expressions`() {
        assertFalse(NumericExpression.isValid("\$unknown"))
        assertFalse(NumericExpression.isValid("1 +"))
        assertFalse(NumericExpression.isValid("(1 + 2"))
        assertTrue(NumericExpression.isValid("my-var - 1"))
    }
}
