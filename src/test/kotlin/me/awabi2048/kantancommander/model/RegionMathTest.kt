package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 領域判定テストです。
 *
 * Target絞り込みのセレクター直方体・操作ツールの範囲選択で共有する
 * 幾何契約を固定します。Bukkit実体を必要とせず、サーバーなしで実行できます。
 */
class RegionMathTest {
    private val base = SpatialVector(10.0, 64.0, -5.0)

    @Test
    fun `contains follows the vanilla floor block box`() {
        // 基準ブロック（10, 64, -5）を含みます。
        assertTrue(RegionMath.contains(SpatialVector(10.2, 64.9, -4.1), base, 0.0, 0.0, 0.0))
        // 範囲外は含みません。
        assertFalse(RegionMath.contains(SpatialVector(11.0, 64.0, -5.0), base, 0.0, 0.0, 0.0))
        // 未指定の軸は制限しません。
        assertTrue(RegionMath.contains(SpatialVector(10.0, 64.0, -5.0), base, null, null, null))
        // 未指定の軸は制限しません。
        assertTrue(RegionMath.contains(SpatialVector(999.0, 64.0, -5.0), base, null, 0.0, 0.0))
        // dx=1 は基準ブロックと +1 ブロックを含みます（従来の dx/dy/dz と同じ意味）。
        assertTrue(RegionMath.contains(SpatialVector(11.9, 64.0, -5.0), base, 1.0, 0.0, 0.0))
        assertFalse(RegionMath.contains(SpatialVector(12.0, 64.0, -5.0), base, 1.0, 0.0, 0.0))
    }

    @Test
    fun `corners normalize to the same cuboid in any order`() {
        val forward = RegionMath.cuboidFromCorners(
            SpatialVector(10.2, 64.0, -5.0),
            SpatialVector(12.7, 66.0, -3.0),
        )
        val reversed = RegionMath.cuboidFromCorners(
            SpatialVector(12.7, 66.0, -3.0),
            SpatialVector(10.2, 64.0, -5.0),
        )
        assertEquals(forward, reversed)
        assertEquals(SpatialVector(10.2, 64.0, -5.0), forward.minimum)
        assertEquals(2.0, forward.dx)
        assertEquals(2.0, forward.dy)
        assertEquals(2.0, forward.dz)
        // 正規化した範囲は両端のブロックを含みます。
        val origin = SpatialVector(10.0, 64.0, -5.0)
        assertTrue(RegionMath.contains(SpatialVector(12.9, 66.9, -3.1), origin, forward.dx, forward.dy, forward.dz))
        assertFalse(RegionMath.contains(SpatialVector(13.0, 64.0, -5.0), origin, forward.dx, forward.dy, forward.dz))
    }

    @Test
    fun `selector cuboid treats unspecified axes as unbounded`() {
        val origin = SpatialVector(10.0, 64.0, -5.0)
        val boxMin = SpatialVector(11.5, 64.0, -4.8)
        val boxMax = SpatialVector(12.0, 65.8, -4.1)
        // 全軸未指定は常に交差します。
        assertTrue(RegionMath.overlapsSelectorCuboid(origin, null, null, null, boxMin, boxMax))
        // x軸だけ指定した場合、他軸は無制限です。
        assertTrue(RegionMath.overlapsSelectorCuboid(origin, 2.0, null, null, boxMin, boxMax))
        assertFalse(RegionMath.overlapsSelectorCuboid(origin, -20.0, null, null, boxMin, boxMax))
    }

    @Test
    fun `selector cuboid uses bounding box overlap like vanilla`() {
        // extent=0 でも幅1ブロックの箱になります（バニラの +1.0）。
        assertTrue(RegionMath.overlapsSelectorAxis(10.0, 0.0, 10.5, 11.0))
        assertFalse(RegionMath.overlapsSelectorAxis(10.0, 0.0, 11.5, 12.0))
        // 辺が接するだけでは交差しません。
        assertFalse(RegionMath.overlapsSelectorAxis(10.0, 1.0, 12.0, 12.5))
        assertTrue(RegionMath.overlapsSelectorAxis(10.0, 1.0, 11.9, 12.5))
    }

    @Test
    fun `negative extents stretch the cuboid to the negative direction`() {
        // dx=-2 は [origin-2, origin+1] の箱です（正方向の末端は常に +1.0）。
        val (minimum, maximum) = RegionMath.selectorAxisRange(10.0, -2.0)
        assertEquals(8.0, minimum)
        assertEquals(11.0, maximum)
        assertTrue(RegionMath.overlapsSelectorAxis(10.0, -2.0, 8.5, 9.0))
        assertFalse(RegionMath.overlapsSelectorAxis(10.0, -2.0, 11.5, 12.0))
    }
}
