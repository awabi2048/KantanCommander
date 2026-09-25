package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiDocument
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.GestureLowerPanel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.data.BlockData

/** 変数一覧の1件です。文言・素材・ホバー解決値は呼び出し側で用意します。 */
data class WorldVariableEntry(
    val name: String,
    val label: Component,
    /** 値・型の2行ホバーです（常設説明を置換します）。 */
    val hover: GestureGuiHoverText,
    val inUse: Boolean,
    /** 削除ボタンのホバーです（常設説明を置換します）。 */
    val deleteHover: GestureGuiHoverText,
)

/** 変数一覧画面の解決済み入力です。 */
data class WorldVariablesData(
    val title: Component,
    val description: Component,
    val emptyLabel: Component?,
    val entries: List<WorldVariableEntry>,
    val entryBackground: BlockData,
    val deleteBackgroundInUse: BlockData,
    val deleteBackgroundFree: BlockData,
    val page: Int,
    val pageCount: Int,
    val pagerAvailableBackground: BlockData,
    val pagerDisabledBackground: BlockData,
    val createLabel: Component,
    val createBackground: BlockData,
    val backLabel: Component,
    val backBackground: BlockData,
)

/** 型選択肢の1件です。 */
data class WorldVariableTypeOption(
    val key: String,
    val label: Component,
    val selected: Boolean,
    val background: BlockData,
    val outline: BlockData?,
)

/** 型選択画面の解決済み入力です。 */
data class WorldVariableTypeData(
    val title: Component,
    val description: Component,
    val options: List<WorldVariableTypeOption>,
    val nextLabel: Component,
    val nextBackground: BlockData,
    val nextEnabled: Boolean,
    val backLabel: Component,
    val backBackground: BlockData,
)

/** 削除確認画面の解決済み入力です。 */
data class WorldVariableDeleteData(
    val title: Component,
    val body: Component,
    val confirmLabel: Component,
    val confirmBackground: BlockData,
    val cancelLabel: Component,
    val cancelBackground: BlockData,
)

/**
 * 変数系子画面の宣言区画です。
 *
 * 旧 `buildWorldVariables` 系の座標・寸法・素材・要素 ID・ホバー位置をそのまま転記します。
 * 一覧カードと削除ボタンは意味の異なる隣接要素として分離し、カード全体の
 * クリック領域に削除操作を吸わせません。
 */
