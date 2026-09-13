package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOutline
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData

/** issue #8 のディスク引数定義の1件です。文言・素材は呼び出し側で解決します。 */
data class DiskArgumentRow(
    val name: String,
    val nameLabel: Component,
    val typeLabel: Component,
    val background: BlockData,
    val outline: BlockData?,
)

/**
 * issue #8 のディスク設定画面の解決済み入力です。
 *
 * 取り出し操作と引数定義の一覧を一画面で扱います。要素 ID は操作振分けの
 * 照合対象であり、`lower-disk-take-out`・`lower-diskdef-*` を操作側と共有します。
 */
data class DiskSettingsData(
    val title: Component,
    val body: Component,
    val takeOutLabel: Component,
    val takeOutBackground: BlockData,
    val emptyArgs: Component,
    val arguments: List<DiskArgumentRow>,
    val page: Int,
    val pageCount: Int,
    val pagerAvailableBackground: BlockData,
    val pagerDisabledBackground: BlockData,
    val addLabel: Component,
    val addBackground: BlockData,
    val backLabel: Component,
    val backBackground: BlockData,
)

/**
 * issue #8 のディスク設定子画面の宣言区画です。
 *
 * 取り出しカードを上部に置き、引数は名前・型・削除の3カード1行で示します。
 * 引数は2件ずつページングし、追加カードと戻る操作が重ならないようにします。
 */
object DeclarativeDiskSettings {
    private const val TAKE_OUT_Y = 0.04
    private const val ARG_ROW_TOP_Y = -0.07
    private const val ARG_ROW_PITCH = 0.11
    private const val ARG_ROW_HEIGHT = 0.09
    private const val BOTTOM_ROW_Y = -0.29
    private const val PAGER_Y = 0.13

    fun settingsDocument(
        data: DiskSettingsData,
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        nodes += GestureGuiText(
            text = data.title,
            size = 0.0060,
            lineWidth = 200,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "disk-settings-title",
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
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-disk-take-out",
                label = data.takeOutLabel,
                background = data.takeOutBackground,
                cx = 0.0,
                cy = TAKE_OUT_Y,
                w = GestureLowerPanel.CHILD_CHOICE_WIDTH,
                h = ARG_ROW_HEIGHT,
                labelSize = 0.0048,
                labelWidth = 140,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "disk-settings-take-out",
            ),
        )
        if (data.arguments.isEmpty()) {
            nodes += GestureGuiText(
                text = data.emptyArgs,
                size = 0.0052,
                lineWidth = 200,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "disk-settings-empty",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, 0.0, ARG_ROW_TOP_Y, 1.0, 0.0052 * 3.0),
            )
        } else {
            data.arguments.forEachIndexed { index, argument ->
                val cy = ARG_ROW_TOP_Y - index * ARG_ROW_PITCH
                addArgumentCard(
                    nodes,
                    frame,
                    elementId = "lower-diskdef-name:${argument.name}",
                    label = argument.nameLabel,
                    background = argument.background,
                    outline = argument.outline,
                    cx = -0.20,
                    width = 0.48,
                    cy = cy,
                    visualIndex = index,
                )
                addArgumentCard(
                    nodes,
                    frame,
                    elementId = "lower-diskdef-type:${argument.name}",
                    label = argument.typeLabel,
                    background = argument.background,
                    outline = null,
                    cx = 0.17,
                    width = 0.22,
                    cy = cy,
                    visualIndex = index,
                )
                addArgumentCard(
                    nodes,
                    frame,
                    elementId = "lower-diskdef-delete:${argument.name}",
                    label = Component.text("×"),
                    background = argument.background,
                    outline = null,
                    cx = 0.37,
                    width = 0.14,
                    cy = cy,
                    visualIndex = index,
                )
            }
        }
        if (data.pageCount > 1) {
            DeclarativeSettings.pagerSection(
                nodes,
                frame,
                idPrefix = "disk-settings",
                elementPrefix = "lower-diskdef-page",
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
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-diskdef-add",
                label = data.addLabel,
                background = data.addBackground,
                cx = 0.0,
                cy = BOTTOM_ROW_Y,
                w = GestureLowerPanel.CHILD_CHOICE_WIDTH,
                h = ARG_ROW_HEIGHT,
                labelSize = 0.0048,
                labelWidth = 140,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "disk-settings-add",
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

    /**
     * 引数行の1カード（操作面＋文言）を配置します。
     *
     * 文言ノードのIDは要素ID由来とし、連番に依存しません。
     */
    private fun addArgumentCard(
        nodes: MutableList<GestureGuiNode>,
        frame: ContentFrame,
        elementId: String,
        label: Component,
        background: BlockData,
        outline: BlockData?,
        cx: Double,
        width: Double,
        cy: Double,
        visualIndex: Int,
    ) {
        nodes += GestureGuiBlock(
            blockData = background,
            width = GestureGuiSizeSpec.Fixed(width),
            height = GestureGuiSizeSpec.Fixed(ARG_ROW_HEIGHT),
            outline = outline?.let { GestureGuiOutline(it) },
            id = elementId,
            actionId = elementId,
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            hover = null,
            absolute = placed(frame, cx, cy, width, ARG_ROW_HEIGHT),
        )
        nodes += GestureGuiText(
            text = label,
            size = 0.0045,
            lineWidth = 115,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "$elementId-label",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, cx, cy - 0.010, 1.0, 0.0045 * 3.0),
        )
    }
}
