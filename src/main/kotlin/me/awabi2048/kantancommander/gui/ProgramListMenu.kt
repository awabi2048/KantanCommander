package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import java.util.UUID
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.DiskItemService
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player

class ProgramListMenu(private val plugin: KantanCommanderPlugin) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                owner = OWNER,
                id = MENU_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_PREVIOUS to handle { context ->
                        val page = context.route.payload[PAGE]?.toIntOrNull() ?: 0
                        MenuActionResult.Success(MenuUpdate.Replace(route(page - 1)))
                    },
                    ACTION_NEXT to handle { context ->
                        val page = context.route.payload[PAGE]?.toIntOrNull() ?: 0
                        MenuActionResult.Success(MenuUpdate.Replace(route(page + 1)))
                    },
                    ACTION_CLOSE to handle { MenuActionResult.Success(MenuUpdate.Close) },
                    ACTION_SELECT to handle { context ->
                        val scriptId = context.payload[SCRIPT_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@handle MenuActionResult.Ignored
                        val script = plugin.scripts.load(scriptId) ?: return@handle MenuActionResult.Ignored
                        if (context.click.isRightClick) {
                            MenuActionResult.Success(MenuUpdate.Navigate(SequenceEditorMenu.route(script.id)))
                        } else {
                            context.player.inventory.addItem(DiskItemService.create(script, context.player)).values
                                .forEach { context.player.world.dropItem(context.player.location, it) }
                            MenuActionResult.Success(MenuUpdate.None)
                        }
                    },
                ),
            )
        )
    }

    fun open(player: Player, page: Int = 0) {
        runtime.open(player, route(page))
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val layout = KcGui.layouts.pagedList54()
        val scripts = plugin.scripts.listOwned(player.uniqueId)
        val total = ((scripts.size + layout.itemSlots.size - 1) / layout.itemSlots.size).coerceAtLeast(1)
        val page = (route.payload[PAGE]?.toIntOrNull() ?: 0).coerceIn(0, total - 1)
        val elements = mutableListOf<MenuElement>()

        scripts.drop(page * layout.itemSlots.size).take(layout.itemSlots.size).forEachIndexed { index, script ->
            elements += MenuElement(
                slot = layout.itemSlots[index],
                item = KcGui.item(
                    Material.MUSIC_DISC_13,
                    script.name,
                    GuiNameStyle.PRIMARY,
                    listOf(
                        GuiLoreLine.Data(KcI18n.text(player, "item.commands"), script.commands.size, "§f"),
                        GuiLoreLine.Data(KcI18n.text(player, "item.trigger"), KcI18n.text(player, script.trigger.key), "§f"),
                        GuiLoreLine.Spacer,
                        KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.programs.action_get")),
                        KcGui.action(player, "lore.click.right", KcI18n.text(player, "gui.programs.action_edit")),
                    ),
                ),
                role = GuiElementRole.CONTENT,
                actionId = ACTION_SELECT,
                actionPayload = mapOf(SCRIPT_ID to script.id.toString()),
            )
        }

        elements += navigationElement(layout.previousPageSlot, page > 0, "<", ACTION_PREVIOUS)
        elements += navigationElement(layout.nextPageSlot, page < total - 1, ">", ACTION_NEXT)
        elements += MenuElement(
            layout.backSlot,
            KcGui.elements.backItem(KcI18n.text(player, "gui.common.close")),
            GuiElementRole.CANCEL,
            ACTION_CLOSE,
        )
        elements += MenuElement(
            layout.infoSlot,
            KcGui.item(Material.BOOK, "${page + 1}/$total", GuiNameStyle.MUTED),
            GuiElementRole.DECORATION,
        )
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, "gui.programs.title")), elements)
    }

    private fun navigationElement(slot: Int, enabled: Boolean, name: String, actionId: String): MenuElement {
        return if (enabled) {
            MenuElement(slot, KcGui.item(Material.ARROW, name), GuiElementRole.NAVIGATION, actionId)
        } else {
            MenuElement(slot, KcGui.elements.decoration(Material.BARRIER), GuiElementRole.DECORATION)
        }
    }

    private fun handle(block: (com.awabi2048.ccsystem.api.gui.MenuActionContext) -> MenuActionResult) =
        MenuActionHandler(block)

    companion object {
        const val OWNER = "kantan"
        const val MENU_ID = "programs"
        private const val PAGE = "page"
        private const val SCRIPT_ID = "scriptId"
        private const val ACTION_PREVIOUS = "previous"
        private const val ACTION_NEXT = "next"
        private const val ACTION_CLOSE = "close"
        private const val ACTION_SELECT = "select"

        fun route(page: Int = 0) = MenuRoute(OWNER, MENU_ID, mapOf(PAGE to page.coerceAtLeast(0).toString()))
    }
}
