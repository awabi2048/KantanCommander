package me.awabi2048.kantancommander.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GestureSettingTreeTest {
    @Test
    fun `tree node exposes nested detail without mixing sibling branches`() {
        val distance = GestureSettingTreeNode("filter:distance", "距離")
        val nearby = GestureSettingTreeNode(
            id = "target:NEARBY_ENTITIES",
            label = "周囲のエンティティ",
            selected = true,
            children = listOf(distance),
        )
        val nearest = GestureSettingTreeNode("target:NEAREST_PLAYER", "最も近いプレイヤー")
        val root = listOf(nearby, nearest)

        assertTrue(nearby.hasChildren)
        assertEquals(distance, nearby.find("filter:distance"))
        assertEquals(null, nearest.find("filter:distance"))
        assertEquals(nearby, root.first { it.id == "target:NEARBY_ENTITIES" })
    }

    @Test
    fun `path replaces a selection at the same depth and preserves parents`() {
        val root = GestureSettingTreePath("target", CommandSettingRole.NODE_TARGET)
            .selectAtDepth(0, "target:NEAREST_PLAYER")
        val nested = root.enterChild("target:NEAREST_PLAYER")
            .selectAtDepth(1, "filter:minimumDistance")
        val sibling = nested.selectAtDepth(1, "filter:maximumDistance")

        assertEquals(listOf("target:NEAREST_PLAYER"), root.nodeIds)
        assertEquals(listOf("target:NEAREST_PLAYER", "filter:minimumDistance"), nested.nodeIds)
        assertEquals(listOf("target:NEAREST_PLAYER", "filter:maximumDistance"), sibling.nodeIds)
        assertEquals(root, nested.leaveChild())
    }
}
