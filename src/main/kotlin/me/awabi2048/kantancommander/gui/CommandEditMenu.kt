package me.awabi2048.kantancommander.gui
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys

import com.awabi2048.ccsystem.CCSystem
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.gui.GuiElementRole
import com.awabi2048.ccsystem.api.gui.GuiMenuActionIntent
import com.awabi2048.ccsystem.api.gui.GuiMenuEntryData
import com.awabi2048.ccsystem.api.gui.GuiNameStyle
import com.awabi2048.ccsystem.api.gui.InventoryMenuDefinition
import com.awabi2048.ccsystem.api.gui.InventoryMenuView
import com.awabi2048.ccsystem.api.gui.MenuActionContext
import com.awabi2048.ccsystem.api.gui.MenuActionHandler
import com.awabi2048.ccsystem.api.gui.MenuActionResult
import com.awabi2048.ccsystem.api.gui.MenuCustomFormRequest
import com.awabi2048.ccsystem.api.gui.MenuElement
import com.awabi2048.ccsystem.api.gui.MenuFormButton
import com.awabi2048.ccsystem.api.gui.MenuFormHandler
import com.awabi2048.ccsystem.api.gui.MenuFormInput
import com.awabi2048.ccsystem.api.gui.MenuRoute
import com.awabi2048.ccsystem.api.gui.MenuSimpleFormRequest
import com.awabi2048.ccsystem.api.gui.MenuUpdate
import me.awabi2048.kantancommander.KantanCommanderPlugin
import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.ParticleDisplayMode
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.FireworkSettings
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.VariableType
import me.awabi2048.kantancommander.model.TemporaryVariableType
import me.awabi2048.kantancommander.model.SystemVariableNames
import me.awabi2048.kantancommander.util.KcI18n
import org.bukkit.Material
import org.bukkit.Location
import org.bukkit.Sound
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
                        if (!CommandPresentationPolicy.isSimpleEditable(type)) {
                            // 簡易GUIでは実行コマンドと待機だけを追加できます。
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        val sourceId = context.route.payload[SOURCE_ID]?.takeIf(String::isNotBlank)
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        val edge = context.route.payload[EDGE]?.let {
                            runCatching { GraphEditor.Edge.valueOf(it) }.getOrNull()
                        } ?: GraphEditor.Edge.ENTRY
                        val mergeConditionId = context.route.payload[MERGE_CONDITION_ID]?.takeIf(String::isNotBlank)
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        val continuationId = context.route.payload[CONTINUATION_ID]?.takeIf(String::isNotBlank)
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        // 挿入処理は共通グラフ更新入口へ通します。表示中のscriptを
                        // 直接変更せず、レイアウト検証・保存・配置表示更新を両GUIで同じ
                        // 順序にすることで、挿入経路だけ別の正本状態を作りません。
                        // 簡易GUIでは合流・繰り返し制御を追加できないため、通常挿入だけを行います。
                        val node = runCatching {
                            CommandSettingsModel.updateGraph(plugin, script.id, context.player.uniqueId) { candidateGraph ->
                                GraphEditor.insert(
                                    candidateGraph,
                                    sourceId,
                                    edge,
                                    type,
                                    continuationId = continuationId,
                                )
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
                        // 簡易GUIでは設定値を持たない制御コマンドを追加できないため、
                        // 追加後は必ず設定画面へ進みます。
                        MenuActionResult.Success(MenuUpdate.Replace(settingsRoute(context.route, node.id)))
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
                        val kind = context.payload["kind"]
                            ?.let { runCatching { TargetKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        // 簡易GUIでは3プリセットだけを扱います。複雑な条件指定は行えません。
                        if (kind != TargetKind.NEAREST_PLAYER &&
                            kind != TargetKind.NEARBY_PLAYERS &&
                            kind != TargetKind.ALL_PLAYERS
                        ) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (node(context.route) == null) return@MenuActionHandler MenuActionResult.Ignored
                        // プリセット選択は絞り込み条件を引き継がず、種別の既定値から
                        // 作り直します。簡易GUIでは隠れた条件を表示・編集できないため、
                        // 見えている選択と保存値がずれないことを優先します。
                        if (!updateNode(context.player, context.route) { node ->
                                CommandSettingsModel.setTargetSpec(node, routeRole, TargetSpec(kind))
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
                        MenuActionResult.Success(MenuUpdate.Replace(context.route))
                    },
                    // 「周囲のプレイヤー」の距離だけを数値入力で調整します。
                    "distance" to MenuActionHandler { context ->
                        if (selectedTargetSpec(context.route)?.kind != TargetKind.NEARBY_PLAYERS) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        openTargetDistanceForm(context.player, context.route)
                        MenuActionResult.Success(MenuUpdate.None)
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
                        // 簡易GUIでは制御ブロック位置だけを直接選択します。
                        // 対象の現在位置は「対象」項目から対象選択画面へ進みます。
                        if (kind != PositionKind.DISK) return@MenuActionHandler MenuActionResult.Ignored
                        val currentNode = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                        if (!CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(currentNode, role, kind)) {
                            // 表示後に操作条件が変化しても、保存入口で同じ判定を再実行します。
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (!updateNode(context.player, context.route) { node ->
                            CommandSettingsModel.setPositionSpec(
                                node,
                                role,
                                PositionSpec(kind),
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
                SETTINGS_ID,
                renderer = { renderSettings(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "delete" to MenuActionHandler { context ->
                        val nodeId = context.payload[NODE_ID]
                            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            ?: node(context.route)?.id
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        MenuActionResult.Success(MenuUpdate.Navigate(deleteRoute(context.route, nodeId)))
                    },
                    "field" to MenuActionHandler { context ->
                        val field = context.payload["field"] ?: return@MenuActionHandler MenuActionResult.Ignored
                        val node = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!CommandPresentationPolicy.isSimpleEditable(node.type)) {
                            // 古い経路から制御系ノードの設定画面へ届いた操作は受け付けません。
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (settingsFields(node).none { it.key == field }) {
                            // 依存値の変更後に残った古い設定タブからの操作を受け付けず、
                            // 表示と同じ共通フィールド集合を保存入口でも適用します。
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (CommandSettingsModel.supportsTemporaryValueReference(node, field)) {
                            // 簡易GUIでは一時変数参照を扱いません。直接値へ切り替えて
                            // 下の通常値入力へ進みます。既存の参照は解除されます。
                            if (!updateNode(context.player, context.route, configuredFields = emptySet()) {
                                    CommandSettingsModel.selectLiteralValueSource(it, field)
                                }) {
                                return@MenuActionHandler MenuActionResult.Rejected(
                                    KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                                )
                            }
                        }
                        if (field == "item" && (node.type == CommandType.GIVE_ITEM ||
                                (node.type == CommandType.ENTITY_ACTION && node.string("action", "ride") == "equip"))) {
                            // 付与アイテムはインベントリ内の実物をクリックして選択する
                            // 旧方式へ戻します（インベントリGUIの標準選択導線）。
                            val scriptId = scriptId(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                            plugin.itemSelection.begin(context.player, scriptId, node.id, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field == "operation" && node.type == CommandType.BLOCK_OPERATION) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, BLOCK_OPERATION_ID))
                            )
                        }
                        if (field == "block" && node.type == CommandType.BLOCK_OPERATION) {
                            return@MenuActionHandler setHeldBlock(context)
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
                        if (field == "soundOperation" && node.type == CommandType.PLAY_SOUND) {
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(
                                    it,
                                    "soundOperation",
                                    if (SoundSettings.operation(it) == SoundOperation.PLAY) {
                                        SoundOperation.STOP.name
                                    } else {
                                        SoundOperation.PLAY.name
                                    },
                                )
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        if (field == "soundTargetPosition" && node.type == CommandType.PLAY_SOUND) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, SOUND_TARGET_POSITION_ID))
                            )
                        }
                        if (field == "soundSetting" && node.type == CommandType.PLAY_SOUND) {
                            showSoundSettingForm(context.player, context.route, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field == "particleDisplay" && node.type == CommandType.PARTICLE) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, PARTICLE_DISPLAY_ID))
                            )
                        }
                        if (field == "particle" && node.type == CommandType.PARTICLE) {
                            showParticleForm(context.player, context.route, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field == "geometrySettings" && node.type == CommandType.PARTICLE) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, GEOMETRY_SETTINGS_ID))
                            )
                        }
                        if (field == "fireworkContents" && node.type == CommandType.FIREWORK_LAUNCH) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, FIREWORK_CONTENTS_ID))
                            )
                        }
                        if (field == "fireworkPosition" && node.type == CommandType.FIREWORK_LAUNCH) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(genericPositionRoute(context.route, "firework_position"))
                            )
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
                        if (field == "particleParameters" && node.type == CommandType.PARTICLE) {
                            showParticleParametersForm(context.player, context.route, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field in setOf(
                                "count", "seconds", "text", "subtitle", "customName", "itemData", "value",
                                 "entity", "tags", "tag", "sound",
                                "soundParameters", "particle", "particleData", "particleDensity", "burstHeight",
                                "effect", "level", "intensity", "volume", "pitch",
                                "x", "y", "z", "entityId", "item",
                            )) {
                            showFieldForm(context.player, context.route, field, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (node.type == CommandType.BLOCK_OPERATION &&
                            (field == "position" || field == "from" || field == "to")
                        ) {
                            // ブロック操作の配置位置・範囲は座標の数値入力で指定します。
                            openBlockPositionForm(context.player, context.route, field)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val target = when {
                            field == "destination" -> positionRoute(context.route, "destination")
                            field == "target" || field == "subject" -> targetRoute(context.route, "node_target")
                            field == "other" && node.type == CommandType.ENTITY_ACTION ->
                                targetRoute(context.route, "secondary_target")
                            field == "soundPosition" -> positionRoute(context.route, "sound_position")
                            field == "position" && node.type == CommandType.PARTICLE ->
                                positionRoute(context.route, "particle_position")
                            field == "summonPosition" -> positionRoute(context.route, "summon_position")
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
                SOUND_TARGET_POSITION_ID,
                renderer = { renderSoundTargetPosition(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "target" to MenuActionHandler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(targetRoute(context.route, "node_target")))
                    },
                    "position" to MenuActionHandler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(genericPositionRoute(context.route, "sound_position")))
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                GENERIC_POSITION_ID,
                renderer = { renderGenericPosition(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "disk" to MenuActionHandler { context ->
                        val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) { node ->
                                CommandSettingsModel.setPositionSpec(node, role, PositionSpec(PositionKind.DISK))
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                    "coordinates" to MenuActionHandler { context ->
                        val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        openCoordinatePositionForm(context.player, context.route, role)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                PARTICLE_DISPLAY_ID,
                renderer = { renderParticleDisplay(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "target" to MenuActionHandler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(targetRoute(context.route, "node_target")))
                    },
                    "mode" to MenuActionHandler { context ->
                        val mode = context.payload["mode"]
                            ?.let { runCatching { ParticleDisplayMode.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, ParticleGeometry.PARAM_DISPLAY_MODE, mode.name)
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
                GEOMETRY_SETTINGS_ID,
                renderer = { renderGeometrySettings(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "shape" to MenuActionHandler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(choiceRoute(context.route, PARTICLE_SHAPE_ID)))
                    },
                    "numbers" to MenuActionHandler { context ->
                        val node = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        showGeometryForm(context.player, context.route, node)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "end" to MenuActionHandler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(genericPositionRoute(context.route, "particle_end")))
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                PARTICLE_SHAPE_ID,
                renderer = { renderParticleShapes(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val shape = context.payload["shape"]
                            ?.let { runCatching { ParticleShape.valueOf(it) }.getOrNull() }
                            ?.takeIf { it != ParticleShape.POINT_SCATTER }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, ParticleGeometry.PARAM_SHAPE, shape.name)
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
                FIREWORK_CONTENTS_ID,
                renderer = { renderFireworkContents(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "held" to MenuActionHandler { context ->
                        setHeldFirework(context.player, context.route)
                    },
                    "launch" to MenuActionHandler { context ->
                        launchFireworkPreview(context.player, context.route)
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
                        // 簡易GUIでは制御系コマンドを削除できません。
                        val target = script.graph.nodes[nodeId]
                        if (target != null && !CommandPresentationPolicy.isSimpleEditable(target.type)) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
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
        // 簡易GUIでは実行コマンドと待機だけを固定表示します。カテゴリ切替は
        // 行わず、制御系コマンドの追加はフルGUI（ジェスチャーGUI）へ委ねます。
        val types = CommandPresentationPolicy.SIMPLE_TYPES
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
        if (!CommandPresentationPolicy.isSimpleEditable(node.type)) {
            // 古い経路から制御系ノードの設定画面へ届いた場合は表示のみとし、
            // 設定変更・削除の入口を作りません。
            return InventoryMenuView(
                45,
                KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_COMMAND_SETTINGS)),
                listOf(
                    KcGui.menuEntry(
                        player = player,
                        slot = 22,
                        material = node.type.icon,
                        name = KcI18n.text(player, node.type.key),
                        style = GuiNameStyle.PRIMARY,
                        description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SIMPLE_LOCKED_DESCRIPTION),
                    ),
                    backElement(player),
                ),
            )
        }
        val fields = settingsFields(node)
        val slots = CommandSettingsSlotPolicy.slots(node.type, fields.map(EditorField::key))
        val menuSize = CommandSettingsSlotPolicy.size(node.type, fields.size)
        val elements = fields.mapIndexed { index, field ->
            val display = field.value(node)
            // 複数の意味値を持つ表示はLoreのデータ行へ分けます。単一行の結合は
            // 行ピッチを崩すため、意味ごとの行をそのままデータ行へ写します。
            val data = display.rows(player)?.map { row ->
                GuiMenuEntryData(row.label, row.value)
            } ?: listOf(GuiMenuEntryData(
                KcI18n.text(player, field.label),
                display.render(player),
            ))
            KcGui.menuEntry(
                player = player,
                slot = slots[index],
                material = field.material,
                name = KcI18n.text(player, field.label),
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, field.descriptionKey),
                data = data,
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
        // 削除は右クリックではなく、設定画面内の削除項目から確認画面へ進みます。
        elements += choiceElement(
            player = player,
            slot = 53,
            material = Material.RED_CONCRETE,
            name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DELETE_COMMAND),
            actionId = "delete",
            payload = mapOf(NODE_ID to node.id.toString()),
            style = GuiNameStyle.DANGER,
        )
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
    private fun renderTarget(player: Player, route: MenuRoute): InventoryMenuView {
        // 簡易GUIでは対象を3プリセットから選択します。複雑な条件指定は行えず、
        // プリセット選択時に隠れた絞り込み条件は引き継ぎません。
        val current = selectedTargetSpec(route)
        val options = listOf(
            Triple(TargetKind.NEAREST_PLAYER, Material.PLAYER_HEAD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER),
            Triple(TargetKind.NEARBY_PLAYERS, Material.COMPASS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS),
            Triple(TargetKind.ALL_PLAYERS, Material.GLOBE_BANNER_PATTERN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(
            options.size + if (current?.kind == TargetKind.NEARBY_PLAYERS) 1 else 0,
        )
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                KcI18n.text(player, option.third),
                "select",
                mapOf("kind" to option.first.name),
                dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                dataValue = if (current?.kind == option.first) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECTED)
                } else null,
            )
        }.toMutableList()
        // 「周囲のプレイヤー」の距離だけを数値入力で調整します。
        if (current?.kind == TargetKind.NEARBY_PLAYERS) {
            elements += choiceElement(
                player = player,
                slot = layout.itemSlots[options.size],
                material = Material.RECOVERY_COMPASS,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE),
                actionId = "distance",
                dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE),
                dataValue = displayDistance(current.minimumDistance, current.maximumDistance),
            )
        }
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_TARGET_TITLE)), elements)
    }

    private fun renderPosition(player: Player, route: MenuRoute): InventoryMenuView {
        val destination = route.payload[ROLE] == "destination"
        val positionRole = CommandSettingRole.fromRoute(route.payload[ROLE])
        val currentNode = node(route)
        val currentKind = currentNode?.let { CommandSettingsModel.positionKind(it, positionRole) }
        // 簡易GUIでは制御ブロック位置と対象の現在位置の2択だけを提示します。
        // テレポート先の対象選択は「対象を選択」項目から対象選択画面へ進みます。
        // 保存済みの座標・一時変数等の値は変更せず保持します。
        val options = listOf(
            Triple(PositionKind.DISK, Material.COMMAND_BLOCK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION),
            Triple(PositionKind.TARGET, Material.ENDER_PEARL, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size + if (destination) 1 else 0)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                KcI18n.text(player, option.third),
                "select",
                mapOf("kind" to option.first.name),
                dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION),
                dataValue = if (currentKind == option.first) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECTED)
                } else null,
                enabled = currentNode?.let {
                    CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(it, positionRole, option.first)
                } ?: true,
                disabledDescription = if (option.first == PositionKind.DISK) {
                    listOf(CommandSettingAvailabilityPolicy.CONTROL_BLOCK_POSITION_DISABLED_HOVER)
                } else null,
            )
        }.toMutableList()
        if (destination) {
            elements += choiceElement(
                player = player,
                slot = layout.itemSlots[options.size],
                material = Material.PLAYER_HEAD,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_OTHER_ENTITY),
                actionId = "target",
            )
        }
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(layout.size, KcGui.title(KcI18n.text(player, if (destination) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_POSITION_DESTINATION_TITLE else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION)), elements)
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
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_HELD_BLOCK_REQUIRED))
            return MenuActionResult.Ignored
        }
        val currentNode = node(route) ?: return MenuActionResult.Ignored
        if (HeldSettingOverwritePolicy.requiresConfirmation(currentNode.string("block"))) {
            // Formへ渡す値と確認時の保存世代を固定します。確認中に別の編集が
            // 入った場合は古い設定を上書きせず、保存失敗としてFormへ戻します。
            showBlockOverwriteForm(
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
    private fun showBlockOverwriteForm(
        player: Player,
        route: MenuRoute,
        blockId: String,
        expectedRevision: Long?,
    ) {
        showConfirmForm(
            player = player,
            route = route,
            formId = "block-overwrite",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_BLOCK_OVERWRITE_TITLE),
            content = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_BLOCK_OVERWRITE_WARN),
            confirmLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
            cancelLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL),
            onConfirm = {
                updateNode(
                    player,
                    route,
                    configuredFields = setOf("block"),
                    expectedRevision = expectedRevision,
                ) { node ->
                    CommandSettingsModel.setParameter(node, "block", blockId)
                }
            },
        )
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

    private fun settingsFields(node: CommandNode): List<EditorField> {
        // 簡易GUIでは向き指定を扱わないため、設定入口から除外します。
        // 保存済みの向き値は変更せず保持し、実行時の既定解決へ委ねます。
        return CommandSettingsModel.visibleFields(node).filter { it.key != "destinationFacing" }
    }

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

    /**
     * 簡易GUI（統合版専用）のForm入力基盤です。
     *
     * BedrockではDialogが使えないため、文字・数値入力はFloodgate CustomFormへ
     * 送ります。検証仕様（CommandDialogSpecs.Spec）はDialogと共有し、画面ごとに
     * 検証を複製しません。検証失敗時は文言を送って同じFormを開き直し、保存後は
     * 呼び出し元のインベントリ画面へ戻ります。Formを閉じた場合も復帰します。
     */
    private data class FormTextField(
        val id: String,
        val label: String,
        val initial: String,
        val placeholder: String = "",
        val spec: CommandDialogSpecs.Spec,
    )

    private fun showTextForm(
        player: Player,
        route: MenuRoute,
        formId: String,
        title: String,
        fields: List<FormTextField>,
        extraValidate: (Map<String, String>) -> LocalizationKey<String>? = { null },
        save: (Map<String, String>) -> Boolean,
    ) {
        val forms = CCSystem.getAPI().getMenuFormService()
        if (!forms.isAvailable(player)) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SIMPLE_FORM_UNAVAILABLE))
            return
        }
        forms.show(
            player,
            MenuCustomFormRequest(
                owner = SequenceEditorMenu.OWNER,
                id = formId,
                title = title,
                inputs = fields.map { field ->
                    MenuFormInput.Text(field.id, field.label, field.placeholder, field.initial)
                },
                handler = MenuFormHandler { _, response ->
                    val values = fields.associate { field ->
                        field.id to CommandDialogSpecs.normalize(field.id, response.textValue(field.id))
                    }
                    val validationError = fields.firstNotNullOfOrNull { field ->
                        field.spec.validateInput(values.getValue(field.id))
                    } ?: extraValidate(values)
                    if (validationError != null) {
                        player.sendMessage(KcI18n.component(player, validationError))
                        return@MenuFormHandler MenuActionResult.Success(MenuUpdate.Refresh)
                    }
                    if (!save(values)) {
                        player.sendMessage(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        return@MenuFormHandler MenuActionResult.Success(MenuUpdate.Resume)
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                },
                onClosed = MenuFormHandler { _, _ -> MenuActionResult.Success(MenuUpdate.Resume) },
            ),
        )
    }

    /** 肯定・否定の二択確認をSimpleFormで表示します。 */
    private fun showConfirmForm(
        player: Player,
        route: MenuRoute,
        formId: String,
        title: String,
        content: String,
        confirmLabel: String,
        cancelLabel: String,
        onConfirm: () -> Boolean,
    ) {
        val forms = CCSystem.getAPI().getMenuFormService()
        if (!forms.isAvailable(player)) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SIMPLE_FORM_UNAVAILABLE))
            return
        }
        forms.show(
            player,
            MenuSimpleFormRequest(
                owner = SequenceEditorMenu.OWNER,
                id = formId,
                title = title,
                content = content,
                buttons = listOf(
                    MenuFormButton("confirm", confirmLabel),
                    MenuFormButton("cancel", cancelLabel),
                ),
                handler = MenuFormHandler { _, response ->
                    if (response.textValue("button") != "confirm") {
                        return@MenuFormHandler MenuActionResult.Success(MenuUpdate.Resume)
                    }
                    if (!onConfirm()) {
                        player.sendMessage(KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        return@MenuFormHandler MenuActionResult.Success(MenuUpdate.Resume)
                    }
                    MenuActionResult.Success(MenuUpdate.Replace(route))
                },
                onClosed = MenuFormHandler { _, _ -> MenuActionResult.Success(MenuUpdate.Resume) },
            ),
        )
    }

    /** 「周囲のプレイヤー」の距離だけを数値入力で調整します。空欄は指定解除です。 */
    private fun openTargetDistanceForm(player: Player, route: MenuRoute) {
        val currentSpec = selectedTargetSpec(route) ?: return
        val inputSpec = requireNotNull(CommandDialogSpecs.targetFilter("distance"))
        showTextForm(
            player = player,
            route = route,
            formId = "target-distance",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf(
                FormTextField(
                    "minimum",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE),
                    currentSpec.minimumDistance?.toString().orEmpty(),
                    spec = inputSpec,
                ),
                FormTextField(
                    "maximum",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE),
                    currentSpec.maximumDistance?.toString().orEmpty(),
                    spec = inputSpec,
                ),
            ),
            extraValidate = { values ->
                val minimum = values.getValue("minimum").trim().takeIf(String::isNotEmpty)
                    ?.let(CommandDialogSpecs::finiteDouble)
                val maximum = values.getValue("maximum").trim().takeIf(String::isNotEmpty)
                    ?.let(CommandDialogSpecs::finiteDouble)
                if (minimum != null && maximum != null && minimum > maximum) {
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_ERROR_MINIMUM_ABOVE_MAXIMUM
                } else null
            },
            save = { values ->
                updateTargetSpec(player, route) { _ ->
                    currentSpec.copy(
                        minimumDistance = values.getValue("minimum").trim().takeIf(String::isNotEmpty)
                            ?.let(CommandDialogSpecs::finiteDouble),
                        maximumDistance = values.getValue("maximum").trim().takeIf(String::isNotEmpty)
                            ?.let(CommandDialogSpecs::finiteDouble),
                    )
                }
            },
        )
    }

    /** ブロック操作の配置位置・範囲を座標の数値入力で指定します。 */
    private fun openBlockPositionForm(player: Player, route: MenuRoute, field: String) {
        val role = when (field) {
            "position" -> CommandSettingRole.BLOCK_POSITION
            "from" -> CommandSettingRole.BLOCK_FROM
            "to" -> CommandSettingRole.BLOCK_TO
            else -> return
        }
        val current = node(route)?.let { CommandSettingsModel.positionSpec(it, role) }
        val location = player.location
        showTextForm(
            player = player,
            route = route,
            formId = "block-position-$field",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf("x", "y", "z").map { axis ->
                FormTextField(
                    axis,
                    axis.uppercase(),
                    // 保存値の意味付けを記法へ戻して表示し、Form内での往復を保ちます。
                    when (axis) {
                        "x" -> CommandDialogSpecs.formatCoordinateComponent(
                            current?.semanticsX ?: CoordinateSemantics.ABSOLUTE,
                            current?.x ?: location.x,
                        )
                        "y" -> CommandDialogSpecs.formatCoordinateComponent(
                            current?.semanticsY ?: CoordinateSemantics.ABSOLUTE,
                            current?.y ?: location.y,
                        )
                        else -> CommandDialogSpecs.formatCoordinateComponent(
                            current?.semanticsZ ?: CoordinateSemantics.ABSOLUTE,
                            current?.z ?: location.z,
                        )
                    },
                    spec = CommandDialogSpecs.coordinateSpec(axis),
                )
            },
            save = { values ->
                val x = CommandDialogSpecs.parseCoordinateComponent(values.getValue("x"))
                val y = CommandDialogSpecs.parseCoordinateComponent(values.getValue("y"))
                val z = CommandDialogSpecs.parseCoordinateComponent(values.getValue("z"))
                if (x == null || y == null || z == null) {
                    false
                } else {
                    updateNode(player, route) { command ->
                        // Dialog記法の意味付けを保存します。向き成分は本欄で扱わないため保持します。
                        val kept = CommandSettingsModel.positionSpec(command, role)
                            ?.takeIf { kept -> kept.kind == PositionKind.COORDINATES }
                            ?: PositionSpec(PositionKind.COORDINATES)
                        CommandSettingsModel.setPositionSpec(
                            command,
                            role,
                            kept.copy(
                                x = x.second, y = y.second, z = z.second,
                                semanticsX = x.first, semanticsY = y.first, semanticsZ = z.first,
                            ),
                        )
                    }
                }
            },
        )
    }

    private fun showFieldForm(
        player: Player,
        route: MenuRoute,
        field: String,
        node: CommandNode,
    ) {
        if (field == "staySeconds" && node.type == CommandType.DISPLAY_TEXT) {
            showDisplayTimingForm(player, route, node)
            return
        }
        if (field == "soundParameters" && node.type == CommandType.PLAY_SOUND) {
            showSoundParametersForm(player, route, node)
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
            "particle" -> ParticleSettings.particle(node)?.name ?: "FLAME"
            "particleData" -> ""
            "particleDensity" -> "0.5"
            "burstHeight" -> "20.0"
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
        val currentValue = node.string(field, defaultValue)
        // 候補値のボタン提示（Dialogのsuggestions）はFormでは行えません。
        // 入力例は初期値で示し、検証文言で制約を案内します。
        showTextForm(
            player = player,
            route = route,
            formId = "field-$field",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf(
                FormTextField(
                    field,
                    KcI18n.text(player, spec.labelKey),
                    currentValue,
                    spec = spec,
                ),
            ),
            save = { values ->
                updateNode(player, route) {
                    CommandSettingsModel.setParameter(it, field, values.getValue(field))
                }
            },
        )
    }

    /** 効果音の音量・ピッチを一つの設定項目として編集します。 */
    private fun showSoundParametersForm(player: Player, route: MenuRoute, node: CommandNode) {
        val volumeSpec = requireNotNull(CommandDialogSpecs.field(node, "volume"))
        val pitchSpec = requireNotNull(CommandDialogSpecs.field(node, "pitch"))
        showTextForm(
            player = player,
            route = route,
            formId = "sound-parameters",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf(
                FormTextField(
                    "volume",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VOLUME),
                    node.string("volume", "1.0"),
                    spec = volumeSpec,
                ),
                FormTextField(
                    "pitch",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PITCH),
                    node.string("pitch", "1.0"),
                    spec = pitchSpec,
                ),
            ),
            save = { values ->
                updateNode(player, route, configuredFields = setOf("soundParameters")) { command ->
                    CommandSettingsModel.setParameters(command, values)
                }
            },
        )
    }

    /** パーティクルの範囲・速度・個数を1回の確認で保存します。 */
    private fun showParticleParametersForm(player: Player, route: MenuRoute, node: CommandNode) {
        val fields = listOf(
            ParticleSettings.PARAM_DELTA_X,
            ParticleSettings.PARAM_DELTA_Y,
            ParticleSettings.PARAM_DELTA_Z,
            ParticleSettings.PARAM_SPEED,
            ParticleSettings.PARAM_COUNT,
        )
        val specs = fields.associateWith { requireNotNull(CommandDialogSpecs.field(node, it)) }
        val labels = mapOf(
            ParticleSettings.PARAM_DELTA_X to KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX),
            ParticleSettings.PARAM_DELTA_Y to KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY),
            ParticleSettings.PARAM_DELTA_Z to KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ),
            ParticleSettings.PARAM_SPEED to KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SPEED),
            ParticleSettings.PARAM_COUNT to KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT),
        )
        showTextForm(
            player = player,
            route = route,
            formId = "particle-parameters",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = fields.map { key ->
                FormTextField(
                    key,
                    labels.getValue(key),
                    node.string(key, node.type.defaults[key].orEmpty()),
                    spec = specs.getValue(key),
                )
            },
            save = { values ->
                updateNode(player, route, configuredFields = setOf("particleParameters")) { command ->
                    CommandSettingsModel.setParameters(command, values)
                }
            },
        )
    }

    /** 効果音設定の束ね値（ID・音量・ピッチ／ID・カテゴリ）を一つの画面で編集します。 */
    private fun showSoundSettingForm(player: Player, route: MenuRoute, node: CommandNode) {
        val inputs = CommandDialogSpecs.soundSettingInputs(node)
        showTextForm(
            player = player,
            route = route,
            formId = "sound-setting",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = inputs.map { input ->
                FormTextField(
                    input.id,
                    KcI18n.text(player, input.labelKey),
                    input.initial,
                    spec = input.spec,
                )
            },
            save = { values ->
                updateNode(player, route, configuredFields = setOf("soundSetting")) { command ->
                    CommandSettingsModel.setParameters(command, values)
                }
            },
        )
    }

    /**
     * パーティクル種類と詳細データを一つの画面で編集します。
     *
     * VIBRATION／TRAILは終点XYZを1群として束ねた入力欄から単一詳細文字列へ
     * 合成して保存します。保存形式は従来の1文字列のまま変えません。
     */
    private fun showParticleForm(player: Player, route: MenuRoute, node: CommandNode) {
        val inputs = CommandDialogSpecs.particleInputs(node)
        showTextForm(
            player = player,
            route = route,
            formId = "particle",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = inputs.map { input ->
                FormTextField(
                    input.id,
                    KcI18n.text(player, input.labelKey),
                    input.initial,
                    spec = input.spec,
                )
            },
            save = { values ->
                updateNode(player, route, configuredFields = setOf("particle", "particleData")) { command ->
                    val composed = mutableMapOf<String, String>()
                    composed[ParticleSettings.PARAM_PARTICLE] =
                        values.getValue(ParticleSettings.PARAM_PARTICLE)
                    when {
                        values.containsKey("vibX") -> composed[ParticleSettings.PARAM_DATA] =
                            CommandDialogSpecs.composeVibrationData(values)
                        values.containsKey("trailX") -> composed[ParticleSettings.PARAM_DATA] =
                            CommandDialogSpecs.composeTrailData(values)
                        values.containsKey(ParticleSettings.PARAM_DATA) -> composed[ParticleSettings.PARAM_DATA] =
                            values.getValue(ParticleSettings.PARAM_DATA)
                    }
                    CommandSettingsModel.setParameters(command, composed)
                }
            },
        )
    }

    /** 図形数値を現形状ごとに一つの画面で編集します。 */
    private fun showGeometryForm(player: Player, route: MenuRoute, node: CommandNode) {
        val inputs = CommandDialogSpecs.geometryInputs(node)
        showTextForm(
            player = player,
            route = route,
            formId = "geometry-numbers",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = inputs.map { input ->
                FormTextField(
                    input.id,
                    KcI18n.text(player, input.labelKey),
                    input.initial,
                    spec = input.spec,
                )
            },
            save = { values ->
                updateNode(player, route, configuredFields = setOf("geometrySettings")) { command ->
                    CommandSettingsModel.setParameters(command, values)
                }
            },
        )
    }

    /** 汎用位置（制御ブロック位置または座標入力）を座標フォームで指定します。 */
    private fun openCoordinatePositionForm(player: Player, route: MenuRoute, role: CommandSettingRole) {
        val current = node(route)?.let { CommandSettingsModel.positionSpec(it, role) }
        val location = player.location
        showTextForm(
            player = player,
            route = route,
            formId = "coordinate-position-${role.routeValue}",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf("x", "y", "z").map { axis ->
                FormTextField(
                    axis,
                    axis.uppercase(),
                    when (axis) {
                        "x" -> CommandDialogSpecs.formatCoordinateComponent(
                            current?.semanticsX ?: CoordinateSemantics.ABSOLUTE,
                            current?.x ?: location.x,
                        )
                        "y" -> CommandDialogSpecs.formatCoordinateComponent(
                            current?.semanticsY ?: CoordinateSemantics.ABSOLUTE,
                            current?.y ?: location.y,
                        )
                        else -> CommandDialogSpecs.formatCoordinateComponent(
                            current?.semanticsZ ?: CoordinateSemantics.ABSOLUTE,
                            current?.z ?: location.z,
                        )
                    },
                    spec = CommandDialogSpecs.coordinateSpec(axis),
                )
            },
            save = { values ->
                val x = CommandDialogSpecs.parseCoordinateComponent(values.getValue("x"))
                val y = CommandDialogSpecs.parseCoordinateComponent(values.getValue("y"))
                val z = CommandDialogSpecs.parseCoordinateComponent(values.getValue("z"))
                if (x == null || y == null || z == null) {
                    false
                } else {
                    updateNode(player, route) { command ->
                        val kept = CommandSettingsModel.positionSpec(command, role)
                            ?.takeIf { kept -> kept.kind == PositionKind.COORDINATES }
                            ?: PositionSpec(PositionKind.COORDINATES)
                        CommandSettingsModel.setPositionSpec(
                            command,
                            role,
                            kept.copy(
                                x = x.second, y = y.second, z = z.second,
                                semanticsX = x.first, semanticsY = y.first, semanticsZ = z.first,
                            ),
                        )
                    }
                }
            },
        )
    }

    /**
     * 花火の中身をメインハンドの実物から保存します。
     *
     * GIVE_ITEMの付与アイテムと同型の item＋itemData 対であり、FIREWORK_ROCKET
     * 以外は受け付けません。上書き時は確認画面を挟みます。
     */
    private fun setHeldFirework(player: Player, route: MenuRoute): MenuActionResult {
        val held = player.inventory.itemInMainHand
        if (held.type != Material.FIREWORK_ROCKET) {
            player.sendMessage(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_HELD_FIREWORK_REQUIRED))
            return MenuActionResult.Ignored
        }
        val itemKey = held.type.key.toString()
        val itemData = me.awabi2048.kantancommander.item.ItemStackCodec.encode(held)
        val currentNode = node(route) ?: return MenuActionResult.Ignored
        if (currentNode.string(FireworkSettings.PARAM_ITEM).isNotBlank() ||
            currentNode.string(FireworkSettings.PARAM_DATA).isNotBlank()
        ) {
            showConfirmForm(
                player = player,
                route = route,
                formId = "firework-overwrite",
                title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_ITEM_OVERWRITE_TITLE),
                content = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_CONFIRM_ITEM_OVERWRITE_WARN),
                confirmLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CONFIRM),
                cancelLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_CANCEL),
                onConfirm = {
                    updateNode(
                        player,
                        route,
                        configuredFields = setOf("fireworkContents"),
                    ) { command ->
                        CommandSettingsModel.setParameter(command, FireworkSettings.PARAM_ITEM, itemKey)
                        CommandSettingsModel.setParameter(command, FireworkSettings.PARAM_DATA, itemData)
                    }
                },
            )
            return MenuActionResult.Success(MenuUpdate.None)
        }
        if (!updateNode(player, route, configuredFields = setOf("fireworkContents")) { command ->
                CommandSettingsModel.setParameter(command, FireworkSettings.PARAM_ITEM, itemKey)
                CommandSettingsModel.setParameter(command, FireworkSettings.PARAM_DATA, itemData)
            }
        ) {
            return MenuActionResult.Rejected(
                KcI18n.component(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
            )
        }
        return MenuActionResult.Success(MenuUpdate.Refresh)
    }

    /**
     * 設定中の花火をその場で打ち上げます。
     *
     * 本実行と同じ検証済み方式（初速固定・ワンショット起爆判定）を使い、
     * 保存は行わず使い捨てで発射します。召喚体台帳へは登録しません。
     */
    private fun launchFireworkPreview(player: Player, route: MenuRoute): MenuActionResult {
        val node = node(route) ?: return MenuActionResult.Ignored
        val data = node.string(FireworkSettings.PARAM_DATA)
        val stack = data.takeIf(String::isNotBlank)?.let {
            me.awabi2048.kantancommander.item.ItemStackCodec.decode(it)
        } ?: node.string(FireworkSettings.PARAM_ITEM).takeIf(String::isNotBlank)?.let { raw ->
            org.bukkit.Material.matchMaterial(raw)?.let { org.bukkit.inventory.ItemStack(it) }
        } ?: return MenuActionResult.Ignored
        if (stack.type != Material.FIREWORK_ROCKET) return MenuActionResult.Ignored
        val height = node.string(FireworkSettings.PARAM_HEIGHT, FireworkSettings.HEIGHT_DEFAULT.toString())
            .toDoubleOrNull()?.takeIf(Double::isFinite) ?: FireworkSettings.HEIGHT_DEFAULT
        // 試射は操作者（Gesture GUIエディターの操作者）の位置から打ち上げ、
        // 発射者を自機に固定して接触起爆を避けます。
        val launchAt = player.location.clone()
        if (height <= 0.0) {
            val instant = player.world.spawn(launchAt, org.bukkit.entity.Firework::class.java)
            instant.setItem(stack)
            instant.setShotAtAngle(false)
            instant.detonate()
            return MenuActionResult.Success(MenuUpdate.None)
        }
        val rocket = player.world.spawn(launchAt, org.bukkit.entity.Firework::class.java)
        rocket.setItem(stack)
        rocket.setShotAtAngle(false)
        rocket.shooter = player
        rocket.velocity = org.bukkit.util.Vector(0.0, FireworkSettings.INITIAL_UPWARD_VELOCITY, 0.0)
        rocket.velocity = org.bukkit.util.Vector(0.0, FireworkSettings.INITIAL_UPWARD_VELOCITY, 0.0)
        rocket.ticksToDetonate = FireworkSettings.backstopLifetime(height)
        val target = launchAt.clone().add(0.0, height, 0.0)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            detonatePreviewAtTarget(rocket, target)
        }, FireworkSettings.detonationTicks(height).toLong().coerceAtLeast(1L))
        return MenuActionResult.Success(MenuUpdate.None)
    }

    /** 試射のワンショット起爆判定です。本実行と同一条件で目標高度へ補正起爆します。 */
    private fun detonatePreviewAtTarget(rocket: org.bukkit.entity.Firework, target: Location, retriesLeft: Int = 5) {
        val world = target.world
        if (!rocket.isValid || rocket.isDetonated || world == null ||
            !world.isChunkLoaded(target.blockX shr 4, target.blockZ shr 4)
        ) {
            if (retriesLeft > 0) {
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    detonatePreviewAtTarget(rocket, target, retriesLeft - 1)
                }, 20L)
            } else {
                runCatching { rocket.remove() }
            }
            return
        }
        rocket.teleport(target)
        rocket.detonate()
    }

    private fun renderSoundTargetPosition(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
        val operation = node?.let(SoundSettings::operation) ?: SoundOperation.PLAY
        val buttons = buildList {
            add(Triple("target", Material.PLAYER_HEAD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET))
            if (operation == SoundOperation.PLAY) {
                add(Triple("position", Material.COMPASS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_POSITION))
            }
        }
        val layout = ChoiceMenuLayoutPolicy.layout(buttons.size)
        val elements = buttons.mapIndexed { index, button ->
            choiceElement(
                player,
                layout.itemSlots[index],
                button.second,
                KcI18n.text(player, button.third),
                button.first,
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_TARGET_POSITION)),
            elements,
        )
    }

    private fun renderGenericPosition(player: Player, route: MenuRoute): InventoryMenuView {
        val layout = ChoiceMenuLayoutPolicy.layout(2)
        val elements = listOf(
            choiceElement(
                player,
                layout.itemSlots[0],
                Material.COMMAND_BLOCK,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION),
                "disk",
            ),
            choiceElement(
                player,
                layout.itemSlots[1],
                Material.COMPASS,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES),
                "coordinates",
            ),
        ).toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION)),
            elements,
        )
    }

    private fun renderParticleDisplay(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
        val mode = node?.let(ParticleGeometry::displayMode) ?: ParticleDisplayMode.POINT
        val layout = ChoiceMenuLayoutPolicy.layout(3)
        val elements = listOf(
            choiceElement(
                player,
                layout.itemSlots[0],
                Material.PLAYER_HEAD,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                "target",
            ),
            choiceElement(
                player,
                layout.itemSlots[1],
                Material.SNOWBALL,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARTICLE_POINT),
                "mode",
                payload = mapOf("mode" to ParticleDisplayMode.POINT.name),
                dataValue = if (mode == ParticleDisplayMode.POINT) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECTED)
                } else null,
            ),
            choiceElement(
                player,
                layout.itemSlots[2],
                Material.FILLED_MAP,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARTICLE_GEOMETRY),
                "mode",
                payload = mapOf("mode" to ParticleDisplayMode.GEOMETRY.name),
                dataValue = if (mode == ParticleDisplayMode.GEOMETRY) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECTED)
                } else null,
            ),
        ).toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DISPLAY)),
            elements,
        )
    }

    private fun renderGeometrySettings(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
        val shape = node?.let(ParticleGeometry::shape) ?: ParticleShape.POINT_SCATTER
        val showEnd = shape == ParticleShape.LINE || shape == ParticleShape.BOX
        val buttons = buildList {
            add("shape")
            add("numbers")
            if (showEnd) add("end")
        }
        val layout = ChoiceMenuLayoutPolicy.layout(buttons.size)
        val elements = buttons.mapIndexed { index, button ->
            when (button) {
                "shape" -> choiceElement(
                    player,
                    layout.itemSlots[index],
                    Material.FILLED_MAP,
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_SETTINGS),
                    "shape",
                    dataValue = KcI18n.text(player, geometryShapeKey(shape)),
                )
                "numbers" -> choiceElement(
                    player,
                    layout.itemSlots[index],
                    Material.PAPER,
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_PARAMETERS),
                    "numbers",
                )
                else -> choiceElement(
                    player,
                    layout.itemSlots[index],
                    Material.COMPASS,
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION),
                    "end",
                )
            }
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_SETTINGS)),
            elements,
        )
    }

    private fun renderParticleShapes(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
        val current = node?.let(ParticleGeometry::shape) ?: ParticleShape.POINT_SCATTER
        val options = listOf(
            Triple(ParticleShape.LINE, Material.STICK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_LINE),
            Triple(ParticleShape.CIRCLE, Material.COMPASS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_CIRCLE),
            Triple(ParticleShape.ARC, Material.BOW, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_ARC),
            Triple(ParticleShape.BOX, Material.CHEST, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_BOX),
            Triple(ParticleShape.SPHERE, Material.ENDER_PEARL, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_SPHERE),
            Triple(ParticleShape.HELIX, Material.BLAZE_ROD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_HELIX),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                KcI18n.text(player, option.third),
                "select",
                payload = mapOf("shape" to option.first.name),
                dataValue = if (current == option.first) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECTED)
                } else null,
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_SETTINGS)),
            elements,
        )
    }

    private fun renderFireworkContents(player: Player, route: MenuRoute): InventoryMenuView {
        val layout = ChoiceMenuLayoutPolicy.layout(2)
        val elements = listOf(
            choiceElement(
                player,
                layout.itemSlots[0],
                Material.CHEST,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_EDIT_FROM_MAINHAND),
                "held",
            ),
            choiceElement(
                player,
                layout.itemSlots[1],
                Material.FIREWORK_ROCKET,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_FIREWORK_PREVIEW),
                "launch",
            ),
        ).toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FIREWORK_CONTENTS)),
            elements,
        )
    }

    private fun showDisplayTimingForm(player: Player, route: MenuRoute, node: CommandNode) {
        val durationSpec = requireNotNull(CommandDialogSpecs.field(node, "staySeconds"))
        showTextForm(
            player = player,
            route = route,
            formId = "display-timing",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DURATION_TITLE),
            fields = listOf(
                FormTextField(
                    "fadeInSeconds",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_IN),
                    node.string("fadeInSeconds", "1"),
                    spec = durationSpec,
                ),
                FormTextField(
                    "staySeconds",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_STAY),
                    node.string("staySeconds", "3"),
                    spec = durationSpec,
                ),
                FormTextField(
                    "fadeOutSeconds",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_FADE_OUT),
                    node.string("fadeOutSeconds", "1"),
                    spec = durationSpec,
                ),
            ),
            save = { values ->
                updateNode(player, route) { command ->
                    CommandSettingsModel.setParameters(command, values)
                }
            },
        )
    }

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
        private const val SETTINGS_ID = "command_settings"
        private const val ENTITY_EQUIPMENT_SLOT_ID = "entity_equipment_slot"
        private const val DISPLAY_MODE_ID = "display_mode"
        private const val BLOCK_OPERATION_ID = "block_operation"
        private const val DELETE_ID = "delete_command"
        private const val TARGET_ID = "target_settings"
        private const val POSITION_ID = "position_settings"
        private const val SOUND_TARGET_POSITION_ID = "sound_target_position"
        private const val GENERIC_POSITION_ID = "generic_position"
        private const val PARTICLE_DISPLAY_ID = "particle_display"
        private const val GEOMETRY_SETTINGS_ID = "geometry_settings"
        private const val PARTICLE_SHAPE_ID = "particle_shape"
        private const val FIREWORK_CONTENTS_ID = "firework_contents"
        private const val SCRIPT_ID = "scriptId"
        private const val NODE_ID = "nodeId"
        private const val SOURCE_ID = "sourceId"
        private const val EDGE = "edge"
        private const val MERGE_CONDITION_ID = "mergeConditionId"
        private const val CONTINUATION_ID = "continuationId"
        private const val ROLE = "role"

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

        fun deleteRoute(current: MenuRoute, nodeId: UUID) =
            requireNotNull(EditorSession.from(current)).route(
                SequenceEditorMenu.OWNER,
                DELETE_ID,
                mapOf(NODE_ID to nodeId.toString()),
            )

        private fun targetRoute(route: MenuRoute, role: String) =
            route.copy(id = TARGET_ID, payload = route.payload + (ROLE to role))

        private fun positionRoute(route: MenuRoute, role: String) =
            route.copy(id = POSITION_ID, payload = route.payload + (ROLE to role))

        private fun genericPositionRoute(route: MenuRoute, role: String) =
            route.copy(id = GENERIC_POSITION_ID, payload = route.payload + (ROLE to role))

        private fun choiceRoute(route: MenuRoute, id: String) = route.copy(id = id)

        private fun scriptId(route: MenuRoute) = EditorSession.from(route)?.scriptId
    }
}

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
        override fun rows(player: Player): List<TimingRow> = listOf(
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

    /** パーティクルの範囲3軸・速度・個数を、設定画面の現在値にも意味付きで表示します。 */
    data class ParticleParameters(
        val deltaX: String,
        val deltaY: String,
        val deltaZ: String,
        val speed: String,
        val count: String,
    ) : DisplayValue {
        // 5値を5行へ分けると行ピッチが半減して縦に重なるため、3軸は範囲1行へ
        // まとめます。表示時間タブと同一形状になり、既存の複数行配置に収まります。
        override fun rows(player: Player): List<TimingRow> = listOf(
            TimingRow(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_SPREAD),
                listOf(deltaX, deltaY, deltaZ).joinToString(" / "),
            ),
            TimingRow(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SPEED), speed),
            TimingRow(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT), count),
        )
    }

    /** LOCATIONの位置・向きを、一つの設定欄で現在値として表示します。 */
    data class Location(
        val position: DisplayValue?,
        val facing: DisplayValue?,
    ) : DisplayValue

    /** 効果音の操作・対象・位置を、一つの設定欄で現在値として表示します。 */
    data class SoundTargetPosition(
        val targetKind: TargetKind?,
        val positionKind: PositionKind?,
        val operation: SoundOperation,
    ) : DisplayValue {
        override fun rows(player: Player): List<TimingRow> = buildList {
            add(TimingRow(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                targetKind?.let { displayTarget(it).render(player) }
                    ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET),
            ))
            if (operation == SoundOperation.PLAY) {
                add(TimingRow(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_POSITION),
                    positionKind?.let { displayPosition(it).render(player) }
                        ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET),
                ))
            }
        }
    }

    /** 効果音設定の束ね値を、操作ごとに意味付きで表示します。 */
    data class SoundSetting(
        val operation: SoundOperation,
        val sound: String,
        val volume: String,
        val pitch: String,
        val category: String,
        val tempRef: String?,
    ) : DisplayValue {
        override fun rows(player: Player): List<TimingRow> {
            tempRef?.let {
                return listOf(TimingRow(
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND),
                    it,
                ))
            }
            return when (operation) {
                SoundOperation.PLAY -> listOf(
                    TimingRow(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND), sound.ifBlank {
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
                    }),
                    TimingRow(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_PARAMETERS),
                        "$volume / $pitch",
                    ),
                )
                SoundOperation.STOP -> listOf(
                    TimingRow(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND),
                        sound.ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL) },
                    ),
                    TimingRow(
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_SCOPE),
                        category.ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL) },
                    ),
                )
            }
        }
    }

    /** パーティクルの対象と表示方式を、一つの設定欄で現在値として表示します。 */
    data class ParticleDisplay(
        val targetKind: TargetKind?,
        val mode: ParticleDisplayMode,
    ) : DisplayValue {
        override fun rows(player: Player): List<TimingRow> = listOf(
            TimingRow(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                targetKind?.let { displayTarget(it).render(player) }
                    ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET),
            ),
            TimingRow(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DISPLAY),
                KcI18n.text(
                    player,
                    if (mode == ParticleDisplayMode.GEOMETRY) {
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARTICLE_GEOMETRY
                    } else {
                        KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARTICLE_POINT
                    },
                ),
            ),
        )
    }

    /** 図形設定の形状と主要寸法を、一つの設定欄で現在値として表示します。 */
    data class GeometrySettings(
        val shape: ParticleShape,
        val summary: String,
    ) : DisplayValue

    fun render(player: Player): String = when (this) {
        is Literal -> value
        is Localized -> KcI18n.text(player, key)
        is Timing -> rows(player).joinToString(" / ") { "${it.label}=${it.value}" }
        is ParticleParameters -> rows(player).joinToString(" / ") { "${it.label}=${it.value}" }
        is SoundTargetPosition -> rows(player).joinToString(" / ") { "${it.label}=${it.value}" }
        is SoundSetting -> rows(player).joinToString(" / ") { "${it.label}=${it.value}" }
        is ParticleDisplay -> rows(player).joinToString(" / ") { "${it.label}=${it.value}" }
        is GeometrySettings -> "${KcI18n.text(player, geometryShapeKey(shape))} $summary".trim()
        is Location -> buildList {
            position?.let {
                add("${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION)}=${it.render(player)}")
            }
            facing?.let {
                add("${KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FACING)}=${it.render(player)}")
            }
        }.joinToString(" / ").ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }
    }

    /**
     * 意味ごとの行へ分けて表示できる値を返します。
     *
     * Inventory GUIは行ごとをLoreのデータ行へ分け、Gesture GUIは複数行
     * レイアウトへ通します。単一行で十分な値はnullを返します。
     */
    fun rows(player: Player): List<TimingRow>? = when (this) {
        is Timing -> rows(player)
        is ParticleParameters -> rows(player)
        is SoundTargetPosition -> rows(player)
        is SoundSetting -> rows(player)
        is ParticleDisplay -> rows(player)
        else -> null
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
            field(
                "soundOperation",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_OPERATION,
                Material.COMPARATOR,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displaySoundOperation(SoundSettings.operation(it)) },
            field(
                "soundTargetPosition",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_TARGET_POSITION,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_POSITION,
            ) {
                DisplayValue.SoundTargetPosition(
                    targetKind = it.targetSpec?.kind,
                    positionKind = it.soundPositionSpec?.kind,
                    operation = SoundSettings.operation(it),
                )
            },
            field(
                "soundSetting",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_SETTING,
                Material.NOTE_BLOCK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_PARAMETERS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_PARAMETERS,
            ) {
                DisplayValue.SoundSetting(
                    operation = SoundSettings.operation(it),
                    sound = it.string("sound"),
                    volume = it.string("volume", "1.0"),
                    pitch = it.string("pitch", "1.0"),
                    category = it.string("soundCategory"),
                    tempRef = it.soundTempRef?.takeIf(String::isNotBlank),
                )
            },
            field("soundScope", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_SCOPE, Material.GLOBE_BANNER_PATTERN,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_SCOPE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_SCOPE,
            ) { displaySoundScope(it.string("soundScope", "POSITION")) },
        )
        CommandType.PARTICLE -> listOf(
            field(
                "particleDisplay",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DISPLAY,
                Material.ENDER_EYE,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) {
                DisplayValue.ParticleDisplay(
                    targetKind = it.targetSpec?.kind,
                    mode = ParticleGeometry.displayMode(it),
                )
            },
            field(
                ParticleSettings.PARAM_PARTICLE,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE,
                Material.FIREWORK_STAR,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PARTICLE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PARTICLE,
            ) { displayParticle(it) },
            field(
                "position",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) { it.particlePositionSpec?.kind?.let(::displayPosition) ?: displayUnset() },
            field(
                "particleParameters",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_PARAMETERS,
                Material.SNOWBALL,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PARTICLE_PARAMETERS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PARTICLE_PARAMETERS,
            ) {
                DisplayValue.ParticleParameters(
                    it.string(ParticleSettings.PARAM_DELTA_X, "0.0"),
                    it.string(ParticleSettings.PARAM_DELTA_Y, "0.0"),
                    it.string(ParticleSettings.PARAM_DELTA_Z, "0.0"),
                    it.string(ParticleSettings.PARAM_SPEED, "0.0"),
                    it.string(ParticleSettings.PARAM_COUNT, "1"),
                )
            },
            field(
                "geometrySettings",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_SETTINGS,
                Material.FILLED_MAP,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PARTICLE_PARAMETERS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PARTICLE_PARAMETERS,
            ) { displayGeometrySettings(it) },
            field(
                "particleDensity",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DENSITY,
                Material.SUGAR,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PARTICLE_PARAMETERS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PARTICLE_PARAMETERS,
            ),
        )
        CommandType.FIREWORK_LAUNCH -> listOf(
            field(
                "fireworkPosition",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FIREWORK_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SUMMON_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SUMMON_POSITION,
            ) { it.fireworkPositionSpec?.kind?.let(::displayPosition) ?: displayUnset() },
            field(
                "fireworkContents",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_FIREWORK_CONTENTS,
                Material.FIREWORK_ROCKET,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_GIVE_ITEM,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_GIVE_ITEM,
            ) { displayFireworkContents(it) },
            field(
                "burstHeight",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BURST_HEIGHT,
                Material.ARROW,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE,
            ),
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
        // 折り畳みノードの設定欄は折り畳み前の種別と共用します。編集は外側へ行い、
        // 展開時に内部先頭へ反映します。
        CommandType.FOLDED_FOR -> fields(CommandType.FOR_START, node)
        CommandType.FOLDED_IF -> fields(CommandType.CONDITION, node)
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

private fun displaySoundOperation(operation: SoundOperation): DisplayValue = DisplayValue.Localized(
    when (operation) {
        SoundOperation.PLAY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SOUND_PLAY
        SoundOperation.STOP -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SOUND_STOP
    },
)

private fun geometryShapeKey(shape: ParticleShape): LocalizationKey<String> = when (shape) {
    ParticleShape.POINT_SCATTER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARTICLE_POINT
    ParticleShape.LINE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_LINE
    ParticleShape.CIRCLE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_CIRCLE
    ParticleShape.ARC -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_ARC
    ParticleShape.BOX -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_BOX
    ParticleShape.SPHERE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_SPHERE
    ParticleShape.HELIX -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_GEOMETRY_HELIX
}

/** パーティクル種類と詳細データを、一つの設定欄で現在値として表示します。 */
private fun displayParticle(node: CommandNode): DisplayValue {
    val particle = ParticleSettings.particle(node)?.name ?: node.string(ParticleSettings.PARAM_PARTICLE)
    val data = node.string(ParticleSettings.PARAM_DATA).takeIf(String::isNotBlank)
    return displayLiteral(if (data == null) particle else "$particle + $data")
}

/** 図形設定の形状と主要寸法を、一つの設定欄で現在値として表示します。 */
private fun displayGeometrySettings(node: CommandNode): DisplayValue {
    val shape = ParticleGeometry.shape(node)
    if (shape == ParticleShape.POINT_SCATTER) return displayUnset()
    fun param(key: String): String = node.string(key, node.type.defaults[key].orEmpty())
    val summary = when (shape) {
        ParticleShape.LINE -> ""
        ParticleShape.CIRCLE -> "r=${param(ParticleGeometry.PARAM_RADIUS)}"
        ParticleShape.ARC -> "r=${param(ParticleGeometry.PARAM_RADIUS)} ${param(ParticleGeometry.PARAM_START_ANGLE)}-${param(ParticleGeometry.PARAM_END_ANGLE)}"
        ParticleShape.BOX -> ""
        ParticleShape.SPHERE -> "r=${param(ParticleGeometry.PARAM_RADIUS)}"
        ParticleShape.HELIX -> "r=${param(ParticleGeometry.PARAM_RADIUS)} h=${param(ParticleGeometry.PARAM_HEIGHT)} x${param(ParticleGeometry.PARAM_TURNS)}"
        ParticleShape.POINT_SCATTER -> ""
    }
    return DisplayValue.GeometrySettings(shape, summary)
}

/** 花火の中身を、素材IDと設定済み状態で表示します。 */
private fun displayFireworkContents(node: CommandNode): DisplayValue {
    val item = node.string(FireworkSettings.PARAM_ITEM)
    val data = node.string(FireworkSettings.PARAM_DATA)
    if (item.isBlank() && data.isBlank()) return displayUnset()
    return displayLiteral(item.ifBlank { "minecraft:firework_rocket" })
}

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
    FacingKind.TARGET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_TARGET
    FacingKind.COPY_ORIENTATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COPY_ORIENTATION
    FacingKind.TEMPORARY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE
    FacingKind.COORDINATES -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FACE_COORDINATES
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
