package me.awabi2048.kantancommander.gui

/** 右下の対象分類カードの1つ分の横方向の配置です。 */
internal data class GestureTargetChoiceLayoutSlot(
    val centerX: Double,
    val width: Double,
)

/**
 * 既存の4スロット領域を、対象分類2択のために前半2スロット・後半2スロットへ
 * 結合するレイアウト規則です。元の外側の境界とスロット間の余白を維持することで、
 * 選択肢数を減らしても右ペイン内の視覚的な基準位置を変えません。
 */
internal object GestureTargetChoiceLayoutPolicy {
    const val SLOT_COUNT = 4
    const val SLOTS_PER_CHOICE = 2
    const val CHOICE_COUNT = SLOT_COUNT / SLOTS_PER_CHOICE
    const val SPAN_START_X = -0.43
    const val SPAN_END_X = 1.00
    const val GAP = 0.04

    fun slots(choiceCount: Int): List<GestureTargetChoiceLayoutSlot> {
        require(choiceCount in 0..CHOICE_COUNT) {
            "対象分類カード数は0～${CHOICE_COUNT}件である必要があります: count=$choiceCount"
        }
        val span = SPAN_END_X - SPAN_START_X
        val singleSlotWidth = (span - GAP * (SLOT_COUNT - 1)) / SLOT_COUNT
        // 前後2スロットをカードへ含めるため、ペア内部の余白もカード幅へ含めます。
        val choiceWidth = singleSlotWidth * SLOTS_PER_CHOICE + GAP * (SLOTS_PER_CHOICE - 1)
        val pitch = choiceWidth + GAP
        return List(choiceCount) { index ->
            val left = SPAN_START_X + index * pitch
            GestureTargetChoiceLayoutSlot(
                centerX = left + choiceWidth / 2.0,
                width = choiceWidth,
            )
        }
    }
}
