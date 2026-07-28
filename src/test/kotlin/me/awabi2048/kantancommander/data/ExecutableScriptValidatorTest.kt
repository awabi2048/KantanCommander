package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.TimerSetting
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ExecutableScriptValidatorTest {
    @Test
    fun `incomplete command settings fail execution preflight`() {
        val script = DiskScript(name = "incomplete", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.DISK_CALL)
        GraphEditor.append(script.graph, CommandType.CONTEXT)
        val ride = GraphEditor.append(script.graph, CommandType.ENTITY_ACTION)
        ride.params["other"] = ""

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.contains("ディスク内容が未設定") })
        assertTrue(errors.any { it.contains("コンテキストが未設定") })
        assertTrue(errors.any { it.contains("乗り物となる対象が未設定") })
    }

    @Test
    fun `variable operation must match its declared type`() {
        val script = DiskScript(name = "variable", owner = UUID.randomUUID())
        val variable = GraphEditor.append(script.graph, CommandType.VARIABLE)
        variable.params.putAll(
            mapOf(
                "name" to "flag",
                "type" to VariableType.TEXT.name,
                "operation" to VariableOperation.TOGGLE.name,
            )
        )

        assertTrue(
            ExecutableScriptValidator.validate(script).any { it.contains("切替は真偽値だけ") }
        )
    }

    @Test
    fun `always active and interval require an enabled valid timer`() {
        val script = DiskScript(
            name = "timer",
            owner = UUID.randomUUID(),
            activation = ActivationMode.ALWAYS_ACTIVE,
            timer = TimerSetting(enabled = false, intervalUnits = 0),
        )
        assertTrue(ExecutableScriptValidator.validate(script).any { it.contains("タイマーオフ") })

        script.timer.enabled = true
        assertTrue(ExecutableScriptValidator.validate(script).any { it.contains("タイマー間隔") })
    }

    @Test
    fun `legacy raw selector and coordinate strings do not satisfy structured settings`() {
        val script = DiskScript(name = "legacy", owner = UUID.randomUUID())
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.params["target"] = "@a"
        teleport.params["destination"] = "~ ~ ~"

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.contains("対象が未設定") })
        assertTrue(errors.any { it.contains("移動先が未設定") })
    }

    @Test
    fun `execution preflight uses configured graph limits`() {
        val script = DiskScript(name = "limits", owner = UUID.randomUUID())
        val first = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        first.targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        val second = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        second.targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)

        assertTrue(
            ExecutableScriptValidator.validate(
                script,
                GraphLimits(maximumNodeCount = 1),
            ).any { it.contains("上限 1") }
        )
        assertFalse(
            ExecutableScriptValidator.validate(
                script,
                GraphLimits(maximumNodeCount = 2),
            ).any { it.contains("ノード数が上限") }
        )
    }
}
