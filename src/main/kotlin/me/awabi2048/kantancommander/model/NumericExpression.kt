package me.awabi2048.kantancommander.model

import kotlin.math.pow

/**
 * VARIABLEの数値計算で共有する小さな式言語です。
 *
 * GUI・実行・バニラ出力がそれぞれ独自にsplitや優先順位処理を持つと、同じ式が
 * 保存時と実行時で別の値になるため、字句解析と構文解析をモデル層へ集約します。
 * 許可する構文は数値リテラル、`${variable_name}` 形式のワールド内変数参照、
 * `%{variable_name}%` 形式の一時変数参照、四則演算、べき乗（`^`・右結合）、
 * 6種の三角関数（`sin`／`cos`／`tan`／`asin`／`acos`／`atan`）、括弧です。
 * システム変数は `${CURRENT_LOOP_COUNT}` のように大文字で記述します。
 *
 * 数値リテラル自体の受理は字句・構文層の責務であり、CALCULATE（計算して保存）の
 * 「変数同士に限定」する契約は検証層（`ExecutableScriptValidator`）と入力画面が
 * [hasLiteralOperand] で判定します。単純な数値代入は ASSIGN、乱数は RANDOM を
 * 使うため、CALCULATE の式にリテラルは現れません。
 *
 * 三角関数の角度単位は度数法です。Minecraft の yaw／pitch と同じ単位に揃え、
 * 利用者がラジアン換算を意識しないようにします。逆三角関数の戻り値も度です。
 */
object NumericExpression {
    /** 対応する三角関数名です。小文字で記述します。 */
    val TRIGONOMETRIC_FUNCTIONS: Set<String> = setOf("sin", "cos", "tan", "asin", "acos", "atan")
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

    class Parsed internal constructor(
        private val root: Expr,
        val references: Set<String>,
        val temporaryReferences: Set<String> = emptySet(),
    ) {
        fun evaluate(resolve: (String) -> Double?): Double? =
            evaluate(resolve, { null })

        /** ワールド内変数と一時変数を区別して評価します。 */
        fun evaluate(worldResolve: (String) -> Double?, tempResolve: (String) -> Double?): Double? =
            root.evaluate { name, temporary ->
                if (temporary) tempResolve(name) else worldResolve(name)
            }?.takeIf(Double::isFinite)

        /** Vanilla exporterがscoreboard演算へ写像できるよう、式を後置記法で公開します。 */
        internal fun postfix(): List<PostfixToken> = buildList { root.appendPostfix(this) }

        /**
         * 数値リテラルを被演算子に含むかを返します。
         *
         * issue #3 の「計算に使用できる値は変数同士に限定」する契約の判定です。
         * 単項マイナスで修飾されたリテラル（`-2` 等）もリテラルとして扱い、
         * 変数参照だけの式（`-${a}` を含む）は含まないと判定します。
         */
        fun hasLiteralOperand(): Boolean = postfix().any { it is PostfixToken.Literal }

        /**
         * 後置記法がバニラscoreboardへ写像可能かを返します。
         *
         * 四則演算と単項マイナスだけが対象であり、べき乗・三角関数はバニラに
         * 同等概念がないため出力側で明示エラーにします。
         */
        internal fun isVanillaLowerable(): Boolean {
            val tokens = runCatching { postfix() }.getOrNull() ?: return false
            return tokens.none { it is PostfixToken.Function || (it is PostfixToken.Operator && it.value == '^') }
        }
    }

    internal sealed interface PostfixToken {
        data class Literal(val value: Double) : PostfixToken
        data class Reference(val name: String, val temporary: Boolean = false) : PostfixToken
        data class Operator(val value: Char) : PostfixToken
        data class Function(val name: String) : PostfixToken
    }

    fun parse(raw: String): ParseResult {
        val source = raw.trim()
        if (source.isEmpty()) return ParseResult(error = ParseError(ErrorCode.EMPTY))
        return try {
            Parser(source).parse().let { root ->
                ParseResult(Parsed(root, root.references(), root.temporaryReferences()))
            }
        } catch (failure: ParseFailure) {
            ParseResult(error = failure.error)
        }
    }

    fun isValid(raw: String): Boolean = parse(raw).isSuccess

    fun referencedNames(raw: String): Set<String> = parse(raw).expression?.references.orEmpty()

