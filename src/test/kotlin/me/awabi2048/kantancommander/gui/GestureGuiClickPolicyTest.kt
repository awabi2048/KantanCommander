package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GestureGuiClickPolicyTest {
    @Test
    fun `main hand setting accepts both normalized click directions`() {
        assertEquals(
            setOf(
                GestureGuiGesture.PRIMARY,
                GestureGuiGesture.SECONDARY,
                GestureGuiGesture.SHIFT_PRIMARY,
                GestureGuiGesture.SHIFT_SECONDARY,
            ),
            GestureGuiClickPolicy.MAIN_HAND,
        )
    }
}
