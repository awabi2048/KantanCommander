/**
 * DSLテキストを構文木（DslProgram）へ変換する再帰下降パーサーです。
 *
 * `src/main/kotlin/me/awabi2048/kantancommander/dsl/DslParser.kt` の
 * 1:1 移植です。型の妥当性は扱わず、構文と形状だけを検証します。
 */
import {
    DslArg,
    DslAssign,
    DslAssignOp,
    DslAssignTarget,
    DslBreak,
    DslCallDisk,
    DslCallResult,
    DslCallExpr,
    DslCallStmt,
    DslComponentExpr,
    DslConst,
    DslContinue,
    DslDecl,
    DslDelete,
    DslExpr,
    DslIf,
    DslNumberLiteral,
    DslPos,
    DslPositionComponent,
    DslProgram,
    DslRepeat,
    DslReturn,
    DslSpecialPosition,
    DslStmt,
    DslStringLiteral,
    DslStringPart,
    DslVarDecl,
    VALUE_TYPES,
    ValueTypeName,
} from "./ast";
import { DslParseError, DslToken, TokenKind, tokenize } from "./lexer";

export const GAMEMODE_CONSTANTS = new Set(["survival", "creative", "adventure", "spectator"]);

function key(pos: DslPos): string {
    return `${pos.line}:${pos.column}`;
}

class Parser {
    private readonly comments = new Map<number, string>();
    private readonly tokens: DslToken[];
    private index = 0;
    /** 最後に消費したトークンの行です。コメントの所属先を行番号で判定します。 */
    private lastLine = 0;
    /** 文の pos をキーとするコメント一覧です。 */
    private readonly stmtComments = new Map<string, string[]>();
    /** 次の文へ引き継ぐ、未所属のコメントです。 */
    private pending: string[] = [];

    constructor(text: string) {
        this.tokens = tokenize(text).filter((t) => {
            if (t.kind === TokenKind.COMMENT) {
                this.comments.set(t.pos.line, t.text);
                return false;
            }
            return true;
        });
    }

    private peek(): DslToken {
        return this.tokens[this.index];
    }

    private peekKind(offset = 0): TokenKind {
        return this.tokens[this.index + offset]?.kind ?? TokenKind.EOF;
    }

    private advance(): DslToken {
        const t = this.tokens[this.index++];
        this.lastLine = t.pos.line;
        return t;
    }

    private accept(kind: TokenKind): DslToken | null {
        return this.peek().kind === kind ? this.advance() : null;
    }

    private expect(kind: TokenKind, message: string): DslToken {
        return this.accept(kind) ?? this.fail(this.peek().pos, message);
    }

    private fail(pos: DslPos, message: string): never {
        throw new DslParseError(message, pos);
    }

    /** 文の終端 `;` です。1行化した入力も解釈できるよう必須とします。 */
    private expectSemicolon() {
        this.expect(TokenKind.SEMICOLON, "文の終わりに ';' を書いてください");
    }

    parse(): DslProgram {
        const head = this.expect(TokenKind.PROGRAM, "スクリプトは program で始めてください");
        const name = this.parsePlainString(
            "program の後に \"名前\" を書いてください",
            "program 名に補間は使えません",
        );
        this.expectSemicolon();
        // 宣言部（import／trigger／defarg）は本文より前にだけ書けます。
        const decls: DslDecl[] = [];
        while (true) {
            const k = this.peek().kind;
            if (k === TokenKind.IMPORT) decls.push(this.parseImport());
            else if (k === TokenKind.TRIGGER) decls.push(this.parseTrigger());
            else if (k === TokenKind.DEFARG) decls.push(this.parseDefarg());
            else break;
        }
        const body: DslStmt[] = [];
        while (this.peek().kind !== TokenKind.EOF) {
            body.push(this.parseStatement());
        }
        // 末尾に残ったコメント（本文の最後の文以降）は最後の文へ寄せて保持します。
        // 文が1つも無いプログラムではコメントの所属先が無いため保持しません。
        if (this.comments.size > 0 && body.length > 0) {
            const k = key(body[body.length - 1].pos);
            const list = this.stmtComments.get(k) ?? [];
            for (const v of [...this.comments.keys()].sort((a, b) => a - b)) {
                list.push(this.comments.get(v)!);
            }
            this.stmtComments.set(k, list);
            this.comments.clear();
        }
        return { kind: "program", name, decls, body, pos: head.pos, comments: this.stmtComments };
    }

