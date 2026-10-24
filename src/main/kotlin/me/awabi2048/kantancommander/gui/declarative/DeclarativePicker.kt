package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiComponents
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiGrid
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHover
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHoverPosition
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiMainArrangement
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCrossAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiOverlay
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiThemes
import me.awabi2048.kantancommander.gui.GestureCommandPickerLayoutPolicy
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import net.kyori.adventure.text.Component

/** PICKER左タブ列の分類です。文言は解決済みで渡します。 */
data class PickerCategory(
    val label: String,
    val selected: Boolean,
)

/** PICKER右詳細の種別カードです。ホバー座標は行位置規則の解決済み値で渡します。 */
data class PickerCard(
    /** 要素 ID（lower-type:〜）に用いる種別名です。 */
    val key: String,
    val label: String,
    /**
     * issue #review P2-10: ホバー説明の意味行です。
     *
     * コマンド説明の2行構成（機能説明・バニラ対応）を潰さないため、
     * 空白結合の単一文字列ではなく行構造で保持します。
     */
    val hoverLines: List<String>,
    val hoverX: Double,
    val hoverY: Double,
)

/** PICKERページャの矢印です。文言は解決済みで渡し、素材はテーマ（有効 accent／無効 disabled）が決めます。 */
data class PickerArrow(
    val targetPage: Int,
    val glyph: Component,
    val available: Boolean,
)

/** PICKERページャです。単ページ時は null にします。 */
data class PickerPager(
    val arrows: List<PickerArrow>,
    val status: Component,
)

/**
 * PICKER画面の解決済み入力です。i18n・候補算出は呼び出し側で行います。
 *
 * 構造・配置・素材は `gesture-gui/picker.html` と標準テーマが保持するため、
 * ここには文言・選択状態・ホバー位置といった実行時データだけを残します。
 */
data class PickerData(
    val categories: List<PickerCategory>,
    val description: Component,
    /** 表示ページ分の種別です。表示順のまま渡します。 */
    val cards: List<PickerCard>,
    val pager: PickerPager?,
    val closeLabel: Component,
)

/**
 * PICKER画面（左分類タブ＋右種別グリッド＋ページャ）の宣言コンパイルです。
 *
 * 画面の区画・配置は `gesture-gui/picker.html`、素材語彙は標準テーマが持ちます。
 * ここではタブ・カード・ページャといった実行時決定の一覧をノードとして組み立て、
 * マークアップ上の容器（`picker-tabs` / `picker-cards` / `picker-pager`）へ注入します。
 * 要素 ID と操作受付（CLICK 集合・`lower-*` 系列）は旧実装と同一です。
 */
object DeclarativePicker {
    private const val RESOURCE_PATH = "gesture-gui/picker.html"

    // 旧レイアウト規則由来の寸法（GestureCommandPickerLayoutPolicy / SETTINGS_TAB_* との対応）
    private const val CARD_WIDTH = 0.66
    private const val CARD_HEIGHT = 0.10
    private const val CARD_ROW_GAP = 0.01
    private const val CARD_COLUMN_GAP = 0.11
    private const val TAB_WIDTH = 0.47
    private const val TAB_HEIGHT = 0.10
    private const val ARROW_WIDTH = 0.18
    private const val ARROW_HEIGHT = 0.10

    fun compile(
        data: PickerData,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
        theme: GestureGuiTheme = GestureGuiThemes.standard(),
    ): KantanLayoutBridge.Compiled {
        val injections = buildMap<String, List<GestureGuiNode>> {
            put("picker-tabs", data.categories.mapIndexed { index, category ->
                GestureGuiComponents.tab(
                    id = "lower-cat:$index",
                    label = Component.text(category.label),
                    actionId = "lower-cat:$index",
                    theme = theme,
                    selected = category.selected,
                    baseWidth = GestureGuiSizeSpec.Fixed(TAB_WIDTH),
                    height = GestureGuiSizeSpec.Fixed(TAB_HEIGHT),
                    textSize = 0.0055,
                    acceptedGestures = GestureGuiClickPolicy.CLICK,
                )
            })
            put("picker-cards", listOf(cardGrid(data, theme)))
            data.pager?.let { pager ->
                put("picker-pager", pagerChildren(pager, theme))
            }
        }
        return KantanLayoutBridge.compileHtmlResource(
            resourcePath = RESOURCE_PATH,
            environment = KantanGuiTheme.environment(
                components = mapOf(
                    "picker-description-body" to data.description,
                    "lower-close-picker" to data.closeLabel,
                ),
                theme = theme,
            ),
            onAction = onAction,
            panel = panel,
            onDiagnostics = onDiagnostics,
            injections = injections,
        )
    }

