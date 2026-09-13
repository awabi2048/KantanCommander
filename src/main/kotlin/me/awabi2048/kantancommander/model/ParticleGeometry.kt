package me.awabi2048.kantancommander.model

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * 旧PARTICLEの表示方式キーです。#30でコマンド分離したため正規保存（v16以降）には含めません。
 *
 * 旧保存データ（v15以前）の移行時に `particleDisplayMode` 文字列を読み分ける
 * ためだけにキー文字列を残しています。新規コードは CommandType（PARTICLE／
 * PARTICLE_GEOMETRY）で分岐してください。旧POINT／GEOMETRYのenum自体は廃止しました。
 */

/**
 * 図形表示で描画できる形状です。
 *
 * POINT_SCATTERは既存互換の単点散布であり、図形パラメータを使用しません。
 * issue #12の初期候補（Line／Circle／Arc／Box・Wireframe／Sphere／Helix）を
 * すべて今回作業に含めます。
 */
enum class ParticleShape {
    POINT_SCATTER,
    LINE,
    CIRCLE,
    ARC,
    BOX,
    SPHERE,
    HELIX,
    ;

    companion object {
        fun parse(raw: String?): ParticleShape =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() } ?: POINT_SCATTER
    }
}

/**
 * 図形表示の保存キー・検証上限・点数見積もり・生成点計算を一元化します。
 *
 * 密度は「ブロックあたり粒子数、最小刻み1/4096」です。既定0.5・最大2.0は
 * issue #12の合意値であり、見積点数 = ceil(密度 × 幾何量) で求めます。
 * 幾何量は形状ごとに長さ・面積・体積を使い分けます。
 *
 * 位置の役割分担は次のとおりです。
 * - 中心・始点・基点：particlePositionSpec（既存）
 * - 終点・対角点：particleEndPositionSpec（新設）
 * - 法線・軸：geometryAxisYaw／geometryAxisPitch（度・方向ベクトル）
 */
object ParticleGeometry {
    const val PARAM_DISPLAY_MODE = "particleDisplayMode"
    const val PARAM_SHAPE = "particleShape"
    const val PARAM_DENSITY = "particleDensity"
    const val PARAM_RADIUS = "geometryRadius"
    const val PARAM_HEIGHT = "geometryHeight"
    const val PARAM_TURNS = "geometryTurns"
    const val PARAM_START_ANGLE = "geometryStartAngle"
    const val PARAM_END_ANGLE = "geometryEndAngle"
    const val PARAM_ROTATION = "geometryRotation"
    const val PARAM_AXIS_YAW = "geometryAxisYaw"
    const val PARAM_AXIS_PITCH = "geometryAxisPitch"
    const val PARAM_STEP = "geometryStep"
    const val PARAM_WIREFRAME = "geometryWireframe"

    /** 密度の合意値です。既定0.5・最大2.0・最小刻み1/4096。 */
    const val DENSITY_DEFAULT = 0.5
    const val DENSITY_MAX = 2.0
    const val DENSITY_GRANULARITY = 1.0 / 4096.0

    /** 半径・高さ・長さの上限（ブロック）です。極端な生成を検証時に抑止します。 */
    const val MAX_RADIUS = 32.0
    const val MAX_HEIGHT = 64.0
    const val MAX_LENGTH = 128.0

    /** 1回の図形表示で生成する点数の上限です。quota超過の常時拒否を避けます。 */
    const val MAX_POINTS = 4096

    /** 角度の正規範囲（度）です。 */
    const val MIN_ANGLE = -360.0
    const val MAX_ANGLE = 720.0

    /** 巻き数の範囲です。 */
    const val MIN_TURNS = 0.25
    const val MAX_TURNS = 16.0

    /** 生成ステップ（隣接点の最大間隔・ブロック）の範囲です。 */
    const val MIN_STEP = 0.1
    const val MAX_STEP = 8.0

    fun shape(node: CommandNode): ParticleShape =
        ParticleShape.parse(node.params[PARAM_SHAPE])

    fun density(node: CommandNode): Double? =
        node.params[PARAM_DENSITY]?.toDoubleOrNull()?.takeIf(Double::isFinite)

    /** 密度値が合意範囲（0超過・2.0以下）の有限値かを返します。 */
    fun isDensityValue(raw: String): Boolean {
        val value = raw.toDoubleOrNull() ?: return false
        return value.isFinite() && value > 0.0 && value <= DENSITY_MAX
    }

