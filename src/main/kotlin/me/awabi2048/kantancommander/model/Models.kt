package me.awabi2048.kantancommander.model

import com.awabi2048.ccsystem.api.localization.LocalizationKey
import com.awabi2048.ccsystem.api.localization.generated.KantanKantanCommanderCleanKeys as KcKeys
import org.bukkit.Material
import java.util.Locale
import java.util.UUID

const val STRUCTURED_FORMAT_VERSION = 17
const val TICKS_PER_SECOND = 20
const val MAX_TIMER_SECONDS = 86_400
const val DEFAULT_TIMER_SECONDS = 1.0
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
     * ジェスチャーGUIで折りたたみ表示するfor開始ノードのID群です。
     *
     * 新形式（v11）では折り畳み自体をノード種別（FOLDED_FOR／FOLDED_IF）として
     * 永続化するため使用しません。旧JSONの読み捨て互換のために項目だけ残します。
     */
    var collapsedForStartIds: MutableSet<UUID>? = null,
    /**
     * ジェスチャーGUIで折りたたみ表示する条件分岐ノードのID群です。
     *
     * 新形式（v11）では折り畳み自体をノード種別（FOLDED_FOR／FOLDED_IF）として
     * 永続化するため使用しません。旧JSONの読み捨て互換のために項目だけ残します。
     */
    var collapsedConditionIds: MutableSet<UUID>? = null,
    /**
     * ノードを持たないスクリプトでも、プログラム名またはタイマーを明示的に
     * 編集したことを保持します。配置からのディスク出力可否は値の非空判定では
     * なく、この編集状態とグラフの両方を共通判定するため、初期配置の既定名を
     * 誤って「内容あり」と扱いません。旧JSONには存在しないためfalseで復元します。
     */
    var contentModified: Boolean = false,
    /**
     * issue #8 の関数引数定義です。
     *
     * ディスクとして取り出した時点で外部から呼び出せる関数として扱い、引数は
     * 呼び出し先の実行開始時点で定義済みのEXECUTION変数になります。別途の
     * 公開設定は作りません。旧JSONには存在しないため空で復元します。
     * 引数はすべて必須とし、optional／default値は初期実装に含めません。
     */
    var arguments: List<DiskArgument> = emptyList(),
    /**
     * issue #7 の起動方法の明示指定です。
     *
     * 旧JSONには存在しないため null で復元し、その場合は [effectiveTrigger] が
     * 従来の `activation`＋`timer` から読み替えます。新規保存は常に明示値を書き、
     * 従来欄も整合させるため、null は旧保存の意味だけを持ちます。
     */
    var trigger: ScriptTrigger? = null,
)

/**
 * issue #8 の関数引数定義です。
 *
 * name は EXECUTION変数名、type は値型です。呼び出し側は全引数に対応する値を
 * 設定します。
 */
data class DiskArgument(
    val name: String = "",
    val type: ValueType = ValueType.NUMBER,
)

/**
 * issue #8 の「値を返す」ノードの値型を推論します。
 *
 * 変数由来の場合は呼び出し側の定義表で解決するため、ここではリテラルの
 * 推論だけを行います。真偽値リテラル・数値リテラル以外は文字列とします。
 */
fun inferLiteralType(raw: String): ValueType {
    val normalized = raw.trim().lowercase()
    if (normalized == "true" || normalized == "false") return ValueType.BOOLEAN
    if (raw.trim().toDoubleOrNull()?.isFinite() == true) return ValueType.NUMBER
    return ValueType.STRING
}

/**
 * issue #8 の呼出側が保持する被呼出側の引数宣言です。
 *
 * snapshot と同じく取り出し時点の独立複写であり、被呼出側の編集で黙って
 * 変わりません。形式は `name:TYPE` のカンマ区切りです。検証層で live の
 * 定義との差分（引数名変更・削除）を検出します。
 */
fun CommandNode.declaredArguments(): List<DiskArgument> {
    val raw = params["declaredArgs"]?.takeIf(String::isNotBlank) ?: return emptyList()
    return raw.split(',').mapNotNull { entry ->
        val name = entry.substringBefore(':').trim()
        val type = ValueType.parse(entry.substringAfter(':', "")) ?: return@mapNotNull null
        if (!CommandValueRules.isVariableName(name)) return@mapNotNull null
        DiskArgument(name, type)
    }
}

/** 引数宣言を保存形式へ符号化します。 */
fun encodeDeclaredArguments(arguments: List<DiskArgument>): String =
    arguments.joinToString(",") { "${it.name}:${it.type.name}" }

/** 配置されたかんたんコマンダー制御ブロックからディスクへ出力できる内容があるかを判定します。 */
fun DiskScript.hasDiskContent(): Boolean = contentModified || graph.nodes.isNotEmpty()

/**
 * 折りたたみ表示するfor開始ノードのID群です。
 *
 * 新形式（v11）では使用しません。旧JSONの読み捨て互換のために残します。
 */
@Deprecated("折り畳みはFOLDED_FORへ移行しました。読み捨て専用です。")
fun DiskScript.collapsedForStarts(): MutableSet<UUID> =
    collapsedForStartIds ?: linkedSetOf<UUID>().also { collapsedForStartIds = it }

/**
 * 折りたたみ表示する条件分岐ノードのID群です。
 *
 * 新形式（v11）では使用しません。旧JSONの読み捨て互換のために残します。
 */
@Deprecated("折り畳みはFOLDED_IFへ移行しました。読み捨て専用です。")
fun DiskScript.collapsedConditions(): MutableSet<UUID> =
    collapsedConditionIds ?: linkedSetOf<UUID>().also { collapsedConditionIds = it }

