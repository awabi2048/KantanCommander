package me.awabi2048.kantancommander.model

/** Dialogで入力した文字列へワールド内変数を埋め込むための共通記法です。 */
object VariableTemplate {
    private val referencePattern = Regex("\\$\\{([a-z][a-z0-9_.-]{0,63})}")

    fun references(raw: String): Set<String> = referencePattern.findAll(raw).map { it.groupValues[1] }.toSet()

    /** 構文上の変数参照だけを抽出し、未知の `${...}` も入力エラーとして検出できるようにします。 */
    fun hasMalformedReference(raw: String): Boolean =
        raw.contains("${'$'}{") && referencePattern.replace(raw, "").contains("${'$'}{")

    fun interpolate(raw: String, resolve: (String) -> WorldVariableValue?): String? {
        val references = references(raw)
        if (hasMalformedReference(raw)) return null
        if (references.any { resolve(it) == null }) return null
        return referencePattern.replace(raw) { match ->
            stringify(requireNotNull(resolve(match.groupValues[1])))
        }
    }

    fun stringify(value: WorldVariableValue): String = when (value.type) {
        VariableType.NUMBER -> value.numberValue?.let(::formatNumber).orEmpty()
        VariableType.STRING -> value.stringValue.orEmpty()
    }

    private fun formatNumber(value: Double): String =
        if (value.isFinite() && value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
