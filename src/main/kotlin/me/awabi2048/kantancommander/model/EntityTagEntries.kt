package me.awabi2048.kantancommander.model

/**
 * タグ操作の一覧を1つの保存文字列へ符号化する境界です。
 *
 * 1ノードで複数タグを扱うため、`params`（Map<String,String>）に複数キーを
 * 増やさず、単一キー [PARAM_ENTRIES] へ `add=<tag>;remove=<tag>` 形式で
 * 保存します。`=` と `;` はタグ文字（[A-Za-z0-9_.:+-]）と衝突しないため
 * 区切り文字として安全です。変数参照（`%{name}%` 形式）も1カード1値として
 * 扱い、同形式で保存します。
 */
object EntityTagEntries {
    /** paramsに保存するキーです。 */
    const val PARAM_ENTRIES = "tagEntries"

    /** 追加操作の接頭辞です。 */
    const val MODE_ADD = "add"

    /** 削除操作の接頭辞です。 */
    const val MODE_REMOVE = "remove"

    /** 1件分のタグ操作です。[mode] は add／remove のいずれかです。 */
    data class Entry(val mode: String, val tag: String)

    /** 保存文字列をパースします。空欄は空一覧であり、不正形式はnullです。 */
    fun parse(raw: String): List<Entry>? {
        if (raw.isBlank()) return emptyList()
        val entries = mutableListOf<Entry>()
        for (token in raw.split(";")) {
            if (token.isBlank()) return null
            val separator = token.indexOf('=')
            if (separator <= 0) return null
            val mode = token.substring(0, separator)
            val tag = token.substring(separator + 1)
            if (mode != MODE_ADD && mode != MODE_REMOVE) return null
            if (tag.isBlank()) return null
            entries += Entry(mode, tag)
        }
        // 同一タグの重複・add/remove混在は呼び出し側が検証エラーにします。
        // ここでは構文確認に留めます。
        return entries
    }

    /** 一覧を保存文字列へ符号化します。空一覧は空文字列です。 */
    fun encode(entries: List<Entry>): String =
        entries.joinToString(";") { "${it.mode}=${it.tag}" }

    /**
     * 値の正当性を返します。
     *
     * リテラルはタグ形式、参照形式（変数・一時）は参照形式の検査へ委ねます。
     * 重複タグ・同一タグのadd/remove混在は偽とします。
     */
    fun isValid(entries: List<Entry>): Boolean {
        if (entries.isEmpty()) return false
        val seen = mutableSetOf<String>()
        for (entry in entries) {
            val literal = VariableTemplate.references(entry.tag).isEmpty() &&
                TemporaryTemplate.references(entry.tag).isEmpty()
            if (literal && !CommandValueRules.isTag(entry.tag)) return false
            if (!seen.add(entry.tag)) return false
        }
        return true
    }
}
