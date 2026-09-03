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
        // テクスチャはボタンの種類（選択方式と値状態）で決まり、選択状態は内側枠で表現します。
        assertEquals(
            org.bukkit.Material.CYAN_TERRACOTTA,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
            ),
        )
        assertEquals(
            org.bukkit.Material.MAGENTA_TERRACOTTA,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.MULTIPLE,
                GestureSettingValueState.INITIAL,
            ),
        )
        // 旧シグネチャは後方互換のためテクスチャに影響しません。
        assertEquals(
            org.bukkit.Material.CYAN_TERRACOTTA,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
                selected = true,
            ),
        )
        assertEquals(
            org.bukkit.Material.CYAN_TERRACOTTA,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
                selected = false,
            ),
        )
        // タブのGlowは警告専用とし、選択状態は内側枠へ移します。
        assertEquals(null, GestureSettingVisualPolicy.tabGlowColor(selected = true, attention = false))
        assertEquals(org.bukkit.Color.RED.asARGB(), GestureSettingVisualPolicy.tabGlowColor(selected = false, attention = true))
        assertEquals(org.bukkit.Color.PURPLE.asARGB(), GestureSettingVisualPolicy.tabGlowColor(selected = true, attention = true))
        assertEquals(null, GestureSettingVisualPolicy.tabGlowColor(selected = false, attention = false))

        assertEquals(org.bukkit.Material.BLUE_CONCRETE, GestureSettingVisualPolicy.tabOutlineMaterial(selected = true))
        assertEquals(null, GestureSettingVisualPolicy.tabOutlineMaterial(selected = false))
        // タブ以外の設定ボタンは、完了状態にかかわらず選択中だけ白い内側枠で示します。
        assertEquals(org.bukkit.Material.WHITE_CONCRETE, GestureSettingVisualPolicy.nonTabOutlineMaterial(selected = true))
        assertEquals(null, GestureSettingVisualPolicy.nonTabOutlineMaterial(selected = false))
    }
}
