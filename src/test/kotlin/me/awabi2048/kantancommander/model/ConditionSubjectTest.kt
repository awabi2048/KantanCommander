package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphEditor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #27: 判定対象（ConditionSubject）の対応・互換テストです。
 *
 * 新規 `subject` 欄と旧 `kind` 欄の二重管理がずれないこと、旧保存だけのノードが
 * 意味を変えず表示・検証できることを保証します。
 */
class ConditionSubjectTest {
    @Test
    fun `legacy kinds map to subjects`() {
        assertEquals(ConditionSubject.PLAYER, ConditionKind.PLAYER_STATE.toSubject())
        assertEquals(ConditionSubject.VARIABLE, ConditionKind.VARIABLE_STATE.toSubject())
        assertEquals(ConditionSubject.BLOCK, ConditionKind.BLOCK_STATE.toSubject())
        assertEquals(ConditionSubject.CONTROL_BLOCK, ConditionKind.CONTROL_BLOCK_STATE.toSubject())
    }

    @Test
    fun `legacy target exists splits by target kind`() {
        // 対象未設定は中立なその他扱いです。
        assertEquals(ConditionSubject.OTHER_ENTITY, ConditionKind.TARGET_EXISTS.toSubject(null))
        val playerTarget = TargetSpec(kind = TargetKind.NEARBY_PLAYERS)
        assertEquals(ConditionSubject.PLAYER, ConditionKind.TARGET_EXISTS.toSubject(playerTarget))
        val entityTarget = TargetSpec(kind = TargetKind.NEARBY_ENTITIES, entityType = "minecraft:zombie")
        assertEquals(ConditionSubject.OTHER_ENTITY, ConditionKind.TARGET_EXISTS.toSubject(entityTarget))
    }

    @Test
    fun `subjects mirror back to legacy kinds`() {
        assertEquals(ConditionKind.PLAYER_STATE, ConditionSubject.PLAYER.legacyKind())
        assertEquals(ConditionKind.TARGET_EXISTS, ConditionSubject.OTHER_ENTITY.legacyKind())
        assertEquals(ConditionKind.BLOCK_STATE, ConditionSubject.BLOCK.legacyKind())
        assertEquals(ConditionKind.VARIABLE_STATE, ConditionSubject.VARIABLE.legacyKind())
        assertEquals(ConditionKind.CONTROL_BLOCK_STATE, ConditionSubject.CONTROL_BLOCK.legacyKind())
    }

    @Test
    fun `explicit subject wins over legacy kind`() {
        val node = CommandType.CONDITION.newNode().apply {
            params["kind"] = ConditionKind.PLAYER_STATE.name
            params["subject"] = ConditionSubject.BLOCK.name
        }
        assertEquals(ConditionSubject.BLOCK, node.conditionSubject())
    }

    @Test
    fun `invalid subject falls back to legacy kind`() {
        val node = CommandType.CONDITION.newNode().apply {
            params["kind"] = ConditionKind.VARIABLE_STATE.name
            params["subject"] = "UNKNOWN_SUBJECT"
        }
        assertEquals(ConditionSubject.VARIABLE, node.conditionSubject())
    }

    @Test
    fun `unknown kind and subject default to other entity`() {
        val node = CommandType.CONDITION.newNode().apply {
            params["kind"] = "UNKNOWN_KIND"
            params["subject"] = "UNKNOWN_SUBJECT"
        }
        assertEquals(ConditionSubject.OTHER_ENTITY, node.conditionSubject())
    }

    @Test
    fun `setting a subject mirrors the legacy kind`() {
        val node = CommandType.CONDITION.newNode()
        node.setConditionSubject(ConditionSubject.PLAYER)
        assertEquals(ConditionSubject.PLAYER.name, node.params["subject"])
        assertEquals(ConditionKind.PLAYER_STATE.name, node.params["kind"])
        assertEquals(ConditionSubject.PLAYER, node.conditionSubject())

        node.setConditionSubject(ConditionSubject.OTHER_ENTITY)
        assertEquals(ConditionKind.TARGET_EXISTS.name, node.params["kind"])
        assertEquals(ConditionSubject.OTHER_ENTITY, node.conditionSubject())
    }

    @Test
    fun `new nodes default to the legacy existence check`() {
        // 既定値は旧既定 `TARGET_EXISTS` そのままで、表示上はその他存在判定になります。
        // 旧保存との互換のため、既定値へ `subject` 欄は持たせません。
        val node = CommandType.CONDITION.newNode()
        assertEquals(ConditionKind.TARGET_EXISTS.name, node.params["kind"])
        assertEquals(null, node.params["subject"])
        assertEquals(ConditionSubject.OTHER_ENTITY, node.conditionSubject())
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
    fun `legacy target exists without a target still fails validation`() {
        // 旧保存（`kind` のみ）の振る舞いが変わらないことを保証します。
        val script = DiskScript(name = "legacy-exists", owner = UUID.randomUUID())
        val condition = GraphEditor.append(script.graph, CommandType.CONDITION).apply {
            params["kind"] = ConditionKind.TARGET_EXISTS.name
        }
        assertTrue(
            ExecutableScriptValidator.validate(script).any {
                it.nodeId == condition.id && it.fieldKeys == setOf("condition")
            },
        )
    }
}
