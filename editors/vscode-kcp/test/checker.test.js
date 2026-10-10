// Checker 移植＋カタログ照合の回帰テストです。
const { test } = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const { analyze } = require("../out/analysis");
const { loadCatalogFrom } = require("../out/catalog");

const catalog = loadCatalogFrom(path.join(__dirname, "..", "data", "dsl-catalog.json"));
const ok = (text, worldTypes) => analyze(text, worldTypes, new Set(), catalog).diagnostics;
const err = (text, worldTypes) => ok(text, worldTypes).filter((d) => d.severity === "error");

test("最小プログラムは正常", () => {
    assert.deepEqual(ok('program "t";'), []);
});

test("未定義変数を検出する", () => {
    const d = err('program "t";\nvar x: number = missing;');
    assert.equal(d.length, 1);
    assert.match(d[0].message, /未定義の変数 'missing'/);
});

test("型不一致を検出する", () => {
    const d = err('program "t";\nvar s: string = 3;');
    assert.match(d[0].message, /string 型ですが、number 型/);
});

test("null は宣言・代入・条件で有効だが式では無効", () => {
    assert.deepEqual(err('program "t";\nvar l: location = null;\nvar n: number = 1;\nn = null;'), []);
    assert.match(err('program "t";\nvar n: number = null + 1;')[0].message, /null は値の位置では使えません/);
});

test("import world 無しで world 参照を拒否する", () => {
    const d = err('program "t";\nvar x: number = world.score;');
    assert.match(d[0].message, /import world;/);
});

test("world.x の定義と参照", () => {
    assert.deepEqual(err(`program "t";
        import world;
        var world.score: number = 0;
        var x: number = world.score;
        world.score += 1;
        del world.score;`), []);
});

test("外部定義の world 変数を解決する", () => {
    const worldTypes = new Map([["my-score", "number"]]);
    assert.deepEqual(err('program "t";\nimport world;\nvar x: number = world.my_score;', worldTypes), []);
    assert.match(err('program "t";\nimport world;\nvar x: number = world.other;', worldTypes)[0].message, /未定義のワールド変数/);
});

test("未知の命令を拒否する", () => {
    const d = err('program "t";\nfly(speed = 1);');
    assert.match(d[0].message, /未対応の命令 'fly'/);
});

test("必須引数の欠落を検出する", () => {
    // wait は独自検査が先に走るため、それ以外の必須引数を持つ命令を使います。
    const withRequired = Object.keys(catalog.commands).find((c) =>
        c !== "wait" && Object.values(catalog.commands[c].args).some((a) => a.required));
    const d = err(`program "t";\n${withRequired}();`);
    assert.match(d[0].message, /必須の引数/);
});

test("未知の引数名を拒否する", () => {
    const d = err('program "t";\nwait(foo = 1);');
    assert.match(d[0].message, /seconds/);
    const other = Object.keys(catalog.commands).find((c) =>
        c !== "wait" && catalog.commands[c].extraParamPrefix === undefined);
    const d2 = err(`program "t";\n${other}(zzz = 1);`);
    assert.match(d2[0].message, /'zzz' はありません/);
});

test("enum 引数の値を検証する", () => {
    const entry = Object.entries(catalog.commands).find(([, s]) =>
        Object.values(s.args).some((a) => a.kind === "LITERAL" && a.enumValues));
    assert.ok(entry);
    const [name, spec] = entry;
    const litArg = Object.entries(spec.args).find(([, a]) => a.kind === "LITERAL" && a.enumValues)[0];
    const d = err(`program "t";\n${name}(${litArg} = "nonexistent");`);
    assert.match(d[0].message, /いずれかです/);
});

test("wait の引数を検証する", () => {
    assert.deepEqual(err('program "t";\nwait(0.5);'), []);
    assert.deepEqual(err('program "t";\nwait(seconds = 0.05);'), []);
    assert.match(err('program "t";\nwait(0.03);')[0].message, /0\.05 刻み/);
    assert.match(err('program "t";\nwait(1, 2);')[0].message, /1個だけ/);
});

