package me.awabi2048.kantancommander.export

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ParticleDisplayMode
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

class EffectsExportTest {
    @TempDir
    lateinit var temp: File

    @Test
    fun `stop sound is exported as stopsound with source and targets`() {
        val store = ScriptStore(temp.resolve("stop"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "stop")
        GraphEditor.append(script.graph, CommandType.PLAY_SOUND).apply {
            params["soundOperation"] = SoundOperation.STOP.name
            params["sound"] = "minecraft:block.note_block.harp"
            params["soundCategory"] = "MASTER"
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        }

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("stopsound @a["), body)
        assertTrue(body.contains("master minecraft:block.note_block.harp"), body)
    }

    @Test
    fun `stop all omits source and sound`() {
        val store = ScriptStore(temp.resolve("stop-all"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "stop all")
        GraphEditor.append(script.graph, CommandType.PLAY_SOUND).apply {
            params["soundOperation"] = SoundOperation.STOP.name
        }

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("stopsound @a"), body)
    }

    @Test
    fun `play sound with target uses the selector as receivers`() {
        val store = ScriptStore(temp.resolve("play-target"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "play target")
        GraphEditor.append(script.graph, CommandType.PLAY_SOUND).apply {
            params["sound"] = "minecraft:block.note_block.harp"
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        }

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("playsound minecraft:block.note_block.harp master @a["), body)
    }

    @Test
    fun `geometry display is rejected for vanilla export`() {
        val store = ScriptStore(temp.resolve("geometry"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "geometry")
        GraphEditor.append(script.graph, CommandType.PARTICLE).apply {
            params["particleDisplayMode"] = ParticleDisplayMode.GEOMETRY.name
            params["particleShape"] = ParticleShape.CIRCLE.name
            params["particleDensity"] = "0.5"
            params["geometryRadius"] = "3.0"
        }

        val result = VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script)
        val failure = assertInstanceOf(StandaloneCompilation.Failure::class.java, result)
        assertTrue(failure.errors.any { it.contains("図形表示") }, failure.errors.toString())
    }

    @Test
    fun `firework is exported as summon with flight nbt`() {
        val store = ScriptStore(temp.resolve("firework"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "firework")
        GraphEditor.append(script.graph, CommandType.FIREWORK_LAUNCH).apply {
            params["fireworkItem"] = "minecraft:firework_rocket"
            params["burstHeight"] = "20.0"
            fireworkPositionSpec = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0)
        }

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("summon minecraft:firework_rocket 1.0 2.0 3.0 {Flight:1b,Explosions:[]}"), body)
    }

    @Test
    fun `particle with target uses the selector as receivers`() {
        val store = ScriptStore(temp.resolve("particle-target"), Logger.getAnonymousLogger())
        val script = store.create(UUID.randomUUID(), "particle target")
        GraphEditor.append(script.graph, CommandType.PARTICLE).apply {
            params["particle"] = "FLAME"
            targetSpec = TargetSpec(TargetKind.NEARBY_PLAYERS)
        }

        val result = assertInstanceOf(
            StandaloneCompilation.Success::class.java,
            VanillaDatapackExporter(store, temp.resolve("exports")).compileForStandalone(script),
        )
        val body = result.functions.values.joinToString("\n")
        assertTrue(body.contains("force @a["), body)
    }
}
