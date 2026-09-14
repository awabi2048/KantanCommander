package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiHoverText
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import java.lang.reflect.Proxy
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 設定系区画の宣言移行テストです。旧 helper 群との座標・要素 ID の一致を検証します。
 * サーバーなしで実行できます。
 */
class DeclarativeSettingsTest {
    private val panel = GestureGuiPanel(width = 2.1213203435596424, height = 1.0606601717798212, frameWidth = 0.045)

    private fun blockData(): BlockData = Proxy.newProxyInstance(
        BlockData::class.java.classLoader,
        arrayOf(BlockData::class.java),
    ) { _, _, _ -> null } as BlockData

    private fun player(): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "toString" -> "mock-player"
            "hashCode" -> 1
            "equals" -> false
            else -> null
        }
    } as Player

    private fun frame() = contentFrame(panel)

    @Test
    fun `tabs duplicate and delete preserve legacy geometry`() {
        val nodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        DeclarativeSettings.tabsSection(
            nodes,
            frame(),
            tabs = listOf(
                SettingsTabEntry(Component.text("tab0"), selected = true, attentionHover = null, background = blockData()),
                SettingsTabEntry(Component.text("tab1"), selected = false, attentionHover = null, background = blockData()),
            ),
            duplicateLabel = Component.text("dup"),
            duplicateBackground = blockData(),
            deleteLabel = Component.text("del"),
            deleteBackground = blockData(),
        )
        val compiled = KantanLayoutBridge.compile(DeclarativeSettings.document(nodes, panel), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        assertEquals(setOf("lower-tab:0", "lower-tab:1", "lower-duplicate", "lower-delete"), byId.keys)
        // 選択中タブは右へ15%伸ばします。
        val tab0 = byId.getValue("lower-tab:0")
        assertEquals(-0.7975 + 0.47 * 0.15 / 2.0 - 0.47 * 1.15 / 2.0, tab0.bounds.minX, 1.0e-9)
        assertEquals(-0.7975 + 0.47 * 0.15 / 2.0 + 0.47 * 1.15 / 2.0, tab0.bounds.maxX, 1.0e-9)
        val del = byId.getValue("lower-delete")
        assertEquals(-0.7975 - 0.235, del.bounds.minX, 1.0e-9)
        assertEquals(-0.43 - 0.05, del.bounds.minY, 1.0e-9)
    }

    @Test
    fun `execution tab extracts to a fixed row and actions share one row`() {
        val nodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        DeclarativeSettings.tabsSection(
            nodes,
            frame(),
            tabs = listOf(
                SettingsTabEntry(Component.text("tab0"), selected = true, attentionHover = null, background = blockData()),
                SettingsTabEntry(Component.text("tab1"), selected = false, attentionHover = null, background = blockData()),
                SettingsTabEntry(Component.text("exec"), selected = false, attentionHover = null, background = blockData()),
            ),
            duplicateLabel = Component.text("dup"),
            duplicateBackground = blockData(),
            deleteLabel = Component.text("del"),
            deleteBackground = blockData(),
            executionTabIndex = 2,
        )
        val compiled = KantanLayoutBridge.compile(DeclarativeSettings.document(nodes, panel), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val byId = compiled.elements.associateBy { it.elementId }
        assertEquals(
            setOf("lower-tab:0", "lower-tab:1", "lower-tab:exec", "lower-duplicate", "lower-delete"),
            byId.keys,
        )
        // タブ列の番号は全項目基準のまま変えません。
        val tab1 = byId.getValue("lower-tab:1")
        assertEquals(0.38 - 0.11 - 0.05, tab1.bounds.minY, 1.0e-9)
        // 実行位置タブは下端の操作行の直上へ固定配置します。
        val exec = byId.getValue("lower-tab:exec")
        assertEquals(-0.32 - 0.05, exec.bounds.minY, 1.0e-9)
        assertEquals(-0.32 + 0.05, exec.bounds.maxY, 1.0e-9)
        // 削除・複製は下端1行に半幅で並びます。
        val del = byId.getValue("lower-delete")
        assertEquals(-0.7975 - 0.235, del.bounds.minX, 1.0e-9)
        assertEquals(-0.7975, del.bounds.maxX, 1.0e-9)
        val dup = byId.getValue("lower-duplicate")
        assertEquals(-0.7975, dup.bounds.minX, 1.0e-9)
        assertEquals(-0.7975 + 0.235, dup.bounds.maxX, 1.0e-9)
    }

    @Test
    fun `disabled choice becomes a silent consumer with hover`() {
        val nodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        val hover = GestureGuiHoverText(Component.text("reason"), 0.28, -0.35)
        DeclarativeSettings.choiceCardsSection(
            nodes,
            frame(),
            cards = listOf(
                SettingsChoiceCard(
                    elementId = "lower-setting-choice:x",
                    label = Component.text("X", NamedTextColor.GRAY),
                    background = blockData(),
                    outline = null,
                    enabled = false,
                    cx = -0.10,
                    cy = 0.08,
                    w = 0.66,
                    h = 0.10,
                    labelDy = -0.012,
                    labelSize = 0.0045,
                    labelWidth = 115,
                    hover = hover,
                    replacesDescription = false,
                    visualTag = "setting-choice",
                    visualIndex = 0,
                ),
            ),
        )
        val compiled = KantanLayoutBridge.compile(DeclarativeSettings.document(nodes, panel), onAction = {})
        // 消費面は診断対象外です。
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val element = compiled.elements.single()
        assertEquals("lower-setting-choice:x", element.elementId)
        assertTrue(element.acceptedGestures.isEmpty())
        assertNotNull(element.hoverText)
        assertEquals(0.28, element.hoverText!!.x, 1.0e-9)
    }

    @Test
    fun `choice hover replacement resolves the description node`() {
        val nodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        DeclarativeSettings.descriptionSection(
            nodes,
            frame(),
            body = Component.text("desc", NamedTextColor.GRAY),
            detail = null,
        )
        DeclarativeSettings.choiceCardsSection(
            nodes,
            frame(),
            cards = listOf(
                SettingsChoiceCard(
                    elementId = "lower-setting-choice:y",
                    label = Component.text("Y"),
                    background = blockData(),
                    outline = null,
                    enabled = true,
                    cx = -0.53,
                    cy = 0.08,
                    w = 0.92,
                    h = 0.10,
                    labelDy = -0.012,
                    labelSize = 0.0045,
                    labelWidth = 115,
                    hover = GestureGuiHoverText(Component.text("detail"), 0.0, 0.23),
                    replacesDescription = true,
                    visualTag = "setting-choice",
                    visualIndex = 0,
                ),
            ),
        )
        val compiled = KantanLayoutBridge.compile(
            DeclarativeSettings.document(nodes, panel),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val element = compiled.elements.associateBy { it.elementId }.getValue("lower-setting-choice:y")
        val descVisual = compiled.visuals.first { it.visualId.contains("setting-description-hover") }
        assertEquals(descVisual.visualId, element.hoverText?.replacesVisualId)
    }

    @Test
    fun `action card carries guard gestures and hover`() {
        val nodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        DeclarativeSettings.actionCardSection(
            nodes,
            frame(),
            SettingsActionCard(
                elementId = "lower-edit:seconds",
                label = Component.text("edit", NamedTextColor.GOLD),
                background = blockData(),
                cx = 0.28,
                cy = 0.02,
                w = 1.2,
                h = 0.16,
                labelSize = 0.0055,
                labelWidth = 180,
                gestures = setOf(GestureGuiGesture.PRIMARY, GestureGuiGesture.SECONDARY),
                guard = { _, gesture -> gesture == GestureGuiGesture.PRIMARY },
                hover = GestureGuiHoverText(Component.text("hover"), 0.28, -0.35),
                visualTag = "lower-edit",
            ),
        )
        val compiled = KantanLayoutBridge.compile(DeclarativeSettings.document(nodes, panel), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val element = compiled.elements.single()
        assertEquals("lower-edit:seconds", element.elementId)
        assertEquals(-0.32, element.bounds.minX, 1.0e-9)
        assertEquals(0.88, element.bounds.maxX, 1.0e-9)
        val actor = player()
        assertTrue(element.acceptsGesture(actor, GestureGuiGesture.PRIMARY))
        assertTrue(!element.acceptsGesture(actor, GestureGuiGesture.SECONDARY))
        assertNotNull(element.hoverText)
        assertNull(element.hoverText!!.replacesVisualId)
    }

    @Test
    fun `action card outline passes through to the block visual`() {
        val nodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        DeclarativeSettings.actionCardSection(
            nodes,
            frame(),
            SettingsActionCard(
                elementId = "lower-value-source:item:LITERAL",
                label = Component.text("literal", NamedTextColor.GOLD),
                background = blockData(),
                outline = blockData(),
                cx = -0.04,
                cy = 0.02,
                w = 0.56,
                h = 0.16,
                labelSize = 0.0043,
                labelWidth = 92,
                gestures = setOf(
                    com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture.PRIMARY,
                    com.awabi2048.ccsystem.api.gesturegui.GestureGuiGesture.SHIFT_PRIMARY,
                ),
                guard = null,
                hover = null,
                visualTag = "lower-value-source-literal",
            ),
        )
        val compiled = KantanLayoutBridge.compile(DeclarativeSettings.document(nodes, panel), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val block = compiled.visuals.filterIsInstance<com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual.Block>()
            .single()
        // 選択中白枠が背景へ付与されます。
        assertNotNull(block.outline)
        // 方式指定は手持ちと無関係に操作面を持ちます。
        assertEquals("lower-value-source:item:LITERAL", compiled.elements.single().elementId)
    }

    @Test
    fun `value rows and description place legacy coordinates`() {
        val nodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        DeclarativeSettings.descriptionSection(
            nodes,
            frame(),
            body = Component.text("body", NamedTextColor.GRAY),
            detail = DescriptionDetail(Component.text("warn", NamedTextColor.RED), 0.17),
        )
        DeclarativeSettings.valueRowsSection(
            nodes,
            frame(),
            rows = listOf(SettingsValueRow("lower-setting", "label", Component.text("value"), 0.27)),
        )
        val compiled = KantanLayoutBridge.compile(DeclarativeSettings.document(nodes, panel), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        // 操作面なし・診断なしで文言だけが配置されます。
        assertTrue(compiled.elements.isEmpty())
        assertEquals(4, compiled.visuals.size)
    }
}
