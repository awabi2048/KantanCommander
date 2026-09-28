package me.awabi2048.kantancommander.operationtool

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.WrappedDataValue
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import me.awabi2048.kantancommander.KantanCommanderPlugin
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Level

/**
 * 操作ツールwandの選択Entityを操作者へだけ発光させます。
 *
 * 実体の発光フラグは触らず、ENTITY_METADATAパケットの共有flagsへ
 * 発光bit（0x40）を操作者宛だけに差し込みます。差分適用なので、
 * Entityの再送信・メタデータ更新・再ログイン後にも発光が維持されます。
 * ProtocolLibはCC-Systemのdepend経由で必ず存在しますが、解決できない
 * 場合は発光なしで動作します。
 */
class OperationToolGlow(private val plugin: KantanCommanderPlugin) {
    /** 操作者UUID -> その操作者へ発光表示中の実体ID集合です。 */
    private val viewed = mutableMapOf<UUID, MutableSet<Int>>()

    /** 実体ID -> 発光表示している操作者UUID集合です。 */
    private val viewers = mutableMapOf<Int, MutableSet<UUID>>()

    private var manager: ProtocolManager? = null
    private var adapter: PacketAdapter? = null
    private var byteSerializer: WrappedDataWatcher.Serializer? = null

    /** ProtocolLibを解決し、送信パケットへ発光bitを差し込むlistenerを登録します。 */
    fun start() {
        val resolved = runCatching { ProtocolLibrary.getProtocolManager() }.getOrElse { failure ->
            plugin.logger.log(
                Level.WARNING,
                "ProtocolLibを解決できないため、操作ツールの発光表示は無効です。",
                failure,
            )
            return
        }
        manager = resolved
        byteSerializer = runCatching {
            WrappedDataWatcher.Registry.get(Byte::class.javaObjectType, false)
        }.getOrNull()
        adapter = object : PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {
            override fun onPacketSending(event: PacketEvent) {
                val entityId = event.packet.integers.read(0)
                if (event.player.uniqueId !in (viewers[entityId] ?: return)) return
                patchFlags(event.packet, glow = true)
            }
        }
        resolved.addPacketListener(adapter)
    }

    /** パケットlistenerを外し、追跡状態を破棄します。実体フラグは触っていないため戻し不要です。 */
    fun stop() {
        adapter?.let { manager?.removePacketListener(it) }
        adapter = null
        manager = null
        viewed.clear()
        viewers.clear()
    }

    /**
     * 選択集合と発光表示を同期します。
     *
     * 新たに選択されたEntityへ発光を適用し、選択から外れたEntityの発光を
     * 実体の現在フラグへ戻します。選択を変更した全経路から呼び出します。
     */
    fun sync(player: Player, selectedUuids: Set<UUID>) {
        val shown = viewed[player.uniqueId] ?: mutableSetOf()
        val selectedIds = selectedUuids.mapNotNull { plugin.server.getEntity(it)?.entityId }.toSet()
        val toAdd = selectedIds - shown
        val toRemove = shown - selectedIds
        toAdd.forEach { entityId -> apply(player, entityId) }
        toRemove.forEach { entityId -> release(player, entityId) }
    }

    /** 操作者へだけEntity発光を開始します。 */
    private fun apply(player: Player, entityId: Int) {
        viewed.getOrPut(player.uniqueId) { mutableSetOf() }.add(entityId)
        viewers.getOrPut(entityId) { mutableSetOf() }.add(player.uniqueId)
        sendFlags(player, entityId, glow = true)
    }

    /** 操作者へだけEntity発光を解除し、実体の現在フラグを送り直します。 */
    private fun release(player: Player, entityId: Int) {
        viewed[player.uniqueId]?.remove(entityId)
        viewers[entityId]?.remove(player.uniqueId)
        sendFlags(player, entityId, glow = false)
    }

    /** 操作者の発光表示をすべて解除します。ログアウト・全選択解除時に呼び出します。 */
    fun clear(playerId: UUID) {
        val shown = viewed.remove(playerId) ?: return
        val player = plugin.server.getPlayer(playerId)
        for (entityId in shown) {
            viewers[entityId]?.remove(playerId)
            if (player != null) sendFlags(player, entityId, glow = false)
        }
    }

    /**
     * 実体の共有flagsへ発光bitを差し込んだ差分metadataを操作者へ送ります。
     *
     * 解除時は実体の現在flagsをそのまま送るため、別系統が付けた発光や
     * 他の状態bitを壊しません。
     */
    private fun sendFlags(player: Player, entityId: Int, glow: Boolean) {
        val protocol = manager ?: return
        val serializer = byteSerializer ?: return
        val entity = plugin.server.worlds.asSequence()
            .flatMap { it.entities.asSequence() }
            .firstOrNull { it.entityId == entityId } ?: return
        val flags = currentFlags(entity)
        val value = if (glow) (flags.toInt() or GLOWING_BIT).toByte() else flags
        val packet = protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA)
        packet.integers.write(0, entityId)
        packet.dataValueCollectionModifier.write(0, listOf(WrappedDataValue(0, serializer, value)))
        runCatching { protocol.sendServerPacket(player, packet) }
    }

    /** サーバーが送信するmetadataへ発光bitを差し込みます。 */
    private fun patchFlags(packet: PacketContainer, glow: Boolean) {
        val serializer = byteSerializer ?: return
        val values = packet.dataValueCollectionModifier.read(0)?.toMutableList() ?: return
        val index = values.indexOfFirst { it.index == 0 }
        val base = (values.getOrNull(index)?.value as? Byte) ?: 0
        val patched = if (glow) (base.toInt() or GLOWING_BIT).toByte() else base
        val value = WrappedDataValue(0, serializer, patched)
        if (index >= 0) values[index] = value else values.add(0, value)
        packet.dataValueCollectionModifier.write(0, values)
    }

    /** 実体の現在の共有flagsを返します。取得できない場合は0です。 */
    private fun currentFlags(entity: Entity): Byte =
        runCatching { WrappedDataWatcher.getEntityWatcher(entity).getObject(0) as? Byte }
            .getOrNull() ?: 0

    companion object {
        /** Entity共有flagsの発光bitです。 */
        private const val GLOWING_BIT = 0x40
    }
}
