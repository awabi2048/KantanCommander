package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData

/** issue #7 の起動方法の選択肢1件です。文言・素材は呼び出し側で解決します。 */
data class TriggerChoiceOption(
    val key: String,
    val label: Component,
    val selected: Boolean,
    val background: BlockData,
    val outline: BlockData?,
)

/** issue #7 の起動方法の選択画面の解決済み入力です。選択時に即時確定します。 */
data class TriggerChoiceData(
    val title: Component,
    val description: Component,
    val options: List<TriggerChoiceOption>,
    val backLabel: Component,
    val backBackground: BlockData,
    /** 候補ページ番号です。単一ページの画面では0のままにします。 */
    val page: Int = 0,
    /** 候補ページ数です。2以上でページャを表示します。 */
    val pageCount: Int = 1,
    val pagerAvailableBackground: BlockData? = null,
    val pagerDisabledBackground: BlockData? = null,
)

/** 選択済みイベントの詳細子画面へ渡す表示値です。保存済みの値とは分離します。 */
data class TriggerDetailData(
    val eventLabel: String,
    val eventName: Component,
    val description: Component,
    val descriptionDetail: DescriptionDetail? = null,
    val confirmLabel: Component,
    val backLabel: Component,
    val tabBackground: BlockData,
    val selectedTabBackground: BlockData,
    val confirmBackground: BlockData,
    val backBackground: BlockData,
    val tabs: List<TriggerDetailTab> = emptyList(),
    val selectedTab: Int = 0,
    val values: List<SettingsValueRow> = emptyList(),
    /** 通常TARGETと同じ候補カード列です。独自の縦並び操作は持ちません。 */
    val choices: List<SettingsChoiceCard> = emptyList(),
)

/** 詳細画面の左タブです。通常設定と同じく選択状態だけを持ちます。 */
data class TriggerDetailTab(val key: String, val label: Component)

/**
 * issue #7 の起動方法の選択子画面の宣言区画です。
 *
 * 型選択子画面と同じ座標・寸法・素材・操作振分けの約束を使い、要素 ID の接頭辞だけを
 * `lower-trigger-kind`／`lower-trigger-event` へ変えます。選択は即時確定のため
 * ［次へ］操作は持たず、戻る操作は設定画面共通の `lower-setting-back` を使います。
 * 3件以上の候補は2列格子へ並べ、端数行は中央へ寄せて重心を保ちます。
 * 候補が多い画面はページ分割し、戻る操作と重ならないようにします。
 */
object DeclarativeTriggers {
    /**
     * イベント選択の1ページあたり候補数です。
     *
     * 2列×3行で戻る操作・ページャと重なりません。7候補は2ページ（6＋1）です。
     */
    const val EVENT_PAGE_SIZE = 6

    private fun visibleOptions(data: TriggerChoiceData): List<TriggerChoiceOption> =
        if (data.pageCount > 1) {
            data.options.drop(data.page * EVENT_PAGE_SIZE).take(EVENT_PAGE_SIZE)
        } else {
            data.options
        }
    fun kindDocument(data: TriggerChoiceData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument =
        choiceDocument(data, idPrefix = "trigger-kind", visualTag = "trigger-kind", panel = panel)

    fun eventDocument(data: TriggerChoiceData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument =
        choiceDocument(data, idPrefix = "trigger-event", visualTag = "trigger-event", panel = panel)

    /** イベント詳細の左列は、一般コマンド設定が実際に使うタブ列で描画します。 */
    fun detailDocument(data: TriggerDetailData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.tabColumnSection(
            nodes,
            frame,
            tabs = data.tabs.mapIndexed { index, tab ->
                val selected = index == data.selectedTab
                SettingsTabEntry(
                    label = tab.label,
                    selected = selected,
                    attentionHover = null,
                    background = if (selected) data.selectedTabBackground else data.tabBackground,
                    // 通常設定のタブと同じく設定済み縁は使いません。
                    outline = null,
                    elementId = "lower-trigger-detail-tab:${tab.key}",
                )
            },
        )
        DeclarativeSettings.descriptionSection(
            nodes,
            frame,
            body = data.description,
            detail = data.descriptionDetail,
            centerX = 0.28,
            hoverY = GestureLowerPanel.ACTION_DESCRIPTION_Y,
        )
        DeclarativeSettings.valueRowsSection(
            nodes,
            frame,
            rows = listOf(SettingsValueRow("trigger-detail-event", data.eventLabel, data.eventName, GestureLowerPanel.SETTING_VALUE_ANCHOR_Y)) + data.values,
        )
        DeclarativeSettings.choiceCardsSection(nodes, frame, data.choices)
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-trigger-detail-confirm",
                label = data.confirmLabel,
                background = data.confirmBackground,
                cx = 0.28,
                cy = -0.47,
                w = 0.92,
                h = 0.08,
                labelSize = 0.0048,
                labelWidth = 140,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "trigger-detail-confirm",
            ),
        )
        DeclarativeSettings.backSection(
            nodes,
            frame,
            label = data.backLabel,
            background = data.backBackground,
            centerX = GestureLowerPanel.SETTINGS_TAB_CENTER_X,
            width = GestureLowerPanel.SETTINGS_TAB_WIDTH,
        )
        return DeclarativeSettings.document(nodes, panel)
    }

