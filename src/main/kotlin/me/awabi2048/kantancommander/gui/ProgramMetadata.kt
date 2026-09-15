package me.awabi2048.kantancommander.gui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * issue #13 のプログラム所有者・作成情報の表示正本です。
 *
 * 一覧・アイテム lore・注視表示で同じ書式を使うため、ここに集約します。
 * 日時はサーバーの既定タイムゾーンで統一し、表示ごとに異なる解釈をしません。
 * 「作成者」と「現在所有者」の分離は行いません。所有権の移転機能がなく、
 * 分離しても表示以外の用途がないためです。移転機能の追加時に再検討します。
 */
object ProgramMetadata {
    private val CREATED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    /** 作成日時を `yyyy/MM/dd HH:mm` で表します。 */
    fun formatCreatedAt(createdAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        CREATED_AT_FORMAT.format(Instant.ofEpochMilli(createdAtMillis).atZone(zone))

    /**
     * 所有者の表示名を解決します。名前が分からない場合は UUID の先頭8文字を
     * 示し、空欄にはしません。
     */
    fun ownerName(owner: UUID, lookup: (UUID) -> String? = { null }): String =
        lookup(owner)?.takeIf(String::isNotBlank) ?: owner.toString().take(8)
}
