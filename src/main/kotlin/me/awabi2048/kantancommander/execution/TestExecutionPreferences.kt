package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

/** テスト開始確認画面の設定をプレイヤー単位で保持します。未保存時は両方OFFです。 */
class TestExecutionPreferences(plugin: KantanCommanderPlugin) {
    private val debugKey = org.bukkit.NamespacedKey(plugin, "test_debug_mode")
    private val logKey = org.bukkit.NamespacedKey(plugin, "test_log_output")

    fun debugMode(player: Player): Boolean =
        player.persistentDataContainer.get(debugKey, PersistentDataType.BYTE)?.toInt() == 1

    fun logOutput(player: Player): Boolean =
        player.persistentDataContainer.get(logKey, PersistentDataType.BYTE)?.toInt() == 1

    fun save(player: Player, debugMode: Boolean, logOutput: Boolean) {
        val container = player.persistentDataContainer
        container.set(debugKey, PersistentDataType.BYTE, if (debugMode) 1.toByte() else 0.toByte())
        container.set(logKey, PersistentDataType.BYTE, if (logOutput) 1.toByte() else 0.toByte())
    }
}
