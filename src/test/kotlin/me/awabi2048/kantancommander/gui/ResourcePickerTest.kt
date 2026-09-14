package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.ParticleSettings
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #28 の候補選択子画面の回帰テストです。
 *
 * Registry由来の一覧取得は [CommandDialogSpecs.registeredIds] を正本とし、
 * ここでは種別対応・ページング・アイコン解決だけを固定します。
 * サーバーなしでも検証できる範囲に留めます。
 */
class ResourcePickerTest {
    @Test
    fun `field keys map to picker kinds`() {
        assertEquals(GestureResourcePickerKind.ENTITY_TYPE, ResourcePicker.kindForFieldKey("summonEntity"))
        assertEquals(GestureResourcePickerKind.ENTITY_TYPE, ResourcePicker.kindForFieldKey("entityType"))
        assertEquals(GestureResourcePickerKind.PARTICLE, ResourcePicker.kindForFieldKey(ParticleSettings.PARAM_PARTICLE))
        assertEquals(GestureResourcePickerKind.SOUND, ResourcePicker.kindForFieldKey("sound"))
        assertEquals(GestureResourcePickerKind.EFFECT, ResourcePicker.kindForFieldKey("effect"))
        assertNull(ResourcePicker.kindForFieldKey("text"))
        assertNull(ResourcePicker.kindForFieldKey("volume"))
        assertNull(ResourcePicker.kindForFieldKey("block"))
    }

    @Test
    fun `paging splits ids into grid pages`() {
        assertEquals(24, ResourcePicker.PAGE_SIZE)
        assertEquals(1, ResourcePicker.pageCount(0))
        assertEquals(1, ResourcePicker.pageCount(24))
        assertEquals(2, ResourcePicker.pageCount(25))
        val ids = (1..50).map { "minecraft:mob$it" }
        assertEquals(24, ResourcePicker.pageItems(ids, 0).size)
        assertEquals(24, ResourcePicker.pageItems(ids, 1).size)
        assertEquals(2, ResourcePicker.pageItems(ids, 2).size)
        assertTrue(ResourcePicker.pageItems(ids, 3).isEmpty())
        assertTrue(ResourcePicker.pageItems(ids, -1).isEmpty())
        assertEquals("minecraft:mob1", ResourcePicker.pageItems(ids, 0).first())
        assertEquals("minecraft:mob25", ResourcePicker.pageItems(ids, 1).first())
    }

    @Test
    fun `display names are human readable`() {
        assertEquals("Cow", ResourcePicker.displayName(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:cow"))
        assertEquals("Zombified Piglin", ResourcePicker.displayName(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:zombified_piglin"))
        assertEquals("Flame", ResourcePicker.displayName(GestureResourcePickerKind.PARTICLE, "FLAME"))
    }

    @Test
    fun `icons resolve to representative materials with fallback`() {
        // 対応表にある候補は代表素材へ解決します。
        assertEquals(Material.COW_SPAWN_EGG, ResourcePickerIcons.resolve(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:cow"))
        assertEquals(Material.ARMOR_STAND, ResourcePickerIcons.resolve(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:armor_stand"))
        // Mannequinにスポーンエッグはないため頭で表示する。
        assertEquals(Material.PLAYER_HEAD, ResourcePickerIcons.resolve(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:mannequin"))
        assertEquals(Material.BLAZE_POWDER, ResourcePickerIcons.resolve(GestureResourcePickerKind.PARTICLE, "FLAME"))
        assertEquals(Material.NOTE_BLOCK, ResourcePickerIcons.resolve(GestureResourcePickerKind.SOUND, "minecraft:block.note_block.harp"))
        assertEquals(Material.MUSIC_DISC_13, ResourcePickerIcons.resolve(GestureResourcePickerKind.SOUND, "minecraft:music_disc.13"))
        // 対応表にない候補もフォールバック表示があります。
        assertEquals(
            ResourcePickerIcons.fallback(GestureResourcePickerKind.ENTITY_TYPE),
            ResourcePickerIcons.resolve(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:unknown_future_mob"),
        )
        assertEquals(
            ResourcePickerIcons.fallback(GestureResourcePickerKind.PARTICLE),
            ResourcePickerIcons.resolve(GestureResourcePickerKind.PARTICLE, "UNKNOWN_PARTICLE"),
        )
        assertEquals(
            ResourcePickerIcons.fallback(GestureResourcePickerKind.EFFECT),
            ResourcePickerIcons.resolve(GestureResourcePickerKind.EFFECT, "minecraft:unknown_effect"),
        )
        assertEquals(
            ResourcePickerIcons.fallback(GestureResourcePickerKind.SOUND),
            ResourcePickerIcons.resolve(GestureResourcePickerKind.SOUND, "custompack:weird_sound"),
        )
    }

    @Test
    fun `suggestions stay within the shared registry boundary`() {
        // Dialog候補検索とPickerは同じ取得境界を使います。
        // サーバーなしでは空列ですが、契約（部分集合・空欄空列）は維持します。
        listOf("summonEntity", "sound", "effect", ParticleSettings.PARAM_PARTICLE).forEach { fieldKey ->
            val ids = CommandDialogSpecs.registeredIds(fieldKey)
            val found = CommandDialogSpecs.suggestions(fieldKey, "xyz-no-such-id")
            assertTrue(ids.containsAll(found), fieldKey)
            assertTrue(CommandDialogSpecs.suggestions(fieldKey, "   ").isEmpty(), fieldKey)
        }
        assertTrue(CommandDialogSpecs.registeredIds("text").isEmpty())
    }
}
