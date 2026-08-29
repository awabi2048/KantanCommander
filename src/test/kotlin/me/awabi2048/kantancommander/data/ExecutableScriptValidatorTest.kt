package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.TimerSetting
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.BlockOperationMode
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
            timer = TimerSetting(enabled = false, intervalSeconds = 0),
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

    @Test
    fun `non finite selectors and incomplete positions fail preflight`() {
        val script = DiskScript(name = "unsafe", owner = UUID.randomUUID())
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(
            TargetKind.NEAREST_PLAYER,
            minimumDistance = Double.NaN,
            limit = 0,
        )
        teleport.destinationSpec = PositionSpec(PositionKind.COORDINATES, x = 1.0, y = null, z = 3.0)

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.contains("対象距離は有限値") })
        assertTrue(errors.any { it.contains("対象数は1以上") })
        assertTrue(errors.any { it.contains("座標が未設定") })
    }

    @Test
    fun `fixed targets and captured orientations require complete values`() {
        val script = DiskScript(name = "target-shape", owner = UUID.randomUUID())
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(TargetKind.FIXED_ENTITY)
        teleport.destinationSpec = PositionSpec(PositionKind.CAPTURED, 1.0, 2.0, 3.0, null, null)
        teleport.contextOverride = me.awabi2048.kantancommander.model.ExecutionContextSpec(
            facing = FacingSpec(FacingKind.CAPTURED, yaw = null, pitch = null),
        )

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.contains("固定エンティティが未設定") })
        assertTrue(errors.any { it.contains("捕捉した向き") })
    }

    @Test
    fun `for ranges may reference world variables`() {
        val script = DiskScript(name = "for-world", owner = UUID.randomUUID())
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params.putAll(
            mapOf(
                "startSource" to "WORLD",
                "startValue" to "base",
                "endSource" to "WORLD",
                "endValue" to "limit",
                "stepSource" to "FIXED",
                "stepValue" to "1",
            )
        )

        assertFalse(
            ExecutableScriptValidator.validate(script).any { it.contains("forの") },
        )
    }

    @Test
    fun `loop values cannot be stored into world variables`() {
        val script = DiskScript(name = "loop-world", owner = UUID.randomUUID())
        val variable = GraphEditor.append(script.graph, CommandType.VARIABLE)
        variable.params.putAll(
            mapOf(
                "name" to "shared",
                "scope" to "WORLD",
                "type" to VariableType.INTEGER.name,
                "operation" to VariableOperation.SET.name,
                "value" to "\$current_iteration_value",
            )
        )

        assertTrue(
            ExecutableScriptValidator.validate(script).any { it.contains("ループ値はワールド内変数へ保存できません") },
        )
    }

    @Test
    fun `block operations and entity deletion require their structured inputs`() {
        val script = DiskScript(name = "new-operations", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION)
        GraphEditor.append(script.graph, CommandType.ENTITY_DELETE)

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.contains("配置ブロック") })
        assertTrue(errors.any { it.contains("ブロック配置位置") })
        assertTrue(errors.any { it.contains("削除対象") })
    }

    @Test
    fun `fill rejects a statically oversized volume`() {
        val script = DiskScript(name = "large-fill", owner = UUID.randomUUID())
        val fill = GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION)
        fill.params["operation"] = BlockOperationMode.FILL.value
        fill.params["block"] = "minecraft:stone"
        fill.blockFromSpec = PositionSpec(PositionKind.COORDINATES, 0.0, 0.0, 0.0)
        fill.blockToSpec = PositionSpec(PositionKind.COORDINATES, 100.0, 100.0, 100.0)

        assertTrue(ExecutableScriptValidator.validate(script).any { it.contains("32768") })
    }
}
