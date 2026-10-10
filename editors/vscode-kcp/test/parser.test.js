// DslParser 移植の回帰テストです。
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { parse } = require("../out/parser");
const { DslParseError } = require("../out/lexer");

test("最小プログラムを読む", () => {
    const p = parse('program "test";');
    assert.equal(p.kind, "program");
    assert.equal(p.name, "test");
    assert.deepEqual(p.decls, []);
    assert.deepEqual(p.body, []);
});

test("program で始まらない入力はエラー", () => {
    assert.throws(() => parse("var x: number = 1;"), DslParseError);
});

test("宣言部を読む", () => {
    const p = parse(`program "t";
        import world;
        trigger timer(interval = 20);
        defarg who: entity;
        var x: number = 1;
    `);
    assert.equal(p.decls.length, 3);
    assert.equal(p.decls[0].kind, "importWorld");
    assert.equal(p.decls[1].kind, "triggerDecl");
    assert.equal(p.decls[1].spec.name, "timer");
    assert.equal(p.decls[1].spec.args[0].name, "interval");
    assert.equal(p.decls[2].kind, "defarg");
    assert.equal(p.decls[2].name, "who");
    assert.equal(p.decls[2].type, "entity");
});

test("trigger redstone を裸識別子で読む", () => {
    const p = parse('program "t";\ntrigger redstone;');
    assert.equal(p.decls[0].spec.name, "redstone");
    assert.deepEqual(p.decls[0].spec.args, []);
});

test("変数宣言・代入・複合代入を読む", () => {
    const p = parse(`program "t";
        var n: number = 0;
        n += 3;
        world.score = n;
        target(kind = "nearest").vars.hp = 10;
    `);
    assert.equal(p.body[0].kind, "varDecl");
    assert.equal(p.body[1].kind, "assign");
    assert.equal(p.body[1].op, "ADD");
    assert.equal(p.body[2].target.kind, "worldVariable");
    assert.equal(p.body[3].target.kind, "entityVariable");
    assert.equal(p.body[3].target.name, "hp");
});

test("if/else・repeat・終端文を読む", () => {
    const p = parse(`program "t";
        if (x > 0) { return; } else if (y) { return 1; } else { break; }
        repeat (3) { continue; }
    `);
    const ifStmt = p.body[0];
    assert.equal(ifStmt.kind, "if");
    assert.equal(ifStmt.thenBody[0].kind, "return");
    assert.equal(ifStmt.elseBody[0].kind, "if");
    assert.equal(ifStmt.elseBody[0].elseBody[0].kind, "break");
    assert.equal(p.body[1].kind, "repeat");
});

test("演算子優先順位", () => {
    const p = parse('program "t";\nvar x: boolean = 1 + 2 * 3 == 7 && !false;');
    const value = p.body[0].value;
    assert.equal(value.kind, "binary");
    assert.equal(value.op, "AND");
    assert.equal(value.left.op, "EQ");
    assert.equal(value.left.left.op, "ADD");
    assert.equal(value.left.left.right.op, "MUL");
});

test("位置リテラルを読む", () => {
    const p = parse('program "t";\nvar p: location = ~ ~1 ~-2.5;\nvar q: location = base ~ ^0 1;\nvar y: number = ~90;');
    const lit = p.body[0].value;
    assert.equal(lit.kind, "positionLiteral");
    assert.equal(lit.base, null);
    assert.equal(lit.components[1].value, 1);
    assert.equal(lit.components[2].value, -2.5);
    const based = p.body[1].value;
    assert.equal(based.kind, "positionLiteral");
    assert.equal(based.base.kind, "varRef");
    assert.equal(based.components[0].semantics, "RELATIVE");
    assert.equal(based.components[1].semantics, "LOCAL");
    const comp = p.body[2].value;
    assert.equal(comp.kind, "componentExpr");
    assert.equal(comp.value, 90);
});

test("位置リテラルは3成分必須", () => {
    assert.throws(() => parse('program "t";\nvar p: location = ~ ~1;'), DslParseError);
});

test("呼出しと引数を読む", () => {
    const p = parse(`program "t";
        teleport(e, to = ~ ~ ~);
        target(kind = "all_players", sort = "nearest");
        exists(kind = "zombie", r = 5);
    `);
    assert.equal(p.body[0].kind, "callStmt");
    assert.equal(p.body[0].args[0].name, null);
    assert.equal(p.body[0].args[1].name, "to");
    assert.equal(p.body[1].args[0].name, "kind");
});

test("位置引数は名前付きの前のみ", () => {
    assert.throws(
        () => parse('program "t";\ntarget(sort = "x", "y");'),
        DslParseError,
    );
});

test("call と del を読む", () => {
    const p = parse(`program "t";
        call "other-program";
        del world.score;
        del e.vars.flag;
    `);
    assert.equal(p.body[0].kind, "callDisk");
    assert.equal(p.body[0].diskId.kind, "string");
    assert.equal(p.body[1].target.kind, "worldVariable");
    assert.equal(p.body[2].target.kind, "entityVariable");
});

test("コメントが文へ所属する", () => {
    const p = parse(`program "t";
        // leading
        var x: number = 1; // trailing
        // before repeat
        repeat (2) {
            // inside
            break;
        }
    `);
    const commentsOf = (s) => p.comments.get(`${s.pos.line}:${s.pos.column}`) ?? [];
    assert.deepEqual(commentsOf(p.body[0]), ["leading", "trailing"]);
    assert.deepEqual(commentsOf(p.body[1]), ["before repeat"]);
    assert.deepEqual(commentsOf(p.body[1].body[0]), ["inside"]);
});

test("null を読む", () => {
    const p = parse('program "t";\nvar l: location = null;\nif (null) { }\n');
    assert.equal(p.body[0].value.kind, "null");
    assert.equal(p.body[1].condition.kind, "null");
});

test("プロパティ・loop.count・特殊位置を読む", () => {
    const p = parse(`program "t";
        var x: number = e.health;
        var y: number = loop.count;
        var z: location = disk;
        var w: string = v.data("key");
    `);
    assert.equal(p.body[0].value.kind, "propertyRead");
    assert.equal(p.body[1].value.kind, "loopCount");
    assert.equal(p.body[2].value.kind, "specialPosition");
    assert.equal(p.body[3].value.name, "data");
});
