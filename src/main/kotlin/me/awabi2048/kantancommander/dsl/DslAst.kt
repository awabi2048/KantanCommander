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

/** `program "name" { ... }` の1ファイル分です。 */
data class DslProgram(
    val name: String,
    val body: List<DslStmt>,
    override val pos: DslPos,
) : DslNode

// ---- 文 ----

sealed interface DslStmt : DslNode

/** `var name: type = expr;` です。型注記は必須です。 */
data class DslVarDecl(
    val name: String,
    val type: ValueType,
    val value: DslExpr,
    override val pos: DslPos,
) : DslStmt

/** 代入先です。実行内変数とワールド変数を区別します。 */
sealed interface DslAssignTarget : DslNode {
    data class Variable(val name: String, override val pos: DslPos) : DslAssignTarget
    data class WorldVariable(val name: String, override val pos: DslPos) : DslAssignTarget
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

/** `call "disk_id";` です。 */
data class DslCallDisk(val diskId: DslStringLiteral, override val pos: DslPos) : DslStmt

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
