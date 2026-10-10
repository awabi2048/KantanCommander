/**
 * テキスト解析の入口です。字句・構文・意味検証を順に実行し、
 * 診断情報へ変換します。拡張機能層はここだけを呼びます。
 */
import { DslPos, DslProgram, stringLiteralPlainText, ValueTypeName } from "./ast";
import { Catalog, loadCatalog } from "./catalog";
import { Checker, DslCheckError } from "./checker";
import { DslParseError } from "./lexer";
import { parse } from "./parser";

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
 */
export function analyze(
    text: string,
    worldTypes: Map<string, ValueTypeName> = new Map(),
    knownPrograms: Set<string> = new Set(),
    catalog: Catalog = loadCatalog(),
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
    const uuidPattern = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
    for (const stmt of program.body) {
        if (stmt.kind !== "callDisk") continue;
        const target = stringLiteralPlainText(stmt.diskId);
        if (target === null) continue;
        if (uuidPattern.test(target)) continue;
        if (target === program.name) {
            diagnostics.push({ pos: stmt.pos, message: "プログラム自身を呼び出す循環参照になります", severity: "error" });
        } else if (!knownPrograms.has(target)) {
            diagnostics.push({
                pos: stmt.diskId.pos,
                message: `プログラム '${target}' がワークスペース内に見つかりません`,
                severity: "warning",
            });
        }
    }
    return { program, diagnostics };
}
