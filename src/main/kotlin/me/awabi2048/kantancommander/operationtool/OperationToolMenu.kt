package me.awabi2048.kantancommander.operationtool

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
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
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.EntityGroupStore
import me.awabi2048.kantancommander.gui.KcGui
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.SpatialVector
import me.awabi2048.kantancommander.structure.StructureSaveResult
import me.awabi2048.kantancommander.structure.StructureStore
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.util.UUID

/**
 * issue #14の操作ツールwandの操作用Inventory GUIです。
 *
 * 画面ファミリーはSettings（情報と設定操作を分離し、現在値と有効な操作だけを表示）
 * とし、背景・Lore・操作案内はCC-SystemのGUI APIへ意味データと実行可否を渡して
 * 生成します。設定アイコンのNameは固定された項目名・命令だけに使用し、
 * 現在値・件数はLoreのデータブロックへ表示します。
 *
 * 本画面は選択・可視化の導線だけを担当し、Structureのシリアライズ・永続化、
 * Entity Groupの永続化、Entity Tag等の実処理は既存domainへ委譲します。
 */
class OperationToolMenu(private val plugin: KantanCommanderPlugin) {
    private val runtime = CCSystem.getAPI().getMenuRuntimeService()
    private val dialogs = CCSystem.getAPI().getMenuDialogService()

    /** 入力ダイアログの応答待ちです。古い応答の二重適用を防ぐためのトークンです。 */
    private val pendingDialogs = mutableMapOf<UUID, String>()

    init {
        runtime.register(areaDefinition())
        runtime.register(entityDefinition())
    }

    /** エリア操作GUIを開きます。有効なエリアがない場合は何もしません。 */
    fun openArea(player: Player) {
        val session = plugin.operationTool.sessions().get(player.uniqueId) ?: return
        if (!session.hasValidArea()) return
        if (!plugin.placementAccess.requireManage(player, player.world.name)) return
        runtime.open(player, MenuRoute(OWNER, AREA_MENU_ID, emptyMap()))
    }

    /** Entity操作GUIを開きます。選択が空の場合は何もしません。 */
    fun openEntity(player: Player) {
        val session = plugin.operationTool.sessions().get(player.uniqueId) ?: return
        pruneVanished(player, session)
        if (session.selected().isEmpty()) return
        if (!plugin.placementAccess.requireManage(player, player.world.name)) return
        runtime.open(player, MenuRoute(OWNER, ENTITY_MENU_ID, emptyMap()))
    }

    private fun areaDefinition(): InventoryMenuDefinition = InventoryMenuDefinition(
        owner = OWNER,
        id = AREA_MENU_ID,
        renderer = { context -> renderArea(context.player) },
        actions = mapOf(
            ACTION_SAVE_STRUCTURE to MenuActionHandler { context ->
                if (!checkManage(context.player)) return@MenuActionHandler MenuActionResult.Ignored
                showStructureDialog(context.player)
                MenuActionResult.Success(MenuUpdate.None)
            },
            ACTION_CLOSE to MenuActionHandler { MenuActionResult.Success(MenuUpdate.Close) },
        ),
    )

    private fun entityDefinition(): InventoryMenuDefinition = InventoryMenuDefinition(
        owner = OWNER,
        id = ENTITY_MENU_ID,
        renderer = { context -> renderEntity(context.player) },
        actions = mapOf(
            ACTION_TAG_ADD to MenuActionHandler { context ->
                if (!checkManage(context.player)) return@MenuActionHandler MenuActionResult.Ignored
                showTagDialog(context.player, add = true)
                MenuActionResult.Success(MenuUpdate.None)
            },
            ACTION_TAG_REMOVE to MenuActionHandler { context ->
                if (!checkManage(context.player)) return@MenuActionHandler MenuActionResult.Ignored
                showTagDialog(context.player, add = false)
                MenuActionResult.Success(MenuUpdate.None)
            },
            ACTION_DELETE to MenuActionHandler { context ->
                if (!checkManage(context.player)) return@MenuActionHandler MenuActionResult.Ignored
                showDeleteDialog(context.player)
                MenuActionResult.Success(MenuUpdate.None)
            },
            ACTION_GROUP_CREATE to MenuActionHandler { context ->
                if (!checkManage(context.player)) return@MenuActionHandler MenuActionResult.Ignored
                showGroupDialog(context.player)
                MenuActionResult.Success(MenuUpdate.None)
            },
            ACTION_CLOSE to MenuActionHandler { MenuActionResult.Success(MenuUpdate.Close) },
        ),
    )

