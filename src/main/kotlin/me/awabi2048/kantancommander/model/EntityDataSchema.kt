package me.awabi2048.kantancommander.model

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import io.papermc.paper.datacomponent.item.ResolvableProfile
import io.papermc.paper.entity.CollarColorable
import org.bukkit.DyeColor
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.Ageable
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Axolotl
import org.bukkit.entity.Bee
import org.bukkit.entity.Boat
import org.bukkit.entity.Cat
import org.bukkit.entity.Creeper
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Fox
import org.bukkit.entity.Frog
import org.bukkit.entity.Horse
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Llama
import org.bukkit.entity.Mannequin
import org.bukkit.entity.MushroomCow
import org.bukkit.entity.Parrot
import org.bukkit.entity.Pose
import org.bukkit.entity.PufferFish
import org.bukkit.entity.Rabbit
import org.bukkit.entity.Sheep
import org.bukkit.entity.Shulker
import org.bukkit.entity.Sittable
import org.bukkit.entity.Slime
import org.bukkit.entity.Steerable
import org.bukkit.entity.Villager
import org.bukkit.entity.Wolf
import org.bukkit.entity.Zombie
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
    /** 真偽値です。Gesture上は選択→クリックの直接切替えで編集します。 */
    BOOLEAN,

    /** 有限候補です。内部IDと表示名の対応はスキーマ側で管理し、数値を直接入力させません。 */
    ENUM,

    /** 自由文字列です。Dialog入力で編集します。 */
    TEXT,

    /**
     * 範囲付き整数です。Dialog入力で編集し、定義の [EntityPropertyDefinition.intRange]
     * で検証します。スライムの大きさ等の段階値を扱います。
     */
    INTEGER,
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
    /** INTEGER種別の許容範囲です。INTEGER以外では使いません。 */
    val intRange: IntRange? = null,
    val optionKey: LocalizationKey<String>,
)

/** 型付き設定値です。保存文字列と実行値の対応を型で固定します。 */
sealed interface EntityPropertyValue {
    data class BooleanValue(val value: Boolean) : EntityPropertyValue

    data class EnumValue(val choiceId: String) : EntityPropertyValue

    data class TextValue(val text: String) : EntityPropertyValue

    data class IntegerValue(val value: Int) : EntityPropertyValue
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
    const val PROPERTY_ALLOW_FLIGHT = "allow_flight"
    const val PROPERTY_PERSISTENT = "persistent"
    const val PROPERTY_CUSTOM_NAME = "custom_name"
    const val PROPERTY_ARMOR_STAND_ARMS = "armor_stand_arms"
    const val PROPERTY_ARMOR_STAND_SMALL = "armor_stand_small"
    const val PROPERTY_ARMOR_STAND_MARKER = "armor_stand_marker"
    const val PROPERTY_ARMOR_STAND_BASE_PLATE = "armor_stand_base_plate"
    const val PROPERTY_RABBIT_TYPE = "rabbit_type"

    // エンティティ固有項目です。対象確定時のみ表示し、和集合表示は行いません。
    // 能力横断項目（幼体・おすわり）は対応EntityType集合で限定し、実行時の
    // capability判定を最終判定とします。
    const val PROPERTY_AGEABLE_BABY = "ageable_baby"
    const val PROPERTY_SHEEP_COLOR = "sheep_color"
    const val PROPERTY_SHEEP_SHEARED = "sheep_sheared"
    const val PROPERTY_SLIME_SIZE = "slime_size"
    const val PROPERTY_CREEPER_POWERED = "creeper_powered"
    const val PROPERTY_SITTABLE_SITTING = "sittable_sitting"
    const val PROPERTY_COLLAR_COLOR = "collar_color"
    const val PROPERTY_CAT_TYPE = "cat_type"
    const val PROPERTY_AXOLOTL_VARIANT = "axolotl_variant"
    const val PROPERTY_FOX_TYPE = "fox_type"
    const val PROPERTY_FROG_VARIANT = "frog_variant"
    const val PROPERTY_VILLAGER_PROFESSION = "villager_profession"
    const val PROPERTY_ZOMBIE_BABY = "zombie_baby"
    const val PROPERTY_PIG_SADDLE = "pig_saddle"
    const val PROPERTY_PUFFER_STATE = "puffer_state"
    const val PROPERTY_VILLAGER_TYPE = "villager_type"
    const val PROPERTY_MOOSHROOM_VARIANT = "mooshroom_variant"
    const val PROPERTY_HORSE_COLOR = "horse_color"
    const val PROPERTY_HORSE_STYLE = "horse_style"
    const val PROPERTY_LLAMA_COLOR = "llama_color"
    const val PROPERTY_PARROT_VARIANT = "parrot_variant"
    const val PROPERTY_SHULKER_COLOR = "shulker_color"
    const val PROPERTY_BOAT_TYPE = "boat_type"
    const val PROPERTY_BEE_NECTAR = "bee_nectar"
    const val PROPERTY_BEE_ANGER = "bee_anger"

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

    // DyeColor共用選択肢のstableなIDです。BukkitのDyeColor名の小文字と対応します。
    const val DYE_WHITE = "white"
    const val DYE_ORANGE = "orange"
    const val DYE_MAGENTA = "magenta"
    const val DYE_LIGHT_BLUE = "light_blue"
    const val DYE_YELLOW = "yellow"
    const val DYE_LIME = "lime"
    const val DYE_PINK = "pink"
    const val DYE_GRAY = "gray"
    const val DYE_LIGHT_GRAY = "light_gray"
    const val DYE_CYAN = "cyan"
    const val DYE_PURPLE = "purple"
    const val DYE_BLUE = "blue"
    const val DYE_BROWN = "brown"
    const val DYE_GREEN = "green"
    const val DYE_RED = "red"
    const val DYE_BLACK = "black"

    // 猫種類のstableな選択肢IDです。BukkitのCat.Type名と対応します。
    const val CAT_TABBY = "tabby"
    const val CAT_BLACK = "black"
    const val CAT_RED = "red"
    const val CAT_SIAMESE = "siamese"
    const val CAT_BRITISH_SHORTHAIR = "british_shorthair"
    const val CAT_CALICO = "calico"
    const val CAT_PERSIAN = "persian"
    const val CAT_RAGDOLL = "ragdoll"
    const val CAT_WHITE = "white"
    const val CAT_JELLIE = "jellie"
    const val CAT_ALL_BLACK = "all_black"

