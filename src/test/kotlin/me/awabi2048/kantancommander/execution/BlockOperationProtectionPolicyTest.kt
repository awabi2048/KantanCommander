package me.awabi2048.kantancommander.execution

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlockOperationProtectionPolicyTest {
    @Test
    fun `保護対象がないfillは実行可能です`() {
        assertFalse(
            BlockOperationProtectionPolicy.hasProtectedBlock(
                minX = 0,
                maxX = 1,
                minY = 0,
                maxY = 1,
                minZ = 0,
                maxZ = 1,
                isProtected = { false },
            )
        )
    }

    @Test
    fun `領域内に保護対象が1つでもあればfill全体を拒否します`() {
        val protected = BlockOperationCoordinate(1, 0, 1)

        assertTrue(
            BlockOperationProtectionPolicy.hasProtectedBlock(
                minX = 0,
                maxX = 1,
                minY = 0,
                maxY = 1,
                minZ = 0,
                maxZ = 1,
                isProtected = { it == protected },
            )
        )
    }
}
