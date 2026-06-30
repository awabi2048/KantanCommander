package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.*
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.DataManager
import me.awabi2048.kantancommander.data.model.CommandType
import me.awabi2048.kantancommander.data.model.DiskScript
import me.awabi2048.kantancommander.data.model.ScriptCommand
import me.awabi2048.kantancommander.data.model.TriggerType
import me.awabi2048.kantancommander.util.ClipboardCodec
import me.awabi2048.kantancommander.util.I18nHelper
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID

class SequenceEditorMenu(private val plugin: KantanCommanderPlugin) : Listener {

    private companion object {
        const val OWNER = "kantan_commander"
        const val MENU_ID = "editor"

        // PAGED_LIST_54 のフッターで back/info 以外を編集操作に割り当てる。
        const val SLOT_ADD = 46
        const val SLOT_TEST = 47
        const val SLOT_COPY = 48
        const val SLOT_PASTE = 50
        const val SLOT_RENAME = 51
        const val SLOT_TRIGGER = 52
        const val SLOT_SAVE = 53
    }

    private val navigation get() = CCSystem.getAPI().getMenuNavigationService()
    private val elementService get() = CCSystem.getAPI().getGuiElementService()
    private val layout get() = KantanGuiElements.pagedListLayout()
    private val soundService get() = CCSystem.getAPI().getMenuSoundService()

    // プレイヤー毎の編集セッション
    private val sessions = mutableMapOf<UUID, EditorSession>()
    private val playerClipboard = mutableMapOf<UUID, String>()

    private data class EditorSession(
        val script: DiskScript,
        var currentPage: Int = 0
    )

    fun initialize() {
        navigation.registerOpener(OWNER, MENU_ID) { player, route ->
            val uuidStr = route.payload["uuid"] ?: return@registerOpener false
            val uuid = try { UUID.fromString(uuidStr) } catch (_: Exception) { return@registerOpener false }
            val script = DataManager.load(uuid) ?: return@registerOpener false
            open(player, script)
            true
        }
    }

    fun shutdown() {
        navigation.unregisterOwner(OWNER)
    }

    fun open(player: Player, script: DiskScript) {
        val session = EditorSession(script)
        sessions[player.uniqueId] = session

        val title = I18nHelper.string(player, "gui.editor.title", mapOf("name" to script.name))
        val holder = KantanMenuHolder(player.uniqueId, OWNER, MENU_ID)
        val inventory = Bukkit.createInventory(holder, layout.size, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(title))
        holder.backingInventory = inventory

        render(player, session, inventory)
        player.openInventory(inventory)
        soundService.onMenuOpen(player, MENU_ID)
    }

