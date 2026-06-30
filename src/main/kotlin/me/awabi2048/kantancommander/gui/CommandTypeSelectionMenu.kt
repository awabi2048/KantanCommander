package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.*
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.model.CommandType
import me.awabi2048.kantancommander.data.model.DiskScript
import me.awabi2048.kantancommander.data.model.ScriptCommand
import me.awabi2048.kantancommander.util.I18nHelper
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import java.util.UUID

class CommandTypeSelectionMenu(private val plugin: KantanCommanderPlugin) : Listener {

    private companion object {
        const val OWNER = "kantan_commander"
        const val MENU_ID = "add_command"

        // FREE_45 の本文領域に命令タイプを並べる。外枠と戻る位置はCC-Systemに従う。
        val COMMAND_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        )
    }

    private val navigation get() = CCSystem.getAPI().getMenuNavigationService()
    private val elementService get() = CCSystem.getAPI().getGuiElementService()
    private val layout get() = KantanGuiElements.commandPaletteLayout()
    private val soundService get() = CCSystem.getAPI().getMenuSoundService()

    /** 追加先のスクリプト (プレイヤーUUID -> DiskScript) */
    private val targetScripts = mutableMapOf<UUID, DiskScript>()

    fun initialize() {
        navigation.registerOpener(OWNER, MENU_ID) { player, route ->
            val uuidStr = route.payload["scriptUuid"] ?: return@registerOpener false
            val scriptUuid = try { java.util.UUID.fromString(uuidStr) } catch (_: Exception) { return@registerOpener false }
            val script = me.awabi2048.kantancommander.data.DataManager.load(scriptUuid) ?: return@registerOpener false
            open(player, script)
            true
        }
    }

    fun open(player: Player, script: DiskScript) {
        targetScripts[player.uniqueId] = script

        val holder = KantanMenuHolder(player.uniqueId, OWNER, MENU_ID)
        val inventory = Bukkit.createInventory(holder, layout.size, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(I18nHelper.string(player, "gui.add_command.title")))
        holder.backingInventory = inventory

        render(player, inventory)
        player.openInventory(inventory)
        soundService.onMenuOpen(player, MENU_ID)
    }

    private fun render(player: Player, inventory: Inventory) {
        inventory.clear()
        KantanGuiElements.applyStandardFrame(inventory)

        val types = CommandType.entries

        types.forEachIndexed { index, type ->
            if (index < COMMAND_SLOTS.size) {
                inventory.setItem(COMMAND_SLOTS[index], elementService.item(GuiItemSpec(
                    material = type.icon,
                    name = GuiNameSpec.Text(I18nHelper.string(player, type.displayNameKey), GuiNameStyle.PRIMARY),
                    lore = GuiLoreSpec.Auto(
                        type.paramDefinitions.map { I18nHelper.string(player, it.displayNameKey) },
                        GuiLoreFrame.BOTH
                    ),
                    role = GuiElementRole.CONTENT,
                    amount = 1
                )))
            }
        }

        inventory.setItem(layout.backSlot, elementService.backItem(I18nHelper.string(player, "gui.common.back")))
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? KantanMenuHolder ?: return
        if (holder.menuOwner != OWNER || holder.menuId != MENU_ID) return
        val player = event.whoClicked as? Player ?: return
        if (holder.ownerId != player.uniqueId) { event.isCancelled = true; return }
        event.isCancelled = true

        if (event.rawSlot == layout.backSlot) {
            soundService.onMenuClick(player, MENU_ID, MenuClickType.CANCEL)
            val script = targetScripts[player.uniqueId] ?: return
            plugin.sequenceEditorMenu.open(player, script)
            return
        }

        val typeIndex = rawSlotToTypeIndex(event.rawSlot) ?: return
        if (typeIndex >= CommandType.entries.size) return

        val type = CommandType.entries[typeIndex]
        val script = targetScripts[player.uniqueId] ?: return

        val maxCommands = plugin.config.getInt("max-commands-per-disk", 32)
        if (script.commands.size >= maxCommands) {
            player.sendMessage(I18nHelper.string(player, "message.max_commands", mapOf("max" to maxCommands.toString())))
            soundService.onMenuClick(player, MENU_ID, MenuClickType.CANCEL)
            return
        }

        val cmd = ScriptCommand(type)
        script.commands.add(cmd)
        soundService.onMenuClick(player, MENU_ID, MenuClickType.CONFIRM)
        plugin.commandParamMenu.open(player, script, script.commands.size - 1)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? KantanMenuHolder ?: return
        if (holder.menuOwner != OWNER) return
        event.isCancelled = true
    }

    private fun rawSlotToTypeIndex(slot: Int): Int? {
        val idx = COMMAND_SLOTS.indexOf(slot)
        return if (idx >= 0) idx else null
    }
}
