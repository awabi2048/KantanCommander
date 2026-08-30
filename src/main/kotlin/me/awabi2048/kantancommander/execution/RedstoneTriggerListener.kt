package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.placement.PlacedBlockMaterials
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.Block
import org.bukkit.block.data.type.RedstoneWire
import org.bukkit.event.EventPriority
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import java.util.UUID

class RedstoneTriggerListener(private val plugin: KantanCommanderPlugin) : Listener {
    private val runtimeState = RedstoneRuntimeState()

    fun start() {
        // プラグイン再起動時も、保存済みの側面ダストを現行の接続仕様へ
        // 一度だけ補正してから監視を開始します。以後の変更は物理更新イベントで追従します。
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.placements.all().forEach { placement ->
                val world = Bukkit.getWorld(placement.world) ?: return@forEach
                enforceDustConnections(world.getBlockAt(placement.x, placement.y, placement.z))
            }
        })
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::tick), 1L, 1L)
    }

    private fun tick() {
        val now = plugin.server.currentTick.toLong()
        plugin.placements.all().forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            val block = world.getBlockAt(placement.x, placement.y, placement.z)
            if (!PlacedBlockMaterials.isPlacedBlock(block.type)) return@forEach
            val script = plugin.scripts.load(placement.scriptId) ?: return@forEach
            // 隣接ブロック自身の集約電力ではなく、対象ブロックへ入力された面を読む。
            // これによりリピーターの入力側など、出力方向でない信号を誤検知しません。
            val powerMask = incomingPowerMask(block)
            val hasPower = powerMask != 0
            val previous = runtimeState.observePower(placement.key, powerMask)
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

    /**
     * 拡張ブロックの側面へ置かれたダストの接続状態を、意図したワールド形状へ補正します。
     * 対象は同じ高さの水平4面だけで、上下の面には手を入れません。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        enforceTopologyAround(event.block)
    }

    /** バニラの物理更新で接続が再計算された場合も、意図した形状を再適用します。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        enforceTopologyAround(event.block)
    }

    private fun enforceTopologyAround(changed: Block) {
        val candidates = buildList {
            if (PlacedBlockMaterials.isPlacedBlock(changed.type)) add(changed)
            if (changed.type == Material.REDSTONE_WIRE) {
                HORIZONTAL_FACES.mapNotNullTo(this) { face ->
                    changed.getRelative(face).takeIf { PlacedBlockMaterials.isPlacedBlock(it.type) }
                }
            }
        }.distinctBy { "${it.world.uid}:${it.x}:${it.y}:${it.z}" }
        candidates.forEach(::enforceDustConnections)
    }

    private fun enforceDustConnections(target: Block) {
        HORIZONTAL_FACES.forEach { face ->
            val dust = target.getRelative(face)
            val wire = dust.blockData as? RedstoneWire ?: return@forEach
            val towardTarget = face.oppositeFace
            if (wire.getFace(towardTarget) == RedstoneWire.Connection.SIDE) return@forEach
            val corrected = wire.clone() as RedstoneWire
            corrected.setFace(towardTarget, RedstoneWire.Connection.SIDE)
            // 接続プロパティの変更自体が要件です。バニラの物理更新を発生させず、
            // このイベント経路の冪等な再適用で形状を確定します。
            dust.setBlockData(corrected, false)
        }
    }

    private fun incomingPowerMask(block: Block): Int = POWER_FACES.mapIndexedNotNull { index, face ->
        block.getBlockPower(face).takeIf { it > 0 }?.let { 1 shl index }
    }.fold(0, Int::or)

    companion object {
        private val POWER_FACES = listOf(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
        )
        private val HORIZONTAL_FACES = listOf(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
        )
    }
}

internal class RedstoneRuntimeState {
    private val powered = mutableMapOf<String, Boolean>()
    private val masks = mutableMapOf<String, Int>()
    private val lastRun = mutableMapOf<UUID, Long>()

    fun observePower(placementKey: String, current: Boolean): Boolean {
        return observePower(placementKey, if (current) 1 else 0)
    }

    fun observePower(placementKey: String, currentMask: Int): Boolean {
        require(currentMask >= 0) { "redstone power mask must not be negative" }
        masks[placementKey] = currentMask
        val current = currentMask != 0
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
        masks.remove(placementKey)
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