object DeclarativeVariables {
    fun variablesDocument(data: WorldVariablesData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        nodes += GestureGuiText(
            text = data.title,
            // タブ列以外は補完なしで即時反映します。
            interpolationTicks = 0,
            size = 0.0060,
            lineWidth = 200,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "variables-title",
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
                id = "variables-empty",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(frame, 0.0, GestureLowerPanel.CHILD_CHOICE_TOP_Y, 1.0, 0.0052 * 3.0),
            )
        } else {
            data.entries.forEachIndexed { index, entry ->
                val column = index % 2
                val row = index / 2
                val columnCenter = if (column == 0) -0.53 else 0.53
                val columnLeft = columnCenter - GestureLowerPanel.CHILD_CHOICE_WIDTH / 2.0
                val cardCx = columnLeft + GestureLowerPanel.WORLD_VARIABLE_CARD_WIDTH / 2.0
                val deleteCx = columnLeft + GestureLowerPanel.WORLD_VARIABLE_CARD_WIDTH +
                    GestureLowerPanel.WORLD_VARIABLE_DELETE_GAP +
                    GestureLowerPanel.WORLD_VARIABLE_DELETE_WIDTH / 2.0
                val cy = GestureLowerPanel.CHILD_CHOICE_TOP_Y - row * GestureLowerPanel.CHILD_CHOICE_PITCH
                nodes += GestureGuiBlock(
                    blockData = data.entryBackground,
                    interpolationTicks = 0,
                    width = GestureGuiSizeSpec.Fixed(GestureLowerPanel.WORLD_VARIABLE_CARD_WIDTH),
                    height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTING_CHOICE_HEIGHT),
                    id = "lower-variable:${entry.name}",
                    actionId = "lower-variable:${entry.name}",
                    acceptedGestures = GestureGuiClickPolicy.CLICK,
                    hover = DeclarativeSettings.hoverSpecOf(entry.hover),
                    absolute = placed(
                        frame,
                        cardCx,
                        cy,
                        GestureLowerPanel.WORLD_VARIABLE_CARD_WIDTH,
                        GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                    ),
                )
                nodes += GestureGuiText(
                    text = entry.label,
                    interpolationTicks = 0,
                    size = 0.0045,
                    lineWidth = 100,
                    alignment = GestureGuiTextAlignment.CENTER,
                    id = "variables-label-$index",
                    width = GestureGuiSizeSpec.Fixed(1.0),
                    height = GestureGuiSizeSpec.Auto,
                    absolute = placed(frame, cardCx, cy - 0.012, 1.0, 0.0045 * 3.0),
                )
                nodes += GestureGuiBlock(
                    blockData = if (entry.inUse) data.deleteBackgroundInUse else data.deleteBackgroundFree,
                    interpolationTicks = 0,
                    width = GestureGuiSizeSpec.Fixed(GestureLowerPanel.WORLD_VARIABLE_DELETE_WIDTH),
                    height = GestureGuiSizeSpec.Fixed(GestureLowerPanel.SETTING_CHOICE_HEIGHT),
                    id = "lower-variable-delete:${entry.name}",
                    actionId = "lower-variable-delete:${entry.name}",
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
                    text = Component.text(
                        "🗑",
                        if (entry.inUse) NamedTextColor.GRAY else NamedTextColor.WHITE,
                    ),
                    interpolationTicks = 0,
                    size = 0.0048,
                    lineWidth = 40,
                    alignment = GestureGuiTextAlignment.CENTER,
                    id = "variables-delete-label-$index",
                    width = GestureGuiSizeSpec.Fixed(1.0),
                    height = GestureGuiSizeSpec.Auto,
                    absolute = placed(frame, deleteCx, cy - 0.012, 1.0, 0.0048 * 3.0),
                )
            }
            if (data.pageCount > 1) {
                DeclarativeSettings.pagerSection(
                    nodes,
                    frame,
                    idPrefix = "variables",
                    elementPrefix = "lower-variables-page",
                    page = data.page,
                    pageCount = data.pageCount,
                    centerX = 0.0,
                    centerY = GestureLowerPanel.CHILD_PAGER_Y,
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
                elementId = "lower-variables-create",
                label = data.createLabel,
                background = data.createBackground,
                cx = -0.53,
                cy = -0.43,
                w = GestureLowerPanel.CHILD_CHOICE_WIDTH,
                h = 0.10,
                labelSize = 0.0048,
                labelWidth = 140,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "variables-create",
            ),
        )
        DeclarativeSettings.backSection(
            nodes,
            frame,
            label = data.backLabel,
            background = data.backBackground,
            centerX = 0.53,
            width = GestureLowerPanel.CHILD_CHOICE_WIDTH,
        )
        return DeclarativeSettings.document(nodes, panel)
    }

    fun typeDocument(data: WorldVariableTypeData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        nodes += GestureGuiText(
            text = data.title,
            interpolationTicks = 0,
            size = 0.0060,
            lineWidth = 200,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "world-variable-type-title",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, 0.0, GestureLowerPanel.CHILD_HEADER_Y, 1.0, 0.0060 * 3.0),
        )
        nodes += GestureGuiText(
            text = data.description,
            interpolationTicks = 0,
            size = GestureLowerPanel.DESCRIPTION_TEXT_SIZE,
            lineWidth = 220,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "world-variable-type-description",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, 0.0, GestureLowerPanel.CHILD_HOVER_Y, 1.0, 0.0043 * 3.0),
        )
        DeclarativeSettings.choiceCardsSection(
            nodes,
            frame,
            cards = data.options.mapIndexed { index, option ->
                val cx = if (index == 0) -0.53 else 0.53
                SettingsChoiceCard(
                    elementId = "lower-world-variable-type:${option.key}",
                    label = option.label,
                    background = option.background,
                    outline = option.outline,
                    enabled = true,
                    cx = cx,
                    cy = GestureLowerPanel.CHILD_CHOICE_TOP_Y,
                    w = GestureLowerPanel.CHILD_CHOICE_WIDTH,
                    h = GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                    labelDy = -0.012,
                    labelSize = 0.0045,
                    labelWidth = 115,
                    hover = null,
                    replacesDescription = false,
                    visualTag = "world-variable-type",
                    visualIndex = index,
                )
            },
        )
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = if (data.nextEnabled) "lower-world-variable-type-next" else null,
                label = data.nextLabel,
                background = data.nextBackground,
                cx = -0.53,
                cy = -0.43,
                w = GestureLowerPanel.CHILD_CHOICE_WIDTH,
                h = 0.10,
                labelSize = 0.0048,
                labelWidth = 140,
                gestures = if (data.nextEnabled) GestureGuiClickPolicy.CLICK else emptySet(),
                guard = null,
                hover = null,
                visualTag = "world-variable-type-next",
                consume = !data.nextEnabled,
            ),
        )
        DeclarativeSettings.backSection(
            nodes,
            frame,
            label = data.backLabel,
            background = data.backBackground,
            centerX = 0.53,
            width = GestureLowerPanel.CHILD_CHOICE_WIDTH,
        )
        return DeclarativeSettings.document(nodes, panel)
    }

    fun deleteConfirmDocument(data: WorldVariableDeleteData, panel: GestureGuiPanel = GestureGuiPanel()): GestureGuiDocument {
        val frame = contentFrame(panel)
        val nodes = mutableListOf<GestureGuiNode>()
        nodes += GestureGuiText(
            text = data.title,
            interpolationTicks = 0,
            size = 0.0060,
            lineWidth = 200,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "world-variable-delete-title",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, 0.0, GestureLowerPanel.CHILD_HEADER_Y, 1.0, 0.0060 * 3.0),
        )
        nodes += GestureGuiText(
            text = data.body,
            interpolationTicks = 0,
            size = GestureLowerPanel.DESCRIPTION_TEXT_SIZE,
            lineWidth = 220,
            alignment = GestureGuiTextAlignment.CENTER,
            id = "world-variable-delete-body",
            width = GestureGuiSizeSpec.Fixed(1.0),
            height = GestureGuiSizeSpec.Auto,
            absolute = placed(frame, 0.0, GestureLowerPanel.CHILD_HOVER_Y, 1.0, 0.0043 * 3.0),
        )
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-world-variable-delete-confirm",
                label = data.confirmLabel,
                background = data.confirmBackground,
                cx = -0.53,
                cy = -0.43,
                w = GestureLowerPanel.CHILD_CHOICE_WIDTH,
                h = 0.10,
                labelSize = 0.0048,
                labelWidth = 100,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "world-variable-delete-confirm",
            ),
        )
        // 取消は戻る操作と同一 ID を用います（旧実装どおり）。
        DeclarativeSettings.actionCardSection(
            nodes,
            frame,
            SettingsActionCard(
                elementId = "lower-setting-back",
                label = data.cancelLabel,
                background = data.cancelBackground,
                cx = 0.53,
                cy = -0.43,
                w = GestureLowerPanel.CHILD_CHOICE_WIDTH,
                h = 0.10,
                labelSize = 0.0048,
                labelWidth = 100,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "world-variable-delete-cancel",
            ),
        )
        return DeclarativeSettings.document(nodes, panel)
    }
}
