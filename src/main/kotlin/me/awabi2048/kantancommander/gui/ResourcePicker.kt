package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.model.ParticleSettings
import org.bukkit.Material

/**
 * issue #28 のID候補選択の種別です。
 *
 * Dialogの候補検索とGesture Resource Pickerで共有し、種別が増えた場合は
 * この共通仕組みへ追加します。
 */
enum class GestureResourcePickerKind {
    ENTITY_TYPE,
    PARTICLE,
    EFFECT,
    SOUND,
}

/**
 * issue #28 の候補データ・表示解決の境界です。
 *
 * Registry由来の一覧取得は [CommandDialogSpecs.registeredIds] を正本とし、
 * ここでは表示用の種別対応・ページング・アイコン解決だけを扱います。
 * アイコン解決は一覧UI本体から分離した種別ごとのResolverです。
 * BukkitのRegistryへ触れないため、単体テストで検証します。
 */
object ResourcePicker {
    /** 1ページの列数・行数です。正方形小型アイコンを6〜8列×3〜4行へ収めます。 */
    const val COLUMNS = 8
    const val ROWS = 3

    /** 1ページの件数です。 */
    const val PAGE_SIZE = COLUMNS * ROWS

    /** 候補選択の対象になる設定項目かを返します。 */
    fun kindForFieldKey(fieldKey: String): GestureResourcePickerKind? = when (fieldKey) {
        "entity", "entityType" -> GestureResourcePickerKind.ENTITY_TYPE
        ParticleSettings.PARAM_PARTICLE -> GestureResourcePickerKind.PARTICLE
        "sound" -> GestureResourcePickerKind.SOUND
        "effect" -> GestureResourcePickerKind.EFFECT
        else -> null
    }

    /** 候補IDの表示名です。ホバーにNamespaced IDと併せて表示します。 */
    fun displayName(kind: GestureResourcePickerKind, id: String): String {
        // パーティクルIDは大文字のため、小文字へ正規化してから整形します。
        val bare = id.substringAfterLast(':').substringAfterLast('.').lowercase()
        return bare.split('_', '-', ' ').joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }
    }

    /** 全ページ数です。空一覧は1ページとして扱います。 */
    fun pageCount(total: Int): Int = maxOf((total + PAGE_SIZE - 1) / PAGE_SIZE, 1)

    /** 指定ページの候補IDです。範囲外は空列を返します。 */
    fun pageItems(ids: List<String>, page: Int): List<String> {
        if (page < 0) return emptyList()
        val from = page * PAGE_SIZE
        if (from >= ids.size) return emptyList()
        return ids.drop(from).take(PAGE_SIZE)
    }
}

/**
 * issue #28 の候補アイコン解決です。
 *
 * 候補IDから表示用素材を解決します。直接対応する素材がない種類は
 * 汎用フォールバックを使い、解決不能な候補にも表示を保証します。
 * Material enumだけを扱うため、サーバーなしで検証します。
 */
object ResourcePickerIcons {
    /** 種別ごとの汎用フォールバックです。 */
    fun fallback(kind: GestureResourcePickerKind): Material = when (kind) {
        GestureResourcePickerKind.ENTITY_TYPE -> Material.EGG
        GestureResourcePickerKind.PARTICLE -> Material.FIREWORK_STAR
        GestureResourcePickerKind.EFFECT -> Material.POTION
        GestureResourcePickerKind.SOUND -> Material.NOTE_BLOCK
    }

    /** 候補IDの表示素材を解決します。解決できない場合はフォールバックです。 */
    fun resolve(kind: GestureResourcePickerKind, id: String): Material =
        when (kind) {
            GestureResourcePickerKind.ENTITY_TYPE -> entityIcon(id)
            GestureResourcePickerKind.PARTICLE -> particleIcon(id)
            GestureResourcePickerKind.EFFECT -> effectIcon(id)
            GestureResourcePickerKind.SOUND -> soundIcon(id)
        } ?: fallback(kind)

