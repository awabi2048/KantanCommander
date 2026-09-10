package me.awabi2048.kantancommander.util

/**
 * 引数なしの静的文言の解決結果を使い回す小規模キャッシュです。
 *
 * issue #15 の再生成抑止基盤であり、上下画面の再構築ごとに行われる
 * 固定ラベル（DUPLICATE／DELETE／BACK等）の言語解決をまとめます。
 * 引数付きの動的文言は対象外にし、呼び出し側で毎回解決します。
 * Bukkitへ依存しないため単体テストで検証します。
 *
 * 前提条件は以下であり、崩れた場合は [clear] で破棄します。
 * - 同一locale＋同一キーIDの解決結果は不変（カタログは起動時に契約照合済みの埋め込み正本）
 * - locale違いはキーへ含めて分離（利用中の言語切替えへ追従）
 */
class StaticTextCache(
    private val maxEntries: Int = 512,
) {
    data class Key(val locale: String, val id: String)

    private val texts = object : LinkedHashMap<Key, String>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, String>): Boolean =
            size > maxEntries
    }

    private val textLists = object : LinkedHashMap<Key, List<String>>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<String>>): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun text(locale: String, id: String, resolve: () -> String): String =
        texts.getOrPut(Key(locale, id)) { resolve() }

    /**
     * 一覧文言の解決結果を使い回します。
     *
     * 呼び出し側での変更から正本を守るため、返却時は複写を渡します。
     * 解決コストの削減が目的であり、複写自体は小規模です。
     */
    @Synchronized
    fun textList(locale: String, id: String, resolve: () -> List<String>): List<String> =
        textLists.getOrPut(Key(locale, id)) { resolve().toList() }.toList()

    @Synchronized
    fun clear() {
        texts.clear()
        textLists.clear()
    }

    @Synchronized
    fun size(): Int = texts.size + textLists.size
}
