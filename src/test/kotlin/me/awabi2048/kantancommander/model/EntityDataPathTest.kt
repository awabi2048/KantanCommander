package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 統合選択IDとタグ一覧符号化のテストです。
 *
 * 旧3択キーは保存せず、単一IDで設定対象を一意に表すこと、
 * タグ一覧の符号化・重複検出を固定します。
 */
class EntityDataPathTest {
    @Test
    fun `tag path round trips`() {
        assertEquals(EntityDataPath.Parsed.Tag, EntityDataPath.parse(EntityDataPath.TAG))
        assertEquals(EntityDataPath.Kind.TAG, EntityDataPath.kindOf(EntityDataPath.TAG))
    }

    @Test
    fun `attribute path carries the attribute id`() {
        val parsed = EntityDataPath.parse(EntityDataPath.attributePath("minecraft:max_health"))
        assertEquals(EntityDataPath.Parsed.Attribute("minecraft:max_health"), parsed)
        assertEquals(EntityDataPath.Kind.ATTRIBUTE, EntityDataPath.kindOf(EntityDataPath.attributePath("minecraft:max_health")))
        assertEquals("minecraft:max_health", EntityDataPath.attributeIdOf(EntityDataPath.attributePath("minecraft:max_health")))
        assertNull(EntityDataPath.propertyIdOf(EntityDataPath.attributePath("minecraft:max_health")))
    }

    @Test
    fun `property path carries the property id`() {
        val parsed = EntityDataPath.parse(EntityDataPath.propertyPath("gravity"))
        assertEquals(EntityDataPath.Parsed.Property("gravity"), parsed)
        assertEquals(EntityDataPath.Kind.PROPERTY, EntityDataPath.kindOf(EntityDataPath.propertyPath("gravity")))
        assertEquals("gravity", EntityDataPath.propertyIdOf(EntityDataPath.propertyPath("gravity")))
        assertNull(EntityDataPath.attributeIdOf(EntityDataPath.propertyPath("gravity")))
    }

    @Test
    fun `blank and unknown paths are unset`() {
        assertNull(EntityDataPath.parse(""))
        assertNull(EntityDataPath.parse("dance"))
        assertNull(EntityDataPath.parse("attribute:"))
        assertNull(EntityDataPath.parse("property:"))
        assertNull(EntityDataPath.kindOf(""))
    }

    @Test
    fun `tag entries encode and decode`() {
        val entries = listOf(
            EntityTagEntries.Entry(EntityTagEntries.MODE_ADD, "kc_test"),
            EntityTagEntries.Entry(EntityTagEntries.MODE_REMOVE, "kc_old"),
        )
        val encoded = EntityTagEntries.encode(entries)
        assertEquals(entries, EntityTagEntries.parse(encoded))
        assertTrue(EntityTagEntries.isValid(entries))
    }

    @Test
    fun `tag entries reject blanks duplicates and malformed tokens`() {
        assertTrue(EntityTagEntries.parse("").orEmpty().isEmpty())
        assertNull(EntityTagEntries.parse("add"))
        assertNull(EntityTagEntries.parse("fly=kc_test"))
        assertNull(EntityTagEntries.parse("add="))
        assertFalse(EntityTagEntries.isValid(emptyList()))
        assertFalse(
            EntityTagEntries.isValid(
                listOf(
                    EntityTagEntries.Entry(EntityTagEntries.MODE_ADD, "kc_test"),
                    EntityTagEntries.Entry(EntityTagEntries.MODE_REMOVE, "kc_test"),
                ),
            ),
        )
        assertFalse(
            EntityTagEntries.isValid(
                listOf(EntityTagEntries.Entry(EntityTagEntries.MODE_ADD, "has space")),
            ),
        )
    }

