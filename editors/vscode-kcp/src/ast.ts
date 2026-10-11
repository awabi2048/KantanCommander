/**
 * `.kcp` DSL の構文木型です。
 *
 * `src/main/kotlin/me/awabi2048/kantancommander/dsl/DslAst.kt` の
 * 1:1 移植です。Kotlin の sealed class は `kind` タグの
 * discriminated union として表現します。
 *
 * 位置は Kotlin 側と同じく行・桁ともに 1 始まりです（VSCode API 側で
 * 0 始まりへ変換します）。
 */

export interface DslPos {
    line: number;
    column: number;
}

export function posToString(pos: DslPos): string {
    return `${pos.line}:${pos.column}`;
}

/** 値型です。モデルの `ValueType` に対応します。 */
export type ValueTypeName =
    | "number" | "string" | "boolean" | "location" | "item" | "block" | "entity";

export const VALUE_TYPES: readonly ValueTypeName[] = [
    "number", "string", "boolean", "location", "item", "block", "entity",
];

/** 座標成分のセマンティクスです（`CoordinateSemantics`）。 */
export type CoordinateSemantics = "ABSOLUTE" | "RELATIVE" | "LOCAL";

// ---- プログラム ----

export interface DslProgram {
    kind: "program";
    name: string;
    decls: DslDecl[];
    body: DslStmt[];
    pos: DslPos;
    /** 文の pos をキーとする所属コメント一覧です。 */
    comments: Map<string, string[]>;
}

// ---- 宣言部 ----

export type DslDecl = DslImportWorld | DslTriggerDecl | DslDefarg;

export interface DslImportWorld {
    kind: "importWorld";
    pos: DslPos;
}

export interface DslTriggerDecl {
    kind: "triggerDecl";
    spec: DslCallExpr;
    pos: DslPos;
}

export interface DslDefarg {
    kind: "defarg";
    name: string;
    type: ValueTypeName;
    pos: DslPos;
}

// ---- 文 ----

export type DslStmt =
    | DslVarDecl
    | DslAssign
    | DslIf
    | DslRepeat
    | DslBreak
    | DslContinue
    | DslReturn
    | DslCallStmt
    | DslCallDisk
    | DslDelete;

export interface DslVarDecl {
    kind: "varDecl";
    /** 空文字列は GUI の未命名スロット（`var null`）を表します。 */
    name: string;
    type: ValueTypeName;
    value: DslExpr;
    world: boolean;
    pos: DslPos;
}

export type DslAssignTarget =
    | { kind: "variable"; name: string; pos: DslPos }
    | { kind: "worldVariable"; name: string; pos: DslPos }
    | { kind: "entityVariable"; entity: DslExpr; name: string; pos: DslPos };

export type DslAssignOp = "ASSIGN" | "ADD" | "SUB" | "MUL" | "DIV" | "MOD";

export interface DslAssign {
    kind: "assign";
    target: DslAssignTarget;
    op: DslAssignOp;
    value: DslExpr;
    pos: DslPos;
}

export interface DslIf {
    kind: "if";
    condition: DslExpr;
    thenBody: DslStmt[];
    elseBody: DslStmt[] | null;
    pos: DslPos;
}

export interface DslRepeat {
    kind: "repeat";
    count: DslExpr;
    body: DslStmt[];
    pos: DslPos;
}

export interface DslBreak {
    kind: "break";
    pos: DslPos;
}

export interface DslContinue {
    kind: "continue";
    pos: DslPos;
}

export interface DslReturn {
    kind: "return";
    value: DslExpr | null;
    pos: DslPos;
}

export interface DslArg {
    /** null の位置引数は単一必須引数の命令のみ許します。 */
    name: string | null;
    value: DslExpr;
    pos: DslPos;
}

export interface DslCallStmt {
    kind: "callStmt";
    name: string;
    args: DslArg[];
    pos: DslPos;
}

/** `call "disk";` / `call "disk"(arg = 値, ...);` の呼出し文です。 */
export interface DslCallDisk {
    kind: "callDisk";
    diskId: DslStringLiteral;
    args: DslArg[];
    pos: DslPos;
}

