package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.ParticleSettings
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.EntityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #28 の候補選択子画面の回帰テストです。
 *
 * Registry由来の一覧取得は [CommandDialogSpecs.registeredIds] を正本とし、
 * ここでは種別対応・コマンドID表示・ページング・アイコン解決を固定します。
 * サーバーなしでも検証できる範囲に留めます。
 */
class ResourcePickerTest {
    @Test
    fun `field keys map to picker kinds`() {
        assertEquals(GestureResourcePickerKind.ENTITY_TYPE, ResourcePicker.kindForFieldKey("summonEntity"))
        assertEquals(GestureResourcePickerKind.ENTITY_TYPE, ResourcePicker.kindForFieldKey("entityType"))
        assertEquals(GestureResourcePickerKind.PARTICLE, ResourcePicker.kindForFieldKey(ParticleSettings.PARAM_PARTICLE))
        assertEquals(GestureResourcePickerKind.SOUND, ResourcePicker.kindForFieldKey("sound"))
        assertEquals(GestureResourcePickerKind.SOUND, ResourcePicker.kindForFieldKey("soundSetting"))
        assertEquals(GestureResourcePickerKind.EFFECT, ResourcePicker.kindForFieldKey("effect"))
        assertNull(ResourcePicker.kindForFieldKey("text"))
        assertNull(ResourcePicker.kindForFieldKey("volume"))
        assertNull(ResourcePicker.kindForFieldKey("block"))
    }

    @Test
    fun `preset display contains only command ids`() {
        GestureResourcePickerKind.entries.forEach { kind ->
            ResourcePresets.ids(kind).forEach { id ->
                val expected = if (kind == GestureResourcePickerKind.PARTICLE) {
                    ResourcePicker.particleDisplayId(id)
                } else {
                    id
                }
                assertEquals(expected, ResourcePresetDisplay.commandId(kind, id))
            }
        }
        assertEquals("sound", ResourcePicker.valueFieldKey("soundSetting"))
        assertEquals(
            "minecraft:flame",
            ResourcePresetDisplay.commandId(GestureResourcePickerKind.PARTICLE, "FLAME"),
        )
        assertEquals(
            "minecraft:block.note_block.harp",
            ResourcePresetDisplay.commandId(GestureResourcePickerKind.SOUND, "minecraft:block.note_block.harp"),
        )
    }

    @Test
    fun `paging splits ids into grid pages`() {
        assertEquals(32, ResourcePicker.PAGE_SIZE)
        assertEquals(1, ResourcePicker.pageCount(0))
        assertEquals(1, ResourcePicker.pageCount(32))
        assertEquals(2, ResourcePicker.pageCount(33))
        val ids = (1..70).map { "minecraft:mob$it" }
        assertEquals(32, ResourcePicker.pageItems(ids, 0).size)
        assertEquals(32, ResourcePicker.pageItems(ids, 1).size)
        assertEquals(6, ResourcePicker.pageItems(ids, 2).size)
        assertTrue(ResourcePicker.pageItems(ids, 3).isEmpty())
        assertTrue(ResourcePicker.pageItems(ids, -1).isEmpty())
        assertEquals("minecraft:mob1", ResourcePicker.pageItems(ids, 0).first())
        assertEquals("minecraft:mob33", ResourcePicker.pageItems(ids, 1).first())
    }

    @Test
    fun `presets fit on a single picker page`() {
        // ピッカー表示はプリセットに限定し、ページ送りなしで視認性を保ちます。
        GestureResourcePickerKind.entries.forEach { kind ->
            val ids = ResourcePresets.ids(kind)
            assertTrue(ids.isNotEmpty(), kind.name)
            assertTrue(ids.size <= ResourcePicker.PAGE_SIZE, kind.name)
            assertEquals(ids.size, ids.distinct().size, kind.name)
            assertTrue(ids.all(String::isNotBlank), kind.name)
        }
        assertEquals(24, ResourcePresets.ids(GestureResourcePickerKind.ENTITY_TYPE).size)
        assertEquals(32, ResourcePresets.ids(GestureResourcePickerKind.PARTICLE).size)
        assertEquals(24, ResourcePresets.ids(GestureResourcePickerKind.EFFECT).size)
        assertEquals(24, ResourcePresets.ids(GestureResourcePickerKind.SOUND).size)
    }

