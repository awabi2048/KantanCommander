package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.ActivationMode
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.UUID
import me.awabi2048.kantancommander.placement.PlacedBlockMaterials

class RedstoneTriggerListener(private val plugin: KantanCommanderPlugin) : Listener {
    private val runtimeState = RedstoneRuntimeState()

    fun start() {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::tick), 1L, 1L)
    }

    private fun tick() {
        val now = plugin.server.currentTick.toLong()
        plugin.placements.all().forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            val block = world.getBlockAt(placement.x, placement.y, placement.z)
            if (!PlacedBlockMaterials.isPlacedBlock(block.type)) return@forEach
            val script = plugin.scripts.load(placement.scriptId) ?: return@forEach
            val hasPower = POWER_FACES.any { face ->
                val adjacent = block.getRelative(face)
                adjacent.blockPower > 0 || adjacent.isBlockPowered || adjacent.isBlockIndirectlyPowered
            }
            val previous = runtimeState.observePower(placement.key, hasPower)
            val previousRun = runtimeState.timerAnchor(script.id, script.timer.enabled, now)
            val shouldRun = RedstoneActivationPolicy.shouldRun(
                activation = script.activation,
                timerEnabled = script.timer.enabled,
                intervalTicks = script.timer.intervalTicks,
                wasPowered = previous,
                isPowered = hasPower,
                currentTick = now,
                lastRunTick = previousRun,
            )
            if (shouldRun) {
                runtimeState.markRun(script.id, now)
                plugin.executor.execute(script.id, block.location.add(0.5, 0.5, 0.5))
            }
        }
    }

    fun resetTiming(scriptId: UUID) {
        runtimeState.resetTiming(scriptId)
    }

    fun forget(placementKey: String, scriptId: UUID) {
        runtimeState.forget(placementKey, scriptId)
    }

    companion object {
        private val POWER_FACES = listOf(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
        )
    }
}

internal class RedstoneRuntimeState {
    private val powered = mutableMapOf<String, Boolean>()
    private val lastRun = mutableMapOf<UUID, Long>()

    fun observePower(placementKey: String, current: Boolean): Boolean {
        // サーバー起動直後・再配置直後など初回観測時は、通電状態を記録するだけで
        // 立ち上がりとは数えない（前回値として現在値そのものを返す）。
        val previous = powered.put(placementKey, current)
        return previous ?: current
    }

    fun timerAnchor(scriptId: UUID, timerEnabled: Boolean, currentTick: Long): Long? {
        if (!timerEnabled) {
            lastRun.remove(scriptId)
            return null
        }
        return lastRun.getOrPut(scriptId) { currentTick }
    }

    fun markRun(scriptId: UUID, currentTick: Long) {
        lastRun[scriptId] = currentTick
    }

    fun resetTiming(scriptId: UUID) {
        lastRun.remove(scriptId)
    }

    fun forget(placementKey: String, scriptId: UUID) {
        powered.remove(placementKey)
        lastRun.remove(scriptId)
    }
}

internal object RedstoneActivationPolicy {
    fun shouldRun(
        activation: ActivationMode,
        timerEnabled: Boolean,
        intervalTicks: Long,
        wasPowered: Boolean,
        isPowered: Boolean,
        currentTick: Long,
        lastRunTick: Long?,
    ): Boolean {
        if (!timerEnabled) {
            return activation == ActivationMode.NEEDS_REDSTONE && !wasPowered && isPowered
        }
        // タイマー有効時は、レッドストーン信号の立ち上がりで即時実行して基準時刻を張り直し、
        // 以降は常時実行へ切り替えた場合と同じく、間隔経過だけで定期実行する。
        // 通電が切れている間も経過時間は進むため、再通電時に間隔が満ちていれば直ちに実行される。
        if (activation == ActivationMode.NEEDS_REDSTONE && !wasPowered && isPowered) return true
        val intervalElapsed = lastRunTick != null && currentTick - lastRunTick >= intervalTicks
        return intervalElapsed
    }
}