    /** 一時変数 `%{name}%` の参照名だけを返します。 */
    fun referencedTemporaryNames(raw: String): Set<String> =
        parse(raw).expression?.temporaryReferences.orEmpty()

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data class Name(val value: String, val temporary: Boolean = false) : Token
        data class Function(val name: String) : Token
        data object Plus : Token
        data object Minus : Token
        data object Multiply : Token
        data object Divide : Token
        data object Power : Token
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

        /**
         * べき乗を右結合で解析します。
         *
         * 底は単項演算を含まない一次式だけを受け、`-2^2` は `-(2^2)` と読みます。
         * 指数側は単項演算を許可するため `2^-3` も書けます。`2^3^2` は
         * `2^(3^2)` になります。
         */
        private fun parsePower(): Expr {
            val base = parsePrimary()
            return if (peek() == Token.Power) {
                advance()
                Binary('^', base, parseUnary())
            } else base
        }

        private fun parseUnary(): Expr {
            val head = peek()
            return when (head) {
                Token.Plus -> {
                    advance()
                    parseUnary()
                }
                Token.Minus -> {
                    advance()
                    UnaryMinus(parseUnary())
                }
                else -> parsePower()
            }
        }

        private fun parsePrimary(): Expr {
            return when (val token = advance()) {
                is Token.Number -> Literal(token.value)
                is Token.Name -> Name(token.value, token.temporary)
                is Token.Function -> {
                    if (advance() !is Token.LeftParen) fail(ErrorCode.OPERAND_REQUIRED)
                    FunctionCall(token.name, parseAdditive()).also {
                        if (advance() !is Token.RightParen) fail(ErrorCode.UNCLOSED_PARENTHESIS)
                    }
                }
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
        fun evaluate(resolve: (String) -> Double?): Double? =
            evaluate { name, _ -> resolve(name) }

        fun evaluate(resolve: (String, Boolean) -> Double?): Double?
        fun references(): Set<String>
        fun temporaryReferences(): Set<String> = emptySet()
    }

    private fun Expr.appendPostfix(output: MutableList<PostfixToken>) {
        when (this) {
            is Literal -> output += PostfixToken.Literal(value)
            is Name -> output += PostfixToken.Reference(value, temporary)
            is UnaryMinus -> {
                operand.appendPostfix(output)
                output += PostfixToken.Operator('~')
            }
            is FunctionCall -> {
                argument.appendPostfix(output)
                output += PostfixToken.Function(name)
            }
            is Binary -> {
                left.appendPostfix(output)
                right.appendPostfix(output)
                output += PostfixToken.Operator(operator)
            }
        }
    }

    private data class Literal(val value: Double) : Expr {
        override fun evaluate(resolve: (String, Boolean) -> Double?): Double = value
        override fun references(): Set<String> = emptySet()
    }

    private data class Name(val value: String, val temporary: Boolean = false) : Expr {
        override fun evaluate(resolve: (String, Boolean) -> Double?): Double? = resolve(value, temporary)
        override fun references(): Set<String> = if (temporary) emptySet() else setOf(value)
        override fun temporaryReferences(): Set<String> = if (temporary) setOf(value) else emptySet()
    }

    private data class UnaryMinus(val operand: Expr) : Expr {
        override fun evaluate(resolve: (String, Boolean) -> Double?): Double? = operand.evaluate(resolve)?.let { -it }
        override fun references(): Set<String> = operand.references()
        override fun temporaryReferences(): Set<String> = operand.temporaryReferences()
    }

    /**
     * 三角関数呼び出しです。角度単位は度数法に固定します。
     *
     * `sin`／`cos`／`tan` は度をラジアンへ変換して評価し、`asin`／`acos`／
     * `atan` の戻り値は度で返します。定義域外（例: `asin(2)`）や非有限結果は
     * null（解決失敗）として扱い、ゼロ除算と同じ契約にします。
     */
    private data class FunctionCall(val name: String, val argument: Expr) : Expr {
        override fun evaluate(resolve: (String, Boolean) -> Double?): Double? {
            val value = argument.evaluate(resolve) ?: return null
            if (!value.isFinite()) return null
            return when (name) {
                "sin" -> kotlin.math.sin(Math.toRadians(value))
                "cos" -> kotlin.math.cos(Math.toRadians(value))
                "tan" -> kotlin.math.tan(Math.toRadians(value))
                "asin" -> Math.toDegrees(kotlin.math.asin(value))
                "acos" -> Math.toDegrees(kotlin.math.acos(value))
                "atan" -> Math.toDegrees(kotlin.math.atan(value))
                else -> null
            }?.takeIf(Double::isFinite)
        }

        override fun references(): Set<String> = argument.references()

        override fun temporaryReferences(): Set<String> = argument.temporaryReferences()
    }

    private data class Binary(val operator: Char, val left: Expr, val right: Expr) : Expr {
        override fun evaluate(resolve: (String, Boolean) -> Double?): Double? {
            val lhs = left.evaluate(resolve) ?: return null
            val rhs = right.evaluate(resolve) ?: return null
            return when (operator) {
                '+' -> lhs + rhs
                '-' -> lhs - rhs
                '*' -> lhs * rhs
                '/' -> if (rhs == 0.0) null else lhs / rhs
                '^' -> lhs.pow(rhs)
                else -> null
            }?.takeIf(Double::isFinite)
        }

        override fun references(): Set<String> = left.references() + right.references()

        override fun temporaryReferences(): Set<String> =
            left.temporaryReferences() + right.temporaryReferences()
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
                '^' -> {
                    result += Token.Power
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
                            val functionEnd = scanFunctionName(source, index)
                            if (functionEnd != null) {
                                val name = source.substring(index, functionEnd).lowercase()
                                result += Token.Function(name)
                                index = functionEnd
                                continue
                            }
                            val nameEnd = scanName(source, index)
                            val tempEnd = if (nameEnd == null) scanTemporaryName(source, index) else null
                            if (nameEnd == null && tempEnd == null) {
                                parseFailure(ErrorCode.INVALID_CHARACTER, char.toString())
                            }
                            if (nameEnd != null) {
                                val raw = source.substring(index, nameEnd)
                                val name = raw.removePrefix("${'$'}{").removeSuffix("}")
                                if (!SystemVariableNames.isReferenceName(name)) {
                                    parseFailure(ErrorCode.INVALID_VARIABLE_NAME, name)
                                }
                                result += Token.Name(name, temporary = false)
                                index = nameEnd
                            } else {
                                val end = requireNotNull(tempEnd)
                                val raw = source.substring(index, end)
                                val name = raw.removePrefix("%{").removeSuffix("}%")
                                if (!SystemVariableNames.isReferenceName(name)) {
                                    parseFailure(ErrorCode.INVALID_VARIABLE_NAME, name)
                                }
                                result += Token.Name(name, temporary = true)
                                index = end
                            }
                        }
                    }
            }
        }
        result += Token.End
        return result
    }

    /**
     * 三角関数名の終端位置を返します。
     *
     * 英字の連続が `(` に続く場合だけ関数として扱い、対応表にない名前は
     * 関数にしません（後段で文字不正として失敗します）。`${...}`／`%{...}%`
     * 参照と衝突しないよう、`$`／`%` 開始はここでは扱いません。
     */
    private fun scanFunctionName(source: String, start: Int): Int? {
        if (!source[start].isLetter()) return null
        var end = start
        while (end < source.length && source[end].isLetter()) end++
        if (end == start) return null
        val name = source.substring(start, end).lowercase()
        if (name !in TRIGONOMETRIC_FUNCTIONS) return null
        if (source.getOrNull(end) != '(') return null
        return end
    }

    private fun scanNumber(source: String, start: Int): Int? {        val suffix = source.substring(start).takeIf {
            it.startsWith('.') || it.firstOrNull()?.isDigit() == true
        } ?: return null
        val match = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?").find(suffix)
            ?: return null
        return start + match.value.length
    }

    private fun scanName(source: String, start: Int): Int? {
        if (source[start] != '$' || source.getOrNull(start + 1) != '{') return null
        val close = source.indexOf('}', start + 2)
        if (close == -1) parseFailure(ErrorCode.INVALID_VARIABLE_NAME, source.substring(start))
        return close + 1
    }

    /**
     * 一時変数 `%{name}%` の終端位置を返します。
     *
     * `%` 演算子は式言語に存在しないため、`%` 開始は常に参照として扱います。
     * 閉じ `%` がなければ変数名不正として失敗させます。
     */
    private fun scanTemporaryName(source: String, start: Int): Int? {
        if (source[start] != '%') return null
        if (source.getOrNull(start + 1) != '{') return null
        val close = source.indexOf("}%", start + 2)
        if (close == -1) parseFailure(ErrorCode.INVALID_VARIABLE_NAME, source.substring(start))
        return close + 2
    }
}
