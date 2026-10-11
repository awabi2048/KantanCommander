package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.CommandValueRules
import me.awabi2048.kantancommander.model.EventTriggerArguments
import me.awabi2048.kantancommander.model.EventTriggerKind
import me.awabi2048.kantancommander.model.NumericExpression
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
    /** スクリプト内で `var world.x` として新規定義したワールド変数の型表です。 */
    private val worldVars = LinkedHashMap<String, ValueType>()
    /** `del world.x` で削除済みとなったワールド変数名（モデル名）です。 */
    private val worldDeleted = mutableSetOf<String>()
    private var loopDepth = 0
    /** `import world;` 宣言の有無です。world 参照・place_structure の前提です。 */
    private var importWorld = false
    private var triggerSeen = false

    /**
     * ワールド変数名（DSL名）の現在の型です。スクリプト内定義・外部定義の順に
     * 解決し、`del world.x` で削除済みの名前は未定義として扱います。
     */
    private fun worldType(dslName: String): ValueType? {
        val key = modelName(dslName)
        if (key in worldDeleted) return null
        return worldVars[key] ?: worldTypes[key]
    }

    private fun error(pos: DslPos, message: String): Nothing = throw DslCheckException(message, pos)

    /** 検証を実行し、最終的な実行内変数の型表を返します。 */
    fun check(program: DslProgram): Map<String, ValueType> {
        checkDecls(program.decls)
        checkBlock(program.body)
        return vars.toMap()
    }

    /**
     * 宣言部（import／trigger／defarg）を検証します。
     * 宣言順は import → trigger → defarg 固定です。
     * defarg は呼出し引数として本文開始時点で定義済みの実行内変数になり、
     * `trigger event` はイベント種別に応じたイベント引数を同様に定義します。
     * イベント引数のシードは defarg より先に行い、同名 defarg は重複として
     * 拒否します。
     */
    private fun checkDecls(decls: List<DslDecl>) {
        var stage = 0
        decls.forEach { decl ->
            val next = when (decl) {
                is DslImportWorld -> 1
                is DslTriggerDecl -> 2
                is DslDefarg -> 3
            }
            if (next < stage) {
                error(decl.pos, "宣言の順序は import → trigger → defarg です")
            }
            stage = next
        }
        decls.forEach { decl ->
            when (decl) {
                is DslImportWorld -> importWorld = true
                is DslTriggerDecl -> checkTrigger(decl)
                is DslDefarg -> {
                    if (decl.name.isEmpty()) {
                        error(decl.pos, "defarg には引数名が必要です")
                    }
                    checkVarName(decl.name, decl.pos)
                    if (decl.name in vars) {
                        error(decl.pos, "引数 '${decl.name}' が重複しています")
                    }
                    vars[decl.name] = decl.type
                }
            }
        }
    }

    /** `trigger` 宣言を検証します。種別ごとの引数構成を確認します。 */
    private fun checkTrigger(decl: DslTriggerDecl) {
        if (triggerSeen) error(decl.pos, "trigger は1つだけ宣言できます")
        triggerSeen = true
        val spec = decl.spec
        when (spec.name) {
            "redstone" -> if (spec.args.isNotEmpty()) {
                error(spec.pos, "trigger redstone は引数を取りません")
            }
            "timer" -> {
                val arg = spec.args.singleOrNull()
                    ?: error(spec.pos, "trigger timer には interval 引数が必要です")
                if (arg.name != null && arg.name != "interval") {
                    error(arg.pos, "trigger timer の引数名は interval です")
                }
                val v = (arg.value as? DslNumberLiteral)?.value
                    ?: error(arg.value.pos, "trigger timer の間隔は数値リテラルで指定してください")
                if (!CommandValueRules.isTimerSeconds(v)) {
                    error(arg.value.pos, "trigger timer の間隔が範囲外です")
                }
            }
            "event" -> checkEventTrigger(spec)
            else -> error(spec.pos, "trigger の種別は timer/redstone/event のいずれかです")
        }
    }

    /** `trigger event(...)` の引数を検証し、イベント引数を変数表へ導入します。 */
    private fun checkEventTrigger(spec: DslCallExpr) {
        val seen = mutableSetOf<String>()
        var kind: EventTriggerKind? = null
        spec.args.forEach { arg ->
            val name = arg.name ?: error(arg.pos, "trigger event の引数は名前付きが必須です")
            if (!seen.add(name)) error(arg.pos, "引数 '${name}' が重複しています")
            when (name) {
                "kind" -> {
                    val raw = (arg.value as? DslStringLiteral)?.plainText()
                        ?: error(arg.pos, "kind は文字列リテラルで指定してください")
                    kind = EventTriggerKind.parse(raw)
                        ?: error(arg.pos, "kind が不明です（${EVENT_KINDS.joinToString("/")}）")
                }
                "actor", "entity" -> {
                    if (checkExpr(arg.value) != ValueType.ENTITY) {
                        error(arg.pos, "引数 '${name}' は entity 型が必要です")
                    }
                }
                "side" -> literalEnum(arg, TRIGGER_SIDES)
                "state" -> literalEnum(arg, TRIGGER_STATES)
                "block", "block_world", "actor_id", "entity_id" -> {
                    if ((arg.value as? DslStringLiteral)?.plainText() == null) {
                        error(arg.pos, "引数 '${name}' は文字列リテラルで指定してください")
                    }
                }
                "block_x", "block_y", "block_z" -> {
                    val v = (arg.value as? DslNumberLiteral)?.value
                    if (v == null || v % 1.0 != 0.0) {
                        error(arg.pos, "引数 '${name}' は整数リテラルで指定してください")
                    }
                }
                else -> error(arg.pos, "trigger event の引数名 '${name}' が不明です")
            }
        }
        if (kind == null) error(spec.pos, "trigger event には kind 引数が必要です")
        // イベント引数（event_actor 等）は実行開始時に定義済みの変数として導入します。
        EventTriggerArguments.seedTypesFor(kind).forEach { (name, type) ->
            vars.getOrPut(name) { type }
        }
    }

    private fun literalEnum(arg: DslArg, allowed: Set<String>) {
        val raw = (arg.value as? DslStringLiteral)?.plainText()
            ?: error(arg.pos, "引数 '${arg.name}' は文字列リテラルで指定してください")
        if (raw !in allowed) error(arg.pos, "'${raw}' は使えません（${allowed.joinToString("/")}）")
    }

    /** `import world;` が無い状態でのワールドコンテキスト使用を拒否します。 */
    private fun requireWorld(pos: DslPos, what: String) {
        if (!importWorld) error(pos, "${what}には冒頭の 'import world;' 宣言が必要です")
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
                if (stmt.world) requireWorld(stmt.pos, "ワールド変数の定義")
                // `= null` は値が未設定の宣言です（型は宣言に従い、値は空で保持します）。
                // `= call "x"(..)` は戻り値を受け取る宣言です。呼出し側の引数を検査し、
                // 宣言型との整合は DISK_CALL の検証規則（受取変数の存在確認）に委ねます。
                if (stmt.value is DslCallResult) {
                    checkCallArgs(stmt.value.diskId, stmt.value.args)
                    exprTypes?.put(stmt.value, stmt.type)
                } else if (stmt.value !is DslNull) {
                    val actual = checkExpr(stmt.value)
                    if (actual != stmt.type) {
                        error(stmt.value.pos, "変数 '${stmt.name}' は ${stmt.type.dslName()} 型ですが、${actual.dslName()} 型の値は代入できません")
                    }
                }
                if (stmt.name.isEmpty()) return
                if (stmt.world) {
                    // `var world.x` は新規定義（DEFINE）です。同一プログラム内の
                    // 重複宣言は拒否しますが、外部定義表（ワールドの変数定義）に
                    // 既にある名前は許容します。実行時の DEFINE は定義済みの変数を
                    // 上書きしない no-op であるため、宣言は「その変数が存在する」
                    // 表明として機能します。
                    val key = modelName(stmt.name)
                    if (key in worldVars) {
                        error(stmt.pos, "ワールド変数 '${stmt.name}' は既に定義されています")
                    }
                    worldVars[key] = stmt.type
                    worldDeleted.remove(key)
                } else {
                    if (stmt.name in vars) error(stmt.pos, "変数 '${stmt.name}' は既に定義されています")
                    vars[stmt.name] = stmt.type
                }
            }
            is DslAssign -> {
                val targetType = when (val t = stmt.target) {
                    is DslAssignTarget.Variable -> {
                        checkVarName(t.name, t.pos)
                        // 未命名（空名）スロットへの書き込みは型表を持たないため
                        // 定義確認を行いません（名なし変数は参照できません）。
                        if (t.name.isEmpty()) null
                        else vars[t.name]
                            ?: error(t.pos, "未定義の変数 '${t.name}' です")
                    }
                    is DslAssignTarget.WorldVariable -> {
                        requireWorld(t.pos, "ワールド変数の使用")
                        checkVarName(t.name, t.pos)
                        if (t.name.isEmpty()) null
                        else worldType(t.name)
                            ?: error(t.pos, "未定義のワールド変数 '${t.name}' です")
                    }
                    is DslAssignTarget.EntityVariable -> {
                        checkVarName(t.name, t.pos)
                        val entityType = checkExpr(t.entity)
                        if (entityType != ValueType.ENTITY) {
                            error(t.entity.pos, "エンティティ変数の対象は entity 型が必要です（${entityType.dslName()} 型）")
                        }
                        // エンティティ変数は定義表を持たず、書き込み型は値側の型です。
                        null
                    }
                }
                // `= null` は値を未設定へ戻す代入です（複合代入では使えません）。
                // 式型は代入先の型として記録し、低級化側は空値を書きます。
                // `= call "x"(..)` は DISK_CALL の戻り値受取へ写像します。戻り値の
                // 型は呼出し先解析に依存するため、この層では型一致を判定しません。
                val valueType = when {
                    stmt.value is DslCallResult -> {
                        if (stmt.op != DslAssignOp.ASSIGN) {
                            error(stmt.value.pos, "call の戻り値は = 代入でのみ受け取れます")
                        }
                        if (stmt.target is DslAssignTarget.EntityVariable) {
                            error(stmt.value.pos, "call の戻り値はエンティティ変数へ書き込めません")
                        }
                        checkCallArgs(stmt.value.diskId, stmt.value.args)
                        (targetType ?: ValueType.STRING).also { exprTypes?.put(stmt.value, it) }
                    }
                    stmt.value is DslNull && stmt.op == DslAssignOp.ASSIGN ->
                        (targetType ?: ValueType.STRING).also { exprTypes?.put(stmt.value, it) }
                    // `vars.x`・`vars.x.prop` は対象エンティティ自身の変数読み取りです。
                    // エンティティ変数への代入の値でのみ有効で、COPY／PROPERTY へ写像します。
                    stmt.value is DslEntityVarRef -> {
                        if (stmt.target !is DslAssignTarget.EntityVariable) {
                            error(stmt.value.pos, "vars.<name> はエンティティ変数の代入値としてのみ使えます")
                        }
                        checkVarName(stmt.value.name, stmt.value.pos)
                        // COPY は複写元の型を引き継ぐため、この層では型を確定しません。
                        targetType
                    }
                    stmt.value is DslPropertyRead && stmt.value.source is DslEntityVarRef -> {
                        if (stmt.target !is DslAssignTarget.EntityVariable) {
                            error(stmt.value.pos, "vars.<name> はエンティティ変数の代入値としてのみ使えます")
                        }
                        checkEntityVarPropertyRead(stmt.value)
                    }
                    // PROPERTY の参照元は対象Entity自身の変数に限定されます。
                    // 実行内・ワールド変数の詳細値は値欄を通らないため写像できません。
                    stmt.value is DslPropertyRead && stmt.target is DslAssignTarget.EntityVariable ->
                        error(stmt.value.pos, "エンティティ変数への書き込みでは、詳細値の参照元は vars.<name> で指定してください")
                    else -> checkExpr(stmt.value)
                }
                if (stmt.op == DslAssignOp.ASSIGN) {
                    // `vars.x`（COPY）は複写元の型を引き継ぐため、ここでは比較しません。
                    if (valueType != null && stmt.value !is DslCallResult && targetType != null && valueType != targetType) {
                        error(stmt.value.pos, "${targetType.dslName()} 型の変数へ ${valueType.dslName()} 型の値は代入できません")
                    }
                } else {
                    if (stmt.target is DslAssignTarget.EntityVariable) {
                        // エンティティ変数は読み出し経路を持たず、自己参照の複合代入は表現不能です。
                        error(stmt.pos, "エンティティ変数への複合代入はモデルが未対応のため使えません")
                    }
                    if (targetType != ValueType.NUMBER || valueType != ValueType.NUMBER) {
                        error(stmt.pos, "複合代入は number 型同士でのみ使えます")
                    }
                    // モデルの数値式言語は剰余を持たないため、%= は写像不能です。
                    if (stmt.op == DslAssignOp.MOD) {
                        error(stmt.pos, "剰余代入（%=）はモデルが未対応のため使えません")
                    }
                }
            }
            is DslDelete -> {
                when (val t = stmt.target) {
                    is DslAssignTarget.Variable -> {
                        checkVarName(t.name, t.pos)
                        if (t.name.isNotEmpty() && vars.remove(t.name) == null) {
                            error(t.pos, "未定義の変数 '${t.name}' は削除できません")
                        }
                    }
                    is DslAssignTarget.WorldVariable -> {
                        requireWorld(t.pos, "ワールド変数の削除")
                        checkVarName(t.name, t.pos)
                        if (t.name.isNotEmpty()) {
                            if (worldType(t.name) == null) {
                                error(t.pos, "未定義のワールド変数 '${t.name}' は削除できません")
                            }
                            worldVars.remove(modelName(t.name))
                            worldDeleted += modelName(t.name)
                        }
                    }
                    is DslAssignTarget.EntityVariable -> {
                        checkVarName(t.name, t.pos)
                        val entityType = checkExpr(t.entity)
                        if (entityType != ValueType.ENTITY) {
                            error(t.entity.pos, "エンティティ変数の対象は entity 型が必要です（${entityType.dslName()} 型）")
                        }
                    }
                }
            }
            is DslIf -> {
                // `if (null)` は条件 subject 未設定の CONDITION ノードへ写像します。
                if (stmt.condition !is DslNull) {
                    val cond = checkExpr(stmt.condition)
                    if (cond != ValueType.BOOLEAN) error(stmt.condition.pos, "if の条件は boolean 型が必要です（${cond.dslName()} 型）")
                }
                checkBlock(stmt.thenBody)
                stmt.elseBody?.let(::checkBlock)
            }
            is DslRepeat -> {
                if (stmt.count !is DslNull) {
                    val count = checkExpr(stmt.count)
                    if (count != ValueType.NUMBER) error(stmt.count.pos, "repeat の回数は number 型が必要です")
                }
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
            is DslCallDisk -> checkCallArgs(stmt.diskId, stmt.args)
        }
    }

    /**
     * `call` の文・式共通の検査です。対象名リテラルと引数列を検査します。
     * 引数名と呼出し先 `defarg` 宣言の照合・型一致はファイル間解決が必要な
     * ため、この層では文法（名前付き・重複なし・値式が妥当）までを保証し、
     * 実在確認はコンパイル時のリンク解決に委ねます。
     */
    private fun checkCallArgs(literal: DslStringLiteral, args: List<DslArg>) {
        checkStringLiteral(literal)
        val seen = mutableSetOf<String>()
        for (arg in args) {
            val name = arg.name ?: error(arg.pos, "call の引数は名前付きで指定してください")
            checkVarName(name, arg.pos)
            if (!seen.add(name)) error(arg.pos, "引数 '${name}' が重複しています")
            // `arg = null` は呼出し先の設定欄が未設定であることを表します。
            if (arg.value !is DslNull) checkExpr(arg.value)
        }
    }

    private fun checkCallStmt(stmt: DslCallStmt) {
        // 引数値の `null` はその設定欄の未設定を表し、型検証をスキップします。
        stmt.args.forEach { if (it.value !is DslNull) checkExpr(it.value) }
        if (stmt.name == "place_structure") requireWorld(stmt.pos, "ストラクチャーの配置")
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
        is DslNull -> error(expr.pos, "null は値の位置では使えません（spec 引数・条件・変数名の未設定を表す場合のみ使えます）")
        is DslNumberLiteral -> ValueType.NUMBER
        is DslStringLiteral -> { checkStringLiteral(expr); ValueType.STRING }
        is DslBooleanLiteral -> ValueType.BOOLEAN
        is DslVarRef -> vars[expr.name] ?: error(expr.pos, "未定義の変数 '${expr.name}' です")
        is DslWorldRef -> {
            requireWorld(expr.pos, "ワールド変数の参照")
            checkVarName(expr.name, expr.pos)
            if (expr.name.isEmpty()) error(expr.pos, "名前が未設定のワールド変数は参照できません")
            worldType(expr.name)
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
        is DslSpecialPosition -> ValueType.LOCATION
        // `~`/`^` 接頭辞の単一成分（`~12`）は location() 等の引数内でのみ
        // 意味を持ちます。型上は number として扱い、コンパイラが用途ごとに
        // 座標セマンティクスへ写像します。
        is DslComponentExpr -> ValueType.NUMBER
        is DslLoopCount -> {
            // CURRENT_LOOP_COUNT はループ外で未解決になるため、DSL では repeat 内に限定します。
            if (loopDepth == 0) error(expr.pos, "loop.count は repeat の内側でのみ使えます")
            ValueType.NUMBER
        }
        // `call "x"(..)` の式は戻り値受取専用です。代入・宣言以外の式位置では
        // 意味を持たないため、ここでは一律に拒否します。
        is DslCallResult -> error(expr.pos, "call は代入・変数宣言の値としてのみ使えます")
        // `vars.<name>` はエンティティ変数への代入値でのみ有効です。それ以外の
        // 式位置（条件・命令引数・他変数への代入等）では意味を持ちません。
        is DslEntityVarRef -> error(expr.pos, "vars.<name> はエンティティ変数の代入値としてのみ使えます")
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

    /**
     * `vars.<src>.<prop>` / `vars.<src>.<prop>(vars.<arg>)` のエンティティ変数
     * 詳細値読み取りを検証します。ENTITY scope は定義表を持たず参照元・
     * 引数変数の型は実行時に決まるため、詳細値の存在・引数の有無・
     * 引数が同じ対象の変数参照であることまでを確認します。
     */
    private fun checkEntityVarPropertyRead(expr: DslPropertyRead): ValueType {
        val source = expr.source as DslEntityVarRef
        checkVarName(source.name, source.pos)
        val def = ValueProperties.definition(expr.name)
            ?: error(expr.pos, "不明な詳細値 '${expr.name}' です")
        when {
            def.argType == null && expr.arg != null ->
                error(expr.arg.pos, "詳細値 '${expr.name}' は引数を取りません")
            def.argType != null && expr.arg == null ->
                error(expr.pos, "詳細値 '${expr.name}' には ${def.argType.dslName()} 型の引数が必要です")
            def.argType != null -> {
                // 引数変数は参照元と同じ対象のエンティティ変数から読み出されます。
                val arg = expr.arg!!
                if (arg !is DslEntityVarRef) {
                    error(arg.pos, "エンティティ変数の詳細値引数は vars.<name> で指定してください")
                }
                checkVarName(arg.name, arg.pos)
            }
        }
        return def.result
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
        "location" -> {
            // yaw/pitch だけの指定は向き（ROTATION）の省略形です。
            // 座標成分が1つでもあれば通常の位置指定として x/y/z を必須にします。
            val names = expr.args.mapNotNull { it.name }.toSet()
            val rotationOnly = names.isNotEmpty() && "x" !in names && "y" !in names && "z" !in names
            checkConstructor(expr, LOCATION_ARGS, relaxRequired = rotationOnly)
            ValueType.LOCATION
        }
        "item" -> { checkConstructor(expr, ITEM_ARGS); ValueType.ITEM }
        "block" -> { checkConstructor(expr, BLOCK_ARGS); ValueType.BLOCK }
        "target" -> checkTarget(expr)
        "player" -> { checkTargetCondCall(expr, playerExtras = true); ValueType.BOOLEAN }
        "exists" -> { checkTargetCondCall(expr, playerExtras = false); ValueType.BOOLEAN }
        "signal" -> { checkSignalCall(expr); ValueType.BOOLEAN }
        "random" -> { checkRandomCall(expr); ValueType.NUMBER }
        "myworld_spawn" -> { checkConstructor(expr, MYWORLD_ARGS); ValueType.LOCATION }
        "orientation" -> { checkOrientation(expr); ValueType.ENTITY }
        // `pow`・三角関数はモデルの数値式言語へそのまま写像します。
        // `a ^ b` は位置成分 `^x` と文法が衝突するため `pow(a, b)` の関数形です。
        "pow" -> { checkNumericFunction(expr, 2); ValueType.NUMBER }
        in NumericExpression.TRIGONOMETRIC_FUNCTIONS -> {
            checkNumericFunction(expr, 1)
            ValueType.NUMBER
        }
        else -> error(expr.pos, "不明な呼出し '${expr.name}' です")
    }

    /**
     * `pow(a, b)`・`sin(x)` 等の数値関数を検証します。モデルの式言語は
     * 位置引数のみのため、名前付き引数・個数違いは拒否します。
     */
    private fun checkNumericFunction(expr: DslCallExpr, arity: Int) {
        if (expr.args.size != arity || expr.args.any { it.name != null }) {
            error(expr.pos, "'${expr.name}' の引数は ${arity} 個の位置引数で指定してください")
        }
        for (arg in expr.args) {
            if (checkExpr(arg.value) != ValueType.NUMBER) {
                error(arg.pos, "'${expr.name}' の引数は number 型が必要です")
            }
        }
    }

    /**
     * `player(...)`・`exists(...)` の対象部を検証します。
     * `target = <entity式>` で直接指定するか、`kind = "..."` 以下の target と
     * 同じ引数で絞り込みます。player は補足条件（sneaking／item）も取ります。
     */
    private fun checkTargetCondCall(expr: DslCallExpr, playerExtras: Boolean) {
        val direct = expr.args.firstOrNull { it.name == "target" }
        if (direct != null && direct.value !is DslNull) {
            val type = checkExpr(direct.value)
            if (type != ValueType.ENTITY) {
                error(direct.pos, "target は entity 型が必要です（${type.dslName()} 型）")
            }
        }
        val seen = mutableSetOf<String>()
        expr.args.forEach { arg ->
            if (arg.name == null) error(arg.pos, "条件呼出しの引数は名前付きが必須です")
            if (!seen.add(arg.name)) error(arg.pos, "引数 '${arg.name}' が重複しています")
            // `arg = null` はその条件欄の未設定を表します。
            if (arg.value is DslNull) return@forEach
            when {
                arg.name == "target" -> Unit // 上で型検証済み
                // `at` は判定位置（conditionPositionSpec）です。
                arg.name == "at" -> {
                    if (checkExpr(arg.value) != ValueType.LOCATION) {
                        error(arg.pos, "引数 'at' は location 型が必要です")
                    }
                }
                arg.name in TARGET_COND_ARGS -> {
                    val spec = TARGET_COND_ARGS.getValue(arg.name)
                    val type = checkExpr(arg.value)
                    if (type != spec.type) error(arg.pos, "引数 '${arg.name}' は ${spec.type.dslName()} 型が必要です")
                    if (!isCompileTimeLiteral(arg.value)) {
                        error(arg.value.pos, "'${expr.name}' の対象引数はリテラルのみ使用できます")
                    }
                }
                playerExtras && arg.name == "sneaking" -> {
                    if (checkExpr(arg.value) != ValueType.BOOLEAN) error(arg.pos, "引数 'sneaking' は boolean 型が必要です")
                    if (arg.value !is DslBooleanLiteral) error(arg.value.pos, "sneaking は true/false リテラルのみ使用できます")
                }
                playerExtras && arg.name == "item" -> {
                    if (checkExpr(arg.value) != ValueType.ITEM) error(arg.pos, "引数 'item' は item 型が必要です")
                }
                else -> error(arg.pos, "不明な引数名 '${arg.name}' です")
            }
        }
        if (direct != null) {
            // target= と kind 引数群の併用は対象指定が二重になるため拒否します。
            val others = expr.args.filter { it.name != "target" && it.name in TARGET_COND_ARGS }
            if (others.isNotEmpty()) error(others.first().pos, "target と kind 系引数は併用できません")
        } else if (expr.args.none { it.name == "kind" }) {
            // モデルの PLAYER／OTHER_ENTITY 判定は対象指定を必須とします。
            error(expr.pos, "'${expr.name}' には対象指定（kind = ... または target = ...）が必要です")
        }
    }

    /** `signal(4)`・`signal(min = a, max = b)` の信号強度条件を検証します。 */
    private fun checkSignalCall(expr: DslCallExpr) {
        val positional = expr.args.filter { it.name == null }
        val named = expr.args.filter { it.name != null }
        if (positional.isNotEmpty() && named.isNotEmpty()) {
            error(expr.pos, "signal は位置引数か min/max 引数のどちらか一方で指定してください")
        }
        val bounds = if (positional.isNotEmpty()) {
            if (positional.size != 1) error(expr.pos, "signal の位置引数は1個だけです")
            listOf(positional.single())
        } else {
            named.forEach {
                if (it.name != "min" && it.name != "max") error(it.pos, "signal の引数名は min/max です")
            }
            if (named.map { it.name }.distinct().size != named.size) error(expr.pos, "signal の引数が重複しています")
            named
        }
        if (bounds.isEmpty()) error(expr.pos, "signal には強度または min/max の指定が必要です")
        bounds.forEach { arg ->
            if (arg.value is DslNull) return@forEach
            val type = checkExpr(arg.value)
            if (type != ValueType.NUMBER) error(arg.pos, "signal の強度は number 型が必要です")
            val v = (arg.value as? DslNumberLiteral)?.value
                ?: error(arg.value.pos, "signal の強度は数値リテラルで指定してください")
            if (v < 0 || v > 15 || v % 1.0 != 0.0) error(arg.value.pos, "signal の強度は 0..15 の整数が必要です")
        }
    }

    /** `random(min = a, max = b)` の乱数生成を検証します。範囲はリテラル必須です。 */
    private fun checkRandomCall(expr: DslCallExpr) {
        checkConstructor(expr, RANDOM_ARGS)
    }

    /** `orientation(<entity式>)` の向き複写を検証します。 */
    private fun checkOrientation(expr: DslCallExpr) {
        val arg = expr.args.singleOrNull()
            ?: error(expr.pos, "orientation は引数を1個だけ取ります")
        if (arg.name != null && arg.name != "target") {
            error(arg.pos, "orientation の引数名は target です")
        }
        val type = checkExpr(arg.value)
        if (type != ValueType.ENTITY) error(arg.pos, "orientation の対象は entity 型が必要です")
    }

    /**
     * コンストラクタの引数を検証します。コンストラクタは全引数が名前付きで、
     * 未知の引数名・必須引数の欠落・重複・型不一致をエラーにします。
     *
     * location/item/block/target の引数はモデルの構造指定（PositionSpec/TargetSpec/
     * TypedValue）へ直接写像するため、実行時式は展開できずリテラルだけを受理します。
     */
    private fun checkConstructor(
        expr: DslCallExpr,
        spec: Map<String, ArgSpec>,
        relaxRequired: Boolean = false,
    ) {
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
        if (!relaxRequired) {
            spec.filter { it.value.required && it.key !in seen }.keys.firstOrNull()?.let {
                error(expr.pos, "必須の引数 '$it' がありません")
            }
        }
        // item の個数はモデルの TypedValue 組立が読まないため、表層でも拒否します。
        if (expr.name == "item" && "amount" in seen) {
            error(expr.pos, "item の 'amount' はモデルが未対応のため使用できません")
        }
    }

    /** コンパイル時定数となる式（数値・非補間文字列・真偽値・名前空間定数）です。 */
    private fun isCompileTimeLiteral(expr: DslExpr): Boolean = when (expr) {
        is DslNumberLiteral, is DslBooleanLiteral, is DslConst, is DslComponentExpr -> true
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

    internal data class ArgSpec(val type: ValueType, val required: Boolean)

    /**
     * DSL の変数名をモデルの変数名へ写像します。
     * 写像規則は [DslNames] が単一正本です。`worldTypes` のキーは
     * モデル側の変数名であるため、参照時にもこの写像を適用します。
     */
    private fun modelName(name: String): String = DslNames.modelName(name)

    /**
     * DSL で宣言・参照できる変数名を検証します。
     * 小文字 snake_case（`[a-z][a-z0-9_]*`）に限定することで、
     * `_`→`-` の写像が一意に定まります。
     */
    private fun checkVarName(name: String, pos: DslPos) {
        // 空文字列は `null` 指定による未命名スロットで、命名規則の対象外です。
        if (name.isEmpty()) return
        if (!namePattern.matches(name)) {
            error(pos, "変数名 '${name}' は小文字の snake_case（[a-z][a-z0-9_]*）で指定してください")
        }
        if (name in RESERVED_NAMES) {
            error(pos, "'${name}' は予約された名前のため変数名として使えません")
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
        // 以下の語彙表はエディタ拡張機能のカタログ生成（generateDslCatalog）
        // からも参照するため internal で公開します。
        internal val INTERP_TYPES = setOf(ValueType.NUMBER, ValueType.STRING, ValueType.BOOLEAN)

        // モデルの条件評価が等値比較できる型（VARIABLE 判定の対応型と一致）です。
        internal val COMPARABLE_TYPES = setOf(ValueType.NUMBER, ValueType.BOOLEAN, ValueType.STRING)

        internal val LOCATION_ARGS = mapOf(
            "x" to ArgSpec(ValueType.NUMBER, true),
            "y" to ArgSpec(ValueType.NUMBER, true),
            "z" to ArgSpec(ValueType.NUMBER, true),
            "yaw" to ArgSpec(ValueType.NUMBER, false),
            "pitch" to ArgSpec(ValueType.NUMBER, false),
        )
        internal val ITEM_ARGS = mapOf(
            "id" to ArgSpec(ValueType.STRING, true),
            "amount" to ArgSpec(ValueType.NUMBER, false),
            "data" to ArgSpec(ValueType.STRING, false),
        )
        internal val BLOCK_ARGS = mapOf(
            "id" to ArgSpec(ValueType.STRING, true),
        )
        internal val TARGET_ARGS = mapOf(
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
        internal val TARGET_KINDS = setOf(
            "nearest_player", "nearby_players", "all_players",
            "random_player", "nearest_entity", "nearby_entities",
        )

        // 条件呼出し（player/exists）が受け取る対象絞り込み引数です。target(...) と同一です。
        internal val TARGET_COND_ARGS = TARGET_ARGS.mapValues { it.value.copy(required = false) }

        internal val RANDOM_ARGS = mapOf(
            "min" to ArgSpec(ValueType.NUMBER, true),
            "max" to ArgSpec(ValueType.NUMBER, true),
        )
        internal val MYWORLD_ARGS = mapOf(
            "kind" to ArgSpec(ValueType.STRING, false),
        )

        internal val GAMEMODE_NAMES = setOf("survival", "creative", "adventure", "spectator")
        internal val EVENT_KINDS = EventTriggerKind.entries.map { it.name.lowercase() }
        internal val TRIGGER_SIDES = setOf("left", "right")
        internal val TRIGGER_STATES = setOf("on", "off")

        internal val namePattern = Regex("[a-z][a-z0-9_]*")
        /** 予約位置式・システム変数と衝突するため変数名として使えない識別子です。 */
        internal val RESERVED_NAMES = setOf("disk", "captured", "loop", "vars")
    }
}
