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

    /** システム変数として正式な大文字名かを返します。 */
    fun isSystemName(name: String): Boolean = name in all

    /** 大文字・小文字を問わず、ユーザーが占有できない予約名かを返します。 */
    fun isReservedName(raw: String): Boolean =
        raw.trim().uppercase(Locale.ROOT) in all

    /** ユーザーが定義できる変数名かを返します。 */
    fun isUserName(raw: String): Boolean =
        userNamePattern.matches(raw) && !isReservedName(raw)

    /** `${...}` の中に書ける、ユーザー変数またはシステム変数の名前かを返します。 */
    fun isReferenceName(name: String): Boolean =
        isSystemName(name) || isUserName(name)
}
