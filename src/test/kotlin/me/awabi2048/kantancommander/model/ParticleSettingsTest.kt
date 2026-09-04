package me.awabi2048.kantancommander.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.bukkit.Particle
import org.bukkit.inventory.ItemStack

class ParticleSettingsTest {
    @Test
    fun `all Bukkit particle data shapes are lowered to current vanilla SNBT`() {
        assertEquals(
            "{color:[0.066667,0.133333,0.2,1]}",
            ParticleSettings.parseData(Particle.ENTITY_EFFECT, "#112233")
                .getOrThrow()
                .vanillaArgument(Particle.ENTITY_EFFECT),
        )
        assertEquals(
            "{color:[0.066667,0.133333,0.2,0.501961]}",
            ParticleSettings.parseData(Particle.FLASH, "#80112233")
                .getOrThrow()
                .vanillaArgument(Particle.FLASH),
        )
        assertEquals(
            "{color:[0.066667,0.133333,0.2,0.501961]}",
            ParticleSettings.parseData(Particle.TINTED_LEAVES, "#80112233")
                .getOrThrow()
                .vanillaArgument(Particle.TINTED_LEAVES),
        )
        assertEquals(
            "{color:[1,0,0],scale:2}",
            ParticleSettings.parseData(Particle.DUST, "#ff0000 2")
                .getOrThrow()
                .vanillaArgument(Particle.DUST),
        )
        assertEquals(
            "{from_color:[1,0,0],scale:0.5,to_color:[0,0,1]}",
            ParticleSettings.parseData(Particle.DUST_COLOR_TRANSITION, "#ff0000 #0000ff 0.5")
                .getOrThrow()
                .vanillaArgument(Particle.DUST_COLOR_TRANSITION),
        )
        assertEquals(
            "{color:[0,1,0],power:1.25}",
            ParticleSettings.parseData(Particle.EFFECT, "#00ff00 1.25")
                .getOrThrow()
                .vanillaArgument(Particle.EFFECT),
        )
        assertEquals(
            "{item:\"minecraft:stone\"}",
            ParticleSettings.ParticleDataSpec.Item("minecraft:stone", TestItemStack())
                .vanillaArgument(Particle.ITEM),
        )
        assertEquals(
            "{block_state:{Name:\"minecraft:redstone_lamp\",Properties:{\"lit\":\"true\"}}}",
            ParticleSettings.ParticleDataSpec.Block("redstone_lamp[lit=true]")
                .vanillaArgument(Particle.BLOCK),
        )
        assertEquals(
            "{power:2}",
            ParticleSettings.parseData(Particle.DRAGON_BREATH, "2")
                .getOrThrow()
                .vanillaArgument(Particle.DRAGON_BREATH),
        )
        assertEquals(
            "{roll:1.25}",
            ParticleSettings.parseData(Particle.SCULK_CHARGE, "1.25")
                .getOrThrow()
                .vanillaArgument(Particle.SCULK_CHARGE),
        )
        assertEquals(
            "{delay:20}",
            ParticleSettings.parseData(Particle.SHRIEK, "20")
                .getOrThrow()
                .vanillaArgument(Particle.SHRIEK),
        )
        assertEquals(
            "{destination:{type:block,pos:[1.5,64,-2.25]},arrival_in_ticks:20}",
            ParticleSettings.parseData(Particle.VIBRATION, "1.5 64 -2.25 20")
                .getOrThrow()
                .vanillaArgument(Particle.VIBRATION),
        )
        assertEquals(
            "{target:[1,64,2],color:[1,0,0,1],duration:10}",
            ParticleSettings.parseData(Particle.TRAIL, "1 64 2 #ff0000 10")
                .getOrThrow()
                .vanillaArgument(Particle.TRAIL),
        )
    }

    /** Bukkit ItemStackのMaterialコンストラクタはRegistryを要求するため、SNBT変換だけを検証します。 */
    private class TestItemStack : ItemStack()
}
