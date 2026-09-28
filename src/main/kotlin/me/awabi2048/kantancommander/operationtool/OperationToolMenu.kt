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
import me.awabi2048.kantancommander.model.RegionMath
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

    /**
     * エリア操作GUIを開きます。
     *
     * 左クリック＝メニュー起動の操作体系に合わせ、エリア未選択でも開き、
     * 現在状態だけを表示します。権限は選択エリアのあるワールドで判定します。
     */
    fun openArea(player: Player) {
        val session = plugin.operationTool.sessions().getOrCreate(player.uniqueId)
        val worldName = session.pos1?.worldName ?: player.world.name
        if (!plugin.placementAccess.requireManage(player, worldName)) return
        runtime.open(player, MenuRoute(OWNER, AREA_MENU_ID, emptyMap()))
    }

    /**
     * Entity操作GUIを開きます。
     *
     * 左クリック＝メニュー起動の操作体系に合わせ、選択が空でも開き、
     * 現在状態だけを表示します。
     */
    fun openEntity(player: Player) {
        val session = plugin.operationTool.sessions().getOrCreate(player.uniqueId)
        resolveTargets(player, session)
        if (!plugin.placementAccess.requireManage(player, player.world.name)) return
        runtime.open(player, MenuRoute(OWNER, ENTITY_MENU_ID, emptyMap()))
    }

    /**
     * 指定Entityのタグ一覧をチャットへ表示します。
     *
     * 選択が済んでいない状態でEntityを左クリックした際の導線です。
     * 参照専用のため画面は開かず、タグは1行へ列挙します。
     */
    fun sendEntityTags(player: Player, target: Entity) {
        if (!plugin.placementAccess.requireManage(player, player.world.name)) return
        val tags = target.scoreboardTags.sorted()
        val tagsText = if (tags.isEmpty()) {
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TAGS_NONE)
        } else {
            tags.joinToString(", ")
        }
        val name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(target.name())
        player.sendMessage(
            Component.text(
                KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_ENTITY_TAGS,
                    mapOf("name" to name, "tags" to tagsText),
                ),
                NamedTextColor.GRAY,
            ),
        )
    }

    /** 応答待ちダイアログのトークンを破棄します。ログアウト・キック時に呼び出します。 */
    fun clearPendingDialog(playerId: UUID) {
        pendingDialogs.remove(playerId)
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
        ),
    )

    private fun renderArea(player: Player): InventoryMenuView {
        val layout = KcGui.layouts.settings45()
        val session = plugin.operationTool.sessions().get(player.uniqueId)
        val elements = mutableListOf<MenuElement>()
        val bounds = session?.areaBounds()
        // ワールド名は通知・画面ともに省略し、座標だけを示します。
        val areaText = if (session != null && bounds != null) {
            val first = requireNotNull(session.pos1)
            val second = requireNotNull(session.pos2)
            "${formatPoint(first.position)} → ${formatPoint(second.position)}"
        } else {
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        }
        // Qキーで指定した構造物原点を状態として併記します。
        val originText = session?.origin
            ?.takeIf { it.worldName == session.pos1?.worldName }
            ?.let { formatPoint(it.position) }
            ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
        elements += KcGui.menuEntry(
            player = player,
            slot = layout.infoSlot,
            material = Material.MAP,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TITLE_AREA),
            style = GuiNameStyle.PRIMARY,
            role = GuiElementRole.CONTENT,
            data = listOf(
                GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STATUS_AREA), areaText),
                GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STATUS_ORIGIN), originText),
            ),
        )
        if (session != null && session.hasValidArea()) {
            elements += KcGui.menuEntry(
                player = player,
                slot = ACTION_SLOT_SINGLE,
                material = Material.STRUCTURE_BLOCK,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_SAVE_STRUCTURE),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DESC_SAVE_STRUCTURE),
                actions = listOf(GuiMenuActionIntent.AnyClick(ACTION_SAVE_STRUCTURE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_SAVE_STRUCTURE))),
            )
        }
        elements += KcGui.elements.backEntry(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TITLE_AREA)), elements)
    }

    private fun renderEntity(player: Player): InventoryMenuView {
        val layout = KcGui.layouts.settings45()
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
        // タグ一覧は専用スロットへ分離し、状態表示と描画責務を分けます。
        elements += KcGui.menuEntry(
            player = player,
            slot = TAG_LIST_SLOT,
            material = Material.BOOK,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STATUS_TAGS),
            style = GuiNameStyle.DEFAULT,
            role = GuiElementRole.CONTENT,
            data = tagDataRows(player, session),
        )
        if (count > 0) {
            val actions = listOf(
                EntityAction(ACTION_TAG_ADD, Material.NAME_TAG,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_TAG_ADD,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DESC_TAG_ADD),
                EntityAction(ACTION_TAG_REMOVE, Material.SHEARS,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_TAG_REMOVE,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DESC_TAG_REMOVE),
                EntityAction(ACTION_DELETE, Material.LAVA_BUCKET,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_ENTITY_DELETE,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DESC_ENTITY_DELETE),
                EntityAction(ACTION_GROUP_CREATE, Material.CHEST,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_GROUP_CREATE,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DESC_GROUP_CREATE),
            )
            actions.forEachIndexed { index, action ->
                val label = KcI18n.text(player, action.labelKey)
                elements += KcGui.menuEntry(
                    player = player,
                    slot = ENTITY_ACTION_SLOTS[index],
                    material = action.material,
                    name = label,
                    style = GuiNameStyle.PRIMARY,
                    description = KcI18n.list(player, action.descriptionKey),
                    actions = listOf(GuiMenuActionIntent.AnyClick(action.actionId, label)),
                )
            }
        }
        elements += KcGui.elements.backEntry(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TITLE_ENTITY)), elements)
    }

    /**
     * 選択中Entityへ付与されているタグの一覧行です。
     *
     * タグ除去の入力ダイアログからも見えるよう、タグごとの付与数を
     * データ行として示します。行が多い場合は末尾を「他{count}件」で畳みます。
     */
    private fun tagDataRows(player: Player, session: OperationToolSession?): List<GuiMenuEntryData> {
        val tagCounts = selectedTagCounts(session)
        if (tagCounts.isEmpty()) {
            return listOf(
                GuiMenuEntryData(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TAGS_NONE),
                    null,
                ),
            )
        }
        val rows = mutableListOf<GuiMenuEntryData>()
        tagCounts.entries.take(TAG_ROW_LIMIT).forEach { (tag, tagged) ->
            rows += GuiMenuEntryData(tag, tagged)
        }
        val rest = tagCounts.size - TAG_ROW_LIMIT
        if (rest > 0) {
            rows += GuiMenuEntryData(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TAGS_MORE, mapOf("count" to rest)),
                null,
            )
        }
        return rows
    }

    /** 選択中Entityへ付与されているタグの名前→件数です。ロード済みのEntityだけを数えます。 */
    private fun selectedTagCounts(session: OperationToolSession?): Map<String, Int> {
        if (session == null) return emptyMap()
        return session.selected().asSequence()
            .mapNotNull { plugin.server.getEntity(it) }
            .filter { it.isValid && !it.isDead }
            .flatMap { it.scoreboardTags }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
    }

    /** 選択中Entityへ付与されているタグ名の一覧です。ダイアログ本文の現行表示に使います。 */
    private fun selectedTagNames(session: OperationToolSession?): List<String> =
        selectedTagCounts(session).keys.toList()

    private data class EntityAction(
        val actionId: String,
        val material: Material,
        val labelKey: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>,
        val descriptionKey: com.awabi2048.ccsystem.api.localization.LocalizationKey<List<String>>,
    )

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
                        val name = response.textValue(INPUT_NAME).trim()
                        if (!StructureStore.NAME_PATTERN.matches(name)) {
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                validationComponent(target, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_INVALID_NAME),
                            )
                        }
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        saveStructure(target, name)
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
        val session = plugin.operationTool.sessions().get(player.uniqueId)
        val bounds = session?.areaBounds()
        val first = session?.pos1
        if (bounds == null || first == null) {
            return MenuActionResult.Success(MenuUpdate.Close)
        }
        // 権限は現在ワールドではなく保存対象エリアのワールドで判定します。
        if (!plugin.placementAccess.requireManage(player, first.worldName)) return MenuActionResult.Ignored
        val world = plugin.server.getWorld(first.worldName) ?: run {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_FAILED, emptyMap(), NamedTextColor.RED)
            return MenuActionResult.Success(MenuUpdate.Close)
        }
        val from = Location(world, bounds.minimum.x, bounds.minimum.y, bounds.minimum.z)
        val to = Location(world, bounds.minimum.x + bounds.dx, bounds.minimum.y + bounds.dy, bounds.minimum.z + bounds.dz)
        // Qキーで指定された原点は、領域内のブロックの場合だけ保存へ引き渡します。
        val originLocation = session?.origin
            ?.takeIf { origin ->
                origin.worldName == first.worldName && RegionMath.contains(
                    origin.position, bounds.minimum, bounds.dx, bounds.dy, bounds.dz,
                )
            }
            ?.let { Location(world, it.position.x, it.position.y, it.position.z) }
        return when (plugin.structures.save(name, from, to, originLocation)) {
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
        // 除去対象を選べるよう、現在付与されているタグ一覧を本文へ添えます。
        val session = plugin.operationTool.sessions().get(player.uniqueId)
        val tags = selectedTagNames(session)
        val tagsText = if (tags.isEmpty()) {
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TAGS_NONE)
        } else {
            tags.joinToString(", ")
        }
        val body = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_TAG_BODY)
            .map { Component.text(it) } +
            Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_TAG_CURRENT, mapOf("tags" to tagsText)))
        dialogs.show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = dialogId,
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_TAG_TITLE)),
                body = body,
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
                        val tag = response.textValue(INPUT_TAG).trim()
                        if (!CommandValueRules.isTag(tag)) {
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                validationComponent(target, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_INVALID_TAG),
                            )
                        }
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        applyTag(target, tag, add)
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
        val session = plugin.operationTool.sessions().get(player.uniqueId) ?: return MenuActionResult.Success(MenuUpdate.Close)
        val targets = resolveTargets(player, session)
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
        val count = resolveTargets(player, session).size
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
        val targets = resolveTargets(player, session)
        var removed = 0
        targets.forEach { entity ->
            if (runCatching { entity.remove() }.isSuccess) removed++
        }
        resolveTargets(player, session)
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
                        val name = response.textValue(INPUT_NAME).trim()
                        val invalid = when {
                            name.isBlank() || name.length > 64 ->
                                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_INVALID_GROUP_NAME
                            plugin.entityGroups.exists(name) ->
                                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_GROUP_EXISTS
                            else -> null
                        }
                        if (invalid != null) {
                            return@MenuDialogHandler MenuActionResult.Rejected(validationComponent(target, invalid))
                        }
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        createGroup(target, name)
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
        val session = plugin.operationTool.sessions().get(player.uniqueId) ?: return MenuActionResult.Success(MenuUpdate.Close)
        val targets = resolveTargets(player, session)
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
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_GROUP_FAILED, emptyMap(), NamedTextColor.RED)
            MenuActionResult.Success(MenuUpdate.None)
        }
    }

    /**
     * 選択集合のうち現在ワールドで操作可能なEntityを返します。
     *
     * ロード済みで死亡・無効が確認できたEntityのみ選択集合から外し、未ロードや
     * 別ワールドで解決できないEntityは選択へ残します。消失は無視して成功可能な
     * ものだけを実行するため、全操作の入口で呼び出します。
     */
    private fun resolveTargets(player: Player, session: OperationToolSession): List<Entity> {
        session.retainExisting { id ->
            val entity = plugin.server.getEntity(id) ?: return@retainExisting true
            entity.isValid && !entity.isDead
        }
        // 消失判定で縮んだ選択に発光表示を追従させます。
        plugin.operationTool.syncGlow(player)
        return session.selected().mapNotNull { plugin.server.getEntity(it) }
            .filter { it.isValid && !it.isDead && it.world.uid == player.world.uid }
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

    /**
     * ダイアログ入力の検証失敗文言です。
     *
     * [MenuActionResult.Rejected] へ渡すと、入力値を初期値として引き継いだ
     * 同一ダイアログが再表示され、本文末尾へこの文言が添えられます。
     */
    private fun validationComponent(
        player: Player,
        key: com.awabi2048.ccsystem.api.localization.LocalizationKey<String>,
    ): Component = Component.text(KcI18n.text(player, key), NamedTextColor.RED)

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

        private const val INPUT_NAME = "name"
        private const val INPUT_TAG = "tag"

        private const val ACTION_SLOT_SINGLE = 22
        private val ENTITY_ACTION_SLOTS = listOf(19, 21, 23, 25)

        /** タグ一覧を描画する専用スロットです。45スロット画面の3行目・操作列の中央です。 */
        private const val TAG_LIST_SLOT = 20

        /** タグ一覧データ行の表示上限です。超過分は「他{count}件」で畳みます。 */
        private const val TAG_ROW_LIMIT = 8

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
