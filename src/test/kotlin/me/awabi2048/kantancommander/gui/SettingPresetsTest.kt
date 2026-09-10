package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.MyworldSpawnKind
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `myworld spawn preset defaults to member and legacy maps to member`() {
        val preset = SettingPresets.positionSpecFor(SettingPresets.PositionPreset.MYWORLD_SPAWN)
        assertEquals(PositionKind.MYWORLD_SPAWN, preset.kind)
        assertEquals(MyworldSpawnKind.MEMBER, preset.myworldSpawn)
        assertEquals(SettingPresets.PositionPreset.MYWORLD_SPAWN, SettingPresets.matchPositionPreset(preset))

        // 旧来の単一種別（種別未設定）はメンバー用として互換移行します。
        val legacy = PositionSpec(PositionKind.MYWORLD_SPAWN)
        assertEquals(SettingPresets.PositionPreset.MYWORLD_SPAWN, SettingPresets.matchPositionPreset(legacy))

        val guest = PositionSpec(PositionKind.MYWORLD_SPAWN, myworldSpawn = MyworldSpawnKind.GUEST)
        // ゲスト用はメンバー用プリセットの選択済み表示にしません。
        assertNull(SettingPresets.matchPositionPreset(guest))
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
    fun `facing presets exclude current orientation`() {
        // 「現在の向き」は新規プリセットの選択済み表示にしません。
        assertNull(SettingPresets.matchFacingPreset(FacingSpec(FacingKind.CAPTURED)))
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
        // 単一限定の対象由来位置だけが制約違反です。
        assertTrue(
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
}
