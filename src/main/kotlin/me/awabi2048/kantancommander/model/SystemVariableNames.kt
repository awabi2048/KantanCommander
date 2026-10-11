package me.awabi2048.kantancommander.model

import java.util.Locale

/**
 * Kantan Commanderが実行コンテキストへ公開するシステム変数の名前を一元管理します。
 *
 * ユーザー変数は小文字で正規化して保存されるため、予約名の判定を各入力画面へ
 * 複製すると、API経由の定義や大文字入力だけがすり抜けます。予約名・参照名・
 * ユーザー変数名の境界をここへ集約し、保存、実行、式解析、GUIで同じ規則を使います。
 */
object SystemVariableNames {
    const val CURRENT_LOOP_COUNT = "CURRENT_LOOP_COUNT"

    val all: Set<String> = setOf(CURRENT_LOOP_COUNT)

    private val userNamePattern = Regex("[a-z][a-z0-9_.-]{0,63}")

    /**
     * ユーザーが宣言できる変数名の許容形です（参照・保存形式より狭い）。
     *
     * 保存形式はコンパイラ生成の一時変数 `dsl.tmpN`（`.`を含む）を受理する必要が
     * ありますが、`.` と `_` をユーザー名に許すと DSL では `-`・`.` と同じ
     * 識別子へ写像されて区別が付きません。GUI／外部入力から新規宣言できる名前は
     * `[a-z][a-z0-9-]` に限り、DSL テキストとの間で一意に往復します。
     */
    private val declarableNamePattern = Regex("[a-z][a-z0-9-]{0,63}")

    /** システム変数として正式な大文字名かを返します。 */
    fun isSystemName(name: String): Boolean = name in all

    /** 大文字・小文字を問わず、ユーザーが占有できない予約名かを返します。 */
    fun isReservedName(raw: String): Boolean =
        raw.trim().uppercase(Locale.ROOT) in all

    /** ユーザーが定義できる変数名かを返します（既存データの受理判定用の広い形）。 */
    fun isUserName(raw: String): Boolean =
        userNamePattern.matches(raw) && !isReservedName(raw)

    /**
     * GUI／外部入力から新規に宣言できる変数名かを返します。
     * DSL へ一意に往復できない `.`・`_` を含む名前はここで拒否します。
     */
    fun isDeclarableName(raw: String): Boolean =
        declarableNamePattern.matches(raw) && !isReservedName(raw)

    /** `${...}` の中に書ける、ユーザー変数またはシステム変数の名前かを返します。 */
    fun isReferenceName(name: String): Boolean =
        isSystemName(name) || isUserName(name)
}