    // ウーパールーパー種類のstableな選択肢IDです。BukkitのAxolotl.Variant名と対応します。
    const val AXOLOTL_LUCY = "lucy"
    const val AXOLOTL_WILD = "wild"
    const val AXOLOTL_GOLD = "gold"
    const val AXOLOTL_CYAN = "cyan"
    const val AXOLOTL_BLUE = "blue"

    // キツネ種類のstableな選択肢IDです。BukkitのFox.Type名と対応します。
    const val FOX_RED = "red"
    const val FOX_SNOW = "snow"

    // カエル種類のstableな選択肢IDです。BukkitのFrog.Variant名と対応します。
    const val FROG_TEMPERATE = "temperate"
    const val FROG_WARM = "warm"
    const val FROG_COLD = "cold"

    // 村人職業のstableな選択肢IDです。BukkitのVillager.Profession名の小文字と対応します。
    const val VILLAGER_PROFESSION_NONE = "none"
    const val VILLAGER_PROFESSION_ARMORER = "armorer"
    const val VILLAGER_PROFESSION_BUTCHER = "butcher"
    const val VILLAGER_PROFESSION_CARTOGRAPHER = "cartographer"
    const val VILLAGER_PROFESSION_CLERIC = "cleric"
    const val VILLAGER_PROFESSION_FARMER = "farmer"
    const val VILLAGER_PROFESSION_FISHERMAN = "fisherman"
    const val VILLAGER_PROFESSION_FLETCHER = "fletcher"
    const val VILLAGER_PROFESSION_LEATHERWORKER = "leatherworker"
    const val VILLAGER_PROFESSION_LIBRARIAN = "librarian"
    const val VILLAGER_PROFESSION_MASON = "mason"
    const val VILLAGER_PROFESSION_NITWIT = "nitwit"
    const val VILLAGER_PROFESSION_SHEPHERD = "shepherd"
    const val VILLAGER_PROFESSION_TOOLSMITH = "toolsmith"
    const val VILLAGER_PROFESSION_WEAPONSMITH = "weaponsmith"

    // 村人見た目のstableな選択肢IDです。BukkitのVillager.Type名と対応します。
    const val VILLAGER_TYPE_DESERT = "desert"
    const val VILLAGER_TYPE_JUNGLE = "jungle"
    const val VILLAGER_TYPE_PLAINS = "plains"
    const val VILLAGER_TYPE_SAVANNA = "savanna"
    const val VILLAGER_TYPE_SNOW = "snow"
    const val VILLAGER_TYPE_SWAMP = "swamp"
    const val VILLAGER_TYPE_TAIGA = "taiga"

    // ムーシュルーム種類のstableな選択肢IDです。BukkitのMushroomCow.Variant名と対応します。
    const val MOOSHROOM_RED = "red"
    const val MOOSHROOM_BROWN = "brown"

    // 馬毛色のstableな選択肢IDです。BukkitのHorse.Color名と対応します。
    const val HORSE_WHITE = "white"
    const val HORSE_CREAMY = "creamy"
    const val HORSE_CHESTNUT = "chestnut"
    const val HORSE_BROWN = "brown"
    const val HORSE_BLACK = "black"
    const val HORSE_GRAY = "gray"
    const val HORSE_DARK_BROWN = "dark_brown"

    // 馬模様のstableな選択肢IDです。BukkitのHorse.Style名と対応します。
    const val HORSE_STYLE_NONE = "none"
    const val HORSE_STYLE_WHITE = "white"
    const val HORSE_STYLE_WHITEFIELD = "whitefield"
    const val HORSE_STYLE_WHITE_DOTS = "white_dots"
    const val HORSE_STYLE_BLACK_DOTS = "black_dots"

    // ラマ色のstableな選択肢IDです。BukkitのLlama.Color名と対応します。
    const val LLAMA_CREAMY = "creamy"
    const val LLAMA_WHITE = "white"
    const val LLAMA_BROWN = "brown"
    const val LLAMA_GRAY = "gray"

    // オウム色のstableな選択肢IDです。BukkitのParrot.Variant名と対応します。
    const val PARROT_RED = "red"
    const val PARROT_BLUE = "blue"
    const val PARROT_GREEN = "green"
    const val PARROT_CYAN = "cyan"
    const val PARROT_GRAY = "gray"

    // ボート種類のstableな選択肢IDです。BukkitのBoat.Type名と対応します。
    // Paper 26.1.2のBoat.TypeにPALE_OAKは存在しないため含めません。
    const val BOAT_OAK = "oak"
    const val BOAT_SPRUCE = "spruce"
    const val BOAT_BIRCH = "birch"
    const val BOAT_JUNGLE = "jungle"
    const val BOAT_ACACIA = "acacia"
    const val BOAT_DARK_OAK = "dark_oak"
    const val BOAT_MANGROVE = "mangrove"
    const val BOAT_CHERRY = "cherry"
    const val BOAT_BAMBOO = "bamboo"

    // フグ膨張段階のstableな選択肢IDです。PufferFishのint値（0／1／2）と対応します。
    const val PUFFER_UNPUFFED = "unpuffed"
    const val PUFFER_HALF_PUFFED = "half_puffed"
    const val PUFFER_FULLY_PUFFED = "fully_puffed"

    private val rabbitChoices = listOf(
        EntityPropertyChoice(RABBIT_BROWN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_BROWN),
        EntityPropertyChoice(RABBIT_WHITE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_WHITE),
        EntityPropertyChoice(RABBIT_BLACK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_BLACK),
        EntityPropertyChoice(RABBIT_BLACK_AND_WHITE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_BLACK_AND_WHITE),
        EntityPropertyChoice(RABBIT_GOLD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_GOLD),
        EntityPropertyChoice(RABBIT_SALT_AND_PEPPER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_SALT_AND_PEPPER),
        EntityPropertyChoice(RABBIT_KILLER_BUNNY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_RABBIT_KILLER_BUNNY),
    )

