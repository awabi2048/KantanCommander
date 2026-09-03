package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.TemporaryVariableType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorMenuLayoutTest {
    @Test
    fun `structured target and destination values are shown in settings`() {
        val node = CommandType.TELEPORT.newNode().apply {
            targetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            destinationSpec = PositionSpec(PositionKind.MYWORLD_SPAWN)
        }
        val values = EditorMenuLayout.fields(CommandType.TELEPORT).associate { it.key to it.value(node) }

        assertEquals(DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NEAREST_PLAYER), values["target"])
        assertEquals(DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MYWORLD_SPAWN), values["destination"])
    }

    @Test
    fun `internal control values declare localized display semantics`() {
        val entityAction = CommandType.ENTITY_ACTION.newNode().apply { params["action"] = "dismount" }
        val displayText = CommandType.DISPLAY_TEXT.newNode().apply { params["mode"] = "actionbar" }
        val cameraShake = CommandType.CAMERA_SHAKE.newNode().apply { params["shakeType"] = "positional" }
        val equipment = CommandType.ENTITY_ACTION.newNode().apply {
            params["action"] = "equip"
            params["slot"] = "OFF_HAND"
        }
        val condition = CommandType.CONDITION.newNode().apply { params["kind"] = "PLAYER_STATE" }
        val variable = CommandType.VARIABLE.newNode().apply { params["value"] = "\${CURRENT_LOOP_COUNT}" }
        val loop = CommandType.FOR_START.newNode().apply { params["count"] = "3" }

        assertEquals(
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DISMOUNT),
            EditorMenuLayout.fields(CommandType.ENTITY_ACTION).single { it.key == "action" }.value(entityAction),
        )
        assertEquals(
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_ACTIONBAR),
            EditorMenuLayout.fields(CommandType.DISPLAY_TEXT).single { it.key == "mode" }.value(displayText),
        )
        assertEquals(
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_SHAKE_POSITIONAL),
            EditorMenuLayout.fields(CommandType.CAMERA_SHAKE).single { it.key == "shakeType" }.value(cameraShake),
        )
        assertEquals(
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_EQUIPMENT_OFF_HAND),
            EditorMenuLayout.fields(CommandType.ENTITY_ACTION).single { it.key == "slot" }.value(equipment),
        )
        assertEquals(
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_PLAYER_STATE),
            EditorMenuLayout.fields(CommandType.CONDITION).single { it.key == "kind" }.value(condition),
        )
        assertEquals(
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT),
            EditorMenuLayout.fields(CommandType.VARIABLE).single { it.key == "value" }.value(variable),
        )
        val loopValues = EditorMenuLayout.fields(CommandType.FOR_START).associate { it.key to it.value(loop) }
        assertEquals(DisplayValue.Literal("3"), loopValues["count"])
    }

    @Test
    fun `shared parameter names use domain-specific explanations`() {
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TELEPORT_TARGET,
            EditorMenuLayout.fields(CommandType.TELEPORT).single { it.key == "target" }.descriptionKey,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_GIVE_TARGET,
            EditorMenuLayout.fields(CommandType.GIVE_ITEM).single { it.key == "target" }.descriptionKey,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DISPLAY_DURATION,
            EditorMenuLayout.fields(CommandType.DISPLAY_TEXT).single { it.key == "staySeconds" }.descriptionKey,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_WAIT_SECONDS,
            EditorMenuLayout.fields(CommandType.WAIT).single { it.key == "seconds" }.descriptionKey,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_EFFECT_SECONDS,
            EditorMenuLayout.fields(CommandType.APPLY_EFFECT).single { it.key == "seconds" }.descriptionKey,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_CAMERA_SHAKE_SECONDS,
            EditorMenuLayout.fields(CommandType.CAMERA_SHAKE).single { it.key == "seconds" }.descriptionKey,
        )
    }

    @Test
    fun `shared parameter names do not leak into semantically different command fields`() {
        val teleportFacing = EditorMenuLayout.fields(CommandType.TELEPORT).single { it.key == "destinationFacing" }
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESTINATION_FACING, teleportFacing.label)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_DESTINATION_FACING, teleportFacing.descriptionKey)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_DESTINATION_FACING, teleportFacing.actionKey)

        val tagOperation = EditorMenuLayout.fields(CommandType.ENTITY_ACTION).single { it.key == "tagOperation" }
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG_OPERATION, tagOperation.label)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TAG_OPERATION, tagOperation.descriptionKey)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TAG_OPERATION, tagOperation.actionKey)

        val summonFields = EditorMenuLayout.fields(CommandType.SUMMON_ENTITY)
        val customName = summonFields.single { it.key == "customName" }
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_DISPLAY_NAME, customName.label)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY_DISPLAY_NAME, customName.descriptionKey)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_ENTITY_DISPLAY_NAME, customName.actionKey)
        val summonPosition = summonFields.single { it.key == "summonPosition" }
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SUMMON_POSITION, summonPosition.label)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SUMMON_POSITION, summonPosition.descriptionKey)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SUMMON_POSITION, summonPosition.actionKey)

        val soundFields = EditorMenuLayout.fields(CommandType.PLAY_SOUND)
        val soundScope = soundFields.single { it.key == "soundScope" }
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_SCOPE, soundScope.label)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_SCOPE, soundScope.descriptionKey)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_SCOPE, soundScope.actionKey)
        val soundPosition = soundFields.single { it.key == "soundPosition" }
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_POSITION, soundPosition.label)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_POSITION, soundPosition.descriptionKey)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_POSITION, soundPosition.actionKey)
    }

    @Test
    fun `entity and sound layouts expose consolidated settings`() {
        val entityFields = EditorMenuLayout.fields(CommandType.ENTITY_ACTION)
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_EQUIPMENT_ITEM,
            entityFields.single { it.key == "item" }.label,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OVERWRITE,
            entityFields.single { it.key == "overwrite" }.label,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_ENTITY_TAG,
            entityFields.single { it.key == "tag" }.descriptionKey,
        )
        assertFalse(entityFields.any { it.key == "itemData" })

        val soundFields = EditorMenuLayout.fields(CommandType.PLAY_SOUND)
        assertTrue(soundFields.any { it.key == "soundParameters" })
        assertFalse(soundFields.any { it.key == "volume" || it.key == "pitch" })
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_PARAMETERS,
            soundFields.single { it.key == "soundParameters" }.descriptionKey,
        )
    }

    @Test
    fun `temporary variable layouts provide presentation for every temporary type`() {
        val expectedKeys = mapOf(
            TemporaryVariableType.NUMBER to setOf("name", "tempType", "value"),
            TemporaryVariableType.STRING to setOf("name", "tempType", "value"),
            TemporaryVariableType.POSITION to setOf("name", "tempType", "x", "y", "z"),
            TemporaryVariableType.ITEM to setOf("name", "tempType", "item"),
            TemporaryVariableType.BLOCK to setOf("name", "tempType", "block"),
            TemporaryVariableType.ENTITY to setOf("name", "tempType", "entityId"),
            TemporaryVariableType.SOUND to setOf("name", "tempType", "sound", "volume", "pitch"),
            TemporaryVariableType.EFFECT to setOf("name", "tempType", "effect", "level", "seconds"),
        )

        expectedKeys.forEach { (type, keys) ->
            val node = CommandType.TEMP_SET.newNode().apply {
                params["tempType"] = type.name
            }
            val fields = EditorMenuLayout.fields(CommandType.TEMP_SET, node)

            assertEquals(keys, fields.map(EditorField::key).toSet(), "temporary type=$type")
        }
    }
}
