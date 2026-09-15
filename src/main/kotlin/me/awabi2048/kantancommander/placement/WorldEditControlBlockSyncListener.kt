package me.awabi2048.kantancommander.placement

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import com.awabi2048.ccsystem.api.worldedit.WorldEditBlockPoint
import com.awabi2048.ccsystem.api.worldedit.WorldEditCapture
import com.awabi2048.ccsystem.api.worldedit.WorldEditClipboardOperation
import com.awabi2048.ccsystem.api.worldedit.WorldEditCommands
import com.awabi2048.ccsystem.api.worldedit.WorldEditMoveCommandParser
import com.awabi2048.ccsystem.api.worldedit.WorldEditMoveOffset
import com.awabi2048.ccsystem.api.worldedit.WorldEditOrigin
import com.awabi2048.ccsystem.api.worldedit.WorldEditReconciler
import com.awabi2048.ccsystem.api.worldedit.WorldEditRelativeEntry
import com.awabi2048.ccsystem.api.worldedit.WorldEditAvailability
import com.awabi2048.ccsystem.api.worldedit.WorldEditSelectionSupport
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * WorldEdit等で制御ブロックが移動した際の台帳同期です。
 *
 * `//copy`・`//cut`・`//paste`・`//move` はBukkitの通常ブロックイベントを
 * 通らないため、CC-SystemのWorldEdit支援（`PlayerCommandPreprocessEvent` 先読み・
 * 相対化・遅延確定）を使い、配置台帳・スクリプト・表示・起動索引を追従させます。
 * 対象外の一括操作は警告と周期的掃除へ委ね、自動追従しません。
 * 向き（`facing`）は貼付け時の回転・反転でも維持します。
 */
