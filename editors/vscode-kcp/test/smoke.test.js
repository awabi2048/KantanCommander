// 実データ .kcp 全件を走査するスモークテストです。
// KCP_SMOKE_DIR 環境変数で対象ディレクトリを指定します。
const { test } = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const fs = require("node:fs");
const { analyze } = require("../out/analysis");
const { loadCatalogFrom } = require("../out/catalog");
const { WorkspaceIndex } = require("../out/workspace");

const DIR = process.env.KCP_SMOKE_DIR ||
    "D:\\Minecraft\\Chiyogami-26.2\\transported\\1011_programs\\structured-disks";

const catalog = loadCatalogFrom(path.join(__dirname, "..", "data", "dsl-catalog.json"));

test("実データ .kcp を全件解析する", (t) => {
    if (!fs.existsSync(DIR)) {
        t.skip(`対象ディレクトリがありません: ${DIR}`);
        return;
    }
    const files = fs.readdirSync(DIR).filter((f) => f.endsWith(".kcp"));
    assert.ok(files.length > 0, ".kcp ファイルが見つかりません");

    // ワークスペース索引を先に構築します（world.x 定義・プログラム名）。
    const index = new WorkspaceIndex();
    const texts = new Map();
    for (const f of files) {
        const text = fs.readFileSync(path.join(DIR, f), "utf-8");
        texts.set(f, text);
        index.update(f, text);
    }
    const worldTypes = index.worldTypes();
    const programNames = new Set(index.programNames().keys());

    const failures = [];
    const warnings = [];
    for (const [f, text] of texts) {
        const result = analyze(text, worldTypes, programNames, catalog);
        for (const d of result.diagnostics) {
            (d.severity === "error" ? failures : warnings).push(`${f}:${d.pos.line}:${d.pos.column} ${d.message}`);
        }
    }
    console.log(`parsed ${files.length} files: ${failures.length} errors, ${warnings.length} warnings`);
    for (const w of warnings.slice(0, 20)) console.log(`  warn ${w}`);
    if (failures.length > 0) {
        for (const f of failures.slice(0, 30)) console.log(`  FAIL ${f}`);
    }
    assert.equal(failures.length, 0, `${failures.length} files with errors`);
});
