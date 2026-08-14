package me.awabi2048.kantancommander.gui
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
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
import me.awabi2048.kantancommander.model.DiskProfile
import me.awabi2048.kantancommander.model.effectiveProfile
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
                            val copy = plugin.scripts.copyForItem(script)
                            context.player.inventory.addItem(DiskItemService.create(copy, context.player)).values
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
            elements += KcGui.menuEntry(
                player = player,
                slot = layout.itemSlots[index],
                material = Material.MUSIC_DISC_13,
                name = script.name,
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_ENTRY_DESCRIPTION),
                data = listOf(
                    GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_COMMANDS), script.graph.nodes.size),
                    GuiMenuEntryData(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_PROFILE),
                        KcI18n.text(player, if (script.effectiveProfile == DiskProfile.SIMPLE) "profile.simple" else "profile.standard"),
                    ),
                    GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_TRIGGER), KcI18n.text(player, script.activation.key)),
                ),
                role = GuiElementRole.CONTENT,
                actions = listOf(
                    GuiMenuActionIntent.LeftRight(
                        GuiMenuActionIntent.AnyClick(ACTION_SELECT, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_ACTION_GET), mapOf(SCRIPT_ID to script.id.toString())),
                        GuiMenuActionIntent.AnyClick(ACTION_SELECT, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_ACTION_EDIT), mapOf(SCRIPT_ID to script.id.toString())),
                    ),
                ),
            )
        }

        elements += navigationElement(player, layout.previousPageSlot, page > 0, "<", ACTION_PREVIOUS)
        elements += navigationElement(player, layout.nextPageSlot, page < total - 1, ">", ACTION_NEXT)
        elements += KcGui.elements.backEntry(player, layout.backSlot)
        elements += KcGui.menuEntry(
            player, layout.infoSlot, Material.BOOK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_PAGE),
            GuiNameStyle.MUTED, GuiElementRole.CONTENT,
            data = listOf(GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_PAGE), "${page + 1}/$total")),
        )
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_TITLE)), elements)
    }

    private fun navigationElement(player: Player, slot: Int, enabled: Boolean, name: String, actionId: String): MenuElement {
        return if (enabled) {
            KcGui.menuEntry(
                player = player,
                slot = slot,
                material = Material.ARROW,
                name = name,
                role = GuiElementRole.NAVIGATION,
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_NAVIGATION_DESCRIPTION),
                actions = listOf(GuiMenuActionIntent.AnyClick(actionId, name)),
            )
        } else {
            KcGui.entry(player, slot, Material.BARRIER, "", role = GuiElementRole.DECORATION)
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
