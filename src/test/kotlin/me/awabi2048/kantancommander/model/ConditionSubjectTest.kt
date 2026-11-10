package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.PersistedScriptValidator
import me.awabi2048.kantancommander.data.ScriptStore
import me.awabi2048.kantancommander.dsl.DslCompiler
import me.awabi2048.kantancommander.dsl.DslParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger

/**
 * issue #27: 判定対象（ConditionSubject）の単一正本テストです。
 *
 * 条件の読込・検証・実行・表示・出力は `subject` 欄だけを参照します。
 * 旧 `kind` 欄の変換処理は残さず、旧値は未設定として検証層で拒否します。
 */
class ConditionSubjectTest {
    @Test
    fun `判定種類の変更時のみエンティティ指定を消去する`() {
        for (before in listOf(ConditionSubject.PLAYER, ConditionSubject.OTHER_ENTITY)) {
            for (after in ConditionSubject.entries) {
                val node = CommandType.CONDITION.newNode()
                node.setConditionSubject(before)
                val target = TargetSpec(TargetKind.NEARBY_ENTITIES, tag = "sample")
                node.targetSpec = target
                node.markConfigured("target")

                node.setConditionSubject(after)

                if (before == after) {
                    assertEquals(target, node.targetSpec)
                    assertTrue(node.isExplicitlyConfigured("target"))
                } else {
                    assertNull(node.targetSpec, "$before から $after")
                    assertFalse(node.isExplicitlyConfigured("target"))
                }
            }
        }
    }

    @Test
    fun `判定種類の切替後は保存直後も再読込後も旧対象がない`(@TempDir temp: Path) {
        val store = ScriptStore(temp.toFile(), Logger.getAnonymousLogger())
        val script = store.createPlacement("repro")
        val source = """program "repro"; if (exists(kind = "nearby_entities", type = "minecraft:zombie", tag = "sample")) { return; }"""
        script.graph = DslCompiler().compile(DslParser.parse(source)).graph
        store.save(script)
        val changed = requireNotNull(store.load(script.id))
        val node = changed.graph.nodes.values.first { it.type == CommandType.CONDITION }
        assertEquals("sample", node.targetSpec?.tag)
        node.setConditionSubject(ConditionSubject.CONTROL_BLOCK)
        node.params["redstoneSignal"] = "1.."
        store.save(changed)

        assertNull(requireNotNull(store.load(script.id)).graph.nodes.values.first { it.type == CommandType.CONDITION }.targetSpec)
        val reloaded = requireNotNull(ScriptStore(temp.toFile(), Logger.getAnonymousLogger()).load(script.id))
        val restored = reloaded.graph.nodes.values.first { it.type == CommandType.CONDITION }
        assertNull(restored.targetSpec)
        restored.setConditionSubject(ConditionSubject.OTHER_ENTITY)
        assertNull(restored.targetSpec)
    }

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
    fun `obsolete value subject is rejected as unset`() {
        // 「値を比較」判定対象は撤去しました。旧値は unknown と同じく未設定として扱います。
        val node = CommandType.CONDITION.newNode().apply {
            params["subject"] = "VALUE"
        }
        assertNull(node.conditionSubject())
    }

    @Test
    fun `persisted layer rejects unknown subjects but allows missing ones`() {
        fun rootWithSubject(subject: String?): com.google.gson.JsonObject {
            val params = if (subject == null) "{}" else """{"subject": "$subject"}"""
            val nodeId = UUID.randomUUID()
            return com.google.gson.JsonParser.parseString(
                """
                {
                  "formatVersion": 18,
                  "id": "${UUID.randomUUID()}",
                  "name": "subject-check",
                  "trigger": {"kind": "REDSTONE"},
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
        val script = DiskScript(name = "subject-player")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            setConditionSubject(ConditionSubject.PLAYER)
        }
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == condition.id && it.fieldKeys == setOf("target")
            },
        )
    }

    @Test
    fun `other entity subject without a target fails validation`() {
        val script = DiskScript(name = "subject-other")
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            setConditionSubject(ConditionSubject.OTHER_ENTITY)
        }
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == condition.id && it.fieldKeys == setOf("target")
            },
        )
    }

    @Test
    fun `missing subject fails validation on the subject tab`() {
        // 旧 `kind` のみの保存は変換せず、判定対象タブの未設定として拒否します。
        val script = DiskScript(name = "legacy-kind")
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
