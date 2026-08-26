package me.awabi2048.kantancommander.placement

import org.bukkit.Material

/** 設置された拡張コマンドブロックの実体ブロック素材。タイマー状態を色で示す。 */
object PlacedBlockMaterials {
    val materials = setOf(Material.ORANGE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS)

    fun forTimer(enabled: Boolean): Material =
        if (enabled) Material.PURPLE_STAINED_GLASS else Material.ORANGE_STAINED_GLASS

    fun isPlacedBlock(material: Material) = material in materials
}