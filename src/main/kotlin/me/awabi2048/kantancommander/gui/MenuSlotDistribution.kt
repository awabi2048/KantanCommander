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
internal data class ChoiceMenuLayout(val size: Int, val itemSlots: List<Int>, val backSlot: Int)

internal object ChoiceMenuLayoutPolicy {
    fun layout(count: Int): ChoiceMenuLayout = when (count) {
        1 -> ChoiceMenuLayout(45, listOf(22), 36)
        2 -> ChoiceMenuLayout(45, listOf(20, 24), 36)
        3 -> ChoiceMenuLayout(45, listOf(20, 22, 24), 36)
        in 4..7 -> ChoiceMenuLayout(45, leftPackedSlots(count, firstRow = 2, maxRows = 1), 36)
        in 8..14 -> ChoiceMenuLayout(54, leftPackedSlots(count, firstRow = 2, maxRows = 2), 45)
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
