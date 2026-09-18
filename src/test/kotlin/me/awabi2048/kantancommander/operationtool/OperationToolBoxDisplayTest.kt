package me.awabi2048.kantancommander.operationtool

import me.awabi2048.kantancommander.model.RegionMath
import me.awabi2048.kantancommander.model.SpatialVector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OperationToolBoxDisplayTest {
    @Test
    fun `box has twelve edges`() {
        val edges = OperationToolBoxDisplay.boxEdges(SpatialVector(0.0, 0.0, 0.0), SpatialVector(4.0, 3.0, 2.0))
        assertEquals(OperationToolBoxDisplay.EDGE_COUNT, edges.size)
        assertEquals(12, edges.size)
    }

    @Test
    fun `edge placement extends by half thickness on both ends`() {
        val (origin, transformation) = OperationToolBoxDisplay.edgePlacement(
            SpatialVector(0.0, 0.0, 0.0),
            SpatialVector(4.0, 0.0, 0.0),
        )
        val thickness = OperationToolBoxDisplay.EDGE_THICKNESS
        assertEquals(-thickness / 2.0, origin.x, 1e-9)
        assertEquals(-thickness / 2.0, origin.y, 1e-9)
        assertEquals(-thickness / 2.0, origin.z, 1e-9)
        assertEquals((4.0 + thickness).toFloat(), transformation.scale.x, 1e-6f)
        assertEquals(thickness.toFloat(), transformation.scale.y, 1e-6f)
        assertEquals(thickness.toFloat(), transformation.scale.z, 1e-6f)
    }

    @Test
    fun `degenerate edge becomes a corner cube`() {
        val (origin, transformation) = OperationToolBoxDisplay.edgePlacement(
            SpatialVector(1.0, 2.0, 3.0),
            SpatialVector(1.0, 2.0, 3.0),
        )
        val thickness = OperationToolBoxDisplay.EDGE_THICKNESS
        assertEquals(1.0 - thickness / 2.0, origin.x, 1e-9)
        assertEquals(2.0 - thickness / 2.0, origin.y, 1e-9)
        assertEquals(3.0 - thickness / 2.0, origin.z, 1e-9)
        assertEquals(thickness.toFloat(), transformation.scale.x, 1e-6f)
        assertEquals(thickness.toFloat(), transformation.scale.y, 1e-6f)
        assertEquals(thickness.toFloat(), transformation.scale.z, 1e-6f)
    }

    @Test
    fun `area display bounds include the block extent`() {
        val bounds = RegionMath.cuboidFromCorners(
            SpatialVector(1.0, 2.0, 3.0),
            SpatialVector(4.0, 5.0, 6.0),
        )
        val (minimum, maximum) = OperationToolSession.areaDisplayBounds(bounds)
        assertEquals(SpatialVector(1.0, 2.0, 3.0), minimum)
        assertEquals(SpatialVector(5.0, 6.0, 7.0), maximum)
    }
}
