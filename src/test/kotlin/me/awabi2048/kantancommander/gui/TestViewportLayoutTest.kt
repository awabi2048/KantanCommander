package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TestViewportLayoutTest {
    private val nodeA = UUID.randomUUID()
    private val nodeB = UUID.randomUUID()

    private fun tailLayout(): GraphLayout {
        val cells = linkedMapOf(
            MapPoint(0, 0) to MapCell(MapPoint(0, 0), MapCellKind.NODE, nodeA),
            MapPoint(1, 0) to MapCell(
                MapPoint(1, 0),
                MapCellKind.PATH,
                insertionTarget = InsertionTarget(nodeA, GraphEditor.Edge.NEXT),
            ),
            MapPoint(2, 0) to MapCell(MapPoint(2, 0), MapCellKind.NODE, nodeB),
            MapPoint(3, 0) to MapCell(
                MapPoint(3, 0),
                MapCellKind.PATH,
                insertionTarget = InsertionTarget(nodeB, GraphEditor.Edge.NEXT),
            ),
            MapPoint(4, 0) to MapCell(
                MapPoint(4, 0),
                MapCellKind.ADD,
                insertionTarget = InsertionTarget(nodeB, GraphEditor.Edge.NEXT),
            ),
        )
        return GraphLayout(
            cells = cells,
            nodePoints = mapOf(nodeA to MapPoint(0, 0), nodeB to MapPoint(2, 0)),
            width = 6,
            height = 2,
        )
    }

    @Test
    fun `removes ADD cells and their extension paths`() {
        // 末端の延長経路とADDを取り除き、真のノード配置だけを残します。
        val pruned = tailLayout().withoutEditArtifacts(emptySet())
        assertFalse(pruned.cells.values.any { it.kind == MapCellKind.ADD })
        assertFalse(MapPoint(3, 0) in pruned.cells)
        assertTrue(MapPoint(0, 0) in pruned.cells)
        assertTrue(MapPoint(1, 0) in pruned.cells)
        assertTrue(MapPoint(2, 0) in pruned.cells)
        assertEquals(mapOf(nodeA to MapPoint(0, 0), nodeB to MapPoint(2, 0)), pruned.nodePoints)
        assertEquals(4, pruned.width)
    }

    @Test
    fun `keeps traversed extension paths for highlighting`() {
        // 実行済み経路の延長はハイライト表示のため残します。
        val pruned = tailLayout().withoutEditArtifacts(
            setOf(TestExecutionEdge(nodeB, GraphEditor.Edge.NEXT)),
        )
        assertFalse(pruned.cells.values.any { it.kind == MapCellKind.ADD })
        assertTrue(MapPoint(3, 0) in pruned.cells)
    }

    @Test
    fun `returns the same layout when no ADD cells exist`() {
        val clean = GraphLayout(
            cells = mapOf(
                MapPoint(0, 0) to MapCell(MapPoint(0, 0), MapCellKind.NODE, nodeA),
                MapPoint(1, 0) to MapCell(MapPoint(1, 0), MapCellKind.PATH),
                MapPoint(2, 0) to MapCell(MapPoint(2, 0), MapCellKind.NODE, nodeB),
            ),
            nodePoints = mapOf(nodeA to MapPoint(0, 0), nodeB to MapPoint(2, 0)),
            width = 4,
            height = 2,
        )
        assertSame(clean, clean.withoutEditArtifacts(emptySet()))
    }
}
