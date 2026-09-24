package me.awabi2048.kantancommander.model

/**
 * issue #5 の型付き条件比較モデルです。
 *
 * 「左辺・演算子・右辺」の共通モデルで値を比較します。左辺は変数参照だけを
 * 扱い、右辺はリテラル（スカラーはテンプレート参照を含む）で指定します。
 * 正常な不一致（false）と、値解決失敗・型不一致を区別します。
 */
object ConditionComparison {
    /** 比較演算子です。文字列保存は旧形式と互換です。 */
    const val EQUAL = "=="
    const val NOT_EQUAL = "!="
    const val GREATER = ">"
    const val GREATER_OR_EQUAL = ">="
    const val LESS = "<"
    const val LESS_OR_EQUAL = "<="
    const val CONTAINS = "contains"
    /**
     * issue #5 の領域述語です。左辺のLOCATION／ENTITY値が [RegionSpec] の
     * 内側にあるかを判定します。右辺リテランの代わりにノードの領域指定を使い、
     * [compare] では扱わず実行層の領域 resolver へ接続します。
     */
    const val IN_REGION = "in_region"
    const val NOT_IN_REGION = "not_in_region"

    /** 全演算子の一覧です。GUIの循環選択と検証で共有します。 */
    val ALL: List<String> = listOf(EQUAL, NOT_EQUAL, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL, CONTAINS, IN_REGION, NOT_IN_REGION)

    /** 型ごとに使える演算子を返します。 */
    fun operatorsFor(type: ValueType): Set<String> = when (type) {
        ValueType.NUMBER -> setOf(EQUAL, NOT_EQUAL, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL)
        ValueType.STRING -> setOf(EQUAL, NOT_EQUAL, CONTAINS)
        ValueType.ENTITY -> setOf(EQUAL, NOT_EQUAL, IN_REGION, NOT_IN_REGION)
        ValueType.LOCATION -> setOf(IN_REGION, NOT_IN_REGION)
        ValueType.ITEM,
        ValueType.BLOCK,
        -> emptySet()
    }

    /** 領域述語の演算子かを返します。右辺ではなくノードの領域指定を使います。 */
    fun isRegionPredicate(operator: String): Boolean = operator == IN_REGION || operator == NOT_IN_REGION

    /**
     * 型付き値を比較します。
     *
     * 正常な比較結果だけを返し、型外の演算子・未対応型の組み合わせは null で
     * 返します。呼び出し側は null を型不一致（INVALID）として扱い、正常な
     * false へ畳まないでください。
     */
    fun compare(left: TypedValue, operator: String, right: TypedValue): Boolean? {
        if (left.type != right.type) return null
        return when (left.type) {
            ValueType.NUMBER -> {
                val actual = left.numberValue ?: return null
                val expected = right.numberValue ?: return null
                val comparison = actual.compareTo(expected)
                when (operator) {
                    EQUAL -> comparison == 0
                    NOT_EQUAL -> comparison != 0
                    GREATER -> comparison > 0
                    GREATER_OR_EQUAL -> comparison >= 0
                    LESS -> comparison < 0
                    LESS_OR_EQUAL -> comparison <= 0
                    else -> null
                }
            }
            ValueType.STRING -> {
                val actual = left.stringValue ?: return null
                val expected = right.stringValue ?: return null
                when (operator) {
                    EQUAL -> actual == expected
                    NOT_EQUAL -> actual != expected
                    CONTAINS -> actual.contains(expected)
                    else -> null
                }
            }
            ValueType.ENTITY -> {
                when (operator) {
                    EQUAL -> left.entityId == right.entityId
                    NOT_EQUAL -> left.entityId != right.entityId
                    else -> null
                }
            }
            ValueType.LOCATION,
            ValueType.ITEM,
            ValueType.BLOCK,
            -> null
        }
    }
}
