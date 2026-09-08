package me.awabi2048.kantancommander.gui

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiBounds
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiElement
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * グラフCustom adapterのテストです。旧来経路との等価性を検証します。
 * サーバーなしで実行できます（品目表示は実機検証とします）。
 */
class GraphCustomAdapterTest {
    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun frame() = GraphViewportMapper.frame(
        minX = -1.0, maxX = 1.0, minY = -0.5, maxY = 0.5,
        columns = 4, rows = 2, scale = 1.0,
    )

    @Test
    fun `scale applies to map visuals and elements only`() {
        val mapBlock = GestureGuiVisual.Block("node-bg-1", 0.1, 0.2, 0.3, 0.3, blockData(), 4)
        val mapText = GestureGuiVisual.Text("node-label-1", 0.1, 0.2, Component.text("a"))
        val chrome = GestureGuiVisual.Block("back-block", 1.0, 1.0, 0.2, 0.2, blockData(), 4)
        val mapElement = GestureGuiElement(
            "node:1",
            GestureGuiBounds(0.0, 0.1, 0.2, 0.3),
            setOf(GestureGuiGesture.PRIMARY),
            GestureGuiHoverText(Component.text("h"), 0.1, 0.2),
            "node-icon-1",
        )
        val chromeElement = GestureGuiElement("nav-close", GestureGuiBounds(0.9, 0.9, 1.0, 1.0), setOf(GestureGuiGesture.PRIMARY))
        val scaled = scaleGraphOutput(
            listOf(mapBlock, mapText, chrome),
            listOf(mapElement, chromeElement),
            2.0,
        )
        val block = scaled.visuals.filterIsInstance<GestureGuiVisual.Block>().single { it.visualId == "node-bg-1" }
        assertEquals(0.2, block.x, 1.0e-9)
        assertEquals(0.6, block.width, 1.0e-9)
        val text = scaled.visuals.filterIsInstance<GestureGuiVisual.Text>().single()
        assertEquals(0.2, text.x, 1.0e-9)
        val chromeKept = scaled.visuals.filterIsInstance<GestureGuiVisual.Block>().single { it.visualId == "back-block" }
        assertEquals(1.0, chromeKept.x, 1.0e-9)
        val element = scaled.elements.single { it.elementId == "node:1" }
        assertEquals(0.4, element.bounds.maxX, 1.0e-9)
        assertEquals(0.2, element.hoverText!!.x, 1.0e-9)
        assertEquals(1.0, scaled.elements.single { it.elementId == "nav-close" }.bounds.maxX, 1.0e-9)
    }

    @Test
    fun `custom conversion preserves ids and declares text bounds`() {
        val output = ScaledGraphOutput(
            visuals = listOf(
                GestureGuiVisual.Block("node-bg-1", 0.0, 0.0, 0.2, 0.2, blockData(), 4),
                GestureGuiVisual.Text("node-label-1", 0.0, 0.0, Component.text("a")),
            ),
            elements = listOf(
                GestureGuiElement(
                    "node:1",
                    GestureGuiBounds(-0.1, -0.1, 0.1, 0.1),
                    setOf(GestureGuiGesture.PRIMARY),
                    GestureGuiHoverText(Component.text("h"), 0.0, 0.1),
                    "node-icon-1",
                    { _, _ -> true },
                ),
                GestureGuiElement(
                    "path:return:0:0",
                    GestureGuiBounds(-0.1, -0.1, 0.1, 0.1),
                    emptySet(),
                    GestureGuiHoverText(Component.text("r"), 0.0, 0.1),
                ),
            ),
        )
        val result = toCustomResult(output, frame())
        // 表示IDはそのまま引き継ぎます。
        assertEquals(
            setOf("node-bg-1", "node-label-1"),
            result.visuals.map { it.visualId }.toSet(),
        )
        // 文言の表示矩形は旧切断仮定（0.06/0.04）と同一式で申告します。
        val declared = result.visualBounds.getValue("node-label-1")
        assertEquals(-0.06, declared.minX, 1.0e-9)
        assertEquals(0.06, declared.maxX, 1.0e-9)
        assertEquals(-0.04, declared.minY, 1.0e-9)
        // Blockは幅・高さから求まるため申告しません。
        assertNull(result.visualBounds["node-bg-1"])
        // 操作は要素IDをactionへ写し、受付なしは無音消費にします。
        val byId = result.elements.associateBy { it.elementId }
        val node = byId.getValue("node:1")
        assertEquals(setOf(GestureGuiGesture.PRIMARY), node.acceptedGestures)
        assertEquals("node:1", node.actionId)
        assertNotNull(node.gestureGuard)
        assertNotNull(node.hoverText)
        assertEquals("node-icon-1", node.targetVisualId)
        val returning = byId.getValue("path:return:0:0")
        assertTrue(returning.acceptedGestures.isEmpty())
        assertNull(returning.actionId)
        assertTrue(returning.consumeInput)
    }

    @Test
    fun `shadow diff is empty on match and flags divergence`() {
        val visuals = listOf<GestureGuiVisual>(
            GestureGuiVisual.Block("node-bg-1", 0.0, 0.0, 0.2, 0.2, blockData(), 4),
            GestureGuiVisual.Text("node-label-1", 5.0, 5.0, Component.text("far")),
        )
        val elements = listOf(
            GestureGuiElement("node:1", GestureGuiBounds(-0.1, -0.1, 0.1, 0.1), setOf(GestureGuiGesture.PRIMARY)),
        )
        val clip = GestureGuiBounds(-1.0, -0.5, 1.0, 0.5)
        // 画面内の文言は残り、画面外は除去されます。
        val kept = visuals.take(1)
        val result = toCustomResult(ScaledGraphOutput(visuals, elements), frame())
        assertTrue(
            diffGraphShadow(visuals, elements, kept, elements, result, clip).isEmpty(),
        )
        // 申告なし・ガード不一致・未知要素を検出します。
        val broken = result.copy(
            visuals = result.visuals.filter { it.visualId != "node-bg-1" },
            elements = result.elements.map {
                if (it.elementId == "node:1") it.copy(acceptedGestures = emptySet(), actionId = null) else it
            } + ghostElement(),
        )
        val diffs = diffGraphShadow(visuals, elements, kept, elements, broken, clip)
        assertTrue(diffs.any { it.contains("missing visual") }, diffs.toString())
        assertTrue(diffs.any { it.contains("extra custom element") }, diffs.toString())
    }

    private fun ghostElement() =
        com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiCustomElement(
            elementId = "ghost",
            bounds = GestureGuiBounds(0.0, 0.0, 0.1, 0.1),
            acceptedGestures = setOf(GestureGuiGesture.PRIMARY),
            actionId = "ghost",
        )
}
