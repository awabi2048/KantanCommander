package me.awabi2048.kantancommander.gui.setting

import com.awabi2048.ccsystem.api.gesturegui.GestureGuiPanel
import java.lang.reflect.Proxy
import java.util.UUID
import me.awabi2048.kantancommander.gui.CommandSettingContext
import me.awabi2048.kantancommander.gui.CommandSettingRole
import me.awabi2048.kantancommander.gui.declarative.KantanLayoutBridge
import me.awabi2048.kantancommander.gui.declarative.SettingsChoiceCard
import me.awabi2048.kantancommander.gui.declarative.contentFrame
import me.awabi2048.kantancommander.gui.declarative.DeclarativeSettings
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.PositionKind
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 汎用設定層の回帰テストです。サーバーなしで実行できます。
 *
 * - 対象・位置・向きをコマンド固有に分岐させないこと
 * - 二段階フローが同一規則であること
 * - 候補文言IDが要素ID由来で重複診断にならないこと
 */
class GenericSettingProvidersTest {
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

    private fun node(): CommandNode = CommandNode(type = CommandType.TELEPORT)

    private fun context(role: CommandSettingRole?): CommandSettingContext =
        CommandSettingContext(UUID.randomUUID(), UUID.randomUUID(), role)

    private fun fakeText(): (com.awabi2048.ccsystem.api.localization.LocalizationKey<String>) -> String =
        { it.id.substringAfterLast(".") }

    @Test
    fun `facing provides unified five choices without legacy kinds`() {
        val choices = FacingSettingProvider.choices(node(), context(CommandSettingRole.DESTINATION_FACING), player(), text = fakeText())
        // 上段3＋下段2の順序を固定します。
        assertEquals(
            listOf("facing:COORDINATES", "facing:ROTATION", "facing:TARGET", "facing:COPY_ORIENTATION", "facing:TEMPORARY"),
            choices.map { it.id },
        )
        assertFalse(choices.any { it.id.contains("CAPTURED") })
        assertFalse(choices.any { it.id.contains("MYWORLD_SPAWN") })
        // 注視・複写は単一体参照の子を持ちます。
        val target = choices.single { it.id == "facing:${FacingKind.TARGET.name}" }
        val copy = choices.single { it.id == "facing:${FacingKind.COPY_ORIENTATION.name}" }
        assertTrue(target.hasChildren)
        assertTrue(copy.hasChildren)
        // 単一参照に一時変数と上限数は含めません。
        val childIds = (target.children + copy.children).map { it.id }.toSet()
        assertFalse(childIds.any { it == "target:TEMPORARY" })
        val filterIds = (target.children.flatMap { it.children } + copy.children.flatMap { it.children }).map { it.id }.toSet()
        assertFalse(filterIds.contains("filter:limit"))
    }

    @Test
    fun `facing choices do not depend on role`() {
        val destination = FacingSettingProvider.choices(node(), context(CommandSettingRole.DESTINATION_FACING), player(), text = fakeText())
        val temporary = FacingSettingProvider.choices(node(), context(CommandSettingRole.TEMPORARY_LOCATION_FACING), player(), text = fakeText())
        assertEquals(destination.map { it.id }, temporary.map { it.id })
    }

    @Test
    fun `position destination delegates entity reference to target`() {
        val choices = PositionSettingProvider.choices(node(), context(CommandSettingRole.DESTINATION), player(), text = fakeText())
        assertEquals(
            listOf("position:COORDINATES", "position:TARGET", "position:TEMPORARY"),
            choices.map { it.id },
        )
        val target = choices.single { it.id == "position:${PositionKind.TARGET.name}" }
        assertTrue(target.hasChildren)
        // 一時変数は対象設定側に残し、移動先実体参照へ混在させません。
        assertFalse(target.children.any { it.id == "target:TEMPORARY" })
    }

    @Test
    fun `single entity excludes temporary and limit`() {
        val categories = ReferenceSettingProviders.singleEntityCategories(
            node(),
            context(CommandSettingRole.DESTINATION_FACING),
            player(),
            text = fakeText(),
        )
        assertFalse(categories.any { it.id == "target:TEMPORARY" })
        val filters = ReferenceSettingProviders.targetFilters(
            node(),
            context(CommandSettingRole.DESTINATION_FACING),
            player(),
            singleOnly = true,
            text = fakeText(),
        )
        assertFalse(filters.any { it.id == "filter:limit" })
        val normal = ReferenceSettingProviders.targetFilters(
            node(),
            context(CommandSettingRole.NODE_TARGET),
            player(),
            singleOnly = false,
            text = fakeText(),
        )
        // 通常対象には上限数が残ります（種別によっては非表示の場合があります）。
        assertTrue(normal.isNotEmpty())
    }

    @Test
    fun `two stage engine enters child only when selected with children`() {
        assertTrue(SettingSelectionEngine.shouldEnterChild(wasSelected = true, hasChildren = true))
        assertFalse(SettingSelectionEngine.shouldEnterChild(wasSelected = false, hasChildren = true))
        assertFalse(SettingSelectionEngine.shouldEnterChild(wasSelected = true, hasChildren = false))
        assertFalse(SettingSelectionEngine.shouldEnterChild(wasSelected = false, hasChildren = false))
    }

    @Test
    fun `facing copy advances only on reselect while selected`() {
        // 未選択時の初回は留まります（座標→複写の直接遷移を防ぎます）。
        assertTrue(
            SettingSelectionEngine.decideFacingCopy(false, FacingKind.COORDINATES) is SettingSelectionEngine.Next.Stay,
        )
        assertTrue(
            SettingSelectionEngine.decideFacingCopy(false, null) is SettingSelectionEngine.Next.Stay,
        )
        // 選択状態での再クリックだけが進みます。開き直し直後の保存済み選択も含みます。
        assertTrue(
            SettingSelectionEngine.decideFacingCopy(true, FacingKind.COPY_ORIENTATION) is SettingSelectionEngine.Next.EnterChild,
        )
        // 別種別の選択表示が残る状態では進みません。
        assertTrue(
            SettingSelectionEngine.decideFacingCopy(true, FacingKind.COORDINATES) is SettingSelectionEngine.Next.Stay,
        )
    }

    @Test
    fun `duplicate visual tag with different elements does not diagnose`() {
        val nodes = mutableListOf<com.awabi2048.ccsystem.api.gesturegui.layout.GestureGuiNode>()
        val frame = contentFrame(panel)
        // 旧採番（visualTag+index）では重複した組み合わせも、要素ID由来では一意になります。
        listOf("lower-setting-choice:a" to 0, "lower-setting-choice:b" to 0).forEach { (elementId, index) ->
            DeclarativeSettings.choiceCardsSection(
                nodes,
                frame,
                cards = listOf(
                    SettingsChoiceCard(
                        elementId = elementId,
                        label = Component.text("label$index"),
                        background = blockData(),
                        outline = null,
                        enabled = true,
                        cx = -0.10,
                        cy = 0.08,
                        w = 0.66,
                        h = 0.10,
                        labelDy = -0.012,
                        labelSize = 0.0045,
                        labelWidth = 115,
                        hover = null,
                        replacesDescription = false,
                        visualTag = "setting-choice",
                        visualIndex = index,
                    ),
                ),
            )
        }
        val compiled = KantanLayoutBridge.compile(DeclarativeSettings.document(nodes, panel), onAction = {})
        assertTrue(compiled.diagnostics.isEmpty(), "diagnostics: ${compiled.diagnostics}")
    }
}
