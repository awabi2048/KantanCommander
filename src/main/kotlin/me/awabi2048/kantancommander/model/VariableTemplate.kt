package me.awabi2048.kantancommander.model

/**
 * 文字列・単一数値入力で使う `${NAME}` 形式の共通参照記法です。
 *
 * 通常のワールド変数は小文字、実行コンテキスト由来のシステム変数は大文字で
 * 表記します。解析自体はこのクラスへ集約し、実行時の値解決だけを呼び出し側へ
 * 渡すことで、GUI・実行・出力が異なる記法を受理する状態を防ぎます。
 */
object VariableTemplate {
    private val referencePattern = Regex("\\$\\{([A-Za-z][A-Za-z0-9_.-]{0,63})}")

    fun references(raw: String): Set<String> = referencePattern.findAll(raw).map { it.groupValues[1] }.toSet()

    /** `${...}` の構文または予約済み参照名が不正かを返します。 */
    fun hasMalformedReference(raw: String): Boolean {
        if (!raw.contains("${'$'}{")) return false
        val matched = references(raw)
        return referencePattern.replace(raw, "").contains("${'$'}{") ||
            matched.any { !SystemVariableNames.isReferenceName(it) }
    }

    /** 文字列中に単一の参照だけがあるかを返します。数値入力欄で共有します。 */
    fun isSingleReference(raw: String): Boolean =
        references(raw).size == 1 &&
            !hasMalformedReference(raw) &&
            raw.trim().matches(referencePattern)

    fun interpolate(raw: String, resolve: (String) -> WorldVariableValue?): String? =
        interpolateText(raw) { name ->
            if (SystemVariableNames.isSystemName(name)) null else resolve(name)?.let(::stringify)
        }

    /** システム変数を含む実行コンテキスト用の文字列補間入口です。 */
    fun interpolateText(raw: String, resolve: (String) -> String?): String? {
        val names = references(raw)
        if (hasMalformedReference(raw)) return null
        if (names.any { resolve(it) == null }) return null
        return referencePattern.replace(raw) { match ->
            requireNotNull(resolve(match.groupValues[1]))
        }
    }

    fun stringify(value: WorldVariableValue): String = when (value.type) {
        VariableType.NUMBER -> value.numberValue?.let(::formatNumber).orEmpty()
        VariableType.STRING -> value.stringValue.orEmpty()
    }

    private fun formatNumber(value: Double): String =
        if (value.isFinite() && value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
