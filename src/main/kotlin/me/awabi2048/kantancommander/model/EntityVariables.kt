package me.awabi2048.kantancommander.model

import me.awabi2048.kantancommander.error.ErrorCode
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import java.util.Base64
import java.util.Locale
import java.util.UUID

/**
 * issue #22 のEntity変数の保存形式です。
 *
 * Entity自身のPersistentDataContainerへ、かんコマ専用namespaceのキーで
 * 型付き値を文字列化して保存します。Bukkit実体は値として保持せず、
 * 再解決可能な表現だけを使います（#2 の契約）。
 *
 * 形式は `TYPE:payload` の単一文字列です。payloadの区切り文字と被らないよう、
 * 自由文字列はBase64で包みます。
 */
object EntityVariableCodec {
    /** PDCのnamespaceです。他pluginの領域には触れません。 */
    const val NAMESPACE = "kantancommander"

    /** 変数名からPDCキーを作る接頭辞です。一覧取得時の選別に使います。 */
    const val KEY_PREFIX = "entityvar_"

    fun keyFor(name: String): NamespacedKey =
        NamespacedKey(NAMESPACE, KEY_PREFIX + name.trim().lowercase(Locale.ROOT))

    fun isVariableKey(key: NamespacedKey): Boolean =
        key.namespace == NAMESPACE && key.key.startsWith(KEY_PREFIX)

    fun nameOf(key: NamespacedKey): String =
        key.key.removePrefix(KEY_PREFIX)

    /** 型付き値を保存文字列へ変換します。構造不正は null です。 */
    fun encode(value: TypedValue): String? {
        if (!value.isValid()) return null
        val payload: String = when (value.type) {
            ValueType.NUMBER -> value.numberValue.toString()
            ValueType.STRING -> base64(value.stringValue.orEmpty())
            ValueType.BOOLEAN -> value.booleanValue.toString()
            ValueType.LOCATION -> {
                val location = value.location ?: return null
                listOf(location.x, location.y, location.z, location.yaw, location.pitch)
                    .joinToString(",")
            }
            ValueType.VECTOR -> {
                val vector = value.vector ?: return null
                listOf(vector.x, vector.y, vector.z).joinToString(",")
            }
            ValueType.ITEM -> listOf(
                value.item.orEmpty(),
                value.itemData?.let(::base64).orEmpty(),
                (value.amount ?: 1).toString(),
            ).joinToString(",")
            ValueType.BLOCK -> value.block.orEmpty()
            ValueType.ENTITY -> value.entityId?.toString().orEmpty()
        }
        return "${value.type.name}:$payload"
    }

    /** 保存文字列を型付き値へ復元します。不正形式は null です。 */
    fun decode(raw: String?): TypedValue? {
        if (raw == null) return null
        val separator = raw.indexOf(':')
        if (separator <= 0) return null
        val type = ValueType.parse(raw.substring(0, separator)) ?: return null
        val payload = raw.substring(separator + 1)
        val value: TypedValue = when (type) {
            ValueType.NUMBER -> TypedValue(type, numberValue = payload.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null)
            ValueType.STRING -> TypedValue(type, stringValue = unbase64(payload) ?: return null)
            ValueType.BOOLEAN -> when (payload) {
                "true" -> TypedValue(type, booleanValue = true)
                "false" -> TypedValue(type, booleanValue = false)
                else -> return null
            }
            ValueType.LOCATION -> {
                val parts = payload.split(',')
                if (parts.size != 5) return null
                val numbers = parts.map { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
                TypedValue(
                    type,
                    location = SavedLocation(numbers[0], numbers[1], numbers[2], numbers[3].toFloat(), numbers[4].toFloat()),
                )
            }
            ValueType.VECTOR -> {
                val parts = payload.split(',')
                if (parts.size != 3) return null
                val numbers = parts.map { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
                TypedValue(type, vector = SavedVector(numbers[0], numbers[1], numbers[2]))
            }
            ValueType.ITEM -> {
                val parts = payload.split(',')
                if (parts.size != 3) return null
                val amount = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
                // 空の実体は未保持と同義であり、null へ正規化します。
                val itemData = parts[1].takeIf(String::isNotBlank)?.let { unbase64(it) ?: return null }
                TypedValue(
                    type,
                    item = parts[0].takeIf(String::isNotBlank) ?: return null,
                    itemData = itemData,
                    amount = amount,
                )
            }
            ValueType.BLOCK -> TypedValue(type, block = payload.takeIf(String::isNotBlank) ?: return null)
            // 空は未解決の正当な状態、不正形式は破損として拒否します。
            ValueType.ENTITY -> if (payload.isBlank()) {
                TypedValue(type)
            } else {
                TypedValue(
                    type,
                    entityId = runCatching { UUID.fromString(payload) }.getOrNull() ?: return null,
                )
            }
        }
        return value.takeIf(TypedValue::isValid)
    }

    private fun base64(raw: String): String =
        Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))

    private fun unbase64(raw: String): String? = runCatching {
        Base64.getDecoder().decode(raw).toString(Charsets.UTF_8)
    }.getOrNull()
}

/**
 * issue #22 のEntity変数storeです。
 *
 * Entity自身に付随する永続領域へ#2の共通codecで値を保存します。事前DEFINEを
 * 要求せず、変数名は常に文字列で指定します。読み出しで複数Entityから単一値を
 * 暗黙採用しないよう、単一解決を呼び出し側の契約にします。
 *
 * 永続領域の実体は [EntityVariableBackend] の背後に分離し、単体テストでは
 * メモリbackendを使います。
 */
