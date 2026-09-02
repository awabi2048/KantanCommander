package me.awabi2048.kantancommander.gui

/**
 * ジェスチャー設定画面の現在値行を、既存の表示位置から計算します。
 *
 * 1行表示時の現在値アンカーと詳細案内アンカーの間隔を基準行高として再利用し、
 * 複数行表示では行高と行間の比率から行ピッチを算出します。詳細案内は最後の値行の
 * 下へ同じ行ピッチ分移動します。入力欄などの下限が指定された場合は、値行と詳細案内
 * をまとめて上へ移動し、説明・値・警告が他の操作領域へ重ならないようにします。
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
        rowGapRatio: Double = 0.0,
        minimumDetailY: Double? = null,
    ): GestureSettingValueRowsLayout {
        require(rowCount > 0) { "現在値行は1行以上必要です: $rowCount" }
        require(valueAnchorY > detailAnchorY) {
            "現在値アンカーは詳細案内アンカーより上に必要です: $valueAnchorY <= $detailAnchorY"
        }
        require(rowGapRatio >= 0.0 && rowGapRatio.isFinite()) {
            "行間率は0以上の有限値が必要です: $rowGapRatio"
        }
        require(minimumDetailY == null || minimumDetailY.isFinite()) {
            "詳細案内の下限位置は有限値が必要です: $minimumDetailY"
        }

        if (rowCount == 1) {
            val detailY = maxOf(detailAnchorY, minimumDetailY ?: detailAnchorY)
            val shift = detailY - detailAnchorY
            return GestureSettingValueRowsLayout(
                rowCentersY = listOf(valueAnchorY + shift),
                detailCenterY = detailY,
            )
        }

        // 行高を既存アンカー間の基準ピッチとみなし、その10%などを行間として加えます。
        // これにより「行間率」を座標へ直接埋め込まず、位置関係から一貫して算出できます。
        val baseRowHeight = (valueAnchorY - detailAnchorY) / (rowCount - 1)
        val rowGap = baseRowHeight * rowGapRatio
        val rowPitch = baseRowHeight + rowGap
        val unshiftedRowCenters = List(rowCount) { index -> valueAnchorY - rowPitch * index }
        val unshiftedDetailY = unshiftedRowCenters.last() - rowPitch
        val shift = maxOf(0.0, (minimumDetailY ?: unshiftedDetailY) - unshiftedDetailY)
        val rowCenters = unshiftedRowCenters.map { it + shift }
        return GestureSettingValueRowsLayout(
            rowCentersY = rowCenters,
            detailCenterY = unshiftedDetailY + shift,
        )
    }
}
