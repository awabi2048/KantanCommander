package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiItemSpec
import com.awabi2048.ccsystem.api.gui.GuiLoreBlock
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiLoreSpec
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiMenuDisplaySpec
import com.awabi2048.ccsystem.api.gui.GuiNameSpec
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuAcceptedClicks
import com.awabi2048.ccsystem.api.gui.MenuActionBranch
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuInteraction
import com.awabi2048.ccsystem.api.gui.MenuGesture
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
        val activationActions = if (script.timer.enabled) {
            listOf(
                GuiMenuActionIntent.AnyClick(
                    actionId = "activation",
                    label = KcI18n.text(player, "gui.editor.activation_action"),
                ),
            )
        } else emptyList()
        elements += KcGui.menuEntry(
            player = player,
            slot = 36,
            material = if (script.activation == me.awabi2048.kantancommander.model.ActivationMode.NEEDS_REDSTONE) {
                Material.LEVER
            } else Material.REDSTONE_TORCH,
            name = KcI18n.text(player, "gui.editor.activation"),
            style = GuiNameStyle.PRIMARY,
            role = if (activationActions.isEmpty()) GuiElementRole.CONTENT else GuiElementRole.ACTION,
            description = KcI18n.list(player, "gui.editor.activation_description"),
            data = listOf(
                GuiMenuEntryData(
                    KcI18n.text(player, "gui.editor.activation_mode_label"),
                    KcI18n.text(player, script.activation.key),
                    GuiValueTone.DEFAULT,
                ),
            ),
            warnings = if (script.timer.enabled) emptyList() else {
                KcI18n.list(player, "gui.editor.activation_timer_required")
            },
            actions = activationActions,
        )
        elements += KcGui.menuEntry(
            player = player,
            slot = 37,
            material = Material.CLOCK,
            name = KcI18n.text(player, "gui.editor.timer"),
            style = GuiNameStyle.PRIMARY,
            description = KcI18n.list(player, "gui.editor.timer_description"),
            data = buildList {
                add(
                    GuiMenuEntryData(
                        KcI18n.text(player, "gui.editor.state_label"),
                        KcI18n.text(player, if (script.timer.enabled) "gui.editor.enabled" else "gui.editor.disabled"),
                        if (script.timer.enabled) GuiValueTone.SUCCESS else GuiValueTone.MUTED,
                    ),
                )
                if (script.timer.enabled) {
                    add(
                        GuiMenuEntryData(
                            KcI18n.text(player, "gui.editor.interval_label"),
                            KcI18n.text(player, "gui.editor.interval_units", mapOf("value" to script.timer.intervalUnits)),
                            GuiValueTone.DEFAULT,
                        ),
                    )
                }
            },
            actions = listOf(
                GuiMenuActionIntent.AnyClick("timer", KcI18n.text(player, "gui.editor.timer_action")),
            ),
        )
        elements += KcGui.menuEntry(
            player = player,
            slot = 38,
            material = Material.COMPASS,
            name = KcI18n.text(player, "gui.editor.center"),
            style = GuiNameStyle.PRIMARY,
            description = KcI18n.list(player, "gui.editor.center_description"),
            actions = listOf(
                GuiMenuActionIntent.AnyClick("center", KcI18n.text(player, "gui.editor.center_action")),
            ),
        )
        elements += KcGui.elements.menuDisplay(
            GuiMenuDisplaySpec(
                slot = 39,
                item = GuiItemSpec(
                    material = Material.MAP,
                    name = GuiNameSpec.Text(KcI18n.text(player, "gui.editor.info"), GuiNameStyle.PRIMARY),
                    lore = GuiLoreSpec.Blocks(
                        listOf(
                            GuiLoreBlock(KcI18n.list(player, "gui.editor.info_description").map(GuiLoreLine::Text)),
                            // セルの色自体が表示範囲を表すため、図は文字列化せずAdventure Componentで保持します。
                            GuiLoreBlock(listOf(GuiLoreLine.Component(GraphDiagramRenderer.render(layout, origin)))),
                            GuiLoreBlock(
                                listOf(
                                    GuiLoreLine.Data(
                                        KcI18n.text(player, "gui.editor.info_viewport_label"),
                                        KcI18n.text(player, "gui.editor.info_viewport_value"),
                                        "§e",
                                    ),
                                    GuiLoreLine.Data(
                                        KcI18n.text(player, "gui.editor.info_omitted_label"),
                                        KcI18n.text(player, "gui.editor.info_omitted_value"),
                                        "§7",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    role = GuiElementRole.CONTENT,
                    amount = 1,
                ),
            ),
        )
        if (placement(route) != null) {
            elements += KcGui.menuEntry(
                player = player,
                slot = 40,
                material = Material.MUSIC_DISC_13,
                name = KcI18n.text(player, "gui.editor.output"),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, "gui.editor.output_description"),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick("output", KcI18n.text(player, "gui.editor.output_copy")),
                ),
            )
            elements += KcGui.menuEntry(
                player = player,
                slot = 41,
                material = Material.RED_CONCRETE,
                name = KcI18n.text(player, "gui.editor.remove"),
                style = GuiNameStyle.DANGER,
                description = KcI18n.list(player, "gui.editor.remove_description"),
                warnings = listOf(KcI18n.text(player, "gui.editor.remove_warning")),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick("remove", KcI18n.text(player, "gui.editor.remove_action")),
                ),
            )
        }
        elements += KcGui.menuEntry(
            player = player,
            slot = 42,
            material = Material.BOOK,
            name = KcI18n.text(player, "gui.editor.save"),
            style = GuiNameStyle.PRIMARY,
            description = KcI18n.list(player, "gui.editor.save_description"),
            actions = listOf(
                GuiMenuActionIntent.AnyClick("save_library", KcI18n.text(player, "gui.editor.save_action")),
            ),
        )
        elements += KcGui.menuEntry(
            player = player,
            slot = 44,
            material = Material.RECOVERY_COMPASS,
            name = KcI18n.text(player, "gui.editor.navigate"),
            style = GuiNameStyle.PRIMARY,
            description = KcI18n.list(player, "gui.editor.navigate_description"),
            actions = listOf(
                GuiMenuActionIntent.GestureAction("navigate", MenuGesture.PLAIN_LEFT, KcI18n.text(player, "gui.editor.move_left")),
                GuiMenuActionIntent.GestureAction("navigate", MenuGesture.PLAIN_RIGHT, KcI18n.text(player, "gui.editor.move_right")),
                GuiMenuActionIntent.GestureAction("navigate", MenuGesture.SHIFT_LEFT, KcI18n.text(player, "gui.editor.move_up")),
                GuiMenuActionIntent.GestureAction("navigate", MenuGesture.SHIFT_RIGHT, KcI18n.text(player, "gui.editor.move_down")),
            ),
        )
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.title")), elements)
    }

    private fun commandElement(player: Player, slot: Int, node: CommandNode) = KcGui.menuEntry(
        player = player,
        slot = slot,
        material = node.type.icon,
        name = KcI18n.text(player, node.type.key),
        role = GuiElementRole.ACTION,
        description = KcI18n.list(player, "${node.type.key}_description"),
        data = listOf(
            GuiMenuEntryData(
                KcI18n.text(player, "gui.editor.settings_summary_label"),
                commandSummary(player, node),
            ),
        ),
        actions = listOf(
            GuiMenuActionIntent.GestureAction(
                "command",
                MenuGesture.PLAIN_LEFT,
                KcI18n.text(player, "gui.editor.edit_action"),
                mapOf("nodeId" to node.id.toString()),
            ),
            GuiMenuActionIntent.GestureAction(
                "command",
                MenuGesture.PLAIN_RIGHT,
                KcI18n.text(player, "gui.editor.delete_action"),
                mapOf("nodeId" to node.id.toString()),
            ),
        ),
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
            val addAtEnd = cell.kind == MapCellKind.ADD
            return KcGui.menuEntry(
                player = player,
                slot = slot,
                material = MapCellMaterialPolicy.material(cell.kind),
                name = KcI18n.text(player, if (addAtEnd) "gui.editor.add" else "gui.editor.insert"),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(
                    player,
                    if (addAtEnd) "gui.editor.add_description" else "gui.editor.insert_description",
                ),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick(
                        actionId = "add",
                        label = KcI18n.text(player, if (addAtEnd) "gui.editor.add" else "gui.editor.insert"),
                        payload = mapOf(
                    "sourceId" to insertionTarget.sourceId?.toString().orEmpty(),
                    "edge" to insertionTarget.edge.name,
                    "mergeConditionId" to insertionTarget.mergeConditionId?.toString().orEmpty(),
                        ),
                    ),
                ),
            )
        }
        val material = MapCellMaterialPolicy.material(cell.kind)
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
        interaction = if (id == "navigate") {
            MenuInteraction.Branches(
                listOf(
                    MenuActionBranch(id, MenuAcceptedClicks.PLAIN_LEFT),
                    MenuActionBranch(id, MenuAcceptedClicks.PLAIN_RIGHT),
                    MenuActionBranch(id, MenuAcceptedClicks.SHIFT_LEFT),
                    MenuActionBranch(id, MenuAcceptedClicks.SHIFT_RIGHT),
                ),
            )
        } else null,
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
        val visibleX = OverviewAxis.select(layout.width, origin.x, 9, MAX_WIDTH)
        val visibleY = OverviewAxis.select(layout.height, origin.y, 3, MAX_HEIGHT)
        val result = Component.text()
        var hasLine = false
        fun nextLine() {
            if (hasLine) result.append(Component.newline())
            hasLine = true
        }
        var previousY: Int? = null
        for (y in visibleY) {
            if (previousY != null && y > previousY + 1) {
                nextLine()
                result.append(Component.text("⋮", NamedTextColor.GRAY))
            }
            nextLine()
            var previousX: Int? = null
            for (x in visibleX) {
                if (previousX != null && x > previousX + 1) {
                    result.append(Component.text("…", NamedTextColor.GRAY))
                }
                val selected = x in origin.x until origin.x + 9 &&
                    y in origin.y until origin.y + 3
                val occupied = layout.cells.containsKey(MapPoint(x, y))
                result.append(Component.text(
                    if (occupied) "■" else " ",
                    if (selected && occupied) NamedTextColor.YELLOW else NamedTextColor.GRAY,
                ))
                previousX = x
            }
            previousY = y
        }
        return result.build()
    }
}

