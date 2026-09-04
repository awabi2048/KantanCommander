package me.awabi2048.kantancommander.gui

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HeldBlockSettingPolicyTest {
    @Test
    fun `素手はminecraft airとして保存します`() {
        assertEquals("minecraft:air", HeldBlockSettingPolicy.materialId(Material.AIR))
    }

    @Test
    fun `ブロックは名前空間付き素材IDとして保存します`() {
        assertEquals(
            "minecraft:stone",
            HeldBlockSettingPolicy.materialId("minecraft:stone", isAir = false, isBlock = true),
        )
    }

    @Test
    fun `非ブロックアイテムは未選択として扱います`() {
        assertNull(
            HeldBlockSettingPolicy.materialId("minecraft:stick", isAir = false, isBlock = false),
        )
    }
}
