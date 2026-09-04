package me.awabi2048.kantancommander.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlacementAccessRulesTest {
    @Test
    fun `admin or MyWorld build permission grants management`() {
        assertTrue(PlacementAccessRules.canManage(admin = true, canBuildInWorld = false))
        assertTrue(PlacementAccessRules.canManage(admin = false, canBuildInWorld = true))
    }

    @Test
    fun `ordinary player without MyWorld build permission cannot manage`() {
        assertFalse(PlacementAccessRules.canManage(admin = false, canBuildInWorld = false))
    }
}
