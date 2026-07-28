package me.awabi2048.kantancommander.item

import org.bukkit.inventory.ItemStack
import java.util.Base64

object ItemStackCodec {
    fun encode(item: ItemStack): String =
        Base64.getEncoder().encodeToString(item.clone().apply { amount = 1 }.serializeAsBytes())

    fun decode(raw: String): ItemStack? = runCatching {
        ItemStack.deserializeBytes(Base64.getDecoder().decode(raw))
    }.getOrNull()
}
