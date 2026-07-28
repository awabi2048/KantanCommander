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
    fun `partial node context overrides only configured fields`() {
        val inherited = ExecutionContextSpec(
            executor = TargetSpec(TargetKind.ACTIVATOR),
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
    fun `for range supports inclusive and exclusive ends in both directions`() {
        assertTrue(ExecutionSemantics.withinForRange(3, 3, 1, true))
        assertFalse(ExecutionSemantics.withinForRange(3, 3, 1, false))
        assertTrue(ExecutionSemantics.withinForRange(-2, -2, -1, true))
        assertFalse(ExecutionSemantics.withinForRange(-2, -2, -1, false))
        assertFalse(ExecutionSemantics.withinForRange(0, 10, 0, true))
    }
}
