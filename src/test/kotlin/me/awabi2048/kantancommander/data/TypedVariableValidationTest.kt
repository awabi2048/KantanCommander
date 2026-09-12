package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.TypedValue
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableOperation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #2 の変数操作コマンド検証テストです。
 *
 * scope（EXECUTION／WORLD）の切り替えと操作内容（DEFINE／SET／CHANGE／COPY／
 * DELETE）の成否条件を固定します。ENTITY scope は #22 で接続するため、
 * ここでは未対応として拒否されることを確認します。
 */
class TypedVariableValidationTest {
    private fun scriptWith(
        configure: me.awabi2048.kantancommander.model.CommandNode.() -> Unit,
    ): DiskScript {
        val script = DiskScript(name = "typed-variables", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.VARIABLE).configure()
        return script
    }

    private fun worldDefinitions(vararg pairs: Pair<String, TypedValue>): Map<String, TypedValue> =
        pairs.toMap()

    @Test
    fun `world define rejects duplicates and accepts fresh names`() {
        val duplicate = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "counter"
            params["type"] = ValueType.NUMBER.name
            params["operation"] = VariableOperation.DEFINE.name
            params["value"] = "1"
        }
        val definitions = worldDefinitions(
            "counter" to TypedValue(ValueType.NUMBER, numberValue = 0.0),
        )
        assertTrue(
            ExecutableScriptValidator.validate(duplicate, variableDefinitions = definitions)
                .any { it.message.contains("既に定義") },
        )

