package me.awabi2048.kantancommander.gui

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

        assertEquals(DisplayValue.Localized("gui.option.nearest_player"), values["target"])
        assertEquals(DisplayValue.Localized("gui.option.myworld_spawn"), values["destination"])
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
            DisplayValue.Localized("gui.option.dismount"),
            EditorMenuLayout.fields(CommandType.ENTITY_ACTION).single { it.key == "action" }.value(entityAction),
        )
        assertEquals(
            DisplayValue.Localized("gui.option.actionbar"),
            EditorMenuLayout.fields(CommandType.DISPLAY_TEXT).single { it.key == "mode" }.value(displayText),
        )
        assertEquals(
            DisplayValue.Localized("gui.option.shake_positional"),
            EditorMenuLayout.fields(CommandType.CAMERA_SHAKE).single { it.key == "shakeType" }.value(cameraShake),
        )
        assertEquals(
            DisplayValue.Localized("gui.option.equipment_off_hand"),
            EditorMenuLayout.fields(CommandType.EQUIP_ITEM).single { it.key == "slot" }.value(equipment),
        )
        assertEquals(
            DisplayValue.Localized("condition.entity_state"),
            EditorMenuLayout.fields(CommandType.CONDITION).single { it.key == "kind" }.value(condition),
        )
        assertEquals(
            DisplayValue.Localized("gui.option.current_loop_count"),
            EditorMenuLayout.fields(CommandType.VARIABLE).single { it.key == "value" }.value(variable),
        )
        val loopValues = EditorMenuLayout.fields(CommandType.FOR_START).associate { it.key to it.value(loop) }
        assertEquals(DisplayValue.Localized("gui.option.fixed_value"), loopValues["startSource"])
        assertEquals(DisplayValue.Localized("gui.option.temporary_variable"), loopValues["endSource"])
    }
}
