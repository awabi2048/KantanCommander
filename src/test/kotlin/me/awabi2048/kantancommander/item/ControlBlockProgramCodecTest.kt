package me.awabi2048.kantancommander.item

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ControlBlockStateKind
import me.awabi2048.kantancommander.model.DiskScript
import me.awabi2048.kantancommander.model.selectedControlBlockStates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ControlBlockProgramCodecTest {
    @Test
    fun `embedded program round trips the complete graph`() {
        val node = CommandNode(type = CommandType.CONDITION).apply {
            params["subject"] = "CONTROL_BLOCK"
            controlBlockStates = linkedSetOf(ControlBlockStateKind.REDSTONE_INPUT)
        }
        val script = DiskScript(
            name = "同梱テスト",
            owner = UUID.randomUUID(),
            graph = me.awabi2048.kantancommander.model.CommandGraph(
                entryNodeId = node.id,
                nodes = linkedMapOf(node.id to node),
            ),
        )

        val decoded = ControlBlockProgramCodec.decode(ControlBlockProgramCodec.encode(script))

        assertNotNull(decoded)
        assertEquals(script.name, decoded?.name)
        assertEquals(script.owner, decoded?.owner)
        assertEquals(script.graph.entryNodeId, decoded?.graph?.entryNodeId)
        assertEquals(script.graph.nodes.keys, decoded?.graph?.nodes?.keys)
        assertEquals("CONTROL_BLOCK", decoded?.graph?.nodes?.get(node.id)?.params?.get("subject"))
        assertEquals(
            setOf(ControlBlockStateKind.REDSTONE_INPUT),
            decoded?.graph?.nodes?.get(node.id)?.selectedControlBlockStates(),
        )
    }

    @Test
    fun `malformed embedded data is rejected`() {
        assertNull(ControlBlockProgramCodec.decode("not-a-program"))
    }

    @Test
    fun `embedded legacy equip is normalized to place item`() {
        // #21: 制御ブロックアイテム内の旧装備操作も、復元時に PLACE_ITEM へ
        // 正本化します。ファイル読込と同じ正規化を適用します。
        val node = CommandNode(type = CommandType.ENTITY_ACTION).apply {
            params["action"] = "equip"
            params["slot"] = "HEAD"
            params["item"] = "minecraft:stone"
            params["overwrite"] = "false"
        }
        val script = DiskScript(
            name = "同梱移行テスト",
            owner = UUID.randomUUID(),
            graph = me.awabi2048.kantancommander.model.CommandGraph(
                entryNodeId = node.id,
                nodes = linkedMapOf(node.id to node),
            ),
        )

        val decoded = ControlBlockProgramCodec.decode(ControlBlockProgramCodec.encode(script))

        assertNotNull(decoded)
        val migrated = requireNotNull(decoded).graph.nodes.getValue(node.id)
        assertEquals(CommandType.PLACE_ITEM, migrated.type)
        assertEquals("EQUIPMENT", migrated.string("placeDestination", ""))
        assertEquals("HEAD", migrated.string("slot", ""))
        assertEquals("1", migrated.string("count", ""))
        assertEquals("false", migrated.string("overwrite", ""))
    }
}
