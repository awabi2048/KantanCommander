package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material

class GraphLayoutEngineTest {
    @Test
    fun `commands always have one path cell between them`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val second = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val layout = GraphLayoutEngine.layout(graph)

        assertEquals(MapPoint(1, 1), layout.nodePoints[first.id])
        assertEquals(MapPoint(3, 1), layout.nodePoints[second.id])
        assertEquals(MapCellKind.PATH, layout.cells[MapPoint(2, 1)]?.kind)
    }

    @Test
    fun `layout is deterministic and keeps one cell margin`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val nested = GraphEditor.append(graph, CommandType.FOR_START, condition.id)
        GraphEditor.appendToForBody(graph, nested.id, CommandType.DISPLAY_TEXT)
        GraphEditor.appendMerge(graph, condition.id)

        val first = GraphLayoutEngine.layout(graph)
        val second = GraphLayoutEngine.layout(graph)

        assertEquals(first, second)
        assertTrue(first.cells.keys.all { it.x in 1 until first.width - 1 })
        assertTrue(first.cells.keys.all { it.y in 1 until first.height - 1 })
        assertEquals(graph.nodes.keys, first.nodePoints.keys)
    }

    @Test
    fun `generated nested branch and loop structures never cross`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val trueLoop = GraphEditor.append(graph, CommandType.FOR_START)
        val bodyCondition = GraphEditor.appendToForBody(graph, trueLoop.id, CommandType.CONDITION)
        GraphEditor.appendToForBody(graph, trueLoop.id, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, bodyCondition.id)
        GraphEditor.appendMerge(graph, bodyCondition.id)

        val falseCondition = GraphEditor.append(graph, CommandType.CONDITION, outer.id)
        GraphEditor.append(graph, CommandType.WAIT, outer.id)
        val falseLoop = GraphEditor.append(graph, CommandType.FOR_START, falseCondition.id)
        GraphEditor.appendToForBody(graph, falseLoop.id, CommandType.DISPLAY_TEXT)
        GraphEditor.appendMerge(graph, falseCondition.id)
        GraphEditor.appendMerge(graph, outer.id)

        val layout = GraphLayoutEngine.layout(graph)
        assertEquals(graph.nodes.keys, layout.nodePoints.keys)
        assertTrue(layout.nodePoints.values.all { it.x % 2 == 1 && it.y % 2 == 1 })
        assertTrue(layout.cells.values.any { it.kind == MapCellKind.LOOP_RETURN_PATH })
        assertTrue(layout.cells.values.any { it.kind == MapCellKind.BRANCH_PATH })
    }

    @Test
    fun `true continues straight and false bends downward before matching merge`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.WAIT)
        val falseNode = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val layout = GraphLayoutEngine.layout(graph)

        assertEquals(1, layout.nodePoints[condition.id]?.y)
        assertEquals(1, layout.nodePoints[trueNode.id]?.y)
        assertTrue(layout.nodePoints[falseNode.id]!!.y > 1)
        assertEquals(1, layout.nodePoints[merge.id]?.y)
    }

    @Test
    fun `open condition keeps true and false insertion targets distinct`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)

        val layout = GraphLayoutEngine.layout(graph)

        assertEquals(GraphEditor.Edge.TRUE, layout.cells[MapPoint(2, 1)]?.insertionTarget?.edge)
        assertEquals(GraphEditor.Edge.FALSE, layout.cells[MapPoint(2, 2)]?.insertionTarget?.edge)
    }

    @Test
    fun `nested true branch moves outer false branch below its occupied area`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val inner = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.WAIT, inner.id)
        GraphEditor.appendMerge(graph, inner.id)
        val outerFalse = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, outer.id)
        GraphEditor.appendMerge(graph, outer.id)

        val layout = GraphLayoutEngine.layout(graph)
        val innerFalseY = layout.nodePoints.values.maxOf(MapPoint::y)
        assertTrue(layout.nodePoints[outerFalse.id]!!.y >= innerFalseY)
    }

    @Test
    fun `viewport is a literal crop and navigation is one cell within margin bounds`() {
        val graph = CommandGraph.empty()
        repeat(6) { GraphEditor.append(graph, CommandType.WAIT) }
        val layout = GraphLayoutEngine.layout(graph)

        assertTrue(layout.canMove(MapPoint(0, 0), 1, 0, 9, 5))
        assertFalse(layout.canMove(MapPoint(0, 0), -1, 0, 9, 5))
        assertEquals(
            layout.cells[MapPoint(3, 1)]?.nodeId,
            layout.viewport(MapPoint(1, 0), 9, 5)[MapPoint(2, 1)]?.nodeId,
        )
    }

    @Test
    fun `for start body end and return path are rendered as one structure`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val body = GraphEditor.appendToForBody(graph, start.id, CommandType.WAIT)
        val end = start.pairedNodeId?.let(graph.nodes::get)!!
        val layout = GraphLayoutEngine.layout(graph)

        assertEquals(layout.nodePoints[start.id]?.y, layout.nodePoints[body.id]?.y)
        assertEquals(layout.nodePoints[start.id]?.y, layout.nodePoints[end.id]?.y)
        assertTrue(layout.cells.values.any { it.kind == MapCellKind.LOOP_RETURN_PATH })
    }

    @Test
    fun `for body paths remain insertion targets`() {
        val emptyGraph = CommandGraph.empty()
        val emptyStart = GraphEditor.append(emptyGraph, CommandType.FOR_START)
        val emptyLayout = GraphLayoutEngine.layout(emptyGraph)
        assertEquals(
            InsertionTarget(emptyStart.id, GraphEditor.Edge.FOR_BODY),
            emptyLayout.cells[MapPoint(2, 1)]?.insertionTarget,
        )

        val populatedGraph = CommandGraph.empty()
        val populatedStart = GraphEditor.append(populatedGraph, CommandType.FOR_START)
        val body = GraphEditor.appendToForBody(populatedGraph, populatedStart.id, CommandType.WAIT)
        val populatedLayout = GraphLayoutEngine.layout(populatedGraph)
        val endPoint = requireNotNull(populatedLayout.nodePoints[requireNotNull(populatedStart.pairedNodeId)])
        assertEquals(
            InsertionTarget(body.id, GraphEditor.Edge.NEXT),
            populatedLayout.cells[MapPoint(endPoint.x - 1, endPoint.y)]?.insertionTarget,
        )
    }

    @Test
    fun `merge side path inserts at the false branch tail`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val falseTail = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val layout = GraphLayoutEngine.layout(graph)
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val falseY = requireNotNull(layout.nodePoints[falseTail.id]).y

        val mergeSide = layout.cells[MapPoint(mergePoint.x - 1, falseY)]
        assertEquals(falseTail.id, mergeSide?.insertionTarget?.sourceId)
        assertEquals(GraphEditor.Edge.NEXT, mergeSide?.insertionTarget?.edge)
        assertEquals(condition.id, mergeSide?.insertionTarget?.mergeConditionId)
    }

    @Test
    fun `info diagram uses uniform squares and truncates tall maps`() {
        val graph = CommandGraph.empty()
        val conditions = mutableListOf<me.awabi2048.kantancommander.model.CommandNode>()
        repeat(5) {
            conditions += GraphEditor.append(graph, CommandType.CONDITION)
        }
        GraphEditor.append(graph, CommandType.WAIT)
        conditions.asReversed().forEach { condition ->
            GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
            GraphEditor.appendMerge(graph, condition.id)
        }
        val layout = GraphLayoutEngine.layout(graph)
        val diagram = PlainTextComponentSerializer.plainText().serialize(
            GraphDiagramRenderer.render(layout, MapPoint(0, 0))
        )

        assertTrue(diagram.contains("■"))
        assertFalse(diagram.any { it in "○+┌┐└┘├┤┬┴┼│─╔╗╚╝║═LP" })
        assertTrue(diagram.contains("⋮"))
    }

    @Test
    fun `map materials distinguish only add and loop return paths`() {
        assertEquals(Material.YELLOW_STAINED_GLASS_PANE, MapCellMaterialPolicy.material(MapCellKind.ADD))
        assertEquals(Material.LIGHT_BLUE_STAINED_GLASS_PANE, MapCellMaterialPolicy.material(MapCellKind.LOOP_RETURN_PATH))
        assertEquals(Material.WHITE_STAINED_GLASS_PANE, MapCellMaterialPolicy.material(MapCellKind.PATH))
        assertEquals(Material.WHITE_STAINED_GLASS_PANE, MapCellMaterialPolicy.material(MapCellKind.BRANCH_PATH))
        assertThrows<IllegalStateException> { MapCellMaterialPolicy.material(MapCellKind.NODE) }
    }

    @Test
    fun `overview keeps map boundaries and the complete viewport`() {
        val selected = OverviewAxis.select(size = 40, viewportStart = 17, viewportSize = 9, limit = 21)

        assertEquals(0, selected.first())
        assertEquals(39, selected.last())
        assertTrue((17..25).all(selected::contains))
        assertEquals(21, selected.size)
    }

    @Test
    fun `linear tail add is part of map bounds and reachable after four commands`() {
        val graph = CommandGraph.empty()
        repeat(4) {
            GraphEditor.append(graph, CommandType.WAIT)
        }

        val layout = GraphLayoutEngine.layout(graph)
        val add = layout.cells.values.single { it.kind == MapCellKind.ADD }

        assertEquals(MapPoint(9, 1), add.point)
        assertEquals(11, layout.width)
        assertTrue(layout.canMove(MapPoint(0, 0), 1, 0, 9, 3))
        assertTrue(layout.viewport(MapPoint(1, 0), 9, 3).values.any { it.kind == MapCellKind.ADD })
    }

    @Test
    fun `empty graph add is a bounded map element`() {
        val layout = GraphLayoutEngine.layout(CommandGraph.empty())

        assertEquals(MapCellKind.ADD, layout.cells[MapPoint(1, 1)]?.kind)
        assertEquals(3, layout.width)
        assertEquals(3, layout.height)
    }

    @Test
    fun `closed for adds after for end and info does not expose legacy LP markers`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)

        val layout = GraphLayoutEngine.layout(graph)
        val end = requireNotNull(start.pairedNodeId)
        val endPoint = requireNotNull(layout.nodePoints[end])
        val add = layout.cells.values.single { it.kind == MapCellKind.ADD }
        val diagram = PlainTextComponentSerializer.plainText().serialize(
            GraphDiagramRenderer.render(layout, MapPoint(0, 0))
        )

        assertEquals(MapPoint(endPoint.x + 2, endPoint.y), add.point)
        assertFalse(diagram.any { it in "○+LP" })
        assertTrue(diagram.count { it == '■' } >= 3)
    }
}
