package me.awabi2048.kantancommander

import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanLocalizationDependencyTest {
    @Test
    fun `CC-System dependency provides the complete Kantan GUI contract`() {
        assertEquals(
            "a2610f62d9a16b73c8adb88886221fa3b8e0d81a7d5af000884db2b566806e46",
            LocalizationCatalogContract.fingerprint("kantan_commander_clean"),
        )
        assertEquals(
            LocalizationKey.ValueType.TEXT_LIST,
            LocalizationCatalogContract.valueType("kantan_commander_clean.gui.editor.add_description"),
        )
    }
}
