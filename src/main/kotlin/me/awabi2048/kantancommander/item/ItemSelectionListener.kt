package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import java.util.UUID

class ItemSelectionListener(private val plugin: KantanCommanderPlugin) : Listener {
    private val selections = mutableMapOf<UUID, Selection>()

    fun begin(player: Player, scriptId: UUID, nodeId: UUID, returnRoute: MenuRoute) {
        selections[player.uniqueId] = Selection(scriptId, nodeId, returnRoute, SelectionKind.ITEM)
    }

    fun beginDisk(player: Player, scriptId: UUID, nodeId: UUID, returnRoute: MenuRoute) {
        selections[player.uniqueId] = Selection(scriptId, nodeId, returnRoute, SelectionKind.DISK)
    }

    fun beginMaterial(
        player: Player,
        scriptId: UUID,
        nodeId: UUID,
        returnRoute: MenuRoute,
        parameter: String,
    ) {
        selections[player.uniqueId] = Selection(scriptId, nodeId, returnRoute, SelectionKind.MATERIAL, parameter)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val selection = selections[player.uniqueId] ?: return
        if (event.clickedInventory != player.inventory) return
        event.isCancelled = true
        val selected = event.currentItem?.takeUnless { it.type == Material.AIR } ?: return
        val script = plugin.scripts.load(selection.scriptId) ?: return cancel(player)
        val candidateGraph = script.graph.deepCopy()
        val node = candidateGraph.nodes[selection.nodeId] ?: return cancel(player)
        when (selection.kind) {
            SelectionKind.ITEM -> {
                // 付与・装備とも数量・Name/Lore等を含む実体をスナップショット保存します。
                node.params["item"] = selected.type.key.toString()
                node.params["itemData"] = ItemStackCodec.encode(selected)
                node.markConfigured("item")
            }
            SelectionKind.DISK -> {
                val selectedId = KantanItemService.diskId(selected) ?: return cancel(player)
                val selectedScript = plugin.scripts.load(selectedId) ?: return cancel(player)
                node.params["diskId"] = selectedId.toString()
                node.snapshot = selectedScript.graph.deepCopy()
                node.markConfigured("diskId")
            }
            SelectionKind.MATERIAL -> {
                val parameter = selection.parameter ?: return
                node.params[parameter] = selected.type.key.toString()
                node.markConfigured(parameter)
            }
        }
        runCatching { plugin.scripts.save(script.copy(graph = candidateGraph)) }
            .onFailure { failure ->
                selections.remove(player.uniqueId)
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "アイテム／素材選択の保存に失敗しました: script=${selection.scriptId} node=${selection.nodeId}",
                    failure,
                )
            }
            .getOrElse { return }
        selections.remove(player.uniqueId)
        CCSystem.getAPI().getMenuRuntimeService().open(player, selection.returnRoute)
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        // メニューを閉じることは明示的なキャンセル。アイテムは変更されないが、
        // 無音で失敗すると「設定できない」と誤解されるため通知します。
        val player = event.player as? Player ?: return
        if (selections.remove(player.uniqueId) != null) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_ITEM_SELECTION_CANCELLED))
        }
    }

    private fun cancel(player: Player) {
        selections.remove(player.uniqueId)
    }

    private enum class SelectionKind { ITEM, DISK, MATERIAL }

    private data class Selection(
        val scriptId: UUID,
        val nodeId: UUID,
        val returnRoute: MenuRoute,
        val kind: SelectionKind,
        val parameter: String? = null,
    )
}