    private fun choiceDocument(
        data: TriggerChoiceData,
        idPrefix: String,
        visualTag: String,
        panel: GestureGuiPanel,
    ): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        nodes += GestureGuiText(
            text = data.title,
            // タブ列以外は補完なしで即時反映します。
            interpolationTicks = 0,
            size = 0.0060,
            lineWidth = 200,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "$idPrefix-title",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, 0.0, GestureLowerPanel.CHILD_HEADER_Y, 1.0, 0.0060 * 3.0),
        )
        DeclarativeSettings.descriptionSection(
            nodes,
            frame,
            body = data.description,
            detail = null,
            centerX = 0.0,
            hoverY = GestureLowerPanel.CHILD_HOVER_Y,
        )
        val visible = visibleOptions(data)
        DeclarativeSettings.choiceCardsSection(
            nodes,
            frame,
            cards = visible.mapIndexed { index, option ->
                val row = index / 2
                val oddLast = visible.size % 2 == 1 && index == visible.lastIndex
                val cx = if (oddLast) 0.0 else if (index % 2 == 0) -0.53 else 0.53
                SettingsChoiceCard(
                    elementId = "lower-$idPrefix:${option.key}",
                    label = option.label,
                    background = option.background,
                    outline = option.outline,
                    enabled = true,
                    cx = cx,
                    cy = GestureLowerPanel.CHILD_CHOICE_TOP_Y - row * GestureLowerPanel.CHILD_CHOICE_PITCH,
                    w = GestureLowerPanel.CHILD_CHOICE_WIDTH,
                    h = GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                    labelDy = -0.012,
                    labelSize = 0.0045,
                    labelWidth = 115,
                    hover = null,
                    replacesDescription = false,
                    visualTag = visualTag,
                    visualIndex = index,
                )
            },
        )
        if (data.pageCount > 1) {
            DeclarativeSettings.pagerSection(
                nodes,
                frame,
                idPrefix = idPrefix,
                elementPrefix = "lower-$idPrefix-page",
                page = data.page,
                pageCount = data.pageCount,
                centerX = 0.0,
                centerY = GestureLowerPanel.CHILD_PAGER_Y,
                arrowBackgrounds = listOf(data.page - 1, data.page + 1).map { targetPage ->
                    if (targetPage in 0 until data.pageCount) {
                        requireNotNull(data.pagerAvailableBackground)
                    } else {
                        requireNotNull(data.pagerDisabledBackground)
                    }
                },
                arrowGlyphs = listOf("◀", "▶").mapIndexed { arrowIndex, glyph ->
                    val targetPage = if (arrowIndex == 0) data.page - 1 else data.page + 1
                    Component.text(glyph).color(
                        if (targetPage in 0 until data.pageCount) {
                            net.kyori.adventure.text.format.NamedTextColor.WHITE
                        } else {
                            net.kyori.adventure.text.format.NamedTextColor.GRAY
                        },
                    )
                },
                status = Component.text("${data.page + 1}/${data.pageCount}"),
            )
        }
        DeclarativeSettings.backSection(
            nodes,
            frame,
            label = data.backLabel,
            background = data.backBackground,
            centerX = 0.0,
            width = GestureLowerPanel.CHILD_CHOICE_WIDTH,
        )
        return DeclarativeSettings.document(nodes, panel)
    }
}
