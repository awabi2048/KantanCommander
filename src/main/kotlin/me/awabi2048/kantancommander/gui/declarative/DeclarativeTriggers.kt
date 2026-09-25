package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOutline
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
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

/** 選択済みイベントの詳細子画面に渡す表示値です。保存済みの値とは分離します。 */
data class TriggerDetailData(
    val title: Component,
    val eventLabel: String,
    val eventName: Component,
    val description: Component,
    val confirmLabel: Component,
    val backLabel: Component,
    val tabBackground: BlockData,
    val tabOutline: BlockData,
    val confirmBackground: BlockData,
    val backBackground: BlockData,
    val tabs: List<TriggerDetailTab> = emptyList(),
    val selectedTab: Int = 0,
    val values: List<SettingsValueRow> = emptyList(),
    val actions: List<TriggerDetailAction> = emptyList(),
)

/** 詳細画面の左タブです。設定済みと選択中は別の状態として保持します。 */
data class TriggerDetailTab(val key: String, val label: Component, val configured: Boolean)

/** 詳細画面右側の操作です。値はNameに含めず、上部の値行へ出します。 */
data class TriggerDetailAction(val key: String, val label: Component, val background: BlockData, val outline: BlockData? = null)

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

    /** 左タブ・右設定の配置は、イベントを再クリックした後の詳細子画面だけに適用します。 */
    fun detailDocument(data: TriggerDetailData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        val tabs = data.tabs.ifEmpty { listOf(TriggerDetailTab("event", data.title, false)) }
        tabs.forEachIndexed { index, tab ->
            val selected = index == data.selectedTab
            val tabWidth = GestureSettingVisualPolicy.selectedTabWidth(GestureLowerPanel.SETTINGS_TAB_WIDTH, selected)
            val tabCenterX = GestureSettingVisualPolicy.selectedTabCenterX(
                GestureLowerPanel.SETTINGS_TAB_CENTER_X, GestureLowerPanel.SETTINGS_TAB_WIDTH, selected,
            )
            val cy = GestureLowerPanel.SETTINGS_TAB_TOP_Y - index * GestureLowerPanel.SETTINGS_TAB_PITCH
            nodes += GestureGuiBlock(
                blockData = data.tabBackground,
                interpolationTicks = 0,
                width = GestureGuiSizeSpec.Fixed(tabWidth),
                height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTINGS_TAB_HEIGHT),
                outline = when {
                    selected -> GestureGuiOutline(data.tabOutline)
                    tab.configured -> GestureGuiOutline(data.confirmBackground)
                    else -> null
                },
                id = "lower-trigger-detail-tab:${tab.key}",
                actionId = "lower-trigger-detail-tab:${tab.key}",
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                absolute = placed(frame, tabCenterX, cy, tabWidth, GestureLowerPanel.SETTINGS_TAB_HEIGHT),
            )
            nodes += GestureGuiText(
                text = tab.label,
                interpolationTicks = 0,
                size = 0.0049,
                lineWidth = 90,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "trigger-detail-tab-label-$index",
                width = GestureGuiSizeSpec.Fixed(tabWidth),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, GestureLowerPanel.SETTINGS_TAB_CENTER_X, cy - 0.02, tabWidth, 0.0049 * 3.0),
            )
        }
        DeclarativeSettings.descriptionSection(
            nodes,
            frame,
            body = data.description,
            detail = null,
            centerX = 0.28,
            hoverY = GestureLowerPanel.ACTION_DESCRIPTION_Y,
        )
        DeclarativeSettings.valueRowsSection(
            nodes,
            frame,
            rows = listOf(SettingsValueRow("trigger-detail-event", data.eventLabel, data.eventName, GestureLowerPanel.SETTING_VALUE_ANCHOR_Y)) + data.values,
        )
        data.actions.forEachIndexed { index, action ->
            DeclarativeSettings.actionCardSection(
                nodes, frame,
                SettingsActionCard(
                    elementId = "lower-trigger-detail-action:${action.key}",
                    label = action.label,
                    background = action.background,
                    outline = action.outline,
                    cx = 0.28,
                    cy = 0.06 - index * 0.12,
                    w = 0.55,
                    h = 0.10,
                    labelSize = 0.0048,
                    labelWidth = 100,
                    gestures = GestureGuiClickPolicy.CLICK,
                    guard = null,
                    hover = null,
                    visualTag = "trigger-detail-action-${action.key}",
                ),
            )
        }
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-trigger-detail-confirm",
                label = data.confirmLabel,
                background = data.confirmBackground,
                cx = 0.28,
                cy = -0.34,
                w = 0.55,
                h = 0.10,
                labelSize = 0.0048,
                labelWidth = 100,
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
