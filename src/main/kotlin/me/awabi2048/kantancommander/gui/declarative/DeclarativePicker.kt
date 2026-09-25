package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiAbsoluteOffsets
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHover
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHoverPosition
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureCommandPickerLayoutPolicy
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import me.awabi2048.kantancommander.gui.GestureSettingVisualPolicy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData

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

/** PICKERページャの矢印です。文言・素材は解決済みで渡します。 */
data class PickerArrow(
    val targetPage: Int,
    val glyph: Component,
    val background: BlockData,
    val available: Boolean,
)

/** PICKERページャです。単ページ時は null にします。 */
data class PickerPager(
    val arrows: List<PickerArrow>,
    val status: Component,
)

/** PICKER画面の解決済み入力です。i18n・候補算出は呼び出し側で行います。 */
data class PickerData(
    val categories: List<PickerCategory>,
    val tabBackgrounds: List<BlockData>,
    val description: Component,
    /** 表示ページ分の種別です。表示順のまま渡します。 */
    val cards: List<PickerCard>,
    val cardBackgrounds: List<BlockData>,
    val pager: PickerPager?,
    val closeLabel: Component,
    val closeBackground: BlockData,
)

/**
 * PICKER画面（左分類タブ＋右種別グリッド＋ページャ）の宣言文書です。
 *
 * 旧 `buildPicker` の座標・寸法・素材・要素 ID・ホバー位置をそのまま転記します。
 * ホバー行位置規則は旧 `parallelButtonHover` の解決値を入力として受けます。
 */
