/**
 * DSL 構文木の意味検証です。
 *
 * `src/main/kotlin/me/awabi2048/kantancommander/dsl/DslChecker.kt` の
 * 1:1 移植です。型の整合・変数の定義順・詳細値・組み込み呼出しの引数・
 * break/continue のループ内配置を検証します。
 *
 * 差分（エディタ側の追加）:
 * - 命令名・引数名の妥当性は Kotlin 側ではコンパイラ（写像層）が担いますが、
 *   エディタでは同一テーブル（dsl-catalog.json の commands）を使って
 *   ここで検証します（写像規則の範囲内でエラー文言も寄せます）。
 * - `exprTypes` の記録はコンパイル専用のため持ちません。
 */
import {
    DslArg,
    DslCallExpr,
    DslExpr,
    DslPos,
    DslProgram,
    DslStmt,
    DslStringLiteral,
    binaryOpSymbol,
    posToString,
    stringLiteralPlainText,
    ValueTypeName,
} from "./ast";
import { Catalog, loadCatalog } from "./catalog";
import { modelName } from "./names";

/** DSL の意味検証の失敗です。 */
export class DslCheckError extends Error {
    constructor(
        message: string,
        readonly pos: DslPos,
    ) {
        super(`${posToString(pos)}: ${message}`);
        this.name = "DslCheckError";
    }
}

const INTERP_TYPES = new Set<ValueTypeName>(["number", "string", "boolean"]);
const COMPARABLE_TYPES = new Set<ValueTypeName>(["number", "boolean", "string"]);

type V = ValueTypeName;

export class Checker {
    /** 実行内変数の型表です。ブロックはスコープを分けず、実行内変数は平坦です。 */
    private readonly vars = new Map<string, V>();
    /** スクリプト内で `var world.x` として新規定義したワールド変数の型表です。 */
    private readonly worldVars = new Map<string, V>();
    /** `del world.x` で削除済みとなったワールド変数名（モデル名）です。 */
    private readonly worldDeleted = new Set<string>();
    private loopDepth = 0;
    /** `import world;` 宣言の有無です。world 参照・place_structure の前提です。 */
    private importWorld = false;
    private triggerSeen = false;

    /**
     * @param worldTypes 外部定義のワールド変数型表です（モデル名キー）。
     *   ワークスペース内の他プログラムの `var world.x` 定義から収集します。
     */
    constructor(
        private readonly worldTypes: Map<string, V> = new Map(),
        private readonly catalog: Catalog = loadCatalog(),
    ) {}

    private worldType(name: string): V | undefined {
        const key = modelName(name);
        if (this.worldDeleted.has(key)) return undefined;
        return this.worldVars.get(key) ?? this.worldTypes.get(key);
    }

    private error(pos: DslPos, message: string): never {
        throw new DslCheckError(message, pos);
    }

    /** 検証を実行し、最終的な実行内変数の型表を返します。 */
    check(program: DslProgram): Map<string, V> {
        this.checkDecls(program.decls);
        this.checkBlock(program.body);
        return this.vars;
    }

    /**
     * 宣言部（import／trigger／defarg）を検証します。
     * 宣言順は import → trigger → defarg 固定です。
     * defarg は呼出し引数として本文開始時点で定義済みの実行内変数になり、
     * `trigger event` はイベント種別に応じたイベント引数を同様に定義します。
     */
    private checkDecls(decls: DslProgram["decls"]) {
        let stage = 0;
        for (const decl of decls) {
            const next = decl.kind === "importWorld" ? 1 : decl.kind === "triggerDecl" ? 2 : 3;
            if (next < stage) {
                this.error(decl.pos, "宣言の順序は import → trigger → defarg です");
            }
            stage = next;
        }
        for (const decl of decls) {
            if (decl.kind === "importWorld") {
                this.importWorld = true;
            } else if (decl.kind === "triggerDecl") {
                this.checkTrigger(decl);
            } else {
                if (decl.name === "") {
                    this.error(decl.pos, "defarg には引数名が必要です");
                }
                this.checkVarName(decl.name, decl.pos);
                if (this.vars.has(decl.name)) {
                    this.error(decl.pos, `引数 '${decl.name}' が重複しています`);
                }
                this.vars.set(decl.name, decl.type);
            }
        }
    }

    /** `trigger` 宣言を検証します。種別ごとの引数構成を確認します。 */
    private checkTrigger(decl: Extract<DslProgram["decls"][number], { kind: "triggerDecl" }>) {
        if (this.triggerSeen) this.error(decl.pos, "trigger は1つだけ宣言できます");
        this.triggerSeen = true;
        const spec = decl.spec;
        switch (spec.name) {
            case "redstone":
                if (spec.args.length > 0) {
                    this.error(spec.pos, "trigger redstone は引数を取りません");
                }
                break;
            case "timer": {
                if (spec.args.length !== 1) {
                    this.error(spec.pos, "trigger timer には interval 引数が必要です");
                }
                const arg = spec.args[0];
                if (arg.name !== null && arg.name !== "interval") {
                    this.error(arg.pos, "trigger timer の引数名は interval です");
                }
                const v = arg.value.kind === "number" ? arg.value.value : null;
                if (v === null) {
                    this.error(arg.value.pos, "trigger timer の間隔は数値リテラルで指定してください");
                }
                if (!isTimerSeconds(v)) {
                    this.error(arg.value.pos, "trigger timer の間隔が範囲外です");
                }
                break;
            }
            case "event":
                this.checkEventTrigger(spec);
                break;
            default:
                this.error(spec.pos, "trigger の種別は timer/redstone/event のいずれかです");
        }
    }

