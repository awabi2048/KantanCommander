package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
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
    fun `gesture display timing stays one tab while showing each current value`() {
        val title = CommandType.DISPLAY_TEXT.newNode().apply { params["mode"] = "title" }

        assertEquals(
            listOf("target", "mode", "text", "subtitle", "staySeconds"),
            CommandSettingsModel.visibleFields(title).map { it.key },
        )
        assertEquals(
            listOf("target", "mode", "text", "subtitle", "staySeconds"),
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
            listOf("target", "mode", "text"),
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
            "kantan_commander_clean.gui.gesture.warning.sound_parameters",
            CommandSettingsModel.incompleteWarningKey(sound, "soundParameters").id,
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
            "kantan_commander_clean.gui.gesture.warning.sound_position",
            CommandSettingsModel.incompleteWarningKey(sound, "soundPosition").id,
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
        assertEquals("soundParameters", CommandSettingsModel.visibleAttentionFieldKey(sound, "volume"))
        assertEquals("soundParameters", CommandSettingsModel.visibleAttentionFieldKey(sound, "pitch"))
        assertEquals("soundScope", CommandSettingsModel.visibleAttentionFieldKey(sound, "soundPosition"))
    }

    @Test
    fun `entity action fields follow the selected operation allowlist`() {
        val node = CommandType.ENTITY_ACTION.newNode()
        fun keys(action: String): List<String> {
            node.params["action"] = action
            return CommandSettingsModel.visibleFields(node).map { it.key }
        }

        assertEquals(listOf("target", "action", "other"), keys("ride"))
        assertEquals(listOf("target", "action"), keys("dismount"))
        assertEquals(listOf("target", "action", "slot", "item", "overwrite"), keys("equip"))
        assertEquals(listOf("target", "action", "tagOperation", "tag"), keys("tag"))
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

        assertEquals(listOf("sound", "soundParameters", "soundScope", "soundPosition"), fields.map { it.key })
        assertFalse(fields.any { it.key == "volume" || it.key == "pitch" })

        node.params["volume"] = "0.5"
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "soundParameters"))
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

}
