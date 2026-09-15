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
import org.bukkit.Material
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
     * `sourceWasGlass` は確保時に移動元が配置素材だったかを表し、既に drift
     * している台帳残存を移動判定から除外するために使います。
     */
    private data class RelativePlacement(
        val placementKey: String,
        val scriptId: UUID,
        val facing: String,
        val relativeX: Int,
        val relativeY: Int,
        val relativeZ: Int,
        val sourceWasGlass: Boolean,
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
        val rawCommand: String,
        val minHeight: Int,
        val maxHeight: Int,
        val snapshot: MoveGlassSnapshot,
        val placements: List<RelativePlacement>,
    )

    /**
     * 確保時点の周辺ガラス配置です。
     *
     * 予測移動先の不一致（方向解釈の差異等）に備え、移動元周辺の配置素材の
     * 有無だけを保持します。確保後に非ガラス→ガラスへ遷移した台帳未登録セルを
     * 実際の移動先として採用し、既存のずれガラスを誤採用しません。
     */
    private data class MoveGlassSnapshot(
        val halfEdge: Int,
        val glass: Map<Triple<Int, Int, Int>, Boolean>,
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
        captureMove(event.player, offset, event.message)
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
            placements = capture.entries.map {
                val absolute = Triple(capture.origin.x + it.relativeX, capture.origin.y + it.relativeY, capture.origin.z + it.relativeZ)
                toRelativePlacement(
                    it.entry, it.relativeX, it.relativeY, it.relativeZ,
                    player.world.getBlockAt(absolute.first, absolute.second, absolute.third).type,
                )
            },
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

    private fun captureMove(player: Player, offset: WorldEditMoveOffset, rawCommand: String) {
        val world = player.world
        val worldPlacements = plugin.placements.all().filter { it.world == world.name }
        if (worldPlacements.isEmpty()) {
            pendingMoves.remove(player.uniqueId)
            return
        }
        val capture = WorldEditSelectionSupport.capture(player, worldPlacements) { pointOf(world, it) }
            ?: return
        if (capture.entries.isEmpty()) {
            pendingMoves.remove(player.uniqueId)
            return
        }
        val halfEdge = moveSearchHalfEdge(offset)
        val relatives = capture.entries.map {
            val absolute = Triple(capture.origin.x + it.relativeX, capture.origin.y + it.relativeY, capture.origin.z + it.relativeZ)
            toRelativePlacement(
                it.entry,
                it.relativeX, it.relativeY, it.relativeZ,
                world.getBlockAt(absolute.first, absolute.second, absolute.third).type,
            )
        }
        if (relatives.none { it.sourceWasGlass }) {
            // 確保時点で実体がない項目だけの場合は、移動追跡せず掃除へ委ねます。
            pendingMoves.remove(player.uniqueId)
            plugin.logger.fine(
                "[KantanCommander] //move の対象に実体がないため追跡しません: command=$rawCommand",
            )
            return
        }
        val pending = PendingMove(
            sourceWorldName = world.name,
            worldKey = world.key.toString(),
            origin = capture.origin,
            offset = offset,
            rawCommand = rawCommand,
            minHeight = world.minHeight,
            maxHeight = world.maxHeight,
            snapshot = snapshotAround(world, relatives.map { sourcePointOf(capture.origin, it) }, halfEdge),
            placements = relatives,
        )
        pendingMoves[player.uniqueId] = pending
        plugin.logger.info(
            "[KantanCommander] //move を追跡します: player=${player.name} command=$rawCommand " +
                "offset=(${offset.x},${offset.y},${offset.z}) " +
                "sources=${relatives.map { sourcePointOf(capture.origin, it).toKeyString() }}",
        )
        // PlayerCommandPreprocessEventはコマンド実行前に発生するため、実ブロックの
        // 移動が完了するまで再確認し、成功したものだけ台帳へ反映します。
        // FAWEの低速編集に備えて試行上限を既定より延ばします。
        WorldEditReconciler.runUntilReady(
            plugin = plugin,
            isStillCurrent = { pendingMoves[player.uniqueId] === pending },
            isReady = { isMoveCompleted(pending) },
            onReady = {
                pendingMoves.remove(player.uniqueId, pending)
                applyPendingMove(pending, pending.offset)
            },
            onTimeout = { attempts -> resolveMoveTimeout(player.uniqueId, pending, attempts) },
            maxAttempts = MOVE_MAX_ATTEMPTS,
        )
    }

    /**
     * 移動完了条件（高速 path）です。
     *
     * 移動先だけが先に配置された途中状態で確定すると、移動元のUUIDを早く解放して
     * 重複登録を招くため、移動元の実体消去も完了条件に含めます。確保時に既に
     * 実体がなかった項目は判定から除外し、掃除へ委ねます。
     */
    private fun isMoveCompleted(pending: PendingMove): Boolean {
        val world = Bukkit.getWorld(pending.sourceWorldName) ?: return false
        return pending.placements.filter { it.sourceWasGlass }.all { relative ->
            val source = sourcePoint(pending, relative)
            val target = targetPoint(source, pending.offset)
            !PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(source.first, source.second, source.third).type) &&
                PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(target.first, target.second, target.third).type)
        }
    }

    /**
     * 試行期限時の分類と証拠 fallback です。
     *
     * 移動失敗（移動元が残存し遷移なし）は警告なく破棄します。予測と異なる
     * 変位で移動した場合は、確保後のガラス遷移から実測変位を求めて適用します。
     * いずれでもない不定状態だけを座標・材質付きで警告します。
     */
    private fun resolveMoveTimeout(playerUuid: UUID, pending: PendingMove, attempts: Int) {
        if (pendingMoves[playerUuid] !== pending) return
        pendingMoves.remove(playerUuid, pending)
        val world = Bukkit.getWorld(pending.sourceWorldName)
        if (world == null) {
            plugin.logger.warning(
                "[KantanCommander] //move の確定時にワールドがありません: " +
                    "command=${pending.rawCommand} world=${pending.worldKey}, attempt=$attempts",
            )
            return
        }
        val live = pending.placements.filter { it.sourceWasGlass }
        if (live.isEmpty()) {
            plugin.logger.fine(
                "[KantanCommander] //move の対象は既に実体がなく掃除へ委ねます: command=${pending.rawCommand}",
            )
            return
        }
        val sourcesIntact = live.all { relative ->
            val source = sourcePoint(pending, relative)
            PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(source.first, source.second, source.third).type)
        }
        val actual = findActualTranslation(world, pending, live)
        if (actual != null) {
            plugin.logger.info(
                "[KantanCommander] //move を実測変位で適用します: command=${pending.rawCommand} " +
                    "predicted=(${pending.offset.x},${pending.offset.y},${pending.offset.z}) " +
                    "actual=(${actual.x},${actual.y},${actual.z})",
            )
            applyPendingMove(pending, actual)
            return
        }
        if (sourcesIntact) {
            // 移動元が残り遷移もない場合は、移動自体が不成立のため警告しません。
            plugin.logger.fine(
                "[KantanCommander] //move は不成立のため破棄します: command=${pending.rawCommand}",
            )
            return
        }
        plugin.logger.warning(
            "[KantanCommander] //move の完了状態を確認できないまま同期期限に達しました: " +
                "command=${pending.rawCommand} world=${pending.worldKey}, attempt=$attempts " +
                live.joinToString(" ") { relative ->
                    val source = sourcePoint(pending, relative)
                    val target = targetPoint(source, pending.offset)
                    val sourceType = world.getBlockAt(source.first, source.second, source.third).type
                    val targetType = world.getBlockAt(target.first, target.second, target.third).type
                    "src=${source.toKeyString()}:$sourceType dst=${target.toKeyString()}:$targetType"
                },
        )
    }

    /**
     * 台帳の移動確定です。
     *
     * [offset] には予測変位または実測変位を渡します。移動元の実体が消えて
     * いれば同じスクリプトを移し、残っていればコピー相当として新しい
     * スクリプトを発行します。facingは維持します。
     */
    private fun applyPendingMove(pending: PendingMove, offset: WorldEditMoveOffset) {
        // 呼び出し側で保留の同一性を確認・除去済みのため、ここでは再確認しません。
        val world = Bukkit.getWorld(pending.sourceWorldName) ?: return
        for (relative in pending.placements) {
            if (!relative.sourceWasGlass) continue
            val source = sourcePoint(pending, relative)
            val target = targetPoint(source, offset)
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
     * 確保後のガラス遷移から実測変位を求めます。
     *
     * 方向解釈の差異等で予測が外れた場合に備え、移動元周辺で「確保時に非ガラス→
     * 現在ガラス」へ遷移した台帳未登録セルを探します。全項目に単一の平行移動が
     * 成立する場合だけ変位を返し、曖昧な場合は null とします。
     */
    private fun findActualTranslation(
        world: World,
        pending: PendingMove,
        live: List<RelativePlacement>,
    ): WorldEditMoveOffset? {
        val registered = plugin.placements.all()
            .filter { it.world == pending.sourceWorldName }
            .map { Triple(it.x, it.y, it.z) }
            .toSet()
        val perItem = live.map { relative ->
            val source = sourcePoint(pending, relative)
            candidatesAround(world, pending, source)
                .filter { it !in registered }
                .map { candidate ->
                    WorldEditMoveOffset(
                        candidate.first - source.first,
                        candidate.second - source.second,
                        candidate.third - source.third,
                    )
                }
                .toSet()
        }
        if (perItem.any { it.isEmpty() }) return null
        if (live.size == 1) {
            // 単一項目で候補が複数の場合は誤採用を避けて不定とします。
            return perItem.single().singleOrNull()
        }
        val common = perItem.reduce { acc, set -> acc.intersect(set) }
        return common.singleOrNull()
    }

    /** 確保後にガラスへ遷移した周辺セルを返します。 */
    private fun candidatesAround(
        world: World,
        pending: PendingMove,
        source: Triple<Int, Int, Int>,
    ): List<Triple<Int, Int, Int>> {
        val halfEdge = pending.snapshot.halfEdge
        val found = mutableListOf<Triple<Int, Int, Int>>()
        for (dx in -halfEdge..halfEdge) {
            for (dy in -halfEdge..halfEdge) {
                for (dz in -halfEdge..halfEdge) {
                    val cell = Triple(source.first + dx, source.second + dy, source.third + dz)
                    if (cell.second < pending.minHeight || cell.second > pending.maxHeight) continue
                    if (pending.snapshot.glass[cell] != false) continue
                    if (!PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(cell.first, cell.second, cell.third).type)) continue
                    found.add(cell)
                }
            }
        }
        return found
    }

    /** 確保時点の周辺ガラス配置を記録します。 */
    private fun snapshotAround(
        world: World,
        sources: List<Triple<Int, Int, Int>>,
        halfEdge: Int,
    ): MoveGlassSnapshot {
        val glass = linkedMapOf<Triple<Int, Int, Int>, Boolean>()
        for (source in sources) {
            for (dx in -halfEdge..halfEdge) {
                for (dy in -halfEdge..halfEdge) {
                    for (dz in -halfEdge..halfEdge) {
                        val cell = Triple(source.first + dx, source.second + dy, source.third + dz)
                        if (cell.second < world.minHeight || cell.second > world.maxHeight) continue
                        glass.getOrPut(cell) {
                            PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(cell.first, cell.second, cell.third).type)
                        }
                    }
                }
            }
        }
        return MoveGlassSnapshot(halfEdge, glass)
    }

    /** 探索半辺です。変位量に余裕を持たせ、上限で主スレッド負荷を抑えます。 */
    private fun moveSearchHalfEdge(offset: WorldEditMoveOffset): Int =
        maxOf(kotlin.math.abs(offset.x), kotlin.math.abs(offset.y), kotlin.math.abs(offset.z), FALLBACK_HALF_EDGE_MIN)
            .coerceAtMost(FALLBACK_HALF_EDGE_MAX)

    /**
     * 実体だけが消えた配置を整理します。
     *
     * 台帳・表示・スクリプトをまとめて削除し、起動索引と隣接ダスト形状も
     * 戻します。ワールド未読込時は対象外とします。処理中の移動・複写の
     * 移動元は除外し、低速編集中のスクリプト消失を防ぎます。
     */
    private fun sweepOnce() {
        for (placement in plugin.placements.all()) {
            if (isMoveInFlight(placement.key)) continue
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
     * 処理中の移動・複写の移動元かを返します。
     *
     * 低速な外部編集中に掃除が移動元配置を消すと、スクリプトが失われて
     * 確定時に追従できなくなるため、掃除対象から除外します。
     */
    private fun isMoveInFlight(placementKey: String): Boolean =
        pendingMoves.values.any { pending -> pending.placements.any { it.placementKey == placementKey } } ||
            pendingClipboards.values.any { pending -> pending.placements.any { it.placementKey == placementKey } }

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

    private fun toRelativePlacement(
        entry: DiskPlacement,
        relativeX: Int,
        relativeY: Int,
        relativeZ: Int,
        sourceMaterial: Material,
    ): RelativePlacement =
        RelativePlacement(
            entry.key, entry.scriptId, entry.facing,
            relativeX, relativeY, relativeZ,
            PlacedBlockMaterials.isPlacedBlock(sourceMaterial),
        )

    /** 保留時の相対差分へ基準点を加算し、移動元の絶対座標へ戻します。 */
    private fun sourcePoint(pending: PendingMove, relative: RelativePlacement): Triple<Int, Int, Int> =
        sourcePointOf(pending.origin, relative)

    private fun sourcePointOf(origin: WorldEditOrigin, relative: RelativePlacement): Triple<Int, Int, Int> =
        Triple(
            origin.x + relative.relativeX,
            origin.y + relative.relativeY,
            origin.z + relative.relativeZ,
        )

    /**
     * 移動先の絶対座標です。
     *
     * 移動元絶対座標へ変位を加算します。相対差分へ直接加算すると基準点が
     * 欠落し、予測先がずれて完了検出できなくなるため、必ずこの経路を使います。
     */
    private fun targetPoint(source: Triple<Int, Int, Int>, offset: WorldEditMoveOffset): Triple<Int, Int, Int> =
        Triple(
            source.first + offset.x,
            source.second + offset.y,
            source.third + offset.z,
        )

    private fun Triple<Int, Int, Int>.toKeyString(): String = "$first,$second,$third"

    private fun keyOf(worldName: String, x: Int, y: Int, z: Int): String = "$worldName,$x,$y,$z"

    companion object {
        /** ゴースト掃除の間隔です。配置の増減は稀なため、5秒毎の照合で十分です。 */
        const val SWEEP_INTERVAL_TICKS = 100L

        /** 低速な外部編集に備えた移動確定の試行上限です。 */
        const val MOVE_MAX_ATTEMPTS = 100

        /** 実測変位探索の最小半辺です。 */
        const val FALLBACK_HALF_EDGE_MIN = 8

        /** 実測変位探索の最大半辺です。主スレッド負荷を抑える上限です。 */
        const val FALLBACK_HALF_EDGE_MAX = 12
    }
}