    /**
     * 図形の幾何量（長さ・面積・体積）を返します。検証不能な値はnullです。
     *
     * 終端長さ・半径・高さ・巻き数・角度はすべてparams文字列から解決し、
     * テンプレート参照を含む値は実行時解決へ委譲するためここでは扱いません。
     */
    fun measure(shape: ParticleShape, params: Map<String, String>, endDistance: Double?): Double? = when (shape) {
        ParticleShape.POINT_SCATTER -> null
        ParticleShape.LINE -> endDistance
        ParticleShape.CIRCLE -> {
            val radius = params[PARAM_RADIUS]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            if (radius == null) null else 2.0 * PI * radius
        }

        ParticleShape.ARC -> {
            val radius = params[PARAM_RADIUS]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            val sweep = sweepDegrees(params)
            if (radius == null || sweep == null) null else 2.0 * PI * radius * (sweep / 360.0)
        }

        ParticleShape.BOX -> {
            val edges = boxEdges(params, endDistance)
            if (edges == null) {
                null
            } else {
                val (sx, sy, sz) = edges
                2.0 * (sx * sy + sy * sz + sz * sx)
            }
        }

        ParticleShape.SPHERE -> {
            val radius = params[PARAM_RADIUS]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            if (radius == null) null else 4.0 * PI * radius * radius
        }

        ParticleShape.HELIX -> {
            val radius = params[PARAM_RADIUS]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            val height = params[PARAM_HEIGHT]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            val turns = params[PARAM_TURNS]?.toDoubleOrNull()?.takeIf(Double::isFinite)
            if (radius == null || height == null || turns == null || turns <= 0.0) {
                null
            } else {
                val circumference = 2.0 * PI * radius
                turns * kotlin.math.sqrt(circumference * circumference + (height / turns) * (height / turns))
            }
        }
    }

    /**
     * 見積点数を返します。幾何量が確定できない場合・密度が不正な場合はnullです。
     *
     * 微小体積の切り上げ（ceil）で最低1点を保証し、上限超過は呼び出し側の
     * validatorとquota判定へ委ねます。
     */
    fun estimatePoints(shape: ParticleShape, params: Map<String, String>, density: Double, endDistance: Double?): Int? {
        if (!density.isFinite() || density <= 0.0) return null
        val measured = measure(shape, params, endDistance) ?: return null
        if (!measured.isFinite() || measured < 0.0) return null
        return ceil(density * measured).toInt().coerceAtLeast(1)
    }

    /**
     * 生成点のオフセット列（中心・始点からの相対XYZ）を返します。
     *
     * stepは隣接点の最大間隔であり、密度とは独立した形状の粗さです。点数は
     * estimatePointsの見積もりと一致しない場合があり、実行時は見積もり値を
     * quota予約に使い、実際の生成はこの列を上限で打ち切ります。
     */
    fun generate(shape: ParticleShape, resolved: ResolvedGeometry): List<Triple<Double, Double, Double>> = when (shape) {
        ParticleShape.POINT_SCATTER -> listOf(Triple(0.0, 0.0, 0.0))
        ParticleShape.LINE -> generateLine(resolved)
        ParticleShape.CIRCLE -> generateCircle(resolved, 0.0, 360.0)
        ParticleShape.ARC -> generateCircle(resolved, resolved.startAngle, resolved.endAngle)
        ParticleShape.BOX -> generateBox(resolved)
        ParticleShape.SPHERE -> generateSphere(resolved)
        ParticleShape.HELIX -> generateHelix(resolved)
    }

    /** 実行時に解決された図形パラメータです。角度は度、軸は正規化済み方向です。 */
    data class ResolvedGeometry(
        val radius: Double = 0.0,
        val height: Double = 0.0,
        val turns: Double = 1.0,
        val startAngle: Double = 0.0,
        val endAngle: Double = 360.0,
        val rotation: Double = 0.0,
        val axisYaw: Double = 0.0,
        val axisPitch: Double = -90.0,
        val step: Double = 0.5,
        val wireframe: Boolean = true,
        /** LINEの終端・BOXの対角点の相対座標です。 */
        val endOffset: Triple<Double, Double, Double>? = null,
        /** BOXの辺長（sx, sy, sz）です。 */
        val boxEdges: Triple<Double, Double, Double>? = null,
    )

