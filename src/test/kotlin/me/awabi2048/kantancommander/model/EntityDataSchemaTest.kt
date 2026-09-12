package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.bukkit.entity.EntityType

/**
 * issue #10 の型付きEntity設定スキーマの検証です。
 *
 * 任意NBTではなく限定定義の項目だけを扱うこと、EntityType確定時のみ固有項目を
 * 表示すること、値の型・候補を項目ごとに固定することを固定します。
 * Paper Registryを必要としない範囲で検証し、実行時解決は別途検証します。
 */
class EntityDataSchemaTest {
    @Test
    fun `unknown property id resolves to null without fallback`() {
        assertNull(EntityDataSchema.definition("no_such_property"))
        assertNull(EntityDataSchema.definition(""))
        assertNull(EntityDataSchema.parse("no_such_property", "true"))
    }

    @Test
    fun `unknown entity type shows only universal properties`() {
        val ids = EntityDataSchema.propertiesFor(null).map { it.id }
        assertTrue(ids.containsAll(listOf("silent", "invulnerable", "gravity", "glowing", "persistent", "custom_name")))
        assertFalse(ids.contains("ai"), "AIはLivingEntity向けのため種類不明時は表示しない")
        assertFalse(ids.contains("armor_stand_arms"))
        assertFalse(ids.contains("rabbit_type"))
    }

    @Test
    fun `player target shows universal and living properties`() {
        val ids = EntityDataSchema.propertiesFor(EntityType.PLAYER).map { it.id }
        assertTrue(ids.contains("ai"))
        assertTrue(ids.contains("silent"))
        assertFalse(ids.contains("armor_stand_arms"))
        assertFalse(ids.contains("rabbit_type"))
    }

    @Test
    fun `armor stand shows its own properties`() {
        val ids = EntityDataSchema.propertiesFor(EntityType.ARMOR_STAND).map { it.id }
        assertTrue(ids.containsAll(listOf("armor_stand_arms", "armor_stand_small", "armor_stand_marker", "armor_stand_base_plate")))
        assertFalse(ids.contains("rabbit_type"))
    }

    @Test
    fun `rabbit shows its own enum property`() {
        val definition = EntityDataSchema.definition("rabbit_type")
        assertNotNull(definition)
        assertEquals(EntityPropertyValueKind.ENUM, definition!!.valueKind)
        assertEquals(
            setOf("brown", "white", "black", "black_and_white", "gold", "salt_and_pepper", "killer_bunny"),
            definition.enumChoices.map { it.id }.toSet(),
        )
    }

    @Test
    fun `boolean values parse strictly`() {
        assertEquals(EntityPropertyValue.BooleanValue(true), EntityDataSchema.parse("ai", "true"))
        assertEquals(EntityPropertyValue.BooleanValue(false), EntityDataSchema.parse("gravity", "false"))
        assertNull(EntityDataSchema.parse("ai", "yes"))
        assertNull(EntityDataSchema.parse("ai", "1"))
        assertNull(EntityDataSchema.parse("ai", ""))
    }

    @Test
    fun `enum values accept only defined choices`() {
        assertEquals(EntityPropertyValue.EnumValue("brown"), EntityDataSchema.parse("rabbit_type", "brown"))
        assertEquals(EntityPropertyValue.EnumValue("killer_bunny"), EntityDataSchema.parse("rabbit_type", "killer_bunny"))
        assertNull(EntityDataSchema.parse("rabbit_type", "BROWN"))
        assertNull(EntityDataSchema.parse("rabbit_type", "7"))
        assertNull(EntityDataSchema.parse("rabbit_type", ""))
    }

    @Test
    fun `text values pass through for custom name`() {
        assertEquals(EntityPropertyValue.TextValue("hello"), EntityDataSchema.parse("custom_name", "hello"))
        assertEquals(EntityPropertyValue.TextValue(""), EntityDataSchema.parse("custom_name", ""))
    }

    @Test
    fun `static support separates impossible from runtime dependent`() {
        assertEquals(
            EntityPropertySupport.STATICALLY_UNSUPPORTED,
            EntityDataSchema.staticSupport(EntityType.PLAYER, "rabbit_type"),
        )
        assertEquals(
            EntityPropertySupport.STATICALLY_UNSUPPORTED,
            EntityDataSchema.staticSupport(EntityType.ZOMBIE, "armor_stand_arms"),
        )
        assertEquals(
            EntityPropertySupport.STATICALLY_SUPPORTED,
            EntityDataSchema.staticSupport(EntityType.PLAYER, "ai"),
        )
        assertEquals(
            EntityPropertySupport.STATICALLY_SUPPORTED,
            EntityDataSchema.staticSupport(EntityType.ZOMBIE, "gravity"),
        )
        assertEquals(
            EntityPropertySupport.STATICALLY_UNSUPPORTED,
            EntityDataSchema.staticSupport(EntityType.ZOMBIE, "no_such_property"),
        )
        assertEquals(
            EntityPropertySupport.RUNTIME_DEPENDENT,
            EntityDataSchema.staticSupport(null, "ai"),
        )
        assertEquals(
            EntityPropertySupport.RUNTIME_DEPENDENT,
            EntityDataSchema.staticSupport(null, "rabbit_type"),
        )
    }

    @Test
    fun `known entity type resolves from player kinds without registry`() {
        assertEquals(
            EntityType.PLAYER,
            EntityDataSchema.knownEntityType(TargetSpec(TargetKind.NEAREST_PLAYER)),
        )
        assertEquals(
            EntityType.PLAYER,
            EntityDataSchema.knownEntityType(TargetSpec(TargetKind.ALL_PLAYERS)),
        )
        assertNull(EntityDataSchema.knownEntityType(TargetSpec(TargetKind.NEAREST_ENTITY)))
        assertNull(EntityDataSchema.knownEntityType(null))
    }
}
