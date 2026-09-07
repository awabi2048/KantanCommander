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
    fun `height converts to vanilla detonation ticks`() {
        // y = 0.02M^2 + 0.03M の逆変換を四捨五入し、移動回数-1を起爆tickにします。
        assertEquals(30, FireworkSettings.detonationTicks(20.0))
        assertEquals(53, FireworkSettings.detonationTicks(60.0))
        assertEquals(69, FireworkSettings.detonationTicks(100.0))
        assertEquals(125, FireworkSettings.detonationTicks(320.0))
        assertEquals(0, FireworkSettings.detonationTicks(0.0))
        // バックストップは到達不能時の保険として余裕を持たせます。
        assertEquals(130, FireworkSettings.backstopLifetime(20.0))
    }

    @Test
    fun `height formula roundtrips within half-tick travel`() {
        listOf(1.0, 20.0, 60.0, 100.0, 320.0).forEach { height ->
            val moves = FireworkSettings.heightToMoves(height)
            val reached = FireworkSettings.movesToHeight(moves)
            // 量子化誤差は半tick移動分に収まります。
            assertTrue(kotlin.math.abs(reached - height) < 2.6, "height=$height reached=$reached")
        }
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

    @Test
    fun `preview site snaps to the editor block center`() {
        val center = FireworkSettings.editorBlockCenter(org.bukkit.Location(null, 10.3, 64.0, -3.7))
        assertEquals(10.5, center.x)
        assertEquals(64.5, center.y)
        assertEquals(-3.5, center.z, 0.000001)

        val negative = FireworkSettings.editorBlockCenter(org.bukkit.Location(null, -1.2, 0.0, -1.2))
        assertEquals(-1.5, negative.x, 0.000001)
        assertEquals(0.5, negative.y, 0.000001)
        assertEquals(-1.5, negative.z, 0.000001)
    }
}
