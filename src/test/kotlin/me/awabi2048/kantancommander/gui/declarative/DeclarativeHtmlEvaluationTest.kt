package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.html.GestureGuiHtmlEnvironment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiLayoutErrorCode
import com.awabi2048.ccsystem.core.gesturegui.html.GestureGuiHtml
import com.awabi2048.ccsystem.core.gesturegui.layout.GestureGuiLayoutCompiler
import com.awabi2048.ccsystem.core.gesturegui.layout.GestureGuiLayoutEngine
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HTML frontendの適用性評価です。
 *
 * 成果物へ影響しません。確認系子画面をHTMLテンプレートで再記述し、
 * Kotlin DSL版（[DeclarativeConfirm]）との等価性・診断・保守性を記録します。
 * 既知の差異（受付種別・文言色・行幅・丸め）は仕様差として表明します。
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

    // 内容域原点（-1.0157, 0.4853）基準の絶対配置です。DSL側の placed() と同じ換算を
    // 手計算した値であり、HTML側に魔数が露出すること自体が保守性評価の対象です。
    private val html = """
        <section id="confirm-root" style="width: 100%; height: 100%;">
          <p id="confirm-title" style="position: absolute; left: 0.5157; top: 0.3678; width: 1.0; font-size: 0.005;">TITLE</p>
          <p id="confirm-warn" style="position: absolute; left: 0.5157; top: 0.4293; width: 1.0; font-size: 0.004;">WARN</p>
          <button id="confirm-delete" action="confirm-delete" style="position: absolute; left: 0.5057; top: 0.5053; width: 0.48; height: 0.12; font-size: 0.004;">YES</button>
          <button id="confirm-cancel" action="confirm-cancel" style="position: absolute; left: 1.0457; top: 0.5053; width: 0.48; height: 0.12; font-size: 0.004;">NO</button>
        </section>
    """.trimIndent()

    private fun dslCompiled() = KantanLayoutBridge.compile(
        DeclarativeConfirm.document(
            ConfirmTexts(
                title = Component.text("TITLE"),
                warn = Component.text("WARN"),
                yes = Component.text("YES"),
                no = Component.text("NO"),
            ),
            blockData(),
            blockData(),
            panel,
        ),
        onAction = {},
    )

    private fun htmlCompiled() = run {
        val parsed = GestureGuiHtml.parse(html, environment, "confirm.html", panel)
        assertTrue(parsed.diagnostics.isEmpty(), "diagnostics: ${parsed.diagnostics}")
        val actions = setOf("confirm-delete", "confirm-cancel")
        val resolved = GestureGuiLayoutEngine.layout(parsed.document, knownActionIds = actions)
        assertTrue(resolved.diagnostics.isEmpty(), "diagnostics: ${resolved.diagnostics}")
        val compiled = GestureGuiLayoutCompiler.compile(
            resolved,
            screenId = "s",
            actionHandlers = actions.associateWith { {} },
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        compiled
    }

    @Test
    fun `absolute html matches dsl geometry`() {
        val dsl = dslCompiled()
        assertTrue(dsl.diagnostics.isEmpty(), "diagnostics: ${dsl.diagnostics}")
        val html = htmlCompiled()
        // 要素 ID と操作域は一致します（小数第4位の丸め誤差まで）。
        val dslElements = dsl.elements.associateBy { it.elementId }
        val htmlElements = html.view.definition.elements.associateBy { it.elementId }
        assertEquals(dslElements.keys, htmlElements.keys)
        dslElements.forEach { (id, expected) ->
            val actual = htmlElements.getValue(id)
            assertEquals(expected.bounds.minX, actual.bounds.minX, 1.0e-4)
            assertEquals(expected.bounds.minY, actual.bounds.minY, 1.0e-4)
            assertEquals(expected.bounds.maxX, actual.bounds.maxX, 1.0e-4)
            assertEquals(expected.bounds.maxY, actual.bounds.maxY, 1.0e-4)
        }
        // 表示の種類・中心・寸法も一致します（visual ID 体系は異なります）。
        // 文言の border 幅は方式で異なるため、中心座標で比較します。
        // 辞書式整列では丸め誤差で順序がずれるため、数値で整列します。
        fun geometry(visuals: List<GestureGuiVisual>) = visuals.map {
            when (it) {
                is GestureGuiVisual.Block -> listOf("block", it.x, it.y, it.width, it.height)
                is GestureGuiVisual.Text -> listOf("text", it.x, it.y)
                is GestureGuiVisual.Item -> listOf("item", it.x, it.y)
            }
        }.sortedWith(compareBy({ it[0] as String }, { it[1] as Double }, { (it.getOrElse(2) { 0.0 }) as Double }))
        val expected = geometry(dsl.visuals)
        val actual = geometry(html.view.visuals)
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (want, got) ->
            assertEquals(want[0], got[0])
            (1 until want.size).forEach { index ->
                assertEquals(want[index] as Double, got[index] as Double, 1.0e-4)
            }
        }
    }

    @Test
    fun `known html gaps are recorded`() {
        val html = htmlCompiled()
        // 受付種別は PRIMARY のみで、スニーク対応の CLICK 集合になりません（Profile 1 の制限）。
        html.view.definition.elements.forEach {
            assertEquals(setOf(GestureGuiGesture.PRIMARY), it.acceptedGestures)
        }
        // 文言色・行幅は指定できず、無色・既定行幅になります。
        val texts = html.view.visuals.filterIsInstance<GestureGuiVisual.Text>()
        assertTrue(texts.isNotEmpty())
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
        val parsed = GestureGuiHtml.parse(bad, environment, "eval.html", panel)
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
}
