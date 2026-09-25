package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData

/** issue #8 の呼び出し設定の引数カード1件です。 */
data class DiskCallArgumentCard(
    val name: String,
    val label: Component,
    val background: BlockData,
    val outline: BlockData?,
)

/**
 * issue #8 の呼び出し設定画面の解決済み入力です。
 *
 * 引数の値（リテラルまたは変数参照）と、戻り値の受取先（scope＋変数名）を
 * 一画面で編集します。要素 ID は操作振分けの照合対象です。
 */
data class DiskCallSettingsData(
    val title: Component,
    val body: Component,
    val emptyArgs: Component,
    val arguments: List<DiskCallArgumentCard>,
    val page: Int,
    val pageCount: Int,
    val pagerAvailableBackground: BlockData,
    val pagerDisabledBackground: BlockData,
    val returnLabel: Component,
    val returnScopeLabel: Component,
    val returnScopeBackground: BlockData,
    val returnNameLabel: Component,
    val returnNameBackground: BlockData,
    val backLabel: Component,
    val backBackground: BlockData,
)

/**
 * issue #8 の呼び出し設定子画面の宣言区画です。
 *
 * 引数は1列のカードで示し、3件ずつページングします。カードは値の種別に応じて
 * 値入力または変数名入力のDialogへ進みます。戻り値の受取先は下部の2カード
 * （scope切替・変数名）で編集します。要素 ID は `lower-disk-arg:<name>`・
 * `lower-disk-return-scope`・`lower-disk-return-name` を操作側と共有します。
 */
object DeclarativeDiskCall {
    private const val ARG_ROW_TOP_Y = 0.04
    private const val ARG_ROW_PITCH = 0.11
    private const val ARG_ROW_HEIGHT = 0.09
    private const val RETURN_LABEL_Y = -0.28
    private const val RETURN_ROW_Y = -0.35
    private const val PAGER_Y = 0.13

    fun settingsDocument(
        data: DiskCallSettingsData,
        panel: GestureGuiPanel = GestureGuiPanel(),
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
            id = "disk-call-title",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, 0.0, GestureLowerPanel.CHILD_HEADER_Y, 1.0, 0.0060 * 3.0),
        )
        DeclarativeSettings.descriptionSection(
            nodes,
            frame,
            body = data.body,
            detail = null,
            centerX = 0.0,
            hoverY = GestureLowerPanel.CHILD_HOVER_Y,
        )
        if (data.arguments.isEmpty()) {
            nodes += GestureGuiText(
                text = data.emptyArgs,
                interpolationTicks = 0,
                size = 0.0052,
                lineWidth = 200,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "disk-call-empty",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, 0.0, ARG_ROW_TOP_Y, 1.0, 0.0052 * 3.0),
            )
        } else {
            DeclarativeSettings.choiceCardsSection(
                nodes,
                frame,
                cards = data.arguments.mapIndexed { index, argument ->
                    SettingsChoiceCard(
                        elementId = "lower-disk-arg:${argument.name}",
                        label = argument.label,
                        background = argument.background,
                        outline = argument.outline,
                        enabled = true,
                        cx = 0.0,
                        cy = ARG_ROW_TOP_Y - index * ARG_ROW_PITCH,
                        w = GestureLowerPanel.CHILD_CHOICE_WIDTH,
                        h = ARG_ROW_HEIGHT,
                        labelDy = -0.010,
                        labelSize = 0.0045,
                        labelWidth = 150,
                        hover = null,
                        replacesDescription = false,
                        visualTag = "disk-call-arg",
                        visualIndex = index,
                    )
                },
            )
        }
        if (data.pageCount > 1) {
            DeclarativeSettings.pagerSection(
                nodes,
                frame,
                idPrefix = "disk-call",
                elementPrefix = "lower-disk-arg-page",
                page = data.page,
                pageCount = data.pageCount,
                centerX = 0.0,
                centerY = PAGER_Y,
                arrowBackgrounds = listOf(data.page - 1, data.page + 1).map { targetPage ->
                    if (targetPage in 0 until data.pageCount) data.pagerAvailableBackground
                    else data.pagerDisabledBackground
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
        nodes += GestureGuiText(
            text = data.returnLabel,
            interpolationTicks = 0,
            size = 0.0045,
            lineWidth = 200,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "disk-call-return-label",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, 0.0, RETURN_LABEL_Y, 1.0, 0.0045 * 3.0),
        )
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-disk-return-scope",
                label = data.returnScopeLabel,
                background = data.returnScopeBackground,
                cx = -0.28,
                cy = RETURN_ROW_Y,
                w = 0.30,
                h = ARG_ROW_HEIGHT,
                labelSize = 0.0045,
                labelWidth = 60,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "disk-call-return-scope",
            ),
        )
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-disk-return-name",
                label = data.returnNameLabel,
                background = data.returnNameBackground,
                cx = 0.18,
                cy = RETURN_ROW_Y,
                w = 0.58,
                h = ARG_ROW_HEIGHT,
                labelSize = 0.0045,
                labelWidth = 120,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "disk-call-return-name",
            ),
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
