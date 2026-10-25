package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * テスト系子画面の宣言移行テストです。旧 `buildTestConfirmation` 系との
 * 座標・要素 ID の一致を検証します。サーバーなしで実行できます。
 */
class DeclarativeTestTest {
    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private val click = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY)

    @Test
    fun `test confirmation preserves toggles and actions`() {
        val compiled = DeclarativeTest.confirmCompiled(
            TestConfirmData(
                title = Component.text("title"),
                description = Component.text("desc", NamedTextColor.GRAY),
                toggles = listOf(
                    TestToggleData(
                        id = "test-confirm-debug",
                        label = Component.text("debug on", NamedTextColor.GREEN),
                        background = blockData(),
                        centerY = 0.13,
                    ),
                    TestToggleData(
                        id = "test-confirm-log",
                        label = Component.text("log off", NamedTextColor.GRAY),
                        background = blockData(),
                        centerY = -0.02,
                    ),
                ),
                startLabel = Component.text("start"),
                startBackground = blockData(),
                cancelLabel = Component.text("cancel"),
                cancelBackground = blockData(),
            ),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        assertEquals(
            setOf("test-confirm-debug", "test-confirm-log", "test-confirm-start", "test-confirm-cancel"),
            byId.keys,
        )
        byId.values.forEach { assertEquals(click, it.acceptedGestures) }
        // 開始ボタンの操作域は旧 rect(-0.30, -0.34, 0.50, 0.12) と一致します。
        val start = byId.getValue("test-confirm-start")
        assertEquals(-0.55, start.bounds.minX, 1.0e-9)
        assertEquals(-0.05, start.bounds.maxX, 1.0e-9)
        assertEquals(-0.40, start.bounds.minY, 1.0e-9)
        assertEquals(-0.28, start.bounds.maxY, 1.0e-9)
    }

    @Test
    fun `test status has no interactions`() {
        val compiled = DeclarativeTest.statusCompiled(
            title = Component.text("title"),
            rows = listOf(
                CenteredLine("test-status-elapsed", Component.text("elapsed 0"), 0.21),
                CenteredLine("test-status-count", Component.text("count 0"), 0.00),
                CenteredLine("test-status-log", Component.text("log on"), -0.21),
            ),
            hint = Component.text("hint", NamedTextColor.GRAY),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        // 状態画面に操作面はありません。
        assertTrue(compiled.elements.isEmpty())
        assertEquals(5, compiled.visuals.size)
    }

    @Test
    fun `test result preserves rows and complete action`() {
        val compiled = DeclarativeTest.resultCompiled(
            title = Component.text("title"),
            rows = listOf(
                CenteredLine("test-result-duration", Component.text("duration 0"), 0.22),
                CenteredLine("test-result-wait", Component.text("wait 0"), 0.09),
                CenteredLine("test-result-real", Component.text("real 0"), -0.02),
                CenteredLine("test-result-count", Component.text("count 1"), -0.15),
                CenteredLine("test-result-status", Component.text("ok", NamedTextColor.GREEN), -0.28),
                CenteredLine("test-result-cause", Component.text("cause"), -0.40),
            ),
            completeLabel = Component.text("done"),
            completeBackground = blockData(),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        assertEquals(setOf("test-result-complete"), byId.keys)
        assertEquals(click, byId.getValue("test-result-complete").acceptedGestures)
        val complete = byId.getValue("test-result-complete")
        assertEquals(-0.35, complete.bounds.minX, 1.0e-9)
        assertEquals(0.35, complete.bounds.maxX, 1.0e-9)
        // 題名＋行6件＋確定背景＋確定文言です。
        assertEquals(9, compiled.visuals.size)
    }
}
