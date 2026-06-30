package me.awabi2048.kantancommander.gui

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

class KantanMenuHolder(
    val ownerId: UUID,
    val menuOwner: String,
    val menuId: String
) : InventoryHolder {
    lateinit var backingInventory: Inventory
    override fun getInventory(): Inventory = backingInventory
}
