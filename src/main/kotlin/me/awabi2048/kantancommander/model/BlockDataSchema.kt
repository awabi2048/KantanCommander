package me.awabi2048.kantancommander.model

import org.bukkit.block.BlockFace
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.AnaloguePowerable
import org.bukkit.block.data.Attachable
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Brushable
import org.bukkit.block.data.Directional
import org.bukkit.block.data.FaceAttachable
import org.bukkit.block.data.Hangable
import org.bukkit.block.data.Hatchable
import org.bukkit.block.data.Levelled
import org.bukkit.block.data.Lightable
import org.bukkit.block.data.MultipleFacing
import org.bukkit.block.data.Openable
import org.bukkit.block.data.Orientable
import org.bukkit.block.data.Powerable
import org.bukkit.block.data.Rail
import org.bukkit.block.data.Rotatable
import org.bukkit.block.data.Segmentable
import org.bukkit.block.data.SideChaining
import org.bukkit.block.data.Snowable
import org.bukkit.block.data.Waterlogged
import org.bukkit.block.data.type.Bamboo
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.Beehive
import org.bukkit.block.data.type.Bell
import org.bukkit.block.data.type.BigDripleaf
import org.bukkit.block.data.type.BrewingStand
import org.bukkit.block.data.type.BubbleColumn
import org.bukkit.block.data.type.Cake
import org.bukkit.block.data.type.Candle
import org.bukkit.block.data.type.CaveVinesPlant
import org.bukkit.block.data.type.Chest
import org.bukkit.block.data.type.ChiseledBookshelf
import org.bukkit.block.data.type.CommandBlock
import org.bukkit.block.data.type.Comparator
import org.bukkit.block.data.type.CopperGolemStatue
import org.bukkit.block.data.type.Crafter
import org.bukkit.block.data.type.CreakingHeart
import org.bukkit.block.data.type.DaylightDetector
import org.bukkit.block.data.type.DecoratedPot
import org.bukkit.block.data.type.Dispenser
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.DriedGhast
import org.bukkit.block.data.type.EndPortalFrame
import org.bukkit.block.data.type.Farmland
import org.bukkit.block.data.type.FlowerBed
import org.bukkit.block.data.type.Gate
import org.bukkit.block.data.type.Hopper
import org.bukkit.block.data.type.Jigsaw
import org.bukkit.block.data.type.Jukebox
import org.bukkit.block.data.type.Leaves
import org.bukkit.block.data.type.Lectern
import org.bukkit.block.data.type.MossyCarpet
import org.bukkit.block.data.type.NoteBlock
import org.bukkit.block.data.type.Piston
import org.bukkit.block.data.type.PistonHead
import org.bukkit.block.data.type.PotentSulfur
import org.bukkit.block.data.type.RedstoneWire
import org.bukkit.block.data.type.Repeater
import org.bukkit.block.data.type.RespawnAnchor
import org.bukkit.block.data.type.Sapling
import org.bukkit.block.data.type.Scaffolding
import org.bukkit.block.data.type.SculkCatalyst
import org.bukkit.block.data.type.SculkSensor
import org.bukkit.block.data.type.SculkShrieker
import org.bukkit.block.data.type.SeaPickle
import org.bukkit.block.data.type.Slab
import org.bukkit.block.data.type.Snow
import org.bukkit.block.data.type.Speleothem
import org.bukkit.block.data.type.Stairs
import org.bukkit.block.data.type.StructureBlock
import org.bukkit.block.data.type.TNT
import org.bukkit.block.data.type.TechnicalPiston
import org.bukkit.block.data.type.TestBlock
import org.bukkit.block.data.type.TrialSpawner
import org.bukkit.block.data.type.Tripwire
import org.bukkit.block.data.type.TurtleEgg
import org.bukkit.block.data.type.Vault
import org.bukkit.block.data.type.Wall

