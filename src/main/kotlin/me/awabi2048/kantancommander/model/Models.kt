package me.awabi2048.kantancommander.model

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import org.bukkit.Material
import java.util.Locale
import java.util.UUID

const val STRUCTURED_FORMAT_VERSION = 8
const val TICKS_PER_SECOND = 20
const val MIN_TIMER_SECONDS = 1
const val MAX_TIMER_SECONDS = 86_400
const val MAX_BLOCK_OPERATION_VOLUME = 32_768L

data class DiskScript(
    val formatVersion: Int = STRUCTURED_FORMAT_VERSION,
    val id: UUID = UUID.randomUUID(),
    var name: String,
    val owner: UUID,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * 旧形式の一覧フラグです。現在のライブラリ／履歴の正本はScriptStoreの
     * 関係ファイルであり、この値は移行時だけ読み取ってfalseへ正規化します。
     */
    @Deprecated("Use ScriptStore library/history relations")
    var listed: Boolean = false,
    var activation: ActivationMode = ActivationMode.NEEDS_REDSTONE,
    var timer: TimerSetting = TimerSetting(),
    var graph: CommandGraph = CommandGraph.empty(),
    /**
     * 同じプログラムを複数画面から編集する際の楽観的競合検出値です。
     * 旧JSONには存在しないため0で復元し、ScriptStoreの保存境界で進めます。
     */
    var revision: Long = 0L,
    /**
     * ノードを持たないスクリプトでも、プログラム名またはタイマーを明示的に
     * 編集したことを保持します。配置からのディスク出力可否は値の非空判定では
     * なく、この編集状態とグラフの両方を共通判定するため、初期配置の既定名を
     * 誤って「内容あり」と扱いません。旧JSONには存在しないためfalseで復元します。
     */
    var contentModified: Boolean = false,
)

/** 配置されたかんたんコマンダー制御ブロックからディスクへ出力できる内容があるかを判定します。 */
fun DiskScript.hasDiskContent(): Boolean = contentModified || graph.nodes.isNotEmpty()

data class TimerSetting(
    var enabled: Boolean = false,
    var intervalSeconds: Int = MIN_TIMER_SECONDS,
) {
    fun normalized() = copy(intervalSeconds = intervalSeconds.coerceIn(MIN_TIMER_SECONDS, MAX_TIMER_SECONDS))
    /** Minecraftの内部スケジューラへ渡すtick値は、このモデル境界でだけ算出します。 */
    val intervalTicks: Long get() = intervalSeconds.coerceIn(MIN_TIMER_SECONDS, MAX_TIMER_SECONDS) * TICKS_PER_SECOND.toLong()
}

enum class ActivationMode(val key: LocalizationKey<String>) {
    NEEDS_REDSTONE(KcKeys.KANTAN_COMMANDER_CLEAN_ACTIVATION_NEEDS_REDSTONE),
    ALWAYS_ACTIVE(KcKeys.KANTAN_COMMANDER_CLEAN_ACTIVATION_ALWAYS_ACTIVE);

    fun toggled(timerEnabled: Boolean): ActivationMode =
        if (!timerEnabled) NEEDS_REDSTONE else if (this == NEEDS_REDSTONE) ALWAYS_ACTIVE else NEEDS_REDSTONE
}

data class CommandGraph(
    var entryNodeId: UUID? = null,
    val nodes: LinkedHashMap<UUID, CommandNode> = linkedMapOf(),
) {
    companion object {
        fun empty() = CommandGraph()
    }

    fun deepCopy(): CommandGraph {
        val copied = linkedMapOf<UUID, CommandNode>()
        nodes.forEach { (id, node) ->
            copied[id] = node.copy(
                params = node.params.toMutableMap(),
                configuredFields = node.configuredFields?.toMutableSet(),
                contextOverride = node.contextOverride?.copy(),
                targetSpec = node.targetSpec?.copy(),
                secondaryTargetSpec = node.secondaryTargetSpec?.copy(),
                destinationSpec = node.destinationSpec?.copy(),
                destinationTargetSpec = node.destinationTargetSpec?.copy(),
                destinationFacingSpec = node.destinationFacingSpec?.copy(),
                conditionPositionSpec = node.conditionPositionSpec?.copy(),
                blockPositionSpec = node.blockPositionSpec?.copy(),
                blockFromSpec = node.blockFromSpec?.copy(),
                blockToSpec = node.blockToSpec?.copy(),
                soundPositionSpec = node.soundPositionSpec?.copy(),
                summonPositionSpec = node.summonPositionSpec?.copy(),
                temporaryEntityTargetSpec = node.temporaryEntityTargetSpec?.copy(),
                temporaryLocationPositionSpec = node.temporaryLocationPositionSpec?.copy(),
                temporaryLocationFacingSpec = node.temporaryLocationFacingSpec?.copy(),
                snapshot = node.snapshot?.deepCopy(),
            )
        }
        return CommandGraph(entryNodeId, copied)
    }
}

