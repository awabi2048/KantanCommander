package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.permissions.PermissionAttachment
import java.util.UUID

/**
 * ジェスチャーエディターの公開ファサード。
 * 上部ビューポート（ジェスチャーGUI）と下部パネル（インベントリGUIまたは将来のジェスチャーGUI）を統括します。
 */
class GestureEditorFacade(
    val plugin: KantanCommanderPlugin,
) {
    private val sessions = mutableMapOf<UUID, GestureSequenceEditor>()
    private val externalEditorSuppressions = mutableMapOf<UUID, PermissionAttachment>()

    fun open(player: Player, placement: DiskPlacement) {
        val scriptId = placement.scriptId
        val world = org.bukkit.Bukkit.getWorld(placement.world)
        val anchor: Location? = if (world != null) {
            // ブロック実体との視線干渉を避け、ジェスチャー画面全体を
            // 基準位置から合計0.8ブロック上へ配置します（従来の0.3ブロックに
            // 今回の追加0.5ブロックを加算）。上部・下部の両画面は
            // 同じanchorから姿勢を算出するため、相対位置は変わりません。
            Location(world, placement.x + 0.5, placement.y + 0.5 + GESTURE_DISPLAY_VERTICAL_OFFSET, placement.z + 0.5)
        } else null
        val state = GestureEditorState(
            scriptId = scriptId,
            placement = placement,
            origin = MapPoint(0, 0),
            anchor = anchor,
        )
        openEditor(player, state)
    }

    fun open(player: Player, scriptId: UUID) {
        val state = GestureEditorState(
            scriptId = scriptId,
            placement = null,
            origin = MapPoint(0, 0),
            anchor = null,
        )
        openEditor(player, state)
    }

    /**
     * 同一プレイヤーの旧エディターを先に終了し、新エディターはopen成功後だけ登録します。
     * 先にMapを上書きすると、open失敗時に実体のないセッションが残り、旧終了通知が
     * 新エディターを誤って削除するためです。
     */
    private fun openEditor(player: Player, state: GestureEditorState) {
        sessions.remove(player.uniqueId)?.let {
            it.closeImmediately(player.uniqueId)
            releaseExternalEditorSuppression(player.uniqueId)
        }
        installExternalEditorSuppression(player)
        val editor = GestureSequenceEditor(plugin, state, ::onSessionClosed)
        val opened = runCatching {
            editor.open(player)
            true
        }.getOrElse { failure ->
            releaseExternalEditorSuppression(player.uniqueId)
            // PlayerInteractEventから呼ばれる編集入口で例外を再送すると、ブロック操作
            // イベント全体がサーバーERRORになり、次のクリックまで同じ競合を繰り返します。
            // 入力claim競合はCC-System側で所有者を保護したうえで失敗するため、ここでは
            // 画面を開けなかった事実をログへ残し、イベントへ例外を漏らしません。
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "Gesture GUIを開けませんでした: player=${player.uniqueId}, script=${state.scriptId}",
                failure,
            )
            false
        }
        if (opened) sessions[player.uniqueId] = editor
    }

    /** 旧セッションの終了通知が同一プレイヤーの新セッションへ干渉しないよう照合します。 */
    private fun onSessionClosed(editor: GestureSequenceEditor, ownerId: UUID, sessionId: UUID) {
        if (sessions[ownerId] === editor && editor.isCurrentSession(sessionId)) {
            sessions.remove(ownerId)
            releaseExternalEditorSuppression(ownerId)
        }
    }

    fun handleGestureGesture(player: Player) {
        // 将来的なジェスチャー入力処理
    }

    fun close(player: Player) {
        sessions.remove(player.uniqueId)?.closeImmediately(player.uniqueId)
        releaseExternalEditorSuppression(player.uniqueId)
    }

    /** 配置ブロック破壊時に、その配置を編集中の全プレイヤーを即時終了させます。 */
    fun closeForPlacement(placement: DiskPlacement) {
        sessions.filterValues { it.isEditing(placement) }.keys.toList().forEach { playerId ->
            sessions.remove(playerId)?.closeImmediately(playerId)
            releaseExternalEditorSuppression(playerId)
        }
    }

    fun closeAll() {
        sessions.keys.toList().forEach { playerId ->
            sessions.remove(playerId)?.closeImmediately(playerId)
            releaseExternalEditorSuppression(playerId)
        }
    }

    /**
     * Kantanの編集入口を処理するイベントより前に呼び出し、EASの同一右クリックから
     * 新しいSessionが開始されるのを防ぎます。EASにはキャンセル済みイベントを無視して
     * 所持ワンドと権限だけで開始する経路があるため、イベントキャンセルより先に必要です。
     */
    internal fun prepareExternalEditorSuppression(player: Player) {
        installExternalEditorSuppression(player)
    }

    /** 開始判定用の一時抑制を、Gestureエディターが開かなかった場合だけ解除します。 */
    internal fun releaseExternalEditorSuppressionIfClosed(playerId: UUID) {
        if (playerId !in sessions) releaseExternalEditorSuppression(playerId)
    }

    /**
     * EASはPlayerInteractEventがキャンセル済みでも、所持ワンドと権限だけで
     * Sessionを開始します。そのため、Gesture GUIの開始前に編集権限を一時的に
     * 拒否し、終了時に必ずAttachmentを外して元の権限へ戻します。Axiom側は
     * Display GizmoをCC-Systemが個別に隠すため、ここではEASの開始条件だけを扱います。
     */
    private fun installExternalEditorSuppression(player: Player) {
        if (!plugin.server.pluginManager.isPluginEnabled(EASY_ARMOR_STANDS_PLUGIN)) return
        releaseExternalEditorSuppression(player.uniqueId)
        val attachment = player.addAttachment(plugin)
        attachment.setPermission(EAS_EDIT_PERMISSION, false)
        externalEditorSuppressions[player.uniqueId] = attachment
    }

    private fun releaseExternalEditorSuppression(playerId: UUID) {
        val attachment = externalEditorSuppressions.remove(playerId) ?: return
        val player = org.bukkit.Bukkit.getPlayer(playerId) ?: return
        runCatching { player.removeAttachment(attachment) }
            .onFailure { failure ->
                plugin.logger.warning("外部エンティティ編集権限の復元に失敗しました: player=$playerId: ${failure.message}")
            }
    }

    private companion object {
        const val GESTURE_DISPLAY_VERTICAL_OFFSET: Double = 0.8
        const val EASY_ARMOR_STANDS_PLUGIN = "EasyArmorStands"
        const val EAS_EDIT_PERMISSION = "easyarmorstands.edit"
    }
}
