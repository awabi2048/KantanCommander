package me.awabi2048.kantancommander.gui
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiLoreLine
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiValueTone
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuDialogButton
import com.awabi2048.ccsystem.api.gui.MenuDialogHandler
import com.awabi2048.ccsystem.api.gui.MenuDialogInput
import com.awabi2048.ccsystem.api.gui.MenuDialogRequest
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TargetSort
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.ContextSource
import me.awabi2048.kantancommander.model.effectiveContextSource
import me.awabi2048.kantancommander.item.ItemStackCodec
import me.awabi2048.kantancommander.util.KcI18n
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
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
                    "category" to MenuActionHandler { context ->
                        val category = context.payload["category"]
                            ?.let { value -> CommandCategory.entries.firstOrNull { it.routeValue == value } }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        MenuActionResult.Success(
                            MenuUpdate.Replace(context.route.copy(payload = context.route.payload + (PICKER_CATEGORY to category.routeValue)))
                        )
                    },
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
                        // 表示後に別画面でグラフが更新されると、表示時には有効だった
                        // 合流候補が無効になることがあります。ジェスチャーGUIと同じ
                        // 検証を実行境界でも行い、IllegalArgumentExceptionをイベントへ
                        // 漏らさず安全に操作を無視します。
                        if (type == CommandType.MERGE && !GraphEditor.canAppendMerge(script.graph, mergeConditionId)) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        // 挿入処理は共通グラフ更新入口へ通します。表示中のscriptを
                        // 直接変更せず、レイアウト検証・保存・配置表示更新を両GUIで同じ
                        // 順序にすることで、挿入経路だけ別の正本状態を作りません。
                        val node = runCatching {
                            CommandSettingsModel.updateGraph(plugin, script.id, context.player.uniqueId) { candidateGraph ->
                                if (type == CommandType.MERGE) {
                                    if (!GraphEditor.canAppendMerge(candidateGraph, mergeConditionId)) {
                                        null
                                    } else {
                                        GraphEditor.appendMerge(candidateGraph, requireNotNull(mergeConditionId))
                                    }
                                } else {
                                    GraphEditor.insert(candidateGraph, sourceId, edge, type)
                                }
                            }
                        }.getOrElse { failure ->
                            plugin.logger.log(
                                java.util.logging.Level.WARNING,
                                "コマンド挿入を保存できませんでした: script=${script.id} type=$type",
                                failure,
                            )
                            return@MenuActionHandler MenuActionResult.Rejected(
                                Component.text("コマンドを追加できませんでした。経路を確認してください。"),
                            )
                        }
                        if (node == null) return@MenuActionHandler MenuActionResult.Ignored
                        // 追加完了を通常のクリック音と区別できるよう、保存成功後だけ
                        // 成功音を鳴らします。保存失敗時に成功音を先に鳴らしません。
                        context.player.playSound(
                            context.player.location,
                            Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                            1.0f,
                            2.0f,
                        )
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
                        val routeRole = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                        val current = selectedTargetSpec(context.route)
                        val category = context.payload["category"]
                            ?.let { runCatching { TargetCategory.valueOf(it) }.getOrNull() }
                            ?: context.payload["kind"]
                                ?.let { runCatching { TargetKind.valueOf(it) }.getOrNull() }
                                ?.let(CommandSettingsModel::targetCategory)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val script = script(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        val node = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!CommandSettingsModel.targetCategoryAvailable(script.graph, node.id, category)) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        val currentKind = current?.kind
                        val kind = if (CommandSettingsModel.targetCategoryMatches(currentKind, category)) {
                            currentKind ?: CommandSettingsModel.defaultTargetKind(category)
                        } else {
                            CommandSettingsModel.defaultTargetKind(category)
                        }
                        val fixedEntityId = if (kind == TargetKind.FIXED_ENTITY) {
                            context.player.getTargetEntity(32)?.uniqueId
                                ?: return@MenuActionHandler MenuActionResult.Ignored
                        } else null
                        if (!updateNode(context.player, context.route) { node ->
                            // 種類の再選択は設定値確認の再訪を兼ねるため、既存の絞り込み条件を
                            // 引き継ぐ。プレイヤー系⇔エンティティ系の切り替え時だけ、適用対象外と
                            // なるentityTypeを初期化する。
                            val role = routeRole
                            val current = CommandSettingsModel.targetSpec(node, role)
                            val entityKind = kind == TargetKind.NEAREST_ENTITY || kind == TargetKind.NEARBY_ENTITIES
                            val spec = (current ?: TargetSpec(kind)).copy(
                                kind = kind,
                                fixedEntityId = fixedEntityId,
                                entityType = if (entityKind) current?.entityType else null,
                            )
                            CommandSettingsModel.setTargetSpec(
                                node,
                                role,
                                spec,
                            )
                        }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
                        if (CommandSettingsModel.targetSupportsDetailedFilters(kind)) {
                            MenuActionResult.Success(MenuUpdate.Replace(choiceRoute(context.route, TARGET_FILTER_ID)))
                        } else {
                            MenuActionResult.Success(MenuUpdate.Back)
                        }
                    },
                    // 既に設定済みの種類を再選択せず、絞り込み条件だけを確認・編集できる
                    // 短路経路です（設定値が「初期化される」誤解を防ぎます）。
                    "filter" to MenuActionHandler { context ->
                        if (selectedTargetSpec(context.route) == null) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        MenuActionResult.Success(MenuUpdate.Replace(choiceRoute(context.route, TARGET_FILTER_ID)))
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
                    "kind" to MenuActionHandler { context ->
                        val current = selectedTargetSpec(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        val category = CommandSettingsModel.targetCategory(current.kind)
                        val kinds = CommandSettingsModel.targetKinds(category)
                        if (kinds.isEmpty()) return@MenuActionHandler MenuActionResult.Ignored
                        val currentIndex = kinds.indexOf(current.kind).coerceAtLeast(-1)
                        val nextKind = kinds[(currentIndex + 1) % kinds.size]
                        val fixedId = if (nextKind == TargetKind.FIXED_ENTITY) {
                            context.player.getTargetEntity(32)?.uniqueId
                                ?: return@MenuActionHandler MenuActionResult.Ignored
                        } else null
                        if (!updateTargetSpec(context.player, context.route) { spec ->
                            spec.copy(
                                kind = nextKind,
                                fixedEntityId = fixedId,
                                entityType = if (nextKind in setOf(TargetKind.NEAREST_ENTITY, TargetKind.NEARBY_ENTITIES)) spec.entityType else null,
                            )
                        }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "sort" to MenuActionHandler { context ->
                        if (!updateTargetSpec(context.player, context.route) { spec ->
                            spec.copy(sort = TargetSort.entries[(spec.sort.ordinal + 1) % TargetSort.entries.size])
                        }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "gameMode" to MenuActionHandler { context ->
                        val modes = listOf(null, "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")
                        if (!updateTargetSpec(context.player, context.route) { spec ->
                            val next = (modes.indexOf(spec.gameMode) + 1).coerceAtLeast(0) % modes.size
                            spec.copy(gameMode = modes[next])
                        }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "entityType" to targetFilterDialog("entityType"),
                    "distance" to targetFilterDialog("distance"),
                    "range" to targetFilterDialog("range"),
                    "limit" to targetFilterDialog("limit"),
                    "tag" to targetFilterDialog("tag"),
                    "name" to targetFilterDialog("name"),
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
                    val currentKind = node(context.route)?.let { CommandSettingsModel.positionKind(it, CommandSettingRole.fromRoute(context.route.payload[ROLE])) }
                    if (kind == PositionKind.COORDINATES) {
                        // 座標は「選択」と「入力」を別操作にします。未選択からの
                        // クリックでは座標方式だけを確定し、次のクリックでDialogを開きます。
                        if (currentKind != PositionKind.COORDINATES) {
                            val location = context.player.location
                            if (!updateNode(context.player, context.route) { node ->
                                CommandSettingsModel.setPositionSpec(
                                    node,
                                    CommandSettingRole.fromRoute(context.route.payload[ROLE]),
                                    PositionSpec(PositionKind.COORDINATES, location.x, location.y, location.z),
                                )
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Replace(context.route))
                        }
                        showPositionDialog(context.player, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val location = context.player.location
                        val spec = if (kind == PositionKind.CAPTURED) {
                            PositionSpec(kind, location.x, location.y, location.z, location.yaw, location.pitch)
                        } else PositionSpec(kind)
                        if (!updateNode(context.player, context.route) { node ->
                            CommandSettingsModel.setPositionSpec(
                                node,
                                CommandSettingRole.fromRoute(context.route.payload[ROLE]),
                                spec,
                            )
                        }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
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
                        if (!updateNode(context.player, context.route) { node ->
                            CommandSettingsModel.setFacingSpec(
                                node,
                                spec,
                                CommandSettingRole.fromRoute(context.route.payload[ROLE]) ?: CommandSettingRole.CONTEXT_FACING,
                            )
                        }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
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
                        if (field == "item" && (node.type == CommandType.GIVE_ITEM ||
                                (node.type == CommandType.ENTITY_ACTION && node.string("action", "ride") == "equip"))) {
                            // 付与アイテムはインベントリ内の実物をクリックして選択する
                            // 旧方式へ戻します（インベントリGUIの標準選択導線）。
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
                        if (field == "operation" && node.type == CommandType.BLOCK_OPERATION) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, BLOCK_OPERATION_ID))
                            )
                        }
                        if (field == "block" && node.type == CommandType.BLOCK_OPERATION) {
                            return@MenuActionHandler setHeldBlock(context)
                        }
                        if (field == "inverted" && node.type == CommandType.CONDITION) {
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "inverted", (!it.boolean("inverted")).toString())
                            }) {
                                return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field.endsWith("Source") && node.type == CommandType.FOR_START) {
                            // FORの入力元は固定値とワールド変数だけです。実行セッションの
                            // 一時変数を再導入すると、VARIABLEの保存先統一と矛盾するため、
                            // 選択肢の循環もこの2択に限定します。
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(
                                    it,
                                    field,
                                    when (it.string(field, "FIXED")) {
                                        "WORLD" -> "FIXED"
                                        else -> "WORLD"
                                    },
                                )
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "inclusiveEnd" && node.type == CommandType.FOR_START) {
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "inclusiveEnd", (!it.boolean("inclusiveEnd", true)).toString())
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
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
                        if (field == "changeMode" && node.type == CommandType.VARIABLE) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, VARIABLE_CHANGE_MODE_ID))
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
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(
                                    it,
                                    "action",
                                    when (it.string("action", "ride")) {
                                        "ride" -> "dismount"
                                        "dismount" -> "equip"
                                        "equip" -> "tag"
                                        else -> "ride"
                                    },
                                )
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "slot" && node.type == CommandType.ENTITY_ACTION && node.string("action", "ride") == "equip") {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, ENTITY_EQUIPMENT_SLOT_ID)),
                            )
                        }
                        if (field == "overwrite" && node.type == CommandType.ENTITY_ACTION) {
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "overwrite", (!it.boolean("overwrite")).toString())
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "tagOperation" && node.type == CommandType.ENTITY_ACTION) {
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(
                                    it,
                                    "tagOperation",
                                    if (it.string("tagOperation", "add") == "add") "remove" else "add",
                                )
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "soundScope" && node.type == CommandType.PLAY_SOUND) {
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(
                                    it,
                                    "soundScope",
                                    if (it.string("soundScope", "CONTEXT") == "CONTEXT") "WORLD" else "CONTEXT",
                                )
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "shakeType" && node.type == CommandType.CAMERA_SHAKE) {
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(
                                    it,
                                    "shakeType",
                                    if (it.string("shakeType", "positional") == "positional") "rotational" else "positional",
                                )
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "contextSource") {
                            if (!updateNode(context.player, context.route) { CommandSettingsModel.toggleContextSource(it) }) {
                                return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field in setOf(
                                "count", "seconds", "text", "subtitle", "customName", "itemData", "value",
                                "startValue", "endValue", "stepValue", "entity", "tags", "tag", "sound",
                                "soundParameters", "effect", "level", "intensity",
                            )) {
                            showFieldDialog(context.player, context.route, field, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val target = when {
                            field == "destination" -> positionRoute(context.route, "destination")
                            field == "destinationFacing" -> facingRoute(context.route, "destination_facing")
                            field == "executor" -> targetRoute(context.route, "context_executor")
                            field == "target" && node.type == CommandType.CONTEXT -> targetRoute(context.route, "context_target")
                            field == "target" || field == "subject" -> targetRoute(context.route, "node_target")
                            field == "other" && node.type == CommandType.ENTITY_ACTION ->
                                targetRoute(context.route, "secondary_target")
                            field == "position" && node.type == CommandType.BLOCK_OPERATION ->
                                positionRoute(context.route, "block_position")
                            field == "from" && node.type == CommandType.BLOCK_OPERATION ->
                                positionRoute(context.route, "block_from")
                            field == "to" && node.type == CommandType.BLOCK_OPERATION ->
                                positionRoute(context.route, "block_to")
                            field == "soundPosition" -> positionRoute(context.route, "sound_position")
                            field == "position" -> positionRoute(context.route, "context_position")
                            field == "facing" -> facingRoute(context.route, "context_facing")
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
                        if (!updateNode(context.player, context.route) {
                            val next = when (it.string("sneaking")) {
                                "" -> "true"
                                "true" -> "false"
                                else -> ""
                            }
                            CommandSettingsModel.setParameter(it, "sneaking", next)
                        }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "variable" to MenuActionHandler { context ->
                        showStringParameterDialog(
                            context.player,
                            context.route,
                            "variable",
                            CommandDialogSpecs.variableName,
                        )
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "operator" to MenuActionHandler { context ->
                        val operators = listOf("==", "!=", ">", ">=", "<", "<=")
                        if (!updateNode(context.player, context.route) {
                            val current = operators.indexOf(it.string("operator", "==")).coerceAtLeast(0)
                            CommandSettingsModel.setParameter(it, "operator", operators[(current + 1) % operators.size])
                        }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "value" to MenuActionHandler { context ->
                        showStringParameterDialog(
                            context.player,
                            context.route,
                            "value",
                            CommandDialogSpecs.conditionValue,
                        )
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "position" to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(positionRoute(context.route, "condition_position"))
                        )
                    },
                    "block" to MenuActionHandler { context -> setHeldBlock(context) },
                    // 条件のアイテムもインベントリ内クリック選択へ統一します。
                    "item" to materialSelection("item"),
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
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "kind", kind.name)
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
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
                        MenuActionResult.Success(MenuUpdate.Navigate(facingRoute(context.route, "context_facing")))
                    },
                    "source" to MenuActionHandler { context ->
                        if (!updateNode(context.player, context.route) { CommandSettingsModel.toggleContextSource(it) }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "inherit" to MenuActionHandler { context ->
                        if (!updateNode(context.player, context.route) { CommandSettingsModel.clearContextOverride(it) }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
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
                            ?.takeIf { it in setOf("tellraw", "title", "subtitle", "actionbar") }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "mode", mode)
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                BLOCK_OPERATION_ID,
                renderer = { renderBlockOperations(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val operation = context.payload["operation"]
                            ?.takeIf { it == "setblock" || it == "fill" }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "operation", operation)
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                ENTITY_EQUIPMENT_SLOT_ID,
                renderer = { renderEquipmentSlots(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val slot = context.payload["slot"]
                            ?.takeIf { it in setOf("HAND", "OFF_HAND", "HEAD", "CHEST", "LEGS", "FEET") }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "slot", slot)
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
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
                        if (!updateNode(context.player, context.route) {
                            CommandSettingsModel.setParameter(it, "type", type.name)
                            val current = runCatching {
                                VariableOperation.valueOf(it.string("operation"))
                            }.getOrNull()
                            if (current !in allowedVariableOperations(type)) {
                                CommandSettingsModel.setParameter(
                                    it,
                                    "operation",
                                    allowedVariableOperations(type).first().name,
                                )
                            }
                        }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
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
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "operation", operation.name)
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                VARIABLE_CHANGE_MODE_ID,
                renderer = { renderVariableChangeModes(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val mode = context.payload["changeMode"]
                            ?.let { runCatching { VariableChangeMode.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "changeMode", mode.name)
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
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
                        val updated = runCatching {
                            CommandSettingsModel.updateTimer(
                                plugin,
                                script.id,
                                enabled = false,
                                editorId = context.player.uniqueId,
                            )
                        }.getOrElse { failure ->
                            plugin.logger.log(
                                java.util.logging.Level.WARNING,
                                "タイマー設定の停止を保存できませんでした: script=${script.id}",
                                failure,
                            )
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
                        if (!updated) return@MenuActionHandler MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                    "on" to MenuActionHandler { context ->
                        val script = script(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        showTimerDialog(context.player, context.route, script.id, script.timer.intervalSeconds)
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
                        // 削除も挿入と同じ共通グラフ更新境界へ通します。分岐／合流の
                        // 整合性や描画セル衝突を確認できた候補だけを正本へ保存します。
                        val deleted = runCatching {
                            CommandSettingsModel.updateGraph(plugin, script.id, context.player.uniqueId) { candidateGraph ->
                                if (GraphEditor.delete(candidateGraph, nodeId)) true else null
                            }
                        }
                            .getOrElse { failure ->
                                plugin.logger.log(
                                    java.util.logging.Level.WARNING,
                                    "コマンド削除を保存できませんでした: script=${script.id} node=$nodeId",
                                    failure,
                                )
                                return@MenuActionHandler MenuActionResult.Rejected(
                                    Component.text("コマンドを削除できませんでした。経路を確認してください。"),
                                )
                            }
                        if (deleted != true) return@MenuActionHandler MenuActionResult.Ignored
                        // 削除の確定後だけ削除音を鳴らします。確認画面を開いただけ、
                        // または保存に失敗した場合は状態を変えていないため鳴らしません。
                        context.player.playSound(
                            context.player.location,
                            Sound.BLOCK_BAMBOO_HIT,
                            1.0f,
                            1.0f,
                        )
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
        val category = CommandCategory.fromRoute(route.payload[PICKER_CATEGORY])
        val types = CommandType.entries.filter { type ->
            if (CommandPresentationPolicy.category(type) != category) return@filter false
            when (type) {
                CommandType.FOR_END -> false
                CommandType.MERGE -> GraphEditor.canAppendMerge(script?.graph, mergeConditionId)
                CommandType.BREAK, CommandType.CONTINUE -> insideFor
                else -> true
            }
        }
        val elements = types.mapIndexed { index, type ->
            KcGui.menuEntry(
                player = player,
                slot = CommandPickerLayoutPolicy.itemSlots[index],
                material = type.icon,
                name = KcI18n.text(player, type.key),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, type.descriptionKey),
                actions = listOf(GuiMenuActionIntent.AnyClick(
                    actionId = "select",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ADD),
                    payload = mapOf("type" to type.name),
                )),
            )
        }.toMutableList()
        CommandPickerLayoutPolicy.itemSlots.drop(types.size).forEach { slot ->
            elements += MenuElement(
                slot,
                KcGui.elements.decoration(Material.WHITE_STAINED_GLASS_PANE),
                GuiElementRole.DECORATION,
            )
        }
        CommandCategory.entries.forEachIndexed { index, option ->
            val selected = option == category
            elements += KcGui.menuEntry(
                player = player,
                slot = CommandPickerLayoutPolicy.categorySlots[index],
                material = when (option) {
                    CommandCategory.EXECUTION -> Material.COMMAND_BLOCK
                    CommandCategory.CONTROL -> Material.CHAIN_COMMAND_BLOCK
                },
                name = KcI18n.text(player, option.labelKey),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, option.descriptionKey),
                data = listOf(GuiMenuEntryData(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CATEGORY_STATE),
                    KcI18n.text(player, if (selected) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECTED else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_NOT_SELECTED),
                    if (selected) GuiValueTone.SUCCESS else GuiValueTone.MUTED,
                )),
                actions = if (selected) emptyList() else listOf(GuiMenuActionIntent.AnyClick(
                    actionId = "category",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SHOW_CATEGORY),
                    payload = mapOf("category" to option.routeValue),
                )),
                glint = selected,
            )
        }
        elements += backElement(player, CommandPickerLayoutPolicy.BACK_SLOT)
        return InventoryMenuView(CommandPickerLayoutPolicy.SIZE, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECT_COMMAND)), elements)
    }

    /**
     * 選択画面の説明・値・クリック受付を同じ意味宣言から生成します。
     * 個別画面でLoreだけ、または受付クリックだけが取り残される再発を防ぎます。
     */
    private fun choiceElement(
        player: Player,
        slot: Int,
        material: Material,
        name: String,
        actionId: String,
        payload: Map<String, String> = emptyMap(),
        dataLabel: String? = null,
        dataValue: String? = null,
        description: List<String>? = null,
        style: GuiNameStyle = GuiNameStyle.PRIMARY,
        enabled: Boolean = true,
    ): MenuElement = KcGui.menuEntry(
        player = player,
        slot = slot,
        material = if (enabled) material else DisabledGuiVisualPolicy.material,
        name = name,
        style = style,
        description = description
            ?: KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CHOICE_DESCRIPTION, mapOf("value" to name)),
        data = if (dataLabel == null || dataValue == null) emptyList() else listOf(GuiMenuEntryData(dataLabel, dataValue)),
        actions = if (enabled) listOf(GuiMenuActionIntent.AnyClick(
            actionId = actionId,
            label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECT_ACTION),
            payload = payload,
        )) else emptyList(),
    )

    private fun renderSettings(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
            ?: return InventoryMenuView(45, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_COMMAND_SETTINGS)), listOf(backElement(player)))
        val fields = settingsFields(node)
        val slots = CommandSettingsSlotPolicy.slots(node.type, fields.map(EditorField::key))
        val menuSize = CommandSettingsSlotPolicy.size(node.type, fields.size)
        val elements = fields.mapIndexed { index, field ->
            KcGui.menuEntry(
                player = player,
                slot = slots[index],
                material = field.material,
                name = KcI18n.text(player, field.label),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, field.descriptionKey),
                data = listOf(GuiMenuEntryData(
                    KcI18n.text(player, field.label),
                    field.value(node).render(player),
                )),
                actions = listOf(GuiMenuActionIntent.AnyClick(
                    actionId = "field",
                    label = KcI18n.text(player, field.actionKey),
                    payload = mapOf("field" to field.key),
                )),
            )
        }.toMutableList()
        elements += backElement(player, CommandSettingsSlotPolicy.backSlot(node.type, fields.size))
        return InventoryMenuView(
            menuSize,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_COMMAND_SETTINGS_NAMED, mapOf("command" to KcI18n.text(player, node.type.key)))),
            elements,
        )
    }

    private fun renderTarget(player: Player, route: MenuRoute): InventoryMenuView {
        // 実行モデルの細分類（最も近い／周囲／全員など）は大分類の詳細設定へ
        // まとめ、ここでは仕様上の三択だけを表示します。
        val node = node(route)
        val graph = script(route)?.graph
        val options = listOf(
            Triple(TargetCategory.INHERITED, Material.TARGET, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERITED_TARGET)),
            Triple(TargetCategory.PLAYER, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER)),
            Triple(TargetCategory.NON_PLAYER_ENTITY, Material.ARMOR_STAND, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            val enabled = node?.let { currentNode ->
                graph?.let { currentGraph ->
                    CommandSettingsModel.targetCategoryAvailable(currentGraph, currentNode.id, option.first)
                } ?: false
            } ?: option.first != TargetCategory.INHERITED
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                option.third,
                "select",
                mapOf("category" to option.first.name),
                enabled = enabled,
                style = if (enabled) GuiNameStyle.PRIMARY else GuiNameStyle.MUTED,
            )
        }.toMutableList()
        selectedTargetSpec(route)?.let { spec ->
            val filterTitle = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TARGET_FILTER_TITLE)
            elements += choiceElement(
                player = player,
                slot = 40,
                material = Material.COMPARATOR,
                name = filterTitle,
                actionId = "filter",
                dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                dataValue = displayTarget(spec.kind).render(player),
            )
        }
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TARGET_TITLE)), elements)
    }

    private fun renderTargetFilters(player: Player, route: MenuRoute): InventoryMenuView {
        val spec = selectedTargetSpec(route) ?: TargetSpec(TargetKind.NEAREST_ENTITY)
        // 種別に対して意味を持つ詳細条件だけを提示します
        // （プレイヤー種別にentityType、エンティティ種別にgameModeは解決しないため）。
        val allOptions = listOf(
            DetailOption(Material.TARGET, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, "kind", displayTarget(spec.kind)),
            DetailOption(Material.ARMOR_STAND, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_TYPE, "entityType", displayLiteral(spec.entityType)),
            DetailOption(Material.COMPARATOR, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE, "distance", displayLiteral(displayDistance(spec.minimumDistance, spec.maximumDistance))),
            DetailOption(Material.COMPASS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_RANGE, "range", displayLiteral(displayTargetRange(spec.dx, spec.dy, spec.dz))),
            DetailOption(Material.REPEATER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LIMIT, "limit", displayLiteral(spec.limit)),
            DetailOption(Material.COMPARATOR, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SORT, "sort", DisplayValue.Localized(when (spec.sort) {
                TargetSort.NEAREST -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SORT_NEAREST
                TargetSort.FURTHEST -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SORT_FURTHEST
                TargetSort.RANDOM -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SORT_RANDOM
            })),
            DetailOption(Material.PLAYER_HEAD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GAME_MODE, "gameMode", displayGameMode(spec.gameMode)),
            DetailOption(Material.NAME_TAG, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG, "tag", displayLiteral(spec.tag)),
            DetailOption(Material.OAK_SIGN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME, "name", displayLiteral(spec.name)),
        )
        val options = allOptions.filter { CommandSettingsModel.targetFilterApplies(spec.kind, it.action) }
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player, layout.itemSlots[index], option.material,
                KcI18n.text(player, option.nameKey), option.action,
                dataLabel = KcI18n.text(player, option.nameKey), dataValue = option.value.render(player),
                description = listOf(targetFilterDescription(player, option.action)),
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TARGET_FILTER_TITLE)), elements)
    }

    /** 絞り込み項目の説明もジェスチャーGUIと同じカタログから生成します。 */
    private fun targetFilterDescription(player: Player, parameter: String): String = if (parameter == "range") {
        KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_RANGE).joinToString(" ")
    } else {
        KcI18n.text(
            player,
            when (parameter) {
                "kind" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_DEFAULT
                "entityType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_ENTITY_TYPE
                "distance" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_MINIMUM_DISTANCE_BODY
                "limit" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_LIMIT
                "sort" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_SORT
                "gameMode" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_GAME_MODE
                "tag" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_TAG
                "name" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_NAME
                else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_DEFAULT
            },
        )
    }

    private fun renderPosition(player: Player, route: MenuRoute): InventoryMenuView {
        val destination = route.payload[ROLE] == "destination"
        // 「現在位置を設定」は編集画面の選択肢から廃止しました。
        // 既存データのCAPTURED値は読み込み・実行側で引き続き扱えますが、
        // 新規設定では座標／ディスク／対象など明示的な方式だけを提示します。
        val layout = ChoiceMenuLayoutPolicy.layout(if (destination) 2 else 5)
        val elements = if (destination) {
            mutableListOf(
                choiceElement(player, 20, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES_SET), "select", mapOf("kind" to PositionKind.COORDINATES.name)),
                choiceElement(player, 22, Material.ENDER_PEARL, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_OTHER_ENTITY), "target"),
            )
        } else {
            val options = listOf(
                Triple(PositionKind.DISK, Material.COMMAND_BLOCK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISK_POSITION)),
                Triple(PositionKind.EXECUTOR, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EXECUTOR_POSITION)),
                Triple(PositionKind.TARGET, Material.TARGET, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION)),
                Triple(PositionKind.MYWORLD_SPAWN, Material.RESPAWN_ANCHOR, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN)),
                Triple(PositionKind.COORDINATES, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES)),
            )
            options.mapIndexed { index, option ->
                choiceElement(player, layout.itemSlots[index], option.second, option.third,
                    "select", mapOf("kind" to option.first.name))
            }.toMutableList()
        }
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, if (destination) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_POSITION_DESTINATION_TITLE else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_POSITION_CONTEXT_TITLE)), elements)
    }

    private fun renderFacing(player: Player, route: MenuRoute): InventoryMenuView {
        val options = listOf(
            Triple(FacingKind.INHERITED, Material.GRAY_DYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_UNCHANGED)),
            Triple(FacingKind.CAPTURED, Material.SPYGLASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_FACING)),
            Triple(FacingKind.EXECUTOR, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EXECUTOR_FACING)),
            Triple(FacingKind.TARGET, Material.TARGET, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET)),
            Triple(FacingKind.COORDINATES, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES)),
            Triple(FacingKind.MYWORLD_SPAWN, Material.RESPAWN_ANCHOR, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN)),
            Triple(FacingKind.ROTATION, Material.REPEATER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMERIC)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("kind" to option.first.name))
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_FACING_TITLE)), elements)
    }

    private fun renderTimer(player: Player, route: MenuRoute): InventoryMenuView {
        val script = script(route)
        val elements = mutableListOf(
            choiceElement(player, 20, Material.REDSTONE_TORCH, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED), "off"),
            choiceElement(player, 24, Material.CLOCK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED), "on",
                dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INTERVAL_LABEL),
                dataValue = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INTERVAL_SECONDS, mapOf("value" to (script?.timer?.intervalSeconds ?: 1)))),
            backElement(player),
        )
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TIMER)), elements)
    }

    private fun renderConditionKinds(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(ConditionKind.TARGET_EXISTS, Material.ENDER_EYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_TARGET_EXISTS)),
            Triple(ConditionKind.PLAYER_STATE, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_PLAYER_STATE)),
            Triple(ConditionKind.VARIABLE_STATE, Material.REDSTONE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_VARIABLE_STATE)),
            Triple(ConditionKind.BLOCK_STATE, Material.GRASS_BLOCK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_BLOCK_STATE)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("kind" to option.first.name))
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CONDITION_TITLE)), elements)
    }

    private fun renderConditionDetail(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
            ?: return InventoryMenuView(45, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CONDITION_DETAIL_TITLE)), listOf(backElement(player)))
        val kind = runCatching { ConditionKind.valueOf(node.string("kind")) }
            .getOrDefault(ConditionKind.TARGET_EXISTS)
        val options = when (kind) {
            ConditionKind.TARGET_EXISTS -> listOf(
                DetailOption(Material.TARGET, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, "target", displayTarget(node.targetSpec?.kind ?: TargetKind.INHERITED_TARGET)),
            )
            ConditionKind.PLAYER_STATE -> listOf(
                DetailOption(Material.TARGET, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, "target", displayTarget(node.targetSpec?.kind ?: TargetKind.INHERITED_TARGET)),
                DetailOption(Material.LEVER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_STATE, "state", displayPlayerSneaking(node.string("sneaking"))),
                DetailOption(Material.CHEST, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM_CONDITION, "item", displayLiteral(node.string("item"))),
            )
            ConditionKind.VARIABLE_STATE -> listOf(
                DetailOption(Material.NAME_TAG, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, "variable", displayLiteral(node.string("variable"))),
                DetailOption(Material.COMPARATOR, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATOR, "operator", DisplayValue.Literal(node.string("operator", "=="))),
                DetailOption(Material.REPEATER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, "value", DisplayValue.Literal(node.string("value", "0"))),
            )
            ConditionKind.BLOCK_STATE -> listOf(
                DetailOption(
                    Material.COMPASS,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                    "position",
                    displayPosition(node.conditionPositionSpec?.kind ?: PositionKind.DISK),
                ),
                DetailOption(Material.GRASS_BLOCK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK, "block", DisplayValue.Literal(node.string("block", "minecraft:air"))),
            )
        }
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.material, KcI18n.text(player, option.nameKey), option.action,
                dataLabel = KcI18n.text(player, option.nameKey), dataValue = option.value.render(player))
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CONDITION_DETAIL_TITLE)), elements)
    }

    private fun renderVariableValue(player: Player, route: MenuRoute): InventoryMenuView {
        val script = script(route)
        val node = node(route)
        // 反復値・ループ回数はワールド変数へ保存せず、for body内だけの
        // 読み取り専用値として扱うため、forの外からは選択肢を表示しません。
        val insideFor = script != null && node != null &&
            node.string("type", VariableType.NUMBER.name) == VariableType.NUMBER.name &&
            GraphEditor.isInsideFor(script.graph, node.id, GraphEditor.Edge.NEXT)
        val options = buildList {
            add(DetailOption(Material.WRITABLE_BOOK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DIRECT_VALUE, "direct", DisplayValue.Literal("")))
            if (insideFor) {
                add(DetailOption(Material.COMPARATOR, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_ITERATION, "iteration", DisplayValue.Literal("")))
                add(DetailOption(Material.REPEATER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT, "count", DisplayValue.Literal("")))
            }
        }
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.material,
                KcI18n.text(player, option.nameKey), option.action)
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_VARIABLE_VALUE_TITLE)), elements)
    }

    private fun setVariableValue(value: String) = MenuActionHandler { context ->
        if (!updateNode(context.player, context.route) {
                CommandSettingsModel.setParameter(it, "value", value)
            }) {
            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
        }
        MenuActionResult.Success(MenuUpdate.Back)
    }

    private fun materialSelection(parameter: String) = MenuActionHandler { context ->
        val scriptId = scriptId(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
        val nodeId = node(context.route)?.id ?: return@MenuActionHandler MenuActionResult.Ignored
        plugin.itemSelection.beginMaterial(context.player, scriptId, nodeId, context.route, parameter)
        MenuActionResult.Success(MenuUpdate.None)
    }

    /** ブロックは自由入力や任意スロット選択を許さず、メインハンドの実物から取得します。 */
    private fun setHeldBlock(context: MenuActionContext): MenuActionResult {
        val held = context.player.inventory.itemInMainHand
        if (held.type == Material.AIR || !held.type.isBlock) return MenuActionResult.Ignored
        if (!updateNode(context.player, context.route) {
                CommandSettingsModel.setParameter(it, "block", held.type.key.toString())
            }) {
            return MenuActionResult.Rejected(
                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
            )
        }
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    private fun renderVariableTypes(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(VariableType.NUMBER, Material.COMPARATOR, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMBER)),
            Triple(VariableType.STRING, Material.WRITABLE_BOOK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("type" to option.first.name))
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_VARIABLE_TYPE_TITLE)), elements)
    }

    private fun renderEquipmentSlots(player: Player): InventoryMenuView {
        val options = listOf(
            Triple("HAND", Material.ARMOR_STAND, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HAND),
            Triple("OFF_HAND", Material.SHIELD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_OFF_HAND),
            Triple("HEAD", Material.CARVED_PUMPKIN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HEAD),
            Triple("CHEST", Material.IRON_CHESTPLATE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_CHEST),
            Triple("LEGS", Material.IRON_LEGGINGS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_LEGS),
            Triple("FEET", Material.IRON_BOOTS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_FEET),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                KcI18n.text(player, option.third),
                "select",
                mapOf("slot" to option.first),
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EQUIPMENT_SLOT)),
            elements,
        )
    }

    private fun renderVariableOperations(player: Player, route: MenuRoute): InventoryMenuView {
        val type = node(route)?.string("type")
            ?.let { runCatching { VariableType.valueOf(it) }.getOrNull() }
            ?: VariableType.NUMBER
        val options = listOf(
            Triple(VariableOperation.DEFINE, Material.LIME_DYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DEFINE)),
            Triple(VariableOperation.CHANGE, Material.SLIME_BALL, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHANGE)),
        ).filter { it.first in allowedVariableOperations(type) }
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("operation" to option.first.name))
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_VARIABLE_OPERATION_TITLE)), elements)
    }

    private fun renderVariableChangeModes(player: Player, route: MenuRoute): InventoryMenuView {
        val nodeId = route.payload["nodeId"]?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
        val node = nodeId?.let { script(route)?.graph?.nodes?.get(it) }
        val isString = node?.string("type", VariableType.NUMBER.name) == VariableType.STRING.name
        val options = if (isString) {
            listOf(Triple(VariableChangeMode.ASSIGN, Material.LIME_DYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ASSIGN)))
        } else {
            listOf(
                Triple(VariableChangeMode.ASSIGN, Material.LIME_DYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ASSIGN)),
                Triple(VariableChangeMode.CALCULATE, Material.COMPARATOR, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CALCULATE)),
            )
        }
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("changeMode" to option.first.name))
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_VARIABLE_OPERATION_TITLE)), elements)
    }

    private fun settingsFields(node: CommandNode): List<EditorField> {
        return CommandSettingsModel.visibleFields(node)
    }

    private fun allowedVariableOperations(type: VariableType): List<VariableOperation> =
        CommandSettingsModel.allowedVariableOperations(type)

    private fun renderDisplayModes(player: Player): InventoryMenuView {
        val options = listOf(
            Triple("tellraw", Material.WRITABLE_BOOK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHAT)),
            Triple("title", Material.OAK_SIGN, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TITLE)),
            Triple("subtitle", Material.WRITABLE_BOOK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SUBTITLE)),
            Triple("actionbar", Material.NAME_TAG, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ACTIONBAR)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("mode" to option.first))
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISPLAY_MODE_TITLE)), elements)
    }

    private fun renderBlockOperations(player: Player): InventoryMenuView {
        val options = listOf(
            Triple("setblock", Material.BRICKS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BLOCK_SETBLOCK)),
            Triple("fill", Material.BRICKS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BLOCK_FILL)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                option.third,
                "select",
                mapOf("operation" to option.first),
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_OPERATION)),
            elements,
        )
    }

    private fun renderContextOverride(player: Player, route: MenuRoute): InventoryMenuView {
        val contextNode = node(route)
        val options = listOf(
            ContextOption(19, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EXECUTOR), "executor", state(player, contextNode?.let { CommandSettingsModel.targetSpec(it, CommandSettingRole.CONTEXT_EXECUTOR) != null } == true)),
            ContextOption(20, Material.TARGET, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET), "target", state(player, contextNode?.let { CommandSettingsModel.targetSpec(it, CommandSettingRole.CONTEXT_TARGET) != null } == true)),
            ContextOption(21, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION), "position", state(player, contextNode?.let { CommandSettingsModel.positionSpec(it, CommandSettingRole.CONTEXT_POSITION) != null } == true)),
            ContextOption(22, Material.SPYGLASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING), "facing", state(player, contextNode?.let { CommandSettingsModel.facingSpec(it) != null } == true)),
            ContextOption(24, Material.GRAY_DYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERIT_ALL), "inherit", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CLEAR_CONTEXT)),
            ContextOption(
                28,
                Material.COMPARATOR,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONTEXT_SOURCE),
                "source",
                KcI18n.text(player, if (contextNode?.let(CommandSettingsModel::contextSource) == ContextSource.PREVIOUS) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTEXT_PREVIOUS
                } else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTEXT_BASE),
            ),
        )
        val elements = options.map { option ->
            choiceElement(player, option.slot, option.material, option.name, option.action,
                dataLabel = option.name, dataValue = option.value)
        }.toMutableList()
        elements += backElement(player, 45)
        return InventoryMenuView(54, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CONTEXT_TITLE)), elements)
    }

    private fun renderDelete(player: Player, route: MenuRoute): InventoryMenuView {
        val elements = listOf(
            choiceElement(player, 20, Material.BARRIER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CANCEL_DELETE), "back"),
            choiceElement(player, 24, Material.RED_CONCRETE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DELETE_COMMAND), "delete", style = GuiNameStyle.DANGER),
        )
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DELETE_TITLE)), elements)
    }

    private fun showTimerDialog(player: Player, route: MenuRoute, scriptId: UUID, seconds: Int) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "timer-edit",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_TITLE),
                body = CommandDialogSpecs.timerBody(player, seconds),
                inputs = listOf(CommandDialogSpecs.timerInput(player, seconds)),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ENABLE), MenuDialogHandler { _, response ->
                    val rawValue = response.textValue("seconds").trim()
                    val validationError = CommandDialogSpecs.timerSeconds.validateInput(rawValue)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, validationError)
                        )
                    }
                    val value = requireNotNull(rawValue.toIntOrNull())
                    val updated = runCatching {
                        CommandSettingsModel.updateTimer(
                            plugin,
                            scriptId,
                            enabled = true,
                            intervalSeconds = value,
                            editorId = player.uniqueId,
                        )
                    }.getOrElse { failure ->
                        plugin.logger.log(
                            java.util.logging.Level.WARNING,
                            "タイマー設定を保存できませんでした: script=$scriptId",
                            failure,
                        )
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
                    if (!updated) return@MenuDialogHandler MenuActionResult.Ignored
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL), MenuDialogHandler { _, _ ->
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
            )
        )
    }

    private fun showVariableNameDialog(player: Player, route: MenuRoute, currentName: String) {
        val spec = CommandDialogSpecs.variableName
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "variable-name",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.body(player, spec, currentName),
                inputs = listOf(CommandDialogSpecs.input(player, "name", currentName, spec)),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    // 大文字を暗黙変換せず、Gesture GUIと同じ変数名規則で検証します。
                    // 入力を片方だけ正規化すると、同じ設定でもGUIによって保存値が変わります。
                    val name = response.textValue("name").trim()
                    val validationError = spec.validateInput(name)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, validationError),
                        )
                    }
                    if (!updateNode(player, route) {
                            CommandSettingsModel.setParameter(it, "name", name)
                        }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL), MenuDialogHandler { _, _ ->
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
            )
        )
    }

    private fun showFieldDialog(
        player: Player,
        route: MenuRoute,
        field: String,
        node: CommandNode,
        initialOverride: String? = null,
        candidateValues: List<String> = emptyList(),
    ) {
        if (field == "staySeconds" && node.type == CommandType.DISPLAY_TEXT) {
            showDisplayTimingDialog(player, route, node)
            return
        }
        if (field == "soundParameters" && node.type == CommandType.PLAY_SOUND) {
            showSoundParametersDialog(player, route, node)
            return
        }
        val defaultValue = when (field) {
            "count" -> "1"
            "seconds" -> when (node.type) {
                CommandType.WAIT -> "1"
                CommandType.APPLY_EFFECT -> "30"
                CommandType.CAMERA_SHAKE -> "5"
                else -> "1"
            }
            "text", "subtitle", "customName", "itemData", "value", "tags", "tag" -> ""
            "startValue", "endValue" -> "0"
            "stepValue" -> "1"
            "entity" -> "minecraft:pig"
            "sound" -> "minecraft:block.note_block.harp"
            "volume", "pitch" -> "1.0"
            "effect" -> "minecraft:speed"
            "level" -> "1"
            "intensity" -> "1.0"
            "shakeType" -> "positional"
            "slot" -> "HAND"
            else -> return
        }
        val valueSource = if (field in setOf("startValue", "endValue", "stepValue")) {
            node.string(field.removeSuffix("Value") + "Source", "FIXED")
        } else null
        val spec = CommandDialogSpecs.field(node, field, valueSource) ?: return
        val currentValue = initialOverride ?: node.string(field, defaultValue)
        val candidateButtons = candidateValues.take(12).map { candidate ->
            MenuDialogButton(
                Component.text(candidate),
                MenuDialogHandler { _, _ ->
                    val value = CommandDialogSpecs.normalize(field, candidate)
                    val validationError = spec.validateInput(value)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    }
                    if (!updateNode(player, route) {
                            CommandSettingsModel.setParameter(it, field, value)
                        }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                        )
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                },
            )
        }
        val candidateFooterActions = if (CommandDialogSpecs.supportsSuggestions(field)) {
            listOf(MenuDialogButton(
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SHOW_DETAILS),
                MenuDialogHandler { _, response ->
                    val query = response.textValue(field).trim()
                    showFieldDialog(
                        player = player,
                        route = route,
                        field = field,
                        node = node,
                        initialOverride = query,
                        candidateValues = CommandDialogSpecs.suggestions(field, query),
                    )
                    // 入力値を引き継いだ新しいDialogを同期表示するため、元の
                    // Dialogを外部入力へ戻す処理は行いません。
                    MenuActionResult.Success(MenuUpdate.None)
                },
            ))
        } else emptyList()
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "field-$field",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.body(player, spec, currentValue),
                inputs = listOf(CommandDialogSpecs.input(player, field, currentValue, spec)),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val value = CommandDialogSpecs.normalize(field, response.textValue(field))
                    val validationError = spec.validateInput(value)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    }
                    if (!updateNode(player, route) {
                            CommandSettingsModel.setParameter(it, field, value)
                        }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = dialogCancel(player, route),
                additionalActions = candidateButtons,
                footerActions = candidateFooterActions,
                multiActionWithoutExit = candidateFooterActions.isNotEmpty(),
                columns = if (candidateFooterActions.isNotEmpty()) 3 else 1,
            )
        )
    }

    /** 効果音の音量・ピッチを一つの設定項目として編集します。 */
    private fun showSoundParametersDialog(player: Player, route: MenuRoute, node: CommandNode) {
        val volumeSpec = requireNotNull(CommandDialogSpecs.field(node, "volume"))
        val pitchSpec = requireNotNull(CommandDialogSpecs.field(node, "pitch"))
        val volume = node.string("volume", "1.0")
        val pitch = node.string("pitch", "1.0")
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "sound-parameters",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.soundParametersBody(player, volume, pitch),
                inputs = CommandDialogSpecs.soundParametersInputs(player, volume, pitch),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { _, response ->
                        val volumeValue = CommandDialogSpecs.normalize("volume", response.textValue("volume"))
                        val pitchValue = CommandDialogSpecs.normalize("pitch", response.textValue("pitch"))
                        val validationError = volumeSpec.validateInput(volumeValue)
                            ?: pitchSpec.validateInput(pitchValue)
                        if (validationError != null) {
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                KcI18n.component(player, validationError),
                            )
                        }
                        if (!updateNode(player, route, configuredFields = setOf("soundParameters")) { command ->
                                CommandSettingsModel.setParameters(
                                    command,
                                    mapOf("volume" to volumeValue, "pitch" to pitchValue),
                                )
                            }) {
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        MenuActionResult.Success(MenuUpdate.Replace(route))
                    },
                ),
                cancel = dialogCancel(player, route),
            ),
        )
    }

    private fun showDisplayTimingDialog(player: Player, route: MenuRoute, node: CommandNode) {
        val fadeIn = node.string("fadeInSeconds", "1")
        val stay = node.string("staySeconds", "3")
        val fadeOut = node.string("fadeOutSeconds", "1")
        val durationSpec = requireNotNull(CommandDialogSpecs.field(node, "staySeconds"))
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "display-timing",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TITLE),
                body = CommandDialogSpecs.durationBody(
                    player,
                    fadeIn,
                    stay,
                    fadeOut,
                    node.string("mode", "tellraw"),
                ),
                inputs = CommandDialogSpecs.durationInputs(player, fadeIn, stay, fadeOut),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val rawValues = listOf("fadeInSeconds", "staySeconds", "fadeOutSeconds").associateWith { key -> response.textValue(key).trim() }
                    val validationError = rawValues.values
                        .mapNotNull(durationSpec::validateInput)
                        .firstOrNull()
                    if (validationError != null) return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    val values = rawValues.mapValues { (_, value) -> requireNotNull(value.toIntOrNull()) }
                    if (!updateNode(player, route) { command ->
                        CommandSettingsModel.setParameters(
                            command,
                            values.mapValues { (_, value) -> value.toString() },
                        )
                    }) return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
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
        spec: CommandDialogSpecs.Spec,
    ) {
        val current = node(route)?.string(parameter).orEmpty()
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "parameter-$parameter",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.body(player, spec, current),
                inputs = listOf(CommandDialogSpecs.input(player, parameter, current, spec)),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val value = CommandDialogSpecs.normalize(parameter, response.textValue(parameter))
                    val validationError = spec.validateInput(value)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    }
                    if (!updateNode(player, route) {
                            CommandSettingsModel.setParameter(it, parameter, value)
                        }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
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
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DESTINATION_COORDINATES_TITLE),
            currentX = current?.x ?: location.x,
            currentY = current?.y ?: location.y,
            currentZ = current?.z ?: location.z,
        ) { x, y, z ->
            updateNode(player, route) { command ->
                CommandSettingsModel.setPositionSpec(
                    command,
                    CommandSettingRole.fromRoute(route.payload[ROLE]),
                    PositionSpec(PositionKind.COORDINATES, x, y, z),
                )
            }
        }
    }

    private fun showFacingCoordinatesDialog(player: Player, route: MenuRoute) {
        val role = CommandSettingRole.fromRoute(route.payload[ROLE]) ?: CommandSettingRole.CONTEXT_FACING
        val current = node(route)?.let { CommandSettingsModel.facingSpec(it, role) }
        val location = player.location
        showCoordinateDialog(
            player = player,
            route = route,
            id = "facing-coordinates",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FACING_COORDINATES_TITLE),
            currentX = current?.x ?: location.x,
            currentY = current?.y ?: location.y,
            currentZ = current?.z ?: location.z,
        ) { x, y, z ->
            updateNode(player, route) { command ->
                CommandSettingsModel.setFacingSpec(
                    command,
                    FacingSpec(FacingKind.COORDINATES, x = x, y = y, z = z),
                    role,
                )
            }
        }
    }

    private fun showRotationDialog(player: Player, route: MenuRoute) {
        val role = CommandSettingRole.fromRoute(route.payload[ROLE]) ?: CommandSettingRole.CONTEXT_FACING
        val current = node(route)?.let { CommandSettingsModel.facingSpec(it, role) }
        val location = player.location
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "facing-rotation",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_TITLE),
                body = CommandDialogSpecs.rotationBody(
                    player,
                    current?.yaw ?: location.yaw,
                    current?.pitch ?: location.pitch,
                ),
                inputs = CommandDialogSpecs.rotationInputs(
                    player,
                    current?.yaw ?: location.yaw,
                    current?.pitch ?: location.pitch,
                ),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val yaw = CommandDialogSpecs.finiteFloat(response.textValue("yaw"))
                    val pitch = CommandDialogSpecs.finiteFloat(response.textValue("pitch"))
                    if (yaw == null || pitch == null || !yaw.isFinite() || !pitch.isFinite()) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_INVALID))
                    }
                    if (!updateNode(player, route) { command ->
                        CommandSettingsModel.setFacingSpec(
                            command,
                            FacingSpec(FacingKind.ROTATION, yaw = yaw, pitch = pitch),
                            role,
                        )
                    }) return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
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
        save: (Double, Double, Double) -> Boolean,
    ) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = id,
                title = Component.text(title),
                body = CommandDialogSpecs.coordinateBody(player, currentX, currentY, currentZ),
                inputs = CommandDialogSpecs.coordinateInputs(player, currentX, currentY, currentZ),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val x = CommandDialogSpecs.finiteDouble(response.textValue("x"))
                    val y = CommandDialogSpecs.finiteDouble(response.textValue("y"))
                    val z = CommandDialogSpecs.finiteDouble(response.textValue("z"))
                    if (x == null || y == null || z == null ||
                        !x.isFinite() || !y.isFinite() || !z.isFinite()
                    ) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_COORDINATES_INVALID))
                    }
                    if (!save(x, y, z)) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = dialogCancel(player, route),
            )
        )
    }

    private fun dialogCancel(player: Player, route: MenuRoute) =
        MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL), MenuDialogHandler { _, _ ->
            MenuActionResult.Success(MenuUpdate.Replace(route))
        })

    private fun selectedPosition(node: CommandNode, role: String?): PositionSpec? =
        CommandSettingsModel.positionSpec(node, CommandSettingRole.fromRoute(role))

    private fun back() = MenuActionHandler { MenuActionResult.Success(MenuUpdate.Back) }

    private fun backElement(player: Player, slot: Int = 36) =
        KcGui.elements.backEntry(player, slot)

    private fun state(player: Player, configured: Boolean): String =
        KcI18n.text(player, if (configured) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONFIGURED else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERITED)

    private fun selectedTargetSpec(route: MenuRoute): TargetSpec? {
        val node = node(route) ?: return null
        return CommandSettingsModel.targetSpec(
            node,
            CommandSettingRole.fromRoute(route.payload[ROLE]),
        )
    }

    private fun updateTargetSpec(player: Player, route: MenuRoute, change: (TargetSpec) -> TargetSpec): Boolean =
        updateNode(player, route) { node ->
            val current = selectedTargetSpec(route) ?: TargetSpec(TargetKind.NEAREST_ENTITY)
            val updated = change(current)
            CommandSettingsModel.setTargetSpec(
                node,
                CommandSettingRole.fromRoute(route.payload[ROLE]),
                updated,
            )
        }

    private fun targetFilterDialog(parameter: String) = MenuActionHandler { context ->
        if (parameter == "range") {
            openTargetRangeDialog(context.player, context.route)
        } else {
            openTargetFilterDialog(context.player, context.route, parameter)
        }
        MenuActionResult.Success(MenuUpdate.None)
    }

    /** 対象範囲は一つの設定項目として、X/Y/Zを同じDialogで編集します。 */
    private fun openTargetRangeDialog(player: Player, route: MenuRoute) {
        val currentSpec = selectedTargetSpec(route) ?: return
        val inputSpec = requireNotNull(CommandDialogSpecs.targetFilter("range"))
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "target-filter-range",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.rangeBody(player, currentSpec.dx, currentSpec.dy, currentSpec.dz),
                inputs = CommandDialogSpecs.rangeInputs(player, currentSpec.dx, currentSpec.dy, currentSpec.dz),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { _, response ->
                        val raw = listOf("dx", "dy", "dz").associateWith { key ->
                            response.textValue(key).trim().takeIf(String::isNotEmpty)
                        }
                        val validationError = raw.values
                            .filterNotNull()
                            .mapNotNull(inputSpec::validateInput)
                            .firstOrNull()
                        if (validationError != null) {
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                KcI18n.component(player, validationError),
                            )
                        }
                        val values = raw.mapValues { (_, value) -> value?.let(CommandDialogSpecs::finiteDouble) }
                        if (values.any { (rawValue, parsed) -> raw[rawValue] != null && parsed == null }) {
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISTANCE_INVALID),
                            )
                        }
                        val updated = currentSpec.copy(
                            dx = values.getValue("dx"),
                            dy = values.getValue("dy"),
                            dz = values.getValue("dz"),
                        )
                        if (!updateTargetSpec(player, route) { _ -> updated }) {
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        MenuActionResult.Success(MenuUpdate.Replace(route))
                    },
                ),
                cancel = dialogCancel(player, route),
            ),
        )
    }

    private fun openTargetFilterDialog(
        player: Player,
        route: MenuRoute,
        parameter: String,
        initialOverride: String? = null,
        candidateValues: List<String> = emptyList(),
    ): MenuActionResult {
        val currentSpec = selectedTargetSpec(route) ?: return MenuActionResult.Ignored
        if (parameter == "range") {
            openTargetRangeDialog(player, route)
            return MenuActionResult.Success(MenuUpdate.None)
        }
        val inputSpec = CommandDialogSpecs.targetFilter(parameter) ?: return MenuActionResult.Ignored
        val current = when (parameter) {
            "entityType" -> currentSpec.entityType
            "distance" -> displayDistance(currentSpec.minimumDistance, currentSpec.maximumDistance)
            "limit" -> currentSpec.limit?.toString()
            "tag" -> currentSpec.tag
            else -> currentSpec.name
        }.orEmpty().let { initialOverride ?: it }
        val inputs = if (parameter == "distance") {
            listOf(
                MenuDialogInput.Text(
                    "minimum",
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE),
                    currentSpec.minimumDistance?.toString().orEmpty(),
                    maxLength = inputSpec.maxLength,
                ),
                MenuDialogInput.Text(
                    "maximum",
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE),
                    currentSpec.maximumDistance?.toString().orEmpty(),
                    maxLength = inputSpec.maxLength,
                ),
            )
        } else {
            listOf(CommandDialogSpecs.input(player, parameter, current, inputSpec))
        }
        // 候補値は入力欄の直下へ並べ、補助操作はフッターへ分離します。
        // これにより、確定・詳細表示・キャンセルが常にDialog最下部へ固定されます。
        val candidateButtons = candidateValues.take(12).map { candidate ->
            MenuDialogButton(
                Component.text(candidate),
                MenuDialogHandler { _, _ ->
                    val value = CommandDialogSpecs.normalize(parameter, candidate)
                    val validationError = inputSpec.validateInput(value)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, validationError),
                        )
                    }
                    val updated = when (parameter) {
                        "entityType" -> currentSpec.copy(entityType = value)
                        "tag" -> currentSpec.copy(tag = value)
                        "limit" -> currentSpec.copy(limit = value.toIntOrNull())
                        else -> currentSpec.copy(name = value)
                    }
                    if (!updateTargetSpec(player, route) { _ -> updated }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                        )
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                },
            )
        }
        val candidateFooterActions = if (CommandDialogSpecs.supportsSuggestions(parameter)) {
            listOf(MenuDialogButton(
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_SHOW_DETAILS),
                MenuDialogHandler { _, response ->
                    val query = response.textValue(parameter).trim()
                    openTargetFilterDialog(
                        player,
                        route,
                        parameter,
                        initialOverride = query,
                        candidateValues = CommandDialogSpecs.suggestions(parameter, query),
                    )
                    MenuActionResult.Success(MenuUpdate.None)
                },
            ))
        } else emptyList()
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "target-filter-$parameter",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.body(player, inputSpec, current),
                inputs = inputs,
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val updated = if (parameter == "distance") {
                        val minimumRaw = response.textValue("minimum").trim().takeIf(String::isNotEmpty)
                        val maximumRaw = response.textValue("maximum").trim().takeIf(String::isNotEmpty)
                        val validationError = listOfNotNull(minimumRaw, maximumRaw)
                        .mapNotNull(inputSpec::validateInput)
                            .firstOrNull()
                        if (validationError != null) {
                            return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                        }
                        currentSpec.copy(
                            minimumDistance = minimumRaw?.toDoubleOrNull()?.takeIf(Double::isFinite),
                            maximumDistance = maximumRaw?.toDoubleOrNull()?.takeIf(Double::isFinite),
                        )
                    } else {
                        val raw = response.textValue(parameter)
                            .takeIf { it.trim().isNotEmpty() }
                            ?.let { CommandDialogSpecs.normalize(parameter, it) }
                        val validationError = raw?.let(inputSpec::validateInput)
                        if (validationError != null) {
                            return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                        }
                        val decimalValue = raw?.toDoubleOrNull()?.takeIf(Double::isFinite)
                        val integerValue = raw?.toIntOrNull()
                        when (parameter) {
                            "limit" -> currentSpec.copy(limit = integerValue)
                            "entityType" -> currentSpec.copy(entityType = raw?.let { CommandDialogSpecs.normalize(parameter, it) })
                            "tag" -> currentSpec.copy(tag = raw)
                            else -> currentSpec.copy(name = raw)
                        }
                    }
                    if (updated.minimumDistance != null && updated.maximumDistance != null &&
                        updated.minimumDistance > updated.maximumDistance
                    ) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_MINIMUM_ABOVE_MAXIMUM))
                    }
                    if (!updateTargetSpec(player, route) { _ ->
                        updated
                    }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = dialogCancel(player, route),
                additionalActions = candidateButtons,
                footerActions = candidateFooterActions,
                multiActionWithoutExit = candidateFooterActions.isNotEmpty(),
                columns = if (candidateFooterActions.isNotEmpty()) 3 else 1,
            )
        )
        return MenuActionResult.Success(MenuUpdate.None)
    }

    private fun script(route: MenuRoute) = scriptId(route)?.let(plugin.scripts::load)

    private fun node(route: MenuRoute): CommandNode? {
        val script = script(route) ?: return null
        val id = route.payload[NODE_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return script.graph.nodes[id]
    }

    private fun updateNode(
        player: Player,
        route: MenuRoute,
        configuredFields: Set<String> = emptySet(),
        change: (CommandNode) -> Unit,
    ): Boolean {
        val context = CommandSettingContext.from(route) ?: return false
        return runCatching {
            CommandSettingsModel.updateNode(
                plugin,
                context,
                configuredFields,
                editorId = player.uniqueId,
                change = change,
            ) != null
        }
            .onFailure { failure ->
                plugin.logger.log(
                    java.util.logging.Level.WARNING,
                    "コマンド設定の保存に失敗しました: script=${context.scriptId} node=${context.nodeId}",
                    failure,
                )
            }
            .getOrDefault(false)
    }

    companion object {
        private const val PICKER_ID = "command_type"
        private const val SETTINGS_ID = "command_settings"
        private const val TIMER_ID = "timer_settings"
        private const val CONDITION_KIND_ID = "condition_kind"
        private const val CONDITION_DETAIL_ID = "condition_detail"
        private const val VARIABLE_TYPE_ID = "variable_type"
        private const val VARIABLE_OPERATION_ID = "variable_operation"
        private const val VARIABLE_CHANGE_MODE_ID = "variable_change_mode"
        private const val VARIABLE_VALUE_ID = "variable_value"
        private const val ENTITY_EQUIPMENT_SLOT_ID = "entity_equipment_slot"
        private const val DISPLAY_MODE_ID = "display_mode"
        private const val BLOCK_OPERATION_ID = "block_operation"
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
        private const val PICKER_CATEGORY = "pickerCategory"
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

        private fun facingRoute(route: MenuRoute, role: String = "context_facing") =
            route.copy(id = FACING_ID, payload = route.payload + (ROLE to role))

        private fun choiceRoute(route: MenuRoute, id: String) = route.copy(id = id)

        private fun scriptId(route: MenuRoute) = EditorSession.from(route)?.scriptId
    }
}


