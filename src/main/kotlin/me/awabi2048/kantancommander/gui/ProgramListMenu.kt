package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.*
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.DataManager
import me.awabi2048.kantancommander.data.PlacedDiskManager
import me.awabi2048.kantancommander.item.DiskItemFactory
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

class ProgramListMenu(private val plugin: KantanCommanderPlugin) : Listener {

    private companion object {
        const val OWNER = "kantan_commander"
        const val MENU_ID = "programs"
    }

    private val navigation get() = CCSystem.getAPI().getMenuNavigationService()
    private val elementService get() = CCSystem.getAPI().getGuiElementService()
    private val layout get() = KantanGuiElements.pagedListLayout()
    private val soundService get() = CCSystem.getAPI().getMenuSoundService()

    private val sessions = mutableMapOf<UUID, ProgramSession>()

    private data class ProgramSession(
        var currentPage: Int = 0
    )

    fun initialize() {
        navigation.registerOpener(OWNER, MENU_ID) { player, route ->
            open(player)
            true
        }
    }

    fun open(player: Player) {
        val session = sessions.getOrPut(player.uniqueId) { ProgramSession() }

        val holder = KantanMenuHolder(player.uniqueId, OWNER, MENU_ID)
        val inventory = Bukkit.createInventory(holder, layout.size, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(I18nHelper.string(player, "gui.programs.title")))
        holder.backingInventory = inventory

        render(player, session, inventory)
        player.openInventory(inventory)
        soundService.onMenuOpen(player, MENU_ID)
    }

