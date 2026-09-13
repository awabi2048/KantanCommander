package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * issue #8 の呼び出し設定子画面のテストです。
 *
 * 引数カード・戻り値受取先の要素 ID が操作振分けと一致し、診断なしに変換される
 * ことを固定します。サーバーなしで実行できます。
 */
class DiskCallSettingsDocumentTest {
    private val panel = GestureGuiPanel(width = 2.1213203435596424, height = 1.0606601717798212, frameWidth = 0.045)

    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun data(argumentNames: List<String>, pageCount: Int = 1): DiskCallSettingsData = DiskCallSettingsData(
        title = Component.text("title"),
        body = Component.text("body"),
        emptyArgs = Component.text("none"),
        arguments = argumentNames.map { name ->
            DiskCallArgumentCard(
                name = name,
                label = Component.text(name),
                background = blockData(),
                outline = null,
            )
        },
        page = 0,
        pageCount = pageCount,
        pagerAvailableBackground = blockData(),
        pagerDisabledBackground = blockData(),
        returnLabel = Component.text("return"),
        returnScopeLabel = Component.text("EXECUTION"),
        returnScopeBackground = blockData(),
        returnNameLabel = Component.text("unset"),
        returnNameBackground = blockData(),
        backLabel = Component.text("back"),
        backBackground = blockData(),
    )

    @Test
    fun `argument and return element ids are kept`() {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeDiskCall.settingsDocument(data(listOf("count", "where")), panel),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val ids = compiled.elements.map { it.elementId }.toSet()
        assertTrue(ids.contains("lower-disk-arg:count"))
        assertTrue(ids.contains("lower-disk-arg:where"))
        assertTrue(ids.contains("lower-disk-return-scope"))
        assertTrue(ids.contains("lower-disk-return-name"))
        assertTrue(ids.contains("lower-setting-back"))
        compiled.elements.forEach {
            assertEquals(GestureGuiClickPolicy.CLICK, it.acceptedGestures)
        }
    }

    @Test
    fun `empty arguments and pager render without diagnostics`() {
        val empty = KantanLayoutBridge.compile(
            DeclarativeDiskCall.settingsDocument(data(emptyList()), panel),
            onAction = {},
        )
        assertTrue(empty.diagnostics.isEmpty(), "diagnostics: ${empty.diagnostics}")

        val paged = KantanLayoutBridge.compile(
            DeclarativeDiskCall.settingsDocument(
                data(listOf("a", "b", "c"), pageCount = 2).copy(page = 0),
                panel,
            ),
            onAction = {},
        )
        assertTrue(paged.diagnostics.isEmpty(), "diagnostics: ${paged.diagnostics}")
        assertTrue(paged.elements.any { it.elementId.startsWith("lower-disk-arg-page:") })
    }
}
