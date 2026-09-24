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
        // v17改名キーを使います。一時対象指定の "entity" は一覧選択を使いません。
        "summonEntity", "entityType" -> GestureResourcePickerKind.ENTITY_TYPE
        ParticleSettings.PARAM_PARTICLE -> GestureResourcePickerKind.PARTICLE
        // PLAY_SOUNDは保存パラメータ "sound" を、GUI上の一括設定欄から操作します。
        "sound", "soundSetting" -> GestureResourcePickerKind.SOUND
        "effect" -> GestureResourcePickerKind.EFFECT
        else -> null
    }

    /** GUIの集約欄と、候補の実保存先を対応付けます。 */
    fun valueFieldKey(fieldKey: String): String = when (fieldKey) {
        "soundSetting" -> "sound"
        else -> fieldKey
    }

    /**
     * プリセットのパーティクル表示名（バニラID表記）です。
     *
     * 保存形式のBukkit enum名とバニラ `/particle` のIDは一致しない場合があるため、
     * 推測変換は行わず明示表で保持します。表外のIDは原文を返し、表示を欠落させません。
     * 保存・検証・実行には使わず、表示専用です。
     */
    fun particleDisplayId(id: String): String = PARTICLE_VANILLA_IDS[id] ?: id

    /** Bukkit enum名からバニラ `/particle` のID表記への明示対応です。 */
    val PARTICLE_VANILLA_IDS: Map<String, String> = mapOf(
        "FLAME" to "minecraft:flame",
        "SMOKE" to "minecraft:smoke",
        "LARGE_SMOKE" to "minecraft:large_smoke",
        "HEART" to "minecraft:heart",
        "NOTE" to "minecraft:note",
        "PORTAL" to "minecraft:portal",
        "ENCHANT" to "minecraft:enchant",
        "CRIT" to "minecraft:crit",
        "CLOUD" to "minecraft:cloud",
        "DUST" to "minecraft:dust",
        "FIREWORK" to "minecraft:firework",
        "TOTEM_OF_UNDYING" to "minecraft:totem_of_undying",
        "SOUL" to "minecraft:soul",
        "DRAGON_BREATH" to "minecraft:dragon_breath",
        "HAPPY_VILLAGER" to "minecraft:happy_villager",
        "WITCH" to "minecraft:witch",
        "POOF" to "minecraft:poof",
        "EXPLOSION" to "minecraft:explosion",
        "BUBBLE" to "minecraft:bubble",
        "SPLASH" to "minecraft:splash",
        "LAVA" to "minecraft:lava",
        "END_ROD" to "minecraft:end_rod",
        "SWEEP_ATTACK" to "minecraft:sweep_attack",
        "SONIC_BOOM" to "minecraft:sonic_boom",
    )

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
 * ピッカーに表示する使用頻度の高いプリセットIDの境界です。
 *
 * 大量にあるRegistry由来の全候補をそのまま並べると視認性・操作性が下がるため、
 * ピッカー表示はこのプリセットに限定します。保存・検証・実行・データパック出力の
 * 正本は [CommandDialogSpecs.registeredIds] の全件のままとし、プリセット外の値は
 * Dialogのテキスト入力と候補検索による経路でのみ指定します。サーバーなしで検証
 * できるよう、Registryへ触れない固定ID表として保持します。
 */
object ResourcePresets {
    /** 召喚・対象絞り込みで頻用するエンティティ種別です。表示順に並べます。 */
    val ENTITY_TYPE_IDS: List<String> = listOf(
        "minecraft:pig",
        "minecraft:cow",
        "minecraft:sheep",
        "minecraft:chicken",
        "minecraft:villager",
        "minecraft:zombie",
        "minecraft:skeleton",
        "minecraft:creeper",
        "minecraft:spider",
        "minecraft:enderman",
        "minecraft:armor_stand",
        "minecraft:item_frame",
        "minecraft:tnt",
        "minecraft:firework_rocket",
        "minecraft:lightning_bolt",
        "minecraft:experience_orb",
        "minecraft:slime",
        "minecraft:ghast",
        "minecraft:blaze",
        "minecraft:witch",
        "minecraft:iron_golem",
        "minecraft:wolf",
        "minecraft:cat",
        "minecraft:mannequin",
    )

