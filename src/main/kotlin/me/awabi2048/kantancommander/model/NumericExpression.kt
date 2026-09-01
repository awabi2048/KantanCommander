package me.awabi2048.kantancommander.model

/**
 * VARIABLEの数値計算で共有する小さな式言語です。
 *
 * GUI・実行・バニラ出力がそれぞれ独自にsplitや優先順位処理を持つと、同じ式が
 * 保存時と実行時で別の値になるため、字句解析と構文解析をモデル層へ集約します。
 * 許可する構文は数値リテラル、変数名、読み取り専用の$current_*、四則演算、括弧です。
 */
object NumericExpression {
    private val namePattern = Regex("[a-z][a-z0-9_.-]{0,63}")
    private val readOnlyNames = setOf("\$current_iteration_value", "\$current_loop_count")

    /**
     * パーサーの失敗理由を表示文言から分離します。
     *
     * 式をモデル層で日本語化すると、保存・実行・GUIごとに翻訳が分岐します。
     * ここでは安定したエラー種別だけを返し、ユーザー向けの文面はCC-Systemの
     * ローカライズキーへ解決します。
     */
    enum class ErrorCode {
        EMPTY,
        TRAILING_CHARACTERS,
        UNCLOSED_PARENTHESIS,
        OPERAND_REQUIRED,
        INVALID_NUMBER,
        INVALID_CHARACTER,
        INVALID_READONLY_NAME,
        INVALID_VARIABLE_NAME,
    }

    data class ParseError(
        val code: ErrorCode,
        val token: String? = null,
    )

    data class ParseResult(
        val expression: Parsed? = null,
        val error: ParseError? = null,
    ) {
        val isSuccess: Boolean get() = expression != null
    }

    class Parsed internal constructor(private val root: Expr, val references: Set<String>) {
        fun evaluate(resolve: (String) -> Double?): Double? = root.evaluate(resolve)?.takeIf(Double::isFinite)

        /** Vanilla exporterがscoreboard演算へ写像できるよう、式を後置記法で公開します。 */
        internal fun postfix(): List<PostfixToken> = buildList { root.appendPostfix(this) }
    }

    internal sealed interface PostfixToken {
        data class Literal(val value: Double) : PostfixToken
        data class Reference(val name: String) : PostfixToken
        data class Operator(val value: Char) : PostfixToken
    }

    fun parse(raw: String): ParseResult {
        val source = raw.trim()
        if (source.isEmpty()) return ParseResult(error = ParseError(ErrorCode.EMPTY))
        return try {
            Parser(source).parse().let { root ->
                ParseResult(Parsed(root, root.references()))
            }
        } catch (failure: ParseFailure) {
            ParseResult(error = failure.error)
        }
    }

    fun isValid(raw: String): Boolean = parse(raw).isSuccess

