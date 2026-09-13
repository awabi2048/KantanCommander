package me.awabi2048.kantancommander.model

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import io.papermc.paper.datacomponent.item.ResolvableProfile
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Pose
import org.bukkit.entity.Rabbit
import org.bukkit.inventory.MainHand

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

    // Mannequin固有項目です。descriptionは設定不可とし、ここでは扱いません。
    // profileはスキンのプレイヤー名のみを受け付け、UUID・テクスチャ直接指定は行いません。
    const val PROPERTY_MANNEQUIN_PROFILE_NAME = "mannequin_profile_name"
    // スコア下に表示する説明文です。空欄は除去して既定表示に戻します。
    const val PROPERTY_MANNEQUIN_DESCRIPTION = "mannequin_description"
    const val PROPERTY_MANNEQUIN_IMMOVABLE = "mannequin_immovable"
    const val PROPERTY_MANNEQUIN_MAIN_HAND = "mannequin_main_hand"
    const val PROPERTY_MANNEQUIN_POSE = "mannequin_pose"
    const val PROPERTY_MANNEQUIN_SKIN_CAPE = "mannequin_skin_cape"
    const val PROPERTY_MANNEQUIN_SKIN_JACKET = "mannequin_skin_jacket"
    const val PROPERTY_MANNEQUIN_SKIN_LEFT_SLEEVE = "mannequin_skin_left_sleeve"
    const val PROPERTY_MANNEQUIN_SKIN_RIGHT_SLEEVE = "mannequin_skin_right_sleeve"
    const val PROPERTY_MANNEQUIN_SKIN_LEFT_PANTS = "mannequin_skin_left_pants"
    const val PROPERTY_MANNEQUIN_SKIN_RIGHT_PANTS = "mannequin_skin_right_pants"
    const val PROPERTY_MANNEQUIN_SKIN_HAT = "mannequin_skin_hat"

    /** 利き手のstableな選択肢IDです。BukkitのMainHand名と対応します。 */
    const val MANNEQUIN_MAIN_HAND_LEFT = "left"
    const val MANNEQUIN_MAIN_HAND_RIGHT = "right"

    // ポーズのstableな選択肢IDです。BukkitのPose名の小文字と対応します。
    // 静的一覧はプレイヤー系の代表的な候補に絞り、実行時にMannequin.validPoses()で最終判定します。
    const val MANNEQUIN_POSE_STANDING = "standing"
    const val MANNEQUIN_POSE_SNEAKING = "sneaking"
    const val MANNEQUIN_POSE_SWIMMING = "swimming"
    const val MANNEQUIN_POSE_SLEEPING = "sleeping"
    const val MANNEQUIN_POSE_SITTING = "sitting"
    const val MANNEQUIN_POSE_FALL_FLYING = "fall_flying"
    const val MANNEQUIN_POSE_SPIN_ATTACK = "spin_attack"

    // スキンのプレイヤー名は解決可能な名前のみを受け付けます。
    // 空欄・長さ・文字種は編集時に検証し、存在確認はクライアント側の動的解決へ委ねます。
    private val MANNEQUIN_PROFILE_NAME_PATTERN = Regex("[A-Za-z0-9_]{3,16}")

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

    // 利き手の選択肢です。BukkitのMainHand名と1対1で対応付けます。
    private val mannequinMainHandChoices = listOf(
        EntityPropertyChoice(MANNEQUIN_MAIN_HAND_LEFT, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MANNEQUIN_MAIN_HAND_LEFT),
        EntityPropertyChoice(MANNEQUIN_MAIN_HAND_RIGHT, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MANNEQUIN_MAIN_HAND_RIGHT),
    )

    // ポーズの選択肢です。子画面での選択専用であり、直接入力させません。
    // BukkitのPose名の小文字と対応し、実行時にMannequin.validPoses()で最終判定します。
    private val mannequinPoseChoices = listOf(
        EntityPropertyChoice(MANNEQUIN_POSE_STANDING, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MANNEQUIN_POSE_STANDING),
        EntityPropertyChoice(MANNEQUIN_POSE_SNEAKING, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MANNEQUIN_POSE_SNEAKING),
        EntityPropertyChoice(MANNEQUIN_POSE_SWIMMING, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MANNEQUIN_POSE_SWIMMING),
        EntityPropertyChoice(MANNEQUIN_POSE_SLEEPING, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MANNEQUIN_POSE_SLEEPING),
        EntityPropertyChoice(MANNEQUIN_POSE_SITTING, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MANNEQUIN_POSE_SITTING),
        EntityPropertyChoice(MANNEQUIN_POSE_FALL_FLYING, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MANNEQUIN_POSE_FALL_FLYING),
        EntityPropertyChoice(MANNEQUIN_POSE_SPIN_ATTACK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MANNEQUIN_POSE_SPIN_ATTACK),
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
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_PROFILE_NAME, EntityPropertyValueKind.TEXT,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_PROFILE_NAME,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_DESCRIPTION, EntityPropertyValueKind.TEXT,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_DESCRIPTION,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_IMMOVABLE, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_IMMOVABLE,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_MAIN_HAND, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            enumChoices = mannequinMainHandChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_MAIN_HAND,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_POSE, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            enumChoices = mannequinPoseChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_POSE,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_SKIN_CAPE, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_SKIN_CAPE,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_SKIN_JACKET, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_SKIN_JACKET,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_SKIN_LEFT_SLEEVE, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_SKIN_LEFT_SLEEVE,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_SKIN_RIGHT_SLEEVE, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_SKIN_RIGHT_SLEEVE,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_SKIN_LEFT_PANTS, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_SKIN_LEFT_PANTS,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_SKIN_RIGHT_PANTS, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_SKIN_RIGHT_PANTS,
        ),
        EntityPropertyDefinition(
            PROPERTY_MANNEQUIN_SKIN_HAT, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.MANNEQUIN.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MANNEQUIN_SKIN_HAT,
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
        PROPERTY_MANNEQUIN_IMMOVABLE -> (entity as? Mannequin)?.let { it.setImmovable(value); true } ?: false
        PROPERTY_MANNEQUIN_SKIN_CAPE,
        PROPERTY_MANNEQUIN_SKIN_JACKET,
        PROPERTY_MANNEQUIN_SKIN_LEFT_SLEEVE,
        PROPERTY_MANNEQUIN_SKIN_RIGHT_SLEEVE,
        PROPERTY_MANNEQUIN_SKIN_LEFT_PANTS,
        PROPERTY_MANNEQUIN_SKIN_RIGHT_PANTS,
        PROPERTY_MANNEQUIN_SKIN_HAT,
        -> applyMannequinSkinPart(entity, propertyId, value)
        else -> false
    }

    /** enum値を適用します。選択肢IDとBukkitのenum値の対応はここで管理します。 */
    fun applyEnum(entity: Entity, propertyId: String, choiceId: String): Boolean {
        if (propertyId == PROPERTY_RABBIT_TYPE) {
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
        if (propertyId == PROPERTY_MANNEQUIN_MAIN_HAND) {
            val mannequin = entity as? Mannequin ?: return false
            val hand = when (choiceId) {
                MANNEQUIN_MAIN_HAND_LEFT -> MainHand.LEFT
                MANNEQUIN_MAIN_HAND_RIGHT -> MainHand.RIGHT
                else -> return false
            }
            return runCatching { mannequin.setMainHand(hand); true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_MANNEQUIN_POSE) {
            val mannequin = entity as? Mannequin ?: return false
            val pose = mannequinPose(choiceId) ?: return false
            // 静的一覧より狭い実行時制約があればそちらを優先します。
            // 単体試験環境などBridge未初期化時は静的一覧の判定に留めます。
            val valid = runCatching { Mannequin.validPoses() }.getOrNull()
            if (valid != null && pose !in valid) return false
            return runCatching { mannequin.setPose(pose); true }.getOrDefault(false)
        }
        return false
    }

    /**
     * スキンのプレイヤー名が構文として正しいかを返します。
     *
     * 解決可能性の存在確認はクライアント側の動的解決へ委ね、ここでは
     * 空欄・長さ・文字種のみを検証します。
     */
    fun isMannequinProfileName(raw: String): Boolean =
        MANNEQUIN_PROFILE_NAME_PATTERN.matches(raw)

    /**
     * スキンのプレイヤー名を適用します。適用前に[supports]で可否を判定してください。
     *
     * 名前のみの動的プロファイルを作り、スキン解決をクライアントへ委ねます。
     * メインスレッドを止める外部問い合わせは行いません。
     */
    fun applyMannequinProfile(entity: Entity, playerName: String): Boolean {
        val mannequin = entity as? Mannequin ?: return false
        if (!isMannequinProfileName(playerName)) return false
        return runCatching {
            mannequin.setProfile(ResolvableProfile.resolvableProfile().name(playerName).build())
            true
        }.getOrDefault(false)
    }

    // ポーズ選択肢IDとBukkitのPoseの対応です。不明IDはnullとします。
    fun mannequinPose(choiceId: String): Pose? = when (choiceId) {
        MANNEQUIN_POSE_STANDING -> Pose.STANDING
        MANNEQUIN_POSE_SNEAKING -> Pose.SNEAKING
        MANNEQUIN_POSE_SWIMMING -> Pose.SWIMMING
        MANNEQUIN_POSE_SLEEPING -> Pose.SLEEPING
        MANNEQUIN_POSE_SITTING -> Pose.SITTING
        MANNEQUIN_POSE_FALL_FLYING -> Pose.FALL_FLYING
        MANNEQUIN_POSE_SPIN_ATTACK -> Pose.SPIN_ATTACK
        else -> null
    }

    // 肌部位の表示切替を適用します。部位IDとSkinParts.Mutableの対応はここで管理します。
    private fun applyMannequinSkinPart(entity: Entity, propertyId: String, value: Boolean): Boolean {
        val mannequin = entity as? Mannequin ?: return false
        return runCatching {
            val parts = mannequin.skinParts
            when (propertyId) {
                PROPERTY_MANNEQUIN_SKIN_CAPE -> parts.setCapeEnabled(value)
                PROPERTY_MANNEQUIN_SKIN_JACKET -> parts.setJacketEnabled(value)
                PROPERTY_MANNEQUIN_SKIN_LEFT_SLEEVE -> parts.setLeftSleeveEnabled(value)
                PROPERTY_MANNEQUIN_SKIN_RIGHT_SLEEVE -> parts.setRightSleeveEnabled(value)
                PROPERTY_MANNEQUIN_SKIN_LEFT_PANTS -> parts.setLeftPantsEnabled(value)
                PROPERTY_MANNEQUIN_SKIN_RIGHT_PANTS -> parts.setRightPantsEnabled(value)
                PROPERTY_MANNEQUIN_SKIN_HAT -> parts.setHatsEnabled(value)
                else -> return false
            }
            mannequin.setSkinParts(parts)
            true
        }.getOrDefault(false)
    }
}