    /**
     * パーティクル表示で頻用する種類です。
     *
     * 保存形式のBukkit enum名（大文字）のまま保持し、正規化前の比較には使いません。
     * 呼び出し側は [CommandDialogSpecs.normalize] 済みの現在値で照合します。
     */
    val PARTICLE_IDS: List<String> = listOf(
        "FLAME",
        "SMOKE",
        "LARGE_SMOKE",
        "HEART",
        "NOTE",
        "PORTAL",
        "ENCHANT",
        "CRIT",
        "CLOUD",
        "DUST",
        "FIREWORK",
        "TOTEM_OF_UNDYING",
        "SOUL",
        "DRAGON_BREATH",
        "HAPPY_VILLAGER",
        "WITCH",
        "POOF",
        "EXPLOSION",
        "BUBBLE",
        "SPLASH",
        "LAVA",
        "END_ROD",
        "SWEEP_ATTACK",
        "SONIC_BOOM",
    )

    /** 効果付与で頻用する効果IDです。表示順に並べます。 */
    val EFFECT_IDS: List<String> = listOf(
        "minecraft:speed",
        "minecraft:slowness",
        "minecraft:haste",
        "minecraft:strength",
        "minecraft:regeneration",
        "minecraft:resistance",
        "minecraft:fire_resistance",
        "minecraft:night_vision",
        "minecraft:invisibility",
        "minecraft:jump_boost",
        "minecraft:poison",
        "minecraft:wither",
        "minecraft:levitation",
        "minecraft:glowing",
        "minecraft:luck",
        "minecraft:darkness",
        "minecraft:instant_health",
        "minecraft:instant_damage",
        "minecraft:absorption",
        "minecraft:saturation",
        "minecraft:slow_falling",
        "minecraft:conduit_power",
        "minecraft:dolphins_grace",
        "minecraft:hero_of_the_village",
    )

    /** 効果音再生で頻用するサウンドIDです。表示順に並べます。 */
    val SOUND_IDS: List<String> = listOf(
        "minecraft:block.note_block.harp",
        "minecraft:block.note_block.pling",
        "minecraft:block.note_block.bell",
        "minecraft:entity.player.levelup",
        "minecraft:entity.experience_orb.pickup",
        "minecraft:entity.firework_rocket.launch",
        "minecraft:entity.firework_rocket.blast",
        "minecraft:entity.generic.explode",
        "minecraft:entity.lightning_bolt.thunder",
        "minecraft:block.anvil.land",
        "minecraft:block.chest.open",
        "minecraft:block.portal.travel",
        "minecraft:entity.ender_dragon.growl",
        "minecraft:entity.wither.spawn",
        "minecraft:block.beacon.activate",
        "minecraft:weather.rain",
        "minecraft:entity.arrow.shoot",
        "minecraft:block.chest.close",
        "minecraft:entity.tnt.primed",
        "minecraft:block.portal.trigger",
        "minecraft:entity.ender_pearl.throw",
        "minecraft:block.note_block.bass",
        "minecraft:entity.firework_rocket.twinkle",
        "minecraft:weather.thunder",
    )

    /** 種別ごとのプリセットID一覧を返します。順序は表示順です。 */
    fun ids(kind: GestureResourcePickerKind): List<String> = when (kind) {
        GestureResourcePickerKind.ENTITY_TYPE -> ENTITY_TYPE_IDS
        GestureResourcePickerKind.PARTICLE -> PARTICLE_IDS
        GestureResourcePickerKind.EFFECT -> EFFECT_IDS
        GestureResourcePickerKind.SOUND -> SOUND_IDS
    }