private data class ContextOption(
    val slot: Int,
    val material: Material,
    val name: String,
    val action: String,
    val value: String,
)

private data class DetailOption(
    val material: Material,
    val nameKey: LocalizationKey<String>,
    val action: String,
    val value: DisplayValue,
)

/**
 * Loreへ渡す値が翻訳対象か利用者入力値かを、設定定義の時点で固定します。
 * 内部enum名を文字列から推測する方式に戻さないため、表示時の分岐はこの型だけを見ます。
 */
sealed interface DisplayValue {
    data class Literal(val value: String) : DisplayValue
    data class Localized(val key: LocalizationKey<String>) : DisplayValue

    fun render(player: Player): String = when (this) {
        is Literal -> value
        is Localized -> KcI18n.text(player, key)
    }
}

data class EditorField(
    val key: String,
    val label: LocalizationKey<String>,
    val material: Material,
    val descriptionKey: LocalizationKey<List<String>>,
    val actionKey: LocalizationKey<String>,
    val value: (CommandNode) -> DisplayValue,
)

object EditorMenuLayout {
    fun fields(type: CommandType): List<EditorField> {
        val fields = when (type) {
        CommandType.TELEPORT -> listOf(
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TELEPORT_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TELEPORT_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field("destination", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESTINATION, Material.COMPASS) {
                it.destinationTargetSpec?.kind?.let(::displayTarget)
                    ?: it.destinationSpec?.kind?.let(::displayPosition)
                    ?: displayUnset()
            },
            field(
                "destinationFacing",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING,
                Material.SPYGLASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_FACING,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_FACING,
            ) { it.destinationFacingSpec?.kind?.let(::displayFacing) ?: displayUnset() },
        )
        CommandType.GIVE_ITEM -> listOf(
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GIVE_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_GIVE_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_GIVE_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field(
                "item",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM,
                Material.CHEST,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_GIVE_ITEM,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_GIVE_ITEM,
            ),
            field("count", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT, Material.DIAMOND),
        )
        CommandType.ENTITY_ACTION -> listOf(
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY_ACTION_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY_ACTION_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field("action", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION, Material.SADDLE) { displayEntityAction(it.string("action", "ride")) },
            field("other", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OTHER, Material.ANVIL) {
                it.secondaryTargetSpec?.kind?.let(::displayTarget) ?: displayUnset()
            },
            field("slot", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EQUIPMENT_SLOT, Material.ARMOR_STAND) {
                displayEquipmentSlot(it.string("slot", "HAND"))
            },
            field(
                "item",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EQUIPMENT_ITEM,
                Material.CHEST,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EQUIP_ITEM,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_EQUIP_ITEM,
            ),
            field(
                "overwrite",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OVERWRITE,
                Material.LIME_DYE,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OVERWRITE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OVERWRITE,
            ) {
                displayBoolean(it.boolean("overwrite"))
            },
            field(
                "tagOperation",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATION,
                Material.NAME_TAG,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) {
                displayTagOperation(it.string("tagOperation", "add"))
            },
            field(
                "tag",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAGS,
                Material.NAME_TAG,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY_TAG,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY_TAG,
            ),
        )
        CommandType.DISPLAY_TEXT -> listOf(
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DISPLAY_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISPLAY_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISPLAY_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field("mode", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MODE, Material.OAK_SIGN) { displayTextMode(it.string("mode", "tellraw")) },
            field(
                "text",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TEXT,
                Material.WRITTEN_BOOK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TEXT,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TEXT,
            ),
            field(
                "subtitle",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TEXT,
                Material.WRITABLE_BOOK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TEXT,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TEXT,
            ),
            field(
                "staySeconds",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DURATION,
                Material.CLOCK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISPLAY_DURATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISPLAY_DURATION,
            ),
        )
        CommandType.WAIT -> listOf(
            field(
                "seconds",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
                Material.CLOCK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_WAIT_SECONDS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_WAIT_SECONDS,
            ),
        )
        CommandType.SUMMON_ENTITY -> listOf(
            field("entity", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY, Material.ZOMBIE_SPAWN_EGG),
            field(
                "customName",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_NAME,
                Material.NAME_TAG,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_NAME,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_NAME,
            ),
            field("tags", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAGS, Material.NAME_TAG),
        )
        CommandType.PLAY_SOUND -> listOf(
            field("sound", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND, Material.NOTE_BLOCK),
            field(
                "soundParameters",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_PARAMETERS,
                Material.JUKEBOX,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_PARAMETERS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_PARAMETERS,
            ) { displaySoundParameters(it.string("volume", "1.0"), it.string("pitch", "1.0")) },
            field("soundScope", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION, Material.GLOBE_BANNER_PATTERN,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) { displaySoundScope(it.string("soundScope", "CONTEXT")) },
            field("soundPosition", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION, Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) { it.soundPositionSpec?.kind?.let(::displayPosition) ?: displayUnset() },
        )
        CommandType.APPLY_EFFECT -> listOf(
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EFFECT_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_EFFECT_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field("effect", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EFFECT, Material.POTION),
            field("level", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LEVEL, Material.GLOWSTONE_DUST),
            field(
                "seconds",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
                Material.CLOCK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EFFECT_SECONDS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_EFFECT_SECONDS,
            ),
        )
        CommandType.CAMERA_SHAKE -> listOf(
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_CAMERA_SHAKE_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_CAMERA_SHAKE_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field("intensity", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_INTENSITY, Material.SPYGLASS),
            field(
                "seconds",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
                Material.CLOCK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_CAMERA_SHAKE_SECONDS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_CAMERA_SHAKE_SECONDS,
            ),
            field("shakeType", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SHAKE_TYPE, Material.COMPASS) { displayShakeType(it.string("shakeType")) },
        )
        CommandType.BLOCK_OPERATION -> listOf(
            field(
                "operation",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_OPERATION,
                Material.BRICKS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK_OPERATION,
            ) {
                displayBlockOperation(it.string("operation", "setblock"))
            },
            field(
                "block",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK,
                Material.BRICKS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK,
            ),
            field(
                "position",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK_POSITION,
            ) {
                it.blockPositionSpec?.kind?.let(::displayPosition) ?: displayUnset()
            },
            field(
                "from",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_FROM,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK_FROM,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK_FROM,
            ) {
                it.blockFromSpec?.kind?.let(::displayPosition) ?: displayUnset()
            },
            field(
                "to",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_TO,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK_TO,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK_TO,
            ) {
                it.blockToSpec?.kind?.let(::displayPosition) ?: displayUnset()
            },
        )
        CommandType.ENTITY_DELETE -> listOf(
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY_DELETE_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY_DELETE_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
        )
        CommandType.CONDITION -> listOf(
            field("inverted", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_INVERTED, Material.REDSTONE_TORCH) { displayBoolean(it.boolean("inverted")) },
            field("kind", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONDITION_KIND, Material.COMPARATOR) { displayCondition(it.string("kind")) },
            field("condition", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONDITION_VALUE, Material.TARGET) { displayCondition(it.string("kind")) },
        )
        CommandType.CONTEXT -> listOf(
            field("executor", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EXECUTOR, Material.PLAYER_HEAD) {
                it.contextOverride?.executor?.kind?.let(::displayTarget) ?: displayUnset()
            },
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.TARGET,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_CONTEXT_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_CONTEXT_TARGET,
            ) { it.contextOverride?.target?.kind?.let(::displayTarget) ?: displayUnset() },
            field("position", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION, Material.COMPASS) {
                it.contextOverride?.position?.kind?.let(::displayPosition) ?: displayUnset()
            },
            field("facing", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING, Material.SPYGLASS) {
                it.contextOverride?.facing?.kind?.let(::displayFacing) ?: displayUnset()
            },
        )
        CommandType.DISK_CALL -> listOf(
            field("diskId", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DISK, Material.MUSIC_DISC_13),
        )
        CommandType.VARIABLE -> listOf(
            field("operation", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATION, Material.REDSTONE) { displayVariableOperation(it.string("operation")) },
            field("name", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, Material.NAME_TAG),
            field("type", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TYPE, Material.STRUCTURE_VOID) { displayVariableType(it.string("type")) },
            field("changeMode", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATION, Material.COMPARATOR,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayVariableChangeMode(it.string("changeMode", "ASSIGN")) },
            field("value", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, Material.COMPARATOR) { displayVariableValue(it.string("value")) },
        )
        CommandType.MERGE, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> emptyList()
        CommandType.FOR_START -> listOf(
            field("startSource", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_START_SOURCE, Material.LIME_DYE) { displayForSource(it.string("startSource", "FIXED")) },
            field("endSource", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_END_SOURCE, Material.RED_DYE) { displayForSource(it.string("endSource", "FIXED")) },
            field("stepSource", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_STEP_SOURCE, Material.ARROW) { displayForSource(it.string("stepSource", "FIXED")) },
            field("startValue", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_START, Material.LIME_DYE),
            field("endValue", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_END, Material.RED_DYE),
            field("stepValue", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_STEP, Material.ARROW),
            field("inclusiveEnd", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_INCLUSIVE_END, Material.COMPARATOR) {
                displayBoolean(it.boolean("inclusiveEnd", true))
            },
        )
        }
        return fields
    }

    private fun field(
        key: String,
        label: LocalizationKey<String>,
        material: Material,
        descriptionKey: LocalizationKey<List<String>>? = null,
        actionKey: LocalizationKey<String>? = null,
        value: (CommandNode) -> DisplayValue = { displayLiteral(it.string(key)) },
    ): EditorField {
        val defaultPresentation = if (descriptionKey == null || actionKey == null) {
            fieldPresentation(key)
        } else null
        return EditorField(
            key,
            label,
            material,
            descriptionKey ?: requireNotNull(defaultPresentation).first,
            actionKey ?: requireNotNull(defaultPresentation).second,
            value,
        )
    }

    /** JSONパラメータ名と表示用キーを明示対応させ、翻訳キーの文字列合成を禁止します。 */
    private fun fieldPresentation(key: String): Pair<LocalizationKey<List<String>>, LocalizationKey<String>> = when (key) {
        "destination" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DESTINATION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DESTINATION
        "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_COUNT to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_COUNT
        "action" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ACTION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ACTION
        "other" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OTHER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OTHER
        "mode" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_MODE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_MODE
        "text" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TEXT to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TEXT
        "entity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY
        "tags" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TAGS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TAGS
        "sound" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND
        "volume" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VOLUME to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VOLUME
        "pitch" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PITCH to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PITCH
        "effect" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EFFECT to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_EFFECT
        "level" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_LEVEL to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_LEVEL
        "intensity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_INTENSITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_INTENSITY
        "shakeType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SHAKETYPE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SHAKETYPE
        "slot" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SLOT to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SLOT
        "inverted" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_INVERTED to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_INVERTED
        "kind" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_KIND to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_KIND
        "condition" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_CONDITION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_CONDITION
        "block" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK
        "from" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK_FROM to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK_FROM
        "to" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK_TO to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK_TO
        "executor" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EXECUTOR to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_EXECUTOR
        "position" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION
        "facing" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_FACING to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_FACING
        "diskId" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISKID to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISKID
        "scope" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SCOPE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SCOPE
        "name" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_NAME to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_NAME
        "type" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TYPE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TYPE
        "operation" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION
        "value" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE
        "startSource" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_STARTSOURCE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_STARTSOURCE
        "endSource" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENDSOURCE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENDSOURCE
        "stepSource" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_STEPSOURCE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_STEPSOURCE
        "startValue" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_STARTVALUE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_STARTVALUE
        "endValue" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENDVALUE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENDVALUE
        "stepValue" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_STEPVALUE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_STEPVALUE
        "inclusiveEnd" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_INCLUSIVEEND to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_INCLUSIVEEND
        else -> error("未定義のエディターフィールドです: $key")
    }
}

