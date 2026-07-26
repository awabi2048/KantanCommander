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
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.ActivationMode
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
                SETTINGS_ID,
                renderer = { renderSettings(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "field" to MenuActionHandler { MenuActionResult.Success(MenuUpdate.Refresh) },
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
                    "on" to MenuActionHandler { MenuActionResult.Success(MenuUpdate.Refresh) },
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
                    "delete" to MenuActionHandler { MenuActionResult.Success(MenuUpdate.Back) },
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

    private fun renderDelete(player: Player, route: MenuRoute): InventoryMenuView {
        val elements = listOf(
            MenuElement(20, KcGui.item(Material.BARRIER, "削除を中止", GuiNameStyle.PRIMARY), GuiElementRole.CANCEL, "back"),
            MenuElement(24, KcGui.item(Material.RED_CONCRETE, "コマンドを削除", GuiNameStyle.DANGER), GuiElementRole.ACTION, "delete"),
        )
        return InventoryMenuView(45, KcGui.title("コマンド削除の確認"), elements)
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

    companion object {
        private const val PICKER_ID = "command_type"
        private const val SETTINGS_ID = "command_settings"
        private const val TIMER_ID = "timer_settings"
        private const val DELETE_ID = "delete_command"
        private const val SCRIPT_ID = "scriptId"
        private const val NODE_ID = "nodeId"
        private const val LANE = "lane"

        fun typeRoute(scriptId: UUID, lane: Int = 0) =
            MenuRoute(SequenceEditorMenu.OWNER, PICKER_ID, mapOf(SCRIPT_ID to scriptId.toString(), LANE to lane.toString()))

        fun settingsRoute(scriptId: UUID, nodeId: UUID) =
            MenuRoute(SequenceEditorMenu.OWNER, SETTINGS_ID, mapOf(SCRIPT_ID to scriptId.toString(), NODE_ID to nodeId.toString()))

        fun deleteRoute(scriptId: UUID, nodeId: UUID) =
            MenuRoute(SequenceEditorMenu.OWNER, DELETE_ID, mapOf(SCRIPT_ID to scriptId.toString(), NODE_ID to nodeId.toString()))

        fun timerRoute(scriptId: UUID) =
            MenuRoute(SequenceEditorMenu.OWNER, TIMER_ID, mapOf(SCRIPT_ID to scriptId.toString()))

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
        CommandType.MERGE -> emptyList()
    }

    private fun field(key: String, label: String, material: Material, value: (CommandNode) -> String = { it.string(key, "未設定") }) =
        EditorField(key, label, material, value)
}
