package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import java.lang.reflect.Proxy
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * プログラム全体設定の区画テストです。旧 `addScriptSetting` との
 * 要素 ID・操作域・ホバーの一致を検証します。サーバーなしで実行できます。
 *
 * 要素 ID は操作振分けの照合対象であり、背景 visual 用の別 ID を
 * ノード ID へ指定すると振分けに到達しません。この回帰を防ぐため、
 * コンパイル後の要素 ID 全文を表明します。
 */
class DeclarativeScriptSettingsTest {
    private val panel = GestureGuiPanel(width = 2.1213203435596424, height = 1.0606601717798212, frameWidth = 0.045)

    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun card(
        elementId: String,
        cx: Double,
        attentionHover: String?,
    ) = ScriptSettingCard(
        elementId = elementId,
        label = "label",
        value = Component.text("value"),
        background = blockData(),
        // 図柄は解決・変換で参照されないため、単体テストでは省略します。
        icon = null,
        attentionHover = attentionHover?.let {
            // `singleLineHover` と等価な注意ホバーです（常設説明スロット位置）。
            GestureGuiHoverText(Component.text(it), x = 0.28, y = -0.35)
        },
        cx = cx,
        w = 0.60,
    )

    @Test
    fun `script cards keep dispatch element ids`() {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeSettings.scriptSettingsDocument(
                Component.text("hint"),
                listOf(
                    card("lower-script-name", -0.68, null),
                    card("lower-script-timer", 0.0, "unset"),
                    card("lower-script-variables", 0.68, null),
                ),
                panel,
            ),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val elements = compiled.elements.associateBy { it.elementId }
        // 操作振分けが照合する3件の ID がそのまま残ります。
        assertEquals(
            setOf("lower-script-name", "lower-script-timer", "lower-script-variables"),
            elements.keys,
        )
        // 操作域は旧来の矩形（中心・寸法）と一致します。
        val timer = elements.getValue("lower-script-timer")
        assertEquals(0.60, timer.bounds.maxX - timer.bounds.minX, 1.0e-9)
        assertEquals(0.17, timer.bounds.maxY - timer.bounds.minY, 1.0e-9)
        assertEquals(0.0, (timer.bounds.minX + timer.bounds.maxX) / 2.0, 1.0e-9)
        assertEquals(-0.02, (timer.bounds.minY + timer.bounds.maxY) / 2.0, 1.0e-9)
        // 注意ホバーは timer のみに付き、他には付きません。
        assertNotNull(timer.hoverText)
        assertNull(elements.getValue("lower-script-name").hoverText)
        assertNull(elements.getValue("lower-script-variables").hoverText)
        // 受付種別は旧来どおりクリックです。
        elements.values.forEach {
            assertEquals(GestureGuiClickPolicy.CLICK, it.acceptedGestures)
        }
    }
}