    private fun sweepDegrees(params: Map<String, String>): Double? {
        val start = params[PARAM_START_ANGLE]?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        val end = params[PARAM_END_ANGLE]?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        return (end - start).coerceIn(0.0, 720.0)
    }

    private fun boxEdges(params: Map<String, String>, endDistance: Double?): Triple<Double, Double, Double>? {
        // BOXは終端位置（対角点）から辺長を導出します。実行時は解決済み値を優先します。
        val distance = endDistance ?: return null
        if (!distance.isFinite() || distance <= 0.0) return null
        val side = distance / kotlin.math.sqrt(3.0)
        return Triple(side, side, side)
    }

    private fun axisBasis(yawDegrees: Double, pitchDegrees: Double): Triple<Triple<Double, Double, Double>, Triple<Double, Double, Double>, Triple<Double, Double, Double>> {
        // 法線（axis）を中心に回転する平面の基底（u, v）と法線（n）を返します。
        // yaw／pitchはマインクラフトの向き约定（度）で与えます。
        val yaw = Math.toRadians(yawDegrees)
        val pitch = Math.toRadians(pitchDegrees)
        val n = Triple(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
        val up = if (kotlin.math.abs(n.second) > 0.99) Triple(1.0, 0.0, 0.0) else Triple(0.0, 1.0, 0.0)
        val u = normalize(cross(up, n))
        val v = cross(n, u)
        return Triple(u, v, n)
    }

    private fun cross(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>) =
        Triple(
            a.second * b.third - a.third * b.second,
            a.third * b.first - a.first * b.third,
            a.first * b.second - a.second * b.first,
        )

    private fun normalize(v: Triple<Double, Double, Double>): Triple<Double, Double, Double> {
        val length = kotlin.math.sqrt(v.first * v.first + v.second * v.second + v.third * v.third)
        if (!length.isFinite() || length <= 0.0) return Triple(1.0, 0.0, 0.0)
        return Triple(v.first / length, v.second / length, v.third / length)
    }

    private fun generateLine(resolved: ResolvedGeometry): List<Triple<Double, Double, Double>> {
        val end = resolved.endOffset ?: return listOf(Triple(0.0, 0.0, 0.0))
        val length = kotlin.math.sqrt(end.first * end.first + end.second * end.second + end.third * end.third)
        if (!length.isFinite() || length <= 0.0) return listOf(Triple(0.0, 0.0, 0.0))
        val steps = (length / resolved.step.coerceAtLeast(MIN_STEP)).toInt().coerceAtLeast(1)
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            Triple(end.first * t, end.second * t, end.third * t)
        }
    }

    private fun generateCircle(resolved: ResolvedGeometry, fromDegrees: Double, toDegrees: Double): List<Triple<Double, Double, Double>> {
        val radius = resolved.radius.coerceAtLeast(0.0)
        if (radius <= 0.0) return listOf(Triple(0.0, 0.0, 0.0))
        val sweep = (toDegrees - fromDegrees).coerceIn(0.0, 720.0)
        if (sweep <= 0.0) return listOf(Triple(0.0, 0.0, 0.0))
        val arcLength = 2.0 * PI * radius * (sweep / 360.0)
        val steps = (arcLength / resolved.step.coerceAtLeast(MIN_STEP)).toInt().coerceAtLeast(8)
        val (u, v, _) = axisBasis(resolved.axisYaw, resolved.axisPitch)
        val base = Math.toRadians(resolved.rotation + fromDegrees)
        return (0..steps).map { i ->
            val angle = base + Math.toRadians(sweep) * (i.toDouble() / steps)
            val cu = cos(angle) * radius
            val cv = sin(angle) * radius
            Triple(
                u.first * cu + v.first * cv,
                u.second * cu + v.second * cv,
                u.third * cu + v.third * cv,
            )
        }
    }

