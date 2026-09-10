package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * issue #15 のTTLキャッシュの回帰テストです。
 *
 * 世代管理の対象外だが再計算が高価な検証の間引き条件を、 fake時計で検証します。
 * 容量上限と無効化条件も担保し、キー数の発散を防ぎます。
 */
class TtlCacheTest {
    @Test
    fun `hit within ttl avoids recompute`() {
        var now = 1_000L
        val cache = TtlCache<String, String>(ttlMillis = 1_500L, clock = { now })
        var computations = 0
        fun resolve(): String = cache.getOrCompute("key") {
            computations++
            "value"
        }

        assertEquals("value", resolve())
        now = 2_000L
        assertEquals("value", resolve())

        assertEquals(1, computations)
        assertEquals(1, cache.size())
    }

    @Test
    fun `recomputes after ttl expiry`() {
        var now = 1_000L
        var version = 1
        val cache = TtlCache<String, Int>(ttlMillis = 1_500L, clock = { now })
        fun resolve(): Int = cache.getOrCompute("key") { version }

        assertEquals(1, resolve())
        now = 2_500L
        version = 2
        assertEquals(2, resolve())
    }

    @Test
    fun `clock skew recomputes instead of keeping stale`() {
        var now = 5_000L
        val cache = TtlCache<String, String>(ttlMillis = 1_500L, clock = { now })
        var computations = 0
        fun resolve(): String = cache.getOrCompute("key") {
            computations++
            "value"
        }

        resolve()
        // 時刻が巻き戻った場合は古い値を返さず再計算します。
        now = 1_000L
        resolve()

        assertEquals(2, computations)
    }

    @Test
    fun `invalidate and clear drop entries`() {
        val cache = TtlCache<String, String>(ttlMillis = 1_500L)
        cache.getOrCompute("a") { "a" }
        cache.getOrCompute("b") { "b" }
        cache.invalidate("a")

        assertEquals(1, cache.size())
        cache.clear()

        assertEquals(0, cache.size())
    }

    @Test
    fun `evicts oldest beyond capacity`() {
        val cache = TtlCache<String, String>(ttlMillis = 60_000L, maxEntries = 2)
        cache.getOrCompute("a") { "a" }
        cache.getOrCompute("b") { "b" }
        cache.getOrCompute("c") { "c" }

        assertEquals(2, cache.size())
    }

    @Test
    fun `rejects non-positive settings`() {
        assertThrows<IllegalArgumentException> { TtlCache<String, String>(ttlMillis = 0L) }
        assertThrows<IllegalArgumentException> { TtlCache<String, String>(ttlMillis = 1_000L, maxEntries = 0) }
    }
}
