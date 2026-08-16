package me.awabi2048.kantancommander.execution

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SummonLimitPolicyTest {
    @Test
    fun `both world and server limits must have room`() {
        assertTrue(SummonLimitPolicy.canSummon(255, 2047))
        assertFalse(SummonLimitPolicy.canSummon(256, 1000))
        assertFalse(SummonLimitPolicy.canSummon(10, 2048))
    }
}
