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
                        // 挿入処理は、表示中のscriptを直接変更せず候補グラフへ適用します。
                        // レイアウト検証や保存に失敗しても、メニューの次の操作が半端な
                        // ノード／合流を参照しないよう、保存成功時だけ正本を更新します。
                        val candidateGraph = script.graph.deepCopy()
                        val node = runCatching {
                            if (type == CommandType.MERGE) {
                                GraphEditor.appendMerge(candidateGraph, requireNotNull(mergeConditionId))
                            } else {
                                GraphEditor.insert(candidateGraph, sourceId, edge, type)
                            }
                        }.mapCatching { inserted ->
                            plugin.scripts.save(script.copy(graph = candidateGraph))
                            inserted
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
                        val kind = context.payload["kind"]?.let { runCatching { TargetKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val fixedEntityId = if (kind == TargetKind.FIXED_ENTITY) {
                            context.player.getTargetEntity(32)?.uniqueId
                                ?: return@MenuActionHandler MenuActionResult.Ignored
                        } else null
                        if (!updateNode(context.route) { node ->
                            // 種類の再選択は設定値確認の再訪を兼ねるため、既存の絞り込み条件を
                            // 引き継ぐ。プレイヤー系⇔エンティティ系の切り替え時だけ、適用対象外と
                            // なるentityTypeを初期化する。
                            val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
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
                    "sort" to MenuActionHandler { context ->
                        if (!updateTargetSpec(context.route) { spec ->
                            spec.copy(sort = TargetSort.entries[(spec.sort.ordinal + 1) % TargetSort.entries.size])
                        }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "gameMode" to MenuActionHandler { context ->
                        val modes = listOf(null, "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")
                        if (!updateTargetSpec(context.route) { spec ->
                            val next = (modes.indexOf(spec.gameMode) + 1).coerceAtLeast(0) % modes.size
                            spec.copy(gameMode = modes[next])
                        }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "entityType" to targetFilterDialog("entityType"),
                    "minimumDistance" to targetFilterDialog("minimumDistance"),
                    "maximumDistance" to targetFilterDialog("maximumDistance"),
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
                        if (!updateNode(context.route) { node ->
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
                        if (!updateNode(context.route) { node ->
                            CommandSettingsModel.setFacingSpec(node, spec)
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
                        if (field == "item" && node.type in setOf(CommandType.GIVE_ITEM, CommandType.EQUIP_ITEM)) {
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
                        if (field == "inverted" && node.type == CommandType.CONDITION) {
                            if (!updateNode(context.route) { it.params["inverted"] = (!it.boolean("inverted")).toString() }) {
                                return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "scope" && node.type == CommandType.VARIABLE) {
                            if (!updateNode(context.route) {
                                it.params["scope"] = if (it.string("scope") == "WORLD") "TEMPORARY" else "WORLD"
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field.endsWith("Source") && node.type == CommandType.FOR_START) {
                            // 参照元は固定値→一時変数→ワールド内変数の3択を循環する（仕様10.2）。
                            if (!updateNode(context.route) {
                                it.params[field] = when (it.string(field, "FIXED")) {
                                    "TEMPORARY" -> "WORLD"
                                    "WORLD" -> "FIXED"
                                    else -> "TEMPORARY"
                                }
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "inclusiveEnd" && node.type == CommandType.FOR_START) {
                            if (!updateNode(context.route) {
                                it.params["inclusiveEnd"] = (!it.boolean("inclusiveEnd", true)).toString()
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
                            if (!updateNode(context.route) {
                                it.params["action"] = if (it.string("action", "ride") == "ride") "dismount" else "ride"
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "contextSource") {
                            if (!updateNode(context.route) { CommandSettingsModel.toggleContextSource(it) }) {
                                return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field in setOf(
                                "count", "ticks", "text", "stay", "value", "startValue", "endValue", "stepValue",
                                "entity", "tags", "sound", "volume", "pitch", "effect", "level", "seconds",
                                "intensity", "shakeType", "slot",
                            )) {
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
                        if (!updateNode(context.route) {
                            it.params["state"] = if (it.string("state", "sneaking") == "sneaking") "on_ground" else "sneaking"
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
                    "scope" to MenuActionHandler { context ->
                        if (!updateNode(context.route) {
                            it.params["variableScope"] =
                                if (it.string("variableScope") == VariableScope.WORLD.name) {
                                    VariableScope.TEMPORARY.name
                                } else VariableScope.WORLD.name
                        }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "operator" to MenuActionHandler { context ->
                        val operators = listOf("set", "unset", "==", "!=", ">", ">=", "<", "<=")
                        if (!updateNode(context.route) {
                            val current = operators.indexOf(it.string("operator", "==")).coerceAtLeast(0)
                            it.params["operator"] = operators[(current + 1) % operators.size]
                        }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "value" to MenuActionHandler { context ->
                        showStringParameterDialog(
                            context.player,
                            context.route,
                            "value",
                            CommandDialogSpecs.signedInteger,
                        )
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "position" to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(positionRoute(context.route, "condition_position"))
                        )
                    },
                    "block" to materialSelection("block"),
                    // 条件のアイテムもインベントリ内クリック選択へ統一します。
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
                        if (!updateNode(context.route) { it.params["kind"] = kind.name }) {
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
                        MenuActionResult.Success(MenuUpdate.Navigate(facingRoute(context.route)))
                    },
                    "source" to MenuActionHandler { context ->
                        if (!updateNode(context.route) { CommandSettingsModel.toggleContextSource(it) }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "inherit" to MenuActionHandler { context ->
                        if (!updateNode(context.route) { it.contextOverride = null }) {
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
                            ?.takeIf { it in setOf("tellraw", "title", "actionbar") }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.route) { it.params["mode"] = mode }) {
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
                VARIABLE_TYPE_ID,
                renderer = { renderVariableTypes(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val type = context.payload["type"]
                            ?.let { runCatching { VariableType.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.route) {
                            it.params["type"] = type.name
                            val current = runCatching {
                                VariableOperation.valueOf(it.string("operation"))
                            }.getOrNull()
                            if (current !in allowedVariableOperations(type)) {
                                it.params["operation"] = allowedVariableOperations(type).first().name
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
                        if (!updateNode(context.route) { it.params["operation"] = operation.name }) {
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
                TIMER_ID,
                renderer = { renderTimer(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "off" to MenuActionHandler { context ->
                        val script = script(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        script.timer.enabled = false
                        script.activation = ActivationMode.NEEDS_REDSTONE
                        runCatching {
                            plugin.scripts.save(script)
                            plugin.resetActivationTiming(script.id)
                            plugin.placements.refreshDisplaysForScript(script.id)
                        }.getOrElse { failure ->
                            plugin.logger.log(
                                java.util.logging.Level.WARNING,
                                "タイマー設定の停止を保存できませんでした: script=${script.id}",
                                failure,
                            )
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
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
                        // 削除も挿入と同じ候補グラフ方式に統一します。分岐／合流の
                        // 整合性や描画セル衝突で保存に失敗しても、表示中の正本を壊しません。
                        val candidateGraph = script.graph.deepCopy()
                        if (!GraphEditor.delete(candidateGraph, nodeId)) return@MenuActionHandler MenuActionResult.Ignored
                        runCatching { plugin.scripts.save(script.copy(graph = candidateGraph)) }
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
                material = if (option == CommandCategory.PROCESS) Material.COMMAND_BLOCK else Material.CHAIN_COMMAND_BLOCK,
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
    ): MenuElement = KcGui.menuEntry(
        player = player,
        slot = slot,
        material = material,
        name = name,
        style = style,
        description = description
            ?: KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CHOICE_DESCRIPTION, mapOf("value" to name)),
        data = if (dataLabel == null || dataValue == null) emptyList() else listOf(GuiMenuEntryData(dataLabel, dataValue)),
        actions = listOf(GuiMenuActionIntent.AnyClick(
            actionId = actionId,
            label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECT_ACTION),
            payload = payload,
        )),
    )

    private fun renderSettings(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
            ?: return InventoryMenuView(45, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_COMMAND_SETTINGS)), listOf(backElement(player)))
        val fields = settingsFields(node)
        val slots = CommandSettingsSlotPolicy.slots(node.type, fields.map(EditorField::key))
        val menuSize = CommandSettingsSlotPolicy.size(node.type)
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
        if (CommandPresentationPolicy.supportsContextOverride(node.type)) {
            val configured = node.contextOverride != null
            elements += KcGui.menuEntry(
                player = player,
                slot = CommandSettingsSlotPolicy.contextSlot(node.type),
                material = Material.RECOVERY_COMPASS,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONTEXT),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_CONTEXT),
                data = listOf(GuiMenuEntryData(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONTEXT_APPLICATION),
                    KcI18n.text(player, if (configured) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONFIGURED else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERITED),
                    if (configured) GuiValueTone.SUCCESS else GuiValueTone.MUTED,
                )),
                actions = listOf(GuiMenuActionIntent.AnyClick(
                    actionId = "field",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_CONTEXT),
                    payload = mapOf("field" to "context"),
                )),
            )
        }
        elements += backElement(player, CommandSettingsSlotPolicy.backSlot(node.type))
        return InventoryMenuView(
            menuSize,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_COMMAND_SETTINGS_NAMED, mapOf("command" to KcI18n.text(player, node.type.key)))),
            elements,
        )
    }

    private fun renderTarget(player: Player, route: MenuRoute): InventoryMenuView {
        // 「実行者」「起動したプレイヤー」は実行モデル上の起動者が不在のため廃止しました。
        // 絞り込み条件は既存種類の再選択なしで「詳細フィルタ」から確認・編集できます。
        val options = listOf(
            Triple(TargetKind.INHERITED_TARGET, Material.TARGET, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERITED_TARGET)),
            Triple(TargetKind.NEAREST_PLAYER, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER)),
            Triple(TargetKind.NEARBY_PLAYERS, Material.FILLED_MAP, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS)),
            Triple(TargetKind.ALL_PLAYERS, Material.MAP, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS)),
            Triple(TargetKind.RANDOM_PLAYER, Material.ENDER_EYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RANDOM_PLAYER)),
            Triple(TargetKind.NEAREST_ENTITY, Material.ARMOR_STAND, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY)),
            Triple(TargetKind.NEARBY_ENTITIES, Material.LEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES)),
            Triple(TargetKind.FIXED_ENTITY, Material.ARMOR_STAND, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
                choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("kind" to option.first.name))
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
            DetailOption(Material.ARMOR_STAND, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_TYPE, "entityType", displayLiteral(spec.entityType)),
            DetailOption(Material.LIME_DYE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE, "minimumDistance", displayLiteral(spec.minimumDistance)),
            DetailOption(Material.RED_DYE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE, "maximumDistance", displayLiteral(spec.maximumDistance)),
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
    private fun targetFilterDescription(player: Player, parameter: String): String = KcI18n.text(
        player,
        when (parameter) {
            "entityType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_ENTITY_TYPE
            "minimumDistance" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_MINIMUM_DISTANCE_BODY
            "maximumDistance" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_MAXIMUM_DISTANCE_BODY
            "limit" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_LIMIT
            "sort" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_SORT
            "gameMode" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_GAME_MODE
            "tag" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_TAG
            "name" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_NAME
            else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DESC_FILTER_DEFAULT
        },
    )

    private fun renderPosition(player: Player, route: MenuRoute): InventoryMenuView {
        val destination = route.payload[ROLE] == "destination"
        // 「現在位置を設定」は編集画面の選択肢から廃止しました。
        // 既存データのCAPTURED値は読み込み・実行側で引き続き扱えますが、
        // 新規設定では座標／ディスク／対象など明示的な方式だけを提示します。
        val layout = ChoiceMenuLayoutPolicy.layout(if (destination) 2 else 7)
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
                Triple(PositionKind.TEMPORARY_VARIABLE, Material.REDSTONE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE)),
                Triple(PositionKind.WORLD_VARIABLE, Material.ENDER_CHEST, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE)),
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
                dataValue = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_INTERVAL_UNITS, mapOf("value" to (script?.timer?.intervalUnits ?: 1)))),
            backElement(player),
        )
        return InventoryMenuView(45, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TIMER)), elements)
    }

    private fun renderConditionKinds(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(ConditionKind.TARGET_EXISTS, Material.ENDER_EYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_TARGET_EXISTS)),
            Triple(ConditionKind.ENTITY_STATE, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_ENTITY_STATE)),
            Triple(ConditionKind.VARIABLE_STATE, Material.REDSTONE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_VARIABLE_STATE)),
            Triple(ConditionKind.BLOCK_STATE, Material.GRASS_BLOCK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_BLOCK_STATE)),
            Triple(ConditionKind.ITEM_POSSESSION, Material.CHEST, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_ITEM_POSSESSION)),
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
            ConditionKind.ENTITY_STATE -> listOf(
                DetailOption(Material.TARGET, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, "target", displayTarget(node.targetSpec?.kind ?: TargetKind.INHERITED_TARGET)),
                DetailOption(Material.LEVER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_STATE, "state", displayEntityState(node.string("state", "sneaking"))),
            )
            ConditionKind.VARIABLE_STATE -> listOf(
                DetailOption(Material.NAME_TAG, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, "variable", displayLiteral(node.string("variable"))),
                DetailOption(Material.ENDER_CHEST, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE_SCOPE, "scope", displayVariableScope(node.string("variableScope", VariableScope.TEMPORARY.name))),
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
            ConditionKind.ITEM_POSSESSION -> listOf(
                DetailOption(Material.TARGET, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, "target", displayTarget(node.targetSpec?.kind ?: TargetKind.INHERITED_TARGET)),
                DetailOption(
                    Material.CHEST,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM_CONDITION,
                    "item",
                    displayLiteral(node.string("item")),
                ),
                DetailOption(Material.DIAMOND, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT, "count", DisplayValue.Literal(node.string("count", "1"))),
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
        // 反復値・ループ回数は一時変数（起動ローカル）だけへ保存できるため、
        // ワールド内変数ノードでは選択肢を表示しない（仕様12.2）。
        val insideFor = script != null && node != null &&
            node.string("type") == VariableType.INTEGER.name &&
            node.string("scope", VariableScope.TEMPORARY.name) != VariableScope.WORLD.name &&
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
        if (!updateNode(context.route) { it.params["value"] = value }) {
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

    private fun renderVariableTypes(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(VariableType.BOOLEAN, Material.LEVER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TRUE_FALSE)),
            Triple(VariableType.INTEGER, Material.REPEATER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INTEGER)),
            Triple(VariableType.DECIMAL, Material.COMPARATOR, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DECIMAL)),
            Triple(VariableType.TEXT, Material.WRITABLE_BOOK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT)),
            Triple(VariableType.POSITION, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION)),
            Triple(VariableType.ENTITY, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_REFERENCE)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("type" to option.first.name))
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_VARIABLE_TYPE_TITLE)), elements)
    }

    private fun renderVariableOperations(player: Player, route: MenuRoute): InventoryMenuView {
        val type = node(route)?.string("type")
            ?.let { runCatching { VariableType.valueOf(it) }.getOrNull() }
            ?: VariableType.BOOLEAN
        val options = listOf(
            Triple(VariableOperation.SET, Material.LIME_DYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SET)),
            Triple(VariableOperation.ADD, Material.SLIME_BALL, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ADD)),
            Triple(VariableOperation.SUBTRACT, Material.FERMENTED_SPIDER_EYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SUBTRACT)),
            Triple(VariableOperation.TOGGLE, Material.LEVER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TOGGLE)),
            Triple(VariableOperation.STORE_POSITION, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STORE_POSITION)),
            Triple(VariableOperation.STORE_TARGET, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STORE_TARGET)),
            Triple(VariableOperation.CLEAR, Material.BARRIER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CLEAR)),
        ).filter { it.first in allowedVariableOperations(type) }
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("operation" to option.first.name))
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

    private fun renderContextOverride(player: Player, route: MenuRoute): InventoryMenuView {
        val context = node(route)?.contextOverride
        val options = listOf(
            ContextOption(19, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EXECUTOR), "executor", state(player, context?.executor != null)),
            ContextOption(20, Material.TARGET, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET), "target", state(player, context?.target != null)),
            ContextOption(21, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION), "position", state(player, context?.position != null)),
            ContextOption(22, Material.SPYGLASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING), "facing", state(player, context?.facing != null)),
            ContextOption(24, Material.GRAY_DYE, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INHERIT_ALL), "inherit", KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CLEAR_CONTEXT)),
            ContextOption(
                28,
                Material.COMPARATOR,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONTEXT_SOURCE),
                "source",
                KcI18n.text(player, if (node(route)?.effectiveContextSource == ContextSource.PREVIOUS) {
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

    private fun showTimerDialog(player: Player, route: MenuRoute, scriptId: UUID, units: Int) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "timer-edit",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_TITLE),
                body = listOf(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_BODY),
                    Component.text("現在値: ${units}単位。1単位=10tickです。", NamedTextColor.GRAY),
                ),
                inputs = listOf(
                    MenuDialogInput.Text(
                        "units",
                        KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_INTERVAL),
                        units.toString(),
                        maxLength = 5,
                    )
                ),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ENABLE), MenuDialogHandler { _, response ->
                    val value = response.textValue("units").toIntOrNull()
                    if (value == null || value !in 1..MAX_TIMER_UNITS) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_TIMER_INVALID)
                        )
                    }
                    val script = plugin.scripts.load(scriptId)
                        ?: return@MenuDialogHandler MenuActionResult.Ignored
                    script.timer.enabled = true
                    script.timer.intervalUnits = value
                    runCatching {
                        plugin.scripts.save(script)
                        plugin.resetActivationTiming(script.id)
                        plugin.placements.refreshDisplaysForScript(script.id)
                    }.getOrElse { failure ->
                        plugin.logger.log(
                            java.util.logging.Level.WARNING,
                            "タイマー設定を保存できませんでした: script=${script.id}",
                            failure,
                        )
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_BACK), MenuDialogHandler { _, _ ->
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
                    val name = response.textValue("name").trim().lowercase()
                    val validationError = name.takeIf(String::isNotEmpty)?.let(spec.validate)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, validationError),
                        )
                    }
                    if (!updateNode(route) { it.params["name"] = name }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                }),
                cancel = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_BACK), MenuDialogHandler { _, _ ->
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
        val defaultValue = when (field) {
            "count" -> "1"
            "ticks" -> "20"
            "text", "value", "tags" -> ""
            "startValue", "endValue" -> "0"
            "stepValue" -> "1"
            "entity" -> "minecraft:pig"
            "sound" -> "minecraft:block.note_block.harp"
            "volume", "pitch" -> "1.0"
            "effect" -> "minecraft:speed"
            "level" -> "1"
            "seconds" -> "30"
            "intensity" -> "1.0"
            "shakeType" -> "positional"
            "slot" -> "HAND"
            else -> return
        }
        val valueSource = if (field in setOf("startValue", "endValue", "stepValue")) {
            node.string(field.removeSuffix("Value") + "Source", "FIXED")
        } else null
        val spec = CommandDialogSpecs.field(field, valueSource) ?: return
        val currentValue = node.string(field, defaultValue)
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "field-$field",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.body(player, spec, currentValue),
                inputs = listOf(CommandDialogSpecs.input(player, field, currentValue, spec)),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val value = response.textValue(field).trim()
                    val validationError = value.takeIf(String::isNotEmpty)?.let(spec.validate)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    }
                    if (!updateNode(route) { it.params[field] = value }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
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
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TITLE),
                body = listOf(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_BODY),
                    Component.text(
                        "現在値: fadeIn=${node.string("fadeIn", "10")}, stay=${node.string("stay", "60")}, fadeOut=${node.string("fadeOut", "10")} tick",
                        NamedTextColor.GRAY,
                    ),
                ),
                inputs = listOf(
                    MenuDialogInput.Text("fadeIn", KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_IN), node.string("fadeIn", "10")),
                    MenuDialogInput.Text("stay", KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STAY), node.string("stay", "60")),
                    MenuDialogInput.Text("fadeOut", KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_OUT), node.string("fadeOut", "10")),
                ),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val values = listOf("fadeIn", "stay", "fadeOut").associateWith { response.textValue(it).toIntOrNull() }
                    if (values.values.any { it == null || it < 0 }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_INVALID)
                        )
                    }
                    if (!updateNode(route) { command ->
                        values.forEach { (key, value) -> command.params[key] = value.toString() }
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
                    val value = response.textValue(parameter).trim()
                    val validationError = value.takeIf(String::isNotEmpty)?.let(spec.validate)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    }
                    if (!updateNode(route) { it.params[parameter] = value }) {
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
        val spec = CommandDialogSpecs.variableName
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "position-variable",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.body(player, spec, current),
                inputs = listOf(CommandDialogSpecs.input(player, "name", current, spec)),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val name = response.textValue("name").trim().lowercase()
                    val validationError = name.takeIf(String::isNotEmpty)?.let(spec.validate)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, validationError),
                        )
                    }
                    if (!updateNode(route) { command ->
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
                    }) return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
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
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FACING_COORDINATES_TITLE),
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
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_TITLE),
                body = listOf(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_BODY),
                    Component.text("現在値: yaw=${current?.yaw ?: location.yaw}, pitch=${current?.pitch ?: location.pitch}", NamedTextColor.GRAY),
                ),
                inputs = listOf(
                    MenuDialogInput.Text("yaw", Component.text("yaw"), (current?.yaw ?: location.yaw).toString()),
                    MenuDialogInput.Text("pitch", Component.text("pitch"), (current?.pitch ?: location.pitch).toString()),
                ),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val yaw = response.textValue("yaw").toFloatOrNull()
                    val pitch = response.textValue("pitch").toFloatOrNull()
                    if (yaw == null || pitch == null || !yaw.isFinite() || !pitch.isFinite()) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_INVALID))
                    }
                    if (!updateNode(route) { command ->
                        command.contextOverride = (command.contextOverride ?: ExecutionContextSpec()).copy(
                            facing = FacingSpec(FacingKind.ROTATION, yaw = yaw, pitch = pitch)
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
                body = listOf(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_COORDINATES_BODY),
                    Component.text("現在値: X=$currentX, Y=$currentY, Z=$currentZ", NamedTextColor.GRAY),
                    Component.text("3つの座標を入力した位置へ移動・判定します。", NamedTextColor.GRAY),
                ),
                inputs = listOf(
                    MenuDialogInput.Text("x", Component.text("X"), currentX.toString()),
                    MenuDialogInput.Text("y", Component.text("Y"), currentY.toString()),
                    MenuDialogInput.Text("z", Component.text("Z"), currentZ.toString()),
                ),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val x = response.textValue("x").toDoubleOrNull()
                    val y = response.textValue("y").toDoubleOrNull()
                    val z = response.textValue("z").toDoubleOrNull()
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
        MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_BACK), MenuDialogHandler { _, _ ->
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

    private fun updateTargetSpec(route: MenuRoute, change: (TargetSpec) -> TargetSpec): Boolean =
        updateNode(route) { node ->
            val current = selectedTargetSpec(route) ?: TargetSpec(TargetKind.NEAREST_ENTITY)
            val updated = change(current)
            CommandSettingsModel.setTargetSpec(
                node,
                CommandSettingRole.fromRoute(route.payload[ROLE]),
                updated,
            )
        }

    private fun targetFilterDialog(
        parameter: String,
    ) = MenuActionHandler { context ->
        val player = context.player
        val currentSpec = selectedTargetSpec(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
        val inputSpec = CommandDialogSpecs.targetFilter(parameter) ?: return@MenuActionHandler MenuActionResult.Ignored
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
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.body(player, inputSpec, current),
                inputs = listOf(CommandDialogSpecs.input(player, parameter, current, inputSpec)),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val raw = response.textValue(parameter).trim().takeIf(String::isNotEmpty)
                    val validationError = raw?.let(inputSpec.validate)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    }
                    val decimalValue = raw?.toDoubleOrNull()?.takeIf(Double::isFinite)
                    val integerValue = raw?.toIntOrNull()
                    val updated = when (parameter) {
                        "minimumDistance" -> currentSpec.copy(minimumDistance = decimalValue)
                        "maximumDistance" -> currentSpec.copy(maximumDistance = decimalValue)
                        "limit" -> currentSpec.copy(limit = integerValue)
                        "entityType" -> currentSpec.copy(entityType = raw)
                        "tag" -> currentSpec.copy(tag = raw)
                        else -> currentSpec.copy(name = raw)
                    }
                    if (updated.minimumDistance != null && updated.maximumDistance != null &&
                        updated.minimumDistance > updated.maximumDistance
                    ) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_MINIMUM_ABOVE_MAXIMUM))
                    }
                    if (!updateTargetSpec(context.route) { _ ->
                        updated
                    }) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(context.route))
                }),
                cancel = dialogCancel(player, context.route),
            )
        )
        MenuActionResult.Success(MenuUpdate.None)
    }

    private fun script(route: MenuRoute) = scriptId(route)?.let(plugin.scripts::load)

    private fun node(route: MenuRoute): CommandNode? {
        val script = script(route) ?: return null
        val id = route.payload[NODE_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return script.graph.nodes[id]
    }

    private fun updateNode(route: MenuRoute, change: (CommandNode) -> Unit): Boolean {
        val context = CommandSettingContext.from(route) ?: return false
        return runCatching { CommandSettingsModel.updateNode(plugin, context, change) != null }
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

        private fun facingRoute(route: MenuRoute) = route.copy(id = FACING_ID)

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
            field("target", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, Material.PLAYER_HEAD) {
                it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset()
            },
            field("destination", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESTINATION, Material.COMPASS) {
                it.destinationTargetSpec?.kind?.let(::displayTarget)
                    ?: it.destinationSpec?.kind?.let(::displayPosition)
                    ?: displayUnset()
            },
        )
        CommandType.GIVE_ITEM -> listOf(
            field("target", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GIVE_TARGET, Material.PLAYER_HEAD) {
                it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset()
            },
            field("item", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM, Material.CHEST),
            field("count", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT, Material.DIAMOND),
        )
        CommandType.ENTITY_ACTION -> listOf(
            field("target", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, Material.PLAYER_HEAD) {
                it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset()
            },
            field("action", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION, Material.SADDLE) { displayEntityAction(it.string("action", "ride")) },
            field("other", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OTHER, Material.ANVIL) {
                it.secondaryTargetSpec?.kind?.let(::displayTarget) ?: displayUnset()
            },
        )
        CommandType.DISPLAY_TEXT -> listOf(
            field("target", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DISPLAY_TARGET, Material.PLAYER_HEAD) {
                it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset()
            },
            field("mode", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MODE, Material.OAK_SIGN) { displayTextMode(it.string("mode", "tellraw")) },
            field("text", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TEXT, Material.WRITTEN_BOOK),
            field("stay", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DURATION, Material.CLOCK),
        )
        CommandType.WAIT -> listOf(field("ticks", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WAIT, Material.CLOCK))
        CommandType.SUMMON_ENTITY -> listOf(
            field("entity", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY, Material.ZOMBIE_SPAWN_EGG),
            field("tags", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAGS, Material.NAME_TAG),
        )
        CommandType.PLAY_SOUND -> listOf(
            field("sound", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND, Material.NOTE_BLOCK),
            field("volume", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VOLUME, Material.JUKEBOX),
            field("pitch", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PITCH, Material.NOTE_BLOCK),
        )
        CommandType.APPLY_EFFECT -> listOf(
            field("target", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, Material.PLAYER_HEAD) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field("effect", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EFFECT, Material.POTION),
            field("level", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LEVEL, Material.GLOWSTONE_DUST),
            field("seconds", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS, Material.CLOCK),
        )
        CommandType.CAMERA_SHAKE -> listOf(
            field("target", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, Material.PLAYER_HEAD) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field("intensity", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_INTENSITY, Material.SPYGLASS),
            field("seconds", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS, Material.CLOCK),
            field("shakeType", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SHAKE_TYPE, Material.COMPASS) { displayShakeType(it.string("shakeType")) },
        )
        CommandType.EQUIP_ITEM -> listOf(
            field("target", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, Material.PLAYER_HEAD) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field("slot", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EQUIPMENT_SLOT, Material.ARMOR_STAND) { displayEquipmentSlot(it.string("slot")) },
            field("item", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM, Material.CHEST),
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
            field("target", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, Material.TARGET) {
                it.contextOverride?.target?.kind?.let(::displayTarget) ?: displayUnset()
            },
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
            field("scope", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SCOPE, Material.ENDER_CHEST) { displayVariableScope(it.string("scope", "TEMPORARY")) },
            field("name", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, Material.NAME_TAG),
            field("type", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TYPE, Material.STRUCTURE_VOID) { displayVariableType(it.string("type")) },
            field("operation", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATION, Material.REDSTONE) { displayVariableOperation(it.string("operation")) },
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
        value: (CommandNode) -> DisplayValue = { displayLiteral(it.string(key)) },
    ): EditorField {
        val (description, action) = fieldPresentation(key)
        return EditorField(key, label, material, description, action, value)
    }

    /** JSONパラメータ名と表示用キーを明示対応させ、翻訳キーの文字列合成を禁止します。 */
    private fun fieldPresentation(key: String): Pair<LocalizationKey<List<String>>, LocalizationKey<String>> = when (key) {
        "target" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TARGET to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TARGET
        "destination" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DESTINATION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DESTINATION
        "item" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ITEM to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ITEM
        "count" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_COUNT to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_COUNT
        "action" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ACTION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ACTION
        "other" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OTHER to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OTHER
        "mode" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_MODE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_MODE
        "text" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TEXT to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TEXT
        "stay" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_STAY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_STAY
        "ticks" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TICKS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TICKS
        "entity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY
        "tags" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TAGS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TAGS
        "sound" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND
        "volume" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VOLUME to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VOLUME
        "pitch" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PITCH to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PITCH
        "effect" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EFFECT to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_EFFECT
        "level" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_LEVEL to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_LEVEL
        "seconds" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SECONDS to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SECONDS
        "intensity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_INTENSITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_INTENSITY
        "shakeType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SHAKETYPE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SHAKETYPE
        "slot" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SLOT to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SLOT
        "inverted" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_INVERTED to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_INVERTED
        "kind" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_KIND to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_KIND
        "condition" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_CONDITION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_CONDITION
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
    PositionKind.TEMPORARY_VARIABLE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
    PositionKind.WORLD_VARIABLE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE
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

private fun displayVariableScope(value: String) = DisplayValue.Localized(
    if (value == VariableScope.WORLD.name) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE,
)

private fun displayVariableType(value: String) = runCatching { VariableType.valueOf(value) }.getOrNull()?.let {
    DisplayValue.Localized(when (it) {
        VariableType.BOOLEAN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TRUE_FALSE
        VariableType.INTEGER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_INTEGER
        VariableType.DECIMAL -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DECIMAL
        VariableType.TEXT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT
        VariableType.POSITION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION
        VariableType.ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_REFERENCE
    })
} ?: displayUnset()

private fun displayVariableOperation(value: String) = runCatching { VariableOperation.valueOf(value) }.getOrNull()?.let {
    DisplayValue.Localized(when (it) {
        VariableOperation.SET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SET
        VariableOperation.ADD -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ADD
        VariableOperation.SUBTRACT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SUBTRACT
        VariableOperation.TOGGLE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TOGGLE
        VariableOperation.STORE_POSITION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STORE_POSITION
        VariableOperation.STORE_TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STORE_TARGET
        VariableOperation.CLEAR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CLEAR
    })
} ?: displayUnset()

private fun displayVariableValue(value: String) = when (value) {
    "$" + "current_iteration_value" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_ITERATION)
    "$" + "current_loop_count" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT)
    else -> displayLiteral(value)
}

private fun displayEntityAction(value: String) = DisplayValue.Localized(
    if (value == "dismount") KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISMOUNT else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RIDE,
)

private fun displayTextMode(value: String) = DisplayValue.Localized(when (value) {
    "title" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TITLE
    "actionbar" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ACTIONBAR
    else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHAT
})

private fun displayEntityState(value: String) = DisplayValue.Localized(
    if (value == "on_ground") KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ON_GROUND else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SNEAKING,
)

private fun displayForSource(value: String) = DisplayValue.Localized(
    when (value) {
        "TEMPORARY" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
        "WORLD" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_WORLD_VARIABLE
        else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_VALUE
    },
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