    /**
     * 次のトークンの手前までにある未消費コメントを pending へ集めます。
     * 宣言部やファイル先頭のコメントは、最初の文の直前に現れるものとして
     * ここで一緒に拾われます。
     */
    private collectLeading() {
        const nextLine = this.peek().pos.line;
        for (const line of [...this.comments.keys()].sort((a, b) => a - b)) {
            if (line > this.lastLine && line < nextLine) {
                this.pending.push(this.comments.get(line)!);
                this.comments.delete(line);
            }
        }
    }

    /** `import world;` の宣言です。現状の宣言対象は world のみです。 */
    private parseImport(): DslDecl {
        const head = this.advance();
        const what = this.advance();
        if (what.kind !== TokenKind.WORLD) this.fail(what.pos, "import の対象は world です");
        this.expectSemicolon();
        return { kind: "importWorld", pos: head.pos };
    }

    /**
     * `trigger timer(interval = 20);`・`trigger redstone;`・`trigger event(...);`
     * です。裸識別子（redstone）は引数なし呼出しへ正規化します。
     */
    private parseTrigger(): DslDecl {
        const head = this.advance();
        const expr = this.parseIdentExpr();
        this.expectSemicolon();
        let spec: DslCallExpr;
        if (expr.kind === "callExpr") {
            spec = expr;
        } else if (expr.kind === "varRef") {
            spec = { kind: "callExpr", name: expr.name, args: [], pos: expr.pos };
        } else {
            return this.fail(expr.pos, "trigger には timer(...)・redstone・event(...) のいずれかを書いてください");
        }
        return { kind: "triggerDecl", spec, pos: head.pos };
    }

    /** `defarg name: type;` の引数宣言です。引数名は必須で、`defarg null:` は検証で拒否します。 */
    private parseDefarg(): DslDecl {
        const head = this.advance();
        const name = this.parseVarName("defarg の後に引数名を書いてください");
        this.expect(TokenKind.COLON, "引数名の後に ':' を書いてください");
        const type = this.dslType(this.expect(TokenKind.IDENT, "':' の後に型名を書いてください"));
        this.expectSemicolon();
        return { kind: "defarg", name, type, pos: head.pos };
    }

    /** `"..."` の素文字列を読みます。補間を含む場合はエラーにします。 */
    private parsePlainString(missingMessage: string, interpMessage: string): string {
        this.expect(TokenKind.STRING_START, missingMessage);
        let sb = "";
        while (true) {
            const k = this.peek().kind;
            if (k === TokenKind.STRING_TEXT) sb += this.advance().text;
            else if (k === TokenKind.INTERP_START) this.fail(this.peek().pos, interpMessage);
            else if (k === TokenKind.STRING_END) { this.advance(); return sb; }
            else this.fail(this.peek().pos, "文字列が正しく閉じられていません");
        }
    }

    /** ブロック内の文列を読み、終端トークンを消費して返します。 */
    private parseStatements(terminator: TokenKind): DslStmt[] {
        const stmts: DslStmt[] = [];
        while (this.peek().kind !== terminator) {
            if (this.peek().kind === TokenKind.EOF) this.fail(this.peek().pos, "ブロックが閉じられていません");
            stmts.push(this.parseStatement());
        }
        // ブロック末尾の未所属コメントは、次の文へ引き継いで保持します。
        this.collectLeading();
        this.advance();
        return stmts;
    }

    private parseBlock(): DslStmt[] {
        this.expect(TokenKind.LBRACE, "'{' を書いてください");
        return this.parseStatements(TokenKind.RBRACE);
    }

    /**
     * 文を読み、その文へ所属するコメントを解決します。
     * 文の前行にあるコメントは先行コメント、文と同じ行の末尾コメントは
     * その文のコメントとして保持します（印刷時はいずれも文の直前へ出ます）。
     */
    private parseStatement(): DslStmt {
        this.collectLeading();
        // この文の手前までのコメントを確定します。内側の文を読む再帰へ
        // 引き継がないよう、いったん pending から切り離します。
        const mine = this.pending;
        this.pending = [];
        const stmt = this.parseStatementBody();
        const trailing = this.comments.get(this.lastLine);
        if (trailing !== undefined) {
            this.comments.delete(this.lastLine);
            mine.push(trailing);
        }
        if (mine.length > 0) {
            const k = key(stmt.pos);
            const list = this.stmtComments.get(k) ?? [];
            this.stmtComments.set(k, list.concat(mine));
        }
        return stmt;
    }

