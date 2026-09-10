package me.awabi2048.kantancommander.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * issue #15 の静的文言キャッシュの回帰テストです。
 *
 * 引数なしの固定ラベル解決を使い回し、引数付きの動的文言には触れないことを
 * 解決回数で検証します。locale違いの分離と上限超過時の破棄も担保します。
 */
class StaticTextCacheTest {
    @Test
    fun `same locale and id resolves once`() {
        val cache = StaticTextCache()
        var resolutions = 0
        repeat(3) {
            val resolved = cache.text("ja_JP", "key.close") {
                resolutions++
                "閉じる"
            }
            assertEquals("閉じる", resolved)
        }

        assertEquals(1, resolutions)
        assertEquals(1, cache.size())
    }

    @Test
    fun `different locales resolve separately`() {
        val cache = StaticTextCache()
        val japanese = cache.text("ja_JP", "key.close") { "閉じる" }
        val english = cache.text("en_US", "key.close") { "Close" }

        assertEquals("閉じる", japanese)
        assertEquals("Close", english)
        assertEquals(2, cache.size())
    }

    @Test
    fun `text list returns defensive copies`() {
        val cache = StaticTextCache()
        val first = cache.textList("ja_JP", "key.desc") { listOf("一行目", "二行目") }
        (first as MutableList<String>).clear()

        // 呼び出し側の変更から正本を守り、再取得では欠落しません。
        val second = cache.textList("ja_JP", "key.desc") { error("再解決してはなりません") }
        assertEquals(listOf("一行目", "二行目"), second)
        assertNotSame(first, second)
    }

    @Test
    fun `text returns shared instance`() {
        val cache = StaticTextCache()
        var resolutions = 0
        fun resolve(): String = cache.text("ja_JP", "key.close") {
            resolutions++
            StringBuilder("閉じる").toString()
        }

        val first = resolve()
        val second = resolve()

        // 文字列は不変のため共有し、解決コストだけを抑止します。
        assertSame(first, second)
        assertEquals(1, resolutions)
    }

    @Test
    fun `evicts oldest beyond capacity`() {
        val cache = StaticTextCache(maxEntries = 2)
        var resolutions = 0
        fun resolve(id: String): String = cache.text("ja_JP", id) {
            resolutions++
            id
        }

        resolve("a")
        resolve("b")
        resolve("c")
        // 容量超過で最古が破棄され、再取得で再解決します。
        resolve("a")

        assertEquals(4, resolutions)
        assertEquals(2, cache.size())
    }

    @Test
    fun `clear drops all entries`() {
        val cache = StaticTextCache()
        cache.text("ja_JP", "key.close") { "閉じる" }
        cache.textList("ja_JP", "key.desc") { listOf("説明") }
        cache.clear()

        assertEquals(0, cache.size())
    }
}
