package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 設定候補子画面の区画テストです。旧 `buildSettingChoices(child = true)` との
 * 座標・要素 ID・ホバー置換の一致を検証します。サーバーなしで実行できます。
 */
class DeclarativeSettingChoicesTest {
    private val panel = GestureGuiPanel(width = 2.1213203435596424, height = 1.0606601717798212, frameWidth = 0.045)

    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun frame() = contentFrame(panel)

    private fun choice(
        elementId: String,
        enabled: Boolean,
        hover: GestureGuiHoverText?,
        index: Int,
    ) = SettingsChoiceCard(
        elementId = elementId,
        label = Component.text("label$index"),
        background = blockData(),
        outline = null,
        enabled = enabled,
        cx = if (index % 2 == 0) -0.53 else 0.53,
        cy = 0.08 - (index / 2) * 0.12,
        w = 0.92,
        h = 0.10,
        labelDy = -0.012,
        labelSize = 0.0045,
        labelWidth = 115,
        hover = hover,
        replacesDescription = true,
        visualTag = "setting-choice",
        visualIndex = index,
    )

    @Test
    fun `child screen compiles without diagnostics`() {
        val nodes = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.descriptionSection(
            nodes,
            frame(),
            body = Component.text("desc", NamedTextColor.GRAY),
            detail = null,
            centerX = 0.0,
            hoverY = 0.23,
        )
        DeclarativeSettings.valueRowsSection(
            nodes,
            frame(),
            rows = listOf(SettingsValueRow("setting-header", "field", "value", 0.36)),
            labelX = -0.53,
            valueX = 0.53,
        )
        DeclarativeSettings.choiceCardsSection(
            nodes,
            frame(),
            cards = listOf(
                choice("lower-setting-choice:a", enabled = true, hover = null, index = 0),
                choice("lower-setting-choice:b", enabled = true, hover = null, index = 1),
            ),
        )
        DeclarativeSettings.backSection(
            nodes,
            frame(),
            label = Component.text("back"),
            background = blockData(),
            centerX = 0.0,
            width = 1.70,
        )
        val compiled = KantanLayoutBridge.compile(DeclarativeSettings.document(nodes, panel), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        assertEquals(
            setOf("lower-setting-choice:a", "lower-setting-choice:b", "lower-setting-back"),
            compiled.elements.associateBy { it.elementId }.keys,
        )
    }

    @Test
    fun `child choices use child coordinates and description replacement`() {
        val nodes = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.descriptionSection(
            nodes,
            frame(),
            body = Component.text("desc", NamedTextColor.GRAY),
            detail = null,
            centerX = 0.0,
            hoverY = 0.23,
        )
        DeclarativeSettings.choiceCardsSection(
            nodes,
            frame(),
            cards = listOf(
                choice(
                    "lower-setting-choice:a",
                    enabled = true,
                    hover = GestureGuiHoverText(Component.text("detail"), 0.0, 0.23),
                    index = 0,
                ),
            ),
        )
        val compiled = KantanLayoutBridge.compile(DeclarativeSettings.document(nodes, panel), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val element = compiled.elements.associateBy { it.elementId }.getValue("lower-setting-choice:a")
        // 子画面座標（左右 ±0.53、先頭行 0.08）と一致します。
        assertEquals(-0.53 - 0.46, element.bounds.minX, 1.0e-9)
        assertEquals(-0.53 + 0.46, element.bounds.maxX, 1.0e-9)
        assertEquals(0.08 - 0.05, element.bounds.minY, 1.0e-9)
        // 常設説明の置換先は宣言ノード基準で解決されます。
        val descVisual = compiled.visuals.first { it.visualId.contains("setting-description-hover") }
        assertEquals(descVisual.visualId, element.hoverText?.replacesVisualId)
    }

    @Test
    fun `child pager and hint branches compile`() {
        val hintNodes = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.hintSection(hintNodes, frame(), Component.text("hint"), x = 0.0, y = 0.12, id = "setting-hint")
        DeclarativeSettings.backSection(
            hintNodes,
            frame(),
            label = Component.text("back"),
            background = blockData(),
            centerX = 0.0,
            width = 1.70,
        )
        val hintCompiled = KantanLayoutBridge.compile(DeclarativeSettings.document(hintNodes, panel), onAction = {})
        assertTrue(hintCompiled.diagnostics.isEmpty(), "diagnostics: ${hintCompiled.diagnostics}")
        assertTrue(hintCompiled.elements.associateBy { it.elementId }.containsKey("lower-setting-back"))

        val pagerNodes = mutableListOf<GestureGuiNode>()
        DeclarativeSettings.pagerSection(
            pagerNodes,
            frame(),
            idPrefix = "setting-choice",
            elementPrefix = "lower-setting-choice-page",
            page = 0,
            pageCount = 2,
            centerX = 0.0,
            centerY = -0.34,
            arrowBackgrounds = listOf(blockData(), blockData()),
            arrowGlyphs = listOf(Component.text("◀"), Component.text("▶")),
            status = Component.text("1/2"),
        )
        val pagerCompiled = KantanLayoutBridge.compile(DeclarativeSettings.document(pagerNodes, panel), onAction = {})
        assertTrue(pagerCompiled.diagnostics.isEmpty(), "diagnostics: ${pagerCompiled.diagnostics}")
        val click = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY)
        val elements = pagerCompiled.elements.associateBy { it.elementId }
        assertEquals(click, elements.getValue("lower-setting-choice-page:1").acceptedGestures)
        assertNotNull(elements["lower-setting-choice-page:1"])
    }
}
