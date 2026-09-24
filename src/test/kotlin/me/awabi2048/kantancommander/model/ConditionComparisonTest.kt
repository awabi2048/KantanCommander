package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #5 の型付き条件比較テストです。
 *
 * 左辺・演算子・右辺の共通モデルで NUMBER／STRING と複合値述語
 * （ENTITY同一性）を検証します。正常な不一致と解決失敗・型不一致の区別は
 * 実行層の ConditionEvaluation で扱い、ここでは比較契約を固定します。
 */
class ConditionComparisonTest {
    @Test
    fun `numbers compare with six operators`() {
        val left = TypedValue(ValueType.NUMBER, numberValue = 3.0)
        val right = TypedValue(ValueType.NUMBER, numberValue = 5.0)
        assertEquals(true, ConditionComparison.compare(left, "==", TypedValue(ValueType.NUMBER, numberValue = 3.0)))
        assertEquals(true, ConditionComparison.compare(left, "!=", right))
        assertEquals(true, ConditionComparison.compare(right, ">", left))
        assertEquals(true, ConditionComparison.compare(right, ">=", right))
        assertEquals(true, ConditionComparison.compare(left, "<", right))
        assertEquals(true, ConditionComparison.compare(left, "<=", left))
        assertEquals(false, ConditionComparison.compare(left, ">", right))
        assertEquals(false, ConditionComparison.compare(left, "==", right))
        // 型外の演算子は不一致ではなく不適用として区別します。
        assertNull(ConditionComparison.compare(left, "contains", right))
    }

    @Test
    fun `strings compare with equality and containment`() {
        val left = TypedValue(ValueType.STRING, stringValue = "hello world")
        assertEquals(true, ConditionComparison.compare(left, "==", TypedValue(ValueType.STRING, stringValue = "hello world")))
        assertEquals(true, ConditionComparison.compare(left, "!=", TypedValue(ValueType.STRING, stringValue = "other")))
        assertEquals(true, ConditionComparison.compare(left, "contains", TypedValue(ValueType.STRING, stringValue = "lo wo")))
        assertEquals(false, ConditionComparison.compare(left, "contains", TypedValue(ValueType.STRING, stringValue = "bye")))
        assertNull(ConditionComparison.compare(left, ">", TypedValue(ValueType.STRING, stringValue = "a")))
    }

    @Test
    fun `entities compare by identity`() {
        val id = UUID.randomUUID()
        val left = TypedValue(ValueType.ENTITY, entityId = id)
        assertEquals(true, ConditionComparison.compare(left, "==", TypedValue(ValueType.ENTITY, entityId = id)))
        assertEquals(true, ConditionComparison.compare(left, "!=", TypedValue(ValueType.ENTITY, entityId = UUID.randomUUID())))
        assertEquals(false, ConditionComparison.compare(left, "==", TypedValue(ValueType.ENTITY)))
        assertNull(ConditionComparison.compare(left, ">", TypedValue(ValueType.ENTITY, entityId = id)))
    }

    @Test
    fun `mismatched types never compare`() {
        val number = TypedValue(ValueType.NUMBER, numberValue = 1.0)
        val text = TypedValue(ValueType.STRING, stringValue = "1")
        assertNull(ConditionComparison.compare(number, "==", text))
        assertNull(ConditionComparison.compare(number, "!=", text))
    }

    @Test
    fun `operators are published per type`() {
        assertTrue(ConditionComparison.operatorsFor(ValueType.NUMBER).containsAll(listOf("==", "!=", ">", ">=", "<", "<=")))
        assertEquals(setOf("==", "!=", "contains"), ConditionComparison.operatorsFor(ValueType.STRING))
        assertEquals(
            setOf("==", "!=", "in_region", "not_in_region"),
            ConditionComparison.operatorsFor(ValueType.ENTITY),
        )
        assertEquals(
            setOf("in_region", "not_in_region"),
            ConditionComparison.operatorsFor(ValueType.LOCATION),
        )
        assertTrue(ConditionComparison.ALL.contains("contains"))
        assertTrue(ConditionComparison.ALL.containsAll(listOf("in_region", "not_in_region")))
        assertTrue(ConditionComparison.isRegionPredicate("in_region"))
        assertTrue(ConditionComparison.isRegionPredicate("not_in_region"))
        assertFalse(ConditionComparison.isRegionPredicate("=="))
        // 領域述語は右辺ではなくノードの領域指定を使うため、比較関数では扱いません。
        assertNull(
            ConditionComparison.compare(
                TypedValue(ValueType.LOCATION),
                "in_region",
                TypedValue(ValueType.LOCATION),
            ),
        )
    }
}
