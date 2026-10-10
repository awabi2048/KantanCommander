// DslLexer 移植の回帰テストです。
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { tokenize, TokenKind, DslParseError } = require("../out/lexer");

const kinds = (text) => tokenize(text).map((t) => t.kind);

test("キーワードを識別する", () => {
    assert.deepEqual(kinds("program import world trigger defarg var if else repeat break continue return call del null true false"), [
        TokenKind.PROGRAM, TokenKind.IMPORT, TokenKind.WORLD, TokenKind.TRIGGER, TokenKind.DEFARG,
        TokenKind.VAR, TokenKind.IF, TokenKind.ELSE, TokenKind.REPEAT, TokenKind.BREAK,
        TokenKind.CONTINUE, TokenKind.RETURN, TokenKind.CALL, TokenKind.DEL,
        TokenKind.NULL, TokenKind.TRUE, TokenKind.FALSE, TokenKind.EOF,
    ]);
});

test("識別子・数値を読む", () => {
    const tokens = tokenize("my_var 123 4.5 -7");
    assert.equal(tokens[0].kind, TokenKind.IDENT);
    assert.equal(tokens[0].text, "my_var");
    assert.equal(tokens[1].kind, TokenKind.NUMBER);
    assert.equal(tokens[1].text, "123");
    assert.equal(tokens[2].text, "4.5");
    assert.equal(tokens[3].kind, TokenKind.MINUS);
});

test("演算子を区別する", () => {
    assert.deepEqual(kinds("= == ! != < <= > >= + - * / % && || += -= *= /= %= ~ ^ . , ; : ( ) { }"), [
        TokenKind.ASSIGN, TokenKind.EQ, TokenKind.NOT, TokenKind.NE,
        TokenKind.LT, TokenKind.LE, TokenKind.GT, TokenKind.GE,
        TokenKind.PLUS, TokenKind.MINUS, TokenKind.STAR, TokenKind.SLASH, TokenKind.PERCENT,
        TokenKind.AND, TokenKind.OR,
        TokenKind.PLUS_ASSIGN, TokenKind.MINUS_ASSIGN, TokenKind.STAR_ASSIGN,
        TokenKind.SLASH_ASSIGN, TokenKind.PERCENT_ASSIGN,
        TokenKind.TILDE, TokenKind.CARET, TokenKind.DOT, TokenKind.COMMA,
        TokenKind.SEMICOLON, TokenKind.COLON,
        TokenKind.LPAREN, TokenKind.RPAREN, TokenKind.LBRACE, TokenKind.RBRACE,
        TokenKind.EOF,
    ]);
});

test("文字列とエスケープを読む", () => {
    const tokens = tokenize('"hello\\n\\"world\\""');
    assert.deepEqual(tokens.map((t) => t.kind), [
        TokenKind.STRING_START, TokenKind.STRING_TEXT, TokenKind.STRING_END, TokenKind.EOF,
    ]);
    assert.equal(tokens[1].text, "hello\n\"world\"");
});

test("文字列補間を読む", () => {
    const tokens = tokenize('"a${x + 1}b"');
    assert.deepEqual(tokens.map((t) => t.kind), [
        TokenKind.STRING_START, TokenKind.STRING_TEXT,
        TokenKind.INTERP_START,
        TokenKind.IDENT, TokenKind.PLUS, TokenKind.NUMBER,
        TokenKind.INTERP_END,
        TokenKind.STRING_TEXT, TokenKind.STRING_END, TokenKind.EOF,
    ]);
});

test("行コメントを収集する", () => {
    for (const text of ["x = 1; // note\ny = 2;", "x = 1; # note\ny = 2;"]) {
        const tokens = tokenize(text);
        const comments = tokens.filter((t) => t.kind === TokenKind.COMMENT);
        assert.equal(comments.length, 1);
        assert.equal(comments[0].text, "note");
        assert.equal(comments[0].pos.line, 1);
    }
});

test("閉じない文字列はエラー", () => {
    assert.throws(() => tokenize('"unterminated'), DslParseError);
});

test("不明な文字はエラー", () => {
    assert.throws(() => tokenize("x @ y"), DslParseError);
});

test("位置情報を保持する", () => {
    const tokens = tokenize("a\n  b");
    assert.deepEqual(tokens[0].pos, { line: 1, column: 1 });
    assert.deepEqual(tokens[1].pos, { line: 2, column: 3 });
});
