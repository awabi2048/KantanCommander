package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.TimerSetting
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
}
