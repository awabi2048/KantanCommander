package me.awabi2048.kantancommander.item
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.hasDiskContent
import me.awabi2048.kantancommander.placement.PlacedBlockMaterials
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class KantanInteractionListener(private val plugin: KantanCommanderPlugin) : Listener {
    /**
     * EASのSessionListener（通常優先度）より先に、Kantanブロック操作の右クリックを
     * 判定します。EASはPlayerInteractEventがキャンセル済みでもSessionを開始し得るため、
     * HIGH優先度の本処理だけでは「ワンドを持った瞬間のUI」を防げません。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun suppressExternalEditorStart(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.player.isSneaking) return
        val clicked = event.clickedBlock ?: return
        if (plugin.placements.find(clicked.location) == null) return
        if (event.hand != EquipmentSlot.HAND) {
            // 右クリックはメインハンドだけをKantanの編集入口として扱います。
            // オフハンドの同一入力は、EASやバニラのブロック操作へ漏らしません。
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
            event.isCancelled = true
            return
        }
        plugin.gestureEditor.prepareExternalEditorSuppression(event.player)
        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.gestureEditor.releaseExternalEditorSuppressionIfClosed(event.player.uniqueId)
        })
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item
        val itemKind = KantanItemService.kind(item)
        val diskId = KantanItemService.diskId(item)
        val clickedPlacement = event.clickedBlock?.let { plugin.placements.find(it.location) }

        if (
            event.action == Action.RIGHT_CLICK_BLOCK &&
            event.hand != EquipmentSlot.HAND &&
            clickedPlacement != null
        ) {
            // PlayerInteractEventは手ごとに発火するため、オフハンド側では編集／書き込みを
            // 一切再評価しません。LOWESTのEAS抑制と同じく、外部のブロック使用も止めます。
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY)
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY)
            event.isCancelled = true
            return
        }

        if (itemKind == KantanItemKind.DISK && event.action == Action.RIGHT_CLICK_BLOCK && clickedPlacement == null) {
            // ディスク単体から編集画面を開く導線を廃止します。配置物を正しく
            // 解決できない右クリックでは、通常ブロックのGUIへフォールバックしません。
            event.isCancelled = true
            return
        }

        if (
            clickedPlacement != null &&
            event.action == Action.RIGHT_CLICK_BLOCK &&
            !player.isSneaking
        ) {
            // 拡張コマンドブロックを手に持った右クリックは通常のブロック配置を優先し、編集画面を開かない。
            if (itemKind == KantanItemKind.BLOCK) return
            // GUIを開く経路はイベントをキャンセルするため、バニラが送る腕振りが
            // 発生しません。実際に編集／ディスク挿入を受け付ける右クリックだけ、
            // 使用した手に対応するスイングを明示的に送って操作の成立を見せます。
            if (event.hand == EquipmentSlot.OFF_HAND) {
                player.swingOffHand()
            } else {
                player.swingMainHand()
            }
            event.isCancelled = true
            val canManagePlacement = plugin.placementAccess.canManage(player, clickedPlacement.world)
            // 既存のGesture画面を操作するだけの第三者には、設置・破壊・
            // ディスク書き込みを許可しません。共有GUIへの参加経路だけを
            // 建築権限から分離します。
            val canOperateExistingGesture =
                itemKind != KantanItemKind.DISK &&
                    plugin.gestureEditor.canOperateExisting(player, clickedPlacement)
            if (!canManagePlacement && !canOperateExistingGesture) {
                player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PLACEMENT_ACCESS))
                return
            }
            if (canOperateExistingGesture && !canManagePlacement) return
            // プログラムディスク挿入は常に既存の書き込み確認へ送り、Gesture GUIを開かない。
            if (itemKind == KantanItemKind.DISK) {
                val diskScriptId = diskId ?: return
                plugin.editorMenu.openWriteConfirm(player, clickedPlacement, diskScriptId)
                return
            }
            if (plugin.config.getBoolean("use-gesture-editor", false)) {
                plugin.gestureEditor.open(player, clickedPlacement)
                return
            }
            plugin.editorMenu.open(player, clickedPlacement)
            return
        }

        when (KantanItemPolicy.itemAction(itemKind, event.action, player.isSneaking)) {
            KantanItemAction.NONE -> return
            KantanItemAction.OPEN -> {
                val script = diskId?.let(plugin.scripts::load) ?: return
                event.isCancelled = true
                plugin.editorMenu.open(player, script.id)
            }
        }
    }

    /**
     * 拡張コマンドブロックの設置。バニラのブロック配置イベントを捕捉して配置物へ変換する。
     *
     * 元のイベントはキャンセルせず、バニラの配置確定とアイテム消費に任せる。
     * キャンセルするとバニラが配置位置を巻き戻し、ガラス実体だけが消えてBlockDisplayが残るため。
     * 保護プラグインには先に判定させるため優先度はHIGHESTとする。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (KantanItemService.kind(event.itemInHand) != KantanItemKind.BLOCK) return
        val player = event.player
        if (!plugin.placementAccess.canManage(player, player.world.name)) {
            event.isCancelled = true
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PLACEMENT_ACCESS))
            plugin.logger.info("拡張コマンドブロックの設置を権限不足で拒否: player=${player.name}, world=${player.world.name}")
            return
        }
        plugin.logger.info("拡張コマンドブロックの設置を検出: player=${player.name}, location=${event.block.location}, material=${event.block.type}")
        // バニラの配置イベントが配置位置を保証済み（置換可能性・プレイヤー重なりもバニラが検証済み）のため、
        // 自前の再判定は行わず、既存配置物の重複と保護判定だけを追加確認する。
        // 失敗時は元イベントをキャンセルし、バニラの巻き戻しで配置位置を元へ戻す。
        if (!placeBlock(player, event.block.location, event.hand)) {
            event.isCancelled = true
        }
    }

    /** 拡張コマンドブロックの破壊。管理権限があれば内容をプログラムディスクとして出力して撤去する。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val placement = plugin.placements.find(block.location) ?: return
        if (!plugin.placementAccess.canManage(event.player, placement.world)) {
            event.isCancelled = true
            event.player.sendMessage(KcI18n.text(event.player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_NO_PLACEMENT_ACCESS))
            plugin.logger.info("拡張コマンドブロックの破壊を権限不足で拒否: player=${event.player.name}, location=${block.location}")
            return
        }
        event.isCancelled = true
        // 編集中の表示Entity・入力クレームを先に解放し、破壊後に古い画面が残らないようにします。
        plugin.gestureEditor.closeForPlacement(placement)
        outputDiskAndRemove(event.player, block, placement)
    }

    /**
     * 拡張コマンドブロックを配置する。成功時はtrueを返し、失敗時はfalseを返して
     * 呼び出し側が元のBlockPlaceEventをキャンセルし、バニラの巻き戻しに任せる。
     */
    private fun placeBlock(
        player: Player,
        location: Location,
        hand: EquipmentSlot,
    ): Boolean {
        val block = location.block
        // 通常のブロックと同じ体験にするため、失敗時もメッセージを出さず何も起きない扱いにする。
        // 原因の追跡用に、失敗の理由を常時サーバーログへ記録する。
        // バニラが配置位置を保証済みのため置換可能性は再判定せず、Kantan独自の重複チェックだけ行う。
        if (plugin.placements.find(block.location) != null) {
            plugin.logger.info("既存の配置物があるため設置せず: location=${block.location}, material=${block.type}")
            return false
        }

        val targetBox = org.bukkit.util.BoundingBox.of(
            block.location.toVector(),
            block.location.clone().add(1.0, 1.0, 1.0).toVector(),
        )
        if (player.boundingBox.overlaps(targetBox)) {
            plugin.logger.info("プレイヤーと重なる位置のため設置せず: player=${player.name}, location=${block.location}")
            return false
        }

        // 配置直後の初期名は設定値ではなく作成者を明示します。ライブラリ／履歴から
        // 複製した後も、元の作成者とプログラム名を識別できるようにするためです。
        val placedScript = plugin.scripts.createPlacement(
            player.uniqueId,
            "${player.name}のプログラム",
        )
        val material = PlacedBlockMaterials.forTimer(placedScript.timer.enabled)
        if (!block.canPlace(Bukkit.createBlockData(material))) {
            plugin.scripts.delete(placedScript.id)
            plugin.logger.info(
                "ブロック状態が設置不可のため設置せず: location=${block.location}, " +
                    "target=${block.type}, material=$material",
            )
            return false
        }
        // バニラの一時設置（infested_stone）を実体のガラスへ置き換える。
        // 元のBlockPlaceEventはキャンセルしないため、バニラの巻き戻しで実体が消えることはない。
        block.setType(material, false)
        val placement = DiskPlacement(
            block.world.name, block.x, block.y, block.z, placedScript.id,
            player.facing.name, null
        )
        runCatching {
            plugin.placements.add(placement)
            plugin.placements.spawnDisplay(block.world, placement)
        }.onFailure { error ->
            runCatching { plugin.placements.remove(block.world, block.x, block.y, block.z) }
                .onFailure { cleanupError ->
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "設置失敗後の配置台帳クリーンアップにも失敗しました: location=${block.location}",
                        cleanupError,
                    )
                }
            runCatching { plugin.scripts.delete(placedScript.id) }
                .onFailure { cleanupError ->
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "設置失敗後のスクリプトクリーンアップにも失敗しました: script=${placedScript.id}",
                        cleanupError,
                    )
                }
            plugin.logger.log(
                java.util.logging.Level.INFO,
                "配置データの保存または表示体スポーンに失敗したため設置を中止: location=${block.location}",
                error,
            )
            return false
        }
        // 配置音はバニラが元イベントの成功時に再生する。設置時のパーティクルはバニラ同様に出さない。
        // 後続リスナー（保護プラグイン等）が元イベントをキャンセルして巻き戻した場合に備え、
        // 次のtickで実体が残っているかを確認し、残っていなければ配置を撤去する。
        scheduleGhostCheck(placement, block.location)
        plugin.logger.info("拡張コマンドブロックを設置: placement=${placement.key}, script=${placedScript.id}")
        return true
    }

    /** 設置直後の実体消失（後続リスナーによるキャンセル巻き戻し）を検出して配置を撤去する。 */
    private fun scheduleGhostCheck(placement: DiskPlacement, location: Location) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val world = plugin.server.getWorld(placement.world) ?: return@Runnable
            val block = world.getBlockAt(placement.x, placement.y, placement.z)
            if (PlacedBlockMaterials.isPlacedBlock(block.type)) return@Runnable
            plugin.logger.info(
                "設置直後に配置ブロックが消失したため配置を撤去: placement=${placement.key}, material=${block.type}",
            )
            // 配置が外部要因で消えた場合も、編集中のEntity・入力claim・Dialogを
            // 先に閉じて、削除済みスクリプトへ遅延入力が届かないようにします。
            plugin.gestureEditor.closeForPlacement(placement)
            // 実体が先に消えた場合は、対象側へ残った特殊接続を台帳の現状態に
            // 基づいて解除します。以降の台帳削除が失敗しても、ガラスではないため
            // 通常のバニラ接続へ戻せます。
            plugin.refreshRedstoneTopologyAround(block)
            val removed = runCatching {
                plugin.placements.remove(world, placement.x, placement.y, placement.z)
            }.getOrElse { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "消失した配置の台帳撤去に失敗しました: placement=${placement.key}",
                    failure,
                )
                return@Runnable
            } ?: return@Runnable
            plugin.placements.removeDisplay(world, removed.displayId)
            runCatching { plugin.scripts.delete(removed.scriptId) }
                .onFailure { failure ->
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "消失した配置のスクリプト削除に失敗しました: placement=${placement.key}",
                        failure,
                    )
                }
        })
    }

    /** 破壊時の後始末。内容をプログラムディスクとして出力し、表示・配置・スクリプトを削除する。 */
    private fun outputDiskAndRemove(player: Player, block: Block, placement: DiskPlacement) {
        val world = block.world
        // 破壊音・パーティクルは、イベントキャンセル下でもバニラがクライアントへ送信するため自前では再生しない。
        val source = plugin.scripts.load(placement.scriptId)
        var outputScript: me.awabi2048.kantancommander.model.DiskScript? = null
        var outputItem: org.bukkit.inventory.ItemStack? = null
        // ノードがなくても、名前またはタイマーを明示編集したスクリプトは出力します。
        if (source?.hasDiskContent() == true) {
            val copiedScript = runCatching { plugin.scripts.copyForItem(source) }.getOrElse { error ->
                plugin.logger.log(
                    java.util.logging.Level.INFO,
                    "破壊時のスクリプト複製に失敗したため、内容を出力せず破壊: location=${block.location}, script=${placement.scriptId}",
                    error,
                )
                null
            }
            if (copiedScript != null) {
                outputScript = copiedScript
                outputItem = runCatching { KantanItemService.createDisk(copiedScript, player) }.getOrElse { error ->
                    runCatching { plugin.scripts.delete(copiedScript.id) }
                    plugin.logger.log(
                        java.util.logging.Level.INFO,
                        "破壊時のプログラムディスク生成に失敗: location=${block.location}, script=${copiedScript.id}",
                        error,
                    )
                    outputScript = null
                    null
                }
            }
        } else if (source == null) {
            plugin.logger.info("参照スクリプトが消失しているため、内容を出力せず破壊: location=${block.location}, script=${placement.scriptId}")
        } else {
            plugin.logger.info("内容が空のためディスクを出力せず破壊: location=${block.location}, script=${placement.scriptId}")
        }
        // 配置台帳を先に保存します。表示体やブロックを先に消すと、保存失敗時に
        // ワールド上だけ消えた「幽霊配置」になり、再起動後の復元対象と一致しません。
        val removed = runCatching {
            plugin.placements.remove(world, placement.x, placement.y, placement.z)
        }.getOrElse { failure ->
            outputScript?.let { runCatching { plugin.scripts.delete(it.id) } }
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "配置台帳の削除に失敗したため破壊を中止: location=${block.location}, placement=${placement.key}",
                failure,
            )
            return
        } ?: run {
            outputScript?.let { runCatching { plugin.scripts.delete(it.id) } }
            plugin.logger.warning("配置台帳が既に削除されているため破壊を中止: location=${block.location}")
            return
        }
        plugin.placements.removeDisplay(world, removed.displayId)
        block.setType(Material.AIR, false)
        // ブロックを物理更新なしで消すため、隣接ダストに残る拡張ブロック向けの
        // 特殊接続を明示的にバニラ形状へ戻します。
        plugin.refreshRedstoneTopologyAround(block)
        if (source != null) {
            runCatching { plugin.scripts.delete(source.id) }
                .onFailure { failure ->
                    plugin.logger.log(
                        java.util.logging.Level.WARNING,
                        "配置撤去後のスクリプト削除に失敗しました: script=${source.id}, placement=${placement.key}",
                        failure,
                    )
                }
        }
        outputItem?.let { item ->
            player.inventory.addItem(item).values.forEach { world.dropItemNaturally(player.location, it) }
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_DISK_OUTPUT))
            plugin.logger.info("破壊時のディスク出力完了: location=${block.location}, script=${outputScript?.id}")
        }
        plugin.logger.info("拡張コマンドブロックの破壊処理完了: placement=${placement.key}")
    }
}
