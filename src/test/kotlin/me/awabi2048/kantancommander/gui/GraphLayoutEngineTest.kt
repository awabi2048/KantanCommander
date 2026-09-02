package me.awabi2048.kantancommander.gui

import me.awabi2048.kantancommander.data.GraphEditor
import me.awabi2048.kantancommander.data.GraphValidator
import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `layout cell limit rejects expansion before unbounded allocation`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT)

        assertThrows<IllegalArgumentException> {
            GraphLayoutEngine.layout(graph, maxCells = 1)
        }
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
    fun `false branch leaves the condition from its lower side`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val falseNode = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        GraphEditor.appendMerge(graph, condition.id)

        val layout = GraphLayoutEngine.layout(graph)
        val conditionPoint = requireNotNull(layout.nodePoints[condition.id])
        val falsePoint = requireNotNull(layout.nodePoints[falseNode.id])

        assertTrue(falsePoint.y > conditionPoint.y)
        (conditionPoint.y + 1..falsePoint.y).forEach { y ->
            assertEquals(
                MapCellKind.BRANCH_PATH,
                layout.cells[MapPoint(conditionPoint.x, y)]?.kind,
            )
        }
        // 中心列の最下段は角、そこから右へ1セル進んでfalse枝先頭へ入ります。
        assertEquals(
            condition.id,
            layout.cells[MapPoint(conditionPoint.x + 1, falsePoint.y)]
                ?.insertionTarget
                ?.sourceId,
        )
        assertEquals(GraphEditor.Edge.FALSE, layout.cells[MapPoint(conditionPoint.x + 1, falsePoint.y)]?.insertionTarget?.edge)
    }

    @Test
    fun `unequal branches align the returning column with the longest branch merge`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val trueTail = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val falseTail = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        val layout = GraphLayoutEngine.layout(graph)
        val trueTailPoint = requireNotNull(layout.nodePoints[trueTail.id])
        val falseTailPoint = requireNotNull(layout.nodePoints[falseTail.id])
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])

        // 最長のTRUE枝末端から通常どおり2セル先にMERGEを置き、短いFALSE枝だけを
        // MERGEと同じx列まで延ばして、直下から上向きに接続します。
        assertEquals(trueTailPoint.x + 2, mergePoint.x)
        assertEquals(MapCellKind.BRANCH_PATH, layout.cells[MapPoint(mergePoint.x, falseTailPoint.y)]?.kind)
        assertEquals(MapCellKind.BRANCH_PATH, layout.cells[MapPoint(mergePoint.x, mergePoint.y + 1)]?.kind)
        // 長さ調整用の連続経路は、false末尾から合流直前までの全水平セルを
        // 同じ挿入判定領域にします。MERGE直下の縦線だけは接続専用です。
        (falseTailPoint.x + 1..mergePoint.x).forEach { x ->
            assertEquals(falseTail.id, layout.cells[MapPoint(x, falseTailPoint.y)]?.insertionTarget?.sourceId)
            assertEquals(GraphEditor.Edge.NEXT, layout.cells[MapPoint(x, falseTailPoint.y)]?.insertionTarget?.edge)
        }
    }

    @Test
    fun `merge node receives the returning branch immediately below it`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val layout = GraphLayoutEngine.layout(graph)
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])

        assertEquals(MapCellKind.BRANCH_PATH, layout.cells[MapPoint(mergePoint.x, mergePoint.y + 1)]?.kind)
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
        assertEquals(MapCellKind.BRANCH_PATH, layout.cells[MapPoint(mergePoint.x, mergePoint.y + 1)]?.kind)
        assertEquals(MapCellKind.PATH, layout.cells[MapPoint(mergePoint.x + 1, mergePoint.y)]?.kind)
    }

    @Test
    fun `paired condition exposes an add point for a null terminated branch`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        condition.falseNext = null

        val layout = GraphLayoutEngine.layout(graph)
        val conditionPoint = requireNotNull(layout.nodePoints[condition.id])
        val add = layout.cells[MapPoint(conditionPoint.x + 2, conditionPoint.y + 2)]

        assertEquals(MapCellKind.ADD, add?.kind)
        assertEquals(GraphEditor.Edge.FALSE, add?.insertionTarget?.edge)
        assertEquals(merge.id, condition.trueNext)
    }

    @Test
    fun `open condition keeps true and false insertion targets distinct`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)

        val layout = GraphLayoutEngine.layout(graph)

        assertEquals(GraphEditor.Edge.TRUE, layout.cells[MapPoint(2, 1)]?.insertionTarget?.edge)
        assertEquals(GraphEditor.Edge.FALSE, layout.cells[MapPoint(2, 3)]?.insertionTarget?.edge)
        // 開いた枝の縦幹は、枝末端の追加ボタンが同一の挿入先を提供するため
        // 接続専用になります（横経路の追加ボタン直前抑制と同じ規則）。
        assertEquals(null, layout.cells[MapPoint(1, 2)]?.insertionTarget)
    }

    @Test
    fun `nested condition exposes head insertion on the enclosing stem and none on its own empty stem`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val merge = GraphEditor.appendMerge(graph, outer.id)
        // 外側FALSE枝の先頭へ、まだ合流を持たない内側条件を挿入し、
        // 内側のFALSE枝を外側の合流で閉じる（追加ボタンのない閉じた枝）。
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)
        graph.nodes.getValue(inner.id).falseNext = merge.id

        val layout = GraphLayoutEngine.layout(graph)
        val outerPoint = requireNotNull(layout.nodePoints[outer.id])
        val innerPoint = requireNotNull(layout.nodePoints[inner.id])

        // 外側の縦幹は、非空のFALSE枝の先頭への挿入を受け付けます。
        assertEquals(
            InsertionTarget(outer.id, GraphEditor.Edge.FALSE, outer.id),
            layout.cells[MapPoint(outerPoint.x, outerPoint.y + 1)]?.insertionTarget,
        )
        // 内側条件のFALSE枝は空のため、内側の縦幹は接続専用（追加ボタンが担う）。
        assertEquals(
            null,
            layout.cells[MapPoint(innerPoint.x, innerPoint.y + 1)]?.insertionTarget,
        )
    }

    @Test
    fun `nested open branch add point carries the enclosing merge continuation`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)

        val layout = GraphLayoutEngine.layout(graph)
        val add = layout.cells.values.first {
            it.kind == MapCellKind.ADD &&
                it.insertionTarget?.let { target ->
                    target.sourceId == inner.id && target.edge == GraphEditor.Edge.FALSE
                } == true
        }
        val target = requireNotNull(add.insertionTarget)

        // 左下の追加位置は、内側条件の枝で正常終了する通常ノードも受け付けます。
        // 継続先は、明示的にMERGEを選んだ場合だけ再合流先として使用します。
        assertEquals(inner.id, target.mergeConditionId)
        assertEquals(outerMerge.id, target.continuationId)
        val preview = requireNotNull(GraphLayoutEngine.previewInsertion(graph, target))
        assertTrue(GraphValidator.validate(preview.graph).isEmpty())
    }

    @Test
    fun `open condition with a non-empty false branch exposes the branch head insertion on its stem`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val merge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)
        val falseNode = GraphEditor.append(graph, CommandType.WAIT, inner.id)

        val layout = GraphLayoutEngine.layout(graph)
        val innerPoint = requireNotNull(layout.nodePoints[inner.id])
        assertNotNull(layout.nodePoints[falseNode.id])

        // 非空のFALSE枝を持つ縦幹も、親合流へ戻る継続先を保持します。
        assertEquals(
            InsertionTarget(inner.id, GraphEditor.Edge.FALSE, inner.id, continuationId = merge.id),
            layout.cells[MapPoint(innerPoint.x, innerPoint.y + 1)]?.insertionTarget,
        )
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
        val bodyPoint = requireNotNull(layout.nodePoints[body.id])
        val returnY = layout.cells.keys.filter {
            it.x == bodyPoint.x && it.y > bodyPoint.y && layout.cells[it]?.kind == MapCellKind.LOOP_RETURN_PATH
        }
            .minOf(MapPoint::y)
        assertEquals(
            setOf(
                MapPoint(bodyPoint.x - 1, returnY),
                MapPoint(bodyPoint.x + 1, returnY),
            ),
            layout.loopReturnArrowPoints,
        )
        assertTrue(layout.loopReturnArrowPoints.none { it.x == bodyPoint.x })
        assertEquals(
            layout.loopReturnArrowPoints,
            layout.projection(MapPoint(0, 0), layout.width, layout.height).loopReturnArrowPoints,
        )
    }

    @Test
    fun `loop return arrows use horizontal middle path slots inside nested branches`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val bodyCondition = GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.WAIT, bodyCondition.id)
        val falseNode = GraphEditor.insert(graph, bodyCondition.id, GraphEditor.Edge.FALSE, CommandType.DISPLAY_TEXT)
        val merge = GraphEditor.appendMerge(graph, bodyCondition.id)

        val layout = GraphLayoutEngine.layout(graph)
        val startPoint = requireNotNull(layout.nodePoints[start.id])
        val endPoint = requireNotNull(layout.nodePoints[requireNotNull(start.pairedNodeId)])
        val returnY = layout.cells.keys.filter {
            it.y > startPoint.y && layout.cells[it]?.kind == MapCellKind.LOOP_RETURN_PATH
        }
            .minOf(MapPoint::y)
        val expectedX = layout.cells.asSequence()
            .filter { (point, cell) ->
                point.x in (startPoint.x + 1) until endPoint.x &&
                    point.y in startPoint.y until returnY &&
                    cell.kind in setOf(MapCellKind.PATH, MapCellKind.BRANCH_PATH) &&
                    layout.cells[MapPoint(point.x - 1, point.y)]?.kind in CONNECTABLE_CELL_KINDS &&
                    layout.cells[MapPoint(point.x + 1, point.y)]?.kind in CONNECTABLE_CELL_KINDS
            }
            .map { (point, _) -> point.x }
            .toSet()
        assertTrue(expectedX.isNotEmpty())
        assertEquals(expectedX, layout.loopReturnArrowPoints.map(MapPoint::x).toSet())
        assertTrue(layout.loopReturnArrowPoints.all { layout.cells[it]?.kind == MapCellKind.LOOP_RETURN_PATH })
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
    fun `merge return path exposes every horizontal cell at the false branch tail`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val falseTail = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val layout = GraphLayoutEngine.layout(graph)
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val falseTailPoint = requireNotNull(layout.nodePoints[falseTail.id])
        val falseY = requireNotNull(layout.nodePoints[falseTail.id]).y

        (falseTailPoint.x + 1..mergePoint.x).forEach { x ->
            val path = layout.cells[MapPoint(x, falseY)]
            assertEquals(falseTail.id, path?.insertionTarget?.sourceId)
            assertEquals(GraphEditor.Edge.NEXT, path?.insertionTarget?.edge)
            assertEquals(condition.id, path?.insertionTarget?.mergeConditionId)
        }
    }

    @Test
    fun `continuous branch path exposes every cell after each source`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueFirst = GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val falseFirst = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val falseTail = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        val layout = GraphLayoutEngine.layout(graph)
        val falseTailPoint = requireNotNull(layout.nodePoints[falseTail.id])
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val targetCells = (falseTailPoint.x + 1..mergePoint.x).map { x ->
            MapPoint(x, falseTailPoint.y)
        }
        targetCells.forEach { point ->
            val target = layout.cells[point]?.insertionTarget
            assertEquals(falseTail.id, target?.sourceId)
            assertEquals(GraphEditor.Edge.NEXT, target?.edge)
            assertEquals(condition.id, target?.mergeConditionId)
        }

        val target = requireNotNull(layout.cells[targetCells.first()]?.insertionTarget)
        val candidate = graph.deepCopy()
        GraphEditor.insert(candidate, target.sourceId, target.edge, CommandType.CONDITION)
        assertDoesNotThrow { GraphLayoutEngine.layout(candidate) }
    }

    @Test
    fun `condition stem exposes the branch head insertion and merge stem exposes the tail insertion`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val falseNode = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        val layout = GraphLayoutEngine.layout(graph)
        val conditionPoint = requireNotNull(layout.nodePoints[condition.id])
        val falsePoint = requireNotNull(layout.nodePoints[falseNode.id])
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])

        // 非空のFALSE枝を持つ条件の縦幹は、枝の先頭への挿入を受け付けます。
        assertEquals(
            InsertionTarget(condition.id, GraphEditor.Edge.FALSE, condition.id),
            layout.cells[MapPoint(conditionPoint.x, conditionPoint.y + 1)]?.insertionTarget,
        )
        // 合流側の縦幹は、枝末端への挿入（tail, NEXT）を受け付けます。
        assertEquals(
            InsertionTarget(falseNode.id, GraphEditor.Edge.NEXT, condition.id),
            layout.cells[MapPoint(mergePoint.x, falsePoint.y - 1)]?.insertionTarget,
        )
    }

    @Test
    fun `every vertical condition segment near the merge uses the tail insertion target`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val falseFirst = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val falseTail = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        val layout = GraphLayoutEngine.layout(graph)
        val conditionPoint = requireNotNull(layout.nodePoints[condition.id])
        val falsePoint = requireNotNull(layout.nodePoints[falseFirst.id])
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val expected = InsertionTarget(falseTail.id, GraphEditor.Edge.NEXT, condition.id)

        assertTrue(falsePoint.y > conditionPoint.y + 1)
        // 合流側の縦幹は、枝末端への挿入を受け付けます。
        (conditionPoint.y + 1..falsePoint.y).forEach { y ->
            assertEquals(expected, layout.cells[MapPoint(mergePoint.x, y)]?.insertionTarget)
        }
        // 条件直下の縦幹は、非空のFALSE枝の先頭への挿入を受け付けます。
        (conditionPoint.y + 1..falsePoint.y).forEach { y ->
            assertEquals(
                InsertionTarget(condition.id, GraphEditor.Edge.FALSE, condition.id),
                layout.cells[MapPoint(conditionPoint.x, y)]?.insertionTarget,
            )
        }
    }

    @Test
    fun `insertion preview relayouts the graph before highlighting the new node`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val second = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val layout = GraphLayoutEngine.layout(graph)
        val pathPoint = MapPoint(2, 1)
        val target = requireNotNull(layout.cells[pathPoint]?.insertionTarget)
        val preview = requireNotNull(GraphLayoutEngine.previewInsertion(graph, target))

        assertEquals(MapPoint(3, 1), preview.layout.nodePoints[preview.insertedNodeId])
        assertEquals(MapPoint(5, 1), preview.layout.nodePoints[second.id])
        assertEquals(first.id, target.sourceId)
        assertEquals(2, graph.nodes.size)
        assertEquals(3, preview.graph.nodes.size)
    }

    @Test
    fun `condition inserted at a branch head does not duplicate the parent merge`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val falseFirst = GraphEditor.append(graph, CommandType.WAIT, outer.id)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, outer.id)
        GraphEditor.appendMerge(graph, outer.id)

        val layout = GraphLayoutEngine.layout(graph)
        val outerPoint = requireNotNull(layout.nodePoints[outer.id])
        val falseY = requireNotNull(layout.nodePoints[falseFirst.id]).y
        val branchHead = requireNotNull(
            requireNotNull(layout.cells[MapPoint(outerPoint.x + 1, falseY)]).insertionTarget,
        )
        val candidate = graph.deepCopy()

        GraphEditor.insert(candidate, branchHead.sourceId, branchHead.edge, CommandType.CONDITION)
        assertDoesNotThrow { GraphLayoutEngine.layout(candidate) }
    }

    @Test
    fun `nested open condition keeps the upper chain connected to the parent merge`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val trueHead = GraphEditor.append(graph, CommandType.WAIT)
        val merge = GraphEditor.appendMerge(graph, outer.id)

        // TRUE枝の末尾直後へ、まだ合流していない条件分岐を挿入します。
        // 内側条件のFALSE枝が下へ広がっても、外側合流までの上段経路は連続で
        // なければなりません（以前は下枝の幅を上段終端と誤認して1セル欠落）。
        GraphEditor.insert(graph, trueHead.id, GraphEditor.Edge.NEXT, CommandType.CONDITION)

        val layout = GraphLayoutEngine.layout(graph)
        val inner = layout.nodePoints.entries
            .first { (id, _) -> id != outer.id && id != merge.id && graph.nodes[id]?.type == CommandType.CONDITION }
            .value
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        ((inner.x + 1) until mergePoint.x).forEach { x ->
            assertTrue(layout.cells[MapPoint(x, inner.y)]?.kind in PATH_CELL_KINDS)
        }
    }

    @Test
    fun `nested open condition keeps insertion target on the upper merge compensation path`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val trueHead = GraphEditor.append(graph, CommandType.WAIT)
        val merge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, trueHead.id, GraphEditor.Edge.NEXT, CommandType.CONDITION)

        val layout = GraphLayoutEngine.layout(graph)
        val innerPoint = requireNotNull(layout.nodePoints[inner.id])
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val expected = InsertionTarget(
            inner.id,
            GraphEditor.Edge.TRUE,
            inner.id,
            continuationId = merge.id,
        )

        // 内側条件のTRUE枝から外側MERGEまで、FALSE枝の幅を埋める上段経路にも
        // 同じ挿入先を付与します。途中セルだけ判定が欠けると、クリック位置で
        // 挿入可否が変わり、GestureGUIの経路表示と操作対象が分離します。
        ((innerPoint.x + 1) until mergePoint.x).forEach { x ->
            assertEquals(expected, layout.cells[MapPoint(x, innerPoint.y)]?.insertionTarget)
        }
    }

    @Test
    fun `nested false add point reserves a separate parent merge column`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val trueHead = GraphEditor.append(graph, CommandType.WAIT)
        val merge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, trueHead.id, GraphEditor.Edge.NEXT, CommandType.CONDITION)
        GraphEditor.insert(graph, inner.id, GraphEditor.Edge.FALSE, CommandType.WAIT)

        val layout = GraphLayoutEngine.layout(graph)
        val innerPoint = requireNotNull(layout.nodePoints[inner.id])
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val innerAdd = layout.cells.entries
            .first { (point, cell) -> point.y > innerPoint.y && cell.kind == MapCellKind.ADD }
            .key

        // 追加ポイントが存在する列へ親合流の縦線を置かず、1ノード分右へ
        // 退避します。上段の内側条件→親合流も全セル連続でなければなりません。
        assertEquals(innerAdd.x + 2, mergePoint.x)
        ((innerPoint.x + 1) until mergePoint.x).forEach { x ->
            assertTrue(layout.cells[MapPoint(x, innerPoint.y)]?.kind in PATH_CELL_KINDS)
        }
        assertTrue(layout.cells[MapPoint(mergePoint.x, innerAdd.y)]?.kind in PATH_CELL_KINDS)
    }

    @Test
    fun `L shaped merge keeps the full vertical connector into the merge node`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        val layout = GraphLayoutEngine.layout(graph)
        val mergePoint = requireNotNull(layout.nodePoints[merge.id])
        val mergeX = GestureEditorLayout.cellCenterX(mergePoint.x)
        val mergeCenter = GestureEditorLayout.cellCenterY(mergePoint.y)
        val belowCenter = GestureEditorLayout.cellCenterY(mergePoint.y + 1)
        val connector = GesturePathRenderer.buildSegments(
            layout.cells,
            xCenter = GestureEditorLayout::cellCenterX,
            yCenter = GestureEditorLayout::cellCenterY,
            thickness = GestureEditorLayout.PATH_THICKNESS,
        ).filter {
            kotlin.math.abs(it.x - mergeX) <= 1.0e-9 &&
                it.w == GestureEditorLayout.PATH_THICKNESS &&
                it.y + it.h / 2.0 > minOf(mergeCenter, belowCenter) &&
                it.y - it.h / 2.0 < maxOf(mergeCenter, belowCenter)
        }

        // 縦線全体の3分割位置に依存せず、直下セルからMERGE中心までの被覆を検証します。
        assertEquals(
            kotlin.math.abs(belowCenter - mergeCenter),
            connector.sumOf {
                minOf(it.y + it.h / 2.0, maxOf(mergeCenter, belowCenter)) -
                    maxOf(it.y - it.h / 2.0, minOf(mergeCenter, belowCenter))
            },
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

    @Test
    fun `for body compensation path keeps insertion target from an open condition`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val body = GraphEditor.appendToForBody(graph, start.id, CommandType.WAIT)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val inner = GraphEditor.insert(graph, body.id, GraphEditor.Edge.NEXT, CommandType.CONDITION)

        val layout = GraphLayoutEngine.layout(graph)
        val innerPoint = requireNotNull(layout.nodePoints[inner.id])
        val endPoint = requireNotNull(layout.nodePoints[end.id])
        val expected = InsertionTarget(
            inner.id,
            GraphEditor.Edge.TRUE,
            inner.id,
            continuationId = end.id,
        )

        // 内側条件のFALSE枝で広がった幅をFOR_ENDまで埋める主行にも、TRUE枝の
        // 挿入先を引き継ぎます。空のTRUE枝だけを対象にせず、補償経路全体を
        // 同じ操作領域にすることが目的です。
        ((innerPoint.x + 1) until endPoint.x).forEach { x ->
            assertEquals(expected, layout.cells[MapPoint(x, innerPoint.y)]?.insertionTarget)
        }
    }
}