    fun referencedNames(raw: String): Set<String> = parse(raw).expression?.references.orEmpty()

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data class Name(val value: String) : Token
        data object Plus : Token
        data object Minus : Token
        data object Multiply : Token
        data object Divide : Token
        data object LeftParen : Token
        data object RightParen : Token
        data object End : Token
    }

    private class Parser(private val source: String) {
        private val tokens = tokenize(source)
        private var index = 0

        fun parse(): Expr {
            val expression = parseAdditive()
            if (peek() !is Token.End) fail(ErrorCode.TRAILING_CHARACTERS)
            return expression
        }

        private fun parseAdditive(): Expr {
            var expression = parseMultiplicative()
            while (true) {
                expression = when (peek()) {
                    Token.Plus -> {
                        advance()
                        Binary('+', expression, parseMultiplicative())
                    }
                    Token.Minus -> {
                        advance()
                        Binary('-', expression, parseMultiplicative())
                    }
                    else -> return expression
                }
            }
        }

        private fun parseMultiplicative(): Expr {
            var expression = parseUnary()
            while (true) {
                expression = when (peek()) {
                    Token.Multiply -> {
                        advance()
                        Binary('*', expression, parseUnary())
                    }
                    Token.Divide -> {
                        advance()
                        Binary('/', expression, parseUnary())
                    }
                    else -> return expression
                }
            }
        }

        private fun parseUnary(): Expr = when (peek()) {
            Token.Plus -> {
                advance()
                parseUnary()
            }
            Token.Minus -> {
                advance()
                UnaryMinus(parseUnary())
            }
            else -> parsePrimary()
        }

        private fun parsePrimary(): Expr {
            return when (val token = advance()) {
                is Token.Number -> Literal(token.value)
                is Token.Name -> Name(token.value)
                Token.LeftParen -> parseAdditive().also {
                    if (advance() !is Token.RightParen) fail(ErrorCode.UNCLOSED_PARENTHESIS)
                }
                else -> fail(ErrorCode.OPERAND_REQUIRED)
            }
        }

        private fun peek(): Token = tokens[index]
        private fun advance(): Token = tokens[index++]
        private fun fail(code: ErrorCode): Nothing = NumericExpression.parseFailure(code)
    }

    private class ParseFailure(val error: ParseError) : IllegalArgumentException()

    private fun parseFailure(code: ErrorCode, token: String? = null): Nothing =
        throw ParseFailure(ParseError(code, token))

    internal sealed interface Expr {
        fun evaluate(resolve: (String) -> Double?): Double?
        fun references(): Set<String>
    }

    private fun Expr.appendPostfix(output: MutableList<PostfixToken>) {
        when (this) {
            is Literal -> output += PostfixToken.Literal(value)
            is Name -> output += PostfixToken.Reference(value)
            is UnaryMinus -> {
                operand.appendPostfix(output)
                output += PostfixToken.Operator('~')
            }
            is Binary -> {
                left.appendPostfix(output)
                right.appendPostfix(output)
                output += PostfixToken.Operator(operator)
            }
        }
    }

    private data class Literal(val value: Double) : Expr {
        override fun evaluate(resolve: (String) -> Double?): Double = value
        override fun references(): Set<String> = emptySet()
    }

    private data class Name(val value: String) : Expr {
        override fun evaluate(resolve: (String) -> Double?): Double? = resolve(value)
        override fun references(): Set<String> = setOf(value)
    }

    private data class UnaryMinus(val operand: Expr) : Expr {
        override fun evaluate(resolve: (String) -> Double?): Double? = operand.evaluate(resolve)?.let { -it }
        override fun references(): Set<String> = operand.references()
    }

    private data class Binary(val operator: Char, val left: Expr, val right: Expr) : Expr {
        override fun evaluate(resolve: (String) -> Double?): Double? {
            val lhs = left.evaluate(resolve) ?: return null
            val rhs = right.evaluate(resolve) ?: return null
            return when (operator) {
                '+' -> lhs + rhs
                '-' -> lhs - rhs
                '*' -> lhs * rhs
                '/' -> if (rhs == 0.0) null else lhs / rhs
                else -> null
            }?.takeIf(Double::isFinite)
        }

        override fun references(): Set<String> = left.references() + right.references()
    }

    private fun tokenize(source: String): List<Token> {
        val result = mutableListOf<Token>()
        var index = 0
        while (index < source.length) {
            when (val char = source[index]) {
                ' ', '\t', '\r', '\n' -> index++
                '+' -> {
                    result += Token.Plus
                    index++
                }
                '-' -> {
                    result += Token.Minus
                    index++
                }
                '*' -> {
                    result += Token.Multiply
                    index++
                }
                '/' -> {
                    result += Token.Divide
                    index++
                }
                '(' -> {
                    result += Token.LeftParen
                    index++
                }
                ')' -> {
                    result += Token.RightParen
                    index++
                }
                else -> {
                    val numberEnd = scanNumber(source, index)
                    if (numberEnd != null) {
                        val raw = source.substring(index, numberEnd)
                        val number = raw.toDoubleOrNull()?.takeIf(Double::isFinite)
                            ?: parseFailure(ErrorCode.INVALID_NUMBER, raw)
                        result += Token.Number(number)
                        index = numberEnd
                    } else {
                        val nameEnd = scanName(source, index)
                        if (nameEnd == null) parseFailure(ErrorCode.INVALID_CHARACTER, char.toString())
                        val name = source.substring(index, nameEnd)
                        if (name.startsWith('$') && name !in readOnlyNames) {
                            parseFailure(ErrorCode.INVALID_READONLY_NAME, name)
                        }
                        if (!name.startsWith('$') && !namePattern.matches(name)) {
                            parseFailure(ErrorCode.INVALID_VARIABLE_NAME, name)
                        }
                        result += Token.Name(name)
                        index = nameEnd
                    }
                }
            }
        }
        result += Token.End
        return result
    }

    private fun scanNumber(source: String, start: Int): Int? {
        val suffix = source.substring(start).takeIf {
            it.startsWith('.') || it.firstOrNull()?.isDigit() == true
        } ?: return null
        val match = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?").find(suffix)
            ?: return null
        return start + match.value.length
    }

    private fun scanName(source: String, start: Int): Int? {
        if (source[start] == '$') {
            val end = source.indexOfFirstFrom(start + 1) { !it.isLetterOrDigit() && it != '_' }
            return if (end == -1) source.length else end
        }
        if (!source[start].isLetter()) return null
        val end = source.indexOfFirstFrom(start + 1) { !it.isLetterOrDigit() && it != '_' && it != '.' && it != '-' }
        return if (end == -1) source.length else end
    }

    private fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
        for (index in start until length) if (predicate(this[index])) return index
        return -1
    }
}
