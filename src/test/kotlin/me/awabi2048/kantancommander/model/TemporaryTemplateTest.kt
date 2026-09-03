package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemporaryTemplateTest {
    @Test
    fun `new reference syntax is extracted and interpolated`() {
        val raw = "value=%{Player_Name}%"

        assertEquals(setOf("Player_Name"), TemporaryTemplate.references(raw))
        assertTrue(TemporaryTemplate.isSingleReference("%{value}%"))
        assertEquals("value=ready", TemporaryTemplate.interpolateText(raw) { "ready" })
    }

    @Test
    fun `ordinary percent characters remain literal without an escape syntax`() {
        val raw = "progress: 50% (%old%)"

        assertFalse(TemporaryTemplate.hasMalformedReference(raw))
        assertEquals(raw, TemporaryTemplate.interpolateText(raw) { "unused" })
        assertFalse(TemporaryTemplate.isSingleReference(raw))
    }

    @Test
    fun `invalid braced references are rejected`() {
        assertTrue(TemporaryTemplate.hasMalformedReference("%{missing"))
        assertTrue(TemporaryTemplate.hasMalformedReference("%{bad name}%"))
        assertNull(TemporaryTemplate.interpolateText("%{missing", { "unused" }))
        assertFalse(TemporaryTemplate.hasMalformedReference("%old%"))
    }

    @Test
    fun `multiple references require every value`() {
        val raw = "%{first}%/%{second}%"

        assertEquals("A/B", TemporaryTemplate.interpolateText(raw) { name ->
            if (name == "first") "A" else if (name == "second") "B" else null
        })
        assertNull(TemporaryTemplate.interpolateText(raw) { name ->
            if (name == "first") "A" else null
        })
    }
}
