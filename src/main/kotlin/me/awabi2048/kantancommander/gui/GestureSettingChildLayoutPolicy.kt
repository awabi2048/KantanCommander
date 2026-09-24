package me.awabi2048.kantancommander.gui

import kotlin.math.floor

/**
 * 設定候補の子画面で、戻る操作・通常候補・下揃え候補の配置領域を分けます。
 *
 * 戻るボタンを先に固定してその上を本文領域として扱うことで、候補数が増減しても
 * 詳細設定や一時変数参照が戻る操作へ重ならない配置を保ちます。
 */
internal object GestureSettingChildLayoutPolicy {
    const val TOP_CHOICE_Y = 0.08
    const val CHOICE_PITCH = 0.12
    const val CHOICE_HEIGHT = 0.10
    const val BACK_Y = -0.43
    const val BACK_HEIGHT = 0.10
    const val FOOTER_GAP = 0.04
    const val GROUP_GAP = 0.02
    const val COLUMN_COUNT = 2

    data class Page(
        val index: Int,
        val count: Int,
        val topStart: Int,
        val topEndExclusive: Int,
        val topRowCount: Int,
        val bottomRowCount: Int,
        val topPageSize: Int,
    )

    fun plan(topChoiceCount: Int, bottomChoiceCount: Int, requestedPage: Int): Page {
        require(topChoiceCount >= 0) { "通常候補数は0以上である必要があります。" }
        require(bottomChoiceCount >= 0) { "下揃え候補数は0以上である必要があります。" }

        val bottomRows = rowsFor(bottomChoiceCount)
        val footerTop = BACK_Y + BACK_HEIGHT / 2.0
        val bottomRowLowestCenter = footerTop + FOOTER_GAP + CHOICE_HEIGHT / 2.0
        val highestBottomCenter = bottomRowLowestCenter + (bottomRows - 1).coerceAtLeast(0) * CHOICE_PITCH
        val minimumTopCenter = if (bottomRows > 0) {
            highestBottomCenter + CHOICE_HEIGHT + GROUP_GAP
        } else {
            footerTop + FOOTER_GAP + CHOICE_HEIGHT / 2.0
        }
        val topRowsPerPage = floor((TOP_CHOICE_Y - minimumTopCenter) / CHOICE_PITCH + 1.0e-9)
            .toInt() + 1
        require(topRowsPerPage > 0) {
            "下揃え候補が戻る領域または見出し領域へ入り、設定候補の配置を確保できません。"
        }
        val topPageSize = topRowsPerPage * COLUMN_COUNT
        val pageCount = ((topChoiceCount + topPageSize - 1) / topPageSize).coerceAtLeast(1)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        val topStart = page * topPageSize
        val topEnd = (topStart + topPageSize).coerceAtMost(topChoiceCount)

        return Page(
            index = page,
            count = pageCount,
            topStart = topStart,
            topEndExclusive = topEnd,
            topRowCount = rowsFor(topEnd - topStart),
            bottomRowCount = bottomRows,
            topPageSize = topPageSize,
        )
    }

    fun topY(row: Int): Double {
        require(row >= 0)
        return TOP_CHOICE_Y - row * CHOICE_PITCH
    }

    fun bottomY(row: Int, rowCount: Int): Double {
        require(row >= 0 && row < rowCount)
        val footerTop = BACK_Y + BACK_HEIGHT / 2.0
        val lowestCenter = footerTop + FOOTER_GAP + CHOICE_HEIGHT / 2.0
        return lowestCenter + (rowCount - 1 - row) * CHOICE_PITCH
    }

    private fun rowsFor(choiceCount: Int): Int = (choiceCount + COLUMN_COUNT - 1) / COLUMN_COUNT
}

/** 設定経路の親フレーム数から、物理的に開く子画面の識別子を決めます。 */
internal object GestureSettingChildScreenIds {
    const val ROOT = "gesture-editor-setting-child"

    fun depthForRouteSize(routeSize: Int): Int = (routeSize - 1).coerceAtLeast(1)

    fun forDepth(depth: Int): String {
        require(depth >= 1) { "設定子画面の深さは1以上である必要があります。" }
        return if (depth == 1) ROOT else "$ROOT:$depth"
    }

    fun forRouteSize(routeSize: Int): String = forDepth(depthForRouteSize(routeSize))
}
