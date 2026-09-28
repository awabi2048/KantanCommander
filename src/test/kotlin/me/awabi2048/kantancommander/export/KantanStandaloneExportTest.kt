package me.awabi2048.kantancommander.export

import java.nio.file.Path
import java.util.UUID
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.TimerSetting
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.TypedValue
import org.junit.jupiter.api.Assertions.assertEquals
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
            timer = TimerSetting(true, 3.0),
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
                        "enabled" to TypedValue(ValueType.NUMBER, numberValue = 1.0),
                        "message" to TypedValue(ValueType.STRING, stringValue = "hello"),
                    ),
                )
            ),
        ).writeTo(root)

        val load = root.resolve("datapacks/kantan-commander/data/kantan/function/load.mcfunction").toFile().readText()
        val timer = root.resolve(
            "datapacks/kantan-commander/data/kantan/function/placed/${scriptId}_timer.mcfunction"
        ).toFile().readText()
        val tick = root.resolve(
            "datapacks/kantan-commander/data/kantan/function/tick.mcfunction"
        ).toFile().readText()

        assertTrue(load.contains("${VanillaStorageNames.variablePath("abc_enabled", false)} set value 1.0d"))
        assertTrue(load.contains("${VanillaStorageNames.variablePath("abc_message", false)} set value \"hello\""))
        assertTrue(load.contains("repeating_command_block[facing=east]"))
        assertTrue(load.contains("auto:1b"))
        assertTrue(load.contains("tag=kantan_commander_display"))
        assertTrue(timer.contains("matches 60.."))
        assertTrue(tick.contains("matches 60.."))
        assertTrue(tick.contains("scoreboard players add #timer_"))
        assertTrue(root.resolve("datapacks/kantan-commander/data/minecraft/tags/function/tick.json").toFile().isFile)
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

    @Test
    fun `condition variable name is namespaced`() {
        // issue #5: ワールド内変数の参照だけが素の変数名を持つため、名前空間を付けます。
        val node = me.awabi2048.kantancommander.model.CommandNode(
            type = me.awabi2048.kantancommander.model.CommandType.CONDITION,
            params = linkedMapOf(
                "subject" to me.awabi2048.kantancommander.model.ConditionSubject.VARIABLE.name,
                "variable" to "counter",
            ),
        )
        assertEquals("ns_counter", namespaceExportParam(node, "variable", "counter", "ns"))
    }
}
