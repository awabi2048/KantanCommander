package me.awabi2048.kantancommander.dsl

/** DSL の字句解析・構文解析の失敗です。ユーザー向けの日本語メッセージを持ちます。 */
class DslParseException(message: String, val pos: DslPos) : Exception("${pos}: $message")

enum class DslTokenKind {
    IDENT, NUMBER,
    STRING_START, STRING_TEXT, STRING_END, INTERP_START, INTERP_END,
    PROGRAM, VAR, IF, ELSE, REPEAT, BREAK, CONTINUE, RETURN, CALL, WORLD, TRUE, FALSE, DEL,
    LBRACE, RBRACE, LPAREN, RPAREN, COMMA, SEMICOLON, COLON, DOT,
    ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN,
    EQ, NE, LT, LE, GT, GE, AND, OR, NOT,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    TILDE, CARET, EOF,
}

data class DslToken(val kind: DslTokenKind, val text: String, val pos: DslPos)

/**
 * DSLテキストをトークン列へ変換します。
 *
 * 文字列リテラルは `STRING_START` …（`STRING_TEXT` または `INTERP_START` 式 `INTERP_END`）…
 * `STRING_END` の並びで出し、補間式はパーサーへそのまま渡せる通常トークンとして流します。
 * `#` と `//` の行コメントは読み飛ばします。
 */
class DslLexer(private val text: String) {
    private var index = 0
    private var line = 1
    private var column = 1
    private val tokens = mutableListOf<DslToken>()

    /** 文字列リテラルのスキャン中かどうかです。 */
    private var inString = false
    /** `"` の位置です。閉じ忘れエラーの報告に使います。 */
    private var stringStartPos: DslPos? = null
    /** `${` で開いた補間ごとの `{` 深さです。末尾要素が現在の補間です。 */
    private val interpBraceDepth = ArrayDeque<Int>()

    private fun pos(): DslPos = DslPos(line, column)

    private fun peekChar(offset: Int = 0): Char? = text.getOrNull(index + offset)

    private fun advance() {
        if (index < text.length && text[index] == '\n') {
            line++
            column = 1
        } else {
            column++
        }
        index++
    }

    private fun emit(kind: DslTokenKind, value: String, at: DslPos = pos()) {
        tokens += DslToken(kind, value, at)
    }

    private fun error(at: DslPos, message: String): Nothing = throw DslParseException(message, at)

    fun tokenize(): List<DslToken> {
        while (true) {
            if (inString) {
                if (index >= text.length) {
                    error(stringStartPos ?: pos(), "文字列が閉じられていません")
                }
                continueString()
            } else {
                if (index >= text.length) break
                when (val c = text[index]) {
                    ' ' , '\t', '\r', '\n' -> advance()
                    '#' -> skipLineComment()
                    '/' -> if (peekChar(1) == '/') skipLineComment() else lexSymbol()
                    '"' -> {
                        stringStartPos = pos()
                        emit(DslTokenKind.STRING_START, "\"")
                        inString = true
                        advance()
                    }
                    in '0'..'9' -> lexNumber()
                    else -> if (c.isLetter() || c == '_') lexIdent() else lexSymbol()
                }
            }
        }
        emit(DslTokenKind.EOF, "")
        return tokens
    }

    private fun skipLineComment() {
        while (index < text.length && text[index] != '\n') advance()
    }

    private fun lexNumber() {
        val start = pos()
        val sb = StringBuilder()
        while (peekChar()?.isDigit() == true) {
            sb.append(text[index]); advance()
        }
        if (peekChar() == '.' && peekChar(1)?.isDigit() == true) {
            sb.append(text[index]); advance()
            while (peekChar()?.isDigit() == true) {
                sb.append(text[index]); advance()
            }
        }
        emit(DslTokenKind.NUMBER, sb.toString(), start)
    }

    private fun lexIdent() {
        val start = pos()
        val sb = StringBuilder()
        while (peekChar()?.let { it.isLetterOrDigit() || it == '_' } == true) {
            sb.append(text[index]); advance()
        }
        emit(keywords[sb.toString()] ?: DslTokenKind.IDENT, sb.toString(), start)
    }

