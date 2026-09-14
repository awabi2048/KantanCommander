package me.awabi2048.kantancommander.model

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * 種別翻訳名の解決テストです。
 *
 * 独自の翻訳表は持たず、バニラ翻訳キーへのtranslatableを返します。
 * 未知IDはID原文を返し、表示を欠落させません。
 * サーバーなし試験ではBukkitのenum直結で解決します。
 */
class EntityTypeDisplayTest {
    @Test
    fun `known types resolve to vanilla translation keys`() {
        val mannequin = EntityTypeDisplay.componentFor("minecraft:mannequin")
        val translatable = assertInstanceOf(TranslatableComponent::class.java, mannequin)
        assertEquals("entity.minecraft.mannequin", translatable.key())

        val pig = EntityTypeDisplay.componentFor("minecraft:pig")
        assertEquals(
            "entity.minecraft.pig",
            assertInstanceOf(TranslatableComponent::class.java, pig).key(),
        )
    }

    @Test
    fun `unknown ids fall back to the raw id without custom translation`() {
        val unknown = EntityTypeDisplay.componentFor("minecraft:not_a_real_mob")
        assertEquals("minecraft:not_a_real_mob", assertInstanceOf(TextComponent::class.java, unknown).content())

        val custom = EntityTypeDisplay.componentFor("mymod:custom_mob")
        assertEquals("mymod:custom_mob", assertInstanceOf(TextComponent::class.java, custom).content())

        assertEquals("", assertInstanceOf(TextComponent::class.java, EntityTypeDisplay.componentFor("   ")).content())
    }

    @Test
    fun `labeled format appends the raw id to the display name`() {
        val pig = EntityTypeDisplay.labeledFor("minecraft:pig")
        val translatable = assertInstanceOf(TranslatableComponent::class.java, pig)
        assertEquals("entity.minecraft.pig", translatable.key())
        assertEquals(1, translatable.children().size)
        assertEquals(
            "（minecraft:pig）",
            assertInstanceOf(TextComponent::class.java, translatable.children().single()).content(),
        )
        assertEquals("", assertInstanceOf(TextComponent::class.java, EntityTypeDisplay.labeledFor("   ")).content())
    }

    @Test
    fun `color is applied to the translatable when specified`() {
        val colored = EntityTypeDisplay.componentFor("minecraft:pig", NamedTextColor.GRAY)
        assertEquals(NamedTextColor.GRAY, assertInstanceOf(TranslatableComponent::class.java, colored).color())
        assertEquals(Component.empty().color(), EntityTypeDisplay.componentFor("minecraft:pig").color())
    }
}
