/**
 * DSL の字句解析です。
 *
 * `src/main/kotlin/me/awabi2048/kantancommander/dsl/DslLexer.kt` の
 * 1:1 移植です。文字列リテラルは STRING_START … STRING_END の
 * トークン列として出し、`${` 補間は通常トークンとして流します。
 * `#` と `//` の行コメントは COMMENT トークンとして出します。
 */
import { DslPos, posToString } from "./ast";

/** DSL の字句・構文解析の失敗です。 */
export class DslParseError extends Error {
    constructor(
        message: string,
        readonly pos: DslPos,
    ) {
        super(`${posToString(pos)}: ${message}`);
        this.name = "DslParseError";
    }
}

export enum TokenKind {
    IDENT, NUMBER,
    STRING_START, STRING_TEXT, STRING_END, INTERP_START, INTERP_END,
    PROGRAM, VAR, IF, ELSE, REPEAT, BREAK, CONTINUE, RETURN, CALL, WORLD, TRUE, FALSE, DEL,
    IMPORT, TRIGGER, DEFARG, NULL,
    LBRACE, RBRACE, LPAREN, RPAREN, COMMA, SEMICOLON, COLON, DOT,
    ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN, PERCENT_ASSIGN,
    EQ, NE, LT, LE, GT, GE, AND, OR, NOT,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    TILDE, CARET, COMMENT, EOF,
}

export interface DslToken {
    kind: TokenKind;
    text: string;
    pos: DslPos;
}

const KEYWORDS = new Map<string, TokenKind>([
    ["program", TokenKind.PROGRAM],
    ["var", TokenKind.VAR],
    ["if", TokenKind.IF],
    ["else", TokenKind.ELSE],
    ["repeat", TokenKind.REPEAT],
    ["break", TokenKind.BREAK],
    ["continue", TokenKind.CONTINUE],
    ["return", TokenKind.RETURN],
    ["call", TokenKind.CALL],
    ["world", TokenKind.WORLD],
    ["del", TokenKind.DEL],
    ["import", TokenKind.IMPORT],
    ["trigger", TokenKind.TRIGGER],
    ["defarg", TokenKind.DEFARG],
    ["null", TokenKind.NULL],
    ["true", TokenKind.TRUE],
    ["false", TokenKind.FALSE],
]);

function isLetter(c: string): boolean {
    return /\p{L}/u.test(c);
}

function isLetterOrDigit(c: string): boolean {
    return /[\p{L}\p{N}]/u.test(c);
}

function isDigit(c: string): boolean {
    return c >= "0" && c <= "9";
}

