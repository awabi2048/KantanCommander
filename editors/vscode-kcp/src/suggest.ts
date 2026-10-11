/**
 * 補完・ホバー候補の生成ロジックです。
 *
 * VSCode API へ依存しない純粋な文字列・位置ベースの実装で、
 * 単体テストからも利用できます。エディタ層が候補を
 * CompletionItem / Hover へ写像します。
 */
import { DslProgram, DslStmt, stringLiteralPlainText, ValueTypeName } from "./ast";
import { Catalog } from "./catalog";
import { parse } from "./parser";
import { WorkspaceIndex } from "./workspace";

export interface Suggestion {
    label: string;
    kind: "keyword" | "type" | "command" | "call" | "arg" | "variable" | "worldVar" |
        "constant" | "property" | "program" | "trigger" | "eventKind" | "enumValue";
    detail?: string;
}

const STMT_KEYWORDS = ["var", "if", "repeat", "break", "continue", "return", "call", "del"];
const DECL_KEYWORDS = ["import", "trigger", "defarg"];
const VALUE_CALLS = ["location", "item", "block", "target", "random", "myworld_spawn", "orientation"];
const COND_CALLS = ["player", "exists", "signal"];
const TRIGGER_NAMES = ["timer", "redstone", "event"];

/** カーソル位置の直前の「文脈」を軽量解析で判定します。 */
export type CursorContext =
    | { kind: "callTarget" }                        // `call "..|`
    | { kind: "worldMember" }                       // `world.|`
    | { kind: "gamemodeMember" }                    // `gamemode.|`
    | { kind: "propertyAccess"; receiver: string }  // `expr.|`
    | { kind: "callArg"; callName: string }         // `name(a = 1, |`
    | { kind: "callDiskArg"; programName: string }  // `call "n"(a = 1, |`
    | { kind: "typeName" }                          // `var x: |` / `defarg x: |`
    | { kind: "triggerName" }                       // `trigger |`
    | { kind: "eventKind" }                         // `trigger event(kind = "|`
    | { kind: "stringLiteralArg"; callName: string; argName: string } // `name(arg = "|`
    | { kind: "statement" }                         // 文の先頭候補
    | { kind: "expression" };                       // 式の先頭候補

function linePrefix(text: string, offset: number): string {
    const lineStart = text.lastIndexOf("\n", offset - 1) + 1;
    return text.slice(lineStart, offset);
}

/**
 * カーソル直前の開き括弧が未対応であれば、その呼出し名を返します。
 * 文字列・コメントを雑に除外しつつ `(` の深度を数えます。
 */
export function enclosingCallName(text: string, offset: number): string | null {
    let depth = 0;
    let inString = false;
    let escape = false;
    let inLineComment = false;
    for (let i = offset - 1; i >= 0; i--) {
        const c = text[i];
        if (inLineComment) {
            if (c === "\n") inLineComment = false;
            continue;
        }
        if (inString) {
            if (escape) { escape = false; continue; }
            if (c === "\\") { escape = true; continue; }
            if (c === "\"") inString = false;
            continue;
        }
        if (c === "/" && text[i - 1] === "/") { inLineComment = true; i--; continue; }
        if (c === "\"") { inString = true; continue; }
        if (c === ")") depth++;
        else if (c === "(") {
            if (depth === 0) {
                // 直前の識別子を取り出します。
                let j = i - 1;
                while (j >= 0 && /[ \t]/.test(text[j])) j--;
                let end = j + 1;
                while (j >= 0 && /[a-zA-Z0-9_]/.test(text[j])) j--;
                const name = text.slice(j + 1, end);
                return name || null;
            }
            depth--;
        }
    }
    return null;
}

/**
 * 直前が文字列リテラル内かを判定します。`call "` 内補完に使います。
 * 行内の `"` の奇偶で簡易判定します（複数行文字列は DSL にありません）。
 */
function insideString(text: string, offset: number): boolean {
    const prefix = linePrefix(text, offset);
    let inString = false;
    let escape = false;
    for (const c of prefix) {
        if (escape) { escape = false; continue; }
        if (c === "\\") { escape = true; continue; }
        if (c === "\"") inString = !inString;
    }
    return inString;
}

