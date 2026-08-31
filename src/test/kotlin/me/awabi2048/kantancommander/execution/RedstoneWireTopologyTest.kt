package me.awabi2048.kantancommander.execution

import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.RedstoneWire
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedstoneWireTopologyTest {
    @Test
    fun `extended target replaces only its face and clears stale extra connections`() {
        val vanilla = horizontalConnections(RedstoneWire.Connection.NONE)

        val actual = RedstoneWireTopology.resolveHorizontalConnections(
            vanillaConnections = vanilla,
            extendedTargetFaces = setOf(BlockFace.NORTH),
        )

        assertEquals(RedstoneWire.Connection.SIDE, actual[BlockFace.NORTH])
        assertEquals(RedstoneWire.Connection.NONE, actual[BlockFace.SOUTH])
        assertEquals(RedstoneWire.Connection.NONE, actual[BlockFace.EAST])
        assertEquals(RedstoneWire.Connection.NONE, actual[BlockFace.WEST])
    }

    @Test
    fun `vanilla connection shape is retained on non-target faces`() {
        val vanilla = mapOf(
            BlockFace.NORTH to RedstoneWire.Connection.SIDE,
            BlockFace.SOUTH to RedstoneWire.Connection.UP,
            BlockFace.EAST to RedstoneWire.Connection.NONE,
            BlockFace.WEST to RedstoneWire.Connection.SIDE,
        )

        assertEquals(
            vanilla,
            RedstoneWireTopology.resolveHorizontalConnections(vanilla, emptySet()),
        )
    }

    @Test
    fun `multiple extended target faces connect independently`() {
        val actual = RedstoneWireTopology.resolveHorizontalConnections(
            vanillaConnections = horizontalConnections(RedstoneWire.Connection.NONE),
            extendedTargetFaces = setOf(BlockFace.EAST, BlockFace.WEST),
        )

        assertEquals(RedstoneWire.Connection.NONE, actual[BlockFace.NORTH])
        assertEquals(RedstoneWire.Connection.NONE, actual[BlockFace.SOUTH])
        assertEquals(RedstoneWire.Connection.SIDE, actual[BlockFace.EAST])
        assertEquals(RedstoneWire.Connection.SIDE, actual[BlockFace.WEST])
    }

    private fun horizontalConnections(connection: RedstoneWire.Connection) = mapOf(
        BlockFace.NORTH to connection,
        BlockFace.SOUTH to connection,
        BlockFace.EAST to connection,
        BlockFace.WEST to connection,
    )
}
