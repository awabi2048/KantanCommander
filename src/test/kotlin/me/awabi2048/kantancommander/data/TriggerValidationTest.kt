package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.DiskArgument
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.ValueType
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #7 の起動方法の検証テストです。
 *
 * 明示の起動方法の検査と従来欄の検査の切替え、イベント引数の定義シードを固定します。
 * イベント引数は実行開始時に定義済みのEXECUTION変数であり、検証層は
 * データフロー解析の入口へ種子として与えます。
 */
class TriggerValidationTest {
    private fun script(configure: DiskScript.() -> Unit): DiskScript =
        DiskScript(name = "trigger", owner = UUID.randomUUID()).apply(configure)

    private fun stateCondition(configure: me.awabi2048.kantancommander.model.CommandNode.() -> Unit): DiskScript {
        val prepared = script {}
        GraphEditor.append(prepared.graph, CommandType.CONDITION).apply(configure)
        return prepared
    }

    @Test
    fun `explicit timer with bad interval reports trigger`() {
        val prepared = script {
            trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = -1.0)
        }
        val errors = ExecutableScriptValidator.validate(prepared)
        assertTrue(errors.any { it.nodeId == null && it.fieldKeys == setOf("trigger") })
    }

    @Test
    fun `explicit event without kind is rejected`() {
        val prepared = script {
            trigger = ScriptTrigger(TriggerKind.EVENT, event = null)
        }
        val errors = ExecutableScriptValidator.validate(prepared)
        assertTrue(errors.any { it.nodeId == null && it.fieldKeys == setOf("trigger") })
    }

    @Test
    fun `removed button-off event is rejected at load without silent remapping`() {
        // 廃止した BUTTON_OFF を保存した開発途中データは、別種別へ読み替えず破損として隔離します。
        val root = JsonParser.parseString(
            """
            {
              "formatVersion": 17,
              "id": "${UUID.randomUUID()}",
              "name": "removed-event",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 0,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": false, "intervalSeconds": 1.0},
              "graph": {"nodes": {}},
              "trigger": {"kind": "EVENT", "event": "BUTTON_OFF", "intervalSeconds": 1.0}
            }
            """.trimIndent(),
        ).asJsonObject
        assertTrue(
            PersistedScriptValidator.validate(root).any { it.contains("root/trigger/event") },
        )
    }

    @Test
    fun `explicit trigger skips legacy timer checks`() {
        // 従来欄だけを見れば矛盾する組み合わせでも、明示の起動方法が正本です。
        val prepared = script {
            timer.enabled = false
            activation = me.awabi2048.kantancommander.model.ActivationMode.ALWAYS_ACTIVE
            trigger = ScriptTrigger(TriggerKind.REDSTONE)
        }
        val errors = ExecutableScriptValidator.validate(prepared)
        assertFalse(errors.any { it.nodeId == null && "timer" in it.fieldKeys })
    }

    @Test
    fun `event arguments are predefined execution variables`() {
        fun eventScript(withTrigger: Boolean): DiskScript {
            val prepared = stateCondition {
                params["subject"] = ConditionSubject.OTHER_ENTITY.name
                // BLOCK_BREAK の操作者（ENTITY）は対象の一時変数参照として利用できます。
                targetSpec = TargetSpec(
                    TargetKind.TEMPORARY,
                    tempName = me.awabi2048.kantancommander.model.EventTriggerArguments.ACTOR,
                )
            }
            if (withTrigger) {
                prepared.trigger = ScriptTrigger(TriggerKind.EVENT, EventTriggerKind.BLOCK_BREAK)
            }
            return prepared
        }
        // イベント起動では event_actor が定義済みのため検証を通ります。
        assertFalse(
            ExecutableScriptValidator.validate(eventScript(true)).any { it.nodeId != null },
        )
        // 同じ記述でも起動方法がなければ未定義として拒否します。
        assertTrue(
            ExecutableScriptValidator.validate(eventScript(false)).any { it.nodeId != null },
        )
    }

    @Test
    fun `disk arguments seed the callee graph`() {
        fun argScript(withArguments: Boolean): DiskScript {
            val prepared = stateCondition {
                params["subject"] = ConditionSubject.VARIABLE.name
                params["variable"] = "counter"
                params["operator"] = "=="
                params["value"] = "%{count}%"
            }
            if (withArguments) {
                prepared.arguments = listOf(DiskArgument("count", ValueType.NUMBER))
            }
            return prepared
        }
        // 自ディスクの引数定義は、呼び出された実行開始時に定義済みのため検証を通ります。
        assertFalse(
            ExecutableScriptValidator.validate(argScript(true)).any { it.nodeId != null },
        )
        // 同じ記述でも引数定義がなければ未定義として拒否します。
        assertTrue(
            ExecutableScriptValidator.validate(argScript(false)).any { it.nodeId != null },
        )
    }

    @Test
    fun `disk call snapshot uses declared arguments`() {
        val callee = stateCondition {
            params["subject"] = ConditionSubject.VARIABLE.name
            params["variable"] = "counter"
            params["operator"] = "=="
            params["value"] = "%{count}%"
        }
        val caller = script {}
        GraphEditor.append(caller.graph, CommandType.DISK_CALL).apply {
            params["declaredArgs"] = "count:NUMBER"
            params["arg_count"] = "1"
            snapshot = callee.graph
        }
        // 呼び出し側の引数宣言が、スナップショット内部の定義として使われます。
        assertFalse(
            ExecutableScriptValidator.validate(caller).any { it.nodeId != null },
        )
    }
}