    private fun lexSymbol() {
        val at = pos()
        val c = text[index]
        fun two(kind2: DslTokenKind, kind1: DslTokenKind, second: Char) {
            if (peekChar(1) == second) { advance(); advance(); emit(kind2, "$c$second", at) }
            else { advance(); emit(kind1, c.toString(), at) }
        }
        when (c) {
            '{' -> {
                if (interpBraceDepth.isNotEmpty()) {
                    interpBraceDepth[interpBraceDepth.lastIndex]++
                }
                advance(); emit(DslTokenKind.LBRACE, "{", at)
            }
            '}' -> {
                advance()
                if (interpBraceDepth.isNotEmpty() && interpBraceDepth.last() == 0) {
                    // 補間式の終端です。`}` はトークンにせず文字列スキャンへ戻ります。
                    interpBraceDepth.removeLast()
                    emit(DslTokenKind.INTERP_END, "}", at)
                    inString = true
                } else {
                    if (interpBraceDepth.isNotEmpty()) {
                        interpBraceDepth[interpBraceDepth.lastIndex]--
                    }
                    emit(DslTokenKind.RBRACE, "}", at)
                }
            }
            '(' -> { advance(); emit(DslTokenKind.LPAREN, "(", at) }
            ')' -> { advance(); emit(DslTokenKind.RPAREN, ")", at) }
            ',' -> { advance(); emit(DslTokenKind.COMMA, ",", at) }
            ';' -> { advance(); emit(DslTokenKind.SEMICOLON, ";", at) }
            ':' -> { advance(); emit(DslTokenKind.COLON, ":", at) }
            '.' -> { advance(); emit(DslTokenKind.DOT, ".", at) }
            '~' -> { advance(); emit(DslTokenKind.TILDE, "~", at) }
            '^' -> { advance(); emit(DslTokenKind.CARET, "^", at) }
            '=' -> two(DslTokenKind.EQ, DslTokenKind.ASSIGN, '=')
            '!' -> two(DslTokenKind.NE, DslTokenKind.NOT, '=')
            '<' -> two(DslTokenKind.LE, DslTokenKind.LT, '=')
            '>' -> two(DslTokenKind.GE, DslTokenKind.GT, '=')
            '&' -> if (peekChar(1) == '&') { advance(); advance(); emit(DslTokenKind.AND, "&&", at) }
                else error(at, "'&' は '&&' として書いてください")
            '|' -> if (peekChar(1) == '|') { advance(); advance(); emit(DslTokenKind.OR, "||", at) }
                else error(at, "'|' は '||' として書いてください")
            '+' -> two(DslTokenKind.PLUS_ASSIGN, DslTokenKind.PLUS, '=')
            '-' -> two(DslTokenKind.MINUS_ASSIGN, DslTokenKind.MINUS, '=')
            '*' -> two(DslTokenKind.STAR_ASSIGN, DslTokenKind.STAR, '=')
            '/' -> two(DslTokenKind.SLASH_ASSIGN, DslTokenKind.SLASH, '=')
            '%' -> two(DslTokenKind.PERCENT_ASSIGN, DslTokenKind.PERCENT, '=')
            else -> error(at, "解釈できない文字 '$c' です")
        }
    }

    /**
     * 文字列リテラルの中身をスキャンします。`"`・`${`・`\` 以外はそのまま蓄積し、
     * `${` では INTERP_START を出して通常字句へ戻ります。
     */
    private fun continueString() {
        val textStart = pos()
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) emit(DslTokenKind.STRING_TEXT, sb.toString(), textStart)
        }
        while (index < text.length) {
            val c = text[index]
            when {
                c == '"' -> {
                    flush()
                    val at = pos(); advance()
                    emit(DslTokenKind.STRING_END, "\"", at)
                    inString = false
                    return
                }
                c == '$' && peekChar(1) == '{' -> {
                    flush()
                    val at = pos(); advance(); advance()
                    emit(DslTokenKind.INTERP_START, "\${", at)
                    interpBraceDepth.addLast(0)
                    inString = false
                    return
                }
                c == '\\' -> {
                    advance()
                    when (val e = peekChar()) {
                        '"' -> { sb.append('"'); advance() }
                        '\\' -> { sb.append('\\'); advance() }
                        'n' -> { sb.append('\n'); advance() }
                        else -> error(pos(), "使用できないエスケープ '\\${e ?: ""}' です（\\\" \\\\ \\n のみ）")
                    }
                }
                else -> { sb.append(c); advance() }
            }
        }
        // 末尾まで来ても閉じられなかった場合は tokenize 側がエラーにします。
    }

    private val keywords = mapOf(
        "program" to DslTokenKind.PROGRAM,
        "var" to DslTokenKind.VAR,
        "if" to DslTokenKind.IF,
        "else" to DslTokenKind.ELSE,
        "repeat" to DslTokenKind.REPEAT,
        "break" to DslTokenKind.BREAK,
        "continue" to DslTokenKind.CONTINUE,
        "return" to DslTokenKind.RETURN,
        "call" to DslTokenKind.CALL,
        "world" to DslTokenKind.WORLD,
        "del" to DslTokenKind.DEL,
        "true" to DslTokenKind.TRUE,
        "false" to DslTokenKind.FALSE,
    )
}
