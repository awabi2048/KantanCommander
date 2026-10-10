package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * #30のパーティクル／図形分離に伴う保存移行テストです。
 *
 * - 旧 `PARTICLE / POINT`（mode欠落時も含む）→ 新 `PARTICLE`。散布系・種類系を保持し、
 *   図形系キーと旧modeキーを除去します。
 * - 旧 `PARTICLE / GEOMETRY` → 新 `PARTICLE_GEOMETRY`。図形系・種類系を保持し、
 *   散布系キーと旧modeキーを除去します。
 * - 移行後は正規保存形式から旧modeキーを除外します。
 * - snapshotへ退避した副グラフも同じ規則で移行します。
 */
class ParticleMigrationTest {
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

    private fun particleNode(nodeId: UUID, params: String): String =
        """
        "$nodeId": {
          "id": "$nodeId",
          "type": "PARTICLE",
          "params": {$params}
        }
        """.trimIndent()

    @Test
    fun `v15 point migrates to particle keeping scatter values`() {
        val dir = temp.resolve("v15-point").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(
            dir,
            scriptId,
            15,
            nodeId,
            particleNode(
                nodeId,
                """
                "particle": "FLAME", "particleData": "",
                "particleDeltaX": "1.0", "particleDeltaY": "2.0", "particleDeltaZ": "3.0",
                "particleSpeed": "0.5", "particleCount": "10",
                "particleDisplayMode": "POINT",
                "particleShape": "CIRCLE", "particleDensity": "0.5", "geometryRadius": "3.0"
                """.trimIndent(),
            ),
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.PARTICLE, migrated.type)
        // 散布系・種類系は保持します。
        assertEquals("1.0", migrated.string("particleDeltaX", ""))
        assertEquals("10", migrated.string("particleCount", ""))
        assertEquals("FLAME", migrated.string("particle", ""))
        // 図形系キーと旧modeキーは除去します。
        assertFalse(migrated.params.containsKey("particleShape"))
        assertFalse(migrated.params.containsKey("particleDensity"))
        assertFalse(migrated.params.containsKey("geometryRadius"))
        assertFalse(migrated.params.containsKey("particleDisplayMode"))
        assertEquals(18, loaded.formatVersion)
    }

    @Test
    fun `v15 geometry migrates to particle geometry keeping shape values`() {
        val dir = temp.resolve("v15-geometry").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(
            dir,
            scriptId,
            15,
            nodeId,
            particleNode(
                nodeId,
                """
                "particle": "FLAME", "particleData": "",
                "particleDeltaX": "9.0", "particleSpeed": "9.0", "particleCount": "99",
                "particleDisplayMode": "GEOMETRY",
                "particleShape": "CIRCLE", "particleDensity": "1.5", "geometryRadius": "5.0"
                """.trimIndent(),
            ),
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.PARTICLE_GEOMETRY, migrated.type)
        // 図形系・種類系は保持します。
        assertEquals("CIRCLE", migrated.string("particleShape", ""))
        assertEquals("1.5", migrated.string("particleDensity", ""))
        assertEquals("5.0", migrated.string("geometryRadius", ""))
        assertEquals("FLAME", migrated.string("particle", ""))
        // 散布系キーと旧modeキーは除去します。
        assertFalse(migrated.params.containsKey("particleDeltaX"))
        assertFalse(migrated.params.containsKey("particleSpeed"))
        assertFalse(migrated.params.containsKey("particleCount"))
        assertFalse(migrated.params.containsKey("particleDisplayMode"))
        assertEquals(18, loaded.formatVersion)
    }

    @Test
    fun `v15 missing mode migrates to particle as point`() {
        // 旧mode欠落はPOINT扱いとし、新PARTICLEへ移行します。
        val dir = temp.resolve("v15-missing").also(File::mkdirs)
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        writeRaw(
            dir,
            scriptId,
            15,
            nodeId,
            particleNode(
                nodeId,
                """
                "particle": "FLAME", "particleData": "",
                "particleDeltaX": "0.0", "particleSpeed": "0.0", "particleCount": "1",
                "particleShape": "CIRCLE", "geometryRadius": "3.0"
                """.trimIndent(),
            ),
        )

        val store = ScriptStore(dir, Logger.getAnonymousLogger())
        val loaded = requireNotNull(store.load(scriptId))
        val migrated = loaded.graph.nodes.getValue(nodeId)
        assertEquals(CommandType.PARTICLE, migrated.type)
        assertTrue(migrated.params.containsKey("particleCount"))
        assertFalse(migrated.params.containsKey("particleShape"))
        assertEquals(18, loaded.formatVersion)
    }
}
