package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.DiskPlacement
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

/**
 * ジェスチャーエディターの公開ファサード。
 * 上部ビューポート（ジェスチャーGUI）と下部パネル（インベントリGUIまたは将来のジェスチャーGUI）を統括します。
 */
class GestureEditorFacade(
    val plugin: KantanCommanderPlugin,
) {
    private val sessions = mutableMapOf<UUID, GestureSequenceEditor>()

    fun open(player: Player, placement: DiskPlacement) {
        val scriptId = placement.scriptId
        val world = org.bukkit.Bukkit.getWorld(placement.world)
        val anchor: Location? = if (world != null) {
            // ブロック実体との視線干渉を避け、ジェスチャー画面全体を
            // 基準位置から0.3ブロック上へ配置します。上部・下部の両画面は
            // 同じanchorから姿勢を算出するため、相対位置は変わりません。
            Location(world, placement.x + 0.5, placement.y + 0.5 + GESTURE_DISPLAY_VERTICAL_OFFSET, placement.z + 0.5)
        } else null
        val state = GestureEditorState(
            scriptId = scriptId,
            placement = placement,
            origin = MapPoint(0, 0),
            anchor = anchor,
        )
        val editor = GestureSequenceEditor(plugin, state)
        sessions[player.uniqueId] = editor
        editor.open(player)
    }

    fun open(player: Player, scriptId: UUID) {
        val state = GestureEditorState(
            scriptId = scriptId,
            placement = null,
            origin = MapPoint(0, 0),
            anchor = null,
        )
        val editor = GestureSequenceEditor(plugin, state)
        sessions[player.uniqueId] = editor
        editor.open(player)
    }

    fun handleGestureGesture(player: Player) {
        // 将来的なジェスチャー入力処理
    }

    fun close(player: Player) {
        sessions.remove(player.uniqueId)?.closeImmediately(player.uniqueId)
    }

    /** 配置ブロック破壊時に、その配置を編集中の全プレイヤーを即時終了させます。 */
    fun closeForPlacement(placement: DiskPlacement) {
        sessions.filterValues { it.isEditing(placement) }.keys.toList().forEach { playerId ->
            sessions.remove(playerId)?.closeImmediately(playerId)
        }
    }

    fun closeAll() {
        sessions.keys.toList().forEach { playerId ->
            sessions.remove(playerId)?.closeImmediately(playerId)
        }
    }

    private companion object {
        const val GESTURE_DISPLAY_VERTICAL_OFFSET: Double = 0.3
    }
}
