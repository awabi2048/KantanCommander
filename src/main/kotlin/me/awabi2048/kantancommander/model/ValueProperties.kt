package me.awabi2048.kantancommander.model

import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import java.util.UUID

/**
 * issue #3 の詳細値取得レジストリです。
 *
 * 変数へ値を設定する際の値生成方法の一つとして、別変数の詳細値を型安全な
 * adapterで取得します。任意reflection／NBT pathは公開しません。取得した値は
 * 必ず一度変数へ保存し、他コマンドの入力へ直接差し込まない操作仕様です。
 *
 * 初期対応一覧（#3）:
 * - ENTITY: name／type／health／location
 * - LOCATION: x／y／z／yaw／pitch
 * - ITEM: type／amount
 * - BLOCK: type
 */
object ValueProperties {
    /** 詳細値の定義です。GUIの選択肢と検証・実行で共有します。 */
    data class PropertyDef(
        /** 保存・表示に使う固定IDです。 */
        val id: String,
        /** 取得元にできる値型です。 */
        val sources: Set<ValueType>,
        /** 取得結果の値型です。 */
        val result: ValueType,
    )

    val ALL: List<PropertyDef> = listOf(
        PropertyDef("name", setOf(ValueType.ENTITY), ValueType.STRING),
        PropertyDef("type", setOf(ValueType.ENTITY, ValueType.ITEM, ValueType.BLOCK), ValueType.STRING),
        PropertyDef("health", setOf(ValueType.ENTITY), ValueType.NUMBER),
        PropertyDef("location", setOf(ValueType.ENTITY), ValueType.LOCATION),
        PropertyDef("x", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("y", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("z", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("yaw", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("pitch", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("amount", setOf(ValueType.ITEM), ValueType.NUMBER),
    )

    fun definition(id: String): PropertyDef? =
        ALL.firstOrNull { it.id == id }

    /** 指定型から取得できる詳細値の一覧を返します。 */
    fun forSource(type: ValueType): List<PropertyDef> =
        ALL.filter { type in it.sources }

    /**
     * 詳細値を取得します。
     *
     * ENTITY系の解決には実体検索が必要です。Bukkit実体は保持せず、呼び出し側が
     * 解決した検索関数だけを受け取ります。取得不能（実体消失・型外・未知ID）は
     * null で返し、呼び出し側の失敗処理へ委ねます。
     */
    fun resolve(
        source: TypedValue,
        property: String,
        entityLookup: (UUID) -> Entity?,
    ): TypedValue? {
        val def = definition(property) ?: return null
        if (source.type !in def.sources) return null
        val value: TypedValue = when (property) {
            "name" -> {
                val entity = source.entityId?.let(entityLookup) ?: return null
                TypedValue(ValueType.STRING, stringValue = entity.name)
            }
            "type" -> when (source.type) {
                ValueType.ENTITY -> {
                    val entity = source.entityId?.let(entityLookup) ?: return null
                    TypedValue(ValueType.STRING, stringValue = entity.type.key().asString())
                }
                ValueType.ITEM -> TypedValue(ValueType.STRING, stringValue = source.item ?: return null)
                ValueType.BLOCK -> TypedValue(ValueType.STRING, stringValue = source.block ?: return null)
                else -> return null
            }
            "health" -> {
                val entity = source.entityId?.let(entityLookup) ?: return null
                val health = (entity as? LivingEntity)?.health?.takeIf(Double::isFinite) ?: return null
                TypedValue(ValueType.NUMBER, numberValue = health)
            }
            "location" -> {
                val entity = source.entityId?.let(entityLookup) ?: return null
                val location = entity.location
                TypedValue(
                    ValueType.LOCATION,
                    location = SavedLocation(location.x, location.y, location.z, location.yaw, location.pitch),
                )
            }
            "x" -> TypedValue(ValueType.NUMBER, numberValue = source.location?.x ?: return null)
            "y" -> TypedValue(ValueType.NUMBER, numberValue = source.location?.y ?: return null)
            "z" -> TypedValue(ValueType.NUMBER, numberValue = source.location?.z ?: return null)
            "yaw" -> TypedValue(
                ValueType.NUMBER,
                numberValue = source.location?.yaw?.toDouble() ?: return null,
            )
            "pitch" -> TypedValue(
                ValueType.NUMBER,
                numberValue = source.location?.pitch?.toDouble() ?: return null,
            )
            "amount" -> TypedValue(
                ValueType.NUMBER,
                numberValue = (source.amount ?: 1).toDouble(),
            )
            else -> return null
        }
        if (value.type != def.result || !value.isValid()) return null
        return value
    }
}