class EntityVariableStore(
    private val backend: EntityVariableBackend,
) {
    /**
     * 解決された複数Entityへ同じ値を一括書き込みします。
     *
     * 対象が空の場合は何もせず 0 を返します。
     */
    fun write(targets: List<Entity>, name: String, value: TypedValue): Int {
        require(value.isValid()) { "invalid variable value" }
        val normalized = normalize(name)
        val encoded = EntityVariableCodec.encode(value) ?: error("value cannot be encoded")
        targets.forEach { backend.write(it, normalized, encoded) }
        return targets.size
    }

    /**
     * 単一Entityから値を読み出します。
     *
     * 対象が0件・2件以上の場合は暗黙採用せず [VariableRead.Failed] を返します。
     * 未定義名も失敗です。失敗理由は#6の共通コードへ接続します。
     */
    fun readSingle(targets: List<Entity>, name: String): VariableRead {
        if (targets.size != 1) return VariableRead.Failed(VariableFailure.Unset)
        val normalized = normalize(name)
        val raw = backend.read(targets.single(), normalized) ?: return VariableRead.Failed(VariableFailure.Undefined)
        val value = EntityVariableCodec.decode(raw) ?: return VariableRead.Failed(VariableFailure.Unset)
        return VariableRead.Value(value)
    }

    /** 解決された複数Entityから変数を削除します。削除件数を返します。 */
    fun delete(targets: List<Entity>, name: String): Int {
        val normalized = normalize(name)
        return targets.count { backend.remove(it, normalized) }
    }

    /** Entityに付随する変数をすべて破棄します。死亡時のリセットに使います。 */
    fun clear(entity: Entity) {
        backend.clear(entity)
    }

    /**
     * 複製先へ変数をそのまま複製します。
     *
     * 繁殖・変化等の複製経路で、複製元の変数一式を引き継ぎます。複製件数を返します。
     */
    fun copyTo(source: Entity, destination: Entity): Int {
        val names = backend.names(source)
        names.forEach { name ->
            val raw = backend.read(source, name) ?: return@forEach
            backend.write(destination, name, raw)
        }
        return names.size
    }

    private fun normalize(raw: String): String {
        val value = raw.trim().lowercase(Locale.ROOT)
        require(CommandValueRules.isVariableName(value)) { "invalid variable name" }
        return value
    }
}

/**
 * issue #22 の変数読み出し結果です。
 *
 * 正常値と失敗理由を区別し、#6の共通エラー情報へ接続します。後続の条件分岐
 * （#5）や値生成（#3）はこの型でENTITY変数を参照します。
 */
sealed interface VariableRead {
    data class Value(val value: TypedValue) : VariableRead
    data class Failed(val failure: VariableFailure) : VariableRead

    fun errorCode(): ErrorCode = when (this) {
        is Value -> error("success has no error code")
        is Failed -> failure.toErrorCode()
    }

    /** 正常値を取り出します。失敗時は null です。 */
    fun asValue(): TypedValue? = (this as? Value)?.value
}

/**
 * Entity変数の永続領域backendです。
 *
 * 本番はEntityのPDC、単体テストはメモリ実装を使います。専用namespaceの
 * キーだけを扱い、他pluginのPDC・metadataは公開しません。
 */
interface EntityVariableBackend {
    fun read(entity: Entity, name: String): String?
    fun write(entity: Entity, name: String, encoded: String)
    fun remove(entity: Entity, name: String): Boolean
    fun clear(entity: Entity)
    fun names(entity: Entity): Set<String>
}

/** PDCを永続領域にする本番backendです。 */
class PdcEntityVariableBackend : EntityVariableBackend {
    override fun read(entity: Entity, name: String): String? =
        entity.persistentDataContainer.get(EntityVariableCodec.keyFor(name), PersistentDataType.STRING)

    override fun write(entity: Entity, name: String, encoded: String) {
        entity.persistentDataContainer.set(EntityVariableCodec.keyFor(name), PersistentDataType.STRING, encoded)
    }

    override fun remove(entity: Entity, name: String): Boolean {
        val container = entity.persistentDataContainer
        val key = EntityVariableCodec.keyFor(name)
        if (!container.has(key, PersistentDataType.STRING)) return false
        container.remove(key)
        return true
    }

    override fun clear(entity: Entity) {
        val container = entity.persistentDataContainer
        container.keys.filter(::isVariableKey).forEach(container::remove)
    }

    override fun names(entity: Entity): Set<String> =
        entity.persistentDataContainer.keys
            .filter(::isVariableKey)
            .mapTo(linkedSetOf(), EntityVariableCodec::nameOf)

    private fun isVariableKey(key: NamespacedKey): Boolean =
        EntityVariableCodec.isVariableKey(key)
}

/** 単体テスト用のメモリbackendです。Entity UUIDごとに符号化済み値を保持します。 */
class MapEntityVariableBackend : EntityVariableBackend {
    private val storage = linkedMapOf<UUID, MutableMap<String, String>>()

    override fun read(entity: Entity, name: String): String? =
        storage[entity.uniqueId]?.get(name)

    override fun write(entity: Entity, name: String, encoded: String) {
        storage.getOrPut(entity.uniqueId) { linkedMapOf() }[name] = encoded
    }

    override fun remove(entity: Entity, name: String): Boolean =
        storage[entity.uniqueId]?.remove(name) != null

    override fun clear(entity: Entity) {
        storage.remove(entity.uniqueId)
    }

    override fun names(entity: Entity): Set<String> =
        storage[entity.uniqueId]?.keys?.toSet().orEmpty()
}
