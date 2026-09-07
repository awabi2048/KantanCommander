package me.awabi2048.kantancommander.model

import org.bukkit.Material
import kotlin.math.ceil

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

    /**
     * ロケットの上昇速度（ブロック／tick）の仮定値です。
     *
     * バニラの推進力ごとの到達高度から逆算した概算であり、厳密な高度一致では
     * なく「指定高度付近での起爆」を保証するための変換定数です。実機検証で
     * ずれる場合はこの1か所を調整します。
     */
    const val ASCENT_BLOCKS_PER_TICK = 0.5

    fun burstHeight(node: CommandNode): Double? =
        node.params[PARAM_HEIGHT]?.toDoubleOrNull()?.takeIf(Double::isFinite)

    /** 打ち上げ高さが0〜320の有限値かを返します。 */
    fun isBurstHeight(raw: String): Boolean {
        val value = raw.toDoubleOrNull() ?: return false
        return value.isFinite() && value in HEIGHT_MIN..HEIGHT_MAX
    }

    /** 指定高度からflight power（1〜3）を逆算します。高さ0は即時破裂扱いです。 */
    fun heightToPower(height: Double): Int = when {
        height <= 0.0 -> 1
        height <= 20.0 -> 1
        height <= 60.0 -> 2
        else -> 3
    }

    /** 指定高度から起爆までのtick数を逆算します。高さ0は1tick（即時相当）です。 */
    fun heightToTicks(height: Double): Int =
        if (height <= 0.0) 1 else ceil(height / ASCENT_BLOCKS_PER_TICK).toInt().coerceAtLeast(1)

    /** 花火アイテムとして有効な素材かを返します。 */
    fun isFireworkMaterial(raw: String): Boolean =
        CommandValueRules.material(raw, allowAir = false) == Material.FIREWORK_ROCKET

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