    @Test
    fun `particle presets cover data-required kinds except exclusions`() {
        // 詳細設定を要するパーティクルはプリセットで直接選べます。
        // 指定除外の種類はプリセットへ出さず、ID入力＋詳細設定ボタンの経路に限定します。
        val excluded = setOf(
            "INSTANT_EFFECT",
            "ENTITY_EFFECT",
            "TINTED_LEAVES",
            "BLOCK",
            "DUST_PILLAR",
            "BLOCK_MARKER",
            "FALLING_DUST",
            "DRAGON_BREATH",
            "SCULK_CHARGE",
            "SHRIEK",
            "VIBRATION",
            "TRAIL",
            // MC 26.2で追加された間欠泉系は詳細データが必須のため、ID入力＋詳細設定経路に限定します。
            "GEYSER",
            "GEYSER_PLUME",
            "GEYSER_BASE",
            "GEYSER_POOF",
        )
        val presets = ResourcePresets.ids(GestureResourcePickerKind.PARTICLE).toSet()
        excluded.forEach { assertFalse(it in presets, it) }
        Particle.entries
            .filter { ParticleSettings.requiresData(it) }
            .map { it.name }
            .filter { it !in excluded }
            .forEach { assertTrue(it in presets, it) }
    }

    @Test
    fun `presets use valid value forms`() {
        // Namespaced ID系は小文字の正規形、パーティクルはBukkit enum名のまま保持します。
        ResourcePresets.ids(GestureResourcePickerKind.ENTITY_TYPE).forEach { id ->
            assertEquals(id.lowercase(), id, id)
        }
        ResourcePresets.ids(GestureResourcePickerKind.EFFECT).forEach { id ->
            assertEquals(id.lowercase(), id, id)
        }
        ResourcePresets.ids(GestureResourcePickerKind.SOUND).forEach { id ->
            assertEquals(id.lowercase(), id, id)
        }
        ResourcePresets.ids(GestureResourcePickerKind.PARTICLE).forEach { id ->
            runCatching { Particle.valueOf(id) }.getOrNull() ?: error("unknown particle preset: $id")
        }
        ResourcePresets.ids(GestureResourcePickerKind.ENTITY_TYPE).forEach { id ->
            val path = id.substringAfter(':').uppercase()
            runCatching { EntityType.valueOf(path) }.getOrNull() ?: error("unknown entity preset: $id")
        }
    }

    @Test
    fun `inline element ids round trip field keys and ids`() {
        // 親タブ直下の操作面IDは、設定項目キーと候補IDを欠落なく運びます。
        // 候補ID自体のコロンはID側に残し、最初の区切りだけを項目キーとします。
        val pick = InlineResourcePicker.pickElementId("sound", "minecraft:block.note_block.harp")
        assertEquals("sound" to "minecraft:block.note_block.harp", InlineResourcePicker.parsePick(pick))
        val particle = InlineResourcePicker.pickElementId("particle", "FLAME")
        assertEquals("particle" to "FLAME", InlineResourcePicker.parsePick(particle))
        assertEquals("effect", InlineResourcePicker.parseInput(InlineResourcePicker.inputElementId("effect")))
        assertEquals("particle", InlineResourcePicker.parseDetail(InlineResourcePicker.detailElementId("particle")))
        // 不正形式は受け付けません。
        assertNull(InlineResourcePicker.parsePick("lower-inline-pick:"))
        assertNull(InlineResourcePicker.parsePick("lower-inline-pick:sound:"))
        assertNull(InlineResourcePicker.parsePick("lower-inline-pick::minecraft:pig"))
        assertNull(InlineResourcePicker.parsePick("lower-inline-input:sound"))
        assertNull(InlineResourcePicker.parsePick("lower-resource-pick:minecraft:pig"))
        assertNull(InlineResourcePicker.parseInput("lower-inline-input:"))
        assertNull(InlineResourcePicker.parseInput("lower-inline-pick:sound:minecraft:pig"))
        assertNull(InlineResourcePicker.parseDetail("lower-inline-detail:"))
        assertNull(InlineResourcePicker.parseDetail("lower-inline-input:particle"))
        assertNull(InlineResourcePicker.parseInput(InlineResourcePicker.detailElementId("particle")))
        // 選択・入力・詳細設定の接頭辞は互いに干渉しません。
        assertEquals(8, InlineResourcePicker.COLUMNS)
    }

