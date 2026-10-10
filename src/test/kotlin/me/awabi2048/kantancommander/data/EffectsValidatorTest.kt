package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.SoundOperation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class EffectsValidatorTest {
    private fun scriptWith(type: CommandType, configure: me.awabi2048.kantancommander.model.CommandNode.() -> Unit = {}): DiskScript {
        val script = DiskScript(name = "effects")
        GraphEditor.append(script.graph, type).configure()
        return script
    }

    @Test
    fun `stop sound accepts category sound and blank-all`() {
        val scoped = scriptWith(CommandType.PLAY_SOUND) {
            params["soundOperation"] = SoundOperation.STOP.name
            params["sound"] = "minecraft:block.note_block.harp"
            params["soundCategory"] = "MASTER"
        }
        assertTrue(ExecutableScriptValidator.validate(scoped).isEmpty())

        val all = scriptWith(CommandType.PLAY_SOUND) {
            params["soundOperation"] = SoundOperation.STOP.name
        }
        assertTrue(ExecutableScriptValidator.validate(all).isEmpty())
    }

    @Test
    fun `stop sound rejects unknown category and sound id`() {
        val category = scriptWith(CommandType.PLAY_SOUND) {
            params["soundOperation"] = SoundOperation.STOP.name
            params["soundCategory"] = "unknown"
        }
        assertTrue(ExecutableScriptValidator.validate(category).any { it.fieldKeys == setOf("soundSetting") })

        val sound = scriptWith(CommandType.PLAY_SOUND) {
            params["soundOperation"] = SoundOperation.STOP.name
            params["sound"] = "not a sound id !!!"
        }
        assertTrue(ExecutableScriptValidator.validate(sound).any { it.fieldKeys == setOf("soundSetting") })
    }

    @Test
    fun `play sound keeps the legacy range checks on the bundled tab`() {
        val script = scriptWith(CommandType.PLAY_SOUND) {
            params["sound"] = "minecraft:block.note_block.harp"
            params["volume"] = "99.0"
        }
        assertTrue(ExecutableScriptValidator.validate(script).any { it.fieldKeys == setOf("soundSetting") })
    }

    @Test
    fun `geometry circle passes with agreed density`() {
        // #30: 図形表示は独立したPARTICLE_GEOMETRY型で検証します。
        val script = scriptWith(CommandType.PARTICLE_GEOMETRY) {
            params["particle"] = "FLAME"
            params["particleShape"] = ParticleShape.CIRCLE.name
            params["particleDensity"] = "0.5"
            params["geometryRadius"] = "3.0"
        }
        assertTrue(ExecutableScriptValidator.validate(script).isEmpty())
    }

    @Test
    fun `geometry rejects point scatter density overflow and missing end`() {
        val scatter = scriptWith(CommandType.PARTICLE_GEOMETRY) {
            params["particleShape"] = ParticleShape.POINT_SCATTER.name
        }
        assertTrue(ExecutableScriptValidator.validate(scatter).any { it.fieldKeys == setOf("geometrySettings") })

        val density = scriptWith(CommandType.PARTICLE_GEOMETRY) {
            params["particleShape"] = ParticleShape.CIRCLE.name
            params["particleDensity"] = "2.1"
        }
        assertTrue(ExecutableScriptValidator.validate(density).any { it.fieldKeys == setOf("particleDensity") })

        val line = scriptWith(CommandType.PARTICLE_GEOMETRY) {
            params["particleShape"] = ParticleShape.LINE.name
            particlePositionSpec = PositionSpec(PositionKind.COORDINATES, 0.0, 0.0, 0.0)
        }
        assertTrue(ExecutableScriptValidator.validate(line).any { it.fieldKeys == setOf("geometrySettings") })
    }

    @Test
    fun `oversized geometry estimate is rejected before quota`() {
        val script = scriptWith(CommandType.PARTICLE_GEOMETRY) {
            params["particleShape"] = ParticleShape.SPHERE.name
            params["particleDensity"] = "2.0"
            params["geometryRadius"] = "32.0"
        }
        val errors = ExecutableScriptValidator.validate(script)
        assertTrue(errors.any { it.fieldKeys == setOf("geometrySettings") && it.message.contains("大きすぎ") })
    }

    @Test
    fun `point scatter keeps the legacy checks`() {
        val script = scriptWith(CommandType.PARTICLE) {
            params["particleCount"] = "0"
        }
        assertTrue(ExecutableScriptValidator.validate(script).any { it.fieldKeys == setOf("particleParameters") })
    }

    @Test
    fun `firework requires contents and bounded height`() {
        val missing = scriptWith(CommandType.FIREWORK_LAUNCH) {}
        assertTrue(ExecutableScriptValidator.validate(missing).any { it.fieldKeys == setOf("fireworkContents") })

        val height = scriptWith(CommandType.FIREWORK_LAUNCH) {
            params["fireworkItem"] = "minecraft:firework_rocket"
            params["burstHeight"] = "321"
        }
        assertTrue(ExecutableScriptValidator.validate(height).any { it.fieldKeys == setOf("burstHeight") })

        val valid = scriptWith(CommandType.FIREWORK_LAUNCH) {
            params["fireworkItem"] = "minecraft:firework_rocket"
            params["burstHeight"] = "20.0"
        }
        assertFalse(ExecutableScriptValidator.validate(valid).any { it.fieldKeys == setOf("fireworkContents") || it.fieldKeys == setOf("burstHeight") })
    }
}
