package me.awabi2048.kantancommander.model

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeInstance
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity

/**
 * issue #32 のattribute操作を支える型付き境界です。
 *
 * EntityTypeごとの巨大なwhenをGUIと実行の各所へ増やさず、対象が持つ
 * capability（[AttributeInstance]の有無）で適用可否を解決します。
 * modifierの識別には現行Paper APIに合わせてNamespacedKeyを使用し、
 * UUID前提の新規設計にはしません。
 */
object EntityAttributeSupport {
    /** attribute操作の保存値です。 */
    const val PARAM_ATTRIBUTE = "attribute"
    const val PARAM_OPERATION = "attributeOperation"
    const val PARAM_MODIFIER_KEY = "modifierKey"
    const val PARAM_AMOUNT = "modifierAmount"
    const val PARAM_MODIFIER_OPERATION = "modifierOperation"

    /** 基本値を設定します。 */
    const val OPERATION_BASE_VALUE = "base_value"

    /** modifierを追加します。 */
    const val OPERATION_MODIFIER_ADD = "modifier_add"

    /** modifierを削除します。 */
    const val OPERATION_MODIFIER_REMOVE = "modifier_remove"

    /**
     * modifier演算の保存値です。Bukkitの演算子と1対1で対応します。
     *
     * - [MODIFIER_ADD_NUMBER]: 数値を加算します（vanillaのadd_value相当）。
     * - [MODIFIER_MULTIPLY_BASE]: 基本値に乗算します（vanillaのadd_multiplied_base相当）。
     * - [MODIFIER_MULTIPLY_TOTAL]: 合計に乗算します（vanillaのadd_multiplied_total相当）。
     */
    const val MODIFIER_ADD_NUMBER = "add_number"
    const val MODIFIER_MULTIPLY_BASE = "multiply_base"
    const val MODIFIER_MULTIPLY_TOTAL = "multiply_total"

    fun isOperation(raw: String): Boolean =
        raw == OPERATION_BASE_VALUE || raw == OPERATION_MODIFIER_ADD || raw == OPERATION_MODIFIER_REMOVE

    fun isModifierOperation(raw: String): Boolean =
        raw == MODIFIER_ADD_NUMBER || raw == MODIFIER_MULTIPLY_BASE || raw == MODIFIER_MULTIPLY_TOTAL

    fun modifierOperation(raw: String): AttributeModifier.Operation? = when (raw) {
        MODIFIER_ADD_NUMBER -> AttributeModifier.Operation.ADD_NUMBER
        MODIFIER_MULTIPLY_BASE -> AttributeModifier.Operation.ADD_SCALAR
        MODIFIER_MULTIPLY_TOTAL -> AttributeModifier.Operation.MULTIPLY_SCALAR_1
        else -> null
    }

    /** vanilla出力で使う演算子名を返します。不正確な変換は行いません。 */
    fun vanillaModifierOperation(raw: String): String? = when (raw) {
        MODIFIER_ADD_NUMBER -> "add_value"
        MODIFIER_MULTIPLY_BASE -> "add_multiplied_base"
        MODIFIER_MULTIPLY_TOTAL -> "add_multiplied_total"
        else -> null
    }

    /** attributeのIDが構文として正しく、実在するattributeを指すかを返します。 */
    fun isAttributeId(raw: String): Boolean {
        val key = NamespacedKey.fromString(raw) ?: return false
        if (!paperRegistryAvailable) return true
        return runCatching { Registry.ATTRIBUTE.get(key) != null }.getOrDefault(false)
    }

    /**
     * attributeを解決します。
     *
     * Paper Registryが初期化できないAPI単体テスト環境ではnullを返します。
     * 構文確認だけが必要な検証側は[isAttributeId]を使い、実行側は解決失敗を
     * 失敗として扱います。実行時と同じRegistryで解決する点はCommandValueRulesと揃えます。
     */
    fun resolve(raw: String): Attribute? {
        if (!paperRegistryAvailable) return null
        val key = NamespacedKey.fromString(raw) ?: return null
        return runCatching { Registry.ATTRIBUTE.get(key) }.getOrNull()
    }

    private val paperRegistryAvailable = runCatching { Registry.ATTRIBUTE }.isSuccess

    /** modifier識別キーがNamespacedKey構文かを返します。 */
    fun isModifierKey(raw: String): Boolean = NamespacedKey.fromString(raw) != null

    fun modifierKey(raw: String): NamespacedKey? = NamespacedKey.fromString(raw)

    /**
     * 対象がattributeを持つか（capability）を解決します。
     *
     * 対応しない対象はnullであり、呼び出し側は「非適用」として数えます（#6）。
     */
    fun instanceOf(entity: Entity, attribute: Attribute): AttributeInstance? {
        if (entity !is LivingEntity) return null
        return runCatching { entity.getAttribute(attribute) }.getOrNull()
    }

    /**
     * 基本値を設定します。有限値であることは呼び出し側の検証で担保します。
     *
     * entity種ごとのdefault値の扱いが複雑なため、曖昧な「デフォルトへ戻す」
     * 操作は用意しません。
     */
    fun setBaseValue(instance: AttributeInstance, value: Double): Boolean {
        if (!value.isFinite()) return false
        runCatching { instance.baseValue = value }.getOrNull() ?: return false
        return true
    }

    /**
     * modifierを追加します。
     *
     * バニラの `attribute ... modifier add` と等価にするため、同キーの
     * modifierが既に存在する場合は置き換えず失敗とします。実行と出力の
     * 同一意味を保つための契約であり、呼び出し側は失敗として数えます。
     */
    fun addModifier(instance: AttributeInstance, key: NamespacedKey, amount: Double, operation: AttributeModifier.Operation): Boolean {
        if (!amount.isFinite()) return false
        if (runCatching { instance.getModifier(key) }.getOrNull() != null) return false
        return runCatching {
            instance.addModifier(AttributeModifier(key, amount, operation))
            true
        }.getOrDefault(false)
    }

    /**
     * modifierを削除します。存在しないmodifierの削除は偽を返し、
     * 呼び出し側が失敗として数えます（バニラの `modifier remove` と等価）。
     */
    fun removeModifier(instance: AttributeInstance, key: NamespacedKey): Boolean {
        val existing = runCatching { instance.getModifier(key) }.getOrNull() ?: return false
        return runCatching {
            instance.removeModifier(existing)
            true
        }.getOrDefault(false)
    }
}