    private fun generateBox(resolved: ResolvedGeometry): List<Triple<Double, Double, Double>> {
        val end = resolved.endOffset ?: return listOf(Triple(0.0, 0.0, 0.0))
        val step = resolved.step.coerceAtLeast(MIN_STEP)
        fun axisPoints(length: Double): List<Double> {
            if (length <= 0.0) return listOf(0.0)
            val steps = (length / step).toInt().coerceAtLeast(1)
            return (0..steps).map { it.toDouble() / steps * length }
        }

        val xs = axisPoints(kotlin.math.abs(end.first))
        val ys = axisPoints(kotlin.math.abs(end.second))
        val zs = axisPoints(kotlin.math.abs(end.third))
        val sx = if (end.first >= 0) 1.0 else -1.0
        val sy = if (end.second >= 0) 1.0 else -1.0
        val sz = if (end.third >= 0) 1.0 else -1.0
        if (!resolved.wireframe) {
            // 表面全体ではなく頂点8点を返し、点数爆発を避けます。
            return listOf(
                Triple(0.0, 0.0, 0.0), Triple(end.first, 0.0, 0.0),
                Triple(0.0, end.second, 0.0), Triple(0.0, 0.0, end.third),
                Triple(end.first, end.second, 0.0), Triple(end.first, 0.0, end.third),
                Triple(0.0, end.second, end.third), Triple(end.first, end.second, end.third),
            )
        }
        val points = linkedSetOf<Triple<Double, Double, Double>>()
        // 12辺のワイヤーフレームを生成します。
        xs.forEach { x ->
            points += Triple(x * sx, 0.0, 0.0)
            points += Triple(x * sx, end.second, 0.0)
            points += Triple(x * sx, 0.0, end.third)
            points += Triple(x * sx, end.second, end.third)
        }
        ys.forEach { y ->
            points += Triple(0.0, y * sy, 0.0)
            points += Triple(end.first, y * sy, 0.0)
            points += Triple(0.0, y * sy, end.third)
            points += Triple(end.first, y * sy, end.third)
        }
        zs.forEach { z ->
            points += Triple(0.0, 0.0, z * sz)
            points += Triple(end.first, 0.0, z * sz)
            points += Triple(0.0, end.second, z * sz)
            points += Triple(end.first, end.second, z * sz)
        }
        return points.toList()
    }

    private fun generateSphere(resolved: ResolvedGeometry): List<Triple<Double, Double, Double>> {
        val radius = resolved.radius.coerceAtLeast(0.0)
        if (radius <= 0.0) return listOf(Triple(0.0, 0.0, 0.0))
        val step = resolved.step.coerceAtLeast(MIN_STEP)
        // 緯度分割で球面を走査し、極の重複を避けます。
        val bands = ((PI * radius) / step).toInt().coerceIn(4, 64)
        val points = mutableListOf<Triple<Double, Double, Double>>()
        val (u, v, n) = axisBasis(resolved.axisYaw, resolved.axisPitch)
        val base = Math.toRadians(resolved.rotation)
        for (b in 0..bands) {
            val polar = PI * (b.toDouble() / bands)
            val ringRadius = radius * sin(polar)
            val height = radius * cos(polar)
            val circumference = 2.0 * PI * ringRadius
            val segments = if (circumference <= step) 1 else (circumference / step).toInt().coerceAtLeast(8)
            for (s in 0 until segments) {
                val angle = base + 2.0 * PI * (s.toDouble() / segments)
                val cu = cos(angle) * ringRadius
                val cv = sin(angle) * ringRadius
                points += Triple(
                    u.first * cu + v.first * cv + n.first * height,
                    u.second * cu + v.second * cv + n.second * height,
                    u.third * cu + v.third * cv + n.third * height,
                )
            }
        }
        return points
    }

    private fun generateHelix(resolved: ResolvedGeometry): List<Triple<Double, Double, Double>> {
        val radius = resolved.radius.coerceAtLeast(0.0)
        val height = resolved.height.coerceAtLeast(0.0)
        val turns = resolved.turns.coerceIn(MIN_TURNS, MAX_TURNS)
        if (radius <= 0.0 && height <= 0.0) return listOf(Triple(0.0, 0.0, 0.0))
        val circumference = 2.0 * PI * radius
        val approxLength = turns * kotlin.math.sqrt(circumference * circumference + (height / turns) * (height / turns))
        val steps = (approxLength / resolved.step.coerceAtLeast(MIN_STEP)).toInt().coerceAtLeast(16)
        val (u, v, n) = axisBasis(resolved.axisYaw, resolved.axisPitch)
        val base = Math.toRadians(resolved.rotation)
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            val angle = base + 2.0 * PI * turns * t
            val cu = cos(angle) * radius
            val cv = sin(angle) * radius
            val rise = height * t
            Triple(
                u.first * cu + v.first * cv + n.first * rise,
                u.second * cu + v.second * cv + n.second * rise,
                u.third * cu + v.third * cv + n.third * rise,
            )
        }
    }
}
