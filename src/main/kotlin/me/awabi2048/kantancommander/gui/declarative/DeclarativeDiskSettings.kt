package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOutline
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureChoicePane
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import me.awabi2048.kantancommander.gui.GestureSettingChildLayoutPolicy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
 * 取り出しカードを最上行に置き、引数は名前・型・削除の3カード1行の1列一覧で
 * 示します。行位置・下端操作行は設定候補と同じ
 * [GestureSettingChildLayoutPolicy] を使い、ページャーは下端操作行
 * （左列［追加］・右列［戻る］）の直上の帯へ固定します。
 */
object DeclarativeDiskSettings {
    /**
     * 引数行の1ページ件数です。
     * ページャー帯を1行分予約し、取り出し行を除いた上段行数から決めます。
     */
    val ARG_ROW_PAGE_SIZE: Int =
        GestureSettingChildLayoutPolicy.topPageSizeFor(1) /
            GestureSettingChildLayoutPolicy.COLUMN_COUNT - 1

    fun settingsDocument(
        data: DiskSettingsData,
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): GestureGuiDocument {
        val pane = GestureChoicePane.CHILD
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        nodes += GestureGuiText(
            text = data.title,
            // タブ列以外は補完なしで即時反映します。
            interpolationTicks = 0,
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
            centerX = pane.centerX,
            hoverY = GestureLowerPanel.CHILD_HOVER_Y,
        )
        // 取り出し操作は最上行のバナーとして置き、引数行はその下の上段行を使います。
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-disk-take-out",
                label = data.takeOutLabel,
                background = data.takeOutBackground,
                cx = pane.centerX,
                cy = GestureSettingChildLayoutPolicy.topY(0),
                w = pane.cardWidth,
                h = GestureSettingChildLayoutPolicy.CHOICE_HEIGHT,
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
                interpolationTicks = 0,
                size = 0.0052,
                lineWidth = 200,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "disk-settings-empty",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, pane.centerX, GestureSettingChildLayoutPolicy.topY(1), 1.0, 0.0052 * 3.0),
            )
        } else {
            data.arguments.forEachIndexed { index, argument ->
                // 取り出し行を1行分消費するため、引数行は2行目から始めます。
                val cy = GestureSettingChildLayoutPolicy.topY(1 + index)
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
            // 下端操作行は［追加］［戻る］が占めるため、ページャーは
            // その直上の帯へ固定します。件数・ページで位置は変わりません。
            DeclarativeSettings.pagerSection(
                nodes,
                frame,
                idPrefix = "disk-settings",
                elementPrefix = "lower-diskdef-page",
                page = data.page,
                pageCount = data.pageCount,
                centerX = pane.centerX,
                centerY = GestureSettingChildLayoutPolicy.pagerBandY(),
                arrowBackgrounds = listOf(data.page - 1, data.page + 1).map { targetPage ->
                    if (targetPage in 0 until data.pageCount) data.pagerAvailableBackground
                    else data.pagerDisabledBackground
                },
                arrowGlyphs = listOf("◀", "▶").mapIndexed { arrowIndex, glyph ->
                    val targetPage = if (arrowIndex == 0) data.page - 1 else data.page + 1
                    Component.text(glyph).color(
                        if (targetPage in 0 until data.pageCount) NamedTextColor.WHITE else NamedTextColor.GRAY,
                    )
                },
                status = Component.text("${data.page + 1}/${data.pageCount}"),
            )
        }
        // 下端操作行は左列［追加］・右列［戻る］の共通規則です。
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-diskdef-add",
                label = data.addLabel,
                background = data.addBackground,
                cx = pane.leftX,
                cy = GestureSettingChildLayoutPolicy.BACK_Y,
                w = pane.cardWidth,
                h = GestureSettingChildLayoutPolicy.BACK_HEIGHT,
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
            centerX = pane.backX,
            width = pane.backWidth,
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
        val height = GestureSettingChildLayoutPolicy.CHOICE_HEIGHT
        nodes += GestureGuiBlock(
            blockData = background,
            interpolationTicks = 0,
            width = GestureGuiSizeSpec.Fixed(width),
            height = GestureGuiSizeSpec.Fixed(height),
            outline = outline?.let { GestureGuiOutline(it) },
            id = elementId,
            actionId = elementId,
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            hover = null,
            absolute = placed(frame, cx, cy, width, height),
        )
        nodes += GestureGuiText(
            text = label,
            interpolationTicks = 0,
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
