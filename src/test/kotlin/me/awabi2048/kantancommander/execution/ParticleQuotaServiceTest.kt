package me.awabi2048.kantancommander.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ParticleQuotaServiceTest {
    @Test
    fun `configured count itself fills the world budget and expires after one second`() {
        val service = ParticleQuotaService(limitProvider = { 600 })
        val worldId = UUID.randomUUID()

        assertTrue(service.tryAcquire(worldId, 400, 0L))
        assertTrue(service.tryAcquire(worldId, 200, 500_000_000L))
        assertFalse(service.tryAcquire(worldId, 1, 500_000_000L))

        // 境界時刻では最初の400個だけが期限切れになり、直近の200個は残ります。
        assertTrue(service.tryAcquire(worldId, 1, 1_000_000_000L))
        assertEquals(201, service.usage(worldId, 1_000_000_000L))
    }

    @Test
    fun `world windows are isolated and one request over the limit is rejected`() {
        val service = ParticleQuotaService(limitProvider = { 600 })
        val firstWorld = UUID.randomUUID()
        val secondWorld = UUID.randomUUID()

        assertFalse(service.tryAcquire(firstWorld, 601, 0L))
        assertTrue(service.tryAcquire(firstWorld, 600, 0L))
        assertTrue(service.tryAcquire(secondWorld, 600, 0L))
        assertFalse(service.tryAcquire(firstWorld, 1, 1L))
        assertFalse(service.tryAcquire(secondWorld, 1, 1L))
    }
}
