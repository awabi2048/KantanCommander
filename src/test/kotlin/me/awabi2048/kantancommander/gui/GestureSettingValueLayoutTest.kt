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
    fun `three values add the configured row gap and honor the lower boundary`() {
        val layout = GestureSettingValueLayout.calculate(
            rowCount = 3,
            valueAnchorY = 0.27,
            detailAnchorY = 0.17,
            rowGapRatio = 0.30,
            minimumDetailY = 0.12,
        )

        assertEquals(3, layout.rowCentersY.size)
        assertEquals(0.315, layout.rowCentersY[0], 1.0e-9)
        assertEquals(0.25, layout.rowCentersY[1], 1.0e-9)
        assertEquals(0.185, layout.rowCentersY[2], 1.0e-9)
        assertEquals(0.12, layout.detailCenterY, 1.0e-9)
    }

    @Test
    fun `row gap ratio increases the calculated pitch`() {
        val layout = GestureSettingValueLayout.calculate(
            rowCount = 3,
            valueAnchorY = 0.27,
            detailAnchorY = 0.17,
            rowGapRatio = 0.30,
        )

        assertEquals(0.065, layout.rowCentersY[0] - layout.rowCentersY[1], 1.0e-9)
        assertEquals(0.05 * 0.30, 0.065 - 0.05, 1.0e-9)
    }

    @Test
    fun `invalid row count and reversed anchors are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GestureSettingValueLayout.calculate(0, 0.27, 0.17)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GestureSettingValueLayout.calculate(2, 0.17, 0.27)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GestureSettingValueLayout.calculate(2, 0.27, 0.17, -0.10)
        }
    }
}
