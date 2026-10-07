package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.ValueProperties
import me.awabi2048.kantancommander.model.ValueType

/** DSL の意味検証の失敗です。ユーザー向けの日本語メッセージを持ちます。 */
class DslCheckException(message: String, val pos: DslPos) : Exception("$pos: $message")

/**
 * DSL 構文木の意味検証です。
 *
 * 構文上成立する式・文について、型の整合・変数の定義順・詳細値の存在と引数型・
 * 組み込み呼出し（コンストラクタ・target・wait）の引数・break/continue の
 * ループ内配置を検証します。実行モデルへの写像は行わず、DSLテキストだけで
 * 判定できる規則を担います。
 *
 * @param worldTypes ワールド変数の型表です。実行コンテキスト（ディスク編集対象）に
 *   応じて呼出し側が供給します。表にないワールド変数は未定義としてエラーにします。
 */
class DslChecker(
    private val worldTypes: Map<String, ValueType> = emptyMap(),
) {
    /** 実行内変数の型表です。ブロックはスコープを分けず、実行内変数は平坦です。 */
    private val vars = LinkedHashMap<String, ValueType>()
    private var loopDepth = 0

    private fun error(pos: DslPos, message: String): Nothing = throw DslCheckException(message, pos)

    /** 検証を実行し、最終的な実行内変数の型表を返します。 */
    fun check(program: DslProgram): Map<String, ValueType> {
        program.body.forEach(::checkStmt)
        return vars.toMap()
    }

    private fun checkStmt(stmt: DslStmt) {
        when (stmt) {
            is DslVarDecl -> {
                val actual = checkExpr(stmt.value)
                if (actual != stmt.type) {
                    error(stmt.value.pos, "変数 '${stmt.name}' は ${stmt.type.dslName()} 型ですが、${actual.dslName()} 型の値は代入できません")
                }
                if (stmt.name in vars) error(stmt.pos, "変数 '${stmt.name}' は既に定義されています")
                vars[stmt.name] = stmt.type
            }
            is DslAssign -> {
                val targetType = when (val t = stmt.target) {
                    is DslAssignTarget.Variable -> vars[t.name]
                        ?: error(t.pos, "未定義の変数 '${t.name}' です")
                    is DslAssignTarget.WorldVariable -> worldTypes[t.name]
                        ?: error(t.pos, "未定義のワールド変数 '${t.name}' です")
                }
                val valueType = checkExpr(stmt.value)
                if (stmt.op == DslAssignOp.ASSIGN) {
                    if (valueType != targetType) {
                        error(stmt.value.pos, "${targetType.dslName()} 型の変数へ ${valueType.dslName()} 型の値は代入できません")
                    }
                } else {
                    if (targetType != ValueType.NUMBER || valueType != ValueType.NUMBER) {
                        error(stmt.pos, "複合代入は number 型同士でのみ使えます")
                    }
                }
            }
            is DslIf -> {
                val cond = checkExpr(stmt.condition)
                if (cond != ValueType.BOOLEAN) error(stmt.condition.pos, "if の条件は boolean 型が必要です（${cond.dslName()} 型）")
                stmt.thenBody.forEach(::checkStmt)
                stmt.elseBody?.forEach(::checkStmt)
            }
            is DslRepeat -> {
                val count = checkExpr(stmt.count)
                if (count != ValueType.NUMBER) error(stmt.count.pos, "repeat の回数は number 型が必要です")
                if (stmt.count is DslNumberLiteral) {
                    val n = stmt.count.value
                    if (n < 1 || n % 1.0 != 0.0) error(stmt.count.pos, "repeat の回数は 1 以上の整数が必要です")
                }
                loopDepth++
                try { stmt.body.forEach(::checkStmt) } finally { loopDepth-- }
            }
            is DslBreak -> if (loopDepth == 0) error(stmt.pos, "break は repeat の内側でのみ使えます")
            is DslContinue -> if (loopDepth == 0) error(stmt.pos, "continue は repeat の内側でのみ使えます")
            is DslReturn -> stmt.value?.let { checkExpr(it) }
            is DslCallStmt -> checkCallStmt(stmt)
            is DslCallDisk -> checkStringLiteral(stmt.diskId)
        }
    }

    private fun checkCallStmt(stmt: DslCallStmt) {
        stmt.args.forEach { checkExpr(it.value) }
        when (stmt.name) {
            "wait" -> {
                val arg = stmt.args.singleOrNull()
                    ?: error(stmt.pos, "wait は引数を1個だけ取ります")
                if (arg.name != null && arg.name != "seconds") {
                    error(arg.pos, "wait の引数名は seconds です")
                }
                val type = checkExpr(arg.value)
                if (type != ValueType.NUMBER) error(arg.pos, "wait の秒数は number 型が必要です")
                if (arg.value is DslNumberLiteral && !CommandValueRules.isWaitSeconds(arg.value.value)) {
                    error(arg.pos, "wait の秒数は 0 より大きい 0.05 刻み（tick 境界）の値が必要です")
                }
            }
            // その他の命令は引数スキーマを持たないため、ここでは式の検証のみ行います。
            // 命令名・引数名の妥当性はモデルへの写像層で検証します。
            else -> Unit
        }
    }

    private fun checkStringLiteral(lit: DslStringLiteral) {
        lit.parts.forEach { part ->
            if (part is DslInterpPart) checkInterp(part)
        }
    }

    /** 補間内の式は表示可能な型（number/string/boolean）のみ許します。 */
    private fun checkInterp(part: DslInterpPart) {
        val type = checkExpr(part.expr)
        if (type !in INTERP_TYPES) {
            error(part.expr.pos, "文字列補間には number/string/boolean 型のみ使えます（${type.dslName()} 型）")
        }
    }

    private fun checkExpr(expr: DslExpr): ValueType = when (expr) {
        is DslNumberLiteral -> ValueType.NUMBER
        is DslStringLiteral -> { checkStringLiteral(expr); ValueType.STRING }
        is DslBooleanLiteral -> ValueType.BOOLEAN
        is DslVarRef -> vars[expr.name] ?: error(expr.pos, "未定義の変数 '${expr.name}' です")
        is DslWorldRef -> worldTypes[expr.name] ?: error(expr.pos, "未定義のワールド変数 '${expr.name}' です")
        is DslConst -> when (expr.group) {
            "gamemode" -> ValueType.STRING
            else -> error(expr.pos, "不明な定数 '${expr.group}.${expr.name}' です")
        }
        is DslUnary -> {
            val operand = checkExpr(expr.operand)
            when (expr.op) {
                DslUnaryOp.NOT -> if (operand != ValueType.BOOLEAN) error(expr.operand.pos, "'!' は boolean 型へのみ使えます") else ValueType.BOOLEAN
                DslUnaryOp.NEG -> if (operand != ValueType.NUMBER) error(expr.operand.pos, "単項 '-' は number 型へのみ使えます") else ValueType.NUMBER
            }
        }
        is DslBinary -> checkBinary(expr)
        is DslPropertyRead -> checkPropertyRead(expr)
        is DslCallExpr -> checkCallExpr(expr)
        is DslPositionLiteral -> checkPositionLiteral(expr)
    }

    private fun checkBinary(expr: DslBinary): ValueType {
        val left = checkExpr(expr.left)
        val right = checkExpr(expr.right)
        fun fail(): Nothing = error(
            expr.pos,
            "'${expr.op.symbol()}' は ${left.dslName()} 型と ${right.dslName()} 型には使えません",
        )
        return when (expr.op) {
            DslBinaryOp.ADD, DslBinaryOp.SUB, DslBinaryOp.MUL, DslBinaryOp.DIV, DslBinaryOp.MOD ->
                if (left == ValueType.NUMBER && right == ValueType.NUMBER) ValueType.NUMBER else fail()
            DslBinaryOp.EQ, DslBinaryOp.NE ->
                if (left == right) ValueType.BOOLEAN else fail()
            DslBinaryOp.LT, DslBinaryOp.LE, DslBinaryOp.GT, DslBinaryOp.GE ->
                if (left == ValueType.NUMBER && right == ValueType.NUMBER) ValueType.BOOLEAN else fail()
            DslBinaryOp.AND, DslBinaryOp.OR ->
                if (left == ValueType.BOOLEAN && right == ValueType.BOOLEAN) ValueType.BOOLEAN else fail()
        }
    }

    private fun checkPropertyRead(expr: DslPropertyRead): ValueType {
        val sourceType = checkExpr(expr.source)
        val def = ValueProperties.definition(expr.name)
            ?: error(expr.pos, "不明な詳細値 '${expr.name}' です")
        if (sourceType !in def.sources) {
            error(expr.pos, "${sourceType.dslName()} 型からは詳細値 '${expr.name}' を取得できません")
        }
        when {
            def.argType == null && expr.arg != null ->
                error(expr.arg.pos, "詳細値 '${expr.name}' は引数を取りません")
            def.argType != null && expr.arg == null ->
                error(expr.pos, "詳細値 '${expr.name}' には ${def.argType.dslName()} 型の引数が必要です")
            def.argType != null -> {
                val argType = checkExpr(expr.arg!!)
                if (argType != def.argType) {
                    error(expr.arg.pos, "詳細値 '${expr.name}' の引数は ${def.argType.dslName()} 型が必要です")
                }
            }
        }
        return def.result
    }

    private fun checkCallExpr(expr: DslCallExpr): ValueType = when (expr.name) {
        "location" -> { checkConstructor(expr, LOCATION_ARGS); ValueType.LOCATION }
        "item" -> { checkConstructor(expr, ITEM_ARGS); ValueType.ITEM }
        "block" -> { checkConstructor(expr, BLOCK_ARGS); ValueType.BLOCK }
        "target" -> checkTarget(expr)
        else -> error(expr.pos, "不明な呼出し '${expr.name}' です")
    }

    /**
     * コンストラクタの引数を検証します。コンストラクタは全引数が名前付きで、
     * 未知の引数名・必須引数の欠落・重複・型不一致をエラーにします。
     */
    private fun checkConstructor(expr: DslCallExpr, spec: Map<String, ArgSpec>) {
        val seen = mutableSetOf<String>()
        expr.args.forEach { arg ->
            if (arg.name == null) error(arg.pos, "コンストラクタの引数は名前付きが必須です")
            val argSpec = spec[arg.name] ?: error(arg.pos, "不明な引数名 '${arg.name}' です（${spec.keys.joinToString("/")}）")
            if (!seen.add(arg.name)) error(arg.pos, "引数 '${arg.name}' が重複しています")
            val type = checkExpr(arg.value)
            if (type != argSpec.type) {
                error(arg.pos, "引数 '${arg.name}' は ${argSpec.type.dslName()} 型が必要です")
            }
        }
        spec.filter { it.value.required && it.key !in seen }.keys.firstOrNull()?.let {
            error(expr.pos, "必須の引数 '$it' がありません")
        }
    }

    private fun checkTarget(expr: DslCallExpr): ValueType {
        val kindArg = expr.args.firstOrNull { it.name == "kind" }
            ?: error(expr.pos, "target には引数 kind が必要です")
        checkConstructor(expr, TARGET_ARGS)
        val kind = (kindArg.value as? DslStringLiteral)?.plainText()
            ?: error(kindArg.pos, "target の kind は文字列リテラルで指定してください")
        if (kind !in TARGET_KINDS) {
            error(kindArg.pos, "target の kind が不明です（${TARGET_KINDS.joinToString("/")}）")
        }
        return ValueType.ENTITY
    }

    private fun checkPositionLiteral(expr: DslPositionLiteral): ValueType {
        expr.base?.let {
            val baseType = checkExpr(it)
            if (baseType != ValueType.LOCATION) {
                error(it.pos, "位置リテラルの基準は location 型が必要です（${baseType.dslName()} 型）")
            }
        }
        return ValueType.LOCATION
    }

    private data class ArgSpec(val type: ValueType, val required: Boolean)

    private fun ValueType.dslName(): String = name.lowercase()
    private fun DslBinaryOp.symbol(): String = when (this) {
        DslBinaryOp.ADD -> "+"; DslBinaryOp.SUB -> "-"; DslBinaryOp.MUL -> "*"
        DslBinaryOp.DIV -> "/"; DslBinaryOp.MOD -> "%"; DslBinaryOp.EQ -> "=="
        DslBinaryOp.NE -> "!="; DslBinaryOp.LT -> "<"; DslBinaryOp.LE -> "<="
        DslBinaryOp.GT -> ">"; DslBinaryOp.GE -> ">="; DslBinaryOp.AND -> "&&"
        DslBinaryOp.OR -> "||"
    }

    companion object {
        private val INTERP_TYPES = setOf(ValueType.NUMBER, ValueType.STRING, ValueType.BOOLEAN)

        private val LOCATION_ARGS = mapOf(
            "x" to ArgSpec(ValueType.NUMBER, true),
            "y" to ArgSpec(ValueType.NUMBER, true),
            "z" to ArgSpec(ValueType.NUMBER, true),
            "yaw" to ArgSpec(ValueType.NUMBER, false),
            "pitch" to ArgSpec(ValueType.NUMBER, false),
        )
        private val ITEM_ARGS = mapOf(
            "id" to ArgSpec(ValueType.STRING, true),
            "amount" to ArgSpec(ValueType.NUMBER, false),
            "data" to ArgSpec(ValueType.STRING, false),
        )
        private val BLOCK_ARGS = mapOf(
            "id" to ArgSpec(ValueType.STRING, true),
        )
        private val TARGET_ARGS = mapOf(
            "kind" to ArgSpec(ValueType.STRING, true),
            "type" to ArgSpec(ValueType.STRING, false),
            "within" to ArgSpec(ValueType.NUMBER, false),
            "farther" to ArgSpec(ValueType.NUMBER, false),
            "limit" to ArgSpec(ValueType.NUMBER, false),
            "sort" to ArgSpec(ValueType.STRING, false),
            "gamemode" to ArgSpec(ValueType.STRING, false),
            "tag" to ArgSpec(ValueType.STRING, false),
            "name" to ArgSpec(ValueType.STRING, false),
            "dx" to ArgSpec(ValueType.NUMBER, false),
            "dy" to ArgSpec(ValueType.NUMBER, false),
            "dz" to ArgSpec(ValueType.NUMBER, false),
        )
        private val TARGET_KINDS = setOf(
            "nearest_player", "nearby_players", "all_players",
            "random_player", "nearest_entity", "nearby_entities",
        )
    }
}
