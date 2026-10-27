package me.awabi2048.kantancommander.gui

import kotlin.math.floor

/**
 * 設定候補カードのページ分割・縦配置を親画面（右ペイン）と子画面で共通に決めます。
 *
 * 下端の操作行を先に固定してその上を本文領域として扱うことで、候補数が増減しても
 * 詳細設定や一時変数参照が戻る操作へ重ならない配置を保ちます。
 * 操作行はページャーが左列・戻るが右列の範囲に固定し、ページ数や件数で変えません。
 * X方向の寸法は [GestureChoicePane] が画面区分ごとに持ちます。
 */
internal object GestureSettingChildLayoutPolicy {
    const val TOP_CHOICE_Y = 0.08
    const val CHOICE_PITCH = 0.12
    const val CHOICE_HEIGHT = 0.10
    const val BACK_Y = -0.43
    const val BACK_HEIGHT = 0.10
    const val FOOTER_GAP = 0.04
    const val GROUP_GAP = 0.02
    const val GROUP_DIVIDER_HEIGHT = 0.008
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
        val planned = com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiPagination.plan(
            topChoiceCount, topRowsPerPage, COLUMN_COUNT, requestedPage,
        )
        val topPageSize = planned.pageSize
        val pageCount = planned.count
        val page = planned.index
        val topStart = planned.start
        val topEnd = planned.endExclusive

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

    /**
     * 上段候補と下揃え候補の境界線を表示するかどうかです。
     * 両方のグループが同時に存在する画面だけに置き、片方だけの画面や
     * ページ移動で出し入れしません。
     */
    fun showsGroupDivider(topChoiceCount: Int, bottomChoiceCount: Int): Boolean =
        topChoiceCount > 0 && bottomChoiceCount > 0

    /**
     * 上段候補と下揃え候補の境界線の中心Yです。
     * 下揃え最上行の上端からグループ間余白の半分だけ上へ固定し、
     * 候補数・ページ数・表示ページで動かしません。
     */
    fun groupDividerY(bottomRowCount: Int): Double {
        require(bottomRowCount > 0) { "境界線は下揃え候補がある画面だけに置きます。" }
        return bottomY(bottomRowCount - 1, bottomRowCount) + CHOICE_HEIGHT / 2.0 + GROUP_GAP / 2.0
    }

    /**
     * 下端操作行の左列を画面固有の主操作（追加・作成など）が占める画面で、
     * ページャーを置く帯の中心Yです。下揃え1行分の帯と同じ高さに固定し、
     * 件数やページで動かしません。
     */
    fun pagerBandY(): Double = bottomY(0, 1)

    /** 下揃え候補数に対する、上段1ページあたりの候補数です。 */
    fun topPageSizeFor(bottomChoiceCount: Int): Int = plan(0, bottomChoiceCount, 0).topPageSize

    private fun rowsFor(choiceCount: Int): Int = (choiceCount + COLUMN_COUNT - 1) / COLUMN_COUNT
}

/**
 * 設定候補カード2列の画面区分ごとの横方向寸法です。
 *
 * 親画面（右ペイン）と子画面で列中心・カード幅だけが異なり、
 * 縦配置・ページ分割は [GestureSettingChildLayoutPolicy] を共有します。
 * 下端の操作行は「ページャー＝左列の範囲、戻る＝右列の範囲」に固定します。
 */
internal enum class GestureChoicePane(
    val leftX: Double,
    val rightX: Double,
    val centerX: Double,
    val cardWidth: Double,
    val labelWidth: Int,
    val pagerArrowLeftX: Double,
    val pagerArrowRightX: Double,
    val pagerArrowWidth: Double,
    val pagerStatusX: Double,
    val backX: Double,
    val backWidth: Double,
) {
    /** 親画面の右ペインです。 */
    PARENT(
        leftX = -0.10,
        rightX = 0.67,
        centerX = 0.285,
        cardWidth = 0.66,
        labelWidth = 115,
        pagerArrowLeftX = -0.33,
        pagerArrowRightX = 0.13,
        pagerArrowWidth = 0.20,
        pagerStatusX = -0.10,
        backX = 0.67,
        backWidth = 0.66,
    ),

    /** タブ列のない子画面（全面）です。 */
    CHILD(
        leftX = -0.53,
        rightX = 0.53,
        centerX = 0.0,
        cardWidth = 0.92,
        labelWidth = 160,
        pagerArrowLeftX = -0.85,
        pagerArrowRightX = -0.21,
        pagerArrowWidth = 0.28,
        pagerStatusX = -0.53,
        backX = 0.53,
        backWidth = 0.92,
    );

    /** 列番号（0=左・1=右）のカード中心Xです。 */
    fun columnX(column: Int): Double {
        require(column in 0 until GestureSettingChildLayoutPolicy.COLUMN_COUNT) {
            "設定候補の列番号は0または1です: $column"
        }
        return if (column == 0) leftX else rightX
    }

    /** 左列の左端から右列の右端までの幅です。2列全体へ広げる操作行に使います。 */
    val columnsSpanWidth: Double
        get() = rightX + cardWidth / 2.0 - (leftX - cardWidth / 2.0)

    /**
     * ページャー状態文字から左右矢印中心までのずらし幅です。
     * `pagerSection` の「centerX±offset」形式で使います。
     */
    val pagerArrowOffset: Double
        get() = pagerStatusX - pagerArrowLeftX

    /**
     * 列中心を境にした列帯の半幅です。
     * 1行へ名札＋操作枠など複数枠を並べる一覧行で、列帯の左端を出すときに使います。
     */
    val columnBandHalfWidth: Double
        get() = (rightX - leftX) / 2.0

    /** 左列カードの横範囲（左端・右端）です。 */
    val leftColumnRange: ClosedFloatingPointRange<Double>
        get() = (leftX - cardWidth / 2.0)..(leftX + cardWidth / 2.0)

    /** 右列カードの横範囲（左端・右端）です。 */
    val rightColumnRange: ClosedFloatingPointRange<Double>
        get() = (rightX - cardWidth / 2.0)..(rightX + cardWidth / 2.0)
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
