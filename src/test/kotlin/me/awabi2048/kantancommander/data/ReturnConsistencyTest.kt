package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #8 の戻り値型一貫性のテストです。
 *
 * 戻り元の変数は参照元scopeの定義表で解決します。EXECUTION はフロー解析の
 * 定義表、WORLD は永続定義表を見ます。scope を無視して WORLD 定義を引くと、
 * 同名変数の型で誤判定するため、その取り違えを回帰として防ぎます。
 */
class ReturnConsistencyTest {
    private fun script(configure: DiskScript.() -> Unit): DiskScript =
        DiskScript(name = "returns").apply(configure)

    private fun DiskScript.defineExecution(name: String, type: ValueType, value: String) {
        GraphEditor.append(graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "scope" to VariableScope.EXECUTION.name,
                "name" to name,
                "type" to type.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to value,
            ),
        )
    }

    private fun DiskScript.returnVariable(name: String, scope: VariableScope = VariableScope.EXECUTION) {
        GraphEditor.append(graph, CommandType.RETURN_VALUE).params.putAll(
            mapOf("sourceScope" to scope.name, "source" to name),
        )
    }

    private fun DiskScript.returnLiteral(value: String) {
        GraphEditor.append(graph, CommandType.RETURN_VALUE).params["value"] = value
    }

    @Test
    fun `execution sourced returns with mixed types are rejected`() {
        val prepared = script {
            defineExecution("total", ValueType.NUMBER, "1")
            defineExecution("label", ValueType.STRING, "x")
            returnVariable("total")
            returnVariable("label")
        }

        assertTrue(
            ExecutableScriptValidator.validate(prepared)
                .any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    @Test
    fun `world variable with same name does not disturb execution resolution`() {
        val prepared = script {
            defineExecution("total", ValueType.NUMBER, "1")
            returnVariable("total")
            returnLiteral("2")
        }
        val definitions = mapOf(
            "total" to TypedValue(ValueType.STRING, stringValue = "other"),
        )

        // EXECUTION の total は NUMBER であり、リテラル "2" と一致します。
        // 同名の WORLD 変数（STRING）で誤判定してはなりません。
        assertFalse(
            ExecutableScriptValidator.validate(prepared, variableDefinitions = definitions)
                .any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    @Test
    fun `mixed literals are still rejected`() {
        val prepared = script {
            returnLiteral("1")
            returnLiteral("x")
        }

        assertTrue(
            ExecutableScriptValidator.validate(prepared)
                .any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    /**
     * issue #8 の呼出側受取検査です。
     *
     * 呼び出し先の返り値は参照元scopeで解決します。EXECUTION は呼び出し側の
     * 引数宣言、WORLD は永続定義表を見ます。
     */
    private fun callerScript(
        receiverType: ValueType,
        declared: String,
        argValue: String,
        callee: DiskScript,
    ): DiskScript {
        val caller = DiskScript(name = "caller")
        GraphEditor.append(caller.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "scope" to VariableScope.EXECUTION.name,
                "name" to "recv",
                "type" to receiverType.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "0",
            ),
        )
        GraphEditor.append(caller.graph, CommandType.DISK_CALL).apply {
            // DISK_CALL は diskId 参照です。参照先は検証時の diskResolver で解決します。
            params["diskId"] = callee.id.toString()
            params["declaredArgs"] = declared
            params["arg_total"] = argValue
            params["returnScope"] = VariableScope.EXECUTION.name
            params["returnName"] = "recv"
        }
        return caller
    }

    private fun resolverFor(callee: DiskScript): (UUID) -> DiskScript? =
        { id -> if (id == callee.id) callee else null }

    private fun calleeReturning(sourceScope: VariableScope, source: String): DiskScript {
        val callee = DiskScript(name = "callee")
        GraphEditor.append(callee.graph, CommandType.RETURN_VALUE).params.putAll(
            mapOf("sourceScope" to sourceScope.name, "source" to source),
        )
        return callee
    }

    private fun calleeDefiningAndReturning(type: ValueType, value: String): DiskScript {
        val callee = DiskScript(name = "callee")
        GraphEditor.append(callee.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "scope" to VariableScope.EXECUTION.name,
                "name" to "work",
                "type" to type.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to value,
            ),
        )
        GraphEditor.append(callee.graph, CommandType.RETURN_VALUE).params.putAll(
            mapOf("sourceScope" to VariableScope.EXECUTION.name, "source" to "work"),
        )
        return callee
    }

    @Test
    fun `caller receiving callee argument ignores same named world variable`() {
        // NUMBER引数 total を返すディスクをNUMBER変数で受ける正常な呼び出しは、
        // 同名STRINGのWORLD変数があっても拒否されません。
        val callee = calleeReturning(VariableScope.EXECUTION, "total")
        val caller = callerScript(
            receiverType = ValueType.NUMBER,
            declared = "total:NUMBER",
            argValue = "1",
            callee = callee,
        )
        val definitions = mapOf(
            "total" to TypedValue(ValueType.STRING, stringValue = "other"),
        )

        assertFalse(
            ExecutableScriptValidator.validate(
                caller,
                variableDefinitions = definitions,
                diskResolver = resolverFor(callee),
            ).any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    @Test
    fun `caller receiving callee argument detects genuine mismatch`() {
        // 受取先がSTRINGの場合、NUMBER引数との不一致を検出します。
        // 旧処理（WORLD定義優先）では同名STRINGを見て見逃していました。
        val callee = calleeReturning(VariableScope.EXECUTION, "total")
        val caller = callerScript(
            receiverType = ValueType.STRING,
            declared = "total:NUMBER",
            argValue = "1",
            callee = callee,
        )
        val definitions = mapOf(
            "total" to TypedValue(ValueType.STRING, stringValue = "other"),
        )

        assertTrue(
            ExecutableScriptValidator.validate(
                caller,
                variableDefinitions = definitions,
                diskResolver = resolverFor(callee),
            ).any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    @Test
    fun `world sourced callee return keeps world resolution`() {        // WORLD参照の返り値は永続定義表で解決し、無関係な引数宣言に引きずられません。
        val callee = calleeReturning(VariableScope.WORLD, "flag")
        val caller = callerScript(
            receiverType = ValueType.STRING,
            declared = "total:NUMBER",
            argValue = "1",
            callee = callee,
        )
        val definitions = mapOf(
            "flag" to TypedValue(ValueType.STRING, stringValue = "ready"),
        )

        assertFalse(
            ExecutableScriptValidator.validate(
                caller,
                variableDefinitions = definitions,
                diskResolver = resolverFor(callee),
            ).any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    @Test
    fun `caller receiving callee local passes with same type receiver`() {
        // 呼出先でNUMBER変数を定義して返す場合、同型の受取は検証を通ります。
        // 旧処理では引数宣言にないため確定できず、検査自体が動いていませんでした。
        val callee = calleeDefiningAndReturning(ValueType.NUMBER, "1")
        val caller = callerScript(
            receiverType = ValueType.NUMBER,
            declared = "",
            argValue = "",
            callee = callee,
        )

        assertFalse(
            ExecutableScriptValidator.validate(caller, diskResolver = resolverFor(callee))
                .any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    @Test
    fun `caller receiving callee local rejects mismatched receiver`() {
        // 呼出先でNUMBER変数を定義して返す場合、STRING受取との不一致を検出します。
        // 旧処理では確定できず見逃していました。
        val callee = calleeDefiningAndReturning(ValueType.NUMBER, "1")
        val caller = callerScript(
            receiverType = ValueType.STRING,
            declared = "",
            argValue = "",
            callee = callee,
        )

        assertTrue(
            ExecutableScriptValidator.validate(caller, diskResolver = resolverFor(callee))
                .any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    /**
     * issue #8 の分岐合流テストです。
     *
     * 片側分岐だけが定義する変数は合流後に使えないため、RETURN解決を除外します。
     * 両分岐で同型を定義すれば合流後も使え、一貫性検証の対象になります。
     * 可視性規則自体は既存のデータフロー解析に従い、共通解決はその結果を使います。
     */
    private fun branchedScript(bothDefine: Boolean): DiskScript {
        val prepared = script {}
        val condition = GraphEditor.append(prepared.graph, CommandType.CONDITION)
        GraphEditor.append(prepared.graph, CommandType.VARIABLE).params.putAll(
            mapOf(
                "scope" to VariableScope.EXECUTION.name,
                "name" to "x",
                "type" to ValueType.NUMBER.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "1",
            ),
        )
        if (bothDefine) {
            GraphEditor.append(prepared.graph, CommandType.VARIABLE, condition.id).params.putAll(
                mapOf(
                    "scope" to VariableScope.EXECUTION.name,
                    "name" to "x",
                    "type" to ValueType.NUMBER.name,
                    "operation" to VariableOperation.DEFINE.name,
                    "value" to "2",
                ),
            )
        } else {
            GraphEditor.append(prepared.graph, CommandType.WAIT, condition.id).params["seconds"] = "1"
        }
        GraphEditor.appendMerge(prepared.graph, condition.id)
        GraphEditor.append(prepared.graph, CommandType.RETURN_VALUE).params.putAll(
            mapOf("sourceScope" to VariableScope.EXECUTION.name, "source" to "x"),
        )
        GraphEditor.append(prepared.graph, CommandType.RETURN_VALUE).params["value"] = "s"
        return prepared
    }

    @Test
    fun `branch merge drops unguaranteed definitions`() {
        // true分岐だけの定義は合流後に使えないため、STRINGリテラルのみ確定します。
        assertFalse(
            ExecutableScriptValidator.validate(branchedScript(false))
                .any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    @Test
    fun `branch merge keeps definitions guaranteed on all paths`() {
        // 両分岐で同型を定義すれば合流後も使え、STRINGリテラルと不一致になります。
        assertTrue(
            ExecutableScriptValidator.validate(branchedScript(true))
                .any { it.message.contains("戻り値の型が一致しません") },
        )
    }

    @Test
    fun `folded region resolves like unfolded`() {
        // 折り畳み展開と直接解析で同じ結果になります。
        val prepared = branchedScript(true)
        val conditionId = prepared.graph.nodes.values
            .first { it.type == CommandType.CONDITION }.id

        assertTrue(
            ExecutableScriptValidator.validate(prepared)
                .any { it.message.contains("戻り値の型が一致しません") },
        )
        assertTrue(GraphEditor.foldCondition(prepared.graph, conditionId))
        assertTrue(
            ExecutableScriptValidator.validate(prepared)
                .any { it.message.contains("戻り値の型が一致しません") },
        )
    }
}
