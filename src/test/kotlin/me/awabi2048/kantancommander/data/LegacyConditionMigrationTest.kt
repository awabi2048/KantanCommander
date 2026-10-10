package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.conditionSubject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * issue #27 の旧条件分類の移行テストです。
 *
 * 旧 `kind` 5種の判定対象への正本化、対象種別による `TARGET_EXISTS` の
 * 振り分け、未知値の非推測、明示設定の付け替えを固定します。
 * 実行結果の維持は移行後の検証通過で確認し、評価自体は既存の
 * subject 別テストが担います。
 */
class LegacyConditionMigrationTest {
    @TempDir
    lateinit var temp: File

    private fun writeCondition(
        dir: File,
        scriptId: UUID,
        nodeId: UUID,
        version: Int,
        params: String,
        targetSpec: String? = null,
        configuredFields: String? = null,
        nodeFields: String? = null,
    ) {
        val target = if (targetSpec == null) "" else ",\n          \"targetSpec\": $targetSpec"
        val configured = if (configuredFields == null) "" else ",\n          \"configuredFields\": $configuredFields"
        val extra = if (nodeFields == null) "" else ",\n          $nodeFields"
        dir.resolve("$scriptId.json").writeText(
            """
            {
              "formatVersion": $version,
              "id": "$scriptId",
              "name": "legacy-kind",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 0,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": false, "intervalSeconds": 1.0},
              "graph": {
                "entryNodeId": "$nodeId",
                "nodes": {
                  "$nodeId": {
                    "id": "$nodeId",
                    "type": "CONDITION",
                    "params": $params$target$configured$extra
                  }
                }
              },
              "revision": 0
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )
    }

    private fun loadSingle(dirName: String, version: Int, params: String, targetSpec: String? = null): me.awabi2048.kantancommander.model.CommandNode {
        val dir = temp.resolve(dirName).also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeCondition(dir, scriptId, nodeId, version, params, targetSpec)
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        assertEquals(18, loaded.formatVersion)
        return loaded.graph.nodes.getValue(nodeId)
    }

    @Test
    fun `legacy kinds map to their subjects keeping details`() {
        // 旧分類と現行判定対象の対応表を固定します。詳細欄は触れません。
        val cases = listOf(
            Triple(
                """{"kind": "PLAYER_STATE", "sneaking": "true"}""",
                ConditionSubject.PLAYER,
                mapOf("sneaking" to "true"),
            ),
            Triple(
                """{"kind": "VARIABLE_STATE", "variable": "count", "operator": ">", "value": "3"}""",
                ConditionSubject.VARIABLE,
                mapOf("variable" to "count", "operator" to ">", "value" to "3"),
            ),
            Triple(
                """{"kind": "BLOCK_STATE", "block": "minecraft:stone"}""",
                ConditionSubject.BLOCK,
                mapOf("block" to "minecraft:stone"),
            ),
            Triple(
                """{"kind": "CONTROL_BLOCK_STATE"}""",
                ConditionSubject.CONTROL_BLOCK,
                emptyMap(),
            ),
        )
        cases.forEachIndexed { index, (params, expected, details) ->
            val node = loadSingle("kind-$index", 17, params)
            assertEquals(expected, node.conditionSubject(), params)
            assertTrue("kind" !in node.params, params)
            details.forEach { (key, value) ->
                assertEquals(value, node.params[key], "$params/$key")
            }
        }
    }

    @Test
    fun `target exists follows the target classification`() {
        // プレイヤー系対象は PLAYER へ振り分けます。
        val player = loadSingle(
            "exists-player",
            17,
            """{"kind": "TARGET_EXISTS"}""",
            """{"kind": "NEAREST_PLAYER", "sort": "NEAREST"}""",
        )
        assertEquals(ConditionSubject.PLAYER, player.conditionSubject())
        assertTrue("kind" !in player.params)

        // 非プレイヤー系対象は OTHER_ENTITY へ振り分けます。
        val entity = loadSingle(
            "exists-entity",
            17,
            """{"kind": "TARGET_EXISTS"}""",
            """{"kind": "NEAREST_ENTITY", "sort": "NEAREST"}""",
        )
        assertEquals(ConditionSubject.OTHER_ENTITY, entity.conditionSubject())

        // 対象未設定は推測せず中立なその他扱いに固定します。
        val missing = loadSingle("exists-missing", 17, """{"kind": "TARGET_EXISTS"}""")
        assertEquals(ConditionSubject.OTHER_ENTITY, missing.conditionSubject())

        // 対象種別自体が不正な保存は、移行前に破損として隔離します。
        // 推測で振り分けず、隔離理由を明示します。
        val brokenDir = temp.resolve("exists-broken").also(File::mkdirs)
        val brokenScript = UUID.randomUUID()
        val brokenNode = UUID.randomUUID()
        writeCondition(
            brokenDir,
            brokenScript,
            brokenNode,
            17,
            """{"kind": "TARGET_EXISTS"}""",
            """{"kind": "FANCY_TARGET"}""",
        )
        val brokenStore = ScriptStore(brokenDir, Logger.getAnonymousLogger())
        assertNull(brokenStore.load(brokenScript))
        assertTrue(requireNotNull(brokenDir.resolve("corrupt").listFiles()).isNotEmpty())
    }

    @Test
    fun `existing subject wins and stale kind is dropped`() {
        // 正本がある場合は変換せず、残存 kind 欄だけを除去します。
        val dir = temp.resolve("subject-wins").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeCondition(
            dir,
            scriptId,
            nodeId,
            17,
            """{"subject": "BLOCK", "kind": "PLAYER_STATE", "block": "minecraft:stone"}""",
        )
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val node = loaded.graph.nodes.getValue(nodeId)
        assertEquals(ConditionSubject.BLOCK, node.conditionSubject())
        assertTrue("kind" !in node.params)
        assertEquals("minecraft:stone", node.params["block"])
    }

    @Test
    fun `unknown kind stays unset without guessing`() {
        // 未知の旧値は推測で読み替えず、検証層の未設定扱いに委ねます。
        val node = loadSingle("kind-unknown", 17, """{"kind": "FANCY_KIND"}""")
        assertNull(node.conditionSubject())
        assertEquals("FANCY_KIND", node.params["kind"])
    }

    @Test
    fun `configured marks follow the migration`() {
        // 明示設定の来歴は kind から subject へ付け替えます。
        val dir = temp.resolve("kind-marks").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeCondition(
            dir,
            scriptId,
            nodeId,
            17,
            """{"kind": "VARIABLE_STATE", "variable": "count"}""",
            configuredFields = """["kind", "variable"]""",
        )
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val node = loaded.graph.nodes.getValue(nodeId)
        assertEquals(ConditionSubject.VARIABLE, node.conditionSubject())
        val marks = node.configuredFields ?: emptySet()
        assertTrue("subject" in marks)
        assertTrue("kind" !in marks)
        assertTrue("variable" in marks)
    }

    @Test
    fun `migrated variable condition passes validation`() {
        // 移行後は新 subject の検証だけを通り、旧 kind の残骸を残しません。
        val dir = temp.resolve("kind-valid").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeCondition(
            dir,
            scriptId,
            nodeId,
            16,
            """{"kind": "VARIABLE_STATE", "variable": "count", "operator": ">=", "value": "2"}""",
        )
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val node = loaded.graph.nodes.getValue(nodeId)
        assertEquals(ConditionSubject.VARIABLE, node.conditionSubject())
        assertEquals(18, loaded.formatVersion)
        assertFalse(
            ExecutableScriptValidator.validate(loaded).any { it.nodeId == nodeId },
        )
    }

    @Test
    fun `legacy redstone input selection migrates to signal range`() {
        // 旧制御ブロック状態の REDSTONE_INPUT は「信号あり」を意味したため、
        // 強度範囲 1.. へ移行します。
        val dir = temp.resolve("redstone-migrate").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeCondition(
            dir,
            scriptId,
            nodeId,
            17,
            """{"kind": "CONTROL_BLOCK_STATE"}""",
            nodeFields = """"controlBlockStates": ["REDSTONE_INPUT"]""",
        )
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val node = loaded.graph.nodes.getValue(nodeId)
        assertEquals(ConditionSubject.CONTROL_BLOCK, node.conditionSubject())
        assertEquals("1..", node.params["redstoneSignal"])
        assertTrue("redstoneSignal" in (node.configuredFields ?: emptySet()))
        // 移行後の条件はそのまま検証を通ります。
        assertFalse(
            ExecutableScriptValidator.validate(loaded).any { it.nodeId == nodeId },
        )
    }

    @Test
    fun `removed value subject becomes unset instead of corrupt`() {
        // 撤去した「値を比較」判定対象は破損として隔離せず、判定対象未設定へ
        // 正本化します。残った詳細欄は無害な残存値として保持します。
        val node = loadSingle(
            "value-subject",
            17,
            """{"subject": "VALUE", "operator": ">=", "value": "1"}""",
        )
        assertNull(node.conditionSubject())
        assertTrue("subject" !in node.params)
        assertTrue("subject" !in (node.configuredFields ?: emptySet()))
    }

    @Test
    fun `legacy control block states without redstone input are dropped`() {
        // REDSTONE_INPUT を含まない旧選択欄は廃止欄として除去するだけです。
        // 条件未設定のまま残るため、検証は引き続き未設定を報告します。
        val dir = temp.resolve("redstone-drop").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeCondition(
            dir,
            scriptId,
            nodeId,
            17,
            """{"kind": "CONTROL_BLOCK_STATE"}""",
            nodeFields = """"controlBlockStates": []""",
        )
        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val node = loaded.graph.nodes.getValue(nodeId)
        assertEquals(ConditionSubject.CONTROL_BLOCK, node.conditionSubject())
        assertTrue("redstoneSignal" !in node.params)
        assertTrue(
            ExecutableScriptValidator.validate(loaded).any { it.nodeId == nodeId },
        )
    }
}
