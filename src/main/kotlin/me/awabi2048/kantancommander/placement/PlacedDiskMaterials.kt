package me.awabi2048.kantancommander.placement

import org.bukkit.Material
import me.awabi2048.kantancommander.model.DiskProfile

object PlacedDiskMaterials {
    val materials = setOf(Material.ORANGE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.TEST_BLOCK)

    fun forTimer(enabled: Boolean, profile: DiskProfile = DiskProfile.STANDARD): Material =
        if (profile == DiskProfile.SIMPLE) Material.TEST_BLOCK
        else if (enabled) Material.PURPLE_STAINED_GLASS else Material.ORANGE_STAINED_GLASS

    fun isPlacedDisk(material: Material) = material in materials
}
