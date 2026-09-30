package me.awabi2048.kantancommander.model

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import java.math.BigDecimal

/**
 * GUI・実行前検証・実行処理が共有する値の受理規則です。
 *
 * 画面側だけで「登録済み」と判定したり、実行側だけでAIRを拒否したりすると、
 * 画面では設定できるのに保存後に実行できない値が生まれます。値の意味を持つ
 * 判定はこの境界へ集約し、各UIは表示と入力の違いだけを担当します。
 */
object CommandValueRules {
    private val ASCII_INTEGER_PATTERN = Regex("[0-9]+")
    private val TICK_SECONDS_DECIMAL = BigDecimal("0.05")
    // 変数名は式・テンプレートへそのまま埋め込むため、先頭を英字に限定します。
    // 数字始まりを許すと、式の数値リテラルと区別できず、GUI・実行・出力で
    // 同じ名前を解釈できなくなります。
    private val TAG_PATTERN = Regex("[A-Za-z0-9_.:+-]{1,64}")

    /**
     * ワールド内変数のSTRING値に許す最大文字数です。
     * 編集Dialogの入力上限・保存層の検証・実行時のVARIABLE代入が同じ値を共有し、
     * Dialogの初期値上限(Paperの入力画面生成)を超える保存値が存在しない不変条件を
     * 読み込み時の正規化と合わせて維持します。
     */
    const val WORLD_VARIABLE_STRING_MAX_LENGTH: Int = 256

    /**
     * Paper Registryはサーバー起動前のAPI単体テスト環境では初期化できません。
     * 実サーバーでこの値がtrueのときだけ実在確認を行い、APIだけの環境では
     * 実行時に同じく行われるNamespacedKey構文確認までをテスト可能な境界とします。
     */
    private val paperRegistryAvailable = runCatching {
        Registry.ENTITY_TYPE
        Registry.EFFECT
    }.isSuccess

    /** Materialへ解決でき、必要に応じてAIRを除外した値を返します。 */
    fun material(raw: String, allowAir: Boolean = true): Material? {
        val material = Material.matchMaterial(raw) ?: return null
        return material.takeIf { allowAir || it != Material.AIR }
    }

    /** 実行時にブロック配置へ使えるMaterialかを返します。setblock/fillのAIRも許可します。 */
    fun placementMaterial(raw: String): Material? = material(raw, allowAir = true)

    /** GUI・保存時検証・実行時が共有する符号なしの正整数パーサーです。 */
    fun parsePositiveInt(raw: String): Int? =
        raw.takeIf(ASCII_INTEGER_PATTERN::matches)?.toIntOrNull()?.takeIf { it > 0 }

    /** 0を許可する符号なし整数パーサーです。表示時間の各区分で使用します。 */
    fun parseNonNegativeInt(raw: String): Int? =
        raw.takeIf(ASCII_INTEGER_PATTERN::matches)?.toIntOrNull()?.takeIf { it >= 0 }

    /** エンティティの種類は実行時と同じENTITY_TYPE Registryで解決します。 */
    fun isEntityTypeId(raw: String): Boolean = registered(raw) { key -> Registry.ENTITY_TYPE.get(key) != null }

    /** 効果種類は実行時と同じEFFECT Registryで解決します。 */
    fun isEffectId(raw: String): Boolean = registered(raw) { key -> Registry.EFFECT.get(key) != null }

    /** サウンドはリソースパック由来のカスタムIDも受け付けるため、構文だけを検証します。 */
    fun isSoundId(raw: String): Boolean = NamespacedKey.fromString(raw) != null

    fun isVariableName(raw: String): Boolean = SystemVariableNames.isUserName(raw)

    /** 一時変数の `%{name}%` 参照記法かを返します。 */
    fun isTemporaryReference(raw: String): Boolean = TemporaryTemplate.isSingleReference(raw)

    /** 一時変数の参照名部分が正当かを返します。 */
    fun isTemporaryName(raw: String): Boolean = SystemVariableNames.isUserName(raw)

    fun isTag(raw: String): Boolean = TAG_PATTERN.matches(raw)

    fun isFiniteDoubleIn(raw: String, range: ClosedFloatingPointRange<Double>): Boolean =
        parseFiniteDouble(raw)?.let { it in range } == true

    /** 入力文字列を、空白を除いた有限Doubleへ変換します。 */
    fun parseFiniteDouble(raw: String): Double? =
        raw.trim().toDoubleOrNull()?.takeIf(Double::isFinite)

    /** `min..max` 形式の数値Rangeです。整数判定は生成側が端点から行います。 */
    data class NumericRange(val min: Double, val max: Double)