data class CommandNode(
    val id: UUID = UUID.randomUUID(),
    val type: CommandType,
    val params: MutableMap<String, String> = linkedMapOf(),
    /**
     * ユーザーが明示的に操作した設定項目です。
     *
     * paramsの値がコマンド既定値と同じでも「操作して設定した」状態を失わない
     * よう、値そのものとは別に保持します。旧JSONにはこの項目が存在しないため
     * nullableにし、読み込み直後はCommandSettingsModelが値から安全に推定します。
     */
    var configuredFields: MutableSet<String>? = null,
    var next: UUID? = null,
    var trueNext: UUID? = null,
    var falseNext: UUID? = null,
    var pairedNodeId: UUID? = null,
    var targetSpec: TargetSpec? = null,
    var secondaryTargetSpec: TargetSpec? = null,
    var destinationSpec: PositionSpec? = null,
    var destinationTargetSpec: TargetSpec? = null,
    /** テレポート先へ適用するノード単位の向きです。 */
    var destinationFacingSpec: FacingSpec? = null,
    var conditionPositionSpec: PositionSpec? = null,
    /** ブロック操作の単一配置位置（setblock相当）。 */
    var blockPositionSpec: PositionSpec? = null,
    /** ブロック操作の範囲始点・終点（fill相当）。 */
    var blockFromSpec: PositionSpec? = null,
    var blockToSpec: PositionSpec? = null,
    /** PLAY_SOUNDで「マイワールド内全域」以外を選んだ場合の再生位置です。 */
    var soundPositionSpec: PositionSpec? = null,
    /** エンティティ召喚で指定された場合の召喚位置です。未設定時は制御ブロック位置を使用します。 */
    var summonPositionSpec: PositionSpec? = null,
    /** TEMP_SET ENTITYの参照元です。通常コマンドと同じTargetSpecで対象を解決します。 */
    var temporaryEntityTargetSpec: TargetSpec? = null,
    /** TEMP_SET LOCATIONの位置側です。通常コマンドと同じPositionSpecで解決します。 */
    var temporaryLocationPositionSpec: PositionSpec? = null,
    /** TEMP_SET LOCATIONの向き側です。通常コマンドと同じFacingSpecで解決します。 */
    var temporaryLocationFacingSpec: FacingSpec? = null,
    /** 非リテラル一時変数の参照名です。空でなければ対応するリテラル値を置き換えます。 */
    var itemTempRef: String? = null,
    var blockTempRef: String? = null,
    var soundTempRef: String? = null,
    var effectTempRef: String? = null,
    var contextOverride: ExecutionContextSpec? = null,
    /** 欠損した旧JSONはBASEとし、既存のCONTEXT継承順序を変えません。 */
    var contextSource: ContextSource? = ContextSource.BASE,
    var snapshot: CommandGraph? = null,
) {
    fun string(key: String, default: String = "") = params[key]?.takeIf(String::isNotBlank) ?: default
    fun int(key: String, default: Int = 0) = params[key]?.toIntOrNull() ?: default
    fun double(key: String, default: Double = 0.0) = params[key]?.toDoubleOrNull() ?: default
    fun boolean(key: String, default: Boolean = false) = params[key]?.toBooleanStrictOrNull() ?: default

    fun isExplicitlyConfigured(key: String): Boolean = configuredFields?.contains(key) == true

    fun markConfigured(vararg keys: String) {
        if (keys.isEmpty()) return
        val fields = configuredFields ?: linkedSetOf<String>().also { configuredFields = it }
        fields += keys
    }

    /** 明示設定の記録を解除し、初期値へ戻した状態を表示へ正しく伝えます。 */
    fun clearConfigured(vararg keys: String) {
        if (keys.isEmpty()) return
        configuredFields?.removeAll(keys.toSet())
        if (configuredFields?.isEmpty() == true) configuredFields = null
    }

    /** 名前空間化された明示設定をまとめて消し、古い詳細フラグを残しません。 */
    fun clearConfiguredPrefix(prefix: String) {
        if (prefix.isEmpty()) return
        configuredFields?.removeIf { it.startsWith(prefix) }
        if (configuredFields?.isEmpty() == true) configuredFields = null
    }
}

