package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ParticleDisplayMode
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
            listOf("soundOperation", "soundTargetPosition", "soundSetting", "soundScope"),
            CommandSettingsModel.visibleFields(play).map { it.key },
        )

        val stop = CommandType.PLAY_SOUND.newNode().apply { params["soundOperation"] = SoundOperation.STOP.name }
        assertEquals(
            listOf("soundOperation", "soundTargetPosition", "soundSetting"),
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
    fun `particle display mode switches the visible tabs`() {
        val point = CommandType.PARTICLE.newNode()
        assertEquals(
            listOf("particleDisplay", "particle", "position", "particleParameters"),
            CommandSettingsModel.visibleFields(point).map { it.key },
        )

        val geometry = CommandType.PARTICLE.newNode().apply { params["particleDisplayMode"] = ParticleDisplayMode.GEOMETRY.name }
        assertEquals(
            listOf("particleDisplay", "particle", "geometrySettings", "particleDensity"),
            CommandSettingsModel.visibleFields(geometry).map { it.key },
        )
    }

    @Test
    fun `particle display combines target and mode`() {
        val node = CommandType.PARTICLE.newNode()
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "particleDisplay"))
        node.targetSpec = TargetSpec(TargetKind.NEARBY_PLAYERS)
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "particleDisplay"))
    }

    @Test
    fun `geometry setting tracks the current shape only`() {
        val node = CommandType.PARTICLE.newNode().apply {
            params["particleDisplayMode"] = ParticleDisplayMode.GEOMETRY.name
            params["particleShape"] = ParticleShape.CIRCLE.name
        }
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "geometrySettings"))

        val line = CommandType.PARTICLE.newNode().apply {
            params["particleDisplayMode"] = ParticleDisplayMode.GEOMETRY.name
            params["particleShape"] = ParticleShape.LINE.name
        }
        assertTrue(CommandSettingsModel.isFieldConfigured(line, "geometrySettings"))
        line.particleEndPositionSpec = PositionSpec(PositionKind.COORDINATES, 1.0, 0.0, 0.0)
        assertTrue(CommandSettingsModel.isFieldConfigured(line, "geometrySettings"))

        val unset = CommandType.PARTICLE.newNode().apply {
            params["particleDisplayMode"] = ParticleDisplayMode.GEOMETRY.name
        }
        assertFalse(CommandSettingsModel.isFieldConfigured(unset, "geometrySettings"))
    }

    @Test
    fun `firework exposes three tabs with dedicated roles`() {
        val node = CommandType.FIREWORK_LAUNCH.newNode()
        assertEquals(
            listOf("fireworkPosition", "fireworkContents", "burstHeight"),
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
        assertEquals(me.awabi2048.kantancommander.model.ParticleDisplayMode.POINT, display.mode)
    }
}
