package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.MyworldSpawnKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #29 のプリセット共有定義の回帰テストです。
 *
 * Inventory GUIとGesture GUIで同じ定義を使うため、ここで正規内容・
 * 正規化・選択済み表示・複数対応可否を固定します。
 */
class SettingPresetsTest {
    @Test
    fun `detail visit moves preset selection to detail side`() {
        // 通常時は形状一致でプリセット側を表示します。
        assertTrue(PresetSelectionDisplay.isPresetSelected(matched = true, configured = true, detailVisited = false))
        assertFalse(
            PresetSelectionDisplay.isDetailSelected(configured = true, unmatched = false, detailVisited = false),
        )
        // 詳細入室後は形状一致のままプリセット側を外し、詳細側を表示します。
        assertFalse(PresetSelectionDisplay.isPresetSelected(matched = true, configured = true, detailVisited = true))
        assertTrue(
            PresetSelectionDisplay.isDetailSelected(configured = true, unmatched = false, detailVisited = true),
        )
        // 未設定時はどちらも表示しません。
        assertFalse(PresetSelectionDisplay.isPresetSelected(matched = false, configured = false, detailVisited = true))
        assertFalse(
            PresetSelectionDisplay.isDetailSelected(configured = false, unmatched = true, detailVisited = true),
        )
        // 詳細で形状が崩れた場合は訪問記録なしでも詳細側を表示します。
        assertTrue(
            PresetSelectionDisplay.isDetailSelected(configured = true, unmatched = true, detailVisited = false),
        )
    }

    @Test
    fun `detail visit keys are independent per field and role`() {
        assertEquals(
            PresetSelectionDisplay.detailVisitKey("target", CommandSettingRole.NODE_TARGET),
            PresetSelectionDisplay.detailVisitKey("target", CommandSettingRole.NODE_TARGET),
        )
        assertTrue(
            PresetSelectionDisplay.detailVisitKey("target", CommandSettingRole.NODE_TARGET) !=
                PresetSelectionDisplay.detailVisitKey("destination", CommandSettingRole.DESTINATION),
        )
    }

    @Test
    fun `nearby presets default to distance 5`() {
        val players = SettingPresets.targetSpecFor(SettingPresets.TargetPreset.NEARBY_PLAYERS)
        assertEquals(TargetKind.NEARBY_PLAYERS, players.kind)
        assertEquals(SettingPresets.NEARBY_DISTANCE_DEFAULT, players.maximumDistance)

        val entities = SettingPresets.targetSpecFor(SettingPresets.TargetPreset.NEARBY_ENTITIES)
        assertEquals(TargetKind.NEARBY_ENTITIES, entities.kind)
        assertEquals(SettingPresets.NEARBY_DISTANCE_DEFAULT, entities.maximumDistance)
    }

    @Test
    fun `preset selection normalizes away detailed filters`() {
        val detailed = TargetSpec(
            TargetKind.NEARBY_PLAYERS,
            maximumDistance = 12.0,
            tag = "vip",
            limit = 3,
        )
        // 詳細付きは正規内容から外れるため選択済み表示にしません。
        assertNull(SettingPresets.matchTargetPreset(detailed))
        // プリセット選択時は正規仕様へ正規化します。
        val normalized = SettingPresets.targetSpecFor(SettingPresets.TargetPreset.NEARBY_PLAYERS)
        assertEquals(SettingPresets.TargetPreset.NEARBY_PLAYERS, SettingPresets.matchTargetPreset(normalized))
    }

    @Test
    fun `myworld spawn preset matches both spawn kinds`() {
        val preset = SettingPresets.positionSpecFor(SettingPresets.PositionPreset.MYWORLD_SPAWN)
        assertEquals(PositionKind.MYWORLD_SPAWN, preset.kind)
        assertEquals(MyworldSpawnKind.MEMBER, preset.myworldSpawn)
        assertEquals(SettingPresets.PositionPreset.MYWORLD_SPAWN, SettingPresets.matchPositionPreset(preset))

        // 旧来の単一種別（種別未設定）はメンバー用として互換移行します。
        val legacy = PositionSpec(PositionKind.MYWORLD_SPAWN)
        assertEquals(SettingPresets.PositionPreset.MYWORLD_SPAWN, SettingPresets.matchPositionPreset(legacy))

        // ゲスト用もプリセットに一致させ、子画面で種別を切り替えます。
        // 再クリックでメンバー用へ上書きしないための照合です。
        val guest = PositionSpec(PositionKind.MYWORLD_SPAWN, myworldSpawn = MyworldSpawnKind.GUEST)
        assertEquals(SettingPresets.PositionPreset.MYWORLD_SPAWN, SettingPresets.matchPositionPreset(guest))
    }

