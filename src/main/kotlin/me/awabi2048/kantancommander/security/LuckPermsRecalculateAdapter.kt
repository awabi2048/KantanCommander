package me.awabi2048.kantancommander.security

import org.bukkit.plugin.Plugin
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.function.Consumer
import java.util.logging.Logger

/**
 * LuckPermsの権限再計算を制御ブロック表示の再評価へ接続します。
 *
 * LuckPermsをKantanCommanderの必須依存にしないため、API参照はリフレクションへ
 * 隔離します。LuckPerms不在・API差異ではfalseを返し、Bukkit側の再評価点
 * （参加・復活・世界変更・インベントリ開放）のみで追従します。
 * イベントは非同期にも届くため、実際の正規化は呼出側がメインスレッドへ寄せます。
 */
object LuckPermsRecalculateAdapter {
    fun register(plugin: Plugin, logger: Logger, onRecalculated: (UUID) -> Unit): Boolean = runCatching {
        if (plugin.server.pluginManager.getPlugin("LuckPerms") == null) return false
        val luckPerms = Class.forName("net.luckperms.api.LuckPermsProvider")
            .getMethod("get").invoke(null) ?: return false
        val eventBus = luckPerms.javaClass.getMethod("getEventBus").invoke(luckPerms) ?: return false
        val eventClass = Class.forName("net.luckperms.api.event.user.UserDataRecalculateEvent")
        val subscribe = eventBus.javaClass.methods.firstOrNull { method ->
            method.name == "subscribe" && method.parameterCount == 3 &&
                method.parameterTypes[1].isAssignableFrom(Class::class.java) &&
                method.parameterTypes[2].isAssignableFrom(Consumer::class.java)
        } ?: return false
        val luckPermsPlugin = plugin.server.pluginManager.getPlugin("LuckPerms") ?: return false
        val handler = Proxy.newProxyInstance(
            Consumer::class.java.classLoader,
            arrayOf(Consumer::class.java),
        ) { _, method, args ->
            if (method.name == "accept" && args != null && args.isNotEmpty()) {
                runCatching {
                    val user = args[0].javaClass.getMethod("getUser").invoke(args[0]) ?: return@runCatching
                    val uuid = user.javaClass.getMethod("getUniqueId").invoke(user) as? UUID
                        ?: return@runCatching
                    onRecalculated(uuid)
                }.onFailure { failure ->
                    logger.log(
                        java.util.logging.Level.FINE,
                        "LuckPerms再計算イベントの処理に失敗しました",
                        failure,
                    )
                }
            }
            null
        }
        subscribe.invoke(eventBus, luckPermsPlugin, eventClass, handler)
        true
    }.getOrDefault(false)
}
