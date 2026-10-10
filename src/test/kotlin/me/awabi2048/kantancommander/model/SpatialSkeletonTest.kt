package me.awabi2048.kantancommander.model

import com.google.gson.JsonParser
import me.awabi2048.kantancommander.data.ExecutableScriptValidator
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.PersistedScriptValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * issue #4 の移植用骨組みの検証です。
 * 設定方法は直方体＋距離を継続し、骨組みの判定だけを確認します。
 */
class SpatialSkeletonTest {
    @Test
    fun `position semantics default to absolute`() {
        val spec = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0)
        assertEquals(CoordinateSemantics.ABSOLUTE, spec.semanticsX)
        assertEquals(CoordinateSemantics.ABSOLUTE, spec.semanticsY)
        assertEquals(CoordinateSemantics.ABSOLUTE, spec.semanticsZ)
        assertEquals(CoordinateSemantics.ABSOLUTE, spec.semanticsYaw)
        assertEquals(CoordinateSemantics.ABSOLUTE, spec.semanticsPitch)
        assertFalse(spec.hasNonAbsoluteSemantics())
    }

    @Test
    fun `minecraft yaw wraps around 180 degrees`() {
        assertEquals(170f, wrapMinecraftYaw(170f))
        assertEquals(-170f, wrapMinecraftYaw(190f))
        assertEquals(180f, wrapMinecraftYaw(-180f))
        assertEquals(0f, wrapMinecraftYaw(360f))
    }

    @Test
    fun `minecraft pitch clamps to vertical range`() {
        assertEquals(45f, clampMinecraftPitch(45f))
        assertEquals(90f, clampMinecraftPitch(120f))
        assertEquals(-90f, clampMinecraftPitch(-120f))
    }

    @Test
    fun `axis semantics read and update round trip`() {
        val spec = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0)
        assertEquals(CoordinateSemantics.ABSOLUTE, spec.axisSemantics("x"))

        val updated = spec.withAxisSemantics("x", CoordinateSemantics.RELATIVE)
        assertEquals(CoordinateSemantics.RELATIVE, updated.axisSemantics("x"))
        assertEquals(CoordinateSemantics.ABSOLUTE, updated.axisSemantics("y"))
        assertEquals(1.0, updated.x)
        // 不明な軸名はそのまま返します。
        assertEquals(spec, spec.withAxisSemantics("w", CoordinateSemantics.RELATIVE))

        val facing = FacingSpec(FacingKind.ROTATION, yaw = 90f, pitch = 0f)
        val turned = facing.withAxisSemantics("yaw", CoordinateSemantics.RELATIVE)
        assertEquals(CoordinateSemantics.RELATIVE, turned.axisSemantics("yaw"))
        assertEquals(CoordinateSemantics.ABSOLUTE, turned.axisSemantics("pitch"))
    }

    @Test
    fun `spatial vector rejects non finite values`() {
        assertTrue(SpatialVector(1.0, 2.0, 3.0).isFinite())
        assertFalse(SpatialVector(Double.NaN, 0.0, 0.0).isFinite())
        assertFalse(SpatialVector(0.0, Double.POSITIVE_INFINITY, 0.0).isFinite())
    }

    @Test
    fun `spatial vector supports addition for future porting`() {
        val sum = SpatialVector(1.0, 2.0, 3.0) + SpatialVector(4.0, -1.0, 0.5)
        assertEquals(SpatialVector(5.0, 1.0, 3.5), sum)
    }

    @Test
    fun `relative semantics on non coordinate position fails validation`() {
        val script = DiskScript(name = "spatial")
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        teleport.destinationSpec = PositionSpec(
            PositionKind.DISK,
            x = 1.0,
            y = 2.0,
            z = 3.0,
            semanticsX = CoordinateSemantics.RELATIVE,
        )

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("相対・ローカルは座標指定") })
    }

    @Test
    fun `local angle fails validation`() {
        val script = DiskScript(name = "spatial")
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        teleport.destinationSpec = PositionSpec(
            PositionKind.COORDINATES,
            x = 1.0,
            y = 2.0,
            z = 3.0,
            semanticsYaw = CoordinateSemantics.LOCAL,
        )

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("向きにローカルは指定できません") })
    }

    @Test
    fun `single axis relative passes validation`() {
        val script = DiskScript(name = "spatial")
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        teleport.destinationSpec = PositionSpec(
            PositionKind.COORDINATES,
            x = 1.0,
            y = 2.0,
            z = 3.0,
            semanticsX = CoordinateSemantics.RELATIVE,
        )

        val errors = ExecutableScriptValidator.validate(script)

        assertFalse(errors.any { it.message.contains("相対・ローカル") })
        assertFalse(errors.any { it.message.contains("座標が未設定") })
    }

    @Test
    fun `relative coordinates require finite offsets`() {
        val script = DiskScript(name = "spatial")
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        teleport.destinationSpec = PositionSpec(
            PositionKind.COORDINATES,
            x = 1.0,
            y = null,
            z = 3.0,
            semanticsX = CoordinateSemantics.RELATIVE,
        )

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.message.contains("座標が未設定") })
    }

    @Test
    fun `absolute coordinates keep existing meaning`() {
        val script = DiskScript(name = "spatial")
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        teleport.destinationSpec = PositionSpec(
            PositionKind.COORDINATES,
            x = 1.0,
            y = 2.0,
            z = 3.0,
        )

        val errors = ExecutableScriptValidator.validate(script)

        assertFalse(errors.any { it.message.contains("座標が未設定") })
        assertFalse(errors.any { it.message.contains("相対・ローカル") })
    }

    @Test
    fun `relative facing yaw passes validation`() {
        val script = DiskScript(name = "spatial")
        val teleport = GraphEditor.append(script.graph, CommandType.TELEPORT)
        teleport.targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        teleport.destinationSpec = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0)
        teleport.destinationFacingSpec = FacingSpec(
            FacingKind.ROTATION,
            yaw = 90f,
            pitch = 0f,
            semanticsYaw = CoordinateSemantics.RELATIVE,
        )

        val errors = ExecutableScriptValidator.validate(script)

        assertFalse(errors.any { it.message.contains("向きにローカル") })
        assertFalse(errors.any { it.message.contains("向きが未設定") })
    }

    @Test
    fun `persisted position requires explicit per axis semantics`() {
        val scriptId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()
        val root = JsonParser.parseString(
            """
            {
              "formatVersion": 13,
              "id": "$scriptId",
              "name": "spatial",
              "owner": "${UUID.randomUUID()}",
              "createdAt": 0,
              "activation": "NEEDS_REDSTONE",
              "timer": {"enabled": false, "intervalSeconds": 1.0},
              "graph": {
                "nodes": {
                  "$nodeId": {
                    "id": "$nodeId",
                    "type": "WAIT",
                    "params": {},
                    "destinationSpec": {"kind": "COORDINATES", "x": 1.0, "y": 2.0, "z": 3.0}
                  }
                }
              }
            }
            """.trimIndent(),
        ).asJsonObject

        val violations = PersistedScriptValidator.validate(root)

        assertTrue(violations.any { it.contains("semanticsX") })
    }
}
