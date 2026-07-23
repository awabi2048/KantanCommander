package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelsTest {
    @Test
    fun newCommandContainsEveryDeclaredDefault() {
        CommandType.entries.forEach { type ->
            val command = type.newCommand()
            assertEquals(type, command.type)
            assertTrue(type.params.all { command.params[it.id] == it.defaultValue })
        }
    }

    @Test
    fun blockModeCyclesLikeCommandBlocks() {
        assertEquals(BlockMode.CHAIN, BlockMode.IMPULSE.next())
        assertEquals(BlockMode.REPEAT, BlockMode.CHAIN.next())
        assertEquals(BlockMode.IMPULSE, BlockMode.REPEAT.next())
    }
}
