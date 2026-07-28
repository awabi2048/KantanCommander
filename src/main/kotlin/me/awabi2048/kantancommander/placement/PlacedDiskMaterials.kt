package me.awabi2048.kantancommander.placement

import org.bukkit.Material

object PlacedDiskMaterials {
    val materials = setOf(Material.ORANGE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS)

    fun forTimer(enabled: Boolean): Material =
        if (enabled) Material.PURPLE_STAINED_GLASS else Material.ORANGE_STAINED_GLASS

    fun isPlacedDisk(material: Material) = material in materials
}
