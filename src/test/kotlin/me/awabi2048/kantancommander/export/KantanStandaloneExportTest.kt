package me.awabi2048.kantancommander.export

import java.nio.file.Path
import java.util.UUID
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.TimerSetting
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KantanStandaloneExportTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `standalone pack restores placements timers and initial variables`() {
        val scriptId = UUID.randomUUID()
        val script = DiskScript(
            id = scriptId,
            name = "timer",
            owner = UUID.randomUUID(),
            activation = ActivationMode.ALWAYS_ACTIVE,
            timer = TimerSetting(true, 3),
            graph = CommandGraph.empty(),
        )
        val program = PreparedProgram(
            placement = DiskPlacement("source", 12, 64, -3, scriptId, "EAST", null),
            script = script,
            dimensionKey = "mwm_export:world_a",
            variableNamespace = "abc",
            functions = mapOf(scriptId.toString() to "return 1\n"),
        )
        PreparedKantanExport(
            listOf(program),
            mapOf(
                "mwm_export:world_a" to PreparedVariables(
                    "abc",
                    mapOf(
                        "enabled" to WorldVariableValue(VariableType.BOOLEAN, booleanValue = true),
                        "message" to WorldVariableValue(VariableType.TEXT, textValue = "hello"),
                    ),
                )
            ),
        ).writeTo(root)

        val load = root.resolve("datapacks/kantan-commander/data/kantan/function/load.mcfunction").toFile().readText()
        val timer = root.resolve(
            "datapacks/kantan-commander/data/kantan/function/placed/${scriptId}_timer.mcfunction"
        ).toFile().readText()

        assertTrue(load.contains("scoreboard players set ${VanillaScoreNames.variableHolder("abc_enabled", false)} kc_vars 1"))
        assertTrue(load.contains("${VanillaStorageNames.variablePath("abc_message", false)} set value \"hello\""))
        assertTrue(load.contains("repeating_command_block[facing=east]"))
        assertTrue(load.contains("auto:1b"))
        assertTrue(load.contains("tag=kantan_commander_display"))
        assertTrue(timer.contains("matches 30.."))
        assertTrue(timer.contains("return run function kantan:$scriptId"))
    }

    @Test
    fun `long variable names remain distinct and valid scoreboard holders`() {
        val first = VanillaScoreNames.variableHolder("world_namespace_1234567890_variable_first", false)
        val second = VanillaScoreNames.variableHolder("world_namespace_1234567890_variable_second", false)

        assertTrue(first != second)
        assertTrue(first.length <= 40)
        assertTrue(second.length <= 40)
    }
}
