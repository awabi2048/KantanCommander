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
    fun `boolean toggle flips strictly and treats unset as false`() {
        // 両GUIのトグル操作で同じ計算を使うため、境界値を固定します。
        assertEquals("true", EntityDataSchema.toggledBooleanValue("false"))
        assertEquals("false", EntityDataSchema.toggledBooleanValue("true"))
        // 未設定・壊れた値はfalseとして扱いtrueへ進めます。
        // 厳密真偽値ではない大文字Trueも未設定扱いです。
        assertEquals("true", EntityDataSchema.toggledBooleanValue(""))
        assertEquals("true", EntityDataSchema.toggledBooleanValue("yes"))
        assertEquals("true", EntityDataSchema.toggledBooleanValue("True"))
    }

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
        assertTrue(ids.contains(EntityDataSchema.PROPERTY_ALLOW_FLIGHT))
        assertFalse(ids.contains("armor_stand_arms"))
        assertFalse(ids.contains("rabbit_type"))
    }

    @Test
    fun `flight permission is a player-only boolean property`() {
        val definition = EntityDataSchema.definition(EntityDataSchema.PROPERTY_ALLOW_FLIGHT)
        assertNotNull(definition)
        assertEquals(EntityPropertyValueKind.BOOLEAN, definition!!.valueKind)
        assertEquals(setOf(EntityType.PLAYER.name), definition.entityTypes)
        assertEquals(
            EntityPropertySupport.STATICALLY_SUPPORTED,
            EntityDataSchema.staticSupport(EntityType.PLAYER, EntityDataSchema.PROPERTY_ALLOW_FLIGHT),
        )
        assertEquals(
            EntityPropertySupport.STATICALLY_UNSUPPORTED,
            EntityDataSchema.staticSupport(EntityType.ZOMBIE, EntityDataSchema.PROPERTY_ALLOW_FLIGHT),
        )
        assertEquals(
            EntityPropertyValue.BooleanValue(true),
            EntityDataSchema.parse(EntityDataSchema.PROPERTY_ALLOW_FLIGHT, "true"),
        )
        assertNull(EntityDataSchema.parse(EntityDataSchema.PROPERTY_ALLOW_FLIGHT, "yes"))
    }

    @Test
    fun `armor stand shows its own properties`() {
        val ids = EntityDataSchema.propertiesFor(EntityType.ARMOR_STAND).map { it.id }
        assertTrue(ids.containsAll(listOf("armor_stand_arms", "armor_stand_small", "armor_stand_marker", "armor_stand_base_plate")))
        assertFalse(ids.contains("rabbit_type"))
        // ArmorStandの部位ポーズ等の複合値は初期対象に含めません（#10の判断を固定）。
        assertTrue(ids.none { it.startsWith("armor_stand_pose") || it.startsWith("pose") })
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
    fun `mannequin shows its own properties`() {
        val ids = EntityDataSchema.propertiesFor(EntityType.MANNEQUIN).map { it.id }
        assertTrue(ids.containsAll(listOf(
            "mannequin_profile_name",
            "mannequin_description",
            "mannequin_immovable",
            "mannequin_main_hand",
            "mannequin_pose",
            "mannequin_skin_cape",
            "mannequin_skin_jacket",
            "mannequin_skin_left_sleeve",
            "mannequin_skin_right_sleeve",
            "mannequin_skin_left_pants",
            "mannequin_skin_right_pants",
            "mannequin_skin_hat",
        )))
        assertFalse(ids.contains("rabbit_type"))
        assertFalse(ids.contains("armor_stand_arms"))
    }

    @Test
    fun `mannequin main hand choices map to left and right`() {
        val definition = EntityDataSchema.definition("mannequin_main_hand")
        assertNotNull(definition)
        assertEquals(EntityPropertyValueKind.ENUM, definition!!.valueKind)
        assertEquals(setOf("left", "right"), definition.enumChoices.map { it.id }.toSet())
        assertEquals(EntityPropertyValue.EnumValue("left"), EntityDataSchema.parse("mannequin_main_hand", "left"))
        assertNull(EntityDataSchema.parse("mannequin_main_hand", "center"))
    }

    @Test
    fun `mannequin pose choices are selected from child screen ids`() {
        val definition = EntityDataSchema.definition("mannequin_pose")
        assertNotNull(definition)
        assertEquals(EntityPropertyValueKind.ENUM, definition!!.valueKind)
        assertEquals(
            setOf("standing", "sneaking", "swimming", "sleeping", "sitting", "fall_flying", "spin_attack"),
            definition.enumChoices.map { it.id }.toSet(),
        )
        assertEquals(EntityPropertyValue.EnumValue("standing"), EntityDataSchema.parse("mannequin_pose", "standing"))
        assertNull(EntityDataSchema.parse("mannequin_pose", "dancing"))
        // 選択肢IDとBukkitのPoseの対応を固定する。
        assertEquals(org.bukkit.entity.Pose.STANDING, EntityDataSchema.mannequinPose("standing"))
        assertEquals(org.bukkit.entity.Pose.SNEAKING, EntityDataSchema.mannequinPose("sneaking"))
        assertNull(EntityDataSchema.mannequinPose("dancing"))
    }

    @Test
    fun `mannequin profile name accepts only resolvable player names`() {
        assertTrue(EntityDataSchema.isMannequinProfileName("Notch"))
        assertTrue(EntityDataSchema.isMannequinProfileName("awabi2048"))
        assertTrue(EntityDataSchema.isMannequinProfileName("a_b"))
        assertFalse(EntityDataSchema.isMannequinProfileName(""))
        assertFalse(EntityDataSchema.isMannequinProfileName("ab"))
        assertFalse(EntityDataSchema.isMannequinProfileName("has space"))
        assertFalse(EntityDataSchema.isMannequinProfileName("abcdefghijklmnopq"))
    }

    @Test
    fun `mannequin description is an empty-default text property`() {
        val definition = EntityDataSchema.definition("mannequin_description")
        assertNotNull(definition)
        assertEquals(EntityPropertyValueKind.TEXT, definition!!.valueKind)
        assertEquals(EntityPropertyValue.TextValue(""), EntityDataSchema.parse("mannequin_description", ""))
        assertEquals(
            EntityPropertySupport.STATICALLY_SUPPORTED,
            EntityDataSchema.staticSupport(EntityType.MANNEQUIN, "mannequin_description"),
        )
        assertEquals(
            EntityPropertySupport.STATICALLY_UNSUPPORTED,
            EntityDataSchema.staticSupport(EntityType.PLAYER, "mannequin_description"),
        )
    }

    @Test
    fun `mannequin properties are scoped to mannequin type`() {
        assertEquals(
            EntityPropertySupport.STATICALLY_SUPPORTED,
            EntityDataSchema.staticSupport(EntityType.MANNEQUIN, "mannequin_pose"),
        )
        assertEquals(
            EntityPropertySupport.STATICALLY_SUPPORTED,
            EntityDataSchema.staticSupport(EntityType.MANNEQUIN, "mannequin_profile_name"),
        )
        assertEquals(
            EntityPropertySupport.STATICALLY_UNSUPPORTED,
            EntityDataSchema.staticSupport(EntityType.PLAYER, "mannequin_pose"),
        )
        assertEquals(
            EntityPropertySupport.STATICALLY_UNSUPPORTED,
            EntityDataSchema.staticSupport(EntityType.ZOMBIE, "mannequin_immovable"),
        )
        assertEquals(
            EntityPropertySupport.RUNTIME_DEPENDENT,
            EntityDataSchema.staticSupport(null, "mannequin_pose"),
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
