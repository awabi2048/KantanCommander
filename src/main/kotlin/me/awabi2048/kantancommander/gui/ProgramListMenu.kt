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
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player

class ProgramListMenu(private val plugin: KantanCommanderPlugin) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        Source.entries.forEach { source -> runtime.register(definition(source)) }
    }

    /** 旧メニュー呼び出しはライブラリへ収束させます。履歴は専用コマンドから開きます。 */
    fun open(player: Player, page: Int = 0) = openLibrary(player, page)

    fun openLibrary(player: Player, page: Int = 0) {
        runtime.open(player, route(Source.LIBRARY, page))
    }

    fun openHistory(player: Player, page: Int = 0) {
        runtime.open(player, route(Source.HISTORY, page))
    }

    private fun definition(source: Source): InventoryMenuDefinition = InventoryMenuDefinition(
        owner = OWNER,
        id = source.menuId,
        renderer = { context -> render(context.player, context.route, source) },
        actions = mapOf(
            ACTION_PREVIOUS to handle { context ->
                val page = context.route.payload[PAGE]?.toIntOrNull() ?: 0
                MenuActionResult.Success(MenuUpdate.Replace(route(source, page - 1)))
            },
            ACTION_NEXT to handle { context ->
                val page = context.route.payload[PAGE]?.toIntOrNull() ?: 0
                MenuActionResult.Success(MenuUpdate.Replace(route(source, page + 1)))
            },
            ACTION_CLOSE to handle { MenuActionResult.Success(MenuUpdate.Close) },
            ACTION_SELECT to handle { context ->
                val scriptId = context.payload[SCRIPT_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@handle MenuActionResult.Ignored
                val script = plugin.scripts.load(scriptId) ?: return@handle MenuActionResult.Ignored
                // ライブラリ／履歴は「保存済み内容を取得する一覧」です。ここから元の
                // 正本を直接編集できるようにすると、履歴関係とライブラリ関係が意図せず
                // 書き換わるため、左右どちらのクリックでも必ず独立したディスクを複製します。
                val copy = plugin.scripts.copyForItem(script)
                context.player.inventory.addItem(KantanItemService.createDisk(copy, context.player)).values
                    .forEach { context.player.world.dropItem(context.player.location, it) }
                MenuActionResult.Success(MenuUpdate.None)
            },
        ),
    )

    private fun render(player: Player, route: MenuRoute, source: Source): InventoryMenuView {
        val layout = KcGui.layouts.pagedList54()
        val scripts = when (source) {
            Source.LIBRARY -> plugin.scripts.listLibrary(player.uniqueId)
            Source.HISTORY -> plugin.scripts.listHistory(player.uniqueId)
        }
        val total = ((scripts.size + layout.itemSlots.size - 1) / layout.itemSlots.size).coerceAtLeast(1)
        val page = (route.payload[PAGE]?.toIntOrNull() ?: 0).coerceIn(0, total - 1)
        val elements = mutableListOf<MenuElement>()

        scripts.drop(page * layout.itemSlots.size).take(layout.itemSlots.size).forEachIndexed { index, script ->
            elements += KcGui.menuEntry(
                player = player,
                slot = layout.itemSlots[index],
                material = Material.MUSIC_DISC_OTHERSIDE,
                name = script.name,
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_ENTRY_DESCRIPTION),
                data = listOf(
                    GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_COMMANDS), script.graph.nodes.size),
                    GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_ITEM_TRIGGER), KcI18n.text(player, script.activation.key)),
                ),
                role = GuiElementRole.CONTENT,
                actions = listOf(
                    GuiMenuActionIntent.AnyClick(
                        ACTION_SELECT,
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_ACTION_GET),
                        mapOf(SCRIPT_ID to script.id.toString()),
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
        const val MENU_ID = "library"
        const val HISTORY_MENU_ID = "history"
        private const val PAGE = "page"
        private const val SCRIPT_ID = "scriptId"
        private const val ACTION_PREVIOUS = "previous"
        private const val ACTION_NEXT = "next"
        private const val ACTION_CLOSE = "close"
        private const val ACTION_SELECT = "select"

        fun route(page: Int = 0) = route(Source.LIBRARY, page)

        private fun route(source: Source, page: Int = 0) =
            MenuRoute(OWNER, source.menuId, mapOf(PAGE to page.coerceAtLeast(0).toString()))
    }

    private enum class Source(val menuId: String) {
        LIBRARY(MENU_ID),
        HISTORY(HISTORY_MENU_ID),
    }
}
