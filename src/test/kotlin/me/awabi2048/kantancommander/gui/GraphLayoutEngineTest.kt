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
    fun `merge node always has a path immediately before it`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val layout = GraphLayoutEngine.layout(graph)
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])

        assertTrue(
            layout.cells[MapPoint(mergePoint.x - 1, mergePoint.y)]?.kind in
                setOf(MapCellKind.PATH, MapCellKind.BRANCH_PATH),
        )
    }

    @Test
    fun `closed merge branches do not create add points`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val layout = GraphLayoutEngine.layout(graph)
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val addPoints = layout.cells.filterValues { it.kind == MapCellKind.ADD }.keys

        // 枝は合流で閉じているため、追加ポイントは合流後の末尾だけです。
        assertEquals(setOf(MapPoint(mergePoint.x + 2, mergePoint.y)), addPoints)
        assertEquals(MapCellKind.BRANCH_PATH, layout.cells[MapPoint(mergePoint.x - 1, mergePoint.y)]?.kind)
        assertEquals(MapCellKind.PATH, layout.cells[MapPoint(mergePoint.x + 1, mergePoint.y)]?.kind)
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
    fun `tail add points are connected to their preceding command`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val falseNode = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val layout = GraphLayoutEngine.layout(graph)

        val trueAdd = layout.cells.entries.single { it.value.kind == MapCellKind.ADD && it.key.y == 1 }.key
        val falseAdd = layout.cells.entries.single { it.value.kind == MapCellKind.ADD && it.key.y > 1 }.key

        assertTrue(layout.cells[MapPoint(trueAdd.x - 1, trueAdd.y)]?.kind in setOf(MapCellKind.PATH, MapCellKind.BRANCH_PATH))
        assertTrue(layout.cells[MapPoint(falseAdd.x - 1, falseAdd.y)]?.kind in setOf(MapCellKind.PATH, MapCellKind.BRANCH_PATH))
        assertEquals(falseNode.id, layout.cells[MapPoint(falseAdd.x - 1, falseAdd.y)]?.insertionTarget?.sourceId)
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
    fun `viewport projection exposes only visible cells and explicit edge continuations`() {
        val graph = CommandGraph.empty()
        repeat(6) { GraphEditor.append(graph, CommandType.WAIT) }
        val layout = GraphLayoutEngine.layout(graph)

        // グローバル x=3..7 だけを表示するため、左右の接続先は範囲外になります。
        val projection = layout.projection(MapPoint(3, 0), width = 5, height = 3)

        assertTrue(projection.cells.keys.all { it.x in 0 until 5 && it.y in 0 until 3 })
        assertTrue(projection.cells.values.none { it.kind == MapCellKind.NODE && it.nodeId == layout.cells[MapPoint(1, 1)]?.nodeId })
        assertTrue(
            projection.boundaryConnections.any {
                it.visible == MapPoint(0, 1) && it.outside == MapPoint(-1, 1) && it.outsideKind == MapCellKind.PATH
            },
        )
        assertTrue(
            projection.boundaryConnections.any {
                it.visible == MapPoint(4, 1) && it.outside == MapPoint(5, 1) && it.outsideKind == MapCellKind.PATH
            },
        )
    }

    @Test
    fun `projection treats an outside add point as a boundary continuation and insertion guard`() {
        val graph = CommandGraph.empty()
        repeat(4) { GraphEditor.append(graph, CommandType.WAIT) }
        val layout = GraphLayoutEngine.layout(graph)
        val add = layout.cells.entries.single { it.value.kind == MapCellKind.ADD }
        val origin = MapPoint(add.key.x - 1, add.key.y)
        val projection = layout.projection(origin, width = 1, height = 1)

        assertEquals(MapCellKind.PATH, projection.cells[MapPoint(0, 0)]?.kind)
        assertEquals(MapCellKind.ADD, projection.boundaryConnections.single { it.outside == MapPoint(1, 0) }.outsideKind)
        assertTrue(projection.hasNeighborOfKind(MapPoint(0, 0), MapCellKind.ADD))
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
    fun `L shaped merge keeps the full connector into the merge node`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        val layout = GraphLayoutEngine.layout(graph)
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val mergeY = GestureEditorLayout.cellCenterY(mergePoint.y)
        val leftCenter = GestureEditorLayout.cellCenterX(mergePoint.x - 1)
        val mergeCenter = GestureEditorLayout.cellCenterX(mergePoint.x)
        val connector = GesturePathRenderer.buildSegments(
            layout.cells,
            xCenter = GestureEditorLayout::cellCenterX,
            yCenter = GestureEditorLayout::cellCenterY,
            thickness = GestureEditorLayout.PATH_THICKNESS,
        ).filter {
            kotlin.math.abs(it.y - mergeY) <= 1.0e-9 &&
                it.h == GestureEditorLayout.PATH_THICKNESS &&
                it.x - it.w / 2.0 >= leftCenter - 1.0e-9 &&
                it.x + it.w / 2.0 <= mergeCenter + 1.0e-9
        }

        // L字の角から合流アイコン中心までの通常接続を3枚で埋め、端点を短縮しません。
        assertEquals(3, connector.size)
        assertEquals(
            mergeCenter - leftCenter,
            connector.sumOf { it.w },
            1.0e-9,
        )
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
        assertEquals(MapCellKind.PATH, layout.cells[MapPoint(add.point.x - 1, add.point.y)]?.kind)
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

    @Test
    fun `path between merge and its successor accepts insertion at the merge node`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        GraphEditor.append(graph, CommandType.WAIT)

        val layout = GraphLayoutEngine.layout(graph)
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val path = layout.cells[MapPoint(mergePoint.x + 1, mergePoint.y)]

        assertEquals(merge.id, path?.insertionTarget?.sourceId)
        assertEquals(GraphEditor.Edge.NEXT, path?.insertionTarget?.edge)
    }

    @Test
    fun `path between for end and its successor accepts insertion at the for end node`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        GraphEditor.appendToForBody(graph, start.id, CommandType.WAIT)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        GraphEditor.append(graph, CommandType.WAIT)

        val layout = GraphLayoutEngine.layout(graph)
        val endPoint = requireNotNull(layout.nodePoints[end.id])
        val path = layout.cells[MapPoint(endPoint.x + 1, endPoint.y)]

        assertEquals(end.id, path?.insertionTarget?.sourceId)
        assertEquals(GraphEditor.Edge.NEXT, path?.insertionTarget?.edge)
    }

    @Test
    fun `body ending with an open condition does not offer ambiguous tail insertion`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)

        val layout = GraphLayoutEngine.layout(graph)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val endPoint = requireNotNull(layout.nodePoints[end.id])
        val tailPath = layout.cells[MapPoint(endPoint.x - 1, endPoint.y)]

        // 挿入先が曖昧なbody末尾は装飾扱いとし、誤った位置への挿入を防ぐ。
        assertEquals(null, tailPath?.insertionTarget)
        // 一方、枝末端の黄色追加アイコンは維持されるため追加導線は失われない。
        assertTrue(layout.cells.values.any { it.kind == MapCellKind.ADD })
    }
}
