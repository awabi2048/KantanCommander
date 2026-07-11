package me.awabi2048.kantancommander.security

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlacementAccessRulesTest {
    private val scriptOwner = UUID.randomUUID()
    private val worldOwner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val stranger = UUID.randomUUID()

    @Test
    fun ownerMemberAndAdminCanManageMwmWorld() {
        assertTrue(PlacementAccessRules.canManage(scriptOwner, scriptOwner, worldOwner, setOf(member), false))
        assertTrue(PlacementAccessRules.canManage(member, scriptOwner, worldOwner, setOf(member), false))
        assertTrue(PlacementAccessRules.canManage(stranger, scriptOwner, worldOwner, emptySet(), true))
    }

    @Test
    fun nonMwmWorldDoesNotGrantMemberAccess() {
        assertFalse(PlacementAccessRules.canManage(member, scriptOwner, null, setOf(member), false))
        assertFalse(PlacementAccessRules.canManage(stranger, scriptOwner, null, emptySet(), false))
    }
}
