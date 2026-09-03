package me.awabi2048.kantancommander.model

/**
 * 一時変数（実行内寿命）の `%{name}%` 記法を扱う共通境界です。
 *
 * ワールド内変数の `${name}`（VariableTemplate）とは記法を分離し、同名共存を
 * 許可します。リテラル利用できる文字列・数値型だけがこの記法を使い、
 * 複合型は一般テキストへ埋め込まず、型付き設定欄または構造化Specから指定します。
 */
object TemporaryTemplate {
    private val referencePattern = Regex("%\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}%")

    fun references(raw: String): Set<String> = referencePattern.findAll(raw).map { it.groupValues[1] }.toSet()

    /** `%{...}%` の開始だけが残っている不正な参照を検出します。単独の `%` はリテラルです。 */
    fun hasMalformedReference(raw: String): Boolean {
        var searchFrom = 0
        while (true) {
            val start = raw.indexOf("%{", searchFrom)
            if (start < 0) return false
            val end = raw.indexOf("}%", start + 2)
            if (end < 0) return true
            val candidate = raw.substring(start, end + 2)
            if (!referencePattern.matches(candidate)) return true
            searchFrom = end + 2
        }
    }

    /** 文字列中に単一の一時参照だけがあるかを返します。数値入力欄で共有します。 */
    fun isSingleReference(raw: String): Boolean =
        references(raw).size == 1 &&
            !hasMalformedReference(raw) &&
            raw.trim().matches(referencePattern)

    /** 実行時の一時変数解決入口です。未定義名があると null を返します。 */
    fun interpolateText(raw: String, resolve: (String) -> String?): String? {
        val names = references(raw)
        if (hasMalformedReference(raw)) return null
        if (names.any { resolve(it) == null }) return null
        return referencePattern.replace(raw) { match ->
            requireNotNull(resolve(match.groupValues[1]))
        }
    }

    /** 一時変数名を小文字へ正規化します。ワールド内変数と同一規則です。 */
    fun normalized(name: String): String = name.trim().lowercase(java.util.Locale.ROOT)
}

/** 一時変数名の正規化の短縮記法です。 */
fun String.normalizedTemporaryName(): String = TemporaryTemplate.normalized(this)
