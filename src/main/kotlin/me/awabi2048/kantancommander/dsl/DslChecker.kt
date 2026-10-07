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
 * @param exprTypes 指定時は検証済みの各式の型を記録します。モデルへの低級化で
 *   一時変数の型を決めるためにコンパイラが利用します。
 */
class DslChecker(
    private val worldTypes: Map<String, ValueType> = emptyMap(),
    private val exprTypes: MutableMap<DslExpr, ValueType>? = null,
) {
    /** 実行内変数の型表です。ブロックはスコープを分けず、実行内変数は平坦です。 */
    private val vars = LinkedHashMap<String, ValueType>()
    private var loopDepth = 0

    private fun error(pos: DslPos, message: String): Nothing = throw DslCheckException(message, pos)

    /** 検証を実行し、最終的な実行内変数の型表を返します。 */
    fun check(program: DslProgram): Map<String, ValueType> {
        checkBlock(program.body)
        return vars.toMap()
    }

    /**
     * 1ブロック分の文を検証します。
     *
     * 終端文（return／break／continue）の後に続く文は実行経路上到達できず、
     * グラフでも「到達不能ノード」として保存不可になるため、ここで拒否します。
     */
    private fun checkBlock(stmts: List<DslStmt>) {
        var terminated = false
        stmts.forEach { stmt ->
            if (terminated) error(stmt.pos, "終端文の後ろの文は到達できません")
            checkStmt(stmt)
            if (stmt is DslReturn || stmt is DslBreak || stmt is DslContinue) terminated = true
        }
    }

    private fun checkStmt(stmt: DslStmt) {
        when (stmt) {
            is DslVarDecl -> {
                checkVarName(stmt.name, stmt.pos)
                val actual = checkExpr(stmt.value)
                if (actual != stmt.type) {
                    error(stmt.value.pos, "変数 '${stmt.name}' は ${stmt.type.dslName()} 型ですが、${actual.dslName()} 型の値は代入できません")
                }
                if (stmt.name in vars) error(stmt.pos, "変数 '${stmt.name}' は既に定義されています")
                vars[stmt.name] = stmt.type
            }
            is DslAssign -> {
                val targetType = when (val t = stmt.target) {
                    is DslAssignTarget.Variable -> {
                        checkVarName(t.name, t.pos)
                        vars[t.name]
                            ?: error(t.pos, "未定義の変数 '${t.name}' です")
                    }
                    is DslAssignTarget.WorldVariable -> {
                        checkVarName(t.name, t.pos)
                        worldTypes[modelName(t.name)]
                            ?: error(t.pos, "未定義のワールド変数 '${t.name}' です")
                    }
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
                    // モデルの数値式言語は剰余を持たないため、%= は写像不能です。
                    if (stmt.op == DslAssignOp.MOD) {
                        error(stmt.pos, "剰余代入（%=）はモデルが未対応のため使えません")
                    }
                }
            }
            is DslIf -> {
                val cond = checkExpr(stmt.condition)
                if (cond != ValueType.BOOLEAN) error(stmt.condition.pos, "if の条件は boolean 型が必要です（${cond.dslName()} 型）")
                checkBlock(stmt.thenBody)
                stmt.elseBody?.let(::checkBlock)
            }
            is DslRepeat -> {
                val count = checkExpr(stmt.count)
                if (count != ValueType.NUMBER) error(stmt.count.pos, "repeat の回数は number 型が必要です")
                if (stmt.count is DslNumberLiteral) {
                    val n = stmt.count.value
                    if (n < 1 || n % 1.0 != 0.0) error(stmt.count.pos, "repeat の回数は 1 以上の整数が必要です")
                }
                loopDepth++
                try { checkBlock(stmt.body) } finally { loopDepth-- }
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

    private fun checkExpr(expr: DslExpr): ValueType {
        val type = inferExpr(expr)
        // コンパイラが一時変数の型・返り値の型を決めるため、検証済みの式型を残します。
        exprTypes?.put(expr, type)
        return type
    }

    private fun inferExpr(expr: DslExpr): ValueType = when (expr) {
        is DslNumberLiteral -> ValueType.NUMBER
        is DslStringLiteral -> { checkStringLiteral(expr); ValueType.STRING }
        is DslBooleanLiteral -> ValueType.BOOLEAN
        is DslVarRef -> vars[expr.name] ?: error(expr.pos, "未定義の変数 '${expr.name}' です")
        is DslWorldRef -> {
            checkVarName(expr.name, expr.pos)
            worldTypes[modelName(expr.name)]
                ?: error(expr.pos, "未定義のワールド変数 '${expr.name}' です")
        }
        is DslConst -> when (expr.group) {
            "gamemode" -> {
                if (expr.name !in GAMEMODE_NAMES) {
                    error(expr.pos, "不明なゲームモード定数 '${expr.name}' です（${GAMEMODE_NAMES.joinToString("/")}）")
                }
                ValueType.STRING
            }
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
            DslBinaryOp.ADD, DslBinaryOp.SUB, DslBinaryOp.MUL, DslBinaryOp.DIV ->
                if (left == ValueType.NUMBER && right == ValueType.NUMBER) ValueType.NUMBER else fail()
            // モデルの数値式言語は剰余を持たないため、% は写像不能です。
            DslBinaryOp.MOD -> error(expr.pos, "剰余演算（%）はモデルが未対応のため使えません")
            DslBinaryOp.EQ, DslBinaryOp.NE -> {
                // モデルの条件が比較できるのは NUMBER／BOOLEAN／STRING の等値と、
                // `pos.block == block(...)` 形式のブロック照合だけです。
                val allowed = when {
                    left == right && left in COMPARABLE_TYPES -> true
                    left == ValueType.BLOCK && right == ValueType.BLOCK &&
                        expr.left is DslPropertyRead && expr.left.name == "block" &&
                        expr.right is DslCallExpr && expr.right.name == "block" -> true
                    else -> false
                }
                if (allowed) ValueType.BOOLEAN else fail()
            }
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
     *
     * location/item/block/target の引数はモデルの構造指定（PositionSpec/TargetSpec/
     * TypedValue）へ直接写像するため、実行時式は展開できずリテラルだけを受理します。
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
            if (!isCompileTimeLiteral(arg.value)) {
                error(arg.value.pos, "'${expr.name}' の引数はリテラルのみ使用できます")
            }
        }
        spec.filter { it.value.required && it.key !in seen }.keys.firstOrNull()?.let {
            error(expr.pos, "必須の引数 '$it' がありません")
        }
        // item の個数はモデルの TypedValue 組立が読まないため、表層でも拒否します。
        if (expr.name == "item" && "amount" in seen) {
            error(expr.pos, "item の 'amount' はモデルが未対応のため使用できません")
        }
    }

    /** コンパイル時定数となる式（数値・非補間文字列・真偽値・名前空間定数）です。 */
    private fun isCompileTimeLiteral(expr: DslExpr): Boolean = when (expr) {
        is DslNumberLiteral, is DslBooleanLiteral, is DslConst -> true
        is DslStringLiteral -> expr.plainText() != null
        is DslUnary -> expr.op == DslUnaryOp.NEG && expr.operand is DslNumberLiteral
        else -> false
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

    /**
     * DSL の変数名をモデルの変数名へ写像します。
     * モデル命名規則 `[a-z][a-z0-9_.-]*` は `_` を含まないため、
     * DSL の snake_case は `-` へ置き換えます。`worldTypes` のキーは
     * モデル側の変数名であるため、参照時にもこの写像を適用します。
     */
    private fun modelName(name: String): String = name.replace('_', '-')

    /**
     * DSL で宣言・参照できる変数名を検証します。
     * 小文字 snake_case（`[a-z][a-z0-9_]*`）に限定することで、
     * `_`→`-` の写像が一意に定まります。
     */
    private fun checkVarName(name: String, pos: DslPos) {
        if (!namePattern.matches(name)) {
            error(pos, "変数名 '${name}' は小文字の snake_case（[a-z][a-z0-9_]*）で指定してください")
        }
    }

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

        // モデルの条件評価が等値比較できる型（VARIABLE 判定の対応型と一致）です。
        private val COMPARABLE_TYPES = setOf(ValueType.NUMBER, ValueType.BOOLEAN, ValueType.STRING)

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

        private val GAMEMODE_NAMES = setOf("survival", "creative", "adventure", "spectator")

        private val namePattern = Regex("[a-z][a-z0-9_]*")
    }
}
