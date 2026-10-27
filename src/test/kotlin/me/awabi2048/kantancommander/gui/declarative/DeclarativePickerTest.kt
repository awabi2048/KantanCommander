package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PICKER画面（`gesture-gui/picker.html`＋注入ノード）の構造検証です。
 *
 * 「リソースが診断なしで解析・注入・コンパイルできる」「要素ID・受付gesture が
 * 旧実装の画面契約を維持する」「区画構成（左タブレール・右カード格子・下端ページャ）
 * が保たれる」ことを固定します。
 *
 * `BlockData` は Proxy、`ItemStack` は未使用のため省略します。テーマ語彙→素材の
 * 実値解決はサーバー側の責務であり、ここではスタブテーマを使います。
 * サーバーなしで実行できます。
 */
class DeclarativePickerTest {
    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    /** 実素材化に Bukkit を要するため、全フィールドをスタブ素材で埋めたテーマです。 */
    private fun stubTheme(): GestureGuiTheme = GestureGuiTheme(
        surface = blockData(),
        accent = blockData(),
        accentDeep = blockData(),
        danger = blockData(),
        back = blockData(),
        on = blockData(),
        off = blockData(),
        outlineSelected = blockData(),
        outlineSet = blockData(),
        disabled = blockData(),
        divider = blockData(),
        scrim = blockData(),
        scrimDanger = blockData(),
    )

    private fun categories() = listOf(
        PickerCategory("Entity", selected = true),
        PickerCategory("Control", selected = false),
    )

    private fun cards() = listOf(
        PickerCard("SAY", "Say", listOf("says"), -0.10, 0.29),
        PickerCard("WAIT", "Wait", listOf("waits"), 0.67, 0.29),
        PickerCard("JUMP", "Jump", listOf("jumps"), -0.10, 0.0),
    )

    private fun data(page: Int = 0, pageCount: Int = 1) = PickerData(
        categories = categories(),
        description = Component.text("desc", NamedTextColor.GRAY),
        cards = cards(),
        pager = if (pageCount > 1) PickerPager(
            arrows = listOf(
                PickerArrow(
                    page - 1,
                    Component.text("◀").color(NamedTextColor.GRAY),
                    available = false,
                ),
                PickerArrow(
                    page + 1,
                    Component.text("▶").color(NamedTextColor.WHITE),
                    available = true,
                ),
            ),
            status = Component.text("${page + 1}/$pageCount"),
        ) else null,
        closeLabel = Component.text("close"),
    )

    private fun compile(data: PickerData = data()) =
        DeclarativePicker.compile(data, onAction = {}, theme = stubTheme())

    @Test
    fun `picker resource compiles without diagnostics`() {
        val compiled = compile()
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
    }

