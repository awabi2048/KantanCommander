package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomRenderer
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomRenderResult
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomElement
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 上部外枠の宣言移行テストです。旧来座標・要素 ID との一致を検証します。
 * サーバーなしで実行できます。
 */
class DeclarativeUpperTest {
    private val panel = GestureGuiPanel(width = 2.90, height = 1.0606601717798212, frameWidth = 0.045)
    private val click = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY)

    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun player(): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "toString" -> "mock-player"
            "hashCode" -> 1
            "equals" -> false
            else -> null
        }
    } as Player

    private val viewport = UpperViewportRect(minX = -1.0, minY = -0.4, maxX = 1.0, maxY = 0.4)
    private val emptyRect = UpperViewportRect(minX = -1.4, minY = -0.48, maxX = 1.4, maxY = 0.48)

    private fun chromeButton() = SettingsActionCard(
        elementId = "nav-up",
        label = Component.text("▲", NamedTextColor.WHITE),
        background = blockData(),
        cx = 1.1,
        cy = 0.2,
        w = 0.12,
        h = 0.12,
        labelSize = 0.011,
        labelWidth = 90,
        labelDy = -0.01,
        gestures = click,
        guard = { _, _ -> true },
        hover = GestureGuiHoverText(Component.text("up"), 1.1, 0.3),
        visualTag = "nav-up",
    )

    private fun stubRenderer() = GestureGuiCustomRenderer { _, _ ->
        GestureGuiCustomRenderResult(
            visuals = listOf(
                GestureGuiVisual.Block("node-bg", 0.0, 0.0, 0.2, 0.2, blockData(), 4),
            ),
            elements = listOf(
                GestureGuiCustomElement(
                    elementId = "node:1",
                    bounds = GestureGuiBounds(-0.1, -0.1, 0.1, 0.1),
                    acceptedGestures = click,
                    actionId = "node:1",
                    targetVisualId = "node-bg",
                ),
            ),
            visualBounds = emptyMap(),
        )
    }

    private fun compile(
        chrome: List<SettingsActionCard> = listOf(chromeButton()),
        includeEmpty: Boolean = true,
        extraActions: Set<String> = setOf("node:1"),
    ) = KantanLayoutBridge.compile(
        DeclarativeUpper.document(viewport, emptyRect, chrome, includeEmpty, panel),
        onAction = {},
        extraActionIds = extraActions,
        customRenderers = mapOf(DeclarativeUpper.GRAPH_RENDERER_ID to stubRenderer()),
    )

    @Test
    fun `upper compiles without diagnostics`() {
        val compiled = compile()
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
    }

    @Test
    fun `element order preserves empty viewport graph and chrome`() {
        val compiled = compile()
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val ids = compiled.elements.map { it.elementId }
        // 余白解除が先頭、グラフ要素、操作列の順序を維持します。
        assertEquals("viewport-empty", ids.first())
        assertEquals("nav-up", ids.last())
        assertTrue(ids.contains("graph/node:1"), "ids: $ids")
        assertTrue(ids.indexOf("graph/node:1") > ids.indexOf("viewport-empty"))
        assertTrue(ids.indexOf("graph/node:1") < ids.indexOf("nav-up"))
    }

    @Test
    fun `viewport and chrome preserve legacy geometry`() {
        val compiled = compile()
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        val empty = byId.getValue("viewport-empty")
        assertEquals(-1.4, empty.bounds.minX, 1.0e-9)
        assertEquals(1.4, empty.bounds.maxX, 1.0e-9)
        assertEquals(-0.48, empty.bounds.minY, 1.0e-9)
        assertEquals(0.48, empty.bounds.maxY, 1.0e-9)
        val nav = byId.getValue("nav-up")
        assertEquals(1.1 - 0.06, nav.bounds.minX, 1.0e-9)
        assertEquals(1.1 + 0.06, nav.bounds.maxX, 1.0e-9)
        assertEquals(0.2 - 0.06, nav.bounds.minY, 1.0e-9)
        assertEquals(click, nav.acceptedGestures)
        assertNotNull(nav.gestureGuard)
        assertNotNull(nav.hoverText)
        assertEquals(1.1, nav.hoverText!!.x, 1.0e-9)
        // グラフ要素は接頭辞付きで compiler を通過します。
        val graph = byId.getValue("graph/node:1")
        assertEquals(-0.1, graph.bounds.minX, 1.0e-9)
        assertEquals("graph/node-bg", graph.targetVisualId)
    }

    @Test
    fun `unlabeled card creates interaction without text`() {
        val compiled = compile(
            chrome = listOf(
                chromeButton().copy(elementId = "test-execution", label = null, visualTag = "test-execution"),
            ),
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        assertTrue(compiled.elements.associateBy { it.elementId }.containsKey("test-execution"))
        // 文言表示は作りません（背景のみ）。
        assertTrue(compiled.visuals.none { it.visualId.contains("test-execution-label") })
    }

    @Test
    fun `strip removes only the graph namespace`() {
        assertEquals("node:1", DeclarativeUpper.stripGraphPrefix("graph/node:1"))
        assertEquals("nav-up", DeclarativeUpper.stripGraphPrefix("nav-up"))
        assertEquals("viewport-empty", DeclarativeUpper.stripGraphPrefix("viewport-empty"))
        assertEquals("graph", DeclarativeUpper.stripGraphPrefix("graph"))
    }
}
