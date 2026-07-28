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
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.DiskItemService
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
                        val source = context.payload["sourceId"]?.takeIf(String::isNotBlank)
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        val edge = context.payload["edge"]?.let {
                            runCatching { me.awabi2048.kantancommander.data.GraphEditor.Edge.valueOf(it) }.getOrNull()
                        } ?: if (source == null) me.awabi2048.kantancommander.data.GraphEditor.Edge.ENTRY
                        else me.awabi2048.kantancommander.data.GraphEditor.Edge.NEXT
                        val mergeCondition = context.payload["mergeConditionId"]?.takeIf(String::isNotBlank)
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        MenuActionResult.Success(MenuUpdate.Navigate(CommandEditMenu.typeRoute(context.route, source, edge, mergeCondition)))
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
                        MenuActionResult.Success(MenuUpdate.Navigate(CommandEditMenu.timerRoute(context.route)))
                    },
                    "center" to handler { context ->
                        if (scriptId(context.route)?.let(plugin.scripts::load) == null) {
                            return@handler MenuActionResult.Ignored
                        }
                        MenuActionResult.Success(MenuUpdate.Replace(route(context.route, 0, 0)))
                    },
                    "navigate" to handler { context -> navigate(context) },
                    "command" to handler { context ->
                        val script = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handler MenuActionResult.Ignored
                        val nodeId = context.payload["nodeId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@handler MenuActionResult.Ignored
                        val node = script.graph.nodes[nodeId] ?: return@handler MenuActionResult.Ignored
                        val target = if (context.click.isRightClick) {
                            CommandEditMenu.deleteRoute(context.route, node.id)
                        } else {
                            CommandEditMenu.settingsRoute(context.route, node.id)
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
                    "save_library" to handler { context ->
                        val source = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handler MenuActionResult.Ignored
                        plugin.scripts.copyToLibrary(source, context.player.uniqueId)
                        context.player.sendMessage(KcI18n.text(context.player, "message.saved"))
                        MenuActionResult.Success(MenuUpdate.Refresh)
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
        val delta = ViewportNavigation.delta(
            context.click.isLeftClick,
            context.click.isRightClick,
            context.click.isShiftClick,
        ) ?: return MenuActionResult.Ignored
        if (!layout.canMove(origin, delta.x, delta.y, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            return MenuActionResult.Ignored
        }
        val next = MapPoint(origin.x + delta.x, origin.y + delta.y)
        return MenuActionResult.Success(MenuUpdate.Replace(route(context.route, next.x, next.y)))
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val script = scriptId(route)?.let(plugin.scripts::load)
            ?: return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.title")), emptyList())
        val origin = origin(route)
        val layout = GraphLayoutEngine.layout(script.graph)
        val elements = mutableListOf<MenuElement>()

        layout.viewport(origin, VIEWPORT_WIDTH, VIEWPORT_HEIGHT).forEach { (point, cell) ->
            val slot = (point.y + 1) * 9 + point.x
            val node = cell.nodeId?.let(script.graph.nodes::get)
            elements += if (node != null) commandElement(player, slot, node) else pathElement(player, slot, cell)
        }
        val activationLore = mutableListOf<GuiLoreLine>(
            GuiLoreLine.Data(KcI18n.text(player, "gui.editor.current"), KcI18n.text(player, script.activation.key), "§f"),
        )
        if (script.timer.enabled) {
            activationLore += KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.activation"))
            elements += action(
                36,
                if (script.activation == me.awabi2048.kantancommander.model.ActivationMode.NEEDS_REDSTONE) Material.LEVER else Material.REDSTONE_TORCH,
                KcI18n.text(player, "gui.editor.activation"),
                "activation",
                activationLore,
            )
        } else {
            elements += MenuElement(
                36,
                KcGui.item(
                    Material.LEVER,
                    KcI18n.text(player, "gui.editor.activation"),
                    GuiNameStyle.PRIMARY,
                    activationLore,
                    GuiElementRole.CONTENT,
                ),
                GuiElementRole.CONTENT,
            )
        }
        elements += action(
            37,
            Material.CLOCK,
            KcI18n.text(player, "gui.editor.timer"),
            "timer",
            listOf(
                GuiLoreLine.Data(
                    KcI18n.text(player, "gui.editor.current"),
                    if (script.timer.enabled) {
                        KcI18n.text(player, "gui.editor.interval_units", mapOf("value" to script.timer.intervalUnits))
                    } else KcI18n.text(player, "gui.editor.disabled"),
                    "§f",
                ),
                KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.timer")),
            ),
        )
        elements += action(
            38,
            Material.COMPASS,
            KcI18n.text(player, "gui.editor.center"),
            "center",
            listOf(KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.center"))),
        )
        elements += MenuElement(
            39,
            KcGui.item(
                Material.MAP,
                KcI18n.text(player, "gui.editor.info"),
                GuiNameStyle.PRIMARY,
                listOf(GuiLoreLine.Component(GraphDiagramRenderer.render(layout, origin))),
                GuiElementRole.CONTENT,
            ),
            GuiElementRole.CONTENT,
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
            42,
            Material.BOOK,
            KcI18n.text(player, "gui.editor.save"),
            "save_library",
            listOf(KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.save"))),
        )
        elements += action(
            44,
            Material.RECOVERY_COMPASS,
            KcI18n.text(player, "gui.editor.navigate"),
            "navigate",
            listOf(
                KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.move_left")),
                KcGui.action(player, "lore.click.right", KcI18n.text(player, "gui.editor.move_right")),
                KcGui.action(player, "lore.click.shift_left", KcI18n.text(player, "gui.editor.move_up")),
                KcGui.action(player, "lore.click.shift_right", KcI18n.text(player, "gui.editor.move_down")),
            ),
        )
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.title")), elements)
    }

    private fun commandElement(player: Player, slot: Int, node: CommandNode) = MenuElement(
        slot,
        KcGui.item(
            node.type.icon,
            KcI18n.text(player, node.type.key),
            GuiNameStyle.DEFAULT,
            listOf(
                GuiLoreLine.Data(KcI18n.text(player, "gui.editor.setting"), commandSummary(player, node), "§f"),
                KcGui.action(player, "lore.click.left", KcI18n.text(player, "gui.editor.setting")),
                KcGui.action(player, "lore.click.right", KcI18n.text(player, "gui.editor.remove")),
            ),
            GuiElementRole.CONTENT,
        ),
        GuiElementRole.CONTENT,
        "command",
        mapOf("nodeId" to node.id.toString()),
    )

    private fun commandSummary(player: Player, node: CommandNode): String = when (node.type) {
        CommandType.TELEPORT -> {
            val target = node.targetSpec?.kind?.let { localizedTarget(player, it) }
                ?: KcI18n.text(player, "gui.field.unset")
            val destination = node.destinationTargetSpec?.kind?.let { localizedTarget(player, it) }
                ?: node.destinationSpec?.kind?.let { localizedPosition(player, it) }
                ?: KcI18n.text(player, "gui.field.unset")
            "$target → $destination"
        }
        CommandType.GIVE_ITEM -> "${node.string("item")} ×${node.int("count", 1)}"
        CommandType.DISPLAY_TEXT -> node.string("text").ifBlank { KcI18n.text(player, "gui.field.unset") }
        CommandType.WAIT -> "${node.int("ticks", 20)} tick"
        CommandType.CONDITION -> runCatching { ConditionKind.valueOf(node.string("kind")) }.getOrNull()
            ?.let { KcI18n.text(player, it.key) }
            ?: KcI18n.text(player, "gui.field.unset")
        CommandType.DISK_CALL ->
            KcI18n.text(player, if (node.snapshot == null) "gui.field.unset" else "gui.option.configured")
        CommandType.VARIABLE -> node.string("name").ifBlank { KcI18n.text(player, "gui.field.unset") }
        CommandType.FOR_START ->
            "${node.string("startValue")}..${node.string("endValue")} / ${node.string("stepValue")}"
        else -> KcI18n.text(player, node.type.key)
    }

    private fun localizedTarget(player: Player, kind: TargetKind): String = KcI18n.text(
        player,
        when (kind) {
            TargetKind.EXECUTOR -> "gui.option.executor"
            TargetKind.ACTIVATOR -> "gui.option.activator"
            TargetKind.INHERITED_TARGET -> "gui.option.inherited_target"
            TargetKind.NEAREST_PLAYER -> "gui.option.nearest_player"
            TargetKind.NEARBY_PLAYERS -> "gui.option.nearby_players"
            TargetKind.ALL_PLAYERS -> "gui.option.all_players"
            TargetKind.RANDOM_PLAYER -> "gui.option.random_player"
            TargetKind.NEAREST_ENTITY -> "gui.option.nearest_entity"
            TargetKind.NEARBY_ENTITIES -> "gui.option.nearby_entities"
            TargetKind.FIXED_ENTITY -> "gui.option.fixed_entity"
        }
    )

    private fun localizedPosition(player: Player, kind: PositionKind): String = KcI18n.text(
        player,
        when (kind) {
            PositionKind.CAPTURED -> "gui.option.current_position"
            PositionKind.DISK -> "gui.option.disk_position"
            PositionKind.EXECUTOR -> "gui.option.executor_position"
            PositionKind.TARGET -> "gui.option.target_position"
            PositionKind.MYWORLD_SPAWN -> "gui.option.myworld_spawn"
            PositionKind.COORDINATES -> "gui.option.coordinates"
            PositionKind.TEMPORARY_VARIABLE -> "gui.option.temporary_variable"
            PositionKind.WORLD_VARIABLE -> "gui.field.world_variable"
        }
    )

    private fun pathElement(player: Player, slot: Int, cell: MapCell): MenuElement {
        val insertionTarget = cell.insertionTarget
        if (insertionTarget != null) {
            return MenuElement(
                slot,
                KcGui.item(
                    when {
                        cell.kind == MapCellKind.ADD -> Material.YELLOW_WOOL
                        cell.kind == MapCellKind.BRANCH_PATH -> Material.CYAN_STAINED_GLASS_PANE
                        else -> Material.GRAY_STAINED_GLASS_PANE
                    },
                    KcI18n.text(player, if (cell.kind == MapCellKind.ADD) "gui.editor.add" else "gui.editor.insert"),
                    GuiNameStyle.PRIMARY,
                    listOf(KcGui.action(player, "lore.click.left", KcI18n.text(player, if (cell.kind == MapCellKind.ADD) "gui.editor.add" else "gui.editor.insert"))),
                    GuiElementRole.ACTION,
                ),
                GuiElementRole.ACTION,
                "add",
                mapOf(
                    "sourceId" to insertionTarget.sourceId?.toString().orEmpty(),
                    "edge" to insertionTarget.edge.name,
                    "mergeConditionId" to insertionTarget.mergeConditionId?.toString().orEmpty(),
                ),
            )
        }
        val material = when (cell.kind) {
            MapCellKind.LOOP_RETURN_PATH -> Material.LIGHT_BLUE_STAINED_GLASS_PANE
            MapCellKind.BRANCH_PATH -> Material.CYAN_STAINED_GLASS_PANE
            else -> Material.GRAY_STAINED_GLASS_PANE
        }
        return MenuElement(slot, KcGui.elements.decoration(material), GuiElementRole.DECORATION)
    }

    private fun action(
        slot: Int,
        material: Material,
        name: String,
        id: String,
        lore: List<GuiLoreLine> = emptyList(),
    ) = MenuElement(
        slot,
        KcGui.item(material, name, GuiNameStyle.PRIMARY, lore, GuiElementRole.ACTION),
        GuiElementRole.ACTION,
        id,
    )

    private fun placement(route: MenuRoute): DiskPlacement? {
        val placement = EditorSession.from(route)?.placement ?: return null
        return plugin.placements.find(
            plugin.server.getWorld(placement.world),
            placement.x,
            placement.y,
            placement.z,
        )
    }

    private fun outputDisk(player: Player, placement: DiskPlacement, removeBlock: Boolean): Boolean {
        val source = plugin.scripts.load(placement.scriptId) ?: return false
        if (!plugin.placementAccess.canManage(player, placement.world)) {
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
        private const val VIEWPORT_WIDTH = 9
        private const val VIEWPORT_HEIGHT = 3

        fun route(scriptId: UUID) = EditorSession.forScript(scriptId).route(OWNER, MENU_ID)

        fun route(placement: DiskPlacement) = EditorSession.forPlacement(placement).route(OWNER, MENU_ID)

        fun editorRoute(current: MenuRoute) =
            EditorSession.from(current)?.route(OWNER, MENU_ID) ?: current.copy(id = MENU_ID)

        private fun route(current: MenuRoute, x: Int, y: Int) =
            EditorSession.from(current)?.withOrigin(MapPoint(x, y))?.route(OWNER, MENU_ID)
                ?: current.copy(id = MENU_ID)

        private fun origin(route: MenuRoute) = EditorSession.from(route)?.origin ?: MapPoint(0, 0)

        private fun scriptId(route: MenuRoute) = EditorSession.from(route)?.scriptId
    }
}

internal object GraphDiagramRenderer {
    private const val MAX_WIDTH = 21
    private const val MAX_HEIGHT = 9

    fun render(layout: GraphLayout, origin: MapPoint): Component {
        val fromX = (origin.x - 4).coerceAtLeast(0)
            .coerceAtMost((layout.width - MAX_WIDTH).coerceAtLeast(0))
        val toX = (fromX + MAX_WIDTH - 1).coerceAtMost(layout.width - 1)
        val fromY = (origin.y - 3).coerceAtLeast(0)
            .coerceAtMost((layout.height - MAX_HEIGHT).coerceAtLeast(0))
        val toY = (fromY + MAX_HEIGHT - 1).coerceAtMost(layout.height - 1)
        val result = Component.text()
        var hasLine = false
        fun nextLine() {
            if (hasLine) result.append(Component.newline())
            hasLine = true
        }
        if (fromY > 0) {
            nextLine()
            result.append(Component.text("⋮", NamedTextColor.GRAY))
        }
        for (y in fromY..toY) {
            nextLine()
            if (fromX > 0) result.append(Component.text("…", NamedTextColor.GRAY))
            for (x in fromX..toX) {
                val selected = x in origin.x until origin.x + 9 &&
                    y in origin.y until origin.y + 3
                val occupied = layout.cells.containsKey(MapPoint(x, y))
                result.append(Component.text(
                    if (occupied) "■" else " ",
                    if (selected && occupied) NamedTextColor.YELLOW else NamedTextColor.GRAY,
                ))
            }
            if (toX < layout.width - 1) result.append(Component.text("…", NamedTextColor.GRAY))
        }
        if (toY < layout.height - 1) {
            nextLine()
            result.append(Component.text("⋮", NamedTextColor.GRAY))
        }
        return result.build()
    }
}

object ViewportNavigation {
    fun delta(left: Boolean, right: Boolean, shift: Boolean): MapPoint? = when {
        shift && left && !right -> MapPoint(0, -1)
        shift && right && !left -> MapPoint(0, 1)
        !shift && left && !right -> MapPoint(-1, 0)
        !shift && right && !left -> MapPoint(1, 0)
        else -> null
    }
}