    private parseStatementBody(): DslStmt {
        switch (this.peek().kind) {
            case TokenKind.VAR: return this.parseVarDecl();
            case TokenKind.IF: return this.parseIf();
            case TokenKind.REPEAT: return this.parseRepeat();
            case TokenKind.BREAK: {
                const t = this.advance();
                this.expectSemicolon();
                return { kind: "break", pos: t.pos } satisfies DslBreak;
            }
            case TokenKind.CONTINUE: {
                const t = this.advance();
                this.expectSemicolon();
                return { kind: "continue", pos: t.pos } satisfies DslContinue;
            }
            case TokenKind.RETURN: return this.parseReturn();
            case TokenKind.CALL: return this.parseCall();
            case TokenKind.DEL: return this.parseDelete();
            // `world.x = e`、`null = e`（未命名スロットへの書き込み）
            case TokenKind.WORLD:
            case TokenKind.NULL:
                return this.parseAssign(this.parseAssignTarget());
            case TokenKind.IDENT: return this.parseIdentStatement();
            case TokenKind.SEMICOLON: return this.fail(this.peek().pos, "文が必要です");
            default:
                return this.fail(this.peek().pos, `文を解釈できません（'${this.peek().text}'）`);
        }
    }

    /**
     * 変数名の位置を読みます。`null` は未命名（GUI の空名スロット）を表し、
     * 内部では空文字列として保持します。
     */
    private parseVarName(missingMessage: string): string {
        if (this.accept(TokenKind.NULL) !== null) return "";
        return this.expect(TokenKind.IDENT, missingMessage).text;
    }

    private parseVarDecl(): DslStmt {
        const head = this.advance();
        // `var world.x: T` はワールド変数の新規定義です。
        const world = this.accept(TokenKind.WORLD) !== null;
        if (world) this.expect(TokenKind.DOT, "var world の後に '.変数名' を書いてください");
        const name = this.parseVarName("var の後に変数名を書いてください");
        this.expect(TokenKind.COLON, "変数名の後に ':' を書いてください");
        const typeTok = this.expect(TokenKind.IDENT, "':' の後に型名を書いてください");
        const type = this.dslType(typeTok);
        this.expect(TokenKind.ASSIGN, "型名の後に '= 初期値' を書いてください（宣言のみは認めません）");
        const value = this.parseExpr();
        this.expectSemicolon();
        return { kind: "varDecl", name, type, value, world, pos: head.pos } satisfies DslVarDecl;
    }

    private dslType(token: DslToken): ValueTypeName {
        const lower = token.text.toLowerCase();
        if (!(VALUE_TYPES as readonly string[]).includes(lower)) {
            this.fail(token.pos, `不明な型名 '${token.text}' です（number/string/boolean/location/item/block/entity）`);
        }
        return lower as ValueTypeName;
    }

    /**
     * 代入・削除の対象を読みます。
     * `x`・`world.x`・`expr.vars.name`（エンティティ変数）のいずれかです。
     * 対象式として呼出し形（`target(...)`）も受理します。
     */
    private parseAssignTarget(): DslAssignTarget {
        const head = this.peek();
        switch (head.kind) {
            case TokenKind.WORLD: {
                this.advance();
                this.expect(TokenKind.DOT, "world の後に '.変数名' を書いてください");
                const name = this.parseVarName("world. の後に変数名を書いてください");
                return { kind: "worldVariable", name, pos: head.pos };
            }
            case TokenKind.NULL: {
                this.advance();
                return { kind: "variable", name: "", pos: head.pos };
            }
            case TokenKind.IDENT: {
                const primary = this.parseIdentExpr();
                return this.parseAssignTargetTail(primary, head.pos);
            }
            default:
                return this.fail(head.pos, "代入先は変数名・world.変数名・対象.vars.変数名のいずれかです");
        }
    }

