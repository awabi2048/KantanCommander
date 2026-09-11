package me.awabi2048.kantancommander.model

import org.bukkit.attribute.AttributeModifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #32 のattribute操作境界の検証です。
 *
 * 操作・演算子のstableな保存値とBukkit API値の対応、ID構文の検証を固定します。
 * 実Registryを必要とする解決は実行環境で検証し、ここでは構文と対応表に留めます。
 */
class EntityAttributeSupportTest {
    @Test
    fun `operation values are recognized`() {
        assertTrue(EntityAttributeSupport.isOperation("base_value"))
        assertTrue(EntityAttributeSupport.isOperation("modifier_add"))
        assertTrue(EntityAttributeSupport.isOperation("modifier_remove"))
        assertFalse(EntityAttributeSupport.isOperation("reset"))
        assertFalse(EntityAttributeSupport.isOperation(""))
    }

    @Test
    fun `modifier operations map to bukkit operations`() {
        assertEquals(AttributeModifier.Operation.ADD_NUMBER, EntityAttributeSupport.modifierOperation("add_number"))
        assertEquals(AttributeModifier.Operation.ADD_SCALAR, EntityAttributeSupport.modifierOperation("multiply_base"))
        assertEquals(AttributeModifier.Operation.MULTIPLY_SCALAR_1, EntityAttributeSupport.modifierOperation("multiply_total"))
        assertNull(EntityAttributeSupport.modifierOperation("unknown"))
    }

    @Test
    fun `modifier operations map to vanilla names without lossy conversion`() {
        assertEquals("add_value", EntityAttributeSupport.vanillaModifierOperation("add_number"))
        assertEquals("add_multiplied_base", EntityAttributeSupport.vanillaModifierOperation("multiply_base"))
        assertEquals("add_multiplied_total", EntityAttributeSupport.vanillaModifierOperation("multiply_total"))
        assertNull(EntityAttributeSupport.vanillaModifierOperation("unknown"))
    }

    @Test
    fun `modifier keys require namespaced syntax`() {
        assertTrue(EntityAttributeSupport.isModifierKey("minecraft:boost"))
        assertTrue(EntityAttributeSupport.isModifierKey("kantan:speed"))
        assertFalse(EntityAttributeSupport.isModifierKey("not a key"))
        assertFalse(EntityAttributeSupport.isModifierKey(""))
    }

    @Test
    fun `attribute ids accept syntax when registry is unavailable`() {
        // API単体テスト環境ではRegistryが初期化できないため、構文確認までを行います。
        assertTrue(EntityAttributeSupport.isAttributeId("minecraft:max_health"))
        assertFalse(EntityAttributeSupport.isAttributeId("not a key"))
        assertFalse(EntityAttributeSupport.isAttributeId(""))
    }
}
