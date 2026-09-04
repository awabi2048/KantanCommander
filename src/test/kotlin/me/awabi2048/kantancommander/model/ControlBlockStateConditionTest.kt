package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControlBlockStateConditionTest {
    @Test
    fun `empty selection never becomes a satisfied condition`() {
        assertFalse(ControlBlockStateConditionPolicy.matches(emptySet(), redstoneInput = true))
    }

    @Test
    fun `all selected states must be satisfied`() {
        val selected = setOf(ControlBlockStateKind.REDSTONE_INPUT)

        assertTrue(ControlBlockStateConditionPolicy.matches(selected, redstoneInput = true))
        assertFalse(ControlBlockStateConditionPolicy.matches(selected, redstoneInput = false))
    }

    @Test
    fun `deep copy keeps the selection independent`() {
        val original = CommandNode(type = CommandType.CONDITION).apply {
            controlBlockStates = linkedSetOf(ControlBlockStateKind.REDSTONE_INPUT)
        }
        val copy = CommandGraph(nodes = linkedMapOf(original.id to original)).deepCopy()
            .nodes.getValue(original.id)

        assertEquals(original.selectedControlBlockStates(), copy.selectedControlBlockStates())
        assertFalse(original.controlBlockStates === copy.controlBlockStates)
    }
}