    /** 先頭式の直後が `.vars.<name>` かを判定します（エンティティ変数アクセスの印）。 */
    private isEntityVarAccess(): boolean {
        return this.peek().kind === TokenKind.DOT &&
            this.peekKind(1) === TokenKind.IDENT &&
            this.tokens[this.index + 1]?.text === "vars" &&
            this.peekKind(2) === TokenKind.DOT;
    }

    private parseAssignTargetTail(primary: DslExpr, pos: DslPos): DslAssignTarget {
        if (this.isEntityVarAccess()) {
            this.advance(); // .
            this.advance(); // vars
            this.advance(); // .
            const name = this.parseVarName("'.vars.' の後に変数名を書いてください");
            return { kind: "entityVariable", entity: primary, name, pos };
        }
        if (primary.kind === "varRef") {
            return { kind: "variable", name: primary.name, pos: primary.pos };
        }
        return this.fail(primary.pos, "代入先は変数名・world.変数名・対象.vars.変数名のいずれかです");
    }

    /** `target op expr;` の演算子と値部分です。 */
    private parseAssign(target: DslAssignTarget): DslStmt {
        let op: DslAssignOp;
        switch (this.peek().kind) {
            case TokenKind.ASSIGN: op = "ASSIGN"; break;
            case TokenKind.PLUS_ASSIGN: op = "ADD"; break;
            case TokenKind.MINUS_ASSIGN: op = "SUB"; break;
            case TokenKind.STAR_ASSIGN: op = "MUL"; break;
            case TokenKind.SLASH_ASSIGN: op = "DIV"; break;
            case TokenKind.PERCENT_ASSIGN: op = "MOD"; break;
            default:
                return this.fail(this.peek().pos, "変数名の後に '=' '+=' '-=' '*=' '/=' '%=' のいずれかを書いてください");
        }
        this.advance();
        const value = this.parseExpr();
        this.expectSemicolon();
        return { kind: "assign", target, op, value, pos: target.pos } satisfies DslAssign;
    }

    /** `del <target>;` の削除文です。 */
    private parseDelete(): DslStmt {
        const head = this.advance();
        const target = this.parseAssignTarget();
        this.expectSemicolon();
        return { kind: "delete", target, pos: head.pos } satisfies DslDelete;
    }

    /**
     * 識別子で始まる文です。`ident(...)` の命令呼出し、`ident op expr` の代入、
     * `expr.vars.name op expr` のエンティティ変数代入のいずれかです。
     */
    private parseIdentStatement(): DslStmt {
        const head = this.peek();
        if (this.peekKind(1) === TokenKind.LPAREN) {
            const primary = this.parseIdentExpr();
            if (this.isEntityVarAccess()) return this.parseAssign(this.parseAssignTargetTail(primary, head.pos));
            if (primary.kind === "callExpr") {
                this.expectSemicolon();
                return { kind: "callStmt", name: primary.name, args: primary.args, pos: primary.pos } satisfies DslCallStmt;
            }
            return this.fail(this.peek().pos, `文を解釈できません（'${this.peek().text}'）`);
        }
        return this.parseAssign(this.parseAssignTarget());
    }

    private parseIf(): DslStmt {
        const head = this.advance();
        this.expect(TokenKind.LPAREN, "if の後に '(' を書いてください");
        const condition = this.parseExpr();
        this.expect(TokenKind.RPAREN, "条件の後に ')' を書いてください");
        const thenBody = this.parseBlock();
        let elseBody: DslStmt[] | null = null;
        if (this.accept(TokenKind.ELSE) !== null) {
            const k = this.peek().kind;
            if (k === TokenKind.IF) elseBody = [this.parseIf()];
            else if (k === TokenKind.LBRACE) elseBody = this.parseBlock();
            else this.fail(this.peek().pos, "else の後に 'if' または '{' を書いてください");
        }
        return { kind: "if", condition, thenBody, elseBody, pos: head.pos } satisfies DslIf;
    }

    private parseRepeat(): DslStmt {
        const head = this.advance();
        this.expect(TokenKind.LPAREN, "repeat の後に '(' を書いてください");
        const count = this.parseExpr();
        this.expect(TokenKind.RPAREN, "回数の後に ')' を書いてください");
        return { kind: "repeat", count, body: this.parseBlock(), pos: head.pos } satisfies DslRepeat;
    }

