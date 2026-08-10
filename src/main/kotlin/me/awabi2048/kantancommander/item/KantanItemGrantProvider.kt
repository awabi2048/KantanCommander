package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.api.item.ItemGrantDefinition
import com.awabi2048.ccsystem.api.item.ItemGrantProvider
import com.awabi2048.ccsystem.api.item.ItemGrantRequest
import com.awabi2048.ccsystem.api.item.ItemGrantResult
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskProfile

class KantanItemGrantProvider(
    private val plugin: KantanCommanderPlugin
) : ItemGrantProvider {
    override val owner: String = "kantan"

    override fun definitions(): Collection<ItemGrantDefinition> =
        listOf(
            ItemGrantDefinition(
                id = DiskItemService.STANDARD_ITEM_ID,
                permission = "cc.item.give.kantan",
                maximumAmount = 1,
                argumentSuggestions = { emptyList() }
            ),
            ItemGrantDefinition(
                id = DiskItemService.SIMPLE_ITEM_ID,
                permission = "cc.item.give.kantan",
                maximumAmount = 1,
                argumentSuggestions = { emptyList() }
            ),
        )

    override fun grant(request: ItemGrantRequest): ItemGrantResult {
        val name = request.arguments.joinToString(" ").ifBlank {
            plugin.config.getString("default-disk-name", "Kantan Disk") ?: "Kantan Disk"
        }
        return runCatching {
            val profile = if (request.definition.id == DiskItemService.SIMPLE_ITEM_ID) {
                DiskProfile.SIMPLE
            } else DiskProfile.STANDARD
            val item = DiskItemService.createUnset(name, request.target, profile)
            var dropped = 0
            request.target.inventory.addItem(item).values.forEach { overflow ->
                dropped += overflow.amount
                request.target.world.dropItemNaturally(request.target.location, overflow)
            }
            ItemGrantResult(
                success = true,
                grantedAmount = if (dropped == 0) 1 else 0,
                droppedAmount = dropped,
                message = null
            )
        }.getOrElse { failure -> ItemGrantResult(false, 0, 0, failure.message ?: "disk creation failed") }
    }
}
