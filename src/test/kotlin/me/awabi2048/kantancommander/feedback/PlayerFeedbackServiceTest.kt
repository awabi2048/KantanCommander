package me.awabi2048.kantancommander.feedback

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class PlayerFeedbackServiceTest {
    @Test
    fun `first message is sent`() {
        val service = PlayerFeedbackService(clockMillis = { 0L })
        assertTrue(service.shouldSend(UUID.randomUUID(), "権限がありません。"))
    }

    @Test
    fun `same message within throttle window is suppressed`() {
        val service = PlayerFeedbackService(clockMillis = { 1000L })
        val playerId = UUID.randomUUID()

        assertTrue(service.shouldSend(playerId, "権限がありません。"))
        assertFalse(service.shouldSend(playerId, "権限がありません。"))
    }

    @Test
    fun `same message after throttle window is sent again`() {
        var now = 0L
        val service = PlayerFeedbackService(clockMillis = { now })
        val playerId = UUID.randomUUID()

        assertTrue(service.shouldSend(playerId, "権限がありません。"))
        now = 3000L
        assertTrue(service.shouldSend(playerId, "権限がありません。"))
    }

    @Test
    fun `different message bypasses throttle`() {
        val service = PlayerFeedbackService(clockMillis = { 1000L })
        val playerId = UUID.randomUUID()

        assertTrue(service.shouldSend(playerId, "A"))
        assertTrue(service.shouldSend(playerId, "B"))
    }

    @Test
    fun `throttle state is independent per player`() {
        val service = PlayerFeedbackService(clockMillis = { 1000L })

        assertTrue(service.shouldSend(UUID.randomUUID(), "A"))
        assertTrue(service.shouldSend(UUID.randomUUID(), "A"))
    }
}
