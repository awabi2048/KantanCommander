package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #26 のコマンド実行位置の回帰テストです。
 *
 * 旧コンテキストの実行者・対象・向き・継承は復活させず、位置だけを
 * コマンド単位設定として保持します。後続ノードへ継承しません。
 */
class ExecutionPositionTest {
    @Test
    fun `deepCopy duplicates execution position`() {
        val node = CommandType.SUMMON_ENTITY.newNode()
        node.executionPositionSpec = PositionSpec(PositionKind.COORDINATES, x = 1.0, y = 2.0, z = 3.0)
        val copied = node.deepCopy()
        assertEquals(node.executionPositionSpec, copied.executionPositionSpec)
        copied.executionPositionSpec = PositionSpec(PositionKind.DISK)
        assertEquals(PositionKind.COORDINATES, node.executionPositionSpec?.kind)
    }

    @Test
    fun `execution position is unset by default`() {
        val node = CommandType.SUMMON_ENTITY.newNode()
        assertNull(node.executionPositionSpec)
        assertFalse(CommandSettingsModel.isFieldConfigured(node, "executionPosition"))
    }

    @Test
    fun `supported commands expose execution position and control commands do not`() {
        listOf(
            CommandType.TELEPORT,
            CommandType.GIVE_ITEM,
            CommandType.ENTITY_ACTION,
            CommandType.DISPLAY_TEXT,
            CommandType.SUMMON_ENTITY,
            CommandType.PLAY_SOUND,
            CommandType.PARTICLE,
            CommandType.FIREWORK_LAUNCH,
            CommandType.APPLY_EFFECT,
            CommandType.CAMERA_SHAKE,
            CommandType.BLOCK_OPERATION,
            CommandType.ENTITY_DELETE,
            CommandType.CONDITION,
            CommandType.DISK_CALL,
        ).forEach { type ->
            assertTrue(CommandSettingsModel.supportsExecutionPosition(type), type.name)
        }
        listOf(
            CommandType.WAIT,
            CommandType.VARIABLE,
            CommandType.TEMP_SET,
            CommandType.FOR_START,
            CommandType.FOR_END,
            CommandType.BREAK,
            CommandType.CONTINUE,
            CommandType.MERGE,
        ).forEach { type ->
            assertFalse(CommandSettingsModel.supportsExecutionPosition(type), type.name)
        }
    }

    @Test
    fun `visible fields contain execution position only for supported commands`() {
        val summon = CommandType.SUMMON_ENTITY.newNode()
        assertTrue(CommandSettingsModel.visibleFields(summon).any { it.key == "executionPosition" })
        val wait = CommandType.WAIT.newNode()
        assertFalse(CommandSettingsModel.visibleFields(wait).any { it.key == "executionPosition" })
    }

    @Test
    fun `position role roundtrips through the shared boundary`() {
        val node = CommandType.PARTICLE.newNode()
        val role = CommandSettingRole.EXECUTION_POSITION
        val descriptor = CommandSettingsModel.descriptor(node, "executionPosition")
        assertEquals(CommandSettingEditor.POSITION, descriptor.editor)
        assertEquals(role, descriptor.role)

        CommandSettingsModel.setPositionSpec(
            node,
            role,
            PositionSpec(PositionKind.COORDINATES, x = 10.0, y = 64.0, z = -3.0),
        )
        assertEquals(PositionKind.COORDINATES, CommandSettingsModel.positionKind(node, role))
        assertEquals(PositionSpec(PositionKind.COORDINATES, x = 10.0, y = 64.0, z = -3.0), CommandSettingsModel.positionSpec(node, role))
        assertTrue(CommandSettingsModel.isFieldConfigured(node, "executionPosition"))
    }

    @Test
    fun `execution position does not leak into sibling specs`() {
        val node = CommandType.SUMMON_ENTITY.newNode()
        CommandSettingsModel.setPositionSpec(node, CommandSettingRole.EXECUTION_POSITION, PositionSpec(PositionKind.DISK))
        assertNull(node.summonPositionSpec)
        CommandSettingsModel.setPositionSpec(node, CommandSettingRole.SUMMON_POSITION, PositionSpec(PositionKind.DISK))
        assertEquals(PositionKind.DISK, node.executionPositionSpec?.kind)
        assertEquals(PositionKind.DISK, node.summonPositionSpec?.kind)
    }
}
