package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.TypedValue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ExecutableScriptValidatorTest {
    @Test
    fun `incomplete command settings fail execution preflight`() {
        val script = DiskScript(name = "incomplete")
        GraphEditor.append(script.graph, CommandType.DISK_CALL)
        val ride = GraphEditor.append(script.graph, CommandType.ENTITY_ACTION)
        ride.params["other"] = ""

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("ディスク内容が未設定") })
        assertTrue(errors.any { it.message.contains("乗り物となる対象が未設定") })
    }

    @Test
    fun `structured errors carry the node and gui field keys`() {
        val script = DiskScript(name = "structured")
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        val diskCall = GraphEditor.append(script.graph, CommandType.DISK_CALL)
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
        condition.params["subject"] = me.awabi2048.kantancommander.model.ConditionSubject.OTHER_ENTITY.name

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
            errors.any { it.nodeId == condition.id && it.fieldKeys == setOf("target") },
            "条件の対象未設定は対象（target）タブへ投影される",
        )
        // 表示形式はrendered()だけが担当し、"path: message" の契約を維持します。
        assertTrue(
            errors.all { it.rendered() == "${it.path}: ${it.message}" },
        )
    }

    @Test
    fun `control block condition requires a configured redstone signal`() {
        val script = DiskScript(name = "control-state")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            params["subject"] = ConditionSubject.CONTROL_BLOCK.name
        }

        // 空欄は「条件なし」として拒否します。
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == condition.id && it.fieldKeys == setOf("condition")
            },
        )

        // 構文上受理できない値は条件なしと区別して拒否します。
        condition.params["redstoneSignal"] = "abc"
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == condition.id && it.message.contains("不正")
            },
        )

        // 0指定は無信号を判定する有効な条件です。
        condition.params["redstoneSignal"] = "0"
        assertFalse(ExecutableScriptValidator.validate(script).any { it.nodeId == condition.id })

        // 強度範囲を指定した場合も判定条件として有効です。
        condition.params["redstoneSignal"] = "1.."
        assertFalse(ExecutableScriptValidator.validate(script).any { it.nodeId == condition.id })
    }

    @Test
    fun `variable definitions support number and string values`() {
        val script = DiskScript(name = "variable")
        val variable = GraphEditor.append(script.graph, CommandType.VARIABLE)
        variable.params.putAll(
            mapOf(
                "name" to "message",
                "type" to ValueType.STRING.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "ready",
            )
        )

        assertFalse(ExecutableScriptValidator.validate(script).any { it.nodeId == variable.id })
    }

    @Test
    fun `temporary references are available only after their definition`() {
        val script = DiskScript(name = "temporary-order")
        val display = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
            params["text"] = "%{message}%"
        }
        val definition = GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "message"
            params["tempType"] = ValueType.STRING.name
            params["value"] = "ready"
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.nodeId == display.id && it.message.contains("一時変数が未定義") })
        assertFalse(errors.any { it.nodeId == definition.id && it.message.contains("一時変数が未定義") })
    }

    @Test
    fun `temporary references defined on only one branch are unavailable after merge`() {
        val script = DiskScript(name = "temporary-branch")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        }
        val definition = GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "message"
            params["tempType"] = ValueType.STRING.name
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
        val script = DiskScript(name = "temporary-branch-common")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "message"
            params["tempType"] = ValueType.STRING.name
            params["value"] = "true"
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET, condition.id).apply {
            params["name"] = "message"
            params["tempType"] = ValueType.STRING.name
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
        val script = DiskScript(name = "temporary-redefinition")
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "value"
            params["tempType"] = ValueType.STRING.name
            params["value"] = "text"
        }
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "value"
            params["tempType"] = ValueType.NUMBER.name
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
        val script = DiskScript(name = "temporary-composite-text")
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "point"
            params["tempType"] = ValueType.LOCATION.name
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
    fun `removed temporary sound and effect types are rejected`() {
        val script = DiskScript(name = "removed-temp-types")
        val sound = GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "sound",
                    "tempType" to "SOUND",
                    "sound" to "minecraft:block.note_block.harp",
                    "volume" to "35",
                    "pitch" to "1",
                ),
            )
        }
        val effect = GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params.putAll(
                mapOf(
                    "name" to "effect",
                    "tempType" to "EFFECT",
                    "effect" to "minecraft:speed",
                    "level" to "1",
                    "seconds" to "30",
                ),
            )
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.nodeId == sound.id && it.fieldKeys == setOf("tempType") })
        assertTrue(errors.any { it.nodeId == effect.id && it.fieldKeys == setOf("tempType") })
    }

    @Test
    fun `timer trigger requires a valid interval`() {
        val script = DiskScript(
            name = "timer",
            trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = 0.0),
        )
        assertTrue(ExecutableScriptValidator.validate(script).any { it.message.contains("タイマー間隔") })

        script.trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = 0.5)
        assertTrue(ExecutableScriptValidator.validate(script).none { it.fieldKeys == setOf("trigger") })
    }

    @Test
    fun `legacy raw selector and coordinate strings do not satisfy structured settings`() {
        val script = DiskScript(name = "legacy")
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.params["target"] = "@a"
        teleport.params["destination"] = "~ ~ ~"

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("対象が未設定") })
        assertTrue(errors.any { it.message.contains("移動先座標が未設定") })
    }

    @Test
    fun `execution preflight uses configured graph limits`() {
        val script = DiskScript(name = "limits")
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
        val script = DiskScript(name = "unsafe")
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
    fun `temporary targets and rotation orientations require complete values`() {
        // issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
        // 未完了の単一参照は一時変数参照で固定します。
        val script = DiskScript(name = "target-shape")
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(TargetKind.TEMPORARY)
        teleport.destinationSpec = PositionSpec(PositionKind.CAPTURED, 1.0, 2.0, 3.0, null, null)
        teleport.destinationFacingSpec = FacingSpec(FacingKind.ROTATION, yaw = null, pitch = null)

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("一時変数") })
        assertTrue(errors.any { it.message.contains("向きが未設定") })
    }

    @Test
    fun `repeat count may reference a world variable`() {
        val script = DiskScript(name = "for-world")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["count"] = "\${limit}"

        assertFalse(
            ExecutableScriptValidator.validate(script).any { it.message.contains("forの") },
        )
    }

    @Test
    fun `numeric inputs reject string world variable references`() {
        val script = DiskScript(name = "for-string-world")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        start.params["count"] = "\${limit}"

        val errors = ExecutableScriptValidator.validate(
            script,
            variableDefinitions = mapOf(
                "limit" to TypedValue(ValueType.STRING, stringValue = "3"),
            ),
        )

        assertTrue(errors.any { it.message.contains("数値型変数だけを参照できます") })
    }

    @Test
    fun `loop values may be assigned inside a for body`() {
        val script = DiskScript(name = "loop-world")
        val start = GraphEditor.append(script.graph, CommandType.FOR_START)
        val variable = GraphEditor.appendToForBody(script.graph, start.id, CommandType.VARIABLE)
        variable.params.putAll(
            mapOf(
                "name" to "shared",
                "type" to ValueType.NUMBER.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "\${CURRENT_LOOP_COUNT}",
            )
        )

        assertFalse(ExecutableScriptValidator.validate(script).any { it.nodeId == variable.id })
    }

    @Test
    fun `system references in structured target filters follow the for body scope`() {
        val outside = DiskScript(name = "outside-target-template")
        val outsideDisplay = GraphEditor.append(outside.graph, CommandType.DISPLAY_TEXT)
        outsideDisplay.targetSpec = TargetSpec(
            TargetKind.ALL_PLAYERS,
            tag = "\${CURRENT_LOOP_COUNT}",
        )

        val outsideErrors = ExecutableScriptValidator.validate(outside)
        assertTrue(outsideErrors.any { it.nodeId == outsideDisplay.id && it.message.contains("for本体内") })

        val inside = DiskScript(name = "inside-target-template")
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
    fun `actionbar validates only the single display duration`() {
        // issue #35-2: アクションバーは単独の表示時間だけを使い、
        // フェード値は検証対象にしません。
        val script = DiskScript(name = "actionbar-duration")
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

        assertFalse(
            ExecutableScriptValidator.validate(script).any {
            it.message.contains("表示時間は0秒以上")
            }
        )

        actionbar.params["staySeconds"] = "-2"

        assertTrue(
            ExecutableScriptValidator.validate(script).any {
            it.message.contains("表示時間は0秒以上")
            }
        )
    }

    @Test
    fun `actionbar ignores leftover fade variable references`() {
        // issue #review P2-7: 非表示項目の残存値は検証対象外です。
        // 有効項目の定義は DisplayTextTimingPolicy で共有します。
        val script = DiskScript(name = "actionbar-stale-fade")
        val actionbar = GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT)
        actionbar.targetSpec = TargetSpec(TargetKind.ALL_PLAYERS)
        actionbar.params.putAll(
            mapOf(
                "mode" to "actionbar",
                "fadeInSeconds" to "%{missing}%",
                "staySeconds" to "3",
                "fadeOutSeconds" to "1",
            )
        )

        assertTrue(ExecutableScriptValidator.validate(script).isEmpty())
    }

    @Test
    fun `temporary target skips detail filter validation`() {
        // issue #review P2-9: 一時変数参照は詳細条件を使わない契約のため、
        // 残存条件の形式検査を行いません。変数名の定義検査だけ残します。
        val script = DiskScript(name = "temporary-filters")
        val node = GraphEditor.append(script.graph, CommandType.ENTITY_DELETE)
        node.targetSpec = TargetSpec(TargetKind.TEMPORARY, tempName = "foe", tag = "###", maximumDistance = -1.0)

        val validationErrors = ExecutableScriptValidator.validate(script)
        // 変数名自体の定義検査は残し、詳細条件の形式検査を行いません。
        assertTrue(validationErrors.any { it.message.contains("一時変数が未定義") })
        assertFalse(validationErrors.any { it.message.contains("タグ") || it.message.contains("対象距離") }) {
            "unexpected errors: ${validationErrors.joinToString(" | ") { it.rendered() }}"
        }
    }

    @Test
    fun `display and wait durations require whole tick values`() {
        val displayScript = DiskScript(name = "fractional-display")
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

        val waitScript = DiskScript(name = "wait-boundaries")
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
        val script = DiskScript(name = "new-operations")
        GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION)
        GraphEditor.append(script.graph, CommandType.ENTITY_DELETE)

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("配置ブロック") })
        assertTrue(errors.any { it.message.contains("ブロック配置位置") })
        assertTrue(errors.any { it.message.contains("削除対象") })
    }

    @Test
    fun `fill rejects a statically oversized volume`() {
        val script = DiskScript(name = "large-fill")
        val fill = GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION)
        fill.params["operation"] = BlockOperationMode.FILL.value
        fill.params["block"] = "minecraft:stone"
        fill.blockFromSpec = PositionSpec(PositionKind.COORDINATES, 0.0, 0.0, 0.0)
        fill.blockToSpec = PositionSpec(PositionKind.COORDINATES, 100.0, 100.0, 100.0)

        assertTrue(ExecutableScriptValidator.validate(script).any { it.message.contains("32768") })
    }

    @Test
    fun `structure place requires a valid name and known rotation and mirror`() {
        val script = DiskScript(name = "structure-place-check")
        val place = GraphEditor.append(script.graph, CommandType.STRUCTURE_PLACE)

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.nodeId == place.id && it.fieldKeys == setOf("structure") })

        place.params["structure"] = "my_house-01"
        assertTrue(
            ExecutableScriptValidator.validate(script).none { it.nodeId == place.id },
        )

        place.params["rotation"] = "sideways"
        place.params["mirror"] = "upside_down"
        val invalid = ExecutableScriptValidator.validate(script)
        assertTrue(invalid.any { it.nodeId == place.id && it.fieldKeys == setOf("rotation") })
        assertTrue(invalid.any { it.nodeId == place.id && it.fieldKeys == setOf("mirror") })
    }

    @Test
    fun `entity group requires its operation name and per-operation inputs`() {
        val script = DiskScript(name = "entity-group-check")
        val group = GraphEditor.append(script.graph, CommandType.ENTITY_GROUP)

        val blank = ExecutableScriptValidator.validate(script)
        assertTrue(blank.any { it.nodeId == group.id && it.fieldKeys == setOf("operation") })
        assertTrue(blank.any { it.nodeId == group.id && it.fieldKeys == setOf("group") })

        group.params["operation"] = "create"
        group.params["group"] = "Invalid Name!"
        val badName = ExecutableScriptValidator.validate(script)
        assertTrue(badName.any { it.nodeId == group.id && it.fieldKeys == setOf("group") })
        assertTrue(badName.any { it.nodeId == group.id && it.fieldKeys == setOf("target") })

        group.params["operation"] = "move"
        group.params["group"] = "my_group-01"
        group.params["moveX"] = "100"
        group.params["moveY"] = "~0"
        group.params["moveZ"] = "^5"
        val badMove = ExecutableScriptValidator.validate(script)
        assertTrue(badMove.any { it.nodeId == group.id && it.fieldKeys == setOf("moveZ") })
        assertTrue(badMove.none { it.nodeId == group.id && it.fieldKeys == setOf("moveX") })

        group.params["operation"] = "rotate"
        group.params["moveX"] = ""
        group.params["moveY"] = ""
        group.params["moveZ"] = ""
        group.params["rotateX"] = "~0"
        group.params["rotateY"] = "~45"
        group.params["rotateZ"] = "0"
        assertTrue(
            ExecutableScriptValidator.validate(script).none { it.nodeId == group.id },
        )

        group.params["operation"] = "set_pivot"
        val missingPivot = ExecutableScriptValidator.validate(script)
        assertTrue(missingPivot.any { it.nodeId == group.id && it.fieldKeys == setOf("pivot") })
    }

    @Test
    fun `summon tags are validated as one string rather than a comma-separated list`() {        val script = DiskScript(name = "single-tag")
        val summon = GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY)
        summon.params["summonEntity"] = "minecraft:pig"
        summon.params["tags"] = "first_tag,second_tag"

        assertTrue(
            ExecutableScriptValidator.validate(script).any { it.nodeId == summon.id && it.fieldKeys == setOf("tags") },
        )

        summon.params["tags"] = "first_tag"
        assertFalse(
            ExecutableScriptValidator.validate(script).any { it.nodeId == summon.id && it.fieldKeys == setOf("tags") },
        )
    }

    @Test
    fun `fresh particle node fails preflight until the kind is chosen`() {
        val script = DiskScript(name = "fresh-particle")
        val particle = GraphEditor.append(script.graph, CommandType.PARTICLE)

        // 選択肢型に既定値を置かないため、新規作成直後は未設定エラーになります。
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == particle.id && it.fieldKeys == setOf("particle")
            },
            "未選択の粒子種類は検証を通らない",
        )

        particle.params[me.awabi2048.kantancommander.model.ParticleSettings.PARAM_PARTICLE] = "FLAME"
        assertTrue(
            ExecutableScriptValidator.validate(script).none { it.nodeId == particle.id },
            "明示選択したFLAME散布設定は検証を通る",
        )
    }

    @Test
    fun `stale detail data on dataless particle is ignored`() {
        val script = DiskScript(name = "stale-particle-data")
        val particle = GraphEditor.append(script.graph, CommandType.PARTICLE)
        particle.params[me.awabi2048.kantancommander.model.ParticleSettings.PARAM_DATA] = "#ff0000 2"

        assertTrue(
            ExecutableScriptValidator.validate(script).none {
                it.nodeId == particle.id &&
                    it.fieldKeys == setOf(me.awabi2048.kantancommander.model.ParticleSettings.PARAM_DATA)
            },
            "旧保存に残った詳細値は表示・実行へ漏らさないため検証対象外にする",
        )
    }

    @Test
    fun `invalid detail data on data particle fails on the detail tab`() {
        val script = DiskScript(name = "broken-particle-data")
        val particle = GraphEditor.append(script.graph, CommandType.PARTICLE)
        particle.params[me.awabi2048.kantancommander.model.ParticleSettings.PARAM_PARTICLE] = "DUST"
        particle.params[me.awabi2048.kantancommander.model.ParticleSettings.PARAM_DATA] = "not-a-color"

        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == particle.id &&
                    it.fieldKeys == setOf(me.awabi2048.kantancommander.model.ParticleSettings.PARAM_DATA)
            },
        )
    }

    @Test
    fun `particle count error maps to the parameters tab`() {
        val script = DiskScript(name = "broken-particle-count")
        val particle = GraphEditor.append(script.graph, CommandType.PARTICLE)
        particle.params[me.awabi2048.kantancommander.model.ParticleSettings.PARAM_COUNT] = "0"

        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == particle.id && it.fieldKeys == setOf("particleParameters")
            },
        )
    }

    @Test
    fun `single only positions reject embedded targets`() {
        val script = DiskScript(name = "single-target-position")
        val block = GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION).apply {
            params["operation"] = BlockOperationMode.FILL.value
            params["block"] = "minecraft:stone"
            blockFromSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.NEARBY_PLAYERS),
            )
            blockToSpec = PositionSpec(PositionKind.COORDINATES, x = 0.0, y = 64.0, z = 0.0)
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == block.id && it.message.contains("複数位置は指定できません")
            },
            "fillの始点に対象由来位置は使えない",
        )
    }

    @Test
    fun `single only positions allow statically single embedded targets`() {
        val script = DiskScript(name = "single-static-target-position")
        val block = GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION).apply {
            params["operation"] = BlockOperationMode.FILL.value
            params["block"] = "minecraft:stone"
            blockFromSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.NEAREST_PLAYER),
            )
            blockToSpec = PositionSpec(PositionKind.COORDINATES, x = 0.0, y = 64.0, z = 0.0)
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).none {
                it.nodeId == block.id && it.message.contains("複数位置は指定できません")
            },
            "最寄り等の0..1指定は単一限定でも許可する",
        )
    }

    @Test
    fun `embedded targets in multi positions are validated`() {
        val script = DiskScript(name = "embedded-target")
        val summon = GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY).apply {
            params["summonEntity"] = "minecraft:zombie"
            summonPositionSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.TEMPORARY, tempName = "!!!"),
            )
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).any { it.nodeId == summon.id },
            "内包対象の不正な一時変数名は検証で検出する",
        )
    }

    @Test
    fun `execution position accepts entity set resolution`() {
        val script = DiskScript(name = "execution-position-target")
        val summon = GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY).apply {
            params["summonEntity"] = "minecraft:zombie"
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            executionPositionSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.NEAREST_PLAYER),
            )
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).none {
                it.nodeId == summon.id && it.message.contains("複数位置は指定できません")
            },
            "実行位置の対象由来は単一制約の対象外",
        )
    }

    @Test
    fun `particle end allows statically single embedded targets`() {
        val script = DiskScript(name = "particle-end-static")
        val particle = GraphEditor.append(script.graph, CommandType.PARTICLE).apply {
            particleEndPositionSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.NEAREST_PLAYER),
            )
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).none {
                it.nodeId == particle.id && it.message.contains("複数位置は指定できません")
            },
            "図形終点の静的単一は先頭1件へ解決するため許可する",
        )
    }

    @Test
    fun `particle end rejects multi embedded targets`() {
        val script = DiskScript(name = "particle-end-multi")
        val particle = GraphEditor.append(script.graph, CommandType.PARTICLE).apply {
            particleEndPositionSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.NEARBY_PLAYERS),
            )
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == particle.id && it.message.contains("複数位置は指定できません")
            },
            "図形終点の複数は対応関係が定まらないため拒否する",
        )
    }

    @Test
    fun `temporary positions resolve components against the variable`() {
        val script = DiskScript(name = "temp-base-offset")
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "base"
            params["tempType"] = ValueType.LOCATION.name
            temporaryLocationPositionSpec = PositionSpec(
                PositionKind.COORDINATES, x = 0.0, y = 64.0, z = 0.0,
            )
            temporaryLocationFacingSpec = FacingSpec(FacingKind.ROTATION, yaw = 0f, pitch = 0f)
        }
        val summon = GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY).apply {
            params["summonEntity"] = "minecraft:zombie"
            summonPositionSpec = PositionSpec(
                PositionKind.TEMPORARY,
                tempName = "base",
                // 基準変数からの差分です。省略したy成分は基準値を継承します。
                x = 1.0,
                z = -2.0,
                semanticsX = CoordinateSemantics.RELATIVE,
                semanticsZ = CoordinateSemantics.RELATIVE,
            )
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).none { it.nodeId == summon.id },
            "一時変数を基準にした成分別指定は受理する",
        )
    }

    @Test
    fun `temporary positions reject missing offsets and local angles`() {
        val script = DiskScript(name = "temp-base-invalid")
        GraphEditor.append(script.graph, CommandType.TEMP_SET).apply {
            params["name"] = "base"
            params["tempType"] = ValueType.LOCATION.name
            temporaryLocationPositionSpec = PositionSpec(
                PositionKind.COORDINATES, x = 0.0, y = 64.0, z = 0.0,
            )
            temporaryLocationFacingSpec = FacingSpec(FacingKind.ROTATION, yaw = 0f, pitch = 0f)
        }
        val summon = GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY).apply {
            params["summonEntity"] = "minecraft:zombie"
            summonPositionSpec = PositionSpec(
                PositionKind.TEMPORARY,
                tempName = "base",
                semanticsX = CoordinateSemantics.RELATIVE,
                semanticsYaw = CoordinateSemantics.LOCAL,
            )
        }

        val errors = ExecutableScriptValidator.validate(script)
        assertTrue(
            errors.any { it.nodeId == summon.id && it.message.contains("有限値で指定") },
            "RELATIVE成分の値欠落は拒否する",
        )
        assertTrue(
            errors.any { it.nodeId == summon.id && it.message.contains("向きにローカル") },
            "角度のLOCALは変数基準でも拒否する",
        )
    }

    @Test
    fun `temporary positions require a defined location variable`() {
        val script = DiskScript(name = "temp-base-undefined")
        val summon = GraphEditor.append(script.graph, CommandType.SUMMON_ENTITY).apply {
            params["summonEntity"] = "minecraft:zombie"
            summonPositionSpec = PositionSpec(
                PositionKind.TEMPORARY,
                tempName = "base",
                x = 1.0,
                semanticsX = CoordinateSemantics.RELATIVE,
            )
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == summon.id && it.message.contains("一時変数が未定義")
            },
            "基準変数の未定義は従来どおり拒否する",
        )
    }

    @Test
    fun `block positions fall back to the execution position`() {
        val script = DiskScript(name = "block-exec-fallback")
        val block = GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION).apply {
            params["operation"] = BlockOperationMode.SETBLOCK.value
            params["block"] = "minecraft:stone"
            executionPositionSpec = PositionSpec(PositionKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0)
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).none {
                it.nodeId == block.id && it.message.contains("ブロック配置位置が未設定です")
            },
            "実行位置があれば配置位置の省略を許可する",
        )
    }

    @Test
    fun `fill range rejects multi execution fallbacks`() {
        val script = DiskScript(name = "fill-multi-exec")
        val block = GraphEditor.append(script.graph, CommandType.BLOCK_OPERATION).apply {
            params["operation"] = BlockOperationMode.FILL.value
            params["block"] = "minecraft:stone"
            blockFromSpec = PositionSpec(PositionKind.COORDINATES, x = 0.0, y = 64.0, z = 0.0)
            executionPositionSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.NEARBY_PLAYERS),
            )
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == block.id && it.message.contains("既定値に複数位置は指定できません")
            },
            "片端欠落＋複数実行位置の組合せは拒否する",
        )
    }
}
