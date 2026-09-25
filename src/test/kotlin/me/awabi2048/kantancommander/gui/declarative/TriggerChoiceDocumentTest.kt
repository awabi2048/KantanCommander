package me.awabi2048.kantancommander.gui.declarative

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBlock
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiBox
import com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiText
import me.awabi2048.kantancommander.gui.GestureGuiClickPolicy
import net.kyori.adventure.text.Component
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
        val compiled = KantanLayoutBridge.compile(
            DeclarativeTriggers.kindDocument(data("TIMER", "REDSTONE", "EVENT"), panel),
            onAction = {},
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
    fun `event document pages seven events without overlap`() {
        val names = listOf(
            "BLOCK_BREAK",
            "BLOCK_PLACE",
            "BUTTON_ON",
            "LEVER_CLICK",
            "PRESSURE_PLATE_ON",
            "ENTITY_CLICK",
            "BLOCK_CLICK",
        )
        val first = KantanLayoutBridge.compile(
            DeclarativeTriggers.eventDocument(
                data(*names.toTypedArray()).copy(
                    page = 0,
                    pageCount = 2,
                    pagerAvailableBackground = blockData(),
                    pagerDisabledBackground = blockData(),
                ),
                panel,
            ),
            onAction = {},
        )
        assertTrue(first.diagnostics.isEmpty(), "diagnostics: ${first.diagnostics}")
        val firstIds = first.elements.map { it.elementId }.toSet()
        // 1ページに6件だけを示し、ページャで移動します。
        assertEquals(6, firstIds.count { it.startsWith("lower-trigger-event:") })
        assertTrue(firstIds.any { it.startsWith("lower-trigger-event-page:") })
        // 戻る操作と重ならないよう、ページャは候補の上部にあります。
        assertTrue(firstIds.contains("lower-setting-back"))
        val selected = data(*names.toTypedArray()).copy(
            options = data(*names.toTypedArray()).options.map { it.copy(selected = it.key == "BLOCK_CLICK") },
            page = 1,
            pageCount = 2,
            pagerAvailableBackground = blockData(),
            pagerDisabledBackground = blockData(),
        )
        val second = KantanLayoutBridge.compile(DeclarativeTriggers.eventDocument(selected, panel), onAction = {})
        assertTrue(second.diagnostics.isEmpty(), "diagnostics: ${second.diagnostics}")
        val secondIds = second.elements.map { it.elementId }.toSet()
        assertEquals(1, secondIds.count { it.startsWith("lower-trigger-event:") })
        assertTrue("lower-trigger-event:BLOCK_CLICK" in secondIds)
        assertTrue("lower-trigger-event:BLOCK_BREAK" !in secondIds)
    }

    @Test
    fun `detail screen exposes confirm and back after event selection`() {
        val compiled = KantanLayoutBridge.compile(
            DeclarativeTriggers.detailDocument(
                TriggerDetailData(
                    title = Component.text("details"),
                    eventLabel = "event",
                    eventName = Component.text("block break"),
                    description = Component.text("description"),
                    confirmLabel = Component.text("confirm"),
                    backLabel = Component.text("back"),
                    tabBackground = blockData(),
                    selectedTabBackground = blockData(),
                    tabOutline = blockData(),
                    confirmBackground = blockData(),
                    backBackground = blockData(),
                ),
                panel,
            ),
            onAction = {},
        )
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        assertEquals(
            setOf("lower-trigger-detail-confirm", "lower-setting-back", "lower-trigger-detail-tab:event"),
            compiled.elements.map { it.elementId }.toSet(),
        )
    }

    @Test
    fun `detail screen exposes condition tabs and actions without overlapping ids`() {
        val data = TriggerDetailData(
            title = Component.text("details"),
            eventLabel = "event",
            eventName = Component.text("lever"),
            description = Component.text("description"),
            confirmLabel = Component.text("confirm"),
            backLabel = Component.text("back"),
            tabBackground = blockData(),
            selectedTabBackground = blockData(),
            tabOutline = blockData(),
            confirmBackground = blockData(),
            backBackground = blockData(),
            tabs = listOf(
                TriggerDetailTab("who", Component.text("who"), true),
                TriggerDetailTab("target", Component.text("target"), false),
                TriggerDetailTab("operation", Component.text("operation"), true),
            ),
            selectedTab = 2,
            actions = listOf(
                TriggerDetailAction("any", Component.text("any"), blockData()),
                TriggerDetailAction("on", Component.text("on"), blockData()),
                TriggerDetailAction("off", Component.text("off"), blockData()),
            ),
        )
        val compiled = KantanLayoutBridge.compile(DeclarativeTriggers.detailDocument(data, panel), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
        val ids = compiled.elements.map { it.elementId }.toSet()
        assertTrue(ids.containsAll(setOf(
            "lower-trigger-detail-tab:who", "lower-trigger-detail-tab:target",
            "lower-trigger-detail-tab:operation", "lower-trigger-detail-action:any",
            "lower-trigger-detail-action:on", "lower-trigger-detail-action:off",
            "lower-trigger-detail-confirm", "lower-setting-back",
        )))
    }

    @Test
    fun `detail tabs use the same column geometry and text as command settings`() {
        val idleBackground = blockData()
        val selectedBackground = blockData()
        val data = TriggerDetailData(
            title = Component.text("details"),
            eventLabel = "event",
            eventName = Component.text("lever"),
            description = Component.text("description"),
            confirmLabel = Component.text("confirm"),
            backLabel = Component.text("back"),
            tabBackground = idleBackground,
            selectedTabBackground = selectedBackground,
            tabOutline = blockData(),
            confirmBackground = blockData(),
            backBackground = blockData(),
            tabs = listOf(
                TriggerDetailTab("who", Component.text("who"), true),
                TriggerDetailTab("target", Component.text("target"), false),
            ),
            selectedTab = 1,
        )
        val detailNodes = (DeclarativeTriggers.detailDocument(data, panel).root as GestureGuiBox).children
        val commandNodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        DeclarativeSettings.tabColumnSection(
            commandNodes,
            contentFrame(panel),
            listOf(
                SettingsTabEntry(Component.text("who"), false, null, idleBackground),
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
            assertNotNull(detail.outline, "選択中または設定済みの詳細タブには枠が必要です")
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
