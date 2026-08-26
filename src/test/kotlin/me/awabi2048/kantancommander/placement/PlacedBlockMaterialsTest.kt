package me.awabi2048.kantancommander.placement

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlacedBlockMaterialsTest {
    @Test
    fun `timer mode selects command-block colored glass`() {
        assertEquals(Material.ORANGE_STAINED_GLASS, PlacedBlockMaterials.forTimer(false))
        assertEquals(Material.PURPLE_STAINED_GLASS, PlacedBlockMaterials.forTimer(true))
        assertTrue(PlacedBlockMaterials.isPlacedBlock(Material.ORANGE_STAINED_GLASS))
        assertTrue(PlacedBlockMaterials.isPlacedBlock(Material.PURPLE_STAINED_GLASS))
        assertFalse(PlacedBlockMaterials.isPlacedBlock(Material.TEST_BLOCK))
        assertFalse(PlacedBlockMaterials.isPlacedBlock(Material.NOTE_BLOCK))
    }
}