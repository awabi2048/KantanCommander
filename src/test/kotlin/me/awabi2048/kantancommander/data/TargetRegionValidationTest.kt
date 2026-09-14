package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.RegionAnchorKind
import me.awabi2048.kantancommander.model.RegionAnchorSpec
import me.awabi2048.kantancommander.model.RegionKind
import me.awabi2048.kantancommander.model.RegionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #4 のTarget絞り込みへの領域接続テストです。
 *
 * 明示領域つきTargetの検証（形状・範囲・距離・基準）と、従来の dx/dy/dz
 * の互換維持を固定します。実行時の containment は [RegionMathTest] で
 * 固定し、ここでは検証層の接続を扱います。
 */
class TargetRegionValidationTest {
    private fun scriptWithRegion(region: RegionSpec?): DiskScript {
        val script = DiskScript(name = "target-region", owner = UUID.randomUUID())
        GraphEditor.append(script.graph, CommandType.DISPLAY_TEXT).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY, region = region)
            params["mode"] = "tellraw"
            params["text"] = "hi"
        }
        return script
    }

    private fun regionErrors(script: DiskScript) =
        ExecutableScriptValidator.validate(script).filter { it.path.contains("/region") }

    @Test
    fun `valid cuboid region passes`() {
        val script = scriptWithRegion(
            RegionSpec(
                kind = RegionKind.CUBOID,
                anchor = RegionAnchorSpec(RegionAnchorKind.CONTROL_BLOCK),
                dx = 2.0,
                dy = 1.0,
                dz = 2.0,
            ),
        )
        assertTrue(regionErrors(script).isEmpty())
    }

    @Test
    fun `unset region keeps the legacy path without region errors`() {
        // 領域未設定時は従来の dx/dy/dz だけを使い、領域検証へ入りません。
        val script = scriptWithRegion(null)
        script.graph.nodes.values.first().targetSpec =
            TargetSpec(TargetKind.NEAREST_ENTITY, dx = 1.0, dy = 1.0, dz = 1.0)
        assertTrue(regionErrors(script).isEmpty())
        // 空の領域指定（未設定相当）も領域検証へ入りません。
        assertTrue(regionErrors(scriptWithRegion(RegionSpec())).isEmpty())
    }

    @Test
    fun `non cuboid shapes are rejected`() {
        val script = scriptWithRegion(
            RegionSpec(kind = RegionKind.SPHERE, dx = 1.0, dy = 1.0, dz = 1.0),
        )
        assertTrue(regionErrors(script).any { it.fieldKeys == setOf("kind") })
    }

    @Test
    fun `negative extents and inverted distances are rejected`() {
        val extents = scriptWithRegion(RegionSpec(dx = -1.0, dy = 1.0, dz = 1.0))
        assertTrue(regionErrors(extents).any { it.fieldKeys == setOf("dx") })

        val distances = scriptWithRegion(
            RegionSpec(dx = 1.0, dy = 1.0, dz = 1.0, minimumDistance = 5.0, maximumDistance = 2.0),
        )
        assertTrue(regionErrors(distances).any { it.fieldKeys == setOf("minimumDistance") })
    }

    @Test
    fun `anchors require their parameters`() {
        val fixed = scriptWithRegion(
            RegionSpec(anchor = RegionAnchorSpec(RegionAnchorKind.FIXED), dx = 1.0),
        )
        assertTrue(regionErrors(fixed).any { it.fieldKeys == setOf("anchor") })

        val temporary = scriptWithRegion(
            RegionSpec(anchor = RegionAnchorSpec(RegionAnchorKind.TEMPORARY), dx = 1.0),
        )
        assertTrue(regionErrors(temporary).any { it.fieldKeys == setOf("anchor") })

        val target = scriptWithRegion(
            RegionSpec(anchor = RegionAnchorSpec(RegionAnchorKind.TARGET_ENTITY), dx = 1.0),
        )
        assertTrue(regionErrors(target).any { it.fieldKeys == setOf("anchor") })

        // 固定基準に位置があれば検証を通ります。
        val fixedPosition = scriptWithRegion(
            RegionSpec(
                anchor = RegionAnchorSpec(
                    RegionAnchorKind.FIXED,
                    position = PositionSpec(PositionKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0),
                ),
                dx = 1.0,
            ),
        )
        assertTrue(regionErrors(fixedPosition).isEmpty())
    }
}
