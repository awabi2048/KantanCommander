package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.CoordinateSemantics
import me.awabi2048.kantancommander.model.ValueType

/**
 * DSLテキストを構文木（DslProgram）へ変換する再帰下降パーサーです。
 *
 * `.docs/specs/kantan-commander-dsl-draft.md` の表層文法に従います。
 * 型の妥当性（引数の型・個数・プロパティ存在・変数定義順など）は扱わず、
 * 構文と形状だけを検証します。失敗は DslParseException を1件投げます。
 */
class DslParser(text: String) {
    /** 行番号ごとのコメント本文です。文への所属が決まるまでここで保持します。 */
    private val comments = sortedMapOf<Int, String>()
    private val tokens: List<DslToken>
    private var index = 0
    /** 最後に消費したトークンの行です。コメントの所属先を行番号で判定するために使います。 */
    private var lastLine = 0
    /** 文の pos をキーとするコメント一覧です。 */
    private val stmtComments = mutableMapOf<DslPos, MutableList<String>>()
    /** 次の文へ引き継ぐ、未所属のコメントです。 */
    private val pending = mutableListOf<String>()

    init {
        tokens = DslLexer(text).tokenize().filter {
            if (it.kind == DslTokenKind.COMMENT) {
                comments[it.pos.line] = it.text
                false
            } else true
        }
    }

    private fun peek(): DslToken = tokens[index]
    private fun peekKind(): DslTokenKind = peek().kind
    private fun peekKind(offset: Int): DslTokenKind = tokens.getOrNull(index + offset)?.kind ?: DslTokenKind.EOF
    private fun advance(): DslToken = tokens[index++].also { lastLine = it.pos.line }
    private fun accept(kind: DslTokenKind): DslToken? = if (peekKind() == kind) advance() else null
    private fun expect(kind: DslTokenKind, message: String): DslToken =
        accept(kind) ?: error(peek().pos, message)
    private fun error(pos: DslPos, message: String): Nothing = throw DslParseException(message, pos)

    /** 文の終端 `;` です。1行化した入力も解釈できるよう必須とします。 */
    private fun expectSemicolon() {
        expect(DslTokenKind.SEMICOLON, "文の終わりに ';' を書いてください")
    }

    fun parse(): DslProgram {
        val head = expect(DslTokenKind.PROGRAM, "スクリプトは program で始めてください")
        val name = parsePlainString("program の後に \"名前\" を書いてください", "program 名に補間は使えません")
        expectSemicolon()
        // 宣言部（import／trigger／defarg）は本文より前にだけ書けます。
        val decls = mutableListOf<DslDecl>()
        while (true) {
            when (peekKind()) {
                DslTokenKind.IMPORT -> decls += parseImport()
                DslTokenKind.TRIGGER -> decls += parseTrigger()
                DslTokenKind.DEFARG -> decls += parseDefarg()
                else -> break
            }
        }
        val body = mutableListOf<DslStmt>()
        while (peekKind() != DslTokenKind.EOF) {
            body += parseStatement()
        }
        // 末尾に残ったコメント（本文の最後の文以降）は最後の文へ寄せて保持します。
        // 文が1つも無いプログラムではコメントの所属先が無いため保持しません。
        if (comments.isNotEmpty() && body.isNotEmpty()) {
            stmtComments.getOrPut(body.last().pos) { mutableListOf() } += comments.values
            comments.clear()
        }
        return DslProgram(name, decls, body, head.pos, stmtComments)
    }

    /**
     * 次のトークンの手前までにある未消費コメントを pending へ集めます。
     * 宣言部やファイル先頭のコメントは、最初の文の直前に現れるものとして
     * ここで一緒に拾われます。
     */
    private fun collectLeading() {
        val nextLine = peek().pos.line
        comments.keys
            .filter { it in (lastLine + 1) until nextLine }
            .forEach { pending += comments.remove(it)!! }
    }

    /** `import world;` の宣言です。現状の宣言対象は world のみです。 */
    private fun parseImport(): DslDecl {
        val head = advance()
        val what = advance()
        if (what.kind != DslTokenKind.WORLD) error(what.pos, "import の対象は world です")
        expectSemicolon()
        return DslImportWorld(head.pos)
    }

