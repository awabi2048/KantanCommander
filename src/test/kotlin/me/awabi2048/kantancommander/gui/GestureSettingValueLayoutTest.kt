package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GestureSettingValueLayoutTest {
    @Test
    fun `single value keeps the existing anchors`() {
        val layout = GestureSettingValueLayout.calculate(
            rowCount = 1,
            valueAnchorY = 0.27,
            detailAnchorY = 0.17,
        )

        assertEquals(listOf(0.27), layout.rowCentersY)
        assertEquals(0.17, layout.detailCenterY, 1.0e-9)
    }

    @Test
    fun `three values divide the existing value area into equal rows`() {
        val layout = GestureSettingValueLayout.calculate(
            rowCount = 3,
            valueAnchorY = 0.27,
            detailAnchorY = 0.17,
        )

        assertEquals(3, layout.rowCentersY.size)
        assertEquals(0.27, layout.rowCentersY[0], 1.0e-9)
        assertEquals(0.22, layout.rowCentersY[1], 1.0e-9)
        assertEquals(0.17, layout.rowCentersY[2], 1.0e-9)
        assertEquals(0.12, layout.detailCenterY, 1.0e-9)
    }

    @Test
    fun `invalid row count and reversed anchors are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GestureSettingValueLayout.calculate(0, 0.27, 0.17)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GestureSettingValueLayout.calculate(2, 0.17, 0.27)
        }
    }
}
