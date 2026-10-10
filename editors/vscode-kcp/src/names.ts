/**
 * DSL の変数名とモデル変数名の相互写像です。
 *
 * `src/main/kotlin/me/awabi2048/kantancommander/dsl/DslNames.kt` の
 * 1:1 移植です。`_`→`-` 写像によりユーザー変数は `_` を含まない形へ揃い、
 * `_` を含むシステム変数（イベント引数等）と名前空間が分離されます。
 */

/** 実行時にモデル名そのままで用意されるシステム変数の DSL 名です。 */
const SYSTEM_NAMES = new Set([
    "event_actor",
    "event_block",
    "event_position",
    "event_target",
]);

/** DSL 名 → モデル名です。システム名はそのまま、ユーザー名は `_`→`-` です。 */
export function modelName(dsl: string): string {
    return SYSTEM_NAMES.has(dsl) ? dsl : dsl.replaceAll("_", "-");
}

/** モデル名 → DSL 名です（`-`→`_`、`.`→`_`）。 */
export function dslName(model: string): string {
    return model.replaceAll("-", "_").replaceAll(".", "_");
}
