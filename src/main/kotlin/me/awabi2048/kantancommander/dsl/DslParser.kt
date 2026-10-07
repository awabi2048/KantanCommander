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
    private val tokens: List<DslToken> = DslLexer(text).tokenize()
    private var index = 0

    private fun peek(): DslToken = tokens[index]
    private fun peekKind(): DslTokenKind = peek().kind
    private fun peekKind(offset: Int): DslTokenKind = tokens.getOrNull(index + offset)?.kind ?: DslTokenKind.EOF
    private fun advance(): DslToken = tokens[index++]
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
        expect(DslTokenKind.LBRACE, "program 名の後に '{' を書いてください")
        val body = parseStatements(DslTokenKind.RBRACE)
        expect(DslTokenKind.EOF, "program ブロックの後に余分な記述があります")
        return DslProgram(name, body, head.pos)
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
        advance()
        return stmts
    }

    private fun parseBlock(): List<DslStmt> {
        expect(DslTokenKind.LBRACE, "'{' を書いてください")
        return parseStatements(DslTokenKind.RBRACE)
    }

    private fun parseStatement(): DslStmt = when (peekKind()) {
        DslTokenKind.VAR -> parseVarDecl()
        DslTokenKind.IF -> parseIf()
        DslTokenKind.REPEAT -> parseRepeat()
        DslTokenKind.BREAK -> { val t = advance(); expectSemicolon(); DslBreak(t.pos) }
        DslTokenKind.CONTINUE -> { val t = advance(); expectSemicolon(); DslContinue(t.pos) }
        DslTokenKind.RETURN -> parseReturn()
        DslTokenKind.CALL -> parseCall()
        DslTokenKind.WORLD -> parseAssign(worldTarget = true)
        DslTokenKind.IDENT -> parseIdentStatement()
        DslTokenKind.SEMICOLON -> error(peek().pos, "文が必要です")
        else -> error(peek().pos, "文を解釈できません（'${peek().text}'）")
    }

    private fun parseVarDecl(): DslStmt {
        val head = advance()
        val name = expect(DslTokenKind.IDENT, "var の後に変数名を書いてください").text
        expect(DslTokenKind.COLON, "変数名の後に ':' を書いてください")
        val typeTok = expect(DslTokenKind.IDENT, "':' の後に型名を書いてください")
        val type = dslType(typeTok)
        expect(DslTokenKind.ASSIGN, "型名の後に '= 初期値' を書いてください（宣言のみは認めません）")
        val value = parseExpr()
        expectSemicolon()
        return DslVarDecl(name, type, value, head.pos)
    }

    private fun dslType(token: DslToken): ValueType {
        val type = ValueType.entries.firstOrNull { it.name.equals(token.text, ignoreCase = true) }
            ?: error(token.pos, "不明な型名 '${token.text}' です（number/string/boolean/location/item/block/entity）")
        return type
    }

    /** `world.<name> op expr;` または `ident op expr;` の代入文です。 */
    private fun parseAssign(worldTarget: Boolean): DslStmt {
        val head = advance()
        val target: DslAssignTarget = if (worldTarget) {
            expect(DslTokenKind.DOT, "world の後に '.変数名' を書いてください")
            val name = expect(DslTokenKind.IDENT, "world. の後に変数名を書いてください")
            DslAssignTarget.WorldVariable(name.text, head.pos)
        } else {
            DslAssignTarget.Variable(head.text, head.pos)
        }
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
        return DslAssign(target, op, value, head.pos)
    }

    /** 識別子で始まる文です。`ident(...)` の命令呼出しか `ident op expr` の代入です。 */
    private fun parseIdentStatement(): DslStmt {
        return when (peekKind(1)) {
            DslTokenKind.LPAREN -> {
                val name = advance()
                val args = parseArgs()
                expectSemicolon()
                DslCallStmt(name.text, args, name.pos)
            }
            else -> parseAssign(worldTarget = false)
        }
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
                    DslPropertyRead(expr, name.text, arg, dot.pos)
                }
                // `<expr> ~x ~y ~z` は変数基準の位置リテラルです。
                DslTokenKind.TILDE, DslTokenKind.CARET -> parsePositionLiteral(expr)
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
        DslTokenKind.TRUE -> { val t = advance(); DslBooleanLiteral(true, t.pos) }
        DslTokenKind.FALSE -> { val t = advance(); DslBooleanLiteral(false, t.pos) }
        DslTokenKind.WORLD -> {
            val t = advance()
            expect(DslTokenKind.DOT, "world の後に '.変数名' を書いてください")
            val name = expect(DslTokenKind.IDENT, "world. の後に変数名を書いてください")
            DslWorldRef(name.text, t.pos)
        }
        DslTokenKind.IDENT -> parseIdentExpr()
        DslTokenKind.LPAREN -> {
            advance()
            val inner = parseExpr()
            expect(DslTokenKind.RPAREN, "')' を書いてください")
            inner
        }
        DslTokenKind.TILDE, DslTokenKind.CARET -> parsePositionLiteral(null)
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
     * 位置リテラル `成分 成分 成分` を読みます。
     * 各成分は `数値`（絶対）・`~数値`（相対）・`^数値`（ローカル）で、
     * `~`/`^` 単独は 0 とします。必ず3成分を要求します。
     */
    private fun parsePositionLiteral(base: DslExpr?): DslPositionLiteral {
        val start = peek().pos
        val components = (1..3).map { parsePositionComponent(it) }
        return DslPositionLiteral(base, components, base?.pos ?: start)
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
