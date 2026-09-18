package me.awabi2048.kantancommander.operationtool

import me.awabi2048.kantancommander.model.SpatialVector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperationToolListenerMathTest {
    @Test
    fun `adjacent slot moves and wraparound are scroll steps`() {
        assertTrue(OperationToolListener.isScrollStep(3, 4))
        assertTrue(OperationToolListener.isScrollStep(4, 3))
        assertTrue(OperationToolListener.isScrollStep(0, 8))
        assertTrue(OperationToolListener.isScrollStep(8, 0))
    }

    @Test
    fun `number key jumps and same slots are not scroll steps`() {
        assertFalse(OperationToolListener.isScrollStep(0, 5))
        assertFalse(OperationToolListener.isScrollStep(2, 7))
        assertFalse(OperationToolListener.isScrollStep(4, 4))
    }

    @Test
    fun `ray hitting aabb returns the nearest distance`() {
        val distance = OperationToolListener.rayAabbDistance(
            origin = SpatialVector(0.0, 0.0, 0.0),
            direction = SpatialVector(1.0, 0.0, 0.0),
            minimum = SpatialVector(5.0, -1.0, -1.0),
            maximum = SpatialVector(6.0, 1.0, 1.0),
        )
        assertEquals(5.0, distance)
    }

    @Test
    fun `ray missing aabb returns null`() {
        assertNull(
            OperationToolListener.rayAabbDistance(
                origin = SpatialVector(0.0, 0.0, 0.0),
                direction = SpatialVector(1.0, 0.0, 0.0),
                minimum = SpatialVector(5.0, 2.0, -1.0),
                maximum = SpatialVector(6.0, 3.0, 1.0),
            ),
        )
    }

    @Test
    fun `ray starting inside aabb returns zero`() {
        assertEquals(
            0.0,
            OperationToolListener.rayAabbDistance(
                origin = SpatialVector(5.5, 0.0, 0.0),
                direction = SpatialVector(1.0, 0.0, 0.0),
                minimum = SpatialVector(5.0, -1.0, -1.0),
                maximum = SpatialVector(6.0, 1.0, 1.0),
            ),
        )
    }

    @Test
    fun `parallel ray outside the slab returns null`() {
        assertNull(
            OperationToolListener.rayAabbDistance(
                origin = SpatialVector(0.0, 5.0, 0.0),
                direction = SpatialVector(1.0, 0.0, 0.0),
                minimum = SpatialVector(5.0, -1.0, -1.0),
                maximum = SpatialVector(6.0, 1.0, 1.0),
            ),
        )
    }
}
