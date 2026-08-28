package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.ContextSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExecutionSemanticsTest {
    @Test
    fun `partial node context overrides only configured fields`() {
        val inherited = ExecutionContextSpec(
            executor = TargetSpec(TargetKind.INHERITED_TARGET),
            target = TargetSpec(TargetKind.NEAREST_PLAYER),
            position = PositionSpec(PositionKind.DISK),
            facing = FacingSpec(FacingKind.INHERITED),
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
    fun `previous execute context is selected only when requested and node override stays strongest`() {
        val base = ExecutionContextSpec(position = PositionSpec(PositionKind.DISK))
        val previous = ExecutionContextSpec(
            position = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0),
            target = TargetSpec(TargetKind.NEAREST_PLAYER),
        )
        val override = ExecutionContextSpec(target = TargetSpec(TargetKind.ALL_PLAYERS))

        val inherited = requireNotNull(ExecutionSemantics.effectiveContext(base, previous, ContextSource.PREVIOUS, override))
        assertEquals(previous.position, inherited.position)
        assertEquals(override.target, inherited.target)
        assertEquals(base, ExecutionSemantics.effectiveContext(base, previous, ContextSource.BASE, null))
    }

    @Test
    fun `for range supports inclusive and exclusive ends in both directions`() {
        assertTrue(ExecutionSemantics.withinForRange(3, 3, 1, true))
        assertFalse(ExecutionSemantics.withinForRange(3, 3, 1, false))
        assertTrue(ExecutionSemantics.withinForRange(-2, -2, -1, true))
        assertFalse(ExecutionSemantics.withinForRange(-2, -2, -1, false))
        assertFalse(ExecutionSemantics.withinForRange(0, 10, 0, true))
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
    fun `for increment detects signed 64 bit overflow`() {
        assertEquals(3L, ExecutionSemantics.nextForValue(1, 2))
        assertEquals(null, ExecutionSemantics.nextForValue(Long.MAX_VALUE, 1))
        assertEquals(null, ExecutionSemantics.nextForValue(Long.MIN_VALUE, -1))
    }
}
