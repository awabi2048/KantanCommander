package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.ValueType

/**
 * DSLテキスト上の位置です。行・桁はともに1始まりで、パースエラーの報告に使います。
 */
data class DslPos(val line: Int, val column: Int) {
    override fun toString(): String = "$line:$column"
}

/**
 * DSLの構文木です。`.docs/specs/kantan-commander-dsl-draft.md` の表層文法に対応し、
 * 実行モデル（CommandNode 等）の構造は持ちません。文法上の表現だけを保持し、
 * モデルへの低級化は別層が行います。
 */
sealed interface DslNode {
    val pos: DslPos
}

/**
 * `.kcp` ファイル1つ分です。`program "name";` の宣言行・宣言部
 * （import／trigger／defarg）・トップレベルの本文から成ります。
 */
data class DslProgram(
    val name: String,
    val decls: List<DslDecl>,
    val body: List<DslStmt>,
    override val pos: DslPos,
    /**
     * 文へ所属する `//` コメントです。キーは所属文の pos、値はコメント本文
     * （`//` 記号を除く、出現順）です。宣言部・ファイル末尾のコメントは
     * 最寄りの文へ寄せて保持します。
     */
    val comments: Map<DslPos, List<String>> = emptyMap(),
) : DslNode

// ---- 宣言部 ----

sealed interface DslDecl : DslNode

/**
 * `import world;` です。このプログラムがワールドコンテキスト
 * （ワールド変数・ワールド内ストラクチャー）へアクセスすることを宣言します。
 */
data class DslImportWorld(override val pos: DslPos) : DslDecl

/**
 * `trigger timer(interval = 20);`・`trigger redstone;`・`trigger event(...);`
 * です。`redstone` の裸識別子は引数なし呼出しへ正規化します。
 */
data class DslTriggerDecl(val spec: DslCallExpr, override val pos: DslPos) : DslDecl

/**
 * `defarg name: type;` の呼出し引数宣言です。宣言順が引数順になり、
 * 呼出し側では実行開始時に定義済みの EXECUTION 変数として現れます。
 */
data class DslDefarg(val name: String, val type: ValueType, override val pos: DslPos) : DslDecl

// ---- 文 ----

sealed interface DslStmt : DslNode

/**
 * `var name: type = expr;` / `var world.name: type = expr;` です。型注記は必須です。
 * world=true はワールド変数の新規定義（VARIABLE・DEFINE）へ写像します。
 * name が空文字列の場合は GUI の未命名スロット（`var null`）を表します。
 */
data class DslVarDecl(
    val name: String,
    val type: ValueType,
    val value: DslExpr,
    val world: Boolean = false,
    override val pos: DslPos,
) : DslStmt

/** 代入先です。実行内変数・ワールド変数・エンティティ変数を区別します。 */
sealed interface DslAssignTarget : DslNode {
    /** name が空文字列の場合は未命名（`null` 指定）を表します。 */
    data class Variable(val name: String, override val pos: DslPos) : DslAssignTarget
    data class WorldVariable(val name: String, override val pos: DslPos) : DslAssignTarget
    /** `expr.vars.name` のエンティティ変数です。expr は entity 型の対象式です。 */
    data class EntityVariable(val entity: DslExpr, val name: String, override val pos: DslPos) : DslAssignTarget
}

enum class DslAssignOp { ASSIGN, ADD, SUB, MUL, DIV, MOD }

/** `x = expr;` / `x += expr;` / `world.x = expr;` 等の代入文です。 */
data class DslAssign(
    val target: DslAssignTarget,
    val op: DslAssignOp,
    val value: DslExpr,
    override val pos: DslPos,
) : DslStmt

/**
 * `if (cond) { } / else if (cond) { } / else { }` です。
 * else if は elseBody が DslIf を1件だけ持つ形へ正規化します。
 */
data class DslIf(
    val condition: DslExpr,
    val thenBody: List<DslStmt>,
    val elseBody: List<DslStmt>?,
    override val pos: DslPos,
) : DslStmt

/** `repeat(n) { ... }` です。 */
data class DslRepeat(
    val count: DslExpr,
    val body: List<DslStmt>,
    override val pos: DslPos,
) : DslStmt

data class DslBreak(override val pos: DslPos) : DslStmt
data class DslContinue(override val pos: DslPos) : DslStmt

/** `return;`（残り打ち切り）と `return expr;`（値返却）の両方を表します。 */
data class DslReturn(val value: DslExpr?, override val pos: DslPos) : DslStmt

/** 名前付き引数です。`name` が null の位置引数は単一必須引数の命令のみ許します。 */
data class DslArg(val name: String?, val value: DslExpr, val pos: DslPos)

/** `wait(1);` や `summon(entity = ..., at = ...);` のような命令呼出し文です。 */
data class DslCallStmt(
    val name: String,
    val args: List<DslArg>,
    override val pos: DslPos,
) : DslStmt

/**
 * `call "disk";` / `call "disk"(arg = 値, ...);` の呼出し文です。
 * 引数は呼出し先の `defarg` 宣言名との照合で検証します。
 */
data class DslCallDisk(
    val diskId: DslStringLiteral,
    val args: List<DslArg>,
    override val pos: DslPos,
) : DslStmt

