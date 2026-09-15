package me.awabi2048.kantancommander.hittest

import me.awabi2048.kantancommander.model.SpatialVector

/**
 * issue #23 の Hit Test の純粋計算です。
 *
 * Bukkit 実体を持たず、検証・実行・単体試験で同じ交差判定を使います。
 * 回転は正規直交フレームで表し、オイラー角の合成順序問題を持ち込みません。
 * フレーム自体の回転には [me.awabi2048.kantancommander.model.EntityGroupMath] と
 * 同じ X→Y→Z 外因性オイラー順序を使い、バニラの ModelPart 適用順序と一致させます。
 */
object HitTestMath {
    /** 方向は正規化済みであることを要求します。非有限・零ベクトルは不正です。 */
    data class HitRay(val origin: SpatialVector, val direction: SpatialVector) {
        fun isValid(): Boolean =
            origin.isFinite() && direction.isFinite() &&
                direction.lengthSquared() > 1e-12
    }

    /**
     * 回転直方体（OBB）です。[axisX]／[axisY]／[axisZ] は正規直交フレームであり、
     * [halfExtents] は各軸方向の半寸法です。
     */
    data class HitBox(
        val center: SpatialVector,
        val axisX: SpatialVector,
        val axisY: SpatialVector,
        val axisZ: SpatialVector,
        val halfExtents: SpatialVector,
    ) {
        fun isValid(): Boolean =
            center.isFinite() && halfExtents.isFinite() &&
                halfExtents.x >= 0.0 && halfExtents.y >= 0.0 && halfExtents.z >= 0.0
    }

    /** 軸平行境界ボックス（broad phase 用）です。 */
    data class HitAabb(val minimum: SpatialVector, val maximum: SpatialVector) {
        fun isValid(): Boolean =
            minimum.isFinite() && maximum.isFinite() &&
                minimum.x <= maximum.x && minimum.y <= maximum.y && minimum.z <= maximum.z
    }

    /** 軸平行の箱を作ります。中心と全寸法から半寸法を求めます。 */
    fun aabbBox(minimum: SpatialVector, size: SpatialVector): HitBox = HitBox(
        center = SpatialVector(
            minimum.x + size.x / 2.0,
            minimum.y + size.y / 2.0,
            minimum.z + size.z / 2.0,
        ),
        axisX = SpatialVector(1.0, 0.0, 0.0),
        axisY = SpatialVector(0.0, 1.0, 0.0),
        axisZ = SpatialVector(0.0, 0.0, 1.0),
        halfExtents = SpatialVector(size.x / 2.0, size.y / 2.0, size.z / 2.0),
    )

    /**
     * 箱を pivot まわりで回転します。
     *
     * [eulerDegrees] は度数単位の (x, y, z) であり、X軸→Y軸→Z軸の順に
     * 適用します。中心の差分とフレーム軸の両方を回転するため、
     * 親ボーンの回転を子へ合成できます。
     */
    fun rotateBox(box: HitBox, pivot: SpatialVector, eulerDegrees: Triple<Double, Double, Double>): HitBox {
        fun rotate(offset: SpatialVector) =
            me.awabi2048.kantancommander.model.EntityGroupMath.rotateOffset(offset, eulerDegrees)
        val relative = SpatialVector(
            box.center.x - pivot.x,
            box.center.y - pivot.y,
            box.center.z - pivot.z,
        )
        val turned = rotate(relative)
        return box.copy(
            center = SpatialVector(pivot.x + turned.x, pivot.y + turned.y, pivot.z + turned.z),
            axisX = rotate(box.axisX),
            axisY = rotate(box.axisY),
            axisZ = rotate(box.axisZ),
        )
    }

    /** 箱全体を平行移動します。 */
    fun moveBox(box: HitBox, offset: SpatialVector): HitBox = box.copy(
        center = SpatialVector(
            box.center.x + offset.x,
            box.center.y + offset.y,
            box.center.z + offset.z,
        ),
    )

    /** 箱を原点基準で一様拡大縮小します。フレームは不変です。 */
    fun scaleBox(box: HitBox, scale: Double): HitBox = box.copy(
        center = SpatialVector(box.center.x * scale, box.center.y * scale, box.center.z * scale),
        halfExtents = SpatialVector(
            box.halfExtents.x * scale,
            box.halfExtents.y * scale,
            box.halfExtents.z * scale,
        ),
    )

