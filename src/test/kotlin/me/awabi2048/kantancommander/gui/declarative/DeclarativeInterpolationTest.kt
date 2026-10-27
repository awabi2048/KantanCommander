package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiColumn
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiGrid
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiItem
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiOverlay
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiRow
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * 下部画面の表示物における interpolation の既定を固定します。
 *
 * 左側のタブ列だけが演出用の補完を受けるため interpolationTicks を指定せず、
 * それ以外の表示物は補完なし（0）で即時反映します。タブ以外へ補完が効くと
 * 切替時の残像・遅延になるため、文書単位で回帰を検出します。
 */
class DeclarativeInterpolationTest {
    private val panel = GestureGuiPanel(width = 2.1213203435596424, height = 1.0606601717798212, frameWidth = 0.045)

    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun ticks(node: GestureGuiNode): Int? = when (node) {
        is GestureGuiBlock -> node.interpolationTicks
        is GestureGuiText -> node.interpolationTicks
        is GestureGuiItem -> node.interpolationTicks
        else -> null
    }

    /** 表示物ノードへ潜って列挙します（カードの背景＋文言はOverlayの子です）。 */
    private fun leafNodes(node: GestureGuiNode): List<GestureGuiNode> = when (node) {
        is GestureGuiBox -> node.children.flatMap(::leafNodes)
        is GestureGuiColumn -> node.children.flatMap(::leafNodes)
        is GestureGuiRow -> node.children.flatMap(::leafNodes)
        is GestureGuiGrid -> node.children.flatMap(::leafNodes)
        is GestureGuiOverlay -> node.children.flatMap(::leafNodes)
        else -> listOf(node)
    }

    @Test
    fun `only tab column keeps presentation complement`() {
        val tabs = listOf(
            SettingsTabEntry(
                label = Component.text("tab"),
                selected = true,
                attentionHover = null,
                background = blockData(),
                outline = null,
                elementId = "lower-tab:0",
            ),
        )
        val tabNodes = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.tabColumnSection(tabNodes, contentFrame(panel), tabs)
        // タブ列は演出用の補完を受けるため未指定のままにします。
        tabNodes.flatMap(::leafNodes).forEach { assertNull(ticks(it), "tab node must keep null complement: ${it.id}") }

        val instantNodes = mutableListOf<GestureGuiNode>()
        val frame = contentFrame(panel)
        DeclarativeSettings.hintSection(instantNodes, frame, Component.text("hint"))
        DeclarativeSettings.descriptionSection(
            instantNodes,
            frame,
            body = Component.text("body"),
            detail = null,
        )
        DeclarativeSettings.valueRowsSection(
            instantNodes,
            frame,
            rows = listOf(SettingsValueRow("row", "label", Component.text("value"), 0.0)),
        )
        DeclarativeSettings.actionCardSection(
            instantNodes,
            frame,
            SettingsActionCard(
                elementId = "action",
                label = Component.text("action"),
                background = blockData(),
                cx = 0.0,
                cy = 0.0,
                w = 0.5,
                h = 0.1,
                labelSize = 0.005,
                labelWidth = 100,
                gestures = GestureGuiClickPolicy.CLICK,
                guard = null,
                hover = null,
                visualTag = "action",
            ),
        )
        DeclarativeSettings.backSection(
            instantNodes,
            frame,
            label = Component.text("back"),
            background = blockData(),
            centerX = 0.0,
            width = 0.5,
        )
        DeclarativeSettings.pagerSection(
            instantNodes,
            frame,
            idPrefix = "pager",
            elementPrefix = "lower-pager-page",
            page = 0,
            pageCount = 1,
            centerX = 0.0,
            centerY = 0.0,
            arrowBackgrounds = listOf(blockData(), blockData()),
            arrowGlyphs = listOf(Component.text("a"), Component.text("b")),
            status = Component.text("1/1"),
        )
        // タブ列以外は補完なし（0）で即時反映します。
        instantNodes.flatMap(::leafNodes).forEach { assertEquals(0, ticks(it), "non-tab node must be instant: ${it.id}") }
    }
}
