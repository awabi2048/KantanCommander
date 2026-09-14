package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.EntityAttributeSupport
import me.awabi2048.kantancommander.model.EntityDataPath
import me.awabi2048.kantancommander.model.EntityDataSchema
import me.awabi2048.kantancommander.model.EntityTagEntries
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 統合選択形式の設定モデルのテストです。
 *
 * 設定対象IDごとの表示許可リスト、編集経路、警告キーの固定、実行位置の対応を固定します。
 */
class EntityOperationSettingsTest {
    private fun pathNode(path: String): CommandNode =
        CommandType.ENTITY_OPERATION.newNode().apply { params[EntityDataPath.PARAM_PATH] = path }

    private fun keys(node: CommandNode): List<String> =
        CommandSettingsModel.visibleFields(node).map { it.key }

    @Test
    fun `entity operation fields follow the selected path allowlist`() {
        assertEquals(
            listOf("target", EntityDataPath.PARAM_PATH, "executionPosition"),
            keys(pathNode("")),
        )
        assertEquals(
            listOf(
                "target", EntityDataPath.PARAM_PATH,
                EntityTagEntries.PARAM_ENTRIES, "executionPosition",
            ),
            keys(pathNode(EntityDataPath.TAG)),
        )
        assertEquals(
            listOf(
                "target", EntityDataPath.PARAM_PATH,
                EntityAttributeSupport.PARAM_VALUE, "executionPosition",
            ),
            keys(pathNode(EntityDataPath.attributePath("minecraft:max_health"))),
        )
        // 真偽値は統合選択画面のクリックで直接切り替えるため、値タブを持ちません。
        assertEquals(
            listOf("target", EntityDataPath.PARAM_PATH, "executionPosition"),
            keys(pathNode(EntityDataPath.propertyPath("gravity"))),
        )
        // 文字列・整数は値タブを持ちます。
        assertEquals(
            listOf(
                "target", EntityDataPath.PARAM_PATH,
                EntityDataSchema.PARAM_VALUE, "executionPosition",
            ),
            keys(pathNode(EntityDataPath.propertyPath("custom_name"))),
        )
        assertEquals(
            listOf(
                "target", EntityDataPath.PARAM_PATH,
                EntityDataSchema.PARAM_VALUE, "executionPosition",
            ),
            keys(pathNode(EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_BEE_ANGER))),
        )
    }

    @Test
    fun `entity operation supports execution position`() {
        assertTrue(CommandSettingsModel.supportsExecutionPosition(CommandType.ENTITY_OPERATION))
    }

    @Test
    fun `gesture column tabs stay fixed regardless of selection`() {
        // 詳細（タグ一覧・attribute値・設定値）は子画面で編集するため、
        // ジェスチャーのタブ列は対象・項目・実行位置に固定します。
        // タブ列が選択で増減すると表示と経路がずれるためです。
        // 実行位置タブは固定行へ抽出するため、上限検査の対象外にします。
        fun columnKeys(path: String): List<String> =
            CommandSettingsModel.gestureVisibleFields(pathNode(path))
                .map { it.key }
                .filter { it != "executionPosition" }
        val expected = listOf("target", EntityDataPath.PARAM_PATH)
        assertEquals(expected, columnKeys(""))
        assertEquals(expected, columnKeys(EntityDataPath.TAG))
        assertEquals(expected, columnKeys(EntityDataPath.attributePath("minecraft:max_health")))
        assertEquals(expected, columnKeys(EntityDataPath.propertyPath("gravity")))
        assertEquals(expected, columnKeys(EntityDataPath.propertyPath("rabbit_type")))
        assertEquals(expected, columnKeys(EntityDataPath.propertyPath("custom_name")))
    }

    @Test
    fun `descriptor maps entity operation fields to shared editors`() {
        val node = pathNode(EntityDataPath.TAG)
        assertEquals(
            CommandSettingEditor.ENTITY_DATA_ROOT,
            CommandSettingsModel.descriptor(node, EntityDataPath.PARAM_PATH).editor,
        )
        assertEquals(
            CommandSettingEditor.ENTITY_TAG_LIST,
            CommandSettingsModel.descriptor(node, EntityTagEntries.PARAM_ENTRIES).editor,
        )
        assertEquals(
            CommandSettingEditor.TEXT,
            CommandSettingsModel.descriptor(node, EntityAttributeSupport.PARAM_VALUE).editor,
        )
        assertEquals(
            CommandSettingEditor.ENTITY_PROPERTY_VALUE,
            CommandSettingsModel.descriptor(
                pathNode(EntityDataPath.propertyPath("rabbit_type")),
                EntityDataSchema.PARAM_VALUE,
            ).editor,
        )
        assertEquals(
            CommandSettingEditor.TEXT,
            CommandSettingsModel.descriptor(
                pathNode(EntityDataPath.propertyPath("custom_name")),
                EntityDataSchema.PARAM_VALUE,
            ).editor,
        )
        assertEquals(
            CommandSettingEditor.TEXT,
            CommandSettingsModel.descriptor(
                pathNode(EntityDataPath.propertyPath("gravity")),
                EntityDataSchema.PARAM_VALUE,
            ).editor,
        )
    }

    @Test
    fun `incomplete warnings use fixed keys for entity operation tabs`() {
        val node = pathNode(EntityDataPath.TAG)
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.target",
            CommandSettingsModel.incompleteWarningKey(node, "target").id,
        )
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.entity_data",
            CommandSettingsModel.incompleteWarningKey(node, EntityDataPath.PARAM_PATH).id,
        )
        val motion = CommandType.ENTITY_ACTION.newNode().apply { params["action"] = "motion" }
        assertEquals(
            "kantan_commander_clean.gui.gesture.warning.motion",
            CommandSettingsModel.incompleteWarningKey(motion, "motionX").id,
        )
    }

    @Test
    fun `detail validation keys project onto the selection tab`() {
        val node = pathNode(EntityDataPath.TAG)
        assertEquals(
            EntityDataPath.PARAM_PATH,
            CommandSettingsModel.visibleAttentionFieldKey(node, EntityTagEntries.PARAM_ENTRIES),
        )
        assertEquals(
            EntityDataPath.PARAM_PATH,
            CommandSettingsModel.visibleAttentionFieldKey(node, EntityAttributeSupport.PARAM_VALUE),
        )
        assertEquals(
            EntityDataPath.PARAM_PATH,
            CommandSettingsModel.visibleAttentionFieldKey(node, EntityDataSchema.PARAM_VALUE),
        )
    }

    @Test
    fun `property value presentation follows the property kind`() {
        fun presentation(path: String): GesturePresentation {
            val node = pathNode(path)
            val descriptor = CommandSettingsModel.descriptor(node, EntityDataSchema.PARAM_VALUE)
            return SettingSpec.presentationFor(node, EntityDataSchema.PARAM_VALUE, descriptor)
        }

        assertEquals(GesturePresentation.INPUT_DIALOG, presentation(EntityDataPath.propertyPath("gravity")))
        assertEquals(GesturePresentation.CHOICE, presentation(EntityDataPath.propertyPath("rabbit_type")))
        assertEquals(GesturePresentation.INPUT_DIALOG, presentation(EntityDataPath.propertyPath("custom_name")))
        assertEquals(
            GesturePresentation.INPUT_DIALOG,
            presentation(EntityDataPath.propertyPath(EntityDataSchema.PROPERTY_SLIME_SIZE)),
        )
    }
}
