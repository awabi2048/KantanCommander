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
import me.awabi2048.kantancommander.model.CommandParam
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player

class CommandEditMenu(private val plugin: KantanCommanderPlugin) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                OWNER,
                TYPE_MENU_ID,
                renderer = { context -> renderTypePicker(context.player, context.route) },
                actions = mapOf(
                    ACTION_BACK to handle { MenuActionResult.Success(MenuUpdate.Back) },
                    ACTION_TYPE to handle { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handle MenuActionResult.Ignored
                        val type = context.payload[TYPE]?.let { name -> CommandType.entries.firstOrNull { it.name == name } }
                            ?: return@handle MenuActionResult.Ignored
                        val max = plugin.config.getInt("max-commands-per-disk", 32)
                        if (script.commands.size >= max) {
                            context.player.sendMessage(KcI18n.text(context.player, "message.max_commands", mapOf("max" to max)))
                            return@handle MenuActionResult.Rejected()
                        }
                        script.commands.add(type.newCommand())
                        plugin.scripts.save(script)
                        MenuActionResult.Success(MenuUpdate.Replace(paramsRoute(script.id, script.commands.lastIndex)))
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                OWNER,
                PARAMS_MENU_ID,
                renderer = { context -> renderParamEditor(context.player, context.route) },
                actions = mapOf(
                    ACTION_BACK to handle { MenuActionResult.Success(MenuUpdate.Back) },
                    ACTION_PARAM to handle { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handle MenuActionResult.Ignored
                        val commandIndex = context.route.payload[COMMAND_INDEX]?.toIntOrNull()
                            ?: return@handle MenuActionResult.Ignored
                        val command = script.commands.getOrNull(commandIndex)
                            ?: return@handle MenuActionResult.Ignored
                        val paramId = context.payload[PARAM_ID] ?: return@handle MenuActionResult.Ignored
                        val param = command.type.params.firstOrNull { it.id == paramId }
                            ?: return@handle MenuActionResult.Ignored
                        command.params[param.id] = nextValue(param, command.params[param.id] ?: param.defaultValue)
                        plugin.scripts.save(script)
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                ),
            )
        )
    }

    fun openTypePicker(player: Player, scriptId: UUID) {
        runtime.open(player, typeRoute(scriptId))
    }

    fun openParamEditor(player: Player, scriptId: UUID, commandIndex: Int) {
        runtime.open(player, paramsRoute(scriptId, commandIndex))
    }

    private fun renderTypePicker(player: Player, route: MenuRoute): InventoryMenuView {
        val layout = KcGui.layouts.free45()
        val slots = listOf(10, 11, 12, 13, 14, 15, 16)
        val elements = CommandType.entries.mapIndexed { index, type ->
            MenuElement(
                slots[index],
                KcGui.item(
                    type.icon,
                    KcI18n.text(player, type.key),
                    GuiNameStyle.PRIMARY,
                    type.params.map { GuiLoreLine.Text(KcI18n.text(player, it.key)) },
                ),
                GuiElementRole.ACTION,
                ACTION_TYPE,
                mapOf(TYPE to type.name),
            )
        }.toMutableList()
        elements += MenuElement(layout.backSlot, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")), GuiElementRole.BACK, ACTION_BACK)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, "gui.type.title")), elements)
    }

    private fun renderParamEditor(player: Player, route: MenuRoute): InventoryMenuView {
        val script = scriptId(route)?.let(plugin.scripts::load)
        val commandIndex = route.payload[COMMAND_INDEX]?.toIntOrNull()
        val command = commandIndex?.let { script?.commands?.getOrNull(it) }
        val layout = KcGui.layouts.settings54()
        if (script == null || commandIndex == null || command == null) {
            return InventoryMenuView(
                layout.size,
                KcGui.title(KcI18n.text(player, "gui.params.title", mapOf("type" to "-"))),
                listOf(MenuElement(layout.backSlot, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")), GuiElementRole.BACK, ACTION_BACK)),
            )
        }
        val slots = (10..16) + (19..25)
        val elements = command.type.params.mapIndexed { index, param ->
            val value = command.params[param.id] ?: param.defaultValue
            MenuElement(
                slots[index],
                KcGui.item(
                    icon(param),
                    "${KcI18n.text(player, param.key)}: $value",
                    GuiNameStyle.PRIMARY,
                    listOf(KcGui.singleAction(player, KcI18n.text(player, "gui.params.action_cycle"))),
                ),
                GuiElementRole.ACTION,
                ACTION_PARAM,
                mapOf(PARAM_ID to param.id),
            )
        }.toMutableList()
        elements += MenuElement(layout.backSlot, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")), GuiElementRole.BACK, ACTION_BACK)
        elements += MenuElement(layout.infoSlot, KcGui.item(Material.BOOK, "#${commandIndex + 1}", GuiNameStyle.MUTED), GuiElementRole.DECORATION)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, "gui.params.title", mapOf("type" to KcI18n.text(player, command.type.key)))),
            elements,
        )
    }

    private fun nextValue(param: CommandParam, current: String): String = when (param) {
        is CommandParam.Choice -> param.options[(param.options.indexOf(current).takeIf { it >= 0 } ?: 0).let { (it + 1) % param.options.size }]
        is CommandParam.Number -> (current.toDoubleOrNull()?.plus(1.0) ?: param.defaultValue.toDouble()).toString().removeSuffix(".0")
        is CommandParam.Text -> current
    }

    private fun icon(param: CommandParam): Material = when (param) {
        is CommandParam.Choice -> Material.COMPARATOR
        is CommandParam.Number -> Material.REPEATER
        is CommandParam.Text -> Material.PAPER
    }

    private fun handle(block: (com.awabi2048.ccsystem.api.gui.MenuActionContext) -> MenuActionResult) =
        MenuActionHandler(block)

    companion object {
        const val OWNER = ProgramListMenu.OWNER
        const val TYPE_MENU_ID = "type"
        const val PARAMS_MENU_ID = "params"
        private const val SCRIPT_ID = "scriptId"
        private const val COMMAND_INDEX = "commandIndex"
        private const val TYPE = "type"
        private const val PARAM_ID = "paramId"
        private const val ACTION_BACK = "back"
        private const val ACTION_TYPE = "type"
        private const val ACTION_PARAM = "param"

        fun typeRoute(scriptId: UUID) = MenuRoute(OWNER, TYPE_MENU_ID, mapOf(SCRIPT_ID to scriptId.toString()))

        fun paramsRoute(scriptId: UUID, commandIndex: Int) = MenuRoute(
            OWNER,
            PARAMS_MENU_ID,
            mapOf(SCRIPT_ID to scriptId.toString(), COMMAND_INDEX to commandIndex.toString()),
        )

        private fun scriptId(route: MenuRoute): UUID? =
            route.payload[SCRIPT_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