    /** 種別カードの2列格子です。縦横で異なる間隔はCSS単一 gap で表せないためノードで組み立てます。 */
    private fun cardGrid(data: PickerData, theme: GestureGuiTheme): GestureGuiNode =
        GestureGuiGrid(
            children = data.cards.mapIndexed { index, card -> cardNode(index, card, theme) },
            columns = GestureCommandPickerLayoutPolicy.COLUMNS,
            id = "picker-cards-grid",
            // セル幅がカード幅と一致するよう、2列＋列間隔の合計を明示します。
            width = GestureGuiSizeSpec.Fixed(CARD_WIDTH * 2 + CARD_COLUMN_GAP),
            height = GestureGuiSizeSpec.Auto,
            gap = CARD_COLUMN_GAP,
            crossGap = CARD_ROW_GAP,
        )

    /**
     * 種別カードです。旧実装の背景・ラベル分離構造を Overlay 内の部品構成へ写します。
     * 切り替わりは即時反映（interpolationTicks = 0）です。
     */
    private fun cardNode(
        index: Int,
        card: PickerCard,
        theme: GestureGuiTheme,
    ): GestureGuiNode = GestureGuiOverlay(
        children = listOf(
            GestureGuiBlock(
                blockData = theme.accentDeep,
                interpolationTicks = 0,
                width = GestureGuiSizeSpec.Auto,
                height = GestureGuiSizeSpec.Auto,
                id = "card-$index-bg",
            ),
            GestureGuiText(
                text = Component.text(card.label),
                interpolationTicks = 0,
                size = 0.0045,
                lineWidth = 115,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "type-$index",
            ),
        ),
        id = "lower-type:${card.key}",
        actionId = "lower-type:${card.key}",
        acceptedGestures = GestureGuiClickPolicy.CLICK,
        width = GestureGuiSizeSpec.Fixed(CARD_WIDTH),
        height = GestureGuiSizeSpec.Fixed(CARD_HEIGHT),
        // ホバーは旧行位置規則の解決値をそのまま用います。
        // 複数行は Component.newline() で結合します（単一文字列化しません）。
        hover = GestureGuiHover(
            text = card.hoverLines.filter(String::isNotBlank)
                .map(Component::text)
                .foldIndexed(Component.empty() as Component) { lineIndex, result, line ->
                    if (lineIndex == 0) line else result.append(Component.newline()).append(line)
                },
            size = GestureLowerPanel.DESCRIPTION_TEXT_SIZE,
            lineWidth = 280,
            position = GestureGuiHoverPosition.Fixed(card.hoverX, card.hoverY),
        ),
        mainArrangement = GestureGuiMainArrangement.CENTER,
        crossAlignment = GestureGuiCrossAlignment.CENTER,
    )

    /** ページャ行の子（戻る矢印・ページ表示・進む矢印）です。利用不可方向は操作面を作りません。 */
    private fun pagerChildren(
        pager: PickerPager,
        theme: GestureGuiTheme,
    ): List<GestureGuiNode> {
        val children = mutableListOf<GestureGuiNode>()
        pager.arrows.forEach { arrow ->
            children += GestureGuiOverlay(
                children = listOf(
                    GestureGuiBlock(
                        blockData = if (arrow.available) theme.accent else theme.disabled,
                        interpolationTicks = 0,
                        width = GestureGuiSizeSpec.Auto,
                        height = GestureGuiSizeSpec.Auto,
                        id = "picker-page-${arrow.targetPage}-inner",
                    ),
                    GestureGuiText(
                        text = arrow.glyph,
                        interpolationTicks = 0,
                        size = 0.005,
                        lineWidth = 60,
                        alignment = GestureGuiTextAlignment.CENTER,
                        id = "picker-page-${arrow.targetPage}-label",
                    ),
                ),
                id = if (arrow.available) "lower-picker-page:${arrow.targetPage}" else "picker-page-${arrow.targetPage}-bg",
                actionId = if (arrow.available) "lower-picker-page:${arrow.targetPage}" else null,
                acceptedGestures = if (arrow.available) GestureGuiClickPolicy.CLICK else emptySet(),
                width = GestureGuiSizeSpec.Fixed(ARROW_WIDTH),
                height = GestureGuiSizeSpec.Fixed(ARROW_HEIGHT),
                mainArrangement = GestureGuiMainArrangement.CENTER,
                crossAlignment = GestureGuiCrossAlignment.CENTER,
            )
        }
        // ページ表示は矢印の間へ置きます（呼び出し側は [prev, next] の2矢印を渡します）。
        children.add(children.size.coerceAtMost(1), GestureGuiText(
            text = pager.status,
            interpolationTicks = 0,
            size = 0.004,
            lineWidth = 80,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "picker-page-status",
        ))
        return children
    }
}
