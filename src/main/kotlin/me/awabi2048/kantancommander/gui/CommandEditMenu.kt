package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.kantancommander.model.MAX_TIMER_UNITS
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.ActivationMode
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.ExecutionContextSpec
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

class CommandEditMenu(private val plugin: KantanCommanderPlugin) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()

    init {
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                PICKER_ID,
                renderer = { renderPicker(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val script = script(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        val type = context.payload["type"]?.let { runCatching { CommandType.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val sourceId = context.route.payload[SOURCE_ID]?.takeIf(String::isNotBlank)
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        val edge = context.route.payload[EDGE]?.let {
                            runCatching { GraphEditor.Edge.valueOf(it) }.getOrNull()
                        } ?: GraphEditor.Edge.ENTRY
                        val mergeConditionId = context.route.payload[MERGE_CONDITION_ID]?.takeIf(String::isNotBlank)
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        val node = if (type == CommandType.MERGE) {
                            GraphEditor.appendMerge(script.graph, requireNotNull(mergeConditionId))
                        } else {
                            GraphEditor.insert(script.graph, sourceId, edge, type)
                        }
                        plugin.scripts.save(script)
                        if (type in setOf(CommandType.MERGE, CommandType.BREAK, CommandType.CONTINUE)) {
                            MenuActionResult.Success(MenuUpdate.Replace(SequenceEditorMenu.editorRoute(context.route)))
                        } else {
                            MenuActionResult.Success(MenuUpdate.Replace(settingsRoute(context.route, node.id)))
                        }
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                TARGET_ID,
                renderer = { renderTarget(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val kind = context.payload["kind"]?.let { runCatching { TargetKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val fixedEntityId = if (kind == TargetKind.FIXED_ENTITY) {
                            context.player.getTargetEntity(32)?.uniqueId
                                ?: return@MenuActionHandler MenuActionResult.Ignored
                        } else null
                        updateNode(context.route) { node ->
                            val spec = TargetSpec(kind, fixedEntityId = fixedEntityId)
                            when (context.route.payload[ROLE]) {
                                "destination" -> {
                                    node.destinationTargetSpec = spec
                                    node.destinationSpec = null
                                }
                                "context_executor" -> node.contextOverride =
                                    (node.contextOverride ?: ExecutionContextSpec()).copy(executor = spec)
                                "context_target" -> node.contextOverride =
                                    (node.contextOverride ?: ExecutionContextSpec()).copy(target = spec)
                                "secondary_target" -> node.secondaryTargetSpec = spec
                                else -> node.targetSpec = spec
                            }
                        }
                        if (kind in setOf(
                                TargetKind.NEAREST_PLAYER, TargetKind.NEARBY_PLAYERS, TargetKind.ALL_PLAYERS,
                                TargetKind.RANDOM_PLAYER, TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES,
                            )
                        ) {
                            MenuActionResult.Success(MenuUpdate.Replace(choiceRoute(context.route, TARGET_FILTER_ID)))
                        } else {
                            MenuActionResult.Success(MenuUpdate.Back)
                        }
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                TARGET_FILTER_ID,
                renderer = { renderTargetFilters(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "sort" to MenuActionHandler { context ->
                        updateTargetSpec(context.route) { spec ->
                            spec.copy(sort = TargetSort.entries[(spec.sort.ordinal + 1) % TargetSort.entries.size])
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "gameMode" to MenuActionHandler { context ->
                        val modes = listOf(null, "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")
                        updateTargetSpec(context.route) { spec ->
                            val next = (modes.indexOf(spec.gameMode) + 1).coerceAtLeast(0) % modes.size
                            spec.copy(gameMode = modes[next])
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "excludeExecutor" to toggleTargetFlag(true),
                    "excludeActivator" to toggleTargetFlag(false),
                    "entityType" to targetFilterDialog("entityType", "gui.field.entity_type"),
                    "minimumDistance" to targetFilterDialog("minimumDistance", "gui.field.minimum_distance", decimal = true),
                    "maximumDistance" to targetFilterDialog("maximumDistance", "gui.field.maximum_distance", decimal = true),
                    "limit" to targetFilterDialog("limit", "gui.field.limit", integer = true),
                    "tag" to targetFilterDialog("tag", "gui.field.tag"),
                    "name" to targetFilterDialog("name", "gui.field.name"),
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                POSITION_ID,
                renderer = { renderPosition(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val kind = context.payload["kind"]?.let { runCatching { PositionKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (kind == PositionKind.COORDINATES) {
                            showPositionDialog(context.player, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (kind in setOf(PositionKind.TEMPORARY_VARIABLE, PositionKind.WORLD_VARIABLE)) {
                            showPositionVariableDialog(context.player, context.route, kind)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val location = context.player.location
                        val spec = if (kind == PositionKind.CAPTURED) {
                            PositionSpec(kind, location.x, location.y, location.z, location.yaw, location.pitch)
                        } else PositionSpec(kind)
                        updateNode(context.route) { node ->
                            when (context.route.payload[ROLE]) {
                                "destination" -> {
                                    node.destinationSpec = spec
                                    node.destinationTargetSpec = null
                                }
                                "condition_position" -> node.conditionPositionSpec = spec
                                else -> node.contextOverride =
                                    (node.contextOverride ?: ExecutionContextSpec()).copy(position = spec)
                            }
                        }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                    "target" to MenuActionHandler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(targetRoute(context.route, "destination")))
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                FACING_ID,
                renderer = { renderFacing(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val kind = context.payload["kind"]?.let { runCatching { FacingKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (kind == FacingKind.COORDINATES) {
                            showFacingCoordinatesDialog(context.player, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (kind == FacingKind.ROTATION) {
                            showRotationDialog(context.player, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val location = context.player.location
                        val spec = if (kind == FacingKind.CAPTURED) {
                            FacingSpec(kind, yaw = location.yaw, pitch = location.pitch)
                        } else FacingSpec(kind)
                        updateNode(context.route) { node ->
                            node.contextOverride = (node.contextOverride ?: ExecutionContextSpec()).copy(facing = spec)
                        }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                SETTINGS_ID,
                renderer = { renderSettings(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "field" to MenuActionHandler { context ->
                        val field = context.payload["field"] ?: return@MenuActionHandler MenuActionResult.Ignored
                        val node = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (field == "item" && node.type == CommandType.GIVE_ITEM) {
                            val scriptId = scriptId(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                            plugin.itemSelection.begin(context.player, scriptId, node.id, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field == "diskId" && node.type == CommandType.DISK_CALL) {
                            val scriptId = scriptId(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                            plugin.itemSelection.beginDisk(context.player, scriptId, node.id, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field == "kind" && node.type == CommandType.CONDITION) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, CONDITION_KIND_ID))
                            )
                        }
                        if (field == "condition" && node.type == CommandType.CONDITION) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, CONDITION_DETAIL_ID))
                            )
                        }
                        if (field == "inverted" && node.type == CommandType.CONDITION) {
                            updateNode(context.route) { it.params["inverted"] = (!it.boolean("inverted")).toString() }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "scope" && node.type == CommandType.VARIABLE) {
                            updateNode(context.route) {
                                it.params["scope"] = if (it.string("scope") == "WORLD") "TEMPORARY" else "WORLD"
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field.endsWith("Source") && node.type == CommandType.FOR_START) {
                            updateNode(context.route) {
                                it.params[field] = if (it.string(field) == "TEMPORARY") "FIXED" else "TEMPORARY"
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "inclusiveEnd" && node.type == CommandType.FOR_START) {
                            updateNode(context.route) {
                                it.params["inclusiveEnd"] = (!it.boolean("inclusiveEnd", true)).toString()
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "type" && node.type == CommandType.VARIABLE) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, VARIABLE_TYPE_ID))
                            )
                        }
                        if (field == "operation" && node.type == CommandType.VARIABLE) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, VARIABLE_OPERATION_ID))
                            )
                        }
                        if (field == "name" && node.type == CommandType.VARIABLE) {
                            showVariableNameDialog(context.player, context.route, node.string("name"))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field == "value" && node.type == CommandType.VARIABLE) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, VARIABLE_VALUE_ID))
                            )
                        }
                        if (field == "mode" && node.type == CommandType.DISPLAY_TEXT) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, DISPLAY_MODE_ID))
                            )
                        }
                        if (field == "action" && node.type == CommandType.ENTITY_ACTION) {
                            updateNode(context.route) {
                                it.params["action"] = if (it.string("action", "ride") == "ride") "dismount" else "ride"
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field in setOf("count", "ticks", "text", "stay", "value", "startValue", "endValue", "stepValue")) {
                            showFieldDialog(context.player, context.route, field, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val target = when {
                            field == "destination" -> positionRoute(context.route, "destination")
                            field == "executor" -> targetRoute(context.route, "context_executor")
                            field == "target" && node.type == CommandType.CONTEXT -> targetRoute(context.route, "context_target")
                            field == "target" || field == "subject" -> targetRoute(context.route, "node_target")
                            field == "other" && node.type == CommandType.ENTITY_ACTION ->
                                targetRoute(context.route, "secondary_target")
                            field == "position" -> positionRoute(context.route, "context_position")
                            field == "facing" -> facingRoute(context.route)
                            field == "context" -> choiceRoute(context.route, CONTEXT_OVERRIDE_ID)
                            else -> null
                        } ?: return@MenuActionHandler MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Navigate(target))
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                CONDITION_DETAIL_ID,
                renderer = { renderConditionDetail(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "target" to MenuActionHandler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(targetRoute(context.route, "node_target")))
                    },
                    "state" to MenuActionHandler { context ->
                        updateNode(context.route) {
                            it.params["state"] = if (it.string("state", "sneaking") == "sneaking") "on_ground" else "sneaking"
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "variable" to MenuActionHandler { context ->
                        showStringParameterDialog(
                            context.player,
                            context.route,
                            "variable",
                            "gui.dialog.condition_variable_title",
                            "gui.dialog.condition_variable_body",
                        )
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "scope" to MenuActionHandler { context ->
                        updateNode(context.route) {
                            it.params["variableScope"] =
                                if (it.string("variableScope") == VariableScope.WORLD.name) {
                                    VariableScope.TEMPORARY.name
                                } else VariableScope.WORLD.name
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "operator" to MenuActionHandler { context ->
                        val operators = listOf("set", "unset", "==", "!=", ">", ">=", "<", "<=")
                        updateNode(context.route) {
                            val current = operators.indexOf(it.string("operator", "==")).coerceAtLeast(0)
                            it.params["operator"] = operators[(current + 1) % operators.size]
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "value" to MenuActionHandler { context ->
                        showStringParameterDialog(
                            context.player,
                            context.route,
                            "value",
                            "gui.dialog.condition_value_title",
                            "gui.dialog.condition_value_body",
                            signedInteger = true,
                        )
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "position" to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(positionRoute(context.route, "condition_position"))
                        )
                    },
                    "block" to materialSelection("block"),
                    "item" to materialSelection("item"),
                    "count" to MenuActionHandler { context ->
                        val node = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        showFieldDialog(context.player, context.route, "count", node)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                VARIABLE_VALUE_ID,
                renderer = { renderVariableValue(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "direct" to MenuActionHandler { context ->
                        val node = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        showFieldDialog(context.player, context.route, "value", node)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "iteration" to setVariableValue(CURRENT_ITERATION),
                    "count" to setVariableValue(CURRENT_LOOP_COUNT),
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                CONDITION_KIND_ID,
                renderer = { renderConditionKinds(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val kind = context.payload["kind"]
                            ?.let { runCatching { ConditionKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        updateNode(context.route) { it.params["kind"] = kind.name }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                CONTEXT_OVERRIDE_ID,
                renderer = { renderContextOverride(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "executor" to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(targetRoute(context.route, "context_executor"))
                        )
                    },
                    "target" to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(targetRoute(context.route, "context_target"))
                        )
                    },
                    "position" to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(positionRoute(context.route, "context_position"))
                        )
                    },
                    "facing" to MenuActionHandler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(facingRoute(context.route)))
                    },
                    "inherit" to MenuActionHandler { context ->
                        updateNode(context.route) { it.contextOverride = null }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                DISPLAY_MODE_ID,
                renderer = { renderDisplayModes(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val mode = context.payload["mode"]
                            ?.takeIf { it in setOf("tellraw", "title", "actionbar") }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        updateNode(context.route) { it.params["mode"] = mode }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                VARIABLE_TYPE_ID,
                renderer = { renderVariableTypes(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val type = context.payload["type"]
                            ?.let { runCatching { VariableType.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        updateNode(context.route) {
                            it.params["type"] = type.name
                            val current = runCatching {
                                VariableOperation.valueOf(it.string("operation"))
                            }.getOrNull()
                            if (current !in allowedVariableOperations(type)) {
                                it.params["operation"] = allowedVariableOperations(type).first().name
                            }
                        }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                VARIABLE_OPERATION_ID,
                renderer = { renderVariableOperations(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val operation = context.payload["operation"]
                            ?.let { runCatching { VariableOperation.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val type = node(context.route)?.string("type")
                            ?.let { runCatching { VariableType.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (operation !in allowedVariableOperations(type)) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        updateNode(context.route) { it.params["operation"] = operation.name }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                TIMER_ID,
                renderer = { renderTimer(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "off" to MenuActionHandler { context ->
                        val script = script(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        script.timer.enabled = false
                        script.activation = ActivationMode.NEEDS_REDSTONE
                        plugin.scripts.save(script)
                        plugin.resetActivationTiming(script.id)
                        plugin.placements.refreshDisplaysForScript(script.id)
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                    "on" to MenuActionHandler { context ->
                        val script = script(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        showTimerDialog(context.player, context.route, script.id, script.timer.intervalUnits)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                DELETE_ID,
                renderer = { renderDelete(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "delete" to MenuActionHandler { context ->
                        val script = script(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        val nodeId = context.route.payload[NODE_ID]
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!GraphEditor.delete(script.graph, nodeId)) return@MenuActionHandler MenuActionResult.Ignored
                        plugin.scripts.save(script)
                        MenuActionResult.Success(MenuUpdate.Replace(SequenceEditorMenu.editorRoute(context.route)))
                    },
                ),
            )
        )
    }

    private fun renderPicker(player: Player, route: MenuRoute): InventoryMenuView {
        val script = script(route)
        val sourceId = route.payload[SOURCE_ID]?.takeIf(String::isNotBlank)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val edge = route.payload[EDGE]?.let { runCatching { GraphEditor.Edge.valueOf(it) }.getOrNull() }
        val mergeConditionId = route.payload[MERGE_CONDITION_ID]?.takeIf(String::isNotBlank)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val insideFor = script?.graph?.let {
            GraphEditor.isInsideFor(it, sourceId, edge ?: GraphEditor.Edge.ENTRY)
        } == true
        val types = CommandType.entries.filter { type ->
            when (type) {
                CommandType.FOR_END -> false
                CommandType.MERGE -> GraphEditor.canAppendMerge(script?.graph, mergeConditionId)
                CommandType.BREAK, CommandType.CONTINUE -> insideFor
                else -> true
            }
        }
        val elements = types.mapIndexed { index, type ->
            MenuElement(
                pickerSlots(types.size)[index],
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
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.select_command")), elements)
    }

    private fun pickerSlots(count: Int): List<Int> {
        require(count in 1..27)
        return (0 until count).chunked(9).flatMapIndexed { row, chunk ->
            val first = 9 + row * 9 + (9 - chunk.size) / 2
            (first until first + chunk.size).toList()
        }
    }

    private fun renderSettings(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
            ?: return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.command_settings")), listOf(backElement(player)))
        val fields = settingsFields(node)
        val elements = fields.mapIndexed { index, field ->
            MenuElement(
                EditorMenuLayout.centeredSlots(fields.size)[index],
                KcGui.item(
                    field.material,
                    KcI18n.text(player, field.label),
                    GuiNameStyle.PRIMARY,
                    listOf(
                        GuiLoreLine.Data(
                            KcI18n.text(player, "gui.editor.current"),
                            localizedValue(player, field.value(node)),
                            "§f",
                        ),
                        KcGui.action(player, "lore.click.left", KcI18n.text(player, field.label)),
                    ),
                    GuiElementRole.ACTION,
                ),
                GuiElementRole.ACTION,
                "field",
                mapOf("field" to field.key),
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(
            45,
            KcGui.title(KcI18n.text(player, "gui.editor.command_settings_named", mapOf("command" to KcI18n.text(player, node.type.key)))),
            elements,
        )
    }

    private fun renderTarget(player: Player, route: MenuRoute): InventoryMenuView {
        val options = listOf(
            Triple(TargetKind.EXECUTOR, Material.PLAYER_HEAD, KcI18n.text(player, "gui.option.executor")),
            Triple(TargetKind.ACTIVATOR, Material.LEVER, KcI18n.text(player, "gui.option.activator")),
            Triple(TargetKind.INHERITED_TARGET, Material.TARGET, KcI18n.text(player, "gui.option.inherited_target")),
            Triple(TargetKind.NEAREST_PLAYER, Material.COMPASS, KcI18n.text(player, "gui.option.nearest_player")),
            Triple(TargetKind.NEARBY_PLAYERS, Material.FILLED_MAP, KcI18n.text(player, "gui.option.nearby_players")),
            Triple(TargetKind.ALL_PLAYERS, Material.MAP, KcI18n.text(player, "gui.option.all_players")),
            Triple(TargetKind.RANDOM_PLAYER, Material.ENDER_EYE, KcI18n.text(player, "gui.option.random_player")),
            Triple(TargetKind.NEAREST_ENTITY, Material.ARMOR_STAND, KcI18n.text(player, "gui.option.nearest_entity")),
            Triple(TargetKind.NEARBY_ENTITIES, Material.LEAD, KcI18n.text(player, "gui.option.nearby_entities")),
            Triple(TargetKind.FIXED_ENTITY, Material.ARMOR_STAND, KcI18n.text(player, "gui.option.fixed_entity")),
        )
        val elements = options.mapIndexed { index, option ->
            MenuElement(
                pickerSlots(options.size)[index],
                KcGui.item(option.second, option.third, GuiNameStyle.PRIMARY),
                GuiElementRole.ACTION,
                "select",
                mapOf("kind" to option.first.name),
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.target_title")), elements)
    }

    private fun renderTargetFilters(player: Player, route: MenuRoute): InventoryMenuView {
        val spec = selectedTargetSpec(route) ?: TargetSpec(TargetKind.NEAREST_ENTITY)
        val options = listOf(
            DetailOption(Material.ARMOR_STAND, "gui.field.entity_type", "entityType", spec.entityType ?: "-"),
            DetailOption(Material.LIME_DYE, "gui.field.minimum_distance", "minimumDistance", spec.minimumDistance?.toString() ?: "-"),
            DetailOption(Material.RED_DYE, "gui.field.maximum_distance", "maximumDistance", spec.maximumDistance?.toString() ?: "-"),
            DetailOption(Material.REPEATER, "gui.field.limit", "limit", spec.limit?.toString() ?: "-"),
            DetailOption(Material.COMPARATOR, "gui.field.sort", "sort", spec.sort.name),
            DetailOption(Material.PLAYER_HEAD, "gui.field.game_mode", "gameMode", spec.gameMode ?: "-"),
            DetailOption(Material.NAME_TAG, "gui.field.tag", "tag", spec.tag ?: "-"),
            DetailOption(Material.OAK_SIGN, "gui.field.name", "name", spec.name ?: "-"),
            DetailOption(Material.BARRIER, "gui.field.exclude_executor", "excludeExecutor", spec.excludeExecutor.toString()),
            DetailOption(Material.LEVER, "gui.field.exclude_activator", "excludeActivator", spec.excludeActivator.toString()),
        )
        val elements = options.mapIndexed { index, option ->
            MenuElement(
                pickerSlots(options.size)[index],
                KcGui.item(
                    option.material,
                    KcI18n.text(player, option.nameKey),
                    GuiNameStyle.PRIMARY,
                    listOf(GuiLoreLine.Data(KcI18n.text(player, "gui.editor.current"), localizedValue(player, option.value), "§f")),
                ),
                GuiElementRole.ACTION,
                option.action,
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.target_filter_title")), elements)
    }

    private fun renderPosition(player: Player, route: MenuRoute): InventoryMenuView {
        val destination = route.payload[ROLE] == "destination"
        val elements = if (destination) {
            mutableListOf(
                MenuElement(20, KcGui.item(Material.COMPASS, KcI18n.text(player, "gui.option.coordinates_set"), GuiNameStyle.PRIMARY), GuiElementRole.ACTION, "select", mapOf("kind" to PositionKind.COORDINATES.name)),
                MenuElement(22, KcGui.item(Material.ENDER_PEARL, KcI18n.text(player, "gui.option.other_entity"), GuiNameStyle.PRIMARY), GuiElementRole.ACTION, "target"),
                MenuElement(24, KcGui.item(Material.RECOVERY_COMPASS, KcI18n.text(player, "gui.option.current_position_set"), GuiNameStyle.PRIMARY), GuiElementRole.ACTION, "select", mapOf("kind" to PositionKind.CAPTURED.name)),
            )
        } else {
            val options = listOf(
                Triple(PositionKind.CAPTURED, Material.RECOVERY_COMPASS, KcI18n.text(player, "gui.option.current_position")),
                Triple(PositionKind.DISK, Material.COMMAND_BLOCK, KcI18n.text(player, "gui.option.disk_position")),
                Triple(PositionKind.EXECUTOR, Material.PLAYER_HEAD, KcI18n.text(player, "gui.option.executor_position")),
                Triple(PositionKind.TARGET, Material.TARGET, KcI18n.text(player, "gui.option.target_position")),
                Triple(PositionKind.MYWORLD_SPAWN, Material.RESPAWN_ANCHOR, KcI18n.text(player, "gui.option.myworld_spawn")),
                Triple(PositionKind.COORDINATES, Material.COMPASS, KcI18n.text(player, "gui.option.coordinates")),
                Triple(PositionKind.TEMPORARY_VARIABLE, Material.REDSTONE, KcI18n.text(player, "gui.option.temporary_variable")),
                Triple(PositionKind.WORLD_VARIABLE, Material.ENDER_CHEST, KcI18n.text(player, "gui.field.world_variable")),
            )
            options.mapIndexed { index, option ->
                MenuElement(
                    EditorMenuLayout.centeredSlots(options.size)[index],
                    KcGui.item(option.second, option.third, GuiNameStyle.PRIMARY),
                    GuiElementRole.ACTION,
                    "select",
                    mapOf("kind" to option.first.name),
                )
            }.toMutableList()
        }
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, if (destination) "gui.editor.position_destination_title" else "gui.editor.position_context_title")), elements)
    }

    private fun renderFacing(player: Player, route: MenuRoute): InventoryMenuView {
        val options = listOf(
            Triple(FacingKind.INHERITED, Material.GRAY_DYE, KcI18n.text(player, "gui.option.unchanged")),
            Triple(FacingKind.CAPTURED, Material.SPYGLASS, KcI18n.text(player, "gui.option.current_facing")),
            Triple(FacingKind.EXECUTOR, Material.PLAYER_HEAD, KcI18n.text(player, "gui.option.executor_facing")),
            Triple(FacingKind.TARGET, Material.TARGET, KcI18n.text(player, "gui.option.face_target")),
            Triple(FacingKind.COORDINATES, Material.COMPASS, KcI18n.text(player, "gui.option.face_coordinates")),
            Triple(FacingKind.MYWORLD_SPAWN, Material.RESPAWN_ANCHOR, KcI18n.text(player, "gui.option.myworld_spawn")),
            Triple(FacingKind.ROTATION, Material.REPEATER, KcI18n.text(player, "gui.option.numeric")),
        )
        val elements = options.mapIndexed { index, option ->
            MenuElement(
                EditorMenuLayout.centeredSlots(options.size)[index],
                KcGui.item(option.second, option.third, GuiNameStyle.PRIMARY),
                GuiElementRole.ACTION,
                "select",
                mapOf("kind" to option.first.name),
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.facing_title")), elements)
    }

    private fun renderTimer(player: Player, route: MenuRoute): InventoryMenuView {
        val script = script(route)
        val elements = mutableListOf(
            MenuElement(21, KcGui.item(Material.REDSTONE_TORCH, KcI18n.text(player, "gui.editor.disabled"), GuiNameStyle.PRIMARY), GuiElementRole.ACTION, "off"),
            MenuElement(
                23,
                KcGui.item(
                    Material.CLOCK,
                    KcI18n.text(player, "gui.editor.enabled"),
                    GuiNameStyle.PRIMARY,
                    listOf(GuiLoreLine.Data(
                        KcI18n.text(player, "gui.editor.timer"),
                        KcI18n.text(player, "gui.editor.interval_units", mapOf("value" to (script?.timer?.intervalUnits ?: 1))),
                        "§f",
                    )),
                ),
                GuiElementRole.ACTION,
                "on",
            ),
            backElement(player),
        )
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.timer")), elements)
    }

    private fun renderConditionKinds(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(ConditionKind.TARGET_EXISTS, Material.ENDER_EYE, KcI18n.text(player, "condition.target_exists")),
            Triple(ConditionKind.ENTITY_STATE, Material.PLAYER_HEAD, KcI18n.text(player, "condition.entity_state")),
            Triple(ConditionKind.VARIABLE_STATE, Material.REDSTONE, KcI18n.text(player, "condition.variable_state")),
            Triple(ConditionKind.BLOCK_STATE, Material.GRASS_BLOCK, KcI18n.text(player, "condition.block_state")),
            Triple(ConditionKind.ITEM_POSSESSION, Material.CHEST, KcI18n.text(player, "condition.item_possession")),
        )
        val elements = options.mapIndexed { index, option ->
            MenuElement(
                EditorMenuLayout.centeredSlots(options.size)[index],
                KcGui.item(option.second, option.third, GuiNameStyle.PRIMARY),
                GuiElementRole.ACTION,
                "select",
                mapOf("kind" to option.first.name),
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.condition_title")), elements)
    }

    private fun renderConditionDetail(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
            ?: return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.condition_detail_title")), listOf(backElement(player)))
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }
            .getOrDefault(ConditionKind.TARGET_EXISTS)
        val options = when (kind) {
            ConditionKind.TARGET_EXISTS -> listOf(
                DetailOption(Material.TARGET, "gui.field.target", "target", node.targetSpec?.kind?.name ?: "EXECUTOR"),
            )
            ConditionKind.ENTITY_STATE -> listOf(
                DetailOption(Material.TARGET, "gui.field.target", "target", node.targetSpec?.kind?.name ?: "EXECUTOR"),
                DetailOption(Material.LEVER, "gui.field.entity_state", "state", node.string("state", "sneaking")),
            )
            ConditionKind.VARIABLE_STATE -> listOf(
                DetailOption(Material.NAME_TAG, "gui.field.variable", "variable", node.string("variable", "-")),
                DetailOption(Material.ENDER_CHEST, "gui.field.variable_scope", "scope", node.string("variableScope", VariableScope.TEMPORARY.name)),
                DetailOption(Material.COMPARATOR, "gui.field.operator", "operator", node.string("operator", "==")),
                DetailOption(Material.REPEATER, "gui.field.value", "value", node.string("value", "0")),
            )
            ConditionKind.BLOCK_STATE -> listOf(
                DetailOption(
                    Material.COMPASS,
                    "gui.field.position",
                    "position",
                    node.conditionPositionSpec?.kind?.name ?: "DISK",
                ),
                DetailOption(Material.GRASS_BLOCK, "gui.field.block", "block", node.string("block", "minecraft:air")),
            )
            ConditionKind.ITEM_POSSESSION -> listOf(
                DetailOption(Material.TARGET, "gui.field.target", "target", node.targetSpec?.kind?.name ?: "EXECUTOR"),
                DetailOption(
                    Material.CHEST,
                    "gui.field.item_condition",
                    "item",
                    node.string("item").ifBlank { KcI18n.text(player, "gui.field.unset") },
                ),
                DetailOption(Material.DIAMOND, "gui.field.count", "count", node.string("count", "1")),
            )
        }
        val slots = EditorMenuLayout.centeredSlots(options.size)
        val elements = options.mapIndexed { index, option ->
            MenuElement(
                slots[index],
                KcGui.item(
                    option.material,
                    KcI18n.text(player, option.nameKey),
                    GuiNameStyle.PRIMARY,
                    listOf(GuiLoreLine.Data(KcI18n.text(player, "gui.editor.current"), localizedValue(player, option.value), "§f")),
                ),
                GuiElementRole.ACTION,
                option.action,
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.condition_detail_title")), elements)
    }

    private fun renderVariableValue(player: Player, route: MenuRoute): InventoryMenuView {
        val script = script(route)
        val node = node(route)
        val insideFor = script != null && node != null &&
            node.string("type") == VariableType.INTEGER.name &&
            GraphEditor.isInsideFor(script.graph, node.id, GraphEditor.Edge.NEXT)
        val options = buildList {
            add(DetailOption(Material.WRITABLE_BOOK, "gui.option.direct_value", "direct", ""))
            if (insideFor) {
                add(DetailOption(Material.COMPARATOR, "gui.option.current_iteration", "iteration", ""))
                add(DetailOption(Material.REPEATER, "gui.option.current_loop_count", "count", ""))
            }
        }
        val elements = options.mapIndexed { index, option ->
            MenuElement(
                EditorMenuLayout.centeredSlots(options.size)[index],
                KcGui.item(option.material, KcI18n.text(player, option.nameKey), GuiNameStyle.PRIMARY),
                GuiElementRole.ACTION,
                option.action,
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.variable_value_title")), elements)
    }

    private fun setVariableValue(value: String) = MenuActionHandler { context ->
        updateNode(context.route) { it.params["value"] = value }
        MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun materialSelection(parameter: String) = MenuActionHandler { context ->
        val scriptId = scriptId(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
        val nodeId = node(context.route)?.id ?: return@MenuActionHandler MenuActionResult.Ignored
        plugin.itemSelection.beginMaterial(context.player, scriptId, nodeId, context.route, parameter)
        MenuActionResult.Success(MenuUpdate.None)
    }

    private fun renderVariableTypes(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(VariableType.BOOLEAN, Material.LEVER, KcI18n.text(player, "gui.option.true_false")),
            Triple(VariableType.INTEGER, Material.REPEATER, KcI18n.text(player, "gui.option.integer")),
            Triple(VariableType.DECIMAL, Material.COMPARATOR, KcI18n.text(player, "gui.option.decimal")),
            Triple(VariableType.TEXT, Material.WRITABLE_BOOK, KcI18n.text(player, "gui.option.text")),
            Triple(VariableType.POSITION, Material.COMPASS, KcI18n.text(player, "gui.option.position")),
            Triple(VariableType.ENTITY, Material.PLAYER_HEAD, KcI18n.text(player, "gui.option.entity_reference")),
        )
        val elements = options.mapIndexed { index, option ->
            MenuElement(
                EditorMenuLayout.centeredSlots(options.size)[index],
                KcGui.item(option.second, option.third, GuiNameStyle.PRIMARY),
                GuiElementRole.ACTION,
                "select",
                mapOf("type" to option.first.name),
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.variable_type_title")), elements)
    }

    private fun renderVariableOperations(player: Player, route: MenuRoute): InventoryMenuView {
        val type = node(route)?.string("type")
            ?.let { runCatching { VariableType.valueOf(it) }.getOrNull() }
            ?: VariableType.BOOLEAN
        val options = listOf(
            Triple(VariableOperation.SET, Material.LIME_DYE, KcI18n.text(player, "gui.option.set")),
            Triple(VariableOperation.ADD, Material.SLIME_BALL, KcI18n.text(player, "gui.option.add")),
            Triple(VariableOperation.SUBTRACT, Material.FERMENTED_SPIDER_EYE, KcI18n.text(player, "gui.option.subtract")),
            Triple(VariableOperation.TOGGLE, Material.LEVER, KcI18n.text(player, "gui.option.toggle")),
            Triple(VariableOperation.STORE_POSITION, Material.COMPASS, KcI18n.text(player, "gui.option.store_position")),
            Triple(VariableOperation.STORE_TARGET, Material.PLAYER_HEAD, KcI18n.text(player, "gui.option.store_target")),
            Triple(VariableOperation.CLEAR, Material.BARRIER, KcI18n.text(player, "gui.option.clear")),
        ).filter { it.first in allowedVariableOperations(type) }
        val elements = options.mapIndexed { index, option ->
            MenuElement(
                EditorMenuLayout.centeredSlots(options.size)[index],
                KcGui.item(option.second, option.third, GuiNameStyle.PRIMARY),
                GuiElementRole.ACTION,
                "select",
                mapOf("operation" to option.first.name),
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.variable_operation_title")), elements)
    }

    private fun settingsFields(node: CommandNode): List<EditorField> {
        val fields = EditorMenuLayout.fields(node.type)
        if (node.type == CommandType.ENTITY_ACTION && node.string("action") != "ride") {
            return fields.filterNot { it.key == "other" }
        }
        if (node.type == CommandType.DISPLAY_TEXT && node.string("mode") != "title") {
            return fields.filterNot { it.key == "stay" }
        }
        if (node.type != CommandType.VARIABLE) return fields
        val operation = runCatching {
            VariableOperation.valueOf(node.string("operation"))
        }.getOrDefault(VariableOperation.SET)
        return fields.filterNot { field ->
            field.key == "value" &&
                operation !in setOf(VariableOperation.SET, VariableOperation.ADD, VariableOperation.SUBTRACT)
        }
    }

    private fun allowedVariableOperations(type: VariableType): List<VariableOperation> = when (type) {
        VariableType.BOOLEAN -> listOf(VariableOperation.SET, VariableOperation.TOGGLE, VariableOperation.CLEAR)
        VariableType.INTEGER, VariableType.DECIMAL ->
            listOf(VariableOperation.SET, VariableOperation.ADD, VariableOperation.SUBTRACT, VariableOperation.CLEAR)
        VariableType.TEXT -> listOf(VariableOperation.SET, VariableOperation.CLEAR)
        VariableType.POSITION -> listOf(VariableOperation.STORE_POSITION, VariableOperation.CLEAR)
        VariableType.ENTITY -> listOf(VariableOperation.STORE_TARGET, VariableOperation.CLEAR)
    }

    private fun renderDisplayModes(player: Player): InventoryMenuView {
        val options = listOf(
            Triple("tellraw", Material.WRITABLE_BOOK, KcI18n.text(player, "gui.option.chat")),
            Triple("title", Material.OAK_SIGN, KcI18n.text(player, "gui.option.title")),
            Triple("actionbar", Material.NAME_TAG, KcI18n.text(player, "gui.option.actionbar")),
        )
        val elements = options.mapIndexed { index, option ->
            MenuElement(
                EditorMenuLayout.centeredSlots(options.size)[index],
                KcGui.item(option.second, option.third, GuiNameStyle.PRIMARY),
                GuiElementRole.ACTION,
                "select",
                mapOf("mode" to option.first),
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.display_mode_title")), elements)
    }

    private fun renderContextOverride(player: Player, route: MenuRoute): InventoryMenuView {
        val context = node(route)?.contextOverride
        val options = listOf(
            ContextOption(20, Material.PLAYER_HEAD, KcI18n.text(player, "gui.option.executor"), "executor", state(player, context?.executor != null)),
            ContextOption(21, Material.TARGET, KcI18n.text(player, "gui.field.target"), "target", state(player, context?.target != null)),
            ContextOption(22, Material.COMPASS, KcI18n.text(player, "gui.field.position"), "position", state(player, context?.position != null)),
            ContextOption(23, Material.SPYGLASS, KcI18n.text(player, "gui.field.facing"), "facing", state(player, context?.facing != null)),
            ContextOption(24, Material.GRAY_DYE, KcI18n.text(player, "gui.option.inherit_all"), "inherit", KcI18n.text(player, "gui.option.clear_context")),
        )
        val elements = options.map { option ->
            MenuElement(
                option.slot,
                KcGui.item(
                    option.material,
                    option.name,
                    GuiNameStyle.PRIMARY,
                    listOf(GuiLoreLine.Data(KcI18n.text(player, "gui.editor.current"), option.value, "§f")),
                ),
                GuiElementRole.ACTION,
                option.action,
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.context_title")), elements)
    }

    private fun renderDelete(player: Player, route: MenuRoute): InventoryMenuView {
        val elements = listOf(
            MenuElement(20, KcGui.item(Material.BARRIER, KcI18n.text(player, "gui.editor.cancel_delete"), GuiNameStyle.PRIMARY), GuiElementRole.CANCEL, "back"),
            MenuElement(24, KcGui.item(Material.RED_CONCRETE, KcI18n.text(player, "gui.editor.delete_command"), GuiNameStyle.DANGER), GuiElementRole.ACTION, "delete"),
        )
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, "gui.editor.delete_title")), elements)
    }

    private fun showTimerDialog(player: Player, route: MenuRoute, scriptId: UUID, units: Int) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "timer-edit",
                title = KcI18n.component(player, "gui.dialog.timer_title"),
                body = listOf(KcI18n.component(player, "gui.dialog.timer_body")),
                inputs = listOf(
                    MenuDialogInput.Text(
                        "units",
                        KcI18n.component(player, "gui.dialog.interval"),
                        units.toString(),
                        maxLength = 5,
                    )
                ),
                confirm = MenuDialogButton(KcI18n.component(player, "gui.dialog.enable"), MenuDialogHandler { _, response ->
                    val value = response.textValue("units").toIntOrNull()
                    if (value == null || value !in 1..MAX_TIMER_UNITS) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, "gui.dialog.timer_invalid")
                        )
                    }
                    val script = plugin.scripts.load(scriptId)
                        ?: return@MenuDialogHandler MenuActionResult.Ignored
                    script.timer.enabled = true
                    script.timer.intervalUnits = value
                    plugin.scripts.save(script)
                    plugin.resetActivationTiming(script.id)
                    plugin.placements.refreshDisplaysForScript(script.id)
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(KcI18n.component(player, "gui.dialog.back"), MenuDialogHandler { _, _ ->
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
            )
        )
    }

    private fun showVariableNameDialog(player: Player, route: MenuRoute, currentName: String) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "variable-name",
                title = KcI18n.component(player, "gui.dialog.variable_title"),
                body = listOf(KcI18n.component(player, "gui.dialog.variable_body")),
                inputs = listOf(
                    MenuDialogInput.Text(
                        "name",
                        KcI18n.component(player, "gui.dialog.variable_name"),
                        currentName,
                        maxLength = 64,
                    )
                ),
                confirm = MenuDialogButton(KcI18n.component(player, "gui.dialog.confirm"), MenuDialogHandler { _, response ->
                    val name = response.textValue("name").trim().lowercase()
                    if (!name.matches(Regex("[a-z0-9_.-]{1,64}"))) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, "gui.dialog.variable_invalid")
                        )
                    }
                    updateNode(route) { it.params["name"] = name }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(KcI18n.component(player, "gui.dialog.back"), MenuDialogHandler { _, _ ->
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
            )
        )
    }

    private fun showFieldDialog(player: Player, route: MenuRoute, field: String, node: CommandNode) {
        if (field == "stay" && node.type == CommandType.DISPLAY_TEXT) {
            showDisplayTimingDialog(player, route, node)
            return
        }
        val definition = when (field) {
            "count" -> FieldDialogDefinition("field_count", "1", true)
            "ticks" -> FieldDialogDefinition("field_ticks", "20", true)
            "text" -> FieldDialogDefinition("field_text", "", false)
            "value" -> FieldDialogDefinition("field_value", "", false)
            "startValue" -> FieldDialogDefinition("field_start", "0", false)
            "endValue" -> FieldDialogDefinition("field_end", "0", false)
            "stepValue" -> FieldDialogDefinition("field_step", "1", false)
            else -> return
        }
        val title = KcI18n.text(player, "gui.dialog.${definition.key}")
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "field-$field",
                title = KcI18n.component(player, "gui.dialog.${definition.key}"),
                body = listOf(KcI18n.component(player, "gui.dialog.${definition.key}_body")),
                inputs = listOf(
                    MenuDialogInput.Text(
                        field,
                        KcI18n.component(player, "gui.dialog.${definition.key}"),
                        node.string(field, definition.defaultValue),
                        maxLength = if (field == "text" || field == "value") 512 else 10,
                    )
                ),
                confirm = MenuDialogButton(KcI18n.component(player, "gui.dialog.confirm"), MenuDialogHandler { _, response ->
                    val value = response.textValue(field)
                    if (definition.positiveInteger && (value.toIntOrNull() ?: 0) < 1) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, "gui.dialog.positive_invalid", mapOf("field" to title))
                        )
                    }
                    if (field in setOf("startValue", "endValue", "stepValue")) {
                        val source = node.string(field.removeSuffix("Value") + "Source", "FIXED")
                        if (source == "FIXED" && value.toLongOrNull() == null) {
                            return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, "gui.dialog.integer_invalid"))
                        }
                        if (field == "stepValue" && source == "FIXED" && value.toLongOrNull() == 0L) {
                            return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, "gui.dialog.step_zero"))
                        }
                    }
                    updateNode(route) { it.params[field] = value }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = dialogCancel(player, route),
            )
        )
    }

    private fun showDisplayTimingDialog(player: Player, route: MenuRoute, node: CommandNode) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "display-timing",
                title = KcI18n.component(player, "gui.dialog.duration_title"),
                body = listOf(KcI18n.component(player, "gui.dialog.duration_body")),
                inputs = listOf(
                    MenuDialogInput.Text("fadeIn", KcI18n.component(player, "gui.dialog.fade_in"), node.string("fadeIn", "10")),
                    MenuDialogInput.Text("stay", KcI18n.component(player, "gui.dialog.stay"), node.string("stay", "60")),
                    MenuDialogInput.Text("fadeOut", KcI18n.component(player, "gui.dialog.fade_out"), node.string("fadeOut", "10")),
                ),
                confirm = MenuDialogButton(KcI18n.component(player, "gui.dialog.confirm"), MenuDialogHandler { _, response ->
                    val values = listOf("fadeIn", "stay", "fadeOut").associateWith { response.textValue(it).toIntOrNull() }
                    if (values.values.any { it == null || it < 0 }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, "gui.dialog.duration_invalid")
                        )
                    }
                    updateNode(route) { command ->
                        values.forEach { (key, value) -> command.params[key] = value.toString() }
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = dialogCancel(player, route),
            )
        )
    }

    private fun showStringParameterDialog(
        player: Player,
        route: MenuRoute,
        parameter: String,
        titleKey: String,
        bodyKey: String,
        signedInteger: Boolean = false,
    ) {
        val current = node(route)?.string(parameter).orEmpty()
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "parameter-$parameter",
                title = KcI18n.component(player, titleKey),
                body = listOf(KcI18n.component(player, bodyKey)),
                inputs = listOf(MenuDialogInput.Text(parameter, KcI18n.component(player, titleKey), current, maxLength = 64)),
                confirm = MenuDialogButton(KcI18n.component(player, "gui.dialog.confirm"), MenuDialogHandler { _, response ->
                    val value = response.textValue(parameter)
                    if (signedInteger && value.toLongOrNull() == null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, "gui.dialog.integer_invalid"))
                    }
                    updateNode(route) { it.params[parameter] = value }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = dialogCancel(player, route),
            )
        )
    }

    private fun showPositionDialog(player: Player, route: MenuRoute) {
        val current = node(route)?.let { selectedPosition(it, route.payload[ROLE]) }
        val location = player.location
        showCoordinateDialog(
            player = player,
            route = route,
            id = "position-coordinates",
            title = KcI18n.text(player, "gui.dialog.destination_coordinates_title"),
            currentX = current?.x ?: location.x,
            currentY = current?.y ?: location.y,
            currentZ = current?.z ?: location.z,
        ) { x, y, z ->
            updateNode(route) { command ->
                val spec = PositionSpec(PositionKind.COORDINATES, x, y, z)
                when (route.payload[ROLE]) {
                    "destination" -> {
                        command.destinationSpec = spec
                        command.destinationTargetSpec = null
                    }
                    "condition_position" -> command.conditionPositionSpec = spec
                    else -> command.contextOverride =
                        (command.contextOverride ?: ExecutionContextSpec()).copy(position = spec)
                }
            }
        }
    }

    private fun showPositionVariableDialog(player: Player, route: MenuRoute, kind: PositionKind) {
        val current = node(route)?.let { selectedPosition(it, route.payload[ROLE]) }?.variable.orEmpty()
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "position-variable",
                title = KcI18n.component(player, "gui.dialog.position_variable_title"),
                body = listOf(KcI18n.component(player, "gui.dialog.position_variable_body")),
                inputs = listOf(
                    MenuDialogInput.Text("name", KcI18n.component(player, "gui.dialog.variable_name"), current, maxLength = 64)
                ),
                confirm = MenuDialogButton(KcI18n.component(player, "gui.dialog.confirm"), MenuDialogHandler { _, response ->
                    val name = response.textValue("name").trim().lowercase()
                    if (!name.matches(Regex("[a-z0-9_.-]{1,64}"))) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, "gui.dialog.variable_invalid")
                        )
                    }
                    updateNode(route) { command ->
                        val spec = PositionSpec(kind, variable = name)
                        when (route.payload[ROLE]) {
                            "destination" -> {
                                command.destinationSpec = spec
                                command.destinationTargetSpec = null
                            }
                            "condition_position" -> command.conditionPositionSpec = spec
                            else -> command.contextOverride =
                                (command.contextOverride ?: ExecutionContextSpec()).copy(position = spec)
                        }
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = dialogCancel(player, route),
            )
        )
    }

    private fun showFacingCoordinatesDialog(player: Player, route: MenuRoute) {
        val current = node(route)?.contextOverride?.facing
        val location = player.location
        showCoordinateDialog(
            player = player,
            route = route,
            id = "facing-coordinates",
            title = KcI18n.text(player, "gui.dialog.facing_coordinates_title"),
            currentX = current?.x ?: location.x,
            currentY = current?.y ?: location.y,
            currentZ = current?.z ?: location.z,
        ) { x, y, z ->
            updateNode(route) { command ->
                command.contextOverride = (command.contextOverride ?: ExecutionContextSpec()).copy(
                    facing = FacingSpec(FacingKind.COORDINATES, x = x, y = y, z = z)
                )
            }
        }
    }

    private fun showRotationDialog(player: Player, route: MenuRoute) {
        val current = node(route)?.contextOverride?.facing
        val location = player.location
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "facing-rotation",
                title = KcI18n.component(player, "gui.dialog.rotation_title"),
                body = listOf(KcI18n.component(player, "gui.dialog.rotation_body")),
                inputs = listOf(
                    MenuDialogInput.Text("yaw", Component.text("yaw"), (current?.yaw ?: location.yaw).toString()),
                    MenuDialogInput.Text("pitch", Component.text("pitch"), (current?.pitch ?: location.pitch).toString()),
                ),
                confirm = MenuDialogButton(KcI18n.component(player, "gui.dialog.confirm"), MenuDialogHandler { _, response ->
                    val yaw = response.textValue("yaw").toFloatOrNull()
                    val pitch = response.textValue("pitch").toFloatOrNull()
                    if (yaw == null || pitch == null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, "gui.dialog.rotation_invalid"))
                    }
                    updateNode(route) { command ->
                        command.contextOverride = (command.contextOverride ?: ExecutionContextSpec()).copy(
                            facing = FacingSpec(FacingKind.ROTATION, yaw = yaw, pitch = pitch)
                        )
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = dialogCancel(player, route),
            )
        )
    }

    private fun showCoordinateDialog(
        player: Player,
        route: MenuRoute,
        id: String,
        title: String,
        currentX: Double,
        currentY: Double,
        currentZ: Double,
        save: (Double, Double, Double) -> Unit,
    ) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = id,
                title = Component.text(title),
                body = listOf(KcI18n.component(player, "gui.dialog.coordinates_body")),
                inputs = listOf(
                    MenuDialogInput.Text("x", Component.text("X"), currentX.toString()),
                    MenuDialogInput.Text("y", Component.text("Y"), currentY.toString()),
                    MenuDialogInput.Text("z", Component.text("Z"), currentZ.toString()),
                ),
                confirm = MenuDialogButton(KcI18n.component(player, "gui.dialog.confirm"), MenuDialogHandler { _, response ->
                    val x = response.textValue("x").toDoubleOrNull()
                    val y = response.textValue("y").toDoubleOrNull()
                    val z = response.textValue("z").toDoubleOrNull()
                    if (x == null || y == null || z == null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, "gui.dialog.coordinates_invalid"))
                    }
                    save(x, y, z)
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = dialogCancel(player, route),
            )
        )
    }

    private fun dialogCancel(player: Player, route: MenuRoute) =
        MenuDialogButton(KcI18n.component(player, "gui.dialog.back"), MenuDialogHandler { _, _ ->
            MenuActionResult.Success(MenuUpdate.Replace(route))
        })

    private fun selectedPosition(node: CommandNode, role: String?): PositionSpec? =
        when (role) {
            "destination" -> node.destinationSpec
            "condition_position" -> node.conditionPositionSpec
            else -> node.contextOverride?.position
        }

    private fun back() = MenuActionHandler { MenuActionResult.Success(MenuUpdate.Back) }

    private fun backElement(player: Player) =
        KcGui.elements.backEntry(player, 36)

    private fun state(player: Player, configured: Boolean): String =
        KcI18n.text(player, if (configured) "gui.option.configured" else "gui.option.inherited")

    private fun selectedTargetSpec(route: MenuRoute): TargetSpec? {
        val node = node(route) ?: return null
        return when (route.payload[ROLE]) {
            "destination" -> node.destinationTargetSpec
            "context_executor" -> node.contextOverride?.executor
            "context_target" -> node.contextOverride?.target
            "secondary_target" -> node.secondaryTargetSpec
            else -> node.targetSpec
        }
    }

    private fun updateTargetSpec(route: MenuRoute, change: (TargetSpec) -> TargetSpec) {
        updateNode(route) { node ->
            val current = selectedTargetSpec(route) ?: TargetSpec(TargetKind.NEAREST_ENTITY)
            val updated = change(current)
            when (route.payload[ROLE]) {
                "destination" -> node.destinationTargetSpec = updated
                "context_executor" -> node.contextOverride =
                    (node.contextOverride ?: ExecutionContextSpec()).copy(executor = updated)
                "context_target" -> node.contextOverride =
                    (node.contextOverride ?: ExecutionContextSpec()).copy(target = updated)
                "secondary_target" -> node.secondaryTargetSpec = updated
                else -> node.targetSpec = updated
            }
        }
    }

    private fun toggleTargetFlag(executor: Boolean) = MenuActionHandler { context ->
        updateTargetSpec(context.route) {
            if (executor) it.copy(excludeExecutor = !it.excludeExecutor)
            else it.copy(excludeActivator = !it.excludeActivator)
        }
        MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun targetFilterDialog(
        parameter: String,
        titleKey: String,
        decimal: Boolean = false,
        integer: Boolean = false,
    ) = MenuActionHandler { context ->
        val player = context.player
        val currentSpec = selectedTargetSpec(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
        val current = when (parameter) {
            "entityType" -> currentSpec.entityType
            "minimumDistance" -> currentSpec.minimumDistance?.toString()
            "maximumDistance" -> currentSpec.maximumDistance?.toString()
            "limit" -> currentSpec.limit?.toString()
            "tag" -> currentSpec.tag
            else -> currentSpec.name
        }.orEmpty()
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "target-filter-$parameter",
                title = KcI18n.component(player, titleKey),
                body = listOf(KcI18n.component(player, "gui.dialog.filter_body")),
                inputs = listOf(MenuDialogInput.Text(parameter, KcI18n.component(player, titleKey), current, maxLength = 64)),
                confirm = MenuDialogButton(KcI18n.component(player, "gui.dialog.confirm"), MenuDialogHandler { _, response ->
                    val raw = response.textValue(parameter).trim().takeIf(String::isNotEmpty)
                    if (decimal && raw != null && (raw.toDoubleOrNull() == null || raw.toDouble() < 0.0)) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, "gui.dialog.integer_invalid"))
                    }
                    if (integer && raw != null && (raw.toIntOrNull() ?: 0) < 1) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, "gui.dialog.integer_invalid"))
                    }
                    updateTargetSpec(context.route) { spec ->
                        when (parameter) {
                            "entityType" -> spec.copy(entityType = raw)
                            "minimumDistance" -> spec.copy(minimumDistance = raw?.toDouble())
                            "maximumDistance" -> spec.copy(maximumDistance = raw?.toDouble())
                            "limit" -> spec.copy(limit = raw?.toInt())
                            "tag" -> spec.copy(tag = raw)
                            else -> spec.copy(name = raw)
                        }
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(context.route))
                }),
                cancel = dialogCancel(player, context.route),
            )
        )
        MenuActionResult.Success(MenuUpdate.None)
    }

    private fun localizedValue(player: Player, value: String): String = when (value) {
        "継承" -> KcI18n.text(player, "gui.option.inherited")
        "設定済み" -> KcI18n.text(player, "gui.option.configured")
        "オン" -> KcI18n.text(player, "gui.editor.enabled")
        "オフ" -> KcI18n.text(player, "gui.editor.disabled")
        "一時変数" -> KcI18n.text(player, "gui.option.temporary_variable")
        "ワールド内変数" -> KcI18n.text(player, "gui.field.world_variable")
        "未設定" -> KcI18n.text(player, "gui.field.unset")
        "true" -> KcI18n.text(player, "gui.editor.enabled")
        "false" -> KcI18n.text(player, "gui.editor.disabled")
        else -> enumValueKey(value)?.let { KcI18n.text(player, it) } ?: value
    }

    private fun enumValueKey(value: String): String? {
        runCatching { TargetKind.valueOf(value) }.getOrNull()?.let {
            return when (it) {
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
        }
        runCatching { PositionKind.valueOf(value) }.getOrNull()?.let {
            return when (it) {
                PositionKind.CAPTURED -> "gui.option.current_position"
                PositionKind.DISK -> "gui.option.disk_position"
                PositionKind.EXECUTOR -> "gui.option.executor_position"
                PositionKind.TARGET -> "gui.option.target_position"
                PositionKind.MYWORLD_SPAWN -> "gui.option.myworld_spawn"
                PositionKind.COORDINATES -> "gui.option.coordinates"
                PositionKind.TEMPORARY_VARIABLE -> "gui.option.temporary_variable"
                PositionKind.WORLD_VARIABLE -> "gui.field.world_variable"
            }
        }
        runCatching { FacingKind.valueOf(value) }.getOrNull()?.let {
            return when (it) {
                FacingKind.INHERITED -> "gui.option.unchanged"
                FacingKind.CAPTURED -> "gui.option.current_facing"
                FacingKind.EXECUTOR -> "gui.option.executor_facing"
                FacingKind.TARGET -> "gui.option.face_target"
                FacingKind.COORDINATES -> "gui.option.face_coordinates"
                FacingKind.MYWORLD_SPAWN -> "gui.option.myworld_spawn"
                FacingKind.ROTATION -> "gui.option.numeric"
            }
        }
        runCatching { ConditionKind.valueOf(value) }.getOrNull()?.let { return it.key }
        runCatching { VariableType.valueOf(value) }.getOrNull()?.let {
            return when (it) {
                VariableType.BOOLEAN -> "gui.option.true_false"
                VariableType.INTEGER -> "gui.option.integer"
                VariableType.DECIMAL -> "gui.option.decimal"
                VariableType.TEXT -> "gui.option.text"
                VariableType.POSITION -> "gui.option.position"
                VariableType.ENTITY -> "gui.option.entity_reference"
            }
        }
        runCatching { VariableOperation.valueOf(value) }.getOrNull()?.let {
            return "gui.option.${it.name.lowercase()}"
        }
        runCatching { TargetSort.valueOf(value) }.getOrNull()?.let {
            return "gui.option.sort_${it.name.lowercase()}"
        }
        return null
    }

    private fun script(route: MenuRoute) = scriptId(route)?.let(plugin.scripts::load)

    private fun node(route: MenuRoute): CommandNode? {
        val script = script(route) ?: return null
        val id = route.payload[NODE_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return script.graph.nodes[id]
    }

    private fun updateNode(route: MenuRoute, change: (CommandNode) -> Unit) {
        val script = script(route) ?: return
        val id = route.payload[NODE_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val node = script.graph.nodes[id] ?: return
        change(node)
        plugin.scripts.save(script)
        plugin.placements.refreshDisplaysForScript(script.id)
    }

    companion object {
        private const val PICKER_ID = "command_type"
        private const val SETTINGS_ID = "command_settings"
        private const val TIMER_ID = "timer_settings"
        private const val CONDITION_KIND_ID = "condition_kind"
        private const val CONDITION_DETAIL_ID = "condition_detail"
        private const val VARIABLE_TYPE_ID = "variable_type"
        private const val VARIABLE_OPERATION_ID = "variable_operation"
        private const val VARIABLE_VALUE_ID = "variable_value"
        private const val DISPLAY_MODE_ID = "display_mode"
        private const val CONTEXT_OVERRIDE_ID = "context_override"
        private const val DELETE_ID = "delete_command"
        private const val TARGET_ID = "target_settings"
        private const val TARGET_FILTER_ID = "target_filters"
        private const val POSITION_ID = "position_settings"
        private const val FACING_ID = "facing_settings"
        private const val SCRIPT_ID = "scriptId"
        private const val NODE_ID = "nodeId"
        private const val SOURCE_ID = "sourceId"
        private const val EDGE = "edge"
        private const val MERGE_CONDITION_ID = "mergeConditionId"
        private const val ROLE = "role"
        private const val CURRENT_ITERATION = "\$current_iteration_value"
        private const val CURRENT_LOOP_COUNT = "\$current_loop_count"

        fun typeRoute(
            current: MenuRoute,
            sourceId: UUID?,
            edge: GraphEditor.Edge,
            mergeConditionId: UUID? = null,
        ) =
            requireNotNull(EditorSession.from(current)).route(
                SequenceEditorMenu.OWNER,
                PICKER_ID,
                mapOf(
                    SOURCE_ID to sourceId?.toString().orEmpty(),
                    EDGE to edge.name,
                    MERGE_CONDITION_ID to mergeConditionId?.toString().orEmpty(),
                ),
            )

        fun settingsRoute(current: MenuRoute, nodeId: UUID) =
            requireNotNull(EditorSession.from(current)).route(
                SequenceEditorMenu.OWNER,
                SETTINGS_ID,
                mapOf(NODE_ID to nodeId.toString()),
            )

        fun deleteRoute(current: MenuRoute, nodeId: UUID) =
            requireNotNull(EditorSession.from(current)).route(
                SequenceEditorMenu.OWNER,
                DELETE_ID,
                mapOf(NODE_ID to nodeId.toString()),
            )

        fun timerRoute(current: MenuRoute) =
            requireNotNull(EditorSession.from(current)).route(SequenceEditorMenu.OWNER, TIMER_ID)

        private fun targetRoute(route: MenuRoute, role: String) =
            route.copy(id = TARGET_ID, payload = route.payload + (ROLE to role))

        private fun positionRoute(route: MenuRoute, role: String) =
            route.copy(id = POSITION_ID, payload = route.payload + (ROLE to role))

        private fun facingRoute(route: MenuRoute) = route.copy(id = FACING_ID)

        private fun choiceRoute(route: MenuRoute, id: String) = route.copy(id = id)

        private fun scriptId(route: MenuRoute) = EditorSession.from(route)?.scriptId
    }
}

private data class FieldDialogDefinition(
    val key: String,
    val defaultValue: String,
    val positiveInteger: Boolean,
)

private data class ContextOption(
    val slot: Int,
    val material: Material,
    val name: String,
    val action: String,
    val value: String,
)

private data class DetailOption(
    val material: Material,
    val nameKey: String,
    val action: String,
    val value: String,
)

data class EditorField(
    val key: String,
    val label: String,
    val material: Material,
    val value: (CommandNode) -> String,
)

object EditorMenuLayout {
    private val rowSlots = (18..26).toList()

    fun centeredSlots(count: Int): List<Int> {
        require(count in 1..9)
        val start = (rowSlots.size - count) / 2
        return rowSlots.subList(start, start + count)
    }

    fun fields(type: CommandType): List<EditorField> = when (type) {
        CommandType.TELEPORT -> listOf(
            field("target", "gui.field.target", Material.PLAYER_HEAD) {
                it.targetSpec?.kind?.name ?: "未設定"
            },
            field("destination", "gui.field.destination", Material.COMPASS) {
                it.destinationTargetSpec?.kind?.name ?: it.destinationSpec?.kind?.name ?: "未設定"
            },
            field("context", "gui.field.context", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.GIVE_ITEM -> listOf(
            field("target", "gui.field.give_target", Material.PLAYER_HEAD) {
                it.targetSpec?.kind?.name ?: "未設定"
            },
            field("item", "gui.field.item", Material.CHEST),
            field("count", "gui.field.count", Material.DIAMOND),
            field("context", "gui.field.context", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.ENTITY_ACTION -> listOf(
            field("target", "gui.field.target", Material.PLAYER_HEAD) {
                it.targetSpec?.kind?.name ?: "未設定"
            },
            field("action", "gui.field.action", Material.SADDLE),
            field("other", "gui.field.other", Material.ANVIL) {
                it.secondaryTargetSpec?.kind?.name ?: "未設定"
            },
            field("context", "gui.field.context", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.DISPLAY_TEXT -> listOf(
            field("target", "gui.field.display_target", Material.PLAYER_HEAD) {
                it.targetSpec?.kind?.name ?: "未設定"
            },
            field("mode", "gui.field.mode", Material.OAK_SIGN),
            field("text", "gui.field.text", Material.WRITTEN_BOOK),
            field("stay", "gui.field.duration", Material.CLOCK),
            field("context", "gui.field.context", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.WAIT -> listOf(field("ticks", "gui.field.wait", Material.CLOCK))
        CommandType.CONDITION -> listOf(
            field("inverted", "gui.field.inverted", Material.REDSTONE_TORCH) { if (it.boolean("inverted")) "オン" else "オフ" },
            field("kind", "gui.field.condition_kind", Material.COMPARATOR),
            field("condition", "gui.field.condition_value", Material.TARGET) { it.string("kind") },
            field("context", "gui.field.context", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.CONTEXT -> listOf(
            field("executor", "gui.field.executor", Material.PLAYER_HEAD) {
                it.contextOverride?.executor?.kind?.name ?: "未設定"
            },
            field("target", "gui.field.target", Material.TARGET) {
                it.contextOverride?.target?.kind?.name ?: "未設定"
            },
            field("position", "gui.field.position", Material.COMPASS) {
                it.contextOverride?.position?.kind?.name ?: "未設定"
            },
            field("facing", "gui.field.facing", Material.SPYGLASS) {
                it.contextOverride?.facing?.kind?.name ?: "未設定"
            },
        )
        CommandType.DISK_CALL -> listOf(
            field("diskId", "gui.field.disk", Material.MUSIC_DISC_13),
            field("context", "gui.field.context", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.VARIABLE -> listOf(
            field("scope", "gui.field.scope", Material.ENDER_CHEST) { if (it.string("scope") == "WORLD") "ワールド内変数" else "一時変数" },
            field("name", "gui.field.variable", Material.NAME_TAG),
            field("type", "gui.field.type", Material.STRUCTURE_VOID),
            field("operation", "gui.field.operation", Material.REDSTONE),
            field("value", "gui.field.value", Material.COMPARATOR),
            field("context", "gui.field.context", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.MERGE, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> emptyList()
        CommandType.FOR_START -> listOf(
            field("startSource", "gui.field.start_source", Material.LIME_DYE),
            field("endSource", "gui.field.end_source", Material.RED_DYE),
            field("stepSource", "gui.field.step_source", Material.ARROW),
            field("startValue", "gui.field.start", Material.LIME_DYE),
            field("endValue", "gui.field.end", Material.RED_DYE),
            field("stepValue", "gui.field.step", Material.ARROW),
            field("inclusiveEnd", "gui.field.inclusive_end", Material.COMPARATOR) {
                it.boolean("inclusiveEnd", true).toString()
            },
        )
    }

    private fun field(key: String, label: String, material: Material, value: (CommandNode) -> String = { it.string(key, "未設定") }) =
        EditorField(key, label, material, value)
}
