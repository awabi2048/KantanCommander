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
import me.awabi2048.kantancommander.model.declaredArguments
import me.awabi2048.kantancommander.model.applyCalledDisk
import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.DiskArgument
import me.awabi2048.kantancommander.item.KantanItemService
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.conditionSubject
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityPropertyChoice
import me.awabi2048.kantancommander.model.EntityPropertySupport
import me.awabi2048.kantancommander.model.EntityPropertyValueKind
import me.awabi2048.kantancommander.model.EntityTagEntries
import me.awabi2048.kantancommander.model.EntityGroupOperation
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.StructureMirror
import me.awabi2048.kantancommander.model.StructureRotation
import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.ParticleGeometry
import me.awabi2048.kantancommander.model.ParticleShape
import me.awabi2048.kantancommander.model.PlaceItemDestination
import me.awabi2048.kantancommander.model.FireworkSettings
import me.awabi2048.kantancommander.model.SoundOperation
import me.awabi2048.kantancommander.model.SoundSettings
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.MyworldSpawnKind
import com.awabi2048.ccsystem.api.localization.generated.MyworldGuiSettingsKeys
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableChangeMode
import me.awabi2048.kantancommander.model.ValueType
import me.awabi2048.kantancommander.model.VariableScope
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
                    // issue #13: 簡易分類のタブ切替です。制御・変数系はフルGUIへ
                    // 委ねるため、簡易分類以外の指定は無視します。
                    "tab" to MenuActionHandler { context ->
                        val category = context.payload[PICKER_CATEGORY]
                            ?.let { runCatching { CommandCategory.valueOf(it) }.getOrNull() }
                            ?.takeIf { it.isSimple() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        MenuActionResult.Success(
                            MenuUpdate.Replace(
                                context.route.copy(payload = context.route.payload + (PICKER_CATEGORY to category.name)),
                            ),
                        )
                    },
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
                        val kind = context.payload["kind"]
                            ?.let { runCatching { TargetKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        // issue #29: 4プリセットを正規仕様で保存します。
                        // 旧3種以外の種別はプリセット経路で扱いません。
                        val preset = SettingPresets.TargetPreset.entries.firstOrNull { preset ->
                            presetTargetKind(preset) == kind
                        } ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (node(context.route) == null) return@MenuActionHandler MenuActionResult.Ignored
                        // issue #29: 選択済みプリセットの再クリックで距離を開きます。
                        // 付近系だけが詳細を持ち、それ以外は正規仕様へ設定します。
                        val matchedPreset = SettingPresets.matchTargetPreset(effectiveTargetSpec(context.route))
                        if ((kind == TargetKind.NEARBY_PLAYERS || kind == TargetKind.NEARBY_ENTITIES) &&
                            matchedPreset?.let { presetTargetKind(it) == kind } == true
                        ) {
                            openTargetDistanceForm(context.player, context.route)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val embedRole = embeddedPositionRole(context.route)
                        if (embedRole != null) {
                            // 位置に内包する対象を同じ選択経路で設定します。
                            // 単一限定では複数になり得る候補を拒否します。
                            if (MultiPositionPolicy.multiplicityFor(embedRole) == PositionMultiplicity.SINGLE &&
                                preset != SettingPresets.TargetPreset.NEAREST_PLAYER
                            ) {
                                return@MenuActionHandler MenuActionResult.Ignored
                            }
                            if (!updateNode(context.player, context.route) { node ->
                                    val base = CommandSettingsModel.positionSpec(node, embedRole)
                                        ?.takeIf { base -> base.kind == PositionKind.TARGET }
                                        ?: PositionSpec(PositionKind.TARGET)
                                    CommandSettingsModel.setPositionSpec(
                                        node,
                                        embedRole,
                                        base.copy(
                                            kind = PositionKind.TARGET,
                                            target = SettingPresets.targetSpecFor(preset),
                                        ),
                                    )
                                }) {
                                return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            }
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(genericPositionRoute(context.route, embedRole.routeValue)),
                            )
                        }
                        val routeRole = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                        // プリセット選択は絞り込み条件を引き継がず、種別の既定値から
                        // 作り直します。簡易GUIでは隠れた条件を表示・編集できないため、
                        // 見えている選択と保存値がずれないことを優先します。
                        // issue #34-1: 内包役割へは内包対象として保存します。
                        if (!updateNode(context.player, context.route) { node ->
                                if (CommandSettingsModel.isEmbeddedTargetRole(routeRole)) {
                                    CommandSettingsModel.setEmbeddedTargetSpec(
                                        node,
                                        routeRole,
                                        SettingPresets.withoutPresetConversion(SettingPresets.targetSpecFor(preset)),
                                    )
                                } else {
                                    CommandSettingsModel.setTargetSpec(
                                        node,
                                        routeRole,
                                        SettingPresets.targetSpecFor(preset),
                                    )
                                }
                            }) {
                                return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                        }
                        MenuActionResult.Success(MenuUpdate.Replace(context.route))
                    },
                    // 「付近」の距離だけを数値入力で調整します。
                    "distance" to MenuActionHandler { context ->
                        val kind = effectiveTargetSpec(context.route)?.kind
                        if (kind != TargetKind.NEARBY_PLAYERS && kind != TargetKind.NEARBY_ENTITIES) {
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
                        // issue #8: 呼び出し設定は制御系ですが両GUIで編集します。
                        // ディスク選択は手持ち入力、引数・戻り値は専用画面へ進みます。
                        if (node.type == CommandType.DISK_CALL && field == "diskId") {
                            return@MenuActionHandler setHeldDisk(context)
                        }
                        if (node.type == CommandType.DISK_CALL && field == "diskCallSettings") {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, DISK_CALL_SETTINGS_ID))
                            )
                        }
                        if (!CommandPresentationPolicy.isSimpleEditable(node.type)) {
                            // 古い経路から制御系ノードの設定画面へ届いた操作は受け付けません。
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (settingsFields(node).none { it.key == field }) {
                            // 依存値の変更後に残った古い設定タブからの操作を受け付けず、
                            // 表示と同じ共通フィールド集合を保存入口でも適用します。
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        // issue #33-2: 画面を開く操作では永続値を変更しません。
                        // 一時変数参照の解除は直接値の確定時（setParameter）にのみ行い、
                        // キャンセル・戻る・切断・入力失敗では元の参照と値を保ちます。
                        // 直接値の保存入口（showFieldForm・選択確定・手持ち設定）は
                        // いずれも setParameter 経由で参照解除を伴うため、ここでの
                        // 事前保存は不要です。
                        if (field == "item" && (node.type == CommandType.GIVE_ITEM ||
                                node.type == CommandType.PLACE_ITEM)) {
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
                                        "dismount" -> "motion"
                                        // 旧equip／tagは新規選択肢に出さず、循環後はrideへ戻します。
                                        else -> "ride"
                                    },
                                )
                            }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        // 統合選択形式では設定対象の一覧画面へ遷移します。
                        // 旧3択の循環・modifier循環は廃止しました。
                        if (field == EntityDataPath.PARAM_PATH && node.type == CommandType.ENTITY_OPERATION) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, ENTITY_DATA_ROOT_ID)),
                            )
                        }
                        // タグ一覧画面へ遷移します。追加・モード切替・削除は一覧画面で行います。
                        if (field == EntityTagEntries.PARAM_ENTRIES && node.type == CommandType.ENTITY_OPERATION) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, ENTITY_TAG_LIST_ID)),
                            )
                        }
                        // attribute値はDialog入力で編集します。ID自体は一覧選択が正本です。
                        if (field == EntityAttributeSupport.PARAM_VALUE && node.type == CommandType.ENTITY_OPERATION) {
                            showFieldForm(context.player, context.route, field, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        // 設定値の編集は項目の型で振り分けます。
                        // 真偽値は直接切替、有限候補は一覧選択、文字列・整数は入力画面です。
                        if (field == EntityDataSchema.PARAM_VALUE && node.type == CommandType.ENTITY_OPERATION) {
                            val propertyId = EntityDataPath.propertyIdOf(node.string(EntityDataPath.PARAM_PATH))
                            val definition = propertyId?.let { EntityDataSchema.definition(it) }
                            when (definition?.valueKind) {
                                EntityPropertyValueKind.BOOLEAN -> {
                                    if (!updateNode(context.player, context.route) {
                                        CommandSettingsModel.setParameter(
                                            it,
                                            EntityDataSchema.PARAM_VALUE,
                                            (!(it.string(EntityDataSchema.PARAM_VALUE).toBooleanStrictOrNull() ?: false)).toString(),
                                        )
                                    }) return@MenuActionHandler MenuActionResult.Rejected(KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED))
                                    return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                                }
                                EntityPropertyValueKind.ENUM -> {
                                    return@MenuActionHandler MenuActionResult.Success(
                                        MenuUpdate.Navigate(choiceRoute(context.route, ENTITY_PROPERTY_VALUE_ID)),
                                    )
                                }
                                else -> {
                                    showFieldForm(context.player, context.route, field, node)
                                    return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                                }
                            }
                        }
                        // issue #10: 召喚時個別設定の一覧画面へ遷移します。
                        if (field == "summonData" && node.type == CommandType.SUMMON_ENTITY) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, SUMMON_DATA_ROOT_ID)),
                            )
                        }
                        // 旧equipのスロット選択はPLACE_ITEMへ移行したため削除します。
                        // 配置スロットは配置先で入力経路が変わります。装備は既存の
                        // 装備選択、数値スロットは1始まり表示の数値入力を使います。
                        // 額縁は固定のため欄自体を出さず、ここへは到達しません。
                        if (field == "placeDestination" && node.type == CommandType.PLACE_ITEM) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, PLACE_DESTINATION_ID)),
                            )
                        }
                        if (field == "slot" && node.type == CommandType.PLACE_ITEM) {
                            val destination = PlaceItemDestination.from(node.string("placeDestination"))
                            if (destination == PlaceItemDestination.EQUIPMENT) {
                                return@MenuActionHandler MenuActionResult.Success(
                                    MenuUpdate.Navigate(choiceRoute(context.route, ENTITY_EQUIPMENT_SLOT_ID)),
                                )
                            }
                            showPlaceSlotForm(context.player, context.route, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if (field == "overwrite" && (node.type == CommandType.ENTITY_ACTION || node.type == CommandType.PLACE_ITEM)) {
                            if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "overwrite", (!it.boolean("overwrite")).toString())
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
                        if (field == "particleDisplay" && (node.type == CommandType.PARTICLE || node.type == CommandType.PARTICLE_GEOMETRY)) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, PARTICLE_DISPLAY_ID))
                            )
                        }
                        if (field == "particle" && (node.type == CommandType.PARTICLE || node.type == CommandType.PARTICLE_GEOMETRY)) {
                            showParticleForm(context.player, context.route, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        // #30: 図形の設定は二段階です。未選択時は図形選択へ、選択済みは
                        // その図形固有の詳細設定（形状変更＋数値）へ進みます。
                        if (field == "geometrySettings" && node.type == CommandType.PARTICLE_GEOMETRY) {
                            val target = if (ParticleGeometry.shape(node) == ParticleShape.POINT_SCATTER) {
                                choiceRoute(context.route, PARTICLE_SHAPE_ID)
                            } else {
                                choiceRoute(context.route, GEOMETRY_SETTINGS_ID)
                            }
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(target)
                            )
                        }
                        if (field == "geometryEnd" && node.type == CommandType.PARTICLE_GEOMETRY) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(genericPositionRoute(context.route, "particle_end"))
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
                        // issue #26: 実行位置は汎用位置画面へ進みます。
                        // 制御系には表示しないため、ここへ届くのは対応コマンドだけです。
                        if (field == "executionPosition") {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(genericPositionRoute(context.route, "execution_position"))
                            )
                        }
                        // issue #33-1: 建造物・群操作の設定項目へ操作・保存経路を接続します。
                        // 表示されるのに操作が Ignored へ落ちる接続漏れを解消します。
                        // 選択肢項目は専用子画面、名前・軸入力は汎用入力へ進めます。
                        if (field == "rotation" && node.type == CommandType.STRUCTURE_PLACE) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, STRUCTURE_ROTATION_ID))
                            )
                        }
                        if (field == "mirror" && node.type == CommandType.STRUCTURE_PLACE) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, STRUCTURE_MIRROR_ID))
                            )
                        }
                        if (field == "operation" && node.type == CommandType.ENTITY_GROUP) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, ENTITY_GROUP_OPERATION_ID))
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
                        // issue #33-1: 汎用テキスト入力の受付は入力仕様の有無で判定します。
                        // 項目名の手動列挙と入力仕様が別管理だと、新設定の追加時に
                        // 表示だけ増えて操作が Ignored へ落ちる接続漏れが起きます。
                        // 専用画面・直接操作を持つ項目は上流の分岐で処理済みのため、
                        // ここへ届くのは汎用入力だけです。建造物名・群名・軸入力・
                        // 表示時間などもこの経路で編集します。
                        if (CommandDialogSpecs.field(node, field) != null) {
                            showFieldForm(context.player, context.route, field, node)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        if ((node.type == CommandType.BLOCK_OPERATION ||
                                (node.type == CommandType.PLACE_ITEM &&
                                    PlaceItemDestination.from(node.string("placeDestination")) == PlaceItemDestination.BLOCK_CONTAINER)) &&
                            (field == "position" || field == "from" || field == "to")
                        ) {
                            // ブロック操作とアイテム配置の配置位置・範囲は座標の数値入力で指定します。
                            openBlockPositionForm(context.player, context.route, field)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        val target = when {
                            field == "destination" -> genericPositionRoute(context.route, "destination")
                            field == "target" || field == "subject" -> targetRoute(context.route, "node_target")
                            field == "other" && node.type == CommandType.ENTITY_ACTION ->
                                targetRoute(context.route, "secondary_target")
                            // issue #29: 効果音・パーティクル・召喚の位置は汎用プリセット画面へ進みます。
                            // #30: 図形の中心・始点も同じ表示位置画面を使います。
                            field == "soundPosition" -> genericPositionRoute(context.route, "sound_position")
                            field == "position" && (node.type == CommandType.PARTICLE || node.type == CommandType.PARTICLE_GEOMETRY) ->
                                genericPositionRoute(context.route, "particle_position")
                            field == "summonPosition" -> genericPositionRoute(context.route, "summon_position")
                            // issue #33-1: 建造物の範囲・設置位置、群pivotも汎用位置画面へ進みます。
                            // 保存先の役割は汎用位置画面側で解決し、通常対象への取り違えは
                            // #34-1 の役割厳密化で防ぎます。
                            field == "from" && node.type == CommandType.STRUCTURE_SAVE ->
                                genericPositionRoute(context.route, CommandSettingRole.BLOCK_FROM.routeValue)
                            field == "to" && node.type == CommandType.STRUCTURE_SAVE ->
                                genericPositionRoute(context.route, CommandSettingRole.BLOCK_TO.routeValue)
                            field == "structurePlace" && node.type == CommandType.STRUCTURE_PLACE ->
                                genericPositionRoute(context.route, CommandSettingRole.STRUCTURE_PLACE_POSITION.routeValue)
                            field == "pivot" && node.type == CommandType.ENTITY_GROUP ->
                                genericPositionRoute(context.route, CommandSettingRole.GROUP_PIVOT.routeValue)
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
        // issue #33-1: 建造物の回転・反転、群操作の選択子画面です。
        // 表示される設定項目に操作・保存経路がない接続漏れを解消します。
        // 保存規則はジェスチャーGUIと同一（定数IDのみ受付）です。
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                STRUCTURE_ROTATION_ID,
                renderer = { renderStructureRotations(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val rotation = context.payload["rotation"]
                            ?.let(StructureRotation::from)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "rotation", rotation.value)
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
                STRUCTURE_MIRROR_ID,
                renderer = { renderStructureMirrors(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val mirror = context.payload["mirror"]
                            ?.let(StructureMirror::from)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "mirror", mirror.value)
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
                ENTITY_GROUP_OPERATION_ID,
                renderer = { renderEntityGroupOperations(it.player) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val operation = context.payload["operation"]
                            ?.let(EntityGroupOperation::from)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "operation", operation.value)
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
                ENTITY_DATA_ROOT_ID,
                renderer = { renderEntityDataRoot(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "page" to entityPageAction(),
                    "select" to MenuActionHandler { context ->
                        val selection = context.payload["selection"]
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val current = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        when {
                            selection == "tag" -> {
                                if (!updateNode(context.player, context.route) {
                                        CommandSettingsModel.setParameter(it, EntityDataPath.PARAM_PATH, EntityDataPath.TAG)
                                    }) {
                                    return@MenuActionHandler MenuActionResult.Rejected(
                                        KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                                    )
                                }
                                MenuActionResult.Success(
                                    MenuUpdate.Navigate(choiceRoute(context.route, ENTITY_TAG_LIST_ID)),
                                )
                            }
                            selection == "attribute" -> MenuActionResult.Success(
                                MenuUpdate.Navigate(choiceRoute(context.route, ENTITY_ATTRIBUTE_SELECT_ID)),
                            )
                            selection.startsWith("property:") -> {
                                val propertyId = selection.removePrefix("property:")
                                val definition = EntityDataSchema.definition(propertyId)
                                    ?: return@MenuActionHandler MenuActionResult.Ignored
                                val previous = EntityDataPath.propertyIdOf(current.string(EntityDataPath.PARAM_PATH))
                                if (!updateNode(context.player, context.route) {
                                        CommandSettingsModel.setParameter(it, EntityDataPath.PARAM_PATH, EntityDataPath.propertyPath(propertyId))
                                        // 項目切替時は値をクリアし、古い項目の値残存を防ぎます。
                                        if (previous != null && previous != propertyId) {
                                            CommandSettingsModel.setParameter(it, EntityDataSchema.PARAM_VALUE, "")
                                        }
                                    }) {
                                    return@MenuActionHandler MenuActionResult.Rejected(
                                        KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                                    )
                                }
                                when (definition.valueKind) {
                                    // 真偽値は選択→クリックで直接切り替えます。子画面を持ちません。
                                    EntityPropertyValueKind.BOOLEAN -> {
                                        if (!updateNode(context.player, context.route) {
                                                val toggled = if (previous == propertyId) {
                                                    (!(it.string(EntityDataSchema.PARAM_VALUE).toBooleanStrictOrNull() ?: false)).toString()
                                                } else "true"
                                                CommandSettingsModel.setParameter(it, EntityDataSchema.PARAM_VALUE, toggled)
                                            }) {
                                            return@MenuActionHandler MenuActionResult.Rejected(
                                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                                            )
                                        }
                                        MenuActionResult.Success(MenuUpdate.Refresh)
                                    }
                                    EntityPropertyValueKind.ENUM ->
                                        MenuActionResult.Success(
                                            MenuUpdate.Navigate(choiceRoute(context.route, ENTITY_PROPERTY_VALUE_ID)),
                                        )
                                    EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> {
                                        val fresh = node(context.route)
                                            ?: return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Back)
                                        showFieldForm(context.player, context.route, EntityDataSchema.PARAM_VALUE, fresh)
                                        MenuActionResult.Success(MenuUpdate.None)
                                    }
                                }
                            }
                            else -> MenuActionResult.Ignored
                        }
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                ENTITY_ATTRIBUTE_SELECT_ID,
                renderer = { renderEntityAttributeSelect(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "page" to entityPageAction(),
                    "select" to MenuActionHandler { context ->
                        val attributeId = context.payload["attribute"]
                            ?.takeIf(EntityAttributeSupport::isAttributeId)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, EntityDataPath.PARAM_PATH, EntityDataPath.attributePath(attributeId))
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        // 選択→変更後の値入力フローです。値Dialogを直接開きます。
                        val fresh = node(context.route)
                            ?: return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Back)
                        showFieldForm(context.player, context.route, EntityAttributeSupport.PARAM_VALUE, fresh)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                ENTITY_TAG_LIST_ID,
                renderer = { renderEntityTagList(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "page" to entityPageAction(),
                    "select" to MenuActionHandler { context ->
                        val payload = context.payload
                        // 新規タグ入力行です。入力値をaddモードで追記します。
                        if (payload["add"] != null) {
                            val current = node(context.route)
                                ?: return@MenuActionHandler MenuActionResult.Ignored
                            showTagEntryForm(context.player, context.route, current)
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.None)
                        }
                        // カードのモードボタンです。add⇔removeを切り替えます。
                        val modeIndex = payload["mode"]?.toIntOrNull()
                        if (modeIndex != null) {
                            val current = node(context.route)
                                ?.let { EntityTagEntries.parse(it.string(EntityTagEntries.PARAM_ENTRIES)) }
                                ?: return@MenuActionHandler MenuActionResult.Ignored
                            val target = current.getOrNull(modeIndex) ?: return@MenuActionHandler MenuActionResult.Ignored
                            val toggled = if (target.mode == EntityTagEntries.MODE_ADD) EntityTagEntries.MODE_REMOVE else EntityTagEntries.MODE_ADD
                            val updated = current.toMutableList().also { it[modeIndex] = target.copy(mode = toggled) }
                            if (!EntityTagEntries.isValid(updated)) return@MenuActionHandler MenuActionResult.Ignored
                            if (!updateNode(context.player, context.route) {
                                    CommandSettingsModel.setParameter(it, EntityTagEntries.PARAM_ENTRIES, EntityTagEntries.encode(updated))
                                }) {
                                return@MenuActionHandler MenuActionResult.Rejected(
                                    KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                                )
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Refresh)
                        }
                        MenuActionResult.Ignored
                    },
                    // カードの削除ボタンです。該当エントリのみ削除します。
                    "delete" to MenuActionHandler { context ->
                        val index = context.payload["index"]?.toIntOrNull()
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val current = node(context.route)
                            ?.let { EntityTagEntries.parse(it.string(EntityTagEntries.PARAM_ENTRIES)) }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (index !in current.indices) return@MenuActionHandler MenuActionResult.Ignored
                        val updated = current.toMutableList().also { it.removeAt(index) }
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, EntityTagEntries.PARAM_ENTRIES, EntityTagEntries.encode(updated))
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                ENTITY_PROPERTY_VALUE_ID,
                renderer = { renderEntityPropertyValues(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val choice = context.payload["choice"]
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val propertyId = node(context.route)
                            ?.let { EntityDataPath.propertyIdOf(it.string(EntityDataPath.PARAM_PATH)) }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val current = EntityDataSchema.definition(propertyId)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        // 有限候補のみ選択適用します。真偽値・文字列・整数は別経路で編集します。
                        if (current.valueKind != EntityPropertyValueKind.ENUM) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (current.enumChoices.none { it.id == choice }) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, EntityDataSchema.PARAM_VALUE, choice)
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
                SUMMON_DATA_ROOT_ID,
                renderer = { renderSummonDataRoot(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "page" to entityPageAction(),
                    "select" to MenuActionHandler { context ->
                        val propertyId = context.payload["property"]
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val current = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (current.type != CommandType.SUMMON_ENTITY) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        val definition = EntityDataSchema.definition(propertyId)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        // 召喚種類の変更後に残った非対応項目は選択できません。
                        // 検証層でも静的対象外として検出します。
                        val summonType = EntityDataSchema.knownSummonType(current.string("summonEntity"))
                        if (EntityDataSchema.staticSupport(summonType, propertyId) == EntityPropertySupport.STATICALLY_UNSUPPORTED) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        val key = EntityDataSchema.summonDataKey(propertyId)
                        when (definition.valueKind) {
                            // 真偽値は選択→クリックで直接切り替えます。子画面を持ちません。
                            EntityPropertyValueKind.BOOLEAN -> {
                                if (!updateNode(context.player, context.route) {
                                        val toggled = (!(it.string(key).toBooleanStrictOrNull() ?: false)).toString()
                                        CommandSettingsModel.setParameter(it, key, toggled)
                                    }) {
                                    return@MenuActionHandler MenuActionResult.Rejected(
                                        KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                                    )
                                }
                                MenuActionResult.Success(MenuUpdate.Refresh)
                            }
                            EntityPropertyValueKind.ENUM -> MenuActionResult.Success(
                                MenuUpdate.Navigate(
                                    choiceRoute(context.route, SUMMON_PROPERTY_VALUE_ID)
                                        .copy(payload = context.route.payload + ("summonProperty" to propertyId)),
                                ),
                            )
                            EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> {
                                val fresh = node(context.route)
                                    ?: return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Back)
                                showFieldForm(context.player, context.route, key, fresh)
                                MenuActionResult.Success(MenuUpdate.None)
                            }
                        }
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                SUMMON_PROPERTY_VALUE_ID,
                renderer = { renderSummonPropertyValues(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "select" to MenuActionHandler { context ->
                        val choice = context.payload["choice"]
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val propertyId = context.route.payload["summonProperty"]
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val current = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (current.type != CommandType.SUMMON_ENTITY) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        val definition = EntityDataSchema.definition(propertyId)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        // 有限候補のみ選択適用します。真偽値・文字列・整数は別経路で編集します。
                        if (definition.valueKind != EntityPropertyValueKind.ENUM) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (definition.enumChoices.none { it.id == choice }) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, EntityDataSchema.summonDataKey(propertyId), choice)
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
                PLACE_DESTINATION_ID,
                renderer = { renderPlaceDestinations(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "toggleOverwrite" to MenuActionHandler { context ->
                        if (!updateNode(context.player, context.route) {
                                CommandSettingsModel.setParameter(it, "overwrite", (!it.boolean("overwrite")).toString())
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        // 配置先画面に留まり、上書き表示だけを更新します。
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "select" to MenuActionHandler { context ->
                        val destination = PlaceItemDestination.from(context.payload["placeDestination"])
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) {
                                // 配置先の切替えで要求対象が変わるため、スロットは
                                // 既定値へ戻して不正な組合せを残しません。
                                CommandSettingsModel.setParameter(it, "placeDestination", destination.paramValue)
                                CommandSettingsModel.setParameter(it, "slot", PlaceItemDestination.defaultSlot(destination))
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
                DISK_CALL_SETTINGS_ID,
                renderer = { renderDiskCallSettings(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "arg" to MenuActionHandler { context ->
                        val name = context.payload["name"]?.takeIf(String::isNotBlank)
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val argument = node(context.route)?.declaredArguments()
                            ?.firstOrNull { it.name == name }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        openDiskCallArgumentForm(context.player, context.route, argument)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "returnScope" to MenuActionHandler { context ->
                        val current = node(context.route) ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (current.string("returnName").isBlank()) {
                            // 受取先がない場合は変更しません。
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        val next = if (current.string("returnScope")
                                .ifBlank { VariableScope.EXECUTION.name } == VariableScope.WORLD.name
                        ) {
                            VariableScope.EXECUTION.name
                        } else {
                            VariableScope.WORLD.name
                        }
                        if (!updateNode(context.player, context.route) {
                                it.params["returnScope"] = next
                                it.markConfigured("returnScope")
                            }
                        ) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        MenuActionResult.Success(MenuUpdate.Refresh)
                    },
                    "returnName" to MenuActionHandler { context ->
                        openDiskCallReturnNameForm(context.player, context.route)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                    "argPage" to MenuActionHandler { context ->
                        val page = context.payload["page"]?.toIntOrNull()
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(
                                context.route.copy(payload = context.route.payload + ("argPage" to page.toString())),
                            ),
                        )
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
                    // issue #29: 位置プリセットは選択済みの再クリックで詳細を開きます。
                    // 移動先の最寄り・付近は対象指定へ保存します。
                    "preset" to MenuActionHandler { context ->
                        val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val preset = context.payload["preset"]
                            ?.let { runCatching { SettingPresets.PositionPreset.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val currentNode = node(context.route)
                        val matched = if (role == CommandSettingRole.DESTINATION && currentNode != null) {
                            SettingPresets.matchDestinationPreset(
                                CommandSettingsModel.targetSpec(currentNode, role),
                                CommandSettingsModel.positionSpec(currentNode, role),
                            )
                        } else {
                            currentNode?.let { CommandSettingsModel.positionSpec(it, role) }
                                ?.let(SettingPresets::matchPositionPreset)
                        } == preset
                        // issue #29: 付近系プリセットは複数になり得るため単一限定では拒否します。
                        // 最寄り等の静的0..1は許可します。移動先は対象指定のため対象外です。
                        if (preset == SettingPresets.PositionPreset.NEARBY_PLAYERS &&
                            role != CommandSettingRole.DESTINATION &&
                            !CommandSettingAvailabilityPolicy.isEmbeddedTargetPositionEnabled(role)
                        ) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        if (matched) {
                            // 選択済みの再クリックで詳細を開きます。
                            return@MenuActionHandler when (preset) {
                                SettingPresets.PositionPreset.NEARBY_PLAYERS -> {
                                    if (role == CommandSettingRole.DESTINATION) {
                                        MenuActionResult.Success(
                                            MenuUpdate.Navigate(targetRoute(context.route, role.routeValue)),
                                        )
                                    } else {
                                        openPositionTargetDistanceForm(context.player, context.route, role)
                                        MenuActionResult.Success(MenuUpdate.None)
                                    }
                                }
                                SettingPresets.PositionPreset.MYWORLD_SPAWN ->
                                    MenuActionResult.Success(
                                        MenuUpdate.Navigate(choiceRoute(context.route, SPAWN_KIND_ID)),
                                    )
                                SettingPresets.PositionPreset.COORDINATES -> {
                                    openCoordinatePositionForm(context.player, context.route, role)
                                    MenuActionResult.Success(MenuUpdate.None)
                                }
                                else -> MenuActionResult.Success(MenuUpdate.Replace(context.route))
                            }
                        }
                        if (role == CommandSettingRole.DESTINATION &&
                            (preset == SettingPresets.PositionPreset.NEAREST_PLAYER ||
                                preset == SettingPresets.PositionPreset.NEARBY_PLAYERS)
                        ) {
                            val targetPreset = if (preset == SettingPresets.PositionPreset.NEARBY_PLAYERS) {
                                SettingPresets.TargetPreset.NEARBY_PLAYERS
                            } else {
                                SettingPresets.TargetPreset.NEAREST_PLAYER
                            }
                            if (!updateNode(context.player, context.route) { node ->
                                    CommandSettingsModel.setTargetSpec(
                                        node,
                                        role,
                                        SettingPresets.targetSpecFor(targetPreset),
                                    )
                                }) {
                                return@MenuActionHandler MenuActionResult.Rejected(
                                    KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                                )
                            }
                            return@MenuActionHandler MenuActionResult.Success(MenuUpdate.Back)
                        }
                        if (!updateNode(context.player, context.route) { node ->
                                CommandSettingsModel.setPositionSpec(
                                    node,
                                    role,
                                    SettingPresets.positionSpecFor(preset),
                                )
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                    "spawn" to MenuActionHandler { context ->
                        val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val spawn = context.payload["spawn"]
                            ?.let { runCatching { MyworldSpawnKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) { node ->
                                CommandSettingsModel.setPositionSpec(
                                    node,
                                    role,
                                    PositionSpec(PositionKind.MYWORLD_SPAWN, myworldSpawn = spawn),
                                )
                            }) {
                            return@MenuActionHandler MenuActionResult.Rejected(
                                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
                            )
                        }
                        MenuActionResult.Success(MenuUpdate.Back)
                    },
                    "entity" to MenuActionHandler { context ->
                        val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        // issue #29: 移動先は対象指定画面へ進み、それ以外は内包経路へ進みます。
                        if (role == CommandSettingRole.DESTINATION) {
                            return@MenuActionHandler MenuActionResult.Success(
                                MenuUpdate.Navigate(targetRoute(context.route, role.routeValue)),
                            )
                        }
                        if (!CommandSettingAvailabilityPolicy.isEmbeddedTargetPositionEnabled(role)) {
                            return@MenuActionHandler MenuActionResult.Ignored
                        }
                        MenuActionResult.Success(
                            MenuUpdate.Navigate(embeddedTargetRoute(context.route, role.routeValue)),
                        )
                    },
                    "temporary" to MenuActionHandler { context ->
                        val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        openPositionTemporaryForm(context.player, context.route, role)
                        MenuActionResult.Success(MenuUpdate.None)
                    },
                ),
            )
        )
        runtime.register(
            InventoryMenuDefinition(
                SequenceEditorMenu.OWNER,
                SPAWN_KIND_ID,
                renderer = { renderSpawnKind(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "spawn" to MenuActionHandler { context ->
                        val role = CommandSettingRole.fromRoute(context.route.payload[ROLE])
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        val spawn = context.payload["spawn"]
                            ?.let { runCatching { MyworldSpawnKind.valueOf(it) }.getOrNull() }
                            ?: return@MenuActionHandler MenuActionResult.Ignored
                        if (!updateNode(context.player, context.route) { node ->
                                CommandSettingsModel.setPositionSpec(
                                    node,
                                    role,
                                    PositionSpec(PositionKind.MYWORLD_SPAWN, myworldSpawn = spawn),
                                )
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
                PARTICLE_DISPLAY_ID,
                renderer = { renderParticleDisplay(it.player, it.route) },
                actions = mapOf(
                    "back" to back(),
                    "target" to MenuActionHandler { context ->
                        MenuActionResult.Success(MenuUpdate.Navigate(targetRoute(context.route, "node_target")))
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
        // issue #13: 簡易GUIでは簡易分類（基礎・Entity・建築・演出）のタブで
        // 選択します。分類ごとの候補は固定14枠に収まるため、ページングは
        // 行いません。制御・変数系の追加はフルGUI（ジェスチャーGUI）へ委ねます。
        // 旧来の全件固定表示は19件が14枠を超えて破綻していたため廃止します。
        val categories = CommandCategory.entries.filter { it.isSimple() }
        val selected = route.payload[PICKER_CATEGORY]
            ?.let { runCatching { CommandCategory.valueOf(it) }.getOrNull() }
            ?.takeIf { it.isSimple() }
            ?: CommandCategory.BASIC
        val types = CommandPresentationPolicy.SIMPLE_TYPES.filter { CommandPresentationPolicy.category(it) == selected }
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
        categories.forEachIndexed { index, category ->
            val active = category == selected
            elements += KcGui.menuEntry(
                player = player,
                slot = CommandPickerLayoutPolicy.categorySlots[index],
                material = if (active) Material.LIME_CONCRETE else Material.GRAY_CONCRETE,
                name = KcI18n.text(player, category.labelKey),
                style = if (active) GuiNameStyle.PRIMARY else GuiNameStyle.MUTED,
                description = KcI18n.list(player, category.descriptionKey),
                actions = listOf(GuiMenuActionIntent.AnyClick(
                    actionId = "tab",
                    label = KcI18n.text(player, category.labelKey),
                    payload = mapOf(PICKER_CATEGORY to category.name),
                )),
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
        if (!CommandPresentationPolicy.isSimpleEditable(node.type) && node.type != CommandType.DISK_CALL) {
            // 古い経路から制御系ノードの設定画面へ届いた場合は表示のみとし、
            // 設定変更・削除の入口を作りません。
            // issue #8: 呼び出し設定は制御系ですが両GUIで編集するため除外します。
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
            // 種別名はバニラ翻訳のComponentで渡し、CC-System側でComponent行へ変換します。
            val data = display.rows(player)?.map { row ->
                GuiMenuEntryData(row.label, row.value)
            } ?: listOf(when (display) {
                is DisplayValue.EntityTypeName -> GuiMenuEntryData(
                    KcI18n.text(player, field.label),
                    display.entityComponent(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                )
                else -> GuiMenuEntryData(
                    KcI18n.text(player, field.label),
                    display.render(player),
                )
            })
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
        // 簡易GUIでは制御系コマンドを削除できないため、DISK_CALLの設定は許可しても
        // 削除項目は出しません。
        if (CommandPresentationPolicy.isSimpleEditable(node.type)) {
            elements += choiceElement(
                player = player,
                slot = 53,
                material = Material.RED_CONCRETE,
                name = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_DELETE_COMMAND),
                actionId = "delete",
                payload = mapOf(NODE_ID to node.id.toString()),
                style = GuiNameStyle.DANGER,
            )
        }
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
        // issue #29: 簡易GUIでは4プリセットから選択します。複雑な条件指定は行えず、
        // プリセット選択時に隠れた絞り込み条件は引き継ぎません。
        // 内包経路では位置に内包する対象を表示・編集します。
        val current = effectiveTargetSpec(route)
        val matched = SettingPresets.matchTargetPreset(current)
        // issue #29: 内包経路の単一限定では、複数になり得る候補だけを無効化し、
        // 静的0..1の選択は許可します。
        val embedSingle = embeddedPositionRole(route)?.let {
            MultiPositionPolicy.multiplicityFor(it) == PositionMultiplicity.SINGLE
        } == true
        val options = listOf(
            Triple(SettingPresets.TargetPreset.NEARBY_PLAYERS, Material.COMPASS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS),
            Triple(SettingPresets.TargetPreset.MYWORLD_PLAYERS, Material.GLOBE_BANNER_PATTERN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ALL_PLAYERS),
            Triple(SettingPresets.TargetPreset.NEAREST_PLAYER, Material.PLAYER_HEAD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER),
            Triple(SettingPresets.TargetPreset.NEARBY_ENTITIES, Material.ZOMBIE_SPAWN_EGG, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_ENTITIES),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(
            options.size + if (current?.kind == TargetKind.NEARBY_PLAYERS || current?.kind == TargetKind.NEARBY_ENTITIES) 1 else 0,
        )
        val elements = options.mapIndexed { index, option ->
            val multiCapable = option.first != SettingPresets.TargetPreset.NEAREST_PLAYER
            val enabled = !embedSingle || !multiCapable
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                KcI18n.text(player, option.third),
                "select",
                mapOf("kind" to presetTargetKind(option.first).name),
                dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                dataValue = if (matched == option.first) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECTED)
                } else null,
                enabled = enabled,
                disabledDescription = if (enabled) null
                else listOf(CommandSettingAvailabilityPolicy.MULTI_POSITION_DISABLED_HOVER),
            )
        }.toMutableList()
        // 「付近」の距離だけを数値入力で調整します。
        if (current?.kind == TargetKind.NEARBY_PLAYERS || current?.kind == TargetKind.NEARBY_ENTITIES) {
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

    /** 呼び出すディスクは手持ちの実物から取得します。引数宣言も同時に転記します。 */
    private fun setHeldDisk(context: MenuActionContext): MenuActionResult {
        val diskId = KantanItemService.diskId(context.player.inventory.itemInMainHand)
        if (diskId == null) {
            context.player.sendMessage(
                KcI18n.text(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_HELD_DISK_REQUIRED),
            )
            return MenuActionResult.Ignored
        }
        val disk = plugin.scripts.load(diskId)
        if (disk == null) {
            context.player.sendMessage(
                KcI18n.text(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_HELD_DISK_REQUIRED),
            )
            return MenuActionResult.Ignored
        }
        if (!updateNode(context.player, context.route, configuredFields = setOf("diskId")) {
                it.applyCalledDisk(diskId, disk)
            }
        ) {
            return MenuActionResult.Rejected(
                KcI18n.component(context.player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_SAVE_FAILED),
            )
        }
        context.player.sendMessage(
            KcI18n.text(
                context.player,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_MESSAGE_DISK_SET,
                mapOf("disk" to disk.name),
            ),
        )
        return MenuActionResult.Success(MenuUpdate.Refresh)
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

    /**
     * 配置先の選択肢を描画します。
     *
     * 5つの配置先は対象の能力ごとに分かれ、実行時に適用可否を判定します。
     * 選択肢の文言は配置先 adapter の表示キーへ一元化します。
     */
    /**
     * 配置先の5択に加え、上書き設定の単一トグルを中央下段へ置きます。
     *
     * 上書き設定は独立タブを持たず、この画面で完結します。Nameは固定ラベルの
     * みにし、現在値（有効／無効）はLoreのデータ行へ分離します。
     */
    private fun renderPlaceDestinations(player: Player, route: MenuRoute): InventoryMenuView {
        val options = listOf(
            Triple(PlaceItemDestination.EQUIPMENT, Material.ARMOR_STAND, PlaceItemDestination.EQUIPMENT.labelKey),
            Triple(PlaceItemDestination.PLAYER_HOTBAR, Material.BLAZE_POWDER, PlaceItemDestination.PLAYER_HOTBAR.labelKey),
            Triple(PlaceItemDestination.PLAYER_INVENTORY, Material.CHEST, PlaceItemDestination.PLAYER_INVENTORY.labelKey),
            Triple(PlaceItemDestination.ITEM_FRAME, Material.ITEM_FRAME, PlaceItemDestination.ITEM_FRAME.labelKey),
            Triple(PlaceItemDestination.BLOCK_CONTAINER, Material.BARREL, PlaceItemDestination.BLOCK_CONTAINER.labelKey),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                KcI18n.text(player, option.third),
                "select",
                mapOf("placeDestination" to option.first.paramValue),
            )
        }.toMutableList()
        node(route)?.let { current ->
            val overwritten = current.boolean("overwrite")
            // Nameは新規固定ラベルのみにし、現在値（有効／無効）はLoreのデータ行へ分離します。
            val overwritePlaceLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OVERWRITE_PLACE)
            elements += KcGui.menuEntry(
                player = player,
                slot = PLACE_OVERWRITE_TOGGLE_SLOT,
                material = Material.LIME_DYE,
                name = overwritePlaceLabel,
                style = GuiNameStyle.PRIMARY,
                description = KcI18n.list(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OVERWRITE),
                data = listOf(GuiMenuEntryData(
                    overwritePlaceLabel,
                    displayBoolean(overwritten).render(player),
                )),
                actions = listOf(GuiMenuActionIntent.AnyClick(
                    actionId = "toggleOverwrite",
                    label = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OVERWRITE),
                )),
            )
        }
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PLACE_DESTINATION)),
            elements,
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

    /**
     * 選択画面のページ送り行です。候補上限（14件）に収めるため、
     * 8件ずつ区切って前後ページ行を付けます。呼び出し側は行数＋2行で
     * レイアウトを組みます。
     */
    private fun appendEntityPager(
        player: Player,
        layout: ChoiceMenuLayout,
        elements: MutableList<MenuElement>,
        baseIndex: Int,
        page: Int,
        pageCount: Int,
        pageKey: String,
    ) {
        if (pageCount <= 1) return
        elements += choiceElement(
            player,
            layout.itemSlots[baseIndex],
            Material.ARROW,
            "◀",
            "page",
            mapOf("page" to (page - 1).toString(), "pageKey" to pageKey),
            enabled = page > 0,
        )
        elements += choiceElement(
            player,
            layout.itemSlots[baseIndex + 1],
            Material.ARROW,
            "▶",
            "page",
            mapOf("page" to (page + 1).toString(), "pageKey" to pageKey),
            enabled = page < pageCount - 1,
        )
    }

    /**
     * ページ送り操作です。同一画面内でページだけを切り替えます。
     *
     * 対象のページキーは操作側のpayloadで指定し、画面ごとの分岐を増やしません。
     */
    private fun entityPageAction() = MenuActionHandler { context ->
        val page = context.payload["page"]?.toIntOrNull()
            ?: return@MenuActionHandler MenuActionResult.Ignored
        val pageKey = context.payload["pageKey"]?.takeIf(String::isNotBlank)
            ?: return@MenuActionHandler MenuActionResult.Ignored
        MenuActionResult.Success(
            MenuUpdate.Navigate(
                context.route.copy(payload = context.route.payload + (pageKey to page.toString())),
            ),
        )
    }

    /**
     * 統合選択の一覧です。
     *
     * タグ・attributeグループ・各データ項目を同じ階層に並べます。
     * 対象から確定できるEntityTypeで候補を絞り、種類不明時は汎用項目だけを
     * 提示します。選択肢の表示名はスキーマ定義の型付きキーを使います。
     * 候補が多い場合は8件ずつページングします。
     */
    private fun renderEntityDataRoot(player: Player, route: MenuRoute): InventoryMenuView {
        val currentNode = node(route)
        val knownType = currentNode?.let { EntityDataSchema.knownEntityType(it.targetSpec) }
        data class RootOption(val selection: String, val material: Material, val label: String)
        val options = buildList {
            add(RootOption(
                "tag",
                Material.NAME_TAG,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_DATA_TAG),
            ))
            if (knownType == null || knownType.isAlive) {
                add(RootOption(
                    "attribute",
                    Material.ENCHANTED_BOOK,
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_DATA_ATTRIBUTE),
                ))
            }
            EntityDataSchema.propertiesFor(knownType).forEach { definition ->
                add(RootOption(
                    "property:${definition.id}",
                    when (definition.valueKind) {
                        EntityPropertyValueKind.BOOLEAN -> Material.LIME_DYE
                        EntityPropertyValueKind.ENUM -> Material.BOOK
                        EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> Material.PAPER
                    },
                    KcI18n.text(player, definition.optionKey),
                ))
            }
        }
        // 現在選択中の項目が候補外の場合は未選択表示とし、表示から意味を推測しません。
        val pageSize = 8
        val pageCount = ((options.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = route.payload["rootPage"]?.toIntOrNull()?.coerceIn(0, pageCount - 1) ?: 0
        val rows = options.drop(page * pageSize).take(pageSize)
        // 行＋ページャ2行で上限（14件）に収めます。
        val layout = ChoiceMenuLayoutPolicy.layout((rows.size + if (pageCount > 1) 2 else 0).coerceAtLeast(1))
        val elements = rows.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.material,
                option.label,
                "select",
                mapOf("selection" to option.selection),
            )
        }.toMutableList()
        appendEntityPager(player, layout, elements, rows.size, page, pageCount, "rootPage")
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_DATA)),
            elements,
        )
    }

    /**
     * attribute子画面の一覧です。選択→変更後の値入力フローであり、
     * ID自体の自由入力欄は持ちません。候補が多いため8件ずつページングします。
     */
    private fun renderEntityAttributeSelect(player: Player, route: MenuRoute): InventoryMenuView {
        val options = EntityAttributeSupport.allAttributeIds()
        val pageSize = 8
        val pageCount = ((options.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = route.payload["attributePage"]?.toIntOrNull()?.coerceIn(0, pageCount - 1) ?: 0
        val rows = options.drop(page * pageSize).take(pageSize)
        // 行＋ページャ2行で上限（14件）に収めます。
        val layout = ChoiceMenuLayoutPolicy.layout((rows.size + if (pageCount > 1) 2 else 0).coerceAtLeast(1))
        val elements = rows.mapIndexed { index, id ->
            choiceElement(
                player,
                layout.itemSlots[index],
                Material.ENCHANTED_BOOK,
                id,
                "select",
                mapOf("attribute" to id),
            )
        }.toMutableList()
        appendEntityPager(player, layout, elements, rows.size, page, pageCount, "attributePage")
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_DATA_ATTRIBUTE)),
            elements,
        )
    }

    /**
     * タグ一覧画面です。入力で行を追加し、タグごとのカードへ列挙します。
     *
     * カードごとにモード切替行と削除行を並べ、2子ボタンの操作を
     * インベントリGUIでも等価に到達できるようにします。
     */
    /**
     * タグ一覧画面です。
     *
     * issue #33-3: 共通UI規則に従い、Nameへ対象識別（タグ名）または固定の
     * 操作名だけを入れ、状態（追加／削除）・対象名の補足はLoreのデータ行へ
     * 分離します。Nameへの状態混入は行いません。
     */
    private fun renderEntityTagList(player: Player, route: MenuRoute): InventoryMenuView {
        val entries = node(route)?.let { EntityTagEntries.parse(it.string(EntityTagEntries.PARAM_ENTRIES)) }.orEmpty()
        data class TagRow(
            val material: Material,
            val label: String,
            val action: String,
            val payload: Map<String, String>,
            val dataLabel: String? = null,
            val dataValue: String? = null,
        )
        val allRows = buildList {
            add(TagRow(
                Material.WRITABLE_BOOK,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TAG_ADD),
                "select",
                mapOf("add" to "true"),
            ))
            entries.forEachIndexed { index, entry ->
                val modeLabel = KcI18n.text(
                    player,
                    if (entry.mode == EntityTagEntries.MODE_ADD) KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ADD
                    else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REMOVE,
                )
                add(TagRow(
                    Material.NAME_TAG,
                    entry.tag,
                    "select",
                    mapOf("mode" to index.toString()),
                    dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG_OPERATION),
                    dataValue = modeLabel,
                ))
                add(TagRow(
                    Material.BARRIER,
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TAG_DELETE),
                    "delete",
                    mapOf("index" to index.toString()),
                    dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG),
                    dataValue = entry.tag,
                ))
            }
        }
        // 追加行＋カード2行の並びを8件ずつページングし、上限（14件）に収めます。
        val pageSize = 8
        val pageCount = ((allRows.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = route.payload["tagPage"]?.toIntOrNull()?.coerceIn(0, pageCount - 1) ?: 0
        val rows = allRows.drop(page * pageSize).take(pageSize)
        val layout = ChoiceMenuLayoutPolicy.layout((rows.size + if (pageCount > 1) 2 else 0).coerceAtLeast(1))
        val elements = rows.mapIndexed { index, row ->
            choiceElement(
                player,
                layout.itemSlots[index],
                row.material,
                row.label,
                row.action,
                row.payload,
                dataLabel = row.dataLabel,
                dataValue = row.dataValue,
            )
        }.toMutableList()
        appendEntityPager(player, layout, elements, rows.size, page, pageCount, "tagPage")
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG_ENTRIES)),
            elements,
        )
    }

    /**
     * タグ一覧への追加入力です。入力値をaddモードで追記します。
     *
     * 重複タグは検証エラーとし、保存済み一覧を壊しません。
     */
    private fun showTagEntryForm(player: Player, route: MenuRoute, currentNode: CommandNode) {
        val spec = CommandDialogSpecs.field(currentNode, EntityTagEntries.PARAM_ENTRIES) ?: return
        showTextForm(
            player = player,
            route = route,
            formId = "field-tag-entry",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf(
                FormTextField(
                    EntityTagEntries.PARAM_ENTRIES,
                    KcI18n.text(player, spec.labelKey),
                    "",
                    spec = spec,
                ),
            ),
            save = { values ->
                val tag = values.getValue(EntityTagEntries.PARAM_ENTRIES).trim()
                val entries = EntityTagEntries.parse(currentNode.string(EntityTagEntries.PARAM_ENTRIES))
                    ?: return@showTextForm false
                val updated = entries + EntityTagEntries.Entry(EntityTagEntries.MODE_ADD, tag)
                if (!EntityTagEntries.isValid(updated)) return@showTextForm false
                updateNode(player, route) {
                    CommandSettingsModel.setParameter(it, EntityTagEntries.PARAM_ENTRIES, EntityTagEntries.encode(updated))
                }
            },
        )
    }

    /**
     * 有限候補の設定値一覧です。
     *
     * 内部IDと表示名の対応はスキーマ側で管理し、内部数値を直接入力させません。
     * 真偽値・文字列・整数はこの画面を使わず、直接切替・入力画面で編集します。
     */
    private fun renderEntityPropertyValues(player: Player, route: MenuRoute): InventoryMenuView {
        val propertyId = node(route)?.let { EntityDataPath.propertyIdOf(it.string(EntityDataPath.PARAM_PATH)) }
        val definition = propertyId?.let { EntityDataSchema.definition(it) }
        val options: List<EntityPropertyChoice> =
            if (definition?.valueKind == EntityPropertyValueKind.ENUM) definition.enumChoices
            else emptyList()
        val layout = ChoiceMenuLayoutPolicy.layout(options.size.coerceAtLeast(1))
        val elements = options.mapIndexed { index, choice ->
            choiceElement(
                player,
                layout.itemSlots[index],
                Material.BOOK,
                KcI18n.text(player, choice.optionKey),
                "select",
                mapOf("choice" to choice.id),
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PROPERTY_VALUE)),
            elements,
        )
    }

    /**
     * issue #10: 召喚時個別設定の一覧です。
     *
     * 召喚種類が確定できる場合は共通項目に加えて型固有項目を示し、
     * 確定できない場合は汎用項目だけを示します。現在値は各行に添え、
     * 値の編集は項目の型で振り分けます。
     */
    private fun renderSummonDataRoot(player: Player, route: MenuRoute): InventoryMenuView {
        val currentNode = node(route)
        val summonType = currentNode?.let { EntityDataSchema.knownSummonType(it.string("summonEntity")) }
        val entries = currentNode?.let { EntityDataSchema.summonDataEntries(it.params) }.orEmpty()
        data class SummonRow(val selection: String, val material: Material, val label: String, val value: String?)
        val options = EntityDataSchema.propertiesFor(summonType).map { definition ->
            SummonRow(
                definition.id,
                when (definition.valueKind) {
                    EntityPropertyValueKind.BOOLEAN -> Material.LIME_DYE
                    EntityPropertyValueKind.ENUM -> Material.BOOK
                    EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> Material.PAPER
                },
                    KcI18n.text(player, definition.optionKey),
                    entries[definition.id]?.let { displaySummonPropertyValue(player, definition, it) },
            )
        }
        val pageSize = 8
        val pageCount = ((options.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = route.payload["summonPage"]?.toIntOrNull()?.coerceIn(0, pageCount - 1) ?: 0
        val rows = options.drop(page * pageSize).take(pageSize)
        val layout = ChoiceMenuLayoutPolicy.layout((rows.size + if (pageCount > 1) 2 else 0).coerceAtLeast(1))
        val elements = rows.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.material,
                option.label,
                "select",
                mapOf("property" to option.selection),
                dataLabel = option.label,
                dataValue = option.value,
            )
        }.toMutableList()
        appendEntityPager(player, layout, elements, rows.size, page, pageCount, "summonPage")
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_DATA)),
            elements,
        )
    }

    /** 召喚時個別設定の現在値を表示します。未設定は省略し、値欄の空白と区別します。 */
    private fun displaySummonPropertyValue(
        player: Player,
        definition: me.awabi2048.kantancommander.model.EntityPropertyDefinition,
        raw: String,
    ): String? = when (definition.valueKind) {
        EntityPropertyValueKind.BOOLEAN -> raw.toBooleanStrictOrNull()?.toString()
        EntityPropertyValueKind.ENUM -> definition.enumChoices.firstOrNull { it.id == raw }
            ?.let { KcI18n.text(player, it.optionKey) }
        EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> raw.takeIf(String::isNotBlank)
    }

    /**
     * 召喚時個別設定の有限候補の一覧です。
     *
     * 内部IDと表示名の対応はスキーマ側で管理し、内部数値を直接入力させません。
     * 対象項目は経路で受け取り、保存形式（単一選択）を汚染しません。
     */
    private fun renderSummonPropertyValues(player: Player, route: MenuRoute): InventoryMenuView {
        val propertyId = route.payload["summonProperty"]
        val definition = propertyId?.let { EntityDataSchema.definition(it) }
        val options: List<EntityPropertyChoice> =
            if (definition?.valueKind == EntityPropertyValueKind.ENUM) definition.enumChoices
            else emptyList()
        val layout = ChoiceMenuLayoutPolicy.layout(options.size.coerceAtLeast(1))
        val elements = options.mapIndexed { index, choice ->
            choiceElement(
                player,
                layout.itemSlots[index],
                Material.BOOK,
                KcI18n.text(player, choice.optionKey),
                "select",
                mapOf("choice" to choice.id),
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PROPERTY_VALUE)),
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

    /**
     * issue #33-1: 建造物の回転選択子画面です（簡易GUI）。
     *
     * 選択肢・保存値（小文字文字列）はジェスチャーGUIと同一であり、
     * 検証層が拒否する未知値は受け付けません。
     */
    private fun renderStructureRotations(player: Player): InventoryMenuView {
        val options = StructureRotation.entries.map { rotation ->
            Triple(rotation.value, Material.COMPASS, "${rotation.degrees}°")
        }
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                option.third,
                "select",
                mapOf("rotation" to option.first),
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_OPERATION)),
            elements,
        )
    }

    /**
     * issue #33-1: 建造物の反転選択子画面です（簡易GUI）。
     *
     * 選択肢・保存値はジェスチャーGUIと同一です。
     */
    private fun renderStructureMirrors(player: Player): InventoryMenuView {
        val options = StructureMirror.entries.map { mirror ->
            val labelKey = when (mirror) {
                StructureMirror.NONE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STRUCTURE_MIRROR_NONE
                StructureMirror.LEFT_RIGHT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STRUCTURE_MIRROR_LEFT_RIGHT
                StructureMirror.FRONT_BACK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_STRUCTURE_MIRROR_FRONT_BACK
            }
            Triple(mirror.value, Material.GLASS, KcI18n.text(player, labelKey))
        }
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                option.third,
                "select",
                mapOf("mirror" to option.first),
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_OPERATION)),
            elements,
        )
    }

    /**
     * issue #33-1: 群操作の選択子画面です（簡易GUI）。
     *
     * 8操作の選択肢・保存値はジェスチャーGUIと同一です。操作変更で
     * 表示タブが切り替わるため、保存後は設定一覧へ戻します。
     */
    private fun renderEntityGroupOperations(player: Player): InventoryMenuView {
        val options = EntityGroupOperation.entries.map { operation ->
            val labelKey = when (operation) {
                EntityGroupOperation.CREATE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_CREATE
                EntityGroupOperation.ADD -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_ADD
                EntityGroupOperation.REMOVE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_REMOVE
                EntityGroupOperation.DELETE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_DELETE
                EntityGroupOperation.SET_PIVOT -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_SET_PIVOT
                EntityGroupOperation.MOVE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_MOVE
                EntityGroupOperation.ROTATE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_ROTATE
                EntityGroupOperation.CLEANUP -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_GROUP_CLEANUP
            }
            Triple(operation.value, Material.ARMOR_STAND, KcI18n.text(player, labelKey))
        }
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
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATION)),
            elements,
        )
    }

    /**
     * issue #8 の呼び出し設定子画面です（簡易GUI）。
     *
     * 引数の値（複合型は変数名、スカラー型はリテラル）と戻り値の受取先を編集します。
     * 保存規則はジェスチャーGUIと同一であり、リテラルと変数指定は排他です。
     * 引数は8件ずつページングし、候補上限（14件）に収めます。
     */
    private fun renderDiskCallSettings(player: Player, route: MenuRoute): InventoryMenuView {
        val current = node(route)
        if (current == null || current.type != CommandType.DISK_CALL) {
            return InventoryMenuView(
                45,
                KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DISK_CALL_SETTINGS)),
                listOf(backElement(player)),
            )
        }
        val pageSize = 8
        val declared = current.declaredArguments()
        val pageCount = ((declared.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = route.payload["argPage"]?.toIntOrNull()?.coerceIn(0, pageCount - 1) ?: 0
        val rows = declared.drop(page * pageSize).take(pageSize)
        // 引数行＋戻り値2行＋ページャ2行で上限（14件）に収めます。
        val layout = ChoiceMenuLayoutPolicy.layout((rows.size + 2 + if (pageCount > 1) 2 else 0).coerceAtLeast(1))
        val elements = mutableListOf<MenuElement>()
        rows.forEachIndexed { index, argument ->
            val variable = current.string("argvar_${argument.name}").takeIf(String::isNotBlank)
            val literal = current.string("arg_${argument.name}").takeIf(String::isNotBlank)
            val value = variable?.let { "%{$it}%" } ?: literal.orEmpty()
                .ifBlank { KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET) }
            elements += choiceElement(
                player,
                layout.itemSlots[index],
                Material.WRITABLE_BOOK,
                argument.name,
                "arg",
                mapOf("name" to argument.name),
                dataLabel = KcI18n.text(player, displayValueTypeLabel(argument.type)),
                dataValue = value,
            )
        }
        val returnName = current.string("returnName")
        val returnScope = current.string("returnScope").ifBlank { VariableScope.EXECUTION.name }
        elements += choiceElement(
            player,
            layout.itemSlots[rows.size],
            Material.ENDER_EYE,
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SCOPE),
            "returnScope",
            dataValue = if (returnScope == VariableScope.WORLD.name) {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_WORLD)
            } else {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_EXECUTION)
            },
        )
        elements += choiceElement(
            player,
            layout.itemSlots[rows.size + 1],
            Material.NAME_TAG,
            KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_RETURN),
            "returnName",
            dataValue = returnName.ifBlank {
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET)
            },
        )
        if (pageCount > 1) {
            elements += choiceElement(
                player,
                layout.itemSlots[rows.size + 2],
                Material.ARROW,
                "◀",
                "argPage",
                mapOf("page" to (page - 1).toString()),
                enabled = page > 0,
            )
            elements += choiceElement(
                player,
                layout.itemSlots[rows.size + 3],
                Material.ARROW,
                "▶",
                "argPage",
                mapOf("page" to (page + 1).toString()),
                enabled = page < pageCount - 1,
            )
        }
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DISK_CALL_SETTINGS)),
            elements,
        )
    }

    /** 呼び出し引数の値を編集します。型に応じて値入力または変数名入力へ進みます。 */
    private fun openDiskCallArgumentForm(player: Player, route: MenuRoute, argument: DiskArgument) {
        val current = node(route) ?: return
        val complex = argument.type !in setOf(ValueType.NUMBER, ValueType.STRING, ValueType.BOOLEAN)
        if (complex) {
            val spec = CommandDialogSpecs.variableName
            showTextForm(
                player = player,
                route = route,
                formId = "disk-call-arg-variable",
                title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
                fields = listOf(
                    FormTextField(
                        "value",
                        KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_ARG_VARIABLE),
                        current.string("argvar_${argument.name}"),
                        spec = spec,
                    ),
                ),
                save = { values ->
                    val raw = values.getValue("value").trim()
                    if (raw.isEmpty() || !CommandValueRules.isVariableName(raw)) return@showTextForm false
                    updateNode(player, route) {
                        it.params["argvar_${argument.name}"] = raw
                        it.params.remove("arg_${argument.name}")
                        it.clearConfigured("arg_${argument.name}")
                        it.markConfigured("argvar_${argument.name}")
                    }
                },
            )
            return
        }
        val spec = CommandDialogSpecs.Spec(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE,
            256,
            validate = { raw ->
                if (CommandSettingsModel.isCallArgumentLiteralValid(argument.type, raw)) null
                else KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_ERROR_INPUT_FORMAT
            },
        )
        showTextForm(
            player = player,
            route = route,
            formId = "disk-call-arg-value",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf(
                FormTextField(
                    "value",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_ARG_VALUE),
                    current.string("arg_${argument.name}"),
                    spec = spec,
                ),
            ),
            save = { values ->
                val raw = values.getValue("value").trim()
                if (raw.isEmpty() || !CommandSettingsModel.isCallArgumentLiteralValid(argument.type, raw)) {
                    return@showTextForm false
                }
                updateNode(player, route) {
                    it.params["arg_${argument.name}"] = raw
                    it.params.remove("argvar_${argument.name}")
                    it.clearConfigured("argvar_${argument.name}")
                    it.markConfigured("arg_${argument.name}")
                }
            },
        )
    }

    /** 戻り値の受取先変数名を編集します。空欄で受取を解除します。 */
    private fun openDiskCallReturnNameForm(player: Player, route: MenuRoute) {
        val current = node(route) ?: return
        // 空欄での解除を受け付けるため、必須指定を外します。空欄以外は変数名を検証します。
        val spec = CommandDialogSpecs.variableName.copy(required = false)
        showTextForm(
            player = player,
            route = route,
            formId = "disk-call-return-name",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf(
                FormTextField(
                    "value",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_DIALOG_DISK_CALL_RETURN_NAME),
                    current.string("returnName"),
                    spec = spec,
                ),
            ),
            save = { values ->
                val raw = values.getValue("value").trim()
                if (raw.isNotEmpty() && !CommandValueRules.isVariableName(raw)) return@showTextForm false
                updateNode(player, route) {
                    if (raw.isEmpty()) {
                        it.params.remove("returnName")
                        it.params.remove("returnScope")
                        it.clearConfigured("returnName")
                        it.clearConfigured("returnScope")
                    } else {
                        it.params["returnName"] = raw
                        if (it.string("returnScope").isBlank()) {
                            it.params["returnScope"] = VariableScope.EXECUTION.name
                        }
                        it.markConfigured("returnName")
                        it.markConfigured("returnScope")
                    }
                }
            },
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

    /** 「付近」の距離だけを数値入力で調整します。空欄は指定解除です。 */
    private fun openTargetDistanceForm(player: Player, route: MenuRoute) {
        val embedRole = embeddedPositionRole(route)
        val currentSpec = effectiveTargetSpec(route) ?: return
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
                if (embedRole != null) {
                    updateNode(player, route) { node ->
                        val base = CommandSettingsModel.positionSpec(node, embedRole)
                            ?.takeIf { base -> base.kind == PositionKind.TARGET }
                            ?: PositionSpec(PositionKind.TARGET)
                        val target = base.target ?: currentSpec
                        CommandSettingsModel.setPositionSpec(
                            node,
                            embedRole,
                            base.copy(
                                kind = PositionKind.TARGET,
                                target = target.copy(
                                    minimumDistance = values.getValue("minimum").trim().takeIf(String::isNotEmpty)
                                        ?.let(CommandDialogSpecs::finiteDouble),
                                    maximumDistance = values.getValue("maximum").trim().takeIf(String::isNotEmpty)
                                        ?.let(CommandDialogSpecs::finiteDouble),
                                ),
                            ),
                        )
                    }
                } else {
                    updateTargetSpec(player, route) { _ ->
                        currentSpec.copy(
                            minimumDistance = values.getValue("minimum").trim().takeIf(String::isNotEmpty)
                                ?.let(CommandDialogSpecs::finiteDouble),
                            maximumDistance = values.getValue("maximum").trim().takeIf(String::isNotEmpty)
                                ?.let(CommandDialogSpecs::finiteDouble),
                        )
                    }
                }
            },
        )
    }

    /** issue #29: 位置の一時変数参照を設定します。 */
    private fun openPositionTemporaryForm(player: Player, route: MenuRoute, role: CommandSettingRole) {
        val current = node(route)?.let { CommandSettingsModel.positionSpec(it, role) }
        showTextForm(
            player = player,
            route = route,
            formId = "position-temporary",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf(
                FormTextField(
                    "tempName",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE),
                    current?.takeIf { it.kind == PositionKind.TEMPORARY }?.tempName.orEmpty(),
                    spec = CommandDialogSpecs.variableName,
                ),
            ),
            save = { values ->
                val raw = values.getValue("tempName").trim()
                if (raw.isEmpty()) {
                    false
                } else {
                    updateNode(player, route) { node ->
                        CommandSettingsModel.setPositionSpec(
                            node,
                            role,
                            PositionSpec(PositionKind.TEMPORARY, tempName = raw),
                        )
                    }
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

    /**
     * 数値スロットの入力画面です。
     *
     * 保存形式 (0始まり) ではなく GUI 表示 (1始まり) で受け付け、保存時に
     * 変換します。装備・額縁は選択・固定のためここへは到達しません。
     * 仕様と検証は CommandDialogSpecs.placeSlot へ一元化します。
     */
    private fun showPlaceSlotForm(player: Player, route: MenuRoute, node: CommandNode) {
        val destination = PlaceItemDestination.from(node.string("placeDestination")) ?: return
        val spec = CommandDialogSpecs.placeSlot(node) ?: return
        showTextForm(
            player = player,
            route = route,
            formId = "field-slot",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf(
                FormTextField(
                    "slot",
                    KcI18n.text(player, spec.labelKey),
                    PlaceItemDestination.toDisplay(destination, node.string("slot")),
                    spec = spec,
                ),
            ),
            save = { values ->
                val saved = PlaceItemDestination.fromDisplay(destination, values.getValue("slot"))
                    ?: return@showTextForm false
                updateNode(player, route) {
                    CommandSettingsModel.setParameter(it, "slot", saved)
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
            "summonEntity" -> "minecraft:pig"
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
            "motionX", "motionY", "motionZ" -> "0.0"
            EntityAttributeSupport.PARAM_VALUE -> "0.0"
            EntityDataSchema.PARAM_VALUE -> ""
            EntityTagEntries.PARAM_ENTRIES -> return
            "entityId", "item", "block" -> ""
            // issue #33-1: 建造物名・群名・群の軸入力の初期表示です。
            // 検証仕様は入力仕様が担い、ここでは空欄または数値既定を示します。
            "structure", "group" -> ""
            "moveX", "moveY", "moveZ", "rotateX", "rotateY", "rotateZ" -> "0.0"
            // issue #10: 召喚時個別設定値は項目別の検証仕様で初期表示します。
            // ここでは空欄初期表示とし、範囲・書式は入力仕様が担います。
            // issue #33-1: 入力仕様を持つ新しい設定項目は保存値の初期表示へ
            // 落とします。仕様のない項目は上流で除外済みのため到達しません。
            else -> node.string(field, "")
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
                    // issue #10: 召喚種類の変更時は非対応の個別設定を残しません。
                    if (field == "summonEntity" && it.type == CommandType.SUMMON_ENTITY) {
                        CommandSettingsModel.setSummonEntity(it, values.getValue(field))
                    } else {
                        CommandSettingsModel.setParameter(it, field, values.getValue(field))
                    }
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

    /**
     * issue #29 の汎用位置プリセット画面です。
     *
     * 頻用する5プリセットとスポーン種別・一時変数を提示し、エンティティ由来は
     * 対象選択画面の内包経路へ進みます。選択済みの再クリックで詳細を開く方式は
     * Gesture GUIと同一です。移動先も同じ親画面を使い、保存先だけ役割で分けます。
     * Inventory GUIとGesture GUIで同じ共有定義を使います。
     */
    private fun renderGenericPosition(player: Player, route: MenuRoute): InventoryMenuView {
        val role = CommandSettingRole.fromRoute(route.payload[ROLE])
        val currentNode = node(route)
        val current = currentNode?.let { CommandSettingsModel.positionSpec(it, role) }
        val matched = if (role == CommandSettingRole.DESTINATION && currentNode != null) {
            SettingPresets.matchDestinationPreset(
                CommandSettingsModel.targetSpec(currentNode, role),
                current,
            )
        } else {
            SettingPresets.matchPositionPreset(current)
        }
        val entityTargetsEnabled = role == CommandSettingRole.DESTINATION ||
            CommandSettingAvailabilityPolicy.isEmbeddedTargetPositionEnabled(role)
        data class Row(
            val material: Material,
            val name: String,
            val actionId: String,
            val payload: Map<String, String> = emptyMap(),
            val selected: Boolean = false,
            val enabled: Boolean = true,
            val disabledDescription: List<String>? = null,
            val dataValue: String? = null,
        )
        val spawnLabel = when {
            current?.kind != PositionKind.MYWORLD_SPAWN -> null
            SettingPresets.myworldSpawnOrMember(current.myworldSpawn) == MyworldSpawnKind.GUEST ->
                KcI18n.text(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_GUEST)
            else -> KcI18n.text(player, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_MEMBER)
        }
        val rows = buildList {
            add(Row(Material.COMMAND_BLOCK, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONTROL_BLOCK_POSITION), "disk", selected = matched == SettingPresets.PositionPreset.CONTROL_BLOCK))
            // issue #29: 無効化は取得元ではなく件数で判定し、静的0..1は単一限定でも許可します。
            // 移動先の最寄り・付近は対象指定への保存のため、単一制約の対象外です。
            add(Row(Material.PLAYER_HEAD, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER), "preset", mapOf("preset" to SettingPresets.PositionPreset.NEAREST_PLAYER.name), selected = matched == SettingPresets.PositionPreset.NEAREST_PLAYER))
            add(Row(Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEARBY_PLAYERS), "preset", mapOf("preset" to SettingPresets.PositionPreset.NEARBY_PLAYERS.name), selected = matched == SettingPresets.PositionPreset.NEARBY_PLAYERS, enabled = entityTargetsEnabled, disabledDescription = multiDisabled(entityTargetsEnabled)))
            add(
                Row(
                    Material.BEACON,
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN),
                    "preset",
                    mapOf("preset" to SettingPresets.PositionPreset.MYWORLD_SPAWN.name),
                    selected = matched == SettingPresets.PositionPreset.MYWORLD_SPAWN,
                    dataValue = spawnLabel,
                ),
            )
            add(Row(Material.COMPASS, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_COORDINATES), "coordinates", selected = matched == SettingPresets.PositionPreset.COORDINATES))
            add(Row(Material.ENDER_PEARL, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TARGET_POSITION), "entity", selected = entitySelected(currentNode, role, matched), enabled = entityTargetsEnabled, disabledDescription = multiDisabled(entityTargetsEnabled)))
            add(Row(Material.NAME_TAG, KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE), "temporary", selected = current?.kind == PositionKind.TEMPORARY))
        }
        val layout = ChoiceMenuLayoutPolicy.layout(rows.size)
        val elements = rows.mapIndexed { index, row ->
            choiceElement(
                player,
                layout.itemSlots[index],
                row.material,
                row.name,
                row.actionId,
                row.payload,
                dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION),
                dataValue = row.dataValue ?: if (row.selected) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECTED)
                } else null,
                enabled = row.enabled && (currentNode?.let {
                    // DISK行だけ既存の上書き制約を適用します。他行はプリセット固有の可否に従います。
                    row.actionId != "disk" || CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(it, role, PositionKind.DISK)
                } ?: true),
                disabledDescription = row.disabledDescription,
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION)),
            elements,
        )
    }

    /** issue #29: 単一限定時の無効理由です。有効時は理由なしです。 */
    private fun multiDisabled(enabled: Boolean): List<String>? =
        if (enabled) null else listOf(CommandSettingAvailabilityPolicy.MULTI_POSITION_DISABLED_HOVER)

    /**
     * issue #29: スポーン種別2択の子画面です。
     *
     * 旧来の単一種別はメンバー用として表示します。Gesture GUIの
     * 再クリック子画面と同じ選択を提示します。
     */
    private fun renderSpawnKind(player: Player, route: MenuRoute): InventoryMenuView {
        val role = CommandSettingRole.fromRoute(route.payload[ROLE])
        val current = node(route)?.let { CommandSettingsModel.positionSpec(it, role) }
        val resolved = SettingPresets.myworldSpawnOrMember(current
            ?.takeIf { it.kind == PositionKind.MYWORLD_SPAWN }?.myworldSpawn)
        val options = listOf(
            Triple(MyworldSpawnKind.MEMBER, Material.BEACON, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_MEMBER),
            Triple(MyworldSpawnKind.GUEST, Material.BEACON, MyworldGuiSettingsKeys.GUI_SETTINGS_SPAWN_TYPE_GUEST),
        )
        val layout = ChoiceMenuLayoutPolicy.layout(options.size)
        val elements = options.mapIndexed { index, option ->
            choiceElement(
                player,
                layout.itemSlots[index],
                option.second,
                KcI18n.text(player, option.third),
                "spawn",
                mapOf("spawn" to option.first.name),
                dataLabel = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION),
                dataValue = if (resolved == option.first && current?.kind == PositionKind.MYWORLD_SPAWN) {
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_EDITOR_SELECTED)
                } else null,
            )
        }.toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION)),
            elements,
        )
    }

    /**
     * issue #29: 位置に内包する対象の付近距離を変更します。
     *
     * Gesture GUIの再クリック距離変更と同じ保存先を使い、表示だけが異なります。
     */
    private fun openPositionTargetDistanceForm(player: Player, route: MenuRoute, role: CommandSettingRole) {
        val current = node(route)?.let { CommandSettingsModel.positionSpec(it, role)?.target } ?: return
        val inputSpec = requireNotNull(CommandDialogSpecs.targetFilter("distance"))
        showTextForm(
            player = player,
            route = route,
            formId = "position-target-distance",
            title = KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_GESTURE_DIALOG_INPUT_TITLE),
            fields = listOf(
                FormTextField(
                    "minimum",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MINIMUM_DISTANCE),
                    current.minimumDistance?.toString().orEmpty(),
                    spec = inputSpec,
                ),
                FormTextField(
                    "maximum",
                    KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MAXIMUM_DISTANCE),
                    current.maximumDistance?.toString().orEmpty(),
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
                updateNode(player, route) { node ->
                    val base = CommandSettingsModel.positionSpec(node, role)
                        ?.takeIf { base -> base.kind == PositionKind.TARGET }
                        ?: PositionSpec(PositionKind.TARGET)
                    val target = base.target ?: current
                    CommandSettingsModel.setPositionSpec(
                        node,
                        role,
                        base.copy(
                            kind = PositionKind.TARGET,
                            target = target.copy(
                                minimumDistance = values.getValue("minimum").trim().takeIf(String::isNotEmpty)
                                    ?.let(CommandDialogSpecs::finiteDouble),
                                maximumDistance = values.getValue("maximum").trim().takeIf(String::isNotEmpty)
                                    ?.let(CommandDialogSpecs::finiteDouble),
                            ),
                        ),
                    )
                }
            },
        )
    }

    /**
     * issue #29: エンティティ導線の選択表示です。
     *
     * 移動先は対象指定の有無で判定し、それ以外は対象由来位置で判定します。
     */
    private fun entitySelected(node: CommandNode?, role: CommandSettingRole?, matched: SettingPresets.PositionPreset?): Boolean {
        if (node == null) return false
        if (role == CommandSettingRole.DESTINATION) {
            return matched == null &&
                CommandSettingsModel.positionSpec(node, role)?.kind != PositionKind.TEMPORARY &&
                CommandSettingsModel.targetSpec(node, role) != null
        }
        val current = CommandSettingsModel.positionSpec(node, role)
        return current?.kind == PositionKind.TARGET && matched == null
    }

    /**
     * 表示対象（particleDisplay）の設定画面です。
     *
     * #30で表示方式の選択は廃止し、対象指定だけを扱います。コマンド選択そのものが
     * 通常表示／図形表示の選択を兼ねるため、ここで方式を切り替えません。
     */
    private fun renderParticleDisplay(player: Player, route: MenuRoute): InventoryMenuView {
        val layout = ChoiceMenuLayoutPolicy.layout(1)
        val elements = listOf(
            choiceElement(
                player,
                layout.itemSlots[0],
                Material.PLAYER_HEAD,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                "target",
            ),
        ).toMutableList()
        elements += backElement(player, layout.backSlot)
        return InventoryMenuView(
            layout.size,
            KcGui.title(KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DISPLAY)),
            elements,
        )
    }

    /**
     * 図形固有の詳細設定画面です。
     *
     * #30の二段階目の画面であり、選択済み形状の変更とその形状の数値を扱います。
     * 終点・対角点は表示位置（geometryEnd）へ分離したため、ここでは扱いません。
     */
    private fun renderGeometrySettings(player: Player, route: MenuRoute): InventoryMenuView {
        val node = node(route)
        val shape = node?.let(ParticleGeometry::shape) ?: ParticleShape.POINT_SCATTER
        val layout = ChoiceMenuLayoutPolicy.layout(2)
        val elements = listOf(
            choiceElement(
                player,
                layout.itemSlots[0],
                Material.FILLED_MAP,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_GEOMETRY_SETTINGS),
                "shape",
                dataValue = KcI18n.text(player, geometryShapeKey(shape)),
            ),
            choiceElement(
                player,
                layout.itemSlots[1],
                Material.PAPER,
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_PARAMETERS),
                "numbers",
            ),
        ).toMutableList()
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

    /**
     * issue #34-1: 対象選択画面の通常対象です。
     *
     * 内包役割が ROLE へ直接入った場合も通常対象へ流さず、位置・向き仕様の
     * 内部対象を返します。保存先の取り違えを入口で防ぐための一元化です。
     */
    private fun selectedTargetSpec(route: MenuRoute): TargetSpec? {
        val node = node(route) ?: return null
        val role = CommandSettingRole.fromRoute(route.payload[ROLE])
        return if (CommandSettingsModel.isEmbeddedTargetRole(role)) {
            CommandSettingsModel.embeddedTargetSpec(node, role)
        } else {
            CommandSettingsModel.targetSpec(node, role)
        }
    }

    /**
     * issue #29: 対象選択画面の実効対象です。
     *
     * 内包経路（EMBED_ROLE）では位置に内包する対象を返し、それ以外は
     * 役割直下の対象を返します。表示・距離編集で共有します。
     */
    private fun effectiveTargetSpec(route: MenuRoute): TargetSpec? {
        val embedRole = embeddedPositionRole(route)
            ?: return selectedTargetSpec(route)
        return node(route)?.let { CommandSettingsModel.positionSpec(it, embedRole)?.target }
    }

    /** issue #29: 対象選択画面が位置内包対象を編集している場合の位置役割です。 */
    private fun embeddedPositionRole(route: MenuRoute): CommandSettingRole? =
        route.payload[EMBED_ROLE]?.let(CommandSettingRole::fromRoute)

    /** issue #29: 対象プリセットが表す種別です。簡易GUIの選択と共有定義を対応付けます。 */
    private fun presetTargetKind(preset: SettingPresets.TargetPreset): TargetKind = when (preset) {
        SettingPresets.TargetPreset.NEARBY_PLAYERS -> TargetKind.NEARBY_PLAYERS
        SettingPresets.TargetPreset.MYWORLD_PLAYERS -> TargetKind.ALL_PLAYERS
        SettingPresets.TargetPreset.NEAREST_PLAYER -> TargetKind.NEAREST_PLAYER
        SettingPresets.TargetPreset.NEARBY_ENTITIES -> TargetKind.NEARBY_ENTITIES
    }

    /**
     * issue #34-1: 対象選択画面の保存入口です。
     *
     * 内包役割へは [CommandSettingsModel.setEmbeddedTargetSpec] で保存し、
     * 通常対象への取り違えを防ぎます。編集したフィールド以外は維持されます。
     */
    private fun updateTargetSpec(player: Player, route: MenuRoute, change: (TargetSpec) -> TargetSpec): Boolean =
        updateNode(player, route) { node ->
            val role = CommandSettingRole.fromRoute(route.payload[ROLE])
            if (CommandSettingsModel.isEmbeddedTargetRole(role)) {
                val current = CommandSettingsModel.embeddedTargetSpec(node, role)
                    ?: TargetSpec(TargetKind.NEAREST_ENTITY)
                CommandSettingsModel.setEmbeddedTargetSpec(node, role, change(current))
            } else {
                val current = CommandSettingsModel.targetSpec(node, role) ?: TargetSpec(TargetKind.NEAREST_ENTITY)
                val updated = change(current)
                CommandSettingsModel.setTargetSpec(node, role, updated)
            }
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
        /** issue #13: 簡易選択画面の分類タブの経路キーです。 */
        private const val PICKER_CATEGORY = "category"
        private const val SETTINGS_ID = "command_settings"
        private const val ENTITY_EQUIPMENT_SLOT_ID = "entity_equipment_slot"
        private const val ENTITY_DATA_ROOT_ID = "entity_data_root"
        private const val ENTITY_ATTRIBUTE_SELECT_ID = "entity_attribute_select"
        private const val ENTITY_TAG_LIST_ID = "entity_tag_list"
        private const val ENTITY_PROPERTY_VALUE_ID = "entity_property_value"
        /** issue #10: 召喚時個別設定の一覧画面です。適用可能な項目だけを列挙します。 */
        private const val SUMMON_DATA_ROOT_ID = "summon_data_root"
        /** issue #10: 召喚時個別設定の有限候補の一覧画面です。対象項目は経路で受け取ります。 */
        private const val SUMMON_PROPERTY_VALUE_ID = "summon_property_value"
        private const val PLACE_DESTINATION_ID = "place_destination"
        // 配置先画面の下段中央（45枠の下段中央）へ置く上書きトグル枠です。
        // 戻る枠（36）と配置先5択（19〜23）と重なりません。
        private const val PLACE_OVERWRITE_TOGGLE_SLOT = 40
        private const val DISPLAY_MODE_ID = "display_mode"
        private const val BLOCK_OPERATION_ID = "block_operation"
        // issue #33-1: 建造物の回転・反転、群操作の選択子画面です。
        // 表示される設定項目に操作・保存経路がない接続漏れを解消します。
        private const val STRUCTURE_ROTATION_ID = "structure_rotation"
        private const val STRUCTURE_MIRROR_ID = "structure_mirror"
        private const val ENTITY_GROUP_OPERATION_ID = "entity_group_operation"
        private const val DELETE_ID = "delete_command"
        private const val TARGET_ID = "target_settings"
        private const val SOUND_TARGET_POSITION_ID = "sound_target_position"
        private const val GENERIC_POSITION_ID = "generic_position"
        /** issue #29: スポーン種別2択の子画面です。選択済みの再クリックで開きます。 */
        private const val SPAWN_KIND_ID = "spawn_kind"
        private const val PARTICLE_DISPLAY_ID = "particle_display"
        private const val GEOMETRY_SETTINGS_ID = "geometry_settings"
        private const val PARTICLE_SHAPE_ID = "particle_shape"
        /** issue #8: 呼び出し設定（引数・戻り値の受取先）の子画面です。 */
        private const val DISK_CALL_SETTINGS_ID = "disk_call_settings"
        private const val FIREWORK_CONTENTS_ID = "firework_contents"
        private const val SCRIPT_ID = "scriptId"
        private const val NODE_ID = "nodeId"
        private const val SOURCE_ID = "sourceId"
        private const val EDGE = "edge"
        private const val MERGE_CONDITION_ID = "mergeConditionId"
        private const val CONTINUATION_ID = "continuationId"
        private const val ROLE = "role"
        /** issue #29: 対象選択画面が位置内包対象を編集している場合の位置役割です。 */
        private const val EMBED_ROLE = "embedRole"

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

        /**
         * issue #29: 位置に内包する対象の選択経路です。
         *
         * ROLEは対象選択画面の保存先ではなく、内包先の位置役割を表します。
         * EMBED_ROLEがあれば対象選択の保存・距離編集は内包対象へ向かいます。
         */
        private fun embeddedTargetRoute(route: MenuRoute, role: String) =
            route.copy(id = TARGET_ID, payload = route.payload + (ROLE to role) + (EMBED_ROLE to role))

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
    /**
     * エンティティ種別IDです。独自翻訳は持たず、バニラ翻訳キーへの
     * translatableを各表示経路で解決します。文字列が必須の文脈では
     * ID原文へ落とします。
     */
    data class EntityTypeName(val rawId: String) : DisplayValue {
        fun entityComponent(color: net.kyori.adventure.text.format.TextColor? = null): net.kyori.adventure.text.Component =
            me.awabi2048.kantancommander.model.EntityTypeDisplay.componentFor(rawId, color)
    }
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
    ) : DisplayValue {
        override fun rows(player: Player): List<TimingRow> {
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

    /**
     * パーティクルの表示対象を、一つの設定欄で現在値として表示します。
     *
     * #30で表示方式の選択は廃止しました。対象指定だけを扱います。
     */
    data class ParticleDisplay(
        val targetKind: TargetKind?,
    ) : DisplayValue {
        override fun rows(player: Player): List<TimingRow> = listOf(
            TimingRow(
                KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET),
                targetKind?.let { displayTarget(it).render(player) }
                    ?: KcI18n.text(player, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET),
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
        is EntityTypeName -> rawId
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
        CommandType.PLACE_ITEM -> listOf(
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PLACE_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PLACE_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field(
                "placeDestination",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PLACE_DESTINATION,
                Material.HOPPER,
            ) { displayPlaceDestination(it.string("placeDestination", PlaceItemDestination.EQUIPMENT.paramValue)) },
            field("slot", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PLACE_SLOT, Material.ARMOR_STAND) {
                displayPlaceSlot(it)
            },
            field(
                "item",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PLACE_ITEM,
                Material.CHEST,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PLACE_ITEM,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PLACE_ITEM,
            ) { displayTypedValue(it, "item") },
            field("count", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_COUNT, Material.DIAMOND),
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
                "position",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PLACE_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PLACE_POSITION,
            ) {
                it.blockPositionSpec?.kind?.let(::displayPosition) ?: displayUnset()
            },
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
            // ENTITY_ACTIONはride/dismount/motionに限定します。旧equip欄は
            // PLACE_ITEM、旧tag欄はENTITY_OPERATIONへ移行済みのため出しません。
            field(
                "motionX",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_X,
                Material.ARROW,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_MOTION_X,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_MOTION_X,
            ),
            field(
                "motionY",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_Y,
                Material.ARROW,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_MOTION_Y,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_MOTION_Y,
            ),
            field(
                "motionZ",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_Z,
                Material.ARROW,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_MOTION_Z,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_MOTION_Z,
            ),
        )
        CommandType.ENTITY_OPERATION -> listOf(
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY_ACTION_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY_ACTION_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field(
                EntityDataPath.PARAM_PATH,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_DATA,
                Material.BOOK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PROPERTY,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PROPERTY,
            ) { displayEntityDataPath(it) },
            field(
                EntityTagEntries.PARAM_ENTRIES,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG_ENTRIES,
                Material.NAME_TAG,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TAGS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TAGS,
            ) { displayTagEntries(it) },
            field(
                EntityAttributeSupport.PARAM_VALUE,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ATTRIBUTE_VALUE,
                Material.GOLD_NUGGET,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ATTRIBUTE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ATTRIBUTE,
            ),
            field(
                EntityDataSchema.PARAM_VALUE,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PROPERTY_VALUE,
                Material.PAPER,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PROPERTY_VALUE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PROPERTY_VALUE,
            ) { displayEntityPropertyValue(it) },
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
            field("summonEntity", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY, Material.ZOMBIE_SPAWN_EGG) {
                displayEntityTypeName(it.string("summonEntity"))
            },
            field(
                "summonData",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_DATA,
                Material.BOOK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PROPERTY,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PROPERTY,
            ) { displaySummonData(it) },
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
                )
            },
            field("soundScope", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_SCOPE, Material.GLOBE_BANNER_PATTERN,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_SCOPE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_SCOPE,
            ) { displaySoundScope(it.string("soundScope", "POSITION")) },
        )
        // #30: 両コマンドともタブ順を「種類 → 表示設定 → 表示位置 → 表示対象」に統一します。
        // 種類（particle）と表示対象（particleDisplay）は共有し、表示設定の中身だけが型固有です。
        CommandType.PARTICLE -> listOf(
            field(
                ParticleSettings.PARAM_PARTICLE,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE,
                Material.FIREWORK_STAR,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PARTICLE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PARTICLE,
            ) { displayParticle(it) },
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
                "position",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) { it.particlePositionSpec?.kind?.let(::displayPosition) ?: displayUnset() },
            field(
                "particleDisplay",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DISPLAY,
                Material.ENDER_EYE,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) {
                DisplayValue.ParticleDisplay(
                    targetKind = it.targetSpec?.kind,
                )
            },
        )
        CommandType.PARTICLE_GEOMETRY -> listOf(
            field(
                ParticleSettings.PARAM_PARTICLE,
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE,
                Material.FIREWORK_STAR,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_PARTICLE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_PARTICLE,
            ) { displayParticle(it) },
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
            field(
                "position",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) { it.particlePositionSpec?.kind?.let(::displayPosition) ?: displayUnset() },
            field(
                "geometryEnd",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) { it.particleEndPositionSpec?.kind?.let(::displayPosition) ?: displayUnset() },
            field(
                "particleDisplay",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PARTICLE_DISPLAY,
                Material.ENDER_EYE,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) {
                DisplayValue.ParticleDisplay(
                    targetKind = it.targetSpec?.kind,
                )
            },
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
        // issue #11: 保存は名前＋範囲、設置は名前＋回転・反転＋位置です。
        // 範囲・位置は BLOCK_OPERATION と同じ位置設定木を流用します。
        CommandType.STRUCTURE_SAVE -> listOf(
            field(
                "structure",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_STRUCTURE,
                Material.STRUCTURE_BLOCK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK,
            ) { displayTypedValue(it, "structure") },
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
        CommandType.STRUCTURE_PLACE -> listOf(
            field(
                "structure",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_STRUCTURE,
                Material.STRUCTURE_VOID,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK,
            ) { displayTypedValue(it, "structure") },
            field(
                "rotation",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_OPERATION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayTypedValue(it, "rotation") },
            field(
                "mirror",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_OPERATION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayTypedValue(it, "mirror") },
            field(
                "structurePlace",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_BLOCK_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_BLOCK_POSITION,
            ) {
                it.structurePlacePositionSpec?.kind?.let(::displayPosition) ?: displayUnset()
            },
        )
        // issue #24: 群操作は操作・群名・対象・pivot・軸入力です。
        // 対象・pivot は既存の位置・対象設定木を流用します。
        CommandType.ENTITY_GROUP -> listOf(
            field(
                "operation",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATION,
                Material.ARMOR_STAND,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayTypedValue(it, "operation") },
            field(
                "group",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_GROUP,
                Material.NAME_TAG,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_NAME,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_NAME,
            ) { displayTypedValue(it, "group") },
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISPLAY_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISPLAY_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field(
                "pivot",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_GROUP_PIVOT,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) {
                it.groupPivotPositionSpec?.kind?.let(::displayPosition) ?: displayUnset()
            },
            field(
                "moveX",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_X,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayTypedValue(it, "moveX") },
            field(
                "moveY",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_Y,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayTypedValue(it, "moveY") },
            field(
                "moveZ",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_Z,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayTypedValue(it, "moveZ") },
            field(
                "rotateX",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_X,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayTypedValue(it, "rotateX") },
            field(
                "rotateY",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_Y,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayTypedValue(it, "rotateY") },
            field(
                "rotateZ",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_MOTION_Z,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayTypedValue(it, "rotateZ") },
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
            field("kind", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONDITION_KIND, Material.COMPARATOR) { displayCondition(it) },
            field("condition", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_CONDITION_VALUE, Material.TARGET) { displayCondition(it) },
        )
        CommandType.DISK_CALL -> listOf(
            field("diskId", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DISK, Material.MUSIC_DISC_13),
            // issue #8: 呼び出し先へ渡す引数と戻り値の受取先は、専用の子画面で編集します。
            // 現在値欄には設定済み件数を示し、親タブを増やしません。
            field(
                "diskCallSettings",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DISK_CALL_SETTINGS,
                Material.WRITABLE_BOOK,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISK_CALL_SETTINGS,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISK_CALL_SETTINGS,
            ) { node ->
                val configured = node.declaredArguments().isNotEmpty() || node.string("returnName").isNotBlank()
                if (configured) {
                    DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CONFIGURED)
                } else {
                    displayUnset()
                }
            },
        )
        // issue #8: 「値を返す」は変数由来またはリテラル値のどちらかを返します。
        // 検証層と同じく source 指定時は変数、空欄時は value 欄のリテラルを採用します。
        CommandType.RETURN_VALUE -> listOf(
            field("source", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE_SOURCE, Material.NAME_TAG),
            field("value", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, Material.COMPARATOR) { displayVariableValue(it.string("value")) },
        )
        CommandType.VARIABLE -> listOf(
            field("scope", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE_SCOPE, Material.ENDER_EYE) { displayVariableScope(it.string("scope")) },
            field("operation", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATION, Material.REDSTONE) { displayVariableOperation(it.string("operation")) },
            field(
                "target",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                Material.PLAYER_HEAD,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISPLAY_TARGET,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISPLAY_TARGET,
            ) { it.targetSpec?.kind?.let(::displayTarget) ?: displayUnset() },
            field("name", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, Material.NAME_TAG),
            field("type", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TYPE, Material.STRUCTURE_VOID) { displayValueType(it.string("type")) },
            field("changeMode", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OPERATION, Material.COMPARATOR,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_OPERATION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_OPERATION,
            ) { displayVariableChangeMode(it.string("changeMode", "ASSIGN")) },
            field("value", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, Material.COMPARATOR) { displayVariableValue(it.string("value")) },
            field("source", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE_SOURCE, Material.NAME_TAG),
            field(
                "propertySource",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PROPERTY_SOURCE,
                Material.NAME_TAG,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_NAME,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_NAME,
            ),
            field(
                "property",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE_PROPERTY,
                Material.TARGET,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE,
            ) { displayLiteral(it.string("property")) },
            field(
                "randomMin",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_RANDOM_MIN,
                Material.COMPARATOR,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE,
            ),
            field(
                "randomMax",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_RANDOM_MAX,
                Material.COMPARATOR,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE,
            ),
        ) + typedValueSubFields(
            node ?: CommandNode(type = CommandType.VARIABLE),
            ValueType.parse(node?.string("type")) ?: ValueType.NUMBER,
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
     * NUMBER・STRING・BOOLEAN は共通値欄、VECTOR は成分欄、LOCATIONは位置・向きを
     * 共通設定画面へ委譲し、ITEM・BLOCKはメインハンド、ENTITYは共通対象選択、
     * SOUND・EFFECTは共通のID／数値入力仕様を使います。型自体はtempType欄で変更し、
     * 再設定は上書きとして扱います。値欄の組み立ては変数操作コマンドと共有します。
     */
    private fun tempSetFields(node: CommandNode): List<EditorField> = buildList {
        add(field("name", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VARIABLE, Material.NAME_TAG))
        add(field("tempType", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TYPE, Material.STRUCTURE_VOID) {
            displayTemporaryType(it.string("tempType"))
        })
        val type = ValueType.parse(node.string("tempType", ValueType.NUMBER.name)) ?: ValueType.NUMBER
        if (type == ValueType.NUMBER || type == ValueType.STRING || type == ValueType.BOOLEAN) {
            add(field("value", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_VALUE, Material.COMPARATOR) {
                displayVariableValue(it.string("value"))
            })
        }
        addAll(typedValueSubFields(node, type))
    }

    /**
     * issue #2 の型別値入力欄です。
     *
     * 一時変数の設定と変数操作コマンドで共有します。NUMBER・STRING・BOOLEAN は
     * 親の共通値欄を使うためここでは出しません。VECTOR は成分欄、複合6型は
     * 型ごとの共通設定欄を返します。
     */
    private fun typedValueSubFields(node: CommandNode, type: ValueType): List<EditorField> = buildList {
        when (type) {
            ValueType.NUMBER, ValueType.STRING, ValueType.BOOLEAN -> Unit
            ValueType.VECTOR -> {
                add(field(
                    "x",
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX,
                    Material.COMPARATOR,
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE,
                ))
                add(field(
                    "y",
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY,
                    Material.COMPARATOR,
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE,
                ))
                add(field(
                    "z",
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ,
                    Material.COMPARATOR,
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_VALUE,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_VALUE,
                ))
            }
            ValueType.LOCATION -> add(field(
                "location",
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_POSITION,
                Material.COMPASS,
                descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_POSITION,
                actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_POSITION,
            ) { displayTemporaryLocation(it) })
            ValueType.ITEM ->
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
            ValueType.BLOCK ->
                add(field("block", KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK, Material.BRICKS))
            ValueType.ENTITY ->
                add(field(
                    "entity",
                    KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TARGET,
                    Material.ARMOR_STAND,
                    descriptionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY,
                    actionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY,
                ) { it.temporaryEntityTargetSpec?.kind?.let(::displayTarget) ?: displayUnset() })
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
        // v17改名キーも同じ文言を使います。表示ラベル自体は変えません。
        "summonEntity" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY
        "placeDestination" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DESTINATION to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DESTINATION
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
        "source" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_NAME to KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_NAME
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
 * 種別IDの表示です。空欄は未設定とし、値がある場合は翻訳名解決へ委ねます。
 * 独自翻訳は持たず、各表示経路がバニラ翻訳キーへ変換します。
 */
private fun displayEntityTypeName(rawId: String): DisplayValue =
    rawId.takeIf(String::isNotBlank)?.let(DisplayValue::EntityTypeName) ?: displayUnset()

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
    // 種類はバニラID表記で示します。保存形式のenum名は使いません。
    val particle = ParticleSettings.particle(node)?.let { ResourcePicker.particleDisplayId(it.name) }
        ?: node.string(ParticleSettings.PARAM_PARTICLE)
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

/** 条件の判定対象を表示します。旧インベントリGUIは表示専用であり、編集はGesture側で行います。 */
private fun displayCondition(node: CommandNode) = node.conditionSubject()
    ?.let { DisplayValue.Localized(it.key) } ?: displayUnset()

/** issue #2 の共通値型を、既存の表示キーで解決します。真偽値・ベクトルは新規キーを使います。 */
private fun displayValueTypeLabel(type: ValueType): LocalizationKey<String> = when (type) {
    ValueType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMBER
    ValueType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT
    ValueType.BOOLEAN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_TYPE_BOOLEAN
    ValueType.LOCATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION
    ValueType.VECTOR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_TYPE_VECTOR
    ValueType.ITEM -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM
    ValueType.BLOCK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK
    ValueType.ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
}

private fun displayValueType(value: String) = runCatching { ValueType.valueOf(value) }.getOrNull()?.let {
    DisplayValue.Localized(displayValueTypeLabel(it))
} ?: displayUnset()

/** 一時変数の型を、変数操作コマンドと共通の表示キーで解決します。 */
private fun displayTemporaryType(value: String) =
    ValueType.parse(value)?.let {
        DisplayValue.Localized(displayValueTypeLabel(it))
    } ?: displayUnset()

/** issue #2 の変数scopeを、既存の表示キーで解決します。 */
private fun displayVariableScope(value: String) = VariableScope.parse(value)?.let {
    DisplayValue.Localized(when (it) {
        VariableScope.EXECUTION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_EXECUTION
        VariableScope.WORLD -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_SCOPE_WORLD
        VariableScope.ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
    })
} ?: displayUnset()

private fun displayVariableOperation(value: String) = runCatching { VariableOperation.valueOf(value) }.getOrNull()?.let {
    DisplayValue.Localized(when (it) {
        VariableOperation.DEFINE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DEFINE
        VariableOperation.SET -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SET
        VariableOperation.CHANGE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CHANGE
        VariableOperation.COPY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_OPERATION_COPY
        VariableOperation.DELETE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REMOVE
    })
} ?: displayUnset()

private fun displayVariableChangeMode(value: String) = runCatching { VariableChangeMode.valueOf(value) }.getOrNull()?.let {
    DisplayValue.Localized(when (it) {
        VariableChangeMode.ASSIGN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ASSIGN
        VariableChangeMode.CALCULATE -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CALCULATE
        VariableChangeMode.PROPERTY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_CHANGE_PROPERTY
        VariableChangeMode.RANDOM -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_CHANGE_RANDOM
    })
} ?: displayUnset()

private fun displayVariableValue(value: String) = when (value) {
    "${'$'}{${SystemVariableNames.CURRENT_LOOP_COUNT}}" -> DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT)
    else -> displayLiteral(value)
}

private fun displayEntityAction(value: String) = DisplayValue.Localized(
    when (value) {
        "dismount" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISMOUNT
        // 旧equipはPLACE_ITEM、旧tagはENTITY_OPERATIONへ移行済みのため表示しません。
        "motion" -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MOTION
        else -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RIDE
    },
)

/**
 * 統合選択の設定対象表示です。
 *
 * 未選択・不明値は未設定表示とし、表示文字列から意味を推測しません。
 * attribute IDは対象識別名として原文表示します。
 */
private fun displayEntityDataPath(node: CommandNode): DisplayValue {
    return when (val parsed = EntityDataPath.parse(node.string(EntityDataPath.PARAM_PATH))) {
        is EntityDataPath.Parsed.Tag ->
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ENTITY_DATA_TAG)
        is EntityDataPath.Parsed.Attribute -> displayLiteral(parsed.attributeId)
        is EntityDataPath.Parsed.Property -> {
            val definition = EntityDataSchema.definition(parsed.propertyId) ?: return displayUnset()
            val knownType = EntityDataSchema.knownEntityType(node.targetSpec)
            if (definition !in EntityDataSchema.propertiesFor(knownType)) return displayUnset()
            DisplayValue.Localized(definition.optionKey)
        }
        null -> displayUnset()
    }
}

/**
 * タグ一覧の表示です。件数のみを示し、個別タグは一覧画面で確認します。
 *
 * Nameへ可変の件数・内容を含めない画面契約に従い、件数は値欄の
 * リテラル表示に留めます。
 */
private fun displayTagEntries(node: CommandNode): DisplayValue {
    val entries = EntityTagEntries.parse(node.string(EntityTagEntries.PARAM_ENTRIES))
        ?: return displayUnset()
    if (entries.isEmpty()) return displayUnset()
    return displayLiteral(entries.size.toString())
}

/**
 * issue #10 の召喚時個別設定の表示です。
 *
 * 設定件数を値欄のリテラル表示に留め、Nameへ件数・内容を含めません。
 * 項目ごとの値は一覧画面で編集します。
 */
private fun displaySummonData(node: CommandNode): DisplayValue {
    val count = EntityDataSchema.summonDataEntries(node.params).size
    if (count == 0) return displayUnset()
    return displayLiteral(count.toString())
}

/**
 * データ設定値の表示です。
 *
 * 真偽値は有効・無効の選択表示、有限候補は候補名、文字列・整数は原文表示とし、
 * Nameへ現在値を混ぜない画面契約に従います。
 */
private fun displayEntityPropertyValue(node: CommandNode): DisplayValue {
    val propertyId = EntityDataPath.propertyIdOf(node.string(EntityDataPath.PARAM_PATH))
        ?: return displayUnset()
    val definition = EntityDataSchema.definition(propertyId)
        ?: return displayUnset()
    val raw = node.string(EntityDataSchema.PARAM_VALUE)
    return when (definition.valueKind) {
        EntityPropertyValueKind.BOOLEAN -> displayBoolean(raw.toBooleanStrictOrNull() ?: false)
        EntityPropertyValueKind.ENUM -> definition.enumChoices.firstOrNull { it.id == raw }
            ?.let { DisplayValue.Localized(it.optionKey) } ?: displayUnset()
        EntityPropertyValueKind.TEXT, EntityPropertyValueKind.INTEGER -> displayLiteral(raw)
    }
}
/**
 * 配置先を表示します。不明値は未設定として扱い、保存値の正本は検証側に残します。
 */
private fun displayPlaceDestination(value: String): DisplayValue =
    PlaceItemDestination.from(value)?.let { DisplayValue.Localized(it.labelKey) } ?: displayUnset()

/**
 * 配置スロットを表示します。数値スロットは GUI 表示 (1始まり) へ変換し、
 * 保存形式 (0始まり) をそのまま見せません。装備は既存の装備表示へ委ねます。
 */
private fun displayPlaceSlot(node: CommandNode): DisplayValue {
    val destination = PlaceItemDestination.from(node.string("placeDestination")) ?: return displayUnset()
    val slot = node.string("slot")
    if (!PlaceItemDestination.isValidSlot(destination, slot)) return displayUnset()
    if (destination == PlaceItemDestination.EQUIPMENT) return displayEquipmentSlot(slot)
    if (destination == PlaceItemDestination.ITEM_FRAME) return DisplayValue.Localized(destination.labelKey)
    return DisplayValue.Literal(PlaceItemDestination.toDisplay(destination, slot))
}

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