    /**
     * `trigger timer(interval = 20);`・`trigger redstone;`・`trigger event(...);`
     * です。裸識別子（redstone）は引数なし呼出しへ正規化します。
     */
    private fun parseTrigger(): DslDecl {
        val head = advance()
        val expr = parseIdentExpr()
        expectSemicolon()
        val spec = when (expr) {
            is DslCallExpr -> expr
            is DslVarRef -> DslCallExpr(expr.name, emptyList(), expr.pos)
            else -> error(expr.pos, "trigger には timer(...)・redstone・event(...) のいずれかを書いてください")
        }
        return DslTriggerDecl(spec, head.pos)
    }

    /** `defarg name: type;` の引数宣言です。引数名は必須で、`defarg null:` は検証で拒否します。 */
    private fun parseDefarg(): DslDecl {
        val head = advance()
        val name = parseVarName("defarg の後に引数名を書いてください")
        expect(DslTokenKind.COLON, "引数名の後に ':' を書いてください")
        val type = dslType(expect(DslTokenKind.IDENT, "':' の後に型名を書いてください"))
        expectSemicolon()
        return DslDefarg(name, type, head.pos)
    }

    /** `"..."` の素文字列を読みます。補間を含む場合はエラーにします。 */
    private fun parsePlainString(missingMessage: String, interpMessage: String): String {
        expect(DslTokenKind.STRING_START, missingMessage)
        val sb = StringBuilder()
        while (true) {
            when (peekKind()) {
                DslTokenKind.STRING_TEXT -> sb.append(advance().text)
                DslTokenKind.INTERP_START -> error(peek().pos, interpMessage)
                DslTokenKind.STRING_END -> { advance(); return sb.toString() }
                else -> error(peek().pos, "文字列が正しく閉じられていません")
            }
        }
    }

    /** ブロック内の文列を読み、終端トークンを消費して返します。 */
    private fun parseStatements(terminator: DslTokenKind): List<DslStmt> {
        val stmts = mutableListOf<DslStmt>()
        while (peekKind() != terminator) {
            if (peekKind() == DslTokenKind.EOF) error(peek().pos, "ブロックが閉じられていません")
            stmts += parseStatement()
        }
        // ブロック末尾の未所属コメントは、次の文へ引き継いで保持します。
        collectLeading()
        advance()
        return stmts
    }

    private fun parseBlock(): List<DslStmt> {
        expect(DslTokenKind.LBRACE, "'{' を書いてください")
        return parseStatements(DslTokenKind.RBRACE)
    }

    /**
     * 文を読み、その文へ所属するコメントを解決します。
     * 文の前行にあるコメントは先行コメント、文と同じ行の末尾コメントは
     * その文のコメントとして保持します（印刷時はいずれも文の直前へ出ます）。
     */
    private fun parseStatement(): DslStmt {
        collectLeading()
        // この文の手前までのコメントを確定します。内側の文を読む再帰へ
        // 引き継がないよう、いったん pending から切り離します。
        val mine = pending.toMutableList()
        pending.clear()
        val stmt = parseStatementBody()
        comments.remove(lastLine)?.let { mine += it }
        if (mine.isNotEmpty()) {
            stmtComments.getOrPut(stmt.pos) { mutableListOf() } += mine
        }
        return stmt
    }

    private fun parseStatementBody(): DslStmt = when (peekKind()) {
        DslTokenKind.VAR -> parseVarDecl()
        DslTokenKind.IF -> parseIf()
        DslTokenKind.REPEAT -> parseRepeat()
        DslTokenKind.BREAK -> { val t = advance(); expectSemicolon(); DslBreak(t.pos) }
        DslTokenKind.CONTINUE -> { val t = advance(); expectSemicolon(); DslContinue(t.pos) }
        DslTokenKind.RETURN -> parseReturn()
        DslTokenKind.CALL -> parseCall()
        DslTokenKind.DEL -> parseDelete()
        DslTokenKind.WORLD -> parseAssign(parseAssignTarget())
        // `null = e` は未命名（変数名が空）の変数への書き込みです。
        DslTokenKind.NULL -> parseAssign(parseAssignTarget())
        DslTokenKind.IDENT -> parseIdentStatement()
        DslTokenKind.SEMICOLON -> error(peek().pos, "文が必要です")
        else -> error(peek().pos, "文を解釈できません（'${peek().text}'）")
    }

