package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityTagEntries
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
 * 旧equipは [LegacyEquipMigration] で PLACE_ITEM へ正本化します（#21）。
 * 旧kindは変換せず、検証層で未設定として拒否します。
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
    fun `v16 summon custom name migrates to shared summon data`() {
        // issue #10: 旧独立 `customName` は共通スキーマの個別設定へ正本化します。
        val dir = temp.resolve("v16-summon-name").also(File::mkdirs)
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
              "params": {"entity": "minecraft:zombie", "tags": "", "customName": "Boss"},
              "configuredFields": ["entity", "customName"]
            }
            """.trimIndent(),
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.SUMMON_ENTITY, migrated.type)
        assertEquals("Boss", migrated.string(me.awabi2048.kantancommander.model.EntityDataSchema.summonDataKey("custom_name"), ""))
        assertTrue("customName" !in migrated.params)
        val marks = migrated.configuredFields ?: emptySet()
        assertTrue(me.awabi2048.kantancommander.model.EntityDataSchema.summonDataKey("custom_name") in marks)
        assertTrue("customName" !in marks)
        assertEquals(18, loaded.formatVersion)
    }

    @Test
    fun `v16 blank summon custom name is dropped as unset`() {
        // 空の表示名は未設定と同等のため、移行で除去します。
        val dir = temp.resolve("v16-summon-blank").also(File::mkdirs)
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
              "params": {"entity": "minecraft:zombie", "tags": "", "customName": ""},
              "configuredFields": ["entity"]
            }
            """.trimIndent(),
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertTrue("customName" !in migrated.params)
        assertTrue(
            me.awabi2048.kantancommander.model.EntityDataSchema.summonDataEntries(migrated.params).isEmpty(),
        )
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
        assertEquals(18, loaded.formatVersion)
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
        assertEquals(18, loaded.formatVersion)
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

    private fun tagNode(nodeId: UUID, operation: String?, tag: String): String {
        val operationField = if (operation == null) "" else ",\n          \"tagOperation\": \"$operation\""
        return """
        "$nodeId": {
          "id": "$nodeId",
          "type": "ENTITY_ACTION",
          "targetSpec": {"kind": "NEAREST_ENTITY", "sort": "NEAREST"},
          "params": {"action": "tag"$operationField, "tag": "$tag"},
          "configuredFields": ["action", "tag"]
        }
        """.trimIndent()
    }

    @Test
    fun `legacy tag migrates to entity operation keeping its meaning`() {
        // #32: 旧タグ操作は読み込み時に ENTITY_OPERATION へ正本化し、
        // 従来と同じタグ操作として実行・検証・出力できるようにします。
        val dir = temp.resolve("v17-tag").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 17, nodeId, tagNode(nodeId, "remove", "kc_test"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.ENTITY_OPERATION, migrated.type)
        assertEquals(EntityDataPath.TAG, migrated.string(EntityDataPath.PARAM_PATH, ""))
        assertEquals("remove=kc_test", migrated.string(EntityTagEntries.PARAM_ENTRIES, ""))
        assertTrue("action" !in migrated.params)
        assertTrue("tagOperation" !in migrated.params)
        assertTrue("tag" !in migrated.params)
        val marks = migrated.configuredFields ?: emptySet()
        assertTrue(EntityDataPath.PARAM_PATH in marks)
        assertTrue(EntityTagEntries.PARAM_ENTRIES in marks)
        assertTrue("action" !in marks)
        assertEquals(18, loaded.formatVersion)
        // 移行後は新 ENTITY_OPERATION の検証だけを通ります。
        assertTrue(ExecutableScriptValidator.validate(loaded).none { it.nodeId == nodeId })
    }

    @Test
    fun `legacy tag without operation defaults to add`() {
        // 旧実行の既定（欠落時は add）を引き継ぎます。
        val dir = temp.resolve("v16-tag").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 16, nodeId, tagNode(nodeId, null, "kc_test"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.ENTITY_OPERATION, migrated.type)
        assertEquals("add=kc_test", migrated.string(EntityTagEntries.PARAM_ENTRIES, ""))
        assertEquals(18, loaded.formatVersion)
        assertTrue(ExecutableScriptValidator.validate(loaded).none { it.nodeId == nodeId })
    }

    @Test
    fun `legacy tag with an unknown operation stays unmigrated`() {
        // 不明な旧操作は推測で読み替えず、検証層の不明扱いに委ねます。
        val dir = temp.resolve("v17-tag-unknown").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 17, nodeId, tagNode(nodeId, "toggle", "kc_test"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val kept = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.ENTITY_ACTION, kept.type)
        assertEquals("tag", kept.string("action", ""))
        assertTrue(
            ExecutableScriptValidator.validate(loaded)
                .any { it.nodeId == nodeId && it.fieldKeys == setOf("action") },
        )
    }

    private fun equipNode(nodeId: UUID, slot: String, extraParams: String = ""): String = """
        "$nodeId": {
          "id": "$nodeId",
          "type": "ENTITY_ACTION",
          "targetSpec": {"kind": "NEAREST_PLAYER", "sort": "NEAREST"},
          "params": {"action": "equip", "slot": "$slot", "item": "minecraft:stone", "overwrite": "true"$extraParams},
          "configuredFields": ["action", "slot", "item", "overwrite"]
        }
        """.trimIndent()

    @Test
    fun `legacy equip migrates to place item keeping its meaning`() {
        // #21: 旧装備操作は読み込み時に PLACE_ITEM へ正本化し、
        // 従来と同じ装備操作として実行・検証・出力できるようにします。
        val dir = temp.resolve("v17-equip").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 17, nodeId, equipNode(nodeId, "OFF_HAND"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.PLACE_ITEM, migrated.type)
        assertEquals("EQUIPMENT", migrated.string("placeDestination", ""))
        assertEquals("OFF_HAND", migrated.string("slot", ""))
        assertEquals("minecraft:stone", migrated.string("item", ""))
        // 旧equipに個数指定はなく、装備品は単品として正規化します。
        assertEquals("1", migrated.string("count", ""))
        assertEquals("true", migrated.string("overwrite", ""))
        assertTrue("action" !in migrated.params)
        val marks = migrated.configuredFields ?: emptySet()
        assertTrue("placeDestination" in marks)
        assertTrue("action" !in marks)
        assertTrue("slot" in marks)
        assertEquals(18, loaded.formatVersion)
        // 移行後は新 PLACE_ITEM の検証だけを通ります。
        assertTrue(ExecutableScriptValidator.validate(loaded).none { it.nodeId == nodeId })
    }

    @Test
    fun `legacy equip in v16 migrates with the version upgrade`() {
        // バージョン移行と旧装備正本化が合成されることを確認します。
        val dir = temp.resolve("v16-equip").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 16, nodeId, equipNode(nodeId, "HAND"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.PLACE_ITEM, migrated.type)
        assertEquals("EQUIPMENT", migrated.string("placeDestination", ""))
        assertEquals("HAND", migrated.string("slot", ""))
        assertEquals("1", migrated.string("count", ""))
        assertEquals(18, loaded.formatVersion)
        assertTrue(ExecutableScriptValidator.validate(loaded).none { it.nodeId == nodeId })
    }

    @Test
    fun `legacy equip with an unknown slot migrates without silent remapping`() {
        // 不正な旧 slot 値は推測で読み替えず、そのまま引き継いで
        // 新検証層で明示エラーにします。
        val dir = temp.resolve("v17-equip-slot").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(dir, scriptId, 17, nodeId, equipNode(nodeId, "NOPE"))

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.PLACE_ITEM, migrated.type)
        assertEquals("NOPE", migrated.string("slot", ""))
        assertTrue(
            ExecutableScriptValidator.validate(loaded)
                .any { it.nodeId == nodeId && it.fieldKeys == setOf("slot") },
        )
    }
}
