package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiOutline
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
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

/** タグ一覧の1件です。文言・素材・ホバー解決値は呼び出し側で用意します。 */
data class TagListEntry(
    val tag: String,
    /** モード切替ボタンの字形（＋／－）です。 */
    val modeGlyph: Component,
    /** モード切替ボタンの背景です。追加＝緑・削除＝橙で区別します。 */
    val modeBackground: BlockData,
    /** タグ名のホバーです（常設説明を置換します）。 */
    val hover: GestureGuiHoverText,
    /** モードボタンのホバーです（常設説明を置換します）。 */
    val modeHover: GestureGuiHoverText,
    /** 削除ボタンのホバーです（常設説明を置換します）。 */
    val deleteHover: GestureGuiHoverText,
)

/** タグ一覧子画面の解決済み入力です。 */
data class TagListData(
    val title: Component,
    val description: Component,
    val emptyLabel: Component?,
    val entries: List<TagListEntry>,
    val entryBackground: BlockData,
    /** 行自体が入力であるため、縁は一律空色とします。選択ハイライトは付けません。 */
    val edgeBackground: BlockData,
    val deleteBackground: BlockData,
    val page: Int,
    val pageCount: Int,
    val pagerAvailableBackground: BlockData,
    val pagerDisabledBackground: BlockData,
    val createLabel: Component,
    val createBackground: BlockData,
    val backLabel: Component,
    val backBackground: BlockData,
)

/**
 * タグ一覧子画面の宣言区画です。
 *
 * ワールド変数子画面と同一の行構造（名札＋端操作の分離・2列・3行ページ・
 * 追加行・ページャ・戻る）を使い、タグ行だけを3枠（名札＋モード＋削除）へ
 * 拡張します。モード枠は削除枠と同一寸法とし、一覧カード全体の
 * クリック領域に削除操作を吸わせません。
 */
