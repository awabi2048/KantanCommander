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
import me.awabi2048.kantancommander.model.CommandValueRules
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
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.SystemVariableNames
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
                        val continuationId = context.route.payload[CONTINUATION_ID]?.takeIf(String::isNotBlank)
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        // 表示後に別画面でグラフが更新されると、表示時には有効だった
                        // 合流候補が無効になることがあります。ジェスチャーGUIと同じ
                        // 検証を実行境界でも行い、IllegalArgumentExceptionをイベントへ
                        // 漏らさず安全に操作を無視します。
                        if (type == CommandType.MERGE && !GraphEditor.canAppendMerge(script.graph, mergeConditionId, continuationId)) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        // 挿入処理は共通グラフ更新入口へ通します。表示中のscriptを
                        // 直接変更せず、レイアウト検証・保存・配置表示更新を両GUIで同じ
                        // 順序にすることで、挿入経路だけ別の正本状態を作りません。
                        val node = runCatching {
                            CommandSettingsModel.updateGraph(plugin, script.id, context.player.uniqueId) { candidateGraph ->
                                if (type == CommandType.MERGE) {
                                    if (!GraphEditor.canAppendMerge(candidateGraph, mergeConditionId, continuationId)) {
                                        null
                                    } else {
                                        GraphEditor.appendMerge(
                                            candidateGraph,
                                            requireNotNull(mergeConditionId),
                                            continuationId = continuationId,
                                        )
                                    }
                                } else {
                                    GraphEditor.insert(
                                        candidateGraph,
                                        sourceId,
                                        edge,
                                        type,
                                        continuationId = continuationId,
                                    )
                                }
                            }
                        }.getOrElse { failure ->
                            plugin.logger.log(
                                java.util.logging.Level.WARNING,
                                "コマンド挿入を保存できませんでした: script=${script.id} type=$type",
                                failure,
                            )
                            return@MenuActionHandler MenuActionResult.Rejected(
                                GraphLayoutFailureFeedback.operationMessage(context.player, failure),
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
                        val currentKind = current?.kind
                        val kind = if (CommandSettingsModel.targetCategoryMatches(currentKind, category)) {
                            currentKind ?: CommandSettingsModel.defaultTargetKind(category)
                        } else {
                            CommandSettingsModel.defaultTargetKind(category)
                        }
                        if (category == TargetCategory.TEMPORARY) {
                            if (currentKind != TargetKind.TEMPORARY) {
                                // 一時変数は1回目を方式選択、2回目を変数名入力とします。
                                // TargetSpecのsetterが入力前のSpecを未設定として扱うため、
                                // 方式だけ選択した状態で値未入力のまま確定しません。
                                if (!updateNode(context.player, context.route) { target ->
                                        CommandSettingsModel.setTargetSpec(
                                            target,
                                            routeRole,
                                            TargetSpec(TargetKind.TEMPORARY),
                                        )
                                    }) {
                                    return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                                }
                                return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Replace(context.route))
                            }
                            showTemporaryTargetDialog(
                                context.player,
                                context.route,
                                routeRole,
                                current,
                            )
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
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
                        val currentNode = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                        if (!CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(currentNode, role, kind)) {
                            // 表示後に操作条件が変化しても、保存入口で同じ判定を再実行します。
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        val currentKind = CommandSettingsModel.positionKind(currentNode, role)
                        if (kind == PositionKind.COORDINATES) {
                            // 座標は「選択」と「入力」を別操作にします。未選択からの
                            // クリックでは座標方式だけを確定し、次のクリックで入力画面を開きます。
                            if (currentKind != PositionKind.COORDINATES) {
                                val location = context.player.location
                                if (!updateNode(context.player, context.route) { node ->
                                    CommandSettingsModel.setPositionSpec(
                                        node,
                                        role,
                                        PositionSpec(PositionKind.COORDINATES, location.x, location.y, location.z),
                                    )
                                }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                                return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Replace(context.route))
                            }
                            showPositionDialog(context.player, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (kind == PositionKind.TEMPORARY) {
                            if (currentKind != PositionKind.TEMPORARY) {
                                // 一時変数の方式選択と変数名入力を分離し、1回目は
                                // 未完成のPositionSpecだけを保存して入力画面は開きません。
                                if (!updateNode(context.player, context.route) { node ->
                                        CommandSettingsModel.setPositionSpec(
                                            node,
                                            role,
                                            PositionSpec(PositionKind.TEMPORARY),
                                        )
                                    }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                                return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Replace(context.route))
                            }
                            showTemporaryPositionDialog(context.player, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val location = context.player.location
                        val spec = if (kind == PositionKind.CAPTURED) {
                            PositionSpec(kind, location.x, location.y, location.z, location.yaw, location.pitch)
                        } else PositionSpec(kind)
                        if (!updateNode(context.player, context.route) { node ->
                            CommandSettingsModel.setPositionSpec(
                                node,
                                role,
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
                        if (kind == FacingKind.TEMPORARY) {
                            val currentNode = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                            val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                                ?: CommandSettingRole.DESTINATION_FACING
                            if (CommandSettingsModel.facingSpec(currentNode, role)?.kind != FacingKind.TEMPORARY) {
                                // 一時変数は1回目を方式選択、2回目を変数名入力とし、
                                // 入力前のFacingSpecを設定済みにはしません。
                                if (!updateNode(context.player, context.route) { node ->
                                        CommandSettingsModel.setFacingSpec(
                                            node,
                                            FacingSpec(FacingKind.TEMPORARY),
                                            role,
                                        )
                                    }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                                return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Replace(context.route))
                            }
                            showTemporaryFacingDialog(context.player, context.route)
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
                                CommandSettingRole.fromRoute(context.route.payload[ROLE]) ?: CommandSettingRole.DESTINATION_FACING,
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
                LOCATION_ID,
                renderer = { renderTemporaryLocation(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    // LOCATIONは位置と向きを別々の入力値へ分解せず、既存の
                    // PositionSpec/FacingSpec編集画面を子画面として組み合わせます。
                    "position" to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(positionRoute(context.route, "temporary_location_position")),
                        )
                    },
                    "facing" to MenuActionHandler { context ->
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(facingRoute(context.route, "temporary_location_facing")),
                        )
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                VALUE_SOURCE_ID,
                renderer = { renderValueSource(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val field = context.route.payload[VALUE_FIELD]
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val source = context.payload["source"]
                            ?.let { runCatching { CommandValueSource.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val node = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!CommandSettingsModel.supportsTemporaryValueReference(node, field)) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        val currentSource = CommandSettingsModel.temporaryValueSource(node, field)
                        if (source == CommandValueSource.TEMPORARY) {
                            if (currentSource != CommandValueSource.TEMPORARY) {
                                // 一時変数は、方式選択と名前入力を分離します。最初のクリックで
                                // 直接値を消して参照方式を選び、二回目のクリックで初めて
                                // 名前を確定させるため、未入力の参照を設定済み扱いにしません。
                                if (!updateNode(context.player, context.route, configuredFields = emptySet()) {
                                        CommandSettingsModel.selectTemporaryValueSource(it, field)
                                    }) {
                                    return@MenuActionHandler MenuActionResult.Rejected(
                                        KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                                    )
                                }
                                return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                            }
                            showTemporaryReferenceDialog(
                                context.player,
                                context.route,
                                CommandSettingsModel.temporaryValueReference(node, field).orEmpty(),
                                "typed-value-$field",
                            ) { command, name ->
                                CommandSettingsModel.setTemporaryValueReference(command, field, name)
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }

                        val returnRoute = settingsRoute(context.route, node.id)
                        if (!updateNode(context.player, context.route, configuredFields = emptySet()) {
                                CommandSettingsModel.selectLiteralValueSource(it, field)
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        when (field) {
                            "item" -> {
                                val scriptId = scriptId(returnRoute) ?: return@MenuActionHandler MenuActionResult.Ignored
                                plugin.itemSelection.begin(context.player, scriptId, node.id, returnRoute)
                            }
                            "block" -> {
                                return@MenuActionHandler setHeldBlock(
                                    context.player,
                                    returnRoute,
                                    MenuUpdate.Replace(returnRoute),
                                )
                            }
                            "sound", "effect" -> showFieldDialog(context.player, returnRoute, field, node)
                            else -> return@MenuActionHandler MenuActionResult.Ignored
                        }
                        MenuActionResult.Success(MenuUpdate.None)
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
                        if (settingsFields(node).none { it.key == field }) {
                            // 依存値の変更後に残った古い設定タブからの操作を受け付けず、
                            // 表示と同じ共通フィールド集合を保存入口でも適用します。
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (CommandSettingsModel.supportsTemporaryValueReference(node, field)) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(valueSourceRoute(context.route, field)),
                            )
                        }
                        if (field == "item" && (node.type == CommandType.GIVE_ITEM ||
                                (node.type == CommandType.ENTITY_ACTION && node.string("action", "ride") == "equip") ||
                                node.type == CommandType.TEMP_SET)) {
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
                                    if (it.string("soundScope", "POSITION") == "POSITION") "WORLD" else "POSITION",
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
                        if (field == "name" && node.type == CommandType.TEMP_SET) {
                            showVariableNameDialog(context.player, context.route, node.string("name"))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field == "tempType" && node.type == CommandType.TEMP_SET) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, TEMP_TYPE_ID))
                            )
                        }
                        if (field == "value" && node.type == CommandType.TEMP_SET) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, VARIABLE_VALUE_ID)),
                            )
                        }
                        if (field == "entity" && node.type == CommandType.TEMP_SET) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(targetRoute(context.route, "temporary_entity")),
                            )
                        }
                        if (field == "location" && node.type == CommandType.TEMP_SET) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(locationRoute(context.route)),
                            )
                        }
                        if (field == "soundParameters" && node.type == CommandType.TEMP_SET) {
                            showSoundParametersDialog(context.player, context.route, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field == "block" && node.type == CommandType.TEMP_SET) {
                            return@MenuActionHandler setHeldBlock(context)
                        }
                        if (field in setOf(
                                "count", "seconds", "text", "subtitle", "customName", "itemData", "value",
                                 "entity", "tags", "tag", "sound",
                                "soundParameters", "effect", "level", "intensity", "volume", "pitch",
                                "x", "y", "z", "entityId", "item",
                            )) {
                            showFieldDialog(context.player, context.route, field, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val target = when {
                            field == "destination" -> positionRoute(context.route, "destination")
                            field == "destinationFacing" -> facingRoute(context.route, "destination_facing")
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
                            field == "summonPosition" -> positionRoute(context.route, "summon_position")
                            field == "position" && node.type == CommandType.CONDITION ->
                                positionRoute(context.route, "condition_position")
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
                            CommandDialogSpecs.conditionValueSpec(node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored),
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
                     "count" to setVariableValue("${'$'}{${SystemVariableNames.CURRENT_LOOP_COUNT}}"),
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
                TEMP_TYPE_ID,
                renderer = { renderTemporaryTypes(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val type = context.payload["tempType"]
                            ?.let(TemporaryVariableType::parse)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.changeTemporaryType(it, type)
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
                                    GraphLayoutFailureFeedback.operationMessage(context.player, failure),
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
        val continuationId = route.payload[CONTINUATION_ID]?.takeIf(String::isNotBlank)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val insideFor = script?.graph?.let {
            GraphEditor.isInsideFor(it, sourceId, edge ?: GraphEditor.Edge.ENTRY)
        } == true
        val category = CommandCategory.fromRoute(route.payload[PICKER_CATEGORY])
        val types = CommandPickerTypePolicy.types(
            category = category,
            mergeAvailable = GraphEditor.canAppendMerge(script?.graph, mergeConditionId, continuationId),
            insideForBody = insideFor,
        )
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
        CommandPickerLayoutPolicy.emptyItemSlots(types.size).forEach { slot ->
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
        disabledDescription: List<String>? = null,
    ): MenuElement = KcGui.menuEntry(
        player = player,
        slot = slot,
        material = if (enabled) material else DisabledChoiceVisualPolicy.material,
        name = name,
        style = if (enabled) style else DisabledChoiceVisualPolicy.nameStyle,
        description = DisabledChoiceVisualPolicy.hoverLines(
            enabled = enabled,
            normal = description
                ?: KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_CHOICE_DESCRIPTION, mapOf("value" to name)),
            disabled = disabledDescription,
        ),
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
        // 6行レイアウトに合わせ、選択肢があるべき場所が空欄になっている箇所は白の板ガラスで埋めます。
        run {
            val allKeys = EditorMenuLayout.fields(node.type, node).map(EditorField::key)
            val allSlots = runCatching { CommandSettingsSlotPolicy.slots(node.type, allKeys) }.getOrDefault(emptyList())
            val hiddenSlots = allSlots.filter { it !in slots }
            hiddenSlots.forEach { slot ->
                elements += MenuElement(
                    slot,
                    KcGui.elements.decoration(Material.WHITE_STAINED_GLASS_PANE),
                    GuiElementRole.DECORATION,
                )
            }
        }
        elements += backElement(player, CommandSettingsSlotPolicy.backSlot(node.type, fields.size))
        return InventoryMenuView(
            menuSize,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_COMMAND_SETTINGS_NAMED, mapOf("command" to KcI18n.text(player, node.type.key)))),
            elements,
        )
    }

    /**
     * ITEM／BLOCK／SOUND／EFFECTの設定元を選ぶ画面です。
     *
     * 直接値と一時変数参照を同じ画面へ集約し、通常値を入力する既存画面は
     * 選択後の次の導線として再利用します。TEMP_SETの値入力はここへ入らず、
     * 一時変数そのものを定義する既存画面を使い続けます。
     */
    private fun renderValueSource(player: Player, route: MenuRoute): InventoryMenuView {
        val field = route.payload[VALUE_FIELD]
        val node = node(route)
        if (field == null || node == null || !CommandSettingsModel.supportsTemporaryValueReference(node, field)) {
            return InventoryMenuView(
                45,
                KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_COMMAND_SETTINGS)),
                listOf(backElement(player)),
            )
        }
        val selected = CommandSettingsModel.temporaryValueSource(node, field) ?: CommandValueSource.LITERAL
        val options = listOf(CommandValueSource.LITERAL, CommandValueSource.TEMPORARY)
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, source ->
            val lines = typedValueSourceLines(player, field, source)
            choiceElement(
                player = player,
                slot = layout.itemSlots[index],
                material = if (source == selected) Material.CYAN_TERRACOTTA else Material.LIGHT_GRAY_CONCRETE,
                name = lines.joinToString(" "),
                actionId = "select",
                payload = mapOf("source" to source.name),
                description = lines,
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(
                KcI18n.text(
                    player,
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_COMMAND_SETTINGS_NAMED,
                    mapOf("command" to KcI18n.text(player, node.type.key)),
                ),
            ),
            elements,
        )
    }

    private fun renderTarget(player: Player, route: MenuRoute): InventoryMenuView {
        // 実行モデルの細分類（最も近い／周囲／全員など）は大分類の詳細設定へ
        // まとめ、ここでは仕様上の対象分類だけを表示します。
        val options = listOf(
            Triple(TargetCategory.PLAYER, Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER)),
            Triple(TargetCategory.NON_PLAYER_ENTITY, Material.ARMOR_STAND, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY)),
            Triple(TargetCategory.TEMPORARY, Material.REPEATER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                option.third,
                "select",
                mapOf("category" to option.first.name),
                enabled = true,
            )
        }.toMutableList()
        selectedTargetSpec(route)?.takeIf { CommandSettingsModel.targetSupportsDetailedFilters(it.kind) }?.let { spec ->
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
        // （プレイヤー種別にentityType、エンティティの種類にgameModeは解決しないため）。
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
        val positionRole = CommandSettingRole.fromRoute(route.payload[ROLE])
        val currentNode = node(route)
        // 実行状態の上書きは編集画面へ持ち込まず、ここではコマンド固有の
        // 位置指定と一時変数参照だけを提示します。既存のCAPTURED値は
        // 実行モデルの入力として扱いますが、新規設定の入口には出しません。
        val layout = ChoiceMenuLayoutPolicy.layout(if (destination) 3 else 4)
        val elements = if (destination) {
            listOf(
                Triple(PositionKind.COORDINATES, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES_SET)),
                Triple(PositionKind.TARGET, Material.ENDER_PEARL, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_OTHER_ENTITY)),
                Triple(PositionKind.TEMPORARY, Material.REPEATER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE)),
            ).mapIndexed { index, (kind, material, label) ->
                choiceElement(
                    player,
                    layout.itemSlots[index],
                    material,
                    label,
                    if (kind == PositionKind.TARGET) "target" else "select",
                    if (kind == PositionKind.TARGET) emptyMap() else mapOf("kind" to kind.name),
                )
            }.toMutableList()
        } else {
            val options = listOf(
                Triple(PositionKind.DISK, Material.COMMAND_BLOCK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION)),
                Triple(PositionKind.MYWORLD_SPAWN, Material.RESPAWN_ANCHOR, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN)),
                Triple(PositionKind.COORDINATES, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES)),
                Triple(PositionKind.TEMPORARY, Material.REPEATER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE)),
            )
            options.mapIndexed { index, option ->
                choiceElement(player, layout.itemSlots[index], option.second, option.third,
                    "select", mapOf("kind" to option.first.name),
                    enabled = currentNode?.let {
                        CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(it, positionRole, option.first)
                    } ?: true,
                    disabledDescription = if (option.first == PositionKind.DISK) {
                        listOf(CommandSettingAvailabilityPolicy.CONTROL_BLOCK_POSITION_DISABLED_HOVER)
                    } else null,
                )
            }.toMutableList()
        }
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, if (destination) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_POSITION_DESTINATION_TITLE else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION)), elements)
    }

    /**
     * LOCATIONの親画面です。位置と向きを同じ値へ直書きさせず、既存の共通
     * PositionSpec/FacingSpec編集画面へ分岐させることで、通常コマンドと一時値の
     * 入力習慣・検証・参照元を一致させます。
     */
    private fun renderTemporaryLocation(player: Player, route: MenuRoute): InventoryMenuView {
        val current = node(route)
        val options = listOf(
            DetailOption(
                Material.COMPASS,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                "position",
                current?.temporaryLocationPositionSpec?.kind?.let(::displayPosition) ?: displayUnset(),
            ),
            DetailOption(
                Material.SPYGLASS,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING,
                "facing",
                current?.temporaryLocationFacingSpec?.kind?.let(::displayFacing) ?: displayUnset(),
            ),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.material,
                KcI18n.text(player, option.nameKey),
                option.action,
                dataLabel = KcI18n.text(player, option.nameKey),
                dataValue = option.value.render(player),
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION)),
            elements,
        )
    }

    private fun renderFacing(player: Player, route: MenuRoute): InventoryMenuView {
        val destination = route.payload[ROLE] == CommandSettingRole.DESTINATION_FACING.routeValue
        val options = buildList {
            add(Triple(FacingKind.CAPTURED, Material.SPYGLASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_FACING)))
            if (destination) {
                add(Triple(FacingKind.TARGET, Material.TARGET, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET)))
            }
            add(Triple(FacingKind.COORDINATES, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES)))
            add(Triple(FacingKind.MYWORLD_SPAWN, Material.RESPAWN_ANCHOR, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN)))
            add(Triple(FacingKind.ROTATION, Material.REPEATER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMERIC)))
            add(Triple(FacingKind.TEMPORARY, Material.REPEATER, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE)))
        }
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
                DetailOption(Material.TARGET, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, "target", node.targetSpec?.kind?.let(::displayTarget) ?: displayUnset()),
            )
            ConditionKind.PLAYER_STATE -> listOf(
                DetailOption(Material.TARGET, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET, "target", node.targetSpec?.kind?.let(::displayTarget) ?: displayUnset()),
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
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONDITION_POSITION,
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

    /** ブロックは自由入力や任意スロット選択を許さず、メインハンドから取得します。空手はAIRです。 */
    private fun setHeldBlock(context: MenuActionContext): MenuActionResult =
        setHeldBlock(context.player, context.route)

    /**
     * ブロック設定の保存先ルートを呼び出し元から受け取ります。
     *
     * 設定元画面から直接設定した場合も、保存後に設定一覧へ戻せるようにするため、
     * Inventoryの現在表示ルートと保存対象ルートを分離します。上書き確認を開く
     * 場合も同じrouteを渡し、確認後の復帰先を一致させます。
     */
    private fun setHeldBlock(
        player: Player,
        route: MenuRoute,
        successUpdate: MenuUpdate = MenuUpdate.Refresh,
    ): MenuActionResult {
        val held = player.inventory.itemInMainHand
        val blockId = HeldBlockSettingPolicy.materialId(held.type)
        if (blockId == null) {
            player.sendMessage("§cメインハンドにブロックを持ってください。")
            return MenuActionResult.Ignored
        }
        val currentNode = node(route) ?: return MenuActionResult.Ignored
        if (HeldSettingOverwritePolicy.requiresConfirmation(currentNode.string("block"))) {
            // Dialogへ渡す値と確認時の保存世代を固定します。確認中に別の編集が
            // 入った場合は古い設定を上書きせず、保存失敗としてDialogへ戻します。
            showBlockOverwriteDialog(
                player,
                route,
                blockId,
                script(route)?.revision,
            )
            return MenuActionResult.Success(MenuUpdate.None)
        }
        if (!updateNode(player, route) {
                CommandSettingsModel.setParameter(it, "block", blockId)
            }) {
            return MenuActionResult.Rejected(
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
            )
        }
        return MenuActionResult.Success(successUpdate)
    }

    /** インベントリGUIの既存ブロック設定を確認してから、クリック時の値を保存します。 */
    private fun showBlockOverwriteDialog(
        player: Player,
        route: MenuRoute,
        blockId: String,
        expectedRevision: Long?,
    ) {
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "block-overwrite",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_BLOCK_OVERWRITE_TITLE),
                body = listOf(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_BLOCK_OVERWRITE_WARN),
                ),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { _, _ ->
                        if (!updateNode(
                                player,
                                route,
                                configuredFields = setOf("block"),
                                expectedRevision = expectedRevision,
                            ) { node ->
                                CommandSettingsModel.setParameter(node, "block", blockId)
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

    /**
     * 「一時変数を設定」の型選択です。8型を一覧し、保存は tempType へ行います。
     * 表示キーは既存の流用に留め、新規キーを増やしません。
     */
    private fun renderTemporaryTypes(player: Player): InventoryMenuView {
        val options = listOf(
            Triple(TemporaryVariableType.NUMBER, Material.COMPARATOR, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMBER)),
            Triple(TemporaryVariableType.STRING, Material.WRITABLE_BOOK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT)),
            Triple(TemporaryVariableType.LOCATION, Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION)),
            Triple(TemporaryVariableType.ITEM, Material.CHEST, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM)),
            Triple(TemporaryVariableType.BLOCK, Material.BRICKS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK)),
            Triple(TemporaryVariableType.ENTITY, Material.ARMOR_STAND, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY)),
            Triple(TemporaryVariableType.SOUND, Material.NOTE_BLOCK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND)),
            Triple(TemporaryVariableType.EFFECT, Material.POTION, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EFFECT)),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(player, layout.itemSlots[index], option.second, option.third,
                "select", mapOf("tempType" to option.first.name))
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
                body = CommandDialogSpecs.timerBody(player),
                inputs = listOf(CommandDialogSpecs.timerInput(player, seconds)),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ENABLE), MenuDialogHandler { _, response ->
                    val rawValue = response.textValue("seconds").trim()
                    val validationError = CommandDialogSpecs.timerSeconds.validateInput(rawValue)
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(
                            KcI18n.component(player, validationError)
                        )
                    }
                    val value = requireNotNull(CommandValueRules.parsePositiveInt(rawValue))
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
                body = CommandDialogSpecs.body(player, spec),
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

    /**
     * 構造化設定から一時変数名を選ぶ共通入力です。
     *
     * Target／Position／Facingはparamsへ名前を埋め込まず、それぞれのSpecへ
     * 保存します。三つの構造化設定画面が個別に名前入力を実装すると、正規化・世代確認・
     * 取消時の復帰がずれるため、保存処理だけを呼び出し側から受け取ります。
     */
    private fun showTemporaryReferenceDialog(
        player: Player,
        route: MenuRoute,
        currentName: String,
        id: String,
        save: (CommandNode, String) -> Unit,
    ) {
        val spec = CommandDialogSpecs.variableName
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = id,
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                body = CommandDialogSpecs.body(player, spec),
                inputs = listOf(CommandDialogSpecs.input(player, "name", currentName, spec)),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { _, response ->
                        val name = response.textValue("name").trim()
                        val validationError = spec.validateInput(name)
                        if (validationError != null) {
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                KcI18n.component(player, validationError),
                            )
                        }
                        if (!updateNode(player, route) { node -> save(node, name) }) {
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

    private fun showTemporaryTargetDialog(
        player: Player,
        route: MenuRoute,
        role: CommandSettingRole?,
        current: TargetSpec?,
    ) = showTemporaryReferenceDialog(
        player,
        route,
        current?.takeIf { it.kind == TargetKind.TEMPORARY }?.tempName.orEmpty(),
        "temporary-target-name",
    ) { node, name ->
        CommandSettingsModel.setTargetSpec(node, role, TargetSpec(TargetKind.TEMPORARY, tempName = name))
    }

    private fun showTemporaryPositionDialog(player: Player, route: MenuRoute) {
        val role = CommandSettingRole.fromRoute(route.payload[ROLE])
        val current = node(route)?.let { CommandSettingsModel.positionSpec(it, role) }
        showTemporaryReferenceDialog(
            player,
            route,
            current?.takeIf { it.kind == PositionKind.TEMPORARY }?.tempName.orEmpty(),
            "temporary-position-name",
        ) { command, name ->
            CommandSettingsModel.setPositionSpec(command, role, PositionSpec(PositionKind.TEMPORARY, tempName = name))
        }
    }

    private fun showTemporaryFacingDialog(player: Player, route: MenuRoute) {
        val role = CommandSettingRole.fromRoute(route.payload[ROLE]) ?: return
        val current = node(route)?.let { CommandSettingsModel.facingSpec(it, role) }
        showTemporaryReferenceDialog(
            player,
            route,
            current?.takeIf { it.kind == FacingKind.TEMPORARY }?.tempName.orEmpty(),
            "temporary-facing-name",
        ) { command, name ->
            CommandSettingsModel.setFacingSpec(command, FacingSpec(FacingKind.TEMPORARY, tempName = name), role)
        }
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
            "entity" -> "minecraft:pig"
            "sound" -> "minecraft:block.note_block.harp"
            "volume", "pitch" -> "1.0"
            "effect" -> "minecraft:speed"
            "level" -> "1"
            "intensity" -> "1.0"
            "shakeType" -> "positional"
            "slot" -> "HAND"
            "x", "y", "z" -> "0.0"
            "entityId", "item", "block" -> ""
            else -> return
        }
        val spec = CommandDialogSpecs.field(node, field) ?: return
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
                    // 入力値を引き継いだ新しい入力画面を同期表示するため、元の
                    // 入力画面を外部入力へ戻す処理は行いません。
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
                body = CommandDialogSpecs.body(player, spec),
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
                body = CommandDialogSpecs.soundParametersBody(player),
                inputs = CommandDialogSpecs.soundParametersInputs(player, volume, pitch),
                confirm = MenuDialogButton(
                    KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                    MenuDialogHandler { _, response ->
                        val volumeValue = CommandDialogSpecs.normalize("volume", response.textValue("volume"))
                        val pitchValue = CommandDialogSpecs.normalize("pitch", response.textValue("pitch"))
                        val volumeError = volumeSpec.validateInput(volumeValue)
                        val pitchError = pitchSpec.validateInput(pitchValue)
                        if (volumeError != null || pitchError != null) {
                            val messages = buildList {
                                if (volumeError != null) {
                                    add(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_VOLUME_BODY))
                                }
                                if (pitchError != null) {
                                    add(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FIELD_PITCH_BODY))
                                }
                            }
                            return@MenuDialogHandler MenuActionResult.Rejected(
                                Component.text(messages.joinToString("\n"), NamedTextColor.RED),
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
                    node.string("mode", "tellraw"),
                ),
                inputs = CommandDialogSpecs.durationInputs(player, fadeIn, stay, fadeOut),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val rawValues = listOf("fadeInSeconds", "staySeconds", "fadeOutSeconds").associateWith { key -> response.textValue(key).trim() }
                    val validationError = rawValues.values
                        .mapNotNull(durationSpec::validateInput)
                        .firstOrNull()
                    if (validationError != null) return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    if (!updateNode(player, route) { command ->
                        CommandSettingsModel.setParameters(
                            command,
                            rawValues,
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
                body = CommandDialogSpecs.body(player, spec),
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
        val role = CommandSettingRole.fromRoute(route.payload[ROLE]) ?: return
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
        val role = CommandSettingRole.fromRoute(route.payload[ROLE]) ?: return
        val current = node(route)?.let { CommandSettingsModel.facingSpec(it, role) }
        val location = player.location
        CCSystem.getAPI().getMenuDialogService().show(
            player,
            MenuDialogRequest(
                owner = SequenceEditorMenu.OWNER,
                id = "facing-rotation",
                title = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_ROTATION_TITLE),
                body = CommandDialogSpecs.rotationBody(player),
                inputs = CommandDialogSpecs.rotationInputs(
                    player,
                    current?.yaw ?: location.yaw,
                    current?.pitch ?: location.pitch,
                ),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val rawValues = listOf("yaw", "pitch").associateWith { key -> response.textValue(key).trim() }
                    val validationError = rawValues.entries
                        .mapNotNull { (key, raw) -> CommandDialogSpecs.rotationSpec(key).validateInput(raw) }
                        .firstOrNull()
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    }
                    val yaw = CommandDialogSpecs.finiteFloat(rawValues.getValue("yaw"))
                    val pitch = CommandDialogSpecs.finiteFloat(rawValues.getValue("pitch"))
                    if (yaw == null || pitch == null) {
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
                body = CommandDialogSpecs.coordinateBody(player),
                inputs = CommandDialogSpecs.coordinateInputs(player, currentX, currentY, currentZ),
                confirm = MenuDialogButton(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM), MenuDialogHandler { _, response ->
                    val rawValues = listOf("x", "y", "z").associateWith { key -> response.textValue(key).trim() }
                    val validationError = rawValues.entries
                        .mapNotNull { (key, raw) -> CommandDialogSpecs.coordinateSpec(key).validateInput(raw) }
                        .firstOrNull()
                    if (validationError != null) {
                        return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                    }
                    val x = CommandDialogSpecs.finiteDouble(rawValues.getValue("x"))
                    val y = CommandDialogSpecs.finiteDouble(rawValues.getValue("y"))
                    val z = CommandDialogSpecs.finiteDouble(rawValues.getValue("z"))
                    if (x == null || y == null || z == null) {
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
                body = CommandDialogSpecs.rangeBody(player),
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
                CommandDialogSpecs.input(
                    player = player,
                    id = "minimum",
                    initial = currentSpec.minimumDistance?.toString().orEmpty(),
                    spec = inputSpec,
                    label = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE),
                ),
                CommandDialogSpecs.input(
                    player = player,
                    id = "maximum",
                    initial = currentSpec.maximumDistance?.toString().orEmpty(),
                    spec = inputSpec,
                    label = KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE),
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
                        "limit" -> currentSpec.copy(limit = CommandValueRules.parsePositiveInt(value))
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
                body = CommandDialogSpecs.body(player, inputSpec),
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
                            minimumDistance = minimumRaw?.let(CommandDialogSpecs::finiteDouble),
                            maximumDistance = maximumRaw?.let(CommandDialogSpecs::finiteDouble),
                        )
                    } else {
                        val raw = response.textValue(parameter)
                            .takeIf { it.trim().isNotEmpty() }
                            ?.let { CommandDialogSpecs.normalize(parameter, it) }
                        val validationError = raw?.let(inputSpec::validateInput)
                        if (validationError != null) {
                            return@MenuDialogHandler MenuActionResult.Rejected(KcI18n.component(player, validationError))
                        }
                        val integerValue = raw?.let(CommandValueRules::parsePositiveInt)
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
        expectedRevision: Long? = null,
        change: (CommandNode) -> Unit,
    ): Boolean {
        val context = CommandSettingContext.from(route) ?: return false
        return runCatching {
            CommandSettingsModel.updateNode(
                plugin,
                context,
                configuredFields,
                editorId = player.uniqueId,
                expectedRevision = expectedRevision,
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
        private const val VALUE_SOURCE_ID = "typed_value_source"
        private const val SETTINGS_ID = "command_settings"
        private const val TIMER_ID = "timer_settings"
        private const val CONDITION_KIND_ID = "condition_kind"
        private const val CONDITION_DETAIL_ID = "condition_detail"
        private const val VARIABLE_TYPE_ID = "variable_type"
        private const val VARIABLE_OPERATION_ID = "variable_operation"
        private const val VARIABLE_CHANGE_MODE_ID = "variable_change_mode"
        private const val VARIABLE_VALUE_ID = "variable_value"
        private const val TEMP_TYPE_ID = "temp_type"
        private const val ENTITY_EQUIPMENT_SLOT_ID = "entity_equipment_slot"
        private const val DISPLAY_MODE_ID = "display_mode"
        private const val BLOCK_OPERATION_ID = "block_operation"
        private const val DELETE_ID = "delete_command"
        private const val TARGET_ID = "target_settings"
        private const val TARGET_FILTER_ID = "target_filters"
        private const val POSITION_ID = "position_settings"
        private const val FACING_ID = "facing_settings"
        private const val LOCATION_ID = "location_settings"
        private const val SCRIPT_ID = "scriptId"
        private const val NODE_ID = "nodeId"
        private const val SOURCE_ID = "sourceId"
        private const val EDGE = "edge"
        private const val MERGE_CONDITION_ID = "mergeConditionId"
        private const val CONTINUATION_ID = "continuationId"
        private const val PICKER_CATEGORY = "pickerCategory"
        private const val ROLE = "role"
        private const val VALUE_FIELD = "valueField"

        fun typeRoute(
            current: MenuRoute,
            sourceId: UUID?,
            edge: GraphEditor.Edge,
            mergeConditionId: UUID? = null,
            continuationId: UUID? = null,
        ) =
            requireNotNull(EditorSession.from(current)).route(
                SequenceEditorMenu.OWNER,
                PICKER_ID,
                mapOf(
                    SOURCE_ID to sourceId?.toString().orEmpty(),
                    EDGE to edge.name,
                    MERGE_CONDITION_ID to mergeConditionId?.toString().orEmpty(),
                    CONTINUATION_ID to continuationId?.toString().orEmpty(),
                ),
            )

        fun settingsRoute(current: MenuRoute, nodeId: UUID) =
            requireNotNull(EditorSession.from(current)).route(
                SequenceEditorMenu.OWNER,
                SETTINGS_ID,
                mapOf(NODE_ID to nodeId.toString()),
            )

        private fun valueSourceRoute(current: MenuRoute, field: String) =
            current.copy(
                id = VALUE_SOURCE_ID,
                payload = current.payload + (VALUE_FIELD to field),
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

        private fun facingRoute(route: MenuRoute, role: String = "destination_facing") =
            route.copy(id = FACING_ID, payload = route.payload + (ROLE to role))

        private fun locationRoute(route: MenuRoute) = route.copy(id = LOCATION_ID)

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
    /** 現在値をGUI上で意味ごとの行へ分けるための表示単位です。 */
    data class TimingRow(
        val label: String,
        val value: String,
    )

    /** Gesture GUIの「表示時間」タブに3つの現在値を意味付きで表示します。 */
    data class Timing(
        val fadeInSeconds: String,
        val staySeconds: String,
        val fadeOutSeconds: String,
    ) : DisplayValue {
        fun rows(player: Player): List<TimingRow> = listOf(
            TimingRow(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_IN),
                fadeInSeconds,
            ),
            TimingRow(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STAY),
                staySeconds,
            ),
            TimingRow(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_OUT),
                fadeOutSeconds,
            ),
        )
    }

    /** LOCATIONの位置・向きを、一つの設定欄で現在値として表示します。 */
    data class Location(
        val position: DisplayValue?,
        val facing: DisplayValue?,
    ) : DisplayValue

    fun render(player: Player): String = when (this) {
        is Literal -> value
        is Localized -> KcI18n.text(player, key)
        is Timing -> rows(player).joinToString(" / ") { "${it.label}=${it.value}" }
        is Location -> buildList {
            position?.let {
                add("${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION)}=${it.render(player)}")
            }
            facing?.let {
                add("${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING)}=${it.render(player)}")
            }
        }.joinToString(" / ").ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }
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
    /**
     * 同じJSONパラメータ名でもコマンドごとに意味が異なる項目は、専用の表示キーを使います。
     * 汎用キーを流用すると、召喚名が変数名として説明されるなど、設定対象と説明文がずれます。
     */
    fun fields(type: CommandType, node: CommandNode? = null): List<EditorField> {
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
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESTINATION_FACING,
                Material.SPYGLASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DESTINATION_FACING,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DESTINATION_FACING,
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
            ) { displayTypedValue(it, "item") },
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
            ) { displayTypedValue(it, "item") },
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
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG_OPERATION,
                Material.NAME_TAG,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TAG_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TAG_OPERATION,
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
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_DISPLAY_NAME,
                Material.NAME_TAG,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY_DISPLAY_NAME,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY_DISPLAY_NAME,
            ),
            field("tags", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAGS, Material.NAME_TAG),
            field(
                "summonPosition",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SUMMON_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SUMMON_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SUMMON_POSITION,
            ) { it.summonPositionSpec?.kind?.let(::displayPosition) ?: displayUnset() },
        )
        CommandType.PLAY_SOUND -> listOf(
            field("sound", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND, Material.NOTE_BLOCK) {
                displayTypedValue(it, "sound")
            },
            field(
                "soundParameters",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_PARAMETERS,
                Material.JUKEBOX,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_PARAMETERS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_PARAMETERS,
            ) { displaySoundParameters(it.string("volume", "1.0"), it.string("pitch", "1.0")) },
            field("soundScope", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_SCOPE, Material.GLOBE_BANNER_PATTERN,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_SCOPE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_SCOPE,
            ) { displaySoundScope(it.string("soundScope", "POSITION")) },
            field("soundPosition", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_POSITION, Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_POSITION,
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
            field("effect", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EFFECT, Material.POTION) {
                displayTypedValue(it, "effect")
            },
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
            ) { displayTypedValue(it, "block") },
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
            field("inverted", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_INVERTED, Material.REDSTONE_TORCH) { displayConditionInversion(it.boolean("inverted")) },
            field("kind", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONDITION_KIND, Material.COMPARATOR) { displayCondition(it.string("kind")) },
            field("condition", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONDITION_VALUE, Material.TARGET) { displayCondition(it.string("kind")) },
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
        CommandType.TEMP_SET -> tempSetFields(node ?: CommandNode(type = CommandType.TEMP_SET))
        CommandType.MERGE, CommandType.FOR_END, CommandType.BREAK, CommandType.CONTINUE -> emptyList()
        CommandType.FOR_START -> listOf(
            field(
                "count",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_REPEAT_COUNT,
                Material.REPEATER,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_REPEAT_COUNT,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_REPEAT_COUNT,
            ),
        )
        }
        return fields
    }

    /**
     * 「一時変数を設定」の編集項目を、一時型ごとに切り替えます。
     *
     * NUMBER・STRING は共通値欄、LOCATIONは位置・向きを共通設定画面へ委譲し、
     * ITEM・BLOCKはメインハンド、ENTITYは共通対象選択、SOUND・EFFECTは共通の
     * ID／数値入力仕様を使います。型自体はtempType欄で変更し、再設定は上書きとして扱います。
     */
    private fun tempSetFields(node: CommandNode): List<EditorField> = buildList {
        add(field("name", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, Material.NAME_TAG))
        add(field("tempType", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TYPE, Material.STRUCTURE_VOID) {
            displayTemporaryType(it.string("tempType"))
        })
        when (TemporaryVariableType.parse(node.string("tempType", TemporaryVariableType.NUMBER.name))
            ?: TemporaryVariableType.NUMBER) {
            TemporaryVariableType.NUMBER, TemporaryVariableType.STRING ->
                add(field("value", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, Material.COMPARATOR) {
                    displayVariableValue(it.string("value"))
                })
            TemporaryVariableType.LOCATION -> add(field(
                "location",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) { displayTemporaryLocation(it) })
            TemporaryVariableType.ITEM ->
                // 一時アイテムは「付与アイテム」ではなく、実行内値として設定する項目です。
                // コマンド固有の説明キー解決へ流すと item の意味が曖昧になるため、
                // 一時値用の汎用説明をここで明示します。
                add(field(
                    "item",
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM,
                    Material.CHEST,
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE,
                ))
            TemporaryVariableType.BLOCK ->
                add(field("block", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK, Material.BRICKS))
            TemporaryVariableType.ENTITY ->
                add(field(
                    "entity",
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                    Material.ARMOR_STAND,
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY,
                ) { it.temporaryEntityTargetSpec?.kind?.let(::displayTarget) ?: displayUnset() })
            TemporaryVariableType.SOUND -> {
                add(field("sound", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND, Material.NOTE_BLOCK))
                // PLAY_SOUNDと同じく、音量・ピッチは1つの共通入力画面へまとめます。
                add(field(
                    "soundParameters",
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_PARAMETERS,
                    Material.JUKEBOX,
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_PARAMETERS,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_PARAMETERS,
                ) { displaySoundParameters(it.string("volume", "1.0"), it.string("pitch", "1.0")) })
            }
            TemporaryVariableType.EFFECT -> {
                add(field("effect", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EFFECT, Material.POTION))
                add(field("level", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_LEVEL, Material.COMPARATOR))
                // seconds は TEMP_SET ではエフェクトの持続時間だけを表すため、
                // WAIT などの待機時間向け説明へ誤って依存しないよう明示します。
                add(field(
                    "seconds",
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SECONDS,
                    Material.CLOCK,
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EFFECT_SECONDS,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_EFFECT_SECONDS,
                ))
            }
        }
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
        "position" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION
        "facing" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_FACING to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_FACING
        "diskId" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISKID to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISKID
        "scope" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SCOPE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SCOPE
        "name" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_NAME to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_NAME
        "type" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TYPE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TYPE
        "tempType" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TYPE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TYPE
        "operation" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION
        "value" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE
        else -> error("未定義のエディターフィールドです: $key")
    }
}

private fun displayLiteral(value: Any?): DisplayValue = value?.toString()?.takeIf(String::isNotBlank)
    ?.let(DisplayValue::Literal) ?: displayUnset()

/**
 * 実行値を直接設定するか一時変数から解決するかを、現在値表示へ反映します。
 *
 * 一時変数モードで直接値だけを表示すると、画面に見えている値と実行値が一致
 * しません。参照名を値欄へそのまま出し、設定元の選択結果をInventory／Gesture
 * の両方で同じように確認できるようにします。
 */
private fun displayTypedValue(node: CommandNode, fieldKey: String): DisplayValue =
    CommandSettingsModel.temporaryValueReference(node, fieldKey)?.let(::displayLiteral)
        ?: displayLiteral(node.string(fieldKey))

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

private fun displayTemporaryLocation(node: CommandNode): DisplayValue = DisplayValue.Location(
    position = node.temporaryLocationPositionSpec?.kind?.let(::displayPosition),
    facing = node.temporaryLocationFacingSpec?.kind?.let(::displayFacing),
)

private fun displayUnset() = DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
private fun displayBoolean(value: Boolean) = DisplayValue.Localized(
    if (value) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_ENABLED else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DISABLED,
)

/** 条件の反転は一般的な有効／無効ではなく、評価結果への作用を明示します。 */
private fun displayConditionInversion(value: Boolean) = DisplayValue.Localized(
    if (value) {
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_ON
    } else {
        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CHOICE_INVERT_OFF
    },
)

private fun displayTarget(kind: TargetKind) = DisplayValue.Localized(when (kind) {
    TargetKind.NEAREST_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER
    TargetKind.NEARBY_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS
    TargetKind.ALL_PLAYERS -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS
    TargetKind.RANDOM_PLAYER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RANDOM_PLAYER
    TargetKind.NEAREST_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_ENTITY
    TargetKind.NEARBY_ENTITIES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES
    TargetKind.FIXED_ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
    TargetKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
})

private fun displayPosition(kind: PositionKind) = DisplayValue.Localized(when (kind) {
    PositionKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAPTURED_POSITION
    PositionKind.DISK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION
    PositionKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION
    PositionKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
    PositionKind.MYWORLD_SPAWN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN
    PositionKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES
})

private fun displayFacing(kind: FacingKind) = DisplayValue.Localized(when (kind) {
    FacingKind.CAPTURED -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_FACING
    FacingKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET
    FacingKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
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

/** 一時変数の8型を、既存の表示キーで解決します。新規キーは増やしません。 */
private fun displayTemporaryType(value: String) =
    TemporaryVariableType.parse(value)?.let {
        DisplayValue.Localized(when (it) {
            TemporaryVariableType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMBER
            TemporaryVariableType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT
            TemporaryVariableType.LOCATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION
            TemporaryVariableType.ITEM -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM
            TemporaryVariableType.BLOCK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK
            TemporaryVariableType.ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
            TemporaryVariableType.SOUND -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND
            TemporaryVariableType.EFFECT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EFFECT
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
    "${'$'}{${SystemVariableNames.CURRENT_LOOP_COUNT}}" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT)
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
