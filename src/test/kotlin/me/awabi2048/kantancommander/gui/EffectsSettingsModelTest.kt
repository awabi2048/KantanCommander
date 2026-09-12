package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EffectsSettingsModelTest {
    @Test
    fun `sound operation switches the visible tabs`() {
        val play = CommandType.PLAY_SOUND.newNode()
        assertEquals(
            listOf("soundOperation", "soundTargetPosition", "soundSetting", "soundScope", "executionPosition"),
            CommandSettingsModel.visibleFields(play).map { it.key },
        )

        val stop = CommandType.PLAY_SOUND.newNode().apply { params["soundOperation"] = SoundOperation.STOP.name }
        assertEquals(
            listOf("soundOperation", "soundTargetPosition", "soundSetting", "executionPosition"),
            CommandSettingsModel.visibleFields(stop).map { it.key },
        )
    }

    @Test
    fun `sound target and setting combine their domains`() {
        val node = CommandType.PLAY_SOUND.newNode().apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
            soundPositionSpec = PositionSpec(PositionKind.DISK)
            params["sound"] = "minecraft:block.note_block.harp"
        }
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "soundTargetPosition"))
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "soundSetting"))

        val stop = CommandType.PLAY_SOUND.newNode().apply {
            params["soundOperation"] = SoundOperation.STOP.name
            soundPositionSpec = PositionSpec(PositionKind.DISK)
        }
        // 停止操作では再生位置を無視し、対象だけを確認します。
        assertFalse(CommandSettingsModel.isFieldConfigured(stop, "soundTargetPosition"))
        stop.targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        assertTrue(CommandSettingsModel.isFieldConfigured(stop, "soundTargetPosition"))

        stop.params["soundCategory"] = "MASTER"
        assertTrue(CommandSettingsModel.isFieldConfigured(stop, "soundSetting"))
    }

    @Test
    fun `sound descriptors route to the dedicated editors`() {
        val node = CommandType.PLAY_SOUND.newNode()
        assertEquals(CommandSettingEditor.SOUND_OPERATION, CommandSettingsModel.descriptor(node, "soundOperation").editor)
        assertEquals(CommandSettingEditor.SOUND_TARGET_POSITION, CommandSettingsModel.descriptor(node, "soundTargetPosition").editor)
        assertEquals(CommandSettingEditor.SOUND_SCOPE, CommandSettingsModel.descriptor(node, "soundScope").editor)
        assertEquals(CommandSettingEditor.TEXT, CommandSettingsModel.descriptor(node, "soundSetting").editor)
    }

    @Test
    fun `particle and geometry use separate command types with unified tab order`() {
        // #30: 両コマンドとも「種類 → 表示設定 → 表示位置 → 表示対象」の順に統一します。
        val point = CommandType.PARTICLE.newNode()
        assertEquals(
            listOf("particle", "particleParameters", "position", "particleDisplay", "executionPosition"),
            CommandSettingsModel.visibleFields(point).map { it.key },
        )

        val geometry = CommandType.PARTICLE_GEOMETRY.newNode()
        assertEquals(
            listOf("particle", "geometrySettings", "particleDensity", "position", "particleDisplay", "executionPosition"),
            CommandSettingsModel.visibleFields(geometry).map { it.key },
        )

        // 図形の終点・対角点は線・直方体でのみ表示位置へ現れます。
        assertTrue(CommandSettingsModel.isFieldVisible(geometry, "geometryEnd"))
        val circle = CommandType.PARTICLE_GEOMETRY.newNode().apply {
            params["particleShape"] = ParticleShape.CIRCLE.name
        }
        assertFalse(CommandSettingsModel.isFieldVisible(circle, "geometryEnd"))
    }

    @Test
    fun `particle display tracks target only`() {
        val node = CommandType.PARTICLE.newNode()
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "particleDisplay"))
        node.targetSpec = TargetSpec(TargetKind.NEARBY_PLAYERS)
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "particleDisplay"))
    }

    @Test
    fun `geometry setting tracks the current shape only`() {
        val node = CommandType.PARTICLE_GEOMETRY.newNode().apply {
            params["particleShape"] = ParticleShape.CIRCLE.name
        }
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "geometrySettings"))

        val line = CommandType.PARTICLE_GEOMETRY.newNode().apply {
            params["particleShape"] = ParticleShape.LINE.name
        }
        assertTrue(CommandSettingsModel.isFieldConfigured(line, "geometrySettings"))
        // 終点・対角点は表示位置（geometryEnd）として独立に判定します。
        assertFalse(CommandSettingsModel.isFieldConfigured(line, "geometryEnd"))
        line.particleEndPositionSpec = PositionSpec(PositionKind.COORDINATES, 1.0, 0.0, 0.0)
        assertTrue(CommandSettingsModel.isFieldConfigured(line, "geometryEnd"))

        val unset = CommandType.PARTICLE_GEOMETRY.newNode().apply {
            params["particleShape"] = ParticleShape.POINT_SCATTER.name
        }
        assertFalse(CommandSettingsModel.isFieldConfigured(unset, "geometrySettings"))
    }

    @Test
    fun `firework exposes three tabs with dedicated roles`() {
        val node = CommandType.FIREWORK_LAUNCH.newNode()
        assertEquals(
            listOf("fireworkPosition", "fireworkContents", "burstHeight", "executionPosition"),
            CommandSettingsModel.visibleFields(node).map { it.key },
        )
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "fireworkPosition"))
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "fireworkContents"))

        node.fireworkPositionSpec = PositionSpec(PositionKind.DISK)
        node.params["fireworkItem"] = "minecraft:firework_rocket"
        node.params["burstHeight"] = "30.0"
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "fireworkPosition"))
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "fireworkContents"))
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "burstHeight"))
        assertEquals(
            CommandSettingRole.FIREWORK_POSITION,
            CommandSettingsModel.descriptor(node, "fireworkPosition").role,
        )
    }

    @Test
    fun `new positions resolve through their roles`() {
        val node = CommandNode(type = CommandType.PARTICLE).apply {
            particleEndPositionSpec = PositionSpec(PositionKind.DISK)
        }
        assertEquals(PositionKind.DISK, CommandSettingsModel.positionKind(node, CommandSettingRole.PARTICLE_END))

        val firework = CommandNode(type = CommandType.FIREWORK_LAUNCH)
        CommandSettingsModel.setPositionSpec(firework, CommandSettingRole.FIREWORK_POSITION, PositionSpec(PositionKind.DISK))
        assertEquals(PositionKind.DISK, firework.fireworkPositionSpec?.kind)
    }

    @Test
    fun `bundled fields expose typed display values`() {
        val sound = CommandType.PLAY_SOUND.newNode().apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
            params["sound"] = "minecraft:block.note_block.harp"
        }
        val soundFields = EditorMenuLayout.fields(CommandType.PLAY_SOUND).associate { it.key to it.value(sound) }
        val targetPosition = soundFields["soundTargetPosition"] as DisplayValue.SoundTargetPosition
        assertEquals(TargetKind.ALL_PLAYERS, targetPosition.targetKind)
        val setting = soundFields["soundSetting"] as DisplayValue.SoundSetting
        assertEquals("minecraft:block.note_block.harp", setting.sound)

        val particle = CommandType.PARTICLE.newNode()
        val particleFields = EditorMenuLayout.fields(CommandType.PARTICLE).associate { it.key to it.value(particle) }
        val display = particleFields["particleDisplay"] as DisplayValue.ParticleDisplay
        assertEquals(null, display.targetKind)
    }
}
