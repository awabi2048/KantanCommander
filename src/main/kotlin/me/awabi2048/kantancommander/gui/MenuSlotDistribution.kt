package me.awabi2048.kantancommander.gui

import kotlin.math.ceil

/**
 * コマンド数が増えても最終行だけが極端に少なくならないよう、本文3行へ均等に分配します。
 * 各カテゴリは1行7個を基準とし、行内では9列全体の中央へ配置します。
 */
internal object CommandPickerSlotDistribution {
    private const val FIRST_CONTENT_ROW = 1
    private const val MAX_ITEMS_PER_ROW = 7
    private const val MAX_ROWS = 3

    fun slots(count: Int): List<Int> {
        require(count in 1..MAX_ITEMS_PER_ROW * MAX_ROWS)
        val rowCount = ceil(count.toDouble() / MAX_ITEMS_PER_ROW).toInt()
        val baseSize = count / rowCount
        val largerRows = count % rowCount
        val rowSizes = List(rowCount) { row -> baseSize + if (row < largerRows) 1 else 0 }

        return rowSizes.flatMapIndexed { row, rowSize ->
            val firstSlot = (FIRST_CONTENT_ROW + row) * 9 + (9 - rowSize) / 2
            (firstSlot until firstSlot + rowSize).toList()
        }
    }
}

/**
 * 1～4項目の設定画面で、左右の余白を保ちながら項目間隔を揃える配置です。
 * 5項目以上は意味上の組が分かる専用配置を使用し、この汎用規則へ押し込みません。
 */
internal object DistributedSettingSlots {
    fun slots(count: Int): List<Int> = when (count) {
        1 -> listOf(22)
        2 -> listOf(20, 24)
        3 -> listOf(19, 22, 25)
        4 -> listOf(19, 21, 23, 25)
        else -> error("5項目以上の設定には意味単位ごとの専用配置が必要です: count=$count")
    }
}
