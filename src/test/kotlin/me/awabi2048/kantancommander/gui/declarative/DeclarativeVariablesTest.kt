package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 変数系子画面の宣言移行テストです。旧 `buildWorldVariables` 系との
 * 座標・要素 ID・ホバー置換の一致を検証します。サーバーなしで実行できます。
 */
class DeclarativeVariablesTest {
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

    private fun hover(text: String) = GestureGuiHoverText(
        Component.text(text),
        0.0,
        0.23,
        replacesVisualId = me.awabi2048.kantancommander.gui.GestureLowerPanel.SETTING_DESCRIPTION_HOVER_ID,
    )

    private fun entry(name: String, inUse: Boolean) = WorldVariableEntry(
        name = name,
        label = Component.text(name),
        hover = hover("value type"),
        inUse = inUse,
        deleteHover = hover(if (inUse) "in use" else "delete"),
    )

    private fun listData(entries: List<WorldVariableEntry>, page: Int = 0, pageCount: Int = 1) = WorldVariablesData(
        title = Component.text("title"),
        description = Component.text("desc", NamedTextColor.GRAY),
        emptyLabel = if (entries.isEmpty()) Component.text("empty") else null,
        entries = entries,
        entryBackground = blockData(),
        deleteBackgroundInUse = blockData(),
        deleteBackgroundFree = blockData(),
        page = page,
        pageCount = pageCount,
        pagerAvailableBackground = blockData(),
        pagerDisabledBackground = blockData(),
        createLabel = Component.text("create"),
        createBackground = blockData(),
        backLabel = Component.text("back"),
        backBackground = blockData(),
    )

    @Test
    fun `variables list preserves cards deletes and pager`() {
        val compiled = DeclarativeVariables.variablesCompiled(
            listData(listOf(entry("a", false), entry("b", true))),
            onAction = {},
            theme = stubTheme(),
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        val click = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY)
        assertEquals(
            setOf("lower-variable:a", "lower-variable-delete:a", "lower-variable:b", "lower-variable-delete:b",
                "lower-variables-create", "lower-setting-back"),
            byId.keys,
        )
        byId.values.forEach { assertEquals(click, it.acceptedGestures) }
        // 先頭カードは左列・先頭行に配置されます（列中心 -0.53、カード幅 0.72）。
        val card = byId.getValue("lower-variable:a")
        assertEquals(-0.53 - (0.92 / 2.0 - 0.72 / 2.0) - 0.36, card.bounds.minX, 1.0e-9)
        assertEquals(0.08 - 0.05, card.bounds.minY, 1.0e-9)
        // 削除ボタンはカード右に隣接します。
        val delete = byId.getValue("lower-variable-delete:a")
        assertTrue(delete.bounds.minX > card.bounds.maxX)
        assertEquals(card.bounds.minY, delete.bounds.minY, 1.0e-9)
        // ホバーは常設説明を置換します。
        val descVisual = compiled.visuals.first { it.visualId.contains("setting-description-hover") }
        assertEquals(descVisual.visualId, card.hoverText?.replacesVisualId)
        assertEquals(descVisual.visualId, delete.hoverText?.replacesVisualId)
    }

    @Test
    fun `variables empty and pager branches compile`() {
        val empty = DeclarativeVariables.variablesCompiled(
            listData(emptyList()),
            onAction = {},
            theme = stubTheme(),
        )
        assertTrue(empty.diagnostics.isEmpty(), "diagnostics: ${empty.diagnostics}")
        assertTrue(empty.elements.associateBy { it.elementId }.keys.containsAll(
            setOf("lower-variables-create", "lower-setting-back"),
        ))

        val many = listOf(entry("v6", false))
        val paged = DeclarativeVariables.variablesCompiled(
            listData(many, page = 1, pageCount = 2),
            onAction = {},
            theme = stubTheme(),
        )
        assertTrue(paged.diagnostics.isEmpty(), "diagnostics: ${paged.diagnostics}")
        val ids = paged.elements.associateBy { it.elementId }.keys
        assertTrue(ids.contains("lower-variables-page:0"))
        // 2ページ目は変数 v6 のみです。
        assertTrue(ids.contains("lower-variable:v6"))
        assertTrue(ids.none { it == "lower-variable:v0" })
    }

    @Test
    fun `variable type preserves options and conditional next`() {
        fun typeData(selected: Boolean) = WorldVariableTypeData(
            title = Component.text("title"),
            description = Component.text("desc"),
            options = listOf("NUMBER", "STRING").map { key ->
                WorldVariableTypeOption(
                    key = key,
                    label = Component.text(key),
                    selected = selected && key == "NUMBER",
                    background = blockData(),
                    outline = if (selected && key == "NUMBER") blockData() else null,
                )
            },
            nextLabel = Component.text("next", if (selected) NamedTextColor.WHITE else NamedTextColor.GRAY),
            nextBackground = blockData(),
            nextEnabled = selected,
            backLabel = Component.text("back"),
            backBackground = blockData(),
        )
        val enabled = DeclarativeVariables.typeCompiled(
            typeData(selected = true),
            onAction = {},
            theme = stubTheme(),
        )
        assertTrue(enabled.diagnostics.isEmpty(), "diagnostics: ${enabled.diagnostics}")
        val click = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SHIFT_PRIMARY)
        val byId = enabled.elements.associateBy { it.elementId }
        assertEquals(click, byId.getValue("lower-world-variable-type:NUMBER").acceptedGestures)
        assertEquals(click, byId.getValue("lower-world-variable-type-next").acceptedGestures)
        // 選択肢に白枠が付与されます。
        val outlined = enabled.visuals.filterIsInstance<GestureGuiVisual.Block>().count { it.outline != null }
        assertEquals(1, outlined)

        val disabled = DeclarativeVariables.typeCompiled(
            typeData(selected = false),
            onAction = {},
            theme = stubTheme(),
        )
        assertTrue(disabled.diagnostics.isEmpty(), "diagnostics: ${disabled.diagnostics}")
        // 未選択時の次へは無音消費面になり、診断対象外です。
        val consuming = disabled.elements.filter { it.acceptedGestures.isEmpty() }
        assertEquals(1, consuming.size)
    }

    @Test
    fun `variable delete confirmation preserves actions`() {
        val compiled = DeclarativeVariables.deleteConfirmCompiled(
            WorldVariableDeleteData(
                title = Component.text("title"),
                body = Component.text("body", NamedTextColor.GRAY),
                confirmLabel = Component.text("yes", NamedTextColor.WHITE),
                confirmBackground = blockData(),
                cancelLabel = Component.text("no", NamedTextColor.WHITE),
                cancelBackground = blockData(),
            ),
            onAction = {},
            theme = stubTheme(),
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        // 取消は戻る操作と同一 ID を用います。
        assertEquals(
            setOf("lower-world-variable-delete-confirm", "lower-setting-back"),
            byId.keys,
        )
        val confirm = byId.getValue("lower-world-variable-delete-confirm")
        assertEquals(-0.53 - 0.46, confirm.bounds.minX, 1.0e-9)
        assertEquals(-0.43 - 0.05, confirm.bounds.minY, 1.0e-9)
        assertNull(confirm.hoverText)
    }
}
