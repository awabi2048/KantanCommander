package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.api.item.ItemGrantResult
import java.util.logging.Level
import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * かんたんコマンダー制御ブロックの付与処理を一つに集約します。
 *
 * 共通の `/cc give` API と `/kankoma` の自己付与が別々にインベントリ操作を
 * 実装すると、ドロップ処理や監査ログの有無が経路ごとにずれます。このサービスを
 * 両方の入口から使い、付与結果と監査ログを同じ処理で確定させます。
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
                repeat(amount) {
                    target.inventory.addItem(KantanItemService.createBlock(target)).values.forEach { overflow ->
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

        logGrant(sender, target, amount, source, result)
        return result
    }

    /** 成功だけでなく、満杯によるドロップや生成失敗も監査できる形式で残します。 */
    private fun logGrant(
        sender: CommandSender,
        target: Player,
        amount: Int,
        source: String,
        result: ItemGrantResult,
    ) {
        val level = if (result.success) Level.INFO else Level.WARNING
        val reason = result.message?.let { ", reason=$it" } ?: ""
        plugin.logger.log(
            level,
            "かんたんコマンダー制御ブロック付与: source=$source, " +
                "sender=${sender.name}, target=${target.name}, targetUuid=${target.uniqueId}, " +
                "amount=$amount, granted=${result.grantedAmount}, dropped=${result.droppedAmount}, " +
                "world=${target.world.name}, location=${target.location.blockX}," +
                "${target.location.blockY},${target.location.blockZ}$reason",
        )
    }
}
