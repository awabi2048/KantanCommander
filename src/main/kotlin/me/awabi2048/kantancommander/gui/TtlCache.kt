package me.awabi2048.kantancommander.gui

/**
 * 短命な検証結果を使い回すTTLキャッシュです。
 *
 * issue #15 の再生成抑止基盤であり、世代管理の対象外だが再計算が高価な
 * 検証（変数定義付きのテスト可否検証など）を、 bounded な鮮度で再利用します。
 * 世代で不変性が保証できる正本検証とは使い分け、ここでは一定時間だけ
 * 再計算を抑止します。Bukkitへ依存しないため単体テストで検証します。
 *
 * キー数が発散しないよう最新数件だけを保持し、古い世代は自然に破棄します。
 */
class TtlCache<K, V>(
    private val ttlMillis: Long,
    private val maxEntries: Int = 8,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<V>(val value: V, val storedAt: Long)

    private val entries = object : LinkedHashMap<K, Entry<V>>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>): Boolean =
            size > maxEntries
    }

    init {
        require(ttlMillis > 0L) { "TTL must be positive" }
        require(maxEntries > 0) { "capacity must be positive" }
    }

    @Synchronized
    fun getOrCompute(key: K, compute: () -> V): V {
        val now = clock()
        val hit = entries[key]
        // 時刻が巻き戻った場合は古い値を返さず再計算し、staleの残留を防ぎます。
        if (hit != null && now >= hit.storedAt && now - hit.storedAt < ttlMillis) return hit.value
        val computed = compute()
        entries[key] = Entry(computed, now)
        return computed
    }

    @Synchronized
    fun invalidate(key: K) {
        entries.remove(key)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
