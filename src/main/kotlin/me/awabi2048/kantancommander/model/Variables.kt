package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.error.ErrorCode
import java.util.Locale
import java.util.UUID

/**
 * issue #2 の共通値型です。
 *
 * EXECUTION／WORLD／ENTITY の全scopeで共有し、変数操作・条件・計算・DISK_CALL引数・
 * イベント引数のすべてがこの型で値をやり取りします。旧 `VariableType`
 * （NUMBER／STRINGのみ）と旧 `TemporaryVariableType` を統合した正本です。
 * 音・効果の一時変数参照は廃止したため、SOUND／EFFECT 型は持ちません。
 * 旧保存文字列の SOUND／EFFECT 指定は不正として扱います。
 * `ValueType.ENTITY` は値の種類であり、`VariableScope.ENTITY`（変数の所有主体）とは
 * 別概念として扱います。
 */
enum class ValueType {
    NUMBER,
    STRING,
    BOOLEAN,
    LOCATION,
    ITEM,
    BLOCK,
    ENTITY,
    ;

    companion object {
        /**
         * 保存文字列を型へ変換します。
         *
         * 旧一時変数の POSITION 表記は LOCATION へ正規化します。enumへ旧名を残すと
         * entries() が増えてGUIへ旧概念が再登場するため、互換処理はこの境界だけに
         * 閉じ込めます。不明・null は null で返し、呼び出し側で不正として扱います。
         */
        fun parse(raw: String?): ValueType? {
            val normalized = raw?.trim()?.uppercase(Locale.ROOT) ?: return null
            return when (normalized) {
                "POSITION" -> LOCATION
                else -> entries.firstOrNull { it.name == normalized }
            }
        }
    }
}

/**
 * issue #2 の共通型付き値です。
 *
 * 旧 `WorldVariableValue`（WORLD永続）と旧 `TemporaryValue`（実行内寿命）を統合し、
 * 両scopeが同じ表現を使います。Bukkit／Paper の実体オブジェクトは保持せず、
 * 再解決・永続化可能な表現（ID・座標・素材名等）だけを持ちます。ENTITY は UUID
 * だけを保持し、実体の生死・所属ワールドは参照時に解決します。
 */
data class TypedValue(
    val type: ValueType,
    val numberValue: Double? = null,
    val stringValue: String? = null,
    /** BOOLEAN型の値です。 */
    val booleanValue: Boolean? = null,
    /** LOCATION型の解決済み座標・向きです。位置と向きを分離して保持しません。 */
    val location: SavedLocation? = null,
    /** アイテム型の素材IDと実体（Base64）です。 */
    val item: String? = null,
    val itemData: String? = null,
    /** アイテム型の個数です。未設定時は1個として扱います。 */
    val amount: Int? = null,
    /** ブロック型の素材IDです。 */
    val block: String? = null,
    /** ENTITY型の個体IDです。実体は保持しません。 */
    val entityId: UUID? = null,
) {
    /**
     * 型と構造の一致を検証します。
     *
     * 型固有の必須欄が有効値であることと、他型の混入がないことを確認します。
     * 保存層の不変条件・実行時の代入可否・GUIの完了判定がこの一点に依存します。
     */
    fun isValid(): Boolean = when (type) {
        ValueType.NUMBER -> numberValue?.isFinite() == true && isClearExcept("numberValue")
        ValueType.STRING -> stringValue != null &&
            stringValue.length <= CommandValueRules.WORLD_VARIABLE_STRING_MAX_LENGTH &&
            isClearExcept("stringValue")
        ValueType.BOOLEAN -> booleanValue != null && isClearExcept("booleanValue")
        ValueType.LOCATION -> location != null &&
            location.x.isFinite() && location.y.isFinite() && location.z.isFinite() &&
            isClearExcept("location")
        ValueType.ITEM -> item != null && (amount == null || amount > 0) && isClearExcept("item", "itemData", "amount")
        ValueType.BLOCK -> block != null && isClearExcept("block")
        // ENTITY は定義時点で実体がなくても保持します。死亡・別ワールド・未ロードは
        // 参照時に解決不能として扱い、後続だけをスキップできるようにします。
        ValueType.ENTITY -> isClearExcept("entityId")
    }

    private fun isClearExcept(vararg keep: String): Boolean {
        val kept = keep.toSet()
        if ("numberValue" !in kept && numberValue != null) return false
        if ("stringValue" !in kept && stringValue != null) return false
        if ("booleanValue" !in kept && booleanValue != null) return false
        if ("location" !in kept && location != null) return false
        if ("item" !in kept && item != null) return false
        if ("itemData" !in kept && itemData != null) return false
        if ("amount" !in kept && amount != null) return false
        if ("block" !in kept && block != null) return false
        if ("entityId" !in kept && entityId != null) return false
        return true
    }

    companion object {
        /**
         * 型だけの定義で使う空値です。利用者が設定した初期値ではありません。
         *
         * 数値・文字列は型既定の空値、位置はゼロ座標、素材系は代表値、ENTITY は未解決（null）の
         * まま保持します。GUIの作成フローは定義直後に実値の設定へ進みます。
         */
        fun empty(type: ValueType): TypedValue = when (type) {
            ValueType.NUMBER -> TypedValue(type, numberValue = 0.0)
            ValueType.STRING -> TypedValue(type, stringValue = "")
            ValueType.BOOLEAN -> TypedValue(type, booleanValue = false)
            ValueType.LOCATION -> TypedValue(type, location = SavedLocation(0.0, 0.0, 0.0))
            ValueType.ITEM -> TypedValue(type, item = "STONE")
            ValueType.BLOCK -> TypedValue(type, block = "STONE")
            ValueType.ENTITY -> TypedValue(type)
        }
    }
}

