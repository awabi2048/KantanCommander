package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.VariableOperation
import me.awabi2048.kantancommander.model.VariableScope
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.setConditionSubject
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.EntityDataSchema
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.RegionAnchorKind
import me.awabi2048.kantancommander.model.RegionAnchorSpec
import me.awabi2048.kantancommander.model.RegionSpec
import me.awabi2048.kantancommander.model.ValueType
import java.util.UUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows

class CommandSettingsModelTest {
    @Test
    fun `target roles share inventory and gesture storage`() {
        val node = CommandNode(type = CommandType.TELEPORT)
        val destination = TargetSpec(TargetKind.NEAREST_PLAYER)

        CommandSettingsModel.setTargetSpec(node, CommandSettingRole.DESTINATION, destination)

        assertEquals(destination, node.destinationTargetSpec)
        assertNull(node.destinationSpec)
        assertEquals(destination, CommandSettingsModel.targetSpec(node, CommandSettingRole.DESTINATION))
        assertEquals(PositionKind.TARGET, CommandSettingsModel.positionKind(node, CommandSettingRole.DESTINATION))
    }

    @Test
    fun `coordinate position is incomplete until all values are entered`() {
        val node = CommandType.TELEPORT.newNode()

        CommandSettingsModel.setPositionSpec(
            node,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.COORDINATES),
        )

