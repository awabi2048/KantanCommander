package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

class CommandEditMenu(private val plugin: KantanCommanderPlugin) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                MENU_ID,
                renderer = { render(it.player, it.route) },
                actions = mapOf(
                    "back" to MenuActionHandler { MenuActionResult.Success(MenuUpdate.Back) },
                    "select" to MenuActionHandler { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load) ?: return@MenuActionHandler MenuActionResult.Ignored
                        val type = context.payload["type"]?.let { runCatching { CommandType.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        append(script.graph, type, context.route.payload["lane"]?.toIntOrNull() ?: 0)
                        plugin.scripts.save(script)
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val slots = listOf(11, 12, 13, 14, 15, 20, 21, 22, 23)
        val elements = CommandType.entries.mapIndexed { index, type ->
            MenuElement(
                slots[index],
                KcGui.item(
                    type.icon,
                    KcI18n.text(player, type.key),
                    GuiNameStyle.PRIMARY,
                    listOf(KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.add"))),
                ),
                GuiElementRole.ACTION,
                "select",
                mapOf("type" to type.name),
            )
        }.toMutableList()
        elements += MenuElement(36, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")), GuiElementRole.BACK, "back")
        return InventoryMenuView(45, KcGui.title("コマンドを追加"), elements)
    }

    private fun append(graph: me.awabi2048.kantancommander.model.CommandGraph, type: CommandType, lane: Int) {
        val node = type.newNode()
        val tailBefore = graph.nodes.values.lastOrNull { it.next == null && it.type != CommandType.CONDITION }
        val conditionBefore = graph.nodes.values.lastOrNull { it.type == CommandType.CONDITION }
        if (type == CommandType.CONDITION) {
            val merge = CommandType.MERGE.newNode()
            node.trueNext = merge.id
            node.falseNext = merge.id
            node.pairedNodeId = merge.id
            merge.pairedNodeId = node.id
            graph.nodes[node.id] = node
            graph.nodes[merge.id] = merge
        } else {
            graph.nodes[node.id] = node
        }
        if (lane > 0 && conditionBefore != null) {
            val oldFirst = conditionBefore.falseNext
            conditionBefore.falseNext = node.id
            if (type == CommandType.CONDITION) {
                node.pairedNodeId?.let(graph.nodes::get)?.next = oldFirst
            } else {
                node.next = oldFirst
            }
            return
        }
        if (graph.entryNodeId == null) {
            graph.entryNodeId = node.id
            return
        }
        if (tailBefore != null) tailBefore.next = node.id
    }

    companion object {
        private const val MENU_ID = "command_type"
        private const val SCRIPT_ID = "scriptId"
        fun typeRoute(scriptId: UUID, lane: Int = 0) = MenuRoute(SequenceEditorMenu.OWNER, MENU_ID, mapOf(SCRIPT_ID to scriptId.toString(), "lane" to lane.toString()))
        fun paramsRoute(scriptId: UUID, index: Int) = typeRoute(scriptId)
        private fun scriptId(route: MenuRoute) = route.payload[SCRIPT_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
