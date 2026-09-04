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
import me.awabi2048.kantancommander.model.WorldVariableValue
import me.awabi2048.kantancommander.model.TemporaryVariableType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ExecutableScriptValidatorTest {
    @Test
    fun `incomplete command settings fail execution preflight`() {
        val script = DiskScript(name = "incomplete", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.DISK_CALL)
        val ride = GraphEditor.append(script.graph, CommandType.ENTITY_ACTION)
        ride.params["other"] = ""

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("ディスク内容が未設定") })
        assertTrue(errors.any { it.message.contains("乗り物となる対象が未設定") })
    }

    @Test
    fun `structured errors carry the node and gui field keys`() {
        val script = DiskScript(name = "structured", owner = UUID.randomUUID())
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        val diskCall = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params["kind"] = me.awabi2048.kantancommander.model.ConditionKind.TARGET_EXISTS.name

        val errors = ExecutableScriptValidator.validate(script)

        val teleportErrors = errors.filter { it.nodeId == teleport.id }
        assertTrue(teleportErrors.any { it.fieldKeys == setOf("target") })
        assertTrue(teleportErrors.any { it.fieldKeys == setOf("destination") })
        assertTrue(teleportErrors.all { it.path == "root/${teleport.id}" })
        assertTrue(
            errors.any { it.nodeId == diskCall.id && it.fieldKeys == setOf("diskId") },
            "DISK_CALLの未設定はdiskIdタブへ投影される",
        )
        assertTrue(
            errors.any { it.nodeId == condition.id && it.fieldKeys == setOf("condition") },
            "条件の未設定は条件値（condition）タブへ投影される",
        )
        // 表示形式はrendered()だけが担当し、"path: message" の契約を維持します。
        assertTrue(
            errors.all { it.rendered() == "${it.path}: ${it.message}" },
        )
    }

    @Test
    fun `variable definitions support number and string values`() {
        val script = DiskScript(name = "variable", owner = UUID.randomUUID())
        val variable = GraphEditor.append(script.graph, CommandType.VARIABLE)
        variable.params.putAll(
            mapOf(
                "name" to "message",
                "type" to VariableType.STRING.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "ready",
            )
        )

        assertFalse(ExecutableScriptValidator.validate(script).any { it.nodeId == variable.id })
    }

    @Test
    fun `temporary references are available only after their definition`() {
        val script = DiskScript(name = "temporary-order", owner = UUID.randomUUID())
        val display = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
            params["text"] = "%{message}%"
        }
        val definition = GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "message"
            params["tempType"] = TemporaryVariableType.STRING.name
            params["value"] = "ready"
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.nodeId == display.id && it.message.contains("一時変数が未定義") })
        assertFalse(errors.any { it.nodeId == definition.id && it.message.contains("一時変数が未定義") })
    }

    @Test
    fun `temporary references defined on only one branch are unavailable after merge`() {
        val script = DiskScript(name = "temporary-branch", owner = UUID.randomUUID())
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        }
        val definition = GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "message"
            params["tempType"] = TemporaryVariableType.STRING.name
            params["value"] = "ready"
        }
        GraphEditor.append(script.graph, CommandType.WAIT, condition.id)
        GraphEditor.appendMerge(script.graph, condition.id)
        val after = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
            params["text"] = "%{message}%"
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.nodeId == after.id && it.message.contains("一時変数が未定義") })
        assertFalse(errors.any { it.nodeId == definition.id && it.message.contains("一時変数が未定義") })
    }

    @Test
    fun `temporary references defined on every branch retain their common type`() {
        val script = DiskScript(name = "temporary-branch-common", owner = UUID.randomUUID())
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "message"
            params["tempType"] = TemporaryVariableType.STRING.name
            params["value"] = "true"
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET, condition.id).apply {
            params["name"] = "message"
            params["tempType"] = TemporaryVariableType.STRING.name
            params["value"] = "false"
        }
        GraphEditor.appendMerge(script.graph, condition.id)
        val after = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
            params["text"] = "%{message}%"
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertFalse(errors.any { it.nodeId == after.id && it.message.contains("一時変数が未定義") })
    }

    @Test
    fun `temporary redefinition may change type on one execution path`() {
        val script = DiskScript(name = "temporary-redefinition", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "value"
            params["tempType"] = TemporaryVariableType.STRING.name
            params["value"] = "text"
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "value"
            params["tempType"] = TemporaryVariableType.NUMBER.name
            params["value"] = "2"
        }
        val wait = GraphEditor.append(script.graph, CommandType.WAIT).apply {
            params["seconds"] = "%{value}%"
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertFalse(errors.any { it.nodeId == wait.id && it.message.contains("一時変数") })
    }

    @Test
    fun `composite temporary values cannot be embedded in generic text`() {
        val script = DiskScript(name = "temporary-composite-text", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "point"
            params["tempType"] = TemporaryVariableType.LOCATION.name
            temporaryLocationPositionSpec = PositionSpec(
                PositionKind.COORDINATES,
                x = 1.0,
                y = 2.0,
                z = 3.0,
            )
            temporaryLocationFacingSpec = FacingSpec(
                FacingKind.ROTATION,
                yaw = 0f,
                pitch = 0f,
            )
        }
        val display = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
            params["text"] = "%{point}%"
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.nodeId == display.id && it.message.contains("複合型") })
    }

    @Test
    fun `temporary sound fields use their own validation keys and ranges`() {
        val script = DiskScript(name = "temporary-sound-range", owner = UUID.randomUUID())
        val sound = GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "sound",
                    "tempType" to TemporaryVariableType.SOUND.name,
                    "sound" to "minecraft:block.note_block.harp",
                    "volume" to "35",
                    "pitch" to "1",
                ),
            )
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.nodeId == sound.id && it.fieldKeys == setOf("volume") })
    }

    @Test
    fun `always active and interval require an enabled valid timer`() {
        val script = DiskScript(
            name = "timer",
            owner = UUID.randomUUID(),
            activation = ActivationMode.ALWAYS_ACTIVE,
            timer = TimerSetting(enabled = false, intervalSeconds = 0),
        )
        assertTrue(ExecutableScriptValidator.validate(script).any { it.message.contains("タイマーオフ") })

        script.timer.enabled = true
        assertTrue(ExecutableScriptValidator.validate(script).any { it.message.contains("タイマー間隔") })
    }

    @Test
    fun `legacy raw selector and coordinate strings do not satisfy structured settings`() {
        val script = DiskScript(name = "legacy", owner = UUID.randomUUID())
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.params["target"] = "@a"
        teleport.params["destination"] = "~ ~ ~"

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("対象が未設定") })
        assertTrue(errors.any { it.message.contains("移動先座標が未設定") })
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
            ).any { it.message.contains("上限 1") }
        )
        assertFalse(
            ExecutableScriptValidator.validate(
                script,
                GraphLimits(maximumNodeCount = 2),
            ).any { it.message.contains("ノード数が上限") }
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

        assertTrue(errors.any { it.message.contains("対象距離は有限値") })
        assertTrue(errors.any { it.message.contains("対象数は1以上") })
        assertTrue(errors.any { it.message.contains("座標が未設定") })
    }

    @Test
    fun `fixed targets and captured orientations require complete values`() {
        val script = DiskScript(name = "target-shape", owner = UUID.randomUUID())
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(TargetKind.FIXED_ENTITY)
        teleport.destinationSpec = PositionSpec(PositionKind.CAPTURED, 1.0, 2.0, 3.0, null, null)
        teleport.destinationFacingSpec = FacingSpec(FacingKind.CAPTURED, yaw = null, pitch = null)

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("固定エンティティが未設定") })
        assertTrue(errors.any { it.message.contains("捕捉した向き") })
    }

    @Test
    fun `repeat count may reference a world variable`() {
        val script = DiskScript(name = "for-world", owner = UUID.randomUUID())
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["count"] = "\${limit}"

        assertFalse(
            ExecutableScriptValidator.validate(script).any { it.message.contains("forの") },
        )
    }

    @Test
    fun `numeric inputs reject string world variable references`() {
        val script = DiskScript(name = "for-string-world", owner = UUID.randomUUID())
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["count"] = "\${limit}"

        val errors = ExecutableScriptValidator.validate(
            script,
            variableDefinitions = mapOf(
                "limit" to WorldVariableValue(VariableType.STRING, stringValue = "3"),
            ),
        )

        assertTrue(errors.any { it.message.contains("数値型変数だけを参照できます") })
    }

    @Test
    fun `loop values may be assigned inside a for body`() {
        val script = DiskScript(name = "loop-world", owner = UUID.randomUUID())
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        val variable = GraphEditor.appendToForBody(script.graph, start.id, CommandType.VARIABLE)
        variable.params.putAll(
            mapOf(
                "name" to "shared",
                "type" to VariableType.NUMBER.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "\${CURRENT_LOOP_COUNT}",
            )
        )

        assertFalse(ExecutableScriptValidator.validate(script).any { it.nodeId == variable.id })
    }

    @Test
    fun `system references in structured target filters follow the for body scope`() {
        val outside = DiskScript(name = "outside-target-template", owner = UUID.randomUUID())
        val outsideDisplay = GraphEditor.append(outside.graph, CommandType.DISPLAY_TEXT)
        outsideDisplay.targetSpec = TargetSpec(
            TargetKind.ALL_PLAYERS,
            tag = "\${CURRENT_LOOP_COUNT}",
        )

        val outsideErrors = ExecutableScriptValidator.validate(outside)
        assertTrue(outsideErrors.any { it.nodeId == outsideDisplay.id && it.message.contains("for本体内") })

        val inside = DiskScript(name = "inside-target-template", owner = UUID.randomUUID())
        val start = GraphEditor.append(inside.graph, CommandType.FOR_START)
        val insideDisplay = GraphEditor.appendToForBody(inside.graph, start.id, CommandType.DISPLAY_TEXT)
        insideDisplay.targetSpec = TargetSpec(
            TargetKind.ALL_PLAYERS,
            tag = "\${CURRENT_LOOP_COUNT}",
        )

        val insideErrors = ExecutableScriptValidator.validate(inside)
        assertFalse(insideErrors.any { it.nodeId == insideDisplay.id && it.message.contains("for本体内") })
    }

    @Test
    fun `actionbar durations are validated like title durations`() {
        val script = DiskScript(name = "actionbar-duration", owner = UUID.randomUUID())
        val actionbar = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        actionbar.targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        actionbar.params.putAll(
            mapOf(
                "mode" to "actionbar",
                "fadeInSeconds" to "-1",
                "staySeconds" to "3",
                "fadeOutSeconds" to "1",
            )
        )

        assertTrue(
            ExecutableScriptValidator.validate(script).any {
            it.message.contains("表示時間は0秒以上")
            }
        )
    }

    @Test
    fun `display and wait durations require whole tick values`() {
        val displayScript = DiskScript(name = "fractional-display", owner = UUID.randomUUID())
        val display = GraphEditor.append(displayScript.graph, CommandType.DISPLAY_TEXT)
        display.targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        display.params.putAll(
            mapOf(
                "mode" to "title",
                "fadeInSeconds" to "0.01",
                "staySeconds" to "0.05",
                "fadeOutSeconds" to "1",
            )
        )

        val displayErrors = ExecutableScriptValidator.validate(displayScript)
        assertTrue(
            displayErrors.any {
                it.nodeId == display.id &&
                    it.fieldKeys == setOf("fadeInSeconds") &&
                    it.message == "時間の設定は、1ティック = 0.05秒 の単位で行ってください"
            },
        )

        val waitScript = DiskScript(name = "wait-boundaries", owner = UUID.randomUUID())
        val wait = GraphEditor.append(waitScript.graph, CommandType.WAIT)

        wait.params["seconds"] = "0.05"
        assertFalse(
            ExecutableScriptValidator.validate(waitScript).any {
                it.nodeId == wait.id && it.fieldKeys == setOf("seconds") && it.message.contains("ティック")
            },
        )

        wait.params["seconds"] = "86400"
        assertFalse(
            ExecutableScriptValidator.validate(waitScript).any {
                it.nodeId == wait.id && it.fieldKeys == setOf("seconds") && it.message.contains("86400秒以下")
            },
        )

        wait.params["seconds"] = "86400.05"
        assertTrue(
            ExecutableScriptValidator.validate(waitScript).any {
                it.nodeId == wait.id &&
                    it.fieldKeys == setOf("seconds") &&
                    it.message == "待機時間は0秒より大きく86400秒以下の数値で指定してください"
            },
        )

        wait.params["seconds"] = "0.01"
        assertTrue(
            ExecutableScriptValidator.validate(waitScript).any {
                it.nodeId == wait.id &&
                    it.fieldKeys == setOf("seconds") &&
                    it.message == "時間の設定は、1ティック = 0.05秒 の単位で行ってください"
            },
        )
    }

    @Test
    fun `block operations and entity deletion require their structured inputs`() {
        val script = DiskScript(name = "new-operations", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION)
        GraphEditor.append(script.graph, CommandType.ENTITY_DELETE)

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("配置ブロック") })
        assertTrue(errors.any { it.message.contains("ブロック配置位置") })
        assertTrue(errors.any { it.message.contains("削除対象") })
    }

    @Test
    fun `fill rejects a statically oversized volume`() {
        val script = DiskScript(name = "large-fill", owner = UUID.randomUUID())
        val fill = GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION)
        fill.params["operation"] = BlockOperationMode.FILL.value
        fill.params["block"] = "minecraft:stone"
        fill.blockFromSpec = PositionSpec(PositionKind.COORDINATES, 0.0, 0.0, 0.0)
        fill.blockToSpec = PositionSpec(PositionKind.COORDINATES, 100.0, 100.0, 100.0)

        assertTrue(ExecutableScriptValidator.validate(script).any { it.message.contains("32768") })
    }

    @Test
    fun `summon tags are validated as one string rather than a comma-separated list`() {
        val script = DiskScript(name = "single-tag", owner = UUID.randomUUID())
        val summon = GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY)
        summon.params["entity"] = "minecraft:pig"
        summon.params["tags"] = "first_tag,second_tag"

        assertTrue(
            ExecutableScriptValidator.validate(script).any { it.nodeId == summon.id && it.fieldKeys == setOf("tags") },
        )

        summon.params["tags"] = "first_tag"
        assertFalse(
            ExecutableScriptValidator.validate(script).any { it.nodeId == summon.id && it.fieldKeys == setOf("tags") },
        )
    }
}