    /** 乱数の共通記法を検証して分解します。余分な区切り・非有限値・逆順は拒否します。 */
    fun parseNumericRange(raw: String): NumericRange? {
        val normalized = raw.trim()
        if (normalized.contains("...")) return null
        val parts = normalized.split("..")
        if (parts.size != 2) return null
        val min = parseFiniteDouble(parts[0]) ?: return null
        val max = parseFiniteDouble(parts[1]) ?: return null
        if (min > max) return null
        return NumericRange(min, max)
    }

    /**
     * 制御ブロック条件の赤石信号強度指定を検証して 0..15 の整数範囲へ解決します。
     *
     * `4`（強度4ちょうど）・`4..6`（4以上6以下）・`3..`（3以上）・`..2`（2以下）を受理します。
     * 片側省略はその端の限界値を意味し、空欄・非整数・逆順・範囲外・余分な区切り・
     * 両端省略は拒否します。区切りの前後空白は `4 .. 6` のように受理します
     * （`min..max` の乱数範囲と同じ許容方針）。`0`・`..0`・`0..0` のような
     * 0だけを含む範囲も有効な指定であり、無信号の判定として扱います。
     */
    fun parseSignalStrengthRange(raw: String): IntRange? {
        val normalized = raw.trim()
        if (normalized.isEmpty() || normalized.contains("...")) return null
        val parts = normalized.split("..", limit = 2).map(String::trim)
        if (parts.size == 1) {
            val single = parseNonNegativeInt(parts[0]) ?: return null
            return (single..single).takeIf { single <= SIGNAL_STRENGTH_MAX }
        }
        if (parts[0].isBlank() && parts[1].isBlank()) return null
        val min = if (parts[0].isBlank()) 0 else parseNonNegativeInt(parts[0]) ?: return null
        val max = if (parts[1].isBlank()) SIGNAL_STRENGTH_MAX else parseNonNegativeInt(parts[1]) ?: return null
        if (min > max || max > SIGNAL_STRENGTH_MAX) return null
        return min..max
    }

    /** 赤石信号強度の上限です。Minecraftの信号強度 0..15 に合わせます。 */
    const val SIGNAL_STRENGTH_MAX: Int = 15

    /** 秒数がMinecraftのtick境界（0.05秒）の整数倍かを10進数として判定します。 */
    fun isTickAlignedSeconds(raw: String): Boolean = runCatching {
        BigDecimal(raw.trim()).remainder(TICK_SECONDS_DECIMAL).compareTo(BigDecimal.ZERO) == 0
    }.getOrDefault(false)

    /** 既にDoubleへ展開された秒数をtick境界へ照合します。 */
    fun isTickAlignedSeconds(seconds: Double): Boolean =
        seconds.isFinite() && runCatching {
            BigDecimal.valueOf(seconds).remainder(TICK_SECONDS_DECIMAL).compareTo(BigDecimal.ZERO) == 0
        }.getOrDefault(false)

    /** tick境界に一致する秒数をMinecraftのtickへ変換します。丸めは行いません。 */
    fun secondsToTicks(seconds: Double): Long? {
        if (!isTickAlignedSeconds(seconds)) return null
        return runCatching {
            BigDecimal.valueOf(seconds)
                .divide(TICK_SECONDS_DECIMAL)
                .longValueExact()
        }.getOrNull()
    }

    /** DISPLAY_TEXTの時間区分で許可する値です。 */
    fun isDisplayTimeSeconds(seconds: Double): Boolean =
        seconds.isFinite() && seconds in 0.0..MAX_COMMAND_TIME_SECONDS && isTickAlignedSeconds(seconds)

    /** WAITで許可する値です。0秒は待機にならないため許可しません。 */
    fun isWaitSeconds(seconds: Double): Boolean =
        seconds.isFinite() && seconds > 0.0 && seconds <= MAX_COMMAND_TIME_SECONDS && isTickAlignedSeconds(seconds)

    /**
     * プログラムタイマーで許可する秒数です。下限は設けず、0より大きい
     * tick境界の値を許可します。上限はWAITと共通の秒数上限を用います。
     */
    fun isTimerSeconds(seconds: Double): Boolean =
        seconds.isFinite() && seconds > 0.0 && seconds <= MAX_TIMER_SECONDS.toDouble() && isTickAlignedSeconds(seconds)

    private fun registered(raw: String, lookup: (NamespacedKey) -> Boolean): Boolean {
        val key = NamespacedKey.fromString(raw) ?: return false
        if (!paperRegistryAvailable) return true
        return runCatching { lookup(key) }.getOrDefault(false)
    }
}

/** DISPLAY_TEXT／WAITが共有する秒数の上限です。 */
const val MAX_COMMAND_TIME_SECONDS = 86_400.0
