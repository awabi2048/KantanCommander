package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParticleGeometryTest {
    @Test
    fun `density accepts the agreed range`() {
        assertTrue(ParticleGeometry.isDensityValue("0.5"))
        assertTrue(ParticleGeometry.isDensityValue("2.0"))
        assertTrue(ParticleGeometry.isDensityValue("0.000244140625"))
        assertFalse(ParticleGeometry.isDensityValue("0.0"))
        assertFalse(ParticleGeometry.isDensityValue("2.1"))
        assertFalse(ParticleGeometry.isDensityValue("abc"))
    }

    @Test
    fun `circle estimate scales with circumference and density`() {
        val params = mapOf(ParticleGeometry.PARAM_RADIUS to "3.0")
        val estimate = ParticleGeometry.estimatePoints(ParticleShape.CIRCLE, params, 0.5, null)
        // 2π×3×0.5 ≈ 9.42 → 10点
        assertEquals(10, estimate)
    }

    @Test
    fun `sphere estimate scales with surface area`() {
        val params = mapOf(ParticleGeometry.PARAM_RADIUS to "3.0")
        val estimate = ParticleGeometry.estimatePoints(ParticleShape.SPHERE, params, 0.5, null)
        // 4π×9×0.5 ≈ 56.5 → 57点
        assertEquals(57, estimate)
    }

    @Test
    fun `line estimate follows the end distance`() {
        val estimate = ParticleGeometry.estimatePoints(ParticleShape.LINE, emptyMap(), 1.0, 10.0)
        assertEquals(10, estimate)
    }

    @Test
    fun `arc estimate follows the sweep angle`() {
        val params = mapOf(
            ParticleGeometry.PARAM_RADIUS to "4.0",
            ParticleGeometry.PARAM_START_ANGLE to "0.0",
            ParticleGeometry.PARAM_END_ANGLE to "90.0",
        )
        val estimate = ParticleGeometry.estimatePoints(ParticleShape.ARC, params, 1.0, null)
        // 2π×4×0.25 ≈ 6.28 → 7点
        assertEquals(7, estimate)
    }

    @Test
    fun `unresolvable inputs defer estimation`() {
        assertNull(ParticleGeometry.estimatePoints(ParticleShape.CIRCLE, emptyMap(), 0.5, null))
        assertNull(ParticleGeometry.estimatePoints(ParticleShape.LINE, emptyMap(), 0.5, null))
        assertNull(ParticleGeometry.estimatePoints(ParticleShape.SPHERE, mapOf(ParticleGeometry.PARAM_RADIUS to "3.0"), 0.0, null))
    }

    @Test
    fun `line generation connects both ends`() {
        val resolved = ParticleGeometry.ResolvedGeometry(step = 1.0, endOffset = Triple(3.0, 0.0, 0.0))
        val points = ParticleGeometry.generate(ParticleShape.LINE, resolved)
        assertEquals(4, points.size)
        assertEquals(Triple(0.0, 0.0, 0.0), points.first())
        assertEquals(Triple(3.0, 0.0, 0.0), points.last())
    }

    @Test
    fun `circle generation stays on the radius`() {
        val resolved = ParticleGeometry.ResolvedGeometry(radius = 2.0, step = 0.5)
        val points = ParticleGeometry.generate(ParticleShape.CIRCLE, resolved)
        assertTrue(points.size >= 8)
        points.forEach { (x, y, z) ->
            val distance = kotlin.math.sqrt(x * x + y * y + z * z)
            assertTrue(kotlin.math.abs(distance - 2.0) < 0.01, "radius=$distance")
        }
    }

    @Test
    fun `sphere generation distributes points around the center`() {
        val resolved = ParticleGeometry.ResolvedGeometry(radius = 2.0, step = 1.0)
        val points = ParticleGeometry.generate(ParticleShape.SPHERE, resolved)
        assertTrue(points.isNotEmpty())
        val maxAxis = points.maxOf { kotlin.math.abs(it.first) + kotlin.math.abs(it.second) + kotlin.math.abs(it.third) }
        assertTrue(maxAxis <= 6.01)
    }

    @Test
    fun `helix generation rises along the axis`() {
        val resolved = ParticleGeometry.ResolvedGeometry(radius = 1.0, height = 4.0, turns = 2.0, step = 0.25)
        val points = ParticleGeometry.generate(ParticleShape.HELIX, resolved)
        assertTrue(points.size >= 16)
        assertEquals(0.0, points.first().second, 0.01)
        assertEquals(4.0, points.last().second, 0.01)
    }

    @Test
    fun `box wireframe generates edges only`() {
        val resolved = ParticleGeometry.ResolvedGeometry(step = 1.0, endOffset = Triple(2.0, 2.0, 2.0), wireframe = true)
        val points = ParticleGeometry.generate(ParticleShape.BOX, resolved)
        assertTrue(points.isNotEmpty())
        // 辺上の点だけであり、面の内側（1,1,0.5等）は含まない
        assertFalse(points.any { it.first == 1.0 && it.second == 1.0 })
    }

    @Test
    fun `shape and display mode parse with legacy fallback`() {
        assertEquals(ParticleShape.LINE, ParticleShape.parse("LINE"))
        assertEquals(ParticleShape.POINT_SCATTER, ParticleShape.parse("unknown"))
        assertEquals(ParticleShape.POINT_SCATTER, ParticleShape.parse(null))
        assertEquals(ParticleDisplayMode.GEOMETRY, ParticleDisplayMode.parse("GEOMETRY"))
        assertEquals(ParticleDisplayMode.POINT, ParticleDisplayMode.parse(null))
        assertNotNull(SoundOperation.parse("STOP"))
        assertEquals(SoundOperation.PLAY, SoundOperation.parse(null))
    }
}
