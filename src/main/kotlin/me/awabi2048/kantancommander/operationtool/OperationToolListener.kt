package me.awabi2048.kantancommander.operationtool

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.KantanItemKind
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.SpatialVector
import me.awabi2048.kantancommander.model.toSpatialVector
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * issue #14の操作ツールwandの入力処理です。
 *
 * ワールド上で対象を直接選択し、選択後の操作は原則としてInventory GUIから行います。
 * 選択操作そのものと右クリックが競合する場合は、選択操作を優先します。
 * wand所持中のクリックは世界へ作用させず、常にキャンセルして選択操作にします。
 * ただし、かんたんコマンダー制御ブロックへのスニーク右クリックだけは例外とし、
 * バニラの使用・配置へ素通しするため横取りしません。
 *
 * かんたんコマンダーの使用権限と同期させるため、全てのwand操作は
 * 配置アクセス判定のALLOWを要求します。Chanpon側の提出ロック・バックアップ状態は
 * 参照しません（Structure保存の責務分離）。
 */
class OperationToolListener(
    private val plugin: KantanCommanderPlugin,
    private val sessions: OperationToolSessions = OperationToolSessions(),
    private val displays: OperationToolBoxDisplay = OperationToolBoxDisplay(plugin),
    /** 操作可能なEntityの判定です。既存domainの判定を複製せず共有predicateとして扱います。 */
    private val isOperable: (operator: Player, entity: Entity) -> Boolean = { operator, entity ->
        entity.isValid && !entity.isDead &&
            entity !is Player &&
            entity.world.uid == operator.world.uid &&
            !isSystemEntity(entity)
    },
) : Listener {
    private var editTask: BukkitTask? = null
    private val lastCandidateCounts = mutableMapOf<UUID, Int>()

    /**
     * Entity右クリックを処理したtickです。
     *
     * クライアントはEntity右クリック時にinteractパケットへ続けてuse_itemパケットも
     * 送るため、同一tickにPlayerInteractEvent(RIGHT_CLICK_AIR/BLOCK)が追加発火します。
     * その重複分だけを抑止するため、処理済みtickを記録します。
     */
    private val entityClickTicks = mutableMapOf<UUID, Int>()

    /** Box選択開始時の基準点距離です。視点から視線方向へ進んだ点を端点とします。 */
    private val boxReach = 2.0

    fun sessions(): OperationToolSessions = sessions

    fun displays(): OperationToolBoxDisplay = displays

    /** 編集中Boxの追従タスクを停止します。プラグイン無効化時に呼び出します。 */
    fun stop() {
        editTask?.cancel()
        editTask = null
        lastCandidateCounts.clear()
        entityClickTicks.clear()
        displays.removeAll()
        sessions.clear()
    }

    // wand所持中の掘削・破壊は選択操作と誤認されるため抑止します。
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockDamage(event: BlockDamageEvent) {
        if (KantanItemService.kind(event.player.inventory.itemInMainHand) == KantanItemKind.WAND) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (KantanItemService.kind(event.player.inventory.itemInMainHand) == KantanItemKind.WAND) {
            event.isCancelled = true
        }
    }

    // wand所持中のEntity攻撃は選択操作と誤認される上に対象を破壊し得るため抑止します。
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onAttack(event: PrePlayerAttackEntityEvent) {
        if (KantanItemService.kind(event.player.inventory.itemInMainHand) == KantanItemKind.WAND) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (KantanItemService.kind(player.inventory.itemInMainHand) != KantanItemKind.WAND) return
        // メインへwandを持つ間はオフハンドの使用・配置も世界へ作用させません。
        if (event.hand == EquipmentSlot.OFF_HAND) {
            event.isCancelled = true
            return
        }
        if (event.hand != EquipmentSlot.HAND) return
        // Entity右クリックの直後に同一tickで発火する重複分を抑止します。
        if (event.action.isRightClick && entityClickTicks[player.uniqueId] == Bukkit.getCurrentTick()) {
            event.isCancelled = true
            return
        }
        // かんたんコマンダー制御ブロックへのスニーク右クリックは、KantanInteractionListenerが
        // バニラの使用・配置へ素通しします。wandの選択操作で横取りせず、所有権をそちらへ寄せます。
        // 非スニークはKantan側が先にキャンセルするため、ここへは到達しません。
        if (player.isSneaking && event.action == Action.RIGHT_CLICK_BLOCK &&
            event.clickedBlock?.let { plugin.placements.find(it.location) } != null
        ) {
            return
        }
        if (!requireManage(player)) {
            event.isCancelled = true
            return
        }
        val session = sessions.getOrCreate(player.uniqueId)
        when (event.action) {
            Action.LEFT_CLICK_BLOCK, Action.LEFT_CLICK_AIR -> onLeftClick(player, session, event)
            Action.RIGHT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR -> onRightClick(player, session, event)
            else -> return
        }
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        if (KantanItemService.kind(player.inventory.itemInMainHand) != KantanItemKind.WAND) return
        // メインへwandを持つ間はオフハンドのEntity操作も世界へ作用させません。
        if (event.hand == EquipmentSlot.OFF_HAND) {
            event.isCancelled = true
            return
        }
        if (event.hand != EquipmentSlot.HAND) return
        // 同一tickのuse_item由来PlayerInteractEventを重複処理しないよう記録します。
        entityClickTicks[player.uniqueId] = Bukkit.getCurrentTick()
        if (!requireManage(player)) {
            event.isCancelled = true
            return
        }
        val session = sessions.getOrCreate(player.uniqueId)
        // 直接選択モードでの対象Entityへの右クリックは選択へ追加します。
        // 高精度モデルHit Testは使用せず、クリック対象をそのまま扱います。
        if (session.mode == OperationToolMode.ENTITY_DIRECT && isOperable(player, event.rightClicked)) {
            val added = session.addEntities(listOf(event.rightClicked.uniqueId))
            if (added.isEmpty() && session.selected().size >= session.maxEntities) {
                OperationToolFeedback.notifyKey(
                    player, session,
                    KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_SELECTION_FULL,
                    mapOf("max" to session.maxEntities),
                    NamedTextColor.RED,
                )
            } else if (added.isNotEmpty()) {
                OperationToolFeedback.showStatus(player, session)
            }
        }
        event.isCancelled = true
    }

    /**
     * スニーク + スクロールでモードを切り替えます。
     *
     * スクロールの向きで循環方向を変えます（下:次のモード / 上:前のモード）。
     * 番号キー等による大きなスロット移動はスクロールとみなさず、持ち替えとして
     * 選択を保持します。切替時はスロットを維持するためイベントをキャンセルします。
     * wandを手から離した場合は編集中Boxを解除します（確定済み選択は保持）。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (player.isSneaking &&
            KantanItemService.kind(player.inventory.itemInMainHand) == KantanItemKind.WAND &&
            isScrollStep(event.previousSlot, event.newSlot)
        ) {
            if (!requireManage(player)) {
                event.isCancelled = true
                return
            }
            val session = sessions.getOrCreate(player.uniqueId)
            val forward = isScrollForward(event.previousSlot, event.newSlot)
            session.setMode(if (forward) session.mode.next() else session.mode.previous())
            displays.clear(player.uniqueId)
            lastCandidateCounts.remove(player.uniqueId)
            // モード切替のチャット通知は行わず、ActionBarの状態表示のみ残します。
            OperationToolFeedback.showStatus(player, session)
            event.isCancelled = true
            return
        }
        cancelEditingIfHandLeft(player, player.inventory.getItem(event.newSlot))
    }

    /**
     * wandを投げ捨てた場合は編集中Boxを解除します。
     *
     * インベントリ確定がイベントより遅れる場合があるため、1tick後に
     * メインハンドを再確認してから解除します。確定済み選択は保持します。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (KantanItemService.kind(event.itemDrop.itemStack) != KantanItemKind.WAND) return
        val playerId = event.player.uniqueId
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val player = plugin.server.getPlayer(playerId) ?: return@Runnable
            cancelEditingIfHandLeft(player, player.inventory.itemInMainHand)
        }, 1L)
    }

    /**
     * 死亡時はwandを手から離すため、編集中Boxを解除します。
     *
     * 確定済み選択・履歴は保持し、表示だけ破棄します。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        val session = sessions.get(event.entity.uniqueId) ?: return
        if (session.editingBox == null) return
        session.cancelBoxEdit()
        displays.dropEditing(event.entity.uniqueId)
        lastCandidateCounts.remove(event.entity.uniqueId)
    }

    /**
     * Fキー入替でwandがメインハンドから外れた場合は編集中Boxを解除します。
     *
     * 入替後のメインハンド（入替前のオフハンド）で判定します。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        cancelEditingIfHandLeft(event.player, event.mainHandItem)
    }

    /**
     * wandを手から離した場合に編集中Boxを解除します。
     *
     * 範囲作成中だけを対象とし、確定済み選択・履歴・確定表示は保持します。
     * handItemがnull（空欄）の場合も手離しとして扱います。
     */
    private fun cancelEditingIfHandLeft(player: Player, handItem: org.bukkit.inventory.ItemStack?) {
        val session = sessions.get(player.uniqueId) ?: return
        if (session.editingBox == null) return
        if (KantanItemService.kind(handItem) == KantanItemKind.WAND) return
        session.cancelBoxEdit()
        displays.dropEditing(player.uniqueId)
        lastCandidateCounts.remove(player.uniqueId)
        OperationToolFeedback.showStatus(player, session)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onQuit(event: PlayerQuitEvent) {
        // ログアウトだけでは選択を解除せず、編集中Boxの解除と表示破棄に留めます。
        sessions.get(event.player.uniqueId)?.cancelBoxEdit()
        displays.clear(event.player.uniqueId)
        lastCandidateCounts.remove(event.player.uniqueId)
        entityClickTicks.remove(event.player.uniqueId)
        plugin.operationMenu.clearPendingDialog(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKick(event: PlayerKickEvent) {
        sessions.get(event.player.uniqueId)?.cancelBoxEdit()
        displays.clear(event.player.uniqueId)
        lastCandidateCounts.remove(event.player.uniqueId)
        entityClickTicks.remove(event.player.uniqueId)
        plugin.operationMenu.clearPendingDialog(event.player.uniqueId)
    }

    /**
     * ワールド移動時は選択状態を解除します。
     *
     * Entity選択・エリア指定・確定表示は選択時のワールドへ紐付くため、
     * 別ワールドへ持ち越すと見えない選択への操作や別ワールド権限での
     * 実行につながります。移動時に明示通知つきで解除します。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val session = sessions.get(event.player.uniqueId) ?: return
        if (!session.hasSelectionState()) return
        session.clearAll()
        displays.clear(event.player.uniqueId)
        lastCandidateCounts.remove(event.player.uniqueId)
        OperationToolFeedback.notifyKey(
            event.player, session,
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_SELECTION_CLEARED,
            emptyMap(),
            NamedTextColor.YELLOW,
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onJoin(event: PlayerJoinEvent) {
        // 表示は操作者専用のため、参加者から他者のBox表示を隠します。
        displays.hideFrom(event.player.uniqueId)
        // ログアウトで破棄された表示のうち、同ワールドのエリア指定だけを復元します。
        val playerId = event.player.uniqueId
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val player = plugin.server.getPlayer(playerId) ?: return@Runnable
            val session = sessions.get(playerId) ?: return@Runnable
            updateAreaDisplay(player, session)
        }, 1L)
    }

    private fun onLeftClick(player: Player, session: OperationToolSession, event: PlayerInteractEvent) {
        if (player.isSneaking) {
            // スニーク + 左クリックで現在の選択と履歴をすべて解除します。
            session.clearAll()
            displays.clear(player.uniqueId)
            lastCandidateCounts.remove(player.uniqueId)
            OperationToolFeedback.notifyKey(
                player, session,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_SELECTION_CLEARED,
                emptyMap(),
                NamedTextColor.YELLOW,
            )
            return
        }
        when (session.mode) {
            OperationToolMode.AREA -> {
                // 左クリックしたBlockをpos1として設定します。再指定時は更新します。
                // Entity選択用のUndoはこのモードに適用せず、空中は無視します。
                if (event.action != Action.LEFT_CLICK_BLOCK) return
                val block = event.clickedBlock ?: return
                val point = RegionPoint(block.world.name, block.location.toSpatialVector())
                session.setPos1(point)
                OperationToolFeedback.notifyKey(
                    player, session,
                    KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_POS1_SET,
                    mapOf("world" to point.worldName, "x" to block.x, "y" to block.y, "z" to block.z),
                    NamedTextColor.AQUA,
                )
                updateAreaDisplay(player, session)
            }
            OperationToolMode.ENTITY_RANGE, OperationToolMode.ENTITY_DIRECT -> {
                if (session.editingBox != null) {
                    // Box編集中の左クリックは編集中Boxをキャンセルします。
                    session.cancelBoxEdit()
                    displays.dropEditing(player.uniqueId)
                    lastCandidateCounts.remove(player.uniqueId)
                    OperationToolFeedback.showStatus(player, session)
                    return
                }
                // 左クリックで直前の確定済み選択操作をUndoします。
                val removed = session.undoLast()
                if (removed.isEmpty()) {
                    OperationToolFeedback.notifyKey(
                        player, session,
                        KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_UNDO_EMPTY,
                        emptyMap(),
                        NamedTextColor.RED,
                    )
                } else {
                    OperationToolFeedback.showStatus(player, session)
                }
            }
        }
    }

    private fun onRightClick(player: Player, session: OperationToolSession, event: PlayerInteractEvent) {
        when (session.mode) {
            OperationToolMode.AREA -> onAreaRightClick(player, session, event)
            OperationToolMode.ENTITY_RANGE -> onRangeRightClick(player, session)
            OperationToolMode.ENTITY_DIRECT -> {
                // 対象Entityを指していない状態での右クリックはEntity操作GUIを開きます。
                if (session.selected().isNotEmpty()) {
                    plugin.operationMenu.openEntity(player)
                } else {
                    OperationToolFeedback.notifyKey(
                        player, session,
                        KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_NO_SELECTION,
                        emptyMap(),
                        NamedTextColor.YELLOW,
                    )
                }
            }
        }
    }

    private fun onAreaRightClick(player: Player, session: OperationToolSession, event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) {
            // 有効なエリア選択後の空中右クリックはエリア操作GUIを開きます。
            if (session.hasValidArea()) {
                plugin.operationMenu.openArea(player)
            }
            return
        }
        val block = event.clickedBlock ?: return
        val point = RegionPoint(block.world.name, block.location.toSpatialVector())
        session.setPos2(point)
        OperationToolFeedback.notifyKey(
            player, session,
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_POS2_SET,
            mapOf("world" to point.worldName, "x" to block.x, "y" to block.y, "z" to block.z),
            NamedTextColor.AQUA,
        )
        updateAreaDisplay(player, session)
    }

    /**
     * エリア選択のディスプレイ表示を更新します。
     *
     * 両pos指定がある場合のみ水色の範囲表示を行い、未確定・別ワールドの場合は
     * 古い表示を破棄します。表示範囲の換算は [OperationToolSession] へ委譲します。
     */
    private fun updateAreaDisplay(player: Player, session: OperationToolSession) {
        val bounds = session.areaBounds()
        val first = session.pos1
        if (bounds == null || first == null) {
            displays.dropArea(player.uniqueId)
            return
        }
        val (minimum, maximum) = OperationToolSession.areaDisplayBounds(bounds)
        displays.showArea(player.uniqueId, first.worldName, minimum, maximum)
    }

    private fun onRangeRightClick(player: Player, session: OperationToolSession) {
        if (session.editingBox != null) {
            confirmBox(player, session)
            return
        }
        if (player.isSneaking) {
            // スニーク + 右クリックでBox選択を開始します。
            val anchor = eyePoint(player, boxReach)
            session.startBoxEdit(EditingBox(player.world.name, anchor, anchor))
            lastCandidateCounts[player.uniqueId] = 0
            ensureEditTask()
            return
        }
        if (session.selected().isNotEmpty()) {
            // Box非編集中かつ選択済みの右クリックはEntity操作GUIを開きます。
            plugin.operationMenu.openEntity(player)
        } else {
            OperationToolFeedback.notifyKey(
                player, session,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_NO_SELECTION,
                emptyMap(),
                NamedTextColor.YELLOW,
            )
        }
    }

    /**
     * 編集中Boxを確定し、範囲内のEntityを選択集合へ追加します。
     *
     * Entityの基準点ではなくBounding Boxのoverlapで判定し、未選択の候補のみを
     * 追加します。Boxサイズが上限を超えた場合はキャンセルします。
     */
    private fun confirmBox(player: Player, session: OperationToolSession) {
        val box = session.editingBox ?: return
        if (box.worldName != player.world.name) {
            session.cancelBoxEdit()
            displays.dropEditing(player.uniqueId)
            return
        }
        val (minimum, maximum) = box.bounds()
        if (!OperationToolSession.isBoxEdgeWithinLimit(minimum, maximum, session.maxBoxEdge)) {
            session.cancelBoxEdit()
            displays.dropEditing(player.uniqueId)
            OperationToolFeedback.notifyKey(
                player, session,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_BOX_TOO_LARGE,
                mapOf("max" to session.maxBoxEdge.toInt()),
                NamedTextColor.RED,
            )
            return
        }
        val candidates = collectOverlapping(player, minimum, maximum)
        session.retainExisting { id ->
            val entity = plugin.server.getEntity(id) ?: return@retainExisting true
            entity.isValid && !entity.isDead
        }
        val added = session.addEntities(candidates)
        session.cancelBoxEdit()
        lastCandidateCounts.remove(player.uniqueId)
        if (added.isNotEmpty()) {
            displays.replaceConfirmed(player.uniqueId, box.worldName, minimum, maximum)
            OperationToolFeedback.notifyKey(
                player, session,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_ADDED,
                mapOf("count" to added.size),
                NamedTextColor.GREEN,
            )
        } else {
            displays.replaceConfirmed(player.uniqueId, null, null, null)
            if (session.selected().size >= session.maxEntities) {
                OperationToolFeedback.notifyKey(
                    player, session,
                    KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_SELECTION_FULL,
                    mapOf("max" to session.maxEntities),
                    NamedTextColor.RED,
                )
            } else {
                OperationToolFeedback.showStatus(player, session)
            }
        }
    }

    /**
     * 選択BoxとBounding Boxがoverlapする操作可能Entityを収集します。
     *
     * 判定条件を複製せず、[isOperable] の共有predicateを利用します。
     * ツール表示は対象外です。
     */
    private fun collectOverlapping(player: Player, minimum: SpatialVector, maximum: SpatialVector): List<UUID> {
        val selection = org.bukkit.util.BoundingBox(
            minimum.x, minimum.y, minimum.z, maximum.x, maximum.y, maximum.z,
        )
        val centerX = (minimum.x + maximum.x) / 2.0
        val centerY = (minimum.y + maximum.y) / 2.0
        val centerZ = (minimum.z + maximum.z) / 2.0
        val radiusX = (maximum.x - minimum.x) / 2.0 + 1.0
        val radiusY = (maximum.y - minimum.y) / 2.0 + 1.0
        val radiusZ = (maximum.z - minimum.z) / 2.0 + 1.0
        return player.world.getNearbyEntities(
            org.bukkit.Location(player.world, centerX, centerY, centerZ),
            radiusX, radiusY, radiusZ,
        ).filter { entity ->
            isOperable(player, entity) &&
                !displays.isToolDisplay(entity.uniqueId) &&
                entity.boundingBox.overlaps(selection)
        }.map { it.uniqueId }
    }

    /**
     * 編集中Boxの追従タスクを確保します。
     *
     * 現在の視点から視線方向へ2.0 block進んだ点をもう一方の端点として毎tick更新し、
     * プレイヤーの移動・視点回転に追従します。編集中がなくなれば停止します。
     */
    private fun ensureEditTask() {
        if (editTask != null) return
        editTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tickEditingBoxes() }, 1L, 1L)
    }

    private fun tickEditingBoxes() {
        var active = false
        for (player in plugin.server.onlinePlayers) {
            val session = sessions.get(player.uniqueId) ?: continue
            val box = session.editingBox ?: continue
            active = true
            if (box.worldName != player.world.name ||
                KantanItemService.kind(player.inventory.itemInMainHand) != KantanItemKind.WAND
            ) {
                session.cancelBoxEdit()
                displays.dropEditing(player.uniqueId)
                lastCandidateCounts.remove(player.uniqueId)
                continue
            }
            val current = eyePoint(player, boxReach)
            session.updateBoxEdit(current)
            val (minimum, maximum) = session.editingBox?.bounds() ?: continue
            if (!OperationToolSession.isBoxEdgeWithinLimit(minimum, maximum, session.maxBoxEdge)) {
                session.cancelBoxEdit()
                displays.dropEditing(player.uniqueId)
                lastCandidateCounts.remove(player.uniqueId)
                OperationToolFeedback.notifyKey(
                    player, session,
                    KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_BOX_TOO_LARGE,
                    mapOf("max" to session.maxBoxEdge.toInt()),
                    NamedTextColor.RED,
                )
                continue
            }
            displays.showEditing(player.uniqueId, box.worldName, minimum, maximum)
            val count = collectOverlapping(player, minimum, maximum).count { !session.selected().contains(it) }
            if (lastCandidateCounts[player.uniqueId] != count) {
                lastCandidateCounts[player.uniqueId] = count
                OperationToolFeedback.notifyKey(
                    player, session,
                    KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_CANDIDATES,
                    mapOf("count" to count),
                    NamedTextColor.AQUA,
                )
            }
        }
        if (!active) {
            editTask?.cancel()
            editTask = null
        }
    }

    private fun requireManage(player: Player): Boolean =
        plugin.placementAccess.requireManage(player, player.world.name)

    companion object {
        /** 視点から視線方向へ進んだ点を返します。Box端点に使います。 */
        fun eyePoint(player: Player, distance: Double): SpatialVector {
            val eye = player.eyeLocation
            val direction = eye.direction
            return SpatialVector(
                eye.x + direction.x * distance,
                eye.y + direction.y * distance,
                eye.z + direction.z * distance,
            )
        }

        /**
         * ホットバースロットの移動がスクロール操作かどうかを返します。
         *
         * 隣接移動と両端の循環のみをスクロールとみなし、番号キー等による
         * 大きな移動は持ち替えとして扱いモードを切り替えません。
         */
        fun isScrollStep(previousSlot: Int, newSlot: Int): Boolean {
            if (previousSlot == newSlot) return false
            if (kotlin.math.abs(newSlot - previousSlot) == 1) return true
            return (previousSlot == 0 && newSlot == 8) || (previousSlot == 8 && newSlot == 0)
        }

        /**
         * スクロール移動がスロット増加方向かどうかを返します。
         *
         * ホイールを下へ回した増加方向（8→0の循環を含む）を次のモード、
         * 上へ回した減少方向を前のモードへ対応付けます。
         * [isScrollStep] がtrueの移動だけを対象とします。
         */
        fun isScrollForward(previousSlot: Int, newSlot: Int): Boolean =
            (newSlot - previousSlot + 9) % 9 == 1

        /**
         * CC-Systemのシステム実体かどうかを返します。
         *
         * [KantanCommanderPlugin.hitTest] の除外条件と同一契約であり、
         * Operation Tool側へ判定条件を複製しません。
         */
        fun isSystemEntity(entity: Entity): Boolean =
            runCatching { CCSystem.getAPI().getSystemEntityRegistry().isSystemEntity(entity) }
                .getOrDefault(false)
    }
}