    /**
     * 変数名の位置を読みます。`null` は未命名（GUI の空名スロット）を表し、
     * 内部では空文字列として保持します。
     */
    private fun parseVarName(missingMessage: String): String {
        accept(DslTokenKind.NULL)?.let { return "" }
        return expect(DslTokenKind.IDENT, missingMessage).text
    }

    private fun parseVarDecl(): DslStmt {
        val head = advance()
        // `var world.x: T` はワールド変数の新規定義です。
        val world = accept(DslTokenKind.WORLD) != null
        if (world) expect(DslTokenKind.DOT, "var world の後に '.変数名' を書いてください")
        val name = parseVarName("var の後に変数名を書いてください")
        expect(DslTokenKind.COLON, "変数名の後に ':' を書いてください")
        val typeTok = expect(DslTokenKind.IDENT, "':' の後に型名を書いてください")
        val type = dslType(typeTok)
        expect(DslTokenKind.ASSIGN, "型名の後に '= 初期値' を書いてください（宣言のみは認めません）")
        val value = parseExpr()
        expectSemicolon()
        return DslVarDecl(name, type, value, world, head.pos)
    }

    private fun dslType(token: DslToken): ValueType {
        val type = ValueType.entries.firstOrNull { it.name.equals(token.text, ignoreCase = true) }
            ?: error(token.pos, "不明な型名 '${token.text}' です（number/string/boolean/location/item/block/entity）")
        return type
    }

    /**
     * 代入・削除の対象を読みます。
     * `x`・`world.x`・`expr.vars.name`（エンティティ変数）のいずれかです。
     * 対象式として呼出し形（`target(...)`）も受理します。
     */
    private fun parseAssignTarget(): DslAssignTarget {
        val head = peek()
        return when (head.kind) {
            DslTokenKind.WORLD -> {
                advance()
                expect(DslTokenKind.DOT, "world の後に '.変数名' を書いてください")
                val name = parseVarName("world. の後に変数名を書いてください")
                DslAssignTarget.WorldVariable(name, head.pos)
            }
            DslTokenKind.NULL -> {
                advance()
                DslAssignTarget.Variable("", head.pos)
            }
            DslTokenKind.IDENT -> {
                val primary = parseIdentExpr()
                parseAssignTargetTail(primary, head.pos)
            }
            else -> error(head.pos, "代入先は変数名・world.変数名・対象.vars.変数名のいずれかです")
        }
    }

    /** 先頭式の直後が `.vars.<name>` かを判定します（エンティティ変数アクセスの印）。 */
    private fun isEntityVarAccess(): Boolean =
        peekKind() == DslTokenKind.DOT &&
            peekKind(1) == DslTokenKind.IDENT &&
            tokens.getOrNull(index + 1)?.text == "vars" &&
            peekKind(2) == DslTokenKind.DOT

    private fun parseAssignTargetTail(primary: DslExpr, pos: DslPos): DslAssignTarget {
        if (isEntityVarAccess()) {
            advance() // .
            advance() // vars
            advance() // .
            val name = parseVarName("'.vars.' の後に変数名を書いてください")
            return DslAssignTarget.EntityVariable(primary, name, pos)
        }
        if (primary is DslVarRef) return DslAssignTarget.Variable(primary.name, primary.pos)
        error(primary.pos, "代入先は変数名・world.変数名・対象.vars.変数名のいずれかです")
    }

    /** `target op expr;` の演算子と値部分です。 */
    private fun parseAssign(target: DslAssignTarget): DslStmt {
        val op = when (peekKind()) {
            DslTokenKind.ASSIGN -> DslAssignOp.ASSIGN
            DslTokenKind.PLUS_ASSIGN -> DslAssignOp.ADD
            DslTokenKind.MINUS_ASSIGN -> DslAssignOp.SUB
            DslTokenKind.STAR_ASSIGN -> DslAssignOp.MUL
            DslTokenKind.SLASH_ASSIGN -> DslAssignOp.DIV
            DslTokenKind.PERCENT_ASSIGN -> DslAssignOp.MOD
            else -> error(peek().pos, "変数名の後に '=' '+=' '-=' '*=' '/=' '%=' のいずれかを書いてください")
        }
        advance()
        val value = parseExpr()
        expectSemicolon()
        return DslAssign(target, op, value, target.pos)
    }

