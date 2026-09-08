package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
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
 * PICKERの宣言移行テストです。旧 `buildPicker` の配置・要素 ID・ホバー位置との一致を検証します。
 * サーバーなしで実行できます。
 */
class DeclarativePickerTest {
    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun categories() = listOf(
        PickerCategory("Execution", selected = true),
        PickerCategory("Control", selected = false),
    )

    private fun cards() = listOf(
        PickerCard("SAY", "Say", "says", -0.10, 0.29),
        PickerCard("WAIT", "Wait", "waits", 0.67, 0.29),
        PickerCard("JUMP", "Jump", "jumps", -0.10, 0.0),
    )

    private fun data(page: Int = 0, pageCount: Int = 1) = PickerData(
        categories = categories(),
        tabBackgrounds = listOf(blockData(), blockData()),
        description = Component.text("desc", NamedTextColor.GRAY),
        cards = cards(),
        cardBackgrounds = listOf(blockData(), blockData(), blockData()),
        pager = if (pageCount > 1) PickerPager(
            arrows = listOf(
                PickerArrow(
                    page - 1,
                    Component.text("◀").color(NamedTextColor.GRAY),
                    blockData(),
                    available = false,
                ),
                PickerArrow(
                    page + 1,
                    Component.text("▶").color(NamedTextColor.WHITE),
                    blockData(),
                    available = true,
                ),
            ),
            status = Component.text("${page + 1}/$pageCount"),
        ) else null,
        closeLabel = Component.text("close"),
        closeBackground = blockData(),
    )

    @Test
    fun `picker compiles without diagnostics`() {
        val compiled = KantanLayoutBridge.compile(DeclarativePicker.document(data()), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
    }

    @Test
    fun `picker preserves tabs close and cards`() {
        val compiled = KantanLayoutBridge.compile(DeclarativePicker.document(data()), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        val click = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY)
        // 左タブ列・閉じる・種別カードの要素 ID を継承します。
        assertEquals(
            setOf("lower-cat:0", "lower-cat:1", "lower-close-picker", "lower-type:SAY", "lower-type:WAIT", "lower-type:JUMP"),
            byId.keys,
        )
        byId.values.forEach { assertEquals(click, it.acceptedGestures) }
        // 選択中タブは右へ15%伸ばします（幅 0.5405、中心 -0.76225）。
        val tab0 = byId.getValue("lower-cat:0")
        assertEquals(-1.0325, tab0.bounds.minX, 1.0e-9)
        assertEquals(-0.492, tab0.bounds.maxX, 1.0e-9)
        assertEquals(0.38 - 0.05, tab0.bounds.minY, 1.0e-9)
        assertEquals(0.38 + 0.05, tab0.bounds.maxY, 1.0e-9)
        // 非選択タブは基準寸法のままです。
        val tab1 = byId.getValue("lower-cat:1")
        assertEquals(-0.7975 - 0.235, tab1.bounds.minX, 1.0e-9)
        assertEquals(-0.7975 + 0.235, tab1.bounds.maxX, 1.0e-9)
        // 先頭カードは左列・先頭行の rect と一致します。
        val card = byId.getValue("lower-type:SAY")
        assertEquals(-0.10 - 0.33, card.bounds.minX, 1.0e-9)
        assertEquals(-0.10 + 0.33, card.bounds.maxX, 1.0e-9)
        assertEquals(0.20 - 0.05, card.bounds.minY, 1.0e-9)
        assertEquals(0.20 + 0.05, card.bounds.maxY, 1.0e-9)
    }

    @Test
    fun `card hovers follow legacy row positions`() {
        val compiled = KantanLayoutBridge.compile(DeclarativePicker.document(data()), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        // 上段（row 0）はカード上、次の行（最終行）はカード下へ出ます。
        val first = byId.getValue("lower-type:SAY").hoverText
        assertNotNull(first)
        assertEquals(-0.10, first!!.x, 1.0e-9)
        assertEquals(0.20 + 0.05 + 0.04, first.y, 1.0e-9)
        val last = byId.getValue("lower-type:JUMP").hoverText
        assertNotNull(last)
        assertEquals(-0.10, last!!.x, 1.0e-9)
        assertEquals(0.09 - 0.05 - 0.04, last.y, 1.0e-9)
    }

    @Test
    fun `hover coordinates pass through to card elements`() {
        val five = (0 until 5).map { PickerCard("K$it", "k$it", "h$it", 0.28, -0.38) }
        val full = data().copy(cards = five, cardBackgrounds = (0 until 5).map { blockData() })
        val compiled = KantanLayoutBridge.compile(DeclarativePicker.document(full), onAction = {})
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
        val compiled = KantanLayoutBridge.compile(DeclarativePicker.document(data(page = 0, pageCount = 2)), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        // 利用不可方向に操作面は作りません。
        assertNull(byId["lower-picker-page:-1"])
        assertNotNull(byId["lower-picker-page:1"])
        // 両方向の背景は描画します。
        val backgrounds = compiled.visuals.filterIsInstance<GestureGuiVisual.Block>()
            .filter { kotlin.math.abs(it.x - (0.28 - 0.12)) < 1.0e-9 || kotlin.math.abs(it.x - (0.28 + 0.12)) < 1.0e-9 }
        assertEquals(2, backgrounds.size)
        // ページ表示を出します。
        assertTrue(compiled.visuals.filterIsInstance<GestureGuiVisual.Text>().any { it.visualId.contains("page-status") })
    }
}
