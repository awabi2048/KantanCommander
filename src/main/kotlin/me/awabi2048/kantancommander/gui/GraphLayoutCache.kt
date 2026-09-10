package me.awabi2048.kantancommander.gui

/**
 * グラフレイアウト結果の小規模キャッシュです。
 *
 * issue #15 の差分描画基盤であり、値変更だけの保存で全量レイアウトを
 * 繰り返さないために使います。キーは [GraphStructureFingerprint] による
 * 構造署名であり、呼び出し側で署名と上限を組み立てます。
 * Bukkitへ依存しないため単体テストで検証します。
 */
class GraphLayoutCache(
    private val maxEntries: Int = 32,
) {
    private val entries = object : LinkedHashMap<String, GraphLayout>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GraphLayout>): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun getOrCompute(key: String, compute: () -> GraphLayout): GraphLayout {
        entries[key]?.let { return it }
        val computed = compute()
        entries[key] = computed
        return computed
    }

    @Synchronized
    fun peek(key: String): GraphLayout? = entries[key]

    @Synchronized
    fun invalidate(key: String) {
        entries.remove(key)
    }

    /**
     * 全件破棄します。設定上限の変更やセッション終了時に使います。
     *
     * 通常の保存では構造署名が変わるため自然に別キーとなり、ここを
     * 呼ぶ必要はありません。無効化条件の一元化のために入口だけ残します。
     */
    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
