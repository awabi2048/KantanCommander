package me.awabi2048.kantancommander.operationtool

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.KantanItemKind
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.SpatialVector
import me.awabi2048.kantancommander.model.toSpatialVector
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * issue #14の操作ツールwandの入力処理です。
 *
 * ワールド上で対象を直接選択し、選択後の操作は原則としてInventory GUIから行います。
 * 選択操作そのものと右クリックが競合する場合は、選択操作を優先します。
 * wand所持中のクリックは世界へ作用させず、常にキャンセルして選択操作にします。
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
            entity.world.uid == operator.world.uid &&
            entity.uniqueId != operator.uniqueId &&
            !isSystemEntity(entity)
    },
) : Listener {
    private var editTask: BukkitTask? = null
    private val lastCandidateCounts = mutableMapOf<UUID, Int>()

    /** Box選択開始時の基準点距離です。視点から視線方向へ進んだ点を端点とします。 */
    private val boxReach = 2.0

    fun sessions(): OperationToolSessions = sessions

    fun displays(): OperationToolBoxDisplay = displays

    /** 編集中Boxの追従タスクを停止します。プラグイン無効化時に呼び出します。 */
    fun stop() {
        editTask?.cancel()
        editTask = null
        lastCandidateCounts.clear()
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val player = event.player
        if (KantanItemService.kind(player.inventory.itemInMainHand) != KantanItemKind.WAND) return
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
        if (event.hand != EquipmentSlot.HAND) return
        val player = event.player
        if (KantanItemService.kind(player.inventory.itemInMainHand) != KantanItemKind.WAND) return
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
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_SELECTION_FULL, mapOf("max" to session.maxEntities)))
            } else if (added.isNotEmpty()) {
                sendStatus(player, session)
            }
        }
        event.isCancelled = true
    }

    /**
     * スニーク + スクロールでモードを切り替えます。
     *
     * 番号キー等による大きなスロット移動はスクロールとみなさず、持ち替えとして
     * 選択を保持します。切替時はスロットを維持するためイベントをキャンセルします。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (!player.isSneaking) return
        if (KantanItemService.kind(player.inventory.itemInMainHand) != KantanItemKind.WAND) return
        if (!isScrollStep(event.previousSlot, event.newSlot)) return
        if (!requireManage(player)) {
            event.isCancelled = true
            return
        }
        val session = sessions.getOrCreate(player.uniqueId)
        val next = session.mode.next()
        session.setMode(next)
        displays.clear(player.uniqueId)
        lastCandidateCounts.remove(player.uniqueId)
        player.sendMessage(
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_MODE_CHANGED, mapOf("mode" to KcI18n.text(player, next.displayNameKey()))),
        )
        sendStatus(player, session)
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onQuit(event: PlayerQuitEvent) {
        // ログアウトだけでは選択を解除せず、表示だけ破棄してセッションを維持します。
        displays.clear(event.player.uniqueId)
        lastCandidateCounts.remove(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKick(event: PlayerKickEvent) {
        displays.clear(event.player.uniqueId)
        lastCandidateCounts.remove(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onJoin(event: PlayerJoinEvent) {
        // 表示は操作者専用のため、参加者から他者のBox表示を隠します。
        displays.hideFrom(event.player.uniqueId)
    }

    private fun onLeftClick(player: Player, session: OperationToolSession, event: PlayerInteractEvent) {
        if (player.isSneaking) {
            // スニーク + 左クリックで現在の選択と履歴をすべて解除します。
            session.clearAll()
            displays.clear(player.uniqueId)
            lastCandidateCounts.remove(player.uniqueId)
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_SELECTION_CLEARED))
            sendStatus(player, session)
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
                player.sendMessage(
                    KcI18n.text(
                        player,
                        KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_POS1_SET,
                        mapOf("world" to point.worldName, "x" to block.x, "y" to block.y, "z" to block.z),
                    ),
                )
                sendStatus(player, session)
            }
            OperationToolMode.ENTITY_RANGE, OperationToolMode.ENTITY_DIRECT -> {
                if (session.editingBox != null) {
                    // Box編集中の左クリックは編集中Boxをキャンセルします。
                    session.cancelBoxEdit()
                    displays.dropEditing(player.uniqueId)
                    lastCandidateCounts.remove(player.uniqueId)
                    return
                }
                // 左クリックで直前の確定済み選択操作をUndoします。
                val removed = session.undoLast()
                if (removed.isEmpty()) {
                    player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_UNDO_EMPTY))
                } else {
                    sendStatus(player, session)
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
        player.sendMessage(
            KcI18n.text(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_POS2_SET,
                mapOf("world" to point.worldName, "x" to block.x, "y" to block.y, "z" to block.z),
            ),
        )
        sendStatus(player, session)
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
            player.sendMessage(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_BOX_TOO_LARGE, mapOf("max" to session.maxBoxEdge.toInt())),
            )
            return
        }
        val candidates = collectOverlapping(player, minimum, maximum)
        session.retainExisting { id -> player.world.entities.any { it.uniqueId == id && it.isValid && !it.isDead } }
        val added = session.addEntities(candidates)
        session.cancelBoxEdit()
        lastCandidateCounts.remove(player.uniqueId)
        if (added.isNotEmpty()) {
            displays.replaceConfirmed(player.uniqueId, box.worldName, minimum, maximum)
            player.sendMessage(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_ADDED, mapOf("count" to added.size)),
            )
        } else {
            displays.replaceConfirmed(player.uniqueId, null, null, null)
            if (session.selected().size >= session.maxEntities) {
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_SELECTION_FULL, mapOf("max" to session.maxEntities)))
            }
        }
        sendStatus(player, session)
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
            if (box.worldName != player.world.name) {
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
                player.sendMessage(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_BOX_TOO_LARGE, mapOf("max" to session.maxBoxEdge.toInt())),
                )
                continue
            }
            displays.showEditing(player.uniqueId, box.worldName, minimum, maximum)
            val count = collectOverlapping(player, minimum, maximum).count { !session.selected().contains(it) }
            if (lastCandidateCounts[player.uniqueId] != count) {
                lastCandidateCounts[player.uniqueId] = count
                sendStatus(player, session)
            }
        }
        if (!active) {
            editTask?.cancel()
            editTask = null
        }
    }

    private fun requireManage(player: Player): Boolean =
        plugin.placementAccess.requireManage(player, player.world.name)

    /** 現在のモード・選択数をActionBarで即時確認できるようにします。 */
    private fun sendStatus(player: Player, session: OperationToolSession) {
        val modeName = KcI18n.text(player, session.mode.displayNameKey())
        player.sendActionBar(
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STATUS, mapOf("mode" to modeName, "count" to session.selected().size))),
        )
    }

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
         * 視線RayとAABBの交差距離を返します。交差しない場合はnullです。
         *
         * 直接選択モードで視点から最も手前のEntityを選ぶために使います。
         * 高精度モデルHit Testは使用しません。
         */
        fun rayAabbDistance(
            origin: SpatialVector,
            direction: SpatialVector,
            minimum: SpatialVector,
            maximum: SpatialVector,
        ): Double? {
            var nearest = Double.NEGATIVE_INFINITY
            var farthest = Double.POSITIVE_INFINITY
            val origins = doubleArrayOf(origin.x, origin.y, origin.z)
            val directions = doubleArrayOf(direction.x, direction.y, direction.z)
            val minimums = doubleArrayOf(minimum.x, minimum.y, minimum.z)
            val maximums = doubleArrayOf(maximum.x, maximum.y, maximum.z)
            for (axis in 0..2) {
                val dir = directions[axis]
                if (kotlin.math.abs(dir) < 1e-9) {
                    if (origins[axis] < minimums[axis] || origins[axis] > maximums[axis]) return null
                    continue
                }
                var near = (minimums[axis] - origins[axis]) / dir
                var far = (maximums[axis] - origins[axis]) / dir
                if (near > far) {
                    val swapped = near
                    near = far
                    far = swapped
                }
                nearest = kotlin.math.max(nearest, near)
                farthest = kotlin.math.min(farthest, far)
                if (nearest > farthest) return null
            }
            if (farthest < 0.0) return null
            return if (nearest >= 0.0) nearest else 0.0
        }

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
