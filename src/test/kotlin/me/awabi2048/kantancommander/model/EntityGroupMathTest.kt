package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #24 の群変形の数学的整合性を固定します。
 *
 * 回転順序は X→Y→Z の固定であり、絶対・`~` 相対の文法は検証・実行と
 * 同じ [EntityGroupMath] を使います。unload 中の所属維持と消失判定の
 * 境界もここで固定します。
 */
class EntityGroupMathTest {
    @Test
    fun `move axes accept absolute and tilde-relative inputs`() {
        assertEquals(
            CoordinateSemantics.ABSOLUTE to 100.0,
            EntityGroupMath.parseMoveAxis("100"),
        )
        assertEquals(
            CoordinateSemantics.RELATIVE to 5.0,
            EntityGroupMath.parseMoveAxis("~5"),
        )
        assertEquals(
            CoordinateSemantics.RELATIVE to -2.0,
            EntityGroupMath.parseMoveAxis("~-2"),
        )
        assertEquals(
            CoordinateSemantics.RELATIVE to 0.0,
            EntityGroupMath.parseMoveAxis("~"),
        )
        assertNull(EntityGroupMath.parseMoveAxis(""))
        assertNull(EntityGroupMath.parseMoveAxis("^5"))
        assertNull(EntityGroupMath.parseMoveAxis("north"))
    }

    @Test
    fun `rotate axes accept absolute and tilde-relative angles`() {
        assertEquals(
            CoordinateSemantics.ABSOLUTE to 90.0,
            EntityGroupMath.parseRotateAxis("90"),
        )
        assertEquals(
            CoordinateSemantics.RELATIVE to 45.0,
            EntityGroupMath.parseRotateAxis("~45"),
        )
        assertEquals(
            CoordinateSemantics.RELATIVE to 0.0,
            EntityGroupMath.parseRotateAxis("~"),
        )
        assertNull(EntityGroupMath.parseRotateAxis(""))
        assertNull(EntityGroupMath.parseRotateAxis("^45"))
        assertNull(EntityGroupMath.parseRotateAxis("fast"))
    }

    @Test
    fun `yaw rotation turns the offset around the pivot`() {
        val turned = EntityGroupMath.rotateOffset(SpatialVector(1.0, 0.0, 0.0), Triple(0.0, 90.0, 0.0))

        assertEquals(0.0, turned.x, 1e-9)
        assertEquals(0.0, turned.y, 1e-9)
        assertEquals(-1.0, turned.z, 1e-9)
    }

    @Test
    fun `rotation order is fixed as x then y then z`() {
        val sequential = EntityGroupMath.rotateOffset(
            EntityGroupMath.rotateOffset(
                EntityGroupMath.rotateOffset(SpatialVector(1.0, 2.0, 3.0), Triple(30.0, 0.0, 0.0)),
                Triple(0.0, 45.0, 0.0),
            ),
            Triple(0.0, 0.0, 60.0),
        )
        val combined = EntityGroupMath.rotateOffset(SpatialVector(1.0, 2.0, 3.0), Triple(30.0, 45.0, 60.0))

        assertEquals(sequential.x, combined.x, 1e-9)
        assertEquals(sequential.y, combined.y, 1e-9)
        assertEquals(sequential.z, combined.z, 1e-9)
    }

    @Test
    fun `orientation normalizes into a full circle`() {
        assertEquals(10.0, EntityGroupMath.normalizeDegrees(370.0), 1e-9)
        assertEquals(350.0, EntityGroupMath.normalizeDegrees(-10.0), 1e-9)
        assertEquals(0.0, EntityGroupMath.normalizeDegrees(360.0), 1e-9)
    }

    @Test
    fun `centroid averages member positions`() {
        val centroid = EntityGroupMath.centroid(
            listOf(SpatialVector(0.0, 0.0, 0.0), SpatialVector(2.0, 4.0, 6.0)),
        )

        assertNotNull(centroid)
        assertEquals(1.0, centroid!!.x, 1e-9)
        assertEquals(2.0, centroid.y, 1e-9)
        assertEquals(3.0, centroid.z, 1e-9)
        assertNull(EntityGroupMath.centroid(emptyList()))
    }

    @Test
    fun `unresolved members are retained while their chunk state is unknown`() {
        assertTrue(EntityGroupMath.shouldRetainUnresolved(null))
        assertTrue(EntityGroupMath.shouldRetainUnresolved(false))
    }

    @Test
    fun `unresolved members in loaded chunks are cleanup targets`() {
        assertEquals(false, EntityGroupMath.shouldRetainUnresolved(true))
    }
}
