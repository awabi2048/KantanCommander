package me.awabi2048.kantancommander.item

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanItemKindTest {
    @Test
    fun `authorized control block material is a block`() {
        assertEquals(
            KantanItemKind.BLOCK,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.BLOCK_ITEM_ID, Material.INFESTED_STONE),
        )
    }

    @Test
    fun `locked control block material is a block`() {
        assertEquals(
            KantanItemKind.BLOCK,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.BLOCK_ITEM_ID, Material.POISONOUS_POTATO),
        )
    }

    @Test
    fun `program disk and locked control block share material but never confuse`() {
        assertEquals(
            KantanItemKind.DISK,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.DISK_ITEM_ID, Material.POISONOUS_POTATO),
        )
        assertEquals(
            KantanItemKind.BLOCK,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.BLOCK_ITEM_ID, Material.POISONOUS_POTATO),
        )
    }

    @Test
    fun `unexpected materials are unrelated items`() {
        assertEquals(
            KantanItemKind.NONE,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.BLOCK_ITEM_ID, Material.STONE),
        )
        assertEquals(
            KantanItemKind.NONE,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.DISK_ITEM_ID, Material.INFESTED_STONE),
        )
        assertEquals(
            KantanItemKind.NONE,
            KantanItemService.kindOfIdAndMaterial(null, Material.INFESTED_STONE),
        )
        assertEquals(
            KantanItemKind.NONE,
            KantanItemService.kindOfIdAndMaterial("kantan.unknown", Material.POISONOUS_POTATO),
        )
    }

    @Test
    fun `locked view resolves only for locked control blocks`() {        assertEquals(
            true,
            KantanItemService.isLockedView(KantanItemService.BLOCK_ITEM_ID, Material.POISONOUS_POTATO),
        )
        assertEquals(
            false,
            KantanItemService.isLockedView(KantanItemService.BLOCK_ITEM_ID, Material.INFESTED_STONE),
        )
        assertEquals(
            false,
            KantanItemService.isLockedView(KantanItemService.DISK_ITEM_ID, Material.POISONOUS_POTATO),
        )
    }

    @Test
    fun `wand resolves only for tropical fish base material`() {
        assertEquals(
            KantanItemKind.WAND,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.WAND_ITEM_ID, Material.TROPICAL_FISH),
        )
        assertEquals(
            KantanItemKind.NONE,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.WAND_ITEM_ID, Material.POISONOUS_POTATO),
        )
        assertEquals(
            KantanItemKind.NONE,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.WAND_ITEM_ID, Material.STICK),
        )
    }

    @Test
    fun `wand never confuses with block or disk even on shared materials`() {
        assertEquals(
            KantanItemKind.NONE,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.BLOCK_ITEM_ID, Material.TROPICAL_FISH),
        )
        assertEquals(
            KantanItemKind.NONE,
            KantanItemService.kindOfIdAndMaterial(KantanItemService.DISK_ITEM_ID, Material.TROPICAL_FISH),
        )
        assertEquals(
            false,
            KantanItemService.isLockedView(KantanItemService.WAND_ITEM_ID, Material.TROPICAL_FISH),
        )
    }
}