    @Test
    fun `picker preserves element ids and click gestures`() {
        val compiled = compile()
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        val click = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY)
        // 左タブ列・閉じる・種別カードの要素 ID を継承します。
        assertEquals(
            setOf("lower-cat:0", "lower-cat:1", "lower-close-picker", "lower-type:SAY", "lower-type:WAIT", "lower-type:JUMP"),
            byId.keys,
        )
        byId.values.forEach { assertEquals(click, it.acceptedGestures) }
    }

    @Test
    fun `tabs keep dimensions and selected extension`() {
        val compiled = compile()
        val byId = compiled.elements.associateBy { it.elementId }
        // 選択中タブは基準幅0.47を15%伸ばし（0.5405）、左端は内容域端です。
        val tab0 = byId.getValue("lower-cat:0")
        assertEquals(0.5405, tab0.bounds.maxX - tab0.bounds.minX, 1.0e-6)
        assertEquals(0.10, tab0.bounds.maxY - tab0.bounds.minY, 1.0e-6)
        // 非選択タブは基準寸法で、選択中と同じ左端に揃います。
        val tab1 = byId.getValue("lower-cat:1")
        assertEquals(0.47, tab1.bounds.maxX - tab1.bounds.minX, 1.0e-6)
        assertEquals(tab0.bounds.minX, tab1.bounds.minX, 1.0e-6)
        // ピッチ0.11（高さ0.10＋間隔0.01）で縦に積みます。
        assertEquals(0.11, tab0.bounds.minY - tab1.bounds.minY, 1.0e-6)
    }

    @Test
    fun `cards keep 2 column grid metrics`() {
        val compiled = compile()
        val byId = compiled.elements.associateBy { it.elementId }
        val say = byId.getValue("lower-type:SAY")
        val wait = byId.getValue("lower-type:WAIT")
        val jump = byId.getValue("lower-type:JUMP")
        // カードはすべて 0.66×0.10 です。
        for (card in listOf(say, wait, jump)) {
            assertEquals(0.66, card.bounds.maxX - card.bounds.minX, 1.0e-6)
            assertEquals(0.10, card.bounds.maxY - card.bounds.minY, 1.0e-6)
        }
        // 同一行の2カードは同じ高さで、列間隔0.11（左列右端→右列左端）です。
        assertEquals(say.bounds.minY, wait.bounds.minY, 1.0e-6)
        assertEquals(0.11, wait.bounds.minX - say.bounds.maxX, 1.0e-6)
        // 次行は行ピッチ0.11だけ下です。
        assertEquals(0.11, say.bounds.minY - jump.bounds.minY, 1.0e-6)
        // 格子は右領域内で中央揃えです（説明・ページャと同じ列中心）。
        val centerX = (say.bounds.minX + wait.bounds.maxX) / 2.0
        assertEquals(0.28, centerX, 1.0e-3)
    }

    @Test
    fun `card hover keeps description lines separate`() {
        // issue #review P2-10: 説明の2行構成を空白結合で潰さないことを検証します。
        val twoLines = data().copy(
            cards = listOf(PickerCard("SAY", "Say", listOf("機能の説明", "バニラ対応"), -0.10, 0.29)),
        )
        val compiled = compile(twoLines)
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val hover = compiled.elements.associateBy { it.elementId }.getValue("lower-type:SAY").hoverText
        assertNotNull(hover)
        val rendered = hover!!.text.toString()
        assertTrue(rendered.contains("機能の説明"), rendered)
        assertTrue(rendered.contains("バニラ対応"), rendered)
    }

    @Test
    fun `hover coordinates pass through to card elements`() {
        val five = (0 until 5).map { PickerCard("K$it", "k$it", listOf("h$it"), 0.28, -0.38) }
        val compiled = compile(data().copy(cards = five))
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        // 行位置規則の解決値（実行時 adapter が旧 parallelButtonHover で求めます）を
        // そのまま操作面へ載せます。ここでは中段集約スロット値を入力します。
        val middle = compiled.elements.associateBy { it.elementId }.getValue("lower-type:K2").hoverText
        assertNotNull(middle)
        assertEquals(0.28, middle!!.x, 1.0e-9)
        assertEquals(-0.38, middle.y, 1.0e-9)
    }

    @Test
    fun `pager renders both arrows but routes available ones`() {
        val compiled = compile(data(page = 0, pageCount = 2))
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        // 利用不可方向に操作面は作りません。
        assertNull(byId["lower-picker-page:-1"])
        assertNotNull(byId["lower-picker-page:1"])
        // 両方向の背景は描画します（利用可否に関わらず矢印ブロックは2枚）。
        val backgrounds = compiled.visuals.filterIsInstance<GestureGuiVisual.Block>()
            .filter { it.visualId.contains("picker-page") }
        assertEquals(2, backgrounds.size)
        // ページ表示を出します。
        assertTrue(compiled.visuals.filterIsInstance<GestureGuiVisual.Text>().any { it.visualId.contains("page-status") })
    }

    @Test
    fun `single page omits pager elements`() {
        val compiled = compile(data(pageCount = 1))
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        assertTrue(compiled.elements.none { it.elementId.startsWith("lower-picker-page:") })
    }
}
