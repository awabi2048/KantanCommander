package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.BlockOperationMode
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.PositionKind
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandSettingAvailabilityPolicyTest {
    @Test
    fun `setblockの制御ブロック位置は選択できません`() {
        val node = blockOperationNode(BlockOperationMode.SETBLOCK)

        assertFalse(
            CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(
                node,
                CommandSettingRole.BLOCK_POSITION,
                PositionKind.DISK,
            )
        )
    }

    @Test
    fun `fillの制御ブロック位置は選択できます`() {
        val node = blockOperationNode(BlockOperationMode.FILL)

        assertTrue(
            CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(
                node,
                CommandSettingRole.BLOCK_POSITION,
                PositionKind.DISK,
            )
        )
    }

    @Test
    fun `setblockでも別の位置役割は無効化しません`() {
        val node = blockOperationNode(BlockOperationMode.SETBLOCK)

        assertTrue(
            CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(
                node,
                CommandSettingRole.BLOCK_FROM,
                PositionKind.DISK,
            )
        )
    }

    @Test
    fun `setblock以外のノードと位置種別は無効化しません`() {
        val blockNode = blockOperationNode(BlockOperationMode.SETBLOCK)
        val otherNode = CommandNode(type = CommandType.TELEPORT)

        assertTrue(
            CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(
                blockNode,
                CommandSettingRole.BLOCK_POSITION,
                PositionKind.COORDINATES,
            )
        )
        assertTrue(
            CommandSettingAvailabilityPolicy.isPositionChoiceEnabled(
                otherNode,
                CommandSettingRole.BLOCK_POSITION,
                PositionKind.DISK,
            )
        )
    }

    private fun blockOperationNode(mode: BlockOperationMode) = CommandNode(
        type = CommandType.BLOCK_OPERATION,
        params = linkedMapOf("operation" to mode.value),
    )
}
