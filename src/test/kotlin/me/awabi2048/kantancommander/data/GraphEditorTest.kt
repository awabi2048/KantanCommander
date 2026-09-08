package me.awabi2048.kantancommander.data

import me.awabi2048.kantancommander.model.CommandGraph
import me.awabi2048.kantancommander.model.CommandNode
import me.awabi2048.kantancommander.model.CommandType
import me.awabi2048.kantancommander.model.FacingKind
import me.awabi2048.kantancommander.model.FacingSpec
import me.awabi2048.kantancommander.model.PositionKind
import me.awabi2048.kantancommander.model.PositionSpec
import me.awabi2048.kantancommander.model.SearchOriginSpec
import me.awabi2048.kantancommander.model.TargetKind
import me.awabi2048.kantancommander.model.TargetSpec
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphEditorTest {
    @Test
    fun `duplicate copies every command setting and inserts after the source`() {
        val graph = CommandGraph.empty()
        val source = configuredNode(CommandType.WAIT)
        val successor = CommandType.GIVE_ITEM.newNode()
        graph.entryNodeId = source.id
        source.next = successor.id
        graph.nodes[source.id] = source
        graph.nodes[successor.id] = successor

        val clone = requireNotNull(GraphEditor.duplicate(graph, source.id))

        assertNotEquals(source.id, clone.id)
        assertEquals(source.type, clone.type)
        assertEquals(source.params, clone.params)
        assertNotSame(source.params, clone.params)
        assertEquals(source.configuredFields, clone.configuredFields)
        assertNotSame(source.configuredFields, clone.configuredFields)
        assertEquals(source.targetSpec, clone.targetSpec)
        assertNotSame(source.targetSpec, clone.targetSpec)
        assertEquals(source.secondaryTargetSpec, clone.secondaryTargetSpec)
        assertNotSame(source.secondaryTargetSpec, clone.secondaryTargetSpec)
        assertEquals(source.destinationSpec, clone.destinationSpec)
        assertNotSame(source.destinationSpec, clone.destinationSpec)
        assertEquals(source.destinationTargetSpec, clone.destinationTargetSpec)
        assertNotSame(source.destinationTargetSpec, clone.destinationTargetSpec)
        assertEquals(source.destinationFacingSpec, clone.destinationFacingSpec)
        assertNotSame(source.destinationFacingSpec, clone.destinationFacingSpec)
        assertEquals(source.conditionPositionSpec, clone.conditionPositionSpec)
        assertNotSame(source.conditionPositionSpec, clone.conditionPositionSpec)
        assertEquals(source.blockPositionSpec, clone.blockPositionSpec)
        assertNotSame(source.blockPositionSpec, clone.blockPositionSpec)
        assertEquals(source.blockFromSpec, clone.blockFromSpec)
        assertNotSame(source.blockFromSpec, clone.blockFromSpec)
        assertEquals(source.blockToSpec, clone.blockToSpec)
        assertNotSame(source.blockToSpec, clone.blockToSpec)
        assertEquals(source.soundPositionSpec, clone.soundPositionSpec)
        assertNotSame(source.soundPositionSpec, clone.soundPositionSpec)
        assertEquals(source.summonPositionSpec, clone.summonPositionSpec)
        assertNotSame(source.summonPositionSpec, clone.summonPositionSpec)
        assertEquals(source.temporaryEntityTargetSpec, clone.temporaryEntityTargetSpec)
        assertNotSame(source.temporaryEntityTargetSpec, clone.temporaryEntityTargetSpec)
        assertEquals(source.temporaryLocationPositionSpec, clone.temporaryLocationPositionSpec)
        assertNotSame(source.temporaryLocationPositionSpec, clone.temporaryLocationPositionSpec)
        assertEquals(source.temporaryLocationFacingSpec, clone.temporaryLocationFacingSpec)
        assertNotSame(source.temporaryLocationFacingSpec, clone.temporaryLocationFacingSpec)
        assertEquals(source.itemTempRef, clone.itemTempRef)
        assertEquals(source.blockTempRef, clone.blockTempRef)
        assertEquals(source.soundTempRef, clone.soundTempRef)
        assertEquals(source.effectTempRef, clone.effectTempRef)
        assertEquals(source.snapshot, clone.snapshot)
        assertNotSame(source.snapshot, clone.snapshot)
        clone.params["custom"] = "changed"
        assertEquals("value", source.params["custom"])
        val snapshotNodeId = requireNotNull(requireNotNull(source.snapshot).entryNodeId)
        requireNotNull(clone.snapshot).nodes.getValue(snapshotNodeId).params["seconds"] = "5"
        assertEquals("1", requireNotNull(source.snapshot).nodes.getValue(snapshotNodeId).params["seconds"])
        assertEquals(successor.id, clone.next)
        assertEquals(clone.id, source.next)
        assertNull(clone.trueNext)
        assertNull(clone.falseNext)
        assertNull(clone.pairedNodeId)
        assertTrue(clone.id in graph.nodes)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `duplicate keeps a terminal successor position inside a branch`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val source = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val successor = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        val clone = requireNotNull(GraphEditor.duplicate(graph, source.id))

        assertEquals(clone.id, source.next)
        assertEquals(successor.id, clone.next)
        assertEquals(merge.id, successor.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `duplicate rejects structural nodes and the node count limit`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val forStart = GraphEditor.append(graph, CommandType.FOR_START)
        val forEnd = requireNotNull(forStart.pairedNodeId).let(graph.nodes::get)!!
        val structuralNodes = listOf(condition, merge, forStart, forEnd)
        val originalIds = graph.nodes.keys.toSet()

        structuralNodes.forEach { node ->
            assertFalse(GraphEditor.isDuplicable(node))
            assertFalse(GraphEditor.canDuplicate(graph, node.id))
            assertNull(GraphEditor.duplicate(graph, node.id))
        }

        val ordinary = GraphEditor.append(graph, CommandType.WAIT)
        assertFalse(GraphEditor.canDuplicate(graph, ordinary.id, graph.nodes.size))
        assertNull(GraphEditor.duplicate(graph, ordinary.id, graph.nodes.size))
        assertTrue(GraphEditor.canDuplicate(graph, ordinary.id, graph.nodes.size + 1))
        assertEquals(originalIds + ordinary.id, graph.nodes.keys)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `adjacent reorder swaps execution order without changing node identity`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val second = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val third = GraphEditor.append(graph, CommandType.GIVE_ITEM)

        assertTrue(GraphEditor.swapAdjacent(graph, second.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(second.id, graph.entryNodeId)
        assertEquals(first.id, second.next)
        assertEquals(third.id, first.next)

        assertTrue(GraphEditor.swapAdjacent(graph, second.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(first.id, graph.entryNodeId)
        assertEquals(second.id, first.next)
        assertEquals(third.id, second.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `swap availability matches structural and branch boundaries`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val second = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.WAIT, condition.id)
        GraphEditor.appendMerge(graph, condition.id)

        assertFalse(GraphEditor.canSwapAdjacent(graph, first.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphEditor.canSwapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.canSwapAdjacent(graph, second.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.canSwapAdjacent(graph, condition.id, GraphEditor.ReorderDirection.RIGHT))
    }

    @Test
    fun `adjacent reorder works within a branch but does not exchange branch meaning`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.WAIT)
        val falseFirst = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val falseSecond = GraphEditor.append(graph, CommandType.GIVE_ITEM, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertTrue(GraphEditor.swapAdjacent(graph, falseSecond.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(falseSecond.id, condition.falseNext)
        assertEquals(falseFirst.id, falseSecond.next)
        assertEquals(merge.id, falseFirst.next)
        assertEquals(trueNode.id, condition.trueNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `structural nodes and branch boundary cannot be reordered as simple nodes`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val branchNode = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        GraphEditor.appendMerge(graph, condition.id)

        assertEquals(false, GraphEditor.swapAdjacent(graph, condition.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(false, GraphEditor.swapAdjacent(graph, branchNode.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `false branch keeps insertion order`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueCommand = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val first = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val second = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertEquals(first.id, condition.falseNext)
        assertEquals(second.id, first.next)
        assertEquals(merge.id, second.next)
        assertEquals(merge.id, trueCommand.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `nested condition is appended to selected false branch`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val inner = GraphEditor.append(graph, CommandType.CONDITION, outer.id)
        GraphEditor.append(graph, CommandType.WAIT, inner.id)
        GraphEditor.appendMerge(graph, inner.id)
        val outerCommand = GraphEditor.append(graph, CommandType.GIVE_ITEM, outer.id)
        GraphEditor.appendMerge(graph, outer.id)

        assertEquals(inner.id, outer.falseNext)
        assertEquals(outerCommand.id, inner.pairedNodeId?.let(graph.nodes::get)?.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `for start creates a paired end node`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val end = start.pairedNodeId?.let(graph.nodes::get)

        assertEquals(CommandType.FOR_END, end?.type)
        assertEquals(end?.id, start.trueNext)
        assertEquals(start.id, end?.pairedNodeId)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `condition deletion is refused while false branch has an execution node`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        GraphEditor.appendMerge(graph, condition.id)

        assertEquals(false, GraphEditor.delete(graph, condition.id))
        assertTrue(condition.id in graph.nodes)
    }

    @Test
    fun `empty condition deletion also removes matching merge and promotes true path`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.WAIT)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)

        assertTrue(GraphEditor.delete(graph, condition.id))
        assertEquals(trueNode.id, graph.entryNodeId)
        assertEquals(after.id, trueNode.next)
        assertTrue(merge.id !in graph.nodes)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `for pair deletion is allowed only while body is empty`() {
        val empty = CommandGraph.empty()
        val emptyStart = GraphEditor.append(empty, CommandType.FOR_START)
        assertTrue(GraphEditor.delete(empty, emptyStart.id))

        val occupied = CommandGraph.empty()
        val occupiedStart = GraphEditor.append(occupied, CommandType.FOR_START)
        GraphEditor.appendToForBody(occupied, occupiedStart.id, CommandType.WAIT)
        assertEquals(false, GraphEditor.delete(occupied, occupiedStart.id))
    }

    @Test
    fun `outer condition cannot merge before nested condition`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.insert(graph, outer.id, GraphEditor.Edge.TRUE, CommandType.CONDITION)

        assertEquals(false, GraphEditor.canAppendMerge(graph, outer.id))
        assertThrows(IllegalArgumentException::class.java) {
            GraphEditor.appendMerge(graph, outer.id)
        }
    }

    @Test
    fun `nested open condition merges into its enclosing continuation`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)

        // 内側条件のTRUE枝は、挿入時点では親MERGEへ直結しています。ここをそのまま
        // 残すと親MERGEへの入力が3本になるため、appendMergeは直結を内側MERGEへ
        // 付け替え、内側MERGEのNEXTだけを親MERGEへ接続します。
        val innerMerge = GraphEditor.appendMerge(graph, inner.id, continuationId = outerMerge.id)

        assertEquals(innerMerge.id, inner.trueNext)
        assertEquals(innerMerge.id, inner.falseNext)
        assertEquals(outerMerge.id, innerMerge.next)
        assertEquals(outerMerge.id, outer.trueNext)
        assertEquals(inner.id, outer.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `nested merge availability stops at the enclosing continuation`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        // 親合流の後ろに別の未合流条件があっても、内側条件の合流判定へ
        // それを混ぜると、無関係な後続経路のために操作ができなくなります。
        GraphEditor.append(graph, CommandType.CONDITION)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)

        assertTrue(GraphEditor.canAppendMerge(graph, inner.id, outerMerge.id))
        GraphEditor.appendMerge(graph, inner.id, outerMerge.id)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `normal insertion into a nested open branch keeps the branch as an early exit`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)

        val inserted = GraphEditor.insert(
            graph,
            inner.id,
            GraphEditor.Edge.FALSE,
            CommandType.WAIT,
            continuationId = outerMerge.id,
        )

        assertEquals(null, inner.pairedNodeId)
        assertEquals(outerMerge.id, inner.trueNext)
        assertEquals(inserted.id, inner.falseNext)
        assertEquals(null, inserted.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `normal insertion into an open condition in a for body remains a terminal`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val condition = GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!

        val inserted = GraphEditor.insert(
            graph,
            condition.id,
            GraphEditor.Edge.FALSE,
            CommandType.WAIT,
            continuationId = end.id,
        )

        assertEquals(end.id, condition.trueNext)
        assertEquals(null, condition.next)
        assertEquals(inserted.id, condition.falseNext)
        assertEquals(null, inserted.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `merge without a continuation argument infers the enclosing for end`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val condition = GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)
        val falseNode = GraphEditor.insert(graph, condition.id, GraphEditor.Edge.FALSE, CommandType.WAIT)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!

        // 旧呼び出し元が境界IDを渡さなくても、条件の所属FOR_ENDを直接参照から
        // 復元し、MERGEをFOR本体の末尾へ接続できることを確認します。
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertEquals(merge.id, condition.trueNext)
        assertEquals(falseNode.id, condition.falseNext)
        assertEquals(merge.id, falseNode.next)
        assertEquals(end.id, merge.next)
        assertEquals(null, end.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `nested terminal branch never inherits the enclosing for end`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val outer = GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val inner = GraphEditor.insert(
            graph,
            outer.id,
            GraphEditor.Edge.FALSE,
            CommandType.CONDITION,
            continuationId = end.id,
        )
        val first = GraphEditor.insert(
            graph,
            inner.id,
            GraphEditor.Edge.FALSE,
            CommandType.WAIT,
            continuationId = end.id,
        )
        val second = GraphEditor.insert(
            graph,
            first.id,
            GraphEditor.Edge.NEXT,
            CommandType.DISPLAY_TEXT,
            continuationId = end.id,
        )

        // 外側条件のTRUE枝だけがFOR_ENDへ戻り、内側条件とそのFALSE枝は
        // それぞれの深さで独立した終端になります。
        assertEquals(end.id, outer.trueNext)
        assertEquals(inner.id, outer.falseNext)
        assertEquals(null, inner.trueNext)
        assertEquals(first.id, inner.falseNext)
        assertEquals(second.id, first.next)
        assertEquals(null, second.next)
        assertEquals(1, graph.nodes.values.count { node -> node.next == end.id } +
            graph.nodes.values.count { node -> node.trueNext == end.id } +
            graph.nodes.values.count { node -> node.falseNext == end.id })
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `condition insertion into a nested open branch creates a nested early-exit branch`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)

        val inserted = GraphEditor.insert(
            graph,
            inner.id,
            GraphEditor.Edge.FALSE,
            CommandType.CONDITION,
        )

        assertEquals(null, inserted.pairedNodeId)
        assertEquals(null, inserted.trueNext)
        assertEquals(null, inserted.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `explicit merge can rejoin a nested early-exit branch`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)
        val terminal = GraphEditor.insert(graph, inner.id, GraphEditor.Edge.FALSE, CommandType.WAIT)

        val innerMerge = GraphEditor.appendMerge(graph, inner.id, continuationId = outerMerge.id)

        assertEquals(innerMerge.id, terminal.next)
        assertEquals(innerMerge.id, inner.trueNext)
        assertEquals(terminal.id, inner.falseNext)
        assertEquals(outerMerge.id, innerMerge.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `deleting nested merge restores the false branch as an early exit`() {
        val graph = CommandGraph.empty()
        val outer = GraphEditor.append(graph, CommandType.CONDITION)
        val outerMerge = GraphEditor.appendMerge(graph, outer.id)
        val inner = GraphEditor.insert(graph, outer.id, GraphEditor.Edge.FALSE, CommandType.CONDITION)
        val innerMerge = GraphEditor.appendMerge(graph, inner.id, continuationId = outerMerge.id)

        assertTrue(GraphEditor.delete(graph, innerMerge.id))
        assertEquals(null, inner.pairedNodeId)
        assertEquals(outerMerge.id, inner.trueNext)
        assertEquals(null, inner.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `last false command may be deleted while branches remain merged`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val falseCommand = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertTrue(GraphEditor.delete(graph, falseCommand.id))
        assertEquals(merge.id, condition.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `deleting merge reopens branches and keeps successor on true path`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueCommand = GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)

        assertTrue(GraphEditor.delete(graph, merge.id))
        assertEquals(null, condition.pairedNodeId)
        assertEquals(after.id, trueCommand.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `swapBranches exchanges true and false chains while keeping the merge`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.WAIT)
        val falseNode = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)

        assertTrue(GraphEditor.swapBranches(graph, condition.id))
        assertEquals(falseNode.id, condition.trueNext)
        assertEquals(trueNode.id, condition.falseNext)
        assertEquals(condition.id, merge.pairedNodeId)
        assertEquals(merge.id, condition.pairedNodeId)
        assertEquals(after.id, merge.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // 2回で元に戻ります。
        assertTrue(GraphEditor.swapBranches(graph, condition.id))
        assertEquals(trueNode.id, condition.trueNext)
        assertEquals(falseNode.id, condition.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `swapBranches refuses non-condition nodes`() {
        val graph = CommandGraph.empty()
        val node = GraphEditor.append(graph, CommandType.WAIT)

        assertFalse(GraphEditor.swapBranches(graph, node.id))
        assertFalse(GraphEditor.swapBranches(graph, UUID.randomUUID()))
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `expanded structural nodes cannot be reordered`() {
        // 展開中の構造ノード（FOR開始／終了・条件分岐／合流）は移動主体にならず、
        // 入替ボタンをそもそも表示しない。折り畳み前後で種別が変わることはない。
        val graph = CommandGraph.empty()
        val before = GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.GIVE_ITEM, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        GraphEditor.append(graph, CommandType.WAIT)

        listOf(start, end, condition, merge).forEach { structural ->
            assertFalse(GraphEditor.canSwapAdjacent(graph, structural.id, GraphEditor.ReorderDirection.LEFT))
            assertFalse(GraphEditor.canSwapAdjacent(graph, structural.id, GraphEditor.ReorderDirection.RIGHT))
            assertFalse(GraphEditor.swapAdjacent(graph, structural.id, GraphEditor.ReorderDirection.LEFT))
            assertFalse(GraphEditor.swapAdjacent(graph, structural.id, GraphEditor.ReorderDirection.RIGHT))
        }
        // 前後の通常ノードは、境界を越える出入りとして動ける。
        // FOR開始手前の右移動は本体先頭への格納になる。
        assertTrue(GraphEditor.canSwapAdjacent(graph, before.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, before.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(before.id, start.trueNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `body ends move through the boundary of an expanded for`() {
        val graph = CommandGraph.empty()
        val before = GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val head = GraphEditor.appendToForBody(graph, start.id, CommandType.WAIT)
        val tail = GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)

        // 本体先頭の左は開始の前への取り出しになる。
        assertTrue(GraphEditor.canSwapAdjacent(graph, head.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphEditor.swapAdjacent(graph, head.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(head.id, before.next)
        assertEquals(start.id, head.next)
        assertEquals(tail.id, start.trueNext)
        // 本体末尾の右は終了の後ろへの取り出しになる。
        assertTrue(GraphEditor.canSwapAdjacent(graph, tail.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, tail.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(end.id, start.trueNext)
        assertEquals(tail.id, end.next)
        assertNull(tail.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `branch head cannot move out of an expanded condition`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val falseFirst = GraphEditor.append(graph, CommandType.DISPLAY_TEXT, condition.id)
        val falseSecond = GraphEditor.append(graph, CommandType.GIVE_ITEM, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        // FALSE枝先頭の左は取り出し不可のため拒否する（TRUE枝先頭は取り出し可）。
        assertFalse(GraphEditor.canSwapAdjacent(graph, falseFirst.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.swapAdjacent(graph, falseFirst.id, GraphEditor.ReorderDirection.LEFT))
        // 枝内の隣接交換は通常どおり行え、分岐の意味は変わらない。
        assertTrue(GraphEditor.canSwapAdjacent(graph, falseSecond.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphEditor.swapAdjacent(graph, falseSecond.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(falseSecond.id, condition.falseNext)
        assertEquals(falseFirst.id, falseSecond.next)
        assertEquals(merge.id, falseFirst.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `fold for start reconstructs a linear folded node`() {
        val graph = CommandGraph.empty()
        val before = GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val body = GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)
        start.params["count"] = "3"

        assertTrue(GraphEditor.canFoldForStart(graph, start.id))
        assertTrue(GraphEditor.foldForStart(graph, start.id))

        // 外側には開始と同じIDの折り畳みノードが通常形状で残る。
        val folded = graph.nodes.getValue(start.id)
        assertEquals(CommandType.FOLDED_FOR, folded.type)
        assertEquals(after.id, folded.next)
        assertNull(folded.trueNext)
        assertNull(folded.falseNext)
        assertNull(folded.pairedNodeId)
        assertEquals("3", folded.params["count"])
        // 中身は内部グラフへ元のIDのまま退避する。
        val inner = requireNotNull(folded.foldedGraph)
        assertEquals(start.id, inner.entryNodeId)
        assertEquals(setOf(start.id, body.id, end.id), inner.nodes.keys)
        assertEquals(setOf(before.id, start.id, after.id), graph.nodes.keys)
        assertEquals(before.id, graph.entryNodeId)
        assertEquals(start.id, before.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
        assertTrue(GraphEditor.canUnfold(graph, start.id))
    }

    @Test
    fun `unfold restores the for block at the same position`() {
        val graph = CommandGraph.empty()
        val before = GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val body = GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)
        assertTrue(GraphEditor.foldForStart(graph, start.id))

        assertTrue(GraphEditor.unfold(graph, start.id))

        val restored = graph.nodes.getValue(start.id)
        assertEquals(CommandType.FOR_START, restored.type)
        assertNull(restored.foldedGraph)
        assertEquals(body.id, restored.trueNext)
        assertEquals(end.id, restored.pairedNodeId)
        assertEquals(start.id, graph.nodes.getValue(before.id).next)
        assertEquals(after.id, graph.nodes.getValue(end.id).next)
        assertEquals(before.id, graph.entryNodeId)
        assertTrue(GraphValidator.validate(graph).isEmpty())
        assertTrue(GraphEditor.canFoldForStart(graph, start.id))
    }

    @Test
    fun `fold condition requires a merged pair`() {
        // 未合流の分岐は出口が定まらないため折り畳めない。
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val before = graph.deepCopy()

        assertFalse(GraphEditor.canFoldCondition(graph, condition.id))
        assertFalse(GraphEditor.foldCondition(graph, condition.id))
        assertEquals(before.entryNodeId, graph.entryNodeId)
        assertEquals(before.nodes.keys, graph.nodes.keys)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `fold and unfold merged condition round-trips`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val trueNode = GraphEditor.append(graph, CommandType.DISPLAY_TEXT)
        val falseNode = GraphEditor.append(graph, CommandType.GIVE_ITEM, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.WAIT)

        assertTrue(GraphEditor.canFoldCondition(graph, condition.id))
        assertTrue(GraphEditor.foldCondition(graph, condition.id))

        val folded = graph.nodes.getValue(condition.id)
        assertEquals(CommandType.FOLDED_IF, folded.type)
        assertEquals(after.id, folded.next)
        assertNull(folded.trueNext)
        assertNull(folded.falseNext)
        assertNull(folded.pairedNodeId)
        val inner = requireNotNull(folded.foldedGraph)
        assertEquals(condition.id, inner.entryNodeId)
        assertEquals(setOf(condition.id, trueNode.id, falseNode.id, merge.id), inner.nodes.keys)
        assertTrue(GraphValidator.validate(graph).isEmpty())

        assertTrue(GraphEditor.unfold(graph, condition.id))
        val restored = graph.nodes.getValue(condition.id)
        assertEquals(CommandType.CONDITION, restored.type)
        assertEquals(trueNode.id, restored.trueNext)
        assertEquals(falseNode.id, restored.falseNext)
        assertEquals(merge.id, restored.pairedNodeId)
        assertEquals(after.id, graph.nodes.getValue(merge.id).next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `folded nodes swap like normal nodes`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        val middle = GraphEditor.append(graph, CommandType.GIVE_ITEM)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        GraphEditor.append(graph, CommandType.ENTITY_ACTION, condition.id)
        GraphEditor.appendMerge(graph, condition.id)
        val last = GraphEditor.append(graph, CommandType.WAIT)
        assertTrue(GraphEditor.foldForStart(graph, start.id))
        assertTrue(GraphEditor.foldCondition(graph, condition.id))

        // 折り畳みFORと通常ノードの交換。
        assertTrue(GraphEditor.canSwapAdjacent(graph, start.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, start.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(middle.id, first.next)
        assertEquals(start.id, middle.next)
        // 折り畳み同士の交換。
        assertTrue(GraphEditor.canSwapAdjacent(graph, condition.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphEditor.swapAdjacent(graph, condition.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(condition.id, middle.next)
        assertEquals(start.id, graph.nodes.getValue(condition.id).next)
        assertEquals(last.id, graph.nodes.getValue(start.id).next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `folded node settings stay on the wrapper and return on unfold`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        GraphEditor.append(graph, CommandType.WAIT)
        graph.nodes.getValue(start.id).params["count"] = "5"
        assertTrue(GraphEditor.foldForStart(graph, start.id))

        // 折り畳み中の設定編集は外側へ行う。
        graph.nodes.getValue(start.id).params["count"] = "2"
        assertTrue(GraphEditor.unfold(graph, start.id))

        // 展開時は外側を正として内部先頭へ反映する。
        assertEquals("2", graph.nodes.getValue(start.id).params["count"])
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `deleting a folded node removes its contents`() {
        val graph = CommandGraph.empty()
        val before = GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val body = GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)
        assertTrue(GraphEditor.foldForStart(graph, start.id))

        assertTrue(GraphEditor.delete(graph, start.id))

        assertEquals(setOf(before.id, after.id), graph.nodes.keys)
        assertEquals(after.id, graph.nodes.getValue(before.id).next)
        assertFalse(body.id in graph.nodes)
        assertFalse(end.id in graph.nodes)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `duplicate copies a folded node with an independent inner graph`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val body = GraphEditor.appendToForBody(graph, start.id, CommandType.WAIT)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)
        assertTrue(GraphEditor.foldForStart(graph, start.id))

        val clone = requireNotNull(GraphEditor.duplicate(graph, start.id))

        assertEquals(CommandType.FOLDED_FOR, clone.type)
        assertEquals(after.id, clone.next)
        assertEquals(clone.id, graph.nodes.getValue(start.id).next)
        val sourceInner = requireNotNull(graph.nodes.getValue(start.id).foldedGraph)
        val cloneInner = requireNotNull(clone.foldedGraph)
        assertNotSame(sourceInner, cloneInner)
        // 内部先頭は外側IDと同一に保ち、その他の内部IDは付け替える。
        assertEquals(clone.id, cloneInner.entryNodeId)
        val cloneBodyId = requireNotNull(cloneInner.nodes.getValue(clone.id).trueNext)
        assertNotEquals(body.id, cloneBodyId)
        // 内部の複製は独立しており、複製側の変更は正本へ波及しない。
        cloneInner.nodes.getValue(cloneBodyId).params["seconds"] = "5"
        assertEquals("1", sourceInner.nodes.getValue(body.id).params["seconds"])
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `insert and append reject folded types`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)

        assertThrows(IllegalArgumentException::class.java) {
            GraphEditor.insert(graph, null, GraphEditor.Edge.ENTRY, CommandType.FOLDED_FOR)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraphEditor.insert(graph, null, GraphEditor.Edge.ENTRY, CommandType.FOLDED_IF)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraphEditor.append(graph, CommandType.FOLDED_FOR)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GraphEditor.append(graph, CommandType.FOLDED_IF)
        }
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `canUnfold refuses broken wrappers`() {
        val graph = CommandGraph.empty()
        val broken = CommandType.FOLDED_FOR.newNode()
        graph.entryNodeId = broken.id
        graph.nodes[broken.id] = broken

        // 内部グラフなしは展開できない。
        assertFalse(GraphEditor.canUnfold(graph, broken.id))
        assertFalse(GraphEditor.unfold(graph, broken.id))

        // 折り畳み済みの通常ノードでも展開はできない。
        val plain = GraphEditor.append(graph, CommandType.WAIT)
        assertFalse(GraphEditor.canUnfold(graph, plain.id))
        assertFalse(GraphEditor.unfold(graph, plain.id))

        // 折り畳みノードへの折り畳みはできない。
        // 壊れた検証用ノードを取り除いてから再構成する（壊れた内容は検証に通らない）。
        assertTrue(GraphEditor.delete(graph, broken.id))
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        assertTrue(GraphEditor.foldForStart(graph, start.id))
        assertFalse(GraphEditor.canFoldForStart(graph, start.id))
        assertFalse(GraphEditor.foldForStart(graph, start.id))
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `break inside a folded for body stays valid while swapping`() {
        val graph = CommandGraph.empty()
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        GraphEditor.appendToForBody(graph, start.id, CommandType.WAIT)
        GraphEditor.appendToForBody(graph, start.id, CommandType.BREAK)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)
        assertTrue(GraphEditor.foldForStart(graph, start.id))
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // 折り畳み単位の交換では本体所属が変わらず、検証に通り続ける。
        assertTrue(GraphEditor.canSwapAdjacent(graph, start.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, start.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(after.id, graph.entryNodeId)
        assertEquals(start.id, after.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `condition and merge remain out of swap scope`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)
        val merge = GraphEditor.appendMerge(graph, condition.id)

        assertFalse(GraphEditor.canSwapAdjacent(graph, condition.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.canSwapAdjacent(graph, condition.id, GraphEditor.ReorderDirection.RIGHT))
        assertFalse(GraphEditor.canSwapAdjacent(graph, merge.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.canSwapAdjacent(graph, merge.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `insertion with next edge into a condition fails immediately`() {
        val graph = CommandGraph.empty()
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT)

        // 条件分岐にNEXT実行経路はないため、到達不能ノードを作る前に即時失敗します。
        assertThrows(IllegalArgumentException::class.java) {
            GraphEditor.insert(graph, condition.id, GraphEditor.Edge.NEXT, CommandType.WAIT)
        }
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `outer node stores into for head`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val body = GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)

        // first—S[body]—END—after の first を右へ：本体先頭へ格納します。
        assertTrue(GraphEditor.canSwapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(start.id, graph.entryNodeId)
        assertEquals(first.id, start.trueNext)
        assertEquals(body.id, first.next)
        assertEquals(end.id, body.next)
        assertEquals(after.id, end.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `outer node stores into empty for head`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)
        assertEquals(end.id, start.trueNext)

        assertTrue(GraphEditor.canSwapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(start.id, graph.entryNodeId)
        assertEquals(first.id, start.trueNext)
        assertEquals(end.id, first.next)
        assertEquals(after.id, end.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `outer node stores into for tail`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val body = GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)

        // S[body]—END—after の after を左へ：本体末尾へ格納します。
        assertTrue(GraphEditor.canSwapAdjacent(graph, after.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphEditor.swapAdjacent(graph, after.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(after.id, body.next)
        assertEquals(end.id, after.next)
        assertNull(end.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `for head takes out to the left`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val head = GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        val tail = GraphEditor.appendToForBody(graph, start.id, CommandType.GIVE_ITEM)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val after = GraphEditor.append(graph, CommandType.WAIT)

        // 本体先頭を左へ：開始の前へ取り出します。
        assertTrue(GraphEditor.canSwapAdjacent(graph, head.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphEditor.swapAdjacent(graph, head.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(head.id, first.next)
        assertEquals(start.id, head.next)
        assertEquals(tail.id, start.trueNext)
        assertEquals(end.id, tail.next)
        assertEquals(after.id, end.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `for tail takes out to the right`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val head = GraphEditor.appendToForBody(graph, start.id, CommandType.DISPLAY_TEXT)
        val tail = GraphEditor.appendToForBody(graph, start.id, CommandType.GIVE_ITEM)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val after = GraphEditor.append(graph, CommandType.WAIT)

        // 本体末尾を右へ：終了の後ろへ取り出します。
        assertTrue(GraphEditor.canSwapAdjacent(graph, tail.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, tail.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(head.id, start.trueNext)
        assertEquals(end.id, head.next)
        assertEquals(tail.id, end.next)
        assertEquals(after.id, tail.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `outer node enters true head`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val head = CommandType.DISPLAY_TEXT.newNode()
        graph.nodes[head.id] = head
        condition.trueNext = head.id
        val falseHead = GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)
        head.next = merge.id
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // 条件の手前のノードを右へ：TRUE先頭へ入れます。
        assertTrue(GraphEditor.canSwapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(condition.id, graph.entryNodeId)
        assertEquals(first.id, condition.trueNext)
        assertEquals(head.id, first.next)
        assertEquals(falseHead.id, condition.falseNext)
        assertEquals(merge.id, head.next)
        assertEquals(after.id, merge.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `outer node enters true head of unmerged condition`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)

        // 未合流・両枝null終端の条件へ入れても、終端の意味は変えません。
        assertTrue(GraphEditor.canSwapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(condition.id, graph.entryNodeId)
        assertEquals(first.id, condition.trueNext)
        assertNull(first.next)
        assertNull(condition.falseNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `outer node enters true tail`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val head = CommandType.DISPLAY_TEXT.newNode()
        val tail = CommandType.GIVE_ITEM.newNode()
        graph.nodes[head.id] = head
        graph.nodes[tail.id] = tail
        condition.trueNext = head.id
        head.next = tail.id
        GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        tail.next = merge.id
        val after = GraphEditor.append(graph, CommandType.ENTITY_ACTION)
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // 合流の後ろのノードを左へ：TRUE末尾へ入れます。
        assertTrue(GraphEditor.canSwapAdjacent(graph, after.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphEditor.swapAdjacent(graph, after.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(after.id, tail.next)
        assertEquals(merge.id, after.next)
        assertNull(merge.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `true tail entry refused for null-terminated true`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        val after = GraphEditor.append(graph, CommandType.ENTITY_ACTION)
        // TRUE枝をnull終端に戻し、合流に届かない形にします（正常終了として妥当）。
        condition.trueNext = null
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // TRUE枝が合流に届かないため、合流の後ろからは入れません。
        assertFalse(GraphEditor.canSwapAdjacent(graph, after.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.swapAdjacent(graph, after.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(after.id, merge.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `true head takes out to the left`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val head = CommandType.DISPLAY_TEXT.newNode()
        val second = CommandType.GIVE_ITEM.newNode()
        graph.nodes[head.id] = head
        graph.nodes[second.id] = second
        condition.trueNext = head.id
        head.next = second.id
        GraphEditor.append(graph, CommandType.WAIT, condition.id)
        val merge = GraphEditor.appendMerge(graph, condition.id)
        second.next = merge.id
        GraphEditor.append(graph, CommandType.ENTITY_ACTION)
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // TRUE先頭を左へ：条件の前へ取り出します。
        assertTrue(GraphEditor.canSwapAdjacent(graph, head.id, GraphEditor.ReorderDirection.LEFT))
        assertTrue(GraphEditor.swapAdjacent(graph, head.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(head.id, first.next)
        assertEquals(condition.id, head.next)
        assertEquals(second.id, condition.trueNext)
        assertEquals(merge.id, second.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `false head take-out refused but tail take-out allowed`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val condition = GraphEditor.append(graph, CommandType.CONDITION)
        val head = CommandType.DISPLAY_TEXT.newNode()
        graph.nodes[head.id] = head
        condition.trueNext = head.id
        val falseHead = CommandType.GIVE_ITEM.newNode()
        graph.nodes[falseHead.id] = falseHead
        condition.falseNext = falseHead.id
        val merge = GraphEditor.appendMerge(graph, condition.id)
        head.next = merge.id
        falseHead.next = merge.id
        val after = GraphEditor.append(graph, CommandType.ENTITY_ACTION)
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // FALSE先頭の取り出しは不可とし、枝を壊しません。
        assertFalse(GraphEditor.canSwapAdjacent(graph, falseHead.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.swapAdjacent(graph, falseHead.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(falseHead.id, condition.falseNext)
        // FALSE末尾の取り出し（合流の後ろへ）は許可します。
        assertTrue(GraphEditor.canSwapAdjacent(graph, falseHead.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, falseHead.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(merge.id, condition.falseNext)
        assertEquals(falseHead.id, merge.next)
        assertEquals(after.id, falseHead.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `broken for pair refuses crossing`() {
        val graph = CommandGraph.empty()
        val first = GraphEditor.append(graph, CommandType.WAIT)
        val start = CommandType.FOR_START.newNode()
        graph.nodes[start.id] = start
        first.next = start.id
        val after = CommandType.GIVE_ITEM.newNode()
        graph.nodes[after.id] = after
        start.next = after.id

        // ペア壊れFORへの格納・取り出しは拒否します（通常交換は対象外のため触れません）。
        assertFalse(GraphEditor.canSwapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertFalse(GraphEditor.swapAdjacent(graph, first.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(start.id, first.next)
    }

    @Test
    fun `break take-out refused by validation`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val breaker = GraphEditor.appendToForBody(graph, start.id, CommandType.BREAK)
        GraphEditor.append(graph, CommandType.GIVE_ITEM)
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // 本体唯一のBREAKを外へ出すと所属が壊れるため、検証ゲートで拒否します。
        // 拒否時は正本へ戻すため、グラフは変更前のままです（最新正本で確認します）。
        assertFalse(GraphEditor.canSwapAdjacent(graph, breaker.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.swapAdjacent(graph, breaker.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.canSwapAdjacent(graph, breaker.id, GraphEditor.ReorderDirection.RIGHT))
        assertFalse(GraphEditor.swapAdjacent(graph, breaker.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(breaker.id, graph.nodes.getValue(start.id).trueNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `folded mover stores into for head`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val inner = GraphEditor.append(graph, CommandType.FOR_START)
        GraphEditor.appendToForBody(graph, inner.id, CommandType.DISPLAY_TEXT)
        GraphEditor.appendToForBody(graph, inner.id, CommandType.BREAK)
        val outer = GraphEditor.append(graph, CommandType.FOR_START)
        val give = GraphEditor.appendToForBody(graph, outer.id, CommandType.GIVE_ITEM)
        val outerEnd = requireNotNull(outer.pairedNodeId).let(graph.nodes::get)!!
        val after = GraphEditor.append(graph, CommandType.WAIT)
        assertTrue(GraphEditor.foldForStart(graph, inner.id))
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // 折り畳み単位も外側ノードと同じく本体先頭へ格納でき、内包BREAKの所属は保たれます。
        assertTrue(GraphEditor.canSwapAdjacent(graph, inner.id, GraphEditor.ReorderDirection.RIGHT))
        assertTrue(GraphEditor.swapAdjacent(graph, inner.id, GraphEditor.ReorderDirection.RIGHT))
        assertEquals(inner.id, outer.trueNext)
        assertEquals(give.id, graph.nodes.getValue(inner.id).next)
        assertEquals(outerEnd.id, give.next)
        assertEquals(after.id, outerEnd.next)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    @Test
    fun `nested body refuses tail store`() {
        val graph = CommandGraph.empty()
        GraphEditor.append(graph, CommandType.WAIT)
        val start = GraphEditor.append(graph, CommandType.FOR_START)
        val nested = GraphEditor.appendToForBody(graph, start.id, CommandType.CONDITION)
        val end = requireNotNull(start.pairedNodeId).let(graph.nodes::get)!!
        val after = GraphEditor.append(graph, CommandType.GIVE_ITEM)
        assertTrue(GraphValidator.validate(graph).isEmpty())

        // 本体に分岐構造を含む場合の末尾格納は対象外とし、構造を壊しません。
        assertFalse(GraphEditor.canSwapAdjacent(graph, after.id, GraphEditor.ReorderDirection.LEFT))
        assertFalse(GraphEditor.swapAdjacent(graph, after.id, GraphEditor.ReorderDirection.LEFT))
        assertEquals(after.id, end.next)
        assertEquals(nested.id, start.trueNext)
        assertTrue(GraphValidator.validate(graph).isEmpty())
    }

    /** 構造化設定とDISK_CALLのsnapshotを含む、複製対象の全設定を一つのテストデータへ集約します。 */
    private fun configuredNode(type: CommandType): CommandNode {
        val nested = CommandType.WAIT.newNode()
        return type.newNode().also { node ->
            node.params["custom"] = "value"
            node.configuredFields = linkedSetOf("custom", "seconds")
            node.targetSpec = TargetSpec(
                kind = TargetKind.NEAREST_ENTITY,
                entityType = "minecraft:zombie",
                minimumDistance = 1.5,
                maximumDistance = 8.0,
                limit = 2,
                gameMode = "survival",
                tag = "target",
                name = "Target",
                dx = 1.0,
                dy = 2.0,
                dz = 3.0,
                searchOrigin = SearchOriginSpec(
                    positionTemp = "origin",
                    position = PositionSpec(PositionKind.COORDINATES, 4.0, 5.0, 6.0),
                ),
            )
            node.secondaryTargetSpec = TargetSpec(TargetKind.FIXED_ENTITY, fixedEntityId = UUID.randomUUID())
            node.destinationSpec = PositionSpec(PositionKind.TEMPORARY, tempName = "destination")
            node.destinationTargetSpec = TargetSpec(TargetKind.NEAREST_PLAYER)
            node.destinationFacingSpec = FacingSpec(FacingKind.ROTATION, yaw = 45f, pitch = 10f)
            node.conditionPositionSpec = PositionSpec(PositionKind.MYWORLD_SPAWN)
            node.blockPositionSpec = PositionSpec(PositionKind.CAPTURED)
            node.blockFromSpec = PositionSpec(PositionKind.COORDINATES, 1.0, 2.0, 3.0)
            node.blockToSpec = PositionSpec(PositionKind.COORDINATES, 4.0, 5.0, 6.0)
            node.soundPositionSpec = PositionSpec(PositionKind.TARGET)
            node.summonPositionSpec = PositionSpec(PositionKind.TEMPORARY, tempName = "summon")
            node.temporaryEntityTargetSpec = TargetSpec(TargetKind.TEMPORARY, tempName = "entity")
            node.temporaryLocationPositionSpec = PositionSpec(PositionKind.COORDINATES, 7.0, 8.0, 9.0)
            node.temporaryLocationFacingSpec = FacingSpec(FacingKind.COORDINATES, 10.0, 11.0, 12.0)
            node.itemTempRef = "item"
            node.blockTempRef = "block"
            node.soundTempRef = "sound"
            node.effectTempRef = "effect"
            node.snapshot = CommandGraph(nested.id, linkedMapOf(nested.id to nested))
        }
    }
}
