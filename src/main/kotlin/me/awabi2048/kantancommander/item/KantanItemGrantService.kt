package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.api.item.ItemGrantResult
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.security.PlacementDecision
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * かんたんコマンダー制御ブロックの付与処理を一つに集約します。
 *
 * 共通の `/cc give` API と `/kankoma` の自己付与が別々にインベントリ操作を
 * 実装すると、ドロップ処理や付与結果の形式が経路ごとにずれます。このサービスを
 * 両方の入口から使い、付与結果を同じ処理で確定させます。成否の通知は呼出側が行い、
 * ここではコンソールへ出しません。
 */
class KantanItemGrantService(private val plugin: KantanCommanderPlugin) {
    fun grant(
        sender: CommandSender,
        target: Player,
        amount: Int,
        source: String,
    ): ItemGrantResult {
        val result = if (amount !in 1..KantanItemService.MAX_GRANT_AMOUNT) {
            ItemGrantResult(false, 0, 0, "invalid amount")
        } else {
            runCatching {
                var dropped = 0
                // 付与直後から権限状態に合った表示にし、 locked表示への切替を
                // 次の再評価点まで遅延させません。
                val locked = plugin.placementAccess.decide(target, target.world.name) !=
                    PlacementDecision.ALLOW
                repeat(amount) {
                    val item = KantanItemService.normalizeControlBlockView(
                        KantanItemService.createBlock(target),
                        locked,
                    ) ?: KantanItemService.createBlock(target)
                    target.inventory.addItem(item).values.forEach { overflow ->
                        dropped += overflow.amount
                        target.world.dropItemNaturally(target.location, overflow)
                    }
                }
                ItemGrantResult(
                    success = true,
                    grantedAmount = (amount - dropped).coerceAtLeast(0),
                    droppedAmount = dropped,
                    message = null,
                )
            }.getOrElse { failure ->
                ItemGrantResult(false, 0, 0, failure.message ?: "grant failed")
            }
        }

        // 付与の成否は呼出側が操作者へ通知するため、ここではコンソールへ出しません。
        return result
    }
}
