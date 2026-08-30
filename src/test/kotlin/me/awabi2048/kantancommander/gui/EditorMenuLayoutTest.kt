package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import org.junit.jupiter.api.Assertions.assertEquals
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
        val equipment = CommandType.EQUIP_ITEM.newNode().apply { params["slot"] = "OFF_HAND" }
        val condition = CommandType.CONDITION.newNode().apply { params["kind"] = "ENTITY_STATE" }
        val variable = CommandType.VARIABLE.newNode().apply { params["value"] = "$" + "current_loop_count" }
        val loop = CommandType.FOR_START.newNode().apply {
            params["startSource"] = "FIXED"
            params["endSource"] = "TEMPORARY"
        }

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
            EditorMenuLayout.fields(CommandType.EQUIP_ITEM).single { it.key == "slot" }.value(equipment),
        )
        assertEquals(
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_ENTITY_STATE),
            EditorMenuLayout.fields(CommandType.CONDITION).single { it.key == "kind" }.value(condition),
        )
        assertEquals(
            DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CURRENT_LOOP_COUNT),
            EditorMenuLayout.fields(CommandType.VARIABLE).single { it.key == "value" }.value(variable),
        )
        val loopValues = EditorMenuLayout.fields(CommandType.FOR_START).associate { it.key to it.value(loop) }
        assertEquals(DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_VALUE), loopValues["startSource"])
        assertEquals(DisplayValue.Localized(KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEMPORARY_VARIABLE), loopValues["endSource"])
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
}