    private fun renderArea(player: Player): InventoryMenuView {
        val layout = KcGui.layouts.settings54()
        val session = plugin.operationTool.sessions().get(player.uniqueId)
        val elements = mutableListOf<MenuElement>()
        val bounds = session?.areaBounds()
        val areaText = if (session != null && bounds != null) {
            val first = requireNotNull(session.pos1)
            val second = requireNotNull(session.pos2)
            "${first.worldName} ${formatPoint(first.position)} → ${formatPoint(second.position)}"
        } else {
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        }
        elements += KcGui.menuEntry(
            player = player,
            slot = layout.infoSlot,
            material = Material.MAP,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TITLE_AREA),
            style = GuiNameStyle.PRIMARY,
            role = GuiElementRole.CONTENT,
            data = listOf(
                GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STATUS_AREA), areaText),
            ),
        )
        if (session != null && session.hasValidArea()) {
            elements += KcGui.menuEntry(
                player = player,
                slot = ACTION_SLOT_SINGLE,
                material = Material.STRUCTURE_BLOCK,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_SAVE_STRUCTURE),
                style = GuiNameStyle.PRIMARY,
                actions = listOf(GuiMenuActionIntent.AnyClick(ACTION_SAVE_STRUCTURE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_SAVE_STRUCTURE))),
            )
        }
        elements += KcGui.elements.backEntry(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TITLE_AREA)), elements)
    }

    private fun renderEntity(player: Player): InventoryMenuView {
        val layout = KcGui.layouts.settings54()
        val session = plugin.operationTool.sessions().get(player.uniqueId)
        val count = session?.selected()?.size ?: 0
        val modeName = session?.let { KcI18n.text(player, it.mode.displayNameKey()) }.orEmpty()
        val elements = mutableListOf<MenuElement>()
        elements += KcGui.menuEntry(
            player = player,
            slot = layout.infoSlot,
            material = Material.ENDER_EYE,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TITLE_ENTITY),
            style = GuiNameStyle.PRIMARY,
            role = GuiElementRole.CONTENT,
            data = listOf(
                GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STATUS_MODE), modeName),
                GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STATUS_COUNT), count),
            ),
        )
        if (count > 0) {
            val actions = listOf(
                Triple(ACTION_TAG_ADD, Material.NAME_TAG, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_TAG_ADD),
                Triple(ACTION_TAG_REMOVE, Material.NAME_TAG, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_TAG_REMOVE),
                Triple(ACTION_DELETE, Material.LAVA_BUCKET, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_ENTITY_DELETE),
                Triple(ACTION_GROUP_CREATE, Material.CHEST, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_GROUP_CREATE),
            )
            actions.forEachIndexed { index, (actionId, material, labelKey) ->
                val label = KcI18n.text(player, labelKey)
                elements += KcGui.menuEntry(
                    player = player,
                    slot = ENTITY_ACTION_SLOTS[index],
                    material = material,
                    name = label,
                    style = GuiNameStyle.PRIMARY,
                    actions = listOf(GuiMenuActionIntent.AnyClick(actionId, label)),
                )
            }
        }
        elements += KcGui.elements.backEntry(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TITLE_ENTITY)), elements)
    }

    private fun showStructureDialog(player: Player) {
        val dialogId = "wand-structure-${UUID.randomUUID()}"
        pendingDialogs[player.uniqueId] = dialogId
        dialogs.show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = dialogId,
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_TITLE)),
                body = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_BODY)
                    .map { Component.text(it) },
                inputs = listOf(
                    MenuDialogInput.Text(
                        id = INPUT_NAME,
                        label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_TITLE)),
                        initial = "",
                        maxLength = 64,
                    ),
                ),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { target, response ->
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        saveStructure(target, response.textValue(INPUT_NAME).trim())
                    },
                ),
                cancel = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL),
                    MenuDialogHandler { target, _ ->
                        takeDialog(target, dialogId)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            ),
        )
    }

    private fun saveStructure(player: Player, name: String): MenuActionResult {
        if (!checkManage(player)) return MenuActionResult.Ignored
        if (!StructureStore.NAME_PATTERN.matches(name)) {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_INVALID_NAME, emptyMap(), NamedTextColor.RED)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        val session = plugin.operationTool.sessions().get(player.uniqueId)
        val bounds = session?.areaBounds()
        val first = session?.pos1
        if (bounds == null || first == null) {
            return MenuActionResult.Success(MenuUpdate.Close)
        }
        val world = plugin.server.getWorld(first.worldName) ?: run {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_FAILED, emptyMap(), NamedTextColor.RED)
            return MenuActionResult.Success(MenuUpdate.Close)
        }
        val from = Location(world, bounds.minimum.x, bounds.minimum.y, bounds.minimum.z)
        val to = Location(world, bounds.minimum.x + bounds.dx, bounds.minimum.y + bounds.dy, bounds.minimum.z + bounds.dz)
        return when (plugin.structures.save(name, from, to)) {
            StructureSaveResult.Saved -> {
                feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_SAVED, mapOf("name" to name), NamedTextColor.GREEN)
                MenuActionResult.Success(MenuUpdate.Close)
            }
            StructureSaveResult.InvalidName -> {
                feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_INVALID_NAME, emptyMap(), NamedTextColor.RED)
                MenuActionResult.Success(MenuUpdate.None)
            }
            StructureSaveResult.Failed -> {
                feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_FAILED, emptyMap(), NamedTextColor.RED)
                MenuActionResult.Success(MenuUpdate.Close)
            }
        }
    }

    private fun showTagDialog(player: Player, add: Boolean) {
        val dialogId = "wand-tag-${UUID.randomUUID()}"
        pendingDialogs[player.uniqueId] = dialogId
        dialogs.show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = dialogId,
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_TAG_TITLE)),
                body = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_TAG_BODY)
                    .map { Component.text(it) },
                inputs = listOf(
                    MenuDialogInput.Text(
                        id = INPUT_TAG,
                        label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_TAG_TITLE)),
                        initial = "",
                        maxLength = 64,
                    ),
                ),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { target, response ->
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        applyTag(target, response.textValue(INPUT_TAG).trim(), add)
                    },
                ),
                cancel = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL),
                    MenuDialogHandler { target, _ ->
                        takeDialog(target, dialogId)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            ),
        )
    }

    /**
     * 選択集合へタグ操作を適用します。
     *
     * 操作実行時に既に消失しているEntityは選択集合から外して無視し、一部のみ
     * 処理に失敗した場合は成功可能なものだけを実行します（部分適用継続）。
     */
    private fun applyTag(player: Player, tag: String, add: Boolean): MenuActionResult {
        if (!checkManage(player)) return MenuActionResult.Ignored
        if (!CommandValueRules.isTag(tag)) {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_INVALID_TAG, emptyMap(), NamedTextColor.RED)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        val session = plugin.operationTool.sessions().get(player.uniqueId) ?: return MenuActionResult.Success(MenuUpdate.Close)
        val targets = pruneVanished(player, session)
        var applied = 0
        targets.forEach { entity ->
            val done = runCatching {
                if (add) entity.addScoreboardTag(tag) else entity.removeScoreboardTag(tag)
            }.getOrDefault(false)
            // removeScoreboardTagは成否を返さないため、除去後の不在で判定します。
            val success = if (add) done && entity.scoreboardTags.contains(tag) else !entity.scoreboardTags.contains(tag)
            if (runCatching { success }.getOrDefault(false)) applied++
        }
        val key = if (add) KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_TAG_ADDED else KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_TAG_REMOVED
        feedback(player, key, mapOf("tag" to tag, "count" to applied), NamedTextColor.GREEN)
        return MenuActionResult.Success(MenuUpdate.Replace(MenuRoute(OWNER, ENTITY_MENU_ID, emptyMap())))
    }

    private fun showDeleteDialog(player: Player) {
        val session = plugin.operationTool.sessions().get(player.uniqueId) ?: return
        val count = pruneVanished(player, session).size
        if (count == 0) {
            runtime.open(player, MenuRoute(OWNER, ENTITY_MENU_ID, emptyMap()))
            return
        }
        val dialogId = "wand-delete-${UUID.randomUUID()}"
        pendingDialogs[player.uniqueId] = dialogId
        dialogs.show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = dialogId,
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_DELETE_TITLE)),
                body = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_DELETE_BODY, mapOf("count" to count))
                    .map { Component.text(it) },
                inputs = emptyList(),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { target, _ ->
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        deleteSelected(target)
                    },
                ),
                cancel = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL),
                    MenuDialogHandler { target, _ ->
                        takeDialog(target, dialogId)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            ),
        )
    }

    private fun deleteSelected(player: Player): MenuActionResult {
        if (!checkManage(player)) return MenuActionResult.Ignored
        val session = plugin.operationTool.sessions().get(player.uniqueId) ?: return MenuActionResult.Success(MenuUpdate.Close)
        val targets = pruneVanished(player, session)
        var removed = 0
        targets.forEach { entity ->
            if (runCatching { entity.remove() }.isSuccess) removed++
        }
        pruneVanished(player, session)
        feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_ENTITIES_DELETED, mapOf("count" to removed), NamedTextColor.YELLOW)
        return MenuActionResult.Success(MenuUpdate.Replace(MenuRoute(OWNER, ENTITY_MENU_ID, emptyMap())))
    }

    private fun showGroupDialog(player: Player) {
        val dialogId = "wand-group-${UUID.randomUUID()}"
        pendingDialogs[player.uniqueId] = dialogId
        dialogs.show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = dialogId,
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_GROUP_TITLE)),
                body = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_GROUP_BODY)
                    .map { Component.text(it) },
                inputs = listOf(
                    MenuDialogInput.Text(
                        id = INPUT_NAME,
                        label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_GROUP_TITLE)),
                        initial = "",
                        maxLength = 64,
                    ),
                ),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { target, response ->
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        createGroup(target, response.textValue(INPUT_NAME).trim())
                    },
                ),
                cancel = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL),
                    MenuDialogHandler { target, _ ->
                        takeDialog(target, dialogId)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            ),
        )
    }

    /**
     * 選択集合からEntity Groupを作成します。
     *
     * 初期pivotは各EntityのBounding Box中心の重心とし、消失Entity除外後に
     * 実際にグループ化するEntityから再計算します。永続化・メンバー管理は
     * 既存Entity Group domainへ委譲します。
     */
    private fun createGroup(player: Player, name: String): MenuActionResult {
        if (!checkManage(player)) return MenuActionResult.Ignored
        if (name.isBlank() || name.length > 64) {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_INVALID_GROUP_NAME, emptyMap(), NamedTextColor.RED)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        if (plugin.entityGroups.exists(name)) {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_GROUP_EXISTS, emptyMap(), NamedTextColor.RED)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        val session = plugin.operationTool.sessions().get(player.uniqueId) ?: return MenuActionResult.Success(MenuUpdate.Close)
        val targets = pruneVanished(player, session)
        if (targets.isEmpty()) return MenuActionResult.Success(MenuUpdate.Close)
        val centers = targets.map { entity ->
            val box = entity.boundingBox
            SpatialVector(
                (box.minX + box.maxX) / 2.0,
                (box.minY + box.maxY) / 2.0,
                (box.minZ + box.maxZ) / 2.0,
            )
        }
        val pivot = computePivot(centers) ?: return MenuActionResult.Success(MenuUpdate.Close)
        val record = EntityGroupStore.GroupRecord(
            id = UUID.randomUUID(),
            name = name,
            worldName = player.world.name,
            members = targets.map { EntityGroupStore.MemberRecord(it.uniqueId) },
            pivot = pivot,
            orientation = Triple(0.0, 0.0, 0.0),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        return if (runCatching { plugin.entityGroups.create(record) }.getOrDefault(false)) {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_GROUP_CREATED, mapOf("name" to name), NamedTextColor.GREEN)
            MenuActionResult.Success(MenuUpdate.Close)
        } else if (plugin.entityGroups.exists(name)) {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_GROUP_EXISTS, emptyMap(), NamedTextColor.RED)
            MenuActionResult.Success(MenuUpdate.None)
        } else {
            MenuActionResult.Success(MenuUpdate.Close)
        }
    }

    /**
     * 操作直前に消失Entityを選択集合から外し、生存Entityを返します。
     *
     * 消失は無視して成功可能なものだけを実行するため、全操作の入口で呼び出します。
     */
    private fun pruneVanished(player: Player, session: OperationToolSession): List<Entity> {
        val world = player.world
        session.retainExisting { id -> world.entities.any { it.uniqueId == id && it.isValid && !it.isDead } }
        return session.selected().mapNotNull { id ->
            world.entities.firstOrNull { it.uniqueId == id && it.isValid && !it.isDead }
        }
    }

    private fun checkManage(player: Player): Boolean =
        plugin.placementAccess.requireManage(player, player.world.name)

    /**
     * 操作結果をActionBarへ色付きで出します。
     *
     * チャットではなく現在のモード・選択数へ併記し、結果の種別で色付けします。
     */
    private fun feedback(
        player: Player,
        key: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>,
        vars: Map<String, Any>,
        color: NamedTextColor,
    ) {
        val session = plugin.operationTool.sessions().getOrCreate(player.uniqueId)
        OperationToolFeedback.notifyKey(player, session, key, vars, color)
    }

    private fun takeDialog(player: Player, dialogId: String): Boolean =
        pendingDialogs.remove(player.uniqueId) == dialogId && player.isOnline

    private fun formatPoint(point: me.awabi2048.kantancommander.model.SpatialVector): String =
        "${point.x.toInt()},${point.y.toInt()},${point.z.toInt()}"

    companion object {
        const val OWNER = "kantan"
        const val AREA_MENU_ID = "wand-area"
        const val ENTITY_MENU_ID = "wand-entity"

        private const val ACTION_SAVE_STRUCTURE = "wand-save-structure"
        private const val ACTION_TAG_ADD = "wand-tag-add"
        private const val ACTION_TAG_REMOVE = "wand-tag-remove"
        private const val ACTION_DELETE = "wand-delete"
        private const val ACTION_GROUP_CREATE = "wand-group-create"
        private const val ACTION_CLOSE = "close"

        private const val INPUT_NAME = "name"
        private const val INPUT_TAG = "tag"

        private const val ACTION_SLOT_SINGLE = 22
        private val ENTITY_ACTION_SLOTS = listOf(19, 21, 23, 25)

        /**
         * 各EntityのBounding Box中心の重心を求めます。
         *
         * pivot = (c_1 + ... + c_n) / n であり、空集合ではnullです。
         */
        fun computePivot(centers: List<SpatialVector>): SpatialVector? {
            if (centers.isEmpty()) return null
            var x = 0.0
            var y = 0.0
            var z = 0.0
            centers.forEach { x += it.x; y += it.y; z += it.z }
            val count = centers.size.toDouble()
            return SpatialVector(x / count, y / count, z / count)
        }
    }
}
