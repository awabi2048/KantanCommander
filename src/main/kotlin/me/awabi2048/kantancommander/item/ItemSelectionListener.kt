package me.awabi2048.kantancommander.item

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.gui.CommandSettingContext
import me.awabi2048.kantancommander.gui.CommandSettingsModel
import me.awabi2048.kantancommander.model.applyCalledDisk
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

    /**
     * issue #34-2: 選択開始時の保存世代を編集セッションとして保持します。
     *
     * 確定クリック時に世代を検証し、選択中に別の編集が成功していた場合は
     * 保存せず、他の編集者の変更を黙って上書きしません。
     */
    fun begin(player: Player, scriptId: UUID, nodeId: UUID, returnRoute: MenuRoute) {
        selections[player.uniqueId] =
            Selection(scriptId, nodeId, returnRoute, SelectionKind.ITEM, openRevision = plugin.scripts.load(scriptId)?.revision)
    }

    fun beginDisk(player: Player, scriptId: UUID, nodeId: UUID, returnRoute: MenuRoute) {
        selections[player.uniqueId] =
            Selection(scriptId, nodeId, returnRoute, SelectionKind.DISK, openRevision = plugin.scripts.load(scriptId)?.revision)
    }

    fun beginMaterial(
        player: Player,
        scriptId: UUID,
        nodeId: UUID,
        returnRoute: MenuRoute,
        parameter: String,
    ) {
        selections[player.uniqueId] = Selection(
            scriptId,
            nodeId,
            returnRoute,
            SelectionKind.MATERIAL,
            parameter,
            openRevision = plugin.scripts.load(scriptId)?.revision,
        )
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val selection = selections[player.uniqueId] ?: return
        if (event.clickedInventory != player.inventory) return
        event.isCancelled = true
        val selected = event.currentItem?.takeUnless { it.type == Material.AIR } ?: return
        val selectedDisk = if (selection.kind == SelectionKind.DISK) {
            val selectedId = KantanItemService.diskId(selected) ?: return cancel(player)
            val selectedScript = plugin.scripts.load(selectedId) ?: return cancel(player)
            selectedId to selectedScript
        } else null
        val context = CommandSettingContext(selection.scriptId, selection.nodeId)
        val savedNode = runCatching {
            CommandSettingsModel.updateNode(
                plugin,
                context,
                editorId = player.uniqueId,
                expectedRevision = selection.openRevision,
            ) { node ->
                when (selection.kind) {
                    SelectionKind.ITEM -> {
                        // 付与・装備とも数量・Name/Lore等を含む実体をスナップショット保存します。
                        CommandSettingsModel.setParameter(node, "item", selected.type.key.toString())
                        CommandSettingsModel.setParameter(node, "itemData", ItemStackCodec.encode(selected))
                    }
                    SelectionKind.DISK -> {
                        val (selectedId, selectedScript) = requireNotNull(selectedDisk)
                        // DISK_CALL は参照として保持し、識別子と引数宣言だけを転記します。
                        node.applyCalledDisk(selectedId, selectedScript)
                    }
                    SelectionKind.MATERIAL -> {
                        val parameter = requireNotNull(selection.parameter)
                        CommandSettingsModel.setParameter(node, parameter, selected.type.key.toString())
                    }
                }
            }
        }.onFailure {
            selections.remove(player.uniqueId)
        }.getOrNull()
        if (savedNode == null) {
            // 保存失敗（競合を含む）は選択を破棄し、最新状態へ戻します。
            // 他の編集者の変更を上書きしないため、再選択を案内します。
            selections.remove(player.uniqueId)
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
            CCSystem.getAPI().getMenuRuntimeService().open(player, selection.returnRoute)
            return
        }
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
        /** issue #34-2: 選択開始時の保存世代です。確定時に競合を検出します。 */
        val openRevision: Long? = null,
    )
}