class WorldEditControlBlockSyncListener(
    private val plugin: KantanCommanderPlugin,
) : Listener {
    /**
     * 基準点からの相対差分で保持する配置項目です。
     *
     * `placementKey` は `"$world,$x,$y,$z"` 形式の台帳キーであり、移動元の
     * 特定に使います。`scriptId` と `facing` は移動先への再登録に使います。
     */
    private data class RelativePlacement(
        val placementKey: String,
        val scriptId: UUID,
        val facing: String,
        val relativeX: Int,
        val relativeY: Int,
        val relativeZ: Int,
    )

    private data class PendingClipboard(
        val mode: WorldEditClipboardOperation,
        val sourceWorldName: String,
        val origin: WorldEditOrigin,
        val placements: List<RelativePlacement>,
        var sourceRemoved: Boolean = false,
        var sameIdPasteConsumed: Boolean = false,
    )

    private data class PendingMove(
        val sourceWorldName: String,
        val worldKey: String,
        val origin: WorldEditOrigin,
        val offset: WorldEditMoveOffset,
        val placements: List<RelativePlacement>,
    )

    private val pendingClipboards = ConcurrentHashMap<UUID, PendingClipboard>()
    private val pendingMoves = ConcurrentHashMap<UUID, PendingMove>()
    private var sweepTaskId: Int = -1

    /**
     * 周期的ゴースト掃除を開始します。
     *
     * WorldEditの `//set air` 等で実体だけが消えた配置を、台帳・表示・
     * スクリプトごと整理します。登録はWorldEdit有効時のみ行います。
     */
    fun startSweep() {
        if (sweepTaskId != -1) return
        sweepTaskId = plugin.server.scheduler.runTaskTimer(plugin, Runnable { sweepOnce() }, SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS).taskId
    }

    fun stopSweep() {
        if (sweepTaskId == -1) return
        plugin.server.scheduler.cancelTask(sweepTaskId)
        sweepTaskId = -1
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldEditClipboardCommand(event: PlayerCommandPreprocessEvent) {
        val operation = WorldEditCommands.parseClipboardOperation(event.message) ?: return
        if (!WorldEditAvailability.isAvailable()) return
        captureClipboard(event.player, operation)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldEditPasteCommand(event: PlayerCommandPreprocessEvent) {
        if (!WorldEditCommands.isPasteCommand(event.message)) return
        val playerUuid = event.player.uniqueId
        Bukkit.getScheduler().runTask(plugin, Runnable {
            pastePendingPlacements(playerUuid)
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldEditMoveCommand(event: PlayerCommandPreprocessEvent) {
        val command = WorldEditMoveCommandParser.parse(event.message) ?: return
        if (!WorldEditAvailability.isAvailable()) return
        val offset = WorldEditSelectionSupport.resolveMoveOffset(command, event.player) ?: return
        if (offset.isZero()) return
        captureMove(event.player, offset)
    }

    /**
     * 対象外の一括操作を検出して警告します。
     *
     * WorldEdit式（`//`始まり）は選択範囲との包含で、バニラ式は引数座標の
     * 範囲解釈で脅威判定します。警告後は周期的掃除が不正な複製を整理します。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onUnsupportedBulkCommand(event: PlayerCommandPreprocessEvent) {
        if (!WorldEditCommands.isUnsupportedBulkCommand(event.message)) return
        val player = event.player
        val worldPlacements = plugin.placements.all().filter { it.world == player.world.name }
        if (worldPlacements.isEmpty()) return
        val threatened = if (event.message.trim().startsWith("//")) {
            if (!WorldEditAvailability.isAvailable()) return
            val capture = WorldEditSelectionSupport.capture(player, worldPlacements) { pointOf(player.world, it) }
                ?: return
            capture.entries.map { it.entry }
        } else {
            val boxes = VanillaBulkOperationParser.parse(
                event.message,
                player.location.blockX, player.location.blockY, player.location.blockZ,
            )
            // 座標を解釈できない場合（ローカル座標等）は、同一ワールド全体を警告対象にします。
            if (boxes == null) worldPlacements
            else worldPlacements.filter { placement -> boxes.any { it.contains(placement.x, placement.y, placement.z) } }
        }
        if (threatened.isEmpty()) return
        val commandName = WorldEditCommands.normalizedCommandName(event.message)
        player.sendMessage(
            KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WORLDEDIT_UNSUPPORTED_OPERATION,
                mapOf("command" to commandName),
            ),
        )
        plugin.logger.warning(
            "対象外の一括操作を検出しました: command=$commandName player=${player.name} " +
                "placements=${threatened.map { it.key }}",
        )
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        pendingClipboards.remove(event.player.uniqueId)
        pendingMoves.remove(event.player.uniqueId)
    }

    private fun captureClipboard(player: Player, mode: WorldEditClipboardOperation) {
        val worldPlacements = plugin.placements.all().filter { it.world == player.world.name }
        if (worldPlacements.isEmpty()) {
            pendingClipboards.remove(player.uniqueId)
            return
        }
        val capture = WorldEditSelectionSupport.capture(player, worldPlacements) { pointOf(player.world, it) }
            ?: return
        if (capture.entries.isEmpty()) {
            pendingClipboards.remove(player.uniqueId)
            return
        }
        pendingClipboards[player.uniqueId] = PendingClipboard(
            mode = mode,
            sourceWorldName = player.world.name,
            origin = capture.origin,
            placements = capture.entries.map { toRelativePlacement(it.entry, it.relativeX, it.relativeY, it.relativeZ) },
        )
        if (mode == WorldEditClipboardOperation.CUT) {
            // WorldEditのcutはBukkitのBlockBreakEventを通らないため、次のtickで移動元を消します。
            Bukkit.getScheduler().runTask(plugin, Runnable {
                removeCutSourcePlacements(player.uniqueId)
            })
        }
    }

    /**
     * `//cut` の移動元配置を消します。
     *
     * スクリプトは貼付け時の再登録に使うため削除しません。編集中の画面と
     * テスト実行だけを先に解放し、削除済み配置への遅延入力を防ぎます。
     */
    private fun removeCutSourcePlacements(playerUuid: UUID) {
        val pending = pendingClipboards[playerUuid] ?: return
        if (pending.mode != WorldEditClipboardOperation.CUT || pending.sourceRemoved) return
        val player = Bukkit.getPlayer(playerUuid) ?: return
        if (!WorldEditSelectionSupport.isClipboardOriginMatching(player, pending.origin)) return
        val world = Bukkit.getWorld(pending.sourceWorldName) ?: return
        for (relative in pending.placements) {
            val placement = findByKey(relative.placementKey) ?: continue
            releaseRuntimeState(placement)
            val removed = runCatching {
                plugin.placements.remove(world, placement.x, placement.y, placement.z)
            }.getOrNull() ?: continue
            plugin.placements.removeDisplay(world, removed.displayId)
        }
        pending.sourceRemoved = true
    }

    private fun pastePendingPlacements(playerUuid: UUID) {
        val pending = pendingClipboards[playerUuid] ?: return
        val player = Bukkit.getPlayer(playerUuid) ?: return
        val capture = WorldEditCapture(
            origin = pending.origin,
            entries = pending.placements.map {
                WorldEditRelativeEntry(it, it.relativeX, it.relativeY, it.relativeZ)
            },
        )
        val resolved = WorldEditSelectionSupport.resolvePastePoints(player, capture) ?: return
        val world = player.world
        val preserveFirstCutIds = pending.mode == WorldEditClipboardOperation.CUT && !pending.sameIdPasteConsumed
        for (pasted in resolved) {
            val relative = pasted.entry
            // 貼付け先ワールドが変わった場合は登録しません。
            if (pasted.worldKey != world.key.toString()) continue
            val targetBlock = world.getBlockAt(pasted.x, pasted.y, pasted.z)
            // 実体が配置素材でない場合は貼付けが成立していないため、台帳を変更しません。
            if (!PlacedBlockMaterials.isPlacedBlock(targetBlock.type)) continue
            val existing = plugin.placements.find(world, pasted.x, pasted.y, pasted.z)
            if (existing != null) {
                // 同一スクリプトが既に登録済みの場合は何もしません。
                if (existing.scriptId == relative.scriptId) continue
                removePlacementFully(world, existing)
            }
            if (preserveFirstCutIds) {
                // CUT初回は同一スクリプトを移し、facingは維持します。
                registerPlacement(world, pasted.x, pasted.y, pasted.z, relative.scriptId, relative.facing)
            } else {
                val source = plugin.scripts.load(relative.scriptId)
                if (source == null) {
                    plugin.logger.warning("貼付け元のスクリプトが無いため登録を見送りました: placement=${relative.placementKey}")
                    continue
                }
                val copied = runCatching { plugin.scripts.copyForPlacement(source) }.getOrNull()
                if (copied == null) {
                    plugin.logger.warning("貼付け用のスクリプト複製に失敗しました: placement=${relative.placementKey}")
                    continue
                }
                registerPlacement(world, pasted.x, pasted.y, pasted.z, copied.id, relative.facing)
            }
            plugin.refreshRedstoneTopologyAround(targetBlock)
        }
        if (preserveFirstCutIds) {
            pending.sameIdPasteConsumed = true
        }
    }

    private fun captureMove(player: Player, offset: WorldEditMoveOffset) {
        val worldPlacements = plugin.placements.all().filter { it.world == player.world.name }
        if (worldPlacements.isEmpty()) {
            pendingMoves.remove(player.uniqueId)
            return
        }
        val capture = WorldEditSelectionSupport.capture(player, worldPlacements) { pointOf(player.world, it) }
            ?: return
        if (capture.entries.isEmpty()) {
            pendingMoves.remove(player.uniqueId)
            return
        }
        val pending = PendingMove(
            sourceWorldName = player.world.name,
            worldKey = player.world.key.toString(),
            origin = capture.origin,
            offset = offset,
            placements = capture.entries.map { toRelativePlacement(it.entry, it.relativeX, it.relativeY, it.relativeZ) },
        )
        pendingMoves[player.uniqueId] = pending
        // PlayerCommandPreprocessEventはコマンド実行前に発生するため、実ブロックの
        // 移動が完了するまで再確認し、成功したものだけ台帳へ反映します。
        WorldEditReconciler.runUntilReady(
            plugin = plugin,
            isStillCurrent = { pendingMoves[player.uniqueId] === pending },
            isReady = { isMoveCompleted(pending) },
            onReady = { applyPendingMove(player.uniqueId, pending) },
            onTimeout = { attempts ->
                pendingMoves.remove(player.uniqueId, pending)
                plugin.logger.warning(
                    "[KantanCommander] //move の完了状態を確認できないまま同期期限に達しました: " +
                        "world=${pending.worldKey}, attempt=$attempts",
                )
            },
        )
    }

    /**
     * 移動完了条件です。
     *
     * 移動先だけが先に配置された途中状態で確定すると、移動元のUUIDを早く解放して
     * 重複登録を招くため、移動元の実体消去も完了条件に含めます。
     */
    private fun isMoveCompleted(pending: PendingMove): Boolean {
        val world = Bukkit.getWorld(pending.sourceWorldName) ?: return false
        return pending.placements.all { relative ->
            val source = sourcePoint(pending, relative)
            val target = targetPoint(pending, relative)
            !PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(source.first, source.second, source.third).type) &&
                PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(target.first, target.second, target.third).type)
        }
    }

    private fun applyPendingMove(playerUuid: UUID, pending: PendingMove) {
        if (pendingMoves[playerUuid] !== pending) return
        pendingMoves.remove(playerUuid, pending)
        val world = Bukkit.getWorld(pending.sourceWorldName) ?: return
        for (relative in pending.placements) {
            val source = sourcePoint(pending, relative)
            val target = targetPoint(pending, relative)
            val sourceStillPlaced = PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(source.first, source.second, source.third).type)
            val targetPlaced = PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(target.first, target.second, target.third).type)
            if (targetPlaced) {
                val existing = plugin.placements.find(world, target.first, target.second, target.third)
                if (existing != null) {
                    if (existing.scriptId == relative.scriptId) continue
                    removePlacementFully(world, existing)
                }
                // 移動元の実体が消えていれば同じスクリプトを移し、残っていれば
                // コピー相当として新しいスクリプトを発行します。facingは維持します。
                if (!sourceStillPlaced) {
                    val moved = findByKey(relative.placementKey)
                    if (moved != null) {
                        releaseRuntimeState(moved)
                        runCatching {
                            plugin.placements.remove(world, moved.x, moved.y, moved.z)
                        }.getOrNull()?.let { plugin.placements.removeDisplay(world, it.displayId) }
                    }
                    registerPlacement(world, target.first, target.second, target.third, relative.scriptId, relative.facing)
                } else {
                    val sourceScript = plugin.scripts.load(relative.scriptId)
                    if (sourceScript == null) {
                        plugin.logger.warning("移動元のスクリプトが無いため登録を見送りました: placement=${relative.placementKey}")
                        continue
                    }
                    val copied = runCatching { plugin.scripts.copyForPlacement(sourceScript) }.getOrNull()
                    if (copied == null) {
                        plugin.logger.warning("移動用のスクリプト複製に失敗しました: placement=${relative.placementKey}")
                        continue
                    }
                    registerPlacement(world, target.first, target.second, target.third, copied.id, relative.facing)
                }
                plugin.refreshRedstoneTopologyAround(world.getBlockAt(target.first, target.second, target.third))
                plugin.refreshRedstoneTopologyAround(world.getBlockAt(source.first, source.second, source.third))
            } else if (!sourceStillPlaced) {
                // 移動元も移動先も実体でない場合は消去として扱い、残った台帳を掃除します。
                findByKey(relative.placementKey)?.let { removePlacementFully(world, it) }
            }
        }
    }

    /**
     * 実体だけが消えた配置を整理します。
     *
     * 台帳・表示・スクリプトをまとめて削除し、起動索引と隣接ダスト形状も
     * 戻します。ワールド未読込時は対象外とします。
     */
    private fun sweepOnce() {
        for (placement in plugin.placements.all()) {
            val world = Bukkit.getWorld(placement.world) ?: continue
            if (PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(placement.x, placement.y, placement.z).type)) continue
            runCatching {
                removePlacementFully(world, placement)
            }.onFailure { failure ->
                plugin.logger.log(
                    Level.WARNING,
                    "消失した配置の掃除に失敗しました: placement=${placement.key}",
                    failure,
                )
            }
        }
    }

    /**
     * 配置を台帳・表示・スクリプトごと完全に除去します。
     *
     * 編集中の画面とテスト実行を先に解放し、削除済みスクリプトへの遅延入力や
     * 実行セッションの残存を防ぎます。
     */
    private fun removePlacementFully(world: World, placement: DiskPlacement) {
        releaseRuntimeState(placement)
        val removed = runCatching {
            plugin.placements.remove(world, placement.x, placement.y, placement.z)
        }.getOrElse { failure ->
            plugin.logger.log(
                Level.WARNING,
                "配置台帳の削除に失敗しました: placement=${placement.key}",
                failure,
            )
            return
        } ?: return
        plugin.placements.removeDisplay(world, removed.displayId)
        plugin.refreshRedstoneTopologyAround(world.getBlockAt(placement.x, placement.y, placement.z))
        runCatching { plugin.scripts.delete(removed.scriptId) }
            .onFailure { failure ->
                plugin.logger.log(
                    Level.WARNING,
                    "配置除去後のスクリプト削除に失敗しました: script=${removed.scriptId}, placement=${placement.key}",
                    failure,
                )
            }
    }

    private fun registerPlacement(world: World, x: Int, y: Int, z: Int, scriptId: UUID, facing: String) {
        val placement = DiskPlacement(world.name, x, y, z, scriptId, facing, null)
        runCatching {
            plugin.placements.add(placement)
            plugin.placements.spawnDisplay(world, placement)
        }.onFailure { failure ->
            runCatching { plugin.placements.remove(world, x, y, z) }
            plugin.logger.log(
                Level.WARNING,
                "同期した配置の登録に失敗しました: placement=${keyOf(world.name, x, y, z)}",
                failure,
            )
        }
    }

    /** テスト実行と編集中画面を解放し、削除済み配置への遅延到達を防ぎます。 */
    private fun releaseRuntimeState(placement: DiskPlacement) {
        plugin.testExecution.cancel(placement.key, showResult = false, reason = "placement_removed")
        plugin.gestureEditor.closeForPlacement(placement)
    }

    private fun findByKey(placementKey: String): DiskPlacement? =
        plugin.placements.all().firstOrNull { it.key == placementKey }

    private fun pointOf(world: World, placement: DiskPlacement): WorldEditBlockPoint =
        WorldEditBlockPoint(world.key.toString(), placement.x, placement.y, placement.z)

    private fun toRelativePlacement(entry: DiskPlacement, relativeX: Int, relativeY: Int, relativeZ: Int): RelativePlacement =
        RelativePlacement(entry.key, entry.scriptId, entry.facing, relativeX, relativeY, relativeZ)

    /** 保留時の相対差分へ基準点を加算し、移動元の絶対座標へ戻します。 */
    private fun sourcePoint(pending: PendingMove, relative: RelativePlacement): Triple<Int, Int, Int> =
        Triple(
            pending.origin.x + relative.relativeX,
            pending.origin.y + relative.relativeY,
            pending.origin.z + relative.relativeZ,
        )

    private fun targetPoint(pending: PendingMove, relative: RelativePlacement): Triple<Int, Int, Int> =
        Triple(
            relative.relativeX + pending.offset.x,
            relative.relativeY + pending.offset.y,
            relative.relativeZ + pending.offset.z,
        )

    private fun keyOf(worldName: String, x: Int, y: Int, z: Int): String = "$worldName,$x,$y,$z"

    companion object {
        /** ゴースト掃除の間隔です。配置の増減は稀なため、5秒毎の照合で十分です。 */
        const val SWEEP_INTERVAL_TICKS = 100L
    }
}
