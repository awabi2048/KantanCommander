package me.awabi2048.kantancommander.gui

import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GestureEditorReachPolicyTest {
    @Test
    fun `interaction range uses the actor eye to editor anchor distance`() {
        val anchor = Location(null, 0.0, 0.0, 0.0)

        assertTrue(
            GestureEditorReachPolicy.isWithinInteractionRange(
                Location(null, 0.0, 0.0, 3.0), anchor, 3.0,
            ),
        )
        assertFalse(
            GestureEditorReachPolicy.isWithinInteractionRange(
                Location(null, 0.0, 0.0, 3.01), anchor, 3.0,
            ),
        )
    }

    @Test
    fun `interaction range keeps the same one block minimum as GestureGui`() {
        val anchor = Location(null, 0.0, 0.0, 0.0)

        assertTrue(
            GestureEditorReachPolicy.isWithinInteractionRange(
                Location(null, 0.0, 0.0, 0.9), anchor, 0.1,
            ),
        )
        assertFalse(
            GestureEditorReachPolicy.isWithinInteractionRange(
                Location(null, 0.0, 0.0, 1.01), anchor, 0.1,
            ),
        )
    }
}
