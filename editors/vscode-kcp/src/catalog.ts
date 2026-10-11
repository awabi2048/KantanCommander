/**
 * 語彙カタログ（dsl-catalog.json）の型定義と読込みです。
 *
 * `dsl-catalog.json` は `./gradlew generateDslCatalog` が
 * Kotlin 側の語彙正本（DSL_COMMAND_TABLE・引数仕様・詳細値・
 * イベント引数・enum 群）から機械生成します。拡張機能側の語彙は
 * この JSON を読むだけで本体実装と同期します。
 */
import * as fs from "fs";
import * as path from "path";

export interface CatalogArgRule {
    kind: "NUMBER" | "STRING" | "BOOLEAN" | "ENTITY" | "LOCATION" | "ITEM" | "BLOCK" |
        "LITERAL" | "SLOT" | "PATH_PROPERTY" | "PATH_ATTRIBUTE" | "TAG_OP";
    required: boolean;
    enumValues?: string[];
}

export interface CatalogCommand {
    type: string;
    args: Record<string, CatalogArgRule>;
    extraParamPrefix?: string;
}

export interface CatalogCtorArg {
    type: string;
    required: boolean;
}

export interface CatalogValueCall {
    result: string;
    args: Record<string, CatalogCtorArg>;
}

export interface CatalogProperty {
    sources: string[];
    result: string;
    argType?: string;
}

export interface Catalog {
    keywords: string[];
    valueTypes: string[];
    specialPositions: string[];
    reservedNames: string[];
    gamemodeConstants: string[];
    targetKinds: string[];
    triggerSides: string[];
    triggerStates: string[];
    eventKinds: string[];
    varNamePattern: string;
    /** 数値関数の引数個数です（`pow`=2・三角関数=1、いずれも位置引数の number）。 */
    numericFunctions: Record<string, number>;
    maxCommandTimeSeconds: number;
    tickSeconds: number;
    commands: Record<string, CatalogCommand>;
    valueCalls: Record<string, CatalogValueCall>;
    eventArgs: Record<string, Record<string, string>>;
    properties: Record<string, CatalogProperty>;
    entityData: Record<string, string>;
}

let cached: Catalog | null = null;

/** 拡張機能の data/dsl-catalog.json を読み込みます。 */
export function loadCatalog(extensionPath?: string): Catalog {
    if (cached) return cached;
    const base = extensionPath ?? path.join(__dirname, "..");
    const file = path.join(base, "data", "dsl-catalog.json");
    cached = JSON.parse(fs.readFileSync(file, "utf-8")) as Catalog;
    return cached;
}

/** テスト用：外部ファイルから直接読み込みます（キャッシュは更新しません）。 */
export function loadCatalogFrom(file: string): Catalog {
    return JSON.parse(fs.readFileSync(file, "utf-8")) as Catalog;
}
