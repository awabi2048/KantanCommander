package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.error.ErrorCode
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ExecutionErrorWiringTest {
    @Test
    fun `node finished derives error code from legacy reason`() {
        val event = ExecutionNodeFinished(
            scriptId = UUID.randomUUID(),
            nodeId = UUID.randomUUID(),
            nodeType = CommandType.TELEPORT,
            depth = 0,
            attemptNumber = 0,
            outcome = NodeExecutionOutcome.FAILED,
            successfulNodeCount = 0,
            nextNodeId = null,
            tick = 0L,
            reason = "invalid_wait_seconds",
        )
        assertEquals(ErrorCode.INVALID_WAIT_SECONDS, event.errorCode)
    }

    @Test
    fun `skipped temporary and quota codes are distinguishable from failure`() {
        assertEquals(ErrorCode.TEMPORARY_UNRESOLVED, ErrorCode.fromLegacyReason(ErrorCode.TEMPORARY_UNRESOLVED.code))
        assertEquals(ErrorCode.PARTICLE_QUOTA_EXCEEDED, ErrorCode.fromLegacyReason(ErrorCode.PARTICLE_QUOTA_EXCEEDED.code))
        // SKIPPED 系は WARNING、通常失敗は ERROR で表示先が分かれます。
        assertEquals(me.awabi2048.kantancommander.error.ErrorSeverity.WARNING, ErrorCode.TEMPORARY_UNRESOLVED.defaultSeverity)
        assertEquals(me.awabi2048.kantancommander.error.ErrorSeverity.ERROR, ErrorCode.COMMAND_FAILED.defaultSeverity)
    }

    @Test
    fun `condition unresolved and invalid map to return error policy`() {
        assertEquals(
            me.awabi2048.kantancommander.error.FailurePolicy.RETURN_ERROR,
            ErrorCode.CONDITION_UNRESOLVED.defaultPolicy,
        )
        assertEquals(
            me.awabi2048.kantancommander.error.FailurePolicy.RETURN_ERROR,
            ErrorCode.CONDITION_INVALID_TYPE.defaultPolicy,
        )
    }

    @Test
    fun `unknown reason leaves error code null for explicit handling`() {
        val event = ExecutionNodeFinished(
            scriptId = UUID.randomUUID(),
            nodeId = UUID.randomUUID(),
            nodeType = CommandType.WAIT,
            depth = 0,
            attemptNumber = 0,
            outcome = NodeExecutionOutcome.FAILED,
            successfulNodeCount = 0,
            nextNodeId = null,
            tick = 0L,
            reason = "totally_unknown_reason_xyz",
        )
        assertNull(event.errorCode)
    }

    @Test
    fun `execution result derives invalid script code`() {
        val result = ExecutionResult(
            status = ExecutionFinishStatus.FAILURE,
            elapsedTicks = 0L,
            successfulNodeCount = 0,
            attemptCount = 0,
            reason = "invalid_script: root: xxx",
        )
        assertEquals(ErrorCode.INVALID_SCRIPT, result.errorCode)
    }
}
