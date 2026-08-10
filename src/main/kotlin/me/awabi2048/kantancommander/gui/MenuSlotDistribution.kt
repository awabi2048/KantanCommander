package me.awabi2048.kantancommander.gui

/**
 * コマンド選択はタブ間で画面が動かない固定6行です。MWMの8～14件一覧と同じく、
 * 本文の上下に1行ずつ余白を置き、左右1枠を除いた14枠を配置領域とします。
 */
internal object CommandPickerLayoutPolicy {
    const val SIZE = 54
    const val BACK_SLOT = 45
    val itemSlots: List<Int> = (19..25).toList() + (28..34).toList()
    val categorySlots: List<Int> = listOf(49, 51)
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
