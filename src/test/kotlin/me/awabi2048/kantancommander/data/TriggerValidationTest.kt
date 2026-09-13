package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.TriggerKind
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
        GraphEditor.append(prepared.graph, CommandType.CONDITION).apply {
            params["subject"] = ConditionSubject.VALUE.name
            configure()
        }
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
                params["leftScope"] = "EXECUTION"
                // BLOCK_BREAK の操作者（ENTITY）は比較演算子を適用できます。
                params["leftName"] = me.awabi2048.kantancommander.model.EventTriggerArguments.ACTOR
                params["operator"] = "=="
                params["right"] = "x"
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
}
