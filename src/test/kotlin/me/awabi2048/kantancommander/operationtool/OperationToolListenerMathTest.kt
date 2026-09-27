package me.awabi2048.kantancommander.operationtool

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperationToolListenerMathTest {
    @Test
    fun `adjacent slot moves and wraparound are scroll steps`() {
        assertTrue(OperationToolListener.isScrollStep(3, 4))
        assertTrue(OperationToolListener.isScrollStep(4, 3))
        assertTrue(OperationToolListener.isScrollStep(0, 8))
        assertTrue(OperationToolListener.isScrollStep(8, 0))
    }

    @Test
    fun `number key jumps and same slots are not scroll steps`() {
        assertFalse(OperationToolListener.isScrollStep(0, 5))
        assertFalse(OperationToolListener.isScrollStep(2, 7))
        assertFalse(OperationToolListener.isScrollStep(4, 4))
    }

    @Test
    fun `scroll forward follows the slot increasing direction including wraparound`() {
        assertTrue(OperationToolListener.isScrollForward(3, 4))
        assertTrue(OperationToolListener.isScrollForward(8, 0))
        assertFalse(OperationToolListener.isScrollForward(4, 3))
        assertFalse(OperationToolListener.isScrollForward(0, 8))
    }
}