private fun displayLiteral(value: Any?): DisplayValue = value?.toString()?.takeIf(String::isNotBlank)
    ?.let(DisplayValue::Literal) ?: displayUnset()

private fun displayDistance(minimum: Double?, maximum: Double?): String? {
    if (minimum == null && maximum == null) return null
    fun format(value: Double?): String = value?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    }.orEmpty()
    return "${format(minimum)}..${format(maximum)}"
}

/** dx/dy/dzは保存形式を分けたまま、編集画面では一つの範囲設定へまとめます。 */
private fun displayTargetRange(dx: Double?, dy: Double?, dz: Double?): String? {
    if (dx == null && dy == null && dz == null) return null
    fun format(value: Double?): String = value?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    }.orEmpty()
    return "dx=${format(dx)} / dy=${format(dy)} / dz=${format(dz)}"
}

/** 音量とピッチは保存時の2値を、設定画面では一つの項目として表示します。 */
private fun displaySoundParameters(volume: String, pitch: String): DisplayValue =
    displayLiteral("$volume / $pitch")

private fun displayUnset() = DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
private fun displayBoolean(value: Boolean) = DisplayValue.Localized(
    if (value) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED,
)

private fun displayTarget(kind: TargetKind) = DisplayValue.Localized(when (kind) {
    TargetKind.INHERITED_TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERITED_TARGET
    TargetKind.NEAREST_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER
    TargetKind.NEARBY_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS
    TargetKind.ALL_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS
    TargetKind.RANDOM_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RANDOM_PLAYER
    TargetKind.NEAREST_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY
    TargetKind.NEARBY_ENTITIES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES
    TargetKind.FIXED_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
})

