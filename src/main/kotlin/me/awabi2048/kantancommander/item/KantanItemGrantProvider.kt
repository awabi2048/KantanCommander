package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.api.item.ItemGrantDefinition
import com.awabi2048.ccsystem.api.item.ItemGrantProvider
import com.awabi2048.ccsystem.api.item.ItemGrantRequest
import com.awabi2048.ccsystem.api.item.ItemGrantResult

/**
 * 配布対象はかんたんコマンダー制御ブロックだけとする。
 * プログラムディスクは内容を明示的に出力した場合にのみ生成されるため、配布定義を持たない。
 */
class KantanItemGrantProvider(
    private val grantService: KantanItemGrantService,
) : ItemGrantProvider {
    override val owner: String = "kantan"

    override fun definitions(): Collection<ItemGrantDefinition> =
        listOf(
            ItemGrantDefinition(
                id = KantanItemService.BLOCK_ITEM_ID,
                permission = "cc.item.give.kantan",
                maximumAmount = KantanItemService.MAX_GRANT_AMOUNT,
                argumentSuggestions = { emptyList() },
            ),
        )

    override fun grant(request: ItemGrantRequest): ItemGrantResult = grantService.grant(
        sender = request.sender,
        target = request.target,
        amount = request.amount,
        source = "CC-System ItemGrant API",
    )
}
