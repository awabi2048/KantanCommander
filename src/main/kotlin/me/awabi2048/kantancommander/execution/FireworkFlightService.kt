package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.FireworkSettings
import org.bukkit.Location
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID

/**
 * 花火の飛行と破裂を本実行・試射で共有する単一正本です。
 *
 * BukkitのFireworkには破裂位置の直接指定がないため、打ち上げ位置から
 * 上昇する実体を使い、指定高度への到達を毎tick監視して目標座標へ補正起爆します。
 * 監視タスクは実行セッションへ登録しません。最終ノード後にセッションが即時終了しても
 * 起爆予約が取り消されず、単発花火でも指定高度で破裂します。
 * 破裂演出は実体自身の `detonate()` を使うため、別演出の生成は行いません。
 */
class FireworkFlightService(private val plugin: KantanCommanderPlugin) {
    /**
     * 1発分の打ち上げ要求です。
     *
     * [shooter] は試射で発射者を自機へ固定し、接触起爆を避けるための指定です。
     * [summonedByScript] は本実行で召喚体台帳へ登録するためのスクリプトIDです。
     */
    data class Request(
        val launchAt: Location,
        val stack: ItemStack,
        val height: Double,
        val shooter: Player? = null,
        val summonedByScript: UUID? = null,
    )

    /** 高度監視の判定結果です。Bukkit非依存のため単体テストで検証します。 */
    internal enum class FlightCheck {
        WAIT,
        DETONATE,
        ABORT,
    }

    internal object FlightMonitor {
        /** 監視タスクの打ち切り余裕tickです。寿命バックストップ到達後に残留しないようにします。 */
        const val TIMEOUT_MARGIN_TICKS = 40L

        fun check(
            valid: Boolean,
            detonated: Boolean,
            targetChunkLoaded: Boolean,
            currentY: Double,
            targetY: Double,
            elapsedTicks: Long,
            timeoutTicks: Long,
        ): FlightCheck {
            // 実体が失効・起爆済みの場合は補正も再試行もできないため即時終了します。
            if (!valid || detonated) return FlightCheck.ABORT
            // 期限切れは無限飛行にしないため、自然起爆を待たず撤去へ寄せます。
            if (elapsedTicks >= timeoutTicks) return FlightCheck.ABORT
            // 目標チャンクが未読込の間はテレポート補正せず、期限まで待機します。
            if (!targetChunkLoaded) return FlightCheck.WAIT
            // 指定高度への到達・超過でだけ補正起爆し、厳密な高度を保証します。
            return if (currentY >= targetY) FlightCheck.DETONATE else FlightCheck.WAIT
        }

        /** 監視タスクの打ち切りtickです。寿命バックストップより後に設定します。 */
        fun timeoutTicks(height: Double): Long =
            FireworkSettings.detonationTicks(height) + FireworkSettings.LIFETIME_MARGIN + TIMEOUT_MARGIN_TICKS
    }

    /**
     * 花火を打ち上げます。成功時はtrueを返します。
     *
     * 高さ0以下は演出なしの即時破裂です。召喚体台帳への登録失敗時は実体を撤去し、
     * 呼び出し側が試行失敗として扱えるよう例外を逃がします。
     */
    fun launch(request: Request): Boolean {
        val world = request.launchAt.world ?: return false
        if (!request.height.isFinite()) return false
        if (request.height <= 0.0) {
            val instant = world.spawn(request.launchAt.clone(), Firework::class.java)
            instant.setItem(request.stack.clone())
            instant.setShotAtAngle(false)
            request.shooter?.let { instant.shooter = it }
            instant.detonate()
            return true
        }
        val rocket = world.spawn(request.launchAt.clone(), Firework::class.java)
        rocket.setItem(request.stack.clone())
        rocket.setShotAtAngle(false)
        request.shooter?.let { rocket.shooter = it }
        // 初速を厳密値で上書きし、XZドリフト（0×1.15の恒久0）を確定します。
        rocket.velocity = Vector(0.0, FireworkSettings.INITIAL_UPWARD_VELOCITY, 0.0)
        // 自然起爆までの寿命は保険です。到達判定が担うため長めに設定します。
        rocket.ticksToDetonate = FireworkSettings.backstopLifetime(request.height)
        val summonedByScript = request.summonedByScript
        if (summonedByScript != null) {
            try {
                plugin.summonedEntities.register(rocket, summonedByScript)
            } catch (failure: Throwable) {
                runCatching { rocket.remove() }
                throw failure
            }
        }
        val target = request.launchAt.clone().add(0.0, request.height, 0.0)
        val timeoutTicks = FlightMonitor.timeoutTicks(request.height)
        var elapsedTicks = 0L
        lateinit var task: BukkitTask
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            elapsedTicks++
            val chunkLoaded = target.world
                ?.isChunkLoaded(target.blockX shr 4, target.blockZ shr 4) == true
            when (
                FlightMonitor.check(
                    valid = rocket.isValid,
                    detonated = rocket.isDetonated,
                    targetChunkLoaded = chunkLoaded,
                    currentY = rocket.location.y,
                    targetY = target.y,
                    elapsedTicks = elapsedTicks,
                    timeoutTicks = timeoutTicks,
                )
            ) {
                FlightCheck.WAIT -> Unit
                FlightCheck.DETONATE -> {
                    task.cancel()
                    // XZの残留ブレもここで消し、完全垂直の到達点で破裂させます。
                    runCatching {
                        rocket.teleport(target)
                        rocket.detonate()
                    }
                }
                FlightCheck.ABORT -> {
                    task.cancel()
                    runCatching { rocket.remove() }
                }
            }
        }, 1L, 1L)
        return true
    }
}
