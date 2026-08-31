package me.awabi2048.kantancommander.execution

import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.RedstoneWire
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedstoneWireTopologyTest {
    @Test
    fun `isolated dust next to extended target keeps the placement cross shape`() {
        val actual = RedstoneWireTopology.resolveHorizontalConnections(
            vanillaConnections = horizontalConnections(RedstoneWire.Connection.NONE),
            extendedTargetFaces = setOf(BlockFace.NORTH),
            adjacentDustFaces = emptySet(),
        )

        assertEquals(horizontalConnections(RedstoneWire.Connection.SIDE), actual)
    }

    @Test
    fun `extended target adds only its face when another dust is present`() {
        val vanilla = mapOf(
            BlockFace.NORTH to RedstoneWire.Connection.NONE,
            BlockFace.SOUTH to RedstoneWire.Connection.NONE,
            BlockFace.EAST to RedstoneWire.Connection.SIDE,
            BlockFace.WEST to RedstoneWire.Connection.NONE,
        )

        val actual = RedstoneWireTopology.resolveHorizontalConnections(
            vanillaConnections = vanilla,
            extendedTargetFaces = setOf(BlockFace.NORTH),
            adjacentDustFaces = setOf(BlockFace.EAST),
        )

        assertEquals(RedstoneWire.Connection.SIDE, actual[BlockFace.NORTH])
        assertEquals(RedstoneWire.Connection.NONE, actual[BlockFace.SOUTH])
        assertEquals(RedstoneWire.Connection.SIDE, actual[BlockFace.EAST])
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
            RedstoneWireTopology.resolveHorizontalConnections(
                vanillaConnections = vanilla,
                extendedTargetFaces = emptySet(),
                adjacentDustFaces = emptySet(),
            ),
        )
    }

    @Test
    fun `multiple extended target faces connect independently`() {
        val actual = RedstoneWireTopology.resolveHorizontalConnections(
            vanillaConnections = horizontalConnections(RedstoneWire.Connection.NONE),
            extendedTargetFaces = setOf(BlockFace.EAST, BlockFace.WEST),
            adjacentDustFaces = emptySet(),
        )

        assertEquals(RedstoneWire.Connection.SIDE, actual[BlockFace.NORTH])
        assertEquals(RedstoneWire.Connection.SIDE, actual[BlockFace.SOUTH])
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
