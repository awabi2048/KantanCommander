package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiActionContext
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiTextAlignment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutDiagnostic
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiSizeSpec
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData

/** 中央揃えの文言1行です。 */
data class CenteredLine(
    val id: String,
    val text: Component,
    val y: Double,
    val size: Double = 0.005,
    val lineWidth: Int = 230,
)

/** テスト開始確認の切替1件です。文言は解決済みで渡します。 */
data class TestToggleData(
    /** 要素 ID 全文です（例 test-confirm-debug）。 */
    val id: String,
    val label: Component,
    val background: BlockData,
    val centerY: Double,
)

/** テスト開始確認画面の解決済み入力です。 */
data class TestConfirmData(
    val title: Component,
    val description: Component,
    val toggles: List<TestToggleData>,
    val startLabel: Component,
    val startBackground: BlockData,
    val cancelLabel: Component,
    val cancelBackground: BlockData,
)

/**
 * テスト系画面の宣言区画です。骨格 `gesture-gui/test-screen.html` の
 * 「見出し・本文・下端操作行」領域へ、行文言と操作カードを注入して構築します。
 *
 * 旧 `buildTestConfirmation` 系の座標・寸法・素材・要素 ID をそのまま転記します。
 * ガード・ホバー・枠は旧実装になく、操作面は受付集合のみです。
 */
object DeclarativeTest {
    private fun centered(frame: ContentFrame, line: CenteredLine): GestureGuiNode =
        GestureGuiText(
            text = line.text,
            // タブ列以外は補完なしで即時反映します。
            interpolationTicks = 0,
            size = line.size,
            lineWidth = line.lineWidth,
            alignment = GestureGuiTextAlignment.CENTER,
            id = line.id,
            width = GestureGuiSizeSpec.Fixed(1.0),
            // 予約箱の中心がアンカーになるよう高さを明示します。
            height = GestureGuiSizeSpec.Fixed(line.size * 3.0),
            absolute = placed(frame, 0.0, line.y, 1.0, line.size * 3.0),
        )

    private fun actionCard(
        elementId: String,
        label: Component,
        background: BlockData,
        cx: Double,
        cy: Double,
        w: Double,
        h: Double,
        labelSize: Double,
        labelWidth: Int,
        visualTag: String,
    ): SettingsActionCard = SettingsActionCard(
        elementId = elementId,
        label = label,
        background = background,
        cx = cx,
        cy = cy,
        w = w,
        h = h,
        labelSize = labelSize,
        labelWidth = labelWidth,
        gestures = GestureGuiClickPolicy.CLICK,
        guard = null,
        hover = null,
        visualTag = visualTag,
    )

    /** テスト開始確認画面を骨格へ注入して解決・変換します。 */
    fun confirmCompiled(
        data: TestConfirmData,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): KantanLayoutBridge.Compiled {
        val regions = DeclarativeTestScreen.regions(panel)
        val header = mutableListOf(
            centered(regions.header, CenteredLine("test-confirm-title", data.title, 0.34, 0.008, 220)),
            centered(regions.header, CenteredLine("test-confirm-description", data.description, 0.25, 0.0045, 230)),
        )
        val content = mutableListOf<GestureGuiNode>()
        data.toggles.forEach { toggle ->
            DeclarativeSettings.actionCardSection(
                content,
                regions.content,
                actionCard(
                    elementId = toggle.id,
                    label = toggle.label,
                    background = toggle.background,
                    cx = 0.0,
                    cy = toggle.centerY,
                    w = 1.55,
                    h = 0.12,
                    labelSize = 0.0055,
                    labelWidth = 230,
                    visualTag = toggle.id,
                ),
            )
        }
        val footer = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.actionCardSection(
            footer,
            regions.footer,
            actionCard(
                elementId = "test-confirm-start",
                label = data.startLabel,
                background = data.startBackground,
                cx = -0.30,
                cy = -0.34,
                w = 0.50,
                h = 0.12,
                labelSize = 0.005,
                labelWidth = 100,
                visualTag = "test-confirm-start",
            ),
        )
        DeclarativeSettings.actionCardSection(
            footer,
            regions.footer,
            actionCard(
                elementId = "test-confirm-cancel",
                label = data.cancelLabel,
                background = data.cancelBackground,
                cx = 0.30,
                cy = -0.34,
                w = 0.50,
                h = 0.12,
                labelSize = 0.005,
                labelWidth = 100,
                visualTag = "test-confirm-cancel",
            ),
        )
        return DeclarativeTestScreen.compile(
            header = header,
            content = content,
            footer = footer,
            onAction = onAction,
            onDiagnostics = onDiagnostics,
            panel = panel,
        )
    }

    /** 実行状態画面を骨格へ注入して解決・変換します。毎tickの再生成を前提にします。 */
    fun statusCompiled(
        title: Component,
        rows: List<CenteredLine>,
        hint: Component,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): KantanLayoutBridge.Compiled {
        val regions = DeclarativeTestScreen.regions(panel)
        val header = mutableListOf(
            centered(regions.header, CenteredLine("test-status-title", title, 0.39, 0.007, 220)),
        )
        val content = rows.map { centered(regions.content, it) }
        val footer = mutableListOf(
            centered(regions.footer, CenteredLine("test-status-hint", hint, -0.42, 0.0042, 220)),
        )
        return DeclarativeTestScreen.compile(
            header = header,
            content = content,
            footer = footer,
            onAction = onAction,
            onDiagnostics = onDiagnostics,
            panel = panel,
        )
    }

    /** 結果画面を骨格へ注入して解決・変換します。 */
    fun resultCompiled(
        title: Component,
        rows: List<CenteredLine>,
        completeLabel: Component,
        completeBackground: BlockData,
        onAction: (GestureGuiActionContext) -> Unit,
        onDiagnostics: (List<GestureGuiLayoutDiagnostic>) -> Unit = {},
        panel: GestureGuiPanel = GestureGuiPanel(),
    ): KantanLayoutBridge.Compiled {
        val regions = DeclarativeTestScreen.regions(panel)
        val header = mutableListOf(
            centered(regions.header, CenteredLine("test-result-title", title, 0.39, 0.008, 220)),
        )
        val content = rows.map { centered(regions.content, it) }
        val footer = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.actionCardSection(
            footer,
            regions.footer,
            actionCard(
                elementId = "test-result-complete",
                label = completeLabel,
                background = completeBackground,
                cx = 0.0,
                cy = -0.48,
                w = 0.70,
                h = 0.10,
                labelSize = 0.005,
                labelWidth = 120,
                visualTag = "test-result-complete",
            ),
        )
        return DeclarativeTestScreen.compile(
            header = header,
            content = content,
            footer = footer,
            onAction = onAction,
            onDiagnostics = onDiagnostics,
            panel = panel,
        )
    }
}