    /** `trigger event(...)` の引数を検証し、イベント引数を変数表へ導入します。 */
    private checkEventTrigger(spec: DslCallExpr) {
        const seen = new Set<string>();
        let kind: string | null = null;
        for (const arg of spec.args) {
            const name = arg.name ?? this.error(arg.pos, "trigger event の引数は名前付きが必須です");
            if (!seen.add(name)) this.error(arg.pos, `引数 '${name}' が重複しています`);
            switch (name) {
                case "kind": {
                    const raw = arg.value.kind === "string" ? stringLiteralPlainText(arg.value) : null;
                    if (raw === null) this.error(arg.pos, "kind は文字列リテラルで指定してください");
                    if (!this.catalog.eventKinds.includes(raw)) {
                        this.error(arg.pos, `kind が不明です（${this.catalog.eventKinds.join("/")}）`);
                    }
                    kind = raw;
                    break;
                }
                case "actor":
                case "entity":
                    if (this.checkExpr(arg.value) !== "entity") {
                        this.error(arg.pos, `引数 '${name}' は entity 型が必要です`);
                    }
                    break;
                case "side":
                    this.literalEnum(arg, this.catalog.triggerSides);
                    break;
                case "state":
                    this.literalEnum(arg, this.catalog.triggerStates);
                    break;
                case "block":
                case "block_world":
                case "actor_id":
                case "entity_id": {
                    if (arg.value.kind !== "string" || stringLiteralPlainText(arg.value) === null) {
                        this.error(arg.pos, `引数 '${name}' は文字列リテラルで指定してください`);
                    }
                    break;
                }
                case "block_x":
                case "block_y":
                case "block_z": {
                    const v = arg.value.kind === "number" ? arg.value.value : null;
                    if (v === null || v % 1 !== 0) {
                        this.error(arg.pos, `引数 '${name}' は整数リテラルで指定してください`);
                    }
                    break;
                }
                default:
                    this.error(arg.pos, `trigger event の引数名 '${name}' が不明です`);
            }
        }
        if (kind === null) this.error(spec.pos, "trigger event には kind 引数が必要です");
        // イベント引数（event_actor 等）は実行開始時に定義済みの変数として導入します。
        for (const [name, type] of Object.entries(this.catalog.eventArgs[kind!] ?? {})) {
            if (!this.vars.has(name)) {
                this.vars.set(name, type as V);
            }
        }
    }

    private literalEnum(arg: DslArg, allowed: string[]) {
        const raw = arg.value.kind === "string" ? stringLiteralPlainText(arg.value) : null;
        if (raw === null) {
            this.error(arg.pos, `引数 '${arg.name}' は文字列リテラルで指定してください`);
        }
        if (!allowed.includes(raw)) {
            this.error(arg.pos, `'${raw}' は使えません（${allowed.join("/")}）`);
        }
    }

    /** `import world;` が無い状態でのワールドコンテキスト使用を拒否します。 */
    private requireWorld(pos: DslPos, what: string) {
        if (!this.importWorld) this.error(pos, `${what}には冒頭の 'import world;' 宣言が必要です`);
    }

    /**
     * 1ブロック分の文を検証します。
     * 終端文（return／break／continue）の後に続く文は実行経路上到達できません。
     */
    private checkBlock(stmts: DslStmt[]) {
        let terminated = false;
        for (const stmt of stmts) {
            if (terminated) this.error(stmt.pos, "終端文の後ろの文は到達できません");
            this.checkStmt(stmt);
            if (stmt.kind === "return" || stmt.kind === "break" || stmt.kind === "continue") {
                terminated = true;
            }
        }
    }

