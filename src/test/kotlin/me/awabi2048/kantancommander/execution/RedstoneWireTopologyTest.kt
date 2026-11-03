package me.awabi2048.kantancommander.execution

import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.RedstoneWire
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedstoneWireTopologyTest {
    @Test
    fun `周辺索引の判定はチャンク境界と高さ差を含む`() {
        assertTrue(isTopologyAffected(15, 64, -16, 18, 65, -19))
        assertTrue(isTopologyAffected(15, 64, -16, 12, 63, -13))
        assertFalse(isTopologyAffected(15, 64, -16, 19, 64, -16))
        assertFalse(isTopologyAffected(15, 64, -16, 15, 66, -16))
        assertFalse(isTopologyAffected(Int.MIN_VALUE, 0, 0, Int.MAX_VALUE, 0, 0))
    }

    @Test
    fun `候補ダストから到達できる全制御ブロックが事前判定に含まれる`() {
        val center = Triple(15, 64, -16)
        val horizontal = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        val seeds = listOf(center) + BlockFace.entries.filter {
            it in listOf(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
        }.map { Triple(center.first + it.modX, center.second + it.modY, center.third + it.modZ) }
        val wires = seeds + seeds.flatMap { (x, y, z) -> horizontal.map { (dx, dz) -> Triple(x + dx, y, z + dz) } }
        for ((x, y, z) in wires) for ((dx, dz) in horizontal) {
            assertTrue(isTopologyAffected(center.first, center.second, center.third, x + dx, y, z + dz))
        }
    }
    @Test
    fun `isolated dust next to extended target keeps the placement cross shape`() {
        val actual = RedstoneWireTopology.resolveHorizontalConnections(
            vanillaConnections = horizontalConnections(RedstoneWire.Connection.NONE),
            extendedTargetFaces = setOf(BlockFace.NORTH),
            adjacentConnectableFaces = emptySet(),
        )

        assertEquals(horizontalConnections(RedstoneWire.Connection.SIDE), actual)
    }

    @Test
    fun `extended target adds only its face when another redstone-connectable block is present`() {
        val vanilla = mapOf(
            BlockFace.NORTH to RedstoneWire.Connection.NONE,
            BlockFace.SOUTH to RedstoneWire.Connection.NONE,
            BlockFace.EAST to RedstoneWire.Connection.SIDE,
            BlockFace.WEST to RedstoneWire.Connection.NONE,
        )

        val actual = RedstoneWireTopology.resolveHorizontalConnections(
            vanillaConnections = vanilla,
            extendedTargetFaces = setOf(BlockFace.NORTH),
            adjacentConnectableFaces = setOf(BlockFace.EAST),
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
                adjacentConnectableFaces = emptySet(),
            ),
        )
    }

    @Test
    fun `multiple extended target faces connect independently`() {
        val actual = RedstoneWireTopology.resolveHorizontalConnections(
            vanillaConnections = horizontalConnections(RedstoneWire.Connection.NONE),
            extendedTargetFaces = setOf(BlockFace.EAST, BlockFace.WEST),
            adjacentConnectableFaces = emptySet(),
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