    private fun entityIcon(id: String): Material? {
        val path = id.substringAfter(':').uppercase()
        // 対応するスポーンエッグがあれば使います。
        runCatching { Material.valueOf("${path}_SPAWN_EGG") }.getOrNull()?.let { return it }
        return when (path) {
            "ARMOR_STAND" -> Material.ARMOR_STAND
            "ITEM_FRAME", "GLOW_ITEM_FRAME" -> Material.ITEM_FRAME
            "MINECART", "CHEST_MINECART", "FURNACE_MINECART", "TNT_MINECART",
            "HOPPER_MINECART", "COMMAND_BLOCK_MINECART", "SPAWNER_MINECART",
            -> Material.MINECART
            "MINECART_CHEST", "MINECART_FURNACE", "MINECART_TNT", "MINECART_HOPPER",
            "MINECART_COMMAND_BLOCK", "MINECART_SPAWNER",
            -> Material.MINECART
            "ENDER_DRAGON" -> Material.DRAGON_EGG
            "WITHER" -> Material.NETHER_STAR
            "PLAYER", "FISHING_BOBBER" -> Material.PLAYER_HEAD
            // Mannequinにスポーンエッグは存在しないため、発見可能性のために頭で表示します。
            "MANNEQUIN" -> Material.PLAYER_HEAD
            "LEASH_KNOT", "LEASH_HITCH" -> Material.LEAD
            "PAINTING" -> Material.PAINTING
            "ARROW", "SPECTRAL_ARROW" -> Material.ARROW
            "TRIDENT" -> Material.TRIDENT
            "EGG" -> Material.EGG
            "SNOWBALL" -> Material.SNOWBALL
            "FIREBALL", "SMALL_FIREBALL", "DRAGON_FIREBALL" -> Material.FIRE_CHARGE
            "ENDER_PEARL" -> Material.ENDER_PEARL
            "ENDER_CRYSTAL", "END_CRYSTAL" -> Material.END_CRYSTAL
            "TNT" -> Material.TNT
            "FALLING_BLOCK" -> Material.SAND
            "BOAT", "CHEST_BOAT", "ACACIA_BOAT", "BIRCH_BOAT", "DARK_OAK_BOAT",
            "JUNGLE_BOAT", "MANGROVE_BOAT", "OAK_BOAT", "SPRUCE_BOAT",
            -> Material.OAK_BOAT
            else -> null
        }
    }

    private fun particleIcon(id: String): Material? {
        val path = id.substringAfter(':').uppercase()
        return when (path) {
            "FLAME", "SMALL_FLAME", "LAVA", "CAMPFIRE_COSY_SMOKE", "CAMPFIRE_SIGNAL_SMOKE" -> Material.BLAZE_POWDER
            "SMOKE", "LARGE_SMOKE", "POOF", "CLOUD" -> Material.GUNPOWDER
            "BUBBLE", "BUBBLE_POP", "BUBBLE_COLUMN_UP", "SPLASH", "FISHING", "DRIPPING_WATER",
            "FALLING_WATER", "RAIN",
            -> Material.WATER_BUCKET
            "ENCHANT", "ENCHANTED_HIT", "WITCH" -> Material.ENCHANTED_BOOK
            "CRIT", "CRIT_MAGIC", "SWEEP_ATTACK", "DAMAGE_INDICATOR" -> Material.IRON_SWORD
            "HEART", "VILLAGER_HAPPY" -> Material.POPPY
            "VILLAGER_ANGRY" -> Material.BARRIER
            "NOTE" -> Material.NOTE_BLOCK
            "PORTAL", "REVERSE_PORTAL", "END_ROD" -> Material.ENDER_PEARL
            "SPELL", "SPELL_MOB", "SPELL_MOB_AMBIENT", "SPELL_WITCH", "SPELL_INSTANT" -> Material.POTION
            "REDSTONE", "DUST" -> Material.REDSTONE
            "SLIME" -> Material.SLIME_BALL
            "SNOWBALL", "SNOWFLAKE", "SNOW_SHOVEL" -> Material.SNOWBALL
            "FIREWORK", "FIREWORKS_SPARK", "FLASH" -> Material.FIREWORK_ROCKET
            "TOTEM_OF_UNDYING", "TOTEM" -> Material.TOTEM_OF_UNDYING
            "SOUL", "SOUL_FIRE_FLAME", "SNIFFER_EGG", "SCULK_SOUL", "SCULK_CHARGE",
            "SCULK_CHARGE_POP", "SHRIEK", "SONIC_BOOM",
            -> Material.SOUL_LANTERN
            else -> null
        }
    }