    private checkStmt(stmt: DslStmt) {
        switch (stmt.kind) {
            case "varDecl": {
                this.checkVarName(stmt.name, stmt.pos);
                if (stmt.world) this.requireWorld(stmt.pos, "ワールド変数の定義");
                // `= null` は値が未設定の宣言です（型は宣言に従い、値は空で保持します）。
                // `= call "x"(..)` は戻り値を受け取る宣言です。引数を検査し、
                // 宣言型との整合は保存時のモデル検証へ委ねます。
                if (stmt.value.kind === "callResult") {
                    this.checkCallArgs(stmt.value.diskId, stmt.value.args);
                } else if (stmt.value.kind !== "null") {
                    const actual = this.checkExpr(stmt.value);
                    if (actual !== stmt.type) {
                        this.error(stmt.value.pos, `変数 '${stmt.name}' は ${stmt.type} 型ですが、${actual} 型の値は代入できません`);
                    }
                }
                if (stmt.name === "") return;
                if (stmt.world) {
                    // `var world.x` は新規定義（DEFINE）です。同一プログラム内の
                    // 重複宣言は拒否しますが、外部定義表に既にある名前は許容します。
                    const key = modelName(stmt.name);
                    if (this.worldVars.has(key)) {
                        this.error(stmt.pos, `ワールド変数 '${stmt.name}' は既に定義されています`);
                    }
                    this.worldVars.set(key, stmt.type);
                    this.worldDeleted.delete(key);
                } else {
                    if (this.vars.has(stmt.name)) this.error(stmt.pos, `変数 '${stmt.name}' は既に定義されています`);
                    this.vars.set(stmt.name, stmt.type);
                }
                break;
            }
            case "assign": {
                let targetType: V | null;
                switch (stmt.target.kind) {
                    case "variable": {
                        const t = stmt.target;
                        this.checkVarName(t.name, t.pos);
                        // 未命名（空名）スロットへの書き込みは型表を持たないため
                        // 定義確認を行いません（名なし変数は参照できません）。
                        targetType = t.name === ""
                            ? null
                            : this.vars.get(t.name) ?? this.error(t.pos, `未定義の変数 '${t.name}' です`);
                        break;
                    }
                    case "worldVariable": {
                        const t = stmt.target;
                        this.requireWorld(t.pos, "ワールド変数の使用");
                        this.checkVarName(t.name, t.pos);
                        targetType = t.name === ""
                            ? null
                            : this.worldType(t.name) ?? this.error(t.pos, `未定義のワールド変数 '${t.name}' です`);
                        break;
                    }
                    case "entityVariable": {
                        const t = stmt.target;
                        this.checkVarName(t.name, t.pos);
                        const entityType = this.checkExpr(t.entity);
                        if (entityType !== "entity") {
                            this.error(t.entity.pos, `エンティティ変数の対象は entity 型が必要です（${entityType} 型）`);
                        }
                        targetType = null;
                        break;
                    }
                }
                // `= null` は値を未設定へ戻す代入です（複合代入では使えません）。
                // `= call "x"(..)` は戻り値の受取です。戻り値の型は呼出し先の
                // 解析に依存するため、この層では型一致を判定しません。
                const value = stmt.value;
                let valueType: V | null;
                if (value.kind === "callResult") {
                    if (stmt.op !== "ASSIGN") {
                        this.error(value.pos, "call の戻り値は = 代入でのみ受け取れます");
                    }
                    if (stmt.target.kind === "entityVariable") {
                        this.error(value.pos, "call の戻り値はエンティティ変数へ書き込めません");
                    }
                    this.checkCallArgs(value.diskId, value.args);
                    valueType = targetType;
                } else if (value.kind === "entityVarRef") {
                    // `vars.x`・`vars.x.prop` は対象エンティティ自身の変数読み取りです。
                    // エンティティ変数への代入の値でのみ有効で、COPY／PROPERTY へ写像します。
                    if (stmt.target.kind !== "entityVariable") {
                        this.error(value.pos, "vars.<name> はエンティティ変数の代入値としてのみ使えます");
                    }
                    this.checkVarName(value.name, value.pos);
                    // COPY は複写元の型を引き継ぐため、この層では型を確定しません。
                    valueType = targetType;
                } else if (value.kind === "propertyRead" && value.source.kind === "entityVarRef") {
                    if (stmt.target.kind !== "entityVariable") {
                        this.error(value.pos, "vars.<name> はエンティティ変数の代入値としてのみ使えます");
                    }
                    valueType = this.checkEntityVarPropertyRead(value);
                } else if (value.kind === "propertyRead" && stmt.target.kind === "entityVariable") {
                    // PROPERTY の参照元は対象Entity自身の変数に限定されます。
                    // 実行内・ワールド変数の詳細値は値欄を通らないため写像できません。
                    this.error(value.pos, "エンティティ変数への書き込みでは、詳細値の参照元は vars.<name> で指定してください");
                    valueType = this.checkExpr(value);
                } else {
                    valueType = value.kind === "null" && stmt.op === "ASSIGN"
                        ? (targetType ?? "string")
                        : this.checkExpr(value);
                }
                if (stmt.op === "ASSIGN") {
                    // `vars.x`（COPY）は複写元の型を引き継ぐため、ここでは比較しません。
                    if (valueType !== null && value.kind !== "callResult" && targetType !== null && valueType !== targetType) {
                        this.error(value.pos, `${targetType} 型の変数へ ${valueType} 型の値は代入できません`);
                    }
                } else {
                    if (stmt.target.kind === "entityVariable") {
                        this.error(stmt.pos, "エンティティ変数への複合代入はモデルが未対応のため使えません");
                    }
                    if (targetType !== "number" || valueType !== "number") {
                        this.error(stmt.pos, "複合代入は number 型同士でのみ使えます");
                    }
                    if (stmt.op === "MOD") {
                        this.error(stmt.pos, "剰余代入（%=）はモデルが未対応のため使えません");
                    }
                }
                break;
            }
            case "delete": {
                const t = stmt.target;
                if (t.kind === "variable") {
                    this.checkVarName(t.name, t.pos);
                    if (t.name !== "" && !this.vars.delete(t.name)) {
                        this.error(t.pos, `未定義の変数 '${t.name}' は削除できません`);
                    }
                } else if (t.kind === "worldVariable") {
                    this.requireWorld(t.pos, "ワールド変数の削除");
                    this.checkVarName(t.name, t.pos);
                    if (t.name !== "") {
                        if (this.worldType(t.name) === undefined) {
                            this.error(t.pos, `未定義のワールド変数 '${t.name}' は削除できません`);
                        }
                        this.worldVars.delete(modelName(t.name));
                        this.worldDeleted.add(modelName(t.name));
                    }
                } else {
                    this.checkVarName(t.name, t.pos);
                    const entityType = this.checkExpr(t.entity);
                    if (entityType !== "entity") {
                        this.error(t.entity.pos, `エンティティ変数の対象は entity 型が必要です（${entityType} 型）`);
                    }
                }
                break;
            }
            case "if": {
                // `if (null)` は条件 subject 未設定の CONDITION ノードへ写像します。
                if (stmt.condition.kind !== "null") {
                    const cond = this.checkExpr(stmt.condition);
                    if (cond !== "boolean") {
                        this.error(stmt.condition.pos, `if の条件は boolean 型が必要です（${cond} 型）`);
                    }
                }
                this.checkBlock(stmt.thenBody);
                if (stmt.elseBody !== null) this.checkBlock(stmt.elseBody);
                break;
            }
            case "repeat": {
                if (stmt.count.kind !== "null") {
                    const count = this.checkExpr(stmt.count);
                    if (count !== "number") this.error(stmt.count.pos, "repeat の回数は number 型が必要です");
                }
                if (stmt.count.kind === "number") {
                    const n = stmt.count.value;
                    if (n < 1 || n % 1 !== 0) this.error(stmt.count.pos, "repeat の回数は 1 以上の整数が必要です");
                }
                this.loopDepth++;
                try {
                    this.checkBlock(stmt.body);
                } finally {
                    this.loopDepth--;
                }
                break;
            }
            case "break":
                if (this.loopDepth === 0) this.error(stmt.pos, "break は repeat の内側でのみ使えます");
                break;
            case "continue":
                if (this.loopDepth === 0) this.error(stmt.pos, "continue は repeat の内側でのみ使えます");
                break;
            case "return":
                if (stmt.value !== null) this.checkExpr(stmt.value);
                break;
            case "callStmt":
                this.checkCallStmt(stmt);
                break;
            case "callDisk":
                this.checkCallArgs(stmt.diskId, stmt.args);
                break;
        }
    }