    /** `del <target>;` の削除文です。 */
    private fun parseDelete(): DslStmt {
        val head = advance()
        val target = parseAssignTarget()
        expectSemicolon()
        return DslDelete(target, head.pos)
    }

    /**
     * 識別子で始まる文です。`ident(...)` の命令呼出し、`ident op expr` の代入、
     * `expr.vars.name op expr` のエンティティ変数代入のいずれかです。
     */
    private fun parseIdentStatement(): DslStmt {
        val head = peek()
        if (peekKind(1) == DslTokenKind.LPAREN) {
            val primary = parseIdentExpr()
            if (isEntityVarAccess()) return parseAssign(parseAssignTargetTail(primary, head.pos))
            if (primary is DslCallExpr) {
                expectSemicolon()
                return DslCallStmt(primary.name, primary.args, primary.pos)
            }
            error(peek().pos, "文を解釈できません（'${peek().text}'）")
        }
        return parseAssign(parseAssignTarget())
    }

    private fun parseIf(): DslStmt {
        val head = advance()
        expect(DslTokenKind.LPAREN, "if の後に '(' を書いてください")
        val condition = parseExpr()
        expect(DslTokenKind.RPAREN, "条件の後に ')' を書いてください")
        val thenBody = parseBlock()
        val elseBody = if (accept(DslTokenKind.ELSE) != null) {
            when (peekKind()) {
                DslTokenKind.IF -> listOf(parseIf())
                DslTokenKind.LBRACE -> parseBlock()
                else -> error(peek().pos, "else の後に 'if' または '{' を書いてください")
            }
        } else null
        return DslIf(condition, thenBody, elseBody, head.pos)
    }

    private fun parseRepeat(): DslStmt {
        val head = advance()
        expect(DslTokenKind.LPAREN, "repeat の後に '(' を書いてください")
        val count = parseExpr()
        expect(DslTokenKind.RPAREN, "回数の後に ')' を書いてください")
        return DslRepeat(count, parseBlock(), head.pos)
    }

    private fun parseReturn(): DslStmt {
        val head = advance()
        if (peekKind() == DslTokenKind.SEMICOLON) {
            advance()
            return DslReturn(null, head.pos)
        }
        val value = parseExpr()
        expectSemicolon()
        return DslReturn(value, head.pos)
    }

    private fun parseCall(): DslStmt {
        val head = advance()
        val literal = parseStringLiteral()
        expectSemicolon()
        return DslCallDisk(literal, head.pos)
    }

    private fun parseArgs(): List<DslArg> {
        expect(DslTokenKind.LPAREN, "'(' を書いてください")
        val args = mutableListOf<DslArg>()
        var seenNamed = false
        while (peekKind() != DslTokenKind.RPAREN) {
            val argPos = peek().pos
            if (peekKind() == DslTokenKind.IDENT && peekKind(1) == DslTokenKind.ASSIGN) {
                seenNamed = true
                val name = advance().text
                advance()
                args += DslArg(name, parseExpr(), argPos)
            } else {
                if (seenNamed) error(argPos, "位置引数は名前付き引数の前にのみ書けます")
                args += DslArg(null, parseExpr(), argPos)
            }
            if (accept(DslTokenKind.COMMA) == null) break
        }
        expect(DslTokenKind.RPAREN, "')' を書いてください")
        return args
    }

    // ---- 式 ----

    private fun parseExpr(): DslExpr = parseOr()

    private fun parseOr(): DslExpr {
        var expr = parseAnd()
        while (true) {
            val op = accept(DslTokenKind.OR) ?: break
            expr = DslBinary(DslBinaryOp.OR, expr, parseAnd(), op.pos)
        }
        return expr
    }

    private fun parseAnd(): DslExpr {
        var expr = parseEq()
        while (true) {
            val op = accept(DslTokenKind.AND) ?: break
            expr = DslBinary(DslBinaryOp.AND, expr, parseEq(), op.pos)
        }
        return expr
    }

    private fun parseEq(): DslExpr {
        var expr = parseRel()
        while (true) {
            val opTok = accept(DslTokenKind.EQ) ?: accept(DslTokenKind.NE) ?: break
            val op = if (opTok.kind == DslTokenKind.EQ) DslBinaryOp.EQ else DslBinaryOp.NE
            expr = DslBinary(op, expr, parseRel(), opTok.pos)
        }
        return expr
    }