/**
 * ブロック状態（MinecraftのBlockData properties）の型付きスキーマです。
 *
 * EntityDataSchema と同じく、保存形式・GUI・検証・実行・出力が共有する正本です。
 * propertyと候補値は org.bukkit.block.data.* インターフェースを根拠に列挙し、
 * 値の適用は `id[key=value]` 文字列のラウンドトリップ（Bukkit.createBlockData）で
 * 行います。個別setterをブロックごとに実装しません。
 *
 * 保存形式は [PARAM_PREFIX]＋propertyId のパラメータ群で、空値は未設定と同等です。
 * ブロックID変更・一時変数参照への切替時に古い状態を残さないため、
 * 呼び出し側は [clear] でまとめて除去します。
 */
enum class BlockStateValueKind {
    /** 真偽値です。Gesture上は選択→クリックの直接切替えで編集します。 */
    BOOLEAN,

    /** 有限候補です。候補一覧画面で編集し、内部数値を直接入力させません。 */
    ENUM,

    /** 範囲付き整数です。Dialog入力で編集し、定義の [BlockStateProperty.intRange] で検証します。 */
    INTEGER,
}

/**
 * ブロック種類が確定した後の、1項目の編集可能仕様です。
 *
 * [id] はMinecraftのproperty名そのもの（`facing`、`waterlogged` 等）です。
 * 表示名・値名も Minecraft の識別子をそのまま示し、setblock 文法と一致させます。
 */
data class BlockStateProperty(
    val id: String,
    val valueKind: BlockStateValueKind,
    /** ENUM種別の候補（小文字の保存値＝表示値）。ブロックごとの許可集合に絞り込み済みです。 */
    val enumChoices: List<String> = emptyList(),
    /** INTEGER種別の許容範囲です。INTEGER以外ではnullです。 */
    val intRange: IntRange? = null,
)

object BlockDataSchema {
    /** ブロック状態の保存接頭辞です。`blockState_<propertyId>` 形式で保存します。 */
    const val PARAM_PREFIX = "blockState_"

    /** 保存キーを返します。 */
    fun stateKey(propertyId: String): String = "$PARAM_PREFIX$propertyId"

    /** 保存キーからproperty IDを取り出します。ブロック状態以外のキーはnullです。 */
    fun stateIdOf(key: String): String? =
        key.removePrefix(PARAM_PREFIX).takeIf { key.startsWith(PARAM_PREFIX) && it.isNotBlank() }

    /**
     * 設定済みのブロック状態を返します。
     *
     * 空値は未設定と同等に扱い除外します。未知IDは除外せず、呼び出し側
     * （検証層）が不正として扱います。
     */
    fun entries(params: Map<String, String>): Map<String, String> =
        params.mapNotNull { (key, value) ->
            stateIdOf(key)?.takeIf { value.isNotBlank() }?.let { it to value }
        }.toMap()

    /** ノードへ保存されたブロック状態と明示設定の記録をまとめて除去します。 */
    fun clear(node: CommandNode) {
        node.params.keys.removeIf { it.startsWith(PARAM_PREFIX) }
        node.clearConfiguredPrefix(PARAM_PREFIX)
    }

    /** 定義済みの全property IDを返します。未知IDの静的検出に使います。 */
    fun allIds(): Set<String> = allIds

    /**
     * 真偽値の次状態を返します。
     *
     * 未設定・壊れた値はfalseとして扱いtrueへ進めます。両GUIのトグル操作で
     * 同じ計算を使い、表示と保存の食い違いを防ぎます。
     */
    fun toggledBooleanValue(raw: String): String = (!(raw.toBooleanStrictOrNull() ?: false)).toString()