enum class ContextSource { BASE, PREVIOUS }

/** ブロック操作が実行時に採用する配置方式です。保存値は明示的な小文字文字列にします。 */
enum class BlockOperationMode(val value: String) {
    SETBLOCK("setblock"),
    FILL("fill"),
    ;

    companion object {
        fun from(value: String?): BlockOperationMode? = entries.firstOrNull { it.value == value }
    }
}

val CommandNode.effectiveContextSource: ContextSource
    get() = contextSource ?: ContextSource.BASE

enum class TargetKind {
    NEAREST_PLAYER, NEARBY_PLAYERS,
    ALL_PLAYERS, RANDOM_PLAYER, NEAREST_ENTITY, NEARBY_ENTITIES, FIXED_ENTITY, TEMPORARY,
    /** 廃止予定：暗示的継承は行いません。新規設定では使用しません。 */
    @Deprecated("Context abolished")
    INHERITED_TARGET,
}

enum class TargetSort { NEAREST, FURTHEST, RANDOM }

data class SearchOriginSpec(
    val positionTemp: String? = null,
    val position: PositionSpec? = null,
) {
    fun hasAnySetting(): Boolean = positionTemp != null || position != null
}

data class TargetSpec(
    val kind: TargetKind,
    val entityType: String? = null,
    val minimumDistance: Double? = null,
    val maximumDistance: Double? = null,
    val limit: Int? = null,
    val sort: TargetSort = TargetSort.NEAREST,
    val gameMode: String? = null,
    val tag: String? = null,
    val name: String? = null,
    /** セレクターのdx/dy/dzに対応する非負の範囲です。基準は実行コンテキスト位置です。 */
    val dx: Double? = null,
    val dy: Double? = null,
    val dz: Double? = null,
    val fixedEntityId: UUID? = null,
    /** 対象探索の基準位置です。未設定時は制御ブロック位置を用います。 */
    val searchOrigin: SearchOriginSpec? = null,
    /** 一時変数参照時の変数名です。kind=TEMPORARY のときだけ有効です。 */
    val tempName: String? = null,
)

/**
 * DISKは保存形式上の既存値ですが、実際に参照するのはプログラムディスクではなく
 * 実行元の制御ブロックです。表示側では必ず「制御ブロック」として扱います。
 */
enum class PositionKind {
    CAPTURED, DISK, MYWORLD_SPAWN, COORDINATES, TEMPORARY,
    /** 廃止予定：コンテキスト経由解決は行いません。 */
    @Deprecated("Context abolished")
    EXECUTOR,
    /** 廃止予定：コンテキスト経由解決は行いません。 */
    @Deprecated("Context abolished")
    TARGET,
}
data class PositionSpec(
    val kind: PositionKind,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val yaw: Float? = null,
    val pitch: Float? = null,
    /** 一時変数参照時の変数名です。kind=TEMPORARY のときだけ有効です。 */
    val tempName: String? = null,
)

enum class FacingKind {
    CAPTURED, COORDINATES, MYWORLD_SPAWN, ROTATION, TEMPORARY,
    /** 廃止予定：向き不変として扱います。 */
    @Deprecated("Context abolished")
    INHERITED,
    /** 廃止予定：コンテキスト経由解決は行いません。 */
    @Deprecated("Context abolished")
    EXECUTOR,
    /** 廃止予定：コンテキスト経由解決は行いません。 */
    @Deprecated("Context abolished")
    TARGET,
}
data class FacingSpec(
    val kind: FacingKind,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val yaw: Float? = null,
    val pitch: Float? = null,
    /** 一時変数参照時の変数名です。kind=TEMPORARY のときだけ有効です。 */
    val tempName: String? = null,
)

data class ExecutionContextSpec(
    val executor: TargetSpec? = null,
    val target: TargetSpec? = null,
    val position: PositionSpec? = null,
    val facing: FacingSpec? = null,
) {
    /** 空のコンテキストは設定値ではなく、未設定と同じ意味になります。 */
    fun hasAnySetting(): Boolean = executor != null || target != null || position != null || facing != null
}

/** 実効値を持つノード単位コンテキストだけを「上書きあり」と判定します。 */
fun CommandNode.hasContextOverride(): Boolean = contextOverride?.hasAnySetting() == true

enum class ConditionKind(val key: LocalizationKey<String>) {
    TARGET_EXISTS(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_TARGET_EXISTS),
    PLAYER_STATE(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_PLAYER_STATE),
    VARIABLE_STATE(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_VARIABLE_STATE),
    BLOCK_STATE(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_BLOCK_STATE),
}

