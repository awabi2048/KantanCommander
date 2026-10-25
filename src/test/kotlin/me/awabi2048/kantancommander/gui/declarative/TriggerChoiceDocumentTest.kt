package me.awabi2048.kantancommander.gui.declarative

import me.awabi2048.kantancommander.gui.GestureLowerPanel
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.TriggerConditionDraft
import me.awabi2048.kantancommander.model.EventTriggerConditions
import me.awabi2048.kantancommander.model.EventTriggerKind

import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * issue #7 の起動方法の選択子画面のテストです。
 *
 * 種別選択とイベント選択の文書が診断なしに変換され、操作振分けの照合対象である
 * 要素 ID がそのまま残ることを検証します。サーバーなしで実行できます。
 */
class TriggerChoiceDocumentTest {
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

    private fun data(vararg names: String): TriggerChoiceData = TriggerChoiceData(
        title = Component.text("title"),
        description = Component.text("description"),
        options = names.map { name ->
            TriggerChoiceOption(
                key = name,
                label = Component.text(name),
                selected = false,
                // 素材は解決・変換で参照されないため、単体テストでは省略します。
                background = blockData(),
                outline = null,
            )
        },
        backLabel = Component.text("back"),
        backBackground = blockData(),
    )

    @Test
    fun `kind document keeps dispatch element ids`() {
        val compiled = DeclarativeTriggers.kindCompiled(
            data("TIMER", "REDSTONE", "EVENT"),
            onAction = {},
            panel = panel,
            theme = stubTheme(),
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val ids = compiled.elements.map { it.elementId }.toSet()
        assertTrue(ids.contains("lower-trigger-kind:TIMER"))
        assertTrue(ids.contains("lower-trigger-kind:REDSTONE"))
        assertTrue(ids.contains("lower-trigger-kind:EVENT"))
        // 受付種別は旧来どおりクリックです。
        compiled.elements.forEach {
            assertEquals(GestureGuiClickPolicy.CLICK, it.acceptedGestures)
        }
    }

    @Test
    fun `explicit any clear remains an initialized dirty draft`() {
        val draft = TriggerConditionDraft(
            event = EventTriggerKind.BLOCK_BREAK,
            conditions = EventTriggerConditions(blockType = "STONE"),
            initialized = true,
        )

        val cleared = draft.edit(EventTriggerKind.BLOCK_BREAK) { EventTriggerConditions() }

        assertEquals(EventTriggerKind.BLOCK_BREAK, cleared.event)
        assertEquals(EventTriggerConditions(), cleared.conditions)
        assertTrue(cleared.initialized)
        assertTrue(cleared.dirty)
    }

    @Test

    fun `event document lays out choices on the shared grid policy`() {
        val names = listOf(
            "BLOCK_BREAK",
            "BLOCK_PLACE",
            "BUTTON_ON",
            "LEVER_CLICK",
            "PRESSURE_PLATE_ON",
            "ENTITY_CLICK",
            "BLOCK_CLICK",
        )
        // 7件は1ページ（2列×4行）へ収まり、ページャは出しません。
        val single = DeclarativeTriggers.eventCompiled(
            data(*names.toTypedArray()).copy(
                page = 0,
                pagerAvailableBackground = blockData(),
                pagerDisabledBackground = blockData(),
            ),
            onAction = {},
            panel = panel,
            theme = stubTheme(),
        )
        assertTrue(single.diagnostics.isEmpty(), "diagnostics: ${single.diagnostics}")
        val singleIds = single.elements.map { it.elementId }.toSet()
        assertEquals(7, singleIds.count { it.startsWith("lower-trigger-event:") })
        assertTrue(singleIds.none { it.startsWith("lower-trigger-event-page:") })
        assertTrue(singleIds.contains("lower-setting-back"))

        // 9件を超えると2ページ目へ繰り越し、下端操作行の左列へページャを置きます。
        val many = (1..10).map { "EVENT_$it" }
        val first = DeclarativeTriggers.eventCompiled(
            data(*many.toTypedArray()).copy(
                page = 0,
                pagerAvailableBackground = blockData(),
                pagerDisabledBackground = blockData(),
            ),
            onAction = {},
            panel = panel,
            theme = stubTheme(),
        )
        assertTrue(first.diagnostics.isEmpty(), "diagnostics: ${first.diagnostics}")
        val firstIds = first.elements.map { it.elementId }.toSet()
        assertEquals(8, firstIds.count { it.startsWith("lower-trigger-event:") })
        assertTrue(firstIds.any { it.startsWith("lower-trigger-event-page:") })
        val second = DeclarativeTriggers.eventCompiled(
            data(*many.toTypedArray()).copy(
                options = data(*many.toTypedArray()).options.map {
                    it.copy(selected = it.key == "EVENT_10")
                },
                page = 1,
                pagerAvailableBackground = blockData(),
                pagerDisabledBackground = blockData(),
            ),
            onAction = {},
            panel = panel,
            theme = stubTheme(),
        )
        assertTrue(second.diagnostics.isEmpty(), "diagnostics: ${second.diagnostics}")
        val secondIds = second.elements.map { it.elementId }.toSet()
        assertEquals(2, secondIds.count { it.startsWith("lower-trigger-event:") })
        assertTrue("lower-trigger-event:EVENT_10" in secondIds)
        assertTrue("lower-trigger-event:EVENT_1" !in secondIds)
    }

    @Test
    fun `detail screen exposes confirm back and command target tabs`() {
        val compiled = DeclarativeTriggers.detailCompiled(
            TriggerDetailData(
                eventLabel = "event",
                eventName = Component.text("block break"),
                description = Component.text("description"),
                confirmLabel = Component.text("confirm"),
                backLabel = Component.text("back"),
                tabBackground = blockData(),
                selectedTabBackground = blockData(),
                confirmBackground = blockData(),
                backBackground = blockData(),
                tabs = listOf(
                    TriggerDetailTab("PLAYER_FILTER", Component.text("player filter")),
                    TriggerDetailTab("TARGET", Component.text("target")),
                ),
                selectedTab = 1,
            ),
            onAction = {},
            panel = panel,
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val ids = compiled.elements.map { it.elementId }.toSet()
        assertTrue(ids.containsAll(setOf(
            "lower-trigger-detail-tab:PLAYER_FILTER",
            "lower-trigger-detail-tab:TARGET",
            "lower-trigger-detail-confirm",
            "lower-setting-back",
        )))
    }

    @Test
    fun `detail screen uses choice cards instead of vertical action cards`() {
        val data = TriggerDetailData(
            eventLabel = "event",
            eventName = Component.text("lever"),
            description = Component.text("description"),
            confirmLabel = Component.text("confirm"),
            backLabel = Component.text("back"),
            tabBackground = blockData(),
            selectedTabBackground = blockData(),
            confirmBackground = blockData(),
            backBackground = blockData(),
            tabs = listOf(
                TriggerDetailTab("PLAYER_FILTER", Component.text("player filter")),
                TriggerDetailTab("TARGET", Component.text("target")),
                TriggerDetailTab("OPERATION", Component.text("operation")),
            ),
            selectedTab = 2,
            choices = listOf(
                SettingsChoiceCard(
                    elementId = "lower-setting-choice:trigger-operation:any",
                    label = Component.text("any"),
                    background = blockData(),
                    outline = null,
                    enabled = true,
                    cx = -0.10,
                    cy = 0.20,
                    w = 0.66,
                    h = 0.10,
                    labelDy = -0.012,
                    labelSize = 0.0045,
                    labelWidth = 115,
                    hover = null,
                    replacesDescription = false,
                    visualTag = "setting-choice",
                    visualIndex = 0,
                ),
            ),
        )
        val compiled = DeclarativeTriggers.detailCompiled(
            data,
            onAction = {},
            panel = panel,
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val ids = compiled.elements.map { it.elementId }.toSet()
        assertTrue("lower-setting-choice:trigger-operation:any" in ids)
        assertTrue(ids.none { it.startsWith("lower-trigger-detail-action:") })
    }

    @Test
    fun `detail tabs use command geometry without configured outlines`() {
        val idleBackground = blockData()
        val selectedBackground = blockData()
        val data = TriggerDetailData(
            eventLabel = "event",
            eventName = Component.text("lever"),
            description = Component.text("description"),
            confirmLabel = Component.text("confirm"),
            backLabel = Component.text("back"),
            tabBackground = idleBackground,
            selectedTabBackground = selectedBackground,
            confirmBackground = blockData(),
            backBackground = blockData(),
            tabs = listOf(
                TriggerDetailTab("PLAYER_FILTER", Component.text("player filter")),
                TriggerDetailTab("TARGET", Component.text("target")),
            ),
            selectedTab = 1,
        )
        val detail = DeclarativeTriggers.detailCompiled(
            data,
            onAction = {},
            panel = panel,
        )
        val commandNodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        DeclarativeSettings.tabColumnSection(
            commandNodes,
            contentFrame(panel),
            listOf(
                SettingsTabEntry(Component.text("player filter"), false, null, idleBackground),
                SettingsTabEntry(Component.text("target"), true, null, selectedBackground),
            ),
        )
        val command = KantanLayoutBridge.compile(DeclarativeSettings.document(commandNodes, panel), onAction = {})
        assertTrue(detail.diagnostics.isEmpty(), "diagnostics: ${detail.diagnostics}")
        // タブは操作面＋背景＋文言のOverlay構造です。操作域・背景・文言の位置は
        // コマンド設定のタブ列と一致させます（タブ順は上からのためY降順で対応付けます）。
        val detailTabs = listOf("PLAYER_FILTER", "TARGET").map { key ->
            detail.elements.first { it.elementId == "lower-trigger-detail-tab:$key" }
        }
        val commandTabs = listOf("lower-tab:0", "lower-tab:1").map { id ->
            command.elements.first { it.elementId == id }
        }
        detailTabs.zip(commandTabs).forEach { (detailTab, commandTab) ->
            assertEquals(commandTab.bounds.minX, detailTab.bounds.minX, 1.0e-9)
            assertEquals(commandTab.bounds.maxX, detailTab.bounds.maxX, 1.0e-9)
            assertEquals(commandTab.bounds.minY, detailTab.bounds.minY, 1.0e-9)
            assertEquals(commandTab.bounds.maxY, detailTab.bounds.maxY, 1.0e-9)
        }
        val detailBgs = detail.visuals.filterIsInstance<com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual.Block>()
            .filter { it.visualId.contains("trigger-detail-tab:") && it.visualId.endsWith("-bg") }
            .sortedByDescending { it.y }
        val commandBgs = command.visuals.filterIsInstance<com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual.Block>()
            .filter { it.visualId.contains("lower-tab:") && it.visualId.endsWith("-bg") }
            .sortedByDescending { it.y }
        assertEquals(2, detailBgs.size)
        detailBgs.zip(commandBgs).forEach { (detailBg, commandBg) ->
            assertEquals(commandBg.x, detailBg.x, 1.0e-9)
            assertEquals(commandBg.y, detailBg.y, 1.0e-9)
            assertEquals(commandBg.width, detailBg.width, 1.0e-9)
            assertEquals(commandBg.height, detailBg.height, 1.0e-9)
            assertNull(detailBg.outline)
        }
        val detailLabels = detail.visuals.filterIsInstance<com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual.Text>()
            .filter { it.visualId.contains("trigger-detail-tab:") }
            .sortedByDescending { it.y }
        val commandLabels = command.visuals.filterIsInstance<com.awabi2048.ccsystem.api.gesturegui.GestureGuiVisual.Text>()
            .filter { it.visualId.contains("visual-tab-") }
            .sortedByDescending { it.y }
        detailLabels.zip(commandLabels).forEach { (detailLabel, commandLabel) ->
            assertEquals(commandLabel.x, detailLabel.x, 1.0e-9)
            assertEquals(commandLabel.y, detailLabel.y, 1.0e-9)
        }
        assertEquals(2, detailLabels.size)
        assertTrue(detail.elements.none { it.elementId == "lower-delete" || it.elementId == "lower-duplicate" })
    }

    @Test
    fun `detail screen keeps value row and detail hint apart`() {
        // 実運用では現在値行と詳細案内が同時に出ます。行どうしの予約領域が
        // 重ならない配置になっていることを、診断なしの変換で確認します。
        val compiled = DeclarativeTriggers.detailCompiled(
            TriggerDetailData(
                eventLabel = "event",
                eventName = Component.text("lever"),
                description = Component.text("description"),
                descriptionDetail = DescriptionDetail(
                    Component.text("open filters"),
                    GestureLowerPanel.SETTING_DETAIL_ANCHOR_Y,
                ),
                confirmLabel = Component.text("confirm"),
                backLabel = Component.text("back"),
                tabBackground = blockData(),
                selectedTabBackground = blockData(),
                confirmBackground = blockData(),
                backBackground = blockData(),
                tabs = listOf(
                    TriggerDetailTab("PLAYER_FILTER", Component.text("player filter")),
                    TriggerDetailTab("TARGET", Component.text("target")),
                ),
                selectedTab = 1,
                values = listOf(
                    SettingsValueRow(
                        "trigger-detail-value",
                        "value",
                        Component.text("configured"),
                        GestureLowerPanel.SETTING_VALUE_ANCHOR_Y - 0.055,
                    ),
                ),
            ),
            onAction = {},
            panel = panel,
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        assertTrue(compiled.elements.any { it.elementId == "lower-trigger-detail-confirm" })
    }
}
