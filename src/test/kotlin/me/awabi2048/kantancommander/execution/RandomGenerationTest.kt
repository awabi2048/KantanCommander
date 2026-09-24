package me.awabi2048.kantancommander.execution

import kotlin.math.floor
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * issue #3 の乱数生成テストです。
 *
 * 範囲の両端包含、整数範囲→整数・それ以外→実数の型契約、範囲不正の失敗を
 * 固定します。`min..max` のRange入力からの生成を固定シードで検証し、
 * サーバーを要求しません。
 */
class RandomGenerationTest {
    @Test
    fun `integer bounds include both ends and stay integral`() {
        val random = Random(0)
        val seen = mutableSetOf<Double>()
        repeat(500) {
            val value = requireNotNull(requireNotNull(generateRandomRangeValue("1..6", random)).numberValue)
            assertTrue(value in 1.0..6.0, "out of range: $value")
            assertEquals(value, floor(value), "not integral: $value")
            seen += value
        }
        // 固定シードで両端へ到達し、包含を確認します。
        assertTrue(1.0 in seen && 6.0 in seen, "ends not reached: $seen")
    }

    @Test
    fun `real bounds stay within range and produce fractions`() {
        val random = Random(1)
        var fractional = false
        repeat(200) {
            val value = requireNotNull(requireNotNull(generateRandomRangeValue("0.5..1.5", random)).numberValue)
            assertTrue(value in 0.5..1.5, "out of range: $value")
            if (value != floor(value)) fractional = true
        }
        assertTrue(fractional, "no fractional value produced")
    }

    @Test
    fun `invalid ranges fail without a value`() {
        val random = Random(2)
        // 最小値が最大値を超える場合は失敗します。
        assertNull(generateRandomRangeValue("6..1", random))
        // 非数値・非有限値は失敗します。
        assertNull(generateRandomRangeValue("x..6", random))
        assertNull(generateRandomRangeValue("NaN..6", random))
        assertNull(generateRandomRangeValue("1..Infinity", random))
        assertNull(generateRandomRangeValue("1...6", random))
        assertNull(generateRandomRangeValue("1..2..3", random))
    }
}
