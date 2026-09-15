package me.awabi2048.kantancommander.hittest

import me.awabi2048.kantancommander.model.SpatialVector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * issue #23 の交差判定の数学的整合性を固定します。
 *
 * slab 法の正面 hit・miss・内側起点・平行・回転箱を検証します。
 * Bukkit 実体を使わず、純粋計算だけで検証します。
 */
class HitTestMathTest {
    private fun ray(origin: SpatialVector, direction: SpatialVector) =
        HitTestMath.HitRay(origin, direction)

    private fun unitBox(center: SpatialVector = SpatialVector(0.0, 0.0, 0.0)): HitTestMath.HitBox =
        HitTestMath.HitBox(
            center = center,
            axisX = SpatialVector(1.0, 0.0, 0.0),
            axisY = SpatialVector(0.0, 1.0, 0.0),
            axisZ = SpatialVector(0.0, 0.0, 1.0),
            halfExtents = SpatialVector(0.5, 0.5, 0.5),
        )

    @Test
    fun `frontal rays hit at the entry distance`() {
        val distance = HitTestMath.intersectRayBox(
            ray(SpatialVector(0.0, 0.0, -5.0), SpatialVector(0.0, 0.0, 1.0)),
            unitBox(),
            10.0,
        )

        assertNotNull(distance)
        assertEquals(4.5, distance!!, 1e-9)
    }

    @Test
    fun `offset rays miss the box`() {
        assertNull(
            HitTestMath.intersectRayBox(
                ray(SpatialVector(2.0, 0.0, -5.0), SpatialVector(0.0, 0.0, 1.0)),
                unitBox(),
                10.0,
            ),
        )
    }

    @Test
    fun `backward rays miss the box`() {
        assertNull(
            HitTestMath.intersectRayBox(
                ray(SpatialVector(0.0, 0.0, -5.0), SpatialVector(0.0, 0.0, -1.0)),
                unitBox(),
                10.0,
            ),
        )
    }

    @Test
    fun `rays starting inside report zero distance`() {
        val distance = HitTestMath.intersectRayBox(
            ray(SpatialVector(0.0, 0.0, 0.0), SpatialVector(0.0, 0.0, 1.0)),
            unitBox(),
            10.0,
        )

        assertNotNull(distance)
        assertEquals(0.0, distance!!, 1e-9)
    }

    @Test
    fun `parallel rays outside the slab miss`() {
        assertNull(
            HitTestMath.intersectRayBox(
                ray(SpatialVector(0.0, 2.0, 0.0), SpatialVector(1.0, 0.0, 0.0)),
                unitBox(),
                10.0,
            ),
        )
    }

    @Test
    fun `parallel rays inside the slab hit`() {
        val distance = HitTestMath.intersectRayBox(
            ray(SpatialVector(-5.0, 0.0, 0.0), SpatialVector(1.0, 0.0, 0.0)),
            unitBox(),
            10.0,
        )

        assertNotNull(distance)
        assertEquals(4.5, distance!!, 1e-9)
    }

    @Test
    fun `hits beyond the maximum distance are misses`() {
        assertNull(
            HitTestMath.intersectRayBox(
                ray(SpatialVector(0.0, 0.0, -5.0), SpatialVector(0.0, 0.0, 1.0)),
                unitBox(),
                4.0,
            ),
        )
    }

    @Test
    fun `rotated boxes intersect along their frame`() {
        // Y軸まわりに90度回転した細長い箱は、X方向のrayに当たる。
        val long = HitTestMath.HitBox(
            center = SpatialVector(0.0, 0.0, 0.0),
            axisX = SpatialVector(1.0, 0.0, 0.0),
            axisY = SpatialVector(0.0, 1.0, 0.0),
            axisZ = SpatialVector(0.0, 0.0, 1.0),
            halfExtents = SpatialVector(0.1, 0.1, 2.0),
        )
        val turned = HitTestMath.rotateBox(long, SpatialVector(0.0, 0.0, 0.0), Triple(0.0, 90.0, 0.0))
        val distance = HitTestMath.intersectRayBox(
            ray(SpatialVector(-5.0, 0.0, 0.0), SpatialVector(1.0, 0.0, 0.0)),
            turned,
            10.0,
        )

        assertNotNull(distance)
        assertEquals(3.0, distance!!, 1e-9)
    }

    @Test
    fun `aabb broad phase matches the box behavior`() {
        val bounds = HitTestMath.HitAabb(SpatialVector(-0.5, -0.5, -0.5), SpatialVector(0.5, 0.5, 0.5))
        val distance = HitTestMath.intersectRayAabb(
            ray(SpatialVector(0.0, 0.0, -5.0), SpatialVector(0.0, 0.0, 1.0)),
            bounds,
            10.0,
        )

        assertNotNull(distance)
        assertEquals(4.5, distance!!, 1e-9)
        assertNull(
            HitTestMath.intersectRayAabb(
                ray(SpatialVector(2.0, 0.0, -5.0), SpatialVector(0.0, 0.0, 1.0)),
                bounds,
                10.0,
            ),
        )
    }

    @Test
    fun `union aabb contains every rotated corner`() {
        val turned = HitTestMath.rotateBox(unitBox(), SpatialVector(0.0, 0.0, 0.0), Triple(0.0, 45.0, 0.0))
        val union = HitTestMath.unionAabb(listOf(turned))

        assertNotNull(union)
        // 45度回転した単位箱の対角は sqrt(0.5) ≒ 0.7071 まで広がる。
        assertEquals(-0.7071, union!!.minimum.x, 1e-4)
        assertEquals(0.7071, union.maximum.x, 1e-4)
        assertEquals(-0.5, union.minimum.y, 1e-9)
        assertEquals(0.5, union.maximum.y, 1e-9)
        assertNull(HitTestMath.unionAabb(emptyList()))
    }

    @Test
    fun `invalid inputs are misses rather than errors`() {
        assertNull(
            HitTestMath.intersectRayBox(
                ray(SpatialVector(0.0, 0.0, 0.0), SpatialVector(0.0, 0.0, 0.0)),
                unitBox(),
                10.0,
            ),
        )
        assertNull(
            HitTestMath.intersectRayBox(
                ray(SpatialVector(0.0, 0.0, -5.0), SpatialVector(0.0, 0.0, 1.0)),
                unitBox(),
                Double.NaN,
            ),
        )
    }
}
