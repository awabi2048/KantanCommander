package me.awabi2048.kantancommander.data

import com.google.gson.JsonParser
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Target絞り込みの範囲指定（dx/dy/dz・距離）の検証テストです。
 *
 * 直方体範囲はバニラセレクターと同じく負の値を許し、基準は実行位置です。
 * 廃止した領域・探索基準の旧保存項目は永続化検証で拒否します。
 */
class TargetRangeValidationTest {
    private fun scriptWithTarget(spec: TargetSpec): DiskScript {
        val script = DiskScript(name = "target-range", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).apply {
            targetSpec = spec
            params["mode"] = "tellraw"
            params["text"] = "hi"
        }
        return script
    }

    @Test
    fun `cuboid extents accept negative values`() {
        // 負の値は -方向の領域を意味し、バニラと同じく有限値なら許容します。
        val script = scriptWithTarget(
            TargetSpec(TargetKind.NEARBY_ENTITIES, dx = -2.0, dy = 0.0, dz = 3.5),
        )
        assertTrue(ExecutableScriptValidator.validate(script).isEmpty())
    }

    @Test
    fun `non finite cuboid extents are rejected`() {
        val script = scriptWithTarget(
            TargetSpec(TargetKind.NEARBY_ENTITIES, dx = Double.NaN),
        )
        assertTrue(
            ExecutableScriptValidator.validate(script)
                .any { it.fieldKeys == setOf("dx") },
        )
    }

    @Test
    fun `distance range rejects negatives and inversion`() {
        val negative = scriptWithTarget(
            TargetSpec(TargetKind.NEARBY_ENTITIES, minimumDistance = -1.0),
        )
        assertTrue(ExecutableScriptValidator.validate(negative).isNotEmpty())

        val inverted = scriptWithTarget(
            TargetSpec(TargetKind.NEARBY_ENTITIES, minimumDistance = 5.0, maximumDistance = 2.0),
        )
        assertTrue(
            ExecutableScriptValidator.validate(inverted)
                .any { it.message.contains("最小距離が最大距離") },
        )
    }

    private fun persistedRoot(targetSpecJson: String) = JsonParser.parseString(
        """
        {
          "formatVersion": 13,
          "id": "${UUID.randomUUID()}",
          "name": "target-range",
          "owner": "${UUID.randomUUID()}",
          "createdAt": 0,
          "activation": "NEEDS_REDSTONE",
          "timer": {"enabled": false, "intervalSeconds": 1.0},
          "graph": {
            "nodes": {
              "${UUID.randomUUID()}": {
                "id": "${UUID.randomUUID()}",
                "type": "DISPLAY_TEXT",
                "params": {"mode": "tellraw", "text": "hi"},
                "targetSpec": $targetSpecJson
              }
            }
          }
        }
        """.trimIndent(),
    ).asJsonObject

    @Test
    fun `persisted legacy region and searchOrigin fields are rejected`() {
        val region = persistedRoot(
            """{"kind": "NEARBY_ENTITIES", "sort": "NEAREST", "region": {"kind": "CUBOID", "dx": 1.0}}""",
        )
        assertTrue(
            PersistedScriptValidator.validate(region)
                .any { it.contains("/region") },
        )

        val searchOrigin = persistedRoot(
            """{"kind": "NEARBY_ENTITIES", "sort": "NEAREST", "searchOrigin": {"positionTemp": "origin"}}""",
        )
        assertTrue(
            PersistedScriptValidator.validate(searchOrigin)
                .any { it.contains("/searchOrigin") },
        )
    }

    @Test
    fun `persisted negative extents and detailed flag are accepted`() {
        val root = persistedRoot(
            """{"kind": "NEARBY_ENTITIES", "sort": "NEAREST", "dx": -2.0, "dz": 0.5, "detailed": true}""",
        )
        assertTrue(PersistedScriptValidator.validate(root).isEmpty())
    }
}
