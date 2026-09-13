package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * 統合保存版v17の移行テストです。
 *
 * v16は保存キーの改名（召喚の種類 entity→summonEntity、配置先
 * destination→placeDestination、値保持）を適用して読み込みます。
 * v15は#30の粒子移行に加えて同じ改名を適用します。v14以前は受付対象外
 * として隔離します。旧ENTITY_ACTION／ride／dismountは保存形式を変えずに読み込みます。
 * 旧equip・旧kindは変換せず、検証層で移行漏れ・未設定として拒否します。
 */
class EntityMigrationTest {
    @TempDir
    lateinit var temp: File

    private fun writeRaw(dir: File, scriptId: UUID, version: Int, entryNodeId: UUID, nodes: String) {
        dir.resolve("$scriptId.json").writeText(
            """
            {
              "formatVersion": $version,
              "id": "$scriptId",
              "name": "migration",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 0,
              "listed": false,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": false, "intervalSeconds": 1.0},
              "graph": {
                "entryNodeId": "$entryNodeId",
                "nodes": {
                  $nodes
                }
              },
              "revision": 0,
              "contentModified": false
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )
    }

    private fun entityActionNode(nodeId: UUID, action: String, extra: String = "", next: UUID? = null): String {
        val nextField = if (next == null) "" else ",\n          \"next\": \"$next\""
        return """
        "$nodeId": {
          "id": "$nodeId",
          "type": "ENTITY_ACTION"$nextField,
          "params": {"action": "$action", "tagOperation": "add", "tag": "kc_test"$extra}
        }
        """.trimIndent()
    }

    @Test
    fun `v13 tag action is quarantined as incompatible`() {
        // v13以前は受付対象外です。旧ENTITY_ACTION／tagの自動変換は行わず隔離します。
        val dir = temp.resolve("v13-tag").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 13, nodeId, entityActionNode(nodeId, "tag"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertNull(store.load(scriptId))
        assertTrue(requireNotNull(dir.resolve("incompatible").listFiles()).isNotEmpty())
    }

    @Test
    fun `v16 summon entity key migrates keeping its value`() {
        // v17改名の移行試験です。旧キーの値は新キーへ引き継ぎます。
        val dir = temp.resolve("v16-summon").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(
            dir,
            scriptId,
            16,
            nodeId,
            """
            "$nodeId": {
              "id": "$nodeId",
              "type": "SUMMON_ENTITY",
              "params": {"entity": "minecraft:mannequin", "tags": "", "customName": ""},
              "configuredFields": ["entity"]
            }
            """.trimIndent(),
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.SUMMON_ENTITY, migrated.type)
        assertEquals("minecraft:mannequin", migrated.string("summonEntity", ""))
        assertTrue("entity" !in migrated.params)
        assertTrue("summonEntity" in (migrated.configuredFields ?: emptySet()))
        assertEquals(17, loaded.formatVersion)
    }

    @Test
    fun `v16 place destination key migrates keeping its value`() {
        val dir = temp.resolve("v16-place").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(
            dir,
            scriptId,
            16,
            nodeId,
            """
            "$nodeId": {
              "id": "$nodeId",
              "type": "PLACE_ITEM",
              "params": {"destination": "EQUIPMENT", "slot": "HAND", "item": "", "itemData": "", "count": "1", "overwrite": "false"},
              "configuredFields": ["destination"]
            }
            """.trimIndent(),
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.PLACE_ITEM, migrated.type)
        assertEquals("EQUIPMENT", migrated.string("placeDestination", ""))
        assertTrue("destination" !in migrated.params)
        assertTrue("placeDestination" in (migrated.configuredFields ?: emptySet()))
        assertEquals(17, loaded.formatVersion)
    }

    @Test
    fun `v14 is quarantined as incompatible`() {
        // v17受付（17・16・15）の範囲外のため隔離します。
        val dir = temp.resolve("v14-quarantine").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 14, nodeId, entityActionNode(nodeId, "ride"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertNull(store.load(scriptId))
        assertTrue(requireNotNull(dir.resolve("incompatible").listFiles()).isNotEmpty())
    }

    @Test
    fun `v13 is quarantined as incompatible`() {
        val dir = temp.resolve("v13").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 13, nodeId, entityActionNode(nodeId, "tag"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertNull(store.load(scriptId))
        assertTrue(requireNotNull(dir.resolve("incompatible").listFiles()).isNotEmpty())
    }
}