private fun displayPosition(kind: PositionKind) = DisplayValue.Localized(when (kind) {
    PositionKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_POSITION
    PositionKind.DISK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISK_POSITION
    PositionKind.EXECUTOR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EXECUTOR_POSITION
    PositionKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION
    PositionKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN
    PositionKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES
})

private fun displayFacing(kind: FacingKind) = DisplayValue.Localized(when (kind) {
    FacingKind.INHERITED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_UNCHANGED
    FacingKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_FACING
    FacingKind.EXECUTOR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EXECUTOR_FACING
    FacingKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET
    FacingKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES
    FacingKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN
    FacingKind.ROTATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMERIC
})

private fun displayCondition(value: String) = runCatching { ConditionKind.valueOf(value) }.getOrNull()
    ?.let { DisplayValue.Localized(it.key) } ?: displayUnset()

private fun displayVariableType(value: String) = runCatching { VariableType.valueOf(value) }.getOrNull()?.let {
    DisplayValue.Localized(when (it) {
        VariableType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMBER
        VariableType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT
    })
} ?: displayUnset()

private fun displayVariableOperation(value: String) = runCatching { VariableOperation.valueOf(value) }.getOrNull()?.let {
    DisplayValue.Localized(when (it) {
        VariableOperation.DEFINE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DEFINE
        VariableOperation.CHANGE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHANGE
    })
} ?: displayUnset()