    @Test
    fun `changed sort orders leave template matching`() {
        // 並び順の変更は詳細設定のため、テンプレート一致から外れます。
        val furthest = TargetSpec(TargetKind.NEAREST_PLAYER, sort = TargetSort.FURTHEST)
        assertNull(SettingPresets.matchTargetPreset(furthest))
        val random = TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = 5.0, sort = TargetSort.RANDOM)
        assertNull(SettingPresets.matchTargetPreset(random))
        // 既定の最寄り順は引き続き一致します。
        assertEquals(
            SettingPresets.TargetPreset.NEAREST_PLAYER,
            SettingPresets.matchTargetPreset(TargetSpec(TargetKind.NEAREST_PLAYER)),
        )
        assertEquals(
            SettingPresets.TargetPreset.NEARBY_PLAYERS,
            SettingPresets.matchTargetPreset(
                TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = 5.0),
            ),
        )
    }

    @Test
    fun `detail conversion materializes explicit equivalents with provenance`() {
        val nearest = SettingPresets.expandPresetToDetail(TargetSpec(TargetKind.NEAREST_PLAYER))
        assertTrue(nearest.limit == 1)
        assertTrue(nearest.minimumDistance == 0.0)
        assertTrue(nearest.convertedFromPreset)
        // 無変更の間はプリセット選択表示が残ります。
        assertEquals(SettingPresets.TargetPreset.NEAREST_PLAYER, SettingPresets.matchTargetPreset(nearest))

        val nearby = SettingPresets.expandPresetToDetail(
            TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = 5.0),
        )
        assertNull(nearby.limit)
        assertTrue(nearby.minimumDistance == 0.0)
        assertTrue(nearby.convertedFromPreset)
        assertEquals(SettingPresets.TargetPreset.NEARBY_PLAYERS, SettingPresets.matchTargetPreset(nearby))

        // 非プリセットは変換しません。
        val detailed = TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = 12.0, tag = "vip")
        assertEquals(detailed, SettingPresets.expandPresetToDetail(detailed))

        // 冪等であり、変換済みへの再適用で値は変化しません。
        assertEquals(nearest, SettingPresets.expandPresetToDetail(nearest))
    }

    @Test
    fun `hand entered equivalents do not match presets`() {
        // 来歴なしの明示既定値はプリセット扱いにしません。
        assertNull(SettingPresets.matchTargetPreset(TargetSpec(TargetKind.NEAREST_PLAYER, limit = 1)))
        assertNull(SettingPresets.matchTargetPreset(TargetSpec(TargetKind.NEAREST_PLAYER, minimumDistance = 0.0)))
        assertNull(
            SettingPresets.matchTargetPreset(
                TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = 5.0, minimumDistance = 0.0),
            ),
        )
        // 詳細保存で来歴は消え、等価値でもプリセット扱いに戻りません。
        val converted = SettingPresets.expandPresetToDetail(TargetSpec(TargetKind.NEAREST_PLAYER))
        assertNull(SettingPresets.matchTargetPreset(SettingPresets.withoutPresetConversion(converted)))
    }

    @Test
    fun `position presets embed entity targets for set resolution`() {
        val nearest = SettingPresets.positionSpecFor(SettingPresets.PositionPreset.NEAREST_PLAYER)
        assertEquals(PositionKind.TARGET, nearest.kind)
        assertEquals(TargetKind.NEAREST_PLAYER, nearest.target?.kind)
        assertEquals(SettingPresets.PositionPreset.NEAREST_PLAYER, SettingPresets.matchPositionPreset(nearest))

        val control = SettingPresets.positionSpecFor(SettingPresets.PositionPreset.CONTROL_BLOCK)
        assertEquals(PositionKind.DISK, control.kind)
        assertEquals(SettingPresets.PositionPreset.CONTROL_BLOCK, SettingPresets.matchPositionPreset(control))
    }

    @Test
    fun `facing presets exclude copy orientation and legacy kinds`() {
        // 向き複写はプリセットではなく下段の汎用導線で扱います。
        assertNull(
            SettingPresets.matchFacingPreset(
                FacingSpec(FacingKind.COPY_ORIENTATION, target = TargetSpec(TargetKind.NEAREST_PLAYER)),
            ),
        )
        assertEquals(
            SettingPresets.FacingPreset.NEAREST_PLAYER,
            SettingPresets.matchFacingPreset(SettingPresets.facingSpecFor(SettingPresets.FacingPreset.NEAREST_PLAYER)),
        )
        assertEquals(
            SettingPresets.FacingPreset.NUMERIC,
            SettingPresets.matchFacingPreset(SettingPresets.facingSpecFor(SettingPresets.FacingPreset.NUMERIC)),
        )
    }

    @Test
    fun `multiplicity policy is shared across guis`() {
        assertEquals(
            PositionMultiplicity.SINGLE,
            MultiPositionPolicy.multiplicityFor(CommandSettingRole.DESTINATION),
        )
        assertEquals(
            PositionMultiplicity.SINGLE,
            MultiPositionPolicy.multiplicityFor(CommandSettingRole.BLOCK_FROM),
        )
        assertEquals(
            PositionMultiplicity.SINGLE,
            MultiPositionPolicy.multiplicityFor(CommandSettingRole.PARTICLE_END),
        )
        assertEquals(
            PositionMultiplicity.MULTI,
            MultiPositionPolicy.multiplicityFor(CommandSettingRole.SUMMON_POSITION),
        )
        assertEquals(
            PositionMultiplicity.MULTI,
            MultiPositionPolicy.multiplicityFor(CommandSettingRole.EXECUTION_POSITION),
        )
        assertEquals(
            PositionMultiplicity.MULTI,
            MultiPositionPolicy.multiplicityFor(CommandSettingRole.CONDITION_POSITION),
        )
        // 単一限定では複数になり得る対象由来だけが制約違反であり、
        // 静的0..1の指定は取得元にかかわらず許可します。
        assertTrue(
            MultiPositionPolicy.isUnsupportedMulti(
                PositionSpec(PositionKind.TARGET, target = TargetSpec(TargetKind.NEARBY_PLAYERS)),
                CommandSettingRole.BLOCK_FROM,
            ),
        )
        assertTrue(
            MultiPositionPolicy.isUnsupportedMulti(
                PositionSpec(PositionKind.TARGET),
                CommandSettingRole.BLOCK_FROM,
            ),
        )
        assertFalse(
            MultiPositionPolicy.isUnsupportedMulti(
                PositionSpec(PositionKind.TARGET, target = TargetSpec(TargetKind.NEAREST_PLAYER)),
                CommandSettingRole.BLOCK_FROM,
            ),
        )
        assertTrue(
            !MultiPositionPolicy.isUnsupportedMulti(
                PositionSpec(PositionKind.TARGET, target = TargetSpec(TargetKind.NEAREST_PLAYER)),
                CommandSettingRole.SUMMON_POSITION,
            ),
        )
    }

    @Test
    fun `static single targets allow zero or one entity`() {
        assertTrue(TargetSpec(TargetKind.NEAREST_PLAYER).isStaticallySingle())
        assertTrue(TargetSpec(TargetKind.NEAREST_ENTITY).isStaticallySingle())
        // issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
        assertTrue(TargetSpec(TargetKind.TEMPORARY, tempName = "x").isStaticallySingle())
        assertFalse(TargetSpec(TargetKind.NEARBY_PLAYERS).isStaticallySingle())
        assertFalse(TargetSpec(TargetKind.NEARBY_ENTITIES).isStaticallySingle())
        assertFalse(TargetSpec(TargetKind.ALL_PLAYERS).isStaticallySingle())
        // 上限引き上げで複数になり得る指定は含みません。
        assertFalse(TargetSpec(TargetKind.NEAREST_PLAYER, limit = 3).isStaticallySingle())
        assertTrue(TargetSpec(TargetKind.NEAREST_PLAYER, limit = 1).isStaticallySingle())
    }

    @Test
    fun `destination presets share the parent screen definitions`() {
        // 最寄り・付近は対象指定へ照合し、それ以外は位置指定へ委ねます。
        assertEquals(
            SettingPresets.PositionPreset.NEAREST_PLAYER,
            SettingPresets.matchDestinationPreset(TargetSpec(TargetKind.NEAREST_PLAYER), null),
        )
        assertEquals(
            SettingPresets.PositionPreset.NEARBY_PLAYERS,
            SettingPresets.matchDestinationPreset(
                TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = 5.0),
                null,
            ),
        )
        assertEquals(
            SettingPresets.PositionPreset.CONTROL_BLOCK,
            SettingPresets.matchDestinationPreset(null, PositionSpec(PositionKind.DISK)),
        )
        // 詳細な対象指定はプリセット選択表示にしません。
        assertNull(
            SettingPresets.matchDestinationPreset(TargetSpec(TargetKind.ALL_PLAYERS), null),
        )
        assertNull(SettingPresets.matchDestinationPreset(null, null))
    }
}
