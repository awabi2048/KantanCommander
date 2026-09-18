package me.awabi2048.kantancommander.operationtool

import me.awabi2048.kantancommander.model.SpatialVector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OperationToolMenuTest {
    @Test
    fun `pivot is the centroid of bounding box centers`() {
        val pivot = OperationToolMenu.computePivot(
            listOf(
                SpatialVector(0.0, 0.0, 0.0),
                SpatialVector(2.0, 0.0, 0.0),
                SpatialVector(2.0, 3.0, 6.0),
            ),
        )
        assertEquals(SpatialVector(4.0 / 3.0, 1.0, 2.0), pivot)
    }

    @Test
    fun `pivot of a single entity is its own center`() {
        assertEquals(
            SpatialVector(1.0, 2.0, 3.0),
            OperationToolMenu.computePivot(listOf(SpatialVector(1.0, 2.0, 3.0))),
        )
    }

    @Test
    fun `pivot of no entities is null`() {
        assertNull(OperationToolMenu.computePivot(emptyList()))
    }
}
