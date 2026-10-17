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
import me.awabi2048.kantancommander.data.AssetReferenceScanner
import me.awabi2048.kantancommander.data.EntityGroupStore
import me.awabi2048.kantancommander.gui.DisabledGuiVisualPolicy
import me.awabi2048.kantancommander.gui.KcGui
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.MAX_BLOCK_OPERATION_VOLUME
import me.awabi2048.kantancommander.model.RegionMath
import me.awabi2048.kantancommander.model.SpatialVector
import me.awabi2048.kantancommander.structure.StructureSaveOptions
import me.awabi2048.kantancommander.structure.StructureSaveResult
import me.awabi2048.kantancommander.structure.StructureScope
import me.awabi2048.kantancommander.structure.StructureScopes
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
        runtime.register(structuresDefinition())
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
            ACTION_STRUCTURE_LIST to MenuActionHandler { context ->
                if (!checkManage(context.player)) return@MenuActionHandler MenuActionResult.Ignored
                // 一覧は「エリアが置かれているワールドのスコープ」を見せます。
                // 未選択時は操作者の現ワールド基準です。
                val worldName = plugin.operationTool.sessions().get(context.player.uniqueId)
                    ?.pos1?.worldName ?: context.player.world.name
                MenuActionResult.Success(MenuUpdate.Navigate(structuresRoute(worldName, 0)))
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
            ACTION_GROUP_CLEANUP to MenuActionHandler { context ->
                if (!checkManage(context.player)) return@MenuActionHandler MenuActionResult.Ignored
                showGroupCleanupDialog(context.player)
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
        // 建造物一覧はエリア選択の有無にかかわらず開けます。一覧側は
        // 選択エリアのワールド（未選択時は現ワールド）のスコープを見せます。
        elements += KcGui.menuEntry(
            player = player,
            slot = STRUCTURE_LIST_SLOT,
            material = Material.BOOKSHELF,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_STRUCTURE_LIST),
            style = GuiNameStyle.PRIMARY,
            description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DESC_STRUCTURE_LIST),
            actions = listOf(GuiMenuActionIntent.AnyClick(ACTION_STRUCTURE_LIST, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_STRUCTURE_LIST))),
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
        // 未参照のEntity Groupの一括回収は選択状態と無関係のため常に出します。
        val cleanupLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_ACTION_GROUP_CLEANUP)
        elements += KcGui.menuEntry(
            player = player,
            slot = GROUP_CLEANUP_SLOT,
            material = Material.HOPPER,
            name = cleanupLabel,
            style = GuiNameStyle.PRIMARY,
            description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DESC_GROUP_CLEANUP),
            actions = listOf(GuiMenuActionIntent.AnyClick(ACTION_GROUP_CLEANUP, cleanupLabel)),
        )
        elements += KcGui.elements.backEntry(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TITLE_ENTITY)), elements)
    }

    private fun structuresDefinition(): InventoryMenuDefinition = InventoryMenuDefinition(
        owner = OWNER,
        id = STRUCTURES_MENU_ID,
        renderer = { context -> renderStructures(context.player, context.route) },
        actions = mapOf(
            ACTION_STRUCTURE_PREVIOUS to MenuActionHandler { context ->
                val page = context.route.payload[STRUCTURE_PAGE]?.toIntOrNull() ?: 0
                val worldName = context.route.payload[STRUCTURE_WORLD] ?: context.player.world.name
                MenuActionResult.Success(MenuUpdate.Replace(structuresRoute(worldName, page - 1)))
            },
            ACTION_STRUCTURE_NEXT to MenuActionHandler { context ->
                val page = context.route.payload[STRUCTURE_PAGE]?.toIntOrNull() ?: 0
                val worldName = context.route.payload[STRUCTURE_WORLD] ?: context.player.world.name
                MenuActionResult.Success(MenuUpdate.Replace(structuresRoute(worldName, page + 1)))
            },
            ACTION_STRUCTURE_DELETE to MenuActionHandler { context ->
                if (!checkManage(context.player)) return@MenuActionHandler MenuActionResult.Ignored
                val name = context.payload[STRUCTURE_NAME]
                    ?.takeIf(StructureStore.NAME_PATTERN::matches)
                    ?: return@MenuActionHandler MenuActionResult.Ignored
                val worldName = context.route.payload[STRUCTURE_WORLD] ?: context.player.world.name
                val page = context.route.payload[STRUCTURE_PAGE]?.toIntOrNull() ?: 0
                showStructureDeleteDialog(context.player, worldName, name, page)
                MenuActionResult.Success(MenuUpdate.None)
            },
        ),
    )

    /**
     * スコープ内の建造物一覧です。
     *
     * 各エントリは保存時オプション（エンティティ・空気除去）をデータ行へ示し、
     * クリックで個別削除の確認ダイアログを開きます。スコープはルート経由で
     * 固定するため、一覧を開いた後に別ワールドへ移動しても表示対象は変わりません。
     */
    private fun renderStructures(player: Player, route: MenuRoute): InventoryMenuView {
        val layout = KcGui.layouts.pagedList54()
        val worldName = route.payload[STRUCTURE_WORLD] ?: player.world.name
        val scope = StructureScopes.forWorld(worldName)
        val names = plugin.structures.list(scope)
        val total = ((names.size + layout.itemSlots.size - 1) / layout.itemSlots.size).coerceAtLeast(1)
        val page = (route.payload[STRUCTURE_PAGE]?.toIntOrNull() ?: 0).coerceIn(0, total - 1)
        val elements = mutableListOf<MenuElement>()
        val deleteLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_DELETE_TITLE)
        names.drop(page * layout.itemSlots.size).take(layout.itemSlots.size).forEachIndexed { index, name ->
            val meta = plugin.structures.metadataOf(scope, name)
            elements += KcGui.menuEntry(
                player = player,
                slot = layout.itemSlots[index],
                material = Material.STRUCTURE_BLOCK,
                name = name,
                style = GuiNameStyle.PRIMARY,
                role = GuiElementRole.CONTENT,
                data = listOf(
                    GuiMenuEntryData(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURE_OPTION_ENTITIES),
                        optionLabel(player, meta.includeEntities),
                    ),
                    GuiMenuEntryData(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURE_OPTION_REMOVE_AIR),
                        optionLabel(player, meta.removeAir),
                    ),
                ),
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DESC_STRUCTURE_ENTRY),
                actions = listOf(
                    GuiMenuActionIntent.AnyClick(
                        ACTION_STRUCTURE_DELETE,
                        deleteLabel,
                        mapOf(STRUCTURE_NAME to name),
                    ),
                ),
            )
        }
        if (names.isEmpty()) {
            elements += KcGui.menuEntry(
                player = player,
                slot = layout.itemSlots.first(),
                material = Material.PAPER,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURES_EMPTY),
                style = GuiNameStyle.MUTED,
                role = GuiElementRole.CONTENT,
            )
        }
        elements += structureNavigation(player, layout.previousPageSlot, page > 0, "<", ACTION_STRUCTURE_PREVIOUS)
        elements += structureNavigation(player, layout.nextPageSlot, page < total - 1, ">", ACTION_STRUCTURE_NEXT)
        // ページ情報と管理単位を併記します。スコープはワールド単位ではなく
        // MyWorld単位（非MyWorldはグローバル）であることを明示します。
        val scopeLabel = KcI18n.text(
            player,
            if (scope is StructureScope.MyWorld) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURE_SCOPE_MYWORLD
            } else {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURE_SCOPE_GLOBAL
            },
        )
        elements += KcGui.menuEntry(
            player = player,
            slot = layout.infoSlot,
            material = Material.BOOK,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_PAGE),
            style = GuiNameStyle.MUTED,
            role = GuiElementRole.CONTENT,
            data = listOf(
                GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_PROGRAMS_PAGE), "${page + 1}/$total"),
                GuiMenuEntryData(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURE_FIELD_SCOPE), scopeLabel),
            ),
        )
        elements += KcGui.elements.backEntry(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_TITLE_STRUCTURES)), elements)
    }

    /** 保存時オプションのON/OFF表示です。一覧のデータ行へ使います。 */
    private fun optionLabel(player: Player, enabled: Boolean): String = KcI18n.text(
        player,
        if (enabled) {
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURE_OPTION_ON
        } else {
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURE_OPTION_OFF
        },
    )

    /** ページ操作部品です。範囲外は無効部品で位置だけを維持します。 */
    private fun structureNavigation(player: Player, slot: Int, enabled: Boolean, name: String, actionId: String): MenuElement {
        return if (enabled) {
            KcGui.menuEntry(
                player = player,
                slot = slot,
                material = Material.ARROW,
                name = name,
                role = GuiElementRole.NAVIGATION,
                actions = listOf(GuiMenuActionIntent.AnyClick(actionId, name)),
            )
        } else {
            KcGui.entry(player, slot, DisabledGuiVisualPolicy.material, "", role = GuiElementRole.DECORATION)
        }
    }

    /**
     * 建造物の個別削除確認です。
     *
     * 参照中の全プログラムが次回実行時に失敗するため、確認なしの削除は行いません。
     */
    private fun showStructureDeleteDialog(player: Player, worldName: String, name: String, page: Int) {
        val dialogId = "wand-structure-delete-${UUID.randomUUID()}"
        pendingDialogs[player.uniqueId] = dialogId
        dialogs.show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = dialogId,
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_DELETE_TITLE)),
                body = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_DELETE_BODY, mapOf("name" to name))
                    .map { Component.text(it) },
                inputs = emptyList(),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { target, _ ->
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        deleteStructure(target, worldName, name, page)
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

    private fun deleteStructure(player: Player, worldName: String, name: String, page: Int): MenuActionResult {
        val scope = StructureScopes.forWorld(worldName)
        return if (plugin.structures.delete(scope, name)) {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_DELETED, mapOf("name" to name), NamedTextColor.YELLOW)
            MenuActionResult.Success(MenuUpdate.Replace(structuresRoute(worldName, page)))
        } else {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_FAILED, emptyMap(), NamedTextColor.RED)
            MenuActionResult.Success(MenuUpdate.Replace(structuresRoute(worldName, page)))
        }
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

    /**
     * 建造物保存の第1段階（名前入力）です。
     *
     * 名前確定後は、同名既存なら上書き確認、なければそのまま第2段階の
     * 保存オプション入力へ進みます。体積上限はこの時点で早期に拒否します。
     */
    private fun showStructureDialog(player: Player) {
        val session = plugin.operationTool.sessions().get(player.uniqueId)
        val bounds = session?.areaBounds()
        val first = session?.pos1
        if (bounds == null || first == null) return
        // 配置時に必ず失敗する構造物を保存済みとして残さないため、
        // 保存段階で配置と同じ体積上限を適用します。dx等は範囲（辺長-1）です。
        val volume = (bounds.dx + 1.0) * (bounds.dy + 1.0) * (bounds.dz + 1.0)
        if (volume > MAX_BLOCK_OPERATION_VOLUME) {
            feedback(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_TOO_LARGE,
                mapOf("max" to MAX_BLOCK_OPERATION_VOLUME),
                NamedTextColor.RED,
            )
            return
        }
        val worldName = first.worldName
        val scope = StructureScopes.forWorld(worldName)
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
                        if (plugin.structures.exists(scope, name)) {
                            showStructureOverwriteDialog(target, name, worldName)
                        } else {
                            showStructureOptionsDialog(target, name, worldName)
                        }
                        MenuActionResult.Success(MenuUpdate.None)
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
     * 同名建造物がスコープ内に既存の場合の上書き確認です。
     *
     * 同名での上書きは参照中の全プログラムの出力へ即座に影響するため、
     * 確認なしの上書きは行いません。確認時は第2段階へ進みます。
     */
    private fun showStructureOverwriteDialog(player: Player, name: String, worldName: String) {
        val dialogId = "wand-structure-overwrite-${UUID.randomUUID()}"
        pendingDialogs[player.uniqueId] = dialogId
        dialogs.show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = dialogId,
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_OVERWRITE_TITLE)),
                body = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_OVERWRITE_BODY, mapOf("name" to name))
                    .map { Component.text(it) },
                inputs = emptyList(),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { target, _ ->
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        showStructureOptionsDialog(target, name, worldName)
                        MenuActionResult.Success(MenuUpdate.None)
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
     * 建造物保存の第2段階（保存オプション）です。
     *
     * 「エンティティを含める」「空気を取り除く」の2トグルを選ばせます。
     * 既定はどちらもOFF（従来どおりブロックのみ・空気も上書き）です。
     */
    private fun showStructureOptionsDialog(player: Player, name: String, worldName: String) {
        val dialogId = "wand-structure-options-${UUID.randomUUID()}"
        pendingDialogs[player.uniqueId] = dialogId
        dialogs.show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = dialogId,
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_OPTIONS_TITLE)),
                body = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_STRUCTURE_OPTIONS_BODY)
                    .map { Component.text(it) },
                inputs = listOf(
                    MenuDialogInput.BooleanInput(
                        id = INPUT_INCLUDE_ENTITIES,
                        label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURE_OPTION_ENTITIES)),
                        initial = false,
                    ),
                    MenuDialogInput.BooleanInput(
                        id = INPUT_REMOVE_AIR,
                        label = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_STRUCTURE_OPTION_REMOVE_AIR)),
                        initial = false,
                    ),
                ),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { target, response ->
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        saveStructure(
                            target,
                            name,
                            worldName,
                            StructureSaveOptions(
                                includeEntities = response.booleanValue(INPUT_INCLUDE_ENTITIES),
                                removeAir = response.booleanValue(INPUT_REMOVE_AIR),
                            ),
                        )
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
     * 選択エリアを建造物として保存します。
     *
     * スコープは保存対象エリアのワールドから解決し、同スコープの同名は
     * 呼び出し側の上書き確認を経て置き換えます。
     */
    private fun saveStructure(player: Player, name: String, worldName: String, options: StructureSaveOptions): MenuActionResult {
        val session = plugin.operationTool.sessions().get(player.uniqueId)
        val bounds = session?.areaBounds()
        val first = session?.pos1
        if (bounds == null || first == null || first.worldName != worldName) {
            return MenuActionResult.Success(MenuUpdate.Close)
        }
        // 権限は現在ワールドではなく保存対象エリアのワールドで判定します。
        if (!plugin.placementAccess.requireManage(player, first.worldName)) return MenuActionResult.Ignored
        val world = plugin.server.getWorld(first.worldName) ?: run {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_FAILED, emptyMap(), NamedTextColor.RED)
            return MenuActionResult.Success(MenuUpdate.Close)
        }
        val scope = StructureScopes.forWorld(first.worldName)
        val from = Location(world, bounds.minimum.x, bounds.minimum.y, bounds.minimum.z)
        val to = Location(world, bounds.minimum.x + bounds.dx, bounds.minimum.y + bounds.dy, bounds.minimum.z + bounds.dz)
        // Qキーで指定された原点は、領域内のブロックの場合だけ保存へ引き渡します。
        val originLocation = session.origin
            ?.takeIf { origin ->
                origin.worldName == first.worldName && RegionMath.contains(
                    origin.position, bounds.minimum, bounds.dx, bounds.dy, bounds.dz,
                )
            }
            ?.let { Location(world, it.position.x, it.position.y, it.position.z) }
        return when (plugin.structures.save(scope, name, from, to, originLocation, options)) {
            StructureSaveResult.Saved -> {
                feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_SAVED, mapOf("name" to name), NamedTextColor.GREEN)
                MenuActionResult.Success(MenuUpdate.Close)
            }
            StructureSaveResult.InvalidName -> {
                feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_INVALID_NAME, emptyMap(), NamedTextColor.RED)
                MenuActionResult.Success(MenuUpdate.None)
            }
            StructureSaveResult.TooLarge -> {
                feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_STRUCTURE_TOO_LARGE, mapOf("max" to MAX_BLOCK_OPERATION_VOLUME), NamedTextColor.RED)
                MenuActionResult.Success(MenuUpdate.Close)
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
     * 未参照Entity Groupの一括回収確認です。
     *
     * script が参照しなくなった群だけを対象とし、実行中・編集中への自動GCは
     * 行わない設計のため、明示操作として確認へ接続します。
     */
    private fun showGroupCleanupDialog(player: Player) {
        val dialogId = "wand-group-cleanup-${UUID.randomUUID()}"
        pendingDialogs[player.uniqueId] = dialogId
        dialogs.show(
            player,
            MenuDialogRequest(
                owner = OWNER,
                id = dialogId,
                title = Component.text(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_GROUP_CLEANUP_TITLE)),
                body = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_WAND_DIALOG_GROUP_CLEANUP_BODY)
                    .map { Component.text(it) },
                inputs = emptyList(),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { target, _ ->
                        if (!takeDialog(target, dialogId)) return@MenuDialogHandler MenuActionResult.Ignored
                        cleanupGroups(target)
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
     * script から参照されないEntity Groupを一括削除します。
     *
     * 参照集合は保存済み全scriptのコマンド設定から走査します。スコープを
     * 持たないEntity Groupは全スコープ共通のため、参照走査も全体で行います。
     */
    private fun cleanupGroups(player: Player): MenuActionResult {
        if (!checkManage(player)) return MenuActionResult.Ignored
        val referenced = AssetReferenceScanner.groupNames(plugin.scripts.listAll())
        val removed = runCatching { plugin.entityGroups.deleteUnreferenced(referenced) }.getOrNull()
        return if (removed != null) {
            feedback(
                player,
                KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_GROUPS_DELETED,
                mapOf("count" to removed.size),
                NamedTextColor.YELLOW,
            )
            MenuActionResult.Success(MenuUpdate.Replace(MenuRoute(OWNER, ENTITY_MENU_ID, emptyMap())))
        } else {
            feedback(player, KcKeys.KANTAN_COMMANDER_CLEAN_MESSAGE_WAND_GROUP_FAILED, emptyMap(), NamedTextColor.RED)
            MenuActionResult.Success(MenuUpdate.None)
        }
    }

    /** 建造物一覧のルートです。表示対象スコープはワールド名で固定します。 */
    private fun structuresRoute(worldName: String, page: Int): MenuRoute = MenuRoute(
        OWNER,
        STRUCTURES_MENU_ID,
        mapOf(
            STRUCTURE_WORLD to worldName,
            STRUCTURE_PAGE to page.coerceAtLeast(0).toString(),
        ),
    )

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
        const val STRUCTURES_MENU_ID = "wand-structures"

        private const val ACTION_SAVE_STRUCTURE = "wand-save-structure"
        private const val ACTION_STRUCTURE_LIST = "wand-structure-list"
        private const val ACTION_STRUCTURE_DELETE = "wand-structure-delete"
        private const val ACTION_STRUCTURE_PREVIOUS = "wand-structure-previous"
        private const val ACTION_STRUCTURE_NEXT = "wand-structure-next"
        private const val ACTION_TAG_ADD = "wand-tag-add"
        private const val ACTION_TAG_REMOVE = "wand-tag-remove"
        private const val ACTION_DELETE = "wand-delete"
        private const val ACTION_GROUP_CREATE = "wand-group-create"
        private const val ACTION_GROUP_CLEANUP = "wand-group-cleanup"

        private const val INPUT_NAME = "name"
        private const val INPUT_TAG = "tag"
        private const val INPUT_INCLUDE_ENTITIES = "include_entities"
        private const val INPUT_REMOVE_AIR = "remove_air"

        private const val STRUCTURE_WORLD = "world"
        private const val STRUCTURE_PAGE = "page"
        private const val STRUCTURE_NAME = "name"

        private const val ACTION_SLOT_SINGLE = 22
        private val ENTITY_ACTION_SLOTS = listOf(19, 21, 23, 25)

        /** 建造物一覧の入口です。45スロット画面の操作列の中央左です。 */
        private const val STRUCTURE_LIST_SLOT = 20

        /** 未参照Entity Groupの回収入口です。操作列の右端です。 */
        private const val GROUP_CLEANUP_SLOT = 28

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