    // DyeColor共用選択肢です。羊・首輪・シュルカーで共有し、キー爆発を抑えます。
    private val dyeChoices = listOf(
        EntityPropertyChoice(DYE_WHITE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_WHITE),
        EntityPropertyChoice(DYE_ORANGE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_ORANGE),
        EntityPropertyChoice(DYE_MAGENTA, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_MAGENTA),
        EntityPropertyChoice(DYE_LIGHT_BLUE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_LIGHT_BLUE),
        EntityPropertyChoice(DYE_YELLOW, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_YELLOW),
        EntityPropertyChoice(DYE_LIME, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_LIME),
        EntityPropertyChoice(DYE_PINK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_PINK),
        EntityPropertyChoice(DYE_GRAY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_GRAY),
        EntityPropertyChoice(DYE_LIGHT_GRAY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_LIGHT_GRAY),
        EntityPropertyChoice(DYE_CYAN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_CYAN),
        EntityPropertyChoice(DYE_PURPLE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_PURPLE),
        EntityPropertyChoice(DYE_BLUE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_BLUE),
        EntityPropertyChoice(DYE_BROWN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_BROWN),
        EntityPropertyChoice(DYE_GREEN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_GREEN),
        EntityPropertyChoice(DYE_RED, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_RED),
        EntityPropertyChoice(DYE_BLACK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_DYE_BLACK),
    )

    private val catChoices = listOf(
        EntityPropertyChoice(CAT_TABBY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_TABBY),
        EntityPropertyChoice(CAT_BLACK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_BLACK),
        EntityPropertyChoice(CAT_RED, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_RED),
        EntityPropertyChoice(CAT_SIAMESE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_SIAMESE),
        EntityPropertyChoice(CAT_BRITISH_SHORTHAIR, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_BRITISH_SHORTHAIR),
        EntityPropertyChoice(CAT_CALICO, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_CALICO),
        EntityPropertyChoice(CAT_PERSIAN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_PERSIAN),
        EntityPropertyChoice(CAT_RAGDOLL, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_RAGDOLL),
        EntityPropertyChoice(CAT_WHITE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_WHITE),
        EntityPropertyChoice(CAT_JELLIE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_JELLIE),
        EntityPropertyChoice(CAT_ALL_BLACK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_CAT_ALL_BLACK),
    )

    private val axolotlChoices = listOf(
        EntityPropertyChoice(AXOLOTL_LUCY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_AXOLOTL_LUCY),
        EntityPropertyChoice(AXOLOTL_WILD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_AXOLOTL_WILD),
        EntityPropertyChoice(AXOLOTL_GOLD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_AXOLOTL_GOLD),
        EntityPropertyChoice(AXOLOTL_CYAN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_AXOLOTL_CYAN),
        EntityPropertyChoice(AXOLOTL_BLUE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_AXOLOTL_BLUE),
    )

    private val foxChoices = listOf(
        EntityPropertyChoice(FOX_RED, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FOX_RED),
        EntityPropertyChoice(FOX_SNOW, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FOX_SNOW),
    )

    private val frogChoices = listOf(
        EntityPropertyChoice(FROG_TEMPERATE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FROG_TEMPERATE),
        EntityPropertyChoice(FROG_WARM, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FROG_WARM),
        EntityPropertyChoice(FROG_COLD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FROG_COLD),
    )

    private val villagerProfessionChoices = listOf(
        EntityPropertyChoice(VILLAGER_PROFESSION_NONE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_NONE),
        EntityPropertyChoice(VILLAGER_PROFESSION_ARMORER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_ARMORER),
        EntityPropertyChoice(VILLAGER_PROFESSION_BUTCHER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_BUTCHER),
        EntityPropertyChoice(VILLAGER_PROFESSION_CARTOGRAPHER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_CARTOGRAPHER),
        EntityPropertyChoice(VILLAGER_PROFESSION_CLERIC, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_CLERIC),
        EntityPropertyChoice(VILLAGER_PROFESSION_FARMER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_FARMER),
        EntityPropertyChoice(VILLAGER_PROFESSION_FISHERMAN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_FISHERMAN),
        EntityPropertyChoice(VILLAGER_PROFESSION_FLETCHER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_FLETCHER),
        EntityPropertyChoice(VILLAGER_PROFESSION_LEATHERWORKER, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_LEATHERWORKER),
        EntityPropertyChoice(VILLAGER_PROFESSION_LIBRARIAN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_LIBRARIAN),
        EntityPropertyChoice(VILLAGER_PROFESSION_MASON, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_MASON),
        EntityPropertyChoice(VILLAGER_PROFESSION_NITWIT, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_NITWIT),
        EntityPropertyChoice(VILLAGER_PROFESSION_SHEPHERD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_SHEPHERD),
        EntityPropertyChoice(VILLAGER_PROFESSION_TOOLSMITH, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_TOOLSMITH),
        EntityPropertyChoice(VILLAGER_PROFESSION_WEAPONSMITH, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_PROFESSION_WEAPONSMITH),
    )

    private val villagerTypeChoices = listOf(
        EntityPropertyChoice(VILLAGER_TYPE_DESERT, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_TYPE_DESERT),
        EntityPropertyChoice(VILLAGER_TYPE_JUNGLE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_TYPE_JUNGLE),
        EntityPropertyChoice(VILLAGER_TYPE_PLAINS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_TYPE_PLAINS),
        EntityPropertyChoice(VILLAGER_TYPE_SAVANNA, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_TYPE_SAVANNA),
        EntityPropertyChoice(VILLAGER_TYPE_SNOW, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_TYPE_SNOW),
        EntityPropertyChoice(VILLAGER_TYPE_SWAMP, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_TYPE_SWAMP),
        EntityPropertyChoice(VILLAGER_TYPE_TAIGA, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VILLAGER_TYPE_TAIGA),
    )

    private val mooshroomChoices = listOf(
        EntityPropertyChoice(MOOSHROOM_RED, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MOOSHROOM_RED),
        EntityPropertyChoice(MOOSHROOM_BROWN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_MOOSHROOM_BROWN),
    )

    private val horseColorChoices = listOf(
        EntityPropertyChoice(HORSE_WHITE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_WHITE),
        EntityPropertyChoice(HORSE_CREAMY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_CREAMY),
        EntityPropertyChoice(HORSE_CHESTNUT, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_CHESTNUT),
        EntityPropertyChoice(HORSE_BROWN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_BROWN),
        EntityPropertyChoice(HORSE_BLACK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_BLACK),
        EntityPropertyChoice(HORSE_GRAY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_GRAY),
        EntityPropertyChoice(HORSE_DARK_BROWN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_DARK_BROWN),
    )