    /**
     * `call` の文・式共通の検査です。対象名リテラルと引数列を検査します。
     * 引数名と呼出し先 `defarg` 宣言の照合・型一致はファイル間解決が必要な
     * ため、この層では文法（名前付き・重複なし・値式が妥当）までを保証します。
     */
    private checkCallArgs(literal: DslStringLiteral, args: DslArg[]) {
        this.checkStringLiteral(literal);
        const seen = new Set<string>();
        for (const arg of args) {
            if (arg.name === null) this.error(arg.pos, "call の引数は名前付きで指定してください");
            else {
                this.checkVarName(arg.name, arg.pos);
                if (seen.has(arg.name)) this.error(arg.pos, `引数 '${arg.name}' が重複しています`);
                seen.add(arg.name);
            }
            // `arg = null` は呼出し先の設定欄が未設定であることを表します。
            if (arg.value.kind !== "null") this.checkExpr(arg.value);
        }
    }

    private checkCallStmt(stmt: Extract<DslStmt, { kind: "callStmt" }>) {
        // 引数値の `null` はその設定欄の未設定を表し、型検証をスキップします。
        for (const arg of stmt.args) {
            if (arg.value.kind !== "null") this.checkExpr(arg.value);
        }
        if (stmt.name === "place_structure") this.requireWorld(stmt.pos, "ストラクチャーの配置");
        if (stmt.name === "wait") {
            if (stmt.args.length !== 1) {
                this.error(stmt.pos, "wait は引数を1個だけ取ります");
            }
            const arg = stmt.args[0];
            if (arg.name !== null && arg.name !== "seconds") {
                this.error(arg.pos, "wait の引数名は seconds です");
            }
            const type = this.checkExpr(arg.value);
            if (type !== "number") this.error(arg.pos, "wait の秒数は number 型が必要です");
            if (arg.value.kind === "number" && !isWaitSeconds(arg.value.value)) {
                this.error(arg.pos, "wait の秒数は 0 より大きい 0.05 刻み（tick 境界）の値が必要です");
            }
        }
        this.checkCommandTable(stmt);
    }

    /**
     * 命令名・引数名をカタログの命令表（DSL_COMMAND_TABLE 由来）で検証します。
     * Kotlin 側ではコンパイラの写像規則が担う領域で、エラー文言も写像側に寄せます。
     */
    private checkCommandTable(stmt: Extract<DslStmt, { kind: "callStmt" }>) {
        const spec = this.catalog.commands[stmt.name];
        if (spec === undefined) {
            this.error(stmt.pos, `未対応の命令 '${stmt.name}' です`);
        }
        // `wait(1)` のように唯一の必須引数だけ位置引数を許します。
        let args = stmt.args;
        if (args.some((a) => a.name === null)) {
            const required = Object.entries(spec.args).find(([, r]) => r.required);
            const requiredCount = Object.values(spec.args).filter((r) => r.required).length;
            if (required === undefined || requiredCount !== 1 || args.length !== 1 || args[0].name !== null) {
                this.error(stmt.pos, `'${stmt.name}' の引数は名前付きが必須です`);
            }
            args = [{ ...args[0], name: required[0] }];
        }
        const seen = new Set<string>();
        for (const arg of args) {
            const rule = spec.args[arg.name!];
            // tag_add／tag_remove は1引数ごとに1タグを追記するため重複指定を認めます。
            if (rule?.kind !== "TAG_OP" && !seen.add(arg.name!)) {
                this.error(arg.pos, `引数 '${arg.name}' が重複しています`);
            }
            if (rule !== undefined) {
                this.checkArgType(stmt, arg, rule.kind, rule.enumValues ?? null);
            } else if (spec.extraParamPrefix !== undefined) {
                // `summon` の型付きデータ引数は EntityDataSchema の項目IDに限ります。
                const kindName = this.catalog.entityData[arg.name!];
                if (kindName === undefined) {
                    this.error(arg.pos, `'summon' に項目 '${arg.name}' はありません`);
                } else if (arg.value.kind !== "null") {
                    const type = this.checkExpr(arg.value);
                    const expected: V = kindName === "boolean" ? "boolean" : kindName === "integer" ? "number" : "string";
                    if (type !== expected) {
                        this.error(arg.pos, `引数 '${arg.name}' は ${expected} 型が必要です`);
                    }
                }
            } else {
                this.error(arg.pos, `'${stmt.name}' に引数 '${arg.name}' はありません`);
            }
        }
        for (const [name, rule] of Object.entries(spec.args)) {
            if (rule.required && !seen.has(name)) {
                this.error(stmt.pos, `'${stmt.name}' には必須の引数 '${name}' がありません`);
            }
        }
    }

