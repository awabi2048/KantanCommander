package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.ConditionSubject
import me.awabi2048.kantancommander.model.setConditionSubject
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import me.awabi2048.kantancommander.model.ValueType
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
        val placement = CommandType.PLACE_ITEM.newNode().apply {
            params["placeDestination"] = "EQUIPMENT"
            params["slot"] = "OFF_HAND"
        }
        val condition = CommandType.CONDITION.newNode().apply { setConditionSubject(ConditionSubject.PLAYER) }
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
            EditorMenuLayout.fields(CommandType.PLACE_ITEM).single { it.key == "slot" }.value(placement),
        )
        assertEquals(
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DESTINATION_EQUIPMENT),
            EditorMenuLayout.fields(CommandType.PLACE_ITEM).single { it.key == "placeDestination" }.value(placement),
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

        // タグ一覧はENTITY_OPERATIONの統合選択配下で扱い、単一タグ欄は出しません。
        val tagEntries = EditorMenuLayout.fields(CommandType.ENTITY_OPERATION).single { it.key == "tagEntries" }
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_TAG_ENTRIES, tagEntries.label)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TAGS, tagEntries.descriptionKey)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_TAGS, tagEntries.actionKey)

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
        val soundTargetPosition = soundFields.single { it.key == "soundTargetPosition" }
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_TARGET_POSITION, soundTargetPosition.label)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_POSITION, soundTargetPosition.descriptionKey)
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ACTION_SOUND_POSITION, soundTargetPosition.actionKey)
        val soundSetting = soundFields.single { it.key == "soundSetting" }
        assertEquals(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_SOUND_SETTING, soundSetting.label)
    }

    @Test
    fun `entity and sound layouts expose consolidated settings`() {
        val entityFields = EditorMenuLayout.fields(CommandType.ENTITY_ACTION)
        assertFalse(entityFields.any { it.key == "slot" || it.key == "item" || it.key == "overwrite" })
        // 旧tag欄はENTITY_OPERATIONへ移行済みのため、ENTITY_ACTION側には出しません。
        assertFalse(entityFields.any { it.key == "tag" || it.key == "tagOperation" })
        assertFalse(entityFields.any { it.key == "itemData" })
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_TAGS,
            EditorMenuLayout.fields(CommandType.ENTITY_OPERATION).single { it.key == "tagEntries" }.descriptionKey,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ENTITY_DATA,
            EditorMenuLayout.fields(CommandType.ENTITY_OPERATION).single { it.key == "entityDataPath" }.label,
        )

        val placeFields = EditorMenuLayout.fields(CommandType.PLACE_ITEM)
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PLACE_ITEM,
            placeFields.single { it.key == "item" }.label,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_PLACE_DESTINATION,
            placeFields.single { it.key == "placeDestination" }.label,
        )
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_OVERWRITE,
            placeFields.single { it.key == "overwrite" }.label,
        )
        assertFalse(placeFields.any { it.key == "itemData" })

        val soundFields = EditorMenuLayout.fields(CommandType.PLAY_SOUND)
        assertTrue(soundFields.any { it.key == "soundSetting" })
        assertFalse(soundFields.any { it.key == "volume" || it.key == "pitch" })
        assertEquals(
            KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_DESCRIPTION_SOUND_PARAMETERS,
            soundFields.single { it.key == "soundSetting" }.descriptionKey,
        )
    }

    @Test
    fun `temporary variable layouts provide presentation for every temporary type`() {
        val expectedKeys = mapOf(
            ValueType.NUMBER to setOf("name", "tempType", "value"),
            ValueType.STRING to setOf("name", "tempType", "value"),
            ValueType.BOOLEAN to setOf("name", "tempType", "value"),
            ValueType.LOCATION to setOf("name", "tempType", "location"),
            ValueType.VECTOR to setOf("name", "tempType", "x", "y", "z"),
            ValueType.ITEM to setOf("name", "tempType", "item"),
            ValueType.BLOCK to setOf("name", "tempType", "block"),
            ValueType.ENTITY to setOf("name", "tempType", "entity"),
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