    @Test
    fun `integer properties validate their ranges`() {
        val size = EntityDataSchema.definition(EntityDataSchema.PROPERTY_SLIME_SIZE)
        assertEquals(EntityPropertyValueKind.INTEGER, size?.valueKind)
        assertEquals(EntityPropertyValue.IntegerValue(4), EntityDataSchema.parse(EntityDataSchema.PROPERTY_SLIME_SIZE, "4"))
        assertNull(EntityDataSchema.parse(EntityDataSchema.PROPERTY_SLIME_SIZE, "0"))
        assertNull(EntityDataSchema.parse(EntityDataSchema.PROPERTY_SLIME_SIZE, "abc"))
        assertEquals(EntityPropertyValue.IntegerValue(100), EntityDataSchema.parse(EntityDataSchema.PROPERTY_BEE_ANGER, "100"))
        assertNull(EntityDataSchema.parse(EntityDataSchema.PROPERTY_BEE_ANGER, "-1"))
    }

    @Test
    fun `new entity properties resolve their definitions`() {
        // 固有項目の定義漏れを検出します。表示・検証・実行はこの定義を共有します。
        val expected = listOf(
            EntityDataSchema.PROPERTY_AGEABLE_BABY,
            EntityDataSchema.PROPERTY_SHEEP_COLOR,
            EntityDataSchema.PROPERTY_SHEEP_SHEARED,
            EntityDataSchema.PROPERTY_SLIME_SIZE,
            EntityDataSchema.PROPERTY_CREEPER_POWERED,
            EntityDataSchema.PROPERTY_SITTABLE_SITTING,
            EntityDataSchema.PROPERTY_COLLAR_COLOR,
            EntityDataSchema.PROPERTY_CAT_TYPE,
            EntityDataSchema.PROPERTY_AXOLOTL_VARIANT,
            EntityDataSchema.PROPERTY_FOX_TYPE,
            EntityDataSchema.PROPERTY_FROG_VARIANT,
            EntityDataSchema.PROPERTY_VILLAGER_PROFESSION,
            EntityDataSchema.PROPERTY_ZOMBIE_BABY,
            EntityDataSchema.PROPERTY_PIG_SADDLE,
            EntityDataSchema.PROPERTY_PUFFER_STATE,
            EntityDataSchema.PROPERTY_VILLAGER_TYPE,
            EntityDataSchema.PROPERTY_MOOSHROOM_VARIANT,
            EntityDataSchema.PROPERTY_HORSE_COLOR,
            EntityDataSchema.PROPERTY_HORSE_STYLE,
            EntityDataSchema.PROPERTY_LLAMA_COLOR,
            EntityDataSchema.PROPERTY_PARROT_VARIANT,
            EntityDataSchema.PROPERTY_SHULKER_COLOR,
            EntityDataSchema.PROPERTY_BOAT_TYPE,
            EntityDataSchema.PROPERTY_BEE_NECTAR,
            EntityDataSchema.PROPERTY_BEE_ANGER,
        )
        expected.forEach {
            assertTrue(EntityDataSchema.definition(it) != null, it)
        }
        // 却下項目は定義しません。
        assertNull(EntityDataSchema.definition("villager_level"))
        assertNull(EntityDataSchema.definition("phantom_size"))
    }

    @Test
    fun `enum mappings reject unknown ids without touching bukkit registries`() {
        // BukkitのKeyed列挙（Cat.Type等）は単体試験環境で初期化できないため、
        // 不明IDの拒否のみを検証します。正常系の対応は実サーバー起動テストで確認します。
        assertEquals(org.bukkit.DyeColor.RED, EntityDataSchema.dyeColor("red"))
        assertNull(EntityDataSchema.dyeColor("purple-ish"))
        assertNull(EntityDataSchema.catType("purple-ish"))
        assertNull(EntityDataSchema.axolotlVariant("purple-ish"))
        assertNull(EntityDataSchema.foxType("purple-ish"))
        assertNull(EntityDataSchema.frogVariant("purple-ish"))
        assertNull(EntityDataSchema.villagerProfession("purple-ish"))
        assertNull(EntityDataSchema.villagerType("purple-ish"))
        assertNull(EntityDataSchema.mooshroomVariant("purple-ish"))
        assertNull(EntityDataSchema.horseColor("purple-ish"))
        assertNull(EntityDataSchema.horseStyle("purple-ish"))
        assertNull(EntityDataSchema.llamaColor("purple-ish"))
        assertNull(EntityDataSchema.parrotVariant("purple-ish"))
        assertNull(EntityDataSchema.boatType("purple-ish"))
    }
}