/**
 * 挿入可能経路と装飾経路で素材がずれないよう、論理マップの素材対応を一箇所で管理します。
 * 実行順序と逆向きに戻るfor経路だけは、通常経路と異なる色で表示します。
 */
internal object MapCellMaterialPolicy {
    fun material(kind: MapCellKind): Material = when (kind) {
        MapCellKind.ADD -> Material.YELLOW_STAINED_GLASS_PANE
        MapCellKind.LOOP_RETURN_PATH -> Material.LIGHT_BLUE_STAINED_GLASS_PANE
        else -> Material.WHITE_STAINED_GLASS_PANE
    }
}

/**
 * マップの端と現在のビューポートを必ず残しながら、軸方向の概略表示座標を決定します。
 * 省略された座標範囲は、描画側が省略記号へ変換します。
 */
internal object OverviewAxis {
    fun select(size: Int, viewportStart: Int, viewportSize: Int, limit: Int): List<Int> {
        require(size > 0)
        require(limit > 0)
        if (size <= limit) return (0 until size).toList()

        val selected = linkedSetOf(0, size - 1)
        val viewportEnd = (viewportStart + viewportSize - 1).coerceAtMost(size - 1)
        for (coordinate in viewportStart.coerceAtLeast(0)..viewportEnd) selected += coordinate

        var distance = 1
        while (selected.size < limit) {
            val candidates = listOf(
                viewportStart - distance,
                viewportEnd + distance,
                distance,
                size - 1 - distance,
            )
            var added = false
            for (candidate in candidates) {
                if (candidate in 0 until size && selected.size < limit) {
                    added = selected.add(candidate) || added
                }
            }
            if (!added && candidates.all { it !in 0 until size }) break
            distance++
        }
        return selected.sorted()
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
