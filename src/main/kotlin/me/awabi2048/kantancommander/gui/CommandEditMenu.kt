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
import com.awabi2048.ccsystem.api.gui.MenuSoundPolicy
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
import me.awabi2048.kantancommander.model.VariableOperation
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
                        val lane = context.route.payload[LANE]?.toIntOrNull() ?: 0
                        val branchCondition = script.graph.nodes.values
                            .filter { it.type == CommandType.CONDITION }
                            .getOrNull(lane - 1)
                            ?.id
                        val node = GraphEditor.append(script.graph, type, branchCondition)
                        plugin.scripts.save(script)
                        if (type == CommandType.MERGE) {
                            MenuActionResult.Success(MenuUpdate.Replace(SequenceEditorMenu.route(script.id)))
                        } else {
                            MenuActionResult.Success(MenuUpdate.Navigate(settingsRoute(script.id, node.id)))
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
                        updateNode(context.route) { node ->
                            val spec = TargetSpec(kind)
                            when (context.route.payload[ROLE]) {
                                "destination" -> {
                                    node.destinationTargetSpec = spec
                                    node.destinationSpec = null
                                }
                                "context_executor" -> node.contextOverride =
                                    (node.contextOverride ?: ExecutionContextSpec()).copy(executor = spec)
                                "context_target" -> node.contextOverride =
                                    (node.contextOverride ?: ExecutionContextSpec()).copy(target = spec)
                                else -> node.targetSpec = spec
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
                POSITION_ID,
                renderer = { renderPosition(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val kind = context.payload["kind"]?.let { runCatching { PositionKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val location = context.player.location
                        val spec = if (kind == PositionKind.CAPTURED) {
                            PositionSpec(kind, location.x, location.y, location.z, location.yaw, location.pitch)
                        } else PositionSpec(kind)
                        updateNode(context.route) { node ->
                            if (context.route.payload[ROLE] == "destination") {
                                node.destinationSpec = spec
                                node.destinationTargetSpec = null
                            } else {
                                node.contextOverride = (node.contextOverride ?: ExecutionContextSpec()).copy(position = spec)
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
                        if (field == "kind" && node.type == CommandType.CONDITION) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, CONDITION_KIND_ID))
                            )
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
                        val target = when {
                            field == "destination" -> positionRoute(context.route, "destination")
                            field == "executor" -> targetRoute(context.route, "context_executor")
                            field == "target" && node.type == CommandType.CONTEXT -> targetRoute(context.route, "context_target")
                            field == "target" || field == "subject" -> targetRoute(context.route, "node_target")
                            field == "position" -> positionRoute(context.route, "context_position")
                            field == "facing" -> facingRoute(context.route)
                            field == "context" -> targetRoute(context.route, "context_target")
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
                VARIABLE_TYPE_ID,
                renderer = { renderVariableTypes(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val type = context.payload["type"]
                            ?.let { runCatching { VariableType.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        updateNode(context.route) { it.params["type"] = type.name }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                VARIABLE_OPERATION_ID,
                renderer = { renderVariableOperations(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val operation = context.payload["operation"]
                            ?.let { runCatching { VariableOperation.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
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
                    "delete" to MenuActionHandler { MenuActionResult.Ignored },
                ),
            )
        )
    }

    private fun renderPicker(player: Player, route: MenuRoute): InventoryMenuView {
        val script = script(route)
        val lane = route.payload[LANE]?.toIntOrNull() ?: 0
        val types = CommandType.entries.filter { type ->
            type != CommandType.MERGE || GraphEditor.canAppendMerge(script?.graph, lane)
        }
        val elements = types.mapIndexed { index, type ->
            MenuElement(
                EditorMenuLayout.centeredSlots(types.size)[index],
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
        return InventoryMenuView(45, KcGui.title("コマンドを選択"), elements)
    }

    private fun renderSettings(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
            ?: return InventoryMenuView(45, KcGui.title("コマンドの設定"), listOf(backElement(player)))
        val fields = EditorMenuLayout.fields(node.type)
        val elements = fields.mapIndexed { index, field ->
            MenuElement(
                EditorMenuLayout.centeredSlots(fields.size)[index],
                KcGui.item(
                    field.material,
                    field.label,
                    GuiNameStyle.PRIMARY,
                    listOf(GuiLoreLine.Data("現在", field.value(node), "§f")),
                ),
                GuiElementRole.ACTION,
                "field",
                mapOf("field" to field.key),
            )
        }.toMutableList()
        elements += backElement(player)
        return InventoryMenuView(45, KcGui.title("${KcI18n.text(player, node.type.key)}の設定"), elements)
    }

    private fun renderTarget(player: Player, route: MenuRoute): InventoryMenuView {
        val options = listOf(
            Triple(TargetKind.EXECUTOR, Material.PLAYER_HEAD, "実行者"),
            Triple(TargetKind.ACTIVATOR, Material.LEVER, "起動したプレイヤー"),
            Triple(TargetKind.INHERITED_TARGET, Material.TARGET, "現在の対象"),
            Triple(TargetKind.NEAREST_PLAYER, Material.COMPASS, "最も近いプレイヤー"),
            Triple(TargetKind.NEARBY_PLAYERS, Material.FILLED_MAP, "周囲のプレイヤー"),
            Triple(TargetKind.RANDOM_PLAYER, Material.ENDER_EYE, "ランダムなプレイヤー"),
            Triple(TargetKind.NEAREST_ENTITY, Material.ARMOR_STAND, "最も近いエンティティ"),
            Triple(TargetKind.NEARBY_ENTITIES, Material.LEAD, "周囲のエンティティ"),
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
        return InventoryMenuView(45, KcGui.title("対象を設定"), elements)
    }

    private fun renderPosition(player: Player, route: MenuRoute): InventoryMenuView {
        val destination = route.payload[ROLE] == "destination"
        val elements = if (destination) {
            mutableListOf(
                MenuElement(20, KcGui.item(Material.COMPASS, "座標を指定して設定", GuiNameStyle.PRIMARY), GuiElementRole.ACTION, "select", mapOf("kind" to PositionKind.COORDINATES.name)),
                MenuElement(22, KcGui.item(Material.ENDER_PEARL, "ほかのエンティティに移動", GuiNameStyle.PRIMARY), GuiElementRole.ACTION, "target"),
                MenuElement(24, KcGui.item(Material.RECOVERY_COMPASS, "現在位置に設定", GuiNameStyle.PRIMARY), GuiElementRole.ACTION, "select", mapOf("kind" to PositionKind.CAPTURED.name)),
            )
        } else {
            val options = listOf(
                Triple(PositionKind.CAPTURED, Material.RECOVERY_COMPASS, "現在位置"),
                Triple(PositionKind.DISK, Material.COMMAND_BLOCK, "ディスクの位置"),
                Triple(PositionKind.EXECUTOR, Material.PLAYER_HEAD, "実行者の位置"),
                Triple(PositionKind.TARGET, Material.TARGET, "対象の位置"),
                Triple(PositionKind.MYWORLD_SPAWN, Material.RESPAWN_ANCHOR, "MyWorldスポーン"),
                Triple(PositionKind.COORDINATES, Material.COMPASS, "座標を指定"),
                Triple(PositionKind.VARIABLE, Material.REDSTONE, "一時変数"),
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
        return InventoryMenuView(45, KcGui.title(if (destination) "移動先の設定方法" else "実行位置を設定"), elements)
    }

    private fun renderFacing(player: Player, route: MenuRoute): InventoryMenuView {
        val options = listOf(
            Triple(FacingKind.INHERITED, Material.GRAY_DYE, "変更しない"),
            Triple(FacingKind.CAPTURED, Material.SPYGLASS, "現在の向き"),
            Triple(FacingKind.EXECUTOR, Material.PLAYER_HEAD, "実行者と同じ向き"),
            Triple(FacingKind.TARGET, Material.TARGET, "対象を見る"),
            Triple(FacingKind.COORDINATES, Material.COMPASS, "座標を見る"),
            Triple(FacingKind.MYWORLD_SPAWN, Material.RESPAWN_ANCHOR, "MyWorldスポーン"),
            Triple(FacingKind.ROTATION, Material.REPEATER, "数値で指定"),
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
        return InventoryMenuView(45, KcGui.title("向きを設定"), elements)
    }

    private fun renderTimer(player: Player, route: MenuRoute): InventoryMenuView {
        val script = script(route)
        val elements = mutableListOf(
            MenuElement(21, KcGui.item(Material.REDSTONE_TORCH, "オフ", GuiNameStyle.PRIMARY), GuiElementRole.ACTION, "off"),
            MenuElement(
                23,
                KcGui.item(
                    Material.CLOCK,
                    "オン",
                    GuiNameStyle.PRIMARY,
                    listOf(GuiLoreLine.Data("実行間隔", "${script?.timer?.intervalUnits ?: 1}単位", "§f")),
                ),
                GuiElementRole.ACTION,
                "on",
            ),
            backElement(player),
        )
        return InventoryMenuView(45, KcGui.title("タイマー設定"), elements)
    }

    private fun renderConditionKinds(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(ConditionKind.TARGET_EXISTS, Material.ENDER_EYE, "対象が存在する"),
            Triple(ConditionKind.ENTITY_STATE, Material.PLAYER_HEAD, "プレイヤー・エンティティの状態"),
            Triple(ConditionKind.VARIABLE_STATE, Material.REDSTONE, "一時変数の状態"),
            Triple(ConditionKind.BLOCK_STATE, Material.GRASS_BLOCK, "ブロック状態"),
            Triple(ConditionKind.ITEM_POSSESSION, Material.CHEST, "アイテム所持"),
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
        return InventoryMenuView(45, KcGui.title("条件種別"), elements)
    }

    private fun renderVariableTypes(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(VariableType.BOOLEAN, Material.LEVER, "真偽値"),
            Triple(VariableType.INTEGER, Material.REPEATER, "整数"),
            Triple(VariableType.DECIMAL, Material.COMPARATOR, "小数"),
            Triple(VariableType.TEXT, Material.WRITABLE_BOOK, "文字列"),
            Triple(VariableType.POSITION, Material.COMPASS, "位置"),
            Triple(VariableType.ENTITY, Material.PLAYER_HEAD, "対象参照"),
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
        return InventoryMenuView(45, KcGui.title("一時変数の型"), elements)
    }

    private fun renderVariableOperations(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(VariableOperation.SET, Material.LIME_DYE, "設定"),
            Triple(VariableOperation.ADD, Material.SLIME_BALL, "加算"),
            Triple(VariableOperation.SUBTRACT, Material.FERMENTED_SPIDER_EYE, "減算"),
            Triple(VariableOperation.TOGGLE, Material.LEVER, "切り替え"),
            Triple(VariableOperation.STORE_POSITION, Material.COMPASS, "位置を保存"),
            Triple(VariableOperation.STORE_TARGET, Material.PLAYER_HEAD, "対象を保存"),
            Triple(VariableOperation.CLEAR, Material.BARRIER, "消去"),
        )
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
        return InventoryMenuView(45, KcGui.title("一時変数の操作"), elements)
    }

    private fun renderDelete(player: Player, route: MenuRoute): InventoryMenuView {
        val elements = listOf(
            MenuElement(20, KcGui.item(Material.BARRIER, "削除を中止", GuiNameStyle.PRIMARY), GuiElementRole.CANCEL, "back"),
            MenuElement(24, KcGui.item(Material.RED_CONCRETE, "コマンドを削除", GuiNameStyle.DANGER), GuiElementRole.ACTION, "delete"),
        )
        return InventoryMenuView(45, KcGui.title("コマンド削除の確認"), elements)
    }

    private fun showTimerDialog(player: Player, route: MenuRoute, scriptId: UUID, units: Int) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "timer-edit",
                title = Component.text("タイマー設定"),
                body = listOf(Component.text("10 tick（0.5秒）を1単位として、1～86400単位で指定してください。")),
                inputs = listOf(
                    MenuDialogInput.Text(
                        "units",
                        Component.text("実行間隔"),
                        units.toString(),
                        maxLength = 5,
                    )
                ),
                confirm = MenuDialogButton(Component.text("オンにする"), MenuDialogHandler { _, response ->
                    val value = response.textValue("units").toIntOrNull()
                    if (value == null || value !in 1..MAX_TIMER_UNITS) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            Component.text("1～86400の整数で指定してください。")
                        )
                    }
                    val script = plugin.scripts.load(scriptId)
                        ?: return@MenuDialogHandler MenuActionResult.Ignored
                    script.timer.enabled = true
                    script.timer.intervalUnits = value
                    plugin.scripts.save(script)
                    plugin.placements.refreshDisplaysForScript(script.id)
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(Component.text("戻る"), MenuDialogHandler { _, _ ->
                    MenuActionResult.Success(MenuUpdate.Replace(route), MenuSoundPolicy.Silent)
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
                title = Component.text("一時変数"),
                body = listOf(Component.text("MyWorld内で共有する変数名を指定してください。")),
                inputs = listOf(
                    MenuDialogInput.Text(
                        "name",
                        Component.text("変数名"),
                        currentName,
                        maxLength = 64,
                    )
                ),
                confirm = MenuDialogButton(Component.text("設定"), MenuDialogHandler { _, response ->
                    val name = response.textValue("name").trim().lowercase()
                    if (!name.matches(Regex("[a-z0-9_.-]{1,64}"))) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            Component.text("半角英小文字・数字・_ . - を1～64文字で指定してください。")
                        )
                    }
                    updateNode(route) { it.params["name"] = name }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(Component.text("戻る"), MenuDialogHandler { _, _ ->
                    MenuActionResult.Success(MenuUpdate.Replace(route), MenuSoundPolicy.Silent)
                }),
            )
        )
    }

    private fun back() = MenuActionHandler { MenuActionResult.Success(MenuUpdate.Back) }

    private fun backElement(player: Player) =
        MenuElement(36, KcGui.elements.backItem(KcI18n.text(player, "gui.common.back")), GuiElementRole.BACK, "back")

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
        private const val VARIABLE_TYPE_ID = "variable_type"
        private const val VARIABLE_OPERATION_ID = "variable_operation"
        private const val DELETE_ID = "delete_command"
        private const val TARGET_ID = "target_settings"
        private const val POSITION_ID = "position_settings"
        private const val FACING_ID = "facing_settings"
        private const val SCRIPT_ID = "scriptId"
        private const val NODE_ID = "nodeId"
        private const val LANE = "lane"
        private const val ROLE = "role"

        fun typeRoute(scriptId: UUID, lane: Int = 0) =
            MenuRoute(SequenceEditorMenu.OWNER, PICKER_ID, mapOf(SCRIPT_ID to scriptId.toString(), LANE to lane.toString()))

        fun settingsRoute(scriptId: UUID, nodeId: UUID) =
            MenuRoute(SequenceEditorMenu.OWNER, SETTINGS_ID, mapOf(SCRIPT_ID to scriptId.toString(), NODE_ID to nodeId.toString()))

        fun deleteRoute(scriptId: UUID, nodeId: UUID) =
            MenuRoute(SequenceEditorMenu.OWNER, DELETE_ID, mapOf(SCRIPT_ID to scriptId.toString(), NODE_ID to nodeId.toString()))

        fun timerRoute(scriptId: UUID) =
            MenuRoute(SequenceEditorMenu.OWNER, TIMER_ID, mapOf(SCRIPT_ID to scriptId.toString()))

        private fun targetRoute(route: MenuRoute, role: String) =
            route.copy(id = TARGET_ID, payload = route.payload + (ROLE to role))

        private fun positionRoute(route: MenuRoute, role: String) =
            route.copy(id = POSITION_ID, payload = route.payload + (ROLE to role))

        private fun facingRoute(route: MenuRoute) = route.copy(id = FACING_ID)

        private fun choiceRoute(route: MenuRoute, id: String) = route.copy(id = id)

        private fun scriptId(route: MenuRoute) =
            route.payload[SCRIPT_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}

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
            field("target", "対象", Material.PLAYER_HEAD),
            field("destination", "移動先", Material.COMPASS),
            field("context", "個別コンテキスト", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.GIVE_ITEM -> listOf(
            field("target", "付与対象", Material.PLAYER_HEAD),
            field("item", "付与アイテム", Material.CHEST),
            field("count", "個数", Material.DIAMOND),
            field("context", "個別コンテキスト", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.ENTITY_ACTION -> listOf(
            field("target", "対象", Material.PLAYER_HEAD),
            field("action", "アクション", Material.SADDLE),
            field("other", "相手・対象物", Material.ANVIL),
            field("context", "個別コンテキスト", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.DISPLAY_TEXT -> listOf(
            field("target", "表示対象", Material.PLAYER_HEAD),
            field("mode", "表示方式", Material.OAK_SIGN),
            field("text", "表示内容", Material.WRITTEN_BOOK),
            field("stay", "表示時間", Material.CLOCK),
            field("context", "個別コンテキスト", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.WAIT -> listOf(field("ticks", "待機時間", Material.CLOCK))
        CommandType.CONDITION -> listOf(
            field("kind", "条件種別", Material.COMPARATOR),
            field("condition", "条件値", Material.TARGET) { it.summary() },
            field("context", "個別コンテキスト", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.CONTEXT -> listOf(
            field("executor", "実行者", Material.PLAYER_HEAD),
            field("target", "対象", Material.TARGET),
            field("position", "実行位置", Material.COMPASS),
            field("facing", "向き", Material.SPYGLASS),
        )
        CommandType.DISK_CALL -> listOf(
            field("diskId", "呼び出すディスク", Material.MUSIC_DISC_13),
            field("mode", "参照方式", Material.ENDER_EYE),
            field("context", "個別コンテキスト", Material.RECOVERY_COMPASS) { if (it.contextOverride == null) "継承" else "設定済み" },
        )
        CommandType.VARIABLE -> listOf(
            field("name", "一時変数", Material.NAME_TAG),
            field("type", "型", Material.STRUCTURE_VOID),
            field("operation", "操作", Material.REDSTONE),
            field("value", "値", Material.COMPARATOR),
        )
        CommandType.MERGE -> emptyList()
    }

    private fun field(key: String, label: String, material: Material, value: (CommandNode) -> String = { it.string(key, "未設定") }) =
        EditorField(key, label, material, value)
}