test("trigger を検証する", () => {
    assert.deepEqual(err('program "t";\ntrigger timer(interval = 20);'), []);
    assert.deepEqual(err('program "t";\ntrigger redstone;'), []);
    assert.match(err('program "t";\ntrigger timer(interval = 0.03);')[0].message, /範囲外/);
    assert.match(err('program "t";\ntrigger redstone;\ntrigger timer(interval = 20);')[0].message, /1つだけ/);
});

test("trigger event でイベント引数が定義済みになる", () => {
    const kind = catalog.eventKinds[0];
    const argNames = Object.keys(catalog.eventArgs[kind]);
    assert.ok(argNames.length > 0);
    const use = argNames[0];
    const type = catalog.eventArgs[kind][use];
    assert.deepEqual(err(`program "t";\ntrigger event(kind = "${kind}");\nvar v: ${type} = ${use};`), []);
});

test("break/continue は repeat 内のみ", () => {
    assert.deepEqual(err('program "t";\nrepeat (3) { if (true) { break; } continue; }'), []);
    assert.match(err('program "t";\nbreak;')[0].message, /repeat の内側/);
    assert.match(err('program "t";\ncontinue;')[0].message, /repeat の内側/);
    assert.match(err('program "t";\nif (true) { break; }')[0].message, /repeat の内側/);
});

test("終端文の後は到達不能", () => {
    assert.match(err('program "t";\nreturn;\nvar x: number = 1;')[0].message, /到達できません/);
    assert.deepEqual(err('program "t";\nif (true) { return; } else { return; }\nvar x: number = 1;'), []);
});

test("loop.count は repeat 内のみ", () => {
    assert.deepEqual(err('program "t";\nrepeat (3) { var i: number = loop.count; }'), []);
    assert.match(err('program "t";\nvar i: number = loop.count;')[0].message, /repeat の内側/);
});

test("式型の検証", () => {
    assert.deepEqual(err('program "t";\nif (1 > 0 && 2 != 3) { }'), []);
    assert.match(err('program "t";\nif (1 > "a") { }')[0].message, /使えません/);
    assert.match(err('program "t";\nvar n: number = 1 % 2;')[0].message, /剰余演算/);
    assert.match(err('program "t";\nvar b: boolean = !1;')[0].message, /boolean 型へのみ/);
});

test("詳細値の検証", () => {
    const prop = Object.keys(catalog.properties)[0];
    const def = catalog.properties[prop];
    const src = def.sources[0];
    assert.deepEqual(err(`program "t";\nvar e: ${src} = null;\nvar v: ${def.result} = e.${prop};`), []);
    assert.match(err('program "t";\nvar e: entity = null;\nvar v: number = e.zzz;')[0].message, /不明な詳細値/);
});

test("target/条件呼出しを検証する", () => {
    const kind = catalog.targetKinds[0];
    assert.deepEqual(err(`program "t";\nvar e: entity = target(kind = "${kind}");`), []);
    assert.match(err('program "t";\nvar e: entity = target();')[0].message, /kind/);
    assert.match(err(`program "t";\nvar e: entity = target(kind = "ghost");`)[0].message, /kind が不明/);
    assert.match(err('program "t";\nif (player()) { }')[0].message, /対象指定/);
});

test("call の対象を検証する", () => {
    const uuid = "12345678-1234-1234-1234-123456789abc";
    assert.deepEqual(ok(`program "t";\ncall "${uuid}";`), []);
    const self = err('program "t";\ncall "t";');
    assert.match(self[0].message, /循環参照/);
    const missing = analyze('program "t";\ncall "other";', new Map(), new Set(), catalog)
        .diagnostics.filter((d) => d.severity === "warning");
    assert.match(missing[0].message, /見つかりません/);
});

test("gamemode 定数と特殊位置", () => {
    assert.deepEqual(err('program "t";\nvar m: string = gamemode.survival;\nvar d: location = disk;'), []);
    assert.throws(() => require("../out/parser").parse('program "t";\nvar m: string = gamemode.hardcore;'));
});

test("変数名の規則", () => {
    assert.match(err('program "t";\nvar Foo: number = 1;')[0].message, /snake_case/);
    assert.match(err('program "t";\nvar loop: number = 1;')[0].message, /予約された名前/);
    assert.deepEqual(err('program "t";\nvar null: number = 1;'), []);
});
