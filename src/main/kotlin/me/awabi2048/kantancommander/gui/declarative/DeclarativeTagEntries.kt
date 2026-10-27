package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiThemes
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
    fun tagCompiled(
        data: TagListData,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
        theme: GestureGuiTheme = GestureGuiThemes.standard(),
    ): KantanLayoutBridge.Compiled {
        val regions = DeclarativeChildScreen.regions(panel)
        val content = mutableListOf<GestureGuiNode>()
        val band = mutableListOf<GestureGuiNode>()
        if (data.emptyLabel != null) {
            content += GestureGuiText(
                text = data.emptyLabel,
                interpolationTicks = 0,
                size = 0.0052,
                lineWidth = 200,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "tag-list-empty",
                width = GestureGuiSizeSpec.Fixed(1.0),
                // 予約箱の中心がアンカーになるよう高さを明示します。
                height = GestureGuiSizeSpec.Fixed(0.03),
                absolute = placed(
                    regions.content,
                    0.0,
                    GestureSettingChildLayoutPolicy.TOP_CHOICE_Y,
                    1.0,
                    0.03,
                ),
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
                DeclarativeSettings.actionCardSection(
                    content,
                    regions.content,
                    SettingsActionCard(
                        elementId = "lower-tag-card:$index",
                        actionId = modeAction,
                        label = Component.text(entry.tag),
                        background = data.entryBackground,
                        cx = cardCx,
                        cy = cy,
                        w = GestureLowerPanel.TAG_CARD_WIDTH,
                        h = GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                        labelDy = -0.012,
                        labelSize = 0.0045,
                        labelWidth = 100,
                        gestures = GestureGuiClickPolicy.CLICK,
                        guard = null,
                        hover = entry.hover,
                        visualTag = "tag-card-$index",
                        outline = data.edgeBackground,
                    ),
                )
                DeclarativeSettings.actionCardSection(
                    content,
                    regions.content,
                    SettingsActionCard(
                        elementId = "lower-tag-mode-button:$index",
                        actionId = modeAction,
                        label = entry.modeGlyph,
                        background = entry.modeBackground,
                        cx = modeCx,
                        cy = cy,
                        w = GestureLowerPanel.TAG_MODE_WIDTH,
                        h = GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                        labelDy = -0.012,
                        labelSize = 0.0048,
                        labelWidth = 40,
                        gestures = GestureGuiClickPolicy.CLICK,
                        guard = null,
                        hover = entry.modeHover,
                        visualTag = "tag-mode-$index",
                    ),
                )
                DeclarativeSettings.actionCardSection(
                    content,
                    regions.content,
                    SettingsActionCard(
                        elementId = "lower-tag-delete:$index",
                        label = Component.text("🗑", NamedTextColor.WHITE),
                        background = data.deleteBackground,
                        cx = deleteCx,
                        cy = cy,
                        w = GestureLowerPanel.WORLD_VARIABLE_DELETE_WIDTH,
                        h = GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                        labelDy = -0.012,
                        labelSize = 0.0048,
                        labelWidth = 40,
                        gestures = GestureGuiClickPolicy.CLICK,
                        guard = null,
                        hover = entry.deleteHover,
                        visualTag = "tag-delete-$index",
                    ),
                )
            }
            if (data.pageCount > 1) {
                // 下端操作行は［追加］［戻る］が占めるため、ページャーは
                // その直上の帯へ固定します。件数・ページで位置は変わりません。
                DeclarativeSettings.pagerSection(
                    band,
                    regions.band,
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
        val footerLeft = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.actionCardSection(
            footerLeft,
            regions.footerLeft,
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
        val footerRight = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.backSection(
            footerRight,
            regions.footerRight,
            label = data.backLabel,
            background = data.backBackground,
            centerX = GestureChoicePane.CHILD.backX,
            width = GestureChoicePane.CHILD.backWidth,
        )
        return DeclarativeChildScreen.compile(
            title = data.title,
            description = data.description,
            content = content,
            band = band,
            footerLeft = footerLeft,
            footerRight = footerRight,
            onAction = onAction,
            onDiagnostics = onDiagnostics,
            panel = panel,
            theme = theme,
        )
    }
}