export interface DslDelete {
    kind: "delete";
    target: DslAssignTarget;
    pos: DslPos;
}

// ---- 式 ----

export type DslExpr =
    | DslNumberLiteral
    | DslStringLiteral
    | DslBooleanLiteral
    | DslNull
    | DslVarRef
    | DslWorldRef
    | DslConst
    | DslUnary
    | DslBinary
    | DslPropertyRead
    | DslCallExpr
    | DslCallResult
    | DslSpecialPosition
    | DslLoopCount
    | DslPositionLiteral
    | DslComponentExpr;

export interface DslNumberLiteral {
    kind: "number";
    value: number;
    pos: DslPos;
}

export type DslStringPart =
    | { kind: "text"; text: string }
    | { kind: "interp"; expr: DslExpr };

export interface DslStringLiteral {
    kind: "string";
    parts: DslStringPart[];
    pos: DslPos;
}

/** 補間を含まないときだけ文字列値を返します。 */
export function stringLiteralPlainText(lit: DslStringLiteral): string | null {
    const texts: string[] = [];
    for (const p of lit.parts) {
        if (p.kind !== "text") return null;
        texts.push(p.text);
    }
    return texts.join("");
}

export interface DslBooleanLiteral {
    kind: "boolean";
    value: boolean;
    pos: DslPos;
}

/** `null` です。spec 引数・if 条件・変数名・変数値の「未設定」を表します。 */
export interface DslNull {
    kind: "null";
    pos: DslPos;
}

export interface DslVarRef {
    kind: "varRef";
    name: string;
    pos: DslPos;
}

export interface DslWorldRef {
    kind: "worldRef";
    name: string;
    pos: DslPos;
}

export interface DslConst {
    kind: "const";
    group: string;
    name: string;
    pos: DslPos;
}

export type DslUnaryOp = "NOT" | "NEG";

export interface DslUnary {
    kind: "unary";
    op: DslUnaryOp;
    operand: DslExpr;
    pos: DslPos;
}

export type DslBinaryOp =
    | "ADD" | "SUB" | "MUL" | "DIV" | "MOD"
    | "EQ" | "NE" | "LT" | "LE" | "GT" | "GE" | "AND" | "OR";

export function binaryOpSymbol(op: DslBinaryOp): string {
    switch (op) {
        case "ADD": return "+";
        case "SUB": return "-";
        case "MUL": return "*";
        case "DIV": return "/";
        case "MOD": return "%";
        case "EQ": return "==";
        case "NE": return "!=";
        case "LT": return "<";
        case "LE": return "<=";
        case "GT": return ">";
        case "GE": return ">=";
        case "AND": return "&&";
        case "OR": return "||";
    }
}

export interface DslBinary {
    kind: "binary";
    op: DslBinaryOp;
    left: DslExpr;
    right: DslExpr;
    pos: DslPos;
}

export interface DslPropertyRead {
    kind: "propertyRead";
    source: DslExpr;
    name: string;
    arg: DslExpr | null;
    pos: DslPos;
}

export interface DslCallExpr {
    kind: "callExpr";
    name: string;
    args: DslArg[];
    pos: DslPos;
}

/**
 * `x = call "disk"(...)` の式位置の呼出しです。代入・変数宣言の右辺でのみ
 * 使用でき、式の型は呼出し先の戻り値に従います（この層では型を確定しません）。
 */
export interface DslCallResult {
    kind: "callResult";
    diskId: DslStringLiteral;
    args: DslArg[];
    pos: DslPos;
}

export interface DslSpecialPosition {
    kind: "specialPosition";
    name: string;
    pos: DslPos;
}

export interface DslLoopCount {
    kind: "loopCount";
    pos: DslPos;
}

export interface DslPositionComponent {
    semantics: CoordinateSemantics;
    value: number;
}

export interface DslPositionLiteral {
    kind: "positionLiteral";
    base: DslExpr | null;
    components: DslPositionComponent[];
    pos: DslPos;
}

export interface DslComponentExpr {
    kind: "componentExpr";
    semantics: CoordinateSemantics;
    value: number;
    pos: DslPos;
}
