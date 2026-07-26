package me.awabi2048.kantancommander.placement

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlacedDiskMaterialsTest {
    @Test
    fun `timer mode selects command-block colored glass`() {
        assertEquals(Material.ORANGE_STAINED_GLASS, PlacedDiskMaterials.forTimer(false))
        assertEquals(Material.PURPLE_STAINED_GLASS, PlacedDiskMaterials.forTimer(true))
        assertTrue(PlacedDiskMaterials.isPlacedDisk(Material.ORANGE_STAINED_GLASS))
        assertFalse(PlacedDiskMaterials.isPlacedDisk(Material.NOTE_BLOCK))
    }
}
