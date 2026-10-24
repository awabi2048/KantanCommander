package me.awabi2048.kantancommander.gui.declarative

import me.awabi2048.kantancommander.model.TriggerKind
import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import com.awabi2048.ccsystem.api.gesturegui.theme.GestureGuiTheme
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import me.awabi2048.kantancommander.gui.TriggerConditionDraft
import me.awabi2048.kantancommander.model.EventTriggerConditions
import me.awabi2048.kantancommander.model.EventTriggerKind

import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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
        val compiled = KantanLayoutBridge.compile(
            DeclarativeTriggers.detailDocument(
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
                panel,
            ),
            onAction = {},
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
        val compiled = KantanLayoutBridge.compile(DeclarativeTriggers.detailDocument(data, panel), onAction = {})
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
        val detailNodes = (DeclarativeTriggers.detailDocument(data, panel).root as GestureGuiBox).children
        val commandNodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        DeclarativeSettings.tabColumnSection(
            commandNodes,
            contentFrame(panel),
            listOf(
                SettingsTabEntry(Component.text("player filter"), false, null, idleBackground),
                SettingsTabEntry(Component.text("target"), true, null, selectedBackground),
            ),
        )
        val detailTabs = detailNodes.filterIsInstance<GestureGuiBlock>()
            .filter { it.id?.startsWith("lower-trigger-detail-tab:") == true }
        val commandTabs = commandNodes.filterIsInstance<GestureGuiBlock>()
        assertEquals(2, detailTabs.size)
        detailTabs.zip(commandTabs).forEach { (detail, command) ->
            assertEquals(command.absolute, detail.absolute)
            assertEquals(command.width, detail.width)
            assertEquals(command.height, detail.height)
            assertSame(command.blockData, detail.blockData)
            assertNull(detail.outline)
        }
        val detailLabels = detailNodes.filterIsInstance<GestureGuiText>()
            .filter { it.id?.endsWith("-label") == true && it.id?.startsWith("lower-trigger-detail-tab:") == true }
        val commandLabels = commandNodes.filterIsInstance<GestureGuiText>()
        detailLabels.zip(commandLabels).forEach { (detail, command) ->
            assertEquals(command.absolute, detail.absolute)
            assertEquals(command.size, detail.size)
            assertEquals(command.width, detail.width)
        }
        assertEquals(2, detailLabels.size)
        assertTrue(detailNodes.none { it.id == "lower-delete" || it.id == "lower-duplicate" })
    }
}
