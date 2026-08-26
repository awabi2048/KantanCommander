package me.awabi2048.kantancommander

import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract
import com.awabi2048.ccsystem.api.localization.LocalizationKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KantanLocalizationDependencyTest {
    @Test
    fun `CC-System dependency provides the complete Kantan GUI contract`() {
        assertEquals(
            "89fe19c43c9da55345e1b41f6b4a05c54b28804a5f479f7736664e5235707a3e",
            LocalizationCatalogContract.fingerprint("kantan_commander_clean"),
        )
        assertEquals(
            LocalizationKey.ValueType.TEXT_LIST,
            LocalizationCatalogContract.valueType("kantan_commander_clean.gui.editor.add_description"),
        )
    }
}
