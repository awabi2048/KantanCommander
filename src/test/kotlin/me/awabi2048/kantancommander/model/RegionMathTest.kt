package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #4 の領域判定テストです。
 *
 * Target絞り込み・Condition判定・出力で共有する containment と解決契約を
 * 固定します。Bukkit実体を必要とせず、サーバーなしで実行できます。
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
    fun `resolve distinguishes mismatch invalid and unresolved`() {
        val origin = SpatialVector(0.0, 64.0, 0.0)
        val resolved = RegionMath.resolve(
            RegionKind.CUBOID, "world", "world", origin, 1.0, 1.0, 1.0, null, null,
        )
        assertTrue(resolved is RegionResolution.Resolved)

        // world不一致は明示理由で返します。
        assertEquals(
            RegionResolution.WorldMismatch,
            RegionMath.resolve(RegionKind.CUBOID, "other", "world", origin, 1.0, 1.0, 1.0, null, null),
        )
        // 基準未解決は区別します。
        assertEquals(
            RegionResolution.UnresolvedAnchor,
            RegionMath.resolve(RegionKind.CUBOID, "world", "world", null, 1.0, 1.0, 1.0, null, null),
        )
        assertEquals(
            RegionResolution.UnresolvedAnchor,
            RegionMath.resolve(RegionKind.CUBOID, null, "world", origin, 1.0, 1.0, 1.0, null, null),
        )
        // 不正指定は検証層と同じ契約で拒否します。
        assertEquals(
            RegionResolution.InvalidSpec,
            RegionMath.resolve(RegionKind.SPHERE, "world", "world", origin, 1.0, 1.0, 1.0, null, null),
        )
        assertEquals(
            RegionResolution.InvalidSpec,
            RegionMath.resolve(RegionKind.CUBOID, "world", "world", origin, -1.0, 1.0, 1.0, null, null),
        )
        assertEquals(
            RegionResolution.InvalidSpec,
            RegionMath.resolve(RegionKind.CUBOID, "world", "world", origin, 1.0, 1.0, 1.0, 5.0, 2.0),
        )
    }
}
