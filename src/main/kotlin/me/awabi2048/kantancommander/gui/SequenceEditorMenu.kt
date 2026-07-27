package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.DiskItemService
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

class SequenceEditorMenu(private val plugin: KantanCommanderPlugin) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                OWNER,
                MENU_ID,
                renderer = { render(it.player, it.route) },
                actions = mapOf(
                    "back" to handler { MenuActionResult.Success(MenuUpdate.Back) },
                    "add" to handler { context ->
                        val id = scriptId(context.route) ?: return@handler MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Navigate(CommandEditMenu.typeRoute(id, 0)))
                    },
                    "activation" to handler { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handler MenuActionResult.Ignored
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
                        val script = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handler MenuActionResult.Ignored
                        val layout = GraphLayoutEngine.layout(script.graph)
                        val x = ((layout.width - VIEWPORT_WIDTH) / 2).coerceAtLeast(0)
                        val y = ((layout.height - VIEWPORT_HEIGHT) / 2).coerceAtLeast(0)
                        MenuActionResult.Success(MenuUpdate.Replace(route(context.route, x, y)), MenuSoundPolicy.Silent)
                    },
                    "navigate" to handler { context -> navigate(context) },
                    "command" to handler { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handler MenuActionResult.Ignored
                        val nodeId = context.payload["nodeId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@handler MenuActionResult.Ignored
                        val node = script.graph.nodes[nodeId] ?: return@handler MenuActionResult.Ignored
                        val target = if (context.click.isRightClick) {
                            CommandEditMenu.deleteRoute(script.id, node.id)
                        } else {
                            CommandEditMenu.settingsRoute(script.id, node.id)
                        }
                        MenuActionResult.Success(MenuUpdate.Navigate(target))
                    },
                    "output" to handler { context ->
                        val placement = placement(context.route) ?: return@handler MenuActionResult.Ignored
                        if (!outputDisk(context.player, placement, false)) return@handler MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "remove" to handler { context ->
                        val placement = placement(context.route) ?: return@handler MenuActionResult.Ignored
                        if (!outputDisk(context.player, placement, true)) return@handler MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
            ),
        )
    }

    fun open(player: Player, scriptId: UUID) = runtime.open(player, route(scriptId))
    fun open(player: Player, placement: DiskPlacement) = runtime.open(player, route(placement))

    private fun navigate(context: MenuActionContext): MenuActionResult {
        val script = scriptId(context.route)?.let(plugin.scripts::load) ?: return MenuActionResult.Ignored
        val layout = GraphLayoutEngine.layout(script.graph)
        val origin = origin(context.route)
        val delta = when {
            context.click.isShiftClick && context.click.isLeftClick -> MapPoint(0, -1)
            context.click.isShiftClick && context.click.isRightClick -> MapPoint(0, 1)
            !context.click.isShiftClick && context.click.isLeftClick -> MapPoint(-1, 0)
            !context.click.isShiftClick && context.click.isRightClick -> MapPoint(1, 0)
            else -> return MenuActionResult.Ignored
        }
        if (!layout.canMove(origin, delta.x, delta.y, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            return MenuActionResult.Ignored
        }
        val next = MapPoint(origin.x + delta.x, origin.y + delta.y)
        return MenuActionResult.Success(MenuUpdate.Replace(route(context.route, next.x, next.y)), MenuSoundPolicy.Silent)
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val script = scriptId(route)?.let(plugin.scripts::load)
            ?: return InventoryMenuView(45, KcGui.title("コマンドディスク"), emptyList())
        val origin = origin(route)
        val layout = GraphLayoutEngine.layout(script.graph)
        val elements = mutableListOf<MenuElement>()

        layout.viewport(origin, VIEWPORT_WIDTH, VIEWPORT_HEIGHT).forEach { (point, cell) ->
            val slot = (point.y + 1) * 9 + point.x
            val node = cell.nodeId?.let(script.graph.nodes::get)
            elements += if (node != null) commandElement(player, slot, node) else pathElement(slot, cell.kind)
        }
        addPoint(script.graph, layout)?.let { point ->
            val local = MapPoint(point.x - origin.x, point.y - origin.y)
            if (local.x in 0 until VIEWPORT_WIDTH && local.y in 0 until VIEWPORT_HEIGHT) {
                val slot = (local.y + 1) * 9 + local.x
                elements.removeAll { it.slot == slot }
                elements += MenuElement(
                    slot,
                    KcGui.item(Material.YELLOW_WOOL, "コマンドを追加", GuiNameStyle.PRIMARY),
                    GuiElementRole.ACTION,
                    "add",
                )
            }
        }

        elements += action(
            36,
            if (script.activation == me.awabi2048.kantancommander.model.ActivationMode.NEEDS_REDSTONE) Material.LEVER else Material.REDSTONE_TORCH,
            "起動条件",
            "activation",
            listOf(GuiLoreLine.Data("現在", KcI18n.text(player, script.activation.key), "§f")),
        )
        elements += action(
            37,
            Material.CLOCK,
            "タイマー設定",
            "timer",
            listOf(GuiLoreLine.Data("現在", if (script.timer.enabled) "${script.timer.intervalUnits}単位" else "オフ", "§f")),
        )
        elements += action(38, Material.COMPASS, "中心に合わせる", "center")
        elements += MenuElement(
            39,
            KcGui.item(
                Material.MAP,
                "全体情報",
                GuiNameStyle.PRIMARY,
                listOf(GuiLoreLine.Text(graphDiagram(layout, origin))),
            ),
            GuiElementRole.DECORATION,
        )
        if (placement(route) != null) {
            elements += action(
                40,
                Material.MUSIC_DISC_13,
                KcI18n.text(player, "gui.editor.output"),
                "output",
                listOf(KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.output_copy"))),
            )
            elements += action(
                41,
                Material.RED_CONCRETE,
                KcI18n.text(player, "gui.editor.remove"),
                "remove",
                listOf(
                    GuiLoreLine.Warning(KcI18n.text(player, "gui.editor.remove_warning")),
                    KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.remove")),
                ),
            )
        }
        elements += action(
            44,
            Material.RECOVERY_COMPASS,
            "表示位置を移動",
            "navigate",
            listOf(
                KcGui.action(player, "lore.click.left", "左へ移動"),
                KcGui.action(player, "lore.click.right", "右へ移動"),
                KcGui.action(player, "lore.click.shift_left", "上へ移動"),
                KcGui.action(player, "lore.click.shift_right", "下へ移動"),
            ),
        )
        return InventoryMenuView(45, KcGui.title("コマンドディスク"), elements)
    }

    private fun commandElement(player: Player, slot: Int, node: CommandNode) = MenuElement(
        slot,
        KcGui.item(
            node.type.icon,
            KcI18n.text(player, node.type.key),
            GuiNameStyle.DEFAULT,
            listOf(GuiLoreLine.Data("設定", node.summary(), "§f")),
        ),
        GuiElementRole.CONTENT,
        "command",
        mapOf("nodeId" to node.id.toString()),
    )

    private fun pathElement(slot: Int, kind: MapCellKind): MenuElement {
        val material = when (kind) {
            MapCellKind.LOOP_RETURN_PATH -> Material.LIGHT_BLUE_STAINED_GLASS_PANE
            MapCellKind.BRANCH_PATH -> Material.CYAN_STAINED_GLASS_PANE
            else -> Material.GRAY_STAINED_GLASS_PANE
        }
        return MenuElement(slot, KcGui.elements.decoration(material), GuiElementRole.DECORATION)
    }

    private fun graphDiagram(layout: GraphLayout, origin: MapPoint): String {
        val fromX = (origin.x - 4).coerceAtLeast(0)
        val toX = (fromX + 20).coerceAtMost(layout.width - 1)
        val prefix = if (fromX > 0) "…" else ""
        val suffix = if (toX < layout.width - 1) "…" else ""
        return (0 until layout.height).joinToString("\n") { y ->
            val body = (fromX..toX).joinToString("") { x ->
                val selected = x in origin.x until origin.x + VIEWPORT_WIDTH &&
                    y in origin.y until origin.y + VIEWPORT_HEIGHT
                val symbol = when (layout.cells[MapPoint(x, y)]?.kind) {
                    MapCellKind.NODE -> "○"
                    MapCellKind.LOOP_RETURN_PATH -> "═"
                    MapCellKind.BRANCH_PATH -> "─"
                    MapCellKind.PATH -> "─"
                    null -> " "
                }
                (if (selected) "§e" else "§7") + symbol
            }
            "§7$prefix$body§7$suffix"
        }
    }

    private fun addPoint(graph: CommandGraph, layout: GraphLayout): MapPoint? {
        if (graph.entryNodeId == null) return MapPoint(1, 1)
        var current = graph.entryNodeId
        val visited = mutableSetOf<UUID>()
        var tail: CommandNode? = null
        while (current != null && visited.add(current)) {
            tail = graph.nodes[current] ?: break
            current = if (tail.type == CommandType.CONDITION) tail.trueNext else tail.next
        }
        val point = tail?.id?.let(layout.nodePoints::get) ?: return null
        return MapPoint(point.x + 2, point.y)
    }

    private fun action(
        slot: Int,
        material: Material,
        name: String,
        id: String,
        lore: List<GuiLoreLine> = emptyList(),
    ) = MenuElement(slot, KcGui.item(material, name, GuiNameStyle.PRIMARY, lore), GuiElementRole.ACTION, id)

    private fun placement(route: MenuRoute): DiskPlacement? {
        val world = route.payload[WORLD] ?: return null
        val x = route.payload[X]?.toIntOrNull() ?: return null
        val y = route.payload[Y]?.toIntOrNull() ?: return null
        val z = route.payload[Z]?.toIntOrNull() ?: return null
        return plugin.placements.find(plugin.server.getWorld(world), x, y, z)
    }

    private fun outputDisk(player: Player, placement: DiskPlacement, removeBlock: Boolean): Boolean {
        val source = plugin.scripts.load(placement.scriptId) ?: return false
        if (!plugin.placementAccess.canManage(player, placement.world, source.owner)) {
            player.sendMessage(KcI18n.text(player, "message.no_placement_access"))
            return false
        }
        val world = plugin.server.getWorld(placement.world) ?: return false
        val block = world.getBlockAt(placement.x, placement.y, placement.z)
        if (plugin.placements.find(block.location)?.scriptId != source.id) return false

        val output = runCatching { plugin.scripts.copyForItem(source) }.getOrNull() ?: return false
        val item = runCatching { DiskItemService.create(output, player) }.getOrElse {
            plugin.scripts.delete(output.id)
            return false
        }
        player.inventory.addItem(item).values.forEach { world.dropItemNaturally(player.location, it) }
        player.sendMessage(KcI18n.text(player, "message.disk_output"))

        if (removeBlock) {
            plugin.placements.removeDisplay(world, placement.displayId)
            plugin.placements.remove(world, placement.x, placement.y, placement.z)
            block.setType(Material.AIR, false)
            plugin.scripts.delete(source.id)
            player.sendMessage(KcI18n.text(player, "message.placement_removed"))
        }
        return true
    }

    private fun handler(block: (MenuActionContext) -> MenuActionResult) = MenuActionHandler(block)

    companion object {
        const val OWNER = ProgramListMenu.OWNER
        private const val MENU_ID = "editor"
        private const val SCRIPT_ID = "scriptId"
        private const val WORLD = "world"
        private const val X = "x"
        private const val Y = "y"
        private const val Z = "z"
        private const val VIEWPORT_WIDTH = 9
        private const val VIEWPORT_HEIGHT = 3

        fun route(scriptId: UUID) = MenuRoute(
            OWNER,
            MENU_ID,
            mapOf(SCRIPT_ID to scriptId.toString(), "originX" to "0", "originY" to "0"),
        )

        fun route(placement: DiskPlacement) = MenuRoute(
            OWNER,
            MENU_ID,
            mapOf(
                SCRIPT_ID to placement.scriptId.toString(),
                "originX" to "0",
                "originY" to "0",
                WORLD to placement.world,
                X to placement.x.toString(),
                Y to placement.y.toString(),
                Z to placement.z.toString(),
            ),
        )

        private fun route(current: MenuRoute, x: Int, y: Int) = current.copy(
            payload = current.payload + ("originX" to x.toString()) + ("originY" to y.toString()),
        )

        private fun origin(route: MenuRoute) = MapPoint(
            route.payload["originX"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            route.payload["originY"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        )

        private fun scriptId(route: MenuRoute) =
            route.payload[SCRIPT_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
