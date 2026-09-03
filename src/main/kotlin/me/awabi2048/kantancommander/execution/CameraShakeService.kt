package me.awabi2048.kantancommander.execution

import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.entity.Player
import org.geysermc.geyser.api.GeyserApi
import org.geysermc.geyser.api.bedrock.camera.CameraShake

/** Geyser接続のBedrockプレイヤーだけへカメラシェイクを送り、Java版では成功no-opにします。 */
object CameraShakeService {
    fun apply(plugin: KantanCommanderPlugin, player: Player, intensity: Float, seconds: Float, type: String) {
        if (!plugin.server.pluginManager.isPluginEnabled("Geyser-Spigot")) return
        val connection = try {
            GeyserApi.api().connectionByUuid(player.uniqueId)
        } catch (error: LinkageError) {
            plugin.logger.warning("[KantanCommander] Geyser APIにアクセスできませんでした: ${error.message}")
            return
        } ?: return
        val kind = if (type == "rotational") CameraShake.ROTATIONAL else CameraShake.POSITIONAL
        connection.camera().shakeCamera(intensity, seconds, kind)
    }
}
