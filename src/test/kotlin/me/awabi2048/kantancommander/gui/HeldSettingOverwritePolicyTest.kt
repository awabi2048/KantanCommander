package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeldSettingOverwritePolicyTest {
    @Test
    fun `未設定の値は上書き確認を要求しません`() {
        assertFalse(HeldSettingOverwritePolicy.requiresConfirmation("", "  "))
    }

    @Test
    fun `ブロックIDは上書き確認を要求します`() {
        assertTrue(HeldSettingOverwritePolicy.requiresConfirmation("minecraft:stone"))
    }

    @Test
    fun `明示的なminecraft airも設定済みとして扱います`() {
        assertTrue(HeldSettingOverwritePolicy.requiresConfirmation("minecraft:air"))
    }
}
