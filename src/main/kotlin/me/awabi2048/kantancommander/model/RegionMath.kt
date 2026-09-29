package me.awabi2048.kantancommander.model

import org.bukkit.Location

/**
 * 領域の純粋計算です。
 *
 * Bukkit実体を持たず、対象絞り込みの直方体判定・操作ツールの範囲選択・
 * 単体試験で同じ幾何を使います。
 */
object RegionMath {
    /**
     * 直方体の軸方向の包含を判定します。
     *
     * 基準値の床（ブロック境界）から `extent + 1.0` 未満を含みます。
     * `extent` が null の軸は制限しません。非有限・負の範囲は呼び出し側で
     * 事前に除外し、ここでは純粋な幾何判定だけを行います。
     */
    fun containsAxis(value: Double, origin: Double, extent: Double?): Boolean {
        if (extent == null) return true
        val minimum = kotlin.math.floor(origin)
        return value >= minimum && value < minimum + extent + 1.0
    }

    /** 3軸すべての包含を判定します。 */
    fun contains(
        point: SpatialVector,
        origin: SpatialVector,
        dx: Double?,
        dy: Double?,
        dz: Double?,
    ): Boolean =
        containsAxis(point.x, origin.x, dx) &&
            containsAxis(point.y, origin.y, dy) &&
            containsAxis(point.z, origin.z, dz)

    /**
     * バニラセレクターの直方体における1軸の下端・上端を返します。
     *
     * `extent` が負のときは反対方向へ伸び、正方向の末端は常に `+1.0`
     * だけ広がります（extent=0 でも幅1ブロック）。基準位置は床処理しません。
     */
    fun selectorAxisRange(origin: Double, extent: Double): Pair<Double, Double> =
        if (extent < 0.0) (origin + extent) to (origin + 1.0) else origin to (origin + extent + 1.0)

    /**
     * 実体のヒットボックス1軸がバニラセレクター直方体と交差するかを判定します。
     *
     * `extent` が null の軸は判定しません。辺が接するだけでは交差しません。
     */
    fun overlapsSelectorAxis(
        origin: Double,
        extent: Double?,
        boxMinimum: Double,
        boxMaximum: Double,
    ): Boolean {
        if (extent == null) return true
        val (minimum, maximum) = selectorAxisRange(origin, extent)
        return boxMaximum > minimum && boxMinimum < maximum
    }

    /** 実体のヒットボックスがバニラセレクター直方体と3軸とも交差するかを判定します。 */
    fun overlapsSelectorCuboid(
        origin: SpatialVector,
        dx: Double?,
        dy: Double?,
        dz: Double?,
        boxMinimum: SpatialVector,
        boxMaximum: SpatialVector,
    ): Boolean =
        overlapsSelectorAxis(origin.x, dx, boxMinimum.x, boxMaximum.x) &&
            overlapsSelectorAxis(origin.y, dy, boxMinimum.y, boxMaximum.y) &&
            overlapsSelectorAxis(origin.z, dz, boxMinimum.z, boxMaximum.z)

    /** 2点指定の正規化結果です。基準（各軸の最小値）と範囲を持ちます。 */
    data class CuboidBounds(
        val minimum: SpatialVector,
        val dx: Double,
        val dy: Double,
        val dz: Double,
    )

    /**
     * 2点指定を基準（各軸の最小値）と範囲へ正規化します。
     *
     * 逆順の2点も同じ領域になります。containsAxis の基準ブロック含み（+1.0）と
     * 逆演算し、両端のブロックを含む範囲にします。床が一致する2点では
     * 範囲 0（当該ブロックだけ）になります。
     */
    fun cuboidFromCorners(a: SpatialVector, b: SpatialVector): CuboidBounds {
        fun extent(minimum: Double, maximum: Double): Double =
            (kotlin.math.floor(maximum) - kotlin.math.floor(minimum)).coerceAtLeast(0.0)
        val minimum = SpatialVector(
            kotlin.math.min(a.x, b.x),
            kotlin.math.min(a.y, b.y),
            kotlin.math.min(a.z, b.z),
        )
        val maximum = SpatialVector(
            kotlin.math.max(a.x, b.x),
            kotlin.math.max(a.y, b.y),
            kotlin.math.max(a.z, b.z),
        )
        return CuboidBounds(
            minimum = minimum,
            dx = extent(minimum.x, maximum.x),
            dy = extent(minimum.y, maximum.y),
            dz = extent(minimum.z, maximum.z),
        )
    }
}

/** Bukkit位置を純粋計算のベクトルへ写します。ワールドは呼び出し側で扱います。 */
fun Location.toSpatialVector(): SpatialVector = SpatialVector(x, y, z)
