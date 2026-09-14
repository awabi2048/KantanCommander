package me.awabi2048.kantancommander.model

import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * 効果表示名の解決テストです。
 *
 * 独自の翻訳表は持たず、バニラ翻訳キーへのtranslatableへIDを結合して返します。
 * 未解析IDはID原文を返し、表示を欠落させません。
 */
class EffectDisplayTest {
    @Test
    fun `known effects resolve to vanilla translation keys with ids`() {
        val speed = EffectDisplay.labeledFor("minecraft:speed")
        val translatable = assertInstanceOf(TranslatableComponent::class.java, speed)
        assertEquals("effect.minecraft.speed", translatable.key())
        assertEquals(1, translatable.children().size)
        assertEquals(
            "（minecraft:speed）",
            assertInstanceOf(TextComponent::class.java, translatable.children().single()).content(),
        )
    }

    @Test
    fun `blank and malformed ids fall back to raw text`() {
        assertEquals("", assertInstanceOf(TextComponent::class.java, EffectDisplay.labeledFor("   ")).content())
        assertEquals(
            "not-an-id!!!",
            assertInstanceOf(TextComponent::class.java, EffectDisplay.labeledFor("not-an-id!!!")).content(),
        )
    }
}