    private val horseStyleChoices = listOf(
        EntityPropertyChoice(HORSE_STYLE_NONE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_STYLE_NONE),
        EntityPropertyChoice(HORSE_STYLE_WHITE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_STYLE_WHITE),
        EntityPropertyChoice(HORSE_STYLE_WHITEFIELD, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_STYLE_WHITEFIELD),
        EntityPropertyChoice(HORSE_STYLE_WHITE_DOTS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_STYLE_WHITE_DOTS),
        EntityPropertyChoice(HORSE_STYLE_BLACK_DOTS, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_HORSE_STYLE_BLACK_DOTS),
    )

    private val llamaChoices = listOf(
        EntityPropertyChoice(LLAMA_CREAMY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_LLAMA_CREAMY),
        EntityPropertyChoice(LLAMA_WHITE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_LLAMA_WHITE),
        EntityPropertyChoice(LLAMA_BROWN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_LLAMA_BROWN),
        EntityPropertyChoice(LLAMA_GRAY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_LLAMA_GRAY),
    )

    private val parrotChoices = listOf(
        EntityPropertyChoice(PARROT_RED, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARROT_RED),
        EntityPropertyChoice(PARROT_BLUE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARROT_BLUE),
        EntityPropertyChoice(PARROT_GREEN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARROT_GREEN),
        EntityPropertyChoice(PARROT_CYAN, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARROT_CYAN),
        EntityPropertyChoice(PARROT_GRAY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PARROT_GRAY),
    )

    private val boatChoices = listOf(
        EntityPropertyChoice(BOAT_OAK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BOAT_OAK),
        EntityPropertyChoice(BOAT_SPRUCE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BOAT_SPRUCE),
        EntityPropertyChoice(BOAT_BIRCH, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BOAT_BIRCH),
        EntityPropertyChoice(BOAT_JUNGLE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BOAT_JUNGLE),
        EntityPropertyChoice(BOAT_ACACIA, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BOAT_ACACIA),
        EntityPropertyChoice(BOAT_DARK_OAK, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BOAT_DARK_OAK),
        EntityPropertyChoice(BOAT_MANGROVE, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BOAT_MANGROVE),
        EntityPropertyChoice(BOAT_CHERRY, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BOAT_CHERRY),
        EntityPropertyChoice(BOAT_BAMBOO, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_BOAT_BAMBOO),
    )

