/**
 * テキスト解析の入口です。字句・構文・意味検証を順に実行し、
 * 診断情報へ変換します。拡張機能層はここだけを呼びます。
 */
import { DslArg, DslExpr, DslPos, DslProgram, DslStmt, DslStringLiteral, stringLiteralPlainText, ValueTypeName } from "./ast";
import { Catalog, loadCatalog } from "./catalog";
import { Checker, DslCheckError } from "./checker";
import { DslParseError } from "./lexer";
import { parse } from "./parser";
import { fileVariables } from "./suggest";

export interface Diagnostic {
    pos: DslPos;
    message: string;
    severity: "error" | "warning";
}

export interface AnalysisResult {
    program: DslProgram | null;
    diagnostics: Diagnostic[];
}

/**
 * テキストを解析・検証します。
 * @param worldTypes ワークスペース内の他プログラム由来のワールド変数型表（モデル名キー）。
 * @param knownPrograms ワークスペース内のプログラム名一覧です。`call` の対象確認に使います。
 * @param catalog DSL 語彙カタログです。
 * @param programDefargs プログラム名から `defarg` 表（DSL 名 → 型）を引く解決子です。
 *        指定時は `call` の引数名・不足・リテラル型を呼出し先宣言と照合します。
 */
export function analyze(
    text: string,
    worldTypes: Map<string, ValueTypeName> = new Map(),
    knownPrograms: Set<string> = new Set(),
    catalog: Catalog = loadCatalog(),
    programDefargs: (name: string) => Map<string, ValueTypeName> | null = () => null,
): AnalysisResult {
    let program: DslProgram;
    try {
        program = parse(text);
    } catch (e) {
        if (e instanceof DslParseError) {
            return { program: null, diagnostics: [{ pos: e.pos, message: e.message, severity: "error" }] };
        }
        throw e;
    }
    try {
        new Checker(worldTypes, catalog).check(program);
    } catch (e) {
        if (e instanceof DslCheckError) {
            return { program, diagnostics: [{ pos: e.pos, message: e.message, severity: "error" }] };
        }
        throw e;
    }
    const diagnostics: Diagnostic[] = [];
    // `call "name"` の対象をワークスペースのプログラム名で確認します。
    // UUID 表記は直接ID参照のため対象外、見つからない名前は環境依存のため警告とします。
    // ブロック内の call と戻り値受取（`x = call "n"(..)`）も同じ規則で検査します。
    const uuidPattern = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
    const localVars = fileVariables(text).vars;
    const checkCallSite = (diskId: DslStringLiteral, args: DslArg[], pos: DslPos) => {
        const target = stringLiteralPlainText(diskId);
        if (target === null || uuidPattern.test(target)) return;
        if (target === program.name) {
            diagnostics.push({ pos, message: "プログラム自身を呼び出す循環参照になります", severity: "error" });
            return;
        }
        if (!knownPrograms.has(target)) {
            diagnostics.push({
                pos: diskId.pos,
                message: `プログラム '${target}' がワークスペース内に見つかりません`,
                severity: "warning",
            });
            return;
        }
        // 呼出し先の defarg 宣言と照合します。宣言側が取れないときは
        // 引数の照合を行いません（対象の実在確認まで）。
        const defargs = programDefargs(target);
        if (defargs === null) return;
        const given = new Map<string, DslArg>();
        for (const arg of args) {
            if (arg.name === null) continue;
            given.set(arg.name, arg);
            if (!defargs.has(arg.name)) {
                diagnostics.push({
                    pos: arg.pos,
                    message: `引数 '${arg.name}' は '${target}' の defarg に宣言されていません`,
                    severity: "warning",
                });
            }
        }
        for (const [name] of defargs) {
            if (!given.has(name)) {
                diagnostics.push({
                    pos: diskId.pos,
                    message: `'${target}' の引数 '${name}' が未指定です`,
                    severity: "warning",
                });
            }
        }
        // リテラルと変数参照の型は確定できる範囲で宣言型と照合します。
        for (const [name, arg] of given) {
            const declared = defargs.get(name);
            if (declared === undefined || arg.value.kind === "null") continue;
            const actual = literalArgType(arg.value, localVars, worldTypes);
            if (actual !== null && actual !== declared) {
                diagnostics.push({
                    pos: arg.pos,
                    message: `引数 '${name}' は ${declared} 型ですが、${actual} 型の値が指定されています`,
                    severity: "warning",
                });
            }
        }
    };
    const walk = (stmts: DslStmt[]) => {
        for (const stmt of stmts) {
            switch (stmt.kind) {
                case "callDisk":
                    checkCallSite(stmt.diskId, stmt.args, stmt.pos);
                    break;
                case "varDecl":
                case "assign":
                    if (stmt.value.kind === "callResult") {
                        checkCallSite(stmt.value.diskId, stmt.value.args, stmt.value.pos);
                    }
                    break;
                case "if":
                    walk(stmt.thenBody);
                    if (stmt.elseBody !== null) walk(stmt.elseBody);
                    break;
                case "repeat":
                    walk(stmt.body);
                    break;
            }
        }
    };
    walk(program.body);
    return { program, diagnostics };
}

/**
 * call 引数値の静的に確定する型です。リテラル・worldRef・定数・確定済みの
 * 変数参照のみ型を返し、式・詳細値参照等は null（判定不能）とします。
 */
function literalArgType(
    expr: DslExpr,
    vars: Map<string, ValueTypeName>,
    worldTypes: Map<string, ValueTypeName>,
): ValueTypeName | null {
    switch (expr.kind) {
        case "number": return "number";
        case "boolean": return "boolean";
        case "string": return "string";
        case "callExpr":
            // item(...)/block(...)/location(...) 等の値コンストラクタです。
            return null;
        case "varRef":
            return vars.get(expr.name) ?? null;
        case "worldRef":
            return worldTypes.get(expr.name.replaceAll("_", "-")) ?? null;
        default:
            return null;
    }
}
