package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariableTemplateTest {
    @Test
    fun `interpolation stringifies number and string values`() {
        val values = mapOf(
            "count" to WorldVariableValue(VariableType.NUMBER, numberValue = 12.0),
            "label" to WorldVariableValue(VariableType.STRING, stringValue = "hello"),
        )

        assertEquals("count=12 label=hello", VariableTemplate.interpolate("count=\${count} label=\${label}") { values[it] })
        assertEquals(setOf("count", "label"), VariableTemplate.references("\${count} \${label}"))
    }

    @Test
    fun `malformed or missing references are rejected`() {
        assertTrue(VariableTemplate.hasMalformedReference("value=\${BadName}"))
        assertNull(VariableTemplate.interpolate("value=\${missing}") { null })
        assertNull(VariableTemplate.interpolate("value=\${BadName}") { null })
    }

    @Test
    fun `number formatting removes only integral decimal suffix`() {
        assertEquals("12", VariableTemplate.stringify(WorldVariableValue(VariableType.NUMBER, numberValue = 12.0)))
        assertEquals("1.25", VariableTemplate.stringify(WorldVariableValue(VariableType.NUMBER, numberValue = 1.25)))
    }
}
