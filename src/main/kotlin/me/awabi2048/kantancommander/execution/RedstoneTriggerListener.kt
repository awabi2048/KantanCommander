package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.TriggerKind
import me.awabi2048.kantancommander.model.DiskPlacement
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
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import java.util.UUID

class RedstoneTriggerListener(private val plugin: KantanCommanderPlugin) : Listener {
    private val runtimeState = RedstoneRuntimeState()
    private val wireTopology = RedstoneWireTopology(::isExtendedCommandBlock)
    private data class Registration(val placement: DiskPlacement, val kind: TriggerKind, val interval: Long)
    private val active = linkedMapOf<String, Registration>()
    private val timers = LoadedTimerState()

    fun start() {
        rebuild()
        // プラグイン再起動時も、保存済みの拡張ブロック隣接ダストだけを現行の接続仕様へ
        // 一度だけ補正してから監視を開始します。通常のダストはバニラへ任せます。
        Bukkit.getScheduler().runTask(plugin, Runnable {
            plugin.placements.all().forEach { placement ->
                val world = Bukkit.getWorld(placement.world) ?: return@forEach
                if (!world.isChunkLoaded(placement.x shr 4, placement.z shr 4)) return@forEach
                if (!neighborsLoaded(world, placement.x, placement.z, 3)) return@forEach
                wireTopology.refreshAround(world.getBlockAt(placement.x, placement.y, placement.z))
            }
        })
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::tick), 1L, 1L)
    }

    /** 設定変更時だけ起動方法を取得し、毎tickのグラフ複製を避けます。 */
    fun rebuild() {
        val fresh = linkedMapOf<String, Registration>()
        plugin.placements.all().forEach { placement ->
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            if (!world.isChunkLoaded(placement.x shr 4, placement.z shr 4)) return@forEach
            registration(placement)?.let { fresh[placement.key] = it }
        }
        (active.keys - fresh.keys).forEach(runtimeState::forgetPower)
        active.clear()
        active.putAll(fresh)
    }

    private fun registration(placement: DiskPlacement): Registration? {
        val trigger = plugin.scripts.load(placement.scriptId)?.trigger?.normalized() ?: return null
        if (trigger.kind == TriggerKind.EVENT) return null
        return Registration(placement, trigger.kind, trigger.intervalTicks())
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        plugin.placements.inChunk(event.world.name, event.chunk.x, event.chunk.z).forEach { placement ->
            registration(placement)?.let { active[placement.key] = it }
        }
        // 起動時や前回ロード時に隣接チャンク待ちで保留した接続補正も再試行します。
        // 対象チャンクのロード完了後に実行し、別チャンクを同期ロードしません。
        val world = event.world
        val chunkX = event.chunk.x
        val chunkZ = event.chunk.z
        val nearbyChunks = (chunkX - 1..chunkX + 1).flatMap { x ->
            (chunkZ - 1..chunkZ + 1).map { z -> x to z }
        }
        if (nearbyChunks.none { (x, z) -> plugin.placements.inChunk(world.name, x, z).isNotEmpty() }) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            for ((x, z) in nearbyChunks) {
                plugin.placements.inChunk(world.name, x, z).forEach { placement ->
                    if (neighborsLoaded(world, placement.x, placement.z, 3)) {
                        wireTopology.refreshAround(world.getBlockAt(placement.x, placement.y, placement.z))
                    }
                }
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        plugin.placements.inChunk(event.world.name, event.chunk.x, event.chunk.z).forEach { placement ->
            active.remove(placement.key)
            runtimeState.forgetPower(placement.key)
        }
    }

    private fun tick() {
        active.values.toList().forEach { registration ->
            val placement = registration.placement
            if (active[placement.key] != registration) return@forEach
            val world = Bukkit.getWorld(placement.world) ?: return@forEach
            if (!world.isChunkLoaded(placement.x shr 4, placement.z shr 4)) return@forEach
            val block = world.getBlockAt(placement.x, placement.y, placement.z)
            if (!PlacedBlockMaterials.isPlacedBlock(block.type)) {
                // コマンドや外部プラグインで実体だけが置き換えられた場合も、
                // 台帳に残った特殊接続を次の監視周期でバニラ形状へ戻します。
                if (neighborsLoaded(world, placement.x, placement.z, 3)) wireTopology.restoreAfterExtendedRemoval(block)
                return@forEach
            }
            val shouldRun = when (registration.kind) {
                TriggerKind.EVENT -> false
                TriggerKind.TIMER -> timers.advance(placement.key, registration.interval)
                TriggerKind.REDSTONE -> {
                    // 読めない面は前回値を保持し、利用可能な面は監視し続けます。
                    // 境界の1面のために他の面まで監視停止しないことが重要です。
                    var known = 0
                    var mask = 0
                    RedstoneInputReader.POWER_FACES.forEachIndexed { index, face ->
                        val neighbor = block.getRelative(face)
                        if (neighborsLoaded(world, neighbor.x, neighbor.z, 1)) {
                            known = known or (1 shl index)
                            if (neighbor.isBlockFacePowered(face)) mask = mask or (1 shl index)
                        }
                    }
                    runtimeState.observePartialPower(placement.key, mask, known)
                }
            }
            if (shouldRun) {
                val normalExecution = plugin.testExecution.beginNormal(placement.key) ?: return@forEach
                if (registration.kind == TriggerKind.TIMER) timers.markRun(placement.key)
                runCatching {
                    plugin.executor.execute(placement.scriptId, block.location.add(0.5, 0.5, 0.5)) {
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

    private fun neighborsLoaded(world: org.bukkit.World, x: Int, z: Int, radius: Int): Boolean =
        (((x - radius) shr 4)..((x + radius) shr 4)).all { chunkX ->
            (((z - radius) shr 4)..((z + radius) shr 4)).all { chunkZ -> world.isChunkLoaded(chunkX, chunkZ) }
        }

    fun resetTiming(scriptId: UUID) {
        plugin.placements.findByScript(scriptId).forEach { timers.forget(it.key) }
        runtimeState.resetTiming(scriptId)
    }

    fun forget(placementKey: String, scriptId: UUID) {
        timers.forget(placementKey)
        active.remove(placementKey)
        runtimeState.forget(placementKey, scriptId)
    }

    fun forgetScript(scriptId: UUID) {
        plugin.placements.findByScript(scriptId).forEach { forget(it.key, scriptId) }
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

    internal val POWER_FACES = listOf(
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
    private val knownMasks = mutableMapOf<String, Int>()
    private val lastRun = mutableMapOf<UUID, Long>()

    fun forgetPower(placementKey: String) {
        powered.remove(placementKey)
        masks.remove(placementKey)
        knownMasks.remove(placementKey)
    }

    /** 未観測面は保持し、読み取り可能になった瞬間の状態を立ち上がりに数えません。 */
    fun observePartialPower(placementKey: String, current: Int, known: Int): Boolean {
        val old = masks[placementKey] ?: 0
        val previouslyKnown = knownMasks[placementKey] ?: 0
        val newFaces = known and previouslyKnown.inv()
        val baseline = (old and newFaces.inv()) or (current and newFaces)
        val combined = (old and known.inv()) or (current and known)
        masks[placementKey] = combined
        knownMasks[placementKey] = known
        powered[placementKey] = combined != 0
        return baseline == 0 && combined != 0
    }

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
        forgetPower(placementKey)
        lastRun.remove(scriptId)
    }
}

/** ロード中に監視したtickだけを積算し、アンロード中の時間を含めません。 */
internal class LoadedTimerState {
    private val elapsed = mutableMapOf<String, Long>()

    fun advance(key: String, interval: Long): Boolean {
        val ticks = (elapsed[key] ?: 0L) + 1L
        elapsed[key] = ticks
        return ticks >= interval
    }

    fun markRun(key: String) { elapsed[key] = 0L }
    fun forget(key: String) { elapsed.remove(key) }
}

internal object RedstoneActivationPolicy {
    /** 信号の立ち上がりかを返します。初回観測は呼び出し側で除外します。 */
    fun isRisingEdge(wasPowered: Boolean, isPowered: Boolean): Boolean =
        !wasPowered && isPowered

    /** 定期実行の間隔が満ちたかを返します。 */
    fun isIntervalElapsed(intervalTicks: Long, currentTick: Long, lastRunTick: Long?): Boolean =
        lastRunTick != null && currentTick - lastRunTick >= intervalTicks
}