export function cursorContext(text: string, offset: number): CursorContext {
    const prefix = linePrefix(text, offset);
    // `call "..."` の対象名位置です。
    if (/\bcall\s+"[^"]*$/.test(prefix)) return { kind: "callTarget" };
    // `trigger event(kind = "|` です。
    const eventKindMatch = /\bevent\s*\([^)]*\bkind\s*=\s*"[^"]*$/.exec(prefix);
    if (eventKindMatch) return { kind: "eventKind" };
    // `name(arg = "|` の enum 値位置です。
    const litArgMatch = /([a-z_][a-z0-9_]*)\s*=\s*"[^"]*$/.exec(prefix);
    if (litArgMatch) {
        const call = enclosingCallName(text, offset);
        if (call) return { kind: "stringLiteralArg", callName: call, argName: litArgMatch[1] };
    }
    if (/\bworld\.[a-z0-9_]*$/.test(prefix)) return { kind: "worldMember" };
    if (/\bgamemode\.[a-z0-9_]*$/.test(prefix)) return { kind: "gamemodeMember" };
    const propMatch = /([a-zA-Z_][a-zA-Z0-9_]*)\.[a-z0-9_]*$/.exec(prefix);
    if (propMatch) return { kind: "propertyAccess", receiver: propMatch[1] };
    if (/\b(?:var(?:\s+world\.)?|defarg)\s+[a-z0-9_]*\s*:\s*[a-z]*$/i.test(prefix)) {
        return { kind: "typeName" };
    }
    if (/\btrigger\s+[a-z]*$/.test(prefix)) return { kind: "triggerName" };
    // `call "name"(...|` の引数位置です。
    const callDiskMatch = /\bcall\s+"([^"]*)"\s*\([^)]*$/.exec(prefix);
    if (callDiskMatch) return { kind: "callDiskArg", programName: callDiskMatch[1] };
    const call = enclosingCallName(text, offset);
    if (call) return { kind: "callArg", callName: call };
    // 式位置か文位置かの推定：行頭からの空白のみ、または `=`/`(`/`{` 直後なら式。
    if (/[=({,]\s*[a-z0-9_]*$/.test(prefix) || /\b(if|repeat)\s*\(\s*[a-z0-9_]*$/.test(prefix)) {
        return { kind: "expression" };
    }
    return { kind: "statement" };
}

/** ファイル内の `var`・`defarg`・`var world.` 宣言を収集します（解析失敗時は正規表現の近似）。 */
export function fileVariables(text: string): { vars: Map<string, ValueTypeName>; worldVars: Map<string, ValueTypeName> } {
    const vars = new Map<string, ValueTypeName>();
    const worldVars = new Map<string, ValueTypeName>();
    let program: DslProgram | null = null;
    try {
        program = parse(text);
    } catch {
        // 解析失敗時は正規表現で近似収集します（補完用途）。
        const declRe = /\bdefarg\s+([a-z][a-z0-9_]*)\s*:\s*([a-z]+)/g;
        const varRe = /\bvar\s+(world\.)?([a-z][a-z0-9_]*)?\s*:\s*([a-z]+)/g;
        let m: RegExpExecArray | null;
        while ((m = declRe.exec(text))) vars.set(m[1], m[2] as ValueTypeName);
        while ((m = varRe.exec(text))) {
            if (m[1]) worldVars.set(m[2] ?? "", m[3] as ValueTypeName);
            else vars.set(m[2] ?? "", m[3] as ValueTypeName);
        }
        return { vars, worldVars };
    }
    const collect = (stmts: DslStmt[]) => {
        for (const s of stmts) {
            if (s.kind === "varDecl") {
                if (s.world) worldVars.set(s.name, s.type);
                else vars.set(s.name, s.type);
            } else if (s.kind === "if") {
                collect(s.thenBody);
                if (s.elseBody) collect(s.elseBody);
            } else if (s.kind === "repeat") {
                collect(s.body);
            }
        }
    };
    for (const decl of program.decls) {
        if (decl.kind === "defarg") vars.set(decl.name, decl.type);
        if (decl.kind === "triggerDecl" && decl.spec.name === "event") {
            const kindArg = decl.spec.args.find((a) => a.name === "kind");
            const kind = kindArg && kindArg.value.kind === "string"
                ? stringLiteralPlainText(kindArg.value) : null;
            if (kind) {
                for (const [n, t] of Object.entries(loadCatalogEventArgs(kind))) {
                    vars.set(n, t as ValueTypeName);
                }
            }
        }
    }
    collect(program.body);
    return { vars, worldVars };
}

function loadCatalogEventArgs(kind: string): Record<string, string> {
    // catalog は循環 import を避けるため遅延読み込みします。
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const { loadCatalog } = require("./catalog") as typeof import("./catalog");
    return loadCatalog().eventArgs[kind] ?? {};
}

/** 文脈に応じた補完候補を返します。 */
export function suggest(
    text: string,
    offset: number,
    catalog: Catalog,
    index: WorkspaceIndex | null,
): Suggestion[] {
    const ctx = cursorContext(text, offset);
    const { vars, worldVars } = fileVariables(text);
    const varSuggestions = (): Suggestion[] => {
        const out: Suggestion[] = [];
        for (const [name, type] of vars) {
            out.push({ label: name, kind: "variable", detail: `変数 ${name}: ${type}` });
        }
        return out;
    };
    const worldVarSuggestions = (): Suggestion[] => {
        const out: Suggestion[] = [];
        for (const name of worldVars.keys()) {
            out.push({ label: name, kind: "worldVar", detail: `world.${name}` });
        }
        if (index) {
            for (const name of index.worldTypes().keys()) {
                // モデル名 → DSL 名へ写像して重複除去します。
                const dsl = name.replaceAll("-", "_");
                if (!worldVars.has(dsl)) {
                    out.push({ label: dsl, kind: "worldVar", detail: `world.${dsl}` });
                }
            }
        }
        return out;
    };
    const callExprSuggestions = (): Suggestion[] => {
        const out: Suggestion[] = [];
        for (const name of VALUE_CALLS) {
            out.push({ label: name, kind: "call", detail: `${name}(...)` });
        }
        for (const name of COND_CALLS) {
            out.push({ label: name, kind: "call", detail: `${name}(...)` });
        }
        for (const c of catalog.gamemodeConstants) {
            out.push({ label: `gamemode.${c}`, kind: "constant" });
        }
        for (const s of catalog.specialPositions) {
            out.push({ label: s, kind: "constant", detail: "実行コンテキスト位置" });
        }
        out.push({ label: "loop.count", kind: "constant", detail: "ループ回数（repeat 内）" });
        return out;
    };

    switch (ctx.kind) {
        case "callTarget": {
            const out: Suggestion[] = [];
            if (index) {
                for (const name of index.programNames().keys()) {
                    out.push({ label: name, kind: "program" });
                }
            }
            return out;
        }
        case "worldMember":
            return worldVarSuggestions();
        case "gamemodeMember":
            return catalog.gamemodeConstants.map((c) => ({ label: c, kind: "constant" as const }));
        case "propertyAccess": {
            // 受け取り手の型が分かればその型の詳細値だけを出します。
            const varType = vars.get(ctx.receiver);
            const out: Suggestion[] = [];
            for (const [name, def] of Object.entries(catalog.properties)) {
                if (varType === undefined || def.sources.includes(varType)) {
                    out.push({
                        label: name,
                        kind: "property",
                        detail: `.${name}: ${def.result}${def.argType ? `（引数 ${def.argType}）` : ""}`,
                    });
                }
            }
            return out;
        }
        case "callArg": {
            const out: Suggestion[] = [];
            const spec = catalog.commands[ctx.callName] ?? catalog.valueCalls[ctx.callName];
            if (spec !== undefined) {
                const args = "args" in spec ? spec.args : {};
                for (const [name, rule] of Object.entries(args)) {
                    const detail = "kind" in rule
                        ? `${name}${rule.required ? "" : "?"}: ${(rule as { kind: string }).kind.toLowerCase()}`
                        : `${name}: ${(rule as { type: string }).type}`;
                    out.push({ label: `${name} = `, kind: "arg", detail });
                }
            }
            if (ctx.callName === "target" || ctx.callName === "player" || ctx.callName === "exists") {
                out.push({ label: "kind = ", kind: "arg", detail: "対象種別" });
            }
            return [...out, ...varSuggestions(), ...callExprSuggestions()];
        }
        case "callDiskArg": {
            // `call "n"(|` は呼出し先の defarg 名を候補にします。
            const out: Suggestion[] = [];
            if (index) {
                const defargs = index.defargsOf(ctx.programName);
                if (defargs !== null) {
                    for (const [name, type] of defargs) {
                        out.push({ label: `${name} = `, kind: "arg", detail: `${name}: ${type}` });
                    }
                }
            }
            return [...out, ...varSuggestions(), ...callExprSuggestions()];
        }
        case "typeName":
            return catalog.valueTypes.map((t) => ({ label: t, kind: "type" as const }));
        case "triggerName":
            return TRIGGER_NAMES.map((t) => ({ label: t, kind: "trigger" as const }));
        case "eventKind":
            return catalog.eventKinds.map((k) => ({ label: k, kind: "eventKind" as const }));
        case "stringLiteralArg": {
            const rule = catalog.commands[ctx.callName]?.args[ctx.argName];
            if (rule?.enumValues) {
                return rule.enumValues.map((v) => ({ label: v, kind: "enumValue" as const }));
            }
            if (ctx.argName === "kind" && (ctx.callName === "target" || ctx.callName === "player" || ctx.callName === "exists")) {
                return catalog.targetKinds.map((k) => ({ label: k, kind: "enumValue" as const }));
            }
            return [];
        }
        case "statement": {
            const out: Suggestion[] = [];
            for (const k of [...STMT_KEYWORDS, ...DECL_KEYWORDS]) {
                out.push({ label: k, kind: "keyword" });
            }
            for (const name of Object.keys(catalog.commands)) {
                const spec = catalog.commands[name];
                const sig = Object.entries(spec.args)
                    .map(([n, r]) => `${n}${r.required ? "" : "?"}: ${r.kind.toLowerCase()}`)
                    .join(", ");
                out.push({ label: name, kind: "command", detail: `${name}(${sig})` });
            }
            return [...out, ...varSuggestions(), ...callExprSuggestions()];
        }
        case "expression":
            return [...varSuggestions(), ...worldVarSuggestions(), ...callExprSuggestions()];
    }
}