    private fun render(player: Player, session: ProgramSession, inventory: Inventory) {
        inventory.clear()
        val pageLayout = layout

        // フレーム
        KantanGuiElements.applyStandardFrame(inventory)

        val programs = DataManager.listOwned(player.uniqueId)
        val itemsPerPage = pageLayout.itemSlots.size
        val totalPages = ((programs.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
        val page = session.currentPage.coerceIn(0, totalPages - 1)
        session.currentPage = page

        // ページ送り
        inventory.setItem(pageLayout.previousPageSlot, if (page > 0)
            elementService.item(GuiItemSpec(Material.ARROW, GuiNameSpec.Text("←", GuiNameStyle.DEFAULT), GuiLoreSpec.Simple(listOf(I18nHelper.string(player, "gui.common.previous_page"))), GuiElementRole.NAVIGATION, 1))
        else elementService.decoration(Material.BARRIER))
        inventory.setItem(pageLayout.nextPageSlot, if (page < totalPages - 1)
            elementService.item(GuiItemSpec(Material.ARROW, GuiNameSpec.Text("→", GuiNameStyle.DEFAULT), GuiLoreSpec.Simple(listOf(I18nHelper.string(player, "gui.common.next_page"))), GuiElementRole.NAVIGATION, 1))
        else elementService.decoration(Material.BARRIER))

        // プログラム一覧
        val startIndex = page * itemsPerPage
        val pagePrograms = programs.drop(startIndex).take(itemsPerPage)

        pageLayout.itemSlots.forEachIndexed { index, slot ->
            if (index < pagePrograms.size) {
                val script = pagePrograms[index]
                val placements = PlacedDiskManager.findByDisk(script.uuid)
                val creatorName = Bukkit.getOfflinePlayer(script.creator).name ?: "?"

                inventory.setItem(slot, elementService.item(GuiItemSpec(
                    material = Material.MUSIC_DISC_13,
                    name = GuiNameSpec.Text(script.name, GuiNameStyle.PRIMARY),
                    lore = GuiLoreSpec.Rich(
                        listOf(
                            GuiLoreLine.Data(I18nHelper.string(player, "gui.programs.uuid"), "${script.uuid.toString().take(8)}...", "§f"),
                            GuiLoreLine.Data(I18nHelper.string(player, "item.disk_lore.commands"), script.commands.size, "§f"),
                            GuiLoreLine.Data(I18nHelper.string(player, "item.disk_lore.creator"), creatorName, "§f"),
                            GuiLoreLine.Data(I18nHelper.string(player, "gui.programs.placements"), placements.size, "§f"),
                            GuiLoreLine.Spacer,
                            GuiLoreLine.Action(I18nHelper.string(player, "gui.programs.operation_click"), I18nHelper.string(player, "gui.programs.action_get")),
                            GuiLoreLine.Action(I18nHelper.string(player, "gui.programs.operation_right_click"), I18nHelper.string(player, "gui.programs.action_delete")),
                            GuiLoreLine.Action(I18nHelper.string(player, "gui.programs.operation_shift_right_click"), I18nHelper.string(player, "gui.programs.action_clean_placements")),
                        ),
                        GuiLoreFrame.BOTH
                    ),
                    role = GuiElementRole.CONTENT,
                    amount = 1
                )))
            }
        }

        // 戻る
        inventory.setItem(pageLayout.backSlot, elementService.backItem(I18nHelper.string(player, "gui.common.back")))

        // 情報
        inventory.setItem(pageLayout.infoSlot, elementService.item(GuiItemSpec(
            Material.BOOK,
            GuiNameSpec.Text("${page + 1}/$totalPages", GuiNameStyle.MUTED),
            GuiLoreSpec.Rich(
                listOf(GuiLoreLine.Data(I18nHelper.string(player, "gui.programs.total"), programs.size, "§f")),
                GuiLoreFrame.NONE
            ),
            GuiElementRole.ACTION, 1
        )))
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? KantanMenuHolder ?: return
        if (holder.menuOwner != OWNER || holder.menuId != MENU_ID) return
        val player = event.whoClicked as? Player ?: return
        if (holder.ownerId != player.uniqueId) { event.isCancelled = true; return }
        event.isCancelled = true

        val session = sessions[player.uniqueId] ?: return
        val inv = event.view.topInventory
        val slot = event.rawSlot
        val pageLayout = layout

        when (slot) {
            pageLayout.previousPageSlot -> {
                if (session.currentPage > 0) {
                    session.currentPage--
                    soundService.onMenuClick(player, MENU_ID, MenuClickType.NAVIGATION)
                    render(player, session, inv)
                }
            }
            pageLayout.nextPageSlot -> {
                val programs = DataManager.listOwned(player.uniqueId)
                val itemsPerPage = pageLayout.itemSlots.size
                val totalPages = ((programs.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
                if (session.currentPage < totalPages - 1) {
                    session.currentPage++
                    soundService.onMenuClick(player, MENU_ID, MenuClickType.NAVIGATION)
                    render(player, session, inv)
                }
            }
            pageLayout.backSlot -> {
                soundService.onMenuClick(player, MENU_ID, MenuClickType.CANCEL)
                sessions.remove(player.uniqueId)
                player.closeInventory()
            }
            in pageLayout.itemSlots -> {
                val index = pageLayout.itemSlots.indexOf(slot)
                val programIndex = session.currentPage * pageLayout.itemSlots.size + index
                val programs = DataManager.listOwned(player.uniqueId)
                if (programIndex < programs.size) {
                    val script = programs[programIndex]
                    when {
                        event.isRightClick && event.isShiftClick -> {
                            // Shift+右クリック → 設置解除のみ
                            val placements = PlacedDiskManager.findByDisk(script.uuid)
                            placements.forEach { p ->
                                val world = Bukkit.getWorld(p.worldName)
                                if (world != null) {
                                    PlacedDiskManager.removeDisplay(world, p.displayEntityUUID)
                                    PlacedDiskManager.remove(world, p.x, p.y, p.z)
                                }
                            }
                            player.sendMessage(I18nHelper.string(player, "message.placements_removed", mapOf("count" to placements.size.toString())))
                            soundService.onMenuClick(player, MENU_ID, MenuClickType.CONFIRM)
                            render(player, session, inv)
                        }
                        event.isRightClick -> {
                            // 右クリック → 削除確認
                            soundService.onMenuClick(player, MENU_ID, MenuClickType.CANCEL)
                            player.closeInventory()
                            val uuid = script.uuid
                            val name = script.name
                            me.awabi2048.kantancommander.util.DialogInputUtil.showConfirmation(
                                player = player,
                                titleKey = "gui.programs.delete",
                                bodyMessage = I18nHelper.string(player, "gui.programs.delete_confirm", mapOf("name" to name)),
                                onConfirm = {
                                    // 設置マッピング削除
                                    val placements = PlacedDiskManager.findByDisk(uuid)
                                    placements.forEach { p ->
                                        val world = Bukkit.getWorld(p.worldName)
                                        if (world != null) {
                                            PlacedDiskManager.removeDisplay(world, p.displayEntityUUID)
                                            PlacedDiskManager.remove(world, p.x, p.y, p.z)
                                        }
                                    }
                                    DataManager.delete(uuid)
                                    player.sendMessage(I18nHelper.string(player, "message.program_deleted", mapOf("name" to name)))
                                    open(player)
                                },
                                onCancel = {
                                    open(player)
                                }
                            )
                        }
                        else -> {
                            // 左クリック → ディスク取得
                            soundService.onMenuClick(player, MENU_ID, MenuClickType.CONFIRM)
                            val diskItem = DiskItemFactory.createDiskForPlayer(player, script)
                            val leftover = player.inventory.addItem(diskItem)
                            if (leftover.isNotEmpty()) {
                                player.world.dropItem(player.location, leftover.values.first())
                            }
                            player.sendMessage(I18nHelper.string(player, "message.disk_gotten", mapOf("name" to script.name)))
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? KantanMenuHolder ?: return
        if (holder.menuOwner != OWNER) return
        event.isCancelled = true
    }
}
