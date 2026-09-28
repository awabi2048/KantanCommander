package me.awabi2048.kantancommander.operationtool

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.KantanItemKind
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.RegionMath
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
 * wand所持中のクリックは世界へ作用させず、常にキャンセルして選択操作にします。
 * 操作体系は「左クリック＝メニュー起動 / 右クリック＝選択操作 / Fキー＝選択解除 /
 * スニーク+スクロール＝モード切替」で統一し、選択済みEntityは操作者へだけ発光表示します。
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
    private val glow = OperationToolGlow(plugin)

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

    /** 発光表示のパケット連携を開始します。プラグイン有効化時に呼び出します。 */
    fun start() {
        glow.start()
    }

    /** 編集中Boxの追従タスクを停止します。プラグイン無効化時に呼び出します。 */
    fun stop() {
        editTask?.cancel()
        editTask = null
        glow.stop()
        lastCandidateCounts.clear()
        entityClickTicks.clear()
        displays.removeAll()
        sessions.clear()
    }

    /**
     * 選択Entityの発光表示を選択集合へ同期します。
     *
     * メニュー側の消失判定等で選択集合が縮んだ場合にも追従させるため、
     * 選択を変更した全経路から呼び出します。
     */
    fun syncGlow(player: Player) {
        glow.sync(player, sessions.get(player.uniqueId)?.selected() ?: emptySet())
    }

    /**
     * ツール表示を除外した操作可能判定です。
     *
     * [isOperable] 本体は注入可能な共有predicateとして残し、範囲表示の
     * Entityが選択候補へ混ざらないよう、実使用側ではこちらを呼びます。
     */
    private fun isOperableTarget(operator: Player, entity: Entity): Boolean =
        isOperable(operator, entity) &&
            !displays.isToolDisplay(entity.uniqueId)

    // wand所持中の掘削・破壊は選択操作と誤認されるため抑止します。
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockDamage(event: BlockDamageEvent) {
        if (KantanItemService.kind(event.player.inventory.itemInMainHand) == KantanItemKind.WAND) {
            event.isCancelled = true
        }
    }

    // KantanInteractionListener.onBlockBreak（HIGH）は制御ブロックを撤去して出力するため、
    // wand所持中はそれより先（LOWEST）に破壊を抑止します。左クリック＝メニュー起動の
    // 操作体系で、制御ブロックが誤撤去されないようにするための優先度です。
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (KantanItemService.kind(event.player.inventory.itemInMainHand) == KantanItemKind.WAND) {
            event.isCancelled = true
        }
    }

    /**
     * wand所持中のEntity攻撃は対象を破壊し得るため抑止し、左クリック＝メニュー起動
     * の操作体系に合わせてEntity操作GUIを開きます。
     */
    // GestureGuiInputListener（CC-System・LOWEST）が追従画面のセッション中に
    // スニーク右クリックや攻撃・腕振りを消費するため、wandの操作は
    // ignoreCancelled=falseで受け取ります。先にキャンセル済みでも選択操作と
    // メニュー起動だけを実行し、イベントはキャンセルのまま維持します。
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onAttack(event: PrePlayerAttackEntityEvent) {
        val player = event.player
        if (KantanItemService.kind(player.inventory.itemInMainHand) != KantanItemKind.WAND) return
        event.isCancelled = true
        if (!requireManage(player)) return
        val session = sessions.getOrCreate(player.uniqueId)
        if (session.editingBox != null) {
            // Box編集中の左クリックは編集キャンセルです。Entityへの左クリックは
            // PlayerInteractEventを通らず攻撃イベントになるため、ここでも揃えます。
            session.cancelBoxEdit()
            displays.dropEditing(player.uniqueId)
            lastCandidateCounts.remove(player.uniqueId)
            OperationToolFeedback.showStatus(player, session)
            return
        }
        when (session.mode) {
            OperationToolMode.AREA -> {
                if (session.hasValidArea()) {
                    plugin.operationMenu.openArea(player)
                } else {
                    // 選択前のEntity左クリックは、そのEntityのタグ一覧をチャットへ出します。
                    plugin.operationMenu.sendEntityTags(player, event.attacked)
                }
            }
            OperationToolMode.ENTITY_RANGE, OperationToolMode.ENTITY_DIRECT -> {
                if (session.selected().isEmpty()) {
                    plugin.operationMenu.sendEntityTags(player, event.attacked)
                } else {
                    plugin.operationMenu.openEntity(player)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
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
        // かんたんコマンダー制御ブロックへの右クリックはwand操作としません。
        // 非スニークはKantanInteractionListenerが編集入口として先に消費し、
        // スニークはバニラの使用・配置へ素通しさせます。
        if (event.action == Action.RIGHT_CLICK_BLOCK &&
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
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
        // Entity右クリックはモードに応じた選択操作です。直後のuse_item由来
        // PlayerInteractEventはentityClickTicksで抑止済みのため、ここで
        // 右クリックの役割をすべて処理します。高精度モデルHit Testは使用しません。
        when (session.mode) {
            // 直接選択モードでは対象Entityへの右クリックが選択トグルです。
            OperationToolMode.ENTITY_DIRECT -> {
                if (isOperableTarget(player, event.rightClicked)) {
                    toggleDirectSelection(player, session, event.rightClicked)
                }
            }
            // 範囲モードではEntityを向いた右クリックでもBox編集を開始・確定します。
            // これをonInteract側だけに任せると、Entityを狙ったクリックが抑止されて
            // 完了できないため、Entity側でも同じ操作を実行します。
            OperationToolMode.ENTITY_RANGE -> {
                if (session.editingBox != null) {
                    confirmBox(player, session)
                } else if (player.isSneaking) {
                    startBoxEdit(player, session)
                }
            }
            // エリアモードではEntity右クリックを吸収します（pos2の誤設定を防ぐ）。
            OperationToolMode.AREA -> Unit
        }
        event.isCancelled = true
    }

    /**
     * 直接選択モードのEntity右クリックを選択追加・除外のトグルとして扱います。
     */
    private fun toggleDirectSelection(player: Player, session: OperationToolSession, target: Entity) {
        if (session.removeEntity(target.uniqueId)) {
            syncGlow(player)
            OperationToolFeedback.notifyKey(
                player, session,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_REMOVED,
                emptyMap(),
                NamedTextColor.YELLOW,
            )
            return
        }
        val added = session.addEntities(listOf(target.uniqueId))
        if (added.isEmpty() && session.selected().size >= session.maxEntities) {
            OperationToolFeedback.notifyKey(
                player, session,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_SELECTION_FULL,
                mapOf("max" to session.maxEntities),
                NamedTextColor.RED,
            )
        } else if (added.isNotEmpty()) {
            syncGlow(player)
            OperationToolFeedback.showStatus(player, session)
        }
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
            glow.clear(player.uniqueId)
            lastCandidateCounts.remove(player.uniqueId)
            // モード切替のチャット通知は行わず、ActionBarの状態表示のみ残します。
            OperationToolFeedback.showStatus(player, session)
            event.isCancelled = true
            return
        }
        cancelEditingIfHandLeft(player, player.inventory.getItem(event.newSlot))
    }

    /**
     * Qキーによるwand投げ捨てを、AREAモードの構造物原点設定へ転用します。
     *
     * 有効なエリア選択中は投げ捨てを抑止し、領域内を向いた視線先ブロックを
     * 原点として設定します。視線先が届かない・領域外の場合は、プレイヤーが
     * 領域内にいればその位置を原点にします。エリア未選択時は従来どおり
     * 投げ捨てを許可し、手から離れたなら編集中Boxを解除します。
     * インベントリ確定がイベントより遅れる場合があるため、確認は1tick後です。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (KantanItemService.kind(event.itemDrop.itemStack) != KantanItemKind.WAND) return
        val player = event.player
        val session = sessions.get(player.uniqueId)
        if (session != null && session.mode == OperationToolMode.AREA && session.hasValidArea()) {
            event.isCancelled = true
            setStructureOrigin(player, session)
            return
        }
        val playerId = player.uniqueId
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val online = plugin.server.getPlayer(playerId) ?: return@Runnable
            cancelEditingIfHandLeft(online, online.inventory.itemInMainHand)
        }, 1L)
    }

    /**
     * Qキー入力から構造物の原点を解決して設定します。
     *
     * 視線先ブロックが領域内ならそのブロック、視線が届かない・領域外なら
     * プレイヤーが領域内にいる場合だけその位置を原点にします。
     */
    private fun setStructureOrigin(player: Player, session: OperationToolSession) {
        val bounds = session.areaBounds() ?: return
        val hit = runCatching {
            player.rayTraceBlocks(ORIGIN_RAY_DISTANCE, org.bukkit.FluidCollisionMode.NEVER)?.hitBlock
        }.getOrNull()
        val origin = when {
            hit != null && RegionMath.contains(
                SpatialVector(hit.x.toDouble(), hit.y.toDouble(), hit.z.toDouble()),
                bounds.minimum, bounds.dx, bounds.dy, bounds.dz,
            ) -> SpatialVector(hit.x.toDouble(), hit.y.toDouble(), hit.z.toDouble())
            RegionMath.contains(
                player.location.toSpatialVector(),
                bounds.minimum, bounds.dx, bounds.dy, bounds.dz,
            ) -> SpatialVector(
                player.location.blockX.toDouble(),
                player.location.blockY.toDouble(),
                player.location.blockZ.toDouble(),
            )
            else -> null
        }
        if (origin == null) {
            OperationToolFeedback.notifyKey(
                player, session,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_ORIGIN_FAILED,
                emptyMap(),
                NamedTextColor.RED,
            )
            return
        }
        session.setOrigin(RegionPoint(player.world.name, origin))
        OperationToolFeedback.notifyKey(
            player, session,
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_ORIGIN_SET,
            mapOf("x" to origin.x.toInt(), "y" to origin.y.toInt(), "z" to origin.z.toInt()),
            NamedTextColor.AQUA,
        )
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
     * メインハンドにwandがある状態でのFキーは「選択解除」です。
     *
     * 入替イベント自体をキャンセルしてwandをメインハンドへ固定し、
     * Fキーを専用の解除ボタンとして扱います。wandがオフハンド側にある
     * 場合の入替はメインハンドへ戻す通常操作として通します。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        if (KantanItemService.kind(event.offHandItem) != KantanItemKind.WAND) return
        event.isCancelled = true
        val player = event.player
        val session = sessions.get(player.uniqueId) ?: return
        if (!session.hasSelectionState()) return
        session.clearAll()
        displays.clear(player.uniqueId)
        glow.clear(player.uniqueId)
        lastCandidateCounts.remove(player.uniqueId)
        OperationToolFeedback.notifyKey(
            player, session,
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_SELECTION_CLEARED,
            emptyMap(),
            NamedTextColor.YELLOW,
        )
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
        // 発光はパケット偽装のため、次回ログイン時の同期で復元します。
        sessions.get(event.player.uniqueId)?.cancelBoxEdit()
        displays.clear(event.player.uniqueId)
        glow.clear(event.player.uniqueId)
        lastCandidateCounts.remove(event.player.uniqueId)
        entityClickTicks.remove(event.player.uniqueId)
        plugin.operationMenu.clearPendingDialog(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKick(event: PlayerKickEvent) {
        sessions.get(event.player.uniqueId)?.cancelBoxEdit()
        displays.clear(event.player.uniqueId)
        glow.clear(event.player.uniqueId)
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
        glow.clear(event.player.uniqueId)
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
        // ログアウトで破棄された表示のうち、同ワールドのエリア指定と
        // 選択Entityの発光を復元します。
        val playerId = event.player.uniqueId
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val player = plugin.server.getPlayer(playerId) ?: return@Runnable
            val session = sessions.get(playerId) ?: return@Runnable
            updateAreaDisplay(player, session)
            syncGlow(player)
        }, 1L)
    }

    /**
     * 左クリックは、選択が完了している場合だけメニュー起動の操作です。
     *
     * AREAモードではブロックへの通常左クリックがpos1設定で、選択完了後の
     * pos1再指定だけがスニーク+左クリックです。選択が済んでいない間は
     * メニューを開かず操作案内だけを通知します。例外は、Box編集中の
     * 左クリック（編集キャンセル）と、未選択時のEntity左クリック
     * （[onAttack] で対象のタグ一覧をチャットへ出す）です。
     */
    private fun onLeftClick(player: Player, session: OperationToolSession, event: PlayerInteractEvent) {
        when (session.mode) {
            OperationToolMode.AREA -> {
                val block = if (event.action == Action.LEFT_CLICK_BLOCK) event.clickedBlock else null
                // pos1は通常左クリックで指定します。選択済みの再指定だけスニーク+左クリックです。
                if (block != null && (!session.hasValidArea() || player.isSneaking)) {
                    setPos1(player, session, block)
                    return
                }
                if (!session.hasValidArea()) {
                    OperationToolFeedback.notifyKey(
                        player, session,
                        KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_AREA_GUIDE,
                        emptyMap(),
                        NamedTextColor.YELLOW,
                    )
                    return
                }
                plugin.operationMenu.openArea(player)
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
                if (session.selected().isEmpty()) {
                    OperationToolFeedback.notifyKey(
                        player, session,
                        KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_NO_SELECTION,
                        emptyMap(),
                        NamedTextColor.YELLOW,
                    )
                    return
                }
                plugin.operationMenu.openEntity(player)
            }
        }
    }

    private fun onRightClick(player: Player, session: OperationToolSession, event: PlayerInteractEvent) {
        when (session.mode) {
            OperationToolMode.AREA -> {
                // 右クリックしたBlockをpos2として設定します。完了後も常に再指定できます。
                if (event.action != Action.RIGHT_CLICK_BLOCK) return
                val block = event.clickedBlock ?: return
                val point = RegionPoint(block.world.name, block.location.toSpatialVector())
                session.setPos2(point)
                OperationToolFeedback.notifyKey(
                    player, session,
                    KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_POS2_SET,
                    mapOf("x" to block.x, "y" to block.y, "z" to block.z),
                    NamedTextColor.AQUA,
                )
                updateAreaDisplay(player, session)
            }
            OperationToolMode.ENTITY_RANGE -> onRangeRightClick(player, session)
            // 直接選択モードの右クリックはEntity側の [onInteractEntity] で処理します。
            // 空気・ブロックへの右クリックは操作なしです。
            OperationToolMode.ENTITY_DIRECT -> Unit
        }
    }

    private fun setPos1(player: Player, session: OperationToolSession, block: org.bukkit.block.Block) {
        val point = RegionPoint(block.world.name, block.location.toSpatialVector())
        session.setPos1(point)
        OperationToolFeedback.notifyKey(
            player, session,
            KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_POS1_SET,
            mapOf("x" to block.x, "y" to block.y, "z" to block.z),
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
            startBoxEdit(player, session)
        }
        // メニュー起動は左クリックへ統一したため、範囲モードの右クリックは
        // Box編集の開始・確定だけを担当します。
    }

    /**
     * スニーク + 右クリックでBox選択を開始します。
     *
     * 空気・ブロックへの右クリックとEntityへの右クリックの双方から呼ばれます。
     */
    private fun startBoxEdit(player: Player, session: OperationToolSession) {
        val anchor = eyePoint(player, boxReach)
        session.startBoxEdit(EditingBox(player.world.name, anchor, anchor))
        lastCandidateCounts[player.uniqueId] = 0
        ensureEditTask()
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
        syncGlow(player)
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
            isOperableTarget(player, entity) &&
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
        /**
         * Qキー原点設定で視線先ブロックを探す距離です。
         * これを超えると視線外とみなし、領域内のプレイヤー位置へフォールバックします。
         */
        const val ORIGIN_RAY_DISTANCE = 64.0

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
