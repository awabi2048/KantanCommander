package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `main hand availability is evaluated from the current inventory state`() {
        // ItemStackの生成はBukkitサーバー初期化を要求するため、判定本体を
        // Materialで直接検証します。Player版はこの同じ関数へ委譲します。
        assertFalse(GestureGuiClickPolicy.hasMainHandItem(Material.AIR))
        assertTrue(GestureGuiClickPolicy.hasMainHandItem(Material.STONE))
    }
}