object DeclarativeTagEntries {
    fun tagDocument(data: TagListData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        nodes += GestureGuiText(
            text = data.title,
            // タブ列以外は補完なしで即時反映します。
            interpolationTicks = 0,
            size = 0.0060,
            lineWidth = 200,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "tag-list-title",
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
        if (data.emptyLabel != null) {
            nodes += GestureGuiText(
                text = data.emptyLabel,
                interpolationTicks = 0,
                size = 0.0052,
                lineWidth = 200,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "tag-list-empty",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, 0.0, GestureSettingChildLayoutPolicy.TOP_CHOICE_Y, 1.0, 0.0052 * 3.0),
            )
        } else {
            data.entries.forEachIndexed { index, entry ->
                val column = index % 2
                val row = index / 2
                // 列帯の左端は、設定候補と同じ画面区分ポリシーの列中心から取ります。
                val columnLeft = GestureChoicePane.CHILD.columnX(column) -
                    GestureChoicePane.CHILD.columnBandHalfWidth
                val cardCx = columnLeft + GestureLowerPanel.TAG_CARD_WIDTH / 2.0
                val modeCx = columnLeft + GestureLowerPanel.TAG_CARD_WIDTH +
                    GestureLowerPanel.TAG_ICON_GAP +
                    GestureLowerPanel.TAG_MODE_WIDTH / 2.0
                val deleteCx = modeCx + GestureLowerPanel.TAG_MODE_WIDTH / 2.0 +
                    GestureLowerPanel.TAG_ICON_GAP +
                    GestureLowerPanel.WORLD_VARIABLE_DELETE_WIDTH / 2.0
                val cy = GestureSettingChildLayoutPolicy.topY(row)
                val modeAction = "lower-tag-mode:$index"
                // 名札のクリックはモード切替と同義にし、無反応行を作りません。
                nodes += GestureGuiBlock(
                    blockData = data.entryBackground,
                    interpolationTicks = 0,
                    width = GestureGuiSizeSpec.Fixed(GestureLowerPanel.TAG_CARD_WIDTH),
                    height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTING_CHOICE_HEIGHT),
                    id = "lower-tag-card:$index",
                    actionId = modeAction,
                    acceptedGestures = GestureGuiClickPolicy.CLICK,
                    hover = DeclarativeSettings.hoverSpecOf(entry.hover),
                    outline = GestureGuiOutline(data.edgeBackground),
                    absolute = placed(
                        frame,
                        cardCx,
                        cy,
                        GestureLowerPanel.TAG_CARD_WIDTH,
                        GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                    ),
                )
                nodes += GestureGuiText(
                    text = Component.text(entry.tag),
                    interpolationTicks = 0,
                    size = 0.0045,
                    lineWidth = 100,
                    alignment = GestureGuiTextAlignment.CENTER,
                    id = "tag-list-label-$index",
                    width = GestureGuiSizeSpec.Fixed(1.0),
                    height = GestureGuiSizeSpec.Auto,
                    absolute = placed(frame, cardCx, cy - 0.012, 1.0, 0.0045 * 3.0),
                )
                nodes += GestureGuiBlock(
                    blockData = entry.modeBackground,
                    interpolationTicks = 0,
                    width = GestureGuiSizeSpec.Fixed(GestureLowerPanel.TAG_MODE_WIDTH),
                    height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTING_CHOICE_HEIGHT),
                    id = "lower-tag-mode-button:$index",
                    actionId = modeAction,
                    acceptedGestures = GestureGuiClickPolicy.CLICK,
                    hover = DeclarativeSettings.hoverSpecOf(entry.modeHover),
                    absolute = placed(
                        frame,
                        modeCx,
                        cy,
                        GestureLowerPanel.TAG_MODE_WIDTH,
                        GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                    ),
                )
                nodes += GestureGuiText(
                    text = entry.modeGlyph,
                    interpolationTicks = 0,
                    size = 0.0048,
                    lineWidth = 40,
                    alignment = GestureGuiTextAlignment.CENTER,
                    id = "tag-list-mode-label-$index",
                    width = GestureGuiSizeSpec.Fixed(1.0),
                    height = GestureGuiSizeSpec.Auto,
                    absolute = placed(frame, modeCx, cy - 0.012, 1.0, 0.0048 * 3.0),
                )
                nodes += GestureGuiBlock(
                    blockData = data.deleteBackground,
                    interpolationTicks = 0,
                    width = GestureGuiSizeSpec.Fixed(GestureLowerPanel.WORLD_VARIABLE_DELETE_WIDTH),
                    height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTING_CHOICE_HEIGHT),
                    id = "lower-tag-delete:$index",
                    actionId = "lower-tag-delete:$index",
                    acceptedGestures = GestureGuiClickPolicy.CLICK,
                    hover = DeclarativeSettings.hoverSpecOf(entry.deleteHover),
                    absolute = placed(
                        frame,
                        deleteCx,
                        cy,
                        GestureLowerPanel.WORLD_VARIABLE_DELETE_WIDTH,
                        GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                    ),
                )
                nodes += GestureGuiText(
                    text = Component.text("🗑", NamedTextColor.WHITE),
                    interpolationTicks = 0,
                    size = 0.0048,
                    lineWidth = 40,
                    alignment = GestureGuiTextAlignment.CENTER,
                    id = "tag-list-delete-label-$index",
                    width = GestureGuiSizeSpec.Fixed(1.0),
                    height = GestureGuiSizeSpec.Auto,
                    absolute = placed(frame, deleteCx, cy - 0.012, 1.0, 0.0048 * 3.0),
                )
            }
            if (data.pageCount > 1) {
                // 下端操作行は［追加］［戻る］が占めるため、ページャーは
                // その直上の帯へ固定します。件数・ページで位置は変わりません。
                DeclarativeSettings.pagerSection(
                    nodes,
                    frame,
                    idPrefix = "tag-list",
                    elementPrefix = "lower-tag-page",
                    page = data.page,
                    pageCount = data.pageCount,
                    centerX = 0.0,
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
        }
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-tag-create",
                label = data.createLabel,
                background = data.createBackground,
                cx = GestureChoicePane.CHILD.leftX,
                cy = GestureSettingChildLayoutPolicy.BACK_Y,
                w = GestureChoicePane.CHILD.cardWidth,
                h = GestureSettingChildLayoutPolicy.BACK_HEIGHT,
                labelSize = 0.0048,
                labelWidth = 140,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "tag-list-create",
            ),
        )
        DeclarativeSettings.backSection(
            nodes,
            frame,
            label = data.backLabel,
            background = data.backBackground,
            centerX = GestureChoicePane.CHILD.backX,
            width = GestureChoicePane.CHILD.backWidth,
        )
        return DeclarativeSettings.document(nodes, panel)
    }
}