/** `del x;` / `del world.x;` / `del e.vars.n;` の変数削除文です。 */
data class DslDelete(val target: DslAssignTarget, override val pos: DslPos) : DslStmt

// ---- 式 ----

sealed interface DslExpr : DslNode

data class DslNumberLiteral(val value: Double, override val pos: DslPos) : DslExpr

/** 文字列リテラルの部分です。テキストまたは `${expr}` 補間です。 */
sealed interface DslStringPart
data class DslTextPart(val text: String) : DslStringPart
data class DslInterpPart(val expr: DslExpr) : DslStringPart

/** `"..."` / `"…${expr}…"` の文字列リテラルです。parts の連結が文字列値です。 */
data class DslStringLiteral(val parts: List<DslStringPart>, override val pos: DslPos) : DslExpr {
    /** 補間を含まないときだけ文字列値を返します。 */
    fun plainText(): String? =
        if (parts.all { it is DslTextPart }) parts.joinToString("") { (it as DslTextPart).text } else null
}

data class DslBooleanLiteral(val value: Boolean, override val pos: DslPos) : DslExpr

/**
 * `null` です。spec 引数・if 条件・変数名の「未設定」を表す専用の式で、
 * 値としては型を持たず、上記の位置以外では使用できません。
 */
data class DslNull(override val pos: DslPos) : DslExpr

/** 裸の識別子による実行内変数参照です。 */
data class DslVarRef(val name: String, override val pos: DslPos) : DslExpr

/** `world.<name>` のワールド変数参照です。 */
data class DslWorldRef(val name: String, override val pos: DslPos) : DslExpr

/**
 * `gamemode.creative` のような名前空間付き定数です。
 * コンパイル時に定数値へ畳み込まれ、型はSTRINGです。
 */
data class DslConst(val group: String, val name: String, override val pos: DslPos) : DslExpr

enum class DslUnaryOp { NOT, NEG }
data class DslUnary(val op: DslUnaryOp, val operand: DslExpr, override val pos: DslPos) : DslExpr

enum class DslBinaryOp { ADD, SUB, MUL, DIV, MOD, EQ, NE, LT, LE, GT, GE, AND, OR }
data class DslBinary(val op: DslBinaryOp, val left: DslExpr, val right: DslExpr, override val pos: DslPos) : DslExpr

/** `expr.prop` / `expr.prop(arg)` の詳細値読出しです。 */
data class DslPropertyRead(
    val source: DslExpr,
    val name: String,
    val arg: DslExpr?,
    override val pos: DslPos,
) : DslExpr

/**
 * `vars.<name>` です。`e.vars.x = vars.src` の右辺で対象エンティティ自身の
 * 変数を参照します（VARIABLE scope=ENTITY の COPY／PROPERTY へ写像）。
 * エンティティ変数は同一対象の中でしか読み出せないため、代入の値と
 * `vars.src.prop` の参照元・引数としてのみ使用できます。
 */
data class DslEntityVarRef(val name: String, override val pos: DslPos) : DslExpr

/**
 * 実行コンテキストが供給する予約位置式です。`disk` は起動した制御ブロックの
 * 位置（PositionKind.DISK）、`captured` は捕捉済み位置（PositionKind.CAPTURED）
 * へ写像します。型はいずれも location です。
 */
data class DslSpecialPosition(val name: String, override val pos: DslPos) : DslExpr

/** `loop.count` です。内側ループの現在回数（CURRENT_LOOP_COUNT）を表します。 */
data class DslLoopCount(override val pos: DslPos) : DslExpr

/**
 * `x = call "disk"(...)` の式位置の呼出しです。戻り値を実行内・ワールド変数へ
 * 書き込む DISK_CALL へ写像するため、代入・変数宣言の値としてのみ使えます。
 * 式の型は呼出し先の戻り値型に従うため、型検査はコンパイル時へ委ねます。
 */
data class DslCallResult(
    val diskId: DslStringLiteral,
    val args: List<DslArg>,
    override val pos: DslPos,
) : DslExpr

/** `location(...)` `item(...)` `target(...)` 等の名前付き引数呼出し式です。 */
data class DslCallExpr(val name: String, val args: List<DslArg>, override val pos: DslPos) : DslExpr

/** 位置リテラルの1成分です。`~`/`^` 単独のとき value は 0 です。 */
data class DslPositionComponent(val semantics: CoordinateSemantics, val value: Double)

/**
 * `~0 ~1 ~0`（実行基点）・`base ~0 ~1 ~0`（変数基点）の位置リテラルです。
 * 成分は常に x/y/z の3個です。`base` が null のとき実行基点（COORDINATES）です。
 */
data class DslPositionLiteral(
    val base: DslExpr?,
    val components: List<DslPositionComponent>,
    override val pos: DslPos,
) : DslExpr

/**
 * `~12`・`^0` のような接頭辞付き数値の単一成分です。位置リテラルとは別物で、
 * `location(x = ~1, yaw = ~90)` のように引数値としてのみ使えます
 * （向きの相対角度など、成分ごとの座標セマンティクスを表します）。
 */
data class DslComponentExpr(
    val semantics: CoordinateSemantics,
    val value: Double,
    override val pos: DslPos,
) : DslExpr