    private fun effectIcon(id: String): Material? {
        val path = id.substringAfter(':').uppercase()
        return when (path) {
            "SPEED", "SWIFTNESS" -> Material.SUGAR
            "SLOWNESS", "SLOW" -> Material.SOUL_SAND
            "HASTE" -> Material.GOLDEN_PICKAXE
            "MINING_FATIGUE" -> Material.COBWEB
            "STRENGTH" -> Material.BLAZE_POWDER
            "INSTANT_HEALTH", "HEALTH_BOOST", "REGENERATION" -> Material.APPLE
            "INSTANT_DAMAGE", "HARM" -> Material.SPIDER_EYE
            "JUMP_BOOST", "JUMP" -> Material.RABBIT_FOOT
            "NAUSEA", "CONFUSION" -> Material.PUFFERFISH
            "RESISTANCE", "DAMAGE_RESISTANCE" -> Material.IRON_CHESTPLATE
            "FIRE_RESISTANCE" -> Material.MAGMA_CREAM
            "WATER_BREATHING" -> Material.PUFFERFISH
            "INVISIBILITY" -> Material.GLASS_BOTTLE
            "BLINDNESS" -> Material.BLACK_WOOL
            "NIGHT_VISION" -> Material.GOLDEN_CARROT
            "HUNGER" -> Material.ROTTEN_FLESH
            "WEAKNESS" -> Material.FERMENTED_SPIDER_EYE
            "POISON" -> Material.SPIDER_EYE
            "WITHER" -> Material.WITHER_SKELETON_SKULL
            "ABSORPTION" -> Material.GOLDEN_APPLE
            "SATURATION" -> Material.COOKED_BEEF
            "GLOWING" -> Material.GLOWSTONE_DUST
            "LEVITATION" -> Material.SHULKER_SHELL
            "LUCK" -> Material.RABBIT_FOOT
            "UNLUCK" -> Material.COAL
            "SLOW_FALLING" -> Material.FEATHER
            "CONDUIT_POWER" -> Material.NAUTILUS_SHELL
            "DOLPHINS_GRACE" -> Material.COD
            "BAD_OMEN", "OMINOUS" -> Material.OMINOUS_BOTTLE
            "RAID_OMEN", "TRIAL_OMEN" -> Material.TRIAL_KEY
            "DARKNESS" -> Material.SCULK
            "HERO_OF_THE_VILLAGE" -> Material.EMERALD
            "WIND_CHARGED" -> Material.WIND_CHARGE
            "WEAVING" -> Material.COBWEB
            "OOZING" -> Material.SLIME_BALL
            "INFESTED" -> Material.STONE
            else -> null
        }
    }

    private fun soundIcon(id: String): Material? {
        val path = id.substringAfter(':')
        return when {
            path.startsWith("music.") || path.startsWith("music_disc") -> Material.MUSIC_DISC_13
            path.startsWith("block.note_block.") -> Material.NOTE_BLOCK
            path.startsWith("block.") -> Material.JUKEBOX
            path.startsWith("entity.") -> Material.PLAYER_HEAD
            path.startsWith("item.") -> Material.CHEST
            path.startsWith("weather.") -> Material.WATER_BUCKET
            path.startsWith("ambient.") || path.startsWith("event.") -> Material.BELL
            else -> null
        }
    }
}
