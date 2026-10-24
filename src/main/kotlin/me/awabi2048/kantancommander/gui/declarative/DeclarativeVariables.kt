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
 * 変数系子画面の宣言実装です。
 *
 * 画面骨格（タイトル・常設説明・本文・帯・下端操作行の領域配置）は
 * `gesture-gui/child-screen.html` が持ち、ここでは実行時データを載せた
 * ノードを各領域へ注入します。一覧カードと削除ボタンは意味の異なる
 * 隣接要素として分離し、カード全体のクリック領域に削除操作を吸わせません。
 */
object DeclarativeVariables {
    fun variablesCompiled(
        data: WorldVariablesData,
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
                id = "variables-empty",
                width = GestureGuiSizeSpec.Fixed(1.0),
                height = GestureGuiSizeSpec.Auto,
                absolute = placed(
                    regions.content,
                    0.0,
                    GestureSettingChildLayoutPolicy.TOP_CHOICE_Y,
                    1.0,
                    0.0052 * 3.0,
                ),
            )
        } else {
            data.entries.forEachIndexed { index, entry ->
                val column = index % 2
                val row = index / 2
                // 列中心は設定候補と同じ画面区分ポリシーから取ります。
                val columnCenter = GestureChoicePane.CHILD.columnX(column)
                // 1列分の行幅（名札＋削除）は候補カード幅と同じです。
                val columnLeft = columnCenter - GestureChoicePane.CHILD.cardWidth / 2.0
                val cardCx = columnLeft + GestureLowerPanel.WORLD_VARIABLE_CARD_WIDTH / 2.0
                val deleteCx = columnLeft + GestureLowerPanel.WORLD_VARIABLE_CARD_WIDTH +
                    GestureLowerPanel.WORLD_VARIABLE_DELETE_GAP +
                    GestureLowerPanel.WORLD_VARIABLE_DELETE_WIDTH / 2.0
                val cy = GestureSettingChildLayoutPolicy.topY(row)
                DeclarativeSettings.actionCardSection(
                    content,
                    regions.content,
                    SettingsActionCard(
                        elementId = "lower-variable:${entry.name}",
                        label = entry.label,
                        background = data.entryBackground,
                        cx = cardCx,
                        cy = cy,
                        w = GestureLowerPanel.WORLD_VARIABLE_CARD_WIDTH,
                        h = GestureLowerPanel.SETTING_CHOICE_HEIGHT,
                        labelDy = -0.012,
                        labelSize = 0.0045,
                        labelWidth = 100,
                        gestures = GestureGuiClickPolicy.CLICK,
                        guard = null,
                        hover = entry.hover,
                        visualTag = "variable-$index",
                    ),
                )
                DeclarativeSettings.actionCardSection(
                    content,
                    regions.content,
                    SettingsActionCard(
                        elementId = "lower-variable-delete:${entry.name}",
                        label = Component.text(
                            "🗑",
                            if (entry.inUse) NamedTextColor.GRAY else NamedTextColor.WHITE,
                        ),
                        background = if (entry.inUse) data.deleteBackgroundInUse else data.deleteBackgroundFree,
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
                        visualTag = "variable-delete-$index",
                    ),
                )
            }
            if (data.pageCount > 1) {
                // 下端操作行は［新規作成］［戻る］が占めるため、ページャーは
                // その直上の帯へ固定します。件数・ページで位置は変わりません。
                DeclarativeSettings.pagerSection(
                    band,
                    regions.band,
                    idPrefix = "variables",
                    elementPrefix = "lower-variables-page",
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
        // 下端操作行は左列［新規作成］・右列［戻る］の共通規則です。
        val footerLeft = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.actionCardSection(
            footerLeft,
            regions.footerLeft,
            SettingsActionCard(
                elementId = "lower-variables-create",
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
                visualTag = "variables-create",
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

    fun typeCompiled(
        data: WorldVariableTypeData,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
        theme: GestureGuiTheme = GestureGuiThemes.standard(),
    ): KantanLayoutBridge.Compiled {
        val regions = DeclarativeChildScreen.regions(panel)
        val content = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.choiceCardsSection(
            content,
            regions.content,
            cards = data.options.mapIndexed { index, option ->
                SettingsChoiceCard(
                    elementId = "lower-world-variable-type:${option.key}",
                    label = option.label,
                    background = option.background,
                    outline = option.outline,
                    enabled = true,
                    cx = GestureChoicePane.CHILD.columnX(index),
                    cy = GestureSettingChildLayoutPolicy.topY(0),
                    w = GestureChoicePane.CHILD.cardWidth,
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
        val footerLeft = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.actionCardSection(
            footerLeft,
            regions.footerLeft,
            SettingsActionCard(
                elementId = if (data.nextEnabled) "lower-world-variable-type-next" else null,
                label = data.nextLabel,
                background = data.nextBackground,
                cx = GestureChoicePane.CHILD.leftX,
                cy = GestureSettingChildLayoutPolicy.BACK_Y,
                w = GestureChoicePane.CHILD.cardWidth,
                h = GestureSettingChildLayoutPolicy.BACK_HEIGHT,
                labelSize = 0.0048,
                labelWidth = 140,
                gestures = if (data.nextEnabled) GestureGuiClickPolicy.CLICK else emptySet(),
                guard = null,
                hover = null,
                visualTag = "world-variable-type-next",
                consume = !data.nextEnabled,
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
            footerLeft = footerLeft,
            footerRight = footerRight,
            onAction = onAction,
            onDiagnostics = onDiagnostics,
            panel = panel,
            theme = theme,
        )
    }

    fun deleteConfirmCompiled(
        data: WorldVariableDeleteData,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
        theme: GestureGuiTheme = GestureGuiThemes.standard(),
    ): KantanLayoutBridge.Compiled {
        val regions = DeclarativeChildScreen.regions(panel)
        val footerLeft = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.actionCardSection(
            footerLeft,
            regions.footerLeft,
            SettingsActionCard(
                elementId = "lower-world-variable-delete-confirm",
                label = data.confirmLabel,
                background = data.confirmBackground,
                cx = GestureChoicePane.CHILD.leftX,
                cy = GestureSettingChildLayoutPolicy.BACK_Y,
                w = GestureChoicePane.CHILD.cardWidth,
                h = GestureSettingChildLayoutPolicy.BACK_HEIGHT,
                labelSize = 0.0048,
                labelWidth = 100,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "world-variable-delete-confirm",
            ),
        )
        // 取消は戻る操作と同一 ID を用います（旧実装どおり）。
        val footerRight = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.actionCardSection(
            footerRight,
            regions.footerRight,
            SettingsActionCard(
                elementId = "lower-setting-back",
                label = data.cancelLabel,
                background = data.cancelBackground,
                cx = GestureChoicePane.CHILD.backX,
                cy = GestureSettingChildLayoutPolicy.BACK_Y,
                w = GestureChoicePane.CHILD.backWidth,
                h = GestureSettingChildLayoutPolicy.BACK_HEIGHT,
                labelSize = 0.0048,
                labelWidth = 100,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "world-variable-delete-cancel",
            ),
        )
        // 確認画面は本文のない骨格で、確認文言は常設説明の区画へ載せます。
        return DeclarativeChildScreen.compile(
            title = data.title,
            description = data.body,
            footerLeft = footerLeft,
            footerRight = footerRight,
            onAction = onAction,
            onDiagnostics = onDiagnostics,
            panel = panel,
            theme = theme,
        )
    }
}