    /**
     * 正規化済みの現在値がプリセットに含まれるかを返します。
     *
     * 呼び出し側は [CommandDialogSpecs.normalize] 済みの値を渡します。
     * Namespaced ID系は小文字、パーティクルはBukkit enum名のまま照合します。
     * 空欄は未設定であり、プリセット選択でも入力選択でもありません。
     */
    fun isPreset(kind: GestureResourcePickerKind, normalizedValue: String): Boolean {
        if (normalizedValue.isBlank()) return false
        return ids(kind).any { preset ->
            // プリセット表自体が正規形のため、Namespaced ID系は小文字で比較します。
            // パーティクルは大文字enum名のため、そのまま比較します。
            if (kind == GestureResourcePickerKind.PARTICLE) {
                preset == normalizedValue.trim()
            } else {
                preset == normalizedValue.trim().lowercase()
            }
        }
    }
}

/** プリセットの内部値から、ホバーへ表示するコマンド用IDを解決します。 */
object ResourcePresetDisplay {
    /** パーティクルはEnum名を保存しつつ、バニラコマンドIDへ変換します。 */
    fun commandId(kind: GestureResourcePickerKind, id: String): String =
        if (kind == GestureResourcePickerKind.PARTICLE) ResourcePicker.particleDisplayId(id) else id
}

/**
 * 親タブ直下へ配置するプリセット選択の要素ID境界です。
 *
 * 子画面遷移は行わず、保存・検証は子画面時代と同じ正本を使います。
 * Registryへ触れない純粋処理のため、サーバーなしの単体テストで検証します。
 */
object InlineResourcePicker {
    /** 親直下グリッドの列数です。24件を3行に収めます。 */
    const val COLUMNS = 8

    const val PICK_PREFIX = "lower-inline-pick:"
    const val INPUT_PREFIX = "lower-inline-input:"

    /** プリセット選択操作面の要素IDを返します。 */
    fun pickElementId(fieldKey: String, id: String): String = "$PICK_PREFIX$fieldKey:$id"

    /** ID直接入力操作面の要素IDを返します。 */
    fun inputElementId(fieldKey: String): String = "$INPUT_PREFIX$fieldKey"

    /**
     * 選択要素IDを (設定項目キー, 候補ID) へ分解します。
     *
     * 候補ID自体に `:` を含むため、最初の区切りだけを設定項目キーとします。
     * 不正形式はnullを返します。
     */
    fun parsePick(elementId: String): Pair<String, String>? {
        if (!elementId.startsWith(PICK_PREFIX)) return null
        val rest = elementId.removePrefix(PICK_PREFIX)
        val fieldKey = rest.substringBefore(':').takeIf(String::isNotBlank) ?: return null
        val id = rest.substringAfter(':', missingDelimiterValue = "").takeIf(String::isNotBlank) ?: return null
        return fieldKey to id
    }