    private parseReturn(): DslStmt {
        const head = this.advance();
        if (this.peek().kind === TokenKind.SEMICOLON) {
            this.advance();
            return { kind: "return", value: null, pos: head.pos } satisfies DslReturn;
        }
        const value = this.parseExpr();
        this.expectSemicolon();
        return { kind: "return", value, pos: head.pos } satisfies DslReturn;
    }

    private parseCall(): DslStmt {
        const head = this.advance();
        const literal = this.parseStringLiteral();
        const args = this.peek().kind === TokenKind.LPAREN ? this.parseArgs() : [];
        this.expectSemicolon();
        return { kind: "callDisk", diskId: literal, args, pos: head.pos } satisfies DslCallDisk;
    }

    private parseArgs(): DslArg[] {
        this.expect(TokenKind.LPAREN, "'(' を書いてください");
        const args: DslArg[] = [];
        let seenNamed = false;
        while (this.peek().kind !== TokenKind.RPAREN) {
            const argPos = this.peek().pos;
            if (this.peek().kind === TokenKind.IDENT && this.peekKind(1) === TokenKind.ASSIGN) {
                seenNamed = true;
                const name = this.advance().text;
                this.advance();
                args.push({ name, value: this.parseExpr(), pos: argPos });
            } else {
                if (seenNamed) this.fail(argPos, "位置引数は名前付き引数の前にのみ書けます");
                args.push({ name: null, value: this.parseExpr(), pos: argPos });
            }
            if (this.accept(TokenKind.COMMA) === null) break;
        }
        this.expect(TokenKind.RPAREN, "')' を書いてください");
        return args;
    }

    // ---- 式 ----

    private parseExpr(): DslExpr {
        return this.parseOr();
    }

    private parseOr(): DslExpr {
        let expr = this.parseAnd();
        while (true) {
            const op = this.accept(TokenKind.OR);
            if (op === null) break;
            expr = { kind: "binary", op: "OR", left: expr, right: this.parseAnd(), pos: op.pos };
        }
        return expr;
    }

    private parseAnd(): DslExpr {
        let expr = this.parseEq();
        while (true) {
            const op = this.accept(TokenKind.AND);
            if (op === null) break;
            expr = { kind: "binary", op: "AND", left: expr, right: this.parseEq(), pos: op.pos };
        }
        return expr;
    }

    private parseEq(): DslExpr {
        let expr = this.parseRel();
        while (true) {
            const opTok = this.accept(TokenKind.EQ) ?? this.accept(TokenKind.NE);
            if (opTok === null) break;
            const op = opTok.kind === TokenKind.EQ ? "EQ" : "NE";
            expr = { kind: "binary", op, left: expr, right: this.parseRel(), pos: opTok.pos };
        }
        return expr;
    }

    private parseRel(): DslExpr {
        let expr = this.parseAdd();
        while (true) {
            let op: "LT" | "LE" | "GT" | "GE";
            switch (this.peek().kind) {
                case TokenKind.LT: op = "LT"; break;
                case TokenKind.LE: op = "LE"; break;
                case TokenKind.GT: op = "GT"; break;
                case TokenKind.GE: op = "GE"; break;
                default: return expr;
            }
            const at = this.advance().pos;
            expr = { kind: "binary", op, left: expr, right: this.parseAdd(), pos: at };
        }
    }

    private parseAdd(): DslExpr {
        let expr = this.parseMul();
        while (true) {
            let op: "ADD" | "SUB";
            switch (this.peek().kind) {
                case TokenKind.PLUS: op = "ADD"; break;
                case TokenKind.MINUS: op = "SUB"; break;
                default: return expr;
            }
            const at = this.advance().pos;
            expr = { kind: "binary", op, left: expr, right: this.parseMul(), pos: at };
        }
    }

    private parseMul(): DslExpr {
        let expr = this.parseUnary();
        while (true) {
            let op: "MUL" | "DIV" | "MOD";
            switch (this.peek().kind) {
                case TokenKind.STAR: op = "MUL"; break;
                case TokenKind.SLASH: op = "DIV"; break;
                case TokenKind.PERCENT: op = "MOD"; break;
                default: return expr;
            }
            const at = this.advance().pos;
            expr = { kind: "binary", op, left: expr, right: this.parseUnary(), pos: at };
        }
    }

