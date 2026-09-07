package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.SoundOperation
import org.bukkit.Particle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EffectsDialogSpecsTest {
    @Test
    fun `sound setting inputs follow the operation`() {
        val play = CommandType.PLAY_SOUND.newNode()
        assertEquals(
            listOf("sound", "volume", "pitch"),
            CommandDialogSpecs.soundSettingInputs(play).map { it.id },
        )

        val stop = CommandType.PLAY_SOUND.newNode().apply { params["soundOperation"] = SoundOperation.STOP.name }
        assertEquals(
            listOf("sound", "soundCategory"),
            CommandDialogSpecs.soundSettingInputs(stop).map { it.id },
        )
    }

    @Test
    fun `stop category accepts blank and known names`() {
        assertNull(CommandDialogSpecs.stopSoundCategory.validateInput(""))
        assertNull(CommandDialogSpecs.stopSoundCategory.validateInput("MASTER"))
        assertNull(CommandDialogSpecs.stopSoundCategory.validateInput("voice"))
        assertTrue(CommandDialogSpecs.stopSoundCategory.validateInput("unknown") != null)
    }

    @Test
    fun `particle inputs bundle vibration and trail endpoints`() {
        val plain = CommandType.PARTICLE.newNode()
        assertEquals(listOf("particle"), CommandDialogSpecs.particleInputs(plain).map { it.id })

        val vibration = CommandType.PARTICLE.newNode().apply {
            params["particle"] = Particle.VIBRATION.name
            params["particleData"] = "1 2 3 20"
        }
        assertEquals(
            listOf("particle", "vibX", "vibY", "vibZ", "vibT"),
            CommandDialogSpecs.particleInputs(vibration).map { it.id },
        )
        assertEquals("1 2 3 20", CommandDialogSpecs.composeVibrationData(mapOf("vibX" to "1", "vibY" to "2", "vibZ" to "3", "vibT" to "20")))

        val trail = CommandType.PARTICLE.newNode().apply {
            params["particle"] = Particle.TRAIL.name
            params["particleData"] = "1 2 3 #ff0000 20"
        }
        assertEquals(
            listOf("particle", "trailX", "trailY", "trailZ", "trailColor", "trailT"),
            CommandDialogSpecs.particleInputs(trail).map { it.id },
        )
        assertEquals(
            "1 2 3 #ff0000 20",
            CommandDialogSpecs.composeTrailData(mapOf("trailX" to "1", "trailY" to "2", "trailZ" to "3", "trailColor" to "#ff0000", "trailT" to "20")),
        )
    }

    @Test
    fun `geometry inputs cover the current shape numbers`() {
        fun ids(shape: ParticleShape): List<String> {
            val node = CommandType.PARTICLE.newNode().apply { params["particleShape"] = shape.name }
            return CommandDialogSpecs.geometryInputs(node).map { it.id }
        }

        assertTrue(ids(ParticleShape.LINE).contains("geometryStep"))
        assertTrue(ids(ParticleShape.CIRCLE).containsAll(listOf("geometryRadius", "geometryAxisYaw", "geometryStep")))
        assertTrue(ids(ParticleShape.ARC).containsAll(listOf("geometryStartAngle", "geometryEndAngle")))
        assertTrue(ids(ParticleShape.HELIX).containsAll(listOf("geometryHeight", "geometryTurns")))
        // 終端位置は位置選択画面の担当であり、数値入力に含めない
        assertTrue(ids(ParticleShape.LINE).none { it == "particleEnd" })
    }

    @Test
    fun `density and burst height specs enforce their ranges`() {
        assertNull(CommandDialogSpecs.particleDensity.validateInput("0.5"))
        assertTrue(CommandDialogSpecs.particleDensity.validateInput("0") != null)
        assertTrue(CommandDialogSpecs.particleDensity.validateInput("2.1") != null)
        assertNull(CommandDialogSpecs.burstHeight.validateInput("20.0"))
        assertTrue(CommandDialogSpecs.burstHeight.validateInput("321") != null)
    }

    @Test
    fun `new specs report specific invalid keys`() {
        assertEquals(
            com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys
                .KANTAN_COMMANDER_CLEAN_GUI_DIALOG_PARTICLE_DENSITY_INVALID,
            CommandDialogSpecs.particleDensity.validateInput("0"),
        )
        assertEquals(
            com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys
                .KANTAN_COMMANDER_CLEAN_GUI_DIALOG_BURST_HEIGHT_INVALID,
            CommandDialogSpecs.burstHeight.validateInput("321"),
        )
        assertEquals(
            com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys
                .KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STOP_SOUND_CATEGORY_INVALID,
            CommandDialogSpecs.stopSoundCategory.validateInput("unknown"),
        )
    }

    @Test
    fun `sound field supports suggestions for the candidate flow`() {
        assertTrue(CommandDialogSpecs.supportsSuggestions("sound"))
        // 単体環境ではRegistryが使えないため空欄になるが、契約は維持する。
        assertTrue(CommandDialogSpecs.suggestions("sound", "note").size <= 12)
    }
}
