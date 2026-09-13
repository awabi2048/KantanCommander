package me.awabi2048.kantancommander.gui
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

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
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import com.awabi2048.ccsystem.api.gui.MenuCustomFormRequest
import com.awabi2048.ccsystem.api.gui.MenuFormHandler
import com.awabi2048.ccsystem.api.gui.MenuFormInput
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.DiskPlacement
import me.awabi2048.kantancommander.model.DiskArgument
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.displayKey
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.hasDiskContent
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
                        val continuation = context.payload["continuationId"]?.takeIf(String::isNotBlank)
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(
                                CommandEditMenu.typeRoute(context.route, source, edge, mergeCondition, continuation),
                            ),
                        )
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
                        // 簡易GUIは左クリックのみで操作します。制御系コマンドは
                        // 表示のみとし、設定変更はフルGUI（ジェスチャーGUI）へ案内します。
                        // 内部データはフル形式のまま保持するため、削除もしません。
                        // issue #8: 呼び出し設定は制御系ですが両GUIで編集するため除外します。
                        if (!CommandPresentationPolicy.isSimpleEditable(node.type) &&
                            node.type != CommandType.DISK_CALL
                        ) {
                            return@handler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SIMPLE_LOCKED_MESSAGE),
                            )
                        }
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(CommandEditMenu.settingsRoute(context.route, node.id)),
                        )
                    },
                    "output" to handler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(diskSettingsRoute(context.route)))
                    },
                    "save_library" to handler { context ->
                        val source = scriptId(context.route)?.let(plugin.scripts::load)
                            ?: return@handler MenuActionResult.Ignored
                        plugin.scripts.copyToLibrary(source, context.player.uniqueId)
                        context.player.sendMessage(KcI18n.text(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_SAVED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                ),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                OWNER,
                WRITE_ID,
                renderer = { renderWriteConfirm(it.player, it.route) },
                actions = mapOf(
                    "write_cancel" to handler { MenuActionResult.Success(MenuUpdate.Close) },
                    "write" to handler { context ->
                        val placement = placement(context.route) ?: return@handler MenuActionResult.Ignored
                        val diskScriptId = context.route.payload[DISK_ID]
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: return@handler MenuActionResult.Ignored
                        if (!overwritePlacement(context.player, placement, diskScriptId)) {
                            return@handler MenuActionResult.Ignored
                        }
                        MenuActionResult.Success(MenuUpdate.Close)
                    },
                ),
            ),
        )
        runtime.register(
            InventoryMenuDefinition(
                OWNER,
                DISK_SETTINGS_ID,
                renderer = { renderDiskSettings(it.player, it.route) },
                actions = mapOf(
                    "back" to handler { MenuActionResult.Success(MenuUpdate.Back) },
                    "takeOut" to handler { context ->
                        val current = placement(context.route) ?: return@handler MenuActionResult.Ignored
                        if (!outputDisk(context.player, current)) return@handler MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "argName" to handler { context ->
                        val name = context.payload["name"]?.takeIf(String::isNotBlank)
                            ?: return@handler MenuActionResult.Ignored
                        openDiskArgumentNameForm(context.player, context.route, name)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "argType" to handler { context ->
                        if (!cycleDiskArgumentType(context.player, context.route, context.payload["name"])) {
                            return@handler MenuActionResult.Ignored
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "argDelete" to handler { context ->
                        if (!deleteDiskArgument(context.player, context.route, context.payload["name"])) {
                            return@handler MenuActionResult.Ignored
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "argAdd" to handler { context ->
                        openDiskArgumentNameForm(context.player, context.route, null)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            ),
        )
    }

    fun open(player: Player, scriptId: UUID) = runtime.open(player, route(scriptId))
    fun open(player: Player, placement: DiskPlacement) = runtime.open(player, route(placement))

    /** 空のかんたんコマンダー制御ブロックへプログラムディスクの内容を書き込む確認画面を開く。 */
    fun openWriteConfirm(player: Player, placement: DiskPlacement, diskId: UUID) =
        runtime.open(
            player,
            EditorSession.forPlacement(placement).route(OWNER, WRITE_ID, mapOf(DISK_ID to diskId.toString())),
        )

    private fun navigate(context: MenuActionContext): MenuActionResult {
        val script = scriptId(context.route)?.let(plugin.scripts::load) ?: return MenuActionResult.Ignored
        val layout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrElse { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "インベントリGUIの経路移動でレイアウトを生成できません: script=${script.id}",
                failure,
            )
            // 移動操作の拒否理由をイベントログだけに残さず、操作者へ即時に返します。
            return MenuActionResult.Rejected(GraphLayoutFailureFeedback.renderMessage(context.player))
        }
        val origin = origin(context.route)
        // 簡易GUIは左クリックのみのため、移動方向はボタンごとの方向指定で受けます。
        // 右クリック・シフトクリックによる方向指定は行いません。
        val delta = when (context.payload["direction"]) {
            "left" -> MapPoint(-1, 0)
            "right" -> MapPoint(1, 0)
            "up" -> MapPoint(0, -1)
            "down" -> MapPoint(0, 1)
            else -> return MenuActionResult.Ignored
        }
        if (!layout.canMove(origin, delta.x, delta.y, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)) {
            return MenuActionResult.Ignored
        }
        val next = MapPoint(origin.x + delta.x, origin.y + delta.y)
        return MenuActionResult.Success(MenuUpdate.Replace(route(context.route, next.x, next.y)))
    }

    private fun render(player: Player, route: MenuRoute): InventoryMenuView {
        val script = scriptId(route)?.let(plugin.scripts::load)
            ?: return InventoryMenuView(45, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TITLE)), emptyList())
        val origin = origin(route)
        val layout = runCatching { GraphLayoutEngine.layout(script.graph) }.getOrElse { failure ->
            plugin.logger.log(
                java.util.logging.Level.WARNING,
                "インベントリGUIの経路描画でレイアウトを生成できません: script=${script.id}",
                failure,
            )
            return InventoryMenuView(
                45,
                KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TITLE)),
                listOf(
                    KcGui.menuEntry(
                        player = player,
                        slot = 22,
                        material = Material.BARRIER,
                        name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_UPPER_RENDER),
                        style = GuiNameStyle.DANGER,
                        description = listOf(
                            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_REOPEN_HINT),
                        ),
                    ),
                ),
            )
        }
        val elements = mutableListOf<MenuElement>()

        layout.viewport(origin, VIEWPORT_WIDTH, VIEWPORT_HEIGHT).forEach { (point, cell) ->
            val slot = (point.y + 1) * 9 + point.x
            val node = cell.nodeId?.let(script.graph.nodes::get)
            elements += if (node != null) commandElement(player, slot, node) else pathElement(player, slot, cell)
        }
        // 簡易GUIではプログラム共通設定（起動条件・タイマー）を扱いません。
        // 移動は左クリックのみのため、方向ごとに専用ボタンを置きます。
        // 実行不能な方向のボタンは位置を維持したまま装飾表示にします。
        elements += navigateButton(player, 36, "left", Material.RECOVERY_COMPASS,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_LEFT,
            layout.canMove(origin, -1, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
        elements += navigateButton(player, 37, "right", Material.RECOVERY_COMPASS,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_RIGHT,
            layout.canMove(origin, 1, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
        elements += navigateButton(player, 41, "up", Material.RECOVERY_COMPASS,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_UP,
            layout.canMove(origin, 0, -1, VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
        elements += navigateButton(player, 43, "down", Material.RECOVERY_COMPASS,
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_MOVE_DOWN,
            layout.canMove(origin, 0, 1, VIEWPORT_WIDTH, VIEWPORT_HEIGHT))
        elements += KcGui.menuEntry(
            player = player,
            slot = 38,
            material = Material.COMPASS,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CENTER),
            style = GuiNameStyle.PRIMARY,
            description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CENTER_DESCRIPTION),
            actions = listOf(
                GuiMenuActionIntent.AnyClick("center", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CENTER_ACTION)),
            ),
        )
        elements += KcGui.elements.menuDisplay(
            GuiMenuDisplaySpec(
                slot = 39,
                item = GuiItemSpec(
                    material = Material.MAP,
                    name = GuiNameSpec.Text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INFO), GuiNameStyle.PRIMARY),
                    lore = GuiLoreSpec.Blocks(
                        listOf(
                            GuiLoreBlock(KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INFO_DESCRIPTION).map(GuiLoreLine::Text)),
                            // セルの色自体が表示範囲を表すため、図は文字列化せずAdventure Componentで保持します。
                            GuiLoreBlock(listOf(GuiLoreLine.Component(GraphDiagramRenderer.render(layout, origin)))),
                            GuiLoreBlock(
                                listOf(
                                    GuiLoreLine.Data(
                                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INFO_VIEWPORT_LABEL),
                                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INFO_VIEWPORT_VALUE),
                                        "§e",
                                    ),
                                    GuiLoreLine.Data(
                                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INFO_OUTSIDE_LABEL),
                                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INFO_OUTSIDE_VALUE),
                                        "§7",
                                    ),
                                    GuiLoreLine.Data(
                                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INFO_OMITTED_LABEL),
                                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INFO_OMITTED_VALUE),
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
        val currentPlacement = placement(route)
        if (currentPlacement != null) {
            // issue #8: 取り出し操作はディスク設定の子画面へ移し、引数定義と同列に扱います。
            // 引数定義は内容がなくても行えるため、内容有無にかかわらず表示します。
            elements += KcGui.menuEntry(
                player = player,
                slot = 40,
                material = Material.MUSIC_DISC_OTHERSIDE,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISK_SETTINGS),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_OUTPUT_DESCRIPTION),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick("output", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISK_SETTINGS)),
                ),
            )
        }
        elements += KcGui.menuEntry(
            player = player,
            slot = 42,
            material = Material.BOOK,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SAVE),
            style = GuiNameStyle.PRIMARY,
            description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SAVE_DESCRIPTION),
            actions = listOf(
                GuiMenuActionIntent.AnyClick("save_library", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SAVE_ACTION)),
            ),
        )
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TITLE)), elements)
    }

    /**
     * 簡易GUIの移動ボタンです。左クリックのみで方向が確定するため、
     * 方向ごとに独立したAnyClick項目として生成します。
     */
    private fun navigateButton(
        player: Player,
        slot: Int,
        direction: String,
        material: Material,
        labelKey: LocalizationKey<String>,
        enabled: Boolean,
    ): MenuElement {
        if (!enabled) {
            return MenuElement(
                slot,
                KcGui.elements.decoration(Material.WHITE_STAINED_GLASS_PANE),
                GuiElementRole.DECORATION,
            )
        }
        return KcGui.menuEntry(
            player = player,
            slot = slot,
            material = material,
            name = KcI18n.text(player, labelKey),
            style = GuiNameStyle.PRIMARY,
            actions = listOf(
                GuiMenuActionIntent.AnyClick(
                    actionId = "navigate",
                    label = KcI18n.text(player, labelKey),
                    payload = mapOf("direction" to direction),
                ),
            ),
        )
    }

    private fun commandElement(player: Player, slot: Int, node: CommandNode): MenuElement {
        // 簡易GUIは左クリックのみのため、編集・削除の区別はジェスチャーで行いません。
        // 削除は設定画面内の削除項目へ集約し、制御系コマンドは表示のみとします。
        // issue #8: 呼び出し設定は制御系ですが両GUIで編集するため編集扱いとします。
        val editable = CommandPresentationPolicy.isSimpleEditable(node.type) || node.type == CommandType.DISK_CALL
        return KcGui.menuEntry(
            player = player,
            slot = slot,
            material = node.type.icon,
            name = KcI18n.text(player, node.type.key),
            role = GuiElementRole.ACTION,
            description = KcI18n.list(player, node.type.descriptionKey) +
                if (editable) emptyList() else KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SIMPLE_LOCKED_DESCRIPTION),
            data = listOf(
                GuiMenuEntryData(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SETTINGS_SUMMARY_LABEL),
                    commandSummary(player, node),
                ),
            ),
            actions = listOf(
                GuiMenuActionIntent.AnyClick(
                    "command",
                    KcI18n.text(
                        player,
                        if (editable) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_EDIT_ACTION
                        else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SIMPLE_LOCKED_ACTION,
                    ),
                    mapOf("nodeId" to node.id.toString()),
                ),
            ),
        )
    }

    private fun commandSummary(player: Player, node: CommandNode): String = when (node.type) {
        CommandType.TELEPORT -> {
            val target = node.targetSpec?.kind?.let { localizedTarget(player, it) }
                ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
            val destination = node.destinationTargetSpec?.kind?.let { localizedTarget(player, it) }
                ?: node.destinationSpec?.kind?.let { localizedPosition(player, it) }
                ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
            "$target → $destination"
        }
        CommandType.GIVE_ITEM -> "${node.string("item")} ×${node.string("count", "1")}"
        CommandType.PLACE_ITEM -> "${node.string("item")} ×${node.string("count", "1")}"
        CommandType.DISPLAY_TEXT -> node.string("text").ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }
        CommandType.WAIT -> "${node.string("seconds", "1")}秒"
        CommandType.CONDITION -> node.conditionSubject()
            ?.let { KcI18n.text(player, it.key) }
            ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        CommandType.DISK_CALL ->
            KcI18n.text(player, if (node.snapshot == null) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONFIGURED)
        CommandType.VARIABLE -> node.string("name").ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }
        CommandType.FOR_START ->
            "×${node.string("count", "1")}"
        else -> KcI18n.text(player, node.type.key)
    }

    private fun localizedTarget(player: Player, kind: TargetKind): String = KcI18n.text(
        player,
        when (kind) {
            TargetKind.NEAREST_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER
            TargetKind.NEARBY_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS
            TargetKind.ALL_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS
            TargetKind.RANDOM_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RANDOM_PLAYER
            TargetKind.NEAREST_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY
            TargetKind.NEARBY_ENTITIES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES
            TargetKind.FIXED_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
            TargetKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
        }
    )

    private fun localizedPosition(player: Player, kind: PositionKind): String = KcI18n.text(
        player,
        when (kind) {
            PositionKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAPTURED_POSITION
            PositionKind.DISK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION
            PositionKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION
            PositionKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
            PositionKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN
            PositionKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES
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
                name = KcI18n.text(player, if (addAtEnd) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ADD else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INSERT),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(
                    player,
                    if (addAtEnd) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ADD_DESCRIPTION else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INSERT_DESCRIPTION,
                ),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick(
                        actionId = "add",
                        label = KcI18n.text(player, if (addAtEnd) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ADD else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INSERT),
                        payload = mapOf(
                    "sourceId" to insertionTarget.sourceId?.toString().orEmpty(),
                    "edge" to insertionTarget.edge.name,
                    "mergeConditionId" to insertionTarget.mergeConditionId?.toString().orEmpty(),
                    "continuationId" to insertionTarget.continuationId?.toString().orEmpty(),
                        ),
                    ),
                ),
            )
        }
        val material = MapCellMaterialPolicy.material(cell.kind)
        return MenuElement(slot, KcGui.elements.decoration(material), GuiElementRole.DECORATION)
    }

    private fun placement(route: MenuRoute): DiskPlacement? {
        val placement = EditorSession.from(route)?.placement ?: return null
        return plugin.placements.find(
            plugin.server.getWorld(placement.world),
            placement.x,
            placement.y,
            placement.z,
        )
    }

    /** プログラムディスクの内容で、空のかんたんコマンダー制御ブロックを上書きする確認画面。 */
    private fun renderWriteConfirm(player: Player, route: MenuRoute): InventoryMenuView {
        val elements = listOf(
            KcGui.menuEntry(
                player = player,
                slot = 20,
                material = Material.BARRIER,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WRITE_CANCEL),
                style = GuiNameStyle.MUTED,
                role = GuiElementRole.ACTION,
                actions = listOf(
                    GuiMenuActionIntent.AnyClick(
                        "write_cancel",
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WRITE_CANCEL),
                    ),
                ),
            ),
            KcGui.menuEntry(
                player = player,
                slot = 24,
                material = Material.MUSIC_DISC_OTHERSIDE,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WRITE_CONFIRM),
                style = GuiNameStyle.PRIMARY,
                role = GuiElementRole.ACTION,
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WRITE_DESCRIPTION),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick(
                        "write",
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WRITE_CONFIRM),
                    ),
                ),
            ),
        )
        return InventoryMenuView(
            45,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_WRITE_TITLE)),
            elements,
        )
    }

    /** 空のかんたんコマンダー制御ブロックのスクリプトを、プログラムディスクの内容で上書きする。 */
    private fun overwritePlacement(player: Player, placement: DiskPlacement, diskScriptId: UUID): Boolean {
        if (!plugin.placementAccess.requireManage(player, placement.world)) {
            return false
        }
        val diskScript = plugin.scripts.load(diskScriptId) ?: return false
        // 空判定からコピー保存までを同じプログラムロックへ束ね、Gesture GUIの
        // 同時編集が間に入っても、空ではない配置へ古いディスク内容を上書きしません。
        val written = runCatching {
            plugin.scripts.update(placement.scriptId, player.uniqueId) { placementScript ->
                if (placementScript.graph.nodes.isNotEmpty()) return@update null
                placementScript.name = diskScript.name
                placementScript.activation = diskScript.activation
                placementScript.timer = diskScript.timer.copy()
                placementScript.graph = diskScript.graph.deepCopy()
                placementScript.contentModified = diskScript.contentModified
                true
            }
        }
            .onFailure { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "空の配置へディスクを書き込めませんでした: placement=${placement.key}",
                    failure,
                )
            }
            .getOrElse { return false } == true
        if (!written) return false
        plugin.resetActivationTiming(placement.scriptId)
        runCatching { plugin.placements.refreshDisplaysForScript(placement.scriptId) }
            .onFailure { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "ディスク書き込み後の配置表示更新に失敗しました: placement=${placement.key}",
                    failure,
                )
            }
        // インベントリ経路からの書き込みも、同じプログラムを開いているGesture画面へ
        // 即時配布します。これを省くと、表示は古いままでも次の保存時にCASだけが
        // 失敗し、利用者には競合理由が見えない状態になります。
        plugin.gestureEditor.refreshForScript(placement.scriptId)
        player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_DISK_WRITTEN))
        return true
    }

    /**
     * issue #8 のディスク設定子画面です（簡易GUI）。
     *
     * 取り出し操作と引数定義の一覧を示します。引数は名前・型・削除の3行1組で
     * 6組まで表示し、追加行と戻る行を続けます。保存規則はジェスチャーGUIと同一です。
     */
    private fun renderDiskSettings(player: Player, route: MenuRoute): InventoryMenuView {
        val currentPlacement = placement(route)
        val script = currentPlacement?.let { plugin.scripts.load(it.scriptId) }
        if (currentPlacement == null || script == null) {
            return InventoryMenuView(
                45,
                KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISK_SETTINGS)),
                emptyList(),
            )
        }
        val elements = mutableListOf<MenuElement>()
        var slot = 10
        if (script.hasDiskContent()) {
            elements += KcGui.menuEntry(
                player = player,
                slot = slot++,
                material = Material.MUSIC_DISC_OTHERSIDE,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_TAKE_OUT),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_OUTPUT_DESCRIPTION),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick("takeOut", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_TAKE_OUT)),
                ),
            )
        }
        // 引数が多い場合は先頭6組だけを示します。未設定のまま残るため検証で検出します。
        script.arguments.take(6).forEach { argument ->
            elements += KcGui.menuEntry(
                player = player,
                slot = slot++,
                material = Material.NAME_TAG,
                name = argument.name,
                style = GuiNameStyle.PRIMARY,
                data = listOf(
                    GuiMenuEntryData(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TYPE),
                        KcI18n.text(player, argument.type.displayKey()),
                    ),
                ),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick("argName", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_NAME), mapOf("name" to argument.name)),
                ),
            )
            elements += KcGui.menuEntry(
                player = player,
                slot = slot++,
                material = Material.BOOK,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TYPE),
                style = GuiNameStyle.PRIMARY,
                data = listOf(GuiMenuEntryData(argument.name, KcI18n.text(player, argument.type.displayKey()))),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick("argType", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TYPE), mapOf("name" to argument.name)),
                ),
            )
            elements += KcGui.menuEntry(
                player = player,
                slot = slot++,
                material = Material.BARRIER,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REMOVE),
                style = GuiNameStyle.DANGER,
                data = listOf(
                    GuiMenuEntryData(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE),
                        argument.name,
                    ),
                ),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick("argDelete", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REMOVE), mapOf("name" to argument.name)),
                ),
            )
        }
        elements += KcGui.menuEntry(
            player = player,
            slot = slot++,
            material = Material.EMERALD,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_ARG_ADD),
            style = GuiNameStyle.PRIMARY,
            actions = listOf(
                GuiMenuActionIntent.AnyClick("argAdd", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_ARG_ADD)),
            ),
        )
        elements += KcGui.menuEntry(
            player = player,
            slot = 49,
            material = Material.ARROW,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK),
            style = GuiNameStyle.PRIMARY,
            actions = listOf(
                GuiMenuActionIntent.AnyClick("back", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_COMMON_BACK)),
            ),
        )
        return InventoryMenuView(
            54,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISK_SETTINGS)),
            elements,
        )
    }

    /** 引数定義の追加・改名を名前入力で受け付けます。空欄・重複・不正名は拒否します。 */
    private fun openDiskArgumentNameForm(player: Player, route: MenuRoute, current: String?) {
        val scriptId = scriptId(route) ?: return
        val script = plugin.scripts.load(scriptId) ?: return
        showDiskSettingsTextForm(
            player = player,
            route = route,
            formId = "disk-arg-name",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISK_SETTINGS),
            label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_ARG_NAME),
            initial = current.orEmpty(),
            spec = CommandDialogSpecs.variableName,
        ) { raw ->
            if (raw.isEmpty() || !CommandValueRules.isVariableName(raw)) return@showDiskSettingsTextForm false
            if (script.arguments.any { it.name == raw && it.name != current }) return@showDiskSettingsTextForm false
            CommandSettingsModel.updateDiskArguments(plugin, scriptId, editorId = player.uniqueId) { arguments ->
                if (current == null) {
                    arguments += DiskArgument(raw, ValueType.NUMBER)
                } else {
                    val index = arguments.indexOfFirst { it.name == current }
                    if (index >= 0) arguments[index] = arguments[index].copy(name = raw)
                }
            }
        }
    }

    /** 引数定義の型を全型で循環します。 */
    private fun cycleDiskArgumentType(player: Player, route: MenuRoute, name: String?): Boolean {
        val scriptId = scriptId(route) ?: return false
        if (name.isNullOrBlank()) return false
        return CommandSettingsModel.updateDiskArguments(plugin, scriptId, editorId = player.uniqueId) { arguments ->
            val index = arguments.indexOfFirst { it.name == name }
            if (index < 0) return@updateDiskArguments
            val entries = ValueType.entries
            val next = entries[(entries.indexOf(arguments[index].type) + 1) % entries.size]
            arguments[index] = arguments[index].copy(type = next)
        }
    }

    /** 引数定義を削除します。即時確定し、結果を画面に示します。 */
    private fun deleteDiskArgument(player: Player, route: MenuRoute, name: String?): Boolean {
        val scriptId = scriptId(route) ?: return false
        if (name.isNullOrBlank()) return false
        val removed = CommandSettingsModel.updateDiskArguments(plugin, scriptId, editorId = player.uniqueId) { arguments ->
            arguments.removeIf { it.name == name }
        }
        if (removed) {
            player.sendMessage(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_ARG_DELETED),
            )
        }
        return removed
    }

    /**
     * 簡易GUIの文字入力基盤です。
     *
     * 検証仕様はDialogと共有し、画面ごとに検証を複製しません。保存後は
     * 呼び出し元の画面へ戻ります。Formを閉じた場合も復帰します。
     */
    private fun showDiskSettingsTextForm(
        player: Player,
        route: MenuRoute,
        formId: String,
        title: String,
        label: String,
        initial: String,
        spec: CommandDialogSpecs.Spec,
        save: (String) -> Boolean,
    ) {
        val forms = CCSystem.getAPI().getMenuFormService()
        if (!forms.isAvailable(player)) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SIMPLE_FORM_UNAVAILABLE))
            return
        }
        forms.show(
            player,
            MenuCustomFormRequest(
                owner = OWNER,
                id = formId,
                title = title,
                inputs = listOf(MenuFormInput.Text("value", label, "", initial)),
                handler = MenuFormHandler { _, response ->
                    val raw = CommandDialogSpecs.normalize("value", response.textValue("value")).trim()
                    val validationError = spec.validateInput(raw)
                    if (validationError != null) {
                        player.sendMessage(KcI18n.component(player, validationError))
                        return@MenuFormHandler MenuActionResult.Success(MenuUpdate.Refresh)
                    }
                    if (!save(raw)) {
                        player.sendMessage(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        return@MenuFormHandler MenuActionResult.Success(MenuUpdate.Resume)
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                },
                onClosed = MenuFormHandler { _, _ -> MenuActionResult.Success(MenuUpdate.Resume) },
            ),
        )
    }

    private fun outputDisk(player: Player, placement: DiskPlacement): Boolean {
        val source = plugin.scripts.load(placement.scriptId) ?: return false
        // ノードがなくても、名前またはタイマーを明示編集したスクリプトは出力対象です。
        if (!source.hasDiskContent()) return false
        if (!plugin.placementAccess.requireManage(player, placement.world)) {
            return false
        }
        val world = plugin.server.getWorld(placement.world) ?: return false
        val block = world.getBlockAt(placement.x, placement.y, placement.z)
        if (plugin.placements.find(block.location)?.scriptId != source.id) return false

        val output = runCatching { plugin.scripts.copyForItem(source) }.getOrNull() ?: return false
        val item = runCatching { KantanItemService.createDisk(output, player) }.getOrElse {
            runCatching { plugin.scripts.delete(output.id) }
            return false
        }
        player.inventory.addItem(item).values.forEach { world.dropItemNaturally(player.location, it) }
        player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_DISK_OUTPUT))
        return true
    }

    private fun handler(block: (MenuActionContext) -> MenuActionResult) = MenuActionHandler(block)

    companion object {
        const val OWNER = ProgramListMenu.OWNER
        private const val MENU_ID = "editor"
        private const val WRITE_ID = "write_confirm"
        private const val DISK_ID = "diskId"
        /** issue #8: ディスク設定（取り出しと引数定義）の子画面です。 */
        private const val DISK_SETTINGS_ID = "disk_settings"
        private const val VIEWPORT_WIDTH = 9
        private const val VIEWPORT_HEIGHT = 3

        fun route(scriptId: UUID) = EditorSession.forScript(scriptId).route(OWNER, MENU_ID)

        fun route(placement: DiskPlacement) = EditorSession.forPlacement(placement).route(OWNER, MENU_ID)

        fun editorRoute(current: MenuRoute) =
            EditorSession.from(current)?.route(OWNER, MENU_ID) ?: current.copy(id = MENU_ID)

        /** issue #8: 配置のディスク設定画面への経路です。配置文脈を引き継ぎます。 */
        private fun diskSettingsRoute(current: MenuRoute) = current.copy(id = DISK_SETTINGS_ID)

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
        MapCellKind.PATH,
        MapCellKind.BRANCH_PATH,
        -> Material.WHITE_STAINED_GLASS_PANE
        MapCellKind.ADD -> Material.YELLOW_STAINED_GLASS_PANE
        MapCellKind.LOOP_RETURN_PATH -> Material.LIGHT_BLUE_STAINED_GLASS_PANE
        MapCellKind.NODE -> error("ノードの素材はコマンド種別から決定するため、経路素材へ変換できません")
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
