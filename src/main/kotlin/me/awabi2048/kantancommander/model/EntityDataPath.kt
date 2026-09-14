package me.awabi2048.kantancommander.model

/**
 * 統合選択の正本となる設定対象IDです。
 *
 * 旧形式の `operation`（tag／attribute／data の3択）を廃止し、1つのIDで
 * 「何を設定するか」を一意に表します。GUI・検証・実行・出力はこのIDを
 * 分岐の正本とし、旧キー（operation／attribute／property）は保存しません。
 * 未知ID・空欄はフォールバックで吸収せず、未設定・不正として扱います。
 */
object EntityDataPath {
    /** paramsに保存するキーです。 */
    const val PARAM_PATH = "entityDataPath"

    /** タグ操作（一覧管理）の固定IDです。 */
    const val TAG = "tag"

    /** attribute選択の接頭辞です。完全形は `attribute:<namespacedKey>` です。 */
    const val ATTRIBUTE_PREFIX = "attribute:"

    /** 型付きデータ設定の接頭辞です。完全形は `property:<propertyId>` です。 */
    const val PROPERTY_PREFIX = "property:"

    /** パスの種別です。 */
    enum class Kind {
        TAG,
        ATTRIBUTE,
        PROPERTY,
    }

    /** パース済みのパスです。 */
    sealed interface Parsed {
        /** タグ操作です。詳細は [EntityTagEntries] に保存します。 */
        data object Tag : Parsed

        /** attribute操作です。[attributeId] はNamespacedKey形式のID文字列です。 */
        data class Attribute(val attributeId: String) : Parsed

        /** 型付きデータ設定です。[propertyId] は [EntityDataSchema] の安定IDです。 */
        data class Property(val propertyId: String) : Parsed
    }

    /** 保存文字列をパースします。不正・空欄はnullであり、呼び出し側が未設定として扱います。 */
    fun parse(raw: String): Parsed? {
        if (raw.isBlank()) return null
        if (raw == TAG) return Parsed.Tag
        if (raw.startsWith(ATTRIBUTE_PREFIX)) {
            val id = raw.removePrefix(ATTRIBUTE_PREFIX)
            // attribute ID自体の実在確認は EntityAttributeSupport.isAttributeId が担い、
            // ここでは構文（空でないこと）だけを確認します。
            if (id.isBlank()) return null
            return Parsed.Attribute(id)
        }
        if (raw.startsWith(PROPERTY_PREFIX)) {
            val id = raw.removePrefix(PROPERTY_PREFIX)
            if (id.isBlank()) return null
            return Parsed.Property(id)
        }
        return null
    }

    /** 種別を返します。不正値はnullです。 */
    fun kindOf(raw: String): Kind? = when (parse(raw)) {
        is Parsed.Tag -> Kind.TAG
        is Parsed.Attribute -> Kind.ATTRIBUTE
        is Parsed.Property -> Kind.PROPERTY
        null -> null
    }

    /** attribute選択の保存文字列を組み立てます。 */
    fun attributePath(attributeId: String): String = "$ATTRIBUTE_PREFIX$attributeId"

    /** データ設定の保存文字列を組み立てます。 */
    fun propertyPath(propertyId: String): String = "$PROPERTY_PREFIX$propertyId"

    /** 保存文字列からattribute IDを取り出します。attribute選択以外はnullです。 */
    fun attributeIdOf(rawPath: String): String? =
        (parse(rawPath) as? Parsed.Attribute)?.attributeId

    /** 保存文字列から設定項目IDを取り出します。データ設定以外はnullです。 */
    fun propertyIdOf(rawPath: String): String? =
        (parse(rawPath) as? Parsed.Property)?.propertyId
}
