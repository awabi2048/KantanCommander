package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.TypedValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
            previousSeeds = ExecutableScriptValidator.seedTemporaries(previous),
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
    fun `condition subject change matches full validation`() {
        val previous = DiskScript(name = "condition", owner = UUID.randomUUID())
        val condition = GraphEditor.append(previous.graph, CommandType.CONDITION)
        condition.params["subject"] = ConditionSubject.OTHER_ENTITY.name
        val mutated = DiskScript(name = "condition", owner = previous.owner).copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        mutated.graph.nodes.getValue(condition.id).params["subject"] = ConditionSubject.PLAYER.name

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
        first.params["tempType"] = ValueType.NUMBER.name
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
            "limit" to TypedValue(ValueType.NUMBER, numberValue = 3.0),
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
    fun `changed node referencing disk arguments matches full validation`() {
        // issue #8: 引数を参照するノードの編集は、種子付きで増分検証します。
        fun scriptWithArgs(right: String): DiskScript {
            val script = DiskScript(name = "args", owner = UUID.randomUUID())
            script.arguments = listOf(
                me.awabi2048.kantancommander.model.DiskArgument("count", ValueType.NUMBER),
            )
            val condition = GraphEditor.append(script.graph, CommandType.CONDITION)
            condition.params["subject"] = ConditionSubject.VALUE.name
            condition.params["leftScope"] = "EXECUTION"
            condition.params["leftName"] = "count"
            condition.params["operator"] = "=="
            condition.params["right"] = right
            return script
        }
        val previous = scriptWithArgs("1")
        val mutated = scriptWithArgs("1").copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
            arguments = previous.arguments.toList(),
        )
        mutated.graph.nodes.values.first { it.type == CommandType.CONDITION }.params["right"] = "2"

        assertEquivalent(previous, mutated)
    }

    @Test
    fun `argument definition change falls back to full validation`() {
        // issue #8: 引数定義自体の変更は全ノードの定義可視性に波及するため、
        // グラフ不変でも増分検証を使わず全量へ委譲します。
        val previous = DiskScript(name = "args", owner = UUID.randomUUID())
        previous.arguments = listOf(
            me.awabi2048.kantancommander.model.DiskArgument("count", ValueType.NUMBER),
        )
        GraphEditor.append(previous.graph, CommandType.WAIT).params["seconds"] = "1"
        val mutated = DiskScript(name = "args", owner = previous.owner).copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
            arguments = listOf(
                me.awabi2048.kantancommander.model.DiskArgument("count", ValueType.STRING),
            ),
        )

        assertNull(
            IncrementalValidator.refresh(
                previousGraph = previous.graph,
                previousErrors = ExecutableScriptValidator.validate(previous),
                script = mutated,
                previousSeeds = ExecutableScriptValidator.seedTemporaries(previous),
            ),
        )
    }

    @Test
    fun `return value edit falls back to full validation`() {        // issue #8: 戻り値の型一貫性はグラフ全体で判定するため、
        // RETURNノード自体の変更では増分検証を使わず全量へ委譲します。
        val previous = DiskScript(name = "returns", owner = UUID.randomUUID())
        val returned = GraphEditor.append(previous.graph, CommandType.RETURN_VALUE)
        returned.params["value"] = "1"
        val mutated = DiskScript(name = "returns", owner = previous.owner).copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )
        mutated.graph.nodes.getValue(returned.id).params["value"] = "x"

        assertNull(
            IncrementalValidator.refresh(
                previousGraph = previous.graph,
                previousErrors = ExecutableScriptValidator.validate(previous),
                script = mutated,
                previousSeeds = ExecutableScriptValidator.seedTemporaries(previous),
            ),
        )
    }

    @Test
    fun `unused variable operation import anchor keeps compilation honest`() {
        // 将来VARIABLE系の波及条件を追加する際の錨です。
        assertEquals(VariableOperation.DEFINE, VariableOperation.valueOf("DEFINE"))
    }

    private fun executionScript(): DiskScript {
        val script = DiskScript(name = "execution", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "scope" to "EXECUTION",
                "name" to "total",
                "type" to ValueType.NUMBER.name,
                "operation" to VariableOperation.DEFINE.name,
                "changeMode" to "ASSIGN",
                "value" to "1",
            ),
        )
        GraphEditor.append(script.graph, CommandType.RETURN_VALUE).params.putAll(
            mapOf("sourceScope" to "EXECUTION", "source" to "total"),
        )
        return script
    }

    private fun mutatedCopy(previous: DiskScript): DiskScript =
        DiskScript(name = previous.name, owner = previous.owner).copy(
            id = previous.id,
            graph = previous.graph.deepCopy(),
        )

    @Test
    fun `execution define type change falls back to full validation`() {
        // issue #8: DEFINEの型変更は下流の定義可視性に波及するため、
        // 戻り値型の不一致が残ってもGUIに以前の結果を残しません。
        val previous = executionScript()
        val mutated = mutatedCopy(previous)
        mutated.graph.nodes.values
            .first { it.type == CommandType.VARIABLE }
            .params["type"] = ValueType.STRING.name

        assertNull(
            IncrementalValidator.refresh(
                previousGraph = previous.graph,
                previousErrors = ExecutableScriptValidator.validate(previous),
                script = mutated,
                previousSeeds = ExecutableScriptValidator.seedTemporaries(previous),
            ),
        )
    }

    @Test
    fun `define to change operation falls back to full validation`() {
        // issue #8: 操作変更で定義寄与が消える場合も、新世代だけを見ると
        // 検出できないため、旧世代と合わせて判定します。
        val previous = executionScript()
        val mutated = mutatedCopy(previous)
        mutated.graph.nodes.values
            .first { it.type == CommandType.VARIABLE }
            .params["operation"] = VariableOperation.CHANGE.name

        assertNull(
            IncrementalValidator.refresh(
                previousGraph = previous.graph,
                previousErrors = ExecutableScriptValidator.validate(previous),
                script = mutated,
                previousSeeds = ExecutableScriptValidator.seedTemporaries(previous),
            ),
        )
    }

    @Test
    fun `change value edit stays incremental`() {
        // issue #8: 定義の有無を変えないCHANGEの値編集は、下流へ波及しないため
        // 増分のまま全量と一致します。委譲の過剰適用を防ぎます。
        val previous = executionScript()
        val change = GraphEditor.append(previous.graph, CommandType.VARIABLE)
        change.params.putAll(
            mapOf(
                "scope" to "EXECUTION",
                "name" to "total",
                "operation" to VariableOperation.CHANGE.name,
                "changeMode" to "ASSIGN",
                "value" to "2",
            ),
        )
        val mutated = mutatedCopy(previous)
        mutated.graph.nodes.getValue(change.id).params["value"] = "3"

        val previousErrors = ExecutableScriptValidator.validate(previous)
        val incremental = IncrementalValidator.refresh(
            previousGraph = previous.graph,
            previousErrors = previousErrors,
            script = mutated,
            previousSeeds = ExecutableScriptValidator.seedTemporaries(previous),
        )
        assertNotNull(incremental, "定義を変えない編集のはずが全量委譲されました")
        assertEquals(
            signature(ExecutableScriptValidator.validate(mutated)),
            signature(requireNotNull(incremental)),
        )
    }

    @Test
    fun `folded inner define change falls back to full validation`() {
        // issue #8: 折り畳み内部の定義変更も、展開後の定義可視性に波及するため
        // 全量へ委譲します。外側ノードだけを見ると検出できません。
        val previous = DiskScript(name = "folded", owner = UUID.randomUUID())
        val condition = GraphEditor.append(previous.graph, CommandType.CONDITION)
        GraphEditor.append(previous.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "scope" to "EXECUTION",
                "name" to "total",
                "type" to ValueType.NUMBER.name,
                "operation" to VariableOperation.DEFINE.name,
                "changeMode" to "ASSIGN",
                "value" to "1",
            ),
        )
        GraphEditor.append(previous.graph, CommandType.WAIT, condition.id).params["seconds"] = "1"
        GraphEditor.appendMerge(previous.graph, condition.id)
        GraphEditor.append(previous.graph, CommandType.RETURN_VALUE).params.putAll(
            mapOf("sourceScope" to "EXECUTION", "source" to "total"),
        )
        assertTrue(GraphEditor.foldCondition(previous.graph, condition.id))

        val mutated = mutatedCopy(previous)
        val foldedId = mutated.graph.nodes.values.first { it.type == CommandType.FOLDED_IF }.id
        mutated.graph.nodes.getValue(foldedId).foldedGraph
            ?.nodes?.values?.first { it.type == CommandType.VARIABLE }
            ?.params?.set("type", ValueType.STRING.name)
            ?: error("folded test setup is broken")

        assertNull(
            IncrementalValidator.refresh(
                previousGraph = previous.graph,
                previousErrors = ExecutableScriptValidator.validate(previous),
                script = mutated,
                previousSeeds = ExecutableScriptValidator.seedTemporaries(previous),
            ),
        )
    }
}
