package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * issue #8 のディスク設定子画面のテストです。
 *
 * 取り出し・引数行・追加の要素 ID が操作振分けと一致し、診断なしに変換される
 * ことを固定します。サーバーなしで実行できます。
 */
class DiskSettingsDocumentTest {
    private val panel = GestureGuiPanel(width = 2.1213203435596424, height = 1.0606601717798212, frameWidth = 0.045)

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

    private fun data(argumentNames: List<String>): DiskSettingsData = DiskSettingsData(
        title = Component.text("title"),
        body = Component.text("body"),
        takeOutLabel = Component.text("takeOut"),
        takeOutBackground = blockData(),
        emptyArgs = Component.text("none"),
        arguments = argumentNames.map { name ->
            DiskArgumentRow(
                name = name,
                nameLabel = Component.text(name),
                typeLabel = Component.text("NUMBER"),
                background = blockData(),
                outline = null,
            )
        },
        page = 0,
        pageCount = 1,
        pagerAvailableBackground = blockData(),
        pagerDisabledBackground = blockData(),
        addLabel = Component.text("add"),
        addBackground = blockData(),
        backLabel = Component.text("back"),
        backBackground = blockData(),
    )

    @Test
    fun `take out and argument element ids are kept`() {
        val compiled = DeclarativeDiskSettings.settingsCompiled(
            data(listOf("count", "where")),
            onAction = {},
            panel = panel,
            theme = stubTheme(),
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val ids = compiled.elements.map { it.elementId }.toSet()
        assertTrue(ids.contains("lower-disk-take-out"))
        assertTrue(ids.contains("lower-diskdef-name:count"))
        assertTrue(ids.contains("lower-diskdef-type:where"))
        assertTrue(ids.contains("lower-diskdef-delete:count"))
        assertTrue(ids.contains("lower-diskdef-add"))
        assertTrue(ids.contains("lower-setting-back"))
        compiled.elements.forEach {
            assertEquals(GestureGuiClickPolicy.CLICK, it.acceptedGestures)
        }
    }

    @Test
    fun `empty arguments and pager render without diagnostics`() {
        val empty = DeclarativeDiskSettings.settingsCompiled(
            data(emptyList()),
            onAction = {},
            panel = panel,
            theme = stubTheme(),
        )
        assertTrue(empty.diagnostics.isEmpty(), "diagnostics: ${empty.diagnostics}")

        // arguments は呼び出し側がページ分だけ渡す契約です（1ページ2件）。
        val paged = DeclarativeDiskSettings.settingsCompiled(
            data(listOf("a", "b")).copy(page = 0, pageCount = 2),
            onAction = {},
            panel = panel,
            theme = stubTheme(),
        )
        assertTrue(paged.diagnostics.isEmpty(), "diagnostics: ${paged.diagnostics}")
        assertTrue(paged.elements.any { it.elementId.startsWith("lower-diskdef-page:") })
    }
}
