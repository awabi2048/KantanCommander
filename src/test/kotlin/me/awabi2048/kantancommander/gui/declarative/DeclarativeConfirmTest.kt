package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 確認系の宣言移行テストです。旧 `buildConfirm` の座標・要素 ID との一致を検証します。
 * 文言は固定値で与え、サーバーなしで実行できます。
 */
class DeclarativeConfirmTest {
    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun texts() = ConfirmTexts(
        title = Component.text("title"),
        warn = Component.text("warn"),
        yes = Component.text("yes"),
        no = Component.text("no"),
    )

    @Test
    fun `confirm compiles without diagnostics`() {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeConfirm.document(texts(), blockData(), blockData()),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
    }

    @Test
    fun `confirm preserves legacy element ids and bounds`() {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeConfirm.document(texts(), blockData(), blockData()),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        // 旧実装の要素 ID を継承し、共有 onAction の振分けが変わりません。
        assertEquals(setOf("confirm-delete", "confirm-cancel"), byId.keys)
        val click = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY)
        val yes = byId.getValue("confirm-delete")
        assertEquals(click, yes.acceptedGestures)
        // 旧 rect(-0.27, -0.08, 0.48, 0.12) と一致します。
        assertEquals(-0.51, yes.bounds.minX, 1.0e-9)
        assertEquals(-0.14, yes.bounds.minY, 1.0e-9)
        assertEquals(-0.03, yes.bounds.maxX, 1.0e-9)
        assertEquals(-0.02, yes.bounds.maxY, 1.0e-9)
        val no = byId.getValue("confirm-cancel")
        assertEquals(click, no.acceptedGestures)
        assertEquals(0.03, no.bounds.minX, 1.0e-9)
        assertEquals(-0.14, no.bounds.minY, 1.0e-9)
        assertEquals(0.51, no.bounds.maxX, 1.0e-9)
        assertEquals(-0.02, no.bounds.maxY, 1.0e-9)
    }

    @Test
    fun `confirm preserves legacy visuals`() {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeConfirm.document(texts(), blockData(), blockData()),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val blocks = compiled.visuals.filterIsInstance<GestureGuiVisual.Block>()
        assertEquals(2, blocks.size)
        val yesBg = blocks.single { it.x < 0.0 }
        assertEquals(-0.27, yesBg.x, 1.0e-9)
        assertEquals(-0.08, yesBg.y, 1.0e-9)
        assertEquals(0.48, yesBg.width, 1.0e-9)
        assertEquals(0.12, yesBg.height, 1.0e-9)
        val noBg = blocks.single { it.x > 0.0 }
        assertEquals(0.27, noBg.x, 1.0e-9)
        assertEquals(-0.08, noBg.y, 1.0e-9)
        val texts = compiled.visuals.filterIsInstance<GestureGuiVisual.Text>()
        assertEquals(4, texts.size)
        val centers = texts.map { it.x to it.y }
        fun containsCenter(x: Double, y: Double) =
            centers.any { (cx, cy) -> kotlin.math.abs(cx - x) < 1.0e-9 && kotlin.math.abs(cy - y) < 1.0e-9 }
        // 旧 addText の中心座標と一致します。
        assertTrue(containsCenter(0.0, 0.11), "centers: $centers")
        assertTrue(containsCenter(0.0, 0.05), "centers: $centers")
        assertTrue(containsCenter(-0.27, -0.08), "centers: $centers")
        assertTrue(containsCenter(0.27, -0.08), "centers: $centers")
    }
}