    /** 命令引数の値を ArgKind に応じて検証します。`arg = null` はスキップ済みです。 */
    private checkArgType(
        stmt: Extract<DslStmt, { kind: "callStmt" }>,
        arg: DslArg,
        kind: string,
        enumValues: string[] | null,
    ) {
        if (arg.value.kind === "null") return;
        const expected: V | null =
            kind === "NUMBER" ? "number"
            : kind === "STRING" ? "string"
            : kind === "BOOLEAN" ? "boolean"
            : kind === "ENTITY" ? "entity"
            : kind === "ITEM" ? "item"
            : kind === "BLOCK" ? "block"
            : null;
        if (expected !== null) {
            const type = this.checkExpr(arg.value);
            if (type !== expected) {
                this.error(arg.pos, `引数 '${arg.name}' は ${expected} 型が必要です`);
            }
            return;
        }
        if (kind === "LOCATION") {
            // 位置引数は位置式のほか `target(...)`・entity 変数（対象の現在位置）も受けます。
            const type = this.checkExpr(arg.value);
            if (type !== "location" && type !== "entity") {
                this.error(arg.pos, `引数 '${arg.name}' は location 型が必要です`);
            }
            return;
        }
        if (kind === "LITERAL") {
            const raw = arg.value.kind === "string" ? stringLiteralPlainText(arg.value) : null;
            if (raw === null) {
                this.error(arg.pos, `引数 '${arg.name}' は文字列リテラルで指定してください`);
            }
            if (enumValues !== null && !enumValues.includes(raw)) {
                this.error(arg.pos, `引数 '${arg.name}' は ${enumValues.join("/")} のいずれかです`);
            }
            return;
        }
        if (kind === "SLOT") {
            if (arg.value.kind === "number") return;
            const raw = arg.value.kind === "string" ? stringLiteralPlainText(arg.value) : null;
            if (raw === null) {
                this.error(arg.pos, "slot は文字列または数値リテラルで指定してください");
            }
            return;
        }
        // PATH_PROPERTY / PATH_ATTRIBUTE / TAG_OP は文字列リテラルです。
        if (arg.value.kind !== "string" || stringLiteralPlainText(arg.value) === null) {
            this.error(arg.pos, `引数 '${arg.name}' は文字列リテラルで指定してください`);
        }
    }

    private checkStringLiteral(lit: Extract<DslExpr, { kind: "string" }>) {
        for (const part of lit.parts) {
            if (part.kind === "interp") this.checkInterp(part.expr);
        }
    }

    /** 補間内の式は表示可能な型（number/string/boolean）のみ許します。 */
    private checkInterp(expr: DslExpr) {
        const type = this.checkExpr(expr);
        if (!INTERP_TYPES.has(type)) {
            this.error(expr.pos, `文字列補間には number/string/boolean 型のみ使えます（${type} 型）`);
        }
    }

    private checkExpr(expr: DslExpr): V {
        return this.inferExpr(expr);
    }

    private inferExpr(expr: DslExpr): V {
        switch (expr.kind) {
            case "null":
                return this.error(expr.pos, "null は値の位置では使えません（spec 引数・条件・変数名の未設定を表す場合のみ使えます）");
            case "number":
                return "number";
            case "string":
                this.checkStringLiteral(expr);
                return "string";
            case "boolean":
                return "boolean";
            case "varRef":
                return this.vars.get(expr.name) ?? this.error(expr.pos, `未定義の変数 '${expr.name}' です`);
            case "worldRef":
                this.requireWorld(expr.pos, "ワールド変数の参照");
                this.checkVarName(expr.name, expr.pos);
                if (expr.name === "") this.error(expr.pos, "名前が未設定のワールド変数は参照できません");
                return this.worldType(expr.name) ?? this.error(expr.pos, `未定義のワールド変数 '${expr.name}' です`);
            case "const":
                if (expr.group === "gamemode") {
                    if (!this.catalog.gamemodeConstants.includes(expr.name)) {
                        this.error(expr.pos, `不明なゲームモード定数 '${expr.name}' です（${this.catalog.gamemodeConstants.join("/")}）`);
                    }
                    return "string";
                }
                return this.error(expr.pos, `不明な定数 '${expr.group}.${expr.name}' です`);
            case "unary": {
                const operand = this.checkExpr(expr.operand);
                if (expr.op === "NOT") {
                    if (operand !== "boolean") this.error(expr.operand.pos, "'!' は boolean 型へのみ使えます");
                    return "boolean";
                }
                if (operand !== "number") this.error(expr.operand.pos, "単項 '-' は number 型へのみ使えます");
                return "number";
            }
            case "binary":
                return this.checkBinary(expr);
            case "propertyRead":
                return this.checkPropertyRead(expr);
            case "callExpr":
                return this.checkCallExpr(expr);
            case "positionLiteral": {
                if (expr.base !== null) {
                    const baseType = this.checkExpr(expr.base);
                    if (baseType !== "location") {
                        this.error(expr.base.pos, `位置リテラルの基準は location 型が必要です（${baseType} 型）`);
                    }
                }
                return "location";
            }
            case "specialPosition":
                return "location";
            // `~`/`^` 接頭辞の単一成分（`~12`）は location() 等の引数内でのみ
            // 意味を持ちます。型上は number として扱います。
            case "componentExpr":
                return "number";
            case "loopCount":
                // CURRENT_LOOP_COUNT はループ外で未解決になるため、DSL では repeat 内に限定します。
                if (this.loopDepth === 0) this.error(expr.pos, "loop.count は repeat の内側でのみ使えます");
                return "number";
            // `call "x"(..)` の式は戻り値受取専用です。代入・宣言以外の式位置では
            // 意味を持たないため、ここでは一律に拒否します。
            case "callResult":
                return this.error(expr.pos, "call は代入・変数宣言の値としてのみ使えます");
            // `vars.<name>` はエンティティ変数への代入値でのみ有効です。
            case "entityVarRef":
                return this.error(expr.pos, "vars.<name> はエンティティ変数の代入値としてのみ使えます");
        }
    }

