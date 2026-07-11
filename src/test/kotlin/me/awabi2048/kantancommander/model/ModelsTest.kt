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
    fun triggerCyclesBetweenSupportedModes() {
        assertEquals(TriggerMode.REDSTONE_EDGE, TriggerMode.REDSTONE_RISING.next())
        assertEquals(TriggerMode.REDSTONE_RISING, TriggerMode.REDSTONE_EDGE.next())
    }
}
