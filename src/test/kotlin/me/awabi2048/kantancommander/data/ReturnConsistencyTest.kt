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
        DiskScript(name = "returns", owner = UUID.randomUUID()).apply(configure)

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
}
