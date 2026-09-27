package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureChoicePane
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.GestureSettingChildLayoutPolicy
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
 * 設定候補の子画面と同じ列規則（[GestureChoicePane.CHILD]）・縦配置・ページ分割
 * （[GestureSettingChildLayoutPolicy]）を使い、要素 ID の接頭辞だけを
 * `lower-trigger-kind`／`lower-trigger-event` へ変えます。選択は即時確定のため
 * ［次へ］操作は持たず、戻る操作は設定画面共通の `lower-setting-back` を使います。
 * 候補は常に左列から埋め、奇数最終行の中央寄せは行いません。
 * 下端操作行は左列にページャー、右列に戻るを固定します。
 */
object DeclarativeTriggers {
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
            centerX = GestureChoicePane.PARENT.centerX,
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
                cx = GestureChoicePane.PARENT.centerX,
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
        // 列規則・縦配置・ページ分割・下端操作行は設定候補画面と同じ
        // 共通ポリシーから取ります。奇数最終行も中央へ寄せず左列から埋めます。
        val pane = GestureChoicePane.CHILD
        val layout = GestureSettingChildLayoutPolicy.plan(
            topChoiceCount = data.options.size,
            bottomChoiceCount = 0,
            requestedPage = data.page,
        )
        val visible = data.options.subList(layout.topStart, layout.topEndExclusive)
        DeclarativeSettings.choiceCardsSection(
            nodes,
            frame,
            cards = visible.mapIndexed { index, option ->
                val row = index / 2
                SettingsChoiceCard(
                    elementId = "lower-$idPrefix:${option.key}",
                    label = option.label,
                    background = option.background,
                    outline = option.outline,
                    enabled = true,
                    cx = pane.columnX(index % 2),
                    cy = GestureSettingChildLayoutPolicy.topY(row),
                    w = pane.cardWidth,
                    h = GestureSettingChildLayoutPolicy.CHOICE_HEIGHT,
                    labelDy = -0.012,
                    labelSize = 0.0045,
                    labelWidth = pane.labelWidth,
                    hover = null,
                    replacesDescription = false,
                    visualTag = visualTag,
                    visualIndex = index,
                )
            },
        )
        if (layout.count > 1) {
            DeclarativeSettings.pagerSection(
                nodes,
                frame,
                idPrefix = idPrefix,
                elementPrefix = "lower-$idPrefix-page",
                page = layout.index,
                pageCount = layout.count,
                centerX = pane.pagerStatusX,
                centerY = GestureSettingChildLayoutPolicy.BACK_Y,
                arrowBackgrounds = listOf(layout.index - 1, layout.index + 1).map { targetPage ->
                    if (targetPage in 0 until layout.count) {
                        requireNotNull(data.pagerAvailableBackground)
                    } else {
                        requireNotNull(data.pagerDisabledBackground)
                    }
                },
                arrowGlyphs = listOf("◀", "▶").mapIndexed { arrowIndex, glyph ->
                    val targetPage = if (arrowIndex == 0) layout.index - 1 else layout.index + 1
                    Component.text(glyph).color(
                        if (targetPage in 0 until layout.count) {
                            net.kyori.adventure.text.format.NamedTextColor.WHITE
                        } else {
                            net.kyori.adventure.text.format.NamedTextColor.GRAY
                        },
                    )
                },
                status = Component.text("${layout.index + 1}/${layout.count}"),
                arrowOffset = pane.pagerArrowOffset,
                arrowWidth = pane.pagerArrowWidth,
            )
        }
        DeclarativeSettings.backSection(
            nodes,
            frame,
            label = data.backLabel,
            background = data.backBackground,
            centerX = pane.backX,
            width = pane.backWidth,
        )
        return DeclarativeSettings.document(nodes, panel)
    }
}
