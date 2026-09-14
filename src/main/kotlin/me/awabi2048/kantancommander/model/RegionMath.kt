package me.awabi2048.kantancommander.model

import org.bukkit.Location

/**
 * issue #4 の領域解決の結果です。
 *
 * 正常な不一致（範囲外）と以下を区別します。world を跨ぐ比較は呼び出し側で
 * 明示的に失敗させ、黙って不一致にしません。Target絞り込み・Condition判定・
 * 出力・将来の Structure／Group で同じ型を使い、領域判定の分岐を増やしません。
 */
sealed interface RegionResolution {
    /** 解決済みの領域です。原点のワールド名と範囲を持ちます。 */
    data class Resolved(
        val worldName: String,
        val origin: SpatialVector,
        val dx: Double?,
        val dy: Double?,
        val dz: Double?,
        val minimumDistance: Double?,
        val maximumDistance: Double?,
    ) : RegionResolution

    /** 基準（anchor）が未解決です。固定位置・変数・対象のいずれかが確定しません。 */
    data object UnresolvedAnchor : RegionResolution

    /** 基準と実行ワールドが異なります。比較せず明示失敗にします。 */
    data object WorldMismatch : RegionResolution

    /** 形状・範囲・距離の指定が不正です。検証層で事前に検出します。 */
    data object InvalidSpec : RegionResolution
}

/**
 * issue #4 の領域判定の純粋計算です。
 *
 * Bukkit実体を持たず、Target絞り込み・Condition判定・出力・単体試験で
 * 同じ containment を使います。直方体はバニラセレクターと同じ基準ブロックを
 * 含む判定であり、従来の dx/dy/dz と同じ結果になります。
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

    /**
     * 解決の妥当性とワールド一致を判定します。
     *
     * anchor解決（実体参照）は呼び出し側（実行層）が行い、ここでは幾何と
     * ワールドの契約だけを判定します。SPHERE／CYLINDER は予約のため不正とします。
     */
    fun resolve(
        kind: RegionKind,
        anchorWorldName: String?,
        queryWorldName: String,
        origin: SpatialVector?,
        dx: Double?,
        dy: Double?,
        dz: Double?,
        minimumDistance: Double?,
        maximumDistance: Double?,
    ): RegionResolution {
        if (kind != RegionKind.CUBOID) return RegionResolution.InvalidSpec
        if (origin == null || anchorWorldName == null) return RegionResolution.UnresolvedAnchor
        if (anchorWorldName != queryWorldName) return RegionResolution.WorldMismatch
        if (!isValidExtent(dx) || !isValidExtent(dy) || !isValidExtent(dz)) {
            return RegionResolution.InvalidSpec
        }
        if (!isValidDistance(minimumDistance) || !isValidDistance(maximumDistance)) {
            return RegionResolution.InvalidSpec
        }
        if (minimumDistance != null && maximumDistance != null && minimumDistance > maximumDistance) {
            return RegionResolution.InvalidSpec
        }
        return RegionResolution.Resolved(
            worldName = anchorWorldName,
            origin = origin,
            dx = dx,
            dy = dy,
            dz = dz,
            minimumDistance = minimumDistance,
            maximumDistance = maximumDistance,
        )
    }

    private fun isValidExtent(value: Double?): Boolean =
        value == null || (value.isFinite() && value >= 0.0)

    private fun isValidDistance(value: Double?): Boolean =
        value == null || (value.isFinite() && value >= 0.0)
}

/** Bukkit位置を純粋計算のベクトルへ写します。ワールドは呼び出し側で扱います。 */
fun Location.toSpatialVector(): SpatialVector = SpatialVector(x, y, z)