    private parseUnary(): DslExpr {
        switch (this.peek().kind) {
            case TokenKind.NOT: {
                const t = this.advance();
                return { kind: "unary", op: "NOT", operand: this.parseUnary(), pos: t.pos };
            }
            case TokenKind.MINUS: {
                const t = this.advance();
                return { kind: "unary", op: "NEG", operand: this.parseUnary(), pos: t.pos };
            }
            default:
                return this.parsePostfix();
        }
    }

    /** プロパティ読出し（`.name` / `.name(arg)`）と基準付き位置リテラルの後置です。 */
    private parsePostfix(): DslExpr {
        let expr = this.parsePrimary();
        while (true) {
            const k = this.peek().kind;
            if (k === TokenKind.DOT) {
                const dot = this.advance();
                const name = this.expect(TokenKind.IDENT, "'.' の後に詳細値名を書いてください");
                let arg: DslExpr | null = null;
                if (this.accept(TokenKind.LPAREN) !== null) {
                    arg = this.parseExpr();
                    this.expect(TokenKind.RPAREN, "')' を書いてください");
                }
                // `loop.count` は実行コンテキスト供給のシステム変数であり、
                // `vars.<name>` はエンティティ変数の参照であり、
                // いずれも詳細値参照ではなく専用の式として読みます。
                if (expr.kind === "varRef" && expr.name === "loop" && name.text === "count" && arg === null) {
                    expr = { kind: "loopCount", pos: dot.pos };
                } else if (expr.kind === "varRef" && expr.name === "vars" && arg === null) {
                    expr = { kind: "entityVarRef", name: name.text, pos: dot.pos };
                } else {
                    expr = { kind: "propertyRead", source: expr, name: name.text, arg, pos: dot.pos };
                }
            } else if (k === TokenKind.TILDE || k === TokenKind.CARET) {
                expr = this.parsePositionOrComponent(expr);
            } else {
                return expr;
            }
        }
    }

    private parsePrimary(): DslExpr {
        switch (this.peek().kind) {
            case TokenKind.NUMBER: {
                const t = this.advance();
                const value = Number(t.text);
                if (!Number.isFinite(value)) this.fail(t.pos, `数値リテラル '${t.text}' を解釈できません`);
                return { kind: "number", value, pos: t.pos } satisfies DslNumberLiteral;
            }
            case TokenKind.STRING_START:
                return this.parseStringLiteral();
            case TokenKind.NULL: {
                const t = this.advance();
                return { kind: "null", pos: t.pos };
            }
            case TokenKind.TRUE: {
                const t = this.advance();
                return { kind: "boolean", value: true, pos: t.pos };
            }
            case TokenKind.FALSE: {
                const t = this.advance();
                return { kind: "boolean", value: false, pos: t.pos };
            }
            case TokenKind.WORLD: {
                const t = this.advance();
                this.expect(TokenKind.DOT, "world の後に '.変数名' を書いてください");
                const name = this.parseVarName("world. の後に変数名を書いてください");
                return { kind: "worldRef", name, pos: t.pos };
            }
            // `call "x"(...)` を式位置で読みます。戻り値を受け取る代入の値として
            // 構文的には式ですが、使用位置は代入・変数宣言の右辺のみです。
            case TokenKind.CALL: {
                const t = this.advance();
                const literal = this.parseStringLiteral();
                const args = this.peek().kind === TokenKind.LPAREN ? this.parseArgs() : [];
                return { kind: "callResult", diskId: literal, args, pos: t.pos } satisfies DslCallResult;
            }
            case TokenKind.IDENT:
                return this.parseIdentExpr();
            case TokenKind.LPAREN: {
                this.advance();
                const inner = this.parseExpr();
                this.expect(TokenKind.RPAREN, "')' を書いてください");
                return inner;
            }
            case TokenKind.TILDE:
            case TokenKind.CARET:
                return this.parsePositionOrComponent(null);
            default:
                return this.fail(this.peek().pos, `式を解釈できません（'${this.peek().text}'）`);
        }
    }

