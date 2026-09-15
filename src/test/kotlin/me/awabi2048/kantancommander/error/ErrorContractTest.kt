package me.awabi2048.kantancommander.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ErrorContractTest {
    @Test
    fun `severity defaults follow the agreed ERROR WARNING INFO contract`() {
        assertEquals(ErrorSeverity.ERROR, ErrorCode.COMMAND_FAILED.defaultSeverity)
        assertEquals(ErrorSeverity.WARNING, ErrorCode.TEMPORARY_UNRESOLVED.defaultSeverity)
        assertEquals(ErrorSeverity.WARNING, ErrorCode.PARTICLE_QUOTA_EXCEEDED.defaultSeverity)
        assertEquals(ErrorSeverity.WARNING, ErrorCode.CAPABILITY_MISMATCH.defaultSeverity)
        assertEquals(ErrorSeverity.INFO, ErrorCode.TARGET_EMPTY.defaultSeverity)
        assertEquals(ErrorSeverity.INFO, ErrorCode.NON_APPLICABLE_TARGET.defaultSeverity)
        assertEquals(ErrorSeverity.INFO, ErrorCode.MANUAL_STOP.defaultSeverity)
    }

    @Test
    fun `phases distinguish edit persisted preflight runtime`() {
        assertEquals(ValidationPhase.EDIT_TIME, ErrorCode.TIMER_RANGE.defaultPhase)
        assertEquals(ValidationPhase.PERSISTED_STRUCTURAL, ErrorCode.GRAPH_CYCLE.defaultPhase)
        assertEquals(ValidationPhase.PERSISTED_STRUCTURAL, ErrorCode.PERSISTED_SHAPE_INVALID.defaultPhase)
        assertEquals(ValidationPhase.PREFLIGHT, ErrorCode.REQUIRED_PROGRAM_MISSING.defaultPhase)
        // issue #35-3: 固定実体（FIXED_ENTITY_UNRESOLVED）は撤去しました。
        assertEquals(ValidationPhase.RUNTIME, ErrorCode.TRIGGER_CONTEXT_MISSING.defaultPhase)
    }

    @Test
    fun `empty unresolved mismatch invalid map to different policies`() {
        assertEquals(FailurePolicy.EMPTY_RESULT, ErrorCode.TARGET_EMPTY.defaultPolicy)
        assertEquals(FailurePolicy.SKIP_COMMAND, ErrorCode.TEMPORARY_UNRESOLVED.defaultPolicy)
        // issue #35-3: 固定実体（FIXED_ENTITY_UNRESOLVED）は撤去しました。
        assertEquals(FailurePolicy.PARTIAL_APPLY, ErrorCode.CAPABILITY_MISMATCH.defaultPolicy)
        assertEquals(FailurePolicy.RETURN_ERROR, ErrorCode.CONDITION_UNRESOLVED.defaultPolicy)
        assertEquals(FailurePolicy.RETURN_ERROR, ErrorCode.VARIABLE_UNDEFINED.defaultPolicy)
    }

    @Test
    fun `condition unresolved is not a normal false branch`() {
        // 正常比較 false と解決失敗を同一視しないため、既定は FALSE_BRANCH にしない。
        assertEquals(FailurePolicy.RETURN_ERROR, ErrorCode.CONDITION_UNRESOLVED.defaultPolicy)
        assertEquals(FailurePolicy.RETURN_ERROR, ErrorCode.CONDITION_INVALID_TYPE.defaultPolicy)
    }

    @Test
    fun `legacy reason strings resolve without collapsing unknown`() {
        assertEquals(ErrorCode.COMMAND_FAILED, ErrorCode.fromLegacyReason("command_failed"))
        assertEquals(ErrorCode.INVALID_SCRIPT, ErrorCode.fromLegacyReason("invalid_script: root: xxx"))
        assertNull(ErrorCode.fromLegacyReason("totally_unknown_reason_xyz"))
        assertNull(ErrorCode.fromLegacyReason(null))
    }

    @Test
    fun `call stack frames accumulate without flattening callee errors`() {
        val scriptId = UUID.randomUUID()
        val outer = UUID.randomUUID()
        val inner = UUID.randomUUID()
        val error = ExecutionError(code = ErrorCode.COMMAND_FAILED, scriptId = scriptId, nodeId = inner)
            .withCallerFrame(CallFrame(scriptId, outer))
        assertEquals(1, error.callStack.size)
        assertEquals(outer, error.callStack.single().nodeId)
        assertEquals(inner, error.nodeId)
    }

    @Test
    fun `partial applicability tracks applied nonApplicable failed`() {
        val partial = PartialApplicability(appliedCount = 1, nonApplicableCount = 2, failedCount = 3)
        assertEquals(6, partial.totalCount)
        val error = ExecutionError(code = ErrorCode.CAPABILITY_MISMATCH, partial = partial)
        assertEquals(1, error.partial?.appliedCount)
        assertEquals(2, error.partial?.nonApplicableCount)
        assertEquals(3, error.partial?.failedCount)
    }

    @Test
    fun `partial display vars appear only for mixed outcomes`() {
        // issue #32 のレビューF4対応：全件適用時は件数表示を付けず、
        // 非対応・失敗が混ざるときだけ置換変数を返します。
        assertNull(PartialApplicability(appliedCount = 3).displayVars())
        assertNull(PartialApplicability().displayVars())
        assertEquals(
            mapOf("applied" to 1, "nonApplicable" to 2, "failed" to 0),
            PartialApplicability(appliedCount = 1, nonApplicableCount = 2).displayVars(),
        )
        assertEquals(
            mapOf("applied" to 0, "nonApplicable" to 0, "failed" to 1),
            PartialApplicability(failedCount = 1).displayVars(),
        )
    }

    @Test
    fun `normal execution shows ERROR only`() {
        assertTrue(ExecutionError(ErrorCode.COMMAND_FAILED).visibleInNormalExecution())
        assertFalse(ExecutionError(ErrorCode.TEMPORARY_UNRESOLVED).visibleInNormalExecution())
        assertFalse(ExecutionError(ErrorCode.TARGET_EMPTY).visibleInNormalExecution())
    }

    @Test
    fun `every code resolves to a generated localization key`() {
        // 原文フォールバック禁止のため、全コードが生成済みキーへ解決できることを固定します。
        ErrorCode.entries.forEach { code ->
            val key = code.displayKey()
            assertTrue(key.id.startsWith("kantan_commander_clean."), code.code)
        }
    }
}
