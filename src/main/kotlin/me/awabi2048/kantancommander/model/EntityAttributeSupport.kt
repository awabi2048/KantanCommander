package me.awabi2048.kantancommander.model

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeInstance
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity

/**
 * attribute操作を支える型付き境界です。
 *
 * EntityTypeごとの巨大なwhenをGUIと実行の各所へ増やさず、対象が持つ
 * capability（[AttributeInstance]の有無）で適用可否を解決します。
 * 操作はbase値の設定のみに対応し、modifierの追加・削除は扱いません。
 */
object EntityAttributeSupport {
    /** attribute操作の保存値です。 */
    const val PARAM_ATTRIBUTE = "attribute"

    /**
     * attribute値の保存キーです。
     *
     * 統合選択形式では `entityDataPath=attribute:<id>` がIDの正本であり、
     * このキーは変更後のbase値だけを保持します。
     */
    const val PARAM_VALUE = "attributeValue"

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

    /**
     * 選択画面に列挙するattribute ID一覧を返します。
     *
     * Paper Registryが初期化できないAPI単体テスト環境では空一覧を返します。
     * 対象種別での静的絞り込みは行わず、非Living確定時の親画面秘匿と
     * 実行時のcapability判定に委ねます。
     */
    fun allAttributeIds(): List<String> {
        if (!paperRegistryAvailable) return emptyList()
        return runCatching {
            Registry.ATTRIBUTE.map { it.key.toString() }.sorted()
        }.getOrDefault(emptyList())
    }

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
}
