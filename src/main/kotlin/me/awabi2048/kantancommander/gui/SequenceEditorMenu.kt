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
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import net.kyori.adventure.text.Component
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.MAX_TIMER_UNITS
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

class SequenceEditorMenu(private val plugin: KantanCommanderPlugin) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                OWNER, MENU_ID, renderer = { render(it.player, it.route) },
                actions = mapOf(
                    "back" to handler { MenuActionResult.Success(MenuUpdate.Back) },
                    "add" to handler { context ->
                        val id = scriptId(context.route) ?: return@handler MenuActionResult.Ignored
                        val lane = context.route.payload["lane"]?.toIntOrNull() ?: 0
                        MenuActionResult.Success(MenuUpdate.Navigate(CommandEditMenu.typeRoute(id, lane)))
                    },
                    "activation" to handler { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load) ?: return@handler MenuActionResult.Ignored
                        if (!script.timer.enabled) return@handler MenuActionResult.Ignored
                        script.activation = script.activation.toggled(true)
                        plugin.scripts.save(script)
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "timer" to handler { context ->
                        val id = scriptId(context.route) ?: return@handler MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Navigate(CommandEditMenu.timerRoute(id)))
                    },
                    "center" to handler { context ->
                        MenuActionResult.Success(MenuUpdate.Replace(route(context.route, 0, 0)), MenuSoundPolicy.Silent)
                    },
                    "navigate" to handler { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load) ?: return@handler MenuActionResult.Ignored
                        val offset = context.route.payload["offset"]?.toIntOrNull() ?: 0
                        val lane = context.route.payload["lane"]?.toIntOrNull() ?: 0
                        val maximumLane = script.graph.nodes.values.count { it.type == me.awabi2048.kantancommander.model.CommandType.CONDITION }
                        val maximumOffset = (lanePath(script.graph, lane).size - 7).coerceAtLeast(0)
                        val changed = when {
                            context.click.isShiftClick && context.click.isLeftClick && lane > 0 -> route(context.route, offset, lane - 1)
                            context.click.isShiftClick && context.click.isRightClick && lane < maximumLane -> route(context.route, 0, lane + 1)
                            !context.click.isShiftClick && context.click.isLeftClick && offset > 0 -> route(context.route, offset - 1, lane)
                            !context.click.isShiftClick && context.click.isRightClick && offset < maximumOffset -> route(context.route, offset + 1, lane)
                            else -> null
                        } ?: return@handler MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Replace(changed), MenuSoundPolicy.Silent)
                    },
                    "command" to handler { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load) ?: return@handler MenuActionResult.Ignored
                        val nodeId = context.payload["nodeId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@handler MenuActionResult.Ignored
                        val node = script.graph.nodes[nodeId] ?: return@handler MenuActionResult.Ignored
                        if (context.click.isRightClick) {
                            MenuActionResult.Success(MenuUpdate.Navigate(CommandEditMenu.deleteRoute(script.id, node.id)))
                        } else {
                            MenuActionResult.Success(MenuUpdate.Navigate(CommandEditMenu.settingsRoute(script.id, node.id)))
                        }
                    },
                ),
            )
        )
    }

    fun open(player: Player, scriptId: UUID) = runtime.open(player, route(scriptId))

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val script = scriptId(route)?.let(plugin.scripts::load) ?: return InventoryMenuView(45, KcGui.title("コマンドディスク"), emptyList())
        val offset = route.payload["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val lane = route.payload["lane"]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
        val elements = mutableListOf<MenuElement>()
        val ordered = lanePath(script.graph, lane).drop(offset)
        ordered.take(7).forEachIndexed { index, node ->
            elements += MenuElement(
                19 + index,
                KcGui.item(node.type.icon, KcI18n.text(player, node.type.key), GuiNameStyle.DEFAULT,
                    listOf(GuiLoreLine.Data("設定", node.summary(), "§f"))),
                GuiElementRole.CONTENT,
                "command",
                mapOf("nodeId" to node.id.toString()),
            )
        }
        val addSlot = if (ordered.size < 7) 19 + ordered.size else 25
        elements.removeAll { it.slot == addSlot }
        elements += MenuElement(addSlot, KcGui.item(Material.YELLOW_WOOL, "コマンドを追加", GuiNameStyle.PRIMARY), GuiElementRole.ACTION, "add")
        elements += action(36, Material.LEVER, "起動条件", "activation",
            listOf(GuiLoreLine.Data("現在", KcI18n.text(player, script.activation.key), "§f")))
        elements += action(37, Material.CLOCK, "タイマー設定", "timer",
            listOf(GuiLoreLine.Data("現在", if (script.timer.enabled) "${script.timer.intervalUnits}単位" else "オフ", "§f")))
        elements += action(38, Material.COMPASS, "中心に合わせる", "center")
        elements += MenuElement(39, KcGui.item(Material.MAP, "全体情報", GuiNameStyle.PRIMARY,
            listOf(GuiLoreLine.Text(graphDiagram(script.graph.nodes.values.count { it.type == me.awabi2048.kantancommander.model.CommandType.CONDITION }, offset, lane)))), GuiElementRole.DECORATION)
        elements += action(44, Material.RECOVERY_COMPASS, "表示位置を移動", "navigate",
            listOf(
                KcGui.action(player, "lore.click.left", "左へ移動"),
                KcGui.action(player, "lore.click.right", "右へ移動"),
                KcGui.action(player, "lore.click.shift_left", "上へ移動"),
                KcGui.action(player, "lore.click.shift_right", "下へ移動"),
            ))
        return InventoryMenuView(45, KcGui.title("コマンドディスク"), elements)
    }

    private fun graphDiagram(branches: Int, offset: Int, lane: Int): String {
        val marker = if (lane == 0) "§e◆§7" else "◆"
        val falseMarker = if (lane > 0) "§e○§7" else "○"
        return if (branches == 0) "§7…─$marker─○─…" else "§7…─○─$marker─○─◇─…\n      └─$falseMarker─○─┘\n§8表示開始: ${offset + 1}"
    }

    private fun lanePath(graph: me.awabi2048.kantancommander.model.CommandGraph, lane: Int): List<me.awabi2048.kantancommander.model.CommandNode> {
        val conditions = graph.nodes.values.filter { it.type == me.awabi2048.kantancommander.model.CommandType.CONDITION }
        val start = if (lane == 0) graph.entryNodeId else conditions.getOrNull(lane - 1)?.falseNext
        val stop = if (lane == 0) null else conditions.getOrNull(lane - 1)?.pairedNodeId
        val result = mutableListOf<me.awabi2048.kantancommander.model.CommandNode>()
        val visited = mutableSetOf<UUID>()
        var current = start
        while (current != null && current != stop && visited.add(current)) {
            val node = graph.nodes[current] ?: break
            result += node
            current = if (node.type == me.awabi2048.kantancommander.model.CommandType.CONDITION) node.trueNext else node.next
        }
        return result
    }

    private fun action(slot: Int, material: Material, name: String, id: String, lore: List<GuiLoreLine> = emptyList()) =
        MenuElement(slot, KcGui.item(material, name, GuiNameStyle.PRIMARY, lore), GuiElementRole.ACTION, id)

    private fun handler(block: (com.awabi2048.ccsystem.api.gui.MenuActionContext) -> MenuActionResult) = MenuActionHandler(block)

    private fun showNodeDialog(player: Player, route: MenuRoute, scriptId: UUID, node: me.awabi2048.kantancommander.model.CommandNode) {
        val inputs = node.params.map { (key, value) ->
            MenuDialogInput.Text(key, Component.text(key), value, maxLength = 512)
        } + listOf(
            MenuDialogInput.Text("_ctx_executor", Component.text("個別実行者"), ""),
            MenuDialogInput.Text("_ctx_target", Component.text("個別対象"), ""),
            MenuDialogInput.Text("_ctx_position", Component.text("個別位置"), ""),
            MenuDialogInput.Text("_ctx_facing", Component.text("個別向き"), ""),
        )
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = "node-edit",
                title = Component.text("コマンドの詳細"),
                body = listOf(Component.text("値を設定してください。個別コンテキストはexecutor / target / position / facingで指定します。")),
                inputs = inputs,
                confirm = MenuDialogButton(Component.text("保存"), MenuDialogHandler { target, response ->
                    val script = plugin.scripts.load(scriptId) ?: return@MenuDialogHandler MenuActionResult.Ignored
                    val current = script.graph.nodes[node.id] ?: return@MenuDialogHandler MenuActionResult.Ignored
                    current.params.keys.toList().forEach { current.params[it] = response.textValue(it) }
                    current.contextOverride = null
                    if (current.type == me.awabi2048.kantancommander.model.CommandType.DISK_CALL &&
                        current.string("mode") == me.awabi2048.kantancommander.model.DiskCallMode.SNAPSHOT.name
                    ) {
                        current.snapshot = runCatching { UUID.fromString(current.string("diskId")) }.getOrNull()
                            ?.let(plugin.scripts::load)?.graph?.deepCopy()
                    }
                    plugin.scripts.save(script)
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(Component.text("戻る"), MenuDialogHandler { target, _ ->
                    MenuActionResult.Success(MenuUpdate.Replace(route), MenuSoundPolicy.Silent)
                }),
            )
        )
    }

    private fun showTimerDialog(player: Player, route: MenuRoute, scriptId: UUID, units: Int) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = "timer-edit",
                title = Component.text("タイマー設定"),
                body = listOf(Component.text("10 tick（0.5秒）を1単位として、1～86400を指定してください。")),
                inputs = listOf(MenuDialogInput.Text("units", Component.text("実行間隔"), units.toString(), maxLength = 5)),
                confirm = MenuDialogButton(Component.text("オンにする"), MenuDialogHandler { target, response ->
                    val value = response.textValue("units").toIntOrNull()
                    if (value == null || value !in 1..MAX_TIMER_UNITS) {
                        return@MenuDialogHandler MenuActionResult.Rejected(Component.text("1～86400で指定してください。"))
                    }
                    val script = plugin.scripts.load(scriptId) ?: return@MenuDialogHandler MenuActionResult.Ignored
                    script.timer.enabled = true
                    script.timer.intervalUnits = value
                    plugin.scripts.save(script)
                    plugin.placements.refreshDisplaysForScript(script.id)
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(Component.text("戻る"), MenuDialogHandler { target, _ ->
                    MenuActionResult.Success(MenuUpdate.Replace(route), MenuSoundPolicy.Silent)
                }),
            )
        )
    }

    companion object {
        const val OWNER = ProgramListMenu.OWNER
        private const val MENU_ID = "editor"
        private const val SCRIPT_ID = "scriptId"
        fun route(scriptId: UUID) = MenuRoute(OWNER, MENU_ID, mapOf(SCRIPT_ID to scriptId.toString(), "offset" to "0", "lane" to "0"))
        private fun route(current: MenuRoute, offset: Int, lane: Int) = current.copy(payload = current.payload + ("offset" to offset.toString()) + ("lane" to lane.toString()))
        private fun scriptId(route: MenuRoute) = route.payload[SCRIPT_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
