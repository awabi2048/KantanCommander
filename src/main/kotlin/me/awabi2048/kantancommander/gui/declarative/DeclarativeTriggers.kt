package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureLowerPanel
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
)

/**
 * issue #7 の起動方法の選択子画面の宣言区画です。
 *
 * 型選択子画面と同じ座標・寸法・素材・操作振分けの約束を使い、要素 ID の接頭辞だけを
 * `lower-trigger-kind`／`lower-trigger-event` へ変えます。選択は即時確定のため
 * ［次へ］操作は持たず、戻る操作は設定画面共通の `lower-setting-back` を使います。
 * 3件以上の候補は2列格子へ並べ、端数行は中央へ寄せて重心を保ちます。
 */
object DeclarativeTriggers {
    fun kindDocument(data: TriggerChoiceData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument =
        choiceDocument(data, idPrefix = "trigger-kind", visualTag = "trigger-kind", panel = panel)

    fun eventDocument(data: TriggerChoiceData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument =
        choiceDocument(data, idPrefix = "trigger-event", visualTag = "trigger-event", panel = panel)

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
        DeclarativeSettings.choiceCardsSection(
            nodes,
            frame,
            cards = data.options.mapIndexed { index, option ->
                val row = index / 2
                val oddLast = data.options.size % 2 == 1 && index == data.options.lastIndex
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
