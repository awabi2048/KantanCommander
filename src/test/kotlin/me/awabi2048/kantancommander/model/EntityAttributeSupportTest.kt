package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * attribute操作境界の検証です。
 *
 * base値設定のみに対応し、ID構文の検証を固定します。
 * 実Registryを必要とする解決・列挙は実行環境で検証し、
 * ここでは構文確認に留めます。
 */
class EntityAttributeSupportTest {
    @Test
    fun `attribute ids accept syntax when registry is unavailable`() {
        // API単体テスト環境ではRegistryが初期化できないため、構文確認までを行います。
        assertTrue(EntityAttributeSupport.isAttributeId("minecraft:max_health"))
        assertFalse(EntityAttributeSupport.isAttributeId("not a key"))
        assertFalse(EntityAttributeSupport.isAttributeId(""))
    }

    @Test
    fun `attribute id list is empty when registry is unavailable`() {
        // API単体テスト環境ではRegistryが初期化できないため、空一覧を返します。
        assertTrue(EntityAttributeSupport.allAttributeIds().isEmpty())
    }
}
