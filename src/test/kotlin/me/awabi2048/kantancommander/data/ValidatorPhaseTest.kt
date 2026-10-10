package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.error.ErrorCode
import me.awabi2048.kantancommander.error.ValidationPhase
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ScriptTrigger
import me.awabi2048.kantancommander.model.TriggerKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ValidatorPhaseTest {
    @Test
    fun `edit time and preflight phases are distinguished by variable definitions`() {
        val script = DiskScript(name = "phase")
        // タイマー間隔の範囲違反は edit-time/preflight 両方で検出される代表例です。
        script.trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = 0.0)

        val editErrors = ExecutableScriptValidator.validate(script, variableDefinitions = null)
        val preflightErrors = ExecutableScriptValidator.validate(script, variableDefinitions = emptyMap())

        val editTimer = editErrors.single { it.code == ErrorCode.TIMER_RANGE }
        val preflightTimer = preflightErrors.single { it.code == ErrorCode.TIMER_RANGE }
        assertEquals(ValidationPhase.EDIT_TIME, editTimer.phase)
        assertEquals(ValidationPhase.PREFLIGHT, preflightTimer.phase)
    }

    @Test
    fun `structural errors keep node identity and persisted phase`() {
        val script = DiskScript(name = "structural")
        val first = GraphEditor.append(script.graph, CommandType.TELEPORT)
        val second = GraphEditor.append(script.graph, CommandType.TELEPORT)
        // 複数親を人為的に作り、構造エラーの nodeId 保持を確認します。
        second.next = first.id

        val errors = ExecutableScriptValidator.validate(script, variableDefinitions = emptyMap())
        val structural = errors.filter { it.phase == ValidationPhase.PERSISTED_STRUCTURAL }
        assertTrue(structural.isNotEmpty(), "構造エラーは PERSISTED_STRUCTURAL で区別される")
        assertTrue(structural.any { it.nodeId != null }, "構造エラーは nodeId を保持する")
        assertTrue(structural.all { it.code != ErrorCode.CONFIG_INVALID || it.message.isNotBlank() })
    }

    @Test
    fun `graph structured validation preserves error codes`() {
        val script = DiskScript(name = "graph-code")
        GraphEditor.append(script.graph, CommandType.TELEPORT)

        val structured = GraphValidator.validateStructured(script.graph)
        // 正常グラフは構造エラーを出しません。前提が崩れた場合だけ後続の検証が意味を持つため、
        // 空であること自体を固定します（code は非null型のため、all での存在確認は行いません）。
        assertTrue(structured.isEmpty(), "正常グラフは構造エラーを出さない")

        val broken = DiskScript(name = "broken")
        GraphEditor.append(broken.graph, CommandType.TELEPORT)
        // 開始ノードを壊して MISSING 相当の構造エラーを作ります。
        broken.graph.nodes.values.forEach { it.next = UUID.randomUUID() }
        val brokenStructured = GraphValidator.validateStructured(broken.graph)
        assertTrue(brokenStructured.any { it.code == ErrorCode.GRAPH_UNKNOWN_REFERENCE })
        assertTrue(brokenStructured.all { it.nodeId != null || it.code == ErrorCode.GRAPH_NODE_LIMIT })
    }

    @Test
    fun `validation errors convert to common execution errors`() {
        val script = DiskScript(name = "convert")
        script.trigger = ScriptTrigger(TriggerKind.TIMER, intervalSeconds = 0.0)

        val errors = ExecutableScriptValidator.validate(script, variableDefinitions = emptyMap())
        val common = errors.map { it.toExecutionError(script.id) }
        assertTrue(common.all { it.scriptId == script.id })
        assertTrue(common.any { it.code == ErrorCode.TIMER_RANGE && it.phase == ValidationPhase.PREFLIGHT })
    }
}