        val fresh = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "fresh"
            params["type"] = ValueType.BOOLEAN.name
            params["operation"] = VariableOperation.DEFINE.name
            params["value"] = "true"
        }
        assertFalse(
            ExecutableScriptValidator.validate(fresh, variableDefinitions = definitions)
                .any { it.nodeId != null },
        )
    }

    @Test
    fun `set upserts and enforces defined types`() {
        val definitions = worldDefinitions(
            "counter" to TypedValue(ValueType.NUMBER, numberValue = 0.0),
        )
        val mismatch = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "counter"
            params["type"] = ValueType.STRING.name
            params["operation"] = VariableOperation.SET.name
            params["value"] = "x"
        }
        assertTrue(
            ExecutableScriptValidator.validate(mismatch, variableDefinitions = definitions)
                .any { it.message.contains("型が一致しません") },
        )

        val implicit = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "brand_new"
            params["type"] = ValueType.NUMBER.name
            params["operation"] = VariableOperation.SET.name
            params["value"] = "2"
        }
        assertFalse(
            ExecutableScriptValidator.validate(implicit, variableDefinitions = definitions)
                .any { it.nodeId != null },
        )
    }

    @Test
    fun `change copy and delete require definitions`() {
        val definitions = worldDefinitions(
            "counter" to TypedValue(ValueType.NUMBER, numberValue = 0.0),
        )
        val changeMissing = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "missing"
            params["operation"] = VariableOperation.CHANGE.name
            params["changeMode"] = "ASSIGN"
            params["value"] = "1"
        }
        assertTrue(
            ExecutableScriptValidator.validate(changeMissing, variableDefinitions = definitions)
                .any { it.message.contains("未定義") },
        )

        val copyMissingSource = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "counter"
            params["operation"] = VariableOperation.COPY.name
            params["source"] = "missing"
        }
        assertTrue(
            ExecutableScriptValidator.validate(copyMissingSource, variableDefinitions = definitions)
                .any { it.message.contains("複写元変数が未定義") },
        )

        val copyTypeMismatch = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "text"
            params["operation"] = VariableOperation.COPY.name
            params["source"] = "counter"
        }
        val mixed = worldDefinitions(
            "counter" to TypedValue(ValueType.NUMBER, numberValue = 0.0),
            "text" to TypedValue(ValueType.STRING, stringValue = "a"),
        )
        assertTrue(
            ExecutableScriptValidator.validate(copyTypeMismatch, variableDefinitions = mixed)
                .any { it.message.contains("型が一致しません") },
        )

        val deleteMissing = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "missing"
            params["operation"] = VariableOperation.DELETE.name
        }
        assertTrue(
            ExecutableScriptValidator.validate(deleteMissing, variableDefinitions = definitions)
                .any { it.message.contains("未定義") },
        )

        val deletePresent = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "counter"
            params["operation"] = VariableOperation.DELETE.name
        }
        assertFalse(
            ExecutableScriptValidator.validate(deletePresent, variableDefinitions = definitions)
                .any { it.nodeId != null },
        )
    }

    @Test
    fun `entity scope and invalid scope are rejected`() {
        val entity = scriptWith {
            params["scope"] = "ENTITY"
            params["name"] = "tag"
            params["operation"] = VariableOperation.SET.name
            params["value"] = "1"
        }
        assertTrue(
            ExecutableScriptValidator.validate(entity).any { it.message.contains("未対応") },
        )

        val invalid = scriptWith {
            params["scope"] = "ORBIT"
            params["name"] = "tag"
            params["operation"] = VariableOperation.SET.name
            params["value"] = "1"
        }
        assertTrue(
            ExecutableScriptValidator.validate(invalid).any { it.message.contains("範囲が不正") },
        )
    }

    @Test
    fun `execution operations follow the graph flow definitions`() {
        // DEFINE した直後の CHANGE はフロー定義で解決します。
        val script = DiskScript(name = "execution-flow", owner = UUID.randomUUID())
        val define = GraphEditor.append(script.graph, CommandType.VARIABLE)
        define.params.putAll(
            mapOf(
                "scope" to "EXECUTION",
                "name" to "health",
                "type" to ValueType.NUMBER.name,
                "operation" to VariableOperation.DEFINE.name,
                "value" to "20",
            ),
        )
        val change = GraphEditor.append(script.graph, CommandType.VARIABLE)
        change.params.putAll(
            mapOf(
                "scope" to "EXECUTION",
                "name" to "health",
                "operation" to VariableOperation.CHANGE.name,
                "changeMode" to "ASSIGN",
                "value" to "10",
            ),
        )
        assertFalse(ExecutableScriptValidator.validate(script).any { it.nodeId == change.id })

        // フロー外の未定義 CHANGE は拒否します。
        val script2 = DiskScript(name = "execution-missing", owner = UUID.randomUUID())
        val change2 = GraphEditor.append(script2.graph, CommandType.VARIABLE)
        change2.params.putAll(
            mapOf(
                "scope" to "EXECUTION",
                "name" to "ghost",
                "operation" to VariableOperation.CHANGE.name,
                "changeMode" to "ASSIGN",
                "value" to "10",
            ),
        )
        assertTrue(
            ExecutableScriptValidator.validate(script2).any { it.message.contains("未定義") },
        )
    }

    @Test
    fun `boolean and vector values are validated by shape`() {
        val definitions = emptyMap<String, TypedValue>()
        val badBoolean = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "flag"
            params["type"] = ValueType.BOOLEAN.name
            params["operation"] = VariableOperation.DEFINE.name
            params["value"] = "yes"
        }
        assertTrue(
            ExecutableScriptValidator.validate(badBoolean, variableDefinitions = definitions)
                .any { it.fieldKeys == setOf("value") },
        )

        val badVector = scriptWith {
            params["scope"] = "WORLD"
            params["name"] = "direction"
            params["type"] = ValueType.VECTOR.name
            params["operation"] = VariableOperation.DEFINE.name
            params["x"] = "1"
            params["y"] = "oops"
            params["z"] = "3"
        }
        assertTrue(
            ExecutableScriptValidator.validate(badVector, variableDefinitions = definitions)
                .any { it.fieldKeys == setOf("y") },
        )
    }
}
