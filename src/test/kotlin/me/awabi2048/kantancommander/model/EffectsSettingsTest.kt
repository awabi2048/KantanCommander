package me.awabi2048.kantancommander.model

import org.bukkit.SoundCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EffectsSettingsTest {
    @Test
    fun `stop category resolves case-insensitively and blank means all`() {
        assertEquals(SoundCategory.MASTER, SoundSettings.category("MASTER"))
        assertEquals(SoundCategory.MUSIC, SoundSettings.category("music"))
        assertNull(SoundSettings.category(""))
        assertNull(SoundSettings.category(null))
        assertNull(SoundSettings.category("unknown"))
        assertTrue(SoundSettings.isCategoryValue(""))
        assertTrue(SoundSettings.isCategoryValue("voice"))
        assertFalse(SoundSettings.isCategoryValue("unknown"))
    }

    @Test
    fun `burst height accepts the agreed range`() {
        assertTrue(FireworkSettings.isBurstHeight("0"))
        assertTrue(FireworkSettings.isBurstHeight("20.0"))
        assertTrue(FireworkSettings.isBurstHeight("320"))
        assertFalse(FireworkSettings.isBurstHeight("320.1"))
        assertFalse(FireworkSettings.isBurstHeight("-1"))
        assertFalse(FireworkSettings.isBurstHeight("abc"))
    }

    @Test
    fun `height converts to power tiers and detonation ticks`() {
        assertEquals(1, FireworkSettings.heightToPower(0.0))
        assertEquals(1, FireworkSettings.heightToPower(20.0))
        assertEquals(2, FireworkSettings.heightToPower(21.0))
        assertEquals(2, FireworkSettings.heightToPower(60.0))
        assertEquals(3, FireworkSettings.heightToPower(61.0))
        assertEquals(1, FireworkSettings.heightToTicks(0.0))
        assertEquals(40, FireworkSettings.heightToTicks(20.0))
        assertEquals(640, FireworkSettings.heightToTicks(320.0))
    }

    @Test
    fun `firework snbt renders flight and explosions`() {
        val snbt = FireworkSettings.fireworkSnbt(
            2,
            listOf(
                FireworkSettings.FireworkEffectData("small_ball", listOf(0xFF0000), listOf(0x0000FF), trail = true, flicker = false),
            ),
        )
        assertEquals(
            "{Flight:2b,Explosions:[{Shape:\"small_ball\",Colors:[I;16711680],FadeColors:[I;255],HasTrail:1b,HasTwinkle:0b}]}",
            snbt,
        )
    }

    @Test
    fun `bukkit effect names map to vanilla shapes`() {
        assertEquals("small_ball", FireworkSettings.vanillaShape("BALL"))
        assertEquals("large_ball", FireworkSettings.vanillaShape("BALL_LARGE"))
        assertEquals("star", FireworkSettings.vanillaShape("STAR"))
        assertEquals("creeper", FireworkSettings.vanillaShape("CREEPER"))
        assertEquals("burst", FireworkSettings.vanillaShape("BURST"))
    }

    @Test
    fun `firework material accepts only rockets`() {
        assertTrue(FireworkSettings.isFireworkMaterial("minecraft:firework_rocket"))
        assertFalse(FireworkSettings.isFireworkMaterial("minecraft:stone"))
        assertFalse(FireworkSettings.isFireworkMaterial(""))
    }
}