export function tokenize(text: string): DslToken[] {
    let index = 0;
    let line = 1;
    let column = 1;
    const tokens: DslToken[] = [];
    let inString = false;
    let stringStartPos: DslPos | null = null;
    const interpBraceDepth: number[] = [];

    function pos(): DslPos {
        return { line, column };
    }

    function peekChar(offset = 0): string | undefined {
        return text[index + offset];
    }

    function advance() {
        if (index < text.length && text[index] === "\n") {
            line++;
            column = 1;
        } else {
            column++;
        }
        index++;
    }

    function emit(kind: TokenKind, value: string, at: DslPos = pos()) {
        tokens.push({ kind, text: value, pos: at });
    }

    function error(at: DslPos, message: string): never {
        throw new DslParseError(message, at);
    }

    function lexComment() {
        const at = pos();
        advance();
        if (text[index - 1] === "/") advance();
        let sb = "";
        while (index < text.length && text[index] !== "\n") {
            sb += text[index];
            advance();
        }
        emit(TokenKind.COMMENT, sb.trim(), at);
    }

    function lexNumber() {
        const start = pos();
        let sb = "";
        while (peekChar() !== undefined && isDigit(peekChar()!)) {
            sb += text[index];
            advance();
        }
        if (peekChar() === "." && peekChar(1) !== undefined && isDigit(peekChar(1)!)) {
            sb += text[index];
            advance();
            while (peekChar() !== undefined && isDigit(peekChar()!)) {
                sb += text[index];
                advance();
            }
        }
        emit(TokenKind.NUMBER, sb, start);
    }

    function lexIdent() {
        const start = pos();
        let sb = "";
        while (peekChar() !== undefined && (isLetterOrDigit(peekChar()!) || peekChar() === "_")) {
            sb += text[index];
            advance();
        }
        emit(KEYWORDS.get(sb) ?? TokenKind.IDENT, sb, start);
    }

    function lexSymbol() {
        const at = pos();
        const c = text[index];
        function two(kind2: TokenKind, kind1: TokenKind, second: string) {
            if (peekChar(1) === second) {
                advance();
                advance();
                emit(kind2, c + second, at);
            } else {
                advance();
                emit(kind1, c, at);
            }
        }
        switch (c) {
            case "{":
                if (interpBraceDepth.length > 0) {
                    interpBraceDepth[interpBraceDepth.length - 1]++;
                }
                advance();
                emit(TokenKind.LBRACE, "{", at);
                break;
            case "}": {
                advance();
                if (interpBraceDepth.length > 0 && interpBraceDepth[interpBraceDepth.length - 1] === 0) {
                    // 補間式の終端です。`}` はトークンにせず文字列スキャンへ戻ります。
                    interpBraceDepth.pop();
                    emit(TokenKind.INTERP_END, "}", at);
                    inString = true;
                } else {
                    if (interpBraceDepth.length > 0) {
                        interpBraceDepth[interpBraceDepth.length - 1]--;
                    }
                    emit(TokenKind.RBRACE, "}", at);
                }
                break;
            }
            case "(": advance(); emit(TokenKind.LPAREN, "(", at); break;
            case ")": advance(); emit(TokenKind.RPAREN, ")", at); break;
            case ",": advance(); emit(TokenKind.COMMA, ",", at); break;
            case ";": advance(); emit(TokenKind.SEMICOLON, ";", at); break;
            case ":": advance(); emit(TokenKind.COLON, ":", at); break;
            case ".": advance(); emit(TokenKind.DOT, ".", at); break;
            case "~": advance(); emit(TokenKind.TILDE, "~", at); break;
            case "^": advance(); emit(TokenKind.CARET, "^", at); break;
            case "=": two(TokenKind.EQ, TokenKind.ASSIGN, "="); break;
            case "!": two(TokenKind.NE, TokenKind.NOT, "="); break;
            case "<": two(TokenKind.LE, TokenKind.LT, "="); break;
            case ">": two(TokenKind.GE, TokenKind.GT, "="); break;
            case "&":
                if (peekChar(1) === "&") { advance(); advance(); emit(TokenKind.AND, "&&", at); }
                else error(at, "'&' は '&&' として書いてください");
                break;
            case "|":
                if (peekChar(1) === "|") { advance(); advance(); emit(TokenKind.OR, "||", at); }
                else error(at, "'|' は '||' として書いてください");
                break;
            case "+": two(TokenKind.PLUS_ASSIGN, TokenKind.PLUS, "="); break;
            case "-": two(TokenKind.MINUS_ASSIGN, TokenKind.MINUS, "="); break;
            case "*": two(TokenKind.STAR_ASSIGN, TokenKind.STAR, "="); break;
            case "/": two(TokenKind.SLASH_ASSIGN, TokenKind.SLASH, "="); break;
            case "%": two(TokenKind.PERCENT_ASSIGN, TokenKind.PERCENT, "="); break;
            default:
                error(at, `解釈できない文字 '${c}' です`);
        }
    }

    /**
     * 文字列リテラルの中身をスキャンします。`"`・`${`・`\` 以外はそのまま蓄積し、
     * `${` では INTERP_START を出して通常字句へ戻ります。
     */
    function continueString() {
        const textStart = pos();
        let sb = "";
        function flush() {
            if (sb.length > 0) emit(TokenKind.STRING_TEXT, sb, textStart);
        }
        while (index < text.length) {
            const c = text[index];
            if (c === "\"") {
                flush();
                const at = pos();
                advance();
                emit(TokenKind.STRING_END, "\"", at);
                inString = false;
                return;
            } else if (c === "$" && peekChar(1) === "{") {
                flush();
                const at = pos();
                advance();
                advance();
                emit(TokenKind.INTERP_START, "${", at);
                interpBraceDepth.push(0);
                inString = false;
                return;
            } else if (c === "\\") {
                advance();
                const e = peekChar();
                if (e === "\"") { sb += "\""; advance(); }
                else if (e === "\\") { sb += "\\"; advance(); }
                else if (e === "n") { sb += "\n"; advance(); }
                else {
                    error(pos(), `使用できないエスケープ '\\${e ?? ""}' です（\\" \\\\ \\n のみ）`);
                }
            } else {
                sb += c;
                advance();
            }
        }
        // 末尾まで来ても閉じられなかった場合は呼出し側がエラーにします。
    }

    while (true) {
        if (inString) {
            if (index >= text.length) {
                error(stringStartPos ?? pos(), "文字列が閉じられていません");
            }
            continueString();
        } else {
            if (index >= text.length) break;
            const c = text[index];
            if (c === " " || c === "\t" || c === "\r" || c === "\n") {
                advance();
            } else if (c === "#") {
                lexComment();
            } else if (c === "/") {
                if (peekChar(1) === "/") lexComment();
                else lexSymbol();
            } else if (c === "\"") {
                stringStartPos = pos();
                emit(TokenKind.STRING_START, "\"");
                inString = true;
                advance();
            } else if (isDigit(c)) {
                lexNumber();
            } else if (isLetter(c) || c === "_") {
                lexIdent();
            } else {
                lexSymbol();
            }
        }
    }
    emit(TokenKind.EOF, "");
    return tokens;
}