data class TimerSetting(
    var enabled: Boolean = false,
    var intervalSeconds: Double = DEFAULT_TIMER_SECONDS,
) {
    /**
     * 保存・実行へ渡す秒数をモデル内で許す範囲へ寄せます。下限は設けないため、
     * 非有限・0以下は既定値へ、上限超過は上限へ丸めます。tick境界の整合性は
     * 保存・実行前検証（CommandValueRules.isTimerSeconds）が担保します。
     */
    fun normalized() = copy(
        intervalSeconds = intervalSeconds
            .takeIf { it.isFinite() && it > 0.0 }
            ?.coerceAtMost(MAX_TIMER_SECONDS.toDouble())
            ?: DEFAULT_TIMER_SECONDS,
    )
    /** Minecraftの内部スケジューラへ渡すtick値は、このモデル境界でだけ算出します。 */
    val intervalTicks: Long get() =
        CommandValueRules.secondsToTicks(normalized().intervalSeconds) ?: 1L
}

enum class ActivationMode {
    NEEDS_REDSTONE,
    ALWAYS_ACTIVE,
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
            copied[id] = node.deepCopy()
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
    /**
     * issue #5 の型付き条件比較で使う領域述語の領域です。
     *
     * VALUE subject＋領域演算子（`in_region`／`not_in_region`）のときだけ有効で、
     * 左辺のLOCATION／ENTITY値がこの内側にあるかを判定します。解決・検証・出力は
     * #4 の共通 resolver を使い、Condition側で独自の領域計算を持ちません。
     * 旧JSONには存在しないため欠落時は null です。
     */
    var regionSpec: RegionSpec? = null,
    /** 制御ブロック状態条件で選択された判定項目です。複数選択時はAND評価します。 */
    var controlBlockStates: MutableSet<ControlBlockStateKind>? = null,
    /** ブロック操作の単一配置位置（setblock相当）。 */
    var blockPositionSpec: PositionSpec? = null,
    /** ブロック操作の範囲始点・終点（fill相当）。 */
    var blockFromSpec: PositionSpec? = null,
    var blockToSpec: PositionSpec? = null,
    /** PLAY_SOUNDで「マイワールド内全域」以外を選んだ場合の再生位置です。 */
    var soundPositionSpec: PositionSpec? = null,
    /** PARTICLEの散布中心です。未設定時は実行コンテキストの位置を使用します。 */
    var particlePositionSpec: PositionSpec? = null,
    /**
     * PARTICLEの図形表示で使う第二位置です。LINEの終点・BOXの対角点など、
     * 始点（particlePositionSpec）と対になる場合だけ設定します。
     */
    var particleEndPositionSpec: PositionSpec? = null,
    /** FIREWORK_LAUNCHの打ち上げ位置です。未設定時は実行コンテキストの位置を使用します。 */
    var fireworkPositionSpec: PositionSpec? = null,
    /** エンティティ召喚で指定された場合の召喚位置です。未設定時は制御ブロック位置を使用します。 */
    var summonPositionSpec: PositionSpec? = null,
    /**
     * issue #26 のコマンド単位の実行位置です。
     *
     * 廃止済みコンテキスト設定は復活させず、この位置だけを対応コマンドの設定として
     * 保持します。後続コマンドへ継承せず、未設定時は制御ブロック位置を使用します。
     * 対象探索の基準位置・コマンド固有位置の既定値として参照します。
     */
    var executionPositionSpec: PositionSpec? = null,
    /** TEMP_SET ENTITYの参照元です。通常コマンドと同じTargetSpecで対象を解決します。 */
    var temporaryEntityTargetSpec: TargetSpec? = null,
    /** TEMP_SET LOCATIONの位置側です。通常コマンドと同じPositionSpecで解決します。 */
    var temporaryLocationPositionSpec: PositionSpec? = null,
    /** TEMP_SET LOCATIONの向き側です。通常コマンドと同じFacingSpecで解決します。 */
    var temporaryLocationFacingSpec: FacingSpec? = null,
    /** 非リテラル一時変数の参照名です。空でなければ対応するリテラル値を置き換えます。 */
    var itemTempRef: String? = null,
    var blockTempRef: String? = null,
    var snapshot: CommandGraph? = null,
    /**
     * 折り畳みノード（FOLDED_FOR／FOLDED_IF）が内包する構造全体です。
     *
     * 外側グラフには折り畳みノード1個だけが通常ノードと同じ接続形状（単一next）
     * で残り、中身はこの内部グラフへ退避します。FOLDED_FORはFOR_START〜FOR_END
     * の全体、FOLDED_IFはCONDITION〜MERGEの全体を、元のIDのまま保持します。
     * 折り畳み／展開のたびに再構成され、通常グラフの中にそのまま永続化されます。
     * 折り畳みノード以外では常にnullです。
     */
    var foldedGraph: CommandGraph? = null,
) {
    fun string(key: String, default: String = "") = params[key]?.takeIf(String::isNotBlank) ?: default
    fun int(key: String, default: Int = 0) = params[key]?.toIntOrNull() ?: default
    fun double(key: String, default: Double = 0.0) = params[key]?.toDoubleOrNull() ?: default
    fun boolean(key: String, default: Boolean = false) = params[key]?.toBooleanStrictOrNull() ?: default

    /** グラフ更新とコマンド複製が同じ設定コピー規則を使うための、再帰的なノード複製です。 */
    fun deepCopy(): CommandNode = copy(
        params = params.toMutableMap(),
        configuredFields = configuredFields?.toMutableSet(),
        targetSpec = targetSpec?.copy(),
        secondaryTargetSpec = secondaryTargetSpec?.copy(),
        destinationSpec = destinationSpec?.copy(),
        destinationTargetSpec = destinationTargetSpec?.copy(),
        destinationFacingSpec = destinationFacingSpec?.copy(),
        conditionPositionSpec = conditionPositionSpec?.copy(),
        regionSpec = regionSpec?.copy(),
        controlBlockStates = controlBlockStates?.toMutableSet(),
        blockPositionSpec = blockPositionSpec?.copy(),
        blockFromSpec = blockFromSpec?.copy(),
        blockToSpec = blockToSpec?.copy(),
        soundPositionSpec = soundPositionSpec?.copy(),
        particlePositionSpec = particlePositionSpec?.copy(),
        particleEndPositionSpec = particleEndPositionSpec?.copy(),
        fireworkPositionSpec = fireworkPositionSpec?.copy(),
        summonPositionSpec = summonPositionSpec?.copy(),
        executionPositionSpec = executionPositionSpec?.copy(),
        temporaryEntityTargetSpec = temporaryEntityTargetSpec?.copy(),
        temporaryLocationPositionSpec = temporaryLocationPositionSpec?.copy(),
        temporaryLocationFacingSpec = temporaryLocationFacingSpec?.copy(),
        snapshot = snapshot?.deepCopy(),
        foldedGraph = foldedGraph?.deepCopy(),
    )

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

/** ブロック操作が実行時に採用する配置方式です。保存値は明示的な小文字文字列にします。 */
enum class BlockOperationMode(val value: String) {
    SETBLOCK("setblock"),
    FILL("fill"),
    ;

    companion object {
        fun from(value: String?): BlockOperationMode? = entries.firstOrNull { it.value == value }
    }
}

enum class TargetKind {
    NEAREST_PLAYER, NEARBY_PLAYERS,
    ALL_PLAYERS, RANDOM_PLAYER, NEAREST_ENTITY, NEARBY_ENTITIES, FIXED_ENTITY, TEMPORARY,
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
    /** セレクターのdx/dy/dzに対応する非負の範囲です。基準は実行位置です。 */
    val dx: Double? = null,
    val dy: Double? = null,
    val dz: Double? = null,
    /**
     * issue #4 の明示領域による絞り込みです。未設定時は従来の dx/dy/dz と
     * 距離指定だけを使います。設定時は基準（anchor）・範囲・距離を
     * [RegionMath] と共通 resolver で解決し、Target・Condition・出力で
     * 同じ判定を使います。旧保存には存在しないため欠落時は null です。
     */
    val region: RegionSpec? = null,
    val fixedEntityId: UUID? = null,
    /** 対象探索の基準位置です。未設定時は制御ブロック位置を用います。 */
    val searchOrigin: SearchOriginSpec? = null,
    /** 一時変数参照時の変数名です。kind=TEMPORARY のときだけ有効です。 */
    val tempName: String? = null,
    /**
     * プリセットから詳細設定へ変換した来歴です。
     *
     * 詳細設定入室時にプリセット相当の明示値（上限数1・距離下限0等）を
     * 指定した場合だけ真にします。利用者が手入力で等価値にしても真に
     * しないことで、プリセット選択表示への誤判定を防ぎます。
     * 検証・実行・出力はこの値を無視し、Gson永続の既定値は偽です。
     */
    val convertedFromPreset: Boolean = false,
) {
    /**
     * issue #29: 静的に0..1件と確定する指定かを返します。
     *
     * 単一限定の位置でも取得元ではなく件数で判定し、最寄り等の0..1指定を
     * 許可します。上限引き上げで複数になり得る指定は含みません。
     */
    fun isStaticallySingle(): Boolean {
        if ((limit ?: 1) > 1) return false
        return when (kind) {
            TargetKind.NEAREST_PLAYER,
            TargetKind.NEAREST_ENTITY,
            TargetKind.RANDOM_PLAYER,
            TargetKind.FIXED_ENTITY,
            TargetKind.TEMPORARY,
            -> true
            TargetKind.NEARBY_PLAYERS,
            TargetKind.NEARBY_ENTITIES,
            TargetKind.ALL_PLAYERS,
            -> false
        }
    }

    /** issue #29: 複数件になり得る指定かを返します。 */
    fun isMultiCapable(): Boolean = !isStaticallySingle()
}

/**
 * DISKは保存形式上の既存値ですが、実際に参照するのはプログラムディスクではなく
 * 実行元の制御ブロックです。表示側では必ず「制御ブロック」として扱います。
 */
enum class PositionKind {
    CAPTURED, DISK, MYWORLD_SPAWN, COORDINATES, TEMPORARY,
    /** issue #29 の対象エンティティ位置です。内包する対象指定の各位置へ0..N件解決します。 */
    TARGET,
}

/**
 * issue #29 のMyWorldスポーン種別です。
 *
 * 旧来の単一 MYWORLD_SPAWN 相当はメンバー用スポーンとして互換移行します。
 * 実行時はBukkitの通常world spawnではなく、MyWorldManagerが保持する
 * 該当スポーン位置を参照します。
 */
enum class MyworldSpawnKind {
    MEMBER,
    GUEST,
}

/**
 * issue #4 の座標成分の意味付けです。スレッド指示により基準は `session.origin`
 * （制御ブロック位置の複製）に固定します。実行コンテキスト位置は基準にしません。
 *
 * - ABSOLUTE: 絶対値として扱います。従来の保存値と同じ意味です。
 * - RELATIVE: `~` 相当で、`session.origin` からの差分として扱います。
 * - LOCAL: `^` 相当で、`session.origin` の位置と向きを基準にした左・上・前として扱います。
 *   角度には基準が定義できないため使用できず、検証で拒否します。
 */
enum class CoordinateSemantics {
    ABSOLUTE,
    RELATIVE,
    LOCAL,
}

/**
 * Minecraftのyawを (-180, 180] へ正規化します。
 * 相対指定の差分加算など、任意の有限値を起点に畳み込むために使います。
 */
fun wrapMinecraftYaw(degrees: Float): Float {    var value = degrees % 360f
    if (value <= -180f) value += 360f
    if (value > 180f) value -= 360f
    return value
}

/**
 * Minecraftのpitchを [-90, 90] へ丸めます。
 * 相対指定の差分加算が範囲を超過した場合に、実行時の向きを壊さないために使います。
 */
fun clampMinecraftPitch(degrees: Float): Float = degrees.coerceIn(-90f, 90f)

/**
 * 指定軸の現在の意味付けを返します。不明な軸名は絶対として扱います。
 * GUIの循環切替えと実行層で同じ軸名（x/y/z/yaw/pitch）を使います。
 */
fun PositionSpec.axisSemantics(axis: String): CoordinateSemantics = when (axis) {
    "x" -> semanticsX
    "y" -> semanticsY
    "z" -> semanticsZ
    "yaw" -> semanticsYaw
    "pitch" -> semanticsPitch
    else -> CoordinateSemantics.ABSOLUTE
}

/** 指定軸の意味付けだけを変えた複製を返します。不明な軸名はそのまま返します。 */
fun PositionSpec.withAxisSemantics(axis: String, value: CoordinateSemantics): PositionSpec = when (axis) {
    "x" -> copy(semanticsX = value)
    "y" -> copy(semanticsY = value)
    "z" -> copy(semanticsZ = value)
    "yaw" -> copy(semanticsYaw = value)
    "pitch" -> copy(semanticsPitch = value)
    else -> this
}

/** 指定軸の現在の意味付けを返します。不明な軸名は絶対として扱います。 */
fun FacingSpec.axisSemantics(axis: String): CoordinateSemantics = when (axis) {
    "yaw" -> semanticsYaw
    "pitch" -> semanticsPitch
    else -> CoordinateSemantics.ABSOLUTE
}

/** 指定軸の意味付けだけを変えた複製を返します。不明な軸名はそのまま返します。 */
fun FacingSpec.withAxisSemantics(axis: String, value: CoordinateSemantics): FacingSpec = when (axis) {
    "yaw" -> copy(semanticsYaw = value)
    "pitch" -> copy(semanticsPitch = value)
    else -> this
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
    /**
     * 成分ごとの解釈です。COORDINATES／CAPTURED の座標成分に RELATIVE／LOCAL を許し、
     * yaw／pitch には ABSOLUTE／RELATIVE を許します。それ以外の種別・組合せは
     * 検証で拒否します。既定は全 ABSOLUTE で従来の保存値と同じ意味です。
     */
    val semanticsX: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
    val semanticsY: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
    val semanticsZ: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
    val semanticsYaw: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
    val semanticsPitch: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
    /**
     * issue #29 のMyWorldスポーン種別です。kind=MYWORLD_SPAWN のときだけ有効です。
     * 未設定（旧データ）はメンバー用スポーンとして互換移行します。
     */
    val myworldSpawn: MyworldSpawnKind? = null,
    /**
     * issue #29 の対象内包指定です。kind=TARGET のときだけ有効であり、
     * 選択されたエンティティそれぞれの位置へ0..N件解決します。
     */
    val target: TargetSpec? = null,
) {
    /** 座標・角度のいずれかに絶対以外の指定があるかを返します。 */
    fun hasNonAbsoluteSemantics(): Boolean =
        listOf(semanticsX, semanticsY, semanticsZ, semanticsYaw, semanticsPitch)
            .any { it != CoordinateSemantics.ABSOLUTE }
}

/**
 * issue #4 の移植用ベクトル骨組みです。永続化可能な値表現として保持し、
 * Bukkit実体は長期保持しません。演算・property・乱数接続は #3 送りです。
 */
data class SpatialVector(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    /** 有限値のみを有効とします。非有限は検証・実行で拒否します。 */
    fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    operator fun plus(other: SpatialVector) =
        SpatialVector(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: SpatialVector) =
        SpatialVector(x - other.x, y - other.y, z - other.z)

    fun lengthSquared(): Double = x * x + y * y + z * z
}

/**
 * issue #4 の領域形状です。設定方法は直方体＋距離を継続するため、
 * 初回は CUBOID のみ実行対象とし、SPHERE／CYLINDER は予約に留めます。
 */
enum class RegionKind {
    CUBOID,
    SPHERE,
    CYLINDER,
}

/**
 * issue #4 の領域基準です。スレッド指示により4系統を骨組みに含めます。
 * FIXEDは明示座標、CONTROL_BLOCKは制御ブロック位置、TARGET_ENTITYは対象実体位置、
 * TEMPORARYはLOCATION型一時変数を指します。
 */
enum class RegionAnchorKind {
    FIXED,
    CONTROL_BLOCK,
    TARGET_ENTITY,
    TEMPORARY,
}

/**
 * 領域基準の指定です。Target絞り込み・Condition判定・出力の共通 resolver で
 * 解決します。FIXEDは明示座標、CONTROL_BLOCKは制御ブロック位置、
 * TARGET_ENTITYは対象実体位置、TEMPORARYはLOCATION型一時変数を指します。
 */
data class RegionAnchorSpec(
    val kind: RegionAnchorKind = RegionAnchorKind.CONTROL_BLOCK,
    /** FIXED のときの基準位置です。他基準では未使用です。 */
    val position: PositionSpec? = null,
    /** TEMPORARY のときの一時変数名です。他基準では未使用です。 */
    val tempName: String? = null,
    /** TARGET_ENTITY のときの対象指定です。他基準では未使用です。 */
    val target: TargetSpec? = null,
) {
    fun hasAnySetting(): Boolean =
        kind != RegionAnchorKind.CONTROL_BLOCK || position != null || tempName != null || target != null
}

/**
 * issue #4 の明示領域です。直方体（dx/dy/dz相当）と距離指定を持ち、
 * Target絞り込み・Condition判定・出力で同じ resolver を使います。
 * yaw/pitchは持ちません。SPHERE／CYLINDER は予約のため検証で拒否します。
 */
data class RegionSpec(
    val kind: RegionKind = RegionKind.CUBOID,
    val anchor: RegionAnchorSpec = RegionAnchorSpec(),
    /** 直方体の範囲です。vanillaのdx/dy/dz相当で、非負の有限値を要求します。 */
    val dx: Double? = null,
    val dy: Double? = null,
    val dz: Double? = null,
    /** 球・円筒予約を含む距離条件です。0以上の有限値を要求します。 */
    val minimumDistance: Double? = null,
    val maximumDistance: Double? = null,
) {
    /** 未指定はその条件で制限しない扱いです。UI都合の値を暗黙保存しません。 */
    fun hasAnySetting(): Boolean =
        dx != null || dy != null || dz != null ||
            minimumDistance != null || maximumDistance != null ||
            anchor.hasAnySetting() || kind != RegionKind.CUBOID
}

enum class FacingKind {
    COORDINATES, ROTATION, TEMPORARY,
    /** 指定実体の位置を向く注視です。単一体参照へ帰着します。 */
    TARGET,
    /** 指定実体と同じ向きを向く複写です。単一体参照へ帰着します。 */
    COPY_ORIENTATION,
}
data class FacingSpec(
    val kind: FacingKind,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val yaw: Float? = null,
    val pitch: Float? = null,
    /** 一時変数参照時の変数名です。kind=TEMPORARY のときだけ有効で、LOCATION型のみを参照します。 */
    val tempName: String? = null,
    /**
     * 注視・向き複写の参照先実体です。kind=TARGET/COPY_ORIENTATION のときだけ有効で、
     * 単一体に制限したTargetSpecとして解決します。旧JSONには存在しないためnull許容です。
     * 作業ブランチの対象内包指定と同一概念であり、名称を `target` へ統一します。
     */
    val target: TargetSpec? = null,
    /**
     * 向き成分ごとの解釈です。ROTATION のyaw／pitchに
     * ABSOLUTE／RELATIVE を許します。LOCALと他種別への付与は検証で拒否します。
     * 注視点（COORDINATES のx/y/z）は絶対据え置きです。
     */
    val semanticsYaw: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
    val semanticsPitch: CoordinateSemantics = CoordinateSemantics.ABSOLUTE,
) {
    /** 向きのいずれかに絶対以外の指定があるかを返します。 */
    fun hasNonAbsoluteSemantics(): Boolean =
        semanticsYaw != CoordinateSemantics.ABSOLUTE || semanticsPitch != CoordinateSemantics.ABSOLUTE
}

/**
 * 実行エンジンがコマンドをMinecraftの実行状態へ変換するための内部状態です。
 * GUIから保存する設定ではなく、実行元・対象・位置・向きを一つの状態として
 * 引き回す必要がある処理だけで使用します。
 */
internal data class ExecutionContextSpec(
    val executor: TargetSpec? = null,
    val target: TargetSpec? = null,
    val position: PositionSpec? = null,
    val facing: FacingSpec? = null,
) {
    /** 空の実行状態は、追加のexecute句が不要であることを表します。 */
    fun hasAnySetting(): Boolean = executor != null || target != null || position != null || facing != null
}

/**
 * issue #27: 条件分岐の判定対象です。条件の読込・検証・実行・表示・出力の単一正本です。
 *
 * 旧 `kind` 分類（`TARGET_EXISTS`／`PLAYER_STATE` 等）は廃止し、この5区分へ置き換えます。
 * `プレイヤー` と `その他エンティティ` は UI 上区別しますが、条件評価上はいずれも
 * Entity 系として同じ基本ルール（一致対象が1件以上存在すれば成立・絞り込みはAND）を用います。
 * 旧 `kind` 保存は読込時に LegacyConditionMigration で判定対象へ正本化し、
 * 新規保存では `subject` だけを記録します。
 */
enum class ConditionSubject(val key: LocalizationKey<String>) {
    PLAYER(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_PLAYER_STATE),
    OTHER_ENTITY(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_ENTITY_STATE),
    BLOCK(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_BLOCK_STATE),
    VARIABLE(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_VARIABLE_STATE),
    CONTROL_BLOCK(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_CONTROL_BLOCK_STATE),
    /**
     * issue #5 の型付き条件比較です。
     *
     * 左辺（変数）・演算子・右辺（リテラル）の共通モデルで比較します。既存5区分の
     * 専用predicateは残し、新しい値型・property・Regionへの拡張点にします。
     */
    VALUE(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_VALUE_STATE),
}

/**
 * issue #2 の共通値型の表示キーを返します。両GUIで同じ文言を使います。
 *
 * 型名の表示対応はこの一点に集約し、画面ごとの複製を作りません。
 */
fun ValueType.displayKey(): LocalizationKey<String> = when (this) {
    ValueType.NUMBER -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_NUMBER
    ValueType.STRING -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_TEXT
    ValueType.BOOLEAN -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_TYPE_BOOLEAN
    ValueType.LOCATION -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_POSITION
    ValueType.VECTOR -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_VARIABLE_TYPE_VECTOR
    ValueType.ITEM -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_ITEM
    ValueType.BLOCK -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_FIELD_BLOCK
    ValueType.ENTITY -> KcKeys.KANTAN_COMMANDER_CLEAN_GUI_OPTION_FIXED_ENTITY
}

/**
 * ノードの判定対象を返します。条件の単一正本である `params["subject"]` だけを読みます。
 *
 * 未設定・不正値は null とし、旧 `kind` への推測フォールバックは行いません。
 * null は検証層で未設定として拒否し、実行層では評価失敗として停止します。
 */
fun CommandNode.conditionSubject(): ConditionSubject? =
    params["subject"]?.takeIf(String::isNotBlank)
        ?.let { runCatching { ConditionSubject.valueOf(it) }.getOrNull() }

/**
 * 判定対象を設定します。`subject` 欄だけを書き換えます。
 *
 * 残存する旧 `kind` 欄があれば除去し、単一正本にします。
 * 対象固有の詳細欄（sneaking/item/variable/operator/value/block 等）は削除せず保持します。
 * 評価・検証側で現在の判定対象に関係ない欄は無視するため、対象切替えでの入力消失を防ぎます。
 */
fun CommandNode.setConditionSubject(subject: ConditionSubject) {
    params["subject"] = subject.name
    params.remove("kind")
    markConfigured("subject")
    clearConfigured("kind")
}

/**
 * issue #2 の変数scopeを返します。
 *
 * scope 欄のない旧ノードは WORLD として読み替えます。不正値は null とし、
 * 検証層で未対応として拒否します。
 */
fun CommandNode.variableScope(): VariableScope {
    val raw = params["scope"]
    if (raw.isNullOrBlank()) return VariableScope.WORLD
    return VariableScope.parse(raw) ?: VariableScope.WORLD
}

/** scope 欄の厳密な解釈です。不正値を検出する必要がある検証向けに使います。 */
fun CommandNode.variableScopeStrict(): VariableScope? {
    val raw = params["scope"]
    if (raw.isNullOrBlank()) return VariableScope.WORLD
    return VariableScope.parse(raw)
}

/** 制御ブロックの状態条件として選択できる判定項目です。 */
enum class ControlBlockStateKind(val key: LocalizationKey<String>) {
    REDSTONE_INPUT(KcKeys.KANTAN_COMMANDER_CLEAN_CONDITION_CONTROL_BLOCK_REDSTONE_INPUT),
}

/**
 * 制御ブロック状態条件の集合評価を一箇所へ集約します。
 * 将来の判定項目追加時も、複数選択の意味（すべて成立するAND）を実行器とテストで共有します。
 */
object ControlBlockStateConditionPolicy {
    fun matches(selected: Set<ControlBlockStateKind>, redstoneInput: Boolean): Boolean =
        selected.isNotEmpty() && selected.all { state ->
            when (state) {
                ControlBlockStateKind.REDSTONE_INPUT -> redstoneInput
            }
        }
}

/** Gsonで旧JSONを読み込んだ場合もnull集合を空集合として扱う共通アクセサです。 */
fun CommandNode.selectedControlBlockStates(): Set<ControlBlockStateKind> = controlBlockStates.orEmpty()

/** 制御ブロック状態条件の選択を反映し、空集合は保存上nullへ正規化します。 */
fun CommandNode.toggleControlBlockState(state: ControlBlockStateKind) {
    val next = controlBlockStates.orEmpty().toMutableSet()
    if (!next.add(state)) next.remove(state)
    controlBlockStates = next.takeIf { it.isNotEmpty() }
}

/**
 * 変数操作の大分類です。
 *
 * - DEFINE: 新規定義のみ。既存名は拒否します。
 * - SET: 定義済み変数の値設定。未定義名は拒否します（暗黙定義しません）。
 *   型は定義済みの型を正とし、型不一致も拒否します。
 * - CHANGE: 定義済み変数の変更のみ。未定義は拒否します。NUMBERでは
 *   changeMode（ASSIGN／CALCULATE）を選択します。
 * - COPY: 定義済みの別変数へ値を複写します。複写先も定義済みである必要があり、
 *   型は複写元の型を引き継ぎます。
 * - DELETE: 定義と値を削除します。
 *
 * #2 の操作仕様であり、EXECUTION／WORLD の両scopeで同じ意味を持ちます。
 * いずれも明示定義を前提とし、未定義名への書き込みは行いません。
 * ENTITY scope の DEFINE 不要契約は #22 で別途定めます。
 */
enum class VariableOperation { DEFINE, SET, CHANGE, COPY, DELETE }

/**
 * 値を書き込む操作の値生成方法です。
 *
 * - ASSIGN: 値欄・型別設定のリテラル（テンプレート参照を含む）を代入します。
 * - CALCULATE: 数値計算式を評価して代入します（NUMBERのみ）。
 * - PROPERTY: 別変数の詳細値を型安全なadapterで取得して代入します（#3）。
 * - RANDOM: 乱数を生成して代入します（#3、NUMBERのみ）。
 *
 * #2 では CHANGE（NUMBER）の詳細として導入し、#3 で DEFINE／SET の値生成へ
 * 拡張しました。COPY／DELETE では使いません。
 */
enum class VariableChangeMode { CALCULATE, ASSIGN, PROPERTY, RANDOM }

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
    PLACE_ITEM(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PLACE_ITEM, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PLACE_ITEM_DESCRIPTION, Material.ITEM_FRAME, mapOf(
        // v17で改名しました。テレポートの移動先（構造値）と言語上区別します。
        // 旧 "destination" は読込時に移行します。
        "placeDestination" to PlaceItemDestination.EQUIPMENT.paramValue,
        "slot" to PlaceItemDestination.defaultSlot(PlaceItemDestination.EQUIPMENT),
        "item" to "",
        "itemData" to "",
        "count" to "1",
        "overwrite" to "false",
    )),
    // ENTITY_ACTIONは即時的な移動・関係変化（ride／dismount／motion）に限定します。
    // 旧tagはENTITY_OPERATION、旧equipはPLACE_ITEMへ移行済みのため既定値に含めません。
    ENTITY_ACTION(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_ACTION, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_ACTION_DESCRIPTION, Material.SADDLE, mapOf(
        "action" to "ride",
        "motionX" to "0.0",
        "motionY" to "0.0",
        "motionZ" to "0.0",
    )),
    /**
     * 「エンティティを操作」です。
     *
     * 統合選択形式で、設定対象は単一ID [EntityDataPath.PARAM_PATH] で表します。
     * 旧形式の `operation` 3択・modifier操作は廃止し、タグは一覧管理、
     * attributeはbase値設定のみ、データは型付き設定として扱います。
     * 即時的な移動・関係変化（ride／dismount／motion）はENTITY_ACTIONに残し、
     * Entity自身の状態・データ変更はこちらへ分離します。
     */
    ENTITY_OPERATION(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_OPERATION, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_ENTITY_OPERATION_DESCRIPTION, Material.NAME_TAG, mapOf(
        EntityDataPath.PARAM_PATH to "",
        EntityTagEntries.PARAM_ENTRIES to "",
        EntityAttributeSupport.PARAM_VALUE to "0.0",
        EntityDataSchema.PARAM_VALUE to "",
    )),
    DISPLAY_TEXT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISPLAY_TEXT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISPLAY_TEXT_DESCRIPTION, Material.WRITABLE_BOOK, mapOf(
        "mode" to "tellraw", "text" to "", "subtitle" to "", "fadeInSeconds" to "1", "staySeconds" to "3", "fadeOutSeconds" to "1"
    )),
    WAIT(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_WAIT, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_WAIT_DESCRIPTION, Material.CLOCK, mapOf("seconds" to "1")),
    SUMMON_ENTITY(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_SUMMON_ENTITY, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_SUMMON_ENTITY_DESCRIPTION, Material.ZOMBIE_SPAWN_EGG, mapOf(
        // v17で改名しました。一時対象指定の "entity" と言語上区別します。
        // 旧 "entity" は読込時に移行します。
        // issue #10: 表示名は共通スキーマの個別設定（`summonData_custom_name`）へ
        // 統合し、独立 `customName` は新規保存に含めません。旧値は読込時に移行します。
        "summonEntity" to "", "tags" to ""
    )),
    PLAY_SOUND(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PLAY_SOUND, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PLAY_SOUND_DESCRIPTION, Material.NOTE_BLOCK, mapOf(
        "sound" to "", "volume" to "1.0", "pitch" to "1.0", "soundScope" to "POSITION",
        "soundOperation" to SoundOperation.PLAY.name, "soundCategory" to ""
    )),
    PARTICLE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PARTICLE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PARTICLE_DESCRIPTION, Material.FIREWORK_STAR, mapOf(
        // 選択肢型に未選択と区別できない既定値は置きません。種類の未設定は
        // 未設定表示・未設定検証とし、数値欄の実行用既定値とは区別します。
        "particle" to "",
        "particleDeltaX" to "0.0",
        "particleDeltaY" to "0.0",
        "particleDeltaZ" to "0.0",
        "particleSpeed" to "0.0",
        "particleCount" to "1",
        "particleData" to "",
    )),
    /**
     * #30で分離した「図形を表示する」コマンドです。
     *
     * 既存PARTICLEのPOINT／散布設定（個数・速度・XYZ散布）は持たず、図形の
     * 形状・密度・形状数値だけを持ちます。表示位置は中心・始点用の
     * particlePositionSpecと終点・対角点用のparticleEndPositionSpecを使い、
     * 半径・角度・回転等の形状値はparams側の図形設定で扱います。
     * バニラ出力は非対応とし、検証層で明示エラーにします。
     */
    PARTICLE_GEOMETRY(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PARTICLE_GEOMETRY, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_PARTICLE_GEOMETRY_DESCRIPTION, Material.FILLED_MAP, mapOf(
        // 選択肢型に未選択と区別できない既定値は置きません（PARTICLEと同一方針）。
        "particle" to "",
        "particleData" to "",
        "particleShape" to ParticleShape.LINE.name,
        "particleDensity" to "0.5",
        "geometryRadius" to "3.0",
        "geometryHeight" to "3.0",
        "geometryTurns" to "2.0",
        "geometryStartAngle" to "0.0",
        "geometryEndAngle" to "360.0",
        "geometryRotation" to "0.0",
        "geometryAxisYaw" to "0.0",
        "geometryAxisPitch" to "-90.0",
        "geometryStep" to "0.5",
        "geometryWireframe" to "true",
    )),
    FIREWORK_LAUNCH(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FIREWORK_LAUNCH, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FIREWORK_LAUNCH_DESCRIPTION, Material.FIREWORK_ROCKET, mapOf(
        "fireworkItem" to "", "fireworkData" to "", "burstHeight" to "20.0"
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
        // issue #27: 条件の単一正本は判定対象欄です。旧 `kind` 欄は保存しません。
        "subject" to ConditionSubject.OTHER_ENTITY.name,
        "inverted" to "false",
        "sneaking" to "",
        "item" to "",
        "itemData" to "",
        "variable" to "",
        "operator" to ">=",
        "value" to "0.0",
        "block" to "minecraft:air",
        // issue #5: 型付き条件比較（VALUE）の左辺・右辺です。旧ノードにない場合は
        // WORLD scope・空欄として読み替え、検証層で未設定として拒否します。
        "leftScope" to VariableScope.WORLD.name,
        "leftName" to "",
        "right" to "",
    )),
    DISK_CALL(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISK_CALL, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_DISK_CALL_DESCRIPTION, Material.MUSIC_DISC_13, mapOf("diskId" to "")),
    /**
     * issue #8 の「値を返す」制御コマンドです。
     *
     * 実行時に残りのノードを打ち切って呼出元へ戻り、値を返します。source が
     * 空欄の場合は value 欄のリテラルを返し、指定がある場合は sourceScope の
     * 変数を返します。
     */
    RETURN_VALUE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_RETURN_VALUE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_RETURN_VALUE_DESCRIPTION, Material.TARGET, mapOf(
        "sourceScope" to VariableScope.EXECUTION.name,
        "source" to "",
        "value" to "",
    )),
    /**
     * #2 の共通変数操作コマンドです。
     *
     * scope（EXECUTION／WORLD）で対象storeを切り替え、operation で操作内容を
     * 切り替えます。旧ノードに scope がない場合は WORLD として読み替えます。
     * #3 の値生成方法は changeMode（ASSIGN／CALCULATE／PROPERTY／RANDOM）で
     * 切り替え、PROPERTY は propertySource＋property 欄、RANDOM は
     * randomMin／randomMax 欄を使います。source は COPY 時の複写元変数名です。
     */
    VARIABLE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_VARIABLE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_VARIABLE_DESCRIPTION, Material.REDSTONE, mapOf(
        "scope" to VariableScope.WORLD.name,
        "name" to "",
        "type" to ValueType.NUMBER.name,
        "operation" to VariableOperation.DEFINE.name,
        "changeMode" to VariableChangeMode.ASSIGN.name,
        "value" to "0.0",
        "source" to "",
        "propertySource" to "",
        "property" to "",
        "randomMin" to "1.0",
        "randomMax" to "6.0",
    )),
    /** 一時変数を設定します。再設定は上書きとして扱います。 */
    TEMP_SET(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_TEMPORARY_VARIABLE_SET, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_TEMPORARY_VARIABLE_SET_DESCRIPTION, Material.REPEATER, mapOf(
        "name" to "",
        "tempType" to ValueType.NUMBER.name,
        "value" to "0.0",
    )),
    MERGE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_MERGE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_MERGE_DESCRIPTION, Material.HOPPER, emptyMap()),
    FOR_START(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_START, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_START_DESCRIPTION, Material.REPEATER, mapOf(
        "count" to "1",
    )),
    FOR_END(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_END, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_END_DESCRIPTION, Material.COMPARATOR, emptyMap()),
    /**
     * 折り畳まれたFOR全体（FOR_START〜FOR_END）を表す通常形状ノードです。
     *
     * 中身はfoldedGraphへ退避し、外側は単一nextだけで通常ノードと同じく
     * 入れ替えできます。表示文言はFOR開始と共用し、新規言語キーを増やしません。
     * 新規追加はできず、FOR_STARTの折り畳み操作だけで生成します。
     */
    FOLDED_FOR(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_START, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_FOR_START_DESCRIPTION, Material.REPEATER, mapOf(
        "count" to "1",
    )),
    /**
     * 折り畳まれた閉じた分岐全体（CONDITION〜MERGE）を表す通常形状ノードです。
     *
     * 合流のある分岐だけが対象で、未合流は折り畳めません。中身はfoldedGraphへ
     * 退避し、外側は単一nextだけで通常ノードと同じく入れ替えできます。
     * 表示文言は条件分岐と共用し、新規言語キーを増やしません。
     * 新規追加はできず、CONDITIONの折り畳み操作だけで生成します。
     */
    FOLDED_IF(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONDITION, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONDITION_DESCRIPTION, Material.COMPARATOR, mapOf(
        // issue #27: 折り畳み外側は先頭 CONDITION の写しのため、判定対象欄も同じ既定値にします。
        // issue #5 の型付き条件比較（VALUE）の左辺・右辺も CONDITION と同じ既定値にします。
        "subject" to ConditionSubject.OTHER_ENTITY.name,
        "inverted" to "false",
        "sneaking" to "",
        "item" to "",
        "itemData" to "",
        "variable" to "",
        "operator" to ">=",
        "value" to "0.0",
        "block" to "minecraft:air",
        "leftScope" to VariableScope.WORLD.name,
        "leftName" to "",
        "right" to "",
    )),
    BREAK(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BREAK, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_BREAK_DESCRIPTION, Material.BARRIER, emptyMap()),
    CONTINUE(KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTINUE, KcKeys.KANTAN_COMMANDER_CLEAN_COMMAND_CONTINUE_DESCRIPTION, Material.ARROW, emptyMap());

    fun newNode() = CommandNode(type = this, params = defaults.toMutableMap())
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
