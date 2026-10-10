package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.intervalTicks
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
    private val wireTopology = RedstoneWireTopology(::isExtendedCommandBlock)

    fun start() {
        // プラグイン再起動時も、保存済みの拡張ブロック隣接ダストだけを現行の接続仕様へ
        // 一度だけ補正してから監視を開始します。通常のダストはバニラへ任せます。
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.placements.all().forEach { placement ->
                val world = Bukkit.getWorld(placement.world) ?: return@forEach
                wireTopology.refreshAround(world.getBlockAt(placement.x, placement.y, placement.z))
            }
        })
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::tick), 1L, 1L)
    }

    private fun tick() {
        val now = plugin.server.currentTick.toLong()
        plugin.placements.all().forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            val block = world.getBlockAt(placement.x, placement.y, placement.z)
            if (!PlacedBlockMaterials.isPlacedBlock(block.type)) {
                // コマンドや外部プラグインで実体だけが置き換えられた場合も、
                // 台帳に残った特殊接続を次の監視周期でバニラ形状へ戻します。
                wireTopology.restoreAfterExtendedRemoval(block)
                return@forEach
            }
            val script = plugin.scripts.load(placement.scriptId) ?: return@forEach
            // 隣接ブロック自身の集約電力ではなく、対象ブロックへ入力された面を読む。
            // これによりリピーターの入力側など、出力方向でない信号を誤検知しません。
            val powerMask = RedstoneInputReader.incomingPowerMask(block)
            val hasPower = RedstoneInputReader.isPowered(powerMask)
            val previous = runtimeState.observePower(placement.key, powerMask)
            // issue #7: 起動方法は `trigger` が単一正本です。イベント起動は
            // イベントリスナーが担当するため tick では実行しません。
            val trigger = script.trigger.normalized()
            val shouldRun = when (trigger.kind) {
                TriggerKind.EVENT -> false
                TriggerKind.TIMER -> {
                    val anchor = runtimeState.timerAnchor(script.id, now)
                    RedstoneActivationPolicy.isIntervalElapsed(
                        intervalTicks = trigger.intervalTicks(),
                        currentTick = now,
                        lastRunTick = anchor,
                    )
                }
                TriggerKind.REDSTONE ->
                    RedstoneActivationPolicy.isRisingEdge(wasPowered = previous, isPowered = hasPower)
            }
            if (shouldRun) {
                val normalExecution = plugin.testExecution.beginNormal(placement.key) ?: return@forEach
                runtimeState.markRun(script.id, now)
                runCatching {
                    plugin.executor.execute(script.id, block.location.add(0.5, 0.5, 0.5)) {
                        normalExecution.close()
                    }
                }.onFailure { failure ->
                    // 実行開始前の例外でも配置単位の通常実行カウンタを残さないよう、
                    // テスト排他トークンを必ず解放します。
                    normalExecution.close()
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "通常実行の開始に失敗しました: placement=${placement.key}",
                        failure,
                    )
                }
            }
        }
    }

    fun resetTiming(scriptId: UUID) {
        runtimeState.resetTiming(scriptId)
    }

    fun forget(placementKey: String, scriptId: UUID) {
        runtimeState.forget(placementKey, scriptId)
    }

    /** 拡張ブロックに隣接するダストだけを、周囲のワールド状態から再計算します。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.block.type != Material.REDSTONE_WIRE && !isExtendedCommandBlock(event.block)) return
        wireTopology.refreshAround(event.block)
    }

    /** バニラの物理更新で接続が再計算された場合も、意図した形状を再適用します。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        if (event.block.type != Material.REDSTONE_WIRE && !isExtendedCommandBlock(event.block)) return
        wireTopology.refreshAround(event.block)
    }

    private fun isExtendedCommandBlock(block: Block): Boolean =
        PlacedBlockMaterials.isPlacedBlock(block.type) &&
            plugin.placements.isRegistered(block.world, block.x, block.y, block.z)

    /** 拡張ブロック撤去後など、イベント外から隣接ダストをバニラ形状へ戻します。 */
    internal fun refreshDustTopologyAround(block: Block) {
        wireTopology.restoreAfterExtendedRemoval(block)
    }

}

/**
 * 制御ブロックへ実際に入力されているレッドストーン信号を読む共通境界です。
 * 起動トリガーと「制御ブロックの状態」条件が異なる面判定を使うと、起動したのに
 * 条件だけ偽になるため、入力面の集約ロジックをここへ閉じ込めます。
 */
internal object RedstoneInputReader {
    fun isPowered(block: Block): Boolean = isPowered(incomingPowerMask(block))

    fun isPowered(powerMask: Int): Boolean = powerMask != 0

    fun incomingPowerMask(block: Block): Int = POWER_FACES.mapIndexedNotNull { index, face ->
        // 対象側のgetBlockPower(face)は現在のPaper実装でダスト以外の信号強度を
        // 取りこぼすため、隣接ブロック自身が対象方向へ出力しているかを調べます。
        block.getRelative(face).isBlockFacePowered(face).takeIf { it }?.let { 1 shl index }
    }.fold(0, Int::or)

    /**
     * 各入力面へ届いている信号強度の最大値（0..15）です。
     * 入力面の向きは [incomingPowerMask] と同じ解釈へ揃え、強度部分だけを
     * `getBlockPower` で読みます。条件判定側はこの実測強度を範囲指定と比較します。
     */
    fun incomingSignalStrength(block: Block): Int = POWER_FACES.maxOf { face ->
        block.getRelative(face).getBlockPower(face).coerceAtLeast(0)
    }

    private val POWER_FACES = listOf(
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST,
    )
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

    fun timerAnchor(scriptId: UUID, currentTick: Long): Long? =
        lastRun.getOrPut(scriptId) { currentTick }

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
    /** 信号の立ち上がりかを返します。初回観測は呼び出し側で除外します。 */
    fun isRisingEdge(wasPowered: Boolean, isPowered: Boolean): Boolean =
        !wasPowered && isPowered

    /** 定期実行の間隔が満ちたかを返します。 */
    fun isIntervalElapsed(intervalTicks: Long, currentTick: Long, lastRunTick: Long?): Boolean =
        lastRunTick != null && currentTick - lastRunTick >= intervalTicks
}
