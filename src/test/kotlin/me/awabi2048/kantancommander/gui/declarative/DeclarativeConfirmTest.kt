package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.html.GestureGuiHtmlEnvironment
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiHover
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiOverlay
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiColumn
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiGrid
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiRow
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiViewport
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * 確認系画面（`gesture-gui/confirm.html`）の構造検証です。
 *
 * マークアップは JAR 同梱リソースであるため、ここでは「リソースが存在し診断なしで
 * 解析・解決・コンパイルできる」「要素ID・action ID・受付gesture が旧実装の画面契約を
 * 維持する」「ダイアログの対称構成（左右均等・文言上段・ボタン下段）が保たれる」
 * ことを固定します。
 *
 * `BlockData` は Proxy、`ItemStack` はコンストラクタを経由しない空インスタンスで
 * 代用します。テーマ語彙→素材の実値解決はサーバー側の責務であり、ここでは扱いません。
 */
class DeclarativeConfirmTest {
    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun itemStack(): ItemStack {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(ItemStack::class.java) as ItemStack
    }

    private val environment = object : GestureGuiHtmlEnvironment {
        override fun textComponent(text: String, tag: String, classes: Set<String>, id: String?): Component =
            Component.text(id ?: text)

        override fun buttonBackground(background: String?, classes: Set<String>, id: String?): BlockData = blockData()
        override fun containerBackground(background: String, classes: Set<String>, id: String?): BlockData? = null
        override fun outlineBlock(classes: Set<String>, id: String?): BlockData? = null
        override fun blockData(material: String, classes: Set<String>, id: String?): BlockData = blockData()
        override fun itemStack(reference: String, classes: Set<String>, id: String?): ItemStack = itemStack()
        override fun textSize(tag: String, classes: Set<String>, id: String?): Double = 0.005
        override fun hover(tag: String, classes: Set<String>, id: String?): GestureGuiHover? = null
        override fun gestureGuard(tag: String, classes: Set<String>, id: String?): ((Player, GestureGuiGesture) -> Boolean)? = null
    }

    private fun compile() = KantanLayoutBridge.compileHtmlResource(
        resourcePath = "gesture-gui/confirm.html",
        environment = environment,
        onAction = {},
    )

    @Test
    fun `確認画面リソースが診断なしでコンパイルされる`() {
        val compiled = compile()
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
    }

    @Test
    fun `要素IDと受付gestureが旧実装の画面契約を維持する`() {
        val compiled = compile()
        val byId = compiled.elements.associateBy { it.elementId }
        // 旧実装の要素 ID を継承し、共有 onAction の振分けが変わりません。
        assertEquals(setOf(DeclarativeConfirm.YES_ID, DeclarativeConfirm.NO_ID), byId.keys)
        val click = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY)
        assertEquals(click, byId.getValue(DeclarativeConfirm.YES_ID).acceptedGestures)
        assertEquals(click, byId.getValue(DeclarativeConfirm.NO_ID).acceptedGestures)
    }

    @Test
    fun `ダイアログの対称構成と情報順が保たれる`() {
        val compiled = compile()
        val byId = compiled.elements.associateBy { it.elementId }
        val yes = byId.getValue(DeclarativeConfirm.YES_ID).bounds
        val no = byId.getValue(DeclarativeConfirm.NO_ID).bounds

        // 確定・取消は画面中央に対して左右対称で、同じ高さ・同じ寸法です。
        assertEquals(-no.maxX, yes.minX, 1.0e-6)
        assertEquals(-no.minX, yes.maxX, 1.0e-6)
        assertEquals(yes.minY, no.minY, 1.0e-6)
        assertEquals(yes.maxY, no.maxY, 1.0e-6)

        // 文言2件（title/warn）＋ボタンラベル2件、ボタン背景2枚です。
        val texts = compiled.visuals.filterIsInstance<GestureGuiVisual.Text>()
        val blocks = compiled.visuals.filterIsInstance<GestureGuiVisual.Block>()
        assertEquals(4, texts.size)
        assertEquals(2, blocks.size)

        // 文言はボタン行より上にあります（情報順：見出し→警告→操作）。
        val textMaxY = texts.filter { !it.visualId.contains("label") }.maxOf { it.y }
        assertTrue(textMaxY > yes.maxY, "文言はボタン行より上である必要があります")
    }
}
