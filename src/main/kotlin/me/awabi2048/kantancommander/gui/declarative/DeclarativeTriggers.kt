package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOutline
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.GestureSettingVisualPolicy
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

/** issue #7 の起動方法の選択画面の解決済み入力です。選択と確定を分けて表示します。 */
data class TriggerChoiceData(
    val title: Component,
    val description: Component,
    val options: List<TriggerChoiceOption>,
    val backLabel: Component,
    val backBackground: BlockData,
    /** 右側の現在値行に表示する固定ラベルです。 */
    val selectionLabel: String,
    /** 右側の現在値行に表示する選択値です。 */
    val selectionValue: Component,
    /** 選択候補に付随する現在値などの補足です。 */
    val selectionSummary: Component? = null,
    /** 候補ページ番号です。単一ページの画面では0のままにします。 */
    val page: Int = 0,
    /** 候補ページ数です。2以上でページャを表示します。 */
    val pageCount: Int = 1,
    val pagerAvailableBackground: BlockData? = null,
    val pagerDisabledBackground: BlockData? = null,
)

/**
 * issue #7 の起動方法の選択子画面の宣言区画です。
 *
 * 一般コマンド設定と同じ左タブ・右詳細の配置で起動方法を編集します。
 * 1回目のクリックは候補選択だけを行い、選択中の候補をもう一度クリックした時に
 * 詳細画面への遷移または確定を行います。イベント候補は左側を6件単位で固定ページ化します。
 */
object DeclarativeTriggers {
    /**
     * イベント選択の1ページあたり候補数です。
     *
     * 2列×3行で戻る操作・ページャと重なりません。10候補は2ページ（6＋4）です。
     */
    const val EVENT_PAGE_SIZE = 6

    private fun visibleOptions(data: TriggerChoiceData): List<TriggerChoiceOption> =
        if (data.pageCount > 1) data.options.drop(data.page * EVENT_PAGE_SIZE).take(EVENT_PAGE_SIZE)
        else data.options
    fun kindDocument(data: TriggerChoiceData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument =
        choiceDocument(data, idPrefix = "trigger-kind", panel = panel)

    fun eventDocument(data: TriggerChoiceData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument =
        choiceDocument(data, idPrefix = "trigger-event", panel = panel)

    private fun choiceDocument(
        data: TriggerChoiceData,
        idPrefix: String,
        panel: GestureGuiPanel,
    ): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        val visible = visibleOptions(data)
        visible.forEachIndexed { index, option ->
            val selectedWidth = GestureSettingVisualPolicy.selectedTabWidth(
                GestureLowerPanel.SETTINGS_TAB_WIDTH,
                option.selected,
            )
            val selectedCenterX = GestureSettingVisualPolicy.selectedTabCenterX(
                GestureLowerPanel.SETTINGS_TAB_CENTER_X,
                GestureLowerPanel.SETTINGS_TAB_WIDTH,
                option.selected,
            )
            val centerY = GestureLowerPanel.SETTINGS_TAB_TOP_Y - index * GestureLowerPanel.SETTINGS_TAB_PITCH
            val elementId = "lower-$idPrefix:${option.key}"
            nodes += GestureGuiBlock(
                blockData = option.background,
                width = GestureGuiSizeSpec.Fixed(selectedWidth),
                height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTINGS_TAB_HEIGHT),
                id = elementId,
                actionId = elementId,
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                outline = option.outline?.let(::GestureGuiOutline),
                absolute = placed(frame, selectedCenterX, centerY, selectedWidth, GestureLowerPanel.SETTINGS_TAB_HEIGHT),
            )
            nodes += GestureGuiText(
                text = option.label,
                size = 0.0049,
                lineWidth = 90,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "$idPrefix-tab-$index",
                width = GestureGuiSizeSpec.Fixed(selectedWidth),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, GestureLowerPanel.SETTINGS_TAB_CENTER_X, centerY - 0.02, selectedWidth, 0.0049 * 3.0),
            )
        }
        DeclarativeSettings.descriptionSection(
            nodes,
            frame,
            body = data.description,
            detail = data.selectionSummary?.let {
                DescriptionDetail(it, GestureLowerPanel.SETTING_DETAIL_ANCHOR_Y)
            },
            centerX = 0.28,
            hoverY = GestureLowerPanel.ACTION_DESCRIPTION_Y,
        )
        DeclarativeSettings.valueRowsSection(
            nodes,
            frame,
            rows = listOf(
                SettingsValueRow(
                    id = "$idPrefix-selection",
                    label = data.selectionLabel,
                    value = data.selectionValue,
                    y = GestureLowerPanel.SETTING_VALUE_ANCHOR_Y,
                ),
            ),
        )
        if (data.pageCount > 1) {
            DeclarativeSettings.pagerSection(
                nodes,
                frame,
                idPrefix = idPrefix,
                elementPrefix = "lower-$idPrefix-page",
                page = data.page,
                pageCount = data.pageCount,
                centerX = 0.28,
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
            centerX = GestureLowerPanel.SETTINGS_TAB_CENTER_X,
            width = GestureLowerPanel.SETTINGS_TAB_WIDTH,
        )
        return DeclarativeSettings.document(nodes, panel)
    }
}
