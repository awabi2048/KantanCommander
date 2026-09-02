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
    private val EQUIPMENT_SLOTS = setOf("HAND", "OFF_HAND", "HEAD", "CHEST", "LEGS", "FEET")

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

    /** 実行時にブロック配置へ使えるMaterialかを返します。 */
    fun placementMaterial(raw: String): Material? = material(raw, allowAir = false)

    /** GUI・保存時検証・実行時が共有する符号なしの正整数パーサーです。 */
    fun parsePositiveInt(raw: String): Int? =
        raw.takeIf(ASCII_INTEGER_PATTERN::matches)?.toIntOrNull()?.takeIf { it > 0 }

    /** 0を許可する符号なし整数パーサーです。表示時間の各区分で使用します。 */
    fun parseNonNegativeInt(raw: String): Int? =
        raw.takeIf(ASCII_INTEGER_PATTERN::matches)?.toIntOrNull()?.takeIf { it >= 0 }

    /** エンティティ種類は実行時と同じENTITY_TYPE Registryで解決します。 */
    fun isEntityTypeId(raw: String): Boolean = registered(raw) { key -> Registry.ENTITY_TYPE.get(key) != null }

    /** 効果種類は実行時と同じEFFECT Registryで解決します。 */
    fun isEffectId(raw: String): Boolean = registered(raw) { key -> Registry.EFFECT.get(key) != null }

    /** サウンドはリソースパック由来のカスタムIDも受け付けるため、構文だけを検証します。 */
    fun isSoundId(raw: String): Boolean = NamespacedKey.fromString(raw) != null

    fun isVariableName(raw: String): Boolean = SystemVariableNames.isUserName(raw)

    fun isTag(raw: String): Boolean = TAG_PATTERN.matches(raw)

    fun isEquipmentSlot(raw: String): Boolean = raw in EQUIPMENT_SLOTS

    fun isFiniteDoubleIn(raw: String, range: ClosedFloatingPointRange<Double>): Boolean =
        parseFiniteDouble(raw)?.let { it in range } == true

    /** 入力文字列を、空白を除いた有限Doubleへ変換します。 */
    fun parseFiniteDouble(raw: String): Double? =
        raw.trim().toDoubleOrNull()?.takeIf(Double::isFinite)

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

    private fun registered(raw: String, lookup: (NamespacedKey) -> Boolean): Boolean {
        val key = NamespacedKey.fromString(raw) ?: return false
        if (!paperRegistryAvailable) return true
        return runCatching { lookup(key) }.getOrDefault(false)
    }
}

/** DISPLAY_TEXT／WAITが共有する秒数の上限です。 */
const val MAX_COMMAND_TIME_SECONDS = 86_400.0
