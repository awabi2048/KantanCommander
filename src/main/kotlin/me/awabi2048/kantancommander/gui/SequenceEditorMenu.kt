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
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player

class SequenceEditorMenu(private val plugin: KantanCommanderPlugin) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                OWNER,
                MENU_ID,
                renderer = { context -> render(context.player, context.route) },
                actions = mapOf(
                    ACTION_BACK to handle { MenuActionResult.Success(MenuUpdate.Back) },
                    ACTION_ADD to handle { context ->
                        val scriptId = scriptId(context.route) ?: return@handle MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Navigate(CommandEditMenu.typeRoute(scriptId)))
                    },
                    ACTION_TEST to handle { context ->
                        val scriptId = scriptId(context.route) ?: return@handle MenuActionResult.Ignored
                        plugin.executor.execute(scriptId, context.player.location, context.player)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    ACTION_TRIGGER to handle { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handle MenuActionResult.Ignored
                        script.blockMode = script.blockMode.next()
                        plugin.scripts.save(script)
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    ACTION_ACTIVATION to handle { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load) ?: return@handle MenuActionResult.Ignored
                        script.activation = script.activation.next()
                        plugin.scripts.save(script)
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    ACTION_CONDITIONAL to handle { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load) ?: return@handle MenuActionResult.Ignored
                        script.conditional = !script.conditional
                        plugin.scripts.save(script)
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    ACTION_COPY_LIBRARY to handle { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load) ?: return@handle MenuActionResult.Ignored
                        plugin.scripts.copyToLibrary(script, context.player.uniqueId)
                        context.player.sendMessage(KcI18n.text(context.player, "message.copied_to_library"))
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    ACTION_SAVE to handle { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handle MenuActionResult.Ignored
                        plugin.scripts.save(script)
                        context.player.sendMessage(KcI18n.text(context.player, "message.saved"))
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    ACTION_COMMAND to handle { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handle MenuActionResult.Ignored
                        val index = context.payload[COMMAND_INDEX]?.toIntOrNull()
                            ?: return@handle MenuActionResult.Ignored
                        if (index !in script.commands.indices) return@handle MenuActionResult.Ignored
                        if (context.click.isRightClick) {
                            script.commands.removeAt(index)
                            plugin.scripts.save(script)
                            MenuActionResult.Success(MenuUpdate.Refresh)
                        } else {
                            MenuActionResult.Success(MenuUpdate.Navigate(CommandEditMenu.paramsRoute(script.id, index)))
                        }
                    },
                ),
            )
        )
    }

    fun open(player: Player, scriptId: UUID) {
        runtime.open(player, route(scriptId))
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val script = scriptId(route)?.let(plugin.scripts::load)
            ?: return unavailableView(player)
        val layout = KcGui.layouts.settings54()
        val elements = mutableListOf<MenuElement>()
        val commandSlots = (10..16) + (19..25) + (28..34)
        script.commands.take(commandSlots.size).forEachIndexed { index, command ->
            elements += MenuElement(
                commandSlots[index],
                KcGui.item(
                    command.type.icon,
                    "#${index + 1} ${KcI18n.text(player, command.type.key)}",
                    GuiNameStyle.DEFAULT,
                    listOf(
                        GuiLoreLine.Data(KcI18n.text(player, "gui.editor.summary"), command.summary(), "§f"),
                        GuiLoreLine.Spacer,
                        KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.action_edit")),
                        KcGui.action(player, "lore.click.right", KcI18n.text(player, "gui.editor.action_delete")),
                    ),
                ),
                GuiElementRole.CONTENT,
                ACTION_COMMAND,
                mapOf(COMMAND_INDEX to index.toString()),
            )
        }

        elements += action(37, Material.LIME_WOOL, KcI18n.text(player, "gui.editor.add"), ACTION_ADD)
        elements += action(38, Material.FIREWORK_ROCKET, KcI18n.text(player, "gui.editor.test"), ACTION_TEST)
        elements += action(
            39,
            Material.REDSTONE,
            KcI18n.text(player, "gui.editor.mode"),
            ACTION_TRIGGER,
            lore = listOf(GuiLoreLine.Data(KcI18n.text(player, "item.mode"), KcI18n.text(player, script.blockMode.key), "§f")),
        )
        elements += action(40, Material.LEVER, KcI18n.text(player, "gui.editor.activation"), ACTION_ACTIVATION,
            lore = listOf(GuiLoreLine.Data(KcI18n.text(player, "item.activation"), KcI18n.text(player, script.activation.key), "§f")))
        elements += action(41, Material.TRIPWIRE_HOOK, KcI18n.text(player, "gui.editor.condition"), ACTION_CONDITIONAL,
            lore = listOf(GuiLoreLine.Data(KcI18n.text(player, "item.condition"), KcI18n.text(player, if (script.conditional) "condition.conditional" else "condition.unconditional"), "§f")))
        elements += action(42, Material.DIAMOND, KcI18n.text(player, "gui.editor.save"), ACTION_SAVE, GuiElementRole.CONFIRM)
        elements += action(43, Material.WRITABLE_BOOK, KcI18n.text(player, "gui.editor.copy_library"), ACTION_COPY_LIBRARY)
        elements += MenuElement(layout.backSlot, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")), GuiElementRole.BACK, ACTION_BACK)
        elements += MenuElement(layout.infoSlot, KcGui.item(Material.BOOK, "${script.commands.size}", GuiNameStyle.MUTED), GuiElementRole.DECORATION)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, "gui.editor.title", mapOf("name" to script.name))),
            elements,
        )
    }

    private fun unavailableView(player: Player): InventoryMenuView {
        val layout = KcGui.layouts.settings54()
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, "gui.editor.title", mapOf("name" to "-"))),
            listOf(MenuElement(layout.backSlot, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")), GuiElementRole.BACK, ACTION_BACK)),
        )
    }

    private fun action(
        slot: Int,
        material: Material,
        name: String,
        actionId: String,
        role: GuiElementRole = GuiElementRole.ACTION,
        lore: List<GuiLoreLine> = emptyList(),
    ) = MenuElement(slot, KcGui.item(material, name, GuiNameStyle.PRIMARY, lore), role, actionId)

    private fun handle(block: (com.awabi2048.ccsystem.api.gui.MenuActionContext) -> MenuActionResult) =
        MenuActionHandler(block)

    companion object {
        const val OWNER = ProgramListMenu.OWNER
        const val MENU_ID = "editor"
        private const val SCRIPT_ID = "scriptId"
        private const val COMMAND_INDEX = "commandIndex"
        private const val ACTION_BACK = "back"
        private const val ACTION_ADD = "add"
        private const val ACTION_TEST = "test"
        private const val ACTION_TRIGGER = "trigger"
        private const val ACTION_ACTIVATION = "activation"
        private const val ACTION_CONDITIONAL = "conditional"
        private const val ACTION_COPY_LIBRARY = "copy_library"
        private const val ACTION_SAVE = "save"
        private const val ACTION_COMMAND = "command"

        fun route(scriptId: UUID) = MenuRoute(OWNER, MENU_ID, mapOf(SCRIPT_ID to scriptId.toString()))

        private fun scriptId(route: MenuRoute): UUID? =
            route.payload[SCRIPT_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