    private checkBinary(expr: Extract<DslExpr, { kind: "binary" }>): V {
        const left = this.checkExpr(expr.left);
        const right = this.checkExpr(expr.right);
        const fail = (): never => this.error(
            expr.pos,
            `'${binaryOpSymbol(expr.op)}' は ${left} 型と ${right} 型には使えません`,
        );
        switch (expr.op) {
            case "ADD": case "SUB": case "MUL": case "DIV":
                return left === "number" && right === "number" ? "number" : fail();
            // モデルの数値式言語は剰余を持たないため、% は写像不能です。
            case "MOD":
                return this.error(expr.pos, "剰余演算（%）はモデルが未対応のため使えません");
            case "EQ": case "NE": {
                // モデルの条件が比較できるのは NUMBER／BOOLEAN／STRING の等値と、
                // `pos.block == block(...)` 形式のブロック照合だけです。
                const allowed =
                    (left === right && COMPARABLE_TYPES.has(left)) ||
                    (left === "block" && right === "block" &&
                        expr.left.kind === "propertyRead" && expr.left.name === "block" &&
                        expr.right.kind === "callExpr" && expr.right.name === "block");
                return allowed ? "boolean" : fail();
            }
            case "LT": case "LE": case "GT": case "GE":
                return left === "number" && right === "number" ? "boolean" : fail();
            case "AND": case "OR":
                return left === "boolean" && right === "boolean" ? "boolean" : fail();
        }
    }

    /**
     * `vars.<src>.<prop>` / `vars.<src>.<prop>(vars.<arg>)` のエンティティ変数
     * 詳細値読み取りを検証します。ENTITY scope は定義表を持たず参照元・
     * 引数変数の型は実行時に決まるため、詳細値の存在・引数の有無・
     * 引数が同じ対象の変数参照であることまでを確認します。
     */
    private checkEntityVarPropertyRead(expr: Extract<DslExpr, { kind: "propertyRead" }>): V {
        const source = expr.source;
        if (source.kind !== "entityVarRef") return this.error(expr.pos, "vars.<name> の形式で参照してください");
        this.checkVarName(source.name, source.pos);
        const def = this.catalog.properties[expr.name];
        if (def === undefined) {
            return this.error(expr.pos, `不明な詳細値 '${expr.name}' です`);
        }
        if (def.argType === undefined && expr.arg !== null) {
            this.error(expr.arg.pos, `詳細値 '${expr.name}' は引数を取りません`);
        }
        if (def.argType !== undefined && expr.arg === null) {
            this.error(expr.pos, `詳細値 '${expr.name}' には ${def.argType} 型の引数が必要です`);
        }
        if (def.argType !== undefined && expr.arg !== null) {
            // 引数変数は参照元と同じ対象のエンティティ変数から読み出されます。
            if (expr.arg.kind !== "entityVarRef") {
                this.error(expr.arg.pos, "エンティティ変数の詳細値引数は vars.<name> で指定してください");
            } else {
                this.checkVarName(expr.arg.name, expr.arg.pos);
            }
        }
        return def.result as V;
    }

    private checkPropertyRead(expr: Extract<DslExpr, { kind: "propertyRead" }>): V {
        const sourceType = this.checkExpr(expr.source);
        const def = this.catalog.properties[expr.name];
        if (def === undefined) {
            return this.error(expr.pos, `不明な詳細値 '${expr.name}' です`);
        }
        if (!def.sources.includes(sourceType)) {
            this.error(expr.pos, `${sourceType} 型からは詳細値 '${expr.name}' を取得できません`);
        }
        if (def.argType === undefined && expr.arg !== null) {
            this.error(expr.arg.pos, `詳細値 '${expr.name}' は引数を取りません`);
        }
        if (def.argType !== undefined && expr.arg === null) {
            this.error(expr.pos, `詳細値 '${expr.name}' には ${def.argType} 型の引数が必要です`);
        }
        if (def.argType !== undefined && expr.arg !== null) {
            const argType = this.checkExpr(expr.arg);
            if (argType !== def.argType) {
                this.error(expr.arg.pos, `詳細値 '${expr.name}' の引数は ${def.argType} 型が必要です`);
            }
        }
        return def.result as V;
    }

