package me.awabi2048.kantancommander.model

import org.bukkit.Material
import kotlin.math.roundToInt

/**
 * FIREWORK_LAUNCHの保存キーと打ち上げ高さ→推進力の変換を一元化します。
 *
 * 花火の中身はGIVE_ITEMと同型の item＋itemData 対（ItemStackCodecのBase64）で
 * 保存し、手持ちのFIREWORK_ROCKETから取り込みます。打ち上げ高さはブロック単位
 * （0〜320）の直接入力であり、実行時にflight powerと起爆tickへ逆算します。
 */
object FireworkSettings {
    const val PARAM_ITEM = "fireworkItem"
    const val PARAM_DATA = "fireworkData"
    const val PARAM_HEIGHT = "burstHeight"

    /** 打ち上げ高さの範囲（ブロック）です。上限320は建築上限に合わせます。 */
    const val HEIGHT_MIN = 0.0
    const val HEIGHT_MAX = 320.0
    const val HEIGHT_DEFAULT = 20.0

    fun burstHeight(node: CommandNode): Double? =
        node.params[PARAM_HEIGHT]?.toDoubleOrNull()?.takeIf(Double::isFinite)

    /** 打ち上げ高さが0〜320の有限値かを返します。 */
    fun isBurstHeight(raw: String): Boolean {
        val value = raw.toDoubleOrNull() ?: return false
        return value.isFinite() && value in HEIGHT_MIN..HEIGHT_MAX
    }

    /**
     * 指定高度からflight power（1〜3）を逆算します。
     *
     * バニラ出力の近似専用です。実行時は寿命バックストップと到達判定で起爆
     * するため、powerは見た目（尾の長さ）にしか影響しません。
     */
    fun heightToPower(height: Double): Int = when {
        height <= 0.0 -> 1
        height <= 20.0 -> 1
        height <= 60.0 -> 2
        else -> 3
    }

    /**
     * バニラの上昇運動定数です（`FireworkRocketEntity` の逆コンパイル確認値）。
     *
     * 自由飛行（`shotAtAngle=false`）では毎tick `v_xz *= 1.15`、`v_y += 0.04`
     * であり、Y方向の減速・重力はありません。初速を厳密な `(0, 0.05, 0)` で
     * 上書きすれば、`0 × 1.15 = 0` のためXZは恒久的に0を維持します。
     * 起爆は `life > lifetime` のtickであり、移動回数は `lifetime + 1` です。
     */
    const val INITIAL_UPWARD_VELOCITY = 0.05
    const val UPWARD_ACCELERATION = 0.04

    /**
     * 寿命バックストップの余裕tickです。
     *
     * 起爆はワンショットタスクの到達判定が担い、寿命起爆は保険です。到達判定が
     * 誤差で遅れてもこの余裕内で吸収し、漏れたロケットの無限飛行を防ぎます。
     */
    const val LIFETIME_MARGIN = 100

    /**
     * 指定高度に到達する移動回数を返します。
     *
     * 高度式 `y = 0.02M² + 0.03M` の逆変換を四捨五入し、tick量子化の誤差を
     * 半tick移動分（低高度で0.2以下・320で約1.3ブロック）に収めます。
     */
    fun heightToMoves(height: Double): Int {
        if (height <= 0.0) return 0
        val moves = (-0.03 + kotlin.math.sqrt(0.0009 + 0.08 * height)) / 0.04
        return moves.roundToInt().coerceAtLeast(1)
    }

    /** 移動回数から到達高度を求めます（検証・テスト用）。 */
    fun movesToHeight(moves: Int): Double = 0.02 * moves * moves + 0.03 * moves

    /**
     * 起爆タスクの発火tick（`setTicksToDetonate` 相当の移動回数−1）を返します。
     *
     * 発火時点で正確な目標座標へテレポート補正して即時起爆するため、高度は厳密です。
     */
    fun detonationTicks(height: Double): Int = (heightToMoves(height) - 1).coerceAtLeast(0)

    /** 寿命バックストップ（到達不能時の保険）を返します。 */
    fun backstopLifetime(height: Double): Int = heightToMoves(height) - 1 + LIFETIME_MARGIN

    /** 花火アイテムとして有効な素材かを返します。 */
    fun isFireworkMaterial(raw: String): Boolean =
        CommandValueRules.material(raw, allowAir = false) == Material.FIREWORK_ROCKET

    /**
     * 試射の発射地点を解決します。
     *
     * 試射は追従中に発生しないため、つねにクリップ固定のanchor座標を使います。
     * anchorがない場合は制御ブロックの1ブロック上（`placementAbove`）、
     * それもなければ最終代替（操作者位置）へ落とします。
     */
    fun previewSite(
        anchor: org.bukkit.Location?,
        placementAbove: org.bukkit.Location?,
        fallback: org.bukkit.Location,
    ): org.bukkit.Location = anchor?.clone() ?: placementAbove?.clone() ?: fallback.clone()

    /**
     * 制御ブロックの1ブロック上の座標を求めます。
     *
     * ワールドが解決できない場合はnullを返し、呼び出し側で最終代替へ落とします。
     */
    fun placementAbove(placement: DiskPlacement): org.bukkit.Location? {
        val world = org.bukkit.Bukkit.getWorld(placement.world) ?: return null
        return org.bukkit.Location(
            world,
            placement.x + 0.5,
            (placement.y + 1).toDouble(),
            placement.z + 0.5,
        )
    }

    /**
     * 出力・実行で共有する花火効果の型付き表現です。
     *
     * BukkitのFireworkEffectから抽出し、バニラSNBT（Fireworks.Explosions）へ
     * 変換する境界を固定します。shapeはバニラ名（small_ball等）で保持します。
     */
    data class FireworkEffectData(
        val shape: String,
        val colors: List<Int>,
        val fadeColors: List<Int>,
        val trail: Boolean,
        val flicker: Boolean,
    )

    /** Bukkit効果種別をバニラの形状名へ変換します。 */
    fun vanillaShape(bukkitName: String): String = when (bukkitName.uppercase()) {
        "BALL" -> "small_ball"
        "BALL_LARGE" -> "large_ball"
        "STAR" -> "star"
        "CREEPER" -> "creeper"
        "BURST" -> "burst"
        else -> "small_ball"
    }

    /** 花火1発分のSNBT（Fireworksコンパウンド値部）を生成します。 */
    fun fireworkSnbt(power: Int, effects: List<FireworkEffectData>): String {
        val explosions = effects.joinToString(",") { effect ->
            val colors = effect.colors.joinToString(",", prefix = "[I;", postfix = "]")
            val fades = effect.fadeColors.joinToString(",", prefix = "[I;", postfix = "]")
            val trail = if (effect.trail) "1b" else "0b"
            val flicker = if (effect.flicker) "1b" else "0b"
            "{Shape:\"${effect.shape}\",Colors:$colors,FadeColors:$fades,HasTrail:$trail,HasTwinkle:$flicker}"
        }
        return "{Flight:${power}b,Explosions:[$explosions]}"
    }
}
