package me.awabi2048.kantancommander

import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanLocalizationDependencyTest {
    @Test
    fun `CC-System dependency provides the complete Kantan GUI contract`() {
        assertEquals(
            "853b5c296c64bbbc287f09a9ee24a0736ce134a5cf4fb06ae9336ef440bafc64",
            LocalizationCatalogContract.fingerprint("kantan_commander_clean"),
        )
        assertEquals(
            LocalizationKey.ValueType.TEXT_LIST,
            LocalizationCatalogContract.valueType("kantan_commander_clean.gui.editor.add_description"),
        )
    }
}
