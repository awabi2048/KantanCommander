package me.awabi2048.kantancommander.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class KantanCommandPermissionsTest {
    @Test
    fun `root and public subcommands use independent permission nodes`() {
        assertEquals(KantanCommandPermissions.ROOT, KantanCommandPermissions.forSubcommand(null))
        assertEquals(KantanCommandPermissions.HISTORY, KantanCommandPermissions.forSubcommand("history"))
        assertEquals(KantanCommandPermissions.LIBRARY, KantanCommandPermissions.forSubcommand("library"))
        assertNotEquals(KantanCommandPermissions.HISTORY, KantanCommandPermissions.LIBRARY)
        assertNotEquals(KantanCommandPermissions.ROOT, KantanCommandPermissions.HISTORY)
    }

    @Test
    fun `management subcommands retain their own permission nodes`() {
        assertEquals(KantanCommandPermissions.PLACED, KantanCommandPermissions.forSubcommand("placed"))
        assertEquals(KantanCommandPermissions.RELOAD, KantanCommandPermissions.forSubcommand("reload"))
        assertEquals(KantanCommandPermissions.HELP, KantanCommandPermissions.forSubcommand("help"))
    }

    @Test
    fun `unknown subcommands do not inherit a valid command permission`() {
        assertEquals(null, KantanCommandPermissions.forSubcommand("unknown"))
        assertEquals(null, KantanCommandPermissions.forSubcommand("gesture"))
    }
}
