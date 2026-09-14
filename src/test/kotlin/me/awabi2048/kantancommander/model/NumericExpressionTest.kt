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

    @Test
    fun `power binds tighter than unary and associates to the right`() {
        assertEquals(8.0, NumericExpression.parse("2^3").expression?.evaluate { null })
        // 2^(3^2) = 512
        assertEquals(512.0, NumericExpression.parse("2^3^2").expression?.evaluate { null })
        // -(2^2) = -4
        assertEquals(-4.0, NumericExpression.parse("-2^2").expression?.evaluate { null })
        assertEquals(9.0, NumericExpression.parse("(1 + 2)^2").expression?.evaluate { null })
        // 定義域外・非有限結果は解決失敗にします。
        assertNull(NumericExpression.parse("(-8)^0.5").expression?.evaluate { null })
    }

    @Test
    fun `trigonometric functions use degrees`() {
        assertEquals(1.0, NumericExpression.parse("sin(90)").expression?.evaluate { null }!!, 1e-9)
        assertEquals(0.0, NumericExpression.parse("cos(90)").expression?.evaluate { null }!!, 1e-9)
        assertEquals(1.0, NumericExpression.parse("tan(45)").expression?.evaluate { null }!!, 1e-9)
        assertEquals(30.0, NumericExpression.parse("asin(0.5)").expression?.evaluate { null }!!, 1e-9)
        assertEquals(60.0, NumericExpression.parse("acos(0.5)").expression?.evaluate { null }!!, 1e-9)
        assertEquals(45.0, NumericExpression.parse("atan(1)").expression?.evaluate { null }!!, 1e-9)
        // 大文字も受け付け、定義域外は解決失敗にします。
        assertEquals(0.0, NumericExpression.parse("SIN(0)").expression?.evaluate { null }!!, 1e-9)
        assertNull(NumericExpression.parse("asin(2)").expression?.evaluate { null })
        // 未知の関数名は文字不正として拒否します。
        assertFalse(NumericExpression.isValid("foo(1)"))
        assertFalse(NumericExpression.isValid("sin 30"))
    }

    @Test
    fun `literal operand detection separates calculate contract from syntax`() {
        // 構文として正しい式でも、数値リテラルを含むかで CALCULATE 可否が分かれます。
        listOf("2 + 3", "\${a} * 0.5", "sin(30)", "-2", "2^3").forEach { raw ->
            assertTrue(
                requireNotNull(NumericExpression.parse(raw).expression).hasLiteralOperand(),
                raw,
            )
        }
        listOf(
            "\${a} + \${b}",
            "%{x}% * \${rate}",
            "sin(\${angle})",
            "-(\${a})",
            "-\${a}",
        ).forEach { raw ->
            assertTrue(NumericExpression.isValid(raw), raw)
            assertFalse(
                requireNotNull(NumericExpression.parse(raw).expression).hasLiteralOperand(),
                raw,
            )
        }
    }

    @Test
    fun `vanilla lowering accepts only basic arithmetic`() {
        val basic = requireNotNull(NumericExpression.parse("1 + 2 * \${x}").expression)
        assertTrue(basic.isVanillaLowerable())
        val power = requireNotNull(NumericExpression.parse("2^3").expression)
        assertFalse(power.isVanillaLowerable())
        val trig = requireNotNull(NumericExpression.parse("sin(30)").expression)
        assertFalse(trig.isVanillaLowerable())
    }
}