    /**
     * 対象ブロックが持つ編集可能項目を、定義順で返します。
     *
     * 各項目のENUM候補・INTEGER範囲はインターフェース経由で実ブロックから
     * 確定した値（例: Directional.getFaces、Ageable.getMaximumAge）です。
     */
    fun propertiesFor(data: BlockData): List<BlockStateProperty> = buildList {
        sources.forEach { source ->
            if (!source.applies(data)) return@forEach
            add(
                BlockStateProperty(
                    id = source.id,
                    valueKind = source.kind,
                    enumChoices = source.choicesOf(data),
                    intRange = source.rangeOf(data),
                ),
            )
        }
        when {
            data is Wall || data is MossyCarpet -> {
                HORIZONTAL_FACES.forEach { face ->
                    add(
                        BlockStateProperty(
                            id = face.name.lowercase(),
                            valueKind = BlockStateValueKind.ENUM,
                            enumChoices = WALL_HEIGHT_CHOICES,
                        ),
                    )
                }
                if (data is Wall) {
                    add(BlockStateProperty("up", BlockStateValueKind.BOOLEAN))
                }
            }
            data is RedstoneWire -> {
                FACE_ORDER.filter { it in data.allowedFaces }.forEach { face ->
                    add(
                        BlockStateProperty(
                            id = face.name.lowercase(),
                            valueKind = BlockStateValueKind.ENUM,
                            enumChoices = REDSTONE_CONNECTION_CHOICES,
                        ),
                    )
                }
            }
            data is MultipleFacing -> {
                FACE_ORDER.filter { it in data.allowedFaces }.forEach { face ->
                    add(BlockStateProperty(face.name.lowercase(), BlockStateValueKind.BOOLEAN))
                }
            }
        }
    }

    /** 対象ブロック上の指定propertyの編集仕様を返します。 */
    fun propertyFor(data: BlockData, propertyId: String): BlockStateProperty? =
        propertiesFor(data).firstOrNull { it.id == propertyId }

    /** 保存文字列が定義の型・候補・範囲を満たすかを検証します。 */
    fun accepts(prop: BlockStateProperty, raw: String): Boolean = when (prop.valueKind) {
        BlockStateValueKind.BOOLEAN -> raw.toBooleanStrictOrNull() != null
        BlockStateValueKind.ENUM -> raw in prop.enumChoices
        BlockStateValueKind.INTEGER -> raw.trim().toIntOrNull()?.let { it in (prop.intRange ?: return false) } == true
    }

    /**
     * 値の型だけで静的に判定できる許容性です。
     *
     * 対象ブロックがまだ無い場面（検証の静的段階）に使い、IDがどれかの
     * 定義で受理されるかだけを確認します。ブロック確定後は
     * [propertyFor]＋[accepts]による厳密判定が正本です。
     */
    fun staticallyAccepted(propertyId: String, raw: String): Boolean =
        sources.filter { it.id == propertyId }.any { source ->
            when (source.kind) {
                BlockStateValueKind.BOOLEAN -> raw.toBooleanStrictOrNull() != null
                BlockStateValueKind.ENUM -> source.staticChoices.contains(raw) || source.choicesDynamic
                BlockStateValueKind.INTEGER -> raw.trim().toIntOrNull() != null
            }
        }

    /**
     * 現在のBlockDataへ設定状態を適用した新しいBlockDataを返します。
     *
     * `id[key=value]` 文字列で再構築し、組み合わせが
     * Minecraft上成立しない場合はnullを返します。
     */
    fun apply(base: BlockData, entries: Map<String, String>): BlockData? {
        if (entries.isEmpty()) return base
        val id = base.asString.substringBefore('[')
        val body = entries.entries.joinToString(",") { "${it.key}=${it.value}" }
        return runCatching { org.bukkit.Bukkit.createBlockData("$id[$body]") }.getOrNull()
    }

