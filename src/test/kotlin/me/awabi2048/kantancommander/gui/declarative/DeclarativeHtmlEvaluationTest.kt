package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.html.GestureGuiHtmlEnvironment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutErrorCode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutFacade
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HTML フロントエンドのプロファイル適用範囲の検証です。
 *
 * 未対応構文の診断・要素ID経由の文言/guard 注入・予約領域の衝突検出を固定します。
 * 画面固有のマークアップ検証は各画面のテスト（[DeclarativeConfirmTest] 等）へ置きます。
 */
class DeclarativeHtmlEvaluationTest {
    private val panel = GestureGuiPanel(width = 2.1213203435596424, height = 1.0606601717798212, frameWidth = 0.045)

    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private val environment = object : GestureGuiHtmlEnvironment {
        override fun buttonBackground(background: String?, classes: Set<String>, id: String?): BlockData = blockData()
        override fun containerBackground(background: String, classes: Set<String>, id: String?): BlockData? = null
        override fun outlineBlock(classes: Set<String>, id: String?): BlockData? = null
        override fun blockData(material: String, classes: Set<String>, id: String?): BlockData = blockData()
        override fun itemStack(reference: String, classes: Set<String>, id: String?): ItemStack =
            throw UnsupportedOperationException("mc-item is out of evaluation scope")
        override fun textSize(tag: String, classes: Set<String>, id: String?): Double = 0.005
    }

    @Test
    fun `diagnostics detect unsupported constructs with source`() {
        val bad = """
            <section id="root">
              <marquee id="m">Hello</marquee>
              <p id="t" style="float: left;">Text</p>
              <button id="b">NoAction</button>
            </section>
        """.trimIndent()
        val parsed = GestureGuiLayoutFacade.parseHtml(bad, environment, "eval.html", panel)
        assertTrue(
            parsed.diagnostics.any { it.code == GestureGuiLayoutErrorCode.UNSUPPORTED_TAG && it.source == "eval.html" },
            "diagnostics: ${parsed.diagnostics}",
        )
        assertTrue(
            parsed.diagnostics.any { it.code == GestureGuiLayoutErrorCode.UNSUPPORTED_PROPERTY },
            "diagnostics: ${parsed.diagnostics}",
        )
        assertTrue(
            parsed.diagnostics.any { it.code == GestureGuiLayoutErrorCode.UNKNOWN_ACTION },
            "diagnostics: ${parsed.diagnostics}",
        )
    }

    @Test
    fun `html bridge carries components and reports reserved area collisions`() {
        val mappedEnvironment = object : GestureGuiHtmlEnvironment by this@DeclarativeHtmlEvaluationTest.environment {
            override fun textComponent(text: String, tag: String, classes: Set<String>, id: String?): Component =
                if (id == "localized") Component.translatable("kantan.test.localized") else Component.text(text)

            override fun gestureGuard(
                tag: String,
                classes: Set<String>,
                id: String?,
            ): ((org.bukkit.entity.Player, GestureGuiGesture) -> Boolean)? =
                if (id == "action") { _, _ -> false } else null
        }
        val html = """
            <section id="root" style="width: 100%; height: 100%;">
              <p id="localized" style="position: absolute; left: 0.25; top: 0.25; width: 0.60; height: 0.10;">localized</p>
              <button id="action" action="action" style="position: absolute; left: 0.25; top: 0.25; width: 0.60; height: 0.10;">Action</button>
            </section>
        """.trimIndent()

        val compiled = KantanLayoutBridge.compileHtml(
            html = html,
            environment = mappedEnvironment,
            onAction = {},
            documentName = "setting-child.html",
            panel = panel,
        )

        val collisions = compiled.diagnostics.filter { it.code == GestureGuiLayoutErrorCode.OVERLAPPING_CONTENT }
        assertTrue(collisions.isNotEmpty(), "diagnostics: ${compiled.diagnostics}")
        assertTrue(collisions.all { it.source == "setting-child.html" })
        assertTrue(compiled.elements.single { it.elementId == "action" }.gestureGuard != null)
        assertEquals(
            Component.translatable("kantan.test.localized"),
            compiled.visuals.filterIsInstance<GestureGuiVisual.Text>().single { it.visualId.contains("localized") }.text,
        )
    }
}