private fun displayVariableChangeMode(value: String) = runCatching { VariableChangeMode.valueOf(value) }.getOrNull()?.let {
    DisplayValue.Localized(when (it) {
        VariableChangeMode.ASSIGN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ASSIGN
        VariableChangeMode.CALCULATE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CALCULATE
    })
} ?: displayUnset()

private fun displayVariableValue(value: String) = when (value) {
    "$" + "current_iteration_value" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_ITERATION)
    "$" + "current_loop_count" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT)
    else -> displayLiteral(value)
}

private fun displayEntityAction(value: String) = DisplayValue.Localized(
    when (value) {
        "dismount" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISMOUNT
        "equip" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIP
        "tag" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SET_TAG
        else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RIDE
    },
)

private fun displayBlockOperation(value: String) = DisplayValue.Localized(
    if (value == "fill") {
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BLOCK_FILL
    } else {
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BLOCK_SETBLOCK
    },
)

private fun displayTextMode(value: String) = DisplayValue.Localized(when (value) {
    "title" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TITLE
    "actionbar" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ACTIONBAR
    else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHAT
})

private fun displayPlayerSneaking(value: String) = when (value) {
    "true" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SNEAKING)
    "false" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ON_GROUND)
    else -> displayUnset()
}

private fun displayForSource(value: String) = DisplayValue.Localized(
    when (value) {
        "WORLD" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE
        else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_VALUE
    },
)

private fun displayTagOperation(value: String) = DisplayValue.Localized(
    if (value == "remove") KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REMOVE
    else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ADD,
)

private fun displaySoundScope(value: String) = DisplayValue.Localized(
    if (value == "WORLD") KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_WORLD_WIDE
    else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_POSITION,
)

private fun displayGameMode(value: String?) = when (value?.lowercase()) {
    "survival" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GAME_MODE_SURVIVAL)
    "creative" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GAME_MODE_CREATIVE)
    "adventure" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GAME_MODE_ADVENTURE)
    "spectator" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GAME_MODE_SPECTATOR)
    else -> displayUnset()
}

private fun displayShakeType(value: String) = when (value.lowercase()) {
    "positional" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SHAKE_POSITIONAL)
    "rotational" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SHAKE_ROTATIONAL)
    else -> displayUnset()
}

private fun displayEquipmentSlot(value: String) = when (value.uppercase()) {
    "HAND" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HAND)
    "OFF_HAND" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_OFF_HAND)
    "HEAD" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_HEAD)
    "CHEST" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_CHEST)
    "LEGS" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_LEGS)
    "FEET" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_FEET)
    else -> displayUnset()
}