/**
 * issue #2 の変数所有主体です。
 *
 * - EXECUTION: 実行／Call Frame 内だけに存在し、終了時に破棄します。
 * - WORLD: world を所有主体として永続化します。
 * - ENTITY: 特定 Entity を所有主体として永続化します。具体仕様は #22 で定め、
 *   #2 では型・codec・エラー契約だけを接続可能にします。
 */
enum class VariableScope {
    EXECUTION,
    WORLD,
    ENTITY,
    ;

    companion object {
        fun parse(raw: String?): VariableScope? =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase(Locale.ROOT) }
    }
}

/**
 * issue #2 の変数解決失敗です。
 *
 * 未定義・未設定・参照失効・scope不足・型不一致を区別し、#6 の共通エラー情報へ
 * 接続します。message 文字列ではなく型で判定し、テスト可能にします。
 */
sealed interface VariableFailure {
    /** 定義そのものが存在しません。 */
    data object Undefined : VariableFailure

    /** 定義はあるが値が未設定または解決不能です（実体消失等）。 */
    data object Unset : VariableFailure

    /** 参照先の所有主体が失われています（#22 の Entity 消失等）。 */
    data object OwnerMissing : VariableFailure

    /** 実行文脈に必要な scope がありません。 */
    data object ScopeMissing : VariableFailure

    /** 値の型が要求と一致しません。 */
    data class TypeMismatch(val expected: ValueType?, val actual: ValueType?) : VariableFailure

    /** #6 の共通エラーコードへ変換します。 */
    fun toErrorCode(): ErrorCode = when (this) {
        Undefined -> ErrorCode.VARIABLE_UNDEFINED
        Unset -> ErrorCode.VARIABLE_UNSET
        OwnerMissing -> ErrorCode.VARIABLE_OWNER_MISSING
        ScopeMissing -> ErrorCode.VARIABLE_SCOPE_MISSING
        is TypeMismatch -> ErrorCode.VARIABLE_TYPE_MISMATCH
    }
}

/**
 * issue #2 の共通変数store契約です。
 *
 * EXECUTION（実行内寿命）と WORLD（永続化）の両storeがこの境界の背後に分離し、
 * 後続機能（#3・#5・#8・#22）は具象storeではなくこの契約を利用します。
 * ENTITY scope のstoreは #22 でこの契約へ接続します。
 */
interface VariableStore {
    /** 新しい変数を定義します。既存名がある場合は false を返し、何もしません。 */
    fun define(name: String, value: TypedValue): Boolean

    /**
     * 値を設定します。未定義名には暗黙定義として記録します（TEMP_SET互換）。
     *
     * 定義済みと異なる型の上書きは拒否します。呼び出し側は事前に型を確認するか、
     * 拒否時の [VariableFailure.TypeMismatch] を処理してください。
     */
    fun set(name: String, value: TypedValue)

    /** 現在値を返します。未定義・未設定は null です。 */
    fun get(name: String): TypedValue?

    /** 定義済みの型を返します。未定義は null です。 */
    fun definition(name: String): ValueType?

    /** 定義一覧を返します。 */
    fun definitions(): Map<String, ValueType>

    /** 定義と値を削除します。存在しなかった場合は false です。 */
    fun remove(name: String): Boolean
}

/**
 * EXECUTION scope の実行内storeです。
 *
 * 実行開始時に空で生成し、実行／Call Frame 終了時に破棄します。DISK_CALL 越境では
 * 呼び出し側が snapshot・復元して隔離します（WORLD／ENTITY のstoreは複製しません）。
 */
class ExecutionVariables : VariableStore {
    private val values = linkedMapOf<String, TypedValue>()
    private val types = linkedMapOf<String, ValueType>()

    override fun define(name: String, value: TypedValue): Boolean {
        require(value.isValid()) { "invalid variable value" }
        val normalized = normalize(name)
        if (normalized in types) return false
        types[normalized] = value.type
        values[normalized] = value.copy()
        return true
    }

    override fun set(name: String, value: TypedValue) {
        require(value.isValid()) { "invalid variable value" }
        val normalized = normalize(name)
        val declared = types[normalized]
        require(declared == null || declared == value.type) { "variable type cannot change" }
        types.putIfAbsent(normalized, value.type)
        values[normalized] = value.copy()
    }

    override fun get(name: String): TypedValue? =
        values[lenient(name)]?.copy()

    override fun definition(name: String): ValueType? =
        types[lenient(name)]

    override fun definitions(): Map<String, ValueType> =
        types.toMap()

    override fun remove(name: String): Boolean {
        val normalized = lenient(name)
        return (values.remove(normalized) != null) or (types.remove(normalized) != null)
    }

    /** DISK_CALL 越境の隔離用に内容を複写します。 */
    fun snapshot(): Map<String, TypedValue> =
        values.mapValues { (_, value) -> value.copy() }

    /** DISK_CALL 復帰時に呼び出し側の内容へ戻します。 */
    fun restore(snapshot: Map<String, TypedValue>) {
        values.clear()
        types.clear()
        snapshot.forEach { (name, value) ->
            values[name] = value.copy()
            types[name] = value.type
        }
    }

    fun clear() {
        values.clear()
        types.clear()
    }

    private fun normalize(raw: String): String {
        val value = raw.trim().lowercase(Locale.ROOT)
        require(CommandValueRules.isVariableName(value)) { "invalid variable name" }
        return value
    }

    /**
     * 参照系の正規化です。未検証の入力にも例外を投げず、見つからなければ
     * 解決失敗（null・false）として扱います。旧 `temporaries[x]` 参照と
     * 同じく、呼び出し側の失敗処理へ委ねます。
     */
    private fun lenient(raw: String): String =
        raw.trim().lowercase(Locale.ROOT)
}
