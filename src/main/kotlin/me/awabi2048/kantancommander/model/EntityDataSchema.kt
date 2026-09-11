package me.awabi2048.kantancommander.model

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Rabbit

/**
 * issue #10 の型付きEntity設定スキーマです。
 *
 * 任意のNBT path／SNBT文字列は公開せず、KantanCommander側で編集可能項目を
 * 限定定義します。値の型・候補・対応EntityType・GUI入力種別は項目ごとに固定し、
 * NBT由来の否定形名称（NoAI等）ではなく意味ベースの名称（AI等）で公開します。
 * Bukkit／Paperの型付きAPIだけを利用し、低レベル処理はこの境界の内側へ隔離します。
 *
 * issue #3 との対応: 読み出し可能な主要項目は詳細値取得と名称・型を可能な範囲で
 * 揃える方針であり、stableな設定ID（[id]）は将来のread側と共有できる命名とします。
 * ただし本スキーマは個別Entityへの書き込み専用であり、読み出しは #3 の責務です。
 */
enum class EntityPropertyValueKind {
    /** 真偽値です。Gesture上はCheckbox相当で直接切り替えます。 */
    BOOLEAN,

    /** 有限候補です。内部IDと表示名の対応はスキーマ側で管理し、数値を直接入力させません。 */
    ENUM,

    /** 自由文字列です。Dialog入力で編集します。 */
    TEXT,
}

/** ENUM値のstableな選択肢です。内部IDと表示の対応をここで固定します。 */
data class EntityPropertyChoice(
    /** 保存形式に使うstableなIDです。Bukkitのenum名と1対1で対応付けます。 */
    val id: String,
    val optionKey: LocalizationKey<String>,
)

/**
 * 編集可能項目の定義です。
 *
 * - [id]: 保存形式・GUI・実行・出力が共有するstableな設定IDです。
 * - [entityTypes]: nullは全Entity共通項目です。非nullは対応EntityType名（Bukkitの
 *   enum名）の集合であり、編集時点でEntityTypeが確定した場合だけ表示します。
 * - [requiresLiving]: 真の場合はLivingEntityにだけ適用できます。
 * - 未確定事項として残っていたArmorStand pose等の複合値は初期対象に含めません。
 */
data class EntityPropertyDefinition(
    val id: String,
    val valueKind: EntityPropertyValueKind,
    val entityTypes: Set<String>?,
    val requiresLiving: Boolean = false,
    val enumChoices: List<EntityPropertyChoice> = emptyList(),
    val optionKey: LocalizationKey<String>,
)

/** 型付き設定値です。保存文字列と実行値の対応を型で固定します。 */
sealed interface EntityPropertyValue {
    data class BooleanValue(val value: Boolean) : EntityPropertyValue

    data class EnumValue(val choiceId: String) : EntityPropertyValue

    data class TextValue(val text: String) : EntityPropertyValue
}

/**
 * 静的に確定できる対応状況です。
 *
 * issue #6 の区別に従い、「確実に不可能」と「runtimeまで不明」を分けます。
 * 検証側は前者だけを編集時エラーとし、後者は実行時のcapability判定へ委ねます。
 */
enum class EntityPropertySupport {
    /** 静的に対象外と確定します。編集時エラーの対象です。 */
    STATICALLY_UNSUPPORTED,

    /** runtimeの実体が確定するまで判定できません。表示・保存は維持します。 */
    RUNTIME_DEPENDENT,

    /** 静的に適用可能と確定します。 */
    STATICALLY_SUPPORTED,
}

object EntityDataSchema {
    /** データ編集の対象が個別Entityであることを表す保存値です。複数一括編集は扱いません。 */
    const val PARAM_PROPERTY = "property"
    const val PARAM_VALUE = "propertyValue"

    const val PROPERTY_AI = "ai"
    const val PROPERTY_SILENT = "silent"
    const val PROPERTY_INVULNERABLE = "invulnerable"
    const val PROPERTY_GRAVITY = "gravity"
    const val PROPERTY_GLOWING = "glowing"
    const val PROPERTY_PERSISTENT = "persistent"
    const val PROPERTY_CUSTOM_NAME = "custom_name"
    const val PROPERTY_ARMOR_STAND_ARMS = "armor_stand_arms"
    const val PROPERTY_ARMOR_STAND_SMALL = "armor_stand_small"
    const val PROPERTY_ARMOR_STAND_MARKER = "armor_stand_marker"
    const val PROPERTY_ARMOR_STAND_BASE_PLATE = "armor_stand_base_plate"
    const val PROPERTY_RABBIT_TYPE = "rabbit_type"

    /** ウサギ種類のstableな選択肢IDです。BukkitのRabbit.Type名と対応します。 */
    const val RABBIT_BROWN = "brown"
    const val RABBIT_WHITE = "white"
    const val RABBIT_BLACK = "black"
    const val RABBIT_BLACK_AND_WHITE = "black_and_white"
    const val RABBIT_GOLD = "gold"
    const val RABBIT_SALT_AND_PEPPER = "salt_and_pepper"
    const val RABBIT_KILLER_BUNNY = "killer_bunny"

