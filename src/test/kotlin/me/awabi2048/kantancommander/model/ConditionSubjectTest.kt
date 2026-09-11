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
