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
        assertEquals(
            org.bukkit.Material.CYAN_CONCRETE,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.EXCLUSIVE,
                GestureSettingValueState.CONFIGURED,
                selected = true,
            ),
        )
        assertEquals(
            org.bukkit.Material.MAGENTA_TERRACOTTA,
            GestureSettingVisualPolicy.material(
                GestureSettingSelectionMode.MULTIPLE,
                GestureSettingValueState.INITIAL,
                selected = false,
            ),
        )
    }
}
