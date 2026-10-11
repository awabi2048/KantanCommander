/**
 * .kcp（KantanCommander DSL）向け拡張機能のエントリポイントです。
 * 診断・補完・ホバーを提供します。語彙は ../data/dsl-catalog.json
 * （./gradlew generateDslCatalog 生成）を読み、プラグイン本体と同期します。
 */
import * as vscode from "vscode";
import { analyze } from "./analysis";
import { Catalog, loadCatalog } from "./catalog";
import { enclosingCallName, suggest, Suggestion } from "./suggest";
import { WorkspaceIndex } from "./workspace";

const LANG_ID = "kcp";

let catalog: Catalog;
const index = new WorkspaceIndex();
const diagnostics = vscode.languages.createDiagnosticCollection(LANG_ID);

export function activate(context: vscode.ExtensionContext) {
    try {
        catalog = loadCatalog(context.extensionPath);
    } catch {
        vscode.window.showErrorMessage("kcp: dsl-catalog.json の読み込みに失敗しました");
        return;
    }

    // ワークスペース内の既存 .kcp を索引します。
    vscode.workspace.findFiles("**/*.kcp").then((uris) => {
        void Promise.all(uris.map(async (uri) => {
            try {
                const doc = await vscode.workspace.openTextDocument(uri);
                index.update(uri.toString(), doc.getText());
            } catch {
                // 読み込めないファイルは索引から外します。
            }
        })).then(() => {
            for (const doc of vscode.workspace.textDocuments) {
                if (doc.languageId === LANG_ID) revalidate(doc);
            }
        });
    });

    for (const doc of vscode.workspace.textDocuments) {
        if (doc.languageId === LANG_ID) index.update(doc.uri.toString(), doc.getText());
    }

    context.subscriptions.push(
        vscode.workspace.onDidOpenTextDocument((doc) => {
            if (doc.languageId !== LANG_ID) return;
            index.update(doc.uri.toString(), doc.getText());
            revalidate(doc);
        }),
        vscode.workspace.onDidChangeTextDocument((e) => {
            if (e.document.languageId !== LANG_ID) return;
            index.update(e.document.uri.toString(), e.document.getText());
            revalidate(e.document);
        }),
        vscode.workspace.onDidCloseTextDocument((doc) => {
            if (doc.languageId !== LANG_ID) return;
            diagnostics.delete(doc.uri);
        }),
        vscode.workspace.onDidSaveTextDocument((doc) => {
            if (doc.languageId !== LANG_ID) return;
            // 保存で他ファイルの world.x/call 参照が変わり得るため全体を再検証します。
            index.update(doc.uri.toString(), doc.getText());
            for (const d of vscode.workspace.textDocuments) {
                if (d.languageId === LANG_ID) revalidate(d);
            }
        }),
        vscode.languages.registerCompletionItemProvider(
            { language: LANG_ID },
            new KcpCompletionProvider(),
            ".", "\"", "(", ",", "=", " ",
        ),
        vscode.languages.registerHoverProvider({ language: LANG_ID }, new KcpHoverProvider()),
        vscode.languages.registerDefinitionProvider({ language: LANG_ID }, new KcpDefinitionProvider()),
    );
}

export function deactivate() {
    diagnostics.dispose();
}

function toRange(doc: vscode.TextDocument, pos: { line: number; column: number }): vscode.Range {
    const line = Math.max(0, pos.line - 1);
    const col = Math.max(0, pos.column - 1);
    const lineLen = doc.lineAt(Math.min(line, doc.lineCount - 1)).text.length;
    return new vscode.Range(line, Math.min(col, lineLen), line, Math.min(col + 1, lineLen));
}

function revalidate(doc: vscode.TextDocument) {
    const result = analyze(
        doc.getText(),
        index.worldTypes(),
        new Set(index.programNames().keys()),
        catalog,
        (name) => index.defargsOf(name),
    );
    diagnostics.set(doc.uri, result.diagnostics.map((d) =>
        new vscode.Diagnostic(
            toRange(doc, d.pos),
            d.message,
            d.severity === "error" ? vscode.DiagnosticSeverity.Error : vscode.DiagnosticSeverity.Warning,
        ),
    ));
}

class KcpCompletionProvider implements vscode.CompletionItemProvider {
    provideCompletionItems(doc: vscode.TextDocument, position: vscode.Position): vscode.CompletionItem[] {
        const text = doc.getText();
        const offset = doc.offsetAt(position);
        return suggest(text, offset, catalog, index).map((s) => toItem(s));
    }
}

function toItem(s: Suggestion): vscode.CompletionItem {
    const item = new vscode.CompletionItem(s.label, kindFor(s.kind));
    item.detail = s.detail;
    return item;
}

function kindFor(kind: Suggestion["kind"]): vscode.CompletionItemKind {
    switch (kind) {
        case "keyword": return vscode.CompletionItemKind.Keyword;
        case "type": return vscode.CompletionItemKind.TypeParameter;
        case "command": return vscode.CompletionItemKind.Function;
        case "call": return vscode.CompletionItemKind.Method;
        case "arg": return vscode.CompletionItemKind.Field;
        case "variable": return vscode.CompletionItemKind.Variable;
        case "worldVar": return vscode.CompletionItemKind.Variable;
        case "constant": return vscode.CompletionItemKind.EnumMember;
        case "property": return vscode.CompletionItemKind.Property;
        case "program": return vscode.CompletionItemKind.File;
        case "trigger": return vscode.CompletionItemKind.Event;
        case "eventKind": return vscode.CompletionItemKind.EnumMember;
        case "enumValue": return vscode.CompletionItemKind.Value;
    }
}

