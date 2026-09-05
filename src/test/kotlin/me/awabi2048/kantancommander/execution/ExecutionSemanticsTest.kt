package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExecutionSemanticsTest {
    @Test
    fun `internal execution context merge preserves unspecified fields`() {
        val inherited = ExecutionContextSpec(
            executor = TargetSpec(TargetKind.NEAREST_ENTITY),
            target = TargetSpec(TargetKind.NEAREST_PLAYER),
            position = PositionSpec(PositionKind.DISK),
            facing = FacingSpec(FacingKind.CAPTURED, yaw = 0f, pitch = 0f),
        )
        val override = ExecutionContextSpec(
            facing = FacingSpec(FacingKind.ROTATION, yaw = 90f, pitch = 10f),
        )

        val merged = requireNotNull(ExecutionSemantics.mergeContexts(inherited, override))
        assertEquals(inherited.executor, merged.executor)
        assertEquals(inherited.target, merged.target)
        assertEquals(inherited.position, merged.position)
        assertEquals(override.facing, merged.facing)
    }

    @Test
    fun `internal execution context merge lets the newer state override fields`() {
        val base = ExecutionContextSpec(position = PositionSpec(PositionKind.DISK))
        val previous = ExecutionContextSpec(
            position = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0),
            target = TargetSpec(TargetKind.NEAREST_PLAYER),
        )
        val merged = requireNotNull(ExecutionSemantics.mergeContexts(base, previous))
        assertEquals(previous.position, merged.position)
        assertEquals(previous.target, merged.target)
    }

    @Test
    fun `count loop advances only while the current count is below the limit`() {
        assertTrue(ExecutionSemantics.shouldRunNextLoopIteration(1, 3))
        assertFalse(ExecutionSemantics.shouldRunNextLoopIteration(3, 3))
        assertFalse(ExecutionSemantics.shouldRunNextLoopIteration(0, 3))
        assertFalse(ExecutionSemantics.shouldRunNextLoopIteration(1, 0))
    }

    @Test
    fun `condition inversion is applied exactly once`() {
        assertTrue(ExecutionSemantics.conditionResult(true, false))
        assertFalse(ExecutionSemantics.conditionResult(false, false))
        assertFalse(ExecutionSemantics.conditionResult(true, true))
        assertTrue(ExecutionSemantics.conditionResult(false, true))
    }

    @Test
    fun `budget and disk depth stop before the next unit`() {
        assertTrue(ExecutionSemantics.withinBudget(1023, 1024))
        assertFalse(ExecutionSemantics.withinBudget(1024, 1024))
        assertTrue(ExecutionSemantics.withinCallDepth(2, 3))
        assertFalse(ExecutionSemantics.withinCallDepth(3, 3))
    }

    @Test
    fun `loop count increment detects signed 64 bit overflow`() {
        assertEquals(2L, ExecutionSemantics.nextLoopCount(1))
        assertEquals(null, ExecutionSemantics.nextLoopCount(Long.MAX_VALUE))
    }
}