/** ワールド内変数の保存型。数値は常にdoubleで保持します。 */
enum class VariableType { NUMBER, STRING }

/** 変数操作の大分類。すべてMyWorld単位の定義へ反映します。 */
enum class VariableOperation { DEFINE, CHANGE }

/** CHANGE時に数値へ適用する詳細操作です。STRINGではASSIGNだけを許可します。 */
enum class VariableChangeMode { CALCULATE, ASSIGN }

data class WorldVariableValue(
    val type: VariableType,
    val numberValue: Double? = null,
    val stringValue: String? = null,
)

/**
 * 一時変数（実行内寿命）の型です。context型は作りません。
 *
 * リテラル利用できるのは NUMBER・STRING のみで `%{name}%` 記法を使います。
 * 複合6型（LOCATION/ITEM/BLOCK/ENTITY/SOUND/EFFECT）は型付き設定欄で定義し、
 * 一般テキストへの埋め込みはエラーとします。利用側は対応する構造化Specまたは
 * コマンド固有の一時変数参照欄から選択します。
 */
enum class TemporaryVariableType {
    NUMBER,
    STRING,
    /** 位置と向きを一体として扱う一時値です。旧POSITIONは読み込み時だけ受け付けます。 */
    LOCATION,
    ITEM,
    BLOCK,
    ENTITY,
    SOUND,
    EFFECT,
    ;

    companion object {
        /**
         * 保存済みJSONの旧POSITIONを新しいLOCATIONへ正規化します。
         * enumへPOSITIONを残すとentries()が増えてGUIへ旧概念が再登場するため、
         * 互換処理は保存文字列の境界だけに閉じ込めます。
         */
        fun parse(raw: String?): TemporaryVariableType? {
            val normalized = raw?.trim()?.uppercase(Locale.ROOT) ?: return null
            return when (normalized) {
                "POSITION" -> LOCATION
                else -> entries.firstOrNull { it.name == normalized }
            }
        }
    }
}

/** 一時変数の実行時値です。上書き可能で、実行終了時に破棄します。 */
data class TemporaryValue(
    val type: TemporaryVariableType,
    val numberValue: Double? = null,
    val stringValue: String? = null,
    /** LOCATION型の解決済み座標・向きです。位置と向きを分離して保持しません。 */
    val location: SavedLocation? = null,
    /** アイテム型の素材IDと実体（Base64）です。 */
    val item: String? = null,
    val itemData: String? = null,
    /** ブロック・効果・効果音のIDです。 */
    val block: String? = null,
    val entityId: UUID? = null,
    val sound: String? = null,
    val volume: Double? = null,
    val pitch: Double? = null,
    val effect: String? = null,
    val level: Int? = null,
    val seconds: Int? = null,
)

data class SavedLocation(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
)

