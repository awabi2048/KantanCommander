package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GestureSettingTreeTest {
    @Test
    fun `tree node exposes nested detail without mixing sibling branches`() {
        val distance = GestureSettingTreeNode("filter:distance", "距離")
        val nearby = GestureSettingTreeNode(
            id = "target:PLAYER",
            label = "プレイヤー",
            selected = true,
            children = listOf(distance),
        )
        val nearest = GestureSettingTreeNode("target:NON_PLAYER_ENTITY", "プレイヤー以外のエンティティ")
        val root = listOf(nearby, nearest)

        assertTrue(nearby.hasChildren)
        assertEquals(distance, nearby.find("filter:distance"))
        assertEquals(null, nearest.find("filter:distance"))
        assertEquals(nearby, root.first { it.id == "target:PLAYER" })
    }

    @Test
    fun `path replaces a selection at the same depth and preserves parents`() {
        val root = GestureSettingTreePath("target", CommandSettingRole.NODE_TARGET)
            .selectAtDepth(0, "target:PLAYER")
        val nested = root.enterChild("target:PLAYER")
            .selectAtDepth(1, "filter:distance")
        val sibling = nested.selectAtDepth(1, "filter:limit")

        assertEquals(listOf("target:PLAYER"), root.nodeIds)
        assertEquals(listOf("target:PLAYER", "filter:distance"), nested.nodeIds)
        assertEquals(listOf("target:PLAYER", "filter:limit"), sibling.nodeIds)
        assertEquals(root, nested.leaveChild())
    }

    @Test
    fun `selection stays on the current frame except for a second click on a branch`() {
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(wasSelected = false, hasChildren = false),
        )
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(wasSelected = false, hasChildren = true),
        )
        assertSame(
            GestureSettingSelectionAction.STAY_ON_FRAME,
            settingSelectionAction(wasSelected = true, hasChildren = false),
        )
        assertSame(
            GestureSettingSelectionAction.ENTER_CHILD,
            settingSelectionAction(wasSelected = true, hasChildren = true),
        )
    }

    @Test
    fun `visual policy separates selection cardinality and value state`() {
        // 設定項目の通常背景は選択方式・値状態によらず薄灰色で統一します。
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
            ),
        )
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.MULTIPLE,
                GestureSettingValueState.INITIAL,
            ),
        )
        // 旧シグネチャは後方互換のためテクスチャに影響しません。
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
                selected = true,
            ),
        )
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
                selected = false,
            ),
        )
        // タブの警告はGlowではなく、レガシー表記§c相当の赤文字で示します。
        assertEquals(null, GestureSettingVisualPolicy.tabTextColor(attention = false))
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.RED,
            GestureSettingVisualPolicy.tabTextColor(attention = true),
        )

        // タブの選択状態は縁取りではなく右へ幅15%伸ばし、選択中タブの背景はシアンの
        // テラコッタで示します。テキスト位置は維持します。
        assertEquals(0.15, GestureSettingVisualPolicy.SELECTED_TAB_EXTENSION_RATIO, 1.0e-9)
        assertEquals(0.5405, GestureSettingVisualPolicy.selectedTabWidth(0.47, selected = true), 1.0e-9)
        assertEquals(0.47, GestureSettingVisualPolicy.selectedTabWidth(0.47, selected = false), 1.0e-9)
        // 左端固定のため、拡張分の半分だけ中心が右へ移動します。
        assertEquals(-0.76225, GestureSettingVisualPolicy.selectedTabCenterX(-0.7975, 0.47, selected = true), 1.0e-9)
        assertEquals(
            org.bukkit.Material.CYAN_TERRACOTTA,
            GestureSettingVisualPolicy.tabMaterial(selected = true),
        )
        assertEquals(
            org.bukkit.Material.LIGHT_GRAY_CONCRETE,
            GestureSettingVisualPolicy.tabMaterial(selected = false),
        )
        assertEquals(-0.7975, GestureSettingVisualPolicy.selectedTabCenterX(-0.7975, 0.47, selected = false), 1.0e-9)
        // タブ以外の設定ボタンは、完了状態にかかわらず選択中だけ白い外周枠で示します。
        assertEquals(org.bukkit.Material.WHITE_CONCRETE, GestureSettingVisualPolicy.nonTabOutlineMaterial(selected = true))
        assertEquals(null, GestureSettingVisualPolicy.nonTabOutlineMaterial(selected = false))

        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.GOLD,
            GestureSettingVisualPolicy.settingChoiceTextColor(GestureSettingTreeNode("value", "値")),
        )
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.AQUA,
            GestureSettingVisualPolicy.settingChoiceTextColor(
                GestureSettingTreeNode("child", "詳細", children = listOf(GestureSettingTreeNode("leaf", "項目"))),
            ),
        )
        assertEquals(
            net.kyori.adventure.text.format.NamedTextColor.GRAY,
            GestureSettingVisualPolicy.settingChoiceTextColor(
                GestureSettingTreeNode("disabled", "無効", enabled = false),
            ),
        )
    }
}
