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
    fun `event document pages eleven events without overlap`() {
        val names = listOf(
            "BLOCK_BREAK",
            "BLOCK_PLACE",
            "BUTTON_ON",
            "BUTTON_OFF",
            "LEVER_ON",
            "LEVER_OFF",
            "PRESSURE_PLATE_ON",
            "ENTITY_LEFT_CLICK",
            "ENTITY_RIGHT_CLICK",
            "BLOCK_LEFT_CLICK",
            "BLOCK_RIGHT_CLICK",
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
    }
}
