package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.WorldVariableValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #15 の増分検証の回帰テストです。
 *
 * 値変更時の増分結果が全量検証と一致すること、全量へ委譲すべき条件で
 * nullを返すことを検証します。表示順序の差は吸収し、内容の一致を見ます。
 */
class IncrementalValidatorTest {
    /** 表示順序に依存せず内容一致を判定する署名です。 */
    private fun signature(errors: List<ScriptValidationError>): List<String> =
        errors.map { "${it.rendered()}|${it.code}|${it.phase}|${it.nodeId}|${it.fieldKeys.sorted()}" }.sorted()

    private fun assertEquivalent(previous: DiskScript, mutated: DiskScript) {
        val previousErrors = ExecutableScriptValidator.validate(previous)
        val incremental = IncrementalValidator.refresh(
            previousGraph = previous.graph,
            previousErrors = previousErrors,
            script = mutated,
        )
        assertNotNull(incremental, "値変更のはずが全量委譲されました")
        assertEquals(
            signature(ExecutableScriptValidator.validate(mutated)),
            signature(requireNotNull(incremental)),
        )
    }

    private fun linearScript(): DiskScript {
        val script = DiskScript(name = "incremental", owner = UUID.randomUUID())
        val wait = GraphEditor.append(script.graph, CommandType.WAIT)
        wait.params["seconds"] = "1"
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.params["distance"] = "5"
        return script
    }

    @Test
    fun `single field change matches full validation`() {
        val previous = linearScript()
        val mutated = linearScript().copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        mutated.graph.nodes.values.first { it.type == CommandType.WAIT }.params["seconds"] = "3"

        assertEquivalent(previous, mutated)
    }

    @Test
    fun `fixing an error incrementally matches full validation`() {
        val previous = linearScript()
        // TELEPORTは対象・移動先の未設定エラーを含みます。
        val mutated = linearScript().copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        mutated.graph.nodes.values.first { it.type == CommandType.WAIT }.params["seconds"] = "not-a-number"

        assertEquivalent(previous, mutated)
    }

    @Test
    fun `multiple field changes match full validation`() {
        val previous = linearScript()
        val mutated = linearScript().copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        mutated.graph.nodes.values.forEach { it.params["note"] = "x" }

        assertEquivalent(previous, mutated)
    }

    @Test
    fun `metadata only change matches full validation`() {
        val previous = linearScript()
        val mutated = linearScript().copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        mutated.graph.nodes.values.forEach { it.markConfigured("seconds") }

        assertEquivalent(previous, mutated)
    }

    @Test
    fun `condition kind change matches full validation`() {
        val previous = DiskScript(name = "condition", owner = UUID.randomUUID())
        val condition = GraphEditor.append(previous.graph, CommandType.CONDITION)
        condition.params["kind"] = ConditionKind.TARGET_EXISTS.name
        val mutated = DiskScript(name = "condition", owner = previous.owner).copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        mutated.graph.nodes.getValue(condition.id).params["kind"] = ConditionKind.PLAYER_STATE.name

        assertEquivalent(previous, mutated)
    }

    @Test
    fun `structural change falls back to full validation`() {
        val previous = linearScript()
        val mutated = linearScript().copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        GraphEditor.append(mutated.graph, CommandType.WAIT)

        assertNull(
            IncrementalValidator.refresh(
                previousGraph = previous.graph,
                previousErrors = ExecutableScriptValidator.validate(previous),
                script = mutated,
            ),
        )
    }

    @Test
    fun `temporary variable change falls back to full validation`() {
        val previous = DiskScript(name = "temp", owner = UUID.randomUUID())
        val first = GraphEditor.append(previous.graph, CommandType.TEMP_SET)
        first.params["name"] = "flag"
        first.params["tempType"] = TemporaryVariableType.NUMBER.name
        val second = GraphEditor.append(previous.graph, CommandType.WAIT)
        second.params["seconds"] = "\${flag}"
        val mutated = DiskScript(name = "temp", owner = previous.owner).copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        // 一時変数の可視性が下流へ波及し得るため全量へ委譲します。
        mutated.graph.nodes.getValue(first.id).params["name"] = "renamed"

        assertNull(
            IncrementalValidator.refresh(
                previousGraph = previous.graph,
                previousErrors = ExecutableScriptValidator.validate(previous),
                script = mutated,
            ),
        )
    }

    @Test
    fun `timer change with identical graph matches full validation`() {
        val previous = linearScript()
        val mutated = linearScript().copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
            timer = previous.timer.copy(enabled = true, intervalSeconds = 5.0),
        )

        assertEquivalent(previous, mutated)
    }

    @Test
    fun `variable definitions path matches full validation`() {
        val previous = DiskScript(name = "world-var", owner = UUID.randomUUID())
        val start = GraphEditor.append(previous.graph, CommandType.FOR_START)
        start.params["count"] = "\${limit}"
        val definitions = mapOf(
            "limit" to WorldVariableValue(VariableType.NUMBER, numberValue = 3.0),
        )
        val mutated = DiskScript(name = "world-var", owner = previous.owner).copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        mutated.graph.nodes.getValue(start.id).params["count"] = "\${missing}"

        val previousErrors = ExecutableScriptValidator.validate(previous, variableDefinitions = definitions)
        val incremental = IncrementalValidator.refresh(
            previousGraph = previous.graph,
            previousErrors = previousErrors,
            script = mutated,
            variableDefinitions = definitions,
        )
        assertNotNull(incremental)
        assertEquals(
            signature(ExecutableScriptValidator.validate(mutated, variableDefinitions = definitions)),
            signature(requireNotNull(incremental)),
        )
    }

    @Test
    fun `node local validation matches full validation slice`() {
        val script = linearScript()
        val full = ExecutableScriptValidator.validate(script)
        val target = script.graph.nodes.values.first { it.type == CommandType.WAIT }

        val local = ExecutableScriptValidator.validateNodeLocal(script.graph, target.id)

        assertEquals(
            signature(full.filter { it.nodeId == target.id }),
            signature(local),
        )
    }

    @Test
    fun `node local validation of unknown id is empty`() {
        val script = linearScript()

        assertEquals(
            emptyList<ScriptValidationError>(),
            ExecutableScriptValidator.validateNodeLocal(script.graph, UUID.randomUUID()),
        )
    }

    @Test
    fun `unused variable operation import anchor keeps compilation honest`() {
        // 将来VARIABLE系の波及条件を追加する際の錨です。
        assertEquals(VariableOperation.DEFINE, VariableOperation.valueOf("DEFINE"))
    }
}