    private parseIdentExpr(): DslExpr {
        const name = this.advance();
        // `gamemode.<定数>` は名前空間付き定数として読み、変数参照と区別します。
        if (name.text === "gamemode" && this.peek().kind === TokenKind.DOT) {
            this.advance();
            const member = this.expect(TokenKind.IDENT, "gamemode の後に定数名を書いてください");
            if (!GAMEMODE_CONSTANTS.has(member.text)) {
                this.fail(member.pos, `gamemode の定数名が不明です（${[...GAMEMODE_CONSTANTS].join("/")}）`);
            }
            return { kind: "const", group: "gamemode", name: member.text, pos: name.pos } satisfies DslConst;
        }
        if (this.peek().kind === TokenKind.LPAREN) {
            return { kind: "callExpr", name: name.text, args: this.parseArgs(), pos: name.pos } satisfies DslCallExpr;
        }
        // 実行コンテキストが供給する予約位置式です（制御ブロック位置・捕捉位置）。
        if (name.text === "disk" || name.text === "captured") {
            return { kind: "specialPosition", name: name.text, pos: name.pos } satisfies DslSpecialPosition;
        }
        return { kind: "varRef", name: name.text, pos: name.pos };
    }

    private parseStringLiteral(): DslStringLiteral {
        const start = this.expect(TokenKind.STRING_START, "文字列は '\"' で始めてください");
        const parts: DslStringPart[] = [];
        while (true) {
            const k = this.peek().kind;
            if (k === TokenKind.STRING_TEXT) {
                parts.push({ kind: "text", text: this.advance().text });
            } else if (k === TokenKind.INTERP_START) {
                this.advance();
                parts.push({ kind: "interp", expr: this.parseExpr() });
                this.expect(TokenKind.INTERP_END, "補間の '}' を書いてください");
            } else if (k === TokenKind.STRING_END) {
                this.advance();
                return { kind: "string", parts, pos: start.pos };
            } else {
                this.fail(this.peek().pos, "文字列が正しく閉じられていません");
            }
        }
    }

    /**
     * `~`/`^` で始まる式を読みます。後続が成分（`~x`・`^x`・`数値`・`-数値`）なら
     * 3成分の位置リテラル、そうでなければ単一成分（`~12`・`^0`）です。
     * 単一成分は `location(yaw = ~90)` のような引数値でのみ意味を持ちます。
     * `base` がある場合は必ず3成分の位置リテラルとして読みます。
     */
    private parsePositionOrComponent(base: DslExpr | null): DslExpr {
        const start = this.peek().pos;
        const first = this.parsePositionComponent(1);
        const k = this.peek().kind;
        if (k === TokenKind.TILDE || k === TokenKind.CARET || k === TokenKind.NUMBER || k === TokenKind.MINUS) {
            const second = this.parsePositionComponent(2);
            const third = this.parsePositionComponent(3);
            return {
                kind: "positionLiteral",
                base,
                components: [first, second, third],
                pos: base?.pos ?? start,
            };
        }
        if (base !== null) {
            this.fail(this.peek().pos, "位置リテラルは3成分必要です");
        }
        return {
            kind: "componentExpr",
            semantics: first.semantics,
            value: first.value,
            pos: start,
        } satisfies DslComponentExpr;
    }

    private parsePositionComponent(ordinal: number): DslPositionComponent {
        let semantics: "ABSOLUTE" | "RELATIVE" | "LOCAL";
        const k = this.peek().kind;
        if (k === TokenKind.TILDE) { this.advance(); semantics = "RELATIVE"; }
        else if (k === TokenKind.CARET) { this.advance(); semantics = "LOCAL"; }
        else semantics = "ABSOLUTE";
        if (semantics !== "ABSOLUTE" &&
            this.peek().kind !== TokenKind.NUMBER && this.peek().kind !== TokenKind.MINUS) {
            // `~` / `^` 単独は 0 の成分です。
            return { semantics, value: 0 };
        }
        const negative = this.accept(TokenKind.MINUS) !== null;
        const num = this.expect(TokenKind.NUMBER, `位置リテラルの ${ordinal} 成分目の数値を書いてください`);
        const value = Number(num.text);
        if (!Number.isFinite(value)) this.fail(num.pos, `位置成分 '${num.text}' を解釈できません`);
        return { semantics, value: negative ? -value : value };
    }
}

export function parse(text: string): DslProgram {
    return new Parser(text).parse();
}
