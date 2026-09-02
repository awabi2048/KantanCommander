package me.awabi2048.kantancommander.model

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry

/**
 * GUI・実行前検証・実行処理が共有する値の受理規則です。
 *
 * 画面側だけで「登録済み」と判定したり、実行側だけでAIRを拒否したりすると、
 * 画面では設定できるのに保存後に実行できない値が生まれます。値の意味を持つ
 * 判定はこの境界へ集約し、各UIは表示と入力の違いだけを担当します。
 */
object CommandValueRules {
    private val ASCII_INTEGER_PATTERN = Regex("[0-9]+")
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
        raw.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { it in range } == true

    private fun registered(raw: String, lookup: (NamespacedKey) -> Boolean): Boolean {
        val key = NamespacedKey.fromString(raw) ?: return false
        if (!paperRegistryAvailable) return true
        return runCatching { lookup(key) }.getOrDefault(false)
    }
}
