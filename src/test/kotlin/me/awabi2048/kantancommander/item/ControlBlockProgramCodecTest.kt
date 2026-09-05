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
            params["kind"] = "CONTROL_BLOCK_STATE"
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
        assertEquals("CONTROL_BLOCK_STATE", decoded?.graph?.nodes?.get(node.id)?.params?.get("kind"))
        assertEquals(
            setOf(ControlBlockStateKind.REDSTONE_INPUT),
            decoded?.graph?.nodes?.get(node.id)?.selectedControlBlockStates(),
        )
    }

    @Test
    fun `malformed embedded data is rejected`() {
        assertNull(ControlBlockProgramCodec.decode("not-a-program"))
    }
}