    /**
     * ray と OBB の交差距離を求めます。
     *
     * slab 法を箱の局所座標で適用します。ray 原点が箱の内側にある場合は
     * 0.0 を返します。[maxDistance] を超える交差・後方の交差は null です。
     */
    fun intersectRayBox(ray: HitRay, box: HitBox, maxDistance: Double): Double? {
        if (!ray.isValid() || !box.isValid()) return null
        if (!maxDistance.isFinite() || maxDistance < 0.0) return null
        val relative = SpatialVector(
            ray.origin.x - box.center.x,
            ray.origin.y - box.center.y,
            ray.origin.z - box.center.z,
        )
        var nearest = 0.0
        var farthest = maxDistance
        data class AxisProjection(val origin: Double, val direction: Double, val half: Double)
        val axes = listOf(
            AxisProjection(relative.dot(box.axisX), ray.direction.dot(box.axisX), box.halfExtents.x),
            AxisProjection(relative.dot(box.axisY), ray.direction.dot(box.axisY), box.halfExtents.y),
            AxisProjection(relative.dot(box.axisZ), ray.direction.dot(box.axisZ), box.halfExtents.z),
        )
        for ((origin, direction, half) in axes) {
            if (kotlin.math.abs(direction) < 1e-12) {
                // ray が当該軸に平行な場合、範囲外なら交差しません。
                if (origin < -half || origin > half) return null
                continue
            }
            var first = (-half - origin) / direction
            var second = (half - origin) / direction
            if (first > second) {
                val swapped = first
                first = second
                second = swapped
            }
            if (first > nearest) nearest = first
            if (second < farthest) farthest = second
            if (nearest > farthest) return null
        }
        if (farthest < 0.0 || nearest > maxDistance) return null
        return nearest.coerceAtLeast(0.0)
    }

    /** ray と AABB の交差距離を求めます（broad phase 用）。 */
    fun intersectRayAabb(ray: HitRay, bounds: HitAabb, maxDistance: Double): Double? {
        if (!ray.isValid() || !bounds.isValid()) return null
        if (!maxDistance.isFinite() || maxDistance < 0.0) return null
        var nearest = 0.0
        var farthest = maxDistance
        val origins = listOf(ray.origin.x, ray.origin.y, ray.origin.z)
        val directions = listOf(ray.direction.x, ray.direction.y, ray.direction.z)
        val minimums = listOf(bounds.minimum.x, bounds.minimum.y, bounds.minimum.z)
        val maximums = listOf(bounds.maximum.x, bounds.maximum.y, bounds.maximum.z)
        for (index in 0..2) {
            val direction = directions[index]
            if (kotlin.math.abs(direction) < 1e-12) {
                if (origins[index] < minimums[index] || origins[index] > maximums[index]) return null
                continue
            }
            var first = (minimums[index] - origins[index]) / direction
            var second = (maximums[index] - origins[index]) / direction
            if (first > second) {
                val swapped = first
                first = second
                second = swapped
            }
            if (first > nearest) nearest = first
            if (second < farthest) farthest = second
            if (nearest > farthest) return null
        }
        if (farthest < 0.0 || nearest > maxDistance) return null
        return nearest.coerceAtLeast(0.0)
    }

    /** 複数の箱を包含する最小の AABB を求めます（broad phase 用）。 */
    fun unionAabb(boxes: List<HitBox>): HitAabb? {
        if (boxes.isEmpty()) return null
        var minimum = SpatialVector(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
        var maximum = SpatialVector(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
        boxes.forEach { box ->
            cornersOf(box).forEach { corner ->
                minimum = SpatialVector(
                    kotlin.math.min(minimum.x, corner.x),
                    kotlin.math.min(minimum.y, corner.y),
                    kotlin.math.min(minimum.z, corner.z),
                )
                maximum = SpatialVector(
                    kotlin.math.max(maximum.x, corner.x),
                    kotlin.math.max(maximum.y, corner.y),
                    kotlin.math.max(maximum.z, corner.z),
                )
            }
        }
        return HitAabb(minimum, maximum).takeIf { it.isValid() }
    }

    /** 箱の8頂点を求めます。 */
    fun cornersOf(box: HitBox): List<SpatialVector> {
        val signs = listOf(-1.0, 1.0)
        return buildList {
            for (sx in signs) {
                for (sy in signs) {
                    for (sz in signs) {
                        add(
                            SpatialVector(
                                box.center.x + box.axisX.x * box.halfExtents.x * sx +
                                    box.axisY.x * box.halfExtents.y * sy +
                                    box.axisZ.x * box.halfExtents.z * sz,
                                box.center.y + box.axisX.y * box.halfExtents.x * sx +
                                    box.axisY.y * box.halfExtents.y * sy +
                                    box.axisZ.y * box.halfExtents.z * sz,
                                box.center.z + box.axisX.z * box.halfExtents.x * sx +
                                    box.axisY.z * box.halfExtents.y * sy +
                                    box.axisZ.z * box.halfExtents.z * sz,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun SpatialVector.dot(other: SpatialVector): Double =
        x * other.x + y * other.y + z * other.z
}