enum class CommandType(
    val key: LocalizationKey<String>,
    val descriptionKey: LocalizationKey<List<String>>,
    val icon: Material,
    val defaults: Map<String, String>,
) {
    TELEPORT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_TELEPORT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_TELEPORT_DESCRIPTION, Material.ENDER_PEARL, emptyMap()),
    GIVE_ITEM(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_GIVE_ITEM, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_GIVE_ITEM_DESCRIPTION, Material.CHEST, mapOf("count" to "1")),
    ENTITY_ACTION(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_ACTION, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_ACTION_DESCRIPTION, Material.SADDLE, mapOf(
        "action" to "ride",
        "slot" to "HAND",
        "item" to "",
        "itemData" to "",
        "overwrite" to "false",
        "tagOperation" to "add",
        "tag" to "",
    )),
    DISPLAY_TEXT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISPLAY_TEXT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISPLAY_TEXT_DESCRIPTION, Material.WRITABLE_BOOK, mapOf(
        "mode" to "tellraw", "text" to "", "subtitle" to "", "fadeInSeconds" to "1", "staySeconds" to "3", "fadeOutSeconds" to "1"
    )),
    WAIT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_WAIT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_WAIT_DESCRIPTION, Material.CLOCK, mapOf("seconds" to "1")),
    SUMMON_ENTITY(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_SUMMON_ENTITY, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_SUMMON_ENTITY_DESCRIPTION, Material.ZOMBIE_SPAWN_EGG, mapOf(
        "entity" to "", "tags" to "", "customName" to ""
    )),
    PLAY_SOUND(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PLAY_SOUND, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PLAY_SOUND_DESCRIPTION, Material.NOTE_BLOCK, mapOf(
        "sound" to "", "volume" to "1.0", "pitch" to "1.0", "soundScope" to "CONTEXT"
    )),
    APPLY_EFFECT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_APPLY_EFFECT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_APPLY_EFFECT_DESCRIPTION, Material.POTION, mapOf(
        "effect" to "", "level" to "1", "seconds" to "30"
    )),
    CAMERA_SHAKE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CAMERA_SHAKE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CAMERA_SHAKE_DESCRIPTION, Material.SPYGLASS, mapOf(
        "intensity" to "1.0", "seconds" to "5", "shakeType" to "positional"
    )),
    BLOCK_OPERATION(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BLOCK_OPERATION, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BLOCK_OPERATION_DESCRIPTION, Material.BRICKS, mapOf(
        "operation" to "setblock", "block" to ""
    )),
    ENTITY_DELETE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_DELETE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_DELETE_DESCRIPTION, Material.BARRIER, emptyMap()),
    CONDITION(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONDITION, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONDITION_DESCRIPTION, Material.COMPARATOR, mapOf(
        "kind" to ConditionKind.TARGET_EXISTS.name,
        "inverted" to "false",
        "sneaking" to "",
        "item" to "",
        "itemData" to "",
        "variable" to "",
        "operator" to ">=",
        "value" to "0.0",
        "block" to "minecraft:air",
    )),
    CONTEXT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTEXT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTEXT_DESCRIPTION, Material.RECOVERY_COMPASS, mapOf(
        "executor" to "", "target" to "", "position" to "", "facing" to ""
    )),
    DISK_CALL(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISK_CALL, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISK_CALL_DESCRIPTION, Material.MUSIC_DISC_13, mapOf("diskId" to "")),
    /** ワールド内変数を定義・変更します。 */
    VARIABLE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_VARIABLE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_VARIABLE_DESCRIPTION, Material.REDSTONE, mapOf(
        "name" to "",
        "type" to VariableType.NUMBER.name,
        "operation" to VariableOperation.DEFINE.name,
        "changeMode" to VariableChangeMode.ASSIGN.name,
        "value" to "0.0",
    )),
    /** 一時変数を設定します。再設定は上書きとして扱います。 */
    TEMP_SET(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_TEMPORARY_VARIABLE_SET, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_TEMPORARY_VARIABLE_SET_DESCRIPTION, Material.REPEATER, mapOf(
        "name" to "",
        "tempType" to TemporaryVariableType.NUMBER.name,
        "value" to "0.0",
    )),
    MERGE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_MERGE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_MERGE_DESCRIPTION, Material.HOPPER, emptyMap()),
    FOR_START(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_START, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_START_DESCRIPTION, Material.REPEATER, mapOf(
        "count" to "1",
    )),
    FOR_END(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_END, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_END_DESCRIPTION, Material.COMPARATOR, emptyMap()),
    BREAK(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BREAK, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BREAK_DESCRIPTION, Material.BARRIER, emptyMap()),
    CONTINUE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTINUE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTINUE_DESCRIPTION, Material.ARROW, emptyMap());

    fun newNode() = CommandNode(type = this, params = defaults.toMutableMap())
}

/**
 * ノード自身へ実行コンテキストを上書きできるかを表すドメイン契約です。
 *
 * CONTEXTは専用コマンドとしてコンテキストを生成するため、この契約の対象外です。
 * VARIABLEも、値の読み書きと実行位置・対象の選択を一つの設定へ混在させないため、
 * ノード単位の上書きを持ちません。GUIだけで隠すと保存済みデータや実行経路に
 * 同じ機能が残るため、検証・実行・エクスポートもこの契約を参照します。
 */
fun CommandType.supportsContextOverride(): Boolean = when (this) {
    CommandType.TELEPORT,
    CommandType.GIVE_ITEM,
    CommandType.ENTITY_ACTION,
    CommandType.DISPLAY_TEXT,
    CommandType.SUMMON_ENTITY,
    CommandType.PLAY_SOUND,
    CommandType.APPLY_EFFECT,
    CommandType.CAMERA_SHAKE,
    CommandType.BLOCK_OPERATION,
    CommandType.ENTITY_DELETE,
    CommandType.CONDITION,
    CommandType.DISK_CALL,
    -> true

    CommandType.WAIT,
    CommandType.CONTEXT,
    CommandType.VARIABLE,
    CommandType.TEMP_SET,
    CommandType.MERGE,
    CommandType.FOR_START,
    CommandType.FOR_END,
    CommandType.BREAK,
    CommandType.CONTINUE,
    -> false
}

data class DiskPlacement(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val scriptId: UUID,
    val facing: String,
    var displayId: UUID?,
) {
    val key: String get() = "$world,$x,$y,$z"
}
