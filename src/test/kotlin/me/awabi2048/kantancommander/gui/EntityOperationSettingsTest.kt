package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #32 の設定モデル（操作分離）のテストです。
 *
 * 操作ごとの表示許可リスト、編集経路、警告キーの固定、実行位置の対応を固定します。
 */
class EntityOperationSettingsTest {
    private fun operationNode(operation: String): CommandNode =
        CommandType.ENTITY_OPERATION.newNode().apply { params["operation"] = operation }

    private fun keys(node: CommandNode): List<String> =
        CommandSettingsModel.visibleFields(node).map { it.key }

    @Test
    fun `entity operation fields follow the selected operation allowlist`() {
        assertEquals(
            listOf("target", "operation", "tagOperation", "tag", "executionPosition"),
            keys(operationNode("tag")),
        )
        val base = operationNode("attribute").apply {
            params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_BASE_VALUE
        }
        assertEquals(
            listOf(
                "target", "operation",
                EntityAttributeSupport.PARAM_ATTRIBUTE,
                EntityAttributeSupport.PARAM_OPERATION,
                EntityAttributeSupport.PARAM_AMOUNT,
                "executionPosition",
            ),
            keys(base),
        )
        val add = operationNode("attribute").apply {
            params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_MODIFIER_ADD
        }
        assertEquals(
            listOf(
                "target", "operation",
                EntityAttributeSupport.PARAM_ATTRIBUTE,
                EntityAttributeSupport.PARAM_OPERATION,
                EntityAttributeSupport.PARAM_MODIFIER_KEY,
                EntityAttributeSupport.PARAM_AMOUNT,
                EntityAttributeSupport.PARAM_MODIFIER_OPERATION,
                "executionPosition",
            ),
            keys(add),
        )
        val remove = operationNode("attribute").apply {
            params[EntityAttributeSupport.PARAM_OPERATION] = EntityAttributeSupport.OPERATION_MODIFIER_REMOVE
        }
        assertEquals(
            listOf(
                "target", "operation",
                EntityAttributeSupport.PARAM_ATTRIBUTE,
                EntityAttributeSupport.PARAM_OPERATION,
                EntityAttributeSupport.PARAM_MODIFIER_KEY,
                "executionPosition",
            ),
            keys(remove),
        )
        assertEquals(
            listOf(
                "target", "operation",
                EntityDataSchema.PARAM_PROPERTY,
                EntityDataSchema.PARAM_VALUE,
                "executionPosition",
            ),
            keys(operationNode("data")),
        )
    }

    @Test
    fun `entity operation supports execution position`() {
        assertTrue(CommandSettingsModel.supportsExecutionPosition(CommandType.ENTITY_OPERATION))
    }

    @Test
    fun `descriptor maps entity operation fields to shared editors`() {
        val node = operationNode("attribute")
        assertEquals(
            CommandSettingEditor.ENTITY_OPERATION,
            CommandSettingsModel.descriptor(node, "operation").editor,
        )
        assertEquals(
            CommandSettingEditor.ENTITY_ATTRIBUTE_OPERATION,
            CommandSettingsModel.descriptor(node, EntityAttributeSupport.PARAM_OPERATION).editor,
        )
        assertEquals(
            CommandSettingEditor.ENTITY_MODIFIER_OPERATION,
            CommandSettingsModel.descriptor(node, EntityAttributeSupport.PARAM_MODIFIER_OPERATION).editor,
        )
        assertEquals(
            CommandSettingEditor.ENTITY_PROPERTY,
            CommandSettingsModel.descriptor(node, EntityDataSchema.PARAM_PROPERTY).editor,
        )
        assertEquals(
            CommandSettingEditor.ENTITY_PROPERTY_VALUE,
            CommandSettingsModel.descriptor(
                node.apply { params[EntityDataSchema.PARAM_PROPERTY] = "gravity" },
                EntityDataSchema.PARAM_VALUE,
            ).editor,
        )
        assertEquals(
            CommandSettingEditor.TEXT,
            CommandSettingsModel.descriptor(
                node.apply { params[EntityDataSchema.PARAM_PROPERTY] = "custom_name" },
                EntityDataSchema.PARAM_VALUE,
            ).editor,
        )
    }

    @Test
    fun `incomplete warnings use fixed keys for entity operation tabs`() {
        val node = operationNode("data")
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.target",
            CommandSettingsModel.incompleteWarningKey(node, "target").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.operation",
            CommandSettingsModel.incompleteWarningKey(node, "operation").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.attribute",
            CommandSettingsModel.incompleteWarningKey(node, EntityAttributeSupport.PARAM_ATTRIBUTE).id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.modifier_key",
            CommandSettingsModel.incompleteWarningKey(node, EntityAttributeSupport.PARAM_MODIFIER_KEY).id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.modifier_amount",
            CommandSettingsModel.incompleteWarningKey(node, EntityAttributeSupport.PARAM_AMOUNT).id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.property",
            CommandSettingsModel.incompleteWarningKey(node, EntityDataSchema.PARAM_PROPERTY).id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.property_value",
            CommandSettingsModel.incompleteWarningKey(node, EntityDataSchema.PARAM_VALUE).id,
        )
        val motion = CommandType.ENTITY_ACTION.newNode().apply { params["action"] = "motion" }
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.motion",
            CommandSettingsModel.incompleteWarningKey(motion, "motionX").id,
        )
    }

    @Test
    fun `property value presentation follows the property kind`() {
        val node = operationNode("data")
        fun presentation(property: String): GesturePresentation {
            node.params[EntityDataSchema.PARAM_PROPERTY] = property
            val descriptor = CommandSettingsModel.descriptor(node, EntityDataSchema.PARAM_VALUE)
            return SettingSpec.presentationFor(node, EntityDataSchema.PARAM_VALUE, descriptor)
        }

        assertEquals(GesturePresentation.CHECKBOX, presentation("gravity"))
        assertEquals(GesturePresentation.CHOICE, presentation("rabbit_type"))
        assertEquals(GesturePresentation.INPUT_DIALOG, presentation("custom_name"))
    }
}