    /** コマンド文字列向けに `id[key=value]` 形式のブロックIDを組み立てます。 */
    fun toCommandString(base: BlockData, entries: Map<String, String>): String {
        if (entries.isEmpty()) return base.asString.substringBefore('[')
        val id = base.asString.substringBefore('[')
        val body = entries.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }
        return "$id[$body]"
    }

    private val FACE_ORDER = listOf(
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST,
        BlockFace.UP,
        BlockFace.DOWN,
    )
    private val HORIZONTAL_FACES = FACE_ORDER.take(4)

    private val WALL_HEIGHT_CHOICES = listOf("none", "low", "tall")
    private val REDSTONE_CONNECTION_CHOICES = listOf("up", "side", "none")
    private val ORIENTATION_CHOICES = listOf(
        "down_east", "down_north", "down_south", "down_west",
        "up_east", "up_north", "up_south", "up_west",
        "west_up", "east_up", "north_up", "south_up",
    )

    // BukkitのInstrument名とMinecraftのinstrument値の対応です。
    // 実行・出力はcreateBlockDataの文字列検証で最終判定します。
    private val INSTRUMENT_CHOICES = listOf(
        "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
        "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit",
        "banjo", "pling", "trumpet", "trumpet_exposed", "trumpet_oxidized",
        "trumpet_weathered", "zombie", "skeleton", "creeper", "dragon",
        "wither_skeleton", "piglin", "custom_head",
    )

    /** 面ごとの値を持たない、単一propertyの定義元です。 */
    private class Source(
        val id: String,
        val kind: BlockStateValueKind,
        val applies: (BlockData) -> Boolean,
        /** ENUMの静的候補です。動的候補がある場合は [choicesDynamic] と合わせて使います。 */
        val staticChoices: List<String> = emptyList(),
        /** ENUM候補を実ブロックから解決する場合にtrueを返します（静的検証で値を広く許容）。 */
        val choicesDynamic: Boolean = false,
        val rangeOf: (BlockData) -> IntRange? = { null },
        val choicesOf: (BlockData) -> List<String> = { staticChoices },
    )

    private val sources: List<Source> = listOf(
        Source(
            "facing", BlockStateValueKind.ENUM,
            applies = { it is Directional },
            staticChoices = FACE_ORDER.map { face -> face.name.lowercase() },
            choicesDynamic = true,
            choicesOf = { data ->
                FACE_ORDER.filter { it in (data as Directional).faces }
                    .map { it.name.lowercase() }
            },
        ),
        Source(
            "axis", BlockStateValueKind.ENUM,
            applies = { it is Orientable },
            staticChoices = listOf("x", "y", "z"),
            choicesDynamic = true,
            choicesOf = { data ->
                (data as Orientable).axes.map { it.name.lowercase() }
            },
        ),
        Source(
            "rotation", BlockStateValueKind.INTEGER,
            applies = { it is Rotatable },
            rangeOf = { 0..15 },
        ),
        Source(
            "orientation", BlockStateValueKind.ENUM,
            applies = { it is Crafter || it is Jigsaw },
            staticChoices = ORIENTATION_CHOICES,
        ),
        Source(
            "face", BlockStateValueKind.ENUM,
            applies = { it is FaceAttachable },
            staticChoices = listOf("floor", "wall", "ceiling"),
        ),
        Source(
            "half", BlockStateValueKind.ENUM,
            applies = { it is Bisected },
            staticChoices = listOf("top", "bottom"),
        ),
        Source(
            "part", BlockStateValueKind.ENUM,
            applies = { it is Bed },
            staticChoices = listOf("head", "foot"),
        ),
        Source(
            "type", BlockStateValueKind.ENUM,
            applies = { it is Slab },
            staticChoices = listOf("top", "bottom", "double"),
        ),
        Source(
            "type", BlockStateValueKind.ENUM,
            applies = { it is Chest },
            staticChoices = listOf("single", "left", "right"),
        ),
        Source(
            "type", BlockStateValueKind.ENUM,
            applies = { it is TechnicalPiston },
            staticChoices = listOf("normal", "sticky"),
        ),
        Source(
            "shape", BlockStateValueKind.ENUM,
            applies = { it is Stairs },
            staticChoices = listOf("straight", "inner_left", "inner_right", "outer_left", "outer_right"),
        ),
        Source(
            "shape", BlockStateValueKind.ENUM,
            applies = { it is Rail },
            staticChoices = listOf(
                "ascending_east", "ascending_north", "ascending_south", "ascending_west",
                "curve_east", "curve_west", "curve_north", "curve_south", "flat",
            ),
            choicesDynamic = true,
            choicesOf = { data ->
                (data as Rail).shapes.map { it.name.lowercase() }
            },
        ),
        Source(
            "hinge", BlockStateValueKind.ENUM,
            applies = { it is Door },
            staticChoices = listOf("left", "right"),
        ),
        Source(
            "attachment", BlockStateValueKind.ENUM,
            applies = { it is Bell },
            staticChoices = listOf("floor", "ceiling", "single_wall", "double_wall"),
        ),
        Source(
            "tilt", BlockStateValueKind.ENUM,
            applies = { it is BigDripleaf },
            staticChoices = listOf("full", "none", "partial", "unstable"),
        ),
        Source(
            "leaves", BlockStateValueKind.ENUM,
            applies = { it is Bamboo },
            staticChoices = listOf("large", "none", "small"),
        ),
        Source(
            "thickness", BlockStateValueKind.ENUM,
            applies = { it is Speleothem },
            staticChoices = listOf("base", "frustum", "middle", "tip", "tip_merge"),
        ),
        Source(
            "vertical_direction", BlockStateValueKind.ENUM,
            applies = { it is Speleothem },
            staticChoices = listOf("up", "down"),
        ),
        Source(
            "joint", BlockStateValueKind.ENUM,
            applies = { it is Jigsaw },
            staticChoices = listOf("aligned", "rollable"),
        ),
        Source(
            "mode", BlockStateValueKind.ENUM,
            applies = { it is Comparator },
            staticChoices = listOf("compare", "subtract"),
        ),
        Source(
            "mode", BlockStateValueKind.ENUM,
            applies = { it is StructureBlock },
            staticChoices = listOf("corner", "data", "load", "save"),
        ),
        Source(
            "mode", BlockStateValueKind.ENUM,
            applies = { it is TestBlock },
            staticChoices = listOf("start", "log", "fail", "accept"),
        ),
        Source(
            "instrument", BlockStateValueKind.ENUM,
            applies = { it is NoteBlock },
            staticChoices = INSTRUMENT_CHOICES,
        ),
        Source(
            "sculk_sensor_phase", BlockStateValueKind.ENUM,
            applies = { it is SculkSensor },
            staticChoices = listOf("active", "cooldown", "inactive"),
        ),
        Source(
            "trial_spawner_state", BlockStateValueKind.ENUM,
            applies = { it is TrialSpawner },
            staticChoices = listOf(
                "active", "cooldown", "ejecting_reward", "inactive",
                "waiting_for_players", "waiting_for_reward_ejection",
            ),
        ),
        Source(
            "vault_state", BlockStateValueKind.ENUM,
            applies = { it is Vault },
            staticChoices = listOf("active", "ejecting", "inactive", "unlocking"),
        ),
        Source(
            "creaking_heart_state", BlockStateValueKind.ENUM,
            applies = { it is CreakingHeart },
            staticChoices = listOf("awake", "dormant", "uprooted"),
        ),
        Source(
            "potent_sulfur_state", BlockStateValueKind.ENUM,
            applies = { it is PotentSulfur },
            staticChoices = listOf("continuous", "dormant", "dry", "erupting", "wet"),
        ),
        Source(
            "copper_golem_pose", BlockStateValueKind.ENUM,
            applies = { it is CopperGolemStatue },
            staticChoices = listOf("running", "sitting", "standing", "star"),
        ),
        Source(
            "side_chaining", BlockStateValueKind.ENUM,
            applies = { it is SideChaining },
            staticChoices = listOf("center", "left", "right", "unconnected"),
        ),
        Source("waterlogged", BlockStateValueKind.BOOLEAN, applies = { it is Waterlogged }),
        Source("powered", BlockStateValueKind.BOOLEAN, applies = { it is Powerable }),
        Source("open", BlockStateValueKind.BOOLEAN, applies = { it is Openable }),
        Source("lit", BlockStateValueKind.BOOLEAN, applies = { it is Lightable }),
        Source("attached", BlockStateValueKind.BOOLEAN, applies = { it is Attachable }),
        Source("hanging", BlockStateValueKind.BOOLEAN, applies = { it is Hangable }),
        Source("snowy", BlockStateValueKind.BOOLEAN, applies = { it is Snowable }),
        Source("occupied", BlockStateValueKind.BOOLEAN, applies = { it is Bed }),
        Source("conditional", BlockStateValueKind.BOOLEAN, applies = { it is CommandBlock }),
        Source("triggered", BlockStateValueKind.BOOLEAN, applies = { it is Dispenser || it is Crafter }),
        Source("crafting", BlockStateValueKind.BOOLEAN, applies = { it is Crafter }),
        Source("enabled", BlockStateValueKind.BOOLEAN, applies = { it is Hopper }),
        Source("eye", BlockStateValueKind.BOOLEAN, applies = { it is EndPortalFrame }),
        Source("has_book", BlockStateValueKind.BOOLEAN, applies = { it is Lectern }),
        Source("in_wall", BlockStateValueKind.BOOLEAN, applies = { it is Gate }),
        Source("inverted", BlockStateValueKind.BOOLEAN, applies = { it is DaylightDetector }),
        Source("locked", BlockStateValueKind.BOOLEAN, applies = { it is Repeater }),
        Source("extended", BlockStateValueKind.BOOLEAN, applies = { it is Piston }),
        Source("short", BlockStateValueKind.BOOLEAN, applies = { it is PistonHead }),
        Source("signal_fire", BlockStateValueKind.BOOLEAN, applies = { it is org.bukkit.block.data.type.Campfire }),
        Source("drag", BlockStateValueKind.BOOLEAN, applies = { it is BubbleColumn }),
        Source("berries", BlockStateValueKind.BOOLEAN, applies = { it is CaveVinesPlant }),
        Source("bottom", BlockStateValueKind.BOOLEAN, applies = { it is Scaffolding || it is MossyCarpet }),
        Source("bloom", BlockStateValueKind.BOOLEAN, applies = { it is SculkCatalyst }),
        Source("shrieking", BlockStateValueKind.BOOLEAN, applies = { it is SculkShrieker }),
        Source("can_summon", BlockStateValueKind.BOOLEAN, applies = { it is SculkShrieker }),
        Source("persistent", BlockStateValueKind.BOOLEAN, applies = { it is Leaves }),
        Source("disarmed", BlockStateValueKind.BOOLEAN, applies = { it is Tripwire }),
        Source("unstable", BlockStateValueKind.BOOLEAN, applies = { it is TNT }),
        Source("cracked", BlockStateValueKind.BOOLEAN, applies = { it is DecoratedPot }),
        Source("ominous", BlockStateValueKind.BOOLEAN, applies = { it is TrialSpawner || it is Vault }),
        Source("natural", BlockStateValueKind.BOOLEAN, applies = { it is CreakingHeart }),
        Source("has_record", BlockStateValueKind.BOOLEAN, applies = { it is Jukebox }),
        Source("age", BlockStateValueKind.INTEGER, applies = { it is Ageable }, rangeOf = { 0..(it as Ageable).maximumAge }),
        Source("level", BlockStateValueKind.INTEGER, applies = { it is Levelled }, rangeOf = { 0..(it as Levelled).maximumLevel }),
        Source("power", BlockStateValueKind.INTEGER, applies = { it is AnaloguePowerable }, rangeOf = { 0..(it as AnaloguePowerable).maximumPower }),
        Source("delay", BlockStateValueKind.INTEGER, applies = { it is Repeater }, rangeOf = { (it as Repeater).minimumDelay..(it as Repeater).maximumDelay }),
        Source("distance", BlockStateValueKind.INTEGER, applies = { it is Leaves || it is Scaffolding }, rangeOf = {
            when (it) {
                is Leaves -> it.minimumDistance..it.maximumDistance
                is Scaffolding -> 0..it.maximumDistance
                else -> null
            }
        }),
        Source("hatch", BlockStateValueKind.INTEGER, applies = { it is Hatchable }, rangeOf = { 0..(it as Hatchable).maximumHatch }),
        Source("layers", BlockStateValueKind.INTEGER, applies = { it is Snow }, rangeOf = { (it as Snow).minimumLayers..(it as Snow).maximumLayers }),
        Source("moisture", BlockStateValueKind.INTEGER, applies = { it is Farmland }, rangeOf = { 0..(it as Farmland).maximumMoisture }),
        Source("stage", BlockStateValueKind.INTEGER, applies = { it is Sapling }, rangeOf = { 0..(it as Sapling).maximumStage }),
        Source("honey_level", BlockStateValueKind.INTEGER, applies = { it is Beehive }, rangeOf = { 0..(it as Beehive).maximumHoneyLevel }),
        Source("charges", BlockStateValueKind.INTEGER, applies = { it is RespawnAnchor }, rangeOf = { 0..(it as RespawnAnchor).maximumCharges }),
        Source("candles", BlockStateValueKind.INTEGER, applies = { it is Candle }, rangeOf = { (it as Candle).minimumCandles..(it as Candle).maximumCandles }),
        Source("pickles", BlockStateValueKind.INTEGER, applies = { it is SeaPickle }, rangeOf = { (it as SeaPickle).minimumPickles..(it as SeaPickle).maximumPickles }),
        Source("eggs", BlockStateValueKind.INTEGER, applies = { it is TurtleEgg }, rangeOf = { (it as TurtleEgg).minimumEggs..(it as TurtleEgg).maximumEggs }),
        Source("bites", BlockStateValueKind.INTEGER, applies = { it is Cake }, rangeOf = { 0..(it as Cake).maximumBites }),
        Source("flower_amount", BlockStateValueKind.INTEGER, applies = { it is FlowerBed }, rangeOf = { (it as FlowerBed).minimumFlowerAmount..(it as FlowerBed).maximumFlowerAmount }),
        Source("segment_amount", BlockStateValueKind.INTEGER, applies = { it is Segmentable }, rangeOf = { (it as Segmentable).minimumSegmentAmount..(it as Segmentable).maximumSegmentAmount }),
        Source("dusted", BlockStateValueKind.INTEGER, applies = { it is Brushable }, rangeOf = { 0..(it as Brushable).maximumDusted }),
        Source("hydration", BlockStateValueKind.INTEGER, applies = { it is DriedGhast }, rangeOf = { 0..(it as DriedGhast).maximumHydration }),
        Source("note", BlockStateValueKind.INTEGER, applies = { it is NoteBlock }, rangeOf = { 0..24 }),
        Source("has_bottle_0", BlockStateValueKind.BOOLEAN, applies = { it is BrewingStand }),
        Source("has_bottle_1", BlockStateValueKind.BOOLEAN, applies = { it is BrewingStand }),
        Source("has_bottle_2", BlockStateValueKind.BOOLEAN, applies = { it is BrewingStand }),
        Source("slot_0_occupied", BlockStateValueKind.BOOLEAN, applies = { it is ChiseledBookshelf }),
        Source("slot_1_occupied", BlockStateValueKind.BOOLEAN, applies = { it is ChiseledBookshelf }),
        Source("slot_2_occupied", BlockStateValueKind.BOOLEAN, applies = { it is ChiseledBookshelf }),
        Source("slot_3_occupied", BlockStateValueKind.BOOLEAN, applies = { it is ChiseledBookshelf }),
        Source("slot_4_occupied", BlockStateValueKind.BOOLEAN, applies = { it is ChiseledBookshelf }),
        Source("slot_5_occupied", BlockStateValueKind.BOOLEAN, applies = { it is ChiseledBookshelf }),
    )

    private val allIds: Set<String> = sources.map { it.id }.toSet() + FACE_ORDER.map { it.name.lowercase() }
}
