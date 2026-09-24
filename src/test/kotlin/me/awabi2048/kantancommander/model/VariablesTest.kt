package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.error.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #2 の共通変数モデルのテストです。
 *
 * 型・scope・寿命・codec の契約を固定します。後続機能（#3・#5・#8・#22）は
 * 具象storeではなく、このモデルと [VariableStore] 契約を利用します。
 */
class VariablesTest {
    @Test
    fun `value type parses with legacy position fallback`() {
        assertEquals(ValueType.NUMBER, ValueType.parse("NUMBER"))
        assertEquals(ValueType.LOCATION, ValueType.parse("POSITION"))
        assertEquals(ValueType.LOCATION, ValueType.parse(" position "))
        assertNull(ValueType.parse("BOOLEAN"))
        assertNull(ValueType.parse("VECTOR"))
        assertNull(ValueType.parse("unknown"))
        assertNull(ValueType.parse(null))
    }

    @Test
    fun `typed values validate their own shape`() {
        assertTrue(TypedValue(ValueType.NUMBER, numberValue = 1.0).isValid())
        assertFalse(TypedValue(ValueType.NUMBER, numberValue = Double.NaN).isValid())
        assertFalse(TypedValue(ValueType.NUMBER, numberValue = 1.0, stringValue = "x").isValid())
        assertTrue(TypedValue(ValueType.STRING, stringValue = "a").isValid())
        assertTrue(
            TypedValue(ValueType.LOCATION, location = SavedLocation(1.0, 2.0, 3.0)).isValid(),
        )
        assertTrue(TypedValue(ValueType.ITEM, item = "STONE").isValid())
        assertFalse(TypedValue(ValueType.ITEM).isValid())
        assertTrue(TypedValue(ValueType.BLOCK, block = "STONE").isValid())
        // ENTITY は実体なしでも保持します。解決不能は参照時に判定します。
        assertTrue(TypedValue(ValueType.ENTITY).isValid())
        assertTrue(TypedValue(ValueType.ENTITY, entityId = UUID.randomUUID()).isValid())
    }

    @Test
    fun `empty values are valid placeholders for every type`() {
        ValueType.entries.forEach { type ->
            assertTrue(TypedValue.empty(type).isValid(), "empty($type) must be valid")
            assertEquals(type, TypedValue.empty(type).type)
        }
    }

    @Test
    fun `variable scope parses without entity preselection`() {
        assertEquals(VariableScope.EXECUTION, VariableScope.parse("EXECUTION"))
        assertEquals(VariableScope.WORLD, VariableScope.parse("world"))
        assertEquals(VariableScope.ENTITY, VariableScope.parse("ENTITY"))
        assertNull(VariableScope.parse("unknown"))
        assertNull(VariableScope.parse(null))
    }

    @Test
    fun `failures map to the shared error contract`() {
        assertEquals(ErrorCode.VARIABLE_UNDEFINED, VariableFailure.Undefined.toErrorCode())
        assertEquals(ErrorCode.VARIABLE_UNSET, VariableFailure.Unset.toErrorCode())
        assertEquals(ErrorCode.VARIABLE_OWNER_MISSING, VariableFailure.OwnerMissing.toErrorCode())
        assertEquals(ErrorCode.VARIABLE_SCOPE_MISSING, VariableFailure.ScopeMissing.toErrorCode())
        assertEquals(
            ErrorCode.VARIABLE_TYPE_MISMATCH,
            VariableFailure.TypeMismatch(ValueType.NUMBER, ValueType.STRING).toErrorCode(),
        )
    }

    @Test
    fun `execution store defines sets and removes with type stability`() {
        val store = ExecutionVariables()
        assertTrue(store.define("counter", TypedValue(ValueType.NUMBER, numberValue = 1.0)))
        assertFalse(store.define("counter", TypedValue(ValueType.NUMBER, numberValue = 2.0)))
        assertEquals(1.0, store.get("counter")?.numberValue)
        assertEquals(ValueType.NUMBER, store.definition("counter"))
        assertEquals(mapOf("counter" to ValueType.NUMBER), store.definitions())

        // SET は上書きします。型の変更は拒否します。
        store.set("counter", TypedValue(ValueType.NUMBER, numberValue = 5.0))
        assertEquals(5.0, store.get("counter")?.numberValue)
        assertThrows(IllegalArgumentException::class.java) {
            store.set("counter", TypedValue(ValueType.STRING, stringValue = "x"))
        }
        // 未定義名の SET は暗黙定義として記録します。
        store.set("fresh", TypedValue(ValueType.STRING, stringValue = "s"))
        assertEquals(ValueType.STRING, store.definition("fresh"))

        assertTrue(store.remove("counter"))
        assertFalse(store.remove("counter"))
        assertNull(store.get("counter"))
        assertNull(store.definition("counter"))
    }

    @Test
    fun `execution store snapshots isolate call frames`() {
        val store = ExecutionVariables()
        store.set("a", TypedValue(ValueType.NUMBER, numberValue = 1.0))
        val snapshot = store.snapshot()
        store.set("a", TypedValue(ValueType.NUMBER, numberValue = 2.0))
        store.set("b", TypedValue(ValueType.STRING, stringValue = "x"))
        store.restore(snapshot)
        assertEquals(1.0, store.get("a")?.numberValue)
        assertNull(store.get("b"))
        assertNull(store.definition("b"))
    }
}