    /** 入力要素IDを設定項目キーへ分解します。不正形式はnullを返します。 */
    fun parseInput(elementId: String): String? {
        if (!elementId.startsWith(INPUT_PREFIX)) return null
        return elementId.removePrefix(INPUT_PREFIX).takeIf(String::isNotBlank)
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
            // スポーンエッグを持たない種類は、対応する代表素材へ明示します。
            // 汎用フォールバックへ落とすと一覧内で重複表示になります。
            "FIREWORK_ROCKET" -> Material.FIREWORK_ROCKET
            "LIGHTNING_BOLT" -> Material.LIGHTNING_ROD
            "EXPERIENCE_ORB" -> Material.EXPERIENCE_BOTTLE
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

    /**
     * パーティクル候補の表示素材です。
     *
     * プリセット24件が一覧内で重複表示にならないよう、種類ごとに固有の
     * 代表素材を割り当てます。表外の種類は従来の系統別解決へ縮退し、
     * 最後は汎用フォールバックで表示を保証します。
     */
    private fun particleIcon(id: String): Material? {
        val path = id.substringAfter(':').uppercase()
        when (path) {
            "FLAME" -> return Material.BLAZE_POWDER
            "SMOKE" -> return Material.GUNPOWDER
            "LARGE_SMOKE" -> return Material.CAMPFIRE
            "HEART" -> return Material.POPPY
            "NOTE" -> return Material.NOTE_BLOCK
            "PORTAL" -> return Material.ENDER_PEARL
            "ENCHANT" -> return Material.ENCHANTED_BOOK
            "CRIT" -> return Material.IRON_SWORD
            "CLOUD" -> return Material.GLASS
            "DUST", "REDSTONE" -> return Material.REDSTONE
            "FIREWORK", "FIREWORKS_SPARK" -> return Material.FIREWORK_ROCKET
            "TOTEM_OF_UNDYING", "TOTEM" -> return Material.TOTEM_OF_UNDYING
            "SOUL" -> return Material.SOUL_LANTERN
            "DRAGON_BREATH" -> return Material.DRAGON_BREATH
            "HAPPY_VILLAGER" -> return Material.EMERALD
            "WITCH" -> return Material.CAULDRON
            "POOF" -> return Material.FEATHER
            "EXPLOSION", "EXPLOSION_EMITTER" -> return Material.TNT
            "BUBBLE", "BUBBLE_POP", "BUBBLE_COLUMN_UP" -> return Material.WATER_BUCKET
            "SPLASH" -> return Material.FISHING_ROD
            "LAVA" -> return Material.LAVA_BUCKET
            "END_ROD" -> return Material.END_ROD
            "SWEEP_ATTACK" -> return Material.DIAMOND_SWORD
            "SONIC_BOOM" -> return Material.ECHO_SHARD
        }
        return when (path) {
            "SMALL_FLAME", "CAMPFIRE_COSY_SMOKE", "CAMPFIRE_SIGNAL_SMOKE" -> Material.BLAZE_POWDER
            "FISHING", "DRIPPING_WATER",
            "FALLING_WATER", "RAIN",
            -> Material.WATER_BUCKET
            "ENCHANTED_HIT" -> Material.ENCHANTED_BOOK
            "CRIT_MAGIC", "DAMAGE_INDICATOR" -> Material.IRON_SWORD
            "VILLAGER_HAPPY" -> Material.EMERALD
            "VILLAGER_ANGRY" -> Material.BARRIER
            "REVERSE_PORTAL" -> Material.ENDER_PEARL
            "SPELL", "SPELL_MOB", "SPELL_MOB_AMBIENT", "SPELL_WITCH", "SPELL_INSTANT" -> Material.POTION
            "SLIME" -> Material.SLIME_BALL
            "SNOWBALL", "SNOWFLAKE", "SNOW_SHOVEL" -> Material.SNOWBALL
            "FLASH" -> Material.FIREWORK_ROCKET
            "SOUL_FIRE_FLAME", "SNIFFER_EGG", "SCULK_SOUL", "SCULK_CHARGE",
            "SCULK_CHARGE_POP", "SHRIEK",
            -> Material.SOUL_LANTERN
            else -> null
        }
    }

    /**
     * 効果候補の表示素材です。
     *
     * プリセット24件が一覧内で重複表示にならないよう、種類ごとに固有の
     * 代表素材を割り当てます。表外の種類は従来の系統別解決へ縮退します。
     */
    private fun effectIcon(id: String): Material? {
        val path = id.substringAfter(':').uppercase()
        when (path) {
            "SPEED", "SWIFTNESS" -> return Material.SUGAR
            "SLOWNESS", "SLOW" -> return Material.SOUL_SAND
            "HASTE" -> return Material.GOLDEN_PICKAXE
            "MINING_FATIGUE" -> return Material.COBWEB
            "STRENGTH" -> return Material.BLAZE_POWDER
            "INSTANT_HEALTH" -> return Material.APPLE
            "REGENERATION" -> return Material.GLISTERING_MELON_SLICE
            "INSTANT_DAMAGE" -> return Material.FERMENTED_SPIDER_EYE
            "JUMP_BOOST", "JUMP" -> return Material.RABBIT_FOOT
            "RESISTANCE", "DAMAGE_RESISTANCE" -> return Material.IRON_CHESTPLATE
            "FIRE_RESISTANCE" -> return Material.MAGMA_CREAM
            "NIGHT_VISION" -> return Material.GOLDEN_CARROT
            "INVISIBILITY" -> return Material.GLASS_BOTTLE
            "POISON" -> return Material.SPIDER_EYE
            "WITHER" -> return Material.WITHER_SKELETON_SKULL
            "ABSORPTION" -> return Material.GOLDEN_APPLE
            "SATURATION" -> return Material.COOKED_BEEF
            "GLOWING" -> return Material.GLOWSTONE_DUST
            "LEVITATION" -> return Material.SHULKER_SHELL
            "LUCK" -> return Material.GOLD_NUGGET
            "SLOW_FALLING" -> return Material.FEATHER
            "CONDUIT_POWER" -> return Material.NAUTILUS_SHELL
            "DOLPHINS_GRACE" -> return Material.COD
            "DARKNESS" -> return Material.SCULK
            "HERO_OF_THE_VILLAGE" -> return Material.EMERALD
        }
        return when (path) {
            "HEALTH_BOOST" -> Material.APPLE
            "HARM" -> Material.SPIDER_EYE
            "NAUSEA", "CONFUSION" -> Material.PUFFERFISH
            "WATER_BREATHING" -> Material.PUFFERFISH
            "BLINDNESS" -> Material.BLACK_WOOL
            "HUNGER" -> Material.ROTTEN_FLESH
            "WEAKNESS" -> Material.FERMENTED_SPIDER_EYE
            "UNLUCK" -> Material.COAL
            "BAD_OMEN", "OMINOUS" -> Material.OMINOUS_BOTTLE
            "RAID_OMEN", "TRIAL_OMEN" -> Material.TRIAL_KEY
            "WIND_CHARGED" -> Material.WIND_CHARGE
            "WEAVING" -> Material.COBWEB
            "OOZING" -> Material.SLIME_BALL
            "INFESTED" -> Material.STONE
            else -> null
        }
    }

    /**
     * 効果音候補の表示素材です。
     *
     * プリセット24件が一覧内で重複表示にならないよう、個別ID一致を
     * 接頭辞判定より先に解決します。表外のIDは従来の接頭辞解決へ縮退します。
     */
    private fun soundIcon(id: String): Material? {
        val path = id.substringAfter(':')
        when (path) {
            "block.note_block.harp" -> return Material.NOTE_BLOCK
            "block.note_block.pling" -> return Material.AMETHYST_SHARD
            "block.note_block.bell" -> return Material.BELL
            "block.note_block.bass" -> return Material.DARK_OAK_PLANKS
            "entity.player.levelup" -> return Material.EXPERIENCE_BOTTLE
            "entity.experience_orb.pickup" -> return Material.GOLD_NUGGET
            "entity.firework_rocket.launch" -> return Material.FIREWORK_ROCKET
            "entity.firework_rocket.blast" -> return Material.FIRE_CHARGE
            "entity.firework_rocket.twinkle" -> return Material.FIREWORK_STAR
            "entity.generic.explode" -> return Material.TNT
            "entity.tnt.primed" -> return Material.GUNPOWDER
            "entity.lightning_bolt.thunder" -> return Material.LIGHTNING_ROD
            "block.anvil.land" -> return Material.ANVIL
            "block.chest.open" -> return Material.CHEST
            "block.chest.close" -> return Material.TRAPPED_CHEST
            "block.portal.travel" -> return Material.OBSIDIAN
            "block.portal.trigger" -> return Material.FLINT_AND_STEEL
            "entity.ender_pearl.throw" -> return Material.ENDER_PEARL
            "entity.arrow.shoot" -> return Material.ARROW
            "entity.ender_dragon.growl" -> return Material.DRAGON_HEAD
            "entity.wither.spawn" -> return Material.NETHER_STAR
            "block.beacon.activate" -> return Material.BEACON
            "weather.rain" -> return Material.WATER_BUCKET
            "weather.thunder" -> return Material.TRIDENT
        }
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
