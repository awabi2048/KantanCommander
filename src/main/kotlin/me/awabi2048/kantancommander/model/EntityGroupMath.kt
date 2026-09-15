package me.awabi2048.kantancommander.model

import kotlin.math.cos
import kotlin.math.sin

/**
 * issue #24 の Entity Group 変形の純粋計算です。
 *
 * Bukkit 実体を持たず、検証・実行・単体試験で同じ数学を使います。
 * 回転はワールド軸まわりの右手系で、X→Y→Z の順に適用します（外因性オイラー角）。
 * 拡大縮小は対象外であり、ここでも扱いません。
 */
object EntityGroupMath {
    /**
     * 移動軸の入力です。無印は絶対座標、`~` は現在 pivot からの相対差分です。
     *
     * #4 の Dialog 記法と同じ文法であり、ローカル（`^`）は移動基準が
     * 定義できないため拒否します。空欄・非有限値は不正です。
     */
    fun parseMoveAxis(raw: String): Pair<CoordinateSemantics, Double>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("^")) return null
        val relative = trimmed.startsWith("~")
        val number = trimmed.removePrefix("~").trim()
        val value = number.takeIf(String::isNotEmpty)?.toDoubleOrNull()
            ?: 0.0.takeIf { number.isEmpty() }
        if (value == null || !value.isFinite()) return null
        return (if (relative) CoordinateSemantics.RELATIVE else CoordinateSemantics.ABSOLUTE) to value
    }

    /**
     * 回転軸の入力です。無印は群の絶対姿勢、`~` は現在姿勢からの相対差分です。
     *
     * 角度にローカルは基準が定義できないため `^` は拒否します。
     * 空欄・非有限値は不正です。
     */
    fun parseRotateAxis(raw: String): Pair<CoordinateSemantics, Double>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("^")) return null
        val relative = trimmed.startsWith("~")
        val number = trimmed.removePrefix("~").trim()
        val value = number.takeIf(String::isNotEmpty)?.toDoubleOrNull()
            ?: 0.0.takeIf { number.isEmpty() }
        if (value == null || !value.isFinite()) return null
        return (if (relative) CoordinateSemantics.RELATIVE else CoordinateSemantics.ABSOLUTE) to value
    }

    /**
     * オフセットベクトルを pivot まわりで回転します。
     *
     * [eulerDegrees] は度数単位の (x, y, z) であり、X軸→Y軸→Z軸の順に
     * 適用します。呼び出し側は member 位置から pivot を引いた差分を渡し、
     * 戻り値に pivot を足して配置位置を求めます。
     */
    fun rotateOffset(offset: SpatialVector, eulerDegrees: Triple<Double, Double, Double>): SpatialVector {
        val (degreesX, degreesY, degreesZ) = eulerDegrees
        val x = Math.toRadians(degreesX)
        val y = Math.toRadians(degreesY)
        val z = Math.toRadians(degreesZ)
        val (cosX, sinX) = cos(x) to sin(x)
        val (cosY, sinY) = cos(y) to sin(y)
        val (cosZ, sinZ) = cos(z) to sin(z)
        // X軸まわり。
        val y1 = offset.y * cosX - offset.z * sinX
        val z1 = offset.y * sinX + offset.z * cosX
        // Y軸まわり。
        val x2 = offset.x * cosY + z1 * sinY
        val z2 = -offset.x * sinY + z1 * cosY
        // Z軸まわり。
        val x3 = x2 * cosZ - y1 * sinZ
        val y3 = x2 * sinZ + y1 * cosZ
        return SpatialVector(x3, y3, z2)
    }

    /** 群姿勢を [0, 360) 度へ正規化します。絶対・相対の合成後に適用します。 */
    fun normalizeDegrees(degrees: Double): Double {
        if (!degrees.isFinite()) return 0.0
        val normalized = degrees % 360.0
        return if (normalized < 0.0) normalized + 360.0 else normalized
    }

    /** 所属位置の中心を求めます。CREATE 時の pivot 既定値に使います。 */
    fun centroid(points: List<SpatialVector>): SpatialVector? {
        if (points.isEmpty()) return null
        var x = 0.0
        var y = 0.0
        var z = 0.0
        points.forEach { point ->
            x += point.x
            y += point.y
            z += point.z
        }
        val count = points.size.toDouble()
        return SpatialVector(x / count, y / count, z / count)
    }

    /**
     * orphan cleanup の保持判定です。解決不能な所属でも、最終既知チャンクが
     * 未読込・不明の場合は unload 中とみなして維持します。最終既知チャンクが
     * 読込済みで解決できない所属だけを削除対象とします。
     */
    fun shouldRetainUnresolved(lastKnownChunkLoaded: Boolean?): Boolean =
        lastKnownChunkLoaded != true
}
