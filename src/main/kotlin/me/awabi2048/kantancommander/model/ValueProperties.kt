package me.awabi2048.kantancommander.model

import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.UUID

/**
 * issue #3 の詳細値取得レジストリです。
 *
 * 変数へ値を設定する際の値生成方法の一つとして、別変数の詳細値を型安全な
 * adapterで取得します。任意reflection／NBT pathは公開しません。取得した値は
 * 必ず一度変数へ保存し、他コマンドの入力へ直接差し込まない操作仕様です。
 *
 * 詳細値は最大1個の引数変数を取れます（`argType`）。引数の型は定義に固定し、
 * 検証層が引数変数の存在・型を保証します。world・インベントリ等の実体アクセスを
 * 必要とする読み取りは、呼び出し側が注入する [Environment] の関数だけを使い、
 * model層は実行層の具象（信号読み取り・アイテム実体比較）へ依存しません。
 *
 * 対応一覧:
 * - ENTITY: name／type／health／location／gamemode／distance(LOCATION)／has_item(ITEM)
 * - LOCATION: x／y／z／yaw／pitch／distance(LOCATION)／block／redstone_power
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
        /** 引数変数の値型です。引数を取らない詳細値は null です。 */
        val argType: ValueType? = null,
    )

    /**
     * 実行環境へ依存する読み取り関数の注入です。
     *
     * 位置からワールド内の実体を参照する解決は、呼び出し側の基準worldで
     * 行います。未注入の機能に対応する詳細値は解決不能（null）として扱い、
     * 黙って既定値へ読み替えません。
     */
    class Environment(
        /** 位置→その地点のブロックです。 */
        val blockAt: ((SavedLocation) -> org.bukkit.block.Block?)? = null,
        /** 位置→隣接面からの入力信号強度（0..15）です。 */
        val signalStrengthAt: ((SavedLocation) -> Int?)? = null,
        /** 実体とアイテム値から所持判定を返します。 */
        val hasItem: ((Entity, TypedValue) -> Boolean)? = null,
    )

    val ALL: List<PropertyDef> = listOf(
        PropertyDef("name", setOf(ValueType.ENTITY), ValueType.STRING),
        PropertyDef("type", setOf(ValueType.ENTITY, ValueType.ITEM, ValueType.BLOCK), ValueType.STRING),
        PropertyDef("health", setOf(ValueType.ENTITY), ValueType.NUMBER),
        PropertyDef("location", setOf(ValueType.ENTITY), ValueType.LOCATION),
        PropertyDef("gamemode", setOf(ValueType.ENTITY), ValueType.STRING),
        PropertyDef(
            "distance", setOf(ValueType.ENTITY, ValueType.LOCATION), ValueType.NUMBER,
            argType = ValueType.LOCATION,
        ),
        PropertyDef(
            "has_item", setOf(ValueType.ENTITY), ValueType.BOOLEAN,
            argType = ValueType.ITEM,
        ),
        PropertyDef("x", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("y", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("z", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("yaw", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("pitch", setOf(ValueType.LOCATION), ValueType.NUMBER),
        PropertyDef("block", setOf(ValueType.LOCATION), ValueType.BLOCK),
        PropertyDef("redstone_power", setOf(ValueType.LOCATION), ValueType.NUMBER),
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
     * 解決した検索関数だけを受け取ります。取得不能（実体消失・型外・未知ID・
     * 未注入の環境機能）は null で返し、呼び出し側の失敗処理へ委ねます。
     */
    fun resolve(
        source: TypedValue,
        property: String,
        arg: TypedValue? = null,
        environment: Environment = Environment(),
        entityLookup: (UUID) -> Entity?,
    ): TypedValue? {
        val def = definition(property) ?: return null
        if (source.type !in def.sources) return null
        // 引数契約は検証層が保証しますが、実行経路でも型の一致だけは確認します。
        if (arg?.type != def.argType) return null
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
            // ゲームモードはプレイヤーのみが持つ属性です。非プレイヤーは解決不能とし、
            // 空文字や既定値へ黙って落としません。
            "gamemode" -> {
                val player = source.entityId?.let(entityLookup) as? Player ?: return null
                TypedValue(ValueType.STRING, stringValue = player.gameMode.name)
            }
            // 距離は座標差分のユークリッド距離です。向き（yaw・pitch）は距離へ寄与しません。
            "distance" -> {
                val to = arg?.location ?: return null
                val from = when (source.type) {
                    ValueType.ENTITY -> source.entityId?.let(entityLookup)
                        ?.let { SavedLocation(it.location.x, it.location.y, it.location.z) }
                        ?: return null
                    ValueType.LOCATION -> source.location ?: return null
                    else -> return null
                }
                val distance = kotlin.math.sqrt(
                    (from.x - to.x) * (from.x - to.x) +
                        (from.y - to.y) * (from.y - to.y) +
                        (from.z - to.z) * (from.z - to.z),
                )
                TypedValue(ValueType.NUMBER, numberValue = distance)
            }
            "has_item" -> {
                val entity = source.entityId?.let(entityLookup) ?: return null
                val held = environment.hasItem?.invoke(entity, arg ?: return null) ?: return null
                TypedValue(ValueType.BOOLEAN, booleanValue = held)
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
            "block" -> {
                val location = source.location ?: return null
                val material = environment.blockAt?.invoke(location)?.type?.key()?.asString()
                    ?: return null
                TypedValue(ValueType.BLOCK, block = material)
            }
            "redstone_power" -> {
                val location = source.location ?: return null
                val strength = environment.signalStrengthAt?.invoke(location)
                    ?.takeIf { it in 0..15 }
                    ?: return null
                TypedValue(ValueType.NUMBER, numberValue = strength.toDouble())
            }
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