    private fun render(player: Player, session: EditorSession, inventory: Inventory) {
        inventory.clear()
        val pageLayout = layout
        val script = session.script

        // フレーム
        KantanGuiElements.applyStandardFrame(inventory)

        // ページ計算
        val itemsPerPage = pageLayout.itemSlots.size
        val totalPages = ((script.commands.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
        val page = session.currentPage.coerceIn(0, totalPages - 1)
        session.currentPage = page

        // 前ページ
        inventory.setItem(pageLayout.previousPageSlot, if (page > 0)
            elementService.item(GuiItemSpec(Material.ARROW, GuiNameSpec.Text("←", GuiNameStyle.DEFAULT), GuiLoreSpec.Simple(listOf(I18nHelper.string(player, "gui.common.previous_page"))), GuiElementRole.NAVIGATION, 1))
        else elementService.decoration(Material.BARRIER))

        // 次ページ
        inventory.setItem(pageLayout.nextPageSlot, if (page < totalPages - 1)
            elementService.item(GuiItemSpec(Material.ARROW, GuiNameSpec.Text("→", GuiNameStyle.DEFAULT), GuiLoreSpec.Simple(listOf(I18nHelper.string(player, "gui.common.next_page"))), GuiElementRole.NAVIGATION, 1))
        else elementService.decoration(Material.BARRIER))

        // 命令リスト
        val startIndex = page * itemsPerPage
        val pageCommands = script.commands.drop(startIndex).take(itemsPerPage)

        pageLayout.itemSlots.forEachIndexed { index, slot ->
            if (index < pageCommands.size) {
                val cmd = pageCommands[index]
                val cmdIndex = startIndex + index
                inventory.setItem(slot, buildCommandItem(player, cmd, cmdIndex))
            }
        }

        // アクションボタン
        inventory.setItem(pageLayout.backSlot, elementService.backItem(I18nHelper.string(player, "gui.editor.close")))
        inventory.setItem(SLOT_ADD, elementService.item(GuiItemSpec(Material.LIME_WOOL, GuiNameSpec.Text(I18nHelper.string(player, "gui.editor.add"), GuiNameStyle.SUCCESS), GuiLoreSpec.None, GuiElementRole.ACTION, 1)))
        inventory.setItem(SLOT_TEST, elementService.item(GuiItemSpec(Material.FIREWORK_ROCKET, GuiNameSpec.Text(I18nHelper.string(player, "gui.editor.test_run"), GuiNameStyle.WARNING), GuiLoreSpec.None, GuiElementRole.ACTION, 1)))
        inventory.setItem(SLOT_COPY, elementService.item(GuiItemSpec(Material.PAPER, GuiNameSpec.Text(I18nHelper.string(player, "gui.editor.copy_clipboard"), GuiNameStyle.PRIMARY), GuiLoreSpec.None, GuiElementRole.ACTION, 1)))
        inventory.setItem(SLOT_PASTE, elementService.item(GuiItemSpec(Material.MAP, GuiNameSpec.Text(I18nHelper.string(player, "gui.editor.paste_clipboard"), GuiNameStyle.PRIMARY), GuiLoreSpec.None, GuiElementRole.ACTION, 1)))
        inventory.setItem(SLOT_RENAME, elementService.item(GuiItemSpec(Material.NAME_TAG, GuiNameSpec.Text(I18nHelper.string(player, "gui.editor.rename"), GuiNameStyle.PRIMARY), GuiLoreSpec.None, GuiElementRole.ACTION, 1)))
        inventory.setItem(SLOT_TRIGGER, elementService.item(GuiItemSpec(Material.REDSTONE, GuiNameSpec.Text(I18nHelper.string(player, "gui.editor.trigger_type", mapOf("type" to I18nHelper.string(player, script.triggerType.displayNameKey))), GuiNameStyle.PRIMARY), GuiLoreSpec.None, GuiElementRole.ACTION, 1)))
        inventory.setItem(SLOT_SAVE, elementService.item(GuiItemSpec(Material.DIAMOND, GuiNameSpec.Text(I18nHelper.string(player, "gui.editor.save"), GuiNameStyle.SUCCESS), GuiLoreSpec.None, GuiElementRole.ACTION, 1)))

        // 情報スロット
        inventory.setItem(pageLayout.infoSlot, elementService.item(GuiItemSpec(Material.BOOK, GuiNameSpec.Text("${page + 1}/$totalPages", GuiNameStyle.MUTED), GuiLoreSpec.Auto(listOf(I18nHelper.string(player, "gui.editor.page_info", mapOf("count" to script.commands.size.toString()))), GuiLoreFrame.NONE), GuiElementRole.ACTION, 1)))
    }

    private fun buildCommandItem(player: Player, cmd: ScriptCommand, index: Int): ItemStack {
        val summary = cmd.paramSummary()
        val name = I18nHelper.string(player, "gui.editor.command_line", mapOf(
            "index" to (index + 1).toString(),
            "name" to I18nHelper.string(player, cmd.type.displayNameKey),
            "summary" to summary
        ))

        return elementService.item(GuiItemSpec(
            material = cmd.type.icon,
            name = GuiNameSpec.Text(name, GuiNameStyle.DEFAULT),
            lore = GuiLoreSpec.Rich(
                listOf(
                    GuiLoreLine.Action(I18nHelper.string(player, "gui.editor.operation_click"), I18nHelper.string(player, "gui.editor.action_edit")),
                    GuiLoreLine.Action(I18nHelper.string(player, "gui.editor.operation_right_click"), I18nHelper.string(player, "gui.editor.action_delete")),
                    GuiLoreLine.Action(I18nHelper.string(player, "gui.editor.operation_shift_right_click"), I18nHelper.string(player, "gui.editor.action_move_up")),
                    GuiLoreLine.Action(I18nHelper.string(player, "gui.editor.operation_shift_left_click"), I18nHelper.string(player, "gui.editor.action_move_down"))
                ),
                GuiLoreFrame.BOTH
            ),
            role = GuiElementRole.CONTENT,
            amount = 1
        ))
    }

    // ─── クリック処理 ──────────────────────────────

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
                val itemsPerPage = pageLayout.itemSlots.size
                val totalPages = ((session.script.commands.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
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
            SLOT_ADD -> {
                soundService.onMenuClick(player, MENU_ID, MenuClickType.DEFAULT)
                plugin.commandTypeSelectionMenu.open(player, session.script)
            }
            SLOT_TEST -> {
                soundService.onMenuClick(player, MENU_ID, MenuClickType.DEFAULT)
                val executor = me.awabi2048.kantancommander.execution.SequenceExecutor(plugin)
                executor.execute(session.script.commands, player.location, player)
                player.sendMessage(I18nHelper.string(player, "message.executed", mapOf("count" to session.script.commands.size.toString())))
            }
            SLOT_COPY -> {
                val data = ClipboardCodec.encode(session.script.commands)
                playerClipboard[player.uniqueId] = data
                player.sendMessage(I18nHelper.string(player, "message.copied_to_clipboard"))
                soundService.onMenuClick(player, MENU_ID, MenuClickType.CONFIRM)
            }
            SLOT_PASTE -> {
                val data = playerClipboard[player.uniqueId] ?: ""
                val commands = ClipboardCodec.decode(data)
                if (commands != null) {
                    val maxCommands = plugin.config.getInt("max-commands-per-disk", 32)
                    val available = maxCommands - session.script.commands.size
                    val toAdd = commands.take(available)
                    session.script.commands.addAll(toAdd)
                    player.sendMessage(I18nHelper.string(player, "message.pasted_from_clipboard", mapOf("count" to toAdd.size.toString())))
                    soundService.onMenuClick(player, MENU_ID, MenuClickType.CONFIRM)
                } else {
                    player.sendMessage(I18nHelper.string(player, "message.invalid_clipboard"))
                    soundService.onMenuClick(player, MENU_ID, MenuClickType.CANCEL)
                }
                render(player, session, inv)
            }
            SLOT_RENAME -> {
                // Dialog API でリネーム
                soundService.onMenuClick(player, MENU_ID, MenuClickType.DEFAULT)
                player.closeInventory()
                me.awabi2048.kantancommander.util.DialogInputUtil.showTextInput(
                    player = player,
                    titleKey = "gui.editor.rename",
                    paramKey = "name",
                    paramLabel = Component.text(""),
                    currentValue = session.script.name,
                    plugin = plugin,
                    onConfirm = { newName ->
                        session.script.name = newName
                        open(player, session.script)
                    },
                    onCancel = {
                        open(player, session.script)
                    }
                )
            }
            SLOT_TRIGGER -> {
                session.script.triggerType = when (session.script.triggerType) {
                    TriggerType.REDSTONE_EDGE -> TriggerType.REDSTONE_RISING
                    TriggerType.REDSTONE_RISING -> TriggerType.REDSTONE_EDGE
                }
                soundService.onMenuClick(player, MENU_ID, MenuClickType.DEFAULT)
                render(player, session, inv)
            }
            SLOT_SAVE -> {
                DataManager.save(session.script)
                player.sendMessage(I18nHelper.string(player, "message.saved"))
                soundService.onMenuClick(player, MENU_ID, MenuClickType.CONFIRM)
            }
            in pageLayout.itemSlots -> {
                val index = pageLayout.itemSlots.indexOf(slot)
                val cmdIndex = session.currentPage * pageLayout.itemSlots.size + index
                if (cmdIndex < session.script.commands.size) {
                    when {
                        event.isShiftClick && event.isRightClick -> {
                            // Shift+右クリック → 上に移動
                            if (cmdIndex > 0) {
                                val temp = session.script.commands[cmdIndex]
                                session.script.commands[cmdIndex] = session.script.commands[cmdIndex - 1]
                                session.script.commands[cmdIndex - 1] = temp
                                soundService.onMenuClick(player, MENU_ID, MenuClickType.NAVIGATION)
                                render(player, session, inv)
                            }
                        }
                        event.isShiftClick && event.isLeftClick -> {
                            // Shift+左クリック → 下に移動
                            if (cmdIndex < session.script.commands.size - 1) {
                                val temp = session.script.commands[cmdIndex]
                                session.script.commands[cmdIndex] = session.script.commands[cmdIndex + 1]
                                session.script.commands[cmdIndex + 1] = temp
                                soundService.onMenuClick(player, MENU_ID, MenuClickType.NAVIGATION)
                                render(player, session, inv)
                            }
                        }
                        event.isRightClick -> {
                            // 右クリック → 削除確認
                            soundService.onMenuClick(player, MENU_ID, MenuClickType.CANCEL)
                            player.closeInventory()
                            val idx = cmdIndex
                            me.awabi2048.kantancommander.util.DialogInputUtil.showConfirmation(
                                player = player,
                                titleKey = "gui.editor.delete_confirm",
                                bodyMessage = I18nHelper.string(player, "gui.editor.delete_confirm", mapOf("index" to (idx + 1).toString())),
                                onConfirm = {
                                    session.script.commands.removeAt(idx)
                                    player.sendMessage(I18nHelper.string(player, "message.deleted"))
                                    open(player, session.script)
                                },
                                onCancel = {
                                    open(player, session.script)
                                }
                            )
                        }
                        else -> {
                            // 左クリック → パラメータ編集
                            soundService.onMenuClick(player, MENU_ID, MenuClickType.DEFAULT)
                            plugin.commandParamMenu.open(player, session.script, cmdIndex)
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