    private fun parseRel(): DslExpr {
        var expr = parseAdd()
        while (true) {
            val op = when (peekKind()) {
                DslTokenKind.LT -> DslBinaryOp.LT
                DslTokenKind.LE -> DslBinaryOp.LE
                DslTokenKind.GT -> DslBinaryOp.GT
                DslTokenKind.GE -> DslBinaryOp.GE
                else -> break
            }
            val at = advance().pos
            expr = DslBinary(op, expr, parseAdd(), at)
        }
        return expr
    }

    private fun parseAdd(): DslExpr {
        var expr = parseMul()
        while (true) {
            val op = when (peekKind()) {
                DslTokenKind.PLUS -> DslBinaryOp.ADD
                DslTokenKind.MINUS -> DslBinaryOp.SUB
                else -> break
            }
            val at = advance().pos
            expr = DslBinary(op, expr, parseMul(), at)
        }
        return expr
    }

    private fun parseMul(): DslExpr {
        var expr = parseUnary()
        while (true) {
            val op = when (peekKind()) {
                DslTokenKind.STAR -> DslBinaryOp.MUL
                DslTokenKind.SLASH -> DslBinaryOp.DIV
                DslTokenKind.PERCENT -> DslBinaryOp.MOD
                else -> break
            }
            val at = advance().pos
            expr = DslBinary(op, expr, parseUnary(), at)
        }
        return expr
    }

    private fun parseUnary(): DslExpr {
        return when (peekKind()) {
            DslTokenKind.NOT -> { val t = advance(); DslUnary(DslUnaryOp.NOT, parseUnary(), t.pos) }
            DslTokenKind.MINUS -> { val t = advance(); DslUnary(DslUnaryOp.NEG, parseUnary(), t.pos) }
            else -> parsePostfix()
        }
    }

    /** プロパティ読出し（`.name` / `.name(arg)`）と基準付き位置リテラルの後置です。 */
    private fun parsePostfix(): DslExpr {
        var expr = parsePrimary()
        while (true) {
            expr = when (peekKind()) {
                DslTokenKind.DOT -> {
                    val dot = advance()
                    val name = expect(DslTokenKind.IDENT, "'.' の後に詳細値名を書いてください")
                    val arg = if (accept(DslTokenKind.LPAREN) != null) {
                        val a = parseExpr()
                        expect(DslTokenKind.RPAREN, "')' を書いてください")
                        a
                    } else null
                    // `loop.count` は実行コンテキスト供給のシステム変数であり、
                    // 詳細値参照ではなく専用の式として読みます。
                    if (expr is DslVarRef && expr.name == "loop" && name.text == "count" && arg == null) {
                        DslLoopCount(dot.pos)
                    } else {
                        DslPropertyRead(expr, name.text, arg, dot.pos)
                    }
                }
                // `<expr> ~x ~y ~z` は変数基準の位置リテラルです。
                DslTokenKind.TILDE, DslTokenKind.CARET -> parsePositionOrComponent(expr)
                else -> return expr
            }
        }
    }

    private fun parsePrimary(): DslExpr = when (peekKind()) {
        DslTokenKind.NUMBER -> {
            val t = advance()
            val value = t.text.toDoubleOrNull()
                ?: error(t.pos, "数値リテラル '${t.text}' を解釈できません")
            if (!value.isFinite()) error(t.pos, "非有限の数値は使えません")
            DslNumberLiteral(value, t.pos)
        }
        DslTokenKind.STRING_START -> parseStringLiteral()
        DslTokenKind.NULL -> { val t = advance(); DslNull(t.pos) }
        DslTokenKind.TRUE -> { val t = advance(); DslBooleanLiteral(true, t.pos) }
        DslTokenKind.FALSE -> { val t = advance(); DslBooleanLiteral(false, t.pos) }
        DslTokenKind.WORLD -> {
            val t = advance()
            expect(DslTokenKind.DOT, "world の後に '.変数名' を書いてください")
            val name = parseVarName("world. の後に変数名を書いてください")
            DslWorldRef(name, t.pos)
        }
        DslTokenKind.IDENT -> parseIdentExpr()
        DslTokenKind.LPAREN -> {
            advance()
            val inner = parseExpr()
            expect(DslTokenKind.RPAREN, "')' を書いてください")
            inner
        }
        DslTokenKind.TILDE, DslTokenKind.CARET -> parsePositionOrComponent(null)
        else -> error(peek().pos, "式を解釈できません（'${peek().text}'）")
    }

