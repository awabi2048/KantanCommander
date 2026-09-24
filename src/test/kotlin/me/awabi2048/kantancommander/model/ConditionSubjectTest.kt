package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.PersistedScriptValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #27: 判定対象（ConditionSubject）の単一正本テストです。
 *
 * 条件の読込・検証・実行・表示・出力は `subject` 欄だけを参照します。
 * 旧 `kind` 欄の変換処理は残さず、旧値は未設定として検証層で拒否します。
 */
class ConditionSubjectTest {
    @Test
    fun `missing or invalid subjects resolve to null without guessing`() {
        val missing = CommandType.CONDITION.newNode().apply { params.remove("subject") }
        assertNull(missing.conditionSubject())

        val invalid = CommandType.CONDITION.newNode().apply {
            params["subject"] = "UNKNOWN_SUBJECT"
        }
        assertNull(invalid.conditionSubject())

        val blank = CommandType.CONDITION.newNode().apply { params["subject"] = "" }
        assertNull(blank.conditionSubject())
    }

    @Test
    fun `setting a subject keeps a single source`() {
        val node = CommandType.CONDITION.newNode()
        node.setConditionSubject(ConditionSubject.PLAYER)
        assertEquals(ConditionSubject.PLAYER.name, node.params["subject"])
        assertEquals(ConditionSubject.PLAYER, node.conditionSubject())
    }

    @Test
    fun `setting a subject drops a stale kind field`() {
        val node = CommandType.CONDITION.newNode().apply {
            params["subject"] = ConditionSubject.BLOCK.name
            params["kind"] = "TARGET_EXISTS"
        }
        node.setConditionSubject(ConditionSubject.BLOCK)
        assertEquals(ConditionSubject.BLOCK, node.conditionSubject())
        assertFalse(node.params.containsKey("kind"))
    }

    @Test
    fun `new nodes default to the other entity subject`() {
        val node = CommandType.CONDITION.newNode()
        assertEquals(ConditionSubject.OTHER_ENTITY.name, node.params["subject"])
        assertFalse(node.params.containsKey("kind"))
        assertEquals(ConditionSubject.OTHER_ENTITY, node.conditionSubject())
    }

    @Test
    fun `value comparison validates names operators and static types`() {
        fun valueNode(configure: CommandNode.() -> Unit): DiskScript {
            val script = DiskScript(name = "value-subject", owner = UUID.randomUUID())
            GraphEditor.append(script.graph, CommandType.CONDITION).apply {
                params["subject"] = ConditionSubject.VALUE.name
                configure()
            }
            return script
        }
        val definitions = mapOf(
            "counter" to TypedValue(ValueType.NUMBER, numberValue = 1.0),
            "label" to TypedValue(ValueType.STRING, stringValue = "a"),
        )

        // 正常系は検証を通ります。
        val valid = valueNode {
            params["leftScope"] = "WORLD"
            params["leftName"] = "counter"
            params["operator"] = ">="
            params["right"] = "2"
        }
        assertFalse(
            ExecutableScriptValidator.validate(valid, variableDefinitions = definitions).any { it.nodeId != null },
        )

        // 未定義の左辺は拒否します。
        val missing = valueNode {
            params["leftScope"] = "WORLD"
            params["leftName"] = "ghost"
            params["operator"] = "=="
            params["right"] = "1"
        }
        assertTrue(
            ExecutableScriptValidator.validate(missing, variableDefinitions = definitions)
                .any { it.message.contains("未定義") },
        )

        // 型に使えない演算子は拒否します。
        val mismatch = valueNode {
            params["leftScope"] = "WORLD"
            params["leftName"] = "label"
            params["operator"] = ">"
            params["right"] = "b"
        }
        assertTrue(
            ExecutableScriptValidator.validate(mismatch, variableDefinitions = definitions)
                .any { it.message.contains("使えない比較方法") },
        )

        // ENTITY scope の左辺は型が実行時に決まるため、静的には受け付けます。
        // 対象数・定義有無の契約は実行時に判定します。
        val entity = valueNode {
            params["leftScope"] = "ENTITY"
            params["leftName"] = "mark"
            params["operator"] = "=="
            params["right"] = "1"
        }
        assertFalse(
            ExecutableScriptValidator.validate(entity, variableDefinitions = definitions)
                .any { it.nodeId != null },
        )
    }

    @Test
    fun `value conditions require a comparison operator`() {
        fun valueNode(configure: CommandNode.() -> Unit): DiskScript {
            val script = DiskScript(name = "direct-subject", owner = UUID.randomUUID())
            GraphEditor.append(script.graph, CommandType.CONDITION).apply {
                params["subject"] = ConditionSubject.VALUE.name
                configure()
            }
            return script
        }
        val definitions = mapOf("counter" to TypedValue(ValueType.NUMBER, numberValue = 1.0))

        val direct = valueNode {
            params["leftScope"] = "WORLD"
            params["leftName"] = "counter"
            params["operator"] = ""
            params["right"] = ""
        }
        assertTrue(
            ExecutableScriptValidator.validate(direct, variableDefinitions = definitions)
                .any { it.message.contains("比較方法を選択") },
        )

        // 演算子を指定した場合は、未定義の左辺を従来どおり検出します。
        val missing = valueNode {
            params["leftScope"] = "WORLD"
            params["leftName"] = "ghost"
            params["operator"] = "=="
            params["right"] = "1"
        }
        assertTrue(
            ExecutableScriptValidator.validate(missing, variableDefinitions = definitions)
                .any { it.message.contains("未定義") },
        )

        // EXECUTION scope でも演算子なしの真偽値条件は認めません。
        val execution = valueNode {
            params["leftScope"] = "EXECUTION"
            params["leftName"] = "ready"
            params["operator"] = ""
            params["right"] = ""
        }.apply {
            arguments = listOf(me.awabi2048.kantancommander.model.DiskArgument("ready", ValueType.NUMBER))
        }
        assertTrue(
            ExecutableScriptValidator.validate(execution, variableDefinitions = definitions)
                .any { it.message.contains("比較方法を選択") },
        )
    }

