package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CheckboxToggleLabelTest {
    @Test
    fun `fixed label and state are joined with a blank`() {
        assertEquals("上書きして配置する ON", checkboxToggleLabel("上書きして配置する", true))
        assertEquals("上書きして配置する OFF", checkboxToggleLabel("上書きして配置する", false))
    }

    @Test
    fun `missing fixed label falls back to the state only`() {
        assertEquals("ON", checkboxToggleLabel(null, true))
        assertEquals("OFF", checkboxToggleLabel("", false))
        assertEquals("ON", checkboxToggleLabel("  ", true))
    }
}