    private val pufferChoices = listOf(
        EntityPropertyChoice(PUFFER_UNPUFFED, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PUFFER_UNPUFFED),
        EntityPropertyChoice(PUFFER_HALF_PUFFED, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PUFFER_HALF_PUFFED),
        EntityPropertyChoice(PUFFER_FULLY_PUFFED, KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PUFFER_FULLY_PUFFED),
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

    // ゾンビ系の幼体設定対象です。Ageableの幼体設定とは排他的に表示します。
    private val ZOMBIE_BABY_TYPES = setOf(
        EntityType.ZOMBIE.name,
        EntityType.HUSK.name,
        EntityType.DROWNED.name,
        EntityType.ZOMBIE_VILLAGER.name,
    )

    // Sittable能力を持つ代表種別です。実行時は `is Sittable` を最終判定とします。
    private val SITTABLE_TYPES = setOf(
        EntityType.WOLF.name,
        EntityType.CAT.name,
        EntityType.PARROT.name,
        EntityType.FOX.name,
    )

    // 非ゾンビ系のAgeable代表種別です。和集合表示を避けるため、幼体設定が
    // 意味を持つ主要種別に限定します。実行時は `is Ageable` を最終判定とします。
    private val AGEABLE_BABY_TYPES = setOf(
        EntityType.COW.name,
        EntityType.PIG.name,
        EntityType.SHEEP.name,
        EntityType.CHICKEN.name,
        EntityType.VILLAGER.name,
        EntityType.WOLF.name,
        EntityType.CAT.name,
        EntityType.HORSE.name,
        EntityType.DONKEY.name,
        EntityType.MULE.name,
        EntityType.LLAMA.name,
        EntityType.TRADER_LLAMA.name,
        EntityType.RABBIT.name,
        EntityType.FOX.name,
        EntityType.FROG.name,
        EntityType.AXOLOTL.name,
        EntityType.BEE.name,
        EntityType.GOAT.name,
        EntityType.PANDA.name,
        EntityType.PARROT.name,
        EntityType.TURTLE.name,
        EntityType.OCELOT.name,
        EntityType.POLAR_BEAR.name,
        EntityType.MOOSHROOM.name,
        EntityType.STRIDER.name,
        EntityType.HOGLIN.name,
        EntityType.PIGLIN.name,
        EntityType.SNIFFER.name,
        EntityType.CAMEL.name,
        EntityType.ARMADILLO.name,
        EntityType.SKELETON_HORSE.name,
        EntityType.ZOMBIE_HORSE.name,
    )

    // ボート系のEntityType集合です。現行Paperでは木材種別ごとに分かれています。
    // Boat.Type（9種）とは別軸であり、ここでは対象絞り込みに使います。
    private val BOAT_TYPES = setOf(
        EntityType.OAK_BOAT.name,
        EntityType.OAK_CHEST_BOAT.name,
        EntityType.SPRUCE_BOAT.name,
        EntityType.SPRUCE_CHEST_BOAT.name,
        EntityType.BIRCH_BOAT.name,
        EntityType.BIRCH_CHEST_BOAT.name,
        EntityType.JUNGLE_BOAT.name,
        EntityType.JUNGLE_CHEST_BOAT.name,
        EntityType.ACACIA_BOAT.name,
        EntityType.ACACIA_CHEST_BOAT.name,
        EntityType.DARK_OAK_BOAT.name,
        EntityType.DARK_OAK_CHEST_BOAT.name,
        EntityType.MANGROVE_BOAT.name,
        EntityType.MANGROVE_CHEST_BOAT.name,
        EntityType.CHERRY_BOAT.name,
        EntityType.CHERRY_CHEST_BOAT.name,
        EntityType.PALE_OAK_BOAT.name,
        EntityType.PALE_OAK_CHEST_BOAT.name,
        EntityType.BAMBOO_RAFT.name,
        EntityType.BAMBOO_CHEST_RAFT.name,
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
            PROPERTY_ALLOW_FLIGHT, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.PLAYER.name),
            optionKey = com.awabi2048.ccsystem.api.localization.generated.ChanponEnvironmentKeys
                .CHANPON_ENVIRONMENT_FLIGHT_DISPLAY,
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
        // 以下はエンティティ固有項目です。対象確定時のみ表示し、和集合表示は行いません。
        // 能力横断項目は対応EntityType集合で限定し、実行時のcapability判定を最終判定とします。
        EntityPropertyDefinition(
            PROPERTY_AGEABLE_BABY, EntityPropertyValueKind.BOOLEAN,
            entityTypes = AGEABLE_BABY_TYPES,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_AGEABLE_BABY,
        ),
        EntityPropertyDefinition(
            PROPERTY_SHEEP_COLOR, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.SHEEP.name),
            enumChoices = dyeChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_SHEEP_COLOR,
        ),
        EntityPropertyDefinition(
            PROPERTY_SHEEP_SHEARED, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.SHEEP.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_SHEEP_SHEARED,
        ),
        EntityPropertyDefinition(
            PROPERTY_SLIME_SIZE, EntityPropertyValueKind.INTEGER,
            entityTypes = setOf(EntityType.SLIME.name, EntityType.MAGMA_CUBE.name),
            intRange = 1..100,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_SLIME_SIZE,
        ),
        EntityPropertyDefinition(
            PROPERTY_CREEPER_POWERED, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.CREEPER.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_CREEPER_POWERED,
        ),
        EntityPropertyDefinition(
            PROPERTY_SITTABLE_SITTING, EntityPropertyValueKind.BOOLEAN,
            entityTypes = SITTABLE_TYPES,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_SITTABLE_SITTING,
        ),
        EntityPropertyDefinition(
            PROPERTY_COLLAR_COLOR, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.WOLF.name, EntityType.CAT.name),
            enumChoices = dyeChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_COLLAR_COLOR,
        ),
        EntityPropertyDefinition(
            PROPERTY_CAT_TYPE, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.CAT.name),
            enumChoices = catChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_CAT_TYPE,
        ),
        EntityPropertyDefinition(
            PROPERTY_AXOLOTL_VARIANT, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.AXOLOTL.name),
            enumChoices = axolotlChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_AXOLOTL_VARIANT,
        ),
        EntityPropertyDefinition(
            PROPERTY_FOX_TYPE, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.FOX.name),
            enumChoices = foxChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_FOX_TYPE,
        ),
        EntityPropertyDefinition(
            PROPERTY_FROG_VARIANT, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.FROG.name),
            enumChoices = frogChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_FROG_VARIANT,
        ),
        EntityPropertyDefinition(
            PROPERTY_VILLAGER_PROFESSION, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.VILLAGER.name),
            enumChoices = villagerProfessionChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_VILLAGER_PROFESSION,
        ),
        EntityPropertyDefinition(
            PROPERTY_ZOMBIE_BABY, EntityPropertyValueKind.BOOLEAN,
            entityTypes = ZOMBIE_BABY_TYPES,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_ZOMBIE_BABY,
        ),
        EntityPropertyDefinition(
            PROPERTY_PIG_SADDLE, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.PIG.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_PIG_SADDLE,
        ),
        EntityPropertyDefinition(
            PROPERTY_PUFFER_STATE, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.PUFFERFISH.name),
            enumChoices = pufferChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_PUFFER_STATE,
        ),
        EntityPropertyDefinition(
            PROPERTY_VILLAGER_TYPE, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.VILLAGER.name),
            enumChoices = villagerTypeChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_VILLAGER_TYPE,
        ),
        EntityPropertyDefinition(
            PROPERTY_MOOSHROOM_VARIANT, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.MOOSHROOM.name),
            enumChoices = mooshroomChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_MOOSHROOM_VARIANT,
        ),
        EntityPropertyDefinition(
            PROPERTY_HORSE_COLOR, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.HORSE.name),
            enumChoices = horseColorChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_HORSE_COLOR,
        ),
        EntityPropertyDefinition(
            PROPERTY_HORSE_STYLE, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.HORSE.name),
            enumChoices = horseStyleChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_HORSE_STYLE,
        ),
        EntityPropertyDefinition(
            PROPERTY_LLAMA_COLOR, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.LLAMA.name, EntityType.TRADER_LLAMA.name),
            enumChoices = llamaChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_LLAMA_COLOR,
        ),
        EntityPropertyDefinition(
            PROPERTY_PARROT_VARIANT, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.PARROT.name),
            enumChoices = parrotChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_PARROT_VARIANT,
        ),
        EntityPropertyDefinition(
            PROPERTY_SHULKER_COLOR, EntityPropertyValueKind.ENUM,
            entityTypes = setOf(EntityType.SHULKER.name),
            enumChoices = dyeChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_SHULKER_COLOR,
        ),
        EntityPropertyDefinition(
            PROPERTY_BOAT_TYPE, EntityPropertyValueKind.ENUM,
            entityTypes = BOAT_TYPES,
            enumChoices = boatChoices,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_BOAT_TYPE,
        ),
        EntityPropertyDefinition(
            PROPERTY_BEE_NECTAR, EntityPropertyValueKind.BOOLEAN,
            entityTypes = setOf(EntityType.BEE.name),
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_BEE_NECTAR,
        ),
        EntityPropertyDefinition(
            PROPERTY_BEE_ANGER, EntityPropertyValueKind.INTEGER,
            entityTypes = setOf(EntityType.BEE.name),
            intRange = 0..72000,
            optionKey = KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_PROPERTY_BEE_ANGER,
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
     * issue #10 の召喚時個別設定の保存接頭辞です。
     *
     * SUMMONノードは複数項目を持つため、単一キーではなく項目ごとに
     * `summonData_<propertyId>` 形式のキーで保存します。既存Entity編集の
     * 単一項目形式（`entityDataPath`＋`propertyValue`）とは保存形が異なりますが、
     * 項目ID・型・検証・adapterは同一スキーマを共有し、二重定義しません。
     * 空値は未設定と同等に扱い、読み取り時に除外します。
     */
    const val SUMMON_DATA_PREFIX = "summonData_"

    /** 召喚時個別設定の保存キーを返します。 */
    fun summonDataKey(propertyId: String): String = "$SUMMON_DATA_PREFIX$propertyId"

    /** 保存キーから設定項目IDを取り出します。召喚時個別設定以外はnullです。 */
    fun summonDataIdOf(key: String): String? =
        key.removePrefix(SUMMON_DATA_PREFIX).takeIf { key.startsWith(SUMMON_DATA_PREFIX) && it.isNotBlank() }

    /**
     * 召喚時個別設定の一覧を返します。
     *
     * 空値は未設定と同等に扱い除外します。未知IDは除外せず呼び出し側
     * （検証層）が不正として扱います。順序は保存順を維持しません。
     */
    fun summonDataEntries(params: Map<String, String>): Map<String, String> =
        params.mapNotNull { (key, value) ->
            summonDataIdOf(key)?.takeIf { value.isNotBlank() }?.let { it to value }
        }.toMap()

    /**
     * 全安定IDを返します。
     *
     * issue #3 との対応表（`EntityPropertyAlignmentTest`）が読み出し側
     * レジストリとの衝突・分岐を検出するために公開します。IDの追加・改名は
     * #10 の共通化方針に従い、対応表の更新と合わせて行います。
     */
    fun ids(): Set<String> = byId.keys

    /**
     * 召喚種類IDから静的に確定できるEntityTypeを返します。
     *
     * Paper Registryはサーバー起動前のAPI単体テスト環境では初期化できないため、
     * 解決失敗時は不明として扱います。実行時と同じRegistryを使う点は維持します。
     * 不明時は過剰に制限せずruntime判定へ委ねます（#10・#18）。
     */
    fun knownSummonType(rawId: String): EntityType? {
        if (rawId.isBlank()) return null
        return runCatching {
            NamespacedKey.fromString(rawId)?.let { Registry.ENTITY_TYPE.get(it) }
        }.getOrNull()
    }

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
     * 種類ID文字列に対する静的対応状況を返します。
     *
     * 召喚種類のように文字列IDしかない場合に、Registryなしで判定します。
     * 種類集合で確定できる項目は静的に判定し、生存条件など名前だけでは
     * 確定できない項目はruntime-dependentとして実行時へ委ねます。
     * 未知IDは検証層の種類検査が別途扱うため、ここでは対象外にしません。
     */
    fun staticSupportByName(typeName: String?, propertyId: String): EntityPropertySupport {
        val definition = byId[propertyId] ?: return EntityPropertySupport.STATICALLY_UNSUPPORTED
        if (typeName.isNullOrBlank()) return EntityPropertySupport.RUNTIME_DEPENDENT
        if (definition.entityTypes != null) {
            val normalized = typeName.substringAfter(':').trim().uppercase()
            return if (normalized in definition.entityTypes) {
                EntityPropertySupport.STATICALLY_SUPPORTED
            } else {
                EntityPropertySupport.STATICALLY_UNSUPPORTED
            }
        }
        if (definition.requiresLiving) return EntityPropertySupport.RUNTIME_DEPENDENT
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
            EntityPropertyValueKind.INTEGER -> {
                val range = definition.intRange ?: return null
                raw.trim().toIntOrNull()?.takeIf { it in range }?.let(EntityPropertyValue::IntegerValue)
            }
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
        // 種別集合だけでは表現できない能力横断項目は、実体の型で最終判定します。
        return when (propertyId) {
            PROPERTY_AGEABLE_BABY -> entity is Ageable && entity !is Zombie
            PROPERTY_ALLOW_FLIGHT -> entity is Player
            PROPERTY_ZOMBIE_BABY -> entity is Zombie
            PROPERTY_SITTABLE_SITTING -> entity is Sittable
            PROPERTY_COLLAR_COLOR -> entity is CollarColorable
            PROPERTY_SHEEP_COLOR, PROPERTY_SHEEP_SHEARED -> entity is Sheep
            PROPERTY_SLIME_SIZE -> entity is Slime
            PROPERTY_CREEPER_POWERED -> entity is Creeper
            PROPERTY_CAT_TYPE -> entity is Cat
            PROPERTY_AXOLOTL_VARIANT -> entity is Axolotl
            PROPERTY_FOX_TYPE -> entity is Fox
            PROPERTY_FROG_VARIANT -> entity is Frog
            PROPERTY_VILLAGER_PROFESSION, PROPERTY_VILLAGER_TYPE -> entity is Villager
            PROPERTY_PIG_SADDLE -> entity is Steerable
            PROPERTY_PUFFER_STATE -> entity is PufferFish
            PROPERTY_MOOSHROOM_VARIANT -> entity is MushroomCow
            PROPERTY_HORSE_COLOR, PROPERTY_HORSE_STYLE -> entity is Horse
            PROPERTY_LLAMA_COLOR -> entity is Llama
            PROPERTY_PARROT_VARIANT -> entity is Parrot
            PROPERTY_SHULKER_COLOR -> entity is Shulker
            PROPERTY_BOAT_TYPE -> entity is Boat
            PROPERTY_BEE_NECTAR, PROPERTY_BEE_ANGER -> entity is Bee
            else -> true
        }
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
        PROPERTY_ALLOW_FLIGHT -> (entity as? Player)?.let { it.allowFlight = value; true } ?: false
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
        // エンティティ固有の真偽値です。適用前に[supports]で可否を判定してください。
        PROPERTY_AGEABLE_BABY -> (entity as? Ageable)?.let {
            if (value) it.setBaby() else it.setAdult()
            true
        } ?: false
        PROPERTY_SHEEP_SHEARED -> (entity as? Sheep)?.let { it.isSheared = value; true } ?: false
        PROPERTY_CREEPER_POWERED -> (entity as? Creeper)?.let { it.isPowered = value; true } ?: false
        PROPERTY_SITTABLE_SITTING -> (entity as? Sittable)?.let { it.isSitting = value; true } ?: false
        PROPERTY_ZOMBIE_BABY -> (entity as? Zombie)?.let {
            // boolean引数版は非推奨のため、AgeableのsetBaby／setAdultを使い分けます。
            if (value) it.setBaby() else it.setAdult()
            true
        } ?: false
        PROPERTY_PIG_SADDLE -> (entity as? Steerable)?.let { it.setSaddle(value); true } ?: false
        PROPERTY_BEE_NECTAR -> (entity as? Bee)?.let { it.setHasNectar(value); true } ?: false
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
        // DyeColor共用選択肢です。羊・首輪・シュルカーで共有します。
        if (propertyId == PROPERTY_SHEEP_COLOR) {
            val sheep = entity as? Sheep ?: return false
            val color = dyeColor(choiceId) ?: return false
            return runCatching { sheep.color = color; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_COLLAR_COLOR) {
            val collar = entity as? CollarColorable ?: return false
            val color = dyeColor(choiceId) ?: return false
            return runCatching { collar.collarColor = color; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_SHULKER_COLOR) {
            val shulker = entity as? Shulker ?: return false
            val color = dyeColor(choiceId) ?: return false
            return runCatching { shulker.color = color; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_CAT_TYPE) {
            val cat = entity as? Cat ?: return false
            val type = catType(choiceId) ?: return false
            return runCatching { cat.catType = type; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_AXOLOTL_VARIANT) {
            val axolotl = entity as? Axolotl ?: return false
            val variant = axolotlVariant(choiceId) ?: return false
            return runCatching { axolotl.variant = variant; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_FOX_TYPE) {
            val fox = entity as? Fox ?: return false
            val type = foxType(choiceId) ?: return false
            return runCatching { fox.foxType = type; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_FROG_VARIANT) {
            val frog = entity as? Frog ?: return false
            val variant = frogVariant(choiceId) ?: return false
            return runCatching { frog.variant = variant; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_VILLAGER_PROFESSION) {
            val villager = entity as? Villager ?: return false
            val profession = villagerProfession(choiceId) ?: return false
            return runCatching { villager.profession = profession; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_VILLAGER_TYPE) {
            val villager = entity as? Villager ?: return false
            val type = villagerType(choiceId) ?: return false
            return runCatching { villager.villagerType = type; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_MOOSHROOM_VARIANT) {
            val mooshroom = entity as? MushroomCow ?: return false
            val variant = mooshroomVariant(choiceId) ?: return false
            return runCatching { mooshroom.variant = variant; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_HORSE_COLOR) {
            val horse = entity as? Horse ?: return false
            val color = horseColor(choiceId) ?: return false
            return runCatching { horse.color = color; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_HORSE_STYLE) {
            val horse = entity as? Horse ?: return false
            val style = horseStyle(choiceId) ?: return false
            return runCatching { horse.style = style; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_LLAMA_COLOR) {
            val llama = entity as? Llama ?: return false
            val color = llamaColor(choiceId) ?: return false
            return runCatching { llama.color = color; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_PARROT_VARIANT) {
            val parrot = entity as? Parrot ?: return false
            val variant = parrotVariant(choiceId) ?: return false
            return runCatching { parrot.variant = variant; true }.getOrDefault(false)
        }
        if (propertyId == PROPERTY_BOAT_TYPE) {
            val boat = entity as? Boat ?: return false
            val type = boatType(choiceId) ?: return false
            // Boat.Type系API自体が非推奨ですが代替手段がないため意図的に使用します。
            @Suppress("DEPRECATION")
            val applied = runCatching { boat.boatType = type; true }.getOrDefault(false)
            return applied
        }
        if (propertyId == PROPERTY_PUFFER_STATE) {
            val puffer = entity as? PufferFish ?: return false
            val state = when (choiceId) {
                PUFFER_UNPUFFED -> 0
                PUFFER_HALF_PUFFED -> 1
                PUFFER_FULLY_PUFFED -> 2
                else -> return false
            }
            return runCatching { puffer.puffState = state; true }.getOrDefault(false)
        }
        return false
    }

    /**
     * 整数値を適用します。適用前に[supports]で可否を判定してください。
     *
     * 範囲検証は [parse] で担保し、ここではBukkit呼び出しの成否だけを扱います。
     */
    fun applyInteger(entity: Entity, propertyId: String, value: Int): Boolean = when (propertyId) {
        PROPERTY_SLIME_SIZE -> (entity as? Slime)?.let {
            runCatching { it.size = value; true }.getOrDefault(false)
        } ?: false
        PROPERTY_BEE_ANGER -> (entity as? Bee)?.let {
            runCatching { it.anger = value; true }.getOrDefault(false)
        } ?: false
        else -> false
    }

    /** 選択肢IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun dyeColor(choiceId: String): DyeColor? = when (choiceId) {
        DYE_WHITE -> DyeColor.WHITE
        DYE_ORANGE -> DyeColor.ORANGE
        DYE_MAGENTA -> DyeColor.MAGENTA
        DYE_LIGHT_BLUE -> DyeColor.LIGHT_BLUE
        DYE_YELLOW -> DyeColor.YELLOW
        DYE_LIME -> DyeColor.LIME
        DYE_PINK -> DyeColor.PINK
        DYE_GRAY -> DyeColor.GRAY
        DYE_LIGHT_GRAY -> DyeColor.LIGHT_GRAY
        DYE_CYAN -> DyeColor.CYAN
        DYE_PURPLE -> DyeColor.PURPLE
        DYE_BLUE -> DyeColor.BLUE
        DYE_BROWN -> DyeColor.BROWN
        DYE_GREEN -> DyeColor.GREEN
        DYE_RED -> DyeColor.RED
        DYE_BLACK -> DyeColor.BLACK
        else -> null
    }

    /** 猫種類IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun catType(choiceId: String): Cat.Type? = when (choiceId) {
        CAT_TABBY -> Cat.Type.TABBY
        CAT_BLACK -> Cat.Type.BLACK
        CAT_RED -> Cat.Type.RED
        CAT_SIAMESE -> Cat.Type.SIAMESE
        CAT_BRITISH_SHORTHAIR -> Cat.Type.BRITISH_SHORTHAIR
        CAT_CALICO -> Cat.Type.CALICO
        CAT_PERSIAN -> Cat.Type.PERSIAN
        CAT_RAGDOLL -> Cat.Type.RAGDOLL
        CAT_WHITE -> Cat.Type.WHITE
        CAT_JELLIE -> Cat.Type.JELLIE
        CAT_ALL_BLACK -> Cat.Type.ALL_BLACK
        else -> null
    }

    /** ウーパールーパー種類IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun axolotlVariant(choiceId: String): Axolotl.Variant? = when (choiceId) {
        AXOLOTL_LUCY -> Axolotl.Variant.LUCY
        AXOLOTL_WILD -> Axolotl.Variant.WILD
        AXOLOTL_GOLD -> Axolotl.Variant.GOLD
        AXOLOTL_CYAN -> Axolotl.Variant.CYAN
        AXOLOTL_BLUE -> Axolotl.Variant.BLUE
        else -> null
    }

    /** キツネ種類IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun foxType(choiceId: String): Fox.Type? = when (choiceId) {
        FOX_RED -> Fox.Type.RED
        FOX_SNOW -> Fox.Type.SNOW
        else -> null
    }

    /** カエル種類IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun frogVariant(choiceId: String): Frog.Variant? = when (choiceId) {
        FROG_TEMPERATE -> Frog.Variant.TEMPERATE
        FROG_WARM -> Frog.Variant.WARM
        FROG_COLD -> Frog.Variant.COLD
        else -> null
    }

    /** 村人職業IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun villagerProfession(choiceId: String): Villager.Profession? = when (choiceId) {
        VILLAGER_PROFESSION_NONE -> Villager.Profession.NONE
        VILLAGER_PROFESSION_ARMORER -> Villager.Profession.ARMORER
        VILLAGER_PROFESSION_BUTCHER -> Villager.Profession.BUTCHER
        VILLAGER_PROFESSION_CARTOGRAPHER -> Villager.Profession.CARTOGRAPHER
        VILLAGER_PROFESSION_CLERIC -> Villager.Profession.CLERIC
        VILLAGER_PROFESSION_FARMER -> Villager.Profession.FARMER
        VILLAGER_PROFESSION_FISHERMAN -> Villager.Profession.FISHERMAN
        VILLAGER_PROFESSION_FLETCHER -> Villager.Profession.FLETCHER
        VILLAGER_PROFESSION_LEATHERWORKER -> Villager.Profession.LEATHERWORKER
        VILLAGER_PROFESSION_LIBRARIAN -> Villager.Profession.LIBRARIAN
        VILLAGER_PROFESSION_MASON -> Villager.Profession.MASON
        VILLAGER_PROFESSION_NITWIT -> Villager.Profession.NITWIT
        VILLAGER_PROFESSION_SHEPHERD -> Villager.Profession.SHEPHERD
        VILLAGER_PROFESSION_TOOLSMITH -> Villager.Profession.TOOLSMITH
        VILLAGER_PROFESSION_WEAPONSMITH -> Villager.Profession.WEAPONSMITH
        else -> null
    }

    /** 村人見た目IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun villagerType(choiceId: String): Villager.Type? = when (choiceId) {
        VILLAGER_TYPE_DESERT -> Villager.Type.DESERT
        VILLAGER_TYPE_JUNGLE -> Villager.Type.JUNGLE
        VILLAGER_TYPE_PLAINS -> Villager.Type.PLAINS
        VILLAGER_TYPE_SAVANNA -> Villager.Type.SAVANNA
        VILLAGER_TYPE_SNOW -> Villager.Type.SNOW
        VILLAGER_TYPE_SWAMP -> Villager.Type.SWAMP
        VILLAGER_TYPE_TAIGA -> Villager.Type.TAIGA
        else -> null
    }

    /** ムーシュルーム種類IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun mooshroomVariant(choiceId: String): MushroomCow.Variant? = when (choiceId) {
        MOOSHROOM_RED -> MushroomCow.Variant.RED
        MOOSHROOM_BROWN -> MushroomCow.Variant.BROWN
        else -> null
    }

    /** 馬毛色IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun horseColor(choiceId: String): Horse.Color? = when (choiceId) {
        HORSE_WHITE -> Horse.Color.WHITE
        HORSE_CREAMY -> Horse.Color.CREAMY
        HORSE_CHESTNUT -> Horse.Color.CHESTNUT
        HORSE_BROWN -> Horse.Color.BROWN
        HORSE_BLACK -> Horse.Color.BLACK
        HORSE_GRAY -> Horse.Color.GRAY
        HORSE_DARK_BROWN -> Horse.Color.DARK_BROWN
        else -> null
    }

    /** 馬模様IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun horseStyle(choiceId: String): Horse.Style? = when (choiceId) {
        HORSE_STYLE_NONE -> Horse.Style.NONE
        HORSE_STYLE_WHITE -> Horse.Style.WHITE
        HORSE_STYLE_WHITEFIELD -> Horse.Style.WHITEFIELD
        HORSE_STYLE_WHITE_DOTS -> Horse.Style.WHITE_DOTS
        HORSE_STYLE_BLACK_DOTS -> Horse.Style.BLACK_DOTS
        else -> null
    }

    /** ラマ色IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun llamaColor(choiceId: String): Llama.Color? = when (choiceId) {
        LLAMA_CREAMY -> Llama.Color.CREAMY
        LLAMA_WHITE -> Llama.Color.WHITE
        LLAMA_BROWN -> Llama.Color.BROWN
        LLAMA_GRAY -> Llama.Color.GRAY
        else -> null
    }

    /** オウム色IDとBukkitの値の対応です。不明IDはnullとします。 */
    fun parrotVariant(choiceId: String): Parrot.Variant? = when (choiceId) {
        PARROT_RED -> Parrot.Variant.RED
        PARROT_BLUE -> Parrot.Variant.BLUE
        PARROT_GREEN -> Parrot.Variant.GREEN
        PARROT_CYAN -> Parrot.Variant.CYAN
        PARROT_GRAY -> Parrot.Variant.GRAY
        else -> null
    }

    /**
     * ボート種類IDとBukkitの値の対応です。不明IDはnullとします。
     *
     * Boat.Type自体がPaper側で非推奨ですが、木材種別ごとのEntityType変換では
     * 既存個体の種類変更ができないため、代替手段がありません。意図的に使用します。
     */
    @Suppress("DEPRECATION")
    fun boatType(choiceId: String): Boat.Type? = when (choiceId) {
        BOAT_OAK -> Boat.Type.OAK
        BOAT_SPRUCE -> Boat.Type.SPRUCE
        BOAT_BIRCH -> Boat.Type.BIRCH
        BOAT_JUNGLE -> Boat.Type.JUNGLE
        BOAT_ACACIA -> Boat.Type.ACACIA
        BOAT_DARK_OAK -> Boat.Type.DARK_OAK
        BOAT_MANGROVE -> Boat.Type.MANGROVE
        BOAT_CHERRY -> Boat.Type.CHERRY
        BOAT_BAMBOO -> Boat.Type.BAMBOO
        else -> null
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
