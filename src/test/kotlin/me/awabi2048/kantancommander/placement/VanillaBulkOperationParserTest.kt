package me.awabi2048.kantancommander.placement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VanillaBulkOperationParserTest {
    @Test
    fun `setblockの単一点を解釈する`() {
        val boxes = VanillaBulkOperationParser.parse("/setblock 10 64 -3 stone", 0, 0, 0)
        assertEquals(listOf(ThreatenedBox(10, 64, -3, 10, 64, -3)), boxes)
    }

    @Test
    fun `fillの範囲を正規化する`() {
        val boxes = VanillaBulkOperationParser.parse("/fill 5 60 5 1 70 1 stone", 0, 0, 0)
        assertEquals(listOf(ThreatenedBox(1, 60, 1, 5, 70, 5)), boxes)
    }

    @Test
    fun `cloneは複写元と貼付け先の両方を返す`() {
        val boxes = VanillaBulkOperationParser.parse("/clone 0 60 0 2 62 2 10 60 10", 0, 0, 0)
        assertEquals(
            listOf(
                ThreatenedBox(0, 60, 0, 2, 62, 2),
                ThreatenedBox(10, 60, 10, 12, 62, 12),
            ),
            boxes,
        )
    }

    @Test
    fun `相対座標を基準位置から解決する`() {
        val boxes = VanillaBulkOperationParser.parse("/setblock ~ ~-1 ~2 stone", 100, 64, 200)
        assertEquals(listOf(ThreatenedBox(100, 63, 202, 100, 63, 202)), boxes)
    }

    @Test
    fun `ローカル座標と引数不足は解釈不能とする`() {
        assertNull(VanillaBulkOperationParser.parse("/setblock ^ ^ ^1 stone", 0, 0, 0))
        assertNull(VanillaBulkOperationParser.parse("/fill 1 2 3 stone", 0, 0, 0))
        assertNull(VanillaBulkOperationParser.parse("/tp @p 0 64 0", 0, 0, 0))
    }

    @Test
    fun `包含判定が範囲内外を区別する`() {
        val box = ThreatenedBox(1, 60, 1, 5, 70, 5)
        assertTrue(box.contains(3, 64, 3))
        assertFalse(box.contains(6, 64, 3))
    }

    @Test
    fun `対象コマンドを判定する`() {
        assertTrue(VanillaBulkOperationParser.isRelevant("/minecraft:fill 0 0 0 1 1 1 stone"))
        assertTrue(VanillaBulkOperationParser.isRelevant("/clone 0 0 0 1 1 1 2 2 2"))
        assertFalse(VanillaBulkOperationParser.isRelevant("//move 1 north"))
        assertFalse(VanillaBulkOperationParser.isRelevant("/tp @p 0 64 0"))
    }
}