object DeclarativePicker {
    fun document(data: PickerData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument {
        val contentMinX = -panel.width / 2.0 + panel.frameWidth
        val contentMaxY = panel.height / 2.0 - panel.frameWidth

        fun placed(cx: Double, cy: Double, w: Double, h: Double): GestureGuiAbsoluteOffsets =
            GestureGuiAbsoluteOffsets(
                left = (cx - w / 2.0) - contentMinX,
                top = contentMaxY - (cy + h / 2.0),
            )

        val nodes = mutableListOf<GestureGuiNode>()
        // 左タブ列は設定タブと同一寸法です。選択中は右へ15%伸ばします。
        data.categories.forEachIndexed { index, category ->
            val cy = GestureLowerPanel.SETTINGS_TAB_TOP_Y - index * GestureLowerPanel.SETTINGS_TAB_PITCH
            val width = GestureSettingVisualPolicy.selectedTabWidth(GestureLowerPanel.SETTINGS_TAB_WIDTH, category.selected)
            val cx = GestureSettingVisualPolicy.selectedTabCenterX(
                GestureLowerPanel.SETTINGS_TAB_CENTER_X,
                GestureLowerPanel.SETTINGS_TAB_WIDTH,
                category.selected,
            )
            nodes += GestureGuiBlock(
                blockData = data.tabBackgrounds[index],
                width = GestureGuiSizeSpec.Fixed(width),
                height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTINGS_TAB_HEIGHT),
                id = "lower-cat:$index",
                actionId = "lower-cat:$index",
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                absolute = placed(cx, cy, width, GestureLowerPanel.SETTINGS_TAB_HEIGHT),
            )
            // テキスト位置は選択伸縮で動かしません。
            nodes += GestureGuiText(
                text = Component.text(category.label),
                size = 0.0055,
                lineWidth = 90,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "cat-$index",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(GestureLowerPanel.SETTINGS_TAB_CENTER_X, cy - 0.02, 1.0, 0.0055 * 3.0),
            )
        }
        // 閉じるボタンはノード選択中の削除と同じ下端位置です。
        // 左分類タブ列（上記）はタブ演出のため補完を受けます。それ以外は即時反映します。
        nodes += GestureGuiBlock(
            blockData = data.closeBackground,
            interpolationTicks = 0,
            width = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTINGS_TAB_WIDTH),
            height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTINGS_TAB_HEIGHT),
            id = "lower-close-picker",
            actionId = "lower-close-picker",
            acceptedGestures = GestureGuiClickPolicy.CLICK,
            absolute = placed(
                GestureLowerPanel.SETTINGS_TAB_CENTER_X,
                GestureLowerPanel.SETTINGS_TAB_ACTION_Y,
                GestureLowerPanel.SETTINGS_TAB_WIDTH,
                GestureLowerPanel.SETTINGS_TAB_HEIGHT,
            ),
        )
        nodes += GestureGuiText(
            text = data.closeLabel,
            interpolationTicks = 0,
            size = 0.006,
            lineWidth = 90,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "lower-close",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(
                GestureLowerPanel.SETTINGS_TAB_CENTER_X,
                GestureLowerPanel.SETTINGS_TAB_ACTION_Y,
                1.0,
                0.006 * 3.0,
            ),
        )
        nodes += GestureGuiText(
            text = data.description,
            interpolationTicks = 0,
            size = GestureLowerPanel.DESCRIPTION_TEXT_SIZE,
            lineWidth = 280,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "picker-description-body",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(0.28, GestureLowerPanel.ACTION_DESCRIPTION_Y, 1.0, 0.0043 * 3.0),
        )
        data.cards.forEachIndexed { index, card ->
            val column = index % GestureCommandPickerLayoutPolicy.COLUMNS
            val row = index / GestureCommandPickerLayoutPolicy.COLUMNS
            val cx = GestureCommandPickerLayoutPolicy.columnX(column)
            val cy = GestureCommandPickerLayoutPolicy.rowY(row)
            nodes += GestureGuiBlock(
                blockData = data.cardBackgrounds[index],
                // コマンド選択肢カードだけを補間なしで切り替えます。
                interpolationTicks = 0,
                width = GestureGuiSizeSpec.Fixed(GestureCommandPickerLayoutPolicy.CARD_WIDTH),
                height = GestureGuiSizeSpec.Fixed(GestureCommandPickerLayoutPolicy.CARD_HEIGHT),
                id = "lower-type:${card.key}",
                actionId = "lower-type:${card.key}",
                acceptedGestures = GestureGuiClickPolicy.CLICK,
                // ホバーは旧行位置規則の解決値をそのまま用います。
                // 複数行は Component.newline() で結合します（単一文字列化しません）。
                hover = GestureGuiHover(
                    text = card.hoverLines.filter(String::isNotBlank)
                        .map(Component::text)
                        .foldIndexed(Component.empty() as Component) { index, result, line ->
                            if (index == 0) line else result.append(Component.newline()).append(line)
                        },
                    size = GestureLowerPanel.DESCRIPTION_TEXT_SIZE,
                    lineWidth = 280,
                    position = GestureGuiHoverPosition.Fixed(card.hoverX, card.hoverY),
                ),
                absolute = placed(
                    cx,
                    cy,
                    GestureCommandPickerLayoutPolicy.CARD_WIDTH,
                    GestureCommandPickerLayoutPolicy.CARD_HEIGHT,
                ),
            )
            nodes += GestureGuiText(
                text = Component.text(card.label),
                interpolationTicks = 0,
                size = 0.0045,
                lineWidth = 115,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "type-$index",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(cx, cy - 0.012, 1.0, 0.0045 * 3.0),
            )
        }
        data.pager?.let { pager ->
            pager.arrows.forEachIndexed { arrowIndex, arrow ->
                val x = 0.28 + if (arrowIndex == 0) -0.12 else 0.12
                nodes += GestureGuiBlock(
                    blockData = arrow.background,
                    interpolationTicks = 0,
                    width = GestureGuiSizeSpec.Fixed(0.18),
                    height = GestureGuiSizeSpec.Fixed(0.10),
                    // 利用不可方向は操作面を作りません。
                    id = if (arrow.available) "lower-picker-page:${arrow.targetPage}" else "picker-page-${arrow.targetPage}-bg",
                    actionId = if (arrow.available) "lower-picker-page:${arrow.targetPage}" else null,
                    acceptedGestures = if (arrow.available) GestureGuiClickPolicy.CLICK else emptySet(),
                    absolute = placed(x, -0.48, 0.18, 0.10),
                )
                nodes += GestureGuiText(
                    text = arrow.glyph,
                    interpolationTicks = 0,
                    size = 0.005,
                    lineWidth = 60,
                    alignment = GestureGuiTextAlignment.CENTER,
                    id = "picker-page-${arrow.targetPage}-label",
                    width = GestureGuiSizeSpec.Fixed(1.0),
                    height = GestureGuiSizeSpec.Auto,
                    absolute = placed(x, -0.48, 1.0, 0.005 * 3.0),
                )
            }
            nodes += GestureGuiText(
                text = pager.status,
                interpolationTicks = 0,
                size = 0.004,
                lineWidth = 80,
                alignment = GestureGuiTextAlignment.CENTER,
                id = "picker-page-status",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(0.28, -0.48, 1.0, 0.004 * 3.0),
            )
        }
        return GestureGuiDocument(
            GestureGuiBox(
                children = nodes,
                id = "picker-root",
                width = GestureGuiSizeSpec.Percent(1.0),
                height = GestureGuiSizeSpec.Percent(1.0),
            ),
            panel,
        )
    }
}
