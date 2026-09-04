package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.myworldmanager.api.event.MwmWorldDeletedEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class WorldVariableLifecycleListener(
    private val plugin: KantanCommanderPlugin,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldUnload(event: WorldUnloadEvent) {
        // 実ワールドへ作用するテストを、アンロード後のEntity／Schedulerへ
        // ぶら下げたままにしないため、このワールドに属するセッションだけを停止します。
        plugin.testExecution.abortForWorld(event.world.uid)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldDeleted(event: MwmWorldDeletedEvent) {
        plugin.placements.removeWorld(event.worldName).forEach { placement ->
            plugin.testExecution.cancel(placement.key, showResult = false, reason = "world_removed")
            // ワールド削除はBlockBreakEventを経由しないため、ここでも共有Gesture画面を
            // 先に終了させます。残った入力画面の入力だけが後から到着すると、削除済み
            // ノード／スクリプトへ保存しようとして誤警告や再生成を起こします。
            plugin.gestureEditor.closeForPlacement(placement)
            plugin.scripts.delete(placement.scriptId)
        }
        if (!plugin.variables.deleteWorld(event.worldUuid)) {
            plugin.logger.severe(
                "[KantanCommander] MyWorld削除後のワールド内変数ファイルを削除できませんでした: ${event.worldUuid}"
            )
        }
    }
}
