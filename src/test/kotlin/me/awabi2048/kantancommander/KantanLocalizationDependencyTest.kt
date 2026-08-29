package me.awabi2048.kantancommander

import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanLocalizationDependencyTest {
    @Test
    fun `CC-System dependency provides the complete Kantan GUI contract`() {
        assertEquals(
            "55d6fec58db7be89ae8dd833ecebf1a1771e0a65bf8d864be9832ae152224dba",
            LocalizationCatalogContract.fingerprint("kantan_commander_clean"),
        )
        assertEquals(
            LocalizationKey.ValueType.TEXT_LIST,
            LocalizationCatalogContract.valueType("kantan_commander_clean.gui.editor.add_description"),
        )
    }
}