    @Test
    fun `particle vanilla ids cover every preset explicitly`() {
        // 推測変換は行わず、明示表がプリセットと一致することを固定します。
        assertEquals(
            ResourcePresets.ids(GestureResourcePickerKind.PARTICLE).toSet(),
            ResourcePicker.PARTICLE_VANILLA_IDS.keys,
        )
        ResourcePicker.PARTICLE_VANILLA_IDS.forEach { (enumName, vanillaId) ->
            assertEquals("minecraft:${enumName.lowercase()}", vanillaId, enumName)
        }
        assertEquals("minecraft:flame", ResourcePicker.particleDisplayId("FLAME"))
        assertEquals("UNKNOWN_PARTICLE", ResourcePicker.particleDisplayId("UNKNOWN_PARTICLE"))
    }

    @Test
    fun `preset icons are unique within each picker grid`() {
        // 一覧内で重複表示にならないことを固定します。
        GestureResourcePickerKind.entries.forEach { kind ->
            val materials = ResourcePresets.ids(kind).map { id ->
                ResourcePickerIcons.resolve(kind, id)
            }
            assertEquals(materials.size, materials.distinct().size, kind.name)
        }
    }

    @Test
    fun `isPreset matches normalized current values`() {
        assertTrue(ResourcePresets.isPreset(GestureResourcePickerKind.SOUND, "minecraft:block.note_block.harp"))
        assertTrue(ResourcePresets.isPreset(GestureResourcePickerKind.SOUND, "MINECRAFT:BLOCK.NOTE_BLOCK.HARP"))
        assertFalse(ResourcePresets.isPreset(GestureResourcePickerKind.SOUND, "minecraft:block.note_block.didgeridoo"))
        assertTrue(ResourcePresets.isPreset(GestureResourcePickerKind.PARTICLE, "FLAME"))
        assertFalse(ResourcePresets.isPreset(GestureResourcePickerKind.PARTICLE, "FLAME_EXTRA"))
        assertFalse(ResourcePresets.isPreset(GestureResourcePickerKind.PARTICLE, ""))
        assertTrue(ResourcePresets.isPreset(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:pig"))
        assertFalse(ResourcePresets.isPreset(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:warden"))
        assertTrue(ResourcePresets.isPreset(GestureResourcePickerKind.EFFECT, "minecraft:speed"))
        assertFalse(ResourcePresets.isPreset(GestureResourcePickerKind.EFFECT, "minecraft:wind_charged"))
        assertFalse(ResourcePresets.isPreset(GestureResourcePickerKind.EFFECT, "   "))
    }

    @Test
    fun `icons resolve to representative materials with fallback`() {
        // 対応表にある候補は代表素材へ解決します。
        assertEquals(Material.COW_SPAWN_EGG, ResourcePickerIcons.resolve(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:cow"))
        assertEquals(Material.ARMOR_STAND, ResourcePickerIcons.resolve(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:armor_stand"))
        // Mannequinにスポーンエッグはないため頭で表示する。
        assertEquals(Material.PLAYER_HEAD, ResourcePickerIcons.resolve(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:mannequin"))
        assertEquals(Material.BLAZE_POWDER, ResourcePickerIcons.resolve(GestureResourcePickerKind.PARTICLE, "FLAME"))
        assertEquals(Material.FEATHER, ResourcePickerIcons.resolve(GestureResourcePickerKind.PARTICLE, "POOF"))
        assertEquals(Material.IRON_GOLEM_SPAWN_EGG, ResourcePickerIcons.resolve(GestureResourcePickerKind.ENTITY_TYPE, "minecraft:iron_golem"))
        assertEquals(Material.ARROW, ResourcePickerIcons.resolve(GestureResourcePickerKind.SOUND, "minecraft:entity.arrow.shoot"))
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
