package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.api.item.ItemGrantDefinition
import com.awabi2048.ccsystem.api.item.ItemGrantProvider
import com.awabi2048.ccsystem.api.item.ItemGrantRequest
import com.awabi2048.ccsystem.api.item.ItemGrantResult
import me.awabi2048.kantancommander.KantanCommanderPlugin

/**
 * 配布対象は拡張コマンドブロックだけとする。
 * コマンドディスクは内容を明示的に出力した場合にのみ生成されるため、配布定義を持たない。
 */
class KantanItemGrantProvider(
    private val plugin: KantanCommanderPlugin
) : ItemGrantProvider {
    override val owner: String = "kantan"

    override fun definitions(): Collection<ItemGrantDefinition> =
        listOf(
            ItemGrantDefinition(
                id = KantanItemService.BLOCK_ITEM_ID,
                permission = "cc.item.give.kantan",
                maximumAmount = 1,
                argumentSuggestions = { emptyList() }
            ),
        )

    override fun grant(request: ItemGrantRequest): ItemGrantResult {
        return runCatching {
            val item = KantanItemService.createBlock(request.target)
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
        }.getOrElse { failure -> ItemGrantResult(false, 0, 0, failure.message ?: "block creation failed") }
    }
}