    @Test
    fun `region predicates use the node region instead of right values`() {
        fun valueNode(configure: CommandNode.() -> Unit): DiskScript {
            val script = DiskScript(name = "region-subject", owner = UUID.randomUUID())
            GraphEditor.append(script.graph, CommandType.CONDITION).apply {
                params["subject"] = ConditionSubject.VALUE.name
                configure()
            }
            return script
        }
        val definitions = mapOf(
            "spot" to TypedValue(
                ValueType.LOCATION,
                location = SavedLocation(1.0, 2.0, 3.0, 0f, 0f),
            ),
            "counter" to TypedValue(ValueType.NUMBER, numberValue = 1.0),
        )
        fun region(): RegionSpec = RegionSpec(
            anchor = RegionAnchorSpec(RegionAnchorKind.CONTROL_BLOCK),
            dx = 2.0,
            dy = 2.0,
            dz = 2.0,
        )

        // LOCATION変数の領域述語は検証を通ります。
        val inside = valueNode {
            params["leftScope"] = "WORLD"
            params["leftName"] = "spot"
            params["operator"] = "in_region"
            params["right"] = ""
            regionSpec = region()
        }
        assertFalse(
            ExecutableScriptValidator.validate(inside, variableDefinitions = definitions)
                .any { it.nodeId != null },
        )

        // 領域未設定は拒否します。
        val missing = valueNode {
            params["leftScope"] = "WORLD"
            params["leftName"] = "spot"
            params["operator"] = "not_in_region"
            params["right"] = ""
        }
        assertTrue(
            ExecutableScriptValidator.validate(missing, variableDefinitions = definitions)
                .any { it.message.contains("領域が未設定") },
        )

        // LOCATION／ENTITY以外への領域演算子は拒否します。
        val wrongType = valueNode {
            params["leftScope"] = "WORLD"
            params["leftName"] = "counter"
            params["operator"] = "in_region"
            params["right"] = ""
            regionSpec = region()
        }
        assertTrue(
            ExecutableScriptValidator.validate(wrongType, variableDefinitions = definitions)
                .any { it.message.contains("使えない比較方法") },
        )

        // ENTITY scope の領域述語は型が実行時に決まるため、静的には受け付けます。
        val entity = valueNode {
            params["leftScope"] = "ENTITY"
            params["leftName"] = "mark"
            params["operator"] = "in_region"
            params["right"] = ""
            regionSpec = region()
        }
        assertFalse(
            ExecutableScriptValidator.validate(entity, variableDefinitions = definitions)
                .any { it.nodeId != null },
        )
    }

    @Test
    fun `persisted layer rejects unknown subjects but allows missing ones`() {
        fun rootWithSubject(subject: String?): com.google.gson.JsonObject {
            val params = if (subject == null) "{}" else """{"subject": "$subject"}"""
            val nodeId = UUID.randomUUID()
            return com.google.gson.JsonParser.parseString(
                """
                {
                  "formatVersion": 13,
                  "id": "${UUID.randomUUID()}",
                  "name": "subject-check",
                  "owner": "${UUID.randomUUID()}",
                  "createdAt": 0,
                  "activation": "NEEDS_REDSTONE",
                  "timer": {"enabled": false, "intervalSeconds": 1.0},
                  "graph": {
                    "nodes": {
                      "$nodeId": {
                        "id": "$nodeId",
                        "type": "CONDITION",
                        "params": $params
                      }
                    }
                  }
                }
                """.trimIndent(),
            ).asJsonObject
        }

        // 不正値は破損として隔離対象にする。
        assertTrue(
            PersistedScriptValidator.validate(rootWithSubject("UNKNOWN_SUBJECT"))
                .any { it.contains("params/subject") },
        )
        // 欠落（編集中）は許容し、実行前検証に委ねる。
        assertTrue(PersistedScriptValidator.validate(rootWithSubject(null)).isEmpty())
        assertTrue(PersistedScriptValidator.validate(rootWithSubject(ConditionSubject.BLOCK.name)).isEmpty())
    }

    @Test
    fun `player subject without a target fails validation`() {
        val script = DiskScript(name = "subject-player", owner = UUID.randomUUID())
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            setConditionSubject(ConditionSubject.PLAYER)
        }
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == condition.id && it.fieldKeys == setOf("condition")
            },
        )
    }

    @Test
    fun `other entity subject without a target fails validation`() {
        val script = DiskScript(name = "subject-other", owner = UUID.randomUUID())
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            setConditionSubject(ConditionSubject.OTHER_ENTITY)
        }
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == condition.id && it.fieldKeys == setOf("condition")
            },
        )
    }

    @Test
    fun `missing subject fails validation on the subject tab`() {
        // 旧 `kind` のみの保存は変換せず、判定対象タブの未設定として拒否します。
        val script = DiskScript(name = "legacy-kind", owner = UUID.randomUUID())
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            params.remove("subject")
            params["kind"] = "TARGET_EXISTS"
        }
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == condition.id && it.fieldKeys == setOf("kind")
            },
        )
    }
}
