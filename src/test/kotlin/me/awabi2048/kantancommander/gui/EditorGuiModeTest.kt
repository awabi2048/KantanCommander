package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EditorGuiModeTest {
    @Test
    fun `Java Edition selects Gesture GUI and Bedrock selects Inventory GUI`() {
        assertEquals(EditorGuiMode.GESTURE, EditorGuiModeResolver.resolve(isBedrock = false))
        assertEquals(EditorGuiMode.INVENTORY, EditorGuiModeResolver.resolve(isBedrock = true))
    }
}
