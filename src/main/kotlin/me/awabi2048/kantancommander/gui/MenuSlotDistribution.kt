package me.awabi2048.kantancommander.gui

/**
 * MWMの一覧系画面と同様に、左右1枠を余白として残した7列を上段左から順に埋めます。
 * 件数によって既存アイコンの位置を動かさず、追加時の視線移動も左から右、上から下に固定します。
 */
internal object CommandPickerSlotDistribution {
    private const val FIRST_CONTENT_ROW = 1
    private const val MAX_ITEMS_PER_ROW = 7
    private const val MAX_ROWS = 3

    fun slots(count: Int): List<Int> {
        require(count in 1..MAX_ITEMS_PER_ROW * MAX_ROWS)
        return leftPackedSlots(count, FIRST_CONTENT_ROW, MAX_ROWS)
    }
}

/**
 * MWMのChoice画面に合わせ、3択以下は中央対置し、4択以上は本文2行へ左詰めします。
 * 確認画面の対置と、候補一覧の走査順を混同しないための画面ファミリー別規則です。
 */
internal object ChoiceMenuSlotDistribution {
    fun slots(count: Int): List<Int> = when (count) {
        1 -> listOf(22)
        2 -> listOf(20, 24)
        3 -> listOf(20, 22, 24)
        in 4..14 -> leftPackedSlots(count, firstRow = 2, maxRows = 2)
        else -> error("Choice画面の候補数は1～14件である必要があります: count=$count")
    }
}

/**
 * 設定一覧はMWMと同様に本文左端から詰めます。5項目以上は意味グループを持つため、
 * 汎用規則へ押し込まずコマンド専用の二次元配置を使用します。
 */
internal object DistributedSettingSlots {
    fun slots(count: Int): List<Int> {
        require(count in 1..4) { "5項目以上の設定には意味単位ごとの専用配置が必要です: count=$count" }
        return (19 until 19 + count).toList()
    }
}

private fun leftPackedSlots(count: Int, firstRow: Int, maxRows: Int): List<Int> {
    require(count in 1..7 * maxRows)
    return (0 until count).map { index ->
        val row = firstRow + index / 7
        val column = 1 + index % 7
        row * 9 + column
    }
}
