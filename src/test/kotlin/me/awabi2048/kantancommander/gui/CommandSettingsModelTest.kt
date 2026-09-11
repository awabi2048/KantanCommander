package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionKind
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.ParticleSettings
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TemporaryVariableType
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
    fun `fixed target is incomplete until an entity is captured`() {
        val node = CommandType.ENTITY_DELETE.newNode()
        val incomplete = TargetSpec(TargetKind.FIXED_ENTITY)

        CommandSettingsModel.setTargetSpec(node, CommandSettingRole.NODE_TARGET, incomplete)

        assertFalse(CommandSettingsModel.isFieldConfigured(node, "target", CommandSettingRole.NODE_TARGET))
        assertFalse(node.isExplicitlyConfigured("target"))

        CommandSettingsModel.setTargetSpec(
            node,
            CommandSettingRole.NODE_TARGET,
            incomplete.copy(fixedEntityId = UUID.randomUUID()),
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
        node.params["tempType"] = TemporaryVariableType.LOCATION.name
        assertEquals(listOf("name", "tempType", "location"), CommandSettingsModel.visibleFields(node).map { it.key })
        node.params["tempType"] = TemporaryVariableType.SOUND.name
        assertEquals(listOf("name", "tempType", "sound", "soundParameters"), CommandSettingsModel.visibleFields(node).map { it.key })
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
        assertEquals(listOf("target", "action", "slot", "item", "overwrite", "executionPosition"), keys("equip"))
        assertEquals(listOf("target", "action", "tagOperation", "tag", "executionPosition"), keys("tag"))
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
        assertTrue(CommandSettingsModel.targetSupportsDetailedFilters(TargetKind.FIXED_ENTITY))
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
            CommandType.ENTITY_ACTION.newNode().apply { params["action"] = "equip" } to "item",
            CommandType.BLOCK_OPERATION.newNode() to "block",
            CommandType.PLAY_SOUND.newNode() to "sound",
            CommandType.APPLY_EFFECT.newNode() to "effect",
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
        val sound = CommandType.PLAY_SOUND.newNode().apply {
            soundTempRef = "soundValue"
            configuredFields = linkedSetOf("soundTempRef")
        }

        CommandSettingsModel.setParameter(sound, "sound", "minecraft:block.note_block.harp")

        assertNull(sound.soundTempRef)
        assertEquals(CommandValueSource.LITERAL, CommandSettingsModel.temporaryValueSource(sound, "sound"))
        assertTrue(CommandSettingsModel.isFieldConfigured(sound, "sound"))
    }

    @Test
    fun `temporary sound and effect sources keep the bundled setting tab`() {
        val sound = CommandType.PLAY_SOUND.newNode().apply { soundTempRef = "soundValue" }
        val effect = CommandType.APPLY_EFFECT.newNode().apply { effectTempRef = "effectValue" }

        // 束ね設定タブは設定元切替の入口として残り、直接値のダイアログだけが
        // 一時変数参照へ委譲されます。
        assertTrue(CommandSettingsModel.visibleFields(sound).any { it.key == "soundSetting" })
        assertTrue(CommandSettingsModel.visibleFields(sound).any { it.key == "soundTargetPosition" })
        assertFalse(CommandSettingsModel.visibleFields(effect).any { it.key == "level" })
        assertFalse(CommandSettingsModel.visibleFields(effect).any { it.key == "seconds" })
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

        val condition = CommandType.CONDITION.newNode()
        assertFalse(CommandSettingsModel.isFieldConfigured(condition, "kind"))
        CommandSettingsModel.setParameter(condition, "kind", ConditionKind.TARGET_EXISTS.name)
        assertTrue(CommandSettingsModel.isFieldConfigured(condition, "kind"))

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

}