    private checkCallExpr(expr: DslCallExpr): V {
        switch (expr.name) {
            case "location": {
                // yaw/pitch だけの指定は向き（ROTATION）の省略形です。
                // 座標成分が1つでもあれば通常の位置指定として x/y/z を必須にします。
                const names = new Set(expr.args.map((a) => a.name).filter((n) => n !== null));
                const rotationOnly = names.size > 0 && !names.has("x") && !names.has("y") && !names.has("z");
                this.checkConstructor(expr, "location", rotationOnly);
                return "location";
            }
            case "item":
                this.checkConstructor(expr, "item");
                return "item";
            case "block":
                this.checkConstructor(expr, "block");
                return "block";
            case "target":
                return this.checkTarget(expr);
            case "player":
                this.checkTargetCondCall(expr, true);
                return "boolean";
            case "exists":
                this.checkTargetCondCall(expr, false);
                return "boolean";
            case "signal":
                this.checkSignalCall(expr);
                return "boolean";
            case "random":
                this.checkConstructor(expr, "random");
                return "number";
            case "myworld_spawn":
                this.checkConstructor(expr, "myworld_spawn");
                return "location";
            case "orientation":
                this.checkOrientation(expr);
                return "entity";
            default: {
                // `pow`・三角関数はモデルの数値式言語へそのまま写像します。
                // `a ^ b` は位置成分 `^x` と文法が衝突するため `pow(a, b)` の関数形です。
                const arity = this.catalog.numericFunctions[expr.name];
                if (arity !== undefined) {
                    this.checkNumericFunction(expr, arity);
                    return "number";
                }
                return this.error(expr.pos, `不明な呼出し '${expr.name}' です`);
            }
        }
    }

    /**
     * `pow(a, b)`・`sin(x)` 等の数値関数を検証します。モデルの式言語は
     * 位置引数のみのため、名前付き引数・個数違いは拒否します。
     */
    private checkNumericFunction(expr: DslCallExpr, arity: number) {
        if (expr.args.length !== arity || expr.args.some((a) => a.name !== null)) {
            this.error(expr.pos, `'${expr.name}' の引数は ${arity} 個の位置引数で指定してください`);
        }
        for (const arg of expr.args) {
            if (this.checkExpr(arg.value) !== "number") {
                this.error(arg.pos, `'${expr.name}' の引数は number 型が必要です`);
            }
        }
    }

    /**
     * `player(...)`・`exists(...)` の対象部を検証します。
     * `target = <entity式>` で直接指定するか、`kind = "..."` 以下の target と
     * 同じ引数で絞り込みます。player は補足条件（sneaking／item）も取ります。
     */
    private checkTargetCondCall(expr: DslCallExpr, playerExtras: boolean) {
        const direct = expr.args.find((a) => a.name === "target");
        if (direct !== undefined && direct.value.kind !== "null") {
            const type = this.checkExpr(direct.value);
            if (type !== "entity") {
                this.error(direct.pos, `target は entity 型が必要です（${type} 型）`);
            }
        }
        const targetCondArgs = this.catalog.valueCalls["player"].args;
        const seen = new Set<string>();
        for (const arg of expr.args) {
            if (arg.name === null) this.error(arg.pos, "条件呼出しの引数は名前付きが必須です");
            if (!seen.add(arg.name)) this.error(arg.pos, `引数 '${arg.name}' が重複しています`);
            // `arg = null` はその条件欄の未設定を表します。
            if (arg.value.kind === "null") continue;
            if (arg.name === "target") {
                // 上で型検証済み
            } else if (arg.name === "at") {
                if (this.checkExpr(arg.value) !== "location") {
                    this.error(arg.pos, "引数 'at' は location 型が必要です");
                }
            } else if (arg.name in targetCondArgs) {
                const expected = targetCondArgs[arg.name].type;
                const type = this.checkExpr(arg.value);
                if (type !== expected) this.error(arg.pos, `引数 '${arg.name}' は ${expected} 型が必要です`);
                if (!this.isCompileTimeLiteral(arg.value)) {
                    this.error(arg.value.pos, `'${expr.name}' の対象引数はリテラルのみ使用できます`);
                }
            } else if (playerExtras && arg.name === "sneaking") {
                if (this.checkExpr(arg.value) !== "boolean") this.error(arg.pos, "引数 'sneaking' は boolean 型が必要です");
                if (arg.value.kind !== "boolean") this.error(arg.value.pos, "sneaking は true/false リテラルのみ使用できます");
            } else if (playerExtras && arg.name === "item") {
                if (this.checkExpr(arg.value) !== "item") this.error(arg.pos, "引数 'item' は item 型が必要です");
            } else {
                this.error(arg.pos, `不明な引数名 '${arg.name}' です`);
            }
        }
        if (direct !== undefined) {
            // target= と kind 引数群の併用は対象指定が二重になるため拒否します。
            const others = expr.args.filter((a) => a.name !== "target" && a.name !== null && a.name in targetCondArgs);
            if (others.length > 0) this.error(others[0].pos, "target と kind 系引数は併用できません");
        } else if (!expr.args.some((a) => a.name === "kind")) {
            // モデルの PLAYER／OTHER_ENTITY 判定は対象指定を必須とします。
            this.error(expr.pos, `'${expr.name}' には対象指定（kind = ... または target = ...）が必要です`);
        }
    }

    /** `signal(4)`・`signal(min = a, max = b)` の信号強度条件を検証します。 */
    private checkSignalCall(expr: DslCallExpr) {
        const positional = expr.args.filter((a) => a.name === null);
        const named = expr.args.filter((a) => a.name !== null);
        if (positional.length > 0 && named.length > 0) {
            this.error(expr.pos, "signal は位置引数か min/max 引数のどちらか一方で指定してください");
        }
        let bounds: DslArg[];
        if (positional.length > 0) {
            if (positional.length !== 1) this.error(expr.pos, "signal の位置引数は1個だけです");
            bounds = positional;
        } else {
            for (const arg of named) {
                if (arg.name !== "min" && arg.name !== "max") {
                    this.error(arg.pos, "signal の引数名は min/max です");
                }
            }
            if (new Set(named.map((a) => a.name)).size !== named.length) {
                this.error(expr.pos, "signal の引数が重複しています");
            }
            bounds = named;
        }
        if (bounds.length === 0) this.error(expr.pos, "signal には強度または min/max の指定が必要です");
        for (const arg of bounds) {
            if (arg.value.kind === "null") continue;
            const type = this.checkExpr(arg.value);
            if (type !== "number") this.error(arg.pos, "signal の強度は number 型が必要です");
            const v = arg.value.kind === "number" ? arg.value.value : null;
            if (v === null) this.error(arg.value.pos, "signal の強度は数値リテラルで指定してください");
            if (v < 0 || v > 15 || v % 1 !== 0) this.error(arg.value.pos, "signal の強度は 0..15 の整数が必要です");
        }
    }

