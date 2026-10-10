package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PlaceItemValidatorTest {
    private fun validEntityNode() = DiskScript(name = "place").let { script ->
        GraphEditor.append(script.graph, CommandType.PLACE_ITEM).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            params["placeDestination"] = "EQUIPMENT"
            params["slot"] = "HAND"
            params["item"] = "minecraft:stone"
            params["count"] = "1"
            params["overwrite"] = "false"
        } to script
    }

    @Test
    fun `equipment placement with required fields passes`() {
        val (node, script) = validEntityNode()

        assertTrue(ExecutableScriptValidator.validate(script).none { it.nodeId == node.id })
    }

    @Test
    fun `unknown destination fails without further checks`() {
        val script = DiskScript(name = "place")
        val node = GraphEditor.append(script.graph, CommandType.PLACE_ITEM).apply {
            params["placeDestination"] = "UNKNOWN"
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertTrue(errors.any { it.nodeId == node.id && it.fieldKeys == setOf("placeDestination") })
    }

    @Test
    fun `entity destinations require a target and block destinations require a position`() {
        val entityScript = DiskScript(name = "place")
        val entityNode = GraphEditor.append(entityScript.graph, CommandType.PLACE_ITEM).apply {
            params["placeDestination"] = "PLAYER_HOTBAR"
            params["slot"] = "0"
            params["item"] = "minecraft:stone"
        }
        assertTrue(
            ExecutableScriptValidator.validate(entityScript).any {
                it.nodeId == entityNode.id && it.fieldKeys == setOf("target")
            },
        )

        val blockScript = DiskScript(name = "place")
        val blockNode = GraphEditor.append(blockScript.graph, CommandType.PLACE_ITEM).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            params["placeDestination"] = "BLOCK_CONTAINER"
            params["slot"] = "0"
            params["item"] = "minecraft:stone"
        }
        assertTrue(
            ExecutableScriptValidator.validate(blockScript).any {
                it.nodeId == blockNode.id && it.fieldKeys == setOf("position")
            },
        )

        blockNode.blockPositionSpec = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0)
        assertFalse(
            ExecutableScriptValidator.validate(blockScript).any { it.nodeId == blockNode.id },
        )
    }

    @Test
    fun `slot item count and overwrite follow the destination contract`() {
        val script = DiskScript(name = "place")
        val node = GraphEditor.append(script.graph, CommandType.PLACE_ITEM).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            params["placeDestination"] = "PLAYER_HOTBAR"
            params["slot"] = "9"
            params["item"] = ""
            params["count"] = "0"
            params["overwrite"] = "maybe"
        }

        val errors = ExecutableScriptValidator.validate(script)
        val fields = errors.filter { it.nodeId == node.id }.flatMap { it.fieldKeys }.toSet()

        assertTrue(fields.contains("slot"))
        assertTrue(fields.contains("item"))
        assertTrue(fields.contains("count"))
        assertTrue(fields.contains("overwrite"))
    }

    @Test
    fun `frame contents accept the fixed slot and hotbar rejects equipment names`() {
        val script = DiskScript(name = "place")
        val frame = GraphEditor.append(script.graph, CommandType.PLACE_ITEM).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
            params["placeDestination"] = "ITEM_FRAME"
            params["slot"] = "CONTENTS"
            params["item"] = "minecraft:stone"
        }
        val hotbar = GraphEditor.append(script.graph, CommandType.PLACE_ITEM).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            params["placeDestination"] = "PLAYER_HOTBAR"
            params["slot"] = "HAND"
            params["item"] = "minecraft:stone"
        }

        val errors = ExecutableScriptValidator.validate(script)

        assertFalse(errors.any { it.nodeId == frame.id })
        assertTrue(errors.any { it.nodeId == hotbar.id && it.fieldKeys == setOf("slot") })
    }

    @Test
    fun `count accepts variable templates without an upper bound`() {
        val script = DiskScript(name = "place")
        val node = GraphEditor.append(script.graph, CommandType.PLACE_ITEM).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            params["placeDestination"] = "EQUIPMENT"
            params["slot"] = "HAND"
            params["item"] = "minecraft:stone"
            params["count"] = "1000000"
        }

        assertFalse(
            ExecutableScriptValidator.validate(script).any { it.nodeId == node.id },
        )
    }

    @Test
    fun `legacy equip action no longer validates as equipment placement`() {
        val script = DiskScript(name = "place")
        GraphEditor.append(script.graph, CommandType.ENTITY_ACTION).apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_ENTITY)
            params["action"] = "equip"
            params["slot"] = "HAND"
            params["item"] = "minecraft:stone"
        }

        assertTrue(
            ExecutableScriptValidator.validate(script).any { it.fieldKeys == setOf("action") },
        )
    }
}