        assertEquals(PositionKind.COORDINATES, CommandSettingsModel.positionKind(node, CommandSettingRole.DESTINATION))
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "position", CommandSettingRole.DESTINATION))
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "destination"))
        assertTrue(node.configuredFields == null || "destination" !in node.configuredFields.orEmpty())

        CommandSettingsModel.setPositionSpec(
            node,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0),
        )

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "position", CommandSettingRole.DESTINATION))
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "destination"))
    }

    @Test
    fun `incomplete structured values never become configured from an old marker`() {
        val node = CommandType.TELEPORT.newNode().apply {
            configuredFields = linkedSetOf("destination")
            destinationSpec = PositionSpec(PositionKind.COORDINATES)
        }

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "position", CommandSettingRole.DESTINATION))
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "destination"))
    }

    @Test
    fun `temporary target is incomplete until a name is supplied`() {
        // issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
        // 単一参照の完了判定は一時変数参照で固定します。
        val node = CommandType.ENTITY_DELETE.newNode()
        val incomplete = TargetSpec(TargetKind.TEMPORARY)

        CommandSettingsModel.setTargetSpec(node, CommandSettingRole.NODE_TARGET, incomplete)

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET))
        assertFalse(node.isExplicitlyConfigured("target"))

        CommandSettingsModel.setTargetSpec(
            node,
            CommandSettingRole.NODE_TARGET,
            incomplete.copy(tempName = "foe"),
        )

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET))
    }

    @Test
    fun `temporary target position and facing are incomplete until a name is supplied`() {
        val node = CommandType.TELEPORT.newNode()

        CommandSettingsModel.setTargetSpec(
            node,
            CommandSettingRole.NODE_TARGET,
            TargetSpec(TargetKind.TEMPORARY),
        )
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET))

        CommandSettingsModel.setTargetSpec(
            node,
            CommandSettingRole.NODE_TARGET,
            TargetSpec(TargetKind.TEMPORARY, tempName = "selected"),
        )
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET))

        CommandSettingsModel.setPositionSpec(
            node,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.TEMPORARY),
        )
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "destination"))

        CommandSettingsModel.setPositionSpec(
            node,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.TEMPORARY, tempName = "point"),
        )
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "destination"))

        CommandSettingsModel.setFacingSpec(
            node,
            FacingSpec(FacingKind.TEMPORARY),
            CommandSettingRole.DESTINATION_FACING,
        )
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "destinationFacing"))

        CommandSettingsModel.setFacingSpec(
            node,
            FacingSpec(FacingKind.TEMPORARY, tempName = "point"),
            CommandSettingRole.DESTINATION_FACING,
        )
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "destinationFacing"))
    }

    @Test
    fun `temporary value fields follow the selected type`() {
        val node = CommandType.TEMP_SET.newNode()

        assertEquals(listOf("name", "tempType", "value"), CommandSettingsModel.visibleFields(node).map { it.key })
        node.params["tempType"] = ValueType.LOCATION.name
        assertEquals(listOf("name", "tempType", "location"), CommandSettingsModel.visibleFields(node).map { it.key })
    }

    @Test
    fun `coordinate facing is incomplete until all values are entered`() {
        val node = CommandType.TELEPORT.newNode()

        CommandSettingsModel.setFacingSpec(
            node,
            FacingSpec(FacingKind.COORDINATES, x = 1.0, y = null, z = 3.0),
            CommandSettingRole.DESTINATION_FACING,
        )

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "facing", CommandSettingRole.DESTINATION_FACING))
        assertFalse(node.isExplicitlyConfigured("destinationFacing"))

        CommandSettingsModel.setFacingSpec(
            node,
            FacingSpec(FacingKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0),
            CommandSettingRole.DESTINATION_FACING,
        )

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "facing", CommandSettingRole.DESTINATION_FACING))
    }

    @Test
    fun `empty explicitly entered text is not a configured value`() {
        val node = CommandType.WAIT.newNode()

        CommandSettingsModel.setParameter(node, "seconds", "")

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "seconds"))
        assertFalse(node.isExplicitlyConfigured("seconds"))
    }

    @Test
    fun `position lookup does not invent a command position for another role`() {
        val node = CommandType.APPLY_EFFECT.newNode()

        assertNull(CommandSettingsModel.positionSpec(node, CommandSettingRole.NODE_TARGET))
        assertNull(CommandSettingsModel.positionKind(node, CommandSettingRole.NODE_TARGET))
    }

    @Test
    fun `visible fields apply the same conditional rules`() {
        val display = CommandType.DISPLAY_TEXT.newNode()
        assertEquals(false, CommandSettingsModel.visibleFields(display).any { it.key == "staySeconds" })
        assertEquals(false, CommandSettingsModel.visibleFields(display).any { it.key == "subtitle" })

        display.params["mode"] = "title"
        assertEquals(true, CommandSettingsModel.visibleFields(display).any { it.key == "staySeconds" })
        assertEquals(true, CommandSettingsModel.visibleFields(display).any { it.key == "subtitle" })

        display.params["mode"] = "actionbar"
        val actionbarDuration = CommandSettingsModel.visibleFields(display).single { it.key == "staySeconds" }
        assertEquals(true, CommandSettingsModel.visibleFields(display).any { it.key == "staySeconds" })
        assertEquals(
            com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys
                .KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISPLAY_ACTIONBAR_DURATION,
            actionbarDuration.descriptionKey,
        )
        assertEquals(
            com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys
                .KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DISPLAY_ACTIONBAR_DURATION,
            actionbarDuration.actionKey,
        )

        val riding = CommandType.ENTITY_ACTION.newNode()
        assertEquals(true, CommandSettingsModel.visibleFields(riding).any { it.key == "other" })
        riding.params["action"] = "dismount"
        assertEquals(false, CommandSettingsModel.visibleFields(riding).any { it.key == "other" })
    }

    @Test
    fun `execution dependent fields are hidden without clearing saved values`() {
        val teleport = CommandType.TELEPORT.newNode()
        val savedFacing = FacingSpec(FacingKind.ROTATION, yaw = 90.0f, pitch = 15.0f)
        CommandSettingsModel.setFacingSpec(
            teleport,
            savedFacing,
            CommandSettingRole.DESTINATION_FACING,
        )
        CommandSettingsModel.setPositionSpec(
            teleport,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.TEMPORARY, tempName = "point"),
        )

        assertFalse(CommandSettingsModel.visibleFields(teleport).any { it.key == "destinationFacing" })
        assertEquals(savedFacing, teleport.destinationFacingSpec)

        CommandSettingsModel.setPositionSpec(
            teleport,
            CommandSettingRole.DESTINATION,
            PositionSpec(PositionKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0),
        )
        assertTrue(CommandSettingsModel.visibleFields(teleport).any { it.key == "destinationFacing" })

        val sound = CommandType.PLAY_SOUND.newNode()
        assertTrue(CommandSettingsModel.visibleFields(sound).any { it.key == "soundTargetPosition" })
        sound.params["soundOperation"] = "STOP"
        assertTrue(CommandSettingsModel.visibleFields(sound).any { it.key == "soundTargetPosition" })
        assertFalse(CommandSettingsModel.visibleFields(sound).any { it.key == "soundScope" })
        sound.params["soundOperation"] = "PLAY"
        assertTrue(CommandSettingsModel.visibleFields(sound).any { it.key == "soundScope" })
    }

    @Test
    fun `gesture display timing stays one tab while showing each current value`() {
        val title = CommandType.DISPLAY_TEXT.newNode().apply { params["mode"] = "title" }

        assertEquals(
            listOf("target", "mode", "text", "subtitle", "staySeconds", "executionPosition"),
            CommandSettingsModel.visibleFields(title).map { it.key },
        )
        assertEquals(
            listOf("target", "mode", "text", "subtitle", "staySeconds", "executionPosition"),
            CommandSettingsModel.gestureVisibleFields(title).map { it.key },
        )
        assertEquals(
            com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys
                .KANTAN_COMMANDER_CLEAN_GUI_FIELD_DURATION,
            CommandSettingsModel.gestureVisibleFields(title).single { it.key == "staySeconds" }.label,
        )
        assertEquals(
            DisplayValue.Timing("1", "3", "1"),
            CommandSettingsModel.gestureVisibleFields(title).single { it.key == "staySeconds" }.value(title),
        )

        val tellraw = CommandType.DISPLAY_TEXT.newNode()
        assertEquals(
            listOf("target", "mode", "text", "executionPosition"),
            CommandSettingsModel.gestureVisibleFields(tellraw).map { it.key },
        )
    }

    @Test
    fun `incomplete warnings use fixed keys for each setting tab`() {
        val display = CommandType.DISPLAY_TEXT.newNode().apply { params["mode"] = "title" }
        val giveItem = CommandType.GIVE_ITEM.newNode()
        val sound = CommandType.PLAY_SOUND.newNode()
        val teleport = CommandType.TELEPORT.newNode()
        val entityAction = CommandType.ENTITY_ACTION.newNode()
        val summon = CommandType.SUMMON_ENTITY.newNode()

        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.duration",
            CommandSettingsModel.incompleteWarningKey(display, "staySeconds").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.give_target",
            CommandSettingsModel.incompleteWarningKey(giveItem, "target").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.sound_setting",
            CommandSettingsModel.incompleteWarningKey(sound, "soundSetting").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.destination_facing",
            CommandSettingsModel.incompleteWarningKey(teleport, "destinationFacing").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.tag_operation",
            CommandSettingsModel.incompleteWarningKey(entityAction, "tagOperation").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.summon_position",
            CommandSettingsModel.incompleteWarningKey(summon, "summonPosition").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.sound_scope",
            CommandSettingsModel.incompleteWarningKey(sound, "soundScope").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.sound_target_position",
            CommandSettingsModel.incompleteWarningKey(sound, "soundTargetPosition").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.operation",
            CommandSettingsModel.incompleteWarningKey(sound, "soundOperation").id,
        )
    }

    @Test
    fun `validation fields are normalized to visible setting tabs`() {
        val display = CommandType.DISPLAY_TEXT.newNode().apply { params["mode"] = "title" }
        val tellraw = CommandType.DISPLAY_TEXT.newNode()
        val sound = CommandType.PLAY_SOUND.newNode()

        assertEquals("staySeconds", CommandSettingsModel.visibleAttentionFieldKey(display, "fadeInSeconds"))
        assertEquals("staySeconds", CommandSettingsModel.visibleAttentionFieldKey(display, "staySeconds"))
        assertEquals("staySeconds", CommandSettingsModel.visibleAttentionFieldKey(display, "fadeOutSeconds"))
        assertEquals("text", CommandSettingsModel.visibleAttentionFieldKey(display, "subtitle"))
        assertEquals(null, CommandSettingsModel.visibleAttentionFieldKey(tellraw, "fadeInSeconds"))
        assertEquals("soundSetting", CommandSettingsModel.visibleAttentionFieldKey(sound, "volume"))
        assertEquals("soundSetting", CommandSettingsModel.visibleAttentionFieldKey(sound, "pitch"))
        assertEquals("soundSetting", CommandSettingsModel.visibleAttentionFieldKey(sound, "sound"))
        assertEquals("soundTargetPosition", CommandSettingsModel.visibleAttentionFieldKey(sound, "soundPosition"))
        assertEquals("soundTargetPosition", CommandSettingsModel.visibleAttentionFieldKey(sound, "target"))
    }

    @Test
    fun `entity action fields follow the selected operation allowlist`() {
        val node = CommandType.ENTITY_ACTION.newNode()
        fun keys(action: String): List<String> {
            node.params["action"] = action
            return CommandSettingsModel.visibleFields(node).map { it.key }
        }

        assertEquals(listOf("target", "action", "other", "executionPosition"), keys("ride"))
        assertEquals(listOf("target", "action", "executionPosition"), keys("dismount"))
        assertEquals(listOf("target", "action", "motionX", "motionY", "motionZ", "executionPosition"), keys("motion"))
        // 旧equipはPLACE_ITEM、旧tagはENTITY_OPERATIONへ移行したため基本欄へ畳みます。
        assertEquals(listOf("target", "action", "executionPosition"), keys("equip"))
        assertEquals(listOf("target", "action", "executionPosition"), keys("tag"))
    }

    @Test
    fun `place item fields follow the selected destination`() {
        val node = CommandType.PLACE_ITEM.newNode()
        fun keys(destination: String): List<String> {
            node.params["placeDestination"] = destination
            return CommandSettingsModel.visibleFields(node).map { it.key }
        }

        // エンティティ系は対象欄、ブロック系は配置位置欄を使い分けます。
        // 額縁は内容固定のためスロット欄を出しません。
        // 上書き設定は独立タブを持たず、配置先タブの中央下トグルで編集します。
        assertEquals(
            listOf("target", "placeDestination", "slot", "item", "count", "executionPosition"),
            keys("EQUIPMENT"),
        )
        assertEquals(
            listOf("target", "placeDestination", "slot", "item", "count", "executionPosition"),
            keys("PLAYER_HOTBAR"),
        )
        assertEquals(
            listOf("target", "placeDestination", "item", "count", "executionPosition"),
            keys("ITEM_FRAME"),
        )
        assertEquals(
            listOf("placeDestination", "slot", "item", "count", "position", "executionPosition"),
            keys("BLOCK_CONTAINER"),
        )
    }

    @Test
    fun `place item overwrite attention projects to the destination tab`() {
        // 上書き検証の注意表示は、存在しない独立タブではなく配置先タブへ投影します。
        val node = CommandType.PLACE_ITEM.newNode()
        node.params["placeDestination"] = "EQUIPMENT"
        assertEquals("placeDestination", CommandSettingsModel.visibleAttentionFieldKey(node, "overwrite"))
        assertFalse(CommandSettingsModel.visibleFields(node).any { it.key == "overwrite" })
        assertFalse(CommandSettingsModel.gestureVisibleFields(node).any { it.key == "overwrite" })
    }

    @Test
    fun `gesture column tabs fit within six for every place destination`() {
        // 実行位置タブは固定行へ抽出するため、上限検査の対象外にします。
        // 上書き独立タブの除去後は最大5件ですが、上限超過の回帰として維持します。
        fun columnKeys(destination: String?): List<String> {
            val node = CommandType.PLACE_ITEM.newNode()
            if (destination == null) node.params.remove("placeDestination") else node.params["placeDestination"] = destination
            return CommandSettingsModel.gestureVisibleFields(node)
                .map { it.key }
                .filter { it != "executionPosition" }
        }
        for (destination in listOf("EQUIPMENT", "PLAYER_HOTBAR", "PLAYER_INVENTORY", "ITEM_FRAME", "BLOCK_CONTAINER")) {
            assertTrue(columnKeys(destination).size <= 6, destination)
        }
        assertTrue(columnKeys(null).size <= 6, "null destination")
    }

    @Test
    fun `summon entity type follows its own params value`() {
        // v17改名の回帰試験です。一時対象指定と同名だった旧キーの誤判定を固定します。
        val node = CommandType.SUMMON_ENTITY.newNode()
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "summonEntity"))

        CommandSettingsModel.setParameter(node, "summonEntity", "minecraft:mannequin")
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "summonEntity"))

        // 一時対象指定の有無は召喚種類の判定へ混ざりません。
        val bare = CommandType.SUMMON_ENTITY.newNode()
        bare.temporaryEntityTargetSpec = me.awabi2048.kantancommander.model.TargetSpec(
            me.awabi2048.kantancommander.model.TargetKind.NEAREST_ENTITY,
        )
        assertFalse(CommandSettingsModel.isFieldConfigured(bare, "summonEntity"))
    }

    @Test
    fun `place destination follows its own params value`() {
        // v17改名の回帰試験です。テレポート移動先と同名だった旧キーの誤判定を固定します。
        val node = CommandType.PLACE_ITEM.newNode()
        node.params.remove("placeDestination")
        node.clearConfigured("placeDestination")
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "placeDestination"))

        CommandSettingsModel.setParameter(node, "placeDestination", "EQUIPMENT")
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "placeDestination"))

        // テレポート移動先の有無は配置先の判定へ混ざりません。
        val bare = CommandType.PLACE_ITEM.newNode()
        bare.params.remove("placeDestination")
        bare.clearConfigured("placeDestination")
        bare.destinationSpec = me.awabi2048.kantancommander.model.PositionSpec(
            me.awabi2048.kantancommander.model.PositionKind.DISK,
        )
        assertFalse(CommandSettingsModel.isFieldConfigured(bare, "placeDestination"))
    }

    @Test
    fun `literal params fields are display-consistent across all command types`() {
        // v17改名の再発防止試験です。同名キーの意味衝突（保存先と表示判定の
        // 不一致）を全種別×可視項目で機械検証します。構造値・複合項目は
        // 専用の表示規則を持つため対象外です。
        val structuralKeys = setOf(
            "target", "other", "entity", "location", "destination", "destinationFacing",
            "position", "from", "to", "facing",
            "soundPosition", "particlePosition", "particleEnd", "geometryEnd",
            "summonPosition", "fireworkPosition", "executionPosition",
            "soundTargetPosition",
        )
        val compositeKeys = setOf(
            "soundSetting", "particleDisplay", "condition", "geometrySettings", "particleParameters",
            "fireworkContents",
            // issue #11: 設置位置（structurePlace）は構造値のため表示試験の対象外です。
            "structurePlace",
        )
        for (type in CommandType.entries) {
            val keys = CommandSettingsModel.visibleFields(type.newNode()).map { it.key }.toSet()
            for (key in keys) {
                if (key in structuralKeys || key in compositeKeys) continue
                val fresh = type.newNode()
                assertFalse(
                    CommandSettingsModel.isFieldConfigured(fresh, key),
                    "$type/$key は初期状態で未設定のはず",
                )
                val node = type.newNode()
                try {
                    CommandSettingsModel.setParameter(node, key, "probevalue")
                } catch (ignored: IllegalArgumentException) {
                    // 型欄など値域制限のある項目は探査対象外とします。
                    continue
                }
                assertTrue(
                    CommandSettingsModel.isFieldConfigured(node, key),
                    "$type/$key は設定後に設定済みのはず",
                )
            }
        }
    }

    @Test
    fun `descriptor maps structured fields to shared editors`() {
        val condition = CommandSettingsModel.descriptor(CommandNode(type = CommandType.CONDITION), "condition")
        assertEquals(CommandSettingEditor.CONDITION_DETAIL, condition.editor)

        val variable = CommandSettingsModel.descriptor(CommandNode(type = CommandType.VARIABLE), "operation")
        assertEquals(CommandSettingEditor.VARIABLE_OPERATION, variable.editor)
    }

    @Test
    fun `target kind and detailed filter domains are explicitly separated`() {
        val giveItem = CommandType.GIVE_ITEM.newNode()
        assertEquals("target", CommandSettingsModel.visibleFields(giveItem).first().key)
        assertEquals(
            CommandSettingEditor.TARGET,
            CommandSettingsModel.descriptor(giveItem, "target").editor,
        )

        assertTrue(CommandSettingsModel.targetSupportsDetailedFilters(TargetKind.NEARBY_ENTITIES))
        assertTrue(CommandSettingsModel.targetSupportsDetailedFilters(TargetKind.NEAREST_PLAYER))
        // issue #34-4: 一時変数参照は詳細条件を使いません。
        // issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
        assertFalse(CommandSettingsModel.targetSupportsDetailedFilters(TargetKind.TEMPORARY))
        assertFalse(CommandSettingsModel.targetSupportsDetailedFilters(null))
        assertEquals(
            listOf(
                TargetKind.NEAREST_PLAYER,
                TargetKind.NEARBY_PLAYERS,
                TargetKind.ALL_PLAYERS,
                TargetKind.RANDOM_PLAYER,
            ),
            CommandSettingsModel.targetKinds(TargetCategory.PLAYER),
        )
        assertTrue(CommandSettingsModel.targetFilterApplies(TargetKind.NEAREST_PLAYER, "kind"))
        assertFalse(CommandSettingsModel.targetFilterApplies(null, "kind"))
    }

    @Test
    fun `configured state preserves explicit selection of a default value`() {
        val node = CommandType.GIVE_ITEM.newNode()

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "count"))
        node.markConfigured("count")

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "count"))
    }

    @Test
    fun `variable operations expose only their required fields`() {
        // #2: 操作内容ごとに必要な項目だけを表示します。
        fun keysFor(operation: VariableOperation): List<String> {
            val node = CommandType.VARIABLE.newNode().apply {
                params["operation"] = operation.name
            }
            return CommandSettingsModel.visibleFields(node).map { it.key }
        }

        assertEquals(
            listOf("scope", "operation", "name", "type", "changeMode", "value"),
            keysFor(VariableOperation.DEFINE),
        )
        assertEquals(
            listOf("scope", "operation", "name", "type", "changeMode", "value"),
            keysFor(VariableOperation.SET),
        )
        assertEquals(
            listOf("scope", "operation", "name", "changeMode", "value"),
            keysFor(VariableOperation.CHANGE),
        )
        assertEquals(
            listOf("scope", "operation", "name", "source"),
            keysFor(VariableOperation.COPY),
        )
        assertEquals(
            listOf("scope", "operation", "name"),
            keysFor(VariableOperation.DELETE),
        )
    }

    @Test
    fun `variable scope routes names to candidates or free text`() {
        // #2: WORLD scope の変数名は定義済み候補から選択し、EXECUTION scope は文字列入力です。
        val world = CommandType.VARIABLE.newNode().apply { params["scope"] = VariableScope.WORLD.name }
        assertEquals(
            CommandSettingEditor.VARIABLE_NAME,
            CommandSettingsModel.descriptor(world, "name").editor,
        )
        val execution = CommandType.VARIABLE.newNode().apply { params["scope"] = VariableScope.EXECUTION.name }
        assertEquals(
            CommandSettingEditor.TEXT,
            CommandSettingsModel.descriptor(execution, "name").editor,
        )
        // scope 欄のない旧ノードは WORLD として読み替えます。
        val legacy = CommandType.VARIABLE.newNode().apply { params.remove("scope") }
        assertEquals(
            CommandSettingEditor.VARIABLE_NAME,
            CommandSettingsModel.descriptor(legacy, "name").editor,
        )
    }

    @Test
    fun `entity scope exposes the target tab only for entity variables`() {
        // #22: 対象は ENTITY scope でのみ表示します。
        val entity = CommandType.VARIABLE.newNode().apply { params["scope"] = VariableScope.ENTITY.name }
        assertTrue(CommandSettingsModel.visibleFields(entity).map { it.key }.contains("target"))
        assertEquals(
            CommandSettingEditor.TARGET,
            CommandSettingsModel.descriptor(entity, "target").editor,
        )
        val world = CommandType.VARIABLE.newNode().apply { params["scope"] = VariableScope.WORLD.name }
        assertTrue(CommandSettingsModel.visibleFields(world).map { it.key }.none { it == "target" })
    }

    @Test
    fun `repeat command exposes only its count field and rejects reserved variable definitions`() {
        val repeat = CommandType.FOR_START.newNode()
        assertEquals(listOf("count"), CommandSettingsModel.visibleFields(repeat).map { it.key })

        val variable = CommandType.VARIABLE.newNode()
        assertThrows(IllegalArgumentException::class.java) {
            CommandSettingsModel.setParameter(variable, "name", "CURRENT_LOOP_COUNT")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommandSettingsModel.setParameter(variable, "name", "current_loop_count")
        }
    }

    @Test
    fun `target filter state is independent for multi-select details`() {
        val node = CommandType.GIVE_ITEM.newNode().apply {
            targetSpec = TargetSpec(TargetKind.NEARBY_PLAYERS, maximumDistance = 12.0, dx = 4.0)
        }

        assertTrue(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "distance"))
        assertTrue(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "range"))
        assertFalse(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "sort"))
    }

    @Test
    fun `region filter state follows the explicit region`() {
        val node = CommandType.GIVE_ITEM.newNode().apply {
            targetSpec = TargetSpec(
                TargetKind.NEAREST_ENTITY,
                region = RegionSpec(
                    anchor = RegionAnchorSpec(RegionAnchorKind.CONTROL_BLOCK),
                    dx = 2.0,
                ),
            )
        }

        assertTrue(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "region"))
        assertTrue(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "region-anchor:CONTROL_BLOCK"))
        assertFalse(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "region-anchor:FIXED"))
        assertTrue(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "region-range"))
        assertFalse(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "region-distance"))
        // プリセット形状から外れるため、プリセット表示へ戻りません。
        assertNull(SettingPresets.matchTargetPreset(node.targetSpec))

        // 要約文は基準名と範囲を含みます。
        val summary = CommandSettingsModel.formatRegionSummary(node.targetSpec?.region) { key ->
            when (key) {
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_REGION_ANCHOR_CONTROL_BLOCK -> "制御ブロック基準"
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DX -> "dx"
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DY -> "dy"
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DZ -> "dz"
                KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_UNSET -> "未設定"
                else -> key.toString()
            }
        }
        assertTrue(summary?.contains("制御ブロック基準") == true, summary)
        assertTrue(summary?.contains("dx=2") == true, summary)
        // 未設定の領域は要約しません。
        assertNull(CommandSettingsModel.formatRegionSummary(null) { key -> key.toString() })
        assertNull(CommandSettingsModel.formatRegionSummary(RegionSpec()) { key -> key.toString() })
    }

    @Test
    fun `sound parameters are configured as one visible field`() {
        val node = CommandType.PLAY_SOUND.newNode()
        val fields = CommandSettingsModel.visibleFields(node)

        assertEquals(listOf("soundOperation", "soundTargetPosition", "soundSetting", "soundScope", "executionPosition"), fields.map { it.key })
        assertFalse(fields.any { it.key == "volume" || it.key == "pitch" })

        node.params["volume"] = "0.5"
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "soundSetting"))
    }

    @Test
    fun `block operation exposes only the position domain for the selected mode`() {
        val node = CommandType.BLOCK_OPERATION.newNode()

        assertTrue(CommandSettingsModel.visibleFields(node).any { it.key == "position" })
        assertFalse(CommandSettingsModel.visibleFields(node).any { it.key == "from" })
        assertFalse(CommandSettingsModel.visibleFields(node).any { it.key == "to" })

        node.params["operation"] = "fill"
        assertFalse(CommandSettingsModel.visibleFields(node).any { it.key == "position" })
        assertTrue(CommandSettingsModel.visibleFields(node).any { it.key == "from" })
        assertTrue(CommandSettingsModel.visibleFields(node).any { it.key == "to" })
        assertEquals(CommandSettingRole.BLOCK_FROM, CommandSettingsModel.descriptor(node, "from").role)
    }

    @Test
    fun `typed command values switch between literal and temporary sources as one binding`() {
        val item = CommandType.GIVE_ITEM.newNode().apply {
            params["item"] = "minecraft:stone"
            params["itemData"] = "encoded"
        }

        assertEquals(CommandValueSource.LITERAL, CommandSettingsModel.temporaryValueSource(item, "item"))
        assertTrue(CommandSettingsModel.isFieldConfigured(item, "item"))

        CommandSettingsModel.selectTemporaryValueSource(item, "item")
        assertEquals(CommandValueSource.TEMPORARY, CommandSettingsModel.temporaryValueSource(item, "item"))
        assertNull(CommandSettingsModel.temporaryValueReference(item, "item"))
        assertTrue(item.params["item"].isNullOrBlank())
        assertTrue(item.params["itemData"].isNullOrBlank())
        assertFalse(CommandSettingsModel.isFieldConfigured(item, "item"))

        CommandSettingsModel.setTemporaryValueReference(item, "item", "gift")
        assertEquals("gift", item.itemTempRef)
        assertTrue(CommandSettingsModel.isFieldConfigured(item, "item"))

        CommandSettingsModel.selectLiteralValueSource(item, "item")
        assertEquals(CommandValueSource.LITERAL, CommandSettingsModel.temporaryValueSource(item, "item"))
        assertNull(item.itemTempRef)
    }

    @Test
    fun `all executable composite value fields share the source selector`() {
        val cases = listOf(
            CommandType.GIVE_ITEM.newNode() to "item",
            CommandType.PLACE_ITEM.newNode() to "item",
            CommandType.BLOCK_OPERATION.newNode() to "block",
        )

        cases.forEach { (node, field) ->
            assertTrue(CommandSettingsModel.supportsTemporaryValueReference(node, field))
            assertEquals(CommandValueSource.LITERAL, CommandSettingsModel.temporaryValueSource(node, field))

            CommandSettingsModel.selectTemporaryValueSource(node, field)
            assertEquals(CommandValueSource.TEMPORARY, CommandSettingsModel.temporaryValueSource(node, field))
            assertFalse(CommandSettingsModel.isFieldConfigured(node, field))

            CommandSettingsModel.setTemporaryValueReference(node, field, "shared")
            assertEquals("shared", CommandSettingsModel.temporaryValueReference(node, field))
            assertTrue(CommandSettingsModel.isFieldConfigured(node, field))
        }
    }

    @Test
    fun `editing a typed literal clears an old temporary reference`() {
        val item = CommandType.GIVE_ITEM.newNode().apply {
            itemTempRef = "giftValue"
            configuredFields = linkedSetOf("itemTempRef")
        }

        CommandSettingsModel.setParameter(item, "item", "minecraft:stone")

        assertNull(item.itemTempRef)
        assertEquals(CommandValueSource.LITERAL, CommandSettingsModel.temporaryValueSource(item, "item"))
        assertTrue(CommandSettingsModel.isFieldConfigured(item, "item"))
    }

    @Test
    fun `sound and effect settings stay visible without temporary sources`() {
        val sound = CommandType.PLAY_SOUND.newNode()
        val effect = CommandType.APPLY_EFFECT.newNode()

        // 一時変数参照の廃止により、束ね設定タブは直接値のダイアログだけへ進みます。
        assertFalse(CommandSettingsModel.supportsTemporaryValueReference(sound, "soundSetting"))
        assertFalse(CommandSettingsModel.supportsTemporaryValueReference(effect, "effect"))
        assertTrue(CommandSettingsModel.visibleFields(sound).any { it.key == "soundSetting" })
        assertTrue(CommandSettingsModel.visibleFields(sound).any { it.key == "soundTargetPosition" })
        assertTrue(CommandSettingsModel.visibleFields(effect).any { it.key == "level" })
        assertTrue(CommandSettingsModel.visibleFields(effect).any { it.key == "seconds" })
    }

    @Test
    fun `particle parameters keep explicit defaults as configured`() {
        val node = CommandType.PARTICLE.newNode()
        CommandSettingsModel.setParameters(
            node,
            mapOf(
                ParticleSettings.PARAM_DELTA_X to "0.0",
                ParticleSettings.PARAM_DELTA_Y to "0.0",
                ParticleSettings.PARAM_DELTA_Z to "0.0",
                ParticleSettings.PARAM_SPEED to "0.0",
                ParticleSettings.PARAM_COUNT to "1",
            ),
        )
        node.markConfigured("particleParameters")

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "particleParameters"))
        val rendered = CommandSettingsModel.gestureVisibleFields(node)
            .first { it.key == "particleParameters" }.value(node)
        assertEquals(DisplayValue.ParticleParameters("0.0", "0.0", "0.0", "0.0", "1"), rendered)
    }

    @Test
    fun `particle kind change drops stale detail data`() {
        val node = CommandType.PARTICLE.newNode()
        CommandSettingsModel.setParameter(node, ParticleSettings.PARAM_PARTICLE, "DUST")
        CommandSettingsModel.setParameter(node, ParticleSettings.PARAM_DATA, "#ff0000 2")
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "particleData"))

        CommandSettingsModel.setParameter(node, ParticleSettings.PARAM_PARTICLE, "FLAME")

        assertTrue(node.params[ParticleSettings.PARAM_DATA].isNullOrBlank())
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "particleData"))
    }

    @Test
    fun `particle kind change drops incompatible detail data`() {
        val node = CommandType.PARTICLE.newNode()
        CommandSettingsModel.setParameter(node, ParticleSettings.PARAM_PARTICLE, "DUST")
        CommandSettingsModel.setParameter(node, ParticleSettings.PARAM_DATA, "#ff0000 2")

        CommandSettingsModel.setParameter(node, ParticleSettings.PARAM_PARTICLE, "BLOCK")

        assertTrue(node.params[ParticleSettings.PARAM_DATA].isNullOrBlank())
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "particleData"))
    }

    @Test
    fun `dataless particle never reports detail data as configured`() {
        val node = CommandType.PARTICLE.newNode()
        node.params[ParticleSettings.PARAM_DATA] = "#ff0000 2"

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "particleData"))
    }

    @Test
    fun `particle detail input follows the selected kind inside the particle tab`() {
        val node = CommandType.PARTICLE.newNode()
        // 詳細チE�Eタ欁E�E独立タブを持たず、「パーチE��クル」タブ�Eダイアログに統合します、E
        assertFalse(CommandSettingsModel.visibleFields(node).any { it.key == "particleData" })
        assertTrue(CommandSettingsModel.visibleFields(node).any { it.key == "particle" })

        CommandSettingsModel.setParameter(node, ParticleSettings.PARAM_PARTICLE, "DUST")

        assertFalse(CommandSettingsModel.visibleFields(node).any { it.key == "particleData" })
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "particle"))
        assertTrue(CommandSettingsModel.gestureVisibleFields(node).any { it.key == "particle" })
    }

    @Test
    fun `nearby kinds expose only distance filters`() {
        // 周囲系の詳細設定項目は最大距離だけとします。
        assertEquals(
            listOf("distance"),
            CommandSettingsModel.visibleTargetFilterIds(TargetKind.NEARBY_PLAYERS),
        )
        assertEquals(
            listOf("distance"),
            CommandSettingsModel.visibleTargetFilterIds(TargetKind.NEARBY_ENTITIES),
        )
        // 非周囲系は従来どおり種別適用に従います。
        val nearest = CommandSettingsModel.visibleTargetFilterIds(TargetKind.NEAREST_PLAYER)
        assertTrue(nearest.containsAll(listOf("distance", "sort", "tag", "name")))
        // 単一参照では上限数を除外します。
        assertFalse(CommandSettingsModel.visibleTargetFilterIds(TargetKind.NEAREST_PLAYER, singleOnly = true).contains("limit"))
        assertTrue(CommandSettingsModel.visibleTargetFilterIds(TargetKind.NEAREST_PLAYER, singleOnly = false).contains("limit"))
    }

    @Test
    fun `fresh single choice fields are unconfigured until explicitly set`() {
        // 初期時点では全項目を未設定とします。既定値との一致だけでは設定済みにしません。
        val action = CommandType.ENTITY_ACTION.newNode()
        assertFalse(CommandSettingsModel.isFieldConfigured(action, "action"))
        CommandSettingsModel.setParameter(action, "action", "ride")
        assertTrue(CommandSettingsModel.isFieldConfigured(action, "action"))

        // issue #27: 条件の単一正本は subject 欄です。旧 kind 欄は使用しません。
        val condition = CommandType.CONDITION.newNode()
        assertFalse(CommandSettingsModel.isFieldConfigured(condition, "subject"))
        condition.setConditionSubject(ConditionSubject.OTHER_ENTITY)
        assertTrue(CommandSettingsModel.isFieldConfigured(condition, "subject"))

        val display = CommandType.DISPLAY_TEXT.newNode()
        assertFalse(CommandSettingsModel.isFieldConfigured(display, "mode"))
        CommandSettingsModel.setParameter(display, "mode", "tellraw")
        assertTrue(CommandSettingsModel.isFieldConfigured(display, "mode"))

        val block = CommandType.BLOCK_OPERATION.newNode()
        assertFalse(CommandSettingsModel.isFieldConfigured(block, "operation"))
        CommandSettingsModel.setParameter(block, "operation", "setblock")
        assertTrue(CommandSettingsModel.isFieldConfigured(block, "operation"))

        // 空値の保存は未設定へ戻します。
        CommandSettingsModel.setParameter(action, "action", "")
        assertFalse(CommandSettingsModel.isFieldConfigured(action, "action"))
    }

    @Test
    fun `incomplete specs are not complete for display`() {
        // 座標は必須値が揃うまで未完了とします。
        assertFalse(CommandSettingsModel.isPositionSpecComplete(PositionSpec(PositionKind.COORDINATES)))
        assertTrue(
            CommandSettingsModel.isPositionSpecComplete(PositionSpec(PositionKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0)),
        )
        assertFalse(CommandSettingsModel.isFacingSpecComplete(FacingSpec(FacingKind.ROTATION)))
        assertTrue(
            CommandSettingsModel.isFacingSpecComplete(FacingSpec(FacingKind.ROTATION, yaw = 0f, pitch = 0f)),
        )
        // 一時変数は変数名がなければ未完了とします。
        assertFalse(CommandSettingsModel.isPositionSpecComplete(PositionSpec(PositionKind.TEMPORARY)))
        assertFalse(CommandSettingsModel.isFacingSpecComplete(FacingSpec(FacingKind.TEMPORARY)))
        // 注視・複写は参照先の設定済みで判定します。
        assertFalse(CommandSettingsModel.isFacingSpecComplete(FacingSpec(FacingKind.TARGET)))
        assertTrue(
            CommandSettingsModel.isFacingSpecComplete(
                FacingSpec(FacingKind.TARGET, target = TargetSpec(TargetKind.NEAREST_PLAYER)),
            ),
        )
    }

    @Test
    fun `summon type change drops unsupported summon data`() {
        // issue #10: 種類変更時に新しい種類で利用できない個別設定を残しません。
        // EntityTypeの解決はサーバー側で行い、ここでは除去規則を固定します。
        val node = CommandType.SUMMON_ENTITY.newNode().apply {
            params["summonEntity"] = "minecraft:rabbit"
            params[EntityDataSchema.summonDataKey("silent")] = "true"
            params[EntityDataSchema.summonDataKey(EntityDataSchema.PROPERTY_RABBIT_TYPE)] = "brown"
            markConfigured(EntityDataSchema.summonDataKey(EntityDataSchema.PROPERTY_RABBIT_TYPE))
        }
        CommandSettingsModel.pruneSummonData(node, org.bukkit.entity.EntityType.PLAYER)
        // 共通項目は残し、型固有項目だけを除去します。
        assertEquals("true", node.string(EntityDataSchema.summonDataKey("silent")))
        assertFalse(node.params.containsKey(EntityDataSchema.summonDataKey(EntityDataSchema.PROPERTY_RABBIT_TYPE)))
        assertFalse(
            node.configuredFields?.contains(EntityDataSchema.summonDataKey(EntityDataSchema.PROPERTY_RABBIT_TYPE)) == true,
        )

        // 種類設定自体は通常の保存経路で行います。
        CommandSettingsModel.setSummonEntity(node, "minecraft:player")
        assertEquals("minecraft:player", node.string("summonEntity"))
    }

    @Test
    fun `structure place and group pivot are embedded target roles`() {
        // issue #34-1: 建造物設置位置・群pivotは位置設定木の内部に対象を持ちます。
        // 内包対象として扱わないと、対象詳細の編集が通常対象へ流れます。
        assertTrue(CommandSettingsModel.isEmbeddedTargetRole(CommandSettingRole.STRUCTURE_PLACE_POSITION))
        assertTrue(CommandSettingsModel.isEmbeddedTargetRole(CommandSettingRole.GROUP_PIVOT))
        // 通常対象の役割は内包扱いにしません。
        assertFalse(CommandSettingsModel.isEmbeddedTargetRole(CommandSettingRole.NODE_TARGET))
        assertFalse(CommandSettingsModel.isEmbeddedTargetRole(CommandSettingRole.DESTINATION))
        assertFalse(CommandSettingsModel.isEmbeddedTargetRole(null))
    }

    @Test
    fun `plain target access rejects embedded roles instead of falling back`() {
        // issue #34-1: 未知または用途不適合の役割を通常対象へ流さず明示的に拒否します。
        val node = CommandType.STRUCTURE_PLACE.newNode()
        assertThrows(IllegalStateException::class.java) {
            CommandSettingsModel.targetSpec(node, CommandSettingRole.STRUCTURE_PLACE_POSITION)
        }
        assertThrows(IllegalStateException::class.java) {
            CommandSettingsModel.setTargetSpec(
                node,
                CommandSettingRole.STRUCTURE_PLACE_POSITION,
                TargetSpec(TargetKind.NEAREST_PLAYER),
            )
        }
        val group = CommandType.ENTITY_GROUP.newNode()
        assertThrows(IllegalStateException::class.java) {
            CommandSettingsModel.targetSpec(group, CommandSettingRole.GROUP_PIVOT)
        }
        assertThrows(IllegalStateException::class.java) {
            CommandSettingsModel.setTargetSpec(group, CommandSettingRole.GROUP_PIVOT, TargetSpec(TargetKind.NEAREST_PLAYER))
        }
        // 通常対象の役割は従来どおり読み書きできます。
        CommandSettingsModel.setTargetSpec(group, CommandSettingRole.NODE_TARGET, TargetSpec(TargetKind.NEAREST_PLAYER))
        assertEquals(TargetKind.NEAREST_PLAYER, CommandSettingsModel.targetSpec(group, CommandSettingRole.NODE_TARGET)?.kind)
        assertEquals(TargetKind.NEAREST_PLAYER, CommandSettingsModel.targetSpec(group, null)?.kind)
    }

    @Test
    fun `editing pivot target keeps the group target unchanged`() {
        // issue #34-1: Pivotの対象詳細を編集しても、群の所属対象は変わりません。
        // 編集したフィールド以外が変化しないことの回帰試験です。
        val node = CommandType.ENTITY_GROUP.newNode().apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            groupPivotPositionSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.NEAREST_ENTITY),
            )
        }
        val before = node.targetSpec

        CommandSettingsModel.setEmbeddedTargetSpec(
            node,
            CommandSettingRole.GROUP_PIVOT,
            TargetSpec(TargetKind.NEAREST_PLAYER),
        )

        assertEquals(before, node.targetSpec)
        assertEquals(TargetKind.NEAREST_PLAYER, node.groupPivotPositionSpec?.target?.kind)
        assertEquals(TargetKind.NEAREST_PLAYER, CommandSettingsModel.embeddedTargetSpec(node, CommandSettingRole.GROUP_PIVOT)?.kind)
    }

    @Test
    fun `editing structure place target keeps the node target unchanged`() {
        // issue #34-1: 設置位置の対象詳細を編集しても、通常の対象設定は変わりません。
        val node = CommandType.STRUCTURE_PLACE.newNode().apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            structurePlacePositionSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.NEAREST_ENTITY),
            )
        }
        val before = node.targetSpec

        CommandSettingsModel.setEmbeddedTargetSpec(
            node,
            CommandSettingRole.STRUCTURE_PLACE_POSITION,
            TargetSpec(TargetKind.ALL_PLAYERS),
        )

        assertEquals(before, node.targetSpec)
        assertEquals(TargetKind.ALL_PLAYERS, node.structurePlacePositionSpec?.target?.kind)
    }

    @Test
    fun `embedded target configured state does not pollute the node target`() {
        // issue #34-1: 内包対象と通常対象の明示設定フラグは名前空間を分離します。
        val node = CommandType.ENTITY_GROUP.newNode().apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            groupPivotPositionSpec = PositionSpec(
                PositionKind.TARGET,
                target = TargetSpec(TargetKind.NEAREST_ENTITY, tag = "pivot-tag"),
            )
        }

        assertTrue(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.GROUP_PIVOT))
        assertTrue(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.GROUP_PIVOT, "tag"))
        // 通常対象側にタグ条件はないため、内包側の設定が漏れません。
        assertFalse(CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "tag"))
    }

    @Test
    fun `read only model access never clears a temporary reference`() {
        // issue #33-2: 画面を開く操作（表示・判定のための読込）では、永続値・
        // 一時変数参照・明示設定が変化しません。参照解除は直接値の確定時だけです。
        val node = CommandType.GIVE_ITEM.newNode().apply {
            params["item"] = "minecraft:stone"
            markConfigured("item")
            itemTempRef = "gift"
            markConfigured("itemTempRef")
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
        }
        val paramsBefore = node.params.toMap()
        val configuredBefore = node.configuredFields?.toSet().orEmpty()
        val refBefore = node.itemTempRef
        val targetBefore = node.targetSpec

        CommandSettingsModel.visibleFields(node)
        CommandSettingsModel.gestureVisibleFields(node)
        CommandSettingsModel.descriptor(node, "item")
        CommandSettingsModel.isFieldConfigured(node, "item")
        CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET)
        CommandSettingsModel.isTargetFilterConfigured(node, CommandSettingRole.NODE_TARGET, "kind")
        CommandSettingsModel.temporaryValueSource(node, "item")
        CommandSettingsModel.temporaryValueReference(node, "item")
        CommandSettingsModel.supportsTemporaryValueReference(node, "item")
        CommandSettingsModel.targetSpec(node, CommandSettingRole.NODE_TARGET)
        CommandSettingsModel.incompleteWarningKey(node, "item")
        CommandSettingsModel.visibleAttentionFieldKey(node, "item")

        assertEquals(paramsBefore, node.params.toMap())
        assertEquals(configuredBefore, node.configuredFields?.toSet().orEmpty())
        assertEquals(refBefore, node.itemTempRef)
        assertEquals(targetBefore, node.targetSpec)
    }

    @Test
    fun `structure and group visible fields all have an edit path`() {
        // issue #33-1: 表示される設定項目に操作・保存経路がない接続漏れを検出します。
        // 新設定の追加時は、表示定義・編集経路・警告キーの三者を同時に更新しなければ
        // この試験が失敗します。対象・位置は保存先の役割、テキストは入力仕様を要求します。
        val groupOperations = listOf(
            null, "create", "add", "remove", "delete", "set_pivot", "move", "rotate", "cleanup",
        )
        val cases = buildList {
            add(CommandType.STRUCTURE_SAVE.newNode())
            add(CommandType.STRUCTURE_PLACE.newNode())
            groupOperations.forEach { operation ->
                add(CommandType.ENTITY_GROUP.newNode().apply {
                    if (operation != null) params["operation"] = operation
                })
            }
        }
        val roleEditors = setOf(CommandSettingEditor.TARGET, CommandSettingEditor.POSITION, CommandSettingEditor.FACING)
        for (node in cases) {
            for (field in CommandSettingsModel.visibleFields(node)) {
                val descriptor = CommandSettingsModel.descriptor(node, field.key)
                if (descriptor.editor in roleEditors) {
                    assertTrue(
                        descriptor.role != null,
                        "${node.type}.${field.key} は保存先の役割を持たなければなりません",
                    )
                }
                if (descriptor.editor == CommandSettingEditor.TEXT) {
                    assertTrue(
                        CommandDialogSpecs.field(node, field.key) != null,
                        "${node.type}.${field.key} には汎用入力の仕様が必要です",
                    )
                }
                // 警告キーの未定義は error で失敗し、表示と警告の対応漏れを示します。
                CommandSettingsModel.incompleteWarningKey(node, field.key)
            }
        }
    }

    @Test
    fun `temporary references expose no detail filters`() {
        // issue #34-4: 一時変数参照は詳細条件を使いません。
        // 実行側が適用しない条件をGUIで提示しないことを固定します。
        // issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
        assertEquals(emptyList<String>(), CommandSettingsModel.visibleTargetFilterIds(TargetKind.TEMPORARY))
        assertFalse(CommandSettingsModel.targetFilterApplies(TargetKind.TEMPORARY, "tag"))
        assertFalse(CommandSettingsModel.targetFilterApplies(TargetKind.TEMPORARY, "distance"))
        assertTrue(CommandSettingsModel.targetFilterApplies(TargetKind.TEMPORARY, "kind"))
        // 通常検索の契約は変えません。
        assertTrue(CommandSettingsModel.targetFilterApplies(TargetKind.NEAREST_ENTITY, "tag"))
        assertTrue(CommandSettingsModel.visibleTargetFilterIds(TargetKind.NEAREST_ENTITY).contains("tag"))
    }

    @Test
    fun `saving temporary references prunes detail filters`() {
        // issue #34-4: 保存時に残存条件を除去し、保持した条件を黙って無視しません。
        // issue #35-3: 固定実体（FIXED_ENTITY）は撤去しました。
        val temporary = TargetSpec(TargetKind.TEMPORARY, tempName = "foe", tag = "mark", maximumDistance = 8.0)
        val tempNode = CommandType.ENTITY_DELETE.newNode()
        CommandSettingsModel.setTargetSpec(tempNode, CommandSettingRole.NODE_TARGET, temporary)
        val savedTemporary = requireNotNull(tempNode.targetSpec)
        assertEquals("foe", savedTemporary.tempName)
        assertNull(savedTemporary.tag)
        assertNull(savedTemporary.maximumDistance)

        // 通常検索の条件は除去しません。
        val normal = TargetSpec(TargetKind.NEAREST_ENTITY, tag = "mark", maximumDistance = 8.0)
        val normalNode = CommandType.ENTITY_DELETE.newNode()
        CommandSettingsModel.setTargetSpec(normalNode, CommandSettingRole.NODE_TARGET, normal)
        assertEquals("mark", requireNotNull(normalNode.targetSpec).tag)
        assertEquals(8.0, requireNotNull(normalNode.targetSpec).maximumDistance)
    }
}
