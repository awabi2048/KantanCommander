package me.awabi2048.kantancommander.dsl

import me.awabi2048.kantancommander.model.EventTriggerArguments

/**
 * DSL の変数名とモデル変数名の相互写像の単一正本です。
 *
 * DSL の変数名は小文字 snake_case（`[a-z][a-z0-9_]*`）に限定し、モデル側では
 * `_` を `-` へ置き換えます。モデルの命名規則 `[a-z][a-z0-9_.-]*` は `_` を
 * 許容するため、この写像でユーザー変数は `_` を含まない形へ揃い、`_` を
 * 含むシステム変数（イベント引数等）と名前空間が分離されます。
 *
 * イベント引数（`event_actor` 等）は実行時にモデル名そのままで定義済み
 * 変数として渡されるシステム名です。`_`→`-` 写像を掛けると参照が解決
 * できなくなるため、例外的に名をそのまま保持します。
 */
internal object DslNames {
    /** 実行時にモデル名そのままで用意されるシステム変数の DSL 名です。 */
    private val SYSTEM_NAMES = setOf(
        EventTriggerArguments.ACTOR,
        EventTriggerArguments.BLOCK,
        EventTriggerArguments.POSITION,
        EventTriggerArguments.TARGET,
    )

    /** DSL 名 → モデル名です。システム名はそのまま、ユーザー名は `_`→`-` です。 */
    fun modelName(dsl: String): String =
        if (dsl in SYSTEM_NAMES) dsl else dsl.replace('_', '-')

    /** モデル名 → DSL 名です（`-`→`_`、`.`→`_`）。 */
    fun dslName(model: String): String =
        model.replace('-', '_').replace('.', '_')
}
