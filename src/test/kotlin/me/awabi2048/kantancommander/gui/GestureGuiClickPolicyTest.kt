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
    fun `sneak click is treated as the same click as normal click`() {
        // CC-Systemの入力層はスニーク状態でSHIFT_PRIMARYを発行します。
        // 通常クリックとスニーククリックが同じ操作へ正規化されることを保証します。
        assertEquals(
            setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY),
            GestureGuiClickPolicy.CLICK,
        )
        assertTrue(GestureGuiClickPolicy.isPrimaryClick(GestureGuiGesture.PRIMARY))
        assertTrue(GestureGuiClickPolicy.isPrimaryClick(GestureGuiGesture.SHIFT_PRIMARY))
        // 右クリック系とFキーはクリックへ正規化しません。
        assertFalse(GestureGuiClickPolicy.isPrimaryClick(GestureGuiGesture.SECONDARY))
        assertFalse(GestureGuiClickPolicy.isPrimaryClick(GestureGuiGesture.SHIFT_SECONDARY))
        assertFalse(GestureGuiClickPolicy.isPrimaryClick(GestureGuiGesture.SWAP_HAND))
    }

    @Test
    fun `main hand availability is evaluated from the current inventory state`() {
        // ItemStackの生成はBukkitサーバー初期化を要求するため、判定本体を
        // Materialで直接検証します。Player版はこの同じ関数へ委譲します。
        assertFalse(GestureGuiClickPolicy.hasMainHandItem(Material.AIR))
        assertTrue(GestureGuiClickPolicy.hasMainHandItem(Material.STONE))
    }
}
