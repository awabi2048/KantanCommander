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
 * 旧 `kind` 欄は保存境界の正規化（`normalizeConditionSubjects`）で移すだけで、
 * 分岐・表示の正本にしません。
 */
class ConditionSubjectTest {
    @Test
    fun `legacy kinds map to subjects on load`() {
        assertEquals(ConditionSubject.PLAYER, ConditionKind.PLAYER_STATE.toSubject())
        assertEquals(ConditionSubject.VARIABLE, ConditionKind.VARIABLE_STATE.toSubject())
        assertEquals(ConditionSubject.BLOCK, ConditionKind.BLOCK_STATE.toSubject())
        assertEquals(ConditionSubject.CONTROL_BLOCK, ConditionKind.CONTROL_BLOCK_STATE.toSubject())
    }

    @Test
    fun `legacy target exists splits by target kind on load`() {
        // 対象未設定は中立なその他扱いです。
        assertEquals(ConditionSubject.OTHER_ENTITY, ConditionKind.TARGET_EXISTS.toSubject(null))
        val playerTarget = TargetSpec(kind = TargetKind.NEARBY_PLAYERS)
        assertEquals(ConditionSubject.PLAYER, ConditionKind.TARGET_EXISTS.toSubject(playerTarget))
        val entityTarget = TargetSpec(kind = TargetKind.NEARBY_ENTITIES, entityType = "minecraft:zombie")
        assertEquals(ConditionSubject.OTHER_ENTITY, ConditionKind.TARGET_EXISTS.toSubject(entityTarget))
    }

    @Test
    fun `missing or invalid subjects resolve to null without guessing`() {
        val missing = CommandType.CONDITION.newNode().apply { params.remove("subject") }
        assertNull(missing.conditionSubject())

        val invalid = CommandType.CONDITION.newNode().apply {
            params["subject"] = "UNKNOWN_SUBJECT"
            params["kind"] = ConditionKind.PLAYER_STATE.name
        }
        // 旧 `kind` が有効でも推測フォールバックしません。正規化の責務は保存境界です。
        assertNull(invalid.conditionSubject())
    }

    @Test
    fun `setting a subject keeps a single source`() {
        val node = CommandType.CONDITION.newNode()
        node.setConditionSubject(ConditionSubject.PLAYER)
        assertEquals(ConditionSubject.PLAYER.name, node.params["subject"])
        assertFalse(node.params.containsKey("kind"))
        assertEquals(ConditionSubject.PLAYER, node.conditionSubject())

        node.setConditionSubject(ConditionSubject.OTHER_ENTITY)
        assertEquals(ConditionSubject.OTHER_ENTITY.name, node.params["subject"])
        assertFalse(node.params.containsKey("kind"))
        assertEquals(ConditionSubject.OTHER_ENTITY, node.conditionSubject())
    }

    @Test
    fun `new nodes default to the other entity subject`() {
        val node = CommandType.CONDITION.newNode()
        assertEquals(ConditionSubject.OTHER_ENTITY.name, node.params["subject"])
        assertFalse(node.params.containsKey("kind"))
        assertEquals(ConditionSubject.OTHER_ENTITY, node.conditionSubject())
    }

    @Test
    fun `normalization migrates legacy kind saves`() {
        val graph = CommandGraph.empty()
        val legacy = CommandNode(type = CommandType.CONDITION).apply {
            params["kind"] = ConditionKind.PLAYER_STATE.name
            targetSpec = TargetSpec(kind = TargetKind.NEAREST_PLAYER)
        }
        val legacyExists = CommandNode(type = CommandType.CONDITION).apply {
            params["kind"] = ConditionKind.TARGET_EXISTS.name
            targetSpec = TargetSpec(kind = TargetKind.NEARBY_ENTITIES, entityType = "minecraft:zombie")
        }
        val unknown = CommandNode(type = CommandType.CONDITION).apply {
            params["kind"] = "UNKNOWN_KIND"
        }
        graph.nodes[legacy.id] = legacy
        graph.nodes[legacyExists.id] = legacyExists
        graph.nodes[unknown.id] = unknown

        graph.normalizeConditionSubjects()

        assertEquals(ConditionSubject.PLAYER, legacy.conditionSubject())
        assertFalse(legacy.params.containsKey("kind"))
        assertEquals(ConditionSubject.OTHER_ENTITY, legacyExists.conditionSubject())
        assertFalse(legacyExists.params.containsKey("kind"))
        // 未知の旧値は落とさず、検証層の未設定扱いに委ねます。
        assertNull(unknown.conditionSubject())
        assertFalse(unknown.params.containsKey("kind"))
    }

    @Test
    fun `normalization keeps explicit subjects and drops stale kinds`() {
        val graph = CommandGraph.empty()
        val node = CommandNode(type = CommandType.CONDITION).apply {
            params["subject"] = ConditionSubject.BLOCK.name
            params["kind"] = ConditionKind.TARGET_EXISTS.name
        }
        graph.nodes[node.id] = node

        graph.normalizeConditionSubjects()

        assertEquals(ConditionSubject.BLOCK, node.conditionSubject())
        assertFalse(node.params.containsKey("kind"))
    }

    @Test
    fun `normalization reaches snapshot and folded graphs`() {
        val inner = CommandNode(type = CommandType.CONDITION).apply {
            params["kind"] = ConditionKind.VARIABLE_STATE.name
        }
        val outer = CommandNode(type = CommandType.CONDITION).apply {
            params["subject"] = ConditionSubject.OTHER_ENTITY.name
            foldedGraph = CommandGraph(nodes = linkedMapOf(inner.id to inner))
        }
        val graph = CommandGraph(nodes = linkedMapOf(outer.id to outer))

        graph.normalizeConditionSubjects()

        assertEquals(ConditionSubject.VARIABLE, inner.conditionSubject())
        assertFalse(inner.params.containsKey("kind"))
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
        // 欠落（旧保存・編集中）は許容し、読込時正規化と実行前検証に委ねる。
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
        // 正規化前の旧保存（`kind` のみ）は判定対象タブの未設定として拒否します。
        val script = DiskScript(name = "legacy-kind", owner = UUID.randomUUID())
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            params.remove("subject")
            params["kind"] = ConditionKind.TARGET_EXISTS.name
        }
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == condition.id && it.fieldKeys == setOf("kind")
            },
        )
    }
}
