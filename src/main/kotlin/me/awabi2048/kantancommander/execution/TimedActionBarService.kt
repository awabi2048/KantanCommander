package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DisplayTextTiming
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * Bukkit APIだけでアクションバーの表示ライフサイクルを実現します。
 *
 * AdventureのsendActionBarにはTitle.Timesのようなフェード設定がなく、クライアント
 * の既定表示時間を超えて表示し続けることもできません。そのため表示中は一定間隔で
 * 再送し、fadeIn/stay/fadeOutの合計時間が経過したら空のアクションバーを送ります。
 * プレイヤー単位の世代番号で、古いタイマーが後から新しい表示を消さないようにします。
 */
internal class TimedActionBarService(private val plugin: KantanCommanderPlugin) {
    private companion object {
        const val REFRESH_TICKS = 20L
    }

    private val generations = mutableMapOf<UUID, Long>()
    private val tasks = mutableMapOf<UUID, BukkitTask>()
    private val clearTasks = mutableMapOf<UUID, BukkitTask>()

    /** 新しい表示を開始し、同じプレイヤーの前回表示を終了させます。 */
    fun show(player: Player, text: Component, timing: DisplayTextTiming) {
        val playerId = player.uniqueId
        cancel(player, clear = false)
        val generation = nextGeneration(playerId)
        player.sendActionBar(text)

        if (timing.totalTicks <= 0L) {
            val clearTask = plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (!isCurrent(player, generation)) return@Runnable
                if (player.isOnline) player.sendActionBar(Component.empty())
                finish(playerId, generation)
            }, 1L)
            clearTasks[playerId] = clearTask
            return
        }

        lateinit var refreshTask: BukkitTask
        refreshTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable {
                if (!isCurrent(player, generation)) {
                    refreshTask.cancel()
                    return@Runnable
                }
                if (!player.isOnline) {
                    finish(playerId, generation)
                    return@Runnable
                }
                // クライアント既定の保持時間を超える設定でも表示を維持します。
                // 終了時刻は別のrunTaskLaterで管理し、20tick刻みの再送によって
                // 0.05秒単位の設定が最大1秒ずれることを防ぎます。
                player.sendActionBar(text)
            },
            REFRESH_TICKS,
            REFRESH_TICKS,
        )
        tasks[playerId] = refreshTask
        val clearTask = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (!isCurrent(player, generation)) return@Runnable
            if (player.isOnline) player.sendActionBar(Component.empty())
            finish(playerId, generation)
        }, timing.totalTicks)
        clearTasks[playerId] = clearTask
    }

    /** 他の表示方式へ切り替える際に、保留中の再送を停止します。 */
    fun cancel(player: Player, clear: Boolean) {
        val playerId = player.uniqueId
        val hadState = tasks.containsKey(playerId) || clearTasks.containsKey(playerId) || generations.containsKey(playerId)
        tasks.remove(playerId)?.cancel()
        clearTasks.remove(playerId)?.cancel()
        if (hadState) nextGeneration(playerId)
        if (clear && player.isOnline) player.sendActionBar(Component.empty())
    }

    private fun isCurrent(player: Player, generation: Long): Boolean =
        generations[player.uniqueId] == generation

    private fun nextGeneration(playerId: UUID): Long {
        val next = (generations[playerId] ?: 0L) + 1L
        generations[playerId] = next
        return next
    }

    private fun finish(playerId: UUID, generation: Long) {
        if (generations[playerId] != generation) {
            return
        }
        tasks.remove(playerId)?.cancel()
        clearTasks.remove(playerId)?.cancel()
        generations.remove(playerId)
    }
}