class KcpHoverProvider implements vscode.HoverProvider {
    provideHover(doc: vscode.TextDocument, position: vscode.Position): vscode.Hover | null {
        const range = doc.getWordRangeAtPosition(position, /[a-zA-Z_][a-zA-Z0-9_.]*/);
        if (!range) return null;
        const word = doc.getText(range);
        const md = new vscode.MarkdownString();
        md.isTrusted = false;

        // `world.x` / `gamemode.x` の複合語を優先します。
        const text = doc.getText();
        const offset = doc.offsetAt(position);
        if (word === "world" || /\bworld\.[a-z0-9_]+$/.test(word)) {
            const member = word.startsWith("world.") ? word.slice(6) : null;
            if (member) {
                const dsl = member;
                const model = dsl.replaceAll("_", "-");
                const type = index.worldTypes().get(model);
                md.appendMarkdown(`**world.${dsl}**${type ? ` : ${type}` : ""}\n\nワールド変数です。`);
                return new vscode.Hover(md, range);
            }
            md.appendMarkdown("**world**\n\nワールド変数の名前空間です（`import world;` が必要）。");
            return new vscode.Hover(md, range);
        }
        if (word.startsWith("gamemode.")) {
            md.appendMarkdown(`**gamemode.${word.slice(9)}**\n\nゲームモード定数です。`);
            return new vscode.Hover(md, range);
        }

        // 命令・値コンストラクタのシグネチャです。
        const command = catalog.commands[word];
        if (command) {
            const args = Object.entries(command.args)
                .map(([n, r]) => `${n}${r.required ? "" : "?"}: ${r.kind.toLowerCase()}${r.enumValues ? ` (${r.enumValues.join("|")})` : ""}`)
                .join("\n  ");
            md.appendMarkdown(`**${word}** — 命令\n\n\`\`\`kcp\n${word}(\n  ${args}\n)\n\`\`\``);
            return new vscode.Hover(md, range);
        }
        const ctor = catalog.valueCalls[word];
        if (ctor) {
            const args = Object.entries(ctor.args)
                .map(([n, r]) => `${n}${r.required ? "" : "?"}: ${r.type}`)
                .join("\n  ");
            md.appendMarkdown(`**${word}** → ${ctor.result}\n\n\`\`\`kcp\n${word}(\n  ${args}\n)\n\`\`\``);
            return new vscode.Hover(md, range);
        }
        const prop = catalog.properties[word];
        if (prop) {
            md.appendMarkdown(`**.${word}** — ${prop.sources.join("/")} の詳細値 → ${prop.result}`);
            return new vscode.Hover(md, range);
        }
        if (catalog.valueTypes.includes(word)) {
            md.appendMarkdown(`**${word}** — 値型`);
            return new vscode.Hover(md, range);
        }
        if (["timer", "redstone", "event"].includes(word)) {
            md.appendMarkdown(`**trigger ${word}** — 実行契機`);
            return new vscode.Hover(md, range);
        }
        if (catalog.eventKinds.includes(word)) {
            const args = Object.entries(catalog.eventArgs[word] ?? {}).map(([n, t]) => `${n}: ${t}`).join(", ");
            md.appendMarkdown(`**${word}** — イベント契機\n\n導入変数: ${args}`);
            return new vscode.Hover(md, range);
        }
        if (catalog.targetKinds.includes(word)) {
            md.appendMarkdown(`**${word}** — 対象種別（target の kind）`);
            return new vscode.Hover(md, range);
        }
        // 直前が `(` のとき命令引数名の可能性を見ます。
        const call = enclosingCallName(text, offset);
        if (call) {
            const rule = catalog.commands[call]?.args[word] ?? catalog.valueCalls[call]?.args[word];
            if (rule) {
                const detail = "kind" in rule ? (rule as { kind: string }).kind : (rule as { type: string }).type;
                md.appendMarkdown(`**${word}** — ${call} の引数（${detail.toLowerCase()}）`);
                return new vscode.Hover(md, range);
            }
        }
        return null;
    }
}

/** `call "name"` と `world.x`・変数参照の定義ジャンプです。 */
class KcpDefinitionProvider implements vscode.DefinitionProvider {
    provideDefinition(doc: vscode.TextDocument, position: vscode.Position): vscode.Location[] | null {
        const range = doc.getWordRangeAtPosition(position, /"([^"]*)"/);
        if (!range) return null;
        const word = doc.getText(range).replace(/"/g, "");
        const line = doc.lineAt(position.line).text;
        if (/\bcall\s+"/.test(line.slice(0, position.character))) {
            const uri = index.programNames().get(word);
            if (uri === undefined) return null;
            return [new vscode.Location(vscode.Uri.parse(uri), new vscode.Position(0, 0))];
        }
        return null;
    }
}
