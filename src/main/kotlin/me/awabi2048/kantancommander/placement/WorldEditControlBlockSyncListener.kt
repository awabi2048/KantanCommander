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
 * `//undo`・`//redo` は専用イベントを持たないため、実行前台帳の
 * スナップショットと確定後の世界状態を照合し、単一平行移動で結ばれる
 * 場合だけ台帳を追従させます。確定できない場合は掃除を猶予します。
 */
class WorldEditControlBlockSyncListener(
    private val plugin: KantanCommanderPlugin,
) : Listener {
    private data class PendingClipboard(
        val mode: WorldEditClipboardOperation,
        val sourceWorldName: String,
        val origin: WorldEditOrigin,
        val placements: List<RelativePlacement>,
        var sourceRemoved: Boolean = false,
        var sameIdPasteConsumed: Boolean = false,
        /**
         * issue #review P1-2: 切り取り確定済みの移動元台帳キー集合です。
         *
         * 失敗した切り取りでも原点一致だけで削除へ進むと、実ブロック残存なのに
         * 台帳・表示・起動登録が解除されるため、項目別に実変化を確認してから
         * 確定します。貼付け側は未確定項目の同一ID再利用を行いません。
         */
        val removedKeys: MutableSet<String> = mutableSetOf(),
        /**
         * issue #review P1-3/P2-4: 貼付け確定済みの貼付け先台帳キー集合です。
         *
         * 再貼付けの冪等性判定に使い、登録成功後にのみ追加します。
         * 失敗した貼り付けで立てないことで、消失と重複登録を防ぎます。
         */
        val pastedKeys: MutableSet<String> = mutableSetOf(),
        /**
         * issue #review P1-3: 貼付けコマンド時点の貼付け先材質スナップショットです。
         *
         * 今回の貼り付けによる変更を確認できない既存配置は保持するため、
         * 確定直前の比較基準として使います。キーは貼付け先の絶対座標です。
         */
        var pasteSnapshot: Map<Triple<Int, Int, Int>, Material>? = null,
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

    private data class MoveGlassSnapshot(
        val halfEdge: Int,
        val glass: Map<Triple<Int, Int, Int>, Boolean>,
    )

    /**
     * undo／redo 実行前の台帳項目です。
     *
     * WorldEditの履歴APIへ触れず、実行前後の差分だけで追従するため、
     * 確定時の照合基準として保持します。
     */
    private data class UndoLedgerEntry(
        val placementKey: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val scriptId: UUID,
        val facing: String,
    )

    /** undo／redo の追跡保留です。確定まで台帳スナップショットを保持します。 */
    private data class PendingUndo(
        val worldName: String,
        val rawCommand: String,
        val minHeight: Int,
        val maxHeight: Int,
        val entries: List<UndoLedgerEntry>,
        var lastOutcome: UndoOutcome? = null,
    )

    /** undo／redo 確定時の照合結果です。確定系だけ安定確認の対象にします。 */
    private sealed interface UndoOutcome {
        data object Noop : UndoOutcome
        data class Accept(
            val translation: WorldEditMoveOffset,
            val pairs: List<Pair<UndoLedgerEntry, Triple<Int, Int, Int>>>,
        ) : UndoOutcome
        data object Ambiguous : UndoOutcome
    }

    private val pendingClipboards = ConcurrentHashMap<UUID, PendingClipboard>()
    private val pendingMoves = ConcurrentHashMap<UUID, PendingMove>()
    private val pendingUndos = ConcurrentHashMap<UUID, PendingUndo>()

    /**
     * 掃除猶予中の台帳キーと猶予期限（epoch millis）です。
     *
     * undo／redo の照合が確定しない項目を周期的掃除から除外し、
     * プログラム消失を防ぎます。期限切れは掃除時に破棄します。
     */
    private val undoGrace = ConcurrentHashMap<String, Long>()
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
        val pending = pendingClipboards[playerUuid] ?: return
        // issue #review P2-4: 貼付けコマンド時点の材質を記録し、実編集が
        // 完了するまで再確認します。FAWEの低速編集に備えて試行上限を延ばします。
        snapshotPastePoints(event.player, pending)
        WorldEditReconciler.runUntilReady(
            plugin = plugin,
            isStillCurrent = { pendingClipboards[playerUuid] === pending },
            isReady = { isPasteCompleted(playerUuid, pending) },
            onReady = {
                if (pendingClipboards[playerUuid] === pending) {
                    pastePendingPlacements(playerUuid)
                }
            },
            onTimeout = { attempts ->
                plugin.logger.warning(
                    "[KantanCommander] //paste の確定を期限切れで見送りました: " +
                        "player=${event.player.name} attempt=$attempts",
                )
                // 期限後も登録可能な項目だけは確定し、可能な保存を失いません。
                // フラグは実際に登録した項目にだけ立てます。
                if (pendingClipboards[playerUuid] === pending) {
                    pastePendingPlacements(playerUuid)
                }
            },
            maxAttempts = MOVE_MAX_ATTEMPTS,
        )
    }

    /**
     * issue #review P1-3/P2-4: 貼付けコマンド時点の材質スナップショットです。
     *
     * 確定時に今回の貼り付けによる変更か（非配置→配置への遷移か）を判定し、
     * 変更を確認できない既存配置の置換を防ぎます。
     */
    private fun snapshotPastePoints(player: Player, pending: PendingClipboard) {
        val world = player.world
        val capture = WorldEditCapture(
            origin = pending.origin,
            entries = pending.placements.map {
                WorldEditRelativeEntry(it, it.relativeX, it.relativeY, it.relativeZ)
            },
        )
        val resolved = WorldEditSelectionSupport.resolvePastePoints(player, capture) ?: return
        pending.pasteSnapshot = resolved
            .filter { it.worldKey == world.key.toString() }
            .associate { Triple(it.x, it.y, it.z) to world.getBlockAt(it.x, it.y, it.z).type }
    }

    /**
     * issue #review P2-4: 貼付け完了条件です。
     *
     * 全貼付け先が「正しく登録済み」または「今回の変更で登録可能」の場合に
     * 確定します。FAWEの低速編集中は成立せず、再確認を続けます。
     */
    private fun isPasteCompleted(playerUuid: UUID, pending: PendingClipboard): Boolean {
        val player = Bukkit.getPlayer(playerUuid) ?: return false
        val world = player.world
        val capture = WorldEditCapture(
            origin = pending.origin,
            entries = pending.placements.map {
                WorldEditRelativeEntry(it, it.relativeX, it.relativeY, it.relativeZ)
            },
        )
        val resolved = WorldEditSelectionSupport.resolvePastePoints(player, capture) ?: return false
        if (resolved.isEmpty()) return true
        return resolved.all { pasted ->
            if (pasted.worldKey != world.key.toString()) return@all true
            val key = keyOf(world.name, pasted.x, pasted.y, pasted.z)
            if (key in pending.pastedKeys) return@all true
            val useSameId = pending.mode == WorldEditClipboardOperation.CUT &&
                !pending.sameIdPasteConsumed &&
                pasted.entry.placementKey in pending.removedKeys
            if (useSameId) {
                val existing = plugin.placements.find(world, pasted.x, pasted.y, pasted.z)
                if (existing != null && existing.scriptId == pasted.entry.scriptId) return@all true
            }
            val before = pending.pasteSnapshot?.get(Triple(pasted.x, pasted.y, pasted.z))
            val nowGlass = PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(pasted.x, pasted.y, pasted.z).type)
            // 今回の変更で配置素材になった項目、または既に正しく登録済みの項目だけ進めます。
            nowGlass && (before == null || !PlacedBlockMaterials.isPlacedBlock(before))
        }
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
     * `//undo`・`//redo` を検出して台帳追跡を開始します。
     *
     * undo／redo 専用イベントは存在しないため、コマンド実行前の台帳を
     * 記録し、編集完了後に差分照合します。redo も対称な一括編集として
     * 同一経路で扱います。コンソール・コマンドブロック・API直呼びの
     * 履歴操作は検知対象外です。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldEditUndoRedoCommand(event: PlayerCommandPreprocessEvent) {
        if (!WorldEditCommands.isUndoCommand(event.message) && !WorldEditCommands.isRedoCommand(event.message)) return
        if (!WorldEditAvailability.isAvailable()) return
        captureUndo(event.player, event.message)
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
        pendingUndos.remove(event.player.uniqueId)
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
        val pending = PendingClipboard(
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
        pendingClipboards[player.uniqueId] = pending
        if (mode == WorldEditClipboardOperation.CUT) {
            // WorldEditのcutはBukkitのBlockBreakEventを通らないため、移動元の
            // 実変化を確認できるまで再確認し、成功した項目だけ台帳から消します。
            // FAWEの低速編集に備えて移動確定と同じ試行上限を使います。
            WorldEditReconciler.runUntilReady(
                plugin = plugin,
                isStillCurrent = { pendingClipboards[player.uniqueId] === pending },
                isReady = { isCutCompleted(player.uniqueId, pending) },
                onReady = {
                    if (pendingClipboards[player.uniqueId] === pending) {
                        removeCutSourcePlacements(player.uniqueId)
                    }
                },
                onTimeout = { attempts ->
                    plugin.logger.warning(
                        "[KantanCommander] //cut の移動元確定を期限切れで見送りました: " +
                            "player=${player.name} attempt=$attempts",
                    )
                },
                maxAttempts = MOVE_MAX_ATTEMPTS,
            )
        }
    }

    /**
     * issue #review P1-2: 切り取り完了条件です。
     *
     * 全項目の移動元台帳が消えているか、実ブロックが配置素材でなくなって
     * いる場合に確定します。権限不足などで切り取りが失敗した場合は成立せず、
     * 台帳・表示・起動登録を解除しません。
     */
    private fun isCutCompleted(playerUuid: UUID, pending: PendingClipboard): Boolean {
        val player = Bukkit.getPlayer(playerUuid) ?: return false
        val world = Bukkit.getWorld(pending.sourceWorldName) ?: return false
        return pending.placements.all { relative ->
            if (relative.placementKey in pending.removedKeys) return@all true
            val placement = findByKey(relative.placementKey) ?: return@all true
            !PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(placement.x, placement.y, placement.z).type)
        }
    }

    /**
     * `//cut` の移動元配置を消します。
     *
     * スクリプトは貼付け時の再登録に使うため削除しません。編集中の画面と
     * テスト実行だけを先に解放し、削除済み配置への遅延入力を防ぎます。
     * issue #review P1-2: 項目別に実変化（移動元が配置素材でなくなること）を
     * 確認してから確定し、失敗した切り取りで元の配置を解除しません。
     * 全件確定でのみ [PendingClipboard.sourceRemoved] を立てます。
     */
    private fun removeCutSourcePlacements(playerUuid: UUID) {
        val pending = pendingClipboards[playerUuid] ?: return
        if (pending.mode != WorldEditClipboardOperation.CUT || pending.sourceRemoved) return
        val player = Bukkit.getPlayer(playerUuid) ?: return
        if (!WorldEditSelectionSupport.isClipboardOriginMatching(player, pending.origin)) return
        val world = Bukkit.getWorld(pending.sourceWorldName) ?: return
        var failedKeys = 0
        for (relative in pending.placements) {
            if (!pending.removedKeys.add(relative.placementKey)) continue
            val placement = findByKey(relative.placementKey)
            if (placement == null) continue
            // 実ブロックが残っている場合は切り取り不成立のため台帳へ手を触れません。
            if (PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(placement.x, placement.y, placement.z).type)) {
                pending.removedKeys.remove(relative.placementKey)
                failedKeys++
                continue
            }
            releaseRuntimeState(placement)
            val removed = runCatching {
                plugin.placements.remove(world, placement.x, placement.y, placement.z)
            }.getOrNull()
            if (removed == null) {
                pending.removedKeys.remove(relative.placementKey)
                failedKeys++
                continue
            }
            plugin.placements.removeDisplay(world, removed.displayId)
        }
        if (failedKeys > 0) {
            plugin.logger.warning(
                "[KantanCommander] //cut の移動元確定を見送りました: player=${player.name} failed=$failedKeys",
            )
            return
        }
        pending.sourceRemoved = true
    }

    /**
     * issue #review P1-3: 貼付け確定です。
     *
     * 冪等な単一パスであり、再確認・期限後の最善努力のいずれからも呼べます。
     * 複製・登録を先に行い、既存配置の除去は新登録の成功後に行うため、
     * 失敗した貼り付けで既存プログラムを置換しません。同一ID再利用は
     * 切り取り確定済みの項目に限り、実際に登録した項目にだけ消費印を立てます。
     */
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
        val keptOccupants = mutableListOf<String>()
        for (pasted in resolved) {
            val relative = pasted.entry
            // 貼付け先ワールドが変わった場合は登録しません。
            if (pasted.worldKey != world.key.toString()) continue
            val targetKey = keyOf(world.name, pasted.x, pasted.y, pasted.z)
            if (targetKey in pending.pastedKeys) continue
            val targetBlock = world.getBlockAt(pasted.x, pasted.y, pasted.z)
            // 実体が配置素材でない場合は貼付けが成立していないため、台帳を変更しません。
            if (!PlacedBlockMaterials.isPlacedBlock(targetBlock.type)) continue
            val existing = plugin.placements.find(world, pasted.x, pasted.y, pasted.z)
            if (existing != null && existing.scriptId == relative.scriptId) continue
            // CUT同一ID再利用は切り取り確定済みの項目に限ります。
            // 未確定項目は複写経路へ迂回し、同一スクリプトの重複登録を防ぎます。
            val useSameId = pending.mode == WorldEditClipboardOperation.CUT &&
                !pending.sameIdPasteConsumed &&
                relative.placementKey in pending.removedKeys
            // issue #review P1-3: 既存配置は新登録の成功後に除去します。
            // 今回の変更を確認できない既存（貼付け前からある占有）は保持します。
            if (existing != null && !isPasteChangeConfirmed(pending, pasted.x, pasted.y, pasted.z)) {
                keptOccupants.add(targetKey)
                continue
            }
            val scriptId = if (useSameId) {
                relative.scriptId
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
                copied.id
            }
            // 占有の台帳・表示を先に外し（スクリプトは保持）、新登録の失敗時は復元します。
            // 登録後に除去すると新登録を消すため、順序を入れ替えません。
            val occupantBackup = if (existing != null) {
                releaseRuntimeState(existing)
                val removed = runCatching {
                    plugin.placements.remove(world, existing.x, existing.y, existing.z)
                }.getOrNull()
                if (removed == null) {
                    plugin.logger.warning(
                        "[KantanCommander] //paste の既存除去に失敗しました: placement=${existing.key}",
                    )
                    continue
                }
                plugin.placements.removeDisplay(world, removed.displayId)
                removed
            } else null
            if (!registerPlacement(world, pasted.x, pasted.y, pasted.z, scriptId, relative.facing)) {
                if (occupantBackup != null) {
                    runCatching {
                        plugin.placements.add(occupantBackup)
                        plugin.placements.spawnDisplay(world, occupantBackup)
                    }.onFailure { failure ->
                        plugin.logger.log(
                            Level.WARNING,
                            "[KantanCommander] //paste の既存復元に失敗しました: placement=${occupantBackup.key}",
                            failure,
                        )
                    }
                }
                continue
            }
            pending.pastedKeys.add(targetKey)
            if (useSameId) pending.sameIdPasteConsumed = true
            if (occupantBackup != null &&
                plugin.placements.all().none { it.scriptId == occupantBackup.scriptId }
            ) {
                runCatching { plugin.scripts.delete(occupantBackup.scriptId) }
                    .onFailure { failure ->
                        plugin.logger.log(
                            Level.WARNING,
                            "[KantanCommander] //paste の既存スクリプト削除に失敗しました: script=${occupantBackup.scriptId}, placement=${occupantBackup.key}",
                            failure,
                        )
                    }
            }
            plugin.refreshRedstoneTopologyAround(targetBlock)
        }
        if (keptOccupants.isNotEmpty()) {
            plugin.logger.warning(
                "[KantanCommander] //paste の変更を確認できない既存配置を保持しました: " +
                    "player=${player.name} points=${keptOccupants.joinToString(" ")}",
            )
        }
    }

    /**
     * issue #review P1-3: 今回の貼り付けによる変更かを確認します。
     *
     * 貼付けコマンド時点で非配置だったセルが配置素材になっていれば、
     * 今回の編集による到達とみなします。スナップショットにないセル
     * （解決のずれ等）は従来どおり登録へ進めます。
     */
    private fun isPasteChangeConfirmed(pending: PendingClipboard, x: Int, y: Int, z: Int): Boolean {
        val before = pending.pasteSnapshot?.get(Triple(x, y, z)) ?: return true
        return !PlacedBlockMaterials.isPlacedBlock(before)
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
     * 移動写像として一括評価します。重なり移動では中間の移動元に前任者の
     * 到達が残るため、「移動元が消えた」だけでなく「移動元が自バッチの
     * 別項目の移動先と一致する」場合も確定済みとします。確保時に既に
     * 実体がなかった項目は判定から除外し、掃除へ委ねます。
     */
    private fun isMoveCompleted(pending: PendingMove): Boolean {
        val world = Bukkit.getWorld(pending.sourceWorldName) ?: return false
        val live = pending.placements.filter { it.sourceWasGlass }
        if (live.isEmpty()) return true
        val targets = live.mapTo(mutableSetOf()) { targetPoint(sourcePoint(pending, it), pending.offset) }
        return live.all { relative ->
            val source = sourcePoint(pending, relative)
            val target = targetPoint(source, pending.offset)
            if (!PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(target.first, target.second, target.third).type)) {
                return@all false
            }
            !PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(source.first, source.second, source.third).type) ||
                source in targets
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
     * [offset] には予測変位または実測変位を渡します。移動写像を一括評価してから、
     * 第1相で移動元・重なり既存の台帳除去、第2相で移動先登録を行います。
     * 第2相の失敗時は第1相の除去を placementKey 単位で復元し、スクリプトの
     * 削除は全登録の成功後に遅延して行います。重なり移動では中間セルを
     * 二重に扱わないため、完了条件の成立しない配置消失が起きません。
     */
    private fun applyPendingMove(pending: PendingMove, offset: WorldEditMoveOffset) {
        // 呼び出し側で保留の同一性を確認・除去済みのため、ここでは再確認しません。
        val world = Bukkit.getWorld(pending.sourceWorldName) ?: return
        val live = pending.placements.filter { it.sourceWasGlass }
        if (live.isEmpty()) return
        val batchSourceKeys = live.mapTo(mutableSetOf()) {
            val source = sourcePoint(pending, it)
            keyOf(pending.sourceWorldName, source.first, source.second, source.third)
        }
        // 世界状態の読み取りは計画確定前に一括して行い、確定中の変化に引きずられません。
        val glassNow = mutableMapOf<Triple<Int, Int, Int>, Boolean>()
        fun isGlass(point: Triple<Int, Int, Int>): Boolean = glassNow.getOrPut(point) {
            PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(point.first, point.second, point.third).type)
        }
        val ledgerNow = mutableMapOf<Triple<Int, Int, Int>, DiskPlacement?>()
        fun existingAt(point: Triple<Int, Int, Int>): DiskPlacement? = ledgerNow.getOrPut(point) {
            plugin.placements.find(world, point.first, point.second, point.third)
        }
        val plans = WorldEditMovePlanner.planMoveBatch(
            items = live,
            sourceOf = { sourcePoint(pending, it) },
            targetOf = { targetPoint(it, offset) },
            inHeight = { (_, y, _) -> y in world.minHeight..world.maxHeight },
            isGlass = ::isGlass,
            existingAt = ::existingAt,
            batchSourceKeys = batchSourceKeys,
        )
        // 第1相：移動元・重なり既存の台帳除去（スクリプト削除は遅延します）。
        val removedSources = mutableListOf<DiskPlacement>()
        val removedOverlapped = mutableListOf<DiskPlacement>()
        // 消去確定した項目のスクリプトは、成功確定後に遅延して削除します。
        // 同一スクリプトの複数貼付けがあり得るため、他に生存参照がなければ消します。
        val erasedScriptIds = mutableListOf<Pair<UUID, String>>()
        fun deleteScriptIfOrphaned(scriptId: UUID, placementKey: String) {
            if (plugin.placements.all().any { it.scriptId == scriptId }) return
            runCatching { plugin.scripts.delete(scriptId) }
                .onFailure { failure ->
                    plugin.logger.log(
                        Level.WARNING,
                        "[KantanCommander] 移動確定後の不要スクリプト削除に失敗しました: script=$scriptId, placement=$placementKey",
                        failure,
                    )
                }
        }
        var phaseFailed = false
        outer@ for (plan in plans) {
            when (plan.action) {
                MoveItemAction.MOVE_SAME, MoveItemAction.ERASE -> {
                    val moved = findByKey(plan.relative.placementKey)
                    if (moved != null) {
                        releaseRuntimeState(moved)
                        val removed = runCatching {
                            plugin.placements.remove(world, moved.x, moved.y, moved.z)
                        }.getOrNull()
                        if (removed == null) {
                            plugin.logger.warning(
                                "[KantanCommander] //move の移動元除去に失敗したため残りを中止します: placement=${plan.relative.placementKey}",
                            )
                            phaseFailed = true
                            break@outer
                        }
                        plugin.placements.removeDisplay(world, removed.displayId)
                        removedSources.add(removed)
                        if (plan.action == MoveItemAction.ERASE) {
                            erasedScriptIds.add(removed.scriptId to removed.key)
                        }
                    }
                }
                MoveItemAction.COPY_NEW, MoveItemAction.NOOP, MoveItemAction.SKIP -> Unit
            }
            val overlapped = plan.overlappedExisting
            if (overlapped != null) {
                releaseRuntimeState(overlapped)
                val removed = runCatching {
                    plugin.placements.remove(world, overlapped.x, overlapped.y, overlapped.z)
                }.getOrNull()
                if (removed == null) {
                    plugin.logger.warning(
                        "[KantanCommander] //move の重なり既存除去に失敗したため残りを中止します: placement=${overlapped.key}",
                    )
                    phaseFailed = true
                    break@outer
                }
                plugin.placements.removeDisplay(world, removed.displayId)
                removedOverlapped.add(removed)
            }
        }
        // 第2相：移動先登録。失敗時は第1相の除去を復元します。
        val registeredKeys = mutableListOf<Triple<Int, Int, Int>>()
        if (!phaseFailed) {
            for (plan in plans) {
                val ok = when (plan.action) {
                    MoveItemAction.MOVE_SAME -> {
                        val registered = registerPlacement(
                            world, plan.target.first, plan.target.second, plan.target.third,
                            plan.relative.scriptId, plan.relative.facing, rollbackOnFailure = false,
                        )
                        if (registered) registeredKeys.add(plan.target)
                        registered
                    }
                    MoveItemAction.COPY_NEW -> {
                        val sourceScript = plugin.scripts.load(plan.relative.scriptId)
                        if (sourceScript == null) {
                            plugin.logger.warning("移動元のスクリプトが無いため登録を見送りました: placement=${plan.relative.placementKey}")
                            false
                        } else {
                            val copied = runCatching { plugin.scripts.copyForPlacement(sourceScript) }.getOrNull()
                            if (copied == null) {
                                plugin.logger.warning("移動用のスクリプト複製に失敗しました: placement=${plan.relative.placementKey}")
                                false
                            } else {
                                val registered = registerPlacement(
                                    world, plan.target.first, plan.target.second, plan.target.third,
                                    copied.id, plan.relative.facing, rollbackOnFailure = false,
                                )
                                if (registered) registeredKeys.add(plan.target)
                                // 複製後に登録できなかったスクリプトは孤児化させず削除します。
                                if (!registered) {
                                    runCatching { plugin.scripts.delete(copied.id) }
                                }
                                registered
                            }
                        }
                    }
                    MoveItemAction.ERASE, MoveItemAction.NOOP, MoveItemAction.SKIP -> true
                }
                if (!ok) {
                    phaseFailed = true
                    break
                }
                plugin.refreshRedstoneTopologyAround(world.getBlockAt(plan.target.first, plan.target.second, plan.target.third))
                plugin.refreshRedstoneTopologyAround(world.getBlockAt(plan.source.first, plan.source.second, plan.source.third))
            }
        }
        if (phaseFailed) {
            // 復元：登録済み移動先の除去と、除去済み台帳の再登録を行います。
            // バッチ参加スクリプトは削除していないため、配置の再登録で復旧します。
            for (target in registeredKeys) {
                runCatching { plugin.placements.remove(world, target.first, target.second, target.third) }
            }
            for (removed in removedSources + removedOverlapped) {
                runCatching {
                    plugin.placements.add(removed)
                    plugin.placements.spawnDisplay(world, removed)
                }.onFailure { failure ->
                    plugin.logger.log(
                        Level.WARNING,
                        "[KantanCommander] //move の復元に失敗しました: placement=${removed.key}",
                        failure,
                    )
                }
            }
            return
        }
        // 成功確定後に限り、自バッチ外の重なり既存スクリプトと消去確定した
        // 項目のスクリプトを削除します。他に生存参照があるものは残します。
        for (overlapped in removedOverlapped) {
            deleteScriptIfOrphaned(overlapped.scriptId, overlapped.key)
        }
        for ((scriptId, placementKey) in erasedScriptIds) {
            deleteScriptIfOrphaned(scriptId, placementKey)
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
     * undo／redo 実行前の台帳を記録し、編集完了後の照合へ回します。
     *
     * `PlayerCommandPreprocessEvent` はコマンド実行前に発生するため、
     * 台帳のあるワールドでの undo／redo だけを追跡します。履歴引数
     * （回数・世界）は無視し、実際の差分で照合するため結果に影響しません。
     */
    private fun captureUndo(player: Player, rawCommand: String) {
        val world = player.world
        val entries = plugin.placements.all()
            .filter { it.world == world.name }
            .map { UndoLedgerEntry(it.key, it.x, it.y, it.z, it.scriptId, it.facing) }
        if (entries.isEmpty()) return
        val pending = PendingUndo(
            worldName = world.name,
            rawCommand = rawCommand,
            minHeight = world.minHeight,
            maxHeight = world.maxHeight,
            entries = entries,
        )
        pendingUndos[player.uniqueId] = pending
        plugin.logger.info(
            "[KantanCommander] undo/redo を追跡します: player=${player.name} command=$rawCommand " +
                "placements=${entries.size}",
        )
        WorldEditReconciler.runUntilReady(
            plugin = plugin,
            isStillCurrent = { pendingUndos[player.uniqueId] === pending },
            isReady = { isUndoSettled(pending) },
            onReady = {
                if (pendingUndos.remove(player.uniqueId, pending)) {
                    applyUndoRematch(pending)
                }
            },
            onTimeout = { attempts ->
                if (pendingUndos.remove(player.uniqueId, pending)) {
                    graceUndoRematch(pending, attempts)
                }
            },
            maxAttempts = MOVE_MAX_ATTEMPTS,
        )
    }

    /**
     * undo／redo の確定条件です。
     *
     * 照合結果が確定系（変化なし・追従可能）で2回連続一致した場合だけ
     * 進めます。曖昧な間は低速編集中の可能性があるため待機し、期限切れで
     * 猶予経路へ回します。
     */
    private fun isUndoSettled(pending: PendingUndo): Boolean {
        val outcome = computeUndoOutcome(pending)
        val previous = pending.lastOutcome
        pending.lastOutcome = outcome
        return (outcome is UndoOutcome.Noop || outcome is UndoOutcome.Accept) && outcome == previous
    }

    /** 実行前台帳と現在状態の差分から照合結果を求めます。 */
    private fun computeUndoOutcome(pending: PendingUndo): UndoOutcome {
        val world = Bukkit.getWorld(pending.worldName) ?: return UndoOutcome.Ambiguous
        val registered = plugin.placements.all()
            .filter { it.world == pending.worldName }
            .map { Triple(it.x, it.y, it.z) }
            .toSet()
        val lost = findUndoLost(world, pending)
        if (lost.isEmpty()) return UndoOutcome.Noop
        val lostCells = lost.map { Triple(it.x, it.y, it.z) }
        val appeared = scanUndoAppeared(world, pending, lostCells, registered)
            ?: return UndoOutcome.Ambiguous
        val translation = WorldEditUndoRematch.matchTranslation(lostCells, appeared)
            ?: return UndoOutcome.Ambiguous
        // 照合器と同じ座標順で整列し、対応付けを一致させます。
        val orderedLost = lost.sortedWith(compareBy({ it.x }, { it.y }, { it.z }))
        val orderedAppeared = appeared.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        return UndoOutcome.Accept(translation, orderedLost.zip(orderedAppeared))
    }

    /**
     * 台帳に残るのに実体を失った項目を返します。
     *
     * 処理中の移動・複写の移動元と、GUI等で置換・削除済みの台帳は
     * 対象外とします。
     */
    private fun findUndoLost(world: World, pending: PendingUndo): List<UndoLedgerEntry> =
        pending.entries.filter { entry ->
            if (isMoveInFlight(entry.placementKey)) return@filter false
            val current = findByKey(entry.placementKey)
            if (current == null || current.scriptId != entry.scriptId) return@filter false
            !PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(entry.x, entry.y, entry.z).type)
        }

    /**
     * 失われたセル群の周辺で未登録の配置セルを探します。
     *
     * 失われたセル群の外接に余白を付けた一括走査とし、セル数上限を
     * 超える場合は主スレッド負荷を避けて null（曖昧扱い）とします。
     */
    private fun scanUndoAppeared(
        world: World,
        pending: PendingUndo,
        lost: List<Triple<Int, Int, Int>>,
        registered: Set<Triple<Int, Int, Int>>,
    ): List<Triple<Int, Int, Int>>? {
        val minX = lost.minOf { it.first } - UNDO_SEARCH_MARGIN
        val maxX = lost.maxOf { it.first } + UNDO_SEARCH_MARGIN
        val minY = (lost.minOf { it.second } - UNDO_SEARCH_MARGIN).coerceAtLeast(pending.minHeight)
        val maxY = (lost.maxOf { it.second } + UNDO_SEARCH_MARGIN).coerceAtMost(pending.maxHeight)
        val minZ = lost.minOf { it.third } - UNDO_SEARCH_MARGIN
        val maxZ = lost.maxOf { it.third } + UNDO_SEARCH_MARGIN
        val volume = (maxX - minX + 1L) * (maxY - minY + 1L) * (maxZ - minZ + 1L)
        if (volume > UNDO_SCAN_CELL_CAP) return null
        val found = mutableListOf<Triple<Int, Int, Int>>()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val cell = Triple(x, y, z)
                    if (cell in registered) continue
                    if (!PlacedBlockMaterials.isPlacedBlock(world.getBlockAt(x, y, z).type)) continue
                    found.add(cell)
                }
            }
        }
        return found
    }

    /**
     * 照合済み変位で台帳を追従させます。
     *
     * 失われた台帳セルを除去し、出現セルへ同一スクリプトで再登録します。
     * 失敗時は除去分を復元し、消失を防ぐため猶予へ回します。
     */
    private fun applyUndoRematch(pending: PendingUndo) {
        val accept = pending.lastOutcome as? UndoOutcome.Accept ?: return
        val world = Bukkit.getWorld(pending.worldName) ?: return
        undoGrace.keys.removeAll(accept.pairs.map { it.first.placementKey }.toSet())
        val registeredTargets = mutableListOf<Triple<Int, Int, Int>>()
        val removedSources = mutableListOf<DiskPlacement>()
        var failed = false
        for ((entry, target) in accept.pairs) {
            val moved = findByKey(entry.placementKey)
            if (moved != null) {
                releaseRuntimeState(moved)
                val removed = runCatching {
                    plugin.placements.remove(world, moved.x, moved.y, moved.z)
                }.getOrNull()
                if (removed == null) {
                    failed = true
                    break
                }
                plugin.placements.removeDisplay(world, removed.displayId)
                removedSources.add(removed)
            }
            val registered = registerPlacement(
                world, target.first, target.second, target.third,
                entry.scriptId, entry.facing, rollbackOnFailure = false,
            )
            if (registered) {
                registeredTargets.add(target)
                plugin.refreshRedstoneTopologyAround(world.getBlockAt(target.first, target.second, target.third))
            } else {
                failed = true
                break
            }
        }
        if (failed) {
            for (target in registeredTargets) {
                runCatching { plugin.placements.remove(world, target.first, target.second, target.third) }
            }
            for (removed in removedSources) {
                runCatching {
                    plugin.placements.add(removed)
                    plugin.placements.spawnDisplay(world, removed)
                }
            }
            val expiry = System.currentTimeMillis() + UNDO_GRACE_MILLIS
            removedSources.forEach { undoGrace[it.key] = expiry }
            plugin.logger.warning(
                "[KantanCommander] undo/redo の台帳追従に失敗したため復元し掃除を猶予します: " +
                    "command=${pending.rawCommand}",
            )
            return
        }
        plugin.logger.info(
            "[KantanCommander] undo/redo に台帳を追従しました: command=${pending.rawCommand} " +
                "translation=(${accept.translation.x},${accept.translation.y},${accept.translation.z}) " +
                "count=${accept.pairs.size}",
        )
    }

    /**
     * 期限切れ時に失われた項目の掃除を猶予します。
     *
     * 低速編集の完了待ちと曖昧な差分のどちらでも、台帳・スクリプトを
     * 消さずに警告だけ残し、手動確認の時間を確保します。
     */
    private fun graceUndoRematch(pending: PendingUndo, attempts: Int) {
        val world = Bukkit.getWorld(pending.worldName)
        val lostKeys = if (world == null) {
            pending.entries.map { it.placementKey }
        } else {
            findUndoLost(world, pending).map { it.placementKey }
        }
        if (lostKeys.isEmpty()) {
            plugin.logger.fine(
                "[KantanCommander] undo/redo の追跡を終了します（台帳不変）: command=${pending.rawCommand}",
            )
            return
        }
        val expiry = System.currentTimeMillis() + UNDO_GRACE_MILLIS
        lostKeys.forEach { undoGrace[it] = expiry }
        plugin.logger.warning(
            "[KantanCommander] undo/redo の台帳追従を確定できないため掃除を猶予します: " +
                "command=${pending.rawCommand} attempt=$attempts placements=$lostKeys",
        )
    }

    /**
     * 掃除猶予中かを返します。
     *
     * 期限切れの猶予はここで破棄し、掃除対象へ戻します。
     */
    private fun isUndoGraceActive(placementKey: String): Boolean {
        val expiry = undoGrace[placementKey] ?: return false
        if (expiry >= System.currentTimeMillis()) return true
        undoGrace.remove(placementKey, expiry)
        return false
    }

    /**
     * 実体だけが消えた配置を整理します。
     *
     * 台帳・表示・スクリプトをまとめて削除し、起動索引と隣接ダスト形状も
     * 戻します。ワールド未読込時は対象外とします。処理中の移動・複写の
     * 移動元と、undo／redo 猶予中の項目は除外し、低速編集中や未確定差分の
     * スクリプト消失を防ぎます。
     */
    private fun sweepOnce() {
        for (placement in plugin.placements.all()) {
            if (isMoveInFlight(placement.key)) continue
            if (isUndoGraceActive(placement.key)) continue
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
     * issue #review P2-5: 除外は未確定の項目に限定します。再貼付け用の
     * clipboard保持（複数回貼付けの正規機能）と、編集中の掃除除外を分離し、
     * COPY元や確定済みCUTは掃除対象に戻します。TTLは設けません。
     * 正規の複数回貼付けを壊すためです。quit・上書き確保で破棄されます。
     */
    private fun isMoveInFlight(placementKey: String): Boolean =
        pendingMoves.values.any { pending -> pending.placements.any { it.placementKey == placementKey } } ||
            pendingClipboards.values.any { pending ->
                pending.mode == WorldEditClipboardOperation.CUT &&
                    pending.placements.any { it.placementKey == placementKey && it.placementKey !in pending.removedKeys }
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

    /**
     * 配置を台帳・表示へ登録し、成否を返します。
     *
     * 失敗時は警告を残します。一括確定中（[rollbackOnFailure] = false）は
     * 呼び出し側の一括ロールバックへ委ね、個別に重なり先を消しません。
     */
    private fun registerPlacement(
        world: World,
        x: Int,
        y: Int,
        z: Int,
        scriptId: UUID,
        facing: String,
        rollbackOnFailure: Boolean = true,
    ): Boolean {
        val placement = DiskPlacement(world.name, x, y, z, scriptId, facing, null)
        val result = runCatching {
            plugin.placements.add(placement)
            plugin.placements.spawnDisplay(world, placement)
        }
        result.onFailure { failure ->
            if (rollbackOnFailure) {
                runCatching { plugin.placements.remove(world, x, y, z) }
            }
            plugin.logger.log(
                Level.WARNING,
                "同期した配置の登録に失敗しました: placement=${keyOf(world.name, x, y, z)}",
                failure,
            )
        }
        return result.isSuccess
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

        /**
         * undo／redo 照合の探索余白です。
         *
         * 失われたセル群の外接へ付ける片側余白であり、典型的な移動距離を
         * 収めます。走査セル上限と併せて主スレッド負荷を抑えます。
         */
        const val UNDO_SEARCH_MARGIN = 32

        /** undo／redo 照合の一括走査セル上限です。超過時は曖昧扱いにします。 */
        const val UNDO_SCAN_CELL_CAP = 300_000L

        /** undo／redo 未確定時の掃除猶予です。期限切れ後は通常掃除へ戻します。 */
        const val UNDO_GRACE_MILLIS = 30L * 60L * 1000L
    }
}
