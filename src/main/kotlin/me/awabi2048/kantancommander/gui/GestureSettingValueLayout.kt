package me.awabi2048.kantancommander.gui

/**
 * ジェスチャー設定画面の現在値行を、既存の表示位置から計算します。
 *
 * 1行表示時の現在値アンカーと詳細案内アンカーの間隔を行ピッチとして再利用し、
 * 複数行表示では同じ領域へ均等配置します。詳細案内は最後の値行の下へ1ピッチ分
 * 移動するため、値行数を増やしても説明・値・警告が同じ座標へ重なりません。
 */
internal data class GestureSettingValueRowsLayout(
    val rowCentersY: List<Double>,
    val detailCenterY: Double,
)

internal object GestureSettingValueLayout {
    fun calculate(
        rowCount: Int,
        valueAnchorY: Double,
        detailAnchorY: Double,
    ): GestureSettingValueRowsLayout {
        require(rowCount > 0) { "現在値行は1行以上必要です: $rowCount" }
        require(valueAnchorY > detailAnchorY) {
            "現在値アンカーは詳細案内アンカーより上に必要です: $valueAnchorY <= $detailAnchorY"
        }

        if (rowCount == 1) {
            return GestureSettingValueRowsLayout(
                rowCentersY = listOf(valueAnchorY),
                detailCenterY = detailAnchorY,
            )
        }

        val rowPitch = (valueAnchorY - detailAnchorY) / (rowCount - 1)
        val rowCenters = List(rowCount) { index -> valueAnchorY - rowPitch * index }
        return GestureSettingValueRowsLayout(
            rowCentersY = rowCenters,
            detailCenterY = rowCenters.last() - rowPitch,
        )
    }
}
