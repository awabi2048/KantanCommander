package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaceItemDestinationTest {
    @Test
    fun `equipment slots keep the bukkit names`() {
        assertTrue(PlaceItemDestination.isValidSlot(PlaceItemDestination.EQUIPMENT, "HAND"))
        assertTrue(PlaceItemDestination.isValidSlot(PlaceItemDestination.EQUIPMENT, "OFF_HAND"))
        assertTrue(PlaceItemDestination.isValidSlot(PlaceItemDestination.EQUIPMENT, "HEAD"))
        assertFalse(PlaceItemDestination.isValidSlot(PlaceItemDestination.EQUIPMENT, "0"))
        assertFalse(PlaceItemDestination.isValidSlot(PlaceItemDestination.EQUIPMENT, "hand"))
    }

    @Test
    fun `numeric slots use zero based storage ranges`() {
        assertTrue(PlaceItemDestination.isValidSlot(PlaceItemDestination.PLAYER_HOTBAR, "0"))
        assertTrue(PlaceItemDestination.isValidSlot(PlaceItemDestination.PLAYER_HOTBAR, "8"))
        assertFalse(PlaceItemDestination.isValidSlot(PlaceItemDestination.PLAYER_HOTBAR, "9"))
        assertTrue(PlaceItemDestination.isValidSlot(PlaceItemDestination.PLAYER_INVENTORY, "26"))
        assertFalse(PlaceItemDestination.isValidSlot(PlaceItemDestination.PLAYER_INVENTORY, "27"))
        assertTrue(PlaceItemDestination.isValidSlot(PlaceItemDestination.BLOCK_CONTAINER, "53"))
        assertFalse(PlaceItemDestination.isValidSlot(PlaceItemDestination.BLOCK_CONTAINER, "54"))
        assertFalse(PlaceItemDestination.isValidSlot(PlaceItemDestination.PLAYER_HOTBAR, "abc"))
        assertTrue(PlaceItemDestination.isValidSlot(PlaceItemDestination.ITEM_FRAME, "CONTENTS"))
        assertFalse(PlaceItemDestination.isValidSlot(PlaceItemDestination.ITEM_FRAME, "0"))
    }

    @Test
    fun `gui shows one based slots while storage stays zero based`() {
        assertEquals("1", PlaceItemDestination.toDisplay(PlaceItemDestination.PLAYER_HOTBAR, "0"))
        assertEquals("9", PlaceItemDestination.toDisplay(PlaceItemDestination.PLAYER_HOTBAR, "8"))
        assertEquals("27", PlaceItemDestination.toDisplay(PlaceItemDestination.PLAYER_INVENTORY, "26"))
        assertEquals("54", PlaceItemDestination.toDisplay(PlaceItemDestination.BLOCK_CONTAINER, "53"))
        assertEquals("HAND", PlaceItemDestination.toDisplay(PlaceItemDestination.EQUIPMENT, "HAND"))

        assertEquals("0", PlaceItemDestination.fromDisplay(PlaceItemDestination.PLAYER_HOTBAR, "1"))
        assertEquals("8", PlaceItemDestination.fromDisplay(PlaceItemDestination.PLAYER_HOTBAR, "9"))
        assertEquals("53", PlaceItemDestination.fromDisplay(PlaceItemDestination.BLOCK_CONTAINER, "54"))
        assertNull(PlaceItemDestination.fromDisplay(PlaceItemDestination.PLAYER_HOTBAR, "0"))
        assertNull(PlaceItemDestination.fromDisplay(PlaceItemDestination.PLAYER_HOTBAR, "10"))
        assertNull(PlaceItemDestination.fromDisplay(PlaceItemDestination.PLAYER_HOTBAR, "abc"))
        assertEquals("HAND", PlaceItemDestination.fromDisplay(PlaceItemDestination.EQUIPMENT, "HAND"))
        assertNull(PlaceItemDestination.fromDisplay(PlaceItemDestination.EQUIPMENT, "0"))
    }

    @Test
    fun `vanilla slots follow the item replace entity and block syntax`() {
        assertEquals("weapon.mainhand", PlaceItemDestination.vanillaSlot(PlaceItemDestination.EQUIPMENT, "HAND"))
        assertEquals("weapon.offhand", PlaceItemDestination.vanillaSlot(PlaceItemDestination.EQUIPMENT, "OFF_HAND"))
        assertEquals("armor.head", PlaceItemDestination.vanillaSlot(PlaceItemDestination.EQUIPMENT, "HEAD"))
        assertEquals("armor.chest", PlaceItemDestination.vanillaSlot(PlaceItemDestination.EQUIPMENT, "CHEST"))
        assertEquals("armor.legs", PlaceItemDestination.vanillaSlot(PlaceItemDestination.EQUIPMENT, "LEGS"))
        assertEquals("armor.feet", PlaceItemDestination.vanillaSlot(PlaceItemDestination.EQUIPMENT, "FEET"))
        assertEquals("hotbar.8", PlaceItemDestination.vanillaSlot(PlaceItemDestination.PLAYER_HOTBAR, "8"))
        assertEquals("inventory.26", PlaceItemDestination.vanillaSlot(PlaceItemDestination.PLAYER_INVENTORY, "26"))
        assertEquals("contents", PlaceItemDestination.vanillaSlot(PlaceItemDestination.ITEM_FRAME, "CONTENTS"))
        assertEquals("container.53", PlaceItemDestination.vanillaSlot(PlaceItemDestination.BLOCK_CONTAINER, "53"))
        assertNull(PlaceItemDestination.vanillaSlot(PlaceItemDestination.PLAYER_HOTBAR, "9"))
        assertNull(PlaceItemDestination.vanillaSlot(PlaceItemDestination.EQUIPMENT, "0"))
    }

    @Test
    fun `empty check paths follow the serialized slot bytes`() {
        // F1: 本体域インベントリはSlotバイト（9〜35）へ写像します。
        assertEquals("Inventory[{Slot:8b}]", PlaceItemDestination.emptyCheckNbtPath(PlaceItemDestination.PLAYER_HOTBAR, "8"))
        assertEquals("Inventory[{Slot:9b}]", PlaceItemDestination.emptyCheckNbtPath(PlaceItemDestination.PLAYER_INVENTORY, "0"))
        assertEquals("Inventory[{Slot:35b}]", PlaceItemDestination.emptyCheckNbtPath(PlaceItemDestination.PLAYER_INVENTORY, "26"))
        assertEquals("Item", PlaceItemDestination.emptyCheckNbtPath(PlaceItemDestination.ITEM_FRAME, "CONTENTS"))
        assertEquals("Items[{Slot:0b}]", PlaceItemDestination.emptyCheckNbtPath(PlaceItemDestination.BLOCK_CONTAINER, "0"))
        assertEquals("Items[{Slot:53b}]", PlaceItemDestination.emptyCheckNbtPath(PlaceItemDestination.BLOCK_CONTAINER, "53"))
        // 装備先は対象種別でNBT構造が変わり等価な検査を作れないため未対応とします。
        assertNull(PlaceItemDestination.emptyCheckNbtPath(PlaceItemDestination.EQUIPMENT, "HAND"))
        assertNull(PlaceItemDestination.emptyCheckNbtPath(PlaceItemDestination.PLAYER_HOTBAR, "9"))
        assertNull(PlaceItemDestination.emptyCheckNbtPath(PlaceItemDestination.BLOCK_CONTAINER, "54"))
    }

    @Test
    fun `player inventory maps to the bukkit storage slots`() {        assertEquals(0, PlaceItemDestination.bukkitPlayerSlot(PlaceItemDestination.PLAYER_HOTBAR, "0"))
        assertEquals(8, PlaceItemDestination.bukkitPlayerSlot(PlaceItemDestination.PLAYER_HOTBAR, "8"))
        assertEquals(9, PlaceItemDestination.bukkitPlayerSlot(PlaceItemDestination.PLAYER_INVENTORY, "0"))
        assertEquals(35, PlaceItemDestination.bukkitPlayerSlot(PlaceItemDestination.PLAYER_INVENTORY, "26"))
        assertNull(PlaceItemDestination.bukkitPlayerSlot(PlaceItemDestination.PLAYER_INVENTORY, "27"))
        assertNull(PlaceItemDestination.bukkitPlayerSlot(PlaceItemDestination.EQUIPMENT, "HAND"))
        assertNull(PlaceItemDestination.bukkitPlayerSlot(PlaceItemDestination.BLOCK_CONTAINER, "0"))
    }

    @Test
    fun `destinations declare their required target kind`() {
        assertTrue(PlaceItemDestination.EQUIPMENT.requiresEntityTarget)
        assertTrue(PlaceItemDestination.PLAYER_HOTBAR.requiresEntityTarget)
        assertTrue(PlaceItemDestination.PLAYER_INVENTORY.requiresEntityTarget)
        assertTrue(PlaceItemDestination.ITEM_FRAME.requiresEntityTarget)
        assertTrue(PlaceItemDestination.BLOCK_CONTAINER.requiresBlockPosition)
        assertFalse(PlaceItemDestination.BLOCK_CONTAINER.requiresEntityTarget)
        assertFalse(PlaceItemDestination.EQUIPMENT.requiresBlockPosition)
        assertNull(PlaceItemDestination.from("UNKNOWN"))
    }
}
