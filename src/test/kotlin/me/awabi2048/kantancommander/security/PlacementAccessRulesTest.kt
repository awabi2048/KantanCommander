package me.awabi2048.kantancommander.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlacementAccessRulesTest {
    @Test
    fun `admin bypasses every restriction`() {
        assertEquals(
            PlacementDecision.ALLOW,
            PlacementAccessRules.decide(admin = true, inMyWorld = false, toolEnabled = false, creative = false),
        )
    }

    @Test
    fun `outside MyWorld denies before tool permission`() {
        assertEquals(
            PlacementDecision.OUTSIDE_MY_WORLD,
            PlacementAccessRules.decide(admin = false, inMyWorld = false, toolEnabled = true, creative = true),
        )
    }

    @Test
    fun `missing tool permission denies inside MyWorld`() {
        assertEquals(
            PlacementDecision.NO_TOOL_PERMISSION,
            PlacementAccessRules.decide(admin = false, inMyWorld = true, toolEnabled = false, creative = true),
        )
    }

    @Test
    fun `non-creative denies after tool permission`() {
        assertEquals(
            PlacementDecision.NOT_CREATIVE,
            PlacementAccessRules.decide(admin = false, inMyWorld = true, toolEnabled = true, creative = false),
        )
    }

    @Test
    fun `granted tool permission allows creative inside MyWorld`() {
        assertEquals(
            PlacementDecision.ALLOW,
            PlacementAccessRules.decide(admin = false, inMyWorld = true, toolEnabled = true, creative = true),
        )
    }
}