    /** `orientation(<entity式>)` の向き複写を検証します。 */
    private checkOrientation(expr: DslCallExpr) {
        if (expr.args.length !== 1) {
            this.error(expr.pos, "orientation は引数を1個だけ取ります");
        }
        const arg = expr.args[0];
        if (arg.name !== null && arg.name !== "target") {
            this.error(arg.pos, "orientation の引数名は target です");
        }
        const type = this.checkExpr(arg.value);
        if (type !== "entity") this.error(arg.pos, "orientation の対象は entity 型が必要です");
    }

    /**
     * コンストラクタの引数を検証します。コンストラクタは全引数が名前付きで、
     * 未知の引数名・必須引数の欠落・重複・型不一致をエラーにします。
     */
    private checkConstructor(expr: DslCallExpr, name: keyof Catalog["valueCalls"] | string, relaxRequired = false) {
        const spec = this.catalog.valueCalls[name];
        if (spec === undefined) return;
        const seen = new Set<string>();
        for (const arg of expr.args) {
            if (arg.name === null) this.error(arg.pos, "コンストラクタの引数は名前付きが必須です");
            const argSpec = spec.args[arg.name];
            if (argSpec === undefined) {
                this.error(arg.pos, `不明な引数名 '${arg.name}' です（${Object.keys(spec.args).join("/")}）`);
            }
            if (!seen.add(arg.name)) this.error(arg.pos, `引数 '${arg.name}' が重複しています`);
            const type = this.checkExpr(arg.value);
            if (type !== argSpec.type) {
                this.error(arg.pos, `引数 '${arg.name}' は ${argSpec.type} 型が必要です`);
            }
            if (!this.isCompileTimeLiteral(arg.value)) {
                this.error(arg.value.pos, `'${expr.name}' の引数はリテラルのみ使用できます`);
            }
        }
        if (!relaxRequired) {
            const missing = Object.entries(spec.args).find(([n, s]) => s.required && !seen.has(n));
            if (missing !== undefined) {
                this.error(expr.pos, `必須の引数 '${missing[0]}' がありません`);
            }
        }
        // item の個数はモデルの TypedValue 組立が読まないため、表層でも拒否します。
        if (expr.name === "item" && seen.has("amount")) {
            this.error(expr.pos, "item の 'amount' はモデルが未対応のため使用できません");
        }
    }

    /** コンパイル時定数となる式（数値・非補間文字列・真偽値・名前空間定数）です。 */
    private isCompileTimeLiteral(expr: DslExpr): boolean {
        switch (expr.kind) {
            case "number":
            case "boolean":
            case "const":
            case "componentExpr":
                return true;
            case "string":
                return stringLiteralPlainText(expr) !== null;
            case "unary":
                return expr.op === "NEG" && expr.operand.kind === "number";
            default:
                return false;
        }
    }

    private checkTarget(expr: DslCallExpr): V {
        const kindArg = expr.args.find((a) => a.name === "kind");
        if (kindArg === undefined) {
            this.error(expr.pos, "target には引数 kind が必要です");
        }
        this.checkConstructor(expr, "target");
        const kind = kindArg!.value.kind === "string" ? stringLiteralPlainText(kindArg!.value) : null;
        if (kind === null) {
            this.error(kindArg!.pos, "target の kind は文字列リテラルで指定してください");
        }
        if (!this.catalog.targetKinds.includes(kind)) {
            this.error(kindArg!.pos, `target の kind が不明です（${this.catalog.targetKinds.join("/")}）`);
        }
        return "entity";
    }

    /**
     * DSL で宣言・参照できる変数名を検証します。
     * 小文字 snake_case（`[a-z][a-z0-9_]*`）に限定することで、
     * `_`→`-` の写像が一意に定まります。
     */
    private checkVarName(name: string, pos: DslPos) {
        // 空文字列は `null` 指定による未命名スロットで、命名規則の対象外です。
        if (name === "") return;
        if (!new RegExp(`^${this.catalog.varNamePattern}$`).test(name)) {
            this.error(pos, `変数名 '${name}' は小文字の snake_case（[a-z][a-z0-9_]*）で指定してください`);
        }
        if (this.catalog.reservedNames.includes(name)) {
            this.error(pos, `'${name}' は予約された名前のため変数名として使えません`);
        }
    }
}

/** 秒数がMinecraftのtick境界（0.05秒）の整数倍かを判定します。 */
function isTickAlignedSeconds(seconds: number): boolean {
    if (!Number.isFinite(seconds)) return false;
    const scaled = seconds / 0.05;
    return Math.abs(scaled - Math.round(scaled)) < 1e-9;
}

/** `wait` の秒数です。0 より大きい tick 境界の値を許可します。 */
function isWaitSeconds(seconds: number): boolean {
    return Number.isFinite(seconds) && seconds > 0 && seconds <= 86400 && isTickAlignedSeconds(seconds);
}

/** `trigger timer` の間隔です（wait と同じ規則）。 */
function isTimerSeconds(seconds: number): boolean {
    return isWaitSeconds(seconds);
}