    private val rabbitChoices = listOf(
        EntityPropertyChoice(RABBIT_BROWN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_BROWN),
        EntityPropertyChoice(RABBIT_WHITE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_WHITE),
        EntityPropertyChoice(RABBIT_BLACK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_BLACK),
        EntityPropertyChoice(RABBIT_BLACK_AND_WHITE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_BLACK_AND_WHITE),
        EntityPropertyChoice(RABBIT_GOLD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_GOLD),
        EntityPropertyChoice(RABBIT_SALT_AND_PEPPER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_SALT_AND_PEPPER),
        EntityPropertyChoice(RABBIT_KILLER_BUNNY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_KILLER_BUNNY),
    )

    private val definitions: List<EntityPropertyDefinition> = listOf(
        EntityPropertyDefinition(
            PROPERTY_AI, EntityPropertyValueKind.BOOLEAN, entityTypes = null, requiresLiving = true,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_AI,
        ),
        EntityPropertyDefinition(
            PROPERTY_SILENT, EntityPropertyValueKind.BOOLEAN, entityTypes = null,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_SILENT,
        ),
        EntityPropertyDefinition(
            PROPERTY_INVULNERABLE, EntityPropertyValueKind.BOOLEAN, entityTypes = null,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_INVULNERABLE,
        ),
        EntityPropertyDefinition(
            PROPERTY_GRAVITY, EntityPropertyValueKind.BOOLEAN, entityTypes = null,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_GRAVITY,
        ),
        EntityPropertyDefinition(
            PROPERTY_GLOWING, EntityPropertyValueKind.BOOLEAN, entityTypes = null,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_GLOWING,
        ),
        EntityPropertyDefinition(
            PROPERTY_PERSISTENT, EntityPropertyValueKind.BOOLEAN, entityTypes = null,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_PERSISTENT,
        ),
        EntityPropertyDefinition(
            PROPERTY_CUSTOM_NAME, EntityPropertyValueKind.TEXT, entityTypes = null,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_CUSTOM_NAME,
        ),
        EntityPropertyDefinition(
            PROPERTY_ARMOR_STAND_ARMS, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.ARMOR_STAND.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_ARMOR_STAND_ARMS,
        ),
        EntityPropertyDefinition(
            PROPERTY_ARMOR_STAND_SMALL, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.ARMOR_STAND.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_ARMOR_STAND_SMALL,
        ),
        EntityPropertyDefinition(
            PROPERTY_ARMOR_STAND_MARKER, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.ARMOR_STAND.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_ARMOR_STAND_MARKER,
        ),
        EntityPropertyDefinition(
            PROPERTY_ARMOR_STAND_BASE_PLATE, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.ARMOR_STAND.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_ARMOR_STAND_BASE_PLATE,
        ),
        EntityPropertyDefinition(
            PROPERTY_RABBIT_TYPE, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.RABBIT.name),
            enumChoices = rabbitChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_RABBIT_TYPE,
        ),
    )

    private val byId: Map<String, EntityPropertyDefinition> = definitions.associateBy(EntityPropertyDefinition::id)

    /** 安定IDから定義を返します。未知IDはnullであり、フォールバックで吸収しません。 */
    fun definition(id: String): EntityPropertyDefinition? = byId[id]

    /**
     * 対象指定から静的に確定できるEntityTypeを返します。
     *
     * - 対象フィルターのentityTypeが有効なIDなら、その種類と確定します。
     * - プレイヤー系の対象種別はPLAYERと確定します。
     * - それ以外（最近傍Entity・変数参照・固定UUID等）はruntimeまで不明としてnullを返し、
     *   推測で設定項目を隠しません（#18の表示原則）。
     */
    fun knownEntityType(targetSpec: TargetSpec?): EntityType? {
        // Paper Registryはサーバー起動前のAPI単体テスト環境では初期化できないため、
        // 解決失敗時は不明として扱います。実行時と同じRegistryを使う点は維持します。
        val filtered = targetSpec?.entityType?.takeIf(String::isNotBlank)?.let { raw ->
            runCatching {
                NamespacedKey.fromString(raw)?.let { Registry.ENTITY_TYPE.get(it) }
            }.getOrNull()
        }
        if (filtered != null) return filtered
        return when (targetSpec?.kind) {
            TargetKind.NEAREST_PLAYER,
            TargetKind.NEARBY_PLAYERS,
            TargetKind.ALL_PLAYERS,
            TargetKind.RANDOM_PLAYER,
            -> EntityType.PLAYER
            else -> null
        }
    }

    /**
     * 編集時点で表示する設定項目を返します。
     *
     * - EntityTypeが確定できる場合、その種類固有の項目を含めます。
     * - 確定できない場合、全Entityで利用できる汎用項目だけを表示し、
     *   runtime-dependentな固有設定を候補として広げません。
     */
    fun propertiesFor(entityType: EntityType?): List<EntityPropertyDefinition> {
        if (entityType == null) return definitions.filter { it.entityTypes == null && !it.requiresLiving }
        val living = entityType.isAlive
        return definitions.filter { definition ->
            when {
                definition.entityTypes != null -> entityType.name in definition.entityTypes
                definition.requiresLiving -> living
                else -> true
            }
        }
    }

    /**
     * 既知のEntityTypeに対する静的対応状況を返します。
     *
     * 未知ID・静的対象外は呼び出し側が編集時エラーへ変換し、
     * runtime-dependentは実行時のcapability判定へ委ねます。
     */
    fun staticSupport(entityType: EntityType?, propertyId: String): EntityPropertySupport {
        val definition = byId[propertyId] ?: return EntityPropertySupport.STATICALLY_UNSUPPORTED
        if (entityType == null) return EntityPropertySupport.RUNTIME_DEPENDENT
        if (definition.entityTypes != null && entityType.name !in definition.entityTypes) {
            return EntityPropertySupport.STATICALLY_UNSUPPORTED
        }
        if (definition.requiresLiving && !entityType.isAlive) {
            return EntityPropertySupport.STATICALLY_UNSUPPORTED
        }
        return EntityPropertySupport.STATICALLY_SUPPORTED
    }

    /**
     * 保存文字列を型付き値へ変換します。
     *
     * booleanは厳密判定であり、不正値はnullとして検証・実行の両方で拒否します。
     * enumは定義済み選択肢だけを受け付け、内部数値の直接指定は受け付けません。
     */
    fun parse(propertyId: String, raw: String): EntityPropertyValue? {
        val definition = byId[propertyId] ?: return null
        return when (definition.valueKind) {
            EntityPropertyValueKind.BOOLEAN ->
                raw.toBooleanStrictOrNull()?.let(EntityPropertyValue::BooleanValue)
            EntityPropertyValueKind.ENUM ->
                definition.enumChoices.firstOrNull { it.id == raw }?.let { EntityPropertyValue.EnumValue(it.id) }
            EntityPropertyValueKind.TEXT -> EntityPropertyValue.TextValue(raw)
        }
    }

    /**
     * 実行時の適用可否（capability）を返します。
     *
     * 静的判定を通過していても、実際の種類・生存状態が合わなければ偽とします。
     * 呼び出し側は偽を「非適用」として数え、設定自体の不正と区別します（#6）。
     */
    fun supports(entity: Entity, propertyId: String): Boolean {
        val definition = byId[propertyId] ?: return false
        if (definition.entityTypes != null && entity.type.name !in definition.entityTypes) return false
        if (definition.requiresLiving && entity !is LivingEntity) return false
        return true
    }

    /**
     * 真偽値を適用します。適用前に[supports]で可否を判定してください。
     *
     * NBT名の否定形ではなく意味ベースで保存するため、重力あり=trueが
     * setGravity(true)へ直結します。
     */
    fun applyBoolean(entity: Entity, propertyId: String, value: Boolean): Boolean = when (propertyId) {
        PROPERTY_AI -> (entity as? LivingEntity)?.let { it.setAI(value); true } ?: false
        PROPERTY_SILENT -> entity.setSilent(value).let { true }
        PROPERTY_INVULNERABLE -> entity.setInvulnerable(value).let { true }
        PROPERTY_GRAVITY -> entity.setGravity(value).let { true }
        PROPERTY_GLOWING -> entity.setGlowing(value).let { true }
        PROPERTY_PERSISTENT -> entity.setPersistent(value).let { true }
        PROPERTY_ARMOR_STAND_ARMS -> (entity as? ArmorStand)?.let { it.setArms(value); true } ?: false
        PROPERTY_ARMOR_STAND_SMALL -> (entity as? ArmorStand)?.let { it.setSmall(value); true } ?: false
        PROPERTY_ARMOR_STAND_MARKER -> (entity as? ArmorStand)?.let { it.setMarker(value); true } ?: false
        PROPERTY_ARMOR_STAND_BASE_PLATE -> (entity as? ArmorStand)?.let { it.setBasePlate(value); true } ?: false
        else -> false
    }

    /** enum値を適用します。選択肢IDとBukkitのenum値の対応はここで管理します。 */
    fun applyEnum(entity: Entity, propertyId: String, choiceId: String): Boolean {
        if (propertyId != PROPERTY_RABBIT_TYPE) return false
        val rabbit = entity as? Rabbit ?: return false
        val type = when (choiceId) {
            RABBIT_BROWN -> Rabbit.Type.BROWN
            RABBIT_WHITE -> Rabbit.Type.WHITE
            RABBIT_BLACK -> Rabbit.Type.BLACK
            RABBIT_BLACK_AND_WHITE -> Rabbit.Type.BLACK_AND_WHITE
            RABBIT_GOLD -> Rabbit.Type.GOLD
            RABBIT_SALT_AND_PEPPER -> Rabbit.Type.SALT_AND_PEPPER
            RABBIT_KILLER_BUNNY -> Rabbit.Type.THE_KILLER_BUNNY
            else -> return false
        }
        rabbit.setRabbitType(type)
        return true
    }
}