    private fun parseIdentExpr(): DslExpr {
        val name = advance()
        return when {
            // `gamemode.<定数>` は名前空間付き定数として読み、変数参照と区別します。
            name.text == "gamemode" && peekKind() == DslTokenKind.DOT -> {
                advance()
                val member = expect(DslTokenKind.IDENT, "gamemode の後に定数名を書いてください")
                if (member.text !in GAMEMODE_CONSTANTS) {
                    error(member.pos, "gamemode の定数名が不明です（${GAMEMODE_CONSTANTS.joinToString("/")}）")
                }
                DslConst("gamemode", member.text, name.pos)
            }
            peekKind() == DslTokenKind.LPAREN -> DslCallExpr(name.text, parseArgs(), name.pos)
            // 実行コンテキストが供給する予約位置式です（制御ブロック位置・捕捉位置）。
            name.text == "disk" || name.text == "captured" -> DslSpecialPosition(name.text, name.pos)
            else -> DslVarRef(name.text, name.pos)
        }
    }

    private fun parseStringLiteral(): DslStringLiteral {
        val start = expect(DslTokenKind.STRING_START, "文字列は '\"' で始めてください")
        val parts = mutableListOf<DslStringPart>()
        while (true) {
            when (peekKind()) {
                DslTokenKind.STRING_TEXT -> parts += DslTextPart(advance().text)
                DslTokenKind.INTERP_START -> {
                    advance()
                    parts += DslInterpPart(parseExpr())
                    expect(DslTokenKind.INTERP_END, "補間の '}' を書いてください")
                }
                DslTokenKind.STRING_END -> { advance(); return DslStringLiteral(parts, start.pos) }
                else -> error(peek().pos, "文字列が正しく閉じられていません")
            }
        }
    }

    /**
     * `~`/`^` で始まる式を読みます。後続が成分（`~x`・`^x`・`数値`・`-数値`）なら
     * 3成分の位置リテラル、そうでなければ単一成分（`~12`・`^0`）です。
     * 単一成分は `location(yaw = ~90)` のような引数値でのみ意味を持ちます。
     * `base` がある場合は必ず3成分の位置リテラルとして読みます。
     */
    private fun parsePositionOrComponent(base: DslExpr?): DslExpr {
        val start = peek().pos
        val first = parsePositionComponent(1)
        return when (peekKind()) {
            DslTokenKind.TILDE, DslTokenKind.CARET, DslTokenKind.NUMBER, DslTokenKind.MINUS -> {
                val second = parsePositionComponent(2)
                val third = parsePositionComponent(3)
                DslPositionLiteral(base, listOf(first, second, third), base?.pos ?: start)
            }
            else -> {
                if (base != null) {
                    error(peek().pos, "位置リテラルは3成分必要です")
                }
                DslComponentExpr(first.semantics, first.value, start)
            }
        }
    }

    private fun parsePositionComponent(ordinal: Int): DslPositionComponent {
        val semantics = when (peekKind()) {
            DslTokenKind.TILDE -> { advance(); CoordinateSemantics.RELATIVE }
            DslTokenKind.CARET -> { advance(); CoordinateSemantics.LOCAL }
            else -> CoordinateSemantics.ABSOLUTE
        }
        if (semantics != CoordinateSemantics.ABSOLUTE &&
            peekKind() != DslTokenKind.NUMBER && peekKind() != DslTokenKind.MINUS) {
            // `~` / `^` 単独は 0 の成分です。
            return DslPositionComponent(semantics, 0.0)
        }
        val negative = accept(DslTokenKind.MINUS) != null
        val num = expect(DslTokenKind.NUMBER, "位置リテラルの ${ordinal} 成分目の数値を書いてください")
        val value = num.text.toDoubleOrNull() ?: error(num.pos, "位置成分 '${num.text}' を解釈できません")
        return DslPositionComponent(semantics, if (negative) -value else value)
    }

    companion object {
        /** `gamemode.<定数>` の有効な接尾辞です。内部値は GameMode.name の小文字形です。 */
        val GAMEMODE_CONSTANTS = setOf("survival", "creative", "adventure", "spectator")

        fun parse(text: String): DslProgram = DslParser(text).parse()
    }
}
