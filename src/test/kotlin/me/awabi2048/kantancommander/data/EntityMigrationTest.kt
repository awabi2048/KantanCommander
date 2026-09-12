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
 * 統合保存版v15の移行テストです。
 *
 * v13の旧ENTITY_ACTION／tagは新ENTITY_OPERATION／tagへ変換します。
 * v14は形式番号の付け替えのみ行い、内容は変えません。
 * 旧ENTITY_ACTION／ride／dismountは保存形式を変えずに読み込みます。
 * 旧equip・旧kindは変換せず、検証層で移行漏れ・未設定として拒否します。
 * v12以前は従来どおり非対応として隔離します。
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
    fun `v13 tag action migrates to entity operation`() {
        val dir = temp.resolve("v13-tag").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 13, nodeId, entityActionNode(nodeId, "tag"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.ENTITY_OPERATION, migrated.type)
        assertEquals("tag", migrated.string("operation", ""))
        assertEquals("add", migrated.string("tagOperation", ""))
        assertEquals("kc_test", migrated.string("tag", ""))
        assertEquals(15, loaded.formatVersion)
    }

    @Test
    fun `v14 entity operation loads with version bump only`() {
        val dir = temp.resolve("v14-operation").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        dir.resolve("$scriptId.json").writeText(
            """
            {
              "formatVersion": 14,
              "id": "$scriptId",
              "name": "migration",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 0,
              "listed": false,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": false, "intervalSeconds": 1.0},
              "graph": {
                "entryNodeId": "$nodeId",
                "nodes": {
                  "$nodeId": {
                    "id": "$nodeId",
                    "type": "ENTITY_OPERATION",
                    "params": {"operation": "tag", "tagOperation": "add", "tag": "kc_test"}
                  }
                }
              },
              "revision": 0,
              "contentModified": false
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.ENTITY_OPERATION, migrated.type)
        assertEquals("tag", migrated.string("operation", ""))
        assertEquals("kc_test", migrated.string("tag", ""))
        assertEquals(15, loaded.formatVersion)
    }

    @Test
    fun `v13 ride dismount and equip keep their shape`() {
        // 読み込み時は形状を保ちます。旧equipの実行可否は実行前検証が
        // 移行漏れとして拒否します（自動でPLACE_ITEMへ変換しません）。
        val dir = temp.resolve("v13-keep").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val rideId = UUID.randomUUID()
        val equipId = UUID.randomUUID()
        writeRaw(
            dir,
            scriptId,
            13,
            rideId,
            """
            ${entityActionNode(rideId, "ride", next = equipId)},
            ${entityActionNode(equipId, "equip", ", \"slot\": \"HAND\", \"item\": \"minecraft:stone\", \"overwrite\": \"false\"")}
            """.trimIndent(),
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        assertEquals(CommandType.ENTITY_ACTION, loaded.graph.nodes.getValue(rideId).type)
        assertEquals("ride", loaded.graph.nodes.getValue(rideId).string("action", ""))
        val equip = loaded.graph.nodes.getValue(equipId)
        assertEquals(CommandType.ENTITY_ACTION, equip.type)
        assertEquals("equip", equip.string("action", ""))
        assertEquals("minecraft:stone", equip.string("item", ""))
    }

    @Test
    fun `v12 is quarantined as incompatible`() {
        val dir = temp.resolve("v12").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 12, nodeId, entityActionNode(nodeId, "tag"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        assertNull(store.load(scriptId))
        assertTrue(requireNotNull(dir.resolve("incompatible").listFiles()).isNotEmpty())
    }
}
